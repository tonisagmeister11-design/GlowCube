#!/usr/bin/env python3
"""Generates the supe hunter camp template (data/theboys/structure/hunter_camp/main.nbt).

Run:  python3 tools/structure/gen_camp.py
A fenced camp with watchtowers, tents, a campfire, a shooting range, supply chests and six hunters.
"""
import gzip
import os
import random
import struct

DATA_VERSION = 5023
OUT = os.path.join(os.path.dirname(__file__), '..', '..', 'src', 'main', 'resources',
                   'data', 'theboys', 'structure', 'hunter_camp', 'main.nbt')

TAG_END, TAG_BYTE, TAG_INT, TAG_DOUBLE, TAG_STRING, TAG_LIST, TAG_COMPOUND = 0, 1, 3, 6, 8, 9, 10


class Int(int):
    pass


class Dbl(float):
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
    if isinstance(v, Dbl):
        return TAG_DOUBLE
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
    if t == TAG_DOUBLE:
        return struct.pack('>d', float(v))
    return struct.pack('>i', v)


def write_nbt(path, root):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    data = bytes([TAG_COMPOUND]) + _name('') + _payload(root)
    with gzip.open(path, 'wb') as f:
        f.write(data)


SX, SY, SZ = 27, 11, 27
blocks = {}
entities = []


def put(x, y, z, state, nbt=None):
    blocks[(x, y, z)] = (state, nbt)


def fill(x0, y0, z0, x1, y1, z1, state):
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            for z in range(z0, z1 + 1):
                put(x, y, z, state)


rng = random.Random(4242)
C = SX // 2  # centre

# clear everything so the camp sits on a clean plot, then a worn ground
fill(0, 0, 0, SX - 1, SY - 1, SZ - 1, 'minecraft:air')
for x in range(SX):
    for z in range(SZ):
        r = rng.random()
        put(x, 0, z, 'minecraft:coarse_dirt' if r < 0.45 else 'minecraft:podzol' if r < 0.7 else 'minecraft:gravel' if r < 0.85 else 'minecraft:dirt_path')

# ---- perimeter: sandbag-like hay wall with a fence on top and four gaps
for i in range(SX):
    for (x, z) in ((i, 0), (i, SZ - 1), (0, i), (SX - 1, i)):
        gap = abs(i - C) <= 1
        if gap:
            continue
        put(x, 1, z, 'minecraft:hay_block[axis=x]' if (x in (0, SX - 1)) else 'minecraft:hay_block[axis=z]')
        put(x, 2, z, 'minecraft:spruce_fence[east=false,north=false,south=false,waterlogged=false,west=false]')
# barbed wire: iron bars along the top corners
for (x, z) in ((0, 0), (SX - 1, 0), (0, SZ - 1), (SX - 1, SZ - 1)):
    fill(x, 1, z, x, 3, z, 'minecraft:spruce_log[axis=y]')
    put(x, 4, z, 'minecraft:lantern[hanging=false,waterlogged=false]')

# ---- two watchtowers (north-west and south-east)
def tower(x0, z0):
    for dx in (0, 4):
        for dz in (0, 4):
            fill(x0 + dx, 1, z0 + dz, x0 + dx, 6, z0 + dz, 'minecraft:spruce_log[axis=y]')
    for x in range(x0, x0 + 5):
        for z in range(z0, z0 + 5):
            put(x, 6, z, 'minecraft:spruce_planks')
    for i in range(5):
        for (x, z) in ((x0 + i, z0), (x0 + i, z0 + 4), (x0, z0 + i), (x0 + 4, z0 + i)):
            if (x, z) not in {(x0, z0), (x0 + 4, z0), (x0, z0 + 4), (x0 + 4, z0 + 4)}:
                put(x, 7, z, 'minecraft:spruce_fence[east=false,north=false,south=false,waterlogged=false,west=false]')
    for dx in (0, 4):
        for dz in (0, 4):
            put(x0 + dx, 7, z0 + dz, 'minecraft:spruce_log[axis=y]')
            put(x0 + dx, 8, z0 + dz, 'minecraft:torch')
    # ladder on the inside and a roof corner
    for y in range(1, 7):
        put(x0 + 2, y, z0 + 1, 'minecraft:ladder[facing=south,waterlogged=false]')
    put(x0 + 2, 6, z0 + 1, 'minecraft:air')


tower(2, 2)
tower(SX - 7, SZ - 7)

# ---- tents: simple A-frames of wool
def tent(x0, z0, wool, along_z=True):
    w = 5
    for k in range(w):
        for y in range(0, 3):
            pass
    # cross section across x, running along z
    for z in range(z0, z0 + 6):
        for (dx, y) in ((0, 1), (4, 1), (1, 2), (3, 2), (2, 3)):
            put(x0 + dx, y, z, wool)
        for dx in (1, 2, 3):
            put(x0 + dx, 1, z, 'minecraft:spruce_planks') if False else None
    # closed back wall
    for dx in range(1, 4):
        put(x0 + dx, 1, z0 + 5, wool)
    for (dx, y) in ((2, 2),):
        put(x0 + dx, y, z0 + 5, wool)
    # floor (carpet) and bedroll
    for dx in range(1, 4):
        for z in range(z0, z0 + 5):
            put(x0 + dx, 0, z, 'minecraft:brown_wool')
    put(x0 + 2, 1, z0 + 3, 'minecraft:red_bed[facing=south,occupied=false,part=head]')
    put(x0 + 2, 1, z0 + 2, 'minecraft:red_bed[facing=south,occupied=false,part=foot]')
    put(x0 + 1, 1, z0 + 4, 'minecraft:barrel[facing=up,open=false]')


tent(5, 11, 'minecraft:green_wool')
tent(17, 11, 'minecraft:gray_wool')
tent(11, 19, 'minecraft:green_wool')

# ---- campfire with log seats in the middle
put(C, 1, C, 'minecraft:campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]')
for (dx, dz, f) in ((2, 0, 'west'), (-2, 0, 'east'), (0, 2, 'north'), (0, -2, 'south')):
    put(C + dx, 1, C + dz, f'minecraft:spruce_stairs[facing={f},half=bottom,shape=straight,waterlogged=false]')
put(C + 1, 1, C + 1, 'minecraft:cauldron')
put(C - 1, 1, C - 1, 'minecraft:barrel[facing=up,open=false]', {'id': 'minecraft:barrel', 'LootTable': 'theboys:chests/hunter_camp_supplies'})

# ---- supply: weapon chests, a map table and a lectern with a "wanted" book
put(C - 4, 1, 4, 'minecraft:chest[facing=south,type=single,waterlogged=false]', {'id': 'minecraft:chest', 'LootTable': 'theboys:chests/hunter_camp'})
put(C - 3, 1, 4, 'minecraft:chest[facing=south,type=single,waterlogged=false]', {'id': 'minecraft:chest', 'LootTable': 'theboys:chests/hunter_camp_supplies'})
put(C + 2, 1, 4, 'minecraft:cartography_table')
put(C + 3, 1, 4, 'minecraft:fletching_table')
put(C + 4, 1, 4, 'minecraft:smithing_table')
put(C, 1, 4, 'minecraft:lectern[facing=south,has_book=false,powered=false]')
put(C - 2, 1, 4, 'minecraft:grindstone[face=floor,facing=south]')

# ---- shooting range: a row of targets on posts at the east side
for z in range(8, 19, 2):
    put(SX - 4, 1, z, 'minecraft:spruce_fence[east=false,north=false,south=false,waterlogged=false,west=false]')
    put(SX - 4, 2, z, 'minecraft:target[power=0]')
    put(SX - 3, 1, z, 'minecraft:hay_block[axis=y]')

# ---- a cage for captured supes (iron bars) in the south-west corner
fill(3, 1, SZ - 8, 7, 3, SZ - 4, 'minecraft:iron_bars[east=false,north=false,south=false,waterlogged=false,west=false]')
fill(4, 1, SZ - 7, 6, 2, SZ - 5, 'minecraft:air')
fill(3, 4, SZ - 8, 7, 4, SZ - 4, 'minecraft:iron_bars[east=false,north=false,south=false,waterlogged=false,west=false]')
put(5, 1, SZ - 6, 'minecraft:skeleton_skull[powered=false,rotation=4]')

# ---- the hunters: six people, two of them up in the towers
def hunter(x, y, z):
    entities.append({'pos': [Dbl(x + 0.5), Dbl(y), Dbl(z + 0.5)], 'blockPos': [Int(x), Int(y), Int(z)],
                     'nbt': {'id': 'theboys:supe_hunter', 'PersistenceRequired': True}})


hunter(4, 7, 4)               # tower north-west
hunter(SX - 5, 7, SZ - 5)     # tower south-east
hunter(C + 2, 1, C - 2)
hunter(C - 2, 1, C + 3)
hunter(C, 1, 8)
hunter(C + 4, 1, 16)

# ---- palette / output
palette, index, out_blocks = [], {}, []


def state_tag(s):
    if '[' in s:
        name, props = s[:-1].split('[', 1)
        return {'id': name, 'properties': dict(kv.split('=') for kv in props.split(','))}
    return {'id': s}


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

root = {'DataVersion': Int(DATA_VERSION), 'size': [Int(SX), Int(SY), Int(SZ)], 'palette': palette,
        'blocks': out_blocks, 'entities': entities}
write_nbt(OUT, root)
print('wrote', os.path.normpath(OUT), len(out_blocks), 'blocks,', len(palette), 'states,', len(entities), 'hunters')
