/* Velo in 3D: der Fuchs als echtes 3D-Modell (Three.js) mit 20 Animationen.
   - Ein einziger WebGL-Renderer zeichnet alle sichtbaren Fuechse und kopiert das Bild in ihre eigenen Leinwaende
     (spart Speicher und Akku, laeuft auch auf schwachen Geraeten).
   - Jede Stelle, die mascot(...) benutzt (Element .fxw mit data-pose), bekommt automatisch den 3D-Fuchs.
   - Ohne WebGL oder mit "Fuchs in 3D" aus bleibt der bisherige 2D-Fuchs (SVG).
   Animationen: idle, wave, cheer, thumbs, dance, think, sad, sleep, celebrate, ride, run, spin, laugh, surprised,
   love, flex, trophy, torch, point, wink. */
(function () {
  'use strict';
  var T = window.THREE;
  var POSES = ['idle', 'wave', 'cheer', 'thumbs', 'dance', 'think', 'sad', 'sleep', 'celebrate', 'ride', 'run', 'spin', 'laugh', 'surprised', 'love', 'flex', 'trophy', 'torch', 'point', 'wink'];
  var API = { poses: POSES, ok: false, enabled: true };
  window.Fox3D = API;
  if (!T) return;

  var store = { get: function (k, d) { try { var v = localStorage.getItem(k); return v == null ? d : JSON.parse(v); } catch (e) { return d; } }, set: function (k, v) { try { localStorage.setItem(k, JSON.stringify(v)); } catch (e) { } } };
  API.enabled = store.get('fox3d', true) !== false;
  var reduce = window.matchMedia && matchMedia('(prefers-reduced-motion: reduce)').matches;

  // ---------------------------------------------------------------------------------------------
  // Renderer (einer fuer alle)
  // ---------------------------------------------------------------------------------------------
  var MAXPX = 640, renderer = null;
  try {
    var cv = document.createElement('canvas'); cv.width = MAXPX; cv.height = MAXPX;
    renderer = new T.WebGLRenderer({ canvas: cv, alpha: true, antialias: true, preserveDrawingBuffer: true, powerPreference: 'low-power' });
    renderer.setPixelRatio(1); renderer.setSize(MAXPX, MAXPX, false);
    renderer.setClearColor(0x000000, 0);
    renderer.outputColorSpace = T.SRGBColorSpace;
    renderer.setScissorTest(true);
  } catch (e) { renderer = null; }
  if (!renderer) return;
  API.ok = true;
  // Grafikkontext verloren (z. B. App lange im Hintergrund): 2D-Fuchs zeigen, danach wieder 3D
  renderer.domElement.addEventListener('webglcontextlost', function (e) { e.preventDefault(); API.ok = false; unmountAll(); }, false);
  renderer.domElement.addEventListener('webglcontextrestored', function () { API.ok = true; scan(document); }, false);

  var scene = new T.Scene();
  var cam = new T.PerspectiveCamera(26, 1, 0.1, 50);
  scene.add(new T.HemisphereLight(0xfff6ea, 0x7a90b8, 1.35));
  var key = new T.DirectionalLight(0xffffff, 1.9); key.position.set(-2.5, 4, 5); scene.add(key);
  var rim = new T.DirectionalLight(0xbfe3ff, 1.1); rim.position.set(3, 2.5, -3.5); scene.add(rim);
  var fill = new T.DirectionalLight(0xffe2c4, 0.45); fill.position.set(3, 0.5, 4); scene.add(fill);

  // ---------------------------------------------------------------------------------------------
  // Materialien und Bausteine
  // ---------------------------------------------------------------------------------------------
  function mat(c, o) { o = o || {}; return new T.MeshStandardMaterial({ color: c, roughness: o.r != null ? o.r : 0.62, metalness: o.m || 0, emissive: o.e || 0x000000, emissiveIntensity: o.ei || 1, transparent: !!o.tr, opacity: o.op != null ? o.op : 1 }); }
  var M = {
    orange: mat(0xf47c20), orange2: mat(0xe5651a), cream: mat(0xfde9d2, { r: 0.75 }), dark: mat(0x2b1a12, { r: 0.35 }), paw: mat(0x4a2c1d, { r: 0.55 }),
    blue: mat(0x3b82e6, { r: 0.45 }), blueDark: mat(0x1d2f4f, { r: 0.5 }), white: mat(0xffffff, { r: 0.3 }), eye: mat(0x1b1210, { r: 0.12 }),
    pink: mat(0xff8aa0, { r: 0.5 }), blush: mat(0xff7b8f, { r: 0.8, tr: true, op: 0.55 }), gold: mat(0xffc531, { r: 0.25, m: 0.55 }),
    goldDark: mat(0xd68f00, { r: 0.3, m: 0.4 }), metal: mat(0x9aa8b6, { r: 0.35, m: 0.6 }), tire: mat(0x26303a, { r: 0.8 }),
    wood: mat(0x8a5a32), flame: mat(0xffb020, { e: 0xff7a00, ei: 1.6, r: 1 }), flame2: mat(0xfff1a8, { e: 0xffe066, ei: 1.8, r: 1 }),
    heart: mat(0xff4d6d, { e: 0xff2a55, ei: 0.35, r: 0.4 }), zzz: mat(0x6c7ea3, { r: 0.6 }), tear: mat(0x8fd3ff, { r: 0.1, tr: true, op: 0.85 }),
    green: mat(0x2fd59a, { r: 0.4 })
  };
  var OUT = new T.MeshBasicMaterial({ color: 0x2b1a12, side: T.BackSide });
  var G = {};
  function sphere(r, ws, hs) { var k = 's' + r + '_' + (ws || 28); return G[k] || (G[k] = new T.SphereGeometry(r, ws || 28, hs || 20)); }
  function mesh(geo, m, out) {
    var me = new T.Mesh(geo, m);
    if (out) { var o = new T.Mesh(geo, OUT); o.scale.setScalar(1 + out); me.add(o); }
    return me;
  }
  function blob(r, m, sx, sy, sz, out) { var me = mesh(sphere(r), m, out == null ? 0.07 : out); me.scale.set(sx || 1, sy || 1, sz || 1); return me; }
  function capsule(r, len, m, out) { var g = new T.CapsuleGeometry(r, len, 8, 18); var me = mesh(g, m, out == null ? 0.1 : out); return me; }
  function grp(x, y, z) { var g = new T.Group(); g.position.set(x || 0, y || 0, z || 0); return g; }

  // ---------------------------------------------------------------------------------------------
  // Der Fuchs
  // ---------------------------------------------------------------------------------------------
  var F = {};
  var root = grp(); scene.add(root); F.root = root;
  // Schatten am Boden
  (function () {
    var c = document.createElement('canvas'); c.width = c.height = 128; var g = c.getContext('2d');
    var gr = g.createRadialGradient(64, 64, 4, 64, 64, 62); gr.addColorStop(0, 'rgba(13,43,62,.38)'); gr.addColorStop(1, 'rgba(13,43,62,0)');
    g.fillStyle = gr; g.fillRect(0, 0, 128, 128);
    var tx = new T.CanvasTexture(c); tx.colorSpace = T.SRGBColorSpace;
    F.shadow = new T.Mesh(new T.PlaneGeometry(1.7, 1.0), new T.MeshBasicMaterial({ map: tx, transparent: true, depthWrite: false }));
    F.shadow.rotation.x = -Math.PI / 2; F.shadow.position.y = 0.005; scene.add(F.shadow);
  })();
  var body = grp(0, 0, 0); root.add(body); F.body = body;           // Huefte am Boden-Ursprung, alles haengt daran
  var hips = grp(0, 0.92, 0); body.add(hips); F.hips = hips;

  // Proportionen wie eine Comicfigur: grosser Kopf, kurzer runder Koerper, kurze Beine
  hips.position.y = 0.48;
  // Beine
  function leg(side) {
    var L = grp(side * 0.2, 0, 0.02); hips.add(L);
    var thigh = capsule(0.15, 0.1, M.orange); thigh.position.y = -0.15; L.add(thigh);
    var boot = blob(0.17, M.paw, 1.05, 0.72, 1.4); boot.position.set(0, -0.36, 0.07); L.add(boot);
    return L;
  }
  F.legL = leg(1); F.legR = leg(-1);

  // Rumpf (Trikot)
  var torso = grp(0, 0, 0); hips.add(torso); F.torso = torso;
  var jersey = blob(0.52, M.blue, 1.0, 0.92, 0.86); jersey.position.y = 0.38; torso.add(jersey);
  var belt = new T.Mesh(new T.TorusGeometry(0.42, 0.035, 10, 40), M.blueDark); belt.rotation.x = Math.PI / 2; belt.position.y = 0.1; belt.scale.set(1, 0.86, 1); torso.add(belt);
  // Rad-Logo auf der Brust
  var logo = grp(0, 0.47, 0.43); torso.add(logo);
  var wl = new T.Mesh(new T.TorusGeometry(0.07, 0.017, 8, 24), M.white); wl.position.x = -0.095; logo.add(wl);
  var wr = wl.clone(); wr.position.x = 0.095; logo.add(wr);
  var bar = new T.Mesh(new T.CapsuleGeometry(0.014, 0.16, 4, 8), M.white); bar.rotation.z = Math.PI / 2; bar.position.y = 0.04; logo.add(bar);
  logo.rotation.x = -0.1;
  // Kragen
  var collar = new T.Mesh(new T.TorusGeometry(0.2, 0.05, 10, 30), M.blueDark); collar.rotation.x = Math.PI / 2; collar.position.y = 0.8; torso.add(collar);

  // Arme: Schulter -> Ellbogen -> Pfote
  var PAWY = -0.27;
  function arm(side) {
    var S = grp(side * 0.44, 0.66, 0); torso.add(S);
    var sleeve = blob(0.16, M.blue, 1, 1.05, 1, 0.06); sleeve.position.y = -0.03; S.add(sleeve);
    var up = capsule(0.11, 0.14, M.orange); up.position.y = -0.17; S.add(up);
    var E = grp(0, -0.3, 0); S.add(E);
    var low = capsule(0.105, 0.1, M.orange); low.position.y = -0.1; E.add(low);
    var paw = blob(0.14, M.paw, 1, 1, 1); paw.position.y = PAWY; E.add(paw);
    var thumb = capsule(0.05, 0.1, M.paw, 0.15); thumb.position.set(-side * 0.02, PAWY - 0.13, 0.03); thumb.visible = false; E.add(thumb);
    return { S: S, E: E, paw: paw, thumb: thumb };
  }
  F.armL = arm(1); F.armR = arm(-1);

  // Schwanz: Kette aus Gliedern fuer weiches Nachschwingen
  var tailBase = grp(0, 0.12, -0.36); hips.add(tailBase); F.tail = [];
  (function () {
    var parent = tailBase, sizes = [[0.16, M.orange, 0.33], [0.22, M.orange, 0.36], [0.25, M.orange, 0.34], [0.19, M.cream, 0]];
    sizes.forEach(function (s, i) {
      var seg = grp(0, i ? sizes[i - 1][2] : 0, 0); parent.add(seg);
      var b = blob(s[0], s[1], 0.95, 1.25, 0.95); b.position.y = s[0] * 0.75; seg.add(b);
      F.tail.push(seg); parent = seg;
    });
  })();

  // Kopf (Schaedelmitte bei y 0,5 im Kopf-Raum)
  var neck = grp(0, 0.78, 0); torso.add(neck);
  var head = grp(0, 0, 0); neck.add(head); F.head = head;
  var skull = blob(0.62, M.orange, 1.06, 0.93, 0.95); skull.position.y = 0.5; head.add(skull);
  var face = blob(0.5, M.cream, 1.0, 0.62, 0.8, 0.04); face.position.set(0, 0.3, 0.2); head.add(face);
  // Wangenfell seitlich
  [-1, 1].forEach(function (sd) {
    var fluff = mesh(new T.ConeGeometry(0.18, 0.42, 20), M.cream, 0.08); fluff.position.set(sd * 0.6, 0.28, 0.06); fluff.rotation.z = sd * -1.95; fluff.scale.z = 0.8; head.add(fluff);
  });
  var snout = blob(0.21, M.cream, 1.05, 0.78, 1.1, 0.06); snout.position.set(0, 0.3, 0.55); head.add(snout);
  var nose = blob(0.075, M.dark, 1.2, 0.85, 0.9, 0); nose.position.set(0, 0.36, 0.78); head.add(nose);
  var noseHi = blob(0.022, M.white, 1, 1, 1, 0); noseHi.position.set(-0.025, 0.39, 0.845); head.add(noseHi);
  // Wangenroete (bei love/laugh)
  F.blush = [];
  [-1, 1].forEach(function (sd) { var b = blob(0.09, M.blush, 1.3, 0.75, 0.4, 0); b.position.set(sd * 0.37, 0.3, 0.5); b.visible = false; head.add(b); F.blush.push(b); });

  // Augen: grosses Weiss, Pupille mit Glanz; froehlich (Bogen), zu (Strich)
  F.eyes = [];
  [-1, 1].forEach(function (sd) {
    var E = grp(sd * 0.22, 0.52, 0.52); head.add(E);
    var open = grp(); E.add(open);
    var white = blob(0.13, M.white, 0.85, 1.1, 0.5, 0.1); open.add(white);
    var pup = grp(0, 0, 0.04); open.add(pup);
    var ball = blob(0.078, M.eye, 0.9, 1.08, 0.5, 0); pup.add(ball);
    var hi = blob(0.027, M.white, 1, 1, 1, 0); hi.position.set(0.028, 0.035, 0.035); pup.add(hi);
    var hi2 = blob(0.012, M.white, 1, 1, 1, 0); hi2.position.set(-0.025, -0.03, 0.035); pup.add(hi2);
    var happy = new T.Mesh(new T.TorusGeometry(0.075, 0.022, 8, 18, Math.PI), M.eye); happy.position.set(0, -0.02, 0.03); happy.visible = false; E.add(happy);
    var closed = new T.Mesh(new T.CapsuleGeometry(0.02, 0.11, 4, 8), M.eye); closed.rotation.z = Math.PI / 2; closed.position.z = 0.03; closed.visible = false; E.add(closed);
    var brow = new T.Mesh(new T.CapsuleGeometry(0.02, 0.1, 4, 8), M.dark); brow.rotation.z = Math.PI / 2; brow.position.set(0, 0.19, 0.02); brow.visible = false; E.add(brow);
    E.rotation.y = sd * 0.3;
    F.eyes.push({ g: E, open: open, pup: pup, happy: happy, closed: closed, brow: brow, side: sd });
  });
  // Mund
  var mouth = grp(0, 0.13, 0.57); head.add(mouth); F.mouth = {};
  F.mouth.smile = new T.Mesh(new T.TorusGeometry(0.085, 0.02, 8, 20, Math.PI), M.dark); F.mouth.smile.rotation.z = Math.PI; mouth.add(F.mouth.smile);
  F.mouth.frown = new T.Mesh(new T.TorusGeometry(0.075, 0.02, 8, 20, Math.PI), M.dark); F.mouth.frown.position.y = -0.06; mouth.add(F.mouth.frown);
  F.mouth.open = grp(); mouth.add(F.mouth.open);
  var mo = blob(0.105, M.dark, 1.1, 0.85, 0.45, 0); mo.position.set(0, -0.03, -0.02); F.mouth.open.add(mo);
  var tongue = blob(0.062, M.pink, 1.2, 0.6, 0.6, 0); tongue.position.set(0, -0.08, 0.02); F.mouth.open.add(tongue);
  F.mouth.o = blob(0.05, M.dark, 0.9, 1.15, 0.5, 0); F.mouth.o.position.y = -0.03; mouth.add(F.mouth.o);
  F.mouth.o.visible = F.mouth.frown.visible = F.mouth.open.visible = false;
  mouth.rotation.x = -0.3;

  // Ohren (stehen durch die Luftschlitze des Helms)
  function ear(sd) {
    var P = grp(sd * 0.38, 0.95, -0.06); head.add(P);
    var outer = mesh(new T.ConeGeometry(0.22, 0.58, 24), M.orange, 0.08); outer.position.y = 0.25; P.add(outer);
    var inner = mesh(new T.ConeGeometry(0.13, 0.36, 20), M.cream, 0); inner.position.set(0, 0.2, 0.1); inner.scale.z = 0.5; P.add(inner);
    var tip = mesh(new T.ConeGeometry(0.09, 0.19, 20), M.dark, 0); tip.position.y = 0.46; P.add(tip);
    P.rotation.z = sd * -0.32;
    return P;
  }
  F.earL = ear(1); F.earR = ear(-1);

  // Helm (sitzt hoch und leicht nach hinten gekippt, damit Augen und Stirn frei bleiben)
  var helmet = grp(0, 0.68, -0.05); head.add(helmet);
  var shell = mesh(new T.SphereGeometry(0.66, 36, 18, 0, Math.PI * 2, 0, Math.PI * 0.5), M.blue, 0.05); shell.scale.set(1.04, 0.8, 1.0); helmet.add(shell);
  [-0.24, 0, 0.24].forEach(function (x) { var v = new T.Mesh(new T.CapsuleGeometry(0.035, 0.24, 4, 10), M.blueDark); v.position.set(x, 0.45, 0.1); v.rotation.x = 1.2; helmet.add(v); });
  var stripe = new T.Mesh(new T.TorusGeometry(0.66, 0.022, 8, 48, Math.PI), M.white); stripe.rotation.y = Math.PI / 2; stripe.scale.set(1, 0.8, 1.04); helmet.add(stripe);
  var rimg = new T.Mesh(new T.TorusGeometry(0.66, 0.03, 8, 48), M.blueDark); rimg.rotation.x = Math.PI / 2; rimg.scale.set(1.04, 1.0, 1); helmet.add(rimg);
  var brim = mesh(new T.CylinderGeometry(0.22, 0.28, 0.05, 24, 1, false, -0.9, 1.8), M.blueDark, 0); brim.position.set(0, 0.02, 0.6); brim.rotation.x = 0.2; helmet.add(brim);
  helmet.rotation.x = -0.2;

  // ---------------------------------------------------------------------------------------------
  // Requisiten
  // ---------------------------------------------------------------------------------------------
  var props = {};
  // Fahrrad (Kinderrad-Groesse, passend zur Figur); Laengsachse = lokale x-Achse
  (function () {
    var B = grp(0, 0, 0.05); root.add(B); B.visible = false; props.bike = B; B.scale.setScalar(0.9);
    function wheel(x) {
      var W = grp(x, 0.36, 0); B.add(W);
      var tire = new T.Mesh(new T.TorusGeometry(0.33, 0.05, 10, 40), M.tire); W.add(tire);
      var rimm = new T.Mesh(new T.TorusGeometry(0.29, 0.016, 6, 36), M.metal); W.add(rimm);
      for (var k = 0; k < 6; k++) { var sp = new T.Mesh(new T.CylinderGeometry(0.007, 0.007, 0.58, 4), M.metal); sp.rotation.z = k * Math.PI / 6; W.add(sp); }
      var hub = new T.Mesh(new T.CylinderGeometry(0.04, 0.04, 0.08, 12), M.metal); hub.rotation.x = Math.PI / 2; W.add(hub);
      return W;
    }
    props.wheelF = wheel(0.62); props.wheelB = wheel(-0.62);
    function tube(a, b, r, m) {
      var va = new T.Vector3(a[0], a[1], a[2]), vb = new T.Vector3(b[0], b[1], b[2]), len = va.distanceTo(vb);
      var c = new T.Mesh(new T.CylinderGeometry(r, r, len, 10), m); c.position.copy(va).add(vb).multiplyScalar(0.5);
      c.quaternion.setFromUnitVectors(new T.Vector3(0, 1, 0), vb.clone().sub(va).normalize()); B.add(c); return c;
    }
    var bb = [0, 0.36, 0], seat = [-0.12, 0.92, 0], head2 = [0.45, 0.9, 0];
    tube([-0.62, 0.36, 0], bb, 0.04, M.green); tube(bb, seat, 0.04, M.green); tube(seat, [-0.62, 0.36, 0], 0.035, M.green);
    tube(bb, head2, 0.045, M.green); tube(seat, head2, 0.04, M.green); tube(head2, [0.62, 0.36, 0], 0.035, M.green);
    tube([0.45, 0.9, 0], [0.42, 1.1, 0], 0.035, M.metal);
    tube([0.42, 1.1, -0.28], [0.42, 1.1, 0.28], 0.03, M.dark);
    var sad = blob(0.1, M.dark, 1.6, 0.45, 0.9, 0); sad.position.set(-0.14, 0.97, 0); B.add(sad);
    props.crank = grp(0, 0.36, 0); B.add(props.crank);
    var ring = new T.Mesh(new T.TorusGeometry(0.1, 0.02, 6, 20), M.metal); props.crank.add(ring);
    [-1, 1].forEach(function (sd) { var a = new T.Mesh(new T.BoxGeometry(0.035, 0.22, 0.035), M.metal); a.position.set(0, sd * 0.11, sd * 0.09); props.crank.add(a); });
    B.rotation.y = -Math.PI / 2;   // lokale x-Achse (Fahrtrichtung) zeigt nach +z, also dorthin, wohin der Fuchs schaut
  })();
  // Fackel: haengt an der rechten Pfote und zeigt in Verlaengerung des Unterarms (hoch gehalten = Flamme oben)
  (function () {
    var Tch = grp(0, PAWY, 0); Tch.rotation.x = Math.PI; F.armR.E.add(Tch); Tch.visible = false; props.torch = Tch;
    var stick = new T.Mesh(new T.CylinderGeometry(0.035, 0.045, 0.5, 10), M.wood); stick.position.y = 0.1; Tch.add(stick);
    var cup = new T.Mesh(new T.CylinderGeometry(0.09, 0.05, 0.1, 14), M.metal); cup.position.y = 0.36; Tch.add(cup);
    props.flame = grp(0, 0.48, 0); Tch.add(props.flame);
    var f1 = new T.Mesh(new T.ConeGeometry(0.12, 0.34, 16), M.flame); f1.position.y = 0.1; props.flame.add(f1);
    var f2 = new T.Mesh(new T.ConeGeometry(0.06, 0.2, 12), M.flame2); f2.position.set(0, 0.06, 0.03); props.flame.add(f2);
    var glow = new T.PointLight(0xff9a3c, 0, 2.5); glow.position.y = 0.15; props.flame.add(glow); props.glow = glow;
    var gc = document.createElement('canvas'); gc.width = gc.height = 64; var gg = gc.getContext('2d');
    var gr = gg.createRadialGradient(32, 32, 2, 32, 32, 31); gr.addColorStop(0, 'rgba(255,230,140,1)'); gr.addColorStop(0.35, 'rgba(255,160,40,.55)'); gr.addColorStop(1, 'rgba(255,120,0,0)');
    gg.fillStyle = gr; gg.fillRect(0, 0, 64, 64);
    var gt = new T.CanvasTexture(gc); gt.colorSpace = T.SRGBColorSpace;
    props.halo = new T.Sprite(new T.SpriteMaterial({ map: gt, transparent: true, depthWrite: false, blending: T.AdditiveBlending }));
    props.halo.scale.setScalar(0.75); props.halo.position.y = 0.12; props.flame.add(props.halo);
    var f3 = new T.Mesh(new T.ConeGeometry(0.085, 0.26, 12), M.flame); f3.position.set(0.05, 0.05, -0.02); f3.rotation.z = -0.35; props.flame.add(f3);
    var f4 = f3.clone(); f4.position.x = -0.05; f4.rotation.z = 0.35; props.flame.add(f4);
  })();
  // Pokal: in der rechten Pfote, hoch gereckt
  (function () {
    var P = grp(0, PAWY, 0); P.rotation.x = Math.PI; F.armR.E.add(P); P.visible = false; props.trophy = P;
    var I = grp(0, 0.24, 0); I.scale.setScalar(1.15); P.add(I);
    var cup = new T.Mesh(new T.CylinderGeometry(0.24, 0.12, 0.32, 24, 1, true), M.gold); cup.position.y = 0.16; I.add(cup);
    var cupIn = new T.Mesh(new T.CylinderGeometry(0.235, 0.115, 0.31, 24, 1, true), M.goldDark); cupIn.material = M.goldDark.clone(); cupIn.material.side = T.BackSide; cupIn.position.y = 0.16; I.add(cupIn);
    var stem = new T.Mesh(new T.CylinderGeometry(0.04, 0.05, 0.14, 10), M.gold); stem.position.y = -0.05; I.add(stem);
    var base = new T.Mesh(new T.CylinderGeometry(0.14, 0.16, 0.06, 20), M.goldDark); base.position.y = -0.14; I.add(base);
    [-1, 1].forEach(function (sd) { var h = new T.Mesh(new T.TorusGeometry(0.09, 0.022, 8, 20, Math.PI), M.gold); h.position.set(sd * 0.25, 0.18, 0); h.rotation.z = sd > 0 ? -Math.PI / 2 : Math.PI / 2; I.add(h); });
    var star = new T.Mesh(new T.OctahedronGeometry(0.06), M.white); star.position.set(0, 0.17, 0.24); I.add(star);
    P.userData.inner = I;
  })();
  // Herzen, Zzz, Traene, Gedankenpunkte, Konfetti, Funkeln
  function heartGeo() {
    var s = new T.Shape(); s.moveTo(0, -0.1); s.bezierCurveTo(-0.16, 0.0, -0.12, 0.13, 0, 0.06); s.bezierCurveTo(0.12, 0.13, 0.16, 0.0, 0, -0.1);
    var g = new T.ExtrudeGeometry(s, { depth: 0.05, bevelEnabled: true, bevelThickness: 0.02, bevelSize: 0.02, bevelSegments: 3 }); g.center(); return g;
  }
  props.hearts = []; var hg = heartGeo();
  for (var hi = 0; hi < 4; hi++) { var h = new T.Mesh(hg, M.heart); h.visible = false; scene.add(h); props.hearts.push(h); }
  props.zzz = [];
  (function () {
    var c = document.createElement('canvas'); c.width = c.height = 64; var g = c.getContext('2d');
    g.fillStyle = '#5d7096'; g.font = 'bold 52px sans-serif'; g.textAlign = 'center'; g.textBaseline = 'middle'; g.fillText('z', 32, 34);
    var tx = new T.CanvasTexture(c); tx.colorSpace = T.SRGBColorSpace;
    for (var i = 0; i < 3; i++) { var sp = new T.Sprite(new T.SpriteMaterial({ map: tx, transparent: true, depthWrite: false })); sp.visible = false; scene.add(sp); props.zzz.push(sp); }
  })();
  props.tear = blob(0.05, M.tear, 0.8, 1.2, 0.8, 0); props.tear.visible = false; head.add(props.tear);
  props.dots = [];
  for (var di = 0; di < 3; di++) { var d = blob(0.05 + di * 0.025, M.white, 1, 1, 1, 0.12); d.visible = false; scene.add(d); props.dots.push(d); }
  props.confetti = [];
  (function () {
    var cols = [0x2fd59a, 0xffc531, 0xff4d6d, 0x5aa9ff, 0xb197fc, 0xff922b], g = new T.BoxGeometry(0.06, 0.1, 0.012);
    for (var i = 0; i < 26; i++) { var c = new T.Mesh(g, new T.MeshBasicMaterial({ color: cols[i % cols.length], side: T.DoubleSide })); c.visible = false; scene.add(c); c.userData = { a: Math.random() * 6.28, v: 0.6 + Math.random() * 0.9, s: (Math.random() - 0.5) * 2.6, r: Math.random() * 6 }; props.confetti.push(c); }
  })();
  props.sparks = [];
  for (var si = 0; si < 4; si++) { var spk = new T.Mesh(new T.OctahedronGeometry(0.06), mat(0xffe066, { e: 0xffd000, ei: 0.8 })); spk.visible = false; scene.add(spk); props.sparks.push(spk); }

  // ---------------------------------------------------------------------------------------------
  // Pose: Zahlenwerte, die weich ineinander uebergeblendet werden
  // ---------------------------------------------------------------------------------------------
  var NUM = ['y', 'x', 'z', 'rx', 'ry', 'rz', 'sx', 'sy', 'hx', 'hy', 'hz', 'eL', 'eR', 'aLx', 'aLy', 'aLz', 'eLx', 'eLz', 'aRx', 'aRy', 'aRz', 'eRx', 'eRz', 'lL', 'lR', 'tw', 'tl', 'lean', 'lx', 'ly', 'piv'];
  var ANG = { rx: 1, ry: 1, rz: 1 };
  function base() {
    return { y: 0, x: 0, z: 0, rx: 0, ry: 0, rz: 0, sx: 1, sy: 1, hx: 0, hy: 0, hz: 0, eL: 0, eR: 0, aLx: 0, aLy: 0, aLz: 0.18, eLx: -0.15, eLz: 0, aRx: 0, aRy: 0, aRz: -0.18, eRx: -0.15, eRz: 0,
      lL: 0, lR: 0, tw: 0, tl: 0, lean: 0, lx: 0, ly: 0, piv: 0,
      eyes: 'open', eyeL: null, eyeR: null, blink: 0, brow: 'none', mouth: 'smile', blush: false, thumbR: false, thumbL: false, prop: null };
  }
  var S = Math.sin, C = Math.cos, PI = Math.PI;
  function hop(ph) { return 4 * ph * (1 - ph); }                    // 0..1..0
  function ease(x) { return x < 0.5 ? 2 * x * x : 1 - Math.pow(-2 * x + 2, 2) / 2; }
  function spring(x) { return 1 - Math.exp(-6 * x) * C(10 * x); }  // ueberschwingend 0 -> 1
  function blinkAt(t, every, seed) { var ph = (t + seed) % every; return ph < 0.14 ? S(ph / 0.14 * PI) : 0; }
  function breathe(p, t, k) { p.sy = 1 + 0.018 * S(t * 2.2) * (k || 1); p.sx = 1 - 0.008 * S(t * 2.2) * (k || 1); }
  // Blick: wandert alle paar Sekunden weich an eine andere Stelle
  function gaze(p, t, sd) {
    var k = Math.floor((t + sd) / 2.3), f = ((t + sd) / 2.3) % 1, m = Math.min(1, f * 6);
    function at(i) { var a = S(i * 12.9898 + sd) * 43758.5453; a -= Math.floor(a); var b = S(i * 78.233 + sd) * 43758.5453; b -= Math.floor(b); return [a * 2 - 1, (b * 2 - 1) * 0.6]; }
    var a = at(k - 1), b = at(k), e = ease(m);
    p.lx = a[0] + (b[0] - a[0]) * e; p.ly = a[1] + (b[1] - a[1]) * e;
  }

  var A = {};
  // 1 Ruhig dastehen: atmen, Gewicht verlagern, umschauen, blinzeln
  A.idle = function (p, t, sd) {
    breathe(p, t); p.x = 0.02 * S(t * 0.7); p.rz = -0.02 * S(t * 0.7); p.hz = 0.06 * S(t * 0.7 + 0.5);
    p.tw = 0.3 * S(t * 1.7); p.tl = 0.05 * S(t * 0.9); gaze(p, t, sd); p.hy = p.lx * 0.18; p.hx = -p.ly * 0.1;
    p.aLz = 0.2 + 0.03 * S(t * 2.2); p.aRz = -0.2 - 0.03 * S(t * 2.2);
    p.eL = 0.06 * S(t * 1.3 + 1); p.eR = -0.06 * S(t * 1.1);
    p.blink = blinkAt(t, 3.6, sd);
  };
  // 2 Winken
  A.wave = function (p, t, sd) {
    breathe(p, t); p.y = 0.03 * Math.abs(S(t * 4.5)); p.hz = 0.14 + 0.05 * S(t * 4.5); p.ry = -0.12; p.rz = 0.03 * S(t * 4.5);
    p.aRz = -2.45; p.aRx = -0.25; p.eRx = -0.2; p.eRz = -0.35 + 0.6 * S(t * 9);
    p.tw = 0.55 * S(t * 4.5); p.mouth = 'open'; p.blink = blinkAt(t, 3.2, sd); p.eL = 0.2; p.eR = -0.1; p.lx = -0.3; p.ly = 0.2;
  };
  // 3 Jubeln: Huepfen mit Squash & Stretch
  A.cheer = function (p, t) {
    var per = 0.95, ph = (t % per) / per, air = ph < 0.62 ? hop(ph / 0.62) : 0, land = ph >= 0.62 ? S((ph - 0.62) / 0.38 * PI) : 0;
    var pre = ph > 0.88 ? S((ph - 0.88) / 0.12 * PI) : 0;
    p.y = air * 0.38; p.sy = 1 + air * 0.08 - land * 0.15 - pre * 0.08; p.sx = 1 - air * 0.04 + land * 0.1;
    p.aLz = 2.55 + 0.2 * S(t * 13); p.aRz = -2.55 - 0.2 * S(t * 13 + 1); p.eLx = -0.15; p.eRx = -0.15; p.eLz = 0.25; p.eRz = -0.25;
    p.lL = -air * 0.5; p.lR = air * 0.4; p.hx = -0.15 * air; p.tw = 0.8 * S(t * 12); p.tl = 0.4;
    p.eyes = 'happy'; p.mouth = 'open'; p.eL = 0.3; p.eR = -0.3;
  };
  // 4 Daumen hoch
  A.thumbs = function (p, t) {
    breathe(p, t); var nod = S(t * 4.5); p.hx = 0.1 * nod; p.hz = 0.12; p.ry = -0.15;
    p.aRx = -0.85 + 0.06 * nod; p.aRz = -0.35; p.eRx = -1.45; p.thumbR = true;
    p.tw = 0.4 * S(t * 3); p.eyes = 'happy'; p.mouth = 'open'; p.y = 0.02 * Math.abs(nod);
  };
  // 5 Tanzen
  A.dance = function (p, t) {
    var b = t * 4.2, s = S(b);
    p.x = 0.1 * s; p.rz = -0.1 * s; p.y = 0.07 * Math.abs(C(b)); p.sy = 1 - 0.05 * Math.abs(S(b));
    p.aLz = 1.3 + 0.8 * S(b); p.aRz = -1.3 + 0.8 * S(b); p.eLz = 1.2; p.eRz = -1.2; p.eLx = -0.3; p.eRx = -0.3;
    p.hz = 0.18 * s; p.hy = 0.15 * C(b * 0.5); p.lL = 0.4 * Math.max(0, s); p.lR = 0.4 * Math.max(0, -s);
    p.tw = 0.9 * s; p.eyes = 'happy'; p.mouth = 'open'; p.ry = 0.3 * S(b * 0.5);
  };
  // 6 Nachdenken: Pfote am Kinn, Blick nach oben, Gedankenblasen
  A.think = function (p, t, sd) {
    breathe(p, t, 0.6); p.hz = 0.2 + 0.04 * S(t); p.hx = -0.1; p.hy = 0.15;
    p.aRx = -0.75; p.aRz = 0.25; p.eRx = -2.0; p.eRz = 0.3;                            // Pfote ans Kinn
    p.aLx = -0.35; p.aLz = -0.05; p.eLx = -1.45; p.eLz = -0.5;                          // Arm vor dem Bauch
    p.brow = 'think'; p.mouth = 'o'; p.lx = 0.7; p.ly = 0.9; p.tw = 0.18 * S(t * 1.4); p.prop = 'dots'; p.blink = blinkAt(t, 4.2, sd);
  };
  // 7 Traurig
  A.sad = function (p, t) {
    p.sy = 0.95 + 0.01 * S(t * 1.2); p.hx = 0.3 + 0.03 * S(t * 1.2); p.hz = -0.08; p.lean = 0.1;
    p.eL = -0.9; p.eR = 0.9; p.aLz = 0.06; p.aRz = -0.06; p.aLx = 0.15; p.aRx = 0.15;
    p.brow = 'sad'; p.mouth = 'frown'; p.tl = -0.55; p.tw = 0.05 * S(t); p.prop = 'tear'; p.ly = -0.6;
  };
  // 8 Schlafen
  A.sleep = function (p, t) {
    p.sy = 0.96 + 0.035 * S(t * 1.3); p.hx = 0.3; p.hz = 0.3 + 0.03 * S(t * 1.3); p.rz = 0.04;
    p.eyes = 'closed'; p.mouth = 'o'; p.eL = -0.35; p.eR = 0.2; p.aLz = 0.08; p.aRz = -0.08;
    p.tl = -0.25; p.tw = 0.06 * S(t * 0.8); p.prop = 'zzz';
  };
  // 9 Feiern: Salto rueckwaerts mit Konfetti
  A.celebrate = function (p, t) {
    var per = 2.0, ph = (t % per) / per;
    var jump = ph < 0.5 ? hop(ph / 0.5) : 0, land = ph >= 0.5 && ph < 0.7 ? S((ph - 0.5) / 0.2 * PI) : 0, crouch = ph > 0.85 ? S((ph - 0.85) / 0.15 * PI) : 0;
    p.y = jump * 0.55; p.rx = ph < 0.5 ? -ease(ph / 0.5) * PI * 2 : 0; p.piv = 1.1;
    p.sy = 1 - land * 0.18 - crouch * 0.12 + jump * 0.05; p.sx = 1 + land * 0.13;
    p.aLz = 2.4; p.aRz = -2.4; p.eLx = -0.3; p.eRx = -0.3; p.lL = -jump * 0.9; p.lR = -jump * 0.9;
    p.eyes = 'happy'; p.mouth = 'open'; p.tw = 0.7 * S(t * 10); p.prop = 'confetti'; p.eL = 0.35; p.eR = -0.35;
  };
  // 10 Radfahren (seitlich, mit drehenden Raedern und Kurbel)
  A.ride = function (p, t) {
    p.prop = 'bike'; p.y = 0.012 * S(t * 14); p.ry = 0.95; p.hy = -0.55; p.hx = -0.05; p.lean = 0.35;
    var c = t * 7; p.lL = 0.7 + 0.45 * S(c); p.lR = 0.7 + 0.45 * S(c + PI);
    p.aLx = -0.84; p.aRx = -0.84; p.eLx = -0.1; p.eRx = -0.1; p.aLz = 0.1; p.aRz = -0.1;
    p.hz = 0.04 * S(t * 6); p.tw = 0.3 * S(t * 5); p.tl = 0.6; p.mouth = 'open'; p.eyes = 'happy';
    p.crank = c;
  };
  // 11 Rennen
  A.run = function (p, t) {
    var c = t * 9.5, s = S(c);
    p.lean = 0.25; p.y = 0.08 * Math.abs(C(c)); p.sy = 1 + 0.04 * Math.abs(C(c));
    p.lL = 0.9 * s; p.lR = -0.9 * s; p.aLx = -0.9 * s; p.aRx = 0.9 * s; p.eLx = -1.3; p.eRx = -1.3;
    p.hx = -0.2; p.tl = 0.9; p.tw = 0.25 * S(c * 0.5); p.mouth = 'open'; p.eL = -0.4; p.eR = 0.4; p.lx = 0.2;
  };
  // 12 Pirouette
  A.spin = function (p, t) {
    var per = 2.2, ph = (t % per) / per, sp = ph < 0.55 ? ease(ph / 0.55) : 1, settle = ph >= 0.55 ? spring((ph - 0.55) / 0.45) : 0;
    p.ry = sp * PI * 2; p.y = hop(Math.min(1, ph / 0.55)) * 0.3;
    p.aLz = 1.5 * (1 - settle * 0.6); p.aRz = -1.5 * (1 - settle * 0.6); p.sy = 1 + 0.06 * S(ph * PI * 2);
    p.tl = 0.6 * (1 - settle); p.tw = 0.6 * S(t * 8); p.eyes = 'happy'; p.mouth = 'open';
  };
  // 13 Lachen
  A.laugh = function (p, t) {
    var sh = S(t * 26);
    p.y = 0.03 * Math.abs(S(t * 13)); p.rz = 0.04 * sh; p.lean = -0.12; p.hx = -0.3 + 0.06 * S(t * 13); p.sy = 1 + 0.03 * S(t * 13);
    p.aLx = -0.45; p.aRx = -0.45; p.aLz = 0.05; p.aRz = -0.05; p.eLx = -1.3; p.eRx = -1.3; p.eLz = -0.6; p.eRz = 0.6;   // Pfoten am Bauch
    p.eyes = 'happy'; p.mouth = 'open'; p.blush = true; p.tw = 0.6 * sh; p.eL = 0.25; p.eR = -0.25;
  };
  // 14 Ueberrascht
  A.surprised = function (p, t) {
    var per = 2.6, ph = (t % per) / per, jolt = ph < 0.25 ? spring(ph / 0.25) : 1, back = ph < 0.18 ? hop(ph / 0.18) : 0;
    p.y = back * 0.22; p.z = -back * 0.2; p.lean = -0.12 * jolt; p.sy = 1 + 0.05 * jolt;
    p.aLz = 0.9 * jolt; p.aRz = -0.9 * jolt; p.aLx = -0.5 * jolt; p.aRx = -0.5 * jolt; p.eLx = -1.0; p.eRx = -1.0; p.eLz = 0.6; p.eRz = -0.6;
    p.eL = 0.45 * jolt; p.eR = -0.45 * jolt; p.hx = -0.12 * jolt; p.eyes = 'wide'; p.brow = 'up'; p.mouth = 'o'; p.tl = 0.7 * jolt; p.tw = 0.1 * S(t * 20) * (1 - jolt);
  };
  // 15 Verliebt: Pfoten ans Herz, Herzen steigen auf
  A.love = function (p, t) {
    breathe(p, t, 1.4); p.hz = 0.18 + 0.06 * S(t * 2); p.ry = 0.1 * S(t * 1.5); p.y = 0.03 * Math.abs(S(t * 2));
    p.aLx = -0.55; p.aRx = -0.55; p.aLz = -0.1; p.aRz = 0.1; p.eLx = -1.55; p.eRx = -1.55; p.eLz = -0.5; p.eRz = 0.5;
    p.eyes = 'happy'; p.mouth = 'smile'; p.blush = true; p.prop = 'hearts'; p.tw = 0.45 * S(t * 3);
  };
  // 16 Muskeln zeigen
  A.flex = function (p, t) {
    var pump = 0.5 + 0.5 * S(t * 4);
    p.aLz = 1.5; p.aRz = -1.5; p.eLx = 0; p.eRx = 0; p.eLz = 1.75 + 0.25 * pump; p.eRz = -1.75 - 0.25 * pump;
    p.sx = 1.02 + 0.03 * pump; p.sy = 1.02; p.y = 0.02 * pump; p.hx = -0.08; p.hz = 0.08 * S(t * 2);
    p.eyes = 'happy'; p.mouth = 'open'; p.flexK = pump; p.tw = 0.35 * S(t * 4); p.prop = 'sparks';
  };
  // 17 Pokal hochreissen
  A.trophy = function (p, t) {
    var b = Math.abs(S(t * 3));
    p.y = 0.05 * b; p.sy = 1 + 0.03 * b; p.aRz = -2.3; p.eRz = -0.15; p.eRx = -0.1; p.ry = 0.12; p.aLz = 2.3 + 0.15 * S(t * 6); p.eLx = -0.3; p.eLz = 0.3;
    p.hx = -0.2; p.hz = -0.1; p.eyes = 'happy'; p.mouth = 'open'; p.prop = 'trophy'; p.tw = 0.55 * S(t * 6); p.lx = -0.6; p.ly = 0.8;
  };
  // 18 Fackel (mit Szenen-Varianten fuer die Serien-Flamme: laufen, hochhalten, ausholen, anzuenden)
  A.torch = function (p, t, sd, sub) {
    breathe(p, t); p.prop = 'torch'; p.hx = -0.2; p.eyes = 'open'; p.mouth = 'smile'; p.tw = 0.3 * S(t * 2.5);
    p.aRz = -2.5; p.aRx = -0.1; p.eRz = -0.3; p.eRx = -0.1; p.lx = -0.5; p.ly = 0.6; p.flameOn = 1;
    if (sub === 'run') { A.run(p, t); p.prop = 'torch'; p.ry = 1.0; p.aRx = -0.9; p.aRz = -0.3; p.eRx = -1.3; p.eRz = 0; p.flameOn = 0.6; }
    if (sub === 'raise') { p.ry = 0.35; p.mouth = 'o'; }
    if (sub === 'wind') { p.ry = 0.6; p.aRz = -2.2; p.aRx = 0.9; p.eRx = -1.2; p.eRz = 0; p.rz = 0.12; p.lean = -0.15; p.eyes = 'happy'; p.flameOn = 0.4; }
    if (sub === 'strike') { p.ry = 0.7; p.aRz = -0.35; p.aRx = -1.55; p.eRx = -0.15; p.eRz = 0; p.lean = 0.25; p.y = 0.05; p.mouth = 'open'; p.eyes = 'happy'; }
  };
  // 19 Zeigen
  A.point = function (p, t, sd) {
    breathe(p, t); p.ry = -0.3; p.hy = -0.3; p.hz = 0.06; p.y = 0.02 * Math.abs(S(t * 2.5));
    p.aRx = -1.35; p.aRz = -0.55 + 0.05 * S(t * 5); p.eRx = -0.05; p.aLz = 0.3; p.aLx = 0.15;
    p.mouth = 'open'; p.blink = blinkAt(t, 3.4, sd); p.tw = 0.35 * S(t * 2.5); p.thumbR = true; p.lx = -0.9; p.ly = 0.1;
  };
  // 20 Zwinkern mit Fingerpistole
  A.wink = function (p, t) {
    var ph = (t % 2.4) / 2.4, w = ph > 0.2 && ph < 0.55 ? 1 : 0;
    breathe(p, t); p.hz = 0.2; p.ry = -0.15; p.y = 0.03 * Math.abs(S(t * 3));
    p.aRx = -1.25; p.aRz = -0.6; p.eRx = -0.25; p.thumbR = true; p.aRy = 0.15 * w;
    p.eyeR = w ? 'happy' : 'open'; p.eyeL = 'open'; p.mouth = w ? 'open' : 'smile'; p.tw = 0.4 * S(t * 3); p.prop = w ? 'sparks' : null; p.lx = -0.4;
  };

  // ---------------------------------------------------------------------------------------------
  // Pose anwenden
  // ---------------------------------------------------------------------------------------------
  var vPiv = new T.Vector3(), vTmp = new T.Vector3();
  function apply(p, t, inst) {
    // Drehung um einen Punkt in Koerperhoehe (Salto), sonst um die Fuesse
    root.rotation.set(p.rx, p.ry, p.rz);
    root.position.set(p.x, p.y, p.z || 0);
    if (p.piv) { vPiv.set(0, p.piv, 0); var q = root.quaternion; vTmp.copy(vPiv).applyQuaternion(q); root.position.add(vPiv).sub(vTmp); }
    body.scale.set(p.sx, p.sy, p.sx);
    torso.rotation.set(p.lean || 0, 0, 0);
    F.shadow.scale.setScalar(Math.max(0.45, 1 - p.y * 0.55)); F.shadow.material.opacity = Math.max(0.25, 1 - p.y * 0.6); F.shadow.position.x = p.x;
    head.rotation.set(p.hx, p.hy, p.hz);
    F.earL.rotation.set(0, 0, -0.32 + p.eL * 0.5); F.earL.rotation.x = Math.min(0, p.eL) * -0.6;
    F.earR.rotation.set(0, 0, 0.32 + p.eR * 0.5); F.earR.rotation.x = Math.max(0, p.eR) * 0.6;
    F.armL.S.rotation.set(p.aLx, p.aLy, p.aLz); F.armL.E.rotation.set(p.eLx, 0, p.eLz);
    F.armR.S.rotation.set(p.aRx, p.aRy, p.aRz); F.armR.E.rotation.set(p.eRx, 0, p.eRz);
    var fk = p.flexK || 0; F.armL.E.scale.setScalar(1 + fk * 0.12); F.armR.E.scale.setScalar(1 + fk * 0.12);
    F.armR.thumb.visible = !!p.thumbR; F.armL.thumb.visible = !!p.thumbL;
    F.legL.rotation.set(-p.lL, 0, 0.04); F.legR.rotation.set(-p.lR, 0, -0.04);
    // Schwanz mit Nachschwingen (jedes Glied etwas spaeter)
    for (var i = 0; i < F.tail.length; i++) {
      var lag = i * 0.22;
      F.tail[i].rotation.set(i === 0 ? -1.05 + p.tl * 0.6 : 0.32 - p.tl * 0.12, 0, (i === 0 ? 0.55 : 0.1) + p.tw * (0.45 + i * 0.2) * C(lag) * (i ? 0.8 : 1));
    }
    // Gesicht
    var bl = p.blink;
    F.eyes.forEach(function (E) {
      var kind = (E.side < 0 ? p.eyeR : p.eyeL) || p.eyes;
      E.open.visible = kind === 'open' || kind === 'wide'; E.happy.visible = kind === 'happy'; E.closed.visible = kind === 'closed';
      var wide = kind === 'wide' ? 1.3 : 1;
      E.open.scale.set(wide, wide * Math.max(0.08, 1 - bl), wide);
      E.pup.position.set((p.lx || 0) * 0.035, (p.ly || 0) * 0.04, 0.04);
      E.brow.visible = p.brow !== 'none';
      E.brow.rotation.z = Math.PI / 2 + (p.brow === 'sad' ? E.side * 0.45 : p.brow === 'think' ? (E.side < 0 ? 0.3 : -0.05) : 0);
      E.brow.position.y = p.brow === 'up' ? 0.22 : p.brow === 'think' && E.side < 0 ? 0.21 : 0.18;
    });
    F.mouth.smile.visible = p.mouth === 'smile'; F.mouth.open.visible = p.mouth === 'open'; F.mouth.frown.visible = p.mouth === 'frown'; F.mouth.o.visible = p.mouth === 'o';
    F.blush.forEach(function (b) { b.visible = !!p.blush; });
    // Requisiten
    props.bike.visible = p.prop === 'bike';
    if (props.bike.visible) {
      var wr = -(p.crank || 0) * 1.6;
      props.wheelF.rotation.set(0, 0, wr); props.wheelB.rotation.set(0, 0, wr);
      props.crank.rotation.z = -(p.crank || 0);
      hips.position.set(0, 0.9, -0.08);
    } else hips.position.set(0, 0.48, 0);
    props.torch.visible = p.prop === 'torch';
    if (props.torch.visible) {
      var on = p.flameOn == null ? 1 : p.flameOn, fl = on * (1 + 0.12 * S(t * 23) + 0.08 * S(t * 37));
      props.flame.scale.set(fl * (1 + 0.06 * S(t * 17)), fl, fl); props.flame.rotation.z = 0.12 * S(t * 11);
      props.glow.intensity = 2.2 * on; props.halo.material.opacity = 0.75 * on * (0.85 + 0.15 * S(t * 19));
    }
    props.trophy.visible = p.prop === 'trophy';
    if (props.trophy.visible) props.trophy.userData.inner.rotation.y = 0.5 * S(t * 1.6);
    props.hearts.forEach(function (h, i) {
      h.visible = p.prop === 'hearts'; if (!h.visible) return;
      var ph = ((t * 0.55 + i / props.hearts.length) % 1);
      h.position.set((i % 2 ? 0.55 : -0.55) + 0.12 * S(t * 3 + i), 1.6 + ph * 1.3, 0.3); var sc = Math.sin(ph * PI) * (1.1 + 0.15 * S(t * 9)); h.scale.setScalar(sc * 1.4); h.rotation.y = 0.5 * S(t * 2 + i);
    });
    props.zzz.forEach(function (z, i) {
      z.visible = p.prop === 'zzz'; if (!z.visible) return;
      var ph = ((t * 0.35 + i / 3) % 1); z.position.set(0.45 + ph * 0.45, 2.15 + ph * 0.7, 0.3); var s = 0.18 + ph * 0.22; z.scale.set(s, s, s); z.material.opacity = Math.sin(ph * PI);
    });
    props.tear.visible = p.prop === 'tear';
    if (props.tear.visible) { var ph = (t * 0.6) % 1; props.tear.position.set(0.24, 0.42 - ph * 0.45, 0.5); props.tear.scale.setScalar(Math.min(1, ph * 4) * (1 - Math.max(0, ph - 0.8) * 5)); }
    props.dots.forEach(function (d, i) {
      d.visible = p.prop === 'dots'; if (!d.visible) return;
      d.position.set(0.55 + i * 0.18, 2.25 + i * 0.2, 0.2); d.scale.setScalar(0.8 + 0.25 * S(t * 3 - i));
    });
    props.confetti.forEach(function (c, i) {
      c.visible = p.prop === 'confetti'; if (!c.visible) return;
      var u = c.userData, ph = ((t * 0.5 + i / props.confetti.length) % 1);
      c.position.set(u.s * ph * 1.2 + 0.2 * S(t * 3 + u.a), 2.9 - ph * 2.9 * u.v, 0.4 * C(u.a) + 0.2); c.rotation.set(t * 5 + u.r, t * 4 + u.a, t * 3);
    });
    props.sparks.forEach(function (s, i) {
      s.visible = p.prop === 'sparks'; if (!s.visible) return;
      var ph = ((t * 0.8 + i / 4) % 1), a = i * 1.57 + 0.6;
      s.position.set(C(a) * (0.9 + ph * 0.3), 1.6 + S(a) * 0.6 + ph * 0.2, 0.4); s.scale.setScalar(Math.sin(ph * PI) * 1.2); s.rotation.y = t * 4;
    });
  }

  // ---------------------------------------------------------------------------------------------
  // Instanzen: jede .fxw-Huelle bekommt eine Leinwand; ein Takt zeichnet alle sichtbaren
  // ---------------------------------------------------------------------------------------------
  var insts = [], running = false, lastFrame = 0, TAPS = ['spin', 'laugh', 'surprised', 'dance', 'love', 'wink', 'flex'], tapN = 0;
  var SUBS = { 'st-run': 'run', 'st-raise': 'raise', 'st-wind': 'wind', 'st-strike': 'strike' };
  function poseOf(el) {
    var p = el.getAttribute('data-pose') || 'idle';
    if (POSES.indexOf(p) < 0) p = 'idle';
    return p;
  }
  function subOf(el) { for (var k in SUBS) if (el.classList.contains(k)) return SUBS[k]; return null; }
  function mount(el) {
    if (!API.enabled || el.__f3d) return;
    var cvs = document.createElement('canvas'); cvs.className = 'f3d-cv';
    el.appendChild(cvs);
    var inst = { el: el, cv: cvs, ctx: cvs.getContext('2d'), t0: performance.now() / 1000, seed: Math.random() * 5, pose: null, prev: null, blendT: 0, last: null };
    el.__f3d = inst; insts.push(inst); start();
    // Antippen: Velo reagiert mit einer kurzen Extra-Animation (wie bei Duolingo)
    el.addEventListener('pointerdown', function () {
      var i = el.__f3d, now = performance.now() / 1000; if (!i || (i.override && now < i.override.until)) return;
      i.override = { pose: TAPS[tapN++ % TAPS.length], start: now, until: now + 2.0 };
      if (window.VX && VX.haptic) VX.haptic('soft');
    }, { passive: true });
  }
  function unmountAll() { insts.forEach(function (i) { if (i.cv.parentNode) i.cv.remove(); i.el.classList.remove('f3d'); i.el.__f3d = null; }); insts = []; }
  function visible(el) {
    if (!el.isConnected) return false;
    var r = el.getBoundingClientRect();
    return r.width > 4 && r.height > 4 && r.bottom > 0 && r.right > 0 && r.top < innerHeight && r.left < innerWidth && getComputedStyle(el).visibility !== 'hidden';
  }
  function poseAt(inst, now) {
    var t = now - inst.t0, sub = subOf(inst.el), name = poseOf(inst.el);
    if (sub) name = 'torch';
    if (inst.override) { if (now < inst.override.until) { name = inst.override.pose; sub = null; t = now - inst.override.start; } else inst.override = null; }
    var key = name + (sub || '');
    if (key !== inst.pose) { inst.prev = inst.last; inst.blendT = now; inst.pose = key; }
    var p = base(); (A[name] || A.idle)(p, reduce ? 0 : t, inst.seed, sub); p.blink = p.blink || 0;
    // weicher Uebergang von der letzten Pose (0,28 s)
    if (inst.prev) {
      var k = Math.min(1, (now - inst.blendT) / 0.28); k = ease(k);
      if (k < 1) NUM.forEach(function (n) {
        var a = inst.prev[n], b = p[n]; if (typeof a !== 'number' || typeof b !== 'number') return;
        var d = b - a; if (ANG[n]) d = Math.atan2(Math.sin(d), Math.cos(d));   // Drehungen auf dem kuerzesten Weg
        p[n] = a + d * k;
      });
      else inst.prev = null;
    }
    inst.last = p;
    return { p: p, t: t };
  }
  function frame() {
    if (!insts.length) { running = false; return; }
    requestAnimationFrame(frame);
    if (document.hidden) return;
    var now = performance.now() / 1000;
    insts = insts.filter(function (i) { return i.el.isConnected && i.el.__f3d === i; });
    var vis = insts.filter(function (i) { return visible(i.el); });
    // grosse Fuechse (Begruessung, Feiern) mit 60 Bildern/s, kleine in Listen reichen 30 (spart Akku)
    var fps = vis.length > 2 || !vis.some(function (i) { return i.el.offsetHeight >= 100; }) ? 30 : 60;
    if (now - lastFrame < 1 / fps - 0.002) return;
    lastFrame = now;
    vis.forEach(function (inst) {
      var r = inst.cv.getBoundingClientRect(), dpr = Math.min(2, window.devicePixelRatio || 1);
      var w = Math.min(MAXPX, Math.round(r.width * dpr)), h = Math.min(MAXPX, Math.round(r.height * dpr));
      if (inst.cv.width !== w || inst.cv.height !== h) { inst.cv.width = w; inst.cv.height = h; }
      var res = poseAt(inst, now);
      apply(res.p, res.t, inst);
      // feste Kamera fuer alle Posen: kein Springen beim Wechsel, Platz fuer Spruenge und Requisiten
      var ty = 1.5, dz = 8.0;
      cam.aspect = w / h; cam.position.set(0, ty + 0.35, dz); cam.lookAt(0, ty, 0); cam.updateProjectionMatrix();
      renderer.setViewport(0, 0, w, h); renderer.setScissor(0, 0, w, h); renderer.clear();
      renderer.render(scene, cam);
      inst.ctx.clearRect(0, 0, w, h);
      inst.ctx.drawImage(renderer.domElement, 0, MAXPX - h, w, h, 0, 0, w, h);
      if (!inst.drawn) { inst.drawn = true; inst.el.classList.add('f3d'); }
    });
  }
  function start() { if (!running) { running = true; requestAnimationFrame(frame); } }
  function scan(rootEl) { if (!API.enabled || !API.ok) return; (rootEl.querySelectorAll ? rootEl : document).querySelectorAll('.fxw').forEach(mount); if (rootEl.classList && rootEl.classList.contains('fxw')) mount(rootEl); }
  new MutationObserver(function (ms) {
    ms.forEach(function (m) { m.addedNodes.forEach(function (n) { if (n.nodeType === 1) scan(n); }); });
  }).observe(document.documentElement, { childList: true, subtree: true });
  if (document.readyState !== 'loading') scan(document); else document.addEventListener('DOMContentLoaded', function () { scan(document); });

  API.setEnabled = function (on) { API.enabled = !!on; store.set('fox3d', API.enabled); if (on) scan(document); else unmountAll(); };
  // Standbild einer Pose als Leinwand (z. B. fuer das Teilen-Bild einer Fahrt)
  API.still = function (pose, w, h, t) {
    w = Math.min(MAXPX, w || 400); h = Math.min(MAXPX, h || 460);
    var p = base(); (A[pose] || A.idle)(p, t || 0.4, 0, null);
    apply(p, t || 0.4, null);
    cam.aspect = w / h; cam.position.set(0, 1.85, 8.0); cam.lookAt(0, 1.5, 0); cam.updateProjectionMatrix();
    renderer.setViewport(0, 0, w, h); renderer.setScissor(0, 0, w, h); renderer.clear(); renderer.render(scene, cam);
    var c = document.createElement('canvas'); c.width = w; c.height = h;
    c.getContext('2d').drawImage(renderer.domElement, 0, MAXPX - h, w, h, 0, 0, w, h);
    return c;
  };
  API._apply = apply; API._A = A; API._base = base;
})();
