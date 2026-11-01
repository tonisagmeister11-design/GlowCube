// Konten, Credits, anonyme Statistik und Admin-Dashboard fuer Velora.
// Passwoerter werden nur als PBKDF2-Hash gespeichert und sind fuer niemanden lesbar.
import ADMIN_PAGE from './admin.html';
import PRIVACY_PAGE from './datenschutz.html';

const CORS = { 'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Methods': 'GET, POST, DELETE, OPTIONS', 'Access-Control-Allow-Headers': 'Content-Type, X-App-Id, Authorization' };
const json = (o, s = 200) => new Response(JSON.stringify(o), { status: s, headers: { 'Content-Type': 'application/json; charset=utf-8', ...CORS } });
export const CR_FREE = 10, CR_PLUS = 100;
const ITER = 20000;
const enc = new TextEncoder();
const hex = b => [...new Uint8Array(b)].map(x => x.toString(16).padStart(2, '0')).join('');
const rnd = n => hex(crypto.getRandomValues(new Uint8Array(n)));
const day = (t = Date.now()) => new Date(t).toISOString().slice(0, 10);
const sha = async t => hex(await crypto.subtle.digest('SHA-256', enc.encode(t)));

async function pwHash(pw, salt, it) {
  const key = await crypto.subtle.importKey('raw', enc.encode(pw), 'PBKDF2', false, ['deriveBits']);
  return hex(await crypto.subtle.deriveBits({ name: 'PBKDF2', hash: 'SHA-256', salt: enc.encode(salt), iterations: it }, key, 256));
}
function same(a, b) { if (a.length !== b.length) return false; let d = 0; for (let i = 0; i < a.length; i++) d |= a.charCodeAt(i) ^ b.charCodeAt(i); return d === 0; }
const cleanEmail = e => String(e || '').trim().toLowerCase();
const okEmail = e => /^[^\s@]{1,64}@[^\s@]{1,190}\.[a-z]{2,}$/i.test(e);
const short = (s, n) => String(s == null ? '' : s).trim().slice(0, n);

// Nutzer: Dokument u:<id> (mit Metadaten fuer schnelle Listen), Index e:<email>, Sitzung s:<sha(token)>
async function getUser(env, id) { return id ? env.USERS.get('u:' + id, 'json') : null; }
async function putUser(env, u) {
  const meta = { e: u.email, n: u.name, c: u.country, l: u.lang, t: u.created, s: u.seen, p: u.plus ? 1 : 0, a: u.consent && u.consent.anon ? 1 : 0 };
  await env.USERS.put('u:' + u.id, JSON.stringify(u), { metadata: meta });
}
function pub(u) { return { id: u.id, email: u.email, name: u.name, country: u.country, lang: u.lang, created: u.created, plus: !!u.plus, consent: u.consent || {} }; }
async function newSession(env, id, adm) {
  const tok = rnd(32);
  await env.USERS.put('s:' + await sha(tok), JSON.stringify({ id, adm: !!adm }), { expirationTtl: adm ? 12 * 3600 : 365 * 86400 });
  return tok;
}
async function session(req, env) {
  const h = req.headers.get('Authorization') || '';
  const m = h.match(/^Bearer ([a-f0-9]{64})$/); if (!m) return null;
  return env.USERS.get('s:' + await sha(m[1]), 'json');
}
export async function userFromReq(req, env) {
  const s = await session(req, env); if (!s) return null;
  return getUser(env, s.id);
}

// ---------- Credits pro Konto und Tag (Metadaten, damit das Dashboard sie ohne Einzelabfragen sieht) ----------
export async function creditsLeft(env, u) {
  const c = await env.USERS.getWithMetadata('c:' + day() + ':' + u.id);
  const used = c.metadata ? c.metadata.n : 0;
  return Math.max(0, (u.plus ? CR_PLUS : CR_FREE) - used);
}
export async function addCredits(env, u, n) {
  const k = 'c:' + day() + ':' + u.id, c = await env.USERS.getWithMetadata(k);
  const m = c.metadata || { n: 0, q: 0 };
  m.n += n; m.q += 1;
  await env.USERS.put(k, '1', { metadata: m, expirationTtl: 120 * 86400 });
  return Math.max(0, (u.plus ? CR_PLUS : CR_FREE) - m.n);
}

async function body(req) { try { return await req.json(); } catch { return null; } }

export async function accounts(req, env, url) {
  const p = url.pathname;
  if (req.method === 'OPTIONS') return new Response(null, { headers: CORS });
  if (p === '/datenschutz' && req.method === 'GET') return new Response(PRIVACY_PAGE, { headers: { 'Content-Type': 'text/html; charset=utf-8' } });
  if (p === '/admin' && req.method === 'GET') return new Response(ADMIN_PAGE, { headers: { 'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store', 'X-Frame-Options': 'DENY', 'Referrer-Policy': 'no-referrer' } });
  const ip = req.headers.get('CF-Connecting-IP') || 'x';
  if (req.method === 'POST' && (p === '/auth/register' || p === '/auth/login' || p === '/admin/api/login')) {
    const { success } = await env.LIMIT.limit({ key: 'auth:' + ip });
    if (!success) return json({ fehler: 'Zu viele Versuche. Bitte kurz warten.' }, 429);
  }

  if (p === '/auth/register' && req.method === 'POST') {
    const b = await body(req); if (!b) return json({ fehler: 'Ungültige Anfrage' }, 400);
    const email = cleanEmail(b.email), name = short(b.name, 40), pw = String(b.password || '');
    if (!okEmail(email)) return json({ fehler: 'Bitte eine gültige E-Mail-Adresse eingeben.' }, 400);
    if (name.length < 2) return json({ fehler: 'Bitte deinen Namen eingeben.' }, 400);
    if (pw.length < 8) return json({ fehler: 'Das Passwort braucht mindestens 8 Zeichen.' }, 400);
    if (!b.terms) return json({ fehler: 'Bitte der Datenschutzerklärung zustimmen.' }, 400);
    if (await env.USERS.get('e:' + email)) return json({ fehler: 'Für diese E-Mail gibt es schon ein Konto. Bitte anmelden.' }, 409);
    const id = rnd(10), salt = rnd(16), now = Date.now();
    const u = { id, email, name, country: short(b.country, 2).toUpperCase(), lang: short(b.lang, 5), created: now, seen: now, plus: false, consent: { anon: !!b.anon }, pw: { h: await pwHash(pw, salt, ITER), s: salt, it: ITER } };
    await env.USERS.put('e:' + email, id);
    await putUser(env, u);
    return json({ token: await newSession(env, id), user: pub(u) });
  }
  if (p === '/auth/login' && req.method === 'POST') {
    const b = await body(req); if (!b) return json({ fehler: 'Ungültige Anfrage' }, 400);
    const id = await env.USERS.get('e:' + cleanEmail(b.email)), u = await getUser(env, id);
    const h = u ? await pwHash(String(b.password || ''), u.pw.s, u.pw.it) : await pwHash('x', 'y', ITER);
    if (!u || !same(h, u.pw.h)) return json({ fehler: 'E-Mail oder Passwort stimmt nicht.' }, 401);
    u.seen = Date.now(); await putUser(env, u);
    return json({ token: await newSession(env, id), user: pub(u) });
  }
  if (p === '/me') {
    const u = await userFromReq(req, env); if (!u) return json({ fehler: 'Nicht angemeldet' }, 401);
    if (req.method === 'GET') {
      if (Date.now() - (u.seen || 0) > 6 * 3600e3) { u.seen = Date.now(); await putUser(env, u); }
      return json({ user: pub(u), left: await creditsLeft(env, u), max: u.plus ? CR_PLUS : CR_FREE });
    }
    if (req.method === 'POST') {
      const b = await body(req) || {};
      if (b.name != null && short(b.name, 40).length >= 2) u.name = short(b.name, 40);
      if (b.country) u.country = short(b.country, 2).toUpperCase();
      if (b.lang) u.lang = short(b.lang, 5);
      if (b.anon != null) u.consent = { ...(u.consent || {}), anon: !!b.anon };
      // Plus: bis die Bezahlung ueber Google Play eingebaut ist, setzt die Testversion es selbst
      if (b.plus != null) u.plus = !!b.plus;
      u.seen = Date.now(); await putUser(env, u);
      return json({ user: pub(u) });
    }
    if (req.method === 'DELETE') { // Konto loeschen (DSGVO)
      await env.USERS.delete('u:' + u.id); await env.USERS.delete('e:' + u.email);
      return json({ ok: true });
    }
  }
  // Anonyme Statistik: nur mit Zustimmung, ohne Konto-Bezug gespeichert
  if (p === '/stats/ride' && req.method === 'POST') {
    const u = await userFromReq(req, env); if (!u || !(u.consent && u.consent.anon)) return json({ ok: false });
    const b = await body(req) || {}, cells = Array.isArray(b.cells) ? b.cells.filter(c => /^-?\d{1,2}\.\d{2},-?\d{1,3}\.\d{2}$/.test(c)).slice(0, 300) : [];
    if (!cells.length) return json({ ok: true });
    const k = 'h:' + day().slice(0, 7), cur = await env.USERS.get(k, 'json') || {};
    cells.forEach(c => { cur[c] = (cur[c] || 0) + 1; });
    await env.USERS.put(k, JSON.stringify(cur), { expirationTtl: 400 * 86400 });
    return json({ ok: true });
  }
  if (p === '/stats/feat' && req.method === 'POST') {
    const u = await userFromReq(req, env); if (!u || !(u.consent && u.consent.anon)) return json({ ok: false });
    const b = await body(req) || {}, f = b.f && typeof b.f === 'object' ? b.f : {};
    const k = 'f:' + day(), cur = await env.USERS.get(k, 'json') || {};
    Object.keys(f).slice(0, 40).forEach(n => { if (/^[a-z_]{2,24}$/.test(n)) cur[n] = (cur[n] || 0) + Math.max(0, Math.min(500, f[n] | 0)); });
    await env.USERS.put(k, JSON.stringify(cur), { expirationTtl: 120 * 86400 });
    return json({ ok: true });
  }

  // ---------- Admin ----------
  if (p === '/admin/api/login' && req.method === 'POST') {
    const b = await body(req) || {}, email = cleanEmail(b.email);
    const admin = cleanEmail(env.ADMIN_EMAIL || '');
    const id = admin && email === admin ? await env.USERS.get('e:' + email) : null, u = await getUser(env, id);
    const h = u ? await pwHash(String(b.password || ''), u.pw.s, u.pw.it) : await pwHash('x', 'y', ITER);
    if (!u || !same(h, u.pw.h)) return json({ fehler: 'Kein Zugang.' }, 401);
    return json({ token: await newSession(env, u.id, true) });
  }
  // Nur anonyme Summen fuer das Dashboard-Artefakt (keine E-Mails, Namen oder IDs).
  // Zugang mit einem Statistik-Schluessel, dessen SHA-256 in KV unter cfg:statskey liegt.
  if (p === '/admin/api/summary' && req.method === 'GET') {
    const k = req.headers.get('X-Stats-Key') || '', want = await env.USERS.get('cfg:statskey');
    if (!want || !k || !same(await sha(k), want)) return json({ fehler: 'Kein Zugang.' }, 401);
    const list = async prefix => { let out = [], cur; do { const r = await env.USERS.list({ prefix, cursor: cur }); out = out.concat(r.keys); cur = r.list_complete ? null : r.cursor; } while (cur && out.length < 20000); return out; };
    const now = Date.now(), um = (await list('u:')).map(k2 => k2.metadata || {});
    const cnt = (arr, f) => { const o = {}; arr.forEach(x => { const v = f(x) || '?'; o[v] = (o[v] || 0) + 1; }); return o; };
    const signups = {}; um.forEach(m => { if (m.t && now - m.t < 30 * 86400e3) { const d = day(m.t); signups[d] = (signups[d] || 0) + 1; } });
    const feats = {};
    for (let i = 0; i < 30; i++) { const f = await env.USERS.get('f:' + day(now - i * 86400e3), 'json'); if (f) for (const n in f) feats[n] = (feats[n] || 0) + f[n]; }
    const ki = [];
    for (let i = 0; i < 30; i++) { const d = day(now - i * 86400e3), v = await env.USERS.get('ai:' + d, 'json'); if (v) ki.push({ d, n: v.n, q: v.q }); }
    return json({
      stand: new Date(now).toISOString(), nutzer: um.length,
      neu7: um.filter(m => m.t && now - m.t < 7 * 86400e3).length, aktiv7: um.filter(m => m.s && now - m.s < 7 * 86400e3).length,
      plus: um.filter(m => m.p).length, anonym: um.filter(m => m.a).length,
      laender: cnt(um, m => m.c), sprachen: cnt(um, m => m.l), signups, feats, ki, kiLimit: Number(env.SOUL_DAY_CAP || 9000), gratis: 10000
    });
  }
  if (p === '/admin/api/data' && req.method === 'GET') {
    const s = await session(req, env); if (!s || !s.adm) return json({ fehler: 'Kein Zugang.' }, 401);
    const list = async prefix => { let out = [], cur; do { const r = await env.USERS.list({ prefix, cursor: cur }); out = out.concat(r.keys); cur = r.list_complete ? null : r.cursor; } while (cur && out.length < 20000); return out; };
    const users = (await list('u:')).map(k => ({ id: k.name.slice(2), ...(k.metadata || {}) }));
    const since = day(Date.now() - 30 * 86400e3);
    const cr = (await list('c:')).map(k => { const [, d, id] = k.name.split(':'); return { d, id, n: (k.metadata || {}).n || 0, q: (k.metadata || {}).q || 0 }; }).filter(x => x.d >= since);
    const feats = {}, heat = {};
    for (let i = 0; i < 30; i++) { const f = await env.USERS.get('f:' + day(Date.now() - i * 86400e3), 'json'); if (f) for (const n in f) feats[n] = (feats[n] || 0) + f[n]; }
    for (let i = 0; i < 3; i++) { const d = new Date(); d.setMonth(d.getMonth() - i); const h = await env.USERS.get('h:' + d.toISOString().slice(0, 7), 'json'); if (h) for (const c in h) heat[c] = (heat[c] || 0) + h[c]; }
    // Soul-KI-Verbrauch der letzten 30 Tage (Neuronen und Antworten pro Tag) und die Tagesgrenze
    const ki = [];
    for (let i = 0; i < 30; i++) { const d = day(Date.now() - i * 86400e3), v = await env.USERS.get('ai:' + d, 'json'); if (v) ki.push({ d, n: v.n, q: v.q }); }
    return json({ users, credits: cr, feats, heat, ki, kiLimit: Number(env.SOUL_DAY_CAP || 9000), limits: { free: CR_FREE, plus: CR_PLUS } });
  }
  return null;
}
