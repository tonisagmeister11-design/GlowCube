/* Velora: Anmeldung beim ersten Start, Konto in den Einstellungen, Sprache, Land,
   Verbindung zum Server (Credits, Plus, anonyme Statistik nur mit Zustimmung). */
(function () {
  'use strict';
  var V = window.V, VX = window.VX, I = window.I18N; if (!V || !VX || !I) return;
  var A = V.A, $ = V.$, esc = V.esc;
  var BASE = 'https://velora-ki.toni-sagmeister11.workers.dev', APPID = 'velora-2026';
  var TESTER = !!window.__TESTER, REAL = !!window.__REAL_KI;
  var ACC = V.load('acct', null);
  function save() { V.store('acct', ACC); }
  function mascot(m, s) { return VX.mascot ? VX.mascot(m, s) : ''; }
  function hap(t) { if (VX.haptic) VX.haptic(t); }

  // ---------- Texte der Anmeldung in allen App-Sprachen ----------
  var L = {
    welcome: ['Willkommen bei Velora!', 'Welcome to Velora!', 'Bienvenue sur Velora !', 'Benvenuto su Velora!', '¡Bienvenido a Velora!'],
    hello: ['Ich bin Velo. Wähle zuerst deine Sprache.', 'I am Velo. First, choose your language.', 'Je suis Velo. Choisis d\'abord ta langue.', 'Sono Velo. Prima scegli la tua lingua.', 'Soy Velo. Primero elige tu idioma.'],
    acct: ['Dein Konto', 'Your account', 'Ton compte', 'Il tuo account', 'Tu cuenta'],
    newh: ['Neu hier', 'New here', 'Nouveau', 'Nuovo qui', 'Soy nuevo'], have: ['Ich habe ein Konto', 'I have an account', 'J\'ai un compte', 'Ho un account', 'Tengo cuenta'],
    name: ['Dein Name', 'Your name', 'Ton nom', 'Il tuo nome', 'Tu nombre'], mail: ['E-Mail-Adresse', 'Email address', 'Adresse e-mail', 'Indirizzo e-mail', 'Correo electrónico'],
    pw: ['Passwort (mind. 8 Zeichen)', 'Password (min. 8 characters)', 'Mot de passe (8 caractères min.)', 'Password (min. 8 caratteri)', 'Contraseña (mín. 8 caracteres)'],
    pw1: ['Passwort', 'Password', 'Mot de passe', 'Password', 'Contraseña'],
    next: ['Weiter', 'Continue', 'Continuer', 'Continua', 'Continuar'], login: ['Anmelden', 'Log in', 'Se connecter', 'Accedi', 'Iniciar sesión'],
    country: ['Wo wohnst du?', 'Where do you live?', 'Où habites-tu ?', 'Dove vivi?', '¿Dónde vives?'], search: ['Land suchen', 'Search country', 'Chercher un pays', 'Cerca paese', 'Buscar país'],
    privacy: ['Fast geschafft', 'Almost done', 'Presque fini', 'Quasi fatto', 'Casi listo'],
    terms: ['Ich habe die Datenschutzerklärung gelesen und stimme zu.', 'I have read and agree to the privacy policy.', 'J\'ai lu et j\'accepte la politique de confidentialité.', 'Ho letto e accetto l\'informativa sulla privacy.', 'He leído y acepto la política de privacidad.'],
    anon: ['Anonyme Nutzungsdaten teilen (freiwillig). Hilft, Velora zu verbessern: beliebte Gegenden und benutzte Funktionen, ohne Bezug zu dir.', 'Share anonymous usage data (optional). Helps improve Velora: popular areas and used features, not linked to you.', 'Partager des données anonymes (facultatif) : zones populaires et fonctions utilisées, sans lien avec toi.', 'Condividi dati anonimi (facoltativo): zone popolari e funzioni usate, senza collegamento a te.', 'Compartir datos anónimos (opcional): zonas populares y funciones usadas, sin relación contigo.'],
    readp: ['Datenschutzerklärung lesen', 'Read privacy policy', 'Lire la politique', 'Leggi l\'informativa', 'Leer la política'],
    create: ['Konto erstellen', 'Create account', 'Créer le compte', 'Crea account', 'Crear cuenta'],
    done: ['Los geht\'s', 'Let\'s go', 'C\'est parti', 'Andiamo', 'Vamos'], hi: ['Hallo', 'Hi', 'Salut', 'Ciao', 'Hola'],
    ready: ['Dein Konto ist bereit. Ich feiere ab jetzt jede Fahrt mit dir!', 'Your account is ready. From now on I celebrate every ride with you!', 'Ton compte est prêt. Je fête chaque sortie avec toi !', 'Il tuo account è pronto. Festeggio ogni giro con te!', 'Tu cuenta está lista. ¡Celebraré cada ruta contigo!'],
    noNet: ['Keine Verbindung zum Server. Bitte Internet prüfen.', 'No connection to the server. Please check your internet.', 'Pas de connexion au serveur.', 'Nessuna connessione al server.', 'Sin conexión con el servidor.'],
    guest: ['Ohne Konto ansehen (Vorschau)', 'Look around without account (preview)', 'Voir sans compte (aperçu)', 'Guarda senza account (anteprima)', 'Ver sin cuenta (vista previa)'],
    termsReq: ['Bitte der Datenschutzerklärung zustimmen.', 'Please accept the privacy policy.', 'Merci d\'accepter la politique.', 'Accetta l\'informativa.', 'Acepta la política de privacidad.'],
    delQ: ['Konto wirklich löschen? Das kann man nicht rückgängig machen.', 'Really delete your account? This cannot be undone.', 'Supprimer vraiment le compte ?', 'Eliminare davvero l\'account?', '¿Eliminar la cuenta de verdad?'],
    wkq: ['Wie oft willst du pro Woche fahren?', 'How often do you want to ride per week?', 'Combien de fois par semaine veux-tu rouler ?', 'Quante volte a settimana vuoi pedalare?', '¿Cuántas veces por semana quieres ir en bici?'],
    wkt: ['Schaffst du dein Ziel jede Woche, brennt deine Flamme weiter. Du kannst es später ändern.', 'Reach your goal every week and your flame keeps burning. You can change it later.', 'Atteins ton objectif chaque semaine et ta flamme continue de brûler.', 'Raggiungi il tuo obiettivo ogni settimana e la tua fiamma resta accesa.', 'Cumple tu objetivo cada semana y tu llama seguirá encendida.'],
    rides: ['Fahrten', 'rides', 'sorties', 'giri', 'salidas'], ride1: ['Fahrt', 'ride', 'sortie', 'giro', 'salida'],
    signedIn: ['Angemeldet als', 'Signed in as', 'Connecté en tant que', 'Accesso come', 'Sesión de']
  };
  var LI = { de: 0, en: 1, fr: 2, it: 3, es: 4 };
  function tx(k) { var r = L[k]; return r ? r[LI[I.lang] || 0] : k; }

  // ---------- Laender (ISO 3166) mit Flaggen; Namen kommen in der gewaehlten Sprache vom System ----------
  var CC = 'AF AL DZ AD AO AG AR AM AU AT AZ BS BH BD BB BY BE BZ BJ BT BO BA BW BR BN BG BF BI CV KH CM CA CF TD CL CN CO KM CG CD CR CI HR CU CY CZ DK DJ DM DO EC EG SV GQ ER EE SZ ET FJ FI FR GA GM GE DE GH GR GD GT GN GW GY HT HN HK HU IS IN ID IR IQ IE IL IT JM JP JO KZ KE KI XK KW KG LA LV LB LS LR LY LI LT LU MO MG MW MY MV ML MT MH MR MU MX FM MD MC MN ME MA MZ MM NA NR NP NL NZ NI NE NG KP MK NO OM PK PW PS PA PG PY PE PH PL PT PR QA RO RU RW KN LC VC WS SM ST SA SN RS SC SL SG SK SI SB SO ZA KR SS ES LK SD SR SE CH SY TW TJ TZ TH TL TG TO TT TN TR TM TV UG UA AE GB US UY UZ VU VA VE VN YE ZM ZW'.split(' ');
  function flag(c) { return String.fromCodePoint.apply(null, c.split('').map(function (x) { return 127397 + x.charCodeAt(0); })); }
  function cname(c) { try { return new Intl.DisplayNames([I.lang], { type: 'region' }).of(c); } catch (e) { return c; } }
  function guessCC() { var m = (navigator.language || '').match(/-([A-Z]{2})$/i); return m && CC.indexOf(m[1].toUpperCase()) >= 0 ? m[1].toUpperCase() : 'AT'; }

  // ---------- Server ----------
  function api(path, method, body) {
    var h = { 'Content-Type': 'application/json', 'X-App-Id': APPID };
    if (ACC && ACC.token) h.Authorization = 'Bearer ' + ACC.token;
    return fetch(BASE + path, { method: method || 'GET', headers: h, body: body ? JSON.stringify(body) : undefined }).then(function (r) { return r.json().then(function (j) { j._s = r.status; return j; }); });
  }
  VX.token = function () { return ACC && ACC.token; };
  VX.account = function () { return ACC; };

  // ---------- Anmeldebildschirm ----------
  var F = { mode: 'new', lang: V.load('lang', null) || (LI[(navigator.language || 'de').slice(0, 2)] != null ? (navigator.language || 'de').slice(0, 2) : 'en'), cc: guessCC(), name: '', email: '', pw: '', terms: false, anon: false, wk: 2 };
  function onboard(step) {
    var el = $('onb'); if (!el) { el = document.createElement('div'); el.id = 'onb'; document.body.appendChild(el); }
    var dots = '<div class="ob-dots">' + [0, 1, 2, 3, 4].map(function (i) { return '<i class="' + (i <= step ? 'on' : '') + '"></i>'; }).join('') + '</div>';
    var h = '';
    if (step === 0) {
      h = '<div class="ob-hero">' + mascot('wave', 170) + '</div><h1>' + tx('welcome') + '</h1><p>' + tx('hello') + '</p><div class="ob-langs">' +
        I.LANGS.map(function (l) { return '<button class="ob-lang' + (F.lang === l[0] ? ' on' : '') + '" data-l="' + l[0] + '"><span class="fl">' + flag(l[2]) + '</span>' + l[1] + '</button>'; }).join('') + '</div>' +
        '<button class="ob-go" id="obNext">' + tx('next') + '</button>';
    } else if (step === 1) {
      var reg = F.mode === 'new';
      h = '<div class="ob-hero sm">' + mascot('thumbs', 120) + '</div><h1>' + tx('acct') + '</h1><div class="seg ob-seg"><button data-m="new" class="' + (reg ? 'on' : '') + '">' + tx('newh') + '</button><button data-m="login" class="' + (reg ? '' : 'on') + '">' + tx('have') + '</button></div>' +
        (reg ? '<input class="ob-in" id="obName" autocomplete="name" placeholder="' + tx('name') + '" value="' + esc(F.name) + '">' : '') +
        '<input class="ob-in" id="obMail" type="email" autocomplete="email" inputmode="email" placeholder="' + tx('mail') + '" value="' + esc(F.email) + '">' +
        '<div class="ob-pw"><input class="ob-in" id="obPw" type="password" autocomplete="' + (reg ? 'new-password' : 'current-password') + '" placeholder="' + (reg ? tx('pw') : tx('pw1')) + '"><button id="obEye" aria-label="Passwort zeigen">' + V.svg('view', '#5f7184', 20) + '</button></div>' +
        '<div class="ob-err" id="obErr"></div><button class="ob-go" id="obNext">' + (reg ? tx('next') : tx('login')) + '</button>' +
        (TESTER && !REAL ? '<button class="ob-link" id="obGuest">' + tx('guest') + '</button>' : '');
    } else if (step === 2) {
      h = '<h1 style="margin-top:6px">' + tx('country') + '</h1><input class="ob-in" id="obQ" placeholder="' + tx('search') + '"><div class="ob-list" id="obList"></div><button class="ob-go" id="obNext">' + tx('next') + '</button>';
    } else if (step === 3) {
      h = '<div class="ob-hero sm">' + mascot('torch', 130) + '</div><h1>' + tx('wkq') + '</h1><p>' + tx('wkt') + '</p><div class="wkpick">' +
        [1, 2, 3, 4].map(function (g) { return '<button data-wg="' + g + '" class="' + (F.wk === g ? 'on' : '') + '"><b>' + g + '</b><span>' + (g === 1 ? tx('ride1') : tx('rides')) + '</span></button>'; }).join('') + '</div><button class="ob-go" id="obNext">' + tx('next') + '</button>';
    } else if (step === 4) {
      h = '<div class="ob-hero sm">' + mascot('think', 110) + '</div><h1>' + tx('privacy') + '</h1>' +
        '<label class="ob-chk"><input type="checkbox" id="obTerms"' + (F.terms ? ' checked' : '') + '><span>' + tx('terms') + ' <a href="' + BASE + '/datenschutz" target="_blank" rel="noopener">' + tx('readp') + '</a></span></label>' +
        '<label class="ob-chk"><input type="checkbox" id="obAnon"' + (F.anon ? ' checked' : '') + '><span>' + tx('anon') + '</span></label>' +
        '<div class="ob-err" id="obErr"></div><button class="ob-go" id="obNext">' + tx('create') + '</button>';
    } else {
      h = '<div class="ob-hero big">' + mascot('cheer', 190) + '</div><h1>' + tx('hi') + ' ' + esc(ACC.user.name) + '!</h1><p>' + tx('ready') + '</p><button class="ob-go" id="obNext">' + tx('done') + '</button>';
    }
    el.innerHTML = '<div class="ob-box">' + (step < 5 ? dots : '') + h + '</div>';
    el.querySelector('.ob-box').classList.add('ob-in-anim');
    if (step === 5) hap('celebrate');
    bindStep(step);
  }
  function err(t) { var e = $('obErr'); if (e) { e.textContent = t; hap('warn'); } }
  function busy(on) { var b = $('obNext'); if (b) { b.disabled = on; b.classList.toggle('busy', on); } }
  function bindStep(step) {
    var el = $('onb');
    if (step === 0) {
      el.querySelectorAll('[data-l]').forEach(function (b) { b.onclick = function () { F.lang = b.getAttribute('data-l'); V.store('lang', F.lang); I.set(F.lang); onboard(0); }; });
      $('obNext').onclick = function () { onboard(1); };
    }
    if (step === 1) {
      el.querySelectorAll('[data-m]').forEach(function (b) { b.onclick = function () { keep(); F.mode = b.getAttribute('data-m'); onboard(1); }; });
      $('obEye').onclick = function () { var p = $('obPw'); p.type = p.type === 'password' ? 'text' : 'password'; };
      if ($('obGuest')) $('obGuest').onclick = function () { V.store('guest', 1); close(); };
      function keep() { if ($('obName')) F.name = $('obName').value.trim(); F.email = $('obMail').value.trim(); F.pw = $('obPw').value; }
      $('obNext').onclick = function () {
        keep();
        if (F.mode === 'new') {
          if (F.name.length < 2) return err(tx('name'));
          if (!/^[^\s@]+@[^\s@]+\.[a-z]{2,}$/i.test(F.email)) return err(tx('mail'));
          if (F.pw.length < 8) return err(tx('pw'));
          onboard(2); return;
        }
        busy(true);
        api('/auth/login', 'POST', { email: F.email, password: F.pw }).then(function (j) {
          busy(false);
          if (!j.token) return err(j.fehler || 'Fehler');
          ACC = { token: j.token, user: j.user }; save(); applyUser(); hap('success'); onboard(5);
        }).catch(function () { busy(false); err(tx('noNet')); });
      };
    }
    if (step === 2) {
      var list = CC.map(function (c) { return { c: c, n: cname(c) }; }).sort(function (a, b) { return a.n.localeCompare(b.n, I.lang); });
      function draw(q) {
        q = (q || '').toLowerCase();
        $('obList').innerHTML = list.filter(function (x) { return !q || x.n.toLowerCase().indexOf(q) >= 0 || x.c.toLowerCase() === q; }).map(function (x) { return '<button class="ob-c' + (x.c === F.cc ? ' on' : '') + '" data-c="' + x.c + '"><span class="fl">' + flag(x.c) + '</span>' + esc(x.n) + '</button>'; }).join('');
        $('obList').querySelectorAll('[data-c]').forEach(function (b) { b.onclick = function () { F.cc = b.getAttribute('data-c'); hap('tick'); draw($('obQ').value); }; });
        var on = $('obList').querySelector('.on'); if (on && !q) on.scrollIntoView({ block: 'center' });
      }
      draw('');
      $('obQ').oninput = function () { draw(this.value); };
      $('obNext').onclick = function () { onboard(3); };
    }
    if (step === 3) {
      el.querySelectorAll('[data-wg]').forEach(function (b) { b.onclick = function () { F.wk = +b.getAttribute('data-wg'); hap('click'); onboard(3); }; });
      $('obNext').onclick = function () { V.S.wkGoal = F.wk; V.saveS(); onboard(4); };
    }
    if (step === 4) {
      $('obNext').onclick = function () {
        F.terms = $('obTerms').checked; F.anon = $('obAnon').checked;
        if (!F.terms) return err(tx('termsReq'));
        busy(true);
        api('/auth/register', 'POST', { name: F.name, email: F.email, password: F.pw, country: F.cc, lang: F.lang, terms: true, anon: F.anon }).then(function (j) {
          busy(false);
          if (!j.token) { if (j._s === 409) { F.mode = 'login'; onboard(1); setTimeout(function () { err(j.fehler); }, 50); return; } return err(j.fehler || 'Fehler'); }
          F.pw = ''; ACC = { token: j.token, user: j.user }; save(); applyUser(); hap('success'); onboard(5);
        }).catch(function () { busy(false); err(tx('noNet')); });
      };
    }
    if (step === 5) $('obNext').onclick = close;
  }
  function close() {
    var el = $('onb'); if (!el) return;
    el.classList.add('out'); setTimeout(function () { el.remove(); }, 350);
    V.drawHome();
    if (ACC && !V.S.wkGoal && VX.askWeekGoal) setTimeout(VX.askWeekGoal, 600);
  }

  // ---------- Konto anwenden und mit dem Server abgleichen ----------
  function applyUser() {
    if (!ACC) return;
    var u = ACC.user || {};
    if (u.lang && u.lang !== I.lang) { V.store('lang', u.lang); I.set(u.lang); }
    var P = V.load('plus', { on: false });
    if (!!P.on !== !!u.plus && VX.setPlusQuiet) VX.setPlusQuiet(u.plus);
  }
  function refresh() {
    if (!ACC) return;
    api('/me').then(function (j) {
      if (j._s === 401) { ACC = null; save(); onboard(0); return; }
      if (!j.user) return;
      ACC.user = j.user; save(); applyUser();
      VX.srvCredits = { left: j.left, max: j.max };
    }).catch(function () {});
  }
  VX.onPlus = function (on) { if (ACC) api('/me', 'POST', { plus: on }).then(function (j) { if (j.user) { ACC.user = j.user; save(); } refresh(); }).catch(function () {}); };

  // ---------- Soul: Credits vom Server ----------
  var oldUsed = VX.junoUsed;
  VX.junoUsed = function (cr, left) {
    if (left != null) VX.srvCredits = { left: left, max: (VX.srvCredits && VX.srvCredits.max) || (ACC && ACC.user.plus ? 100 : 10) };
    track('juno');
    return oldUsed && oldUsed(cr, left);
  };

  // ---------- Anonyme Nutzung (nur mit Zustimmung) ----------
  var FC = V.load('featCnt', { d: '', f: {} });
  function track(n) { FC.f[n] = (FC.f[n] || 0) + 1; V.store('featCnt', FC); }
  VX.track = track;
  function sendFeats() {
    var today = new Date().toISOString().slice(0, 10);
    if (!ACC || !ACC.user.consent || !ACC.user.consent.anon) return;
    if (FC.d && FC.d !== today && Object.keys(FC.f).length) api('/stats/feat', 'POST', { f: FC.f }).catch(function () {});
    if (FC.d !== today) { FC = { d: today, f: FC.d === today ? FC.f : {} }; V.store('featCnt', FC); }
  }
  function wrap(name, tag) { var o = VX[name]; VX[name] = function (a, b, c) { track(typeof tag === 'function' ? tag(a) : tag); return o && o.apply(this, arguments); }; }
  wrap('routeBound', 'route'); wrap('arrived', 'ziel_erreicht'); wrap('loops', 'rundtour'); wrap('rideSaved', 'fahrt');
  wrap('act', function (a) { return a; });
  var oldRide = VX.rideSaved;
  VX.rideSaved = function (ride) {
    var r = oldRide && oldRide(ride);
    if (ACC && ACC.user.consent && ACC.user.consent.anon && ride && ride.trk && ride.trk.length) {
      var cells = {}; ride.trk.forEach(function (p) { cells[(Math.floor(p[0] / 0.02) * 0.02).toFixed(2) + ',' + (Math.floor(p[1] / 0.02) * 0.02).toFixed(2)] = 1; });
      api('/stats/ride', 'POST', { cells: Object.keys(cells).slice(0, 300) }).catch(function () {});
    }
    return r;
  };

  // ---------- Einstellungen: Konto, Sprache, Land ----------
  var oldTop = VX.menuTop;
  VX.menuTop = function () {
    var u = ACC && ACC.user, h = '';
    if (u) {
      h = '<div class="acc-card"><div class="acc-av">' + esc((u.name || '?').slice(0, 1).toUpperCase()) + '</div><div class="grow"><b>' + esc(u.name) + '</b><span>' + esc(u.email) + '</span></div><span class="fl big">' + (u.country ? flag(u.country) : '') + '</span></div>';
    } else {
      h = '<div class="acc-card" id="accLogin"><div class="acc-av">?</div><div class="grow"><b>' + tx('acct') + '</b><span>' + tx('login') + '</span></div></div>';
    }
    h += '<h3>' + I.t('Sprache') + '</h3><div class="hscroll" style="flex-wrap:wrap;overflow:visible">' + I.LANGS.map(function (l) { return '<button class="chip' + (I.lang === l[0] ? ' on' : '') + '" data-lang="' + l[0] + '"><span class="fl">' + flag(l[2]) + '</span>' + l[1] + '</button>'; }).join('') + '</div>';
    if (u) h += '<div class="linkrow"><button id="accCountry"><span class="fl">' + (u.country ? flag(u.country) : '') + '</span>' + esc(u.country ? cname(u.country) : I.t('Land')) + '</button><button id="accOut">' + I.t('Abmelden') + '</button></div>' +
      '<div class="opt"><div class="grow">' + I.t('Anonyme Nutzungsdaten teilen') + '</div><div class="sw' + (u.consent && u.consent.anon ? ' on' : '') + '" id="swAnon"></div></div>';
    return h + (oldTop ? oldTop() : '');
  };
  var oldBound = VX.menuBound;
  VX.menuBound = function () {
    if (oldBound) oldBound();
    V.sheetEl.querySelectorAll('[data-lang]').forEach(function (b) { b.onclick = function () { var l = b.getAttribute('data-lang'); V.store('lang', l); if (ACC) api('/me', 'POST', { lang: l }).then(function (j) { if (j.user) { ACC.user = j.user; save(); } }).catch(function () {}); setTimeout(function () { location.reload(); }, 150); }; });
    if ($('accLogin')) $('accLogin').onclick = function () { onboard(0); };
    if ($('accOut')) $('accOut').onclick = function () { ACC = null; save(); V.store('guest', 0); onboard(0); };
    if ($('swAnon')) $('swAnon').onclick = function () { var on = !this.classList.contains('on'); this.classList.toggle('on', on); api('/me', 'POST', { anon: on }).then(function (j) { if (j.user) { ACC.user = j.user; save(); } }).catch(function () {}); };
    if ($('accCountry')) $('accCountry').onclick = countryPicker;
    var del = document.createElement('div'); del.className = 'btns';
    if (ACC) { del.innerHTML = '<button class="go sec" id="accDel" style="color:#e5463c">' + I.t('Konto löschen') + '</button>'; V.sheetEl.appendChild(del); $('accDel').onclick = deleteAcc; }
  };
  function countryPicker() {
    var m = document.createElement('div'); m.id = 'vxm';
    m.innerHTML = '<div class="cbox mbox"><h2>' + tx('country') + '</h2><input class="ob-in" id="cpQ" placeholder="' + tx('search') + '"><div class="ob-list" id="cpL" style="max-height:52vh"></div></div>';
    document.body.appendChild(m); m.onclick = function (e) { if (e.target === m) m.remove(); };
    var list = CC.map(function (c) { return { c: c, n: cname(c) }; }).sort(function (a, b) { return a.n.localeCompare(b.n, I.lang); });
    function draw(q) {
      q = (q || '').toLowerCase();
      $('cpL').innerHTML = list.filter(function (x) { return !q || x.n.toLowerCase().indexOf(q) >= 0; }).map(function (x) { return '<button class="ob-c' + (x.c === ACC.user.country ? ' on' : '') + '" data-c="' + x.c + '"><span class="fl">' + flag(x.c) + '</span>' + esc(x.n) + '</button>'; }).join('');
      $('cpL').querySelectorAll('[data-c]').forEach(function (b) { b.onclick = function () { var c = b.getAttribute('data-c'); api('/me', 'POST', { country: c }).then(function (j) { if (j.user) { ACC.user = j.user; save(); } V.showMenu(); }).catch(function () {}); ACC.user.country = c; save(); m.remove(); hap('success'); V.showMenu(); }; });
    }
    draw(''); $('cpQ').oninput = function () { draw(this.value); };
  }
  function deleteAcc() {
    var m = document.createElement('div'); m.id = 'vxm';
    m.innerHTML = '<div class="cbox mbox"><div style="display:flex;gap:12px;align-items:center">' + mascot('sad', 80) + '<div class="grow"><h2>' + I.t('Konto löschen') + '</h2><div class="sub">' + tx('delQ') + '</div></div></div><div class="btns"><button class="go sec" id="dNo">' + I.t('Abbrechen') + '</button><button class="go red" id="dYes">' + I.t('Löschen') + '</button></div></div>';
    document.body.appendChild(m);
    $('dNo').onclick = function () { m.remove(); };
    $('dYes').onclick = function () { api('/me', 'DELETE').then(function () {}).catch(function () {}); ACC = null; save(); m.remove(); onboard(0); };
  }

  // ---------- Start ----------
  I.set(V.load('lang', null) || (ACC && ACC.user && ACC.user.lang) || F.lang);
  if (ACC) { applyUser(); refresh(); sendFeats(); if (!V.S.wkGoal && VX.askWeekGoal) setTimeout(VX.askWeekGoal, 1500); }
  else if (!(TESTER && !REAL && V.load('guest', 0))) setTimeout(function () { onboard(0); }, TESTER ? 950 : 1150);
  V.drawHome();
})();
