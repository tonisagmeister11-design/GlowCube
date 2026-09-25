"""Build all Port Aurelia world chunks as GLB files.

Pipeline:  city plan (citygen) -> per-chunk geometry (terrain, roads, blocks,
buildings with LODs, collision) -> Game/assets/generated/city/chunk_X_Z.glb
plus city_far.glb (one low-detail node per chunk for distant rendering).

Usage (from the project root):
    python Blender/scripts/generate_city.py -- [--plan] [--chunks 8_9,9_9] [--far-only]
    blender -b -P Blender/scripts/generate_city.py -- --plan
"""
import json
import math
import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import numpy as np  # noqa: E402

from _common import (G, GEN, BUILD, GAME, MeshBuilder, reset_scene, clear_objects, export_glb, script_args,  # noqa: E402
                     box, prism, cap_polygon, add_signs)
import generate_roads as R  # noqa: E402
import generate_buildings as B  # noqa: E402
import generate_towers  # noqa: E402,F401
from generate_lods import build_far as build_far_hlod, chunk_center  # noqa: E402
from generate_colliders import new_collision_builders, collision_objects  # noqa: E402

import bpy  # noqa: E402

OUT = os.path.join(GEN, "city")
PIECE = 90.0


# ====================================================================== data
class Plan:
    def __init__(self):
        with open(os.path.join(BUILD, "plan_full.json"), "r", encoding="utf-8") as f:
            self.d = json.load(f)
        self.h = np.load(os.path.join(BUILD, "terrain_h.npy"))
        self.nat = np.load(os.path.join(BUILD, "terrain_natural.npy"))
        self.holes = np.load(os.path.join(BUILD, "terrain_holes.npy"))
        self.res = self.d["terrain_res"]
        self.n = self.d["terrain_n"]
        self.wmin = self.d["world_min"]
        self.chunk = self.d["chunk"]
        self.sea = self.d["sea_level"]

    def height(self, p):
        fx = (p[0] - self.wmin) / self.res
        fz = (p[1] - self.wmin) / self.res
        ix = min(max(int(math.floor(fx)), 0), self.n - 2)
        iz = min(max(int(math.floor(fz)), 0), self.n - 2)
        tx = min(max(fx - ix, 0.0), 1.0)
        tz = min(max(fz - iz, 0.0), 1.0)
        a, b = self.h[iz, ix], self.h[iz, ix + 1]
        c, d = self.h[iz + 1, ix], self.h[iz + 1, ix + 1]
        return float((a * (1 - tx) + b * tx) * (1 - tz) + (c * (1 - tx) + d * tx) * tz)

    def chunk_of(self, x, z):
        cx = int(math.floor((x - self.wmin) / self.chunk))
        cz = int(math.floor((z - self.wmin) / self.chunk))
        n = int(self.d["world_size"] / self.chunk)
        return (max(0, min(n - 1, cx)), max(0, min(n - 1, cz)))


def district_at(x, z):
    from citygen.districts import district_at as da
    return da(x, z)


# ====================================================================== pieces
def split_edge(plan, e):
    """Split an edge's trimmed polyline into chunk-sized pieces."""
    pts = [tuple(p) for p in e["trim_pts"]]
    ys = list(e["trim_ys"])
    L = G.polyline_length(pts)
    if L <= PIECE * 1.5:
        return [(e, True, True)]
    n = int(math.ceil(L / PIECE))
    out = []
    cum = [0.0]
    for i in range(1, len(pts)):
        cum.append(cum[-1] + G.dist(pts[i - 1], pts[i]))
    for k in range(n):
        s0 = L * k / n
        s1 = L * (k + 1) / n
        sub = [G.polyline_point_at(pts, s0)[0]]
        sy = [R._y_along(pts, ys, s0)]
        for i in range(1, len(pts) - 1):
            if s0 < cum[i] < s1:
                sub.append(pts[i])
                sy.append(ys[i])
        sub.append(G.polyline_point_at(pts, s1)[0])
        sy.append(R._y_along(pts, ys, s1))
        pe = dict(e)
        pe["trim_pts"] = [list(p) for p in sub]
        pe["trim_ys"] = sy
        out.append((pe, k == 0, k == n - 1))
    return out


# ====================================================================== terrain
def build_pad_mask(plan):
    """Terrain cells fully covered by block pads (skip them to save overdraw)."""
    n = plan.n - 1
    mask = np.zeros((n, n), dtype=bool)
    res, wmin = plan.res, plan.wmin
    for b in plan.d["blocks"]:
        if b["kind"] in ("natural", "water", "park", "canal"):
            continue
        poly = [tuple(p) for p in b["poly"]]
        x0, z0, x1, z1 = G.poly_bbox(poly)
        i0, i1 = int((x0 - wmin) / res), int((x1 - wmin) / res) + 1
        j0, j1 = int((z0 - wmin) / res), int((z1 - wmin) / res) + 1
        inside = {}
        for j in range(j0, j1 + 2):
            for i in range(i0, i1 + 2):
                inside[(i, j)] = G.point_in_poly((wmin + i * res, wmin + j * res), poly)
        for j in range(j0, j1 + 1):
            for i in range(i0, i1 + 1):
                if 0 <= i < n and 0 <= j < n and inside[(i, j)] and inside[(i + 1, j)] and inside[(i, j + 1)] and inside[(i + 1, j + 1)]:
                    mask[j, i] = True
    return mask


def splat_weights(plan, x, z, h, slope, did):
    grass, dirt, sand, rock = 1.0, 0.0, 0.0, 0.0
    if did == "beach" or (h < plan.sea + 1.4 and did not in ("canal",)):
        sand = 1.0
        grass = 0.0
    if did in ("hills", "luxury", "rural", "rural_east"):
        n = G.fbm(x * 0.006, z * 0.006, 3, 7)
        dirt = G.clamp((n - 0.42) * 3.0, 0.0, 1.0)
        grass = 1.0 - dirt * 0.8
        if did in ("rural", "rural_east"):
            dirt = max(dirt, 0.55)
    if did == "airport":
        dirt = 0.25
    if slope > 0.55:
        rock = G.clamp((slope - 0.55) * 2.5, 0.0, 1.0)
    s = grass + dirt + sand + rock
    return (grass / s, dirt / s, sand / s, rock / s)


def build_terrain(plan, cx, cz, pad_mask, step, mb, mb_col):
    res = plan.res
    cells = int(plan.chunk / res)
    i_base = cx * cells
    j_base = cz * cells
    st = step
    wmin = plan.wmin
    from citygen.terrain import CANAL_X, canal_widths, CANAL_Z_START
    cache = {}

    def vert(i, j):
        k = (i, j)
        v = cache.get(k)
        if v is None:
            ii = min(i, plan.n - 1)
            jj = min(j, plan.n - 1)
            x = wmin + ii * res
            z = wmin + jj * res
            h = float(plan.h[jj, ii])
            hx = float(plan.h[jj, min(ii + 1, plan.n - 1)] - plan.h[jj, max(ii - 1, 0)]) / (2 * res)
            hz = float(plan.h[min(jj + 1, plan.n - 1), ii] - plan.h[max(jj - 1, 0), ii]) / (2 * res)
            slope = math.hypot(hx, hz)
            did = district_at(x, z)
            w = splat_weights(plan, x, z, h, slope, did)
            v = ((x, h, z), w)
            cache[k] = v
        return v

    for jj in range(0, cells, st):
        for ii in range(0, cells, st):
            i = i_base + ii
            j = j_base + jj
            if i >= plan.n - 1 or j >= plan.n - 1:
                continue
            if st == 1:
                if plan.holes[j, i] or pad_mask[j, i]:
                    continue
            else:
                sub_h = plan.holes[j:j + st, i:i + st]
                sub_p = pad_mask[j:j + st, i:i + st]
                if sub_p.all():
                    continue
                if sub_h.any():
                    continue
            a, b = vert(i, j), vert(i + st, j)
            c, d = vert(i + st, j + st), vert(i, j + st)
            pts = [a[0], b[0], c[0], d[0]]
            if max(p[1] for p in pts) < plan.sea - 9.0:
                continue
            cxm = (a[0][0] + c[0][0]) * 0.5
            czm = (a[0][2] + c[0][2]) * 0.5
            fw, tw = canal_widths(czm)
            mat = "terrain"
            surf = "grass"
            if abs(cxm - CANAL_X) < tw + 1.0 and czm > CANAL_Z_START and min(p[1] for p in pts) < -0.2:
                mat = "concrete"
                surf = "concrete"
            elif cxm > 330 and czm > 640 and min(p[1] for p in pts) > -1.0:
                mat = "concrete"
                surf = "concrete"
            elif a[1][2] > 0.5:
                surf = "sand"
            cols = [(v[1][0], v[1][1], v[1][2], v[1][3]) for v in (a, b, c, d)]
            uvs = [(p[0], p[2]) for p in pts]
            base = len(mb.verts)
            mb.verts.extend(pts)
            mb.faces.append([base, base + 1, base + 2, base + 3])
            mb.uvs.append(uvs)
            mb.uv2.append(None)
            mb.cols.append(cols)
            mb.fmat.append(mb.mat(mat))
            # winding: up-facing
            n = __import__("_common").newell(pts)
            if n[1] < 0:
                mb.faces[-1].reverse()
                mb.uvs[-1].reverse()
                mb.cols[-1].reverse()
            if mb_col is not None:
                mb_col[surf].face(pts, None, mat, up=(0, 1, 0))


# ====================================================================== chunk build
def surface_index(plan):
    idx = {}
    for e in plan.d["edges"]:
        if e["level"] != "surface":
            continue
        pts = e["pts"]
        for i in range(len(pts) - 1):
            a, b = tuple(pts[i]), tuple(pts[i + 1])
            m = G.lerp(a, b, 0.5)
            key = (int((m[0] + 1600) // 100), int((m[1] + 1600) // 100))
            idx.setdefault(key, []).append((a, b, e["hw"]))
    return idx


def build_chunk(plan, cx, cz, pad_mask, assign, far_mbs, rng):
    t0 = time.time()
    ground = MeshBuilder()
    marks = MeshBuilder()
    struct = MeshBuilder()
    bl0 = MeshBuilder()
    bl1 = MeshBuilder()
    detail = MeshBuilder()
    far = MeshBuilder()
    terr1 = MeshBuilder()
    cols = new_collision_builders()
    terrain = plan.height
    key = (cx, cz)
    build_terrain(plan, cx, cz, pad_mask, 1, ground, cols)
    build_terrain(plan, cx, cz, pad_mask, 4, far, None)
    for (e, first, last) in assign["edges"].get(key, []):
        if e["level"] == "surface":
            ee = dict(e)
            if not first or not last:
                # interior pieces: suppress crosswalks at artificial ends
                ee["_first"], ee["_last"] = first, last
            _surface_piece(plan, ee, first, last, ground, marks, struct, cols)
        else:
            R.build_elevated_edge(plan.d, e, ground, marks, struct, cols, terrain)
    for n in assign["nodes"].get(key, []):
        R.build_node(plan.d, n, ground, struct, cols)
    for b in assign["blocks"].get(key, []):
        R.build_block(plan.d, b, ground, struct, cols, detail)
    for lot in assign["lots"].get(key, []):
        R.build_lot(plan.d, lot, ground, marks, detail)
    for a in assign["areas"].get(key, []):
        R.build_area(plan.d, a, ground, marks, struct, cols, terrain)
    ctx = B.BuildingContext(bl0, bl1, detail, far, cols["building"], rng)
    for spec in assign["buildings"].get(key, []):
        try:
            B.build(ctx, spec)
        except Exception as ex:  # keep the pipeline going, report
            print(f"  ! building {spec['id']} ({spec['style']}) failed: {ex}")
    if ctx.signs:
        add_signs(detail, ctx.signs)
    # far representation of structures (decks) = include struct lightly? keep only buildings/terrain
    objs = []
    # pivot at the chunk centre: Godot measures visibility ranges (LOD) from the node origin
    center = chunk_center(plan.wmin, plan.chunk, cx, cz)
    for mb, name in ((ground, "Ground"), (marks, "Markings_LOD0"), (struct, "Structures"), (bl0, "Buildings_LOD0"),
                     (bl1, "Buildings_LOD1"), (detail, "Detail_LOD0")):
        if not mb.empty():
            objs.append(mb.to_object(name, origin=center))
    objs.extend(collision_objects(cols))
    tri = sum(m.tri_count() for m in (ground, marks, struct, bl0, detail))
    path = os.path.join(OUT, f"chunk_{cx}_{cz}.glb")
    if objs:
        export_glb(path, objs)
    clear_objects()
    far_mbs[key] = far
    print(f"  chunk {cx}_{cz}: {tri} tris LOD0, {len(assign['buildings'].get(key, []))} buildings "
          f"({time.time() - t0:.1f}s)")
    return tri


def _surface_piece(plan, e, first, last, ground, marks, struct, cols):
    nodes = plan.d["nodes"]
    if first and last:
        R.build_surface_edge(plan.d, e, ground, marks, struct, cols)
        return
    # temporarily swap node kinds at artificial piece ends
    saved = {}
    if not first:
        saved[e["a"]] = nodes[e["a"]]
        nodes[e["a"]] = dict(nodes[e["a"]], kind="joint")
    if not last:
        saved[e["b"]] = nodes[e["b"]]
        nodes[e["b"]] = dict(nodes[e["b"]], kind="joint")
    try:
        R.build_surface_edge(plan.d, e, ground, marks, struct, cols)
    finally:
        for k, v in saved.items():
            nodes[k] = v


def assign_to_chunks(plan):
    A = {k: {} for k in ("edges", "nodes", "blocks", "lots", "areas", "buildings")}
    for e in plan.d["edges"]:
        for (pe, first, last) in split_edge(plan, e):
            pts = pe["trim_pts"]
            m = G.polyline_point_at([tuple(p) for p in pts], G.polyline_length([tuple(p) for p in pts]) * 0.5)[0]
            A["edges"].setdefault(plan.chunk_of(*m), []).append((pe, first, last))
    for n in plan.d["nodes"]:
        A["nodes"].setdefault(plan.chunk_of(*n["pos"]), []).append(n)
    for b in plan.d["blocks"]:
        c = G.poly_centroid([tuple(p) for p in b["poly"]])
        A["blocks"].setdefault(plan.chunk_of(*c), []).append(b)
    for l in plan.d["lots"]:
        c = G.poly_centroid([tuple(p) for p in l["poly"]])
        A["lots"].setdefault(plan.chunk_of(*c), []).append(l)
    for a in plan.d["areas"]:
        for piece in _split_area(a):
            A["areas"].setdefault(plan.chunk_of(*_area_center(piece)), []).append(piece)
    for b in plan.d["buildings"]:
        c = G.poly_centroid([tuple(p) for p in b["footprint"]])
        A["buildings"].setdefault(plan.chunk_of(*c), []).append(b)
    return A


def _area_center(a):
    if "rect" in a:
        x0, z0, x1, z1 = a["rect"]
        return ((x0 + x1) * 0.5, (z0 + z1) * 0.5)
    if "poly" in a:
        return G.poly_centroid([tuple(p) for p in a["poly"]])
    if "pts" in a:
        return tuple(a["pts"][len(a["pts"]) // 2])
    if "center" in a:
        return tuple(a["center"])
    if a["kind"] == "quay":
        return ((a["x0"] + a["x1"]) * 0.5, a["z"])
    return (0.0, 0.0)


def _split_area(a):
    if a["kind"] in ("boardwalk",) and len(a["pts"]) > 8:
        out = []
        pts = a["pts"]
        for i in range(0, len(pts) - 1, 6):
            sub = pts[i:i + 7]
            if len(sub) >= 2:
                out.append(dict(a, pts=sub))
        return out
    if a["kind"] == "quay":
        out = []
        x = a["x0"]
        while x < a["x1"]:
            out.append(dict(a, x0=x, x1=min(a["x1"], x + 200.0)))
            x += 200.0
        return out
    if a["kind"] in ("runway", "taxiway") and "rect" in a:
        x0, z0, x1, z1 = a["rect"]
        out = []
        if z1 - z0 > 200:
            z = z0
            while z < z1:
                out.append(dict(a, rect=(x0, z, x1, min(z1, z + 200.0))))
                z += 200.0
            return out
    return [a]


def build_far(far_mbs, plan):
    build_far_hlod(far_mbs, plan.wmin, plan.chunk, os.path.join(OUT, "city_far.glb"))


def main():
    args = script_args()
    t0 = time.time()
    if "--plan" in args:
        from citygen.plan import build_plan, export
        p = build_plan()
        export(p, os.path.join(GAME, "data", "city"), BUILD)
    reset_scene()
    plan = Plan()
    plan.d["_surface_index"] = surface_index(plan)
    print("[city] pad mask ...")
    pad_mask = build_pad_mask(plan)
    assign = assign_to_chunks(plan)
    chunks = [tuple(c) for c in plan.d["chunks"]]
    if "--chunks" in args:
        sel = args[args.index("--chunks") + 1].split(",")
        chunks = [tuple(int(v) for v in s.split("_")) for s in sel]
    os.makedirs(OUT, exist_ok=True)
    rng = G.Rng(777)
    far_mbs = {}
    total = 0
    for (cx, cz) in chunks:
        total += build_chunk(plan, cx, cz, pad_mask, assign, far_mbs, rng)
    if "--chunks" not in args:
        build_far(far_mbs, plan)
    # manifest for the streaming system
    manifest = dict(chunks=[f"{cx}_{cz}" for (cx, cz) in chunks], chunk=plan.chunk, world_min=plan.wmin)
    if "--chunks" not in args:
        with open(os.path.join(OUT, "manifest.json"), "w") as f:
            json.dump(manifest, f)
    print(f"[city] done: {len(chunks)} chunks, {total} tris, {time.time() - t0:.0f}s")


if __name__ == "__main__":
    main()
