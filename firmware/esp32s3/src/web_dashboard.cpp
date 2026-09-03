/**
 * @file web_dashboard.cpp
 * @brief Interfaz web ligera y moderna embebida para monitoreo de CSI en tiempo real.
 */

#include "web_dashboard.h"
#include "csi_engine.h"
#include <WiFi.h>

static const char INDEX_HTML[] PROGMEM = R"rawliteral(
<!DOCTYPE html>
<html lang="es">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>VitalFi — ESP32-S3 CSI Dashboard</title>
    <style>
        :root {
            --bg: #0b0f19;
            --card-bg: rgba(20, 27, 45, 0.85);
            --accent: #00f2fe;
            --accent-green: #00ff88;
            --text-main: #f0f4f8;
            --text-muted: #8a99ad;
            --border: rgba(0, 242, 254, 0.2);
        }
        * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; }
        body { background: var(--bg); color: var(--text-main); padding: 1.5rem; min-height: 100vh; }
        .header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 1.5rem; border-bottom: 1px solid var(--border); padding-bottom: 1rem; }
        .logo { font-size: 1.5rem; font-weight: 800; background: linear-gradient(135deg, var(--accent), var(--accent-green)); -webkit-background-clip: text; -webkit-text-fill-color: transparent; }
        .badge { background: rgba(0, 255, 136, 0.15); color: var(--accent-green); border: 1px solid var(--accent-green); padding: 0.3rem 0.8rem; border-radius: 999px; font-size: 0.8rem; font-weight: 600; }
        .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 1rem; margin-bottom: 1.5rem; }
        .card { background: var(--card-bg); border: 1px solid var(--border); border-radius: 12px; padding: 1.2rem; backdrop-filter: blur(10px); }
        .card h3 { font-size: 0.85rem; color: var(--text-muted); text-transform: uppercase; margin-bottom: 0.5rem; }
        .card .value { font-size: 1.8rem; font-weight: 700; color: var(--text-main); }
        .card .unit { font-size: 0.9rem; color: var(--accent); margin-left: 0.3rem; }
        .chart-card { background: var(--card-bg); border: 1px solid var(--border); border-radius: 12px; padding: 1.2rem; }
        .chart-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 1rem; }
        .bars { display: flex; align-items: flex-end; height: 180px; gap: 3px; background: rgba(0,0,0,0.3); padding: 10px; border-radius: 8px; overflow-x: auto; }
        .bar { flex: 1; min-width: 4px; background: linear-gradient(to top, var(--accent), var(--accent-green)); border-radius: 2px 2px 0 0; transition: height 0.1s ease; }
        .footer { text-align: center; margin-top: 2rem; color: var(--text-muted); font-size: 0.8rem; }
    </style>
</head>
<body>
    <div class="header">
        <div class="logo">📡 VitalFi ESP32-S3</div>
        <div class="badge" id="status-badge">🟢 EN VIVO (CSI UDP: 5500)</div>
    </div>

    <div class="grid">
        <div class="card">
            <h3>Tasa de Muestreo</h3>
            <div class="value"><span id="val-rate">0.0</span><span class="unit">Hz</span></div>
        </div>
        <div class="card">
            <h3>Potencia RSSI</h3>
            <div class="value"><span id="val-rssi">0</span><span class="unit">dBm</span></div>
        </div>
        <div class="card">
            <h3>Subportadoras OFDM</h3>
            <div class="value"><span id="val-sc">0</span><span class="unit">canales</span></div>
        </div>
        <div class="card">
            <h3>Clientes Wi-Fi</h3>
            <div class="value"><span id="val-clients">0</span><span class="unit">dispositivos</span></div>
        </div>
    </div>

    <div class="chart-card">
        <div class="chart-header">
            <h3>Espectro de Amplitud CSI en Tiempo Real</h3>
            <small style="color: var(--text-muted);">64 Subportadoras OFDM (20 MHz)</small>
        </div>
        <div class="bars" id="bars-container"></div>
    </div>

    <div class="footer">
        VitalFi Open Source Project &copy; 2026 — Detección de Signos Vitales por Wi-Fi
    </div>

    <script>
        const container = document.getElementById('bars-container');
        const NUM_BARS = 64;
        const bars = [];
        for (let i = 0; i < NUM_BARS; i++) {
            const b = document.createElement('div');
            b.className = 'bar';
            b.style.height = '10%';
            container.appendChild(b);
            bars.push(b);
        }

        async function updateTelemetry() {
            try {
                const res = await fetch('/api/telemetry');
                if (res.ok) {
                    const data = await res.json();
                    document.getElementById('val-rate').textContent = data.rate.toFixed(1);
                    document.getElementById('val-rssi').textContent = data.rssi;
                    document.getElementById('val-sc').textContent = data.subcarriers;
                    document.getElementById('val-clients').textContent = data.clients;

                    if (data.spectrum && Array.isArray(data.spectrum)) {
                        for (let i = 0; i < Math.min(bars.length, data.spectrum.length); i++) {
                            const pct = Math.min(100, Math.max(5, data.spectrum[i] * 3.0));
                            bars[i].style.height = pct + '%';
                        }
                    }
                }
            } catch (e) {}
        }
        setInterval(updateTelemetry, 150);
    </script>
</body>
</html>
)rawliteral";

WebDashboard& WebDashboard::getInstance() {
    static WebDashboard instance;
    return instance;
}

WebDashboard::WebDashboard() : _server(80) {}

void WebDashboard::init() {
    setupRoutes();
    _server.begin();
    Serial.println("[HTTP] Dashboard Web iniciado en http://192.168.4.1 (puerto 80)");
}

void WebDashboard::setupRoutes() {
    _server.on("/", HTTP_GET, [this]() { handleRoot(); });
    _server.on("/api/telemetry", HTTP_GET, [this]() { handleApiTelemetry(); });
}

void WebDashboard::handleRoot() {
    _server.send_P(200, "text/html", INDEX_HTML);
}

void WebDashboard::handleApiTelemetry() {
    CsiEngine& engine = CsiEngine::getInstance();
    
    float spectrum[64];
    uint8_t count = engine.getLatestSpectrum(spectrum, 64);

    String json = "{";
    json += "\"rate\":" + String(engine.getSampleRateHz(), 1) + ",";
    json += "\"rssi\":" + String(engine.getLastRssi()) + ",";
    json += "\"subcarriers\":" + String(engine.getActiveSubcarriers()) + ",";
    json += "\"clients\":" + String(WiFi.softAPgetStationNum()) + ",";
    json += "\"pkts\":" + String(engine.getPacketsSent()) + ",";
    json += "\"uptime\":" + String(millis() / 1000) + ",";
    json += "\"spectrum\":[";
    for (uint8_t i = 0; i < count; i++) {
        json += String(spectrum[i], 1);
        if (i < count - 1) json += ",";
    }
    json += "]}";

    _server.send(200, "application/json", json);
}

void WebDashboard::loop() {
    _server.handleClient();
}
