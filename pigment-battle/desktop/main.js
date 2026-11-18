// Desktop-Version (Windows/Mac/Linux) für Steam oder itch.io
const { app, BrowserWindow } = require('electron');
const path = require('path');

function createWindow() {
  const win = new BrowserWindow({
    width: 1280, height: 720, minWidth: 960, minHeight: 540,
    backgroundColor: '#0b0b0d', autoHideMenuBar: true, title: 'Pigment Krieg',
    icon: path.join(__dirname, '..', 'assets', 'icon.png'),
    webPreferences: { contextIsolation: true, sandbox: true },
  });
  win.loadFile(path.join(__dirname, '..', 'index.html'));
  win.webContents.on('before-input-event', (e, input) => {
    if (input.type === 'keyDown' && (input.key === 'F11' || (input.alt && input.key === 'Enter'))) {
      win.setFullScreen(!win.isFullScreen()); e.preventDefault();
    }
  });
}
app.whenReady().then(createWindow);
app.on('window-all-closed', () => app.quit());
