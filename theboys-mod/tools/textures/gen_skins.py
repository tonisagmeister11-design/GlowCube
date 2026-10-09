"""Player skins (64x64 classic/wide layout) + Homelander cape."""
from common import *

FACES = ("top", "bottom", "right", "front", "left", "back")


class Box:
    def __init__(s, w, h, d):
        s.w, s.h, s.d = w, h, d
        s.f = {"top": new(w, d), "bottom": new(w, d), "right": new(d, h),
               "front": new(w, h), "left": new(d, h), "back": new(w, h)}

    def fill(s, c, faces=FACES):
        for n in faces:
            rect(s.f[n], 0, 0, s.f[n].width, s.f[n].height, c)

    def hex(s, base, dark, light, faces=FACES, y0=0, y1=None):
        for n in faces:
            im = s.f[n]
            hexfill(im, base, dark, light, 0, y0, None, (im.height if y1 is None else y1) - y0)

    def noise(s, base, dark, light, faces=FACES, y0=0, y1=None, seed=1):
        for n in faces:
            im = s.f[n]
            for y in range(y0, im.height if y1 is None else y1):
                for x in range(im.width):
                    v = h2(x, y, seed + len(n)) % 9
                    px(im, x, y, light if v == 0 else dark if v == 1 else base)

    def place(s, img, ox, oy):
        w, h, d = s.w, s.h, s.d
        pos = {"top": (d, 0), "bottom": (d + w, 0), "right": (0, d), "front": (d, d),
               "left": (d + w, d), "back": (2 * d + w, d)}
        for n, (x, y) in pos.items():
            img.paste(s.f[n], (ox + x, oy + y))


# ---------------------------------------------------------------- head
def mk_head(P):
    b, o = Box(8, 8, 8), Box(8, 8, 8)
    skin, skin_d = C(P["skin"]), C(P["skin_d"])
    hair, hair_d, hair_l = C(P["hair"]), C(P["hair_d"]), C(P["hair_l"])
    b.fill(skin)
    b.fill(skin_d, ("bottom",))

    def hc(x, y, s=0):
        v = h2(x, y, s) % 7
        return hair_l if v == 0 else hair_d if v <= 2 else hair

    for y in range(8):
        for x in range(8):
            px(b.f["top"], x, y, hc(x, y, 1))
            if y < P["back_rows"]:
                px(b.f["back"], x, y, hc(x, y, 2))
    for x in range(8):
        for y in range(P["front_h"][x]):
            px(b.f["front"], x, y, hc(x, y, 3))
        # sides: x runs front->back for left face, back->front for right face
        for y in range(P["side_h"][x]):
            px(b.f["left"], x, y, hc(x, y, 4))
            px(b.f["right"], 7 - x, y, hc(7 - x, y, 5))
    # face
    f = b.f["front"]
    eye_w, eye_i = C("#f4f4f4"), C(P["eye"])
    brow = C(P["brow"])
    for xs, ws in (((1, 2), "L"), ((5, 6), "R")):
        pass
    px(f, 1, 4, eye_w); px(f, 2, 4, eye_i); px(f, 5, 4, eye_i); px(f, 6, 4, eye_w)
    for x in (1, 2, 5, 6):
        if f.getpixel((x, 3))[:3] == skin[:3]:
            px(f, x, 3, brow)
    if P.get("brow_low"):
        px(f, 2, 3, brow); px(f, 5, 3, brow)
    px(f, 3, 5, skin_d); px(f, 4, 5, skin)
    mouth = C(P.get("mouth", "#b3605f"))
    if P.get("smirk"):
        px(f, 3, 6, C("#d89b86")); px(f, 4, 6, mouth); px(f, 5, 6, mouth); px(f, 6, 5, skin_d)
        px(f, 1, 3, skin_d)
    else:
        px(f, 3, 6, mouth); px(f, 4, 6, mouth)
    # beard
    if P.get("beard"):
        bc, bd = C(P["beard"]), C(P["beard_d"])
        full = P.get("beard_full", True)

        def bpx(im, x, y, c):
            if full or h2(x, y, 9) % 3 != 0:
                px(im, x, y, c)
        for x in range(8):
            for y in range(5, 8):
                if y == 5 and x in (3, 4):
                    continue
                if y == 5 and x in (2, 5):
                    bpx(f, x, y, bd); continue
                bpx(f, x, y, bc if h2(x, y, 3) % 4 else bd)
        px(f, 3, 6, mouth); px(f, 4, 6, C(P.get("mouth_in", "#5a2a2a")))
        px(f, 4, 6, mouth)
        for k in range(4):
            start = (3, 4, 5, 6)[k]
            for y in range(start, 8):
                bpx(b.f["left"], k, y, bc)
                bpx(b.f["right"], 7 - k, y, bc)
        for x in range(8):
            px(b.f["bottom"], x, 7, bc)
            px(b.f["bottom"], x, 6, bc)
    # ears: slightly darker pixel
    px(b.f["left"], 4, 4, skin_d); px(b.f["right"], 3, 4, skin_d)
    # overlay hair
    ov = P["ov"]
    for x in range(8):
        for y in range(8):
            px(o.f["top"], x, y, hc(x, y, 11))
    for x in range(8):
        for y in range(ov["back"]):
            px(o.f["back"], x, y, hc(x, y, 12))
        for y in range(ov["side"][x]):
            px(o.f["left"], x, y, hc(x, y, 13))
            px(o.f["right"], 7 - x, y, hc(7 - x, y, 14))
        for y in range(ov["front"][x]):
            px(o.f["front"], x, y, hc(x, y, 15))
    return b, o


# ---------------------------------------------------------------- generic
def arm_edge(side):
    """returns (outer_face_name, front_x(x), back_x(x))"""
    if side == "R":
        return "right", (lambda x: x), (lambda x: 3 - x)
    return "left", (lambda x: 3 - x), (lambda x: x)


def put(box, face, x, y, c):
    px(box.f[face], x, y, c)


def hline(box, face, y, x0, x1, c):
    for x in range(x0, x1 + 1):
        px(box.f[face], x, y, c)


def rows(box, face, y0, y1, c):
    for y in range(y0, y1 + 1):
        hline(box, face, y, 0, box.f[face].width - 1, c)


# ================================================================ HOMELANDER
def homelander():
    NAVY, NAVYD, NAVYL = C("#1f2f66"), C("#17234f"), C("#2e4491")
    RED, REDD = C("#b01f2a"), C("#741420")
    GOLD, GOLDD, GOLDL = C("#e4b53c"), C("#a97b1f"), C("#fbe38a")
    img = new(64, 64)
    hb, ho = mk_head(dict(skin="#ebc0a0", skin_d="#cf9f82", hair="#d9b35c", hair_d="#bd9440",
                          hair_l="#f0d686", eye="#4f86cc", brow="#a98238", smirk=True,
                          back_rows=6, front_h=[2, 1, 1, 1, 1, 1, 1, 2], side_h=[0, 1, 2, 2, 3, 4, 5, 5],
                          ov=dict(back=5, side=[0, 0, 1, 2, 2, 3, 4, 4], front=[0, 0, 1, 1, 1, 1, 0, 0])))
    hb.place(img, 0, 0); ho.place(img, 32, 0)
    # body
    b, o = Box(8, 12, 4), Box(8, 12, 4)
    b.hex(NAVY, NAVYD, NAVYL)
    f = b.f["front"]
    hline(b, "front", 0, 0, 7, RED); hline(b, "front", 1, 0, 1, RED); hline(b, "front", 1, 6, 7, RED)
    for (x, y) in ((2, 2), (1, 2), (2, 3), (3, 4), (5, 3), (5, 2), (6, 2)):
        pass
    for (x, y) in ((1, 2), (2, 3), (6, 2), (5, 3)):
        px(f, x, y, REDD)
    for y in range(2, 6):
        px(f, 3 if y % 2 else 4, y, NAVYD)
    hline(b, "front", 5, 1, 2, NAVYD); hline(b, "front", 5, 5, 6, NAVYD)
    b.fill(RED, ("top",))
    for n in ("front", "back", "left", "right"):
        rows(b, n, 9, 9, GOLDL); rows(b, n, 10, 10, GOLD)
    hline(b, "back", 11, 0, 7, NAVYD)
    for y in range(2, 9):
        px(b.f["back"], 3, y, NAVYD)
    # buckle
    for x in (3, 4):
        for y in (8, 9, 10):
            px(f, x, y, GOLDL if y == 8 else GOLD)
    px(f, 3, 9, GOLDD); px(f, 4, 9, GOLDD)
    px(f, 2, 9, GOLDD); px(f, 5, 9, GOLDD)
    # collar overlay
    for n in ("front", "back", "left", "right"):
        rows(o, n, 0, 0, RED)
        rows(o, n, 1, 1, REDD if n == "back" else RED)
    px(o.f["front"], 3, 1, NAVYD); px(o.f["front"], 4, 1, NAVYD)
    px(o.f["front"], 3, 0, REDD); px(o.f["front"], 4, 0, REDD)
    b.place(img, 16, 16); o.place(img, 16, 32)
    # arms
    for side, (ox, oy), (oox, ooy) in (("R", (40, 16), (40, 32)), ("L", (32, 48), (48, 48))):
        a, ao = Box(4, 12, 4), Box(4, 12, 4)
        a.hex(NAVY, NAVYD, NAVYL)
        a.fill(RED, ("top",))
        outer, fx, bx = arm_edge(side)
        for n in ("front", "back", "left", "right", "bottom"):
            rows(a, n, 9, 9, REDD)
            rows(a, n, 10, 11, C("#5b2a2c"))
        for n in ("front", "back", "left", "right"):
            hline(a, n, 10, 0, 3, C("#6c3436"))
        ao.fill(GOLD, ("top",))
        for y, col in ((0, GOLDL), (1, GOLD), (2, GOLDD), (3, GOLD)):
            hline(ao, outer, y, 0, 3, col)
        for n, ff in (("front", fx), ("back", bx)):
            for y, col in ((0, GOLDL), (1, GOLD), (2, GOLDD)):
                for x in (0, 1, 2) if y < 2 else (0, 1):
                    put(ao, n, ff(x) if True else x, y, col)
        hline(ao, "top", 0, 0, 3, GOLDL); hline(ao, "top", 3, 0, 3, GOLDD)
        a.place(img, ox, oy); ao.place(img, oox, ooy)
    # legs
    for (ox, oy), (oox, ooy) in (((0, 16), (0, 32)), ((16, 48), (0, 48))):
        l, lo = Box(4, 12, 4), Box(4, 12, 4)
        l.hex(NAVY, NAVYD, NAVYL)
        for n in ("front", "back", "left", "right"):
            hline(l, n, 8, 0, 3, REDD)
            rows(l, n, 9, 11, C("#3b2323"))
            hline(l, n, 11, 0, 3, C("#1b1111"))
            hline(l, n, 9, 0, 3, C("#503030"))
        l.fill(C("#1b1111"), ("bottom",))
        l.place(img, ox, oy)
    return img


def homelander_cape():
    img = new(64, 32)
    R, RD, RL, RDD = C("#b3202b"), C("#8c1722"), C("#cb3039"), C("#5f0f19")
    GOLD, GOLDD = C("#e4b53c"), C("#a97b1f")
    for y in range(16):
        for x in range(10):
            base = R
            if x in (2, 5, 8):
                base = RD
            if x in (3, 6):
                base = RL
            if y > 10 and h2(x, y) % 3 == 0:
                base = RD
            if y > 13:
                base = RD if x % 2 else RDD
            px(img, 1 + x, 1 + y, base)
            px(img, 12 + x, 1 + y, RDD if x % 3 == 0 else C("#7a1220"))
    for x in range(10):
        px(img, 1 + x, 0, GOLDD); px(img, 11 + x, 0, RDD)
    for y in range(16):
        px(img, 0, 1 + y, RD); px(img, 11, 1 + y, RD)
    for x in (1, 2, 9, 10):
        px(img, x, 1, GOLD); px(img, x, 2, GOLDD)
    px(img, 4, 1, GOLD); px(img, 7, 1, GOLD)
    return img


# ================================================================ SOLDIER BOY
def soldier_boy():
    G, GD, GL = C("#33502f"), C("#233a21"), C("#436a3c")
    K, KL = C("#17181b"), C("#33363d")
    GOLD, GOLDD, GOLDL = C("#e4b53c"), C("#a97b1f"), C("#fbe38a")
    img = new(64, 64)
    hb, ho = mk_head(dict(skin="#d9a581", skin_d="#bd8a68", hair="#5e3d25", hair_d="#44291a",
                          hair_l="#82583a", eye="#6f8696", brow="#3a2315", brow_low=True,
                          mouth="#8f4a45",
                          back_rows=6, front_h=[2, 3, 2, 1, 1, 2, 3, 2], side_h=[1, 2, 3, 4, 5, 5, 6, 6],
                          ov=dict(back=7, side=[0, 1, 2, 3, 3, 4, 5, 5], front=[1, 2, 1, 0, 0, 1, 2, 1])))
    hb.place(img, 0, 0); ho.place(img, 32, 0)
    b, o = Box(8, 12, 4), Box(8, 12, 4)
    b.hex(G, GD, GL)
    f = b.f["front"]
    rect(f, 0, 2, 4, 7, K)
    for y in range(2, 9):
        px(f, 3, y, KL)
    px(f, 1, 2, GOLD); px(f, 2, 3, GOLDD); px(f, 0, 3, GOLDD)  # eagle
    px(f, 1, 5, GOLDL); px(f, 1, 4, GOLD); px(f, 0, 5, GOLD); px(f, 2, 5, GOLD); px(f, 1, 6, GOLD)  # star
    for i in range(6):  # diagonal strap
        px(f, 7 - i, 1 + i, K); px(f, 6 - i, 1 + i, K); px(f, 5 - i + 0, 1 + i, KL if i == 3 else K)
    px(f, 6, 2, GOLD)
    hline(b, "front", 9, 0, 7, K); hline(b, "front", 10, 0, 7, K)
    for x in (3, 4):
        px(f, x, 9, GOLD); px(f, x, 10, GOLDD)
    for n in ("left", "right"):
        hline(b, n, 9, 0, 3, K); hline(b, n, 10, 0, 3, K)
    bk = b.f["back"]
    hline(b, "back", 9, 0, 7, K); hline(b, "back", 10, 0, 7, K)
    for i in range(7):
        px(bk, 1 + i, 1 + i, K); px(bk, 6 - i, 1 + i, K)
    b.fill(GD, ("top",)); b.fill(K, ("bottom",))
    for n in ("front", "back", "left", "right"):
        hline(o, n, 0, 0, 7 if n in ("front", "back") else 3, GL)
        hline(o, n, 1, 0, 7 if n in ("front", "back") else 3, G)
    hline(o, "front", 2, 0, 1, GD); hline(o, "front", 2, 6, 7, GD)
    for x in range(0, 8, 2):
        px(o.f["front"], x, 0, GD); px(o.f["back"], x, 0, GD)
    px(o.f["front"], 3, 1, GD); px(o.f["front"], 4, 1, GD)
    px(o.f["front"], 2, 1, GOLD)  # little collar pin
    b.place(img, 16, 16); o.place(img, 16, 32)
    for side, (ox, oy), (oox, ooy) in (("R", (40, 16), (40, 32)), ("L", (32, 48), (48, 48))):
        a, ao = Box(4, 12, 4), Box(4, 12, 4)
        a.hex(G, GD, GL)
        a.fill(GD, ("top",))
        outer, fx, bx = arm_edge(side)
        for n in ("front", "back", "left", "right", "bottom"):
            rows(a, n, 6, 6, K); rows(a, n, 7, 8, K)
            rows(a, n, 9, 11, K)
            rows(a, n, 9, 9, KL)
        if side == "R":
            for n in ("front", "back", "left", "right", "top"):
                rows(ao, n, 0, 1, K) if n != "top" else rows(ao, n, 0, 3, K)
            put(ao, "front", 1, 1, GOLD); put(ao, "right", 1, 0, GOLD)
        else:
            ao.fill(G, ("top",))
            for y, xs, col in ((2, (0, 1, 2, 3), GOLD), (3, (0, 1, 2, 3), GOLDD), (4, (1, 2), GOLD), (5, (1, 2), GOLDD)):
                for x in xs:
                    put(ao, "left", x, y, col)
            hline(ao, "left", 2, 0, 3, GOLDL)
        a.place(img, ox, oy); ao.place(img, oox, ooy)
    for (ox, oy), (oox, ooy) in (((0, 16), (0, 32)), ((16, 48), (0, 48))):
        l, lo = Box(4, 12, 4), Box(4, 12, 4)
        l.hex(G, GD, GL)
        for n in ("front", "back", "left", "right"):
            rows(l, n, 5, 6, K) if n == "front" else None
            hline(l, n, 2, 0, 3, K)
            rows(l, n, 9, 11, K); hline(l, n, 9, 0, 3, KL)
        l.fill(K, ("bottom",))
        l.place(img, ox, oy)
    return img


# ================================================================ A-TRAIN
def a_train():
    B, BD, BL = C("#2f66c4"), C("#234e9c"), C("#4a86e0")
    WH, LB, NAVY, K, KL = C("#f2f6fb"), C("#a9d5ff"), C("#16254f"), C("#16171a"), C("#34373d")
    img = new(64, 64)
    hb, ho = mk_head(dict(skin="#80502f", skin_d="#633a21", hair="#1d1512", hair_d="#120d0b",
                          hair_l="#2e231e", eye="#201510", brow="#120d0b", mouth="#5a2a24",
                          back_rows=5, front_h=[1, 1, 1, 1, 1, 1, 1, 1], side_h=[0, 1, 1, 2, 3, 4, 4, 4],
                          ov=dict(back=0, side=[0] * 8, front=[0] * 8)))
    # goggles on overlay
    LENS1, LENS2, LHI = C("#3a8bff"), C("#1f5fd0"), C("#b8e0ff")
    for x in range(8):
        px(ho.f["front"], x, 3, K)
    for y in (4, 5):
        px(ho.f["front"], 0, y, K); px(ho.f["front"], 7, y, K)
        for x in range(1, 7):
            px(ho.f["front"], x, y, LENS1 if y == 4 else LENS2)
    px(ho.f["front"], 2, 4, LHI); px(ho.f["front"], 3, 4, LHI); px(ho.f["front"], 5, 5, LENS1)
    px(ho.f["front"], 3, 5, K); px(ho.f["front"], 4, 5, K) if False else None
    for x in range(8):
        px(ho.f["left"], x, 4, K); px(ho.f["right"], x, 4, K); px(ho.f["back"], x, 4, K)
    px(ho.f["left"], 0, 3, K); px(ho.f["left"], 0, 5, K); px(ho.f["right"], 7, 3, K); px(ho.f["right"], 7, 5, K)
    px(ho.f["left"], 0, 4, LENS1); px(ho.f["right"], 7, 4, LENS1)
    hb.place(img, 0, 0); ho.place(img, 32, 0)
    b, o = Box(8, 12, 4), Box(8, 12, 4)
    b.hex(B, BD, BL)
    f = b.f["front"]
    for y in range(1, 7):
        for x in range(1, 7):
            px(f, x, y, LB if (y > 1 and h2(x, y, 2) % 5) else WH)
    for y in range(1, 7):
        px(f, 3, y, BD); px(f, 4, y, WH)
    hline(b, "front", 6, 1, 6, BD)
    hline(b, "front", 0, 0, 7, WH)
    for x in (0, 7):
        for y in range(1, 9):
            px(f, x, y, WH)
    for n in ("front", "back", "left", "right"):
        hline(b, n, 9, 0, 7 if n in ("front", "back") else 3, NAVY)
        hline(b, n, 10, 0, 7 if n in ("front", "back") else 3, NAVY)
    ab = ["WWWW"[:0] + "NWWN", "WWWW", "WNNW"]
    for j, r in enumerate(ab):
        for i, ch in enumerate(r):
            px(f, 2 + i, 8 + j, WH if ch == "W" else NAVY)
    bk = b.f["back"]
    for y in range(1, 6):
        for x in range(2, 6):
            px(bk, x, y, LB if (x + y) % 2 else WH)
    for y in range(1, 9):
        px(bk, 0, y, WH); px(bk, 7, y, WH)
    hline(b, "back", 0, 0, 7, WH)
    b.fill(WH, ("top",)); b.fill(NAVY, ("bottom",))
    for n in ("front", "back", "left", "right"):
        hline(o, n, 0, 0, 7 if n in ("front", "back") else 3, WH)
    px(o.f["front"], 3, 0, LB); px(o.f["front"], 4, 0, LB)
    b.place(img, 16, 16); o.place(img, 16, 32)
    skin, skin_d = C("#80502f"), C("#633a21")
    for side, (ox, oy), (oox, ooy) in (("R", (40, 16), (40, 32)), ("L", (32, 48), (48, 48))):
        a, ao = Box(4, 12, 4), Box(4, 12, 4)
        a.hex(B, BD, BL)
        outer, fx, bx = arm_edge(side)
        a.fill(WH, ("top",))
        for y in range(0, 6):
            put(a, outer, 1, y, WH)
            put(a, "front", fx(0), y, WH); put(a, "back", bx(0), y, WH)
        for n in ("front", "back", "left", "right"):
            rows(a, n, 6, 9, K); rows(a, n, 6, 6, KL); rows(a, n, 9, 9, KL)
            hline(a, n, 7, 1, 1, KL)
            rows(a, n, 10, 11, skin)
            hline(a, n, 11, 0, 3, skin_d)
        a.fill(skin_d, ("bottom",))
        hline(ao, outer, 0, 0, 3, WH); hline(ao, "front", 0, 0, 3, WH); hline(ao, "back", 0, 0, 3, WH)
        a.place(img, ox, oy); ao.place(img, oox, ooy)
    for (ox, oy), (oox, ooy) in (((0, 16), (0, 32)), ((16, 48), (0, 48))):
        l = Box(4, 12, 4)
        l.hex(B, BD, BL)
        for n in ("front", "back", "left", "right"):
            rows(l, n, 5, 9, NAVY); hline(l, n, 5, 0, 3, BD)
            rows(l, n, 10, 11, WH)
            hline(l, n, 11, 0, 3, C("#b5bfce"))
            hline(l, n, 10, 0, 3, K)
        for j, r in enumerate((".W.", "WWW", "W.W")):
            for i, ch in enumerate(r):
                if ch == "W":
                    put(l, "front", 1 + i, 6 + j, WH)
        l.fill(K, ("bottom",))
        l.place(img, ox, oy)
    return img


# ================================================================ BUTCHER
def butcher():
    COAT, COATD, COATL = C("#15161b"), C("#0d0e11"), C("#1f2128")
    LAP = C("#34363f")
    SHIRT, SHIRTD = C("#25272f"), C("#1a1b21")
    TR = C("#101113")
    skin, skin_d = C("#e4bda3"), C("#c99f86")
    img = new(64, 64)
    hb, ho = mk_head(dict(skin="#e4bda3", skin_d="#c99f86", hair="#171413", hair_d="#0d0b0a",
                          hair_l="#2c2523", eye="#5b6e7c", brow="#0d0b0a", brow_low=True,
                          mouth="#6a3836",
                          back_rows=6, front_h=[2, 1, 1, 1, 1, 1, 1, 2], side_h=[1, 2, 3, 3, 4, 5, 6, 6],
                          ov=dict(back=7, side=[0, 0, 1, 3, 3, 4, 5, 5], front=[0, 0, 0, 1, 1, 0, 0, 0])))
    hb.place(img, 0, 0); ho.place(img, 32, 0)
    b, o = Box(8, 12, 4), Box(8, 12, 4)
    b.fill(SHIRT)
    b.noise(COAT, COATD, COATL, ("left", "right", "back", "top", "bottom"), seed=3)
    f = b.f["front"]
    for y in range(12):
        for x in range(8):
            px(f, x, y, (SHIRT if h2(x, y) % 5 else SHIRTD) if 2 <= x <= 5 else (COAT if h2(x, y) % 4 else COATL))
    for y in range(2, 12):
        px(f, 3, y, SHIRTD) if y % 3 == 0 else None
    # tie-less open shirt: skin sliver at throat
    px(f, 3, 0, skin_d); px(f, 4, 0, skin_d); px(f, 3, 1, SHIRTD); px(f, 4, 1, SHIRTD)
    hline(b, "front", 11, 0, 7, COATD)
    b.fill(C("#1a1a1f"), ("top",))
    # coat overlay
    o.noise(COAT, COATD, COATL, ("left", "right", "back"), seed=5)
    of = o.f["front"]
    for y in range(12):
        for x in (0, 1, 2, 5, 6, 7):
            px(of, x, y, COAT if h2(x, y, 7) % 4 else COATL)
    for y in range(1, 8):
        px(of, 2, y, LAP); px(of, 5, y, LAP)
    for y in range(8, 12):
        px(of, 2, y, COATD); px(of, 5, y, COATD)
    for n in ("front", "back", "left", "right"):
        hline(o, n, 0, 0, 7 if n in ("front", "back") else 3, LAP)
    px(of, 3, 0, SHIRTD); px(of, 4, 0, SHIRTD)
    for x in (0, 1):
        px(of, x, 1, COATL)
    hline(o, "back", 6, 3, 4, COATD)
    b.place(img, 16, 16); o.place(img, 16, 32)
    for side, (ox, oy), (oox, ooy) in (("R", (40, 16), (40, 32)), ("L", (32, 48), (48, 48))):
        a, ao = Box(4, 12, 4), Box(4, 12, 4)
        a.noise(COAT, COATD, COATL, seed=8)
        for n in ("front", "back", "left", "right"):
            rows(a, n, 10, 11, skin); hline(a, n, 11, 0, 3, skin_d)
            hline(a, n, 9, 0, 3, COATD)
        a.fill(skin_d, ("bottom",))
        outer, fx, bx = arm_edge(side)
        # fleshy tentacle scar patch on the right forearm
        if side == "R":
            for (x, y, c) in ((1, 6, "#5b3a33"), (2, 6, "#6a4439"), (1, 7, "#4a2c28"), (2, 7, "#5b3a33"), (2, 8, "#6a4439")):
                put(a, "front", x, y, C(c))
        for n in ("front", "back", "left", "right"):
            w = 3 if n in ("left", "right") else 3
            hline(ao, n, 8, 0, 3, LAP)
        a.place(img, ox, oy); ao.place(img, oox, ooy)
    for (ox, oy), (oox, ooy) in (((0, 16), (0, 32)), ((16, 48), (0, 48))):
        l, lo = Box(4, 12, 4), Box(4, 12, 4)
        l.noise(TR, C("#0b0b0d"), C("#18191c"), seed=2)
        for n in ("front", "back", "left", "right"):
            rows(l, n, 9, 11, C("#0a0a0b")); hline(l, n, 9, 0, 3, C("#1e1e22")); hline(l, n, 11, 0, 3, C("#26211e"))
        l.fill(C("#0a0a0b"), ("bottom",))
        lo.noise(COAT, COATD, COATL, ("front", "back", "left", "right"), y0=0, y1=7, seed=6)
        hline(lo, "front", 6, 0, 3, COATD); hline(lo, "back", 6, 0, 3, COATD)
        hline(lo, "left", 6, 0, 3, COATD); hline(lo, "right", 6, 0, 3, COATD)
        lo.fill(COAT, ("top",))
        l.place(img, ox, oy); lo.place(img, oox, ooy)
    return img


def run():
    for name, fn in (("soldier_boy", soldier_boy), ("a_train", a_train),
                     ("butcher", butcher), ("homelander", homelander)):
        save(fn(), f"entity/suit/{name}.png")
    save(homelander_cape(), "entity/homelander_cape.png")


if __name__ == "__main__":
    run()
