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

    # Die Optik-Mixins: einige liegen fassungsfrei in src/main, einige je
    # Fassung unter src/versionen/<render>/ - jede Fassung muss alle haben.
    optik = json.loads((WURZEL / "src/main/resources/glowcube.optik.mixins.json").read_text(encoding="utf-8"))
    optik_namen = set(optik.get("client", []))
    gemeinsam = {p.stem for p in (ORDNER / "optik").glob("*.java")}
    for fassung in ("render_1_21", "render_26"):
        eigen = {p.stem for p in (WURZEL / "src/versionen" / fassung / "java/net/glowcube/client/mixin/optik").glob("*.java")}
        vorhanden_optik = gemeinsam | eigen
        for name in sorted(optik_namen - vorhanden_optik):
            print(f"::error title=Optik-Mixin ohne Datei::{name} fehlt fuer {fassung}")
            fehlt_datei.append(name)
        for name in sorted(vorhanden_optik - optik_namen):
            print(f"::error title=Optik-Mixin nicht eingetragen::{name} ({fassung})")
            fehlt_eintrag.append(name)

    if fehlt_datei or fehlt_eintrag:
        return 1

    print(f"::notice title=Mixins::{len(eingetragen)} Eintraege, alle vorhanden.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
