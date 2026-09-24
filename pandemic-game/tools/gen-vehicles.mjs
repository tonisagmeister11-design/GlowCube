// Bettet die in Blender gerenderten Fahrzeug-Sprites als base64 ein.
import fs from 'node:fs'; import path from 'node:path'; import { fileURLToPath } from 'node:url';
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const g = (f) => 'data:image/png;base64,' + fs.readFileSync(path.join(root, 'blender/vehicles', f)).toString('base64');
fs.writeFileSync(path.join(root, 'src/generated/vehicles.js'),
  '// AUTOGENERIERT von tools/gen-vehicles.mjs\nexport const VEHICLES = ' + JSON.stringify({ plane: g('plane.png'), ship: g('ship.png') }) + ';\n');
console.log('Fahrzeug-Sprites eingebettet');
