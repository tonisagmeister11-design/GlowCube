// Einstellungen: erst .env, dann Umgebungsvariablen, dann Kommandozeile.
//
//   node src/index.js --host 1.2.3.4 --port 25565 --owner Toni
//
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const here = path.dirname(fileURLToPath(import.meta.url))
export const ROOT = path.resolve(here, '..')

const envFile = path.join(ROOT, '.env')
if (fs.existsSync(envFile)) process.loadEnvFile(envFile)

// --schluessel wert  oder  --schluessel=wert  ->  { schluessel: wert }
function parseArgs (argv) {
  const out = {}
  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i]
    if (!arg.startsWith('--')) continue
    const eq = arg.indexOf('=')
    if (eq !== -1) {
      out[arg.slice(2, eq)] = arg.slice(eq + 1)
    } else if (i + 1 < argv.length && !argv[i + 1].startsWith('--')) {
      out[arg.slice(2)] = argv[++i]
    } else {
      out[arg.slice(2)] = 'true'
    }
  }
  return out
}

const args = parseArgs(process.argv.slice(2))
const env = process.env

function pick (argName, envName, fallback) {
  if (args[argName] !== undefined) return args[argName]
  if (env[envName] !== undefined && env[envName] !== '') return env[envName]
  return fallback
}

function bool (value) {
  return ['1', 'true', 'yes', 'ja', 'on'].includes(String(value).toLowerCase())
}

function list (value) {
  return String(value || '').split(',').map(s => s.trim()).filter(Boolean)
}

// "1.2.3.4:25566" als --host ist bequem, wenn man die Adresse einfach kopiert.
let host = pick('host', 'MC_HOST', 'localhost')
let port = Number(pick('port', 'MC_PORT', 25565))
const hostPort = /^(.+):(\d+)$/.exec(host)
if (hostPort && !host.includes(']')) {
  host = hostPort[1]
  port = Number(hostPort[2])
}

export const config = {
  host,
  port,
  username: pick('name', 'MC_USERNAME', 'Claude'),
  // "offline" fuer Server mit online-mode=false, "microsoft" fuer ein echtes Konto
  auth: pick('auth', 'MC_AUTH', 'offline'),
  // "auto" fragt den Server; mineflayer kann nicht jede neue Version sofort
  version: pick('version', 'MC_VERSION', 'auto'),

  model: pick('model', 'BOT_MODEL', 'claude-opus-5'),
  effort: pick('effort', 'BOT_EFFORT', 'medium'),
  fallbacks: bool(pick('fallbacks', 'BOT_FALLBACKS', 'true')),

  // Leer = alle duerfen Claude Auftraege geben. Sonst nur diese Spieler.
  owners: list(pick('owner', 'BOT_OWNERS', '')),
  // true = Claude reagiert nur, wenn ihr Name in der Nachricht vorkommt (oder gefluestert wird)
  requireName: bool(pick('require-name', 'BOT_REQUIRE_NAME', 'false')),
  // Wartet unsichtbar, bis jemand /spawn claude tippt (braucht das Plugin)
  standby: bool(pick('standby', 'BOT_STANDBY', 'true')),

  selfDefense: bool(pick('self-defense', 'BOT_SELF_DEFENSE', 'true')),
  autoEat: bool(pick('auto-eat', 'BOT_AUTO_EAT', 'true')),
  allowDigWhileWalking: bool(pick('dig-while-walking', 'BOT_DIG_WHILE_WALKING', 'false')),

  reconnectSeconds: Number(pick('reconnect', 'BOT_RECONNECT_SECONDS', 15)),
  dataDir: path.resolve(ROOT, pick('data', 'BOT_DATA_DIR', 'data')),
  console: bool(pick('console', 'BOT_CONSOLE', 'true')),
  debug: bool(pick('debug', 'BOT_DEBUG', 'false'))
}

export const CONTROL_CHANNEL = 'claude:control'
