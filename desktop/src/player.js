// Plays a stream in a <video>, trying each way to open it until one works (same rules as Android).
'use strict';

class RetryingPlayer {
  constructor(video, { onReady, onFailed, onEnded } = {}) {
    this.video = video;
    this.onReady = onReady || (() => {});
    this.onFailed = onFailed || (() => {});
    this.onEnded = onEnded || (() => {});
    this.engine = null;
    this.token = 0;
    video.addEventListener('ended', () => this.onEnded());
  }

  play(attempts, { live = false, start = 0 } = {}) {
    this.attempts = attempts;
    this.live = live;
    this.start = start;
    this.step = 0;
    this.waited = false;
    this.lastKind = null;
    this.load();
  }

  current() {
    return this.attempts && this.attempts[this.step];
  }

  async load() {
    const token = ++this.token;
    this.teardown();
    const a = this.current();
    if (!a) return;
    await window.stv.setHeaders({ ua: a.ua, referer: a.referer || null });
    if (token !== this.token) return;
    const url = a.url;
    const v = this.video;
    // Listeners belong to this attempt only, so a late event from the previous one is ignored.
    const onPlaying = () => token === this.token && this.ready();
    const onError = async () => {
      if (token !== this.token) return;
      let kind = v.error && v.error.code === 4 ? 'format' : 'network';
      // Chromium reports an unreachable address like an unsupported format: ask the server to tell them apart.
      if (kind === 'format' && !this.engineIsLibrary) {
        try {
          const res = await fetch(url, { method: 'GET', headers: { Range: 'bytes=0-1' } });
          if (!res.ok && res.status !== 416) {
            this.httpCode = res.status;
            kind = 'network';
          }
          if (res.body) res.body.cancel();
        } catch (e) {
          kind = 'network';
        }
      }
      if (token === this.token) this.fail(kind);
    };
    v.addEventListener('playing', onPlaying);
    v.addEventListener('error', onError);
    this.unlisten = () => {
      v.removeEventListener('playing', onPlaying);
      v.removeEventListener('error', onError);
    };
    clearTimeout(this.timer);
    // A stream that never starts counts as failed.
    this.httpCode = null;
    this.timer = setTimeout(() => token === this.token && this.fail('network'), 25000);

    const isHls = /\.m3u8(\?|$)/i.test(url) || /\/hls\/|manifest/i.test(url);
    const isTs = /\.ts(\?|$)/i.test(url) || (this.live && !/\.[a-z0-9]{2,4}(\?|$)/i.test(url.split('/').pop()));
    if (isHls && window.Hls && Hls.isSupported()) {
      const hls = new Hls({ enableWorker: true, lowLatencyMode: false, startPosition: this.start > 0 ? this.start / 1000 : -1 });
      hls.on(Hls.Events.ERROR, (_e, data) => {
        if (!data.fatal || token !== this.token) return;
        const code = data.response && data.response.code;
        this.httpCode = code || null;
        this.fail(data.type === Hls.ErrorTypes.MEDIA_ERROR ? 'format' : 'network');
      });
      hls.loadSource(url);
      hls.attachMedia(v);
      this.engine = { destroy: () => hls.destroy() };
      this.engineIsLibrary = true;
    } else if (isTs && window.mpegts && mpegts.getFeatureList().mseLivePlayback) {
      const p = mpegts.createPlayer({ type: 'mpegts', isLive: this.live, url }, { enableWorker: true, lazyLoad: false, liveBufferLatencyChasing: true });
      p.on(mpegts.Events.ERROR, (type, detail, info) => {
        if (token !== this.token) return;
        this.httpCode = info && info.code ? info.code : null;
        this.fail(type === mpegts.ErrorTypes.MEDIA_ERROR ? 'format' : 'network');
      });
      p.attachMediaElement(v);
      p.load();
      this.engine = { destroy: () => { p.pause(); p.unload(); p.detachMediaElement(); p.destroy(); } };
      this.engineIsLibrary = true;
    } else {
      this.engineIsLibrary = false;
      v.src = url;
      if (this.start > 0) v.currentTime = this.start / 1000;
      this.engine = { destroy: () => { v.removeAttribute('src'); v.load(); } };
    }
    v.play().catch(() => {});
  }

  ready() {
    clearTimeout(this.timer);
    const a = this.current();
    if (!a) return;
    if (Repo.isXtream) Prefs.userAgent = a.ua;
    if (this.live && Repo.isXtream) {
      if (/\.ts$/.test(a.url)) Prefs.liveFormat = 'ts';
      else if (/\.m3u8$/.test(a.url)) Prefs.liveFormat = 'm3u8';
    }
    this.onReady();
  }

  fail(kind) {
    clearTimeout(this.timer);
    if (!this.attempts || !this.current()) return;
    const token = this.token;
    this.lastKind = kind;
    if (!this.live && this.video.currentTime > 0) this.start = this.video.currentTime * 1000;
    if (kind === 'network' || this.attempts.length > 1) {
      // The first refusal often means the previous channel still holds the connection.
      if (!this.waited) {
        this.waited = true;
        setTimeout(() => token === this.token && this.load(), 2500);
        return;
      }
      if (this.step + 1 < this.attempts.length) {
        this.step++;
        this.load();
        return;
      }
    }
    this.teardown();
    this.onFailed(this.describe(kind));
  }

  describe(kind) {
    const c = this.httpCode;
    if (c === 401 || c === 403) return `Le serveur refuse l'accès (code ${c}).\nVérifiez que l'abonnement est actif et que personne d'autre ne l'utilise en ce moment.`;
    if (c === 404) return 'Ce contenu n\'existe plus sur le serveur (code 404).';
    if (c === 429 || c === 458 || c === 509) return `Trop de connexions en même temps (code ${c}).\nFermez l'abonnement sur les autres appareils.`;
    if (c) return `Le serveur a renvoyé une erreur (code ${c}).`;
    if (kind === 'format') return 'Format vidéo non pris en charge par le lecteur intégré.';
    return 'Connexion au flux impossible.';
  }

  teardown() {
    if (this.unlisten) {
      this.unlisten();
      this.unlisten = null;
    }
    if (this.engine) {
      try {
        this.engine.destroy();
      } catch (e) { /* already stopped */ }
      this.engine = null;
    }
  }

  stop() {
    this.token++;
    clearTimeout(this.timer);
    this.attempts = null;
    this.teardown();
  }
}
