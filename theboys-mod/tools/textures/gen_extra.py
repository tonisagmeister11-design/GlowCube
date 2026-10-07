#!/usr/bin/env python3
"""Extra textures: Soldier Boy's heater shield (face + icon), Butcher's crowbar, V-One recolour.

Run from anywhere: python3 tools/textures/gen_extra.py
"""
import colorsys
import os

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), '..', '..', 'src', 'main', 'resources', 'assets', 'theboys', 'textures')

GREEN_D = (38, 52, 24, 255)
GREEN = (62, 80, 34, 255)
GREEN_L = (84, 104, 46, 255)
GOLD_D = (122, 92, 44, 255)
GOLD = (176, 141, 87, 255)
GOLD_L = (222, 190, 120, 255)
MAROON = (107, 30, 35, 255)
OUT = (20, 22, 14, 255)


def kite_inside(x, y, w, h):
    """Heater shield outline in a w*h box: straight top, sides curving into a point at the bottom."""
    cx = (w - 1) / 2
    if y < 0 or y >= h:
        return False
    top = h * 0.42
    if y <= top:
        half = w / 2
    else:
        t = (y - top) / (h - top)
        half = (w / 2) * (1 - t ** 1.6)
    return abs(x - cx) <= half - 0.25


def shield_face(w, h):
    img = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    px = img.load()
    for y in range(h):
        for x in range(w):
            if not kite_inside(x, y, w, h):
                continue
            edge = not (kite_inside(x - 1, y, w, h) and kite_inside(x + 1, y, w, h) and kite_inside(x, y - 1, w, h) and kite_inside(x, y + 1, w, h))
            rim = not (kite_inside(x - 3, y, w, h) and kite_inside(x + 3, y, w, h) and kite_inside(x, y - 3, w, h) and kite_inside(x, y + 3, w, h))
            if edge:
                px[x, y] = OUT
            elif rim:
                px[x, y] = GOLD_L if (x + y) % 5 == 0 else GOLD
            else:
                # brushed dark green metal with a vertical maroon stripe
                shade = GREEN if (x * 7 + y * 3) % 11 else GREEN_L
                if (y * 13 + x * 5) % 17 == 0:
                    shade = GREEN_D
                if abs(x - (w - 1) / 2) < w * 0.07 and y > h * 0.62:
                    shade = MAROON
                px[x, y] = shade
    # eagle with spread wings + star on its breast (drawn on a 32x64 grid, scaled)
    from PIL import ImageDraw
    big = Image.new('RGBA', (32, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(big)
    for side in (-1, 1):
        # wing: a swept fan from the shoulder up and out
        root = (16 + side * 2, 24)
        tip = (16 + side * 13, 10)
        lower = (16 + side * 12, 22)
        d.polygon([root, tip, lower, (16 + side * 3, 31)], fill=GOLD)
        # feather lines
        for k in range(4):
            fx = 16 + side * (5 + k * 2.2)
            d.line([(fx, 14 + k * 0.5 + (4 - k) * 1.5), (fx + side * 1.5, 25 + k)], fill=GOLD_D, width=1)
        d.line([root, tip], fill=GOLD_L, width=1)
    # body and tail
    d.ellipse([12, 20, 20, 42], fill=GOLD)
    d.polygon([(13, 40), (19, 40), (21, 49), (16, 46), (11, 49)], fill=GOLD_D)
    # head with beak and eye
    d.ellipse([13, 13, 19, 21], fill=GOLD_L)
    d.polygon([(19, 16), (22, 17), (19, 19)], fill=(240, 200, 90, 255))
    d.point((17, 16), fill=OUT)
    # talons
    d.line([(13, 42), (11, 45)], fill=GOLD_D)
    d.line([(19, 42), (21, 45)], fill=GOLD_D)
    scaled = big.resize((w, h), Image.NEAREST)
    spx = scaled.load()
    for y in range(h):
        for x in range(w):
            if spx[x, y][3] > 0 and kite_inside(x, y, w, h) and px[x, y] not in (OUT, GOLD, GOLD_L):
                px[x, y] = spx[x, y]
    # five-pointed star on the breast
    import math
    sx, sy = w / 32.0, h / 64.0
    scx, scy, R = w / 2 - 0.5, 30 * sy, 3.6 * sx
    pts = []
    for k in range(10):
        r = R if k % 2 == 0 else R * 0.42
        a = -math.pi / 2 + k * math.pi / 5
        pts.append((scx + r * math.cos(a), scy + r * math.sin(a)))
    for y in range(h):
        for x in range(w):
            inside = False
            j = len(pts) - 1
            for i in range(len(pts)):
                xi, yi = pts[i]
                xj, yj = pts[j]
                if ((yi > y + 0.5) != (yj > y + 0.5)) and (x + 0.5 < (xj - xi) * (y + 0.5 - yi) / (yj - yi + 1e-9) + xi):
                    inside = not inside
                j = i
            if inside and kite_inside(x, y, w, h):
                px[x, y] = (236, 236, 228, 255)
    return img


def crowbar():
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = img.load()
    dark, mid, light = (28, 30, 34, 255), (70, 74, 80, 255), (128, 132, 140, 255)
    # 2px diagonal shaft with outline and highlight
    for i in range(1, 13):
        x, y = i + 1, 14 - i
        for (dx, dy, c) in [(-1, 0, dark), (0, 1, dark), (0, 0, mid), (1, 0, mid), (0, -1, light), (1, -1, dark)]:
            if 0 <= x + dx < 16 and 0 <= y + dy < 16 and px[x + dx, y + dy][3] == 0 or c is light:
                px[x + dx, y + dy] = c
    # hooked claw at the top, curling back down
    for (x, y, c) in [(13, 0, dark), (14, 0, mid), (15, 1, mid), (15, 2, mid), (15, 3, dark), (14, 4, mid), (13, 4, dark), (14, 1, light)]:
        px[x, y] = c
    # flat chisel end
    for (x, y, c) in [(0, 14, dark), (0, 15, mid), (1, 15, mid), (2, 15, dark)]:
        px[x, y] = c
    # blood
    for (x, y) in [(11, 3), (12, 2), (10, 5), (13, 3)]:
        px[x, y] = (138, 3, 3, 255)
    return img


def recolour_v_one(path):
    """V-One looks like a cloudy, baby-blue serum (paler and milkier than standard Compound V)."""
    img = Image.open(path).convert('RGBA')
    px = img.load()
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            h, l, s = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
            # liquid pixels: yellow/orange hues
            if 0.05 <= h <= 0.2 and s > 0.4 and l > 0.25:
                nl = min(0.95, 0.62 + l * 0.35)
                nr, ng, nb = colorsys.hls_to_rgb(0.56, nl, 0.75)
                px[x, y] = (int(nr * 255), int(ng * 255), int(nb * 255), a)
    img.save(path)


def shield_icon():
    w, h = 13, 16
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = img.load()
    ox = 1
    for y in range(h):
        for x in range(w):
            if not kite_inside(x, y, w, h):
                continue
            edge = not (kite_inside(x - 1, y, w, h) and kite_inside(x + 1, y, w, h) and kite_inside(x, y - 1, w, h) and kite_inside(x, y + 1, w, h))
            rim = not (kite_inside(x - 2, y, w, h) and kite_inside(x + 2, y, w, h) and kite_inside(x, y - 2, w, h) and kite_inside(x, y + 2, w, h))
            px[x + ox, y] = OUT if edge else GOLD if rim else GREEN
    # tiny eagle: spread wings (a wide V) + head + white star
    for (x, y, c) in [(3, 4, GOLD), (4, 5, GOLD), (5, 6, GOLD_L), (6, 6, GOLD_L), (7, 6, GOLD_L), (8, 6, GOLD_L), (9, 6, GOLD_L), (10, 5, GOLD), (11, 4, GOLD),
                      (4, 4, GOLD_D), (10, 4, GOLD_D), (7, 4, GOLD_L), (7, 5, GOLD_L), (6, 7, GOLD), (7, 7, (236, 236, 228, 255)), (8, 7, GOLD),
                      (7, 8, GOLD), (6, 9, GOLD_D), (8, 9, GOLD_D), (7, 10, MAROON), (7, 11, MAROON)]:
        px[x, y] = c
    return img


def main():
    face = shield_face(32, 64)
    face.save(os.path.join(ROOT, 'item', 'soldier_boy_shield_face.png'))
    face.save(os.path.join(ROOT, 'entity', 'soldier_boy_shield.png'))
    shield_icon().save(os.path.join(ROOT, 'item', 'soldier_boy_shield.png'))
    crowbar().save(os.path.join(ROOT, 'item', 'crowbar.png'))
    recolour_v_one(os.path.join(ROOT, 'item', 'compound_v1.png'))
    print('extra textures written')


if __name__ == '__main__':
    main()
