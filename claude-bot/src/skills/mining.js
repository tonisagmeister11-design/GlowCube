// Abbauen und Einsammeln.
import pf from 'mineflayer-pathfinder'
import { gotoGoal } from './movement.js'
import { blockMatcher } from './world.js'
import {
  ActionAborted, SkillError, checkAbort, countItem, describeError, dist, fmtPos, inventorySummary, sleep, vec
} from './util.js'

const { goals } = pf

async function equipBestTool (bot, block) {
  const tool = bot.pathfinder.bestHarvestTool(block)
  if (tool && bot.heldItem?.slot !== tool.slot) await bot.equip(tool, 'hand')
  // Braucht der Block ein bestimmtes Werkzeug (z.B. Erz eine Spitzhacke)?
  if (block.harvestTools && !block.canHarvest(bot.heldItem ? bot.heldItem.type : null)) {
    const needed = Object.keys(block.harvestTools).map(id => bot.registry.items[id]?.name).filter(Boolean)
    throw new SkillError(`${block.name} laesst nichts fallen ohne passendes Werkzeug. Geht mit: ${needed.slice(0, 4).join(', ')}.`)
  }
}

// Sammelt Items in der Naehe einer Position auf.
async function pickupNear (bot, pos, signal, radius = 4) {
  await sleep(250, signal)
  for (let round = 0; round < 6; round++) {
    const item = Object.values(bot.entities)
      .filter(e => e.name === 'item' && e.position.distanceTo(pos) <= radius)
      .sort((a, b) => dist(bot, a.position) - dist(bot, b.position))[0]
    if (!item) return
    if (dist(bot, item.position) < 0.9) { await sleep(150, signal); continue }
    try {
      await gotoGoal(bot, new goals.GoalNear(item.position.x, item.position.y, item.position.z, 0.5),
        { signal, timeoutMs: 8000, movements: 'work' })
    } catch (err) {
      if (err instanceof ActionAborted) throw err
      return
    }
  }
}

async function digOne (bot, block, signal) {
  checkAbort(signal)
  await gotoGoal(bot, new goals.GoalLookAtBlock(block.position, bot.world, { reach: 4.5 }), {
    signal, timeoutMs: 45000, movements: 'work', what: `Der Weg zu ${block.name}`
  })
  // Block koennte sich inzwischen veraendert haben
  const fresh = bot.blockAt(block.position)
  if (!fresh || fresh.type !== block.type) return false
  await equipBestTool(bot, fresh)
  checkAbort(signal)
  const onAbort = () => { try { bot.stopDigging() } catch {} }
  signal?.addEventListener('abort', onAbort, { once: true })
  try {
    await bot.dig(fresh, true)
  } catch (err) {
    if (signal?.aborted) throw new ActionAborted()
    throw new SkillError(`Abbauen ging nicht: ${describeError(err)}`)
  } finally {
    signal?.removeEventListener('abort', onAbort)
  }
  await pickupNear(bot, fresh.position, signal)
  return true
}

export async function mine (bot, { block, count = 1, max_distance = 48 }, signal) {
  const ids = blockMatcher(bot, block)
  const target = Math.min(Math.max(Number(count) || 1, 1), 64)
  const skip = new Set()
  let mined = 0
  let failures = 0
  let lastError = null

  bot.currentActivity = `baut ${block} ab (${mined}/${target})`
  try {
    while (mined < target) {
      checkAbort(signal)
      const positions = bot.findBlocks({ matching: [...ids], maxDistance: Math.min(max_distance, 96), count: 64 })
        .filter(p => !skip.has(p.toString()))
        .sort((a, b) => dist(bot, a) - dist(bot, b))
      if (!positions.length) break
      const b = bot.blockAt(positions[0])
      skip.add(positions[0].toString())
      try {
        if (await digOne(bot, b, signal)) {
          mined++
          bot.currentActivity = `baut ${block} ab (${mined}/${target})`
        }
      } catch (err) {
        if (err instanceof ActionAborted) throw err
        lastError = err.message
        // Fehlendes Werkzeug wird nicht besser, wenn man es 5x probiert
        if (/Werkzeug/.test(err.message)) throw err
        if (++failures >= 5) break
      }
    }
  } catch (err) {
    if (err instanceof ActionAborted) {
      return `Abgebrochen nach ${mined} Bloecken. Inventar: ${inventorySummary(bot)}`
    }
    throw err
  } finally {
    bot.currentActivity = null
  }

  if (mined === 0) {
    throw new SkillError(lastError
      ? `Konnte kein ${block} abbauen: ${lastError}`
      : `Kein ${block} im Umkreis von ${max_distance} Bloecken gefunden.`)
  }
  const note = mined < target ? ` (wollte ${target}${lastError ? `, Problem: ${lastError}` : ', mehr gab es nicht in der Naehe'})` : ''
  return `${mined}x ${block} abgebaut${note}. Inventar: ${inventorySummary(bot)}`
}

export async function digAt (bot, { x, y, z }, signal) {
  const b = bot.blockAt(vec(x, y, z))
  if (!b) throw new SkillError('Dieser Chunk ist nicht geladen.')
  if (b.name.endsWith('air')) return `Bei ${fmtPos(b.position)} ist schon Luft.`
  if (b.name === 'bedrock' || b.hardness === null || b.hardness < 0) throw new SkillError(`${b.name} ist unzerstoerbar.`)
  const before = countItem(bot, b.name)
  bot.currentActivity = `baut ${b.name} ab`
  try {
    await digOne(bot, b, signal)
  } finally {
    bot.currentActivity = null
  }
  const got = countItem(bot, b.name) - before
  return `${b.name} bei ${fmtPos(b.position)} abgebaut${got > 0 ? `, ${got} eingesammelt` : ''}.`
}

export async function collectItems (bot, { radius = 16, item }, signal) {
  const r = Math.min(Number(radius) || 16, 48)
  const filter = item ? String(item).toLowerCase() : null
  let picked = 0
  const tried = new Set()
  bot.currentActivity = 'sammelt Items ein'
  try {
    for (let i = 0; i < 40; i++) {
      checkAbort(signal)
      const drop = Object.values(bot.entities)
        .filter(e => e.name === 'item' && !tried.has(e.id) && dist(bot, e.position) <= r)
        .filter(e => !filter || e.getDroppedItem?.()?.name?.includes(filter))
        .sort((a, b) => dist(bot, a.position) - dist(bot, b.position))[0]
      if (!drop) break
      tried.add(drop.id)
      try {
        await gotoGoal(bot, new goals.GoalNear(drop.position.x, drop.position.y, drop.position.z, 0.5),
          { signal, timeoutMs: 15000, movements: 'work' })
        await sleep(200, signal)
        if (!bot.entities[drop.id]) picked++
      } catch (err) {
        if (err instanceof ActionAborted) throw err
      }
    }
  } finally {
    bot.currentActivity = null
  }
  return picked ? `${picked} Items eingesammelt. Inventar: ${inventorySummary(bot)}` : 'Keine Items zum Einsammeln gefunden.'
}
