// The only system functions the page can use.
const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('stv', {
  setHeaders: (h) => ipcRenderer.invoke('set-headers', h),
  toggleFullscreen: () => ipcRenderer.invoke('toggle-fullscreen'),
  setFullscreen: (on) => ipcRenderer.invoke('set-fullscreen', on),
  openExternal: (url) => ipcRenderer.invoke('open-external', url),
  openVlc: (url) => ipcRenderer.invoke('open-vlc', url),
  version: () => ipcRenderer.invoke('version'),
  quit: () => ipcRenderer.invoke('quit'),
});
