// Menschliches Körper-Hologramm für den Krankheitsbildschirm. Lädt human_body.glb
// (Blender) und lässt die Organe aufleuchten, die zu entwickelten Symptomen gehören.
import * as THREE from 'three';
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';
import { MeshoptDecoder } from 'three/addons/libs/meshopt_decoder.module.js';
import { MODEL_DATA } from '../generated/models.js';

export class HologramViewer {
  constructor(canvas) {
    this.canvas = canvas;
    this.renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: true });
    this.renderer.setClearColor(0x000000, 0);
    this.dpr = Math.min(window.devicePixelRatio || 1, 2);
    this.scene = new THREE.Scene();
    this.camera = new THREE.PerspectiveCamera(35, 1, 0.1, 50);
    this.camera.position.set(0, 0.15, 3.0);
    this.scene.add(new THREE.AmbientLight(0x662028, 1.4));
    const d = new THREE.DirectionalLight(0xff9080, 1.6); d.position.set(1, 2, 3); this.scene.add(d);
    this.organs = {};
    this.mixer = null;
    this.clock = new THREE.Clock();
    this._raf = null;
    this.activeOrgans = new Set();
    window.addEventListener('resize', () => this.resize());
    this._load();
  }

  async _load() {
    const loader = new GLTFLoader();
    loader.setMeshoptDecoder(MeshoptDecoder);
    try {
      const buf = b64(MODEL_DATA.human);
      const gltf = await loader.parseAsync(buf, '');
      this.model = gltf.scene;
      const box = new THREE.Box3().setFromObject(this.model);
      const size = new THREE.Vector3(); box.getSize(size);
      const center = new THREE.Vector3(); box.getCenter(center);
      const s = 2.2 / (size.y || 1);
      this.model.scale.setScalar(s);
      this.model.position.sub(center.multiplyScalar(s));
      this.scene.add(this.model);
      this.model.traverse((o) => {
        if (o.isMesh) {
          o.frustumCulled = false;
          if (o.name.startsWith('Organ_')) {
            const name = o.name.replace('Organ_', '');
            this.organs[name] = o;
            o.material = o.material.clone();
            o.material.transparent = true;
            o.material.opacity = 0.0;
            o.material.emissiveIntensity = 0.2;
            o.visible = true;
          } else {
            // Körperhülle: transparentes Hologramm
            o.material = o.material.clone();
            o.material.transparent = true;
            o.material.opacity = 0.22;
            o.material.depthWrite = false;
          }
        }
      });
      if (gltf.animations.length) {
        this.mixer = new THREE.AnimationMixer(this.model);
        for (const c of gltf.animations) this.mixer.clipAction(c).play();
      }
      this.resize();
      if (!this._raf) this._loop();
    } catch (e) {
      console.warn('Hologramm konnte nicht geladen werden', e);
    }
  }

  setOrgans(list) {
    this.activeOrgans = new Set(list);
  }

  resize() {
    const r = this.canvas.getBoundingClientRect();
    const w = Math.max(2, r.width), h = Math.max(2, r.height);
    this.renderer.setPixelRatio(this.dpr);
    this.renderer.setSize(w, h, false);
    this.camera.aspect = w / h; this.camera.updateProjectionMatrix();
  }

  _loop() {
    this._raf = requestAnimationFrame(() => this._loop());
    const dt = Math.min(this.clock.getDelta(), 0.05);
    const t = this.clock.elapsedTime;
    if (this.mixer) this.mixer.update(dt);
    if (this.model) this.model.rotation.y = Math.sin(t * 0.4) * 0.35;
    for (const name in this.organs) {
      const o = this.organs[name];
      const active = this.activeOrgans.has(name);
      const target = active ? 0.85 : 0.0;
      o.material.opacity += (target - o.material.opacity) * 0.08;
      o.material.emissiveIntensity = active ? 1.2 + Math.sin(t * 4) * 0.4 : 0.2;
    }
    this.renderer.render(this.scene, this.camera);
  }

  dispose() { if (this._raf) cancelAnimationFrame(this._raf); this.renderer.dispose(); }
}

function b64(s) {
  const bin = atob(s); const a = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) a[i] = bin.charCodeAt(i);
  return a.buffer;
}
