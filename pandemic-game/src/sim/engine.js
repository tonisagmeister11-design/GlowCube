// Welt-Simulation. Ein Tick = ein Spieltag. Alle sichtbaren Werte (Infizierte,
// Tote, DNA, Cure, Verkehr, Events) stammen aus dieser Engine.
import { PATHOGENS } from '../data/pathogens.js';
import { TRANSMISSION, ABILITIES, SYMPTOMS } from '../data/traits.js';

const ALL_TRAITS = {};
for (const t of [...TRANSMISSION, ...ABILITIES, ...SYMPTOMS]) ALL_TRAITS[t.id] = t;

const clamp = (x, a, b) => Math.max(a, Math.min(b, x));
export const IDLE_DAYS = 180;   // Tage ohne Infizierte, bis abgeschottete Überlebende gewinnen
// cure = Forschungstempo (höher = schnelleres Heilmittel), react = wie heftig
// die Welt auf Tote/Symptome reagiert, detect = Anteil Infizierter, ab dem ein
// Land den Erreger auch ohne Symptome im Labor entdeckt.
export const DIFFICULTIES = {
  leicht: { label: 'Leicht', cure: 0.85, react: 0.75, dna: 1.2, detect: 0.03, deploy: 0.75 },
  normal: { label: 'Normal', cure: 1.45, react: 1.0, dna: 1.0, detect: 0.02, deploy: 1.0 },
  brutal: { label: 'Brutal', cure: 1.4, react: 1.35, dna: 0.85, detect: 0.008, deploy: 1.6 },
};

export class Engine {
  constructor(world, opts) {
    this.world = world;
    this.opts = opts; // {type, name, startIso, difficulty}
    this.def = PATHOGENS[opts.type];
    this.diff = DIFFICULTIES[opts.difficulty] || DIFFICULTIES.normal;
    this.day = 0;
    this.date = new Date(2026, 0, 1);
    this.dna = 8;
    this.totalDnaEarned = 8;
    this.cure = 0;            // 0..1
    this.cureActive = false;
    this.cureRate = 0;        // Forschungsfortschritt pro Tag (Anzeige)
    this.cureDone = false;    // Heilmittel fertig -> wird verteilt (Niederlage erst, wenn alle geheilt sind)
    this.deployment = 0;      // weltweiter Verteilungsgrad 0..1
    this.rewrites = 0;        // wie oft der Erreger sein Erbgut umgebaut hat (DNA-Umbau)
    this.rewriteBoost = 1;    // Forscher lernen mit jedem Umbau dazu
    this.priority = 0;        // globale Alarmstufe: 0 ruhig … ~1.6 weltweite Panik
    this._deadPrev = 0;
    this.evolved = new Set();
    this.evoOrder = [];
    this.devolveCount = 0;
    this.infectivity = this.def.startInf;
    this.severity = this.def.startSev;
    this.lethality = this.def.startLeth;
    this.visibility = 0;      // wann die Welt es merkt
    this.detected = false;
    this.detectDay = null;
    this.gameOver = null;     // {win, reason}
    this.news = [];
    this.bubbles = [];        // aufsteigende DNA-Blasen {iso,type}
    this.rng = mulberry32(hashStr(opts.name + opts.type + opts.startIso));
    this.stats = { infectedPeak: 0 };

    // Sonderzustände
    this.special = {
      controlActive: false, controlled: 0,
      zombieActive: false, zombies: 0, hordeActive: false,
      apesActive: false, apes: 0, apeSpread: false,
      vampireActive: false, vampires: 0,
      xenoActive: false, xeno: 0, xmon: 0,
    };

    const total = world.countries.reduce((a, c) => a + c.pop, 0);
    this.worldPop = total;
    this.countries = {};
    for (const c of world.countries) {
      this.countries[c.iso] = {
        ref: c, pop: c.pop, healthy: c.pop, infected: 0, dead: 0,
        controlled: 0, zombies: 0, apes: 0, vampires: 0, xeno: 0,
        detected: false, airportOpen: c.airport, portOpen: c.port, bordersOpen: true,
        cureContribution: 0, lastDnaDay: -99, closedAir: false, closedPort: false, closedBorder: false,
        measures: 0,   // Quarantäne/Lockdown 0..0.8 – bremst Ansteckung Gesunder
        vac: 0,        // Verteilung des fertigen Heilmittels im Land 0..1
        immune: 0,     // geimpfte/geheilte Gesunde (Teil von healthy), nicht ansteckbar
        collapse: 0,   // Zusammenbruch von Staat & Gesundheitssystem 0..1
        researchW: c.medical * c.wealth * (0.6 + 0.4 * Math.min(1, Math.sqrt(c.pop / 1e8))),
      };
    }
    this.list = Object.values(this.countries);
    this.researchTotal = this.list.reduce((a, st) => a + st.researchW, 0);
    // Start-Infektion
    const s = this.countries[opts.startIso];
    const seed = Math.max(1, Math.round(s.pop * 0.000003));
    s.infected = seed; s.healthy -= seed;
    this.startCountry = opts.startIso;
    this.craterIso = opts.type === 'xenolith' ? opts.startIso : null;

    // Verkehr
    this.vehicles = [];        // {kind, from, to, path, t, speed, infected}
    this.mods = {};
    this.mods_stealth = this.def.stealth || 0;
    this._instab = 0;
    this.recompute();
    this.buildRoutes();
    this.pushNews(`${this.def.name} „${opts.name}“ ist in ${s.ref.name} aufgetreten.`, 'start', opts.startIso);
  }

  // ---- Routen ----
  buildRoutes() {
    const cs = this.world.countries;
    this.airRoutes = [];
    this.airAdj = {};            // iso -> [{iso, w}]
    const airports = cs.filter((c) => c.airport);
    for (const a of airports) {
      const partners = airports
        .filter((b) => b.iso !== a.iso)
        .map((b) => ({ iso: b.iso, w: (a.hub * b.hub * (0.4 + b.travel)) / (1 + geoDist(a, b) / 2600) * (a.region === b.region ? 1.6 : 1) }))
        .sort((x, y) => y.w - x.w)
        .slice(0, 8);
      this.airAdj[a.iso] = partners;
      for (const p of partners) if (a.iso < p.iso) this.airRoutes.push([a.iso, p.iso]);
    }
    // Seewege via Hafen -> seaNode -> Graph -> seaNode -> Hafen (Dijkstra einmalig)
    this.seaRoutes = [];
    this.seaAdj = {};            // iso -> [{iso, nodes}]
    const ports = cs.filter((c) => c.port && c.portPos);
    const nodePath = this.seaGraph();
    for (const a of ports) {
      const partners = ports
        .filter((b) => b.iso !== a.iso && b.seaNode)
        .map((b) => ({ b, d: geoDist(a, b) }))
        .sort((x, y) => x.d - y.d)
        .slice(0, 5);
      this.seaAdj[a.iso] = [];
      for (const { b } of partners) {
        const np = nodePath(a.seaNode, b.seaNode);
        if (!np) continue;
        this.seaAdj[a.iso].push({ iso: b.iso, nodes: np });
        if (a.iso < b.iso) this.seaRoutes.push({ from: a.iso, to: b.iso, nodes: np });
      }
    }
  }

  // Echte internationale Übertragung pro Tick über die Routen.
  // Geschlossene Flughäfen/Häfen/Grenzen kappen die Wege fast vollständig – aber
  // nicht ganz (Schmuggel, Heimkehrer). Tiere & Insekten kennen keine Grenzen.
  spreadInternational() {
    const infM = (0.4 + this.infectivity * 0.05) * 0.5;
    const m = this.mods;
    const leakAir = 0.02 + (m.air || 0) * 0.03;
    const leakSea = 0.025 + (m.sea || 0) * 0.03;
    const leakLand = 0.04 + (m.land || 0) * 0.4;
    for (const iso in this.countries) {
      const st = this.countries[iso];
      if (st.infected < 20) continue;
      const frac = st.infected / Math.max(1, st.pop) * (1 - st.measures * 0.5);
      const travel = st.ref.travel;
      // Luft
      if (st.ref.airport && this.airAdj[iso]) {
        const p = clamp(frac * (0.6 + (m.air || 0) * 3) * travel * infM, 0, 0.95) * (st.airportOpen ? 1 : leakAir);
        for (const { iso: to, w } of this.airAdj[iso]) {
          const dst = this.countries[to];
          if (dst.healthy < 1) continue;
          if (this.rng() < p * Math.min(1, w * 1.5) * (dst.airportOpen ? 1 : leakAir)) this.tryInfect(to, 'air');
        }
      }
      // See
      if (st.ref.port && this.seaAdj[iso]) {
        const p = clamp(frac * (0.5 + (m.sea || 0) * 3) * travel * infM, 0, 0.9) * (st.portOpen ? 1 : leakSea);
        for (const { iso: to } of this.seaAdj[iso]) {
          const dst = this.countries[to];
          if (dst.healthy < 1) continue;
          if (this.rng() < p * (dst.portOpen ? 1 : leakSea)) this.tryInfect(to, 'sea');
        }
      }
      // Land
      const pl = clamp(frac * (0.35 + (m.land || 0) * 3) * infM, 0, 0.85);
      for (const nIso of st.ref.neighbors) {
        const n = this.countries[nIso];
        if (!n || n.healthy < 1) continue;
        const open = st.bordersOpen && n.bordersOpen;
        if (this.rng() < pl * (open ? 1 : leakLand)) this.tryInfect(nIso, 'land');
      }
    }
  }

  seaGraph() {
    const { nodes, edges } = this.world.sea;
    const adj = {};
    for (const k of Object.keys(nodes)) adj[k] = [];
    for (const [a, b] of edges) {
      const d = nodeDist(nodes[a], nodes[b]);
      adj[a].push([b, d]); adj[b].push([a, d]);
    }
    const cache = {};
    return (src, dst) => {
      if (src === dst) return [nodes[src]];
      const key = src + '>' + dst;
      if (cache[key]) return cache[key];
      const dist = {}; const prev = {}; const seen = {};
      for (const k in adj) dist[k] = Infinity;
      dist[src] = 0;
      const q = [[0, src]];
      while (q.length) {
        q.sort((a, b) => a[0] - b[0]);
        const [d, u] = q.shift();
        if (seen[u]) continue; seen[u] = 1;
        if (u === dst) break;
        for (const [v, w] of adj[u]) {
          if (d + w < dist[v]) { dist[v] = d + w; prev[v] = u; q.push([dist[v], v]); }
        }
      }
      if (!isFinite(dist[dst])) { cache[key] = null; return null; }
      const path = []; let u = dst;
      while (u) { path.unshift(nodes[u]); u = prev[u]; }
      cache[key] = path;
      return path;
    };
  }

  // ---- Evolution ----
  canEvolve(id) {
    const t = ALL_TRAITS[id] || this.abilityDef(id);
    if (!t || this.evolved.has(id)) return false;
    if (this.dna < t.cost) return false;
    if (t.req && !t.req.every((r) => this.evolved.has(r))) return false;
    return true;
  }

  abilityDef(id) {
    return (this.def.abilities || []).find((a) => a.id === id);
  }

  evolve(id) {
    const t = ALL_TRAITS[id] || this.abilityDef(id);
    if (!this.canEvolve(id)) return false;
    this.dna -= t.cost;
    this.evolved.add(id);
    this.evoOrder.push(id);
    if (t.cureSet) {
      this.cure = clamp(this.cure - t.cureSet, 0, 1);
      // eine neue Variante macht ein bereits verteiltes Heilmittel teilweise wirkungslos
      if (this.cureDone || this.deployment > 0) for (const st of this.list) { st.immune *= 0.6; st.vac *= 0.5; }
    }
    if (t.zombie) this.special.zombieActive = true;
    if (t.horde) this.special.hordeActive = true;
    if (t.apes) this.special.apesActive = true;
    if (t.apeSpread) this.special.apeSpread = true;
    if (t.vampire) this.special.vampireActive = true;
    if (t.xeno) this.special.xenoActive = true;
    if (t.control) { this.special.controlActive = true; this.pushNews(`Weltweite Berichte über seltsames Verhalten – „${this.opts.name}“ verändert das Bewusstsein der Infizierten.`, 'special'); }
    if (t.zombie) this.pushNews(`ENTSETZEN: Verstorbene stehen wieder auf. „${this.opts.name}“ reanimiert die Toten.`, 'special');
    if (t.vampire) this.pushNews(`Unerklärliche nächtliche Angriffe – eine neue Bedrohung erwacht.`, 'special');
    if (t.apes) this.pushNews(`Wissenschaftler melden ungewöhnlich intelligente Affenkolonien.`, 'special');
    if (t.xeno) this.pushNews(`Fremdartige Kristallformationen breiten sich aus. Der Planet verändert sich.`, 'special');
    this.recompute();
    return true;
  }

  devolve(id) {
    const t = ALL_TRAITS[id] || this.abilityDef(id);
    if (!this.evolved.has(id)) return false;
    // nichts darf von id abhängen
    for (const other of this.evolved) {
      const od = ALL_TRAITS[other] || this.abilityDef(other);
      if (od && od.req && od.req.includes(id)) return false;
    }
    if (t.control || t.zombie || t.vampire || t.apes || t.xeno) return false; // Sondermodi bleiben
    const cost = Math.max(2, Math.round(t.cost * 0.35));
    if (this.dna < cost) return false;
    this.dna -= cost;
    this.evolved.delete(id);
    this.evoOrder = this.evoOrder.filter((x) => x !== id);
    this.devolveCount++;
    this.recompute();
    return true;
  }

  recompute() {
    const d = this.def;
    let inf = d.startInf, sev = d.startSev, leth = d.startLeth;
    this.mods = {};
    this.cureReqBonus = 0;
    this.cureResist = 0;   // Heilmittelresistenz: Heilung/Impfung wirken schwächer
    this.leak = 0;         // Immunflucht: Geimpfte/Geheilte können sich wieder anstecken
    let cureMul = 1;
    this.instabilityStabilised = false;
    for (const id of this.evolved) {
      const t = ALL_TRAITS[id] || this.abilityDef(id);
      if (!t) continue;
      inf += t.inf || 0; sev += t.sev || 0; leth += t.leth || 0;
      if (t.lethMul) leth *= t.lethMul;
      if (t.cureReq) this.cureReqBonus += t.cureReq;
      if (t.cureResist) this.cureResist += t.cureResist;
      if (t.leak) this.leak += t.leak;
      if (t.cureMul) cureMul *= t.cureMul;
      if (t.mods) for (const k in t.mods) this.mods[k] = (this.mods[k] || 0) + t.mods[k];
      if (t.controlBoost) this.special.controlBoost = t.controlBoost;
      if (t.zombieBoost) this.special.zombieBoost = t.zombieBoost;
      if (t.apeBoost) this.special.apeBoost = t.apeBoost;
      if (t.apeSpread) this.special.apeSpread = true;
      if (t.zombieArmor) this.special.zombieArmor = t.zombieArmor;
      if (t.templarArmor) this.special.templarArmor = t.templarArmor;
      if (t.vampireBoost) this.special.vampireBoost = t.vampireBoost;
      if (t.xenoBoost) this.special.xenoBoost = t.xenoBoost;
      if (t.stealth) this.mods_stealth = (this.def.stealth || 0) + t.stealth;
      if (id === 'stabilise') this.instabilityStabilised = true;
    }
    this.infectivity = inf;
    this.severity = sev + (d.instability ? this._instab : 0);
    this.lethality = leth + (d.instability ? this._instab : 0);
    this.baseCureMul = cureMul * (d.cureMul || 1);
    this.cureResist = Math.min(0.8, this.cureResist);
    this.leak = Math.min(0.7, this.leak);
  }

  // ---- DNA-Blasen ----
  spawnBubble(iso, type) {
    this.bubbles.push({ iso, type, id: this.rng() });
  }

  collectBubbles() {
    const b = this.bubbles; this.bubbles = []; return b;
  }

  // ---- Tick ----
  tick() {
    if (this.gameOver) return;
    this.day++;
    this.date = new Date(this.date.getTime() + 86400000);
    const d = this.def;

    // Bio-Waffe: instabile Letalität steigt
    if (d.instability) {
      if (this.instabilityStabilised) this._instab = Math.max(0, this._instab - 0.25);
      else this._instab = Math.min(6, this._instab + 0.025);
      this.recompute();
    }
    // Virus: zufällige Mutation
    if (d.special === 'mutation' && this.totalInfected() > 1000 && this.rng() < 0.006 * d.mutationRate) {
      this.randomMutation();
    }

    const globalInf = Math.max(0.05, this.infectivity) * 0.0075;
    let anyInfected = false;
    this._bubbleScale = Math.min(1, 12 / Math.max(1, this.countriesInfected()));

    for (const iso in this.countries) {
      const st = this.countries[iso];
      const c = st.ref;
      if (st.infected > 0.5) anyInfected = true;
      const alive = st.healthy + st.infected;
      if (alive < 1) {   // weniger als ein Mensch übrig → Land ist ausgestorben
        if (alive > 0) { st.dead += alive; st.healthy = 0; st.infected = 0; }
        continue;
      }

      // interne Ausbreitung (logistisch); Quarantäne schützt noch Gesunde
      const S = this._susceptible(st);
      if (st.infected >= 0.5 && S > 0) {
        const envM = this.envMultiplier(c);
        const densM = 0.6 + Math.min(1.4, c.density / 200) + c.urban * 0.5;
        const frac = st.infected / alive;
        let rate = globalInf * envM * densM * (1 - frac) * (1 - st.measures * 0.75);
        let newInf = st.infected * rate;
        // Land überrannt: die letzten Überlebenden finden keinen Schutz mehr
        if (S < st.pop * 0.01 && st.infected + st.dead > S * 20) {
          newInf = Math.max(newInf, S * (S < 50 ? 1 : 0.25));
        }
        newInf = Math.min(newInf, S);
        if (newInf < 1 && this.rng() < st.infected * rate) newInf = Math.min(1, S);
        this._infectHealthy(st, newInf, S);
      }
      if (st.healthy < 0.5) { st.infected += st.healthy > 0 && st.infected > 0 ? st.healthy : 0; st.healthy = 0; }

      // Tod / Genesung. Zusammengebrochene Länder können niemanden mehr behandeln.
      if (st.infected > 0) {
        // Kontrollierte (Neurax) lassen sich nicht behandeln
        const ctrlShare = st.controlled > 0 ? Math.min(1, st.controlled / Math.max(1, st.infected)) : 0;
        const treat = this.detected ? c.medical * (1 - (this.mods.rich || 0) * 0.7) * (1 - this.cure * 0.3) * (1 - st.collapse) * (1 - ctrlShare * 0.8) : 0;
        let lethRate = clamp(this.lethality * 0.0006 * (1 - treat * 0.6) * (1 + st.collapse * 2), 0, 0.3);
        // völlig zusammengebrochenes Land ohne Gesunde: keine Pflege, kein Essen,
        // kein Wasser – die letzten Infizierten sterben schnell
        if (lethRate > 0 && st.collapse > 0.9 && st.healthy < 1) lethRate = Math.max(lethRate, 0.4);
        let deaths = st.infected * lethRate;
        // letzte Überlebende in einem ausgebluteten Land
        if (lethRate > 0 && st.healthy < 1 && st.infected < 400) deaths = Math.max(deaths, st.infected * 0.35, 1);
        deaths = Math.min(deaths, st.infected);
        st.infected -= deaths; st.dead += deaths;
        if (st.infected < 0.5 && (lethRate > 0 || st.healthy >= 1)) { st.dead += st.infected; st.infected = 0; }
        if (!this.firstDeathIso && st.dead >= 1) this.firstDeathIso = iso;
        // Behandlung, sobald die Forschung fortgeschritten ist
        if (this.cure > 0.15 && !this.cureDone) {
          const heal = st.infected * this.cure * (0.02 * c.medical + this.cure * 0.03) * (1 - st.collapse);
          st.infected -= heal; st.healthy += heal;
        }
      }
      // Fertiges Heilmittel wird verteilt: heilt Infizierte und impft Gesunde.
      // Reiche Länder mit guter Versorgung zuerst, zerfallene Staaten gar nicht.
      if (this.cureDone) {
        const res = this.cureResist;
        // auch zerfallene Länder erhalten etwas über internationale Hilfslieferungen
        const dep = this.diff.deploy || 1;
        const dv = (0.01 + 0.045 * c.medical) * (0.5 + 0.5 * c.wealth) * (1 - 0.7 * st.collapse) * (1 - res * 0.5) * dep;
        st.vac = Math.min(1, st.vac + dv);
        if (st.vac > 0) {
          const heal = st.infected * st.vac * 0.3 * (1 - res) * Math.min(1.5, dep);
          st.infected -= heal; st.healthy += heal; st.immune += heal;
          const free = Math.max(0, st.healthy - st.immune);
          st.immune += free * st.vac * 0.08 * (1 - res * 0.7);
        }
      }
      if (st.immune > st.healthy) st.immune = st.healthy;
      // gute Gesundheitssysteme halten länger durch
      // verlorene Bevölkerung: Tote, aber auch Zombies und Kristallwesen – ein von
      // Untoten überranntes Land hat keinen funktionierenden Staat mehr
      const lost = st.dead + (st.zombies || 0) + (st.xmon || 0);
      st.collapse = clamp((lost / Math.max(1, st.pop) - (0.25 + 0.3 * c.medical)) / 0.35, 0, 1);

      // Entdeckung: sichtbare Symptome – oder Labor-Überwachung bei großer Verbreitung
      if (!st.detected && st.infected > Math.max(80, alive * 0.0006)) {
        const labFrac = this.diff.detect / (0.5 + c.medical);
        if (d.instantDetect || this.visibility > 0.12 + (this.mods_stealth || 0) || st.infected > alive * labFrac) {
          st.detected = true;
        }
      }

      // DNA-Blase gelegentlich in infizierten Ländern (seltener, je mehr Länder)
      if (st.infected > 100 && this.day - st.lastDnaDay > 6 && this.rng() < 0.18 * this._bubbleScale) {
        st.lastDnaDay = this.day;
        this.spawnBubble(iso, 'infect');
      }
    }

    this.spreadInternational();
    this.updateSpecial();
    this.updateVisibility();
    this.updateVehicles();
    this.updatePriority();
    this.updateMeasures();
    this.updateCure();
    this.worldReactions();
    this.updateDna();
    this._milestones();
    this.stats.infectedPeak = Math.max(this.stats.infectedPeak, this.totalInfected());
    this.checkEnd();
  }

  // Ansteckbare Gesunde: Geimpfte/Geheilte nur bei Immunflucht (teilweise)
  _susceptible(st) {
    return Math.max(0, st.healthy - st.immune * (1 - this.leak));
  }

  // n Gesunde anstecken (anteilig auch Geimpfte, wenn Immunflucht aktiv ist)
  _infectHealthy(st, n, S = this._susceptible(st)) {
    if (n <= 0 || S <= 0) return;
    n = Math.min(n, S);
    const fromImm = n * (st.immune * this.leak) / S;
    st.immune = Math.max(0, st.immune - fromImm);
    st.healthy -= n; st.infected += n;
  }

  envMultiplier(c) {
    const m = this.mods;
    let x = 1;
    if (c.climate === 'kalt') x *= 0.55 + (m.cold || 0) + (this.def.coldBonus || 0);
    if (c.climate === 'heiß') x *= 0.8 + (m.hot || 0);
    if (c.climate === 'tropisch') x *= 0.9 + (m.hot || 0) * 0.5 + (m.humid || 0);
    if (c.climate === 'arid') x *= 0.6 + (m.arid || 0);
    if (c.climate === 'gemäßigt') x *= 1.0;
    x *= 1 + (c.wealth > 0.6 ? -(0.25 - (m.rich || 0) * 0.8) : (m.poor || 0));
    x *= 1 + (c.urban - 0.5) * (0.4 + (m.urban || 0));
    x *= 1 + (m.humid || 0) * (c.hum - 0.5);
    return clamp(x, 0.05, 4);
  }

  // ---- Verkehr ----
  updateVehicles() {
    for (const v of this.vehicles) v.t += v.speed;
    this.vehicles = this.vehicles.filter((v) => v.t < 1);
    // Fahrzeuge visualisieren die Übertragung (spreadInternational rechnet sie);
    // ein infiziertes Fahrzeug erreicht ein Land = sichtbarer Ausbreitungsweg.
    const maxV = 16;
    let guard = 0;
    while (this.vehicles.length < maxV && guard++ < 6) {
      if (this.rng() < 0.6) this.spawnAir(); else this.spawnSea();
      if (this.rng() > 0.4) break;  // meist nur 1 neues Fahrzeug pro Tag
    }
  }

  spawnAir() {
    if (!this.airRoutes.length) return;
    const r = this.airRoutes[(this.rng() * this.airRoutes.length) | 0];
    const [aIso, bIso] = this.rng() < 0.5 ? r : [r[1], r[0]];
    const a = this.countries[aIso], b = this.countries[bIso];
    if (!a.airportOpen || !b.airportOpen) return;
    if (!a.ref.airport || !b.ref.airport) return;
    const infected = a.infected / Math.max(1, a.pop) > 0.001 && this.rng() < this.travelInfectChance(a, 'air');
    this.vehicles.push({
      kind: 'air', from: aIso, to: bIso, t: 0,
      speed: (0.02 + 0.01 * this.rng()) * (this.opts._speed || 1),
      infected, ax: a.ref.lon, ay: a.ref.lat, bx: b.ref.lon, by: b.ref.lat, arc: true,
    });
  }

  spawnSea() {
    if (!this.seaRoutes.length) return;
    const rt = this.seaRoutes[(this.rng() * this.seaRoutes.length) | 0];
    const fwd = this.rng() < 0.5;
    const aIso = fwd ? rt.from : rt.to, bIso = fwd ? rt.to : rt.from;
    const a = this.countries[aIso], b = this.countries[bIso];
    if (!a.portOpen || !b.portOpen) return;
    const infected = a.infected > 0 && this.rng() < this.travelInfectChance(a, 'sea');
    const pts = [a.ref.portPos, ...(fwd ? rt.nodes : [...rt.nodes].reverse()), b.ref.portPos];
    // Schiffe fahren nur auf Wasser: Route gegen die Wasser-Maske prüfen.
    if (this.isWaterFn && !this._pathIsWater(pts)) return;
    this.vehicles.push({
      kind: 'sea', from: aIso, to: bIso, t: 0,
      speed: (0.006 + 0.004 * this.rng()) * (this.opts._speed || 1),
      infected, poly: pts,
    });
  }

  _pathIsWater(pts) {
    // entlang aller Segmente Zwischenpunkte prüfen; einzelne Ausreißer erlaubt
    let bad = 0, total = 0;
    for (let i = 1; i < pts.length; i++) {
      const [x0, y0] = pts[i - 1], [x1, y1] = pts[i];
      const steps = Math.max(2, Math.ceil(Math.hypot(x1 - x0, y1 - y0) / 4));
      for (let s = 0; s <= steps; s++) {
        const f = s / steps;
        total++;
        if (!this.isWaterFn(x0 + (x1 - x0) * f, y0 + (y1 - y0) * f)) bad++;
      }
    }
    return bad / Math.max(1, total) < 0.12;
  }

  travelInfectChance(a, kind) {
    const frac = a.infected / Math.max(1, a.pop);
    const base = frac * (0.8 + this.infectivity * 0.05);
    const m = kind === 'air' ? (this.mods.air || 0) : (this.mods.sea || 0);
    return clamp(base * (0.5 + m * 3) * a.ref.travel, 0, 0.9);
  }

  tryInfect(iso, kind) {
    const st = this.countries[iso];
    if (!st || this._susceptible(st) < 1) return;
    if (kind !== 'spore' && st.measures > 0 && this.rng() < st.measures * 0.6) return;   // Einreisekontrollen
    if (st.infected < 1) {
      const seed = Math.max(1, Math.round(st.pop * 0.000004));
      this._infectHealthy(st, seed);
      // Bonus fürs Erreichen eines neuen Landes – groß am Anfang, später klein
      const nC = this.countriesInfected();
      if (nC <= 25 || this.rng() < 0.3) this.spawnBubble(iso, 'country');
      const bonus = nC <= 12 ? 3 : 1;
      this.dna += bonus; this.totalDnaEarned += bonus;
      this._spreadNews = (this._spreadNews || 0) + 1;
      if (this._spreadNews <= 45) this.pushNews(`${st.ref.name} meldet die ersten Fälle von „${this.opts.name}“.`, 'spread', iso);
    } else {
      this._infectHealthy(st, st.infected * 0.02 + 5);
    }
  }

  // ---- Sichtbarkeit / Entdeckung ----
  updateVisibility() {
    const inf = this.totalInfected();
    const frac = inf / this.worldPop;
    let vis = this.severity * 0.02 + frac * 3;
    vis -= (this.mods_stealth || 0);
    this.visibility = clamp(vis, 0, 3);
    if (!this.detected) {
      const anyDetected = this.list.some((s) => s.detected);
      if (anyDetected || this.def.instantDetect) {
        this.detected = true; this.detectDay = this.day; this.cureActive = true;
        this.pushNews(`Die WHO bestätigt einen neuen Erreger: „${this.opts.name}“. Die Heilmittelforschung beginnt.`, 'detect');
      }
    }
  }

  // ---- Globale Alarmstufe ----
  // Steigt mit sichtbaren Symptomen, Verbreitung und vor allem mit Toten – ein
  // plötzliches Massensterben löst weltweite Panik aus. Sinkt kaum wieder.
  updatePriority() {
    const pop = this.worldPop;
    const dead = this.totalDead();
    const deadToday = Math.max(0, dead - this._deadPrev);
    this._deadPrev = dead;
    if (!this.detected) return;
    const infFrac = this.totalInfected() / pop;
    let target = 0.1 + Math.min(0.5, this.severity * 0.008) + infFrac * 0.6 +
      Math.min(0.6, (dead / pop) * 4) + Math.min(0.8, (deadToday / pop) * 300);
    target = Math.min(1.6, target) * this.diff.react;
    const k = target > this.priority ? 0.06 : 0.004;
    this.priority += (target - this.priority) * k;
    const P = (this._pms ||= new Set());
    const say = (key, cond, text) => { if (cond && !P.has(key)) { P.add(key); this.pushNews(text, 'react'); } };
    say('p05', this.priority >= 0.5, `Die WHO ruft wegen „${this.opts.name}" den globalen Gesundheitsnotstand aus. Forschungsgelder werden massiv aufgestockt.`);
    say('p10', this.priority >= 1.0, `Weltweite Panik: Regierungen stellen sämtliche Ressourcen für ein Heilmittel bereit.`);
  }

  // ---- Quarantäne & Lockdown je Land ----
  updateMeasures() {
    let announced = this._measureNews || 0;
    for (const st of this.list) {
      const c = st.ref;
      const active = st.detected || this.priority > 0.8;
      const target = active && st.collapse < 0.9
        ? clamp(this.priority * (0.25 + 0.5 * c.medical + 0.15 * c.wealth), 0, 0.8) * (1 - st.collapse) : 0;
      st.measures += (target - st.measures) * 0.05;
      if (st.measures > 0.45 && !st._lockNews && st.healthy > st.pop * 0.05) {
        st._lockNews = true;
        if (announced++ < 20) this.pushNews(`${c.name} verhängt strenge Quarantäne und Ausgangssperren.`, 'react', c.iso);
      }
      if (st.collapse > 0.8 && !st._collapseNews && st.researchW > 0.12) {
        st._collapseNews = true;
        this.pushNews(`Staatszerfall in ${c.name}: Regierung und Labore haben die Arbeit eingestellt.`, 'lose', c.iso);
      }
    }
    this._measureNews = announced;
  }

  // ---- Cure ----
  // Forschung = funktionierende Laborkapazität × Finanzierung (Alarmstufe).
  // Kollabierte Länder forschen nicht mehr; Länder ohne Fälle nur eingeschränkt.
  updateCure() {
    if (!this.cureActive) { this.cureRate = 0; return; }
    let cap = 0;
    for (const st of this.list) {
      const involved = st.infected > 0 || st.dead > 0;
      // forschen kann nur, wer noch lebt: unter 30 % Überlebenden sinkt die Kapazität
      const aliveF = (st.healthy + st.infected) / Math.max(1, st.pop);
      cap += st.researchW * (1 - st.collapse * 0.85) * (involved ? 1 : 0.6) * clamp(aliveF / 0.3, 0, 1);
    }
    const functional = cap / Math.max(1e-9, this.researchTotal);
    const funding = 0.3 + this.priority * 1.6;
    let rate = 0.009 * functional * funding * this.baseCureMul * this.diff.cure * this.rewriteBoost / (1 + this.cureReqBonus);
    // Gab es schon einmal ein fertiges Heilmittel, muss es nach einem Rückschlag
    // nur angepasst werden – das geht auch mit wenigen verbliebenen Laboren
    if (this.cureEverDone && !this.cureDone) rate = Math.max(rate, 0.011 * this.diff.cure * this.rewriteBoost / (1 + this.cureReqBonus));
    this.cureRate = this.cureDone ? 0 : rate;
    if (!this.cureDone) this.cure = clamp(this.cure + rate, 0, 1);
    if (this.cure >= 1 && !this.cureDone) {
      this.cureDone = true; this.cureDoneDay = this.day; this.cureEverDone = true;
      this.pushNews(`DURCHBRUCH: Das Heilmittel gegen „${this.opts.name}" ist fertig! Die weltweite Verteilung beginnt.`, 'react');
    } else if (this.cure < 1 && this.cureDone) {
      this.cureDone = false;
      this.pushNews(`Rückschlag: Das Heilmittel wirkt nicht mehr gegen „${this.opts.name}" – die Forschung muss nachbessern.`, 'special');
    }
    let vp = 0, tp = 0;
    for (const st of this.list) { vp += st.vac * st.pop; tp += st.pop; }
    this.deployment = vp / Math.max(1, tp);
  }

  // ---- Gegenwehr: DNA-Umbau (beliebig oft, wird jedes Mal teurer) ----
  // Das Erbgut wird schlagartig umgebaut: Das Heilmittel wirkt nicht mehr, die
  // Forscher müssen die Hälfte neu entwickeln und der Impfschutz ist weg.
  // Dafür lernen sie jedes Mal dazu (Forschung danach etwas schneller).
  rewriteCost() { return 30 + 15 * this.rewrites; }

  dnaRewrite() {
    const cost = this.rewriteCost();
    if (this.dna < cost || this.gameOver) return false;
    this.dna -= cost;
    this.rewrites++;
    this.rewriteBoost = 1 + 0.2 * this.rewrites;
    this.cure = Math.min(this.cure, 1) * 0.5;
    for (const st of this.list) { st.immune = 0; st.vac = 0; }
    this.deployment = 0;
    if (this.cureDone) this.cureDone = false;
    this.pushNews(`„${this.opts.name}" hat sein Erbgut umgebaut! Das Heilmittel ist wirkungslos – die Forschung fällt auf ${(this.cure * 100).toFixed(0)} % zurück.`, 'special');
    return true;
  }

  // ---- DNA ----
  // Früh einsetzender, stetiger Zuwachs: hängt von der Zahl betroffener Länder
  // und der Größenordnung der Infizierten ab (nicht vom winzigen Anteil an der
  // Weltbevölkerung). So bekommt man schon mit wenigen tausend Infizierten in
  // vernünftigem Abstand Punkte, ohne dass es später ausufert.
  updateDna() {
    const inf = this.totalInfected() + this.totalDead();
    if (inf < 1) return;
    const rate = (this.def.dnaRate != null ? this.def.dnaRate : 1) * (this.def.dnaSymptomMul || 1);
    const n = this.countriesInfected();
    const perDay = (0.04 * Math.min(n, 15) + 0.006 * Math.max(0, n - 15) + 0.2 * Math.min(6, Math.log10(inf + 10))) * this.diff.dna * rate;
    this._dnaAccum = (this._dnaAccum || 0) + perDay;
    while (this._dnaAccum >= 1) { this._dnaAccum -= 1; this.dna += 1; this.totalDnaEarned += 1; }
  }

  clickBubble(b) {
    let gain = b.type === 'country' ? 2 : b.type === 'special' ? 3 : 1;
    const rate = (this.def.dnaRate != null ? this.def.dnaRate : 1) * (this.def.dnaSymptomMul || 1);
    gain = Math.round(gain * this.diff.dna * rate + 0.4);
    this.dna += gain; this.totalDnaEarned += gain;
    return gain;
  }

  // ---- Meilenstein-Nachrichten (kontextbezogen, kein Spam) ----
  _milestones() {
    const M = (this._ms ||= new Set());
    const mile = (k, cond, text, kind, iso) => { if (cond && !M.has(k)) { M.add(k); this.pushNews(text, kind || 'milestone', iso); } };
    const name = this.opts.name;
    const inf = this.totalInfected() + this.totalDead();
    const dead = this.totalDead();
    mile('firstdeath', this.firstDeathIso, `Erster Todesfall durch „${name}" in ${this.firstDeathIso ? this.countries[this.firstDeathIso].ref.name : ''}.`, 'lose', this.firstDeathIso);
    mile('inf1m', inf >= 1e6, `„${name}" hat über 1 Million Menschen infiziert.`);
    mile('inf100m', inf >= 1e8, `Über 100 Millionen Infektionen weltweit mit „${name}".`);
    mile('inf1b', inf >= 1e9, `„${name}" überschreitet 1 Milliarde Infizierte.`);
    mile('dead1m', dead >= 1e6, `„${name}" hat bereits über 1 Million Todesopfer gefordert.`, 'lose');
    mile('dead100m', dead >= 1e8, `Katastrophe: Über 100 Millionen Tote durch „${name}".`, 'lose');
    mile('c20', this.cure >= 0.2, `Forschungsteams melden erste Fortschritte beim Heilmittel (20 %).`, 'detect');
    mile('c50', this.cure >= 0.5, `Das Heilmittel gegen „${name}" ist zur Hälfte fertig (50 %).`, 'react');
    mile('c75', this.cure >= 0.75, `Warnung: Die Heilmittelforschung erreicht 75 %.`, 'react');
    mile('c90', this.cure >= 0.9, `Das Heilmittel steht kurz vor der Fertigstellung (90 %)!`, 'react');
    mile('spread50', this.countriesInfected() >= 50, `„${name}" hat sich auf über 50 Länder ausgebreitet.`);
    mile('spreadall', this.countriesInfected() >= this.list.length - 3, `Fast jedes Land der Erde ist von „${name}" betroffen.`);
  }

  // ---- Weltreaktionen ----
  worldReactions() {
    if (!this.detected) return;
    if (this.day % 2 !== 0) return;
    const react = this.diff.react;
    // Bei hoher Alarmstufe schotten sich noch kaum betroffene Länder vorsorglich ab
    if (this.priority > 0.6) {
      const pClose = (this.priority - 0.6) * 0.06 * react;
      for (const st of this.list) {
        if (st.infected / Math.max(1, st.pop) > 0.02 || st.dead > st.pop * 0.01) continue;
        const c = st.ref, guard = pClose * (0.5 + c.medical);
        if (st.airportOpen && c.airport && this.rng() < guard) { st.airportOpen = false; st.closedAir = true; this._newsClose(`${c.name} stoppt vorsorglich alle Flüge.`, c.iso); }
        if (st.portOpen && c.port && this.rng() < guard) { st.portOpen = false; st.closedPort = true; this._newsClose(`${c.name} schließt vorsorglich seine Häfen.`, c.iso); }
        if (st.bordersOpen && this.rng() < guard * 0.8) { st.bordersOpen = false; st.closedBorder = true; this._newsClose(`${c.name} riegelt seine Grenzen ab.`, c.iso); }
      }
    }
    const severe = this.list
      .filter((s) => s.infected / Math.max(1, s.pop) > 0.02 || s.dead / Math.max(1, s.pop) > 0.005);
    for (const st of severe) {
      const c = st.ref;
      const sev = st.infected / st.pop + st.dead / st.pop * 2;
      if (st.airportOpen && c.airport && sev > 0.04 && this.rng() < 0.12 * react) {
        st.airportOpen = false; st.closedAir = true;
        this.pushNews(`${c.name} schließt seine Flughäfen, um die Ausbreitung zu bremsen.`, 'react', c.iso);
      }
      if (st.portOpen && c.port && sev > 0.05 && this.rng() < 0.1 * react) {
        st.portOpen = false; st.closedPort = true;
        this.pushNews(`${c.name} schließt seine Häfen.`, 'react', c.iso);
      }
      if (st.bordersOpen && sev > 0.07 && this.rng() < 0.09 * react) {
        st.bordersOpen = false; st.closedBorder = true;
        this.pushNews(`${c.name} schließt seine Landesgrenzen.`, 'react', c.iso);
      }
    }
  }

  _newsClose(text, iso) {
    this._closeNews = (this._closeNews || 0) + 1;
    if (this._closeNews <= 25) this.pushNews(text, 'react', iso);
  }

  // ---- Sondermechaniken ----
  updateSpecial() {
    const sp = this.special;
    if (sp.controlActive) {
      let ctrl = 0;
      for (const iso in this.countries) {
        const st = this.countries[iso];
        const conv = st.infected * (0.02 + (sp.controlBoost || 0) * 0.03);
        const c2 = Math.min(st.infected, conv);
        st.controlled += c2; ctrl += st.controlled;
      }
      sp.controlled = ctrl;
    }
    if (sp.zombieActive) {
      let z = 0;
      for (const iso in this.countries) {
        const st = this.countries[iso];
        // Tote stehen auf – nicht mehr, wo das Heilmittel schon verteilt ist
        if (st.dead > 0 && st.vac < 0.5) {
          const rise = Math.min(st.dead, st.dead * (0.01 + (sp.zombieBoost || 0) * 0.02) * (1 - st.vac));
          st.dead -= rise; st.zombies += rise;
        }
        if (st.zombies > 0 && st.healthy > 0) {
          // Geimpfte/Geheilte werden durch Bisse nicht angesteckt (außer bei Immunflucht)
          this._infectHealthy(st, st.zombies * (0.035 + (sp.zombieBoost || 0) * 0.04));
        }
        // Mit dem verteilten Heilmittel (Gegenmittel) räumt das Militär die Horden ab
        if (st.zombies > 0 && st.vac > 0) {
          st.zombies = Math.max(0, st.zombies - st.zombies * (0.02 + 0.06 * st.vac) * (1 - (sp.zombieArmor || 0) * 0.5));
          if (st.zombies < 50 * st.vac) st.zombies = 0;   // letzte versprengte Untote
        }
        // Militär baut Festungen in betroffenen, wohlhabenden Ländern
        if (this.detected && (st.zombies > st.pop * 0.004 || st.dead > st.pop * 0.01) && st.healthy > st.pop * 0.02) {
          st.fortress = Math.min(1, (st.fortress || 0) + 0.006 * (0.4 + st.ref.wealth));
        }
        // Festung bekämpft Zombies; Zombies greifen die Festung an
        if (st.fortress > 0 && st.zombies > 0) {
          const def = st.zombies * (0.02 + st.fortress * 0.05) * (1 - (sp.zombieArmor || 0));
          st.zombies = Math.max(0, st.zombies - def);
          st.fortress = Math.max(0, st.fortress - (st.zombies / Math.max(1, st.pop)) * 0.04);
        }
        z += st.zombies;
      }
      sp.zombies = z;
      if (sp.hordeActive && this.day % 3 === 0) {
        for (const iso in this.countries) {
          const st = this.countries[iso];
          if (st.zombies > st.pop * 0.01) {
            for (const nIso of st.ref.neighbors) {
              const n = this.countries[nIso];
              if (n && n.zombies < 1 && this.rng() < 0.3) { n.zombies += 10; this.spawnBubble(nIso, 'special'); }
            }
          }
        }
      }
    }
    if (sp.apesActive) {
      let a = 0;
      for (const iso in this.countries) {
        const st = this.countries[iso];
        if (st.infected > st.pop * 0.003 || st.apes > 0) {
          if (st.apes < 1) st.apes = Math.max(50, st.pop * 0.0008);
          st.apes += st.apes * (0.05 + (sp.apeBoost || 0) * 0.05);
          st.apes = Math.min(st.apes, st.pop);
          // intelligente Affen verdrängen Menschen
          const push = Math.min(st.healthy, st.apes * 0.015);
          st.healthy -= push; st.dead += push;   // Menschen sterben im Kampf gegen die Affen
        }
        a += st.apes;
      }
      sp.apes = a;
      if (sp.apeSpread && this.day % 4 === 0) {
        for (const iso in this.countries) {
          const st = this.countries[iso];
          if (st.apes > st.pop * 0.02) for (const nIso of st.ref.neighbors) {
            const n = this.countries[nIso];
            if (n && n.apes < 1 && this.rng() < 0.25) n.apes = 100;
          }
        }
      }
    }
    if (sp.vampireActive) {
      let v = 0;
      for (const iso in this.countries) {
        const st = this.countries[iso];
        if (st.infected > 0 && st.vampires < 1) st.vampires = 1;
        if (st.vampires > 0 && st.healthy > 0) {
          const feed = Math.min(this._susceptible(st), st.vampires * (0.12 + (sp.vampireBoost || 0) * 0.08));
          this._infectHealthy(st, feed);
          st.vampires += feed * 0.05 + 1;
        }
        if (st.vampires > 0 && this.detected && this.cure > 0.2) {
          const templar = st.vampires * st.ref.wealth * 0.02 * (1 - (sp.templarArmor || 0));
          st.vampires = Math.max(0, st.vampires - templar);
        }
        v += st.vampires;
      }
      sp.vampires = v;
    }
    if (sp.xenoActive) {
      let x = 0, xm = 0;
      for (const iso in this.countries) {
        const st = this.countries[iso];
        // Kristalle füllen ein Land langsam, wo Infektion vorhanden ist
        if (st.infected > st.pop * 0.008 || st.xeno > 0) {
          st.xeno = Math.min(1, st.xeno + 0.0035 * (1 + (sp.xenoBoost || 0)));
        }
        // Wo viele Kristalle sind, verwandeln sich Menschen in Kristallwesen
        if (st.xeno > 0.15 && st.healthy > 0) {
          const conv = Math.min(st.healthy, st.pop * st.xeno * 0.004 * (1 + (sp.xenoBoost || 0)));
          st.healthy -= conv; st.xmon = (st.xmon || 0) + conv;
        }
        x += st.xeno * st.pop; xm += st.xmon || 0;
      }
      sp.xeno = x / this.worldPop; sp.xmon = xm;
    }
  }

  randomMutation() {
    const candidates = SYMPTOMS.filter((s) => !this.evolved.has(s.id) && (!s.req || s.req.every((r) => this.evolved.has(r))));
    if (!candidates.length) return;
    const s = candidates[(this.rng() * candidates.length) | 0];
    this.evolved.add(s.id); this.evoOrder.push(s.id);
    this.recompute();
    this.pushNews(`„${this.opts.name}“ ist mutiert und zeigt ein neues Symptom: ${s.name}.`, 'mutation');
  }

  // Gerichtete Sonderaktion: eine wählbare Anzahl Träger (Wirte/Zombies/Vampire/
  // Kristallwesen) in ein Land schicken. amount = Anzahl "Reisende".
  directSeed(iso, mode, amount = 100) {
    const st = this.countries[iso]; if (!st) return false;
    const seed = Math.max(amount, 20);
    this._infectHealthy(st, seed);
    if (mode === 'control') st.controlled += seed * 0.7;
    if (mode === 'zombie') { st.zombies += seed * 1.2; st.fortress = Math.max(0, (st.fortress || 0) - 0.15); }
    if (mode === 'vampire') st.vampires += Math.max(8, seed * 0.05);
    if (mode === 'crystal') { st.xeno = Math.min(1, (st.xeno || 0) + 0.08); st.xmon = (st.xmon || 0) + seed * 0.5; }
    this.spawnBubble(iso, 'special');
    const names = {
      control: `${fmtShort(seed)} kontrollierte Reisende fliegen nach`, zombie: 'Eine Zombie-Horde marschiert nach',
      vampire: 'Ein Vampir zieht nach', crystal: 'Kristallwesen kriechen nach',
    };
    this.pushNews(`${names[mode] || 'Träger reisen nach'} ${st.ref.name} und verbreiten „${this.opts.name}".`, 'special', iso);
    return true;
  }

  // Neurax: Menschen bekommen den Drang, sich freiwillig anzustecken.
  willToInfect() {
    let total = 0;
    for (const iso in this.countries) {
      const st = this.countries[iso];
      if (st.infected > st.pop * 0.005 && st.healthy > 0) {
        const S = this._susceptible(st);
        const conv = Math.min(S, S * 0.12 + st.infected * 0.05);
        this._infectHealthy(st, conv, S); total += conv;
      }
    }
    if (total > 0) this.pushNews(`Massenbekehrung: Millionen wollen sich plötzlich freiwillig mit „${this.opts.name}" anstecken.`, 'special');
    return total;
  }

  // ---- Aktionen aus Sondermechaniken (Buttons) ----
  triggerAbility(id) {
    if (id === 'spore_burst' || id === 'spore_eruption') {
      const uninfected = this.world.countries.filter((c) => this.countries[c.iso].infected < 1);
      const n = id === 'spore_eruption' ? 3 : 1;
      for (let i = 0; i < n && uninfected.length; i++) {
        const c = uninfected.splice((this.rng() * uninfected.length) | 0, 1)[0];
        this.tryInfect(c.iso, 'spore');
        this.pushNews(`Sporenausbruch: „${this.opts.name}“ erreicht ${c.name}.`, 'special', c.iso);
      }
      return true;
    }
    return false;
  }

  // ---- Sieg / Niederlage ----
  // Sieg gibt es für JEDEN Erregertyp und auf jeder Stufe nur, wenn kein Mensch
  // mehr lebt. Kontrolle, Zombies, Affen, Vampire und Kristalle sind Werkzeuge
  // auf dem Weg dorthin – nur infizieren oder kontrollieren reicht nicht.
  checkEnd() {
    if (this.gameOver) return;
    const healthy = this.totalHealthy();
    const infected = this.totalInfected();
    // Sieg zuerst: sterben die letzten Menschen und Infizierten am selben Tag, hat
    // die Krankheit gewonnen
    if (healthy + infected < 1 && this.day > 10) { this.endGame(true, this.winReason()); return; }
    // Niederlage: keine Infizierten und keine Sonderwesen mehr – entweder vom
    // verteilten Heilmittel geheilt oder von selbst ausgestorben.
    // (Ein fertiges Heilmittel allein beendet das Spiel nicht: man kann sich wehren.)
    const specialAlive = this.special.zombies + this.special.apes + this.special.vampires + (this.special.xmon || 0);
    if (infected < 1 && specialAlive < 1 && this.day > 20) { this.endGame(false, this.cureDone ? 'cure' : 'ausgestorben'); return; }
    // Sicherheitsnetz gegen endlose Pattsituationen: ist das Heilmittel seit 500
    // Tagen ununterbrochen weltweit im Einsatz, hat die Menschheit gewonnen
    if (this.cureDone) { this._cureDays = (this._cureDays || 0) + 1; if (this._cureDays > 500) { this.endGame(false, 'held'); return; } }
    else this._cureDays = 0;
    // Abgeschottete Überlebende: keine Infizierten mehr, nur noch Sonderwesen, die
    // die letzten Menschen nicht erreichen (z.B. Zombies vor versiegelten Inseln).
    // Nach einer Warnung hat man IDLE_DAYS Tage Zeit, Träger hinzuschicken.
    this.idleDays = infected < 1 && this.day > 20 ? (this.idleDays || 0) + 1 : 0;
    if (this.idleDays === 30) this.pushNews('Keine neuen Infektionen mehr – die letzten Menschen haben sich abgeschottet. Schick Träger zu ihnen, sonst überlebt die Menschheit!', 'cure');
    if (this.idleDays > IDLE_DAYS) { this.endGame(false, 'isolated'); return; }
    // Nach dem ersten fertigen Heilmittel: sinkt die Zahl der Menschen 300 Tage
    // lang nicht um mindestens 5 %, hat die Menschheit das Patt für sich
    // entschieden (sonst könnte man den Umbau endlos wiederholen)
    if (this.cureEverDone) {
      const alive = healthy + infected;
      if (!(alive > (this._bestAlive || Infinity) * 0.95)) { this._bestAlive = alive; this._stallDays = 0; }
      else if (++this._stallDays > 300) { this.endGame(false, 'held'); return; }
    }
  }

  hasSpecialActive() {
    return this.special.controlActive || this.special.zombieActive || this.special.apesActive ||
      this.special.vampireActive || this.special.xenoActive;
  }

  // Endtext passend zum Erreger (Sondertypen mit aktivierter Fähigkeit)
  winReason() {
    const m = this.def.winMode;
    return m && this.hasSpecialActive() ? m : 'extinction';
  }

  endGame(win, reason) {
    if (this.gameOver) return;
    this.gameOver = { win, reason, day: this.day };
    const reasons = {
      cure: 'Das Heilmittel wurde weltweit verteilt und hat den letzten Infizierten geheilt.',
      ausgestorben: 'Der Erreger ist ausgestorben, bevor er die Menschheit besiegen konnte.',
      isolated: 'Die letzten Menschen haben sich abgeschottet – die Seuche hat sie nie erreicht.',
      held: 'Die Menschheit hat standgehalten – das Heilmittel wird überall verteilt und die Seuche kommt nicht mehr voran.',
      extinction: 'Die gesamte Menschheit wurde infiziert und ausgelöscht.',
      control: 'Gesteuert vom Neurax-Wurm hat sich die Menschheit bis zum letzten Menschen selbst vernichtet.',
      zombie: 'Kein Mensch hat überlebt – die Zombie-Horden beherrschen die Erde.',
      apes: 'Die Menschheit ist ausgestorben – intelligente Affen übernehmen den Planeten.',
      vampire: 'Die letzten Menschen sind gefallen – die Nacht gehört für immer den Vampiren.',
      xeno: 'Kein Mensch lebt mehr – die Erde gehört nun dem Xenolith und seinen Kristallen.',
    };
    this.pushNews(win ? `SIEG: „${this.opts.name}“ hat gewonnen! ${reasons[reason]}` : `NIEDERLAGE: ${reasons[reason]}`, win ? 'win' : 'lose');
    this.gameOver.text = reasons[reason];
  }

  // ---- Aggregate ----
  totalInfected() { let s = 0; for (const k in this.countries) s += this.countries[k].infected; return s; }
  totalDead() { let s = 0; for (const k in this.countries) s += this.countries[k].dead; return s; }
  totalHealthy() { let s = 0; for (const k in this.countries) s += this.countries[k].healthy; return s; }
  countriesInfected() { let s = 0; for (const k in this.countries) if (this.countries[k].infected > 0 || this.countries[k].dead > 0) s++; return s; }

  pushNews(text, kind, iso) {
    this.news.push({ day: this.day, date: new Date(this.date), text, kind, iso });
    if (this.news.length > 200) this.news.shift();
  }
}

function mulberry32(a) {
  return function () {
    a |= 0; a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}
function hashStr(s) { let h = 2166136261; for (let i = 0; i < s.length; i++) { h ^= s.charCodeAt(i); h = Math.imul(h, 16777619); } return h >>> 0; }
function geoDist(a, b) {
  const R = 6371, dLat = (b.lat - a.lat) * Math.PI / 180, dLon = (b.lon - a.lon) * Math.PI / 180;
  const la1 = a.lat * Math.PI / 180, la2 = b.lat * Math.PI / 180;
  const x = Math.sin(dLat / 2) ** 2 + Math.cos(la1) * Math.cos(la2) * Math.sin(dLon / 2) ** 2;
  return 2 * R * Math.asin(Math.min(1, Math.sqrt(x)));
}
function nodeDist(a, b) {
  let dx = Math.abs(a[0] - b[0]); if (dx > 180) dx = 360 - dx;
  return Math.hypot(dx, a[1] - b[1]);
}

function fmtShort(n) {
  n = Math.round(n);
  if (n >= 1e9) return (n / 1e9).toFixed(1) + ' Mrd.';
  if (n >= 1e6) return (n / 1e6).toFixed(1) + ' Mio.';
  if (n >= 1e3) return (n / 1e3).toFixed(0) + ' Tsd.';
  return String(n);
}
