#!/usr/bin/env python3
"""
Legt SeedCrackerX mit in die JAR, damit nur eine Datei in den mods-Ordner muss.

Fabric kann Mods ineinander verpacken: eine JAR unter META-INF/jars, im
Manifest unter "jars" eingetragen, wird beim Start mitgeladen. Genau so
liefert SeedCrackerX selbst seine Abhaengigkeiten aus.

Welche Fassung passt, entscheidet die Spielfassung aus gradle.properties.
Findet sich keine, wird nichts eingebaut und der Build laeuft normal weiter -
dann legt man SeedCrackerX eben wieder von Hand daneben.

Lizenz: SeedCrackerX steht unter MIT, Weitergabe ist also ausdruecklich
erlaubt. Die Herkunft steht in HERKUNFT.md.
"""

import json
import os
import pathlib
import re
import sys
import urllib.request

HERE = pathlib.Path(__file__).parent
PROPS = HERE / "gradle.properties"
MOD_JSON = HERE / "src/main/resources/fabric.mod.json"
ZIEL = HERE / "src/main/resources/META-INF/jars"
RELEASES = "https://api.github.com/repos/19MisterX98/SeedcrackerX/releases?per_page=40"


def spielfassung():
    for zeile in PROPS.read_text(encoding="utf-8").splitlines():
        if zeile.startswith("minecraft_version="):
            return zeile.split("=", 1)[1].strip()
    return None


def melde(text, art="notice"):
    print(text)
    print(f"::{art} title=SeedCrackerX::{text}")


def main():
    fassung = spielfassung()
    if not fassung:
        melde("Keine Spielfassung gefunden - nichts eingebaut", "warning")
        return 0

    kopf = {"User-Agent": "GlowCube-Build"}
    token = os.environ.get("GH_TOKEN")
    if token:
        kopf["Authorization"] = f"Bearer {token}"

    try:
        anfrage = urllib.request.Request(RELEASES, headers=kopf)
        with urllib.request.urlopen(anfrage, timeout=40) as antwort:
            releases = json.load(antwort)
    except Exception as fehler:
        melde(f"Releases nicht erreichbar ({fehler}) - nichts eingebaut", "warning")
        return 0

    # Passend ist, was die Spielfassung im Dateinamen oder im Tag traegt.
    treffer = None
    for release in releases:
        for anhang in release.get("assets", []):
            name = anhang["name"]
            if not name.endswith(".jar") or "source" in name.lower():
                continue
            if fassung in name or fassung in (release.get("tag_name") or ""):
                treffer = (name, anhang["browser_download_url"], release.get("tag_name"))
                break
        if treffer:
            break

    if not treffer:
        gesehen = sorted({a["name"] for r in releases[:8] for a in r.get("assets", [])})[:8]
        melde(f"Keine Fassung fuer Minecraft {fassung} gefunden. Gesehen: {gesehen}", "warning")
        return 0

    name, url, tag = treffer
    ZIEL.mkdir(parents=True, exist_ok=True)
    datei = ZIEL / name
    try:
        with urllib.request.urlopen(urllib.request.Request(url, headers=kopf), timeout=120) as antwort:
            datei.write_bytes(antwort.read())
    except Exception as fehler:
        melde(f"Herunterladen von {name} fehlgeschlagen ({fehler})", "warning")
        return 0

    # Im Manifest eintragen, sonst laedt Fabric die innere JAR nicht.
    manifest = json.loads(MOD_JSON.read_text(encoding="utf-8"))
    manifest["jars"] = [{"file": f"META-INF/jars/{name}"}]
    # Aus "suggests" wird damit eine erfuellte Beigabe.
    manifest.pop("suggests", None)
    MOD_JSON.write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

    melde(f"{name} ({tag}, {datei.stat().st_size // 1024} KB) eingebaut - "
          f"eine Datei weniger im mods-Ordner")
    return 0


if __name__ == "__main__":
    sys.exit(main())
