#!/usr/bin/env python3
"""Arbeitet die Farbtextur eines zugelieferten Fahrzeugmodells um.

Manche Erkennungsmerkmale eines Fahrzeugs stecken nicht in der Geometrie,
sondern in der Textur: Kuehlergrill, Leuchtengrafik, Schriftzuege. Dieses
Werkzeug ersetzt sie durch eigene Gestaltung, damit das Fahrzeug zur Marke
KALDRA gehoert und nicht mehr wie ein bestimmtes Serienmodell aussieht.

Die Bearbeitung steht hier als Programm und nicht von Hand im Bildbearbeiter,
damit sie nachvollziehbar und wiederholbar bleibt: Wenn die Ausgangstextur
noch einmal gebraucht wird, laeuft derselbe Schritt erneut.

Alle Bereiche sind in Anteilen der Texturgroesse angegeben (0 bis 1), damit
dieselbe Angabe bei jeder Aufloesung passt.

Aufruf:
    python3 tools/retexture_vehicle.py --in=<textur.jpg> [--out=<textur.jpg>]
                                       [--preview=<bild.png>]

--preview zeichnet nur die Bereiche ein, ohne etwas zu veraendern - zum
Pruefen, ob die Angaben sitzen.

Mit --only-patches und --patch laesst sich jede beliebige Textur stellenweise
uebermalen, etwa ein Aufdruck auf einem Kleidungsstueck:

    python3 tools/retexture_vehicle.py --only-patches \
        --in=assets/models/people/mann_a_53.png \
        --patch=0.085,0.600,0.210,0.660
"""

import argparse
import sys

from PIL import Image, ImageDraw

# --------------------------------------------------------------- Bereiche
# Kuehlergrill: sieben senkrechte Schlitze sind ein Erkennungszeichen und
# werden durch waagerechte Stege ersetzt.
GRILLE = (0.127, 0.4925, 0.311, 0.5355)

# Rueckleuchten: das kreuzfoermige Leuchtbild wird ein waagerechtes Lichtband.
LAMPS = [
    (0.6135, 0.4530, 0.6670, 0.5000),
    (0.9115, 0.4530, 0.9700, 0.5000),
]

# Reste einer frueheren Bearbeitung auf der Heckklappe: dort standen
# Schriftzuege, die grob uebermalt wurden. Hier kommt die Wagenfarbe hin.
# Die Farbe wird aus der Umgebung gemittelt; der zweite Eintrag je Zeile ist
# nur noch eine Notiz, welcher Bereich gemeint war.
# Dritter Eintrag: feste Farbe, wo die Umgebung nicht einheitlich ist (der
# erste Bereich grenzt oben an die dunkle Heckscheibe - ein Mittelwert
# rundherum waere dort grau statt weiss).
PATCHES = [
    ((0.736, 0.4480, 0.810, 0.4780), "Heckklappe, Rest einer frueheren Bearbeitung", (186, 186, 197)),
    ((0.8780, 0.5145, 0.9210, 0.5335), "Modellschriftzug am hinteren Kotfluegel", None),
]

# Kennzeichentafel: bleibt leer, nur eine neutrale Flaeche.
PLATE = (0.7290, 0.4770, 0.8290, 0.5030)

DARK = (24, 25, 28)
BAR = (126, 129, 134)
LAMP_RED = (122, 16, 20)
LAMP_GLOW = (214, 38, 34)
PLATE_COL = (208, 208, 202)


def px(region, size):
    w, h = size
    x0, y0, x1, y1 = region
    return [int(x0 * w), int(y0 * h), int(x1 * w), int(y1 * h)]


def draw_grille(img, d):
    """Waagerechte Stege statt senkrechter Schlitze."""
    x0, y0, x1, y1 = px(GRILLE, img.size)
    d.rectangle([x0, y0, x1, y1], fill=DARK)
    bars = 4
    span = y1 - y0
    bar_h = max(2, int(span * 0.085))
    gap = (span - bars * bar_h) / (bars + 1)
    for i in range(bars):
        by = int(y0 + gap * (i + 1) + bar_h * i)
        inset = int((x1 - x0) * 0.035)
        d.rectangle([x0 + inset, by, x1 - inset, by + bar_h], fill=BAR)
    return (x0, y0, x1, y1)


def draw_lamp(img, d, region):
    """Waagerechtes Lichtband statt Kreuz. Das dunkle Gehaeuse bleibt."""
    x0, y0, x1, y1 = px(region, img.size)
    bw = int((x1 - x0) * 0.17)
    bh = int((y1 - y0) * 0.15)
    ix0, iy0, ix1, iy1 = x0 + bw, y0 + bh, x1 - bw, y1 - bh
    d.rectangle([ix0, iy0, ix1, iy1], fill=LAMP_RED)
    band_h = max(2, int((iy1 - iy0) * 0.26))
    by = int((iy0 + iy1) / 2 - band_h / 2)
    d.rectangle([ix0 + 2, by, ix1 - 2, by + band_h], fill=LAMP_GLOW)


def ring_colour(img, box, pad=6):
    """Mittlere Farbe eines schmalen Rahmens um den Bereich.

    Ein einzelner Quellpunkt trifft leicht daneben - auf eine Scheibe oder eine
    Kante - und hinterlaesst dann einen Fleck in falscher Farbe. Der Mittelwert
    rundherum passt sich der oertlichen Helligkeit an.
    """
    x0, y0, x1, y1 = box
    w, h = img.size
    r, g, b, n = 0, 0, 0, 0
    for x in range(max(0, x0 - pad), min(w, x1 + pad)):
        for y in (max(0, y0 - pad), min(h - 1, y1 + pad - 1)):
            p = img.getpixel((x, y))
            r += p[0]; g += p[1]; b += p[2]; n += 1
    for y in range(max(0, y0 - pad), min(h, y1 + pad)):
        for x in (max(0, x0 - pad), min(w - 1, x1 + pad - 1)):
            p = img.getpixel((x, y))
            r += p[0]; g += p[1]; b += p[2]; n += 1
    if n == 0:
        return (255, 255, 255)
    return (r // n, g // n, b // n)


def clean_patches(img, d):
    for region, _note, fixed in PATCHES:
        box = px(region, img.size)
        d.rectangle(box, fill=fixed or ring_colour(img, box))


def draw_plate(img, d):
    x0, y0, x1, y1 = px(PLATE, img.size)
    d.rectangle([x0, y0, x1, y1], fill=PLATE_COL)
    d.rectangle([x0, y0, x1, y1], outline=(120, 122, 126), width=2)


def stamp_badge(img, box, badge_path):
    """Setzt das Markenzeichen mittig in den angegebenen Kasten."""
    try:
        badge = Image.open(badge_path).convert("RGBA")
    except OSError:
        print("Hinweis: kein Markenbild unter %s - Grill bleibt ohne Zeichen" % badge_path)
        return
    x0, y0, x1, y1 = box
    h = int((y1 - y0) * 0.56)
    if h < 4:
        return
    badge = badge.resize((int(h * 1.1547), h), Image.LANCZOS)
    cx = (x0 + x1) // 2 - badge.width // 2
    cy = (y0 + y1) // 2 - badge.height // 2
    img.paste(badge, (cx, cy), badge)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--in", dest="src", required=True)
    ap.add_argument("--out", dest="dst")
    ap.add_argument("--badge", dest="badge", default="")
    ap.add_argument("--preview", dest="preview")
    ap.add_argument(
        "--patch", dest="patches", action="append", default=[],
        help="x0,y0,x1,y1 (Anteile) - Bereich mit der Umgebungsfarbe ueberdecken. "
             "Mehrfach angebbar; wirkt ohne die fahrzeugeigenen Bereiche, also "
             "auch auf Texturen von Kleidung oder Gebaeuden.")
    ap.add_argument(
        "--only-patches", dest="only_patches", action="store_true",
        help="Nur die mit --patch angegebenen Bereiche bearbeiten.")
    args = ap.parse_args()

    img = Image.open(args.src).convert("RGB")
    d = ImageDraw.Draw(img)

    extra = []
    for text in args.patches:
        v = text.split(",")
        if len(v) < 4:
            print("WARNUNG: --patch braucht vier Werte, ignoriert:", text)
            continue
        extra.append(tuple(float(x) for x in v[:4]))

    if args.only_patches:
        for region in extra:
            box = px(region, img.size)
            d.rectangle(box, fill=ring_colour(img, box))
        out = args.dst or args.src
        img.save(out, quality=94)
        print("Bereiche ueberdeckt:", len(extra), "->", out)
        return 0

    if args.preview:
        for region, colour in [(GRILLE, (255, 0, 255))] + [(r, (0, 255, 255)) for r in LAMPS] \
                + [(PLATE, (255, 255, 0))] + [(r, (0, 255, 0)) for r, _n, _c in PATCHES]:
            d.rectangle(px(region, img.size), outline=colour, width=4)
        img.save(args.preview)
        print("Vorschau geschrieben:", args.preview)
        return 0

    box = draw_grille(img, d)
    for region in LAMPS:
        draw_lamp(img, d, region)
    clean_patches(img, d)
    for region in extra:
        b = px(region, img.size)
        d.rectangle(b, fill=ring_colour(img, b))
    draw_plate(img, d)
    if args.badge:
        stamp_badge(img, box, args.badge)

    out = args.dst or args.src
    img.save(out, quality=94)
    print("Textur umgearbeitet:", out, img.size)
    return 0


if __name__ == "__main__":
    sys.exit(main())
