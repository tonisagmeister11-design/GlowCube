// Kaempfen.
import pf from 'mineflayer-pathfinder'
import { isHostile } from './world.js'
import { useMovements } from './movement.js'
import { ActionAborted, SkillError, checkAbort, dist, fmtPos, normName, sleep } from './util.js'

const { goals } = pf

const WEAPONS = [
  'netherite_sword', 'diamond_sword', 'netherite_axe', 'iron_sword', 'diamond_axe', 'iron_axe',
  'stone_sword', 'golden_sword', 'stone_axe', 'wooden_sword', 'golden_axe', 'wooden_axe', 'mace', 'trident'
]

export async function equipBestWeapon (bot) {
  for (const name of WEAPONS) {
    const item = bot.inventory.items().find(i => i.name === name)
    if (item) {
      if (bot.heldItem?.name !== name) await bot.equip(item, 'hand')
      return name
    }
  }
  return null
}

// Ziel kann ein Spielername, ein Mob-Typ ("zombie") oder "hostile" (naechstes Monster) sein.
export function findTarget (bot, target, radius = 32) {
  const wanted = String(target || '').trim()
  if (!wanted) throw new SkillError('Kein Ziel angegeben.')
  const playerKey = Object.keys(bot.players).find(n => n.toLowerCase() === wanted.toLowerCase())
  if (playerKey) {
    if (playerKey === bot.username) throw new SkillError('Ich greife mich nicht selbst an.')
    const e = bot.players[playerKey].entity
    if (!e) throw new SkillError(`${playerKey} ist ausser Sichtweite.`)
    return e
  }
  const n = normName(wanted)
  const anyHostile = ['hostile', 'monster', 'monsters', 'mob', 'mobs', 'feind'].includes(n)
  const entity = bot.nearestEntity(e =>
    e !== bot.entity && e.type !== 'player' && e.position && dist(bot, e.position) <= radius &&
    (anyHostile ? isHostile(e) : (e.name === n || normName(e.displayName) === n)))
  if (!entity) throw new SkillError(`Kein ${wanted} im Umkreis von ${radius} Bloecken.`)
  return entity
}

function alive (bot, entity) {
  return !!bot.entities[entity.id] && entity.isValid !== false && (entity.health === undefined || entity.health > 0)
}

// Kaempft gegen ein Wesen, bis es weg ist oder die Zeit um ist.
export async function fight (bot, entity, { signal, timeoutMs = 60000 } = {}) {
  await equipBestWeapon(bot)
  useMovements(bot, 'safe')
  const deadline = Date.now() + timeoutMs
  let lastHit = 0
  let following = false
  try {
    while (alive(bot, entity)) {
      checkAbort(signal)
      if (Date.now() > deadline) return false
      if (bot.health <= 0) return false
      const d = dist(bot, entity.position)
      if (d > 40) return false
      if (d > 2.8 && !following) {
        bot.pathfinder.setGoal(new goals.GoalFollow(entity, 1.5), true)
        following = true
      }
      if (d <= 3.2) {
        await bot.lookAt(entity.position.offset(0, (entity.height ?? 1.6) * 0.8, 0), true)
        // Abklingzeit der Waffe abwarten, sonst macht der Schlag kaum Schaden
        if (Date.now() - lastHit >= 620) {
          bot.attack(entity)
          lastHit = Date.now()
        }
      }
      await sleep(80, signal)
    }
    return true
  } finally {
    try { bot.pathfinder.setGoal(null) } catch {}
  }
}

export async function attack (bot, { target, count = 1 }, signal) {
  const n = Math.min(Math.max(Number(count) || 1, 1), 30)
  let killed = 0
  bot.currentActivity = `kaempft gegen ${target}`
  try {
    for (let i = 0; i < n; i++) {
      checkAbort(signal)
      let entity
      try {
        entity = findTarget(bot, target)
      } catch (err) {
        if (killed > 0) break
        throw err
      }
      const name = entity.username || entity.name
      const won = await fight(bot, entity, { signal, timeoutMs: 60000 })
      if (!won) {
        if (bot.health <= 0) throw new SkillError('Ich bin dabei gestorben.')
        throw new SkillError(`${name} bei ${fmtPos(entity.position)} konnte ich nicht besiegen (zu weit weg oder Zeit abgelaufen). ${killed} besiegt.`)
      }
      killed++
      await sleep(300, signal)
    }
  } catch (err) {
    if (err instanceof ActionAborted) return `Kampf abgebrochen, ${killed} besiegt.`
    throw err
  } finally {
    bot.currentActivity = null
  }
  return `${killed}x ${target} besiegt. Leben ${Math.round(bot.health)}/20.`
}
