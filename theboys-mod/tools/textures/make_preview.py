"""Builds docs/texture_preview.png contact sheet (+ flat front-view figures of the skins)."""
import os, glob
from PIL import Image, ImageDraw, ImageFont
from common import ROOT

TEX = os.path.join(ROOT, "textures")
DOCS = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "docs")
W = 1500


def figure(skin):
    fig = Image.new("RGBA", (16, 32), (0, 0, 0, 0))

    def part(x, y, w, h, dx, dy, ovx, ovy):
        fig.alpha_composite(skin.crop((x, y, x + w, y + h)), (dx, dy))
        fig.alpha_composite(skin.crop((ovx, ovy, ovx + w, ovy + h)), (dx, dy))
    part(4, 4 + 0, 0, 0, 0, 0, 0, 0) if False else None
    part(8, 8, 8, 8, 4, 0, 40, 8)
    part(20, 20, 8, 12, 4, 8, 20, 36)
    part(44, 20, 4, 12, 0, 8, 44, 36)
    part(36, 52, 4, 12, 12, 8, 52, 52)
    part(4, 20, 4, 12, 4, 20, 4, 36)
    part(20, 52, 4, 12, 8, 20, 4, 52)
    return fig


def main():
    font = ImageFont.load_default()
    cells = []
    order = ["entity/suit", "entity", "item", "block", "gui/power"]
    seen = set()
    for sub in order:
        for p in sorted(glob.glob(os.path.join(TEX, sub, "*.png"))):
            if p in seen:
                continue
            seen.add(p)
            im = Image.open(p).convert("RGBA")
            rel = os.path.relpath(p, TEX)
            s = {16: 8, 32: 4, 64: 4}.get(im.width, 4)
            if im.width == 64 and im.height == 32:
                s = 4
            cells.append((rel, im, s))
            if sub == "entity/suit":
                cells.append((rel + " (front)", figure(im), 8))
    icon = Image.open(os.path.join(ROOT, "icon.png")).convert("RGBA")
    cells.append(("icon.png", icon, 2))
    # layout
    rows, cur, curw = [], [], 10
    for c in cells:
        cw = max(c[1].width * c[2], len(c[0]) * 6) + 14
        if curw + cw > W and cur:
            rows.append(cur); cur, curw = [], 10
        cur.append((c, cw)); curw += cw
    rows.append(cur)
    H = 10 + sum(max(c[1].height * c[2] for c, _ in r) + 26 for r in rows)
    sheet = Image.new("RGBA", (W, H), (38, 40, 46, 255))
    d = ImageDraw.Draw(sheet)
    y = 10
    for r in rows:
        x = 10
        rh = max(c[1].height * c[2] for c, _ in r)
        for (label, im, s), cw in r:
            big = im.resize((im.width * s, im.height * s), Image.NEAREST)
            chk = Image.new("RGBA", big.size)
            cd = ImageDraw.Draw(chk)
            for yy in range(0, big.height, 8):
                for xx in range(0, big.width, 8):
                    v = 70 if (xx // 8 + yy // 8) % 2 else 84
                    cd.rectangle((xx, yy, xx + 7, yy + 7), fill=(v, v, v + 4, 255))
            chk.alpha_composite(big)
            d.text((x, y), label, fill=(230, 230, 235, 255), font=font)
            sheet.paste(chk, (x, y + 14))
            x += cw
        y += rh + 26
    os.makedirs(DOCS, exist_ok=True)
    sheet.convert("RGB").save(os.path.join(DOCS, "texture_preview.png"))
    print(sheet.size)


if __name__ == "__main__":
    main()
