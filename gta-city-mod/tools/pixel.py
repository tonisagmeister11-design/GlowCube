"""Tiny procedural pixel-art helper used by gen_textures.py.

Shapes are painted with *materials*. When rendering, every material gets a base colour,
a highlight on its upper edge and a shadow on its lower edge, and the whole silhouette
gets a dark outline. That gives hand-pixelled looking sprites from a few rectangles.
"""
from PIL import Image, ImageDraw

OUTLINE = (22, 22, 26, 255)

# material -> (shadow, base, highlight)
MATERIALS = {
    "metal": ((70, 74, 82), (112, 118, 128), (170, 176, 186)),
    "steel": ((104, 110, 120), (150, 156, 166), (206, 212, 220)),
    "dark": ((28, 29, 33), (46, 48, 54), (74, 77, 86)),
    "poly": ((24, 24, 26), (38, 38, 42), (60, 60, 66)),
    "wood": ((88, 52, 28), (130, 82, 44), (170, 116, 66)),
    "olive": ((62, 72, 40), (88, 102, 58), (122, 138, 82)),
    "tan": ((150, 120, 80), (190, 156, 108), (220, 192, 146)),
    "brass": ((150, 108, 30), (206, 160, 54), (246, 214, 110)),
    "copper": ((140, 70, 40), (184, 104, 62), (224, 146, 98)),
    "red": ((120, 20, 24), (184, 36, 40), (232, 82, 80)),
    "green": ((24, 90, 40), (40, 140, 60), (96, 196, 110)),
    "money": ((52, 102, 52), (86, 150, 80), (150, 206, 132)),
    "white": ((190, 192, 198), (228, 230, 234), (252, 252, 252)),
    "yellow": ((190, 150, 20), (236, 196, 40), (255, 236, 120)),
    "orange": ((180, 86, 20), (230, 126, 34), (255, 176, 90)),
    "blue": ((24, 40, 100), (40, 64, 150), (80, 110, 200)),
    "navy": ((16, 22, 48), (28, 36, 72), (52, 62, 108)),
    "cyan": ((30, 120, 150), (60, 180, 210), (150, 230, 250)),
    "glass": ((40, 80, 110), (70, 130, 170), (170, 220, 240)),
    "black": ((10, 10, 12), (20, 20, 24), (40, 40, 46)),
    "bread": ((170, 110, 50), (210, 150, 70), (240, 190, 110)),
    "meat": ((70, 36, 20), (100, 56, 30), (130, 80, 46)),
    "lettuce": ((50, 130, 40), (80, 180, 60), (140, 220, 100)),
    "cheese": ((210, 150, 20), (246, 190, 40), (255, 226, 110)),
    "purple": ((70, 30, 100), (110, 50, 150), (160, 100, 200)),
    "gray": ((90, 90, 96), (128, 128, 136), (170, 170, 178)),
    "lightgray": ((150, 152, 158), (186, 188, 194), (216, 218, 224)),
}


class Sprite:
    def __init__(self, w, h=None):
        self.w = w
        self.h = h or w
        self.mat = [[None] * self.w for _ in range(self.h)]
        self.fixed = {}

    # ------------------------------------------------------------ painting
    def px(self, x, y, mat):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.mat[y][x] = mat

    def rect(self, x0, y0, x1, y1, mat):
        for y in range(min(y0, y1), max(y0, y1) + 1):
            for x in range(min(x0, x1), max(x0, x1) + 1):
                self.px(x, y, mat)

    def poly(self, points, mat):
        mask = Image.new("L", (self.w, self.h), 0)
        ImageDraw.Draw(mask).polygon(points, fill=255)
        for y in range(self.h):
            for x in range(self.w):
                if mask.getpixel((x, y)):
                    self.px(x, y, mat)

    def line(self, x0, y0, x1, y1, mat):
        n = max(abs(x1 - x0), abs(y1 - y0), 1)
        for i in range(n + 1):
            self.px(round(x0 + (x1 - x0) * i / n), round(y0 + (y1 - y0) * i / n), mat)

    def ellipse(self, x0, y0, x1, y1, mat):
        mask = Image.new("L", (self.w, self.h), 0)
        ImageDraw.Draw(mask).ellipse((x0, y0, x1, y1), fill=255)
        for y in range(self.h):
            for x in range(self.w):
                if mask.getpixel((x, y)):
                    self.px(x, y, mat)

    def color(self, x, y, rgba):
        """Exact colour that is not shaded (details like lenses, text)."""
        if 0 <= x < self.w and 0 <= y < self.h:
            self.fixed[(x, y)] = rgba if len(rgba) == 4 else (*rgba, 255)
            if self.mat[y][x] is None:
                self.mat[y][x] = "_fixed"

    # ------------------------------------------------------------ rendering
    def render(self, outline=True, shade=True):
        img = Image.new("RGBA", (self.w, self.h), (0, 0, 0, 0))
        for y in range(self.h):
            for x in range(self.w):
                m = self.mat[y][x]
                if m is None:
                    continue
                if (x, y) in self.fixed:
                    img.putpixel((x, y), self.fixed[(x, y)])
                    continue
                shadow, base, light = MATERIALS[m]
                c = base
                if shade:
                    above = self.mat[y - 1][x] if y > 0 else None
                    below = self.mat[y + 1][x] if y < self.h - 1 else None
                    if above != m:
                        c = light
                    elif below != m:
                        c = shadow
                img.putpixel((x, y), (*c, 255))
        if outline:
            out = img.copy()
            for y in range(self.h):
                for x in range(self.w):
                    if self.mat[y][x] is not None:
                        continue
                    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                        nx, ny = x + dx, y + dy
                        if 0 <= nx < self.w and 0 <= ny < self.h and self.mat[ny][nx] is not None:
                            out.putpixel((x, y), OUTLINE)
                            break
            img = out
        return img
