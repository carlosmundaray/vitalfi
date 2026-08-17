import os
import xml.etree.ElementTree as ET

res_dir = r"c:\xampp\htdocs\vitalfi\android\app\src\main\res"
base_en = os.path.join(res_dir, "values", "strings.xml")

base_tree = ET.parse(base_en)
base_keys = {elem.attrib['name']: elem.text for elem in base_tree.getroot().findall('string')}

print(f"Total base keys: {len(base_keys)}")

dirs = [d for d in os.listdir(res_dir) if d.startswith("values-")]
print(f"Found {len(dirs)} language directories: {dirs}")

for d in sorted(dirs):
    p = os.path.join(res_dir, d, "strings.xml")
    if not os.path.exists(p):
        print(f"MISSING strings.xml in {d}")
        continue
    try:
        t = ET.parse(p)
        k_map = {elem.attrib['name']: elem.text for elem in t.getroot().findall('string')}
        missing = set(base_keys.keys()) - set(k_map.keys())
        if missing:
            print(f"[{d}] Missing {len(missing)} keys: {missing}")
        else:
            print(f"[{d}] OK: {len(k_map)} keys")
    except Exception as e:
        print(f"[{d}] ERROR: {e}")
