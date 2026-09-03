"""
VitalFi — WiFi Pose Estimation 3D Model (Person-in-WiFi 3D Inspired)

Lightweight neural network for 3D human pose estimation from Wi-Fi CSI data.
Inspired by the Person-in-WiFi 3D architecture (CVPR 2024) but adapted for:
  - ESP32-S3 CSI (fewer subcarriers, noisier phase)
  - Real-time inference on CPU
  - Configurable number of receiver nodes (1-4)

Architecture:
    CSI Input [nodes × subcarriers × time × 2(amp+phase)]
    → Conv2D Spatial Encoder (per-node feature extraction)
    → Cross-Node Fusion (concatenation or attention)
    → Transformer Temporal Encoder (2 layers, 4 heads)
    → Dual Heads:
        - Keypoint Regressor: 17 COCO keypoints × 3 (x, y, z)
        - Posture Classifier: 4 classes (standing, sitting, lying, fallen)

References:
  - Person-in-WiFi 3D: End-to-End Multi-Person 3D Pose Estimation with Wi-Fi (CVPR 2024)
  - WhoFi: WiFi-Based Indoor Human Identification (CSI preprocessing)
"""

import math
import torch
import torch.nn as nn
import torch.nn.functional as F


# --- COCO 17 Keypoint Definitions ---
COCO_KEYPOINTS = [
    "nose", "left_eye", "right_eye", "left_ear", "right_ear",
    "left_shoulder", "right_shoulder", "left_elbow", "right_elbow",
    "left_wrist", "right_wrist", "left_hip", "right_hip",
    "left_knee", "right_knee", "left_ankle", "right_ankle",
]

# Skeleton connections for visualization (pairs of keypoint indices)
COCO_SKELETON = [
    (0, 1), (0, 2), (1, 3), (2, 4),           # Head
    (5, 6), (5, 7), (7, 9), (6, 8), (8, 10),  # Arms
    (5, 11), (6, 12), (11, 12),                 # Torso
    (11, 13), (13, 15), (12, 14), (14, 16),     # Legs
]

POSTURE_CLASSES = ["standing", "sitting", "lying", "fallen"]
NUM_KEYPOINTS = 17


class SpatialCSIEncoder(nn.Module):
    """Extracts spatial features from CSI amplitude+phase per node.

    Input: (batch, channels=2, subcarriers, time_steps)
    Output: (batch, hidden_dim, time_steps)
    """

    def __init__(self, in_channels: int = 2, hidden_dim: int = 64, num_subcarriers: int = 64):
        super().__init__()
        self.conv_blocks = nn.Sequential(
            # Block 1: (2, 64, T) → (32, 32, T)
            nn.Conv2d(in_channels, 32, kernel_size=(3, 1), padding=(1, 0)),
            nn.BatchNorm2d(32),
            nn.ReLU(),
            nn.MaxPool2d(kernel_size=(2, 1)),

            # Block 2: (32, 32, T) → (64, 16, T)
            nn.Conv2d(32, 64, kernel_size=(3, 1), padding=(1, 0)),
            nn.BatchNorm2d(64),
            nn.ReLU(),
            nn.MaxPool2d(kernel_size=(2, 1)),

            # Block 3: (64, 16, T) → (hidden_dim, 8, T)
            nn.Conv2d(64, hidden_dim, kernel_size=(3, 1), padding=(1, 0)),
            nn.BatchNorm2d(hidden_dim),
            nn.ReLU(),
            nn.AdaptiveAvgPool2d((1, None)),  # Collapse subcarrier dim → (hidden, 1, T)
        )

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        """
        Args:
            x: (batch, 2, subcarriers, time_steps) — amplitude + phase
        Returns:
            (batch, hidden_dim, time_steps)
        """
        out = self.conv_blocks(x)  # (B, H, 1, T)
        return out.squeeze(2)  # (B, H, T)


class CrossNodeFusion(nn.Module):
    """Fuses features from multiple ESP32 nodes.

    Supports 1-4 nodes. When only 1 node is available, passes through.
    When multiple nodes are present, uses learned attention weighting.
    """

    def __init__(self, hidden_dim: int = 64, max_nodes: int = 4):
        super().__init__()
        self.max_nodes = max_nodes
        self.hidden_dim = hidden_dim

        # Attention-based fusion: learn importance of each node
        self.node_attention = nn.Sequential(
            nn.Linear(hidden_dim, hidden_dim // 2),
            nn.ReLU(),
            nn.Linear(hidden_dim // 2, 1),
        )

        # Project concatenated features back to hidden_dim
        self.projection = nn.Linear(hidden_dim, hidden_dim)

    def forward(self, node_features: list[torch.Tensor]) -> torch.Tensor:
        """
        Args:
            node_features: List of tensors, each (batch, hidden_dim, T)
        Returns:
            (batch, T, hidden_dim) — fused features, ready for Transformer
        """
        if len(node_features) == 1:
            return node_features[0].permute(0, 2, 1)  # (B, T, H)

        # Stack: (B, num_nodes, H, T)
        stacked = torch.stack(node_features, dim=1)
        B, N, H, T = stacked.shape

        # Compute attention weights per node per timestep
        # Reshape to (B*T, N, H) for attention computation
        stacked_perm = stacked.permute(0, 3, 1, 2).reshape(B * T, N, H)
        attn_scores = self.node_attention(stacked_perm)  # (B*T, N, 1)
        attn_weights = F.softmax(attn_scores, dim=1)  # (B*T, N, 1)

        # Weighted sum: (B*T, H)
        fused = (stacked_perm * attn_weights).sum(dim=1)
        fused = self.projection(fused)

        return fused.reshape(B, T, H)


class TemporalTransformerEncoder(nn.Module):
    """Lightweight Transformer for temporal feature processing."""

    def __init__(
        self,
        d_model: int = 64,
        nhead: int = 4,
        num_layers: int = 2,
        dropout: float = 0.1,
    ):
        super().__init__()
        self.pos_encoding = SinusoidalPositionalEncoding(d_model, dropout=dropout)
        encoder_layer = nn.TransformerEncoderLayer(
            d_model=d_model,
            nhead=nhead,
            dim_feedforward=d_model * 4,
            dropout=dropout,
            batch_first=True,
        )
        self.transformer = nn.TransformerEncoder(encoder_layer, num_layers=num_layers)
        self.output_dim = d_model

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        """
        Args:
            x: (batch, seq_len, d_model)
        Returns:
            (batch, d_model) — global representation via mean pooling
        """
        x = self.pos_encoding(x)
        x = self.transformer(x)
        return x.mean(dim=1)  # Global average pooling


class SinusoidalPositionalEncoding(nn.Module):
    """Standard sinusoidal positional encoding."""

    def __init__(self, d_model: int, max_len: int = 2048, dropout: float = 0.1):
        super().__init__()
        self.dropout = nn.Dropout(p=dropout)
        pe = torch.zeros(max_len, d_model)
        position = torch.arange(0, max_len, dtype=torch.float).unsqueeze(1)
        div_term = torch.exp(
            torch.arange(0, d_model, 2).float() * (-math.log(10000.0) / d_model)
        )
        pe[:, 0::2] = torch.sin(position * div_term)
        if d_model > 1:
            pe[:, 1::2] = torch.cos(position * div_term[: d_model // 2])
        pe = pe.unsqueeze(0)
        self.register_buffer("pe", pe)

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        x = x + self.pe[:, : x.size(1)]
        return self.dropout(x)


class WiFiPoseEstimator(nn.Module):
    """Main model: CSI → 3D Pose + Posture Classification.

    Dual-head architecture:
      - Keypoint head: Regresses 17 × 3 (x, y, z) coordinates
      - Posture head: Classifies into 4 posture categories

    Designed for real-time CPU inference (~5ms per frame on modern laptop).
    """

    def __init__(
        self,
        num_subcarriers: int = 64,
        max_nodes: int = 4,
        hidden_dim: int = 64,
        transformer_layers: int = 2,
        transformer_heads: int = 4,
        dropout: float = 0.2,
        num_keypoints: int = NUM_KEYPOINTS,
        num_postures: int = len(POSTURE_CLASSES),
    ):
        super().__init__()
        self.num_keypoints = num_keypoints
        self.num_postures = num_postures
        self.hidden_dim = hidden_dim

        # Per-node spatial encoder
        self.spatial_encoder = SpatialCSIEncoder(
            in_channels=2,  # amplitude + phase
            hidden_dim=hidden_dim,
            num_subcarriers=num_subcarriers,
        )

        # Cross-node fusion
        self.node_fusion = CrossNodeFusion(
            hidden_dim=hidden_dim,
            max_nodes=max_nodes,
        )

        # Temporal transformer
        self.temporal_encoder = TemporalTransformerEncoder(
            d_model=hidden_dim,
            nhead=transformer_heads,
            num_layers=transformer_layers,
            dropout=dropout,
        )

        # --- Keypoint regression head ---
        self.keypoint_head = nn.Sequential(
            nn.Linear(hidden_dim, hidden_dim * 2),
            nn.ReLU(),
            nn.Dropout(dropout),
            nn.Linear(hidden_dim * 2, num_keypoints * 3),  # 17 × (x, y, z)
        )

        # --- Posture classification head ---
        self.posture_head = nn.Sequential(
            nn.Linear(hidden_dim, hidden_dim),
            nn.ReLU(),
            nn.Dropout(dropout),
            nn.Linear(hidden_dim, num_postures),
        )

        # --- Keypoint confidence head ---
        self.confidence_head = nn.Sequential(
            nn.Linear(hidden_dim, hidden_dim),
            nn.ReLU(),
            nn.Linear(hidden_dim, num_keypoints),
            nn.Sigmoid(),
        )

    def forward(
        self,
        csi_nodes: list[torch.Tensor],
    ) -> dict[str, torch.Tensor]:
        """
        Args:
            csi_nodes: List of tensors per node, each shape
                       (batch, 2, subcarriers, time_steps)
                       Channel 0 = amplitude, Channel 1 = sanitized phase

        Returns:
            Dictionary with:
              - 'keypoints_3d': (batch, 17, 3) — predicted 3D coordinates
              - 'posture_logits': (batch, 4) — posture class logits
              - 'confidence': (batch, 17) — per-keypoint confidence [0, 1]
        """
        # 1. Extract spatial features per node
        node_features = []
        for csi in csi_nodes:
            feat = self.spatial_encoder(csi)  # (B, H, T)
            node_features.append(feat)

        # 2. Fuse across nodes
        fused = self.node_fusion(node_features)  # (B, T, H)

        # 3. Temporal encoding
        temporal = self.temporal_encoder(fused)  # (B, H)

        # 4. Dual heads
        keypoints_flat = self.keypoint_head(temporal)  # (B, 17*3)
        keypoints_3d = keypoints_flat.reshape(-1, self.num_keypoints, 3)

        posture_logits = self.posture_head(temporal)  # (B, 4)
        confidence = self.confidence_head(temporal)  # (B, 17)

        return {
            "keypoints_3d": keypoints_3d,
            "posture_logits": posture_logits,
            "confidence": confidence,
        }

    def predict_posture(self, posture_logits: torch.Tensor) -> list[str]:
        """Convert posture logits to class names."""
        indices = posture_logits.argmax(dim=-1).tolist()
        if isinstance(indices, int):
            indices = [indices]
        return [POSTURE_CLASSES[i] for i in indices]


def create_model(
    num_subcarriers: int = 64,
    max_nodes: int = 4,
    pretrained_path: str | None = None,
) -> WiFiPoseEstimator:
    """Factory function to create and optionally load a pretrained model.

    Args:
        num_subcarriers: Number of OFDM subcarriers (64 for ESP32 20MHz).
        max_nodes: Maximum number of ESP32 receiver nodes.
        pretrained_path: Path to .pt checkpoint file (optional).

    Returns:
        Initialized WiFiPoseEstimator model.
    """
    model = WiFiPoseEstimator(
        num_subcarriers=num_subcarriers,
        max_nodes=max_nodes,
    )

    if pretrained_path:
        state_dict = torch.load(pretrained_path, map_location="cpu", weights_only=True)
        model.load_state_dict(state_dict)
        print(f"[Pose3D] Loaded pretrained model from {pretrained_path}")

    # Count parameters
    total_params = sum(p.numel() for p in model.parameters())
    trainable_params = sum(p.numel() for p in model.parameters() if p.requires_grad)
    print(f"[Pose3D] Model created: {total_params:,} params ({trainable_params:,} trainable)")
    print(f"[Pose3D] Estimated size: {total_params * 4 / 1024 / 1024:.1f} MB (float32)")

    return model
