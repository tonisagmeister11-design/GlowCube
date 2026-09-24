// Minecraft nimmt hoechstens 256 Zeichen pro Nachricht und keine Zeilenumbrueche.

const MAX = 240

// Markdown und Steuerzeichen, die im Spiel nur stoeren wuerden.
export function cleanForChat (text) {
  return String(text)
    .replace(/§/g, '') // Farbcodes
    .replace(/\*\*(.+?)\*\*/g, '$1')
    .replace(/`([^`]*)`/g, '$1')
    .replace(/^#+\s*/gm, '')
    .replace(/[\u0000-\u0009\u000b-\u001f\u007f]/g, '')
}

// Zerlegt einen Text in chattaugliche Stuecke: erst nach Zeilen, dann nach Woertern.
export function splitForChat (text) {
  const out = []
  for (const rawLine of cleanForChat(text).split('\n')) {
    let line = rawLine.replace(/^\s*[-*]\s+/, '- ').trim()
    if (!line) continue
    while (line.length > MAX) {
      let cut = line.lastIndexOf(' ', MAX)
      if (cut < MAX / 2) cut = MAX
      out.push(line.slice(0, cut).trim())
      line = line.slice(cut).trim()
    }
    if (line) out.push(line)
  }
  return out
}

const sleep = ms => new Promise(resolve => setTimeout(resolve, ms))

// Schickt mehrere Zeilen mit kleiner Pause - sonst wirft der Spam-Schutz den Bot raus.
export async function say (bot, text) {
  const lines = splitForChat(text)
  // Nie ungewollt einen Befehl ausfuehren, nur weil eine Antwort mit "/" anfaengt.
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i].startsWith('/') ? ' ' + lines[i] : lines[i]
    bot.chat(line)
    if (i < lines.length - 1) await sleep(700)
  }
  return lines.length
}
