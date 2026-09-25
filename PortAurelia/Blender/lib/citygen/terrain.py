"""Terrain height field for Port Aurelia.

The terrain is a regular height grid (default 4 m) over the whole world square.
Water is a single global plane at SEA_LEVEL: every place where the ground lies
below it is water (ocean, marina, harbour basins, canal, park lake).
"""
import math
import numpy as np

from .geom import smoothstep

SEA_LEVEL = -1.5
WORLD_MIN = -1600.0
WORLD_SIZE = 3200.0

CANAL_X = 290.0
CANAL_Z_START = -770.0
LAKE_C = (-40.0, -560.0)
LAKE_R = (85.0, 45.0)

HARBOR_BASINS = [
    (420.0, 820.0, 560.0, 1200.0),
    (700.0, 800.0, 880.0, 1200.0),
    (1020.0, 860.0, 1180.0, 1200.0),
]
MARINA_BASIN = (-250.0, 790.0, 190.0, 1200.0)


def coast_z(x):
    """Z of the shoreline: land where z < coast_z(x)."""
    if x < -300.0:
        return 865.0 + 20.0 * math.sin(x / 170.0) + 10.0 * math.sin(x / 61.0)
    if x < 330.0:
        return 955.0
    if x < 1400.0:
        return 1070.0
    return 1070.0 - (x - 1400.0) * 0.8


CANAL_WIDEN = (640.0, 780.0)


def canal_widths(z):
    """(floor half width, top half width) of the concrete canal at z."""
    t = smoothstep(CANAL_WIDEN[0], CANAL_WIDEN[1], z)
    return 11.0 + 30.0 * t, 30.0 + 26.0 * t


# ------------------------------------------------------------ numpy noise
def _hash_np(xi, zi, seed):
    h = (xi * 73856093) ^ (zi * 19349663) ^ (seed * 83492791)
    h = h & 0xFFFFFFFF
    h = ((h ^ (h >> 16)) * 0x45D9F3B) & 0xFFFFFFFF
    h = ((h ^ (h >> 16)) * 0x45D9F3B) & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFFFFFF) / 4294967296.0


def value_noise_np(x, z, seed=0):
    xi = np.floor(x).astype(np.int64)
    zi = np.floor(z).astype(np.int64)
    xf = x - xi
    zf = z - zi
    a = _hash_np(xi, zi, seed)
    b = _hash_np(xi + 1, zi, seed)
    c = _hash_np(xi, zi + 1, seed)
    d = _hash_np(xi + 1, zi + 1, seed)
    u = xf * xf * xf * (xf * (xf * 6 - 15) + 10)
    v = zf * zf * zf * (zf * (zf * 6 - 15) + 10)
    return (a * (1 - u) + b * u) * (1 - v) + (c * (1 - u) + d * u) * v


def fbm_np(x, z, octaves=4, seed=0):
    amp, f, s, n = 0.5, 1.0, 0.0, 0.0
    for o in range(octaves):
        s = s + value_noise_np(x * f, z * f, seed + o * 17) * amp
        n += amp
        amp *= 0.5
        f *= 2.03
    return s / n


def _smooth_np(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0.0, 1.0)
    return t * t * (3 - 2 * t)


def natural_height_np(X, Z):
    """Vectorised natural terrain height (before road carving)."""
    h = np.zeros_like(X, dtype=np.float64)

    # --- northern hills ----------------------------------------------------
    t_hill = _smooth_np(-760.0, -1010.0, Z)
    # western hills (above the suburbs / luxury area) start a little earlier
    t_west = _smooth_np(-700.0, -900.0, Z) * _smooth_np(-300.0, -500.0, X)
    t_hill = np.maximum(t_hill, t_west)
    ridge = 16.0 + 92.0 * fbm_np(X / 430.0, Z / 430.0, 5, 11)
    peak = 45.0 * np.exp(-((X - 400.0) ** 2 + (Z + 1150.0) ** 2) / (260.0 ** 2))
    hill = ridge + peak
    # eastern rural valley is gentler
    hill = hill * (1.0 - 0.6 * _smooth_np(780.0, 980.0, X))
    h = h + t_hill * hill

    # --- far north plateau (rural farmland) ---------------------------------
    t_rural = _smooth_np(-1240.0, -1420.0, Z)
    plateau = 48.0 + 14.0 * fbm_np(X / 320.0, Z / 320.0, 3, 23)
    h = h * (1.0 - t_rural) + plateau * t_rural

    # --- coast ---------------------------------------------------------------
    cz = np.vectorize(coast_z)(X[0, :]) if X.ndim == 2 else np.vectorize(coast_z)(X)
    if X.ndim == 2:
        cz = np.broadcast_to(cz[np.newaxis, :], X.shape)
    s = cz - Z  # >0 on land
    beach = X < -300.0
    beach_h = -2.2 + 2.2 * _smooth_np(0.0, 110.0, s)
    beach_h = np.where(s < 0, -2.2 + s * 0.045, beach_h)
    beach_h = np.maximum(beach_h, -12.0)
    h = np.where(beach & (s < 110.0), np.minimum(h, beach_h), h)
    # quay walls elsewhere: deep water right at the edge
    quay_sea = (~beach) & (s < 0)
    h = np.where(quay_sea, np.maximum(-8.0 + s * 0.02, -14.0), h)

    # --- basins ----------------------------------------------------------------
    for (x0, z0, x1, z1) in HARBOR_BASINS + [MARINA_BASIN]:
        inside = (X > x0) & (X < x1) & (Z > z0) & (Z < z1)
        h = np.where(inside, -7.5, h)

    # --- canal -----------------------------------------------------------------
    fw = 11.0 + 30.0 * _smooth_np(CANAL_WIDEN[0], CANAL_WIDEN[1], Z)
    tw = 30.0 + 26.0 * _smooth_np(CANAL_WIDEN[0], CANAL_WIDEN[1], Z)
    dx = np.abs(X - CANAL_X)
    canal_active = _smooth_np(CANAL_Z_START - 5.0, CANAL_Z_START + 5.0, Z)
    floor_h = -3.6 - 3.5 * _smooth_np(760.0, 950.0, Z)
    ch = floor_h + (0.0 - floor_h) * _smooth_np(fw, tw, dx)
    h = np.where((dx < tw) & (canal_active > 0.5), np.minimum(h, ch), h)

    # --- park lake -------------------------------------------------------------
    e = ((X - LAKE_C[0]) / LAKE_R[0]) ** 2 + ((Z - LAKE_C[1]) / LAKE_R[1]) ** 2
    lake_h = -3.2 + 3.2 * _smooth_np(0.55, 1.5, e)
    h = np.where(e < 1.5, np.minimum(h, lake_h), h)

    return h


class Terrain:
    def __init__(self, res=4.0):
        self.res = res
        self.n = int(WORLD_SIZE / res) + 1
        xs = WORLD_MIN + np.arange(self.n) * res
        self.xs = xs
        X, Z = np.meshgrid(xs, xs)  # X varies along axis 1, Z along axis 0
        self.natural = natural_height_np(X, Z)
        self.h = self.natural.copy()
        # 0 = natural, 1 = road carved / flattened
        self.road_mask = np.zeros_like(self.h, dtype=np.float32)
        # cells removed from the terrain mesh (tunnel portals)
        self.holes = np.zeros((self.n - 1, self.n - 1), dtype=bool)

    # ------------------------------------------------------------------ query
    def _sample(self, grid, x, z):
        fx = (x - WORLD_MIN) / self.res
        fz = (z - WORLD_MIN) / self.res
        ix = int(math.floor(fx))
        iz = int(math.floor(fz))
        ix = max(0, min(self.n - 2, ix))
        iz = max(0, min(self.n - 2, iz))
        tx = min(max(fx - ix, 0.0), 1.0)
        tz = min(max(fz - iz, 0.0), 1.0)
        a = grid[iz, ix]
        b = grid[iz, ix + 1]
        c = grid[iz + 1, ix]
        d = grid[iz + 1, ix + 1]
        return float((a * (1 - tx) + b * tx) * (1 - tz) + (c * (1 - tx) + d * tx) * tz)

    def height(self, x, z):
        return self._sample(self.h, x, z)

    def natural_height(self, x, z):
        return self._sample(self.natural, x, z)

    def is_water(self, x, z):
        return self.natural_height(x, z) < SEA_LEVEL + 0.3

    def slope(self, x, z):
        e = 2.0
        dx = self.height(x + e, z) - self.height(x - e, z)
        dz = self.height(x, z + e) - self.height(x, z - e)
        return math.hypot(dx, dz) / (2 * e)

    # ------------------------------------------------------------------ carve
    def carve_segment(self, a, b, ya, yb, half_w, falloff, mode="both"):
        """Flatten terrain along segment a-b to the road height.

        mode: "both" sets terrain to road height, "cut" only lowers it.
        """
        r = half_w + falloff
        x0 = min(a[0], b[0]) - r
        x1 = max(a[0], b[0]) + r
        z0 = min(a[1], b[1]) - r
        z1 = max(a[1], b[1]) + r
        i0 = max(0, int((x0 - WORLD_MIN) / self.res))
        i1 = min(self.n - 1, int((x1 - WORLD_MIN) / self.res) + 1)
        j0 = max(0, int((z0 - WORLD_MIN) / self.res))
        j1 = min(self.n - 1, int((z1 - WORLD_MIN) / self.res) + 1)
        if i1 <= i0 or j1 <= j0:
            return
        xs = self.xs[i0:i1 + 1]
        zs = self.xs[j0:j1 + 1]
        X, Z = np.meshgrid(xs, zs)
        abx, abz = b[0] - a[0], b[1] - a[1]
        l2 = abx * abx + abz * abz
        if l2 < 1e-6:
            t = np.zeros_like(X)
        else:
            t = np.clip(((X - a[0]) * abx + (Z - a[1]) * abz) / l2, 0.0, 1.0)
        px = a[0] + abx * t
        pz = a[1] + abz * t
        d = np.sqrt((X - px) ** 2 + (Z - pz) ** 2)
        y = ya + (yb - ya) * t - 0.12
        w = 1.0 - _smooth_np(half_w, half_w + falloff, d)
        cur = self.h[j0:j1 + 1, i0:i1 + 1]
        if mode == "cut":
            new = np.where(cur > y, cur * (1 - w) + y * w, cur)
        else:
            new = cur * (1 - w) + y * w
        self.h[j0:j1 + 1, i0:i1 + 1] = new
        rm = self.road_mask[j0:j1 + 1, i0:i1 + 1]
        self.road_mask[j0:j1 + 1, i0:i1 + 1] = np.maximum(rm, (d < half_w).astype(np.float32))

    def cut_hole_segment(self, a, b, half_w):
        """Remove terrain cells whose centre lies within half_w of the segment."""
        r = half_w
        x0 = min(a[0], b[0]) - r
        x1 = max(a[0], b[0]) + r
        z0 = min(a[1], b[1]) - r
        z1 = max(a[1], b[1]) + r
        i0 = max(0, int((x0 - WORLD_MIN) / self.res))
        i1 = min(self.n - 2, int((x1 - WORLD_MIN) / self.res) + 1)
        j0 = max(0, int((z0 - WORLD_MIN) / self.res))
        j1 = min(self.n - 2, int((z1 - WORLD_MIN) / self.res) + 1)
        for j in range(j0, j1 + 1):
            for i in range(i0, i1 + 1):
                cx = WORLD_MIN + (i + 0.5) * self.res
                cz = WORLD_MIN + (j + 0.5) * self.res
                abx, abz = b[0] - a[0], b[1] - a[1]
                l2 = abx * abx + abz * abz
                t = 0.0 if l2 < 1e-6 else max(0.0, min(1.0, ((cx - a[0]) * abx + (cz - a[1]) * abz) / l2))
                px, pz = a[0] + abx * t, a[1] + abz * t
                if math.hypot(cx - px, cz - pz) < half_w:
                    self.holes[j, i] = True
