/* Ugljan Rad: Offline-Radkarte, Routing mit Höhen- und Fahrphysik, Navigation für die Insel Ugljan */
(function () {
  'use strict';
  var D = window.UGLJAN;
  var $ = function (id) { return document.getElementById(id); };
  var A = window.Android || null;
  var RAD = Math.PI / 180;

  // =====================================================================
  // Hilfsfunktionen
  // =====================================================================
  function dist(la1, lo1, la2, lo2) {
    var dx = (lo2 - lo1) * 111320 * Math.cos(la1 * RAD), dy = (la2 - la1) * 110540;
    return Math.sqrt(dx * dx + dy * dy);
  }
  function bearing(la1, lo1, la2, lo2) {
    var dx = (lo2 - lo1) * Math.cos(la1 * RAD), dy = la2 - la1;
    return (Math.atan2(dx, dy) / RAD + 360) % 360;
  }
  function esc(s) { return String(s == null ? '' : s).replace(/[&<>"]/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]; }); }
  function fmtDist(m) {
    if (m < 1000) return (m < 100 ? Math.round(m / 5) * 5 : Math.round(m / 10) * 10) + ' m';
    return (m / 1000).toFixed(m < 10000 ? 1 : 0).replace('.', ',') + ' km';
  }
  function fmtKm(m) { return (m / 1000).toFixed(1).replace('.', ','); }
  function fmtTime(s) { var m = Math.max(1, Math.round(s / 60)); return m < 60 ? m + ' min' : Math.floor(m / 60) + ' h ' + ('0' + (m % 60)).slice(-2); }
  function hhmm(d) { return ('0' + d.getHours()).slice(-2) + ':' + ('0' + d.getMinutes()).slice(-2); }
  function clock(s) { return hhmm(new Date(Date.now() + s * 1000)); }
  function pct(g) { return Math.round(g * 100) + ' %'; }
  function store(k, v) { try { localStorage.setItem(k, JSON.stringify(v)); } catch (e) {} }
  function load(k, d) { try { var v = localStorage.getItem(k); return v ? JSON.parse(v) : d; } catch (e) { return d; } }
  function norm(s) { return String(s).toLowerCase().replace(/đ/g, 'd').normalize('NFD').replace(/[̀-ͯ]/g, ''); }

  // =====================================================================
  // Daten
  // =====================================================================
  var N = D.nodes.length / 4;
  var LAT = new Float64Array(N), LON = new Float64Array(N), ELE = new Float32Array(N), SC = new Float32Array(N), COAST = new Uint8Array(N);
  for (var i = 0; i < N; i++) {
    LAT[i] = D.nodes[4 * i] / 1e5; LON[i] = D.nodes[4 * i + 1] / 1e5; ELE[i] = D.nodes[4 * i + 2] / 10;
    var sv = D.nodes[4 * i + 3]; SC[i] = (sv % 10) / 9; COAST[i] = sv >= 10 ? 1 : 0;
  }
  var DEM = D.dem;
  function demEle(lat, lon) {
    var y = (lat - DEM.s) / DEM.dlat, x = (lon - DEM.w) / DEM.dlon;
    var r = Math.max(0, Math.min(DEM.rows - 2, Math.floor(y))), c = Math.max(0, Math.min(DEM.cols - 2, Math.floor(x)));
    var fy = Math.max(0, Math.min(1, y - r)), fx = Math.max(0, Math.min(1, x - c)), z = DEM.z, C = DEM.cols;
    return Math.max(0, z[r * C + c] * (1 - fx) * (1 - fy) + z[r * C + c + 1] * fx * (1 - fy) + z[(r + 1) * C + c] * (1 - fx) * fy + z[(r + 1) * C + c + 1] * fx * fy);
  }

  var CLASS = [
    { n: 'Radweg', c: '#c8f53c', w: 4.5 },
    { n: 'Ruhige Straße', c: '#8e9aa6', w: 2.6 },
    { n: 'Feldweg', c: '#c49a6c', w: 2.6, d: '7 5' },
    { n: 'Pfad', c: '#b09572', w: 2, d: '2 5' },
    { n: 'Fußweg', c: '#76818c', w: 1.6, d: '2 4' },
    { n: 'Landstraße', c: '#dde3e8', w: 3.6 },
    { n: 'Hauptstraße', c: '#ff5a4e', w: 4.2 },
    { n: 'Hauptstraße', c: '#ff5a4e', w: 4.2 },
    { n: 'Grober Weg', c: '#8a6c4c', w: 2.2, d: '4 4' }
  ];
  var SURF = [
    { n: 'Asphalt', c: '#c8f53c' }, { n: 'Pflaster', c: '#9fb3c4' }, { n: 'Verdichtet', c: '#e0c27a' },
    { n: 'Schotter', c: '#c49a6c' }, { n: 'Erde', c: '#9a7650' }, { n: 'Fels', c: '#ff5a4e' }
  ];

  // =====================================================================
  // Einstellungen
  // =====================================================================
  var BIKES = {
    city: { n: 'City / Trekking', P: 105, m: 95, CdA: 0.58, crr: [0.006, 0.011, 0.011, 0.017, 0.026, 0.045], cap: [38, 24, 24, 20, 15, 8], push: 0.15, walkSurf: 9, pref: [1, 1.1, 1.08, 1.25, 1.6, 3.5] },
    road: { n: 'Rennrad', P: 160, m: 82, CdA: 0.34, crr: [0.0045, 0.013, 0.016, 0.03, 0.05, 0.1], cap: [58, 25, 22, 14, 9, 5], push: 0.18, walkSurf: 4, pref: [1, 1.5, 1.8, 3.2, 7, 12] },
    mtb: { n: 'Mountainbike', P: 145, m: 92, CdA: 0.55, crr: [0.0085, 0.011, 0.011, 0.014, 0.018, 0.028], cap: [48, 30, 36, 32, 28, 20], push: 0.3, walkSurf: 9, pref: [1.05, 1.05, 0.95, 0.88, 0.88, 1] },
    ebike: { n: 'E-Bike', P: 100, assist: 230, m: 115, CdA: 0.6, crr: [0.0065, 0.012, 0.012, 0.018, 0.027, 0.045], cap: [42, 25, 26, 23, 17, 10], push: 0.26, walkSurf: 9, pref: [1, 1.1, 1.05, 1.15, 1.4, 2.6] }
  };
  var FIT = { easy: { n: 'Gemütlich', f: 0.72 }, normal: { n: 'Normal', f: 1 }, sport: { n: 'Sportlich', f: 1.35 } };
  var STYLES = {
    fast: { n: 'Schnell & leicht', d: 'Kürzeste Fahrzeit mit möglichst wenig Anstrengung: steile Anstiege und grobe Wege werden gemieden, auch die Hauptstraße ist erlaubt.', cls: [0.92, 1, 1.05, 1.3, 1.7, 1, 1.05, 1.15, 1.25], route: 1, climb: 4, sc: 0 },
    scenic: { n: 'Am schönsten', d: 'Die schönste Strecke: am Meer entlang, durch Wald und Olivenhaine, vorbei an Aussichtspunkten und Stränden, auf Radrouten statt Hauptstraße. Egal ob anstrengend oder leicht.', cls: [0.75, 1, 1, 1.2, 2, 1.8, 4.5, 5.5, 1.15], route: 0.8, climb: 0, sc: 1.0 }
  };
  var CLS_CAP = [40, 35, 99, 99, 12, 99, 99, 99, 99];
  var S = load('settings', {});
  S = {
    bike: BIKES[S.bike] ? S.bike : 'city', fit: FIT[S.fit] ? S.fit : 'normal', style: STYLES[S.style] ? S.style : 'scenic', headUp: !!S.headUp,
    voice: S.voice !== false, wind: S.wind !== false, relief: S.relief !== false, contours: S.contours !== false,
    routes: S.routes !== false, labels: S.labels !== false
  };
  function saveS() { store('settings', S); }

  // =====================================================================
  // Graph (CSR) mit Kanten in Fahrtrichtung
  // =====================================================================
  var W = D.ways, outDeg = new Int32Array(N), uDeg = new Uint8Array(N);
  W.forEach(function (w) {
    var p = w[4], one = w[2] & 1;
    for (var k = 0; k + 1 < p.length; k++) {
      outDeg[p[k]]++; if (!one) outDeg[p[k + 1]]++;
      if (uDeg[p[k]] < 250) uDeg[p[k]]++; if (uDeg[p[k + 1]] < 250) uDeg[p[k + 1]]++;
    }
  });
  var OFF = new Int32Array(N + 1);
  for (i = 0; i < N; i++) OFF[i + 1] = OFF[i] + outDeg[i];
  var E = OFF[N];
  var eFrom = new Int32Array(E), eTo = new Int32Array(E), eWay = new Int32Array(E), eLen = new Float32Array(E), eRise = new Float32Array(E), eBear = new Float32Array(E);
  var eTime = new Float32Array(E), eCost = new Float32Array(E);
  (function () {
    var fill = new Int32Array(N);
    function add(a, b, wi) {
      var e = OFF[a] + fill[a]++;
      eFrom[e] = a; eTo[e] = b; eWay[e] = wi;
      eLen[e] = Math.max(0.5, dist(LAT[a], LON[a], LAT[b], LON[b]));
      eRise[e] = ELE[b] - ELE[a];
      eBear[e] = bearing(LAT[a], LON[a], LAT[b], LON[b]);
    }
    W.forEach(function (w, wi) {
      var p = w[4], one = w[2] & 1;
      for (var k = 0; k + 1 < p.length; k++) { add(p[k], p[k + 1], wi); if (!one) add(p[k + 1], p[k], wi); }
    });
  })();
  // Zusammenhangskomponenten, damit Start/Ziel nur auf das Hauptnetz springen
  var COMP = new Int32Array(N);
  (function () {
    var par = new Int32Array(N); for (var i = 0; i < N; i++) par[i] = i;
    function f(x) { while (par[x] !== x) { par[x] = par[par[x]]; x = par[x]; } return x; }
    W.forEach(function (w) { var p = w[4]; for (var k = 0; k + 1 < p.length; k++) { var a = f(p[k]), b = f(p[k + 1]); if (a !== b) par[a] = b; } });
    var cnt = {}, best = -1, bc = 0;
    for (i = 0; i < N; i++) { var r = f(i); cnt[r] = (cnt[r] || 0) + 1; if (cnt[r] > bc) { bc = cnt[r]; best = r; } }
    for (i = 0; i < N; i++) COMP[i] = f(i) === best ? 1 : 0;
    // nur Knoten, die hin UND zurück erreichbar sind (Einbahn-Sackgassen ausschliessen)
    var hub = -1;
    for (i = 0; i < N && hub < 0; i++) if (COMP[i] && outDeg[i] >= 3) hub = i;
    var rdeg = new Int32Array(N + 1);
    for (var e = 0; e < E; e++) rdeg[eTo[e] + 1]++;
    for (i = 0; i < N; i++) rdeg[i + 1] += rdeg[i];
    var rsrc = new Int32Array(E), rf = rdeg.slice(0, N);
    for (e = 0; e < E; e++) rsrc[rf[eTo[e]]++] = eFrom[e];
    var fw = new Uint8Array(N), bw = new Uint8Array(N), st = [hub];
    fw[hub] = 1;
    while (st.length) { var u = st.pop(); for (e = OFF[u]; e < OFF[u + 1]; e++) if (!fw[eTo[e]]) { fw[eTo[e]] = 1; st.push(eTo[e]); } }
    st = [hub]; bw[hub] = 1;
    while (st.length) { u = st.pop(); for (var k = rdeg[u]; k < rdeg[u + 1]; k++) if (!bw[rsrc[k]]) { bw[rsrc[k]] = 1; st.push(rsrc[k]); } }
    for (i = 0; i < N; i++) COMP[i] = fw[i] && bw[i] ? 1 : 0;
  })();

  // =====================================================================
  // Fahrphysik: Geschwindigkeit aus Leistung, Steigung, Belag, Wind
  // =====================================================================
  var WX = null; // Wetter
  function rideSpeed(B, P, g, s, cls, hw) {
    // liefert m/s; Schieben = 1.1 m/s
    if (g > B.push || s >= B.walkSurf) return 1.1;
    var cos = 1 / Math.sqrt(1 + g * g), sin = g * cos, m = B.m, crr = B.crr[s];
    var cap = Math.min(B.cap[s], CLS_CAP[cls]) / 3.6;
    if (g < -0.07 && s >= 2) cap *= Math.max(0.55, 1 + (g + 0.07) * 4); // steile Schotterabfahrt: bremsen
    var K = 0.5 * 1.2 * B.CdA, G = m * 9.81 * (crr * cos + sin);
    function solve(Pw) {
      var f = function (v) { var va = v + hw; return v * (G + K * va * Math.abs(va)) - Pw; };
      if (f(cap) <= 0) return cap;
      var lo = 0, hi = cap;
      for (var it = 0; it < 28; it++) { var mid = (lo + hi) / 2; if (f(mid) > 0) hi = mid; else lo = mid; }
      return lo;
    }
    var v = solve(P);
    if (B.assist) v = Math.max(v, Math.min(solve(P + B.assist), 25 / 3.6));
    if (v < 1.5) v = 1.1;
    return v;
  }
  var minCostPerM = 0.01, COST = {};
  Object.keys(STYLES).forEach(function (k) { COST[k] = { c: new Float32Array(E), m: 0.01 }; });
  // Kosten fuer beide Varianten gleichzeitig, damit man sie direkt vergleichen kann
  function computeCosts() {
    var B = BIKES[S.bike], P = B.P * FIT[S.fit].f, keys = Object.keys(STYLES);
    var useWind = S.wind && WX && WX.wind != null, Wms = useWind ? WX.wind / 3.6 * 0.65 : 0, wdir = useWind ? WX.dir : 0;
    var cache = new Map(), mins = keys.map(function () { return 1e9; });
    for (var e = 0; e < E; e++) {
      var w = W[eWay[e]], cls = w[0], s = w[3], L = eLen[e];
      var g = L > 4 ? Math.max(-0.35, Math.min(0.35, eRise[e] / L)) : 0;
      var hw = useWind ? Wms * Math.cos((eBear[e] - wdir) * RAD) : 0;
      var key = (Math.round(g * 400) * 64 + s * 9 + cls) * 100 + Math.round(hw * 2);
      var v = cache.get(key);
      if (v === undefined) { v = rideSpeed(B, P, g, s, cls, hw); cache.set(key, v); }
      var t = L / v;
      eTime[e] = t;
      var scE = (SC[eFrom[e]] + SC[eTo[e]]) / 2;
      for (var ki = 0; ki < keys.length; ki++) {
        var st = STYLES[keys[ki]];
        var c = t * st.cls[cls] * B.pref[s] * ((w[2] & 2) ? st.route : 1) * (1 - st.sc * (scE - 0.35));
        if (st.climb && eRise[e] > 0) c += eRise[e] * st.climb * (g > 0.06 ? 2 : 1);
        COST[keys[ki]].c[e] = c;
        var cpm = c / L; if (cpm < mins[ki]) mins[ki] = cpm;
      }
    }
    keys.forEach(function (k, ki) { COST[k].m = mins[ki]; });
    eCost = COST[S.style].c; minCostPerM = COST[S.style].m;
  }
  computeCosts();

  // =====================================================================
  // Routing
  // =====================================================================
  function nearestNode(lat, lon) {
    var best = -1, bd = 1e18, kx = Math.cos(lat * RAD) * 111320, ky = 110540;
    for (var i = 0; i < N; i++) {
      if (!COMP[i]) continue;
      var dx = (LON[i] - lon) * kx, dy = (LAT[i] - lat) * ky, d = dx * dx + dy * dy;
      if (d < bd) { bd = d; best = i; }
    }
    return best;
  }
  var G_ = new Float64Array(N), PE = new Int32Array(N), CL = new Uint8Array(N);
  function astar(src, dst, opt) {
    opt = opt || {};
    G_.fill(Infinity); PE.fill(-1); CL.fill(0);
    var heap = [], hk = [];
    function push(n, k) { heap.push(n); hk.push(k); var i = heap.length - 1; while (i > 0) { var p = (i - 1) >> 1; if (hk[p] <= hk[i]) break; sw(i, p); i = p; } }
    function sw(a, b) { var t = heap[a]; heap[a] = heap[b]; heap[b] = t; t = hk[a]; hk[a] = hk[b]; hk[b] = t; }
    function pop() {
      var top = heap[0], ln = heap.pop(), lk = hk.pop();
      if (heap.length) { heap[0] = ln; hk[0] = lk; var i = 0; for (;;) { var l = 2 * i + 1, r = l + 1, s = i; if (l < heap.length && hk[l] < hk[s]) s = l; if (r < heap.length && hk[r] < hk[s]) s = r; if (s === i) break; sw(i, s); i = s; } }
      return top;
    }
    var CS = COST[opt.style] || COST[S.style], cc = CS.c;
    var h = CS.m * 0.98, tl = LAT[dst], to = LON[dst], pref = opt.prefer, pen = opt.penal;
    G_[src] = 0; push(src, 0);
    while (heap.length) {
      var u = pop(); if (CL[u]) continue; CL[u] = 1;
      if (u === dst) break;
      for (var e = OFF[u]; e < OFF[u + 1]; e++) {
        var v = eTo[e]; if (CL[v]) continue;
        var c = cc[e];
        if (pref && !pref[eWay[e]]) c *= 25;
        if (pen && pen[v]) c *= 3.5;
        var ng = G_[u] + c;
        if (ng < G_[v]) { G_[v] = ng; PE[v] = e; push(v, ng + dist(LAT[v], LON[v], tl, to) * h); }
      }
    }
    if (G_[dst] === Infinity) return null;
    var edges = [], n = dst;
    while (n !== src) { var ed = PE[n]; edges.push(ed); n = eFrom[ed]; }
    return edges.reverse();
  }
  // Route über mehrere Punkte; pts: [{lat,lon,name,prefer?}]
  function routeVia(pts, opt) {
    opt = opt || {};
    var snaps = pts.map(function (p) { return p.node != null ? p.node : nearestNode(p.lat, p.lon); });
    var edges = [], legEnds = [], penal = opt.loop ? new Uint8Array(N) : null;
    for (var k = 0; k + 1 < snaps.length; k++) {
      if (snaps[k] === snaps[k + 1]) { legEnds.push(edges.length); continue; }
      var leg = astar(snaps[k], snaps[k + 1], { prefer: pts[k + 1].prefer, penal: penal, style: opt.style });
      if (!leg) return null;
      if (penal) leg.forEach(function (e) { penal[eTo[e]] = 1; });
      edges = edges.concat(leg); legEnds.push(edges.length);
    }
    var r = analyze(snaps[0], edges);
    r.pts = pts; r.legEnds = legEnds.map(function (ei) { return r.cum[ei]; });
    return r;
  }
  function analyze(src, edges) {
    var nodes = [src], cum = [0], ele = [ELE[src]], secs = 0, mixC = {}, mixS = {}, walk = 0, L = 0, coastLen = 0, beauty = 0;
    var B = BIKES[S.bike];
    for (var k = 0; k < edges.length; k++) {
      var e = edges[k], w = W[eWay[e]];
      nodes.push(eTo[e]); L += eLen[e]; cum.push(L); ele.push(ELE[eTo[e]]); secs += eTime[e];
      var c = w[0] === 7 ? 6 : w[0];
      mixC[c] = (mixC[c] || 0) + eLen[e]; mixS[w[3]] = (mixS[w[3]] || 0) + eLen[e];
      if (eTime[e] / eLen[e] > 0.85) walk += eLen[e];
      if (COAST[eFrom[e]] && COAST[eTo[e]]) coastLen += eLen[e];
      beauty += eLen[e] * (SC[eFrom[e]] + SC[eTo[e]]) / 2;
    }
    // Höhenmeter mit Hysterese
    var asc = 0, desc = 0, ref = ele[0];
    for (k = 1; k < ele.length; k++) { var d = ele[k] - ref; if (d > 3) { asc += d; ref = ele[k]; } else if (d < -3) { desc -= d; ref = ele[k]; } }
    // max. Steigung über 60 m
    var maxG = 0, j = 0;
    for (k = 0; k < cum.length; k++) {
      while (j < cum.length && cum[j] - cum[k] < 100) j++;
      if (j >= cum.length) break;
      var gg = (ele[j] - ele[k]) / (cum[j] - cum[k]); if (gg > maxG) maxG = gg;
    }
    var r = { src: src, edges: edges, nodes: nodes, cum: cum, ele: ele, len: L, secs: secs, asc: asc, desc: desc, maxG: maxG, mixC: mixC, mixS: mixS, walk: walk, coast: coastLen, beauty: L > 0 ? Math.round(beauty / L * 10) : 0 };
    r.climbs = findClimbs(r);
    r.steps = buildSteps(r);
    r.minEle = Math.min.apply(null, ele); r.maxEle = Math.max.apply(null, ele);
    var rough = ((mixS[3] || 0) + (mixS[4] || 0) * 1.5 + (mixS[5] || 0) * 3) / Math.max(1, L);
    var score = asc / Math.max(0.5, L / 1000) + maxG * 60 + rough * 15 + L / 4000;
    r.diff = score < 12 ? 0 : score < 24 ? 1 : 2;
    return r;
  }
  function findClimbs(r) {
    var out = [], ele = r.ele, cum = r.cum, lo = 0, hi = 0;
    function close() {
      var gain = ele[hi] - ele[lo], len = cum[hi] - cum[lo];
      if (gain >= 20 && len > 0 && gain / len >= 0.03) out.push({ a: cum[lo], b: cum[hi], gain: gain, len: len, g: gain / len, top: ele[hi] });
    }
    for (var k = 1; k < ele.length; k++) {
      if (ele[k] > ele[hi]) hi = k;
      if (ele[k] < ele[hi] - 12 || k === ele.length - 1) { if (hi > lo) close(); lo = k; hi = k; }
      else if (ele[k] < ele[lo] && hi === lo) { lo = k; hi = k; }
    }
    return out;
  }
  function alongNode(r, i, dir, m) { var j = i; while (j + dir >= 0 && j + dir < r.nodes.length && Math.abs(r.cum[j] - r.cum[i]) < m) j += dir; return r.nodes[j]; }
  function buildSteps(r) {
    var steps = [], nodes = r.nodes;
    for (var i = 1; i + 1 < nodes.length; i++) {
      var n = nodes[i];
      if (uDeg[n] < 3) continue;
      var a = alongNode(r, i, -1, 20), b = alongNode(r, i, 1, 20);
      var t = ((bearing(LAT[n], LON[n], LAT[b], LON[b]) - bearing(LAT[a], LON[a], LAT[n], LON[n]) + 540) % 360) - 180;
      var w1 = W[eWay[r.edges[i - 1]]], w2 = W[eWay[r.edges[i]]];
      var changed = w1[1] !== w2[1] || w1[0] !== w2[0];
      if (Math.abs(t) < 28 || (!changed && Math.abs(t) < 50)) continue;
      if (steps.length && r.cum[i] - steps[steps.length - 1].at < 20) continue;
      var at = Math.abs(t);
      var txt = at < 55 ? (t < 0 ? 'Leicht links' : 'Leicht rechts') : at < 130 ? (t < 0 ? 'Links abbiegen' : 'Rechts abbiegen') : (t < 0 ? 'Scharf links' : 'Scharf rechts');
      var say = at < 55 ? (t < 0 ? 'leicht links halten' : 'leicht rechts halten') : at < 130 ? (t < 0 ? 'links abbiegen' : 'rechts abbiegen') : (t < 0 ? 'scharf links abbiegen' : 'scharf rechts abbiegen');
      steps.push({ at: r.cum[i], turn: t, text: txt, say: say, name: D.names[w2[1]], cls: w2[0], surf: w2[3] });
    }
    steps.push({ at: r.len, turn: 0, text: 'Ziel erreicht', say: 'Du hast dein Ziel erreicht', name: '', end: true });
    return steps;
  }

  // =====================================================================
  // Karte: MapLibre GL (von der Grafikkarte gezeichnet, fluessiges Drehen und Zoomen, offline)
  // =====================================================================
  var fs = false; // Vollbild
  function Bounds(s, w, n, e) { this.s = s; this.w = w; this.n = n; this.e = e; }
  Bounds.prototype.pad = function (f) { var dl = (this.n - this.s) * f, dw = (this.e - this.w) * f; return new Bounds(this.s - dl, this.w - dw, this.n + dl, this.e + dw); };
  Bounds.prototype.contains = function (p) {
    var la = p.lat != null ? p.lat : p[0], lo = p.lng != null ? p.lng : p.lon != null ? p.lon : p[1];
    return la >= this.s && la <= this.n && lo >= this.w && lo <= this.e;
  };
  Bounds.prototype.ml = function () { return [[this.w, this.s], [this.e, this.n]]; };
  function boundsOf(pts) {
    var b = new Bounds(90, 180, -90, -180);
    pts.forEach(function (p) { b.s = Math.min(b.s, p[0]); b.n = Math.max(b.n, p[0]); b.w = Math.min(b.w, p[1]); b.e = Math.max(b.e, p[1]); });
    return b;
  }
  var bb = D.bbox, bounds = new Bounds(bb[0], bb[1], bb[2], bb[3]);
  var BASE = location.href.replace(/[#?].*$/, '').replace(/[^\/]*$/, '');
  function fc(f) { return { type: 'FeatureCollection', features: f || [] }; }
  function polyF(r, pr) { return { type: 'Feature', properties: pr || {}, geometry: { type: 'Polygon', coordinates: [r.map(function (p) { return [p[1], p[0]]; })] } }; }
  function lineF(ll, pr) { return { type: 'Feature', properties: pr || {}, geometry: { type: 'LineString', coordinates: ll.map(function (p) { return [p[1], p[0]]; }) } }; }
  function ptF(lat, lon, pr) { return { type: 'Feature', properties: pr || {}, geometry: { type: 'Point', coordinates: [lon, lat] } }; }
  function wayLL(w) { return w[4].map(function (n) { return [LAT[n], LON[n]]; }); }
  // Linienbreite waechst mit dem Zoom wie bei Leaflet-Pixeln auf Stufe 14
  function wz(base) { return ['interpolate', ['exponential', 1.5], ['zoom'], 10, ['*', 0.3, base], 14, base, 17, ['*', 1.5, base], 19, ['*', 2.1, base]]; }

  var ORDER = { 4: 0, 3: 1, 8: 2, 2: 3, 1: 4, 5: 5, 6: 6, 7: 7, 0: 8 };
  var roadF = W.map(function (w) { var s = CLASS[w[0]]; return lineF(wayLL(w), { c: w[0], r: (w[2] & 2) ? 1 : 0, col: s.c, w: s.w, o: ORDER[w[0]] }); });
  var contF = [], contLblF = [];
  (function () {
    var longest = {};
    D.contours.forEach(function (c) {
      var major = c[0] % 100 === 0;
      contF.push(lineF(c[1], { major: major ? 1 : 0 }));
      if (major && c[1].length > 6) (longest[c[0]] = longest[c[0]] || []).push(c);
    });
    Object.keys(longest).forEach(function (lvl) {
      longest[lvl].sort(function (a, b) { return b[1].length - a[1].length; }).slice(0, 3).forEach(function (c) {
        var p = c[1][Math.floor(c[1].length / 2)]; contLblF.push(ptF(p[0], p[1], { t: lvl }));
      });
    });
  })();
  var placeF = D.places.map(function (p) {
    var t = p[3], k = (t === 'village' || t === 'town') ? 'v' : (t === 'bay' || t === 'cape' || t === 'peninsula') ? 'b' : (t === 'island' || t === 'islet') ? 'i' : t === 'locality' ? 'l' : 's';
    return ptF(p[1], p[2], { t: p[0], k: k });
  });
  var peakF = D.pois.filter(function (p) { return p[0] === 'peak' && p[3]; }).map(function (p) { return ptF(p[1], p[2], { t: p[3] + (p[4].ele ? ' ' + p[4].ele + ' m' : '') }); });
  var poiF = D.pois.map(function (p, i) { return ptF(p[1], p[2], { i: i, cat: p[0], icon: 'poi-' + p[0] }); }).filter(function (f) { return f.properties.cat !== 'peak'; });
  var rl = D.relief;
  var FONT = ['NotoSansMedium'], HALO = { 'text-halo-color': '#05080b', 'text-halo-width': 1.6, 'text-halo-blur': 0.4 };
  var style = {
    version: 8,
    glyphs: BASE + 'glyphs/{fontstack}/{range}.pbf',
    sources: {
      others: { type: 'geojson', data: fc(D.others.map(function (r) { return polyF(r); })) },
      land: { type: 'geojson', data: fc(D.land.map(function (r) { return polyF(r); })) },
      green: { type: 'geojson', data: fc(D.green.map(function (r) { return polyF(r); })) },
      built: { type: 'geojson', data: fc((D.built || []).map(function (r) { return polyF(r); })) },
      relief: { type: 'image', url: BASE + 'relief.png', coordinates: [[rl[0][1], rl[1][0]], [rl[1][1], rl[1][0]], [rl[1][1], rl[0][0]], [rl[0][1], rl[0][0]]] },
      contours: { type: 'geojson', data: fc(contF) }, contLbl: { type: 'geojson', data: fc(contLblF) },
      roads: { type: 'geojson', data: fc(roadF), tolerance: 0.2 },
      places: { type: 'geojson', data: fc(placeF) }, peaks: { type: 'geojson', data: fc(peakF) },
      pois: { type: 'geojson', data: fc(poiF) },
      acc: { type: 'geojson', data: fc() }, tour: { type: 'geojson', data: fc() }, track: { type: 'geojson', data: fc() },
      route: { type: 'geojson', data: fc() }, cand: { type: 'geojson', data: fc() }, hover: { type: 'geojson', data: fc() }
    },
    layers: [
      { id: 'bg', type: 'background', paint: { 'background-color': '#0d1b26' } },
      { id: 'others', type: 'fill', source: 'others', paint: { 'fill-color': '#1a241f' } },
      { id: 'coast', type: 'line', source: 'land', paint: { 'line-color': '#28485c', 'line-width': wz(7), 'line-opacity': 0.55 } },
      { id: 'land', type: 'fill', source: 'land', paint: { 'fill-color': '#1f2b24' } },
      { id: 'green', type: 'fill', source: 'green', paint: { 'fill-color': '#223a29' } },
      { id: 'built', type: 'fill', source: 'built', paint: { 'fill-color': '#2c342f' } },
      { id: 'relief', type: 'raster', source: 'relief', paint: { 'raster-opacity': 0.85, 'raster-fade-duration': 0, 'raster-resampling': 'linear' } },
      { id: 'contours', type: 'line', source: 'contours', minzoom: 12.5, paint: { 'line-color': '#a6c08f', 'line-width': ['case', ['==', ['get', 'major'], 1], 1.3, 0.7], 'line-opacity': ['case', ['==', ['get', 'major'], 1], 0.42, 0.24] } },
      { id: 'routeglow', type: 'line', source: 'roads', filter: ['==', ['get', 'r'], 1], layout: { 'line-cap': 'round', 'line-join': 'round' }, paint: { 'line-color': '#ffb020', 'line-width': wz(11), 'line-opacity': 0.26 } },
      { id: 'r-foot', type: 'line', source: 'roads', filter: ['==', ['get', 'c'], 4], paint: { 'line-color': ['get', 'col'], 'line-width': wz(['get', 'w']), 'line-dasharray': [1.2, 2.4] } },
      { id: 'r-path', type: 'line', source: 'roads', filter: ['==', ['get', 'c'], 3], paint: { 'line-color': ['get', 'col'], 'line-width': wz(['get', 'w']), 'line-dasharray': [1, 2.5] } },
      { id: 'r-rough', type: 'line', source: 'roads', filter: ['==', ['get', 'c'], 8], paint: { 'line-color': ['get', 'col'], 'line-width': wz(['get', 'w']), 'line-dasharray': [1.8, 1.8] } },
      { id: 'r-track', type: 'line', source: 'roads', filter: ['==', ['get', 'c'], 2], paint: { 'line-color': ['get', 'col'], 'line-width': wz(['get', 'w']), 'line-dasharray': [2.7, 1.9] } },
      { id: 'r-casing', type: 'line', source: 'roads', filter: ['in', ['get', 'c'], ['literal', [5, 6, 7]]], layout: { 'line-join': 'round', 'line-sort-key': ['get', 'o'] }, paint: { 'line-color': '#14181d', 'line-width': wz(['+', 2.5, ['get', 'w']]), 'line-opacity': 0.7 } },
      { id: 'r-solid', type: 'line', source: 'roads', filter: ['in', ['get', 'c'], ['literal', [0, 1, 5, 6, 7]]], layout: { 'line-cap': 'round', 'line-join': 'round', 'line-sort-key': ['get', 'o'] }, paint: { 'line-color': ['get', 'col'], 'line-width': wz(['get', 'w']), 'line-opacity': 0.95 } },
      { id: 'tour', type: 'line', source: 'tour', layout: { 'line-cap': 'round', 'line-join': 'round' }, paint: { 'line-color': '#ff5a4e', 'line-width': wz(5) } },
      { id: 'cand-casing', type: 'line', source: 'cand', layout: { 'line-cap': 'round', 'line-join': 'round' }, paint: { 'line-color': '#14181d', 'line-width': wz(11), 'line-opacity': 0.9 } },
      { id: 'cand', type: 'line', source: 'cand', layout: { 'line-cap': 'round', 'line-join': 'round', 'line-sort-key': ['case', ['==', ['get', 'k'], 'scenic'], 1, 0] }, paint: { 'line-color': ['get', 'col'], 'line-width': wz(6) } },
      { id: 'route-casing', type: 'line', source: 'route', layout: { 'line-cap': 'round', 'line-join': 'round' }, paint: { 'line-color': '#14181d', 'line-width': wz(12), 'line-opacity': 0.92 } },
      { id: 'route', type: 'line', source: 'route', layout: { 'line-cap': 'round', 'line-join': 'round' }, paint: { 'line-color': ['get', 'col'], 'line-width': wz(6.5) } },
      { id: 'track', type: 'line', source: 'track', layout: { 'line-cap': 'round', 'line-join': 'round' }, paint: { 'line-color': '#ff5a4e', 'line-width': wz(4), 'line-opacity': 0.85 } },
      { id: 'acc-fill', type: 'fill', source: 'acc', paint: { 'fill-color': '#c8f53c', 'fill-opacity': 0.07 } },
      { id: 'acc-line', type: 'line', source: 'acc', paint: { 'line-color': '#c8f53c', 'line-width': 1, 'line-opacity': 0.4 } },
      { id: 'hover', type: 'circle', source: 'hover', paint: { 'circle-radius': 8, 'circle-color': '#ffffff', 'circle-stroke-color': '#14181d', 'circle-stroke-width': 3 } },
      { id: 'cont-lbl', type: 'symbol', source: 'contLbl', minzoom: 14.5, layout: { 'text-field': ['get', 't'], 'text-font': ['NotoSansRegular'], 'text-size': 10, 'text-rotation-alignment': 'viewport' }, paint: L_({ 'text-color': '#a3b29a' }) },
      { id: 'peaks', type: 'symbol', source: 'peaks', minzoom: 13.5, layout: { 'text-field': ['get', 't'], 'text-font': FONT, 'text-size': 11, 'text-offset': [0, 0.9], 'text-anchor': 'top', 'text-optional': true }, paint: L_({ 'text-color': '#e9d8b4' }) },
      { id: 'pois', type: 'symbol', source: 'pois', minzoom: 12, filter: ['!', ['in', ['get', 'cat'], ['literal', ['bus', 'parking']]]], layout: { 'icon-image': ['get', 'icon'], 'icon-allow-overlap': true, 'icon-ignore-placement': true } },
      { id: 'pois-minor', type: 'symbol', source: 'pois', minzoom: 15, filter: ['in', ['get', 'cat'], ['literal', ['bus', 'parking']]], layout: { 'icon-image': ['get', 'icon'], 'icon-allow-overlap': true, 'icon-ignore-placement': true } },
      { id: 'pl-loc', type: 'symbol', source: 'places', minzoom: 14, filter: ['==', ['get', 'k'], 'l'], layout: { 'text-field': ['get', 't'], 'text-font': ['NotoSansItalic'], 'text-size': 10.5 }, paint: L_({ 'text-color': '#a9b39f' }) },
      { id: 'pl-bay', type: 'symbol', source: 'places', minzoom: 13.2, filter: ['==', ['get', 'k'], 'b'], layout: { 'text-field': ['get', 't'], 'text-font': ['NotoSansItalic'], 'text-size': ['interpolate', ['linear'], ['zoom'], 13, 10, 16, 12.5], 'text-max-width': 7 }, paint: L_({ 'text-color': '#7fb6d6' }) },
      { id: 'pl-island', type: 'symbol', source: 'places', minzoom: 11, filter: ['==', ['get', 'k'], 'i'], layout: { 'text-field': ['get', 't'], 'text-font': ['NotoSansItalic'], 'text-size': ['interpolate', ['linear'], ['zoom'], 11, 10.5, 15, 13], 'text-letter-spacing': 0.06 }, paint: L_({ 'text-color': '#cfe3ef' }) },
      { id: 'pl-small', type: 'symbol', source: 'places', minzoom: 11.8, filter: ['==', ['get', 'k'], 's'], layout: { 'text-field': ['get', 't'], 'text-font': FONT, 'text-size': ['interpolate', ['linear'], ['zoom'], 12, 9.5, 15, 12], 'text-transform': 'uppercase', 'text-letter-spacing': 0.08 }, paint: L_({ 'text-color': '#c4cdd5' }) },
      { id: 'pl-village', type: 'symbol', source: 'places', minzoom: 9.8, filter: ['==', ['get', 'k'], 'v'], layout: { 'text-field': ['get', 't'], 'text-font': FONT, 'text-size': ['interpolate', ['linear'], ['zoom'], 10, 10, 15, 15], 'text-transform': 'uppercase', 'text-letter-spacing': 0.14 }, paint: L_({ 'text-color': '#e8edf1' }) }
    ]
  };
  function L_(p) { for (var k in HALO) p[k] = HALO[k]; return p; }
  (function () {
    var c = document.createElement('canvas');
    if (!(c.getContext('webgl2') || c.getContext('webgl'))) setTimeout(function () { toast('Dieses Handy unterstützt keine 3D-Grafik (WebGL). Die Karte kann nicht gezeichnet werden.'); }, 500);
  })();
  var map = new maplibregl.Map({
    container: 'map', style: style, center: [15.17, 44.075], zoom: 11.5, minZoom: 9, maxZoom: 19.5,
    maxBounds: bounds.pad(1.2).ml(), attributionControl: false, pitchWithRotate: false, dragRotate: true,
    touchPitch: false, maxPitch: 60, fadeDuration: 0, localIdeographFontFamily: false
  });
  map.addControl(new maplibregl.AttributionControl({ compact: false, customAttribution: 'Daten &copy; OpenStreetMap-Mitwirkende, Höhen: Copernicus DEM' }), 'bottom-right');
  map.addControl(new maplibregl.ScaleControl({ unit: 'metric', maxWidth: 90 }), 'bottom-left');
  // Symbole fuer Orte werden beim ersten Bedarf aus den SVG-Zeichnungen erzeugt
  map.on('styleimagemissing', function (e) {
    var cat = e.id.replace('poi-', ''); if (!CAT[cat] || map.hasImage(e.id)) return;
    var s = 56, cv = document.createElement('canvas'); cv.width = cv.height = s;
    var g = cv.getContext('2d');
    g.fillStyle = 'rgba(0,0,0,0.35)'; rr(g, 4, 6, 48, 48, 17); g.fill();
    g.fillStyle = CAT[cat].c; rr(g, 4, 3, 48, 48, 17); g.fill();
    g.strokeStyle = '#14181d'; g.lineWidth = 4; rr(g, 4, 3, 48, 48, 17); g.stroke();
    g.save(); g.translate(13, 12); g.scale(30 / 24, 30 / 24);
    g.strokeStyle = '#14181d'; g.lineWidth = 2.4; g.lineCap = 'round'; g.lineJoin = 'round';
    var src = IC[cat], m, re = /<(path|circle|rect)([^>]*)\/>/g;
    while ((m = re.exec(src))) {
      var at = {}; m[2].replace(/(\w+)="([^"]*)"/g, function (_, k, v) { at[k] = v; });
      if (m[1] === 'path') g.stroke(new Path2D(at.d));
      else if (m[1] === 'circle') { g.beginPath(); g.arc(+at.cx, +at.cy, +at.r, 0, 2 * Math.PI); g.stroke(); }
      else { rr(g, +at.x, +at.y, +at.width, +at.height, +(at.rx || 0)); g.stroke(); }
    }
    g.restore();
    map.addImage(e.id, g.getImageData(0, 0, s, s), { pixelRatio: 2 });
  });
  function rr(g, x, y, w, h, r) { g.beginPath(); g.moveTo(x + r, y); g.arcTo(x + w, y, x + w, y + h, r); g.arcTo(x + w, y + h, x, y + h, r); g.arcTo(x, y + h, x, y, r); g.arcTo(x, y, x + w, y, r); g.closePath(); }
  var mapReady = false, pendingSrc = {};
  function setSrc(id, data) {
    if (mapReady) map.getSource(id).setData(data); else pendingSrc[id] = data;
  }
  function setVis(id, on) { if (mapReady && map.getLayer(id)) map.setLayoutProperty(id, 'visibility', on ? 'visible' : 'none'); }
  map.on('load', function () {
    mapReady = true;
    Object.keys(pendingSrc).forEach(function (k) { map.getSource(k).setData(pendingSrc[k]); });
    pendingSrc = {};
    updLayers(); updPois();
  });
  function padding() {
    var p = { top: topH(), bottom: fs ? 20 : sheetH() + 10, left: 20, right: fs ? 20 : 64 };
    // Fahrtrichtung oben: eigener Punkt im unteren Drittel, damit man voraus mehr sieht
    if (nav && follow && camBearing() !== null) p.top += Math.max(0, (window.innerHeight - p.top - p.bottom) * 0.4);
    return p;
  }
  function view(lat, lon, z, dur) { map.easeTo({ center: [lon, lat], zoom: z == null ? map.getZoom() : z, padding: padding(), duration: dur == null ? 600 : dur }); }
  // MapLibre rechnet den bereits gesetzten Kartenrand beim Einpassen mit ein -> nur die Differenz uebergeben
  function fitTo(b, dur) {
    var p = padding(), c = map.getPadding(), d = {};
    ['top', 'bottom', 'left', 'right'].forEach(function (k) { d[k] = Math.max(0, p[k] - (c[k] || 0)); });
    var cam = map.cameraForBounds(b.ml(), { padding: d, bearing: map.getBearing(), maxZoom: 17 });
    if (!cam) { map.easeTo({ padding: p, duration: 0 }); cam = map.cameraForBounds(b.ml(), { bearing: map.getBearing(), maxZoom: 17 }); if (!cam) return; }
    map.easeTo({ center: cam.center, zoom: cam.zoom, bearing: cam.bearing, padding: p, duration: dur == null ? 700 : dur });
  }
  function fitIsland() { fitTo(bounds, 0); }

  function updLayers() {
    setVis('relief', S.relief);
    setVis('contours', S.contours); setVis('cont-lbl', S.contours && S.labels);
    setVis('routeglow', S.routes);
    ['pl-village', 'pl-small', 'pl-bay', 'pl-island', 'pl-loc', 'peaks'].forEach(function (id) { setVis(id, S.labels); });
  }

  // =====================================================================
  // Symbole und Orte von Interesse
  // =====================================================================
  var IC = {
    food: '<path d="M7 3v7a2 2 0 0 0 2 2M11 3v7a2 2 0 0 1-2 2v9M9 3v6M17 21V3c-2 1.5-3 4-3 8h3"/>',
    cafe: '<path d="M4 9h12v4a5 5 0 0 1-5 5H9a5 5 0 0 1-5-5zM16 10.5h1.5a2.5 2.5 0 0 1 0 5H16M8 3v3M12 3v3"/>',
    bar: '<path d="M5 4h14l-7 8zM12 12v8M8 20h8"/>',
    beach: '<path d="M3 12a9 9 0 0 1 18 0zM12 12v9M8 21h8"/>',
    water: '<path d="M12 3s6 7 6 11.5a6 6 0 0 1-12 0C6 10 12 3 12 3z"/>',
    wc: '<path d="M3 7l2 10 2.5-7 2.5 7 2-10M21 9a3 3 0 1 0 0 6"/>',
    view: '<path d="M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12z"/><circle cx="12" cy="12" r="3"/>',
    peak: '<path d="M3 20 10 7l4 6 2-3 5 10z"/>',
    sight: '<path d="M5 21V4h11l-2.5 4L16 12H5"/>',
    shop: '<path d="M5 8h14l-1 13H6zM9 8V6a3 3 0 0 1 6 0v2"/>',
    health: '<path d="M12 5v14M5 12h14"/>',
    bike: '<circle cx="6" cy="16" r="3.5"/><circle cx="18" cy="16" r="3.5"/><path d="M6 16l4-7h6l2 7M10 9l3 7 3-7"/>',
    ferry: '<path d="M3 14h18l-3 6H6zM6 14V9h12v5M12 9V4"/>',
    stay: '<path d="M3 19V7M3 14h18v5M21 14v-3a3 3 0 0 0-3-3h-7v6M7 11.5a1.5 1.5 0 1 0 0-.1"/>',
    money: '<rect x="3" y="6" width="18" height="12" rx="2"/><circle cx="12" cy="12" r="2.5"/>',
    fuel: '<path d="M5 21V5a2 2 0 0 1 2-2h6a2 2 0 0 1 2 2v16M4 21h12M5 10h10M15 8l3 3v7a1.5 1.5 0 0 0 3 0V9l-3-3"/>',
    port: '<path d="M12 4v16M8 8h8M5 13a7 7 0 0 0 14 0M12 4a1.5 1.5 0 1 0 0 .1"/>',
    bus: '<rect x="5" y="3" width="14" height="15" rx="2"/><path d="M5 11h14M8 21v-3M16 21v-3"/>',
    parking: '<path d="M8 20V4h5a4 4 0 0 1 0 8H8"/>',
    pin: '<circle cx="12" cy="10" r="3"/><path d="M12 21s-7-6.5-7-11a7 7 0 0 1 14 0c0 4.5-7 11-7 11z"/>',
    flag: '<path d="M6 21V4h12l-3 4 3 4H6"/>',
    road: '<path d="M8 3 5 21M16 3l3 18M12 4v3M12 11v3M12 18v2"/>',
    loop: '<path d="M4 12a8 8 0 1 0 3-6.2M4 4v4h4"/>',
    list: '<path d="M8 6h13M8 12h13M8 18h13M3.5 6h.01M3.5 12h.01M3.5 18h.01"/>',
    play: '<path d="M7 4v16l13-8z"/>',
    rec: '<circle cx="12" cy="12" r="7"/>',
    up: '<path d="M3 18 9 11l4 4 8-9M15 6h6v6"/>',
    wind: '<path d="M3 8h11a3 3 0 1 0-3-3M3 12h15a3 3 0 1 1-3 3M3 16h7"/>',
    sun: '<circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M2 12h2M20 12h2M5 5l1.5 1.5M17.5 17.5 19 19M5 19l1.5-1.5M17.5 6.5 19 5"/>',
    vol: '<path d="M4 9v6h4l5 4V5L8 9zM16.5 8.5a5 5 0 0 1 0 7M19 6a8.5 8.5 0 0 1 0 12"/>',
    mute: '<path d="M4 9v6h4l5 4V5L8 9zM17 9l5 6M22 9l-5 6"/>'
  };
  IC.snack = IC.food;
  var CAT = {
    food: { n: 'Restaurant', c: '#ff9f43' }, snack: { n: 'Imbiss', c: '#ff9f43' }, cafe: { n: 'Cafe', c: '#e8c07d' },
    bar: { n: 'Bar', c: '#c38bff' }, beach: { n: 'Strand', c: '#4fc3f7' }, water: { n: 'Trinkwasser', c: '#4fc3f7' },
    wc: { n: 'Toilette', c: '#9fb3c4' }, view: { n: 'Aussichtspunkt', c: '#c8f53c' }, peak: { n: 'Gipfel', c: '#e9d8b4' },
    sight: { n: 'Sehenswürdigkeit', c: '#f6e05e' }, shop: { n: 'Einkaufen', c: '#7ee0b0' }, health: { n: 'Gesundheit', c: '#ff7b7b' },
    bike: { n: 'Fahrradservice', c: '#c8f53c' }, ferry: { n: 'Fähre', c: '#4fc3f7' }, stay: { n: 'Unterkunft', c: '#b39ddb' },
    money: { n: 'Geld', c: '#7ee0b0' }, fuel: { n: 'Tankstelle', c: '#9fb3c4' }, port: { n: 'Hafen', c: '#4fc3f7' },
    bus: { n: 'Bus', c: '#9fb3c4' }, parking: { n: 'Parkplatz', c: '#6f8fb0' }
  };
  function svg(name, color, size, sw) {
    return '<svg width="' + (size || 16) + '" height="' + (size || 16) + '" viewBox="0 0 24 24" fill="none" stroke="' + color + '" stroke-width="' + (sw || 2.3) + '" stroke-linecap="round" stroke-linejoin="round">' + IC[name] + '</svg>';
  }
  // Namen für unbenannte Orte: "Strand bei Kali"
  function nearestPlace(lat, lon, villagesOnly) {
    var best = null, bd = 1e18;
    D.places.forEach(function (p) { if (villagesOnly && p[3] !== 'village' && p[3] !== 'town' && p[3] !== 'hamlet') return; var d = dist(lat, lon, p[1], p[2]); if (d < bd) { bd = d; best = p; } });
    return best;
  }
  D.pois.forEach(function (p) {
    p.label = p[3] || ((p[4].sub || CAT[p[0]].n) + ' bei ' + nearestPlace(p[1], p[2], true)[0]);
    p.village = nearestPlace(p[1], p[2], true)[0];
  });

  var FILTERS = [
    { id: 'food', n: 'Essen', cats: ['food', 'snack'] },
    { id: 'cafe', n: 'Cafe und Bar', cats: ['cafe', 'bar'] },
    { id: 'beach', n: 'Strände', cats: ['beach'] },
    { id: 'view', n: 'Aussicht', cats: ['view', 'sight'] },
    { id: 'water', n: 'Wasser und WC', cats: ['water', 'wc'] },
    { id: 'shop', n: 'Einkaufen', cats: ['shop', 'money', 'health'] },
    { id: 'bike', n: 'Rad', cats: ['bike'] },
    { id: 'stay', n: 'Unterkunft', cats: ['stay'] },
    { id: 'more', n: 'Fähre, Hafen, Bus', cats: ['ferry', 'port', 'bus', 'fuel', 'parking'] }
  ];
  var active = load('filters', { food: 1, beach: 1, view: 1, water: 1, bike: 1 });
  function drawChips() {
    $('chips').innerHTML = FILTERS.map(function (f) { return '<button class="chip' + (active[f.id] ? ' on' : '') + '" data-f="' + f.id + '">' + esc(f.n) + '</button>'; }).join('');
    updPois();
  }
  function updPois() {
    if (!mapReady) return;
    var cats = [];
    FILTERS.forEach(function (f) { if (active[f.id]) cats = cats.concat(f.cats); });
    map.setFilter('pois', ['all', ['in', ['get', 'cat'], ['literal', cats]], ['!', ['in', ['get', 'cat'], ['literal', ['bus', 'parking']]]]]);
    map.setFilter('pois-minor', ['all', ['in', ['get', 'cat'], ['literal', cats]], ['in', ['get', 'cat'], ['literal', ['bus', 'parking']]]]);
  }
  $('chips').addEventListener('click', function (e) {
    var b = e.target.closest('.chip'); if (!b) return;
    var f = b.getAttribute('data-f'); if (active[f]) delete active[f]; else active[f] = 1;
    store('filters', active); drawChips();
  });

  // =====================================================================
  // Standort
  // =====================================================================
  var me = null, meMarker = null, follow = false, lastSpeed = -1, lastFix = 0, meShown = null, meAnim = null;
  // Folgen: Zoomen und Drehen bleiben dann genau auf der eigenen Position
  function setFollow(on) {
    if (follow === on) return;
    follow = on; $('btnLoc').classList.toggle('on', on);
    map.touchZoomRotate.disable(); map.scrollZoom.disable();
    if (on) { map.touchZoomRotate.enable({ around: 'center' }); map.scrollZoom.enable({ around: 'center' }); }
    else { map.touchZoomRotate.enable(); map.scrollZoom.enable(); }
  }
  function accPoly(lat, lon, r) {
    var c = [], kx = 111320 * Math.cos(lat * RAD), ky = 110540;
    for (var k = 0; k <= 48; k++) { var a = k / 48 * 2 * Math.PI; c.push([lat + Math.sin(a) * r / ky, lon + Math.cos(a) * r / kx]); }
    return fc([polyF(c)]);
  }
  function camBearing() { return nav && !nav.free && S.headUp && me && me.brg >= 0 && lastSpeed > 4 ? me.brg : null; }
  // Punkt gleitet zwischen zwei GPS-Messungen (wie bei Google Maps), Karte folgt synchron
  function moveMe(lat, lon) {
    if (!meShown) meShown = { lat: lat, lon: lon };
    var a = { lat: meShown.lat, lon: meShown.lon }, t0 = performance.now(), dur = dist(a.lat, a.lon, lat, lon) > 150 ? 0 : 900;
    if (meAnim) cancelAnimationFrame(meAnim);
    (function step(now) {
      var f = dur ? Math.min(1, (now - t0) / dur) : 1;
      meShown = { lat: a.lat + (lat - a.lat) * f, lon: a.lon + (lon - a.lon) * f };
      meMarker.setLngLat([meShown.lon, meShown.lat]);
      if (f < 1) meAnim = requestAnimationFrame(step); else meAnim = null;
    })(t0);
    if (follow) {
      var o = { center: [lon, lat], padding: padding(), duration: dur || 300, easing: function (t) { return t; } };
      var b = camBearing(); if (b !== null) { o.bearing = b; o.pitch = 45; }
      map.easeTo(o);
    }
  }
  var PREKO = (function () {
    var f = D.pois.filter(function (p) { return p[0] === 'ferry' && /Preko/i.test(p[3]); })[0] || D.pois.filter(function (p) { return p[0] === 'ferry'; })[0];
    return f ? { lat: f[1], lon: f[2], name: 'Fähre Preko' } : { lat: 44.0764, lon: 15.1944, name: 'Preko' };
  })();
  function onIsland(lat, lon) { return bounds.pad(0.03).contains([lat, lon]); }
  function myRef() { return me && onIsland(me.lat, me.lon) && Date.now() - lastFix < 120000 ? me : null; }
  function startRef() { var r = myRef(); return r ? { lat: r.lat, lon: r.lon, name: 'Mein Standort' } : PREKO; }

  // Tempo zuverlaessig bestimmen: GPS-Messwert bevorzugen, sonst nur aus genauen Punkten rechnen,
  // Stillstand erkennen, unmoegliche Werte (Ortungsspruenge) verwerfen
  var posHist = [], lastGoodSpeedT = 0;
  function cleanSpeed(lat, lon, acc, speed, sacc, isGps, now) {
    var v = -1;
    if (!isGps || acc > 30) return -1;
    // Punkte, die unmoeglich schnell vom letzten entfernt sind, sind Ortungsspruenge
    var prev = posHist[posHist.length - 1];
    if (prev && now - prev.t > 0 && dist(prev.lat, prev.lon, lat, lon) / ((now - prev.t) / 1000) > 22 && now - prev.t < 10000) return -1;
    if (prev && now - prev.t < 500) return -1; // doppelte Meldung
    if (speed >= 0 && !(sacc > 2.5)) v = speed;
    else {
      // ohne GPS-Messwert: Tempo ueber ca. 3 s Fenster, Bewegung muss groesser als die Ungenauigkeit sein
      var old = null;
      for (var i = 0; i < posHist.length; i++) if (now - posHist[i].t >= 2500) old = posHist[i];
      if (old) {
        var D = dist(old.lat, old.lon, lat, lon), T = (now - old.t) / 1000;
        v = D > Math.max(4, (acc + old.acc) / 2) ? D / T : 0;
      }
    }
    posHist.push({ t: now, lat: lat, lon: lon, acc: acc });
    while (posHist.length && now - posHist[0].t > 8000) posHist.shift();
    // Stillstand: seit 4 s nicht weiter bewegt als die Messungenauigkeit
    var first = posHist[0];
    if (posHist.length >= 3 && now - first.t >= 4000) {
      var far = 0;
      posHist.forEach(function (q) { if (now - q.t <= 4500) far = Math.max(far, dist(q.lat, q.lon, lat, lon)); });
      if (far < Math.max(6, acc * 0.8)) v = 0;
    }
    if (v > 22) v = -1; // ueber 80 km/h ist auf dem Rad unmoeglich
    return v;
  }
  window.onGps = function (lat, lon, acc, speed, brg, sacc, isGps) {
    var now = Date.now();
    if (isGps === undefined) isGps = acc <= 100 ? 1 : 0;
    if (sacc === undefined) sacc = -1;
    // Ein grober Punkt kurz nach einem guten GPS-Punkt wird ignoriert (sonst springt der Punkt)
    if (!isGps && me && me.gps && now - lastFix < 15000) return;
    speed = cleanSpeed(lat, lon, acc, speed, sacc, isGps, now);
    if (me && (brg < 0 || speed < 1.5) && dist(me.lat, me.lon, lat, lon) > Math.max(4, acc)) brg = bearing(me.lat, me.lon, lat, lon);
    me = { lat: lat, lon: lon, acc: acc, gps: !!isGps, brg: brg >= 0 ? brg : (me ? me.brg : -1) };
    lastFix = now;
    if (speed >= 0) { lastSpeed = speed * 3.6; lastGoodSpeedT = now; } else if (now - lastGoodSpeedT > 5000) lastSpeed = -1;
    if (!meMarker) {
      var el = document.createElement('div');
      el.className = 'me';
      el.innerHTML = '<svg id="meArrow" width="34" height="34" viewBox="0 0 34 34" style="display:none"><path d="M17 0 24 10H10z" fill="#c8f53c" stroke="#14181d" stroke-width="2"/></svg><div class="dot"></div>';
      meMarker = new maplibregl.Marker({ element: el, rotationAlignment: 'map', pitchAlignment: 'viewport' }).setLngLat([lon, lat]).addTo(map);
    }
    setSrc('acc', accPoly(lat, lon, Math.min(acc, 300)));
    sessPoint(lat, lon, acc, speed);
    updArrow();
    moveMe(lat, lon);
    updSpeedo();
    if (rec) recPoint(lat, lon, acc, speed);
    if (nav) navUpdate();
    else if (state === 'home' && !homeDrawnWithGps && myRef()) drawHome();
    checkGps();
  };
  if (!A && navigator.geolocation) {
    navigator.geolocation.watchPosition(function (p) { onGps(p.coords.latitude, p.coords.longitude, p.coords.accuracy, p.coords.speed == null ? -1 : p.coords.speed, p.coords.heading == null ? -1 : p.coords.heading); }, function () {}, { enableHighAccuracy: true });
  }
  function checkGps() {
    var off = false, txt = '';
    try {
      if (A && !A.hasPermission()) { off = true; txt = 'Standort-Erlaubnis fehlt.'; }
      else if (A && !A.gpsEnabled()) { off = true; txt = 'GPS ist ausgeschaltet.'; }
    } catch (e) {}
    $('gpswarn').style.display = off && !nav ? 'flex' : 'none';
    $('gpswarnText').textContent = txt;
  }
  $('gpswarnBtn').onclick = function () { if (A) A.openLocationSettings(); };
  setInterval(checkGps, 5000); setTimeout(checkGps, 1500);
  $('btnLoc').onclick = function () {
    if (!me) { toast('Noch kein GPS-Signal. Kurz warten oder GPS einschalten.'); return; }
    if (!onIsland(me.lat, me.lon)) { toast('Du bist gerade nicht auf Ugljan.'); map.easeTo({ center: [me.lon, me.lat], zoom: 12 }); return; }
    setFollow(true); lastTouch = Date.now();
    var o = { center: [me.lon, me.lat], zoom: Math.max(map.getZoom(), 16), padding: padding(), duration: 700 };
    var b = camBearing(); if (b !== null) { o.bearing = b; o.pitch = 45; }
    map.easeTo(o);
  };
  // Verschieben mit dem Finger beendet das Folgen; Zoomen und Drehen nicht
  map.on('dragstart', function (e) { if (e.originalEvent) setFollow(false); });

  // ---------- Drehen, Zoomen, Kompass, automatisch zurueck zur Position ----------
  var lastTouch = Date.now();
  ['touchstart', 'touchmove', 'mousedown', 'wheel'].forEach(function (ev) { $('map').addEventListener(ev, function () { lastTouch = Date.now(); }, { passive: true }); });
  function updArrow() {
    var el = document.getElementById('meArrow'); if (!el || !me) return;
    var show = me.brg >= 0 && lastSpeed > 3;
    el.style.display = show ? 'block' : 'none';
    if (show) meMarker.setRotation(me.brg);
  }
  function updCompass() {
    var b = -map.getBearing();
    $('compassNeedle').style.transform = 'rotate(' + b + 'deg)';
    $('btnCompass').classList.toggle('hide', Math.abs(((b + 540) % 360) - 180) < 0.5 && !(nav && !nav.free));
    $('btnCompass').classList.toggle('on', !!(nav && S.headUp));
  }
  function smoothBearing(target, ms) {
    if (Math.abs(((target - map.getBearing() + 540) % 360) - 180) < 0.5 && map.getPitch() < 1) return;
    map.easeTo({ bearing: target, pitch: 0, duration: ms || 500, padding: padding() });
  }
  map.on('rotate', updCompass);
  map.on('pitch', updCompass);
  map.on('movestart', function (e) { if (e.originalEvent) lastTouch = Date.now(); });
  $('btnZoomIn').onclick = function () { lastTouch = Date.now(); map.zoomIn(1); };
  $('btnZoomOut').onclick = function () { lastTouch = Date.now(); map.zoomOut(1); };
  $('btnCompass').onclick = function () {
    lastTouch = Date.now();
    if (nav && !nav.free) {
      S.headUp = !S.headUp; saveS();
      toast(S.headUp ? 'Karte dreht sich mit der Fahrtrichtung' : 'Norden ist oben');
      if (!S.headUp) smoothBearing(0); else if (me && me.brg >= 0) map.easeTo({ bearing: me.brg, pitch: 45, duration: 600, padding: padding() });
      updCompass();
    } else smoothBearing(0);
  };
  // Nach einer Weile ohne Beruehrung zurueck zur eigenen Position (und Norden oben bzw. Fahrtrichtung)
  setInterval(function () {
    var idle = Date.now() - lastTouch, ref = myRef();
    if (map.isMoving()) return;
    var bTarget = camBearing() !== null ? camBearing() : 0, off = Math.abs(((map.getBearing() - bTarget + 540) % 360) - 180) > 0.5 || (bTarget === 0 && map.getPitch() > 1);
    if (nav && idle > 8000) {
      if (ref && !follow) { setFollow(true); var o = { center: [ref.lon, ref.lat], zoom: Math.max(map.getZoom(), 16), padding: padding(), duration: 900 }; if (camBearing() !== null) { o.bearing = bTarget; o.pitch = 45; } else { o.bearing = 0; o.pitch = 0; } map.easeTo(o); }
      else if (off) map.easeTo({ bearing: bTarget, pitch: bTarget ? 45 : 0, duration: 800, padding: padding() });
    } else if (!nav && idle > 20000) {
      if (ref && !follow && (state === 'home' || state === 'near')) { setFollow(true); map.easeTo({ center: [ref.lon, ref.lat], zoom: Math.max(map.getZoom(), 15), bearing: 0, pitch: 0, padding: padding(), duration: 900 }); }
      else if (off) smoothBearing(0, 800);
    }
  }, 1000);

  // ---------- Tempo und Durchschnitt (auch ohne Navigation) ----------
  var sess = { dist: 0, mv: 0, last: null, lastT: 0 }, speedHist = [];
  function sessPoint(lat, lon, acc, speed) {
    var now = Date.now();
    if (speed >= 0) { speedHist.push({ v: speed * 3.6, t: now }); if (speedHist.length > 3) speedHist.shift(); }
    if (acc > 35 || !onIsland(lat, lon)) return;
    if (sess.last) {
      var d = dist(sess.last[0], sess.last[1], lat, lon), dt = (now - sess.lastT) / 1000;
      if (speed < 0.8) { if (speed >= 0) { sess.last = [lat, lon]; sess.lastT = now; } return; }
      if (d < Math.max(4, acc * 0.5)) return;
      if (dt > 0 && dt < 15 && d / dt < 22) { sess.dist += d; sess.mv += dt; }
    }
    sess.last = [lat, lon]; sess.lastT = now;
  }
  function curSpeed() {
    var now = Date.now();
    if (!myRef() || now - lastFix > 6000) return null;
    var h = speedHist.filter(function (x) { return now - x.t < 6000; }).map(function (x) { return x.v; }).sort(function (a, b) { return a - b; });
    if (!h.length) return null;
    var v = h[Math.floor(h.length / 2)]; // Median: einzelne Ausreisser schlagen nicht durch
    return v < 2.5 ? 0 : v;
  }
  function avgSpeed() { var o = rec || sess; return o.mv > 20 ? o.dist / o.mv * 3.6 : null; }
  function updSpeedo() {
    var v = curSpeed(), a = avgSpeed(), show = !!nav || fs || (v !== null && (v > 0 || sess.mv > 20));
    $('speedo').style.display = show ? 'flex' : 'none';
    $('spd').textContent = v === null ? '-' : Math.round(v);
    $('avg').textContent = a === null ? '-' : a.toFixed(1).replace('.', ',');
  }
  setInterval(updSpeedo, 2000);
  $('btnIsland').onclick = function () { setFollow(false); map.easeTo({ bearing: 0, pitch: 0, duration: 0 }); fitTo(bounds); };
  $('btnLayers').onclick = function () { if (!nav) showMenu('map'); };

  // ---------- Vollbild: nur Karte, Tempo unten links ----------
  var IC_FULL = '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.3" stroke-linecap="round" stroke-linejoin="round"><path d="M4 9V4h5M15 4h5v5M20 15v5h-5M9 20H4v-5"/></svg>';
  var IC_EXIT = '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.3" stroke-linecap="round" stroke-linejoin="round"><path d="M9 4v5H4M20 9h-5V4M15 20v-5h5M4 15h5v5"/></svg>';
  function setFs(on) {
    fs = on; document.body.classList.toggle('fs', on);
    if (A) try { A.setFullscreen(on); } catch (e) {}
    $('btnFull').innerHTML = on ? IC_EXIT : IC_FULL; $('btnFull').classList.toggle('on', on);
    $('results').style.display = 'none';
    placeSide(); updSpeedo();
    setTimeout(function () { map.resize(); placeSide(); if (follow && me) moveMe(me.lat, me.lon); }, 120);
  }
  $('btnFull').innerHTML = IC_FULL;
  $('btnFull').onclick = function () { setFs(!fs); };

  var toastT = null;
  function toast(t) {
    var d = document.createElement('div'); d.className = 'toast'; d.textContent = t; document.body.appendChild(d);
    setTimeout(function () { d.remove(); }, 3200);
  }
  function say(t) { if (S.voice && A) try { A.speak(t); } catch (e) {} }
  function buzz(ms) { if (A) try { A.vibrate(ms); } catch (e) {} }

  // =====================================================================
  // Unteres Blatt
  // =====================================================================
  var state = 'home', homeDrawnWithGps = false;
  var sheetEl = $('sheet');
  function sheetH() { return fs ? 0 : (sheetEl.offsetHeight || 200); }
  function topH() { var nv = $('nav'); return nv.style.display === 'flex' ? nv.offsetHeight + 30 : fs ? 20 : 110; }
  function sheet(html, keepScroll) {
    var st = sheetEl.scrollTop;
    if (!keepScroll) sheetEl.classList.remove('mini');
    sheetEl.innerHTML = '<div class="grab" id="grab"><div></div></div>' + html;
    sheetEl.scrollTop = keepScroll ? st : 0;
    placeSide();
  }
  function placeSide() {
    var h = sheetH();
    $('side').style.bottom = (h + 22) + 'px';
    $('speedo').style.bottom = (h + 34) + 'px';
    var lb = document.querySelector('.maplibregl-ctrl-bottom-left'), rb = document.querySelector('.maplibregl-ctrl-bottom-right');
    if (lb) lb.style.bottom = (fs ? 0 : h) + 'px';
    if (rb) rb.style.bottom = (fs ? 0 : h) + 'px';
    if (fs) { $('side').style.bottom = '20px'; $('speedo').style.bottom = '28px'; }
  }
  if (window.ResizeObserver) new ResizeObserver(placeSide).observe(sheetEl);
  sheetEl.addEventListener('click', function (e) {
    if (e.target.closest('#grab')) { sheetEl.classList.toggle('mini'); sheetEl.scrollTop = 0; placeSide(); }
  });

  function nearby(lat, lon, cats, n, maxD) {
    var out = [];
    D.pois.forEach(function (p, i) { if (cats && cats.indexOf(p[0]) < 0) return; var d = dist(lat, lon, p[1], p[2]); if (!maxD || d <= maxD) out.push({ i: i, d: d }); });
    out.sort(function (a, b) { return a.d - b.d; });
    return out.slice(0, n);
  }
  function poiRow(x) {
    var p = D.pois[x.i], c = CAT[p[0]], o = openNow(p[4].oh);
    var sub = (p[4].sub || c.n) + (p[4].cu ? ' &middot; ' + esc(p[4].cu) : '') + (p[3] ? ' &middot; ' + esc(p.village) : '');
    return '<div class="row" data-poi="' + x.i + '"><div class="ico">' + svg(p[0], c.c, 18) + '</div><div class="grow"><div class="t ell">' + esc(p.label) + '</div><div class="s ell">' + sub + '</div></div><div class="r"><div class="s">' + fmtDist(x.d) + '</div>' + (o === true ? '<div class="s okl">offen</div>' : o === false ? '<div class="s bad">zu</div>' : '') + '</div></div>';
  }

  // ---------- Startseite ----------
  function drawHome() {
    state = 'home';
    var ref = myRef();
    homeDrawnWithGps = !!ref;
    var base = ref || PREKO;
    var html = '<h2>Ugljan mit dem Rad</h2><div class="sub">' +
      (ref ? 'GPS aktiv &middot; ' + esc(nearestPlace(ref.lat, ref.lon, true)[0]) + ' &middot; ' + Math.round(demEle(ref.lat, ref.lon)) + ' m über dem Meer' :
        me ? 'Du bist nicht auf der Insel. Routen starten an der Fähre Preko.' : 'Warte auf GPS. Tippe auf die Karte, um ein Ziel zu setzen.') + '</div>' +
      wxHtml() +
      '<div class="quick">' +
      '<button data-act="tours">' + svg('bike', '#c8f53c', 24, 2) + 'Touren</button>' +
      '<button data-act="loop">' + svg('loop', '#c8f53c', 24, 2) + 'Rundtour</button>' +
      '<button data-act="near">' + svg('list', '#c8f53c', 24, 2) + 'In der Nähe</button>' +
      '<button data-act="free">' + svg('rec', '#ff5a4e', 24, 2.4) + 'Fahrt starten</button></div>' +
      '<h3>Essen in der Nähe' + (ref ? '' : ' von Preko') + '</h3><div class="list">' +
      nearby(base.lat, base.lon, ['food', 'snack', 'cafe'], 4).map(poiRow).join('') + '</div>' +
      '<h3>Kartenlegende</h3>' + legendHtml() +
      '<div class="sub" style="margin-top:12px;font-size:12px">Rad: ' + BIKES[S.bike].n + ' &middot; ' + FIT[S.fit].n + ' &middot; Stil ' + STYLES[S.style].n + ' (im Menü änderbar)</div>';
    sheet(html);
  }
  function legendHtml() {
    var items = [[0, 'Radweg'], [-1, 'Radroute (markiert)'], [1, 'Ruhige Straße'], [5, 'Landstraße'], [2, 'Feldweg'], [8, 'Grober Weg'], [3, 'Pfad'], [6, 'Hauptstraße']];
    return '<div class="legend">' + items.map(function (x) { var col = x[0] < 0 ? '#ffb020' : CLASS[x[0]].c; return '<span><i style="background:' + col + '"></i>' + x[1] + '</span>'; }).join('') + '</div>';
  }
  sheetEl.addEventListener('click', function (e) {
    var h = e.target.closest('[data-href]');
    if (h) { location.href = h.getAttribute('data-href'); return; }
    var a = e.target.closest('[data-act]');
    if (a) {
      var act = a.getAttribute('data-act');
      if (act === 'tours') showTours('official');
      else if (act === 'loop') showTours('loop');
      else if (act === 'near') showNearby('food');
      else if (act === 'free') startFree();
      return;
    }
    var r = e.target.closest('[data-poi]');
    if (r && !nav) { var i = +r.getAttribute('data-poi'); setFollow(false); showPoi(i); view(D.pois[i][1], D.pois[i][2], Math.max(map.getZoom(), 16)); }
  });

  // ---------- Öffnungszeiten (einfache Formate) ----------
  var DAYS = ['su', 'mo', 'tu', 'we', 'th', 'fr', 'sa'];
  function openNow(oh) {
    if (!oh) return null;
    try {
      var s = oh.trim().toLowerCase();
      if (s === '24/7') return true;
      var now = new Date(), dow = now.getDay(), mins = now.getHours() * 60 + now.getMinutes(), result = null;
      var rules = s.split(';');
      for (var k = 0; k < rules.length; k++) {
        var r = rules[k].trim(); if (!r) continue;
        var m = r.match(/^((?:[a-z]{2}(?:-[a-z]{2})?,?\s*)+)?\s*(.*)$/);
        var dayPart = m[1], timePart = m[2].trim();
        if (/[a-z]{3,}/.test(dayPart || '') || /(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec|ph|sh)/.test(r)) return null;
        var applies = true;
        if (dayPart) {
          applies = false;
          dayPart.split(',').forEach(function (d) {
            d = d.trim(); if (!d) return;
            var ab = d.split('-'), a = DAYS.indexOf(ab[0]), b = DAYS.indexOf(ab[1] || ab[0]);
            if (a < 0 || b < 0) throw 0;
            for (var x = a, guard = 0; guard < 8; x = (x + 1) % 7, guard++) { if (x === dow) applies = true; if (x === b) break; }
          });
        }
        if (!applies) continue;
        if (timePart === 'off' || timePart === 'closed') { result = false; continue; }
        var open = false;
        timePart.split(',').forEach(function (t) {
          var mm = t.trim().match(/^(\d{1,2}):(\d\d)\s*-\s*(\d{1,2}):(\d\d)$/); if (!mm) throw 0;
          var a = +mm[1] * 60 + +mm[2], b = +mm[3] * 60 + +mm[4];
          if (b <= a) b += 1440;
          if ((mins >= a && mins < b) || (mins + 1440 >= a && mins + 1440 < b)) open = true;
        });
        result = open;
      }
      return result;
    } catch (e) { return null; }
  }

  // ---------- Ziel-Markierungen ----------
  var destMarker = null, viaMarkers = [];
  function setDestMarker(lat, lon) {
    if (destMarker) destMarker.remove();
    var el = document.createElement('div'); el.innerHTML = '<div class="pin" style="background:#c8f53c">' + svg('flag', '#14181d', 15) + '</div>';
    destMarker = new maplibregl.Marker({ element: el, anchor: 'bottom', offset: [9, -2] }).setLngLat([lon, lat]).addTo(map);
  }
  function clearMarkers() {
    if (destMarker) destMarker.remove(); destMarker = null;
    viaMarkers.forEach(function (m) { m.remove(); }); viaMarkers = [];
  }

  // ---------- Detail: Ort von Interesse ----------
  var dest = null;
  function quickRouteInfo(lat, lon) {
    var s = startRef(), a = nearestNode(s.lat, s.lon), b = nearestNode(lat, lon);
    var ed = astar(a, b); if (!ed) return '';
    var r = analyze(a, ed);
    return '<div class="stats"><div class="stat"><b>' + fmtDist(r.len) + '</b><span>' + (s === PREKO ? 'ab Fähre' : 'Strecke') + '</span></div><div class="stat"><b>' + fmtTime(r.secs) + '</b><span>Fahrzeit</span></div><div class="stat"><b>' + Math.round(r.asc) + ' m</b><span>Bergauf</span></div></div>';
  }
  function showPoi(i) {
    var p = D.pois[i], c = CAT[p[0]], e = p[4];
    state = 'poi'; clearRoute(true);
    dest = { lat: p[1], lon: p[2], name: p.label };
    setDestMarker(p[1], p[2]);
    var o = openNow(e.oh), info = '';
    if (e.cu) info += '<span class="tag">' + esc(e.cu) + '</span>';
    if (e.sub && e.sub !== c.n) info += '<span class="tag">' + esc(e.sub) + '</span>';
    if (o === true) info += '<span class="tag lime">Jetzt geöffnet</span>';
    if (o === false) info += '<span class="tag red">Jetzt geschlossen</span>';
    if (e.out === 'yes') info += '<span class="tag">Sitzplätze draussen</span>';
    if (e.fee === 'no') info += '<span class="tag">kostenlos</span>';
    var kv = '';
    if (e.oh) kv += '<div class="kv"><span>Öffnungszeiten</span><div style="text-align:right;max-width:65%">' + esc(e.oh.replace(/;\s*/g, '; ')) + '</div></div>';
    if (e.st) kv += '<div class="kv"><span>Adresse</span><div>' + esc(e.st + (e.hn ? ' ' + e.hn : '')) + '</div></div>';
    if (e.ele) kv += '<div class="kv"><span>Höhe</span><div>' + esc(e.ele) + ' m</div></div>';
    if (e.desc) kv += '<div class="sub" style="margin:8px 0">' + esc(e.desc) + '</div>';
    var btn = '<div class="btns"><button class="go" id="goRoute">Hierhin fahren</button></div>';
    var extra = '';
    if (e.ph) extra += '<button class="go sec" data-href="tel:' + esc(e.ph.split(/[;,]/)[0].replace(/[^\d+]/g, '')) + '">Anrufen</button>';
    if (e.web) extra += '<button class="go sec" data-href="' + esc(/^https?:\/\//.test(e.web) ? e.web : 'https://' + e.web) + '">Website</button>';
    sheet('<div style="display:flex;gap:12px;align-items:center"><div class="ico" style="width:46px;height:46px">' + svg(p[0], c.c, 24) + '</div><div class="grow"><h2>' + esc(p.label) + '</h2><div class="sub">' + esc(e.sub || c.n) + ' &middot; ' + esc(p.village) + '</div></div></div>' +
      '<div style="margin-top:10px">' + info + '</div>' + quickRouteInfo(p[1], p[2]) + kv + btn + (extra ? '<div class="btns">' + extra + '</div>' : ''));
    $('goRoute').onclick = function () { planRoute([startRef(), dest], dest.name); };
  }
  function placeKindName(name) {
    var p = D.places.filter(function (x) { return x[0] === name; })[0];
    return p ? ({ village: 'Dorf auf Ugljan', town: 'Ort auf Ugljan', hamlet: 'Weiler auf Ugljan', bay: 'Bucht', cape: 'Kap', peninsula: 'Halbinsel', locality: 'Flurname', island: 'Insel', islet: 'Inselchen', isolated_dwelling: 'Einzelgehöft' }[p[3]] || 'Ortsteil') : null;
  }
  function showPoint(lat, lon, name) {
    state = 'point'; clearRoute(true);
    var n = nearestNode(lat, lon), nm = '';
    for (var e = OFF[n]; e < OFF[n + 1]; e++) { nm = D.names[W[eWay[e]][1]]; if (nm) break; }
    var place = nearestPlace(lat, lon, false), z = Math.round(demEle(lat, lon));
    dest = { lat: lat, lon: lon, name: name || nm || ('Bei ' + place[0]) };
    setDestMarker(lat, lon);
    var near = nearby(lat, lon, null, 3, 500);
    sheet('<h2>' + esc(dest.name) + '</h2><div class="sub">' + (name ? (placeKindName(name) || 'Ort auf Ugljan') : 'Punkt bei ' + esc(place[0])) + ' &middot; ' + z + ' m über dem Meer' + (dist(lat, lon, LAT[n], LON[n]) > 80 ? ' &middot; ' + fmtDist(dist(lat, lon, LAT[n], LON[n])) + ' abseits vom Weg' : '') + '</div>' +
      quickRouteInfo(lat, lon) +
      '<div class="btns"><button class="go" id="goRoute">Hierhin fahren</button></div>' +
      (near.length ? '<h3>In der Nähe</h3><div class="list">' + near.map(poiRow).join('') + '</div>' : ''));
    $('goRoute').onclick = function () { planRoute([startRef(), dest], dest.name); };
  }
  map.on('click', function (e) {
    if (nav || fs) return;
    $('results').style.display = 'none';
    var ll = { lat: e.lngLat.lat, lng: e.lngLat.lng };
    if (state === 'choose') {
      var ch = map.queryRenderedFeatures([[e.point.x - 10, e.point.y - 10], [e.point.x + 10, e.point.y + 10]], { layers: ['cand'] });
      var pk = ch.length && sheetEl.querySelector('[data-pick="' + ch[0].properties.k + '"]');
      if (pk) pk.click();
      return;
    }
    if (state !== 'pickVia') {
      var hit = map.queryRenderedFeatures([[e.point.x - 12, e.point.y - 12], [e.point.x + 12, e.point.y + 12]], { layers: ['pois', 'pois-minor'] });
      if (hit.length) { showPoi(hit[0].properties.i); return; }
      // Rueckfall: Symbol evtl. noch nicht platziert -> naechsten sichtbaren Ort selbst suchen
      var z = map.getZoom(), cats = {}, best = -1, bd = 22 * 22;
      FILTERS.forEach(function (f) { if (active[f.id]) f.cats.forEach(function (c) { cats[c] = 1; }); });
      if (z >= 12) D.pois.forEach(function (p, i) {
        if (!cats[p[0]] || p[0] === 'peak' || ((p[0] === 'bus' || p[0] === 'parking') && z < 15)) return;
        var q = map.project([p[2], p[1]]), dx = q.x - e.point.x, dy = q.y - e.point.y;
        if (dx * dx + dy * dy < bd) { bd = dx * dx + dy * dy; best = i; }
      });
      if (best >= 0) { showPoi(best); return; }
      // Ortsname angetippt -> Ort oeffnen
      var lab = map.queryRenderedFeatures([[e.point.x - 14, e.point.y - 10], [e.point.x + 14, e.point.y + 10]], { layers: ['pl-village', 'pl-small', 'pl-island', 'pl-bay', 'pl-loc'] });
      if (lab.length) { var g = lab[0].geometry.coordinates; showPoint(g[1], g[0], lab[0].properties.t); return; }
    }
    if (!bounds.pad(0.02).contains(ll)) return;
    if (state === 'pickVia' && plan) {
      var pts = plan.pts.slice(); pts.splice(pts.length - 1, 0, { lat: ll.lat, lon: ll.lng, name: 'Zwischenziel' });
      planRoute(pts, plan.title, L2(plan.opt, { picked: true })); return;
    }
    showPoint(ll.lat, ll.lng);
  });

  // ---------- Route planen und anzeigen ----------
  var plan = null, cur = null, routeLayer = null, hoverMarker = null;
  // Vor jeder Route: schnell & leicht oder am schoensten? Beide werden gerechnet und verglichen.
  function planRoute(pts, title, opt) {
    opt = opt || {};
    if (!opt.picked) { chooseRoute(pts, title, opt); return; }
    setSrc('cand', fc());
    var r = routeVia(pts, opt);
    if (!r) { toast('Keine Route gefunden.'); return; }
    plan = { pts: pts, title: title, opt: opt, desc: opt.desc || '' };
    cur = r; state = 'route';
    drawRoute(r);
    clearMarkers();
    var last = pts[pts.length - 1];
    if (!opt.loop) setDestMarker(last.lat, last.lon);
    pts.slice(1, opt.loop ? pts.length - 1 : -1).forEach(function (p, k) {
      var el = document.createElement('div'); el.className = 'wp'; el.textContent = k + 1;
      viaMarkers.push(new maplibregl.Marker({ element: el }).setLngLat([p.lon, p.lat]).addTo(map));
    });
    drawRouteSheet();
    setFollow(false); fitTo(boundsOf(r.nodes.map(function (n) { return [LAT[n], LON[n]]; })));
  }
  var CAND_COL = { fast: '#4fc3f7', scenic: '#c8f53c' };
  function chooseRoute(pts, title, opt) {
    var res = {}, ok = false;
    Object.keys(STYLES).forEach(function (k) { res[k] = routeVia(pts, L2(opt, { style: k })); if (res[k]) ok = true; });
    if (!ok) { toast('Keine Route gefunden.'); return; }
    state = 'choose'; cur = null;
    if (routeLayer) { setSrc('route', fc()); routeLayer = null; }
    plan = { pts: pts, title: title, opt: opt, desc: opt.desc || '' };
    clearMarkers();
    var last = pts[pts.length - 1];
    if (!opt.loop) setDestMarker(last.lat, last.lon);
    var feats = [], allPts = [];
    ['fast', 'scenic'].forEach(function (k) {
      var r = res[k]; if (!r) return;
      var ll = r.nodes.map(function (n) { return [LAT[n], LON[n]]; });
      feats.push(lineF(ll, { col: CAND_COL[k], k: k })); allPts = allPts.concat(ll);
    });
    setSrc('cand', fc(feats));
    var f = res.fast, sc = res.scenic, same = f && sc && f.edges.length === sc.edges.length && f.edges.every(function (e, i) { return e === sc.edges[i]; });
    function card(k) {
      var r = res[k]; if (!r) return '';
      var other = res[k === 'fast' ? 'scenic' : 'fast'], hint = '';
      if (other && !same) {
        var parts = [];
        if (k === 'fast') {
          var dt = Math.round((other.secs - r.secs) / 60), da = Math.round(other.asc - r.asc);
          if (dt >= 1) parts.push(dt + ' min schneller');
          if (da >= 5) parts.push(da + ' m weniger bergauf');
          if (other.len - r.len >= 200) parts.push(fmtDist(other.len - r.len) + ' kürzer');
          hint = parts.length ? parts.join(', ') : 'Kaum schneller, aber direkter';
        } else {
          var dc = r.coast - other.coast;
          if (dc >= 100) parts.push(fmtDist(dc) + ' mehr direkt am Meer');
          if (r.beauty > other.beauty) parts.push('Schönheit ' + r.beauty + ' statt ' + other.beauty + ' von 10');
          if ((other.mixC[6] || 0) - (r.mixC[6] || 0) >= 200) parts.push(fmtDist((other.mixC[6] || 0) - (r.mixC[6] || 0)) + ' weniger Hauptstraße');
          hint = parts.length ? parts.join(', ') : 'Ähnlich schön, über andere Wege';
        }
      }
      return '<div class="card pick" data-pick="' + k + '" style="border-color:' + (S.style === k ? CAND_COL[k] : 'transparent') + '"><div class="top"><i style="width:14px;height:14px;border-radius:7px;background:' + CAND_COL[k] + ';flex:none;margin-top:4px"></i><div class="grow"><div class="nm">' + STYLES[k].n + '</div>' + (hint ? '<div class="sub" style="font-size:13px">' + hint + '</div>' : '') + '</div><span class="tag ' + DIFF[r.diff][1] + '">' + DIFF[r.diff][0] + '</span></div>' +
        '<div class="meta"><span><b>' + fmtDist(r.len) + '</b></span><span><b>' + fmtTime(r.secs) + '</b></span><span>bergauf <b>' + Math.round(r.asc) + ' m</b></span><span>am Meer <b>' + fmtDist(r.coast) + '</b></span><span>schön <b>' + r.beauty + '/10</b></span></div></div>';
    }
    sheet('<h2>Wie willst du fahren?</h2><div class="sub">Nach ' + esc(title) + ' &middot; Start: ' + esc(pts[0].name || 'Start') + '</div>' +
      (same ? '<div class="sub" style="margin-top:6px">Hier ist die schnellste auch die schönste Strecke.</div>' : '') +
      '<div style="margin-top:12px">' + card('fast') + card('scenic') + '</div>' +
      '<div class="btns"><button class="go sec" id="cancel">Abbrechen</button></div>');
    sheetEl.querySelectorAll('[data-pick]').forEach(function (c) {
      c.onclick = function () {
        var k = c.getAttribute('data-pick'); S.style = k; saveS(); computeCosts();
        planRoute(pts, title, L2(opt, { style: k, picked: true }));
      };
    });
    $('cancel').onclick = function () { clearRoute(); };
    setFollow(false); fitTo(boundsOf(allPts));
  }
  function L2(a, b) { var o = {}; for (var k in a) o[k] = a[k]; for (k in b) o[k] = b[k]; return o; }
  function gradeColor(g) { return g > 0.10 ? '#ff5a4e' : g > 0.06 ? '#ff922b' : g > 0.03 ? '#ffd43b' : g < -0.04 ? '#4fc3f7' : '#c8f53c'; }
  function drawRoute(r) {
    var ll = r.nodes.map(function (n) { return [LAT[n], LON[n]]; }), f = [];
    // nach Steigung eingefaerbt, in 80-m-Abschnitten
    var k = 0;
    while (k < r.nodes.length - 1) {
      var j = k; while (j < r.nodes.length - 1 && r.cum[j] - r.cum[k] < 80) j++;
      var gr = (r.ele[j] - r.ele[k]) / Math.max(1, r.cum[j] - r.cum[k]);
      f.push(lineF(ll.slice(k, j + 1), { col: gradeColor(gr) }));
      k = j;
    }
    setSrc('route', fc(f)); routeLayer = true;
  }
  function clearRoute(keepSheet) {
    if (routeLayer) setSrc('route', fc()); routeLayer = null;
    if (hoverMarker) setSrc('hover', fc()); hoverMarker = null;
    if (tourLayer) setSrc('tour', fc()); tourLayer = null;
    setSrc('cand', fc());
    clearMarkers(); cur = null; plan = null;
    if (!keepSheet) { dest = null; drawHome(); }
  }
  function mixBar(mix, defs, len, order) {
    var bar = '', leg = '';
    order.forEach(function (k) {
      var v = mix[k] || 0; if (v < 1) return; var pc = v / len * 100;
      bar += '<div style="width:' + pc + '%;background:' + defs[k].c + '"></div>';
      if (pc >= 3) leg += '<span><i style="background:' + defs[k].c + '"></i>' + defs[k].n + ' ' + Math.round(pc) + '%</span>';
    });
    return '<div class="mix">' + bar + '</div><div class="legend">' + leg + '</div>';
  }
  var DIFF = [['Leicht', 'lime'], ['Mittel', 'orange'], ['Schwer', 'red']];
  function routeStatsHtml(r) {
    return '<div class="stats"><div class="stat"><b>' + fmtDist(r.len) + '</b><span>Strecke</span></div><div class="stat"><b>' + fmtTime(r.secs) + '</b><span>Fahrzeit</span></div><div class="stat"><b>' + clock(r.secs) + '</b><span>Ankunft</span></div></div>' +
      '<div class="stats"><div class="stat w"><b>' + Math.round(r.asc) + ' m</b><span>Bergauf</span></div><div class="stat w"><b>' + Math.round(r.desc) + ' m</b><span>Bergab</span></div><div class="stat w"><b>' + pct(r.maxG) + '</b><span>Max. Steigung</span></div></div>' +
      '<div class="stats two"><div class="stat"><b style="color:#4fc3f7">' + fmtDist(r.coast) + '</b><span>Direkt am Meer</span></div><div class="stat"><b>' + r.beauty + ' / 10</b><span>Schönheit</span></div></div>';
  }
  function settingsChips() {
    return '<div class="hscroll">' + Object.keys(BIKES).map(function (k) { return '<button class="chip sm' + (k === S.bike ? ' on' : '') + '" data-bike="' + k + '">' + BIKES[k].n + '</button>'; }).join('') + '</div>' +
      '<div class="seg">' + Object.keys(STYLES).map(function (k) { return '<button class="' + (k === S.style ? 'on' : '') + '" data-style="' + k + '">' + STYLES[k].n + '</button>'; }).join('') + '</div>' +
      '<div class="sub" style="font-size:12.5px;margin:-4px 0 4px">' + STYLES[S.style].d + '</div>';
  }
  function bindSettingsChips(after) {
    sheetEl.querySelectorAll('[data-bike]').forEach(function (b) { b.onclick = function () { S.bike = b.getAttribute('data-bike'); saveS(); computeCosts(); after(); }; });
    sheetEl.querySelectorAll('[data-style]').forEach(function (b) { b.onclick = function () { S.style = b.getAttribute('data-style'); saveS(); computeCosts(); after(); }; });
  }
  function drawRouteSheet() {
    var r = cur, d = DIFF[r.diff];
    var start = plan.pts[0];
    var html = '<div style="display:flex;align-items:flex-start;gap:8px"><div class="grow"><h2>' + esc(plan.title) + '</h2><div class="sub">Start: ' + esc(start.name || 'Start') + (start === PREKO && !myRef() && !plan.opt.tour ? ' (du bist nicht auf der Insel)' : '') + (plan.pts.length > 2 && !plan.opt.loop ? ' &middot; ' + (plan.pts.length - 2) + ' Zwischenziel(e)' : '') + '</div></div><span class="tag ' + d[1] + '" style="margin-top:4px">' + d[0] + '</span></div>' +
      (plan.desc ? '<div class="sub" style="margin-top:6px">' + esc(plan.desc) + '</div>' : '') +
      settingsChips() + routeStatsHtml(r) +
      '<div class="prof" id="prof">' + profileSvg(r, 340, 120, true) + '</div><div class="sub" id="profInfo" style="font-size:12px;min-height:16px">Finger auf das Profil legen, um den Punkt auf der Karte zu sehen.</div>' +
      '<div class="btns"><button class="go sec" id="cancel">Abbrechen</button><button class="go" id="start">Los fahren</button></div>' +
      '<h3>Belag</h3>' + mixBar(r.mixS, SURF, r.len, [0, 1, 2, 3, 4, 5]) +
      '<h3>Wegart</h3>' + mixBar(r.mixC, CLASS, r.len, [0, 1, 5, 2, 8, 3, 4, 6]) +
      (r.walk > 30 ? '<div class="sub" style="margin-top:8px">Ca. ' + fmtDist(r.walk) + ' zu steil oder zu grob zum Fahren: dort wird Schieben eingerechnet.</div>' : '') +
      (r.climbs.length ? '<h3>Anstiege</h3>' + r.climbs.map(function (c) {
        return '<div class="climb"><div class="gbar" style="background:' + gradeColor(c.g) + '"></div><div class="grow">' + fmtDist(c.len) + ' mit ' + pct(c.g) + ' &middot; +' + Math.round(c.gain) + ' m</div><div class="sub">ab km ' + fmtKm(c.a) + '</div></div>';
      }).join('') : '') +
      '<div class="btns"><button class="go sec" id="addVia">Zwischenziel</button>' + (plan.pts.length > 2 && !plan.opt.tour ? '<button class="go sec" id="clrVia">Ohne Zwischenziele</button>' : plan.opt.tour ? '<button class="go sec" id="reverse">Umkehren</button>' : '') + '</div>' +
      '<div class="sub" style="margin-top:10px;font-size:12px">Fahrzeit berechnet für ' + BIKES[S.bike].n + ', ' + FIT[S.fit].n + (S.wind && WX ? ', mit aktuellem Wind ' + Math.round(WX.wind) + ' km/h aus ' + compass(WX.dir) : '') + '. Farben: grün flach, gelb ab 3 %, orange ab 6 %, rot ab 10 %, blau bergab.</div>';
    sheet(html);
    bindSettingsChips(function () { planRoute(plan.pts, plan.title, L2(plan.opt, { style: S.style, picked: true })); });
    $('cancel').onclick = function () { clearRoute(); };
    $('start').onclick = startNav;
    $('addVia').onclick = function () { state = 'pickVia'; toast('Tippe auf die Karte, um ein Zwischenziel zu setzen.'); sheetEl.classList.add('mini'); placeSide(); };
    if ($('clrVia')) $('clrVia').onclick = function () { planRoute([plan.pts[0], plan.pts[plan.pts.length - 1]], plan.title, L2(plan.opt, { picked: true })); };
    if ($('reverse')) $('reverse').onclick = function () {
      var p = plan.pts.slice().reverse().map(function (q) { return { lat: q.lat, lon: q.lon, node: q.node, name: q.name }; });
      if (plan.opt.follow) p[p.length - 1].prefer = plan.opt.follow;
      planRoute(p, plan.opt.loop ? plan.title : (p[p.length - 1].name || 'Ziel'), plan.opt);
    };
    bindProfile(r, $('prof'), $('profInfo'));
  }

  // ---------- Höhenprofil ----------
  function profileSvg(r, w, h, axes, mark) {
    var pl = axes ? 30 : 0, pb = axes ? 16 : 0, iw = w - pl, ih = h - pb - 4;
    var lo = Math.max(0, Math.floor((r.minEle - 5) / 20) * 20), hi = Math.max(lo + 40, Math.ceil((r.maxEle + 5) / 20) * 20);
    var X = function (d) { return pl + d / Math.max(1, r.len) * iw; }, Y = function (z) { return 4 + ih - (z - lo) / (hi - lo) * ih; };
    var out = '<svg viewBox="0 0 ' + w + ' ' + h + '" preserveAspectRatio="none" style="height:' + h + 'px">';
    if (axes) {
      [lo, (lo + hi) / 2, hi].forEach(function (z) { out += '<line x1="' + pl + '" x2="' + w + '" y1="' + Y(z) + '" y2="' + Y(z) + '" stroke="#323c47" stroke-width="1"/><text x="' + (pl - 4) + '" y="' + (Y(z) + 4) + '" fill="#93a0ad" font-size="10" text-anchor="end">' + Math.round(z) + '</text>'; });
      var stepKm = r.len > 30000 ? 10000 : r.len > 12000 ? 5000 : r.len > 4000 ? 2000 : 1000;
      for (var km = stepKm; km < r.len; km += stepKm) out += '<text x="' + X(km) + '" y="' + (h - 3) + '" fill="#93a0ad" font-size="10" text-anchor="middle">' + km / 1000 + ' km</text>';
    }
    // gefuellte Flaechen je Steigungsabschnitt
    var k = 0, step = Math.max(40, r.len / 160);
    while (k < r.nodes.length - 1) {
      var j = k; while (j < r.nodes.length - 1 && r.cum[j] - r.cum[k] < step) j++;
      var gr = (r.ele[j] - r.ele[k]) / Math.max(1, r.cum[j] - r.cum[k]);
      var pts = X(r.cum[k]) + ',' + (4 + ih) + ' ';
      for (var q = k; q <= j; q++) pts += X(r.cum[q]).toFixed(1) + ',' + Y(r.ele[q]).toFixed(1) + ' ';
      pts += X(r.cum[j]) + ',' + (4 + ih);
      out += '<polygon points="' + pts + '" fill="' + gradeColor(gr) + '" fill-opacity="0.85"/>';
      k = j;
    }
    if (mark != null) out += '<line x1="' + X(mark) + '" x2="' + X(mark) + '" y1="0" y2="' + (4 + ih) + '" stroke="#fff" stroke-width="2"/>';
    out += '<line id="profCursor" x1="-10" x2="-10" y1="0" y2="' + (4 + ih) + '" stroke="#fff" stroke-width="1.5" stroke-dasharray="3 2"/>';
    return out + '</svg>';
  }
  function sparkSvg(r) {
    var w = 300, h = 36, lo = r.minEle, hi = Math.max(lo + 30, r.maxEle), pts = '0,' + h + ' ';
    var stp = Math.max(1, Math.floor(r.nodes.length / 120));
    for (var k = 0; k < r.nodes.length; k += stp) pts += (r.cum[k] / r.len * w).toFixed(1) + ',' + (h - 2 - (r.ele[k] - lo) / (hi - lo) * (h - 4)).toFixed(1) + ' ';
    pts += w + ',' + h;
    return '<svg class="spark" viewBox="0 0 ' + w + ' ' + h + '" preserveAspectRatio="none"><polygon points="' + pts + '" fill="#c8f53c" fill-opacity="0.25" stroke="#c8f53c" stroke-width="1.2"/></svg>';
  }
  function idxAt(r, d) { var lo = 0, hi = r.cum.length - 1; while (lo < hi) { var m = (lo + hi) >> 1; if (r.cum[m] < d) lo = m + 1; else hi = m; } return lo; }
  function bindProfile(r, el, info) {
    if (!el) return;
    function at(ev) {
      var t = ev.touches ? ev.touches[0] : ev, rc = el.getBoundingClientRect(), pl = 30 / 340 * rc.width;
      var f = Math.max(0, Math.min(1, (t.clientX - rc.left - pl) / (rc.width - pl))), d = f * r.len, k = idxAt(r, d), n = r.nodes[k];
      var k2 = idxAt(r, Math.min(r.len, d + 50)), k1 = idxAt(r, Math.max(0, d - 50));
      var g = (r.ele[k2] - r.ele[k1]) / Math.max(1, r.cum[k2] - r.cum[k1]);
      var cur_ = el.querySelector('#profCursor'), x = 30 + f * 310; cur_.setAttribute('x1', x); cur_.setAttribute('x2', x);
      setSrc('hover', fc([ptF(LAT[n], LON[n])])); hoverMarker = true;
      var w = W[eWay[r.edges[Math.max(0, k - 1)]]];
      info.innerHTML = 'km ' + fmtKm(d) + ' &middot; ' + Math.round(r.ele[k]) + ' m &middot; ' + (g >= 0 ? '+' : '') + Math.round(g * 100) + ' % &middot; ' + CLASS[w[0]].n + ', ' + SURF[w[3]].n;
      if (ev.cancelable) ev.preventDefault();
    }
    el.addEventListener('touchstart', at, { passive: false }); el.addEventListener('touchmove', at, { passive: false });
    el.addEventListener('mousemove', at);
  }

  // =====================================================================
  // Touren
  // =====================================================================
  var tourLayer = null, officialCache = null;
  function findSpec(spec) {
    var ab = spec.split(':'), kind = ab[0], name = norm(ab.slice(1).join(':'));
    if (kind === 'place') { var p = D.places.filter(function (x) { return norm(x[0]) === name; })[0]; if (p) return { lat: p[1], lon: p[2], name: p[0] }; }
    if (kind === 'ferry') return PREKO;
    var q = D.pois.filter(function (x) { return x[0] === kind && norm(x[3]).indexOf(name) >= 0; })[0];
    if (q) return { lat: q[1], lon: q[2], name: q[3] };
    return null;
  }
  var IDEAS = [
    { n: 'Festung Sveti Mihovil', d: 'Der Klassiker der Insel: Auffahrt zur Festung auf dem Kamm (ca. 250 m) mit Blick auf Zadar, Pašman und die Kornaten. Steil, aber lohnend.', pts: ['ferry:Preko', 'sight:Tvrđava sv. Mihovila', 'ferry:Preko'] },
    { n: 'Lukoran, Sutomišćica, Poljana', d: 'Kurze, flache Familienrunde nordwestlich von Preko durch kleine Hafendörfer mit Badebuchten.', pts: ['ferry:Preko', 'place:Lukoran', 'place:Sutomišćica', 'place:Poljana', 'ferry:Preko'] },
    { n: 'Süden: Kali und Kukljica', d: 'Über Kali, das Fischerdorf, nach Kukljica an der Suedspitze. Viele Konobas und Badestellen unterwegs.', pts: ['ferry:Preko', 'place:Kali', 'place:Kukljica', 'ferry:Preko'] },
    { n: 'Aussichtspunkt Gora', d: 'Über Olivenhaine und Feldwege auf den Inselrücken zum Aussichtspunkt Gora.', pts: ['ferry:Preko', 'view:Gora', 'ferry:Preko'] },
    { n: 'Inselnorden bis Muline', d: 'Lange Runde in den ruhigen Norden über Ugljan-Dorf bis nach Muline mit seinen Stränden.', pts: ['ferry:Preko', 'place:Ugljan', 'place:Muline', 'ferry:Preko'] },
    { n: 'Nordspitze und Stražica', d: 'Vom Dorf Ugljan zur Aussicht Stražica an der Nordspitze der Insel.', pts: ['place:Ugljan', 'view:Stražica', 'place:Ugljan'] },
    { n: 'Ganze Insel von Nord nach Sued', d: 'Die komplette Insel in einem Zug: von Muline im Norden bis Kukljica im Süden.', pts: ['place:Muline', 'place:Ugljan', 'place:Preko', 'place:Kali', 'place:Kukljica'], oneway: true }
  ];
  function officialRoutes() {
    if (officialCache && officialCache.bike === S.bike + S.fit + S.style + (WX ? WX.t : '')) return officialCache.list;
    var list = [];
    D.routes.forEach(function (rt, ri) {
      var pref = {}, cnt = {};
      rt.w.forEach(function (wi) { pref[wi] = 1; var p = W[wi][4]; cnt[p[0]] = (cnt[p[0]] || 0) + 1; cnt[p[p.length - 1]] = (cnt[p[p.length - 1]] || 0) + 1; });
      var ends = Object.keys(cnt).filter(function (n) { return cnt[n] === 1 && COMP[+n]; }).map(Number);
      if (ends.length < 2) ends = Object.keys(cnt).map(Number).filter(function (n) { return COMP[n]; });
      var a = ends[0], b = ends[0], bd = -1;
      for (var x = 0; x < ends.length; x++) for (var y = x + 1; y < ends.length; y++) { var d = dist(LAT[ends[x]], LON[ends[x]], LAT[ends[y]], LON[ends[y]]); if (d > bd) { bd = d; a = ends[x]; b = ends[y]; } }
      if (a == null || a === b) return;
      var r = routeVia([{ node: a, lat: LAT[a], lon: LON[a] }, { node: b, lat: LAT[b], lon: LON[b], prefer: pref }]);
      if (!r) return;
      var pa = nearestPlace(LAT[a], LON[a], true)[0], pb = nearestPlace(LAT[b], LON[b], true)[0];
      var title = rt.name && rt.net === 'icn' ? rt.name.replace(' - part Croatia', '') : 'Radroute ' + rt.ref + (rt.name && !/^\d/.test(rt.name) ? ': ' + rt.name.replace(/^\d+\s*-\s*/, '') : ': ' + pa + (pa !== pb ? ' - ' + pb : ''));
      list.push({ ri: ri, title: title, r: r, a: a, b: b, pref: pref, ref: rt.ref });
    });
    officialCache = { bike: S.bike + S.fit + S.style + (WX ? WX.t : ''), list: list };
    return list;
  }
  function tourCard(attr, title, r, sub) {
    var d = DIFF[r.diff];
    return '<div class="card" ' + attr + '><div class="top"><div class="grow"><div class="nm">' + esc(title) + '</div>' + (sub ? '<div class="sub" style="font-size:13px;margin-top:2px">' + esc(sub) + '</div>' : '') + '</div><span class="tag ' + d[1] + '">' + d[0] + '</span></div>' +
      '<div class="meta"><span><b>' + fmtDist(r.len) + '</b></span><span><b>' + fmtTime(r.secs) + '</b></span><span>bergauf <b>' + Math.round(r.asc) + ' m</b></span><span>max. <b>' + pct(r.maxG) + '</b></span><span>am Meer <b>' + fmtDist(r.coast) + '</b></span><span>schön <b>' + r.beauty + '/10</b></span></div>' +
      '<div class="mix" style="height:6px;margin:4px 0 0">' + ['0', '1', '2', '3', '4', '5'].map(function (k) { var v = r.mixS[k] || 0; return v ? '<div style="width:' + v / r.len * 100 + '%;background:' + SURF[k].c + '"></div>' : ''; }).join('') + '</div>' + sparkSvg(r) + '</div>';
  }
  var tourTab = 'official', loopKm = load('loopKm', 20), loopRes = null, ideaCache = null;
  function showTours(tab) {
    tourTab = tab || tourTab; state = 'tours'; clearRoute(true);
    var head = '<h2>Touren auf Ugljan</h2><div class="seg"><button data-tab="official" class="' + (tourTab === 'official' ? 'on' : '') + '">Radrouten</button><button data-tab="ideas" class="' + (tourTab === 'ideas' ? 'on' : '') + '">Tourenideen</button><button data-tab="loop" class="' + (tourTab === 'loop' ? 'on' : '') + '">Rundtour</button></div>';
    var body = '';
    if (tourTab === 'official') {
      body = '<div class="sub" style="margin-bottom:8px">Offiziell beschilderte Radrouten (auf der Karte orange hinterlegt). Werte für ' + BIKES[S.bike].n + '.</div>' +
        officialRoutes().map(function (o, k) { return tourCard('data-off="' + k + '"', o.title, o.r, nearestPlace(LAT[o.a], LON[o.a], true)[0] + ' bis ' + nearestPlace(LAT[o.b], LON[o.b], true)[0]); }).join('');
    } else if (tourTab === 'ideas') {
      var key = S.bike + S.fit + S.style + (WX ? WX.t : '');
      if (!ideaCache || ideaCache.key !== key) {
        ideaCache = { key: key, list: IDEAS.map(function (t) {
          var pts = t.pts.map(findSpec); if (pts.some(function (p) { return !p; })) return null;
          var loop = t.pts[0] === t.pts[t.pts.length - 1] && !t.oneway;
          var r = routeVia(pts, { loop: loop }); return r ? { t: t, pts: pts, r: r, loop: loop } : null;
        }) };
      }
      body = '<div class="sub" style="margin-bottom:8px">Ausgesuchte Touren, Start an der Fähre Preko (außer anders angegeben). Mit "Los fahren" wirst du von deinem Standort aus hingeführt.</div>' +
        ideaCache.list.map(function (x, k) { return x ? tourCard('data-idea="' + k + '"', x.t.n, x.r, x.t.d) : ''; }).join('');
    } else {
      var s = startRef();
      body = '<div class="sub">Die App sucht Rundtouren ab ' + (s === PREKO ? 'der Fähre Preko' : 'deinem Standort') + ' mit möglichst wenig doppelt gefahrenen Wegen.</div>' +
        '<div style="display:flex;align-items:center;gap:12px;margin-top:12px"><input type="range" id="loopKm" min="6" max="70" step="2" value="' + loopKm + '"><b id="loopKmV" style="font-size:20px;min-width:64px;text-align:right">' + loopKm + ' km</b></div>' +
        '<div class="btns"><button class="go" id="loopGo">Rundtouren vorschlagen</button></div><div id="loopOut" style="margin-top:12px">' +
        (loopRes ? loopRes.map(function (x, k) { return tourCard('data-loop="' + k + '"', 'Rundtour ' + (k + 1) + ': ' + x.dir, x.r, 'über ' + x.via); }).join('') : '') + '</div>';
    }
    sheet(head + settingsChips() + body);
    bindSettingsChips(function () { loopRes = null; showTours(); });
    sheetEl.querySelectorAll('[data-tab]').forEach(function (b) { b.onclick = function () { showTours(b.getAttribute('data-tab')); }; });
    if ($('loopKm')) {
      $('loopKm').oninput = function () { loopKm = +this.value; $('loopKmV').textContent = loopKm + ' km'; store('loopKm', loopKm); };
      $('loopGo').onclick = function () { $('loopOut').innerHTML = '<div class="empty">Berechne Rundtouren ...</div>'; setTimeout(function () { loopRes = makeLoops(loopKm * 1000); showTours('loop'); }, 30); };
    }
  }
  sheetEl.addEventListener('click', function (e) {
    var c = e.target.closest('[data-off]');
    if (c) { var o = officialRoutes()[+c.getAttribute('data-off')]; showTourPreview(o.title, [{ node: o.a, lat: LAT[o.a], lon: LON[o.a], name: 'Routenanfang' }, { node: o.b, lat: LAT[o.b], lon: LON[o.b], name: 'Routenende', prefer: o.pref }], { desc: 'Beschilderte Radroute ' + o.ref + '. Mit "Los fahren" führt dich die App zuerst zum Anfang.', follow: o.pref }); return; }
    c = e.target.closest('[data-idea]');
    if (c) { var x = ideaCache.list[+c.getAttribute('data-idea')]; showTourPreview(x.t.n, x.pts, { desc: x.t.d, loop: x.loop }); return; }
    c = e.target.closest('[data-loop]');
    if (c) { var l = loopRes[+c.getAttribute('data-loop')]; showTourPreview('Rundtour ' + Math.round(l.r.len / 1000) + ' km: ' + l.dir, l.pts, { desc: 'Rundtour über ' + l.via + '.', loop: true }); }
  });
  function showTourPreview(title, pts, opt) {
    opt.tour = true;
    planRoute(pts, title, opt);
  }
  // Rundtouren-Generator
  function makeLoops(target) {
    var s = startRef(), sn = nearestNode(s.lat, s.lon), res = [];
    var R = target / (2 * Math.PI) * 1.15;
    for (var h = 0; h < 360; h += 30) {
      var cands = [];
      [[h, R * 1.05], [h + 70, R * 1.6], [h + 140, R * 1.05]].forEach(function (x) {
        var a = x[0] * RAD, la = s.lat + Math.cos(a) * x[1] / 110540, lo = s.lon + Math.sin(a) * x[1] / (111320 * Math.cos(s.lat * RAD));
        cands.push({ lat: la, lon: lo });
      });
      var pts = [{ lat: s.lat, lon: s.lon, name: s.name, node: sn }].concat(cands.map(function (c) { var n = nearestNode(c.lat, c.lon); return { node: n, lat: LAT[n], lon: LON[n], name: '' }; })).concat([{ lat: s.lat, lon: s.lon, name: s.name, node: sn }]);
      var r = routeVia(pts, { loop: true });
      if (!r || r.len < 1000) continue;
      // doppelt gefahrene Anteile
      var seen = {}, dup = 0;
      r.edges.forEach(function (e) { var k = Math.min(eFrom[e], eTo[e]) + '_' + Math.max(eFrom[e], eTo[e]); if (seen[k]) dup += eLen[e]; seen[k] = 1; });
      var score = Math.abs(r.len - target) / target + dup / r.len * 1.2 - r.beauty / 10 * 0.5;
      res.push({ r: r, pts: pts, score: score, h: h });
    }
    res.sort(function (a, b) { return a.score - b.score; });
    var out = [];
    res.forEach(function (x) {
      if (out.length >= 4) return;
      if (out.some(function (y) { return Math.abs(y.r.len - x.r.len) < 300 && Math.abs(y.r.asc - x.r.asc) < 10; })) return;
      var vs = {}; x.pts.slice(1, -1).forEach(function (p) { vs[nearestPlace(p.lat, p.lon, true)[0]] = 1; });
      var dirs = ['Norden', 'Nordosten', 'Osten', 'Südosten', 'Süden', 'Südwesten', 'Westen', 'Nordwesten'];
      x.dir = 'Richtung ' + dirs[Math.round(((x.h + 70) % 360) / 45) % 8];
      x.via = Object.keys(vs).join(', ');
      x.pts[x.pts.length - 1] = x.pts[0];
      out.push(x);
    });
    return out;
  }

  // ---------- In der Nähe ----------
  var nearTab = 'food';
  function showNearby(tab) {
    nearTab = tab || nearTab; state = 'near'; clearRoute(true);
    var ref = myRef(), base = ref || (function () { var c = map.getCenter(); return { lat: c.lat, lon: c.lng }; })();
    var tabs = [['food', 'Essen', ['food', 'snack']], ['cafe', 'Cafe/Bar', ['cafe', 'bar']], ['beach', 'Baden', ['beach']], ['water', 'Wasser/WC', ['water', 'wc']], ['shop', 'Laden', ['shop', 'money', 'health']], ['view', 'Sehen', ['view', 'peak', 'sight']], ['bike', 'Rad', ['bike']], ['stay', 'Schlafen', ['stay']]];
    var t = tabs.filter(function (x) { return x[0] === nearTab; })[0];
    var list = nearby(base.lat, base.lon, t[2], 40);
    sheet('<h2>In der Nähe</h2><div class="sub">' + (ref ? 'Von deinem Standort aus' : 'Von der Kartenmitte aus') + ', Luftlinie</div>' +
      '<div class="hscroll">' + tabs.map(function (x) { return '<button class="chip sm' + (x[0] === nearTab ? ' on' : '') + '" data-near="' + x[0] + '">' + x[1] + '</button>'; }).join('') + '</div>' +
      '<div class="list">' + (list.length ? list.map(poiRow).join('') : '<div class="empty">Nichts gefunden</div>') + '</div>');
    sheetEl.querySelectorAll('[data-near]').forEach(function (b) { b.onclick = function () { showNearby(b.getAttribute('data-near')); }; });
  }

  // =====================================================================
  // Menü
  // =====================================================================
  function sw(id, on) { return '<div class="sw' + (on ? ' on' : '') + '" id="' + id + '"></div>'; }
  function showMenu(focus) {
    state = 'menu'; clearRoute(true);
    var rides = load('rides', []);
    var html = '<h2>Einstellungen</h2>' +
      '<h3>Dein Fahrrad</h3><div class="hscroll" style="flex-wrap:wrap;overflow:visible">' + Object.keys(BIKES).map(function (k) { return '<button class="chip' + (k === S.bike ? ' on' : '') + '" data-mbike="' + k + '">' + BIKES[k].n + '</button>'; }).join('') + '</div>' +
      '<h3>Fitness</h3><div class="seg">' + Object.keys(FIT).map(function (k) { return '<button class="' + (k === S.fit ? 'on' : '') + '" data-fit="' + k + '">' + FIT[k].n + '</button>'; }).join('') + '</div>' +
      '<h3>Routenstil</h3><div class="seg">' + Object.keys(STYLES).map(function (k) { return '<button class="' + (k === S.style ? 'on' : '') + '" data-mstyle="' + k + '">' + STYLES[k].n + '</button>'; }).join('') + '</div><div class="sub">' + STYLES[S.style].d + '</div>' +
      '<h3>Navigation</h3>' +
      '<div class="opt"><div class="grow">Sprachansagen<div class="sub">Abbiegen, Anstiege, Ankunft</div></div>' + sw('swVoice', S.voice) + '</div>' +
      '<div class="opt"><div class="grow">Wind einrechnen<div class="sub">' + (WX ? 'Aktuell ' + Math.round(WX.wind) + ' km/h aus ' + compass(WX.dir) + windName(WX.dir) : 'Wetterdaten brauchen Internet') + '</div></div>' + sw('swWind', S.wind) + '</div>' +
      '<h3 id="mapSec">Karte</h3>' +
      '<div class="opt"><div class="grow">Relief (Berge schattiert)</div>' + sw('swRelief', S.relief) + '</div>' +
      '<div class="opt"><div class="grow">Höhenlinien (alle 25 m)</div>' + sw('swCont', S.contours) + '</div>' +
      '<div class="opt"><div class="grow">Radrouten hervorheben</div>' + sw('swRoutes', S.routes) + '</div>' +
      '<div class="opt"><div class="grow">Ortsnamen</div>' + sw('swLabels', S.labels) + '</div>' +
      '<h3>Legende</h3>' + legendHtml() +
      '<h3>Meine Fahrten</h3>' +
      (rides.length ? rides.map(function (r, k) {
        var d = new Date(r.t);
        return '<div class="row" data-ride="' + k + '"><div class="ico">' + svg('bike', '#c8f53c', 18) + '</div><div class="grow"><div class="t">' + d.toLocaleDateString('de-DE') + ', ' + hhmm(d) + '</div><div class="s">' + fmtTime(r.mv) + ' in Bewegung &middot; ' + Math.round(r.asc) + ' m bergauf &middot; ' + (r.mv > 0 ? (r.dist / r.mv * 3.6).toFixed(1).replace('.', ',') : '0') + ' km/h</div></div><div class="r"><b>' + fmtDist(r.dist) + '</b></div></div>';
      }).join('') : '<div class="empty">Noch keine Fahrten. Jede Navigation und jede freie Fahrt wird aufgezeichnet.</div>') +
      '<div class="btns"><button class="go" id="mFree">Freie Fahrt starten</button></div>' +
      '<h3>Über die App</h3><div class="sub">Ugljan Rad 3.4. Funktioniert komplett offline. Karte: OpenStreetMap, gezeichnet mit MapLibre GL (' + D.ways.length + ' Wege, ' + D.pois.length + ' Orte). Höhen: Copernicus DEM 90 m. Wetter: Open-Meteo (nur mit Internet). Fahrzeiten werden physikalisch aus Leistung, Gewicht, Rollwiderstand je Belag, Luftwiderstand, Steigung und Wind berechnet.</div>';
    sheet(html);
    sheetEl.querySelectorAll('[data-mbike]').forEach(function (b) { b.onclick = function () { S.bike = b.getAttribute('data-mbike'); saveS(); computeCosts(); showMenu(); }; });
    sheetEl.querySelectorAll('[data-fit]').forEach(function (b) { b.onclick = function () { S.fit = b.getAttribute('data-fit'); saveS(); computeCosts(); showMenu(); }; });
    sheetEl.querySelectorAll('[data-mstyle]').forEach(function (b) { b.onclick = function () { S.style = b.getAttribute('data-mstyle'); saveS(); computeCosts(); showMenu(); }; });
    function tg(id, key, after) { $(id).onclick = function () { S[key] = !S[key]; saveS(); this.classList.toggle('on', S[key]); if (after) after(); }; }
    tg('swVoice', 'voice', function () { if (S.voice) say('Sprachansagen eingeschaltet'); });
    tg('swWind', 'wind', computeCosts);
    tg('swRelief', 'relief', updLayers); tg('swCont', 'contours', updLayers); tg('swRoutes', 'routes', updLayers); tg('swLabels', 'labels', updLayers);
    $('mFree').onclick = startFree;
    sheetEl.querySelectorAll('[data-ride]').forEach(function (b) { b.onclick = function () { showRide(+b.getAttribute('data-ride')); }; });
    if (focus === 'map') setTimeout(function () { var el = $('mapSec'); if (el) sheetEl.scrollTop = el.offsetTop - 40; }, 30);
  }
  $('btnMenu').onclick = function () { if (!nav) showMenu(); };
  $('btnTours').onclick = function () { if (!nav) showTours(); };

  function showRide(k) {
    var r = load('rides', [])[k]; if (!r) return;
    state = 'ride'; clearRoute(true);
    if (r.trk && r.trk.length > 1) {
      setSrc('tour', fc([lineF(r.trk)])); tourLayer = true;
      setFollow(false); fitTo(boundsOf(r.trk));
    }
    var d = new Date(r.t);
    sheet('<h2>Fahrt am ' + d.toLocaleDateString('de-DE') + '</h2><div class="sub">Start ' + hhmm(d) + (r.title ? ' &middot; ' + esc(r.title) : '') + '</div>' +
      '<div class="stats"><div class="stat"><b>' + fmtDist(r.dist) + '</b><span>Strecke</span></div><div class="stat"><b>' + fmtTime(r.mv) + '</b><span>In Bewegung</span></div><div class="stat"><b>' + Math.round(r.asc) + ' m</b><span>Bergauf</span></div></div>' +
      '<div class="stats"><div class="stat w"><b>' + (r.mv > 0 ? (r.dist / r.mv * 3.6).toFixed(1).replace('.', ',') : '0') + '</b><span>km/h Schnitt</span></div><div class="stat w"><b>' + Math.round(r.max || 0) + '</b><span>km/h Max</span></div><div class="stat w"><b>' + fmtTime(r.tot || r.mv) + '</b><span>Gesamtzeit</span></div></div>' +
      '<div class="btns"><button class="go sec" id="rideBack">Zurück</button><button class="go red" id="rideDel">Löschen</button></div>');
    $('rideBack').onclick = function () { showMenu(); };
    $('rideDel').onclick = function () { var l = load('rides', []); l.splice(k, 1); store('rides', l); showMenu(); };
  }

  // =====================================================================
  // Navigation und Aufzeichnung
  // =====================================================================
  var nav = null, rec = null, trackLayer = null;
  function recStart(title) {
    rec = { t: Date.now(), title: title || '', dist: 0, mv: 0, asc: 0, max: 0, last: null, lastT: 0, eref: null, trk: [] };
    trackLayer = []; setSrc('track', fc());
  }
  function recPoint(lat, lon, acc, speed) {
    if (acc > 35) return;
    var now = Date.now();
    if (rec.last) {
      var d = dist(rec.last[0], rec.last[1], lat, lon), dt = (now - rec.lastT) / 1000;
      // Strecke nur, wenn man sich nachweislich bewegt (Stillstand und GPS-Zittern zaehlen nicht)
      if (speed < 0.8) { if (speed >= 0) { rec.last = [lat, lon]; rec.lastT = now; } return; }
      if (d < Math.max(4, acc * 0.5)) return;
      if (dt <= 0 || d / dt > 22) return; // Sprung
      rec.dist += d;
      if (dt < 15) rec.mv += dt;
      var cs = curSpeed(); if (cs && cs > rec.max && cs < 80) rec.max = cs;
    }
    var z = demEle(lat, lon);
    if (rec.eref == null) rec.eref = z; else if (z - rec.eref > 3) { rec.asc += z - rec.eref; rec.eref = z; } else if (z < rec.eref - 3) rec.eref = z;
    rec.last = [lat, lon]; rec.lastT = now;
    rec.trk.push([Math.round(lat * 1e5) / 1e5, Math.round(lon * 1e5) / 1e5]);
    trackLayer.push([lat, lon]); if (trackLayer.length > 1) setSrc('track', fc([lineF(trackLayer)]));
  }
  function saveRide(r) {
    var l = load('rides', []);
    var trk = r.trk, stp = Math.max(1, Math.ceil(trk.length / 1500));
    l.unshift({ t: r.t, title: r.title, dist: r.dist, mv: r.mv, asc: r.asc, max: r.max, tot: ((r.lastT || Date.now()) - r.t) / 1000, trk: trk.filter(function (_, k) { return k % stp === 0; }) });
    store('rides', l.slice(0, 30));
  }
  // Zwischenstand sichern, falls Android die App waehrend der Fahrt beendet
  setInterval(function () {
    if (rec && rec.dist > 0) { var c = {}; for (var k in rec) c[k] = rec[k]; var stp = Math.max(1, Math.ceil(rec.trk.length / 1500)); c.trk = rec.trk.filter(function (_, i) { return i % stp === 0; }); store('recDraft', c); }
  }, 20000);
  (function () {
    var d = load('recDraft', null);
    if (d && d.dist > 300) { saveRide(d); setTimeout(function () { toast('Die letzte Fahrt wurde unterbrochen und gespeichert.'); }, 1500); }
    store('recDraft', null);
  })();
  function recStop() {
    if (!rec) return;
    store('recDraft', null);
    if (rec.dist > 300) {
      rec.lastT = Date.now(); saveRide(rec);
      toast('Fahrt gespeichert: ' + fmtDist(rec.dist));
    }
    rec = null;
    if (trackLayer) setSrc('track', fc()); trackLayer = null;
  }

  function enterNavUi() {
    if (A) { A.keepScreenOn(true); try { A.setNavActive(true, nav && nav.free ? 'Freie Fahrt wird aufgezeichnet' : 'Navigation läuft'); } catch (e) {} }
    $('top').classList.add('hide'); $('chips').classList.add('hide'); $('results').style.display = 'none'; $('gpswarn').style.display = 'none';
    $('btnLayers').classList.add('hide'); document.body.classList.add('navmode'); updSpeedo(); updCompass();
    follow = true; $('btnLoc').classList.add('on');
    updMute();
  }
  function leaveNavUi() {
    if (A) { A.keepScreenOn(false); try { A.setNavActive(false, ''); } catch (e) {} }
    $('top').classList.remove('hide'); $('chips').classList.remove('hide'); $('nav').style.display = 'none'; $('climbBox').style.display = 'none';
    $('btnLayers').classList.remove('hide'); document.body.classList.remove('navmode'); updSpeedo(); smoothBearing(0); setTimeout(updCompass, 600);
  }
  function updMute() { $('navMute').innerHTML = svg(S.voice ? 'vol' : 'mute', '#14181d', 24, 2.4); }
  $('navMute').onclick = function () { S.voice = !S.voice; saveS(); updMute(); if (S.voice) say('Ansagen an'); };

  function startNav() {
    var ref = myRef();
    // Wenn GPS auf der Insel: von hier zur Route bzw. zum Ziel
    if (ref && plan) {
      var p0 = plan.pts[0];
      if (dist(ref.lat, ref.lon, p0.lat, p0.lon) > 60 || p0.name !== 'Mein Standort') {
        var pts = [{ lat: ref.lat, lon: ref.lon, name: 'Mein Standort' }].concat(plan.opt.loop || plan.opt.tour ? plan.pts : plan.pts.slice(1));
        var r = routeVia(pts, { loop: plan.opt.loop, style: plan.opt.style });
        if (r) { cur = r; plan.pts = pts; drawRoute(r); }
      }
    }
    nav = { off: 0, said: {}, climbSaid: {}, arrived: false, lastAlong: 0 };
    state = 'nav';
    $('nav').style.display = 'flex';
    enterNavUi();
    recStart(plan ? plan.title : '');
    if (ref) { setFollow(true); var o = { center: [ref.lon, ref.lat], zoom: 16.3, padding: padding(), duration: 800 }; if (camBearing() !== null) { o.bearing = camBearing(); o.pitch = 45; } map.easeTo(o); } else { view(LAT[cur.nodes[0]], LON[cur.nodes[0]], 16); toast('Kein GPS auf der Insel: Vorschau ab Start.'); }
    var first = cur.steps[0];
    say('Los gehts. ' + fmtSay(cur.len) + ' bis zum Ziel, etwa ' + Math.round(cur.secs / 60) + ' Minuten' + (cur.asc > 30 ? ', ' + Math.round(cur.asc) + ' Höhenmeter.' : '.'));
    navUpdate();
  }
  function startFree() {
    clearRoute(true); plan = null; cur = null;
    nav = { free: true }; state = 'nav';
    enterNavUi();
    recStart('Freie Fahrt');
    var ref = myRef(); if (ref) { setFollow(true); view(ref.lat, ref.lon, 16); }
    say('Aufzeichnung gestartet');
    navUpdate();
  }
  function stopNav() {
    var wasFree = nav && nav.free;
    nav = null; leaveNavUi(); recStop();
    if (hoverMarker) { setSrc('hover', fc()); hoverMarker = null; }
    clearRoute();
  }
  function fmtSay(m) { return m < 1000 ? (Math.round(m / 50) * 50 || 50) + ' Meter' : (m / 1000).toFixed(1).replace('.', ',').replace(',0', '') + ' Kilometer'; }
  function project(r, lat, lon, from) {
    var best = { d: 1e18, along: 0, i: 0 }, kx = 111320 * Math.cos(lat * RAD), ky = 110540;
    function seg(i) {
      var a = r.nodes[i], b = r.nodes[i + 1];
      var ax = (LON[a] - lon) * kx, ay = (LAT[a] - lat) * ky, bx = (LON[b] - lon) * kx, by = (LAT[b] - lat) * ky;
      var dx = bx - ax, dy = by - ay, l2 = dx * dx + dy * dy, t = l2 ? Math.max(0, Math.min(1, -(ax * dx + ay * dy) / l2)) : 0;
      var px = ax + t * dx, py = ay + t * dy, d = Math.sqrt(px * px + py * py);
      if (d < best.d) best = { d: d, along: r.cum[i] + t * (r.cum[i + 1] - r.cum[i]), i: i };
    }
    // bevorzugt vorwaerts suchen (wichtig bei Rundtouren, die sich kreuzen)
    for (var i = 0; i + 1 < r.nodes.length; i++) if (r.cum[i + 1] >= from - 150 && r.cum[i] <= from + 1500) seg(i);
    if (best.d > 40) for (i = 0; i + 1 < r.nodes.length; i++) seg(i);
    return best;
  }
  function arrowFor(turn) {
    if (Math.abs(turn) < 25) return 'M24 42 L24 8 M13 19 L24 8 L35 19';
    var right = turn > 0, a = Math.abs(turn);
    if (a < 55) return right ? 'M18 42 L18 26 L34 10 M22 10 L34 10 L34 22' : 'M30 42 L30 26 L14 10 M26 10 L14 10 L14 22';
    if (a > 130) return right ? 'M14 42 L14 12 L32 30 M32 18 L32 30 L20 30' : 'M34 42 L34 12 L16 30 M16 18 L16 30 L28 30';
    return right ? 'M14 42 L14 20 L38 20 M30 12 L38 20 L30 28' : 'M34 42 L34 20 L10 20 M18 12 L10 20 L18 28';
  }
  function remainingTime(r, along) {
    var k = idxAt(r, along), t = 0;
    for (var j = k; j < r.edges.length; j++) t += eTime[r.edges[j]];
    return t;
  }
  function navUpdate() {
    if (!nav) return;
    var ref = myRef();
    updSpeedo();
    if (nav.free) { freeSheet(ref); return; }
    var r = cur, along = nav.lastAlong;
    if (ref) {
      var pr = project(r, ref.lat, ref.lon, nav.lastAlong);
      if (pr.d > 35 + Math.min(ref.acc, 30)) {
        nav.off++;
        if (nav.off >= 3) {
          nav.off = 0;
          // verbleibende Wegpunkte behalten
          var rest = plan.pts.filter(function (p, k) { return k > 0 && r.legEnds[k - 1] > nav.lastAlong + 30; });
          if (!rest.length) rest = [plan.pts[plan.pts.length - 1]];
          var pts = [{ lat: ref.lat, lon: ref.lon, name: 'Mein Standort' }].concat(rest);
          var nr = routeVia(pts, { style: plan.opt.style });
          if (nr) { cur = r = nr; plan.pts = pts; drawRoute(r); nav.said = {}; nav.climbSaid = {}; say('Route wird neu berechnet'); toast('Neue Route'); pr = project(r, ref.lat, ref.lon, 0); }
        }
      } else nav.off = 0;
      along = pr.along; nav.lastAlong = along;
    }
    var remain = Math.max(0, r.len - along);
    var si = 0; while (si < r.steps.length - 1 && r.steps[si].at <= along + 8) si++;
    var step = r.steps[si], next = r.steps[si + 1], toStep = Math.max(0, step.at - along);
    var secs = remainingTime(r, along);
    if (lastSpeed > 8 && ref) { var model = remain / Math.max(1, secs); secs = remain / ((model * 2 + lastSpeed / 3.6) / 3); }
    // Ansagen
    if (ref && !step.end) {
      var key = si + '_' + step.at.toFixed(0);
      if (toStep < 260 && toStep > 90 && !nav.said[key + 'f']) { nav.said[key + 'f'] = 1; say('In ' + fmtSay(toStep) + ' ' + step.say + (step.name ? ' in ' + step.name : '')); }
      if (toStep <= 35 && !nav.said[key + 'n']) { nav.said[key + 'n'] = 1; say('Jetzt ' + step.say + (step.name ? ' in ' + step.name : '')); buzz(250); }
    }
    var upcoming = r.climbs.filter(function (c) { return c.b > along; })[0];
    if (ref && upcoming && upcoming.a - along < 300 && upcoming.a - along > 0 && !nav.climbSaid[upcoming.a]) {
      nav.climbSaid[upcoming.a] = 1;
      say('Achtung, Anstieg in ' + fmtSay(upcoming.a - along) + ': ' + fmtSay(upcoming.len) + ' mit ' + Math.round(upcoming.g * 100) + ' Prozent');
    }
    $('spRem').textContent = fmtDist(remain) + ' · ' + clock(secs);
    if (remain < 25 && ref && !nav.arrived) { nav.arrived = true; say('Du hast dein Ziel erreicht'); buzz(600); }
    if (nav.arrived) {
      $('navDist').textContent = 'Angekommen'; $('navText').textContent = plan.title; $('navThen').textContent = '';
      $('navPath').setAttribute('d', 'M10 26 L20 36 L38 14');
    } else {
      $('navDist').textContent = fmtDist(toStep);
      $('navText').textContent = step.end ? 'Ziel: ' + plan.title : step.text + (step.name ? ' in ' + step.name : ' auf ' + CLASS[step.cls].n);
      $('navThen').textContent = next && next.at - step.at < 150 && !step.end ? 'Danach: ' + next.text : (step.end ? '' : 'Belag: ' + SURF[step.surf].n);
      $('navPath').setAttribute('d', step.end ? 'M14 42 L14 6 L36 14 L14 22' : arrowFor(step.turn));
    }
    // Anstiegsbox
    var cb = $('climbBox');
    var inClimb = r.climbs.filter(function (c) { return c.a <= along && c.b > along; })[0];
    if (inClimb) {
      cb.style.display = 'flex';
      cb.innerHTML = svg('up', gradeColor(inClimb.g), 22) + '<div class="grow">Im Anstieg: noch <b>' + fmtDist(inClimb.b - along) + '</b> und <b>' + Math.round(inClimb.top - r.ele[idxAt(r, along)]) + ' m</b> bis oben</div><b style="color:' + gradeColor(inClimb.g) + '">' + pct(inClimb.g) + '</b>';
    } else if (upcoming && upcoming.a - along < 1500) {
      cb.style.display = 'flex';
      cb.innerHTML = svg('up', gradeColor(upcoming.g), 22) + '<div class="grow">Anstieg in <b>' + fmtDist(upcoming.a - along) + '</b>: ' + fmtDist(upcoming.len) + ', +' + Math.round(upcoming.gain) + ' m</div><b style="color:' + gradeColor(upcoming.g) + '">' + pct(upcoming.g) + '</b>';
    } else cb.style.display = 'none';
    var k = idxAt(r, along), ascLeft = 0, ref2 = r.ele[k];
    for (var j = k + 1; j < r.ele.length; j++) { var dd = r.ele[j] - ref2; if (dd > 3) { ascLeft += dd; ref2 = r.ele[j]; } else if (dd < -3) ref2 = r.ele[j]; }
    sheet('<div class="stats" style="margin-top:0"><div class="stat"><b>' + fmtDist(remain) + '</b><span>Noch</span></div><div class="stat"><b>' + fmtTime(secs) + '</b><span>Restzeit</span></div><div class="stat"><b>' + clock(secs) + '</b><span>Ankunft</span></div></div>' +
      '<div class="prof" id="prof">' + profileSvg(r, 340, 64, false, along) + '</div>' +
      '<div class="stats four"><div class="stat w"><b>' + Math.round(r.ele[k]) + ' m</b><span>Höhe</span></div><div class="stat w"><b>' + Math.round(ascLeft) + ' m</b><span>Noch hoch</span></div><div class="stat w"><b>' + (rec ? fmtKm(rec.dist) : '0,0') + '</b><span>km gefahren</span></div><div class="stat w"><b>' + (rec ? Math.round(rec.asc) : 0) + ' m</b><span>Geklettert</span></div></div>' +
      (nav.arrived ? '<button class="go" id="stopNav">Fahrt beenden und speichern</button>' : '<button class="go sec" id="stopNav">Navigation beenden</button>'), true);
    $('stopNav').onclick = stopNav;
  }
  function freeSheet(ref) {
    $('climbBox').style.display = 'none';
    var z = ref ? Math.round(demEle(ref.lat, ref.lon)) : '-';
    var avg = rec && rec.mv > 0 ? (rec.dist / rec.mv * 3.6).toFixed(1).replace('.', ',') : '0';
    sheet('<h2>Freie Fahrt</h2><div class="sub">' + (ref ? 'Bei ' + esc(nearestPlace(ref.lat, ref.lon, true)[0]) : 'Warte auf GPS auf der Insel') + '</div>' +
      '<div class="stats"><div class="stat"><b>' + (rec ? fmtKm(rec.dist) : '0') + '</b><span>km</span></div><div class="stat"><b>' + (rec ? fmtTime(rec.mv) : '-') + '</b><span>In Bewegung</span></div><div class="stat"><b>' + avg + '</b><span>km/h Schnitt</span></div></div>' +
      '<div class="stats"><div class="stat w"><b>' + z + ' m</b><span>Höhe</span></div><div class="stat w"><b>' + (rec ? Math.round(rec.asc) : 0) + ' m</b><span>Geklettert</span></div><div class="stat w"><b>' + (rec ? Math.round(rec.max) : 0) + '</b><span>km/h Max</span></div></div>' +
      '<button class="go red" id="stopNav">Fahrt beenden und speichern</button>', true);
    $('stopNav').onclick = stopNav;
  }
  setInterval(function () { if (nav) navUpdate(); }, 5000);

  // =====================================================================
  // Wetter und Sonne
  // =====================================================================
  function compass(d) { return ['Nord', 'Nordost', 'Ost', 'Südost', 'Süd', 'Südwest', 'West', 'Nordwest'][Math.round(d / 45) % 8]; }
  function windName(d) { return d >= 15 && d <= 75 ? ' (Bura)' : d >= 105 && d <= 165 ? ' (Jugo)' : d >= 280 && d <= 330 ? ' (Maestral)' : ''; }
  var WCODE = { 0: 'Klar', 1: 'Überwiegend klar', 2: 'Teilweise bewölkt', 3: 'Bedeckt', 45: 'Nebel', 48: 'Nebel', 51: 'Nieselregen', 53: 'Nieselregen', 55: 'Nieselregen', 61: 'Leichter Regen', 63: 'Regen', 65: 'Starker Regen', 80: 'Regenschauer', 81: 'Regenschauer', 82: 'Heftige Schauer', 95: 'Gewitter', 96: 'Gewitter', 99: 'Gewitter' };
  function sunTimes(date) {
    var lat = 44.08, lon = 15.17, rad = RAD;
    var start = new Date(date.getFullYear(), 0, 0), doy = Math.floor((date - start) / 864e5);
    var g = 2 * Math.PI / 365 * (doy - 1);
    var eq = 229.18 * (0.000075 + 0.001868 * Math.cos(g) - 0.032077 * Math.sin(g) - 0.014615 * Math.cos(2 * g) - 0.040849 * Math.sin(2 * g));
    var decl = 0.006918 - 0.399912 * Math.cos(g) + 0.070257 * Math.sin(g) - 0.006758 * Math.cos(2 * g) + 0.000907 * Math.sin(2 * g);
    var ha = Math.acos(Math.cos(90.833 * rad) / (Math.cos(lat * rad) * Math.cos(decl)) - Math.tan(lat * rad) * Math.tan(decl)) / rad;
    var noonUtc = 720 - 4 * lon - eq;
    function mk(minUtc) { var d = new Date(Date.UTC(date.getFullYear(), date.getMonth(), date.getDate(), 0, 0)); return new Date(d.getTime() + minUtc * 60000); }
    return { rise: mk(noonUtc - 4 * ha), set: mk(noonUtc + 4 * ha) };
  }
  function wxHtml() {
    var st = sunTimes(new Date()), now = Date.now();
    var sun = now < st.set.getTime() ? 'Sonnenuntergang ' + hhmm(st.set) + (st.set - now < 2 * 3600e3 ? ' (in ' + fmtTime((st.set - now) / 1000) + ')' : '') : 'Sonnenaufgang ' + hhmm(sunTimes(new Date(now + 864e5)).rise);
    if (!WX) return '<div class="wx">' + svg('sun', '#ffd43b', 26) + '<div class="grow"><div>' + sun + '</div><div class="sub" style="font-size:12px">Wetter und Wind laden, sobald Internet da ist.</div></div></div>';
    return '<div class="wx">' + svg(WX.code <= 2 ? 'sun' : 'wind', '#ffd43b', 26) + '<b>' + Math.round(WX.temp) + '&deg;</b><div class="grow"><div>' + (WCODE[WX.code] || '') + ' &middot; Wind ' + Math.round(WX.wind) + ' km/h aus ' + compass(WX.dir) + windName(WX.dir) + (WX.gust > WX.wind + 10 ? ', Böen ' + Math.round(WX.gust) : '') + '</div><div class="sub" style="font-size:12px">' + sun + (WX.rain != null ? ' &middot; Regenrisiko ' + WX.rain + ' %' : '') + '</div></div>' +
      '<svg width="30" height="30" viewBox="0 0 24 24" style="transform:rotate(' + (WX.dir + 180) + 'deg)"><path d="M12 3v16M6 13l6 6 6-6" fill="none" stroke="#4fc3f7" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"/></svg></div>';
  }
  function loadWeather() {
    if (!window.fetch) return;
    fetch('https://api.open-meteo.com/v1/forecast?latitude=44.08&longitude=15.17&current=temperature_2m,wind_speed_10m,wind_direction_10m,wind_gusts_10m,weather_code&hourly=precipitation_probability&forecast_days=1&timezone=auto')
      .then(function (r) { return r.json(); })
      .then(function (j) {
        var c = j.current, h = new Date().getHours();
        WX = { temp: c.temperature_2m, wind: c.wind_speed_10m, dir: c.wind_direction_10m, gust: c.wind_gusts_10m, code: c.weather_code, rain: j.hourly && j.hourly.precipitation_probability ? Math.max.apply(null, j.hourly.precipitation_probability.slice(h, h + 4)) : null, t: Date.now() };
        computeCosts();
        if (state === 'home') drawHome();
      }).catch(function () {});
  }
  loadWeather(); setInterval(loadWeather, 30 * 60 * 1000);

  // =====================================================================
  // Suche
  // =====================================================================
  var STREETS = (function () {
    var m = {};
    W.forEach(function (w) { var nm = D.names[w[1]]; if (!nm || /^D\d+$/.test(nm)) return; var p = w[4][Math.floor(w[4].length / 2)]; (m[nm] = m[nm] || []).push(p); });
    return Object.keys(m).map(function (k) { var n = m[k][0]; return { name: k, lat: LAT[n], lon: LON[n] }; });
  })();
  var SYN = [
    [/^(essen|restaurant|konoba|hunger|abendessen|mittag)/, ['food', 'snack']], [/^(pizza|fisch|grill|burger)/, null], [/^(eis|kaffee|cafe|kuchen|baeck|back|frueh|fruh)/, ['cafe']],
    [/^(bar|bier|cocktail|drink|pub)/, ['bar']], [/^(strand|baden|schwimm|plaza|bucht)/, ['beach']], [/^(wasser|trink|brunnen|quelle)/, ['water']],
    [/^(toilet|wc|klo|dusche)/, ['wc']], [/^(supermarkt|einkauf|einkauf|laden|konzum|lebensmittel|tommy|studenac)/, ['shop']], [/^(apothek|arzt|doktor|notfall|ambulanz)/, ['health']],
    [/^(faehre|fahre|ferry|trajekt|schiff)/, ['ferry']], [/^(hotel|unterkunft|camping|zimmer|apartment|schlafen|pension)/, ['stay']], [/^(geld|bank|atm|automat|bankomat)/, ['money']],
    [/^(aussicht|blick|view)/, ['view']], [/^(berg|gipfel|hoch)/, ['peak']], [/^(kirche|festung|museum|sehensw|burg|ruine)/, ['sight']],
    [/^(rad|fahrrad|reparatur|pumpe|verleih|bike)/, ['bike']], [/^(tank|benzin)/, ['fuel']], [/^(hafen|marina|boot)/, ['port']], [/^(bus|haltestelle)/, ['bus']], [/^(park)/, ['parking']]
  ];
  // kroatische Begriffe für die Suche auf Deutsch verständlich machen
  var HR = [['tvrdava', 'festung'], ['crkva', 'kirche'], ['crkvica', 'kapelle'], ['plaza', 'strand'], ['uvala', 'bucht'], ['rt ', 'kap '], ['otok', 'insel'],
    ['luka', 'hafen'], ['lucica', 'hafen'], ['vidikovac', 'aussicht'], ['brdo', 'berg'], ['vrh', 'gipfel'], ['sveti', 'heiliger'], ['samostan', 'kloster'],
    ['groblje', 'friedhof'], ['trajekt', 'faehre'], ['ulica', 'strasse'], ['put', 'weg'], ['konoba', 'restaurant gasthaus'], ['pekara', 'baeckerei'],
    ['sladoled', 'eis'], ['ljekarna', 'apotheke'], ['mlin', 'muehle'], ['uljara', 'oelmuehle'], ['masline', 'oliven'], ['kamp', 'camping']];
  function hrWords(h) { var o = ''; HR.forEach(function (x) { if (h.indexOf(x[0]) >= 0) o += ' ' + x[1]; }); return o; }
  function doSearch() {
    var raw = $('search').value.trim(), q = norm(raw), box = $('results');
    $('clr').style.display = raw ? 'block' : 'none';
    if (!q) { box.style.display = 'none'; return; }
    var ref = myRef() || PREKO, res = [], catHit = null;
    SYN.forEach(function (s) { if (s[1] && s[0].test(q)) catHit = s[1]; });
    D.places.forEach(function (p, i) { var h = norm(p[0]); if ((h + ' ' + hrWords(h)).indexOf(q) >= 0) res.push({ k: 0, place: i, d: dist(ref.lat, ref.lon, p[1], p[2]) }); });
    D.pois.forEach(function (p, i) {
      var c = CAT[p[0]], hay = norm(p.label + ' ' + c.n + ' ' + (p[4].sub || '') + ' ' + (p[4].cu || '') + ' ' + p.village);
      hay += ' ' + hrWords(hay);
      var txt = hay.indexOf(q) >= 0;
      if (txt || (catHit && catHit.indexOf(p[0]) >= 0)) res.push({ k: norm(p[3]).indexOf(q) === 0 ? 1 : txt ? 1.5 : 2, i: i, d: dist(ref.lat, ref.lon, p[1], p[2]) });
    });
    STREETS.forEach(function (s, i) { if (norm(s.name).indexOf(q) >= 0) res.push({ k: 3, street: i, d: dist(ref.lat, ref.lon, s.lat, s.lon) }); });
    res.sort(function (a, b) { return a.k - b.k || a.d - b.d; });
    res = res.slice(0, 40);
    box.innerHTML = res.length ? res.map(function (x) {
      if (x.place != null) { var p = D.places[x.place]; return '<div class="row" data-place="' + x.place + '"><div class="ico">' + svg('pin', '#c8f53c', 18) + '</div><div class="grow"><div class="t">' + esc(p[0]) + '</div><div class="s">' + ({ village: 'Dorf', town: 'Ort', hamlet: 'Weiler', bay: 'Bucht', cape: 'Kap', peninsula: 'Halbinsel', locality: 'Flurname', island: 'Insel', islet: 'Inselchen', isolated_dwelling: 'Einzelgehöft' }[p[3]] || 'Ortsteil') + '</div></div><div class="s">' + fmtDist(x.d) + '</div></div>'; }
      if (x.street != null) { var s = STREETS[x.street]; return '<div class="row" data-street="' + x.street + '"><div class="ico">' + svg('road', '#9fb3c4', 18) + '</div><div class="grow"><div class="t">' + esc(s.name) + '</div><div class="s">Straße/Weg &middot; ' + esc(nearestPlace(s.lat, s.lon, true)[0]) + '</div></div><div class="s">' + fmtDist(x.d) + '</div></div>'; }
      return poiRow(x);
    }).join('') : '<div class="row"><div class="s">Nichts gefunden. Tipp: auch "Strand", "Eis", "Wasser" oder "Pizza" geht.</div></div>';
    box.style.display = 'block';
  }
  $('search').addEventListener('input', doSearch);
  $('search').addEventListener('focus', function () { if (this.value) doSearch(); });
  $('clr').onclick = function () { $('search').value = ''; doSearch(); $('search').focus(); };
  $('results').addEventListener('click', function (e) {
    var r = e.target.closest('.row'); if (!r) return;
    $('results').style.display = 'none'; $('search').blur(); $('search').value = ''; $('clr').style.display = 'none';
    setFollow(false);
    if (r.hasAttribute('data-place')) { var p = D.places[+r.getAttribute('data-place')]; showPoint(p[1], p[2], p[0]); view(p[1], p[2], 15); }
    else if (r.hasAttribute('data-street')) { var s = STREETS[+r.getAttribute('data-street')]; showPoint(s.lat, s.lon, s.name); view(s.lat, s.lon, 16); }
    else if (r.hasAttribute('data-poi')) { var i = +r.getAttribute('data-poi'); showPoi(i); view(D.pois[i][1], D.pois[i][2], 16); }
  });

  // =====================================================================
  // Zurück-Taste
  // =====================================================================
  window.goBack = function () {
    if (fs) { setFs(false); return true; }
    if ($('results').style.display === 'block') { $('results').style.display = 'none'; return true; }
    if (nav) {
      // versehentliches Beenden verhindern: zweimal druecken
      if (Date.now() - (window.__backT || 0) < 2500) { stopNav(); return true; }
      window.__backT = Date.now(); toast('Nochmal Zurück drücken, um die Fahrt zu beenden'); return true;
    }
    if (state === 'pickVia') { drawRouteSheet(); state = 'route'; return true; }
    if ((state === 'route' || state === 'choose') && plan && plan.opt.tour) { var t = tourTab; clearRoute(true); showTours(t); return true; }
    if (state === 'ride') { clearRoute(true); showMenu(); return true; }
    if (state !== 'home') { clearRoute(); return true; }
    return false;
  };

  drawChips(); updLayers(); drawHome();
  setTimeout(fitIsland, 50);
  window.__show = showPoi;
  window.__ugljan = { map: map, STYLES: STYLES, routeVia: routeVia, nearestNode: nearestNode, S: S, computeCosts: computeCosts, findSpec: findSpec, makeLoops: makeLoops, officialRoutes: officialRoutes, BIKES: BIKES };
})();
