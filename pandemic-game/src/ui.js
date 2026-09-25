// Gesamte Benutzeroberfläche: Menü, Erregerauswahl (Speed Run), Benennung,
// Startlandwahl, Spiel-HUD, Evolutionsbildschirm (Übertragung/Symptome-Hexraster/
// Fähigkeiten), Länderinfo, Nachrichten, Statistiken und Endscreen.
import { PATHOGENS, PATHOGEN_ORDER } from './data/pathogens.js';
import { TRANSMISSION, ABILITIES, SYMPTOMS, HEX_NEIGHBORS } from './data/traits.js';
import { SPEEDS } from './game.js';
import { HologramViewer } from './three/hologram.js';

const h = (tag, props = {}, ...kids) => {
  const e = document.createElement(tag);
  for (const k in props) {
    const v = props[k];
    if (v == null || v === false) continue;              // undefined/null/false: Attribut weglassen
    if (k === 'class') e.className = v;
    else if (k === 'html') e.innerHTML = v;
    else if (k.startsWith('on')) e.addEventListener(k.slice(2), v);
    else if (k === 'style') e.style.cssText = v;
    else if (k === 'disabled') e.disabled = true;         // echtes Property, nicht String
    else e.setAttribute(k, v === true ? '' : v);
  }
  for (const c of kids.flat()) if (c != null) e.append(c.nodeType ? c : document.createTextNode(c));
  return e;
};
const fmt = (n) => {
  n = Math.round(n);
  if (n >= 1e9) return (n / 1e9).toFixed(2) + ' Mrd.';
  if (n >= 1e6) return (n / 1e6).toFixed(2) + ' Mio.';
  if (n >= 1e3) return (n / 1e3).toFixed(1) + ' Tsd.';
  return String(n);
};
const fmtFull = (n) => Math.round(n).toLocaleString('de-DE');
const LS = 'pandemic_speedrun';

export class UI {
  constructor(root, game, viewer) {
    this.root = root;
    this.game = game;
    this.viewer = viewer;
    this.sel = { type: 'virus', name: '', startIso: null, difficulty: 'normal' };
    this.records = JSON.parse(localStorage.getItem(LS) || '{}');
    this.layers = {};
    this._build();
    game.on('gameover', (g) => this.showEnd(g));
    game.on('tick', () => this.refreshHud());
    game.on('dna', (g) => this.flashDna(g));
    game.on('frame', () => this.updateBubbleLayer());
  }

  _build() {
    this.menuEl = this._buildMenu();
    this.root.append(this.menuEl);
  }

  show(name) {
    for (const k in this.layers) this.layers[k].style.display = 'none';
    if (this.layers[name]) this.layers[name].style.display = '';
    this.active = name;
  }

  // ------------------------------------------------------------- Menü
  _buildMenu() {
    const play = h('button', { class: 'btn primary big', onclick: () => this.openTypeSelect() }, 'Neues Spiel');
    const cont = h('div', { class: 'menu-sub' }, 'Ein globales Krankheits-Strategiespiel');
    const wrap = h('div', { class: 'screen menu' },
      h('div', { class: 'menu-bg' }),
      h('div', { class: 'menu-title' }, h('span', { class: 'plague-word' }, 'PANDEMIA'), h('div', { class: 'menu-tag' }, 'GLOBAL OUTBREAK')),
      cont,
      h('div', { class: 'menu-buttons' }, play),
      h('div', { class: 'menu-foot' }, '3D-Erregermodelle in Blender erstellt · Klicke „Neues Spiel", um zu beginnen'),
    );
    this.layers.menu = wrap;
    return wrap;
  }

  // ------------------------------------------------------------- Typauswahl / Speed Run
  openTypeSelect() {
    if (!this.layers.type) this.layers.type = this._buildTypeSelect();
    this.root.append(this.layers.type);
    this.show('type');
    this._selectType(this.sel.type);
    this.viewer.spin = true;
    this.viewer.setPathogen(this.sel.type);
  }

  _buildTypeSelect() {
    this.typeList = h('div', { class: 'type-list' });
    for (const key of PATHOGEN_ORDER) {
      const p = PATHOGENS[key];
      const rec = this.records[key];
      const row = h('div', { class: 'type-row', 'data-key': key, onclick: () => this._selectType(key) },
        h('div', { class: 'type-info' },
          h('div', { class: 'type-name' }, p.name),
          h('div', { class: 'type-desc' }, p.tagline)),
        h('div', { class: 'type-badge', style: `--c:${p.color}` }, '☣'),
        h('div', { class: 'type-days' }, rec ? rec + ' T' : '– T'),
      );
      this.typeList.append(row);
    }
    const diffSel = h('div', { class: 'diff-select' },
      ...['leicht', 'normal', 'brutal'].map((d) =>
        h('button', { class: 'diff-btn' + (d === this.sel.difficulty ? ' active' : ''), 'data-d': d, onclick: (e) => this._setDiff(d) },
          d[0].toUpperCase() + d.slice(1))));
    this.typeDetail = h('div', { class: 'type-detail' });
    const el = h('div', { class: 'screen typescreen' },
      h('div', { class: 'ts-header' },
        h('button', { class: 'btn back', onclick: () => { this.show('menu'); } }, '‹ Zurück'),
        h('h1', {}, 'Erreger wählen'),
        h('div', {})),
      h('div', { class: 'ts-body' },
        h('div', { class: 'ts-viewer-hint' }, 'Speed-Run-Bestzeiten je Typ'),
        this.typeList,
        h('div', { class: 'ts-right' }, this.typeDetail, diffSel,
          h('button', { class: 'btn primary', onclick: () => this.openName() }, 'Weiter ›'))),
    );
    return el;
  }

  _setDiff(d) {
    this.sel.difficulty = d;
    this.layers.type.querySelectorAll('.diff-btn').forEach((b) => b.classList.toggle('active', b.dataset.d === d));
  }

  _selectType(key) {
    this.sel.type = key;
    const p = PATHOGENS[key];
    this.layers.type.querySelectorAll('.type-row').forEach((r) => r.classList.toggle('active', r.dataset.key === key));
    this.typeDetail.innerHTML = '';
    this.typeDetail.append(
      h('h2', { style: `color:${p.accent}` }, p.name),
      h('p', {}, p.desc),
      h('p', { class: 'intro' }, p.intro),
      h('div', { class: 'stars' }, 'Schwierigkeit: ' + '★'.repeat(p.difficulty) + '☆'.repeat(7 - p.difficulty)),
    );
    this.viewer.setPathogen(key);
    this.viewer.playSpecial('selected');
  }

  // ------------------------------------------------------------- Benennung
  openName() {
    if (!this.layers.name) {
      this.nameInput = h('input', { class: 'name-input', maxlength: '22', placeholder: 'z. B. Rote Grippe' });
      this.layers.name = h('div', { class: 'screen namescreen' },
        h('div', { class: 'name-box' },
          h('h1', {}, 'Benenne deinen Erreger'),
          this.nameInput,
          h('div', { class: 'name-buttons' },
            h('button', { class: 'btn back', onclick: () => this.show('type') }, '‹ Zurück'),
            h('button', { class: 'btn primary', onclick: () => this.confirmName() }, 'Weiter ›'))));
      this.root.append(this.layers.name);
      this.nameInput.addEventListener('keydown', (e) => { if (e.key === 'Enter') this.confirmName(); });
    }
    this.nameInput.value = this.sel.name || PATHOGENS[this.sel.type].name + ' X';
    this.show('name');
    setTimeout(() => this.nameInput.focus(), 50);
  }

  confirmName() {
    const v = this.nameInput.value.trim();
    this.sel.name = v || PATHOGENS[this.sel.type].name;
    this.openStartCountry();
  }

  // ------------------------------------------------------------- Startland
  openStartCountry() {
    if (!this.layers.start) {
      this.startInfo = h('div', { class: 'start-info' }, 'Wähle das Ursprungsland auf der Karte.');
      this.startConfirm = h('button', { class: 'btn primary', disabled: '', onclick: () => this.beginGame() }, 'Ausbruch starten');
      this.layers.start = h('div', { class: 'screen startscreen' },
        h('div', { class: 'start-bar' },
          h('button', { class: 'btn back', onclick: () => this.openName() }, '‹ Zurück'),
          h('h1', {}, 'Startland wählen'),
          this.startConfirm),
        this.startInfo);
      this.root.append(this.layers.start);
    }
    // Karte übernimmt Auswahl
    this.game.map.mode = 'select';
    this.game.map.setEngine(null);
    this.game.map.selectedIso = null;
    this.game.map.onPick = (iso) => this._pickStart(iso);
    // Map-Canvas in diesen Screen bewegen
    this._mountMap(this.layers.start, false);
    if (!this.game._raf) this.game._loop();
    this.show('start');
  }

  _pickStart(iso) {
    this.sel.startIso = iso;
    const c = this.game.world.countries.find((x) => x.iso === iso);
    this.startInfo.innerHTML = '';
    this.startInfo.append(
      h('div', { class: 'start-country' },
        h('h2', {}, c.name),
        h('div', { class: 'start-grid' },
          stat('Bevölkerung', fmt(c.pop)),
          stat('Klima', c.climate),
          stat('Wohlstand', bars(c.wealth)),
          stat('Med. Versorgung', bars(c.medical)),
          stat('Urbanisierung', bars(c.urban)),
          stat('Reiseverkehr', bars(c.travel)),
          stat('Flughafen', c.airport ? 'ja' : 'nein'),
          stat('Hafen', c.port ? 'ja' : 'nein'),
          stat('Nachbarländer', String(c.neighbors.length)),
        )));
    this.startConfirm.disabled = false;
  }

  beginGame() {
    if (!this.sel.startIso) return;
    this.game.map.onPick = (iso) => this.showCountry(iso);
    this._mountMap(this.layers.game || this._buildGame(), true);
    this.game.start({ type: this.sel.type, name: this.sel.name, startIso: this.sel.startIso, difficulty: this.sel.difficulty });
    this.show('game');
    this.refreshHud();
    this.viewer.spin = true;
    this.viewer.setPathogen(this.sel.type);
    this.pushNewsInit();
    if (this.audio) this.audio.playGame();   // Spielmusik von vorne
    // Xenolith: kleiner Asteroid schlägt am Startland ein
    if (this.sel.type === 'xenolith') {
      this.game.setSpeed(0);
      this.game.map.playAsteroidIntro(this.sel.startIso, () => {
        this.game.setSpeed(2);
        this.game.eng.pushNews(`Ein Meteorit schlägt in ${this.game.world.countries.find((c) => c.iso === this.sel.startIso).name} ein – fremdartige Kristalle breiten sich aus.`, 'special', this.sel.startIso);
      });
    }
  }

  _mountMap(target, small) {
    const holder = target.querySelector('.map-holder') || target;
    const cv = this.game.map.canvas;
    holder.append(cv);
    requestAnimationFrame(() => this.game.map.resize());
  }

  // ------------------------------------------------------------- Spiel-HUD
  _buildGame() {
    // Kopfzeile
    this.hudName = h('div', { class: 'hud-name' });
    this.hudDate = h('div', { class: 'hud-date' });
    this.speedBtns = SPEEDS.map((s, i) =>
      h('button', { class: 'sp-btn', 'data-i': i, onclick: () => this.game.setSpeed(i) }, s === 0 ? '❚❚' : s + '×'));
    const top = h('div', { class: 'hud-top' },
      h('div', { class: 'hud-left' }, this.hudName, this.hudDate),
      h('div', { class: 'hud-speed' }, ...this.speedBtns));

    // linke Statistik
    this.statInfected = statBig('Infiziert', '#ff8a3a');
    this.statDead = statBig('Tote', '#ff4a4a');
    this.statHealthy = statBig('Gesund', '#5ada6a');
    this.statCountries = statBig('Länder', '#8ab0ff');
    this.statSpecial = statBig('—', '#c890ff');
    this.statSpecial.el.style.display = 'none';
    const leftStats = h('div', { class: 'hud-stats' }, this.statInfected.el, this.statDead.el, this.statHealthy.el, this.statCountries.el, this.statSpecial.el);

    // Balken
    this.barInf = bar('Übertragbarkeit', '#e050c0');
    this.barSev = bar('Schweregrad', '#e0e050');
    this.barLeth = bar('Letalität', '#a050e0');
    this.barCure = bar('Heilmittel', '#40a0ff');
    this.barAlarm = bar('Alarmstufe', '#ff8030');
    const bars_ = h('div', { class: 'hud-bars' }, this.barInf.el, this.barSev.el, this.barLeth.el, this.barCure.el, this.barAlarm.el);

    // DNA + Krankheit-Button
    this.dnaEl = h('div', { class: 'dna-counter' }, h('span', { class: 'dna-icon' }, '🧬'), h('span', { class: 'dna-val' }, '0'));
    const diseaseBtn = h('button', { class: 'disease-btn', onclick: () => this.openEvolution() }, h('span', {}, '☣'), h('small', {}, 'Krankheit'));

    // Nachrichten-Ticker
    this.newsTicker = h('div', { class: 'news-ticker' });
    const newsBar = h('div', { class: 'news-bar', onclick: () => this.toggleNewsLog() },
      h('span', { class: 'news-label' }, 'NEWS'), this.newsTicker);

    // Sonderfähigkeiten-Leiste (Necroa/Neurax/... Buttons)
    this.specialBar = h('div', { class: 'special-bar' });

    this.bubbleLayer = h('div', { class: 'bubble-layer' });

    const el = h('div', { class: 'screen gamescreen' },
      h('div', { class: 'map-holder' }, this.bubbleLayer),
      top, newsBar, leftStats, bars_,
      h('div', { class: 'hud-bottom' }, this.specialBar, this.dnaEl, diseaseBtn),
    );
    this.layers.game = el;
    this.root.append(el);
    return el;
  }

  pushNewsInit() { this._newsIdx = 0; this._newsShown = 0; }

  refreshHud() {
    const eng = this.game.eng; if (!eng) return;
    this.hudName.textContent = eng.opts.name;
    this.hudDate.textContent = eng.date.toLocaleDateString('de-DE');
    this.speedBtns.forEach((b, i) => b.classList.toggle('active', i === this.game.speedIndex));
    this.statInfected.set(fmt(eng.totalInfected()));
    this.statDead.set(fmt(eng.totalDead()));
    this.statHealthy.set(fmt(eng.totalHealthy()));
    this.statCountries.set(eng.countriesInfected() + '/' + this.game.world.countries.length);
    // Sonderzustand anzeigen (Zombies/Kontrolliert/Affen/Vampire/Xeno)
    const sp = eng.special;
    let special = null;
    if (sp.zombies > 1) special = ['Zombies', fmt(sp.zombies)];
    else if (sp.controlled > 1) special = ['Kontrolliert', fmt(sp.controlled)];
    else if (sp.apes > 1) special = ['Affen', fmt(sp.apes)];
    else if (sp.vampires > 1) special = ['Vampire', fmt(sp.vampires)];
    else if (sp.xmon > 1) special = ['Kristallwesen', fmt(sp.xmon)];
    else if (sp.xeno > 0.001) special = ['Xenoforming', (sp.xeno * 100).toFixed(1) + '%'];
    if (special) {
      this.statSpecial.el.style.display = '';
      this.statSpecial.el.querySelector('.sb-label').textContent = special[0];
      this.statSpecial.set(special[1]);
    } else this.statSpecial.el.style.display = 'none';
    this.barInf.set(clampBar(eng.infectivity / 40));
    this.barSev.set(clampBar(eng.severity / 40));
    this.barLeth.set(clampBar(eng.lethality / 60));
    this.barCure.set(eng.cure, cureText(eng));
    this.barAlarm.set(eng.priority / 1.6, alarmText(eng));
    this.dnaEl.querySelector('.dna-val').textContent = Math.floor(eng.dna);
    this.updateNews();
    this.updateSpecialBar();
    if (this.evoOpen) this.refreshEvolution();
    if (this.countryOpen) this.refreshCountry();
  }

  updateNews() {
    const eng = this.game.eng;
    if (eng.news.length > this._newsShown) {
      const latest = eng.news[eng.news.length - 1];
      this.newsTicker.textContent = latest.text;
      this.newsTicker.classList.remove('flash'); void this.newsTicker.offsetWidth; this.newsTicker.classList.add('flash');
      this._newsShown = eng.news.length;
    }
  }

  toggleNewsLog() {
    if (this.newsLogEl && this.newsLogEl.parentElement) { this.newsLogEl.remove(); this.newsLogEl = null; return; }
    const eng = this.game.eng;
    this.newsLogEl = h('div', { class: 'news-log' },
      h('div', { class: 'news-log-head' }, 'Nachrichtenverlauf', h('button', { class: 'x', onclick: () => this.toggleNewsLog() }, '×')),
      h('div', { class: 'news-log-body' },
        ...[...eng.news].reverse().slice(0, 60).map((n) =>
          h('div', { class: 'news-item ' + n.kind }, h('span', { class: 'nd' }, n.date.toLocaleDateString('de-DE')), n.text))));
    this.layers.game.append(this.newsLogEl);
  }

  updateSpecialBar() {
    const eng = this.game.eng;
    const sp = eng.special;
    // aktive Sonderaktionen je nach Virus
    const actions = [];
    if (eng.evolved.has('spore_burst')) actions.push({ id: 'spore', icon: '💥', label: 'Sporenausbruch', fn: () => { eng.triggerAbility(eng.evolved.has('spore_eruption') ? 'spore_eruption' : 'spore_burst'); this.flashDna(); } });
    if (sp.controlActive) {
      actions.push({ id: 'control', icon: '🧠', label: 'Wirte einfliegen', directed: 'control', plane: true, amounts: true, color: 'rgba(200,120,255,1)' });
      actions.push({ id: 'will', icon: '🌀', label: 'Massenbekehrung', fn: () => { eng.willToInfect(); this.game.map.spawnBubble({ iso: eng.startCountry, type: 'special' }); } });
    }
    if (sp.vampireActive) actions.push({ id: 'vampire', icon: '🩸', label: 'Die Jagd', directed: 'vampire', color: 'rgba(255,40,120,1)' });
    if (sp.zombieActive) actions.push({ id: 'zombie', icon: '🧟', label: 'Zombie-Horde schicken', directed: 'zombie', amounts: true, color: 'rgba(120,230,80,1)' });
    if (sp.xenoActive) actions.push({ id: 'crystal', icon: '💠', label: 'Kristallwesen aussenden', directed: 'crystal', amounts: true, color: 'rgba(200,140,255,1)' });
    const sig = actions.map((a) => a.id).join(',') + eng.opts.type;
    if (sig === this._specialSig) return;
    this._specialSig = sig;
    this.specialBar.innerHTML = '';
    for (const a of actions) {
      this.specialBar.append(h('button', {
        class: 'sp-ability', title: a.directed ? 'Ziel auf der Karte wählen' : (a.fn ? 'Sofort auslösen' : ''),
        onclick: () => { if (a.directed) this._startDirected(a); else a.fn(); },
      }, a.icon + ' ' + a.label));
    }
  }

  _startDirected(a) {
    const eng = this.game.eng;
    const go = (amount) => {
      const from = eng.list.slice().sort((x, y) => y.infected - x.infected)[0];
      this._flashHint(a.icon + ' Zielland auf der Karte wählen …');
      this.game.map.requestTarget((toIso) => {
        const seed = amount || 200;
        const fromIso = from ? from.ref.iso : eng.startCountry;
        const done = () => { eng.directSeed(toIso, a.directed, seed); this.game.map.spawnBubble({ iso: toIso, type: 'special' }); };
        if (a.plane) this.game.map.sendPlane(fromIso, toIso, a.color, done);
        else this.game.map.sendAgent(fromIso, toIso, a.color, done);
      });
    };
    if (a.amounts) {
      // Mengenauswahl (narrativ: wie viele Reisende/Wirte)
      this._amountChooser(a, [['20 Reisende', 2e5], ['100 Reisende', 1e6], ['500 Reisende', 5e6]], go);
    } else go();
  }

  _amountChooser(a, opts, cb) {
    if (this._chooserEl) this._chooserEl.remove();
    this._chooserEl = h('div', { class: 'chooser' },
      h('div', { class: 'chooser-title' }, a.icon + ' ' + a.label + ' – wie viele?'),
      h('div', { class: 'chooser-btns' },
        ...opts.map(([label, val]) => h('button', { class: 'btn', onclick: () => { this._chooserEl.remove(); this._chooserEl = null; cb(val); } }, label)),
        h('button', { class: 'btn back', onclick: () => { this._chooserEl.remove(); this._chooserEl = null; } }, 'Abbrechen')));
    this.layers.game.append(this._chooserEl);
  }

  _flashHint(text) {
    if (!this._hintEl) { this._hintEl = h('div', { class: 'map-hint' }); this.layers.game.append(this._hintEl); }
    this._hintEl.textContent = text; this._hintEl.style.opacity = '1';
    clearTimeout(this._hintT); this._hintT = setTimeout(() => { if (this._hintEl) this._hintEl.style.opacity = '0'; }, 2600);
  }

  flashDna() {
    this.dnaEl.classList.remove('pulse'); void this.dnaEl.offsetWidth; this.dnaEl.classList.add('pulse');
    if (this.game.eng) this.dnaEl.querySelector('.dna-val').textContent = Math.floor(this.game.eng.dna);
  }

  updateBubbleLayer() {
    // DNA-Blasen werden auf dem Canvas gezeichnet; hier nichts Zusätzliches.
  }

  // ------------------------------------------------------------- Länderinfo
  showCountry(iso) {
    this.countryIso = iso; this.countryOpen = true;
    if (!this.countryPanel) {
      this.countryPanel = h('div', { class: 'country-panel' });
      this.layers.game.append(this.countryPanel);
    }
    this.countryPanel.style.display = '';
    this.refreshCountry();
  }

  refreshCountry() {
    const eng = this.game.eng; const iso = this.countryIso;
    const st = eng.countries[iso]; if (!st) return;
    const c = st.ref;
    this.countryPanel.innerHTML = '';
    this.countryPanel.append(
      h('div', { class: 'cp-head' }, h('h3', {}, c.name),
        h('button', { class: 'x', onclick: () => { this.countryPanel.style.display = 'none'; this.countryOpen = false; } }, '×')),
      h('div', { class: 'cp-grid' },
        stat('Bevölkerung', fmt(st.pop)),
        stat('Infiziert', fmt(st.infected)),
        stat('Tot', fmt(st.dead)),
        stat('Gesund', fmt(st.healthy)),
        stat('Med. Versorgung', bars(c.medical)),
        stat('Klima', c.climate),
        stat('Wohlstand', bars(c.wealth)),
        stat('Flughafen', st.airportOpen ? 'offen' : 'geschlossen'),
        stat('Hafen', c.port ? (st.portOpen ? 'offen' : 'geschlossen') : '–'),
        stat('Grenzen', st.bordersOpen ? 'offen' : 'geschlossen'),
        stat('Quarantäne', st.measures > 0.05 ? bars(st.measures / 0.8) : 'keine'),
        stat('Staat', st.collapse > 0.8 ? 'zerfallen' : st.collapse > 0.05 ? 'bricht zusammen' : 'stabil'),
        ...(st.zombies > 1 ? [stat('Zombies', fmt(st.zombies))] : []),
        ...(st.fortress > 0.15 ? [stat('Militärfestung', Math.round(st.fortress * 100) + '%')] : []),
        ...(st.apes > 1 ? [stat('Affen', fmt(st.apes))] : []),
        ...(st.controlled > 1 ? [stat('Kontrolliert', fmt(st.controlled))] : []),
        ...(st.vampires > 1 ? [stat('Vampire', fmt(st.vampires))] : []),
        ...(st.xeno > 0.01 ? [stat('Kristallisation', Math.round(st.xeno * 100) + '%')] : []),
        ...(st.xmon > 1 ? [stat('Kristallwesen', fmt(st.xmon))] : []),
      ));
  }

  // ------------------------------------------------------------- Evolution
  openEvolution() {
    if (!this.layers.evo) this._buildEvolution();
    this.evoOpen = true;
    this.show('evo');
    this._mountViewerTo(this.evoViewerHolder);
    this.viewer.spin = false;
    if (!this.hologram) this.hologram = new HologramViewer(this.holoCanvas);
    this.setEvoTab(this.evoTab || 'transmission');
    this.refreshEvolution();
    // Kartenrender pausiert visuell nicht – Sim läuft weiter im Hintergrund
  }

  closeEvolution() {
    this.evoOpen = false;
    this.show('game');
    this._mountViewerTo(null);
    this.viewer.spin = true;
  }

  _buildEvolution() {
    this.evoTabs = {};
    const tabBar = h('div', { class: 'evo-tabs' },
      ...[['overview', 'Übersicht'], ['transmission', 'Übertragung'], ['symptoms', 'Symptome'], ['abilities', 'Fähigkeiten']].map(([k, label]) => {
        const b = h('button', { class: 'evo-tab', onclick: () => this.setEvoTab(k) }, label);
        this.evoTabs[k] = b; return b;
      }));
    this.evoContent = h('div', { class: 'evo-content' });
    this.evoDetail = h('div', { class: 'evo-detail' });
    this.evoViewerHolder = h('div', { class: 'evo-viewer' });
    this.holoCanvas = h('canvas', { class: 'holo-canvas' });
    this.evoDnaEl = h('div', { class: 'evo-dna' }, '🧬 ', h('span', { class: 'v' }, '0'));
    const el = h('div', { class: 'screen evoscreen' },
      h('div', { class: 'evo-header' },
        h('div', { class: 'evo-title' }),
        tabBar,
        this.evoDnaEl,
        h('button', { class: 'btn back', onclick: () => this.closeEvolution() }, 'Zurück zur Karte ›')),
      h('div', { class: 'evo-main' },
        h('div', { class: 'evo-graph' }, this.evoContent, this.evoDetail),
        h('div', { class: 'evo-side' }, this.evoViewerHolder,
          h('div', { class: 'holo-wrap' }, this.holoCanvas),
          h('div', { class: 'mini-bars' }, this._miniBars()))),
    );
    this.evoTitle = el.querySelector('.evo-title');
    this.layers.evo = el;
    this.root.append(el);
  }

  _miniBars() {
    this.mBarInf = bar('Übertragbarkeit', '#e050c0');
    this.mBarSev = bar('Schweregrad', '#e0e050');
    this.mBarLeth = bar('Letalität', '#a050e0');
    this.mBarCure = bar('Heilmittel', '#40a0ff');
    return h('div', {}, this.mBarInf.el, this.mBarSev.el, this.mBarLeth.el, this.mBarCure.el);
  }

  _mountViewerTo(holder) {
    const cv = this.viewer.canvas;
    if (holder) { holder.append(cv); }
    else { this.viewerHome.append(cv); }
    requestAnimationFrame(() => this.viewer.resize());
  }

  setEvoTab(tab) {
    this.evoTab = tab;
    for (const k in this.evoTabs) this.evoTabs[k].classList.toggle('active', k === tab);
    this.evoContent.innerHTML = '';
    if (tab === 'symptoms') this._renderHexGrid();
    else if (tab === 'overview') this._renderOverview();
    else this._renderTraitList(tab === 'transmission' ? TRANSMISSION : this._abilityDefs());
    this.refreshEvolution();
  }

  _abilityDefs() {
    const eng = this.game.eng;
    const base = ABILITIES;
    const spec = eng.def.abilities || [];
    return [...spec, ...base];
  }

  _renderOverview() {
    const eng = this.game.eng;
    const evolved = eng.evoOrder.map((id) => this._traitById(id)).filter(Boolean);
    this.evoContent.append(
      h('div', { class: 'overview' },
        h('h2', {}, eng.opts.name),
        h('p', {}, PATHOGENS[eng.opts.type].desc),
        h('h3', {}, 'Entwickelte Eigenschaften (' + evolved.length + ')'),
        h('div', { class: 'evolved-list' },
          ...evolved.length ? evolved.map((t) => h('span', { class: 'chip', title: t.desc }, (t.icon || '•') + ' ' + t.name)) : [h('span', { class: 'muted' }, 'Noch nichts entwickelt.')]),
        h('div', { class: 'ov-stats' },
          stat('Startland', this.game.world.countries.find((c) => c.iso === eng.startCountry).name),
          stat('Tag', String(eng.day)),
          stat('Länder betroffen', String(eng.countriesInfected())),
          stat('DNA gesammelt', String(eng.totalDnaEarned)),
        )));
  }

  _traitById(id) {
    return TRANSMISSION.find((t) => t.id === id) || ABILITIES.find((t) => t.id === id) ||
      SYMPTOMS.find((t) => t.id === id) || (this.game.eng.def.abilities || []).find((t) => t.id === id);
  }

  _renderTraitList(list) {
    const grid = h('div', { class: 'trait-grid' });
    for (const t of list) grid.append(this._traitNode(t));
    this.evoContent.append(grid);
  }

  _traitNode(t) {
    const node = h('button', { class: 'trait', 'data-id': t.id, onclick: () => this.selectTrait(t) },
      h('span', { class: 'trait-icon' }, t.icon || '•'),
      h('span', { class: 'trait-name' }, t.name),
      h('span', { class: 'trait-cost' }, t.cost + ' 🧬'));
    return node;
  }

  _renderHexGrid() {
    // Hexraster wie im Symptom-Bildschirm
    const wrap = h('div', { class: 'hexgrid' });
    const size = 46, hgap = size * 0.86, vgap = size * 0.99;
    let maxX = 0, maxY = 0;
    const svgNS = 'http://www.w3.org/2000/svg';
    const links = document.createElementNS(svgNS, 'svg');
    links.setAttribute('class', 'hex-links');
    wrap.append(links);
    const byCoord = {};
    for (const s of SYMPTOMS) byCoord[s.q + ',' + s.r] = s;
    for (const s of SYMPTOMS) {
      const x = s.q * hgap, y = s.r * vgap + (s.q % 2 ? vgap / 2 : 0);
      maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
      const hex = h('button', { class: 'hex', 'data-id': s.id, style: `left:${x}px;top:${y}px`, onclick: () => this.selectTrait(s) },
        h('span', { class: 'hex-icon' }, s.icon), h('span', { class: 'hex-cost' }, s.cost));
      hex.dataset.q = s.q; hex.dataset.r = s.r;
      wrap.append(hex);
    }
    // Verbindungslinien zu benachbarten Symptomen
    links.setAttribute('width', maxX + size); links.setAttribute('height', maxY + size);
    for (const s of SYMPTOMS) {
      const x = s.q * hgap + size / 2, y = s.r * vgap + (s.q % 2 ? vgap / 2 : 0) + size / 2;
      for (const [nq, nr] of HEX_NEIGHBORS(s.q, s.r)) {
        const n = byCoord[nq + ',' + nr];
        if (n && (n.q > s.q || (n.q === s.q && n.r > s.r))) {
          const x2 = n.q * hgap + size / 2, y2 = n.r * vgap + (n.q % 2 ? vgap / 2 : 0) + size / 2;
          const ln = document.createElementNS(svgNS, 'line');
          ln.setAttribute('x1', x); ln.setAttribute('y1', y); ln.setAttribute('x2', x2); ln.setAttribute('y2', y2);
          ln.setAttribute('class', 'hex-link');
          links.append(ln);
        }
      }
    }
    wrap.style.width = (maxX + size) + 'px';
    wrap.style.height = (maxY + size) + 'px';
    this.evoContent.append(wrap);
  }

  selectTrait(t) {
    this.selectedTrait = t;
    const eng = this.game.eng;
    const owned = eng.evolved.has(t.id);
    const can = eng.canEvolve(t.id);
    this.evoDetail.innerHTML = '';
    const organ = t.organ ? ' · Organ: ' + t.organ : '';
    const btn = owned
      ? h('button', { class: 'btn devolve', disabled: this._canDevolve(t) ? undefined : '', onclick: () => this.doDevolve(t) },
        'Zurückentwickeln (' + Math.max(2, Math.round(t.cost * 0.35)) + ' 🧬)')
      : h('button', { class: 'btn evolve', disabled: can ? undefined : '', onclick: () => this.doEvolve(t) },
        can ? 'Entwickeln (' + t.cost + ' 🧬)' : (eng.dna < t.cost ? 'Zu wenig DNA' : 'Voraussetzung fehlt'));
    this.evoDetail.append(
      h('div', { class: 'detail-head' }, h('span', { class: 'detail-icon' }, t.icon || '•'), h('h3', {}, t.name)),
      h('p', {}, t.desc),
      h('div', { class: 'detail-meta' }, 'Kosten: ' + t.cost + ' DNA' + organ + (owned ? ' · entwickelt' : '')),
      btn);
    this._highlightTraits();
  }

  _canDevolve(t) {
    const eng = this.game.eng;
    if (t.control || t.zombie || t.vampire || t.apes || t.xeno) return false;
    for (const other of eng.evolved) {
      const od = this._traitById(other);
      if (od && od.req && od.req.includes(t.id)) return false;
    }
    return eng.dna >= Math.max(2, Math.round(t.cost * 0.35));
  }

  doEvolve(t) {
    if (this.game.eng.evolve(t.id)) {
      this.viewer.playSpecial(t.cat === 'symptom' ? 'mutation' : 'pulse');
      const lvl = this._evoLevel();
      this.viewer.setEvolutionLevel(lvl);
      this.refreshEvolution();
      this.selectTrait(t);
      this.flashDna();
    }
  }
  doDevolve(t) {
    if (this.game.eng.devolve(t.id)) {
      this.viewer.setEvolutionLevel(this._evoLevel());
      this.refreshEvolution();
      this.selectTrait(t);
    }
  }

  _evoLevel() {
    // grobe Zuordnung: Anzahl entwickelter Symptome bestimmt sichtbare Evo-Stufe
    const eng = this.game.eng;
    const n = eng.evoOrder.filter((id) => SYMPTOMS.some((s) => s.id === id)).length +
      (eng.def.abilities || []).filter((a) => eng.evolved.has(a.id)).length;
    return Math.min(3, Math.floor(n / 3));
  }

  refreshEvolution() {
    const eng = this.game.eng; if (!eng) return;
    this.evoTitle.textContent = eng.opts.name + ' · ' + PATHOGENS[eng.opts.type].name;
    this.evoDnaEl.querySelector('.v').textContent = Math.floor(eng.dna);
    if (this.mBarInf) {
      this.mBarInf.set(clampBar(eng.infectivity / 40));
      this.mBarSev.set(clampBar(eng.severity / 40));
      this.mBarLeth.set(clampBar(eng.lethality / 60));
      this.mBarCure.set(eng.cure, cureText(eng));
    }
    this._highlightTraits();
    // Hologramm-Organe nach entwickelten Symptomen
    if (this.hologram) {
      const organs = new Set();
      for (const id of eng.evolved) { const s = SYMPTOMS.find((x) => x.id === id); if (s && s.organ) organs.add(s.organ); }
      this.hologram.setOrgans([...organs]);
    }
  }

  _highlightTraits() {
    const eng = this.game.eng;
    this.evoContent.querySelectorAll('[data-id]').forEach((node) => {
      const id = node.dataset.id;
      const owned = eng.evolved.has(id);
      const can = eng.canEvolve(id);
      node.classList.toggle('owned', owned);
      node.classList.toggle('affordable', !owned && can);
      node.classList.toggle('locked', !owned && !can);
      node.classList.toggle('selected', this.selectedTrait && this.selectedTrait.id === id);
    });
  }

  // ------------------------------------------------------------- Ende
  showEnd(g) {
    const eng = this.game.eng;
    const p = PATHOGENS[eng.opts.type];
    const win = g.win;
    if (win) {
      const rec = this.records[eng.opts.type];
      if (!rec || g.day < rec) { this.records[eng.opts.type] = g.day; localStorage.setItem(LS, JSON.stringify(this.records)); }
    }
    const evolved = eng.evoOrder.map((id) => this._traitById(id)).filter(Boolean);
    const el = h('div', { class: 'screen endscreen ' + (win ? 'win' : 'lose') },
      h('div', { class: 'end-box' },
        h('h1', {}, win ? 'KRANKHEIT HAT GEWONNEN' : 'DIE MENSCHHEIT HAT ÜBERLEBT'),
        h('div', { class: 'end-sub' }, g.text),
        h('div', { class: 'end-grid' },
          stat('Erreger', eng.opts.name),
          stat('Typ', p.name),
          stat('Startland', this.game.world.countries.find((c) => c.iso === eng.startCountry).name),
          stat('Infiziert (Höchststand)', fmt(eng.stats.infectedPeak)),
          stat('Tote', fmtFull(eng.totalDead())),
          stat('Überlebende', fmtFull(eng.totalHealthy())),
          stat('Betroffene Länder', String(eng.countriesInfected())),
          stat('Heilmittel', (eng.cure * 100).toFixed(0) + '%'),
          stat('Dauer', eng.day + ' Tage'),
          stat('DNA gesammelt', String(eng.totalDnaEarned)),
        ),
        h('div', { class: 'end-traits' }, h('h3', {}, 'Entwickelte Eigenschaften'),
          h('div', { class: 'evolved-list' }, ...evolved.map((t) => h('span', { class: 'chip' }, (t.icon || '•') + ' ' + t.name)))),
        h('button', { class: 'btn primary big', onclick: () => this.returnToMenu(el) }, 'Zum Hauptmenü')));
    this.root.append(el);
    this.layers.end = el;
    this.show('end');
    this.game.setSpeed(0);
    if (this.audio) this.audio.playMenu();   // Runde gestoppt -> wieder Investigative
  }

  returnToMenu(endEl) {
    endEl.remove();
    this.viewerHome.append(this.viewer.canvas);
    this.viewer.spin = true;
    this.show('menu');
  }
}

// -------- kleine UI-Bausteine --------
function stat(label, value) {
  return h('div', { class: 'stat' }, h('span', { class: 'stat-l' }, label), h('span', { class: 'stat-v' }, value));
}
function statBig(label, color) {
  const v = h('div', { class: 'sb-val', style: `color:${color}` }, '0');
  const el = h('div', { class: 'stat-big' }, v, h('div', { class: 'sb-label' }, label));
  return { el, set: (t) => { v.textContent = t; } };
}
function bar(label, color) {
  const fill = h('div', { class: 'bar-fill', style: `background:${color}` });
  const val = h('span', { class: 'bar-val' });
  const el = h('div', { class: 'bar' }, h('div', { class: 'bar-label' }, label, val), h('div', { class: 'bar-track' }, fill));
  return { el, set: (f, txt) => { fill.style.width = clampBar(f) * 100 + '%'; if (txt) val.textContent = txt; } };
}
function bars(v) {
  const n = Math.round(v * 5);
  return '▮'.repeat(n) + '▯'.repeat(5 - n);
}
function clampBar(x) { return Math.max(0, Math.min(1, x)); }
function cureText(eng) {
  const pct = (eng.cure * 100).toFixed(0) + '%';
  return eng.cureRate > 0.00005 && eng.cure < 1 ? `${pct} · +${(eng.cureRate * 100).toFixed(2)}%/Tag` : pct;
}
function alarmText(eng) {
  const p = eng.priority;
  if (!eng.detected) return 'unbemerkt';
  return p < 0.25 ? 'Beobachtung' : p < 0.5 ? 'erhöht' : p < 1 ? 'Notstand' : 'Panik';
}
