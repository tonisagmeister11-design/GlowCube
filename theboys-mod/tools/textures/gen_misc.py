"""Items, blocks, GUI icons, tentacle, shield and mod icon."""
import math, os
from PIL import Image, ImageDraw, ImageFont, ImageFilter
from common import *

OUT = C("#14161c")


# ------------------------------------------------------------ syringes
def syringe(liquid=None, hub="#aab4bf", pad="#c9d1d8", cap=None, band=None, sparkle=None):
    """Diagonal syringe, tip top-right. Uses rotated coords d=x+y, a=x-y."""
    im = new(16, 16)
    silver = [C("#eef3f7"), C("#b9c3cd"), C("#828e9b")]
    glass = [C("#e6f4fb", 235), C("#bcd9e8", 235), C("#8fb3c6", 235)]
    body = {}
    for y in range(16):
        for x in range(16):
            d, a = x + y, x - y
            c = None
            if -11 <= a <= -10 and 12 <= d <= 18:
                c = C(cap) if cap else silver[0 if d <= 14 else 1 if d <= 16 else 2]
            elif -9 <= a <= -6 and d in (15, 16):
                c = silver[0] if d == 15 else silver[1]
            elif -5 <= a <= -4 and 12 <= d <= 18:
                c = C(cap) if cap else silver[0 if d <= 14 else 1 if d <= 16 else 2]
            elif -3 <= a <= 5 and 14 <= d <= 16:
                i = d - 14
                if liquid and a <= 4:
                    c = C(liquid[i])
                    if a == 4:
                        c = C(liquid[min(i, 1)]) if i else C(liquid[0])
                else:
                    c = glass[i]
                if band and a == 5:
                    c = C(band)
                if (a in (-1, 2)) and d == 14 and not liquid:
                    c = C("#5a7488")
            elif 6 <= a <= 8 and 14 <= d <= 16:
                c = C(hub) if d != 14 else silver[0]
                if cap and d == 16:
                    c = C(cap)
            if c:
                im.putpixel((x, y), c)
    outline(im, OUT)
    for a in (9, 11, 13):
        x, y = (15 + a) // 2, (15 - a) // 2
        px(im, x, y, C("#ffffff") if a == 13 else C("#d3dbe2"))
    if liquid:  # glossy highlight on the glass
        px(im, 6, 8, C("#ffffff")); px(im, 7, 7, C("#ffffff", 230))
    if sparkle:
        for (x, y, c) in sparkle:
            px(im, x, y, C(c))
    return im


def compound_v():
    return syringe(liquid=["#8ff4ff", "#2fb4ff", "#0f6be0"],
                   sparkle=[(4, 4, "#8ff4ff"), (3, 3, "#d6fbff"), (12, 11, "#8ff4ff")])


def compound_v1():
    return syringe(liquid=["#fff0a0", "#ffc21f", "#d98a0a"], cap="#c8202c", band="#e4b53c",
                   hub="#e4b53c",
                   sparkle=[(4, 3, "#fff0a0"), (3, 4, "#ffffff"), (12, 12, "#ffd75a"), (13, 11, "#fff0a0")])


def empty_syringe():
    return syringe(liquid=None)


def supe_virus():
    im = new(16, 16)
    for y in range(16):
        for x in range(16):
            d, a = x + y, x - y
            c = None
            if -9 <= a <= -5 and 13 <= d <= 17:           # hazard grip
                c = C("#f2c12e") if (a + (d // 2)) % 3 else C("#2a2b2f")
            elif -4 <= a <= 6 and 13 <= d <= 17:
                c = C("#4a4e57") if d != 13 else C("#6d727d")
                if -2 <= a <= 5 and 14 <= d <= 16:
                    c = [C("#c9ff6a"), C("#6fcf2a"), C("#3a8a14")][d - 14]
            elif 7 <= a <= 8 and 14 <= d <= 16:
                c = C("#9aa3ad")
            if c:
                im.putpixel((x, y), c)
    outline(im, OUT)
    for a in (9, 11, 13):
        px(im, (15 + a) // 2, (15 - a) // 2, C("#ffffff") if a == 13 else C("#cdd5db"))
    px(im, 6, 8, C("#f4ffd0")); px(im, 7, 7, C("#f4ffd0"))
    px(im, 3, 11, C("#2a2b2f")); px(im, 9, 5, C("#2a2b2f"))
    # skull/bio dots on the barrel
    for (x, y) in ((7, 9), (8, 10)):
        px(im, x, y, C("#1d3a0c"))
    px(im, 3, 3, C("#8fe83a")); px(im, 12, 12, C("#8fe83a"))
    return im


def shield_icon():
    im = new(16, 16)
    d = ImageDraw.Draw(im)
    d.ellipse((1, 1, 14, 14), fill=C("#7f8791"))
    d.ellipse((3, 3, 12, 12), fill=C("#a9b1ba"))
    for y in range(16):
        for x in range(16):
            if im.getpixel((x, y))[3] and (x + y) < 12 and (x - 1) ** 2 + (y - 1) ** 2 > 0:
                pass
    eagle = ["...##...", "#.####.#", "########", ".######.", "..####..", "...##..."]
    ascii_blit(im, 4, 5, eagle, {"#": C("#e4b53c")})
    px(im, 7, 5, C("#fbe38a")); px(im, 6, 6, C("#fbe38a"))
    px(im, 4, 3, C("#e5eaee")); px(im, 5, 2, C("#e5eaee")); px(im, 3, 4, C("#e5eaee"))
    outline(im, OUT)
    return im


# ------------------------------------------------------------ blocks
def noisy(w, h, base, var, seed=0):
    im = new(w, h)
    b = C(base)
    for y in range(h):
        for x in range(w):
            v = (h2(x, y, seed) % (2 * var + 1)) - var
            px(im, x, y, (max(0, min(255, b[0] + v)), max(0, min(255, b[1] + v)), max(0, min(255, b[2] + v)), 255))
    return im


def lab_tiles():
    im = noisy(16, 16, "#e6eaee", 4, 1)
    G, GD = C("#aab2ba"), C("#c4cad1")
    for i in range(16):
        for k in (7, 15):
            px(im, i, k, G); px(im, k, i, G)
        for k in (0, 8):
            px(im, i, k, C("#f6f8fa")); px(im, k, i, C("#f6f8fa"))
    for t in ((0, 0), (8, 0), (0, 8), (8, 8)):
        px(im, t[0] + 6, t[1] + 6, GD)
    return im


def lab_floor():
    im = noisy(16, 16, "#6a7078", 5, 2)
    for y in range(16):
        for x in range(16):
            r = h2(x, y, 7) % 23
            if r == 0:
                px(im, x, y, C("#8d939b"))
            elif r == 1:
                px(im, x, y, C("#4c5158"))
    for i in range(16):
        px(im, i, 15, C("#5a5f66")); px(im, 15, i, C("#5a5f66"))
        px(im, i, 0, C("#767c84")); px(im, 0, i, C("#767c84"))
    return im


def vought_panel():
    im = new(16, 16)
    for y in range(16):
        for x in range(16):
            v = 0x2a + (h2(x, 0, 3) % 4) * (1 if y % 2 else 0) + (h2(0, y, 4) % 4)
            px(im, x, y, (v, v + 4, v + 12, 255))
    for i in range(16):
        for c, (a, b) in ((C("#4a5262"), (0, 0)),):
            px(im, i, 0, C("#4a5262")); px(im, 0, i, C("#434b59"))
            px(im, i, 15, C("#14171d")); px(im, 15, i, C("#171a21"))
            px(im, i, 1, C("#363d4a")); px(im, 1, i, C("#323945"))
    for (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):
        px(im, x, y, C("#7b8494")); px(im, x + (1 if x < 8 else -1), y, C("#14171d"))
    # stylised V mark
    V1, V2, VD = C("#3f8cff"), C("#1f5fd6"), C("#0b2f78")
    for i in range(6):
        for (x, c) in ((3 + i, V1), (4 + i, V2)):
            px(im, x, 4 + i, c)
            px(im, 15 - x + 0, 4 + i, V1 if c == V2 else V2)
    px(im, 7, 10, V1); px(im, 8, 10, V2)
    px(im, 7, 11, VD); px(im, 8, 11, VD)
    px(im, 3, 3, VD); px(im, 12, 3, VD)
    return im


def fridge_front():
    im = new(16, 16)
    ST, STL, STD = C("#b9c1ca"), C("#dde3e8"), C("#7a838e")
    rect(im, 0, 0, 16, 16, ST)
    for i in range(16):
        px(im, i, 0, STL); px(im, 0, i, STL); px(im, i, 15, STD); px(im, 15, i, STD)
    rect(im, 2, 2, 11, 12, C("#1a2433"))
    for i in range(1, 14):
        pass
    for x in range(1, 14):
        px(im, x, 1, STD); px(im, x, 14, STL)
    for y in range(1, 15):
        px(im, 1, y, STD); px(im, 13, y, STL)
    rect(im, 2, 2, 11, 12, C("#18283a"))
    for sy in (5, 9, 13):
        rect(im, 2, sy, 11, 1, C("#8a949f"))
    for row_y in (3, 7, 11):
        for x in (3, 6, 9):
            rect(im, x, row_y, 2, 2, C("#2fb4ff"))
            px(im, x, row_y + 1, C("#0f6be0")); px(im, x + 1, row_y + 1, C("#0f6be0"))
            px(im, x, row_y, C("#8ff4ff"))
            px(im, x, row_y - 1, C("#d8dde2")); px(im, x + 1, row_y - 1, C("#7a838e"))
    # glass glare
    for i in range(4):
        px(im, 11 - i, 3 + i, C("#4d6a85"))
    rect(im, 14, 5, 1, 6, C("#3a3f47")); px(im, 14, 5, C("#6d737c"))
    px(im, 14, 2, C("#3fe0ff")); px(im, 14, 13, C("#e03030"))
    return im


def fridge_side():
    im = noisy(16, 16, "#b3bbc4", 3, 5)
    for i in range(16):
        px(im, i, 0, C("#dde3e8")); px(im, 0, i, C("#d0d7de"))
        px(im, i, 15, C("#7a838e")); px(im, 15, i, C("#8a939e"))
    for y in range(12, 15):
        for x in range(3, 13):
            px(im, x, y, C("#4d5560") if (y % 2) else C("#2d333b"))
    for y in (3, 4):
        px(im, 8, y, C("#8a939e"))
    for x in range(3, 13):
        px(im, x, 7, C("#97a1ab"))
    return im


def fridge_top():
    im = noisy(16, 16, "#c2c9d0", 3, 6)
    for i in range(16):
        px(im, i, 0, C("#e8edf1")); px(im, 0, i, C("#e8edf1"))
        px(im, i, 15, C("#7a838e")); px(im, 15, i, C("#7a838e"))
    for i in range(1, 15):
        px(im, i, 1, C("#a4adb6")); px(im, 1, i, C("#a4adb6"))
        px(im, i, 14, C("#a4adb6")); px(im, 14, i, C("#a4adb6"))
    for x in range(4, 12, 2):
        rect(im, x, 5, 1, 6, C("#4d5560"))
    return im


def containment_glass():
    im = new(16, 16)
    rect(im, 0, 0, 16, 16, C("#9fd0ff", 46))
    FR, FL, FD = C("#6f7a88"), C("#a2adbb"), C("#3e4651")
    for i in range(16):
        for k in range(2):
            px(im, i, k, FR); px(im, k, i, FR); px(im, i, 15 - k, FR); px(im, 15 - k, i, FR)
        px(im, i, 0, FL); px(im, 0, i, FL); px(im, i, 15, FD); px(im, 15, i, FD)
    for (x, y) in ((1, 1), (14, 1), (1, 14), (14, 14)):
        px(im, x, y, C("#c8d2de"))
    for i, a in enumerate((140, 110, 80, 80, 50, 50)):
        px(im, 4 + i, 11 - i, C("#ffffff", a)); px(im, 5 + i, 11 - i, C("#ffffff", a // 2))
    return im


# ------------------------------------------------------------ gui icons
def poly_icon(points, fill, hi=None, lo=None):
    im = new(16, 16)
    d = ImageDraw.Draw(im)
    d.polygon(points, fill=fill)
    if hi:
        for y in range(16):
            for x in range(16):
                if im.getpixel((x, y))[3] and (x * 1 + 0) % 4 == 1 and y % 2 == 0:
                    im.putpixel((x, y), hi)
    outline(im, OUT)
    return im


def icon_soldier_boy():
    im = new(16, 16)
    d = ImageDraw.Draw(im)
    d.ellipse((0, 0, 15, 15), fill=C("#33502f"))
    d.ellipse((1, 1, 14, 14), fill=None, outline=C("#233a21"))
    star = ["....#....", "....#....", "...###...", "#########", ".#######.", "..#####..", "..##.##..", ".##...##.", ".#.....#."]
    ascii_blit(im, 3, 3, star, {"#": C("#e4b53c")})
    px(im, 7, 5, C("#fbe38a")); px(im, 6, 6, C("#fbe38a")); px(im, 8, 5, C("#fbe38a"))
    outline(im, OUT)
    return im


def icon_a_train():
    im = new(16, 16)
    d = ImageDraw.Draw(im)
    d.polygon([(10, 0), (3, 9), (7, 9), (5, 15), (13, 5), (9, 5), (12, 0)], fill=C("#3fd0ff"))
    d.polygon([(10, 1), (5, 8), (8, 8), (8, 5), (10, 5), (11, 1)], fill=C("#d6f6ff"))
    px(im, 6, 10, C("#d6f6ff")); px(im, 10, 6, C("#1f8fe0")); px(im, 8, 9, C("#1f8fe0")); px(im, 6, 12, C("#1f8fe0"))
    outline(im, OUT)
    return im


def icon_butcher():
    im = new(16, 16)
    d = ImageDraw.Draw(im)
    pts = [(4, 14, 2.2), (5, 11, 2.2), (8, 9, 2.0), (11, 8, 1.8), (12, 5, 1.5), (10, 3, 1.1), (8, 3, 0.7)]
    for (x, y, r) in pts:
        d.ellipse((x - r, y - r, x + r, y + r), fill=C("#6e4a40"))
    for y in range(16):
        for x in range(16):
            if im.getpixel((x, y))[3]:
                if (x + y) % 5 == 0:
                    im.putpixel((x, y), C("#4b2f2c"))
                elif (x - y) % 7 == 0:
                    im.putpixel((x, y), C("#8d6256"))
    for (x, y) in ((5, 10), (9, 9), (12, 6), (6, 13)):
        px(im, x, y, C("#d9a99a"))
    outline(im, OUT)
    return im


def icon_homelander():
    im = new(16, 16)
    d = ImageDraw.Draw(im)
    for (x0, x1) in ((3, 0), (12, 15)):
        d.line((x0, 7, x1, 14), fill=C("#ff3030"), width=2)
        d.line((x0, 7, x1, 14), fill=C("#ffd0b0"), width=1)
    for ex in (1, 10):
        rect(im, ex, 4, 5, 3, C("#14161c"))
        rect(im, ex + 1, 5, 3, 1, C("#ff4a3a"))
        px(im, ex + 2, 5, C("#ffe0c0"))
    rect(im, 0, 3, 16, 1, C("#14161c")) if False else None
    for ex in (1, 10):
        for x in range(ex, ex + 5):
            px(im, x, 3, C("#d9b35c"))
    return im


# ------------------------------------------------------------ tentacle / shield
def tentacle():
    N = 32
    ramp = [C(c) for c in ("#2a1c1b", "#3b2624", "#503430", "#6a4640", "#7f5a52", "#9a7468")]

    def grid(seed, n):
        return [[(h2(i, j, seed) % 1000) / 1000.0 for j in range(n)] for i in range(n)]

    def sample(g, n, x, y):
        fx, fy = x / N * n, y / N * n
        x0, y0 = int(fx) % n, int(fy) % n
        tx, ty = fx - int(fx), fy - int(fy)
        tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
        x1, y1 = (x0 + 1) % n, (y0 + 1) % n
        a = g[x0][y0] * (1 - tx) + g[x1][y0] * tx
        b = g[x0][y1] * (1 - tx) + g[x1][y1] * tx
        return a * (1 - ty) + b * ty
    g1, g2 = grid(11, 4), grid(23, 8)
    im = new(N, N)
    for y in range(N):
        for x in range(N):
            v = 0.55 * sample(g1, 4, x, y) + 0.3 * sample(g2, 8, x, y)
            v += 0.22 * math.sin(2 * math.pi * y / 8 + 2.0 * sample(g1, 4, x, y))  # ridges
            v += 0.08 * math.sin(2 * math.pi * x * 2 / N)
            idx = max(0, min(5, int((v - 0.1) / 0.85 * 6)))
            px(im, x, y, ramp[idx])
    # wet specular sheen + dark pits (suckers)
    for (cx, cy) in ((6, 5), (21, 12), (12, 24), (27, 27), (3, 18)):
        for dx in range(-1, 2):
            for dy in range(-1, 2):
                if abs(dx) + abs(dy) < 2:
                    px(im, (cx + dx) % N, (cy + dy) % N, C("#3a1a1c") if (dx, dy) != (0, 0) else C("#1f0e10"))
        px(im, (cx - 1) % N, (cy - 2) % N, C("#b88a80"))
    for y in range(N):
        for x in range(N):
            if h2(x, y, 99) % 29 == 0:
                px(im, x, y, C("#d8b8ad"))
                px(im, (x + 1) % N, y, C("#a98378"))
    for y in range(N):  # glossy highlights along ridges
        if y % 8 == 2:
            for x in range(N):
                if (x * 3 + y) % 11 < 3:
                    px(im, x, y, C("#b99084"))
    return im


EAGLE_HALF = ["........##", ".......###", "#.....####", "##...#####", "###.######",
              "##########", ".#########", "..########", "#..#######", "#.#..#####",
              "....######", ".....#####", "......####", ".......###"]


def shield():
    N = 32
    im = new(N, N)
    cx = cy = 15.5
    for y in range(N):
        for x in range(N):
            r = math.hypot(x - cx, y - cy)
            if r > 15.6:
                continue
            if r > 14.3:
                c = C("#14161c")
            elif r > 12.3:   # rim
                c = C("#5d6670") if (x + y) % 9 else C("#7d8792")
                if x + y < 24:
                    c = C("#9aa4af")
            else:            # face, brushed steel, lit from top-left
                t = (x + y) / 62.0
                v = int(168 - 52 * t)
                c = (v, v + 6, v + 12, 255)
                if h2(x, 0, 4) % 5 == 0 and y % 2 == 0:
                    c = (c[0] - 8, c[1] - 8, c[2] - 8, 255)
            px(im, x, y, c)
    for i in range(12):  # rivets
        a = i * math.pi / 6
        rx, ry = int(round(cx + 13.3 * math.cos(a))), int(round(cy + 13.3 * math.sin(a)))
        px(im, rx, ry, C("#d8dee4")); px(im, rx + 1, ry, C("#3a4048"))
    # inner ring
    for y in range(N):
        for x in range(N):
            r = math.hypot(x - cx, y - cy)
            if 11.4 < r < 12.3:
                px(im, x, y, C("#3a4048"))
    # eagle
    G, GD, GL = C("#e4b53c"), C("#9a6c14"), C("#fbe38a")
    full = {}
    for j, row in enumerate(EAGLE_HALF):
        for i, ch in enumerate(row):
            if ch == "#":
                full[(i, j)] = 1
                full[(19 - i, j)] = 1
    ox, oy = 6, 9
    for (i, j) in full:
        nb = [(i + 1, j), (i - 1, j), (i, j + 1), (i, j - 1)]
        edge = any(n not in full for n in nb)
        c = GD if edge else (GL if (i + j) % 6 == 0 else G)
        px(im, ox + i, oy + j, c)
    px(im, ox + 9, oy - 1, GD); px(im, ox + 10, oy - 1, GD)
    px(im, ox + 9, oy + 1, C("#14161c")); px(im, ox + 10, oy + 1, C("#14161c"))  # eye
    # star above
    for (x, y) in ((16, 4), (15, 5), (16, 5), (17, 5), (14, 6), (15, 6), (16, 6), (17, 6), (18, 6)):
        pass
    for (x, y) in ((15, 6), (16, 6), (14, 7), (17, 7), (15, 7), (16, 7)):
        px(im, x, y, C("#14161c") if False else G)
    return im


# ------------------------------------------------------------ mod icon
def find_font(size):
    for p in ("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
              "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf"):
        if os.path.exists(p):
            return ImageFont.truetype(p, size)
    try:
        return ImageFont.load_default(size)
    except TypeError:
        return ImageFont.load_default()


def mod_icon():
    N = 128
    im = Image.new("RGBA", (N, N), (10, 12, 18, 255))
    px_ = im.load()
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - 64, y - 52) / 90.0
            r = int(14 + 26 * max(0, 1 - d)); g = int(16 + 40 * max(0, 1 - d)); b = int(24 + 90 * max(0, 1 - d))
            if y > 96:
                r += int((y - 96) * 1.2)
            px_[x, y] = (min(255, r), g, b, 255)
    glow = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    syr = compound_v().resize((96, 96), Image.NEAREST)
    g = syr.copy()
    g = g.filter(ImageFilter.GaussianBlur(7))
    im.alpha_composite(g, (16, 2)); im.alpha_composite(g, (16, 2))
    im.alpha_composite(syr, (16, 2))
    d = ImageDraw.Draw(im)
    f = find_font(19)
    text = "THE BOYS"
    w = d.textlength(text, font=f)
    x = (N - w) / 2
    for dx, dy in ((-2, 0), (2, 0), (0, -2), (0, 2), (2, 2), (-2, -2), (-2, 2), (2, -2), (3, 3)):
        d.text((x + dx, 98 + dy), text, font=f, fill=(10, 4, 6, 255))
    d.text((x, 98), text, font=f, fill=(224, 26, 43, 255))
    for k in range(int(w)):
        d.point((int(x) + k, 124), fill=(224, 26, 43, 255))
    return im


def run():
    save(compound_v(), "item/compound_v.png")
    save(compound_v1(), "item/compound_v1.png")
    save(supe_virus(), "item/supe_virus.png")
    save(empty_syringe(), "item/empty_syringe.png")
    save(shield_icon(), "item/soldier_boy_shield.png")
    save(lab_tiles(), "block/lab_tiles.png")
    save(lab_floor(), "block/lab_floor.png")
    save(vought_panel(), "block/vought_panel.png")
    save(fridge_front(), "block/v_fridge_front.png")
    save(fridge_side(), "block/v_fridge_side.png")
    save(fridge_top(), "block/v_fridge_top.png")
    save(containment_glass(), "block/containment_glass.png")
    save(icon_soldier_boy(), "gui/power/soldier_boy.png")
    save(icon_a_train(), "gui/power/a_train.png")
    save(icon_butcher(), "gui/power/butcher.png")
    save(icon_homelander(), "gui/power/homelander.png")
    save(tentacle(), "entity/tentacle.png")
    save(shield(), "entity/soldier_boy_shield.png")
    mod_icon().save(out("icon.png"))


if __name__ == "__main__":
    run()
