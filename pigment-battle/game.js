/* PIGMENT KRIEG – Schwarz gegen Weiß
 * Strategiespiel für Handy & PC. Reines Canvas, keine Bibliotheken.
 */
'use strict';

/* ======================= Daten ======================= */
const TAU = Math.PI * 2;
const WORLD_W = 2600;
const SAVE_KEY = 'pigment_krieg_v1';

const UNITS = {
  sword:  { name: 'Klinge', hp: 120, dmg: 18, range: 34, cd: 0.85, atk: 0.62, speed: 80,  cost: 30,  spawnCd: 1.2, scale: 1.0,  desc: 'Schneller Schwertkämpfer' },
  spear:  { name: 'Speer',  hp: 155, dmg: 25, range: 62, cd: 1.10, atk: 0.70, speed: 66,  cost: 50,  spawnCd: 2.0, scale: 1.05, desc: 'Lange Reichweite, stark gegen Titanen' },
  archer: { name: 'Bogen',  hp: 80,  dmg: 16, range: 370, cd: 1.55, atk: 0.95, speed: 70, cost: 55,  spawnCd: 2.2, scale: 1.0,  desc: 'Schießt Pfeile aus der Ferne', ranged: true },
  ninja:  { name: 'Ninja',  hp: 95,  dmg: 12, range: 28, cd: 0.30, atk: 0.26, speed: 135, cost: 70,  spawnCd: 2.6, scale: 0.92, desc: 'Blitzschnelle Doppel-Dolche' },
  giant:  { name: 'Titan',  hp: 720, dmg: 62, range: 60, cd: 2.10, atk: 1.15, speed: 40,  cost: 160, spawnCd: 6.0, scale: 1.75, desc: 'Riesiger Hammer, Flächenschaden', aoe: 85 },
};
const UNIT_KEYS = Object.keys(UNITS);

const TIERS = [
  { name: 'Holz',    color: '#9a6a3a', glow: null },
  { name: 'Eisen',   color: '#c3ccd6', glow: null },
  { name: 'Gold',    color: '#ffcc3d', glow: 'rgba(255,200,60,0.55)' },
  { name: 'Diamant', color: '#7ff4ff', glow: 'rgba(110,240,255,0.75)' },
  { name: 'Plasma',  color: '#ff4df5', glow: 'rgba(255,70,240,0.9)' },
];

const HELMETS = [
  { id: 'none',   name: 'Ohne Helm',      price: 0 },
  { id: 'knight', name: 'Ritterhelm',     price: 120 },
  { id: 'ninja',  name: 'Ninja-Band',     price: 150 },
  { id: 'viking', name: 'Wikingerhelm',   price: 180 },
  { id: 'sparta', name: 'Spartaner',      price: 220 },
  { id: 'face',   name: 'Gesichts-Helm',  price: 250, special: true },
  { id: 'crown',  name: 'Königskrone',    price: 400 },
];

const ARENAS = [
  { id: 'plains',  name: 'Graue Steppe',    diff: 1.0,  reward: 90,  sky: ['#5d6878', '#c7b8a2'], sun: '#ffe9c4', far: '#6a7381', mid: '#565d68', ground: '#6e6a62', ground2: '#5a564f', fx: 'dust' },
  { id: 'volcano', name: 'Vulkankrater',    diff: 1.3,  reward: 130, sky: ['#1e0606', '#b8401a'], sun: '#ff9a3c', far: '#4a1a12', mid: '#2e120d', ground: '#3d2c27', ground2: '#2a1d19', fx: 'embers' },
  { id: 'ice',     name: 'Eiswüste',        diff: 1.55, reward: 170, sky: ['#5b7ea6', '#dce8f3'], sun: '#ffffff', far: '#8ea7c1', mid: '#7590ad', ground: '#8ea3b8', ground2: '#7489a0', fx: 'snow' },
  { id: 'neon',    name: 'Neon-Kolosseum',  diff: 1.8,  reward: 230, sky: ['#0d0820', '#45206b'], sun: '#ff4fd8', far: '#251645', mid: '#1a1033', ground: '#29233d', ground2: '#1d182c', fx: 'neon' },
  { id: 'temple',  name: 'Goldener Tempel', diff: 2.1,  reward: 310, sky: ['#4a2f14', '#e6b25a'], sun: '#fff1b8', far: '#9a7140', mid: '#7a5630', ground: '#8a6a3e', ground2: '#6e5430', fx: 'leaves' },
];

const TEAM = {
  black: { fill: '#0b0b0d', rim: 'rgba(255,255,255,0.42)', ink: ['#000000', '#141414', '#262626'], label: 'Schwarz' },
  white: { fill: '#f6f6f2', rim: 'rgba(0,0,0,0.55)',       ink: ['#ffffff', '#ececec', '#d6d6d6'], label: 'Weiß' },
};

/* ======================= Speicher ======================= */
const DEFAULT_SAVE = {
  coins: 300, team: 'black', arena: 0, helmet: 'none', owned: ['none'],
  wlv: { sword: 1, spear: 1, archer: 1, ninja: 1, giant: 1 },
  armor: 0, mine: 0, tower: 0, wins: 0, sound: true,
};
let save = loadSave();
function loadSave() {
  try {
    const s = JSON.parse(localStorage.getItem(SAVE_KEY));
    if (s) return Object.assign({}, DEFAULT_SAVE, s, { wlv: Object.assign({}, DEFAULT_SAVE.wlv, s.wlv) });
  } catch (e) {}
  return JSON.parse(JSON.stringify(DEFAULT_SAVE));
}
function persist() { try { localStorage.setItem(SAVE_KEY, JSON.stringify(save)); } catch (e) {} }

/* ======================= Gesichts-Helm ======================= */
const faceImg = new Image();
const faceRim = {};
faceImg.onload = () => {
  for (const t of ['black', 'white']) faceRim[t] = makeRimmed(faceImg, t === 'black' ? '#0b0b0d' : '#f6f6f2', 9);
  refreshPreviews();
};
faceImg.src = 'assets/face.png';
function makeRimmed(img, color, pad) {
  const c = document.createElement('canvas');
  c.width = img.width + pad * 2; c.height = img.height + pad * 2;
  const g = c.getContext('2d');
  // Silhouette in Teamfarbe als Helmrand
  for (let a = 0; a < 16; a++) {
    const ang = a / 16 * TAU;
    g.drawImage(img, pad + Math.cos(ang) * pad, pad + Math.sin(ang) * pad);
  }
  g.globalCompositeOperation = 'source-in';
  g.fillStyle = color; g.fillRect(0, 0, c.width, c.height);
  g.globalCompositeOperation = 'source-over';
  // feiner Kontrastring
  const c2 = document.createElement('canvas'); c2.width = c.width + 6; c2.height = c.height + 6;
  const g2 = c2.getContext('2d');
  for (let a = 0; a < 12; a++) { const ang = a / 12 * TAU; g2.drawImage(c, 3 + Math.cos(ang) * 3, 3 + Math.sin(ang) * 3); }
  g2.globalCompositeOperation = 'source-in';
  g2.fillStyle = color === '#0b0b0d' ? 'rgba(255,255,255,0.55)' : 'rgba(0,0,0,0.6)'; g2.fillRect(0, 0, c2.width, c2.height);
  g2.globalCompositeOperation = 'source-over';
  g2.drawImage(c, 3, 3);
  g2.drawImage(img, pad + 3, pad + 3);
  // leichter Helm-Glanz oben
  const gr = g2.createLinearGradient(0, 0, 0, c2.height * 0.5);
  gr.addColorStop(0, 'rgba(255,255,255,0.18)'); gr.addColorStop(1, 'rgba(255,255,255,0)');
  g2.globalCompositeOperation = 'source-atop'; g2.fillStyle = gr; g2.fillRect(0, 0, c2.width, c2.height);
  return c2;
}

/* ======================= Helfer ======================= */
const clamp = (v, a, b) => v < a ? a : v > b ? b : v;
const lerp = (a, b, t) => a + (b - a) * t;
const rand = (a, b) => a + Math.random() * (b - a);
const easeOut = t => 1 - Math.pow(1 - t, 3);
const easeIn = t => t * t * t;
const easeInOut = t => t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
function seg(t, a, b) { return clamp((t - a) / (b - a), 0, 1); }
function pick(arr) { return arr[(Math.random() * arr.length) | 0]; }

/* ======================= Sound ======================= */
const Sfx = {
  ctx: null, master: null,
  init() {
    if (this.ctx) { if (this.ctx.state === 'suspended') this.ctx.resume(); return; }
    try {
      this.ctx = new (window.AudioContext || window.webkitAudioContext)();
      this.master = this.ctx.createGain(); this.master.gain.value = 0.45; this.master.connect(this.ctx.destination);
      const len = this.ctx.sampleRate; const b = this.ctx.createBuffer(1, len, len); const d = b.getChannelData(0);
      for (let i = 0; i < len; i++) d[i] = Math.random() * 2 - 1;
      this.noise = b;
    } catch (e) { this.ctx = null; }
  },
  ok() { return this.ctx && save.sound && this.ctx.state === 'running'; },
  _noise(dur, f0, f1, q, vol, type = 'bandpass') {
    if (!this.ok()) return;
    const t = this.ctx.currentTime;
    const s = this.ctx.createBufferSource(); s.buffer = this.noise;
    const f = this.ctx.createBiquadFilter(); f.type = type; f.Q.value = q;
    f.frequency.setValueAtTime(f0, t); f.frequency.exponentialRampToValueAtTime(f1, t + dur);
    const g = this.ctx.createGain(); g.gain.setValueAtTime(vol, t); g.gain.exponentialRampToValueAtTime(0.001, t + dur);
    s.connect(f); f.connect(g); g.connect(this.master); s.start(t, Math.random() * 0.5); s.stop(t + dur);
  },
  _tone(freq, dur, vol, type = 'sine', f1) {
    if (!this.ok()) return;
    const t = this.ctx.currentTime;
    const o = this.ctx.createOscillator(); o.type = type; o.frequency.setValueAtTime(freq, t);
    if (f1) o.frequency.exponentialRampToValueAtTime(f1, t + dur);
    const g = this.ctx.createGain(); g.gain.setValueAtTime(vol, t); g.gain.exponentialRampToValueAtTime(0.001, t + dur);
    o.connect(g); g.connect(this.master); o.start(t); o.stop(t + dur);
  },
  last: {},
  throttle(k, ms) { const n = performance.now(); if (this.last[k] && n - this.last[k] < ms) return false; this.last[k] = n; return true; },
  swoosh() { if (this.throttle('sw', 40)) this._noise(0.16, 900, 3500, 1.2, 0.18); },
  hit() { if (this.throttle('hit', 30)) { this._noise(0.09, 1800, 300, 0.8, 0.5); this._tone(160, 0.08, 0.25, 'triangle', 60); } },
  clang() { if (this.throttle('cl', 60)) { this._tone(1800 + Math.random() * 600, 0.18, 0.08, 'square', 900); this._noise(0.05, 5000, 3000, 2, 0.2); } },
  boom() { if (this.throttle('bm', 80)) { this._tone(90, 0.5, 0.6, 'sine', 30); this._noise(0.4, 600, 80, 0.7, 0.6, 'lowpass'); } },
  twang() { if (this.throttle('tw', 50)) this._tone(420, 0.12, 0.12, 'triangle', 200); },
  coin() { this._tone(990, 0.08, 0.12, 'square'); setTimeout(() => this._tone(1320, 0.12, 0.1, 'square'), 70); },
  spawn() { if (this.throttle('sp', 60)) this._tone(300, 0.12, 0.1, 'sawtooth', 600); },
  death() { if (this.throttle('dt', 40)) this._noise(0.3, 700, 120, 0.6, 0.35, 'lowpass'); },
};

/* ======================= Stickman-Rig ======================= */
// Winkel: 0 = gerade nach unten, positiv = nach vorne (Blickrichtung), ±π = nach oben
function limb(p, a, L, dir) { return { x: p.x + Math.sin(a) * L * dir, y: p.y + Math.cos(a) * L }; }

function basePose() {
  return { lean: 0, lT: 0.08, lS: -0.05, rT: -0.08, rS: -0.12, lU: 0.15, lF: 0.45, rU: 0.25, rF: 1.0, w: 2.4, w2: 2.4, lunge: 0, crouch: 0, headTilt: 0 };
}

function walkPose(p, q, amp = 1) {
  const s = Math.sin(q), c = Math.cos(q);
  p.lT = 0.55 * s * amp; p.lS = p.lT - (0.12 + 0.95 * Math.max(0, c)) * amp;
  p.rT = -0.55 * s * amp; p.rS = p.rT - (0.12 + 0.95 * Math.max(0, -c)) * amp;
  p.lU = -0.45 * s * amp + 0.1; p.lF = p.lU + 0.5;
  p.lean = 0.12 * amp;
  p.bob = Math.abs(Math.sin(q)) * 2.2 * amp;
  return p;
}

function poseFor(u, time) {
  const p = basePose();
  const type = u.type;
  const moving = u.moving;
  const q = u.phase;
  if (moving) walkPose(p, q, type === 'ninja' ? 1.25 : type === 'giant' ? 0.75 : 1);
  else { const br = Math.sin(time * 2.4 + u.seed) * 0.04; p.lean = 0.04 + br; p.lU = 0.12 + br; p.lF = 0.4; }

  // Haltung je Waffe
  if (type === 'sword') { p.rU = 0.35 + (moving ? 0.25 * Math.sin(q) : 0); p.rF = 1.2; p.w = 2.5; }
  if (type === 'spear') { p.rU = 0.5; p.rF = 1.45; p.lU = 0.95; p.lF = 1.5; p.w = 1.62; }
  if (type === 'archer') { p.lU = 0.9; p.lF = 1.25; p.rU = 0.3; p.rF = 0.9; p.w = 1.3; }
  if (type === 'ninja') { p.rU = 0.6; p.rF = 1.9; p.lU = -0.4; p.lF = 0.4; p.w = 2.1; p.w2 = -2.6; p.lean += 0.22; p.crouch = 3; }
  if (type === 'giant') { p.rU = 0.45; p.rF = 1.6; p.lU = 0.6; p.lF = 1.55; p.w = 2.7; p.lean += 0.05; }

  if (u.state === 'attack') attackPose(u, p, u.atkT);
  if (u.hurt > 0) { const h = u.hurt / 0.22; p.lean -= 0.35 * h; p.headTilt = -0.35 * h; p.lU -= 0.6 * h; }
  if (u.victory) { const v = Math.sin(time * 9 + u.seed); p.rU = -2.9 + v * 0.2; p.rF = -3.0; p.w = -3.1; p.lU = -2.6; p.lF = -2.8; p.lean = -0.1; p.bob = Math.max(0, v) * 6; }
  return p;
}

function attackPose(u, p, t) {
  const type = u.type;
  if (type === 'sword') {
    // Ausholen hinter den Kopf, dann Hieb über oben nach vorne-unten
    if (t < 0.38) { const k = easeOut(seg(t, 0, 0.38)); p.rU = lerp(0.35, -2.5, k); p.rF = p.rU - 0.25; p.w = p.rU - 0.35; p.lean = lerp(0.04, -0.18, k); p.lU = lerp(0.1, 0.9, k); p.lF = p.lU + 0.3; }
    else if (t < 0.56) { const k = easeIn(seg(t, 0.38, 0.56)); p.rU = lerp(-2.5, -4.95, k); p.rF = p.rU + 0.12; p.w = p.rU + 0.25; p.lean = lerp(-0.18, 0.42, k); p.lunge = 9 * k; p.lU = lerp(0.9, -0.5, k); p.lF = p.lU + 0.4; p.lT = lerp(0, 0.55, k); p.lS = p.lT - 0.4; p.rT = lerp(0, -0.5, k); p.rS = p.rT; }
    else { const k = easeInOut(seg(t, 0.56, 1)); p.rU = lerp(1.33, 0.35, k); p.rF = lerp(1.45, 1.2, k); p.w = lerp(1.58, 2.5, k); p.lean = lerp(0.42, 0.04, k); p.lunge = 9 * (1 - k); p.lU = lerp(-0.5, 0.1, k); p.lF = p.lU + 0.4; p.lT = 0.55 * (1 - k); p.lS = p.lT - 0.4 * (1 - k); p.rT = -0.5 * (1 - k); p.rS = p.rT; }
  } else if (type === 'spear') {
    if (t < 0.45) { const k = easeOut(seg(t, 0, 0.45)); p.rU = lerp(0.5, -0.25, k); p.rF = 1.5; p.lU = lerp(0.95, 0.35, k); p.lF = 1.5; p.lean = lerp(0, -0.2, k); p.lunge = -6 * k; p.rT = -0.3 * k; p.rS = p.rT - 0.2 * k; }
    else if (t < 0.6) { const k = easeIn(seg(t, 0.45, 0.6)); p.rU = lerp(-0.25, 1.4, k); p.rF = 1.57; p.lU = lerp(0.35, 1.5, k); p.lF = 1.57; p.lean = lerp(-0.2, 0.45, k); p.lunge = lerp(-6, 16, k); p.lT = 0.7 * k; p.lS = p.lT - 0.6 * k; p.rT = -0.6 * k; p.rS = p.rT; }
    else { const k = easeInOut(seg(t, 0.6, 1)); p.rU = lerp(1.4, 0.5, k); p.lU = lerp(1.5, 0.95, k); p.rF = 1.5; p.lF = 1.5; p.lean = lerp(0.45, 0, k); p.lunge = 16 * (1 - k); p.lT = 0.7 * (1 - k); p.lS = p.lT - 0.6 * (1 - k); p.rT = -0.6 * (1 - k); p.rS = p.rT; }
    p.w = 1.6 + (p.lean * 0.15);
  } else if (type === 'archer') {
    p.lU = 1.5; p.lF = 1.56; p.w = 1.56; p.lean = 0.02;
    if (t < 0.6) { const k = easeOut(seg(t, 0, 0.6)); p.rU = lerp(0.9, 1.25, k); p.rF = lerp(1.5, -1.45, k); p.draw = k; p.lean = -0.08 * k; p.rT = -0.25 * k; p.lT = 0.2 * k; }
    else { const k = easeOut(seg(t, 0.6, 1)); p.rU = lerp(1.0, 0.4, k); p.rF = lerp(-1.6, 0.9, k); p.draw = 0; p.lean = lerp(-0.12, 0.02, k); p.rT = -0.25 * (1 - k); p.lT = 0.2 * (1 - k); }
  } else if (type === 'ninja') {
    const alt = u.combo % 2 === 0;
    const k1 = seg(t, 0, 0.35), k2 = seg(t, 0.35, 0.65), k3 = seg(t, 0.65, 1);
    let a = k2 > 0 ? (k3 > 0 ? 1.65 * (1 - easeInOut(k3)) + 0.6 * easeInOut(k3) : lerp(-0.4, 1.65, easeIn(k2))) : lerp(0.6, -0.4, easeOut(k1));
    if (alt) { p.rU = a; p.rF = a + 0.15; p.w = a + 0.1; } else { p.lU = a; p.lF = a + 0.15; p.w2 = a + 0.1; }
    p.lean = 0.3 + (k2 > 0 && k3 === 0 ? 0.2 : 0); p.lunge = k2 > 0 ? 8 * (1 - k3) : 0;
    if (u.combo % 4 === 3) { p.spin = easeInOut(seg(t, 0.1, 0.9)) * TAU; p.bob = Math.sin(seg(t, 0, 1) * Math.PI) * 18; }
  } else if (type === 'giant') {
    if (t < 0.48) { const k = easeOut(seg(t, 0, 0.48)); p.rU = lerp(0.45, -2.9, k); p.lU = lerp(0.6, -2.75, k); p.rF = p.rU - 0.15; p.lF = p.lU - 0.1; p.w = p.rU - 0.2; p.lean = lerp(0.05, -0.28, k); p.bob = 4 * k; }
    else if (t < 0.6) { const k = easeIn(seg(t, 0.48, 0.6)); p.rU = lerp(-2.9, -4.7, k); p.lU = lerp(-2.75, -4.65, k); p.rF = p.rU + 0.1; p.lF = p.lU + 0.1; p.w = p.rU + 0.15; p.lean = lerp(-0.28, 0.6, k); p.crouch = 8 * k; p.lT = 0.5 * k; p.lS = p.lT - 0.7 * k; p.rT = -0.5 * k; p.rS = p.rT - 0.3 * k; }
    else { const k = easeInOut(seg(t, 0.6, 1)); p.rU = lerp(1.58, 0.45, k); p.lU = lerp(1.63, 0.6, k); p.rF = lerp(1.68, 1.6, k); p.lF = lerp(1.73, 1.55, k); p.w = lerp(1.73, 2.7, k); p.lean = lerp(0.6, 0.05, k); p.crouch = 8 * (1 - k); p.lT = 0.5 * (1 - k); p.lS = p.lT - 0.7 * (1 - k); p.rT = -0.5 * (1 - k); p.rS = p.rT - 0.3 * (1 - k); }
  }
}

// berechnet Gelenke relativ zu den Füßen (0,0)
function solve(p, s, dir) {
  const TH = 15 * s, SH = 15 * s, TO = 24 * s, UA = 12 * s, FA = 12 * s;
  const hip0 = { x: 0, y: 0 };
  const lK = limb(hip0, p.lT, TH, dir), lFt = limb(lK, p.lS, SH, dir);
  const rK = limb(hip0, p.rT, TH, dir), rFt = limb(rK, p.rS, SH, dir);
  const lowest = Math.max(lFt.y, rFt.y);
  const oy = -lowest - (p.bob || 0) + (p.crouch || 0) * s;
  const ox = (p.lunge || 0) * s * dir;
  const sh = (q) => ({ x: q.x + ox, y: q.y + oy });
  const hip = sh(hip0);
  const J = { hip, lK: sh(lK), lFt: sh(lFt), rK: sh(rK), rFt: sh(rFt) };
  J.neck = { x: hip.x + Math.sin(p.lean) * TO * dir, y: hip.y - Math.cos(p.lean) * TO };
  J.sh = { x: lerp(hip.x, J.neck.x, 0.9), y: lerp(hip.y, J.neck.y, 0.9) };
  J.lE = limb(J.sh, p.lU, UA, dir); J.lH = limb(J.lE, p.lF, FA, dir);
  J.rE = limb(J.sh, p.rU, UA, dir); J.rH = limb(J.rE, p.rF, FA, dir);
  const hr = 7.6 * s, ha = p.lean + (p.headTilt || 0);
  J.headR = hr;
  J.head = { x: J.neck.x + Math.sin(ha) * (hr + 1.5 * s) * dir, y: J.neck.y - Math.cos(ha) * (hr + 1.5 * s) };
  J.headA = ha;
  return J;
}

/* ---------- Waffen ---------- */
function drawWeapon(g, type, hand, ang, s, dir, tier, p, J, isSecond) {
  const T = TIERS[tier];
  const dx = Math.sin(ang) * dir, dy = Math.cos(ang);
  const px = -dy, py = dx; // senkrecht
  g.save();
  if (T.glow) { g.shadowColor = T.glow; g.shadowBlur = 10 * s; }
  g.lineCap = 'round';
  const line = (x1, y1, x2, y2, w, c) => { g.strokeStyle = c; g.lineWidth = w; g.beginPath(); g.moveTo(x1, y1); g.lineTo(x2, y2); g.stroke(); };
  if (type === 'sword') {
    const L = 28 * s;
    line(hand.x - dx * 4 * s, hand.y - dy * 4 * s, hand.x, hand.y, 3 * s, '#3a2a1c');
    line(hand.x + px * 5 * s, hand.y + py * 5 * s, hand.x - px * 5 * s, hand.y - py * 5 * s, 2.6 * s, '#5a4632');
    // Klinge als Keil
    g.fillStyle = T.color; g.beginPath();
    g.moveTo(hand.x + px * 2.2 * s, hand.y + py * 2.2 * s);
    g.lineTo(hand.x + dx * L + px * 0.6 * s, hand.y + dy * L + py * 0.6 * s);
    g.lineTo(hand.x + dx * (L + 4 * s), hand.y + dy * (L + 4 * s));
    g.lineTo(hand.x - px * 2.2 * s, hand.y - py * 2.2 * s); g.closePath(); g.fill();
    g.shadowBlur = 0; line(hand.x + dx * 3 * s, hand.y + dy * 3 * s, hand.x + dx * (L - 2 * s), hand.y + dy * (L - 2 * s), 0.9 * s, 'rgba(255,255,255,0.65)');
  } else if (type === 'spear') {
    const back = 26 * s, fwd = 40 * s;
    line(hand.x - dx * back, hand.y - dy * back, hand.x + dx * fwd, hand.y + dy * fwd, 2.4 * s, '#6b4a2a');
    g.fillStyle = T.color; g.beginPath();
    const tx = hand.x + dx * (fwd + 12 * s), ty = hand.y + dy * (fwd + 12 * s);
    g.moveTo(tx, ty); g.lineTo(hand.x + dx * fwd + px * 3.6 * s, hand.y + dy * fwd + py * 3.6 * s);
    g.lineTo(hand.x + dx * (fwd - 2 * s), hand.y + dy * (fwd - 2 * s)); g.lineTo(hand.x + dx * fwd - px * 3.6 * s, hand.y + dy * fwd - py * 3.6 * s); g.closePath(); g.fill();
    if (tier >= 2) { g.fillStyle = '#c0392b'; g.beginPath(); g.arc(hand.x + dx * (fwd - 4 * s), hand.y + dy * (fwd - 4 * s), 2 * s, 0, TAU); g.fill(); }
  } else if (type === 'archer') {
    // Bogen an der linken Hand, Sehne zur rechten Hand beim Spannen
    const R = 17 * s;
    const top = { x: hand.x - px * R * dir * dir + dx * -2 * s, y: hand.y - py * R };
    const a0 = Math.atan2(dy, dx);
    g.strokeStyle = tier === 0 ? '#7a4f26' : T.color; g.lineWidth = 2.6 * s;
    g.beginPath(); g.arc(hand.x - dx * 7 * s, hand.y - dy * 7 * s, R, a0 - 1.15, a0 + 1.15); g.stroke();
    const e1 = { x: hand.x - dx * 7 * s + Math.cos(a0 - 1.15) * R, y: hand.y - dy * 7 * s + Math.sin(a0 - 1.15) * R };
    const e2 = { x: hand.x - dx * 7 * s + Math.cos(a0 + 1.15) * R, y: hand.y - dy * 7 * s + Math.sin(a0 + 1.15) * R };
    const pull = (p.draw > 0) ? J.rH : { x: (e1.x + e2.x) / 2, y: (e1.y + e2.y) / 2 };
    g.shadowBlur = 0; g.strokeStyle = 'rgba(230,230,230,0.8)'; g.lineWidth = 0.9 * s;
    g.beginPath(); g.moveTo(e1.x, e1.y); g.lineTo(pull.x, pull.y); g.lineTo(e2.x, e2.y); g.stroke();
    if (p.draw > 0.05) { // Pfeil eingelegt
      line(pull.x, pull.y, pull.x + dx * 30 * s, pull.y + dy * 30 * s, 1.4 * s, '#d8c9a8');
      g.fillStyle = T.color; g.beginPath(); g.arc(pull.x + dx * 30 * s, pull.y + dy * 30 * s, 1.8 * s, 0, TAU); g.fill();
    }
  } else if (type === 'ninja') {
    const L = 14 * s;
    line(hand.x, hand.y, hand.x - dx * 3 * s, hand.y - dy * 3 * s, 2.6 * s, '#222');
    g.fillStyle = T.color; g.beginPath();
    g.moveTo(hand.x + px * 1.8 * s, hand.y + py * 1.8 * s); g.lineTo(hand.x + dx * L, hand.y + dy * L); g.lineTo(hand.x - px * 1.8 * s, hand.y - py * 1.8 * s); g.closePath(); g.fill();
  } else if (type === 'giant') {
    const L = 34 * s;
    line(hand.x - dx * 6 * s, hand.y - dy * 6 * s, hand.x + dx * L, hand.y + dy * L, 3 * s, '#4e3420');
    const hx = hand.x + dx * L, hy = hand.y + dy * L;
    g.save(); g.translate(hx, hy); g.rotate(Math.atan2(dy, dx));
    g.fillStyle = tier === 0 ? '#6d6d6d' : T.color;
    roundRect(g, -6 * s, -11 * s, 12 * s, 22 * s, 2.5 * s); g.fill();
    g.fillStyle = 'rgba(0,0,0,0.25)'; g.fillRect(-6 * s, -2 * s, 12 * s, 4 * s);
    g.restore();
  }
  g.restore();
}
function roundRect(g, x, y, w, h, r) { g.beginPath(); g.moveTo(x + r, y); g.arcTo(x + w, y, x + w, y + h, r); g.arcTo(x + w, y + h, x, y + h, r); g.arcTo(x, y + h, x, y, r); g.arcTo(x, y, x + w, y, r); g.closePath(); }

function weaponTip(type, J, p, s, dir) {
  const L = type === 'sword' ? 31 : type === 'spear' ? 52 : type === 'giant' ? 40 : type === 'ninja' ? 14 : 0;
  return { x: J.rH.x + Math.sin(p.w) * L * s * dir, y: J.rH.y + Math.cos(p.w) * L * s };
}

/* ---------- Helme ---------- */
function drawHelmet(g, id, J, s, dir, teamKey, time) {
  const h = J.head, r = J.headR;
  if (id === 'face' && faceRim[teamKey]) {
    const img = faceRim[teamKey];
    const H = r * 4.2, W = H * img.width / img.height;
    g.save();
    g.translate(h.x, h.y - r * 0.9);
    g.rotate(J.headA * dir * 0.6);
    if (dir < 0) g.scale(-1, 1);
    g.drawImage(img, -W / 2, -H / 2, W, H);
    g.restore();
    return true; // ersetzt Kopf
  }
  g.save(); g.translate(h.x, h.y); g.rotate(J.headA * dir); g.scale(dir, 1);
  if (id === 'knight') {
    const gr = g.createLinearGradient(-r, -r, r, r); gr.addColorStop(0, '#e9eef3'); gr.addColorStop(0.5, '#9aa6b2'); gr.addColorStop(1, '#5d6670');
    g.fillStyle = gr; g.beginPath(); g.arc(0, 0, r * 1.18, Math.PI * 0.95, Math.PI * 2.05); g.lineTo(r * 1.18, r * 0.75); g.lineTo(-r * 1.18, r * 0.75); g.closePath(); g.fill();
    g.fillStyle = '#111'; g.fillRect(r * 0.05, -r * 0.18, r * 1.15, r * 0.2);
    g.strokeStyle = '#4b535c'; g.lineWidth = 0.8 * s; g.beginPath(); g.moveTo(0, -r * 1.15); g.lineTo(0, r * 0.7); g.stroke();
    g.fillStyle = '#c0392b'; g.beginPath(); g.moveTo(-r * 0.2, -r * 1.1); g.quadraticCurveTo(-r * 1.6, -r * 1.9, -r * 1.9, -r * 0.4); g.quadraticCurveTo(-r * 1.0, -r * 1.1, -r * 0.2, -r * 1.1); g.fill();
  } else if (id === 'viking') {
    g.fillStyle = '#8f9aa5'; g.beginPath(); g.arc(0, -r * 0.1, r * 1.12, Math.PI, TAU); g.closePath(); g.fill();
    g.fillStyle = '#6b4a2a'; g.fillRect(-r * 1.15, -r * 0.2, r * 2.3, r * 0.35);
    g.fillStyle = '#efe6cf';
    for (const sx of [-1, 1]) { g.beginPath(); g.moveTo(sx * r * 0.85, -r * 0.4); g.quadraticCurveTo(sx * r * 2.1, -r * 0.6, sx * r * 1.9, -r * 2.0); g.quadraticCurveTo(sx * r * 1.55, -r * 1.0, sx * r * 0.6, -r * 0.85); g.closePath(); g.fill(); }
  } else if (id === 'ninja') {
    g.fillStyle = '#c4161c'; g.fillRect(-r * 1.05, -r * 0.55, r * 2.1, r * 0.42);
    const wv = Math.sin(time * 14) * r * 0.25;
    g.beginPath(); g.moveTo(-r * 0.95, -r * 0.4); g.quadraticCurveTo(-r * 2, -r * 0.6 + wv, -r * 2.9, -r * 0.1 - wv); g.lineTo(-r * 2.7, r * 0.15 - wv); g.quadraticCurveTo(-r * 1.9, -r * 0.2 + wv, -r * 0.95, -r * 0.2); g.fill();
    g.beginPath(); g.moveTo(-r * 0.95, -r * 0.35); g.quadraticCurveTo(-r * 1.8, r * 0.1 - wv, -r * 2.4, r * 0.6 + wv); g.lineTo(-r * 2.2, r * 0.75 + wv); g.quadraticCurveTo(-r * 1.6, 0, -r * 0.95, -r * 0.2); g.fill();
    g.fillStyle = '#eee'; g.beginPath(); g.arc(r * 0.2, -r * 0.34, r * 0.13, 0, TAU); g.fill();
  } else if (id === 'sparta') {
    const gr = g.createLinearGradient(0, -r, 0, r); gr.addColorStop(0, '#f3c55b'); gr.addColorStop(1, '#a8741c');
    g.fillStyle = gr; g.beginPath(); g.arc(0, 0, r * 1.15, Math.PI * 0.9, Math.PI * 2.1); g.lineTo(r * 1.1, r * 1.0); g.lineTo(r * 0.35, r * 1.0); g.lineTo(r * 0.35, r * 0.1); g.lineTo(-r * 1.15, r * 0.6); g.closePath(); g.fill();
    g.fillStyle = '#111'; g.fillRect(r * 0.35, -r * 0.2, r * 0.8, r * 0.28); g.fillRect(r * 0.55, -r * 0.2, r * 0.22, r * 1.1);
    g.fillStyle = '#b3141b'; g.beginPath(); g.moveTo(r * 0.9, -r * 0.9);
    for (let i = 0; i <= 10; i++) { const a = Math.PI * 1.75 - i * 0.12; const rr = r * (1.9 + (i % 2) * 0.12); g.lineTo(Math.cos(a) * rr * 0.9 - r * 0.2, Math.sin(a) * rr * 0.85 + r * 0.2); }
    g.lineTo(-r * 1.0, -r * 0.6); g.closePath(); g.fill();
  } else if (id === 'crown') {
    const gr = g.createLinearGradient(0, -r * 2, 0, -r * 0.4); gr.addColorStop(0, '#fff3a6'); gr.addColorStop(1, '#d99a12');
    g.shadowColor = 'rgba(255,210,80,0.8)'; g.shadowBlur = 8 * s;
    g.fillStyle = gr; g.beginPath(); g.moveTo(-r * 1.0, -r * 0.55); g.lineTo(-r * 1.1, -r * 1.75); g.lineTo(-r * 0.55, -r * 1.15); g.lineTo(0, -r * 1.95); g.lineTo(r * 0.55, -r * 1.15); g.lineTo(r * 1.1, -r * 1.75); g.lineTo(r * 1.0, -r * 0.55); g.closePath(); g.fill();
    g.shadowBlur = 0;
    g.fillStyle = '#e0204a'; g.beginPath(); g.arc(0, -r * 0.95, r * 0.2, 0, TAU); g.fill();
    g.fillStyle = '#35c4ff'; g.beginPath(); g.arc(-r * 0.6, -r * 0.85, r * 0.13, 0, TAU); g.arc(r * 0.6, -r * 0.85, r * 0.13, 0, TAU); g.fill();
  }
  g.restore();
  return false;
}

/* ---------- ganzer Stickman ---------- */
function drawStick(g, x, y, u, p, time, opts = {}) {
  const s = u.scale, dir = u.dir;
  const teamKey = u.color;
  const C = TEAM[teamKey];
  const J = solve(p, s, dir);
  const flash = u.flash > 0;
  const body = flash ? (teamKey === 'black' ? '#7a1010' : '#ff9a9a') : C.fill;
  const lw = 4.2 * s, tw = 5.2 * s;

  g.save(); g.translate(x, y);
  if (p.spin) { g.translate(J.hip.x, J.hip.y); g.rotate(p.spin * dir); g.translate(-J.hip.x, -J.hip.y); }

  const bones = [
    [J.hip, J.lK, lw], [J.lK, J.lFt, lw], [J.hip, J.rK, lw], [J.rK, J.rFt, lw],
    [J.hip, J.neck, tw], [J.sh, J.lE, lw * 0.92], [J.lE, J.lH, lw * 0.92], [J.sh, J.rE, lw * 0.92], [J.rE, J.rH, lw * 0.92],
  ];
  const back = [0, 1, 5, 6]; // hinteres Bein/Arm zuerst
  const tier = u.tier;

  // hintere Waffe (Ninja links, Bogen)
  if (u.type === 'ninja') drawWeapon(g, 'ninja', J.lH, p.w2, s, dir, tier, p, J, true);

  g.lineCap = 'round'; g.lineJoin = 'round';
  // Kontrastkontur
  g.strokeStyle = C.rim;
  for (const [a, b, w] of bones) { g.lineWidth = w + 2.2 * s; g.beginPath(); g.moveTo(a.x, a.y); g.lineTo(b.x, b.y); g.stroke(); }
  // Füllung
  for (let i = 0; i < bones.length; i++) {
    const [a, b, w] = bones[i];
    g.strokeStyle = back.includes(i) ? shade(body, teamKey) : body; g.lineWidth = w;
    g.beginPath(); g.moveTo(a.x, a.y); g.lineTo(b.x, b.y); g.stroke();
  }
  // Füße & Hände als kleine Punkte
  g.fillStyle = body;
  for (const q of [J.lH, J.rH]) { g.beginPath(); g.arc(q.x, q.y, lw * 0.62, 0, TAU); g.fill(); }

  // Waffe vorne
  if (u.type === 'archer') drawWeapon(g, 'archer', J.lH, p.w, s, dir, tier, p, J);
  else drawWeapon(g, u.type, J.rH, p.w, s, dir, tier, p, J);

  // Kopf
  const helmet = u.helmet || 'none';
  let replaced = false;
  if (helmet === 'face') replaced = drawHelmet(g, 'face', J, s, dir, teamKey, time);
  if (!replaced) {
    g.fillStyle = C.rim; g.beginPath(); g.arc(J.head.x, J.head.y, J.headR + 1.2 * s, 0, TAU); g.fill();
    g.fillStyle = body; g.beginPath(); g.arc(J.head.x, J.head.y, J.headR, 0, TAU); g.fill();
    // Augen-Schlitz für Ausdruck
    if (helmet === 'none' || helmet === 'crown' || helmet === 'viking' || helmet === 'ninja') {
      g.fillStyle = teamKey === 'black' ? 'rgba(255,255,255,0.9)' : 'rgba(0,0,0,0.85)';
      const ex = J.head.x + dir * J.headR * 0.42, ey = J.head.y - J.headR * 0.15;
      g.save(); g.translate(ex, ey); g.rotate(J.headA * dir); g.fillRect(-1.6 * s, -0.8 * s, 3.4 * s, 1.6 * s); g.restore();
    }
    if (helmet !== 'none') drawHelmet(g, helmet, J, s, dir, teamKey, time);
  }
  g.restore();
  return J;
}
function shade(c, team) { return team === 'black' ? '#1f1f24' : '#cfcfc8'; }

/* ======================= Kampf ======================= */
class Battle {
  constructor(arenaIdx, playerColor, demo = false) {
    this.arena = ARENAS[arenaIdx];
    this.arenaIdx = arenaIdx;
    this.demo = demo;
    this.colors = [playerColor, playerColor === 'black' ? 'white' : 'black'];
    this.units = []; this.parts = []; this.decals = []; this.projs = []; this.trails = []; this.texts = [];
    this.time = 0; this.over = false; this.result = null; this.kills = 0;
    this.gold = [demo ? 200 : 120, 120];
    this.income = [demo ? 13 : 11 + save.mine * 3, 10 * (0.85 + this.arena.diff * 0.25)];
    this.cmd = ['attack', 'attack'];
    this.spawnCd = [{}, {}];
    const d = this.arena.diff;
    this.bases = [
      { team: 0, x: 130, hp: 2400 * (1 + (demo ? 0 : save.tower * 0.15)), maxHp: 0, flash: 0, shoot: 0, tower: demo ? 1 : save.tower },
      { team: 1, x: WORLD_W - 130, hp: 2200 * (0.8 + d * 0.25), maxHp: 0, flash: 0, shoot: 0, tower: Math.round(d * 1.5) - 1 },
    ];
    for (const b of this.bases) b.maxHp = b.hp;
    this.ai = [{ save: 60, next: 2 }, { save: 80, next: 3 }];
    this.shake = 0; this.hitStop = 0; this.slowmo = 0;
    this.amb = [];
    this.seedScenery();
  }
  seedScenery() {
    const R = mulberry(this.arenaIdx * 977 + 13);
    this.far = []; this.mid = []; this.props = [];
    for (let x = -200; x < WORLD_W + 1400; x += 60) this.far.push({ x, h: 120 + R() * 140 + Math.sin(x * 0.004) * 60 });
    for (let x = -200; x < WORLD_W + 800; x += 40) this.mid.push({ x, h: 50 + R() * 70 + Math.sin(x * 0.009 + 2) * 30 });
    const kinds = { plains: ['rock', 'grass', 'tree'], volcano: ['rock', 'spike', 'lava'], ice: ['crystal', 'rock', 'pine'], neon: ['pillar', 'lamp', 'pillar'], temple: ['column', 'statue', 'grass'] }[this.arena.id];
    for (let x = 330; x < WORLD_W - 330; x += 110 + R() * 160) this.props.push({ x, k: kinds[(R() * kinds.length) | 0], s: 0.7 + R() * 0.6, back: R() < 0.6 });
  }

  /* ---------- Einheiten ---------- */
  canSpawn(team, type) {
    const def = UNITS[type];
    return !this.over && this.gold[team] >= def.cost && !(this.spawnCd[team][type] > 0) && this.units.filter(u => u.team === team && !u.dead).length < 60;
  }
  spawn(team, type) {
    if (!this.canSpawn(team, type)) return false;
    const def = UNITS[type];
    this.gold[team] -= def.cost; this.spawnCd[team][type] = def.spawnCd;
    const player = team === 0 && !this.demo;
    const diff = this.arena.diff;
    const tier = player ? save.wlv[type] - 1 : clamp(this.demo ? (Math.random() * 5) | 0 : this.arenaIdx, 0, 4);
    const hpMul = player ? 1 + save.armor * 0.15 : (this.demo ? 1 : 0.85 + diff * 0.2);
    const dmgMul = 1 + tier * 0.28;
    const b = this.bases[team];
    const u = {
      team, type, def, dir: team === 0 ? 1 : -1, color: this.colors[team],
      x: b.x + (team === 0 ? 50 : -50), z: rand(0, 34), hp: def.hp * hpMul, maxHp: def.hp * hpMul,
      dmg: def.dmg * dmgMul, cdLeft: 0.3, phase: Math.random() * TAU, seed: Math.random() * 100,
      state: 'walk', atkT: 0, flash: 0, hurt: 0, kbx: 0, scale: def.scale, tier, moving: true, combo: 0,
      helmet: player ? save.helmet : (this.demo ? pick(['none', 'knight', 'viking', 'ninja', 'sparta', 'crown', 'none']) : (this.arenaIdx >= 3 ? 'knight' : 'none')),
      spawnT: 0.35,
    };
    if (this.demo && team === 0 && save.helmet === 'face' && Math.random() < 0.6) u.helmet = 'face';
    this.units.push(u);
    for (let i = 0; i < 14; i++) this.parts.push({ k: 'ink', x: u.x + rand(-10, 10), y: -rand(0, 60), z: u.z, vx: rand(-80, 80), vy: rand(-160, 0), life: rand(0.3, 0.6), max: 0.6, r: rand(2, 5), c: pick(TEAM[u.color].ink) });
    if (player) Sfx.spawn();
    return true;
  }

  enemiesOf(team) { return this.units.filter(u => u.team !== team && !u.dead); }

  update(dt) {
    if (this.hitStop > 0) { this.hitStop -= dt; dt *= 0.06; }
    if (this.slowmo > 0) { this.slowmo -= dt; dt *= 0.35; }
    this.time += dt;
    for (let t = 0; t < 2; t++) {
      this.gold[t] += this.income[t] * dt;
      for (const k in this.spawnCd[t]) this.spawnCd[t][k] -= dt;
    }
    if (!this.over) { this.runAI(1, dt); if (this.demo) this.runAI(0, dt); }

    for (const u of this.units) this.updateUnit(u, dt);
    this.units = this.units.filter(u => !(u.dead && u.dead.t > 6));
    for (const b of this.bases) this.updateBase(b, dt);
    this.updateProjs(dt);
    this.updateParts(dt);
    this.updateAmbient(dt);
    for (const tr of this.trails) tr.age += dt;
    this.trails = this.trails.filter(t => t.age < 0.22);
    this.shake *= Math.pow(0.0015, dt);
    if (this.decals.length > 260) this.decals.splice(0, this.decals.length - 260);

    if (!this.over) {
      if (this.bases[1].hp <= 0) this.finish(0);
      else if (this.bases[0].hp <= 0) this.finish(1);
    }
  }

  finish(winner) {
    this.over = true; this.result = winner; this.slowmo = 1.4; this.endT = this.time;
    const b = this.bases[1 - winner];
    for (let i = 0; i < 120; i++) this.parts.push({ k: 'ink', x: b.x + rand(-40, 40), y: -rand(20, 220), z: rand(0, 30), vx: rand(-500, 500), vy: rand(-700, -100), life: rand(0.8, 2), max: 2, r: rand(3, 9), c: pick(TEAM[this.colors[1 - winner]].ink) });
    this.parts.push({ k: 'ring', x: b.x, y: -60, z: 10, life: 0.8, max: 0.8, r: 300 });
    this.shake = 26; Sfx.boom();
    for (const u of this.units) if (!u.dead && u.team === winner) u.victory = true;
    for (const u of this.units) if (!u.dead && u.team !== winner) this.kill(u, winner === 0 ? 1 : -1, true);
  }

  runAI(team, dt) {
    const ai = this.ai[team];
    ai.next -= dt;
    if (ai.next > 0) return;
    ai.next = rand(0.4, 1.0);
    const g = this.gold[team];
    const foes = this.enemiesOf(team);
    const mine = this.units.filter(u => u.team === team && !u.dead);
    const threat = foes.filter(f => Math.abs(f.x - this.bases[team].x) < 700).length;
    if (g < ai.save && threat < 3) return;
    // Konter-Auswahl
    const cnt = {}; for (const f of foes) cnt[f.type] = (cnt[f.type] || 0) + 1;
    const w = { sword: 4, spear: 3 + (cnt.giant || 0) * 3, archer: 3 + (cnt.sword || 0) * 0.5, ninja: 2 + (cnt.archer || 0) * 1.5, giant: (g > 220 ? 3 : 0.5) + (cnt.sword || 0) * 0.6 };
    if (mine.filter(u => u.type === 'archer').length > 6) w.archer = 0.5;
    let tot = 0; for (const k in w) tot += w[k];
    let r = Math.random() * tot, type = 'sword';
    for (const k in w) { r -= w[k]; if (r <= 0) { type = k; break; } }
    if (!this.spawn(team, type)) { if (g > 40) this.spawn(team, 'sword'); }
    else if (this.gold[team] < 30) ai.save = rand(60, 260);
  }

  updateUnit(u, dt) {
    if (u.dead) { this.updateDead(u, dt); return; }
    u.flash -= dt; u.hurt = Math.max(0, u.hurt - dt); u.cdLeft -= dt; u.spawnT = Math.max(0, u.spawnT - dt);
    if (Math.abs(u.kbx) > 2) { u.x += u.kbx * dt; u.kbx *= Math.pow(0.004, dt); }
    u.x = clamp(u.x, 40, WORLD_W - 40);
    if (u.victory) { u.moving = false; return; }

    if (u.state === 'attack') {
      const prev = u.atkT;
      u.atkT += dt / u.atkDur;
      this.recordTrail(u);
      if (!u.hitDone && u.atkT >= u.hitAt) { u.hitDone = true; this.doHit(u); }
      if (u.type === 'sword' && prev < 0.38 && u.atkT >= 0.38) Sfx.swoosh();
      if (u.atkT >= 1) { u.state = 'idle'; u.combo++; }
      u.moving = false; return;
    }

    const def = u.def;
    const target = this.findTarget(u);
    const eb = this.bases[1 - u.team];
    let goal = null;
    if (target) {
      const dist = Math.abs(target.x - u.x) - (target.isBase ? 46 : 0);
      if (dist <= def.range) {
        u.moving = false; u.dir = target.x >= u.x ? 1 : -1;
        if (u.cdLeft <= 0) this.startAttack(u, target);
        return;
      }
      goal = target.x;
    }
    if (goal === null) {
      const cmd = this.cmd[u.team];
      if (cmd === 'defend') {
        const rally = this.bases[u.team].x + u.dir * (260 + (u.seed % 1) * 60 + (def.ranged ? -60 : 0));
        goal = Math.abs(rally - u.x) > 12 ? rally : null;
      } else goal = eb.x;
    }
    if (goal === null) { u.moving = false; u.dir = u.team === 0 ? 1 : -1; return; }
    const d = goal > u.x ? 1 : -1;
    u.dir = d; u.moving = true;
    const sp = def.speed * (u.spawnT > 0 ? 0.5 : 1);
    u.x += d * sp * dt;
    u.phase += dt * sp * 0.11 / u.scale;
  }

  findTarget(u) {
    const def = u.def;
    const aggro = def.ranged ? def.range + 30 : 260;
    let best = null, bd = 1e9;
    const defending = this.cmd[u.team] === 'defend';
    const home = this.bases[u.team].x;
    for (const e of this.units) {
      if (e.team === u.team || e.dead) continue;
      const d = Math.abs(e.x - u.x);
      if (d > aggro) continue;
      if (defending && Math.abs(e.x - home) > 560) continue;
      const score = d + (Math.sign(e.x - u.x) !== (u.team === 0 ? 1 : -1) ? 40 : 0);
      if (score < bd) { bd = score; best = e; }
    }
    if (best) return best;
    const eb = this.bases[1 - u.team];
    if (Math.abs(eb.x - u.x) < def.range + 50 + (def.ranged ? 0 : 10)) return { isBase: true, x: eb.x, base: eb };
    return null;
  }

  startAttack(u, t) {
    u.state = 'attack'; u.atkT = 0; u.hitDone = false; u.target = t;
    u.atkDur = u.def.atk * (1 - u.tier * 0.04);
    u.hitAt = { sword: 0.5, spear: 0.55, archer: 0.62, ninja: 0.5, giant: 0.58 }[u.type];
    u.cdLeft = u.def.cd * (1 - u.tier * 0.05) + rand(0, 0.15);
    u.trailPts = [];
    if (u.type === 'giant') Sfx.swoosh();
  }

  recordTrail(u) {
    if (u.type === 'archer') return;
    const win = { sword: [0.36, 0.62], spear: [0.47, 0.62], ninja: [0.35, 0.66], giant: [0.46, 0.62] }[u.type];
    if (u.atkT < win[0] || u.atkT > win[1]) return;
    const p = poseFor(u, this.time);
    const J = solve(p, u.scale, u.dir);
    let tip, hand;
    if (u.type === 'ninja') {
      const alt = u.combo % 2 === 0; hand = alt ? J.rH : J.lH; const a = alt ? p.w : p.w2;
      tip = { x: hand.x + Math.sin(a) * 14 * u.scale * u.dir, y: hand.y + Math.cos(a) * 14 * u.scale };
    } else { hand = J.rH; tip = weaponTip(u.type, J, p, u.scale, u.dir); }
    const base = { x: hand.x + (tip.x - hand.x) * 0.35, y: hand.y + (tip.y - hand.y) * 0.35 };
    this.trails.push({ u, x: u.x, z: u.z, tip, base, age: 0, color: TIERS[u.tier].glow || (u.color === 'black' ? 'rgba(20,20,20,0.6)' : 'rgba(255,255,255,0.7)'), w: u.type === 'giant' ? 1.6 : 1 });
  }

  doHit(u) {
    const t = u.target;
    if (u.type === 'archer') {
      const p = poseFor(u, this.time); const J = solve(p, u.scale, u.dir);
      const sx = u.x + J.rH.x, sy = J.rH.y;
      let tx, tz = u.z;
      if (t.isBase) tx = t.x; else if (!t.dead) { tx = t.x + (t.moving ? t.dir * t.def.speed * 0.6 : 0); tz = t.z; } else return;
      tx += rand(-10, 10);
      const T = clamp(Math.abs(tx - sx) / 520, 0.35, 1.1), gr = 900;
      const ty = t.isBase ? -70 : -40 * (t.scale || 1);
      this.projs.push({ x: sx, y: sy, z: u.z, tz, vx: (tx - sx) / T, vy: (ty - sy - 0.5 * gr * T * T) / T, g: gr, team: u.team, dmg: u.dmg, tier: u.tier, life: 4, owner: u });
      Sfx.twang();
      return;
    }
    const reach = u.def.range + 22;
    if (u.type === 'giant') {
      const hx = u.x + u.dir * 44 * u.scale;
      this.parts.push({ k: 'ring', x: hx, y: 0, z: u.z, life: 0.45, max: 0.45, r: u.def.aoe * 1.3 });
      for (let i = 0; i < 26; i++) this.parts.push({ k: 'dust', x: hx + rand(-30, 30), y: -rand(0, 8), z: u.z, vx: rand(-260, 260), vy: rand(-220, -40), life: rand(0.5, 1), max: 1, r: rand(5, 13) });
      for (let i = 0; i < 6; i++) this.parts.push({ k: 'rock', x: hx, y: -4, z: u.z, vx: rand(-200, 200), vy: rand(-420, -200), life: 1.2, max: 1.2, r: rand(2, 4), rot: 0 });
      this.shake = Math.max(this.shake, 14); Sfx.boom();
      let any = false;
      for (const e of this.units) {
        if (e.team === u.team || e.dead) continue;
        if (Math.abs(e.x - hx) < u.def.aoe) { this.damage(e, u.dmg, u, true); any = true; }
      }
      const eb = this.bases[1 - u.team];
      if (Math.abs(eb.x - hx) < u.def.aoe + 40) this.damageBase(eb, u.dmg * 1.2, u);
      if (any) this.hitStop = 0.07;
      return;
    }
    if (t.isBase) { this.damageBase(t.base, u.dmg, u); return; }
    if (t.dead || Math.abs(t.x - u.x) > reach) { Sfx.swoosh(); return; }
    // Parade-Chance: Funken wenn Ziel gerade auch zuschlägt
    if (t.state === 'attack' && t.type !== 'archer' && Math.random() < 0.18) {
      this.sparks(lerp(u.x, t.x, 0.5), -40 * u.scale, u.z, 14); Sfx.clang(); this.damage(t, u.dmg * 0.3, u, false); return;
    }
    this.damage(t, u.dmg, u, u.type === 'spear' || (u.type === 'ninja' && u.combo % 4 === 3));
  }

  damage(e, amount, src, heavy) {
    amount *= rand(0.88, 1.12);
    const crit = Math.random() < 0.1;
    if (crit) amount *= 1.8;
    e.hp -= amount; e.flash = 0.1; e.hurt = 0.22;
    const dir = src ? (e.x >= src.x ? 1 : -1) : -e.dir;
    e.kbx += dir * (heavy ? 260 : 90) / e.scale;
    if (e.state === 'attack' && heavy) e.state = 'idle';
    const hy = -38 * e.scale;
    this.inkBurst(e.x, hy, e.z, dir, e.color, heavy ? 22 : 12, heavy);
    if (src && src.tier >= 2) this.sparks(e.x - dir * 6, hy, e.z, 6, TIERS[src.tier].color);
    this.texts.push({ x: e.x + rand(-8, 8), y: hy - 30 * e.scale, z: e.z, v: Math.round(amount), life: 0.9, crit, c: crit ? '#ffd23d' : (src && src.team === 0 && !this.demo ? '#ffffff' : '#ff6b6b') });
    Sfx.hit();
    if (crit) { this.hitStop = Math.max(this.hitStop, 0.05); this.shake = Math.max(this.shake, 5); }
    if (e.hp <= 0) this.kill(e, dir, heavy || crit);
  }

  kill(e, dir, heavy) {
    if (e.dead) return;
    if (e.team === 1 && !this.demo) this.kills++;
    const power = heavy ? rand(1.0, 1.4) : rand(0.35, 0.6);
    e.dead = { t: 0, x: 0, y: 0, vx: dir * 420 * power, vy: -520 * power, rot: 0, rotV: dir * rand(6, 12) * power, grounded: false, splat: false };
    e.state = 'dead'; e.flash = 0.12;
    this.inkBurst(e.x, -40 * e.scale, e.z, dir, e.color, 30, true);
    Sfx.death();
    if (heavy) this.shake = Math.max(this.shake, 6);
  }

  updateDead(u, dt) {
    const d = u.dead; d.t += dt;
    if (!d.grounded) {
      d.vy += 1500 * dt; d.x += d.vx * dt; d.y += d.vy * dt; d.rot += d.rotV * dt;
      if (d.y >= 0 && d.vy > 0) {
        d.y = 0;
        if (!d.splat) { d.splat = true; this.decals.push({ x: u.x + d.x, z: u.z, r: rand(14, 24) * u.scale, c: TEAM[u.color].ink[0], a: 0.85, sx: rand(1.6, 2.4) }); for (let i = 0; i < 8; i++) this.parts.push({ k: 'ink', x: u.x + d.x, y: -2, z: u.z, vx: rand(-140, 140), vy: rand(-180, -40), life: 0.5, max: 0.5, r: rand(2, 4), c: pick(TEAM[u.color].ink) }); }
        if (Math.abs(d.vy) > 260) { d.vy *= -0.32; d.vx *= 0.55; d.rotV *= 0.5; }
        else { d.grounded = true; d.vy = 0; }
      }
    } else {
      d.vx *= Math.pow(0.02, dt); d.x += d.vx * dt;
      const target = Math.round(d.rot / Math.PI - 0.5) * Math.PI + Math.PI / 2;
      d.rot = lerp(d.rot, target, 1 - Math.pow(0.0001, dt));
    }
  }

  inkBurst(x, y, z, dir, color, n, heavy) {
    const ink = TEAM[color].ink;
    for (let i = 0; i < n; i++) {
      const sp = heavy ? rand(150, 520) : rand(80, 300);
      const a = rand(-1.2, 0.5);
      this.parts.push({ k: 'ink', x, y, z, vx: Math.cos(a) * sp * dir + rand(-40, 40), vy: Math.sin(a) * sp - rand(0, 150), life: rand(0.5, 1.1), max: 1.1, r: rand(1.5, heavy ? 5.5 : 4), c: pick(ink) });
    }
    // Spritzer-Strich
    this.parts.push({ k: 'slash', x, y, z, life: 0.16, max: 0.16, r: heavy ? 34 : 22, a: rand(-0.6, 0.6), c: color === 'black' ? '#000' : '#fff' });
  }

  sparks(x, y, z, n, color = '#ffe08a') {
    for (let i = 0; i < n; i++) { const a = rand(0, TAU), sp = rand(150, 480); this.parts.push({ k: 'spark', x, y, z, vx: Math.cos(a) * sp, vy: Math.sin(a) * sp - 100, life: rand(0.15, 0.35), max: 0.35, c: color }); }
  }

  damageBase(b, amount, src) {
    if (this.over) return;
    b.hp -= amount; b.flash = 0.12;
    this.inkBurst(b.x + (src ? -src.dir * 30 : 0), -rand(30, 140), 10, src ? src.dir : 1, this.colors[b.team], 8, false);
    if (Math.random() < 0.3) this.parts.push({ k: 'rock', x: b.x, y: -rand(40, 160), z: 8, vx: rand(-200, 200), vy: rand(-300, -80), life: 1.2, max: 1.2, r: rand(2, 5), rot: 0 });
    Sfx.hit();
  }

  updateBase(b, dt) {
    b.flash -= dt;
    if (this.over || b.hp <= 0) return;
    b.shoot -= dt;
    if (b.shoot > 0) return;
    const range = 430 + b.tower * 40;
    let best = null, bd = 1e9;
    for (const e of this.units) { if (e.team === b.team || e.dead) continue; const d = Math.abs(e.x - b.x); if (d < range && d < bd) { bd = d; best = e; } }
    if (!best) { b.shoot = 0.3; return; }
    b.shoot = Math.max(0.7, 2.2 - b.tower * 0.3);
    const sx = b.x + (b.team === 0 ? 20 : -20), sy = -190;
    const tx = best.x + (best.moving ? best.dir * best.def.speed * 0.5 : 0);
    const T = 0.8, g = 900;
    this.projs.push({ x: sx, y: sy, z: best.z, tz: best.z, vx: (tx - sx) / T, vy: (-40 - sy - 0.5 * g * T * T) / T, g, team: b.team, dmg: 22 + b.tower * 10, tier: clamp(b.tower, 0, 4), life: 4, owner: null, bolt: true });
  }

  updateProjs(dt) {
    for (const p of this.projs) {
      if (p.stuck) { p.life -= dt; continue; }
      p.vy += p.g * dt; p.x += p.vx * dt; p.y += p.vy * dt; p.z = lerp(p.z, p.tz, dt * 3);
      p.life -= dt;
      if (Math.random() < 0.5) this.parts.push({ k: 'trail', x: p.x, y: p.y, z: p.z, life: 0.25, max: 0.25, c: TIERS[p.tier].glow || 'rgba(255,255,255,0.35)' });
      // Treffer
      for (const e of this.units) {
        if (e.team === p.team || e.dead) continue;
        const h = 70 * e.scale;
        if (Math.abs(e.x - p.x) < 10 * e.scale + 4 && p.y > -h && p.y < 2) {
          this.damage(e, p.dmg, p.owner || { x: p.x - Math.sign(p.vx), team: p.team, tier: p.tier, dir: Math.sign(p.vx) }, false);
          p.life = 0; break;
        }
      }
      if (p.life <= 0) continue;
      const eb = this.bases[1 - p.team];
      if (Math.abs(eb.x - p.x) < 44 && p.y > -230) { this.damageBase(eb, p.dmg * 0.8, p.owner); p.life = 0; continue; }
      if (p.y >= 0) { p.y = 0; p.stuck = true; p.life = 2.5; p.rot = Math.atan2(p.vy, p.vx); }
    }
    this.projs = this.projs.filter(p => p.life > 0 && p.x > -100 && p.x < WORLD_W + 100);
  }

  updateParts(dt) {
    for (const p of this.parts) {
      p.life -= dt;
      if (p.k === 'ink' || p.k === 'spark' || p.k === 'rock') {
        p.vy += (p.k === 'spark' ? 600 : 1300) * dt; p.x += p.vx * dt; p.y += p.vy * dt;
        if (p.k === 'rock') p.rot = (p.rot || 0) + dt * 10;
        if (p.y > 0) {
          if (p.k === 'ink') { if (Math.random() < 0.4) this.decals.push({ x: p.x, z: p.z, r: p.r * rand(1.2, 2), c: p.c, a: 0.8, sx: rand(1.4, 2.4) }); p.life = 0; }
          else { p.y = 0; p.vy *= -0.3; p.vx *= 0.6; }
        }
      } else if (p.k === 'dust') { p.x += p.vx * dt; p.y += p.vy * dt; p.vx *= Math.pow(0.05, dt); p.vy *= Math.pow(0.05, dt); p.r += dt * 20; }
    }
    this.parts = this.parts.filter(p => p.life > 0);
    if (this.parts.length > 1400) this.parts.splice(0, this.parts.length - 1400);
    for (const t of this.texts) { t.life -= dt; t.y -= dt * 50; }
    this.texts = this.texts.filter(t => t.life > 0);
    for (const d of this.decals) d.a -= dt * 0.012;
    this.decals = this.decals.filter(d => d.a > 0.05);
  }

  updateAmbient(dt) {
    const fx = this.arena.fx;
    const rate = { dust: 6, embers: 22, snow: 40, neon: 10, leaves: 8 }[fx];
    this.ambAcc = (this.ambAcc || 0) + dt * rate;
    while (this.ambAcc > 1) {
      this.ambAcc--;
      const x = rand(view.camX - 100, view.camX + view.w + 100);
      if (fx === 'embers') this.amb.push({ x, y: rand(-20, 0), vx: rand(-20, 20), vy: rand(-90, -40), life: rand(2, 4), max: 4, r: rand(1, 2.6), c: pick(['#ffb347', '#ff6a2b', '#ffd27a']) });
      else if (fx === 'snow') this.amb.push({ x, y: rand(-700, -420), vx: rand(-30, 10), vy: rand(30, 70), life: 9, max: 9, r: rand(1, 2.8), c: 'rgba(255,255,255,0.9)' });
      else if (fx === 'dust') this.amb.push({ x, y: rand(-300, -10), vx: rand(15, 40), vy: rand(-5, 5), life: 6, max: 6, r: rand(1, 2), c: 'rgba(255,240,220,0.5)' });
      else if (fx === 'neon') this.amb.push({ x, y: rand(-500, -50), vx: rand(-10, 10), vy: rand(-20, -5), life: 4, max: 4, r: rand(1, 2.2), c: pick(['#ff4fd8', '#46e6ff', '#9b6bff']) });
      else this.amb.push({ x, y: rand(-600, -400), vx: rand(20, 60), vy: rand(30, 60), life: 9, max: 9, r: rand(2, 4), c: pick(['#e0a03a', '#c97b28', '#f1c96a']), leaf: true, rot: rand(0, TAU) });
    }
    for (const a of this.amb) { a.life -= dt; a.x += a.vx * dt + (a.leaf ? Math.sin(this.time * 2 + a.r) * 20 * dt : 0); a.y += a.vy * dt; if (a.leaf) a.rot += dt * 3; if (a.y > 10) a.life = 0; }
    this.amb = this.amb.filter(a => a.life > 0);
  }

  /* ---------- Zeichnen ---------- */
  draw(g, W, H, v) {
    const A = this.arena;
    const sk = Math.max(0, this.shake);
    const shx = rand(-sk, sk), shy = rand(-sk, sk) * 0.6;
    // Himmel
    const gr = g.createLinearGradient(0, 0, 0, v.groundY);
    gr.addColorStop(0, A.sky[0]); gr.addColorStop(1, A.sky[1]);
    g.fillStyle = gr; g.fillRect(0, 0, W, H);
    // Sonne / Mond
    const sunX = W * 0.72 - v.camX * v.s * 0.03, sunY = v.groundY - 330 * v.s;
    const sg = g.createRadialGradient(sunX, sunY, 0, sunX, sunY, 160 * v.s);
    sg.addColorStop(0, A.sun); sg.addColorStop(0.18, A.sun); sg.addColorStop(0.2, hexA(A.sun, 0.35)); sg.addColorStop(1, hexA(A.sun, 0));
    g.fillStyle = sg; g.fillRect(sunX - 170 * v.s, sunY - 170 * v.s, 340 * v.s, 340 * v.s);
    if (A.fx === 'neon') this.drawNeonSky(g, W, v);

    g.save(); g.translate(shx, shy);
    // Parallax-Berge
    this.drawRidge(g, this.far, 0.25, A.far, v, 1.0);
    this.drawRidge(g, this.mid, 0.5, A.mid, v, 1.0);
    // Boden
    const gy = v.groundY;
    const gg = g.createLinearGradient(0, gy - 6 * v.s, 0, H);
    gg.addColorStop(0, A.ground); gg.addColorStop(1, A.ground2);
    g.fillStyle = gg; g.fillRect(0, gy - 6 * v.s, W, H);
    g.fillStyle = 'rgba(255,255,255,0.08)'; g.fillRect(0, gy - 6 * v.s, W, 2 * v.s);
    if (A.fx === 'volcano' || A.id === 'volcano') this.drawLavaCracks(g, v);
    if (A.fx === 'neon') this.drawNeonFloor(g, W, H, v);

    g.save();
    g.translate(-v.camX * v.s, gy); g.scale(v.s, v.s);
    // Kulissen hinten
    for (const p of this.props) if (p.back) this.drawProp(g, p, -6);
    // Tinten-Flecken
    for (const d of this.decals) { g.globalAlpha = d.a; g.fillStyle = d.c; g.beginPath(); g.ellipse(d.x, d.z * 0.5 - 2, d.r * d.sx, d.r * 0.42, 0, 0, TAU); g.fill(); }
    g.globalAlpha = 1;
    // Basen
    for (const b of this.bases) this.drawBase(g, b);
    // Schatten
    g.fillStyle = 'rgba(0,0,0,0.25)';
    for (const u of this.units) { const dx = u.dead ? u.dead.x : 0; const ah = u.dead ? Math.min(1, 1 + u.dead.y / 200) : 1; g.globalAlpha = 0.6 * ah * (u.dead ? clamp(5 - u.dead.t, 0, 1) : 1); g.beginPath(); g.ellipse(u.x + dx, u.z * 0.5, 16 * u.scale, 3.5 * u.scale, 0, 0, TAU); g.fill(); }
    g.globalAlpha = 1;
    // Einheiten nach Tiefe
    const list = this.units.slice().sort((a, b) => a.z - b.z);
    for (const u of list) this.drawUnit(g, u);
    // Waffenspuren
    this.drawTrails(g);
    // Projektile
    for (const p of this.projs) this.drawProj(g, p);
    // Partikel
    this.drawParts(g);
    for (const p of this.props) if (!p.back) this.drawProp(g, p, 40);
    // Ambient
    for (const a of this.amb) {
      g.globalAlpha = clamp(a.life / a.max * 2, 0, 1); g.fillStyle = a.c;
      if (a.leaf) { g.save(); g.translate(a.x, a.y); g.rotate(a.rot); g.beginPath(); g.ellipse(0, 0, a.r * 1.6, a.r * 0.7, 0, 0, TAU); g.fill(); g.restore(); }
      else { if (A.fx === 'embers' || A.fx === 'neon') { g.shadowColor = a.c; g.shadowBlur = 6; } g.beginPath(); g.arc(a.x, a.y, a.r, 0, TAU); g.fill(); g.shadowBlur = 0; }
    }
    g.globalAlpha = 1;
    // Schadenszahlen
    g.textAlign = 'center';
    for (const t of this.texts) {
      const k = t.life / 0.9; const sc = t.crit ? 1 + (1 - k) * 0.2 + (k > 0.8 ? (k - 0.8) * 3 : 0) : 1;
      g.globalAlpha = clamp(k * 1.6, 0, 1);
      g.font = `900 ${t.crit ? 17 * sc : 12}px Bungee, Impact, sans-serif`;
      g.lineWidth = 3; g.strokeStyle = 'rgba(0,0,0,0.75)'; g.strokeText((t.crit ? '!' : '') + t.v, t.x, t.y + t.z * 0.5);
      g.fillStyle = t.c; g.fillText((t.crit ? '!' : '') + t.v, t.x, t.y + t.z * 0.5);
    }
    g.globalAlpha = 1;
    g.restore();
    g.restore();
    // Vignette
    const vg = g.createRadialGradient(W / 2, H / 2, Math.min(W, H) * 0.35, W / 2, H / 2, Math.max(W, H) * 0.75);
    vg.addColorStop(0, 'rgba(0,0,0,0)'); vg.addColorStop(1, 'rgba(0,0,0,0.45)');
    g.fillStyle = vg; g.fillRect(0, 0, W, H);
    if (this.hitStop > 0) { g.fillStyle = 'rgba(255,255,255,0.05)'; g.fillRect(0, 0, W, H); }
  }

  drawRidge(g, pts, par, color, v, k) {
    g.fillStyle = color; g.beginPath();
    const off = v.camX * par;
    g.moveTo(0, v.groundY);
    for (const p of pts) { const sx = (p.x - off) * v.s * 0.8; if (sx < -100 || sx > view.W + 100) continue; g.lineTo(sx, v.groundY - p.h * v.s * k); }
    g.lineTo(view.W, v.groundY); g.closePath(); g.fill();
  }
  drawNeonSky(g, W, v) {
    g.save(); g.globalAlpha = 0.5;
    for (let i = 0; i < 6; i++) {
      const x = ((i * 380 - v.camX * 0.15) % (W + 400) + W + 400) % (W + 400) - 200;
      const sw = Math.sin(this.time * 0.7 + i) * 0.4;
      const gr = g.createLinearGradient(x, v.groundY, x + sw * 400, 0);
      gr.addColorStop(0, i % 2 ? 'rgba(70,230,255,0.25)' : 'rgba(255,79,216,0.25)'); gr.addColorStop(1, 'rgba(0,0,0,0)');
      g.fillStyle = gr; g.beginPath(); g.moveTo(x - 10, v.groundY); g.lineTo(x + 10, v.groundY); g.lineTo(x + sw * 500 + 90, 0); g.lineTo(x + sw * 500 - 90, 0); g.fill();
    }
    g.restore();
  }
  drawNeonFloor(g, W, H, v) {
    g.save(); g.strokeStyle = 'rgba(255,79,216,0.35)'; g.lineWidth = 1;
    const gy = v.groundY;
    for (let i = 0; i < 8; i++) { const y = gy + i * i * 4 * v.s; g.beginPath(); g.moveTo(0, y); g.lineTo(W, y); g.stroke(); }
    const step = 80 * v.s; const off = (v.camX * v.s) % step;
    for (let x = -off - W; x < W * 2; x += step) { g.beginPath(); g.moveTo(x, gy); g.lineTo(W / 2 + (x - W / 2) * 2.2, H); g.stroke(); }
    g.restore();
  }
  drawLavaCracks(g, v) {
    g.save(); g.translate(-v.camX * v.s, v.groundY); g.scale(v.s, v.s);
    g.strokeStyle = `rgba(255,${120 + Math.sin(this.time * 3) * 30 | 0},40,0.75)`; g.lineWidth = 2; g.shadowColor = '#ff6a00'; g.shadowBlur = 10;
    const R = mulberry(5);
    for (let x = 0; x < WORLD_W; x += 140) { g.beginPath(); let px = x + R() * 60, py = 6 + R() * 30; g.moveTo(px, py); for (let j = 0; j < 4; j++) { px += 15 + R() * 25; py += (R() - 0.5) * 14; g.lineTo(px, py); } g.stroke(); }
    g.restore();
  }

  drawProp(g, p, zy) {
    const s = p.s; g.save(); g.translate(p.x, zy * 0.5); g.scale(s, s);
    const A = this.arena;
    const dark = p.back ? A.mid : A.ground2;
    g.globalAlpha = p.back ? 0.85 : 1;
    switch (p.k) {
      case 'rock': g.fillStyle = dark; g.beginPath(); g.moveTo(-30, 0); g.lineTo(-22, -22); g.lineTo(2, -32); g.lineTo(26, -14); g.lineTo(32, 0); g.fill(); g.fillStyle = 'rgba(255,255,255,0.08)'; g.beginPath(); g.moveTo(-22, -22); g.lineTo(2, -32); g.lineTo(0, -18); g.fill(); break;
      case 'grass': g.strokeStyle = dark; g.lineWidth = 2; for (let i = -10; i <= 10; i += 4) { g.beginPath(); g.moveTo(i, 0); g.quadraticCurveTo(i + 3, -10, i + 6 + Math.sin(this.time * 2 + i) * 2, -18 - Math.abs(i)); g.stroke(); } break;
      case 'tree': g.fillStyle = dark; g.fillRect(-4, -70, 8, 70); g.beginPath(); g.arc(0, -86, 30, 0, TAU); g.arc(-20, -70, 20, 0, TAU); g.arc(20, -72, 22, 0, TAU); g.fill(); break;
      case 'spike': g.fillStyle = dark; g.beginPath(); g.moveTo(-14, 0); g.lineTo(-4, -60); g.lineTo(4, -30); g.lineTo(10, -48); g.lineTo(18, 0); g.fill(); break;
      case 'lava': g.fillStyle = '#ff7a1a'; g.shadowColor = '#ff5a00'; g.shadowBlur = 20; g.beginPath(); g.ellipse(0, 4, 40, 6, 0, 0, TAU); g.fill(); break;
      case 'crystal': g.fillStyle = 'rgba(200,235,255,0.75)'; g.beginPath(); g.moveTo(-10, 0); g.lineTo(-6, -40); g.lineTo(0, -52); g.lineTo(6, -38); g.lineTo(10, 0); g.fill(); g.fillStyle = 'rgba(255,255,255,0.5)'; g.beginPath(); g.moveTo(0, -52); g.lineTo(6, -38); g.lineTo(2, 0); g.lineTo(-1, 0); g.fill(); break;
      case 'pine': g.fillStyle = dark; g.fillRect(-3, -20, 6, 20); for (let i = 0; i < 3; i++) { g.beginPath(); g.moveTo(-26 + i * 6, -16 - i * 22); g.lineTo(0, -52 - i * 22); g.lineTo(26 - i * 6, -16 - i * 22); g.fill(); } g.fillStyle = '#fff'; g.beginPath(); g.moveTo(-8, -82); g.lineTo(0, -96); g.lineTo(8, -82); g.fill(); break;
      case 'pillar': g.fillStyle = '#1a1230'; g.fillRect(-10, -150, 20, 150); g.fillStyle = Math.sin(this.time * 3 + p.x) > 0 ? '#ff4fd8' : '#46e6ff'; g.shadowColor = g.fillStyle; g.shadowBlur = 16; g.fillRect(-12, -154, 24, 5); g.fillRect(-2, -140, 4, 120); break;
      case 'lamp': g.strokeStyle = '#2a2140'; g.lineWidth = 4; g.beginPath(); g.moveTo(0, 0); g.lineTo(0, -110); g.lineTo(14, -116); g.stroke(); g.fillStyle = '#fff6c2'; g.shadowColor = '#46e6ff'; g.shadowBlur = 24; g.beginPath(); g.arc(16, -114, 5, 0, TAU); g.fill(); break;
      case 'column': g.fillStyle = dark; g.fillRect(-12, -130, 24, 130); g.fillRect(-18, -138, 36, 10); g.fillRect(-18, -6, 36, 6); g.fillStyle = 'rgba(0,0,0,0.15)'; for (let i = -8; i <= 8; i += 6) g.fillRect(i, -128, 2, 122); break;
      case 'statue': g.fillStyle = dark; g.fillRect(-20, -26, 40, 26); g.beginPath(); g.arc(0, -66, 10, 0, TAU); g.fill(); g.fillRect(-7, -56, 14, 30); break;
    }
    g.restore();
  }

  drawBase(g, b) {
    const col = this.colors[b.team];
    const C = TEAM[col];
    const dir = b.team === 0 ? 1 : -1;
    const dmg = 1 - b.hp / b.maxHp;
    g.save(); g.translate(b.x, 4);
    if (b.hp <= 0) { g.globalAlpha = 0.35; g.translate(0, 40); g.rotate(dir * -0.25); }
    // Sockel
    g.fillStyle = 'rgba(0,0,0,0.3)'; g.beginPath(); g.ellipse(0, 0, 80, 10, 0, 0, TAU); g.fill();
    g.fillStyle = b.flash > 0 ? '#c33' : C.fill;
    g.strokeStyle = C.rim; g.lineWidth = 3;
    // Turm aus Tinte
    g.beginPath(); g.moveTo(-58, 0); g.lineTo(-44, -60); g.lineTo(-38, -200); g.lineTo(-50, -206); g.lineTo(-50, -232);
    for (let i = 0; i < 5; i++) { const x = -50 + i * 25; g.lineTo(x, -232); g.lineTo(x, -246); g.lineTo(x + 12.5, -246); g.lineTo(x + 12.5, -232); }
    g.lineTo(50, -232); g.lineTo(50, -206); g.lineTo(38, -200); g.lineTo(44, -60); g.lineTo(58, 0); g.closePath(); g.fill(); g.stroke();
    // Tor + Fenster
    g.fillStyle = col === 'black' ? '#2b2b30' : '#bdbdb5';
    g.beginPath(); g.moveTo(-16, 0); g.lineTo(-16, -34); g.arc(0, -34, 16, Math.PI, 0); g.lineTo(16, 0); g.fill();
    g.fillStyle = col === 'black' ? '#ffcf5a' : '#3a2a10'; g.globalAlpha *= 0.85;
    g.fillRect(-6, -150, 12, 22); g.fillRect(-6, -110, 12, 18);
    g.globalAlpha = b.hp <= 0 ? 0.35 : 1;
    // Risse
    if (dmg > 0.2) { g.strokeStyle = col === 'black' ? 'rgba(255,255,255,0.5)' : 'rgba(0,0,0,0.6)'; g.lineWidth = 2; g.beginPath(); g.moveTo(-30, -180); g.lineTo(-14, -150); g.lineTo(-24, -120); g.lineTo(-8, -90); if (dmg > 0.5) { g.moveTo(30, -60); g.lineTo(16, -100); g.lineTo(28, -130); } if (dmg > 0.75) { g.moveTo(-40, -40); g.lineTo(-10, -60); g.lineTo(10, -40); } g.stroke(); }
    // Fahne
    const wv = Math.sin(this.time * 5) * 6;
    g.strokeStyle = '#555'; g.lineWidth = 3; g.beginPath(); g.moveTo(0, -246); g.lineTo(0, -310); g.stroke();
    g.fillStyle = col === 'black' ? '#111' : '#fafafa'; g.strokeStyle = C.rim; g.lineWidth = 2;
    g.beginPath(); g.moveTo(0, -308); g.quadraticCurveTo(dir * 25, -300 + wv, dir * 52, -298 + wv * 0.5); g.lineTo(dir * 50, -278 + wv * 0.5); g.quadraticCurveTo(dir * 25, -282 + wv, 0, -276); g.closePath(); g.fill(); g.stroke();
    g.restore();
  }

  drawUnit(g, u) {
    let alpha = 1;
    if (u.dead) alpha = clamp(5.5 - u.dead.t, 0, 1);
    if (u.spawnT > 0) alpha = 1 - u.spawnT / 0.35 * 0.8;
    g.globalAlpha = alpha;
    const zy = u.z * 0.5;
    if (u.dead) {
      const d = u.dead;
      const p = basePose(); p.lU = -1.2 + Math.sin(d.t * 8) * 0.3; p.lF = -1.6; p.rU = 2.0; p.rF = 2.6; p.lT = 0.4; p.lS = 0.2; p.rT = -0.3; p.rS = -0.6; p.w = 2.6; p.w2 = -2.4;
      g.save(); g.translate(u.x + d.x, zy + d.y - 6 * u.scale * (d.grounded ? 1 : 0)); g.rotate(d.rot);
      const fakeU = Object.assign({}, u, { flash: d.t < 0.1 ? 1 : 0 });
      drawStick(g, 0, 0, fakeU, p, this.time);
      g.restore();
    } else {
      const p = poseFor(u, this.time);
      drawStick(g, u.x, zy, u, p, this.time);
      // HP-Balken
      if (u.hp < u.maxHp) {
        const w = 26 * u.scale, y = zy - 92 * u.scale - (u.helmet === 'face' ? 18 * u.scale : 0);
        g.fillStyle = 'rgba(0,0,0,0.55)'; g.fillRect(u.x - w / 2 - 1, y - 1, w + 2, 5);
        g.fillStyle = u.team === 0 && !this.demo ? '#5cff8a' : '#ff4a4a'; g.fillRect(u.x - w / 2, y, w * clamp(u.hp / u.maxHp, 0, 1), 3);
      }
    }
    g.globalAlpha = 1;
  }

  drawTrails(g) {
    const groups = new Map();
    for (const t of this.trails) { if (!groups.has(t.u)) groups.set(t.u, []); groups.get(t.u).push(t); }
    g.save(); g.globalCompositeOperation = 'lighter';
    for (const [u, pts] of groups) {
      if (pts.length < 2) continue;
      const zy = u.z * 0.5;
      g.fillStyle = pts[0].color;
      if (u.color === 'black' && !TIERS[u.tier].glow) g.globalCompositeOperation = 'source-over';
      for (let i = 1; i < pts.length; i++) {
        const a = pts[i - 1], b = pts[i];
        g.globalAlpha = clamp(1 - b.age / 0.22, 0, 1) * 0.8;
        g.beginPath();
        g.moveTo(a.x + a.tip.x, zy + a.tip.y); g.lineTo(b.x + b.tip.x, zy + b.tip.y);
        g.lineTo(b.x + b.base.x, zy + b.base.y); g.lineTo(a.x + a.base.x, zy + a.base.y); g.closePath(); g.fill();
      }
      g.globalCompositeOperation = 'lighter';
    }
    g.restore(); g.globalAlpha = 1;
  }

  drawProj(g, p) {
    const zy = p.z * 0.5;
    const ang = p.stuck ? p.rot : Math.atan2(p.vy, p.vx);
    g.save(); g.translate(p.x, p.y + zy); g.rotate(ang);
    if (p.stuck) g.globalAlpha = clamp(p.life, 0, 1);
    const T = TIERS[p.tier];
    if (T.glow) { g.shadowColor = T.glow; g.shadowBlur = 10; }
    g.strokeStyle = '#d8c9a8'; g.lineWidth = p.bolt ? 2.6 : 1.8; g.beginPath(); g.moveTo(p.bolt ? -24 : -20, 0); g.lineTo(0, 0); g.stroke();
    g.fillStyle = T.color; g.beginPath(); g.moveTo(5, 0); g.lineTo(-2, -3); g.lineTo(-2, 3); g.fill();
    g.fillStyle = '#e8e8e8'; g.beginPath(); g.moveTo(-20, 0); g.lineTo(-25, -3.5); g.lineTo(-22, 0); g.lineTo(-25, 3.5); g.fill();
    g.restore();
  }

  drawParts(g) {
    for (const p of this.parts) {
      const zy = p.z * 0.5; const k = clamp(p.life / p.max, 0, 1);
      if (p.k === 'ink') {
        g.globalAlpha = Math.min(1, k * 2); g.fillStyle = p.c;
        const sp = Math.hypot(p.vx, p.vy); const st = clamp(sp / 400, 1, 2.4);
        g.save(); g.translate(p.x, p.y + zy); g.rotate(Math.atan2(p.vy, p.vx)); g.beginPath(); g.ellipse(0, 0, p.r * st, p.r / Math.sqrt(st), 0, 0, TAU); g.fill(); g.restore();
      } else if (p.k === 'spark') {
        g.globalAlpha = k; g.strokeStyle = p.c; g.lineWidth = 2; g.beginPath(); g.moveTo(p.x, p.y + zy); g.lineTo(p.x - p.vx * 0.03, p.y + zy - p.vy * 0.03); g.stroke();
      } else if (p.k === 'dust') {
        g.globalAlpha = k * 0.55; g.fillStyle = this.arena.fx === 'snow' ? '#f4f8ff' : '#b9ab94'; g.beginPath(); g.arc(p.x, p.y + zy, p.r, 0, TAU); g.fill();
      } else if (p.k === 'ring') {
        const r = p.r * easeOut(1 - k);
        g.globalAlpha = k; g.strokeStyle = 'rgba(255,255,255,0.85)'; g.lineWidth = 5 * k + 1; g.beginPath(); g.ellipse(p.x, p.y + zy, r, r * 0.22, 0, 0, TAU); g.stroke();
      } else if (p.k === 'rock') {
        g.globalAlpha = Math.min(1, k * 3); g.fillStyle = this.arena.ground2; g.save(); g.translate(p.x, p.y + zy); g.rotate(p.rot); g.fillRect(-p.r, -p.r, p.r * 2, p.r * 2); g.restore();
      } else if (p.k === 'trail') {
        g.globalAlpha = k * 0.6; g.fillStyle = p.c; g.beginPath(); g.arc(p.x, p.y + zy, 1.6, 0, TAU); g.fill();
      } else if (p.k === 'slash') {
        g.globalAlpha = k; g.strokeStyle = p.c; g.lineWidth = 3 * k + 0.5;
        g.save(); g.translate(p.x, p.y + zy); g.rotate(p.a);
        const r = p.r * (1.3 - k * 0.5);
        g.beginPath(); g.moveTo(-r, 0); g.lineTo(r, 0); g.moveTo(0, -r * 0.5); g.lineTo(0, r * 0.5); g.stroke(); g.restore();
      }
    }
    g.globalAlpha = 1;
  }
}

function mulberry(a) { return function () { a |= 0; a = a + 0x6D2B79F5 | 0; let t = Math.imul(a ^ a >>> 15, 1 | a); t = t + Math.imul(t ^ t >>> 7, 61 | t) ^ t; return ((t ^ t >>> 14) >>> 0) / 4294967296; }; }
function hexA(hex, a) { const n = parseInt(hex.slice(1), 16); return `rgba(${n >> 16 & 255},${n >> 8 & 255},${n & 255},${a})`; }

/* ======================= App / UI ======================= */
const canvas = document.getElementById('game');
const ctx = canvas.getContext('2d');
const view = { W: 0, H: 0, s: 1, camX: 0, w: 0, groundY: 0, dpr: 1, auto: true, autoT: 0 };
let battle = null;
let mode = 'menu'; // menu | play | pause | end
let lastT = performance.now();
const $ = id => document.getElementById(id);

function resize() {
  view.dpr = Math.min(2, window.devicePixelRatio || 1);
  view.W = window.innerWidth; view.H = window.innerHeight;
  canvas.width = view.W * view.dpr; canvas.height = view.H * view.dpr;
  canvas.style.width = view.W + 'px'; canvas.style.height = view.H + 'px';
  const barH = mode === 'menu' ? 0 : ($('bar') ? $('bar').offsetHeight : 90);
  view.s = Math.min(view.H / 470, view.W / 1050);
  view.s = Math.max(view.s, 0.42);
  view.groundY = mode === 'menu' ? view.H * 0.8 : view.H - Math.max(barH + 34, view.H * 0.24);
  view.w = view.W / view.s;
  clampCam();
}
function clampCam() { view.camX = clamp(view.camX, -60, WORLD_W + 60 - view.w); if (view.w > WORLD_W + 120) view.camX = (WORLD_W - view.w) / 2; }

function loop(now) {
  let dt = Math.min(0.05, (now - lastT) / 1000); lastT = now;
  if (battle && mode !== 'pause') {
    battle.update(dt);
    updateCamera(dt);
  }
  if (battle) {
    ctx.setTransform(view.dpr, 0, 0, view.dpr, 0, 0);
    battle.draw(ctx, view.W, view.H, view);
  }
  if (mode === 'play' || mode === 'end') updateHud();
  if (mode === 'menu' && battle && (battle.over && battle.time - battle.endT > 4)) startDemo();
  requestAnimationFrame(loop);
}

function updateCamera(dt) {
  if (mode === 'menu') {
    const xs = battle.units.filter(u => !u.dead).map(u => u.x);
    const mid = xs.length ? xs.reduce((a, b) => a + b, 0) / xs.length : WORLD_W / 2;
    view.camX = lerp(view.camX, mid - view.w / 2, 1 - Math.pow(0.4, dt)); clampCam(); return;
  }
  if (keys.left) { view.camX -= 900 * dt; view.autoT = 3; }
  if (keys.right) { view.camX += 900 * dt; view.autoT = 3; }
  view.autoT -= dt;
  if (view.auto && view.autoT <= 0 && mode === 'play') {
    const mine = battle.units.filter(u => u.team === 0 && !u.dead);
    let front = mine.length ? Math.max(...mine.map(u => u.x)) : battle.bases[0].x;
    const foes = battle.units.filter(u => u.team === 1 && !u.dead && u.x < front + 300);
    if (foes.length) front = Math.max(front, Math.min(...foes.map(u => u.x)));
    const target = front - view.w * 0.55;
    view.camX = lerp(view.camX, target, 1 - Math.pow(0.25, dt));
  }
  if (mode === 'end') { const b = battle.bases[1 - battle.result]; view.camX = lerp(view.camX, b.x - view.w / 2, 1 - Math.pow(0.1, dt)); }
  clampCam();
}

/* ---------- Eingabe ---------- */
const keys = {};
window.addEventListener('keydown', e => {
  if (e.key === 'ArrowLeft' || e.key === 'a') keys.left = true;
  if (e.key === 'ArrowRight' || e.key === 'd') keys.right = true;
  if (mode === 'play') {
    const i = '12345'.indexOf(e.key); if (i >= 0) trySpawn(UNIT_KEYS[i]);
    if (e.key === 'q') setCmd('defend'); if (e.key === 'e') setCmd('attack');
    if (e.key === 'Escape' || e.key === 'p') pauseGame();
  } else if (mode === 'pause' && (e.key === 'Escape' || e.key === 'p')) resumeGame();
});
window.addEventListener('keyup', e => { if (e.key === 'ArrowLeft' || e.key === 'a') keys.left = false; if (e.key === 'ArrowRight' || e.key === 'd') keys.right = false; });

let drag = null;
canvas.addEventListener('pointerdown', e => { Sfx.init(); if (mode !== 'play' && mode !== 'end') return; drag = { x: e.clientX, cam: view.camX, id: e.pointerId }; canvas.setPointerCapture(e.pointerId); });
canvas.addEventListener('pointermove', e => { if (!drag || drag.id !== e.pointerId) return; view.camX = drag.cam - (e.clientX - drag.x) / view.s; view.autoT = 3.5; clampCam(); });
canvas.addEventListener('pointerup', () => { drag = null; });
canvas.addEventListener('pointercancel', () => { drag = null; });
canvas.addEventListener('wheel', e => { if (mode !== 'play') return; view.camX += (e.deltaY + e.deltaX) / view.s * 0.8; view.autoT = 3; clampCam(); e.preventDefault(); }, { passive: false });

/* ---------- HUD ---------- */
function buildBar() {
  const bar = $('units'); bar.innerHTML = '';
  UNIT_KEYS.forEach((k, i) => {
    const d = UNITS[k];
    const b = document.createElement('button');
    b.className = 'ubtn'; b.id = 'u_' + k; b.setAttribute('aria-label', `${d.name} beschwören, ${d.cost} Gold`);
    b.innerHTML = `<canvas width="120" height="120"></canvas><span class="cost">${d.cost}</span><span class="nm">${d.name}</span><span class="key">${i + 1}</span><span class="cd"></span>`;
    b.addEventListener('pointerdown', e => { e.preventDefault(); Sfx.init(); trySpawn(k); });
    bar.appendChild(b);
    drawPortrait(b.querySelector('canvas'), k, save.team, save.helmet, save.wlv[k] - 1);
  });
}
function trySpawn(k) {
  if (!battle || mode !== 'play') return;
  if (battle.spawn(0, k)) { const b = $('u_' + k); b.classList.remove('pop'); void b.offsetWidth; b.classList.add('pop'); }
  else if (battle.gold[0] < UNITS[k].cost) { const g = $('gold'); g.classList.remove('shake'); void g.offsetWidth; g.classList.add('shake'); }
}
function setCmd(c) {
  if (!battle) return; battle.cmd[0] = c;
  $('cmdAtk').classList.toggle('on', c === 'attack'); $('cmdDef').classList.toggle('on', c === 'defend');
  if (c === 'defend') { view.autoT = 0; }
}
function updateHud() {
  const b = battle;
  $('goldv').textContent = Math.floor(b.gold[0]);
  $('hpL').style.width = clamp(b.bases[0].hp / b.bases[0].maxHp, 0, 1) * 100 + '%';
  $('hpR').style.width = clamp(b.bases[1].hp / b.bases[1].maxHp, 0, 1) * 100 + '%';
  $('killv').textContent = b.kills;
  for (const k of UNIT_KEYS) {
    const el = $('u_' + k); if (!el) continue;
    const cd = Math.max(0, b.spawnCd[0][k] || 0) / UNITS[k].spawnCd;
    el.querySelector('.cd').style.height = cd * 100 + '%';
    el.classList.toggle('poor', b.gold[0] < UNITS[k].cost);
  }
  drawMinimap();
  if (mode === 'play' && b.over && b.time - b.endT > 2.2) showEnd();
}
function drawMinimap() {
  const c = $('mini'); const g = c.getContext('2d');
  const W = c.width, H = c.height;
  g.clearRect(0, 0, W, H);
  g.fillStyle = 'rgba(0,0,0,0.45)'; g.fillRect(0, 0, W, H);
  const sx = W / WORLD_W;
  g.strokeStyle = 'rgba(255,255,255,0.7)'; g.lineWidth = 2; g.strokeRect(view.camX * sx, 1, view.w * sx, H - 2);
  for (const u of battle.units) { if (u.dead) continue; g.fillStyle = u.color === 'black' ? '#000' : '#fff'; g.fillRect(u.x * sx - 1.5, H / 2 - 3 + (u.team ? 3 : -3) + u.z * 0.1, 3, 3 + (u.type === 'giant' ? 3 : 0)); }
  for (const b of battle.bases) { g.fillStyle = battle.colors[b.team] === 'black' ? '#000' : '#fff'; g.fillRect(b.x * sx - 4, 2, 8, H - 4); g.strokeStyle = '#888'; g.strokeRect(b.x * sx - 4, 2, 8, H - 4); }
}
$('mini').addEventListener('pointerdown', e => { const r = e.target.getBoundingClientRect(); view.camX = (e.clientX - r.left) / r.width * WORLD_W - view.w / 2; view.autoT = 4; clampCam(); });

/* ---------- Porträts / Vorschau ---------- */
function drawPortrait(c, type, team, helmet, tier, poseT) {
  const g = c.getContext('2d'); const W = c.width, H = c.height;
  g.setTransform(1, 0, 0, 1, 0, 0); g.clearRect(0, 0, W, H);
  const u = { type, def: UNITS[type], dir: 1, color: team, scale: 1, tier, helmet, flash: 0, hurt: 0, phase: 0, seed: 1, state: 'idle', moving: false, combo: 0, atkT: 0 };
  if (poseT !== undefined) { u.state = 'attack'; u.atkT = poseT; }
  const sc = H / (helmet === 'face' ? 128 : 100) * (type === 'giant' ? 0.85 : 1);
  g.save(); g.translate(W / 2 - (type === 'spear' ? 12 : type === 'archer' ? 6 : 4) * sc, H * 0.93); g.scale(sc, sc);
  const p = poseFor(u, 0);
  drawStick(g, 0, 0, u, p, 0);
  g.restore();
}
function refreshPreviews() {
  if ($('units') && $('units').children.length) buildBar();
  renderShop(); renderUpgrades(); renderTeam();
}

/* ---------- Menüs ---------- */
function show(id) { for (const s of document.querySelectorAll('.screen')) s.hidden = s.id !== id; }
function setTab(t) {
  for (const b of document.querySelectorAll('.tab')) b.classList.toggle('on', b.dataset.t === t);
  for (const p of document.querySelectorAll('.pane')) p.hidden = p.id !== 'p_' + t;
  try { localStorage.setItem(SAVE_KEY + '_tab', t); } catch (e) {}
}
function coinsUI() { for (const el of document.querySelectorAll('.coinv')) el.textContent = save.coins; }

function renderTeam() {
  for (const t of ['black', 'white']) {
    const card = $('team_' + t); card.classList.toggle('on', save.team === t);
    drawPortrait(card.querySelector('canvas'), 'sword', t, save.helmet, save.wlv.sword - 1);
  }
}
function renderArenas() {
  const wrap = $('arenas'); wrap.innerHTML = '';
  ARENAS.forEach((a, i) => {
    const b = document.createElement('button'); b.className = 'arena' + (save.arena === i ? ' on' : ''); b.id = 'arena_' + i;
    b.innerHTML = `<canvas width="240" height="120"></canvas><div class="an">${a.name}</div><div class="am"><span>${'★'.repeat(i + 1)}${'☆'.repeat(4 - i)}</span><span>+${a.reward} 🪙</span></div>`;
    b.onclick = () => { save.arena = i; persist(); renderArenas(); startDemo(); };
    wrap.appendChild(b);
    paintArenaThumb(b.querySelector('canvas'), a, i);
  });
}
function paintArenaThumb(c, a, i) {
  const g = c.getContext('2d'); const W = c.width, H = c.height;
  const gr = g.createLinearGradient(0, 0, 0, H); gr.addColorStop(0, a.sky[0]); gr.addColorStop(1, a.sky[1]); g.fillStyle = gr; g.fillRect(0, 0, W, H);
  g.fillStyle = hexA(a.sun, 0.9); g.beginPath(); g.arc(W * 0.72, H * 0.35, 12, 0, TAU); g.fill();
  const R = mulberry(i * 977 + 13);
  g.fillStyle = a.far; g.beginPath(); g.moveTo(0, H * 0.75); for (let x = 0; x <= W; x += 12) g.lineTo(x, H * 0.75 - 20 - R() * 30); g.lineTo(W, H); g.lineTo(0, H); g.fill();
  g.fillStyle = a.mid; g.beginPath(); g.moveTo(0, H * 0.8); for (let x = 0; x <= W; x += 10) g.lineTo(x, H * 0.8 - 8 - R() * 14); g.lineTo(W, H); g.lineTo(0, H); g.fill();
  g.fillStyle = a.ground; g.fillRect(0, H * 0.8, W, H * 0.2);
  for (let k = 0; k < 2; k++) {
    const u = { type: k ? 'spear' : 'sword', def: UNITS.sword, dir: k ? -1 : 1, color: k ? 'white' : 'black', scale: 1, tier: i, helmet: 'none', flash: 0, hurt: 0, phase: 0, seed: 1, state: 'attack', atkT: 0.5, moving: false, combo: 0 };
    g.save(); g.translate(W / 2 + (k ? 26 : -26), H * 0.86); g.scale(0.8, 0.8); drawStick(g, 0, 0, u, poseFor(u, 0), 0); g.restore();
  }
}
function renderShop() {
  const wrap = $('shop'); if (!wrap) return; wrap.innerHTML = '';
  for (const h of HELMETS) {
    const owned = save.owned.includes(h.id), eq = save.helmet === h.id;
    const d = document.createElement('div'); d.className = 'item' + (eq ? ' on' : '') + (h.special ? ' special' : '');
    d.innerHTML = `<canvas width="160" height="170"></canvas><div class="in">${h.name}</div>`;
    const btn = document.createElement('button'); btn.className = 'buy';
    btn.textContent = eq ? 'Ausgerüstet' : owned ? 'Anziehen' : `${h.price} 🪙 kaufen`;
    btn.disabled = eq || (!owned && save.coins < h.price);
    btn.onclick = () => {
      if (!owned) { if (save.coins < h.price) return; save.coins -= h.price; save.owned.push(h.id); Sfx.init(); Sfx.coin(); toast(`${h.name} gekauft!`); }
      save.helmet = h.id; persist(); coinsUI(); refreshPreviews();
    };
    d.appendChild(btn); wrap.appendChild(d);
    drawPortrait(d.querySelector('canvas'), 'sword', save.team, h.id, save.wlv.sword - 1);
  }
}
function renderUpgrades() {
  const wrap = $('upg'); if (!wrap) return; wrap.innerHTML = '';
  for (const k of UNIT_KEYS) {
    const d = UNITS[k], lv = save.wlv[k];
    const cost = lv >= 5 ? 0 : [0, 120, 260, 480, 800][lv];
    const row = document.createElement('div'); row.className = 'urow';
    const pips = TIERS.map((t, i) => `<i style="background:${i < lv ? t.color : 'transparent'};border-color:${t.color}"></i>`).join('');
    row.innerHTML = `<canvas width="110" height="110"></canvas><div class="ui"><div class="un">${d.name} <small>${TIERS[lv - 1].name}</small></div><div class="ud">${d.desc}</div><div class="pips">${pips}</div><div class="us">Schaden ${Math.round(d.dmg * (1 + (lv - 1) * 0.28))} · Leben ${Math.round(d.hp * (1 + save.armor * 0.15))} · Kosten ${d.cost}</div></div>`;
    const btn = document.createElement('button'); btn.className = 'buy';
    btn.textContent = lv >= 5 ? 'MAX' : `${TIERS[lv].name} · ${cost} 🪙`;
    btn.disabled = lv >= 5 || save.coins < cost;
    btn.onclick = () => { save.coins -= cost; save.wlv[k]++; persist(); Sfx.init(); Sfx.coin(); toast(`${d.name}: ${TIERS[lv].name}-Waffe!`); coinsUI(); refreshPreviews(); };
    row.appendChild(btn); wrap.appendChild(row);
    drawPortrait(row.querySelector('canvas'), k, save.team, save.helmet, lv - 1, k === 'archer' ? 0.5 : 0.45);
  }
  const extras = [
    { k: 'armor', n: 'Rüstung', d: '+15 % Leben für alle Krieger', max: 5, base: 150 },
    { k: 'mine', n: 'Pigment-Mine', d: '+3 Gold pro Sekunde', max: 5, base: 140 },
    { k: 'tower', n: 'Burg-Turm', d: 'Mehr Burgleben, Turm schießt schneller', max: 5, base: 160 },
  ];
  for (const x of extras) {
    const lv = save[x.k]; const cost = Math.round(x.base * Math.pow(1.7, lv));
    const row = document.createElement('div'); row.className = 'urow';
    const pips = Array.from({ length: x.max }, (_, i) => `<i style="background:${i < lv ? '#ffcc3d' : 'transparent'};border-color:#ffcc3d"></i>`).join('');
    row.innerHTML = `<div class="ico">${x.k === 'armor' ? '🛡️' : x.k === 'mine' ? '⛏️' : '🏰'}</div><div class="ui"><div class="un">${x.n} <small>Stufe ${lv}</small></div><div class="ud">${x.d}</div><div class="pips">${pips}</div></div>`;
    const btn = document.createElement('button'); btn.className = 'buy';
    btn.textContent = lv >= x.max ? 'MAX' : `${cost} 🪙`; btn.disabled = lv >= x.max || save.coins < cost;
    btn.onclick = () => { save.coins -= cost; save[x.k]++; persist(); Sfx.init(); Sfx.coin(); coinsUI(); renderUpgrades(); };
    row.appendChild(btn); wrap.appendChild(row);
  }
}

let toastT;
function toast(msg) { const t = $('toast'); t.textContent = msg; t.hidden = false; t.classList.remove('in'); void t.offsetWidth; t.classList.add('in'); clearTimeout(toastT); toastT = setTimeout(() => t.hidden = true, 1800); }

function startDemo() {
  battle = new Battle(save.arena, save.team, true);
  battle.gold = [400, 400];
  view.camX = WORLD_W / 2 - view.w / 2;
}

function startGame() {
  Sfx.init();
  mode = 'play'; show('hud');
  battle = new Battle(save.arena, save.team, false);
  buildBar(); setCmd('attack');
  $('hud').dataset.team = save.team;
  $('arenaName').textContent = ARENAS[save.arena].name;
  resize(); view.camX = 0; view.autoT = 0;
  // Startbonus: zwei Klingen
  battle.spawnCd[0] = {}; battle.gold[0] += 60; battle.spawn(0, 'sword'); battle.spawnCd[0] = {}; battle.spawn(0, 'sword');
  battle.spawnCd[0].sword = 1;
  try { if (window.matchMedia('(pointer: coarse)').matches && document.documentElement.requestFullscreen && !document.fullscreenElement) document.documentElement.requestFullscreen().catch(() => {}); } catch (e) {}
}
function pauseGame() { if (mode !== 'play') return; mode = 'pause'; $('pause').hidden = false; }
function resumeGame() { mode = 'play'; $('pause').hidden = true; }
function toMenu() { mode = 'menu'; $('pause').hidden = true; $('end').hidden = true; show('menu'); resize(); startDemo(); coinsUI(); refreshPreviews(); renderArenas(); }
function showEnd() {
  mode = 'end';
  const win = battle.result === 0;
  const a = ARENAS[save.arena];
  const earned = (win ? a.reward : 25) + battle.kills * 3;
  save.coins += earned; if (win) save.wins++; persist();
  $('endTitle').textContent = win ? 'SIEG!' : 'NIEDERLAGE';
  $('endTitle').className = win ? 'win' : 'lose';
  $('endSub').textContent = win ? `Die ${TEAM[battle.colors[1]].label}en sind zerflossen.` : `Das ${TEAM[battle.colors[1]].label}-Pigment hat deine Burg überrollt.`;
  $('endKills').textContent = battle.kills; $('endCoins').textContent = '+' + earned;
  $('end').hidden = false; Sfx.coin();
}

/* ---------- Verdrahtung ---------- */
for (const b of document.querySelectorAll('.tab')) b.onclick = () => { Sfx.init(); setTab(b.dataset.t); };
$('team_black').onclick = () => { save.team = 'black'; persist(); refreshPreviews(); startDemo(); };
$('team_white').onclick = () => { save.team = 'white'; persist(); refreshPreviews(); startDemo(); };
$('play').onclick = startGame;
$('cmdAtk').onclick = () => setCmd('attack');
$('cmdDef').onclick = () => setCmd('defend');
$('pauseBtn').onclick = pauseGame;
$('resume').onclick = resumeGame;
$('quit').onclick = toMenu;
$('again').onclick = () => { $('end').hidden = true; startGame(); };
$('endMenu').onclick = toMenu;
function syncSound() { for (const el of document.querySelectorAll('.snd')) el.textContent = save.sound ? '🔊' : '🔇'; }
for (const el of document.querySelectorAll('.snd')) el.onclick = () => { save.sound = !save.sound; persist(); syncSound(); Sfx.init(); };
syncSound();
window.addEventListener('resize', resize);
window.addEventListener('orientationchange', () => setTimeout(resize, 200));
document.addEventListener('visibilitychange', () => { if (document.hidden && mode === 'play') pauseGame(); });

let tab0 = 'play'; try { tab0 = localStorage.getItem(SAVE_KEY + '_tab') || 'play'; } catch (e) {}
setTab(tab0);
show('menu'); resize(); startDemo(); coinsUI(); renderArenas(); refreshPreviews();
requestAnimationFrame(t => { lastT = t; requestAnimationFrame(loop); });
// für Tests
window.__pk = { get battle() { return battle; }, save, startGame, view };
