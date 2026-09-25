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
import { dumpPlay } from './exploit-test.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const world = JSON.parse(fs.readFileSync(path.join(root, 'src/data/world.json'), 'utf8'));

const SPREAD = ['air1', 'water1', 'contact1', 'air2', 'water2', 'contact2', 'env1', 'cold1', 'heat1', 'arid1', 'env2', 'drug1', 'gene1', 'cold2', 'heat2', 'arid2', 'gene2', 'drug2', 'food1', 'animal1', 'food2', 'insect1', 'animal2', 'insect2', 'xzoo', 'xair'];
const LETHAL = ['organ', 'hemorrhage', 'shock', 'necrosis', 'coma', 'insanity', 'dysentery', 'pneumonia', 'edema', 'fever', 'tumours', 'seizures', 'paralysis'];

// Gute Strategie wie ein erfahrener Spieler: erst überall verbreiten, Rückschläge
// für die Forschung aufsparen, bis das Heilmittel läuft, tödlich erst, wenn
// (fast) jedes Land infiziert ist – oder das Heilmittel bedrohlich wird.
function smartPlay(type, iso, { passive = false, maxDays = 3500, difficulty = 'normal' } = {}) {
  const eng = new Engine(world, { type, name: 'Test', startIso: iso, difficulty });
  eng.opts._speed = 1;
  let si = 0, li = 0, lethalOn = false;
  const log = { maxCountries: 0, detectedDay: null, peakInf: 0 };
  for (let day = 0; day < maxDays && !eng.gameOver; day++) {
    for (const b of eng.collectBubbles()) eng.clickBubble(b);
    if (!passive) {
      const n = eng.countriesInfected(), frac = (eng.totalInfected() + eng.totalDead()) / eng.worldPop;
      if (eng.cure > 0.25) for (const id of ['shuffle1', 'shuffle2', 'shuffle3']) if (eng.canEvolve(id)) { eng.evolve(id); break; }
      if (!lethalOn && ((n >= eng.list.length - 1 && frac > 0.85) || eng.cure > 0.5)) lethalOn = true;
      if (lethalOn) while (li < LETHAL.length) { const id = LETHAL[li]; if (eng.evolved.has(id)) { li++; continue; } if (eng.canEvolve(id)) { eng.evolve(id); li++; } else break; }
      while (si < SPREAD.length) { const id = SPREAD[si]; if (eng.evolved.has(id)) { si++; continue; } if (eng.canEvolve(id)) { eng.evolve(id); si++; } else break; }
      for (const a of eng.def.abilities || []) {
        if ((a.leth && !lethalOn) || (a.cureSet && eng.cure < 0.25)) continue;
        if (!eng.evolved.has(a.id) && eng.canEvolve(a.id)) { eng.evolve(a.id); eng.triggerAbility(a.id); }
      }
      if (type === 'fungus' && eng.evolved.has('spore_burst') && day % 20 === 0) eng.triggerAbility('spore_burst');
    }
    eng.tick();
    if (eng.detected && log.detectedDay == null) log.detectedDay = day;
    log.maxCountries = Math.max(log.maxCountries, eng.countriesInfected());
    if (log.spreadDay == null && eng.countriesInfected() >= 160) log.spreadDay = day;
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
  else if (eng.totalHealthy() + eng.totalInfected() >= 1) problems.push('Sieg, obwohl noch Menschen leben');
  if (log.maxCountries < 150) problems.push('zu wenig Ausbreitung (' + log.maxCountries + ')');
  if (log.detectedDay == null) problems.push('nie entdeckt');
  console.log(`${type.padEnd(10)} ${g ? (g.win ? 'SIEG ' : 'NIED.') : '??? '} Tag ${String(g?.day).padStart(4)} ${(g?.reason || '-').padEnd(11)} ` +
    `Länder=${String(log.maxCountries).padStart(3)} cure=${(eng.cure * 100 | 0)}%` + (problems.length ? '  ⚠ ' + problems.join(', ') : '  ✓'));
  if (problems.length) fail++;
}

console.log('\n=== Passives Spiel muss am Heilmittel scheitern ===');
for (const type of ['bacteria', 'nanovirus']) {
  const { eng } = smartPlay(type, 'USA', { passive: true, maxDays: 5200 });
  const g = eng.gameOver;
  const ok = g && !g.win;  // Niederlage durch Heilmittel ODER Aussterben
  console.log(`${type.padEnd(10)} ${g ? (g.win ? 'SIEG' : 'NIED.') : 'offen'} (${g?.reason}) cure=${(eng.cure * 100 | 0)}%` + (ok ? '  ✓' : '  ⚠ sollte verlieren (Heilmittel/Aussterben)'));
  if (!ok) fail++;
}

console.log('\n=== Startland beeinflusst die Ausbreitung (Virus, strategisch) ===');
const days = {};
for (const iso of ['COD', 'IND', 'USA', 'ISL', 'GRL', 'AUT']) {
  const { eng, log } = smartPlay('virus', iso);
  days[iso] = log.spreadDay;
  console.log(`${iso}: 160 Länder an Tag ${log.spreadDay}, ${eng.gameOver?.win ? 'Sieg' : 'Niederlage'} Tag ${eng.gameOver?.day}, max Länder ${log.maxCountries}`);
}
const dv = Object.values(days); if (!(Math.max(...dv) > Math.min(...dv) * 1.55)) { console.log('⚠ Startland wirkt zu schwach'); fail++; }

console.log('\n=== Brutal: gute Strategie bleibt gewinnbar ===');
{
  let w = 0; const lost = [];
  for (const type of PATHOGEN_ORDER) { const { eng } = smartPlay(type, 'SAU', { difficulty: 'brutal' }); if (eng.gameOver?.win) w++; else lost.push(type); }
  console.log(`Siege ${w}/12` + (lost.length ? ` (knapp verloren: ${lost.join(', ')})` : '') + (w >= 8 ? '  ✓' : '  ⚠ Brutal zu schwer'));
  if (w < 8) fail++;
}

console.log('\n=== "Warten, dann alles kaufen" darf nicht sicher gewinnen ===');
for (const [diff, maxWins] of [['normal', 9], ['brutal', 2]]) {
  let w = 0, t = 0, maxDna = 0;
  for (const type of ['virus', 'bacteria', 'bioweapon', 'parasite']) for (const iso of ['CHN', 'USA', 'BRA']) {
    const { eng, dumped } = dumpPlay(type, iso, diff, 4e9, 2000); t++;
    if (eng.gameOver?.win) w++;
    if (dumped) maxDna = Math.max(maxDna, dumped.dna);
  }
  const ok = w <= maxWins && maxDna < 250;
  console.log(`${diff.padEnd(6)} Siege ${w}/${t}, max. DNA beim Umschalten auf tödlich: ${maxDna | 0}` + (ok ? '  ✓' : '  ⚠ zu leicht'));
  if (!ok) fail++;
}

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
