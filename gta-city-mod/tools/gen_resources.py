"""Writes item/block model JSONs, blockstates, language files and the world preset."""
import json
import os

RES = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "gtacity")
NS = "gtacity"


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


GUNS = ["pistol", "smg", "carbine", "shotgun", "sniper", "minigun", "rpg"]
ITEMS = ["pistol_ammo", "smg_ammo", "rifle_ammo", "shotgun_shells", "sniper_ammo", "rocket", "grenade", "body_armor",
         "medkit", "thermal_drill", "cash", "car_key", "burger", "ecola", "candy_bar"]
HANDHELD = ["knife", "baseball_bat"]
BLOCKS_ALL = ["elevator", "bank_vault", "gas_pump", "atm"]
COUNTERS = ["weapon_counter", "store_counter", "car_counter"]

NAMES = {
    "pistol": ("Pistol", "Pistole"),
    "smg": ("Micro SMG", "Micro-SMG"),
    "carbine": ("Carbine Rifle", "Karabiner"),
    "shotgun": ("Pump Shotgun", "Pumpgun"),
    "sniper": ("Sniper Rifle", "Scharfschützengewehr"),
    "minigun": ("Minigun", "Minigun"),
    "rpg": ("RPG", "Raketenwerfer"),
    "pistol_ammo": ("Pistol Ammo", "Pistolenmunition"),
    "smg_ammo": ("SMG Ammo", "SMG-Munition"),
    "rifle_ammo": ("Rifle Ammo", "Gewehrmunition"),
    "shotgun_shells": ("Shotgun Shells", "Schrotpatronen"),
    "sniper_ammo": ("Sniper Ammo", "Scharfschützenmunition"),
    "rocket": ("Rocket", "Rakete"),
    "grenade": ("Grenade", "Granate"),
    "knife": ("Knife", "Messer"),
    "baseball_bat": ("Baseball Bat", "Baseballschläger"),
    "body_armor": ("Body Armor", "Schutzweste"),
    "medkit": ("Medkit", "Medikit"),
    "thermal_drill": ("Thermal Drill", "Thermobohrer"),
    "cash": ("Cash", "Bargeld"),
    "car_key": ("Car Key", "Autoschlüssel"),
    "burger": ("Burger", "Burger"),
    "ecola": ("eCola", "eCola"),
    "candy_bar": ("Candy Bar", "Schokoriegel"),
}
BLOCK_NAMES = {
    "elevator": ("Elevator", "Aufzug"),
    "bank_vault": ("Bank Vault", "Banktresor"),
    "weapon_counter": ("Ammu-Nation Counter", "Ammu-Nation-Theke"),
    "store_counter": ("24/7 Counter", "24/7-Kasse"),
    "car_counter": ("Car Dealer Desk", "Autohaus-Schalter"),
    "gas_pump": ("Gas Pump", "Zapfsäule"),
    "atm": ("ATM", "Geldautomat"),
}
ENTITY_NAMES = {
    "pedestrian": ("Pedestrian", "Passant"),
    "police": ("Police Officer", "Polizist"),
    "car": ("Car", "Auto"),
    "helicopter": ("Police Helicopter", "Polizeihubschrauber"),
    "rocket": ("Rocket", "Rakete"),
    "grenade": ("Grenade", "Granate"),
}

# The gun sprites point right (muzzle at +x, grip bottom left). Rotating 90 degrees around Y turns +x into -z:
# forward in first person, and along the arm towards the hand in third person - so a hanging arm carries the gun
# pointed at the ground and an arm raised to aim points it forward. Minecraft mirrors the left hand itself
# (it negates the Y/Z rotation and the X translation), so the left hand entries are the negated right ones.
GUN_DISPLAY = {
    "thirdperson_righthand": {"rotation": [0, 90, 0], "translation": [0, 3.5, -1.5], "scale": [0.85, 0.85, 0.85]},
    "thirdperson_lefthand": {"rotation": [0, -90, 0], "translation": [0, 3.5, -1.5], "scale": [0.85, 0.85, 0.85]},
    "firstperson_righthand": {"rotation": [0, 90, 2], "translation": [1.5, 3.2, 1.0], "scale": [0.85, 0.85, 0.85]},
    "firstperson_lefthand": {"rotation": [0, -90, -2], "translation": [1.5, 3.2, 1.0], "scale": [0.85, 0.85, 0.85]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
    "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
    "gui": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
}


def item_definition(name, model):
    write(os.path.join(ASSETS, "items", name + ".json"), {"model": {"type": "minecraft:model", "model": model}})


def main():
    write(os.path.join(ASSETS, "models", "item", "gun_base.json"),
          {"parent": "minecraft:item/generated", "display": GUN_DISPLAY})
    for g in GUNS:
        write(os.path.join(ASSETS, "models", "item", g + ".json"),
              {"parent": NS + ":item/gun_base", "textures": {"layer0": NS + ":item/" + g}})
        item_definition(g, NS + ":item/" + g)
    for i in ITEMS:
        write(os.path.join(ASSETS, "models", "item", i + ".json"),
              {"parent": "minecraft:item/generated", "textures": {"layer0": NS + ":item/" + i}})
        item_definition(i, NS + ":item/" + i)
    for i in HANDHELD:
        write(os.path.join(ASSETS, "models", "item", i + ".json"),
              {"parent": "minecraft:item/handheld", "textures": {"layer0": NS + ":item/" + i}})
        item_definition(i, NS + ":item/" + i)

    for b in BLOCKS_ALL:
        write(os.path.join(ASSETS, "models", "block", b + ".json"),
              {"parent": "minecraft:block/cube_all", "textures": {"all": NS + ":block/" + b}})
    for b in COUNTERS:
        write(os.path.join(ASSETS, "models", "block", b + ".json"),
              {"parent": "minecraft:block/cube_bottom_top", "textures": {
                  "top": NS + ":block/" + b + "_top", "side": NS + ":block/" + b + "_side",
                  "bottom": NS + ":block/" + b + "_side"}})
    for b in BLOCKS_ALL + COUNTERS:
        write(os.path.join(ASSETS, "blockstates", b + ".json"), {"variants": {"": {"model": NS + ":block/" + b}}})
        item_definition(b, NS + ":block/" + b)

    en, de = {}, {}
    for key, (e, d) in NAMES.items():
        en["item.gtacity." + key] = e
        de["item.gtacity." + key] = d
    for key, (e, d) in BLOCK_NAMES.items():
        en["block.gtacity." + key] = e
        de["block.gtacity." + key] = d
    for key, (e, d) in ENTITY_NAMES.items():
        en["entity.gtacity." + key] = e
        de["entity.gtacity." + key] = d
    extra = {
        "key.gtacity.reload": ("Reload", "Nachladen"),
        "key.gtacity.horn": ("Car Horn", "Hupe"),
        "key.gtacity.claim_car": ("Keep This Car", "Auto behalten (wird deins)"),
        "key.gtacity.bring_car": ("Bring My Car", "Eigenes Auto herbeiholen"),
        "key.gtacity.map": ("Map, Jobs, Garage and Villas", "Karte, Jobs, Garage und Villen"),
        "key.category.gtacity.keys": ("GTA City", "GTA City"),
        "generator.minecraft.normal": ("GTA City", "GTA City"),
    }
    for key, (e, d) in extra.items():
        en[key] = e
        de[key] = d
    write(os.path.join(ASSETS, "lang", "en_us.json"), en)
    write(os.path.join(ASSETS, "lang", "de_de.json"), de)

    # Replace the default world type: every new world is the city.
    preset = {
        "dimensions": {
            "minecraft:overworld": {
                "type": "minecraft:overworld",
                "generator": {
                    "type": NS + ":city",
                    "biome_source": {"type": "minecraft:fixed", "biome": "minecraft:the_void"},
                },
            },
            "minecraft:the_nether": {
                "type": "minecraft:the_nether",
                "generator": {
                    "type": "minecraft:noise",
                    "biome_source": {"type": "minecraft:multi_noise", "preset": "minecraft:nether"},
                    "settings": "minecraft:nether",
                },
            },
            "minecraft:the_end": {
                "type": "minecraft:the_end",
                "generator": {
                    "type": "minecraft:noise",
                    "biome_source": {"type": "minecraft:the_end"},
                    "settings": "minecraft:end",
                },
            },
        }
    }
    write(os.path.join(RES, "data", "minecraft", "worldgen", "world_preset", "normal.json"), preset)


if __name__ == "__main__":
    main()
