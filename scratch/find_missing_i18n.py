import os
import re
from bs4 import BeautifulSoup

index_path = r"c:\xampp\htdocs\vitalfi\landing\index.html"

with open(index_path, "r", encoding="utf-8") as f:
    html = f.read()

soup = BeautifulSoup(html, "html.parser")

missing_i18n_tags = []
for tag in soup.find_all(['h1', 'h2', 'h3', 'h4', 'p', 'strong', 'span']):
    if tag.find_parent('svg') or tag.name in ['script', 'style']:
        continue
    # If tag itself has data-i18n, good
    if tag.get('data-i18n'):
        continue
    # If tag has children with data-i18n, good
    children_i18n = tag.find_all(attrs={"data-i18n": True})
    if children_i18n:
        continue
    
    text = tag.get_text(strip=True)
    # ignore brand or symbols
    if text and len(text) > 3 and not text.startswith("Vital") and not text.startswith("v1."):
        missing_i18n_tags.append((tag.name, tag.get('data-od-id', ''), text[:70]))

print(f"Found {len(missing_i18n_tags)} tags missing data-i18n:")
for name, od_id, txt in missing_i18n_tags:
    safe_txt = txt.encode('ascii', 'replace').decode('ascii')
    print(f"<{name} data-od-id='{od_id}'> : {safe_txt}")
