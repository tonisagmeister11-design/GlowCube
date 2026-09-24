// Bettet das Kartenbild (assets/map/world.webp) als base64-Data-URI ein.
import fs from 'node:fs'; import path from 'node:path'; import { fileURLToPath } from 'node:url';
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const b64 = fs.readFileSync(path.join(root, 'assets/map/world.webp')).toString('base64');
fs.mkdirSync(path.join(root, 'src/generated'), { recursive: true });
fs.writeFileSync(path.join(root, 'src/generated/mapimg.js'),
  '// AUTOGENERIERT von tools/gen-map.mjs\nexport const MAP_IMAGE = "data:image/webp;base64,' + b64 + '";\n');
console.log('Kartenbild eingebettet:', (b64.length / 1024).toFixed(0), 'KB base64');
