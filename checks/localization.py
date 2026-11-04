"""Kiểm chuỗi giao diện thiếu bản dịch và tham số định dạng bị lệch giữa hai ngôn ngữ."""

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LANG = ROOT / "src/main/resources/assets/nosclient/lang"
english = json.loads((LANG / "en_us.json").read_text(encoding="utf-8"))
vietnamese = json.loads((LANG / "vi_vn.json").read_text(encoding="utf-8"))
errors = []
if english.keys() != vietnamese.keys():
    errors.append(f"Translation key mismatch: {english.keys() ^ vietnamese.keys()}")
for key, value in english.items():
    if re.findall(r"%(?:\d+\$)?[sd]", value) != re.findall(r"%(?:\d+\$)?[sd]", vietnamese.get(key, "")):
        errors.append(f"Placeholder mismatch: {key}")
for path in (ROOT / "src/client/java").rglob("*.java"):
    for key in re.findall(r'"((?:nosclient\.music\.|key\.nosclient\.music\.)[^"\n]*)"', path.read_text(encoding="utf-8")):
        if key and not key.endswith(".") and key not in english:
            errors.append(f"Missing translation: {key} in {path.relative_to(ROOT)}")
if errors:
    raise SystemExit("\n".join(errors))
print(f"Localization passed: {len(english)} keys in English and Vietnamese")
