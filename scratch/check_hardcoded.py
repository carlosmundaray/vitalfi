import os
import re
import glob

ui_files = glob.glob(r"c:\xampp\htdocs\vitalfi\android\app\src\main\java\com\vitalfi\ui\*.kt")
ui_files += glob.glob(r"c:\xampp\htdocs\vitalfi\android\app\src\main\java\com\vitalfi\*.kt")

hardcoded = []

for f in ui_files:
    filename = os.path.basename(f)
    with open(f, 'r', encoding='utf-8') as file:
        lines = file.readlines()
        for idx, line in enumerate(lines):
            # check string literals in Text("...") or Canvas drawText or strings
            matches = re.findall(r'Text\(\s*"(.*?)"', line)
            matches += re.findall(r'text\s*=\s*"(.*?)"', line)
            matches += re.findall(r'drawText\(\s*"(.*?)"', line)
            matches += re.findall(r'Toast\.makeText\(.*?"(.*?)"', line)
            for m in matches:
                if len(m) > 1 and not re.match(r'^[0-9. +−/%°mHzdBmsVv\-]+$', m) and not m.startswith("R.string"):
                    hardcoded.append((filename, idx + 1, m, line.strip()))

print(f"Found {len(hardcoded)} potential hardcoded UI strings:")
for fname, lnum, val, line in hardcoded:
    safe_val = val.encode('ascii', errors='replace').decode('ascii')
    safe_line = line.encode('ascii', errors='replace').decode('ascii')
    print(f"{fname}:{lnum} -> \"{safe_val}\" | code: {safe_line}")
