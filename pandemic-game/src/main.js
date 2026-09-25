// Einstiegspunkt: baut das Grundgerüst, initialisiert 3D-Viewer, Spiel-Controller
// und Oberfläche. Die Weltdaten und Blender-Modelle sind eingebettet (Single-HTML).
import world from './data/world.json';
import { PathogenViewer } from './three/viewer.js';
import { Game } from './game.js';
import { UI } from './ui.js';
import { AudioManager } from './audio.js';
import { device, applyDevice, lockLandscapeOnFirstTap } from './device.js';

function boot() {
  const app = document.getElementById('app');
  // Handy oder Laptop/PC? Handys laufen immer im Querformat.
  applyDevice();
  const mobile = device.mobile;
  lockLandscapeOnFirstTap();

  // Persistenter 3D-Viewer (Menü-Mittelpunkt / Krankheitsecke)
  const viewerHome = document.createElement('div');
  viewerHome.id = 'viewer-home';
  const viewerCanvas = document.createElement('canvas');
  viewerCanvas.id = 'pathogen-canvas';
  viewerHome.append(viewerCanvas);
  app.append(viewerHome);

  const viewer = new PathogenViewer(viewerCanvas, { particles: true, quality: mobile ? 0.7 : 1 });
  viewer.setPathogen('virus');

  // Karten-Canvas (wird von der UI zwischen Screens verschoben)
  const mapCanvas = document.createElement('canvas');
  mapCanvas.id = 'map-canvas';

  const game = new Game({ world, mapCanvas, viewer });

  const root = document.createElement('div');
  root.id = 'screens';
  app.append(root);

  const audio = new AudioManager();
  const ui = new UI(root, game, viewer);
  ui.mobile = mobile;
  ui.audio = audio;
  ui.viewerHome = viewerHome;
  ui.show('menu');
  audio.playMenu();

  // Ton an/aus-Knopf (immer sichtbar)
  const muteBtn = document.createElement('button');
  muteBtn.id = 'mute-btn';
  muteBtn.title = 'Musik an/aus';
  muteBtn.textContent = '🔊';
  muteBtn.addEventListener('click', () => { muteBtn.textContent = audio.toggle() ? '🔊' : '🔇'; });
  app.append(muteBtn);

  // Vollbild-Knopf auf dem Handy (wo der Browser es erlaubt)
  const fsTarget = document.documentElement;
  if (mobile && (fsTarget.requestFullscreen || fsTarget.webkitRequestFullscreen)) {
    const fsBtn = document.createElement('button');
    fsBtn.id = 'fs-btn'; fsBtn.title = 'Vollbild'; fsBtn.textContent = '⛶';
    fsBtn.addEventListener('click', () => {
      const on = document.fullscreenElement || document.webkitFullscreenElement;
      if (on) (document.exitFullscreen || document.webkitExitFullscreen).call(document);
      else (fsTarget.requestFullscreen || fsTarget.webkitRequestFullscreen).call(fsTarget).catch?.(() => {});
    });
    app.append(fsBtn);
  }

  // Drehen / Größe ändern: Geräteklassen neu setzen, dann Karte und 3D-Ansicht anpassen
  let rT = null;
  const onResize = () => {
    applyDevice();
    clearTimeout(rT);
    rT = setTimeout(() => {
      applyDevice();
      const m = game.map;
      if (m.canvas.parentElement && (m.canvas.parentElement.clientWidth !== m.cw || m.canvas.parentElement.clientHeight !== m.ch)) {
        const c = m._toWorld(m.cw / 2, m.ch / 2), oldW = m.baseW;
        m.resize();
        // gleiche Kartenmitte beibehalten
        const k = m.baseW / oldW;
        m.view.x = m.cw / 2 - c.x * k * m.view.scale; m.view.y = m.ch / 2 - c.y * k * m.view.scale; m._clampView();
      }
      viewer.resize();
    }, 220);
  };
  window.addEventListener('resize', onResize);
  window.addEventListener('orientationchange', onResize);
  document.addEventListener('fullscreenchange', onResize);

  // Test-/Debug-Hooks (für den Browser-Test)
  window.__game = game;
  window.__ui = ui;
  window.__assets = viewer.assets;
  window.__device = device;

  // Tastatur: Leertaste = Pause, 1-5 = Geschwindigkeit
  window.addEventListener('keydown', (e) => {
    if (ui.active !== 'game') return;
    if (e.code === 'Space') { e.preventDefault(); game.togglePause(); }
    else if (e.key >= '1' && e.key <= '5') game.setSpeed(+e.key);
  });

  // Ladeanzeige entfernen
  document.getElementById('loading')?.remove();
}

if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot);
else boot();
