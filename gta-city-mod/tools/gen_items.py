"""Pixel art for all items and blocks of the GTA City mod."""
import os

from PIL import Image

from pixel import Sprite

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "gtacity", "textures")


def save(img, kind, name):
    path = os.path.join(ROOT, kind, name + ".png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


# ====================================================================== guns (32x32, barrel to the right)

def pistol():
    s = Sprite(32)
    s.rect(9, 11, 25, 14, "steel")        # slide
    s.rect(9, 15, 24, 16, "poly")         # frame
    s.rect(26, 12, 26, 13, "dark")        # muzzle
    s.poly([(10, 17), (15, 17), (14, 26), (8, 26)], "poly")  # grip
    s.rect(16, 17, 19, 17, "poly")        # trigger guard top
    s.rect(19, 18, 19, 19, "poly")
    s.rect(16, 20, 19, 20, "poly")
    s.px(17, 18, "dark")                  # trigger
    for x in range(10, 15, 2):            # serrations
        s.px(x, 12, "metal")
        s.px(x, 13, "metal")
    s.px(10, 10, "dark")
    s.px(24, 10, "dark")
    s.rect(9, 22, 13, 22, "dark")         # grip texture
    s.rect(9, 24, 13, 24, "dark")
    return s.render()


def smg():
    s = Sprite(32)
    s.rect(8, 11, 24, 15, "dark")         # body
    s.rect(25, 12, 28, 13, "metal")       # barrel
    s.rect(29, 12, 29, 13, "black")
    s.rect(9, 10, 22, 10, "metal")        # top cover
    s.poly([(14, 16), (18, 16), (18, 27), (14, 27)], "poly")  # grip + mag
    s.rect(14, 26, 18, 27, "metal")
    s.rect(19, 16, 21, 16, "poly")        # guard
    s.rect(21, 17, 21, 18, "poly")
    s.px(19, 17, "dark")
    s.rect(2, 12, 7, 12, "metal")         # folding stock
    s.rect(2, 15, 7, 15, "metal")
    s.rect(2, 12, 3, 16, "metal")
    for x in range(10, 23, 3):
        s.px(x, 13, "black")
    return s.render()


def carbine():
    s = Sprite(32)
    s.poly([(1, 12), (7, 12), (7, 16), (1, 18)], "poly")  # stock
    s.rect(1, 12, 1, 18, "dark")
    s.rect(8, 11, 18, 15, "dark")         # receiver
    s.rect(9, 9, 17, 10, "poly")          # rail
    for x in range(9, 18, 2):
        s.px(x, 9, "dark")
    s.rect(19, 11, 26, 14, "olive")       # handguard
    for x in range(20, 26, 2):
        s.px(x, 13, "dark")
    s.rect(27, 12, 30, 12, "metal")       # barrel
    s.rect(31, 11, 31, 13, "black")       # flash hider
    s.rect(26, 9, 26, 10, "dark")         # front sight
    s.poly([(13, 16), (17, 16), (18, 23), (14, 23)], "metal")  # magazine
    s.poly([(9, 16), (12, 16), (11, 21), (8, 21)], "poly")    # grip
    s.px(12, 17, "black")
    return s.render()


def shotgun():
    s = Sprite(32)
    s.poly([(0, 12), (8, 12), (8, 15), (1, 18), (0, 18)], "wood")  # stock
    s.rect(9, 11, 15, 15, "dark")         # receiver
    s.rect(16, 11, 31, 12, "metal")       # barrel
    s.rect(16, 13, 28, 14, "dark")        # tube magazine
    s.rect(18, 13, 24, 15, "wood")        # pump
    for x in range(19, 24, 2):
        s.px(x, 14, "meat")
    s.rect(30, 10, 30, 10, "dark")        # bead sight
    s.poly([(9, 16), (12, 16), (11, 19), (9, 19)], "wood")
    s.px(12, 16, "black")
    return s.render()


def sniper():
    s = Sprite(32)
    s.poly([(0, 12), (8, 11), (9, 15), (4, 15), (1, 18), (0, 18)], "olive")  # stock
    s.rect(3, 10, 7, 10, "olive")         # cheek rest
    s.rect(9, 11, 16, 14, "dark")         # receiver
    s.rect(17, 12, 30, 12, "metal")       # barrel
    s.rect(31, 11, 31, 13, "black")       # muzzle brake
    s.rect(8, 6, 19, 8, "black")          # scope
    s.rect(7, 5, 8, 9, "black")
    s.rect(19, 5, 20, 9, "black")
    s.color(20, 6, (120, 220, 255))
    s.color(20, 7, (60, 160, 230))
    s.color(7, 7, (60, 160, 230))
    s.rect(11, 9, 11, 10, "dark")         # mounts
    s.rect(16, 9, 16, 10, "dark")
    s.rect(12, 15, 14, 17, "dark")        # magazine
    s.line(24, 13, 22, 18, "dark")        # bipod
    s.line(25, 13, 27, 18, "dark")
    s.px(13, 15, "black")
    return s.render()


def minigun():
    s = Sprite(32)
    s.rect(6, 9, 14, 18, "dark")          # motor housing
    s.rect(6, 13, 14, 13, "yellow")
    s.rect(15, 10, 30, 17, "metal")       # barrel cluster
    for y in (11, 13, 15):
        s.rect(15, y, 30, y, "black")
    s.rect(31, 10, 31, 17, "dark")
    s.rect(24, 9, 25, 18, "dark")         # barrel clamp
    s.rect(8, 6, 12, 6, "poly")           # carry handle
    s.rect(8, 7, 8, 8, "poly")
    s.rect(12, 7, 12, 8, "poly")
    s.rect(3, 19, 10, 25, "olive")        # ammo box
    s.rect(3, 21, 10, 21, "dark")
    s.line(10, 19, 14, 18, "brass")       # belt
    s.rect(2, 12, 5, 14, "poly")          # rear grip
    return s.render()


def rpg():
    s = Sprite(32)
    s.rect(1, 12, 25, 14, "olive")        # tube
    s.rect(0, 11, 2, 15, "dark")          # rear flare
    s.poly([(24, 13), (27, 9), (31, 13), (27, 17)], "green")  # warhead
    s.rect(26, 12, 30, 14, "olive")
    s.rect(31, 12, 31, 14, "dark")
    s.rect(13, 15, 14, 19, "poly")        # grips
    s.rect(18, 15, 19, 18, "poly")
    s.rect(15, 9, 16, 11, "dark")         # sight
    s.rect(6, 12, 7, 14, "dark")
    return s.render()


# ====================================================================== small items (16x16)

def ammo_box(bullet, tall=False):
    s = Sprite(16)
    s.rect(2, 9, 13, 14, "olive")
    s.rect(2, 9, 13, 9, "dark")
    s.rect(6, 11, 9, 12, "yellow")
    top = 3 if tall else 5
    for x in range(3, 13, 2):
        s.rect(x, top + 1, x, 8, bullet)
        s.px(x, top, "copper" if bullet == "brass" else "dark")
    return s.render()


def shells():
    s = Sprite(16)
    s.rect(2, 9, 13, 14, "olive")
    s.rect(2, 9, 13, 9, "dark")
    for x in (3, 6, 9, 12):
        s.rect(x, 4, x + 1, 7, "red")
        s.rect(x, 8, x + 1, 8, "brass")
    return s.render()


def rocket():
    s = Sprite(16)
    s.line(2, 13, 10, 5, "olive")
    s.line(3, 13, 11, 5, "olive")
    s.line(2, 12, 10, 4, "olive")
    s.poly([(9, 4), (13, 1), (14, 2), (11, 7)], "green")
    s.rect(1, 13, 3, 14, "dark")
    s.px(1, 11, "dark")
    s.px(4, 14, "dark")
    return s.render()


def grenade():
    s = Sprite(16)
    s.ellipse(3, 5, 12, 15, "olive")
    for y in (8, 11):
        s.rect(4, y, 11, y, "dark")
    s.rect(7, 5, 11, 5, "dark")
    s.rect(6, 3, 9, 4, "metal")
    s.line(9, 3, 12, 8, "metal")          # lever
    s.ellipse(3, 1, 6, 4, "steel")        # pin ring
    return s.render()


def knife():
    s = Sprite(16)
    s.line(5, 10, 13, 2, "steel")
    s.line(6, 10, 13, 3, "steel")
    s.line(6, 11, 14, 3, "metal")
    s.rect(4, 10, 6, 12, "dark")          # guard
    s.line(1, 14, 4, 11, "poly")
    s.line(2, 14, 5, 11, "poly")
    return s.render()


def bat():
    s = Sprite(16)
    s.line(1, 14, 12, 3, "wood")
    s.line(2, 14, 13, 3, "wood")
    s.line(4, 13, 14, 3, "wood")
    s.line(8, 9, 14, 3, "wood")
    s.line(9, 9, 14, 4, "wood")
    s.rect(1, 13, 3, 15, "poly")
    return s.render()


def cash():
    s = Sprite(16)
    s.rect(2, 12, 15, 14, "money")        # stacked notes below
    s.rect(1, 3, 14, 11, "money")         # top note
    s.rect(2, 4, 13, 10, "money")
    glyph = ["..#..", ".####", "#.#..", ".###.", "..#.#", "####.", "..#.."]
    for gy, row in enumerate(glyph):
        for gx, ch in enumerate(row):
            if ch == "#":
                s.color(6 + gx, 4 + gy, (236, 252, 226))
    s.rect(3, 5, 3, 9, "white")
    s.rect(12, 5, 12, 9, "white")
    return s.render()


def body_armor():
    s = Sprite(16)
    s.poly([(3, 2), (6, 2), (8, 4), (10, 2), (13, 2), (14, 6), (13, 15), (3, 15), (2, 6)], "navy")
    s.rect(4, 8, 12, 9, "dark")
    s.rect(4, 11, 12, 12, "dark")
    s.rect(6, 5, 10, 6, "white")
    s.color(7, 5, (30, 30, 40))
    s.color(9, 5, (30, 30, 40))
    return s.render()


def medkit():
    s = Sprite(16)
    s.rect(1, 4, 14, 14, "white")
    s.rect(5, 2, 10, 3, "gray")
    s.rect(7, 6, 8, 12, "red")
    s.rect(4, 8, 11, 10, "red")
    return s.render()


def drill():
    s = Sprite(16)
    s.rect(2, 4, 10, 8, "orange")
    s.rect(3, 9, 6, 14, "orange")
    s.rect(2, 13, 7, 14, "dark")
    s.rect(11, 5, 12, 7, "metal")
    s.line(13, 6, 15, 6, "steel")
    s.rect(3, 5, 5, 6, "yellow")
    return s.render()


def burger():
    s = Sprite(16)
    s.ellipse(1, 3, 14, 9, "bread")
    s.rect(1, 7, 14, 8, "lettuce")
    s.rect(1, 9, 14, 10, "meat")
    s.rect(2, 11, 13, 11, "cheese")
    s.rect(2, 12, 13, 13, "bread")
    s.color(5, 5, (250, 240, 200))
    s.color(9, 4, (250, 240, 200))
    s.color(11, 6, (250, 240, 200))
    return s.render()


def ecola():
    s = Sprite(16)
    s.rect(5, 2, 10, 14, "red")
    s.rect(5, 1, 10, 1, "steel")
    s.rect(5, 15, 10, 15, "steel")
    s.rect(6, 6, 9, 9, "white")
    s.color(7, 7, (184, 36, 40))
    s.color(8, 8, (184, 36, 40))
    return s.render()


def candy():
    s = Sprite(16)
    s.line(1, 12, 12, 3, "purple")
    s.line(2, 13, 13, 4, "purple")
    s.line(3, 13, 14, 4, "purple")
    s.line(2, 12, 13, 3, "purple")
    s.line(5, 10, 9, 6, "yellow")
    return s.render()


def car_key():
    s = Sprite(16)
    s.ellipse(1, 1, 7, 7, "poly")
    s.rect(3, 3, 5, 5, "red")
    s.line(6, 6, 13, 13, "steel")
    s.line(7, 6, 14, 13, "steel")
    s.rect(11, 12, 12, 14, "steel")
    return s.render()


# ====================================================================== blocks (16x16)

def noise_block(base, jitter=10, seed=1):
    import random
    rnd = random.Random(seed)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            d = rnd.randint(-jitter, jitter)
            img.putpixel((x, y), tuple(max(0, min(255, c + d)) for c in base) + (255,))
    return img


def draw(img, sprite_img):
    img.alpha_composite(sprite_img)
    return img


def elevator():
    img = noise_block((150, 156, 164), 6, 2)
    for y in range(16):
        img.putpixel((7, y), (60, 62, 68, 255))
        img.putpixel((8, y), (200, 204, 210, 255))
        img.putpixel((0, y), (70, 72, 78, 255))
        img.putpixel((15, y), (70, 72, 78, 255))
    for x in range(16):
        img.putpixel((x, 0), (70, 72, 78, 255))
        img.putpixel((x, 15), (70, 72, 78, 255))
    for x in range(5, 11):
        img.putpixel((x, 1), (20, 20, 24, 255))
        img.putpixel((x, 2), (20, 20, 24, 255))
    img.putpixel((6, 1), (255, 200, 60, 255))
    img.putpixel((9, 2), (255, 200, 60, 255))
    return img


def vault():
    img = noise_block((120, 126, 134), 5, 3)
    s = Sprite(16)
    s.ellipse(1, 1, 14, 14, "steel")
    s.ellipse(4, 4, 11, 11, "metal")
    s.line(7, 2, 7, 13, "dark")
    s.line(2, 7, 13, 7, "dark")
    s.line(3, 3, 12, 12, "dark")
    s.line(12, 3, 3, 12, "dark")
    s.rect(6, 6, 8, 8, "yellow")
    return draw(img, s.render())


def counter_side(base, trim):
    img = noise_block(base, 5, 4)
    for x in range(16):
        img.putpixel((x, 0), (*trim, 255))
        img.putpixel((x, 1), (*trim, 255))
        img.putpixel((x, 15), (30, 30, 34, 255))
    for y in range(3, 14):
        img.putpixel((0, y), (40, 40, 44, 255))
        img.putpixel((15, y), (40, 40, 44, 255))
    return img


def weapon_counter_top():
    img = noise_block((170, 210, 220), 6, 5)
    small = pistol().resize((16, 16), Image.NEAREST)
    img.alpha_composite(small)
    return img


def store_counter_top():
    img = noise_block((225, 228, 232), 4, 6)
    s = Sprite(16)
    s.rect(3, 5, 12, 12, "dark")
    s.rect(4, 3, 11, 5, "poly")
    s.rect(5, 6, 10, 7, "green")
    for x in range(4, 12, 2):
        s.rect(x, 9, x, 9, "white")
        s.rect(x, 11, x, 11, "white")
    return draw(img, s.render())


def car_counter_top():
    img = noise_block((36, 36, 40), 4, 7)
    img.alpha_composite(car_key())
    return img


def gas_pump():
    img = noise_block((190, 40, 44), 6, 8)
    s = Sprite(16)
    s.rect(3, 2, 12, 6, "dark")
    for x in range(4, 12):
        for y in range(3, 6):
            s.color(x, y, (110, 230, 120))
    s.rect(3, 9, 12, 13, "white")
    s.rect(6, 10, 9, 12, "yellow")
    s.rect(13, 7, 14, 14, "black")
    return draw(img, s.render(outline=False))


def atm():
    img = noise_block((90, 96, 106), 5, 9)
    s = Sprite(16)
    s.rect(2, 1, 13, 2, "red")
    for x in range(3, 13):
        for y in range(4, 8):
            s.color(x, y, (60, 200, 110))
    s.color(4, 5, (20, 90, 50))
    s.color(5, 5, (20, 90, 50))
    for y in (9, 11):
        for x in range(4, 11, 2):
            s.rect(x, y, x, y, "white")
    s.rect(4, 13, 11, 13, "black")
    s.rect(12, 9, 13, 11, "steel")
    return draw(img, s.render(outline=False))


def main():
    guns = {"pistol": pistol, "smg": smg, "carbine": carbine, "shotgun": shotgun, "sniper": sniper,
            "minigun": minigun, "rpg": rpg}
    for name, fn in guns.items():
        save(fn(), "item", name)
    save(ammo_box("brass"), "item", "pistol_ammo")
    save(ammo_box("copper"), "item", "smg_ammo")
    save(ammo_box("brass", tall=True), "item", "rifle_ammo")
    save(shells(), "item", "shotgun_shells")
    save(ammo_box("steel", tall=True), "item", "sniper_ammo")
    save(rocket(), "item", "rocket")
    save(grenade(), "item", "grenade")
    save(knife(), "item", "knife")
    save(bat(), "item", "baseball_bat")
    save(cash(), "item", "cash")
    save(body_armor(), "item", "body_armor")
    save(medkit(), "item", "medkit")
    save(drill(), "item", "thermal_drill")
    save(burger(), "item", "burger")
    save(ecola(), "item", "ecola")
    save(candy(), "item", "candy_bar")
    save(car_key(), "item", "car_key")

    save(elevator(), "block", "elevator")
    save(vault(), "block", "bank_vault")
    save(counter_side((50, 50, 56), (190, 40, 44)), "block", "weapon_counter_side")
    save(weapon_counter_top(), "block", "weapon_counter_top")
    save(counter_side((230, 232, 236), (60, 170, 70)), "block", "store_counter_side")
    save(store_counter_top(), "block", "store_counter_top")
    save(counter_side((30, 30, 34), (200, 200, 206)), "block", "car_counter_side")
    save(car_counter_top(), "block", "car_counter_top")
    save(gas_pump(), "block", "gas_pump")
    save(atm(), "block", "atm")


if __name__ == "__main__":
    main()
