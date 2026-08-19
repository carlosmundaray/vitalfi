"""
==============================================================================
 VitalFi - Desplegador Automático 1-Click para Routers OpenWrt
==============================================================================
Permite configurar cualquier router con OpenWrt en segundos desde tu PC.

Uso:
  python deploy_openwrt.py [IP_ROUTER] [PASSWORD] [USUARIO]

Ejemplo:
  python deploy_openwrt.py
  python deploy_openwrt.py 192.168.1.1 mi_password_router root
"""

import sys
import os
import time
import getpass

if hasattr(sys.stdout, 'reconfigure'):
    try:
        sys.stdout.reconfigure(encoding='utf-8', errors='replace')
        sys.stderr.reconfigure(encoding='utf-8', errors='replace')
    except Exception:
        pass

try:
    import paramiko
except ImportError:
    print("[!] Instalando dependencia paramiko...")
    import subprocess
    subprocess.check_call([sys.executable, "-m", "pip", "install", "paramiko"])
    import paramiko

SETUP_SCRIPT_SH = r'''#!/bin/sh
# VitalFi - Instalador Universal para Routers OpenWrt
echo "================================================================="
echo "   📡 VitalFi - Configuración Automática de Router OpenWrt"
echo "================================================================="

# 1. Detectar automáticamente la radio Wi-Fi de 2.4 GHz
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
    WIFI_2G="radio0"
fi
echo "[+] Radio 2.4 GHz detectada: $WIFI_2G"

# 2. Configurar el Punto de Acceso (AP) para VitalFi
uci set wireless.$WIFI_2G.disabled='0'
uci -q set wireless.$WIFI_2G.channel='6'

IFACE_NAME="default_$WIFI_2G"
if ! uci -q get wireless.$IFACE_NAME >/dev/null; then
    IFACE_NAME=$(uci add wireless wifi-iface)
fi

uci set wireless.$IFACE_NAME.device="$WIFI_2G"
uci set wireless.$IFACE_NAME.network='lan'
uci set wireless.$IFACE_NAME.mode='ap'
uci set wireless.$IFACE_NAME.ssid='VitalFi-AP'
uci set wireless.$IFACE_NAME.encryption='psk2'
uci set wireless.$IFACE_NAME.key='vitalfi2026'
uci set wireless.$IFACE_NAME.isolate='0'
uci set wireless.$IFACE_NAME.disabled='0'

uci commit wireless
wifi reload 2>/dev/null || true
echo "[+] Wi-Fi 'VitalFi-AP' (WPA2 Protegida) configurado y activado."

# 3. Crear el script de streaming en /usr/bin/vitalfi-stream
cat << '__EOF_STREAM__' > /usr/bin/vitalfi-stream
#!/bin/sh
PORT=5500
TARGET_IP="$1"

if [ -z "$TARGET_IP" ]; then
    TARGET_IP=$(awk '{print $3}' /tmp/dhcp.leases 2>/dev/null | tail -n 1)
fi

if [ -z "$TARGET_IP" ]; then
    echo "[!] Esperando cliente..."
    while [ -z "$TARGET_IP" ]; do
        sleep 2
        TARGET_IP=$(awk '{print $3}' /tmp/dhcp.leases 2>/dev/null | tail -n 1)
    done
fi

echo "[+] Transmitiendo telemetria VitalFi a $TARGET_IP:$PORT"

seq=0
while true; do
    seq=$(( (seq + 1) % 2147483647 ))
    
    raw_sig=$(iw dev 2>/dev/null | grep -A 2 Interface | awk '{print $2}' | while read iface; do
        iw dev "$iface" station dump 2>/dev/null | grep 'signal:' | head -n 1 | awk '{print $2}'
    done | head -n 1)
    
    if [ -z "$raw_sig" ]; then
        raw_sig="-65"
    fi
    
    rssi_byte=$(awk -v s="$raw_sig" 'BEGIN { v = int(s + 100); if (v < 0) v = 0; if (v > 255) v = 255; printf "%c", v }')
    
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
__EOF_STREAM__
chmod +x /usr/bin/vitalfi-stream

# 4. Servicio procd de auto-arranque (/etc/init.d/vitalfi)
cat << '__EOF_PROCD__' > /etc/init.d/vitalfi
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
__EOF_PROCD__
chmod +x /etc/init.d/vitalfi
/etc/init.d/vitalfi enable
/etc/init.d/vitalfi restart 2>/dev/null || true

echo "================================================================="
echo "   ✅ ROUTER CONFIGURADO Y TRANSMITIENDO EXITOSAMENTE"
echo "================================================================="
'''

def main():
    print("=" * 65)
    print("   🚀 VitalFi - Desplegador Automático para Routers OpenWrt")
    print("=" * 65)
    
    ip = sys.argv[1] if len(sys.argv) > 1 else input("IP del Router [default: 192.168.1.1]: ").strip() or "192.168.1.1"
    password = sys.argv[2] if len(sys.argv) > 2 else getpass.getpass("Password SSH del Router (root): ").strip()
    user = sys.argv[3] if len(sys.argv) > 3 else "root"
    
    print(f"\n[*] Conectando por SSH a {user}@{ip}...")
    
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    
    try:
        client.connect(ip, username=user, password=password, timeout=8)
        print("[+] ¡Conexión SSH exitosa!")
        
        print("[*] Aplicando configuración de red, Wi-Fi 2.4 GHz y servicio VitalFi...")
        install_cmd = f"cat << '__EOF_SETUP__'> /tmp/setup_vitalfi.sh\n{SETUP_SCRIPT_SH}\n__EOF_SETUP__\nchmod +x /tmp/setup_vitalfi.sh\n/tmp/setup_vitalfi.sh\nrm -f /tmp/setup_vitalfi.sh"
        
        stdin, stdout, stderr = client.exec_command(install_cmd, timeout=30)
        output = stdout.read().decode('utf-8', errors='ignore')
        err = stderr.read().decode('utf-8', errors='ignore')
        
        print("\n" + output)
        if err and "Warning" not in err:
            print("[!] Avisos:", err)
            
        print("[+] Router listo para usar con la app VitalFi.")
        client.close()
        
    except Exception as e:
        print(f"\n[❌] Error al configurar el router: {e}")
        print("[*] Verifica que estés conectado al router por cable o Wi-Fi y que la IP/contraseña sean correctas.")

if __name__ == '__main__':
    main()
