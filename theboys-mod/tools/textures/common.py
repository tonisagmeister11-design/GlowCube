"""Shared helpers for The Boys texture generators (Pillow only)."""
import os
from PIL import Image

ROOT = os.environ.get("THEBOYS_ASSETS",
                      "/home/user/GlowCube/theboys-mod/src/main/resources/assets/theboys")


def out(rel):
    p = os.path.join(ROOT, rel if rel.endswith("icon.png") else os.path.join("textures", rel))
    os.makedirs(os.path.dirname(p), exist_ok=True)
    return p


def C(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def new(w, h, c=(0, 0, 0, 0)):
    return Image.new("RGBA", (w, h), c)


def px(im, x, y, c):
    if 0 <= x < im.width and 0 <= y < im.height:
        im.putpixel((x, y), c)


def rect(im, x, y, w, h, c):
    for yy in range(y, y + h):
        for xx in range(x, x + w):
            px(im, xx, yy, c)


def h2(x, y, s=0):
    v = (x * 73856093) ^ (y * 19349663) ^ (s * 83492791)
    v ^= v >> 13
    v = (v * 0x5bd1e995) & 0xFFFFFFFF
    return (v ^ (v >> 15)) & 0xFFFF


def hexfill(im, base, dark, light, x0=0, y0=0, w=None, h=None):
    """Subtle 2-tone honeycomb-ish dithering used for the hex suits."""
    w = im.width - x0 if w is None else w
    h = im.height - y0 if h is None else h
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            r = (y // 2) % 2
            p = (x + 2 * r) % 4
            c = base
            if y % 2 == 0 and p == 0:
                c = light
            elif y % 2 == 1 and p == 2:
                c = dark
            px(im, x, y, c)


def ascii_blit(im, x0, y0, rows, legend):
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch in legend:
                px(im, x0 + i, y0 + j, legend[ch])


def outline(im, col, diag=False):
    """Add a 1px outline around opaque pixels (on transparent neighbours)."""
    src = im.copy()
    nb = [(1, 0), (-1, 0), (0, 1), (0, -1)]
    if diag:
        nb += [(1, 1), (-1, -1), (1, -1), (-1, 1)]
    for y in range(im.height):
        for x in range(im.width):
            if src.getpixel((x, y))[3] == 0:
                for dx, dy in nb:
                    xx, yy = x + dx, y + dy
                    if 0 <= xx < im.width and 0 <= yy < im.height and src.getpixel((xx, yy))[3] > 0:
                        im.putpixel((x, y), col)
                        break
    return im


def save(im, rel):
    im.save(out(rel))
