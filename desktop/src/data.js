// Lists, accounts and everything the app remembers. Same rules as the Android app.
'use strict';

const FREE_BASE = 'https://raw.githubusercontent.com/hazz25/SimpleTV/main/free';
const API_UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 SimpleTV/1.0';

// ---- Storage ---------------------------------------------------------------------------------

const Store = {
  get(key, fallback) {
    try {
      const v = localStorage.getItem(key);
      return v === null ? fallback : JSON.parse(v);
    } catch (e) {
      return fallback;
    }
  },
  set(key, value) {
    localStorage.setItem(key, JSON.stringify(value));
  },
  remove(key) {
    localStorage.removeItem(key);
  },
};

function newId() {
  return Math.random().toString(36).slice(2, 10);
}

function hostName(url) {
  return (url.split('://')[1] || url).split('/')[0].split(':')[0] || 'Ma liste';
}

const Prefs = {
  playlists() {
    return Store.get('playlists', []);
  },
  savePlaylists(list) {
    Store.set('playlists', list);
  },
  get active() {
    const all = this.playlists();
    const id = Store.get('activeList', null);
    return all.find((p) => p.id === id) || all[0] || null;
  },
  setActive(id) {
    Store.set('activeList', id);
  },
  addPlaylist(p) {
    const all = this.playlists();
    const same = all.findIndex((o) => o.type === p.type && (p.type === 'xtream'
      ? o.server === p.server && o.user === p.user
      : o.url === p.url));
    if (same >= 0) {
      p.id = all[same].id;
      all[same] = p;
    } else {
      all.push(p);
    }
    this.savePlaylists(all);
    this.setActive(p.id);
    return p;
  },
  removePlaylist(id) {
    this.savePlaylists(this.playlists().filter((p) => p.id !== id));
    for (let i = localStorage.length - 1; i >= 0; i--) {
      const k = localStorage.key(i);
      if (k && k.startsWith(`${id}_`)) localStorage.removeItem(k);
    }
    if (Store.get('activeList', null) === id) Store.remove('activeList');
  },

  // Data that belongs to the active list.
  key(name) {
    return `${(this.active && this.active.id) || 'none'}_${name}`;
  },
  list(name) {
    return Store.get(this.key(name), []);
  },
  saveList(name, l) {
    Store.set(this.key(name), l);
  },
  favorites(type) {
    return this.list(`fav_${type}`);
  },
  toggleFavorite(type, id) {
    const l = this.favorites(type);
    const i = l.indexOf(id);
    if (i >= 0) l.splice(i, 1);
    else l.unshift(id);
    this.saveList(`fav_${type}`, l);
    return i < 0;
  },
  recents(type) {
    return this.list(`recent_${type}`);
  },
  addRecent(type, id) {
    const l = this.recents(type).filter((x) => x !== id);
    l.unshift(id);
    this.saveList(`recent_${type}`, l.slice(0, 60));
  },
  clearRecents() {
    ['live', 'vod', 'series'].forEach((t) => Store.remove(this.key(`recent_${t}`)));
  },
  hidden(type) {
    return new Set(this.list(`hidden_${type}`));
  },
  hideCategory(type, id) {
    const l = this.list(`hidden_${type}`);
    if (!l.includes(id)) l.push(id);
    this.saveList(`hidden_${type}`, l);
  },
  hiddenCount() {
    return ['live', 'vod', 'series'].reduce((n, t) => n + this.list(`hidden_${t}`).length, 0);
  },
  clearHidden() {
    ['live', 'vod', 'series'].forEach((t) => Store.remove(this.key(`hidden_${t}`)));
  },
  position(key) {
    return Store.get(this.key(`pos_${key}`), 0);
  },
  savePosition(key, ms) {
    Store.set(this.key(`pos_${key}`), ms);
  },
  get lastLiveCategory() {
    return Store.get(this.key('lastLiveCat'), null);
  },
  set lastLiveCategory(v) {
    Store.set(this.key('lastLiveCat'), v);
  },

  // Shared by all lists.
  get liveFormat() {
    return Store.get('liveFormat', 'ts');
  },
  set liveFormat(v) {
    Store.set('liveFormat', v);
  },
  get userAgent() {
    return Store.get('userAgent', null);
  },
  set userAgent(v) {
    Store.set('userAgent', v);
  },
  get invertZap() {
    return Store.get('invertZap', false);
  },
  set invertZap(v) {
    Store.set('invertZap', v);
  },
};

// ---- HTTP ------------------------------------------------------------------------------------

async function getText(url) {
  let res;
  try {
    res = await fetch(url, { cache: 'no-store' });
  } catch (e) {
    throw new Error('Connexion au serveur impossible. Vérifiez l\'adresse et votre connexion Internet.');
  }
  if (res.status === 401 || res.status === 403) {
    throw new Error(`Accès refusé par le serveur (${res.status}). Vérifiez vos identifiants.`);
  }
  if (!res.ok) throw new Error(`Le serveur a répondu avec l'erreur ${res.status}`);
  return res.text();
}

async function getJson(url) {
  const text = await getText(url);
  try {
    return JSON.parse(text);
  } catch (e) {
    throw new Error('Réponse inattendue du serveur.');
  }
}

const str = (v) => (v === null || v === undefined ? '' : String(v).trim());
const objects = (v) => (Array.isArray(v) ? v : v && typeof v === 'object' ? Object.values(v) : [])
  .filter((o) => o && typeof o === 'object');

function b64(s) {
  try {
    return decodeURIComponent(escape(atob(s))).trim();
  } catch (e) {
    return s;
  }
}

// ---- Xtream ----------------------------------------------------------------------------------

const Xtream = {
  normalizeServer(raw) {
    let s = raw.trim();
    if (!/^https?:\/\//i.test(s)) s = `http://${s}`;
    s = s.split('?')[0];
    for (const suffix of ['/player_api.php', '/get.php', '/xmltv.php']) {
      const i = s.toLowerCase().indexOf(suffix);
      if (i >= 0) s = s.slice(0, i);
    }
    return s.replace(/\/+$/, '');
  },
  parseLink(raw) {
    const text = raw.trim();
    if (!/username=/i.test(text)) return null;
    try {
      const u = new URL(/^https?:/i.test(text) ? text : `http://${text}`);
      const user = u.searchParams.get('username');
      const pass = u.searchParams.get('password');
      if (!user || !pass) return null;
      return { server: this.normalizeServer(u.href), user, pass };
    } catch (e) {
      return null;
    }
  },
  api(a, action, extra = '') {
    return `${a.server}/player_api.php?username=${encodeURIComponent(a.user)}&password=${encodeURIComponent(a.pass)}`
      + (action ? `&action=${action}` : '') + extra;
  },
  liveUrl: (a, id, ext) => `${a.server}/live/${a.user}/${a.pass}/${id}.${ext}`,
  movieUrl: (a, id, ext) => `${a.server}/movie/${a.user}/${a.pass}/${id}.${ext}`,
  episodeUrl: (a, id, ext) => `${a.server}/series/${a.user}/${a.pass}/${id}.${ext}`,

  async login(a) {
    const root = await getJson(this.api(a, null));
    const ui = root && root.user_info;
    const auth = ui && str(ui.auth);
    if (!ui || (auth !== '1' && auth.toLowerCase() !== 'true')) throw new Error('Identifiants incorrects');
    return {
      status: str(ui.status),
      expDate: Number(ui.exp_date) || null,
      maxConnections: str(ui.max_connections),
    };
  },
  async categories(a, action) {
    return objects(await getJson(this.api(a, action)))
      .filter((c) => c.category_id !== undefined)
      .map((c) => ({ id: str(c.category_id), name: str(c.category_name) || 'Sans nom' }));
  },
  async liveStreams(a) {
    return objects(await getJson(this.api(a, 'get_live_streams')))
      .filter((s) => s.stream_id !== undefined)
      .map((s) => ({
        id: str(s.stream_id), num: Number(s.num) || 0, name: str(s.name),
        icon: str(s.stream_icon) || null, cat: str(s.category_id),
      }));
  },
  async movies(a) {
    return objects(await getJson(this.api(a, 'get_vod_streams')))
      .filter((s) => s.stream_id !== undefined)
      .map((s) => ({
        id: str(s.stream_id), name: str(s.name), image: str(s.stream_icon) || null, cat: str(s.category_id),
        rating: str(s.rating) && str(s.rating) !== '0' ? str(s.rating) : null,
        added: Number(s.added) || 0, ext: str(s.container_extension) || 'mp4',
      }));
  },
  async series(a) {
    return objects(await getJson(this.api(a, 'get_series')))
      .filter((s) => s.series_id !== undefined)
      .map((s) => ({
        id: str(s.series_id), name: str(s.name), image: str(s.cover) || null, cat: str(s.category_id),
        rating: str(s.rating) && str(s.rating) !== '0' ? str(s.rating) : null,
        added: Number(s.last_modified) || 0, ext: '',
      }));
  },
  async shortEpg(a, id) {
    const root = await getJson(this.api(a, 'get_short_epg', `&stream_id=${id}&limit=4`));
    return objects(root && root.epg_listings).map((e) => ({
      title: b64(str(e.title)), start: Number(e.start_timestamp) || 0, end: Number(e.stop_timestamp) || 0,
    }));
  },
  async movieInfo(a, id) {
    const root = await getJson(this.api(a, 'get_vod_info', `&vod_id=${id}`));
    const info = (root && root.info && typeof root.info === 'object' && !Array.isArray(root.info)) ? root.info : {};
    const data = (root && root.movie_data) || {};
    const backdrop = Array.isArray(info.backdrop_path) ? info.backdrop_path[0] : info.backdrop_path;
    return {
      plot: str(info.plot) || str(info.description),
      genre: str(info.genre),
      year: str(info.releasedate).slice(0, 4) || str(info.year),
      duration: str(info.duration),
      rating: str(info.rating),
      cast: str(info.cast) || str(info.actors),
      director: str(info.director),
      poster: str(info.movie_image) || str(info.cover_big) || null,
      backdrop: str(backdrop) || null,
      ext: str(data.container_extension) || null,
    };
  },
  async seriesInfo(a, id) {
    const root = await getJson(this.api(a, 'get_series_info', `&series_id=${id}`));
    const info = (root && root.info && typeof root.info === 'object' && !Array.isArray(root.info)) ? root.info : {};
    const names = {};
    objects(root && root.seasons).forEach((s) => {
      if (str(s.name)) names[Number(s.season_number)] = str(s.name);
    });
    const bySeason = new Map();
    const add = (arr, fallback) => objects(arr).forEach((e, i) => {
      if (!str(e.id)) return;
      const season = Number(e.season) || fallback;
      const epInfo = (e.info && typeof e.info === 'object' && !Array.isArray(e.info)) ? e.info : {};
      if (!bySeason.has(season)) bySeason.set(season, []);
      bySeason.get(season).push({
        id: str(e.id), num: Number(e.episode_num) || i + 1, title: str(e.title),
        ext: str(e.container_extension) || 'mp4', plot: str(epInfo.plot), duration: str(epInfo.duration),
      });
    });
    const eps = root && root.episodes;
    if (Array.isArray(eps)) eps.forEach((arr, i) => add(arr, i + 1));
    else if (eps && typeof eps === 'object') Object.keys(eps).forEach((k) => add(eps[k], Number(k) || 1));
    const seasons = [...bySeason.keys()].sort((x, y) => x - y).map((n) => ({
      number: n, name: names[n] || `Saison ${n}`, episodes: bySeason.get(n).sort((x, y) => x.num - y.num),
    }));
    const backdrop = Array.isArray(info.backdrop_path) ? info.backdrop_path[0] : info.backdrop_path;
    return {
      plot: str(info.plot), genre: str(info.genre), year: (str(info.releaseDate) || str(info.releasedate)).slice(0, 4),
      rating: str(info.rating), cover: str(info.cover) || null, backdrop: str(backdrop) || null, seasons,
    };
  },
};

// ---- M3U -------------------------------------------------------------------------------------

function hashId(s) {
  let h = 0;
  for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) | 0;
  return `m${(h >>> 0).toString(36)}`;
}

const M3u = {
  parse(text) {
    const streams = [];
    const groups = [];
    const seen = new Set();
    let e = null;
    for (const raw of text.split(/\r?\n/)) {
      const line = raw.trim();
      if (!line) continue;
      if (/^#EXTINF/i.test(line)) {
        const attrs = {};
        for (const m of line.matchAll(/([\w-]+)="([^"]*)"/g)) attrs[m[1].toLowerCase()] = m[2];
        let name = '';
        let quoted = false;
        for (let i = 0; i < line.length; i++) {
          if (line[i] === '"') quoted = !quoted;
          else if (line[i] === ',' && !quoted) {
            name = line.slice(i + 1).trim();
            break;
          }
        }
        e = {
          name,
          logo: attrs['tvg-logo'] || null,
          group: (attrs['group-title'] || '').split(';')[0].trim() || null,
          ua: attrs['user-agent'] || attrs['http-user-agent'] || null,
          referer: attrs['http-referrer'] || attrs.referrer || null,
          backups: (attrs['backup-urls'] || '').split('|').map((u) => u.trim()).filter((u) => /^https?:/i.test(u)),
        };
      } else if (/^#EXTGRP:/i.test(line)) {
        if (e) e.group = line.slice(8).trim() || e.group;
      } else if (/^#EXTVLCOPT:/i.test(line)) {
        if (!e) continue;
        const opt = line.slice(11);
        const key = opt.split('=')[0].trim().toLowerCase();
        const value = opt.slice(opt.indexOf('=') + 1).trim();
        if (key === 'http-user-agent') e.ua = value;
        if (key === 'http-referrer' || key === 'http-referer') e.referer = value;
      } else if (!line.startsWith('#')) {
        if (/^https?:/i.test(line)) {
          const id = hashId(line);
          if (!seen.has(id)) {
            seen.add(id);
            const g = (e && e.group) || 'Sans catégorie';
            if (!groups.includes(g)) groups.push(g);
            streams.push({
              id, num: streams.length + 1, name: (e && e.name) || `Chaîne ${streams.length + 1}`,
              icon: e && e.logo, cat: g, url: line, ua: e && e.ua, referer: e && e.referer,
              backups: (e && e.backups) || [],
            });
          }
        }
        e = null;
      }
    }
    return { categories: groups.map((g) => ({ id: g, name: g })), streams };
  },
  async load(url) {
    const r = this.parse(await getText(url));
    if (!r.streams.length) throw new Error('Aucune chaîne trouvée dans cette liste M3U.');
    return r;
  },
};

// ---- Cache of the active list ----------------------------------------------------------------

const Repo = {
  cache: {},
  clear() {
    this.cache = {};
  },
  async once(key, fn) {
    if (!this.cache[key]) this.cache[key] = fn().catch((e) => {
      delete this.cache[key];
      throw e;
    });
    return this.cache[key];
  },
  get list() {
    return Prefs.active;
  },
  account() {
    const l = this.list;
    if (!l) throw new Error('Aucune liste configurée');
    if (l.type !== 'xtream') throw new Error('Cette liste ne propose pas ce contenu.');
    return { server: l.server, user: l.user, pass: l.pass };
  },
  get isXtream() {
    return !!this.list && this.list.type === 'xtream';
  },
  get isFree() {
    return !!this.list && this.list.type === 'free';
  },
  get liveOnly() {
    return !!this.list && this.list.type === 'm3u';
  },
  m3u() {
    return this.once('m3u', () => M3u.load(this.isFree ? `${this.list.url}/live.m3u` : this.list.url));
  },
  freeFilms() {
    return this.once('films', async () => {
      try {
        const arr = await getJson(`${this.list.url}/movies.json`);
        return (Array.isArray(arr) ? arr : []).filter((f) => f && f.url);
      } catch (e) {
        return [];
      }
    });
  },
  async userInfo() {
    if (!this.isXtream) {
      await this.m3u();
      return null;
    }
    return this.once('user', () => Xtream.login(this.account()));
  },
  async liveCategories() {
    if (!this.isXtream) return (await this.m3u()).categories;
    return this.once('liveCats', () => Xtream.categories(this.account(), 'get_live_categories'));
  },
  async liveStreams() {
    if (!this.isXtream) return (await this.m3u()).streams;
    return this.once('live', () => Xtream.liveStreams(this.account()));
  },
  async movieCategories() {
    if (this.isFree) return [...new Set((await this.freeFilms()).map((f) => f.category))].map((c) => ({ id: c, name: c }));
    if (!this.isXtream) return [];
    return this.once('movieCats', () => Xtream.categories(this.account(), 'get_vod_categories'));
  },
  async movies() {
    if (this.isFree) {
      const films = await this.freeFilms();
      return films.map((f, i) => ({
        id: f.id, name: f.title, image: f.poster || null, cat: f.category, rating: null,
        added: films.length - i, ext: 'mp4',
      }));
    }
    if (!this.isXtream) return [];
    return this.once('movies', () => Xtream.movies(this.account()));
  },
  async seriesCategories() {
    if (!this.isXtream) return [];
    return this.once('seriesCats', () => Xtream.categories(this.account(), 'get_series_categories'));
  },
  async series() {
    if (!this.isXtream) return [];
    return this.once('series', () => Xtream.series(this.account()));
  },
  async shortEpg(id) {
    if (!this.isXtream) return [];
    try {
      const now = Date.now() / 1000;
      return (await Xtream.shortEpg(this.account(), id)).filter((e) => !e.end || e.end > now);
    } catch (e) {
      return [];
    }
  },
  async movieInfo(id) {
    if (this.isFree) {
      const f = (await this.freeFilms()).find((x) => x.id === id);
      if (!f) throw new Error('Film introuvable');
      return {
        plot: f.plot || '', genre: 'Libre de droits', year: f.year || '', duration: f.duration || '', rating: '',
        cast: '', director: '', poster: f.poster, backdrop: null, ext: 'mp4', url: f.url,
      };
    }
    return Xtream.movieInfo(this.account(), id);
  },
  seriesInfo(id) {
    return Xtream.seriesInfo(this.account(), id);
  },
  async movieUrl(id, ext) {
    if (this.isFree) {
      const f = (await this.freeFilms()).find((x) => x.id === id);
      if (!f) throw new Error('Film introuvable');
      return f.url;
    }
    return Xtream.movieUrl(this.account(), id, ext);
  },
};

// ---- Ways to open a stream -------------------------------------------------------------------

const AGENTS = [API_UA, 'VLC/3.0.21 LibVLC/3.0.21', 'okhttp/4.12.0'];

function agents(first) {
  const preferred = first || Prefs.userAgent;
  return preferred ? [preferred, ...AGENTS.filter((a) => a !== preferred)] : AGENTS.slice();
}

const Streams = {
  live(s) {
    if (s.url) {
      return [s.url, ...(s.backups || [])].flatMap((u) => agents(s.ua).map((ua) => ({ url: u, ua, referer: s.referer })));
    }
    const a = Repo.account();
    const fmt = Prefs.liveFormat;
    const other = fmt === 'ts' ? 'm3u8' : 'ts';
    const urls = [Xtream.liveUrl(a, s.id, fmt), Xtream.liveUrl(a, s.id, other), `${a.server}/${a.user}/${a.pass}/${s.id}`];
    return agents().flatMap((ua) => urls.map((u) => ({ url: u, ua })));
  },
  vod(url) {
    return agents().map((ua) => ({ url, ua }));
  },
};

// ---- Updates ---------------------------------------------------------------------------------

const Updater = {
  latest: null,
  checked: false,
  pageUrl: 'https://github.com/hazz25/SimpleTV/releases/tag/desktop',
  downloadUrl: 'https://github.com/hazz25/SimpleTV/releases/download/desktop/SimpleTV.exe',
  async check() {
    const rel = await getJson('https://api.github.com/repos/hazz25/SimpleTV/releases/tags/desktop');
    const m = /Version\s+(\d+\.\d+\.\d+)/i.exec((rel && rel.body) || '');
    this.latest = m ? m[1] : null;
    this.checked = true;
    return this.latest;
  },
  newer(current) {
    if (!this.latest) return false;
    const a = this.latest.split('.').map(Number);
    const b = current.split('.').map(Number);
    for (let i = 0; i < 3; i++) {
      if ((a[i] || 0) !== (b[i] || 0)) return (a[i] || 0) > (b[i] || 0);
    }
    return false;
  },
};

// Node test hook (ignored in the app).
if (typeof module !== 'undefined') module.exports = { M3u, Xtream, hashId };
