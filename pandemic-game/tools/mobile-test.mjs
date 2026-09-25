// Handy-Test: emuliertes Smartphone (Touch, Hoch- und Querformat). Prüft mit
// echten Touch-Gesten über das DevTools-Protokoll: automatische Erkennung,
// Pinch-Zoom, Verschieben mit einem Finger, Land antippen, Zombie-Horde per
// Touch schicken, Evolution und alle Bildschirme ohne Konsolenfehler.
import { chromium } from 'playwright-core';
import path from 'node:path';
import fs from 'node:fs';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const shotDir = path.join(root, 'blender', 'shots');
fs.mkdirSync(shotDir, { recursive: true });
const url = 'file://' + path.join(root, 'dist', 'PANDEMIA.html');
const shot = (page, name) => page.screenshot({ path: path.join(shotDir, `mobile-${name}.png`) });

const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium', args: ['--use-gl=swiftshader', '--enable-webgl', '--ignore-gpu-blocklist'] });
let fail = 0;
const check = (ok, msg) => { console.log(`${ok ? 'OK ' : 'ERR'} ${msg}`); if (!ok) fail++; };
const errors = [];

async function phone(width, height) {
  const ctx = await browser.newContext({ viewport: { width, height }, deviceScaleFactor: 2, isMobile: true, hasTouch: true,
    userAgent: 'Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36' });
  const page = await ctx.newPage();
  page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()); });
  page.on('pageerror', (e) => errors.push('PAGEERROR: ' + e.message));
  const cdp = await ctx.newCDPSession(page);
  await page.goto(url, { waitUntil: 'load' });
  await page.waitForTimeout(1500);
  return { ctx, page, cdp };
}
const touch = (cdp, type, pts) => cdp.send('Input.dispatchTouchEvent', { type, touchPoints: pts.map(([x, y], i) => ({ x, y, id: i + 1, radiusX: 4, radiusY: 4, force: 1 })) });
async function pinch(cdp, cx, cy, from, to, steps = 12) {
  await touch(cdp, 'touchStart', [[cx - from, cy], [cx + from, cy]]);
  for (let i = 1; i <= steps; i++) { const d = from + (to - from) * i / steps; await touch(cdp, 'touchMove', [[cx - d, cy], [cx + d, cy]]); }
  await touch(cdp, 'touchEnd', []);
}
async function drag(cdp, x0, y0, x1, y1, steps = 10) {
  await touch(cdp, 'touchStart', [[x0, y0]]);
  for (let i = 1; i <= steps; i++) await touch(cdp, 'touchMove', [[x0 + (x1 - x0) * i / steps, y0 + (y1 - y0) * i / steps]]);
  await touch(cdp, 'touchEnd', []);
}
async function tap(cdp, x, y) { await touch(cdp, 'touchStart', [[x, y]]); await touch(cdp, 'touchEnd', []); }
const screenOf = (page, lon, lat) => page.evaluate(([lon, lat]) => { const m = window.__game.map; const [x, y] = m.proj(lon, lat); return [x * m.view.scale + m.view.x, y * m.view.scale + m.view.y]; }, [lon, lat]);

// ================= Hochformat =================
{
  const { ctx, page, cdp } = await phone(390, 844);
  const cls = await page.evaluate(() => document.documentElement.className);
  check(/mobile/.test(cls) && /portrait/.test(cls), `Handy erkannt (${cls})`);
  await shot(page, '01-menu');
  await page.tap('.menu-buttons .btn.primary'); await page.waitForTimeout(600);
  await page.tap('.type-row[data-key="necroa"]'); await page.waitForTimeout(700);
  await shot(page, '02-type-origin');
  await page.tap('.lore-tab:nth-child(2)'); await page.waitForTimeout(300);
  await shot(page, '03-type-does');
  await page.tap('.lore-tab:nth-child(3)'); await page.waitForTimeout(300);
  await shot(page, '04-type-pros');
  const loreOk = await page.evaluate(() => document.querySelector('.lore-body').textContent.length > 60);
  check(loreOk, 'Erreger-Beschreibung sichtbar');
  await page.tap('.ts-right .btn.primary'); await page.waitForTimeout(300);
  await page.fill('.name-input', 'Handy-Horde');
  await page.tap('.namescreen .btn.primary'); await page.waitForTimeout(700);
  // Startland per Antippen (Ägypten)
  const [ex, ey] = await screenOf(page, 30, 26);
  let [sx, sy] = [ex, ey];
  if (sx < 0 || sx > 390) { await page.evaluate(() => { const m = window.__game.map; const [x, y] = m.proj(30, 26); m.view.x = 195 - x * m.view.scale; m._clampView(); }); [sx, sy] = await screenOf(page, 30, 26); }
  await tap(cdp, sx, sy); await page.waitForTimeout(400);
  const picked = await page.evaluate(() => window.__ui.sel.startIso);
  check(picked === 'EGY', `Startland per Antippen gewählt (${picked})`);
  await shot(page, '05-start');
  await page.tap('.startscreen .btn.primary'); await page.waitForTimeout(900);
  await page.evaluate(() => window.__game.setSpeed(0));
  await shot(page, '06-game');

  // Pinch-Zoom
  const s0 = await page.evaluate(() => window.__game.map.view.scale);
  await pinch(cdp, 195, 450, 40, 150); await page.waitForTimeout(200);
  const s1 = await page.evaluate(() => window.__game.map.view.scale);
  check(s1 > s0 * 2, `Pinch-Zoom vergrößert (${s0.toFixed(2)} → ${s1.toFixed(2)})`);
  await shot(page, '07-pinch');
  await pinch(cdp, 195, 450, 150, 70); await page.waitForTimeout(200);
  const s2 = await page.evaluate(() => window.__game.map.view.scale);
  check(s2 < s1, `Pinch-Zoom verkleinert (${s1.toFixed(2)} → ${s2.toFixed(2)})`);

  // Verschieben mit einem Finger
  const v0 = await page.evaluate(() => ({ ...window.__game.map.view }));
  await drag(cdp, 300, 450, 120, 420); await page.waitForTimeout(700);
  const v1 = await page.evaluate(() => ({ ...window.__game.map.view }));
  check(v1.x < v0.x - 100, `Verschieben mit einem Finger (x ${v0.x.toFixed(0)} → ${v1.x.toFixed(0)}, inkl. Schwung)`);

  // Land antippen -> Länderfenster
  await page.evaluate(() => { const m = window.__game.map; m.view.scale = 2; m.centerOn('EGY'); });
  const [cx, cy] = await screenOf(page, 30, 26);
  await tap(cdp, cx, cy); await page.waitForTimeout(500);
  const panel = await page.evaluate(() => { const p = document.querySelector('.country-panel'); return p && p.style.display !== 'none' ? p.querySelector('h3').textContent : null; });
  check(panel === 'Ägypten', `Land antippen öffnet Länderfenster (${panel})`);
  await shot(page, '08-country');
  await page.tap('.country-panel .x'); await page.waitForTimeout(200);

  // Tippen aufs Meer schließt das Länderfenster
  await page.evaluate(() => window.__ui.showCountry('EGY'));
  const [ox, oy] = await screenOf(page, 19, 34);   // offenes Mittelmeer
  await tap(cdp, ox, oy); await page.waitForTimeout(500);
  const closed = await page.evaluate(() => document.querySelector('.country-panel').style.display === 'none');
  check(closed, 'Tippen aufs Meer schließt das Länderfenster');

  // Doppeltippen zoomt (ohne ein Land auszuwählen)
  const d0 = await page.evaluate(() => window.__game.map.view.scale);
  // (Der Software-Renderer im Test ist zu langsam für echte 0,3-s-Doppeltipps über
  //  das DevTools-Protokoll – daher zwei Touch-Pointer-Taps direkt hintereinander.)
  await page.evaluate(() => {
    const cv = window.__game.map.canvas, r = cv.getBoundingClientRect();
    const fire = (type) => cv.dispatchEvent(new PointerEvent(type, { pointerId: 77, pointerType: 'touch', isPrimary: true, clientX: r.left + 200, clientY: r.top + 300, bubbles: true }));
    fire('pointerdown'); fire('pointerup'); fire('pointerdown'); fire('pointerup');
  });
  await page.waitForTimeout(700);
  const d1 = await page.evaluate(() => window.__game.map.view.scale);
  check(d1 > d0 * 1.5, `Doppeltippen zoomt (${d0.toFixed(2)} → ${d1.toFixed(2)})`);
  const noPanel = await page.evaluate(() => document.querySelector('.country-panel').style.display === 'none');
  check(noPanel, 'Doppeltippen öffnet kein Länderfenster');

  // Zombies vorbereiten und Horde per Touch schicken
  await page.evaluate(() => {
    const e = window.__game.eng;
    for (const id of ['air1', 'contact1', 'reanimation', 'organ']) { e.dna += 80; e.evolve(id); }
    let g = 0; while (e.special.zombies < 5e5 && g++ < 400 && !e.gameOver) e.tick();
    window.__ui.refreshHud();
    const m = window.__game.map; m.view.scale = 1.6; m.centerOn('TUR');
  });
  await page.waitForTimeout(300);
  await page.tap('.sp-ability'); await page.waitForTimeout(300);
  await shot(page, '09-chooser');
  await page.tap('.chooser-btns .btn:nth-child(2)'); await page.waitForTimeout(300);
  const hint = await page.evaluate(() => { const t = document.querySelector('.target-hint'); return t && t.style.display !== 'none' ? t.textContent : null; });
  check(!!hint, `Zielauswahl-Hinweis mit Abbrechen (${hint})`);
  const [tx, ty] = await screenOf(page, 35, 39);   // Türkei
  await tap(cdp, tx, ty); await page.waitForTimeout(1100);
  const swarm = await page.evaluate(() => (window.__game.map.agents || []).filter((a) => a.kind === 'swarm').length);
  check(swarm > 0, `Zombie-Horde zieht als Punkteschwarm los (${swarm})`);
  await shot(page, '10-horde');
  await page.waitForTimeout(3500);
  const zTur = await page.evaluate(() => window.__game.eng.countries.TUR.zombies);
  check(zTur > 0, `Horde ist in der Türkei angekommen (${Math.round(zTur)} Zombies)`);

  // Evolution
  await page.tap('.disease-btn'); await page.waitForTimeout(800);
  await shot(page, '11-evo');
  await page.tap('.evo-tab:nth-child(3)'); await page.waitForTimeout(500);
  await shot(page, '12-evo-symptoms');
  const hexVisible = await page.evaluate(() => { const g = document.querySelector('.hexgrid'); if (!g) return false; const r = g.getBoundingClientRect(); return r.width > 0 && r.right <= window.innerWidth + 1 && r.left >= -1; });
  check(hexVisible, 'Symptom-Hexraster passt auf den Bildschirm');
  await page.tap('.hex'); await page.waitForTimeout(200);
  const detail = await page.evaluate(() => document.querySelector('.evo-detail').textContent.length > 10);
  check(detail, 'Symptom antippen zeigt Details');
  await page.tap('.evo-header .btn.back'); await page.waitForTimeout(400);
  await ctx.close();
}

// ================= Querformat =================
{
  const { ctx, page } = await phone(844, 390);
  const cls = await page.evaluate(() => document.documentElement.className);
  check(/mobile/.test(cls) && /landscape/.test(cls), `Querformat erkannt (${cls})`);
  await page.tap('.menu-buttons .btn.primary'); await page.waitForTimeout(600);
  await page.tap('.type-row[data-key="xenolith"]'); await page.waitForTimeout(600);
  await shot(page, '13-land-type');
  await page.tap('.ts-right .btn.primary'); await page.waitForTimeout(300);
  await page.tap('.namescreen .btn.primary'); await page.waitForTimeout(600);
  await page.evaluate(() => window.__ui._pickStart('BRA')); await page.waitForTimeout(200);
  await page.tap('.startscreen .btn.primary'); await page.waitForTimeout(4000);
  await page.evaluate(() => { const e = window.__game.eng; for (let i = 0; i < 90; i++) e.tick(); window.__ui.refreshHud(); });
  await page.waitForTimeout(800);
  await shot(page, '14-land-game');
  await page.tap('.disease-btn'); await page.waitForTimeout(800);
  await shot(page, '15-land-evo');
  // nichts darf seitlich überlaufen
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth + 1);
  check(!overflow, 'Kein seitliches Überlaufen');
  await ctx.close();
}

await browser.close();
const real = errors.filter((e) => !/GroupMarkerNotSet|SwiftShader|Automatic fallback|GL_|deprecated/i.test(e));
check(real.length === 0, `Keine Konsolenfehler (${real.length})`);
real.slice(0, 5).forEach((e) => console.log('   ', e));
if (fail) { console.log(`\n${fail} Problem(e) ✗`); process.exit(1); }
console.log('\nHandy-Test bestanden ✓');
