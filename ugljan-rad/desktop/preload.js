// Kennzeichnet die Windows-Version (Testhilfen ohne GPS, Sprachausgabe des Systems)
window.__desktop = true;
// Fehlt die 3D-Grafik, startet die App mit Software-Grafik neu
const { ipcRenderer } = require('electron');
window.__needSoftGL = () => ipcRenderer.send('soft-gl');
