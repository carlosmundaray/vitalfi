"""
==============================================================================
VitalFi — ESP32-S3 Hardware CSI Live Packet Inspector & Tester (UDP 5500)
==============================================================================

Herramienta de diagnóstico y visualización en tiempo real para verificar
la recepción de matrices CSI transmitidas por el ESP32-S3.

Uso:
  python python/esp32_csi_listener.py [PORT]
==============================================================================
"""

import sys
import socket
import struct
import math
import time
from collections import deque

DEFAULT_PORT = 5500
HEADER_SIZE = 24
MAGIC_EXPECTED = 0x5646  # 'VF' en Little Endian (0x46, 0x56)


def run_listener(port=DEFAULT_PORT):
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    
    # En Windows/Linux enlazar a 0.0.0.0 para recibir broadcast y unicast
    sock.bind(("", port))
    
    print("=" * 70)
    print(f"  VitalFi — Receptor de Telemetría y Matrices CSI (ESP32-S3)")
    print(f"  Escuchando en socket UDP: 0.0.0.0:{port}")
    print("=" * 70)
    print("Esperando tramas desde el ESP32-S3...")

    pkt_count = 0
    start_time = time.time()
    last_stat_time = time.time()
    recent_amplitudes = deque(maxlen=100)

    try:
        while True:
            data, addr = sock.recvfrom(4096)
            now = time.time()
            pkt_count += 1

            if len(data) < HEADER_SIZE:
                continue

            # Desempaquetar cabecera de 24 bytes (Little Endian)
            # Formato:
            # I: seq (uint32)
            # B: raw_rssi (uint8)
            # B: flags (uint8)
            # B: noise_floor (uint8)
            # B: chan_util (uint8)
            # B: mcs_rate (uint8)
            # B: station_cnt (uint8)
            # B: snr (uint8)
            # H: chan_freq (uint16)
            # B: bw (uint8)
            # H: magic (uint16)
            # B: num_sc (uint8)
            # B: node_id (uint8)
            # I: timestamp_ms (uint32)
            # H: reserved (uint16)
            (
                seq,
                raw_rssi,
                flags,
                noise_floor,
                chan_util,
                mcs_rate,
                station_cnt,
                snr,
                chan_freq,
                bw,
                magic,
                num_sc,
                node_id,
                ts_ms,
                reserved,
            ) = struct.unpack("<IBBBBBBBHBHBBIH", data[:HEADER_SIZE])

            if magic != MAGIC_EXPECTED:
                # Paquete con formato desconocido o ping de texto
                continue

            real_rssi = raw_rssi - 100
            real_noise = noise_floor - 100
            is_real_csi = (flags & 0x01) != 0

            # Desempaquetar subportadoras (cada subportadora: 2 x int16 = 4 bytes)
            subcarrier_bytes = data[HEADER_SIZE:]
            subcarriers_count = min(num_sc, len(subcarrier_bytes) // 4)
            amplitudes = []
            phases = []

            for i in range(subcarriers_count):
                r, img = struct.unpack_from("<hh", subcarrier_bytes, i * 4)
                amp = math.sqrt(r * r + img * img)
                phase = math.atan2(img, r)
                amplitudes.append(amp)
                phases.append(phase)

            if amplitudes:
                recent_amplitudes.append(sum(amplitudes) / len(amplitudes))

            # Imprimir estadísticas cada ~1 segundo
            if now - last_stat_time >= 1.0:
                elapsed = now - last_stat_time
                fps = pkt_count / elapsed
                pkt_count = 0
                last_stat_time = now

                avg_amp = (sum(amplitudes) / len(amplitudes)) if amplitudes else 0.0
                bar = "#" * int(min(30, max(1, avg_amp / 2.0)))

                print(
                    f"[ESP32-S3 -> {addr[0]}] "
                    f"Seq: {seq:6d} | "
                    f"Rate: {fps:4.1f} Hz | "
                    f"RSSI: {real_rssi:3d} dBm | "
                    f"SNR: {snr:2d} dB | "
                    f"Subportadoras: {subcarriers_count:2d} | "
                    f"Nivel: [{bar:<30}]"
                )

    except KeyboardInterrupt:
        print("\nDeteniendo inspector CSI...")
    finally:
        sock.close()


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_PORT
    run_listener(port)
