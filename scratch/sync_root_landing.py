import os
import shutil

root_dir = r"c:\xampp\htdocs\vitalfi"
landing_dir = os.path.join(root_dir, "landing")

# Copy landing files to root
shutil.copy2(os.path.join(landing_dir, "index.html"), os.path.join(root_dir, "index.html"))
shutil.copy2(os.path.join(landing_dir, "i18n.js"), os.path.join(root_dir, "i18n.js"))
shutil.copy2(os.path.join(landing_dir, "manual.html"), os.path.join(root_dir, "manual.html"))

# Copy assets to root if needed
landing_assets = os.path.join(landing_dir, "assets")
root_assets = os.path.join(root_dir, "assets")
if os.path.exists(landing_assets):
    os.makedirs(root_assets, exist_ok=True)
    for item in os.listdir(landing_assets):
        s = os.path.join(landing_assets, item)
        d = os.path.join(root_assets, item)
        if os.path.isfile(s):
            shutil.copy2(s, d)

print("Synchronized landing page to root directory as well!")
