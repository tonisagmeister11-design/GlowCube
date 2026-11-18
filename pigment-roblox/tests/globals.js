// Findet unbekannte globale Namen (meist Tippfehler) in allen Lua-Dateien.
const fs = require('fs'), path = require('path');
const luaparse = require(process.env.LUAPARSE);
const ok = new Set(('game workspace script Instance Vector3 Vector2 CFrame Color3 UDim UDim2 Enum Random NumberSequence ' +
  'NumberSequenceKeypoint NumberRange ColorSequence ColorSequenceKeypoint TweenInfo task math string table pairs ipairs type ' +
  'tostring tonumber print warn pcall setmetatable os require select error next unpack rawget rawset getmetatable').split(' '));
const root = path.join(__dirname, '..', 'src');
let found = 0;
(function walk(d) {
  for (const f of fs.readdirSync(d)) {
    const p = path.join(d, f);
    if (fs.statSync(p).isDirectory()) { walk(p); continue; }
    if (!p.endsWith('.lua')) continue;
    const names = new Map();
    const ast = luaparse.parse(fs.readFileSync(p, 'utf8'), { luaVersion: '5.1', scope: true, locations: true });
    const skip = new Set();
    (function visit(n) {
      if (!n || typeof n !== 'object') return;
      if (Array.isArray(n)) { n.forEach(visit); return; }
      if (n.type === 'MemberExpression') skip.add(n.identifier);
      if (n.type === 'TableKeyString') skip.add(n.key);
      if (n.type === 'Identifier' && n.isLocal === false && !skip.has(n) && !ok.has(n.name)) names.set(n.name, n.loc.start.line);
      for (const k in n) if (k !== 'loc' && k !== 'range') visit(n[k]);
    })(ast);
    for (const [k, line] of names) { found++; console.log(path.relative(root, p) + ':' + line, 'unbekannt:', k); }
  }
})(root);
console.log(found ? found + ' Verdachtsfälle' : 'keine unbekannten Namen');
