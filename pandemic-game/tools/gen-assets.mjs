// Erzeugt src/generated/models.js (GLB als base64) und manifest.js.
// So sind die Blender-Assets ohne separate Dateien in die Single-HTML einbettbar.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const modelsDir = path.join(root, 'assets', 'models');
const genDir = path.join(root, 'src', 'generated');
fs.mkdirSync(genDir, { recursive: true });

const manifest = JSON.parse(fs.readFileSync(path.join(modelsDir, 'manifest.json'), 'utf8'));

let out = '// AUTOGENERIERT von tools/gen-assets.mjs – nicht bearbeiten.\n// Blender-GLB-Modelle als base64 (Meshopt-komprimiert).\nexport const MODEL_DATA = {\n';
let total = 0;
for (const [key, entry] of Object.entries(manifest)) {
  const file = path.join(root, entry.file);
  if (!fs.existsSync(file)) { console.warn('fehlt:', entry.file); continue; }
  const b64 = fs.readFileSync(file).toString('base64');
  total += b64.length;
  out += `  ${JSON.stringify(key)}: ${JSON.stringify(b64)},\n`;
}
out += '};\n';
fs.writeFileSync(path.join(genDir, 'models.js'), out);

// Manifest ohne rawBytes-Rauschen
const clean = {};
for (const [k, v] of Object.entries(manifest)) {
  clean[k] = { file: v.file, animations: v.animations, idle: v.idle, morphTargets: v.morphTargets, evolutionNodes: v.evolutionNodes, triangles: v.triangles, bones: v.bones };
}
fs.writeFileSync(path.join(genDir, 'manifest.js'), '// AUTOGENERIERT von tools/gen-assets.mjs\nexport const MANIFEST = ' + JSON.stringify(clean, null, 2) + ';\n');

console.log(`Modelle eingebettet: ${Object.keys(manifest).length}, base64 gesamt ${(total / 1024 / 1024).toFixed(2)} MB`);
