#!/usr/bin/env python3
"""
Prueft jeden Minecraft-/Fabric-Import gegen die echten JARs und schlaegt bei
Fehlschlag den neuen Namen vor.

Hintergrund: Zwischen den Minecraft-Fassungen wandern Klassen durch die
Pakete. Jeden Namen einzeln per Build-Fehler zu erfragen kostet pro Name
einen Durchlauf. Hier wird stattdessen einmal in die JARs geschaut, die
Loom tatsaechlich aufgeloest hat.

Aufruf: probe-imports.py <datei-mit-classpath>
"""

import collections
import pathlib
import re
import sys
import zipfile

HERE = pathlib.Path(__file__).parent
SOURCES = HERE / "src/main/java"
INTERESSANT = ("net.minecraft.", "net.fabricmc.", "com.mojang.")


def imports_aus_quelltext():
    """Jeder Import auf fremde Klassen, mit den Dateien, die ihn brauchen."""
    gefunden = collections.defaultdict(set)
    for datei in SOURCES.rglob("*.java"):
        for zeile in datei.read_text(encoding="utf-8").splitlines():
            treffer = re.match(r"\s*import\s+(static\s+)?([\w.]+)\s*;", zeile)
            if treffer:
                name = treffer.group(2)
                if name.startswith(INTERESSANT):
                    gefunden[name].add(datei.relative_to(SOURCES).as_posix())
    return gefunden


def klassen_im_classpath(classpath_datei):
    """Alle Klassennamen aus allen JARs des Classpath."""
    alle = set()
    for zeile in pathlib.Path(classpath_datei).read_text(encoding="utf-8").splitlines():
        pfad = zeile.strip()
        if not pfad.endswith(".jar") or not pathlib.Path(pfad).exists():
            continue
        try:
            with zipfile.ZipFile(pfad) as archiv:
                for eintrag in archiv.namelist():
                    if eintrag.endswith(".class"):
                        alle.add(eintrag[:-len(".class")].replace("/", "."))
        except zipfile.BadZipFile:
            continue
    return alle


def main():
    if len(sys.argv) < 2:
        print("::error::Kein Classpath uebergeben")
        return 1

    vorhanden = klassen_im_classpath(sys.argv[1])
    print(f"{len(vorhanden)} Klassen im Classpath gefunden.")
    if not vorhanden:
        print("::error title=Classpath::Keine JARs lesbar - der Bericht waere wertlos.")
        return 1

    # Nachschlagewerk: einfacher Name -> alle vollen Namen.
    nach_kurzname = collections.defaultdict(list)
    for name in vorhanden:
        nach_kurzname[name.rsplit(".", 1)[-1]].append(name)

    fehlend = []
    for name, dateien in sorted(imports_aus_quelltext().items()):
        if name in vorhanden:
            continue
        kurz = name.rsplit(".", 1)[-1]
        # Innere Klassen tauchen als Aussen$Innen auf.
        alternativen = [k for k in nach_kurzname.get(kurz, []) if "$" not in k]
        if not alternativen:
            alternativen = [k for k in vorhanden
                            if k.rsplit(".", 1)[-1].replace("$", ".").endswith(kurz)][:5]
        fehlend.append((name, sorted(alternativen)[:5], sorted(dateien)))

    if not fehlend:
        print("Alle Importe sind im Classpath vorhanden.")
        return 0

    zeilen = []
    print("=" * 70)
    print(f"{len(fehlend)} Importe gehen ins Leere")
    print("=" * 70)
    for name, alternativen, dateien in fehlend:
        vorschlag = alternativen[0] if alternativen else "KEIN TREFFER"
        print(f"\n  {name}")
        print(f"    -> {vorschlag}")
        if len(alternativen) > 1:
            print(f"       weitere: {', '.join(alternativen[1:])}")
        print(f"       gebraucht in: {', '.join(dateien)}")
        zeilen.append(f"{name}  ->  {vorschlag}")

    print("::error title=Umbenannte Klassen::" + "%0A".join(zeilen[:25]))
    return 1


if __name__ == "__main__":
    sys.exit(main())
