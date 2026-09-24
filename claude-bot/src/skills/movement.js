// Laufen, Folgen, Hinschauen, rohe Steuerung.
import pf from 'mineflayer-pathfinder'
import {
  ActionAborted, SkillError, checkAbort, describeError, dist, fmtPos, sleep, stopBody, vec, withTimeout
} from './util.js'

const { goals, Movements } = pf

// Zwei Bewegungsarten: "safe" veraendert die Welt nicht (fuer normales Laufen),
// "work" darf graben und Bloecke unterlegen (fuer Abbauen und Bauen).
export function setupMovements (bot, { digWhileWalking = false } = {}) {
  const safe = new Movements(bot)
  safe.canDig = digWhileWalking
  safe.allow1by1towers = digWhileWalking
  if (!digWhileWalking) safe.scafoldingBlocks = []
  safe.allowParkour = true
  safe.allowSprinting = true

  const work = new Movements(bot)
  work.canDig = true
  work.allow1by1towers = true
  work.allowParkour = true
  work.allowSprinting = true

  bot.movementProfiles = { safe, work }
  bot.pathfinder.setMovements(safe)
  bot.pathfinder.thinkTimeout = 10000
}

export function useMovements (bot, kind) {
  const profile = bot.movementProfiles?.[kind]
  if (profile) bot.pathfinder.setMovements(profile)
}

// Laeuft zu einem Ziel. Bricht sauber ab, wenn das Signal kommt.
export async function gotoGoal (bot, goal, { signal, timeoutMs = 90000, movements = 'safe', what = 'Der Weg' } = {}) {
  checkAbort(signal)
  useMovements(bot, movements)
  const onAbort = () => stopBody(bot)
  signal?.addEventListener('abort', onAbort, { once: true })
  try {
    await withTimeout(bot, bot.pathfinder.goto(goal), timeoutMs, what)
  } catch (err) {
    if (signal?.aborted) throw new ActionAborted()
    if (err instanceof SkillError) throw err
    throw new SkillError(describeError(err))
  } finally {
    signal?.removeEventListener('abort', onAbort)
    useMovements(bot, 'safe')
  }
}

export function playerEntity (bot, name) {
  const wanted = String(name || '').toLowerCase()
  const key = Object.keys(bot.players).find(n => n.toLowerCase() === wanted)
  if (!key) throw new SkillError(`Spieler "${name}" ist nicht online.`)
  const entity = bot.players[key].entity
  if (!entity) {
    throw new SkillError(`${key} ist ausser Sichtweite, ich kann die Position nicht sehen. ` +
      `${key} kann "/spawn claude" tippen, dann tauche ich direkt daneben auf.`)
  }
  return entity
}

export async function goTo (bot, { x, y, z, range = 1, allow_digging = false }, signal) {
  const hasY = y !== undefined && y !== null
  const target = vec(x, hasY ? y : bot.entity.position.y, z)
  if ([target.x, target.y, target.z].some(Number.isNaN)) throw new SkillError('Ungueltige Koordinaten.')
  const goal = hasY
    ? new goals.GoalNear(target.x, target.y, target.z, range)
    : new goals.GoalNearXZ(target.x, target.z, range)
  await gotoGoal(bot, goal, {
    signal,
    timeoutMs: 180000,
    movements: allow_digging ? 'work' : 'safe',
    what: 'Der Weg'
  })
  const here = bot.entity.position
  const d = hasY ? dist(bot, target) : Math.hypot(here.x - target.x, here.z - target.z)
  if (d > range + 3) return `Nicht ganz angekommen - stehe bei ${fmtPos(bot.entity.position)}, noch ${Math.round(d)} Bloecke entfernt.`
  return `Angekommen bei ${fmtPos(bot.entity.position)}.`
}

export async function goToPlayer (bot, { player, distance = 2 }, signal) {
  const entity = playerEntity(bot, player)
  const p = entity.position
  await gotoGoal(bot, new goals.GoalNear(p.x, p.y, p.z, distance), { signal, timeoutMs: 120000 })
  await bot.lookAt(entity.position.offset(0, entity.height ?? 1.6, 0)).catch(() => {})
  return `Bin bei ${entity.username || player} (${fmtPos(bot.entity.position)}).`
}

// Folgen laeuft im Hintergrund weiter, bis eine andere Bewegung startet oder "stop" kommt.
export function follow (bot, { player, distance = 3, protect = false }) {
  const entity = playerEntity(bot, player)
  useMovements(bot, 'safe')
  bot.pathfinder.setGoal(new goals.GoalFollow(entity, distance), true)
  bot.followState = { player: entity.username, entity, distance, protect: !!protect, goalActive: true }
  return protect
    ? `Folge ${entity.username} und beschuetze sie/ihn vor Monstern.`
    : `Folge ${entity.username} (Abstand ${distance}).`
}

export function stopFollowing (bot) {
  if (!bot.followState) return
  bot.followState = null
  try { bot.pathfinder.setGoal(null) } catch {}
}

// Spieler kann ausser Sicht und wieder zurueck kommen - dann bekommt er ein neues Entity-Objekt.
export function refreshFollow (bot) {
  const state = bot.followState
  if (!state) return
  const entity = bot.players[state.player]?.entity
  if (!entity) return
  if (state.entity !== entity || !state.goalActive) {
    state.entity = entity
    state.goalActive = true
    useMovements(bot, 'safe')
    bot.pathfinder.setGoal(new goals.GoalFollow(entity, state.distance), true)
  }
}

const DIRECTIONS = {
  north: [0, -1], norden: [0, -1],
  south: [0, 1], sueden: [0, 1], süden: [0, 1],
  east: [1, 0], osten: [1, 0],
  west: [-1, 0], westen: [-1, 0]
}

export async function moveDirection (bot, { direction, blocks = 10 }, signal) {
  const pos = bot.entity.position
  let dx, dz
  const key = String(direction).toLowerCase()
  if (DIRECTIONS[key]) {
    [dx, dz] = DIRECTIONS[key]
  } else if (key === 'forward' || key === 'vorwaerts' || key === 'vorwärts') {
    dx = -Math.sin(bot.entity.yaw)
    dz = -Math.cos(bot.entity.yaw)
  } else {
    throw new SkillError('Richtung muss north, south, east, west oder forward sein.')
  }
  const tx = pos.x + dx * blocks
  const tz = pos.z + dz * blocks
  await gotoGoal(bot, new goals.GoalNearXZ(tx, tz, 2), { signal, timeoutMs: 180000 })
  return `Stehe jetzt bei ${fmtPos(bot.entity.position)}.`
}

export async function lookAt (bot, { x, y, z, player }) {
  if (player) {
    const entity = playerEntity(bot, player)
    await bot.lookAt(entity.position.offset(0, entity.height ?? 1.6, 0))
    return `Schaue ${entity.username} an.`
  }
  await bot.lookAt(vec(x, y, z))
  return `Schaue auf ${x} ${y} ${z}.`
}

const CONTROLS = ['forward', 'back', 'left', 'right', 'jump', 'sprint', 'sneak']

// Rohe Tastensteuerung - fuer alles, wofuer es kein eigenes Werkzeug gibt.
export async function holdControls (bot, { controls, duration_ms = 1000 }, signal) {
  const list = Array.isArray(controls) ? controls : [controls]
  for (const c of list) {
    if (!CONTROLS.includes(c)) throw new SkillError(`Unbekannte Taste "${c}". Erlaubt: ${CONTROLS.join(', ')}`)
  }
  const ms = Math.min(Math.max(Number(duration_ms) || 0, 50), 20000)
  for (const c of list) bot.setControlState(c, true)
  try {
    await sleep(ms, signal)
  } finally {
    for (const c of list) bot.setControlState(c, false)
  }
  return `${list.join('+')} fuer ${ms} ms gedrueckt. Position jetzt ${fmtPos(bot.entity.position)}.`
}

export function setSneak (bot, { on }) {
  bot.setControlState('sneak', !!on)
  return on ? 'Schleiche jetzt.' : 'Schleiche nicht mehr.'
}
