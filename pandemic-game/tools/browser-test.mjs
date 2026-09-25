// Browser-Test mit Playwright (Chromium ist vorinstalliert). Lädt dist/PANDEMIA.html,
// prüft, dass WebGL, alle Blender-Modelle und die UI funktionieren, spielt eine
// kurze Partie und macht Screenshots der wichtigsten Bildschirme.
import { chromium } from 'playwright-core';
import path from 'node:path';
import fs from 'node:fs';
import { fileURLToPath } from 'node:url';
import { PATHOGEN_ORDER } from '../src/data/pathogens.js';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const shotDir = path.join(root, 'blender', 'shots');
fs.mkdirSync(shotDir, { recursive: true });
const url = 'file://' + path.join(root, 'dist', 'PANDEMIA.html');

const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium', args: ['--use-gl=swiftshader', '--enable-webgl', '--ignore-gpu-blocklist'] });
const page = await browser.newPage({ viewport: { width: 1280, height: 720 }, deviceScaleFactor: 1 });
const errors = [];
page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()); });
page.on('pageerror', (e) => errors.push('PAGEERROR: ' + e.message));

await page.goto(url, { waitUntil: 'load' });
await page.waitForTimeout(1500);

// Direkt im Seitenkontext den AssetManager nutzen (global bereitgestellt)
const probe = await page.evaluate(async (keys) => {
  const out = {};
  try {
    const mgr = window.__assets;
    if (!mgr) return { error: 'kein AssetManager global' };
    for (const k of keys) {
      const loaded = await mgr.load(k);
      let tris = 0;
      loaded.scene.traverse((o) => { if (o.isMesh && o.geometry) { const g = o.geometry; tris += (g.index ? g.index.count : g.attributes.position.count) / 3; } });
      out[k] = { fallback: !!loaded.fallback, anims: Object.keys(loaded.clips).length, tris: Math.round(tris), evo: loaded.evoNodes.length };
    }
  } catch (e) { out.error = e.message; }
  return out;
}, PATHOGEN_ORDER);

console.log('=== Modell-Ladeprüfung im Browser ===');
let fail = 0;
if (probe.error) { console.log('FEHLER:', probe.error); fail++; }
for (const k of PATHOGEN_ORDER) {
  const r = probe[k];
  if (!r) { console.log(k, 'FEHLT'); fail++; continue; }
  const bad = r.fallback || r.tris < 100 || r.anims < 1;
  console.log(`${bad ? 'ERR' : 'OK '} ${k.padEnd(10)} tris=${String(r.tris).padStart(6)} anims=${r.anims} evo=${r.evo}${r.fallback ? ' FALLBACK!' : ''}`);
  if (bad) fail++;
}

// Menü-Screenshot
await page.screenshot({ path: path.join(shotDir, '01-menu.png') });

// Spielablauf: Neues Spiel -> Virus -> Name -> Startland -> Spiel
await page.click('.menu-buttons .btn.primary');
await page.waitForTimeout(600);
await page.screenshot({ path: path.join(shotDir, '02-typeselect.png') });
// Necroa wählen (Zombie-Sondermechanik)
await page.click('.type-row[data-key="necroa"]');
await page.waitForTimeout(500);
await page.click('.ts-right .btn.primary'); // Weiter -> Name
await page.waitForTimeout(300);
await page.fill('.name-input', 'Test-Seuche');
await page.click('.namescreen .btn.primary'); // -> Startland
await page.waitForTimeout(600);
await page.screenshot({ path: path.join(shotDir, '03-startcountry.png') });
// Startland per Simulation setzen (Klick auf Karte ist unzuverlässig) und starten
await page.evaluate(() => { window.__ui._pickStart('CHN'); });
await page.waitForTimeout(200);
await page.click('.startscreen .btn.primary'); // Ausbruch starten
await page.waitForTimeout(400);
// Auf 4x stellen und laufen lassen
await page.evaluate(() => window.__game.setSpeed(4));
await page.waitForTimeout(1500);
await page.screenshot({ path: path.join(shotDir, '04-game.png') });

// Jeder Landesteil muss antippbar sein – auch weit vom Landesmittelpunkt entfernte
// (früher waren z.B. Alaska und Französisch-Guayana nicht anklickbar)
const hits = await page.evaluate(() => {
  const m = window.__game.map; m.view.scale = 1; m._clampView();
  const at = (lon, lat) => { const [x, y] = m.proj(lon, lat); return m._hit(x + m.view.x, y + m.view.y); };
  return { USA_Alaska: at(-150, 64), FRA_Guayana: at(-53, 4), RUS_Kaliningrad: at(21, 54.7), USA_Hawaii: at(-155.5, 19.6), RUS_Tschukotka: at(175, 66) };
});
for (const [k, v] of Object.entries(hits)) {
  const ok = v === k.slice(0, 3);
  console.log(`${ok ? 'OK ' : 'ERR'} Antippen ${k}: ${v}`);
  if (!ok) fail++;
}

// Evolution öffnen
await page.click('.disease-btn');
await page.waitForTimeout(800);
await page.screenshot({ path: path.join(shotDir, '05-evolution-transmission.png') });
await page.click('.evo-tab:nth-child(3)'); // Symptome (Hexraster)
await page.waitForTimeout(500);
await page.screenshot({ path: path.join(shotDir, '06-evolution-symptoms.png') });
// Ein Symptom entwickeln, falls möglich
const evolved = await page.evaluate(() => {
  const eng = window.__game.eng; eng.dna += 50;
  window.__ui.refreshEvolution();
  const node = document.querySelector('.hex');
  if (node) node.click();
  const btn = document.querySelector('.evo-detail .btn.evolve');
  if (btn && !btn.disabled) { btn.click(); return true; }
  return false;
});
await page.waitForTimeout(400);
await page.screenshot({ path: path.join(shotDir, '07-evolution-after.png') });
console.log('Symptom entwickelt:', evolved);

// zurück zur Karte, schnell durchsimulieren bis Spielende
await page.click('.evo-header .btn.back');
await page.waitForTimeout(300);
const ended = await page.evaluate(async () => {
  const g = window.__game; const eng = g.eng;
  // aggressive Auto-Evolution + Zeitraffer
  const order = ['air1', 'water1', 'contact1', 'air2', 'water2', 'env1', 'cold1', 'heat1', 'drug1', 'reanimation', 'horde', 'cytopathic', 'organ', 'hemorrhage', 'shock', 'necrosis'];
  let bi = 0, guard = 0;
  while (!eng.gameOver && guard++ < 4000) {
    for (const b of eng.collectBubbles()) eng.clickBubble(b);
    eng.dna += 3;
    while (bi < order.length) { const id = order[bi]; if (eng.evolved.has(id)) { bi++; continue; } if (eng.canEvolve(id)) { eng.evolve(id); eng.triggerAbility(id); bi++; } else break; }
    eng.tick();
    for (const b of eng.collectBubbles()) g.map.spawnBubble(b);
  }
  return eng.gameOver;
});
console.log('Spielende:', JSON.stringify(ended));
await page.waitForTimeout(500);
// Endscreen erscheint über gameover-Event nur bei natürlichem Loop; hier manuell:
await page.evaluate((g) => window.__ui.showEnd(g), ended);
await page.waitForTimeout(600);
await page.screenshot({ path: path.join(shotDir, '08-end.png') });

// relevante Fehler filtern (WebGL-SwiftShader-Rauschen ignorieren)
const realErrors = errors.filter((e) => !/GroupMarkerNotSet|SwiftShader|Automatic fallback|GL_|deprecated/i.test(e));
console.log('\nKonsolenfehler:', realErrors.length);
realErrors.slice(0, 10).forEach((e) => console.log('  ', e));

await browser.close();
if (fail) { console.log(`\n${fail} Modellproblem(e) ✗`); process.exit(1); }
if (!ended || !ended.win) { console.log('\n⚠ Partie endete nicht mit Sieg'); }
if (realErrors.length) { console.log('\n⚠ Konsolenfehler vorhanden'); process.exit(1); }
console.log('\nBrowser-Test bestanden ✓');
