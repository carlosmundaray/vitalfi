#!/usr/bin/env python3
"""
==============================================================================
 VitalFi - Desplegador de Binarios y Artefactos de Release
==============================================================================
Copia y sincroniza las APKs, Bundles (AAB) y mappings a las carpetas releases/ y landing/.
"""

import os
import shutil
import json

root_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
landing_dir = os.path.join(root_dir, "landing")
landing_releases = os.path.join(landing_dir, "releases")
releases_dir = os.path.join(root_dir, "releases")

# Leer versión actual de version.json
version_json_path = os.path.join(landing_dir, "version.json")
version_name = "1.3.35"
if os.path.exists(version_json_path):
    try:
        with open(version_json_path, "r", encoding="utf-8") as f:
            vdata = json.load(f)
            version_name = vdata.get("latestVersion", version_name)
    except Exception:
        pass

apk_src = os.path.join(root_dir, "android", "app", "build", "outputs", "apk", "release", "app-release.apk")
aab_src = os.path.join(root_dir, "android", "app", "build", "outputs", "bundle", "release", "app-release.aab")
mapping_src = os.path.join(root_dir, "android", "app", "build", "outputs", "mapping", "release", "mapping.txt")

os.makedirs(releases_dir, exist_ok=True)
os.makedirs(landing_releases, exist_ok=True)

if os.path.exists(apk_src):
    shutil.copy2(apk_src, os.path.join(releases_dir, f"VitalFi-v{version_name}-signed.apk"))
    shutil.copy2(apk_src, os.path.join(releases_dir, "VitalFi-latest-signed.apk"))
    shutil.copy2(apk_src, os.path.join(landing_releases, f"VitalFi-v{version_name}-signed.apk"))
    shutil.copy2(apk_src, os.path.join(landing_releases, "VitalFi-latest-signed.apk"))
    print(f"[+] APK copiada: {os.path.getsize(apk_src) / (1024*1024):.2f} MB")

if os.path.exists(aab_src):
    shutil.copy2(aab_src, os.path.join(releases_dir, f"VitalFi-v{version_name}-signed.aab"))
    shutil.copy2(aab_src, os.path.join(landing_releases, f"VitalFi-v{version_name}-signed.aab"))
    print(f"[+] AAB copiado: {os.path.getsize(aab_src) / (1024*1024):.2f} MB")

if os.path.exists(mapping_src):
    shutil.copy2(mapping_src, os.path.join(releases_dir, f"mapping-v{version_name}.txt"))
    shutil.copy2(mapping_src, os.path.join(landing_releases, f"mapping-v{version_name}.txt"))

print("[+] Todos los artefactos de release se han desplegado correctamente.")
