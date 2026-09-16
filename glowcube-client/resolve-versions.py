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

    # --- Erst bei Mojang nachsehen, wie die Fassung beschaffen ist: braucht
    #     sie Mappings, und welches Java verlangt sie? Beides steht dort.
    mode = "none"
    java = props.get("java_version", "21")
    try:
        listing = json.loads(fetch("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"))
        entry = next((v for v in listing["versions"] if v["id"] == minecraft), None)
        if entry:
            detail = json.loads(fetch(entry["url"]))
            if "client_mappings" in detail.get("downloads", {}):
                mode = "mojang"
            java = str(detail.get("javaVersion", {}).get("majorVersion", java))
            report.append(f"Mappings       {mode}  "
                          f"({'Mojang liefert welche' if mode == 'mojang' else 'unobfuskiert, keine noetig'})")
            report.append(f"Java           {java}")
        else:
            report.append(f"Mappings       none  ({minecraft} steht nicht im Mojang-Verzeichnis)")
    except Exception as error:
        report.append(f"Mappings       none  (Mojang nicht erreichbar: {error})")
    write_prop("mappings_mode", mode)
    write_prop("java_version", java)

    # --- Loom. Es gibt zwei Plugins, und welches passt, haengt genau daran:
    #     'fabric-loom' remappt und braucht Mappings, 'net.fabricmc.fabric-loom'
    #     remappt nicht und kommt nur mit unobfuskierten Fassungen klar.
    if mode == "none":
        kandidaten = [("net.fabricmc.fabric-loom",
                       "net/fabricmc/fabric-loom/net.fabricmc.fabric-loom.gradle.plugin"),
                      ("fabric-loom", "fabric-loom/fabric-loom.gradle.plugin")]
    else:
        kandidaten = [("fabric-loom", "fabric-loom/fabric-loom.gradle.plugin"),
                      ("net.fabricmc.fabric-loom",
                       "net/fabricmc/fabric-loom/net.fabricmc.fabric-loom.gradle.plugin")]

    loom = loom_id = None
    for plugin_id, marker in kandidaten:
        try:
            found = newest_in_maven(marker)
        except Exception as error:
            report.append(f"Loom-Plugin    {plugin_id}: nicht da ({error})")
            continue
        if found:
            loom, loom_id = found, plugin_id
            report.append(f"Loom-Plugin    {plugin_id}  {found}")
            break

    if not loom:
        try:
            loom, loom_id = newest_in_maven("net/fabricmc/fabric-loom"), "fabric-loom"
            report.append(f"Loom-Plugin    fabric-loom  {loom}  (ueber die Bibliothek)")
        except Exception as error:
            report.append(f"Loom-Plugin    NICHT AUFLOESBAR - {error}")

    if loom:
        write_prop("loom_version", loom)
        write_prop("loom_plugin_id", loom_id)
    else:
        emit(report)
        print("::error title=Loom::Keine Loom-Fassung gefunden.")
        return 1

    # --- fabric.mod.json auf die wirklich gebaute Fassung ziehen, sonst
    #     weigert sich der Loader spaeter, den Mod ueberhaupt zu laden.
    manifest = json.loads(MOD_JSON.read_text(encoding="utf-8"))
    before = manifest["depends"].get("minecraft")
    manifest["depends"]["minecraft"] = f">={minecraft}"
    manifest["depends"]["java"] = f">={java}"
    MOD_JSON.write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n",
                        encoding="utf-8")
    if before != f">={minecraft}":
        report.append(f"fabric.mod.json angepasst: minecraft {before} -> >={minecraft}")

    emit(report)
    return 0


if __name__ == "__main__":
    sys.exit(main())
