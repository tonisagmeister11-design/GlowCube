// Reflexe: Dinge, fuer die man nicht nachdenken muss - essen, zurueckschlagen,
// Ruestung anziehen, beim Folgen beschuetzen. Laufen nur, wenn Claude gerade
// keine eigene Aktion ausfuehrt.
import { config } from './config.js'
import { log } from './log.js'
import { fight } from './skills/combat.js'
import { bestFood, equipBestArmor } from './skills/inventory.js'
import { refreshFollow } from './skills/movement.js'
import { ActionAborted, fmtPos } from './skills/util.js'
import { isHostile } from './skills/world.js'

export class Reflexes {
  constructor (bot, brain, isActive) {
    this.bot = bot
    this.brain = brain
    this.isActive = isActive
    this.fightCtl = null
    this.eating = false
    this.lastPlayerHitNotice = 0
    this.lowHealthWarned = false
    this.timers = []

    this.onHurt = this.onHurt.bind(this)
    this.onHealth = this.onHealth.bind(this)
    this.onCollect = this.onCollect.bind(this)
    bot.on('entityHurt', this.onHurt)
    bot.on('health', this.onHealth)
    bot.on('playerCollect', this.onCollect)
    this.timers.push(setInterval(() => this.tick(), 1000))
  }

  stop () {
    for (const t of this.timers) clearInterval(t)
    this.cancel()
    this.bot.removeListener('entityHurt', this.onHurt)
    this.bot.removeListener('health', this.onHealth)
    this.bot.removeListener('playerCollect', this.onCollect)
  }

  // Claude startet eine eigene Aktion - Reflex-Kampf beenden
  cancel () {
    if (this.fightCtl) this.fightCtl.abort(new ActionAborted('Aktion'))
    this.fightCtl = null
  }

  get idle () {
    return this.isActive() && !this.brain.busy && !this.fightCtl && !this.eating && this.bot.entity
  }

  async defend (entity) {
    if (!this.idle) return
    const bot = this.bot
    this.fightCtl = new AbortController()
    const ctl = this.fightCtl
    if (bot.followState) bot.followState.goalActive = false
    log.info(`Reflex: wehre mich gegen ${entity.name}`)
    try {
      await fight(bot, entity, { signal: ctl.signal, timeoutMs: 20000 })
    } catch (err) {
      if (!(err instanceof ActionAborted)) log.debug('Reflex-Kampf:', err.message)
    } finally {
      if (this.fightCtl === ctl) this.fightCtl = null
      refreshFollow(bot)
    }
  }

  onHurt (entity) {
    const bot = this.bot
    if (entity !== bot.entity || !this.isActive()) return
    const pos = bot.entity.position
    const mob = bot.nearestEntity(e => isHostile(e) && e.position.distanceTo(pos) < 6)
    if (mob) {
      if (config.selfDefense) this.defend(mob)
      return
    }
    const player = bot.nearestEntity(e => e.type === 'player' && e !== bot.entity && e.position.distanceTo(pos) < 5)
    if (player && Date.now() - this.lastPlayerHitNotice > 20000) {
      this.lastPlayerHitNotice = Date.now()
      this.brain.notify(`[Spiel] Du hast Schaden bekommen, ${player.username} steht direkt neben dir (hat dich vielleicht geschlagen). Leben ${Math.round(bot.health)}/20.`)
    }
  }

  onHealth () {
    const bot = this.bot
    if (!this.isActive()) return
    if (bot.health <= 6 && !this.lowHealthWarned && bot.health > 0) {
      this.lowHealthWarned = true
      this.brain.notify(`[Spiel] Achtung, du hast nur noch ${Math.round(bot.health)}/20 Leben (Hunger ${bot.food}/20).`)
    } else if (bot.health >= 14) {
      this.lowHealthWarned = false
    }
  }

  onCollect (collector) {
    if (collector !== this.bot.entity) return
    setTimeout(() => {
      if (!this.idle) return
      equipBestArmor(this.bot).catch(() => {})
    }, 600)
  }

  async tick () {
    const bot = this.bot
    if (!bot.entity || !this.isActive()) return
    if (bot.followState) refreshFollow(bot)

    // Beschuetzen: Monster in der Naehe von uns beim Folgen angreifen
    if (bot.followState?.protect && this.idle) {
      const mob = bot.nearestEntity(e => isHostile(e) && e.position.distanceTo(bot.entity.position) < 10)
      if (mob && mob.name !== 'creeper') this.defend(mob)
    }

    // Essen, wenn hungrig und gerade nichts los ist
    if (config.autoEat && this.idle && bot.game?.gameMode === 'survival' &&
        (bot.food <= 14 || (bot.health < 12 && bot.food < 20))) {
      const food = bestFood(bot)
      if (!food) return
      this.eating = true
      const previous = bot.heldItem
      try {
        await bot.equip(food, 'hand')
        await bot.consume()
        log.info(`Reflex: ${food.name} gegessen (Hunger ${bot.food}/20)`)
        if (previous && previous.name !== food.name) {
          const again = bot.inventory.items().find(i => i.name === previous.name)
          if (again) await bot.equip(again, 'hand')
        }
      } catch (err) {
        log.debug('Reflex-Essen:', err.message)
      } finally {
        this.eating = false
      }
    }
  }

  onDeath () {
    this.cancel()
    const pos = this.bot.entity?.position
    this.brain.notify(`[Spiel] Du bist gestorben${pos ? ` bei ${fmtPos(pos)}` : ''}. Deine Sachen liegen dort. Du bist wieder am Spawnpunkt.`)
  }
}
