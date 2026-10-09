"""Black Adam: skin, cape, Shazam serum (icon + 3D sheet) and HUD icon."""
import os
from common import *
from gen_skins import Box, mk_head, arm_edge, put, hline, rows
from gen_misc import syringe
from gen_extra import syringe_sheet

K, K2, K3, HI = C("#141416"), C("#222328"), C("#33353c"), C("#4a4d57")
GOLD, GOLD_D, GOLD_L, GOLD_W = C("#e8b32a"), C("#a8781a"), C("#ffd86a"), C("#fff4c4")
SIDES = ("front", "back", "left", "right")


def muscle(box, seed, faces=("top", "bottom") + SIDES):
    """Black suit with a sculpted look: lighter ridges, dark creases."""
    for n in faces:
        im = box.f[n]
        for y in range(im.height):
            for x in range(im.width):
                v = h2(x, y, seed * 17 + len(n)) % 13
                px(im, x, y, K2 if v == 0 else C("#0c0c0e") if v == 1 else K)


def skin():
    img = new(64, 64)
    hb, ho = mk_head(dict(skin="#b07a54", skin_d="#925f3e", hair="#17120f", hair_d="#0d0a08", hair_l="#2a221c",
                          eye="#2a1a10", brow="#120d0a", mouth="#7a4434", brow_low=True,
                          back_rows=5, front_h=[1, 1, 1, 1, 1, 1, 1, 1], side_h=[1, 1, 1, 2, 2, 3, 3, 3],
                          ov=dict(back=0, side=[0] * 8, front=[0] * 8)))
    f = hb.f["front"]
    # stern face: heavy brow, a faint golden glow in the eyes
    px(f, 2, 4, C("#e9c766")); px(f, 5, 4, C("#e9c766"))
    px(f, 1, 3, C("#120d0a")); px(f, 6, 3, C("#120d0a"))
    px(f, 3, 7, C("#925f3e")); px(f, 4, 7, C("#925f3e"))
    hb.place(img, 0, 0); ho.place(img, 32, 0)

    # ---- body: black suit, the golden lightning bolt across the chest, gold belt
    b, o = Box(8, 12, 4), Box(8, 12, 4)
    muscle(b, 2)
    fr = b.f["front"]
    # pecs and abs
    for x in (1, 2, 5, 6):
        px(fr, x, 4, K2)
    for y in (6, 7):
        px(fr, 2, y, K2); px(fr, 5, y, K2)
    px(fr, 3, 6, C("#0c0c0e")); px(fr, 4, 6, C("#0c0c0e"))
    bolt = [(5, 0), (6, 0), (4, 1), (5, 1), (3, 2), (4, 2), (2, 3), (3, 3), (4, 3), (5, 3), (6, 3), (4, 4), (5, 4), (3, 5), (4, 5), (2, 6), (3, 6), (2, 7)]
    for (x, y) in bolt:
        px(fr, x, y, GOLD)
    for (x, y) in ((5, 0), (4, 1), (3, 2), (2, 3), (3, 3)):
        px(fr, x, y, GOLD_L)
    for (x, y) in ((6, 3), (5, 4), (4, 5), (3, 6)):
        px(fr, x, y, GOLD_D)
    # belt with the big buckle
    for n in SIDES:
        hline(b, n, 8, 0, b.f[n].width - 1, GOLD_D)
        hline(b, n, 9, 0, b.f[n].width - 1, GOLD)
    for x in (3, 4):
        px(fr, x, 8, GOLD_W); px(fr, x, 9, GOLD_L); px(fr, x, 10, GOLD_D)
    b.fill(K2, ("top",))
    # back: spine ridge
    for y in range(1, 8):
        px(b.f["back"], 3, y, K2); px(b.f["back"], 4, y, C("#0c0c0e"))
    # overlay: gold-trimmed collar
    for n in SIDES:
        hline(o, n, 0, 0, o.f[n].width - 1, GOLD_D)
    px(o.f["front"], 3, 0, GOLD_L); px(o.f["front"], 4, 0, GOLD_L)
    b.place(img, 16, 16); o.place(img, 16, 32)

    # ---- arms: black, gold bracers, bare hands
    for side, (ox, oy), (oox, ooy) in (("R", (40, 16), (40, 32)), ("L", (32, 48), (48, 48))):
        a, ao = Box(4, 12, 4), Box(4, 12, 4)
        muscle(a, 5 if side == "R" else 6)
        outer, fx, bx = arm_edge(side)
        for n in SIDES:
            rows(a, n, 0, 1, K2)
            rows(a, n, 6, 9, GOLD)
            hline(a, n, 6, 0, 3, GOLD_L)
            hline(a, n, 9, 0, 3, GOLD_D)
            rows(a, n, 10, 11, C("#b07a54"))
            hline(a, n, 11, 0, 3, C("#925f3e"))
        # a little lightning engraved on the bracer
        put(a, outer, 1, 7, GOLD_W); put(a, outer, 2, 8, GOLD_W)
        a.fill(C("#925f3e"), ("bottom",))
        # overlay: shoulder plates
        ao.fill(K3, ("top",))
        for n in SIDES:
            hline(ao, n, 0, 0, 3, K3)
            hline(ao, n, 1, 0, 3, GOLD_D if n == outer else K2)
        a.place(img, ox, oy); ao.place(img, oox, ooy)

    # ---- legs: black, high black boots with gold rims
    for (ox, oy), (oox, ooy) in (((0, 16), (0, 32)), ((16, 48), (0, 48))):
        l, lo = Box(4, 12, 4), Box(4, 12, 4)
        muscle(l, 9)
        for n in SIDES:
            rows(l, n, 7, 11, C("#0b0b0c"))
            hline(l, n, 7, 0, 3, GOLD)
            hline(l, n, 11, 0, 3, C("#050505"))
        put(l, "front", 1, 4, K2); put(l, "front", 2, 4, K2)
        l.fill(C("#050505"), ("bottom",))
        l.place(img, ox, oy)
    return img


def cape():
    img = new(64, 32)
    for y in range(16):
        for x in range(10):
            c = K
            if x in (2, 5, 8):
                c = C("#0b0b0d")
            if x in (3, 6):
                c = K2
            px(img, 1 + x, 1 + y, c)
            px(img, 12 + x, 1 + y, C("#1a1a1e") if x % 3 else C("#101012"))
    for x in range(10):
        px(img, 1 + x, 0, GOLD); px(img, 11 + x, 0, GOLD_D)
        px(img, 1 + x, 16, GOLD_D)
    for y in range(16):
        px(img, 0, 1 + y, GOLD_D); px(img, 11, 1 + y, GOLD_D)
    # golden lightning down the back
    for (x, y) in ((6, 3), (5, 4), (6, 4), (4, 5), (5, 5), (5, 6), (4, 7), (3, 8)):
        px(img, x, y, GOLD)
    return img


def hud_icon():
    im = new(16, 16)
    for y in range(16):
        for x in range(16):
            if (x - 7.5) ** 2 + (y - 7.5) ** 2 <= 7.4 ** 2:
                px(im, x, y, K if (x + y) % 4 else K2)
    bolt = ["......####",
            ".....####.",
            "....####..",
            "...#######",
            "......###.",
            ".....###..",
            "....###...",
            "...##.....",
            "..#......."]
    for j, row in enumerate(bolt):
        for i, ch in enumerate(row):
            if ch == "#":
                px(im, 3 + i, 3 + j, GOLD_L if i + j < 9 else GOLD)
    return outline(im, C("#f2c230"))


def run():
    save(skin(), "entity/suit/black_adam.png")
    save(cape(), "entity/black_adam_cape.png")
    save(hud_icon(), "gui/power/black_adam.png")
    icon = syringe(liquid=["#fff4c4", "#f2c230", "#a8781a"], cap="#141416", band="#f2c230", hub="#33353c",
                   sparkle=[(4, 4, "#fff4c4"), (3, 3, "#ffffff"), (12, 11, "#ffd86a")])
    save(icon, "item/shazam_serum.png")
    sheet = syringe_sheet((242, 194, 48, 255), (255, 244, 196, 255), (20, 20, 22, 255))
    sheet.save(os.path.join(ROOT, "textures", "item", "shazam_serum_3d.png"))


if __name__ == "__main__":
    run()
