// Welt-Simulation. Ein Tick = ein Spieltag. Alle sichtbaren Werte (Infizierte,
// Tote, DNA, Cure, Verkehr, Events) stammen aus dieser Engine.
import { PATHOGENS } from '../data/pathogens.js';
import { TRANSMISSION, ABILITIES, SYMPTOMS } from '../data/traits.js';

const ALL_TRAITS = {};
for (const t of [...TRANSMISSION, ...ABILITIES, ...SYMPTOMS]) ALL_TRAITS[t.id] = t;

const clamp = (x, a, b) => Math.max(a, Math.min(b, x));
export const DIFFICULTIES = {
  leicht: { label: 'Leicht', cureReq: 0.85, react: 0.75, dna: 1.2 },
  normal: { label: 'Normal', cureReq: 1.0, react: 1.0, dna: 1.0 },
  brutal: { label: 'Brutal', cureReq: 1.3, react: 1.35, dna: 0.85 },
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
    this.cureReqMul = this.diff.cureReq;
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
      };
    }
    this.list = Object.values(this.countries);
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
  // Flughafen-/Hafen-/Grenzschließungen kappen die jeweiligen Wege direkt.
  spreadInternational() {
    const infM = (0.4 + this.infectivity * 0.05) * 0.5;
    for (const iso in this.countries) {
      const st = this.countries[iso];
      if (st.infected < 20) continue;
      const frac = st.infected / Math.max(1, st.pop);
      const travel = st.ref.travel;
      // Luft
      if (st.airportOpen && this.airAdj[iso]) {
        const p = clamp(frac * (0.6 + (this.mods.air || 0) * 3) * travel * infM, 0, 0.95);
        for (const { iso: to, w } of this.airAdj[iso]) {
          const dst = this.countries[to];
          if (!dst.airportOpen || dst.healthy < 1) continue;
          if (this.rng() < p * Math.min(1, w * 1.5)) this.tryInfect(to, 'air');
        }
      }
      // See
      if (st.portOpen && this.seaAdj[iso]) {
        const p = clamp(frac * (0.5 + (this.mods.sea || 0) * 3) * travel * infM, 0, 0.9);
        for (const { iso: to } of this.seaAdj[iso]) {
          const dst = this.countries[to];
          if (!dst.portOpen || dst.healthy < 1) continue;
          if (this.rng() < p) this.tryInfect(to, 'sea');
        }
      }
      // Land
      if (st.bordersOpen) {
        const p = clamp(frac * (0.35 + (this.mods.land || 0) * 3) * infM, 0, 0.85);
        for (const nIso of st.ref.neighbors) {
          const n = this.countries[nIso];
          if (!n || !n.bordersOpen || n.healthy < 1) continue;
          if (this.rng() < p) this.tryInfect(nIso, 'land');
        }
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
    if (t.cureSet) this.cure = clamp(this.cure - t.cureSet, 0, 1);
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
    let cureMul = 1;
    this.instabilityStabilised = false;
    for (const id of this.evolved) {
      const t = ALL_TRAITS[id] || this.abilityDef(id);
      if (!t) continue;
      inf += t.inf || 0; sev += t.sev || 0; leth += t.leth || 0;
      if (t.lethMul) leth *= t.lethMul;
      if (t.cureReq) this.cureReqBonus += t.cureReq;
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

    for (const iso in this.countries) {
      const st = this.countries[iso];
      const c = st.ref;
      if (st.infected > 0.5) anyInfected = true;
      const alive = st.healthy + st.infected;
      if (alive < 1) continue;

      // interne Ausbreitung (logistisch)
      if (st.infected >= 0.5 && st.healthy > 0) {
        const envM = this.envMultiplier(c);
        const densM = 0.6 + Math.min(1.4, c.density / 200) + c.urban * 0.5;
        const frac = st.infected / alive;
        let rate = globalInf * envM * densM * (1 - frac);
        let newInf = st.infected * rate;
        newInf = Math.min(newInf, st.healthy);
        if (st.healthy > 0 && newInf < 1 && this.rng() < st.infected * rate) newInf = 1;
        st.infected += newInf; st.healthy -= newInf;
      }

      // Tod / Genesung
      if (st.infected > 0) {
        const treat = this.detected ? c.medical * (1 - (this.mods.rich || 0) * 0.7) * (1 - this.cure * 0.3) : 0;
        const lethRate = clamp(this.lethality * 0.00042 * (1 - treat * 0.6), 0, 0.22);
        let deaths = st.infected * lethRate;
        deaths = Math.min(deaths, st.infected);
        st.infected -= deaths; st.dead += deaths;
        if (!this.firstDeathIso && st.dead >= 1) this.firstDeathIso = iso;
        // Heilung, sobald Cure fortgeschritten
        if (this.cure > 0.15) {
          const heal = st.infected * this.cure * (0.02 * c.medical + this.cure * 0.03);
          st.infected -= heal; st.healthy += heal;
        }
      }

      // Entdeckung
      if (!st.detected && st.infected > Math.max(80, alive * 0.0006)) {
        if (d.instantDetect || this.visibility > 0.12 + (this.mods_stealth || 0) || st.infected > alive * 0.02) {
          st.detected = true;
        }
      }

      // DNA-Blase gelegentlich in infizierten Ländern
      if (st.infected > 100 && this.day - st.lastDnaDay > 6 && this.rng() < 0.18) {
        st.lastDnaDay = this.day;
        this.spawnBubble(iso, 'infect');
      }

      // Cure-Beitrag
      if (this.detected) {
        st.cureContribution = c.medical * c.wealth * (st.infected > 0 || st.dead > 0 ? 1 : 0.15);
      }
    }

    this.spreadInternational();
    this.updateSpecial();
    this.updateVisibility();
    this.updateVehicles();
    this.updateCure();
    this.worldReactions();
    this.updateDna();
    this._milestones();
    this.stats.infectedPeak = Math.max(this.stats.infectedPeak, this.totalInfected());
    this.checkEnd();
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
    this.vehicles.push({
      kind: 'sea', from: aIso, to: bIso, t: 0,
      speed: (0.006 + 0.004 * this.rng()) * (this.opts._speed || 1),
      infected, poly: pts,
    });
  }

  travelInfectChance(a, kind) {
    const frac = a.infected / Math.max(1, a.pop);
    const base = frac * (0.8 + this.infectivity * 0.05);
    const m = kind === 'air' ? (this.mods.air || 0) : (this.mods.sea || 0);
    return clamp(base * (0.5 + m * 3) * a.ref.travel, 0, 0.9);
  }

  tryInfect(iso, kind) {
    const st = this.countries[iso];
    if (!st || st.healthy < 1) return;
    if (kind === 'air' && !st.airportOpen) return;
    if (kind === 'sea' && !st.portOpen) return;
    if (kind === 'land' && !st.bordersOpen) return;
    if (st.infected < 1) {
      const seed = Math.max(1, Math.round(st.pop * 0.000004));
      st.infected += seed; st.healthy -= seed;
      this.spawnBubble(iso, 'country');
      this.dna += 1; this.totalDnaEarned += 1;
      this._spreadNews = (this._spreadNews || 0) + 1;
      if (this._spreadNews <= 45) this.pushNews(`${st.ref.name} meldet die ersten Fälle von „${this.opts.name}“.`, 'spread', iso);
    } else {
      const add = Math.min(st.healthy, st.infected * 0.02 + 5);
      st.infected += add; st.healthy -= add;
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

  // ---- Cure ----
  updateCure() {
    if (!this.cureActive) return;
    let contrib = 0, weight = 0;
    for (const iso in this.countries) {
      const st = this.countries[iso];
      contrib += st.cureContribution;
      weight += st.ref.wealth;
    }
    const globalKnowledge = clamp(this.totalInfected() / this.worldPop + this.totalDead() / this.worldPop * 1.5, 0, 1);
    const req = 100 * this.cureReqMul * (1 + this.cureReqBonus);
    // Sobald entdeckt, forschen die Labore stetig; Ausbreitung/Tote beschleunigen.
    const research = (contrib / Math.max(1, weight)) * (0.045 + globalKnowledge * 0.05) * this.baseCureMul;
    const floor = 0.0003 * this.baseCureMul; // Grundfortschritt: passive Erreger werden geheilt
    this.cure = clamp(this.cure + Math.max(floor, (research / req) * this.severityUrgency()), 0, 1);
  }

  severityUrgency() {
    return 1 + clamp(this.severity * 0.02 + this.totalDead() / this.worldPop * 2, 0, 1.5);
  }

  // ---- DNA ----
  updateDna() {
    const inf = this.totalInfected() + this.totalDead();
    const rate = (this.def.dnaRate != null ? this.def.dnaRate : 1) * (this.def.dnaSymptomMul || 1);
    this._dnaAccum = (this._dnaAccum || 0) + inf / this.worldPop * 0.16 * this.diff.dna * rate;
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
        if (st.dead > 0) {
          const rise = Math.min(st.dead, st.dead * (0.01 + (sp.zombieBoost || 0) * 0.02));
          st.dead -= rise; st.zombies += rise;
        }
        if (st.zombies > 0 && st.healthy > 0) {
          const bite = Math.min(st.healthy, st.zombies * (0.035 + (sp.zombieBoost || 0) * 0.04));
          st.healthy -= bite; st.infected += bite;
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
          st.healthy -= push;
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
          const feed = Math.min(st.healthy, st.vampires * (0.12 + (sp.vampireBoost || 0) * 0.08));
          st.healthy -= feed; st.infected += feed;
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
    if (st.healthy > 0) { const s = Math.min(st.healthy, seed); st.healthy -= s; st.infected += s; }
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
        const conv = Math.min(st.healthy, st.healthy * 0.12 + st.infected * 0.05);
        st.healthy -= conv; st.infected += conv; total += conv;
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
        this.tryInfect(c.iso, 'air');
        this.pushNews(`Sporenausbruch: „${this.opts.name}“ erreicht ${c.name}.`, 'special', c.iso);
      }
      return true;
    }
    return false;
  }

  // ---- Sieg / Niederlage ----
  checkEnd() {
    if (this.gameOver) return;
    const healthy = this.totalHealthy();
    const infected = this.totalInfected();

    // Niederlage: ausgestorben
    const specialAlive = this.special.zombies + this.special.apes + this.special.vampires + this.special.controlled + (this.special.xmon || 0);
    if (infected < 1 && specialAlive < 1 && this.day > 20) {
      this.endGame(false, 'ausgestorben');
      return;
    }
    // Niederlage: Heilmittel fertig (nur wenn kein Sondersieg schon erreicht)
    if (this.cure >= 1) {
      if (!this.checkSpecialWin(true)) this.endGame(false, 'cure');
      return;
    }
    // Sieg: Sondermodi
    if (this.checkSpecialWin(false)) return;
    // Standard-Sieg: gesamte Menschheit besiegt (gilt auch bei Sonderklassen)
    if (healthy < 1 && this.day > 10) { this.endGame(true, 'extinction'); return; }
  }

  hasSpecialActive() {
    return this.special.controlActive || this.special.zombieActive || this.special.apesActive ||
      this.special.vampireActive || this.special.xenoActive;
  }

  checkSpecialWin(force) {
    const healthy = this.totalHealthy();
    const d = this.def;
    if (d.winMode === 'control' && this.special.controlActive) {
      if (this.special.controlled >= (healthy + this.special.controlled) * 0.95 && this.special.controlled > this.worldPop * 0.4) {
        this.endGame(true, 'control'); return true;
      }
    }
    if (d.winMode === 'zombie' && this.special.zombieActive) {
      if (healthy < this.worldPop * 0.02 && this.special.zombies > this.worldPop * 0.03) { this.endGame(true, 'zombie'); return true; }
    }
    if (d.winMode === 'apes' && this.special.apesActive) {
      if (this.special.apes >= this.worldPop * 0.4 && healthy < this.worldPop * 0.1) { this.endGame(true, 'apes'); return true; }
    }
    if (d.winMode === 'vampire' && this.special.vampireActive) {
      if (healthy < this.worldPop * 0.02 && this.special.vampires > 1000) { this.endGame(true, 'vampire'); return true; }
    }
    if (d.winMode === 'xeno' && this.special.xenoActive) {
      if (this.special.xeno >= 0.98) { this.endGame(true, 'xeno'); return true; }
    }
    if (force && healthy < 1) { this.endGame(true, 'extinction'); return true; }
    return false;
  }

  endGame(win, reason) {
    if (this.gameOver) return;
    this.gameOver = { win, reason, day: this.day };
    const reasons = {
      cure: 'Die Menschheit hat rechtzeitig ein Heilmittel entwickelt.',
      ausgestorben: 'Der Erreger ist ausgestorben, bevor er die Menschheit besiegen konnte.',
      extinction: 'Die gesamte Menschheit wurde infiziert und ausgelöscht.',
      control: 'Die Menschheit steht vollständig unter der Kontrolle des Neurax-Wurms.',
      zombie: 'Die Zombie-Apokalypse hat die Menschheit ausgelöscht.',
      apes: 'Die intelligente Affenpopulation hat die Menschheit verdrängt.',
      vampire: 'Die Vampire haben die Menschheit ausgelöscht.',
      xeno: 'Der Planet wurde vollständig in Kristall verwandelt.',
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
