#!/usr/bin/env python3
"""
Sucht Klassen im Classpath nach Namensbestandteil.

Wenn eine ganze API ausgetauscht wurde, hilft weder der alte Name noch das
alte Paket. Dann bleibt die Suche nach dem Begriff.

Aufruf: probe-search.py <classpath-datei> <begriff> ...
"""

import sys
import pathlib

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from probe_imports_helper import klassen_im_classpath  # noqa: E402


def main():
    vorhanden = klassen_im_classpath(sys.argv[1])
    bericht = []
    for begriff in sys.argv[2:]:
        treffer = sorted(k for k in vorhanden
                         if begriff.lower() in k.rsplit(".", 1)[-1].lower() and "$" not in k)
        print(f"\n===== '{begriff}': {len(treffer)} Treffer")
        for name in treffer[:40]:
            print("   " + name)
        bericht.append(f"--- {begriff} ({len(treffer)})")
        bericht.extend("   " + t for t in treffer[:12])
    print("::notice title=Namenssuche::" + "%0A".join(bericht[:80]))
    return 0


if __name__ == "__main__":
    sys.exit(main())
