"""Supe hunters: four ordinary-looking people (Steve-like) in different clothes."""
from common import *
from gen_skins import Box, mk_head, arm_edge, put, hline, rows

SIDES = ("front", "back", "left", "right")


def cloth(box, base, dark, light, seed, faces=("top", "bottom") + SIDES, rate=8):
    for n in faces:
        im = box.f[n]
        for y in range(im.height):
            for x in range(im.width):
                v = h2(x, y, seed * 11 + len(n)) % rate
                px(im, x, y, light if v == 0 else dark if v == 1 else base)


def camo(box, seed, faces=("top", "bottom") + SIDES):
    cols = [C("#556b2f"), C("#3b4727"), C("#7a6a3a"), C("#2b3320")]
    for n in faces:
        im = box.f[n]
        for y in range(im.height):
            for x in range(im.width):
                # blotchy camo: low-frequency noise from 2x2 cells
                v = h2(x // 2 + (y // 3), y // 2, seed * 5 + len(n)) % 7
                c = cols[0] if v < 3 else cols[1] if v < 5 else cols[2] if v == 5 else cols[3]
                if h2(x, y, seed) % 9 == 0:
                    c = cols[3]
                px(im, x, y, c)


def person(v):
    img = new(64, 64)
    hb, ho = mk_head(dict(skin=v["skin"], skin_d=v["skin_d"], hair=v["hair"], hair_d=v["hair_d"], hair_l=v["hair_l"],
                          eye=v["eye"], brow=v["brow"], mouth=v.get("mouth", "#a2574f"),
                          back_rows=v.get("back_rows", 5), front_h=v.get("front_h", [2, 1, 1, 1, 1, 1, 1, 2]),
                          side_h=v.get("side_h", [0, 1, 2, 2, 3, 3, 4, 4]),
                          ov=dict(back=v.get("ov_back", 4), side=v.get("ov_side", [0, 0, 1, 2, 2, 3, 3, 3]), front=[0, 0, 1, 1, 1, 1, 0, 0])))
    f = hb.f["front"]
    # a plain, friendly human face: a real nose bridge, a tiny scar for some
    px(f, 3, 5, C(v["skin_d"])); px(f, 4, 5, C(v["skin"]))
    if v.get("scar"):
        px(f, 6, 4, C("#b9807a")); px(f, 6, 5, C("#b9807a"))
    hb.place(img, 0, 0); ho.place(img, 32, 0)

    # ---- torso
    b, o = Box(8, 12, 4), Box(8, 12, 4)
    if v["torso"] == "camo":
        camo(b, 3)
    else:
        cloth(b, C(v["torso"][0]), C(v["torso"][1]), C(v["torso"][2]), 3)
    fr = b.f["front"]
    # undershirt in the middle, collar
    for y in range(0, 8):
        for x in (3, 4):
            px(fr, x, y, C(v["under"]))
    px(fr, 3, 0, C(v["skin_d"])); px(fr, 4, 0, C(v["skin_d"]))
    for n in SIDES:
        hline(b, n, 9, 0, b.f[n].width - 1, C(v["belt"]))
        hline(b, n, 10, 0, b.f[n].width - 1, C(v["pants"][1]))
    px(fr, 3, 9, C("#c9a24a")); px(fr, 4, 9, C("#c9a24a"))
    b.fill(C(v["under"]), ("top",))
    for y in range(1, 8):
        px(b.f["back"], 3, y, C(v["torso"][1]) if v["torso"] != "camo" else C("#2b3320"))
    b.place(img, 16, 16); o.place(img, 16, 32)

    # ---- arms: sleeves and bare hands
    for side, (ox, oy), (oox, ooy) in (("R", (40, 16), (40, 32)), ("L", (32, 48), (48, 48))):
        a, ao = Box(4, 12, 4), Box(4, 12, 4)
        if v["torso"] == "camo":
            camo(a, 5 if side == "R" else 6)
        else:
            cloth(a, C(v["sleeve"][0]), C(v["sleeve"][1]), C(v["sleeve"][2]), 5 if side == "R" else 6)
        for n in SIDES:
            rows(a, n, 9, 11, C(v["skin"]) if not v.get("gloves") else C(v["gloves"]))
            hline(a, n, 9, 0, 3, C(v["skin_d"]) if not v.get("gloves") else C("#0e0e10"))
        a.fill(C(v["skin_d"]), ("bottom",))
        a.place(img, ox, oy); ao.place(img, oox, ooy)

    # ---- legs
    for (ox, oy), (oox, ooy) in (((0, 16), (0, 32)), ((16, 48), (0, 48))):
        l, lo = Box(4, 12, 4), Box(4, 12, 4)
        if v.get("camo_pants"):
            camo(l, 9)
        else:
            cloth(l, C(v["pants"][0]), C(v["pants"][1]), C(v["pants"][2]), 9)
        for n in SIDES:
            rows(l, n, 9, 11, C(v["boots"]))
            hline(l, n, 9, 0, 3, C(v["boots_l"]))
        l.fill(C("#0b0b0c"), ("bottom",))
        # cargo pocket
        put(l, "left", 1, 5, C(v["pants"][1])); put(l, "left", 2, 5, C(v["pants"][1])); put(l, "left", 2, 6, C(v["pants"][1]))
        l.place(img, ox, oy)
    return img


VARIANTS = [
    # 0: camo hunter, brown hair
    dict(skin="#c58c63", skin_d="#a8714c", hair="#4a3222", hair_d="#33221a", hair_l="#6b4a34", eye="#4a6b8a", brow="#33221a",
         torso="camo", sleeve=("#556b2f", "#3b4727", "#7a6a3a"), under="#6b6240", belt="#3a2f22", camo_pants=True,
         pants=("#556b2f", "#3b4727", "#7a6a3a"), boots="#2a2118", boots_l="#3a2f22"),
    # 1: tactical operator, short dark hair, a scar
    dict(skin="#e0b394", skin_d="#c4977a", hair="#1d1612", hair_d="#120d0a", hair_l="#2e231c", eye="#3a2a1c", brow="#120d0a", scar=True,
         torso=("#23252b", "#17181d", "#34363f"), sleeve=("#2b2d34", "#1c1d22", "#3b3e48"), under="#3a3d46", belt="#17181d",
         pants=("#2b2d34", "#1c1d22", "#3b3e48"), boots="#101113", boots_l="#202226", gloves="#17181d", back_rows=3, side_h=[0, 0, 1, 1, 2, 2, 3, 3]),
    # 2: former worker in a hi-vis vest, dark blond hair
    dict(skin="#e9c2a2", skin_d="#cfa386", hair="#8a6a3c", hair_d="#6b4f2a", hair_l="#a8854f", eye="#5a8a9a", brow="#6b4f2a",
         torso=("#6b7280", "#4b515c", "#8a909b"), sleeve=("#6b7280", "#4b515c", "#8a909b"), under="#8a909b", belt="#3a3a3a",
         pants=("#3b5b8a", "#2b4468", "#4d72a8"), boots="#4a3320", boots_l="#5b4129"),
    # 3: younger, black hair under a red cap, darker skin
    dict(skin="#8a5a3a", skin_d="#6e4429", hair="#15100d", hair_d="#0b0806", hair_l="#2a201a", eye="#2a1a10", brow="#0b0806",
         torso=("#4b4f58", "#363a42", "#5f646e"), sleeve=("#4b4f58", "#363a42", "#5f646e"), under="#5f646e", belt="#2a2a2a",
         pants=("#3b5b8a", "#2b4468", "#4d72a8"), boots="#d9d9d9", boots_l="#ffffff", back_rows=4),
]


def run():
    for i, v in enumerate(VARIANTS):
        save(person(v), f"entity/supe_hunter_{i}.png")


if __name__ == "__main__":
    run()
