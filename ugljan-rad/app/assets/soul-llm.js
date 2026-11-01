/* Soul-Sprachmodell: ein echtes KI-Sprachmodell, das direkt auf dem Geraet laeuft (llama.cpp mit Qwen3).
   Android: ueber die App-Bruecke (JNI). Windows: ein mitgeliefertes llama-server-Programm auf dem eigenen PC.
   Spaeter optional: ein eigener Server mit derselben Schnittstelle (localStorage "soulServer").
   Kein Cloudflare, keine fremde KI, keine Kosten. Soul (soul.js) entscheidet, wann das Modell gefragt wird. */
(function () {
  'use strict';
  var V = window.V, VX = window.VX || (window.VX = {}); if (!V) return;
  var A = V.A, D = window.__velLLM || null;
  function $(id) { return document.getElementById(id); }
  function esc(s) { return String(s == null ? '' : s).replace(/[&<>"]/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]; }); }

  var MODELS = {
    std: { n: 'Soul', d: 'Schnell, läuft auf jedem Handy', size: 1107409472, think: true },
    pro: { n: 'Soul Pro', d: 'Klüger und natürlicher, braucht ein starkes Gerät', size: 2497281120, think: false }
  };
  function srvUrl() { try { return (localStorage.getItem('soulServer') || '').replace(/\/+$/, ''); } catch (e) { return ''; } }
  function srvModel() { try { return localStorage.getItem('soulServerModel') || ''; } catch (e) { return ''; } }
  function backend() {
    if (A && A.llmInfo) return 'android';
    if (D) return 'desktop';
    if (srvUrl()) return 'server';
    return null;
  }
  var st = { info: null, loaded: '', loading: null, dl: null, err: '', port: 0 };
  var wants = V.load('soulModel', '');            // gewaehltes Modell ('std' | 'pro')
  var hideCard = false;

  function info() {
    var b = backend();
    if (b === 'android') { try { st.info = JSON.parse(A.llmInfo()); } catch (e) { st.info = null; } }
    else if (b === 'desktop') { try { st.info = D.info(); } catch (e) { st.info = null; } }
    else if (b === 'server') st.info = { ok: true, rec: 'pro', proOk: true, models: { pro: { have: true }, std: { have: true } }, loaded: 'srv' };
    else st.info = null;
    if (st.info && st.info.dl && !st.dl) st.dl = st.info.dl;
    return st.info;
  }
  function installed() {
    var i = st.info || info(); if (!i || !i.ok) return '';
    if (backend() === 'server') return 'srv';
    if (wants && i.models[wants] && i.models[wants].have) return wants;
    return i.models.pro && i.models.pro.have ? 'pro' : i.models.std && i.models.std.have ? 'std' : '';
  }
  function usable() { return !!backend() && !!installed(); }

  // ---------------------------------------------------------------------------------------------
  // Laden, Herunterladen, Ereignisse
  // ---------------------------------------------------------------------------------------------
  var loadWait = [];
  function ensure() {
    var w = installed(); if (!w) return Promise.resolve(false);
    if (backend() === 'server') return Promise.resolve(true);
    if (st.loaded === w) return Promise.resolve(true);
    return new Promise(function (res) {
      loadWait.push(res);
      if (st.loading === w) return;
      st.loading = w; render();
      if (backend() === 'android') A.llmLoad(w);
      else D.load(w).then(function (port) { st.port = port; onEv('load', { ok: !!port, which: w }); }, function () { onEv('load', { ok: false }); });
      setTimeout(function () { if (st.loading === w) { st.loading = null; flush(false); } }, 90000);
    });
  }
  function flush(ok) { var l = loadWait; loadWait = []; l.forEach(function (f) { f(ok); }); }
  var gens = {};
  function onEv(type, d) {
    if (type === 'tok') { var g = gens[d.id]; if (g) { g.text += d.t; g.cb && g.cb(clean(g.text, false)); } return; }
    if (type === 'end') { var g2 = gens[d.id]; if (g2) { delete gens[d.id]; g2.res({ text: g2.text, n: d.n }); } return; }
    if (type === 'load') { st.loading = null; st.loaded = d.ok ? d.which : ''; st.err = d.ok ? '' : 'load'; flush(!!d.ok); render(); return; }
    if (type === 'dl') { st.dl = d; renderProgress(); return; }
    if (type === 'dlEnd') {
      st.dl = null; info();
      if (d.ok) { wants = d.which; V.store('soulModel', wants); V.toast('Soul kann jetzt richtig sprechen'); HOOK('haptic', 'success'); ensure(); }
      else if (d.err !== 'stopped') V.toast(d.err === 'space' ? 'Zu wenig Speicherplatz für das Sprachmodell' : d.err === 'checksum' ? 'Download fehlerhaft, bitte nochmal versuchen' : 'Download unterbrochen. Tippe auf Fortsetzen.');
      render(); return;
    }
  }
  function HOOK(n, a) { try { return VX[n] && VX[n](a); } catch (e) { } }
  if (D && D.onEvent) D.onEvent(onEv);

  function download(w) {
    var i = info(); if (!i) return;
    var need = MODELS[w].size - ((i.models[w] && i.models[w].part) || 0);
    if (i.free && i.free < need + 300e6) { V.toast('Zu wenig Speicherplatz: ' + Math.ceil(need / 1e9 * 10) / 10 + ' GB frei machen'); return; }
    st.dl = { which: w, done: (i.models[w] && i.models[w].part) || 0, total: MODELS[w].size };
    if (backend() === 'android') A.llmDownload(w); else D.download(w);
    render();
  }
  function cancel() { if (backend() === 'android') A.llmCancel(); else if (D) D.cancel(); }
  function remove(w) {
    if (backend() === 'android') A.llmDelete(w); else if (D) D.del(w);
    if (st.loaded === w) st.loaded = '';
    if (wants === w) { wants = ''; V.store('soulModel', ''); }
    info(); render();
  }

  // ---------------------------------------------------------------------------------------------
  // Prompt bauen und Text erzeugen
  // ---------------------------------------------------------------------------------------------
  var SYS = [
    'Du bist Soul, die KI in der Fahrrad-App Velora. Du läufst direkt auf dem Gerät der Person, privat und ohne Cloud.',
    'Stil: locker, warmherzig und natürlich, wie ein guter Freund. Du duzt. Antworte in der Sprache, in der die Person schreibt (meist Deutsch), in korrekter Grammatik.',
    'Länge: meist 1 bis 3 kurze Sätze. Nur bei Erklärungen, Anleitungen oder Tipps ausführlicher, dann mit kurzen Stichpunkten ("- ").',
    'Verwende ein oder zwei passende Emojis. Keine Kosenamen und nimm kein Geschlecht an.',
    'Beantworte genau das, was gefragt wurde, ohne Floskeln wie "Super Frage". Wiederhole nicht die Frage. Stell höchstens eine kurze Rückfrage.',
    'Wenn die Person von Gefühlen oder einem schlechten Tag erzählt: zuerst ehrliches Mitgefühl, dann nachfragen. Schlag nicht sofort eine Radtour vor.',
    'Die Zeile [Info: ...] ist nur Hintergrund (Zeit, Ort, Wetter, was du über die Person weißt). Erwähne daraus nur etwas, wenn es zur Nachricht passt.',
    'Fakten: Erfinde nichts. Stehen [Suchergebnisse] oder [Velora-Wissen] in der Nachricht, übernimm Zahlen, Jahreszahlen und Namen genau von dort. Weißt du etwas nicht sicher, sag das ehrlich.',
    'Velora kann: Routen planen, Rundtouren vorschlagen, Wetter, Orte in der Nähe, Navigation, Fahrten mit Kalorien, Wochenziel, Abzeichen, Offline-Karten, Live-Standort teilen. Erfinde keine anderen App-Funktionen oder Befehle.',
    'Bleib freundlich und jugendfrei. Bei Verletzungen oder Gesundheit gib nur allgemeine Tipps und rate zu einem Arzt.'
  ].join('\n');
  function tok(s) { return Math.ceil(String(s).length / 3.1); }
  function build(o, which, nPredict) {
    var think = MODELS[which] ? MODELS[which].think : false;
    var head = '<|im_start|>system\n' + SYS + '<|im_end|>\n';
    var u = '';
    if (o.ctx) u += '[Info: ' + o.ctx + ']\n';
    if (o.refs && o.refs.length) u += '[Velora-Wissen: ' + o.refs.join(' | ').replace(/\*\*/g, '').slice(0, 1400) + ']\n';
    if (o.search) u += '[Suchergebnisse (' + o.search.src + '): ' + o.search.text.slice(0, 1500) + ']\n';
    u += (u ? '\n' : '') + o.q;
    var tail = '<|im_start|>user\n' + u + '<|im_end|>\n<|im_start|>assistant\n' + (think ? '<think>\n\n</think>\n\n' : '');
    var budget = 2048 - nPredict - 40 - tok(head) - tok(tail), hist = '';
    var h = (o.hist || []).slice(-4);
    for (var i = h.length - 1; i >= 0; i--) {
      var turn = '<|im_start|>user\n' + h[i].u.slice(0, 300) + '<|im_end|>\n<|im_start|>assistant\n' + String(h[i].a).replace(/\*\*/g, '').slice(0, 350) + '<|im_end|>\n';
      if (tok(turn) > budget) break;
      budget -= tok(turn); hist = turn + hist;
    }
    return head + hist + tail;
  }
  function clean(t, final) {
    t = String(t).replace(/<think>[\s\S]*?<\/think>\s*/g, '').replace(/^<think>[\s\S]*$/, '').replace(/<\|im_(start|end)\|>.*$/s, '');
    t = t.replace(/\u{1F6B4}\u200D[\u2640\u2642]\uFE0F?/gu, '\u{1F6B4}').replace(/\u{1F6B5}\u200D[\u2640\u2642]\uFE0F?/gu, '\u{1F6B5}');
    t = t.replace(/^\s*(Soul|Assistant|Assistent)\s*:\s*/i, '').replace(/\[(Info|Velora-Wissen|Suchergebnisse)[^\]]*\]\s*/g, '');
    return final ? t.trim() : t.replace(/^\s+/, '');
  }
  var gid = 0;
  function rawGen(prompt, nPredict, temp, cb) {
    var b = backend(), id = 'g' + (++gid);
    if (b === 'android' || b === 'desktop') {
      return new Promise(function (res) {
        gens[id] = { text: '', cb: cb, res: res };
        if (b === 'android') A.llmGenerate(id, prompt, nPredict, temp); else D.generate(id, prompt, nPredict, temp);
      });
    }
    var base = b === 'server' ? srvUrl() : 'http://127.0.0.1:' + st.port;
    var ctl = window.AbortController ? new AbortController() : null;
    gens[id] = { ctl: ctl };
    return fetch(base + '/completion', {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, signal: ctl && ctl.signal,
      body: JSON.stringify({ prompt: prompt, n_predict: nPredict, temperature: temp, top_p: 0.9, top_k: 40, min_p: 0.05, repeat_penalty: 1.1, repeat_last_n: 64, stream: true, cache_prompt: true, stop: ['<|im_end|>', '<|im_start|>'] })
    }).then(function (r) {
      if (!r.ok || !r.body) throw new Error('http ' + r.status);
      var rd = r.body.getReader(), dec = new TextDecoder(), buf = '', text = '', n = 0;
      function pump() {
        return rd.read().then(function (x) {
          if (x.done) return { text: text, n: n };
          buf += dec.decode(x.value, { stream: true });
          var lines = buf.split('\n'); buf = lines.pop();
          lines.forEach(function (l) {
            if (l.indexOf('data: ') !== 0) return;
            try { var j = JSON.parse(l.slice(6)); if (j.content) { text += j.content; n++; cb && cb(clean(text, false)); } } catch (e) { }
          });
          return pump();
        });
      }
      return pump();
    }).finally(function () { delete gens[id]; });
  }
  function stop() {
    if (backend() === 'android') A.llmStop(); else if (backend() === 'desktop') D.stop();
    Object.keys(gens).forEach(function (k) { if (gens[k].ctl) gens[k].ctl.abort(); });
  }

  var busy = false;
  // o: { q, ctx, refs, search, hist, long }; cb(teilText) fuer Wort-fuer-Wort-Anzeige
  function answer(o, cb) {
    if (busy) return Promise.reject(new Error('busy'));
    return ensure().then(function (ok) {
      if (!ok) throw new Error('not loaded');
      busy = true;
      var which = backend() === 'server' ? (srvModel() || 'pro') : st.loaded, nP = o.long ? 400 : 200;
      var t0 = Date.now();
      var pr = build(o, which, nP); st.lastPrompt = pr;
      return rawGen(pr, nP, 0.6, cb).then(function (r) {
        busy = false;
        var t = clean(r.text, true);
        if (r.n >= nP - 2) { var cut = t.search(/[.!?…][^.!?…]*$/); if (cut > 40) t = t.slice(0, cut + 1); }
        if (VX.track) try { VX.track('soul_llm'); } catch (e) { }
        st.last = { ms: Date.now() - t0, n: r.n };
        if (!t) throw new Error('empty');
        return t;
      }, function (e) { busy = false; throw e; });
    });
  }

  // ---------------------------------------------------------------------------------------------
  // Oberflaeche: Karte oben im Soul-Chat (Download, Fortschritt, Modellwahl)
  // ---------------------------------------------------------------------------------------------
  function gb(b) { return (b / 1e9).toFixed(1).replace('.', ',') + ' GB'; }
  var manage = false;
  function cardHtml() {
    var b = backend(); if (!b || b === 'server') return '';
    var i = info(); if (!i || !i.ok) return '';
    var have = installed(), dl = st.dl;
    if (dl) {
      var p = dl.total ? Math.min(100, dl.done / dl.total * 100) : 0;
      return '<div class="sllm" id="soulLlm"><div class="sllm-t"><b>' + (dl.state === 'check' ? 'Wird geprüft' : MODELS[dl.which].n + ' wird geladen') + '</b><span id="sllmPct">' + Math.floor(p) + ' %</span></div>' +
        '<div class="meter sllm-bar"><i id="sllmBar" style="width:' + p + '%"></i></div>' +
        '<div class="sub" id="sllmSub">' + gb(dl.done) + ' von ' + gb(dl.total) + '. Du kannst Soul währenddessen normal benutzen.</div>' +
        '<div class="btns"><button class="chip sm" data-sllm="cancel">Pausieren</button></div></div>';
    }
    if (have && !manage) {
      return '<div class="sllm on" id="soulLlm"><span class="sllm-dot' + (st.loading ? ' wait' : '') + '"></span><div class="grow"><b>' + MODELS[have].n + '</b> <span class="sub">' + (st.loading ? 'wacht auf ...' : st.loaded ? 'Sprachmodell aktiv, offline auf deinem Gerät' : st.err ? 'konnte nicht starten' : 'bereit') + '</span></div><button class="chip sm" data-sllm="manage">Verwalten</button></div>';
    }
    if (!have && hideCard) return '<div class="sllm mini" id="soulLlm"><button class="chip sm" data-sllm="show">Echtes Sprachmodell laden</button></div>';
    var rec = i.rec || 'std';
    var opts = ['std', 'pro'].map(function (w) {
      var m = MODELS[w], h = i.models[w] && i.models[w].have, part = i.models[w] && i.models[w].part;
      var dis = w === 'pro' && !i.proOk;
      return '<div class="sllm-opt' + (w === rec ? ' rec' : '') + (dis ? ' dis' : '') + '"><div class="grow"><b>' + m.n + '</b>' + (w === rec ? ' <span class="sllm-tag">Empfohlen</span>' : '') +
        '<div class="sub">' + m.d + '. ' + gb(m.size) + (dis ? '. Dein Gerät hat zu wenig Arbeitsspeicher.' : '') + '</div></div>' +
        (h ? (have === w ? '<button class="chip sm" data-sllm="del:' + w + '">Löschen</button>' : '<button class="chip sm" data-sllm="use:' + w + '">Benutzen</button><button class="chip sm" data-sllm="del:' + w + '">Löschen</button>')
          : dis ? '' : '<button class="chip sm' + (w === rec ? ' on' : '') + '" data-sllm="dl:' + w + '">' + (part ? 'Fortsetzen' : 'Laden') + '</button>') + '</div>';
    }).join('');
    return '<div class="sllm big" id="soulLlm">' + (have ? '<div class="sllm-t"><b>Sprachmodell verwalten</b><button class="chip sm" data-sllm="close">Fertig</button></div>' :
      '<div class="sllm-t"><b>Soul kann jetzt richtig sprechen</b><button class="chip sm" data-sllm="hide">Später</button></div>' +
      '<div class="sub">Lade einmalig ein echtes KI-Sprachmodell. Danach versteht Soul jede Frage, plaudert natürlich und läuft direkt auf deinem Gerät: ohne Internet, privat und kostenlos. WLAN empfohlen.</div>') +
      opts + '</div>';
  }
  function render() {
    var el = $('soulLlm'); if (!el) return;
    var h = cardHtml(); if (!h) { el.remove(); return; }
    el.outerHTML = h; bind();
  }
  function renderProgress() {
    var d = st.dl; if (!d) return;
    if (!$('sllmBar')) { render(); return; }
    var p = d.total ? Math.min(100, d.done / d.total * 100) : 0;
    $('sllmBar').style.width = p + '%'; $('sllmPct').textContent = Math.floor(p) + ' %';
    $('sllmSub').textContent = d.state === 'check' ? 'Fast fertig, die Datei wird geprüft ...' : gb(d.done) + ' von ' + gb(d.total) + '. Du kannst Soul währenddessen normal benutzen.';
  }
  function bind() {
    document.querySelectorAll('[data-sllm]').forEach(function (b) {
      b.onclick = function () {
        var a = b.getAttribute('data-sllm'), w = a.split(':')[1];
        HOOK('haptic', 'tap');
        if (a === 'cancel') cancel();
        else if (a === 'manage') { manage = true; render(); }
        else if (a === 'close') { manage = false; render(); }
        else if (a === 'hide') { hideCard = true; render(); }
        else if (a === 'show') { hideCard = false; render(); }
        else if (a.indexOf('dl:') === 0) download(w);
        else if (a.indexOf('use:') === 0) { wants = w; V.store('soulModel', w); manage = false; ensure(); render(); }
        else if (a.indexOf('del:') === 0) { if (confirm(MODELS[w].n + ' löschen? Du kannst es später wieder laden.')) { remove(w); manage = installed() ? manage : false; render(); } }
      };
    });
  }
  // In den Kopf des Soul-Chats einhaengen und beim Oeffnen das Modell aufwecken
  var oldHead = VX.junoHead;
  VX.junoHead = function () {
    var h = (oldHead && oldHead()) || '';
    if (!backend()) return h;
    info();
    if (installed() && !st.loaded && !st.loading) setTimeout(ensure, 50);
    setTimeout(bind, 0);
    return h + cardHtml();
  };
  // Nach dem Start im Hintergrund vorladen, damit die erste Antwort schnell kommt
  setTimeout(function () { if (backend() && installed()) ensure(); }, 6000);

  window.SoulLLM = { model: function () { return backend() === 'server' ? (srvModel() || 'pro') : installed(); }, _ev: onEv, backend: backend, info: info, usable: usable, ensure: ensure, answer: answer, stop: stop, download: download, cancel: cancel, remove: remove, state: st, _build: build };
})();
