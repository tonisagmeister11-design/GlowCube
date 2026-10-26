"""Reproducible inventory helper for the uploaded Your PC Mine JAR."""
from __future__ import annotations
import hashlib, json, zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent
JAR = ROOT / "YPM-1.0.5.jar"

with zipfile.ZipFile(JAR) as zf:
    names = zf.namelist()
    metadata = json.loads(zf.read("fabric.mod.json"))
    en = json.loads(zf.read("assets/ypm/lang/en_us.json"))
    ru = json.loads(zf.read("assets/ypm/lang/ru_ru.json"))
    classes = sorted(n[:-6].replace('/', '.') for n in names if n.endswith('.class') and n.startswith('dev/errnicraft/ypm/'))
report = {
    "source_archive": "Your PC Mine 1.0.5-EXTRACT_ME.zip",
    "jar": JAR.name,
    "sha256": hashlib.sha256(JAR.read_bytes()).hexdigest(),
    "mod_metadata": metadata,
    "class_count_dev_errnicraft_ypm": len(classes),
    "classes": classes,
    "resource_files": [n for n in names if not n.endswith('.class')],
    "language_key_count_en_us": len(en),
    "language_key_count_ru_ru": len(ru),
    "language_keys": en,
}
(ROOT / "mod_inventory.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
print(json.dumps({"jar": report["jar"], "sha256": report["sha256"], "class_count": report["class_count_dev_errnicraft_ypm"], "en_us_keys": len(en), "ru_ru_keys": len(ru)}, indent=2))
