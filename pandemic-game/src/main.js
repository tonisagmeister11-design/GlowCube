// Einstiegspunkt: baut das Grundgerüst, initialisiert 3D-Viewer, Spiel-Controller
// und Oberfläche. Die Weltdaten und Blender-Modelle sind eingebettet (Single-HTML).
import world from './data/world.json';
import { PathogenViewer } from './three/viewer.js';
import { Game } from './game.js';
import { UI } from './ui.js';
import { AudioManager } from './audio.js';

function boot() {
  const app = document.getElementById('app');

  // Persistenter 3D-Viewer (Menü-Mittelpunkt / Krankheitsecke)
  const viewerHome = document.createElement('div');
  viewerHome.id = 'viewer-home';
  const viewerCanvas = document.createElement('canvas');
  viewerCanvas.id = 'pathogen-canvas';
  viewerHome.append(viewerCanvas);
  app.append(viewerHome);

  const viewer = new PathogenViewer(viewerCanvas, { particles: true, quality: 1 });
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
