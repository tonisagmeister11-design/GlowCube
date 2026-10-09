"""Black Adam: skin, cape, Shazam serum (icon + 3D sheet) and HUD icon."""
import os
from common import *
from gen_skins import Box, mk_head, arm_edge, put, hline, rows
from gen_misc import syringe
from gen_extra import syringe_sheet

K, K2, K3, HI = C("#2a2d33"), C("#383c44"), C("#464b54"), C("#5a606b")
KD, KDD = C("#1d1f24"), C("#141519")
GOLD, GOLD_D, GOLD_L, GOLD_W = C("#f2a23a"), C("#b8641c"), C("#ffd06a"), C("#fff3c0")
SKIN, SKIN_D, SKIN_L = C("#b47a52"), C("#93603c"), C("#cd9670")
SIDES = ("front", "back", "left", "right")


def suit(box, seed, faces=("top", "bottom") + SIDES):
    """Charcoal suit with a leathery, scaled texture."""
    for n in faces:
        im = box.f[n]
        for y in range(im.height):
            for x in range(im.width):
                v = h2(x + (y % 2), y, seed * 17 + len(n)) % 9
                px(im, x, y, K2 if v == 0 else KD if v in (1, 2) else K)


def skin():
    img = new(64, 64)
    # bald: the "hair" is skin, with a shine on top
    hb, ho = mk_head(dict(skin="#b47a52", skin_d="#93603c", hair="#b07650", hair_d="#a86f4b", hair_l="#b8805a",
                          eye="#2a1a10", brow="#3a2418", mouth="#7a4434", brow_low=True,
                          back_rows=8, front_h=[0] * 8, side_h=[0] * 8,
                          ov=dict(back=0, side=[0] * 8, front=[0] * 8)))
    for (x, y) in ((3, 2), (4, 2), (3, 3), (4, 4), (2, 3)):
        px(hb.f["top"], x, y, SKIN_L)
    f = hb.f["front"]
    px(f, 1, 3, C("#3a2418")); px(f, 6, 3, C("#3a2418"))
    px(f, 3, 7, SKIN_D); px(f, 4, 7, SKIN_D); px(f, 2, 6, SKIN_D); px(f, 5, 6, SKIN_D)
    hb.place(img, 0, 0); ho.place(img, 32, 0)

    # ---- body: charcoal suit, dark V collar from the shoulders, the glowing bolt in it
    b, o = Box(8, 12, 4), Box(8, 12, 4)
    suit(b, 2)
    fr = b.f["front"]
    for i in range(4):
        px(fr, i, i, KDD); px(fr, 7 - i, i, KDD)
        px(fr, i + 1, i, KD); px(fr, 6 - i, i, KD)
    bolt = {(2, 1): GOLD_D, (3, 1): GOLD, (4, 1): GOLD, (5, 1): GOLD_D,
            (3, 2): GOLD_L, (4, 2): GOLD, (5, 2): GOLD_D,
            (2, 3): GOLD, (3, 3): GOLD_W, (4, 3): GOLD_L, (5, 3): GOLD,
            (3, 4): GOLD_L, (4, 4): GOLD_W, (4, 5): GOLD_L, (3, 5): GOLD_D, (4, 6): GOLD, (4, 7): GOLD_D}
    for (x, y), c in bolt.items():
        px(fr, x, y, c)
    # sculpted chest and abs
    for x in (1, 6):
        px(fr, x, 5, K2)
    for y in (6, 7):
        px(fr, 2, y, K2); px(fr, 5, y, K2)
    # wide, layered dark belt
    for n in SIDES:
        hline(b, n, 8, 0, b.f[n].width - 1, K3)
        hline(b, n, 9, 0, b.f[n].width - 1, KDD)
        hline(b, n, 10, 0, b.f[n].width - 1, KD)
    for x in (3, 4):
        px(fr, x, 9, HI)
    b.fill(K2, ("top",))
    for y in range(1, 8):
        px(b.f["back"], 3, y, KD); px(b.f["back"], 4, y, K2)
    # overlay: the bolt glows out a little, raised collar
    for n in SIDES:
        hline(o, n, 0, 0, o.f[n].width - 1, KD)
    px(o.f["front"], 3, 3, C("#ffe7a0", 160)); px(o.f["front"], 4, 4, C("#ffe7a0", 160))
    b.place(img, 16, 16); o.place(img, 16, 32)

    # ---- arms: charcoal, segmented dark bracers, bare hands
    for side, (ox, oy), (oox, ooy) in (("R", (40, 16), (40, 32)), ("L", (32, 48), (48, 48))):
        a, ao = Box(4, 12, 4), Box(4, 12, 4)
        suit(a, 5 if side == "R" else 6)
        outer, fx, bx = arm_edge(side)
        for n in SIDES:
            rows(a, n, 0, 1, K2)
            rows(a, n, 6, 9, KD)
            hline(a, n, 6, 0, 3, K3)
            hline(a, n, 8, 0, 3, KDD)
            rows(a, n, 10, 11, SKIN)
            hline(a, n, 11, 0, 3, SKIN_D)
        put(a, outer, 1, 7, HI)
        a.fill(SKIN_D, ("bottom",))
        ao.fill(K3, ("top",))
        for n in SIDES:
            hline(ao, n, 0, 0, 3, K3)
            hline(ao, n, 1, 0, 3, K2 if n == outer else KD)
        a.place(img, ox, oy); ao.place(img, oox, ooy)

    # ---- legs: charcoal, dark boots
    for (ox, oy), (oox, ooy) in (((0, 16), (0, 32)), ((16, 48), (0, 48))):
        l, lo = Box(4, 12, 4), Box(4, 12, 4)
        suit(l, 9)
        for n in SIDES:
            rows(l, n, 8, 11, KDD)
            hline(l, n, 8, 0, 3, KD)
            hline(l, n, 11, 0, 3, C("#0c0c0e"))
        put(l, "front", 1, 4, K2); put(l, "front", 2, 4, K2)
        l.fill(C("#0c0c0e"), ("bottom",))
        l.place(img, ox, oy)
    return img


def cape():
    """Dark, worn cloak (he wears it with the hood up in the film)."""
    img = new(64, 32)
    for y in range(16):
        for x in range(10):
            v = h2(x, y, 4) % 7
            c = KD if v == 0 else KDD if v == 1 else C("#24262b")
            if x in (2, 5, 8):
                c = KDD
            if y >= 14 and h2(x, y, 8) % 2:
                c = KDD  # frayed hem
            px(img, 1 + x, 1 + y, c)
            px(img, 12 + x, 1 + y, KDD if x % 3 else C("#101114"))
    for x in range(10):
        px(img, 1 + x, 0, KD); px(img, 11 + x, 0, KDD); px(img, 1 + x, 16, KDD)
    for y in range(16):
        px(img, 0, 1 + y, KDD); px(img, 11, 1 + y, KDD)
    return img


def hud_icon():
    im = new(16, 16)
    for y in range(16):
        for x in range(16):
            if (x - 7.5) ** 2 + (y - 7.5) ** 2 <= 7.4 ** 2:
                px(im, x, y, KDD if (x + y) % 4 else KD)
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
