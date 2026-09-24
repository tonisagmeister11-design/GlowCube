// Prozedurale Ersatzmodelle (Three.js), falls ein GLB fehlt oder nicht lädt.
// Nur Sicherheitsnetz – bevorzugte Quelle sind die Blender-GLBs.
import * as THREE from 'three';
import { PATHOGENS } from '../data/pathogens.js';

export function buildFallback(key) {
  const def = PATHOGENS[key] || { color: '#d84a4a' };
  const col = new THREE.Color(def.color || '#d84a4a');
  const group = new THREE.Group();
  const mat = new THREE.MeshStandardMaterial({ color: col, roughness: 0.4, emissive: col.clone().multiplyScalar(0.15) });
  const core = new THREE.Mesh(new THREE.IcosahedronGeometry(0.7, 3), mat);
  group.add(core);
  const spikeMat = new THREE.MeshStandardMaterial({ color: col.clone().offsetHSL(0, 0, 0.1), roughness: 0.5 });
  const n = 30;
  for (let i = 0; i < n; i++) {
    const y = 1 - (i + 0.5) / n * 2;
    const r = Math.sqrt(1 - y * y);
    const th = Math.PI * (3 - Math.sqrt(5)) * i;
    const dir = new THREE.Vector3(Math.cos(th) * r, y, Math.sin(th) * r);
    const sp = new THREE.Mesh(new THREE.ConeGeometry(0.05, 0.3, 5), spikeMat);
    sp.position.copy(dir.clone().multiplyScalar(0.75));
    sp.quaternion.setFromUnitVectors(new THREE.Vector3(0, 1, 0), dir);
    group.add(sp);
  }
  const evoNodes = [];
  const clips = {};
  return {
    scene: group, animations: [], clips, morphMeshes: [], evoNodes, fallback: true,
    _spin: core,
  };
}
