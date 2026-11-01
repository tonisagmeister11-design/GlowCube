// Testmodus fuer den Browser: Schriften aus der Datei, Demo-Juno ohne Server, Test-Standort
(function () {
  var G = window.__GLYPHS;
  maplibregl.addProtocol('vg', function (p) {
    var k = decodeURIComponent(p.url.replace('vg://g/', ''));
    var b = G[k] || G['NotoSansMedium/' + k.split('/')[1]];
    if (!b) return Promise.reject(new Error('no glyph ' + k));
    var s = atob(b), u = new Uint8Array(s.length);
    for (var i = 0; i < s.length; i++) u[i] = s.charCodeAt(i);
    return Promise.resolve({ data: u.buffer });
  });

  // Juno-Demo: beantwortet lokal, der KI-Server wird nie aufgerufen
  var realFetch = window.fetch.bind(window);
  function demo(body) {
    var v = body.verlauf || [], q = (v.length ? v[v.length - 1].content : '').toLowerCase();
    var t;
    if (/hallo|hi\b|hey/.test(q)) t = 'Hallo, ich bin Juno. Das hier ist der Testmodus: Ich antworte mit Beispieltexten, damit dein Tageslimit nicht verbraucht wird.';
    else if (/essen|restaurant|konoba|hunger/.test(q)) t = 'Im Testmodus suche ich nicht wirklich. In der echten App findet Juno hier echte Restaurants in deiner Nähe und kann dich direkt hinführen.';
    else if (/route|fahr|nach /.test(q)) t = 'In der echten App plane ich jetzt eine Route. Im Tester kannst du dafür ein Ziel suchen oder auf die Karte tippen.';
    else t = 'Testantwort von Juno. Deine Frage war: "' + (v.length ? v[v.length - 1].content : '') + '". In der App auf dem Handy antwortet hier die echte KI.';
    t += '\n\n- Die Ladeanimation siehst du bei jeder Frage etwa 3 Sekunden lang.\n- Es wurde nichts an den Server geschickt.';
    return new Promise(function (res) {
      setTimeout(function () { res(new Response(JSON.stringify({ antwort: t }), { headers: { 'Content-Type': 'application/json' } })); }, 3000);
    });
  }
  window.fetch = function (url, opt) {
    var u = String(url && url.url || url);
    if (!window.__REAL_KI && /workers\.dev\/chat/.test(u)) { try { return demo(JSON.parse(opt.body)); } catch (e) { return demo({}); } }
    return realFetch(url, opt);
  };

  // Chat-Vorschau: Hinweis auf die volle Version (Standort, Internet, Anmeldung)
  if (!window.__REAL_KI) addEventListener('load', function () {
    var d = document.createElement('div');
    d.style.cssText = 'position:fixed;inset:0;z-index:9000;background:rgba(13,31,48,.55);display:flex;align-items:center;justify-content:center;padding:20px;font-family:Inter,system-ui,sans-serif';
    d.innerHTML = '<div style="background:#fff;border-radius:24px;padding:22px;max-width:360px;text-align:center;color:#13263a">' + (window.VX && VX.mascot ? VX.mascot('wave', 120) : '') +
      '<h2 style="margin:8px 0 6px;font-size:21px">Volle Version öffnen</h2><p style="color:#5f7184;font-size:14.5px;line-height:1.45;margin:0 0 16px">Diese Vorschau im Chat darf weder deinen Standort noch das Internet nutzen. Die volle Velora mit echtem Standort, Weltkarte, Anmeldung und Juno läuft hier:</p>' +
      '<a href="https://velora-ki.toni-sagmeister11.workers.dev/" target="_blank" rel="noopener" style="display:block;background:#2fd59a;color:#0d2b3e;font-weight:800;font-size:17px;padding:15px;border-radius:16px;text-decoration:none">Volle Version öffnen</a>' +
      '<button id="pvGo" style="margin-top:12px;border:0;background:none;color:#5f7184;font-size:14px;text-decoration:underline">Trotzdem Vorschau ohne Internet ansehen</button></div>';
    document.body.appendChild(d);
    document.getElementById('pvGo').onclick = function () { d.remove(); };
  });
  addEventListener('load', function () {
    var bar = document.createElement('div');
    bar.textContent = window.__REAL_KI ? 'Testversion: echte Soul-KI' : 'Chat-Vorschau: Demo-Gebiet offline, Soul mit Beispielantworten';
    bar.style.cssText = 'position:fixed;left:50%;transform:translateX(-50%);bottom:calc(6px + env(safe-area-inset-bottom));z-index:9999;background:#0d2b3e;color:#fff;font:600 11px system-ui;padding:4px 10px;border-radius:10px;opacity:.85;pointer-events:none;white-space:nowrap';
    document.body.appendChild(bar);
    setTimeout(function () { bar.remove(); }, 7000);
    if (!window.__REAL_KI) setTimeout(function () { if (window.onGps) onGps(44.0764, 15.1944, 5, 0, -1, -1, 1, 8); }, 1200);
  });
})();
