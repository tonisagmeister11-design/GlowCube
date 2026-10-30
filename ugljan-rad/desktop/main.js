// Velora für Windows: dieselbe App wie auf Android, in einem Electron-Fenster
const { app, BrowserWindow, protocol, net, Menu, session, shell } = require('electron');
const path = require('path');
const { pathToFileURL } = require('url');

// Eigenes Schema statt file://, damit Karte, Schriften und Online-Dienste wie im Browser laden
protocol.registerSchemesAsPrivileged([
  { scheme: 'app', privileges: { standard: true, secure: true, supportFetchAPI: true, corsEnabled: true, stream: true } }
]);

app.whenReady().then(() => {
  const root = path.join(__dirname, 'assets');
  protocol.handle('app', (req) => {
    let p = decodeURIComponent(new URL(req.url).pathname);
    if (p === '/' || p === '') p = '/index.html';
    const file = path.normalize(path.join(root, p));
    if (!file.startsWith(root)) return new Response('nicht erlaubt', { status: 403 });
    return net.fetch(pathToFileURL(file).toString());
  });
  Menu.setApplicationMenu(null);
  session.defaultSession.setPermissionRequestHandler((wc, perm, cb) => cb(perm === 'geolocation' || perm === 'fullscreen'));
  const win = new BrowserWindow({
    width: 440, height: 900, minWidth: 360, minHeight: 600, title: 'Velora', backgroundColor: '#14181d',
    icon: path.join(__dirname, 'icon.png'),
    webPreferences: { preload: path.join(__dirname, 'preload.js'), contextIsolation: false, sandbox: false }
  });
  // Links (Website, Telefon) im Standardprogramm öffnen
  win.webContents.setWindowOpenHandler(({ url }) => { shell.openExternal(url); return { action: 'deny' }; });
  win.webContents.on('will-navigate', (e, url) => { if (!url.startsWith('app://')) { e.preventDefault(); shell.openExternal(url); } });
  win.loadURL('app://ugljan/index.html');
});
app.on('window-all-closed', () => app.quit());
