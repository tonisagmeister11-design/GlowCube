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
import os
import pathlib
import re
import sys
import urllib.request
import xml.etree.ElementTree as ET

META = "https://meta.fabricmc.net/v2/versions"
MAVEN = "https://maven.fabricmc.net"
HERE = pathlib.Path(__file__).parent
PROPS = HERE / "gradle.properties"
MOD_JSON = HERE / "src/main/resources/fabric.mod.json"


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


def newest_in_maven(group_path):
    """Die aktuellste Fassung laut maven-metadata.xml.

    Bei Loom sind SNAPSHOT-Fassungen der Normalfall, nicht die Ausnahme -
    deshalb wird hier nicht danach gefiltert.
    """
    root = ET.fromstring(fetch(f"{MAVEN}/{group_path}/maven-metadata.xml"))
    latest = root.findtext("versioning/latest")
    if latest:
        return latest
    versions = [v.text for v in root.iter("version") if v.text]
    return versions[-1] if versions else None


def latest_in_maven(group_path, predicate=lambda v: True):
    """Neueste Version aus einer maven-metadata.xml, die zum Filter passt."""
    root = ET.fromstring(fetch(f"{MAVEN}/{group_path}/maven-metadata.xml"))
    versions = [v.text for v in root.iter("version") if v.text]
    matching = [v for v in versions if predicate(v)]
    return matching[-1] if matching else None


def emit(report):
    """Bericht ins Log, als Annotation und in die Zusammenfassung des Laufs."""
    print("=" * 62)
    print("Verwendete Fassungen")
    print("=" * 62)
    for line in report:
        print("  " + line)
    print("=" * 62)
    print()
    print(PROPS.read_text(encoding="utf-8"))

    print("::notice title=Fabric-Versionen::" + "%0A".join(report))

    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as handle:
            handle.write("### Verwendete Fassungen\n\n")
            for line in report:
                handle.write(f"- `{line}`\n")
            handle.write("\n")


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

    # --- Loom. Hier lag beim zweiten Lauf der Hund begraben: der geratene
    #     Pfad lief in einen 404, also blieb eine uralte Fassung stehen, die
    #     mit Minecraft von 2026 nichts anfangen kann. Darum mehrere
    #     Kandidaten - und ein harter Abbruch, wenn keiner traegt.
    loom = None
    for path in ("net/fabricmc/fabric-loom",                 # die Bibliothek
                 "fabric-loom/fabric-loom.gradle.plugin"):   # der Plugin-Marker
        try:
            loom = newest_in_maven(path)
            if loom:
                report.append(f"Loom           {loom}  (aus {path})")
                break
        except Exception as error:
            report.append(f"Loom           {path} liefert nichts: {error}")
    if loom:
        write_prop("loom_version", loom)
    else:
        report.append("Loom           NICHT AUFLOESBAR - Abbruch")
        emit(report)
        print("::error title=Loom::Keine Loom-Fassung gefunden. Ohne die ist "
              "jeder weitere Fehler nur Folgeschaden.")
        return 1

    # --- Braucht diese Spielfassung ueberhaupt Mappings? Seit 26.1 ist
    #     Minecraft unobfuskiert und liefert keine mappings-Datei mehr mit.
    #     Statt zu raten wird bei Mojang nachgesehen.
    mode = "none"
    try:
        listing = json.loads(fetch("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"))
        entry = next((v for v in listing["versions"] if v["id"] == minecraft), None)
        if entry:
            detail = json.loads(fetch(entry["url"]))
            if "client_mappings" in detail.get("downloads", {}):
                mode = "mojang"
            report.append(f"Mappings       {mode}  "
                          f"({'Mojang liefert welche' if mode == 'mojang' else 'unobfuskiert, keine noetig'})")
        else:
            report.append(f"Mappings       none  ({minecraft} steht nicht im Mojang-Verzeichnis)")
    except Exception as error:
        report.append(f"Mappings       none  (Mojang nicht erreichbar: {error})")
    write_prop("mappings_mode", mode)

    # --- fabric.mod.json auf die wirklich gebaute Fassung ziehen, sonst
    #     weigert sich der Loader spaeter, den Mod ueberhaupt zu laden.
    manifest = json.loads(MOD_JSON.read_text(encoding="utf-8"))
    before = manifest["depends"].get("minecraft")
    manifest["depends"]["minecraft"] = f">={minecraft}"
    MOD_JSON.write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n",
                        encoding="utf-8")
    if before != f">={minecraft}":
        report.append(f"fabric.mod.json angepasst: minecraft {before} -> >={minecraft}")

    emit(report)
    return 0


if __name__ == "__main__":
    sys.exit(main())
