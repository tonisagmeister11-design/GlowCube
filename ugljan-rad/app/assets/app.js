/* Ugljan Rad: Offline-Radkarte und Navigation fuer die Insel Ugljan */
(function () {
  var D = window.UGLJAN;
  var $ = function (id) { return document.getElementById(id); };

  // ---------- Grunddaten ----------
  var N = D.nodes.length / 2;
  var LAT = new Float64Array(N), LON = new Float64Array(N);
  for (var i = 0; i < N; i++) { LAT[i] = D.nodes[2 * i] / 1e5; LON[i] = D.nodes[2 * i + 1] / 1e5; }

  function dist(la1, lo1, la2, lo2) {
    var dx = (lo2 - lo1) * 111320 * Math.cos(la1 * Math.PI / 180), dy = (la2 - la1) * 110540;
    return Math.sqrt(dx * dx + dy * dy);
  }
  function bearing(la1, lo1, la2, lo2) {
    var dx = (lo2 - lo1) * Math.cos(la1 * Math.PI / 180), dy = la2 - la1;
    return (Math.atan2(dx, dy) * 180 / Math.PI + 360) % 360;
  }

  var CLASS = [
    { n: 'Radweg', c: '#c8f53c', w: 4.5, sp: 16 },
    { n: 'Ruhige Strasse', c: '#8995a1', w: 2.5, sp: 17 },
    { n: 'Feldweg', c: '#c49a6c', w: 2.5, d: '7 5', sp: 12 },
    { n: 'Pfad', c: '#a88f6e', w: 2, d: '2 5', sp: 8 },
    { n: 'Fussweg', c: '#6f7a85', w: 1.6, d: '2 4', sp: 6 },
    { n: 'Landstrasse', c: '#d0d6dc', w: 3.5, sp: 18 },
    { n: 'Hauptstrasse', c: '#ff5a4e', w: 4, sp: 19 },
    { n: 'Hauptstrasse', c: '#ff5a4e', w: 4, sp: 19 },
    { n: 'Grober Weg', c: '#8a6c4c', w: 2.2, d: '4 4', sp: 8 }
  ];
  var MODES = {
    relax: { n: 'Radwege', f: [0.6, 1.0, 1.15, 1.7, 2.6, 1.6, 3.5, 4.5, 2.4], r: 0.8 },
    mtb: { n: 'Gelaende', f: [0.9, 1.25, 0.7, 0.8, 1.9, 1.9, 3.2, 4, 0.85], r: 0.85 },
    fast: { n: 'Kuerzeste', f: [1, 1, 1.05, 1.3, 1.8, 1, 1.05, 1.1, 1.5], r: 1 }
  };
  var mode = 'relax';
  try { mode = localStorage.getItem('mode') || 'relax'; } catch (e) {}
  if (!MODES[mode]) mode = 'relax';

  // Graph: Kanten als flache Listen
  var adj = []; for (i = 0; i < N; i++) adj.push([]);
  D.ways.forEach(function (w, wi) {
    var p = w[3], one = w[2] & 1;
    for (var k = 0; k + 1 < p.length; k++) {
      var a = p[k], b = p[k + 1], L = dist(LAT[a], LON[a], LAT[b], LON[b]);
      adj[a].push(b, L, wi);
      if (!one) adj[b].push(a, L, wi);
    }
  });

  // ---------- Karte ----------
  var bb = D.bbox;
  var bounds = L.latLngBounds([bb[0], bb[1]], [bb[2], bb[3]]);
  var map = L.map('map', {
    zoomControl: false, attributionControl: true, preferCanvas: true,
    maxBounds: bounds.pad(0.6), minZoom: 11, maxZoom: 19, zoomSnap: 0.25
  });
  map.attributionControl.setPrefix('').addAttribution('Kartendaten &copy; OpenStreetMap-Mitwirkende (ODbL)');
  var canvas = L.canvas({ padding: 0.5, tolerance: 6 });
  map.fitBounds(bounds, { paddingBottomRight: [0, 160] });

  D.others.forEach(function (r) { L.polygon(r, { renderer: canvas, stroke: false, fillColor: '#1b2621', fillOpacity: 1, interactive: false }).addTo(map); });
  D.land.forEach(function (r) {
    L.polygon(r, { renderer: canvas, color: '#2d4a5c', weight: 6, opacity: 0.5, fillColor: '#1f2b24', fillOpacity: 1, interactive: false }).addTo(map);
  });
  D.green.forEach(function (r) { L.polygon(r, { renderer: canvas, stroke: false, fillColor: '#24382a', fillOpacity: 1, interactive: false }).addTo(map); });

  function wayLatLngs(w) { return w[3].map(function (n) { return [LAT[n], LON[n]]; }); }
  // Radrouten-Leuchtspur unter den Wegen
  D.ways.forEach(function (w) {
    if (w[2] & 2) L.polyline(wayLatLngs(w), { renderer: canvas, color: '#ffb020', weight: 10, opacity: 0.28, interactive: false, lineCap: 'round' }).addTo(map);
  });
  var order = [4, 3, 8, 2, 1, 5, 6, 7, 0];
  order.forEach(function (c) {
    D.ways.forEach(function (w) {
      if (w[0] !== c) return;
      var s = CLASS[c];
      L.polyline(wayLatLngs(w), { renderer: canvas, color: s.c, weight: s.w, dashArray: s.d, opacity: 0.95, interactive: false, lineCap: 'round' }).addTo(map);
    });
  });

  // Ortsnamen
  var labels = [];
  D.places.forEach(function (p) {
    var big = p[3] === 'village' || p[3] === 'town';
    var m = L.marker([p[1], p[2]], { interactive: false, icon: L.divIcon({ className: '', html: '<div class="lbl' + (big ? '' : ' small') + '" style="transform:translate(-50%,-50%)">' + esc(p[0]) + '</div>', iconSize: [0, 0] }) });
    labels.push({ m: m, big: big });
  });
  function updLabels() {
    var z = map.getZoom();
    labels.forEach(function (l) { var show = l.big ? z >= 11.5 : z >= 14; if (show && !map.hasLayer(l.m)) l.m.addTo(map); if (!show && map.hasLayer(l.m)) map.removeLayer(l.m); });
  }
  map.on('zoomend', updLabels); updLabels();

  // ---------- Symbole (SVG, keine Emojis) ----------
  var IC = {
    food: '<path d="M7 3v7a2 2 0 0 0 2 2M11 3v7a2 2 0 0 1-2 2v9M9 3v6M17 21V3c-2 1.5-3 4-3 8h3"/>',
    cafe: '<path d="M4 9h12v4a5 5 0 0 1-5 5H9a5 5 0 0 1-5-5zM16 10.5h1.5a2.5 2.5 0 0 1 0 5H16M8 3v3M12 3v3"/>',
    bar: '<path d="M5 4h14l-7 8zM12 12v8M8 20h8"/>',
    beach: '<path d="M3 12a9 9 0 0 1 18 0zM12 12v9M8 21h8"/>',
    water: '<path d="M12 3s6 7 6 11.5a6 6 0 0 1-12 0C6 10 12 3 12 3z"/>',
    wc: '<path d="M3 7l2 10 2.5-7 2.5 7 2-10M21 9a3 3 0 1 0 0 6"/>',
    view: '<path d="M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12z"/><circle cx="12" cy="12" r="3"/>',
    sight: '<path d="M5 21V4h11l-2.5 4L16 12H5"/>',
    shop: '<path d="M5 8h14l-1 13H6zM9 8V6a3 3 0 0 1 6 0v2"/>',
    pharmacy: '<path d="M12 5v14M5 12h14"/>',
    bike: '<circle cx="6" cy="16" r="3.5"/><circle cx="18" cy="16" r="3.5"/><path d="M6 16l4-7h6l2 7M10 9l3 7 3-7"/>',
    ferry: '<path d="M3 14h18l-3 6H6zM6 14V9h12v5M12 9V4"/>',
    pin: '<circle cx="12" cy="10" r="3"/>',
    flag: '<path d="M6 21V4h12l-3 4 3 4H6"/>'
  };
  IC.snack = IC.food;
  var CAT = {
    food: { n: 'Restaurant', c: '#ff9f43' }, snack: { n: 'Imbiss', c: '#ff9f43' }, cafe: { n: 'Cafe', c: '#e8c07d' },
    bar: { n: 'Bar', c: '#c38bff' }, beach: { n: 'Strand', c: '#4fc3f7' }, water: { n: 'Trinkwasser', c: '#4fc3f7' },
    wc: { n: 'Toilette', c: '#9fb3c4' }, view: { n: 'Aussichtspunkt', c: '#c8f53c' }, sight: { n: 'Sehenswuerdigkeit', c: '#f6e05e' },
    shop: { n: 'Laden', c: '#7ee0b0' }, pharmacy: { n: 'Apotheke', c: '#7ee0b0' }, bike: { n: 'Fahrradservice', c: '#c8f53c' },
    ferry: { n: 'Faehre', c: '#4fc3f7' }
  };
  function svg(name, color, size) {
    return '<svg width="' + (size || 16) + '" height="' + (size || 16) + '" viewBox="0 0 24 24" fill="none" stroke="' + color + '" stroke-width="2.3" stroke-linecap="round" stroke-linejoin="round">' + IC[name] + '</svg>';
  }
  function esc(s) { return String(s).replace(/[&<>"]/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]; }); }

  var FILTERS = [
    { id: 'food', n: 'Essen', cats: ['food', 'snack'] },
    { id: 'cafe', n: 'Cafe und Bar', cats: ['cafe', 'bar'] },
    { id: 'beach', n: 'Straende', cats: ['beach'] },
    { id: 'view', n: 'Aussicht', cats: ['view', 'sight'] },
    { id: 'water', n: 'Wasser und WC', cats: ['water', 'wc'] },
    { id: 'shop', n: 'Einkaufen', cats: ['shop', 'pharmacy'] },
    { id: 'bike', n: 'Rad und Faehre', cats: ['bike', 'ferry'] }
  ];
  var active = { food: 1, beach: 1, view: 1 };
  var poiMarkers = D.pois.map(function (p, idx) {
    var c = CAT[p[0]];
    var m = L.marker([p[1], p[2]], { icon: L.divIcon({ className: '', iconSize: [26, 26], iconAnchor: [13, 13], html: '<div class="poi" style="background:' + c.c + '">' + svg(p[0], '#14181d', 15) + '</div>' }) });
    m.on('click', function (e) { L.DomEvent.stopPropagation(e); showPoi(idx); });
    return m;
  });
  function drawChips() {
    $('chips').innerHTML = FILTERS.map(function (f) {
      return '<button class="chip' + (active[f.id] ? ' on' : '') + '" data-f="' + f.id + '">' + esc(f.n) + '</button>';
    }).join('');
    var cats = {};
    FILTERS.forEach(function (f) { if (active[f.id]) f.cats.forEach(function (c) { cats[c] = 1; }); });
    D.pois.forEach(function (p, i) {
      var on = cats[p[0]] && map.getZoom() >= 12.5;
      if (on && !map.hasLayer(poiMarkers[i])) poiMarkers[i].addTo(map);
      if (!on && map.hasLayer(poiMarkers[i])) map.removeLayer(poiMarkers[i]);
    });
  }
  $('chips').addEventListener('click', function (e) {
    var b = e.target.closest('.chip'); if (!b) return;
    var f = b.getAttribute('data-f'); if (active[f]) delete active[f]; else active[f] = 1;
    drawChips();
  });
  map.on('zoomend', drawChips); drawChips();

  // ---------- Standort ----------
  var me = null, meMarker = null, accCircle = null, follow = false, lastSpeed = -1;
  var PREKO = (function () {
    var f = D.pois.filter(function (p) { return p[0] === 'ferry'; });
    var best = f.filter(function (p) { return /Preko/i.test(p[3]); })[0] || f[0];
    return best ? { lat: best[1], lon: best[2], name: 'Faehre Preko' } : { lat: 44.0817, lon: 15.1876, name: 'Preko' };
  })();
  function onIsland(lat, lon) { return bounds.pad(0.05).contains([lat, lon]); }

  window.onGps = function (lat, lon, acc, speed, brg) {
    me = { lat: lat, lon: lon, acc: acc };
    if (speed >= 0) lastSpeed = speed * 3.6;
    if (!meMarker) {
      meMarker = L.marker([lat, lon], { interactive: false, zIndexOffset: 1000, icon: L.divIcon({ className: '', iconSize: [22, 22], iconAnchor: [11, 11], html: '<div class="youarehere"></div>' }) }).addTo(map);
      accCircle = L.circle([lat, lon], { radius: acc, renderer: canvas, color: '#c8f53c', weight: 1, opacity: 0.4, fillOpacity: 0.07, interactive: false }).addTo(map);
    } else { meMarker.setLatLng([lat, lon]); accCircle.setLatLng([lat, lon]).setRadius(acc); }
    if (follow) map.panTo([lat, lon], { animate: true });
    if (nav) navUpdate();
    else if (state === 'home') drawHome();
  };
  // Browser-Fallback (z. B. beim Testen ohne Android-Bruecke)
  if (!window.Android && navigator.geolocation) {
    navigator.geolocation.watchPosition(function (p) { onGps(p.coords.latitude, p.coords.longitude, p.coords.accuracy, p.coords.speed == null ? -1 : p.coords.speed, -1); }, function () {}, { enableHighAccuracy: true });
  }
  $('btnLoc').onclick = function () {
    if (!me) { toast('Noch kein GPS-Signal. Standort am Handy einschalten.'); return; }
    if (!onIsland(me.lat, me.lon)) { toast('Du bist gerade nicht auf Ugljan.'); return; }
    follow = true; $('btnLoc').classList.add('on'); map.setView([me.lat, me.lon], Math.max(map.getZoom(), 16));
  };
  map.on('dragstart', function () { follow = false; $('btnLoc').classList.remove('on'); });
  $('btnIsland').onclick = function () { follow = false; $('btnLoc').classList.remove('on'); map.fitBounds(bounds, { paddingBottomRight: [0, 160] }); };

  function toast(t) {
    var d = document.createElement('div'); d.className = 'toast'; d.textContent = t; document.body.appendChild(d);
    setTimeout(function () { d.remove(); }, 3200);
  }

  // ---------- Routing (A*) ----------
  function nearestNode(lat, lon) {
    var best = -1, bd = 1e18;
    for (var i = 0; i < N; i++) {
      if (!adj[i].length) continue;
      var d = dist(lat, lon, LAT[i], LON[i]);
      if (d < bd) { bd = d; best = i; }
    }
    return best;
  }
  function route(from, to, m) {
    var M = MODES[m], f = M.f, hmin = Math.min.apply(null, f) * Math.min(1, M.r);
    var g = new Float64Array(N).fill(Infinity), prev = new Int32Array(N).fill(-1), pway = new Int32Array(N).fill(-1);
    var closed = new Uint8Array(N);
    var heap = [], hf = [];
    function push(n, k) {
      heap.push(n); hf.push(k); var i = heap.length - 1;
      while (i > 0) { var p = (i - 1) >> 1; if (hf[p] <= hf[i]) break; swap(i, p); i = p; }
    }
    function swap(a, b) { var t = heap[a]; heap[a] = heap[b]; heap[b] = t; t = hf[a]; hf[a] = hf[b]; hf[b] = t; }
    function pop() {
      var top = heap[0], ln = heap.pop(), lk = hf.pop();
      if (heap.length) {
        heap[0] = ln; hf[0] = lk; var i = 0;
        for (;;) { var l = 2 * i + 1, r = l + 1, s = i; if (l < heap.length && hf[l] < hf[s]) s = l; if (r < heap.length && hf[r] < hf[s]) s = r; if (s === i) break; swap(i, s); i = s; }
      }
      return top;
    }
    g[from] = 0; push(from, 0);
    while (heap.length) {
      var u = pop();
      if (closed[u]) continue; closed[u] = 1;
      if (u === to) break;
      var a = adj[u];
      for (var k = 0; k < a.length; k += 3) {
        var v = a[k]; if (closed[v]) continue;
        var w = D.ways[a[k + 2]], cost = a[k + 1] * f[w[0]] * ((w[2] & 2) ? M.r : 1);
        var ng = g[u] + cost;
        if (ng < g[v]) { g[v] = ng; prev[v] = u; pway[v] = a[k + 2]; push(v, ng + dist(LAT[v], LON[v], LAT[to], LON[to]) * hmin); }
      }
    }
    if (g[to] === Infinity) return null;
    var nodes = [], wl = [], n = to;
    while (n !== -1) { nodes.push(n); wl.push(pway[n]); n = prev[n]; }
    nodes.reverse(); wl.reverse(); // wl[i] = Weg vom Knoten i-1 zu i
    var cum = [0], mix = {}, secs = 0;
    for (var i = 1; i < nodes.length; i++) {
      var d = dist(LAT[nodes[i - 1]], LON[nodes[i - 1]], LAT[nodes[i]], LON[nodes[i]]);
      cum.push(cum[i - 1] + d);
      var c = D.ways[wl[i]][0];
      var key = (c === 7 ? 6 : c);
      mix[key] = (mix[key] || 0) + d;
      secs += d / (CLASS[c].sp / 3.6);
    }
    return { nodes: nodes, ways: wl, cum: cum, len: cum[cum.length - 1], mix: mix, secs: secs, steps: buildSteps(nodes, wl, cum) };
  }
  function pointAlong(nodes, cum, i, dir, meters) {
    var j = i;
    while (j + dir >= 0 && j + dir < nodes.length && Math.abs(cum[j] - cum[i]) < meters) j += dir;
    return nodes[j];
  }
  function buildSteps(nodes, wl, cum) {
    var steps = [];
    for (var i = 1; i + 1 < nodes.length; i++) {
      var n = nodes[i];
      if (adj[n].length < 9) continue; // Kreuzung: mindestens 3 Kanten
      var a = pointAlong(nodes, cum, i, -1, 20), b = pointAlong(nodes, cum, i, 1, 20);
      var b1 = bearing(LAT[a], LON[a], LAT[n], LON[n]), b2 = bearing(LAT[n], LON[n], LAT[b], LON[b]);
      var t = ((b2 - b1 + 540) % 360) - 180;
      var changed = D.ways[wl[i]][1] !== D.ways[wl[i + 1]][1];
      if (Math.abs(t) < 30 || (!changed && Math.abs(t) < 50)) continue;
      if (steps.length && cum[i] - steps[steps.length - 1].at < 25) continue;
      var txt = Math.abs(t) < 55 ? (t < 0 ? 'Leicht links' : 'Leicht rechts') : Math.abs(t) < 130 ? (t < 0 ? 'Links abbiegen' : 'Rechts abbiegen') : (t < 0 ? 'Scharf links' : 'Scharf rechts');
      var nm = D.names[D.ways[wl[i + 1]][1]];
      steps.push({ at: cum[i], turn: t, text: txt, name: nm, cls: D.ways[wl[i + 1]][0], node: n });
    }
    steps.push({ at: cum[cum.length - 1], turn: 0, text: 'Ziel erreicht', name: '', end: true });
    return steps;
  }

  // ---------- Zustand und unteres Blatt ----------
  var state = 'home', dest = null, cur = null, routeLayer = null, destMarker = null, tourLayer = null, nav = null, startInfo = null;

  function fmtDist(m) { return m < 1000 ? Math.round(m / 10) * 10 + ' m' : (m / 1000).toFixed(m < 10000 ? 1 : 0).replace('.', ',') + ' km'; }
  function fmtTime(s) { var m = Math.max(1, Math.round(s / 60)); return m < 60 ? m + ' min' : Math.floor(m / 60) + ' h ' + (m % 60) + ' min'; }
  function clock(s) { var d = new Date(Date.now() + s * 1000); return ('0' + d.getHours()).slice(-2) + ':' + ('0' + d.getMinutes()).slice(-2); }
  function sheet(html) { $('sheet').innerHTML = '<div class="grab"></div>' + html; }
  function myRef() { return me && onIsland(me.lat, me.lon) ? me : null; }

  function nearby(lat, lon, cats, n) {
    return D.pois.map(function (p, i) { return { i: i, d: dist(lat, lon, p[1], p[2]) }; })
      .filter(function (x) { return !cats || cats.indexOf(D.pois[x.i][0]) >= 0; })
      .sort(function (a, b) { return a.d - b.d; }).slice(0, n);
  }
  function poiRow(x) {
    var p = D.pois[x.i], c = CAT[p[0]];
    return '<div class="row" data-poi="' + x.i + '"><div class="ico">' + svg(p[0], c.c, 18) + '</div><div style="flex:1"><div class="t">' + esc(p[3] || c.n) + '</div><div class="s">' + c.n + (p[4].city ? ' &middot; ' + esc(p[4].city) : '') + '</div></div><div class="s">' + fmtDist(x.d) + '</div></div>';
  }

  function drawHome() {
    state = 'home';
    var ref = myRef() || { lat: 44.0817, lon: 15.1876 };
    var total = 0, bike = 0;
    D.ways.forEach(function (w) { var p = w[3]; for (var k = 1; k < p.length; k++) { var d = dist(LAT[p[k - 1]], LON[p[k - 1]], LAT[p[k]], LON[p[k]]); total += d; if (w[0] === 0 || w[0] === 2 || (w[2] & 2)) bike += d; } });
    sheet('<h2>Ugljan mit dem Rad</h2><div class="sub">' + (myRef() ? 'GPS aktiv. Tippe auf die Karte oder suche ein Ziel.' : (me ? 'Du bist nicht auf der Insel. Routen starten an der Faehre Preko.' : 'Warte auf GPS. Tippe auf die Karte oder suche ein Ziel.')) + '</div>' +
      '<div class="stats"><div class="stat"><b>' + Math.round(bike / 1000) + ' km</b><span>Rad- und Feldwege</span></div><div class="stat"><b>' + D.routes.length + '</b><span>Radrouten</span></div></div>' +
      legend() +
      '<div class="sub" style="margin-top:14px;text-transform:uppercase;letter-spacing:1px;font-size:12px">Essen in der Naehe' + (myRef() ? '' : ' von Preko') + '</div><div class="list">' +
      nearby(ref.lat, ref.lon, ['food', 'snack', 'cafe'], 4).map(poiRow).join('') + '</div>');
  }
  function legend() {
    var items = [[0, 'Radweg'], [-1, 'Markierte Radroute'], [1, 'Ruhige Strasse'], [2, 'Feldweg'], [3, 'Pfad'], [6, 'Hauptstrasse']];
    return '<div class="legend">' + items.map(function (x) {
      var col = x[0] < 0 ? '#ffb020' : CLASS[x[0]].c; return '<span><i style="background:' + col + '"></i>' + x[1] + '</span>';
    }).join('') + '</div>';
  }

  function setDestMarker(lat, lon) {
    if (destMarker) map.removeLayer(destMarker);
    destMarker = L.marker([lat, lon], { interactive: false, zIndexOffset: 900, icon: L.divIcon({ className: '', iconSize: [30, 30], iconAnchor: [15, 30], html: '<div class="pin" style="background:#c8f53c">' + svg('flag', '#14181d', 15) + '</div>' }) }).addTo(map);
  }

  function showPoi(i) {
    var p = D.pois[i], c = CAT[p[0]], e = p[4], ref = myRef();
    state = 'poi'; dest = { lat: p[1], lon: p[2], name: p[3] || c.n };
    setDestMarker(p[1], p[2]);
    var info = '';
    if (e.cu) info += '<span class="tag">' + esc(e.cu.replace(/_/g, ' ').replace(/;/g, ', ')) + '</span>';
    if (e.oh) info += '<div class="sub" style="margin-top:8px">Oeffnungszeiten: ' + esc(e.oh) + '</div>';
    if (e.ph) info += '<div class="sub">Telefon: <a href="tel:' + esc(e.ph.replace(/\s/g, '')) + '">' + esc(e.ph) + '</a></div>';
    sheet('<div style="display:flex;gap:12px;align-items:center"><div class="ico" style="width:44px;height:44px">' + svg(p[0], c.c, 24) + '</div><div><h2>' + esc(p[3] || c.n) + '</h2><div class="sub">' + c.n + (ref ? ' &middot; ' + fmtDist(dist(ref.lat, ref.lon, p[1], p[2])) + ' Luftlinie' : '') + '</div></div></div>' +
      '<div style="margin-top:8px">' + info + '</div><div class="btns" style="margin-top:14px"><button class="go" id="goRoute">Hierhin fahren</button></div>');
    $('goRoute').onclick = planRoute;
  }
  function showPoint(lat, lon) {
    state = 'point';
    var n = nearestNode(lat, lon);
    var nm = '';
    for (var k = 2; k < adj[n].length; k += 3) { nm = D.names[D.ways[adj[n][k]][1]]; if (nm) break; }
    var near = nearby(lat, lon, null, 1)[0];
    var place = D.places.slice().sort(function (a, b) { return dist(lat, lon, a[1], a[2]) - dist(lat, lon, b[1], b[2]); })[0];
    dest = { lat: lat, lon: lon, name: nm || ('Bei ' + place[0]) };
    setDestMarker(lat, lon);
    sheet('<h2>' + esc(dest.name) + '</h2><div class="sub">Ausgewaehlter Punkt bei ' + esc(place[0]) + '</div>' +
      (near && near.d < 400 ? '<div class="list" style="margin-top:8px">' + poiRow(near) + '</div>' : '') +
      '<div class="btns" style="margin-top:12px"><button class="go" id="goRoute">Hierhin fahren</button></div>');
    $('goRoute').onclick = planRoute;
  }

  map.on('click', function (e) {
    if (nav) return;
    $('results').style.display = 'none';
    if (!bounds.pad(0.02).contains(e.latlng)) return;
    showPoint(e.latlng.lat, e.latlng.lng);
  });

  function planRoute() {
    var ref = myRef();
    startInfo = ref ? { lat: ref.lat, lon: ref.lon, name: 'Mein Standort' } : PREKO;
    var a = nearestNode(startInfo.lat, startInfo.lon), b = nearestNode(dest.lat, dest.lon);
    cur = route(a, b, mode);
    if (!cur) { toast('Keine Route gefunden.'); return; }
    state = 'route';
    drawRoute();
    map.fitBounds(L.latLngBounds(cur.nodes.map(function (n) { return [LAT[n], LON[n]]; })), { paddingTopLeft: [30, 120], paddingBottomRight: [30, 330] });
    drawRouteSheet();
  }
  function drawRoute() {
    if (routeLayer) map.removeLayer(routeLayer);
    var ll = cur.nodes.map(function (n) { return [LAT[n], LON[n]]; });
    routeLayer = L.layerGroup([
      L.polyline(ll, { renderer: canvas, color: '#14181d', weight: 11, opacity: 0.9, interactive: false, lineJoin: 'round' }),
      L.polyline(ll, { renderer: canvas, color: '#c8f53c', weight: 6, opacity: 1, interactive: false, lineJoin: 'round' }),
      L.polyline(ll, { renderer: canvas, color: '#14181d', weight: 1.5, dashArray: '1 9', opacity: 0.8, interactive: false })
    ]).addTo(map);
  }
  function mixBar(r) {
    var parts = [[0, 'Radweg'], [2, 'Feldweg'], [8, 'Grob'], [3, 'Pfad'], [1, 'Ruhig'], [4, 'Fussweg'], [5, 'Landstr.'], [6, 'Hauptstr.']];
    var bar = '', leg = '';
    parts.forEach(function (x) {
      var v = r.mix[x[0]] || 0; if (v < 1) return; var pc = v / r.len * 100;
      bar += '<div style="width:' + pc + '%;background:' + CLASS[x[0]].c + '"></div>';
      if (pc >= 3) leg += '<span><i style="background:' + CLASS[x[0]].c + '"></i>' + x[1] + ' ' + Math.round(pc) + '%</span>';
    });
    return '<div class="mix">' + bar + '</div><div class="legend">' + leg + '</div>';
  }
  function drawRouteSheet() {
    sheet('<h2>' + esc(dest.name) + '</h2><div class="sub">Start: ' + esc(startInfo.name) + (startInfo === PREKO ? ' (du bist nicht auf der Insel)' : '') + '</div>' +
      '<div class="modes">' + Object.keys(MODES).map(function (k) { return '<button class="chip' + (k === mode ? ' on' : '') + '" data-m="' + k + '">' + MODES[k].n + '</button>'; }).join('') + '</div>' +
      '<div class="stats"><div class="stat"><b>' + fmtDist(cur.len) + '</b><span>Strecke</span></div><div class="stat"><b>' + fmtTime(cur.secs) + '</b><span>Fahrzeit</span></div><div class="stat"><b>' + clock(cur.secs) + '</b><span>Ankunft</span></div></div>' +
      mixBar(cur) +
      '<div class="btns" style="margin-top:14px"><button class="go sec" id="cancel">Abbrechen</button><button class="go" id="start">Los fahren</button></div>');
    $('sheet').querySelectorAll('[data-m]').forEach(function (b) {
      b.onclick = function () { mode = b.getAttribute('data-m'); try { localStorage.setItem('mode', mode); } catch (e) {} planRoute(); };
    });
    $('cancel').onclick = clearRoute;
    $('start').onclick = startNav;
  }
  function clearRoute() {
    if (routeLayer) map.removeLayer(routeLayer); if (destMarker) map.removeLayer(destMarker);
    if (tourLayer) map.removeLayer(tourLayer);
    routeLayer = destMarker = tourLayer = null; cur = null; dest = null; drawHome();
  }

  // ---------- Navigation ----------
  function startNav() {
    nav = { idx: 0, off: 0, along: 0 };
    state = 'nav';
    follow = true; $('btnLoc').classList.add('on');
    if (window.Android) Android.keepScreenOn(true);
    $('top').classList.add('hide'); $('chips').classList.add('hide'); $('results').style.display = 'none';
    $('nav').style.display = 'flex';
    var ref = myRef();
    if (ref) map.setView([ref.lat, ref.lon], 17); else map.setView([LAT[cur.nodes[0]], LON[cur.nodes[0]]], 17);
    if (!ref) toast('Ohne GPS auf der Insel: Vorschau ab Faehre Preko.');
    navUpdate();
  }
  function stopNav() {
    nav = null; if (window.Android) Android.keepScreenOn(false);
    $('top').classList.remove('hide'); $('chips').classList.remove('hide'); $('nav').style.display = 'none';
    clearRoute();
  }
  function project(lat, lon) {
    // naechster Punkt auf der Route (Suche ab letztem Index, dann global)
    var best = { d: 1e18, along: 0, i: 0 };
    function seg(i) {
      var a = cur.nodes[i], b = cur.nodes[i + 1];
      var kx = 111320 * Math.cos(lat * Math.PI / 180), ky = 110540;
      var ax = (LON[a] - lon) * kx, ay = (LAT[a] - lat) * ky, bx = (LON[b] - lon) * kx, by = (LAT[b] - lat) * ky;
      var dx = bx - ax, dy = by - ay, l2 = dx * dx + dy * dy, t = l2 ? Math.max(0, Math.min(1, -(ax * dx + ay * dy) / l2)) : 0;
      var px = ax + t * dx, py = ay + t * dy, d = Math.sqrt(px * px + py * py);
      if (d < best.d) best = { d: d, along: cur.cum[i] + t * (cur.cum[i + 1] - cur.cum[i]), i: i };
    }
    for (var i = 0; i + 1 < cur.nodes.length; i++) seg(i);
    return best;
  }
  function arrowFor(turn) {
    if (Math.abs(turn) < 25) return 'M24 42 L24 8 M13 19 L24 8 L35 19';
    var right = turn > 0, sharp = Math.abs(turn) > 120;
    if (Math.abs(turn) < 55) return right ? 'M18 42 L18 26 L34 10 M22 10 L34 10 L34 22' : 'M30 42 L30 26 L14 10 M26 10 L14 10 L14 22';
    if (sharp) return right ? 'M14 42 L14 12 L32 30 M32 18 L32 30 L20 30' : 'M34 42 L34 12 L16 30 M16 18 L16 30 L28 30';
    return right ? 'M14 42 L14 20 L38 20 M30 12 L38 20 L30 28' : 'M34 42 L34 20 L10 20 M18 12 L10 20 L18 28';
  }
  function navUpdate() {
    if (!nav || !cur) return;
    var ref = myRef();
    var along = 0;
    if (ref) {
      var pr = project(ref.lat, ref.lon);
      if (pr.d > 45 + Math.min(ref.acc, 40)) {
        nav.off++;
        if (nav.off >= 2) {
          nav.off = 0; startInfo = { lat: ref.lat, lon: ref.lon, name: 'Mein Standort' };
          var r = route(nearestNode(ref.lat, ref.lon), cur.nodes[cur.nodes.length - 1], mode);
          if (r) { cur = r; drawRoute(); toast('Neue Route berechnet'); pr = project(ref.lat, ref.lon); }
        }
      } else nav.off = 0;
      along = pr.along;
    }
    var remain = Math.max(0, cur.len - along);
    var step = cur.steps.filter(function (s) { return s.at > along + 5; })[0] || cur.steps[cur.steps.length - 1];
    var toStep = Math.max(0, step.at - along);
    var avg = cur.secs > 0 ? cur.len / cur.secs : 4.4;
    if (lastSpeed > 6) avg = (avg + lastSpeed / 3.6) / 2;
    var secs = remain / avg;
    if (remain < 25 && ref) {
      $('navDist').textContent = 'Angekommen'; $('navText').textContent = dest ? dest.name : '';
      $('navPath').setAttribute('d', 'M10 26 L20 36 L38 14');
    } else {
      $('navDist').textContent = fmtDist(toStep);
      $('navText').textContent = step.end ? 'Ziel: ' + (dest ? dest.name : '') : step.text + (step.name ? ' in ' + step.name : ' auf ' + CLASS[step.cls].n);
      $('navPath').setAttribute('d', step.end ? 'M14 42 L14 6 L36 14 L14 22' : arrowFor(step.turn));
    }
    sheet('<div class="stats" style="margin-top:0"><div class="stat"><b>' + fmtDist(remain) + '</b><span>Noch</span></div><div class="stat"><b>' + fmtTime(secs) + '</b><span>Restzeit</span></div><div class="stat"><b>' + clock(secs) + '</b><span>Ankunft</span></div></div>' +
      '<div class="sub" style="margin-bottom:10px">' + (lastSpeed >= 0 && ref ? 'Tempo ' + Math.round(lastSpeed) + ' km/h &middot; ' : '') + 'Ziel: ' + esc(dest ? dest.name : '') + '</div>' +
      '<button class="go sec" id="stopNav">Navigation beenden</button>');
    $('stopNav').onclick = stopNav;
  }

  // ---------- Radrouten ----------
  function showTours() {
    state = 'tours';
    sheet('<h2>Radrouten auf Ugljan</h2><div class="sub">Markierte Routen. Antippen zum Anzeigen.</div><div class="list">' +
      D.routes.map(function (r, i) {
        return '<div class="row" data-tour="' + i + '"><div class="ico">' + svg('bike', '#ffb020', 18) + '</div><div style="flex:1"><div class="t">' + esc(r.name) + '</div><div class="s">' + (r.ref ? 'Nr. ' + esc(r.ref) + ' &middot; ' : '') + 'auf der Insel</div></div><div class="s">' + fmtDist(r.len) + '</div></div>';
      }).join('') + '</div>');
  }
  function showTour(i) {
    var r = D.routes[i];
    state = 'tour';
    if (tourLayer) map.removeLayer(tourLayer);
    var lines = r.w.map(function (wi) { return wayLatLngs(D.ways[wi]); });
    tourLayer = L.layerGroup([
      L.polyline(lines, { renderer: canvas, color: '#14181d', weight: 11, opacity: 0.9, interactive: false }),
      L.polyline(lines, { renderer: canvas, color: '#ffb020', weight: 6, interactive: false })
    ]).addTo(map);
    var b = L.latLngBounds([].concat.apply([], lines));
    map.fitBounds(b, { paddingTopLeft: [30, 120], paddingBottomRight: [30, 300] });
    // Startpunkt = naechstes Routenende zum Nutzer
    var ref = myRef() || PREKO, ends = [];
    r.w.forEach(function (wi) { var p = D.ways[wi][3]; ends.push(p[0], p[p.length - 1]); });
    ends.sort(function (a, b2) { return dist(ref.lat, ref.lon, LAT[a], LON[a]) - dist(ref.lat, ref.lon, LAT[b2], LON[b2]); });
    var s = ends[0];
    var ty = {}; r.w.forEach(function (wi) { var c = D.ways[wi][0]; ty[CLASS[c].n] = 1; });
    sheet('<h2>' + esc(r.name) + '</h2><div class="sub">' + Object.keys(ty).join(', ') + '</div>' +
      '<div class="stats"><div class="stat"><b>' + fmtDist(r.len) + '</b><span>Laenge</span></div><div class="stat"><b>' + fmtTime(r.len / 3.6) + '</b><span>ca. Fahrzeit</span></div></div>' +
      '<div class="btns"><button class="go sec" id="back">Zurueck</button><button class="go" id="toStart">Zum Routenanfang</button></div>');
    $('back').onclick = function () { if (tourLayer) map.removeLayer(tourLayer); tourLayer = null; showTours(); };
    $('toStart').onclick = function () { dest = { lat: LAT[s], lon: LON[s], name: 'Start ' + r.name }; setDestMarker(LAT[s], LON[s]); planRoute(); };
  }
  $('btnTours').onclick = function () { if (!nav) showTours(); };

  $('sheet').addEventListener('click', function (e) {
    var r = e.target.closest('[data-poi]');
    if (r) { var p = D.pois[+r.getAttribute('data-poi')]; map.setView([p[1], p[2]], 16); showPoi(+r.getAttribute('data-poi')); return; }
    var t = e.target.closest('[data-tour]');
    if (t) showTour(+t.getAttribute('data-tour'));
  });

  // ---------- Suche ----------
  function norm(s) { return s.toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/đ/g, 'd'); }
  $('search').addEventListener('input', function () {
    var q = norm(this.value.trim());
    var box = $('results');
    if (!q) { box.style.display = 'none'; return; }
    var ref = myRef() || PREKO;
    var res = [];
    D.places.forEach(function (p) { if (norm(p[0]).indexOf(q) >= 0) res.push({ place: p, d: dist(ref.lat, ref.lon, p[1], p[2]) }); });
    D.pois.forEach(function (p, i) {
      var c = CAT[p[0]];
      if (norm((p[3] || '') + ' ' + c.n + ' ' + (p[4].cu || '')).indexOf(q) >= 0) res.push({ i: i, d: dist(ref.lat, ref.lon, p[1], p[2]) });
    });
    res.sort(function (a, b) { return (a.place ? 0 : 1) - (b.place ? 0 : 1) || a.d - b.d; });
    res = res.slice(0, 25);
    box.innerHTML = res.length ? res.map(function (x, k) {
      if (x.place) return '<div class="row" data-place="' + D.places.indexOf(x.place) + '"><div class="ico">' + svg('pin', '#c8f53c', 18) + '</div><div style="flex:1"><div class="t">' + esc(x.place[0]) + '</div><div class="s">Ort</div></div><div class="s">' + fmtDist(x.d) + '</div></div>';
      return poiRow(x);
    }).join('') : '<div class="row"><div class="s">Nichts gefunden</div></div>';
    box.style.display = 'block';
  });
  $('results').addEventListener('click', function (e) {
    var r = e.target.closest('.row'); if (!r) return;
    $('results').style.display = 'none'; $('search').blur(); $('search').value = '';
    if (r.hasAttribute('data-place')) {
      var p = D.places[+r.getAttribute('data-place')];
      map.setView([p[1], p[2]], 15); showPoint(p[1], p[2]); dest.name = p[0]; $('sheet').querySelector('h2').textContent = p[0];
    } else if (r.hasAttribute('data-poi')) {
      var i = +r.getAttribute('data-poi'); map.setView([D.pois[i][1], D.pois[i][2]], 16); showPoi(i);
    }
  });

  window.goBack = function () {
    if ($('results').style.display === 'block') { $('results').style.display = 'none'; return true; }
    if (nav) { stopNav(); return true; }
    if (state === 'tour') { if (tourLayer) map.removeLayer(tourLayer); tourLayer = null; showTours(); return true; }
    if (state !== 'home') { clearRoute(); return true; }
    return false;
  };

  drawHome();
})();
