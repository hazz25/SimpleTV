// Simple TV for Windows: the window, request headers for IPTV servers, and a few system actions.
const { app, BrowserWindow, ipcMain, session, shell, Menu } = require('electron');
const path = require('path');
const fs = require('fs');
const { spawn } = require('child_process');

const DEFAULT_UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 SimpleTV/1.0';

// Header values for the stream being opened: IPTV servers often check the player name or the
// page a stream is opened from. GitHub requests (updates, free lists) are left untouched.
let streamHeaders = { ua: DEFAULT_UA, referer: null };

function isHttpUrl(u) {
  return typeof u === 'string' && /^https?:\/\//i.test(u);
}

function createWindow() {
  Menu.setApplicationMenu(null);
  const win = new BrowserWindow({
    width: 1280,
    height: 760,
    minWidth: 960,
    minHeight: 600,
    backgroundColor: '#0B1020',
    title: 'Simple TV',
    icon: path.join(__dirname, 'assets', 'icon.png'),
    autoHideMenuBar: true,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      // IPTV servers do not send the headers a browser page needs to read their streams.
      // The app only ever loads its own local page, so this is limited to reading streams and lists.
      webSecurity: false,
    },
  });

  session.defaultSession.webRequest.onBeforeSendHeaders((details, callback) => {
    const headers = details.requestHeaders;
    const url = details.url;
    if (isHttpUrl(url) && !/github(usercontent)?\.com/i.test(url)) {
      headers['User-Agent'] = streamHeaders.ua || DEFAULT_UA;
      if (streamHeaders.referer) headers['Referer'] = streamHeaders.referer;
    }
    callback({ requestHeaders: headers });
  });

  // Never navigate away from the app; links open in the normal browser.
  win.webContents.setWindowOpenHandler(({ url }) => {
    if (/^https:\/\//i.test(url)) shell.openExternal(url);
    return { action: 'deny' };
  });
  win.webContents.on('will-navigate', (e) => e.preventDefault());

  win.loadFile(path.join(__dirname, 'src', 'index.html'));
}

ipcMain.handle('set-headers', (_e, h) => {
  streamHeaders = {
    ua: (h && typeof h.ua === 'string' && h.ua) || DEFAULT_UA,
    referer: h && isHttpUrl(h.referer) ? h.referer : null,
  };
});

ipcMain.handle('toggle-fullscreen', (e) => {
  const w = BrowserWindow.fromWebContents(e.sender);
  w.setFullScreen(!w.isFullScreen());
  return w.isFullScreen();
});

ipcMain.handle('set-fullscreen', (e, on) => {
  BrowserWindow.fromWebContents(e.sender).setFullScreen(!!on);
});

ipcMain.handle('open-external', (_e, url) => {
  if (typeof url === 'string' && /^https:\/\//i.test(url)) shell.openExternal(url);
});

// Fallback for films in a format Windows' built-in decoders refuse (AC3 sound, some MKV files).
ipcMain.handle('open-vlc', (_e, url) => {
  if (!isHttpUrl(url)) return false;
  const candidates = [
    process.env['ProgramFiles'] && path.join(process.env['ProgramFiles'], 'VideoLAN', 'VLC', 'vlc.exe'),
    process.env['ProgramFiles(x86)'] && path.join(process.env['ProgramFiles(x86)'], 'VideoLAN', 'VLC', 'vlc.exe'),
  ].filter(Boolean);
  const exe = candidates.find((p) => fs.existsSync(p));
  if (!exe) return false;
  spawn(exe, [url, `--http-user-agent=${streamHeaders.ua}`], { detached: true, stdio: 'ignore' }).unref();
  return true;
});

ipcMain.handle('version', () => app.getVersion());
ipcMain.handle('quit', () => app.quit());

app.whenReady().then(createWindow);
app.on('window-all-closed', () => app.quit());
