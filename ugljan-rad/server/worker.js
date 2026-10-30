// Ugljan Rad KI-Server auf Cloudflare Workers.
// Die App schickt eine Frage an POST /ki, der Worker fragt die eingebaute Cloudflare-KI (Workers AI)
// und gibt die Antwort zurück. Kein fremder API-Key nötig, kein Schlüssel in der App.
// Schutz: Limit pro Gerät/IP und maximale Textlänge, damit niemand das Gratis-Kontingent leerfragt.

const MODEL = '@cf/meta/llama-3.1-8b-instruct';
const SYSTEM = 'Du bist der Fahrrad-Assistent der App "Ugljan Rad". Antworte kurz, freundlich und auf Deutsch. ' +
  'Du hilfst bei Radtouren, Sehenswürdigkeiten, Pausen und Tipps unterwegs. Erfinde keine Fakten über Orte.';

const CORS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
  'Access-Control-Allow-Headers': 'Content-Type, X-App-Id'
};

function json(data, status = 200) {
  return new Response(JSON.stringify(data), { status, headers: { 'Content-Type': 'application/json; charset=utf-8', ...CORS } });
}

export default {
  async fetch(req, env) {
    if (req.method === 'OPTIONS') return new Response(null, { headers: CORS });
    const url = new URL(req.url);
    if (url.pathname !== '/ki' || req.method !== 'POST') return json({ fehler: 'Nicht gefunden' }, 404);

    // Einfache Kennung der App (kein echtes Geheimnis, hält aber Zufallsbesucher fern)
    if (req.headers.get('X-App-Id') !== env.APP_ID) return json({ fehler: 'Nicht erlaubt' }, 403);

    // Limit: pro IP höchstens LIMIT Anfragen pro Minute (Cloudflare Rate Limiting, gratis)
    const ip = req.headers.get('CF-Connecting-IP') || 'unbekannt';
    const { success } = await env.LIMIT.limit({ key: ip });
    if (!success) return json({ fehler: 'Zu viele Anfragen, bitte kurz warten.' }, 429);

    let body;
    try { body = await req.json(); } catch { return json({ fehler: 'Ungültige Anfrage' }, 400); }
    const frage = String(body.frage || '').slice(0, 800);
    const kontext = String(body.kontext || '').slice(0, 1500); // z. B. Standort, Route, Wetter aus der App
    if (!frage.trim()) return json({ fehler: 'Leere Frage' }, 400);

    try {
      const out = await env.AI.run(MODEL, {
        messages: [
          { role: 'system', content: SYSTEM + (kontext ? '\nInfos aus der App: ' + kontext : '') },
          { role: 'user', content: frage }
        ],
        max_tokens: 400
      });
      return json({ antwort: out.response });
    } catch (e) {
      // z. B. wenn das Tageskontingent aufgebraucht ist
      return json({ fehler: 'KI gerade nicht verfügbar, bitte später nochmal.' }, 503);
    }
  }
};
