/** Run with DotcrateWebEditor's vite-node; source meshes and icon pipeline stay unchanged. */
import { readFileSync, writeFileSync } from 'node:fs';
import * as THREE from '/Users/noguchinaoya/git/DotcrateWebEditor/node_modules/three/build/three.module.js';
import { GLTFExporter } from '/Users/noguchinaoya/git/DotcrateWebEditor/node_modules/three/examples/jsm/exporters/GLTFExporter.js';
import { JSDOM } from '/Users/noguchinaoya/git/DotcrateWebEditor/node_modules/jsdom/lib/api.js';
import { parseIconSvg } from '/Users/noguchinaoya/git/DotcrateWebEditor/src/icon/normalize';
import { buildOutline, DEFAULT_PLACEMENT } from '/Users/noguchinaoya/git/DotcrateWebEditor/src/icon/placement';
import { embossGeometry } from '/Users/noguchinaoya/git/DotcrateWebEditor/src/preview/iconMesh';
const root = '/Users/noguchinaoya/git/DotcrateWebEditor';
globalThis.DOMParser = new JSDOM('').window.DOMParser;
globalThis.FileReader = class {
  result: unknown;
  onloadend: (() => void) | null = null;
  async readAsArrayBuffer(blob: Blob) { this.result = await blob.arrayBuffer(); this.onloadend?.(); }
};
const asset = JSON.parse(readFileSync(`${root}/public/model/deep-flat.json`, 'utf8'));
const icons = JSON.parse(readFileSync(`${root}/public/icons/material-symbols-filled.json`, 'utf8'));
const source = parseIconSvg(`<svg xmlns="http://www.w3.org/2000/svg" viewBox="${icons.viewBox}"><path d="${icons.icons.question_mark}"/></svg>`);
const group = new THREE.Group();
for (const part of asset.parts) {
  const geom = new THREE.BufferGeometry();
  for (const [attribute, value] of [['position', part.positions], ['normal', part.normals]]) {
    const bytes = Buffer.from(value, 'base64');
    geom.setAttribute(attribute, new THREE.BufferAttribute(new Float32Array(bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength)), 3));
  }
  const mesh = new THREE.Mesh(geom, new THREE.MeshStandardMaterial({color: part.role === 'housing' ? '#7E9E7C' : '#FAF9F6', roughness: .85}));
  mesh.name = `tag-box-${part.role}`;
  group.add(mesh);
}
const mark = new THREE.Mesh(embossGeometry(buildOutline(source, DEFAULT_PLACEMENT), .4), new THREE.MeshStandardMaterial({color:'#E8907E', roughness:.85}));
mark.position.z = 18;
mark.name = 'question-mark';
group.add(mark);
// Stand the square upright, front +Z; source geometry is already XY with Z depth.
const bounds = new THREE.Box3().setFromObject(group);
group.position.copy(bounds.getCenter(new THREE.Vector3()).negate());
const scene = new THREE.Scene();
scene.add(group);
const data = await new GLTFExporter().parseAsync(scene, {binary:true});
const out = '/Users/noguchinaoya/git/CLumo/companion/android/app/src/main/assets/models/unknown-tag-box.glb';
writeFileSync(out, Buffer.from(data));
console.log(JSON.stringify({out, bytes:data.byteLength, bounds:bounds.getSize(new THREE.Vector3()).toArray(), parts:group.children.length}));
