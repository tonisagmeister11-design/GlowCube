// Absprache mit dem Server-Plugin ueber den Plugin-Kanal "claude:control".
//
// Bot    -> Plugin: hello|1|standby   oder   hello|1|active     (nach dem Betreten)
//                   leave                                         (Claude will verschwinden)
// Plugin -> Bot:    state|standby   /   state|active|<Spieler>
//                   summon|<Spieler>                              (/spawn claude)
//                   dismiss|<Spieler>                             (/despawn claude)
//
// Ohne Plugin kommt nie eine Antwort - dann ist Claude einfach normal im Spiel.
import { EventEmitter } from 'node:events'
import { CONTROL_CHANNEL, config } from './config.js'
import { log } from './log.js'
import { SkillError } from './skills/util.js'

const PROTOCOL = 1
const PLUGIN_WAIT_MS = 4000

export class Control extends EventEmitter {
  constructor () {
    super()
    this.state = 'connecting' // connecting | standby | active
    this.pluginPresent = false
    this.bot = null
  }

  get active () {
    return this.state === 'active'
  }

  attach (bot) {
    this.bot = bot
    this.state = 'connecting'
    this.pluginPresent = false

    // Empfangen koennen wir sofort; beim Server anmelden (minecraft:register) erst in der
    // Spielphase - Paper schickt nur auf angemeldeten Kanaelen.
    bot._client.registerChannel(CONTROL_CHANNEL, 'restBuffer', false)
    bot._client.on(CONTROL_CHANNEL, data => this.onMessage(Buffer.from(data).toString('utf8')))
    bot.once('login', () => {
      try {
        bot._client.writeChannel('minecraft:register', [CONTROL_CHANNEL])
      } catch (err) {
        log.warn('Plugin-Kanal konnte nicht angemeldet werden:', err.message)
      }
    })

    bot.once('spawn', () => {
      this.send(`hello|${PROTOCOL}|${config.standby ? 'standby' : 'active'}`)
      setTimeout(() => {
        if (this.bot !== bot || this.pluginPresent) return
        log.info('Kein Claude-Plugin auf dem Server gefunden - ich bin direkt im Spiel.')
        this.setState('active', null)
      }, PLUGIN_WAIT_MS)
    })
  }

  detach () {
    this.state = 'connecting'
    this.pluginPresent = false
  }

  send (text) {
    try {
      this.bot._client.writeChannel(CONTROL_CHANNEL, Buffer.from(text, 'utf8'))
    } catch (err) {
      log.debug('Plugin-Nachricht ging nicht raus:', err.message)
    }
  }

  onMessage (text) {
    log.debug('Plugin sagt:', text)
    const [kind, arg1, arg2] = text.split('|')
    this.pluginPresent = true
    if (kind === 'state') {
      this.setState(arg1 === 'active' ? 'active' : 'standby', arg2 && arg2 !== '-' ? arg2 : null)
    } else if (kind === 'summon') {
      this.setState('active', arg1 || null, true)
    } else if (kind === 'dismiss') {
      this.setState('standby', arg1 || null)
    }
  }

  setState (state, player, summoned = false) {
    const changed = state !== this.state
    this.state = state
    if (state === 'standby') {
      // Unsichtbar warten: keine Physik, damit der Zuschauer-Koerper nicht "faellt"
      this.bot.physicsEnabled = false
      if (changed) this.emit('standby', player)
    } else {
      this.bot.physicsEnabled = true
      if (changed || summoned) this.emit('active', { player, summoned })
    }
  }

  // Claude verabschiedet sich selbst
  leave () {
    if (!this.pluginPresent) throw new SkillError('Ohne das Server-Plugin kann ich nicht verschwinden.')
    this.send('leave')
    return 'Verschwinde, bis mich jemand mit /spawn claude ruft.'
  }
}
