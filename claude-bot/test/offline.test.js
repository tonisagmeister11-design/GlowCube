// Schnelltest ohne Minecraft-Server und ohne API-Schluessel:
//   npm run check
// Prueft Chat-Zerlegung, Werkzeug-Schemas und die Gespraechsschleife des Gehirns
// mit einer vorgetaeuschten Claude-API und einem Attrappen-Bot.
import assert from 'node:assert/strict'
import os from 'node:os'
import path from 'node:path'
import { Vec3 } from 'vec3'

process.env.BOT_DATA_DIR = path.join(os.tmpdir(), `claude-bot-test-${process.pid}`)
process.env.ANTHROPIC_API_KEY = 'test'

const { splitForChat } = await import('../src/chat.js')
const { TOOLS, TOOL_SCHEMAS } = await import('../src/tools.js')
const { Brain } = await import('../src/brain.js')
const { Memory } = await import('../src/memory.js')

// --- Chat ---
const long = 'wort '.repeat(120)
const parts = splitForChat(`**Hallo**\n\n${long}`)
assert.equal(parts[0], 'Hallo')
assert.ok(parts.every(p => p.length <= 240), 'Chatzeilen zu lang')
assert.ok(parts.length >= 3)

// --- Werkzeuge ---
const names = new Set()
for (const t of TOOL_SCHEMAS) {
  assert.match(t.name, /^[a-z_]+$/)
  assert.ok(!names.has(t.name), `doppelt: ${t.name}`)
  names.add(t.name)
  assert.equal(t.input_schema.type, 'object')
  for (const req of t.input_schema.required) assert.ok(req in t.input_schema.properties, `${t.name}: ${req} fehlt`)
}
assert.equal(TOOLS.length, TOOL_SCHEMAS.length)

// --- Gehirn mit Attrappen ---
const said = []
const bot = {
  username: 'Claude',
  entity: { position: new Vec3(10, 64, -5), yaw: 0 },
  health: 20,
  food: 20,
  game: { dimension: 'overworld', gameMode: 'survival' },
  time: { timeOfDay: 1000 },
  heldItem: null,
  players: { Claude: {}, Toni: { entity: { position: new Vec3(12, 64, -5) } } },
  chat: m => said.push(m),
  clearControlStates () {},
  stopDigging () {},
  deactivateItem () {}
}

const requests = []
const replies = [
  { stop_reason: 'tool_use', content: [{ type: 'text', text: 'Mach ich!' }, { type: 'tool_use', id: 't1', name: 'remember', input: { note: 'Basis bei 10 64 -5' } }] },
  { stop_reason: 'tool_use', content: [{ type: 'tool_use', id: 't2', name: 'gibts_nicht', input: {} }, { type: 'tool_use', id: 't3', name: 'stop', input: {} }] },
  { stop_reason: 'end_turn', content: [{ type: 'text', text: 'Hab ich mir gemerkt.' }] }
]
const brain = new Brain({ memory: new Memory() })
brain.client = {
  messages: {
    create: async params => {
      requests.push(structuredClone(params))
      return { ...replies.shift(), usage: {} }
    }
  },
  beta: { messages: { create: async params => brain.client.messages.create(params) } }
}
brain.attach(bot)
brain.notify('<Toni> merk dir unsere Basis')
await new Promise(resolve => setTimeout(resolve, 800))
while (brain.running) await new Promise(resolve => setTimeout(resolve, 50))

assert.equal(requests.length, 3)
assert.deepEqual(said, ['Mach ich!', 'Hab ich mir gemerkt.'])
assert.equal(brain.memory.notes.at(-1), 'Basis bei 10 64 -5')
const first = requests[0]
assert.match(first.messages[0].content[0].text, /<Toni> merk dir unsere Basis/)
assert.match(first.messages[0].content[0].text, /Position 10 64 -5/)
assert.equal(first.tools.length, TOOLS.length)
// Jede tool_use-Antwort bekommt direkt danach ihre tool_results
const last = requests[2].messages
assert.deepEqual(last.map(m => m.role), ['user', 'assistant', 'user', 'assistant', 'user'])
const results = last[4].content
assert.deepEqual(results.map(r => r.tool_use_id), ['t2', 't3'])
assert.equal(results[0].is_error, true)
assert.equal(results[1].content, 'Stehe still.')

console.log('Alle Offline-Tests bestanden.')
