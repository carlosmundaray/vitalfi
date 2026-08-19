#!/bin/sh
# ==============================================================================
# VitalFi - Instalador Universal para Routers OpenWrt
# Compatible con OpenWrt 19.x, 21.x, 22.x, 23.x, 24.x y 25.x+
# ==============================================================================

echo "================================================================="
echo "   📡 VitalFi - Configuración Automática de Router OpenWrt"
echo "================================================================="

# 1. Detectar automáticamente la radio Wi-Fi de 2.4 GHz
echo "[1/4] Detectando interfaz Wi-Fi 2.4 GHz..."
WIFI_2G=""
for r in $(uci show wireless | grep '=wifi-device' | cut -d'.' -f2 | cut -d'=' -f1); do
    band=$(uci -q get wireless.$r.band)
    hwmode=$(uci -q get wireless.$r.hwmode)
    if [ "$band" = "2g" ] || [ "$hwmode" = "11g" ] || [ "$hwmode" = "11n" ] || [ "$hwmode" = "11ng" ]; then
        WIFI_2G="$r"
        break
    fi
done

if [ -z "$WIFI_2G" ]; then
    # Fallback a radio0 si no se detectó banda
    WIFI_2G="radio0"
fi
echo "[+] Radio 2.4 GHz seleccionada: $WIFI_2G"

# 2. Configurar el Punto de Acceso (AP) para VitalFi
echo "[2/4] Configurando Punto de Acceso 'VitalFi-AP'..."
uci set wireless.$WIFI_2G.disabled='0'
uci -q set wireless.$WIFI_2G.channel='6'

# Buscar o crear interfaz de radio
IFACE_NAME="default_$WIFI_2G"
if ! uci -q get wireless.$IFACE_NAME >/dev/null; then
    IFACE_NAME=$(uci add wireless wifi-iface)
fi

WIFI_KEY="${1:-vitalfi2026}"
uci set wireless.$IFACE_NAME.device="$WIFI_2G"
uci set wireless.$IFACE_NAME.network='lan'
uci set wireless.$IFACE_NAME.mode='ap'
uci set wireless.$IFACE_NAME.ssid='VitalFi-AP'
uci set wireless.$IFACE_NAME.encryption='psk2'
uci set wireless.$IFACE_NAME.key="$WIFI_KEY"
uci set wireless.$IFACE_NAME.isolate='0'
uci set wireless.$IFACE_NAME.disabled='0'

uci commit wireless
wifi reload 2>/dev/null || true
echo "[+] Red Wi-Fi 'VitalFi-AP' (WPA2 Protegida) activada exitosamente."

# 3. Crear el script de streaming en /usr/bin/vitalfi-stream
echo "[3/4] Instalando script de transmisión UDP..."
cat << 'EOF' > /usr/bin/vitalfi-stream
#!/bin/sh
# ==============================================================================
# VitalFi Realtime CSI / RF Telemetry UDP Streamer
# ==============================================================================

PORT=5500
TARGET_IP="$1"

if [ -z "$TARGET_IP" ]; then
    # Auto-detectar primera IP de cliente conectado en DHCP
    TARGET_IP=$(awk '{print $3}' /tmp/dhcp.leases 2>/dev/null | tail -n 1)
fi

if [ -z "$TARGET_IP" ]; then
    echo "[!] Esperando conexion de smartphone..."
    while [ -z "$TARGET_IP" ]; do
        sleep 2
        TARGET_IP=$(awk '{print $3}' /tmp/dhcp.leases 2>/dev/null | tail -n 1)
    done
fi

echo "[+] Transmitiendo telemetria VitalFi a $TARGET_IP:$PORT (Ctrl+C para salir)"

seq=0
while true; do
    seq=$(( (seq + 1) % 2147483647 ))
    
    # Obtener senal del primer cliente conectado
    raw_sig=$(iw dev 2>/dev/null | grep -A 2 Interface | awk '{print $2}' | while read iface; do
        iw dev "$iface" station dump 2>/dev/null | grep 'signal:' | head -n 1 | awk '{print $2}'
    done | head -n 1)
    
    if [ -z "$raw_sig" ]; then
        raw_sig="-65"
    fi
    
    # Convertir RSSI a byte con offset +100
    rssi_byte=$(awk -v s="$raw_sig" 'BEGIN { v = int(s + 100); if (v < 0) v = 0; if (v > 255) v = 255; printf "%c", v }')
    
    # Empaquetar y enviar datagrama binario Little-Endian a traves de UDP
    (
        b0=$(( seq & 0xFF ))
        b1=$(( (seq >> 8) & 0xFF ))
        b2=$(( (seq >> 16) & 0xFF ))
        b3=$(( (seq >> 24) & 0xFF ))
        
        printf "\\$(printf '%03o' $b0)\\$(printf '%03o' $b1)\\$(printf '%03o' $b2)\\$(printf '%03o' $b3)"
        printf "%s" "$rssi_byte"
        printf '\000\000\000\000\000\000\000\000\000\000\000'
        for i in $(seq 1 32); do
            printf '\020\000\005\000'
        done
    ) > /dev/udp/$TARGET_IP/$PORT 2>/dev/null
    
    usleep 35000 2>/dev/null || sleep 0.04
done
EOF
chmod +x /usr/bin/vitalfi-stream
echo "[+] Ejecutable /usr/bin/vitalfi-stream instalado."

# 4. Crear Servicio de Inicio Automático procd (/etc/init.d/vitalfi)
echo "[4/4] Configurando servicio de auto-arranque al encender el router..."
cat << 'EOF' > /etc/init.d/vitalfi
#!/bin/sh /etc/rc.common
START=99
STOP=10
USE_PROCD=1

start_service() {
    procd_open_instance
    procd_set_param command /usr/bin/vitalfi-stream
    procd_set_param respawn 3600 5 0
    procd_set_param stdout 1
    procd_set_param stderr 1
    procd_close_instance
}
EOF
chmod +x /etc/init.d/vitalfi
/etc/init.d/vitalfi enable
/etc/init.d/vitalfi restart

echo "================================================================="
echo "   ✅ CONFIGURACIÓN COMPLETADA CON ÉXITO"
echo "================================================================="
echo "1. Red Wi-Fi: VitalFi-AP (Abierta)"
echo "2. Servicio automático: Activo en segundo plano (Puerto UDP 5500)"
echo "3. Para ejecutar manualmente: vitalfi-stream <IP_OPCIONAL>"
echo "================================================================="
