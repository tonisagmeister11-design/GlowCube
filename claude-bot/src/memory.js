// Dauerhafte Notizen ("Merk dir: unsere Basis ist bei ...") - ueberleben Neustarts.
import fs from 'node:fs'
import path from 'node:path'
import { config } from './config.js'
import { log } from './log.js'

const MAX_NOTES = 50

export class Memory {
  constructor (file = path.join(config.dataDir, 'memory.json')) {
    this.file = file
    this.notes = []
    try {
      const data = JSON.parse(fs.readFileSync(file, 'utf8'))
      if (Array.isArray(data.notes)) this.notes = data.notes.filter(n => typeof n === 'string')
    } catch (err) {
      if (err.code !== 'ENOENT') log.warn('Notizen konnten nicht gelesen werden:', err.message)
    }
  }

  save () {
    fs.mkdirSync(path.dirname(this.file), { recursive: true })
    fs.writeFileSync(this.file, JSON.stringify({ notes: this.notes }, null, 2))
  }

  add (note) {
    const text = String(note || '').trim().slice(0, 300)
    if (!text) return 'Leere Notiz, nichts gespeichert.'
    this.notes.push(text)
    if (this.notes.length > MAX_NOTES) this.notes.shift()
    this.save()
    return `Gemerkt (#${this.notes.length}): ${text}`
  }

  remove (index) {
    const i = Number(index) - 1
    if (!(i >= 0 && i < this.notes.length)) return `Keine Notiz #${index}.`
    const [gone] = this.notes.splice(i, 1)
    this.save()
    return `Vergessen: ${gone}`
  }

  render () {
    return this.notes.map((n, i) => `#${i + 1} ${n}`).join('\n')
  }
}
