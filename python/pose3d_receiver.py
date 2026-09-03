"""
VitalFi — Pose3D UDP Receiver (Multi-Node CSI + Phase)

Receives CSI frames with sanitized phase data from one or more
ESP32-S3 nodes via UDP port 5502 and accumulates temporal windows
for the pose estimation pipeline.

Each node is identified by its node_id in the VitalFi packet header.
Frames are buffered per-node and assembled into synchronized windows
for batch inference.

Usage:
    receiver = Pose3DReceiver(port=5502)
    receiver.start()
    # ... in processing loop:
    window = receiver.get_window(duration_ms=300)
"""

import struct
import socket
import math
import time
import threading
from collections import defaultdict, deque
from dataclasses import dataclass, field

import numpy as np

# VitalFi packet constants (must match vitalfi_packet.h)
HEADER_SIZE = 24
MAGIC_EXPECTED = 0x5646  # 'VF'
FRAME_TYPE_POSE_CSI = 0x01
FLAG_HAS_PHASE = 0x20

# Phase subcarrier: 6 bytes (int16 real, int16 imag, int16 phase_san)
PHASE_SUBCARRIER_SIZE = 6
# Standard subcarrier: 4 bytes (int16 real, int16 imag)
STANDARD_SUBCARRIER_SIZE = 4


@dataclass
class CSIFrame:
    """A single CSI frame from one ESP32 node."""
    node_id: int
    seq: int
    timestamp_ms: int
    rssi: int
    snr: int
    channel: int
    num_subcarriers: int
    amplitude: np.ndarray       # shape: (num_subcarriers,)
    phase_sanitized: np.ndarray  # shape: (num_subcarriers,) — milliradians
    iq_real: np.ndarray         # shape: (num_subcarriers,)
    iq_imag: np.ndarray         # shape: (num_subcarriers,)
    recv_time: float = 0.0      # local receive timestamp


@dataclass
class CSIWindow:
    """A temporal window of CSI frames from all nodes, ready for inference."""
    node_ids: list[int]
    frames_per_node: dict[int, list[CSIFrame]]
    start_time_ms: int
    end_time_ms: int
    total_frames: int

    def to_tensor_input(self, num_subcarriers: int = 64, time_steps: int = 30) -> dict[int, np.ndarray]:
        """Convert to model input format.

        Returns:
            Dict mapping node_id → numpy array of shape (2, num_subcarriers, time_steps)
            Channel 0 = amplitude (normalized), Channel 1 = sanitized phase (normalized)
        """
        result = {}
        for node_id, frames in self.frames_per_node.items():
            amp_matrix = np.zeros((num_subcarriers, time_steps), dtype=np.float32)
            phase_matrix = np.zeros((num_subcarriers, time_steps), dtype=np.float32)

            n_frames = min(len(frames), time_steps)
            for t in range(n_frames):
                frame = frames[-(n_frames - t)]  # Most recent frames
                n_sc = min(frame.num_subcarriers, num_subcarriers)
                amp_matrix[:n_sc, t] = frame.amplitude[:n_sc]
                phase_matrix[:n_sc, t] = frame.phase_sanitized[:n_sc]

            # Normalize amplitude to [0, 1]
            amp_max = amp_matrix.max()
            if amp_max > 0:
                amp_matrix /= amp_max

            # Normalize phase from milliradians to [-1, 1]
            phase_matrix = phase_matrix / 3142.0

            # Stack: (2, subcarriers, time_steps)
            result[node_id] = np.stack([amp_matrix, phase_matrix], axis=0)

        return result


class Pose3DReceiver:
    """Threaded UDP receiver for VitalFi Pose3D CSI packets."""

    def __init__(
        self,
        port: int = 5502,
        max_buffer_frames: int = 300,
        bind_addr: str = "",
    ):
        self.port = port
        self.bind_addr = bind_addr
        self.max_buffer_frames = max_buffer_frames

        # Per-node circular buffer of CSI frames
        self._buffers: dict[int, deque[CSIFrame]] = defaultdict(
            lambda: deque(maxlen=max_buffer_frames)
        )
        self._lock = threading.Lock()
        self._running = False
        self._thread: threading.Thread | None = None
        self._stats = {
            "total_packets": 0,
            "valid_frames": 0,
            "invalid_magic": 0,
            "wrong_frame_type": 0,
        }

    def start(self) -> None:
        """Start the receiver thread."""
        if self._running:
            return
        self._running = True
        self._thread = threading.Thread(target=self._receive_loop, daemon=True)
        self._thread.start()
        print(f"[Pose3D Receiver] Listening on UDP port {self.port}")

    def stop(self) -> None:
        """Stop the receiver thread."""
        self._running = False
        if self._thread:
            self._thread.join(timeout=2.0)

    def _receive_loop(self) -> None:
        """Main receive loop running in a background thread."""
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        sock.settimeout(1.0)
        sock.bind((self.bind_addr, self.port))

        while self._running:
            try:
                data, addr = sock.recvfrom(4096)
                self._stats["total_packets"] += 1
                frame = self._parse_packet(data)
                if frame:
                    frame.recv_time = time.time()
                    with self._lock:
                        self._buffers[frame.node_id].append(frame)
                    self._stats["valid_frames"] += 1
            except socket.timeout:
                continue
            except Exception as e:
                print(f"[Pose3D Receiver] Error: {e}")

        sock.close()

    def _parse_packet(self, data: bytes) -> CSIFrame | None:
        """Parse a VitalFi Pose3D packet."""
        if len(data) < HEADER_SIZE:
            return None

        # Unpack 24-byte header (must match vitalfi_header_t)
        (
            seq, raw_rssi, flags, noise_floor, chan_util,
            mcs_rate, station_cnt, snr, chan_freq, bw,
            magic, num_sc, node_id, ts_ms,
            frame_type, frame_flags,
        ) = struct.unpack("<IBBBBBBBHBHBBIBB", data[:HEADER_SIZE])

        if magic != MAGIC_EXPECTED:
            self._stats["invalid_magic"] += 1
            return None

        real_rssi = raw_rssi - 100

        # Determine subcarrier format based on frame type / flags
        payload = data[HEADER_SIZE:]
        has_phase = (flags & FLAG_HAS_PHASE) != 0

        if has_phase:
            sc_size = PHASE_SUBCARRIER_SIZE  # 6 bytes per subcarrier
        else:
            sc_size = STANDARD_SUBCARRIER_SIZE  # 4 bytes per subcarrier

        actual_sc = min(num_sc, len(payload) // sc_size)
        if actual_sc == 0:
            return None

        amplitudes = np.zeros(actual_sc, dtype=np.float32)
        phases = np.zeros(actual_sc, dtype=np.float32)
        reals = np.zeros(actual_sc, dtype=np.float32)
        imags = np.zeros(actual_sc, dtype=np.float32)

        for i in range(actual_sc):
            offset = i * sc_size
            if has_phase:
                r, im, ph = struct.unpack_from("<hhh", payload, offset)
                phases[i] = float(ph)  # Already in milliradians
            else:
                r, im = struct.unpack_from("<hh", payload, offset)
                phases[i] = math.atan2(im, r) * 1000.0  # Convert to mrad

            reals[i] = float(r)
            imags[i] = float(im)
            amplitudes[i] = math.sqrt(r * r + im * im)

        return CSIFrame(
            node_id=node_id,
            seq=seq,
            timestamp_ms=ts_ms,
            rssi=real_rssi,
            snr=snr,
            channel=int((chan_freq - 2407) / 5) if chan_freq > 2400 else 0,
            num_subcarriers=actual_sc,
            amplitude=amplitudes,
            phase_sanitized=phases,
            iq_real=reals,
            iq_imag=imags,
        )

    def get_window(
        self,
        duration_ms: int = 300,
        min_frames: int = 5,
    ) -> CSIWindow | None:
        """Get a temporal window of CSI frames from all nodes.

        Args:
            duration_ms: Window duration in milliseconds.
            min_frames: Minimum frames required per node.

        Returns:
            CSIWindow if sufficient data is available, None otherwise.
        """
        with self._lock:
            if not self._buffers:
                return None

            now_ms = int(time.time() * 1000)
            cutoff_ms = now_ms - duration_ms

            window_frames: dict[int, list[CSIFrame]] = {}
            for node_id, buffer in self._buffers.items():
                recent = [f for f in buffer if f.recv_time * 1000 >= cutoff_ms]
                if len(recent) >= min_frames:
                    window_frames[node_id] = recent

            if not window_frames:
                return None

            total = sum(len(frames) for frames in window_frames.values())
            all_ts = [
                f.timestamp_ms
                for frames in window_frames.values()
                for f in frames
            ]

            return CSIWindow(
                node_ids=sorted(window_frames.keys()),
                frames_per_node=window_frames,
                start_time_ms=min(all_ts),
                end_time_ms=max(all_ts),
                total_frames=total,
            )

    def get_active_nodes(self) -> list[int]:
        """Get list of node IDs that have sent data recently (last 5s)."""
        cutoff = time.time() - 5.0
        with self._lock:
            return [
                node_id
                for node_id, buffer in self._buffers.items()
                if buffer and buffer[-1].recv_time >= cutoff
            ]

    @property
    def stats(self) -> dict:
        return dict(self._stats)


if __name__ == "__main__":
    # Quick test: receive and print stats
    receiver = Pose3DReceiver(port=5502)
    receiver.start()
    print("Receiving Pose3D CSI frames... Press Ctrl+C to stop.")
    try:
        while True:
            time.sleep(1.0)
            nodes = receiver.get_active_nodes()
            window = receiver.get_window(duration_ms=500)
            print(
                f"Active nodes: {nodes} | "
                f"Stats: {receiver.stats} | "
                f"Window: {window.total_frames if window else 0} frames"
            )
    except KeyboardInterrupt:
        receiver.stop()
        print("\nStopped.")
