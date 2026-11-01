// Kennzeichnet die Windows-Version (Testhilfen ohne GPS, Sprachausgabe des Systems)
window.__desktop = true;
// Fehlt die 3D-Grafik, startet die App mit Software-Grafik neu
const { ipcRenderer } = require('electron');
window.__needSoftGL = () => ipcRenderer.send('soft-gl');
// Soul-Sprachmodell auf dem PC (siehe llm.js)
window.__velLLM = {
  info: () => ipcRenderer.sendSync('llm-info'),
  download: (w) => ipcRenderer.send('llm-download', w),
  cancel: () => ipcRenderer.send('llm-cancel'),
  del: (w) => ipcRenderer.send('llm-del', w),
  load: (w) => ipcRenderer.invoke('llm-load', w),
  generate: (id, prompt, n, temp) => ipcRenderer.send('llm-gen', id, prompt, n, temp),
  stop: () => ipcRenderer.send('llm-stop'),
  onEvent: (cb) => ipcRenderer.on('llm-ev', (e, type, data) => cb(type, data))
};
