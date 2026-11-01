/* Velora 5.4: Haptik, smoothes Design, Navigationspfeil, Maskottchen Velo und Velora Plus.
   Haengt sich ueber window.VX an die Haken in app.js (HOOK). */
(function () {
  'use strict';
  var V = window.V; if (!V) return;
  var A = V.A, S = V.S, $ = V.$, esc = V.esc, svg = V.svg, map = V.map;
  var VX = window.VX = {};
  var TESTER = !!window.__TESTER;
  var KI_BASE = 'https://velora-ki.toni-sagmeister11.workers.dev', KI_ID = 'velora-2026';

  function dayKey(t) { var d = new Date(t); return d.getFullYear() + '-' + ('0' + (d.getMonth() + 1)).slice(-2) + '-' + ('0' + d.getDate()).slice(-2); }
  function dayStart(t) { var d = new Date(t); d.setHours(0, 0, 0, 0); return d.getTime(); }
  function weekStart(t) { var d = new Date(dayStart(t)); var wd = (d.getDay() + 6) % 7; return d.getTime() - wd * 864e5; }
  function km(m) { return V.fmtKm(m); }
  function num(v) { return String(v).replace('.', ','); }
  function rnd(n) { var c = 'abcdefghijkmnpqrstuvwxyz23456789', o = ''; for (var i = 0; i < n; i++) o += c[Math.floor(Math.random() * c.length)]; return o; }

  // =====================================================================
  // Velora Plus (Bezahlung kommt spaeter ueber Google Play, bis dahin zum Testen freischaltbar)
  // =====================================================================
  var P = V.load('plus', { on: false });
  function isPlus() { return !!P.on; }
  VX.setPlusQuiet = function (on) { P.on = !!on; V.store('plus', P); applyLook(); };
  function setPlus(on) { P.on = on; P.since = on ? Date.now() : 0; V.store('plus', P); applyLook(); updWidget(); if (VX.onPlus) VX.onPlus(on); }
  var TAG = '<span class="plustag">PLUS</span>';
  var FEATS = [
    ['juno', '100 Juno-Credits am Tag', 'Statt 10. Für lange Gespräche, Recherche und viele Routen.'],
    ['juno', 'Juno als Reiseführer', 'Erzählt unterwegs, an welchen Orten du gerade vorbeifährst.'],
    ['loop', 'Tagesplaner', 'Ein ganzer Tag mit Aussicht, Mittagessen und Badestopp als eine Route.'],
    ['bike', 'Mehr Fahrprofile', 'Gravel sowie Familie mit Kindern (ruhig und flach).'],
    ['wind', 'E-Bike-Akkuplaner', 'Reicht dein Akku für die Route? Mit Ladestationen in der Nähe.'],
    ['sun', 'Regenwarnung für die Route', 'Zeigt, ob es unterwegs zu deiner Ankunftszeit regnet.'],
    ['up', 'Bestzeiten an Anstiegen', 'Velo misst jeden Anstieg und feiert deine Rekorde.'],
    ['road', 'Offline-Karten ohne Grenze', 'Beliebig viele und größere Gebiete (gratis: ein kleines Gebiet).'],
    ['flag', 'GPX-Export und -Import', 'Strecken mit anderen Apps und Geräten austauschen.'],
    ['sun', 'Mehr Farben und Pfeile', 'Abendrot, Lavendel, Kirschblüte, Mitternacht, Gold und weitere Navigationspfeile.']
  ];
  var PRICE = load2('price', 'year');
  function load2(k, d) { return V.load(k, d); }
  // Fenster ueber allem (waehrend der Navigation wird das Blatt laufend neu gezeichnet)
  function modal(html) {
    closeModal();
    var el = document.createElement('div'); el.id = 'vxm';
    el.innerHTML = '<div class="cbox mbox">' + html + '</div>';
    document.body.appendChild(el);
    el.onclick = function (e) { if (e.target === el) closeModal(); };
    return el;
  }
  function closeModal() { var m = $('vxm'); if (m) m.remove(); }
  function paywall(why) {
    if (isPlus()) { plusActive(); return; }
    closeModal(); var old = $('plusx'); if (old) old.remove();
    var el = document.createElement('div'); el.id = 'plusx';
    var stars = ''; for (var i = 0; i < 26; i++) stars += '<i style="left:' + Math.round(Math.random() * 100) + '%;top:' + Math.round(Math.random() * 100) + '%;animation-delay:' + (Math.random() * 3).toFixed(2) + 's;transform:scale(' + (0.4 + Math.random()).toFixed(2) + ')"></i>';
    el.innerHTML = '<div class="px-aurora"></div><div class="px-stars">' + stars + '</div>' +
      '<button class="px-close" id="pxClose" aria-label="Schließen"><svg width="22" height="22" viewBox="0 0 24 24" stroke="#fff" stroke-width="2.6" stroke-linecap="round"><path d="M6 6l12 12M18 6 6 18"/></svg></button>' +
      '<div class="px-scroll"><div class="px-hero"><div class="px-ring"></div>' + mascot('cheer', 150) + '</div>' +
      '<div class="px-logo">VELORA <span class="px-rb">PLUS</span></div>' +
      '<div class="px-sub">' + (why ? esc(why) + ' gehört zu Velora Plus.' : 'Mehr Juno, mehr Touren, mehr Abenteuer.') + '</div>' +
      '<div class="px-feats">' + FEATS.map(function (f, i) { return '<div class="px-card" style="animation-delay:' + (0.15 + i * 0.06).toFixed(2) + 's"><div class="px-ic">' + svg(f[0], '#ffffff', 20, 2.3) + '</div><div class="grow"><b>' + f[1] + '</b><span>' + f[2] + '</span></div></div>'; }).join('') + '</div>' +
      '<div class="px-price"><button data-price="month" class="' + (PRICE === 'month' ? 'on' : '') + '"><span class="px-pl">Monatlich</span><b>2,99 €</b><span>pro Monat</span></button>' +
      '<button data-price="year" class="' + (PRICE === 'year' ? 'on' : '') + '"><em>Spare 44 %</em><span class="px-pl">Jährlich</span><b>19,99 €</b><span>nur 1,67 € im Monat</span></button></div>' +
      '<button class="px-cta" id="plusGo"><span>Plus freischalten</span></button>' +
      '<div class="px-free">Kostenlos bleiben: Navigation weltweit, Rundtouren, Rückenwind-Touren, Routen speichern, Fahrten-Tagebuch mit Kalorien, Wochenziel und Rückblick, alle Abzeichen, Serie mit Velo, Live-Standort, Widget, ein Offline-Gebiet und 10 Juno-Credits am Tag.</div>' +
      '<div class="px-note">Testversion: Die Bezahlung über Google Play kommt mit der Veröffentlichung. Bis dahin wird Plus hier kostenlos zum Ausprobieren freigeschaltet.</div></div>';
    document.body.appendChild(el);
    hap('soft');
    function close() { el.classList.add('out'); setTimeout(function () { el.remove(); }, 300); }
    $('pxClose').onclick = close;
    el.querySelectorAll('[data-price]').forEach(function (b) { b.onclick = function () { PRICE = b.getAttribute('data-price'); V.store('price', PRICE); el.querySelectorAll('[data-price]').forEach(function (x) { x.classList.toggle('on', x === b); }); hap('click'); }; });
    $('plusGo').onclick = function () { setPlus(true); close(); celebrate({ mood: 'cheer', title: 'Willkommen bei Plus!', text: 'Alle Funktionen sind jetzt freigeschaltet. Viel Spaß beim Fahren!' }); if (V.state === 'menu') V.showMenu(); };
  }
  // Plus aktiv: schlicht
  function plusActive() {
    var m = modal('<div style="display:flex;align-items:center;gap:12px">' + mascot('thumbs', 70) + '<div class="grow"><h2>Velora Plus</h2><div class="sub">Plus ist aktiv. Danke für deine Unterstützung!</div></div></div>' +
      '<div class="pbox ok"><div class="sub">' + CR_PLUS + ' Juno-Credits am Tag, Reiseführer, Tagesplaner, Akkuplaner, Regenwarnung, Bestzeiten, GPX, Offline-Karten ohne Grenze, alle Farben und Pfeile.</div></div>' +
      '<div class="btns"><button class="go sec" id="plusOff">Plus beenden (Test)</button><button class="go" id="plusOk">Schließen</button></div>');
    $('plusOff').onclick = function () { setPlus(false); closeModal(); if (V.state === 'menu') V.showMenu(); };
    $('plusOk').onclick = closeModal;
  }
  // true = darf; sonst Paywall
  function gate(name) { if (isPlus()) return true; hap('tick'); paywall(name); return false; }
  VX.bikeLocked = function (k) { return V.BIKES[k] && V.BIKES[k].pro && !gate('Das Profil ' + V.BIKES[k].n); };

  // =====================================================================
  // Haptik: kurze, knackige Effekte (Android); im Browser-Tester als Anzeige oben
  // =====================================================================
  var HAPN = { tick: 'Tick', click: 'Klick', soft: 'Sanft', success: 'Erfolg', celebrate: 'Feier', left: 'Links (2x)', right: 'Rechts (3x)', warn: 'Warnung', turn: 'Abbiegen' };
  var PAT = { tick: [8], click: [18], soft: [10, 60, 8], success: [20, 50, 35], celebrate: [25, 60, 25, 60, 60], left: [35, 110, 35], right: [35, 110, 35, 110, 35], warn: [110, 80, 110, 80, 110], turn: [40, 100, 40] };
  var hvT = null;
  function hap(t) {
    var lv = S.hap == null ? 2 : S.hap;
    if (!lv) return true;
    if (t === 'turn') t = 'left';
    if (A && A.haptic) { try { A.haptic(t, lv); } catch (e) {} }
    else if (navigator.vibrate) { try { navigator.vibrate(PAT[t] || [15]); } catch (e) {} }
    if (TESTER) {
      var el = $('hapviz'); if (!el) { el = document.createElement('div'); el.id = 'hapviz'; document.body.appendChild(el); }
      el.textContent = 'Vibration: ' + (HAPN[t] || t) + (lv === 1 ? ' (leicht)' : '');
      el.classList.remove('on'); void el.offsetWidth; el.classList.add('on');
      clearTimeout(hvT); hvT = setTimeout(function () { el.classList.remove('on'); }, 900);
    }
    return true;
  }
  VX.haptic = hap;
  document.addEventListener('pointerdown', function (e) {
    var b = e.target.closest && e.target.closest('button,.chip,.row,.card,.sw,[data-act],.badge');
    if (!b) return;
    var strong = b.matches('.go:not(.sec),#start,#chatSend,.quick button,#plusGo,.price button');
    hap(strong ? 'click' : 'tick');
  }, { capture: true, passive: true });

  // =====================================================================
  // Maskottchen Velo, der Fuchs (fox.js) und seine Animationen
  // =====================================================================
  function mascot(mood, size) {
    var m = { idle: 'idle', wave: 'wave', cheer: 'cheer', sad: 'sad', ride: 'ride', think: 'think', thumbs: 'thumbs', sleep: 'sleep', trophy: 'trophy', torch: 'torch' }[mood] || 'idle';
    return '<div class="fxw fx-' + m + '" style="width:' + Math.round(size * 0.87) + 'px;height:' + size + 'px">' + (window.Fox ? Fox.svg(m) : '') + '</div>';
  }
  VX.mascot = mascot;
  var FLAME = '<svg width="22" height="22" viewBox="0 0 24 24"><path d="M12 2c1 4 6 6 6 12a6 6 0 0 1-12 0c0-3 2-5 3-6 0 2 1 3 2 3 0-4-1-6 1-9z" fill="#ff922b"/><path d="M12 12c1 2 3 3 3 5a3 3 0 0 1-6 0c0-1 1-2 1-3 1 1 2 0 2-2z" fill="#ffd43b"/></svg>';
  // grosse Flamme fuer die Serie, in drei Schichten (flackert ueber CSS)
  function bigFlame(cls) {
    return '<svg class="bflame ' + (cls || '') + '" viewBox="0 0 120 150"><defs><radialGradient id="bfG" cx="50%" cy="70%" r="60%"><stop offset="0" stop-color="#ffe066"/><stop offset=".55" stop-color="#ff922b"/><stop offset="1" stop-color="#f03e3e"/></radialGradient></defs>' +
      '<g class="bf-o"><path d="M60 6 C70 34 104 52 104 92 A44 44 0 0 1 16 92 C16 70 30 56 38 46 C38 60 44 70 54 72 C50 50 52 26 60 6 Z" fill="url(#bfG)"/></g>' +
      '<g class="bf-m"><path d="M60 52 C66 70 86 80 86 102 A26 26 0 0 1 34 102 C34 90 42 82 46 76 C47 86 52 90 58 90 C55 78 56 64 60 52 Z" fill="#ffd43b"/></g>' +
      '<g class="bf-i"><path d="M60 88 C64 98 74 104 74 114 A14 14 0 0 1 46 114 C46 106 52 102 54 98 C55 104 58 106 61 106 C59 100 59 94 60 88 Z" fill="#fff9db"/></g></svg>';
  }

  // ---------- Feiern: Warteschlange, damit mehrere Animationen nacheinander kommen ----------
  var celeQ = [], celeOpen = false;
  function celebrate(o) { celeQ.push(o); if (celeOpen && !$('cele')) celeOpen = false; if (!celeOpen) nextCele(); }
  function confetti(el, n) {
    var cols = ['#2fd59a', '#5aa9ff', '#ffa94d', '#f06595', '#ffd43b', '#b197fc'];
    for (var i = 0; i < (n || 70); i++) {
      var c = document.createElement('i'); c.className = 'conf';
      c.style.left = Math.random() * 100 + '%'; c.style.background = cols[i % cols.length];
      c.style.setProperty('--dx', (Math.random() * 160 - 80) + 'px'); c.style.setProperty('--rot', (Math.random() * 900 - 450) + 'deg');
      c.style.animationDuration = (1.8 + Math.random() * 1.8) + 's'; c.style.animationDelay = (Math.random() * 0.6) + 's';
      if (Math.random() < 0.3) c.style.borderRadius = '50%';
      el.appendChild(c);
    }
  }
  function countUp(el, to, ms, fmt) {
    var t0 = performance.now();
    (function step(t) { var k = Math.min(1, (t - t0) / ms), e = 1 - Math.pow(1 - k, 3); el.textContent = fmt(to * e); if (k < 1) requestAnimationFrame(step); })(t0);
  }
  function nextCele() {
    var o = celeQ.shift(); if (!o) { celeOpen = false; return; }
    celeOpen = true;
    var el = document.createElement('div'); el.id = 'cele';
    var body = o.kind === 'streak' ? streakHtml(o) : o.kind === 'ride' ? rideHtml(o) : o.kind === 'badge' ? badgeHtml(o) : genericHtml(o);
    el.innerHTML = '<div class="cbox c-' + (o.kind || 'gen') + '">' + body + '<button class="go" id="celeOk">' + esc(o.btn || 'Weiter') + '</button></div>';
    document.body.appendChild(el);
    if (!o.kind) { confetti(el, 70); hap('celebrate'); anim(el.querySelector('.cbox .fxw'), [{ transform: 'translateY(120px) scale(.5)' }, { transform: 'translateY(-20px) scale(1.1)', offset: .6 }, { transform: 'none' }], { duration: 650, easing: 'cubic-bezier(.3,1.3,.5,1)' }); }
    if (o.say && S.voice) setTimeout(function () { V.say(o.say); }, o.kind === 'streak' ? 1600 : 300);
    if (o.kind === 'streak') runStreak(el, o);
    if (o.kind === 'ride') runRide(el, o);
    if (o.kind === 'badge') runBadge(el, o);
    function close() { el.style.transition = 'opacity .25s'; el.style.opacity = 0; setTimeout(function () { el.remove(); nextCele(); }, 250); }
    $('celeOk').onclick = close;
    el.onclick = function (e) { if (e.target === el) close(); };
  }
  VX.celebrate = celebrate;
  function genericHtml(o) {
    return mascot(o.mood || 'cheer', 150) + (o.big != null ? '<div class="big acc">' + o.big + '</div>' : '') + '<h2>' + esc(o.title) + '</h2><div class="sub">' + esc(o.text || '') + '</div>';
  }
  // Hilfen fuer die Szenen
  var TS = function () { return window.__ts || 1; }; // Zeitlupe nur fuer Tests
  function anim(el, kf, o) { if (!el || !el.animate) return; o = Object.assign({ fill: 'forwards' }, o); o.duration *= TS(); if (o.delay) o.delay *= TS(); return el.animate(kf, o); }
  function at(ms, f) { return setTimeout(f, ms * TS()); }
  function sparkBurst(el, n, cols, dist) {
    if (!el) return;
    for (var i = 0; i < n; i++) {
      var b = document.createElement('b'); b.className = 'spk2'; el.appendChild(b);
      var a = Math.random() * Math.PI * 2, d = (dist || 120) * (0.5 + Math.random() * 0.7), sz = 4 + Math.random() * 7;
      b.style.width = b.style.height = sz + 'px'; b.style.background = cols[i % cols.length];
      anim(b, [{ transform: 'translate(-50%,-50%) scale(1)', opacity: 1 }, { transform: 'translate(calc(-50% + ' + Math.round(Math.cos(a) * d) + 'px),calc(-50% + ' + Math.round(Math.sin(a) * d - 30) + 'px)) scale(.2)', opacity: 0 }], { duration: 600 + Math.random() * 500, easing: 'cubic-bezier(.1,.8,.3,1)' });
    }
  }
  function shake(el, f) { anim(el, [{ transform: 'translate(0,0)' }, { transform: 'translate(-' + f + 'px,' + f / 2 + 'px)' }, { transform: 'translate(' + f + 'px,-' + f / 2 + 'px)' }, { transform: 'translate(-' + f / 2 + 'px,' + f / 3 + 'px)' }, { transform: 'translate(' + f / 3 + 'px,0)' }, { transform: 'translate(0,0)' }], { duration: 420, easing: 'ease-out', fill: 'none' }); }
  var LOGS = '<svg class="slogs" viewBox="0 0 120 50"><path d="M14 38 L104 18" stroke="#7a4a2a" stroke-width="13" stroke-linecap="round"/><path d="M16 18 L106 40" stroke="#8b5a35" stroke-width="13" stroke-linecap="round"/><circle cx="104" cy="18" r="6" fill="#c08a5a"/><circle cx="106" cy="40" r="6" fill="#c08a5a"/><g class="ember"><ellipse cx="60" cy="27" rx="14" ry="6" fill="#868e96"/><ellipse cx="60" cy="25" rx="6" ry="3" fill="#adb5bd"/></g></svg>';

  // Serie: Velo rennt mit der Fackel herein, holt aus und zuendet die Flamme mit voller Wucht
  function streakHtml(o) {
    var names = ['Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa', 'So'], ws = weekStart(Date.now()), today = (new Date().getDay() + 6) % 7;
    var days = names.map(function (n, i) {
      var on = rodeOn(ws + i * 864e5), isT = i === today;
      return '<div class="sd' + (on && !isT ? ' on' : '') + (isT ? ' today' : '') + '"><i>' + (on || isT ? FLAME : '') + '</i><span>' + n + '</span></div>';
    }).join('');
    return '<div class="sscene"><div class="sglow"></div>' + LOGS + '<div class="sflame">' + bigFlame() + '</div><div class="sring"></div><div class="sring r2"></div><div class="sbits"></div>' +
      '<div class="sfox">' + mascot('torch', 140) + '</div><div class="sflash"></div></div>' +
      '<div class="snum"><span class="old">' + o.from + '</span><span class="new">' + o.to + '</span></div>' +
      '<div class="slate"><h2>' + (o.to === 1 ? 'Serie gestartet!' : o.to + ' Tage in Folge!') + '</h2><div class="sub">' + esc(o.text) + '</div><div class="sweek">' + days + '</div></div>';
  }
  function runStreak(el) {
    var box = el.querySelector('.cbox'), sc = el.querySelector('.sscene'), fw = sc.querySelector('.sfox'), fx = fw.querySelector('.fxw');
    var flame = sc.querySelector('.sflame'), ok = el.querySelector('#celeOk');
    ok.style.opacity = 0; ok.style.pointerEvents = 'none';
    fx.classList.add('st-run');
    anim(fw, [{ transform: 'translateX(-260px)' }, { transform: 'translateX(0)' }], { duration: 650, easing: 'cubic-bezier(.2,.7,.3,1)' });
    at(150, function () { hap('tick'); }); at(400, function () { hap('tick'); });
    at(660, function () { fx.classList.remove('st-run'); anim(fx, [{ transform: 'scale(1.08,.9)' }, { transform: 'scale(1)' }], { duration: 260, easing: 'cubic-bezier(.3,1.6,.5,1)', fill: 'none' }); });
    at(760, function () { fx.classList.add('st-raise'); hap('soft'); });
    at(1080, function () { fx.classList.add('st-wind'); });
    at(1420, function () {
      fx.classList.remove('st-wind'); fx.classList.add('st-strike');
      anim(fw, [{ transform: 'translateX(0)' }, { transform: 'translateX(58px)' }], { duration: 110, easing: 'cubic-bezier(.5,0,1,.5)' });
    });
    at(1530, function () { // Aufprall
      hap('warn'); box.classList.add('lit');
      anim(sc.querySelector('.sflash'), [{ opacity: 0 }, { opacity: .95, offset: .15 }, { opacity: 0 }], { duration: 420, easing: 'ease-out' });
      shake(box, 12);
      anim(flame, [{ transform: 'translateX(-50%) scale(0)', opacity: 0 }, { transform: 'translateX(-50%) scale(1.45)', opacity: 1, offset: .45 }, { transform: 'translateX(-50%) scale(.9)', offset: .7 }, { transform: 'translateX(-50%) scale(1)', opacity: 1 }], { duration: 750, easing: 'cubic-bezier(.2,.9,.3,1)' });
      sc.querySelectorAll('.sring').forEach(function (r, i) { anim(r, [{ transform: 'translate(-50%,-50%) scale(.1)', opacity: .9 }, { transform: 'translate(-50%,-50%) scale(' + (i ? 3.4 : 2.4) + ')', opacity: 0 }], { duration: 650 + i * 200, delay: i * 70, easing: 'cubic-bezier(.1,.7,.3,1)' }); });
      sparkBurst(sc.querySelector('.sbits'), 36, ['#ffd43b', '#ff922b', '#fff3bf', '#ff6b6b'], 150);
      anim(sc.querySelector('.ember'), [{ opacity: 1 }, { opacity: 0 }], { duration: 200 });
      anim(fw, [{ transform: 'translateX(58px)' }, { transform: 'translateX(0)' }], { duration: 420, easing: 'cubic-bezier(.2,.8,.3,1)' });
    });
    at(1950, function () { // Zahl knallt herein
      box.classList.add('flip'); hap('click');
      anim(el.querySelector('.snum .new'), [{ transform: 'scale(3.2)', opacity: 0 }, { transform: 'scale(.85)', opacity: 1, offset: .6 }, { transform: 'scale(1)', opacity: 1 }], { duration: 480, easing: 'cubic-bezier(.3,1.4,.5,1)' });
      at(170, function () { shake(box, 6); });
    });
    at(2250, function () { fw.innerHTML = mascot('cheer', 140); anim(el.querySelector('.slate'), [{ opacity: 0, transform: 'translateY(16px)' }, { opacity: 1, transform: 'none' }], { duration: 420, easing: 'cubic-bezier(.2,.8,.2,1)' }); hap('celebrate'); confetti(el, 60); });
    at(2700, function () { box.classList.add('day'); var t = el.querySelector('.sd.today i'); if (t) sparkBurst(t, 12, ['#ffd43b', '#ff922b'], 40); hap('success'); });
    at(3100, function () { ok.style.transition = 'opacity .3s'; ok.style.opacity = 1; ok.style.pointerEvents = ''; });
  }
  // Fahrt beendet: Velo rast herein, bremst mit Staubwolke, springt ab und zeigt den Daumen
  function rideHtml(o) {
    return '<div class="rscene"><div class="rroad"></div><div class="rdust"></div><div class="rfox">' + mascot('ride', 130) + '</div></div><div class="rlate"><h2>' + esc(o.title) + '</h2>' +
      '<div class="rstats"><div><b data-cu="' + o.km + '" data-f="km">0</b><span>km</span></div><div><b data-cu="' + o.min + '" data-f="int">0</b><span>Minuten</span></div><div><b data-cu="' + o.asc + '" data-f="int">0</b><span>Höhenmeter</span></div><div><b data-cu="' + o.kcal + '" data-f="int">0</b><span>kcal</span></div></div>' +
      '<div class="sub">' + esc(o.text || '') + '</div></div>';
  }
  function runRide(el) {
    var fw = el.querySelector('.rfox'), dust = el.querySelector('.rdust');
    anim(fw, [{ transform: 'translateX(-340px)' }, { transform: 'translateX(12px)', offset: .82 }, { transform: 'translateX(0)' }], { duration: 900, easing: 'cubic-bezier(.15,.8,.3,1)' });
    at(620, function () { // Bremsen
      hap('warn'); anim(fw.firstChild, [{ transform: 'rotate(0)' }, { transform: 'rotate(-9deg)' }, { transform: 'rotate(2deg)' }, { transform: 'rotate(0)' }], { duration: 500, easing: 'ease-out', fill: 'none' });
      sparkBurst(dust, 16, ['#ced4da', '#adb5bd', '#e9ecef'], 70);
    });
    at(1150, function () { // absteigen, Daumen hoch
      fw.innerHTML = mascot('thumbs', 130);
      anim(fw.firstChild, [{ transform: 'translateY(0) scale(1,1)' }, { transform: 'translateY(-38px) scale(.95,1.05)', offset: .45 }, { transform: 'translateY(0) scale(1.08,.92)', offset: .8 }, { transform: 'none' }], { duration: 520, easing: 'ease-out', fill: 'none' });
      hap('success');
    });
    at(1300, function () { anim(el.querySelector('.rlate'), [{ opacity: 0, transform: 'translateY(16px)' }, { opacity: 1, transform: 'none' }], { duration: 400, easing: 'cubic-bezier(.2,.8,.2,1)' }); });
    el.querySelectorAll('[data-cu]').forEach(function (b, i) {
      var to = +b.getAttribute('data-cu'), f = b.getAttribute('data-f') === 'km' ? function (v) { return num(v.toFixed(1)); } : function (v) { return String(Math.round(v)); };
      at(1450 + i * 220, function () { countUp(b, to, 800, f); anim(b, [{ transform: 'scale(1.5)' }, { transform: 'scale(1)' }], { duration: 400, easing: 'cubic-bezier(.3,1.6,.5,1)', fill: 'none' }); hap('tick'); });
    });
    at(2500, function () { confetti(el, 40); hap('celebrate'); });
  }
  // Abzeichen: faellt wirbelnd vom Himmel, Velo faengt es, Lichtstrahlen
  function badgeHtml(o) {
    return '<div class="bscene"><div class="rays"></div><div class="bdrop">' + badgeSvg(o.fam, o.tier, false, true) + '</div><div class="sbits"></div><div class="bfox2">' + mascot('cheer', 92) + '</div></div>' +
      '<div class="blate"><div class="btier" style="color:' + TIERS[o.tier - 1][2] + '">' + TIERS[o.tier - 1][0] + '</div><h2>' + esc(o.fam[1]) + '</h2><div class="sub">' + esc(o.text) + '</div></div>';
  }
  function runBadge(el) {
    var box = el.querySelector('.cbox'), d = el.querySelector('.bdrop'), rays = el.querySelector('.rays');
    rays.style.opacity = 0;
    anim(d, [{ transform: 'translate(-50%,-260px) rotateY(0deg) scale(.6)' }, { transform: 'translate(-50%,0) rotateY(1080deg) scale(1.15)', offset: .78 }, { transform: 'translate(-50%,0) rotateY(1080deg) scale(1)' }], { duration: 950, easing: 'cubic-bezier(.4,0,.2,1)' });
    at(740, function () {
      hap('warn'); shake(box, 9);
      anim(rays, [{ opacity: 0, transform: 'scale(.3) rotate(0)' }, { opacity: 1, transform: 'scale(1) rotate(30deg)' }], { duration: 500, easing: 'ease-out' });
      sparkBurst(el.querySelector('.bscene .sbits'), 30, ['#ffd43b', '#fff', '#74c0fc', '#ff8787'], 130);
      anim(el.querySelector('.bfox2'), [{ transform: 'translateX(-50%) scale(1.15,.82)' }, { transform: 'translateX(-50%) scale(1)' }], { duration: 380, easing: 'cubic-bezier(.3,1.6,.5,1)', fill: 'none' });
    });
    at(950, function () { anim(el.querySelector('.blate'), [{ opacity: 0, transform: 'translateY(14px)' }, { opacity: 1, transform: 'none' }], { duration: 400, easing: 'cubic-bezier(.2,.8,.2,1)' }); confetti(el, 50); hap('celebrate'); });
  }

  // =====================================================================
  // Fahrten-Tagebuch, Serie, Wochenziel (kostenlos)
  // =====================================================================
  var LOG = V.load('log', null);
  if (!LOG) { LOG = V.load('rides', []).map(function (r) { return { t: r.t, d: r.dist, mv: r.mv, asc: r.asc, max: r.max || 0 }; }).reverse(); V.store('log', LOG); }
  function kcal(r) {
    var h = r.mv / 3600, v = r.mv > 0 ? r.d / r.mv * 3.6 : 0;
    var met = v < 16 ? 4 : v < 19 ? 6.8 : v < 22 ? 8 : 10;
    if (S.bike === 'ebike') met *= 0.6;
    return Math.round(met * S.weight * h + r.asc * S.weight * 9.81 / 4184 / 0.24 * 0.3);
  }
  function rodeOn(t0) { return LOG.some(function (r) { return r.d >= 1000 && r.t >= t0 && r.t < t0 + 864e5; }); }
  function streak() {
    var days = {}; LOG.forEach(function (r) { if (r.d >= 1000) days[dayKey(r.t)] = 1; });
    var now = Date.now(), cur = 0, t = dayStart(now);
    if (!days[dayKey(t)]) t -= 864e5; // heute noch nicht gefahren: Serie bis gestern zaehlt
    while (days[dayKey(t)]) { cur++; t -= 864e5; }
    var best = 0, run = 0, keys = Object.keys(days).sort(), prev = null;
    keys.forEach(function (k) { var d = new Date(k + 'T12:00:00').getTime(); run = prev && Math.round((d - prev) / 864e5) === 1 ? run + 1 : 1; prev = d; if (run > best) best = run; });
    var last = keys.length ? new Date(keys[keys.length - 1] + 'T12:00:00').getTime() : 0;
    return { cur: cur, best: best, today: !!days[dayKey(now)], lost: !cur && best > 1 && now - last < 7 * 864e5 };
  }
  function sumSince(t0, t1) {
    var o = { d: 0, mv: 0, asc: 0, kcal: 0, n: 0, maxD: 0 };
    LOG.forEach(function (r) { if (r.t >= t0 && (!t1 || r.t < t1)) { o.d += r.d; o.mv += r.mv; o.asc += r.asc; o.kcal += kcal(r); o.n++; if (r.d > o.maxD) o.maxD = r.d; } });
    return o;
  }
  function weekDays(ws) { var out = []; for (var i = 0; i < 7; i++) { var a = ws + i * 864e5; out.push(sumSince(a, a + 864e5).d); } return out; }

  // =====================================================================
  // Abzeichen: Familien mit bis zu 10 Stufen, damit sie ueber Jahre reichen
  // =====================================================================
  var TIERS = [['Bronze', '#f3b680', '#b4652d'], ['Silber', '#f1f4f7', '#8796a5'], ['Gold', '#ffe27a', '#d39b00'], ['Platin', '#dff5ff', '#5aa3c4'], ['Smaragd', '#8af0bd', '#0f9f63'],
    ['Saphir', '#9cc8ff', '#1f5fd1'], ['Rubin', '#ff9eac', '#c8102e'], ['Amethyst', '#dcbcff', '#7a3fd1'], ['Diamant', '#ffffff', '#47b8e6'], ['Legende', '#ffe08a', '#ff4fa0']];
  // [id, Name, Symbol, Stufen, Wert(c), Text(x)]
  var FAM = [
    ['km', 'Kilometerfresser', 'road', [10, 50, 100, 250, 500, 1000, 2500, 5000, 10000, 25000], function (c) { return c.tot / 1000; }, function (x) { return x + ' km insgesamt'; }],
    ['hm', 'Bergziege', 'up', [500, 1000, 2500, 5000, 8849, 15000, 25000, 50000, 100000], function (c) { return c.asc; }, function (x) { return (x === 8849 ? '8849 m (einmal Mount Everest)' : x + ' Höhenmeter') + ' insgesamt'; }],
    ['rides', 'Stammgast', 'bike', [1, 10, 25, 50, 100, 250, 500, 1000], function (c) { return c.n; }, function (x) { return x === 1 ? 'Erste Fahrt' : x + ' Fahrten'; }],
    ['streak', 'Feuer und Flamme', 'sun', [3, 7, 14, 30, 60, 100, 200, 365], function (c) { return c.best; }, function (x) { return x + ' Tage in Folge gefahren'; }],
    ['long', 'Langstrecke', 'flag', [25, 50, 75, 100, 150, 200], function (c) { return c.maxD / 1000; }, function (x) { return x + ' km an einem Tag'; }],
    ['climbday', 'Gipfelstürmer', 'up', [250, 500, 1000, 1500, 2000, 3000], function (c) { return c.maxAsc; }, function (x) { return x + ' Höhenmeter an einem Tag'; }],
    ['top', 'Höhenluft', 'peak', [500, 1000, 1500, 2000, 2500], function (c) { return c.top; }, function (x) { return 'Auf ' + x + ' m über dem Meer geradelt'; }],
    ['explore', 'Entdecker', 'pin', [3, 10, 25, 50, 100, 250], function (c) { return c.cells; }, function (x) { return 'In ' + x + ' verschiedenen Gegenden gefahren'; }],
    ['far', 'Weltenbummler', 'loop', [1, 3, 5, 10, 20], function (c) { return c.far; }, function (x) { return x + (x === 1 ? ' Fahrt' : ' Fahrten') + ' über 300 km von zu Hause'; }],
    ['tours', 'Ziel erreicht', 'flag', [1, 5, 10, 25, 50, 100, 250], function (c) { return c.arr; }, function (x) { return x + (x === 1 ? ' Navigation' : ' Navigationen') + ' bis ans Ziel'; }],
    ['goal', 'Wochenheld', 'list', [1, 4, 12, 26, 52, 104], function (c) { return c.goals; }, function (x) { return x + (x === 1 ? ' Woche' : ' Wochen') + ' mit erreichtem Wochenziel'; }],
    ['months', 'Treue', 'sun', [1, 3, 6, 12, 24, 36, 60], function (c) { return c.months; }, function (x) { return x + (x === 1 ? ' Monat' : ' Monate') + ' mit mindestens einer Fahrt'; }],
    ['seasons', 'Bei jedem Wetter', 'wind', [4, 8, 12, 20], function (c) { return c.seasons; }, function (x) { return x + ' Jahreszeiten mit Fahrten'; }],
    ['early', 'Frühaufsteher', 'sun', [1, 5, 20, 50], function (c) { return c.early; }, function (x) { return x + (x === 1 ? ' Fahrt' : ' Fahrten') + ' vor 7 Uhr'; }],
    ['night', 'Nachteule', 'mute', [1, 5, 20, 50], function (c) { return c.night; }, function (x) { return x + (x === 1 ? ' Fahrt' : ' Fahrten') + ' nach 21 Uhr'; }],
    ['weekend', 'Wochenendkrieger', 'bike', [5, 25, 100, 250], function (c) { return c.wkend; }, function (x) { return x + ' Fahrten am Wochenende'; }],
    ['speed', 'Rakete', 'wind', [30, 40, 50, 60], function (c) { return c.vmax; }, function (x) { return 'Höchstgeschwindigkeit ' + x + ' km/h'; }],
    ['juno', 'Juno-Freund', 'juno', [1, 10, 50, 200, 500], function (c) { return c.juno; }, function (x) { return x + (x === 1 ? ' Frage' : ' Fragen') + ' an Juno'; }]
  ];
  var GOT = V.load('tiers', {});
  function cellOf(r) { return r.c || null; }
  function badgeCtx() {
    var c = { n: 0, tot: 0, maxD: 0, maxAsc: 0, asc: 0, top: 0, best: streak().best, cells: 0, far: 0, arr: 0, goals: 0, months: 0, seasons: 0, early: 0, night: 0, wkend: 0, vmax: 0, juno: V.load('junoTotal', 0) };
    var cells = {}, months = {}, seas = {}, day = {}, weeks = {}, home = V.load('homeCell', null);
    LOG.forEach(function (r) {
      if (r.d < 300) return;
      var d = new Date(r.t), h = d.getHours(), wd = d.getDay();
      c.n++; c.tot += r.d; c.asc += r.asc; if (r.d > c.maxD) c.maxD = r.d; if ((r.top || 0) > c.top) c.top = r.top; if ((r.max || 0) < 90 && (r.max || 0) > c.vmax) c.vmax = r.max;
      var dk = dayKey(r.t); day[dk] = (day[dk] || 0) + r.asc;
      if (r.c) { cells[r.c] = 1; if (home && dist2(r.c, home) > 300000) c.far++; }
      if (r.arr) c.arr++;
      if (h >= 3 && h < 7) c.early++; if (h >= 21) c.night++; if (wd === 0 || wd === 6) c.wkend++;
      months[d.getFullYear() + '-' + d.getMonth()] = 1;
      seas[(d.getMonth() === 11 ? d.getFullYear() + 1 : d.getFullYear()) + '-' + Math.floor(((d.getMonth() + 1) % 12) / 3)] = 1;
      var wk = weekStart(r.t); weeks[wk] = (weeks[wk] || 0) + r.d;
    });
    for (var k in day) if (day[k] > c.maxAsc) c.maxAsc = day[k];
    c.cells = Object.keys(cells).length; c.months = Object.keys(months).length; c.seasons = Object.keys(seas).length;
    for (var w in weeks) if (weeks[w] >= S.goalKm * 1000) c.goals++;
    return c;
  }
  function dist2(a, b) { var p = a.split(','), q = b.split(','); return V.dist(+p[0], +p[1], +q[0], +q[1]); }
  function tierOf(f, c) { var v = f[4](c), t = 0; f[3].forEach(function (x, i) { if (v >= x) t = i + 1; }); return { t: t, v: v }; }
  function checkBadges(show) {
    var c = badgeCtx(), fresh = [];
    FAM.forEach(function (f) { var t = tierOf(f, c).t; if (t > (GOT[f[0]] || 0)) { GOT[f[0]] = t; fresh.push([f, t]); } });
    if (fresh.length) V.store('tiers', GOT);
    if (show) fresh.forEach(function (x) { celebrate({ kind: 'badge', fam: x[0], tier: x[1], text: x[0][5](x[0][3][x[1] - 1]) + '. Stufe ' + x[1] + ' von ' + x[0][3].length + '.', btn: 'Stark' }); });
  }
  // Abzeichen-Design: Sechseck mit Edelmetall-Rand, Sternen fuer die Stufe und Band
  var bId = 0;
  function badgeSvg(f, tier, locked, big) {
    var id = 'bd' + (++bId), T = TIERS[Math.max(0, tier - 1)], c1 = locked ? '#e9eef3' : T[1], c2 = locked ? '#aeb8c2' : T[2];
    var ic = svg(f[2], locked ? '#c3ccd5' : '#ffffff', 24, 2.6).replace(/^<svg[^>]*>/, '').replace(/<\/svg>$/, '');
    var stars = '', ns = locked ? 0 : Math.min(5, tier), sx = 50 - (ns - 1) * 6;
    for (var i = 0; i < ns; i++) stars += '<path transform="translate(' + (sx + i * 12) + ' 9) scale(.42)" d="M0 -10 L2.9 -3.1 10 -3.1 4.3 1.2 6.5 8.1 0 4 -6.5 8.1 -4.3 1.2 -10 -3.1 -2.9 -3.1Z" fill="#fff" stroke="' + c2 + '" stroke-width="2"/>';
    return '<svg class="bdg' + (big ? ' big' : '') + (locked ? ' lock' : '') + '" viewBox="0 0 100 112"><defs>' +
      '<linearGradient id="' + id + 'r" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="' + c1 + '"/><stop offset=".5" stop-color="' + c2 + '"/><stop offset="1" stop-color="' + c1 + '"/></linearGradient>' +
      '<radialGradient id="' + id + 'f" cx="40%" cy="30%" r="80%"><stop offset="0" stop-color="' + c1 + '"/><stop offset="1" stop-color="' + c2 + '"/></radialGradient>' +
      (tier === 10 && !locked ? '<linearGradient id="' + id + 'l" x1="0" x2="1"><stop offset="0" stop-color="#ff6b6b"/><stop offset=".25" stop-color="#ffd43b"/><stop offset=".5" stop-color="#51cf66"/><stop offset=".75" stop-color="#4dabf7"/><stop offset="1" stop-color="#cc5de8"/></linearGradient>' : '') +
      '<clipPath id="' + id + 'c"><path d="M50 14 L83 33 V73 L50 94 L17 73 V33 Z"/></clipPath></defs>' +
      '<path d="M50 3 L93 27 V79 L50 105 L7 79 V27 Z" fill="url(#' + id + (tier === 10 && !locked ? 'l' : 'r') + ')" stroke="rgba(13,43,62,.35)" stroke-width="1.5"/>' +
      '<path d="M50 14 L83 33 V73 L50 94 L17 73 V33 Z" fill="url(#' + id + 'f)"/>' +
      '<g clip-path="url(#' + id + 'c)"><path d="M50 14 L83 33 V45 C70 38 30 38 17 45 V33 Z" fill="#fff" opacity=".22"/>' + (big ? '<rect class="shine" x="-40" y="0" width="22" height="120" fill="#fff" opacity=".55" transform="skewX(-20)"/>' : '') + '</g>' +
      '<circle cx="50" cy="54" r="21" fill="rgba(0,0,0,.14)"/><g transform="translate(34 38) scale(1.33)" fill="none" stroke="' + (locked ? '#c3ccd5' : '#ffffff') + '" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round">' + ic + '</g>' +
      (locked ? '<g transform="translate(66 72)"><circle r="10" fill="#8796a5"/><rect x="-5" y="-2" width="10" height="8" rx="1.5" fill="#fff"/><path d="M-3 -2 v-3 a3 3 0 0 1 6 0 v3" stroke="#fff" stroke-width="2" fill="none"/></g>' : '') +
      stars + '<path d="M22 92 H78 L74 100 L78 108 H22 L26 100 Z" fill="' + c2 + '" stroke="rgba(13,43,62,.3)" stroke-width="1"/>' +
      '<text x="50" y="103.5" text-anchor="middle" font-family="Inter,sans-serif" font-size="8.5" font-weight="800" fill="#fff" letter-spacing=".6" textLength="40" lengthAdjust="spacingAndGlyphs" style="letter-spacing:.6px">' + (locked ? 'STUFE 1' : 'STUFE ' + tier) + '</text></svg>';
  }
  function badgeGrid(c) {
    return '<div class="badges">' + FAM.map(function (f, i) {
      var t = tierOf(f, c), nx = f[3][t.t], prev = t.t ? f[3][t.t - 1] : 0, pc = nx ? Math.max(0, Math.min(100, (t.v - prev) / (nx - prev) * 100)) : 100;
      return '<div class="badge' + (t.t ? ' got' : '') + '" data-fam="' + i + '">' + badgeSvg(f, Math.max(1, t.t), !t.t) + '<b>' + esc(f[1]) + '</b><div class="bbar"><i style="width:' + pc + '%"></i></div></div>';
    }).join('') + '</div>';
  }
  function showFamily(i) {
    var f = FAM[i], c = badgeCtx(), t = tierOf(f, c), nx = f[3][t.t];
    var m = modal('<div style="text-align:center">' + badgeSvg(f, Math.max(1, t.t), !t.t, true) + '<h2>' + esc(f[1]) + '</h2><div class="sub">' + (t.t ? 'Stufe ' + t.t + ' von ' + f[3].length + ' (' + TIERS[t.t - 1][0] + ')' : 'Noch nicht freigeschaltet') + '</div>' +
      (nx ? '<div class="pbox"><div class="t">Nächste Stufe</div><div class="sub">' + esc(f[5](nx)) + '</div><div class="meter"><i style="width:' + Math.min(100, t.v / nx * 100) + '%;background:linear-gradient(90deg,var(--lime),var(--acc))"></i></div><div class="sub">Aktuell: ' + num(Math.round(t.v * 10) / 10) + '</div></div>' : '<div class="pbox ok"><div class="sub">Alle Stufen geschafft. Legende!</div></div>') +
      '<div class="tiers">' + f[3].map(function (x, k) { return '<div class="' + (k < t.t ? 'got' : '') + '">' + badgeSvg(f, k + 1, k >= t.t) + '<span>' + esc(f[5](x)) + '</span></div>'; }).join('') + '</div>' +
      '<div class="btns"><button class="go sec" id="famClose">Schließen</button></div></div>');
    $('famClose').onclick = closeModal;
  }

  // ---------- Bestzeiten an Anstiegen (Plus) ----------
  var BEST = V.load('bests', {});
  function climbsOf(ride) {
    var trk = ride.trk, tt = ride.tt, out = [];
    if (!trk || !tt || tt.length !== trk.length || trk.length < 5) return out;
    var ele = ride.ee && ride.ee.length === trk.length ? ride.ee.slice() : trk.map(function (p) { return V.onIsland(p[0], p[1]) ? V.demEle(p[0], p[1]) : null; });
    var lo = 0, hi = 0;
    function flush() {
      if (ele[hi] != null && ele[lo] != null && ele[hi] - ele[lo] >= 30 && hi > lo) {
        var len = 0; for (var j = lo + 1; j <= hi; j++) len += V.dist(trk[j - 1][0], trk[j - 1][1], trk[j][0], trk[j][1]);
        var t = tt[hi] - tt[lo];
        if (len >= 300 && t > 20) out.push({ a: lo, b: hi, gain: ele[hi] - ele[lo], len: len, t: t });
      }
    }
    for (var i = 1; i < trk.length; i++) {
      if (ele[i] == null) { flush(); lo = hi = i; continue; }
      if (ele[lo] == null) { lo = hi = i; continue; }
      if (ele[i] > ele[hi]) hi = i;
      if (ele[i] < ele[hi] - 12 || ele[i] < ele[lo]) { flush(); lo = hi = i; }
    }
    flush();
    return out.map(function (c) {
      var A0 = trk[c.a], B0 = trk[c.b], g = function (v) { return Math.round(v * 400) / 400; };
      c.key = g(A0[0]) + ',' + g(A0[1]) + '>' + g(B0[0]) + ',' + g(B0[1]);
      c.name = 'Anstieg +' + Math.round(c.gain) + ' m (' + V.fmtDist(c.len) + ')';
      return c;
    });
  }
  function fmtMin(s) { s = Math.round(s); return Math.floor(s / 60) + ':' + ('0' + s % 60).slice(-2) + ' min'; }
  function checkBests(ride) {
    climbsOf(ride).forEach(function (c) {
      var b = BEST[c.key];
      if (!b || c.t < b.t) {
        var old = b ? b.t : null;
        BEST[c.key] = { name: (b && b.name) || c.name, t: c.t, gain: Math.round(c.gain), len: Math.round(c.len), when: ride.t, prev: old };
        if (old && isPlus()) celebrate({ mood: 'cheer', big: fmtMin(c.t).replace(' min', ''), title: 'Neuer Rekord!', text: c.name + ': ' + Math.round(old - c.t) + ' Sekunden schneller als deine alte Bestzeit.', say: 'Neuer Rekord am Anstieg' });
      }
    });
    V.store('bests', BEST);
  }

  // ---------- Fahrt gespeichert: Tagebuch, Serie, Abzeichen, Rekorde ----------
  var arrivedFlag = false;
  VX.rideSaved = function (ride) {
    var before = streak();
    var c0 = ride.trk && ride.trk[0] ? ride.trk[0][0].toFixed(1) + ',' + ride.trk[0][1].toFixed(1) : null;
    if (c0 && !V.load('homeCell', null)) V.store('homeCell', c0);
    LOG.push({ t: ride.t, d: ride.dist, mv: ride.mv, asc: ride.asc, max: ride.max || 0, top: ride.top != null ? Math.round(ride.top) : null, c: c0, arr: arrivedFlag ? 1 : 0 });
    arrivedFlag = false;
    V.store('log', LOG);
    var after = streak();
    if (ride.dist >= 1000) {
      var first = LOG.filter(function (r) { return r.d >= 1000; }).length === 1;
      celebrate({ kind: 'ride', title: first ? 'Deine erste Fahrt!' : 'Fahrt geschafft!', km: Math.round(ride.dist / 100) / 10, min: Math.round(ride.mv / 60), asc: Math.round(ride.asc), kcal: kcal({ d: ride.dist, mv: ride.mv, asc: ride.asc }), text: first ? 'Ich bin Velo und feiere ab jetzt jede Fahrt mit dir.' : 'Gut gemacht!' });
      if (!before.today) celebrate({ kind: 'streak', from: Math.max(0, after.cur - 1), to: after.cur, text: after.cur === 1 ? 'Fahr morgen wieder, dann brennt die Flamme weiter.' : after.cur >= after.best ? 'Deine längste Serie bisher. Morgen wieder fahren!' : 'Deine Bestmarke sind ' + after.best + ' Tage. Bleib dran!', say: after.cur > 1 ? after.cur + ' Tage in Folge!' : 'Serie gestartet!' });
    }
    checkBests(ride);
    checkBadges(true);
    updWidget();
  };
  checkBadges(false);

  // ---------- Startbildschirm-Widget (kostenlos) ----------
  function updWidget() {
    if (!A || !A.setWidget) return;
    try {
      var wk = sumSince(weekStart(Date.now())), st = streak();
      A.setWidget(km(wk.d) + ' km', 'diese Woche' + (st.cur ? ' · Serie ' + st.cur + ' ' + (st.cur === 1 ? 'Tag' : 'Tage') : ''), wk.d >= S.goalKm * 1000 ? 'Wochenziel geschafft' : 'Noch ' + km(Math.max(0, S.goalKm * 1000 - wk.d)) + ' km bis zum Wochenziel');
    } catch (e) {}
  }
  updWidget();

  // ---------- Startseite: Velo mit Serie und Wochenziel ----------
  VX.homeHtml = function () {
    var st = streak(), wk = sumSince(weekStart(Date.now())), goal = S.goalKm * 1000, mood, head, txt;
    var hr = new Date().getHours();
    if (!LOG.length) { mood = 'wave'; head = 'Hallo, ich bin Velo!'; txt = 'Starte deine erste Fahrt. Ich zähle mit und feiere jede Serie mit dir.'; }
    else if (st.today) { mood = 'thumbs'; head = st.cur > 1 ? st.cur + ' Tage in Folge!' : 'Heute schon gefahren!'; txt = 'Diese Woche: ' + km(wk.d) + ' km. ' + (st.cur > 1 ? 'Morgen geht die Serie weiter.' : 'Fahr morgen wieder für eine Serie.'); }
    else if (hr >= 22 || hr < 6) { mood = 'sleep'; head = 'Gute Nacht!'; txt = st.cur ? 'Morgen fahren, sonst erlischt deine Flamme (' + st.cur + ' Tage).' : 'Morgen ist ein guter Tag für eine Runde.'; }
    else if (st.cur > 0) { mood = 'wave'; head = 'Serie: ' + st.cur + (st.cur === 1 ? ' Tag' : ' Tage'); txt = hr >= 18 ? 'Heute noch eine kleine Runde? Sonst erlischt die Flamme.' : 'Fahr heute, damit deine Flamme weiterbrennt.'; }
    else if (st.lost) { mood = 'sad'; head = 'Die Flamme ist aus'; txt = 'Deine Serie von ' + st.best + ' Tagen ist vorbei. Eine Fahrt heute zündet sie neu an!'; }
    else { mood = 'idle'; head = (hr < 11 ? 'Guten Morgen!' : hr < 18 ? 'Lust auf eine Runde?' : 'Guten Abend!'); txt = 'Diese Woche: ' + km(wk.d) + ' km. Eine kurze Tour bringt dich in Schwung.'; }
    return '<div class="velo-card" data-act="stats">' + mascot(mood, 74) + '<div class="bubble"><b>' + esc(head) + '</b><span>' + esc(txt) + '</span>' +
      '<div class="goalbar"><i style="width:' + Math.min(100, wk.d / goal * 100).toFixed(1) + '%"></i></div></div>' +
      '<div class="flame' + (st.cur ? '' : ' off') + '">' + FLAME + st.cur + '</div></div>';
  };
  VX.act = function (act) {
    if (act === 'stats') showStats();
    else if (act === 'plus') paywall();
    else if (act === 'dayplan') showDayPlan();
  };

  // ---------- Statistik-Seite (kostenlos; Bestzeiten mit Plus) ----------
  function showStats() {
    V.state = 'stats';
    var st = streak(), now = Date.now(), ws = weekStart(now), days = weekDays(ws), mx = Math.max.apply(null, days.concat([1000]));
    var wk = sumSince(ws), lastWk = sumSince(ws - 7 * 864e5, ws), mo = new Date(now); mo.setDate(1); mo.setHours(0, 0, 0, 0);
    var mon = sumSince(mo.getTime()), yr = sumSince(new Date(new Date().getFullYear(), 0, 1).getTime()), all = sumSince(0);
    var names = ['Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa', 'So'], c = badgeCtx(), goal = S.goalKm * 1000, diff = lastWk.d ? Math.round((wk.d - lastWk.d) / lastWk.d * 100) : null;
    var nGot = FAM.reduce(function (a, f) { return a + tierOf(f, c).t; }, 0), nAll = FAM.reduce(function (a, f) { return a + f[3].length; }, 0);
    var html = '<div style="display:flex;align-items:center;gap:12px">' + mascot(st.today ? 'thumbs' : 'wave', 84) + '<div class="grow"><h2>Deine Fahrten</h2><div class="sub"><span class="flame inl">' + FLAME + st.cur + '</span> Serie &middot; Bestmarke ' + st.best + ' Tage</div></div></div>' +
      '<h3>Diese Woche</h3><div class="weekbars">' + days.map(function (d, i) { var h = d / mx * 100; return '<div><em>' + (d ? km(d) : '') + '</em><i class="' + (d ? '' : 'zero') + '" style="height:' + Math.max(4, h) + '%;animation-delay:' + i * 0.05 + 's"></i><span>' + names[i] + '</span></div>'; }).join('') + '</div>' +
      '<div class="pbox"><div class="t">Wochenziel ' + S.goalKm + ' km</div><div class="meter"><i style="width:' + Math.min(100, wk.d / goal * 100) + '%;background:linear-gradient(90deg,var(--lime),var(--acc))"></i></div><div class="sub">' + km(wk.d) + ' von ' + S.goalKm + ' km' + (wk.d >= goal ? ' &middot; geschafft' : '') + '</div>' +
      '<div class="seg">' + [20, 30, 50, 80, 120, 200].map(function (g) { return '<button data-goal="' + g + '" class="' + (S.goalKm === g ? 'on' : '') + '">' + g + '</button>'; }).join('') + '</div></div>' +
      '<div class="stats"><div class="stat"><b>' + km(wk.d) + '</b><span>km Woche</span></div><div class="stat"><b>' + km(mon.d) + '</b><span>km Monat</span></div><div class="stat"><b>' + km(yr.d) + '</b><span>km Jahr</span></div></div>' +
      '<div class="stats"><div class="stat w"><b>' + wk.kcal + '</b><span>kcal Woche</span></div><div class="stat w"><b>' + Math.round(wk.asc) + ' m</b><span>Höhe Woche</span></div><div class="stat w"><b>' + (all.mv ? num((all.d / all.mv * 3.6).toFixed(1)) : '0') + '</b><span>km/h Schnitt</span></div></div>' +
      '<div class="stats"><div class="stat w"><b>' + km(all.d) + '</b><span>km gesamt</span></div><div class="stat w"><b>' + km(all.maxD) + '</b><span>Längste</span></div><div class="stat w"><b>' + all.n + '</b><span>Fahrten</span></div></div>' +
      '<h3>Wochenrückblick</h3><div class="velo-card">' + mascot(diff != null && diff >= 0 ? 'thumbs' : 'idle', 64) + '<div class="bubble"><span>' + esc(recap(wk, lastWk, diff, st)) + '</span></div></div>' +
      '<h3>Abzeichen ' + nGot + ' / ' + nAll + ' Stufen</h3>' + badgeGrid(c);
    var bl = Object.keys(BEST).map(function (k) { return BEST[k]; }).sort(function (a, b) { return b.gain - a.gain; });
    html += '<h3>Bestzeiten an Anstiegen' + (isPlus() ? '' : TAG) + '</h3>' + (!isPlus() ? '<div class="pbox locked" data-lock="Bestzeiten an Anstiegen"><div class="sub">Velo misst deine Zeit an jedem Anstieg und feiert neue Rekorde.</div></div>'
      : bl.length ? bl.slice(0, 10).map(function (b) { return '<div class="row"><div class="ico">' + svg('up', '#e8590c', 18) + '</div><div class="grow"><div class="t ell">' + esc(b.name) + '</div><div class="s">' + new Date(b.when).toLocaleDateString('de-DE') + (b.prev ? ' &middot; vorher ' + fmtMin(b.prev) : '') + '</div></div><div class="r"><b>' + fmtMin(b.t) + '</b></div></div>'; }).join('') : '<div class="empty sub">Fahr einen Anstieg hoch, dann misst Velo deine Zeit.</div>') +
      '<div class="btns"><button class="go sec" id="stBack">Zurück</button></div>';
    V.sheet(html); V.sheetEl.classList.add('tall');
    V.sheetEl.querySelectorAll('[data-goal]').forEach(function (b) { b.onclick = function () { S.goalKm = +b.getAttribute('data-goal'); V.saveS(); updWidget(); showStats(); }; });
    V.sheetEl.querySelectorAll('[data-fam]').forEach(function (b) { b.onclick = function () { showFamily(+b.getAttribute('data-fam')); }; });
    V.sheetEl.querySelectorAll('[data-lock]').forEach(function (b) { b.onclick = function () { gate(b.getAttribute('data-lock')); }; });
    $('stBack').onclick = function () { V.drawHome(); };
  }
  function recap(wk, lw, diff, st) {
    if (!wk.n && !lw.n) return 'Diese Woche noch keine Fahrt. Schon 20 Minuten auf dem Rad machen den Kopf frei!';
    var t = 'Diese Woche ' + km(wk.d) + ' km in ' + wk.n + (wk.n === 1 ? ' Fahrt' : ' Fahrten') + ', ' + Math.round(wk.asc) + ' Höhenmeter und rund ' + wk.kcal + ' kcal.';
    if (diff != null) t += diff >= 0 ? ' Das sind ' + diff + ' % mehr als letzte Woche, stark!' : ' Letzte Woche waren es ' + km(lw.d) + ' km, da geht noch was.';
    if (st.cur >= 3) t += ' Deine Flamme brennt seit ' + st.cur + ' Tagen.';
    return t;
  }

  // =====================================================================
  // Juno: kostenlos 5 Fragen am Tag, mit Plus unbegrenzt
  // =====================================================================
  // Credits: jede Rechenrunde der KI kostet einen (einfache Frage 1, mit Suche oder Recherche 2 bis 4)
  var CR_FREE = 10, CR_PLUS = 100;
  function crMax() { return VX.srvCredits ? VX.srvCredits.max : isPlus() ? CR_PLUS : CR_FREE; }
  function crUsed() { var c = V.load('junoCr', { d: '', n: 0 }); return c.d === dayKey(Date.now()) ? c.n : 0; }
  function crLeft() { return VX.srvCredits ? VX.srvCredits.left : Math.max(0, crMax() - crUsed()); }
  VX.junoGate = function () {
    if (crLeft() >= 1) return true;
    if (isPlus()) { V.toast('Deine ' + CR_PLUS + ' Credits für heute sind aufgebraucht. Morgen gibt es neue.'); return false; }
    paywall('Mehr Juno-Credits (' + CR_PLUS + ' statt ' + CR_FREE + ' am Tag)'); return false;
  };
  VX.junoUsed = function (cr) {
    V.store('junoCr', { d: dayKey(Date.now()), n: crUsed() + (cr || 1) });
    V.store('junoTotal', V.load('junoTotal', 0) + 1);
    var left = crLeft();
    if (!isPlus() && left <= 3) V.toast(left ? 'Noch ' + left + ' Juno-Credits heute' : 'Juno-Credits für heute aufgebraucht');
    var h = $('crHead'); if (h) h.outerHTML = VX.junoHead();
    checkBadges(true);
  };
  VX.junoHead = function () {
    var m = crMax(), l = crLeft();
    return '<div class="crhead" id="crHead"><svg width="18" height="18" viewBox="0 0 24 24"><circle cx="12" cy="12" r="10" fill="#ffd43b" stroke="#e09a00" stroke-width="2"/><path d="M12 7v10M9 9.5h4.5a1.75 1.75 0 0 1 0 3.5h-3a1.75 1.75 0 0 0 0 3.5H15" stroke="#a86b00" stroke-width="1.8" fill="none" stroke-linecap="round"/></svg>' +
      '<div class="grow"><b>' + l + ' von ' + m + ' Credits heute</b><div class="meter" style="margin:4px 0 0"><i style="width:' + (l / m * 100) + '%;background:linear-gradient(90deg,#ffd43b,#ff922b)"></i></div></div>' +
      (isPlus() ? '' : '<button class="chip sm" data-act="plus">Mehr mit Plus</button>') + '</div>';
  };

  // =====================================================================
  // Routenblatt: E-Bike-Akku, Regenwarnung, Speichern, GPX, Live teilen
  // =====================================================================
  var ASSIST = { eco: ['Eco', 0.35], tour: ['Tour', 0.5], turbo: ['Turbo', 0.68] };
  function batteryWh(r) {
    var m = 115, v = r.len / Math.max(60, r.secs), crr = 0.008, E = 0;
    for (var i = 1; i < r.ele.length; i++) { var dh = r.ele[i] - r.ele[i - 1]; if (dh > 0) E += m * 9.81 * dh; }
    E += crr * m * 9.81 * r.len + 0.5 * 1.2 * 0.6 * v * v * r.len;
    return E / 3600 * ASSIST[S.assist][1] / 0.78 + r.len / 1000 * 1.5;
  }
  VX.routeHtml = function (r) {
    var h = '';
    if (S.bike === 'ebike') {
      if (isPlus()) {
        var wh = batteryWh(r), p = wh / S.batt * 100, col = p > 90 ? '#e5463c' : p > 65 ? '#f08c00' : '#12b886';
        h += '<div class="pbox' + (p > 90 ? ' warn' : '') + '"><div class="t">' + svg('wind', col, 18) + 'E-Bike-Akku: ca. ' + Math.round(wh) + ' Wh</div><div class="meter"><i style="width:' + Math.min(100, p) + '%;background:' + col + '"></i></div>' +
          '<div class="sub">' + (p > 100 ? 'Der Akku reicht wahrscheinlich nicht. Unterwegs laden oder sparsamer fahren.' : p > 90 ? 'Knapp: Du kommst mit wenig Reserve an.' : 'Reicht gut: am Ziel noch etwa ' + Math.round(100 - p) + ' %.') + '</div>' +
          '<div class="seg">' + Object.keys(ASSIST).map(function (k) { return '<button data-assist="' + k + '" class="' + (S.assist === k ? 'on' : '') + '">' + ASSIST[k][0] + '</button>'; }).join('') + '</div>' +
          '<div class="seg">' + [400, 500, 625, 750].map(function (b) { return '<button data-batt="' + b + '" class="' + (S.batt === b ? 'on' : '') + '">' + b + ' Wh</button>'; }).join('') + '</div>' +
          (p > 65 ? '<button class="go sec" id="chargeBtn" style="height:42px;font-size:15px">Ladestationen in der Nähe</button><div id="chargeOut"></div>' : '') + '</div>';
      } else h += '<div class="pbox locked" data-lock="E-Bike-Akkuplaner"><div class="t">' + svg('wind', '#7c5cff', 18) + 'Reicht dein Akku?' + TAG + '</div><div class="sub">Akkuverbrauch und Ladestationen für diese Route.</div></div>';
    }
    h += isPlus() ? '<div class="pbox" id="rainBox"><div class="t">' + svg('sun', '#5aa9ff', 18) + 'Wetter unterwegs</div><div class="sub">Prüfe Regen entlang der Route ...</div></div>'
      : '<div class="pbox locked" data-lock="Die Regenwarnung"><div class="t">' + svg('sun', '#7c5cff', 18) + 'Regnet es unterwegs?' + TAG + '</div><div class="sub">Regenwarnung zu deiner Ankunftszeit an jedem Abschnitt.</div></div>';
    h += '<div class="linkrow"><button id="rtSave">' + svg('flag', 'currentColor', 18) + 'Speichern</button><button id="rtGpx">' + svg('road', 'currentColor', 18) + 'GPX' + (isPlus() ? '' : TAG) + '</button></div>';
    return h;
  };
  VX.routeBound = function (r, plan) {
    var el = V.sheetEl;
    el.querySelectorAll('[data-lock]').forEach(function (b) { b.onclick = function () { gate(b.getAttribute('data-lock')); }; });
    el.querySelectorAll('[data-assist]').forEach(function (b) { b.onclick = function () { S.assist = b.getAttribute('data-assist'); V.saveS(); rerouteSheet(); }; });
    el.querySelectorAll('[data-batt]').forEach(function (b) { b.onclick = function () { S.batt = +b.getAttribute('data-batt'); V.saveS(); rerouteSheet(); }; });
    if ($('chargeBtn')) $('chargeBtn').onclick = function () { chargers(r); };
    $('rtSave').onclick = function () { saveRoute(r, plan); };
    $('rtGpx').onclick = function () { if (gate('GPX-Export')) exportGpx(r, plan.title); };
    if (isPlus() && $('rainBox')) rainCheck(r);
  };
  function rerouteSheet() { var st = V.sheetEl.scrollTop; if (V.plan && V.cur) { V.drawRouteSheet(); V.sheetEl.scrollTop = st; } }
  function chargers(r) {
    var out = $('chargeOut'); out.innerHTML = '<div class="sub" style="margin-top:8px">Suche Ladestationen ...</div>';
    var la = r.la, lo = r.lo, s = Math.min.apply(null, la), n = Math.max.apply(null, la), w = Math.min.apply(null, lo), e = Math.max.apply(null, lo);
    fetch('https://nominatim.openstreetmap.org/search?format=jsonv2&limit=8&q=charging%20station&bounded=1&viewbox=' + (w - 0.03) + ',' + (n + 0.03) + ',' + (e + 0.03) + ',' + (s - 0.03))
      .then(function (x) { return x.json(); }).then(function (l) {
        if (!l.length) { out.innerHTML = '<div class="sub" style="margin-top:8px">Keine Ladestationen in der Nähe der Route gefunden. Tipp: Viele Cafés laden E-Bikes, einfach fragen.</div>'; return; }
        out.innerHTML = l.map(function (x, i) { return '<div class="row" data-ch="' + i + '"><div class="ico">' + svg('pin', '#12b886', 18) + '</div><div class="grow"><div class="t ell">' + esc(x.name || 'Ladestation') + '</div><div class="s ell">' + esc(String(x.display_name).split(',').slice(1, 3).join(',')) + '</div></div></div>'; }).join('');
        out.querySelectorAll('[data-ch]').forEach(function (b) { b.onclick = function () { var x = l[+b.getAttribute('data-ch')]; V.setFollow(false); V.view(+x.lat, +x.lon, 16); V.sheetEl.classList.add('mini'); }; });
      }).catch(function () { out.innerHTML = '<div class="sub" style="margin-top:8px">Für die Suche braucht die App Internet.</div>'; });
  }
  function rainCheck(r) {
    var box = $('rainBox'), now = Date.now(), pts = [0, 0.33, 0.66, 1].map(function (f) { var k = V.idxAt(r, r.len * f); return { la: r.la[k], lo: r.lo[k], km: r.cum[k], t: now + (r.tc[k] || 0) * 1000 }; });
    fetch('https://api.open-meteo.com/v1/forecast?latitude=' + pts.map(function (p) { return p.la.toFixed(3); }).join(',') + '&longitude=' + pts.map(function (p) { return p.lo.toFixed(3); }).join(',') + '&hourly=precipitation,precipitation_probability&forecast_days=2&timezone=auto&timeformat=unixtime')
      .then(function (x) { return x.json(); }).then(function (d) {
        var arr = Array.isArray(d) ? d : [d], warn = [];
        pts.forEach(function (p, i) {
          var h = arr[i] && arr[i].hourly; if (!h) return;
          var k = 0; while (k < h.time.length - 1 && h.time[k + 1] * 1000 <= p.t) k++;
          var mm = h.precipitation[k] || 0, pr = h.precipitation_probability ? h.precipitation_probability[k] || 0 : 0;
          if (mm >= 0.3 || pr >= 50) warn.push({ km: p.km, t: p.t, mm: mm, pr: pr });
        });
        if (!$('rainBox')) return;
        if (!warn.length) { box.className = 'pbox ok'; box.innerHTML = '<div class="t">' + svg('sun', '#0c9a68', 18) + 'Trocken unterwegs</div><div class="sub">Bis zur Ankunft um ' + V.clock(r.secs) + ' ist auf der Strecke kein Regen gemeldet.</div>'; }
        else { var w0 = warn[0]; box.className = 'pbox warn'; box.innerHTML = '<div class="t">' + svg('sun', '#e5463c', 18) + 'Regen möglich</div><div class="sub">Bei km ' + km(w0.km) + ' gegen ' + V.hhmm(new Date(w0.t)) + ': ' + w0.pr + ' % Regenwahrscheinlichkeit' + (w0.mm ? ', ' + num(w0.mm.toFixed(1)) + ' mm' : '') + '. Regenjacke einpacken oder früher losfahren.</div>'; hap('soft'); }
      }).catch(function () { if ($('rainBox')) box.querySelector('.sub').textContent = 'Wetter braucht Internet.'; });
  }

  // ---------- Gespeicherte Routen ----------
  var SAVED = V.load('saved', []);
  function saveRoute(r, plan) {
    if (SAVED.some(function (x) { return x.title === plan.title && Math.abs(x.len - r.len) < 50; })) { V.toast('Diese Route ist schon gespeichert'); return; }
    SAVED.unshift({ t: Date.now(), title: plan.title, len: r.len, asc: r.asc, secs: r.secs, opt: { loop: !!plan.opt.loop, tour: !!plan.opt.tour, style: plan.opt.style || S.style }, pts: plan.pts.map(function (p) { return { lat: p.lat, lon: p.lon, name: p.name || '' }; }) });
    V.store('saved', SAVED.slice(0, 50)); hap('success'); V.toast('Route gespeichert');
  }
  function showSaved() {
    V.state = 'saved';
    V.sheet('<h2>Gespeicherte Routen</h2>' + (SAVED.length ? SAVED.map(function (x, i) { return '<div class="row" data-sv="' + i + '"><div class="ico">' + svg(x.opt.loop ? 'loop' : 'flag', 'currentColor', 18) + '</div><div class="grow"><div class="t ell">' + esc(x.title) + '</div><div class="s">' + V.fmtDist(x.len) + ' &middot; ' + V.fmtTime(x.secs) + ' &middot; ' + Math.round(x.asc) + ' m bergauf</div></div><button class="chip sm" data-svdel="' + i + '">Löschen</button></div>'; }).join('') : '<div class="velo-card">' + mascot('think', 50) + '<div class="bubble"><span>Noch keine Routen. Plane eine Route und tippe auf "Speichern".</span></div></div>') +
      '<div class="btns"><button class="go sec" id="gpxIn">GPX importieren' + (isPlus() ? '' : TAG) + '</button></div><input type="file" id="gpxFile" accept=".gpx,application/gpx+xml,application/xml,text/xml" style="display:none">' +
      '<div class="btns"><button class="go sec" id="svBack">Zurück</button></div>');
    V.sheetEl.querySelectorAll('[data-sv]').forEach(function (b) { b.onclick = function (e) { if (e.target.closest('[data-svdel]')) return; var x = SAVED[+b.getAttribute('data-sv')]; V.planRoute(x.pts, x.title, { picked: true, loop: x.opt.loop, tour: x.opt.tour, style: x.opt.style }); }; });
    V.sheetEl.querySelectorAll('[data-svdel]').forEach(function (b) { b.onclick = function () { SAVED.splice(+b.getAttribute('data-svdel'), 1); V.store('saved', SAVED); showSaved(); }; });
    $('gpxIn').onclick = function () { if (gate('GPX-Import')) $('gpxFile').click(); };
    $('gpxFile').onchange = function () { var f = this.files[0]; if (!f) return; var rd = new FileReader(); rd.onload = function () { importGpx(String(rd.result), f.name); }; rd.readAsText(f); };
    $('svBack').onclick = function () { V.showMenu(); };
  }
  function exportGpx(r, title) {
    var x = '<?xml version="1.0" encoding="UTF-8"?>\n<gpx version="1.1" creator="Velora" xmlns="http://www.topografix.com/GPX/1/1">\n<trk><name>' + esc(title) + '</name><trkseg>\n';
    for (var i = 0; i < r.la.length; i++) x += '<trkpt lat="' + r.la[i].toFixed(6) + '" lon="' + r.lo[i].toFixed(6) + '"><ele>' + Math.round(r.ele[i]) + '</ele></trkpt>\n';
    x += '</trkseg></trk>\n</gpx>\n';
    var name = 'Velora-' + String(title).replace(/[^A-Za-z0-9äöüÄÖÜß]+/g, '-').slice(0, 40) + '.gpx';
    if (A && A.saveFile) { var p = A.saveFile(name, 'application/gpx+xml', x); if (p) { hap('success'); V.toast('Gespeichert: ' + p); return; } }
    try {
      var a = document.createElement('a'); a.href = URL.createObjectURL(new Blob([x], { type: 'application/gpx+xml' })); a.download = name; document.body.appendChild(a); a.click(); a.remove();
    } catch (e) {}
    V.sheet('<h2>GPX-Datei</h2><div class="sub">Falls kein Download startet: Text kopieren und als .gpx speichern.</div><textarea class="gpxta" readonly>' + esc(x) + '</textarea><div class="btns"><button class="go" id="gpxCopy">Kopieren</button><button class="go sec" id="gpxBack">Zurück</button></div>');
    $('gpxCopy').onclick = function () { try { navigator.clipboard.writeText(x).then(function () { V.toast('Kopiert'); }); } catch (e) {} };
    $('gpxBack').onclick = function () { V.planRoute(V.plan.pts, V.plan.title, Object.assign({}, V.plan.opt, { picked: true })); };
  }
  function importGpx(text, fname) {
    try {
      var doc = new DOMParser().parseFromString(text, 'application/xml'), q = doc.getElementsByTagName('trkpt');
      if (!q.length) q = doc.getElementsByTagName('rtept');
      if (!q.length) q = doc.getElementsByTagName('wpt');
      var all = []; for (var i = 0; i < q.length; i++) all.push({ lat: +q[i].getAttribute('lat'), lon: +q[i].getAttribute('lon') });
      all = all.filter(function (p) { return isFinite(p.lat) && isFinite(p.lon); });
      if (all.length < 2) { V.toast('In der Datei ist keine Strecke'); return; }
      var nm = doc.getElementsByTagName('name')[0], title = nm ? nm.textContent.trim() : fname.replace(/\.gpx$/i, '');
      var n = Math.min(12, all.length), pts = [];
      for (var k = 0; k < n; k++) pts.push(all[Math.round(k * (all.length - 1) / (n - 1))]);
      pts[0].name = 'GPX-Start'; pts[pts.length - 1].name = title || 'GPX-Ziel';
      var loop = V.dist(pts[0].lat, pts[0].lon, pts[n - 1].lat, pts[n - 1].lon) < 200;
      hap('success');
      V.planRoute(pts, title || 'GPX-Strecke', { picked: true, tour: true, loop: loop, desc: 'Aus GPX importiert (' + all.length + ' Punkte). Die App fährt die Strecke über ' + n + ' Punkte nach.' });
    } catch (e) { V.toast('GPX konnte nicht gelesen werden'); }
  }

  // =====================================================================
  // Navigation: Pfeile auf der Route, gefahrener Teil grau, Reisefuehrer, Live teilen
  // =====================================================================
  var curR = null, lastDone = 0, mapOk = false, mapTries = 0;
  function mapInit() {
    if (!V.mapReady || !map.getLayer || !map.getLayer('route')) { if (++mapTries < 100) setTimeout(mapInit, 300); return; }
    try {
      var c = document.createElement('canvas'); c.width = c.height = 48; var g = c.getContext('2d');
      g.lineCap = 'round'; g.lineJoin = 'round';
      g.strokeStyle = 'rgba(13,43,62,.55)'; g.lineWidth = 11; g.beginPath(); g.moveTo(16, 12); g.lineTo(30, 24); g.lineTo(16, 36); g.stroke();
      g.strokeStyle = '#ffffff'; g.lineWidth = 6; g.beginPath(); g.moveTo(16, 12); g.lineTo(30, 24); g.lineTo(16, 36); g.stroke();
      map.addImage('chev', g.getImageData(0, 0, 48, 48), { pixelRatio: 2 });
      var ls = map.getStyle().layers, i = ls.findIndex(function (l) { return l.id === 'route'; }), before = ls[i + 1] ? ls[i + 1].id : undefined;
      map.addSource('rDone', { type: 'geojson', data: V.fc() });
      map.addSource('rAhead', { type: 'geojson', data: V.fc() });
      map.addLayer({ id: 'rDone', type: 'line', source: 'rDone', layout: { 'line-cap': 'round', 'line-join': 'round' }, paint: { 'line-color': '#a7b1bc', 'line-width': ['interpolate', ['exponential', 1.5], ['zoom'], 10, 2.4, 14, 7.5, 18, 16] } }, before);
      map.addLayer({ id: 'rArrows', type: 'symbol', source: 'rAhead', layout: { 'symbol-placement': 'line', 'symbol-spacing': ['interpolate', ['linear'], ['zoom'], 12, 50, 17, 90], 'icon-image': 'chev', 'icon-size': ['interpolate', ['linear'], ['zoom'], 11, 0.35, 15, 0.6, 18, 0.85], 'icon-allow-overlap': true, 'icon-ignore-placement': true, 'icon-rotation-alignment': 'map', 'icon-keep-upright': false } }, before);
      mapOk = true;
      if (curR) VX.routeDrawn(curR);
    } catch (e) { if (window.console) console.error(e); }
  }
  mapInit();
  function lineOf(r, a, b) { var c = []; for (var i = a; i <= b && i < r.la.length; i++) c.push([r.lo[i], r.la[i]]); return c; }
  VX.routeDrawn = function (r) {
    curR = r; lastDone = 0;
    if (!mapOk) return;
    map.getSource('rDone').setData(V.fc());
    map.getSource('rAhead').setData(r ? { type: 'Feature', properties: {}, geometry: { type: 'LineString', coordinates: lineOf(r, 0, r.la.length - 1) } } : V.fc());
  };
  var lastTick = 0, guideT = 0, told = {}, liveT = 0;
  VX.navTick = function (r, along, ref) {
    var now = Date.now();
    if (mapOk && r && now - lastTick > 1200 && Math.abs(along - lastDone) > 5) {
      lastTick = now; lastDone = along;
      var k = V.idxAt(r, along), done = lineOf(r, 0, k);
      if (done.length > 1) map.getSource('rDone').setData({ type: 'Feature', properties: {}, geometry: { type: 'LineString', coordinates: done } });
      map.getSource('rAhead').setData({ type: 'Feature', properties: {}, geometry: { type: 'LineString', coordinates: lineOf(r, Math.max(0, k - 1), r.la.length - 1) } });
    }
    if (ref) { guideCheck(ref); liveSend(ref, r, along); }
  };
  VX.navStop = function () { liveStop(); var g = $('guide'); if (g) g.remove(); if (mapOk) map.getSource('rDone').setData(V.fc()); };
  VX.arrived = function (plan) { hap('success'); arrivedFlag = true; };
  VX.navSheet = function () {
    if (!V.nav || V.nav.free) return;
    var b = document.createElement('button'); b.className = 'go sec'; b.id = 'liveBtn'; b.style.cssText = 'height:42px;font-size:15px;margin-top:8px';
    b.innerHTML = live ? 'Live-Standort wird geteilt' : 'Live-Standort teilen';
    V.sheetEl.appendChild(b);
    b.onclick = liveStart;
  };

  // ---------- Juno als Reisefuehrer (Wikipedia, kostet kein Juno-Kontingent) ----------
  function guideCheck(ref) {
    if (!isPlus() || !S.guide || V.lastSpeed < 4) return;
    var now = Date.now(); if (now - guideT < 90000) return; guideT = now;
    fetch('https://de.wikipedia.org/w/api.php?action=query&format=json&origin=*&generator=geosearch&ggscoord=' + ref.lat.toFixed(5) + '|' + ref.lon.toFixed(5) + '&ggsradius=700&ggslimit=6&prop=extracts&exintro=1&explaintext=1&exsentences=2')
      .then(function (x) { return x.json(); }).then(function (d) {
        var pages = d.query && d.query.pages; if (!pages) return;
        var list = Object.keys(pages).map(function (k) { return pages[k]; }).filter(function (p) { return !told[p.pageid] && p.extract && p.extract.length > 60; }).sort(function (a, b) { return a.index - b.index; });
        var p = list[0]; if (!p) return;
        told[p.pageid] = 1;
        var txt = p.extract.replace(/\s*\([^)]*\)/g, '').replace(/\s+/g, ' ').trim();
        showGuide(p.title, txt);
        if (S.voice) V.say('Du fährst gerade an ' + p.title + ' vorbei. ' + txt);
      }).catch(function () {});
  }
  function showGuide(title, txt) {
    var g = $('guide'); if (g) g.remove();
    g = document.createElement('div'); g.id = 'guide';
    g.innerHTML = mascot('wave', 40) + '<div class="grow"><b>' + esc(title) + '</b><span>' + esc(txt) + '</span></div>';
    document.body.appendChild(g); hap('soft');
    g.onclick = function () { g.remove(); };
    setTimeout(function () { if (g.parentNode) { g.style.transition = 'opacity .4s'; g.style.opacity = 0; setTimeout(function () { g.remove(); }, 400); } }, 15000);
  }

  // ---------- Live-Standort teilen ----------
  var live = V.load('live', null);
  if (live && Date.now() - live.t > 3 * 3600e3) { live = null; V.store('live', null); }
  function liveStart() {
    if (!live) { live = { id: rnd(12), key: rnd(28), t: Date.now() }; V.store('live', live); }
    var url = KI_BASE + '/l/' + live.id;
    liveT = 0; if (V.meRef()) liveSend(V.meRef(), V.cur, V.nav ? V.nav.lastAlong : 0);
    modal('<div style="display:flex;align-items:center;gap:12px">' + mascot('wave', 56) + '<div class="grow"><h2>Live teilen</h2><div class="sub">Wer den Link hat, sieht während dieser Fahrt deinen Standort. Er endet mit der Fahrt.</div></div></div>' +
      '<div class="pbox"><div class="t" style="word-break:break-all;font-size:14px">' + esc(url) + '</div></div>' +
      '<div class="btns"><button class="go" id="liveShare">Link teilen</button><button class="go sec" id="liveCopy">Kopieren</button></div>' +
      '<div class="btns"><button class="go sec" id="liveBack">Zurück zur Navigation</button></div>');
    $('liveShare').onclick = function () {
      var t = 'Hier kannst du sehen, wo ich gerade mit dem Rad fahre: ' + url;
      if (A && A.shareText) A.shareText('Live-Standort teilen', t);
      else if (navigator.share) navigator.share({ title: 'Velora Live', text: t, url: url }).catch(function () {});
      else { try { navigator.clipboard.writeText(url); V.toast('Link kopiert'); } catch (e) {} }
    };
    $('liveCopy').onclick = function () { try { navigator.clipboard.writeText(url).then(function () { V.toast('Link kopiert'); }); } catch (e) { V.toast(url); } };
    $('liveBack').onclick = closeModal;
  }
  function liveSend(ref, r, along) {
    if (!live || Date.now() - liveT < 60000) return;
    liveT = Date.now();
    var body = { id: live.id, key: live.key, lat: +ref.lat.toFixed(5), lon: +ref.lon.toFixed(5), spd: V.lastSpeed > 0 ? Math.round(V.lastSpeed) : 0 };
    if (r && V.plan) { body.rest = Math.max(0, Math.round(r.len - along)); body.ziel = V.plan.title; var k = V.idxAt(r, along); body.eta = Date.now() + Math.max(0, r.secs - (r.tc[k] || 0)) * 1000; }
    fetch(KI_BASE + '/live', { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-App-Id': KI_ID }, body: JSON.stringify(body) }).catch(function () {});
  }
  function liveStop() {
    if (!live) return;
    fetch(KI_BASE + '/live', { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-App-Id': KI_ID }, body: JSON.stringify({ id: live.id, key: live.key, ende: true }) }).catch(function () {});
    live = null; V.store('live', null);
  }

  // =====================================================================
  // Standort-Pfeil: 3D-Pfeil, Blickkegel, weiches Drehen, Kamera kippt beim Fahren
  // =====================================================================
  var ARROWS = {
    '3d': ['3D-Pfeil', '<svg viewBox="0 0 50 54"><path d="M25 3 L45 49 L25 39 Z" style="fill:var(--acc)"/><path d="M25 3 L5 49 L25 39 Z" style="fill:var(--lime)"/><path d="M25 3 L45 49 L25 39 L5 49 Z" fill="none" stroke="#fff" stroke-width="3.5" stroke-linejoin="round"/></svg>'],
    pfeil: ['Klassisch', '<svg viewBox="0 0 50 54"><path d="M25 4 L44 46 L25 37 L6 46 Z" style="fill:var(--acc)" stroke="#fff" stroke-width="4" stroke-linejoin="round"/></svg>'],
    dart: ['Pfeilspitze', '<svg viewBox="0 0 50 54"><defs><linearGradient id="aD" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#ffb35c"/><stop offset="1" stop-color="#f76707"/></linearGradient></defs><path d="M25 2 L37 50 L25 43 L13 50 Z" fill="url(#aD)" stroke="#fff" stroke-width="3.5" stroke-linejoin="round"/></svg>'],
    rad: ['Rad', '<svg viewBox="0 0 50 54"><path d="M25 1 L33 15 H17 Z" style="fill:var(--acc)" stroke="#fff" stroke-width="2.5" stroke-linejoin="round"/><circle cx="25" cy="32" r="17" fill="#0d2b3e" stroke="#fff" stroke-width="3"/><circle cx="25" cy="32" r="11" style="fill:var(--lime)"/><circle cx="21" cy="30" r="2.6" fill="#0d2b3e"/><circle cx="29" cy="30" r="2.6" fill="#0d2b3e"/><path d="M20.5 35.5 Q25 39 29.5 35.5" stroke="#0d2b3e" stroke-width="2" fill="none" stroke-linecap="round"/></svg>']
  };
  var rotNow = 0, upgraded = null;
  function rotTo(deg) {
    var d = ((deg - rotNow) % 360 + 540) % 360 - 180; rotNow += d;
    var el = upgraded && upgraded.querySelector('.me-rot'); if (el) el.style.transform = 'rotate(' + rotNow + 'deg)';
  }
  function upgradeMe() {
    var mk = V.meMarker(); if (!mk) return;
    var el = mk.getElement();
    if (upgraded !== el) {
      upgraded = el; el.classList.add('me2');
      var orig = mk.setRotation.bind(mk); orig(0);
      mk.setRotation = function (r) { rotTo(r); return mk; };
      el.innerHTML = '<div class="me-rot"><div class="cone"></div><div id="meArrow" style="display:none">' + (ARROWS[S.arrow] || ARROWS['3d'])[1] + '</div></div><div class="dot"></div>';
    }
    var me = V.me; if (!me) return;
    var arrow = $('meArrow'), moving = arrow && arrow.style.display === 'block';
    if (me.brg >= 0) rotTo(me.brg);
    var cone = el.querySelector('.cone'); if (cone) cone.style.opacity = !moving && me.brg >= 0 ? 1 : 0;
  }
  function setArrowSkin() { if (upgraded) { var a = $('meArrow'); if (a) a.innerHTML = (ARROWS[S.arrow] || ARROWS['3d'])[1]; } }
  setInterval(upgradeMe, 400);
  if (!S.hu2) { S.headUp = true; S.hu2 = true; V.saveS(); } // beim Navigieren dreht und kippt die Karte mit (wie im Auto-Navi)

  // =====================================================================
  // Touren: Rueckenwind auf dem Heimweg (Plus)
  // =====================================================================
  function tailShare(r) {
    var WX = V.WX; if (!WX || WX.wind == null || !r.la) return null;
    var half = r.len / 2, sum = 0, tot = 0, to = (WX.dir + 180) % 360;
    for (var i = 1; i < r.la.length; i++) {
      if (r.cum[i] < half) continue;
      var L = r.cum[i] - r.cum[i - 1]; if (L <= 0) continue;
      sum += Math.cos((V.bearing(r.la[i - 1], r.lo[i - 1], r.la[i], r.lo[i]) - to) * Math.PI / 180) * L; tot += L;
    }
    return tot ? sum / tot : null;
  }
  VX.loops = function (res) {
    if (!res || !V.WX) return res;
    res.forEach(function (x) {
      var t = tailShare(x.r); x.tail = t;
      if (t != null && V.WX.wind >= 8) x.why = (x.why ? x.why + ' · ' : '') + (t > 0.25 ? 'Heimweg mit Rückenwind' : t < -0.25 ? 'Heimweg mit Gegenwind' : 'Heimweg mit Seitenwind');
    });
    if (S.tailwind) res.sort(function (a, b) { return (b.tail || 0) - (a.tail || 0); });
    return res;
  };
  VX.toursHtml = function () {
    return '<div class="opt" id="twRow"><div class="grow">Rückenwind auf dem Heimweg<div class="sub">' + (V.WX ? 'Wind ' + Math.round(V.WX.wind) + ' km/h' : 'Braucht Wetterdaten (Internet)') + '</div></div><div class="sw' + (S.tailwind ? ' on' : '') + '" id="swTail"></div></div>';
  };
  VX.toursBound = function () {
    var s = $('swTail'); if (!s) return;
    s.onclick = function () { S.tailwind = !S.tailwind; V.saveS(); s.classList.toggle('on', S.tailwind); };
  };

  // =====================================================================
  // Tagesplaner (Plus): Aussicht, Essen, Baden als eine Rundtour mit Zeitplan
  // =====================================================================
  var DP = V.load('dayplan', { h: 5, start: 10, want: { view: 1, food: 1, beach: 1, cafe: 0 } });
  function showDayPlan() {
    if (!gate('Der Tagesplaner')) return;
    V.state = 'dayplan';
    var W = [['view', 'Aussicht'], ['food', 'Mittagessen'], ['beach', 'Baden'], ['cafe', 'Kaffeepause']];
    V.sheet('<div style="display:flex;align-items:center;gap:12px">' + mascot('think', 60) + '<div class="grow"><h2>Tagesplaner</h2><div class="sub">Velo plant dir einen ganzen Tag als Rundtour, mit Pausen.</div></div></div>' +
      '<h3>Wie lange?</h3><div class="seg">' + [3, 5, 7].map(function (h) { return '<button data-dph="' + h + '" class="' + (DP.h === h ? 'on' : '') + '">' + h + ' Stunden</button>'; }).join('') + '</div>' +
      '<h3>Start</h3><div class="seg">' + [8, 9, 10, 11, 13].map(function (h) { return '<button data-dps="' + h + '" class="' + (DP.start === h ? 'on' : '') + '">' + h + ':00</button>'; }).join('') + '</div>' +
      '<h3>Was willst du erleben?</h3><div class="hscroll" style="flex-wrap:wrap;overflow:visible">' + W.map(function (w) { return '<button class="chip' + (DP.want[w[0]] ? ' on' : '') + '" data-dpw="' + w[0] + '">' + w[1] + '</button>'; }).join('') + '</div>' +
      '<div class="btns"><button class="go" id="dpGo">Tag planen</button></div><div class="btns"><button class="go sec" id="dpJuno">Juno um Ideen fragen</button></div><div id="dpOut"></div>' +
      '<div class="btns"><button class="go sec" id="dpBack">Zurück</button></div>');
    V.sheetEl.querySelectorAll('[data-dph]').forEach(function (b) { b.onclick = function () { DP.h = +b.getAttribute('data-dph'); V.store('dayplan', DP); showDayPlan(); }; });
    V.sheetEl.querySelectorAll('[data-dps]').forEach(function (b) { b.onclick = function () { DP.start = +b.getAttribute('data-dps'); V.store('dayplan', DP); showDayPlan(); }; });
    V.sheetEl.querySelectorAll('[data-dpw]').forEach(function (b) { b.onclick = function () { var k = b.getAttribute('data-dpw'); DP.want[k] = DP.want[k] ? 0 : 1; V.store('dayplan', DP); showDayPlan(); }; });
    $('dpGo').onclick = planDay;
    $('dpJuno').onclick = function () { V.showJuno(); V.askJuno('Plane mir einen schönen Radtag von ' + DP.h + ' Stunden ab ' + DP.start + ' Uhr hier in der Gegend, mit ' + W.filter(function (w) { return DP.want[w[0]]; }).map(function (w) { return w[1]; }).join(', ') + '. Nenne echte Orte mit Uhrzeiten.'); };
    $('dpBack').onclick = function () { V.showMenu(); };
  }
  function planDay() {
    var s = V.startRef(), out = $('dpOut');
    var STOP = { view: [['view', 'sight', 'peak'], 20, 'Aussicht'], food: [['food'], 70, 'Mittagessen'], beach: [['beach'], 90, 'Baden'], cafe: [['cafe'], 30, 'Kaffeepause'] };
    var keys = Object.keys(STOP).filter(function (k) { return DP.want[k]; });
    if (!keys.length) { V.toast('Wähle mindestens einen Stopp'); return; }
    var budget = DP.h * 60, pause = keys.reduce(function (a, k) { return a + STOP[k][1]; }, 0), rideMin = Math.max(40, budget - pause);
    var radius = Math.min(18000, rideMin / 60 * 14000 / 2.4);
    var local = V.nearIsl(s) && V.onIsland(s.lat, s.lon);
    out.innerHTML = '<div class="m-load">' + mascot('think', 80) + '</div><div class="sub" style="text-align:center">Velo sucht schöne Stopps ...</div>';
    function cands(cats, R) {
      if (local) return V.D.pois.filter(function (p) { return cats.indexOf(p[0]) >= 0 && V.dist(s.lat, s.lon, p[1], p[2]) < R; }).map(function (p) { return { lat: p[1], lon: p[2], name: p[3] || V.CAT[p[0]].n, named: !!p[3] }; });
      return V.onlinePois(s.lat, s.lon, cats, 400, R).map(function (x) { return { lat: x.lat, lon: x.lon, name: x.name, named: true }; });
    }
    function pick(R) {
      var stops = [], used = {};
      keys.forEach(function (k, ki) {
        var ang = ki / keys.length * Math.PI * 2 + 0.6, tgt = { lat: s.lat + Math.cos(ang) * R * 0.7 / 110540, lon: s.lon + Math.sin(ang) * R * 0.7 / (111320 * Math.cos(s.lat * Math.PI / 180)) };
        var c = cands(STOP[k][0], R).filter(function (x) { return !used[x.name]; });
        c.sort(function (a, b) { return V.dist(tgt.lat, tgt.lon, a.lat, a.lon) - V.dist(tgt.lat, tgt.lon, b.lat, b.lon) - (a.named ? 300 : 0) + (b.named ? 300 : 0); });
        if (c[0]) { used[c[0].name] = 1; stops.push({ k: k, p: c[0] }); }
      });
      stops.sort(function (a, b) { return Math.atan2(a.p.lon - s.lon, a.p.lat - s.lat) - Math.atan2(b.p.lon - s.lon, b.p.lat - s.lat); });
      if (DP.want.food) { var fi = stops.findIndex(function (x) { return x.k === 'food'; }); if (fi >= 0 && stops.length > 2) { var f = stops.splice(fi, 1)[0]; stops.splice(Math.floor(stops.length / 2), 0, f); } }
      return stops;
    }
    function finish(r, pts, stops) {
      var t = DP.start * 60, plan = [V.hhmm(dt(t)) + ' Start'];
      stops.forEach(function (x, i) {
        var ride = (r.tc[V.idxAt(r, r.legEnds[i])] || 0) - (i ? r.tc[V.idxAt(r, r.legEnds[i - 1])] || 0 : 0);
        t += ride / 60; plan.push(V.hhmm(dt(t)) + ' ' + STOP[x.k][2] + ': ' + x.p.name + ' (' + STOP[x.k][1] + ' min)'); t += STOP[x.k][1];
      });
      t += (r.secs - (r.tc[V.idxAt(r, r.legEnds[stops.length - 1])] || 0)) / 60; plan.push(V.hhmm(dt(t)) + ' zurück am Start');
      hap('success');
      V.planRoute(pts, 'Tagesplan ' + DP.h + ' h', { picked: true, loop: true, tour: true, pre: r, style: 'scenic', desc: plan.join('  ·  ') });
    }
    function ptsOf(stops) { return [{ lat: s.lat, lon: s.lon, name: s.name }].concat(stops.map(function (x) { return { lat: x.p.lat, lon: x.p.lon, name: x.p.name }; })).concat([{ lat: s.lat, lon: s.lon, name: s.name }]); }
    function fail(msg) { if ($('dpOut')) $('dpOut').innerHTML = '<div class="velo-card">' + mascot('sad', 60) + '<div class="bubble"><span>' + esc(msg) + '</span></div></div>'; }
    if (local) {
      for (var R = radius; R > 1500; R *= 0.75) {
        var st = pick(R); if (!st.length) continue;
        var pts = ptsOf(st), r = V.routeVia(pts, { loop: true, style: 'scenic' });
        if (r && r.secs / 60 <= rideMin * 1.15) { finish(r, pts, st); return; }
      }
      fail('Dafür reicht die Zeit nicht. Nimm mehr Stunden oder weniger Stopps.'); return;
    }
    // weltweit: passende Orte rund um Zielpunkte bei OpenStreetMap (Overpass) abfragen, Route online berechnen
    var Q = { view: '["tourism"="viewpoint"]', food: '["amenity"="restaurant"]["name"]', beach: '["leisure"="bathing_place"]', cafe: '["amenity"~"cafe|ice_cream"]["name"]' };
    var Q2 = { beach: '["natural"="beach"]' };
    function targets(R) {
      return keys.map(function (k, ki) { var ang = ki / keys.length * Math.PI * 2 + 0.6; return { k: k, lat: s.lat + Math.cos(ang) * R * 0.7 / 110540, lon: s.lon + Math.sin(ang) * R * 0.7 / (111320 * Math.cos(s.lat * Math.PI / 180)) }; });
    }
    function overpass(R) {
      var tg = targets(R), q = '[out:json][timeout:25];(';
      tg.forEach(function (t) { var a = '(around:' + Math.round(R * 0.45) + ',' + t.lat.toFixed(5) + ',' + t.lon.toFixed(5) + ');'; q += 'nwr' + Q[t.k] + a; if (Q2[t.k]) q += 'nwr' + Q2[t.k] + a; });
      q += ');out center 400;';
      var body = 'data=' + encodeURIComponent(q);
      function go(u) { return fetch(u, { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: body }).then(function (x) { if (!x.ok) throw new Error(x.status); return x.json(); }); }
      return go('https://overpass-api.de/api/interpreter').catch(function () { return go('https://overpass.kumi.systems/api/interpreter'); }).then(function (j) {
        var res = { view: [], food: [], beach: [], cafe: [] };
        (j.elements || []).forEach(function (e) {
          var t = e.tags || {}, la = e.lat != null ? e.lat : e.center && e.center.lat, lo = e.lon != null ? e.lon : e.center && e.center.lon;
          if (la == null) return;
          var k = t.tourism === 'viewpoint' ? 'view' : t.amenity === 'restaurant' ? 'food' : /cafe|ice_cream/.test(t.amenity || '') ? 'cafe' : (t.natural === 'beach' || t.leisure === 'bathing_place') ? 'beach' : null;
          if (k) res[k].push({ lat: la, lon: lo, name: t['name:de'] || t.name || STOP[k][2], named: !!t.name });
        });
        return { tg: tg, res: res };
      });
    }
    (function attempt(R, tries) {
      overpass(R).then(function (o) {
        var stops = [], used = {};
        o.tg.forEach(function (t) {
          var c = o.res[t.k].filter(function (x) { return !used[x.name + x.lat]; });
          c.sort(function (a, b) { return V.dist(t.lat, t.lon, a.lat, a.lon) - V.dist(t.lat, t.lon, b.lat, b.lon) - (a.named ? 400 : 0) + (b.named ? 400 : 0); });
          if (c[0]) { used[c[0].name + c[0].lat] = 1; stops.push({ k: t.k, p: c[0] }); }
        });
        if (!stops.length) { if (tries < 2) attempt(R * 1.5, tries + 1); else fail('In der Umgebung habe ich keine passenden Stopps gefunden. Wähle andere Stopps.'); return; }
        stops.sort(function (a, b) { return Math.atan2(a.p.lon - s.lon, a.p.lat - s.lat) - Math.atan2(b.p.lon - s.lon, b.p.lat - s.lat); });
        if (DP.want.food) { var fi = stops.findIndex(function (x) { return x.k === 'food'; }); if (fi >= 0 && stops.length > 2) { var f = stops.splice(fi, 1)[0]; stops.splice(Math.floor(stops.length / 2), 0, f); } }
        var pts = ptsOf(stops);
        return V.fetchExt(pts, 'scenic').then(function (r) {
          if (r.secs / 60 > rideMin * 1.2 && tries < 2) { attempt(R * 0.65, tries + 1); return; }
          finish(r, pts, stops);
        });
      }).catch(function () { fail('Für den Tagesplan braucht die App Internet.'); });
    })(radius, 0);
  }
  function dt(min) { var d = new Date(); d.setHours(0, 0, 0, 0); return new Date(d.getTime() + min * 60000); }

  // =====================================================================
  // Offline-Karten (Plus, Android): Ausschnitt fuer Reisen ohne Internet laden
  // =====================================================================
  function tilesFor(b, z0, z1) {
    var out = [];
    function x2(lon, z) { return Math.floor((lon + 180) / 360 * Math.pow(2, z)); }
    function y2(lat, z) { var r = lat * Math.PI / 180; return Math.floor((1 - Math.log(Math.tan(r) + 1 / Math.cos(r)) / Math.PI) / 2 * Math.pow(2, z)); }
    for (var z = z0; z <= z1; z++) for (var x = x2(b.getWest(), z); x <= x2(b.getEast(), z); x++) for (var y = y2(b.getNorth(), z); y <= y2(b.getSouth(), z); y++) out.push([z, x, y]);
    return out;
  }
  var OFF_FREE = 1500, OFF_PLUS = 6000;
  function showOffline() {
    V.state = 'offline';
    var areas = V.load('offAreas', 0);
    var info = A && A.tileInfo ? A.tileInfo().split(',') : null;
    V.sheet('<div style="display:flex;align-items:center;gap:12px">' + mascot('idle', 56) + '<div class="grow"><h2>Offline-Karten</h2><div class="sub">Karte auf das Gebiet schieben, das du ohne Internet brauchst, und speichern.</div></div></div>' +
      (A && A.downloadTiles ? '<div class="pbox"><div class="t">Gespeichert</div><div class="sub">' + (info ? info[0] + ' Kartenteile, ' + num((info[1] / 1048576).toFixed(1)) + ' MB' : '-') + '</div></div>' +
        (isPlus() ? '' : '<div class="sub" style="font-size:13px;margin-bottom:6px">Gratis: ein Gebiet (etwa eine Stadt mit Umgebung). Mit Plus beliebig viele und größere.</div>') + '<div class="btns"><button class="go" id="dlGo">Sichtbaren Bereich speichern</button></div><div id="dlBox"></div><div class="btns"><button class="go sec" id="dlClear">Alle löschen</button></div>' +
        '<div class="sub" style="font-size:12px;margin-top:8px">Gespeichert werden Karte, Relief und Radrouten bis Zoomstufe 14. Neue Routen berechnen braucht weiterhin Internet.</div>'
        : '<div class="pbox"><div class="sub">Offline-Karten gibt es in der Android-App.</div></div>') +
      '<div class="btns"><button class="go sec" id="dlBack">Zurück</button></div>');
    $('dlBack').onclick = function () { V.showMenu(); };
    if (!$('dlGo')) return;
    $('dlClear').onclick = function () { A.clearTiles(); V.store('offAreas', 0); showOffline(); };
    $('dlGo').onclick = function () {
      if (!isPlus() && areas >= 1) { gate('Mehr als ein Offline-Gebiet'); return; }
      var lim = isPlus() ? OFF_PLUS : OFF_FREE, b = map.getBounds(), z1 = 14, list;
      while ((list = tilesFor(b, 6, z1)).length > lim && z1 > 10) z1--;
      if (list.length > lim) { if (isPlus()) V.toast('Bereich zu groß, bitte weiter hineinzoomen'); else gate('Größere Offline-Gebiete'); return; }
      V.store('offAreas', areas + 1);
      var src = map.getSource('ofm'), ofm = src && src.tiles && src.tiles[0], urls = [];
      if (src && src.url) urls.push(src.url);
      list.forEach(function (t) {
        function f(tpl) { return tpl.replace('{z}', t[0]).replace('{x}', t[1]).replace('{y}', t[2]); }
        if (ofm) urls.push(f(ofm));
        urls.push(f('https://s3.amazonaws.com/elevation-tiles-prod/terrarium/{z}/{x}/{y}.png'));
        if (t[0] >= 8) urls.push(f('https://tile.waymarkedtrails.org/cycling/{z}/{x}/{y}.png'));
      });
      if (!ofm) { V.toast('Bitte erst die Online-Karte laden (Internet an)'); return; }
      $('dlBox').innerHTML = '<div class="pbox"><div class="t" id="dlT">Lade ' + urls.length + ' Kartenteile ...</div><div class="meter"><i id="dlBar" style="width:0;background:var(--lime)"></i></div><button class="go sec" id="dlStop" style="height:40px;font-size:14px">Abbrechen</button></div>';
      $('dlStop').onclick = function () { A.stopDownload(); };
      A.downloadTiles(JSON.stringify(urls));
    };
  }
  VX.dlProgress = function (done, total, fail, end) {
    var bar = $('dlBar'), t = $('dlT');
    if (bar) bar.style.width = (done / Math.max(1, total) * 100) + '%';
    if (t) t.textContent = end ? (done >= total ? 'Fertig: Bereich ist offline verfügbar' : 'Abgebrochen') + (fail ? ' (' + fail + ' Fehler)' : '') : done + ' von ' + total + ' Kartenteilen';
    if (end && done >= total) hap('success');
  };

  // =====================================================================
  // Menue: Plus, Statistik, Einstellungen fuer Haptik, Design, Pfeil
  // =====================================================================
  var THEMES = [['mint', 'Minze', '#2fd59a'], ['ocean', 'Ozean', '#5aa9ff'], ['forest', 'Wald', '#69db7c'], ['sunset', 'Abendrot', '#ffa94d', 1], ['lavender', 'Lavendel', '#b197fc', 1], ['cherry', 'Kirschblüte', '#f783ac', 1], ['midnight', 'Mitternacht', '#748ffc', 1], ['gold', 'Gold', '#fcc419', 1]];
  function themeFree(t) { return THEMES.some(function (x) { return x[0] === t && !x[3]; }); }
  VX.menuTop = function () {
    return (isPlus() ? '<div class="plusbar" data-act="plus"><div class="ico">' + svg('flag', '#6b4dff', 18) + '</div><div class="grow"><b>Velora Plus ist aktiv</b><span>Alle Funktionen freigeschaltet</span></div></div>'
      : '<div class="plusbar" data-act="plus">' + mascot('thumbs', 56) + '<div class="grow"><b>Velora Plus</b><span>100 Juno-Credits am Tag, Reiseführer, Tagesplaner, Akkuplaner und mehr</span></div><button class="go plus" style="width:auto;padding:0 14px;height:40px;font-size:14px">Ansehen</button></div>') +
      '<div class="linkrow"><button data-act="stats">' + svg('list', 'currentColor', 18) + 'Statistik</button><button id="mSaved">' + svg('flag', 'currentColor', 18) + 'Meine Routen</button>' +
      '<button data-act="dayplan">' + svg('loop', 'currentColor', 18) + 'Tagesplaner</button><button id="mOffline">' + svg('road', 'currentColor', 18) + 'Offline-Karten</button></div>';
  };
  VX.menuNav = function () {
    var plus = isPlus();
    return '<div class="opt"><div class="grow">Vibration<div class="sub">Beim Tippen, Abbiegen (links 2x, rechts 3x) und bei Erfolgen</div></div></div>' +
      '<div class="seg" style="margin-top:0">' + [[0, 'Aus'], [1, 'Leicht'], [2, 'Stark']].map(function (x) { return '<button data-hap="' + x[0] + '" class="' + (S.hap === x[0] ? 'on' : '') + '">' + x[1] + '</button>'; }).join('') + '</div>' +
      '<div class="opt"><div class="grow">Nachtmodus<div class="sub">Dunkles Design, automatisch nach Sonnenuntergang</div></div></div>' +
      '<div class="seg" style="margin-top:0">' + [['off', 'Aus'], ['auto', 'Automatisch'], ['on', 'An']].map(function (x) { return '<button data-night="' + x[0] + '" class="' + (S.night === x[0] ? 'on' : '') + '">' + x[1] + '</button>'; }).join('') + '</div>' +
      '<div class="opt"><div class="grow">Farbe</div></div><div class="hscroll" style="margin-top:0">' + THEMES.map(function (t, i) { return '<button class="chip' + (S.theme === t[0] ? ' on' : '') + '" data-theme="' + t[0] + '"><i style="width:12px;height:12px;border-radius:50%;background:' + t[2] + ';display:inline-block"></i>' + t[1] + (t[3] && !plus ? TAG : '') + '</button>'; }).join('') + '</div>' +
      '<div class="opt"><div class="grow">Navigationspfeil</div></div><div class="hscroll" style="margin-top:0">' + Object.keys(ARROWS).map(function (k) { return '<button class="chip' + (S.arrow === k ? ' on' : '') + '" data-arrow="' + k + '"><span style="width:18px;height:20px;display:inline-block">' + ARROWS[k][1] + '</span>' + ARROWS[k][0] + (plus || k === '3d' || k === 'pfeil' ? '' : TAG) + '</button>'; }).join('') + '</div>' +
      '<div class="opt"><div class="grow">Juno als Reiseführer' + (plus ? '' : TAG) + '<div class="sub">Erzählt unterwegs über Orte, an denen du vorbeifährst</div></div>' + '<div class="sw' + (S.guide && plus ? ' on' : '') + '" id="swGuide"></div></div>' +
      (S.bike === 'road' ? '<div class="opt"><div class="grow">Rennrad: maximale Steigung</div></div><div class="seg" style="margin-top:0">' + [[0, 'Egal'], [0.06, '6 %'], [0.08, '8 %'], [0.1, '10 %']].map(function (x) { return '<button data-maxg="' + x[0] + '" class="' + (S.maxG === x[0] ? 'on' : '') + '">' + x[1] + '</button>'; }).join('') + '</div>' : '');
  };
  VX.menuBound = function () {
    var el = V.sheetEl;
    el.querySelectorAll('[data-mbike]').forEach(function (b) { var k = b.getAttribute('data-mbike'); if (V.BIKES[k].pro && !isPlus()) b.insertAdjacentHTML('beforeend', TAG); });
    el.querySelectorAll('[data-hap]').forEach(function (b) { b.onclick = function () { S.hap = +b.getAttribute('data-hap'); V.saveS(); hap('success'); V.showMenu(); }; });
    el.querySelectorAll('[data-night]').forEach(function (b) { b.onclick = function () { S.night = b.getAttribute('data-night'); V.saveS(); applyLook(); V.showMenu(); }; });
    el.querySelectorAll('[data-theme]').forEach(function (b) { b.onclick = function () { var t = b.getAttribute('data-theme'); if (!themeFree(t) && !gate('Diese Farbe')) return; S.theme = t; V.saveS(); applyLook(); V.showMenu(); }; });
    el.querySelectorAll('[data-arrow]').forEach(function (b) { b.onclick = function () { var a = b.getAttribute('data-arrow'); if (a !== '3d' && a !== 'pfeil' && !gate('Navigationspfeile')) return; S.arrow = a; V.saveS(); setArrowSkin(); V.showMenu(); }; });
    el.querySelectorAll('[data-maxg]').forEach(function (b) { b.onclick = function () { S.maxG = +b.getAttribute('data-maxg'); V.saveS(); V.computeCosts(); V.showMenu(); }; });
    if ($('swGuide')) $('swGuide').onclick = function () { if (!gate('Juno als Reiseführer')) return; S.guide = !S.guide; V.saveS(); this.classList.toggle('on', S.guide); };
    if ($('mSaved')) $('mSaved').onclick = showSaved;
    if ($('mOffline')) $('mOffline').onclick = showOffline;
  };
  VX.back = function () {
    if ($('cele')) { $('cele').querySelector('#celeOk').click(); return true; }
    if ($('vxm')) { closeModal(); return true; }
    var st = V.state;
    if ($('plusx')) { $('pxClose').click(); return true; }
    if (st === 'saved' || st === 'dayplan' || st === 'offline') { V.showMenu(); return true; }
    if (st === 'stats') { V.drawHome(); return true; }
    return false;
  };

  // =====================================================================
  // Aussehen: Farbthema, Nachtmodus, Startbildschirm, weiche Blaetter
  // =====================================================================
  function isNight() { try { var t = V.sunTimes(new Date()), n = Date.now(); return n < t.rise.getTime() - 15 * 60000 || n > t.set.getTime() + 20 * 60000; } catch (e) { return false; } }
  function applyLook() {
    var theme = isPlus() || themeFree(S.theme) ? S.theme : 'mint';
    if (theme === 'mint') document.body.removeAttribute('data-theme'); else document.body.setAttribute('data-theme', theme);
    var night = S.night === 'on' || (S.night === 'auto' && isNight());
    document.body.classList.toggle('night', night);
    var tc = document.querySelector('meta[name=theme-color]'); if (tc) tc.content = night ? '#0f1720' : '#ffffff';
  }
  applyLook(); setInterval(applyLook, 60000);

  // Blatt: Inhalte gleiten weich herein, beim Laden faehrt Velo Rad
  var lastState = V.state, enterT = null;
  new MutationObserver(function () {
    var st = V.state;
    if (st !== lastState && st !== 'nav') {
      V.sheetEl.classList.remove('enter'); void V.sheetEl.offsetWidth; V.sheetEl.classList.add('enter');
      clearTimeout(enterT); enterT = setTimeout(function () { V.sheetEl.classList.remove('enter'); }, 700);
    }
    lastState = st;
    if (st === 'loading' && !V.sheetEl.querySelector('.m-load')) V.sheetEl.insertAdjacentHTML('beforeend', '<div class="m-load">' + mascot('ride', 74) + '</div><div class="skel"></div><div class="skel s2"></div>');
  }).observe(V.sheetEl, { childList: true });

  // Blatt mit dem Finger hoch- und runterziehen
  (function () {
    var y0 = null, dy = 0, el = V.sheetEl;
    el.addEventListener('touchstart', function (e) { if (!e.target.closest('.grab') && !(el.scrollTop <= 0 && e.touches[0].clientY - el.getBoundingClientRect().top < 56)) return; y0 = e.touches[0].clientY; dy = 0; el.classList.add('drag'); }, { passive: true });
    el.addEventListener('touchmove', function (e) { if (y0 == null) return; dy = e.touches[0].clientY - y0; el.style.transform = 'translateY(' + (dy > 0 ? dy : dy / 4) + 'px)'; }, { passive: true });
    el.addEventListener('touchend', function () {
      if (y0 == null) return; y0 = null; el.classList.remove('drag'); el.style.transform = '';
      if (dy > 60) { el.classList.add('mini'); el.classList.remove('tall'); hap('tick'); }
      else if (dy < -50) { if (el.classList.contains('mini')) el.classList.remove('mini'); else el.classList.add('tall'); hap('tick'); }
      el.scrollTop = 0; window.dispatchEvent(new Event('resize'));
    });
  })();

  // Startbildschirm ausblenden
  (function () {
    var sp = $('splash'); if (!sp) return;
    var m = sp.querySelector('.sm'); if (m) m.innerHTML = mascot('wave', 110);
    setTimeout(function () { sp.classList.add('out'); setTimeout(function () { sp.remove(); }, 600); }, TESTER ? 900 : 1100);
  })();

  V.drawHome();
})();
