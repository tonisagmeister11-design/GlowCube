// Alles, was Claude im Spiel tun kann - als Werkzeuge fuer die Claude-API.
//
// physical: true  -> die Aktion bewegt den Koerper. Sie bekommt ein Abbruch-Signal
//                    ("stop" im Chat) und beendet vorher das Folgen.
import * as movement from './skills/movement.js'
import * as world from './skills/world.js'
import * as mining from './skills/mining.js'
import * as building from './skills/building.js'
import * as crafting from './skills/crafting.js'
import * as inv from './skills/inventory.js'
import * as combat from './skills/combat.js'
import * as interact from './skills/interact.js'
import { SkillError, sleep, stopBody } from './skills/util.js'

const num = { type: 'number' }
const int = { type: 'integer' }
const str = { type: 'string' }
const xyz = { x: int, y: int, z: int }

function obj (properties = {}, required = []) {
  return { type: 'object', properties, required }
}

function def (name, description, input_schema, run, physical = false) {
  return { name, description, input_schema, run, physical }
}

export const TOOLS = [
  // --- Wahrnehmung ---
  def('status', 'Your full status: position, health, hunger, armor, gamemode, weather, who is online, inventory summary.',
    obj(), bot => world.status(bot)),
  def('look_around', 'See your surroundings: nearby players, mobs (hostile marked), dropped items, important blocks (ores, logs, chests, crafting tables, beds, water, ...) with the nearest position, and the most common blocks around you.',
    obj({ radius: { ...int, description: 'Search radius in blocks, default 16, max 48' } }),
    (bot, a) => world.lookAround(bot, a)),
  def('find_blocks', 'Find the nearest blocks of given types. Generic names work: "log" finds all logs, "iron_ore" includes deepslate_iron_ore. Only loaded chunks.',
    obj({
      blocks: { type: 'array', items: str, description: 'Minecraft block IDs, e.g. ["oak_log"], ["diamond_ore"], ["chest"]' },
      max_distance: { ...int, description: 'Default 64, max 128' },
      count: { ...int, description: 'Default 10' }
    }, ['blocks']),
    (bot, a) => world.findBlocks(bot, a)),
  def('block_info', 'What block is at a position, its state (e.g. door open, crop age) and sign text.',
    obj(xyz, ['x', 'y', 'z']), (bot, a) => world.blockInfo(bot, a)),
  def('inventory', 'Detailed inventory with slots, durability, enchantments, armor, main and off hand.',
    obj(), bot => inv.inventory(bot)),

  // --- Bewegung ---
  def('go_to', 'Walk to coordinates. Leave y out to only care about x/z. allow_digging lets you break/place blocks on the way (only in the wild, never through builds).',
    obj({ ...xyz, range: { ...num, description: 'How close, default 1' }, allow_digging: { type: 'boolean' } }, ['x', 'z']),
    (bot, a, c) => movement.goTo(bot, a, c.signal), true),
  def('go_to_player', 'Walk to a player ("come here").',
    obj({ player: str, distance: { ...num, description: 'Default 2' } }, ['player']),
    (bot, a, c) => movement.goToPlayer(bot, a, c.signal), true),
  def('follow', 'Keep following a player in the background until stop or another movement action. protect=true also fights hostile mobs that come close.',
    obj({ player: str, distance: { ...num, description: 'Default 3' }, protect: { type: 'boolean' } }, ['player']),
    (bot, a) => movement.follow(bot, a), true),
  def('move', 'Walk a number of blocks in a compass direction (north/south/east/west) or forward.',
    obj({ direction: { type: 'string', enum: ['north', 'south', 'east', 'west', 'forward'] }, blocks: int }, ['direction']),
    (bot, a, c) => movement.moveDirection(bot, a, c.signal), true),
  def('look_at', 'Turn your head towards a player or a position.',
    obj({ player: str, ...xyz }), (bot, a) => movement.lookAt(bot, a)),
  def('hold_keys', 'Raw controls like a keyboard: hold keys for some time. Keys: forward, back, left, right, jump, sprint, sneak. Use for anything not covered by other tools (e.g. jump, swim up, dodge).',
    obj({ controls: { type: 'array', items: { type: 'string', enum: ['forward', 'back', 'left', 'right', 'jump', 'sprint', 'sneak'] } }, duration_ms: int }, ['controls']),
    (bot, a, c) => movement.holdControls(bot, a, c.signal), true),
  def('sneak', 'Start or stop sneaking.', obj({ on: { type: 'boolean' } }, ['on']), (bot, a) => movement.setSneak(bot, a)),
  def('stop', 'Stop everything you are doing right now: walking, following, digging, fighting.',
    obj(), bot => { movement.stopFollowing(bot); stopBody(bot); return 'Stehe still.' }),

  // --- Abbauen ---
  def('mine', 'Find, walk to, dig and pick up blocks of a type. Chooses the best tool automatically. Generic names like "log" or "iron_ore" work. Takes a while for big counts.',
    obj({ block: { ...str, description: 'Block ID, e.g. oak_log, log, stone, coal_ore, sand' }, count: { ...int, description: 'Default 1, max 64' }, max_distance: int }, ['block']),
    (bot, a, c) => mining.mine(bot, a, c.signal), true),
  def('dig_block', 'Dig one specific block at a position and pick up the drop.',
    obj(xyz, ['x', 'y', 'z']), (bot, a, c) => mining.digAt(bot, a, c.signal), true),
  def('collect_items', 'Pick up dropped items lying around.',
    obj({ radius: int, item: { ...str, description: 'Only this item (optional)' } }),
    (bot, a, c) => mining.collectItems(bot, a, c.signal), true),

  // --- Bauen ---
  def('place_block', 'Place one block from your inventory at a position. Needs a neighbouring solid block to attach to.',
    obj({ item: str, ...xyz }, ['item', 'x', 'y', 'z']),
    (bot, a, c) => building.placeAt(bot, a, c.signal), true),
  def('build', 'Build a structure from a list of blocks (max 512 per call). Placed bottom-up automatically. Compute the coordinates yourself (walls, floors, houses, towers ...). Check you have enough material first.',
    obj({
      blocks: {
        type: 'array',
        items: obj({ item: str, ...xyz }, ['item', 'x', 'y', 'z'])
      }
    }, ['blocks']),
    (bot, a, c) => building.build(bot, a, c.signal), true),

  // --- Craften ---
  def('craft', 'Craft items. Uses a crafting table nearby, or places one from your inventory if needed. If ingredients are missing it tells you the recipe. count = number of items wanted.',
    obj({ item: { ...str, description: 'Item ID, e.g. oak_planks, stick, crafting_table, wooden_pickaxe, torch' }, count: int }, ['item']),
    (bot, a, c) => crafting.craft(bot, a, c.signal), true),
  def('smelt', 'Smelt/cook items in a furnace nearby (or place one from inventory). Picks fuel automatically. Without item: only take finished output from the furnace. wait=false puts items in and leaves (10 s per item).',
    obj({ item: str, count: int, fuel: str, wait: { type: 'boolean' } }),
    (bot, a, c) => crafting.smelt(bot, a, c.signal), true),

  // --- Inventar ---
  def('equip', 'Put an item into your hand, off-hand or armor slot.',
    obj({ item: str, slot: { type: 'string', enum: ['hand', 'off-hand', 'head', 'torso', 'legs', 'feet'] } }, ['item']),
    (bot, a) => inv.equip(bot, a)),
  def('unequip', 'Empty a slot (hand, off-hand, head, torso, legs, feet).',
    obj({ slot: { type: 'string', enum: ['hand', 'off-hand', 'head', 'torso', 'legs', 'feet'] } }, ['slot']),
    (bot, a) => inv.unequip(bot, a)),
  def('equip_best_armor', 'Put on the best armor you have.', obj(), bot => inv.equipBestArmor(bot)),
  def('drop_item', 'Drop items on the ground. item "all" drops the whole inventory. Leave count out for all of that item.',
    obj({ item: str, count: int }, ['item']), (bot, a) => inv.toss(bot, a)),
  def('give', 'Walk to a player and throw them items. Leave count out to give all of that item.',
    obj({ player: str, item: str, count: int }, ['player', 'item']),
    (bot, a, c) => inv.giveTo(bot, a, c.signal), true),
  def('eat', 'Eat food. Without item the best food in your inventory is chosen.',
    obj({ item: str }), (bot, a) => inv.eat(bot, a)),

  // --- Kampf ---
  def('attack', 'Fight a mob type ("zombie"), any hostile mob ("hostile") or a player by name, until defeated. count = how many to kill. Equips your best weapon.',
    obj({ target: str, count: int }, ['target']),
    (bot, a, c) => combat.attack(bot, a, c.signal), true),

  // --- Interaktion ---
  def('activate_block', 'Right-click a block: doors, trapdoors, gates, levers, buttons, bells, note blocks, repeaters, crafting stations, beds ... Optionally hold an item first (e.g. bone_meal on crops, wheat_seeds on farmland, water_bucket, flint_and_steel, a hoe on dirt).',
    obj({ ...xyz, item: str }, ['x', 'y', 'z']),
    (bot, a, c) => interact.activateBlock(bot, a, c.signal), true),
  def('use_item', 'Right-click with an item in the air: bow (hold_ms 1200, look_at a target), shield (hold), potion, ender_pearl, snowball, spyglass, goat_horn ... look_at can be a mob type, player name or "x y z".',
    obj({ item: str, hold_ms: int, look_at: str, off_hand: { type: 'boolean' } }),
    (bot, a, c) => interact.useItem(bot, a, c.signal), true),
  def('interact_entity', 'Right-click a mob or player, optionally holding an item: breed/feed animals (wheat, carrot, seeds), shear sheep, milk cows (bucket), name tag, lead, saddle ...',
    obj({ target: { ...str, description: 'Mob type or player name' }, item: str }, ['target']),
    (bot, a, c) => interact.interactEntity(bot, a, c.signal), true),
  def('mount', 'Ride something: horse, boat, minecart, pig, strider ...', obj({ target: str }, ['target']),
    (bot, a, c) => interact.mount(bot, a, c.signal), true),
  def('dismount', 'Get off whatever you are riding.', obj(), bot => interact.dismount(bot)),
  def('sleep', 'Sleep in the nearest bed (only at night or during thunderstorms).', obj(),
    (bot, a, c) => interact.sleepInBed(bot, a, c.signal), true),
  def('wake_up', 'Get out of bed.', obj(), bot => interact.wake(bot)),
  def('container', 'Use a chest, barrel, shulker box, ender chest, hopper ...: list its content, deposit or withdraw items. item "all" moves everything. Without coordinates the nearest container is used.',
    obj({ action: { type: 'string', enum: ['list', 'deposit', 'withdraw'] }, ...xyz, item: str, count: int }, ['action']),
    (bot, a, c) => interact.container(bot, a, c.signal), true),
  def('fish', 'Fish with a fishing rod at water nearby.', obj({ count: int }),
    (bot, a, c) => interact.fish(bot, a, c.signal), true),
  def('trade', 'Trade with a villager or wandering trader. Without index it lists the offers; with index it performs that trade `times` times.',
    obj({ target: { ...str, description: 'Default villager' }, index: int, times: int }),
    (bot, a, c) => interact.trade(bot, a, c.signal), true),

  // --- Chat und Befehle ---
  def('run_command', 'Run a server command as yourself, e.g. "home", "spawn", "tpa Toni", "msg Toni hi". Without the leading slash. Only works with the permissions you have on the server.',
    obj({ command: str }, ['command']),
    (bot, a) => {
      const cmd = String(a.command).replace(/^\/+/, '').trim()
      if (!cmd) throw new SkillError('Leerer Befehl.')
      bot.expectServerReplyUntil = Date.now() + 4000
      bot.chat('/' + cmd)
      return `Befehl /${cmd} gesendet. Antworten des Servers kommen als Ereignis.`
    }),
  def('whisper', 'Send a private message to one player.', obj({ player: str, message: str }, ['player', 'message']),
    (bot, a) => { bot.whisper(a.player, String(a.message).slice(0, 200)); return 'Gefluestert.' }),

  // --- Gedaechtnis ---
  def('remember', 'Save a note permanently (survives restarts): base coordinates, promises, preferences of players ...',
    obj({ note: str }, ['note']), (bot, a, c) => c.memory.add(a.note)),
  def('forget', 'Delete a saved note by its number.', obj({ number: int }, ['number']),
    (bot, a, c) => c.memory.remove(a.number)),

  // --- Sonstiges ---
  def('wait', 'Wait some seconds (e.g. for crops, for a player, for night to end). Max 60.',
    obj({ seconds: num }, ['seconds']),
    async (bot, a, c) => { await sleep(Math.min(Math.max(Number(a.seconds) || 1, 0), 60) * 1000, c.signal); return 'Gewartet.' }, true),
  def('leave', 'Disappear from the world until someone summons you again with /spawn claude. Only when a player tells you to go away.',
    obj(), (bot, a, c) => c.leave())
]

const byName = new Map(TOOLS.map(t => [t.name, t]))

// Fuer die API: nur name/description/input_schema
export const TOOL_SCHEMAS = TOOLS.map(({ name, description, input_schema: inputSchema }) => ({
  name, description, input_schema: inputSchema
}))

export function getTool (name) {
  return byName.get(name)
}
