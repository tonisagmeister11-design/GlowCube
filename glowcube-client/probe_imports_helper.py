"""Gemeinsame JAR-Leserei fuer probe-imports.py und probe-search.py."""

import io
import pathlib
import zipfile


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


