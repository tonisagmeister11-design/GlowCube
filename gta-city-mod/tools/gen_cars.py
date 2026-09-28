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
}
TEX_W, TEX_H = 256, 160
CABIN_V = 80
WHEEL_UV = (200, 0)
BUMPER_UV = (140, 80)
LIGHTBAR_UV = (140, 90)

COLORS = {
    "red": (178, 30, 34), "blue": (34, 70, 160), "black": (26, 26, 30), "white": (226, 228, 232),
    "silver": (160, 164, 172), "green": (30, 110, 60), "yellow": (236, 190, 30), "orange": (230, 110, 30),
    "lime": (120, 200, 40), "navy": (24, 32, 70), "purple": (100, 40, 140), "darkgreen": (30, 60, 40),
}
VARIANTS = {
    "sedan": ["red", "blue", "black", "white", "silver", "green", "purple"],
    "sports": ["red", "yellow", "orange", "blue", "black", "lime"],
    "suv": ["black", "white", "silver", "darkgreen", "navy"],
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


def main():
    os.makedirs(OUT, exist_ok=True)
    seed = 0
    for shape, colors in VARIANTS.items():
        for c in colors:
            make(shape, c, seed).save(os.path.join(OUT, "%s_%s.png" % (shape, c)))
            seed += 1
    make("sedan", "white", 99, "police").save(os.path.join(OUT, "police.png"))
    make("sedan", "yellow", 98, "taxi").save(os.path.join(OUT, "taxi.png"))


if __name__ == "__main__":
    main()
