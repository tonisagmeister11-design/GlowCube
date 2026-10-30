// Velora KI-Server ("Juno") auf Cloudflare Workers.
// Die App schickt den Chatverlauf und ihre Live-Daten (Standort, Tempo, Höhe, Route, Navigation ...) an POST /chat.
// Juno antwortet mit Hilfe von Werkzeugen, die echte Daten holen (OpenStreetMap, Wikivoyage/Wikipedia, Open-Meteo),
// und kann der App Aktionen geben (Route planen, Tour vorschlagen, Route anpassen ...).
// Kein fremder API-Key: Die KI läuft über Cloudflare Workers AI. Kein Schlüssel steckt in der App.

const DEFAULT_MODEL = '@cf/openai/gpt-oss-120b';
const UA = 'Velora-Fahrrad-App/1.0 (https://github.com/tonisagmeister11-design/glowcube)';

const CORS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
  'Access-Control-Allow-Headers': 'Content-Type, X-App-Id'
};
const json = (data, status = 200) =>
  new Response(JSON.stringify(data), { status, headers: { 'Content-Type': 'application/json; charset=utf-8', ...CORS } });

const SYSTEM = `Du bist Juno, die KI der Fahrrad-App "Velora". Du begleitest Radfahrer weltweit: bei Routen, Touren, Pausen, Sehenswürdigkeiten, Wetter, Technik und allen anderen Fragen.

Regeln:
- Antworte in der Sprache des Nutzers (meist Deutsch), freundlich, locker und kurz: meist 2 bis 6 Sätze. Unterwegs liest oder hört man dich auf dem Rad.
- Kein Markdown außer **fett** und Aufzählungen mit "- ". Keine Tabellen, keine Überschriften, keine Emojis.
- Erfinde NIEMALS Orte, Restaurants, Straßen, Öffnungszeiten oder Fakten. Nenne Orte nur, wenn sie aus einem Werkzeug-Ergebnis oder dem App-Kontext stammen. Schmücke nichts aus, was nicht in den Daten steht. Wenn du etwas nicht weißt, sag es oder nutze ein Werkzeug.
- Im Kontext steht unter "orte_in_der_naehe" eine Liste echter Orte aus den Kartendaten der App. Nutze sie zuerst. Reicht sie nicht, nutze das Werkzeug orte_in_der_naehe (Umkreissuche). orte_suchen ist nur für bestimmte Namen/Ziele, nie für Kategorien wie "Eis" oder "Restaurant".
- Übernimm Ort und Land genau so, wie sie in den Daten stehen. Behaupte nie, ein Ort liege an einem anderen Ort.
- Gibt es in der Nähe nichts Passendes, sag das ehrlich und nenne höchstens den nächsten echten Treffer mit Entfernung.
- Fragen wie "Gibt es in X einen Strand / ein Restaurant ...?" beantwortest du mit orte_in_der_naehe an den Koordinaten von X (vorher orte_suchen), nicht aus dem Gedächtnis. Für Hintergrund zu Orten, Regionen, Sehenswürdigkeiten oder ob eine Gegend/Route zum Radfahren taugt: recherche. Für Wetter: wetter.
- Die Live-Daten der App (Standort, Tempo, Schnitt, Höhe, gefahrene Strecke, Höhenmeter, aktuelle Route, Navigation, Rad, Fitness, Wetter) stehen im Kontext. Nutze sie für Fragen wie "wie schnell bin ich", "wie weit noch", "wie steil wird es".
- Nenne keine Koordinaten im Text (die App zeigt Orte selbst auf der Karte).
- Rufe App-Aktionen NUR auf, wenn der Nutzer in seiner LETZTEN Nachricht ausdrücklich etwas Neues will (planen, vorschlagen, ändern, starten, zeigen). Fragen wie "wie weit", "wie viele Höhenmeter", "wie lange" beantwortest du nur mit den Live-Daten, ohne Aktion. Wiederhole nie eine frühere Aktion von selbst.
- Routen und Touren erstellt die App, nicht du. Wenn der Nutzer eine Route, Tour oder Änderung will, rufe die passende Aktion auf:
  route_planen (Ziel mit Koordinaten - hole sie vorher mit orte_suchen, falls sie nicht im Kontext stehen),
  tour_vorschlagen (Rundtour nach Dauer, Schwierigkeit, Schönheit),
  route_anpassen (kuerzer, laenger, flacher, schoener, schneller),
  navigation_starten, ort_zeigen.
  route_planen und tour_vorschlagen zeigen die Route nur an; die Navigation startet erst mit navigation_starten (nur wenn der Nutzer losfahren will).
  Sag dann kurz, was du veranlasst hast. Die genauen Zahlen der neuen Route zeigt die App selbst an, erfinde keine.
- Sicherheit geht vor: Bei Gefahr (Unfall, Verletzung) rate, den Notruf 112 zu wählen.`;

const TOOLS = [
  fn('orte_suchen', 'Sucht einen Ort, eine Adresse, eine Sehenswürdigkeit oder ein Ziel weltweit in OpenStreetMap. Liefert echte Namen mit Koordinaten.', {
    suchbegriff: { type: 'string', description: 'z. B. "Nin", "Gardasee", "Kloster Galovac"' },
    lat: { type: 'number', description: 'optional: Breitengrad, um nahe Ergebnisse zu bevorzugen' },
    lon: { type: 'number', description: 'optional: Längengrad' }
  }, ['suchbegriff']),
  fn('orte_in_der_naehe', 'Findet echte Orte in der Nähe aus OpenStreetMap: restaurant, cafe, eis, bar, imbiss, baeckerei, supermarkt, trinkwasser, toilette, fahrradladen, fahrradreparatur, strand, aussicht, sehenswuerdigkeit, unterkunft, camping, apotheke, bankomat, badestelle, spielplatz.', {
    kategorie: { type: 'string' },
    lat: { type: 'number' }, lon: { type: 'number' },
    radius_km: { type: 'number', description: 'Suchradius, Standard 3' }
  }, ['kategorie', 'lat', 'lon']),
  fn('recherche', 'Recherchiert im Internet (Wikivoyage und Wikipedia) zu Orten, Regionen, Sehenswürdigkeiten, Radwegen oder ob eine Gegend zum Radfahren gut ist.', {
    thema: { type: 'string' }
  }, ['thema']),
  fn('wetter', 'Aktuelles Wetter und die nächsten Stunden (Temperatur, Wind, Böen, Regen) an einem Ort.', {
    lat: { type: 'number' }, lon: { type: 'number' }
  }, ['lat', 'lon']),
  // Aktionen, die die App ausführt
  fn('route_planen', 'App-Aktion: plant eine Fahrradroute vom Standort zu einem Ziel.', {
    ziel_name: { type: 'string' }, lat: { type: 'number' }, lon: { type: 'number' },
    stil: { type: 'string', enum: ['schnell', 'schoen'], description: 'schnell = schnell & leicht, schoen = am schönsten' }
  }, ['ziel_name', 'lat', 'lon']),
  fn('tour_vorschlagen', 'App-Aktion: schlägt Rundtouren ab dem Standort vor.', {
    dauer_min: { type: 'number' },
    schwierigkeit: { type: 'string', enum: ['leicht', 'mittel', 'schwer'] },
    schoenheit: { type: 'string', enum: ['egal', 'schoen', 'sehr_schoen'] }
  }, ['dauer_min']),
  fn('route_anpassen', 'App-Aktion: passt die aktuelle Route oder Tour an.', {
    wunsch: { type: 'string', enum: ['kuerzer', 'laenger', 'flacher', 'schoener', 'schneller'] }
  }, ['wunsch']),
  fn('navigation_starten', 'App-Aktion: startet die Navigation auf der aktuell geplanten Route.', {}, []),
  fn('ort_zeigen', 'App-Aktion: zeigt einen Ort auf der Karte.', {
    name: { type: 'string' }, lat: { type: 'number' }, lon: { type: 'number' }
  }, ['name', 'lat', 'lon'])
];
const APP_ACTIONS = new Set(['route_planen', 'tour_vorschlagen', 'route_anpassen', 'navigation_starten', 'ort_zeigen']);

function fn(name, description, properties, required) {
  return { type: 'function', function: { name, description, parameters: { type: 'object', properties, required } } };
}

function distKm(a, b, c, d) {
  const R = 6371, r = Math.PI / 180, x = Math.sin((c - a) * r / 2) ** 2 + Math.cos(a * r) * Math.cos(c * r) * Math.sin((d - b) * r / 2) ** 2;
  return Math.round(2 * R * Math.asin(Math.sqrt(x)) * 10) / 10;
}
async function getJson(url, opts = {}, ms = 9000) {
  const ctl = new AbortController(); const t = setTimeout(() => ctl.abort(), ms);
  try {
    const r = await fetch(url, { ...opts, signal: ctl.signal, headers: { 'User-Agent': UA, ...(opts.headers || {}) } });
    if (!r.ok) throw new Error('HTTP ' + r.status);
    return await r.json();
  } finally { clearTimeout(t); }
}

// ---------- Werkzeuge mit echten Daten ----------
async function orteSuchen({ suchbegriff, lat, lon }) {
  let list = [];
  try {
    const bias = lat != null && lon != null ? `&lat=${lat}&lon=${lon}` : '';
    const j = await getJson(`https://photon.komoot.io/api/?q=${encodeURIComponent(suchbegriff)}&limit=6&lang=de${bias}`);
    list = (j.features || []).map(f => ({
      name: f.properties.name || f.properties.street, typ: f.properties.osm_value,
      ort: f.properties.city || f.properties.county || '', land: f.properties.country || '',
      lat: f.geometry.coordinates[1], lon: f.geometry.coordinates[0]
    }));
  } catch (e) {
    const j = await getJson(`https://nominatim.openstreetmap.org/search?format=jsonv2&addressdetails=1&limit=6&accept-language=de&q=${encodeURIComponent(suchbegriff)}`);
    list = j.map(x => ({ name: x.name || x.display_name.split(',')[0], typ: x.type, ort: (x.address || {}).city || (x.address || {}).town || (x.address || {}).village || '', land: (x.address || {}).country || '', lat: +x.lat, lon: +x.lon }));
  }
  return list.filter(x => x.name).map(x => (lat != null ? { ...x, entfernung_km: distKm(lat, lon, x.lat, x.lon) } : x));
}

// Kategorien -> OpenStreetMap-Typen (Nominatim-Suche nach POI-Typ im Umkreis)
const KAT = {
  restaurant: ['restaurant'], cafe: ['cafe'], bar: ['bar', 'pub'], imbiss: ['fast_food'], eis: ['ice_cream'],
  baeckerei: ['bakery'], supermarkt: ['supermarket', 'convenience'], trinkwasser: ['drinking_water'], toilette: ['toilets'],
  fahrradladen: ['bicycle'], fahrradreparatur: ['bicycle_repair_station', 'bicycle'], strand: ['beach'], aussicht: ['viewpoint'],
  sehenswuerdigkeit: ['attraction', 'museum', 'castle', 'monument', 'ruins'], unterkunft: ['hotel', 'guest_house', 'hostel', 'apartment'],
  camping: ['camp_site'], apotheke: ['pharmacy'], bankomat: ['atm'], badestelle: ['beach', 'bathing_place'], spielplatz: ['playground']
};
async function orteInDerNaehe({ kategorie, lat, lon, radius_km }) {
  const k = String(kategorie || '').toLowerCase().replace('ä', 'ae').replace('ö', 'oe').replace('ü', 'ue').replace('ß', 'ss').replace(/\s+/g, '');
  const types = KAT[k] || [k.replace(/[^a-z_]/g, '')];
  const r = Math.min(15, Math.max(0.5, radius_km || 3));
  const dLat = r / 111, dLon = r / (111 * Math.cos(lat * Math.PI / 180));
  const vb = [lon - dLon, lat + dLat, lon + dLon, lat - dLat].map(v => v.toFixed(5)).join(',');
  const all = [];
  for (const t of types.slice(0, 3)) {
    try {
      const j = await getJson(`https://nominatim.openstreetmap.org/search?amenity=${encodeURIComponent(t)}&viewbox=${vb}&bounded=1&format=jsonv2&addressdetails=1&extratags=1&limit=15&accept-language=de`, {}, 7000);
      j.forEach(x => {
        const ex = x.extratags || {}, ad = x.address || {};
        all.push({ name: x.name || null, kategorie: k, lat: +x.lat, lon: +x.lon, entfernung_km: distKm(lat, lon, +x.lat, +x.lon),
          ort: ad.village || ad.town || ad.city || ad.hamlet || ad.suburb || '', oeffnungszeiten: ex.opening_hours, kueche: ex.cuisine,
          telefon: ex.phone || ex['contact:phone'], website: ex.website || ex['contact:website'] });
      });
    } catch (e) { }
  }
  if (!all.length) return { gefunden: [], hinweis: 'In OpenStreetMap ist in diesem Umkreis nichts davon eingetragen (oder der Dienst war nicht erreichbar).' };
  all.sort((a, b) => a.entfernung_km - b.entfernung_km);
  const seen = new Set(), named = [];
  all.forEach(x => { const key = x.name + Math.round(x.lat * 1e4); if (x.name && !seen.has(key)) { seen.add(key); named.push(x); } });
  const unnamed = all.filter(x => !x.name);
  return { gefunden: named.slice(0, 10), ohne_namen: unnamed.length ? { anzahl: unnamed.length, naechster: unnamed[0] } : undefined };
}

async function recherche({ thema }) {
  const res = [];
  for (const wiki of ['de.wikivoyage.org', 'de.wikipedia.org']) {
    try {
      const s = await getJson(`https://${wiki}/w/api.php?action=query&list=search&srsearch=${encodeURIComponent(thema)}&srlimit=1&format=json&origin=*`);
      const title = s.query?.search?.[0]?.title; if (!title) continue;
      const e = await getJson(`https://${wiki}/w/api.php?action=query&prop=extracts&explaintext=1&exsectionformat=plain&exchars=1800&titles=${encodeURIComponent(title)}&format=json&origin=*`);
      const page = Object.values(e.query?.pages || {})[0];
      if (page?.extract) res.push({ quelle: wiki, titel: title, text: page.extract.slice(0, 1800) });
    } catch (e) { }
  }
  return res.length ? res : { hinweis: 'Nichts gefunden' };
}

async function wetter({ lat, lon }) {
  const j = await getJson(`https://api.open-meteo.com/v1/forecast?latitude=${lat}&longitude=${lon}&current=temperature_2m,wind_speed_10m,wind_direction_10m,wind_gusts_10m,precipitation,weather_code&hourly=temperature_2m,precipitation_probability,wind_speed_10m&forecast_days=1&timezone=auto`);
  const h = new Date().getHours();
  return { jetzt: j.current, naechste_stunden: (j.hourly?.time || []).slice(h, h + 6).map((t, i) => ({ zeit: t.slice(11), temp: j.hourly.temperature_2m[h + i], regen_prozent: j.hourly.precipitation_probability[h + i], wind_kmh: j.hourly.wind_speed_10m[h + i] })) };
}

const SERVER_TOOLS = {  orte_suchen: orteSuchen, orte_in_der_naehe: orteInDerNaehe, recherche, wetter };

// ---------- Chat mit Werkzeug-Schleife ----------
function msgOf(out) {
  if (out && out.choices) return out.choices[0].message || {};
  return { content: out && out.response, tool_calls: out && out.tool_calls };
}

export default {
  async fetch(req, env) {
    if (req.method === 'OPTIONS') return new Response(null, { headers: CORS });
    const url = new URL(req.url);
    if (req.method !== 'POST' || (url.pathname !== '/chat' && url.pathname !== '/ki' && url.pathname !== '/werkzeug')) return json({ fehler: 'Nicht gefunden' }, 404);
    if (req.headers.get('X-App-Id') !== env.APP_ID) return json({ fehler: 'Nicht erlaubt' }, 403);
    // Werkzeug direkt testen (nur mit App-Kennung)
    if (url.pathname === '/werkzeug') {
      const b = await req.json(); const t0 = Date.now();
      try { return json({ ergebnis: await SERVER_TOOLS[b.name](b.args || {}), ms: Date.now() - t0 }); } catch (e) { return json({ fehler: e.message, ms: Date.now() - t0 }); }
    }
    const ip = req.headers.get('CF-Connecting-IP') || 'unbekannt';
    const { success } = await env.LIMIT.limit({ key: ip });
    if (!success) return json({ fehler: 'Zu viele Anfragen, bitte kurz warten.' }, 429);

    let body;
    try { body = await req.json(); } catch { return json({ fehler: 'Ungültige Anfrage' }, 400); }
    // alte Form {frage} weiter unterstützen
    let verlauf = Array.isArray(body.verlauf) ? body.verlauf : [{ role: 'user', content: String(body.frage || '') }];
    verlauf = verlauf.slice(-12).map(m => ({ role: m.role === 'assistant' ? 'assistant' : 'user', content: String(m.content || '').slice(0, 1500) }));
    if (!verlauf.length || !verlauf[verlauf.length - 1].content.trim()) return json({ fehler: 'Leere Frage' }, 400);
    const kontext = JSON.stringify(body.kontext || {}).slice(0, 6000);

    const messages = [
      { role: 'system', content: SYSTEM + '\n\nLive-Daten der App (JSON):\n' + kontext },
      ...verlauf
    ];
    const aktionen = [], orte = [];
    // Modell: aus der Konfiguration; zum Testen per Anfrage waehlbar (nur aus der erlaubten Liste)
    const ERLAUBT = ['@cf/openai/gpt-oss-120b', '@cf/meta/llama-3.3-70b-instruct-fp8-fast', '@cf/mistralai/mistral-small-3.1-24b-instruct', '@cf/meta/llama-4-scout-17b-16e-instruct', '@cf/qwen/qwen3-30b-a3b-fp8'];
    const model = (body.modell && ERLAUBT.includes(body.modell)) ? body.modell : (env.MODEL || DEFAULT_MODEL);
    try {
      for (let schritt = 0; schritt < 5; schritt++) {
        // Nach einer App-Aktion keine weiteren Werkzeuge mehr anbieten: nur noch kurz bestätigen
        const out = await env.AI.run(model, aktionen.length ? { messages, max_tokens: 300 } : { messages, tools: TOOLS, max_tokens: 900 });
        const m = msgOf(out);
        const calls = aktionen.length ? [] : (m.tool_calls || []);
        if (!calls.length) {
          let antwort = cleanText(m.content);
          if (!antwort) {
            // kaputte oder leere Ausgabe: mit einem zweiten Modell aus denselben echten Daten neu formulieren
            antwort = await fallbackAnswer(env, messages);
          }
          if (!antwort) antwort = aktionen.length ? 'Erledigt, schau in die App.' : 'Das habe ich gerade nicht hinbekommen. Frag mich bitte nochmal.';
          return json({ antwort, aktionen, orte: dedupe(orte), modell: model });
        }
        messages.push({ role: 'assistant', content: m.content || '', tool_calls: calls });
        for (const c of calls) {
          const name = c.function?.name || c.name;
          let args = c.function?.arguments ?? c.arguments ?? {};
          if (typeof args === 'string') { try { args = JSON.parse(args || '{}'); } catch { args = {}; } }
          let result;
          if (APP_ACTIONS.has(name)) {
            aktionen.push({ typ: name, ...args });
            result = { status: 'Die App führt das jetzt aus und zeigt das Ergebnis an.' };
          } else if (SERVER_TOOLS[name]) {
            try { result = await SERVER_TOOLS[name](args); } catch (e) { result = { fehler: 'Werkzeug fehlgeschlagen: ' + e.message }; }
            collectPlaces(result, orte);
          } else result = { fehler: 'Unbekanntes Werkzeug' };
          messages.push({ role: 'tool', tool_call_id: c.id, name, content: JSON.stringify(result).slice(0, 5000) });
        }
      }
      return json({ antwort: 'Das war mir gerade zu kompliziert. Frag mich bitte etwas genauer.', aktionen, orte: dedupe(orte) });
    } catch (e) {
      console.error('KI-Fehler', e && e.message);
      if (/4006|daily free allocation/i.test(String(e && e.message)))
        return json({ fehler: 'Juno hat ihr kostenloses Tageskontingent für heute aufgebraucht. Ab 1 Uhr nachts (deutsche Zeit) geht es wieder. Mit dem bezahlten Cloudflare-Tarif gäbe es kein Tageslimit.' }, 503);
      return json({ fehler: 'Juno ist gerade nicht erreichbar. Bitte später nochmal versuchen.' }, 503);
    }
  }
};

const FALLBACK_MODEL = '@cf/meta/llama-3.3-70b-instruct-fp8-fast';
async function fallbackAnswer(env, messages) {
  // Werkzeug-Ergebnisse als Text zusammenfassen, damit das Ersatzmodell sie ohne Werkzeug-Format versteht
  const sys = messages[0].content;
  const userMsgs = messages.filter(m => m.role === 'user');
  const toolText = messages.filter(m => m.role === 'tool').map(m => (m.name || 'werkzeug') + ': ' + m.content).join('\n').slice(0, 6000);
  try {
    const out = msgOf(await env.AI.run(FALLBACK_MODEL, { messages: [
      { role: 'system', content: sys + (toolText ? '\n\nErgebnisse der Werkzeuge (echte Daten):\n' + toolText : '') },
      { role: 'user', content: userMsgs[userMsgs.length - 1].content }
    ], max_tokens: 500 }));
    return cleanText(out.content);
  } catch (e) { return ''; }
}

// Interne Steuerzeichen oder Zeichensalat des Modells erkennen und entfernen
function cleanText(t) {
  t = String(t || '');
  if (/<\|(channel|start|end|call|return|message)\|>/.test(t)) {
    const fin = t.split('<|channel|>final');
    t = fin.length > 1 ? fin[fin.length - 1] : '';
    t = t.replace(/<\|[^|]*\|>/g, '').replace(/^\s*(assistant)?\s*/i, '');
  }
  t = t.trim();
  // Wiederholungs-Salat ("Ap Ap Ap ...", "!!!!!!!!") oder Text fast ohne Buchstaben verwerfen
  if (/(\b\w+\b)(\s+\1){5,}/i.test(t) || /(.)\1{11,}/.test(t) || t.length > 2500) return '';
  const letters = (t.match(/[A-Za-zÄÖÜäöüßÀ-ÿčćšžđČĆŠŽĐ]/g) || []).length;
  if (t.length > 20 && letters / t.length < 0.45) return '';
  return t;
}

// Orte aus Werkzeug-Ergebnissen sammeln, damit die App sie antippbar anzeigen kann
function collectPlaces(r, orte) {
  const list = Array.isArray(r) ? r : (r && r.gefunden) || [];
  list.forEach(x => { if (x && x.name && x.lat != null) orte.push({ name: x.name, lat: x.lat, lon: x.lon, info: x.kategorie || x.typ || '' }); });
}
function dedupe(o) {
  const seen = new Set();
  return o.filter(x => { const k = x.name + Math.round(x.lat * 1000); if (seen.has(k)) return false; seen.add(k); return true; }).slice(0, 12);
}
