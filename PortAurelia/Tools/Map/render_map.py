"""Renders the in-game map texture (full map screen + radar) from the city plan.

Input : Blender/build/plan_full.json + terrain_h.npy (written by the city plan step)
Output: Game/assets/ui/generated/map.png (4096 px = 3200 m, 0.78 m per pixel)

Style: dark water, warm land with hillshade, green parks/fields, light city blocks,
building footprints, roads by class (highways orange, avenues light, streets white).
Run with the pipeline python:  Tools/Blender/venv/bin/python Tools/Map/render_map.py
"""
import json
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
BUILD = os.path.join(ROOT, "Blender", "build")
OUT_DIR = os.path.join(ROOT, "Game", "assets", "ui", "generated")
SIZE = int(sys.argv[1]) if len(sys.argv) > 1 else 4096


def main():
    plan = json.load(open(os.path.join(BUILD, "plan_full.json")))
    wmin, wsize = plan["world_min"], plan["world_size"]
    sea = plan["sea_level"]
    s = SIZE / wsize

    def tp(p):
        return ((p[0] - wmin) * s, (p[1] - wmin) * s)

    # ---- terrain base with hillshade
    h = np.load(os.path.join(BUILD, "terrain_h.npy")).astype(np.float32)
    land = h >= sea + 0.05
    depth = np.clip((sea - h) / 12.0, 0, 1)
    hn = np.clip(h / 110.0, 0, 1)
    img = np.zeros(h.shape + (3,), np.float32)
    land_col = np.array([0.80, 0.78, 0.70]) * (1 - hn[..., None] * 0.25) + np.array([0.55, 0.62, 0.45]) * hn[..., None] * 0.25
    water_col = np.array([0.33, 0.55, 0.66]) * (1 - depth[..., None]) + np.array([0.12, 0.26, 0.38]) * depth[..., None]
    img[:] = np.where(land[..., None], land_col, water_col)
    gz, gx = np.gradient(h)
    shade = np.clip(1.0 + (-gx + gz) * 0.06, 0.7, 1.25)
    img = np.clip(img * np.where(land, shade, 1.0)[..., None], 0, 1)
    base = Image.fromarray((img * 255).astype(np.uint8)).resize((SIZE, SIZE), Image.BICUBIC)
    base = base.convert("RGBA")
    d = ImageDraw.Draw(base, "RGBA")

    # ---- areas (fields, docks, airport)
    area_cols = {"field": (150, 176, 110, 255), "harbor_yard": (176, 170, 160, 255), "dock": (170, 164, 150, 255),
                 "taxiway": (120, 120, 124, 255), "runway": (90, 90, 96, 255), "apron": (150, 150, 152, 255),
                 "parking_area": (165, 165, 168, 255), "lake": (70, 120, 150, 255), "basin": (60, 110, 140, 255),
                 "boardwalk": (170, 140, 100, 255), "pier": (160, 130, 95, 255), "quay": (170, 164, 150, 255)}
    for a in plan["areas"]:
        col = area_cols.get(a["kind"])
        if col is None:
            continue
        if "rect" in a:
            x0, z0, x1, z1 = a["rect"]
            d.rectangle([tp((x0, z0)), tp((x1, z1))], fill=col)
        elif "poly" in a:
            d.polygon([tp(p) for p in a["poly"]], fill=col)
    # ---- blocks and lots
    for b in plan["blocks"]:
        if b["kind"] == "block":
            d.polygon([tp(p) for p in b["poly"]], fill=(222, 218, 208, 255))
        elif b["kind"] == "natural":
            d.polygon([tp(p) for p in b["poly"]], fill=(160, 184, 128, 255))
    lot_cols = {"pocket_park": (150, 192, 120, 255), "park_small": (150, 192, 120, 255), "plaza": (205, 200, 190, 255),
                "parking_lot": (190, 190, 192, 255), "house_yard": (205, 214, 180, 255), "yard": (214, 212, 200, 255),
                "yard_industrial": (196, 192, 184, 255), "construction": (200, 180, 140, 255), "landmark": (214, 206, 190, 255)}
    for lt in plan["lots"]:
        col = lot_cols.get(lt["kind"])
        if col:
            d.polygon([tp(p) for p in lt["poly"]], fill=col)
    # ---- buildings (darker with height)
    for bd in plan["buildings"]:
        hgt = bd["height"]
        c = int(np.clip(190 - hgt * 0.55, 120, 190))
        d.polygon([tp(p) for p in bd["footprint"]], fill=(c, c - 2, c + 6, 255), outline=(c - 25, c - 25, c - 18, 255))
    # ---- roads
    nodes = {n["id"]: n for n in plan["nodes"]}

    def road_style(e):
        if e["level"] == "elevated":
            return (236, 170, 60, 255) if e["rtype"] != "ramp" else (240, 196, 110, 255), (150, 100, 30, 255)
        if e["rtype"] in ("avenue", "boulevard"):
            return (250, 246, 232, 255), (150, 146, 136, 255)
        return (255, 255, 255, 255), (160, 156, 150, 255)

    edges = sorted(plan["edges"], key=lambda e: 1 if e["level"] == "elevated" else 0)
    for pass_ in (0, 1):
        for e in edges:
            fill, edge_col = road_style(e)
            w = max(2.0, e["hw"] * 2 * s)
            pts = [tp(p) for p in e["pts"]]
            if pass_ == 0:
                d.line(pts, fill=edge_col, width=int(w + 2), joint="curve")
            else:
                d.line(pts, fill=fill, width=int(w), joint="curve")
        if pass_ == 1:
            for n in plan["nodes"]:
                if n.get("poly") and len(n["poly"]) >= 3 and n["level"] == "surface":
                    d.polygon([tp(p) for p in n["poly"]], fill=(255, 255, 255, 255))
    # tunnels dashed dark
    for t in plan["tunnels"]:
        pts = [tp((p[0], p[2])) for p in t["pts"]]
        for i in range(0, len(pts) - 1, 2):
            d.line([pts[i], pts[i + 1]], fill=(90, 90, 96, 255), width=4)
    base = base.filter(ImageFilter.SMOOTH)
    os.makedirs(OUT_DIR, exist_ok=True)
    out = os.path.join(OUT_DIR, "map.png")
    base.convert("RGB").save(out, optimize=True)
    print("[map] wrote", out, SIZE, "px")


if __name__ == "__main__":
    main()
