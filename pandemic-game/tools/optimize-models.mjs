// Optimiert die Blender-Exporte (blender/export/*.glb) fuer den Browser:
// gltfpack mit Meshopt-Kompression + Quantisierung, Namen/Materialien/Extras bleiben
// erhalten. Danach wird geprueft, dass Animationen, Morph-Targets und
// Evolutions-Nodes identisch zum Rohexport sind.
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const rawDir = path.join(root, 'blender', 'export');
const outDir = path.join(root, 'assets', 'models');
const manifestPath = path.join(outDir, 'manifest.json');

function readGlbJson(file) {
  const buf = fs.readFileSync(file);
  if (buf.readUInt32LE(0) !== 0x46546c67) throw new Error(`${file}: keine GLB-Datei`);
  const len = buf.readUInt32LE(12);
  return { json: JSON.parse(buf.subarray(20, 20 + len).toString('utf8')), bytes: buf.length };
}

function summary(j) {
  return {
    anims: (j.animations || []).map((a) => a.name).sort(),
    morphs: [...new Set((j.meshes || []).flatMap((m) => m.extras?.targetNames || []))].sort(),
    evo: (j.nodes || []).map((n) => n.name || '').filter((n) => n.startsWith('EVO')).sort(),
  };
}

const manifest = fs.existsSync(manifestPath) ? JSON.parse(fs.readFileSync(manifestPath, 'utf8')) : {};
const byFile = Object.fromEntries(Object.entries(manifest).map(([k, v]) => [path.basename(v.file), k]));
let failed = 0;
let total = 0;
for (const f of fs.readdirSync(rawDir).filter((x) => x.endsWith('.glb')).sort()) {
  const src = path.join(rawDir, f);
  const dst = path.join(outDir, f);
  execFileSync(process.execPath, [path.join(root, 'node_modules', 'gltfpack', 'cli.js'),
    '-i', src, '-o', dst, '-cc', '-kn', '-km', '-ke', '-vpf'], { stdio: 'pipe' });
  const a = summary(readGlbJson(src).json);
  const packed = readGlbJson(dst);
  const b = summary(packed.json);
  const errs = [];
  for (const k of ['anims', 'morphs', 'evo']) {
    if (JSON.stringify(a[k]) !== JSON.stringify(b[k])) errs.push(`${k} verändert: ${a[k]} -> ${b[k]}`);
  }
  total += packed.bytes;
  const key = byFile[f];
  if (key) manifest[key].bytes = packed.bytes;
  const raw = fs.statSync(src).size;
  console.log(`${errs.length ? 'ERR' : 'OK '} ${f.padEnd(20)} ${(raw / 1024).toFixed(0).padStart(5)} KB -> ${(packed.bytes / 1024).toFixed(0).padStart(5)} KB ${errs.join('; ')}`);
  if (errs.length) failed++;
}
fs.writeFileSync(manifestPath, JSON.stringify(manifest, null, 2));
console.log(`Gesamt: ${(total / 1024 / 1024).toFixed(2)} MB`);
process.exit(failed ? 1 : 0);
