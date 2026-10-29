#!/usr/bin/env python3
"""Baut aus OSM-Rohdaten (API-Kacheln) die Datendatei der App fuer die Insel Ugljan.
Aufruf: build_data.py <ordner-mit-osm-dateien> <ausgabe.js>
Im Ordner: t_*.osm (Kacheln von api.openstreetmap.org/api/0.6/map) und island.osm (relation/6756342/full).
"""
import glob, json, math, os, sys, time
import xml.etree.ElementTree as ET
from shapely.geometry import Polygon, Point, LineString
from shapely.ops import unary_union, linemerge, polygonize
from shapely.prepared import prep

src, out = sys.argv[1], sys.argv[2]
nodes, ways, rels = {}, {}, {}

def tags(el):
    return {t.get("k"): t.get("v") for t in el.findall("tag")}

for f in sorted(glob.glob(os.path.join(src, "*.osm"))):
    root = ET.parse(f).getroot()
    for n in root.findall("node"):
        nodes[n.get("id")] = (float(n.get("lat")), float(n.get("lon")), tags(n))
    for w in root.findall("way"):
        ways[w.get("id")] = ([nd.get("ref") for nd in w.findall("nd")], tags(w))
    for r in root.findall("relation"):
        rels[r.get("id")] = ([(m.get("type"), m.get("ref"), m.get("role")) for m in r.findall("member")], tags(r))

def way_coords(wid):
    return [(nodes[i][1], nodes[i][0]) for i in ways[wid][0] if i in nodes]

def relation_polygon(rid):
    members, _ = rels[rid]
    outer = [LineString(way_coords(r)) for t, r, role in members if t == "way" and role == "outer" and r in ways]
    return unary_union(list(polygonize(linemerge(outer))))

island = relation_polygon("6756342")
island_buf = prep(island.buffer(0.0006))
inside = lambda lat, lon: island_buf.contains(Point(lon, lat))

def rings_of(geom, tol):
    g = geom.simplify(tol, preserve_topology=True)
    polys = [g] if g.geom_type == "Polygon" else list(g.geoms)
    return [[[round(y, 5), round(x, 5)] for x, y in p.exterior.coords] for p in polys if not p.is_empty]

land = rings_of(island, 0.00004)
others = []
for wid, (nd, t) in ways.items():
    if t.get("place") in ("island", "islet") and nd and nd[0] == nd[-1]:
        c = way_coords(wid)
        if len(c) >= 4 and not island.contains(Polygon(c).representative_point()):
            others += rings_of(Polygon(c), 0.00005)

green = []
for wid, (nd, t) in ways.items():
    if (t.get("landuse") == "forest" or t.get("natural") == "wood") and nd and nd[0] == nd[-1]:
        c = way_coords(wid)
        if len(c) >= 4:
            p = Polygon(c).buffer(0)
            if p.area > 2e-7 and island.intersects(p):
                for r in rings_of(p.intersection(island), 0.00006):
                    if len(r) >= 4:
                        green.append(r)

route_ways, routes = {}, []
for rid, (m, t) in rels.items():
    if t.get("route") not in ("bicycle", "mtb"):
        continue
    wl = [r for ty, r, role in m if ty == "way" and r in ways and any(i in nodes and inside(nodes[i][0], nodes[i][1]) for i in ways[r][0])]
    if not wl:
        continue
    label = t.get("name") or ("Radroute " + t.get("ref", "")).strip()
    if t.get("ref") and t.get("name") and t.get("network") != "icn":
        label = t["ref"] + " - " + t["name"]
    routes.append({"ref": t.get("ref", ""), "name": label, "w": wl})
    for w in wl:
        route_ways[w] = 1

CLS = {"cycleway": 0, "living_street": 1, "residential": 1, "unclassified": 1, "service": 1, "road": 1,
       "track": 2, "path": 3, "bridleway": 3, "footway": 4, "pedestrian": 4,
       "tertiary": 5, "tertiary_link": 5, "secondary": 6, "secondary_link": 6, "primary": 7, "primary_link": 7, "trunk": 7}
used, node_list, way_out, names, name_idx = {}, [], [], [], {}

def nid(i):
    if i not in used:
        used[i] = len(node_list); node_list.append(i)
    return used[i]

for wid, (nd, t) in ways.items():
    hw = t.get("highway")
    if hw not in CLS or t.get("bicycle") == "no" or t.get("area") == "yes":
        continue
    if t.get("access") in ("private", "no") and t.get("bicycle") not in ("yes", "designated", "permissive"):
        continue
    if hw == "service" and t.get("service") in ("parking_aisle", "drive-through"):
        continue
    pts = [i for i in nd if i in nodes]
    if len(pts) < 2 or not any(inside(nodes[i][0], nodes[i][1]) for i in pts):
        continue
    c = CLS[hw]
    if t.get("bicycle") == "designated" and c in (3, 4):
        c = 0
    if c == 2 and t.get("tracktype") in ("grade4", "grade5"):
        c = 8
    flags = 0
    if t.get("oneway") in ("yes", "1", "-1") and t.get("oneway:bicycle") != "no":
        flags |= 1
        if t.get("oneway") == "-1":
            pts = pts[::-1]
    if wid in route_ways:
        flags |= 2
    nm = t.get("name") or t.get("ref") or ""
    if nm not in name_idx:
        name_idx[nm] = len(names); names.append(nm)
    way_out.append([c, name_idx[nm], flags, [nid(i) for i in pts], wid])

flat = []
for i in node_list:
    flat += [round(nodes[i][0] * 1e5), round(nodes[i][1] * 1e5)]
wid_to_idx = {w[4]: k for k, w in enumerate(way_out)}
route_out = []
for r in routes:
    idx = [wid_to_idx[w] for w in r["w"] if w in wid_to_idx]
    L = 0.0
    for k in idx:
        p = way_out[k][3]
        for a, b in zip(p, p[1:]):
            la1, lo1, la2, lo2 = flat[a*2]/1e5, flat[a*2+1]/1e5, flat[b*2]/1e5, flat[b*2+1]/1e5
            L += math.hypot((lo2-lo1)*111320*math.cos(math.radians(la1)), (la2-la1)*110540)
    if idx:
        route_out.append({"ref": r["ref"], "name": r["name"], "w": idx, "len": round(L)})
route_out.sort(key=lambda r: int(r["ref"]) if r["ref"].isdigit() else 999)

def poi_cat(t):
    a, s, tu, h = t.get("amenity"), t.get("shop"), t.get("tourism"), t.get("historic")
    if a == "restaurant": return "food"
    if a == "fast_food": return "snack"
    if a in ("cafe", "ice_cream") or s == "bakery": return "cafe"
    if a in ("bar", "pub"): return "bar"
    if a == "drinking_water": return "water"
    if a == "toilets": return "wc"
    if s == "bicycle" or a in ("bicycle_rental", "bicycle_repair_station"): return "bike"
    if s in ("supermarket", "convenience"): return "shop"
    if a == "pharmacy": return "pharmacy"
    if a == "ferry_terminal": return "ferry"
    if t.get("natural") == "beach" or t.get("leisure") == "beach_resort": return "beach"
    if tu == "viewpoint": return "view"
    if tu in ("attraction", "museum") or h in ("castle", "fort", "ruins", "archaeological_site", "church", "monastery"): return "sight"
    return None

pois, seen = [], set()
def add_poi(t, lat, lon):
    cat = poi_cat(t)
    if not cat or not inside(lat, lon):
        return
    name = t.get("name") or ""
    if cat == "sight" and not name:
        return
    key = (cat, name, round(lat, 4), round(lon, 4))
    if key in seen:
        return
    seen.add(key)
    ex = {}
    for ki, ko in (("opening_hours", "oh"), ("cuisine", "cu"), ("phone", "ph"), ("contact:phone", "ph"), ("website", "web"), ("addr:city", "city")):
        if t.get(ki) and ko not in ex:
            ex[ko] = t[ki]
    pois.append([cat, round(lat, 5), round(lon, 5), name, ex])

for i, (lat, lon, t) in nodes.items():
    if t:
        add_poi(t, lat, lon)
for wid, (nd, t) in ways.items():
    if poi_cat(t):
        c = way_coords(wid)
        if c:
            add_poi(t, sum(p[1] for p in c)/len(c), sum(p[0] for p in c)/len(c))

places = [[t["name"], round(lat, 5), round(lon, 5), t["place"]] for i, (lat, lon, t) in nodes.items()
          if t.get("place") in ("village", "town", "hamlet", "suburb") and t.get("name") and inside(lat, lon)]

b = island.bounds
data = {"bbox": [b[1], b[0], b[3], b[2]], "land": land, "others": others, "green": green,
        "nodes": flat, "ways": [w[:4] for w in way_out], "names": names,
        "routes": route_out, "pois": pois, "places": places}
with open(out, "w") as fh:
    fh.write("window.UGLJAN=")
    json.dump(data, fh, separators=(",", ":"), ensure_ascii=False)
    fh.write(";\n")
from collections import Counter
print("ways", len(way_out), "nodes", len(node_list), "pois", len(pois), "places", [p[0] for p in places], "green", len(green), "size", os.path.getsize(out))
print(Counter(p[0] for p in pois)); print(Counter(w[0] for w in way_out))
for r in route_out: print(r["ref"], r["name"], r["len"])
