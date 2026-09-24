// PathogenAssetManager: lädt die in Blender erzeugten GLB-Modelle, verwaltet
// Animationen (AnimationMixer), zwischenspeichert geladene Szenen, tauscht
// Modelle mit Überblendung aus und fällt bei Fehlern auf prozedurale Ersatz-
// modelle zurück. Die GLBs sind im Build als data:-URIs eingebettet.
import * as THREE from 'three';
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';
import { MeshoptDecoder } from 'three/addons/libs/meshopt_decoder.module.js';
import { MODEL_DATA } from '../generated/models.js';
import { MANIFEST } from '../generated/manifest.js';
import { buildFallback } from './fallback.js';

export class PathogenAssetManager {
  constructor() {
    this.loader = new GLTFLoader();
    this.loader.setMeshoptDecoder(MeshoptDecoder);
    this.cache = new Map();     // key -> { scene, animations, clips, morphMeshes, evoNodes }
    this.loading = new Map();
  }

  manifest(key) { return MANIFEST[key] || {}; }
  animationList(key) { return (MANIFEST[key] || {}).animations || []; }

  async load(key) {
    if (this.cache.has(key)) return this.cache.get(key);
    if (this.loading.has(key)) return this.loading.get(key);
    const p = this._load(key).catch((err) => {
      console.warn('Modell konnte nicht geladen werden, nutze Fallback:', key, err);
      const fb = buildFallback(key);
      this.cache.set(key, fb);
      return fb;
    });
    this.loading.set(key, p);
    const res = await p;
    this.loading.delete(key);
    this.cache.set(key, res);
    return res;
  }

  async _load(key) {
    const data = MODEL_DATA[key];
    if (!data) throw new Error('kein Modell-Datensatz: ' + key);
    const buf = base64ToArrayBuffer(data);
    const gltf = await this.loader.parseAsync(buf, '');
    const scene = gltf.scene;
    const evoNodes = [];
    const morphMeshes = [];
    scene.traverse((o) => {
      if (o.isMesh) {
        o.frustumCulled = false;
        o.castShadow = false;
        if (o.morphTargetInfluences) morphMeshes.push(o);
        if (Array.isArray(o.material)) o.material.forEach(fixMaterial);
        else fixMaterial(o.material);
      }
      if (o.name && o.name.startsWith('EVO')) evoNodes.push(o);
    });
    // Evolutions-Objekte zunächst ausblenden
    for (const n of evoNodes) n.visible = false;
    const clips = {};
    for (const c of gltf.animations) clips[c.name] = c;
    return { scene, animations: gltf.animations, clips, morphMeshes, evoNodes, fallback: false };
  }

  // Erzeugt eine spielbereite Instanz (eigener Mixer, geklonte Szene wäre teuer –
  // wir nutzen je Modell eine Instanz, da immer nur eines sichtbar ist).
  instance(loaded) {
    const mixer = new THREE.AnimationMixer(loaded.scene);
    const actions = {};
    for (const name in loaded.clips) {
      const a = mixer.clipAction(loaded.clips[name]);
      a.clampWhenFinished = true;
      actions[name] = a;
    }
    return { ...loaded, mixer, actions, current: null };
  }
}

function fixMaterial(m) {
  if (!m) return;
  m.side = THREE.FrontSide;
  if (m.transparent) m.depthWrite = false;
  // Emission sichtbar halten
  if (m.emissiveIntensity != null && m.emissive && (m.emissive.r + m.emissive.g + m.emissive.b) > 0) {
    m.emissiveIntensity = Math.max(m.emissiveIntensity, 1);
  }
}

function base64ToArrayBuffer(b64) {
  const bin = atob(b64);
  const len = bin.length;
  const bytes = new Uint8Array(len);
  for (let i = 0; i < len; i++) bytes[i] = bin.charCodeAt(i);
  return bytes.buffer;
}
