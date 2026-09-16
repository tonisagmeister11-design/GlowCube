#!/usr/bin/env python3
"""
Zeigt die echten Signaturen ausgewaehlter Klassen mit javap.

Wenn eine Klasse spurlos verschwunden ist, hilft die Suche nach ihrem Namen
nicht weiter. Dann fragt man dort, wo sie benutzt wird: Was nimmt
Screen.render() heute entgegen? Das benennt den Nachfolger eindeutig.

Aufruf: probe-api.py <classpath-datei> <klasse>[:<filter>] ...
"""

import pathlib
import re
import subprocess
import sys


def main():
    if len(sys.argv) < 3:
        print("::error::Zu wenige Argumente")
        return 0

    classpath = ":".join(
        zeile.strip()
        for zeile in pathlib.Path(sys.argv[1]).read_text(encoding="utf-8").splitlines()
        if zeile.strip()
    )

    bericht = []
    for auftrag in sys.argv[2:]:
        klasse, _, muster = auftrag.partition(":")
        try:
            ausgabe = subprocess.run(
                ["javap", "-classpath", classpath, klasse],
                capture_output=True, text=True, timeout=120).stdout
        except Exception as fehler:
            bericht.append(f"{klasse}: javap scheitert ({fehler})")
            continue

        if not ausgabe.strip():
            bericht.append(f"{klasse}: gibt es nicht")
            continue

        zeilen = [z.strip() for z in ausgabe.splitlines() if z.strip().endswith(";")]
        if muster:
            zeilen = [z for z in zeilen if re.search(muster, z)]

        print(f"\n===== {klasse} " + ("(gefiltert: " + muster + ")" if muster else ""))
        for zeile in zeilen[:40]:
            print("   " + zeile)
        bericht.append(f"--- {klasse}")
        bericht.extend("   " + z for z in zeilen[:40])

    print("::notice title=Echte Signaturen::" + "%0A".join(bericht[:160]))
    return 0


if __name__ == "__main__":
    sys.exit(main())
