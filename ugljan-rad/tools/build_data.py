#!/usr/bin/env python3
"""Baut aus OSM-Rohdaten und dem Höhenraster die Daten der App für die Insel Ugljan.

Aufruf: build_data.py <ordner-mit-osm-dateien> <dem.json> <assets-ordner>
Im OSM-Ordner: t_*.osm (Kacheln von api.openstreetmap.org/api/0.6/map) und island.osm
(relation/6756342/full). dem.json kommt von fetch_dem.py.
Schreibt data.js und relief.png in den Assets-Ordner.
"""
import glob, json, math, os, sys
import xml.etree.ElementTree as ET
import numpy as np
from PIL import Image, ImageDraw, ImageFilter
from shapely.geometry import Polygon, Point, LineString, MultiLineString
from shapely.ops import unary_union, linemerge, polygonize
from shapely.prepared import prep

src, dem_path, outdir = sys.argv[1], sys.argv[2], sys.argv[3]
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

# ---------- Höhenraster ----------
dem = json.load(open(dem_path))
Z = np.array(dem["z"], dtype=float)
DS, DW, DLAT, DLON = dem["s"], dem["w"], dem["dlat"], dem["dlon"]

def elev(lat, lon):
    y = (lat - DS) / DLAT; x = (lon - DW) / DLON
    r0 = int(math.floor(y)); c0 = int(math.floor(x))
    r0 = max(0, min(Z.shape[0] - 2, r0)); c0 = max(0, min(Z.shape[1] - 2, c0))
    fy = min(1, max(0, y - r0)); fx = min(1, max(0, x - c0))
    z = (Z[r0, c0] * (1 - fx) * (1 - fy) + Z[r0, c0 + 1] * fx * (1 - fy) +
         Z[r0 + 1, c0] * (1 - fx) * fy + Z[r0 + 1, c0 + 1] * fx * fy)
    return max(0.0, z)

def mdist(la1, lo1, la2, lo2):
    return math.hypot((lo2 - lo1) * 111320 * math.cos(math.radians(la1)), (la2 - la1) * 110540)

def rings_of(geom, tol):
    g = geom.simplify(tol, preserve_topology=True)
    polys = [g] if g.geom_type == "Polygon" else list(g.geoms)
    return [[[round(y, 5), round(x, 5)] for x, y in p.exterior.coords] for p in polys if not p.is_empty]

land = rings_of(island, 0.00003)
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

# Siedlungsflaechen (für die Ortsdarstellung)
built = []
for wid, (nd, t) in ways.items():
    if t.get("landuse") in ("residential", "retail", "commercial") and nd and nd[0] == nd[-1]:
        c = way_coords(wid)
        if len(c) >= 4:
            p = Polygon(c).buffer(0)
            if p.area > 1e-7 and island.intersects(p):
                for r in rings_of(p.intersection(island), 0.00004):
                    if len(r) >= 4:
                        built.append(r)

# ---------- Radrouten ----------
route_ways, routes = {}, []
for rid, (m, t) in rels.items():
    if t.get("route") not in ("bicycle", "mtb"):
        continue
    wl = [r for ty, r, role in m if ty == "way" and r in ways and any(i in nodes and inside(nodes[i][0], nodes[i][1]) for i in ways[r][0])]
    if not wl:
        continue
    label = t.get("name") or ""
    routes.append({"ref": t.get("ref", ""), "name": label, "net": t.get("network", ""), "w": wl})
    for w in wl:
        route_ways[w] = 1

# ---------- Wegenetz ----------
CLS = {"cycleway": 0, "living_street": 1, "residential": 1, "unclassified": 1, "service": 1, "road": 1,
       "track": 2, "path": 3, "bridleway": 3, "footway": 4, "pedestrian": 4,
       "tertiary": 5, "tertiary_link": 5, "secondary": 6, "secondary_link": 6, "primary": 7, "primary_link": 7, "trunk": 7}
# Belag: 0 glatt, 1 Pflaster, 2 verdichtet, 3 Schotter, 4 Erde/Gras, 5 Fels/grob
SURF = {"asphalt": 0, "paved": 0, "concrete": 0, "concrete:plates": 1, "paving_stones": 1, "sett": 1,
        "cobblestone": 1, "unhewn_cobblestone": 4, "compacted": 2, "fine_gravel": 2, "gravel": 3, "unpaved": 3,
        "pebblestone": 3, "dirt": 4, "ground": 4, "earth": 4, "grass": 4, "mud": 4, "sand": 4, "rock": 5, "stone": 5}
DEF_SURF = {0: 0, 1: 0, 2: 3, 3: 4, 4: 1, 5: 0, 6: 0, 7: 0, 8: 4}
TRACK_SURF = {"grade1": 0, "grade2": 2, "grade3": 3, "grade4": 4, "grade5": 4}

coords_out = []  # [lat, lon, ele]
osm_to_idx = {}
way_out, names, name_idx = [], [], {}

def nid(i):
    if i not in osm_to_idx:
        osm_to_idx[i] = len(coords_out)
        la, lo = nodes[i][0], nodes[i][1]
        coords_out.append([la, lo, elev(la, lo)])
    return osm_to_idx[i]

def new_pt(la, lo):
    coords_out.append([la, lo, elev(la, lo)])
    return len(coords_out) - 1

STEP = 25.0
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
    s = SURF.get(t.get("surface", ""), TRACK_SURF.get(t.get("tracktype", ""), DEF_SURF[c]))
    if t.get("smoothness") in ("bad", "very_bad"):
        s = max(s, 3)
    if t.get("smoothness") in ("horrible", "very_horrible", "impassable"):
        s = 5
    try:
        if float((t.get("mtb:scale") or "0").rstrip("+-")) >= 2:
            s = 5
    except ValueError:
        pass
    flags = 0
    if t.get("oneway") in ("yes", "1", "-1") and t.get("oneway:bicycle") != "no" and t.get("cycleway") != "opposite":
        flags |= 1
        if t.get("oneway") == "-1":
            pts = pts[::-1]
    if wid in route_ways:
        flags |= 2
    if t.get("bridge") == "yes":
        flags |= 4
    if t.get("lit") == "yes":
        flags |= 8
    nm = t.get("name") or t.get("ref") or ""
    if nm not in name_idx:
        name_idx[nm] = len(names); names.append(nm)
    seq = [nid(pts[0])]
    for a, b in zip(pts, pts[1:]):
        la1, lo1, la2, lo2 = nodes[a][0], nodes[a][1], nodes[b][0], nodes[b][1]
        d = mdist(la1, lo1, la2, lo2)
        k = int(d // STEP)
        for j in range(1, k + 1):
            f = j / (k + 1)
            seq.append(new_pt(la1 + (la2 - la1) * f, lo1 + (lo2 - lo1) * f))
        seq.append(nid(b))
    way_out.append([c, name_idx[nm], flags, s, seq, wid])

# Höhen glaetten: das 90-m-Raster liefert quer am Hang falsche Steigungen. Laplace-Glaettung über das
# Wegenetz (Nachbarn im Abstand von ca. 25 m), entspricht etwa +-75 m Fenster.
nbr = [[] for _ in coords_out]
for w in way_out:
    seq = w[4]
    for a_, b_ in zip(seq, seq[1:]):
        nbr[a_].append(b_); nbr[b_].append(a_)
z = np.array([p[2] for p in coords_out])
for _ in range(8):
    nz = z.copy()
    for k, nb in enumerate(nbr):
        if nb:
            nz[k] = 0.5 * z[k] + 0.5 * sum(z[j] for j in nb) / len(nb)
    z = nz
for k in range(len(coords_out)):
    coords_out[k][2] = max(0.0, float(z[k]))
# Bruecken: Höhe linear zwischen den Enden
for w in way_out:
    if w[2] & 4:
        seq = w[4]; z0 = coords_out[seq[0]][2]; z1 = coords_out[seq[-1]][2]
        for k in range(1, len(seq) - 1):
            coords_out[seq[k]][2] = z0 + (z1 - z0) * k / (len(seq) - 1)

# ---------- Schoenheit je Wegpunkt (0-9, +10 = direkt am Meer) ----------
import shapely
from shapely import affinity
def to_m(g):
    return affinity.affine_transform(g, [80100, 0, 0, 110540, -15.15 * 80100, -44.08 * 110540])
coast_m = to_m(island.boundary)
nat = []
for wid, (nd, t) in ways.items():
    if (t.get("landuse") in ("forest", "orchard", "meadow", "vineyard") or t.get("natural") in ("wood", "scrub", "heath")) and nd and nd[0] == nd[-1]:
        c = way_coords(wid)
        if len(c) >= 4:
            p_ = Polygon(c).buffer(0)
            if p_.area > 5e-8 and island.intersects(p_):
                nat.append(p_)
nat_m = to_m(unary_union(nat)) if nat else None
attr = []
for i, (lat, lon, t) in nodes.items():
    c_ = None
    if t.get("tourism") == "viewpoint" or (t.get("natural") == "peak" and t.get("name")): c_ = 1.0
    elif t.get("natural") == "beach" or t.get("leisure") == "beach_resort": c_ = 0.8
    elif t.get("historic") or t.get("tourism") in ("attraction", "artwork"): c_ = 0.6
    if c_ and inside(lat, lon):
        attr.append(((lon - 15.15) * 80100, (lat - 44.08) * 110540, c_))
for wid, (nd, t) in ways.items():
    if t.get("natural") == "beach" or t.get("leisure") == "beach_resort":
        cc = way_coords(wid)
        if cc:
            attr.append(((cc[0][0] - 15.15) * 80100, (cc[0][1] - 44.08) * 110540, 0.8))
pts_m = shapely.points([((lo - 15.15) * 80100, (la - 44.08) * 110540) for la, lo, zz in coords_out])
dcoast = shapely.distance(pts_m, coast_m)
in_nat = shapely.contains_xy(nat_m, [p.x for p in pts_m], [p.y for p in pts_m]) if nat_m is not None else np.zeros(len(coords_out), bool)
XY = np.array([[p.x, p.y] for p in pts_m])
A_ = np.array(attr) if attr else np.zeros((0, 3))
scenic = []
for k, (la, lo, zz) in enumerate(coords_out):
    d = dcoast[k]
    coast = 1.0 if d < 70 else max(0.0, 1 - (d - 70) / 330)
    nat_v = 0.45 if in_nat[k] else 0.0
    poi = 0.0
    if len(A_):
        dd = np.hypot(A_[:, 0] - XY[k, 0], A_[:, 1] - XY[k, 1])
        poi = float(np.max(A_[:, 2] * np.clip(1 - dd / 250, 0, 1)))
    view = min(1.0, max(0.0, (zz - 60) / 160)) * 0.7 if d < 1500 else 0.0
    sc = 1 - (1 - coast * 0.9) * (1 - nat_v) * (1 - poi * 0.6) * (1 - view)
    scenic.append(min(9, int(round(sc * 9))) + (10 if d < 150 else 0))

flat = []
for (la, lo, zz), sc in zip(coords_out, scenic):
    flat += [round(la * 1e5), round(lo * 1e5), round(zz * 10), sc]
from collections import Counter as _C
print("scenic", sorted(_C(x % 10 for x in scenic).items()), "coast", sum(1 for x in scenic if x >= 10))

wid_to_idx = {w[5]: k for k, w in enumerate(way_out)}
route_out = []
for r in routes:
    idx = [wid_to_idx[w] for w in r["w"] if w in wid_to_idx]
    if idx:
        route_out.append({"ref": r["ref"], "name": r["name"], "net": r["net"], "w": idx})
route_out.sort(key=lambda r: (r["net"] == "icn", int(r["ref"]) if r["ref"].isdigit() else 999))

# ---------- Orte von Interesse ----------
def poi_cat(t):
    a, s, tu, h, n, l = t.get("amenity"), t.get("shop"), t.get("tourism"), t.get("historic"), t.get("natural"), t.get("leisure")
    if a == "restaurant": return "food"
    if a == "fast_food": return "snack"
    if a in ("cafe", "ice_cream") or s in ("bakery", "pastry", "ice_cream"): return "cafe"
    if a in ("bar", "pub", "biergarten", "nightclub"): return "bar"
    if a in ("drinking_water", "water_point") or n == "spring" or (a == "fountain" and t.get("drinking_water") == "yes"): return "water"
    if a in ("toilets", "shower"): return "wc"
    if s == "bicycle" or a in ("bicycle_rental", "bicycle_repair_station", "compressed_air"): return "bike"
    if s in ("supermarket", "convenience", "greengrocer", "butcher", "deli", "farm", "wine", "alcohol", "general", "kiosk", "seafood"): return "shop"
    if a in ("pharmacy", "doctors", "clinic", "hospital", "dentist") or t.get("emergency") == "defibrillator": return "health"
    if a in ("ferry_terminal",): return "ferry"
    if n == "beach" or l in ("beach_resort", "bathing_place"): return "beach"
    if tu == "viewpoint": return "view"
    if n == "peak" and (t.get("name") or t.get("ele")): return "peak"
    if tu in ("attraction", "museum", "artwork", "gallery") or h or a == "place_of_worship" or (a == "monastery"): return "sight"
    if tu in ("hotel", "guest_house", "hostel", "apartment", "camp_site", "motel", "chalet"): return "stay"
    if a in ("atm", "bank", "bureau_de_change", "post_office"): return "money"
    if a == "fuel": return "fuel"
    if l == "marina" or t.get("harbour") == "yes" or l == "slipway": return "port"
    if a == "bus_station" or t.get("highway") == "bus_stop": return "bus"
    if a == "parking" and t.get("access") not in ("private", "customers"): return "parking"
    return None

CUISINE = {"seafood": "Fisch und Meeresfrüchte", "pizza": "Pizza", "regional": "Regional", "italian": "Italienisch",
           "croatian": "Kroatisch", "mediterranean": "Mediterran", "burger": "Burger", "ice_cream": "Eis", "grill": "Grill",
           "coffee_shop": "Kaffee", "fish": "Fisch", "international": "International", "sandwich": "Sandwiches",
           "local": "Regional", "steak_house": "Steak", "barbecue": "Grill", "cake": "Kuchen", "crepe": "Crepes"}
pois = []
by_name = {}

def add_poi(t, lat, lon):
    cat = poi_cat(t)
    if not cat or not inside(lat, lon):
        return
    name = t.get("name") or t.get("name:hr") or ""
    if cat in ("sight", "port") and not name:
        return
    if cat == "sight" and t.get("tourism") == "attraction" and t.get("building"):
        return
    if name:
        for q in by_name.get((cat, name), []):
            if mdist(lat, lon, q[1], q[2]) < 300:
                return
    else:
        for q in pois[-400:]:
            if q[0] == cat and not q[3] and mdist(lat, lon, q[1], q[2]) < 15:
                return
    ex = {}
    for ki, ko in (("opening_hours", "oh"), ("phone", "ph"), ("contact:phone", "ph"), ("mobile", "ph"),
                   ("website", "web"), ("contact:website", "web"), ("addr:street", "st"), ("addr:housenumber", "hn"),
                   ("ele", "ele"), ("description", "desc"), ("stars", "stars"), ("outdoor_seating", "out"),
                   ("internet_access", "wifi"), ("fee", "fee"), ("wheelchair", "wc")):
        if t.get(ki) and ko not in ex:
            ex[ko] = t[ki]
    sub = {"ice_cream": "Eis", "bakery": "Bäckerei", "pub": "Pub", "nightclub": "Club", "bicycle_rental": "Fahrradverleih",
           "bicycle_repair_station": "Reparaturstation", "compressed_air": "Luftpumpe", "spring": "Quelle", "shower": "Dusche",
           "doctors": "Arzt", "clinic": "Ambulanz", "dentist": "Zahnarzt", "defibrillator": "Defibrillator", "camp_site": "Campingplatz",
           "hotel": "Hotel", "guest_house": "Pension", "apartment": "Apartment", "hostel": "Hostel", "atm": "Geldautomat",
           "bank": "Bank", "post_office": "Post", "place_of_worship": "Kirche", "museum": "Museum", "castle": "Festung",
           "fort": "Festung", "ruins": "Ruine", "archaeological_site": "Ausgrabung", "supermarket": "Supermarkt",
           "convenience": "Lebensmittel", "greengrocer": "Obst und Gemüse", "butcher": "Metzger", "wine": "Wein",
           "kiosk": "Kiosk", "bus_stop": "Bushaltestelle", "slipway": "Slipanlage", "marina": "Marina", "seafood": "Fischladen"}
    for k in ("amenity", "shop", "tourism", "historic", "natural", "leisure", "emergency", "highway"):
        if t.get(k) in sub:
            ex["sub"] = sub[t[k]]; break
    cu = [CUISINE.get(x.strip(), x.strip().replace("_", " ").capitalize()) for x in (t.get("cuisine") or "").split(";") if x.strip()]
    if cu:
        ex["cu"] = ", ".join(cu[:3])
    p = [cat, round(lat, 5), round(lon, 5), name, ex]
    pois.append(p)
    if name:
        by_name.setdefault((cat, name), []).append(p)

for i, (lat, lon, t) in nodes.items():
    if t:
        add_poi(t, lat, lon)
for wid, (nd, t) in ways.items():
    if poi_cat(t) and not t.get("highway"):
        c = way_coords(wid)
        if c:
            add_poi(t, sum(p[1] for p in c) / len(c), sum(p[0] for p in c) / len(c))
for rid, (m, t) in rels.items():
    if poi_cat(t):
        pts = [nodes[i] for ty, w, role in m if ty == "way" and w in ways for i in ways[w][0] if i in nodes]
        if pts:
            add_poi(t, sum(p[0] for p in pts) / len(pts), sum(p[1] for p in pts) / len(pts))
for p in pois:
    if p[0] in ("peak", "view") and "ele" not in p[4]:
        p[4]["ele"] = str(round(elev(p[1], p[2])))

# ---------- Ortsnamen: Doerfer, Weiler, Ortsteile, Flurnamen, Buchten, Kaps, Inselchen ----------
# Punkte, Flaechen und Relationen; Buchten liegen mit ihrem Namenspunkt oft im Wasser,
# deshalb je nach Art ein groesserer Abstand zur Kueste.
SETTLE = ("village", "town", "hamlet", "suburb", "quarter", "neighbourhood", "isolated_dwelling", "locality")
def place_kind(t):
    if t.get("place") in SETTLE + ("island", "islet"):
        return t["place"]
    if t.get("natural") in ("bay", "cape", "peninsula", "island", "islet"):
        return t["natural"]
    return None
MAXD = {"bay": 900, "cape": 400, "peninsula": 400, "island": 1500, "islet": 1500}
cands = []
def add_place(t, lat, lon):
    k = place_kind(t)
    nm = t.get("name")
    if not k or not nm or (nm == "Ugljan" and k in ("island", "islet")):
        return
    if island.distance(Point(lon, lat)) * 80000 > MAXD.get(k, 300):
        return
    cands.append([nm, round(lat, 5), round(lon, 5), k])
for i, (lat, lon, t) in nodes.items():
    if t:
        add_place(t, lat, lon)
for wid, (nd, t) in ways.items():
    if place_kind(t) and t.get("name"):
        c = way_coords(wid)
        if c:
            g = Polygon(c).buffer(0) if len(c) >= 4 and nd[0] == nd[-1] else LineString(c)
            pp = g.representative_point() if g.geom_type in ("Polygon", "MultiPolygon") else g.interpolate(0.5, normalized=True)
            add_place(t, pp.y, pp.x)
for rid, (m, t) in rels.items():
    if place_kind(t) and t.get("name") and rid != "6756342":
        pts = [nodes[i] for ty, w, role in m if ty == "way" and w in ways for i in ways[w][0] if i in nodes]
        if pts:
            add_place(t, sum(p[0] for p in pts) / len(pts), sum(p[1] for p in pts) / len(pts))
# doppelte Namen (Punkt und Flaeche desselben Ortes) zusammenfassen, Siedlungen bevorzugen
PRIO = {k: i for i, k in enumerate(("town", "village", "hamlet", "suburb", "quarter", "neighbourhood", "isolated_dwelling", "locality", "island", "islet", "bay", "cape", "peninsula"))}
cands.sort(key=lambda c: PRIO[c[3]])
places = []
for c in cands:
    if any(c[0] == q[0] and mdist(c[1], c[2], q[1], q[2]) < 600 for q in places):
        continue
    places.append(c)

# ---------- Höhenlinien ----------
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
ys = DS + np.arange(Z.shape[0]) * DLAT
xs = DW + np.arange(Z.shape[1]) * DLON
cs = plt.contour(xs, ys, Z, levels=list(range(25, 400, 25)))
contours = []
isl_s = island.buffer(-0.0002)
for lvl, segs in zip(cs.levels, cs.allsegs):
    lines = [LineString(s) for s in segs if len(s) >= 3]
    if not lines:
        continue
    g = MultiLineString(lines).intersection(isl_s).simplify(0.00008)
    geoms = [g] if g.geom_type == "LineString" else list(getattr(g, "geoms", []))
    for ln in geoms:
        if ln.geom_type == "LineString" and ln.length > 0.0015:
            contours.append([int(lvl), [[round(y, 5), round(x, 5)] for x, y in ln.coords]])

# ---------- Relief (Schummerung) als Bild ----------
up = 6
H, Wd = Z.shape
img = Image.fromarray(Z.astype(np.float32), mode="F").resize((Wd * up, H * up), Image.BICUBIC)
z = np.array(img, dtype=float)
cy = DLAT * 110540 / up; cx = DLON * 111320 * math.cos(math.radians(44.08)) / up
gy, gx = np.gradient(z, cy, cx)
slope = np.arctan(np.hypot(gx, gy) * 1.6)
aspect = np.arctan2(-gx, gy)
az, alt = math.radians(315), math.radians(40)
# Bildzeilen laufen von Sued nach Nord -> Lichtrichtung entsprechend
shade = np.sin(alt) * np.cos(slope) + np.cos(alt) * np.sin(slope) * np.cos(az - aspect + math.pi)
shade = np.clip(shade, 0, 1)
flatv = np.sin(alt)
dark = np.clip((flatv - shade) / flatv, 0, 1)
light = np.clip((shade - flatv) / (1 - flatv), 0, 1)
rgba = np.zeros((H * up, Wd * up, 4), dtype=np.uint8)
alpha = np.clip(dark * 200 + light * 70, 0, 255)
rgba[..., 0] = np.where(dark > 0, 0, 230)
rgba[..., 1] = np.where(dark > 0, 6, 245)
rgba[..., 2] = np.where(dark > 0, 12, 200)
rgba[..., 3] = alpha.astype(np.uint8)
mask = Image.new("L", (Wd * up, H * up), 0)
md = ImageDraw.Draw(mask)
geoms = [island] if island.geom_type == "Polygon" else list(island.geoms)
for g in geoms:
    md.polygon([((x - DW) / DLON * up, (y - DS) / DLAT * up) for x, y in g.exterior.coords], fill=255)
mask = mask.filter(ImageFilter.GaussianBlur(1.5))
rgba[..., 3] = (rgba[..., 3].astype(float) * np.array(mask) / 255).astype(np.uint8)
im = Image.fromarray(rgba, "RGBA").transpose(Image.FLIP_TOP_BOTTOM)
im.save(os.path.join(outdir, "relief.png"), optimize=True)
relief_bounds = [[DS - DLAT / 2, DW - DLON / 2], [DS + (H - 0.5) * DLAT, DW + (Wd - 0.5) * DLON]]

b = island.bounds
data = {"bbox": [b[1], b[0], b[3], b[2]], "land": land, "others": others, "green": green, "built": built,
        "nodes": flat, "ways": [w[:5] for w in way_out], "names": names, "routes": route_out,
        "pois": pois, "places": places, "contours": contours, "relief": relief_bounds,
        "maxEle": round(float(Z.max())),
        "dem": {"s": DS, "w": DW, "dlat": DLAT, "dlon": DLON, "rows": int(Z.shape[0]), "cols": int(Z.shape[1]),
                "z": [int(round(v)) for v in Z.flatten()]}}
with open(os.path.join(outdir, "data.js"), "w") as fh:
    fh.write("window.UGLJAN=")
    json.dump(data, fh, separators=(",", ":"), ensure_ascii=False)
    fh.write(";\n")
from collections import Counter
print("ways", len(way_out), "nodes", len(coords_out), "pois", len(pois), "places", len(places), "contours", len(contours),
      "size", os.path.getsize(os.path.join(outdir, "data.js")), "relief", im.size, "maxEle", Z.max())
print(Counter(p[0] for p in pois))
print(Counter(w[3] for w in way_out))
