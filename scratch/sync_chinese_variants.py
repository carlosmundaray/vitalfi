import os
import shutil

res_dir = r"c:\xampp\htdocs\vitalfi\android\app\src\main\res"
zh_src = os.path.join(res_dir, "values-zh", "strings.xml")

targets = [
    "values-zh-rCN",
    "values-zh-rTW",
    "values-b+zh+Hans"
]

for t in targets:
    t_dir = os.path.join(res_dir, t)
    os.makedirs(t_dir, exist_ok=True)
    shutil.copy2(zh_src, os.path.join(t_dir, "strings.xml"))
    print(f"Synced {zh_src} -> {t_dir}")

print("Chinese resource variants synchronized successfully!")
