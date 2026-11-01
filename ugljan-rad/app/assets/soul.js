/* Soul: die eigene KI von Velora. Laeuft komplett in der App, ohne fremde KI und ohne Kosten.
   Prinzip wie im Projekt "Soul": Absicht erkennen (mit Tippfehler-Korrektur), Orte/Zahlen/Zeiten herauslesen,
   Werkzeuge der App ausfuehren, Antwort bauen, bei Wissensfragen im Hintergrund recherchieren
   (Wikipedia, auf Android zusaetzlich DuckDuckGo ueber die App) und die passendsten Saetze auswaehlen. */
(function () {
  'use strict';
  var V = window.V, VX = window.VX || {}; if (!V) return;
  var A = V.A;

  // ---------------------------------------------------------------------------------------------
  // Text-Werkzeuge
  // ---------------------------------------------------------------------------------------------
  function norm(s) {
    return String(s || '').toLowerCase().replace(/ä/g, 'ae').replace(/ö/g, 'oe').replace(/ü/g, 'ue').replace(/ß/g, 'ss')
      .normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/[^a-z0-9\s.,%+\-*/:?']/g, ' ').replace(/[?!]/g, ' ').replace(/([a-z])[.,](?=\s|$)/g, '$1').replace(/\s+/g, ' ').trim();
  }
  function pick(a) { return a[Math.floor(Math.random() * a.length)]; }
  function cap(s) { return s ? s.charAt(0).toUpperCase() + s.slice(1) : s; }
  function km(m) { return (m / 1000).toFixed(1).replace('.', ','); }
  function kmE(m) { return (m / 1000).toFixed(1); }
  function lev(a, b) {
    if (Math.abs(a.length - b.length) > 2) return 9;
    var d = []; for (var i = 0; i <= a.length; i++) { d[i] = [i]; }
    for (var j = 1; j <= b.length; j++) d[0][j] = j;
    for (i = 1; i <= a.length; i++) for (j = 1; j <= b.length; j++) d[i][j] = Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + (a[i - 1] === b[j - 1] ? 0 : 1));
    return d[a.length][b.length];
  }
  // Wortschatz der Absichten fuer die Tippfehler-Korrektur
  var VOCAB = ('hallo servus wetter regen regnet wind temperatur morgen heute uebermorgen wochenende route strecke navigation starte fahren fahrt fahrten ' +
    'restaurant essen pizza burger hunger kaffee cafe eis kuchen strand baden schwimmen aussicht aussichtspunkt wasser toilette radladen werkstatt ' +
    'supermarkt apotheke unterkunft hotel sehenswuerdigkeit museum kirche tour runde ausflug kuerzer flacher schoener schneller laenger leichter ' +
    'statistik kalorien abzeichen serie flamme rekord bestzeit woche monat insgesamt letzte geschwindigkeit tempo ankunft anstieg hoehe ' +
    'reifen schlauch platten flicken luftdruck kette schaltung bremse sattel licht helm training trainieren ausdauer motivation danke witz ' +
    'weather forecast restaurant nearby distance tomorrow today calories streak record speed tire chain brake helmet thanks joke').split(' ');
  var VSET = {}; VOCAB.forEach(function (w) { VSET[w] = 1; });
  // haeufige Woerter, die nie "korrigiert" werden duerfen
  var COMMON = {}; ('nichts gerne immer wieder einfach wirklich vielleicht eigentlich bisschen gerade weisst kannst koennen machen machst haben hatte hatten waren wurde wurden werden warum wieso woher welche welcher welches dieser diese dieses meine meinen meiner deine seine unsere schoen grosse kleine lange wenig alles etwas jemand niemand leute menschen freund freunde freundin familie mutter vater bruder schwester katze arbeit schule hause zuhause trinken schlafen spielen lernen fahren gehen kommen sehen sagen denken glauben finden wissen heisse heisst wohne wohnst liebe liebst magst brauche brauchst suche suchst hilfe fragen antwort problem gefaellt gefuehle langweilig traurig gluecklich krank muede stress gestern abends nachts wochen monate jahre stunde stunden minuten berge wasser sonne schnee wolken ganzen keinen keiner morgens mittags danke bitte hallo tschuess spaeter frueher anders andere anderen vielen trotzdem deshalb genau richtig falsch neues neuen alten nochmal erzaehl erzaehle schreib schreibe zeige gibst geben nehmen bringen bringst fahre faehrst gefahren geradelt unterwegs lieblings meinung denkst haeltst findest'.split(' ')).forEach(function (w) { COMMON[w] = 1; });
  function fix(t) {
    return t.split(' ').map(function (w) {
      if (w.length < 6 || VSET[w] || SW[w] || COMMON[w] || /\d/.test(w)) return w;
      var best = null, bd = w.length >= 9 ? 2 : 1;
      VOCAB.forEach(function (v) { var d = lev(w, v); if (d <= bd && (!best || d < best.d)) best = { v: v, d: d }; });
      return best ? best.v : w;
    }).join(' ');
  }
  function has(t, re) { return re.test(t); }

  // Sprache: App-Sprache, ausser die Nachricht ist klar englisch
  function langOf(raw) {
    var app = window.I18N ? I18N.lang : 'de', t = ' ' + norm(raw) + ' ';
    var en = (t.match(/ (the|is|are|what|where|how|can|you|your|my|please|show|me|weather|near|nearby|thanks|thank|hello|hi|there|i|to|do|does|which|who|will|it|tomorrow|today|get|tell|joke|rain|good|morning|any|some|take|about|know|another|one|bye|much|many|should|would|could|with|for|of|and|from|this|that|have|has) /g) || []).length;
    var de = (t.match(/ (der|die|das|ist|und|wie|wo|was|kannst|du|dir|mein|meine|bitte|zeig|mir|wetter|ich|zu|nach|wer|welche|hallo|danke|gibt|es|hier|heute|morgen|regnet|erzaehl|einen|eine|war|letzte|servus|gehts) /g) || []).length;
    if (en > de) return 'en';
    if (de > en) return 'de';
    return app === 'de' ? 'de' : 'en';
  }
  var L = 'de';
  function T(de, en) { return L === 'de' ? de : en; }

  // ---------------------------------------------------------------------------------------------
  // Wissen (soul-data.js) und Abgleich: Schluesselsaetze gegen die Nachricht, mit Wortstamm und Tippfehlern
  // ---------------------------------------------------------------------------------------------
  var SD = window.SOUL_DATA || { faq: [], chat: [], facts: [], riddles: [], quiz: [], story: null, poems: [], opinions: [] };
  function stem(w) { return w.length > 5 ? w.replace(/(ungen|ung|heit|keit|lich|isch|ern|en|er|es|em|e|n|s)$/, '') : w; }
  function keyScore(key, toks) {
    var kt = key.split(' '), hit = 0;
    kt.forEach(function (w) {
      var sw = stem(w);
      if (toks.some(function (m) { return m === w || stem(m) === sw || (w.length >= 5 && m.length >= 5 && lev(m, w) <= 1); })) hit++;
    });
    return { hit: hit, n: kt.length, r: hit / kt.length };
  }
  // bestes Element einer Liste; strong: mindestens zwei Woerter muessen passen
  function bestOf(list, t, opt) {
    opt = opt || {};
    var toks = t.split(' ').filter(Boolean), best = null;
    list.forEach(function (e, i) {
      e.k.forEach(function (key) {
        var s = keyScore(key, toks);
        if (s.n <= 2 ? s.r < 1 : s.r < 0.75) return;
        if (opt.strong && s.hit < 2) return;
        if (opt.coverage && s.n === 1 && toks.length > 3) return;
        if (opt.coverage && s.hit / toks.length < opt.coverage && toks.length > 4) return;
        var sc = s.hit + (s.r === 1 ? 0.5 : 0);
        if (!best || sc > best.sc) best = { e: e, i: i, sc: sc };
      });
    });
    return best;
  }
  // nie zweimal hintereinander dieselbe Formulierung
  var lastPick = {};
  function pickNR(arr, key) {
    if (!arr || !arr.length) return '';
    if (arr.length < 2) return arr[0];
    var i, g = 0; do { i = Math.floor(Math.random() * arr.length); } while (i === lastPick[key] && ++g < 10);
    lastPick[key] = i; return arr[i];
  }

  // Was Soul sich ueber dich merkt (bleibt auf dem Handy)
  var MEM = V.load('soulMem', { name: null, ort: null, mag: [], rad: null, alter: null, places: {} });
  function saveMem() { V.store('soulMem', MEM); }

  // ---------------------------------------------------------------------------------------------
  // Gespraechsgedaechtnis
  // ---------------------------------------------------------------------------------------------
  var M = { last: null, place: null, list: [], offer: null, article: null, sentIdx: 0, name: null, turns: 0, lastTopic: null };
  function userName() {
    if (MEM.name) return MEM.name;
    try { var a = VX.account && VX.account(); if (a && a.user && a.user.name) return a.user.name.split(' ')[0]; } catch (e) {}
    return null;
  }
  function userCountry() { try { var a = VX.account && VX.account(); return a && a.user && a.user.country; } catch (e) { return null; } }

  // ---------------------------------------------------------------------------------------------
  // Hilfen: Netz (Hintergrund-Browser), Orte, Wetter
  // ---------------------------------------------------------------------------------------------
  var pend = {}, pid = 0;
  // Auf Android holt die App Seiten selbst (ohne Browser-Sperren), sonst normales fetch
  function getText(url) {
    if (A && A.fetchText) return new Promise(function (res, rej) { var id = 'f' + (++pid); pend[id] = { res: res, rej: rej }; setTimeout(function () { if (pend[id]) { delete pend[id]; rej(new Error('timeout')); } }, 15000); A.fetchText(id, url); });
    return fetch(url).then(function (r) { if (!r.ok) throw new Error(r.status); return r.text(); });
  }
  function getJSON(url) { return fetch(url).then(function (r) { if (!r.ok) throw new Error(r.status); return r.json(); }); }
  function here(k) { return k && k.position ? { lat: k.position.lat, lon: k.position.lon, gps: k.position.quelle === 'GPS' } : null; }
  function geocode(q, k) {
    var h = here(k), u = 'https://photon.komoot.io/api/?q=' + encodeURIComponent(q) + '&limit=5&lang=' + (L === 'de' ? 'de' : 'en') + (h ? '&lat=' + h.lat + '&lon=' + h.lon : '');
    return getJSON(u).then(function (j) {
      var f = (j.features || [])[0]; if (!f) throw new Error('none');
      var p = f.properties, g = f.geometry.coordinates;
      return { name: p.name || q, lat: g[1], lon: g[0], sub: [p.city && p.city !== p.name ? p.city : '', p.country || ''].filter(Boolean).join(', ') };
    }).catch(function () {
      return getJSON('https://nominatim.openstreetmap.org/search?format=jsonv2&limit=1&q=' + encodeURIComponent(q)).then(function (l) {
        if (!l.length) throw new Error('none'); return { name: String(l[0].display_name).split(',')[0], lat: +l[0].lat, lon: +l[0].lon, sub: String(l[0].display_name).split(',').slice(1, 3).join(',') };
      });
    });
  }
  var WC = { 0: ['klar', 'clear'], 1: ['überwiegend klar', 'mostly clear'], 2: ['teilweise bewölkt', 'partly cloudy'], 3: ['bedeckt', 'overcast'], 45: ['Nebel', 'fog'], 48: ['Nebel', 'fog'], 51: ['Nieselregen', 'drizzle'], 53: ['Nieselregen', 'drizzle'], 55: ['Nieselregen', 'drizzle'], 61: ['leichter Regen', 'light rain'], 63: ['Regen', 'rain'], 65: ['starker Regen', 'heavy rain'], 71: ['leichter Schnee', 'light snow'], 73: ['Schnee', 'snow'], 75: ['starker Schnee', 'heavy snow'], 80: ['Regenschauer', 'showers'], 81: ['Regenschauer', 'showers'], 82: ['heftige Schauer', 'heavy showers'], 95: ['Gewitter', 'thunderstorm'], 96: ['Gewitter mit Hagel', 'thunderstorm with hail'], 99: ['Gewitter mit Hagel', 'thunderstorm with hail'] };
  function wcode(c) { var w = WC[c] || WC[Math.floor(c / 10) * 10] || ['wechselhaft', 'changeable']; return L === 'de' ? w[0] : w[1]; }
  function weather(h) {
    return getJSON('https://api.open-meteo.com/v1/forecast?latitude=' + h.lat.toFixed(3) + '&longitude=' + h.lon.toFixed(3) +
      '&current=temperature_2m,weather_code,wind_speed_10m,wind_gusts_10m,precipitation&hourly=precipitation_probability,temperature_2m,weather_code&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,wind_speed_10m_max,sunset&forecast_days=4&timezone=auto');
  }

  // ---------------------------------------------------------------------------------------------
  // Absichten
  // ---------------------------------------------------------------------------------------------
  var CATS = [
    ['food', /\b(essen|restaurant|restaurants|pizza|pizzeria|burger|hunger|hungrig|mittagessen|abendessen|gasthaus|gasthof|wirtshaus|konoba|food|eat|lunch|dinner|schnitzel|pasta|sushi|kebab|doener)\b/],
    ['cafe', /\b(cafe|cafes|kaffee|coffee|eis|eisdiele|kuchen|baeckerei|bakery|ice cream|gelato|fruehstueck|breakfast)\b/],
    ['bar', /\b(bar|bars|bier|beer|drink|cocktail|pub|kneipe)\b/],
    ['beach', /\b(strand|straende|baden|schwimmen|badesee|see zum baden|beach|swim|swimming|badestelle)\b/],
    ['view', /\b(aussicht|aussichtspunkt|panorama|ausblick|view|viewpoint|sonnenuntergang schauen)\b/],
    ['water', /\b(trinkwasser|wasser auffuellen|brunnen|water|trinken)\b/],
    ['wc', /\b(wc|toilette|toiletten|klo|toilet|restroom)\b/],
    ['bike', /\b(radladen|fahrradladen|radgeschaeft|werkstatt|reparatur|bike shop|repair|fahrradwerkstatt)\b/],
    ['shop', /\b(supermarkt|einkaufen|laden|shop|groceries|spar|billa|lidl|hofer|aldi)\b/],
    ['health', /\b(apotheke|arzt|krankenhaus|pharmacy|doctor|hospital)\b/],
    ['stay', /\b(hotel|unterkunft|uebernachten|pension|camping|zelten|hostel)\b/],
    ['sight', /\b(sehenswuerdigkeit|sehenswuerdigkeiten|museum|kirche|burg|schloss|denkmal|sightseeing|attraction|castle)\b/]
  ];
  var CAT_N = { food: ['Restaurants', 'restaurants'], cafe: ['Cafés', 'cafés'], bar: ['Bars', 'bars'], beach: ['Badestellen', 'swimming spots'], view: ['Aussichtspunkte', 'viewpoints'], water: ['Trinkwasser-Stellen', 'drinking water'], wc: ['Toiletten', 'toilets'], bike: ['Radwerkstätten', 'bike shops'], shop: ['Läden', 'shops'], health: ['Apotheken und Ärzte', 'pharmacies and doctors'], stay: ['Unterkünfte', 'places to stay'], sight: ['Sehenswürdigkeiten', 'sights'] };
  var CAT_EMO = { food: '🍽️', cafe: '☕', bar: '🍺', beach: '🏖️', view: '🌄', water: '💧', wc: '🚻', bike: '🔧', shop: '🛒', health: '💊', stay: '🛏️', sight: '🏰' };
  var OSMTAG = { food: 'amenity=restaurant', cafe: 'amenity~"cafe|ice_cream"', bar: 'amenity~"bar|pub|biergarten"', beach: 'leisure=bathing_place', view: 'tourism=viewpoint', water: 'amenity=drinking_water', wc: 'amenity=toilets', bike: 'shop=bicycle', shop: 'shop~"supermarket|convenience"', health: 'amenity~"pharmacy|doctors|hospital"', stay: 'tourism~"hotel|guest_house|hostel|camp_site"', sight: 'tourism~"attraction|museum"' };
  var KCAT = { food: ['restaurant', 'fast_food', 'food', 'snack', 'pizza'], cafe: ['cafe', 'ice_cream', 'bakery', 'konditorei'], bar: ['bar', 'pub', 'beer', 'biergarten'], beach: ['beach', 'swimming', 'bathing', 'strand'], view: ['viewpoint', 'aussicht', 'view'], water: ['drinking_water', 'wasser', 'trinkwasser'], wc: ['toilets', 'wc', 'toilette'], bike: ['bicycle', 'fahrrad'], shop: ['grocery', 'supermarket', 'shop', 'convenience'], health: ['pharmacy', 'doctors', 'hospital', 'gesundheit'], stay: ['lodging', 'hotel', 'campsite', 'unterkunft'], sight: ['attraction', 'museum', 'castle', 'monument', 'sehenswuerdigkeit'] };

  function catOf(t) { for (var i = 0; i < CATS.length; i++) if (CATS[i][1].test(t)) return CATS[i][0]; return null; }
  var Q = /^(wer|was|wann|wo|wie|warum|wieso|weshalb|welche|welcher|welches|wieviel|wieviele|woher|wohin|kann|kannst|ist|sind|gibt|hat|haben|soll|darf|weisst|who|what|when|where|why|how|which|is|are|can|does|do|did|should|will)\b/;
  function isQuestion(raw, t) { return /\?\s*$/.test(raw) || Q.test(t); }

  function durationOf(t) {
    var m = t.match(/(\d+(?:[.,]\d+)?)\s*(stunden|stunde|std|h|hours|hour)\b/); if (m) return Math.round(parseFloat(m[1].replace(',', '.')) * 60);
    m = t.match(/(\d+)\s*(minuten|min|minutes)\b/); if (m) return +m[1];
    if (/halbe stunde|half an hour/.test(t)) return 30;
    if (/eineinhalb|anderthalb|1 1\/2/.test(t)) return 90;
    if (/(eine|ein|one|an) (stunde|hour)/.test(t)) return 60;
    if (/(zwei|two) (stunden|hours)/.test(t)) return 120;
    if (/(drei|three) (stunden|hours)/.test(t)) return 180;
    if (/halben tag|half a day|half day/.test(t)) return 240;
    if (/ganzen tag|whole day|full day|tagestour/.test(t)) return 360;
    return null;
  }
  function distanceOf(t) { var m = t.match(/(\d+(?:[.,]\d+)?)\s*(km|kilometer)\b/); return m ? parseFloat(m[1].replace(',', '.')) : null; }
  // Ziel aus "nach X", "zu X", "to X"; Grossschreibung aus dem Original behalten
  function targetOf(raw) {
    var r = raw.replace(/[?!.]+$/, '');
    var m = r.match(/\b(?:nach|zum|zur|zu|bis|ins|in den|in die|richtung|to|towards)\s+(.{2,60}?)(?:\s+(?:fahren|radeln|navigieren|planen|bitte|please|mit dem rad|with the bike|route)\b|$)/i);
    if (!m) m = r.match(/\b(?:route|weg|strecke|radweg)\s+(?:nach|zu|zum|zur|to)?\s*(.{2,60})$/i);
    if (!m) return null;
    var x = m[1].replace(/^(der|die|das|dem|den|the)\s+/i, '').trim();
    if (/^(hause|haus|home)$/i.test(x)) return { home: true };
    if (/^(dort|da|dahin|dorthin|there|it|this place)$/i.test(x)) return { there: true };
    return x.length >= 2 ? { q: x } : null;
  }

  function detect(raw, t) {
    var q = isQuestion(raw, t);
    if (/\d+(?:[.,]\d+)?\s*(%|prozent|percent)\s*(von|of)\s*\d/.test(t) || /\d+(?:[.,]\d+)?\s*(km|kilometer|meilen|miles|mi|kmh|km\/h|ms|m\/s)\s*(in|to)\s*(km|kilometer|meilen|miles|mi|kmh|km\/h|ms|m\/s)\b/.test(t)) return 'math';
    if (/^[\d\s+\-*/().,x:^%=]+$/.test(t.replace(/\b(mal|plus|minus|geteilt durch|durch|times|divided by|ist|=)\b/g, ' ').replace(/(was ist|rechne|berechne|what is|calculate)/, '')) && /\d/.test(t) && /[+\-*/x:]|mal|plus|minus|geteilt|times|divided/.test(t)) return 'math';
    if (/^(ja|jo|jep|jup|gerne|gern|klar|ok|okay|passt|mach (das|mal|es)|bitte|yes|yeah|yep|sure|go|los|na klar|unbedingt|auf jeden fall)\b/.test(t) && t.split(' ').length <= 5) return 'yes';
    if (/^(nein|nee|ne|no|nope|lieber nicht|doch nicht|nicht noetig|passt schon)\b/.test(t) && t.split(' ').length <= 5) return 'no';
    if (/\b(starte?|beginne?|start|begin)\b.*\b(navigation|route|fahrt|navi)\b|\b(navigation|route) (starten|start)\b|\blass uns (los|fahren|losfahren)\b|\blets go\b|\bfahr los\b/.test(t)) return 'nav_start';
    if (/\b(wie (werde|wird) (ich|man)|werden|trainier|training|fitter|ausdauer|kondition|abnehmen|get faster|get fitter)\b/.test(t) && /\b(schneller|fitter|besser|staerker|ausdauer|kondition|bergauf|berg|abnehmen|faster|fitter|stronger)\b/.test(t)) return 'training';
    if (/\b(kuerzer|flacher|leichter|einfacher|schoener|schneller|laenger|weniger (steil|berge?|hoehenmeter|anstieg)|ohne (berge?|anstiege)|nicht so (steil|lang|weit)|zu (steil|lang|weit)|shorter|flatter|easier|nicer|faster|longer|less (steep|climbing)|too (steep|long|far))\b/.test(t) && !/\bwarum\b/.test(t)) return 'adjust';
    var tg = targetOf(raw);
    if (tg && /\b(route|weg|fahr|fahre|fahren|radeln|navigier|bring|komme|kommen|plan|zeig mir den weg|get to|ride|cycle|directions|way|navigate|take me|wie weit)\b/i.test(t) && !/\b(wetter|weather)\b/.test(t)) return 'route';
    if (/\b(wie schnell (bin|fahre) ich|mein tempo|wie weit (noch|ist es noch)|wann (bin ich|komme ich) (da|an)|restzeit|wie lange noch|wie viele? (km|kilometer) noch|naechste (abbiegung|anweisung)|naechster anstieg|wie hoch bin ich|auf welcher hoehe|how fast am i|how far (is it )?(left|to go)|when will i arrive|eta)\b/.test(t)) return 'live';
    if (/\b(anziehen|kleidung|jacke mitnehmen|regenjacke|what (should i )?wear|clothes)\b/.test(t)) return 'wear';
    if (/\b(wetter|regen|regnet|regnen|wind|windig|temperatur|grad|sonnig|sonne scheint|gewitter|schnee|wie warm|wie kalt|weather|rain|raining|forecast|windy|temperature|sunny)\b/.test(t)) return 'weather';
    var mine = /\b(ich|mein|meine|meinen|meiner|mir|hab|habe|bin|my|i|me)\b/.test(t);
    if (/\b(statistik|abzeichen|serie|flamme|streak|rekord|rekorde|bestzeit|bestzeiten|wochenziel|meine (fahrt|fahrten|woche|leistung|kilometer|km)|letzte(n)? fahrt|wie (gut|weit|viel|oft|schnell) bin ich (gefahren|geradelt|unterwegs)|wie war (meine|die|meine letzte) (fahrt|woche|tour)|insgesamt gefahren|diese woche gefahren|bin ich besser|fortschritt|my (ride|rides|stats|week|streak|badges|progress)|how (far|much|often) (did|have) i|last ride)\b/.test(t) ||
      (mine && /\b(kalorien|kcal|wie viele? (km|kilometer|hoehenmeter|fahrten)|letzten monat|calories)\b/.test(t))) return 'stats';
    if (/\b(tour|touren|runde|rundtour|rundfahrt|ausflug|ausfahrt|radtour|vorschlag|vorschlagen|idee|ideen|loop|ride idea|where (should|can) i ride|wohin (soll|kann) ich (fahren|radeln))\b/.test(t) && !catOf(t)) return 'tour';
    var c = catOf(t);
    if (c && (/\b(in der naehe|hier|nearby|um mich|in meiner naehe|wo (gibt|ist|kann|finde|bekomme)|naechste|naechstes|naechsten|gibt es|irgendwo|zeig|zeige|suche|such|find|where|any|around|hungrig|hunger|durst)\b/.test(t) || t.split(' ').length <= 3)) return 'nearby';
    if (/\b(problem|probleme|kaputt|defekt|funktioniert nicht|geht nicht|klappt nicht)\b/.test(t) && /\b(rad|fahrrad|bike|reifen|kette|bremse|bremsen|schaltung|licht|sattel|pedal|ebike|akku)\b/.test(t)) return 'bike';
    if (/\b(platten|platter reifen|reifen|schlauch|flicken|luftdruck|reifendruck|kette|kettenoel|schaltung|gaenge|gang springt|bremse|bremsen|quietscht|knackt|sattel|sattelhoehe|licht|beleuchtung|oelen|schmieren|wartung|putzen|flat tire|puncture|tyre|tire pressure|chain|gears|brakes?|saddle|squeak|maintenance)\b/.test(t)) return 'bike';
    if (/\b(schneller werden|fitter|fit werden|trainier|training|ausdauer|kondition|berg ?hoch|bergauf (besser|leichter)|abnehmen|trittfrequenz|kadenz|intervall|get faster|get fitter|climb better|lose weight|cadence)\b/.test(t)) return 'training';
    if (/\b(helm|sicherheit|sicher fahren|unfall|im dunkeln|nachts fahren|verkehrsregel|handzeichen|safety|helmet|accident|at night)\b/.test(t)) return 'safety';
    if (/\b(velora plus|plus|premium|abo|preis|kostet die app|wochenziel|offline.?karte|live.?standort|live teilen|gpx|tagesplaner|widget|abzeichen|velo|fuchs|rueckenwind|vibration|nachtmodus)\b/.test(t) && /\b(wie|was|wozu|erklaer|funktioniert|bedeutet|kann ich|wo finde|how|what|explain)\b/.test(t)) return 'apphelp';
    if (/\b(wie spaet|uhrzeit|wie viel uhr|welcher tag|welches datum|datum heute|what time|what day)\b/.test(t)) return 'time';
    if (/^(hallo|hi|hey|servus|moin|gruess gott|griass di|gruezi|guten (morgen|tag|abend)|hello|good (morning|afternoon|evening)|yo|na du|huhu|hallihallo)\b/.test(t)) return t.split(' ').length <= 4 ? 'greet' : (q ? 'greetq' : 'greet');
    if (/\b(wie geht( es|s)?( dir)?|wie gehts|was geht|alles (gut|klar) bei dir|how are you|hows it going|whats up)\b/.test(t)) return 'howru';
    if (/\b(wer bist du|was bist du|wie heisst du|bist du (eine |ein )?(ki|bot|mensch|roboter)|who are you|what are you|your name|are you (a |an )?(ai|bot|human))\b/.test(t)) return 'who';
    if (/\b(was kannst du|was kann ich (dich )?fragen|wobei hilfst du|hilfe|help|what can you do)\b/.test(t)) return 'can';
    if (/\b(danke|dankeschoen|vielen dank|merci|thx|thanks|thank you|grazie|gracias)\b/.test(t)) return 'thanks';
    if (/\b(tschuess|tschau|ciao|bye|bis (bald|spaeter|dann|morgen)|gute nacht|baba|servas|see you|good night)\b/.test(t)) return 'bye';
    if (/\b(witz|witze|joke|jokes|bring mich zum lachen|was lustiges|funny)\b/.test(t)) return 'joke';
    if (/\b(du bist (so |echt |wirklich )?(toll|super|cool|klasse|lieb|die beste|genial|nett|hilfreich|schlau)|ich mag dich|ich liebe dich|gut gemacht|love you|you are (great|awesome|cool|the best)|good job)\b/.test(t)) return 'compliment';
    if (/\b(du bist (so |echt )?(dumm|bloed|doof|schlecht|nutzlos|langweilig)|halt die klappe|stupid|useless|shut up)\b/.test(t)) return 'insult';
    if (/\b(keine lust|bin (so |sehr |total )?(muede|faul|kaputt|erschoepft)|schaffe (es|das) nicht|zu anstrengend|no motivation|tired|lazy)\b/.test(t)) return 'lowmood';
    if (/\b(war|bin) (heute |gestern |gerade |eben )?(radeln|radfahren|rad fahren|gefahren|geradelt|unterwegs|biken)\b|\b(i (went|was) (cycling|riding)|i rode)\b/.test(t)) return 'rode';
    if (/\b(bin (gut drauf|happy|gluecklich|stolz|froh)|super tag|geschafft|ich habe es geschafft|feeling great|i did it)\b/.test(t)) return 'goodmood';
    if (/\b(wie alt bist du|how old are you)\b/.test(t)) return 'age';
    if (q) return 'knowledge';
    return 'chat';
  }

  // ---------------------------------------------------------------------------------------------
  // Wissen: Wikipedia (und auf Android DuckDuckGo) im Hintergrund, beste Saetze auswaehlen
  // ---------------------------------------------------------------------------------------------
  var STOP = ' der die das ein eine einer eines einem einen und oder ist sind war waren wird werden wer was wann wo wie warum wieso weshalb welche welcher welches wieviel wieviele woher wohin kann kannst koennen ich du er sie es wir ihr mir mich dir dich uns euch mein dein sein ihr auf in im an am zu zum zur von vom mit bei fuer ueber unter aus nach vor seit bis hat haben gibt gab es den dem des denn dann doch mal noch schon so sehr auch nur nicht kein keine bitte sag sage mir erklaer erklaere erzaehl weisst du eigentlich genau eigentlich viel viele the a an of to in on at for is are was were who what when where why how which do does did can could you me tell about please '.split(' ');
  var SW = {}; STOP.forEach(function (w) { if (w) SW[w] = 1; });
  function content(t) { return t.split(' ').filter(function (w) { return w.length > 2 && !SW[w]; }); }
  function qtype(t) {
    if (/\b(wann|when|jahr|year|seit wann)\b/.test(t)) return 'year';
    if (/\b(wie hoch|hoehe|how high|how tall|height)\b/.test(t)) return 'height';
    if (/\b(wie lang|wie weit|laenge|how long|how far|length)\b/.test(t)) return 'length';
    if (/\b(wie viele|wieviele|wie viel|anzahl|einwohner|how many|how much|population)\b/.test(t)) return 'count';
    if (/\b(wo liegt|wo ist|where is|where are)\b/.test(t)) return 'where';
    if (/\b(wer|who)\b/.test(t)) return 'who';
    return 'what';
  }
  // Saetze trennen, aber nicht nach Abkuerzungen (z. B., v. Chr., ü. A., Nr., Zahlen wie "1. Januar")
  var ABBR = { bzw: 1, ca: 1, etc: 1, usw: 1, inkl: 1, evtl: 1, ggf: 1, vgl: 1, chr: 1, jh: 1, jhd: 1, nr: 1, st: 1, dr: 1, prof: 1, mio: 1, mrd: 1, bspw: 1, sog: 1, gem: 1, hl: 1, ev: 1, kath: 1, eig: 1, mr: 1, mrs: 1, vs: 1, approx: 1, est: 1, jr: 1, sr: 1 };
  function sentences(txt) {
    var t = String(txt).replace(/\s+/g, ' ').replace(/\s*\([^)]{0,80}\)/g, ''), out = [], start = 0, re = /[.!?]\s+(?=[A-ZÄÖÜ0-9„"])/g, m;
    while ((m = re.exec(t))) {
      var before = t.slice(start, m.index), w = (before.match(/(\S+)$/) || ['', ''])[1].replace(/^[„"(]/, '');
      if (m[0][0] === '.' && (w.length <= 2 || /^\d+$/.test(w) || ABBR[w.toLowerCase()])) continue;
      out.push(t.slice(start, m.index + 1).trim()); start = m.index + m[0].length;
    }
    out.push(t.slice(start).trim());
    return out.filter(function (s) { return s.length > 25 && s.length < 450; });
  }
  function rank(sents, t) {
    var cw = content(fix(t)), ty = qtype(t);
    return sents.map(function (s, i) {
      var n = norm(s), sc = 0;
      cw.forEach(function (w) { if (n.indexOf(w.slice(0, Math.max(4, w.length - 2))) >= 0) sc += 2; });
      if (ty === 'year' && /\b1\d{3}|20\d{2}\b/.test(s)) sc += 3;
      if (ty === 'height' && /\d[\d.,]*\s*(m|meter|metern)\b/.test(s)) sc += 4;
      if (ty === 'length' && /\d[\d.,]*\s*(km|kilometer|m|meter)\b/.test(s)) sc += 4;
      if (ty === 'count' && /\d[\d.,]*/.test(s)) sc += 3;
      if (ty === 'where' && /\b(liegt|befindet|located|lies|im|in der|in den)\b/.test(n)) sc += 2;
      sc += Math.max(0, 3 - i) * (ty === 'what' || ty === 'who' ? 1.2 : 0.4);
      return { s: s, sc: sc, i: i };
    }).sort(function (a, b) { return b.sc - a.sc; });
  }
  function wikiLang() { return L === 'de' ? 'de' : 'en'; }
  function wikiSearch(query, lang) {
    return getJSON('https://' + lang + '.wikipedia.org/w/api.php?action=query&list=search&format=json&origin=*&srlimit=3&srsearch=' + encodeURIComponent(query))
      .then(function (j) { return ((j.query && j.query.search) || []).map(function (x) { return x.title; }); });
  }
  function wikiText(title, lang) {
    return getJSON('https://' + lang + '.wikipedia.org/w/api.php?action=query&prop=extracts&explaintext=1&exsectionformat=plain&format=json&origin=*&redirects=1&titles=' + encodeURIComponent(title))
      .then(function (j) { var p = j.query && j.query.pages, k = p && Object.keys(p)[0]; return k && p[k].extract ? { title: p[k].title, text: p[k].extract.slice(0, 20000) } : null; });
  }
  function ddg(query) {
    if (!(A && A.fetchText)) return Promise.reject(new Error('no bridge'));
    return getText('https://html.duckduckgo.com/html/?q=' + encodeURIComponent(query)).then(function (h) {
      var out = [], re = /class="result__snippet"[^>]*>([\s\S]*?)<\/a>/g, m;
      while ((m = re.exec(h)) && out.length < 6) out.push(m[1].replace(/<[^>]+>/g, '').replace(/&amp;/g, '&').replace(/&quot;/g, '"').replace(/&#x27;/g, "'").replace(/\s+/g, ' ').trim());
      return out;
    });
  }
  var PRON = { er: 1, sie: 1, es: 1, ihn: 1, ihm: 1, ihr: 1, dort: 1, da: 1, dem: 1, davon: 1, darueber: 1, dieser: 1, diese: 1, dieses: 1, it: 1, he: 1, she: 1, they: 1, there: 1, that: 1, this: 1, him: 1, her: 1 };
  function knowledge(raw, t) {
    // Folgefrage zum letzten Thema ohne neues Stichwort: im selben Artikel nachsehen
    var rest = content(fix(t)).filter(function (w) { return !PRON[w] && !{ hoch: 1, lang: 1, alt: 1, gross: 1, weit: 1, viele: 1, gebaut: 1, gegruendet: 1, high: 1, tall: 1, long: 1, old: 1, built: 1, founded: 1, liegt: 1, wohnen: 1, leben: 1 }[w]; });
    if (M.article && !rest.length && t.split(' ').some(function (w) { return PRON[w]; })) {
      var r0 = rank(M.article.sents, t), b0 = r0.filter(function (x) { return M.article.used.indexOf(x.s) < 0 || x.sc >= 4; })[0] || r0[0];
      M.article.used.push(b0.s);
      return Promise.resolve(b0.s + ' 📚');
    }
    var cw = content(fix(t));
    // Suchbegriff aus den Originalwoertern (mit Umlauten und ß), ohne Fragewoerter
    var QW = { hoch: 1, lang: 1, gross: 1, alt: 1, weit: 1, viele: 1, gegruendet: 0, high: 1, tall: 1, long: 1, old: 1, big: 1, many: 1 };
    var orig = String(raw).replace(/[?!.,;:„“"()]/g, ' ').split(/\s+/).filter(function (w) { var n = norm(w); return n.length > 2 && !SW[n] && !QW[n]; });
    var query = orig.join(' ') || cw.join(' ') || raw;
    var lang = wikiLang();
    return wikiSearch(query, lang).then(function (titles) {
      if (!titles.length && lang !== 'en') return wikiSearch(query, 'en').then(function (t2) { lang = 'en'; return t2; });
      return titles;
    }).then(function (titles) {
      if (!titles.length) throw new Error('nothing');
      // Titel bevorzugen, der die Suchwoerter enthaelt
      var best = titles[0], bs = -1;
      titles.forEach(function (ti) { var n = norm(ti), s = cw.filter(function (w) { return n.indexOf(w) >= 0; }).length; if (s > bs) { bs = s; best = ti; } });
      return wikiText(best, lang);
    }).then(function (a) {
      if (!a) throw new Error('nothing');
      var ss = sentences(a.text); if (!ss.length) throw new Error('nothing');
      var r = rank(ss, t), first = ss[0], top = r[0];
      var pickS = qtype(t) === 'what' || qtype(t) === 'who' ? [first].concat(top.s !== first ? [top.s] : []) : [top.s].concat(top.s !== first && r[0].sc < 5 ? [first] : []);
      M.article = { title: a.title, sents: ss, used: pickS.slice(), lang: lang }; M.lastTopic = a.title;
      if (/^(Er|Sie|Es|Dieser|Diese|Dieses|Dort|It|He|She|They)\b/.test(pickS[0]) && pickS[0] !== first) pickS = [first, pickS[0]];
      var txt = pickS.slice(0, 2).join(' ');
      if (lang === 'en' && L === 'de') txt = 'Auf Deutsch habe ich nichts gefunden, hier aus der englischen Wikipedia: ' + txt;
      return txt + ' 📚\n\n' + T('Quelle: Wikipedia (' + a.title + '). Sag „mehr“, wenn du mehr wissen willst.', 'Source: Wikipedia (' + a.title + '). Say "more" to learn more.');
    }).catch(function () {
      return ddg(query).then(function (sn) {
        if (!sn.length) throw new Error('nothing');
        var r = rank(sn.map(function (x) { return x.split(/(?<=[.!?])\s+/)[0]; }), t);
        return r[0].s + ' 🔎\n\n' + T('(aus einer Websuche)', '(from a web search)');
      });
    }).catch(function () {
      return pick([T('Hm, dazu habe ich gerade nichts Verlässliches gefunden 🤔 Magst du die Frage anders stellen?', 'Hm, I could not find anything reliable on that 🤔 Could you ask it differently?'),
        T('Da muss ich passen, dazu finde ich nichts Sicheres 🙈 Frag mich gern etwas zum Radfahren, da bin ich richtig gut!', 'I have to pass on that one 🙈 Ask me anything about cycling, that is my thing!')]);
    });
  }

  // ---------------------------------------------------------------------------------------------
  // Antworten pro Absicht
  // ---------------------------------------------------------------------------------------------
  function R(text, extra) { var o = { antwort: text, aktionen: [], orte: [] }; if (extra) for (var k in extra) o[k] = extra[k]; return Promise.resolve(o); }
  var JOKES = {
    de: ['Warum fahren Fahrräder nie alleine? Weil sie zwei-reifig sind! 🚲😄', 'Was sagt ein Fahrrad zum anderen? „Reifen wir mal zusammen ab!“ 😂', 'Mein Fahrrad und ich haben eine feste Beziehung. Es ist immer für mich da, nur beim Bergauffahren lässt es mich hängen 😅', 'Warum ist das Fahrrad umgefallen? Es war zwei-müde! 😴🚲', 'Ich wollte einen Witz über Kettenschaltungen erzählen, aber ich komme einfach nicht in den richtigen Gang 🤭',
      'Was ist das Lieblingsessen von Radfahrern? Speichen-Spaghetti! 🍝😄', 'Warum hat der Radfahrer seinen Wecker auf das Rad gelegt? Er wollte eine Zeitfahrt machen! ⏰🚴',
      'Treffen sich zwei Fahrräder. Sagt das eine: „Du siehst aber platt aus!“ Sagt das andere: „Ich hatte einen anstrengenden Tag, ich bin völlig durch… den Schlauch!“ 😂',
      'Wie nennt man einen Fuchs auf dem Rennrad? Einen Turbo-Fux! 🦊💨', 'Warum gehen Fahrräder nie in die Schule? Weil sie schon genug Speichen-Wissen haben! 🤓',
      'Mein Rad hat mir gesagt, ich soll mehr trainieren. Ich glaube, es wollte mich nur antreiben 😄', 'Was sagt der Berg zum Radfahrer? „Komm doch rauf, wenn du dich traust!“ ⛰️😏'],
    en: ['Why can\'t a bicycle stand up by itself? Because it\'s two-tired! 🚲😄', 'I wanted to tell a joke about gears, but I couldn\'t shift into the right mood 🤭', 'What do you call a cyclist who doesn\'t like hills? Flat-out honest 😂', 'Why did the bicycle go to the doctor? It was feeling a bit run-down 😄', 'I tried to make a bike joke but it fell flat… like my tyre 🛞😂']
  };
  function chain(t, k) {
    var name = userName(), hr = new Date().getHours(), d = L === 'de';
    var I = intentCache;
    switch (I) {
      case 'greet': case 'greetq':
        var cc = userCountry(), hi = cc === 'AT' ? ['Servus', 'Griaß di', 'Hallo'] : cc === 'CH' ? ['Grüezi', 'Hoi', 'Hallo'] : cc === 'DE' ? ['Hallo', 'Moin', 'Hey'] : ['Hallo', 'Hey', 'Hi'];
        var h1 = hr >= 5 && hr < 11 ? 'Guten Morgen' : hr >= 18 && hr < 23 ? 'Guten Abend' : pickNR(hi, 'hi'), nm = name ? ' ' + name : '';
        if (hr >= 23 || hr < 5) h1 = pickNR([h1, 'Na, noch wach', 'Hey, Nachteule'], 'night');
        var wx = k && k.wetter, wtxt = '';
        if (wx && wx.temp_c != null) wtxt = wx.regenrisiko_prozent >= 50 ? ' Draußen sieht es nach Regen aus 🌧️, eher ein Tag für Planung oder eine kurze Runde mit Jacke.' : wx.temp_c >= 12 ? ' Draußen hat es ' + wx.temp_c + ' °C, ' + (wx.wind_kmh >= 30 ? 'etwas windig, aber' : 'richtig') + ' gutes Radwetter ☀️' : ' Draußen hat es frische ' + wx.temp_c + ' °C, also warm anziehen 🧣';
        return R(d ? pickNR([h1 + nm + '! 👋' + wtxt + ' Was darf es heute sein?', h1 + nm + '! 😊 Schön, dass du da bist.' + wtxt + ' Wobei kann ich helfen?', h1 + nm + '! 🦊' + (wtxt || ' Lust auf eine Runde?')], 'greet')
          : pickNR(['Hi' + nm + '! 👋 What can I do for you today?', 'Hey' + nm + '! 😊 Ready to ride?', (hr < 11 ? 'Good morning' : hr >= 18 ? 'Good evening' : 'Hello') + nm + '! 🦊 How can I help?'], 'greet'));
      case 'howru':
        return R(pickNR(d ? ['Mir geht\'s super, danke! 😄 Ich hab heute schon Routen in Gedanken geplant. Und dir? Lust auf eine Runde? 🚴', 'Bestens! ☀️ Wie geht\'s dir denn? Fährst du heute noch?', 'Gut, danke der Nachfrage! 😊 Wie war dein Tag bisher?']
          : ['Great, thanks! 😄 And you? Fancy a ride today? 🚴', 'All good here! ☀️ How about you?']));
      case 'who':
        return R(d ? 'Ich bin **Soul**, die KI von Velora 🦊✨ Ich laufe direkt hier in der App, ohne fremde KI. Ich plane Routen und Touren, kenne das Wetter, finde schöne Orte in der Nähe, werte deine Fahrten aus und recherchiere für dich, wenn du etwas wissen willst.'
          : 'I\'m **Soul**, Velora\'s AI 🦊✨ I run right here in the app, no external AI. I plan routes and tours, know the weather, find nice places nearby, analyse your rides and look things up for you.');
      case 'can':
        return R(d ? 'Ich kann dir helfen bei:\n- 🗺️ **Routen**: „Plan mir eine Route nach Melk“, danach „flacher“ oder „kürzer“\n- 🔄 **Touren**: „Schlag mir eine 2-Stunden-Tour vor“\n- 📍 **In der Nähe**: „Wo gibt es hier Pizza?“, „Aussichtspunkte in der Nähe“\n- ☀️ **Wetter**: „Regnet es morgen?“, „Was soll ich anziehen?“\n- 📊 **Deine Fahrten**: „Wie war meine Woche?“, „Wie viele Kalorien?“\n- 🔧 **Rad-Hilfe**: „Wie flicke ich einen Reifen?“\n- 📚 **Wissen**: „Wie hoch ist der Großglockner?“, danach „Und der Mont Blanc?“\n- 🧠 **Ich merke mir dich**: „Ich heiße …“, „Meine Arbeit ist in …“\n- 🎲 **Spaß**: Witze, Rätsel, Quiz, Geschichten, Gedichte, Fun Facts\n- 😄 Und einfach plaudern!'
          : 'I can help with:\n- 🗺️ **Routes**: "Plan a route to Vienna", then "flatter" or "shorter"\n- 🔄 **Tours**: "Suggest a 2-hour tour"\n- 📍 **Nearby**: "Where can I get pizza?"\n- ☀️ **Weather**: "Will it rain tomorrow?"\n- 📊 **Your rides**: "How was my week?"\n- 🔧 **Bike help**: "How do I fix a flat?"\n- 📚 **Knowledge**: "How high is Mont Blanc?"\n- 😄 And just chatting!');
      case 'thanks':
        return R(pickNR(d ? ['Sehr gern! 😊', 'Immer wieder gern! 🦊', 'Gerne doch! Gute Fahrt 🚴💨', 'Dafür bin ich da! ✨'] : ['You\'re welcome! 😊', 'Anytime! 🦊', 'Happy to help! Enjoy the ride 🚴💨']));
      case 'bye':
        return R(pickNR(d ? ['Bis bald' + (name ? ', ' + name : '') + '! 👋 Gute Fahrt!', 'Tschüss! 🦊 Melde dich, wenn du eine Tour brauchst.', hr >= 20 ? 'Gute Nacht! 🌙 Morgen wartet die nächste Runde.' : 'Bis später! 🚴'] : ['See you' + (name ? ', ' + name : '') + '! 👋 Ride safe!', 'Bye! 🦊']));
      case 'joke': return R(pickNR(JOKES[d ? 'de' : 'en'], 'joke') + T(' Noch einer? 😄', ' Another one? 😄'), { _offer: 'joke' });
      case 'compliment': return R(pick(d ? ['Oh, danke! 🥰 Das geht runter wie ein frisch geöltes Kettenblatt.', 'Du bist aber auch toll! 😊 Zusammen sind wir ein starkes Team 🚴🦊', 'Danke! ✨ Das motiviert mich richtig.'] : ['Aww, thank you! 🥰', 'You\'re great too! 😊 We make a good team 🚴🦊']));
      case 'insult': return R(pick(d ? ['Autsch 😅 Ich gebe mein Bestes und lerne dazu. Sag mir, was nicht gepasst hat, dann versuche ich es nochmal.', 'Okay, das war wohl nichts 🙈 Wie kann ich es besser machen?'] : ['Ouch 😅 I\'m doing my best. Tell me what went wrong and I\'ll try again.']));
      case 'lowmood':
        return R(pick(d ? ['Kenn ich 😊 Weißt du was? Schon 20 Minuten auf dem Rad machen den Kopf frei. Soll ich dir eine kurze, flache Runde raussuchen? 🌿', 'Keine Sorge, jeder hat solche Tage 💛 Wie wäre es mit einer gemütlichen Runde zu einem Café? ☕ Ich such dir eine raus, wenn du willst.']
          : ['I get it 😊 Even 20 minutes on the bike clear your head. Want me to find a short, flat loop? 🌿']), { _offer: 'tour_easy' });
      case 'rode': return R(T('Wie cool! 🎉 Wie war es? Wenn du die Fahrt mit Velora aufgezeichnet hast, sag „Wie war meine letzte Fahrt?“ und ich werte sie dir aus 📊', 'Nice! 🎉 How was it? Ask "How was my last ride?" and I\'ll analyse it 📊'));
      case 'goodmood': return R(pick(d ? ['Wie cool! 🎉 Das freut mich richtig.', 'Stark! 💪 Weiter so!', 'Yes! 🔥 So muss das sein.'] : ['How cool! 🎉', 'Awesome! 💪 Keep it up!']));
      case 'age': return R(d ? 'Ich bin noch ganz frisch 🦊 Aber ich lerne jeden Tag dazu!' : 'I\'m brand new 🦊 but I learn every day!');
      case 'time': var now = new Date(); return R(d ? 'Es ist ' + V.hhmm(now) + ' Uhr, ' + now.toLocaleDateString('de-DE', { weekday: 'long', day: 'numeric', month: 'long' }) + ' 🕒' : 'It\'s ' + V.hhmm(now) + ', ' + now.toLocaleDateString('en-GB', { weekday: 'long', day: 'numeric', month: 'long' }) + ' 🕒');
    }
    return null;
  }
  var intentCache = null;

  function doMath(t) {
    var m = t.match(/(\d+(?:[.,]\d+)?)\s*(?:%|prozent|percent)\s*(?:von|of)\s*(\d+(?:[.,]\d+)?)/);
    if (m) return Math.round(parseFloat(m[1].replace(',', '.')) * parseFloat(m[2].replace(',', '.'))) / 100;
    m = t.match(/(\d+(?:[.,]\d+)?)\s*(km|kilometer|meilen|miles|mi|kmh|km\/h|ms|m\/s)\s*(?:in|to|=)\s*(km|kilometer|meilen|miles|mi|kmh|km\/h|ms|m\/s)/);
    if (m) {
      var x = parseFloat(m[1].replace(',', '.')), f = m[2], to = m[3], isKm = /^(km|kilometer)$/, isMi = /^(meilen|miles|mi)$/, isKmh = /^(kmh|km\/h)$/, isMs = /^(ms|m\/s)$/;
      if (isKm.test(f) && isMi.test(to)) return Math.round(x / 1.609344 * 100) / 100;
      if (isMi.test(f) && isKm.test(to)) return Math.round(x * 1.609344 * 100) / 100;
      if (isKmh.test(f) && isMs.test(to)) return Math.round(x / 3.6 * 100) / 100;
      if (isMs.test(f) && isKmh.test(to)) return Math.round(x * 3.6 * 100) / 100;
    }
    var e = t.replace(/(was ist|rechne|berechne|what is|calculate|=|\?)/g, ' ').replace(/geteilt durch|durch|divided by|:/g, '/').replace(/\bmal\b|\btimes\b|x/g, '*').replace(/\bplus\b/g, '+').replace(/\bminus\b/g, '-').replace(/,/g, '.').replace(/\s+/g, '');
    if (!/^[\d+\-*/().%^]+$/.test(e)) return null;
    try { var v = Function('"use strict";return (' + e.replace(/\^/g, '**') + ')')(); if (typeof v === 'number' && isFinite(v)) return Math.round(v * 1e6) / 1e6; } catch (x) {}
    return null;
  }

  function doRoute(raw, t, k) {
    var tg = targetOf(raw) || {};
    if (tg.there && M.place) tg = { place: M.place };
    var PK = { arbeit: 'arbeit', firma: 'arbeit', buero: 'arbeit', job: 'arbeit', work: 'arbeit', office: 'arbeit', schule: 'schule', school: 'schule', uni: 'uni', universitaet: 'uni', zuhause: 'zuhause', home: 'zuhause' };
    var pk = tg.home ? 'zuhause' : tg.q ? PK[norm(tg.q).replace(/^(meiner|meinem|meine|mein|my) /, '')] : null;
    if (pk) {
      var adr = MEM.places[pk] || (pk === 'zuhause' ? MEM.ort : null);
      if (!adr) return R(T('Wo ist ' + { arbeit: 'deine Arbeit', schule: 'deine Schule', uni: 'deine Uni', zuhause: 'dein Zuhause' }[pk] + '? 🤔 Sag mir zum Beispiel „' + { arbeit: 'Meine Arbeit ist in der Hauptstraße 5 in Graz', schule: 'Meine Schule ist in der Schulgasse 3 in Linz', uni: 'Meine Uni ist die TU Wien', zuhause: 'Mein Zuhause ist in der Lindengasse 7 in Wien' }[pk] + '“, dann merke ich es mir für immer 😊', 'Where is it? 🤔 Tell me e.g. "My work is at 5 Main Street, Graz" and I\'ll remember it 😊'));
      tg = { q: adr };
    }
    var style = /\b(schnell|fast|direkt|kuerzeste|quick)\b/.test(t) ? 'schnell' : 'schoen';
    var go = function (p) {
      M.place = p;
      var a = { typ: 'route_planen', lat: p.lat, lon: p.lon, ziel_name: p.name, stil: style };
      var h = here(k), dd = h ? V.dist(h.lat, h.lon, p.lat, p.lon) : null;
      var txt = T('Alles klar, ich plane dir die Route nach **' + p.name + '** 🗺️' + (dd ? ' (Luftlinie ' + km(dd) + ' km)' : '') + '. ' + (style === 'schoen' ? 'Ich nehme die schönere Strecke mit Radwegen und ruhigen Wegen 🌿' : 'Ich nehme die schnelle Variante ⚡') + ' Danach kannst du „flacher“, „kürzer“ oder „schöner“ sagen.',
        'Sure, planning your route to **' + p.name + '** 🗺️' + (dd ? ' (' + kmE(dd) + ' km as the crow flies)' : '') + '. You can then say "flatter", "shorter" or "nicer".');
      return { antwort: txt, aktionen: [a], orte: [] };
    };
    if (tg.place) return Promise.resolve(go(tg.place));
    if (!tg.q) return R(T('Wohin soll es gehen? 🚴 Sag mir einfach das Ziel, zum Beispiel „Route nach Krems“.', 'Where do you want to go? 🚴 Just tell me, e.g. "route to Salzburg".'));
    // erst in der letzten Liste suchen ("zum ersten", Name aus der Liste)
    var li = M.list.filter(function (o) { return norm(o.name).indexOf(norm(tg.q)) >= 0; })[0];
    if (li) return Promise.resolve(go(li));
    return geocode(tg.q, k).then(go).catch(function () { return { antwort: T('Den Ort „' + tg.q + '“ finde ich leider nicht 🤔 Magst du ihn genauer schreiben, zum Beispiel mit Stadt?', 'I can\'t find "' + tg.q + '" 🤔 Could you be more specific?'), aktionen: [], orte: [] }; });
  }

  function doAdjust(t, k) {
    var w = /\b(kuerzer|shorter|nicht so (lang|weit)|zu (lang|weit))\b/.test(t) ? 'kuerzer' : /\b(flacher|leichter|einfacher|weniger|ohne|steil|flatter|easier|less)\b/.test(t) ? 'flacher' : /\b(schoener|nicer)\b/.test(t) ? 'schoener' : /\b(schneller|faster)\b/.test(t) ? 'schneller' : 'laenger';
    if (!k.route) return R(T('Gerade ist keine Route offen 🗺️ Sag mir zuerst ein Ziel oder lass dir eine Tour vorschlagen.', 'There\'s no route open yet 🗺️ Tell me a destination first.'));
    var N = { kuerzer: ['kürzer', 'shorter'], flacher: ['flacher', 'flatter'], schoener: ['schöner', 'nicer'], schneller: ['schneller', 'faster'], laenger: ['länger', 'longer'] }[w];
    return R(T('Okay, ich mache die Route ' + N[0] + ' 🔄 Bisher: ' + String(k.route.km).replace('.', ',') + ' km mit ' + k.route.bergauf_m + ' Höhenmetern. Gleich siehst du die neue Variante.', 'Okay, making the route ' + N[1] + ' 🔄 Currently ' + k.route.km + ' km with ' + k.route.bergauf_m + ' m climbing.'), { aktionen: [{ typ: 'route_anpassen', wunsch: w }] });
  }

  function doTour(t, k) {
    var dur = durationOf(t), dist = distanceOf(t);
    if (!dur && dist) dur = Math.round(dist / 16 * 60);
    var diff = /\b(leicht|einfach|gemuetlich|flach|locker|easy|flat|relaxed)\b/.test(t) ? 'leicht' : /\b(schwer|anstrengend|sportlich|knackig|hart|berg|bergig|hard|tough|hilly)\b/.test(t) ? 'schwer' : /\b(mittel|medium)\b/.test(t) ? 'mittel' : null;
    var beau = /\b(schoen|schoenste|traumhaft|landschaft|aussicht|natur|beautiful|scenic|nice)\b/.test(t) ? 'sehr_schoen' : null;
    if (!dur && !diff && !beau && M.offer !== 'tour_easy') {
      M.offer = 'tour_ask';
      return R(T('Gern! 🔄 Wie lange willst du ungefähr fahren? Zum Beispiel „1 Stunde“, „2 Stunden locker“ oder „halber Tag, sportlich“.', 'Sure! 🔄 How long do you want to ride? E.g. "1 hour", "2 hours easy" or "half a day, hard".'));
    }
    var a = { typ: 'tour_vorschlagen', dauer_min: dur || 90 };
    if (diff) a.schwierigkeit = diff; if (beau) a.schoenheit = beau;
    var d = dur || 90;
    return R(T('Ich suche dir eine ' + (d >= 60 ? (d / 60).toString().replace('.', ',') + '-Stunden' : d + '-Minuten') + '-Runde' + (diff ? ', ' + (diff === 'leicht' ? 'schön locker' : diff === 'schwer' ? 'mit ordentlich Bergen 💪' : 'mittelschwer') : '') + (beau ? ', besonders schön 🌄' : '') + ' ab deinem Standort. Gleich siehst du die Vorschläge 🦊',
      'Looking for a ' + (d >= 60 ? d / 60 + '-hour' : d + '-minute') + ' loop' + (diff ? ' (' + diff + ')' : '') + ' from where you are 🦊'), { aktionen: [a] });
  }

  function overpassNear(cat, h, r) {
    var q = '[out:json][timeout:20];nwr[' + OSMTAG[cat] + ']["name"](around:' + r + ',' + h.lat.toFixed(5) + ',' + h.lon.toFixed(5) + ');out center 40;';
    return fetch('https://overpass-api.de/api/interpreter', { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: 'data=' + encodeURIComponent(q) }).then(function (x) { return x.json(); })
      .then(function (j) { return (j.elements || []).map(function (e) { var la = e.lat != null ? e.lat : e.center && e.center.lat, lo = e.lon != null ? e.lon : e.center && e.center.lon; return la == null ? null : { name: e.tags['name:' + L] || e.tags.name, lat: la, lon: lo, d: V.dist(h.lat, h.lon, la, lo), cu: e.tags.cuisine, oh: e.tags.opening_hours }; }).filter(Boolean); });
  }
  function doNearby(t, k) {
    var cat = catOf(t) || 'food', h = here(k);
    if (!h) return R(T('Dafür brauche ich deinen Standort 📍 Schalte bitte GPS ein.', 'I need your location for that 📍 Please turn on GPS.'));
    var keys = KCAT[cat];
    var list = (k.orte_in_der_naehe || []).filter(function (o) { var c = norm(o.kategorie || ''); return keys.some(function (x) { return c.indexOf(x) >= 0; }); })
      .map(function (o) { return { name: o.name, lat: o.lat, lon: o.lon, d: o.km * 1000, cu: o.kueche, open: o.jetzt_offen }; });
    var p = list.length >= 3 ? Promise.resolve(list) : overpassNear(cat, h, 3000).then(function (l) { return l.length ? l : overpassNear(cat, h, 8000); }).catch(function () { return list; });
    return p.then(function (l) {
      if (/\bpizza\b/.test(t)) l = l.filter(function (o) { return /pizza/i.test((o.cu || '') + o.name); }).concat(l.filter(function (o) { return !/pizza/i.test((o.cu || '') + o.name); }));
      else l.sort(function (a, b) { return a.d - b.d; });
      l = l.filter(function (o, i, a) { return o.name && a.findIndex(function (x) { return x.name === o.name; }) === i; }).slice(0, 5);
      if (!l.length) return { antwort: T('In deiner Nähe finde ich gerade keine ' + CAT_N[cat][0] + ' 😕 Soll ich etwas weiter weg suchen? Schreib zum Beispiel den Ort dazu.', 'I can\'t find any ' + CAT_N[cat][1] + ' nearby 😕'), aktionen: [], orte: [] };
      M.list = l; M.place = l[0]; M.offer = 'route_first';
      var lines = l.map(function (o, i) { return (i + 1) + '. **' + o.name + '**, ' + (o.d < 1000 ? Math.round(o.d / 10) * 10 + ' m' : km(o.d) + ' km') + (o.cu ? ' (' + String(o.cu).replace(/;/g, ', ') + ')' : '') + (o.open === true ? T(', jetzt offen', ', open now') : o.open === false ? T(', gerade zu', ', closed now') : ''); });
      return { antwort: T(CAT_EMO[cat] + ' ' + cap(CAT_N[cat][0]) + ' in deiner Nähe:\n' + lines.join('\n') + '\n\nSoll ich dich zu **' + l[0].name + '** bringen? 🚴', CAT_EMO[cat] + ' ' + cap(CAT_N[cat][1]) + ' near you:\n' + lines.join('\n') + '\n\nShall I take you to **' + l[0].name + '**? 🚴'),
        aktionen: [], orte: l.map(function (o) { return { name: o.name, lat: o.lat, lon: o.lon }; }) };
    });
  }

  function doWeather(t, k, wear) {
    var h = here(k); if (!h) { var c = V.map.getCenter(); h = { lat: c.lat, lon: c.lng }; }
    var day = /\buebermorgen|day after tomorrow\b/.test(t) ? 2 : /\b(morgen|tomorrow)\b/.test(t) && !/\bheute morgen\b/.test(t) ? 1 : /\b(wochenende|weekend)\b/.test(t) ? 'we' : 0;
    return weather(h).then(function (w) {
      var D = w.daily, cu = w.current, i = day === 'we' ? Math.max(1, (6 - new Date().getDay() + 7) % 7) : day;
      if (i > 3) i = 3;
      var tmax = Math.round(D.temperature_2m_max[i]), tmin = Math.round(D.temperature_2m_min[i]), pr = D.precipitation_probability_max[i], wind = Math.round(D.wind_speed_10m_max[i]), code = D.weather_code[i];
      var good = pr < 40 && wind < 35 && tmax >= 8 && code < 61;
      var dayN = T(['heute', 'morgen', 'übermorgen', 'in drei Tagen'][i], ['today', 'tomorrow', 'the day after tomorrow', 'in three days'][i]);
      if (wear) {
        var tt = i === 0 ? Math.round(cu.temperature_2m) : tmax, list = [];
        if (tt < 5) list = T(['Winterjacke oder Thermojacke', 'Handschuhe und Mütze unter dem Helm', 'lange Hose'], ['winter jacket', 'gloves and a cap under the helmet', 'long trousers']);
        else if (tt < 12) list = T(['warme Jacke', 'leichte Handschuhe', 'lange Hose'], ['warm jacket', 'light gloves', 'long trousers']);
        else if (tt < 18) list = T(['Langarmshirt oder leichte Jacke', 'eine Weste zum Wegpacken'], ['long sleeves or a light jacket', 'a packable vest']);
        else list = T(['T-Shirt und kurze Hose', 'Sonnencreme und Sonnenbrille 😎', 'genug Wasser'], ['t-shirt and shorts', 'sunscreen and sunglasses 😎', 'enough water']);
        if (pr >= 40) list.push(T('Regenjacke 🌧️', 'rain jacket 🌧️'));
        if (wind >= 30) list.push(T('etwas Winddichtes 💨', 'something windproof 💨'));
        return { antwort: T('Bei ' + tt + ' °C ' + dayN + ' würde ich anziehen: ' + list.join(', ') + '. 🧥', 'At ' + tt + ' °C ' + dayN + ' I\'d wear: ' + list.join(', ') + '. 🧥'), aktionen: [], orte: [] };
      }
      var emo = code >= 95 ? '⛈️' : code >= 71 && code < 80 ? '❄️' : code >= 51 ? '🌧️' : code >= 3 ? '☁️' : code >= 1 ? '🌤️' : '☀️';
      var txt;
      if (i === 0) txt = T(emo + ' Gerade ' + Math.round(cu.temperature_2m) + ' °C, ' + wcode(cu.weather_code) + ', Wind ' + Math.round(cu.wind_speed_10m) + ' km/h. Heute bis ' + tmax + ' °C, Regenrisiko ' + pr + ' %.', emo + ' Right now ' + Math.round(cu.temperature_2m) + ' °C, ' + wcode(cu.weather_code) + ', wind ' + Math.round(cu.wind_speed_10m) + ' km/h. Today up to ' + tmax + ' °C, ' + pr + ' % chance of rain.');
      else txt = T(emo + ' ' + cap(dayN) + ': ' + wcode(code) + ', ' + tmin + ' bis ' + tmax + ' °C, Regenrisiko ' + pr + ' %, Wind bis ' + wind + ' km/h.', emo + ' ' + cap(dayN) + ': ' + wcode(code) + ', ' + tmin + ' to ' + tmax + ' °C, ' + pr + ' % chance of rain, wind up to ' + wind + ' km/h.');
      if (/\b(regen|regnet|regnen|rain)\b/.test(t)) txt = (pr >= 50 ? T('Ja, ' + dayN + ' sieht es nach Regen aus 🌧️ (' + pr + ' %). ', 'Yes, rain is likely ' + dayN + ' 🌧️ (' + pr + ' %). ') : pr >= 25 ? T('Vielleicht, das Regenrisiko liegt ' + dayN + ' bei ' + pr + ' % 🌦️. ', 'Maybe, ' + pr + ' % chance ' + dayN + ' 🌦️. ') : T('Eher nicht, nur ' + pr + ' % Regenrisiko ' + dayN + ' ☀️. ', 'Probably not, only ' + pr + ' % ' + dayN + ' ☀️. ')) + txt;
      txt += good ? T(' Perfektes Radwetter! 🚴', ' Perfect cycling weather! 🚴') : pr >= 60 ? T(' Eher ein Tag für drinnen, oder mit Regenjacke 😉', ' Better pack a rain jacket 😉') : wind >= 35 ? T(' Achtung, ganz schön windig 💨', ' Watch out, quite windy 💨') : '';
      return { antwort: txt, aktionen: [], orte: [] };
    }).catch(function () { return { antwort: T('Das Wetter kann ich gerade nicht abrufen 📡 Hast du Internet?', 'I can\'t get the weather right now 📡'), aktionen: [], orte: [] }; });
  }

  function doLive(t, k) {
    var n = k.navigation, txt = [];
    if (/\b(wie schnell|tempo|how fast)\b/.test(t)) txt.push(k.tempo_kmh != null ? T('Du fährst gerade ' + k.tempo_kmh + ' km/h' + (k.schnitt_kmh ? ', im Schnitt ' + String(k.schnitt_kmh).replace('.', ',') + ' km/h' : '') + ' 💨', 'You\'re doing ' + k.tempo_kmh + ' km/h' + (k.schnitt_kmh ? ', average ' + k.schnitt_kmh : '') + ' 💨') : T('Gerade messe ich kein Tempo, bist du in Bewegung? 🤔', 'I can\'t measure speed right now 🤔'));
    if (/\b(hoch|hoehe|high)\b/.test(t)) txt.push(k.hoehe_m != null ? T('Du bist auf ' + k.hoehe_m + ' m über dem Meer ⛰️', 'You\'re at ' + k.hoehe_m + ' m above sea level ⛰️') : T('Die Höhe kenne ich gerade nicht.', 'I don\'t know your altitude right now.'));
    if (/\b(weit|wann|lange|ankunft|km noch|kilometer noch|far|arrive|eta|left)\b/.test(t) || !txt.length) {
      if (n && n.aktiv) txt.push(n.angekommen ? T('Du bist angekommen! 🎉', 'You\'ve arrived! 🎉') : T('Noch ' + String(n.rest_km).replace('.', ',') + ' km, etwa ' + n.rest_min + ' Minuten. Ankunft gegen ' + n.ankunft + ' Uhr 🏁', String(n.rest_km) + ' km to go, about ' + n.rest_min + ' min. ETA ' + n.ankunft + ' 🏁'));
      else txt.push(T('Gerade läuft keine Navigation. Sag mir ein Ziel und ich führe dich hin 🗺️', 'No navigation running. Tell me a destination 🗺️'));
    }
    if (/\b(anstieg|steigung|berg|climb)\b/.test(t) && n && n.naechster_anstieg) txt.push(T('Der nächste Anstieg kommt in ' + n.naechster_anstieg.in_m + ' m: ' + n.naechster_anstieg.laenge_m + ' m mit ' + n.naechster_anstieg.prozent + ' % 💪', 'Next climb in ' + n.naechster_anstieg.in_m + ' m: ' + n.naechster_anstieg.laenge_m + ' m at ' + n.naechster_anstieg.prozent + ' % 💪'));
    if (/\b(abbiegung|anweisung|turn)\b/.test(t) && n) txt.push(T('Als Nächstes: ' + n.naechste_anweisung + ' ➡️', 'Next: ' + n.naechste_anweisung + ' ➡️'));
    return R(txt.join('\n'));
  }

  function doStats(t) {
    var s = VX.statsData ? VX.statsData() : null;
    if (!s || !s.n) return R(T('Du hast noch keine Fahrt aufgezeichnet 🚴 Starte eine Navigation oder „Fahrt starten“, dann kann ich dir alles auswerten!', 'No rides recorded yet 🚴 Start one and I can analyse everything!'));
    var st = s.streak, F = function (n) { return n + T(n === 1 ? ' Fahrt' : ' Fahrten', n === 1 ? ' ride' : ' rides'); }, kmS = function (m) { return T(km(m), kmE(m)); };
    if (/\b(serie|flamme|streak)\b/.test(t)) return R(st.cur ? T('🔥 Deine Flamme brennt seit **' + st.cur + (st.cur === 1 ? ' Woche' : ' Wochen') + '**! Diese Woche: ' + st.week + ' von ' + F(st.target) + '. ' + (st.done ? 'Wochenziel schon geschafft 💪' : 'Noch ' + F(st.left) + ' bis Sonntag.') + ' Deine Bestmarke: ' + st.best + (st.best === 1 ? ' Woche.' : ' Wochen.'), '🔥 Your streak: **' + st.cur + ' weeks**! This week ' + st.week + '/' + st.target + ' rides.') : T('Gerade brennt keine Flamme 🕯️ Schaff ' + F(st.target) + ' diese Woche, dann zünde ich sie an! (Diese Woche: ' + st.week + ')', 'No streak right now 🕯️ Do ' + st.target + ' rides this week!'));
    if (/\b(abzeichen|badges?)\b/.test(t)) return R(T('🏅 Du hast ' + s.badges.got + ' von ' + s.badges.all + ' Abzeichen-Stufen. ' + (s.badges.next.length ? 'Am nächsten dran: **' + s.badges.next[0].name + '** (' + s.badges.next[0].text + ', ' + s.badges.next[0].pct + ' %).' : ''), '🏅 You have ' + s.badges.got + ' of ' + s.badges.all + ' badge levels.'));
    if (/\b(rekord|rekorde|bestzeit|bestzeiten|record)\b/.test(t)) return R(s.bests.length ? T('⏱️ Deine stärksten Anstiege:\n' + s.bests.map(function (b) { return '- ' + b.name + ': ' + Math.floor(b.t / 60) + ':' + ('0' + Math.round(b.t % 60)).slice(-2) + ' min'; }).join('\n'), '⏱️ Your best climbs:\n' + s.bests.map(function (b) { return '- ' + b.name + ': ' + Math.round(b.t / 60) + ' min'; }).join('\n')) : T('Noch keine Bestzeiten 😊 Fahr einen Anstieg hoch, dann messe ich deine Zeit (Plus).', 'No records yet 😊'));
    if (/\b(kalorien|calories|kcal)\b/.test(t)) return R(T('🔥 Diese Woche hast du rund **' + s.week.kcal + ' kcal** verbrannt' + (s.last ? ', bei der letzten Fahrt etwa ' + s.last.kcal + ' kcal' : '') + '. Das sind ungefähr ' + Math.max(1, Math.round(s.week.kcal / 250)) + ' Stück Kuchen 🍰😄', '🔥 This week about **' + s.week.kcal + ' kcal** burned. That\'s ~' + Math.max(1, Math.round(s.week.kcal / 250)) + ' slices of cake 🍰😄'));
    if (/\b(letzte|last|zuletzt)\b/.test(t) && s.last) {
      var l = s.last, v = l.mv ? l.d / l.mv * 3.6 : 0, p = s.prev, pv = p && p.mv ? p.d / p.mv * 3.6 : null;
      return R(T('🚴 Deine letzte Fahrt (' + new Date(l.t).toLocaleDateString('de-DE', { weekday: 'long' }) + '): **' + km(l.d) + ' km** in ' + Math.round(l.mv / 60) + ' Minuten, ' + Math.round(l.asc) + ' Höhenmeter, Schnitt ' + v.toFixed(1).replace('.', ',') + ' km/h' + (l.max ? ', Spitze ' + Math.round(l.max) + ' km/h' : '') + ', etwa ' + l.kcal + ' kcal.' + (pv ? (v > pv * 1.03 ? ' Schneller als davor 📈 Stark!' : v < pv * 0.97 ? ' Etwas gemütlicher als davor, auch gut 😊' : ' Ungefähr so schnell wie davor 👍') : ''),
        '🚴 Your last ride: **' + kmE(l.d) + ' km** in ' + Math.round(l.mv / 60) + ' min, ' + Math.round(l.asc) + ' m climbing, avg ' + v.toFixed(1) + ' km/h, ~' + l.kcal + ' kcal.'));
    }
    if (/\b(monat|month)\b/.test(t)) { var df = s.lastMonth.d ? Math.round((s.month.d - s.lastMonth.d) / s.lastMonth.d * 100) : null; return R(T('📅 Diesen Monat: **' + km(s.month.d) + ' km** in ' + F(s.month.n) + ', ' + Math.round(s.month.asc) + ' Höhenmeter.' + (df != null ? ' Letzten Monat waren es ' + km(s.lastMonth.d) + ' km (' + (df >= 0 ? '+' : '') + df + ' %).' : ''), '📅 This month: **' + kmE(s.month.d) + ' km** in ' + s.month.n + ' rides.')); }
    if (/\b(insgesamt|gesamt|alle|total|overall|jahr|year)\b/.test(t)) return R(T('🌍 Insgesamt bist du **' + km(s.all.d) + ' km** gefahren, in ' + F(s.all.n) + ' mit ' + Math.round(s.all.asc) + ' Höhenmetern. Dieses Jahr: ' + km(s.year.d) + ' km. Längste Fahrt: ' + (s.longest ? km(s.longest.d) : '0') + ' km 💪', '🌍 In total **' + kmE(s.all.d) + ' km** in ' + s.all.n + ' rides.'));
    var w = s.week, lw = s.lastWeek, diff = lw.d ? Math.round((w.d - lw.d) / lw.d * 100) : null;
    var verdict = !w.n ? T('Diese Woche bist du noch nicht gefahren, wie wäre es mit einer kleinen Runde? 😊', 'No rides this week yet, fancy a short loop? 😊') : diff == null ? T('Guter Start! 💪', 'Good start! 💪') : diff >= 20 ? T('Deutlich mehr als letzte Woche, richtig stark! 🔥', 'Way more than last week, awesome! 🔥') : diff >= 0 ? T('Etwas mehr als letzte Woche 📈', 'A bit more than last week 📈') : T('Etwas weniger als letzte Woche, da geht noch was 😉', 'A bit less than last week 😉');
    return R(T('📊 Diese Woche: **' + km(w.d) + ' km** in ' + F(w.n) + ', ' + Math.round(w.asc) + ' Höhenmeter, rund ' + w.kcal + ' kcal.' + (lw.n ? ' Letzte Woche: ' + km(lw.d) + ' km.' : '') + ' ' + verdict + (st.cur ? '\n🔥 Serie: ' + st.cur + (st.cur === 1 ? ' Woche' : ' Wochen') + '.' : ''),
      '📊 This week: **' + kmE(w.d) + ' km** in ' + w.n + ' rides, ' + Math.round(w.asc) + ' m climbing, ~' + w.kcal + ' kcal. ' + verdict));
  }

  var BIKE = {
    de: [
      [/\b(platten|platter|flicken|schlauch|loch|puncture|flat)\b/, '🔧 **Platten flicken:**\n1. Laufrad ausbauen, Luft ganz rauslassen.\n2. Reifen mit zwei Reifenhebern an einer Seite von der Felge ziehen, Schlauch rausnehmen.\n3. Schlauch aufpumpen und das Loch suchen (Zischen hören oder im Wasser Blasen sehen).\n4. Stelle anrauen, Vulkanisierflüssigkeit drauf, kurz trocknen lassen, Flicken fest andrücken.\n5. Reifen innen mit der Hand abtasten (steckt noch ein Splitter?), Schlauch rein, Reifen aufziehen, aufpumpen.\nTipp: Ein Ersatzschlauch ist unterwegs oft schneller als Flicken 😉'],
      [/\b(luftdruck|reifendruck|pressure|aufpumpen)\b/, '🛞 **Reifendruck** steht immer auf der Reifenflanke (z. B. „3,5–5 bar“). Faustregel: Rennrad 6–8 bar, City/Trekking 3,5–5 bar, Mountainbike 1,8–2,5 bar, Gravel 2,5–3,5 bar. Schwerere Fahrer etwas mehr, auf Schotter etwas weniger. Einmal pro Woche prüfen!'],
      [/\b(kette|kettenoel|oelen|schmieren|chain)\b/, '⛓️ **Kette pflegen:** Mit einem Lappen abwischen, dann auf jedes Kettenglied einen kleinen Tropfen Kettenöl geben, die Kurbel ein paar Mal rückwärts drehen und überschüssiges Öl abwischen. Je nach Wetter alle 200–300 km. Quietscht sie, ist es höchste Zeit 😄'],
      [/\b(schaltung|gaenge|gang springt|gears|schalten)\b/, '⚙️ **Schaltung springt?** Oft ist der Schaltzug ein bisschen gelängt. Am Schaltwerk oder Schalthebel sitzt eine kleine Einstellschraube: Springt die Kette schlecht auf größere Ritzel, die Schraube eine halbe Umdrehung gegen den Uhrzeigersinn drehen und testen. Hilft das nicht, kurz in eine Werkstatt, die richten das in fünf Minuten 🔧'],
      [/\b(bremse|bremsen|quietscht|brake)\b/, '🛑 **Bremsen:** Quietschen kommt meist von Schmutz oder Öl. Felgenbremse: Felge und Beläge mit Alkohol reinigen. Scheibenbremse: Scheibe mit Bremsenreiniger säubern, niemals Öl drauf! Sind die Beläge dünner als ca. 1 mm, unbedingt tauschen. Bremsen sind das Wichtigste am Rad, im Zweifel lieber in die Werkstatt.'],
      [/\b(sattel|sattelhoehe|saddle)\b/, '🪑 **Sattelhöhe:** Setz dich drauf und stell die Ferse aufs Pedal ganz unten: Das Bein sollte dann gerade sein. Beim normalen Treten (Fußballen auf dem Pedal) bleibt das Knie leicht gebeugt. Zu tief belastet die Knie, zu hoch lässt die Hüfte wackeln.'],
      [/\b(licht|beleuchtung|light)\b/, '💡 **Licht:** Vorne weiß, hinten rot, dazu Reflektoren an Pedalen und Speichen. In der Dämmerung und nachts immer einschalten, auch auf beleuchteten Straßen. Akku-Lampen vor längeren Abendtouren laden 🔋'],
      [/./, '🔧 Bei Rad-Problemen helfe ich gern! Sag mir genauer, was los ist: Platten, Reifendruck, Kette, Schaltung, Bremsen, Sattel oder Licht?']
    ],
    en: [
      [/\b(flat|puncture|tube|patch)\b/, '🔧 **Fixing a flat:** remove the wheel, lever one side of the tyre off, pull out the tube, find the hole (listen or use water), roughen, glue, wait, patch. Check the inside of the tyre for thorns, refit and pump up. A spare tube is often quicker on the road 😉'],
      [/\b(pressure|pump)\b/, '🛞 Tyre pressure is printed on the sidewall. Roughly: road 6–8 bar, city 3.5–5, MTB 1.8–2.5, gravel 2.5–3.5. Check weekly!'],
      [/\b(chain|lube|oil)\b/, '⛓️ Wipe the chain, one drop of oil per link, spin backwards, wipe off the excess. Every 200–300 km.'],
      [/./, '🔧 Happy to help! Is it a flat, tyre pressure, chain, gears, brakes, saddle or lights?']
    ]
  };
  function doBike(t) { var l = BIKE[L === 'de' ? 'de' : 'en']; for (var i = 0; i < l.length; i++) if (l[i][0].test(t)) return R(l[i][1]); }
  function doTraining(t) {
    return R(T(/\b(berg|bergauf|climb)\b/.test(t) ? '⛰️ **Besser bergauf:** Früh in einen leichten Gang schalten, gleichmäßig treten statt mit Kraft (ca. 70–90 Umdrehungen pro Minute), im Sitzen bleiben und ruhig atmen. Trainiere einmal pro Woche einen Anstieg in deiner Nähe mehrmals hintereinander. Die Bestzeiten an Anstiegen zeige ich dir in der Statistik 💪'
      : /\b(abnehmen|lose weight)\b/.test(t) ? '⚖️ **Abnehmen mit dem Rad:** Am besten mehrere lockere Fahrten pro Woche von 45–90 Minuten, bei denen du dich noch unterhalten könntest. Das verbrennt viel Fett. Deine Kalorien siehst du in der Statistik. Und: genug trinken 💧'
      : '💪 **Schneller und fitter werden:**\n- 2–3 Fahrten pro Woche, die meisten locker (unterhalten geht noch)\n- einmal pro Woche Intervalle: 4 × 4 Minuten zügig, dazwischen 3 Minuten locker\n- leichte Gänge und hohe Trittfrequenz (80–90/min)\n- Ruhetage gehören dazu 😴\nMit deinem Wochenziel und der Flamme bleibst du dran 🔥',
      '💪 **Get faster:** 2–3 rides a week, mostly easy; once a week intervals (4 × 4 min hard, 3 min easy); spin light gears (80–90 rpm); rest days matter 😴'));
  }
  function doSafety() { return R(T('🪖 **Sicher unterwegs:**\n- Helm tragen, auch auf kurzen Strecken\n- Licht vorne weiß, hinten rot, bei Dämmerung immer an\n- Handzeichen beim Abbiegen, Blickkontakt mit Autofahrern\n- Abstand zu parkenden Autos (Türen!)\n- Bei Live-Standort teilen sieht deine Familie, wo du bist 📍', '🪖 **Stay safe:** wear a helmet, lights on at dusk, hand signals, keep clear of parked car doors, and share your live location 📍'));}
  var HELP = [
    [/\b(serie|flamme|wochenziel|streak)\b/, ['🔥 Die **Serie** zählt Wochen: Du wählst, wie oft du pro Woche fahren willst (1–4 Mal). Schaffst du das bis Sonntag, brennt die Flamme eine Woche weiter. Ändern kannst du das Ziel in der Statistik.', '🔥 The **streak** counts weeks: pick 1–4 rides per week; reach it by Sunday and the flame keeps burning.']],
    [/\b(plus|premium|abo|preis)\b/, ['✨ **Velora Plus** kostet 2,99 € im Monat oder 19,99 € im Jahr. Dazu gehören Reiseführer, Tagesplaner, Gravel- und Familienprofil, E-Bike-Akkuplaner, Regenwarnung, Bestzeiten, GPX, mehr Offline-Karten, Farben und Pfeile. Mich gibt es für alle kostenlos 🦊', '✨ **Velora Plus**: €2.99/month or €19.99/year. I\'m free for everyone 🦊']],
    [/\b(offline)\b/, ['📴 **Offline-Karten:** Menü → Offline-Karten → Karte auf das Gebiet schieben → speichern. Gratis ein Gebiet, mit Plus beliebig viele.', '📴 Menu → Offline maps → move the map → save.']],
    [/\b(live)\b/, ['📍 **Live teilen:** Während einer Navigation unten auf „Live-Standort teilen“ tippen und den Link schicken. Er endet automatisch mit der Fahrt.', '📍 During navigation tap "Share live location" and send the link.']],
    [/\b(gpx)\b/, ['📁 **GPX:** In jeder Route gibt es „GPX“ zum Exportieren, unter Menü → Meine Routen kannst du GPX-Dateien importieren (Plus).', '📁 Export via "GPX" in a route, import under Menu → My routes (Plus).']],
    [/\b(tagesplaner)\b/, ['🗓️ Der **Tagesplaner** (Plus) baut dir einen ganzen Tag mit Aussicht, Mittagessen und Baden als eine Runde mit Uhrzeiten: Menü → Tagesplaner.', '🗓️ Menu → Day planner (Plus).']],
    [/\b(abzeichen)\b/, ['🏅 Es gibt 18 Abzeichen-Familien mit bis zu 10 Stufen, von Bronze bis Legende. Tippe in der Statistik auf ein Abzeichen, dann siehst du, was dir zur nächsten Stufe fehlt.', '🏅 18 badge families with up to 10 levels. Tap one in the stats to see your progress.']],
    [/\b(widget)\b/, ['📱 Das **Widget**: Startbildschirm lange drücken → Widgets → Velora. Es zeigt deine Wochenkilometer und die Serie.', '📱 Long-press the home screen → Widgets → Velora.']],
    [/\b(velo|fuchs)\b/, ['🦊 **Velo** ist dein Fuchs-Maskottchen. Er feiert deine Fahrten und zündet die Flamme an. Ich bin Soul, die KI. Wir sind ein Team 😄', '🦊 **Velo** is the fox mascot who celebrates your rides. I\'m Soul, the AI. We\'re a team 😄']],
    [/\b(rueckenwind)\b/, ['💨 Bei Tour-Vorschlägen kannst du „Rückenwind auf dem Heimweg“ einschalten. Dann lege ich die Runde so, dass dich der Wind zurückschiebt.', '💨 Turn on "Tailwind on the way home" in tour suggestions.']],
    [/./, ['Frag mich ruhig genauer, zum Beispiel „Wie funktioniert die Serie?“ oder „Was kann Plus?“ 😊', 'Ask me more specifically, e.g. "How does the streak work?" 😊']]
  ];
  function doHelp(t) { for (var i = 0; i < HELP.length; i++) if (HELP[i][0].test(t)) return R(T(HELP[i][1][0], HELP[i][1][1])); }

  // Spezielle Inhalte aus dem Wissenspaket
  function special(tag) {
    var d = L === 'de';
    if (tag === '__fact__') { var f = pickNR(SD.facts, 'fact'); return R((d ? '💡 Wusstest du? ' + f[0] : '💡 Did you know? ' + f[1]) + T(' Willst du noch einen?', ' Want another one?'), { _offer: 'fact' }); }
    if (tag === '__riddle__') { var r = pickNR(SD.riddles, 'riddle'); M.riddle = r; M.riddleT = M.turns; return R('🧩 ' + r[0] + T(' (Rate mal! Oder sag „Lösung“.)', ' (Take a guess! Or say "answer".)')); }
    if (tag === '__quiz__') { var q = pickNR(SD.quiz, 'quiz'); M.quiz = q; M.quizT = M.turns; return R('🎯 **Quiz:** ' + q[0] + '\n' + q[1].map(function (o, i) { return (i + 1) + ') ' + o; }).join('\n') + T('\nAntworte mit 1, 2 oder 3!', '\nAnswer 1, 2 or 3!')); }
    if (tag === '__story__') {
      var S = SD.story; if (!S) return R('📖');
      return R('📖 Es war einmal ' + pick(S.who) + '. Eines Tages ging die Fahrt ' + pick(S.where) + '. Alles war wunderbar, doch ' + pick(S.problem) + '. ' + pick(S.solve) + ', ' + pick(S.end) + T('\n\nSoll ich noch eine erzählen? 😊', ''), { _offer: 'story' });
    }
    if (tag === '__poem__') return R(pickNR(SD.poems, 'poem'));
    return null;
  }
  // Statements ohne klare Absicht: auf den Inhalt eingehen statt Floskeln
  function chat(raw, t) {
    var tg = targetOf(raw);
    if (tg && tg.q && /\b(fahre|fahren|radle|radeln|will|moechte|wuerde gern|going|want)\b/.test(t)) { M.offer = 'route_to:' + tg.q; return R(T('Klingt nach einem Plan! 🚴 Soll ich dir eine Route nach **' + tg.q + '** planen?', 'Sounds like a plan! 🚴 Shall I plan a route to **' + tg.q + '**?')); }
    if (/\b(hunger|hungrig|durst)\b/.test(t)) return doNearby('essen', window.__ugljan.kontext());
    var pos = /\b(gut|super|toll|schoen|geil|cool|genial|perfekt|happy|froh|freu|great|good|nice|awesome|love)\w*/.test(t), neg = /\b(schlecht|doof|bloed|nervig|kaputt|traurig|schade|mist|schlimm|scheiss|krank|verletzt|gestorben|streit|angst|sorgen?|anstrengend|stressig|muehsam|nervt|bad|sad|terrible|annoying|sick|exhausting)\w*/.test(t);
    var d = L === 'de';
    // Orte und Personen nach Praepositionen ("am Neusiedler See", "mit Anna"); deutsche Hauptwoerter allein sind keine Namen
    var PREP = /\b(am|an der|an den|im|in|nach|zum|zur|beim|bei|auf dem|auf der|aus|to|at)\s+((?:[A-ZÄÖÜ][\wäöüß-]+)(?:\s+[A-ZÄÖÜ][\wäöüß-]+){0,2})/;
    var pm = raw.match(PREP), per = raw.match(/\bmit\s+([A-ZÄÖÜ][a-zäöüß]+)\b(?!\s+[A-ZÄÖÜ])/);
    var past = /\b(war|waren|gewesen|hatte|gestern|i was|we were)\b/.test(t);
    if (pm && past) { var pr = /^(am|im|beim|zum|zur)$/i.test(pm[1]) ? pm[1] + ' ' : 'in '; return R(pickNR(d ? ['Wie schön! 😊 Wie war es ' + pr + pm[2] + '?' + (per ? ' Mit ' + per[1] + ' zusammen klingt nach einem richtig tollen Tag!' : '') + ' Seid ihr mit dem Rad hin? 🚴', 'Oh, ' + pm[2] + '! 😍 Erzähl, was habt ihr dort gemacht?'] : ['Nice! 😊 How was ' + pm[2] + '?'], 'past')); }
    if (neg) return R(pickNR(d ? ['Oh nein, das tut mir leid 💛 Magst du mir mehr erzählen?', 'Das klingt nicht schön 😕 Was ist passiert?', 'Oje 😣 Kann ich dir irgendwie helfen?'] : ['Oh no, I\'m sorry 💛 Want to tell me more?', 'That doesn\'t sound good 😕 What happened?'], 'neg'));
    if (pm && !past) { M.offer = 'route_to:' + pm[2]; return R(pickNR(d ? [pm[2] + '? Soll ich dir eine Route dorthin planen oder etwas darüber herausfinden? 🦊', 'Oh, ' + pm[2] + '! Willst du mit dem Rad hin? Ich plane dir gern die Strecke 🚴'] : [pm[2] + '? Want me to plan a route there? 🦊'], 'place')); }
    if (per) return R(pickNR(d ? ['Mit ' + per[1] + '? Wie schön 😊 Erzähl mehr!', 'Grüß ' + per[1] + ' von mir! 😄'] : ['With ' + per[1] + '? Nice 😊'], 'person'));
    var names = [];
    if (neg) return R(pickNR(d ? ['Oh, das klingt nicht so toll 😕 Magst du mir mehr erzählen?', 'Hm, das tut mir leid 💛 Was ist passiert?', 'Das ist ärgerlich 😣 Kann ich dir irgendwie helfen?'] : ['Oh, that doesn\'t sound great 😕 Want to tell me more?', 'Sorry to hear that 💛 What happened?'], 'neg'));
    if (pos) return R(pickNR(d ? ['Das klingt richtig gut! 😄 Erzähl mehr!', 'Wie schön! 🎉 Das freut mich.', 'Cool! 😊 Und wie geht es weiter?'] : ['That sounds great! 😄 Tell me more!', 'How nice! 🎉'], 'pos'));
    if (names.length) return R(pickNR(d ? ['Spannend, ' + names[0] + '! 🤔 Was genau interessiert dich daran? Ich kann auch nachschauen, was ich darüber finde.', names[0] + '? Erzähl mir mehr darüber 😊', 'Oh, ' + names[0] + '! Soll ich dir eine Route dorthin planen oder etwas darüber recherchieren? 🦊'] : ['Interesting, ' + names[0] + '! 🤔 Want me to look it up or plan a route there?'], 'names'), names[0].length > 2 ? { _offer: 'route_to:' + names[0] } : null);
    if (/\b(ich|mir|mich|mein)\b/.test(t)) return R(pickNR(d ? ['Verstehe 😊 Und wie fühlst du dich dabei?', 'Okay! Erzähl ruhig weiter, ich höre zu 🦊', 'Aha, spannend 🤔 Was hast du als Nächstes vor?'] : ['I see 😊 And how do you feel about it?', 'Okay! Go on, I\'m listening 🦊'], 'me'));
    return R(pickNR(T(['Hm, da bin ich mir nicht sicher, was du meinst 🤔 Kannst du es anders sagen? Oder frag mich nach Touren, Wetter, Orten oder deinen Fahrten.', 'Das habe ich nicht ganz verstanden 🙈 Meinst du eine Frage? Dann stell sie mir ruhig direkt.', 'Okay! 👍 Wenn du eine Frage hast, frag einfach drauf los, ich versuche mein Bestes.'],
      ['Hm, I\'m not sure what you mean 🤔 Could you rephrase it?', 'I didn\'t quite get that 🙈 Is it a question? Just ask me directly.']), 'fallback'));
  }
  // Gedaechtnis: Aussagen ueber dich erkennen und speichern
  function memorize(raw, t) {
    var m, d = L === 'de', said = null;
    if ((m = raw.match(/(?:ich hei(?:ß|ss)e|mein name ist|nenn mich|du kannst mich .* nennen|my name is|call me)\s+([A-Za-zÄÖÜäöüß\-]{2,20})/i)) && !/^(soul|du|dich)$/i.test(m[1])) { MEM.name = cap(m[1]); said = T('Freut mich, ' + MEM.name + '! 😊 Ich merke mir deinen Namen.', 'Nice to meet you, ' + MEM.name + '! 😊'); }
    else if ((m = raw.match(/(?:ich wohne in|ich lebe in|ich komme aus|i live in|i am from|i'm from)\s+([^,.!?]{2,40})/i))) { MEM.ort = m[1].trim(); said = T('Ah, ' + MEM.ort + '! 🏡 Merk ich mir. Soll ich dir mal schöne Touren in der Gegend suchen?', 'Ah, ' + MEM.ort + '! 🏡 Noted.'); }
    else if ((m = raw.match(/\b(?:meine?|my)\s+(arbeit|firma|schule|uni|universit[äa]t|b[üu]ro|wohnung|zuhause|work|office|school|home)\s+(?:ist|liegt|is)\s+(?:in der |in |am |an der |auf der |bei |at |in the |die |der |das )?([^!?]{3,80})/i))) {
      if (!/^(in der|in|am|an der|auf der|bei|at|in the)\s/i.test(raw.slice(raw.toLowerCase().indexOf(m[2].toLowerCase()) - 8)) && !/\d|stra(ß|ss)e|gasse|weg|platz|allee|ring|street|road|avenue|tu |uni /i.test(m[2]) && !/ (in der|in|am|an der|bei|at) $/i.test(raw.slice(0, raw.toLowerCase().indexOf(m[2].toLowerCase())))) m = null;
    }
    if (said == null && m && m[2] && /\b(?:meine?|my)\s+(arbeit|firma|schule|uni|universit|b[üu]ro|wohnung|zuhause|work|office|school|home)/i.test(raw)) {
      var key = { arbeit: 'arbeit', firma: 'arbeit', 'büro': 'arbeit', buero: 'arbeit', work: 'arbeit', office: 'arbeit', schule: 'schule', school: 'schule', uni: 'uni', 'universität': 'uni', universitaet: 'uni', wohnung: 'zuhause', zuhause: 'zuhause', home: 'zuhause' }[m[1].toLowerCase()] || 'zuhause';
      MEM.places[key] = m[2].trim().replace(/[.]$/, ''); said = T('Alles klar, gemerkt! 📍 Sag einfach „Route zur ' + ({ arbeit: 'Arbeit', schule: 'Schule', uni: 'Uni', zuhause: 'Hause' }[key]) + '“, dann bringe ich dich hin.', 'Got it! 📍 Just say "route to ' + key + '".');
    }
    else if ((m = raw.match(/(?:ich bin|i am|i'm)\s+(\d{1,2})\s*(?:jahre|years)/i))) { MEM.alter = +m[1]; said = T(m[1] + ' Jahre, cool! 😊', m[1] + ', cool! 😊'); }
    else if ((m = raw.match(/(?:ich (?:habe|fahre) (?:ein|einen|eine)|mein (?:fahrrad|rad) ist (?:ein|eine)|i (?:have|ride) an?)\s+((?:[a-zäöüß-]+\s){0,2}(?:rennrad|gravelbike|gravel|mountainbike|mtb|e-?bike|pedelec|trekkingrad|cityrad|citybike|stadtrad|hollandrad|klapprad|lastenrad|fahrrad|bike))\b/i))) { MEM.rad = m[1].trim(); said = T('Ein ' + MEM.rad + ', sehr schön! 🚲 Merk ich mir.', 'A ' + MEM.rad + ', nice! 🚲'); }
    else if ((m = raw.match(/(?:ich mag|ich liebe|mein lieblings\w* ist|i like|i love)\s+([^,.!?]{2,40})/i)) && !/^(dich|dir|you|es|das|soul)\b/i.test(m[1])) { var v = m[1].trim(); if (MEM.mag.indexOf(v) < 0) MEM.mag.push(v); MEM.mag = MEM.mag.slice(-12); said = T(pickNR(['Oh, ' + v + '! 😊 Gemerkt.', v + ' finde ich auch super! 💚', 'Schön, dass du ' + v + ' magst! Ich merke es mir 🦊'], 'like'), 'Nice, ' + v + '! 😊 Noted.'); }
    if (said) { saveMem(); return R(said); }
    if (/\b(was weisst du (ueber|von) (mir|mich)|was hast du dir (ueber mich )?gemerkt|kennst du mich|was merkst du dir|what do you know about me)\b/.test(t)) {
      var f = [];
      if (userName()) f.push(T('du heißt **' + userName() + '**', 'your name is **' + userName() + '**'));
      if (MEM.ort) f.push(T('du wohnst in ' + MEM.ort, 'you live in ' + MEM.ort));
      if (MEM.alter) f.push(T('du bist ' + MEM.alter + ' Jahre alt', 'you are ' + MEM.alter));
      if (MEM.rad) f.push(T('du fährst ein ' + MEM.rad, 'you ride a ' + MEM.rad));
      Object.keys(MEM.places).forEach(function (k2) { f.push(T(({ arbeit: 'deine Arbeit', schule: 'deine Schule', uni: 'deine Uni', zuhause: 'dein Zuhause' }[k2] || k2) + ' ist ' + MEM.places[k2], 'your ' + k2 + ' is ' + MEM.places[k2])); });
      if (MEM.mag.length) f.push(T('du magst ' + MEM.mag.slice(-4).join(', '), 'you like ' + MEM.mag.slice(-4).join(', ')));
      var s2 = VX.statsData && VX.statsData(); if (s2 && s2.n) f.push(T('du bist schon ' + km(s2.all.d) + ' km mit Velora gefahren', 'you have ridden ' + kmE(s2.all.d) + ' km with Velora'));
      return R(f.length ? T('🧠 Ich weiß: ' + f.join(', ') + '. Wenn ich etwas vergessen soll, sag „Vergiss das“.', '🧠 I know: ' + f.join(', ') + '.') : T('Noch nicht viel 😊 Erzähl mir doch etwas über dich, zum Beispiel wie du heißt oder wo du wohnst!', 'Not much yet 😊 Tell me about yourself!'));
    }
    if (/\b(wie heisse ich|weisst du wie ich heisse|whats my name|what is my name)\b/.test(t)) return R(userName() ? T('Du heißt ' + userName() + '! 😊', 'Your name is ' + userName() + '! 😊') : T('Das weiß ich noch nicht 🙈 Wie heißt du denn?', 'I don\'t know yet 🙈 What\'s your name?'));
    if (/\b(wo wohne ich|where do i live)\b/.test(t)) return R(MEM.ort ? T('In ' + MEM.ort + ' 🏡', 'In ' + MEM.ort + ' 🏡') : T('Das hast du mir noch nicht verraten 😊', 'You haven\'t told me yet 😊'));
    if (/\b(vergiss (das|alles|mich)|loesch (das|alles)|forget (that|everything))\b/.test(t)) { MEM = { name: null, ort: null, mag: [], rad: null, alter: null, places: {} }; saveMem(); return R(T('Okay, ich habe alles vergessen, was du mir über dich erzählt hast 🧹', 'Okay, I forgot everything you told me 🧹')); }
    return null;
  }
  // Meinung: "Was haeltst du von X?", "Magst du X?"
  function opinion(raw, t) {
    if (!/\b(was haeltst du von|was denkst du (ueber|von)|wie findest du|magst du|was ist deine meinung|what do you think (of|about)|do you like)\b/.test(t)) return null;
    var t2 = t.replace(/-/g, '');
    for (var i = 0; i < SD.opinions.length; i++) if (SD.opinions[i][0].test(t2) || SD.opinions[i][0].test(t)) return R(SD.opinions[i][1]);
    var x = raw.replace(/.*(h(?:ä|ae|a)lt(?:e)?st du von|denkst du (?:über|ueber|von)|findest du|magst du|deine meinung (?:zu|über|ueber)|think (?:of|about)|do you like)\s*/i, '').replace(/[?!.]+$/, '').trim();
    if (!x) return null;
    return R(pickNR(T(['Zu ' + x + ' habe ich keine feste Meinung, ich bin ja eine KI 😊 Aber erzähl mir, wie du das siehst!', 'Hm, ' + x + '? Ich finde es spannend, was Menschen daran mögen. Was gefällt dir daran? 🤔', 'Gute Frage! Ich bin da neutral, aber neugierig: Was denkst du über ' + x + '? 🦊'], ['I don\'t have a strong opinion on ' + x + ' 😊 What do you think?']), 'opinion'));
  }

  // ---------------------------------------------------------------------------------------------
  // Echtes Sprachmodell (soul-llm.js): Gespraech, Wissen und Rad-Fragen. App-Aktionen bleiben hier.
  // ---------------------------------------------------------------------------------------------
  var LLM_I = /^(chat|knowledge|training|safety|bike|lowmood|goodmood|greet|greetq|howru|who|can|thanks|bye|compliment|insult|rode|age|smalltalk)$/;
  var HIST = [];
  function withTimeout(p, ms) { return Promise.race([p, new Promise(function (r) { setTimeout(function () { r(null); }, ms); })]); }
  function needSearch(raw, t) {
    if (/\b(such|suche|google|googel|im internet|online nach|recherchier|look up|search)\b/.test(t)) return true;
    if (/\b(aktuell|aktuelle|heute|neueste|neuesten|neu|news|nachrichten|preis|kostet|ergebnis|gewonnen|weltmeister|2025|2026|2027|latest|current|price)\b/.test(t) && !/\b(wetter|regen)\b/.test(t)) return true;
    var ty = qtype(t); if (ty !== 'what') return true;
    // Eigennamen (gross geschrieben, nicht am Satzanfang): Personen, Orte, Firmen
    var w = String(raw).replace(/^\s*\S+/, '').match(/\b[A-ZÄÖÜ][a-zäöüß]{2,}/g) || [];
    return w.filter(function (x) { return !/^(Ich|Du|Sie|Er|Es|Wir|Ihr|Rad|Fahrrad|Was|Wie|Wo|Wer|Warum|Kannst|Velora|Soul)$/.test(x) && !SW[norm(x)]; }).length > 0 && /\b(wer|was ist|was sind|was war|erzaehl|weisst du|kennst du)\b/.test(t);
  }
  function research(raw, t) {
    var cw = content(fix(t)), lang = wikiLang();
    var QW = { hoch: 1, lang: 1, gross: 1, alt: 1, weit: 1, viele: 1, high: 1, tall: 1, long: 1, old: 1, big: 1, many: 1, such: 1, suche: 1, google: 1, internet: 1 };
    var orig = String(raw).replace(/[?!.,;:„“"()]/g, ' ').split(/\s+/).filter(function (w) { var n = norm(w); return n.length > 2 && !SW[n] && !QW[n] && !PRON[n]; });
    var query = orig.join(' ') || cw.join(' ') || raw;
    // Folgefrage mit "er/sie/es": letztes Thema dazunehmen
    if (M.lastTopic && (t.split(' ').some(function (w) { return PRON[w]; }) || /^und\b/.test(t))) query = M.lastTopic + ' ' + orig.filter(function (w) { return !/^(und|wurde|wurden|ersten|erste|mal|zum)$/i.test(w); }).join(' ');
    var wiki = wikiSearch(query, lang).then(function (titles) {
      if (!titles.length) throw new Error('none');
      var best = titles[0], bs = -1;
      titles.forEach(function (ti) { var n = norm(ti), sc = cw.filter(function (w) { return n.indexOf(w) >= 0; }).length; if (sc > bs) { bs = sc; best = ti; } });
      return wikiText(best, lang);
    }).then(function (a) {
      if (!a) return null;
      var ss = sentences(a.text); if (!ss.length) return null;
      var r = rank(ss, t), pickS = ss.slice(0, 3);
      r.slice(0, 4).forEach(function (x) { if (pickS.indexOf(x.s) < 0) pickS.push(x.s); });
      M.lastTopic = a.title;
      return { title: a.title, text: pickS.join(' ') };
    }).catch(function () { return null; });
    var web = (A && A.fetchText) ? ddg(query).then(function (sn) { return sn.slice(0, 4).join(' | '); }).catch(function () { return ''; }) : Promise.resolve('');
    return Promise.all([withTimeout(wiki, 7000), withTimeout(web, 7000)]).then(function (x) {
      var w = x[0], d = x[1];
      if (!w && !d) return null;
      var txt = (w ? w.text : '') + (d ? (w ? ' || Web: ' : '') + d : '');
      return { text: txt, src: w ? 'Wikipedia: ' + w.title + (d ? ', Websuche' : '') : 'Websuche', title: w ? w.title : null };
    });
  }
  // Nur Hintergrund, der zur Nachricht passt (sonst redet das Modell ungefragt ueber Wetter und Ort)
  function ctxLine(k, t) {
    var p = [], now = new Date();
    var ride = /\b(rad|fahr|fahrt|tour|runde|draussen|raus|heute|morgen|wetter|warm|kalt|regen|wind|anziehen|lust|ride|bike|outside|weather)\w*/.test(t);
    p.push('Jetzt: ' + now.toLocaleDateString('de-DE', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' }) + ', ' + String(now.getHours()).padStart(2, '0') + ':' + String(now.getMinutes()).padStart(2, '0') + ' Uhr');
    if (k.position && k.position.ort && (ride || /\b(hier|wo bin|gegend|naehe|stadt|ort|here|area)\b/.test(t))) p.push('Ort: ' + k.position.ort);
    if (k.wetter && ride) p.push('Wetter jetzt: ' + k.wetter.temp_c + ' °C, ' + (k.wetter.lage || '') + ', Wind ' + k.wetter.wind_kmh + ' km/h');
    if (k.rad && /\b(rad|fahrrad|bike|reifen|kette|schaltung|bremse|sattel|ebike|akku)\w*/.test(t)) p.push('Rad in der App: ' + k.rad);
    var me = [];
    if (userName()) me.push('heißt ' + userName());
    if (MEM.alter) me.push(MEM.alter + ' Jahre');
    if (MEM.ort) me.push('wohnt in ' + MEM.ort);
    if (MEM.rad) me.push('fährt ' + MEM.rad);
    if (MEM.mag && MEM.mag.length) me.push('mag ' + MEM.mag.slice(-4).join(', '));
    if (me.length) p.push('Die Person, mit der du sprichst (nicht du selbst): ' + me.join(', '));
    if (/\b(ich|mein|meine|mir|my|i)\b/.test(t) && /\b(fahrt|fahrten|gefahren|km|kilometer|woche|training|fit|stats)\w*/.test(t)) try { var s2 = VX.statsData && VX.statsData(); if (s2 && s2.n) p.push('Fahrten mit Velora: ' + s2.n + ', zusammen ' + km(s2.all.d) + ' km'); } catch (e) {}
    if (k.route && /\b(route|strecke|weg|anstieg|steil|lang|weit)\w*/.test(t)) p.push('Offene Route: ' + k.route.titel + ', ' + k.route.km + ' km, ' + k.route.bergauf_m + ' Hm');
    if (k.navigation && k.navigation.aktiv) p.push('Navigation läuft, noch ' + k.navigation.rest_km + ' km');
    return p.join('; ');
  }
  function llmPath(raw, t, I, k, onPart) {
    var refs = [];
    var fs = bestOf(SD.faq, t, { strong: true }), fq = fs || (/^(chat|knowledge|bike|training)$/.test(I) ? bestOf(SD.faq, t, { coverage: 0.2 }) : null);
    // Kleines Modell: gepruefte Anleitungen aus dem Velora-Wissen direkt zeigen (das Modell formuliert sie sonst fehlerhaft um)
    if (SoulLLM.model() === 'std' && /^(bike|training|safety)$/.test(I)) {
      var cur = fs ? R(L === 'de' ? fs.e.a : fs.e.e) : I === 'bike' ? doBike(t) : I === 'training' ? doTraining(t) : doSafety();
      if (cur) return Promise.resolve(cur).then(function (o) { if (!o || !o.antwort) throw new Error('none'); return o; });
    }
    if (fq) refs.push(L === 'de' ? fq.e.a : fq.e.e);
    var pre = null;
    if (!fq && I === 'bike') pre = doBike(t); else if (!fq && I === 'training') pre = doTraining(t); else if (I === 'safety') pre = doSafety();
    var srch = null;
    return Promise.resolve(pre).then(function (o) {
      if (o && o.antwort) refs.push(o.antwort);
      return (I === 'knowledge' || I === 'chat') && !fq && needSearch(raw, t) ? research(raw, t) : null;
    }).then(function (sr) {
      if (sr) M.lastSearch = { sr: sr, turn: M.turns };
      else if (M.lastSearch && M.turns - M.lastSearch.turn <= 3 && (/^und\b/.test(t) || t.split(' ').some(function (w) { return PRON[w]; }))) sr = M.lastSearch.sr;
      srch = sr;
      var long = /\b(erklaer|erklaere|wie funktioniert|anleitung|schritt|schritte|tipps|liste|unterschied|vergleich|warum|explain|how does|steps|tips|difference|why)\b/.test(t);
      return SoulLLM.answer({ q: raw, ctx: ctxLine(k, t), refs: refs, search: sr, hist: HIST, long: long }, onPart);
    }).then(function (txt) {
      if (srch && srch.title) txt += '\n\n' + T('Quelle: Wikipedia (' + srch.title + ')', 'Source: Wikipedia (' + srch.title + ')');
      else if (srch) txt += '\n\n' + T('(mit Websuche)', '(with web search)');
      return { antwort: txt, aktionen: [], orte: [], llm: true };
    });
  }

  // ---------------------------------------------------------------------------------------------
  // Einstieg
  // ---------------------------------------------------------------------------------------------
  // Verlauf fuers Sprachmodell (auch Antworten der App-Werkzeuge, damit Folgefragen passen)
  function ask(raw, k, onPart) {
    return ask0(raw, k, onPart).then(function (o) { if (o && o.antwort) { HIST.push({ u: String(raw), a: String(o.antwort) }); if (HIST.length > 8) HIST.shift(); } return o; });
  }
  function ask0(raw, k, onPart) {
    k = k || (window.__ugljan && __ugljan.kontext ? __ugljan.kontext() : {});
    L = langOf(raw);
    var t = fix(norm(raw)), I = detect(raw, t);
    M.turns++;
    // Folgefragen und Bestaetigungen
    if (/^(mehr|weiter|erzaehl mehr|und weiter|more|tell me more|go on)\b/.test(t) && M.article) {
      var rest = M.article.sents.filter(function (s) { return M.article.used.indexOf(s) < 0; }).slice(0, 2);
      if (rest.length) { M.article.used = M.article.used.concat(rest); return finish(R(rest.join(' ') + ' 📖'), 'more'); }
    }
    var ord = t.match(/\b(das |der |die |zum |zur |zu dem )?(erste|ersten|zweite|zweiten|dritte|dritten|vierte|fuenfte|first|second|third)\b/);
    if (ord && M.list.length) { var ix = { erste: 0, ersten: 0, first: 0, zweite: 1, zweiten: 1, second: 1, dritte: 2, dritten: 2, third: 2, vierte: 3, fuenfte: 4 }[ord[2]]; if (M.list[ix]) { M.place = M.list[ix]; if (I !== 'knowledge') return finish(doRoute('zu dort', t, k), 'route'); } }
    if (/\b(wie weit (ist|sind) (das|es|die|der)|how far is (it|that))\b/.test(t) && M.place) { var h0 = here(k); if (h0) return finish(R(T('📍 **' + M.place.name + '** ist ' + km(V.dist(h0.lat, h0.lon, M.place.lat, M.place.lon)) + ' km Luftlinie entfernt. Soll ich dich hinbringen?', '📍 **' + M.place.name + '** is ' + kmE(V.dist(h0.lat, h0.lon, M.place.lat, M.place.lon)) + ' km away. Want me to take you there?')), 'live'); }
    if (/\b(wie weit (ist|sind) (das|es)|how far is (it|that))\b/.test(t) && !M.place && !(k.navigation && k.navigation.aktiv)) return finish(R(T('Wohin genau meinst du? 🤔 Nenn mir den Ort, dann sage ich dir, wie weit es ist.', 'Where exactly do you mean? 🤔')), 'chat');
    if (/\b(dorthin|dahin|da hin|hin bringen|hinbringen|bring mich (hin|dahin|dorthin)|take me there)\b/.test(t) && M.place) return finish(doRoute('zu dort', t, k), 'route');
    if (M.offer && /^(quiz|fact|story|joke)$/.test(M.offer) && /^(noch (eine|einen|einer|eins|mal)|nochmal|weiter|another|again|one more)\b/.test(t)) I = 'yes';
    if (I === 'yes' && M.offer) {
      var o = M.offer; M.offer = null;
      if (/^(quiz|fact|story)$/.test(o)) return finish(special({ quiz: '__quiz__', fact: '__fact__', story: '__story__' }[o]), o);
      if (o === 'joke') return finish(R(pickNR(JOKES[L === 'de' ? 'de' : 'en'], 'joke') + T(' Noch einer? 😄', ' Another one? 😄'), { _offer: 'joke' }), 'joke');
      if (o === 'route_first' && M.place) return finish(doRoute('zu dort', t, k), 'route');
      if (o === 'tour_easy') return finish(doTour('1 stunde leicht', k), 'tour');
      if (o.indexOf('route_to:') === 0) return finish(doRoute('nach ' + o.slice(9), 'route nach ' + norm(o.slice(9)), k), 'route');
    }
    if (I === 'yes') return finish(R(pick(T(['Super! 😊 Was machen wir?', 'Klar! Wobei kann ich helfen? 🦊'], ['Great! 😊 What shall we do?']))), 'yes');
    if (I === 'no') { M.offer = null; return finish(R(pick(T(['Alles klar 👍 Sag Bescheid, wenn du etwas brauchst.', 'Okay, kein Problem 😊'], ['Alright 👍', 'No problem 😊']))), 'no'); }
    if (M.offer === 'tour_ask' && (durationOf(t) || /\b(leicht|schwer|mittel|easy|hard)\b/.test(t))) { M.offer = null; return finish(doTour(t, k), 'tour'); }
    // Quiz- und Raetsel-Antworten
    if (M.quiz) {
      var qz = M.quiz, nn = t.match(/^\s*([123]|a|b|c|eins|zwei|drei)\b/), gi = nn ? { 1: 0, 2: 1, 3: 2, a: 0, b: 1, c: 2, eins: 0, zwei: 1, drei: 2 }[nn[1]] : qz[1].findIndex(function (o) { return norm(o).length > 2 && t.indexOf(norm(o)) >= 0; });
      if (gi != null && gi >= 0) { M.quiz = null; M.offer = 'quiz'; return finish(R(gi === qz[2] ? pickNR(T(['Richtig! 🎉 Stark!', 'Genau! ✅ Du kennst dich aus!', 'Jawohl! 🏆'], ['Correct! 🎉']), 'qok') + T(' Noch eine Frage?', ' Another one?') : T('Leider nicht 😅 Richtig ist: **' + qz[1][qz[2]] + '**. Noch eine Frage?', 'Not quite 😅 The answer is **' + qz[1][qz[2]] + '**. Another one?')), 'quiz'); }
    }
    if (M.riddle && M.turns - (M.riddleT || 0) > 3) M.riddle = null;
    if (M.quiz && M.turns - (M.quizT || 0) > 3) M.quiz = null;
    if (M.riddle && I !== 'greet') {
      var rd = M.riddle;
      if (new RegExp('\\b(' + rd[1] + ')').test(t)) { M.riddle = null; return finish(R(T('Richtig! 🎉 ', 'Correct! 🎉 ') + rd[2]), 'riddle'); }
      if (/\b(loesung|aufloesung|sag es|keine ahnung|weiss nicht|gib auf|answer|i give up)\b/.test(t)) { M.riddle = null; return finish(R(T('Die Lösung: ', 'The answer: ') + rd[2]), 'riddle'); }
      if (t.split(' ').length <= 2 && I === 'chat' && !bestOf(SD.chat, t, { coverage: 0.34 })) return finish(R(pickNR(T(['Nicht ganz 😄 Rate nochmal!', 'Knapp daneben! 🤏 Noch ein Versuch?', 'Hmm, leider nein 🙈 Willst du die Lösung?'], ['Not quite 😄 Try again!']), 'rno')), 'riddle');
    }
    // Gedaechtnis (Aussagen ueber dich, Fragen nach dir)
    var mem = memorize(raw, t); if (mem) return finish(mem, 'memory');
    // Echtes Sprachmodell, wenn geladen: Gespraech und Wissen (Witze, Quiz, Raetsel bleiben handverlesen)
    if (window.SoulLLM && SoulLLM.usable() && LLM_I.test(I) && !M.riddle && !M.quiz && !/\b(witz|witze|joke|quiz|raetsel|riddle)\b/.test(t)) {
      var I0 = I;
      return finish(llmPath(raw, t, I, k, onPart).catch(function () { return ruleRest(raw, t, I0, k); }), 'llm_' + I);
    }
    return ruleRest(raw, t, I, k);
  }
  // Regelbasierte Antworten (auch Rueckfall, wenn das Sprachmodell nicht da ist)
  function ruleRest(raw, t, I, k) {
    // Rad-Wissensbuch, wenn klar danach gefragt wird (zwei passende Woerter)
    if (/^(chat|knowledge|weather|wear|training|safety|bike|lowmood|goodmood|tour)$/.test(I)) {
      var fq = bestOf(SD.faq, t, { strong: true });
      if (fq) return finish(R(L === 'de' ? fq.e.a : fq.e.e), 'faq');
    }
    intentCache = I;
    var r = chain(t, k);
    if (r) return finish(r, I);
    switch (I) {
      case 'math': var v = doMath(t); return finish(R(v == null ? T('Das kann ich nicht ausrechnen 🤔', 'I can\'t calculate that 🤔') : '🧮 ' + String(v).replace('.', L === 'de' ? ',' : '.')), I);
      case 'route': return finish(doRoute(raw, t, k), I);
      case 'adjust': return finish(doAdjust(t, k), I);
      case 'nav_start': return finish(k.route ? R(T('Los geht\'s! 🚴💨 Gute Fahrt!', 'Let\'s go! 🚴💨'), { aktionen: [{ typ: 'navigation_starten' }] }) : R(T('Es ist noch keine Route offen. Wohin willst du? 🗺️', 'No route open yet. Where to? 🗺️')), I);
      case 'tour': return finish(doTour(t, k), I);
      case 'nearby': return finish(doNearby(t, k), I);
      case 'weather': return finish(doWeather(t, k, false), I);
      case 'wear': return finish(doWeather(t, k, true), I);
      case 'live': return finish(doLive(t, k), I);
      case 'stats': return finish(doStats(t), I);
      case 'bike': return finish(doBike(t), I);
      case 'training': return finish(doTraining(t), I);
      case 'safety': return finish(doSafety(), I);
      case 'apphelp': return finish(doHelp(t), I);
    }
    // Wissensbuch (auch einzelne Fachwoerter), Plauder-Sammlung, Meinungen
    var fq2 = bestOf(SD.faq, t, { coverage: 0.2 });
    if (fq2 && (I === 'knowledge' || I === 'chat')) return finish(R(L === 'de' ? fq2.e.a : fq2.e.e), 'faq');
    var op = opinion(raw, t); if (op) return finish(op, 'opinion');
    var ch = bestOf(SD.chat, t, { coverage: 0.34 });
    if (ch) {
      var arr = (L === 'de' ? ch.e.a : ch.e.e) || ch.e.a, ans = pickNR(arr, 'c' + ch.i);
      var sp = /^__\w+__$/.test(ans) ? special(ans) : null;
      return finish(sp || R(ans), 'smalltalk');
    }
    // "Und der Mont Blanc?" nach einer Wissensfrage
    var und = t.match(/^und (?:was ist mit |wie ist es mit |what about )?(.{2,40})$/);
    if (und && M.lastQ) { var raw2 = M.lastQ.replace(/(ist|sind|war|liegt|is|was)\s+(der|die|das|the)?\s*.+$/i, '$1 ') + raw.replace(/^\s*und\s+(was ist mit|wie ist es mit|what about)?\s*/i, '').replace(/[?!.]+$/, ''); return finish(knowledge(raw2, fix(norm(raw2))).then(function (x) { return { antwort: x, aktionen: [], orte: [] }; }), 'knowledge'); }
    if (I === 'knowledge') { M.lastQ = raw.replace(/[?!.]+$/, ''); return finish(knowledge(raw, t).then(function (x) { return { antwort: x, aktionen: [], orte: [] }; }), I); }
    return finish(chat(raw, t), 'chat');
  }
  function finish(p, intent) {
    if (intent && VX.track) try { VX.track('soul_' + intent); } catch (e) {}
    return Promise.resolve(p).then(function (o) {
      if (o._offer) { M.offer = o._offer; delete o._offer; }
      o.credits = 0;
      return o;
    });
  }
  // Antwort der App auf Hintergrund-Abrufe (Android)
  function got(id, text, err) { var p = pend[id]; if (!p) return; delete pend[id]; if (err) p.rej(new Error(err)); else p.res(text); }
  window.Soul = { ask: ask, _got: got, reset: function () { HIST = []; M.lastSearch = null; M.article = null; M.lastTopic = null; M.offer = null; }, _detect: function (s) { L = langOf(s); return detect(s, fix(norm(s))); }, _dbg: function (s) { var t = fix(norm(s)); return { t: t, content: content(t), article: !!M.article, lastQ: M.lastQ }; } };
})();
