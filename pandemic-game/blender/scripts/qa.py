"""Automatische Qualitaetskontrolle fuer jedes erzeugte Modell."""
import json
import os
import struct

import bpy
from mathutils import Vector

import lib


def check_blender(col, required_anims):
    errors = []
    objs = lib.collection_objects(col)
    meshes = [o for o in objs if o.type == 'MESH']
    rigs = [o for o in objs if o.type == 'ARMATURE']
    if not meshes:
        errors.append("kein Mesh")
    tris = 0
    lo = Vector((1e9, 1e9, 1e9))
    hi = Vector((-1e9, -1e9, -1e9))
    for o in meshes:
        me = o.data
        # validate() liefert True, wenn ungueltige Geometrie korrigiert werden musste
        if me.validate(verbose=False):
            errors.append(f"{o.name}: Mesh musste repariert werden")
        if len(me.polygons) == 0:
            errors.append(f"{o.name}: keine Flaechen")
        if not me.materials or any(m is None for m in me.materials):
            errors.append(f"{o.name}: Material fehlt")
        tris += lib.tri_count(o)
        for c in o.bound_box:
            w = o.matrix_world @ Vector(c)
            lo = Vector(map(min, lo, w))
            hi = Vector(map(max, hi, w))
        if o.parent is None and (abs(o.scale.x - 1) > 1e-4 or abs(o.rotation_euler.x) > 1e-4):
            errors.append(f"{o.name}: Transform nicht angewendet")
        if any(m.type == 'ARMATURE' for m in o.modifiers) and o.parent not in rigs:
            errors.append(f"{o.name}: Armature-Modifier ohne Parent-Rig")
    bones = 0
    for r in rigs:
        bones += len(r.data.bones)
        if not r.data.bones:
            errors.append(f"{r.name}: Armature ohne Bones")
        if (r.location.length > 1e-4) or abs(r.scale.x - 1) > 1e-4:
            errors.append(f"{r.name}: Rig nicht im Ursprung / skaliert")
    size = hi - lo
    extent = max(size) if meshes else 0
    center = (hi + lo) / 2
    if extent < 0.5 or extent > 6:
        errors.append(f"Bounding Box unplausibel ({extent:.2f})")
    if center.length > 1.2:
        errors.append(f"Pivot weit vom Zentrum ({center.length:.2f})")
    return {"errors": errors, "triangles": tris, "bones": bones, "extent": round(extent, 3),
            "center": [round(c, 3) for c in center], "meshes": len(meshes)}


def read_glb(path):
    data = open(path, "rb").read()
    magic, version, length = struct.unpack("<III", data[:12])
    assert magic == 0x46546C67, "keine GLB-Datei"
    jlen = struct.unpack("<I", data[12:16])[0]
    return json.loads(data[20:20 + jlen]), len(data)


def check_glb(path, required_anims):
    errors = []
    if not os.path.exists(path):
        return {"errors": ["Export fehlgeschlagen"], "bytes": 0}
    j, nbytes = read_glb(path)
    anims = [a["name"] for a in j.get("animations", [])]
    for a in required_anims:
        if a not in anims:
            errors.append(f"Animation fehlt: {a}")
    morph = sorted({n for m in j.get("meshes", []) for n in m.get("extras", {}).get("targetNames", [])})
    evo = [n["name"] for n in j.get("nodes", []) if n.get("name", "").startswith("EVO")]
    if not j.get("materials"):
        errors.append("GLB ohne Materialien")
    for a in j.get("animations", []):
        if not a.get("channels"):
            errors.append(f"Animation {a['name']} ohne Kanaele")
    return {"errors_glb": errors, "animations": anims, "bytes": nbytes, "morph_targets": morph,
            "evolution_nodes": evo, "skins": len(j.get("skins", [])),
            "materials": len(j.get("materials", []))}
