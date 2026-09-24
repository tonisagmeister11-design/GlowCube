// Baut die komplette Anwendung zu einer einzigen index.html: JS (three.js, Spiel,
// eingebettete Blender-GLBs als base64) und CSS werden inline eingebettet. Es wird
// kein Entwicklungsserver, kein Blender und keine externe Datei zum Spielen benötigt.
import esbuild from 'esbuild';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { execFileSync } from 'node:child_process';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

// 1) sicherstellen, dass Daten + eingebettete Modelle aktuell sind
if (!fs.existsSync(path.join(root, 'src/data/world.json'))) execFileSync(process.execPath, [path.join(root, 'tools/build-data.mjs')], { stdio: 'inherit' });
if (!fs.existsSync(path.join(root, 'src/generated/models.js'))) execFileSync(process.execPath, [path.join(root, 'tools/gen-assets.mjs')], { stdio: 'inherit' });
if (!fs.existsSync(path.join(root, 'src/generated/audio.js'))) execFileSync(process.execPath, [path.join(root, 'tools/gen-audio.mjs')], { stdio: 'inherit' });

const threeAddons = {
  name: 'three-addons',
  setup(b) {
    b.onResolve({ filter: /^three\/addons\// }, (args) => ({
      path: path.join(root, 'node_modules/three/examples/jsm', args.path.replace('three/addons/', '')),
    }));
  },
};

const result = await esbuild.build({
  entryPoints: [path.join(root, 'src/main.js')],
  bundle: true,
  minify: true,
  format: 'iife',
  target: 'es2020',
  loader: { '.css': 'text', '.json': 'json' },
  plugins: [threeAddons],
  write: false,
  legalComments: 'none',
});

let js = result.outputFiles[0].text;

// CSS wird als Text importiert und über ./style.css injiziert -> wir hängen es als <style> an
// (main.js importiert './style.css' als Text; esbuild liefert den String, den wir hier
//  in ein <style>-Tag stecken, indem main.js ihn nutzt. Einfacher: CSS separat einlesen.)
const css = fs.readFileSync(path.join(root, 'src/style.css'), 'utf8');

const html = `<!DOCTYPE html>
<html lang="de">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
<meta name="theme-color" content="#0a0305">
<title>PANDEMIC · GlowCube</title>
<style>${css}</style>
</head>
<body>
<div id="app"></div>
<div id="loading">Lade Erreger…</div>
<script>${js}</script>
</body>
</html>`;

const outFile = path.join(root, 'dist', 'index.html');
fs.mkdirSync(path.dirname(outFile), { recursive: true });
fs.writeFileSync(outFile, html);
const kb = (Buffer.byteLength(html) / 1024).toFixed(0);
console.log(`dist/index.html geschrieben: ${(kb / 1024).toFixed(2)} MB (JS ${(js.length / 1024 / 1024).toFixed(2)} MB)`);
