#!/usr/bin/env python3
"""
Prueft, dass Mixin-Liste und Mixin-Dateien zusammenpassen.

Ein Mixin, das in glowcube.mixins.json fehlt, wird nie geladen - das Modul
dahinter tut dann einfach nichts, ohne dass irgendwo ein Fehler steht. Der
umgekehrte Fall, ein Eintrag ohne Datei, bricht den Spielstart ab. Beides
faellt sonst erst beim Nutzer auf.
"""

import json
import pathlib
import sys

WURZEL = pathlib.Path(__file__).parent
KONFIG = WURZEL / "src/main/resources/glowcube.mixins.json"
ORDNER = WURZEL / "src/main/java/net/glowcube/client/mixin"


def main():
    konfig = json.loads(KONFIG.read_text(encoding="utf-8"))
    eingetragen = set(konfig.get("client", [])) | set(konfig.get("mixins", []))
    vorhanden = {p.stem for p in ORDNER.glob("*.java")}

    fehlt_datei = sorted(eingetragen - vorhanden)
    fehlt_eintrag = sorted(vorhanden - eingetragen)

    if fehlt_datei:
        print("::error title=Mixin ohne Datei::" + ", ".join(fehlt_datei))
    if fehlt_eintrag:
        print("::error title=Mixin nicht eingetragen::" + ", ".join(fehlt_eintrag)
              + " - liegt im Ordner, steht aber nicht in glowcube.mixins.json"
              + " und wird deshalb nie geladen.")

    if fehlt_datei or fehlt_eintrag:
        return 1

    print(f"::notice title=Mixins::{len(eingetragen)} Eintraege, alle vorhanden.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
