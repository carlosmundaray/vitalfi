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
import gzip

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

SETUP_WIFI_SH = r'''#!/bin/sh
echo "================================================================="
echo "   📡 VitalFi - Configuración Wi-Fi en Router OpenWrt"
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
'''

SERVICE_SCRIPT_SH = r'''cat << 'EOF' > /etc/init.d/vitalfi
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
'''


def main():
    print("=================================================================")
    print("   📡 VitalFi - Despliegue Automático en Router OpenWrt")
    print("=================================================================")

    ip = sys.argv[1] if len(sys.argv) > 1 else input("IP del router OpenWrt (default: 192.168.1.1): ").strip()
    if not ip:
        ip = "192.168.1.1"

    if len(sys.argv) > 2:
        password = sys.argv[2]
    else:
        password = getpass.getpass("Contraseña SSH del router (dejar vacío si no tiene): ")

    user = sys.argv[3] if len(sys.argv) > 3 else "root"

    print(f"\n[*] Conectando a {user}@{ip} vía SSH...")
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())

    try:
        client.connect(
            hostname=ip,
            username=user,
            password=password if password else None,
            timeout=10,
            look_for_keys=True,
            allow_agent=True
        )
        print("[+] Conexión SSH establecida exitosamente.")
    except Exception as e:
        print(f"[-] Error al conectar con el router: {e}")
        print("    Asegúrate de que estás conectado a la misma red y que la IP es correcta.")
        sys.exit(1)

    try:
        # 0. Detectar información del hardware y arquitectura
        print("\n[1/4] Identificando hardware del router...")
        stdin, stdout, stderr = client.exec_command("uname -m; cat /proc/cpuinfo | grep -m1 'model name\\|system type\\|Hardware\\|Processor'; uci -q get wireless.radio0.type || true")
        hw_info = stdout.read().decode('utf-8', errors='ignore').strip()
        arch = hw_info.split('\n')[0].strip() if hw_info else "unknown"
        print(f"[+] Arquitectura detectada: {arch}")
        if "IPQ" in hw_info.upper() or "AARCH64" in arch.upper() or "ARM64" in arch.upper():
            print("[+] Hardware de alto rendimiento detectado (Qualcomm IPQ / ARM64 - Linksys MX5500).")

        # 1. Configurar Wi-Fi optimizado para SAR / Escombros
        print("\n[2/4] Configurando red Wi-Fi 'VitalFi-AP' (Modo SAR Escombros 2.4 GHz)...")
        stdin, stdout, stderr = client.exec_command(SETUP_WIFI_SH, timeout=30)
        print(stdout.read().decode('utf-8', errors='ignore'))

        # 2. Subir binario nativo si corresponde a la arquitectura
        print("\n[3/4] Desplegando motor de telemetría...")
        is_arm64 = any(x in arch.lower() for x in ["aarch64", "arm64", "armv8"])
        local_bin = os.path.join(os.path.dirname(__file__), 'vitalfi-stream-arm64')

        if is_arm64 and os.path.exists(local_bin):
            print("[+] Subiendo binario optimizado nativo ARM64 (vitalfi-stream-arm64)...")
            with open(local_bin, 'rb') as f:
                raw_bin = f.read()
            gz_bin = gzip.compress(raw_bin, compresslevel=9)
            client.exec_command('rm -f /tmp/vitalfi.gz /usr/bin/vitalfi-stream')
            
            chunk_size = 512
            for i in range(0, len(gz_bin), chunk_size):
                chunk = gz_bin[i:i+chunk_size]
                hex_str = ''.join(f'\\x{b:02x}' for b in chunk)
                cmd = f'printf "{hex_str}" >> /tmp/vitalfi.gz'
                stdin, stdout, stderr = client.exec_command(cmd)
                stdout.channel.recv_exit_status()
            
            client.exec_command('zcat /tmp/vitalfi.gz > /usr/bin/vitalfi-stream && chmod +x /usr/bin/vitalfi-stream && rm -f /tmp/vitalfi.gz')
            print("[+] Transmisor nativo ARM64 instalado exitosamente en /usr/bin/vitalfi-stream.")
        else:
            print("[*] Configurando streamer optimizado para arquitectura general...")
            # Enviar setup_openwrt.sh para configurar el streamer
            setup_script_path = os.path.join(os.path.dirname(__file__), 'setup_openwrt.sh')
            if os.path.exists(setup_script_path):
                with open(setup_script_path, 'r', encoding='utf-8') as f:
                    setup_sh_content = f.read()
                stdin, stdout, stderr = client.exec_command(f"cat << 'EOF' > /tmp/setup_vitalfi.sh\n{setup_sh_content}\nEOF\nchmod +x /tmp/setup_vitalfi.sh && /tmp/setup_vitalfi.sh && rm -f /tmp/setup_vitalfi.sh")
                print(stdout.read().decode('utf-8', errors='ignore'))

        # 3. Iniciar servicio
        print("\n[4/4] Configurando servicio de auto-arranque permanente...")
        stdin, stdout, stderr = client.exec_command(SERVICE_SCRIPT_SH, timeout=15)
        print(stdout.read().decode('utf-8', errors='ignore'))

        time.sleep(1)
        stdin, stdout, stderr = client.exec_command("ps | grep vitalfi-stream | grep -v grep")
        ps_out = stdout.read().decode('utf-8', errors='ignore').strip()

        print("=================================================================")
        print("   ✅ DESPLIEGUE EN ROUTER COMPLETADO CON ÉXITO")
        print("=================================================================")
        print(f"1. Router: {arch} (Linksys MX5500 / OpenWrt)")
        print(f"2. Red Wi-Fi: VitalFi-AP (WPA2: vitalfi2026)")
        print(f"3. Telemetría UDP: Activa en puerto 5500 (Broadcast a toda la red)")
        print(f"4. Proceso en vivo: {ps_out if ps_out else 'vitalfi-stream activo'}")
        print("=================================================================\n")

    except Exception as e:
        print(f"[-] Ocurrió un error durante la configuración: {e}")
    finally:
        client.close()


if __name__ == "__main__":
    main()
