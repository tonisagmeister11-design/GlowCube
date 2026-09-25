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
// Element wie mit dem Finger antippen: echte Touch-Events in der Bildschirmmitte
// des Elements (funktioniert auch bei gedrehter Oberfläche)
async function tapEl(page, cdp, sel, wait = 350) {
  const c = await page.evaluate((sel) => {
    const e = document.querySelector(sel); if (!e) return null;
    e.scrollIntoView({ block: 'nearest', inline: 'nearest' });
    const r = e.getBoundingClientRect(); return [r.left + r.width / 2, r.top + r.height / 2];
  }, sel);
  if (!c) throw new Error('Element fehlt: ' + sel);
  await tap(cdp, c[0], c[1]);
  await page.waitForTimeout(wait);
}

// Koordinaten der (ggf. um 90° gedrehten) Oberfläche -> Bildschirm-Koordinaten
const toScreen = (page, x, y) => page.evaluate(([x, y]) => (window.__device.rotated ? [innerWidth - y, x] : [x, y]), [x, y]);
const screenOf = async (page, lon, lat) => {
  const [x, y] = await page.evaluate(([lon, lat]) => { const m = window.__game.map; const [x, y] = m.proj(lon, lat); return [x * m.view.scale + m.view.x, y * m.view.scale + m.view.y]; }, [lon, lat]);
  return toScreen(page, x, y);
};
const appSize = (page) => page.evaluate(() => { const a = document.getElementById('app'); return [a.clientWidth, a.clientHeight]; });

// Gesamter Spielablauf mit Touch-Gesten (einmal hochkant gehalten, einmal quer)
async function gameFlow(width, height, tag, expectRotated) {
  const { ctx, page, cdp } = await phone(width, height);
  const dev = await page.evaluate(() => ({ ...window.__device, cls: document.documentElement.className }));
  check(dev.phone && dev.landscape && dev.rotated === expectRotated,
    `[${tag}] Handy erkannt, Spiel im Querformat${expectRotated ? ' (Oberfläche gedreht)' : ''} – ${dev.cls}`);
  const [aw, ah] = await appSize(page);
  check(aw > ah, `[${tag}] Spielfläche ist quer (${aw}×${ah})`);
  const chip = await page.evaluate(() => document.querySelector('.device-chip')?.textContent);
  check(/Handy/.test(chip || ''), `[${tag}] Menü zeigt erkanntes Gerät (${chip})`);
  await shot(page, `${tag}-01-menu`);
  await tapEl(page, cdp, '.menu-buttons .btn.primary', 600);
  await tapEl(page, cdp, '.type-row[data-key="necroa"]', 700);
  await shot(page, `${tag}-02-type`);
  await tapEl(page, cdp, '.lore-tab:nth-child(2)', 300);
  const loreOk = await page.evaluate(() => document.querySelector('.lore-body').textContent.length > 60);
  check(loreOk, `[${tag}] Erreger-Beschreibung per Tippen umschaltbar`);
  await tapEl(page, cdp, '.ts-right .btn.primary', 300);
  await page.fill('.name-input', 'Handy-Horde');
  await tapEl(page, cdp, '.namescreen .btn.primary', 800);

  // Startland per Antippen (Ägypten)
  await page.evaluate(() => { const m = window.__game.map; m.view.scale = 1.4; m.centerOn('EGY'); });
  let [sx, sy] = await screenOf(page, 30, 26);
  await tap(cdp, sx, sy); await page.waitForTimeout(500);
  const picked = await page.evaluate(() => window.__ui.sel.startIso);
  check(picked === 'EGY', `[${tag}] Startland per Antippen gewählt (${picked})`);
  await tapEl(page, cdp, '.startscreen .btn.primary', 900);
  await page.evaluate(() => window.__game.setSpeed(0));
  await shot(page, `${tag}-03-game`);

  // Pinch-Zoom um die Bildmitte
  const [pcx, pcy] = await toScreen(page, aw / 2, ah / 2);
  const s0 = await page.evaluate(() => window.__game.map.view.scale);
  await pinch(cdp, pcx, pcy, 30, 110); await page.waitForTimeout(200);
  const s1 = await page.evaluate(() => window.__game.map.view.scale);
  check(s1 > s0 * 2, `[${tag}] Pinch-Zoom vergrößert (${s0.toFixed(2)} → ${s1.toFixed(2)})`);
  await pinch(cdp, pcx, pcy, 110, 50); await page.waitForTimeout(200);
  const s2 = await page.evaluate(() => window.__game.map.view.scale);
  check(s2 < s1, `[${tag}] Pinch-Zoom verkleinert (${s1.toFixed(2)} → ${s2.toFixed(2)})`);

  // Verschieben mit einem Finger (in Spielrichtung nach links wischen)
  const v0 = await page.evaluate(() => ({ ...window.__game.map.view }));
  const [d0x, d0y] = await toScreen(page, aw * 0.75, ah * 0.5);
  const [d1x, d1y] = await toScreen(page, aw * 0.35, ah * 0.5);
  await drag(cdp, d0x, d0y, d1x, d1y); await page.waitForTimeout(700);
  const v1 = await page.evaluate(() => ({ ...window.__game.map.view }));
  check(v1.x < v0.x - 100 && Math.abs(v1.y - v0.y) < 60, `[${tag}] Wischen verschiebt in die richtige Richtung (x ${v0.x.toFixed(0)} → ${v1.x.toFixed(0)})`);

  // Land antippen -> Länderfenster
  await page.evaluate(() => { const m = window.__game.map; m.view.scale = 2; m.centerOn('EGY'); });
  const [cx, cy] = await screenOf(page, 30, 26);
  await tap(cdp, cx, cy); await page.waitForTimeout(500);
  const panel = await page.evaluate(() => { const p = document.querySelector('.country-panel'); return p && p.style.display !== 'none' ? p.querySelector('h3').textContent : null; });
  check(panel === 'Ägypten', `[${tag}] Land antippen öffnet Länderfenster (${panel})`);
  await shot(page, `${tag}-04-country`);

  // Tippen aufs Meer schließt das Länderfenster
  const [ox, oy] = await screenOf(page, 19, 34);   // offenes Mittelmeer
  await tap(cdp, ox, oy); await page.waitForTimeout(500);
  const closed = await page.evaluate(() => document.querySelector('.country-panel').style.display === 'none');
  check(closed, `[${tag}] Tippen aufs Meer schließt das Länderfenster`);

  // Doppeltippen zoomt (ohne ein Land auszuwählen). Der Software-Renderer im Test
  // ist zu langsam für echte 0,3-s-Doppeltipps über das DevTools-Protokoll –
  // daher zwei Touch-Pointer-Taps direkt hintereinander (in Bildschirm-Koordinaten).
  const dz0 = await page.evaluate(() => window.__game.map.view.scale);
  const [qx, qy] = await toScreen(page, aw * 0.5, ah * 0.3);
  await page.evaluate(([qx, qy]) => {
    const cv = window.__game.map.canvas;
    const fire = (type) => cv.dispatchEvent(new PointerEvent(type, { pointerId: 77, pointerType: 'touch', isPrimary: true, clientX: qx, clientY: qy, bubbles: true }));
    fire('pointerdown'); fire('pointerup'); fire('pointerdown'); fire('pointerup');
  }, [qx, qy]);
  await page.waitForTimeout(700);
  const dz1 = await page.evaluate(() => window.__game.map.view.scale);
  check(dz1 > dz0 * 1.5, `[${tag}] Doppeltippen zoomt (${dz0.toFixed(2)} → ${dz1.toFixed(2)})`);

  // Zombies vorbereiten und Horde per Touch schicken
  await page.evaluate(() => {
    const e = window.__game.eng;
    for (const id of ['air1', 'contact1', 'reanimation', 'organ']) { e.dna += 80; e.evolve(id); }
    let g = 0; while (e.special.zombies < 5e5 && g++ < 400 && !e.gameOver) e.tick();
    window.__ui.refreshHud();
    const m = window.__game.map; m.view.scale = 1.6; m.centerOn('TUR');
  });
  await page.waitForTimeout(300);
  await tapEl(page, cdp, '.sp-ability', 300);
  await tapEl(page, cdp, '.chooser-btns .btn:nth-child(2)', 300);
  const hint = await page.evaluate(() => { const t = document.querySelector('.target-hint'); return t && t.style.display !== 'none' ? t.textContent : null; });
  check(!!hint, `[${tag}] Zielauswahl-Hinweis mit Abbrechen`);
  const [tx, ty] = await screenOf(page, 35, 39);   // Türkei
  await tap(cdp, tx, ty); await page.waitForTimeout(1100);
  const swarm = await page.evaluate(() => (window.__game.map.agents || []).filter((a) => a.kind === 'swarm').length);
  check(swarm > 0, `[${tag}] Zombie-Horde zieht als Punkteschwarm los`);
  await shot(page, `${tag}-05-horde`);
  await page.waitForTimeout(3500);
  const zTur = await page.evaluate(() => window.__game.eng.countries.TUR.zombies);
  check(zTur > 0, `[${tag}] Horde ist in der Türkei angekommen (${Math.round(zTur)} Zombies)`);

  // Evolution
  await tapEl(page, cdp, '.disease-btn', 800);
  await tapEl(page, cdp, '.evo-tab:nth-child(3)', 500);
  await shot(page, `${tag}-06-evo`);
  const hexVisible = await page.evaluate(() => { const g = document.querySelector('.hexgrid'); if (!g) return false; const r = g.getBoundingClientRect(); return r.width > 0 && r.left >= -1 && r.top >= -1 && r.right <= innerWidth + 1 && r.bottom <= innerHeight + 1; });
  check(hexVisible, `[${tag}] Symptom-Hexraster liegt im Bild`);
  await tapEl(page, cdp, '.hex', 200);
  const detail = await page.evaluate(() => document.querySelector('.evo-detail').textContent.length > 10);
  check(detail, `[${tag}] Symptom antippen zeigt Details`);
  await tapEl(page, cdp, '.evo-header .btn.back', 400);
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth > innerWidth + 1 || document.documentElement.scrollHeight > innerHeight + 1);
  check(!overflow, `[${tag}] Nichts ragt über den Bildschirm hinaus`);
  await ctx.close();
}

await gameFlow(390, 844, 'hochkant', true);    // Handy hochkant gehalten -> Oberfläche wird gedreht
await gameFlow(844, 390, 'quer', false);       // Handy quer gehalten

// Laptop/PC darf NICHT als Handy erkannt werden
{
  const ctx = await browser.newContext({ viewport: { width: 1366, height: 768 } });
  const page = await ctx.newPage();
  await page.goto(url, { waitUntil: 'load' }); await page.waitForTimeout(1200);
  const d = await page.evaluate(() => ({ ...window.__device, chip: document.querySelector('.device-chip')?.textContent }));
  check(!d.mobile && !d.rotated && d.kind === 'pc' && /Laptop/.test(d.chip), `Laptop erkannt (${d.chip})`);
  await ctx.close();
}

await browser.close();
const real = errors.filter((e) => !/GroupMarkerNotSet|SwiftShader|Automatic fallback|GL_|deprecated/i.test(e));
check(real.length === 0, `Keine Konsolenfehler (${real.length})`);
real.slice(0, 5).forEach((e) => console.log('   ', e));
if (fail) { console.log(`\n${fail} Problem(e) ✗`); process.exit(1); }
console.log('\nHandy-Test bestanden ✓');
