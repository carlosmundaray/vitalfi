"""
VitalFi — Pose3D Processing Pipeline

Full pipeline from raw CSI windows to 3D skeleton output:
  1. Receive temporal window of CSI frames from multiple nodes
  2. Preprocess: normalize amplitude, sanitize phase (re-sanitize if needed)
  3. Build model input tensors
  4. Run inference with WiFiPoseEstimator
  5. Post-process: temporal smoothing, outlier filtering
  6. Fuse with vital signs detection results
  7. Emit combined result

Usage:
    pipeline = Pose3DPipeline(model_path="pose3d_model.pt")
    pipeline.start()

    # Pipeline runs continuously, results available via callback or queue
"""

from __future__ import annotations

import time
import threading
from dataclasses import dataclass, field
from collections import deque

import numpy as np
import torch

from pose3d_model import (
    WiFiPoseEstimator, create_model,
    COCO_KEYPOINTS, COCO_SKELETON, POSTURE_CLASSES, NUM_KEYPOINTS,
)
from pose3d_receiver import Pose3DReceiver, CSIWindow
from csi_processor import sanitize_phase


@dataclass
class PoseResult:
    """Result of a single pose estimation inference."""
    timestamp: float                    # Unix timestamp of the result
    keypoints_3d: np.ndarray            # (17, 3) — x, y, z coordinates
    confidence: np.ndarray              # (17,) — per-keypoint confidence [0, 1]
    posture: str                        # "standing" / "sitting" / "lying" / "fallen"
    posture_confidence: float           # Confidence of posture classification
    active_nodes: list[int]             # Node IDs that contributed
    inference_time_ms: float            # Model inference duration
    total_frames_used: int              # Total CSI frames in the window


@dataclass
class FusedResult:
    """Combined pose estimation + vital signs result."""
    pose: PoseResult | None
    is_breathing: bool = False
    respiratory_rate: float = 0.0
    heart_rate: float = 0.0
    vital_confidence: float = 0.0
    description: str = ""


class TemporalSmoother:
    """Exponential moving average smoother for 3D keypoints."""

    def __init__(self, alpha: float = 0.3, outlier_threshold: float = 0.5):
        self.alpha = alpha
        self.outlier_threshold = outlier_threshold
        self._prev_keypoints: np.ndarray | None = None

    def smooth(self, keypoints: np.ndarray, confidence: np.ndarray) -> np.ndarray:
        """Apply temporal smoothing with outlier rejection.

        Args:
            keypoints: (17, 3) raw predicted keypoints.
            confidence: (17,) per-keypoint confidence.

        Returns:
            Smoothed keypoints (17, 3).
        """
        if self._prev_keypoints is None:
            self._prev_keypoints = keypoints.copy()
            return keypoints

        smoothed = np.zeros_like(keypoints)
        for i in range(keypoints.shape[0]):
            diff = np.linalg.norm(keypoints[i] - self._prev_keypoints[i])

            if diff > self.outlier_threshold and confidence[i] < 0.5:
                # Likely outlier — keep previous value
                smoothed[i] = self._prev_keypoints[i]
            else:
                # Weighted EMA: higher confidence → more weight to new data
                effective_alpha = self.alpha * max(0.3, confidence[i])
                smoothed[i] = (
                    self._prev_keypoints[i] * (1 - effective_alpha)
                    + keypoints[i] * effective_alpha
                )

        self._prev_keypoints = smoothed.copy()
        return smoothed

    def reset(self) -> None:
        self._prev_keypoints = None


class Pose3DPipeline:
    """Main processing pipeline for Wi-Fi 3D pose estimation."""

    def __init__(
        self,
        model_path: str | None = None,
        receiver_port: int = 5502,
        window_duration_ms: int = 300,
        inference_interval_ms: int = 100,
        num_subcarriers: int = 64,
        time_steps: int = 30,
        max_nodes: int = 4,
    ):
        self.window_duration_ms = window_duration_ms
        self.inference_interval_ms = inference_interval_ms
        self.num_subcarriers = num_subcarriers
        self.time_steps = time_steps

        # Initialize model
        self.model = create_model(
            num_subcarriers=num_subcarriers,
            max_nodes=max_nodes,
            pretrained_path=model_path,
        )
        self.model.eval()

        # Initialize receiver
        self.receiver = Pose3DReceiver(port=receiver_port)

        # Temporal smoother
        self.smoother = TemporalSmoother(alpha=0.3)

        # Result queue
        self._results: deque[PoseResult] = deque(maxlen=100)
        self._latest_result: PoseResult | None = None
        self._latest_fused: FusedResult | None = None
        self._lock = threading.Lock()
        self._running = False
        self._thread: threading.Thread | None = None

        # Callbacks
        self._on_result_callbacks: list = []

    def on_result(self, callback) -> None:
        """Register a callback for new pose results.

        callback(result: PoseResult) will be called from the pipeline thread.
        """
        self._on_result_callbacks.append(callback)

    def start(self) -> None:
        """Start the receiver and processing pipeline."""
        self.receiver.start()
        self._running = True
        self._thread = threading.Thread(target=self._pipeline_loop, daemon=True)
        self._thread.start()
        print(f"[Pose3D Pipeline] Started (window={self.window_duration_ms}ms, "
              f"interval={self.inference_interval_ms}ms)")

    def stop(self) -> None:
        """Stop the pipeline and receiver."""
        self._running = False
        if self._thread:
            self._thread.join(timeout=3.0)
        self.receiver.stop()
        print("[Pose3D Pipeline] Stopped")

    def _pipeline_loop(self) -> None:
        """Main processing loop."""
        while self._running:
            t_start = time.time()

            try:
                result = self._process_one()
                if result:
                    with self._lock:
                        self._results.append(result)
                        self._latest_result = result
                    for cb in self._on_result_callbacks:
                        try:
                            cb(result)
                        except Exception as e:
                            print(f"[Pose3D Pipeline] Callback error: {e}")
            except Exception as e:
                print(f"[Pose3D Pipeline] Processing error: {e}")

            # Sleep to maintain target inference rate
            elapsed = time.time() - t_start
            sleep_time = max(0, self.inference_interval_ms / 1000.0 - elapsed)
            if sleep_time > 0:
                time.sleep(sleep_time)

    def _process_one(self) -> PoseResult | None:
        """Process one window of CSI data."""
        window = self.receiver.get_window(
            duration_ms=self.window_duration_ms,
            min_frames=3,
        )
        if window is None:
            return None

        # Convert to model input tensors
        tensor_dict = window.to_tensor_input(
            num_subcarriers=self.num_subcarriers,
            time_steps=self.time_steps,
        )

        if not tensor_dict:
            return None

        # Build list of tensors per node
        node_ids = sorted(tensor_dict.keys())
        node_tensors = []
        for nid in node_ids:
            arr = tensor_dict[nid]  # (2, subcarriers, time_steps)
            tensor = torch.from_numpy(arr).unsqueeze(0)  # (1, 2, S, T)
            node_tensors.append(tensor)

        # Run inference
        t_infer_start = time.time()
        with torch.no_grad():
            output = self.model(node_tensors)
        t_infer_ms = (time.time() - t_infer_start) * 1000

        # Extract results
        keypoints_3d = output["keypoints_3d"][0].numpy()  # (17, 3)
        confidence = output["confidence"][0].numpy()  # (17,)
        posture_logits = output["posture_logits"][0]  # (4,)

        # Post-process: temporal smoothing
        keypoints_3d = self.smoother.smooth(keypoints_3d, confidence)

        # Posture classification
        posture_probs = torch.softmax(posture_logits, dim=0).numpy()
        posture_idx = int(posture_probs.argmax())
        posture = POSTURE_CLASSES[posture_idx]
        posture_conf = float(posture_probs[posture_idx])

        return PoseResult(
            timestamp=time.time(),
            keypoints_3d=keypoints_3d,
            confidence=confidence,
            posture=posture,
            posture_confidence=posture_conf,
            active_nodes=node_ids,
            inference_time_ms=t_infer_ms,
            total_frames_used=window.total_frames,
        )

    def get_latest(self) -> PoseResult | None:
        """Get the most recent pose estimation result."""
        with self._lock:
            return self._latest_result

    def get_latest_fused(self) -> FusedResult | None:
        """Get the most recent fused result (pose + vitals)."""
        with self._lock:
            return self._latest_fused

    def fuse_with_vitals(
        self,
        is_breathing: bool,
        respiratory_rate: float,
        heart_rate: float,
        vital_confidence: float,
    ) -> FusedResult:
        """Combine pose estimation with vital signs detection.

        Creates a human-readable description of the victim's state.
        """
        pose = self.get_latest()

        # Build description
        parts = []
        if pose:
            posture_emoji = {
                "standing": "🧍",
                "sitting": "🪑",
                "lying": "🛏️",
                "fallen": "⚠️",
            }
            emoji = posture_emoji.get(pose.posture, "❓")
            parts.append(f"{emoji} Postura: {pose.posture} ({pose.posture_confidence:.0%})")
            parts.append(f"Nodos activos: {len(pose.active_nodes)}")

        if is_breathing:
            parts.append(f"💨 Respiración: {respiratory_rate:.0f} RPM")
        if heart_rate > 0:
            parts.append(f"❤️ Pulso: {heart_rate:.0f} BPM")

        if not parts:
            parts.append("⏳ Esperando datos...")

        description = " | ".join(parts)

        result = FusedResult(
            pose=pose,
            is_breathing=is_breathing,
            respiratory_rate=respiratory_rate,
            heart_rate=heart_rate,
            vital_confidence=vital_confidence,
            description=description,
        )

        with self._lock:
            self._latest_fused = result

        return result
