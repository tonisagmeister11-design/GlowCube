// PathogenViewer: eine Three.js-Szene, die das aktuelle Erregermodell hochwertig
// präsentiert – langsame Rotation, Schweben, Pulsieren, Beleuchtung, Partikel,
// animierter Modellwechsel mit Auflöse-/Partikeleffekt, Evolutionszustände.
import * as THREE from 'three';
import { EffectComposer } from 'three/addons/postprocessing/EffectComposer.js';
import { RenderPass } from 'three/addons/postprocessing/RenderPass.js';
import { UnrealBloomPass } from 'three/addons/postprocessing/UnrealBloomPass.js';
import { OutputPass } from 'three/addons/postprocessing/OutputPass.js';
import { PathogenAssetManager } from './assets.js';
import { PATHOGENS } from '../data/pathogens.js';

export class PathogenViewer {
  constructor(canvas, { particles = true, quality = 1 } = {}) {
    this.canvas = canvas;
    this.assets = new PathogenAssetManager();
    this.renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: true, powerPreference: 'high-performance' });
    this.renderer.setClearColor(0x000000, 0);
    this.renderer.toneMapping = THREE.ACESFilmicToneMapping;
    this.renderer.toneMappingExposure = 1.15;
    this.dpr = Math.min(window.devicePixelRatio || 1, quality >= 1 ? 2 : 1.3);
    this.scene = new THREE.Scene();
    this.camera = new THREE.PerspectiveCamera(38, 1, 0.1, 100);
    this.camera.position.set(0, 0, 5.2);
    this.holder = new THREE.Group();
    this.scene.add(this.holder);
    this.clock = new THREE.Clock();
    this.current = null;      // aktive Instanz
    this.outgoing = null;     // in Auflösung
    this.key = null;
    this.idleName = null;
    this.transition = 0;
    this.spin = true;
    this.autoIdle = true;
    this._setupLights();
    if (particles) this._setupParticles();
    // Postprocessing: Bloom lässt leuchtende Teile (Kerne, Spikes) aller Erreger glühen
    this.composer = new EffectComposer(this.renderer);
    this.composer.addPass(new RenderPass(this.scene, this.camera));
    this.bloom = new UnrealBloomPass(new THREE.Vector2(1, 1), 0.7, 0.6, 0.85);
    this.composer.addPass(this.bloom);
    this.composer.addPass(new OutputPass());
    this._raf = null;
    this._onResize = () => this.resize();
    window.addEventListener('resize', this._onResize);
    this.resize();
  }

  _setupLights() {
    this.scene.add(new THREE.AmbientLight(0x331014, 1.1));
    const key = new THREE.DirectionalLight(0xffd9c0, 2.2); key.position.set(3, 4, 5); this.scene.add(key);
    const rim = new THREE.PointLight(0xff2030, 30, 30); rim.position.set(-4, 2, -3); this.scene.add(rim);
    this.rim = rim;
    const fill = new THREE.DirectionalLight(0x6080ff, 0.6); fill.position.set(-3, -2, 2); this.scene.add(fill);
    // dezenter Halo hinter dem Modell
    const halo = new THREE.Mesh(
      new THREE.SphereGeometry(3.4, 24, 16),
      new THREE.MeshBasicMaterial({ color: 0x330008, transparent: true, opacity: 0.5, side: THREE.BackSide, depthWrite: false }));
    this.scene.add(halo);
  }

  _setupParticles() {
    const N = 320;
    const geo = new THREE.BufferGeometry();
    const pos = new Float32Array(N * 3);
    const sp = new Float32Array(N);
    for (let i = 0; i < N; i++) {
      pos[i * 3] = (Math.random() - 0.5) * 12;
      pos[i * 3 + 1] = (Math.random() - 0.5) * 8;
      pos[i * 3 + 2] = (Math.random() - 0.5) * 8 - 2;
      sp[i] = 0.2 + Math.random() * 0.8;
    }
    geo.setAttribute('position', new THREE.BufferAttribute(pos, 3));
    this._pspeed = sp;
    const mat = new THREE.PointsMaterial({ color: 0xff5544, size: 0.05, transparent: true, opacity: 0.5, depthWrite: false, blending: THREE.AdditiveBlending });
    this.particles = new THREE.Points(geo, mat);
    this.scene.add(this.particles);
    // Auflöse-Partikel für Übergänge
    const bgeo = new THREE.BufferGeometry();
    bgeo.setAttribute('position', new THREE.BufferAttribute(new Float32Array(600 * 3), 3));
    this._burst = new THREE.Points(bgeo, new THREE.PointsMaterial({ color: 0xff6040, size: 0.08, transparent: true, opacity: 0, depthWrite: false, blending: THREE.AdditiveBlending }));
    this.scene.add(this._burst);
    this._burstV = new Float32Array(600 * 3);
    this._burstT = 0;
  }

  async setPathogen(key, { animate = true } = {}) {
    if (key === this.key && this.current) return;
    const prevKey = this.key;
    this.key = key;
    const def = PATHOGENS[key];
    if (this.rim && def) this.rim.color.set(def.accent || def.color);
    const loaded = await this.assets.load(key);
    if (this.key !== key) return; // zwischenzeitlich gewechselt
    const inst = this.assets.instance(loaded);
    inst.scene.rotation.set(0, 0, 0);
    this._fit(inst);
    // Übergang: altes Modell auflösen
    if (this.current && animate && prevKey) {
      this.outgoing = this.current;
      this.outgoing._fade = 1;
      this._emitBurst();
    } else if (this.current) {
      this.holder.remove(this.current.scene);
    }
    this.current = inst;
    inst.scene.scale.setScalar(animate && prevKey ? 0.01 : inst._fitScale);
    this.holder.add(inst.scene);
    this.transition = animate && prevKey ? 1 : 0;
    // Idle-Animation starten
    this.idleName = (def && def.model && this.assets.manifest(key).idle) || this._firstIdle(inst);
    this.playIdle();
    this.applyEvolution([]);
    if (!this._raf) this._loop();
  }

  _firstIdle(inst) {
    const names = Object.keys(inst.actions);
    return names.find((n) => n.endsWith('_idle')) || names[0] || null;
  }

  _fit(inst) {
    const box = new THREE.Box3().setFromObject(inst.scene);
    const size = new THREE.Vector3(); box.getSize(size);
    const center = new THREE.Vector3(); box.getCenter(center);
    const maxDim = Math.max(size.x, size.y, size.z) || 1;
    inst._fitScale = 2.6 / maxDim;
    inst.scene.position.sub(center.multiplyScalar(inst._fitScale));
    inst._center = center;
  }

  playIdle() {
    if (!this.current || !this.idleName) return;
    this.play(this.idleName, { loop: true });
  }

  play(name, { loop = false, fade = 0.3 } = {}) {
    const inst = this.current;
    if (!inst || !inst.actions[name]) return false;
    const a = inst.actions[name];
    if (inst.current && inst.current !== a) inst.current.fadeOut(fade);
    a.reset();
    a.setLoop(loop ? THREE.LoopRepeat : THREE.LoopOnce, loop ? Infinity : 1);
    a.fadeIn(fade).play();
    inst.current = a;
    if (!loop) {
      inst.mixer.addEventListener('finished', this._onFinish = (e) => {
        if (e.action === a) { inst.mixer.removeEventListener('finished', this._onFinish); this.playIdle(); }
      });
    }
    return true;
  }

  // Spielt eine Sonderanimation, sofern vorhanden (z.B. virus_selected, *_mutation)
  playSpecial(suffix) {
    if (!this.current) return;
    const name = Object.keys(this.current.actions).find((n) => n.endsWith('_' + suffix));
    if (name) this.play(name, { loop: false });
  }

  // Blendet Evolutions-Zusatzobjekte je nach Fortschritt ein
  applyEvolution(activeList) {
    if (!this.current) return;
    const evo = this.current.evoNodes;
    const set = new Set(activeList);
    for (const n of evo) {
      const m = /EVO(\d+)/.exec(n.name);
      const idx = m ? +m[1] : 0;
      n.visible = set.has(idx) || set.has('all');
    }
  }

  setEvolutionLevel(level) {
    // level 0..3 -> zeigt EVO1..EVO<level>
    const list = [];
    for (let i = 1; i <= level; i++) list.push(i);
    this.applyEvolution(list);
  }

  _emitBurst() {
    if (!this._burst || !this.outgoing) return;
    const pos = this._burst.geometry.attributes.position.array;
    const c = new THREE.Vector3();
    this.outgoing.scene.getWorldPosition(c);
    for (let i = 0; i < 600; i++) {
      pos[i * 3] = c.x + (Math.random() - 0.5) * 1.5;
      pos[i * 3 + 1] = c.y + (Math.random() - 0.5) * 2;
      pos[i * 3 + 2] = c.z + (Math.random() - 0.5) * 1.5;
      this._burstV[i * 3] = (Math.random() - 0.5) * 3;
      this._burstV[i * 3 + 1] = (Math.random() - 0.5) * 3;
      this._burstV[i * 3 + 2] = (Math.random() - 0.5) * 3;
    }
    this._burst.geometry.attributes.position.needsUpdate = true;
    this._burstT = 1;
    this._burst.material.opacity = 0.9;
  }

  resize() {
    // Layout-Größe (auch korrekt, wenn die Oberfläche gedreht ist)
    const w = Math.max(2, this.canvas.clientWidth), h = Math.max(2, this.canvas.clientHeight);
    this.renderer.setPixelRatio(this.dpr);
    this.renderer.setSize(w, h, false);
    this.camera.aspect = w / h;
    this.camera.updateProjectionMatrix();
    if (this.composer) { this.composer.setPixelRatio(this.dpr); this.composer.setSize(w, h); this.bloom.setSize(w, h); }
  }

  _loop() {
    this._raf = requestAnimationFrame(() => this._loop());
    const dt = Math.min(this.clock.getDelta(), 0.05);
    const t = this.clock.elapsedTime;
    if (this.current) {
      this.current.mixer.update(dt);
      if (this.spin) this.holder.rotation.y += dt * 0.35;
      this.holder.position.y = Math.sin(t * 0.8) * 0.06;
      // Einblenden
      if (this.transition > 0) {
        this.transition = Math.max(0, this.transition - dt * 1.6);
        const s = (1 - this.transition);
        this.current.scene.scale.setScalar(this.current._fitScale * easeOut(s));
      }
      if (this.current.fallback && this.current._spin) this.current._spin.rotation.y += dt;
    }
    if (this.outgoing) {
      this.outgoing._fade -= dt * 2.2;
      const f = this.outgoing._fade;
      this.outgoing.scene.scale.setScalar(this.outgoing._fitScale * Math.max(0.001, f));
      this.outgoing.scene.traverse((o) => { if (o.isMesh && o.material) setOpacity(o.material, f); });
      if (f <= 0) { this.holder.remove(this.outgoing.scene); this.outgoing = null; }
    }
    if (this.particles) {
      const pos = this.particles.geometry.attributes.position.array;
      for (let i = 0; i < this._pspeed.length; i++) {
        pos[i * 3 + 1] += dt * this._pspeed[i] * 0.3;
        if (pos[i * 3 + 1] > 4) pos[i * 3 + 1] = -4;
      }
      this.particles.geometry.attributes.position.needsUpdate = true;
      this.particles.rotation.y = t * 0.02;
    }
    if (this._burstT > 0) {
      this._burstT -= dt * 1.5;
      const pos = this._burst.geometry.attributes.position.array;
      for (let i = 0; i < 600 * 3; i++) pos[i] += this._burstV[i] * dt;
      this._burst.geometry.attributes.position.needsUpdate = true;
      this._burst.material.opacity = Math.max(0, this._burstT) * 0.9;
    }
    if (this.composer) this.composer.render(); else this.renderer.render(this.scene, this.camera);
  }

  dispose() {
    if (this._raf) cancelAnimationFrame(this._raf);
    this._raf = null;
    window.removeEventListener('resize', this._onResize);
    this.renderer.dispose();
  }
}

function easeOut(x) { return 1 - Math.pow(1 - x, 3); }
function setOpacity(m, o) {
  const arr = Array.isArray(m) ? m : [m];
  for (const mm of arr) { mm.transparent = true; mm.opacity = Math.max(0, Math.min(1, o)); mm.depthWrite = false; }
}
