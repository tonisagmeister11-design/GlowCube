#!/usr/bin/env python3
"""
Sucht zur Spielfassung (z. B. 1.21.11 oder 26.3) die passende Paper-API und
Java-Fassung und schreibt sie als GitHub-Output:
  paper_version=...   java_version=...

Aufruf: paper-fassung.py <fassung>
"""

import os
import sys
import urllib.request
import xml.etree.ElementTree as ET

MAVEN = ("https://repo.papermc.io/repository/maven-public/"
         "io/papermc/paper/paper-api/maven-metadata.xml")


def main():
    fassung = sys.argv[1]
    root = ET.fromstring(urllib.request.urlopen(MAVEN, timeout=60).read())
    alle = [v.text for v in root.findall("versioning/versions/version")]
    passend = [v for v in alle if v == fassung or v.startswith(fassung + "-") or v.startswith(fassung + ".")]
    print("Paper-API-Fassungen fuer", fassung, ":", passend[-15:])
    if not passend:
        naechste = [v for v in alle if v.split("-")[0].split(".")[0] == fassung.split(".")[0]]
        print("::error title=Paper::Keine Paper-API fuer " + fassung + ". Vorhanden: " + ", ".join(naechste[-12:]))
        return 1
    bevorzugt = [v for v in passend if v == fassung + "-R0.1-SNAPSHOT"]
    wahl = bevorzugt[0] if bevorzugt else passend[-1]
    java = "21" if fassung.startswith("1.") else "25"
    print("Gewaehlt:", wahl, "mit Java", java)
    with open(os.environ.get("GITHUB_OUTPUT", "/dev/stdout"), "a", encoding="utf-8") as aus:
        aus.write(f"paper_version={wahl}\njava_version={java}\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
