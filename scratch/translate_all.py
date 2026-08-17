import os
import xml.etree.ElementTree as ET

# Base dictionary of translations for major languages
# All keys must match res/values/strings.xml

base_en_path = r"c:\xampp\htdocs\vitalfi\android\app\src\main\res\values\strings.xml"
res_dir = r"c:\xampp\htdocs\vitalfi\android\app\src\main\res"

tree = ET.parse(base_en_path)
root = tree.getroot()

keys = [elem.attrib['name'] for elem in root.findall('string')]
print(f"Total keys in base strings.xml: {len(keys)}")

# Let's define translation templates for languages
# We will create directories and write strings.xml for each language
