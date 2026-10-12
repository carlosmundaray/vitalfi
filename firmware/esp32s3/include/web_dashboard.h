/**
 * @file web_dashboard.h
 * @brief Servidor Web integrado en el ESP32-S3 para diagnóstico y visualización en tiempo real.
 * 
 * Permite acceder desde el navegador de tu teléfono o PC a:
 * http://192.168.4.1 (Modo AP)
 */

#pragma once

#include <Arduino.h>
#include <WebServer.h>

class WebDashboard {
public:
    static WebDashboard& getInstance();

    void init();
    void loop();

private:
    WebDashboard();
    ~WebDashboard() = default;

    WebServer _server;

    void setupRoutes();
    void handleRoot();
    void handleApiTelemetry();
    void handleApiSubcarriers();
};
