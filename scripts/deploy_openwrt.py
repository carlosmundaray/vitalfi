"""
==============================================================================
 VitalFi - Desplegador Automático 1-Click para Routers OpenWrt
==============================================================================
Permite configurar cualquier router con OpenWrt en segundos desde tu PC.

Uso:
  python scripts/deploy_openwrt.py [IP_ROUTER] [PASSWORD] [USUARIO]

Ejemplo:
  python scripts/deploy_openwrt.py
  python scripts/deploy_openwrt.py 192.168.1.1 mi_password_router root
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

if command -v lua >/dev/null 2>&1 && lua -e 'require "nixio"' >/dev/null 2>&1; then
    exec lua - "$PORT" "$TARGET_IP" << 'LUA_EOF'
local nixio = require "nixio"
local port = tonumber(arg[1]) or 5500
local target_override = arg[2]

local sock = nixio.socket("inet", "dgram")
sock:setopt("socket", "broadcast", 1)
local seq = 0

while true do
    seq = (seq + 1) % 2147483647
    local raw_sig = -65
    local f = io.popen("iw dev 2>/dev/null | grep -A 2 Interface | awk '{print $2}' | while read iface; do iw dev \"$iface\" station dump 2>/dev/null | grep 'signal:' | head -n 1 | awk '{print $2}'; done | head -n 1")
    if f then
        local line = f:read("*l")
        f:close()
        if line and tonumber(line) then raw_sig = tonumber(line) end
    end

    local rssi_byte = math.floor(raw_sig + 100)
    if rssi_byte < 0 then rssi_byte = 0 end
    if rssi_byte > 255 then rssi_byte = 255 end

    local b0 = seq % 256
    local b1 = math.floor(seq / 256) % 256
    local b2 = math.floor(seq / 65536) % 256
    local b3 = math.floor(seq / 16777216) % 256

    local header = string.char(b0, b1, b2, b3, rssi_byte) .. string.rep("\0", 11)
    local subcarriers = {}
    for i = 1, 32 do subcarriers[i] = string.char(16, 0, 5, 0) end
    local payload = header .. table.concat(subcarriers)

    pcall(function() sock:sendto(payload, "255.255.255.255", port) end)
    pcall(function() sock:sendto(payload, "192.168.1.255", port) end)

    if target_override and #target_override > 0 then
        pcall(function() sock:sendto(payload, target_override, port) end)
    else
        local lf = io.open("/tmp/dhcp.leases", "r")
        if lf then
            for l in lf:lines() do
                local ip = l:match("%S+%s+%S+%s+(%S+)")
                if ip then pcall(function() sock:sendto(payload, ip, port) end) end
            end
            lf:close()
        end
    end

    nixio.nanosleep(0, 35000000)
end
LUA_EOF
fi

BCAST="192.168.1.255"
seq=0
while true; do
    seq=$(( (seq + 1) % 2147483647 ))
    raw_sig=$(iw dev 2>/dev/null | grep -A 2 Interface | awk '{print $2}' | while read iface; do
        iw dev "$iface" station dump 2>/dev/null | grep 'signal:' | head -n 1 | awk '{print $2}'
    done | head -n 1)
    [ -z "$raw_sig" ] && raw_sig="-65"
    rssi_val=$(( raw_sig + 100 ))
    [ $rssi_val -lt 0 ] && rssi_val=0
    [ $rssi_val -gt 255 ] && rssi_val=255

    b0=$(( seq & 0xFF ))
    b1=$(( (seq >> 8) & 0xFF ))
    b2=$(( (seq >> 16) & 0xFF ))
    b3=$(( (seq >> 24) & 0xFF ))

    PAYLOAD=$(
        printf "\\$(printf '%03o' $b0)\\$(printf '%03o' $b1)\\$(printf '%03o' $b2)\\$(printf '%03o' $b3)"
        printf "\\$(printf '%03o' $rssi_val)"
        printf '\000\000\000\000\000\000\000\000\000\000\000'
        for i in $(seq 1 32); do printf '\020\000\005\000'; done
    )

    printf "%s" "$PAYLOAD" | nc -u -w1 255.255.255.255 $PORT 2>/dev/null || true
    printf "%s" "$PAYLOAD" | nc -u -w1 $BCAST $PORT 2>/dev/null || true
    usleep 40000 2>/dev/null || sleep 0.05
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
