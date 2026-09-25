// Weltkarte auf Canvas 2D – komplett selbst gerendert aus den eigenen Länder-
// Polygonen (kein Foto). Landflächen und Grenzen stammen aus derselben Geometrie,
// daher gibt es keinerlei Versatz. Länder werden nach Klima eingefärbt
// (grün/gelb/hell). Wasser ist alles außerhalb der Länder – Schiffe fahren daher
// garantiert nur auf Wasser, Flugzeuge überall. Driftende Wolken beleben die Karte.
import { VEHICLES } from '../generated/vehicles.js';
import { toLocal } from '../device.js';

const clamp = (x, a, b) => Math.max(a, Math.min(b, x));
// Saubere equirektangulare Projektion (2:1). Voller Bereich mit etwas Rand oben/
// unten, damit Grönland/Südspitzen Platz haben.
const CAL = { aspect: 2.0, latTop: 84, latBot: -60 };
function latToFrac(lat) { return (CAL.latTop - lat) / (CAL.latTop - CAL.latBot); }

// Infektionspunkte: jede Bevölkerungsgruppe hat ihre eigene Punktfarbe.
// Code 0 = kein Punkt. Reihenfolge = Zeichenpriorität innerhalb eines Landes.
export const DOT_TYPES = [
  null,
  { key: 'xeno', label: 'Kristallisiert', rgb: [110, 215, 255] },
  { key: 'dead', label: 'Tot', rgb: [72, 8, 12] },
  { key: 'zombie', label: 'Zombies', rgb: [95, 225, 60] },
  { key: 'xmon', label: 'Kristallwesen', rgb: [175, 245, 255] },
  { key: 'vampire', label: 'Vampire', rgb: [255, 45, 150] },
  { key: 'ape', label: 'Affen', rgb: [255, 150, 30] },
  { key: 'control', label: 'Kontrolliert', rgb: [175, 95, 255] },
  { key: 'inf', label: 'Infiziert', rgb: [236, 30, 26] },
];
const DOT_CODE = Object.fromEntries(DOT_TYPES.map((d, i) => [d ? d.key : 'none', i]));
export const DOT_RGBA = (key, a = 1) => { const d = DOT_TYPES[DOT_CODE[key]]; return `rgba(${d.rgb.join(',')},${a})`; };
// Handys: etwas geringere Auflösung der Punkt-Ebenen (Speicher)
const IS_COARSE = typeof window !== 'undefined' && window.matchMedia && window.matchMedia('(pointer: coarse)').matches;
const DOT_SPACING = 3;          // Abstand der Punkte in Basis-Pixeln
const DOT_R = DOT_SPACING * 0.95; // Radius: Punkte überlappen -> volles Land wirkt komplett gefärbt

export class WorldMap {
  constructor(canvas, world) {
    this.canvas = canvas;
    this.ctx = canvas.getContext('2d');
    this.world = world;
    this.byIso = {};
    for (const c of world.countries) this.byIso[c.iso] = c;
    this.view = { x: 0, y: 0, scale: 1 };
    this.hoverIso = null;
    this.selectedIso = null;
    this.mode = 'play';
    this.onPick = null;
    this.onBubble = null;
    this.eng = null;
    this.bubbles = [];
    this.dpr = Math.min(window.devicePixelRatio || 1, 2);
    this.ready = true;
    this.planeImg = new Image(); this.planeImg.src = VEHICLES.plane;
    this.shipImg = new Image(); this.shipImg.src = VEHICLES.ship;
    this.astImg = new Image(); if (VEHICLES.asteroid) this.astImg.src = VEHICLES.asteroid;
    this._landColors = {};
    for (const c of world.countries) this._landColors[c.iso] = this._climateColor(c);
    this.popDots = [];      // gerade erscheinende Punkte (Animation)
    this._time = 0;
    this._buildDotSprites();
    this._buildCloudTile();
    this.asteroid = null;
    this._infDirty = true;
    this._lastInfDay = -1;
    this._bindEvents();
    this.resize();
  }

  // ---------- Projektion ----------
  proj(lon, lat) {
    return [(lon + 180) / 360 * this.baseW, latToFrac(lat) * this.baseH];
  }

  // ---------- Infektionspunkte: Positionen je Land ----------
  // Dichtes, leicht verwackeltes Raster über ALLE Landesteile (auch Alaska,
  // Französisch-Guayana, Kaliningrad …). Reihenfolge: von einem Ausbruchsherd
  // nach außen, mit Streuung – so wächst die Infektion sichtbar.
  _buildDots() {
    const S = DOT_SPACING;
    this.dots = {};
    const mask = document.createElement('canvas');
    const mx = mask.getContext('2d', { willReadFrequently: true });
    for (const c of this.world.countries) {
      const bb = this.bbox[c.iso];
      const w = Math.max(1, Math.ceil((bb[2] - bb[0]) / S) + 1), h = Math.max(1, Math.ceil((bb[3] - bb[1]) / S) + 1);
      mask.width = w; mask.height = h;
      mx.setTransform(1 / S, 0, 0, 1 / S, -bb[0] / S, -bb[1] / S);
      mx.fillStyle = '#fff'; mx.fill(this.paths[c.iso]);
      const data = mx.getImageData(0, 0, w, h).data;
      const rng = mulberry(hashStr(c.iso + 'dots'));
      const xs = [], ys = [];
      for (let j = 0; j < h; j++) for (let i = 0; i < w; i++) {
        if (data[(j * w + i) * 4 + 3] < 90) continue;
        xs.push(bb[0] + (i + 0.5 + (rng() - 0.5) * 0.7) * S);
        ys.push(bb[1] + (j + 0.5 + (rng() - 0.5) * 0.7) * S);
      }
      // Kleinststaaten/Inseln: ein paar Punkte direkt im Polygon
      for (let k = 0; xs.length < 3 && k < 400; k++) {
        const x = bb[0] + rng() * (bb[2] - bb[0]), y = bb[1] + rng() * (bb[3] - bb[1]);
        if (this._inCountry(c.iso, x, y)) { xs.push(x); ys.push(y); }
      }
      if (!xs.length) { const [x, y] = this.proj(c.lon, c.lat); xs.push(x); ys.push(y); }
      const d = { n: xs.length, x: new Float32Array(xs), y: new Float32Array(ys), state: new Uint8Array(xs.length) };
      this.dots[c.iso] = d;
      const seed = (rng() * d.n) | 0;
      this._orderDots(c.iso, d.x[seed], d.y[seed]);
    }
    this._dotsReset = true;
  }

  _orderDots(iso, sx, sy) {
    const d = this.dots[iso]; if (!d) return;
    const rng = mulberry(hashStr(iso + 'order'));
    // zweiter, schwächerer Herd bei großen Ländern
    const k2 = d.n > 300 ? (rng() * d.n) | 0 : -1;
    const idx = Array.from({ length: d.n }, (_, i) => i);
    let maxD = 1;
    const dist = new Float32Array(d.n);
    for (let i = 0; i < d.n; i++) {
      let v = Math.hypot(d.x[i] - sx, d.y[i] - sy);
      if (k2 >= 0) v = Math.min(v, Math.hypot(d.x[i] - d.x[k2], d.y[i] - d.y[k2]) * 1.35);
      dist[i] = v; if (v > maxD) maxD = v;
    }
    const key = new Float32Array(d.n);
    for (let i = 0; i < d.n; i++) key[i] = dist[i] / maxD * 0.62 + rng() * 0.38;
    idx.sort((a, b) => key[a] - key[b]);
    d.x = Float32Array.from(idx, (i) => d.x[i]);
    d.y = Float32Array.from(idx, (i) => d.y[i]);
    // Erster Punkt exakt am Herd
    let best = 0, bd = 1e9;
    for (let i = 0; i < d.n; i++) { const v = Math.hypot(d.x[i] - sx, d.y[i] - sy); if (v < bd) { bd = v; best = i; } }
    [d.x[0], d.x[best]] = [d.x[best], d.x[0]]; [d.y[0], d.y[best]] = [d.y[best], d.y[0]];
  }

  _buildDotSprites() {
    this.dotSprites = DOT_TYPES.map((t) => {
      if (!t) return null;
      const S = 24, c = S / 2;
      const cv = document.createElement('canvas'); cv.width = cv.height = S;
      const x = cv.getContext('2d');
      const [r, g, b] = t.rgb;
      const lite = (v) => Math.min(255, v + 38), dark = (v) => Math.round(v * 0.8);
      const gr = x.createRadialGradient(c * 0.9, c * 0.88, 0, c, c, c);
      gr.addColorStop(0, `rgba(${lite(r)},${lite(g)},${lite(b)},1)`);
      gr.addColorStop(0.35, `rgba(${r},${g},${b},1)`);
      gr.addColorStop(0.78, `rgba(${dark(r)},${dark(g)},${dark(b)},1)`);
      gr.addColorStop(1, `rgba(${dark(r)},${dark(g)},${dark(b)},0)`);
      x.fillStyle = gr; x.beginPath(); x.arc(c, c, c, 0, 7); x.fill();
      return cv;
    });
  }

  _inRings(rings, x, y) {
    let inside = false;
    for (const ring of rings) {
      for (let i = 0, j = ring.length - 1; i < ring.length; j = i++) {
        const xi = ring[i][0], yi = ring[i][1], xj = ring[j][0], yj = ring[j][1];
        if (((yi > y) !== (yj > y)) && (x < (xj - xi) * (y - yi) / (yj - yi) + xi)) inside = !inside;
      }
    }
    return inside;
  }

  // ---------- Wolken-Kachel (kleine, weiche Wölkchen, gut durchsichtig) ----------
  _buildCloudTile() {
    const w = 1024, h = 512;
    const cv = document.createElement('canvas'); cv.width = w; cv.height = h;
    const x = cv.getContext('2d');
    x.clearRect(0, 0, w, h);
    const rng = mulberry(9182);
    // wenige, kleine Wolkencluster aus mehreren weichen Ballen
    for (let i = 0; i < 26; i++) {
      const cx = rng() * w, cy = rng() * h;
      const puffs = 3 + (rng() * 4 | 0);
      for (let j = 0; j < puffs; j++) {
        const px = cx + (rng() - 0.5) * 70, py = cy + (rng() - 0.5) * 40;
        const r = 14 + rng() * 34;
        const a = 0.05 + rng() * 0.08;
        for (const ox of [0, cx < 120 ? w : 0, cx > w - 120 ? -w : 0]) {
          if (ox === 0 && (cx < 120 || cx > w - 120) && false) continue;
          const g = x.createRadialGradient(px + ox, py, 0, px + ox, py, r);
          g.addColorStop(0, `rgba(240,244,255,${a})`); g.addColorStop(0.6, `rgba(240,244,255,${a * 0.5})`); g.addColorStop(1, 'rgba(240,244,255,0)');
          x.fillStyle = g; x.beginPath(); x.arc(px + ox, py, r, 0, 7); x.fill();
        }
      }
    }
    this.cloudTile = cv;
    // Land-Textur (feine Struktur für mehr Realismus)
    const nt = document.createElement('canvas'); nt.width = 256; nt.height = 256;
    const nx = nt.getContext('2d'); const rn = mulberry(4711);
    for (let i = 0; i < 2600; i++) {
      const v = rn(); const shade = v < 0.5 ? 0 : 255; const al = 0.03 + rn() * 0.05;
      nx.fillStyle = `rgba(${shade},${shade},${shade},${al})`;
      const s = 1 + rn() * 2; nx.fillRect(rn() * 256, rn() * 256, s, s);
    }
    this.noiseTile = nt;
  }

  // ---------- Events (Pan/Zoom/Klick) ----------
  // ---------- Eingabe: Maus, Touch und Stift über Pointer-Events ----------
  // 1 Finger: verschieben (mit Schwung) · 2 Finger: Pinch-Zoom um die Finger-
  // mitte (gleichzeitig verschieben) · Tippen: Land wählen / DNA einsammeln /
  // Ziel festlegen · Doppeltippen: hineinzoomen · Mausrad: zoomen · Maus: Hover.
  _bindEvents() {
    const cv = this.canvas;
    cv.style.touchAction = 'none';
    const ptrs = new Map();
    let g = null;
    const pos = (e) => toLocal(cv, e.clientX, e.clientY);   // berücksichtigt gedrehte Oberfläche
    const startPinch = () => {
      const [a, b] = [...ptrs.values()];
      const mid = { x: (a.x + b.x) / 2, y: (a.y + b.y) / 2 };
      g.mode = 'pinch'; g.pinched = true;
      g.d0 = Math.max(10, Math.hypot(a.x - b.x, a.y - b.y)); g.s0 = this.view.scale;
      g.anchor = this._toWorld(mid.x, mid.y);
    };
    cv.addEventListener('pointerdown', (e) => {
      if (e.pointerType === 'mouse' && e.button !== 0) return;
      try { cv.setPointerCapture(e.pointerId); } catch (_) { /* ältere Browser */ }
      const p = pos(e); ptrs.set(e.pointerId, p);
      this._inertia = null; this._zoomAnim = null;
      this.touchMode = e.pointerType !== 'mouse';
      if (this.touchMode) this.hoverIso = null;
      if (ptrs.size === 1) g = { mode: 'pan', start: p, last: p, t0: performance.now(), lt: performance.now(), moved: 0, vx: 0, vy: 0, pinched: false, touch: this.touchMode };
      else if (ptrs.size === 2 && g) startPinch();
    });
    cv.addEventListener('pointermove', (e) => {
      const p = pos(e);
      if (e.pointerType === 'mouse') this.mouse = p;
      if (!ptrs.has(e.pointerId)) {
        // reines Überfahren mit der Maus
        if (e.pointerType === 'mouse' && !g) { this.hoverIso = this._hit(p.x, p.y); cv.style.cursor = this.targetMode ? 'crosshair' : this.hoverIso ? 'pointer' : 'grab'; }
        return;
      }
      ptrs.set(e.pointerId, p);
      if (!g) return;
      if (g.mode === 'pinch' && ptrs.size >= 2) {
        const [a, b] = [...ptrs.values()];
        const mid = { x: (a.x + b.x) / 2, y: (a.y + b.y) / 2 };
        const d = Math.hypot(a.x - b.x, a.y - b.y);
        this.view.scale = clamp(g.s0 * d / g.d0, 1, 8);
        this.view.x = mid.x - g.anchor.x * this.view.scale;
        this.view.y = mid.y - g.anchor.y * this.view.scale;
        this._clampView();
        g.moved += 20;
        return;
      }
      if (g.mode === 'pan') {
        const dx = p.x - g.last.x, dy = p.y - g.last.y;
        g.moved += Math.abs(dx) + Math.abs(dy);
        const now = performance.now(), dt = Math.max(1, now - g.lt) / 1000;
        g.vx = g.vx * 0.6 + (dx / dt) * 0.4; g.vy = g.vy * 0.6 + (dy / dt) * 0.4;
        g.last = p; g.lt = now;
        this.view.x += dx; this.view.y += dy; this._clampView();
        if (g.moved > 4) cv.style.cursor = 'grabbing';
      }
    });
    const end = (e) => {
      if (!ptrs.has(e.pointerId)) return;
      ptrs.delete(e.pointerId);
      if (!g) return;
      if (ptrs.size === 1) {            // ein Finger bleibt nach dem Pinch liegen -> weiter verschieben
        const p = [...ptrs.values()][0];
        g.mode = 'pan'; g.last = p; g.lt = performance.now(); g.vx = g.vy = 0;
        return;
      }
      if (ptrs.size > 1) { startPinch(); return; }
      const up = pos(e);
      const tol = g.touch ? 12 : 6;
      // Tippen = Finger kaum bewegt (Dauer egal – das Handy kann kurz ausgelastet sein)
      const isTap = !g.pinched && g.moved < tol && e.type !== 'pointercancel';
      if (isTap) this._tap(up, g.touch, e.timeStamp);
      else if (g.mode === 'pan' && !g.pinched && performance.now() - g.lt < 80 && Math.hypot(g.vx, g.vy) > 120) {
        this._inertia = { vx: clamp(g.vx, -3000, 3000), vy: clamp(g.vy, -3000, 3000) };
      }
      cv.style.cursor = this.targetMode ? 'crosshair' : 'grab';
      g = null;
    };
    cv.addEventListener('pointerup', end);
    cv.addEventListener('pointercancel', end);
    cv.addEventListener('pointerleave', (e) => { if (e.pointerType === 'mouse' && !ptrs.size) this.hoverIso = null; });
    cv.addEventListener('wheel', (e) => {
      e.preventDefault();
      const p = pos(e);
      this.zoomAt(p.x, p.y, this.view.scale * (e.deltaY < 0 ? 1.15 : 0.87), false);
    }, { passive: false });
    // iOS Safari: Seiten-Zoom-Gesten unterdrücken, die Karte zoomt selbst
    for (const ev of ['gesturestart', 'gesturechange']) cv.addEventListener(ev, (e) => e.preventDefault());
  }

  // Tippen/Klicken: Doppeltipp zoomt (Touch), sonst normaler Klick
  _tap(p, touch, stamp) {
    const now = stamp || performance.now();
    const last = this._lastTap;
    this._lastTap = { t: now, x: p.x, y: p.y };
    if (!touch || this.targetMode) { this._click(p, touch); return; }
    if (last && now - last.t < 330 && Math.hypot(p.x - last.x, p.y - last.y) < 40) {
      // Doppeltipp: zoomen statt auswählen
      clearTimeout(this._tapTimer); this._tapTimer = null;
      this._lastTap = null;
      this.zoomAt(p.x, p.y, this.view.scale >= 7.5 ? 1 : this.view.scale * 2.2, true);
      return;
    }
    // DNA-Blasen sofort einsammeln, Länder erst nach kurzer Wartezeit wählen
    if (this._collectBubbleAt(p, true)) { this._lastTap = null; return; }
    clearTimeout(this._tapTimer);
    this._tapTimer = setTimeout(() => { this._tapTimer = null; this._click(p, true); }, 260);
  }

  // Zoom um einen Bildschirmpunkt, optional weich animiert
  zoomAt(sx, sy, scale, animate = true) {
    scale = clamp(scale, 1, 8);
    if (!animate) {
      const before = this._toWorld(sx, sy);
      this.view.scale = scale;
      this.view.x = sx - before.x * scale; this.view.y = sy - before.y * scale;
      this._clampView();
      return;
    }
    this._zoomAnim = { sx, sy, from: this.view.scale, to: scale, anchor: this._toWorld(sx, sy), t: 0 };
  }

  // Ansicht auf ein Land zentrieren (Zoom bleibt)
  centerOn(iso) {
    const c = this.byIso[iso]; if (!c) return;
    const [x, y] = this.proj(c.lon, c.lat);
    this.view.x = this.cw / 2 - x * this.view.scale; this.view.y = this.ch / 2 - y * this.view.scale;
    this._clampView();
  }

  _updateCamera(dt) {
    if (this._zoomAnim) {
      const z = this._zoomAnim;
      z.t = Math.min(1, z.t + dt / 0.28);
      const e = 1 - Math.pow(1 - z.t, 3);
      this.view.scale = z.from + (z.to - z.from) * e;
      this.view.x = z.sx - z.anchor.x * this.view.scale; this.view.y = z.sy - z.anchor.y * this.view.scale;
      this._clampView();
      if (z.t >= 1) this._zoomAnim = null;
    }
    if (this._inertia) {
      const v = this._inertia;
      const ox = this.view.x, oy = this.view.y;
      this.view.x += v.vx * dt; this.view.y += v.vy * dt; this._clampView();
      if (this.view.x === ox) v.vx = 0;
      if (this.view.y === oy) v.vy = 0;
      const k = Math.exp(-dt * 4.5); v.vx *= k; v.vy *= k;
      if (Math.hypot(v.vx, v.vy) < 12) this._inertia = null;
    }
  }

  _toWorld(sx, sy) { return { x: (sx - this.view.x) / this.view.scale, y: (sy - this.view.y) / this.view.scale }; }
  _clampView() {
    const vw = this.cw, vh = this.ch;
    const sw = this.baseW * this.view.scale, sh = this.baseH * this.view.scale;
    this.view.x = clamp(this.view.x, vw - sw, 0);
    this.view.y = clamp(this.view.y, vh - sh, 0);
    if (sw <= vw) this.view.x = (vw - sw) / 2;
    if (sh <= vh) this.view.y = (vh - sh) / 2;
  }

  // DNA-Blase an einer Bildschirmposition einsammeln (Handy: Fingerkuppen-Radius)
  _collectBubbleAt(m, touch) {
    const wp = this._toWorld(m.x, m.y);
    const rad = (touch ? 30 : 16) / this.view.scale;
    let best = -1, bd = 1e9;
    for (let i = this.bubbles.length - 1; i >= 0; i--) {
      const b = this.bubbles[i];
      const [bx, by] = this.proj(b.x, b.y);
      const d = Math.hypot(bx - wp.x, by - wp.y - (b.age * 2));
      if (d < rad && d < bd) { bd = d; best = i; }
    }
    if (best < 0) return false;
    const b = this.bubbles[best]; if (this.onBubble) this.onBubble(b); this.bubbles.splice(best, 1);
    return true;
  }

  _click(m, touch = false) {
    if (this._collectBubbleAt(m, touch)) return;
    const iso = this._hit(m.x, m.y);
    if (this.targetMode && iso) { const cb = this.targetMode; this.targetMode = null; this.canvas.style.cursor = 'grab'; cb(iso); return; }
    if (iso && this.onPick) { this.selectedIso = iso; this.onPick(iso); }
    else if (!iso && this.onEmpty) this.onEmpty();
  }

  // Treffertest über die echte Landesform (alle Landesteile, egal wie weit
  // sie vom Mittelpunkt entfernt sind – z.B. Alaska oder Französisch-Guayana)
  _hit(sx, sy) {
    const w = this._toWorld(sx, sy);
    for (const c of this.world.countries) {
      const bb = this.bbox[c.iso];
      if (w.x < bb[0] || w.x > bb[2] || w.y < bb[1] || w.y > bb[3]) continue;
      if (this._inCountry(c.iso, w.x, w.y)) return c.iso;
    }
    // Länder, die auf dem Bildschirm winzig erscheinen (Inseln): großzügiger
    // Fangradius – gemessen in Bildschirm-Pixeln, damit es bei jeder Größe passt
    const sc = this.view.scale, tol = 10 / sc;
    let ni = null, nd = 1e9;
    for (const c of this.world.countries) {
      const bb = this.bbox[c.iso];
      if (Math.max(bb[2] - bb[0], bb[3] - bb[1]) * sc > 16) continue;
      const dx = Math.max(bb[0] - w.x, 0, w.x - bb[2]), dy = Math.max(bb[1] - w.y, 0, w.y - bb[3]);
      const d = Math.hypot(dx, dy);
      if (d < nd) { nd = d; ni = c.iso; }
    }
    return nd < tol ? ni : null;
  }

  _inCountry(iso, x, y) {
    const rings = this.projRings && this.projRings[iso]; if (!rings) return false;
    let inside = false;
    for (const ring of rings) {
      for (let i = 0, j = ring.length - 1; i < ring.length; j = i++) {
        const xi = ring[i][0], yi = ring[i][1], xj = ring[j][0], yj = ring[j][1];
        if (((yi > y) !== (yj > y)) && (x < (xj - xi) * (y - yi) / (yj - yi) + xi)) inside = !inside;
      }
    }
    return inside;
  }

  // ---------- Resize / Pfade ----------
  resize() {
    const parent = this.canvas.parentElement;
    // Layout-Größe (nicht die gedrehte Bildschirmbox)
    this.cw = (parent && parent.clientWidth) || 1280; this.ch = (parent && parent.clientHeight) || 720;
    this.canvas.width = this.cw * this.dpr; this.canvas.height = this.ch * this.dpr;
    this.canvas.style.width = this.cw + 'px'; this.canvas.style.height = this.ch + 'px';
    // Bild füllt die Ansicht, Seitenverhältnis 16:9 beibehalten
    this.baseW = Math.max(this.cw, this.ch * CAL.aspect);
    this.baseH = this.baseW / CAL.aspect;
    if (this.baseH < this.ch) { this.baseH = this.ch; this.baseW = this.baseH * CAL.aspect; }
    this._clampView();
    this._buildPaths();
    this._buildDots();
    if (this.eng) { const c = this.byIso[this.eng.startCountry]; if (c) { const [x, y] = this.proj(c.lon, c.lat); this._orderDots(c.iso, x, y); } }
    this.popDots = [];
    this._infCanvas = document.createElement('canvas');
    this._infRes = IS_COARSE ? 1.5 : 2; // höhere Auflösung für scharfe Infektionspunkte beim Zoom
    this._infCanvas.width = Math.round(this.baseW * this._infRes);
    this._infCanvas.height = Math.round(this.baseH * this._infRes);
    this._ovCanvas = document.createElement('canvas');
    this._ovCanvas.width = this._infCanvas.width; this._ovCanvas.height = this._infCanvas.height;
    this._dotsReset = true;
    this._infDirty = true;
    this._renderBase();
    this._buildLandMask();
    this._sanitizeSea();
  }

  // ---------- Eigene Kartengrafik (aus den Länder-Polygonen) ----------
  _climateColor(c) {
    // Basisfarbe nach Klima + leichte länderspezifische Variation
    let h, s, l;
    if (c.temp < -4 || c.iso === 'GRL') { h = 190; s = 12; l = 82; }        // Eis/sehr kalt: fast weiß
    else if (c.climate === 'kalt') { h = 120; s = 16; l = 55; }              // kühl: blasses Grün
    else if (c.climate === 'arid') { h = 46; s = 55; l = 62; }               // Wüste: sandgelb
    else if (c.climate === 'heiß') { h = 52; s = 45; l = 55; }               // heiß-trocken: goldbraun
    else if (c.climate === 'tropisch') { h = 108; s = 45; l = 38; }          // tropisch: sattes Grün
    else { h = 100, s = 38, l = 46; }                                        // gemäßigt: Grün
    const j = (hashStr(c.iso) % 1000) / 1000 - 0.5;
    h += j * 12; l += j * 8; s += j * 8;
    return { h, s, l };
  }

  _hsl(o, dl = 0, ds = 0) { return `hsl(${o.h.toFixed(0)},${clamp(o.s + ds, 0, 100).toFixed(0)}%,${clamp(o.l + dl, 0, 100).toFixed(0)}%)`; }

  _renderBase() {
    const resB = 1.6;
    const bc = this._baseCanvas = document.createElement('canvas');
    bc.width = Math.round(this.baseW * resB); bc.height = Math.round(this.baseH * resB);
    const x = bc.getContext('2d');
    x.setTransform(resB, 0, 0, resB, 0, 0);
    // Ozean-Verlauf
    const g = x.createLinearGradient(0, 0, 0, this.baseH);
    g.addColorStop(0, '#0b2036'); g.addColorStop(0.5, '#0c2f4e'); g.addColorStop(1, '#08182b');
    x.fillStyle = g; x.fillRect(0, 0, this.baseW, this.baseH);
    // subtile Ozean-Struktur
    if (this.cloudTile) { x.globalAlpha = 0.05; for (let ox = 0; ox < this.baseW; ox += 1024) x.drawImage(this.cloudTile, ox, this.baseH * 0.1, 1024, this.baseH * 0.8); x.globalAlpha = 1; }
    // Länder füllen
    for (const c of this.world.countries) {
      const col = this._landColors[c.iso];
      const path = this.paths[c.iso];
      // sanfter Küstenglanz
      x.save();
      x.shadowColor = 'rgba(120,180,220,0.5)'; x.shadowBlur = 4;
      x.fillStyle = this._hsl(col); x.fill(path);
      x.restore();
    }
    // Terrain-Schattierung (oben heller) + Grenzen
    for (const c of this.world.countries) {
      const col = this._landColors[c.iso];
      const path = this.paths[c.iso];
      const [, cy] = this.proj(c.lon, c.lat);
      x.save(); x.clip(path);
      const lg = x.createLinearGradient(0, cy - 60, 0, cy + 60);
      lg.addColorStop(0, this._hsl(col, 10)); lg.addColorStop(1, this._hsl(col, -10));
      x.globalAlpha = 0.5; x.fillStyle = lg; x.fillRect(0, 0, this.baseW, this.baseH); x.globalAlpha = 1;
      x.restore();
    }
    // feine Land-Textur (Relief) über alle Landflächen
    if (this.noiseTile) {
      x.save();
      x.beginPath();
      for (const c of this.world.countries) for (const ring of this.world.geo[c.iso]) {
        const pts = this._ringPoints(ring);
        pts.forEach((q, i) => i ? x.lineTo(q[0], q[1]) : x.moveTo(q[0], q[1])); x.closePath();
      }
      x.clip();
      x.globalAlpha = 0.5;
      for (let oy = 0; oy < this.baseH; oy += 256) for (let ox = 0; ox < this.baseW; ox += 256) x.drawImage(this.noiseTile, ox, oy, 256, 256);
      x.globalAlpha = 1;
      x.restore();
    }
    // Ländergrenzen (dünn)
    x.lineJoin = 'round';
    for (const c of this.world.countries) {
      x.lineWidth = 0.6; x.strokeStyle = 'rgba(20,30,25,0.5)'; x.stroke(this.paths[c.iso]);
    }
  }

  _buildLandMask() {
    const MW = 700, MH = Math.round(700 / CAL.aspect);
    const cv = document.createElement('canvas'); cv.width = MW; cv.height = MH;
    const x = cv.getContext('2d', { willReadFrequently: true });
    x.fillStyle = '#000'; x.fillRect(0, 0, MW, MH);
    x.save(); x.scale(MW / this.baseW, MH / this.baseH);
    x.fillStyle = '#fff';
    for (const c of this.world.countries) x.fill(this.paths[c.iso]);
    x.restore();
    let data; try { data = x.getImageData(0, 0, MW, MH).data; } catch (e) { this.water = null; return; }
    const mask = new Uint8Array(MW * MH);
    for (let i = 0; i < MW * MH; i++) mask[i] = data[i * 4] < 128 ? 1 : 0; // Wasser = schwarz
    this.water = { mask, MW, MH };
  }

  // Ring in Bildschirmpunkte projizieren; Längengrad "entrollen", damit Länder,
  // die die Datumsgrenze überschreiten (Alaska/Russland/Fidschi), keine Linie
  // quer über die Karte ziehen.
  _ringPoints(ring) {
    const out = [];
    let prevLon = ring[0][0];
    let acc = ring[0][0];
    for (let i = 0; i < ring.length; i++) {
      let lon = ring[i][0];
      while (lon - prevLon > 180) lon -= 360;
      while (lon - prevLon < -180) lon += 360;
      prevLon = lon;
      out.push(this.proj(lon, ring[i][1]));
    }
    return out;
  }

  _buildPaths() {
    this.paths = {};
    this.projRings = {};   // projizierte Ringe (für Treffertest, identisch zur Zeichnung)
    this.bbox = {};
    for (const c of this.world.countries) {
      const p = new Path2D();
      const rings = [];
      const bb = [1e9, 1e9, -1e9, -1e9];
      for (const ring of this.world.geo[c.iso]) {
        const pts = this._ringPoints(ring);
        for (let i = 0; i < pts.length; i++) {
          if (i === 0) p.moveTo(pts[i][0], pts[i][1]); else p.lineTo(pts[i][0], pts[i][1]);
          bb[0] = Math.min(bb[0], pts[i][0]); bb[1] = Math.min(bb[1], pts[i][1]);
          bb[2] = Math.max(bb[2], pts[i][0]); bb[3] = Math.max(bb[3], pts[i][1]);
        }
        p.closePath();
        rings.push(pts);
      }
      this.paths[c.iso] = p;
      this.projRings[c.iso] = rings;
      this.bbox[c.iso] = bb;
    }
  }

  setEngine(eng) {
    this.eng = eng; this._infDirty = true; this._lastInfDay = -1; this._sanitizeSea();
    this.popDots = []; this.agents = [];
    // Ausbruch im Startland beginnt am Startmarker
    if (eng && this.dots) {
      const c = this.byIso[eng.startCountry];
      if (c) { const [x, y] = this.proj(c.lon, c.lat); this._orderDots(c.iso, x, y); }
    }
    this._dotsReset = true;
  }

  // ---------- Wasser (alles außerhalb der Länder) ----------
  isWater(lon, lat) {
    if (!this.water) return true;
    const fx = (lon + 180) / 360, fy = latToFrac(lat);
    const ix = Math.round(fx * this.water.MW), iy = Math.round(fy * this.water.MH);
    if (ix < 0 || iy < 0 || ix >= this.water.MW || iy >= this.water.MH) return true;
    return this.water.mask[iy * this.water.MW + ix] === 1;
  }

  // Küstenpunkt aufs nächste Wasser schieben (Häfen liegen so am offenen Meer)
  _snapToWater(lon, lat) {
    if (this.isWater(lon, lat)) return [lon, lat];
    for (let r = 1; r <= 8; r++) {
      for (let a = 0; a < 16; a++) {
        const ang = a / 16 * Math.PI * 2;
        const nl = lon + Math.cos(ang) * r * 0.8, nt = lat + Math.sin(ang) * r * 0.8;
        if (this.isWater(nl, nt)) return [nl, nt];
      }
    }
    return [lon, lat];
  }

  // Häfen ans Wasser schieben und die Seewege der Engine auf reine Wasserrouten
  // reduzieren (damit sichtbare Schiffe niemals über Land fahren).
  _sanitizeSea() {
    if (!this.water) return;
    // Häfen nur einmal ans Wasser schieben
    if (!this._portsSnapped) {
      this._portsSnapped = true;
      for (const c of this.world.countries) {
        if (c.port && c.portPos) c.portPos = this._snapToWater(c.portPos[0], c.portPos[1]);
      }
    }
    // isWaterFn bei jedem (neuen) Spiel setzen, damit Schiffe stets geprüft werden
    if (this.eng) this.eng.isWaterFn = (lon, lat) => this.isWater(lon, lat);
  }

  focusCountry(iso, scale = 3) {
    const c = this.byIso[iso]; if (!c) return;
    const [x, y] = this.proj(c.lon, c.lat);
    this.view.scale = scale;
    this.view.x = this.cw / 2 - x * scale; this.view.y = this.ch / 2 - y * scale;
    this._clampView();
  }

  // Zielauswahl für gerichtete Sonderfähigkeiten (z.B. Neurax-Kontrolle)
  requestTarget(cb) { this.targetMode = cb; this.canvas.style.cursor = 'crosshair'; }
  cancelTarget() { this.targetMode = null; this.canvas.style.cursor = 'grab'; }

  // ---------- Infektionsschicht: Punkte ----------
  // Pro Land wird aus der Simulation berechnet, welcher Punkt welche Farbe hat.
  // Anteil sichtbarer Punkte = Wurzel des betroffenen Bevölkerungsanteils: schon
  // der erste Infizierte zeigt einen Punkt, bei 100 % ist das Land voll.
  // Neue Punkte "ploppen" animiert auf; nur Änderungen werden gezeichnet.
  _desiredCodes(st, d, out) {
    const pop = Math.max(1, st.pop);
    const ctrl = Math.min(st.controlled || 0, st.infected);
    // Reihenfolge = DOT_TYPES-Codes 2..8 (Tote zuerst: dort begann der Ausbruch)
    const w = [0, 0, st.dead, st.zombies || 0, st.xmon || 0, st.vampires || 0, st.apes || 0, ctrl, st.infected - ctrl];
    let A = 0; for (let k = 2; k < w.length; k++) { w[k] = Math.max(0, w[k]); A += w[k]; }
    // Vampire sind nur wenige – trotzdem sichtbar machen (Anteil wächst logarithmisch)
    const v = w[5];
    if (v >= 1) { const add = Math.max(0, A * Math.min(0.25, 0.025 * Math.log10(1 + v)) - v); w[5] += add; A += add; }
    const nX = Math.min(d.n, Math.round(d.n * (st.xeno || 0)));
    const rest = d.n - nX;
    const nP = A >= 0.5 ? Math.min(rest, Math.max(1, Math.round(rest * Math.sqrt(Math.min(1, A / pop))))) : 0;
    // Zielanzahl je Gruppe; kleine Rückgänge werden ignoriert ("klebrig"), damit
    // Grenzen zwischen den Farben nicht täglich hin- und herspringen
    const cnt = (d.cnt ||= new Int32Array(DOT_TYPES.length));
    const tol = Math.max(2, d.n * 0.04);
    let acc = 0, prev = 0, sum = 0;
    for (let k = 2; k < w.length; k++) {
      acc += w[k];
      const cum = A > 0 ? Math.round(nP * acc / A) : 0;
      let t = cum - prev; prev = cum;
      if (nP > 0 && t < cnt[k] && cnt[k] - t <= tol) t = cnt[k];
      if (nP === 0) t = 0;
      cnt[k] = t; sum += t;
    }
    // zu viele durch Klebrigkeit -> zuerst bei den Infizierten kürzen
    for (let k = w.length - 1; sum > rest && k >= 2; k--) { const cut = Math.min(cnt[k], sum - rest); cnt[k] -= cut; sum -= cut; }
    out.fill(0);
    for (let i = 0; i < nX; i++) out[i] = DOT_CODE.xeno;
    let from = nX;
    for (let k = 2; k < w.length; k++) { const to = from + cnt[k]; for (let i = from; i < to; i++) out[i] = k; from = to; }
  }

  // Rot (Infizierte) und Kristallboden liegen unten, alle besonderen Gruppen
  // (Tote, Zombies, Kontrollierte, Affen, Vampire, Kristallwesen) darüber
  _layerOf(code) { return code === DOT_CODE.inf || code === DOT_CODE.xeno ? this._infCanvas : this._ovCanvas; }

  _updateDots() {
    if (!this.eng || !this.dots || !this._infCanvas) return;
    const L = [this._infCanvas.getContext('2d'), this._ovCanvas.getContext('2d')];
    const ctxOf = (code) => (this._layerOf(code) === this._infCanvas ? L[0] : L[1]);
    const reset = this._dotsReset;
    if (reset) {
      for (const x of L) { x.setTransform(1, 0, 0, 1, 0, 0); x.clearRect(0, 0, this._infCanvas.width, this._infCanvas.height); }
      for (const iso in this.dots) { const d = this.dots[iso]; d.state.fill(0); if (d.cnt) d.cnt.fill(0); }
      this.popDots = [];
    }
    for (const x of L) x.setTransform(this._infRes, 0, 0, this._infRes, 0, 0);
    const animated = new Set(this.popDots.map((p) => p.iso + ':' + p.i));
    let budget = reset ? 0 : 450 - this.popDots.length;   // max. gleichzeitige Plopp-Animationen
    const now = this._time;
    for (const c of this.world.countries) {
      const d = this.dots[c.iso];
      const st = this.eng.countries[c.iso];
      if (!this._want || this._want.length < d.n) this._want = new Uint8Array(Math.max(d.n, 8192));
      const want = this._want.subarray(0, d.n);
      this._desiredCodes(st, d, want);
      // Punkte verschwinden (z.B. Heilung) oder wandern von der oberen in die
      // untere Ebene -> Land muss neu gezeichnet werden. Das passiert höchstens
      // alle paar Tage je Land; neue Punkte erscheinen dagegen sofort.
      // je Land versetzt (Phase aus dem Ländercode), damit nie alle gleichzeitig dran sind
      const allowFull = reset || (this.eng.day + (d.phase ??= hashStr(c.iso) % 6)) % 6 === 0;
      let full = false; const changed = [];
      for (let i = 0; i < d.n; i++) {
        const a = d.state[i], b = want[i];
        if (a === b) continue;
        if (b === 0 || (a !== 0 && ctxOf(a) !== ctxOf(b))) {
          if (allowFull) { full = true; break; }
          want[i] = a; continue;
        }
        changed.push(i);
      }
      if (!full && !changed.length) continue;
      for (const x of L) { x.save(); x.clip(this.paths[c.iso]); }
      if (full) {
        const bb = this.bbox[c.iso];
        for (const x of L) x.clearRect(bb[0] - 3, bb[1] - 3, bb[2] - bb[0] + 6, bb[3] - bb[1] + 6);
        for (let i = 0; i < d.n; i++) if (want[i]) this._blitDot(ctxOf(want[i]), d.x[i], d.y[i], want[i]);
        this.popDots = this.popDots.filter((p) => p.iso !== c.iso);
      } else {
        for (const i of changed) {
          const isNew = d.state[i] === 0;
          if (isNew && animated.has(c.iso + ':' + i)) continue;
          if (isNew && budget > 0) {
            // gestaffeltes Aufploppen über den Tag verteilt
            this.popDots.push({ iso: c.iso, i, t0: now + Math.random() * 0.35 });
            budget--;
          } else this._blitDot(ctxOf(want[i]), d.x[i], d.y[i], want[i]);
        }
      }
      for (const x of L) x.restore();
      d.state.set(want);
    }
    this._dotsReset = false;
    this._infDirty = false;
  }

  _blitDot(ctx, px, py, code, scale = 1) {
    const r = DOT_R * scale;
    ctx.drawImage(this.dotSprites[code], px - r, py - r, r * 2, r * 2);
  }

  // Animation: Punkt wächst mit Überschwinger und Leuchten, dann fest eingebrannt
  _drawPopDots(ctx) {
    if (!this.popDots.length) return;
    const now = this._time, DUR = 0.55;
    const done = {};
    ctx.save();
    for (const p of this.popDots) {
      const a = (now - p.t0) / DUR;
      if (a < 0) continue;
      const d = this.dots[p.iso]; const code = d.state[p.i];
      if (a >= 1 || !code) { (done[p.iso] ||= []).push(p); continue; }
      const sc = a < 0.6 ? (a / 0.6) * 1.9 : 1.9 - (a - 0.6) / 0.4 * 0.9;
      ctx.globalAlpha = Math.min(1, a * 3);
      this._blitDot(ctx, d.x[p.i], d.y[p.i], code, sc);
    }
    ctx.restore();
    const isos = Object.keys(done);
    if (!isos.length) return;
    const L = [this._infCanvas.getContext('2d'), this._ovCanvas.getContext('2d')];
    for (const x of L) x.setTransform(this._infRes, 0, 0, this._infRes, 0, 0);
    for (const iso of isos) {
      const d = this.dots[iso];
      for (const x of L) { x.save(); x.clip(this.paths[iso]); }
      for (const p of done[iso]) {
        const code = d.state[p.i];
        if (code) this._blitDot(this._layerOf(code) === this._infCanvas ? L[0] : L[1], d.x[p.i], d.y[p.i], code);
      }
      for (const x of L) x.restore();
    }
    const fin = new Set(isos.flatMap((iso) => done[iso]));
    this.popDots = this.popDots.filter((p) => !fin.has(p));
  }

  // Punktfarben, die in dieser Partie vorkommen (für die Legende der UI)
  legendKeys() {
    if (!this.eng) return [];
    const sp = this.eng.special, type = this.eng.opts.type;
    const keys = ['inf', 'dead'];
    if (sp.zombieActive || type === 'necroa') keys.push('zombie');
    if (sp.controlActive || type === 'neurax') keys.push('control');
    if (sp.apesActive || type === 'simian') keys.push('ape');
    if (sp.vampireActive || type === 'shadow') keys.push('vampire');
    if (sp.xenoActive || type === 'xenolith') keys.push('xeno', 'xmon');
    return keys;
  }

  spawnBubble(b) {
    const c = this.byIso[b.iso]; if (!c) return;
    if (this.bubbles.length > 16) this.bubbles.shift();
    this.bubbles.push({ iso: b.iso, x: c.lon + (Math.random() - 0.5) * 3, y: c.lat + (Math.random() - 0.5) * 3, age: 0, life: 9, type: b.type });
  }

  playAsteroidIntro(iso, cb) {
    const c = this.byIso[iso] || this.byIso[this.eng?.startCountry];
    const [tx, ty] = this.proj(c ? c.lon : 0, c ? c.lat : 0);
    // Start im sichtbaren Bereich (oben rechts), damit der Anflug klar sichtbar ist
    const off = 300 / Math.max(1, this.view.scale);
    this.asteroid = { t: 0, tx, ty, sx: tx + off * 0.7, sy: ty - off, cb, flash: 0, trail: [] };
  }

  update(dt) {
    this._time += dt;
    this._updateCamera(dt);
    for (let i = this.bubbles.length - 1; i >= 0; i--) { const b = this.bubbles[i]; b.age += dt; b.y += dt * 0.15; if (b.age > b.life) this.bubbles.splice(i, 1); }
    if (this.asteroid) {
      const a = this.asteroid;
      if (a.t < 1) {
        a.t = Math.min(1, a.t + dt * 0.5);
        const e = a.t * a.t;
        a.x = a.sx + (a.tx - a.sx) * e; a.y = a.sy + (a.ty - a.sy) * e;
        a.trail.push([a.x, a.y]); if (a.trail.length > 18) a.trail.shift();
        if (a.t >= 1) { a.flash = 1; if (a.cb) { a.cb(); a.cb = null; } }
      } else if (a.flash > 0) { a.flash -= dt * 1.2; if (a.flash <= 0) this.asteroid = null; }
    }
    // Infektionsschicht bei Tageswechsel neu aufbauen
    if (this.eng && this.eng.day !== this._lastInfDay) { this._lastInfDay = this.eng.day; this._infDirty = true; }
  }

  render(t) {
    const ctx = this.ctx;
    ctx.save();
    ctx.scale(this.dpr, this.dpr);
    ctx.fillStyle = '#050f1c'; ctx.fillRect(0, 0, this.cw, this.ch);
    ctx.translate(this.view.x, this.view.y);
    ctx.scale(this.view.scale, this.view.scale);

    ctx.imageSmoothingEnabled = true; ctx.imageSmoothingQuality = 'high';
    // Selbst gerenderte Karte (Ozean + Länder + Grenzen) aus dem Basis-Canvas
    if (this._baseCanvas) ctx.drawImage(this._baseCanvas, 0, 0, this.baseW, this.baseH);

    // driftende Wolken (zwei Ebenen)
    this._drawClouds(ctx, t);

    // Infektionspunkte (hochauflösend vorgerendert) + gerade aufploppende Punkte
    if ((this._infDirty || this._dotsReset) && this._infCanvas) this._updateDots();
    if (this._infCanvas && this.eng) {
      ctx.drawImage(this._infCanvas, 0, 0, this.baseW, this.baseH);
      ctx.drawImage(this._ovCanvas, 0, 0, this.baseW, this.baseH);
    }
    if (this.eng) this._drawPopDots(ctx);

    // Land unter der Maus / ausgewähltes Land hervorheben (Grenzen sind bereits im Basis-Canvas)
    if (this.hoverIso && this.hoverIso !== this.selectedIso) {
      this._glowCountry(ctx, this.hoverIso, 'rgba(255,255,255,0.12)');
      this._outlineCountry(ctx, this.hoverIso, 'rgba(255,255,255,0.95)', 1.6, 6, 'rgba(255,255,255,0.7)');
    }
    if (this.selectedIso) {
      const pulse = 0.16 + 0.08 * Math.sin(t * 3);
      this._glowCountry(ctx, this.selectedIso, `rgba(255,225,120,${pulse})`);
      this._outlineCountry(ctx, this.selectedIso, '#ffe070', 2.2, 10 + 5 * Math.sin(t * 3), '#ffb020');
    }

    if (this.eng) {
      this._drawCrater(ctx);
      this._drawFortresses(ctx);
      this._drawVehicles(ctx, t);
      this._drawSpecialAgents(ctx, t);
    }
    this._drawBubbles(ctx, t);
    if (this.eng && this.eng.startCountry) this._drawStartMarker(ctx);
    if (this.asteroid) this._drawAsteroid(ctx, t);

    ctx.restore();

    // Ländername als Tooltip beim Überfahren (Bildschirmkoordinaten)
    if (this.hoverIso && this.mouse && !this.touchMode) {
      const name = this.byIso[this.hoverIso] ? this.byIso[this.hoverIso].name : '';
      if (name) {
        ctx.save(); ctx.scale(this.dpr, this.dpr);
        ctx.font = '600 13px system-ui, sans-serif';
        const w = ctx.measureText(name).width + 16;
        let tx = this.mouse.x + 14, ty = this.mouse.y - 6;
        if (tx + w > this.cw) tx = this.cw - w - 4;
        ctx.fillStyle = 'rgba(12,4,6,0.85)'; ctx.strokeStyle = 'rgba(255,90,70,0.5)'; ctx.lineWidth = 1;
        roundRect(ctx, tx, ty - 18, w, 22, 5); ctx.fill(); ctx.stroke();
        ctx.fillStyle = '#ffe0d0'; ctx.textBaseline = 'middle'; ctx.textAlign = 'left';
        ctx.fillText(name, tx + 8, ty - 7);
        ctx.restore();
      }
    }
  }

  _glowCountry(ctx, iso, style) {
    const path = this.paths[iso]; if (!path) return;
    const bb = this.bbox[iso];
    ctx.save(); ctx.clip(path); ctx.fillStyle = style; ctx.fillRect(bb[0] - 2, bb[1] - 2, bb[2] - bb[0] + 4, bb[3] - bb[1] + 4); ctx.restore();
  }

  // Nachgezogener, leuchtender Grenz-Umriss um ein Land
  _outlineCountry(ctx, iso, color, width, glow, glowColor) {
    const path = this.paths[iso]; if (!path) return;
    ctx.save();
    ctx.lineJoin = 'round'; ctx.lineCap = 'round';
    ctx.shadowColor = glowColor || color; ctx.shadowBlur = glow / this.view.scale;
    ctx.strokeStyle = color; ctx.lineWidth = width / this.view.scale;
    ctx.stroke(path);
    ctx.shadowBlur = 0; // zweiter, klarer Strich obendrauf
    ctx.stroke(path);
    ctx.restore();
  }

  _drawClouds(ctx, t) {
    if (!this.cloudTile) return;
    const W = this.baseW, H = this.baseH;
    ctx.save();
    ctx.beginPath(); ctx.rect(0, 0, W, H); ctx.clip();
    const layers = [{ sp: 3, op: 0.22, sc: 1 }, { sp: 6, op: 0.14, sc: 1.5 }];
    for (const L of layers) {
      const off = ((t * L.sp) % W);
      ctx.globalAlpha = L.op;
      const tw = W * L.sc, th = H;
      for (let ox = -tw; ox < W + tw; ox += tw) ctx.drawImage(this.cloudTile, ox - off, 0, tw, th);
    }
    ctx.globalAlpha = 1;
    ctx.restore();
  }

  // ---------- Fahrzeuge ----------
  _drawVehicles(ctx, t) {
    for (const v of this.eng.vehicles) {
      if (v.kind === 'air') {
        const [ax, ay] = this.proj(v.ax, v.ay);
        let [bx, by] = this.proj(v.bx, v.by);
        if (Math.abs(bx - ax) > this.baseW / 2) bx += bx < ax ? this.baseW : -this.baseW;
        const mx = (ax + bx) / 2, my = (ay + by) / 2 - Math.hypot(bx - ax, by - ay) * 0.18;
        const tt = v.t;
        const x = (1 - tt) * (1 - tt) * ax + 2 * (1 - tt) * tt * mx + tt * tt * bx;
        const y = (1 - tt) * (1 - tt) * ay + 2 * (1 - tt) * tt * my + tt * tt * by;
        // Ableitung -> Flugrichtung
        const dx = 2 * (1 - tt) * (mx - ax) + 2 * tt * (bx - mx);
        const dy = 2 * (1 - tt) * (my - ay) + 2 * tt * (by - my);
        const ang = Math.atan2(dy, dx);
        // Flugspur
        ctx.strokeStyle = v.infected ? 'rgba(255,90,60,0.30)' : 'rgba(200,220,255,0.14)';
        ctx.lineWidth = 0.7 / this.view.scale; ctx.setLineDash([3 / this.view.scale, 3 / this.view.scale]);
        ctx.beginPath();
        for (let s = 0; s <= tt; s += 0.08) { const px = (1 - s) * (1 - s) * ax + 2 * (1 - s) * s * mx + s * s * bx; const py = (1 - s) * (1 - s) * ay + 2 * (1 - s) * s * my + s * s * by; s === 0 ? ctx.moveTo(px, py) : ctx.lineTo(px, py); }
        ctx.stroke(); ctx.setLineDash([]);
        this._drawPlane(ctx, x, y, ang, v.infected, t);
      } else {
        const seg = this._alongPoly(v.poly, v.t); if (!seg) continue;
        const seg2 = this._alongPoly(v.poly, Math.max(0, v.t - 0.02)) || seg;
        const ang = Math.atan2(seg[1] - seg2[1], seg[0] - seg2[0]);
        this._drawShip(ctx, seg[0], seg[1], ang, v.infected, t);
      }
    }
  }

  _drawPlane(ctx, x, y, ang, infected, t) {
    // Größe in Weltkoordinaten (auf Bildschirm ~konstant), 3D-gerenderter Sprite
    const px = clamp(26 / this.view.scale, 12, 30);
    ctx.save(); ctx.translate(x, y); ctx.rotate(ang + Math.PI / 2);
    if (infected) { ctx.shadowColor = 'rgba(255,70,50,0.9)'; ctx.shadowBlur = 8 / this.view.scale; }
    if (this.planeImg.complete) ctx.drawImage(this.planeImg, -px / 2, -px / 2, px, px);
    if (infected) { // roter Infektions-Marker
      ctx.shadowBlur = 0; ctx.fillStyle = 'rgba(255,60,40,0.95)';
      ctx.beginPath(); ctx.arc(0, -px * 0.32, px * 0.09, 0, 7); ctx.fill();
    }
    ctx.restore();
  }

  _drawShip(ctx, x, y, ang, infected, t) {
    const px = clamp(24 / this.view.scale, 11, 28);
    ctx.save(); ctx.translate(x, y); ctx.rotate(ang + Math.PI / 2);
    ctx.translate(0, Math.sin(t * 2 + x) * 0.4 / this.view.scale);
    // Kielwasser
    ctx.fillStyle = 'rgba(210,235,255,0.2)';
    ctx.beginPath(); ctx.moveTo(-px * 0.25, px * 0.4); ctx.lineTo(px * 0.35, px * 0.9); ctx.lineTo(-px * 0.35, px * 0.9); ctx.closePath(); ctx.fill();
    if (infected) { ctx.shadowColor = 'rgba(255,70,50,0.9)'; ctx.shadowBlur = 7 / this.view.scale; }
    if (this.shipImg.complete) ctx.drawImage(this.shipImg, -px / 2, -px / 2, px, px);
    if (infected) { ctx.shadowBlur = 0; ctx.fillStyle = 'rgba(255,60,40,0.95)'; ctx.beginPath(); ctx.arc(0, 0, px * 0.09, 0, 7); ctx.fill(); }
    ctx.restore();
  }

  _drawSpecialAgents(ctx, t) {
    if (!this.agents || !this.agents.length) return;
    const now = this._time;
    for (let i = this.agents.length - 1; i >= 0; i--) {
      const a = this.agents[i];
      const tt = clamp((now - a.t0) / a.dur, 0, 1);
      if (a.kind === 'plane') this._drawAgentPlane(ctx, a, tt);
      else this._drawSwarm(ctx, a, now);
      if (tt >= 1 && a.cb) { a.cb(); a.cb = null; }
      if (now - a.t0 > a.dur + (a.kind === 'plane' ? 0 : a.tail + 0.6)) this.agents.splice(i, 1);
    }
  }

  _bezier(ax, ay, mx, my, bx, by, s) {
    const u = 1 - s;
    return [u * u * ax + 2 * u * s * mx + s * s * bx, u * u * ay + 2 * u * s * my + s * s * by];
  }

  _drawAgentPlane(ctx, a, tt) {
    const [ax, ay] = a.a, [bx, by] = a.b;
    const mx = (ax + bx) / 2, my = (ay + by) / 2 - Math.hypot(bx - ax, by - ay) * 0.16;
    const [x, y] = this._bezier(ax, ay, mx, my, bx, by, tt);
    const [x2, y2] = this._bezier(ax, ay, mx, my, bx, by, Math.min(1, tt + 0.01));
    const ang = Math.atan2(y2 - y, x2 - x);
    ctx.strokeStyle = a.color.replace('1)', '0.5)'); ctx.lineWidth = 1.4 / this.view.scale; ctx.setLineDash([4 / this.view.scale, 4 / this.view.scale]);
    ctx.beginPath();
    for (let s = 0; s <= tt; s += 0.04) { const [px, py] = this._bezier(ax, ay, mx, my, bx, by, s); s === 0 ? ctx.moveTo(px, py) : ctx.lineTo(px, py); }
    ctx.stroke(); ctx.setLineDash([]);
    if (tt >= 1) return;
    const px = clamp(30 / this.view.scale, 14, 34);
    ctx.save(); ctx.translate(x, y); ctx.rotate(ang + Math.PI / 2);
    ctx.shadowColor = a.color; ctx.shadowBlur = 12 / this.view.scale;
    if (this.planeImg.complete) ctx.drawImage(this.planeImg, -px / 2, -px / 2, px, px);
    ctx.shadowBlur = 0; ctx.fillStyle = a.color; ctx.beginPath(); ctx.arc(0, -px * 0.32, px * 0.11, 0, 7); ctx.fill();
    ctx.restore();
  }

  // Schwarm aus einzelnen Punkten (Zombies, Vampire, Kristallwesen), die über
  // Land ziehen und sich im Zielland verteilen
  _drawSwarm(ctx, a, now) {
    const sprite = this.dotSprites[a.code];
    for (const p of a.parts) {
      const s = clamp((now - a.t0 - p.delay) / a.dur, 0, 1);
      if (now - a.t0 - p.delay < 0) continue;
      const [ax, ay] = p.a, [bx, by] = p.b;
      const mx = (ax + bx) / 2 + p.nx, my = (ay + by) / 2 + p.ny;
      let [x, y] = this._bezier(ax, ay, mx, my, bx, by, s);
      // leichtes Wanken beim Marschieren
      const wob = (1 - s) * 1.2;
      x += Math.sin(now * 7 + p.ph) * wob; y += Math.cos(now * 6 + p.ph) * wob;
      const fade = s >= 1 ? clamp(1 - (now - a.t0 - p.delay - a.dur) / 0.6, 0, 1) : 1;
      if (fade <= 0) continue;
      const r = DOT_R * (1.15 + 0.2 * Math.sin(now * 9 + p.ph));
      ctx.globalAlpha = fade * 0.35;
      ctx.drawImage(sprite, x - r * 2.2, y - r * 2.2, r * 4.4, r * 4.4);
      ctx.globalAlpha = fade;
      ctx.drawImage(sprite, x - r, y - r, r * 2, r * 2);
    }
    ctx.globalAlpha = 1;
  }

  // amount steuert die Größe des Schwarms
  sendAgent(fromIso, toIso, color, cb, kind = 'dot', amount = 1e6, mode = 'zombie') {
    const A = this.byIso[fromIso], B = this.byIso[toIso]; if (!A || !B) { if (cb) cb(); return; }
    let [ax, ay] = this.proj(A.lon, A.lat); let [bx, by] = this.proj(B.lon, B.lat);
    if (Math.abs(bx - ax) > this.baseW / 2) bx += bx < ax ? this.baseW : -this.baseW;
    const dist = Math.hypot(bx - ax, by - ay);
    this.agents ||= [];
    if (kind === 'plane') {
      this.agents.push({ kind, a: [ax, ay], b: [bx, by], t0: this._time, dur: 1.6 + dist / 260, color, cb });
      return;
    }
    const code = { zombie: DOT_CODE.zombie, vampire: DOT_CODE.vampire, crystal: DOT_CODE.xmon, control: DOT_CODE.control }[mode] || DOT_CODE.inf;
    const n = clamp(Math.round(6 + Math.log10(Math.max(10, amount)) * 5), 10, 44);
    const src = this.dots[fromIso], dst = this.dots[toIso];
    const pick = (d, fx, fy) => {
      if (!d) return [fx, fy];
      const i = (Math.random() * Math.min(d.n, 400)) | 0;
      return [d.x[i], d.y[i]];
    };
    const arc = Math.min(40, dist * 0.12);
    const parts = [];
    for (let k = 0; k < n; k++) {
      const pa = pick(src, ax, ay), pb = pick(dst, bx, by);
      if (Math.abs(pb[0] - pa[0]) > this.baseW / 2) pb[0] += pb[0] < pa[0] ? this.baseW : -this.baseW;
      parts.push({ a: pa, b: pb, delay: Math.random() * 0.9, ph: Math.random() * 7, nx: (Math.random() - 0.5) * 14, ny: -arc + (Math.random() - 0.5) * 14 });
    }
    this.agents.push({ kind: 'swarm', code, parts, t0: this._time, dur: 2.2 + dist / 320, tail: 0.9, cb });
  }
  sendPlane(fromIso, toIso, color, cb) { this.sendAgent(fromIso, toIso, color, cb, 'plane'); }

  _drawFortresses(ctx) {
    if (!this.eng || this.eng.opts.type !== 'necroa') return;
    for (const c of this.world.countries) {
      const st = this.eng.countries[c.iso];
      if (!st.fortress || st.fortress < 0.15) continue;
      const [x, y] = this.proj(c.lon, c.lat);
      const s = clamp(7 / this.view.scale, 3, 8) * (0.6 + st.fortress);
      ctx.save(); ctx.translate(x, y);
      ctx.fillStyle = 'rgba(40,60,90,0.9)'; ctx.strokeStyle = '#9fc0ff'; ctx.lineWidth = 1 / this.view.scale;
      // Festung: Turm mit Zinnen
      ctx.beginPath(); ctx.rect(-s, -s * 0.6, s * 2, s * 1.2); ctx.fill(); ctx.stroke();
      ctx.fillStyle = '#9fc0ff';
      for (let k = -1; k <= 1; k++) ctx.fillRect(k * s * 0.6 - s * 0.18, -s * 0.85, s * 0.36, s * 0.3);
      ctx.restore();
    }
  }

  _drawCrater(ctx) {
    if (!this.eng || !this.eng.craterIso) return;
    const c = this.byIso[this.eng.craterIso]; if (!c) return;
    const [x, y] = this.proj(c.lon, c.lat);
    const s = clamp(9 / this.view.scale, 4, 11);
    ctx.save(); ctx.translate(x, y);
    const g = ctx.createRadialGradient(0, 0, 0, 0, 0, s);
    g.addColorStop(0, 'rgba(60,30,70,0.9)'); g.addColorStop(0.6, 'rgba(120,70,150,0.5)'); g.addColorStop(1, 'rgba(120,70,150,0)');
    ctx.fillStyle = g; ctx.beginPath(); ctx.arc(0, 0, s, 0, 7); ctx.fill();
    ctx.strokeStyle = 'rgba(200,150,255,0.7)'; ctx.lineWidth = 1 / this.view.scale;
    ctx.beginPath(); ctx.arc(0, 0, s * 0.6, 0, 7); ctx.stroke();
    ctx.restore();
  }

  _alongPoly(poly, t) {
    if (!poly || poly.length < 2) return null;
    const pts = poly.map(([lo, la]) => this.proj(lo, la));
    let total = 0; const segs = [];
    for (let i = 1; i < pts.length; i++) { const d = Math.hypot(pts[i][0] - pts[i - 1][0], pts[i][1] - pts[i - 1][1]); segs.push(d); total += d; }
    let target = t * total, acc = 0;
    for (let i = 1; i < pts.length; i++) { if (acc + segs[i - 1] >= target) { const f = (target - acc) / (segs[i - 1] || 1); return [pts[i - 1][0] + (pts[i][0] - pts[i - 1][0]) * f, pts[i - 1][1] + (pts[i][1] - pts[i - 1][1]) * f]; } acc += segs[i - 1]; }
    return pts[pts.length - 1];
  }

  _drawBubbles(ctx, t) {
    for (const b of this.bubbles) {
      const [x, y] = this.proj(b.x, b.y); const yy = y - b.age * 2;
      const pulse = 1 + 0.15 * Math.sin(t * 6 + b.age * 4);
      const r = (b.type === 'country' ? 7 : b.type === 'special' ? 8 : 5.5) / this.view.scale * pulse;
      const alpha = clamp(1 - b.age / b.life, 0, 1);
      ctx.globalAlpha = alpha; ctx.beginPath(); ctx.arc(x, yy, r, 0, 7);
      const g = ctx.createRadialGradient(x, yy, 0, x, yy, r);
      const col = b.type === 'special' ? '200,120,255' : b.type === 'country' ? '255,190,60' : '120,210,255';
      g.addColorStop(0, `rgba(${col},1)`); g.addColorStop(1, `rgba(${col},0)`);
      ctx.fillStyle = g; ctx.fill();
      ctx.fillStyle = '#06121f'; ctx.font = `bold ${5.5 / this.view.scale}px sans-serif`; ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
      ctx.fillText('DNA', x, yy);
      ctx.globalAlpha = 1;
    }
  }

  _drawStartMarker(ctx) {
    const c = this.byIso[this.eng.startCountry]; if (!c) return;
    const [x, y] = this.proj(c.lon, c.lat);
    const pulse = 6 + Math.sin(Date.now() / 300) * 2;
    ctx.strokeStyle = '#ff5a3a'; ctx.lineWidth = 1.4 / this.view.scale;
    ctx.beginPath(); ctx.arc(x, y, pulse / this.view.scale, 0, 7); ctx.stroke();
  }

  _drawAsteroid(ctx, t) {
    const a = this.asteroid;
    if (a.flash > 0 && a.t >= 1) {
      const [tx, ty] = [a.tx, a.ty];
      const r = (1 - a.flash) * 120 / this.view.scale;
      const g = ctx.createRadialGradient(tx, ty, 0, tx, ty, r);
      g.addColorStop(0, `rgba(180,120,255,${a.flash})`); g.addColorStop(0.5, `rgba(255,120,60,${a.flash * 0.6})`); g.addColorStop(1, 'rgba(255,120,60,0)');
      ctx.fillStyle = g; ctx.beginPath(); ctx.arc(tx, ty, r, 0, 7); ctx.fill();
      return;
    }
    // feuriger Schweif
    for (let i = 0; i < a.trail.length; i++) {
      const p = a.trail[i]; const al = i / a.trail.length;
      const rr = (2 + al * 10) / this.view.scale;
      const g = ctx.createRadialGradient(p[0], p[1], 0, p[0], p[1], rr);
      g.addColorStop(0, `rgba(255,${200 + al * 55 | 0},${120 + al * 100 | 0},${al * 0.8})`);
      g.addColorStop(1, 'rgba(255,120,40,0)');
      ctx.fillStyle = g; ctx.beginPath(); ctx.arc(p[0], p[1], rr, 0, 7); ctx.fill();
    }
    // Asteroid (3D-Sprite), rotiert
    const s = clamp(34 / this.view.scale, 16, 60);
    ctx.save(); ctx.translate(a.x, a.y);
    // Glut-Halo
    const hg = ctx.createRadialGradient(0, 0, 0, 0, 0, s * 0.8);
    hg.addColorStop(0, 'rgba(255,150,60,0.5)'); hg.addColorStop(1, 'rgba(255,120,40,0)');
    ctx.fillStyle = hg; ctx.beginPath(); ctx.arc(0, 0, s * 0.8, 0, 7); ctx.fill();
    ctx.rotate(t * 1.5);
    if (this.astImg && this.astImg.complete) ctx.drawImage(this.astImg, -s / 2, -s / 2, s, s);
    ctx.restore();
  }
}

function mulberry(a) { return function () { a |= 0; a = (a + 0x6d2b79f5) | 0; let t = Math.imul(a ^ (a >>> 15), 1 | a); t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t; return ((t ^ (t >>> 14)) >>> 0) / 4294967296; }; }
function hashStr(s) { let h = 2166136261; for (let i = 0; i < s.length; i++) { h ^= s.charCodeAt(i); h = Math.imul(h, 16777619); } return h >>> 0; }

function roundRect(ctx, x, y, w, h, r) {
  ctx.beginPath();
  ctx.moveTo(x + r, y); ctx.arcTo(x + w, y, x + w, y + h, r); ctx.arcTo(x + w, y + h, x, y + h, r);
  ctx.arcTo(x, y + h, x, y, r); ctx.arcTo(x, y, x + w, y, r); ctx.closePath();
}
