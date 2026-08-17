import os
import xml.sax.saxutils as saxutils

res_dir = r"c:\xampp\htdocs\vitalfi\android\app\src\main\res"

# We have translations for:
# en, es, zh, hi, ar, fr, pt, ru, ja, de, tr, it, in/id, uk, bn, ko, vi, pl, nl, fa, th, sw, ro, el, cs, sv, tl/fil, he/iw

# Let's write the translation dictionaries
translations = {}

# We will load English as base template
base_xml = os.path.join(res_dir, "values", "strings.xml")
with open(base_xml, "r", encoding="utf-8") as f:
    en_content = f.read()

# Let's extract key-value pairs from en_content
import re
kv_pairs = re.findall(r'<string name="([^"]+)">([^<]+)</string>', en_content)
en_dict = {k: v for k, v in kv_pairs}

# Spanish dictionary
es_xml = os.path.join(res_dir, "values-es", "strings.xml")
with open(es_xml, "r", encoding="utf-8") as f:
    es_content = f.read()
es_kv = re.findall(r'<string name="([^"]+)">([^<]+)</string>', es_content)
es_dict = {k: v for k, v in es_kv}

print(f"Loaded {len(en_dict)} EN keys, {len(es_dict)} ES keys")
