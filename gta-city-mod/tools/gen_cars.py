"""Car textures. Box-UV layout must match de.gtacity.client.model.CarModel (texture 256x160)."""
import os
import random

from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "gtacity", "textures",
                   "entity", "car")

# name: (body w, h, d), (cabin w, h, d)  -- keep in sync with CarModel.java
SHAPES = {
    "sedan": ((28, 10, 64), (24, 9, 32)),
    "sports": ((28, 8, 62), (24, 7, 26)),
    "suv": ((30, 13, 66), (28, 11, 40)),
    "super": ((30, 5, 54), (20, 4, 20)),
}
TEX_W, TEX_H = 256, 160
CABIN_V = 80
WHEEL_UV = (200, 0)
BUMPER_UV = (140, 80)
LIGHTBAR_UV = (140, 90)
SPOILER_UV = (140, 100)   # wing 26 x 2 x 6
STAND_UV = (140, 112)     # stands 2 x 3 x 2

COLORS = {
    "red": (178, 30, 34), "blue": (34, 70, 160), "black": (26, 26, 30), "white": (226, 228, 232),
    "silver": (160, 164, 172), "green": (30, 110, 60), "yellow": (236, 190, 30), "orange": (230, 110, 30),
    "lime": (120, 200, 40), "carbon": (34, 34, 38), "pearl": (236, 236, 244), "magenta": (200, 30, 120), "navy": (24, 32, 70), "purple": (100, 40, 140), "darkgreen": (30, 60, 40),
}
VARIANTS = {
    "sedan": ["red", "blue", "black", "white", "silver", "green", "purple"],
    "sports": ["red", "yellow", "orange", "blue", "black", "lime"],
    "suv": ["black", "white", "silver", "darkgreen", "navy"],
}
# Supercars: (paint, racing stripe)
SUPER = {
    "red": ("red", (20, 20, 22)), "orange": ("orange", (20, 20, 22)), "lime": ("lime", (20, 20, 22)),
    "pearl": ("pearl", (200, 30, 40)), "carbon": ("carbon", (236, 190, 30)), "magenta": ("magenta", (240, 240, 245)),
}


class Tex:
    def __init__(self, seed):
        self.img = Image.new("RGBA", (TEX_W, TEX_H), (0, 0, 0, 0))
        self.rnd = random.Random(seed)

    def put(self, x, y, c, jitter=3):
        if 0 <= x < TEX_W and 0 <= y < TEX_H:
            d = self.rnd.randint(-jitter, jitter) if jitter else 0
            self.img.putpixel((x, y), tuple(max(0, min(255, v + d)) for v in c[:3]) + (255,))

    def rect(self, x, y, w, h, c, jitter=3):
        for yy in range(y, y + h):
            for xx in range(x, x + w):
                self.put(xx, yy, c, jitter)


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c)


def faces(u, v, w, h, d):
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "west": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "east": (u + d + w, v + d, d, h),
        "back": (u + 2 * d + w, v + d, w, h),
    }


GLASS = (40, 60, 80)
GLASS_HI = (90, 130, 160)
DARK = (30, 30, 34)
CHROME = (190, 194, 200)


def paint_body(t, shape, color, stripe=None):
    (w, h, d), _ = SHAPES[shape]
    f = faces(0, 0, w, h, d)
    x, y, fw, fh = f["top"]
    t.rect(x, y, fw, fh, color)
    for yy in (y + 6, y + fh - 14):  # trunk and hood seams
        t.rect(x + 1, yy, fw - 2, 1, shade(color, 0.8))
    if stripe:
        t.rect(x + fw // 2 - 2, y, 4, fh, stripe)
    x, y, fw, fh = f["bottom"]
    t.rect(x, y, fw, fh, DARK)

    for side in ("west", "east"):
        x, y, fw, fh = f[side]
        t.rect(x, y, fw, fh, color)
        t.rect(x, y + fh - 2, fw, 2, shade(color, 0.55))       # skirt
        t.rect(x, y, fw, 1, shade(color, 1.15))                # shoulder highlight
        front_right = side == "west"
        for dist in (12, d - 12):                              # wheel arches (front / rear)
            cx = x + (fw - dist if front_right else dist)
            for yy in range(fh - 5, fh):
                r = 5 - (yy - (fh - 5))
                half = 6 if yy >= fh - 3 else 5
                for xx in range(cx - half, cx + half):
                    t.put(xx, y + yy, DARK, 2)
        door1 = x + (fw * 5 // 8 if front_right else fw * 3 // 8)
        door2 = x + (fw * 3 // 8 if front_right else fw * 5 // 8)
        for dx in (door1, door2):
            t.rect(dx, y + 1, 1, fh - 4, shade(color, 0.7))
        for dx in (door1, door2):
            hx = dx - 4 if front_right else dx + 2
            t.rect(hx, y + 3, 2, 1, CHROME, 0)
        if stripe:
            t.rect(x, y + fh // 2, fw, 1, stripe)

    x, y, fw, fh = f["front"]
    t.rect(x, y, fw, fh, color)
    t.rect(x + 6, y + 3, fw - 12, fh - 5, DARK)                # grille
    for gx in range(x + 7, x + fw - 6, 2):
        t.rect(gx, y + 4, 1, fh - 7, (60, 60, 66))
    t.rect(x + 1, y + 2, 4, 3, (250, 246, 220), 0)             # headlights
    t.rect(x + fw - 5, y + 2, 4, 3, (250, 246, 220), 0)
    t.rect(x, y + fh - 1, fw, 1, DARK)

    x, y, fw, fh = f["back"]
    t.rect(x, y, fw, fh, color)
    t.rect(x + 1, y + 2, 5, 3, (200, 20, 20), 0)               # tail lights
    t.rect(x + fw - 6, y + 2, 5, 3, (200, 20, 20), 0)
    t.rect(x + fw // 2 - 4, y + fh - 5, 8, 3, (240, 240, 230), 0)  # plate
    t.rect(x + fw // 2 - 3, y + fh - 4, 6, 1, (40, 40, 120), 0)
    t.rect(x, y + fh - 1, fw, 1, DARK)


def paint_cabin(t, shape, color, roof=None):
    _, (w, h, d) = SHAPES[shape]
    f = faces(0, CABIN_V, w, h, d)
    x, y, fw, fh = f["top"]
    t.rect(x, y, fw, fh, roof or color)
    x, y, fw, fh = f["bottom"]
    t.rect(x, y, fw, fh, DARK)
    for side in ("west", "east"):
        x, y, fw, fh = f[side]
        t.rect(x, y, fw, fh, color)
        t.rect(x + 2, y + 1, fw - 4, fh - 2, GLASS)
        t.rect(x + 2, y + 1, fw - 4, 1, GLASS_HI)
        t.rect(x + fw // 2, y, 2, fh, color)                   # B-pillar
    x, y, fw, fh = f["front"]
    t.rect(x, y, fw, fh, color)
    t.rect(x + 1, y + 1, fw - 2, fh - 1, GLASS)
    for i in range(3):
        t.put(x + 4 + i, y + 2 + i, GLASS_HI, 0)
        t.put(x + 8 + i, y + 2 + i, GLASS_HI, 0)
    x, y, fw, fh = f["back"]
    t.rect(x, y, fw, fh, color)
    t.rect(x + 2, y + 1, fw - 4, fh - 2, GLASS)


def paint_wheels(t):
    u, v = WHEEL_UV
    f = faces(u, v, 4, 10, 10)
    for name, (x, y, fw, fh) in f.items():
        t.rect(x, y, fw, fh, (22, 22, 24))
        if name in ("west", "east"):
            cx, cy = x + 5, y + 5
            for yy in range(y, y + fh):
                for xx in range(x, x + fw):
                    r = ((xx + 0.5 - cx) ** 2 + (yy + 0.5 - cy) ** 2) ** 0.5
                    if r < 3.2:
                        t.put(xx, yy, CHROME if r > 1.2 else (90, 90, 96), 4)
        else:
            for yy in range(y, y + fh, 2):
                t.rect(x, yy, fw, 1, (40, 40, 44))


def paint_bumper(t, w):
    u, v = BUMPER_UV
    for name, (x, y, fw, fh) in faces(u, v, w, 3, 2).items():
        t.rect(x, y, fw, fh, (54, 56, 62))
        t.rect(x, y, fw, 1, (120, 124, 130))


def paint_lightbar(t, kind):
    u, v = LIGHTBAR_UV
    for name, (x, y, fw, fh) in faces(u, v, 16, 2, 4).items():
        if kind == "police":
            t.rect(x, y, fw // 2, fh, (220, 30, 30), 0)
            t.rect(x + fw // 2, y, fw - fw // 2, fh, (40, 80, 240), 0)
        else:
            t.rect(x, y, fw, fh, (240, 200, 30), 0)
            for xx in range(x, x + fw, 2):
                t.put(xx, y, (20, 20, 20), 0)


def make(shape, color_name, seed, special=None):
    t = Tex(seed)
    color = COLORS.get(color_name, (200, 200, 200))
    stripe = None
    roof = None
    if special == "police":
        color = (236, 236, 240)
        roof = (236, 236, 240)
    if special == "taxi":
        color = COLORS["yellow"]
        stripe = (20, 20, 20)
    paint_body(t, shape, color, stripe)
    if special == "police":
        (w, h, d), _ = SHAPES[shape]
        f = faces(0, 0, w, h, d)
        for side in ("west", "east"):
            x, y, fw, fh = f[side]
            t.rect(x + fw // 4, y + 1, fw // 2, fh - 3, (20, 20, 24))    # black doors
            t.rect(x + fw // 4 + 4, y + 3, fw // 2 - 8, 2, (240, 240, 240), 0)
        x, y, fw, fh = f["front"]
        t.rect(x, y, fw, fh, (20, 20, 24))
        t.rect(x + 1, y + 2, 4, 3, (250, 246, 220), 0)
        t.rect(x + fw - 5, y + 2, 4, 3, (250, 246, 220), 0)
    paint_cabin(t, shape, color, roof)
    paint_wheels(t)
    paint_bumper(t, SHAPES[shape][0][0])
    paint_lightbar(t, special or "police")
    return t.img


LED = (220, 240, 255)
TAIL_LED = (255, 30, 40)


SUPER_PARTS = {  # (u, v, w, h, d) -- keep in sync with CarModel.createSuper()
    "nose": (0, 110, 28, 3, 14), "cover": (0, 130, 18, 2, 12), "screen": (88, 80, 18, 2, 6),
    "intake": (150, 120, 1, 3, 8), "mirror": (170, 120, 2, 1, 1), "wheel": (200, 24, 4, 9, 9),
    "wing": (140, 100, 28, 1, 6), "stand": (140, 112, 2, 6, 2),
}
CARBON = (24, 24, 28)


def paint_super(t, color, stripe):
    (w, h, d), (cw, ch, cd) = SHAPES["super"]
    hi = shade(color, 1.18)
    lo = shade(color, 0.7)
    # main body: front edge is the "top" face row nearest v=0 -> z = -20 (towards the nose)
    f = faces(0, 0, w, h, d)
    x, y, fw, fh = f["top"]
    t.rect(x, y, fw, fh, color)
    t.rect(x + fw // 2 - 4, y, 3, fh, stripe)                  # twin racing stripes
    t.rect(x + fw // 2 + 1, y, 3, fh, stripe)
    t.rect(x, y, 2, fh, hi)                                    # bright shoulder lines
    t.rect(x + fw - 2, y, 2, fh, hi)
    x, y, fw, fh = f["bottom"]
    t.rect(x, y, fw, fh, DARK)
    for side in ("west", "east"):
        x, y, fw, fh = f[side]
        t.rect(x, y, fw, fh, color)
        t.rect(x, y, fw, 1, hi)
        t.rect(x, y + fh - 1, fw, 1, CARBON)                   # carbon side skirt
        front_right = side == "west"   # west face: u grows from the back (z=+34) to the front
        # wheel arches: front wheel at z=-24 (body starts at -20 -> only the rear part of the arch shows),
        # rear wheel at z=22 -> 42 px from the body front
        for centre in (-4, 42):
            cx = x + (fw - centre if front_right else centre)
            for yy in range(fh - 4, fh):
                for xx in range(cx - 6, cx + 6):
                    if x <= xx < x + fw:
                        t.put(xx, y + yy, (14, 14, 16), 1)
        # swooping crease towards the side intake
        for i in range(20):
            px = x + (fw - (10 + i) if front_right else 10 + i)
            t.put(px, y + 1 + i // 7, lo, 0)
    x, y, fw, fh = f["front"]   # the body front sits behind the nose: mostly hidden, keep it dark
    t.rect(x, y, fw, fh, lo)
    x, y, fw, fh = f["back"]
    t.rect(x, y, fw, fh, color)
    t.rect(x + 1, y + 1, fw - 2, 1, TAIL_LED, 0)                # full-width LED tail strip
    t.rect(x + 3, y + 2, fw - 6, fh - 2, (18, 18, 20))          # diffuser
    for xx in range(x + 4, x + fw - 4, 3):
        t.rect(xx, y + 2, 1, fh - 2, (60, 60, 66))
    t.rect(x + fw // 2 - 5, y + fh - 2, 3, 2, (150, 150, 158), 0)  # quad exhaust
    t.rect(x + fw // 2 + 2, y + fh - 2, 3, 2, (150, 150, 158), 0)

    # nose: long flat wedge with vents and slim LED lights
    u, v, nw, nh, nd = SUPER_PARTS["nose"]
    f = faces(u, v, nw, nh, nd)
    for name, (x, y, fw, fh) in f.items():
        t.rect(x, y, fw, fh, DARK if name == "bottom" else color)
    x, y, fw, fh = f["top"]
    t.rect(x + fw // 2 - 4, y, 3, fh, stripe)
    t.rect(x + fw // 2 + 1, y, 3, fh, stripe)
    t.rect(x + 3, y + 3, 6, 7, (22, 22, 26))                   # bonnet vents
    t.rect(x + fw - 9, y + 3, 6, 7, (22, 22, 26))
    for yy in range(y + 4, y + 10, 2):
        t.rect(x + 3, yy, 6, 1, lo)
        t.rect(x + fw - 9, yy, 6, 1, lo)
    x, y, fw, fh = f["front"]
    t.rect(x, y, fw, fh, color)
    t.rect(x + 1, y, 7, 1, LED, 0)                              # slim LED headlights
    t.rect(x + fw - 8, y, 7, 1, LED, 0)
    t.rect(x + 9, y + 1, fw - 18, fh - 1, (16, 16, 18))         # big central intake
    for side in ("west", "east"):
        x, y, fw, fh = f[side]
        t.rect(x, y + fh - 1, fw, 1, CARBON)

    # canopy: dark tinted glass all round, paint only on the roof
    f = faces(0, CABIN_V, cw, ch, cd)
    tint = (16, 22, 30)
    tint_hi = (70, 96, 120)
    for name, (x, y, fw, fh) in f.items():
        if name == "top":
            t.rect(x, y, fw, fh, color)
            t.rect(x + fw // 2 - 4, y, 3, fh, stripe)
            t.rect(x + fw // 2 + 1, y, 3, fh, stripe)
        elif name == "bottom":
            t.rect(x, y, fw, fh, DARK)
        else:
            t.rect(x, y, fw, fh, tint)
            t.rect(x, y, fw, 1, color)
            t.rect(x + 2, y + 1, max(2, fw // 3), 1, tint_hi)
    # raked windscreen in front of the canopy
    u, v, sw, sh, sd = SUPER_PARTS["screen"]
    for name, (x, y, fw, fh) in faces(u, v, sw, sh, sd).items():
        t.rect(x, y, fw, fh, tint)
        if name == "top":
            for i in range(min(fw, fh)):
                t.put(x + 3 + i, y + i, tint_hi, 0)
    # engine cover with louvres
    u, v, ew, eh, ed = SUPER_PARTS["cover"]
    for name, (x, y, fw, fh) in faces(u, v, ew, eh, ed).items():
        t.rect(x, y, fw, fh, color)
        if name == "top":
            t.rect(x + 2, y + 1, fw - 4, fh - 2, CARBON)
            for yy in range(y + 2, y + fh - 1, 2):
                t.rect(x + 3, yy, fw - 6, 1, (70, 70, 76))
    for part in ("intake",):
        u, v, iw, ih, idd = SUPER_PARTS[part]
        for name, (x, y, fw, fh) in faces(u, v, iw, ih, idd).items():
            t.rect(x, y, fw, fh, (14, 14, 16))
    u, v, mw, mh, md = SUPER_PARTS["mirror"]
    for name, (x, y, fw, fh) in faces(u, v, mw, mh, md).items():
        t.rect(x, y, fw, fh, color)
    u, v, ww, wh, wd = SUPER_PARTS["wing"]
    for name, (x, y, fw, fh) in faces(u, v, ww, wh, wd).items():
        t.rect(x, y, fw, fh, CARBON)
        if name == "top":
            t.rect(x, y + fh - 1, fw, 1, stripe)
            t.rect(x, y, 2, fh, color)
            t.rect(x + fw - 2, y, 2, fh, color)
    u, v, sw, sh, sd = SUPER_PARTS["stand"]
    for name, (x, y, fw, fh) in faces(u, v, sw, sh, sd).items():
        t.rect(x, y, fw, fh, (40, 40, 44))


def paint_super_wheels(t):
    u, v, ww, wh, wd = SUPER_PARTS["wheel"]
    for name, (x, y, fw, fh) in faces(u, v, ww, wh, wd).items():
        t.rect(x, y, fw, fh, (18, 18, 20))
        if name in ("west", "east"):
            cx, cy = x + fw / 2, y + fh / 2
            for yy in range(y, y + fh):
                for xx in range(x, x + fw):
                    r = ((xx + 0.5 - cx) ** 2 + (yy + 0.5 - cy) ** 2) ** 0.5
                    if 1.1 < r < 3.4:
                        spoke = (int((xx - cx) * 3 + (yy - cy)) % 3) == 0
                        t.put(xx, yy, (34, 34, 38) if spoke else (60, 62, 68), 2)   # dark sport rims
                    elif r <= 1.1:
                        t.put(xx, yy, (220, 30, 30), 0)                              # red hub
        else:
            for yy in range(y, y + fh, 2):
                t.rect(x, yy, fw, 1, (34, 34, 38))


def make_super(color_name, stripe, seed):
    t = Tex(seed)
    paint_super(t, COLORS[color_name], stripe)
    paint_wheels(t)
    paint_super_wheels(t)
    paint_bumper(t, SHAPES["super"][0][0])
    paint_lightbar(t, "police")
    return t.img


# ---------------------------------------------------------------- helicopter (texture 256x128)
HELI_W, HELI_H = 256, 128
# part: (u, v, w, h, d) -- keep in sync with HelicopterModel.java
HELI_PARTS = {
    "body": (0, 0, 22, 16, 36), "nose": (0, 56, 18, 12, 8), "boom": (120, 0, 6, 6, 40),
    "fin": (120, 50, 2, 14, 8), "skid": (0, 80, 2, 2, 40), "strut": (90, 80, 2, 4, 2),
    "mast": (100, 80, 4, 4, 4), "blade": (0, 100, 55, 1, 5), "tail_blade": (120, 80, 1, 16, 3),
}


def heli_texture():
    img = Image.new("RGBA", (HELI_W, HELI_H), (0, 0, 0, 0))
    t = Tex(77)
    t.img = img
    navy, white, glass = (26, 36, 76), (232, 234, 240), (40, 70, 100)

    def part(name, fill):
        u, v, w, h, d = HELI_PARTS[name]
        fl = faces(u, v, w, h, d)
        for fname, (x, y, fw, fh) in fl.items():
            fill(fname, x, y, fw, fh)

    def body(fname, x, y, fw, fh):
        if fname == "top":
            t.rect(x, y, fw, fh, white)
        elif fname == "bottom":
            t.rect(x, y, fw, fh, navy)
        else:
            t.rect(x, y, fw, fh, navy)
            t.rect(x, y, fw, fh // 3, white)                     # white upper half
            t.rect(x, y + fh // 3, fw, 1, (200, 170, 40), 0)     # gold stripe
            if fname in ("west", "east"):
                t.rect(x + fw // 3, y + 2, fw // 3, fh // 2, (14, 14, 18))   # open door
                t.rect(x + 2, y + 2, fw // 5, fh // 3, glass)
                t.rect(x + fw - fw // 5 - 2, y + 2, fw // 5, fh // 3, glass)
            if fname == "front":
                t.rect(x + 2, y + 1, fw - 4, fh // 2, glass)
    part("body", body)

    def nose(fname, x, y, fw, fh):
        t.rect(x, y, fw, fh, navy)
        if fname in ("top", "front", "west", "east"):
            t.rect(x + 1, y + 1, fw - 2, fh // 2 + 1, glass)
            t.rect(x + 2, y + 1, fw // 3, 1, (120, 170, 210))
    part("nose", nose)
    part("boom", lambda f, x, y, fw, fh: (t.rect(x, y, fw, fh, navy), t.rect(x, y, fw, 1, white)))
    part("fin", lambda f, x, y, fw, fh: (t.rect(x, y, fw, fh, navy), t.rect(x, y, fw, 2, (220, 30, 30), 0)))
    for name in ("skid", "strut", "mast"):
        part(name, lambda f, x, y, fw, fh: t.rect(x, y, fw, fh, (50, 52, 58)))
    part("blade", lambda f, x, y, fw, fh: (t.rect(x, y, fw, fh, (30, 30, 34)), t.rect(x, y, 3, fh, (230, 200, 40), 0)))
    part("tail_blade", lambda f, x, y, fw, fh: t.rect(x, y, fw, fh, (30, 30, 34)))
    return img


def main():
    os.makedirs(OUT, exist_ok=True)
    seed = 0
    for shape, colors in VARIANTS.items():
        for c in colors:
            make(shape, c, seed).save(os.path.join(OUT, "%s_%s.png" % (shape, c)))
            seed += 1
    make("sedan", "white", 99, "police").save(os.path.join(OUT, "police.png"))
    make("sedan", "yellow", 98, "taxi").save(os.path.join(OUT, "taxi.png"))
    for i, (name, (color, stripe)) in enumerate(SUPER.items()):
        make_super(color, stripe, 200 + i).save(os.path.join(OUT, "super_%s.png" % name))
    heli_dir = os.path.join(OUT, "..", "helicopter")
    os.makedirs(heli_dir, exist_ok=True)
    heli_texture().save(os.path.join(heli_dir, "police.png"))


if __name__ == "__main__":
    main()
