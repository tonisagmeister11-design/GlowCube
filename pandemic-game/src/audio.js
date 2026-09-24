// Musiksteuerung.
//  - Menü (und alle Vor-Spiel-Bildschirme): "Investigative Atmosphere", in Schleife.
//  - Im Spiel: zwei "Omnipresent Watch"-Tracks im Wechsel. Wenn ein Track endet,
//    kurze Stille, dann faded der nächste Track langsam ein (sauberer Übergang).
//  - Wird die Runde gestoppt (Spielende / zurück ins Menü): Spielmusik stoppt,
//    Menümusik läuft wieder.
//  - Beim Start einer neuen Runde beginnt die Spielmusik von vorne (Track 0, 0:00).
import { AUDIO } from './generated/audio.js';

export class AudioManager {
  constructor() {
    this.target = 0.6;
    this.enabled = true;
    this.mode = null;                 // 'menu' | 'game' | null
    this.gameIndex = 0;
    this.gameSrcs = [AUDIO.game1, AUDIO.game2];
    this.menuSrc = AUDIO.menu;
    this.el = new Audio();
    this.el.preload = 'auto';
    this.el.crossOrigin = 'anonymous';
    this.el.volume = 0;
    this.el.addEventListener('ended', () => this._onEnded());
    this._fadeRaf = null;
    this._silenceTimer = null;
    this._pendingMode = 'menu';
    // Browser erlauben Audio erst nach einer Nutzerinteraktion
    const kick = () => {
      this._unlocked = true;
      if (this.mode === null) this._apply(this._pendingMode);
      else this.el.play().catch(() => {});
      window.removeEventListener('pointerdown', kick);
      window.removeEventListener('keydown', kick);
      window.removeEventListener('touchstart', kick);
    };
    window.addEventListener('pointerdown', kick);
    window.addEventListener('keydown', kick);
    window.addEventListener('touchstart', kick);
  }

  playMenu() { this._request('menu'); }
  playGame() { this._request('game'); }

  _request(mode) {
    this._pendingMode = mode;
    if (!this._unlocked) return;      // wird beim ersten Klick nachgeholt
    this._apply(mode);
  }

  _apply(mode) {
    if (mode === 'menu') {
      this.mode = 'menu';
      this._clearTimers();
      this.el.loop = true;
      if (this.el.src !== this.menuSrc) this.el.src = this.menuSrc;
      try { this.el.currentTime = 0; } catch {}
      this._safePlay();
      this._fadeTo(this.enabled ? this.target : 0, 900);
    } else if (mode === 'game') {
      this.mode = 'game';
      this.gameIndex = 0;
      this._clearTimers();
      this.el.loop = false;
      this._startGameTrack(0);
    }
  }

  _startGameTrack(i) {
    this.el.src = this.gameSrcs[i];
    try { this.el.currentTime = 0; } catch {}
    this.el.volume = 0;
    this._safePlay();
    this._fadeTo(this.enabled ? this.target : 0, 1400);
  }

  _onEnded() {
    if (this.mode !== 'game') return;
    // kurze Stille, dann nächsten Track langsam einblenden
    this.el.volume = 0;
    this._silenceTimer = setTimeout(() => {
      this.gameIndex = (this.gameIndex + 1) % this.gameSrcs.length;
      this._startGameTrack(this.gameIndex);
    }, 1000);
  }

  stop() {
    this._clearTimers();
    this._fadeTo(0, 500, () => this.el.pause());
    this.mode = null;
  }

  setEnabled(on) {
    this.enabled = on;
    if (!on) this._fadeTo(0, 300, () => this.el.pause());
    else { this._safePlay(); this._fadeTo(this.target, 400); }
  }

  toggle() { this.setEnabled(!this.enabled); return this.enabled; }

  _safePlay() { if (this.enabled) this.el.play().catch(() => {}); }

  _fadeTo(v, ms, cb) {
    if (this._fadeRaf) cancelAnimationFrame(this._fadeRaf);
    const start = this.el.volume;
    const t0 = performance.now();
    const step = (t) => {
      const k = Math.min(1, (t - t0) / ms);
      this.el.volume = Math.max(0, Math.min(1, start + (v - start) * k));
      if (k < 1) this._fadeRaf = requestAnimationFrame(step);
      else { this._fadeRaf = null; if (cb) cb(); }
    };
    this._fadeRaf = requestAnimationFrame(step);
  }

  _clearTimers() {
    if (this._silenceTimer) { clearTimeout(this._silenceTimer); this._silenceTimer = null; }
    if (this._fadeRaf) { cancelAnimationFrame(this._fadeRaf); this._fadeRaf = null; }
  }
}
