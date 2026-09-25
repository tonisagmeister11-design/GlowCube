"""City plan orchestration: builds the complete plan and writes JSON for Godot + Blender."""
import json
import math
import os
import time

import numpy as np

from . import geom as G
from .blocks import BlockBuilder, PAD_H
from .districts import DISTRICTS, district_at
from .layout import build_layout
from .pois import PoiBuilder
from .props import PropPlacer
from .roads import Network, ROAD_TYPES
from .special_areas import AreaBuilder
from .terrain import Terrain, SEA_LEVEL, WORLD_MIN, WORLD_SIZE

SEED = 20260925
CHUNK = 200.0
PLAN_VERSION = 3


def chunk_key(x, z):
    cx = int(math.floor((x - WORLD_MIN) / CHUNK))
    cz = int(math.floor((z - WORLD_MIN) / CHUNK))
    n = int(WORLD_SIZE / CHUNK)
    return max(0, min(n - 1, cx)), max(0, min(n - 1, cz))


def build_plan(verbose=True):
    t0 = time.time()
    log = (lambda *a: print("[plan]", *a, f"({time.time() - t0:.1f}s)")) if verbose else (lambda *a: None)
    rng = G.Rng(SEED)
    T = Terrain(4.0)
    log("terrain", T.n)
    net = Network(T)
    build_layout(net, T)
    net.build()
    net.compute_intersections()
    log("roads", len(net.nodes), "nodes", len(net.edges), "edges")

    # ---------------------------------------------------------------- carve terrain
    for e in net.edges:
        pts = e.pts
        if e.level == "surface":
            w = e.hw + max(e.walk, 1.0) + 1.0
            for i in range(len(pts) - 1):
                if e.bridge[i] and e.bridge[i + 1]:
                    continue
                T.carve_segment(pts[i], pts[i + 1], e.ys[i], e.ys[i + 1], w, 10.0, "both")
        else:
            w = e.hw + 1.5
            for i in range(len(pts) - 1):
                if e.tunnel[i] and e.tunnel[i + 1]:
                    continue
                T.carve_segment(pts[i], pts[i + 1], e.ys[i], e.ys[i + 1], w, 14.0, "cut")
    # tunnel portals: remove terrain cells just inside each tunnel mouth
    tunnels = []
    for e in net.edges:
        if not any(e.tunnel):
            continue
        i = 0
        n = len(e.pts)
        while i < n:
            if e.tunnel[i]:
                j = i
                while j < n and e.tunnel[j]:
                    j += 1
                seg = list(range(max(0, i - 1), min(n, j + 1)))
                tunnels.append(dict(edge=e.id, pts=[(e.pts[k][0], e.ys[k], e.pts[k][1]) for k in seg],
                                    hw=e.hw))
                # holes around both portals (from open cut to a few cells inside)
                for k in (i - 1, i, j - 1, j):
                    if 0 <= k < n - 1:
                        T.cut_hole_segment(e.pts[k], e.pts[k + 1], e.hw + 2.0)
                i = j
            else:
                i += 1
    log("carved; tunnels", len(tunnels))

    faces = net.find_faces()
    net.build_lanes()
    net.build_signals(rng)
    net.build_ped_graph()
    net.build_parking_bays(rng)
    log("lanes", len(net.lanes), "ped", len(net.ped_nodes), "bays", len(net.parking))

    # elevated deck footprints (for lot exclusion and pillars)
    elev = []
    for e in net.edges:
        if e.level != "elevated":
            continue
        for i in range(len(e.pts) - 1):
            if e.bridge[i] or e.bridge[i + 1]:
                elev.append((e.pts[i], e.pts[i + 1], e.hw))
    bb = BlockBuilder(net, T, rng, elev)
    bb.classify(faces)
    bb.generate()
    log("blocks", len(bb.blocks), "lots", len(bb.lots), "buildings", len(bb.buildings))

    ab = AreaBuilder(bb, net, T, rng)
    ab.harbor()
    ab.airport()
    ab.beach()
    ab.marina()
    ab.parks()
    ab.hills()
    log("areas", len(ab.areas), "buildings", len(bb.buildings))

    pp = PropPlacer(net, T, rng, bb, ab.areas)
    pp.roads()
    pp.lots()
    park_paths = []
    for b in bb.blocks:
        if b["kind"] == "park":
            park_paths.extend(pp.park_block(b))
    pp.nature()
    for (t, x, y, z, r, s) in ab.static_props:
        pp.add(t, x, y, z, r, s if isinstance(s, int) else 0)
    log("props", len(pp.props), "signals", len(pp.signals))

    pois = PoiBuilder(bb.buildings, rng).build()
    log("pois", len(pois))

    return dict(terrain=T, net=net, bb=bb, ab=ab, pp=pp, pois=pois, tunnels=tunnels,
                park_paths=park_paths, faces=faces)


# ====================================================================== export
def _r(v, n=2):
    return round(float(v), n)


def export(plan, game_data_dir, blender_build_dir):
    T = plan["terrain"]
    net = plan["net"]
    bb = plan["bb"]
    ab = plan["ab"]
    pp = plan["pp"]
    pois = plan["pois"]
    os.makedirs(game_data_dir, exist_ok=True)
    os.makedirs(blender_build_dir, exist_ok=True)

    # ------------------------------------------------------------ roads.json (AI)
    nodes = []
    for n in net.nodes:
        nodes.append(dict(id=n.id, p=[_r(n.pos[0]), _r(n.y), _r(n.pos[1])], k=n.kind, c=n.control,
                          lv=n.level, ph={str(k): v for k, v in n.phases.items()}, off=n.offset,
                          e=n.edges, r=_r(n.radius)))
    edges = []
    for e in net.edges:
        pts, ys, t0, t1 = net.edge_trimmed(e)
        edges.append(dict(id=e.id, a=e.a, b=e.b, t=e.rtype, ow=e.oneway, hw=_r(e.hw), lv=e.level,
                          name=e.name, sp=ROAD_TYPES[e.rtype]["speed"], walk=ROAD_TYPES[e.rtype]["walk"],
                          pts=[[_r(p[0]), _r(y), _r(p[1])] for p, y in zip(e.pts, e.ys)],
                          lf=e.lanes_f, lb=e.lanes_b, park=e.parking,
                          br=any(e.bridge), tu=any(e.tunnel)))
    lanes = []
    for l in net.lanes:
        rec = dict(id=l["id"], k=0 if l["kind"] == "lane" else 1, pts=[list(p) for p in l["pts"]],
                   sp=_r(l["speed"], 1), nx=l["next"], l=l.get("left", -1), r=l.get("right", -1),
                   e=l["edge"], fn=l["from_node"], tn=l["to_node"], i=l["index"], n=l["count"])
        if l["kind"] == "connector":
            rec["nd"] = l["node"]
            rec["tr"] = {"straight": 0, "left": 1, "right": 2}[l["turn"]]
            rec["ie"] = l["in_edge"]
        lanes.append(rec)
    roads = dict(version=PLAN_VERSION, nodes=nodes, edges=edges, lanes=lanes, signals=pp.signals)
    _write(os.path.join(game_data_dir, "roads.json"), roads)

    # ------------------------------------------------------------ peds.json
    peds = dict(nodes=[dict(id=p["id"], p=list(p["pos"]), nd=p["node"]) for p in net.ped_nodes],
                edges=[dict(a=e["a"], b=e["b"], k=0 if e["kind"] == "walk" else 1,
                            path=[list(q) for q in e["path"]], nd=e.get("node", -1),
                            ph=e.get("phase", -1), e=e["edge"]) for e in net.ped_edges],
                park_paths=[[[_r(p[0]), _r(p[1])] for p in pa] for pa in plan["park_paths"]])
    _write(os.path.join(game_data_dir, "peds.json"), peds)

    # ------------------------------------------------------------ map.json (UI map)
    map_roads = []
    for e in net.edges:
        pts, ys, t0, t1 = net.edge_trimmed(e)
        map_roads.append(dict(t=e.rtype, lv=e.level, w=_r(e.hw * 2, 1),
                              pts=[[_r(p[0], 1), _r(p[1], 1)] for p in e.pts], name=e.name,
                              tu=any(e.tunnel)))
    map_nodes = [dict(poly=[[_r(p[0], 1), _r(p[1], 1)] for p in n.poly], lv=n.level)
                 for n in net.nodes if n.poly and len(n.poly) >= 3]
    map_blocks = [dict(k=b["kind"], d=b["district"], sp=b.get("special", ""),
                       poly=[[_r(p[0], 1), _r(p[1], 1)] for p in b["poly"]]) for b in bb.blocks]
    map_bld = [dict(fp=[[_r(p[0], 1), _r(p[1], 1)] for p in b["footprint"]], h=_r(b["height"], 0),
                    u=b.get("usage", "generic")) for b in bb.buildings]
    map_areas = []
    for a in ab.areas:
        rec = dict(k=a["kind"])
        for key in ("rect", "poly", "pts", "center", "radii", "width"):
            if key in a:
                v = a[key]
                if key in ("poly", "pts"):
                    v = [[_r(p[0], 1), _r(p[1], 1)] for p in v]
                rec[key] = v
        map_areas.append(rec)
    # coastline polyline (for map water rendering)
    from .terrain import coast_z
    coast = [[_r(x, 1), _r(coast_z(x), 1)] for x in np.arange(WORLD_MIN, WORLD_MIN + WORLD_SIZE + 1, 20.0)]
    mp = dict(world=dict(min=WORLD_MIN, size=WORLD_SIZE), roads=map_roads, junctions=map_nodes,
              blocks=map_blocks, buildings=map_bld, areas=map_areas, coast=coast,
              districts=[dict(id=d["id"], name=d["name"], rect=d["rect"]) for d in DISTRICTS])
    _write(os.path.join(game_data_dir, "map.json"), mp)

    # ------------------------------------------------------------ world.json
    parking = list(net.parking) + list(pp.lot_parking)
    for p in parking:
        p["pos"] = [_r(v) for v in p["pos"]]
        p["dir"] = [_r(v, 3) for v in p["dir"]]
    safehouse = next((p for p in pois if p["type"] == "safehouse"), None)
    spawn = safehouse["entrance"] if safehouse else (-530.0, 0.2, -60.0)
    chunks = sorted(set(chunk_key(*G.poly_centroid(b["footprint"])) for b in bb.buildings)
                    | set(chunk_key(p[1], p[3]) for p in pp.props)
                    | set(_land_chunks(T)))
    world = dict(version=PLAN_VERSION, seed=SEED, world_min=WORLD_MIN, world_size=WORLD_SIZE, chunk=CHUNK,
                 sea_level=SEA_LEVEL, spawn=list(spawn), chunks=[list(c) for c in chunks],
                 pois=pois, parking=parking,
                 buildings=[dict(id=b["id"], fp=b["footprint"], y=b["y"], h=b["height"], s=b["style"],
                                 u=b.get("usage", "generic"), n=b.get("name", ""), d=b["district"],
                                 f=b["front"], i=b.get("interior", "")) for b in bb.buildings],
                 tunnels=plan["tunnels"],
                 districts=[dict(id=d["id"], name=d["name"], rect=d["rect"], ped=d["ped"], traffic=d["traffic"])
                            for d in DISTRICTS])
    _write(os.path.join(game_data_dir, "world.json"), world)

    # ------------------------------------------------------------ props per chunk (Godot MultiMesh)
    by_chunk = {}
    for (t, x, y, z, r, v) in pp.props:
        k = chunk_key(x, z)
        by_chunk.setdefault(f"{k[0]}_{k[1]}", []).append([t, x, y, z, r, v])
    _write(os.path.join(game_data_dir, "props.json"), by_chunk)

    # ------------------------------------------------------------ full plan for Blender
    full = dict(
        version=PLAN_VERSION, chunk=CHUNK, world_min=WORLD_MIN, world_size=WORLD_SIZE, sea_level=SEA_LEVEL,
        terrain_res=T.res, terrain_n=T.n,
        nodes=[dict(id=n.id, pos=list(n.pos), y=n.y, kind=n.kind, control=n.control, level=n.level,
                    poly=[list(p) for p in (n.poly or [])], sorted=n.sorted,
                    trim={str(k): v for k, v in n.trim.items()}, radius=n.radius,
                    dirs={str(k): list(v) for k, v in n.dirs.items()},
                    corners=[[list(p) for p in c] for c in n.corners]) for n in net.nodes],
        edges=[_edge_full(net, e) for e in net.edges],
        blocks=bb.blocks, lots=bb.lots, buildings=bb.buildings, areas=ab.areas,
        pois=pois, tunnels=plan["tunnels"], park_paths=plan["park_paths"],
        crosswalks=[e for e in net.ped_edges if e["kind"] == "crosswalk"],
        parking=parking, props=pp.props, signals=pp.signals,
        chunks=[list(c) for c in chunks],
    )
    _write(os.path.join(blender_build_dir, "plan_full.json"), full, indent=None)
    np.save(os.path.join(blender_build_dir, "terrain_h.npy"), T.h.astype(np.float32))
    np.save(os.path.join(blender_build_dir, "terrain_natural.npy"), T.natural.astype(np.float32))
    np.save(os.path.join(blender_build_dir, "terrain_roadmask.npy"), T.road_mask.astype(np.float32))
    np.save(os.path.join(blender_build_dir, "terrain_holes.npy"), T.holes)
    return dict(chunks=len(chunks))


def _edge_full(net, e):
    pts, ys, t0, t1 = net.edge_trimmed(e)
    return dict(id=e.id, a=e.a, b=e.b, rtype=e.rtype, oneway=e.oneway, parking=e.parking, level=e.level,
                name=e.name, hw=e.hw, walk=ROAD_TYPES[e.rtype]["walk"], median=ROAD_TYPES[e.rtype]["median"],
                lanes=ROAD_TYPES[e.rtype]["lanes"], lw=ROAD_TYPES[e.rtype]["lw"],
                shoulder=ROAD_TYPES[e.rtype]["shoulder"],
                pts=[list(p) for p in e.pts], ys=list(e.ys), bridge=list(e.bridge), tunnel=list(e.tunnel),
                trim_pts=[list(p) for p in pts], trim_ys=list(ys), t0=t0, t1=t1)


def _land_chunks(T):
    out = []
    n = int(WORLD_SIZE / CHUNK)
    step = int(CHUNK / T.res)
    for cz in range(n):
        for cx in range(n):
            sub = T.natural[cz * step:(cz + 1) * step + 1, cx * step:(cx + 1) * step + 1]
            if (sub > SEA_LEVEL - 4.0).any():
                out.append((cx, cz))
    return out


def _write(path, data, indent=None):
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, separators=(",", ":"), indent=indent)
