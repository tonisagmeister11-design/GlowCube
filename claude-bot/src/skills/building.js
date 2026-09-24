// Bloecke setzen - einzeln oder als ganzer Bauplan.
import pf from 'mineflayer-pathfinder'
import { Vec3 } from 'vec3'
import { gotoGoal } from './movement.js'
import {
  ActionAborted, SkillError, checkAbort, describeError, dist, findItems, fmtPos, normName, sleep, vec
} from './util.js'

const { goals } = pf

const REPLACEABLE = new Set([
  'air', 'cave_air', 'void_air', 'water', 'lava', 'short_grass', 'grass', 'tall_grass', 'fern', 'large_fern',
  'dead_bush', 'snow', 'vine', 'seagrass', 'tall_seagrass', 'fire', 'soul_fire', 'glow_lichen', 'bush',
  'short_dry_grass', 'tall_dry_grass', 'leaf_litter'
])

export function isReplaceable (block) {
  return !block || REPLACEABLE.has(block.name)
}

function isSolid (block) {
  return block && !isReplaceable(block) && block.boundingBox === 'block'
}

// Erst von unten, dann seitlich, zuletzt von oben dranbauen.
const FACES = [
  new Vec3(0, -1, 0), new Vec3(1, 0, 0), new Vec3(-1, 0, 0),
  new Vec3(0, 0, 1), new Vec3(0, 0, -1), new Vec3(0, 1, 0)
]

function findReference (bot, pos) {
  for (const offset of FACES) {
    const ref = bot.blockAt(pos.plus(offset))
    if (isSolid(ref)) return { ref, face: offset.scaled(-1) }
  }
  return null
}

function occupiesTarget (bot, pos) {
  const p = bot.entity.position
  const bx = Math.floor(p.x)
  const bz = Math.floor(p.z)
  const by = Math.floor(p.y)
  return bx === pos.x && bz === pos.z && (by === pos.y || by + 1 === pos.y)
}

export async function placeAt (bot, { item, x, y, z }, signal) {
  checkAbort(signal)
  const pos = vec(Math.floor(x), Math.floor(y), Math.floor(z))
  const current = bot.blockAt(pos)
  if (!current) throw new SkillError(`Chunk bei ${fmtPos(pos)} ist nicht geladen.`)
  const name = normName(item)
  if (current.name === name) return `${name} steht bei ${fmtPos(pos)} schon.`
  if (!isReplaceable(current)) throw new SkillError(`Bei ${fmtPos(pos)} ist schon ${current.name}. Erst abbauen.`)

  const stack = findItems(bot, name)[0]
  if (!stack) throw new SkillError(`Ich habe kein ${name} im Inventar.`)

  const reference = findReference(bot, pos)
  if (!reference) throw new SkillError(`Bei ${fmtPos(pos)} gibt es keinen Nachbarblock zum Dranbauen. Von unten nach oben bauen.`)

  if (dist(bot, pos.offset(0.5, 0.5, 0.5)) > 4.2) {
    await gotoGoal(bot, new goals.GoalNear(pos.x, pos.y, pos.z, 3), { signal, timeoutMs: 45000, movements: 'work' })
  }
  if (occupiesTarget(bot, pos)) {
    await gotoGoal(bot, new goals.GoalInvert(new goals.GoalNear(pos.x, pos.y, pos.z, 1.5)),
      { signal, timeoutMs: 10000, movements: 'work' })
    try { bot.pathfinder.setGoal(null) } catch {}
  }
  checkAbort(signal)

  await bot.equip(stack, 'hand')
  try {
    await bot.placeBlock(reference.ref, reference.face)
  } catch (err) {
    // Manche Server bestaetigen langsam - nachschauen, ob der Block trotzdem steht.
    await sleep(300, signal)
    const after = bot.blockAt(pos)
    if (!after || isReplaceable(after)) throw new SkillError(`Setzen ging nicht: ${describeError(err)}`)
  }
  return `${stack.name} bei ${fmtPos(pos)} gesetzt.`
}

// Bauplan: Liste von {x,y,z,item}. Wird von unten nach oben abgearbeitet.
export async function build (bot, { blocks }, signal) {
  if (!Array.isArray(blocks) || !blocks.length) throw new SkillError('Leerer Bauplan.')
  if (blocks.length > 512) throw new SkillError('Hoechstens 512 Bloecke pro Aufruf - teile den Bau auf.')
  const plan = blocks
    .map(b => ({ ...b, x: Math.floor(b.x), y: Math.floor(b.y), z: Math.floor(b.z) }))
    .sort((a, b) => a.y - b.y || dist(bot, vec(a.x, a.y, a.z)) - dist(bot, vec(b.x, b.y, b.z)))

  let placed = 0
  const failed = []
  bot.currentActivity = `baut (${placed}/${plan.length})`
  try {
    for (let pass = 0; pass < 2; pass++) {
      const retry = []
      for (const step of (pass === 0 ? plan : failed.splice(0))) {
        checkAbort(signal)
        try {
          await placeAt(bot, step, signal)
          placed++
          bot.currentActivity = `baut (${placed}/${plan.length})`
        } catch (err) {
          if (err instanceof ActionAborted) throw err
          step.error = err.message
          retry.push(step)
          // Material alle? Dann hat Weitermachen keinen Sinn.
          if (/kein .* im Inventar/.test(err.message)) {
            failed.push(...retry)
            throw new SkillError(`${placed}/${plan.length} gesetzt, dann ging das Material aus: ${err.message}`)
          }
        }
      }
      failed.push(...retry)
      if (!failed.length) break
    }
  } catch (err) {
    if (err instanceof ActionAborted) return `Abgebrochen nach ${placed}/${plan.length} Bloecken.`
    throw err
  } finally {
    bot.currentActivity = null
  }
  if (!failed.length) return `Fertig: alle ${placed} Bloecke gesetzt.`
  const sample = failed.slice(0, 5).map(f => `${f.x} ${f.y} ${f.z}: ${f.error}`).join('; ')
  return `${placed}/${plan.length} gesetzt. ${failed.length} gingen nicht, z.B. ${sample}`
}

// Sucht einen freien Platz neben dem Bot, um z.B. eine Werkbank hinzustellen.
export function freeSpotNearby (bot) {
  const base = bot.entity.position.floored()
  const offsets = [[1, 0], [-1, 0], [0, 1], [0, -1], [1, 1], [-1, -1], [1, -1], [-1, 1], [2, 0], [-2, 0], [0, 2], [0, -2]]
  for (const dy of [0, 1, -1]) {
    for (const [dx, dz] of offsets) {
      const pos = base.offset(dx, dy, dz)
      if (isReplaceable(bot.blockAt(pos)) && isSolid(bot.blockAt(pos.offset(0, -1, 0))) && !occupiesTarget(bot, pos)) return pos
    }
  }
  return null
}
