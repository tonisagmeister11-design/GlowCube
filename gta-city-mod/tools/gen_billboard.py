"""The GlowCube billboard: cuts the logo (tools/art/glowcube_logo.png) into 8 x 8 block textures and writes the
block models and the blockstate of gtacity:billboard (facing + tile). The world generator puts the billboard on
some office roofs and skyscraper fronts (Buildings.java).

    python3 tools/gen_billboard.py
"""
import json
import os

from PIL import Image, ImageEnhance

N = 8          # tiles per side (keep in sync with BillboardBlock.SIZE)
TILE = 64      # pixels per tile
HERE = os.path.dirname(__file__)
ASSETS = os.path.join(HERE, "..", "src", "main", "resources", "assets", "gtacity")
FACINGS = {"north": 0, "east": 90, "south": 180, "west": 270}


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def main():
    logo = Image.open(os.path.join(HERE, "art", "glowcube_logo.png")).convert("RGB")
    logo = logo.resize((N * TILE, N * TILE), Image.LANCZOS)
    logo = ImageEnhance.Contrast(logo).enhance(1.08)
    # A thin glowing green frame around the picture.
    px = logo.load()
    for i in range(N * TILE):
        for w in range(3):
            for (x, y) in ((i, w), (i, N * TILE - 1 - w), (w, i), (N * TILE - 1 - w, i)):
                px[x, y] = (40, 230, 60) if w < 2 else (10, 90, 20)
    out = os.path.join(ASSETS, "textures", "block", "billboard")
    os.makedirs(out, exist_ok=True)
    variants = {}
    for row in range(N):
        for col in range(N):
            tile = row * N + col
            logo.crop((col * TILE, row * TILE, (col + 1) * TILE, (row + 1) * TILE)).save(
                os.path.join(out, "tile_%d.png" % tile))
            write_json(os.path.join(ASSETS, "models", "block", "billboard_%d.json" % tile), {
                "parent": "minecraft:block/block",
                "textures": {"front": "gtacity:block/billboard/tile_%d" % tile,
                             "side": "minecraft:block/black_concrete",
                             "particle": "minecraft:block/black_concrete"},
                "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": {
                    "north": {"uv": [0, 0, 16, 16], "texture": "#front", "cullface": "north"},
                    "south": {"texture": "#side", "cullface": "south"},
                    "east": {"texture": "#side", "cullface": "east"},
                    "west": {"texture": "#side", "cullface": "west"},
                    "up": {"texture": "#side", "cullface": "up"},
                    "down": {"texture": "#side", "cullface": "down"}}}],
            })
            for facing, rot in FACINGS.items():
                v = {"model": "gtacity:block/billboard_%d" % tile}
                if rot:
                    v["y"] = rot
                variants["facing=%s,tile=%d" % (facing, tile)] = v
    write_json(os.path.join(ASSETS, "blockstates", "billboard.json"), {"variants": variants})
    print("billboard:", N * N, "tiles")


if __name__ == "__main__":
    main()
