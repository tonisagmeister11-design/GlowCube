// Kleinkram, den alle Faehigkeiten brauchen.
import { Vec3 } from 'vec3'

export class ActionAborted extends Error {
  constructor (reason = 'abgebrochen') {
    super(reason)
    this.name = 'ActionAborted'
  }
}

// Ein Fehler, dessen Text direkt an Claude geht - kein Stacktrace, nur was schiefging.
export class SkillError extends Error {
  constructor (message) {
    super(message)
    this.name = 'SkillError'
  }
}

export function checkAbort (signal) {
  if (signal?.aborted) throw new ActionAborted(signal.reason?.message || String(signal.reason || 'abgebrochen'))
}

export function sleep (ms, signal) {
  return new Promise((resolve, reject) => {
    if (signal?.aborted) return reject(new ActionAborted())
    const timer = setTimeout(done, ms)
    function done () {
      signal?.removeEventListener('abort', onAbort)
      resolve()
    }
    function onAbort () {
      clearTimeout(timer)
      reject(new ActionAborted())
    }
    signal?.addEventListener('abort', onAbort, { once: true })
  })
}

// Haelt alles an, was der Bot gerade koerperlich tut.
export function stopBody (bot) {
  try { bot.pathfinder?.setGoal(null) } catch {}
  try { bot.pathfinder?.stop() } catch {}
  try { bot.stopDigging() } catch {}
  try { bot.clearControlStates() } catch {}
  try { bot.deactivateItem() } catch {}
}

export function vec (x, y, z) {
  return new Vec3(Number(x), Number(y), Number(z))
}

export function fmtPos (p) {
  if (!p) return '?'
  return `${Math.floor(p.x)} ${Math.floor(p.y)} ${Math.floor(p.z)}`
}

export function dist (bot, p) {
  return bot.entity.position.distanceTo(p)
}

// "Eichenholz", "oak log", "minecraft:oak_log" -> "oak_log"
export function normName (name) {
  return String(name || '').trim().toLowerCase().replace(/^minecraft:/, '').replace(/[\s-]+/g, '_')
}

export function itemDef (bot, name) {
  const n = normName(name)
  const def = bot.registry.itemsByName[n]
  if (!def) throw new SkillError(`Unbekanntes Item "${name}". Nutze die englischen Minecraft-IDs, z.B. oak_log, cobblestone, iron_pickaxe.`)
  return def
}

export function blockDef (bot, name) {
  const n = normName(name)
  const def = bot.registry.blocksByName[n]
  if (!def) throw new SkillError(`Unbekannter Block "${name}". Nutze die englischen Minecraft-IDs, z.B. oak_log, stone, iron_ore.`)
  return def
}

// Findet Items im Inventar. Erlaubt auch Oberbegriffe wie "log" oder "planks".
export function findItems (bot, name) {
  const n = normName(name)
  const items = bot.inventory.items()
  const exact = items.filter(i => i.name === n)
  if (exact.length) return exact
  return items.filter(i => i.name.endsWith('_' + n) || i.name.includes(n))
}

export function countItem (bot, name) {
  const n = normName(name)
  return bot.inventory.items().filter(i => i.name === n).reduce((sum, i) => sum + i.count, 0)
}

export function inventorySummary (bot) {
  const counts = new Map()
  for (const item of bot.inventory.items()) counts.set(item.name, (counts.get(item.name) || 0) + item.count)
  if (!counts.size) return 'leer'
  return [...counts].map(([name, count]) => `${count}x ${name}`).join(', ')
}

// Faengt Mineflayer-Fehler ab und macht daraus etwas Lesbares.
export function describeError (err) {
  if (!err) return 'unbekannter Fehler'
  if (err.name === 'NoPath') return 'kein Weg dorthin gefunden'
  if (err.name === 'Timeout') return 'Wegsuche hat zu lange gedauert'
  if (err.name === 'GoalChanged' || err.name === 'PathStopped') return 'Weg wurde abgebrochen'
  return err.message || String(err)
}

// Startet eine Aufgabe mit Zeitlimit. Wird sie abgebrochen, steht der Bot danach still.
export async function withTimeout (bot, promise, ms, what) {
  let timer
  const timeout = new Promise((resolve, reject) => {
    timer = setTimeout(() => {
      stopBody(bot)
      reject(new SkillError(`${what} hat laenger als ${Math.round(ms / 1000)} s gedauert und wurde abgebrochen`))
    }, ms)
  })
  try {
    return await Promise.race([promise, timeout])
  } finally {
    clearTimeout(timer)
  }
}
