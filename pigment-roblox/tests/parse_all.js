// Syntaxprüfung aller Lua-Dateien (Luau ist eine Obermenge; wir nutzen nur Lua-5.1-Syntax)
const fs = require('fs'), path = require('path');
const luaparse = require(process.env.LUAPARSE);
const root = path.join(__dirname, '..', 'src');
let bad = 0, n = 0;
(function walk(d) {
  for (const f of fs.readdirSync(d)) {
    const p = path.join(d, f);
    if (fs.statSync(p).isDirectory()) walk(p);
    else if (p.endsWith('.lua')) {
      n++;
      try { luaparse.parse(fs.readFileSync(p, 'utf8'), { luaVersion: '5.1', comments: false }); }
      catch (e) { bad++; console.log('FEHLER', path.relative(root, p), e.message); }
    }
  }
})(root);
console.log(`${n} Dateien geprüft, ${bad} mit Fehlern`);
