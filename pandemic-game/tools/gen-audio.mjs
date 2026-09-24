// Bettet die Musik (assets/audio/*.mp3) als base64-Data-URIs in
// src/generated/audio.js ein, damit alles in der Single-HTML enthalten ist.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const dir = path.join(root, 'assets', 'audio');
const genDir = path.join(root, 'src', 'generated');
fs.mkdirSync(genDir, { recursive: true });

const tracks = { menu: 'menu.mp3', game1: 'game1.mp3', game2: 'game2.mp3' };
let out = '// AUTOGENERIERT von tools/gen-audio.mjs – nicht bearbeiten.\nexport const AUDIO = {\n';
let total = 0;
for (const [key, file] of Object.entries(tracks)) {
  const p = path.join(dir, file);
  if (!fs.existsSync(p)) { console.warn('fehlt:', file); continue; }
  const b64 = fs.readFileSync(p).toString('base64');
  total += b64.length;
  out += `  ${key}: "data:audio/mpeg;base64,${b64}",\n`;
}
out += '};\n';
fs.writeFileSync(path.join(genDir, 'audio.js'), out);
console.log(`Audio eingebettet: ${Object.keys(tracks).length} Tracks, base64 gesamt ${(total / 1024 / 1024).toFixed(2)} MB`);
