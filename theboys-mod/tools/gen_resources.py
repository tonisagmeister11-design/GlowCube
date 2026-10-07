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


# ------------------------------------------------------------------ simple items
for name in ['compound_v', 'compound_v1', 'uranium_injector', 'empty_syringe']:
    w(os.path.join(A, 'models', 'item', name + '.json'), {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'theboys:item/' + name}})
    item_def(name, 'theboys:item/' + name)

w(os.path.join(A, 'models', 'item', 'crowbar.json'), {'parent': 'minecraft:item/handheld', 'textures': {'layer0': 'theboys:item/crowbar'}})
item_def('crowbar', 'theboys:item/crowbar')

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
    'firstperson_righthand': {'rotation': [0, 180, -5], 'translation': [-15, 3.25, -11], 'scale': [1.25, 1.25, 1.25]},
    'firstperson_lefthand': {'rotation': [0, 180, -5], 'translation': [5, 5, -11], 'scale': [1.25, 1.25, 1.25]},
})
face = 'theboys:item/soldier_boy_shield_face'
# same footprint as the vanilla shield (12 x 22 x 1 plate + handle) so the vanilla hand poses fit
shield_elements = [
    {'from': [2, -3, 9], 'to': [14, 19, 10],
     'faces': {
         'north': {'uv': [0, 0, 16, 16], 'texture': '#face'},
         'south': {'uv': [16, 0, 0, 16], 'texture': '#face'},
         'east': {'uv': [0, 0, 0.5, 16], 'texture': '#face'},
         'west': {'uv': [0, 0, 0.5, 16], 'texture': '#face'},
         'up': {'uv': [0, 0, 16, 0.5], 'texture': '#face'},
         'down': {'uv': [0, 15.5, 16, 16], 'texture': '#face'}}},
    {'from': [7, 5, 10], 'to': [9, 11, 12],
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
