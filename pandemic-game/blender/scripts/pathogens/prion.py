"""Prion: fehlgefaltetes Protein – alpha-Helices, beta-Faltblatt-Pfeile und
Verbindungsschleifen. Jedes Proteinsegment hat einen eigenen Bone (Faltung)."""
import math

from mathutils import Matrix, Vector, noise

import lib
from lib import Builder, Clip, fbm, frames, smoothstep, wave

ANIMATIONS = ["prion_idle", "prion_fold", "prion_unstable"]
IDLE = "prion_idle"

HELIX_COL = [(0.8, 0.03, 0.08), (0.95, 0.2, 0.15), (0.6, 0.02, 0.12)]
SHEET_COL = (0.98, 0.78, 0.55)
LOOP_COL = (0.92, 0.88, 0.9)


def helix_path(a, b, turns, radius, n=60):
    axis = b - a
    d = axis.normalized()
    s = d.orthogonal().normalized()
    t = d.cross(s)
    pts = []
    for i in range(n):
        u = i / (n - 1)
        ang = u * turns * math.tau
        pts.append(a + axis * u + (s * math.cos(ang) + t * math.sin(ang)) * radius)
    return pts


def build(col):
    rnd = lib.rng(41)
    b = Builder()
    # Sekundaerstruktur-Elemente kompakt anordnen (Tertiaerstruktur)
    specs = []
    kinds = ["H", "S", "H", "S", "S", "H", "S", "H", "H", "S"]
    dirs = lib.fib_sphere(len(kinds), 0.35, rnd)
    for i, k in enumerate(kinds):
        c = dirs[i] * rnd.uniform(0.18, 0.42)
        ax = (dirs[(i * 3 + 1) % len(dirs)] + Vector((rnd.uniform(-.5, .5), rnd.uniform(-.5, .5), rnd.uniform(-.5, .5)))).normalized()
        L = rnd.uniform(0.5, 0.72) if k == "H" else rnd.uniform(0.42, 0.6)
        a, e = c - ax * L / 2, c + ax * L / 2
        specs.append((k, a, e))
    ends = []
    for i, (k, a, e) in enumerate(specs):
        part = 10 + i
        if k == "H":
            pts = helix_path(a, e, rnd.uniform(3.0, 4.0), 0.085, 64)
            b.tube(pts, lambda t: 0.028, sides=8, part=part,
                   ring_shape=lambda t, ang: 1.0 / math.sqrt(math.cos(ang) ** 2 + (math.sin(ang) / 0.42) ** 2) * 1.25)
            ends.append((pts[0], pts[-1]))
        else:
            d = (e - a).normalized()
            wob = d.orthogonal().normalized()
            pts = [a + (e - a) * (j / 23) + wob * 0.025 * math.sin(j * 0.9) for j in range(24)]

            def w(t):
                if t < 0.78:
                    return 0.055
                if t < 0.8:
                    return 0.1
                return 0.1 * (1 - (t - 0.8) / 0.2) + 0.004
            b.tube(pts, w, sides=8, part=part,
                   ring_shape=lambda t, ang: 1.0 / math.sqrt(math.cos(ang) ** 2 + (math.sin(ang) / 0.22) ** 2),
                   normal_hint=wob)
            ends.append((pts[0], pts[-1]))
    # Verbindungsschleifen (Loops) zwischen den Elementen
    for i in range(len(ends) - 1):
        p0 = ends[i][1]
        p1 = ends[i + 1][0]
        mid = (p0 + p1) / 2
        mid = mid + mid.normalized() * 0.18 + Vector((rnd.uniform(-.1, .1), rnd.uniform(-.1, .1), rnd.uniform(-.1, .1)))
        b.tube(lib.catmull([p0, mid, p1], 8), lambda t: 0.018, sides=6, part=60 + i, cap_start=False, cap_end=False)
    # N- und C-Terminus
    b.tube(lib.catmull([ends[0][0], ends[0][0] * 1.4 + Vector((0, 0, 0.1)), ends[0][0] * 1.7], 6),
           lambda t: 0.018 * (1 - 0.5 * t), sides=6, part=60 + len(ends), cap_start=False)
    obj = b.to_object("Prion_Protein", col)

    lib.displace(obj, lambda co, n, p, t: 0.004 * fbm(co * 30, 2))

    def col_fn(co, n, part, t):
        if 10 <= part < 60:
            k = kinds[part - 10]
            if k == "H":
                c = HELIX_COL[(part - 10) % 3]
                return lib.lerp3(c, (1.0, 0.5, 0.4), 0.25 * smoothstep(-0.3, 0.8, fbm(co * 6, 2)))
            return lib.lerp3(SHEET_COL, (0.9, 0.35, 0.25), t * 0.6)
        return LOOP_COL
    lib.vertex_colors(obj, col_fn)
    lib.assign(obj, lib.material("Prion_Protein", rough=0.32, coat=0.7, coat_rough=0.15, vcol=True))

    # --- Rig: ein Bone pro Sekundaerstruktur
    bones = [("root", (0, 0, 0), (0, 0, 0.2), None)]
    for i, (k, a, e) in enumerate(specs):
        bones.append((f"fold_{i:02d}", tuple(a), tuple(e), "root"))
    rig = lib.armature("Prion_Rig", col, bones)
    fold = [f"fold_{i:02d}" for i in range(len(specs))]

    def rules(p):
        if 10 <= p < 60:
            return [fold[p - 10]]
        i = p - 60
        return [fold[max(0, min(i, len(fold) - 1))], fold[max(0, min(i + 1, len(fold) - 1))]]
    lib.auto_weights(obj, rig, rules, "root", power=2)

    # --- Evolution: Amyloid-Fibrillen (gestapelte Platten), Aggregat-Knoten, Kristallkeim
    evo = []
    eb = Builder()
    for j in range(3):
        base = dirs[j * 3] * 0.55
        d = (dirs[j * 3] + Vector((0.3, -0.2, 0.4))).normalized()
        s = d.orthogonal().normalized()
        for k in range(9):
            c = base + d * 0.045 * k
            rot = Matrix.Rotation(k * 0.25, 3, d) @ s
            eb.cone(c - rot * 0.07, c + rot * 0.07, 0.022, 0.022, segs=4, part=0)
    e1 = eb.to_object("EVO1_Prion_Fibrils", col)
    lib.assign(e1, lib.material("Prion_Fibril", (0.95, 0.9, 0.7), rough=0.3, coat=0.5))
    evo.append(e1)
    eb = Builder()
    for d in lib.fib_sphere(10, 0.3, lib.rng(6)):
        eb.icosphere(d * 0.62, 0.05, 1, part=0, scale=(1.2, 0.8, 1))
    e2 = eb.to_object("EVO2_Prion_Aggregates", col)
    lib.assign(e2, lib.material("Prion_Aggregate", (0.7, 0.05, 0.3), rough=0.35, coat=0.5,
                                emit=(0.8, 0.05, 0.4), emit_strength=0.8))
    evo.append(e2)
    eb = Builder()
    eb.icosphere((0, 0, 0), 0.16, 1, part=0)
    for d in lib.fib_sphere(6, 0.2, lib.rng(1)):
        eb.cone(d * 0.12, d * 0.34, 0.035, 0.0, segs=4, part=0, cap=False)
    e3 = eb.to_object("EVO3_Prion_Seed", col)
    lib.assign(e3, lib.material("Prion_Seed", (1.0, 0.3, 0.6), rough=0.2, emit=(1.0, 0.1, 0.4), emit_strength=2.5))
    evo.append(e3)
    for o in evo:
        lib.auto_weights(o, rig, lambda p: None, "root")

    lib.shape_key(obj, "Misfold", lambda co, n, p, t: Vector((noise.noise(co * 2.2), noise.noise(co * 2.2 + Vector((4, 0, 0))),
                                                             noise.noise(co * 2.2 + Vector((0, 7, 0))))) * 0.09)
    lib.shape_key(obj, "Unstable", lambda co, n, p, t: 0.025 * noise.noise(co * 14))
    lib.shape_key(obj, "Evolve", lambda co, n, p, t: 0.02 if 10 <= p < 60 else 0.0)

    ph = [rnd.uniform(0, math.tau) for _ in fold]
    L = 210
    with Clip("prion_idle", L) as c:
        for f in frames(L, 6):
            for i, bn in enumerate(fold):
                c.bone(rig, bn, f, rot=(0.07 * wave(f, L, ph[i], 1), 0.1 * wave(f, L, ph[i] + 1, 2),
                                        0.07 * wave(f, L, ph[i] + 2, 1)))
            c.bone(rig, "root", f, rot=(0, 0, math.tau * (f - 1) / L * 0.0 + 0.2 * wave(f, L, 0, 1)))
            c.shape(obj, "Misfold", f, 0.2 + 0.2 * wave(f, L, 0, 1))
    L = 150
    with Clip("prion_fold", L) as c:
        for f in frames(L, 3):
            u = (f - 1) / L
            env = math.sin(math.pi * u) ** 2
            for i, bn in enumerate(fold):
                sgn = 1 if i % 2 else -1
                c.bone(rig, bn, f, rot=(sgn * 0.9 * env, 0.6 * env * math.sin(ph[i]), -sgn * 0.5 * env),
                       loc=(0, -0.04 * env, 0))
            c.shape(obj, "Misfold", f, env)
    L = 60
    with Clip("prion_unstable", L) as c:
        for f in frames(L, 2):
            for i, bn in enumerate(fold):
                c.bone(rig, bn, f, rot=(0.12 * wave(f, L, ph[i], 7), 0.12 * wave(f, L, ph[i] * 2, 5), 0.08 * wave(f, L, ph[i], 9)),
                       scl=1 + 0.05 * wave(f, L, ph[i], 6))
            c.shape(obj, "Unstable", f, 0.6 + 0.4 * wave(f, L, 0, 8))
            c.shape(obj, "Misfold", f, 0.5 + 0.3 * wave(f, L, 1, 3))
    return {"main": obj}
