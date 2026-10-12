/**
 * @file vitalfi_packet.h
 * @brief Estructura de trama binaria UDP VitalFi v2 para transmisión de CSI y Telemetría RF.
 * 
 * Cumple al 100% con la especificación deserializada en CsiUdpCollector.kt de VitalFi Android.
 */

#pragma once

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#define VITALFI_UDP_PORT         5500
#define VITALFI_HEADER_SIZE      24
#define VITALFI_MAGIC_LO         0x46   /* 'F' (Little Endian byte 14) */
#define VITALFI_MAGIC_HI         0x56   /* 'V' (Little Endian byte 15) */
#define VITALFI_MAGIC            0x5646 /* 'VF' */

#define VITALFI_MAX_SUBCARRIERS  128    /* 64 para 20 MHz (LLTF/HT-LTF) o hasta 128 */

/* Flags de protocolo (Byte 5) */
#define VITALFI_FLAG_REAL_CSI    0x01   /* CSI real capturado por hardware */
#define VITALFI_FLAG_HAS_NOISE   0x02   /* Ruido de fondo válido */
#define VITALFI_FLAG_HAS_RATE    0x04   /* Tasa MCS válida */
#define VITALFI_FLAG_HAS_SURVEY  0x08   /* Estadísticas de canal válidas */
#define VITALFI_FLAG_RSSI_ONLY   0x10   /* Modo solo RSSI sin subportadoras */

/**
 * @brief Cabecera de 24 bytes alineada a byte exacto para VitalFi v2
 */
#pragma pack(push, 1)
typedef struct {
    uint32_t seq;           /* [0..3] Número de secuencia incremental */
    uint8_t  raw_rssi;      /* [4] RSSI en formato unsigned (rssi_dbm + 100) */
    uint8_t  flags;         /* [5] Flags de estado (REAL_CSI, etc.) */
    uint8_t  noise_floor;   /* [6] Piso de ruido (noise_dbm + 100) */
    uint8_t  chan_util;     /* [7] % Utilización de canal (0..100) */
    uint8_t  mcs_rate;      /* [8] Índice MCS / Tasa Wi-Fi */
    uint8_t  station_cnt;   /* [9] Cantidad de clientes conectados */
    uint8_t  snr;           /* [10] SNR estimado en dB */
    uint16_t chan_freq;     /* [11..12] Frecuencia central en MHz (ej: 2437) */
    uint8_t  bw;            /* [13] Ancho de banda en MHz (20 o 40) */
    uint16_t magic;         /* [14..15] Número mágico 0x5646 ('VF') */
    uint8_t  num_sc;        /* [16] Cantidad de subportadoras OFDM adjuntas */
    uint8_t  node_id;       /* [17] ID del nodo sensor ESP32 (1..255) */
    uint32_t timestamp_ms;  /* [18..21] Uptime en milisegundos */
    uint16_t reserved;      /* [22..23] Reservado (0x0000) */
} vitalfi_header_t;

/**
 * @brief Estructura de una subportadora CSI (Componentes Real e Imaginaria I/Q)
 */
typedef struct {
    int16_t real;           /* Componente I (en fase) */
    int16_t imag;           /* Componente Q (en cuadratura) */
} vitalfi_subcarrier_t;

#pragma pack(pop)

#ifdef __cplusplus
}
#endif
