// Soul-Sprachmodell für Windows: lädt das Modell einmalig herunter und startet llama-server (llama.cpp)
// nur auf diesem PC (127.0.0.1). Keine Cloud, keine Kosten.
const { app, ipcMain } = require('electron');
const path = require('path');
const fs = require('fs');
const os = require('os');
const https = require('https');
const http = require('http');
const crypto = require('crypto');
const { spawn } = require('child_process');

const MODELS = {
  std: { url: 'https://huggingface.co/unsloth/Qwen3-1.7B-GGUF/resolve/main/Qwen3-1.7B-Q4_K_M.gguf', size: 1107409472, sha: 'b139949c5bd74937ad8ed8c8cf3d9ffb1e99c866c823204dc42c0d91fa181897', file: 'soul-std.gguf' },
  pro: { url: 'https://huggingface.co/unsloth/Qwen3-4B-Instruct-2507-GGUF/resolve/main/Qwen3-4B-Instruct-2507-Q4_K_M.gguf', size: 2497281120, sha: '3605803b982cb64aead44f6c1b2ae36e3acdb41d8e46c8a94c6533bc4c67e597', file: 'soul-pro.gguf' }
};
const dir = () => path.join(app.getPath('userData'), 'soul');
const file = (w) => path.join(dir(), MODELS[w].file);
const have = (w) => { try { return fs.statSync(file(w)).size === MODELS[w].size; } catch (e) { return false; } };
const partSize = (w) => { try { return fs.statSync(file(w) + '.part').size; } catch (e) { return 0; } };
function serverExe() {
  const c = [path.join(process.resourcesPath || '', 'llm', 'llama-server.exe'), path.join(__dirname, 'llm', 'llama-server.exe'), path.join(__dirname, 'llm', 'llama-server')];
  return c.find((p) => { try { return fs.existsSync(p); } catch (e) { return false; } }) || null;
}

let win = null, dl = null, srv = null, loaded = '', port = 0;
const send = (type, data) => { try { if (win && !win.isDestroyed()) win.webContents.send('llm-ev', type, data); } catch (e) { } };

function info() {
  const ram = Math.round(os.totalmem() / 1048576);
  let free = 0; try { const s = fs.statfsSync(app.getPath('userData')); free = s.bavail * s.bsize; } catch (e) { }
  const models = {};
  for (const w of Object.keys(MODELS)) models[w] = { have: have(w), size: MODELS[w].size, part: partSize(w) };
  return { ok: !!serverExe(), ram, rec: ram >= 7000 ? 'pro' : 'std', proOk: ram >= 5500, loaded, models, free, dl: dl ? { which: dl.which, done: dl.done, total: dl.total, state: dl.state } : undefined };
}

function get(url, headers, cb, hops = 0) {
  const req = https.get(url, { headers }, (res) => {
    if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location && hops < 6) { res.resume(); get(new URL(res.headers.location, url).toString(), headers, cb, hops + 1); return; }
    cb(null, res, req);
  });
  req.on('error', (e) => cb(e));
  req.setTimeout(30000, () => req.destroy(new Error('timeout')));
  return req;
}

function download(w) {
  if (dl || !MODELS[w]) return;
  const m = MODELS[w], part = file(w) + '.part';
  fs.mkdirSync(dir(), { recursive: true });
  dl = { which: w, done: partSize(w), total: m.size, state: 'run', stop: false, req: null };
  let tries = 0, last = 0;
  const finish = (err) => { const d = dl; dl = null; send('dlEnd', { which: w, ok: !err, err: err || '' }); if (d && d.req) try { d.req.destroy(); } catch (e) { } };
  const verify = () => {
    dl.state = 'check'; send('dl', { which: w, state: 'check', done: m.size, total: m.size });
    const h = crypto.createHash('sha256');
    fs.createReadStream(part).on('data', (b) => h.update(b)).on('error', () => finish('read')).on('end', () => {
      if (h.digest('hex') !== m.sha) { try { fs.unlinkSync(part); } catch (e) { } return finish('checksum'); }
      try { fs.renameSync(part, file(w)); } catch (e) { return finish('rename'); }
      finish(null);
    });
  };
  const step = () => {
    if (dl.stop) return finish('stopped');
    const have0 = partSize(w);
    if (have0 >= m.size) return verify();
    const headers = { 'User-Agent': 'Velora/6.6 (Windows)' };
    if (have0 > 0) headers.Range = 'bytes=' + have0 + '-';
    dl.req = get(m.url, headers, (err, res) => {
      if (err) return retry(err.message);
      if (res.statusCode !== 200 && res.statusCode !== 206) { res.resume(); return retry('http ' + res.statusCode); }
      const out = fs.createWriteStream(part, { flags: res.statusCode === 206 ? 'a' : 'w' });
      dl.done = res.statusCode === 206 ? have0 : 0;
      res.on('data', (b) => {
        if (dl.stop) { res.destroy(); return; }
        dl.done += b.length;
        const now = Date.now(); if (now - last > 400) { last = now; send('dl', { which: w, done: dl.done, total: m.size }); }
      });
      res.pipe(out);
      out.on('finish', () => { tries = 0; if (dl.stop) return finish('stopped'); step(); });
      res.on('error', (e) => retry(e.message));
      res.on('aborted', () => retry('aborted'));
    });
  };
  const retry = (msg) => { if (!dl || dl.stop) return finish('stopped'); if (++tries > 8) return finish(msg); setTimeout(step, 2000 * tries); };
  step();
}

function stopServer() { if (srv) { try { srv.kill(); } catch (e) { } srv = null; } loaded = ''; port = 0; }
function freePort() { return new Promise((res) => { const s = require('net').createServer(); s.listen(0, '127.0.0.1', () => { const p = s.address().port; s.close(() => res(p)); }); }); }
function health(p) { return new Promise((res) => { http.get({ host: '127.0.0.1', port: p, path: '/health', timeout: 1500 }, (r) => { r.resume(); res(r.statusCode === 200); }).on('error', () => res(false)); }); }

async function load(w) {
  if (loaded === w && srv) return port;
  if (!have(w)) throw new Error('missing');
  const exe = serverExe(); if (!exe) throw new Error('no server');
  stopServer();
  const p = await freePort();
  const threads = Math.max(2, Math.min(8, Math.floor(os.cpus().length / 2)));
  srv = spawn(exe, ['-m', file(w), '-c', '4096', '-np', '1', '-t', String(threads), '--host', '127.0.0.1', '--port', String(p), '--no-webui'], { windowsHide: true, stdio: 'ignore' });
  srv.on('exit', () => { srv = null; loaded = ''; });
  for (let i = 0; i < 240; i++) {
    await new Promise((r) => setTimeout(r, 500));
    if (!srv) throw new Error('exit');
    if (await health(p)) { loaded = w; port = p; return p; }
  }
  stopServer(); throw new Error('timeout');
}

// Text erzeugen: der Hauptprozess spricht mit llama-server und reicht die Stücke an die App weiter
const gens = {};
function generate(id, prompt, nPredict, temp) {
  if (!port) { send('end', { id, n: -1 }); return; }
  const body = JSON.stringify({ prompt, n_predict: nPredict, temperature: temp, top_p: 0.9, top_k: 40, min_p: 0.05, repeat_penalty: 1.1, repeat_last_n: 64, stream: true, cache_prompt: true, stop: ['<|im_end|>', '<|im_start|>'] });
  let n = 0, buf = '';
  const req = http.request({ host: '127.0.0.1', port, path: '/completion', method: 'POST', headers: { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(body) } }, (res) => {
    res.setEncoding('utf8');
    res.on('data', (c) => {
      buf += c; const lines = buf.split('\n'); buf = lines.pop();
      let t = '';
      for (const l of lines) { if (!l.startsWith('data: ')) continue; try { const j = JSON.parse(l.slice(6)); if (j.content) { t += j.content; n++; } } catch (e) { } }
      if (t) send('tok', { id, t });
    });
    res.on('end', () => { delete gens[id]; send('end', { id, n }); });
  });
  req.on('error', () => { delete gens[id]; send('end', { id, n: n || -1 }); });
  gens[id] = req;
  req.end(body);
}

function setup(w) {
  win = w;
  ipcMain.on('llm-info', (e) => { e.returnValue = info(); });
  ipcMain.on('llm-download', (e, which) => download(which));
  ipcMain.on('llm-cancel', () => { if (dl) { dl.stop = true; if (dl.req) try { dl.req.destroy(); } catch (e) { } } });
  ipcMain.on('llm-del', (e, which) => { if (loaded === which) stopServer(); for (const f of [file(which), file(which) + '.part']) try { fs.unlinkSync(f); } catch (x) { } });
  ipcMain.handle('llm-load', (e, which) => load(which));
  ipcMain.on('llm-gen', (e, id, prompt, n, temp) => generate(id, prompt, n, temp));
  ipcMain.on('llm-stop', () => { for (const k of Object.keys(gens)) try { gens[k].destroy(); } catch (x) { } });
  app.on('before-quit', stopServer);
}
module.exports = { setup };
