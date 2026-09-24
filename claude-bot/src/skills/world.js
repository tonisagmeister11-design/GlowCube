// Wahrnehmung: Was ist um mich herum?
import { SkillError, dist, fmtPos, inventorySummary, normName, vec } from './util.js'

const HOSTILE = new Set([
  'zombie', 'zombie_villager', 'husk', 'drowned', 'skeleton', 'stray', 'bogged', 'wither_skeleton',
  'creeper', 'spider', 'cave_spider', 'witch', 'slime', 'magma_cube', 'phantom', 'pillager',
  'vindicator', 'evoker', 'illusioner', 'ravager', 'vex', 'blaze', 'ghast', 'hoglin', 'zoglin',
  'piglin_brute', 'silverfish', 'endermite', 'guardian', 'elder_guardian', 'shulker', 'warden',
  'breeze', 'creaking', 'wither', 'ender_dragon'
])

export function isHostile (entity) {
  if (!entity || entity.type === 'player') return false
  return HOSTILE.has(entity.name) || entity.type === 'hostile'
}

// Bloecke, die fuer Entscheidungen wirklich zaehlen - mit Position des naechsten.
const INTERESTING = /(_ore$|ancient_debris|_log$|_stem$|crafting_table|furnace|smoker|chest|barrel|shulker_box|_bed$|spawner|water$|lava$|anvil|enchanting_table|brewing_stand|farmland|wheat|carrots|potatoes|beetroots|sugar_cane|pumpkin$|melon$|_door$|nether_portal|end_portal)/

function timeOfDay (bot) {
  const t = bot.time?.timeOfDay ?? 0
  // Tick 0 = 6:00 Uhr
  const hours = Math.floor(((t / 1000) + 6) % 24)
  const minutes = Math.floor(((t % 1000) / 1000) * 60)
  const hh = String(hours).padStart(2, '0')
  const mm = String(minutes).padStart(2, '0')
  const phase = (t >= 12542 && t <= 23460) ? 'Nacht' : 'Tag'
  return `${hh}:${mm} (${phase})`
}

export function activityOf (bot) {
  if (bot.followState) return `folgt ${bot.followState.player}${bot.followState.protect ? ' (beschuetzt)' : ''}`
  if (bot.currentActivity) return bot.currentActivity
  return 'nichts'
}

// Einzeilige Lage, die jedem Ereignis beigelegt wird.
export function shortStatus (bot) {
  const p = bot.entity?.position
  const held = bot.heldItem ? bot.heldItem.name : 'nichts'
  return `Position ${fmtPos(p)} (${bot.game?.dimension ?? '?'}) | Leben ${Math.round(bot.health ?? 0)}/20 | ` +
    `Hunger ${bot.food ?? '?'}/20 | ${timeOfDay(bot)} | Hand: ${held} | Taetigkeit: ${activityOf(bot)}`
}

export function nearbyPlayers (bot, radius = 64) {
  const out = []
  for (const [name, info] of Object.entries(bot.players)) {
    if (name === bot.username) continue
    if (info.entity) {
      const d = dist(bot, info.entity.position)
      if (d <= radius) out.push(`${name} (${Math.round(d)} Bloecke, bei ${fmtPos(info.entity.position)})`)
    }
  }
  return out
}

export function status (bot) {
  const armor = ['head', 'torso', 'legs', 'feet']
    .map(dest => bot.inventory.slots[bot.getEquipmentDestSlot(dest)])
    .filter(Boolean).map(i => i.name)
  const offhand = bot.inventory.slots[45]
  const online = Object.keys(bot.players).filter(n => n !== bot.username)
  const lines = [
    shortStatus(bot),
    `Saettigung ${Number.isFinite(bot.foodSaturation) ? Math.round(bot.foodSaturation) : '?'} | Level ${bot.experience?.level ?? 0} | Spielmodus ${bot.game?.gameMode} | ` +
      `Wetter ${bot.isRaining ? 'Regen' : 'klar'} | Blick ${Math.round(((bot.entity.yaw * 180) / Math.PI + 360) % 360)} Grad`,
    `Ruestung: ${armor.length ? armor.join(', ') : 'keine'} | Nebenhand: ${offhand ? offhand.name : 'leer'}`,
    `Im Wasser: ${bot.entity.isInWater ? 'ja' : 'nein'} | Am Boden: ${bot.entity.onGround ? 'ja' : 'nein'}`,
    `Online: ${online.length ? online.join(', ') : 'niemand sonst'}`,
    `Inventar: ${inventorySummary(bot)}`
  ]
  return lines.join('\n')
}

export function lookAround (bot, { radius = 16 } = {}) {
  const r = Math.min(Math.max(Number(radius) || 16, 4), 48)
  const me = bot.entity.position
  const lines = []

  const players = nearbyPlayers(bot, r * 2)
  lines.push(`Spieler: ${players.length ? players.join('; ') : 'keine in der Naehe'}`)

  // Lebewesen und herumliegende Items, gruppiert
  const mobs = new Map()
  const drops = []
  for (const e of Object.values(bot.entities)) {
    if (e === bot.entity || !e.position) continue
    const d = me.distanceTo(e.position)
    if (d > r) continue
    if (e.type === 'player') continue
    if (e.name === 'item') {
      const item = e.getDroppedItem?.()
      drops.push(`${item ? `${item.count}x ${item.name}` : 'Item'} (${Math.round(d)})`)
      continue
    }
    const name = e.name || e.displayName || 'unbekannt'
    const entry = mobs.get(name) || { count: 0, nearest: Infinity, pos: null, hostile: isHostile(e) }
    entry.count++
    if (d < entry.nearest) { entry.nearest = d; entry.pos = e.position }
    mobs.set(name, entry)
  }
  const mobList = [...mobs].sort((a, b) => a[1].nearest - b[1].nearest)
    .map(([name, m]) => `${m.count}x ${name}${m.hostile ? ' (feindlich!)' : ''} naechster ${Math.round(m.nearest)} Bloecke bei ${fmtPos(m.pos)}`)
  lines.push(`Lebewesen: ${mobList.length ? mobList.join('; ') : 'keine'}`)
  if (drops.length) lines.push(`Items am Boden: ${drops.slice(0, 15).join(', ')}`)

  // Interessante Bloecke mit der Position des naechsten
  const found = bot.findBlocks({
    matching: b => b && INTERESTING.test(b.name),
    maxDistance: r,
    count: 400
  })
  const byName = new Map()
  for (const pos of found) {
    const b = bot.blockAt(pos)
    if (!b) continue
    const entry = byName.get(b.name) || { count: 0, nearest: Infinity, pos: null }
    entry.count++
    const d = me.distanceTo(pos)
    if (d < entry.nearest) { entry.nearest = d; entry.pos = pos }
    byName.set(b.name, entry)
  }
  const interesting = [...byName].sort((a, b) => a[1].nearest - b[1].nearest)
    .map(([name, e]) => `${name} x${e.count} (naechster bei ${fmtPos(e.pos)}, ${Math.round(e.nearest)} weit)`)
  lines.push(`Wichtige Bloecke: ${interesting.length ? interesting.join('; ') : 'keine'}`)

  // Grobe Umgebung: haeufigste Bloecke in kleinem Umkreis
  const rr = Math.min(r, 8)
  const counts = new Map()
  for (let dx = -rr; dx <= rr; dx++) {
    for (let dy = -4; dy <= 4; dy++) {
      for (let dz = -rr; dz <= rr; dz++) {
        const b = bot.blockAt(me.offset(dx, dy, dz))
        if (!b || b.name.endsWith('air')) continue
        counts.set(b.name, (counts.get(b.name) || 0) + 1)
      }
    }
  }
  const common = [...counts].sort((a, b) => b[1] - a[1]).slice(0, 12).map(([n, c]) => `${n} ${c}`)
  lines.push(`Umgebung (${rr} Bloecke): ${common.join(', ') || 'nur Luft'}`)

  const below = bot.blockAt(me.offset(0, -1, 0))
  const cursor = bot.blockAtCursor?.(6)
  lines.push(`Unter mir: ${below?.name ?? '?'} | Im Blick: ${cursor ? `${cursor.name} bei ${fmtPos(cursor.position)}` : 'nichts'}`)
  return lines.join('\n')
}

// "log" findet alle Holzarten, "iron_ore" auch deepslate_iron_ore.
export function blockMatcher (bot, names) {
  const wanted = (Array.isArray(names) ? names : [names]).map(normName).filter(Boolean)
  if (!wanted.length) throw new SkillError('Kein Blockname angegeben.')
  const ids = new Set()
  for (const w of wanted) {
    // Genauer Name gewinnt; nur Oberbegriffe wie "log" oder "ore" matchen die Endung.
    const exact = bot.registry.blocksByName[w]
    if (exact) {
      ids.add(exact.id)
      const deep = bot.registry.blocksByName['deepslate_' + w]
      if (deep) ids.add(deep.id)
      continue
    }
    for (const block of bot.registry.blocksArray) {
      if (block.name.endsWith('_' + w)) ids.add(block.id)
    }
  }
  if (!ids.size) throw new SkillError(`Keinen Block namens ${wanted.join(', ')} gefunden. Nutze die englischen Minecraft-IDs (z.B. oak_log, iron_ore, stone).`)
  return ids
}

export function findBlocks (bot, { blocks, max_distance = 64, count = 10 }) {
  const ids = blockMatcher(bot, blocks)
  const positions = bot.findBlocks({
    matching: [...ids],
    maxDistance: Math.min(Number(max_distance) || 64, 128),
    count: Math.min(Number(count) || 10, 50)
  })
  if (!positions.length) return `Nichts davon im Umkreis von ${max_distance} Bloecken gefunden (nur geladene Chunks).`
  return positions
    .map(p => ({ p, b: bot.blockAt(p), d: dist(bot, p) }))
    .sort((a, b) => a.d - b.d)
    .map(({ p, b, d }) => `${b?.name} bei ${fmtPos(p)} (${Math.round(d)} weit)`)
    .join('\n')
}

export function blockInfo (bot, { x, y, z }) {
  const b = bot.blockAt(vec(x, y, z))
  if (!b) return 'Dieser Chunk ist nicht geladen.'
  const parts = [`${b.name} bei ${fmtPos(b.position)}`]
  const props = b.getProperties?.()
  if (props && Object.keys(props).length) parts.push(`Zustand: ${JSON.stringify(props)}`)
  const signText = b.getSignText?.()
  if (signText && signText.some?.(t => t)) parts.push(`Schild: ${signText.filter(Boolean).join(' / ')}`)
  parts.push(`Lichtlevel ${b.light ?? '?'} | Himmelslicht ${b.skyLight ?? '?'}`)
  return parts.join('\n')
}
