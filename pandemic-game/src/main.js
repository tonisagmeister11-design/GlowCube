// Einstiegspunkt: baut das Grundgerüst, initialisiert 3D-Viewer, Spiel-Controller
// und Oberfläche. Die Weltdaten und Blender-Modelle sind eingebettet (Single-HTML).
import world from './data/world.json';
import { PathogenViewer } from './three/viewer.js';
import { Game } from './game.js';
import { UI } from './ui.js';
import { AudioManager } from './audio.js';

// Handy/Tablet automatisch erkennen: grober Zeiger (Finger) oder Touch-Gerät
// mit kleinem Bildschirm. Setzt CSS-Klassen für das mobile Layout.
function detectMobile() {
  const coarse = window.matchMedia && matchMedia('(pointer: coarse)').matches;
  const touch = (navigator.maxTouchPoints || 0) > 0 || 'ontouchstart' in window;
  const small = Math.min(window.innerWidth, window.innerHeight) < 600;
  return !!(coarse || (touch && small));
}
function applyDeviceClasses() {
  const root = document.documentElement;
  const mobile = detectMobile();
  root.classList.toggle('mobile', mobile);
  root.classList.toggle('portrait', window.innerHeight > window.innerWidth);
  root.classList.toggle('landscape', window.innerHeight <= window.innerWidth);
  root.classList.toggle('compact', Math.min(window.innerWidth, window.innerHeight) < 520);
  return mobile;
}

function boot() {
  const app = document.getElementById('app');
  const mobile = applyDeviceClasses();
  window.addEventListener('resize', applyDeviceClasses);
  window.addEventListener('orientationchange', () => setTimeout(applyDeviceClasses, 150));

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

  // Test-/Debug-Hooks (für den Browser-Test)
  window.__game = game;
  window.__ui = ui;
  window.__assets = viewer.assets;

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
