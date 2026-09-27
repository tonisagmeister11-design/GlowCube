"""Car decal atlas: fictional sponsor / company logos and race number roundels.

Layout: 4 columns x 8 rows of 512x256 cells (2048x2048 RGBA, alpha = coverage).
    cells  0-15  sponsor / company logos (names are made up for Port Aurelia)
    cells 16-31  race number roundels
Needs Pillow (the Blender venv has it). Called from generate_textures.py.
"""
import os
import random

from PIL import Image, ImageDraw, ImageFont

CW, CH = 512, 256
SPONSORS = [
    # name, style, colours (primary, secondary, text)
    ("VOLTEX", "slash", (255, 214, 0), (20, 20, 22), (255, 255, 255)),
    ("HELIX OIL", "plate", (200, 20, 30), (255, 255, 255), (255, 255, 255)),
    ("RAPTOR TYRES", "outline", (255, 255, 255), (15, 15, 15), (255, 255, 255)),
    ("KODIAK ENERGY", "emblem", (40, 200, 90), (10, 30, 20), (255, 255, 255)),
    ("MAKO RACING", "slash", (0, 170, 255), (255, 255, 255), (255, 255, 255)),
    ("BAYSIDE PIZZA", "plate", (230, 120, 20), (255, 240, 200), (255, 255, 255)),
    ("PULSE FM", "emblem", (230, 30, 140), (30, 10, 40), (255, 255, 255)),
    ("APEX MOTORS", "outline", (255, 60, 40), (255, 255, 255), (255, 255, 255)),
    ("TIDEWAY EXPRESS", "plate", (20, 90, 200), (255, 255, 255), (255, 255, 255)),
    ("FALCO LOGISTICS", "emblem", (255, 150, 0), (25, 25, 30), (255, 255, 255)),
    ("ORBITA", "slash", (160, 80, 255), (255, 255, 255), (255, 255, 255)),
    ("BLAZE", "outline", (255, 90, 0), (255, 220, 0), (255, 220, 0)),
    ("NEPTUNE WAVE", "emblem", (0, 190, 200), (5, 40, 60), (255, 255, 255)),
    ("ZEPHYR", "slash", (240, 240, 240), (200, 0, 20), (255, 255, 255)),
    ("GRIDLINE", "plate", (15, 15, 18), (255, 214, 0), (255, 214, 0)),
    ("AURELIA ELECTRIC", "emblem", (60, 130, 255), (255, 255, 255), (255, 255, 255)),
]


def _font(size, italic=True):
    cands = ["/usr/share/fonts/truetype/freefont/FreeSansBoldOblique.ttf" if italic else
             "/usr/share/fonts/truetype/freefont/FreeSansBold.ttf",
             "/usr/share/fonts/truetype/liberation/LiberationSans-BoldItalic.ttf" if italic else
             "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf",
             "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
             "C:/Windows/Fonts/arialbi.ttf" if italic else "C:/Windows/Fonts/arialbd.ttf"]
    for p in cands:
        if os.path.exists(p):
            return ImageFont.truetype(p, size)
    return ImageFont.load_default()


def _fit_font(draw, text, max_w, max_h, italic=True):
    size = max_h
    while size > 10:
        f = _font(size, italic)
        box = draw.textbbox((0, 0), text, font=f)
        if box[2] - box[0] <= max_w and box[3] - box[1] <= max_h:
            return f, box
        size -= 4
    f = _font(10, italic)
    return f, draw.textbbox((0, 0), text, font=f)


def _text_center(draw, text, cx, cy, max_w, max_h, fill, stroke=0, stroke_fill=None, italic=True):
    f, box = _fit_font(draw, text, max_w, max_h, italic)
    w, h = box[2] - box[0], box[3] - box[1]
    draw.text((cx - w / 2 - box[0], cy - h / 2 - box[1]), text, font=f, fill=fill, stroke_width=stroke,
              stroke_fill=stroke_fill)


def sponsor_cell(name, style, c1, c2, ct):
    # draw at 2x and downsample for clean edges
    S = 2
    im = Image.new("RGBA", (CW * S, CH * S), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    W, H = CW * S, CH * S
    if style == "plate":
        d.rounded_rectangle((16, 40, W - 16, H - 40), radius=60, fill=c1 + (255,))
        d.rounded_rectangle((34, 58, W - 34, H - 58), radius=48, outline=c2 + (255,), width=10)
        _text_center(d, name, W / 2, H / 2, W - 140, H - 190, ct + (255,), italic=False)
    elif style == "slash":
        d.polygon([(60, H - 40), (200, 40), (W - 40, 40), (W - 180, H - 40)], fill=c1 + (255,))
        d.polygon([(10, H - 40), (80, 40), (150, 40), (80, H - 40)], fill=c2 + (255,))
        _text_center(d, name, W / 2 + 40, H / 2, W - 330, H - 150, c2 + (255,) if c1[0] + c1[1] + c1[2] > 500 else ct + (255,))
    elif style == "outline":
        _text_center(d, name, W / 2, H / 2 - 14, W - 60, H - 150, c1 + (255,), stroke=12, stroke_fill=c2 + (255,))
        d.rectangle((W * 0.12, H - 78, W * 0.88, H - 58), fill=c1 + (255,))
    else:  # emblem: roundel + wordmark
        r = H * 0.36
        cx, cy = H * 0.5, H * 0.5
        d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=c1 + (255,))
        d.ellipse((cx - r * 0.62, cy - r * 0.62, cx + r * 0.62, cy + r * 0.62), fill=c2 + (255,))
        _text_center(d, name[0], cx, cy, r * 1.0, r * 1.0, c1 + (255,), italic=False)
        words = name.split(" ")
        if len(words) > 1:
            _text_center(d, words[0], (H + W) / 2, H * 0.36, W - H - 40, H * 0.34, ct + (255,), stroke=6,
                         stroke_fill=(10, 10, 12, 255))
            _text_center(d, " ".join(words[1:]), (H + W) / 2, H * 0.68, W - H - 40, H * 0.22, c1 + (255,), stroke=5,
                         stroke_fill=(10, 10, 12, 255), italic=False)
        else:
            _text_center(d, name, (H + W) / 2, H / 2, W - H - 40, H * 0.4, ct + (255,), stroke=6,
                         stroke_fill=(10, 10, 12, 255))
    return im.resize((CW, CH), Image.LANCZOS)


def number_cell(num, style):
    S = 2
    im = Image.new("RGBA", (CW * S, CH * S), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    W, H = CW * S, CH * S
    cx, cy, r = W / 2, H / 2, H * 0.46
    if style == 0:
        d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=(250, 250, 250, 255), outline=(15, 15, 15, 255), width=12)
        _text_center(d, str(num), cx, cy, r * 1.4, r * 1.2, (15, 15, 15, 255), italic=False)
    elif style == 1:
        d.rounded_rectangle((cx - r * 1.1, cy - r, cx + r * 1.1, cy + r), radius=30, fill=(250, 250, 250, 255))
        _text_center(d, str(num), cx, cy, r * 1.8, r * 1.4, (200, 10, 20, 255))
    else:
        _text_center(d, str(num), cx, cy, r * 2.6, r * 1.8, (255, 255, 255, 255), stroke=14,
                     stroke_fill=(10, 10, 10, 255))
    return im.resize((CW, CH), Image.LANCZOS)


def build(out_dir):
    atlas = Image.new("RGBA", (CW * 4, CH * 8), (0, 0, 0, 0))
    for i, s in enumerate(SPONSORS):
        atlas.paste(sponsor_cell(*s), ((i % 4) * CW, (i // 4) * CH))
    rng = random.Random(77)
    nums = rng.sample(range(2, 99), 16)
    for k, n in enumerate(nums):
        i = 16 + k
        atlas.paste(number_cell(n, k % 3), ((i % 4) * CW, (i // 4) * CH))
    path = os.path.join(out_dir, "car_decals.png")
    atlas.save(path)
    return path


if __name__ == "__main__":
    here = os.path.dirname(os.path.abspath(__file__))
    print(build(os.path.normpath(os.path.join(here, "..", "..", "Game", "assets", "textures"))))
