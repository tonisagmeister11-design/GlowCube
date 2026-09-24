// Inventar: ansehen, ausruesten, wegwerfen, weitergeben, essen.
import pf from 'mineflayer-pathfinder'
import { gotoGoal, playerEntity } from './movement.js'
import { SkillError, countItem, findItems, inventorySummary, normName, sleep } from './util.js'

const { goals } = pf

const DESTS = { hand: 'hand', 'off-hand': 'off-hand', offhand: 'off-hand', head: 'head', torso: 'torso', chest: 'torso', legs: 'legs', feet: 'feet' }

export function inventory (bot) {
  const slots = bot.inventory.slots
  const armor = { Kopf: 5, Brust: 6, Beine: 7, Fuesse: 8 }
  const worn = Object.entries(armor).map(([label, slot]) => `${label}: ${slots[slot]?.name ?? '-'}`).join(', ')
  const lines = bot.inventory.items().map(i => {
    const extra = []
    if (i.durabilityUsed && i.maxDurability) extra.push(`Haltbarkeit ${i.maxDurability - i.durabilityUsed}/${i.maxDurability}`)
    const enchants = i.enchants?.map(e => `${e.name} ${e.lvl}`)
    if (enchants?.length) extra.push(enchants.join(' '))
    if (i.customName) extra.push(`Name "${i.customName}"`)
    return `Slot ${i.slot}: ${i.count}x ${i.name}${extra.length ? ` (${extra.join(', ')})` : ''}`
  })
  return [
    `In der Hand: ${bot.heldItem?.name ?? 'nichts'} | Nebenhand: ${slots[45]?.name ?? 'nichts'}`,
    `Ruestung: ${worn}`,
    `Freie Plaetze: ${bot.inventory.emptySlotCount()}`,
    ...(lines.length ? lines : ['Inventar ist leer.'])
  ].join('\n')
}

export async function equip (bot, { item, slot = 'hand' }) {
  const dest = DESTS[String(slot).toLowerCase()]
  if (!dest) throw new SkillError('Slot muss hand, off-hand, head, torso, legs oder feet sein.')
  const stack = findItems(bot, item)[0]
  if (!stack) throw new SkillError(`Ich habe kein ${item}.`)
  await bot.equip(stack, dest)
  return `${stack.name} ausgeruestet (${dest}).`
}

export async function unequip (bot, { slot = 'hand' }) {
  const dest = DESTS[String(slot).toLowerCase()]
  if (!dest) throw new SkillError('Slot muss hand, off-hand, head, torso, legs oder feet sein.')
  await bot.unequip(dest)
  return `${dest} ist jetzt leer.`
}

// "all" wirft alles weg, sonst die angegebene Menge.
async function tossItems (bot, item, count) {
  if (normName(item) === 'all') {
    let n = 0
    for (const stack of bot.inventory.items()) { await bot.tossStack(stack); n += stack.count }
    return `alles (${n} Items)`
  }
  const stacks = findItems(bot, item)
  if (!stacks.length) throw new SkillError(`Ich habe kein ${item}. Inventar: ${inventorySummary(bot)}`)
  const name = stacks[0].name
  const have = countItem(bot, name)
  const n = count === undefined || count === null || count === 'all' ? have : Math.min(Number(count), have)
  await bot.toss(stacks[0].type, null, n)
  return `${n}x ${name}`
}

export async function toss (bot, { item, count }) {
  const what = await tossItems(bot, item, count)
  return `${what} fallen gelassen.`
}

export async function giveTo (bot, { player, item, count }, signal) {
  const entity = playerEntity(bot, player)
  const p = entity.position
  await gotoGoal(bot, new goals.GoalNear(p.x, p.y, p.z, 2), { signal, timeoutMs: 60000 })
  await bot.lookAt(entity.position.offset(0, 1.2, 0), true)
  await sleep(150, signal)
  const what = await tossItems(bot, item, count)
  return `${what} an ${entity.username} geworfen.`
}

// Was lohnt sich am meisten zu essen? Giftiges nur, wenn nichts anderes da ist.
const BAD_FOOD = new Set(['rotten_flesh', 'spider_eye', 'poisonous_potato', 'pufferfish', 'chorus_fruit', 'suspicious_stew'])

export function bestFood (bot) {
  const foods = bot.registry.foodsByName || {}
  const candidates = bot.inventory.items().filter(i => foods[i.name])
  if (!candidates.length) return null
  candidates.sort((a, b) => {
    const badA = BAD_FOOD.has(a.name) ? 1 : 0
    const badB = BAD_FOOD.has(b.name) ? 1 : 0
    if (badA !== badB) return badA - badB
    return (foods[b.name].foodPoints + foods[b.name].saturation) - (foods[a.name].foodPoints + foods[a.name].saturation)
  })
  return candidates[0]
}

export async function eat (bot, { item } = {}) {
  if (bot.food >= 20 && bot.game.gameMode !== 'creative') return 'Bin satt (20/20).'
  const food = item ? findItems(bot, item)[0] : bestFood(bot)
  if (!food) throw new SkillError('Nichts Essbares im Inventar.')
  const previous = bot.heldItem
  await bot.equip(food, 'hand')
  await bot.consume()
  if (previous && previous.name !== food.name) {
    const again = bot.inventory.items().find(i => i.name === previous.name)
    if (again) await bot.equip(again, 'hand').catch(() => {})
  }
  return `${food.name} gegessen. Hunger jetzt ${bot.food}/20.`
}

// Beste Ruestung anziehen - einfach nach Material.
const ARMOR_RANK = ['leather', 'golden', 'chainmail', 'iron', 'turtle', 'diamond', 'netherite']
const ARMOR_SLOTS = { helmet: ['head', 5], chestplate: ['torso', 6], leggings: ['legs', 7], boots: ['feet', 8] }

function armorRank (name) {
  return ARMOR_RANK.findIndex(m => name.startsWith(m + '_'))
}

export async function equipBestArmor (bot) {
  const changed = []
  for (const [piece, [dest, slot]] of Object.entries(ARMOR_SLOTS)) {
    const worn = bot.inventory.slots[slot]
    const best = bot.inventory.items()
      .filter(i => i.name.endsWith('_' + piece))
      .sort((a, b) => armorRank(b.name) - armorRank(a.name))[0]
    if (best && (!worn || armorRank(best.name) > armorRank(worn.name))) {
      await bot.equip(best, dest)
      changed.push(best.name)
    }
  }
  return changed.length ? `Angezogen: ${changed.join(', ')}.` : 'Habe schon die beste Ruestung an.'
}
