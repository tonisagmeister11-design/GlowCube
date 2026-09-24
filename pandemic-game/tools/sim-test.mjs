// Headless-Regressionstest der Simulation. Prüft:
//  - realistische Ausbreitung über Land/Luft/See,
//  - jeder Krankheitstyp kann mit vernünftiger Strategie gewinnen,
//  - passives Spiel verliert am Heilmittel (Niederlage-Bedingung),
//  - Startland beeinflusst den Verlauf,
//  - die Welt reagiert (Flughäfen/Grenzen schließen).
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { Engine } from '../src/sim/engine.js';
import { PATHOGEN_ORDER } from '../src/data/pathogens.js';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const world = JSON.parse(fs.readFileSync(path.join(root, 'src/data/world.json'), 'utf8'));

const SPREAD = ['air1', 'water1', 'contact1', 'air2', 'water2', 'contact2', 'env1', 'cold1', 'heat1', 'arid1', 'env2', 'drug1', 'gene1', 'cold2', 'heat2', 'arid2', 'gene2', 'food1', 'animal1', 'food2'];
const LETHAL = ['organ', 'hemorrhage', 'shock', 'necrosis', 'coma', 'insanity', 'dysentery', 'pneumonia', 'edema', 'fever', 'tumours', 'seizures', 'paralysis'];

function smartPlay(type, iso, { passive = false, maxDays = 3500 } = {}) {
  const eng = new Engine(world, { type, name: 'Test', startIso: iso, difficulty: 'normal' });
  eng.opts._speed = 1;
  let si = 0, li = 0;
  const log = { maxCountries: 0, detectedDay: null, peakInf: 0 };
  for (let day = 0; day < maxDays && !eng.gameOver; day++) {
    for (const b of eng.collectBubbles()) eng.clickBubble(b);
    if (!passive) {
      const frac = (eng.totalInfected() + eng.totalDead()) / eng.worldPop;
      while (si < SPREAD.length) { const id = SPREAD[si]; if (eng.evolved.has(id)) { si++; continue; } if (eng.canEvolve(id)) { eng.evolve(id); si++; } else break; }
      if (frac > 0.75 || eng.cure > 0.3) {
        while (li < LETHAL.length) { const id = LETHAL[li]; if (eng.evolved.has(id)) { li++; continue; } if (eng.canEvolve(id)) { eng.evolve(id); li++; } else break; }
      }
      for (const a of eng.def.abilities || []) if (!eng.evolved.has(a.id) && eng.canEvolve(a.id)) { eng.evolve(a.id); eng.triggerAbility(a.id); }
      if (type === 'fungus' && eng.evolved.has('spore_burst') && day % 20 === 0) eng.triggerAbility('spore_burst');
    }
    eng.tick();
    if (eng.detected && log.detectedDay == null) log.detectedDay = day;
    log.maxCountries = Math.max(log.maxCountries, eng.countriesInfected());
    log.peakInf = Math.max(log.peakInf, eng.totalInfected());
  }
  return { eng, log };
}

let fail = 0;
console.log('=== Strategisches Spiel: jeder Typ muss gewinnen können ===');
for (const type of PATHOGEN_ORDER) {
  const { eng, log } = smartPlay(type, 'SAU');
  const g = eng.gameOver;
  const problems = [];
  if (!g) problems.push('kein Ende');
  else if (!g.win) problems.push('verloren trotz guter Strategie: ' + g.reason);
  if (log.maxCountries < 150) problems.push('zu wenig Ausbreitung (' + log.maxCountries + ')');
  if (log.detectedDay == null) problems.push('nie entdeckt');
  console.log(`${type.padEnd(10)} ${g ? (g.win ? 'SIEG ' : 'NIED.') : '??? '} Tag ${String(g?.day).padStart(4)} ${(g?.reason || '-').padEnd(11)} ` +
    `Länder=${String(log.maxCountries).padStart(3)} cure=${(eng.cure * 100 | 0)}%` + (problems.length ? '  ⚠ ' + problems.join(', ') : '  ✓'));
  if (problems.length) fail++;
}

console.log('\n=== Passives Spiel muss am Heilmittel scheitern ===');
for (const type of ['bacteria', 'nanovirus']) {
  const { eng } = smartPlay(type, 'USA', { passive: true });
  const g = eng.gameOver;
  const ok = g && !g.win && g.reason === 'cure';
  console.log(`${type.padEnd(10)} ${g ? (g.win ? 'SIEG' : 'NIED.') : 'offen'} (${g?.reason}) cure=${(eng.cure * 100 | 0)}%` + (ok ? '  ✓' : '  ⚠ sollte am Heilmittel verlieren'));
  if (!ok) fail++;
}

console.log('\n=== Startland beeinflusst den Verlauf (Virus, strategisch) ===');
const days = {};
for (const iso of ['COD', 'IND', 'USA', 'ISL', 'GRL', 'AUT']) {
  const { eng, log } = smartPlay('virus', iso);
  days[iso] = eng.gameOver?.day;
  console.log(`${iso}: Sieg Tag ${eng.gameOver?.day}, max Länder ${log.maxCountries}`);
}
if (!(days.GRL > days.COD * 1.3)) { console.log('⚠ Startland wirkt zu schwach'); fail++; }

console.log('\n=== Weltreaktionen ===');
{
  const { eng } = smartPlay('bioweapon', 'CHN');
  const air = eng.list.filter((s) => s.closedAir).length;
  const bord = eng.list.filter((s) => s.closedBorder).length;
  console.log(`Flughäfen geschlossen: ${air}, Grenzen geschlossen: ${bord}, Nachrichten erzeugt: ${eng.news.length}`);
  if (air < 5 && bord < 5) { console.log('⚠ keine Reaktionen'); fail++; }
}

console.log(fail ? `\n${fail} Problem(e) ✗` : '\nAlle Tests bestanden ✓');
process.exit(fail ? 1 : 0);
