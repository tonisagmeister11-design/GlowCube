// Rechtsklick auf alles: Tueren, Hebel, Truhen, Betten, Tiere, Dorfbewohner, Angeln ...
import pf from 'mineflayer-pathfinder'
import { gotoGoal } from './movement.js'
import { findTarget } from './combat.js'
import {
  ActionAborted, SkillError, checkAbort, countItem, dist, findItems, fmtPos, inventorySummary, normName, sleep, vec
} from './util.js'

const { goals } = pf

async function reachBlock (bot, block, signal) {
  if (dist(bot, block.position.offset(0.5, 0.5, 0.5)) > 4) {
    await gotoGoal(bot, new goals.GoalLookAtBlock(block.position, bot.world, { reach: 4 }),
      { signal, timeoutMs: 60000, what: `Der Weg zu ${block.name}` })
  }
}

async function reachEntity (bot, entity, signal, range = 2.5) {
  if (dist(bot, entity.position) > range + 0.5) {
    const p = entity.position
    await gotoGoal(bot, new goals.GoalNear(p.x, p.y, p.z, range), { signal, timeoutMs: 60000 })
  }
}

function blockAtOrThrow (bot, x, y, z) {
  const b = bot.blockAt(vec(x, y, z))
  if (!b) throw new SkillError('Dieser Chunk ist nicht geladen.')
  return b
}

async function holdItem (bot, item) {
  if (!item) return
  const stack = findItems(bot, item)[0]
  if (!stack) throw new SkillError(`Ich habe kein ${item}.`)
  await bot.equip(stack, 'hand')
}

// Rechtsklick auf einen Block: Tuer, Hebel, Knopf, Falltuer, Glocke, ...
export async function activateBlock (bot, { x, y, z, item }, signal) {
  const b = blockAtOrThrow(bot, x, y, z)
  await reachBlock(bot, b, signal)
  await holdItem(bot, item)
  await bot.lookAt(b.position.offset(0.5, 0.5, 0.5), true)
  await bot.activateBlock(b)
  await sleep(250, signal)
  const after = bot.blockAt(b.position)
  const props = after?.getProperties?.()
  return `Rechtsklick auf ${b.name} bei ${fmtPos(b.position)}${item ? ` mit ${item}` : ''}.` +
    (props && Object.keys(props).length ? ` Zustand jetzt: ${JSON.stringify(props)}` : '')
}

// Item benutzen (Rechtsklick in die Luft): Bogen, Schild, Trank, Enderperle, Eimer, Schneeball ...
export async function useItem (bot, { item, hold_ms = 0, look_at, off_hand = false }, signal) {
  await holdItem(bot, item)
  if (look_at) {
    try {
      const e = findTarget(bot, look_at, 64)
      const d = dist(bot, e.position)
      // Bei Pfeilen etwas hoeher zielen, je weiter weg
      await bot.lookAt(e.position.offset(0, (e.height ?? 1.6) * 0.7 + d * 0.03, 0), true)
    } catch (err) {
      const parts = String(look_at).split(/[\s,]+/).map(Number)
      if (parts.length === 3 && parts.every(n => !Number.isNaN(n))) await bot.lookAt(vec(...parts), true)
      else throw err
    }
  }
  bot.activateItem(!!off_hand)
  const ms = Math.min(Math.max(Number(hold_ms) || 0, 0), 10000)
  if (ms > 0) {
    try {
      await sleep(ms, signal)
    } finally {
      bot.deactivateItem()
    }
  }
  return `${bot.heldItem?.name ?? 'Hand'} benutzt${ms ? ` (${ms} ms gehalten)` : ''}.`
}

// Rechtsklick auf ein Wesen: fuettern/zuechten, scheren, melken, Namensschild, Leine, reiten ...
export async function interactEntity (bot, { target, item }, signal) {
  const entity = findTarget(bot, target)
  await reachEntity(bot, entity, signal)
  await holdItem(bot, item)
  await bot.lookAt(entity.position.offset(0, (entity.height ?? 1) * 0.6, 0), true)
  bot.activateEntity(entity)
  await sleep(300, signal)
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow)
  return `Rechtsklick auf ${entity.username || entity.name} bei ${fmtPos(entity.position)}${item ? ` mit ${item}` : ''}.`
}

export async function mount (bot, { target }, signal) {
  const entity = findTarget(bot, target)
  await reachEntity(bot, entity, signal, 2)
  bot.mount(entity)
  await sleep(500, signal)
  return bot.vehicle ? `Sitze jetzt auf ${entity.name}.` : `Aufsteigen auf ${entity.name} hat nicht geklappt.`
}

export function dismount (bot) {
  if (!bot.vehicle) return 'Ich sitze auf nichts.'
  bot.dismount()
  return 'Abgestiegen.'
}

export async function sleepInBed (bot, _args, signal) {
  const bed = bot.findBlock({ matching: b => bot.isABed(b), maxDistance: 48 })
  if (!bed) throw new SkillError('Kein Bett in der Naehe. Ein Bett ist 3x Wolle + 3x Bretter.')
  await reachBlock(bot, bed, signal)
  try {
    await bot.sleep(bed)
  } catch (err) {
    throw new SkillError(`Schlafen geht nicht: ${err.message}`)
  }
  return `Liege im Bett bei ${fmtPos(bed.position)}.`
}

export async function wake (bot) {
  if (!bot.isSleeping) return 'Ich schlafe gar nicht.'
  await bot.wake()
  return 'Aufgestanden.'
}

const CONTAINERS = ['chest', 'trapped_chest', 'barrel', 'ender_chest', 'hopper', 'dispenser', 'dropper']

function isContainer (b) {
  return b && (CONTAINERS.includes(b.name) || b.name.endsWith('shulker_box'))
}

// Truhen und Co.: ansehen, einlagern, herausnehmen.
export async function container (bot, { action = 'list', x, y, z, item, count }, signal) {
  let block
  if (x !== undefined && y !== undefined && z !== undefined) {
    block = blockAtOrThrow(bot, x, y, z)
    if (!isContainer(block)) throw new SkillError(`Bei ${fmtPos(block.position)} ist ${block.name}, kein Behaelter.`)
  } else {
    block = bot.findBlock({ matching: isContainer, maxDistance: 16 })
    if (!block) throw new SkillError('Kein Behaelter (Truhe, Fass, ...) im Umkreis von 16 Bloecken.')
  }
  await reachBlock(bot, block, signal)
  checkAbort(signal)

  const win = await bot.openContainer(block)
  try {
    const listing = () => {
      const counts = new Map()
      for (const i of win.containerItems()) counts.set(i.name, (counts.get(i.name) || 0) + i.count)
      return counts.size ? [...counts].map(([n, c]) => `${c}x ${n}`).join(', ') : 'leer'
    }
    const where = `${block.name} bei ${fmtPos(block.position)}`
    if (action === 'list') return `${where} enthaelt: ${listing()}`

    if (action === 'deposit') {
      const all = normName(item) === 'all'
      const stacks = all ? bot.inventory.items() : findItems(bot, item)
      if (!stacks.length) throw new SkillError(`Ich habe kein ${item}.`)
      const names = [...new Set(stacks.map(s => s.name))]
      let moved = 0
      for (const name of names) {
        const have = countItem(bot, name)
        const n = all || count === undefined || count === null ? have : Math.min(Number(count) - moved, have)
        if (n <= 0) break
        await win.deposit(bot.registry.itemsByName[name].id, null, n)
        moved += n
      }
      return `${moved} Items eingelagert. In ${where} jetzt: ${listing()}`
    }

    if (action === 'withdraw') {
      const all = normName(item) === 'all'
      const inside = win.containerItems().filter(i => all || i.name === normName(item) || i.name.includes(normName(item)))
      if (!inside.length) throw new SkillError(`${item} ist nicht in ${where}. Drin: ${listing()}`)
      let moved = 0
      for (const name of [...new Set(inside.map(i => i.name))]) {
        const avail = inside.filter(i => i.name === name).reduce((s, i) => s + i.count, 0)
        const n = all || count === undefined || count === null ? avail : Math.min(Number(count) - moved, avail)
        if (n <= 0) break
        try {
          await win.withdraw(bot.registry.itemsByName[name].id, null, n)
          moved += n
        } catch (err) {
          if (/full/i.test(err.message)) return `Inventar ist voll. ${moved} Items genommen.`
          throw err
        }
      }
      return `${moved} Items genommen. Inventar: ${inventorySummary(bot)}`
    }
    throw new SkillError('action muss list, deposit oder withdraw sein.')
  } finally {
    win.close()
  }
}

export async function fish (bot, { count = 1 }, signal) {
  const rod = findItems(bot, 'fishing_rod')[0]
  if (!rod) throw new SkillError('Ich habe keine Angel (fishing_rod = 3 Stoecke + 2 Faeden).')
  await bot.equip(rod, 'hand')
  const water = bot.findBlock({ matching: b => b.name === 'water', maxDistance: 8 })
  if (!water) throw new SkillError('Kein Wasser in der Naehe (8 Bloecke).')
  await bot.lookAt(water.position.offset(0.5, 0.5, 0.5), true)

  const n = Math.min(Math.max(Number(count) || 1, 1), 20)
  let caught = 0
  bot.currentActivity = 'angelt'
  const before = inventorySummary(bot)
  try {
    for (let i = 0; i < n; i++) {
      checkAbort(signal)
      let timer, onAbort
      const stopper = new Promise((resolve, reject) => {
        // Nochmal Rechtsklick holt die Angel ein
        onAbort = () => { bot.activateItem(); reject(new ActionAborted()) }
        signal?.addEventListener('abort', onAbort, { once: true })
        timer = setTimeout(() => { bot.activateItem(); reject(new SkillError('Nach 90 s hat nichts angebissen.')) }, 90000)
      })
      try {
        await Promise.race([bot.fish(), stopper])
      } finally {
        clearTimeout(timer)
        signal?.removeEventListener('abort', onAbort)
        stopper.catch(() => {})
      }
      caught++
      await sleep(500, signal)
    }
  } catch (err) {
    if (!(err instanceof ActionAborted) && caught === 0) throw err
  } finally {
    bot.currentActivity = null
  }
  return `${caught}x etwas gefangen. Vorher: ${before} | Jetzt: ${inventorySummary(bot)}`
}

// Handeln mit Dorfbewohnern. Ohne index: Angebote auflisten.
export async function trade (bot, { target = 'villager', index, times = 1 }, signal) {
  const entity = findTarget(bot, target)
  if (!['villager', 'wandering_trader'].includes(entity.name)) throw new SkillError(`${entity.name} handelt nicht.`)
  await reachEntity(bot, entity, signal, 2)
  const villager = await bot.openVillager(entity)
  try {
    const describe = (t, i) => {
      const inputs = [t.inputItem1, t.inputItem2].filter(Boolean).map(it => `${it.count}x ${it.name}`).join(' + ')
      return `#${i}: ${inputs} -> ${t.outputItem.count}x ${t.outputItem.name}${t.tradeDisabled ? ' (ausverkauft)' : ''}`
    }
    if (index === undefined || index === null) {
      return `Angebote von ${entity.name}:\n${villager.trades.map(describe).join('\n')}`
    }
    const t = villager.trades[index]
    if (!t) throw new SkillError(`Kein Angebot #${index}.`)
    if (t.tradeDisabled) throw new SkillError('Dieses Angebot ist ausverkauft.')
    await bot.trade(villager, index, Math.min(Math.max(Number(times) || 1, 1), 64))
    return `Gehandelt: ${describe(t, index)} x${times}. Inventar: ${inventorySummary(bot)}`
  } catch (err) {
    if (err instanceof SkillError) throw err
    throw new SkillError(`Handel ging nicht (genug Zutaten?): ${err.message}`)
  } finally {
    villager.close()
  }
}
