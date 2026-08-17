import os
import shutil

root_dir = r"c:\xampp\htdocs\vitalfi"
landing_dir = os.path.join(root_dir, "landing")
landing_releases = os.path.join(landing_dir, "releases")
releases_dir = os.path.join(root_dir, "releases")

apk_src = os.path.join(root_dir, "android", "app", "build", "outputs", "apk", "release", "app-release.apk")
aab_src = os.path.join(root_dir, "android", "app", "build", "outputs", "bundle", "release", "app-release.aab")
mapping_src = os.path.join(root_dir, "android", "app", "build", "outputs", "mapping", "release", "mapping.txt")

# 1. Copy to releases/
shutil.copy2(apk_src, os.path.join(releases_dir, "VitalFi-v1.3.35-signed.apk"))
shutil.copy2(aab_src, os.path.join(releases_dir, "VitalFi-v1.3.35-signed.aab"))
if os.path.exists(mapping_src):
    shutil.copy2(mapping_src, os.path.join(releases_dir, "mapping-v1.3.35.txt"))

# 2. Copy to root
shutil.copy2(apk_src, os.path.join(root_dir, "VitalFi-v1.3.35-signed.apk"))
shutil.copy2(aab_src, os.path.join(root_dir, "VitalFi-v1.3.35-signed.aab"))
shutil.copy2(apk_src, os.path.join(root_dir, "VitalFi-latest-signed.apk"))

# 3. Copy to landing/releases
os.makedirs(landing_releases, exist_ok=True)
shutil.copy2(apk_src, os.path.join(landing_releases, "VitalFi-v1.3.35-signed.apk"))
shutil.copy2(aab_src, os.path.join(landing_releases, "VitalFi-v1.3.35-signed.aab"))
shutil.copy2(apk_src, os.path.join(landing_releases, "VitalFi-latest-signed.apk"))
if os.path.exists(mapping_src):
    shutil.copy2(mapping_src, os.path.join(landing_releases, "mapping-v1.3.35.txt"))

# 4. Copy version.json
shutil.copy2(os.path.join(root_dir, "version.json"), os.path.join(landing_dir, "version.json"))

# 5. Update landing/index.html
index_path = os.path.join(landing_dir, "index.html")
with open(index_path, "r", encoding="utf-8") as f:
    index_content = f.read()

index_content = index_content.replace("v1.3.34", "v1.3.35")
index_content = index_content.replace("1.3.34", "1.3.35")
index_content = index_content.replace("Build 41", "Build 42")

with open(index_path, "w", encoding="utf-8") as f:
    f.write(index_content)

# 6. Update landing/i18n.js
i18n_path = os.path.join(landing_dir, "i18n.js")
with open(i18n_path, "r", encoding="utf-8") as f:
    i18n_content = f.read()

i18n_content = i18n_content.replace("v1.3.34", "v1.3.35")
i18n_content = i18n_content.replace("1.3.34", "1.3.35")

with open(i18n_path, "w", encoding="utf-8") as f:
    f.write(i18n_content)

print("Version 1.3.35 artifacts successfully deployed across all locations!")
