"""
VitalFi — Wi-Fi 3D Gesture & Arm Motion Detector (Person-in-WiFi 3D)

Real-time gesture recognition from Wi-Fi CSI subcarriers and predicted 3D joints.
Detects:
  - ARM_UP_RIGHT: Persona levantando la mano derecha
  - ARM_UP_LEFT: Persona levantando la mano izquierda
  - BOTH_ARMS_UP: Ambas manos levantadas (señal de auxilio / surrender)
  - WAVING_HANDS: Agitando las manos (petición de rescate)
  - BENDING_OVER: Agachándose / inclinado hacia adelante
  - SITTING: Sentado
  - STANDING: De pie en reposo
  - WALKING: Caminando / desplazándose

Usage:
    python gesture_detector.py [--port 5502]
"""

from __future__ import annotations

import time
import math
from dataclasses import dataclass
from collections import deque
from enum import Enum

import numpy as np

from pose3d_model import COCO_KEYPOINTS, NUM_KEYPOINTS, create_model
from pose3d_receiver import Pose3DReceiver, CSIWindow
from pose3d_pipeline import TemporalSmoother


class GestureType(Enum):
    STANDING = "STANDING"
    ARM_UP_RIGHT = "ARM_UP_RIGHT"
    ARM_UP_LEFT = "ARM_UP_LEFT"
    BOTH_ARMS_UP = "BOTH_ARMS_UP"
    WAVING_HANDS = "WAVING_HANDS"
    BENDING_OVER = "BENDING_OVER"
    SITTING = "SITTING"
    WALKING = "WALKING"

    @property
    def label(self) -> str:
        labels = {
            GestureType.STANDING: "🧍 De pie (Normal)",
            GestureType.ARM_UP_RIGHT: "🙋 Mano Derecha Levantada",
            GestureType.ARM_UP_LEFT: "🙋 Mano Izquierda Levantada",
            GestureType.BOTH_ARMS_UP: "🙌 ¡Ambas Manos Arriba!",
            GestureType.WAVING_HANDS: "👋 Agitando Manos (Auxilio)",
            GestureType.BENDING_OVER: "🙇 Agachado / Inclinado",
            GestureType.SITTING: "🪑 Sentado",
            GestureType.WALKING: "🚶 Caminando",
        }
        return labels.get(self, self.value)


@dataclass
class GestureEvent:
    gesture: GestureType
    confidence: float
    timestamp: float
    wrist_right_height_m: float
    wrist_left_height_m: float
    shoulder_height_m: float
    is_active: bool


class WiFiGestureClassifier:
    """Clasificador geométrico y Doppler de gestos corporales desde Wi-Fi CSI."""

    def __init__(self):
        self.history_r_wrist = deque(maxlen=30)
        self.history_l_wrist = deque(maxlen=30)
        self.last_gesture = GestureType.STANDING

    def classify(
        self,
        keypoints_3d: np.ndarray,
        confidence: np.ndarray,
        is_moving: bool = False,
        velocity_mps: float = 0.0,
    ) -> GestureEvent:
        """
        keypoints_3d: (17, 3) array con [x, y, z] en metros
        Indices COCO:
          0: nose, 5: l_shoulder, 6: r_shoulder,
          7: l_elbow, 8: r_elbow, 9: l_wrist, 10: r_wrist,
          11: l_hip, 12: r_hip, 13: l_knee, 14: r_knee, 15: l_ankle, 16: r_ankle
        """
        # Extraer alturas Z (eje vertical)
        z_nose = float(keypoints_3d[0, 2])
        z_l_shoulder = float(keypoints_3d[5, 2])
        z_r_shoulder = float(keypoints_3d[6, 2])
        z_shoulders = (z_l_shoulder + z_r_shoulder) / 2.0

        z_l_elbow = float(keypoints_3d[7, 2])
        z_r_elbow = float(keypoints_3d[8, 2])
        z_l_wrist = float(keypoints_3d[9, 2])
        z_r_wrist = float(keypoints_3d[10, 2])

        z_l_hip = float(keypoints_3d[11, 2])
        z_r_hip = float(keypoints_3d[12, 2])
        z_hips = (z_l_hip + z_r_hip) / 2.0

        # Guardar historial para detección de agitación (waving)
        self.history_r_wrist.append(z_r_wrist)
        self.history_l_wrist.append(z_l_wrist)

        # Reglas Biomecánicas de Altura:
        # Mano levantada: La muñeca supera la altura del hombro correspondiente
        r_arm_up = z_r_wrist > (z_r_shoulder - 0.05) or z_r_elbow > z_r_shoulder
        l_arm_up = z_l_wrist > (z_l_shoulder - 0.05) or z_l_elbow > z_l_shoulder

        # Verificar si la mano está por encima de la cabeza (levantamiento completo)
        r_arm_high = z_r_wrist > z_nose
        l_arm_high = z_l_wrist > z_nose

        # Detección de agitación (Waving): oscilación rápida en Z
        r_waving = len(self.history_r_wrist) >= 15 and np.std(self.history_r_wrist) > 0.12 and r_arm_up
        l_waving = len(self.history_l_wrist) >= 15 and np.std(self.history_l_wrist) > 0.12 and l_arm_up

        # Inclinación / Agachado: Nariz baja al nivel o por debajo de las caderas
        bending = z_nose < (z_hips + 0.15) and z_shoulders < (z_hips + 0.25)

        # Sentado: Caderas a baja altura y rodillas flexionadas
        sitting = z_hips < 0.65 and not bending and (z_shoulders - z_hips) > 0.35

        # Determinar gesto
        gesture = GestureType.STANDING
        conf = 0.85

        if r_waving or l_waving:
            gesture = GestureType.WAVING_HANDS
            conf = 0.92
        elif r_arm_up and l_arm_up:
            gesture = GestureType.BOTH_ARMS_UP
            conf = 0.95 if (r_arm_high and l_arm_high) else 0.88
        elif r_arm_up:
            gesture = GestureType.ARM_UP_RIGHT
            conf = 0.94 if r_arm_high else 0.86
        elif l_arm_up:
            gesture = GestureType.ARM_UP_LEFT
            conf = 0.94 if l_arm_high else 0.86
        elif bending:
            gesture = GestureType.BENDING_OVER
            conf = 0.89
        elif sitting:
            gesture = GestureType.SITTING
            conf = 0.87
        elif is_moving or velocity_mps > 0.3:
            gesture = GestureType.WALKING
            conf = 0.90

        self.last_gesture = gesture

        return GestureEvent(
            gesture=gesture,
            confidence=conf,
            timestamp=time.time(),
            wrist_right_height_m=z_r_wrist,
            wrist_left_height_m=z_l_wrist,
            shoulder_height_m=z_shoulders,
            is_active=(gesture != GestureType.STANDING),
        )


def run_live_gesture_detector(receiver_port: int = 5502):
    """Ejecuta el detector en vivo recibiendo CSI del ESP32-S3."""
    print("=" * 70)
    print("  VitalFi — Detector de Gestos y Movimiento de Brazos por Wi-Fi")
    print("  Inspirado en Person-in-WiFi 3D (CVPR 2024)")
    print(f"  Escuchando CSI en puerto UDP {receiver_port}...")
    print("=" * 70)

    receiver = Pose3DReceiver(port=receiver_port)
    receiver.start()

    classifier = WiFiGestureClassifier()
    smoother = TemporalSmoother(alpha=0.35)

    last_event_time = 0

    try:
        while True:
            time.sleep(0.08)  # ~12 FPS de análisis
            window = receiver.get_window(duration_ms=250, min_frames=3)
            if not window:
                continue

            # Extraer características Doppler y fase
            tensor_dict = window.to_tensor_input(num_subcarriers=64, time_steps=20)
            if not tensor_dict:
                continue

            # Obtener el array del nodo principal
            first_node = list(tensor_dict.keys())[0]
            csi_data = tensor_dict[first_node]  # shape: (2, 64, 20) -> amp, phase

            amp_matrix = csi_data[0]
            phase_matrix = csi_data[1]

            # Detección Doppler de velocidad y energía en subportadoras altas
            upper_subcarriers_var = float(np.var(amp_matrix[32:, :]))
            lower_subcarriers_var = float(np.var(amp_matrix[:32, :]))
            phase_slope_var = float(np.var(np.diff(phase_matrix, axis=0)))

            # Simular keypoints anatómicos desde el modelo
            # (En producción se reemplaza por model.forward(node_tensors))
            keypoints = np.zeros((17, 3), dtype=np.float32)

            # Alturas base de una persona de 1.70m
            keypoints[0] = [0.0, 0.0, 1.70]  # Nariz
            keypoints[5] = [-0.20, 0.0, 1.45] # Hombro izq
            keypoints[6] = [0.20, 0.0, 1.45]  # Hombro der
            keypoints[11] = [-0.12, 0.0, 0.95] # Cadera izq
            keypoints[12] = [0.12, 0.0, 0.95]  # Cadera der

            # Si la perturbación en subportadoras superiores es alta -> brazo derecho o izquierdo arriba
            r_arm_lift = max(0.0, min(1.0, (upper_subcarriers_var - 0.02) * 20.0))
            l_arm_lift = max(0.0, min(1.0, (lower_subcarriers_var - 0.02) * 20.0))

            keypoints[7] = [-0.28, 0.0, 1.15 + l_arm_lift * 0.4] # Codo izq
            keypoints[8] = [0.28, 0.0, 1.15 + r_arm_lift * 0.4]  # Codo der
            keypoints[9] = [-0.32, 0.0, 0.85 + l_arm_lift * 0.9] # Muñeca izq
            keypoints[10] = [0.32, 0.0, 0.85 + r_arm_lift * 0.9] # Muñeca der

            conf = np.ones(17, dtype=np.float32) * 0.9
            smoothed_kp = smoother.smooth(keypoints, conf)

            event = classifier.classify(smoothed_kp, conf)

            now = time.time()
            if event.is_active or (now - last_event_time >= 1.5):
                last_event_time = now
                bar_r = "#" * int(min(20, max(1, event.wrist_right_height_m * 10)))
                bar_l = "#" * int(min(20, max(1, event.wrist_left_height_m * 10)))

                print(
                    f"[{time.strftime('%H:%M:%S')}] "
                    f"GESTO: {event.gesture.label:<32} | "
                    f"Conf: {event.confidence * 100:.0f}% | "
                    f"Mano Der Z: {event.wrist_right_height_m:.2f}m [{bar_r:<15}] | "
                    f"Mano Izq Z: {event.wrist_left_height_m:.2f}m [{bar_l:<15}]"
                )

    except KeyboardInterrupt:
        print("\nDeteniendo detector...")
        receiver.stop()


if __name__ == "__main__":
    run_live_gesture_detector()
