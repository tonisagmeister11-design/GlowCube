import { config } from './config.js'

function stamp () {
  return new Date().toTimeString().slice(0, 8)
}

export const log = {
  info: (...a) => console.log(`[${stamp()}]`, ...a),
  warn: (...a) => console.warn(`[${stamp()}] WARNUNG:`, ...a),
  error: (...a) => console.error(`[${stamp()}] FEHLER:`, ...a),
  debug: (...a) => { if (config.debug) console.log(`[${stamp()}] debug:`, ...a) }
}
