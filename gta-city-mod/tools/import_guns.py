"""Takes the gun models, textures, ammo icons and sounds from "Greenboy's Legendary Guns" (MIT License,
by GreenBoyGamerr) into this mod, so they come with it and the other mod is not needed.

    python3 tools/import_guns.py path/to/greenboys_legendary_guns-*.jar

Run it after gen_resources.py and gen_sounds.py (it overwrites the gun items, ammo textures and gun sounds).
The 3D models stay in their Bedrock/GeckoLib format (.geo.json); the mod draws them itself
(de.gtacity.client.render.GeoGunRenderer), GeckoLib is not needed either.
"""
import io
import json
import os
import sys
import zipfile

import numpy as np
import soundfile as sf
from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources")
ASSETS = os.path.join(ROOT, "assets", "gtacity")
SRC = "assets/greenboys_legendary_guns/"

# our item -> (geo model, texture, display settings of the original item)
GUNS = {
    "pistol": ("pistol", "pistol", "pistol.item"),                 # Glock 18
    "deagle": ("deagle", "deagle", "deagle.item"),                 # Desert Eagle
    "smg": ("mpfive", "mp5", "mpfive.item"),                       # MP5
    "carbine": ("newm4a1rifle", "m4a1new", "m4a1riflenew.item"),   # M4A1
    "ak47": ("newak47", "ak47", "newwnewak47.item"),               # AK-47
    "shotgun": ("shotgun", "m1014", "shartgun.item"),              # M1014
    "sniper": ("awm", "awm", "sniper.item"),                       # AWM
    "minigun": ("minigunn", "minigun", "minigunn.item"),           # Minigun
    "rpg": ("rpg", "rpg", "rpg.item"),                             # RPG-7
}
AMMO = {
    "pistol_ammo": "pistolbullet", "smg_ammo": "smgbullet", "rifle_ammo": "riflebullet",
    "shotgun_shells": "shotgunammo", "sniper_ammo": "niperbullet", "rocket": "rpgammo",
}
# our sound event -> original file
SOUNDS = {
    "gun_pistol": "g18", "gun_deagle": "revolvergunshot", "gun_smg": "mp40shot", "gun_rifle": "m4new",
    "gun_ak47": "aknew", "gun_shotgun": "m1014shoot", "gun_sniper": "sniper", "gun_minigun": "scar",
    "rpg_launch": "rpgsoundffect", "reload": "m16reload", "dry_fire": "empty",
}
SUBTITLES = {
    "gun_deagle": ("Desert Eagle fires", "Desert Eagle schießt"),
    "gun_ak47": ("AK-47 fires", "AK-47 schießt"),
}


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


def main(jar_path):
    jar = zipfile.ZipFile(jar_path)
    mods_toml = jar.read("META-INF/mods.toml").decode()
    if 'license="MIT' not in mods_toml:
        sys.exit("Unexpected licence in mods.toml - check before importing!")

    for item, (geo, texture, display) in GUNS.items():
        model = json.loads(jar.read(SRC + "geo/" + geo + ".geo.json"))
        idle = idle_pose(jar, geo)
        write_json(os.path.join(ASSETS, "geo", item + ".geo.json"), clean_model(model, idle))
        write_bytes(os.path.join(ASSETS, "textures", "item", "gun", item + ".png"),
                    jar.read(SRC + "textures/item/" + texture + ".png"))
        settings = json.loads(jar.read(SRC + "models/displaysettings/" + display + ".json"))
        # The special model needs a normal base model for the hand / GUI transforms and the break particles.
        write_json(os.path.join(ASSETS, "models", "item", item + "_3d.json"), {
            "gui_light": "front",
            "textures": {"particle": "gtacity:item/gun/" + item},
            "display": settings.get("display", {}),
        })
        write_json(os.path.join(ASSETS, "items", item + ".json"), {
            "model": {
                "type": "minecraft:special",
                "base": "gtacity:item/" + item + "_3d",
                "model": {"type": "gtacity:geo_gun", "geometry": "gtacity:geo/" + item + ".geo.json",
                          "texture": "gtacity:textures/item/gun/" + item + ".png"},
            }
        })

    for item, texture in AMMO.items():
        img = Image.open(io.BytesIO(jar.read(SRC + "textures/item/" + texture + ".png"))).convert("RGBA")
        if img.size[1] > img.size[0]:
            img = img.crop((0, 0, img.size[0], img.size[0]))  # animated strip: first frame
        img.save(os.path.join(ASSETS, "textures", "item", item + ".png"))

    sounds_path = os.path.join(ASSETS, "sounds.json")
    sounds = json.load(open(sounds_path, encoding="utf-8"))
    for name, original in SOUNDS.items():
        data, rate = sf.read(io.BytesIO(jar.read(SRC + "sounds/" + original + ".ogg")))
        if data.ndim > 1:
            data = data.mean(axis=1)  # positional sounds must be mono
        peak = float(np.max(np.abs(data))) or 1.0
        data = data / peak * 0.85
        sf.write(os.path.join(ASSETS, "sounds", name + ".ogg"), data.astype(np.float32), rate,
                 format="OGG", subtype="VORBIS")
        sounds[name] = {"sounds": ["gtacity:" + name], "subtitle": "subtitles.gtacity." + name}
    write_json(sounds_path, sounds)
    for lang, idx in (("en_us", 0), ("de_de", 1)):
        path = os.path.join(ASSETS, "lang", lang + ".json")
        table = json.load(open(path, encoding="utf-8"))
        for name, texts in SUBTITLES.items():
            table["subtitles.gtacity." + name] = texts[idx]
        write_json(path, table)

    write_bytes(os.path.join(ROOT, "CREDITS-greenboys-legendary-guns.txt"), CREDITS.encode())
    print("imported", len(GUNS), "guns,", len(AMMO), "ammo icons,", len(SOUNDS), "sounds")


def idle_pose(jar, geo):
    """Static scale of the bones in the gun's idle animation (the original hides some parts with scale 0)."""
    try:
        animations = json.loads(jar.read(SRC + "animations/" + geo + ".animation.json"))["animations"]
    except KeyError:
        return {}
    idle = next((a for name, a in animations.items() if name == "idle" or name.endswith(".idle")), {})
    pose = {}
    for bone, channels in idle.get("bones", {}).items():
        scale = channels.get("scale")
        if isinstance(scale, dict) and "vector" not in scale:  # keyframes: take the first one
            scale = scale[sorted(scale, key=float)[0]]
        if isinstance(scale, dict):
            scale = scale.get("vector")
        if isinstance(scale, (int, float)):
            scale = [scale] * 3
        if isinstance(scale, list) and all(isinstance(v, (int, float)) for v in scale):
            pose[bone] = scale
    return pose


def clean_model(model, idle):
    """Drops what only the original's first person animations need: the arms, the muzzle flash, the camera and
    the parts hidden in the idle pose. Other idle scales are kept as "gtacity_scale"."""
    geometry = model["minecraft:geometry"][0]
    bones = geometry["bones"]
    drop = {b["name"] for b in bones if "arm" in b["name"].lower() or b["name"] in ("flash", "camera")
            or idle.get(b["name"]) == [0, 0, 0]}
    changed = True
    while changed:  # children of dropped bones go too
        changed = False
        for b in bones:
            if b["name"] not in drop and b.get("parent") in drop:
                drop.add(b["name"])
                changed = True
    kept = []
    for b in bones:
        if b["name"] in drop:
            continue
        if b["name"] in idle and idle[b["name"]] != [1, 1, 1]:
            b["gtacity_scale"] = idle[b["name"]]
        kept.append(b)
    geometry["bones"] = kept
    return model


def write_bytes(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(data)


CREDITS = """Gun models, gun textures, ammo icons and gun sounds are taken from
"Greenboy's Legendary Guns" 8.7.5 by GreenBoyGamerr
https://www.curseforge.com/members/greenboyyoutuber/projects

They are used under the MIT License, as declared by that mod:

MIT License

Copyright (c) GreenBoyGamerr

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit
persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the
Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
"""

if __name__ == "__main__":
    main(sys.argv[1])
