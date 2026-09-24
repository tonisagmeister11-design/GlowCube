// Weltkarte auf Canvas 2D. Hintergrund ist das eingebettete Satellitenbild; die
// Länder-Polygone (auf das Bild kalibrierte equirektangulare Projektion) dienen
// zur Auswahl, für dezente Grenzen und die Infektionsdarstellung. Infektionen
// erscheinen als wachsende Punktwolken; Flugzeuge und Schiffe sind echte kleine
// Vektorgrafiken. Eine driftende Wolkenschicht belebt die Karte.
import { MAP_IMAGE } from '../generated/mapimg.js';
import { VEHICLES } from '../generated/vehicles.js';

const clamp = (x, a, b) => Math.max(a, Math.min(b, x));
// Kalibrierung der Projektion auf das Kartenbild (16:9).
// Längengrad linear; Breitengrad stückweise (die KI-Karte verteilt die Breiten
// nicht ganz gleichmäßig – v.a. die mittleren Nordbreiten liegen tiefer).
const CAL = {
  lonOff: -0.02, lonScale: 0.982, aspect: 1671 / 941,
  // [Breitengrad, y-Anteil 0..1 von oben], absteigend
  latPts: [
    [90, 0.000], [66, 0.150], [55, 0.225], [45, 0.320], [35, 0.405],
    [23, 0.487], [10, 0.545], [0, 0.588], [-15, 0.665], [-34, 0.792], [-55, 0.930], [-66, 1.000],
  ],
};
function latToFrac(lat) {
  const p = CAL.latPts;
  if (lat >= p[0][0]) return p[0][1];
  if (lat <= p[p.length - 1][0]) return p[p.length - 1][1];
  for (let i = 0; i < p.length - 1; i++) {
    if (lat <= p[i][0] && lat >= p[i + 1][0]) {
      const t = (p[i][0] - lat) / (p[i][0] - p[i + 1][0]);
      return p[i][1] + (p[i + 1][1] - p[i][1]) * t;
    }
  }
  return 0.5;
}

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
    this.ready = false;
    this.img = new Image();
    this.img.onload = () => { this.ready = true; this._buildWaterMask(); this._sanitizeSea(); };
    this.img.src = MAP_IMAGE;
    this.planeImg = new Image(); this.planeImg.src = VEHICLES.plane;
    this.shipImg = new Image(); this.shipImg.src = VEHICLES.ship;
    this._samplePoints();
    this._buildCloudTile();
    this.asteroid = null;
    this._infDirty = true;
    this._lastInfDay = -1;
    this._bindEvents();
    this.resize();
  }

  // ---------- Projektion ----------
  proj(lon, lat) {
    const px = ((lon + 180) / 360 * CAL.lonScale + CAL.lonOff) * this.baseW;
    const py = latToFrac(lat) * this.baseH;
    return [px, py];
  }

  // ---------- Infektions-Stichprobenpunkte je Land ----------
  _samplePoints() {
    this.samples = {};
    for (const c of this.world.countries) {
      const rings = this.world.geo[c.iso];
      if (!rings || !rings.length) { this.samples[c.iso] = [[c.lon, c.lat]]; continue; }
      let minx = 1e9, miny = 1e9, maxx = -1e9, maxy = -1e9;
      for (const r of rings) for (const [x, y] of r) { minx = Math.min(minx, x); miny = Math.min(miny, y); maxx = Math.max(maxx, x); maxy = Math.max(maxy, y); }
      const n = clamp(Math.round(Math.sqrt(c.area) / 45), 5, 44);
      const pts = [];
      let tries = 0;
      const rng = mulberry(hashStr(c.iso));
      while (pts.length < n && tries < n * 40) {
        tries++;
        const x = minx + rng() * (maxx - minx), y = miny + rng() * (maxy - miny);
        if (this._inRings(rings, x, y)) pts.push([x, y]);
      }
      if (!pts.length) pts.push([c.lon, c.lat]);
      // von der Landesmitte nach außen sortieren -> Infektion breitet sich sichtbar aus
      pts.sort((a, b) => ((a[0] - c.lon) ** 2 + (a[1] - c.lat) ** 2) - ((b[0] - c.lon) ** 2 + (b[1] - c.lat) ** 2));
      this.samples[c.iso] = pts;
    }
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

  // ---------- Wolken-Kachel ----------
  _buildCloudTile() {
    const w = 1024, h = 512;
    const cv = document.createElement('canvas'); cv.width = w; cv.height = h;
    const x = cv.getContext('2d');
    x.clearRect(0, 0, w, h);
    const rng = mulberry(9182);
    for (let i = 0; i < 90; i++) {
      const cx = rng() * w, cy = rng() * h * 0.9 + h * 0.05;
      const r = 30 + rng() * 120;
      const g = x.createRadialGradient(cx, cy, 0, cx, cy, r);
      const a = 0.05 + rng() * 0.12;
      g.addColorStop(0, `rgba(235,240,255,${a})`); g.addColorStop(1, 'rgba(235,240,255,0)');
      x.fillStyle = g; x.beginPath(); x.arc(cx, cy, r, 0, 7); x.fill();
      if (cx < 140) { x.beginPath(); x.arc(cx + w, cy, r, 0, 7); x.fill(); }
      if (cx > w - 140) { x.beginPath(); x.arc(cx - w, cy, r, 0, 7); x.fill(); }
    }
    this.cloudTile = cv;
  }

  // ---------- Events (Pan/Zoom/Klick) ----------
  _bindEvents() {
    const cv = this.canvas;
    let drag = null;
    cv.addEventListener('mousedown', (e) => { drag = { x: e.clientX, y: e.clientY, vx: this.view.x, vy: this.view.y, moved: 0 }; });
    window.addEventListener('mousemove', (e) => {
      const r = cv.getBoundingClientRect();
      this.mouse = { x: e.clientX - r.left, y: e.clientY - r.top };
      if (drag) {
        const dx = e.clientX - drag.x, dy = e.clientY - drag.y;
        drag.moved += Math.abs(dx) + Math.abs(dy);
        this.view.x = drag.vx + dx; this.view.y = drag.vy + dy; this._clampView();
      } else {
        this.hoverIso = this._hit(this.mouse.x, this.mouse.y);
        cv.style.cursor = this.hoverIso ? 'pointer' : 'grab';
      }
    });
    window.addEventListener('mouseup', () => { if (drag && drag.moved < 6) this._click(this.mouse); drag = null; });
    cv.addEventListener('wheel', (e) => {
      e.preventDefault();
      const r = cv.getBoundingClientRect();
      const mx = e.clientX - r.left, my = e.clientY - r.top;
      const before = this._toWorld(mx, my);
      this.view.scale = clamp(this.view.scale * (e.deltaY < 0 ? 1.15 : 0.87), 1, 8);
      const after = this._toWorld(mx, my);
      this.view.x += (after.x - before.x) * this.view.scale;
      this.view.y += (after.y - before.y) * this.view.scale;
      this._clampView();
    }, { passive: false });
    cv.addEventListener('touchstart', (e) => { if (e.touches.length === 1) { const t = e.touches[0]; drag = { x: t.clientX, y: t.clientY, vx: this.view.x, vy: this.view.y, moved: 0 }; } }, { passive: true });
    cv.addEventListener('touchmove', (e) => {
      if (drag && e.touches.length === 1) { const t = e.touches[0]; const dx = t.clientX - drag.x, dy = t.clientY - drag.y; drag.moved += Math.abs(dx) + Math.abs(dy); this.view.x = drag.vx + dx; this.view.y = drag.vy + dy; this._clampView(); }
    }, { passive: true });
    cv.addEventListener('touchend', () => { if (drag && drag.moved < 8) { const r = cv.getBoundingClientRect(); this._click({ x: drag.x - r.left, y: drag.y - r.top }); } drag = null; });
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

  _click(m) {
    const wp = this._toWorld(m.x, m.y);
    for (let i = this.bubbles.length - 1; i >= 0; i--) {
      const b = this.bubbles[i];
      const [bx, by] = this.proj(b.x, b.y);
      if (Math.hypot(bx - wp.x, by - wp.y - (b.age * 2)) < 16 / this.view.scale) { if (this.onBubble) this.onBubble(b); this.bubbles.splice(i, 1); return; }
    }
    const iso = this._hit(m.x, m.y);
    if (this.targetMode && iso) { const cb = this.targetMode; this.targetMode = null; this.canvas.style.cursor = 'grab'; cb(iso); return; }
    if (iso && this.onPick) { this.selectedIso = iso; this.onPick(iso); }
  }

  _hit(sx, sy) {
    const w = this._toWorld(sx, sy);
    for (const c of this.world.countries) {
      const [cx, cy] = this.proj(c.lon, c.lat);
      if ((cx - w.x) ** 2 + (cy - w.y) ** 2 < 160 ** 2 && this._inCountry(c.iso, w.x, w.y)) return c.iso;
    }
    let ni = null, nd = 1e9;
    for (const c of this.world.countries) { const [cx, cy] = this.proj(c.lon, c.lat); const d = Math.hypot(cx - w.x, cy - w.y); if (d < nd) { nd = d; ni = c.iso; } }
    return nd < 12 ? ni : null;
  }

  _inCountry(iso, x, y) {
    const rings = this.world.geo[iso]; if (!rings) return false;
    let inside = false;
    for (const ring of rings) {
      for (let i = 0, j = ring.length - 1; i < ring.length; j = i++) {
        const [xi0, yi0] = this.proj(ring[i][0], ring[i][1]);
        const [xj0, yj0] = this.proj(ring[j][0], ring[j][1]);
        if (((yi0 > y) !== (yj0 > y)) && (x < (xj0 - xi0) * (y - yi0) / (yj0 - yi0) + xi0)) inside = !inside;
      }
    }
    return inside;
  }

  // ---------- Resize / Pfade ----------
  resize() {
    const parent = this.canvas.parentElement;
    const r = parent ? parent.getBoundingClientRect() : { width: 0, height: 0 };
    this.cw = r.width || 1280; this.ch = r.height || 720;
    this.canvas.width = this.cw * this.dpr; this.canvas.height = this.ch * this.dpr;
    this.canvas.style.width = this.cw + 'px'; this.canvas.style.height = this.ch + 'px';
    // Bild füllt die Ansicht, Seitenverhältnis 16:9 beibehalten
    this.baseW = Math.max(this.cw, this.ch * CAL.aspect);
    this.baseH = this.baseW / CAL.aspect;
    if (this.baseH < this.ch) { this.baseH = this.ch; this.baseW = this.baseH * CAL.aspect; }
    this._clampView();
    this._buildPaths();
    this._infCanvas = document.createElement('canvas');
    this._infRes = 2; // doppelte Auflösung für scharfe Infektionspunkte beim Zoom
    this._infCanvas.width = Math.round(this.baseW * this._infRes);
    this._infCanvas.height = Math.round(this.baseH * this._infRes);
    this._infDirty = true;
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
    for (const c of this.world.countries) {
      const p = new Path2D();
      for (const ring of this.world.geo[c.iso]) {
        const pts = this._ringPoints(ring);
        for (let i = 0; i < pts.length; i++) { if (i === 0) p.moveTo(pts[i][0], pts[i][1]); else p.lineTo(pts[i][0], pts[i][1]); }
        p.closePath();
      }
      this.paths[c.iso] = p;
    }
  }

  setEngine(eng) { this.eng = eng; this._infDirty = true; this._lastInfDay = -1; this._sanitizeSea(); }

  // ---------- Wasser-Maske (Schiffe fahren nur auf Wasser) ----------
  _buildWaterMask() {
    const MW = 668, MH = 376;
    const cv = document.createElement('canvas'); cv.width = MW; cv.height = MH;
    const x = cv.getContext('2d', { willReadFrequently: true });
    x.drawImage(this.img, 0, 0, MW, MH);
    let data;
    try { data = x.getImageData(0, 0, MW, MH).data; } catch (e) { this.water = null; return; }
    const mask = new Uint8Array(MW * MH);
    for (let i = 0; i < MW * MH; i++) {
      const r = data[i * 4], g = data[i * 4 + 1], b = data[i * 4 + 2];
      // Wasser = blau ODER türkis (b über Rot, b nicht deutlich unter Grün) und
      // nicht zu hell. Land ist grün-dominant, sandfarben oder weiß.
      mask[i] = (b > r && b >= g - 6 && (r + g + b) < 470) ? 1 : 0;
    }
    this.water = { mask, MW, MH };
  }

  isWater(lon, lat) {
    if (!this.water) return true;
    // in Bildanteil (0..1) über dieselbe Projektion umrechnen
    const fx = ((lon + 180) / 360 * CAL.lonScale + CAL.lonOff);
    const fy = latToFrac(lat);
    let ix = Math.round(fx * this.water.MW), iy = Math.round(fy * this.water.MH);
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

  // ---------- Infektionsschicht (gedrosselt neu gezeichnet) ----------
  _renderInfectionLayer() {
    const x = this._infCanvas.getContext('2d');
    x.setTransform(1, 0, 0, 1, 0, 0);
    x.clearRect(0, 0, this._infCanvas.width, this._infCanvas.height);
    x.setTransform(this._infRes, 0, 0, this._infRes, 0, 0); // proj liefert baseW-Koordinaten
    if (!this.eng) return;
    for (const c of this.world.countries) {
      const st = this.eng.countries[c.iso];
      const alive = st.pop || 1;
      const infF = st.infected / alive, deadF = st.dead / alive;
      const zF = st.zombies / alive, apF = st.apes / alive, vF = st.vampires / alive, ctF = st.controlled / alive;
      const xmF = (st.xmon || 0) / alive;
      const any = infF + deadF + zF + apF + vF + ctF + st.xeno + xmF;
      if (any < 1e-4) continue;
      // dezente Einfärbung des Landes
      const sev = clamp(infF * 1.3 + deadF * 2.2 + zF + vF + ctF + st.xeno, 0, 1);
      x.save();
      x.clip(this.paths[c.iso]);
      const g = `rgba(${Math.round(150 + sev * 100)},${Math.round(40 - sev * 20)},${Math.round(30)},${0.18 + sev * 0.4})`;
      x.fillStyle = g;
      const [bx, by] = this.proj(c.lon, c.lat);
      x.fillRect(bx - 200, by - 200, 400, 400);
      x.restore();
      // Punktwolke
      const pts = this.samples[c.iso];
      const drawDots = (frac, color, r) => {
        const cnt = Math.min(pts.length, Math.ceil(frac * pts.length));
        for (let i = 0; i < cnt; i++) { const [px, py] = this.proj(pts[i][0], pts[i][1]); x.fillStyle = color; x.beginPath(); x.arc(px, py, r, 0, 7); x.fill(); }
      };
      if (st.xeno > 0.01) drawDots(st.xeno, 'rgba(150,90,230,0.85)', 1.8);
      if (xmF > 0.003) drawDots(xmF, 'rgba(210,150,255,0.98)', 2.1);
      drawDots(infF, 'rgba(255,90,50,0.85)', 1.7);
      if (deadF > 0.005) drawDots(deadF, 'rgba(60,10,12,0.95)', 1.7);
      if (zF > 0.005) drawDots(zF, 'rgba(120,230,80,0.9)', 1.9);
      if (apF > 0.005) drawDots(apF, 'rgba(220,180,60,0.95)', 1.9);
      if (vF > 0.005) drawDots(vF, 'rgba(255,40,120,0.95)', 1.9);
      if (ctF > 0.005) drawDots(ctF, 'rgba(200,120,255,0.95)', 1.9);
    }
    this._infDirty = false;
  }

  spawnBubble(b) {
    const c = this.byIso[b.iso]; if (!c) return;
    if (this.bubbles.length > 16) this.bubbles.shift();
    this.bubbles.push({ iso: b.iso, x: c.lon + (Math.random() - 0.5) * 3, y: c.lat + (Math.random() - 0.5) * 3, age: 0, life: 9, type: b.type });
  }

  playAsteroidIntro(iso, cb) {
    const c = this.byIso[iso] || this.byIso[this.eng?.startCountry];
    const [tx, ty] = this.proj(c ? c.lon : 0, c ? c.lat : 0);
    this.asteroid = { t: 0, tx, ty, sx: tx - this.baseW * 0.4, sy: ty - this.baseH * 0.6, cb, flash: 0, trail: [] };
  }

  update(dt) {
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
    if (this.ready) ctx.drawImage(this.img, 0, 0, this.baseW, this.baseH);

    // driftende Wolken (zwei Ebenen)
    this._drawClouds(ctx, t);

    // Infektionsschicht (hochauflösend, auf Kartengröße skaliert)
    if (this._infDirty && this._infCanvas) this._renderInfectionLayer();
    if (this._infCanvas && this.eng) { ctx.globalAlpha = 0.95; ctx.drawImage(this._infCanvas, 0, 0, this.baseW, this.baseH); ctx.globalAlpha = 1; }

    // Das Kartenbild hat keine Grenzen – daher zeichnen WIR die Ländergrenzen
    // (die einzigen Grenzen). Dezent, damit die Karte ruhig bleibt.
    ctx.lineWidth = 0.6 / this.view.scale;
    ctx.strokeStyle = 'rgba(255,255,255,0.30)';
    ctx.shadowColor = 'rgba(0,0,0,0.5)'; ctx.shadowBlur = 1.2 / this.view.scale;
    for (const c of this.world.countries) ctx.stroke(this.paths[c.iso]);
    ctx.shadowBlur = 0;
    // Land unter der Maus / ausgewähltes Land zusätzlich hervorheben.
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
    if (this.hoverIso && this.mouse) {
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
    const c = this.byIso[iso]; const [bx, by] = this.proj(c.lon, c.lat);
    ctx.save(); ctx.clip(path); ctx.fillStyle = style; ctx.fillRect(bx - 300, by - 300, 600, 600); ctx.restore();
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
    const layers = [{ sp: 4, op: 0.5, sc: 1 }, { sp: 9, op: 0.35, sc: 1.4 }];
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
    if (!this.agents) return;
    for (let i = this.agents.length - 1; i >= 0; i--) {
      const a = this.agents[i];
      a.t += a.speed;
      let [ax, ay] = this.proj(a.ax, a.ay); let [bx, by] = this.proj(a.bx, a.by);
      if (Math.abs(bx - ax) > this.baseW / 2) bx += bx < ax ? this.baseW : -this.baseW;
      const arc = a.kind === 'plane' ? Math.hypot(bx - ax, by - ay) * 0.16 : 20 / this.view.scale;
      const mx = (ax + bx) / 2, my = (ay + by) / 2 - arc;
      const tt = a.t;
      const x = (1 - tt) * (1 - tt) * ax + 2 * (1 - tt) * tt * mx + tt * tt * bx;
      const y = (1 - tt) * (1 - tt) * ay + 2 * (1 - tt) * tt * my + tt * tt * by;
      if (a.kind === 'plane') {
        const dx = 2 * (1 - tt) * (mx - ax) + 2 * tt * (bx - mx);
        const dy = 2 * (1 - tt) * (my - ay) + 2 * tt * (by - my);
        const ang = Math.atan2(dy, dx);
        // farbige Flugspur
        ctx.strokeStyle = a.color.replace('1)', '0.5)'); ctx.lineWidth = 1.4 / this.view.scale; ctx.setLineDash([4 / this.view.scale, 4 / this.view.scale]);
        ctx.beginPath();
        for (let s = 0; s <= tt; s += 0.06) { const px = (1 - s) * (1 - s) * ax + 2 * (1 - s) * s * mx + s * s * bx; const py = (1 - s) * (1 - s) * ay + 2 * (1 - s) * s * my + s * s * by; s === 0 ? ctx.moveTo(px, py) : ctx.lineTo(px, py); }
        ctx.stroke(); ctx.setLineDash([]);
        const px = clamp(30 / this.view.scale, 14, 34);
        ctx.save(); ctx.translate(x, y); ctx.rotate(ang + Math.PI / 2);
        ctx.shadowColor = a.color; ctx.shadowBlur = 12 / this.view.scale;
        if (this.planeImg.complete) ctx.drawImage(this.planeImg, -px / 2, -px / 2, px, px);
        ctx.shadowBlur = 0; ctx.fillStyle = a.color; ctx.beginPath(); ctx.arc(0, -px * 0.32, px * 0.11, 0, 7); ctx.fill();
        ctx.restore();
      } else {
        ctx.save(); ctx.translate(x, y);
        const pulse = 1 + 0.3 * Math.sin(t * 8);
        ctx.fillStyle = a.color; ctx.globalAlpha = 0.5; ctx.beginPath(); ctx.arc(0, 0, 5 / this.view.scale * pulse, 0, 7); ctx.fill();
        ctx.globalAlpha = 1; ctx.beginPath(); ctx.arc(0, 0, 2.6 / this.view.scale, 0, 7); ctx.fill();
        ctx.restore();
      }
      if (a.t >= 1) { if (a.cb) a.cb(); this.agents.splice(i, 1); }
    }
  }

  sendAgent(fromIso, toIso, color, cb, kind = 'dot') {
    const a = this.byIso[fromIso], b = this.byIso[toIso]; if (!a || !b) { if (cb) cb(); return; }
    (this.agents ||= []).push({ ax: a.lon, ay: a.lat, bx: b.lon, by: b.lat, t: 0, speed: kind === 'plane' ? 0.018 : 0.03, color, cb, kind });
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
    // Schweif
    for (let i = 0; i < a.trail.length; i++) {
      const p = a.trail[i]; const al = i / a.trail.length;
      ctx.fillStyle = `rgba(255,${120 + al * 100},${60 + al * 120},${al * 0.6})`;
      ctx.beginPath(); ctx.arc(p[0], p[1], (1 + al * 3) / this.view.scale, 0, 7); ctx.fill();
    }
    // Felsen
    ctx.save(); ctx.translate(a.x, a.y); ctx.rotate(t * 4);
    ctx.fillStyle = '#5a4a55'; ctx.strokeStyle = '#c89aff'; ctx.lineWidth = 0.4 / this.view.scale;
    const s = 5 / this.view.scale;
    ctx.beginPath();
    for (let i = 0; i < 7; i++) { const ang = i / 7 * Math.PI * 2; const rr = s * (0.7 + 0.4 * Math.sin(i * 2.3)); i ? ctx.lineTo(Math.cos(ang) * rr, Math.sin(ang) * rr) : ctx.moveTo(Math.cos(ang) * rr, Math.sin(ang) * rr); }
    ctx.closePath(); ctx.fill(); ctx.stroke();
    ctx.fillStyle = 'rgba(200,150,255,0.8)'; ctx.beginPath(); ctx.arc(0, 0, s * 0.4, 0, 7); ctx.fill();
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
