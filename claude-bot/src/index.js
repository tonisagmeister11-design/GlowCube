#!/usr/bin/env node
// Claude als Minecraft-Mitspielerin.
//
//   npm start                                  (Einstellungen aus .env)
//   npm start -- --host 1.2.3.4 --owner Toni   (Server-IP direkt angeben)
import readline from 'node:readline'
import mineflayer from 'mineflayer'
import pf from 'mineflayer-pathfinder'
import mc from 'minecraft-protocol'
import minecraftData from 'minecraft-data'
import { config } from './config.js'
import { log } from './log.js'
import { Brain } from './brain.js'
import { Control } from './control.js'
import { Memory } from './memory.js'
import { Reflexes } from './reflexes.js'
import { say } from './chat.js'
import { setupMovements } from './skills/movement.js'

const STOP_WORDS = /^(stop+|stopp+|halt|hör auf|hoer auf|warte|bleib stehen|stehen bleiben|abbrechen|cancel)[.!]*$/i

const memory = new Memory()
const brain = new Brain({ memory })
const control = new Control()
let bot = null
let reflexes = null
let quitting = false
let reconnectTimer = null

brain.isActive = () => control.active
brain.onLeave = () => control.leave()
brain.onPhysicalAction = () => reflexes?.cancel()

control.on('active', ({ player, summoned }) => {
  log.info(summoned ? `Von ${player} gerufen - ich bin im Spiel.` : 'Ich bin im Spiel.')
  brain.notify(summoned
    ? `[Spiel] ${player} hat dich mit /spawn claude gerufen, du stehst jetzt direkt neben ${player}. Begruesse kurz.`
    : '[Spiel] Du bist gerade auf dem Server angekommen.')
})
control.on('standby', player => {
  log.info(`Warte unsichtbar${player ? ` (weggeschickt von ${player})` : ''}. Rufen mit /spawn claude.`)
  brain.abortAction('standby')
  brain.queue.length = 0
})

// --- Wer darf mit Claude reden? ---
function allowed (username) {
  if (!config.owners.length) return true
  return config.owners.some(o => o.toLowerCase() === username.toLowerCase())
}

function addressesMe (message) {
  return message.toLowerCase().includes(config.username.toLowerCase())
}

// Chat kommt je nach Server als echte Spielernachricht oder als Systemtext an.
// Damit nichts doppelt ankommt, merken wir uns die letzten Nachrichten kurz.
const recentChat = new Map()

function handleChat (username, message, whisper = false) {
  if (!bot || username === bot.username || !control.active) return
  const key = `${username}|${message}`
  if (recentChat.has(key)) return
  recentChat.set(key, Date.now())
  setTimeout(() => recentChat.delete(key), 2000)

  if (!allowed(username)) return
  if (config.requireName && !whisper && !addressesMe(message)) return

  const text = message.replace(new RegExp(`^@?${config.username}[,:]?\\s*`, 'i'), '').trim()
  if (STOP_WORDS.test(text)) {
    log.info(`${username} sagt stop - breche ab.`)
    brain.abortAction('stop')
  }
  log.info(`${whisper ? '(gefluestert) ' : ''}<${username}> ${message}`)
  brain.notify(whisper ? `<${username} (fluestert dir)> ${message}` : `<${username}> ${message}`)
}

// Formate wie "<Toni> hi", "[Admin] Toni » hi" oder "Toni: hi"
const SYSTEM_CHAT = /^(?:\[[^\]]*\]\s*)*(?:<([A-Za-z0-9_]{2,16})>|([A-Za-z0-9_]{2,16})\s*(?:»|>>|:|\|))\s*(.+)$/

async function resolveVersion () {
  if (config.version && config.version !== 'auto') return config.version
  try {
    const res = await mc.ping({ host: config.host, port: config.port, closeTimeout: 8000 })
    const protocol = res?.version?.protocol
    const name = res?.version?.name
    const known = minecraftData.versions.pc.filter(v => v.version === protocol).map(v => v.minecraftVersion)
    const usable = known.find(v => minecraftData(v))
    if (usable) {
      log.info(`Server meldet ${name} -> benutze Protokoll ${usable}.`)
      return usable
    }
    log.warn(`Server meldet ${name} (Protokoll ${protocol}). Mineflayer kann hoechstens ${mineflayer.latestSupportedVersion}. ` +
      `Ich verbinde mich als ${mineflayer.latestSupportedVersion} - dafuer muss auf dem Server ViaVersion + ViaBackwards laufen.`)
    return mineflayer.latestSupportedVersion
  } catch (err) {
    log.warn(`Server ${config.host}:${config.port} antwortet nicht auf Ping (${err.message}). Versuche es trotzdem.`)
    return false
  }
}

async function start () {
  reconnectTimer = null
  const version = await resolveVersion()
  log.info(`Verbinde als ${config.username} mit ${config.host}:${config.port} (${config.auth})...`)

  bot = mineflayer.createBot({
    host: config.host,
    port: config.port,
    username: config.username,
    auth: config.auth,
    version,
    profilesFolder: `${config.dataDir}/auth`,
    checkTimeoutInterval: 60000,
    hideErrors: false
  })
  bot.loadPlugin(pf.pathfinder)
  control.attach(bot)
  brain.attach(bot)

  const current = bot
  bot.once('spawn', () => {
    log.info(`Gespawnt bei ${bot.entity.position.floored()} in ${bot.game.dimension}.`)
    setupMovements(bot, { digWhileWalking: config.allowDigWhileWalking })
    reflexes = new Reflexes(bot, brain, () => control.active)
    // Spieler, die beim Betreten schon da sind, nicht als "neu" melden
    current.joinQuietUntil = Date.now() + 10000
  })

  bot.on('chat', (username, message) => handleChat(username, message))
  bot.on('whisper', (username, message) => handleChat(username, message, true))
  bot.on('messagestr', (message, position) => {
    if (position !== 'system') return
    const m = SYSTEM_CHAT.exec(message)
    const who = m && (m[1] || m[2])
    if (who && bot.players[who]) return handleChat(who, m[3])
    // Antworten auf Befehle, die Claude eben geschickt hat
    if (control.active && bot.expectServerReplyUntil > Date.now()) brain.notify(`[Server] ${message}`)
  })

  bot.on('playerJoined', player => {
    if (!control.active || player.username === bot.username || Date.now() < (current.joinQuietUntil ?? Infinity)) return
    if (allowed(player.username)) brain.notify(`[Spiel] ${player.username} hat den Server betreten.`)
  })
  bot.on('death', () => reflexes?.onDeath())
  bot.on('kicked', reason => log.warn('Vom Server geworfen:', typeof reason === 'string' ? reason : JSON.stringify(reason)))
  bot.on('error', err => log.error('Verbindung:', err.message || err))
  bot.on('end', reason => {
    if (bot !== current) return
    log.warn(`Verbindung beendet (${reason}).`)
    reflexes?.stop()
    reflexes = null
    brain.abortAction('Verbindung weg')
    control.detach()
    brain.attach(null)
    if (quitting) return
    log.info(`Neuer Versuch in ${config.reconnectSeconds} s ...`)
    reconnectTimer = setTimeout(() => start().catch(err => log.error(err)), config.reconnectSeconds * 1000)
  })
}

// --- Konsole: direkt mit Claude reden oder den Bot steuern ---
function startConsole () {
  if (!config.console || !process.stdin.isTTY) return
  const rl = readline.createInterface({ input: process.stdin, output: process.stdout })
  log.info('Konsole: Text an Claude schreiben | !text = direkt in den Chat | /quit = beenden')
  rl.on('line', line => {
    const text = line.trim()
    if (!text) return
    if (text === '/quit' || text === '/exit') return shutdown()
    if (text.startsWith('!')) {
      if (bot?.entity) say(bot, text.slice(1))
      return
    }
    if (!control.active) return log.info('Claude wartet gerade unsichtbar - erst mit /spawn claude rufen.')
    if (STOP_WORDS.test(text)) brain.abortAction('stop')
    brain.notify(`<Konsole (Besitzer am Bot-Rechner)> ${text}`)
  })
}

function shutdown () {
  quitting = true
  clearTimeout(reconnectTimer)
  log.info('Beende ...')
  try { bot?.quit('Tschuess') } catch {}
  setTimeout(() => process.exit(0), 500)
}

process.on('SIGINT', shutdown)
process.on('SIGTERM', shutdown)
process.on('unhandledRejection', err => log.error('Unbehandelt:', err))

if (!process.env.ANTHROPIC_API_KEY && !process.env.ANTHROPIC_AUTH_TOKEN) {
  log.warn('ANTHROPIC_API_KEY ist nicht gesetzt - trag ihn in claude-bot/.env ein (siehe .env.example).')
}
log.info(`Modell ${config.model} (Aufwand ${config.effort}) | Besitzer: ${config.owners.join(', ') || 'alle'} | ` +
  `Standby: ${config.standby ? 'an' : 'aus'}`)
startConsole()
start().catch(err => {
  log.error(err)
  process.exit(1)
})
