/**
 * @file csi_engine.h
 * @brief Motor de captura y procesamiento de Channel State Information (CSI) en ESP32-S3.
 */

#pragma once

#include <Arduino.h>
#include <WiFiUdp.h>
#include "vitalfi_packet.h"
#include "wifi_config.h"

class CsiEngine {
public:
    static CsiEngine& getInstance();

    bool init();
    void loop();
    
    // Estadísticas
    uint32_t getPacketsSent() const { return _packetsSent; }
    uint32_t getCsiFramesCaptured() const { return _csiFramesCaptured; }
    float getSampleRateHz() const { return _currentRateHz; }
    int8_t getLastRssi() const { return _lastRssi; }
    uint8_t getActiveSubcarriers() const { return _lastSubcarrierCount; }
    uint8_t getLatestSpectrum(float* out, uint8_t max_count) const;

    // Enviar paquete VitalFi UDP
    void sendVitalFiFrame(const vitalfi_header_t& hdr, const vitalfi_subcarrier_t* subcarriers, uint8_t count);

private:
    CsiEngine();
    ~CsiEngine() = default;

    WiFiUDP _udp;
    IPAddress _broadcastIp;
    uint32_t _seqNumber;
    uint32_t _packetsSent;
    uint32_t _csiFramesCaptured;
    float _currentRateHz;
    int8_t _lastRssi;
    uint8_t _lastSubcarrierCount;

    uint32_t _lastPingMs;
    uint32_t _lastStatsMs;
    uint32_t _framesInLastSec;

    void sendTrafficStimulus();
    void updateRateStatistics();
};
