"""MiniMaus: mouse skin, 3D ear/tail texture and the Mini V syringe (icon + 3D sheet)."""
import os
from common import *
from gen_skins import Box, rows, hline, put
from gen_misc import syringe
from gen_extra import syringe_sheet

FUR, FUR_D, FUR_L = C("#8e9098"), C("#71737c"), C("#a9abb3")
FUR_DD = C("#5c5e66")
CREAM, CREAM_D = C("#efe3d0"), C("#d9cab3")
PINK, PINK_D, PINK_L = C("#f2a2b4"), C("#d97a92"), C("#f9c9d4")
EYE, EYE_HI = C("#121216"), C("#ffffff")
TOOTH = C("#fbfbf4")
BELT, BUCKLE, BUCKLE_D = C("#b3202c"), C("#f2c230"), C("#c2911a")


def fur(box, faces=("top", "bottom", "right", "front", "left", "back"), seed=1, y0=0, y1=None):
    """Soft fur: base with darker strands and a few light tips."""
    for n in faces:
        im = box.f[n]
        for y in range(y0, im.height if y1 is None else y1):
            for x in range(im.width):
                v = h2(x, y, seed + len(n) * 7) % 11
                c = FUR_L if v == 0 else FUR_D if v in (1, 2) else FUR_DD if v == 3 and y % 2 else FUR
                px(im, x, y, c)


def head():
    b, o = Box(8, 8, 8), Box(8, 8, 8)
    fur(b, seed=3)
    f = b.f["front"]
    # big glossy eyes, highlight top-left
    for (x, y, c) in ((1, 3, EYE_HI), (2, 3, EYE), (1, 4, EYE), (2, 4, EYE),
                      (5, 3, EYE_HI), (6, 3, EYE), (5, 4, EYE), (6, 4, EYE)):
        px(f, x, y, c)
    px(f, 1, 2, FUR_L); px(f, 6, 2, FUR_L)
    # muzzle, pink nose, mouth and buck teeth
    for (x, y) in ((2, 5), (5, 5), (1, 6), (2, 6), (5, 6), (6, 6), (2, 7), (5, 7)):
        px(f, x, y, CREAM)
    px(f, 3, 5, PINK); px(f, 4, 5, PINK_D)
    px(f, 3, 6, CREAM_D); px(f, 4, 6, CREAM_D)
    px(f, 3, 7, TOOTH); px(f, 4, 7, TOOTH)
    px(f, 0, 5, PINK_L); px(f, 7, 5, PINK_L)  # blush
    for x in range(8):
        px(b.f["bottom"], x, 0, CREAM_D)
        for y in range(1, 8):
            px(b.f["bottom"], x, y, CREAM)
    # sides: cream cheeks towards the front
    for side, xs in (("left", (0, 1)), ("right", (6, 7))):
        for x in xs:
            for y in (5, 6, 7):
                px(b.f[side], x, y, CREAM)
    # darker fur stripe over the crown
    for y in range(8):
        px(b.f["top"], 3, y, FUR_D); px(b.f["top"], 4, y, FUR_D)
    # overlay: whiskers sticking out of the cheeks, a tuft of hair on top
    of = o.f["front"]
    for (x, y) in ((0, 5), (1, 6), (0, 7), (7, 5), (6, 6), (7, 7)):
        px(of, x, y, FUR_DD)
    for side, xs in (("left", (0, 1, 2)), ("right", (5, 6, 7))):
        for i, x in enumerate(xs):
            px(o.f[side], x, 5 + (i % 2), FUR_DD)
            px(o.f[side], x, 7 - (i % 2), C("#4c4e55"))
    for (x, y) in ((3, 0), (4, 0), (4, 1), (2, 1)):
        px(o.f["front"], x, y, FUR_L)
    for (x, y) in ((3, 6), (4, 6), (3, 7), (4, 7), (2, 7)):
        px(o.f["top"], x, y, FUR_L)
    return b, o


def body():
    b, o = Box(8, 12, 4), Box(8, 12, 4)
    fur(b, seed=5)
    f = b.f["front"]
    # cream belly
    for y in range(12):
        for x in range(8):
            inner = 2 <= x <= 5 and 1 <= y <= 10 or 3 <= x <= 4
            if inner:
                px(f, x, y, CREAM if h2(x, y, 4) % 6 else CREAM_D)
    # hero belt with an "M" buckle
    hline(b, "front", 8, 0, 7, BELT); hline(b, "left", 8, 0, 3, BELT)
    hline(b, "right", 8, 0, 3, BELT); hline(b, "back", 8, 0, 7, BELT)
    for (x, c) in ((2, BUCKLE_D), (3, BUCKLE), (4, BUCKLE), (5, BUCKLE_D)):
        px(f, x, 8, c)
    px(f, 3, 7, BUCKLE_D); px(f, 4, 7, BUCKLE_D); px(f, 3, 9, BUCKLE_D); px(f, 4, 9, BUCKLE_D)
    # darker spine on the back
    for y in range(12):
        if y != 8:
            px(b.f["back"], 3, y, FUR_D); px(b.f["back"], 4, y, FUR_D)
    # overlay: fluffy collar
    for n in ("front", "back"):
        for x in range(8):
            if h2(x, 0, 9) % 3:
                px(o.f[n], x, 0, FUR_L)
    for n in ("left", "right"):
        for x in range(4):
            px(o.f[n], x, 0, FUR_L)
    px(o.f["front"], 3, 1, CREAM); px(o.f["front"], 4, 1, CREAM)
    return b, o


def arm(side):
    a, ao = Box(4, 12, 4), Box(4, 12, 4)
    fur(a, seed=7 if side == "R" else 8)
    for n in ("front", "back", "left", "right"):
        rows(a, n, 10, 11, PINK)
        hline(a, n, 9, 0, 3, FUR_L)
    a.fill(PINK_D, ("bottom",))
    for x in (0, 2):
        put(a, "front", x, 11, PINK_L)  # little fingers
    put(a, "front", 1, 11, PINK_D); put(a, "front", 3, 11, PINK_D)
    # fur cuff on the overlay
    for n in ("front", "back", "left", "right"):
        for x in range(4):
            if h2(x, 9, 3) % 2:
                put(ao, n, x, 9, FUR_L)
    return a, ao


def leg(seed):
    l, lo = Box(4, 12, 4), Box(4, 12, 4)
    fur(l, seed=seed)
    for n in ("front", "back", "left", "right"):
        rows(l, n, 10, 11, PINK)
        hline(l, n, 10, 0, 3, PINK_D)
    l.fill(PINK_D, ("bottom",))
    for x in (0, 1, 2, 3):
        put(l, "front", x, 11, PINK_L if x % 2 == 0 else PINK)  # toes
    for n in ("front", "back", "left", "right"):
        for x in range(4):
            if h2(x, 8, seed) % 3 == 0:
                put(lo, n, x, 9, FUR_L)
    return l, lo


def skin():
    img = new(64, 64)
    hb, ho = head()
    hb.place(img, 0, 0); ho.place(img, 32, 0)
    b, o = body()
    b.place(img, 16, 16); o.place(img, 16, 32)
    for side, (ox, oy), (oox, ooy) in (("R", (40, 16), (40, 32)), ("L", (32, 48), (48, 48))):
        a, ao = arm(side)
        a.place(img, ox, oy); ao.place(img, oox, ooy)
    for seed, (ox, oy), (oox, ooy) in ((2, (0, 16), (0, 32)), (6, (16, 48), (0, 48))):
        l, lo = leg(seed)
        l.place(img, ox, oy); lo.place(img, oox, ooy)
    return img


def parts():
    """32x32: ear front (0..15, 0..15), ear back (16..31, 0..15), tail (0..31, 16..31)."""
    img = new(32, 32)
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d <= 7.6:
                if d > 6.2:
                    c = FUR_D if h2(x, y, 1) % 3 == 0 else FUR
                elif d > 5.0:
                    c = PINK_D
                else:
                    c = PINK_L if d < 2.5 else PINK
                    if h2(x, y, 5) % 9 == 0:
                        c = PINK_D  # little veins
                px(img, x, y, c)
                v = h2(x, y, 2) % 7
                px(img, 16 + x, y, FUR_L if v == 0 else FUR_D if v == 1 else FUR)
    for y in range(16, 32):
        for x in range(32):
            c = PINK if (y - 16) % 4 else PINK_D  # ring segments of the tail
            if x % 8 == 0:
                c = PINK_L
            px(img, x, y, c)
    return img


def run():
    save(skin(), "entity/suit/minimaus.png")
    save(parts(), "entity/minimaus_parts.png")
    icon = syringe(liquid=["#ffd3e1", "#ff6fa5", "#d0266b"], cap="#8e9098", band="#f2c230", hub="#c9cbd2",
                   sparkle=[(4, 4, "#ffd3e1"), (3, 3, "#ffffff"), (12, 11, "#ff9cc2")])
    save(icon, "item/mini_v.png")
    sheet = syringe_sheet((255, 111, 165, 255), (255, 211, 225, 255), (142, 144, 152, 255))
    sheet.save(os.path.join(ROOT, "textures", "item", "mini_v_3d.png"))


if __name__ == "__main__":
    run()
