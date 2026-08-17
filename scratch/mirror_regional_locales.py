import os
import shutil

res_dir = r"c:\xampp\htdocs\vitalfi\android\app\src\main\res"

pairs = [
    ("values-es", "values-es-rUS"),
    ("values-pt", "values-pt-rBR"),
    ("values-pt", "values-pt-rPT"),
    ("values-in", "values-id"),
    ("values-iw", "values-he"),
    ("values-tl", "values-fil"),
    ("values-zh", "values-zh-rCN"),
    ("values-zh", "values-zh-rTW")
]

for src, dst in pairs:
    src_p = os.path.join(res_dir, src, "strings.xml")
    dst_dir = os.path.join(res_dir, dst)
    os.makedirs(dst_dir, exist_ok=True)
    shutil.copy2(src_p, os.path.join(dst_dir, "strings.xml"))
    print(f"Mirrored {src} -> {dst}")

print("All regional and country variants mirrored successfully!")
