#!/usr/bin/env python3
"""Laedt ein Hoehenraster (Copernicus GLO-90 ueber api.open-meteo.com) fuer Ugljan.
Aufruf: fetch_dem.py <island.osm> <ausgabe.json>
Nur Rasterpunkte auf/nahe der Insel werden abgefragt, Meer = 0.
"""
import json, sys, time, urllib.request, os
import xml.etree.ElementTree as ET
from shapely.geometry import LineString, Point
from shapely.ops import linemerge, polygonize, unary_union
from shapely.prepared import prep

root = ET.parse(sys.argv[1]).getroot()
nodes = {n.get("id"): (float(n.get("lon")), float(n.get("lat"))) for n in root.findall("node")}
ways = {w.get("id"): [nodes[x.get("ref")] for x in w.findall("nd") if x.get("ref") in nodes] for w in root.findall("way")}
rel = [r for r in root.findall("relation") if r.get("id") == "6756342"][0]
outer = [LineString(ways[m.get("ref")]) for m in rel.findall("member") if m.get("type") == "way" and m.get("role") == "outer" and m.get("ref") in ways]
island = unary_union(list(polygonize(linemerge(outer))))
near = prep(island.buffer(0.004))

S, W, N, E = 44.004, 15.054, 44.164, 15.266
DLAT, DLON = 0.0008, 0.0011
rows, cols = int((N - S) / DLAT) + 1, int((E - W) / DLON) + 1
out = sys.argv[2]
grid = [[0] * cols for _ in range(rows)]
todo = [(r, c) for r in range(rows) for c in range(cols) if near.contains(Point(W + c * DLON, S + r * DLAT))]
print(rows, cols, "query points", len(todo))
for k in range(0, len(todo), 100):
    chunk = todo[k:k + 100]
    url = "https://api.open-meteo.com/v1/elevation?latitude=%s&longitude=%s" % (
        ",".join("%.5f" % (S + r * DLAT) for r, c in chunk), ",".join("%.5f" % (W + c * DLON) for r, c in chunk))
    for attempt in range(8):
        try:
            res = json.load(urllib.request.urlopen(url, timeout=60))["elevation"]
            break
        except Exception as e:
            print("retry", k, e); time.sleep(10 * (attempt + 1))
    else:
        raise SystemExit("failed")
    for (r, c), e in zip(chunk, res):
        grid[r][c] = max(0.0, round(e, 1))
    if k % 2000 == 0:
        print(k, "/", len(todo), flush=True)
    time.sleep(0.15)
json.dump({"s": S, "w": W, "dlat": DLAT, "dlon": DLON, "rows": rows, "cols": cols, "z": grid}, open(out, "w"), separators=(",", ":"))
print("done")
