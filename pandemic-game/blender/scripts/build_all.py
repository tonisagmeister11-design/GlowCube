"""PANDEMIC – Blender-Asset-Pipeline.

Erzeugt alle Pathogen-Modelle als echte Blender-Objekte (Meshes, Materialien,
Armatures, Shape Keys, Keyframe-Animationen), speichert PANDEMIC_GAME.blend,
exportiert GLB-Dateien nach assets/models/ und prueft jedes Ergebnis.

Aufruf (Blender als Python-Modul oder ueber die Blender-Binary):
    python  blender/scripts/build_all.py [--only virus,fungus] [--render]
    blender -b --python blender/scripts/build_all.py -- [--only virus] [--render]
"""
import importlib
import json
import os
import sys
import time

import bpy

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
sys.path.insert(0, HERE)

import lib  # noqa: E402
import qa  # noqa: E402

MODELS_DIR = os.path.join(ROOT, "assets", "models")
RAW_DIR = os.path.join(ROOT, "blender", "export")
BLEND_DIR = os.path.join(ROOT, "blender")
BACKUP_DIR = os.path.join(BLEND_DIR, "backups")
PREVIEW_DIR = os.path.join(BLEND_DIR, "previews")

# key, Collection-Name, Dateiname, Modul
PATHOGENS = [
    ("bacteria", "BACTERIA", "bacterium.glb", "bacteria"),
    ("virus", "VIRUS", "virus.glb", "virus"),
    ("fungus", "FUNGUS", "fungus.glb", "fungus"),
    ("parasite", "PARASITE", "parasite.glb", "parasite"),
    ("prion", "PRION", "prion.glb", "prion"),
    ("nanovirus", "NANOVIRUS", "nanovirus.glb", "nanovirus"),
    ("bioweapon", "BIOWEAPON", "bioweapon.glb", "bioweapon"),
    ("neurax", "NEURAX", "neurax_worm.glb", "neurax"),
    ("necroa", "NECROA", "necroa_virus.glb", "necroa"),
    ("simian", "SIMIAN", "simian_flu.glb", "simian"),
    ("shadow", "SHADOW", "shadow_plague.glb", "shadow"),
    ("xenolith", "XENOLITH", "xenolith.glb", "xenolith"),
]
EXTRA = [("human", "HUMAN_HOLOGRAM", "human_body.glb", "human")]


def parse_args():
    argv = sys.argv
    if "--" in argv:
        argv = argv[argv.index("--") + 1:]
    else:
        argv = argv[1:]
    only = None
    render = "--render" in argv
    if "--only" in argv:
        only = set(argv[argv.index("--only") + 1].split(","))
    return only, render


def setup_scene():
    sc = lib.reset_scene()
    root = sc.collection
    cols = {}
    for name in ("PATHOGENS", "ENVIRONMENT", "CAMERAS", "LIGHTS", "EFFECTS", "EXPORT"):
        cols[name] = lib.collection(name, root)
    # Kamera
    cam_data = bpy.data.cameras.new("PreviewCam")
    cam_data.lens = 50
    cam = bpy.data.objects.new("PreviewCam", cam_data)
    cols["CAMERAS"].objects.link(cam)
    cam.location = (3.1, -3.6, 1.7)
    target = bpy.data.objects.new("CamTarget", None)
    cols["CAMERAS"].objects.link(target)
    con = cam.constraints.new('TRACK_TO')
    con.target = target
    con.track_axis = 'TRACK_NEGATIVE_Z'
    con.up_axis = 'UP_Y'
    sc.camera = cam
    # Licht: Key (warm), Rim (rot), Fill (kuehl)
    for name, loc, energy, color in (
        ("KeyLight", (3, -2, 4), 380, (1.0, 0.85, 0.75)),
        ("RimLight", (-3, 3, 1.5), 700, (1.0, 0.12, 0.08)),
        ("FillLight", (-2, -3, -1), 120, (0.6, 0.7, 1.0)),
    ):
        ld = bpy.data.lights.new(name, 'AREA')
        ld.energy = energy
        ld.color = color
        ld.size = 2.5
        lo = bpy.data.objects.new(name, ld)
        cols["LIGHTS"].objects.link(lo)
        lo.location = loc
        c = lo.constraints.new('TRACK_TO')
        c.target = target
        c.track_axis = 'TRACK_NEGATIVE_Z'
        c.up_axis = 'UP_Y'
    # Umgebung: dunkles Labor-Rot
    world = bpy.data.worlds.new("LabWorld")
    world.use_nodes = True
    bg = world.node_tree.nodes.get("Background")
    bg.inputs[0].default_value = (0.05, 0.004, 0.006, 1)
    bg.inputs[1].default_value = 0.6
    sc.world = world
    # Environment-Platzhalter: Petrischale als Referenzboden fuer Vorschau
    import bmesh
    bm = bmesh.new()
    bmesh.ops.create_circle(bm, cap_ends=True, radius=4.0, segments=48)
    me = bpy.data.meshes.new("LabFloor")
    bm.to_mesh(me)
    bm.free()
    floor = bpy.data.objects.new("LabFloor", me)
    floor.location = (0, 0, -1.6)
    cols["ENVIRONMENT"].objects.link(floor)
    floor.data.materials.append(lib.material("LabFloorMat", (0.08, 0.01, 0.01), rough=0.3, coat=0.5))
    # Effekt-Emitter (Referenz fuer die Partikel im Browser)
    fx = bpy.data.objects.new("FX_ParticleOrigin", None)
    fx.empty_display_type = 'SPHERE'
    cols["EFFECTS"].objects.link(fx)
    return sc, cols


def render_preview(sc, cols, col_name, out_path, extent, center):
    vl = bpy.context.view_layer
    pcol = vl.layer_collection.children["PATHOGENS"]
    for lc in pcol.children:
        lc.exclude = lc.name != col_name
    env = vl.layer_collection.children["ENVIRONMENT"]
    for lc in env.children:
        lc.exclude = lc.name != col_name
    sc.render.engine = 'CYCLES'
    sc.cycles.device = 'CPU'
    sc.cycles.samples = 24
    sc.cycles.use_denoising = False
    sc.render.resolution_x = 420
    sc.render.resolution_y = 420
    sc.render.film_transparent = False
    cam = sc.camera
    from mathutils import Vector
    tgt = bpy.data.objects["CamTarget"]
    tgt.location = Vector(center)
    d = max(extent, 0.8)
    cam.location = Vector(center) + Vector((1.0, -1.25, 0.55)).normalized() * (d * 1.75 + 0.4)
    bpy.data.objects["LabFloor"].location.z = center[2] - d * 0.75
    evo = [o for o in bpy.data.collections[col_name].all_objects if o.name.startswith("EVO")]
    # nur die Idle-Animation fuer die Vorschau auswerten (Ruhepose als Basis)
    for o in bpy.data.collections[col_name].all_objects:
        if o.type == 'ARMATURE':
            for pb in o.pose.bones:
                pb.location = (0, 0, 0)
                pb.rotation_euler = (0, 0, 0)
                pb.scale = (1, 1, 1)
    for o in bpy.data.collections[col_name].all_objects:
        ids = [o] + ([o.data.shape_keys] if o.type == 'MESH' and o.data.shape_keys else [])
        for idb in ids:
            if idb.animation_data:
                for tr in idb.animation_data.nla_tracks:
                    tr.mute = not tr.name.endswith("_idle")
    sc.frame_set(1)
    for o in evo:
        o.hide_render = True
    sc.render.filepath = out_path
    bpy.ops.render.render(write_still=True)
    if evo:
        for o in evo:
            o.hide_render = False
        sc.render.filepath = out_path.replace(".png", "_evolved.png")
        bpy.ops.render.render(write_still=True)
    for o in bpy.data.collections[col_name].all_objects:
        ids = [o] + ([o.data.shape_keys] if o.type == 'MESH' and o.data.shape_keys else [])
        for idb in ids:
            if idb.animation_data:
                for tr in idb.animation_data.nla_tracks:
                    tr.mute = False
    for lc in pcol.children:
        lc.exclude = False
    for lc in env.children:
        lc.exclude = False


def main():
    only, render = parse_args()
    os.makedirs(MODELS_DIR, exist_ok=True)
    os.makedirs(RAW_DIR, exist_ok=True)
    os.makedirs(BACKUP_DIR, exist_ok=True)
    os.makedirs(PREVIEW_DIR, exist_ok=True)
    sc, cols = setup_scene()
    manifest_path = os.path.join(MODELS_DIR, "manifest.json")
    manifest = {}
    if os.path.exists(manifest_path):
        manifest = json.load(open(manifest_path))
    report = []
    entries = PATHOGENS + EXTRA
    for key, col_name, fname, modname in entries:
        if only and key not in only:
            continue
        t0 = time.time()
        parent = cols["PATHOGENS"] if (key, col_name, fname, modname) in PATHOGENS else cols["ENVIRONMENT"]
        col = lib.collection(col_name, parent)
        mod = importlib.import_module("pathogens." + modname)
        sc.frame_set(1)
        info = mod.build(col) or {}
        sc.frame_set(1)
        # Hilfsattribute entfernen, bevor exportiert wird
        for o in lib.collection_objects(col):
            if o.type == 'MESH':
                lib.strip_helper_attributes(o)
        path = os.path.join(RAW_DIR, fname)
        lib.export_glb(col, path)
        result = qa.check_blender(col, getattr(mod, "ANIMATIONS", []))
        glb = qa.check_glb(path, getattr(mod, "ANIMATIONS", []))
        result["errors"] += glb.pop("errors_glb")
        result.update(glb)
        result["seconds"] = round(time.time() - t0, 1)
        report.append((key, result))
        manifest[key] = {
            "file": "assets/models/" + fname,
            "collection": col_name,
            "animations": glb.get("animations", []),
            "triangles": result["triangles"],
            "bones": result["bones"],
            "rawBytes": glb.get("bytes"),
            "morphTargets": glb.get("morph_targets", []),
            "evolutionNodes": glb.get("evolution_nodes", []),
            "idle": getattr(mod, "IDLE", None),
        }
        print(f"[{key}] {json.dumps(result)}")
        # Sicherungskopie nach jedem Modell
        bpy.ops.wm.save_as_mainfile(filepath=os.path.join(BACKUP_DIR, f"PANDEMIC_GAME_{key}.blend"),
                                    compress=True, copy=True)
        if render:
            render_preview(sc, cols, col_name, os.path.join(PREVIEW_DIR, key + ".png"),
                           result.get("extent", 1.0), result.get("center", (0, 0, 0)))
    json.dump(manifest, open(manifest_path, "w"), indent=2)
    if not only:
        bpy.ops.wm.save_as_mainfile(filepath=os.path.join(BLEND_DIR, "PANDEMIC_GAME.blend"), compress=True)
    failed = [k for k, r in report if r["errors"]]
    print("\n==== QA REPORT ====")
    for k, r in report:
        status = "OK " if not r["errors"] else "ERR"
        print(f"{status} {k:10s} tris={r['triangles']:6d} bones={r['bones']:3d} "
              f"anims={len(r.get('animations', []))} size={r.get('bytes', 0) / 1024:.0f}KB "
              f"{'; '.join(r['errors'])}")
    if failed:
        sys.exit(1)


if __name__ == "__main__":
    main()
