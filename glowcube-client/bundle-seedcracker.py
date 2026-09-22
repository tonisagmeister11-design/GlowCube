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

import io
import json
import os
import pathlib
import re
import sys
import urllib.request
import zipfile

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


def lade(url, kopf):
    with urllib.request.urlopen(urllib.request.Request(url, headers=kopf), timeout=120) as antwort:
        return antwort.read()


def teile(text):
    """'1.21.11' -> (1, 21, 11), damit sich Fassungen vergleichen lassen."""
    return tuple(int(t) for t in re.findall(r"\d+", text)[:4])


def passt(bedingung, fassung):
    """Erfuellt die Spielfassung diese Abhaengigkeitsangabe?

    Deckt ab, was in der Praxis vorkommt: eine Liste, eine feste Fassung,
    '~1.21.11', '>=1.21.11 <1.21.12' und Kombinationen davon. Alles, was
    nicht erkannt wird, gilt als nicht passend - lieber nichts einbauen als
    das Falsche.
    """
    if bedingung is None:
        return False
    if isinstance(bedingung, list):
        return any(passt(b, fassung) for b in bedingung)

    hier = teile(fassung)
    text = bedingung.strip()

    if text in ("*", ""):
        return True
    if text == fassung:
        return True

    for teil in text.split():
        teil = teil.strip()
        if teil.startswith(">="):
            if hier < teile(teil[2:]):
                return False
        elif teil.startswith("<="):
            if hier > teile(teil[2:]):
                return False
        elif teil.startswith("<"):
            if hier >= teile(teil[1:]):
                return False
        elif teil.startswith(">"):
            if hier <= teile(teil[1:]):
                return False
        elif teil.startswith("~"):
            # ~1.21.11 heisst: gleiche Haupt- und Nebenfassung, Rest hoeher.
            ziel = teile(teil[1:])
            if hier[:2] != ziel[:2] or hier < ziel:
                return False
        elif teil.startswith("="):
            if hier != teile(teil[1:]):
                return False
        else:
            if teile(teil) != hier:
                return False
    return True


def melde(text, art="notice"):
    print(text)
    print(f"::{art} title=SeedCrackerX::{text}")


def main():
    fassung = spielfassung()
    if not fassung:
        melde("Keine Spielfassung gefunden - nichts eingebaut", "warning")
        return 0

    # SeedCrackerX gibt es nur bis 1.21.x. Ab 26.x wird nichts eingebaut - und
    # das mit Absicht fest verdrahtet, nicht bloss ueber die Fassungspruefung:
    # eine 1.21er-SeedCrackerX-JAR in einer 26.x-Mod bringt ihre eigenen Mixins
    # mit, die dort nicht greifen, und reisst beim Start halb GlowCube um.
    if not fassung.startswith("1."):
        # Zusaetzlich den "seedcrackerx"-Einstiegspunkt aus dem Manifest nehmen:
        # ohne SeedCrackerX ruft ihn zwar ohnehin niemand, aber so bleibt das
        # 26.x-Manifest sauber und nennt nichts, was es nicht gibt.
        try:
            manifest = json.loads(MOD_JSON.read_text(encoding="utf-8"))
            manifest.get("entrypoints", {}).pop("seedcrackerx", None)
            manifest.pop("suggests", None)
            MOD_JSON.write_text(
                json.dumps(manifest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        except Exception as fehler:
            melde(f"Manifest nicht angepasst ({fehler}) - unkritisch", "warning")
        melde(f"Minecraft {fassung}: SeedCrackerX gibt es dafuer nicht - "
              f"nichts eingebaut (so gewollt)")
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

    # SeedCrackerX benennt seine Dateien nach der eigenen Fassung
    # (seedcrackerX-2.16.1.jar), nicht nach der Minecraft-Fassung. Der
    # Dateiname sagt also nichts - die Antwort steht im Manifest jeder JAR
    # unter depends.minecraft. Also von neu nach alt hineinschauen, bis eine
    # passt.
    treffer = None
    geprueft = []
    for release in releases:
        for anhang in release.get("assets", []):
            name = anhang["name"]
            if not name.endswith(".jar") or "source" in name.lower():
                continue
            try:
                inhalt = lade(anhang["browser_download_url"], kopf)
                manifest = json.loads(zipfile.ZipFile(io.BytesIO(inhalt)).read("fabric.mod.json"))
            except Exception:
                continue
            bedingung = manifest.get("depends", {}).get("minecraft")
            geprueft.append(f"{name}={bedingung}")
            if passt(bedingung, fassung):
                treffer = (name, inhalt, release.get("tag_name"), bedingung)
                break
        if treffer:
            break

    if not treffer:
        melde(f"Keine Fassung fuer Minecraft {fassung} gefunden. "
              f"Geprueft: {geprueft[:10]}", "warning")
        return 0

    name, inhalt, tag, bedingung = treffer
    ZIEL.mkdir(parents=True, exist_ok=True)
    datei = ZIEL / name
    datei.write_bytes(inhalt)

    # Im Manifest eintragen, sonst laedt Fabric die innere JAR nicht.
    manifest = json.loads(MOD_JSON.read_text(encoding="utf-8"))
    manifest["jars"] = [{"file": f"META-INF/jars/{name}"}]
    # Aus "suggests" wird damit eine erfuellte Beigabe.
    manifest.pop("suggests", None)
    MOD_JSON.write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

    melde(f"{name} ({tag}, {datei.stat().st_size // 1024} KB, will minecraft {bedingung}) "
          f"eingebaut - eine Datei weniger im mods-Ordner")
    return 0


if __name__ == "__main__":
    sys.exit(main())
