#!/usr/bin/env python3
"""
Prueft, dass es die Namen wirklich gibt, an denen die Mixins haengen.

Der Unterschied zu probe-api.py: das hier *behauptet* etwas und laesst den
Build scheitern, wenn es nicht stimmt. Ein Mixin, dessen Zielmethode es
nicht gibt, faellt sonst erst beim Spielstart auf - beim Nutzer, als Absturz.
Die private Methode, an der der Bewegungshaken haengt, steht in keiner
oeffentlichen Signaturliste; genau solche Faelle sollen hier hart auffliegen.

Aufruf: verify-api.py <classpath-datei> <klasse>#<teilstring> ...
"""

import pathlib
import subprocess
import sys


def signaturen(classpath, klasse):
    ergebnis = subprocess.run(
        ["javap", "-p", "-classpath", classpath, klasse],
        capture_output=True, text=True, timeout=120)
    return ergebnis.stdout


def main():
    if len(sys.argv) < 3:
        print("::error::verify-api.py braucht Classpath und Behauptungen")
        return 2

    classpath = ":".join(
        zeile.strip()
        for zeile in pathlib.Path(sys.argv[1]).read_text(encoding="utf-8").splitlines()
        if zeile.strip()
    )

    zwischenspeicher = {}
    fehlend = []
    geprueft = 0

    for behauptung in sys.argv[2:]:
        klasse, _, gesucht = behauptung.partition("#")
        if klasse not in zwischenspeicher:
            zwischenspeicher[klasse] = signaturen(classpath, klasse)
        text = zwischenspeicher[klasse]

        if not text.strip():
            fehlend.append(f"{klasse} gibt es nicht")
            continue
        geprueft += 1
        if gesucht and gesucht not in text:
            fehlend.append(f"{klasse}: kein '{gesucht}'")

    if fehlend:
        for eintrag in fehlend:
            print(f"::error title=API fehlt::{eintrag}")
        print("::error title=API-Pruefung::" + "%0A".join(fehlend))
        return 1

    print(f"::notice title=API-Pruefung::{geprueft} Behauptungen bestaetigt.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
