"""Development preview of the city plan (requires Pillow; not used at runtime)."""
import numpy as np

from .terrain import WORLD_MIN, WORLD_SIZE, SEA_LEVEL


def render_preview(plan, path, px_per_m=0.5, layers=("terrain", "blocks", "roads", "buildings", "lanes")):
    from PIL import Image, ImageDraw

    size = int(WORLD_SIZE * px_per_m)

    def tp(p):
        return ((p[0] - WORLD_MIN) * px_per_m, (p[1] - WORLD_MIN) * px_per_m)

    T = plan["terrain"]
    h = T.h
    # terrain shading
    img = np.zeros((h.shape[0], h.shape[1], 3), dtype=np.float32)
    land = h >= SEA_LEVEL
    hn = np.clip(h / 140.0, 0, 1)
    img[..., 0] = np.where(land, 0.55 + 0.25 * hn, 0.15)
    img[..., 1] = np.where(land, 0.60 + 0.1 * hn, 0.35 + np.clip(h, -10, 0) * 0.015)
    img[..., 2] = np.where(land, 0.42, 0.55 + np.clip(h, -10, 0) * 0.02)
    gy, gx = np.gradient(h)
    shade = np.clip(1.0 - (gx - gy) * 0.08, 0.6, 1.3)
    img = np.clip(img * shade[..., None], 0, 1)
    base = Image.fromarray((img * 255).astype(np.uint8)).resize((size, size), Image.BILINEAR)
    d = ImageDraw.Draw(base, "RGBA")
    if "blocks" in layers:
        for b in plan.get("blocks", []):
            col = (230, 225, 215, 255) if b["kind"] == "block" else (120, 170, 100, 180)
            d.polygon([tp(p) for p in b["poly"]], fill=col)
    if "buildings" in layers:
        for bd in plan.get("buildings", []):
            hgt = bd["height"]
            c = int(max(60, 200 - hgt * 0.6))
            d.polygon([tp(p) for p in bd["footprint"]], fill=(c, c, c + 15, 255))
    net = plan["net"]
    if "roads" in layers:
        for e in net.edges:
            pts, ys, t0, t1 = net.edge_trimmed(e)
            col = (60, 60, 66, 255)
            if e.level == "elevated":
                col = (200, 120, 40, 230) if e.rtype != "ramp" else (220, 170, 60, 230)
            w = max(1, int(e.hw * 2 * px_per_m))
            d.line([tp(p) for p in pts], fill=col, width=w)
            if any(e.bridge):
                bp = [tp(p) for p, b in zip(e.pts, e.bridge) if b]
                if len(bp) > 1:
                    d.line(bp, fill=(140, 90, 200, 255), width=max(1, w // 2))
            if any(e.tunnel):
                tp_ = [tp(p) for p, b in zip(e.pts, e.tunnel) if b]
                if len(tp_) > 1:
                    d.line(tp_, fill=(0, 0, 0, 255), width=max(1, w // 2))
        for n in net.nodes:
            if n.poly and len(n.poly) >= 3:
                col = (70, 70, 76, 255) if n.level == "surface" else (210, 130, 50, 230)
                d.polygon([tp(p) for p in n.poly], fill=col)
            if n.control == "signal":
                x, y = tp(n.pos)
                d.ellipse([x - 2, y - 2, x + 2, y + 2], fill=(255, 40, 40, 255))
    if "lanes" in layers:
        for l in net.lanes:
            pts = [tp((p[0], p[2])) for p in l["pts"]]
            col = (255, 255, 0, 110) if l["kind"] == "lane" else (0, 255, 255, 90)
            if len(pts) > 1:
                d.line(pts, fill=col, width=1)
    if "ped" in layers:
        for pe in net.ped_edges:
            pts = [tp((p[0], p[2])) for p in pe["path"]]
            col = (255, 120, 200, 200) if pe["kind"] == "crosswalk" else (255, 255, 255, 120)
            d.line(pts, fill=col, width=1)
    base.save(path)
    return path
