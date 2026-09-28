"""Generates 64x64 player-model skins for the city's NPCs (civilians, police, SWAT, gangs)."""
import os
import random

from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "gtacity", "textures",
                   "entity", "npc")

SKIN_TONES = [(255, 222, 196), (241, 200, 166), (226, 176, 136), (200, 146, 104), (164, 112, 76), (124, 84, 56),
              (92, 62, 42)]
HAIR = [(24, 20, 18), (58, 38, 24), (96, 64, 36), (200, 160, 90), (150, 60, 30), (150, 150, 150), (230, 210, 150)]
SHIRTS = [(200, 40, 40), (40, 90, 180), (240, 240, 240), (30, 30, 34), (60, 150, 80), (230, 200, 60), (240, 130, 40),
          (130, 60, 160), (90, 170, 200), (220, 110, 150), (110, 110, 118), (170, 140, 100)]
PANTS = [(46, 70, 120), (30, 30, 34), (170, 146, 100), (90, 90, 96), (60, 90, 150), (110, 76, 50)]
SHOES = [(24, 24, 26), (240, 240, 240), (110, 70, 40), (200, 40, 40)]


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3])


class Skin:
    def __init__(self, seed):
        self.img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
        self.rnd = random.Random(seed)

    def put(self, x, y, c, jitter=6):
        d = self.rnd.randint(-jitter, jitter) if jitter else 0
        self.img.putpixel((x, y), tuple(max(0, min(255, v + d)) for v in c[:3]) + (255,))

    def rect(self, x, y, w, h, c, jitter=6):
        for yy in range(y, y + h):
            for xx in range(x, x + w):
                self.put(xx, yy, c, jitter)

    def box(self, u, v, w, h, d, painter):
        """Calls painter(face, x0, y0, width, height) for all six faces of a box-UV cube."""
        painter("top", u + d, v, w, d)
        painter("bottom", u + d + w, v, w, d)
        painter("right", u, v + d, d, h)
        painter("front", u + d, v + d, w, h)
        painter("left", u + d + w, v + d, d, h)
        painter("back", u + 2 * d + w, v + d, w, h)

    def save(self, name):
        os.makedirs(OUT, exist_ok=True)
        self.img.save(os.path.join(OUT, name + ".png"))


def paint_head(s, o):
    skin, hair, style = o["skin"], o["hair"], o["style"]

    def head(face, x0, y0, w, h):
        s.rect(x0, y0, w, h, skin, 5)
        if style == "bald":
            return
        if face == "top":
            s.rect(x0, y0, w, h, hair, 8)
        elif face in ("right", "left", "back"):
            rows = {"short": 3, "buzz": 2, "cap": 3, "long": 7}[style]
            if face == "back":
                rows = {"short": 5, "buzz": 3, "cap": 5, "long": 8}[style]
            s.rect(x0, y0, w, rows, hair, 8)
        elif face == "front":
            rows = 2 if style != "buzz" else 1
            s.rect(x0, y0, w, rows, hair, 8)
            if style == "long":
                s.rect(x0, y0, 1, 7, hair, 8)
                s.rect(x0 + w - 1, y0, 1, 7, hair, 8)

    s.box(0, 0, 8, 8, 8, head)
    fx, fy = 8, 8  # front face
    brow = shade(o["hair"], 0.8)
    s.put(fx + 1, fy + 3, brow, 0)
    s.put(fx + 2, fy + 3, brow, 0)
    s.put(fx + 5, fy + 3, brow, 0)
    s.put(fx + 6, fy + 3, brow, 0)
    eye = random.Random(sum(o["skin"])).choice([(60, 100, 170), (70, 50, 30), (60, 130, 80), (40, 40, 40)])
    s.put(fx + 1, fy + 4, (245, 245, 245), 0)
    s.put(fx + 2, fy + 4, eye, 0)
    s.put(fx + 5, fy + 4, eye, 0)
    s.put(fx + 6, fy + 4, (245, 245, 245), 0)
    s.put(fx + 3, fy + 5, shade(o["skin"], 0.85), 0)
    s.put(fx + 4, fy + 5, shade(o["skin"], 0.85), 0)
    mouth = shade(o["skin"], 0.6)
    s.put(fx + 3, fy + 6, mouth, 0)
    s.put(fx + 4, fy + 6, mouth, 0)
    if o["beard"]:
        for x in range(8):
            s.put(fx + x, fy + 7, o["hair"], 6)
        s.put(fx, fy + 6, o["hair"], 6)
        s.put(fx + 7, fy + 6, o["hair"], 6)
        s.put(fx + 2, fy + 6, o["hair"], 6)
        s.put(fx + 5, fy + 6, o["hair"], 6)
    if o["style"] == "cap":
        cap = o["cap"]

        def hat(face, x0, y0, w, h):
            if face == "top":
                s.rect(x0, y0, w, h, cap)
            elif face == "bottom":
                return
            else:
                s.rect(x0, y0, w, 3, cap)
                if face == "front":
                    s.rect(x0, y0 + 2, w, 1, shade(cap, 0.6))

        s.box(32, 0, 8, 8, 8, hat)


def paint_body(s, o):
    shirt, top, skin = o["shirt"], o["top"], o["skin"]

    def body(face, x0, y0, w, h):
        base = shirt if top != "suit" else (34, 34, 40)
        s.rect(x0, y0, w, h, base)
        if face == "front":
            if top == "suit":
                s.rect(x0 + 3, y0, 2, 8, (240, 240, 240), 2)
                s.rect(x0 + 3, y0 + 1, 2, 6, (160, 30, 36), 3)
            if top == "tank":
                s.rect(x0 + 2, y0, 4, 2, skin, 4)
            if top == "hoodie":
                s.rect(x0 + 2, y0 + 7, 4, 3, shade(shirt, 0.8))
                s.put(x0 + 3, y0 + 1, (230, 230, 230), 0)
                s.put(x0 + 4, y0 + 1, (230, 230, 230), 0)
            if top == "tshirt":
                s.rect(x0 + 3, y0, 2, 1, skin, 4)
        if face in ("front", "back", "left", "right") and top != "suit":
            s.rect(x0, y0 + h - 1, w, 1, shade(o["pants"], 0.7))  # belt line
        if top == "tank" and face in ("left", "right"):
            s.rect(x0, y0, w, 2, skin, 4)

    s.box(16, 16, 8, 12, 4, body)


def paint_arms(s, o):
    shirt, top, skin = o["shirt"], o["top"], o["skin"]
    sleeve = {"tshirt": 4, "long": 12, "suit": 11, "hoodie": 12, "tank": 0}[top]
    color = shirt if top != "suit" else (34, 34, 40)

    def arm(face, x0, y0, w, h):
        s.rect(x0, y0, w, h, skin, 5)
        if face == "top":
            if sleeve:
                s.rect(x0, y0, w, h, color)
            return
        if face == "bottom":
            return
        if sleeve:
            s.rect(x0, y0, w, sleeve, color)
        if top == "suit":
            s.rect(x0, y0 + 10, w, 1, (240, 240, 240), 2)

    s.box(40, 16, 4, 12, 4, arm)
    s.box(32, 48, 4, 12, 4, arm)


def paint_legs(s, o):
    pants, shoes, skin = o["pants"], o["shoes"], o["skin"]

    def leg(face, x0, y0, w, h):
        s.rect(x0, y0, w, h, pants)
        if face == "top":
            return
        if face == "bottom":
            s.rect(x0, y0, w, h, shoes)
            return
        if o["shorts"]:
            s.rect(x0, y0 + 6, w, h - 6, skin, 5)
        s.rect(x0, y0 + h - 2, w, 2, shoes)
        if face == "front" and o["pants"] == PANTS[0]:
            s.put(x0 + 1, y0 + 3, shade(pants, 1.2), 0)

    s.box(0, 16, 4, 12, 4, leg)
    s.box(16, 48, 4, 12, 4, leg)


def build(o, seed):
    s = Skin(seed)
    paint_head(s, o)
    paint_body(s, o)
    paint_arms(s, o)
    paint_legs(s, o)
    return s


def random_outfit(seed):
    rnd = random.Random(seed * 7919 + 13)
    return dict(
        skin=rnd.choice(SKIN_TONES),
        hair=rnd.choice(HAIR),
        style=rnd.choice(["short", "short", "long", "bald", "cap", "buzz", "long"]),
        beard=rnd.random() < 0.25,
        shirt=rnd.choice(SHIRTS),
        top=rnd.choice(["tshirt", "tshirt", "long", "suit", "hoodie", "tank"]),
        pants=rnd.choice(PANTS),
        shorts=rnd.random() < 0.2,
        shoes=rnd.choice(SHOES),
        cap=rnd.choice(SHIRTS),
    )


def police(seed, swat=False):
    rnd = random.Random(seed * 31 + 7)
    navy = (28, 36, 72) if not swat else (26, 26, 30)
    o = dict(skin=rnd.choice(SKIN_TONES), hair=rnd.choice(HAIR[:4]), style="short", beard=False, shirt=navy,
             top="long", pants=(22, 22, 26), shorts=False, shoes=(16, 16, 18), cap=navy)
    s = build(o, seed + 1000)

    def cap(face, x0, y0, w, h):
        if face == "bottom":
            return
        if swat:
            s.rect(x0, y0, w, h if face == "top" else 4, (32, 34, 38))
            return
        if face == "top":
            s.rect(x0, y0, w, h, navy)
        else:
            s.rect(x0, y0, w, 3, navy)
            if face == "front":
                s.rect(x0, y0 + 2, w, 1, (12, 12, 14), 0)
                s.put(x0 + 3, y0 + 1, (230, 190, 60), 0)
                s.put(x0 + 4, y0 + 1, (230, 190, 60), 0)

    s.box(32, 0, 8, 8, 8, cap)
    if swat:
        for x in range(8, 16):
            s.put(x, 12, (20, 20, 20), 0)   # goggles
        s.put(9, 12, (120, 200, 240), 0)
        s.put(14, 12, (120, 200, 240), 0)

    # badge, belt, vest
    fx, fy = 20, 20
    if swat:
        s.rect(fx, fy + 1, 8, 8, (54, 58, 64))
        s.rect(fx + 1, fy + 3, 6, 1, (220, 220, 220), 0)
    else:
        s.put(fx + 5, fy + 2, (230, 190, 60), 0)
        s.put(fx + 6, fy + 2, (230, 190, 60), 0)
        s.put(fx + 5, fy + 3, (200, 160, 40), 0)
        s.put(fx + 2, fy + 3, (20, 20, 20), 0)  # radio
    s.rect(fx, fy + 10, 8, 1, (14, 14, 16), 0)
    s.put(fx + 3, fy + 10, (200, 200, 200), 0)
    s.put(fx + 4, fy + 10, (200, 200, 200), 0)
    return s


def gang(seed, color):
    rnd = random.Random(seed * 17 + 3)
    o = dict(skin=rnd.choice(SKIN_TONES[3:]), hair=HAIR[0], style=rnd.choice(["buzz", "cap", "bald"]),
             beard=rnd.random() < 0.4, shirt=color, top=rnd.choice(["tshirt", "tank", "hoodie"]),
             pants=rnd.choice([(46, 70, 120), (30, 30, 34), (150, 130, 90)]), shorts=rnd.random() < 0.3,
             shoes=rnd.choice([(240, 240, 240), (24, 24, 26)]), cap=color)
    s = build(o, seed + 2000)
    for x in range(8, 16):   # bandana
        s.put(x, 14, color, 4)
        s.put(x, 15, color, 4)
    return s


def main():
    for i in range(24):
        build(random_outfit(i), i).save("civilian_%d" % i)
    for i in range(4):
        police(i).save("police_%d" % i)
    police(9, swat=True).save("swat_0")
    colors = [(40, 140, 60), (120, 50, 160), (230, 200, 40)]
    for i in range(6):
        gang(i, colors[i % 3]).save("gang_%d" % i)


if __name__ == "__main__":
    main()
