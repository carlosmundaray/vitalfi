/**
 * @file wifi_config.h
 * @brief Configuración de conectividad y parámetros del sensor VitalFi ESP32-S3.
 */

#pragma once

#include <IPAddress.h>

// =============================================================================
// SELECCIÓN DE MODO DE OPERACIÓN
// =============================================================================
// 1 = MODO AP (Punto de Acceso Autónomo): El ESP32 crea la red "VitalFi-Rescue-AP".
//     Ideal para rescates donde NO hay router en el lugar del desastre.
//     Conecta tu teléfono Android directamente al Wi-Fi del ESP32.
//
// 0 = MODO ESTACIÓN (STA): El ESP32 se conecta al router local o al Hotspot
//     compartido desde tu teléfono Android.
#define VITALFI_MODE_AP_DEFAULT      1

// =============================================================================
// CREDENCIALES EN MODO AP (El ESP32 crea la red)
// =============================================================================
#define VITALFI_AP_SSID              "VitalFi-Rescue-AP"
#define VITALFI_AP_PASSWORD          "vitalfi2026"
#define VITALFI_AP_CHANNEL           6       // Canal 1, 6 u 11 recomendado
#define VITALFI_AP_MAX_CONN          4

// =============================================================================
// CREDENCIALES EN MODO ESTACIÓN (Si VITALFI_MODE_AP_DEFAULT = 0)
// =============================================================================
#define VITALFI_STA_SSID             "MiRouterO_Hotspot"
#define VITALFI_STA_PASSWORD         "password123"

// =============================================================================
// CONFIGURACIÓN DE RED Y TRANSMISIÓN UDP
// =============================================================================
#define VITALFI_UDP_TARGET_PORT      5500
#define VITALFI_SENSOR_NODE_ID       1       // Identificador único del sensor (1 a 255)

// Si es 1, envía por Broadcast (255.255.255.255) alcanzando automáticamente a cualquier
// app VitalFi conectada en la misma subred sin necesidad de fijar IPs manuales.
#define VITALFI_USE_BROADCAST        1

// =============================================================================
// FRECUENCIA DE MEDICIÓN Y ESTÍMULO DE TRÁFICO
// =============================================================================
// Intervalo de inyección de paquetes de ping para forzar capturas CSI continuas
// en Hz (20 a 50 paquetes/segundo recomendado para análisis de respiración).
#define VITALFI_CSI_PING_HZ          30

// Pin de LED de estado (DevKitC-1 usa GPIO 48 para WS2812 o GPIO 2 para LED estándar)
#define VITALFI_STATUS_LED_PIN       2
