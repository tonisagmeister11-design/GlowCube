// Erzeugt src/data/geo50.json: detaillierte Länderumrisse (world-atlas 50m) nur
// für die Darstellung, das Antippen und die Infektionspunkte. Die Simulations-
// daten (world.json, 110m) bleiben unverändert. Nur Länder, die im Spiel
// vorkommen; Koordinaten auf 0,01° gerundet, fast doppelte Punkte entfernt.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';
import * as topojson from 'topojson-client';

const require = createRequire(import.meta.url);
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const topo = structuredClone(require('world-atlas/countries-50m.json'));
const wc = require('world-countries');
const world = JSON.parse(fs.readFileSync(path.join(root, 'src/data/world.json'), 'utf8'));
const wanted = new Set(world.countries.map((c) => c.iso));

const byNum = Object.fromEntries(wc.map((c) => [c.ccn3, c]));
const NAME_FIX = { 'N. Cyprus': 'CYP', Somaliland: 'SOM', Kosovo: 'XKX' };
const merged = {};
for (const g of topo.objects.countries.geometries) {
  const iso = byNum[g.id]?.cca3 || NAME_FIX[g.properties.name] || null;
  if (iso && wanted.has(iso)) (merged[iso] ||= []).push(g);
}
const geo = {};
let verts = 0;
const missing = [];
for (const iso of wanted) {
  const gs = merged[iso];
  if (!gs) { missing.push(iso); geo[iso] = world.geo[iso]; continue; }
  const m = gs.length > 1 ? topojson.merge(topo, gs) : topojson.feature(topo, gs[0]).geometry;
  const polys = m.type === 'Polygon' ? [m.coordinates] : m.coordinates;
  const rings = [];
  for (const poly of polys) for (const ring of poly) {
    const r = [];
    for (const [x, y] of ring) {
      const p = [Math.round(x * 100) / 100, Math.round(y * 100) / 100];
      const q = r[r.length - 1];
      if (q && Math.abs(q[0] - p[0]) < 0.015 && Math.abs(q[1] - p[1]) < 0.015) continue;
      r.push(p);
    }
    if (r.length >= 4) { rings.push(r); verts += r.length; }
  }
  geo[iso] = rings.length ? rings : world.geo[iso];
}
fs.writeFileSync(path.join(root, 'src/data/geo50.json'), JSON.stringify(geo));
console.log(`geo50: ${Object.keys(geo).length} Länder, ${verts} Punkte, ` +
  `${(fs.statSync(path.join(root, 'src/data/geo50.json')).size / 1024).toFixed(0)} KB` + (missing.length ? `, aus 110m übernommen: ${missing.join(' ')}` : ''));
