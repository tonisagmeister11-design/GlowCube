// Spiel-Controller: verbindet Simulation (Engine), Weltkarte und 3D-Viewer,
// steuert Geschwindigkeit (Pause/1x/2x/4x/8x/16x) und den Tick-/Render-Loop.
import { Engine } from './sim/engine.js';
import { WorldMap } from './map/map.js';

export const SPEEDS = [0, 1, 2, 4, 8, 16];

export class Game {
  constructor({ world, mapCanvas, viewer }) {
    this.world = world;
    this.map = new WorldMap(mapCanvas, world);
    this.viewer = viewer;             // optionaler PathogenViewer (Krankheits-Ecke)
    this.eng = null;
    this.speedIndex = 2;              // 1x
    this._acc = 0;
    this._raf = null;
    this._last = performance.now();
    this.dayFraction = 0;
    this.listeners = {};
    this.map.onBubble = (b) => {
      const gain = this.eng.clickBubble(b);
      this.emit('dna', gain);
    };
  }

  on(ev, fn) { (this.listeners[ev] ||= []).push(fn); }
  emit(ev, ...a) { (this.listeners[ev] || []).forEach((f) => f(...a)); }

  start(opts) {
    this.eng = new Engine(this.world, opts);
    this.eng.opts._speed = 1;
    this.map.setEngine(this.eng);
    this.map.mode = 'play';
    this.map.selectedIso = null;
    this.map.focusCountry(opts.startIso, 2.4);
    this.setSpeed(2);
    if (!this._raf) this._loop();
  }

  setSpeed(i) {
    this.speedIndex = Math.max(0, Math.min(SPEEDS.length - 1, i));
    this.emit('speed', this.speedIndex);
  }
  togglePause() { this.setSpeed(this.speedIndex === 0 ? this._lastRun || 2 : (this._lastRun = this.speedIndex, 0)); }
  get speed() { return SPEEDS[this.speedIndex]; }
  get paused() { return this.speed === 0; }

  _loop() {
    this._raf = requestAnimationFrame(() => this._loop());
    const now = performance.now();
    let dt = (now - this._last) / 1000; this._last = now;
    dt = Math.min(dt, 0.1);
    const eng = this.eng;
    if (eng && !eng.gameOver && this.speed > 0) {
      // Ein Spieltag pro (0.9s / speed)
      const tickDur = 0.9;
      this._acc += dt * this.speed;
      let ticks = 0;
      eng.opts._speed = 1; // Fahrzeug-Geschwindigkeit unabhängig; Ticks steuern Tempo
      while (this._acc >= tickDur && ticks < 40) {
        this._acc -= tickDur;
        // Fahrzeuge feiner bewegen: pro Tag mehrere Sub-Updates passieren in engine.updateVehicles
        eng.tick();
        ticks++;
        for (const b of eng.collectBubbles()) this.map.spawnBubble(b);
        if (eng.gameOver) { this.emit('gameover', eng.gameOver); break; }
      }
      this.dayFraction = this._acc / tickDur;
      if (ticks) this.emit('tick', eng);
    }
    // Karte animieren (Fahrzeuge visuell weiterbewegen zwischen Ticks)
    if (eng && this.speed > 0) {
      const vdt = dt * this.speed;
      for (const v of eng.vehicles) v.t = Math.min(1, v.t + v.speed * this.speed * 0.4);
    }
    this.map.update(dt);
    this.map.render(now / 1000);
    this.emit('frame', dt);
  }

  stop() { if (this._raf) cancelAnimationFrame(this._raf); this._raf = null; }
}
