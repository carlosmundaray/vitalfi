/**
 * @file csi_engine.cpp
 * @brief Implementación del motor de captura Wi-Fi CSI para ESP32-S3.
 */

#include "csi_engine.h"
#include <WiFi.h>
#include <esp_wifi.h>
#include <esp_wifi_types.h>
#include <esp_system.h>
#include <freertos/FreeRTOS.h>
#include <freertos/queue.h>
#include <math.h>

static QueueHandle_t s_csi_queue = NULL;

typedef struct {
    int8_t rssi;
    int8_t noise_floor;
    uint8_t mcs;
    uint8_t channel;
    uint8_t num_subcarriers;
    uint32_t timestamp;
    vitalfi_subcarrier_t subcarriers[VITALFI_MAX_SUBCARRIERS];
} csi_queue_item_t;

/**
 * @brief Callback nativo de la capa PHY Wi-Fi de ESP-IDF para cada trama recibida
 */
static void IRAM_ATTR _esp_wifi_csi_cb(void *ctx, wifi_csi_info_t *data) {
    if (!data || !data->buf || data->len <= 0 || s_csi_queue == NULL) {
        return;
    }

    csi_queue_item_t item;
    item.rssi = data->rx_ctrl.rssi;
    item.noise_floor = data->rx_ctrl.noise_floor;
    item.mcs = data->rx_ctrl.rate;
    item.channel = data->rx_ctrl.channel;
    item.timestamp = millis();

    // En ESP32-S3, data->buf contiene pares de int8_t (I, Q) o int16_t
    // Para 802.11n 20 MHz (HT20) típicamente 64 o 52 subportadoras (128 bytes)
    int8_t *raw = (int8_t *)data->buf;
    int total_pairs = data->len / 2;
    if (total_pairs > VITALFI_MAX_SUBCARRIERS) {
        total_pairs = VITALFI_MAX_SUBCARRIERS;
    }
    item.num_subcarriers = total_pairs;

    for (int i = 0; i < total_pairs; i++) {
        // Formato estándar ESP32 CSI: [imag, real] o [real, imag]
        int16_t val_imag = (int16_t)raw[i * 2];
        int16_t val_real = (int16_t)raw[i * 2 + 1];

        item.subcarriers[i].real = val_real;
        item.subcarriers[i].imag = val_imag;
    }

    BaseType_t xHigherPriorityTaskWoken = pdFALSE;
    xQueueSendFromISR(s_csi_queue, &item, &xHigherPriorityTaskWoken);
    if (xHigherPriorityTaskWoken) {
        portYIELD_FROM_ISR();
    }
}

CsiEngine& CsiEngine::getInstance() {
    static CsiEngine instance;
    return instance;
}

CsiEngine::CsiEngine()
    : _seqNumber(0)
    , _packetsSent(0)
    , _csiFramesCaptured(0)
    , _currentRateHz(0.0f)
    , _lastRssi(-65)
    , _lastSubcarrierCount(0)
    , _lastPingMs(0)
    , _lastStatsMs(0)
    , _framesInLastSec(0)
{
}

bool CsiEngine::init() {
    Serial.println("[CSI] Inicializando motor de captura CSI en ESP32-S3...");

    // Crear cola para transferir datos desde la ISR del callback al loop principal
    if (s_csi_queue == NULL) {
        s_csi_queue = xQueueCreate(20, sizeof(csi_queue_item_t));
    }

    // Configuración del motor CSI de ESP-IDF
    wifi_csi_config_t csi_config;
    memset(&csi_config, 0, sizeof(wifi_csi_config_t));
    csi_config.lltf_en           = true;  // Legacy Long Training Field
    csi_config.htltf_en          = true;  // HT Long Training Field (802.11n)
    csi_config.stbc_htltf2_en    = true;  // Space-Time Block Code HT-LTF
    csi_config.ltf_merge_en      = true;  // Fusión de LTF para SNR óptimo
    csi_config.channel_filter_en = true;  // Filtro de canal activo
    csi_config.manu_scale        = false; // Escala automática de ganancia
    csi_config.shift             = false;

    esp_err_t err;
    err = esp_wifi_set_csi_config(&csi_config);
    if (err != ESP_OK) {
        Serial.printf("[CSI] ERROR esp_wifi_set_csi_config: %d\n", err);
        return false;
    }

    err = esp_wifi_set_csi_rx_cb(&_esp_wifi_csi_cb, NULL);
    if (err != ESP_OK) {
        Serial.printf("[CSI] ERROR esp_wifi_set_csi_rx_cb: %d\n", err);
        return false;
    }

    err = esp_wifi_set_csi(true);
    if (err != ESP_OK) {
        Serial.printf("[CSI] ERROR esp_wifi_set_csi(true): %d\n", err);
        return false;
    }

    if (WiFi.getMode() == WIFI_AP) {
        _broadcastIp = IPAddress(192, 168, 4, 255);
    } else {
        _broadcastIp = IPAddress(255, 255, 255, 255);
    }
    _udp.begin(VITALFI_UDP_TARGET_PORT);

    Serial.printf("[CSI] Motor CSI ACTIVO. Transmitiendo por UDP al puerto %d (Broadcast: %s)\n", 
                  VITALFI_UDP_TARGET_PORT, _broadcastIp.toString().c_str());
    return true;
}

void CsiEngine::sendVitalFiFrame(const vitalfi_header_t& hdr, const vitalfi_subcarrier_t* subcarriers, uint8_t count) {
    if (WiFi.getMode() == WIFI_AP && WiFi.softAPgetStationNum() == 0) {
        // En modo AP, no transmitir por red si aún no hay clientes conectados
        return;
    }

    size_t packet_size = sizeof(vitalfi_header_t) + (count * sizeof(vitalfi_subcarrier_t));
    uint8_t buffer[sizeof(vitalfi_header_t) + (VITALFI_MAX_SUBCARRIERS * sizeof(vitalfi_subcarrier_t))];

    memcpy(buffer, &hdr, sizeof(vitalfi_header_t));
    if (count > 0 && subcarriers != NULL) {
        memcpy(buffer + sizeof(vitalfi_header_t), subcarriers, count * sizeof(vitalfi_subcarrier_t));
    }

    _udp.beginPacket(_broadcastIp, VITALFI_UDP_TARGET_PORT);
    _udp.write(buffer, packet_size);
    _udp.endPacket();

    _packetsSent++;
}

void CsiEngine::sendTrafficStimulus() {
    // Solo enviar estímulo si hay estaciones conectadas
    if (WiFi.getMode() == WIFI_AP && WiFi.softAPgetStationNum() == 0) {
        return;
    }

    uint8_t ping[4] = {'V', 'F', 'P', 'G'};
    _udp.beginPacket(_broadcastIp, VITALFI_UDP_TARGET_PORT);
    _udp.write(ping, sizeof(ping));
    _udp.endPacket();
}

void CsiEngine::updateRateStatistics() {
    uint32_t now = millis();
    if (now - _lastStatsMs >= 1000) {
        float dt = (now - _lastStatsMs) / 1000.0f;
        _currentRateHz = _framesInLastSec / dt;
        _framesInLastSec = 0;
        _lastStatsMs = now;
    }
}

static float s_latestSpectrum[VITALFI_MAX_SUBCARRIERS];
static uint8_t s_latestSpectrumCount = 0;

uint8_t CsiEngine::getLatestSpectrum(float* out, uint8_t max_count) const {
    if (out == NULL || s_latestSpectrumCount == 0) return 0;
    uint8_t count = min(max_count, s_latestSpectrumCount);
    memcpy(out, s_latestSpectrum, count * sizeof(float));
    return count;
}

void CsiEngine::loop() {
    uint32_t now = millis();

    // 1. Estímulo de tráfico periódico
    uint32_t pingIntervalMs = 1000 / VITALFI_CSI_PING_HZ;
    if (now - _lastPingMs >= pingIntervalMs) {
        _lastPingMs = now;
        sendTrafficStimulus();
    }

    // 2. Procesar tramas CSI recibidas de la cola
    csi_queue_item_t item;
    while (xQueueReceive(s_csi_queue, &item, 0) == pdTRUE) {
        _csiFramesCaptured++;
        _framesInLastSec++;
        _lastRssi = item.rssi;
        _lastSubcarrierCount = item.num_subcarriers;

        // Guardar espectro de amplitudes para la web
        s_latestSpectrumCount = min((uint8_t)VITALFI_MAX_SUBCARRIERS, item.num_subcarriers);
        for (uint8_t i = 0; i < s_latestSpectrumCount; i++) {
            float r = (float)item.subcarriers[i].real;
            float im = (float)item.subcarriers[i].imag;
            s_latestSpectrum[i] = sqrtf(r * r + im * im);
        }

        // Construir cabecera VitalFi v2
        vitalfi_header_t hdr;
        memset(&hdr, 0, sizeof(hdr));

        hdr.seq = ++_seqNumber;
        hdr.raw_rssi = (uint8_t)(item.rssi + 100);
        hdr.flags = VITALFI_FLAG_REAL_CSI | VITALFI_FLAG_HAS_NOISE | VITALFI_FLAG_HAS_RATE;
        hdr.noise_floor = (uint8_t)(item.noise_floor + 100);
        hdr.chan_util = 25; // Promedio estimado de ocupación
        hdr.mcs_rate = item.mcs;
        hdr.station_cnt = WiFi.softAPgetStationNum();
        
        int snr_calc = (int)item.rssi - (int)item.noise_floor;
        hdr.snr = (uint8_t)(snr_calc > 0 ? (snr_calc > 255 ? 255 : snr_calc) : 0);

        // Frecuencia según canal (2.4 GHz: 2412 + 5 * (ch - 1))
        hdr.chan_freq = (uint16_t)(2407 + (item.channel * 5));
        hdr.bw = 20; // 20 MHz
        hdr.magic = VITALFI_MAGIC;
        hdr.num_sc = item.num_subcarriers;
        hdr.node_id = VITALFI_SENSOR_NODE_ID;
        hdr.timestamp_ms = now;
        hdr.reserved = 0x0000;

        // Enviar trama completa a la App Android por UDP
        sendVitalFiFrame(hdr, item.subcarriers, item.num_subcarriers);
    }

    updateRateStatistics();
}
