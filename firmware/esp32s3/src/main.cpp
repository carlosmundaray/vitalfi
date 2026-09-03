/**
 * ==============================================================================
 * VitalFi v2.0 - Firmware ESP32-S3 Hardware CSI Sensor
 * ==============================================================================
 * 
 * Captura matrices de canal OFDM (CSI: Amplitud + Fase) directamente del
 * hardware de radio Wi-Fi 802.11n del ESP32-S3 y las empaqueta en tramas UDP
 * VitalFi v2 hacia la aplicación Android.
 * 
 * Modos de Operación:
 *   - MODO AP (Por defecto): Crea "VitalFi-Rescue-AP". Tu teléfono se conecta
 *     directamente al ESP32 sin requerir routers ni Internet.
 *   - MODO STA: Se conecta a una red Wi-Fi existente o Hotspot del teléfono.
 * 
 * Copyright (C) 2026 VitalFi Project — GPLv3
 * ==============================================================================
 */

#include <Arduino.h>
#include <WiFi.h>
#include "wifi_config.h"
#include "csi_engine.h"
#include "web_dashboard.h"

static uint32_t s_lastLogMs = 0;
static uint32_t s_lastLedToggleMs = 0;
static bool s_ledState = false;

void setupWifi() {
#if VITALFI_MODE_AP_DEFAULT == 1
    Serial.println("\n[WIFI] Configurando Modo Access Point (Punto de Rescate Autónomo)...");
    WiFi.mode(WIFI_AP);
    
    // Configurar IP fija para el SoftAP
    IPAddress local_ip(192, 168, 4, 1);
    IPAddress gateway(192, 168, 4, 1);
    IPAddress subnet(255, 255, 255, 0);
    WiFi.softAPConfig(local_ip, gateway, subnet);

    bool ok = WiFi.softAP(
        VITALFI_AP_SSID, 
        VITALFI_AP_PASSWORD, 
        VITALFI_AP_CHANNEL, 
        0, 
        VITALFI_AP_MAX_CONN
    );

    if (ok) {
        Serial.printf("[WIFI] Red AP iniciada con exito!\n");
        Serial.printf("       SSID:     %s\n", VITALFI_AP_SSID);
        Serial.printf("       Password: %s\n", VITALFI_AP_PASSWORD);
        Serial.printf("       IP AP:    %s\n", WiFi.softAPIP().toString().c_str());
        Serial.printf("       Canal:    %d\n", VITALFI_AP_CHANNEL);
    } else {
        Serial.println("[WIFI] ERROR iniciando SoftAP");
    }

#else
    Serial.println("\n[WIFI] Configurando Modo Estacion (STA)...");
    WiFi.mode(WIFI_STA);
    WiFi.begin(VITALFI_STA_SSID, VITALFI_STA_PASSWORD);

    Serial.printf("[WIFI] Conectando a %s...", VITALFI_STA_SSID);
    int attempts = 0;
    while (WiFi.status() != WL_CONNECTED && attempts < 30) {
        delay(500);
        Serial.print(".");
        attempts++;
    }

    if (WiFi.status() == WL_CONNECTED) {
        Serial.printf("\n[WIFI] Conectado! IP asignada: %s\n", WiFi.localIP().toString().c_str());
    } else {
        Serial.println("\n[WIFI] No se pudo conectar a la red STA. Reiniciando...");
        ESP.restart();
    }
#endif
}

void setup() {
    Serial.begin(115200);
    delay(1000);

    pinMode(VITALFI_STATUS_LED_PIN, OUTPUT);
    digitalWrite(VITALFI_STATUS_LED_PIN, LOW);

    Serial.println("==================================================");
    Serial.println("   VitalFi ESP32-S3 Hardware CSI Radar Sensor     ");
    Serial.println("   Version 2.0.0 (Protocolo Enhanced UDP v2)      ");
    Serial.println("==================================================");

    // 1. Iniciar Wi-Fi
    setupWifi();

    // 2. Iniciar motor CSI en ESP32-S3
    if (!CsiEngine::getInstance().init()) {
        Serial.println("[FATAL] Error inicializando motor CSI. Reiniciando...");
        delay(3000);
        ESP.restart();
    }

    // 3. Iniciar Dashboard Web de diagnóstico en el puerto 80
    WebDashboard::getInstance().init();

    Serial.println("[INIT] ESP32-S3 Listo y transmitiendo matrices CSI a VitalFi.");
    Serial.println("--------------------------------------------------");
}

void loop() {
    // 1. Ejecutar ciclo de bombeo CSI y transmisión UDP
    CsiEngine::getInstance().loop();

    // 2. Atender peticiones del servidor Web embebido
    WebDashboard::getInstance().loop();

    uint32_t now = millis();

    // 3. Parpadeo del LED de estado (Heartbeat a 2 Hz)
    if (now - s_lastLedToggleMs >= 250) {
        s_lastLedToggleMs = now;
        s_ledState = !s_ledState;
        digitalWrite(VITALFI_STATUS_LED_PIN, s_ledState ? HIGH : LOW);
    }

    // 4. Telemetría periódica por monitor serie USB
    if (now - s_lastLogMs >= 2000) {
        s_lastLogMs = now;
        
        CsiEngine& engine = CsiEngine::getInstance();
        uint8_t stations = WiFi.softAPgetStationNum();

        Serial.printf("[TELEMETRY] CSI Rate: %.1f Hz | Pkts Enviados: %u | Frames CSI: %u | RSSI: %d dBm | Subportadoras: %u | Clientes: %u\n",
            engine.getSampleRateHz(),
            engine.getPacketsSent(),
            engine.getCsiFramesCaptured(),
            engine.getLastRssi(),
            engine.getActiveSubcarriers(),
            stations
        );
    }
}
