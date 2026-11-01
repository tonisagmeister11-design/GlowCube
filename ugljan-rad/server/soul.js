// Soul über den Server: die KI rechnet bei Cloudflare (Workers AI), nicht auf dem Handy.
// Kostenbremse: pro Tag (UTC) höchstens SOUL_DAY_CAP Neuronen. Cloudflare schenkt 10.000 am Tag,
// die Grenze liegt darunter. Ist sie erreicht, antwortet die App bis Mitternacht (UTC) mit ihrer eingebauten Logik.
// So entstehen nie Kosten über den Workers-Tarif hinaus.
import { DurableObject } from 'cloudflare:workers';
import { userFromReq } from './accounts.js';

const CORS = { 'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Methods': 'POST, OPTIONS', 'Access-Control-Allow-Headers': 'Content-Type, X-App-Id, Authorization' };
const json = (o, s = 200) => new Response(JSON.stringify(o), { status: s, headers: { 'Content-Type': 'application/json; charset=utf-8', ...CORS } });
const enc = new TextEncoder();
const hex = b => [...new Uint8Array(b)].map(x => x.toString(16).padStart(2, '0')).join('');
const sha = async t => hex(await crypto.subtle.digest('SHA-256', enc.encode(t)));
const day = () => new Date().toISOString().slice(0, 10);

const MODEL = '@cf/qwen/qwen3-30b-a3b-fp8';
// Neuronen pro Million Tokens laut Cloudflare-Preisliste (qwen3-30b-a3b-fp8)
const N_IN = 4625, N_OUT = 30475;
// Antworten pro Person und Tag
const PER_DAY = { anon: 10, free: 30, plus: 120 };

const SYS = [
  'Du bist Soul, die KI in der Fahrrad-App Velora.',
  'Stil: locker, warmherzig und natürlich, wie ein guter Freund. Du duzt. Antworte in der Sprache, in der die Person schreibt (meist Deutsch), in korrekter Grammatik.',
  'Länge: meist 1 bis 3 kurze Sätze. Nur bei Erklärungen, Anleitungen oder Tipps ausführlicher, dann mit kurzen Stichpunkten ("- ").',
  'Verwende ein oder zwei passende Emojis. Keine Kosenamen, keine derbe Umgangssprache (wie "geil") und nimm kein Geschlecht an.',
  'Beantworte genau das, was gefragt wurde, ohne Floskeln wie "Super Frage". Wiederhole nicht die Frage. Stell höchstens eine kurze Rückfrage.',
  'Wenn die Person von Gefühlen oder einem schlechten Tag erzählt: zuerst ehrliches Mitgefühl, dann nachfragen. Schlag nicht sofort eine Radtour vor.',
  'Die Zeile [Info: ...] ist nur Hintergrund (Zeit, Ort, Wetter, was du über die Person weißt). Erwähne daraus nur etwas, wenn es zur Nachricht passt.',
  'Fakten: Erfinde nichts. Stehen [Suchergebnisse] oder [Velora-Wissen] in der Nachricht, übernimm Zahlen, Jahreszahlen und Namen genau von dort. Weißt du etwas nicht sicher, sag das ehrlich.',
  'Velora kann: Routen planen, Rundtouren vorschlagen, Wetter, Orte in der Nähe, Navigation, Fahrten mit Kalorien, Wochenziel, Abzeichen, Offline-Karten, Live-Standort teilen. Erfinde keine anderen App-Funktionen oder Befehle.',
  'Bleib freundlich und jugendfrei. Bei Verletzungen oder Gesundheit gib nur allgemeine Tipps und rate zu einem Arzt.'
].join('\n');


// Exakter Zaehler (ein einziges Durable Object fuer alle): prueft und reserviert vor jeder Antwort,
// rechnet danach den echten Verbrauch ab. So kann die Tagesgrenze auch bei vielen gleichzeitigen Fragen nicht reissen.
const RESERVE = 25; // vorab reservierte Neuronen pro Antwort (eine Antwort braucht meist 5 bis 15)
export class SoulBudget extends DurableObject {
  async take(who, lim, cap) {
    const d = day(), st = this.ctx.storage;
    const last = await st.get('day');
    if (last !== d) { await st.deleteAll(); await st.put('day', d); }
    const tot = (await st.get('tot')) || { n: 0, q: 0, r: 0 };
    if (tot.n + tot.r + RESERVE > cap) return { ok: false, why: 'tag' };
    const uq = (await st.get('u:' + who)) || 0;
    if (uq >= lim) return { ok: false, why: 'person' };
    tot.r += RESERVE; tot.q += 1;
    await st.put('tot', tot); await st.put('u:' + who, uq + 1);
    return { ok: true, rest: Math.max(0, lim - uq - 1) };
  }
  async settle(nrn, d0) {
    const st = this.ctx.storage;
    if ((await st.get('day')) !== d0) return null; // Tag schon gewechselt: neuer Tag beginnt bei 0
    const tot = (await st.get('tot')) || { n: 0, q: 0, r: 0 };
    tot.r = Math.max(0, tot.r - RESERVE); tot.n = Math.round((tot.n + nrn) * 100) / 100;
    await st.put('tot', tot);
    return tot;
  }
  async block(cap) { const st = this.ctx.storage; const tot = (await st.get('tot')) || { n: 0, q: 0, r: 0 }; tot.n = Math.max(tot.n, cap); await st.put('tot', tot); }
}

const s = (x, n) => String(x == null ? '' : x).slice(0, n);
// grobe, eher zu hohe Schätzung der Tokens (falls Cloudflare keine Zahlen mitliefert)
const estTok = t => Math.ceil(String(t).length / 2.6);
const neurons = (pin, pout) => (pin * N_IN + pout * N_OUT) / 1e6;

export async function soul(req, env, ctx) {
  if (req.method === 'OPTIONS') return new Response(null, { headers: CORS });
  if (req.method !== 'POST') return json({ fehler: 'Nicht gefunden' }, 404);
  if (req.headers.get('X-App-Id') !== env.APP_ID) return json({ fehler: 'Nicht erlaubt' }, 403);
  const ip = req.headers.get('CF-Connecting-IP') || 'x';
  const { success } = await env.LIMIT.limit({ key: ip });
  if (!success) return json({ fehler: 'Zu viele Anfragen, bitte kurz warten.', grenze: 'minute' }, 429);
  let b; try { b = await req.json(); } catch { return json({ fehler: 'Ungültige Anfrage' }, 400); }
  const q = s(b.q, 1200).trim(); if (!q) return json({ fehler: 'Leere Frage' }, 400);

  // Kostenbremse für alle zusammen und Grenze pro Person (exakt, im Durable Object)
  const cap = Number(env.SOUL_DAY_CAP || 9000), d0 = day();
  const user = await userFromReq(req, env);
  const who = user ? user.id : 'ip' + (await sha(ip + 'velora')).slice(0, 16);
  const lim = user ? (user.plus ? PER_DAY.plus : PER_DAY.free) : PER_DAY.anon;
  const bud = env.BUDGET.get(env.BUDGET.idFromName('global'));
  const ok = await bud.take(who, lim, cap);
  if (!ok.ok) return json(ok.why === 'tag' ? { fehler: 'Soul ist für heute ausgelastet.', grenze: 'tag' } : { fehler: 'Deine Soul-Antworten für heute sind aufgebraucht.', grenze: 'person' }, 429);

  // Nachricht wie in der App: Info, Velora-Wissen, Suchergebnisse, dann die Frage
  let u = '';
  if (b.ctx) u += '[Info: ' + s(b.ctx, 600) + ']\n';
  if (Array.isArray(b.refs) && b.refs.length) u += '[Velora-Wissen: ' + b.refs.map(x => s(x, 900)).join(' | ').replace(/\*\*/g, '').slice(0, 1400) + ']\n';
  if (b.search && b.search.text) u += '[Suchergebnisse (' + s(b.search.src, 80) + '): ' + s(b.search.text, 1500) + ']\n';
  u += (u ? '\n' : '') + q + ' /no_think';
  const messages = [{ role: 'system', content: SYS }];
  (Array.isArray(b.hist) ? b.hist.slice(-4) : []).forEach(h => { messages.push({ role: 'user', content: s(h.u, 300) }); messages.push({ role: 'assistant', content: s(h.a, 350).replace(/\*\*/g, '') }); });
  messages.push({ role: 'user', content: u });
  const inTok = estTok(messages.map(m => m.content).join('\n'));
  const maxTok = b.long ? 450 : 260;

  let ai;
  try {
    ai = await env.AI.run(env.SOUL_MODEL || MODEL, { messages, max_tokens: maxTok, temperature: 0.6, top_p: 0.9, stream: true });
  } catch (e) {
    const m = String(e && e.message);
    await bud.settle(0, d0);
    if (/4006|daily free allocation|neurons/i.test(m)) { await bud.block(cap); return json({ fehler: 'Soul ist für heute ausgelastet.', grenze: 'tag' }, 429); }
    return json({ fehler: 'Soul ist gerade nicht erreichbar.' }, 503);
  }

  // Strom an die App weiterreichen: data: {"t":"..."} ... data: {"ende":true,"rest":n}
  const { readable, writable } = new TransformStream();
  const w = writable.getWriter();
  const pump = (async () => {
    const rd = ai.getReader(), dec = new TextDecoder();
    let buf = '', out = '', usage = null;
    try {
      for (;;) {
        const x = await rd.read(); if (x.done) break;
        buf += dec.decode(x.value, { stream: true });
        const lines = buf.split('\n'); buf = lines.pop();
        for (const l of lines) {
          if (!l.startsWith('data: ')) continue;
          const d = l.slice(6).trim(); if (d === '[DONE]') continue;
          try {
            const j = JSON.parse(d);
            if (j.usage) usage = j.usage;
            // Achtung: Ziffern kommen teils als Zahl (z. B. 0), deshalb immer in Text umwandeln
            const r0 = j.response != null ? j.response : (j.choices && j.choices[0] && j.choices[0].delta && j.choices[0].delta.content);
            const t = r0 == null ? '' : String(r0);
            if (t !== '') { out += t; await w.write(enc.encode('data: ' + JSON.stringify({ t }) + '\n\n')); }
          } catch (e) { }
        }
      }
    } catch (e) { }
    // Verbrauch zählen: echte Zahlen von Cloudflare, sonst (eher zu hohe) Schätzung
    const pin = usage && usage.prompt_tokens ? usage.prompt_tokens : inTok;
    const pout = usage && usage.completion_tokens ? usage.completion_tokens : estTok(out) + 4;
    const nrn = neurons(pin, pout);
    const tot = await bud.settle(nrn, d0);
    // Zusammenfassung fuers Admin-Dashboard (nur Anzeige)
    if (tot) try { await env.USERS.put('ai:' + d0, JSON.stringify({ n: tot.n, q: tot.q }), { expirationTtl: 40 * 86400 }); } catch (e) { }
    try { await w.write(enc.encode('data: ' + JSON.stringify({ ende: true, rest: ok.rest }) + '\n\n')); await w.close(); } catch (e) { }
  })();
  if (ctx && ctx.waitUntil) ctx.waitUntil(pump);
  return new Response(readable, { headers: { 'Content-Type': 'text/event-stream; charset=utf-8', 'Cache-Control': 'no-cache', ...CORS } });
}
