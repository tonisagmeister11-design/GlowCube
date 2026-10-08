#!/usr/bin/env python3
"""Writes all JSON resources of the mod (models, item definitions, blockstates, loot, worldgen, tags).

Run: python3 tools/gen_resources.py
Language files are maintained by hand in assets/theboys/lang.
"""
import json
import os

BASE = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources')
A = os.path.join(BASE, 'assets', 'theboys')
D = os.path.join(BASE, 'data', 'theboys')
M = os.path.join(BASE, 'data', 'minecraft')


def w(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        json.dump(obj, f, indent=2)
        f.write('\n')


def item_def(name, model):
    w(os.path.join(A, 'items', name + '.json'), {'model': {'type': 'minecraft:model', 'model': model}})


# ------------------------------------------------------------------ items: flat icon in the inventory, real 3D model in the hand
def item_def_3d(name, flat, hand):
    w(os.path.join(A, 'items', name + '.json'), {'model': {
        'type': 'minecraft:select',
        'property': 'minecraft:display_context',
        'cases': [{'when': ['gui', 'fixed', 'on_shelf'], 'model': {'type': 'minecraft:model', 'model': flat}}],
        'fallback': {'type': 'minecraft:model', 'model': hand}}})


def box(frm, to, u0, v0, u1, v1, tex='#t'):
    faces = {d: {'uv': [u0, v0, u1, v1], 'texture': tex} for d in ['north', 'south', 'east', 'west', 'up', 'down']}
    return {'from': frm, 'to': to, 'faces': faces}


# 3D tools are built upright and tilted 45 degrees like a sword sprite, so the vanilla
# "handheld" poses hold them exactly like a tool
TILT = {'origin': [8, 8, 8], 'axis': 'z', 'angle': -45}


def tilted(*boxes):
    for bx in boxes:
        bx['rotation'] = TILT
    return list(boxes)


HANDHELD_3D = {
    'thirdperson_righthand': {'rotation': [0, -90, 55], 'translation': [0, 4.0, 0.5], 'scale': [0.85, 0.85, 0.85]},
    'thirdperson_lefthand': {'rotation': [0, 90, -55], 'translation': [0, 4.0, 0.5], 'scale': [0.85, 0.85, 0.85]},
    'firstperson_righthand': {'rotation': [0, -90, 25], 'translation': [1.13, 3.2, 1.13], 'scale': [0.68, 0.68, 0.68]},
    'firstperson_lefthand': {'rotation': [0, 90, -25], 'translation': [1.13, 3.2, 1.13], 'scale': [0.68, 0.68, 0.68]},
    'ground': {'rotation': [0, 0, 0], 'translation': [0, 2, 0], 'scale': [0.5, 0.5, 0.5]},
    'head': {'rotation': [0, 180, 0], 'translation': [0, 13, 7], 'scale': [1, 1, 1]},
}

# syringe: thumb rest at the bottom, needle at the top
SYRINGE = tilted(
    box([6.75, -0.5, 6.75], [9.25, 0, 9.25], 4, 4, 8, 8),    # thumb rest (cap colour)
    box([7.5, 0, 7.5], [8.5, 3.5, 8.5], 4, 0, 8, 4),         # plunger rod
    box([5.5, 3.5, 7.5], [10.5, 4.2, 8.5], 4, 0, 8, 4),      # finger flange
    box([6.5, 3.5, 6.5], [9.5, 4, 9.5], 4, 0, 8, 4),
    box([7, 4, 7], [9, 12, 9], 0, 0, 4, 8),                  # barrel with the serum
    box([7.4, 12, 7.4], [8.6, 13, 8.6], 4, 4, 8, 8),         # needle hub (cap colour)
    box([7.85, 13, 7.85], [8.15, 17.5, 8.15], 8, 0, 10, 8),  # needle
)
SYRINGE_DISPLAY = HANDHELD_3D
for name in ['compound_v', 'compound_v1', 'mini_v', 'uranium_injector', 'empty_syringe']:
    w(os.path.join(A, 'models', 'item', name + '.json'), {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'theboys:item/' + name}})
    tex = 'theboys:item/' + name + '_3d'
    w(os.path.join(A, 'models', 'item', name + '_3d.json'), {
        'gui_light': 'front',
        'textures': {'t': {'sprite': tex, 'force_translucent': True} if name == 'empty_syringe' else tex, 'particle': tex},
        'elements': SYRINGE,
        'display': SYRINGE_DISPLAY})
    item_def_3d(name, 'theboys:item/' + name, 'theboys:item/' + name + '_3d')

# crowbar: straight shaft with a curled claw on top and a flat chisel end
CROWBAR = tilted(
    box([7.4, -1, 7.4], [8.6, 15, 8.6], 0, 0, 2, 16),
    box([8.6, 14, 7.4], [10.4, 15.2, 8.6], 2, 0, 6, 2),
    box([9.6, 12.2, 7.4], [10.8, 14.2, 8.6], 2, 2, 4, 6),
    box([10.2, 11.4, 7.4], [11.6, 12.4, 8.6], 2, 6, 4, 8),
    box([6.6, -1.8, 7.4], [8.6, -1, 8.6], 4, 8, 8, 10),
)
w(os.path.join(A, 'models', 'item', 'crowbar.json'), {'parent': 'minecraft:item/handheld', 'textures': {'layer0': 'theboys:item/crowbar'}})
w(os.path.join(A, 'models', 'item', 'crowbar_3d.json'), {
    'gui_light': 'front',
    'textures': {'t': 'theboys:item/crowbar_3d', 'particle': 'theboys:item/crowbar_3d'},
    'elements': CROWBAR,
    'display': HANDHELD_3D})
item_def_3d('crowbar', 'theboys:item/crowbar', 'theboys:item/crowbar_3d')

# ------------------------------------------------------------------ shield: flat icon in GUI, 3D heater shield in hand
SHIELD_DISPLAY = {
    'thirdperson_righthand': {'rotation': [0, 90, 0], 'translation': [10, 6, -4], 'scale': [1, 1, 1]},
    'thirdperson_lefthand': {'rotation': [0, 90, 0], 'translation': [10, 6, 12], 'scale': [1, 1, 1]},
    'firstperson_righthand': {'rotation': [0, 180, 5], 'translation': [-10, 1.75, -10], 'scale': [1.25, 1.25, 1.25]},
    'firstperson_lefthand': {'rotation': [0, 180, 5], 'translation': [10, 0, -10], 'scale': [1.25, 1.25, 1.25]},
    'fixed': {'rotation': [0, 180, 0], 'translation': [-4.5, 4.5, -5], 'scale': [0.55, 0.55, 0.55]},
    'ground': {'rotation': [0, 0, 0], 'translation': [2, 4, 2], 'scale': [0.25, 0.25, 0.25]},
    'head': {'rotation': [0, 180, 0], 'translation': [0, 13, 7], 'scale': [1, 1, 1]},
}
SHIELD_BLOCKING = dict(SHIELD_DISPLAY)
SHIELD_BLOCKING.update({
    'thirdperson_righthand': {'rotation': [45, 155, 0], 'translation': [-3.49, 11, -2], 'scale': [1, 1, 1]},
    'thirdperson_lefthand': {'rotation': [45, 155, 0], 'translation': [11.51, 7, 2.5], 'scale': [1, 1, 1]},
    # raised in front of the player, a bit left of the crosshair (derived from the visible idle pose)
    'firstperson_righthand': {'rotation': [0, 180, -2], 'translation': [-15, 5, -9], 'scale': [1.25, 1.25, 1.25]},
    'firstperson_lefthand': {'rotation': [0, 180, 2], 'translation': [5, 5, -9], 'scale': [1.25, 1.25, 1.25]},
})
face = 'theboys:item/soldier_boy_shield_face'
# same footprint as the vanilla shield (12 x 22 x 1 plate + handle) so the vanilla hand poses fit
shield_elements = [
    {'from': [-6, -11, 1], 'to': [6, 11, 2],
     'faces': {
         'north': {'uv': [0, 0, 16, 16], 'texture': '#face'},
         'south': {'uv': [16, 0, 0, 16], 'texture': '#face'},
         'east': {'uv': [0, 0, 0.5, 16], 'texture': '#face'},
         'west': {'uv': [0, 0, 0.5, 16], 'texture': '#face'},
         'up': {'uv': [0, 0, 16, 0.5], 'texture': '#face'},
         'down': {'uv': [0, 15.5, 16, 16], 'texture': '#face'}}},
    {'from': [-1, -3, -5], 'to': [1, 3, 1],
     'faces': {d: {'uv': [7.5, 0, 8.5, 0.5], 'texture': '#face'} for d in ['north', 'south', 'east', 'west', 'up', 'down']}},
]
for name, display in [('soldier_boy_shield_3d', SHIELD_DISPLAY), ('soldier_boy_shield_blocking', SHIELD_BLOCKING)]:
    w(os.path.join(A, 'models', 'item', name + '.json'), {
        'gui_light': 'front',
        'textures': {'face': face, 'particle': face},
        'elements': shield_elements,
        'display': display,
    })
w(os.path.join(A, 'models', 'item', 'soldier_boy_shield.json'), {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'theboys:item/soldier_boy_shield'}})
w(os.path.join(A, 'items', 'soldier_boy_shield.json'), {'model': {
    'type': 'minecraft:select',
    'property': 'minecraft:display_context',
    'cases': [{'when': ['gui', 'ground', 'fixed', 'on_shelf'], 'model': {'type': 'minecraft:model', 'model': 'theboys:item/soldier_boy_shield'}}],
    'fallback': {
        'type': 'minecraft:condition',
        'property': 'minecraft:using_item',
        'on_true': {'type': 'minecraft:model', 'model': 'theboys:item/soldier_boy_shield_blocking'},
        'on_false': {'type': 'minecraft:model', 'model': 'theboys:item/soldier_boy_shield_3d'},
    },
}})

# ------------------------------------------------------------------ blocks
def cube_block(name, texture=None, translucent=False):
    tex = 'theboys:block/' + (texture or name)
    w(os.path.join(A, 'models', 'block', name + '.json'), {
        'parent': 'minecraft:block/cube_all',
        'textures': {'all': {'sprite': tex, 'force_translucent': True} if translucent else tex}})
    w(os.path.join(A, 'blockstates', name + '.json'), {'variants': {'': {'model': 'theboys:block/' + name}}})
    item_def(name, 'theboys:block/' + name)


cube_block('lab_tiles')
cube_block('lab_floor')
cube_block('vought_panel')
cube_block('containment_glass', translucent=True)

w(os.path.join(A, 'models', 'block', 'v_fridge.json'), {
    'parent': 'minecraft:block/orientable',
    'textures': {'front': 'theboys:block/v_fridge_front', 'side': 'theboys:block/v_fridge_side', 'top': 'theboys:block/v_fridge_top'}})
variants = {}
for facing, rot in [('north', 0), ('east', 90), ('south', 180), ('west', 270)]:
    for stocked in ['true', 'false']:
        v = {'model': 'theboys:block/v_fridge'}
        if rot:
            v['y'] = rot
        variants[f'facing={facing},stocked={stocked}'] = v
w(os.path.join(A, 'blockstates', 'v_fridge.json'), {'variants': variants})
item_def('v_fridge', 'theboys:block/v_fridge')

# ------------------------------------------------------------------ loot
for name in ['lab_tiles', 'lab_floor', 'vought_panel', 'v_fridge']:
    w(os.path.join(D, 'loot_table', 'blocks', name + '.json'), {
        'type': 'minecraft:block',
        'pools': [{'rolls': 1, 'condition': {'type': 'minecraft:survives_explosion'},
                   'entries': [{'type': 'minecraft:item', 'name': 'theboys:' + name}]}],
        'random_sequence': 'theboys:blocks/' + name})
w(os.path.join(D, 'loot_table', 'blocks', 'containment_glass.json'), {
    'type': 'minecraft:block',
    'pools': [{'rolls': 1, 'condition': {'type': 'minecraft:match_tool', 'predicate': {
        'predicates': {'minecraft:enchantments': [{'enchantments': 'minecraft:silk_touch', 'levels': {'min': 1}}]}}},
               'entries': [{'type': 'minecraft:item', 'name': 'theboys:containment_glass'}]}],
    'random_sequence': 'theboys:blocks/containment_glass'})


def count(lo, hi):
    return {'type': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': lo, 'max': hi}}


w(os.path.join(D, 'loot_table', 'chests', 'vought_lab.json'), {
    'type': 'minecraft:chest',
    'pools': [
        # Compound V - what the labs are for
        {'rolls': {'type': 'minecraft:uniform', 'min': 1, 'max': 2},
         'entries': [{'type': 'minecraft:item', 'name': 'theboys:compound_v', 'functions': [count(1, 2)]}]},
        # the rare V-One
        {'rolls': 1, 'entries': [
            {'type': 'minecraft:item', 'name': 'theboys:compound_v1', 'weight': 4},
            {'type': 'minecraft:empty', 'weight': 96}]},
        # Mini V - makes MiniMaus
        {'rolls': 1, 'entries': [
            {'type': 'minecraft:item', 'name': 'theboys:mini_v', 'weight': 15},
            {'type': 'minecraft:empty', 'weight': 85}]},
        # radiation injector to get rid of a power again
        {'rolls': 1, 'entries': [
            {'type': 'minecraft:item', 'name': 'theboys:uranium_injector', 'weight': 30},
            {'type': 'minecraft:empty', 'weight': 70}]},
        {'rolls': {'type': 'minecraft:uniform', 'min': 2, 'max': 5}, 'entries': [
            {'type': 'minecraft:item', 'name': 'theboys:empty_syringe', 'weight': 20, 'functions': [count(1, 4)]},
            {'type': 'minecraft:item', 'name': 'minecraft:glass_bottle', 'weight': 15, 'functions': [count(1, 3)]},
            {'type': 'minecraft:item', 'name': 'minecraft:paper', 'weight': 15, 'functions': [count(2, 6)]},
            {'type': 'minecraft:item', 'name': 'minecraft:iron_ingot', 'weight': 10, 'functions': [count(1, 4)]},
            {'type': 'minecraft:item', 'name': 'minecraft:redstone', 'weight': 10, 'functions': [count(2, 8)]},
            {'type': 'minecraft:item', 'name': 'minecraft:gold_ingot', 'weight': 5, 'functions': [count(1, 3)]},
            {'type': 'minecraft:item', 'name': 'minecraft:diamond', 'weight': 2},
            {'type': 'minecraft:item', 'name': 'theboys:crowbar', 'weight': 3}]},
    ],
    'random_sequence': 'theboys:chests/vought_lab'})
w(os.path.join(D, 'loot_table', 'chests', 'vought_lab_supplies.json'), {
    'type': 'minecraft:chest',
    'pools': [
        {'rolls': {'type': 'minecraft:uniform', 'min': 3, 'max': 7}, 'entries': [
            {'type': 'minecraft:item', 'name': 'theboys:empty_syringe', 'weight': 20, 'functions': [count(1, 3)]},
            {'type': 'minecraft:item', 'name': 'minecraft:glass_bottle', 'weight': 20, 'functions': [count(1, 4)]},
            {'type': 'minecraft:item', 'name': 'minecraft:bread', 'weight': 15, 'functions': [count(1, 4)]},
            {'type': 'minecraft:item', 'name': 'minecraft:iron_nugget', 'weight': 15, 'functions': [count(3, 9)]},
            {'type': 'minecraft:item', 'name': 'minecraft:glowstone_dust', 'weight': 10, 'functions': [count(1, 6)]},
            {'type': 'minecraft:item', 'name': 'minecraft:lapis_lazuli', 'weight': 10, 'functions': [count(2, 6)]},
            {'type': 'minecraft:item', 'name': 'theboys:compound_v', 'weight': 4}]},
    ],
    'random_sequence': 'theboys:chests/vought_lab_supplies'})

# ------------------------------------------------------------------ worldgen: Vought labs, about as common as ruined portals
w(os.path.join(D, 'worldgen', 'template_pool', 'vought_lab', 'main.json'), {
    'fallback': 'minecraft:empty',
    'elements': [{'weight': 1, 'element': {
        'element_type': 'minecraft:single_pool_element',
        'location': 'theboys:vought_lab/main',
        'processors': 'minecraft:empty',
        'projection': 'rigid'}}]})
w(os.path.join(D, 'worldgen', 'structure', 'vought_lab.json'), {
    'type': 'minecraft:jigsaw',
    'biomes': '#theboys:has_structure/vought_lab',
    'max_distance_from_center': 80,
    'project_start_to_heightmap': 'WORLD_SURFACE_WG',
    'size': 1,
    'spawn_overrides': {},
    'start_height': {'absolute': -1},
    'start_pool': 'theboys:vought_lab/main',
    'step': 'surface_structures',
    'terrain_adaptation': 'beard_thin',
    'use_expansion_hack': False})
w(os.path.join(D, 'worldgen', 'structure_set', 'vought_labs.json'), {
    'placement': {'type': 'minecraft:random_spread', 'salt': 1946032119, 'separation': 15, 'spacing': 40},
    'structures': [{'structure': 'theboys:vought_lab', 'weight': 1}]})
w(os.path.join(D, 'tags', 'worldgen', 'biome', 'has_structure', 'vought_lab.json'), {
    'values': ['#minecraft:is_forest', '#minecraft:is_taiga', '#minecraft:is_savanna', '#minecraft:is_jungle', '#minecraft:is_hill',
               'minecraft:plains', 'minecraft:sunflower_plains', 'minecraft:snowy_plains', 'minecraft:desert', 'minecraft:meadow',
               'minecraft:cherry_grove', 'minecraft:swamp', 'minecraft:badlands', 'minecraft:windswept_hills',
               {'id': 'minecraft:pale_garden', 'required': False}]})

# ------------------------------------------------------------------ tags
w(os.path.join(M, 'tags', 'block', 'mineable', 'pickaxe.json'), {
    'replace': False,
    'values': ['theboys:lab_tiles', 'theboys:lab_floor', 'theboys:vought_panel', 'theboys:v_fridge', 'theboys:containment_glass']})
print('resources written')
