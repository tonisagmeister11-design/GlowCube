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
import io
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


def klassen_aus_jar(quelle, alle, tiefe=0):
    """Klassen eines JARs - samt der JARs, die darin liegen.

    Fabric API liefert seine Module als JARs innerhalb der JAR aus. Wer nur
    die oberste Ebene liest, haelt saemtliche Fabric-Klassen faelschlich fuer
    verschwunden - genau das ist mir beim ersten Anlauf passiert.
    """
    try:
        with zipfile.ZipFile(quelle) as archiv:
            for eintrag in archiv.namelist():
                if eintrag.endswith(".class"):
                    alle.add(eintrag[:-len(".class")].replace("/", "."))
                elif eintrag.endswith(".jar") and tiefe < 2:
                    with archiv.open(eintrag) as innen:
                        klassen_aus_jar(io.BytesIO(innen.read()), alle, tiefe + 1)
    except (zipfile.BadZipFile, OSError):
        pass


def klassen_im_classpath(classpath_datei):
    """Alle Klassennamen aus allen JARs des Classpath."""
    alle = set()
    for zeile in pathlib.Path(classpath_datei).read_text(encoding="utf-8").splitlines():
        pfad = zeile.strip()
        if pfad.endswith(".jar") and pathlib.Path(pfad).exists():
            klassen_aus_jar(pfad, alle)
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
        paket = name.rsplit(".", 1)[0]
        nachbarn = sorted({k.rsplit(".", 1)[-1] for k in vorhanden
                           if k.rsplit(".", 1)[0] == paket and "$" not in k})
        fehlend.append((name, sorted(alternativen)[:5], sorted(dateien), nachbarn))

    if not fehlend:
        print("Alle Importe sind im Classpath vorhanden.")
        return 0

    zeilen = []
    print("=" * 70)
    print(f"{len(fehlend)} Importe gehen ins Leere")
    print("=" * 70)
    for name, alternativen, dateien, nachbarn in fehlend:
        vorschlag = alternativen[0] if alternativen else "KEIN TREFFER"
        print(f"\n  {name}")
        print(f"    -> {vorschlag}")
        if len(alternativen) > 1:
            print(f"       weitere: {', '.join(alternativen[1:])}")
        print(f"       Paket enthaelt: {', '.join(nachbarn) if nachbarn else '(nichts - Paket gibt es nicht mehr)'}")
        print(f"       gebraucht in: {', '.join(dateien)}")
        kurz = f"{name} -> {vorschlag}"
        if not alternativen:
            kurz += " | Paket: " + (", ".join(nachbarn[:80]) if nachbarn else "leer")
        zeilen.append(kurz)

    print("::error title=Umbenannte Klassen::" + "%0A".join(zeilen[:25]))
    return 1


if __name__ == "__main__":
    sys.exit(main())
