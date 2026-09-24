// Weltkarte auf Canvas 2D. Zeichnet Länder aus Polygonen (equirektangulare
// Projektion), färbt sie nach Infektions-/Todeslage ein, rendert animierte
// Flugzeuge (Bögen) und Schiffe (Seewege) sowie anklickbare DNA-Blasen.
// Unterstützt Zoom/Pan und Länder-Auswahl.
const clamp = (x, a, b) => Math.max(a, Math.min(b, x));

export class WorldMap {
  constructor(canvas, world) {
    this.canvas = canvas;
    this.ctx = canvas.getContext('2d');
    this.world = world;
    this.byIso = {};
    for (const c of world.countries) this.byIso[c.iso] = c;
    this.view = { x: 0, y: 0, scale: 1 };   // Pan/Zoom
    this.hoverIso = null;
    this.selectedIso = null;
    this.mode = 'play';                       // 'select' (Startland) | 'play'
    this.onPick = null;
    this.onBubble = null;
    this.eng = null;
    this.bubbles = [];                        // {iso,x,y,age,type,life}
    this.dpr = Math.min(window.devicePixelRatio || 1, 2);
    this._precompute();
    this._bindEvents();
    this.resize();
  }

  _precompute() {
    // Zentroide in Bildschirmkoordinaten und Bounding je Land
    this.centroids = {};
    for (const c of this.world.countries) this.centroids[c.iso] = [c.lon, c.lat];
  }

  proj(lon, lat) {
    // equirektangular in [0..W, 0..H] (World-Raum, vor View-Transform)
    const W = this.baseW, H = this.baseH;
    return [(lon + 180) / 360 * W, (90 - lat) / 180 * H];
  }

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
        this.view.x = drag.vx + dx; this.view.y = drag.vy + dy;
        this._clampView();
      } else {
        this.hoverIso = this._hit(this.mouse.x, this.mouse.y);
        cv.style.cursor = this.hoverIso ? 'pointer' : 'grab';
      }
    });
    window.addEventListener('mouseup', (e) => {
      if (drag && drag.moved < 6) this._click(this.mouse);
      drag = null;
    });
    cv.addEventListener('wheel', (e) => {
      e.preventDefault();
      const r = cv.getBoundingClientRect();
      const mx = e.clientX - r.left, my = e.clientY - r.top;
      const before = this._toWorld(mx, my);
      this.view.scale = clamp(this.view.scale * (e.deltaY < 0 ? 1.15 : 0.87), 1, 7);
      const after = this._toWorld(mx, my);
      this.view.x += (after.x - before.x) * this.view.scale;
      this.view.y += (after.y - before.y) * this.view.scale;
      this._clampView();
    }, { passive: false });
    // Touch
    cv.addEventListener('touchstart', (e) => {
      if (e.touches.length === 1) { const t = e.touches[0]; drag = { x: t.clientX, y: t.clientY, vx: this.view.x, vy: this.view.y, moved: 0 }; }
    }, { passive: true });
    cv.addEventListener('touchmove', (e) => {
      if (drag && e.touches.length === 1) {
        const t = e.touches[0]; const dx = t.clientX - drag.x, dy = t.clientY - drag.y;
        drag.moved += Math.abs(dx) + Math.abs(dy);
        this.view.x = drag.vx + dx; this.view.y = drag.vy + dy; this._clampView();
      }
    }, { passive: true });
    cv.addEventListener('touchend', (e) => {
      if (drag && drag.moved < 8) { const r = cv.getBoundingClientRect(); this._click({ x: drag.x - r.left, y: drag.y - r.top }); }
      drag = null;
    });
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
    const iso = this._hit(m.x, m.y);
    // Blase getroffen?
    const wp = this._toWorld(m.x, m.y);
    for (let i = this.bubbles.length - 1; i >= 0; i--) {
      const b = this.bubbles[i];
      const [bx, by] = this.proj(b.x, b.y);
      if (Math.hypot(bx - wp.x, by - wp.y) < 14 / this.view.scale) {
        if (this.onBubble) this.onBubble(b);
        this.bubbles.splice(i, 1);
        return;
      }
    }
    if (iso && this.onPick) { this.selectedIso = iso; this.onPick(iso); }
  }

  _hit(sx, sy) {
    const w = this._toWorld(sx, sy);
    // grobe Vorauswahl per Zentroid-Distanz, dann Punkt-in-Polygon
    let best = null, bestD = 1e9;
    for (const c of this.world.countries) {
      const [cx, cy] = this.proj(c.lon, c.lat);
      const d = (cx - w.x) ** 2 + (cy - w.y) ** 2;
      if (d < bestD && d < (120) ** 2) {
        if (this._inCountry(c.iso, w.x, w.y)) { best = c.iso; bestD = 0; break; }
        if (d < bestD) { bestD = d; }
      }
    }
    if (best) return best;
    // Fallback: nächstes Zentroid, wenn nah genug (kleine Inseln)
    let ni = null, nd = 1e9;
    for (const c of this.world.countries) {
      const [cx, cy] = this.proj(c.lon, c.lat);
      const d = Math.hypot(cx - w.x, cy - w.y);
      if (d < nd) { nd = d; ni = c.iso; }
    }
    return nd < 10 ? ni : null;
  }

  _inCountry(iso, x, y) {
    const rings = this.world.geo[iso];
    if (!rings) return false;
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

  resize() {
    const parent = this.canvas.parentElement;
    const r = parent ? parent.getBoundingClientRect() : { width: 0, height: 0 };
    if (!r.width || !r.height) { this.cw = 1280; this.ch = 720; } else { this.cw = r.width; this.ch = r.height; }
    r.width = this.cw; r.height = this.ch;
    this.canvas.width = r.width * this.dpr;
    this.canvas.height = r.height * this.dpr;
    this.canvas.style.width = r.width + 'px';
    this.canvas.style.height = r.height + 'px';
    // Basisgröße der Weltkarte: füllt Breite, 2:1-Verhältnis
    this.baseW = r.width;
    this.baseH = r.width / 2;
    if (this.baseH < r.height) { this.baseH = r.height; this.baseW = r.height * 2; }
    this._clampView();
    this._buildPaths();
  }

  // Path2D je Land einmal im Weltraum bauen (schnelles Neuzeichnen)
  _buildPaths() {
    this.paths = {};
    for (const c of this.world.countries) {
      const p = new Path2D();
      for (const ring of this.world.geo[c.iso]) {
        for (let i = 0; i < ring.length; i++) {
          const [x, y] = this.proj(ring[i][0], ring[i][1]);
          if (i === 0) p.moveTo(x, y); else p.lineTo(x, y);
        }
        p.closePath();
      }
      this.paths[c.iso] = p;
    }
  }

  setEngine(eng) { this.eng = eng; }

  focusCountry(iso, scale = 3) {
    const c = this.byIso[iso]; if (!c) return;
    const [x, y] = this.proj(c.lon, c.lat);
    this.view.scale = scale;
    this.view.x = this.cw / 2 - x * scale;
    this.view.y = this.ch / 2 - y * scale;
    this._clampView();
  }

  countryColor(iso) {
    const st = this.eng ? this.eng.countries[iso] : null;
    if (!st) return '#3a6b3a';
    const total = st.pop || 1;
    const infFrac = st.infected / total;
    const deadFrac = st.dead / total;
    const special = (st.zombies + st.apes + st.vampires + st.xeno * total + st.controlled) / total;
    if (deadFrac > 0.01 || infFrac > 0.001 || special > 0.001) {
      // grün -> gelb -> rot -> dunkelrot je nach Betroffenheit
      const sev = clamp(infFrac * 1.2 + deadFrac * 2 + special, 0, 1);
      const r = Math.round(120 + sev * 135);
      const g = Math.round(150 - sev * 140);
      const b = Math.round(60 - sev * 50);
      return `rgb(${r},${g},${b})`;
    }
    return '#3f7040';
  }

  spawnBubble(b) {
    const c = this.byIso[b.iso]; if (!c) return;
    // leichte Streuung um das Land
    this.bubbles.push({ iso: b.iso, x: c.lon + (Math.random() - 0.5) * 3, y: c.lat + (Math.random() - 0.5) * 3, age: 0, life: 8, type: b.type, ref: b });
  }

  update(dt) {
    for (let i = this.bubbles.length - 1; i >= 0; i--) {
      const b = this.bubbles[i]; b.age += dt; b.y += dt * 0.15;
      if (b.age > b.life) this.bubbles.splice(i, 1);
    }
  }

  render(t) {
    const ctx = this.ctx;
    ctx.save();
    ctx.scale(this.dpr, this.dpr);
    // Ozean
    const grd = ctx.createLinearGradient(0, 0, 0, this.ch);
    grd.addColorStop(0, '#0a2a44'); grd.addColorStop(1, '#06192e');
    ctx.fillStyle = grd; ctx.fillRect(0, 0, this.cw, this.ch);
    ctx.translate(this.view.x, this.view.y);
    ctx.scale(this.view.scale, this.view.scale);

    // Länder
    ctx.lineWidth = 0.4 / this.view.scale;
    ctx.strokeStyle = 'rgba(0,0,0,0.4)';
    for (const c of this.world.countries) {
      const path = this.paths[c.iso];
      ctx.fillStyle = this.countryColor(c.iso);
      ctx.fill(path);
      ctx.stroke(path);
    }
    // Auswahl / Hover hervorheben
    for (const iso of [this.selectedIso, this.hoverIso]) {
      if (!iso || !this.paths[iso]) continue;
      ctx.lineWidth = (iso === this.selectedIso ? 2 : 1.2) / this.view.scale;
      ctx.strokeStyle = iso === this.selectedIso ? '#ffef8a' : '#ffffff';
      ctx.stroke(this.paths[iso]);
    }

    // Verkehr
    if (this.eng) this._drawVehicles(ctx, t);
    // Blasen
    this._drawBubbles(ctx, t);

    ctx.restore();

    // Startland-Marker (im Play-Modus)
    if (this.eng && this.eng.startCountry) this._drawStartMarker(ctx);
  }

  _drawVehicles(ctx, t) {
    const eng = this.eng;
    for (const v of eng.vehicles) {
      if (v.kind === 'air') {
        const [ax, ay] = this.proj(v.ax, v.ay);
        let [bx, by] = this.proj(v.bx, v.by);
        // kürzeste Richtung über die Datumsgrenze
        if (Math.abs(bx - ax) > this.baseW / 2) bx += bx < ax ? this.baseW : -this.baseW;
        const mx = (ax + bx) / 2, my = (ay + by) / 2 - Math.hypot(bx - ax, by - ay) * 0.18;
        const tt = v.t;
        const x = (1 - tt) * (1 - tt) * ax + 2 * (1 - tt) * tt * mx + tt * tt * bx;
        const y = (1 - tt) * (1 - tt) * ay + 2 * (1 - tt) * tt * my + tt * tt * by;
        // Spur
        ctx.strokeStyle = v.infected ? 'rgba(255,80,60,0.35)' : 'rgba(255,255,255,0.12)';
        ctx.lineWidth = 0.6 / this.view.scale;
        ctx.beginPath();
        for (let s = 0; s <= tt; s += 0.1) {
          const px = (1 - s) * (1 - s) * ax + 2 * (1 - s) * s * mx + s * s * bx;
          const py = (1 - s) * (1 - s) * ay + 2 * (1 - s) * s * my + s * s * by;
          if (s === 0) ctx.moveTo(px, py); else ctx.lineTo(px, py);
        }
        ctx.stroke();
        this._icon(ctx, x, y, v.infected ? '#ff6a4a' : '#dfe8ff', 'plane');
      } else {
        const poly = v.poly;
        const seg = this._alongPoly(poly, v.t);
        if (!seg) continue;
        this._icon(ctx, seg[0], seg[1], v.infected ? '#ff7a4a' : '#cfe0ff', 'ship');
      }
    }
  }

  _alongPoly(poly, t) {
    if (!poly || poly.length < 2) return null;
    const pts = poly.map(([lo, la]) => this.proj(lo, la));
    let total = 0; const segs = [];
    for (let i = 1; i < pts.length; i++) { const d = Math.hypot(pts[i][0] - pts[i - 1][0], pts[i][1] - pts[i - 1][1]); segs.push(d); total += d; }
    let target = t * total, acc = 0;
    for (let i = 1; i < pts.length; i++) {
      if (acc + segs[i - 1] >= target) {
        const f = (target - acc) / (segs[i - 1] || 1);
        return [pts[i - 1][0] + (pts[i][0] - pts[i - 1][0]) * f, pts[i - 1][1] + (pts[i][1] - pts[i - 1][1]) * f];
      }
      acc += segs[i - 1];
    }
    return pts[pts.length - 1];
  }

  _icon(ctx, x, y, color, kind) {
    const s = clamp(3 / this.view.scale, 0.9, 3.2);
    ctx.fillStyle = color;
    ctx.beginPath();
    if (kind === 'plane') {
      ctx.arc(x, y, s * 0.9, 0, 7);
    } else {
      ctx.rect(x - s, y - s * 0.5, s * 2, s);
    }
    ctx.fill();
    ctx.save();
    ctx.globalAlpha = 0.4; ctx.beginPath(); ctx.arc(x, y, s * 2, 0, 7); ctx.fillStyle = color; ctx.fill();
    ctx.restore();
  }

  _drawBubbles(ctx, t) {
    for (const b of this.bubbles) {
      const [x, y] = this.proj(b.x, b.y);
      const yy = y - b.age * 2;
      const pulse = 1 + 0.15 * Math.sin(t * 6 + b.age * 4);
      const r = (b.type === 'country' ? 7 : b.type === 'special' ? 8 : 5.5) / this.view.scale * pulse;
      const alpha = clamp(1 - b.age / b.life, 0, 1);
      ctx.globalAlpha = alpha;
      ctx.beginPath(); ctx.arc(x, yy, r, 0, 7);
      const g = ctx.createRadialGradient(x, yy, 0, x, yy, r);
      const col = b.type === 'special' ? '255,120,255' : b.type === 'country' ? '255,190,60' : '255,120,60';
      g.addColorStop(0, `rgba(${col},1)`); g.addColorStop(1, `rgba(${col},0)`);
      ctx.fillStyle = g; ctx.fill();
      ctx.fillStyle = '#fff'; ctx.font = `${5 / this.view.scale}px sans-serif`; ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
      ctx.fillText('DNA', x, yy);
      ctx.globalAlpha = 1;
    }
  }

  _drawStartMarker(ctx) {
    const c = this.byIso[this.eng.startCountry]; if (!c) return;
    ctx.save();
    ctx.translate(this.view.x, this.view.y); ctx.scale(this.view.scale, this.view.scale);
    const [x, y] = this.proj(c.lon, c.lat);
    ctx.strokeStyle = '#ff5a3a'; ctx.lineWidth = 1.4 / this.view.scale;
    ctx.beginPath(); ctx.arc(x, y, 6 / this.view.scale, 0, 7); ctx.stroke();
    ctx.restore();
  }
}
