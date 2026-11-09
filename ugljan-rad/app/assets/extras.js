/* BikeOra 7.1: Ideen von anderen Rad-Apps, auf BikeOra zugeschnitten.
   - Sehenswertes entlang der Route (wie Highlights bei Komoot), nur echte Orte aus der Karte
   - 3D-Vorschau: die Kamera fliegt die Route ab (wie bei Bikemap), mit echtem Gelaende
   - Abbiegeliste vor dem Losfahren (wie das Cue Sheet bei Ride with GPS)
   - Hinweis, wenn man erst nach Sonnenuntergang ankommt
   - Fahrt als Bild teilen (wie bei Strava)
   Haengt sich an die Erweiterungspunkte von app.js/plus.js, aendert keine bestehende Funktion. */
(function () {
  'use strict';
  var V = window.V, VX = window.VX || (window.VX = {}); if (!V) return;
  var A = V.A, $ = V.$, esc = V.esc, svg = V.svg, map = V.map;
  var RAD = Math.PI / 180;
  function hap(t) { if (VX.haptic) VX.haptic(t); }
  function mascot(m, s) { return VX.mascot ? VX.mascot(m, s) : ''; }

  // ---------------------------------------------------------------------------------------------
  // Hilfen
  // ---------------------------------------------------------------------------------------------
  function distM(a1, o1, a2, o2) { var kx = 111320 * Math.cos(a1 * RAD), dx = (o2 - o1) * kx, dy = (a2 - a1) * 110540; return Math.sqrt(dx * dx + dy * dy); }
  // naechster Punkt der Route zu einem Ort: Abstand und Position entlang der Route
  function nearRoute(r, lat, lon) {
    var best = { d: 1e9, i: 0 }, n = r.la.length, st = n > 4000 ? 3 : 1;
    for (var i = 0; i < n; i += st) { var d = distM(lat, lon, r.la[i], r.lo[i]); if (d < best.d) best = { d: d, i: i }; }
    return { d: best.d, at: r.cum[best.i] };
  }
  function modal(html) {
    var old = $('vxm'); if (old) old.remove();
    var el = document.createElement('div'); el.id = 'vxm';
    el.innerHTML = '<div class="cbox mbox">' + html + '</div>';
    document.body.appendChild(el);
    el.onclick = function (e) { if (e.target === el) el.remove(); };
    return el;
  }

  // ---------------------------------------------------------------------------------------------
  // 1) Sehenswertes entlang der Route
  // ---------------------------------------------------------------------------------------------
  var HL = {
    view: { n: 'Sehenswert', cls: ['attraction', 'viewpoint', 'castle', 'monument', 'museum', 'artwork'], ic: 'view', col: '#7b61ff' },
    pause: { n: 'Pause', cls: ['cafe', 'ice_cream', 'bakery', 'restaurant', 'biergarten'], ic: 'cafe', col: '#e8661a' },
    water: { n: 'Trinkwasser', cls: ['drinking_water'], ic: 'water', col: '#1e88e5' },
    bike: { n: 'Radladen', cls: ['bicycle'], ic: 'bike', col: '#0fae73' }
  };
  function hlKind(pr) {
    var c = pr['class'], s = pr.subclass;
    for (var k in HL) if (HL[k].cls.indexOf(s) >= 0 || HL[k].cls.indexOf(c) >= 0) return k;
    return null;
  }
  function findHighlights(r) {
    var out = [], seen = {};
    try {
      map.querySourceFeatures('ofm', { sourceLayer: 'poi' }).forEach(function (f) {
        var pr = f.properties || {}, k = hlKind(pr), name = pr['name:de'] || pr['name:latin'] || pr.name;
        if (!k || !name || !f.geometry || f.geometry.type !== 'Point' || seen[name]) return;
        var g = f.geometry.coordinates, nr = nearRoute(r, g[1], g[0]);
        if (nr.d > (k === 'view' ? 250 : 120)) return;
        seen[name] = 1;
        out.push({ k: k, name: name, lat: g[1], lon: g[0], at: nr.at, d: nr.d });
      });
    } catch (e) { }
    // pro Art hoechstens ein paar, Sehenswertes zuerst, dann nach Position entlang der Route
    var per = { view: 6, pause: 4, water: 3, bike: 2 }, cnt = {};
    out.sort(function (a, b) { return (a.k === 'view' ? 0 : 1) - (b.k === 'view' ? 0 : 1) || a.d - b.d; });
    out = out.filter(function (x) { cnt[x.k] = (cnt[x.k] || 0) + 1; return cnt[x.k] <= per[x.k]; });
    return out.sort(function (a, b) { return a.at - b.at; }).slice(0, 10);
  }
  // Zusaetzlich die OpenStreetMap-Datenbank entlang der Strecke fragen (Overpass, Linie mit Korridor)
  var hlCache = {};
  function overpassAlong(r) {
    var key = r.len + ':' + r.la[0] + ':' + r.lo[0];
    if (hlCache[key]) return Promise.resolve(hlCache[key]);
    var n = r.la.length, k = Math.max(12, Math.min(60, Math.round(r.len / 400))), pts = [];
    for (var i = 0; i < k; i++) { var j = Math.round(i * (n - 1) / (k - 1)); pts.push(r.la[j].toFixed(5) + ',' + r.lo[j].toFixed(5)); }
    var ar = '(around:180,' + pts.join(',') + ');';
    var q = '[out:json][timeout:20];(nwr["tourism"~"^(viewpoint|attraction|museum|artwork)$"]["name"]' + ar + 'nwr["historic"~"^(castle|monument|ruins)$"]["name"]' + ar +
      'node["amenity"~"^(cafe|ice_cream|biergarten)$"]["name"]' + ar + 'node["amenity"="drinking_water"]' + ar + 'node["shop"="bicycle"]["name"]' + ar + ');out center 80;';
    var body = 'data=' + encodeURIComponent(q);
    function go(u) {
      var ctl = window.AbortController ? new AbortController() : null, to = setTimeout(function () { if (ctl) ctl.abort(); }, 15000);
      return fetch(u, { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: body, signal: ctl && ctl.signal })
        .then(function (x) { clearTimeout(to); if (!x.ok) throw new Error(x.status); return x.json(); }, function (e) { clearTimeout(to); throw e; });
    }
    return go('https://overpass-api.de/api/interpreter').catch(function () { return go('https://overpass.kumi.systems/api/interpreter'); }).then(function (j) {
      var out = [];
      (j.elements || []).forEach(function (e) {
        var t = e.tags || {}, la = e.lat != null ? e.lat : e.center && e.center.lat, lo = e.lon != null ? e.lon : e.center && e.center.lon;
        if (la == null) return;
        var kk = t.amenity === 'drinking_water' ? 'water' : t.shop === 'bicycle' ? 'bike' : t.amenity ? 'pause' : 'view';
        var name = t['name:de'] || t.name || (kk === 'water' ? 'Trinkwasser' : '');
        if (!name) return;
        var nr = nearRoute(r, la, lo); if (nr.d > 250) return;
        out.push({ k: kk, name: name, lat: la, lon: lo, at: nr.at, d: nr.d });
      });
      hlCache[key] = out; return out;
    });
  }
  function mergeHl(a, b) {
    var seen = {}, all = [];
    a.concat(b).forEach(function (h) { var id = h.name + '|' + h.k; if (seen[id]) return; seen[id] = 1; all.push(h); });
    var per = { view: 6, pause: 4, water: 3, bike: 2 }, cnt = {};
    all.sort(function (x, y) { return (x.k === 'view' ? 0 : 1) - (y.k === 'view' ? 0 : 1) || x.d - y.d; });
    all = all.filter(function (x) { cnt[x.k] = (cnt[x.k] || 0) + 1; return cnt[x.k] <= per[x.k]; });
    return all.sort(function (x, y) { return x.at - y.at; }).slice(0, 10);
  }
  var hlMarker = null;
  function showHl(h) {
    if (hlMarker) hlMarker.remove();
    var el = document.createElement('div'); el.className = 'hl-pin'; el.style.background = HL[h.k].col;
    el.innerHTML = svg(HL[h.k].ic, '#fff', 16, 2.4);
    hlMarker = new maplibregl.Marker({ element: el }).setLngLat([h.lon, h.lat]).addTo(map);
    V.setFollow(false); map.easeTo({ center: [h.lon, h.lat], zoom: Math.max(map.getZoom(), 15.5), padding: V.padding(), duration: 600 });
    hap('tick');
  }
  function fillHighlights(r) {
    var box = $('hlBox'); if (!box || V.cur !== r || box.getAttribute('data-done')) return;
    box.setAttribute('data-done', '1');
    var tiles = findHighlights(r);
    if (tiles.length) render(r, tiles);
    overpassAlong(r).then(function (op) { render(r, mergeHl(tiles, op)); }, function () { render(r, tiles, true); });
  }
  function render(r, list, failed) {
    var box = $('hlBox'); if (!box || V.cur !== r) return;
    if (!list.length) {
      box.innerHTML = '<div class="sub">' + (failed ? 'Orte entlang der Strecke konnten gerade nicht geladen werden.' : 'Keine eingetragenen Sehenswürdigkeiten oder Pausenorte direkt an der Strecke.') + '</div>';
      return;
    }
    box.innerHTML = list.map(function (h, i) {
      return '<button class="hl-row" data-hl="' + i + '"><span class="hl-ic" style="background:' + HL[h.k].col + '">' + svg(HL[h.k].ic, '#fff', 15, 2.4) + '</span>' +
        '<span class="grow"><b class="ell">' + esc(h.name) + '</b><span>' + HL[h.k].n + ' · bei km ' + V.fmtKm(h.at) + (h.d > 40 ? ' · ' + Math.round(h.d / 10) * 10 + ' m abseits' : '') + '</span></span></button>';
    }).join('');
    box.querySelectorAll('[data-hl]').forEach(function (b) { b.onclick = function () { showHl(list[+b.getAttribute('data-hl')]); }; });
  }

  // ---------------------------------------------------------------------------------------------
  // 2) 3D-Vorschau: Kamera fliegt die Route ab
  // ---------------------------------------------------------------------------------------------
  var fly = null;
  function bearingTo(a1, o1, a2, o2) { var y = Math.sin((o2 - o1) * RAD) * Math.cos(a2 * RAD), x = Math.cos(a1 * RAD) * Math.sin(a2 * RAD) - Math.sin(a1 * RAD) * Math.cos(a2 * RAD) * Math.cos((o2 - o1) * RAD); return (Math.atan2(y, x) / RAD + 360) % 360; }
  function idxAtDist(r, d) { var lo = 0, hi = r.cum.length - 1; while (lo < hi) { var m = (lo + hi) >> 1; if (r.cum[m] < d) lo = m + 1; else hi = m; } return lo; }
  function startFly(r) {
    if (fly) return;
    var reduce = window.matchMedia && matchMedia('(prefers-reduced-motion: reduce)').matches;
    var dur = Math.max(14000, Math.min(36000, r.len / 1000 * 2600)), t0 = null;
    try { if (!map.getSource('v3d')) map.addSource('v3d', { type: 'raster-dem', tiles: ['https://s3.amazonaws.com/elevation-tiles-prod/terrarium/{z}/{x}/{y}.png'], tileSize: 256, maxzoom: 14, encoding: 'terrarium' }); map.setTerrain({ source: 'v3d', exaggeration: 1.4 }); } catch (e) { }
    V.sheetEl.classList.add('mini'); V.setFollow(false);
    var bar = document.createElement('div'); bar.id = 'flyBar';
    bar.innerHTML = '<div class="fly-t"><b>3D-Vorschau</b><span id="flyKm">0 km</span></div><div class="fly-p"><i id="flyP"></i></div><button id="flyStop">Beenden</button>';
    document.body.appendChild(bar);
    fly = { r: r, raf: 0, bear: null };
    $('flyStop').onclick = stopFly;
    fly.onDrag = function (e) { if (e && e.originalEvent) stopFly('drag'); };
    map.on('dragstart', fly.onDrag);
    hap('soft');
    function frame(ts) {
      if (!fly) return;
      if (t0 === null) t0 = ts;
      var p = Math.min(1, (ts - t0) / dur), e = p < 0.5 ? 2 * p * p : 1 - Math.pow(-2 * p + 2, 2) / 2, d = e * r.len;
      var i = idxAtDist(r, d), j = idxAtDist(r, Math.min(r.len, d + 350));
      var b = bearingTo(r.la[i], r.lo[i], r.la[j], r.lo[j]);
      // Blickrichtung weich nachfuehren, damit nichts ruckelt
      if (fly.bear === null) fly.bear = b; else { var dlt = ((b - fly.bear + 540) % 360) - 180; fly.bear = (fly.bear + dlt * 0.04 + 360) % 360; }
      map.jumpTo({ center: [r.lo[i], r.la[i]], zoom: 15.2, pitch: reduce ? 0 : 62, bearing: fly.bear });
      var pe = $('flyP'), ke = $('flyKm'); if (pe) pe.style.width = (p * 100) + '%'; if (ke) ke.textContent = V.fmtKm(d) + ' km von ' + V.fmtKm(r.len) + ' km';
      if (p >= 1) { stopFly('ende'); return; }
      fly.raf = requestAnimationFrame(frame);
    }
    fly.raf = requestAnimationFrame(frame);
  }
  VX.flying = function () { return !!fly; };
  function stopFly(why) {
    if (!fly) return;
    window.__flyStop = why || (new Error().stack || '').split('\n')[2];
    var r = fly.r; cancelAnimationFrame(fly.raf); map.off('dragstart', fly.onDrag); fly = null;
    var b = $('flyBar'); if (b) b.remove();
    try { map.setTerrain(null); } catch (e) { }
    if (V.upd3d) V.upd3d();   // eigenes 3D-Gelaende des Nutzers wieder an
    V.sheetEl.classList.remove('mini');
    map.easeTo({ pitch: 0, bearing: 0, duration: 500 });
    setTimeout(function () { if (V.cur === r) V.fitTo(V.boundsOf(V.routeLL(r))); }, 520);
  }

  // ---------------------------------------------------------------------------------------------
  // 3) Abbiegeliste
  // ---------------------------------------------------------------------------------------------
  function arrow(turn) {
    var a = Math.max(-170, Math.min(170, turn || 0));
    return '<svg width="26" height="26" viewBox="0 0 24 24" style="transform:rotate(' + a + 'deg)"><path d="M12 21V5M6 11l6-6 6 6" fill="none" stroke="currentColor" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round"/></svg>';
  }
  function showSteps(r) {
    var st = r.steps.filter(function (s) { return s.text; });
    modal('<div style="display:flex;align-items:center;gap:10px">' + mascot('think', 64) + '<div class="grow"><h2>Abbiegungen</h2><div class="sub">' + st.length + ' Hinweise auf ' + V.fmtDist(r.len) + '</div></div></div>' +
      '<div class="cue">' + st.map(function (s) {
        return '<div class="cue-r"><span class="cue-a">' + (s.end ? svg('flag', 'currentColor', 22) : arrow(s.turn)) + '</span><span class="grow"><b>' + esc(s.text) + '</b>' + (s.name ? '<span>' + esc(s.name) + '</span>' : '') + '</span><span class="cue-km">' + V.fmtKm(s.at) + ' km</span></div>';
      }).join('') + '</div><div class="btns"><button class="go" id="cueOk">Schließen</button></div>');
    $('cueOk').onclick = function () { var m = $('vxm'); if (m) m.remove(); };
  }

  // ---------------------------------------------------------------------------------------------
  // 4) Dunkelheit
  // ---------------------------------------------------------------------------------------------
  function darkHint(r) {
    try {
      var now = new Date(), st = V.sunTimes(now), arr = new Date(now.getTime() + r.secs * 1000);
      if (now < st.rise || now > st.set) return '<div class="dark-hint">' + svg('sun', '#7a4300', 18) + '<span>Es ist dunkel. Licht vorne und hinten einschalten und gut sichtbar fahren.</span></div>';
      if (arr > st.set) return '<div class="dark-hint">' + svg('sun', '#7a4300', 18) + '<span>Ankunft gegen ' + V.hhmm(arr) + ', die Sonne geht um ' + V.hhmm(st.set) + ' unter. Licht mitnehmen.</span></div>';
    } catch (e) { }
    return '';
  }

  // Ins Routenblatt einhaengen (nach den Kennzahlen)
  var oldRH = VX.routeHtml, oldRB = VX.routeBound;
  VX.routeHtml = function (r, plan) {
    var h = (oldRH ? oldRH(r, plan) || '' : '');
    var nSteps = r.steps ? r.steps.filter(function (s) { return s.text && !s.end; }).length : 0;
    return h + darkHint(r) +
      '<div class="xrow"><button class="xbtn" id="x3d">' + svg('play', 'currentColor', 18) + '<span>3D-Vorschau</span></button>' +
      (nSteps ? '<button class="xbtn" id="xCue">' + svg('list', 'currentColor', 18) + '<span>Abbiegungen (' + nSteps + ')</span></button>' : '') + '</div>' +
      '<h3>Entlang der Strecke</h3><div class="hl-box" id="hlBox"><div class="sub">Suche Sehenswertes und Pausenorte ...</div></div>';
  };
  VX.routeBound = function (r, plan) {
    if (oldRB) oldRB(r, plan);
    if ($('x3d')) $('x3d').onclick = function () { startFly(r); };
    if ($('xCue')) $('xCue').onclick = function () { hap('tick'); showSteps(r); };
    if (hlMarker) { hlMarker.remove(); hlMarker = null; }
    // nach dem Laden der Karte fuellen; spaetestens nach 2,5 s (falls die Karte noch arbeitet)
    setTimeout(function () { if (V.mapReady) map.once('idle', function () { fillHighlights(r); }); }, 200);
    setTimeout(function () { fillHighlights(r); }, 2500);
  };
  // Route weg: Marker und Vorschau aufraeumen
  var oldRD = VX.routeDrawn;
  VX.routeDrawn = function (x) { if (oldRD) oldRD(x); if (!x) { if (hlMarker) { hlMarker.remove(); hlMarker = null; } stopFly(); } };

  // ---------------------------------------------------------------------------------------------
  // 5) Fahrt als Bild teilen
  // ---------------------------------------------------------------------------------------------
  function loadImg(src) { return new Promise(function (res, rej) { var i = new Image(); i.onload = function () { res(i); }; i.onerror = rej; i.src = src; }); }
  function rideImage(ride) {
    var W = 1080, H = 1350, c = document.createElement('canvas'); c.width = W; c.height = H;
    var g = c.getContext('2d');
    var bg = g.createLinearGradient(0, 0, W, H); bg.addColorStop(0, '#0f9f6e'); bg.addColorStop(0.55, '#0b6f7a'); bg.addColorStop(1, '#0b3f5c');
    g.fillStyle = bg; g.fillRect(0, 0, W, H);
    // Strecke
    var trk = ride.trk || [];
    if (trk.length > 1) {
      var la0 = 1e9, la1 = -1e9, lo0 = 1e9, lo1 = -1e9;
      trk.forEach(function (p) { la0 = Math.min(la0, p[0]); la1 = Math.max(la1, p[0]); lo0 = Math.min(lo0, p[1]); lo1 = Math.max(lo1, p[1]); });
      var kx = Math.cos((la0 + la1) / 2 * RAD), bw = Math.max(1e-6, (lo1 - lo0) * kx), bh = Math.max(1e-6, la1 - la0);
      var box = { x: 110, y: 260, w: W - 220, h: 620 }, s = Math.min(box.w / bw, box.h / bh);
      var ox = box.x + (box.w - bw * s) / 2, oy = box.y + (box.h - bh * s) / 2;
      var P = function (p) { return [ox + (p[1] - lo0) * kx * s, oy + (la1 - p[0]) * s]; };
      g.lineJoin = 'round'; g.lineCap = 'round';
      [['rgba(0,0,0,.25)', 26], ['#ffffff', 16], ['#2fd59a', 9]].forEach(function (st) {
        g.strokeStyle = st[0]; g.lineWidth = st[1]; g.beginPath();
        trk.forEach(function (p, i) { var q = P(p); if (i) g.lineTo(q[0], q[1]); else g.moveTo(q[0], q[1]); });
        g.stroke();
      });
      var a = P(trk[0]), b = P(trk[trk.length - 1]);
      g.fillStyle = '#ffffff'; g.beginPath(); g.arc(a[0], a[1], 16, 0, 7); g.fill(); g.fillStyle = '#0f9f6e'; g.beginPath(); g.arc(a[0], a[1], 9, 0, 7); g.fill();
      g.fillStyle = '#ffffff'; g.beginPath(); g.arc(b[0], b[1], 16, 0, 7); g.fill(); g.fillStyle = '#e8661a'; g.beginPath(); g.arc(b[0], b[1], 9, 0, 7); g.fill();
    }
    // Texte
    var font = '"Inter","Roboto",system-ui,sans-serif';
    g.fillStyle = '#ffffff'; g.font = '800 64px ' + font; g.fillText('BikeOra', 80, 140);
    g.font = '600 34px ' + font; g.globalAlpha = 0.85;
    var d = new Date(ride.t), sub = d.toLocaleDateString('de-DE', { weekday: 'long', day: 'numeric', month: 'long' }) + (ride.title ? ' · ' + ride.title : '');
    // nicht unter den Fuchs laufen lassen
    while (sub.length > 4 && g.measureText(sub).width > W - 400) sub = sub.slice(0, -2).replace(/\s*…?$/, '') + '…';
    g.fillText(sub, 80, 196); g.globalAlpha = 1;
    var stats = [[V.fmtDist(ride.dist), 'Strecke'], [V.fmtTime(ride.mv), 'In Bewegung'], [(ride.mv > 0 ? (ride.dist / ride.mv * 3.6).toFixed(1).replace('.', ',') : '0') + ' km/h', 'Schnitt'], [Math.round(ride.asc) + ' m', 'Bergauf']];
    stats.forEach(function (st, i) {
      var x = 80 + (i % 2) * 470, y = 990 + Math.floor(i / 2) * 150;
      g.fillStyle = 'rgba(255,255,255,.14)'; roundRect(g, x, y - 82, 440, 128, 26); g.fill();
      g.fillStyle = '#ffffff'; g.font = '800 54px ' + font; g.fillText(st[0], x + 30, y);
      g.globalAlpha = 0.8; g.font = '600 26px ' + font; g.fillText(st[1].toUpperCase(), x + 30, y + 34); g.globalAlpha = 1;
    });
    // Velo, der Fuchs (in 3D, wenn moeglich)
    if (window.Fox3D && Fox3D.ok && Fox3D.enabled) { try { g.drawImage(Fox3D.still('cheer', 330, 380, 0.25), W - 330, 10, 330, 380); return Promise.resolve(c); } catch (e) { } }
    var foxSvg = window.Fox ? Fox.svg('cheer') : null;
    if (!foxSvg) return Promise.resolve(c);
    var fx = foxSvg.replace('<svg ', '<svg xmlns="http://www.w3.org/2000/svg" width="252" height="290" ').replace('class="fox"', 'class="fox fx-cheer"');
    // Posen werden sonst per CSS gezeigt: die passenden Teile direkt sichtbar machen
    fx = fx.replace('</defs>', '</defs><style>.a,.e,.mo,.f-zzz,.f-dots,.f-medal,.f-spark,.f-tear{display:none}.al-up,.ar-up,.e-happy,.mo-laugh{display:block}</style>');
    return loadImg('data:image/svg+xml;charset=utf-8,' + encodeURIComponent(fx)).then(function (im) { g.drawImage(im, W - 300, 40, 220, 253); return c; }, function () { return c; });
  }
  function roundRect(g, x, y, w, h, r) { g.beginPath(); g.moveTo(x + r, y); g.arcTo(x + w, y, x + w, y + h, r); g.arcTo(x + w, y + h, x, y + h, r); g.arcTo(x, y + h, x, y, r); g.arcTo(x, y, x + w, y, r); g.closePath(); }
  function shareRide(ride) {
    hap('click');
    rideImage(ride).then(function (c) {
      var url = c.toDataURL('image/png');
      if (A && A.shareImage) {
        var ok = false; try { ok = A.shareImage(url.split(',')[1], 'Meine Fahrt mit BikeOra'); } catch (e) { }
        if (ok) return;
      }
      // ohne Teilen-Funktion (PC, aeltere Android-Versionen): Bild zeigen
      var m = modal('<h2>Deine Fahrt als Bild</h2><img src="' + url + '" alt="Fahrt als Bild" style="width:100%;border-radius:16px;margin:10px 0">' +
        '<div class="sub">' + (V.DESKTOP ? 'Rechtsklick auf das Bild, dann „Bild speichern“.' : 'Lange auf das Bild drücken, um es zu speichern.') + '</div><div class="btns"><button class="go" id="imgOk">Schließen</button></div>');
      $('imgOk').onclick = function () { m.remove(); };
    });
  }
  var oldRideB = VX.rideBound;
  VX.rideBound = function (ride, k) {
    if (oldRideB) oldRideB(ride, k);
    var b = $('rideShare'); if (b) b.onclick = function () { shareRide(ride); };
  };
  VX._extras = { findHighlights: findHighlights, startFly: startFly, stopFly: stopFly, rideImage: rideImage, darkHint: darkHint };
})();
