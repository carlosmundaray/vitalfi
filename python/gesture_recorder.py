"""
VitalFi — Wi-Fi + Webcam Ground Truth Gesture Dataset Recorder

Records simultaneous Wi-Fi CSI packets from ESP32-S3 and 3D pose keypoints
from a webcam using MediaPipe Pose to create a synchronized training dataset
(exactly how the Person-in-WiFi 3D paper collected its dataset).

Usage:
    python gesture_recorder.py [--output my_gestures.npz] [--duration 30]
"""

import sys
import time
import argparse
import numpy as np

try:
    import cv2
    HAS_CV2 = True
except ImportError:
    HAS_CV2 = False

try:
    import mediapipe as mp
    HAS_MEDIAPIPE = True
except ImportError:
    HAS_MEDIAPIPE = False

from pose3d_receiver import Pose3DReceiver


def record_dataset(output_file: str = "wifi_gesture_dataset.npz", duration_sec: int = 30):
    print("=" * 70)
    print("  VitalFi — Recolector de Dataset Wi-Fi CSI + Cámara 3D")
    print("  (Metodología exacta de Person-in-WiFi 3D)")
    print(f"  Grabando durante {duration_sec} segundos...")
    print("=" * 70)

    receiver = Pose3DReceiver(port=5502)
    receiver.start()

    csi_amplitudes = []
    csi_phases = []
    poses_3d = []
    timestamps = []

    cap = None
    mp_pose = None
    pose_tracker = None

    if HAS_CV2 and HAS_MEDIAPIPE:
        cap = cv2.VideoCapture(0)
        mp_pose = mp.solutions.pose
        pose_tracker = mp_pose.Pose(min_detection_confidence=0.6, min_tracking_confidence=0.6)
        print("[Cam] Cámara web detectada y activada con MediaPipe 3D.")
    else:
        print("[WARN] OpenCV o MediaPipe no instalados. Grabando solo datos CSI.")

    start_time = time.time()
    frame_idx = 0

    try:
        while time.time() - start_time < duration_sec:
            time.sleep(0.033)  # ~30 FPS
            window = receiver.get_window(duration_ms=100, min_frames=1)

            current_pose = np.zeros((17, 3), dtype=np.float32)

            if cap and cap.isOpened() and pose_tracker:
                ret, frame = cap.read()
                if ret:
                    rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
                    results = pose_tracker.process(rgb)
                    if results.pose_world_landmarks:
                        # Extraer 17 keypoints de interés (COCO format)
                        lm = results.pose_world_landmarks.landmark
                        # Mapear landmarks de MediaPipe a COCO 17
                        coco_indices = [0, 2, 5, 7, 8, 11, 12, 13, 14, 15, 16, 23, 24, 25, 26, 27, 28]
                        for idx, c_idx in enumerate(coco_indices):
                            if c_idx < len(lm):
                                current_pose[idx] = [lm[c_idx].x, lm[c_idx].z, -lm[c_idx].y]

                    cv2.putText(
                        frame,
                        f"Grabando CSI: {int(duration_sec - (time.time() - start_time))}s",
                        (20, 40),
                        cv2.FONT_HERSHEY_SIMPLEX,
                        0.8,
                        (0, 255, 0),
                        2,
                    )
                    cv2.imshow("VitalFi Ground Truth Recorder", frame)
                    if cv2.waitKey(1) & 0xFF == ord('q'):
                        break

            if window:
                tensor_dict = window.to_tensor_input(num_subcarriers=64, time_steps=1)
                if tensor_dict:
                    first_node = list(tensor_dict.keys())[0]
                    amp = tensor_dict[first_node][0, :, 0]
                    phase = tensor_dict[first_node][1, :, 0]

                    csi_amplitudes.append(amp)
                    csi_phases.append(phase)
                    poses_3d.append(current_pose)
                    timestamps.append(time.time())
                    frame_idx += 1

            if frame_idx % 30 == 0:
                print(f"Muestras sincronizadas capturadas: {frame_idx}")

    finally:
        receiver.stop()
        if cap:
            cap.release()
            cv2.destroyAllWindows()

    if csi_amplitudes:
        np.savez_compressed(
            output_file,
            csi_amplitude=np.array(csi_amplitudes, dtype=np.float32),
            csi_phase=np.array(csi_phases, dtype=np.float32),
            keypoints_3d=np.array(poses_3d, dtype=np.float32),
            timestamps=np.array(timestamps, dtype=np.float64),
        )
        print(f"\n[OK] Dataset guardado exitosamente en: {output_file} ({len(csi_amplitudes)} frames)")
    else:
        print("[ERROR] No se recibieron tramas CSI del ESP32-S3 durante la grabación.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", default="wifi_gesture_dataset.npz")
    parser.add_argument("--duration", type=int, default=30)
    args = parser.parse_args()
    record_dataset(args.output, args.duration)
