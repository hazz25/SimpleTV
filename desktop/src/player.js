// Plays a stream in a <video>, trying each way to open it until one works (same rules as Android).
// Once a stream plays, small hiccups are repaired in place instead of restarting it.
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
    this.repairs = [];
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
    this.started = false;
    this.httpCode = null;
    await window.stv.setHeaders({ ua: a.ua, referer: a.referer || null });
    if (token !== this.token) return;
    const url = a.url;
    const v = this.video;

    // Listeners belong to this attempt only, so a late event from the previous one is ignored.
    const onPlaying = () => {
      if (token !== this.token) return;
      clearTimeout(this.stallTimer);
      if (!this.started) this.ready();
    };
    const onWaiting = () => {
      if (token !== this.token || !this.started) return;
      // Still frozen after a while: reopen the same stream quietly.
      clearTimeout(this.stallTimer);
      this.stallTimer = setTimeout(() => token === this.token && this.repair(), 12000);
    };
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
      if (token === this.token) this.problem(kind);
    };
    v.addEventListener('playing', onPlaying);
    v.addEventListener('waiting', onWaiting);
    v.addEventListener('error', onError);
    this.unlisten = () => {
      v.removeEventListener('playing', onPlaying);
      v.removeEventListener('waiting', onWaiting);
      v.removeEventListener('error', onError);
    };
    clearTimeout(this.timer);
    // A stream that never starts counts as failed.
    this.timer = setTimeout(() => token === this.token && !this.started && this.problem('network'), 25000);

    const file = url.split('?')[0].split('/').pop();
    const isHls = /\.m3u8$/i.test(file) || /\/hls\/|manifest/i.test(url);
    const isTs = /\.ts$/i.test(file) || (this.live && !/\.[a-z0-9]{2,4}$/i.test(file));

    if (isHls && window.Hls && Hls.isSupported()) {
      const hls = new Hls({
        enableWorker: true,
        lowLatencyMode: false,
        // Start from a good picture instead of the lowest quality, then adapt.
        abrEwmaDefaultEstimate: 8000000,
        capLevelToPlayerSize: false,
        maxBufferLength: 30,
        liveSyncDurationCount: 4,
        fragLoadingMaxRetry: 6,
        manifestLoadingMaxRetry: 3,
        levelLoadingMaxRetry: 4,
        startPosition: this.start > 0 ? this.start / 1000 : -1,
      });
      let mediaFixes = 0;
      hls.on(Hls.Events.ERROR, (_e, data) => {
        if (!data.fatal || token !== this.token) return;
        const code = data.response && data.response.code;
        this.httpCode = code || null;
        if (this.started && data.type === Hls.ErrorTypes.NETWORK_ERROR) {
          hls.startLoad();
          return;
        }
        if (data.type === Hls.ErrorTypes.MEDIA_ERROR && mediaFixes < 2) {
          mediaFixes++;
          if (mediaFixes === 2) hls.swapAudioCodec();
          hls.recoverMediaError();
          return;
        }
        this.problem(data.type === Hls.ErrorTypes.MEDIA_ERROR ? 'format' : 'network');
      });
      hls.loadSource(url);
      hls.attachMedia(v);
      this.engine = { destroy: () => hls.destroy() };
      this.engineIsLibrary = true;
    } else if (isTs && window.mpegts && mpegts.getFeatureList().mseLivePlayback) {
      const p = mpegts.createPlayer({ type: 'mpegts', isLive: this.live, url }, {
        enableWorker: true,
        enableStashBuffer: true,
        stashInitialSize: 1024 * 1024,
        lazyLoad: false,
        // Chasing the live edge makes the picture jump and go black: keep a steady buffer instead.
        liveBufferLatencyChasing: false,
        autoCleanupSourceBuffer: true,
        autoCleanupMaxBackwardDuration: 60,
        autoCleanupMinBackwardDuration: 30,
      });
      p.on(mpegts.Events.ERROR, (type, _detail, info) => {
        if (token !== this.token) return;
        this.httpCode = info && info.code ? info.code : null;
        this.problem(type === mpegts.ErrorTypes.MEDIA_ERROR ? 'format' : 'network');
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
    this.started = true;
    const a = this.current();
    if (!a) return;
    if (Repo.isXtream) Prefs.userAgent = a.ua;
    if (this.live && Repo.isXtream) {
      if (/\.ts$/.test(a.url)) Prefs.liveFormat = 'ts';
      else if (/\.m3u8$/.test(a.url)) Prefs.liveFormat = 'm3u8';
    }
    this.onReady();
  }

  /** Something went wrong: repair a stream that was playing, or try the next way to open it. */
  problem(kind) {
    if (this.started) {
      this.repair();
      return;
    }
    this.fail(kind);
  }

  /** Reopens the same stream, where it was. Gives up after too many repairs in a short time. */
  repair() {
    const now = Date.now();
    this.repairs = (this.repairs || []).filter((t) => now - t < 60000);
    if (this.repairs.length >= 4) {
      this.started = false;
      this.fail('network');
      return;
    }
    this.repairs.push(now);
    if (!this.live && this.video.currentTime > 0) this.start = this.video.currentTime * 1000;
    const token = this.token;
    setTimeout(() => token === this.token && this.load(), 800);
  }

  fail(kind) {
    clearTimeout(this.timer);
    clearTimeout(this.stallTimer);
    if (!this.attempts || !this.current()) return;
    const token = this.token;
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
    clearTimeout(this.stallTimer);
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
