"""Starlight, Stormfront (slim model), The Deep and Black Noir: skins, capes, katana and HUD icons."""
from common import *
from gen_skins import Box, mk_head, arm_edge, put, hline, rows

SIDES = ("front", "back", "left", "right")


def arm_slots():
    # (side, base offset, overlay offset)
    return (("R", (40, 16), (40, 32)), ("L", (32, 48), (48, 48)))


def leg_slots():
    return (((0, 16), (0, 32)), ((16, 48), (0, 48)))


def ring(box, y, c, faces=SIDES):
    for n in faces:
        hline(box, n, y, 0, box.f[n].width - 1, c)


def shade(box, base, dark, light, seed=1, faces=("top", "bottom") + SIDES, y0=0, y1=None, rate=9):
    """Fabric with a little noise (rate: higher = calmer)."""
    for n in faces:
        im = box.f[n]
        for y in range(y0, im.height if y1 is None else y1):
            for x in range(im.width):
                v = h2(x, y, seed * 13 + len(n)) % rate
                px(im, x, y, light if v == 0 else dark if v == 1 else base)


def side_shadow(box, dark, faces=("left", "right")):
    """Darker back edge on the side faces so the body reads as round."""
    for n in faces:
        im = box.f[n]
        xs = (0,) if n == "right" else (im.width - 1,)
        for x in xs:
            for y in range(im.height):
                px(im, x, y, dark)


# ================================================================ STARLIGHT
def starlight():
    W, WD, WL, WDD = C("#f4f2ec"), C("#e1ddd3"), C("#ffffff"), C("#c9c4b8")
    SIL, SIL_D, SIL_L = C("#b4bcc7"), C("#7c8794"), C("#e6ebf1")
    BLUE, BLUE_D = C("#8fb4d9"), C("#6a8fb8")
    GOLD = C("#e8c46a")
    SKIN, SKIN_D = C("#f2d2bd"), C("#dcb59d")
    TIGHTS = C("#ecd9c9")
    img = new(64, 64)
    hb, ho = mk_head(dict(skin="#f2d2bd", skin_d="#dcb59d", hair="#d9b76f", hair_d="#b8924a", hair_l="#f0d9a0",
                          eye="#4f7fb0", brow="#b8924a", mouth="#c9727a",
                          back_rows=8, front_h=[3, 2, 1, 1, 1, 1, 2, 3], side_h=[2, 3, 5, 6, 7, 8, 8, 8],
                          ov=dict(back=8, side=[1, 2, 4, 5, 6, 7, 8, 8], front=[2, 1, 0, 0, 0, 0, 1, 2])))
    f = hb.f["front"]
    # eyelashes and a soft blush
    px(f, 1, 3, C("#8a6a3a")); px(f, 6, 3, C("#8a6a3a"))
    px(f, 1, 5, C("#efc2b0")); px(f, 6, 5, C("#efc2b0"))
    # a lock of hair falling over the forehead
    px(ho.f["front"], 2, 1, C("#f0d9a0")); px(ho.f["front"], 2, 2, C("#d9b76f"))
    hb.place(img, 0, 0); ho.place(img, 32, 0)

    # ---- body: white suit, silver star, pale blue belt, pleated skirt
    b, o = Box(8, 12, 4), Box(8, 12, 4)
    shade(b, W, WD, WL, seed=2, rate=11)
    side_shadow(b, WD)
    fr = b.f["front"]
    # V-neck of skin at the collar
    px(fr, 3, 0, SKIN); px(fr, 4, 0, SKIN); px(fr, 3, 1, SKIN_D); px(fr, 4, 1, SKIN)
    # the star
    star = ["...ss...",
            "..ssss..",
            "ssssssss",
            ".ssssss.",
            "..ssss..",
            ".ss..ss.",
            "ss....ss"]
    for j, row in enumerate(star):
        for i, ch in enumerate(row):
            if ch == "s":
                px(fr, i, 1 + j, SIL)
    # outline (darker right/lower edges) + shine on the upper left
    for (x, y) in ((4, 1), (5, 2), (7, 3), (6, 4), (5, 5), (6, 6), (7, 7), (0, 7), (2, 5), (1, 4)):
        px(fr, x, y, SIL_D)
    for (x, y) in ((3, 1), (2, 2), (1, 3), (2, 3), (3, 3), (3, 2)):
        px(fr, x, y, SIL_L)
    px(fr, 3, 4, C("#ffffff"))
    # belt
    ring(b, 8, BLUE)
    px(fr, 3, 8, GOLD); px(fr, 4, 8, GOLD)
    for n in SIDES:
        hline(b, n, 8, 0, 0, BLUE_D)
    # skirt: pleats
    for n in SIDES:
        im = b.f[n]
        for y in range(9, 12):
            for x in range(im.width):
                px(im, x, y, WD if x % 2 else W)
    b.fill(WL, ("top",))
    b.fill(WDD, ("bottom",))
    # back: zip
    for y in range(1, 8):
        px(b.f["back"], 3, y, WD)
    # overlay: skirt flares out, shoulders have silver trim
    for n in SIDES:
        im = o.f[n]
        for y in range(9, 12):
            for x in range(im.width):
                c = W if (x + (n == "back")) % 2 == 0 else WD
                if y == 11:
                    c = BLUE if x % 2 == 0 else BLUE_D
                px(im, x, y, c)
    hline(o, "front", 0, 0, 1, SIL); hline(o, "front", 0, 6, 7, SIL)
    b.place(img, 16, 16); o.place(img, 16, 32)

    # ---- arms (slim): white sleeves with a pale blue cuff, bare hands
    for side, (ox, oy), (oox, ooy) in arm_slots():
        a, ao = Box(3, 12, 4), Box(3, 12, 4)
        shade(a, W, WD, WL, seed=5 if side == "R" else 6, rate=11)
        outer, fx, bx = arm_edge(side)
        for n in SIDES:
            hline(a, n, 8, 0, a.f[n].width - 1, BLUE)
            hline(a, n, 9, 0, a.f[n].width - 1, BLUE_D)
            rows(a, n, 10, 11, SKIN)
            hline(a, n, 11, 0, a.f[n].width - 1, SKIN_D)
        a.fill(SKIN_D, ("bottom",))
        # silver shoulder piece
        for n in SIDES:
            hline(ao, n, 0, 0, ao.f[n].width - 1, SIL)
            hline(ao, n, 1, 0, ao.f[n].width - 1, SIL_D)
        ao.fill(SIL, ("top",))
        a.place(img, ox, oy); ao.place(img, oox, ooy)

    # ---- legs: tights, white boots up to the knee
    for (ox, oy), (oox, ooy) in leg_slots():
        l, lo = Box(4, 12, 4), Box(4, 12, 4)
        l.fill(TIGHTS)
        side_shadow(l, C("#dcc4b1"))
        for n in SIDES:
            rows(l, n, 0, 2, W)
            hline(l, n, 1, 0, 3, WD)
            rows(l, n, 6, 11, W)
            hline(l, n, 6, 0, 3, BLUE)
            hline(l, n, 7, 0, 3, WL)
            hline(l, n, 11, 0, 3, WDD)
        put(l, "front", 1, 9, WD); put(l, "front", 2, 9, WD)
        l.fill(C("#b9bec6"), ("bottom",))
        # overlay: the skirt hem over the thighs
        for n in SIDES:
            for x in range(4):
                put(lo, n, x, 0, W if x % 2 == 0 else WD)
        l.place(img, ox, oy); lo.place(img, oox, ooy)
    return img


def starlight_cape():
    img = new(64, 32)
    W, WD, WDD = C("#f4f2ec"), C("#dedad0"), C("#c4bfb2")
    SIL, SIL_D = C("#d8dce2"), C("#9ea6b0")
    for y in range(16):
        for x in range(10):
            c = W
            if x in (2, 5, 8):
                c = WD
            if y > 12:
                c = WD if x % 2 else W
            px(img, 1 + x, 1 + y, c)
            px(img, 12 + x, 1 + y, WDD if x % 3 == 0 else WD)
    for x in range(10):
        px(img, 1 + x, 0, SIL); px(img, 11 + x, 0, SIL_D)
        px(img, 1 + x, 16, SIL_D)
    for y in range(16):
        px(img, 0, 1 + y, SIL_D); px(img, 11, 1 + y, SIL_D)
    # small silver star between the shoulders
    for (x, y) in ((5, 2), (4, 3), (5, 3), (6, 3), (5, 4), (4, 5), (6, 5)):
        px(img, x, y, SIL)
    return img


# ================================================================ STORMFRONT
def stormfront():
    N, ND, NL = C("#1a1d2b"), C("#10121b"), C("#272c40")
    RED, RED_D = C("#b3212b"), C("#7e1820")
    WH = C("#e8e4dc")
    BRASS, BRASS_D = C("#b8913e"), C("#866427")
    BOLT, BOLT_L = C("#ffd24a"), C("#fff4c2")
    K = C("#0b0c10")
    SKIN, SKIN_D = C("#f0cdb4"), C("#d6ad93")
    img = new(64, 64)
    hb, ho = mk_head(dict(skin="#f0cdb4", skin_d="#d6ad93", hair="#2b1e18", hair_d="#1c130f", hair_l="#45322a",
                          eye="#5a3a22", brow="#1c130f", mouth="#a8262b",
                          back_rows=7, front_h=[3, 2, 2, 1, 1, 2, 2, 3], side_h=[3, 4, 5, 6, 6, 7, 7, 7],
                          ov=dict(back=7, side=[2, 3, 5, 6, 6, 7, 7, 7], front=[2, 2, 1, 0, 0, 1, 2, 2])))
    f = hb.f["front"]
    px(f, 1, 3, C("#1c130f")); px(f, 6, 3, C("#1c130f"))  # dark lashes
    px(f, 3, 6, C("#a8262b")); px(f, 4, 6, C("#c43a3f"))  # red lipstick
    px(f, 0, 5, C("#e2b8a0")); px(f, 7, 5, C("#e2b8a0"))
    # bob: the hair ends in a straight line at the jaw, with a shine
    for x in range(8):
        px(ho.f["back"], x, 7, C("#1c130f"))
    px(ho.f["left"], 3, 1, C("#5a4236")); px(ho.f["right"], 4, 1, C("#5a4236"))
    hb.place(img, 0, 0); ho.place(img, 32, 0)

    # ---- body: navy suit, red/white diagonal sash, lightning emblem, brass buckle
    b, o = Box(8, 12, 4), Box(8, 12, 4)
    hexfill(b.f["front"], N, ND, NL)
    for n in ("back", "left", "right", "top", "bottom"):
        hexfill(b.f[n], N, ND, NL)
    side_shadow(b, ND)
    fr = b.f["front"]
    # high collar
    hline(b, "front", 0, 0, 7, RED); hline(b, "back", 0, 0, 7, RED)
    hline(b, "left", 0, 0, 3, RED); hline(b, "right", 0, 0, 3, RED)
    px(fr, 3, 0, WH); px(fr, 4, 0, WH)
    # lightning bolt across the chest
    bolt = [(5, 1), (4, 2), (5, 2), (3, 3), (4, 3), (2, 4), (3, 4), (4, 4), (5, 4), (4, 5), (3, 6), (2, 7)]
    for (x, y) in bolt:
        px(fr, x, y, BOLT)
    for (x, y) in ((5, 1), (4, 2), (3, 3), (2, 4)):
        px(fr, x, y, BOLT_L)
    px(fr, 6, 1, ND); px(fr, 1, 7, ND)
    # belt with brass buckle
    ring(b, 8, K)
    for x, c in ((2, BRASS_D), (3, BRASS), (4, BRASS), (5, BRASS_D)):
        px(fr, x, 8, c)
    px(fr, 3, 9, BRASS_D); px(fr, 4, 9, BRASS_D)
    # red and white trim down the sides
    for n in ("left", "right"):
        for y in range(1, 12):
            if y != 8:
                put(b, n, 1, y, RED)
                put(b, n, 2, y, WH if y % 3 else RED_D)
    # overlay: shoulder boards and the sash
    for n in ("front", "back"):
        hline(o, n, 0, 0, 1, RED); hline(o, n, 0, 6, 7, RED)
    hline(o, "left", 0, 0, 3, RED); hline(o, "right", 0, 0, 3, RED)
    b.place(img, 16, 16); o.place(img, 16, 32)

    # ---- arms (slim): navy, US flag armband, black gloves
    for side, (ox, oy), (oox, ooy) in arm_slots():
        a, ao = Box(3, 12, 4), Box(3, 12, 4)
        for n in ("top", "bottom") + SIDES:
            hexfill(a.f[n], N, ND, NL)
        a.fill(RED, ("top",))
        outer, fx, bx = arm_edge(side)
        for n in SIDES:
            w = a.f[n].width
            rows(a, n, 8, 11, K)
            hline(a, n, 8, 0, w - 1, C("#23252c"))
        a.fill(K, ("bottom",))
        # armband: blue field with white, red-white stripes
        for n in SIDES:
            w = ao.f[n].width
            hline(ao, n, 2, 0, w - 1, RED)
            hline(ao, n, 3, 0, w - 1, WH)
            hline(ao, n, 4, 0, w - 1, RED)
        put(ao, outer, 1, 2, C("#2c3f8a")); put(ao, outer, 2, 2, C("#2c3f8a"))
        put(ao, outer, 1, 3, WH); put(ao, outer, 2, 3, C("#2c3f8a"))
        # glove cuffs
        for n in SIDES:
            hline(ao, n, 8, 0, ao.f[n].width - 1, C("#2a2c33"))
        a.place(img, ox, oy); ao.place(img, oox, ooy)

    # ---- legs: navy, black boots with a red top
    for (ox, oy), (oox, ooy) in leg_slots():
        l, lo = Box(4, 12, 4), Box(4, 12, 4)
        for n in ("top", "bottom") + SIDES:
            hexfill(l.f[n], N, ND, NL)
        side_shadow(l, ND)
        for n in SIDES:
            rows(l, n, 6, 11, K)
            hline(l, n, 6, 0, 3, RED)
            hline(l, n, 11, 0, 3, C("#050507"))
        put(l, "front", 1, 8, C("#23252c")); put(l, "front", 2, 8, C("#23252c"))
        l.fill(C("#050507"), ("bottom",))
        for n in SIDES:
            hline(lo, n, 6, 0, 3, RED_D)
        l.place(img, ox, oy); lo.place(img, oox, ooy)
    return img


def stormfront_cape():
    img = new(64, 32)
    N, ND, NL = C("#1a1d2b"), C("#10121b"), C("#262b3f")
    R, RD = C("#8e1b22"), C("#6a1218")
    BRASS = C("#b8913e")
    for y in range(16):
        for x in range(10):
            c = N
            if x in (2, 5, 8):
                c = ND
            if x in (3, 6):
                c = NL
            px(img, 1 + x, 1 + y, c)
            # red lining
            px(img, 12 + x, 1 + y, RD if x % 3 == 0 else R)
    for x in range(10):
        px(img, 1 + x, 0, R); px(img, 11 + x, 0, RD)
        px(img, 1 + x, 16, R)
    for y in range(16):
        px(img, 0, 1 + y, R); px(img, 11, 1 + y, R)
    for x in (1, 10):
        px(img, x, 1, BRASS)
    # a small lightning bolt
    for (x, y) in ((6, 3), (5, 4), (6, 4), (4, 5), (5, 5), (4, 6), (3, 7)):
        px(img, x, y, C("#ffd24a"))
    return img


# ================================================================ THE DEEP
def deep_scales(im, y0=0, y1=None, base="#2f7f80", dark="#1f5f63", light="#3f9c9a", deep="#123f44"):
    B, D, L, DD = C(base), C(dark), C(light), C(deep)
    for y in range(y0, im.height if y1 is None else y1):
        for x in range(im.width):
            off = 2 if (y // 2) % 2 else 0
            p = (x + off) % 4
            if y % 2 == 0:
                c = D if p == 0 else L if p == 2 else B
            else:
                c = DD if p in (0, 3) else B
            px(im, x, y, c)


def the_deep():
    GOLD, GOLD_D, GOLD_L = C("#c9a24a"), C("#8f6f2a"), C("#ecc96e")
    BELT = C("#b58a3a")
    GILL, GILL_L = C("#8e3b3b"), C("#c46a6a")
    BOOT, BOOT_L = C("#0f3436"), C("#1a4a4c")
    SKIN, SKIN_D = C("#e2b896"), C("#c69a78")
    img = new(64, 64)
    hb, ho = mk_head(dict(skin="#e2b896", skin_d="#c69a78", hair="#4a3324", hair_d="#33231a", hair_l="#6a4a34",
                          eye="#3b6f8f", brow="#33231a", smirk=True, mouth="#a05a52",
                          back_rows=7, front_h=[2, 2, 1, 1, 1, 1, 2, 2], side_h=[1, 1, 2, 2, 3, 3, 4, 4],
                          ov=dict(back=6, side=[0, 1, 1, 2, 2, 3, 3, 3], front=[1, 1, 1, 1, 1, 1, 1, 1])))
    # swept back: the overlay hair is combed backwards, a wet shine on top
    for x in range(8):
        px(ho.f["top"], x, 1, C("#7a583e") if x % 3 == 0 else C("#4a3324"))
    hb.place(img, 0, 0); ho.place(img, 32, 0)

    # ---- body: teal scale suit, gold emblem, gills on the flanks
    b, o = Box(8, 12, 4), Box(8, 12, 4)
    for n in ("top", "bottom") + SIDES:
        deep_scales(b.f[n])
    fr = b.f["front"]
    # V of gold piping from the shoulders to the sternum
    for i in range(4):
        px(fr, i, i, GOLD); px(fr, 7 - i, i, GOLD)
    px(fr, 3, 4, GOLD_D); px(fr, 4, 4, GOLD_D)
    # emblem: a golden wave / fin
    for (x, y, c) in ((2, 5, GOLD), (3, 5, GOLD_L), (4, 5, GOLD_L), (5, 4, GOLD), (5, 5, GOLD), (2, 6, GOLD_D), (3, 6, GOLD), (4, 6, GOLD), (5, 6, GOLD_D)):
        px(fr, x, y, c)
    ring(b, 8, BELT)
    px(fr, 3, 8, GOLD_L); px(fr, 4, 8, GOLD_L)
    for n in SIDES:
        hline(b, n, 9, 0, b.f[n].width - 1, C("#123f44"))
    # gills
    for n in ("left", "right"):
        for y in (3, 5):
            put(b, n, 1, y, GILL); put(b, n, 2, y, GILL)
            put(b, n, 1, y + 1, GILL_L)
    b.place(img, 16, 16)
    # overlay: gold collar
    for n in SIDES:
        hline(o, n, 0, 0, o.f[n].width - 1, GOLD_D)
    px(o.f["front"], 3, 0, GOLD); px(o.f["front"], 4, 0, GOLD)
    o.place(img, 16, 32)

    # ---- arms: scales, gold stripe on the outside, dark webbed gloves
    for side, (ox, oy), (oox, ooy) in (("R", (40, 16), (40, 32)), ("L", (32, 48), (48, 48))):
        a, ao = Box(4, 12, 4), Box(4, 12, 4)
        for n in ("top", "bottom") + SIDES:
            deep_scales(a.f[n])
        outer, fx, bx = arm_edge(side)
        for y in range(0, 9):
            put(a, outer, 1, y, GOLD if y % 4 else GOLD_L)
        for n in SIDES:
            rows(a, n, 9, 11, BOOT)
            hline(a, n, 9, 0, 3, BOOT_L)
        a.fill(BOOT, ("bottom",))
        # fins on the forearms
        put(ao, outer, 1, 6, C("#3f9c9a")); put(ao, outer, 2, 6, C("#2f7f80")); put(ao, outer, 2, 7, C("#2f7f80"))
        a.place(img, ox, oy); ao.place(img, oox, ooy)

    for (ox, oy), (oox, ooy) in leg_slots():
        l, lo = Box(4, 12, 4), Box(4, 12, 4)
        for n in ("top", "bottom") + SIDES:
            deep_scales(l.f[n])
        for n in SIDES:
            rows(l, n, 8, 11, BOOT)
            hline(l, n, 8, 0, 3, GOLD_D)
            hline(l, n, 11, 0, 3, C("#071c1d"))
        l.fill(C("#071c1d"), ("bottom",))
        l.place(img, ox, oy)
    return img


# ================================================================ BLACK NOIR
def black_noir():
    K, K2, K3, HI = C("#19191c"), C("#2b2c32"), C("#3c3e46"), C("#565a64")
    HELM, HELM_D = C("#1d1d21"), C("#0e0e10")
    LENS, LENS_L, LENS_H = C("#3d434d"), C("#5c6470"), C("#9aa6b8")
    GLOVE, BOOT = C("#111113"), C("#0e0e10")
    img = new(64, 64)
    # ---- helmet: smooth, round, with a mouth plate and two dark lenses
    hb, ho = Box(8, 8, 8), Box(8, 8, 8)
    shade(hb, HELM, HELM_D, K3, seed=3, rate=13)
    f = hb.f["front"]
    for x in (1, 2):
        px(f, x, 3, LENS); px(f, x, 4, LENS_L)
    for x in (5, 6):
        px(f, x, 3, LENS); px(f, x, 4, LENS_L)
    px(f, 1, 3, LENS_H); px(f, 5, 3, LENS_H)
    # brow ridge and nose line
    hline(hb, "front", 2, 1, 6, K3)
    px(f, 3, 3, HELM_D); px(f, 4, 3, HELM_D); px(f, 3, 4, K2); px(f, 4, 4, K2)
    # mouth plate with vents
    for x in range(2, 6):
        px(f, x, 6, K2)
    px(f, 3, 6, HELM_D); px(f, 4, 6, HELM_D)
    hline(hb, "front", 7, 2, 5, K3)
    # seams on the sides and the back of the helmet
    for n in ("left", "right"):
        for y in range(2, 8):
            put(hb, n, 3 if n == "left" else 4, y, K2)
    hline(hb, "back", 5, 0, 7, K2)
    px(hb.f["top"], 3, 3, HI); px(hb.f["top"], 4, 4, K3)
    hb.place(img, 0, 0); ho.place(img, 32, 0)

    # ---- body: matte armour, chest plates, segmented abs, belt with pouches, straps for the sword
    b, o = Box(8, 12, 4), Box(8, 12, 4)
    shade(b, K, C("#121214"), K2, seed=4, rate=12)
    side_shadow(b, C("#0e0e10"))
    fr = b.f["front"]
    for (x0, x1) in ((1, 3), (4, 6)):
        for y in (1, 2, 3):
            for x in range(x0, x1 + 1):
                px(fr, x, y, K2)
        px(fr, x0, 1, K3)
    for y in (5, 6, 7):
        px(fr, 2, y, K2); px(fr, 5, y, K2)
        px(fr, 3, y, K3 if y % 2 else K2); px(fr, 4, y, K3 if y % 2 else K2)
    ring(b, 8, K2)
    for x in (1, 6):
        px(fr, x, 8, K3); px(fr, x, 9, K2)
    px(fr, 3, 8, HI); px(fr, 4, 8, K3)
    # diagonal sword strap
    for i in range(7):
        px(fr, 6 - i, 1 + i, C("#18191c"))
        px(b.f["back"], 1 + i, 1 + i, C("#18191c"))
    # back plate
    for y in range(1, 5):
        for x in (2, 3, 4, 5):
            if (x, y) != (2, 1) and (x, y) != (5, 1):
                px(b.f["back"], x, y, K2)
    b.fill(K2, ("top",))
    # overlay: shoulder armour edge
    for n in SIDES:
        hline(o, n, 0, 0, o.f[n].width - 1, K2)
    b.place(img, 16, 16); o.place(img, 16, 32)

    for side, (ox, oy), (oox, ooy) in arm_slots():
        a, ao = Box(4, 12, 4), Box(4, 12, 4)
        shade(a, K, C("#121214"), K2, seed=7 if side == "R" else 8, rate=12)
        outer, fx, bx = arm_edge(side)
        for n in SIDES:
            rows(a, n, 0, 2, K2)
            hline(a, n, 0, 0, 3, K3)
            rows(a, n, 8, 11, GLOVE)
            hline(a, n, 8, 0, 3, K3)
        # forearm guard
        for y in (5, 6, 7):
            put(a, outer, 1, y, K2); put(a, outer, 2, y, K2)
        put(a, outer, 1, 5, HI)
        a.fill(GLOVE, ("bottom",))
        # overlay: shoulder pad
        ao.fill(K2, ("top",))
        for n in SIDES:
            hline(ao, n, 0, 0, 3, K2)
            hline(ao, n, 1, 0, 3, K3 if n == outer else K2)
        a.place(img, ox, oy); ao.place(img, oox, ooy)

    for (ox, oy), (oox, ooy) in leg_slots():
        l, lo = Box(4, 12, 4), Box(4, 12, 4)
        shade(l, K, C("#121214"), K2, seed=9, rate=12)
        for n in SIDES:
            rows(l, n, 8, 11, BOOT)
            hline(l, n, 8, 0, 3, K2)
        # knee pads
        for x in (1, 2):
            put(l, "front", x, 5, K3); put(l, "front", x, 6, K2)
        put(l, "front", 1, 5, HI)
        # thigh holster on the right leg
        hline(l, "right", 2, 0, 3, K2)
        l.fill(BOOT, ("bottom",))
        l.place(img, ox, oy)
    return img


def katana():
    """16x16: blade (top left), grip (top right), guard (bottom left), scabbard (bottom right)."""
    img = new(16, 16)
    for y in range(8):
        for x in range(8):
            # blade: polished steel with the hamon line
            c = C("#e9edf2") if x < 3 else C("#c3cad3") if x < 6 else C("#9aa3ad")
            if x == 3 and y % 3 != 2:
                c = C("#ffffff")
            px(img, x, y, c)
            # grip: black with grey diamond wrapping
            g = C("#141416")
            if (x + y) % 4 == 0 or (x - y) % 4 == 0:
                g = C("#5a5d66")
            px(img, 8 + x, y, g)
            # guard: dark iron with a gold rim
            gu = C("#2a2b30") if 1 <= x <= 6 and 1 <= y <= 6 else C("#c9a24a")
            px(img, x, 8 + y, gu)
            # scabbard: glossy black lacquer
            s = C("#0d0d0f") if (x + y * 3) % 7 else C("#2c2e35")
            px(img, 8 + x, 8 + y, s)
    return img


# ================================================================ HUD icons
def icon_starlight():
    im = new(16, 16)
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d < 7.5:
                a = int(120 * (1 - d / 7.5))
                px(im, x, y, (255, 233, 168, a))
    import math
    pts = []
    for k in range(10):
        r = 7.0 if k % 2 == 0 else 3.0
        ang = -math.pi / 2 + k * math.pi / 5
        pts.append((7.5 + r * math.cos(ang), 8 + r * math.sin(ang)))

    def inside(x, y):
        n, c = len(pts), False
        for i in range(n):
            x1, y1 = pts[i]
            x2, y2 = pts[(i + 1) % n]
            if (y1 > y) != (y2 > y) and x < (x2 - x1) * (y - y1) / (y2 - y1) + x1:
                c = not c
        return c
    for y in range(16):
        for x in range(16):
            if inside(x + 0.5, y + 0.5):
                px(im, x, y, C("#f2f5f9") if x < 8 and y < 9 else C("#d8dce2"))
    px(im, 7, 4, C("#ffffff")); px(im, 6, 7, C("#ffffff"))
    return im


def icon_stormfront():
    im = new(16, 16)
    bolt = ["........######..",
            ".......######...",
            "......######....",
            ".....######.....",
            "....######......",
            "...##########...",
            "......######....",
            ".....######.....",
            "....######......",
            "...#####........",
            "..####..........",
            ".###............"]
    for j, row in enumerate(bolt):
        for i, ch in enumerate(row):
            if ch == "#":
                px(im, i, 2 + j, C("#cfe6ff") if i + j < 13 else C("#6fa8ff"))
    return outline(im, C("#1a1d2b"))


def icon_the_deep():
    im = new(16, 16)
    # a fish leaping out of a wave
    for y in range(16):
        for x in range(16):
            if ((x - 7) / 5.5) ** 2 + ((y - 6.5) / 3.0) ** 2 <= 1:
                px(im, x, y, C("#3f9c9a") if y < 6 else C("#2f7f80"))
    for (x, y) in ((12, 4), (13, 3), (14, 2), (13, 6), (14, 7), (14, 8), (12, 7), (13, 5), (12, 5), (12, 6)):
        px(im, x, y, C("#2f7f80"))
    px(im, 3, 5, C("#ffffff")); px(im, 3, 6, C("#101012"))
    for x in range(16):
        y = 12 + int(1.5 * __import__("math").sin(x * 0.8))
        for yy in range(y, 16):
            px(im, x, yy, C("#1e6fb8") if yy > y else C("#bfefff"))
    return outline(im, C("#0f3436"))


def icon_black_noir():
    im = new(16, 16)
    for y in range(16):
        for x in range(16):
            if ((x - 7.5) / 6.5) ** 2 + ((y - 7.5) / 7.0) ** 2 <= 1:
                px(im, x, y, C("#141416") if (x + y) % 5 else C("#1e1f23"))
    for x in range(3, 7):
        px(im, x, 7, C("#4a4f58")); px(im, x, 8, C("#2c2f36"))
    for x in range(9, 13):
        px(im, x, 7, C("#4a4f58")); px(im, x, 8, C("#2c2f36"))
    px(im, 4, 7, C("#7a8494")); px(im, 10, 7, C("#7a8494"))
    for x in range(5, 11):
        px(im, x, 12, C("#2a2b30"))
    px(im, 6, 2, C("#3a3c44")); px(im, 7, 2, C("#3a3c44"))
    return outline(im, C("#55575e"))


def run():
    save(starlight(), "entity/suit/starlight.png")
    save(starlight_cape(), "entity/starlight_cape.png")
    save(stormfront(), "entity/suit/stormfront.png")
    save(stormfront_cape(), "entity/stormfront_cape.png")
    save(the_deep(), "entity/suit/the_deep.png")
    save(black_noir(), "entity/suit/black_noir.png")
    save(katana(), "entity/noir_katana.png")
    save(icon_starlight(), "gui/power/starlight.png")
    save(icon_stormfront(), "gui/power/stormfront.png")
    save(icon_the_deep(), "gui/power/the_deep.png")
    save(icon_black_noir(), "gui/power/black_noir.png")


if __name__ == "__main__":
    run()
