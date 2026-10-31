/* Velora 5.4: Haptik, smoothes Design, Navigationspfeil, Maskottchen Velo und Velora Plus.
   Haengt sich ueber window.VX an die Haken in app.js (HOOK). */
(function () {
  'use strict';
  var V = window.V; if (!V) return;
  var A = V.A, S = V.S, $ = V.$, esc = V.esc, svg = V.svg, map = V.map;
  var VX = window.VX = {};
  var TESTER = !!window.__TESTER;
  var KI_BASE = 'https://velora-ki.toni-sagmeister11.workers.dev', KI_ID = 'velora-2026';

  function dayKey(t) { var d = new Date(t); return d.getFullYear() + '-' + ('0' + (d.getMonth() + 1)).slice(-2) + '-' + ('0' + d.getDate()).slice(-2); }
  function dayStart(t) { var d = new Date(t); d.setHours(0, 0, 0, 0); return d.getTime(); }
  function weekStart(t) { var d = new Date(dayStart(t)); var wd = (d.getDay() + 6) % 7; return d.getTime() - wd * 864e5; }
  function km(m) { return V.fmtKm(m); }
  function num(v) { return String(v).replace('.', ','); }
  function rnd(n) { var c = 'abcdefghijkmnpqrstuvwxyz23456789', o = ''; for (var i = 0; i < n; i++) o += c[Math.floor(Math.random() * c.length)]; return o; }

  // =====================================================================
  // Velora Plus (Bezahlung kommt spaeter ueber Google Play, bis dahin zum Testen freischaltbar)
  // =====================================================================
  var P = V.load('plus', { on: false });
  function isPlus() { return !!P.on; }
  function setPlus(on) { P.on = on; P.since = on ? Date.now() : 0; V.store('plus', P); applyLook(); updWidget(); }
  var TAG = '<span class="plustag">PLUS</span>';
  var FEATS = [
    ['bike', 'Mehr Fahrprofile', 'Gravel, Familie mit Kindern (ruhig und flach) und Rennrad mit Steigungsgrenze.'],
    ['wind', 'E-Bike-Akkuplaner', 'Reicht dein Akku für die Route? Mit Ladestationen in der Nähe.'],
    ['wind', 'Rückenwind-Touren', 'Rundtouren so gelegt, dass du auf dem Heimweg Rückenwind hast.'],
    ['sun', 'Regenwarnung für die Route', 'Zeigt, ob es unterwegs zu deiner Ankunftszeit regnet.'],
    ['flag', 'Routen speichern und GPX', 'Lieblingsrouten sichern, GPX exportieren und importieren.'],
    ['road', 'Offline-Karten', 'Kartenausschnitte für Reisen ohne Internet herunterladen.'],
    ['list', 'Fahrten-Tagebuch', 'Wochen, Monate, Kalorien, Höhenmeter und Wochenziel.'],
    ['up', 'Abzeichen und Bestzeiten', 'Sammle Abzeichen und jage deine Rekorde an Anstiegen.'],
    ['pin', 'Live-Standort teilen', 'Familie und Freunde sehen per Link, wo du gerade fährst.'],
    ['play', 'Startbildschirm-Widget', 'Wochenkilometer und Serie direkt auf dem Handy.'],
    ['juno', 'Juno ohne Tageslimit', 'Kostenlos 5 Fragen pro Tag, mit Plus so viele du willst.'],
    ['juno', 'Juno als Reiseführer', 'Erzählt unterwegs, an welchen Orten du gerade vorbeifährst.'],
    ['loop', 'Tagesplaner', 'Ein ganzer Tag mit Aussicht, Mittagessen und Badestopp als eine Route.'],
    ['sun', 'Designs und Pfeile', 'Farbthemen und verschiedene Navigationspfeile.']
  ];
  var PRICE = load2('price', 'year');
  function load2(k, d) { return V.load(k, d); }
  // Fenster ueber allem (waehrend der Navigation wird das Blatt laufend neu gezeichnet)
  function modal(html) {
    closeModal();
    var el = document.createElement('div'); el.id = 'vxm';
    el.innerHTML = '<div class="cbox mbox">' + html + '</div>';
    document.body.appendChild(el);
    el.onclick = function (e) { if (e.target === el) closeModal(); };
    return el;
  }
  function closeModal() { var m = $('vxm'); if (m) m.remove(); }
  function paywall(why) {
    var inModal = !!V.nav;
    if (!inModal) V.state = 'plus';
    var html = '<div class="plus-hero"><h2>Velora Plus</h2><div class="sub" style="max-width:68%">' + (why ? esc(why) + ' ist Teil von Velora Plus.' : 'Mehr Touren, mehr Juno, mehr Spaß auf dem Rad.') + '</div>' + mascot('cheer', 92) + '</div>' +
      FEATS.map(function (f) { return '<div class="feat"><div class="ico">' + svg(f[0], '#6b4dff', 18, 2.2) + '</div><div class="grow"><b>' + f[1] + '</b><span>' + f[2] + '</span></div></div>'; }).join('') +
      '<div class="sub" style="margin-top:10px">Rundtouren, Navigation, Juno (5 Fragen am Tag) und die Serie mit Velo bleiben kostenlos.</div>' +
      '<div class="price"><button data-price="month" class="' + (PRICE === 'month' ? 'on' : '') + '"><b>2,99 €</b><span>pro Monat</span></button><button data-price="year" class="' + (PRICE === 'year' ? 'on' : '') + '"><em>-44 %</em><b>19,99 €</b><span>pro Jahr</span></button></div>' +
      (isPlus() ? '<div class="pbox ok"><div class="t">' + svg('flag', '#0c9a68', 18) + 'Plus ist aktiv</div><div class="sub">Danke! Alle Funktionen sind freigeschaltet.</div></div><div class="btns"><button class="go sec" id="plusOff">Plus beenden (Test)</button></div>'
        : '<div class="btns"><button class="go plus" id="plusGo">Plus freischalten</button></div><div class="sub" style="font-size:12px;margin-top:8px;text-align:center">Testversion: Die Bezahlung über Google Play wird mit der Veröffentlichung eingebaut. Bis dahin wird Plus hier kostenlos zum Ausprobieren freigeschaltet.</div>') +
      '<div class="btns"><button class="go sec" id="plusBack">Zurück</button></div>';
    var root = inModal ? modal(html) : (V.sheet(html), V.sheetEl);
    if (!inModal) V.sheetEl.classList.add('tall');
    root.querySelectorAll('[data-price]').forEach(function (b) { b.onclick = function () { PRICE = b.getAttribute('data-price'); V.store('price', PRICE); paywall(why); }; });
    if ($('plusGo')) $('plusGo').onclick = function () { setPlus(true); if (inModal) closeModal(); celebrate({ mood: 'cheer', title: 'Willkommen bei Plus!', text: 'Alle Funktionen sind jetzt freigeschaltet. Viel Spaß beim Fahren!' }); if (!inModal) paywall(why); };
    if ($('plusOff')) $('plusOff').onclick = function () { setPlus(false); paywall(why); };
    $('plusBack').onclick = function () { if (inModal) closeModal(); else V.showMenu(); };
  }
  // true = darf; sonst Paywall
  function gate(name) { if (isPlus()) return true; hap('tick'); paywall(name); return false; }
  VX.bikeLocked = function (k) { return V.BIKES[k] && V.BIKES[k].pro && !gate('Das Profil ' + V.BIKES[k].n); };

  // =====================================================================
  // Haptik: kurze, knackige Effekte (Android); im Browser-Tester als Anzeige oben
  // =====================================================================
  var HAPN = { tick: 'Tick', click: 'Klick', soft: 'Sanft', success: 'Erfolg', celebrate: 'Feier', left: 'Links (2x)', right: 'Rechts (3x)', warn: 'Warnung', turn: 'Abbiegen' };
  var PAT = { tick: [8], click: [18], soft: [10, 60, 8], success: [20, 50, 35], celebrate: [25, 60, 25, 60, 60], left: [35, 110, 35], right: [35, 110, 35, 110, 35], warn: [110, 80, 110, 80, 110], turn: [40, 100, 40] };
  var hvT = null;
  function hap(t) {
    var lv = S.hap == null ? 2 : S.hap;
    if (!lv) return true;
    if (t === 'turn') t = 'left';
    if (A && A.haptic) { try { A.haptic(t, lv); } catch (e) {} }
    else if (navigator.vibrate) { try { navigator.vibrate(PAT[t] || [15]); } catch (e) {} }
    if (TESTER) {
      var el = $('hapviz'); if (!el) { el = document.createElement('div'); el.id = 'hapviz'; document.body.appendChild(el); }
      el.textContent = 'Vibration: ' + (HAPN[t] || t) + (lv === 1 ? ' (leicht)' : '');
      el.classList.remove('on'); void el.offsetWidth; el.classList.add('on');
      clearTimeout(hvT); hvT = setTimeout(function () { el.classList.remove('on'); }, 900);
    }
    return true;
  }
  VX.haptic = hap;
  document.addEventListener('pointerdown', function (e) {
    var b = e.target.closest && e.target.closest('button,.chip,.row,.card,.sw,[data-act],.badge');
    if (!b) return;
    var strong = b.matches('.go:not(.sec),#start,#chatSend,.quick button,#plusGo,.price button');
    hap(strong ? 'click' : 'tick');
  }, { capture: true, passive: true });

  // =====================================================================
  // Maskottchen Velo: ein kleines Rad mit Helm
  // =====================================================================
  var mId = 0;
  function mascot(mood, size) {
    var g = 'vg' + (++mId);
    var spokes = '';
    for (var i = 0; i < 12; i++) { var a = i * Math.PI / 6; spokes += '<line x1="60" y1="64" x2="' + (60 + Math.cos(a) * 37).toFixed(1) + '" y2="' + (64 + Math.sin(a) * 37).toFixed(1) + '"/>'; }
    return '<div class="mascot m-' + mood + '" style="width:' + size + 'px;height:' + size + 'px"><svg viewBox="0 0 120 120" aria-label="Velo">' +
      '<defs><radialGradient id="' + g + 'f" cx="42%" cy="35%" r="70%"><stop offset="0" stop-color="#6ff0bf"/><stop offset=".6" stop-color="#2fd59a"/><stop offset="1" stop-color="#14a873"/></radialGradient>' +
      '<linearGradient id="' + g + 'h" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#ffb35c"/><stop offset="1" stop-color="#f76707"/></linearGradient></defs>' +
      '<ellipse class="m-sh" cx="60" cy="112" rx="30" ry="5" fill="rgba(13,43,62,.16)"/>' +
      '<g class="m-lines" stroke="#9fb0bf" stroke-width="3" stroke-linecap="round"><path d="M6 50 H22"/><path d="M2 64 H16"/><path d="M8 78 H20"/></g>' +
      '<g class="m-body">' +
      '<g class="m-arml" stroke="#0d2b3e" stroke-width="6" stroke-linecap="round" fill="none"><path class="arm-down" d="M19 70 Q9 74 7 84"/><path class="arm-up" d="M19 58 Q8 48 9 34"/></g>' +
      '<g class="m-armr" stroke="#0d2b3e" stroke-width="6" stroke-linecap="round" fill="none"><path class="arm-down" d="M101 70 Q111 74 113 84"/><path class="arm-up" d="M101 58 Q112 48 111 34"/></g>' +
      '<circle cx="60" cy="64" r="44" fill="#0d2b3e"/>' +
      '<circle cx="60" cy="64" r="41" fill="none" stroke="#2a4a62" stroke-width="2.5" stroke-dasharray="3 5"/>' +
      '<circle cx="60" cy="64" r="37" fill="#dfe7ee"/>' +
      '<g class="m-spokes" stroke="#9fb0bf" stroke-width="1.6">' + spokes + '</g>' +
      '<circle cx="60" cy="64" r="31" fill="url(#' + g + 'f)" stroke="#ffffff" stroke-width="2"/>' +
      '<g class="m-eyes"><ellipse cx="49" cy="59" rx="7.5" ry="9" fill="#fff"/><ellipse cx="71" cy="59" rx="7.5" ry="9" fill="#fff"/>' +
      '<circle cx="50.5" cy="60.5" r="4.6" fill="#0d2b3e"/><circle cx="72.5" cy="60.5" r="4.6" fill="#0d2b3e"/><circle cx="52.2" cy="58.4" r="1.7" fill="#fff"/><circle cx="74.2" cy="58.4" r="1.7" fill="#fff"/></g>' +
      '<ellipse cx="40" cy="71" rx="5" ry="3" fill="#ff8fa3" opacity=".75"/><ellipse cx="80" cy="71" rx="5" ry="3" fill="#ff8fa3" opacity=".75"/>' +
      '<path class="mo mo-smile" d="M51 73 Q60 81 69 73" stroke="#0d2b3e" stroke-width="3.2" fill="none" stroke-linecap="round"/>' +
      '<g class="mo mo-open"><path d="M50 71 Q60 89 70 71 Z" fill="#0d2b3e"/><path d="M55 79 Q60 84 65 79 Q60 76 55 79 Z" fill="#ff6b81"/></g>' +
      '<path class="mo mo-sad" d="M52 79 Q60 72 68 79" stroke="#0d2b3e" stroke-width="3.2" fill="none" stroke-linecap="round"/>' +
      '<path class="m-tear" d="M43 68 q-3 5 0 7 q3 -2 0 -7z" fill="#5aa9ff"/>' +
      '<path d="M24 37 C27 12 93 12 96 37 C80 31 40 31 24 37 Z" fill="url(#' + g + 'h)" stroke="#d9480f" stroke-width="1.5"/>' +
      '<path d="M47 18 L44 31 M60 15 V30 M73 18 L76 31" stroke="#ffd8a8" stroke-width="3" stroke-linecap="round"/>' +
      '<path d="M90 33 Q100 34 104 39" stroke="#d9480f" stroke-width="4" stroke-linecap="round" fill="none"/>' +
      '</g></svg></div>';
  }
  VX.mascot = mascot;
  var FLAME = '<svg width="22" height="22" viewBox="0 0 24 24"><path d="M12 2c1 4 6 6 6 12a6 6 0 0 1-12 0c0-3 2-5 3-6 0 2 1 3 2 3 0-4-1-6 1-9z" fill="#ff922b"/><path d="M12 12c1 2 3 3 3 5a3 3 0 0 1-6 0c0-1 1-2 1-3 1 1 2 0 2-2z" fill="#ffd43b"/></svg>';

  // ---------- Feiern ----------
  var celeQ = [], celeOpen = false;
  function celebrate(o) { celeQ.push(o); if (celeOpen && !$('cele')) celeOpen = false; if (!celeOpen) nextCele(); }
  function nextCele() {
    var o = celeQ.shift(); if (!o) { celeOpen = false; return; }
    celeOpen = true;
    var el = document.createElement('div'); el.id = 'cele';
    var top = o.medal ? '<div class="medal">' + medalSvg(o.medal, o.col || '#f5b70a') + '</div>' : mascot(o.mood || 'cheer', 140);
    el.innerHTML = '<div class="cbox">' + top + (o.big != null ? '<div class="big' + (o.flame ? '' : ' acc') + '">' + (o.flame ? FLAME.replace('22', '44').replace('22', '44') : '') + o.big + '</div>' : '') +
      '<h2>' + esc(o.title) + '</h2><div class="sub">' + esc(o.text || '') + '</div><button class="go" id="celeOk">' + esc(o.btn || 'Weiter') + '</button></div>';
    var cols = ['#2fd59a', '#5aa9ff', '#ffa94d', '#f06595', '#ffd43b', '#b197fc'];
    for (var i = 0; i < 70; i++) {
      var c = document.createElement('i'); c.className = 'conf';
      c.style.left = Math.random() * 100 + '%'; c.style.background = cols[i % cols.length];
      c.style.setProperty('--dx', (Math.random() * 160 - 80) + 'px'); c.style.setProperty('--rot', (Math.random() * 900 - 450) + 'deg');
      c.style.animationDuration = (1.8 + Math.random() * 1.8) + 's'; c.style.animationDelay = (Math.random() * 0.6) + 's';
      if (Math.random() < 0.3) c.style.borderRadius = '50%';
      el.appendChild(c);
    }
    document.body.appendChild(el);
    hap('celebrate');
    if (o.say && S.voice) V.say(o.say);
    function close() { el.style.transition = 'opacity .25s'; el.style.opacity = 0; setTimeout(function () { el.remove(); nextCele(); }, 250); }
    $('celeOk').onclick = close;
    el.onclick = function (e) { if (e.target === el) close(); };
  }
  VX.celebrate = celebrate;
  function medalSvg(icon, col) {
    return '<svg viewBox="0 0 64 64"><path d="M22 2h8l6 16-8 4zM42 2h-8l-6 16 8 4z" fill="#e5463c"/><path d="M27 2h3l4 11-3 2zM37 2h-3l-4 11 3 2z" fill="#ff8787"/>' +
      '<circle cx="32" cy="40" r="20" fill="' + col + '"/><circle cx="32" cy="40" r="16" fill="none" stroke="rgba(255,255,255,.55)" stroke-width="2"/>' +
      '<g transform="translate(20 28)">' + svg(icon, '#ffffff', 24, 2.4).replace(/^<svg[^>]*>/, '').replace(/<\/svg>$/, '') + '</g></svg>';
  }

  // =====================================================================
  // Fahrten-Tagebuch, Serie, Wochenziel
  // =====================================================================
  var LOG = V.load('log', null);
  if (!LOG) { LOG = V.load('rides', []).map(function (r) { return { t: r.t, d: r.dist, mv: r.mv, asc: r.asc, max: r.max || 0 }; }).reverse(); V.store('log', LOG); }
  function kcal(r) {
    var h = r.mv / 3600, v = r.mv > 0 ? r.d / r.mv * 3.6 : 0;
    var met = v < 16 ? 4 : v < 19 ? 6.8 : v < 22 ? 8 : 10;
    if (S.bike === 'ebike') met *= 0.6;
    return Math.round(met * S.weight * h + r.asc * S.weight * 9.81 / 4184 / 0.24 * 0.3);
  }
  function streak() {
    var days = {}; LOG.forEach(function (r) { if (r.d >= 1000) days[dayKey(r.t)] = 1; });
    var now = Date.now(), cur = 0, t = dayStart(now);
    if (!days[dayKey(t)]) t -= 864e5; // heute noch nicht gefahren: Serie bis gestern zaehlt
    while (days[dayKey(t)]) { cur++; t -= 864e5; }
    var best = 0, run = 0, keys = Object.keys(days).sort(), prev = null;
    keys.forEach(function (k) { var d = new Date(k + 'T12:00:00').getTime(); run = prev && Math.round((d - prev) / 864e5) === 1 ? run + 1 : 1; prev = d; if (run > best) best = run; });
    return { cur: cur, best: best, today: !!days[dayKey(now)] };
  }
  function sumSince(t0, t1) {
    var o = { d: 0, mv: 0, asc: 0, kcal: 0, n: 0, maxD: 0 };
    LOG.forEach(function (r) { if (r.t >= t0 && (!t1 || r.t < t1)) { o.d += r.d; o.mv += r.mv; o.asc += r.asc; o.kcal += kcal(r); o.n++; if (r.d > o.maxD) o.maxD = r.d; } });
    return o;
  }
  function weekDays(ws) { var out = []; for (var i = 0; i < 7; i++) { var a = ws + i * 864e5; out.push(sumSince(a, a + 864e5).d); } return out; }

  // ---------- Abzeichen ----------
  var IS = (function () { // Nordwest- und Suedostspitze von Ugljan
    var bb = V.D.bbox, k = (bb[2] - bb[0]) / (bb[3] - bb[1]), mx = -1e9, mn = 1e9, a = 0, b = 0;
    for (var i = 0; i < V.LAT.length; i += 7) { var p = V.LAT[i] - V.LON[i] * k; if (p > mx) { mx = p; a = i; } if (p < mn) { mn = p; b = i; } }
    return { nw: [V.LAT[a], V.LON[a]], se: [V.LAT[b], V.LON[b]] };
  })();
  var VIS = V.load('visited', { beach: {}, village: {} });
  var BADGES = [
    ['first', 'Erste Fahrt', 'bike', '#2fd59a', function (c) { return c.n >= 1; }],
    ['km10', '10 km gesamt', 'road', '#5aa9ff', function (c) { return c.tot >= 10000; }],
    ['km100', '100 km gesamt', 'road', '#4c6ef5', function (c) { return c.tot >= 100000; }],
    ['km500', '500 km gesamt', 'road', '#7c5cff', function (c) { return c.tot >= 500000; }],
    ['km1000', '1000 km gesamt', 'road', '#f5b70a', function (c) { return c.tot >= 1000000; }],
    ['r25', '25 km am Stück', 'flag', '#20c997', function (c) { return c.maxD >= 25000; }],
    ['r50', '50 km am Stück', 'flag', '#f76707', function (c) { return c.maxD >= 50000; }],
    ['hm500', '500 Höhenmeter in einer Fahrt', 'up', '#e8590c', function (c) { return c.maxAsc >= 500; }],
    ['hm5k', '5000 Höhenmeter gesamt', 'up', '#c92a2a', function (c) { return c.asc >= 5000; }],
    ['s3', '3 Tage in Folge', 'sun', '#ff922b', function (c) { return c.best >= 3; }],
    ['s7', '7 Tage in Folge', 'sun', '#f76707', function (c) { return c.best >= 7; }],
    ['s30', '30 Tage in Folge', 'sun', '#d9480f', function (c) { return c.best >= 30; }],
    ['ugljan', 'Ganz Ugljan von Spitze zu Spitze', 'loop', '#0c9a68', function (c) { return !!c.ugljan; }],
    ['beach5', '5 Strände besucht', 'beach', '#4fc3f7', function (c) { return c.beaches >= 5; }],
    ['villages', 'Alle Orte auf Ugljan', 'pin', '#e64980', function (c) { return c.villages >= c.villAll; }],
    ['early', 'Frühaufsteher: Start vor 7 Uhr', 'sun', '#fab005', function (c) { return c.early; }],
    ['night', 'Nachtfahrt: Start nach 21 Uhr', 'mute', '#5c7cfa', function (c) { return c.night; }],
    ['juno', '10 Fragen an Juno', 'juno', '#7c5cff', function (c) { return c.juno >= 10; }]
  ];
  var VILLAGES = V.D.places.filter(function (p) { return p[3] === 'village'; });
  var GOT = V.load('badges', {});
  function badgeCtx() {
    var c = { n: 0, tot: 0, maxD: 0, maxAsc: 0, asc: 0, best: streak().best, ugljan: V.load('ugljanDone', false), beaches: Object.keys(VIS.beach).length, villages: Object.keys(VIS.village).length, villAll: VILLAGES.length, early: false, night: false, juno: V.load('junoTotal', 0) };
    LOG.forEach(function (r) { if (r.d < 300) return; c.n++; c.tot += r.d; c.asc += r.asc; if (r.d > c.maxD) c.maxD = r.d; if (r.asc > c.maxAsc) c.maxAsc = r.asc; var h = new Date(r.t).getHours(); if (h < 7 && h >= 3) c.early = true; if (h >= 21) c.night = true; });
    return c;
  }
  function checkBadges(show) {
    var c = badgeCtx(), fresh = [];
    BADGES.forEach(function (b) { if (!GOT[b[0]] && b[4](c)) { GOT[b[0]] = Date.now(); fresh.push(b); } });
    if (fresh.length) V.store('badges', GOT);
    if (show) fresh.forEach(function (b) { celebrate({ medal: b[2], col: b[3], title: 'Neues Abzeichen!', text: b[1] + (isPlus() ? '' : '. Alle Abzeichen siehst du mit Velora Plus.'), btn: 'Super' }); });
  }
  function visitTrack(trk) {
    var beaches = V.D.pois.map(function (p, i) { return p[0] === 'beach' ? i : -1; }).filter(function (i) { return i >= 0; });
    var nw = false, se = false;
    for (var k = 0; k < trk.length; k += 2) {
      var la = trk[k][0], lo = trk[k][1];
      if (!V.onIsland(la, lo)) continue;
      beaches.forEach(function (i) { var p = V.D.pois[i]; if (!VIS.beach[i] && Math.abs(p[1] - la) < 0.002 && V.dist(la, lo, p[1], p[2]) < 150) VIS.beach[i] = 1; });
      VILLAGES.forEach(function (p) { if (!VIS.village[p[0]] && Math.abs(p[1] - la) < 0.006 && V.dist(la, lo, p[1], p[2]) < 500) VIS.village[p[0]] = 1; });
      if (V.dist(la, lo, IS.nw[0], IS.nw[1]) < 2000) nw = true;
      if (V.dist(la, lo, IS.se[0], IS.se[1]) < 2000) se = true;
    }
    if (nw && se) V.store('ugljanDone', true);
    V.store('visited', VIS);
  }

  // ---------- Bestzeiten an Anstiegen ----------
  var BEST = V.load('bests', {});
  function climbsOf(ride) {
    var trk = ride.trk, tt = ride.tt, out = [];
    if (!trk || !tt || tt.length !== trk.length || trk.length < 5) return out;
    var ele = trk.map(function (p) { return V.onIsland(p[0], p[1]) ? V.demEle(p[0], p[1]) : null; });
    var lo = 0, hi = 0;
    function flush() {
      if (ele[hi] - ele[lo] >= 30 && hi > lo) {
        var len = 0; for (var j = lo + 1; j <= hi; j++) len += V.dist(trk[j - 1][0], trk[j - 1][1], trk[j][0], trk[j][1]);
        var t = tt[hi] - tt[lo];
        if (len >= 300 && t > 20) out.push({ a: lo, b: hi, gain: ele[hi] - ele[lo], len: len, t: t });
      }
    }
    for (var i = 1; i < trk.length; i++) {
      if (ele[i] == null) { flush(); lo = hi = i; continue; }
      if (ele[lo] == null) { lo = hi = i; continue; }
      if (ele[i] > ele[hi]) hi = i;
      if (ele[i] < ele[hi] - 12 || ele[i] < ele[lo]) { flush(); lo = hi = i; }
    }
    flush();
    return out.map(function (c) {
      var A0 = trk[c.a], B0 = trk[c.b], g = function (v) { return Math.round(v * 400) / 400; };
      var top = V.nearestPlace(B0[0], B0[1]);
      c.key = g(A0[0]) + ',' + g(A0[1]) + '>' + g(B0[0]) + ',' + g(B0[1]);
      c.name = 'Anstieg bei ' + (top ? top[0] : 'Ugljan');
      return c;
    });
  }
  function fmtMin(s) { s = Math.round(s); return Math.floor(s / 60) + ':' + ('0' + s % 60).slice(-2) + ' min'; }
  function checkBests(ride) {
    climbsOf(ride).forEach(function (c) {
      var b = BEST[c.key];
      if (!b || c.t < b.t) {
        var old = b ? b.t : null;
        BEST[c.key] = { name: c.name, t: c.t, gain: Math.round(c.gain), len: Math.round(c.len), when: ride.t, prev: old };
        if (old && isPlus()) celebrate({ mood: 'cheer', big: fmtMin(c.t).replace(' min', ''), title: 'Neuer Rekord!', text: c.name + ': ' + Math.round(old - c.t) + ' Sekunden schneller als deine alte Bestzeit.', say: 'Neuer Rekord am Anstieg' });
      }
    });
    V.store('bests', BEST);
  }

  // ---------- Fahrt gespeichert: Tagebuch, Serie, Abzeichen, Rekorde ----------
  VX.rideSaved = function (ride) {
    var before = streak();
    LOG.push({ t: ride.t, d: ride.dist, mv: ride.mv, asc: ride.asc, max: ride.max || 0 });
    V.store('log', LOG);
    var after = streak();
    if (ride.trk) visitTrack(ride.trk);
    if (ride.dist >= 1000) {
      if (LOG.filter(function (r) { return r.d >= 1000; }).length === 1) celebrate({ mood: 'cheer', title: 'Deine erste Fahrt!', text: 'Ich bin Velo und feiere ab jetzt jede Fahrt mit dir. Fahr morgen wieder, dann startet deine Serie.', say: 'Super, deine erste Fahrt!' });
      else if (!before.today && after.cur >= 2) celebrate({ mood: 'cheer', flame: true, big: after.cur, title: after.cur + ' Tage in Folge!', text: after.cur >= after.best ? 'Das ist deine längste Serie bisher. Morgen wieder fahren, dann sind es ' + (after.cur + 1) + '.' : 'Stark! Deine Bestmarke sind ' + after.best + ' Tage. Bleib dran!', say: after.cur + ' Tage in Folge, stark!' });
      else celebrate({ mood: 'wave', big: km(ride.dist), title: 'Fahrt gespeichert', text: Math.round(ride.asc) + ' m bergauf, etwa ' + kcal({ d: ride.dist, mv: ride.mv, asc: ride.asc }) + ' kcal. Gut gemacht!' });
      var ws = weekStart(Date.now()), wk = sumSince(ws).d, wkBefore = wk - ride.dist;
      if (isPlus() && wk >= S.goalKm * 1000 && wkBefore < S.goalKm * 1000) celebrate({ mood: 'cheer', big: S.goalKm + ' km', title: 'Wochenziel geschafft!', text: 'Du hast diese Woche schon ' + km(wk) + ' km geschafft.' });
    }
    checkBests(ride);
    checkBadges(true);
    updWidget();
  };
  checkBadges(false);

  // ---------- Startbildschirm-Widget ----------
  function updWidget() {
    if (!A || !A.setWidget) return;
    try {
      if (!isPlus()) { A.setWidget('Velora', 'Widget mit Velora Plus', 'Wochenkilometer und Serie auf einen Blick'); return; }
      var wk = sumSince(weekStart(Date.now())), st = streak();
      A.setWidget(km(wk.d) + ' km', 'diese Woche' + (st.cur ? ' · Serie ' + st.cur + ' ' + (st.cur === 1 ? 'Tag' : 'Tage') : ''), wk.d >= S.goalKm * 1000 ? 'Wochenziel geschafft!' : 'Noch ' + km(Math.max(0, S.goalKm * 1000 - wk.d)) + ' km bis zum Wochenziel');
    } catch (e) {}
  }
  updWidget();

  // ---------- Startseite: Velo mit Serie und Wochenziel ----------
  VX.homeHtml = function () {
    var st = streak(), wk = sumSince(weekStart(Date.now())), goal = S.goalKm * 1000, mood = 'idle', head, txt;
    var hr = new Date().getHours();
    if (!LOG.length) { mood = 'wave'; head = 'Hallo, ich bin Velo!'; txt = 'Starte deine erste Fahrt, ich zähle mit und feiere jede Serie mit dir.'; }
    else if (st.today) { mood = 'cheer'; head = st.cur > 1 ? st.cur + ' Tage in Folge!' : 'Heute schon gefahren!'; txt = 'Diese Woche: ' + km(wk.d) + ' km. ' + (st.cur > 1 ? 'Morgen geht die Serie weiter.' : 'Fahr morgen wieder für eine Serie.'); }
    else if (st.cur > 0) { mood = 'wave'; head = 'Serie: ' + st.cur + (st.cur === 1 ? ' Tag' : ' Tage'); txt = hr >= 18 ? 'Heute noch eine kleine Runde? Sonst ist die Serie morgen weg.' : 'Fahr heute, damit deine Serie weiterläuft.'; }
    else { mood = 'idle'; head = (hr < 11 ? 'Guten Morgen!' : hr < 18 ? 'Lust auf eine Runde?' : 'Guten Abend!'); txt = 'Diese Woche: ' + km(wk.d) + ' km. Eine kurze Tour bringt dich wieder in Schwung.'; }
    return '<div class="velo-card" data-act="stats">' + mascot(mood, 58) + '<div class="bubble"><b>' + esc(head) + '</b><span>' + esc(txt) + '</span>' +
      '<div class="goalbar"><i style="width:' + Math.min(100, wk.d / goal * 100).toFixed(1) + '%"></i></div></div>' +
      (st.cur ? '<div class="flame">' + FLAME + st.cur + '</div>' : '') + '</div>';
  };
  VX.act = function (act) {
    if (act === 'stats') showStats();
    else if (act === 'plus') paywall();
    else if (act === 'dayplan') showDayPlan();
  };

  // ---------- Statistik-Seite ----------
  function showStats() {
    V.state = 'stats';
    var st = streak(), now = Date.now(), ws = weekStart(now), days = weekDays(ws), mx = Math.max.apply(null, days.concat([1000]));
    var wk = sumSince(ws), lastWk = sumSince(ws - 7 * 864e5, ws), mo = new Date(now); mo.setDate(1); mo.setHours(0, 0, 0, 0);
    var mon = sumSince(mo.getTime()), yr = sumSince(new Date(new Date().getFullYear(), 0, 1).getTime()), all = sumSince(0);
    var names = ['Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa', 'So'], c = badgeCtx();
    var html = '<div style="display:flex;align-items:center;gap:12px">' + mascot(st.today ? 'cheer' : 'wave', 70) + '<div class="grow"><h2>Deine Fahrten</h2><div class="sub">Serie: <b>' + st.cur + '</b> ' + (st.cur === 1 ? 'Tag' : 'Tage') + ' &middot; Bestmarke ' + st.best + '</div></div></div>' +
      '<h3>Diese Woche</h3><div class="weekbars">' + days.map(function (d, i) { var h = d / mx * 100; return '<div><em>' + (d ? km(d) : '') + '</em><i class="' + (d ? '' : 'zero') + '" style="height:' + Math.max(4, h) + '%;animation-delay:' + i * 0.05 + 's"></i><span>' + names[i] + '</span></div>'; }).join('') + '</div>';
    if (!isPlus()) {
      html += '<div class="plusbar"><div class="grow"><b>Tagebuch, Abzeichen und Rekorde</b><span>Monate, Kalorien, Wochenziel, ' + BADGES.length + ' Abzeichen und Bestzeiten an Anstiegen gibt es mit Plus.</span></div><button class="go plus" style="width:auto;padding:0 14px;height:42px;font-size:15px" data-act="plus">Plus</button></div>' +
        '<div class="stats"><div class="stat"><b>' + km(wk.d) + '</b><span>km Woche</span></div><div class="stat"><b>' + st.cur + '</b><span>Serie</span></div><div class="stat"><b>' + LOG.length + '</b><span>Fahrten</span></div></div>';
    } else {
      var goal = S.goalKm * 1000, diff = lastWk.d ? Math.round((wk.d - lastWk.d) / lastWk.d * 100) : null;
      html += '<div class="pbox"><div class="t">Wochenziel ' + S.goalKm + ' km</div><div class="meter"><i style="width:' + Math.min(100, wk.d / goal * 100) + '%;background:linear-gradient(90deg,var(--lime),var(--acc))"></i></div><div class="sub">' + km(wk.d) + ' von ' + S.goalKm + ' km' + (wk.d >= goal ? ' &middot; geschafft!' : '') + '</div>' +
        '<div class="seg">' + [20, 30, 50, 80, 120].map(function (g) { return '<button data-goal="' + g + '" class="' + (S.goalKm === g ? 'on' : '') + '">' + g + '</button>'; }).join('') + '</div></div>' +
        '<div class="stats"><div class="stat"><b>' + km(wk.d) + '</b><span>km Woche</span></div><div class="stat"><b>' + km(mon.d) + '</b><span>km Monat</span></div><div class="stat"><b>' + km(yr.d) + '</b><span>km Jahr</span></div></div>' +
        '<div class="stats"><div class="stat w"><b>' + wk.kcal + '</b><span>kcal Woche</span></div><div class="stat w"><b>' + Math.round(wk.asc) + ' m</b><span>Höhe Woche</span></div><div class="stat w"><b>' + (all.mv ? num((all.d / all.mv * 3.6).toFixed(1)) : '0') + '</b><span>km/h Schnitt</span></div></div>' +
        '<div class="stats"><div class="stat w"><b>' + km(all.d) + '</b><span>km gesamt</span></div><div class="stat w"><b>' + km(all.maxD) + '</b><span>Längste</span></div><div class="stat w"><b>' + all.n + '</b><span>Fahrten</span></div></div>' +
        '<h3>Wochenrückblick von Velo</h3><div class="velo-card">' + mascot(diff != null && diff >= 0 ? 'cheer' : 'idle', 50) + '<div class="bubble"><span>' + esc(recap(wk, lastWk, diff, st)) + '</span></div></div>';
      var bl = Object.keys(BEST).map(function (k) { return BEST[k]; }).sort(function (a, b) { return b.gain - a.gain; });
      html += '<h3>Bestzeiten an Anstiegen</h3>' + (bl.length ? bl.slice(0, 8).map(function (b) { return '<div class="row"><div class="ico">' + svg('up', '#e8590c', 18) + '</div><div class="grow"><div class="t ell">' + esc(b.name) + '</div><div class="s">' + V.fmtDist(b.len) + ', +' + b.gain + ' m' + (b.prev ? ' &middot; vorher ' + fmtMin(b.prev) : '') + '</div></div><div class="r"><b>' + fmtMin(b.t) + '</b></div></div>'; }).join('') : '<div class="empty sub">Fahr auf Ugljan einen Anstieg hoch, dann misst Velo deine Zeit.</div>');
    }
    html += '<h3>Abzeichen ' + Object.keys(GOT).length + ' / ' + BADGES.length + '</h3><div class="badges">' + BADGES.map(function (b) { return '<div class="badge' + (GOT[b[0]] ? ' got' : '') + '">' + medalSvg(b[2], b[3]) + esc(b[1]) + '</div>'; }).join('') + '</div>' +
      '<div class="sub" style="font-size:12px">Strände besucht: ' + c.beaches + ' &middot; Orte: ' + c.villages + ' / ' + c.villAll + '</div>' +
      '<div class="btns"><button class="go sec" id="stBack">Zurück</button></div>';
    V.sheet(html); V.sheetEl.classList.add('tall');
    V.sheetEl.querySelectorAll('[data-goal]').forEach(function (b) { b.onclick = function () { S.goalKm = +b.getAttribute('data-goal'); V.saveS(); updWidget(); showStats(); }; });
    $('stBack').onclick = function () { V.drawHome(); };
  }
  function recap(wk, lw, diff, st) {
    if (!wk.n && !lw.n) return 'Diese Woche noch keine Fahrt. Schon 20 Minuten auf dem Rad machen den Kopf frei!';
    var t = 'Diese Woche ' + km(wk.d) + ' km in ' + wk.n + (wk.n === 1 ? ' Fahrt' : ' Fahrten') + ', ' + Math.round(wk.asc) + ' Höhenmeter und rund ' + wk.kcal + ' kcal.';
    if (diff != null) t += diff >= 0 ? ' Das sind ' + diff + ' % mehr als letzte Woche, stark!' : ' Letzte Woche waren es ' + km(lw.d) + ' km, da geht noch was.';
    if (st.cur >= 3) t += ' Deine Serie: ' + st.cur + ' Tage.';
    return t;
  }

  // =====================================================================
  // Juno: kostenlos 5 Fragen am Tag, mit Plus unbegrenzt
  // =====================================================================
  var FREE_Q = 5;
  function junoCount() { var c = V.load('junoCnt', { d: '', n: 0 }); if (c.d !== dayKey(Date.now())) c = { d: dayKey(Date.now()), n: 0 }; return c; }
  VX.junoGate = function () {
    if (isPlus()) return true;
    if (junoCount().n >= FREE_Q) { paywall('Mehr als ' + FREE_Q + ' Fragen am Tag an Juno'); return false; }
    return true;
  };
  VX.junoUsed = function () {
    var c = junoCount(); c.n++; V.store('junoCnt', c);
    V.store('junoTotal', V.load('junoTotal', 0) + 1);
    if (!isPlus() && FREE_Q - c.n <= 2 && FREE_Q - c.n > 0) V.toast('Noch ' + (FREE_Q - c.n) + ' kostenlose Juno-Fragen heute');
    checkBadges(true);
  };

  // =====================================================================
  // Routenblatt: E-Bike-Akku, Regenwarnung, Speichern, GPX, Live teilen
  // =====================================================================
  var ASSIST = { eco: ['Eco', 0.35], tour: ['Tour', 0.5], turbo: ['Turbo', 0.68] };
  function batteryWh(r) {
    var m = 115, v = r.len / Math.max(60, r.secs), crr = 0.008, E = 0;
    for (var i = 1; i < r.ele.length; i++) { var dh = r.ele[i] - r.ele[i - 1]; if (dh > 0) E += m * 9.81 * dh; }
    E += crr * m * 9.81 * r.len + 0.5 * 1.2 * 0.6 * v * v * r.len;
    return E / 3600 * ASSIST[S.assist][1] / 0.78 + r.len / 1000 * 1.5;
  }
  VX.routeHtml = function (r) {
    var h = '';
    if (S.bike === 'ebike') {
      if (isPlus()) {
        var wh = batteryWh(r), p = wh / S.batt * 100, col = p > 90 ? '#e5463c' : p > 65 ? '#f08c00' : '#12b886';
        h += '<div class="pbox' + (p > 90 ? ' warn' : '') + '"><div class="t">' + svg('wind', col, 18) + 'E-Bike-Akku: ca. ' + Math.round(wh) + ' Wh</div><div class="meter"><i style="width:' + Math.min(100, p) + '%;background:' + col + '"></i></div>' +
          '<div class="sub">' + (p > 100 ? 'Der Akku reicht wahrscheinlich nicht. Unterwegs laden oder sparsamer fahren.' : p > 90 ? 'Knapp: Du kommst mit wenig Reserve an.' : 'Reicht gut: am Ziel noch etwa ' + Math.round(100 - p) + ' %.') + '</div>' +
          '<div class="seg">' + Object.keys(ASSIST).map(function (k) { return '<button data-assist="' + k + '" class="' + (S.assist === k ? 'on' : '') + '">' + ASSIST[k][0] + '</button>'; }).join('') + '</div>' +
          '<div class="seg">' + [400, 500, 625, 750].map(function (b) { return '<button data-batt="' + b + '" class="' + (S.batt === b ? 'on' : '') + '">' + b + ' Wh</button>'; }).join('') + '</div>' +
          (p > 65 ? '<button class="go sec" id="chargeBtn" style="height:42px;font-size:15px">Ladestationen in der Nähe</button><div id="chargeOut"></div>' : '') + '</div>';
      } else h += '<div class="pbox locked" data-lock="E-Bike-Akkuplaner"><div class="t">' + svg('wind', '#7c5cff', 18) + 'Reicht dein Akku?' + TAG + '</div><div class="sub">Akkuverbrauch und Ladestationen für diese Route.</div></div>';
    }
    h += isPlus() ? '<div class="pbox" id="rainBox"><div class="t">' + svg('sun', '#5aa9ff', 18) + 'Wetter unterwegs</div><div class="sub">Prüfe Regen entlang der Route ...</div></div>'
      : '<div class="pbox locked" data-lock="Die Regenwarnung"><div class="t">' + svg('sun', '#7c5cff', 18) + 'Regnet es unterwegs?' + TAG + '</div><div class="sub">Regenwarnung zu deiner Ankunftszeit an jedem Abschnitt.</div></div>';
    h += '<div class="linkrow"><button id="rtSave">' + svg('flag', 'currentColor', 18) + 'Speichern' + (isPlus() ? '' : TAG) + '</button><button id="rtGpx">' + svg('road', 'currentColor', 18) + 'GPX' + (isPlus() ? '' : TAG) + '</button></div>';
    return h;
  };
  VX.routeBound = function (r, plan) {
    var el = V.sheetEl;
    el.querySelectorAll('[data-lock]').forEach(function (b) { b.onclick = function () { gate(b.getAttribute('data-lock')); }; });
    el.querySelectorAll('[data-assist]').forEach(function (b) { b.onclick = function () { S.assist = b.getAttribute('data-assist'); V.saveS(); rerouteSheet(); }; });
    el.querySelectorAll('[data-batt]').forEach(function (b) { b.onclick = function () { S.batt = +b.getAttribute('data-batt'); V.saveS(); rerouteSheet(); }; });
    if ($('chargeBtn')) $('chargeBtn').onclick = function () { chargers(r); };
    $('rtSave').onclick = function () { if (gate('Routen speichern')) saveRoute(r, plan); };
    $('rtGpx').onclick = function () { if (gate('GPX-Export')) exportGpx(r, plan.title); };
    if (isPlus() && $('rainBox')) rainCheck(r);
  };
  function rerouteSheet() { var st = V.sheetEl.scrollTop; if (V.plan && V.cur) { V.drawRouteSheet(); V.sheetEl.scrollTop = st; } }
  function chargers(r) {
    var out = $('chargeOut'); out.innerHTML = '<div class="sub" style="margin-top:8px">Suche Ladestationen ...</div>';
    var la = r.la, lo = r.lo, s = Math.min.apply(null, la), n = Math.max.apply(null, la), w = Math.min.apply(null, lo), e = Math.max.apply(null, lo);
    fetch('https://nominatim.openstreetmap.org/search?format=jsonv2&limit=8&q=charging%20station&bounded=1&viewbox=' + (w - 0.03) + ',' + (n + 0.03) + ',' + (e + 0.03) + ',' + (s - 0.03))
      .then(function (x) { return x.json(); }).then(function (l) {
        if (!l.length) { out.innerHTML = '<div class="sub" style="margin-top:8px">Keine Ladestationen in der Nähe der Route gefunden. Tipp: Viele Cafés laden E-Bikes, einfach fragen.</div>'; return; }
        out.innerHTML = l.map(function (x, i) { return '<div class="row" data-ch="' + i + '"><div class="ico">' + svg('pin', '#12b886', 18) + '</div><div class="grow"><div class="t ell">' + esc(x.name || 'Ladestation') + '</div><div class="s ell">' + esc(String(x.display_name).split(',').slice(1, 3).join(',')) + '</div></div></div>'; }).join('');
        out.querySelectorAll('[data-ch]').forEach(function (b) { b.onclick = function () { var x = l[+b.getAttribute('data-ch')]; V.setFollow(false); V.view(+x.lat, +x.lon, 16); V.sheetEl.classList.add('mini'); }; });
      }).catch(function () { out.innerHTML = '<div class="sub" style="margin-top:8px">Für die Suche braucht die App Internet.</div>'; });
  }
  function rainCheck(r) {
    var box = $('rainBox'), now = Date.now(), pts = [0, 0.33, 0.66, 1].map(function (f) { var k = V.idxAt(r, r.len * f); return { la: r.la[k], lo: r.lo[k], km: r.cum[k], t: now + (r.tc[k] || 0) * 1000 }; });
    fetch('https://api.open-meteo.com/v1/forecast?latitude=' + pts.map(function (p) { return p.la.toFixed(3); }).join(',') + '&longitude=' + pts.map(function (p) { return p.lo.toFixed(3); }).join(',') + '&hourly=precipitation,precipitation_probability&forecast_days=2&timezone=auto&timeformat=unixtime')
      .then(function (x) { return x.json(); }).then(function (d) {
        var arr = Array.isArray(d) ? d : [d], warn = [];
        pts.forEach(function (p, i) {
          var h = arr[i] && arr[i].hourly; if (!h) return;
          var k = 0; while (k < h.time.length - 1 && h.time[k + 1] * 1000 <= p.t) k++;
          var mm = h.precipitation[k] || 0, pr = h.precipitation_probability ? h.precipitation_probability[k] || 0 : 0;
          if (mm >= 0.3 || pr >= 50) warn.push({ km: p.km, t: p.t, mm: mm, pr: pr });
        });
        if (!$('rainBox')) return;
        if (!warn.length) { box.className = 'pbox ok'; box.innerHTML = '<div class="t">' + svg('sun', '#0c9a68', 18) + 'Trocken unterwegs</div><div class="sub">Bis zur Ankunft um ' + V.clock(r.secs) + ' ist auf der Strecke kein Regen gemeldet.</div>'; }
        else { var w0 = warn[0]; box.className = 'pbox warn'; box.innerHTML = '<div class="t">' + svg('sun', '#e5463c', 18) + 'Regen möglich</div><div class="sub">Bei km ' + km(w0.km) + ' gegen ' + V.hhmm(new Date(w0.t)) + ': ' + w0.pr + ' % Regenwahrscheinlichkeit' + (w0.mm ? ', ' + num(w0.mm.toFixed(1)) + ' mm' : '') + '. Regenjacke einpacken oder früher losfahren.</div>'; hap('soft'); }
      }).catch(function () { if ($('rainBox')) box.querySelector('.sub').textContent = 'Wetter braucht Internet.'; });
  }

  // ---------- Gespeicherte Routen ----------
  var SAVED = V.load('saved', []);
  function saveRoute(r, plan) {
    if (SAVED.some(function (x) { return x.title === plan.title && Math.abs(x.len - r.len) < 50; })) { V.toast('Diese Route ist schon gespeichert'); return; }
    SAVED.unshift({ t: Date.now(), title: plan.title, len: r.len, asc: r.asc, secs: r.secs, opt: { loop: !!plan.opt.loop, tour: !!plan.opt.tour, style: plan.opt.style || S.style }, pts: plan.pts.map(function (p) { return { lat: p.lat, lon: p.lon, name: p.name || '' }; }) });
    V.store('saved', SAVED.slice(0, 50)); hap('success'); V.toast('Route gespeichert');
  }
  function showSaved() {
    V.state = 'saved';
    V.sheet('<h2>Gespeicherte Routen</h2>' + (SAVED.length ? SAVED.map(function (x, i) { return '<div class="row" data-sv="' + i + '"><div class="ico">' + svg(x.opt.loop ? 'loop' : 'flag', 'currentColor', 18) + '</div><div class="grow"><div class="t ell">' + esc(x.title) + '</div><div class="s">' + V.fmtDist(x.len) + ' &middot; ' + V.fmtTime(x.secs) + ' &middot; ' + Math.round(x.asc) + ' m bergauf</div></div><button class="chip sm" data-svdel="' + i + '">Löschen</button></div>'; }).join('') : '<div class="velo-card">' + mascot('think', 50) + '<div class="bubble"><span>Noch keine Routen. Plane eine Route und tippe auf "Speichern".</span></div></div>') +
      '<div class="btns"><button class="go sec" id="gpxIn">GPX importieren</button></div><input type="file" id="gpxFile" accept=".gpx,application/gpx+xml,application/xml,text/xml" style="display:none">' +
      '<div class="btns"><button class="go sec" id="svBack">Zurück</button></div>');
    V.sheetEl.querySelectorAll('[data-sv]').forEach(function (b) { b.onclick = function (e) { if (e.target.closest('[data-svdel]')) return; var x = SAVED[+b.getAttribute('data-sv')]; V.planRoute(x.pts, x.title, { picked: true, loop: x.opt.loop, tour: x.opt.tour, style: x.opt.style }); }; });
    V.sheetEl.querySelectorAll('[data-svdel]').forEach(function (b) { b.onclick = function () { SAVED.splice(+b.getAttribute('data-svdel'), 1); V.store('saved', SAVED); showSaved(); }; });
    $('gpxIn').onclick = function () { $('gpxFile').click(); };
    $('gpxFile').onchange = function () { var f = this.files[0]; if (!f) return; var rd = new FileReader(); rd.onload = function () { importGpx(String(rd.result), f.name); }; rd.readAsText(f); };
    $('svBack').onclick = function () { V.showMenu(); };
  }
  function exportGpx(r, title) {
    var x = '<?xml version="1.0" encoding="UTF-8"?>\n<gpx version="1.1" creator="Velora" xmlns="http://www.topografix.com/GPX/1/1">\n<trk><name>' + esc(title) + '</name><trkseg>\n';
    for (var i = 0; i < r.la.length; i++) x += '<trkpt lat="' + r.la[i].toFixed(6) + '" lon="' + r.lo[i].toFixed(6) + '"><ele>' + Math.round(r.ele[i]) + '</ele></trkpt>\n';
    x += '</trkseg></trk>\n</gpx>\n';
    var name = 'Velora-' + String(title).replace(/[^A-Za-z0-9äöüÄÖÜß]+/g, '-').slice(0, 40) + '.gpx';
    if (A && A.saveFile) { var p = A.saveFile(name, 'application/gpx+xml', x); if (p) { hap('success'); V.toast('Gespeichert: ' + p); return; } }
    try {
      var a = document.createElement('a'); a.href = URL.createObjectURL(new Blob([x], { type: 'application/gpx+xml' })); a.download = name; document.body.appendChild(a); a.click(); a.remove();
    } catch (e) {}
    V.sheet('<h2>GPX-Datei</h2><div class="sub">Falls kein Download startet: Text kopieren und als .gpx speichern.</div><textarea class="gpxta" readonly>' + esc(x) + '</textarea><div class="btns"><button class="go" id="gpxCopy">Kopieren</button><button class="go sec" id="gpxBack">Zurück</button></div>');
    $('gpxCopy').onclick = function () { try { navigator.clipboard.writeText(x).then(function () { V.toast('Kopiert'); }); } catch (e) {} };
    $('gpxBack').onclick = function () { V.planRoute(V.plan.pts, V.plan.title, Object.assign({}, V.plan.opt, { picked: true })); };
  }
  function importGpx(text, fname) {
    try {
      var doc = new DOMParser().parseFromString(text, 'application/xml'), q = doc.getElementsByTagName('trkpt');
      if (!q.length) q = doc.getElementsByTagName('rtept');
      if (!q.length) q = doc.getElementsByTagName('wpt');
      var all = []; for (var i = 0; i < q.length; i++) all.push({ lat: +q[i].getAttribute('lat'), lon: +q[i].getAttribute('lon') });
      all = all.filter(function (p) { return isFinite(p.lat) && isFinite(p.lon); });
      if (all.length < 2) { V.toast('In der Datei ist keine Strecke'); return; }
      var nm = doc.getElementsByTagName('name')[0], title = nm ? nm.textContent.trim() : fname.replace(/\.gpx$/i, '');
      var n = Math.min(12, all.length), pts = [];
      for (var k = 0; k < n; k++) pts.push(all[Math.round(k * (all.length - 1) / (n - 1))]);
      pts[0].name = 'GPX-Start'; pts[pts.length - 1].name = title || 'GPX-Ziel';
      var loop = V.dist(pts[0].lat, pts[0].lon, pts[n - 1].lat, pts[n - 1].lon) < 200;
      hap('success');
      V.planRoute(pts, title || 'GPX-Strecke', { picked: true, tour: true, loop: loop, desc: 'Aus GPX importiert (' + all.length + ' Punkte). Die App fährt die Strecke über ' + n + ' Punkte nach.' });
    } catch (e) { V.toast('GPX konnte nicht gelesen werden'); }
  }

  // =====================================================================
  // Navigation: Pfeile auf der Route, gefahrener Teil grau, Reisefuehrer, Live teilen
  // =====================================================================
  var curR = null, lastDone = 0, mapOk = false, mapTries = 0;
  function mapInit() {
    if (!V.mapReady || !map.getLayer || !map.getLayer('route')) { if (++mapTries < 100) setTimeout(mapInit, 300); return; }
    try {
      var c = document.createElement('canvas'); c.width = c.height = 48; var g = c.getContext('2d');
      g.lineCap = 'round'; g.lineJoin = 'round';
      g.strokeStyle = 'rgba(13,43,62,.55)'; g.lineWidth = 11; g.beginPath(); g.moveTo(16, 12); g.lineTo(30, 24); g.lineTo(16, 36); g.stroke();
      g.strokeStyle = '#ffffff'; g.lineWidth = 6; g.beginPath(); g.moveTo(16, 12); g.lineTo(30, 24); g.lineTo(16, 36); g.stroke();
      map.addImage('chev', g.getImageData(0, 0, 48, 48), { pixelRatio: 2 });
      var ls = map.getStyle().layers, i = ls.findIndex(function (l) { return l.id === 'route'; }), before = ls[i + 1] ? ls[i + 1].id : undefined;
      map.addSource('rDone', { type: 'geojson', data: V.fc() });
      map.addSource('rAhead', { type: 'geojson', data: V.fc() });
      map.addLayer({ id: 'rDone', type: 'line', source: 'rDone', layout: { 'line-cap': 'round', 'line-join': 'round' }, paint: { 'line-color': '#a7b1bc', 'line-width': ['interpolate', ['exponential', 1.5], ['zoom'], 10, 2.4, 14, 7.5, 18, 16] } }, before);
      map.addLayer({ id: 'rArrows', type: 'symbol', source: 'rAhead', layout: { 'symbol-placement': 'line', 'symbol-spacing': ['interpolate', ['linear'], ['zoom'], 12, 50, 17, 90], 'icon-image': 'chev', 'icon-size': ['interpolate', ['linear'], ['zoom'], 11, 0.35, 15, 0.6, 18, 0.85], 'icon-allow-overlap': true, 'icon-ignore-placement': true, 'icon-rotation-alignment': 'map', 'icon-keep-upright': false } }, before);
      mapOk = true;
      if (curR) VX.routeDrawn(curR);
    } catch (e) { if (window.console) console.error(e); }
  }
  mapInit();
  function lineOf(r, a, b) { var c = []; for (var i = a; i <= b && i < r.la.length; i++) c.push([r.lo[i], r.la[i]]); return c; }
  VX.routeDrawn = function (r) {
    curR = r; lastDone = 0;
    if (!mapOk) return;
    map.getSource('rDone').setData(V.fc());
    map.getSource('rAhead').setData(r ? { type: 'Feature', properties: {}, geometry: { type: 'LineString', coordinates: lineOf(r, 0, r.la.length - 1) } } : V.fc());
  };
  var lastTick = 0, guideT = 0, told = {}, liveT = 0;
  VX.navTick = function (r, along, ref) {
    var now = Date.now();
    if (mapOk && r && now - lastTick > 1200 && Math.abs(along - lastDone) > 5) {
      lastTick = now; lastDone = along;
      var k = V.idxAt(r, along), done = lineOf(r, 0, k);
      if (done.length > 1) map.getSource('rDone').setData({ type: 'Feature', properties: {}, geometry: { type: 'LineString', coordinates: done } });
      map.getSource('rAhead').setData({ type: 'Feature', properties: {}, geometry: { type: 'LineString', coordinates: lineOf(r, Math.max(0, k - 1), r.la.length - 1) } });
    }
    if (ref) { guideCheck(ref); liveSend(ref, r, along); }
  };
  VX.navStop = function () { liveStop(); var g = $('guide'); if (g) g.remove(); if (mapOk) map.getSource('rDone').setData(V.fc()); };
  VX.arrived = function (plan) { hap('success'); };
  VX.navSheet = function () {
    if (!V.nav || V.nav.free) return;
    var b = document.createElement('button'); b.className = 'go sec'; b.id = 'liveBtn'; b.style.cssText = 'height:42px;font-size:15px;margin-top:8px';
    b.innerHTML = live ? 'Live-Standort wird geteilt' : 'Live-Standort teilen' + (isPlus() ? '' : TAG);
    V.sheetEl.appendChild(b);
    b.onclick = function () { if (gate('Live-Standort teilen')) liveStart(); };
  };

  // ---------- Juno als Reisefuehrer (Wikipedia, kostet kein Juno-Kontingent) ----------
  function guideCheck(ref) {
    if (!isPlus() || !S.guide || V.lastSpeed < 4) return;
    var now = Date.now(); if (now - guideT < 90000) return; guideT = now;
    fetch('https://de.wikipedia.org/w/api.php?action=query&format=json&origin=*&generator=geosearch&ggscoord=' + ref.lat.toFixed(5) + '|' + ref.lon.toFixed(5) + '&ggsradius=700&ggslimit=6&prop=extracts&exintro=1&explaintext=1&exsentences=2')
      .then(function (x) { return x.json(); }).then(function (d) {
        var pages = d.query && d.query.pages; if (!pages) return;
        var list = Object.keys(pages).map(function (k) { return pages[k]; }).filter(function (p) { return !told[p.pageid] && p.extract && p.extract.length > 60; }).sort(function (a, b) { return a.index - b.index; });
        var p = list[0]; if (!p) return;
        told[p.pageid] = 1;
        var txt = p.extract.replace(/\s*\([^)]*\)/g, '').replace(/\s+/g, ' ').trim();
        showGuide(p.title, txt);
        if (S.voice) V.say('Du fährst gerade an ' + p.title + ' vorbei. ' + txt);
      }).catch(function () {});
  }
  function showGuide(title, txt) {
    var g = $('guide'); if (g) g.remove();
    g = document.createElement('div'); g.id = 'guide';
    g.innerHTML = mascot('wave', 40) + '<div class="grow"><b>' + esc(title) + '</b><span>' + esc(txt) + '</span></div>';
    document.body.appendChild(g); hap('soft');
    g.onclick = function () { g.remove(); };
    setTimeout(function () { if (g.parentNode) { g.style.transition = 'opacity .4s'; g.style.opacity = 0; setTimeout(function () { g.remove(); }, 400); } }, 15000);
  }

  // ---------- Live-Standort teilen ----------
  var live = V.load('live', null);
  if (live && Date.now() - live.t > 3 * 3600e3) { live = null; V.store('live', null); }
  function liveStart() {
    if (!live) { live = { id: rnd(12), key: rnd(28), t: Date.now() }; V.store('live', live); }
    var url = KI_BASE + '/l/' + live.id;
    liveT = 0; if (V.meRef()) liveSend(V.meRef(), V.cur, V.nav ? V.nav.lastAlong : 0);
    modal('<div style="display:flex;align-items:center;gap:12px">' + mascot('wave', 56) + '<div class="grow"><h2>Live teilen</h2><div class="sub">Wer den Link hat, sieht während dieser Fahrt deinen Standort. Er endet mit der Fahrt.</div></div></div>' +
      '<div class="pbox"><div class="t" style="word-break:break-all;font-size:14px">' + esc(url) + '</div></div>' +
      '<div class="btns"><button class="go" id="liveShare">Link teilen</button><button class="go sec" id="liveCopy">Kopieren</button></div>' +
      '<div class="btns"><button class="go sec" id="liveBack">Zurück zur Navigation</button></div>');
    $('liveShare').onclick = function () {
      var t = 'Hier kannst du sehen, wo ich gerade mit dem Rad fahre: ' + url;
      if (A && A.shareText) A.shareText('Live-Standort teilen', t);
      else if (navigator.share) navigator.share({ title: 'Velora Live', text: t, url: url }).catch(function () {});
      else { try { navigator.clipboard.writeText(url); V.toast('Link kopiert'); } catch (e) {} }
    };
    $('liveCopy').onclick = function () { try { navigator.clipboard.writeText(url).then(function () { V.toast('Link kopiert'); }); } catch (e) { V.toast(url); } };
    $('liveBack').onclick = closeModal;
  }
  function liveSend(ref, r, along) {
    if (!live || Date.now() - liveT < 60000) return;
    liveT = Date.now();
    var body = { id: live.id, key: live.key, lat: +ref.lat.toFixed(5), lon: +ref.lon.toFixed(5), spd: V.lastSpeed > 0 ? Math.round(V.lastSpeed) : 0 };
    if (r && V.plan) { body.rest = Math.max(0, Math.round(r.len - along)); body.ziel = V.plan.title; var k = V.idxAt(r, along); body.eta = Date.now() + Math.max(0, r.secs - (r.tc[k] || 0)) * 1000; }
    fetch(KI_BASE + '/live', { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-App-Id': KI_ID }, body: JSON.stringify(body) }).catch(function () {});
  }
  function liveStop() {
    if (!live) return;
    fetch(KI_BASE + '/live', { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-App-Id': KI_ID }, body: JSON.stringify({ id: live.id, key: live.key, ende: true }) }).catch(function () {});
    live = null; V.store('live', null);
  }

  // =====================================================================
  // Standort-Pfeil: 3D-Pfeil, Blickkegel, weiches Drehen, Kamera kippt beim Fahren
  // =====================================================================
  var ARROWS = {
    '3d': ['3D-Pfeil', '<svg viewBox="0 0 50 54"><path d="M25 3 L45 49 L25 39 Z" style="fill:var(--acc)"/><path d="M25 3 L5 49 L25 39 Z" style="fill:var(--lime)"/><path d="M25 3 L45 49 L25 39 L5 49 Z" fill="none" stroke="#fff" stroke-width="3.5" stroke-linejoin="round"/></svg>'],
    pfeil: ['Klassisch', '<svg viewBox="0 0 50 54"><path d="M25 4 L44 46 L25 37 L6 46 Z" style="fill:var(--acc)" stroke="#fff" stroke-width="4" stroke-linejoin="round"/></svg>'],
    dart: ['Pfeilspitze', '<svg viewBox="0 0 50 54"><defs><linearGradient id="aD" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#ffb35c"/><stop offset="1" stop-color="#f76707"/></linearGradient></defs><path d="M25 2 L37 50 L25 43 L13 50 Z" fill="url(#aD)" stroke="#fff" stroke-width="3.5" stroke-linejoin="round"/></svg>'],
    rad: ['Velo', '<svg viewBox="0 0 50 54"><path d="M25 1 L33 15 H17 Z" style="fill:var(--acc)" stroke="#fff" stroke-width="2.5" stroke-linejoin="round"/><circle cx="25" cy="32" r="17" fill="#0d2b3e" stroke="#fff" stroke-width="3"/><circle cx="25" cy="32" r="11" style="fill:var(--lime)"/><circle cx="21" cy="30" r="2.6" fill="#0d2b3e"/><circle cx="29" cy="30" r="2.6" fill="#0d2b3e"/><path d="M20.5 35.5 Q25 39 29.5 35.5" stroke="#0d2b3e" stroke-width="2" fill="none" stroke-linecap="round"/></svg>']
  };
  var rotNow = 0, upgraded = null;
  function rotTo(deg) {
    var d = ((deg - rotNow) % 360 + 540) % 360 - 180; rotNow += d;
    var el = upgraded && upgraded.querySelector('.me-rot'); if (el) el.style.transform = 'rotate(' + rotNow + 'deg)';
  }
  function upgradeMe() {
    var mk = V.meMarker(); if (!mk) return;
    var el = mk.getElement();
    if (upgraded !== el) {
      upgraded = el; el.classList.add('me2');
      var orig = mk.setRotation.bind(mk); orig(0);
      mk.setRotation = function (r) { rotTo(r); return mk; };
      el.innerHTML = '<div class="me-rot"><div class="cone"></div><div id="meArrow" style="display:none">' + (ARROWS[S.arrow] || ARROWS['3d'])[1] + '</div></div><div class="dot"></div>';
    }
    var me = V.me; if (!me) return;
    var arrow = $('meArrow'), moving = arrow && arrow.style.display === 'block';
    if (me.brg >= 0) rotTo(me.brg);
    var cone = el.querySelector('.cone'); if (cone) cone.style.opacity = !moving && me.brg >= 0 ? 1 : 0;
  }
  function setArrowSkin() { if (upgraded) { var a = $('meArrow'); if (a) a.innerHTML = (ARROWS[S.arrow] || ARROWS['3d'])[1]; } }
  setInterval(upgradeMe, 400);
  if (!S.hu2) { S.headUp = true; S.hu2 = true; V.saveS(); } // beim Navigieren dreht und kippt die Karte mit (wie im Auto-Navi)

  // =====================================================================
  // Touren: Rueckenwind auf dem Heimweg (Plus)
  // =====================================================================
  function tailShare(r) {
    var WX = V.WX; if (!WX || WX.wind == null || !r.la) return null;
    var half = r.len / 2, sum = 0, tot = 0, to = (WX.dir + 180) % 360;
    for (var i = 1; i < r.la.length; i++) {
      if (r.cum[i] < half) continue;
      var L = r.cum[i] - r.cum[i - 1]; if (L <= 0) continue;
      sum += Math.cos((V.bearing(r.la[i - 1], r.lo[i - 1], r.la[i], r.lo[i]) - to) * Math.PI / 180) * L; tot += L;
    }
    return tot ? sum / tot : null;
  }
  VX.loops = function (res) {
    if (!res || !isPlus() || !V.WX) return res;
    res.forEach(function (x) {
      var t = tailShare(x.r); x.tail = t;
      if (t != null && V.WX.wind >= 8) x.why = (x.why ? x.why + ' · ' : '') + (t > 0.25 ? 'Heimweg mit Rückenwind' : t < -0.25 ? 'Heimweg mit Gegenwind' : 'Heimweg mit Seitenwind');
    });
    if (S.tailwind) res.sort(function (a, b) { return (b.tail || 0) - (a.tail || 0); });
    return res;
  };
  VX.toursHtml = function () {
    return '<div class="opt" id="twRow"><div class="grow">Rückenwind auf dem Heimweg' + (isPlus() ? '' : TAG) + '<div class="sub">' + (V.WX ? 'Wind ' + Math.round(V.WX.wind) + ' km/h' : 'Braucht Wetterdaten (Internet)') + '</div></div><div class="sw' + (S.tailwind && isPlus() ? ' on' : '') + '" id="swTail"></div></div>';
  };
  VX.toursBound = function () {
    var s = $('swTail'); if (!s) return;
    s.onclick = function () { if (!gate('Rückenwind-Touren')) return; S.tailwind = !S.tailwind; V.saveS(); s.classList.toggle('on', S.tailwind); };
  };

  // =====================================================================
  // Tagesplaner (Plus): Aussicht, Essen, Baden als eine Rundtour mit Zeitplan
  // =====================================================================
  var DP = V.load('dayplan', { h: 5, start: 10, want: { view: 1, food: 1, beach: 1, cafe: 0 } });
  function showDayPlan() {
    if (!gate('Der Tagesplaner')) return;
    V.state = 'dayplan';
    var W = [['view', 'Aussicht'], ['food', 'Mittagessen'], ['beach', 'Baden'], ['cafe', 'Kaffeepause']];
    V.sheet('<div style="display:flex;align-items:center;gap:12px">' + mascot('think', 60) + '<div class="grow"><h2>Tagesplaner</h2><div class="sub">Velo plant dir einen ganzen Tag als Rundtour, mit Pausen.</div></div></div>' +
      '<h3>Wie lange?</h3><div class="seg">' + [3, 5, 7].map(function (h) { return '<button data-dph="' + h + '" class="' + (DP.h === h ? 'on' : '') + '">' + h + ' Stunden</button>'; }).join('') + '</div>' +
      '<h3>Start</h3><div class="seg">' + [8, 9, 10, 11, 13].map(function (h) { return '<button data-dps="' + h + '" class="' + (DP.start === h ? 'on' : '') + '">' + h + ':00</button>'; }).join('') + '</div>' +
      '<h3>Was willst du erleben?</h3><div class="hscroll" style="flex-wrap:wrap;overflow:visible">' + W.map(function (w) { return '<button class="chip' + (DP.want[w[0]] ? ' on' : '') + '" data-dpw="' + w[0] + '">' + w[1] + '</button>'; }).join('') + '</div>' +
      '<div class="btns"><button class="go" id="dpGo">Tag planen</button></div><div class="btns"><button class="go sec" id="dpJuno">Juno um Ideen fragen</button></div><div id="dpOut"></div>' +
      '<div class="btns"><button class="go sec" id="dpBack">Zurück</button></div>');
    V.sheetEl.querySelectorAll('[data-dph]').forEach(function (b) { b.onclick = function () { DP.h = +b.getAttribute('data-dph'); V.store('dayplan', DP); showDayPlan(); }; });
    V.sheetEl.querySelectorAll('[data-dps]').forEach(function (b) { b.onclick = function () { DP.start = +b.getAttribute('data-dps'); V.store('dayplan', DP); showDayPlan(); }; });
    V.sheetEl.querySelectorAll('[data-dpw]').forEach(function (b) { b.onclick = function () { var k = b.getAttribute('data-dpw'); DP.want[k] = DP.want[k] ? 0 : 1; V.store('dayplan', DP); showDayPlan(); }; });
    $('dpGo').onclick = planDay;
    $('dpJuno').onclick = function () { V.showJuno(); V.askJuno('Plane mir einen schönen Radtag von ' + DP.h + ' Stunden ab ' + DP.start + ' Uhr hier in der Gegend, mit ' + W.filter(function (w) { return DP.want[w[0]]; }).map(function (w) { return w[1]; }).join(', ') + '. Nenne echte Orte mit Uhrzeiten.'); };
    $('dpBack').onclick = function () { V.showMenu(); };
  }
  function planDay() {
    var s = V.startRef();
    if (!V.onIsland(s.lat, s.lon)) { $('dpOut').innerHTML = '<div class="pbox"><div class="sub">Der Tagesplaner rechnet zurzeit auf Ugljan. Für andere Orte frag Juno, sie kennt die Gegend.</div></div>'; return; }
    var STOP = { view: [['view', 'peak'], 20, 'Aussicht'], food: [['food'], 70, 'Mittagessen'], beach: [['beach'], 90, 'Baden'], cafe: [['cafe'], 30, 'Kaffeepause'] };
    var keys = Object.keys(STOP).filter(function (k) { return DP.want[k]; });
    if (!keys.length) { V.toast('Wähle mindestens einen Stopp'); return; }
    var budget = DP.h * 60, pause = keys.reduce(function (a, k) { return a + STOP[k][1]; }, 0), rideMin = Math.max(40, budget - pause);
    var radius = Math.min(14000, rideMin / 60 * 13000 / 2.4);
    var best = null;
    for (var tryR = radius; tryR > 1500 && !best; tryR *= 0.75) {
      var stops = [], used = {};
      keys.forEach(function (k, ki) {
        var ang = ki / keys.length * Math.PI * 2 + 0.6, tgt = { lat: s.lat + Math.cos(ang) * tryR * 0.7 / 110540, lon: s.lon + Math.sin(ang) * tryR * 0.7 / (111320 * Math.cos(s.lat * Math.PI / 180)) };
        var cand = V.D.pois.map(function (p, i) { return { p: p, i: i }; }).filter(function (x) { return STOP[k][0].indexOf(x.p[0]) >= 0 && !used[x.i] && V.dist(s.lat, s.lon, x.p[1], x.p[2]) < tryR; });
        cand.sort(function (a, b) { return V.dist(tgt.lat, tgt.lon, a.p[1], a.p[2]) - V.dist(tgt.lat, tgt.lon, b.p[1], b.p[2]) - (a.p[3] ? 300 : 0) + (b.p[3] ? 300 : 0); });
        if (cand[0]) { used[cand[0].i] = 1; stops.push({ k: k, p: cand[0].p }); }
      });
      if (!stops.length) continue;
      stops.sort(function (a, b) { return Math.atan2(a.p[2] - s.lon, a.p[1] - s.lat) - Math.atan2(b.p[2] - s.lon, b.p[1] - s.lat); });
      if (DP.want.food) { var fi = stops.findIndex(function (x) { return x.k === 'food'; }); if (fi >= 0 && stops.length > 2 && fi !== Math.floor(stops.length / 2)) { var f = stops.splice(fi, 1)[0]; stops.splice(Math.floor(stops.length / 2), 0, f); } }
      var pts = [{ lat: s.lat, lon: s.lon, name: s.name }].concat(stops.map(function (x) { return { lat: x.p[1], lon: x.p[2], name: x.p[3] || V.CAT[x.p[0]].n }; })).concat([{ lat: s.lat, lon: s.lon, name: s.name }]);
      var r = V.routeVia(pts, { loop: true, style: 'scenic' });
      if (r && r.secs / 60 <= rideMin * 1.15) best = { r: r, pts: pts, stops: stops };
    }
    if (!best) { $('dpOut').innerHTML = '<div class="pbox warn"><div class="sub">Dafür reicht die Zeit nicht. Nimm mehr Stunden oder weniger Stopps.</div></div>'; return; }
    var t = DP.start * 60, plan = [V.hhmm(dt(t)) + ' Start ' + best.pts[0].name], r = best.r;
    best.stops.forEach(function (x, i) {
      var ride = (r.tc[V.idxAt(r, r.legEnds[i])] || 0) - (i ? r.tc[V.idxAt(r, r.legEnds[i - 1])] || 0 : 0);
      t += ride / 60; plan.push(V.hhmm(dt(t)) + ' ' + STOP[x.k][2] + ': ' + (best.pts[i + 1].name) + ' (' + STOP[x.k][1] + ' min)'); t += STOP[x.k][1];
    });
    t += (r.secs - (r.tc[V.idxAt(r, r.legEnds[best.stops.length - 1])] || 0)) / 60; plan.push(V.hhmm(dt(t)) + ' zurück in ' + best.pts[0].name);
    hap('success');
    V.planRoute(best.pts, 'Tagesplan ' + DP.h + ' h', { picked: true, loop: true, tour: true, pre: r, style: 'scenic', desc: plan.join('  ·  ') });
  }
  function dt(min) { var d = new Date(); d.setHours(0, 0, 0, 0); return new Date(d.getTime() + min * 60000); }

  // =====================================================================
  // Offline-Karten (Plus, Android): Ausschnitt fuer Reisen ohne Internet laden
  // =====================================================================
  function tilesFor(b, z0, z1) {
    var out = [];
    function x2(lon, z) { return Math.floor((lon + 180) / 360 * Math.pow(2, z)); }
    function y2(lat, z) { var r = lat * Math.PI / 180; return Math.floor((1 - Math.log(Math.tan(r) + 1 / Math.cos(r)) / Math.PI) / 2 * Math.pow(2, z)); }
    for (var z = z0; z <= z1; z++) for (var x = x2(b.getWest(), z); x <= x2(b.getEast(), z); x++) for (var y = y2(b.getNorth(), z); y <= y2(b.getSouth(), z); y++) out.push([z, x, y]);
    return out;
  }
  function showOffline() {
    if (!gate('Offline-Karten')) return;
    V.state = 'offline';
    var info = A && A.tileInfo ? A.tileInfo().split(',') : null;
    V.sheet('<div style="display:flex;align-items:center;gap:12px">' + mascot('idle', 56) + '<div class="grow"><h2>Offline-Karten</h2><div class="sub">Ugljan ist immer offline dabei. Für andere Gegenden: Karte auf den Bereich schieben und speichern.</div></div></div>' +
      (A && A.downloadTiles ? '<div class="pbox"><div class="t">Gespeichert</div><div class="sub">' + (info ? info[0] + ' Kartenteile, ' + num((info[1] / 1048576).toFixed(1)) + ' MB' : '-') + '</div></div>' +
        '<div class="btns"><button class="go" id="dlGo">Sichtbaren Bereich speichern</button></div><div id="dlBox"></div><div class="btns"><button class="go sec" id="dlClear">Alle löschen</button></div>' +
        '<div class="sub" style="font-size:12px;margin-top:8px">Gespeichert werden Karte, Relief und Radrouten bis Zoomstufe 14. Routen außerhalb von Ugljan brauchen weiterhin Internet.</div>'
        : '<div class="pbox"><div class="sub">Offline-Karten gibt es in der Android-App.</div></div>') +
      '<div class="btns"><button class="go sec" id="dlBack">Zurück</button></div>');
    $('dlBack').onclick = function () { V.showMenu(); };
    if (!$('dlGo')) return;
    $('dlClear').onclick = function () { A.clearTiles(); showOffline(); };
    $('dlGo').onclick = function () {
      var b = map.getBounds(), z1 = 14, list;
      while ((list = tilesFor(b, 6, z1)).length > 6000 && z1 > 10) z1--;
      if (list.length > 6000) { V.toast('Bereich zu groß, bitte weiter hineinzoomen'); return; }
      var src = map.getSource('ofm'), ofm = src && src.tiles && src.tiles[0], urls = [];
      if (src && src.url) urls.push(src.url);
      list.forEach(function (t) {
        function f(tpl) { return tpl.replace('{z}', t[0]).replace('{x}', t[1]).replace('{y}', t[2]); }
        if (ofm) urls.push(f(ofm));
        urls.push(f('https://s3.amazonaws.com/elevation-tiles-prod/terrarium/{z}/{x}/{y}.png'));
        if (t[0] >= 8) urls.push(f('https://tile.waymarkedtrails.org/cycling/{z}/{x}/{y}.png'));
      });
      if (!ofm) { V.toast('Bitte erst die Online-Karte laden (Internet an)'); return; }
      $('dlBox').innerHTML = '<div class="pbox"><div class="t" id="dlT">Lade ' + urls.length + ' Kartenteile ...</div><div class="meter"><i id="dlBar" style="width:0;background:var(--lime)"></i></div><button class="go sec" id="dlStop" style="height:40px;font-size:14px">Abbrechen</button></div>';
      $('dlStop').onclick = function () { A.stopDownload(); };
      A.downloadTiles(JSON.stringify(urls));
    };
  }
  VX.dlProgress = function (done, total, fail, end) {
    var bar = $('dlBar'), t = $('dlT');
    if (bar) bar.style.width = (done / Math.max(1, total) * 100) + '%';
    if (t) t.textContent = end ? (done >= total ? 'Fertig: Bereich ist offline verfügbar' : 'Abgebrochen') + (fail ? ' (' + fail + ' Fehler)' : '') : done + ' von ' + total + ' Kartenteilen';
    if (end && done >= total) hap('success');
  };

  // =====================================================================
  // Menue: Plus, Statistik, Einstellungen fuer Haptik, Design, Pfeil
  // =====================================================================
  var THEMES = [['mint', 'Minze', '#2fd59a'], ['ocean', 'Ozean', '#5aa9ff'], ['sunset', 'Abendrot', '#ffa94d'], ['lavender', 'Lavendel', '#b197fc'], ['forest', 'Wald', '#69db7c']];
  VX.menuTop = function () {
    return (isPlus() ? '<div class="plusbar" data-act="plus"><div class="ico">' + svg('flag', '#6b4dff', 18) + '</div><div class="grow"><b>Velora Plus ist aktiv</b><span>Alle Funktionen freigeschaltet</span></div></div>'
      : '<div class="plusbar" data-act="plus">' + mascot('cheer', 46) + '<div class="grow"><b>Velora Plus</b><span>Tagebuch, Abzeichen, Offline-Karten, Juno ohne Limit und mehr</span></div><button class="go plus" style="width:auto;padding:0 14px;height:40px;font-size:14px">Ansehen</button></div>') +
      '<div class="linkrow"><button data-act="stats">' + svg('list', 'currentColor', 18) + 'Statistik</button><button id="mSaved">' + svg('flag', 'currentColor', 18) + 'Meine Routen</button>' +
      '<button data-act="dayplan">' + svg('loop', 'currentColor', 18) + 'Tagesplaner</button><button id="mOffline">' + svg('road', 'currentColor', 18) + 'Offline-Karten</button></div>';
  };
  VX.menuNav = function () {
    var plus = isPlus();
    return '<div class="opt"><div class="grow">Vibration<div class="sub">Beim Tippen, Abbiegen (links 2x, rechts 3x) und bei Erfolgen</div></div></div>' +
      '<div class="seg" style="margin-top:0">' + [[0, 'Aus'], [1, 'Leicht'], [2, 'Stark']].map(function (x) { return '<button data-hap="' + x[0] + '" class="' + (S.hap === x[0] ? 'on' : '') + '">' + x[1] + '</button>'; }).join('') + '</div>' +
      '<div class="opt"><div class="grow">Nachtmodus<div class="sub">Dunkles Design, automatisch nach Sonnenuntergang</div></div></div>' +
      '<div class="seg" style="margin-top:0">' + [['off', 'Aus'], ['auto', 'Automatisch'], ['on', 'An']].map(function (x) { return '<button data-night="' + x[0] + '" class="' + (S.night === x[0] ? 'on' : '') + '">' + x[1] + '</button>'; }).join('') + '</div>' +
      '<div class="opt"><div class="grow">Farbe' + (plus ? '' : TAG) + '</div></div><div class="hscroll" style="margin-top:0">' + THEMES.map(function (t, i) { return '<button class="chip' + (S.theme === t[0] ? ' on' : '') + '" data-theme="' + t[0] + '"><i style="width:12px;height:12px;border-radius:50%;background:' + t[2] + ';display:inline-block"></i>' + t[1] + '</button>'; }).join('') + '</div>' +
      '<div class="opt"><div class="grow">Navigationspfeil' + (plus ? '' : TAG) + '</div></div><div class="hscroll" style="margin-top:0">' + Object.keys(ARROWS).map(function (k) { return '<button class="chip' + (S.arrow === k ? ' on' : '') + '" data-arrow="' + k + '"><span style="width:18px;height:20px;display:inline-block">' + ARROWS[k][1] + '</span>' + ARROWS[k][0] + '</button>'; }).join('') + '</div>' +
      '<div class="opt"><div class="grow">Juno als Reiseführer' + (plus ? '' : TAG) + '<div class="sub">Erzählt unterwegs über Orte, an denen du vorbeifährst</div></div>' + '<div class="sw' + (S.guide && plus ? ' on' : '') + '" id="swGuide"></div></div>' +
      (S.bike === 'road' ? '<div class="opt"><div class="grow">Rennrad: maximale Steigung' + (plus ? '' : TAG) + '</div></div><div class="seg" style="margin-top:0">' + [[0, 'Egal'], [0.06, '6 %'], [0.08, '8 %'], [0.1, '10 %']].map(function (x) { return '<button data-maxg="' + x[0] + '" class="' + (S.maxG === x[0] ? 'on' : '') + '">' + x[1] + '</button>'; }).join('') + '</div>' : '');
  };
  VX.menuBound = function () {
    var el = V.sheetEl;
    el.querySelectorAll('[data-mbike]').forEach(function (b) { var k = b.getAttribute('data-mbike'); if (V.BIKES[k].pro && !isPlus()) b.insertAdjacentHTML('beforeend', TAG); });
    el.querySelectorAll('[data-hap]').forEach(function (b) { b.onclick = function () { S.hap = +b.getAttribute('data-hap'); V.saveS(); hap('success'); V.showMenu(); }; });
    el.querySelectorAll('[data-night]').forEach(function (b) { b.onclick = function () { S.night = b.getAttribute('data-night'); V.saveS(); applyLook(); V.showMenu(); }; });
    el.querySelectorAll('[data-theme]').forEach(function (b) { b.onclick = function () { var t = b.getAttribute('data-theme'); if (t !== 'mint' && !gate('Farbthemen')) return; S.theme = t; V.saveS(); applyLook(); V.showMenu(); }; });
    el.querySelectorAll('[data-arrow]').forEach(function (b) { b.onclick = function () { var a = b.getAttribute('data-arrow'); if (a !== '3d' && !gate('Navigationspfeile')) return; S.arrow = a; V.saveS(); setArrowSkin(); V.showMenu(); }; });
    el.querySelectorAll('[data-maxg]').forEach(function (b) { b.onclick = function () { if (!gate('Die Steigungsgrenze')) return; S.maxG = +b.getAttribute('data-maxg'); V.saveS(); V.computeCosts(); V.showMenu(); }; });
    if ($('swGuide')) $('swGuide').onclick = function () { if (!gate('Juno als Reiseführer')) return; S.guide = !S.guide; V.saveS(); this.classList.toggle('on', S.guide); };
    if ($('mSaved')) $('mSaved').onclick = function () { if (gate('Routen speichern')) showSaved(); };
    if ($('mOffline')) $('mOffline').onclick = showOffline;
  };
  VX.back = function () {
    if ($('cele')) { $('cele').querySelector('#celeOk').click(); return true; }
    if ($('vxm')) { closeModal(); return true; }
    var st = V.state;
    if (st === 'plus' || st === 'saved' || st === 'dayplan' || st === 'offline') { V.showMenu(); return true; }
    if (st === 'stats') { V.drawHome(); return true; }
    return false;
  };

  // =====================================================================
  // Aussehen: Farbthema, Nachtmodus, Startbildschirm, weiche Blaetter
  // =====================================================================
  function isNight() { try { var t = V.sunTimes(new Date()), n = Date.now(); return n < t.rise.getTime() - 15 * 60000 || n > t.set.getTime() + 20 * 60000; } catch (e) { return false; } }
  function applyLook() {
    var theme = isPlus() || S.theme === 'mint' ? S.theme : 'mint';
    if (theme === 'mint') document.body.removeAttribute('data-theme'); else document.body.setAttribute('data-theme', theme);
    var night = S.night === 'on' || (S.night === 'auto' && isNight());
    document.body.classList.toggle('night', night);
    var tc = document.querySelector('meta[name=theme-color]'); if (tc) tc.content = night ? '#0f1720' : '#ffffff';
  }
  applyLook(); setInterval(applyLook, 60000);

  // Blatt: Inhalte gleiten weich herein, beim Laden faehrt Velo Rad
  var lastState = V.state, enterT = null;
  new MutationObserver(function () {
    var st = V.state;
    if (st !== lastState && st !== 'nav') {
      V.sheetEl.classList.remove('enter'); void V.sheetEl.offsetWidth; V.sheetEl.classList.add('enter');
      clearTimeout(enterT); enterT = setTimeout(function () { V.sheetEl.classList.remove('enter'); }, 700);
    }
    lastState = st;
    if (st === 'loading' && !V.sheetEl.querySelector('.m-load')) V.sheetEl.insertAdjacentHTML('beforeend', '<div class="m-load">' + mascot('ride', 74) + '</div><div class="skel"></div><div class="skel s2"></div>');
  }).observe(V.sheetEl, { childList: true });

  // Blatt mit dem Finger hoch- und runterziehen
  (function () {
    var y0 = null, dy = 0, el = V.sheetEl;
    el.addEventListener('touchstart', function (e) { if (!e.target.closest('.grab') && !(el.scrollTop <= 0 && e.touches[0].clientY - el.getBoundingClientRect().top < 56)) return; y0 = e.touches[0].clientY; dy = 0; el.classList.add('drag'); }, { passive: true });
    el.addEventListener('touchmove', function (e) { if (y0 == null) return; dy = e.touches[0].clientY - y0; el.style.transform = 'translateY(' + (dy > 0 ? dy : dy / 4) + 'px)'; }, { passive: true });
    el.addEventListener('touchend', function () {
      if (y0 == null) return; y0 = null; el.classList.remove('drag'); el.style.transform = '';
      if (dy > 60) { el.classList.add('mini'); el.classList.remove('tall'); hap('tick'); }
      else if (dy < -50) { if (el.classList.contains('mini')) el.classList.remove('mini'); else el.classList.add('tall'); hap('tick'); }
      el.scrollTop = 0; window.dispatchEvent(new Event('resize'));
    });
  })();

  // Startbildschirm ausblenden
  (function () {
    var sp = $('splash'); if (!sp) return;
    var m = sp.querySelector('.sm'); if (m) m.innerHTML = mascot('wave', 110);
    setTimeout(function () { sp.classList.add('out'); setTimeout(function () { sp.remove(); }, 600); }, TESTER ? 900 : 1100);
  })();

  V.drawHome();
})();
