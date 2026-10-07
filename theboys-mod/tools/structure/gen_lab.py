#!/usr/bin/env python3
"""Generates the Vought lab structure template (data/theboys/structure/vought_lab/main.nbt).

Run:  python3 tools/structure/gen_lab.py
The NBT writer is self-contained (no third-party libs).
"""
import gzip
import os
import random
import struct

DATA_VERSION = 5023  # Minecraft 26.3
OUT = os.path.join(os.path.dirname(__file__), '..', '..', 'src', 'main', 'resources',
                   'data', 'theboys', 'structure', 'vought_lab', 'main.nbt')

# ---------------------------------------------------------------- NBT writer
TAG_END, TAG_BYTE, TAG_INT, TAG_STRING, TAG_LIST, TAG_COMPOUND = 0, 1, 3, 8, 9, 10


class Int(int):
    pass


def _name(s):
    b = s.encode('utf-8')
    return struct.pack('>H', len(b)) + b


def _tag_type(v):
    if isinstance(v, dict):
        return TAG_COMPOUND
    if isinstance(v, list):
        return TAG_LIST
    if isinstance(v, str):
        return TAG_STRING
    if isinstance(v, bool):
        return TAG_BYTE
    if isinstance(v, int):
        return TAG_INT
    raise TypeError(v)


def _payload(v):
    t = _tag_type(v)
    if t == TAG_COMPOUND:
        out = b''
        for k, x in v.items():
            out += bytes([_tag_type(x)]) + _name(k) + _payload(x)
        return out + bytes([TAG_END])
    if t == TAG_LIST:
        et = _tag_type(v[0]) if v else TAG_END
        return bytes([et]) + struct.pack('>i', len(v)) + b''.join(_payload(x) for x in v)
    if t == TAG_STRING:
        return _name(v)
    if t == TAG_BYTE:
        return struct.pack('>b', int(v))
    return struct.pack('>i', v)


def write_nbt(path, root):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    data = bytes([TAG_COMPOUND]) + _name('') + _payload(root)
    with gzip.open(path, 'wb') as f:
        f.write(data)


# ---------------------------------------------------------------- layout
SX, SY, SZ = 17, 7, 13   # x, y, z
blocks = {}              # (x,y,z) -> (state string, nbt or None)


def put(x, y, z, state, nbt=None):
    blocks[(x, y, z)] = (state, nbt)


def fill(x0, y0, z0, x1, y1, z1, state):
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            for z in range(z0, z1 + 1):
                put(x, y, z, state)


rng = random.Random(1337)

# Air everywhere inside so the lab clears the terrain it is built into.
fill(0, 0, 0, SX - 1, SY - 1, SZ - 1, 'minecraft:air')

# Floor + foundation
fill(0, 0, 0, SX - 1, 0, SZ - 1, 'theboys:lab_floor')

# Walls: steel band at the bottom, white tiles above, concrete pillars at the corners
for y in range(1, 6):
    wall = 'theboys:vought_panel' if y == 1 else 'theboys:lab_tiles'
    for x in range(SX):
        put(x, y, 0, wall)
        put(x, y, SZ - 1, wall)
    for z in range(SZ):
        put(0, y, z, wall)
        put(SX - 1, y, z, wall)
for (x, z) in [(0, 0), (SX - 1, 0), (0, SZ - 1), (SX - 1, SZ - 1), (8, 0), (8, SZ - 1)]:
    fill(x, 1, z, x, 5, z, 'minecraft:light_gray_concrete')

# Roof with recessed lights
fill(0, 6, 0, SX - 1, 6, SZ - 1, 'theboys:vought_panel')
for x in range(3, SX - 2, 4):
    for z in range(3, SZ - 2, 3):
        put(x, 6, z, 'minecraft:sea_lantern')

# Windows (containment glass) on the long sides
for x in (3, 4, 12, 13):
    for y in (2, 3):
        put(x, y, 0, 'theboys:containment_glass')
for z in (3, 4, 8, 9):
    for y in (2, 3):
        put(0, y, z, 'theboys:containment_glass')

# Entrance: open doorway, 3 wide and 3 high
fill(7, 1, 0, 9, 3, 0, 'minecraft:air')
fill(6, 4, 0, 10, 4, 0, 'theboys:vought_panel')

# --- Compound V storage: two rows of tall lab fridges against the back wall
for x in range(1, 7):
    put(x, 1, SZ - 2, 'theboys:v_fridge[facing=north,stocked=true]')
    put(x, 2, SZ - 2, 'theboys:v_fridge[facing=north,stocked=true]')
fill(1, 3, SZ - 2, 6, 3, SZ - 2, 'minecraft:smooth_quartz_slab[type=bottom,waterlogged=false]')

# --- Lab benches in the middle
for x in range(3, 8):
    put(x, 1, 5, 'minecraft:smooth_stone_slab[type=top,waterlogged=false]')
    put(x, 1, 7, 'minecraft:smooth_stone_slab[type=top,waterlogged=false]')
put(3, 2, 5, 'minecraft:brewing_stand[has_bottle_0=true,has_bottle_1=false,has_bottle_2=true]')
put(5, 2, 5, 'minecraft:end_rod[facing=up]')
put(7, 2, 5, 'minecraft:brewing_stand[has_bottle_0=false,has_bottle_1=true,has_bottle_2=false]')
put(4, 2, 7, 'minecraft:light_blue_stained_glass_pane[east=false,north=false,south=false,waterlogged=false,west=false]')
put(6, 2, 7, 'minecraft:end_rod[facing=up]')
put(2, 1, 6, 'minecraft:cauldron')
put(8, 1, 6, 'minecraft:water_cauldron[level=2]')

# --- Supply chest + barrel with lab loot
put(1, 1, 1, 'minecraft:chest[facing=east,type=single,waterlogged=false]',
    {'id': 'minecraft:chest', 'LootTable': 'theboys:chests/vought_lab'})
put(1, 1, 2, 'minecraft:barrel[facing=up,open=false]',
    {'id': 'minecraft:barrel', 'LootTable': 'theboys:chests/vought_lab_supplies'})
put(1, 2, 1, 'minecraft:iron_trapdoor[facing=east,half=bottom,open=false,powered=false,waterlogged=false]')

# --- Containment cells (right side)
for zc in (2, 7):
    # glass front at x=11, cell spans x=12..15, z=zc..zc+3
    for z in range(zc, zc + 4):
        for y in range(1, 5):
            put(11, y, z, 'theboys:containment_glass')
    for x in range(11, SX - 1):
        for y in range(1, 5):
            put(x, y, zc - 1 if zc == 2 else zc - 1, 'theboys:vought_panel' if y == 1 else 'theboys:lab_tiles')
    put(11, 1, zc + 1, 'minecraft:iron_door[facing=west,half=lower,hinge=left,open=false,powered=false]')
    put(11, 2, zc + 1, 'minecraft:iron_door[facing=west,half=upper,hinge=left,open=false,powered=false]')
    put(10, 2, zc + 2, 'minecraft:stone_button[face=wall,facing=west,powered=false]')
    # chains hanging from the ceiling and "blood" on the floor
    put(14, 5, zc + 1, 'minecraft:iron_chain[axis=y,waterlogged=false]')
    put(14, 4, zc + 1, 'minecraft:iron_chain[axis=y,waterlogged=false]')
    for _ in range(4):
        x = rng.randint(12, 15)
        z = rng.randint(zc, zc + 3)
        put(x, 1, z, 'minecraft:redstone_wire[east=none,north=none,power=0,south=none,west=none]')
    put(15, 1, zc + 3, 'minecraft:skeleton_skull[powered=false,rotation=6]')
# wall closing off the cells at the back
for x in range(11, SX - 1):
    for y in range(1, 5):
        put(x, y, 11, 'theboys:vought_panel' if y == 1 else 'theboys:lab_tiles')

# --- Vought branding: a blue "V" on the back wall
for (x, y) in [(10, 5), (11, 4), (12, 3), (13, 4), (14, 5)]:
    put(x, y, SZ - 1, 'minecraft:blue_concrete')

# --- palette / block list
palette = []
index = {}


def state_tag(s):
    if '[' in s:
        name, props = s[:-1].split('[', 1)
        p = dict(kv.split('=') for kv in props.split(','))
        return {'Name': name, 'Properties': p}
    return {'Name': s}


out_blocks = []
for (x, y, z), (state, nbt) in sorted(blocks.items()):
    if not (0 <= x < SX and 0 <= y < SY and 0 <= z < SZ):
        continue
    if state not in index:
        index[state] = len(palette)
        palette.append(state_tag(state))
    b = {'pos': [Int(x), Int(y), Int(z)], 'state': Int(index[state])}
    if nbt:
        b['nbt'] = nbt
    out_blocks.append(b)

root = {
    'DataVersion': Int(DATA_VERSION),
    'size': [Int(SX), Int(SY), Int(SZ)],
    'palette': palette,
    'blocks': out_blocks,
    'entities': [],
}
write_nbt(OUT, root)
print('wrote', os.path.normpath(OUT), len(out_blocks), 'blocks,', len(palette), 'states')
