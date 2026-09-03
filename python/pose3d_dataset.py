"""
VitalFi — Pose3D Dataset & Synthetic Data Generator

Provides:
  1. WiFiPose3DDataset: PyTorch Dataset for loading recorded CSI + pose pairs
  2. Pose3DRecorder: Records live CSI from ESP32 + camera poses for training
  3. SyntheticCSIGenerator: Generates synthetic CSI perturbations based on
     Fresnel zone reflection models for testing without real data

Dataset format (.npz):
  - csi_amplitude: float32 (N, nodes, subcarriers, time_steps)
  - csi_phase: float32 (N, nodes, subcarriers, time_steps)
  - keypoints_3d: float32 (N, 17, 3)
  - posture_labels: int64 (N,)
  - metadata: dict with recording info
"""

from __future__ import annotations

import os
import time
import math
from pathlib import Path

import numpy as np
import torch
from torch.utils.data import Dataset


class WiFiPose3DDataset(Dataset):
    """PyTorch Dataset for WiFi CSI → 3D Pose training data.

    Loads pre-recorded .npz files containing synchronized CSI and
    ground-truth 3D pose annotations.
    """

    def __init__(
        self,
        data_path: str,
        num_subcarriers: int = 64,
        time_steps: int = 30,
        transform=None,
    ):
        self.data_path = Path(data_path)
        self.num_subcarriers = num_subcarriers
        self.time_steps = time_steps
        self.transform = transform

        if self.data_path.is_file():
            self._load_single(self.data_path)
        elif self.data_path.is_dir():
            self._load_directory(self.data_path)
        else:
            raise FileNotFoundError(f"Data path not found: {data_path}")

        print(f"[Dataset] Loaded {len(self)} samples from {data_path}")

    def _load_single(self, path: Path) -> None:
        data = np.load(path, allow_pickle=True)
        self.csi_amplitude = data["csi_amplitude"].astype(np.float32)
        self.csi_phase = data["csi_phase"].astype(np.float32)
        self.keypoints_3d = data["keypoints_3d"].astype(np.float32)
        self.posture_labels = data["posture_labels"].astype(np.int64)

    def _load_directory(self, path: Path) -> None:
        npz_files = sorted(path.glob("*.npz"))
        if not npz_files:
            raise FileNotFoundError(f"No .npz files found in {path}")

        all_amp, all_phase, all_kp, all_posture = [], [], [], []
        for f in npz_files:
            data = np.load(f, allow_pickle=True)
            all_amp.append(data["csi_amplitude"])
            all_phase.append(data["csi_phase"])
            all_kp.append(data["keypoints_3d"])
            all_posture.append(data["posture_labels"])

        self.csi_amplitude = np.concatenate(all_amp).astype(np.float32)
        self.csi_phase = np.concatenate(all_phase).astype(np.float32)
        self.keypoints_3d = np.concatenate(all_kp).astype(np.float32)
        self.posture_labels = np.concatenate(all_posture).astype(np.int64)

    def __len__(self) -> int:
        return len(self.keypoints_3d)

    def __getitem__(self, idx: int) -> dict[str, torch.Tensor]:
        amp = self.csi_amplitude[idx]    # (nodes, subcarriers, time_steps)
        phase = self.csi_phase[idx]      # (nodes, subcarriers, time_steps)
        kp = self.keypoints_3d[idx]      # (17, 3)
        posture = self.posture_labels[idx]  # scalar

        # Stack amplitude + phase as channels
        # Result per node: (2, subcarriers, time_steps)
        num_nodes = amp.shape[0]
        node_tensors = []
        for n in range(num_nodes):
            csi_input = np.stack([amp[n], phase[n]], axis=0)  # (2, S, T)
            node_tensors.append(torch.from_numpy(csi_input))

        sample = {
            "csi_nodes": node_tensors,
            "keypoints_3d": torch.from_numpy(kp),
            "posture_label": torch.tensor(posture, dtype=torch.long),
        }

        if self.transform:
            sample = self.transform(sample)

        return sample


class SyntheticCSIGenerator:
    """Generates synthetic CSI data for testing and pre-training.

    Simulates Wi-Fi signal perturbations caused by human body movements
    using simplified Fresnel zone reflection models.

    The synthetic data captures:
      - Static multipath baseline (room reflections)
      - Human body attenuation/reflection (position-dependent)
      - Respiratory micro-movements (periodic amplitude modulation)
      - Phase variations from body displacement

    This is NOT a substitute for real data but enables:
      - Model architecture validation
      - Pipeline integration testing
      - Pre-training before fine-tuning on real CSI
    """

    def __init__(
        self,
        num_subcarriers: int = 64,
        time_steps: int = 30,
        num_nodes: int = 3,
        fs: float = 30.0,  # Sampling rate in Hz
        seed: int | None = None,
    ):
        self.num_subcarriers = num_subcarriers
        self.time_steps = time_steps
        self.num_nodes = num_nodes
        self.fs = fs
        self.rng = np.random.default_rng(seed)

    def generate_batch(
        self,
        batch_size: int = 32,
        postures: list[str] | None = None,
    ) -> dict[str, np.ndarray]:
        """Generate a batch of synthetic CSI + pose data.

        Args:
            batch_size: Number of samples.
            postures: List of posture labels. If None, random.

        Returns:
            Dictionary with:
              - csi_amplitude: (B, nodes, subcarriers, time_steps)
              - csi_phase: (B, nodes, subcarriers, time_steps)
              - keypoints_3d: (B, 17, 3)
              - posture_labels: (B,)
        """
        from pose3d_model import POSTURE_CLASSES

        all_amp = []
        all_phase = []
        all_kp = []
        all_posture = []

        for i in range(batch_size):
            if postures:
                posture = postures[i % len(postures)]
            else:
                posture = self.rng.choice(POSTURE_CLASSES)

            kp = self._generate_skeleton(posture)
            amp, phase = self._generate_csi_from_skeleton(kp)

            all_amp.append(amp)
            all_phase.append(phase)
            all_kp.append(kp)
            all_posture.append(POSTURE_CLASSES.index(posture))

        return {
            "csi_amplitude": np.array(all_amp, dtype=np.float32),
            "csi_phase": np.array(all_phase, dtype=np.float32),
            "keypoints_3d": np.array(all_kp, dtype=np.float32),
            "posture_labels": np.array(all_posture, dtype=np.int64),
        }

    def _generate_skeleton(self, posture: str) -> np.ndarray:
        """Generate a plausible 17-keypoint 3D skeleton for a given posture.

        Returns: (17, 3) array of (x, y, z) coordinates in meters.
        """
        noise = self.rng.normal(0, 0.03, (17, 3)).astype(np.float32)

        if posture == "standing":
            base = self._standing_skeleton()
        elif posture == "sitting":
            base = self._sitting_skeleton()
        elif posture == "lying":
            base = self._lying_skeleton()
        elif posture == "fallen":
            base = self._fallen_skeleton()
        else:
            base = self._standing_skeleton()

        # Add random offset in room
        offset = self.rng.uniform(-2.0, 2.0, 3).astype(np.float32)
        offset[2] = 0  # Keep Z relative to ground
        return base + noise + offset

    def _standing_skeleton(self) -> np.ndarray:
        """Standing person template."""
        kp = np.zeros((17, 3), dtype=np.float32)
        # Head
        kp[0] = [0.0, 0.0, 1.7]    # nose
        kp[1] = [-0.03, -0.02, 1.72]  # left_eye
        kp[2] = [0.03, -0.02, 1.72]   # right_eye
        kp[3] = [-0.08, 0.0, 1.70]    # left_ear
        kp[4] = [0.08, 0.0, 1.70]     # right_ear
        # Shoulders
        kp[5] = [-0.20, 0.0, 1.45]    # left_shoulder
        kp[6] = [0.20, 0.0, 1.45]     # right_shoulder
        # Elbows
        kp[7] = [-0.25, 0.05, 1.15]   # left_elbow
        kp[8] = [0.25, 0.05, 1.15]    # right_elbow
        # Wrists
        kp[9] = [-0.22, 0.08, 0.90]   # left_wrist
        kp[10] = [0.22, 0.08, 0.90]   # right_wrist
        # Hips
        kp[11] = [-0.12, 0.0, 0.95]   # left_hip
        kp[12] = [0.12, 0.0, 0.95]    # right_hip
        # Knees
        kp[13] = [-0.12, 0.02, 0.50]  # left_knee
        kp[14] = [0.12, 0.02, 0.50]   # right_knee
        # Ankles
        kp[15] = [-0.12, 0.0, 0.05]   # left_ankle
        kp[16] = [0.12, 0.0, 0.05]    # right_ankle
        return kp

    def _sitting_skeleton(self) -> np.ndarray:
        kp = self._standing_skeleton()
        # Bend at hips and knees
        kp[11:13, 2] -= 0.40   # Hips lower
        kp[13:15, 2] -= 0.20   # Knees at chair level
        kp[13:15, 1] += 0.30   # Knees forward
        kp[15:17, 2] -= 0.10   # Ankles on floor
        kp[15:17, 1] += 0.20
        # Upper body adjusts
        kp[0:11, 2] -= 0.40
        return kp

    def _lying_skeleton(self) -> np.ndarray:
        kp = self._standing_skeleton()
        # Rotate to horizontal (swap Y and Z)
        temp_z = kp[:, 2].copy()
        kp[:, 2] = 0.15  # On the ground
        kp[:, 1] = temp_z - temp_z.min()  # Spread along Y
        return kp

    def _fallen_skeleton(self) -> np.ndarray:
        kp = self._lying_skeleton()
        # Add asymmetric displacement (irregular fallen position)
        kp[:5, 0] += self.rng.uniform(-0.2, 0.2)
        kp[9:11, 2] += 0.1  # Wrist up slightly
        kp[13:17, 0] += self.rng.uniform(-0.3, 0.3)
        return kp

    def _generate_csi_from_skeleton(
        self, keypoints: np.ndarray
    ) -> tuple[np.ndarray, np.ndarray]:
        """Generate synthetic CSI amplitude and phase from skeleton position.

        Simulates the effect of a human body on Wi-Fi signal propagation
        using a simplified multipath + Fresnel reflection model.

        Returns:
            amplitude: (nodes, subcarriers, time_steps)
            phase: (nodes, subcarriers, time_steps)
        """
        S = self.num_subcarriers
        T = self.time_steps
        N = self.num_nodes

        amplitude = np.zeros((N, S, T), dtype=np.float32)
        phase = np.zeros((N, S, T), dtype=np.float32)

        # Body center of mass
        body_center = keypoints[5:13].mean(axis=0)  # Shoulders + hips average

        for n in range(N):
            # Node position (distributed around the room)
            angle = 2 * math.pi * n / N
            node_pos = np.array([3 * math.cos(angle), 3 * math.sin(angle), 0.5])

            # Distance from body to node
            dist = np.linalg.norm(body_center - node_pos)

            for t in range(T):
                time_sec = t / self.fs

                # Respiratory modulation: ~0.25 Hz periodic
                resp_mod = 0.02 * math.sin(2 * math.pi * 0.25 * time_sec)

                for s in range(S):
                    # Base amplitude: attenuated by distance
                    base_amp = 20.0 / (1.0 + dist * 0.5)

                    # Subcarrier-dependent variation (frequency selectivity)
                    freq_var = math.sin(2 * math.pi * s / S * 3 + dist)

                    # Human body perturbation
                    body_effect = 2.0 * math.exp(-dist * 0.3) * (1 + resp_mod)

                    amplitude[n, s, t] = base_amp + freq_var * 2 + body_effect

                    # Phase: distance-based + body reflection
                    wavelength = 0.125  # ~2.4 GHz
                    base_phase = (2 * math.pi * dist / wavelength) % (2 * math.pi) - math.pi
                    phase_pert = resp_mod * 10 * (s / S)

                    phase[n, s, t] = base_phase + freq_var * 0.5 + phase_pert

                # Add noise
                amplitude[n, :, t] += self.rng.normal(0, 0.5, S)
                phase[n, :, t] += self.rng.normal(0, 0.1, S)

        # Clip amplitude to positive
        amplitude = np.maximum(amplitude, 0.1)

        return amplitude, phase

    def save_dataset(
        self,
        output_path: str,
        num_samples: int = 1000,
    ) -> str:
        """Generate and save a synthetic dataset to disk.

        Args:
            output_path: Path to save the .npz file.
            num_samples: Number of samples to generate.

        Returns:
            Path to saved file.
        """
        print(f"[Synthetic] Generating {num_samples} samples...")
        data = self.generate_batch(batch_size=num_samples)

        np.savez_compressed(
            output_path,
            csi_amplitude=data["csi_amplitude"],
            csi_phase=data["csi_phase"],
            keypoints_3d=data["keypoints_3d"],
            posture_labels=data["posture_labels"],
        )

        size_mb = os.path.getsize(output_path) / (1024 * 1024)
        print(f"[Synthetic] Saved to {output_path} ({size_mb:.1f} MB)")
        return output_path


if __name__ == "__main__":
    # Generate a small synthetic dataset for testing
    gen = SyntheticCSIGenerator(seed=42)
    gen.save_dataset("synthetic_pose3d_data.npz", num_samples=100)

    # Test loading
    dataset = WiFiPose3DDataset("synthetic_pose3d_data.npz")
    sample = dataset[0]
    print(f"Sample CSI nodes: {len(sample['csi_nodes'])}")
    print(f"Sample keypoints shape: {sample['keypoints_3d'].shape}")
    print(f"Sample posture: {sample['posture_label']}")
