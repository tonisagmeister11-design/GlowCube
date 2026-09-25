"""Points of interest: shops, services, properties, safehouse, mission locations.

POIs are attached to generated buildings. Buildings with `interior` get a walk-in
ground-floor room from the Blender interior builder.
"""
import math

from . import geom as G

# (poi type, display name, preferred districts, preferred styles, count, interior kind)
POI_RULES = [
    ("shop_clothing", "Threadline", ["shopping"], ["retail"], 1, "clothing"),
    ("shop_clothing", "Threadline Vintage", ["oldtown"], ["oldtown"], 1, "clothing"),
    ("shop_weapons", "Iron Sight Arms", ["downtown"], ["midrise", "office", "retail"], 1, "weapons"),
    ("shop_weapons", "Iron Sight Arms", ["industrial"], ["warehouse", "factory"], 1, "weapons"),
    ("shop_convenience", "QuikStop 24", ["residential"], ["apartment"], 2, "convenience"),
    ("shop_convenience", "QuikStop 24", ["oldtown"], ["oldtown"], 1, "convenience"),
    ("shop_convenience", "QuikStop 24", ["downtown"], ["midrise", "office"], 1, "convenience"),
    ("shop_convenience", "QuikStop 24", ["suburbs"], ["house"], 1, "convenience"),
    ("shop_supermarket", "FreshCart Market", ["residential"], ["apartment"], 1, "supermarket"),
    ("shop_supermarket", "FreshCart Market", ["shopping"], ["retail"], 1, "supermarket"),
    ("mechanic", "Torque & Tune", ["industrial"], ["warehouse"], 1, "garage"),
    ("mechanic", "Torque & Tune", ["residential"], ["apartment"], 1, "garage"),
    ("jewelry", "Lumière Jewels", ["shopping"], ["retail"], 1, "jewelry"),
    ("electronics", "Pixelhaus", ["shopping"], ["retail"], 1, "convenience"),
    ("diner", "Blue Dolphin Diner", ["marina", "shopping"], ["marina_shop", "retail"], 1, "convenience"),
]

# properties that can be bought (type, name, district, style, price)
PROPERTY_RULES = [
    ("property_apartment", "Palmview Apartment", ["residential"], ["apartment"], 45000),
    ("property_house", "Westbrook House", ["suburbs"], ["house"], 120000),
    ("property_penthouse", "Skyline Penthouse", ["financial", "downtown"], ["tower", "office"], 450000),
    ("property_garage", "Ironworks Garage", ["industrial"], ["warehouse"], 60000),
    ("property_business", "Club Nocturne", ["entertainment"], ["club"], 250000),
    ("property_villa", "Crestline Villa", ["luxury"], ["villa"], 900000),
]


def _entrance(b, out=1.8):
    f = b["front"]
    m = b["front_mid"]
    return (round(m[0] + f[0] * out, 2), round(b["y"], 2), round(m[1] + f[1] * out, 2))


class PoiBuilder:
    def __init__(self, buildings, rng):
        self.buildings = buildings
        self.rng = rng
        self.pois = []
        self.used = set()

    def _pick(self, districts, styles, near=None):
        cands = [b for b in self.buildings
                 if b["id"] not in self.used and b["district"] in districts and b["style"] in styles
                 and b.get("usage", "generic") == "generic"]
        if not cands:
            cands = [b for b in self.buildings
                     if b["id"] not in self.used and b["district"] in districts and b.get("usage", "generic") == "generic"
                     and b["style"] not in ("tower", "construction")]
        if not cands:
            return None
        if near is not None:
            cands.sort(key=lambda b: G.dist(G.poly_centroid(b["footprint"]), near))
            return cands[0]
        # keep front length reasonable for a shop
        good = [b for b in cands if self._front_len(b) >= 7.0]
        return self.rng.choice(good or cands)

    def _front_len(self, b):
        f = b["front"]
        side = G.right(f)
        us = [G.dot(p, side) for p in b["footprint"]]
        return max(us) - min(us)

    def add(self, ptype, name, b, interior=None, **kw):
        self.used.add(b["id"])
        b["usage"] = ptype
        b["name"] = name
        if interior:
            b["interior"] = interior
            b["shopfront"] = True
        rec = dict(id=len(self.pois), type=ptype, name=name, building=b["id"],
                   entrance=_entrance(b), facing=b["front"], district=b["district"])
        rec.update(kw)
        self.pois.append(rec)
        return rec

    def build(self):
        # fixed-usage buildings from the landmark table
        for b in self.buildings:
            u = b.get("usage", "generic")
            if u in ("police_station", "hospital", "fire_station", "gas_station", "car_dealer",
                     "parking_garage", "landmark_bank_tower", "landmark_mall", "landmark_stadium",
                     "landmark_convention", "airport_terminal", "landmark_grand_hotel",
                     "landmark_aurelia_tower", "landmark_tv_tower", "landmark_observation_tower"):
                name = b.get("name", u)
                interior = {"police_station": "police", "gas_station": "convenience",
                            "car_dealer": "dealer", "landmark_bank_tower": "bank"}.get(u)
                ptype = {"landmark_bank_tower": "bank"}.get(u, u)
                if interior:
                    b["interior"] = interior
                self.used.add(b["id"])
                self.pois.append(dict(id=len(self.pois), type=ptype, name=name, building=b["id"],
                                      entrance=_entrance(b, 2.5), facing=b["front"], district=b["district"]))
        for (ptype, name, districts, styles, count, interior) in POI_RULES:
            for _ in range(count):
                b = self._pick(districts, styles)
                if b is None:
                    continue
                self.add(ptype, name, b, interior)
        # player safehouse near the start area
        b = self._pick(["residential"], ["apartment"], near=(-530.0, -80.0))
        if b:
            self.add("safehouse", "Safehouse", b, None, owned=True, price=0)
        for (ptype, name, districts, styles, price) in PROPERTY_RULES:
            b = self._pick(districts, styles)
            if b:
                self.add(ptype, name, b, None, owned=False, price=price)
        return self.pois
