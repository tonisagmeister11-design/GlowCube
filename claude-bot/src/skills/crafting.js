// Craften und Schmelzen.
import pf from 'mineflayer-pathfinder'
import { gotoGoal } from './movement.js'
import { freeSpotNearby, placeAt } from './building.js'
import {
  ActionAborted, SkillError, checkAbort, countItem, dist, fmtPos, inventorySummary, itemDef, sleep
} from './util.js'

const { goals } = pf

// Findet eine Station (Werkbank, Ofen) in der Naehe oder stellt eine aus dem Inventar hin.
async function ensureStation (bot, blockNames, placeItem, signal) {
  const ids = blockNames.map(n => bot.registry.blocksByName[n]?.id).filter(id => id !== undefined)
  const near = bot.findBlock({ matching: ids, maxDistance: 32 })
  let block = near
  if (!block) {
    if (!countItem(bot, placeItem)) return null
    const spot = freeSpotNearby(bot)
    if (!spot) throw new SkillError(`Kein Platz, um ${placeItem} hinzustellen.`)
    await placeAt(bot, { item: placeItem, x: spot.x, y: spot.y, z: spot.z }, signal)
    block = bot.blockAt(spot)
  }
  if (dist(bot, block.position) > 3.5) {
    await gotoGoal(bot, new goals.GoalNear(block.position.x, block.position.y, block.position.z, 2),
      { signal, timeoutMs: 60000, what: `Der Weg zu ${block.name}` })
  }
  return block
}

function nameOf (bot, id) {
  return bot.registry.items[id]?.name ?? `#${id}`
}

// Was fehlt fuer ein Rezept? Nimmt das erste bekannte Rezept als Beispiel.
function missingFor (bot, itemId) {
  const recipes = bot.recipesAll(itemId, null, true)
  if (!recipes.length) return 'Dafuer gibt es kein Crafting-Rezept (vielleicht schmelzen, finden oder handeln?).'
  const have = id => bot.inventory.items().filter(i => i.type === id).reduce((s, i) => s + i.count, 0)
  // Anteil der Zutaten, die schon da sind - die naheliegendsten Rezepte zuerst
  const score = recipe => {
    const needs = recipe.delta.filter(d => d.count < 0)
    return needs.reduce((s, d) => s + Math.min(have(d.id) / -d.count, 1), 0) / Math.max(needs.length, 1)
  }
  const options = [...recipes].sort((a, b) => score(b) - score(a)).slice(0, 3).map(recipe => {
    const needs = recipe.delta.filter(d => d.count < 0)
      .map(d => `${-d.count}x ${nameOf(bot, d.id)} (habe ${have(d.id)})`)
    return `${needs.join(' + ')}${recipe.requiresTable ? ' an der Werkbank' : ''}`
  })
  return `Mir fehlen Zutaten. Rezept(e): ${[...new Set(options)].join(' ODER ')}`
}

export async function craft (bot, { item, count = 1 }, signal) {
  const def = itemDef(bot, item)
  const wanted = Math.min(Math.max(Number(count) || 1, 1), 576)
  const before = countItem(bot, def.name)

  bot.currentActivity = `craftet ${def.name}`
  try {
    let table = null
    let recipes = bot.recipesFor(def.id, null, 1, null)
    if (!recipes.length) {
      const needsTable = bot.recipesAll(def.id, null, true).some(r => r.requiresTable)
      if (!needsTable) throw new SkillError(missingFor(bot, def.id))
      table = await ensureStation(bot, ['crafting_table'], 'crafting_table', signal)
      if (!table) {
        throw new SkillError(`Fuer ${def.name} brauche ich eine Werkbank - keine in der Naehe und keine im Inventar. ` +
          'Eine Werkbank ist 4x Bretter (planks).')
      }
      recipes = bot.recipesFor(def.id, null, 1, table)
      if (!recipes.length) throw new SkillError(missingFor(bot, def.id))
    }
    checkAbort(signal)

    const recipe = recipes[0]
    const perCraft = recipe.result.count
    // Wie oft reichen die Zutaten?
    let possible = Infinity
    for (const d of recipe.delta) {
      if (d.count >= 0) continue
      const have = bot.inventory.items().filter(i => i.type === d.id).reduce((s, i) => s + i.count, 0)
      possible = Math.min(possible, Math.floor(have / -d.count))
    }
    const times = Math.min(Math.ceil(wanted / perCraft), possible)
    if (times < 1) throw new SkillError(missingFor(bot, def.id))
    await bot.craft(recipe, times, table ?? undefined)
  } finally {
    bot.currentActivity = null
  }

  const made = countItem(bot, def.name) - before
  const note = made < wanted ? ` (wollte ${wanted}, Zutaten reichten nicht fuer mehr)` : ''
  return `${made}x ${def.name} gecraftet${note}. Inventar: ${inventorySummary(bot)}`
}

// Brennwert in "Items pro Stueck"
const FUELS = [
  ['coal', 8], ['charcoal', 8], ['coal_block', 80], ['blaze_rod', 12], ['lava_bucket', 100], ['dried_kelp_block', 20]
]

function pickFuel (bot, name, items) {
  if (name) {
    const def = itemDef(bot, name)
    return { def, perItem: FUELS.find(f => f[0] === def.name)?.[1] ?? 1.5 }
  }
  for (const [fuel, perItem] of FUELS) {
    if (countItem(bot, fuel)) return { def: bot.registry.itemsByName[fuel], perItem }
  }
  // Holz geht auch
  const wood = bot.inventory.items().find(i => /(_planks|_log|_wood|_stem)$/.test(i.name))
  if (wood) return { def: bot.registry.itemsByName[wood.name], perItem: wood.name.endsWith('_planks') ? 1.5 : 1.5 }
  const stick = countItem(bot, 'stick')
  if (stick) return { def: bot.registry.itemsByName.stick, perItem: 0.5 }
  throw new SkillError(`Kein Brennstoff im Inventar (Kohle, Holz, ...). Inventar: ${items}`)
}

// item leer = nur fertige Sachen aus dem naechsten Ofen holen.
export async function smelt (bot, { item, count = 1, fuel, wait }, signal) {
  const furnaceBlock = await ensureStation(bot, ['furnace', 'blast_furnace', 'smoker'], 'furnace', signal)
  if (!furnaceBlock) throw new SkillError('Kein Ofen in der Naehe und keiner im Inventar. Ein Ofen ist 8x cobblestone an der Werkbank.')
  checkAbort(signal)

  bot.currentActivity = 'benutzt den Ofen'
  const furnace = await bot.openFurnace(furnaceBlock)
  try {
    const taken = []
    const takeOutput = async () => {
      const out = furnace.outputItem()
      if (out) {
        await furnace.takeOutput()
        taken.push(`${out.count}x ${out.name}`)
      }
    }
    await takeOutput()
    if (!item) {
      return taken.length ? `Aus dem Ofen geholt: ${taken.join(', ')}.` : 'Im Ofen war nichts Fertiges.'
    }

    const def = itemDef(bot, item)
    const have = countItem(bot, def.name)
    if (!have) throw new SkillError(`Ich habe kein ${def.name}.`)
    const n = Math.min(Math.max(Number(count) || 1, 1), have, 64)
    const f = pickFuel(bot, fuel, inventorySummary(bot))
    const fuelNeeded = Math.min(Math.ceil(n / f.perItem), countItem(bot, f.def.name))
    if (!furnace.fuelItem()) await furnace.putFuel(f.def.id, null, fuelNeeded)
    await furnace.putInput(def.id, null, n)

    const shouldWait = wait ?? n <= 8
    if (!shouldWait) {
      return `${n}x ${def.name} liegt im Ofen bei ${fmtPos(furnaceBlock.position)} (dauert ca. ${n * 10} s). ` +
        'Spaeter mit smelt ohne item abholen.'
    }
    const deadline = Date.now() + n * 10000 + 15000
    let done = 0
    while (done < n && Date.now() < deadline) {
      await sleep(1000, signal)
      const out = furnace.outputItem()
      if (out && (out.count >= n - done || !furnace.inputItem())) {
        done += out.count
        await takeOutput()
      }
    }
    await takeOutput()
    return `Geschmolzen und genommen: ${taken.join(', ') || 'noch nichts fertig'}. Inventar: ${inventorySummary(bot)}`
  } catch (err) {
    if (err instanceof ActionAborted) return 'Schmelzen abgebrochen, der Rest bleibt im Ofen.'
    throw err
  } finally {
    furnace.close()
    bot.currentActivity = null
  }
}
