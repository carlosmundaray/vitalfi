import os
import zipfile

landing_dir = r"c:\xampp\htdocs\vitalfi\landing"
zip_path = os.path.join(landing_dir, "landing.zip")

# Remove old zip first
if os.path.exists(zip_path):
    os.remove(zip_path)

with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as zipf:
    for root, dirs, files in os.walk(landing_dir):
        for file in files:
            full_p = os.path.join(root, file)
            if file == "landing.zip":
                continue
            rel_p = os.path.relpath(full_p, landing_dir)
            zipf.write(full_p, rel_p)

print(f"Generated fresh landing.zip ({os.path.getsize(zip_path)} bytes)")
