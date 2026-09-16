#!/usr/bin/env python3
"""
Traegt die aktuell gueltigen Fabric-Versionen in gradle.properties ein.

Die Nummern in gradle.properties veralten mit jeder Fabric-Veroeffentlichung.
Statt sie von Hand zu pflegen, fragt dieses Skript die offiziellen Quellen:
meta.fabricmc.net fuer Spiel und Loader, das Fabric-Maven fuer API und Loom.

Laeuft vor jedem CI-Build. Wenn eine Quelle nicht erreichbar ist, bleibt der
bisherige Wert stehen - dann meldet sich spaeter der Build selbst.
"""

import json
import pathlib
import re
import sys
import urllib.request
import xml.etree.ElementTree as ET

META = "https://meta.fabricmc.net/v2/versions"
MAVEN = "https://maven.fabricmc.net"
PROPS = pathlib.Path(__file__).with_name("gradle.properties")


def fetch(url, timeout=30):
    with urllib.request.urlopen(url, timeout=timeout) as response:
        return response.read()


def read_props():
    values = {}
    for line in PROPS.read_text(encoding="utf-8").splitlines():
        if "=" in line and not line.lstrip().startswith("#"):
            key, _, value = line.partition("=")
            values[key.strip()] = value.strip()
    return values


def write_prop(key, value):
    text = PROPS.read_text(encoding="utf-8")
    pattern = re.compile(rf"^{re.escape(key)}=.*$", re.MULTILINE)
    if pattern.search(text):
        text = pattern.sub(f"{key}={value}", text)
    else:
        text = text.rstrip("\n") + f"\n{key}={value}\n"
    PROPS.write_text(text, encoding="utf-8")


def latest_in_maven(group_path, predicate=lambda v: True):
    """Neueste Version aus einer maven-metadata.xml, die zum Filter passt."""
    root = ET.fromstring(fetch(f"{MAVEN}/{group_path}/maven-metadata.xml"))
    versions = [v.text for v in root.iter("version") if v.text]
    matching = [v for v in versions if predicate(v)]
    return matching[-1] if matching else None


def main():
    props = read_props()
    wanted = props.get("minecraft_version", "")
    report = []

    # --- Spielfassung: die gewuenschte, falls Fabric sie kennt.
    games = json.loads(fetch(f"{META}/game"))
    known = {g["version"] for g in games}
    if wanted in known:
        minecraft = wanted
        report.append(f"Minecraft      {minecraft}  (wie eingetragen)")
    else:
        minecraft = next(g["version"] for g in games if g["stable"])
        write_prop("minecraft_version", minecraft)
        report.append(f"Minecraft      {minecraft}  (ERSETZT - '{wanted}' kennt Fabric nicht)")

    # --- Loader: der neueste stabile fuer genau diese Spielfassung.
    try:
        loaders = json.loads(fetch(f"{META}/loader/{minecraft}"))
        loader = next(entry["loader"]["version"] for entry in loaders if entry["loader"]["stable"])
        write_prop("loader_version", loader)
        report.append(f"Fabric Loader  {loader}")
    except Exception as error:
        report.append(f"Fabric Loader  UNVERAENDERT ({props.get('loader_version')}) - {error}")

    # --- Fabric API: die neueste Fassung, die auf diese Spielfassung endet.
    try:
        api = latest_in_maven("net/fabricmc/fabric-api/fabric-api",
                              lambda v: v.endswith(f"+{minecraft}"))
        if api:
            write_prop("fabric_version", api)
            report.append(f"Fabric API     {api}")
        else:
            report.append(f"Fabric API     UNVERAENDERT ({props.get('fabric_version')}) "
                          f"- nichts fuer +{minecraft} gefunden")
    except Exception as error:
        report.append(f"Fabric API     UNVERAENDERT ({props.get('fabric_version')}) - {error}")

    # --- Loom: neueste Ausgabe ohne SNAPSHOT.
    try:
        loom = latest_in_maven("net/fabricmc/fabric-loom/fabric-loom.gradle.plugin",
                               lambda v: "SNAPSHOT" not in v.upper())
        if loom:
            write_prop("loom_version", loom)
            report.append(f"Loom           {loom}")
        else:
            report.append(f"Loom           UNVERAENDERT ({props.get('loom_version')})")
    except Exception as error:
        report.append(f"Loom           UNVERAENDERT ({props.get('loom_version')}) - {error}")

    print("=" * 62)
    print("Verwendete Fassungen")
    print("=" * 62)
    for line in report:
        print("  " + line)
    print("=" * 62)
    print()
    print(PROPS.read_text(encoding="utf-8"))
    return 0


if __name__ == "__main__":
    sys.exit(main())
