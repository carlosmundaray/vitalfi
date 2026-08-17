import re
import json

i18n_path = r"c:\xampp\htdocs\vitalfi\landing\i18n.js"
with open(i18n_path, "r", encoding="utf-8") as f:
    content = f.read()

# Check each language in TRANSLATIONS
# Let's find all lang keys: "ar": { ... }, "en": { ... }
langs = re.findall(r'"([a-z]{2}(?:-[a-zA-Z]+)?)":\s*\{', content)
print(f"Found {len(langs)} languages in TRANSLATIONS:", langs)

missing_title = []
missing_body = []

for l in langs:
    pattern = rf'"{l}":\s*\{{([\s\S]*?)\n  \}}'
    match = re.search(pattern, content)
    if match:
        block = match.group(1)
        if '"object.title"' not in block:
            missing_title.append(l)
        if '"object.body"' not in block:
            missing_body.append(l)
    else:
        print(f"Could not parse block for {l}")

print("Missing object.title:", missing_title)
print("Missing object.body:", missing_body)
