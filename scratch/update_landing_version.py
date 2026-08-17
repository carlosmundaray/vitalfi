import os
import shutil
import re

root_dir = r"c:\xampp\htdocs\vitalfi"
landing_dir = os.path.join(root_dir, "landing")
landing_releases = os.path.join(landing_dir, "releases")

# 1. Copy release binaries to landing/releases
os.makedirs(landing_releases, exist_ok=True)
files_to_copy = [
    ("VitalFi-v1.3.34-signed.apk", os.path.join(landing_releases, "VitalFi-v1.3.34-signed.apk")),
    ("VitalFi-v1.3.34-signed.aab", os.path.join(landing_releases, "VitalFi-v1.3.34-signed.aab")),
    ("VitalFi-v1.3.34-signed.apk", os.path.join(landing_releases, "VitalFi-latest-signed.apk")),
    (os.path.join("releases", "mapping-v1.3.34.txt"), os.path.join(landing_releases, "mapping-v1.3.34.txt"))
]

for src_rel, dst in files_to_copy:
    src = os.path.join(root_dir, src_rel)
    if os.path.exists(src):
        shutil.copy2(src, dst)
        print(f"Copied {src} -> {dst}")
    else:
        print(f"Warning: {src} not found")

# 2. Copy version.json to landing/version.json
shutil.copy2(os.path.join(root_dir, "version.json"), os.path.join(landing_dir, "version.json"))
print("Updated landing/version.json")

# 3. Update landing/index.html
index_path = os.path.join(landing_dir, "index.html")
with open(index_path, "r", encoding="utf-8") as f:
    index_content = f.read()

# Replace version occurrences in index.html
new_index_content = index_content.replace("v1.3.33", "v1.3.34")
new_index_content = new_index_content.replace("1.3.33", "1.3.34")
new_index_content = new_index_content.replace("Build 40", "Build 41")
new_index_content = new_index_content.replace("2.1 MB", "1.8 MB")
new_index_content = new_index_content.replace("3.2 MB", "3.1 MB")

with open(index_path, "w", encoding="utf-8") as f:
    f.write(new_index_content)
print("Updated landing/index.html")

# 4. Update landing/i18n.js
i18n_path = os.path.join(landing_dir, "i18n.js")
with open(i18n_path, "r", encoding="utf-8") as f:
    i18n_content = f.read()

new_i18n_content = i18n_content.replace("v1.3.33", "v1.3.34")
new_i18n_content = new_i18n_content.replace("1.3.33", "1.3.34")
new_i18n_content = new_i18n_content.replace("2.1 MB", "1.8 MB")
new_i18n_content = new_i18n_content.replace("2.1 Mo", "1.8 Mo")
new_i18n_content = new_i18n_content.replace("2.1 МБ", "1.8 МБ")
new_i18n_content = new_i18n_content.replace("2.1 ميجابايت", "1.8 ميجابايت")
new_i18n_content = new_i18n_content.replace("3.2 MB", "3.1 MB")
new_i18n_content = new_i18n_content.replace("3.2 Mo", "3.1 Mo")
new_i18n_content = new_i18n_content.replace("3.2 МБ", "3.1 МБ")
new_i18n_content = new_i18n_content.replace("3.2 ميجابايت", "3.1 ميجابايت")

with open(i18n_path, "w", encoding="utf-8") as f:
    f.write(new_i18n_content)
print("Updated landing/i18n.js")

print("Landing page successfully updated to version v1.3.34 (Build 41)!")
