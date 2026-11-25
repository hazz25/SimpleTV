// Simple TV for Windows: screens and navigation.
'use strict';

const app = document.getElementById('app');

// ---- Small helpers ---------------------------------------------------------------------------

/** Builds an element. Text is always set as text, never as HTML: channel names come from servers. */
function h(tag, props, ...kids) {
  const el = document.createElement(tag);
  for (const [k, v] of Object.entries(props || {})) {
    if (v === null || v === undefined || v === false) continue;
    if (k === 'class') el.className = v;
    else if (k === 'text') el.textContent = v;
    else if (k.startsWith('on') && typeof v === 'function') el.addEventListener(k.slice(2).toLowerCase(), v);
    else el.setAttribute(k, v === true ? '' : v);
  }
  for (const kid of kids.flat()) {
    if (kid === null || kid === undefined || kid === false) continue;
    el.append(kid instanceof Node ? kid : document.createTextNode(String(kid)));
  }
  return el;
}

function img(src, cls) {
  const i = h('img', { class: cls, alt: '', loading: 'lazy', draggable: 'false' });
  i.addEventListener('error', () => i.classList.add('broken'));
  if (src && /^https?:\/\//i.test(src)) i.src = src;
  else i.classList.add('broken');
  return i;
}

let toastTimer;
function toast(msg) {
  const t = document.getElementById('toast');
  t.textContent = msg;
  t.classList.remove('hidden');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => t.classList.add('hidden'), 3500);
}

const pad2 = (n) => String(n).padStart(2, '0');
const hhmm = (sec) => {
  const d = new Date(sec * 1000);
  return `${pad2(d.getHours())}:${pad2(d.getMinutes())}`;
};
const dateFr = (sec) => {
  const d = new Date(sec * 1000);
  return `${pad2(d.getDate())}/${pad2(d.getMonth() + 1)}/${d.getFullYear()}`;
};
function duration(ms) {
  const t = Math.floor(ms / 1000);
  const hh = Math.floor(t / 3600);
  const mm = Math.floor((t % 3600) / 60);
  const ss = t % 60;
  return hh ? `${hh}:${pad2(mm)}:${pad2(ss)}` : `${mm}:${pad2(ss)}`;
}
const searchKey = (s) => s.toLowerCase().normalize('NFD').replace(/\p{Mn}+/gu, '');
const spinner = () => h('div', { class: 'spinner' });

// ---- Navigation ------------------------------------------------------------------------------

const stack = [];

function render() {
  const view = stack[stack.length - 1];
  app.replaceChildren(view.render());
}

function open(view) {
  const cur = stack[stack.length - 1];
  if (cur && cur.leave) cur.leave();
  stack.push(view);
  render();
}

function back() {
  if (stack.length <= 1) return;
  const cur = stack.pop();
  if (cur.leave) cur.leave();
  render();
}

function reset(view) {
  while (stack.length) {
    const v = stack.pop();
    if (v.leave) v.leave();
  }
  stack.push(view);
  render();
}

/** Opens a main screen: only the home screen stays underneath. */
function goMain(key) {
  const factory = { live: LiveView, movies: () => VodView('movies'), series: () => VodView('series'), search: SearchView }[key];
  while (stack.length > 1) {
    const v = stack.pop();
    if (v.leave) v.leave();
  }
  if (key === 'home') render();
  else open(factory());
}

document.addEventListener('keydown', (e) => {
  const view = stack[stack.length - 1];
  if (view && view.onKey && view.onKey(e)) {
    e.preventDefault();
    return;
  }
  if (e.key === 'Escape' && !(e.target instanceof HTMLInputElement && e.target.value)) {
    back();
  } else if (e.key === 'F11') {
    window.stv.toggleFullscreen();
    e.preventDefault();
  }
});
document.addEventListener('mouseup', (e) => {
  if (e.button === 3) back(); // mouse "back" button
});

function clock() {
  const el = h('div', { class: 'clock' });
  const tick = () => {
    const d = new Date();
    el.textContent = `${pad2(d.getHours())}:${pad2(d.getMinutes())}`;
  };
  tick();
  const t = setInterval(() => (el.isConnected ? tick() : clearInterval(t)), 15000);
  return el;
}

function topBar(current) {
  const item = (key, label) => h('button', {
    class: `nav${key === current ? ' current' : ''}`,
    text: label,
    onclick: () => key !== current && goMain(key),
  });
  return h('div', { class: 'topbar' },
    item('home', 'Accueil'), item('live', 'TV en direct'), item('movies', 'Films'), item('series', 'Séries'),
    item('search', '🔍  Rechercher'),
    h('div', { class: 'spacer' }),
    h('button', { class: 'nav', text: '⚙', title: 'Paramètres', onclick: () => open(SettingsView()) }),
    h('button', { class: 'nav', text: '⛶', title: 'Plein écran (F11)', onclick: () => window.stv.toggleFullscreen() }),
    clock());
}

function errorText(e) {
  return (e && e.message) || 'Erreur inconnue';
}

// ---- Virtual list / grid (lists can hold tens of thousands of entries) -----------------------

class VirtualGrid {
  constructor({ rowHeight, minWidth = 0, renderItem }) {
    this.rowHeight = rowHeight;
    this.minWidth = minWidth;
    this.renderItem = renderItem;
    this.items = [];
    this.el = h('div', { class: 'vscroll' });
    this.spacer = h('div');
    this.win = h('div', { class: 'vwindow' });
    this.el.append(this.spacer, this.win);
    this.el.addEventListener('scroll', () => this.update());
    new ResizeObserver(() => this.update(true)).observe(this.el);
  }

  setItems(items, keepScroll = false) {
    this.items = items;
    if (!keepScroll) this.el.scrollTop = 0;
    this.update(true);
  }

  update(force) {
    const width = this.el.clientWidth || 1;
    const cols = this.minWidth ? Math.max(1, Math.floor(width / this.minWidth)) : 1;
    const rows = Math.ceil(this.items.length / cols);
    this.spacer.style.height = `${rows * this.rowHeight}px`;
    const first = Math.max(0, Math.floor(this.el.scrollTop / this.rowHeight) - 2);
    const last = Math.min(rows, first + Math.ceil((this.el.clientHeight || 600) / this.rowHeight) + 5);
    const key = `${first}:${last}:${cols}:${this.items.length}`;
    if (!force && key === this.key) return;
    this.key = key;
    this.win.style.top = `${first * this.rowHeight}px`;
    this.win.style.gridTemplateColumns = `repeat(${cols}, minmax(0, 1fr))`;
    const nodes = [];
    for (let i = first * cols; i < Math.min(this.items.length, last * cols); i++) nodes.push(this.renderItem(this.items[i], i));
    this.win.replaceChildren(...nodes);
  }

  refresh() {
    this.update(true);
  }

  scrollTo(index) {
    const cols = this.minWidth ? Math.max(1, Math.floor(this.el.clientWidth / this.minWidth)) : 1;
    this.el.scrollTop = Math.max(0, Math.floor(index / cols) * this.rowHeight - this.el.clientHeight / 3);
    this.update(true);
  }
}

function row({ title, left, right, icon, sub, star, active, onclick, oncontextmenu, onfav, showIcon }) {
  const r = h('div', { class: `row${active ? ' active' : ''}`, onclick, oncontextmenu, title: title },
    left !== undefined && left !== null ? h('div', { class: 'left', text: left }) : null,
    showIcon ? img(icon, 'logo') : null,
    h('div', { class: 'body' }, h('div', { class: 'title', text: title }), sub ? h('div', { class: 'sub', text: sub }) : null),
    star ? h('span', { class: 'star', text: '★' }) : null,
    onfav ? h('button', {
      class: 'fav', text: star ? '★' : '☆', title: star ? 'Retirer des favoris' : 'Ajouter aux favoris',
      onclick: (e) => { e.stopPropagation(); onfav(); },
    }) : null,
    right !== undefined && right !== null ? h('div', { class: 'right', text: right }) : null);
  return r;
}

// ---- Add a list ------------------------------------------------------------------------------

function LoginView() {
  const hasFree = Prefs.playlists().some((p) => p.type === 'free');
  let mode = hasFree ? 'xtream' : 'free';
  const name = h('input', { class: 'field', placeholder: 'Nom de la liste (facultatif)' });
  const url = h('input', { class: 'field', placeholder: 'Adresse de la liste (http://…/liste.m3u)' });
  const server = h('input', { class: 'field', placeholder: 'Adresse du serveur (ex : http://exemple.com:8080)' });
  const user = h('input', { class: 'field', placeholder: "Nom d'utilisateur" });
  const pass = h('input', { class: 'field', placeholder: 'Mot de passe', type: 'password' });
  const help = h('div', { class: 'help' });
  const err = h('div', { class: 'error-text' });
  const go = h('button', { class: 'btn', onclick: submit });
  const tabs = {
    free: h('button', { class: 'btn', text: 'Gratuit', onclick: () => setMode('free') }),
    xtream: h('button', { class: 'btn', text: 'Code Xtream', onclick: () => setMode('xtream') }),
    m3u: h('button', { class: 'btn', text: 'Lien M3U', onclick: () => setMode('m3u') }),
  };
  const HELP = {
    free: 'Sans abonnement et sans réglage : des centaines de chaînes françaises et arabes diffusées gratuitement, et des films libres de droits. La liste est mise à jour automatiquement chaque nuit.',
    xtream: 'Saisissez les informations Xtream fournies par votre fournisseur. Vous pouvez aussi coller votre lien complet (get.php?username=…) dans le champ du serveur.',
    m3u: "Collez l'adresse d'une liste M3U ou M3U8. Elle ne contient que des chaînes en direct.",
  };
  server.addEventListener('change', fillFromLink);
  [pass, url].forEach((f) => f.addEventListener('keydown', (e) => e.key === 'Enter' && submit()));

  function setMode(m) {
    mode = m;
    Object.entries(tabs).forEach(([k, b]) => b.classList.toggle('selected', k === m));
    help.textContent = HELP[m];
    name.classList.toggle('hidden', m === 'free');
    url.classList.toggle('hidden', m !== 'm3u');
    [server, user, pass].forEach((f) => f.classList.toggle('hidden', m !== 'xtream'));
    go.textContent = { free: 'Commencer', xtream: 'Se connecter', m3u: 'Ajouter la liste' }[m];
    err.textContent = '';
  }

  function fillFromLink() {
    const p = Xtream.parseLink(server.value);
    if (!p) return false;
    server.value = p.server;
    user.value = p.user;
    pass.value = p.pass;
    return true;
  }

  async function save(check) {
    err.textContent = '';
    go.disabled = true;
    go.textContent = 'Vérification…';
    try {
      const playlist = await check();
      Prefs.addPlaylist(playlist);
      Repo.clear();
      reset(HomeView());
    } catch (e) {
      err.textContent = errorText(e);
      setMode(mode);
    } finally {
      go.disabled = false;
    }
  }

  function xtream(account, label) {
    save(async () => {
      await Xtream.login(account);
      return { id: newId(), type: 'xtream', name: label || hostName(account.server), ...account };
    });
  }

  function submit() {
    const label = name.value.trim();
    if (mode === 'free') {
      save(async () => {
        const list = Prefs.playlists().find((p) => p.type === 'free') || { id: newId(), type: 'free', name: 'Chaînes gratuites', url: FREE_BASE };
        await M3u.load(`${list.url}/live.m3u`);
        return list;
      });
    } else if (mode === 'm3u') {
      const u = url.value.trim();
      const asXtream = Xtream.parseLink(u);
      if (asXtream) return xtream(asXtream, label);
      if (!/^https?:\/\//i.test(u)) {
        err.textContent = "L'adresse doit commencer par http:// ou https://";
        return;
      }
      save(async () => {
        await M3u.load(u);
        return { id: newId(), type: 'm3u', name: label || hostName(u), url: u };
      });
    } else {
      fillFromLink();
      const s = server.value.trim();
      const us = user.value.trim();
      const pw = pass.value.trim();
      if (!s || !us || !pw) {
        err.textContent = 'Remplissez les trois champs.';
        return;
      }
      xtream({ server: Xtream.normalizeServer(s), user: us, pass: pw }, label);
    }
  }

  return {
    render() {
      setMode(mode);
      return h('div', { class: 'center' }, h('div', { class: 'panel' },
        h('h1', { text: 'Simple TV' }),
        h('div', { text: 'Ajouter une liste' }),
        h('div', { class: 'row-buttons', style: null }, tabs.free, tabs.xtream, tabs.m3u),
        help, name, url, server, user, pass,
        h('div', { class: 'row-buttons' }, go,
          stack.length > 1 ? h('button', { class: 'btn', text: 'Annuler', onclick: back }) : null),
        err));
    },
  };
}

// ---- Home ------------------------------------------------------------------------------------

function HomeView() {
  return {
    render() {
      const list = Prefs.active;
      const expiry = h('div', { class: 'expiry', text: list ? `${list.name}\nChargement…` : '' });
      const status = h('div', { class: 'status' });
      const update = h('button', { class: 'btn gold hidden' });
      const tile = (icon, label, onclick, cls = '') => h('div', { class: `tile ${cls}`, tabindex: '0', onclick },
        h('div', { class: 'icon', text: icon }), h('div', { text: label }));

      (async () => {
        try {
          const info = await Repo.userInfo();
          let details;
          if (!info) details = list.type === 'free' ? 'Sans abonnement' : 'Liste M3U';
          else {
            details = info.expDate ? `Expire le ${dateFr(info.expDate)}` : "Sans date d'expiration";
            if (info.status && info.status.toLowerCase() !== 'active') details += `  •  ${info.status}`;
          }
          expiry.textContent = `${list.name}\n${details}`;
          Repo.liveStreams().catch(() => {});
        } catch (e) {
          expiry.textContent = list.name;
          status.textContent = `Connexion impossible : ${errorText(e)}`;
        }
      })();
      (async () => {
        try {
          if (!Updater.checked) await Updater.check();
          const current = await window.stv.version();
          if (Updater.newer(current)) {
            update.textContent = `⬆  Nouvelle version ${Updater.latest} disponible  •  Télécharger et installer`;
            update.onclick = () => window.stv.openExternal(Updater.downloadUrl);
            update.classList.remove('hidden');
          }
        } catch (e) { /* offline: try next time */ }
      })();

      return h('div', { class: 'home' },
        clock(), expiry,
        h('div', { class: 'logo-text', text: 'Simple TV' }),
        h('div', { class: 'tagline', text: 'Votre TV, simplement' }),
        h('div', { class: 'tiles' },
          tile('📺', 'TV en direct', () => goMain('live'), 'big'),
          h('div', { class: 'stack' }, tile('🎬', 'Films', () => goMain('movies')), tile('⭐', 'Favoris', () => open(LiveView(FAV)))),
          h('div', { class: 'stack' }, tile('🎞', 'Séries', () => goMain('series')), tile('🔍', 'Rechercher', () => goMain('search'))),
          h('div', { class: 'stack' },
            tile('⚙', 'Paramètres', () => open(SettingsView()), 'side'),
            tile('📋', 'Mes listes', () => open(ListsView()), 'side'),
            tile('✖', 'Quitter', () => window.stv.quit(), 'side'))),
        update, status);
    },
  };
}

// ---- Live TV ---------------------------------------------------------------------------------

const RECENT = '__recent';
const ALL = '__all';
const FAV = '__fav';
const NEW = '__new';

function LiveView(startCat) {
  let all = [];
  let byId = new Map();
  let categories = [];
  let cats = [];
  let channels = [];
  let current = startCat || Prefs.lastLiveCategory || ALL;
  let previewing = null;
  let catBox;
  let header;
  let list;
  let preview;
  let video;
  let msg;
  let chName;
  let epgNow;
  let epgNext;

  const visible = () => {
    const hidden = Prefs.hidden('live');
    return hidden.size ? all.filter((s) => !hidden.has(s.cat)) : all;
  };

  function buildCats() {
    const hidden = Prefs.hidden('live');
    const counts = new Map();
    all.forEach((s) => counts.set(s.cat, (counts.get(s.cat) || 0) + 1));
    cats = [
      { id: RECENT, name: 'Vus récemment', count: Prefs.recents('live').filter((id) => byId.has(id)).length },
      { id: ALL, name: 'Toutes les chaînes', count: visible().length },
      { id: FAV, name: '★ Favoris', count: Prefs.favorites('live').filter((id) => byId.has(id)).length },
      ...categories.filter((c) => counts.get(c.id) && !hidden.has(c.id)).map((c) => ({ ...c, count: counts.get(c.id) })),
    ];
    if (!cats.some((c) => c.id === current)) current = ALL;
    catBox.replaceChildren(...cats.map((c) => row({
      title: c.name, right: String(c.count), active: c.id === current,
      onclick: () => showCategory(c.id),
      oncontextmenu: c.id.startsWith('__') ? null : () => {
        Prefs.hideCategory('live', c.id);
        toast(`« ${c.name} » masquée. Paramètres → Catégories masquées pour la réafficher.`);
        if (current === c.id) current = ALL;
        buildCats();
        showCategory(current);
      },
    })));
  }

  function channelsFor(id) {
    if (id === RECENT) return Prefs.recents('live').map((x) => byId.get(x)).filter(Boolean);
    if (id === FAV) return Prefs.favorites('live').map((x) => byId.get(x)).filter(Boolean);
    if (id === ALL) return visible();
    return all.filter((s) => s.cat === id);
  }

  function showCategory(id) {
    current = id;
    Prefs.lastLiveCategory = id;
    channels = channelsFor(id);
    [...catBox.children].forEach((el, i) => el.classList.toggle('active', cats[i] && cats[i].id === id));
    const name = (cats.find((c) => c.id === id) || {}).name || '';
    header.textContent = channels.length ? `${name} (${channels.length})` : `${name} — aucune chaîne`;
    list.setItems(channels);
  }

  function toggleFav(s) {
    const now = Prefs.toggleFavorite('live', s.id);
    toast(now ? 'Ajoutée aux favoris' : 'Retirée des favoris');
    buildCats();
    if (current === FAV) showCategory(FAV);
    else list.refresh();
  }

  function startPreview(s) {
    previewing = s;
    Prefs.addRecent('live', s.id);
    chName.textContent = s.name;
    epgNow.textContent = '';
    epgNext.textContent = '';
    msg.textContent = 'Chargement…';
    msg.classList.remove('hidden');
    list.refresh();
    try {
      preview.play(Streams.live(s), { live: true });
    } catch (e) {
      msg.textContent = errorText(e);
    }
    Repo.shortEpg(s.id).then((epg) => {
      if (previewing !== s) return;
      const [now, next] = epg;
      epgNow.textContent = now ? `En ce moment : ${hhmm(now.start)} – ${hhmm(now.end)}  ${now.title}` : (Repo.isXtream ? 'Pas de programme disponible' : '');
      epgNext.textContent = next ? `Ensuite : ${hhmm(next.start)}  ${next.title}` : '';
    });
  }

  function fullScreen(index) {
    preview.stop();
    open(PlayerView(channels.map((s) => ({ live: s })), index));
  }

  return {
    leave() {
      if (preview) preview.stop();
    },
    render() {
      catBox = h('div', { class: 'scroll' });
      header = h('div', { class: 'header', text: 'Chargement…' });
      video = h('video', { muted: false });
      msg = h('div', { class: 'msg', text: 'Choisissez une chaîne' });
      chName = h('div', { class: 'chname' });
      epgNow = h('div', { class: 'epg-now' });
      epgNext = h('div', { class: 'epg-next' });
      preview = new RetryingPlayer(video, {
        onReady: () => msg.classList.add('hidden'),
        onFailed: (m) => {
          msg.textContent = m;
          msg.classList.remove('hidden');
        },
      });
      const favs = () => new Set(Prefs.favorites('live'));
      let favSet = favs();
      list = new VirtualGrid({
        rowHeight: 46,
        renderItem: (s, i) => {
          if (i === 0) favSet = favs();
          return row({
            title: s.name, left: String(s.num > 0 ? s.num : i + 1), icon: s.icon, showIcon: true,
            star: favSet.has(s.id), active: previewing && previewing.id === s.id,
            onclick: () => (previewing && previewing.id === s.id ? fullScreen(i) : startPreview(s)),
            oncontextmenu: () => toggleFav(s),
            onfav: () => toggleFav(s),
          });
        },
      });
      const screen = h('div', { class: 'screen' }, topBar('live'),
        h('div', { class: 'columns' },
          h('div', { class: 'col', style: null }, h('div', { class: 'header', text: 'Catégories' }), catBox),
          h('div', { class: 'col' }, header, list.el),
          h('div', { class: 'col' },
            h('div', { class: 'preview', ondblclick: () => previewing && fullScreen(channels.indexOf(previewing)) }, video, msg),
            chName, epgNow, epgNext,
            h('div', { class: 'row-buttons' }, h('button', {
              class: 'btn', text: '⛶  Plein écran',
              onclick: () => previewing && fullScreen(Math.max(0, channels.indexOf(previewing))),
            })),
            h('div', { class: 'spacer', style: null }),
            h('div', { class: 'hint', text: 'Clic : aperçu  •  2ᵉ clic ou double-clic : plein écran  •  Clic droit : favori (chaîne) ou masquer (catégorie)' }))));
      const [c1, c2, c3] = screen.querySelectorAll('.columns > .col');
      c1.style.flex = '3';
      c2.style.flex = '4';
      c3.style.flex = '5';

      (async () => {
        try {
          categories = await Repo.liveCategories();
          all = await Repo.liveStreams();
          byId = new Map(all.map((s) => [s.id, s]));
          buildCats();
          showCategory(current);
          const returned = Repo.lastLivePlayed;
          if (returned) {
            Repo.lastLivePlayed = null;
            const s = byId.get(returned.id) || returned;
            startPreview(s);
            const i = channels.findIndex((c) => c.id === s.id);
            if (i >= 0) list.scrollTo(i);
          }
        } catch (e) {
          header.textContent = errorText(e);
        }
      })();
      return screen;
    },
  };
}

// ---- Films and series ------------------------------------------------------------------------

function VodView(mode) {
  const series = mode === 'series';
  const type = series ? 'series' : 'vod';
  let all = [];
  let byId = new Map();
  let categories = [];
  let cats = [];
  let shown = [];
  let current = null;
  let catBox;
  let header;
  let grid;

  const visible = () => {
    const hidden = Prefs.hidden(type);
    return hidden.size ? all.filter((m) => !hidden.has(m.cat)) : all;
  };

  function buildCats() {
    const hidden = Prefs.hidden(type);
    const counts = new Map();
    all.forEach((m) => counts.set(m.cat, (counts.get(m.cat) || 0) + 1));
    cats = [
      { id: RECENT, name: 'Vus récemment', count: Prefs.recents(type).filter((id) => byId.has(id)).length },
      { id: NEW, name: 'Nouveautés', count: Math.min(100, visible().length) },
      { id: ALL, name: series ? 'Toutes les séries' : 'Tous les films', count: visible().length },
      { id: FAV, name: '★ Favoris', count: Prefs.favorites(type).filter((id) => byId.has(id)).length },
      ...categories.filter((c) => counts.get(c.id) && !hidden.has(c.id)).map((c) => ({ ...c, count: counts.get(c.id) })),
    ];
    if (!cats.some((c) => c.id === current)) current = cats[0].count ? RECENT : ALL;
    catBox.replaceChildren(...cats.map((c) => row({
      title: c.name, right: String(c.count), active: c.id === current,
      onclick: () => showCategory(c.id),
      oncontextmenu: c.id.startsWith('__') ? null : () => {
        Prefs.hideCategory(type, c.id);
        toast(`« ${c.name} » masquée. Paramètres → Catégories masquées pour la réafficher.`);
        if (current === c.id) current = ALL;
        buildCats();
        showCategory(current);
      },
    })));
  }

  function itemsFor(id) {
    if (id === RECENT) return Prefs.recents(type).map((x) => byId.get(x)).filter(Boolean);
    if (id === FAV) return Prefs.favorites(type).map((x) => byId.get(x)).filter(Boolean);
    if (id === NEW) return visible().slice().sort((a, b) => b.added - a.added).slice(0, 100);
    if (id === ALL) return visible();
    return all.filter((m) => m.cat === id);
  }

  function showCategory(id) {
    current = id;
    shown = itemsFor(id);
    [...catBox.children].forEach((el, i) => el.classList.toggle('active', cats[i] && cats[i].id === id));
    const name = (cats.find((c) => c.id === id) || {}).name || '';
    header.textContent = shown.length ? `${name} (${shown.length})` : `${name} — vide`;
    grid.setItems(shown);
  }

  function toggleFav(m) {
    const now = Prefs.toggleFavorite(type, m.id);
    toast(now ? 'Ajouté aux favoris' : 'Retiré des favoris');
    buildCats();
    if (current === FAV) showCategory(FAV);
    else grid.refresh();
  }

  return {
    render() {
      catBox = h('div', { class: 'scroll' });
      header = h('div', { class: 'header', text: series ? 'Chargement des séries…' : 'Chargement des films…' });
      let favSet = new Set(Prefs.favorites(type));
      grid = new VirtualGrid({
        rowHeight: 262,
        minWidth: 160,
        renderItem: (m, i) => {
          if (i === 0) favSet = new Set(Prefs.favorites(type));
          return h('div', {
            class: 'poster', title: m.name,
            onclick: () => open(series ? SeriesView(m) : MovieView(m)),
            oncontextmenu: () => toggleFav(m),
          },
          h('div', { class: 'frame' }, h('div', { class: 'alt', text: m.name }), img(m.image),
            m.rating ? h('div', { class: 'badge', text: `★ ${m.rating}` }) : null,
            favSet.has(m.id) ? h('div', { class: 'pstar', text: '★' }) : null),
          h('div', { class: 'ptitle', text: m.name }));
        },
      });
      const screen = h('div', { class: 'screen' }, topBar(mode),
        h('div', { class: 'columns' },
          h('div', { class: 'col' }, h('div', { class: 'header', text: 'Catégories' }), catBox),
          h('div', { class: 'col' }, header, grid.el,
            h('div', { class: 'hint', text: 'Clic droit sur une affiche : favori  •  Clic droit sur une catégorie : la masquer' }))));
      const [c1, c2] = screen.querySelectorAll('.columns > .col');
      c1.style.flex = '3';
      c2.style.flex = '9';

      (async () => {
        try {
          if (Repo.isFree && series) {
            header.textContent = "Il n'existe pas de séries gratuites et libres de droits en français ou en arabe. "
              + 'Retrouvez des séries en direct dans TV en direct → « Films & séries ».';
            return;
          }
          if (Repo.liveOnly) {
            header.textContent = 'Cette liste M3U ne contient que des chaînes en direct. Les films et séries sont disponibles avec un code Xtream (Paramètres → Mes listes).';
            return;
          }
          categories = series ? await Repo.seriesCategories() : await Repo.movieCategories();
          all = series ? await Repo.series() : await Repo.movies();
          byId = new Map(all.map((m) => [m.id, m]));
          buildCats();
          showCategory(current);
        } catch (e) {
          header.textContent = errorText(e);
        }
      })();
      return screen;
    },
  };
}

function MovieView(m) {
  const key = `vod_${m.id}`;
  let ext = m.ext || 'mp4';
  return {
    render() {
      const backdrop = img(null, 'backdrop');
      const cover = img(m.image, 'cover');
      const meta = h('div', { class: 'meta' });
      const plot = h('div', { class: 'plot', text: 'Chargement…' });
      const cast = h('div', { class: 'cast' });
      const playBtn = h('button', { class: 'btn', onclick: () => play(false) });
      const restartBtn = h('button', { class: 'btn', text: '⟲  Depuis le début', onclick: () => play(true) });
      const favBtn = h('button', {
        class: 'btn',
        onclick: () => {
          toast(Prefs.toggleFavorite('vod', m.id) ? 'Ajouté aux favoris' : 'Retiré des favoris');
          buttons();
        },
      });
      function buttons() {
        const pos = Prefs.position(key);
        playBtn.textContent = pos > 0 ? `▶  Reprendre à ${duration(pos)}` : '▶  Lire';
        restartBtn.classList.toggle('hidden', !(pos > 0));
        favBtn.textContent = Prefs.favorites('vod').includes(m.id) ? '★  Retirer des favoris' : '☆  Ajouter aux favoris';
      }
      async function play(fromStart) {
        if (fromStart) Prefs.savePosition(key, 0);
        try {
          const url = await Repo.movieUrl(m.id, ext);
          Prefs.addRecent('vod', m.id);
          open(PlayerView([{ url, title: m.name, key }], 0));
        } catch (e) {
          toast(errorText(e));
        }
      }
      buttons();
      Repo.movieInfo(m.id).then((info) => {
        if (info.ext) ext = info.ext;
        if (!m.image && info.poster) cover.src = info.poster;
        if (info.backdrop && /^https?:/i.test(info.backdrop)) {
          backdrop.classList.remove('broken');
          backdrop.src = info.backdrop;
        }
        meta.textContent = [info.year, info.genre, info.duration, info.rating && info.rating !== '0' ? `★ ${info.rating}` : '']
          .filter(Boolean).join('  •  ');
        plot.textContent = info.plot || 'Pas de résumé disponible.';
        cast.textContent = [info.director ? `Réalisation : ${info.director}` : '', info.cast ? `Avec : ${info.cast}` : '']
          .filter(Boolean).join('\n');
      }).catch(() => {
        plot.textContent = 'Informations indisponibles. Le film peut quand même être lu.';
      });
      return h('div', { class: 'detail' }, backdrop,
        h('div', { class: 'content' },
          h('div', { class: 'row-buttons' }, h('button', { class: 'btn', text: '←  Retour', onclick: back })),
          h('div', { class: 'top' }, cover,
            h('div', { class: 'col' }, h('h1', { text: m.name }), meta, plot, cast,
              h('div', { class: 'row-buttons', style: null }, playBtn, restartBtn, favBtn)))));
    },
    onResume() {},
  };
}

function SeriesView(m) {
  let seasons = [];
  let currentSeason = 0;
  return {
    render() {
      const backdrop = img(null, 'backdrop');
      const cover = img(m.image, 'cover small');
      const meta = h('div', { class: 'meta' });
      const plot = h('div', { class: 'plot', text: 'Chargement…' });
      const seasonBox = h('div', { class: 'scroll' });
      const episodeBox = h('div', { class: 'scroll' });
      const favBtn = h('button', {
        class: 'btn',
        onclick: () => {
          toast(Prefs.toggleFavorite('series', m.id) ? 'Ajoutée aux favoris' : 'Retirée des favoris');
          favLabel();
        },
      });
      const favLabel = () => {
        favBtn.textContent = Prefs.favorites('series').includes(m.id) ? '★  Retirer des favoris' : '☆  Ajouter aux favoris';
      };
      favLabel();
      const epKey = (e) => `ep_${e.id}`;

      function showSeason(i) {
        currentSeason = i;
        seasonBox.replaceChildren(...seasons.map((s, j) => row({
          title: s.name, right: String(s.episodes.length), active: j === i, onclick: () => showSeason(j),
        })));
        const eps = (seasons[i] && seasons[i].episodes) || [];
        episodeBox.replaceChildren(...eps.map((e, j) => {
          const pos = Prefs.position(epKey(e));
          return row({
            title: e.title || `Épisode ${e.num}`, left: `E${pad2(e.num)}`, sub: e.plot || null,
            right: pos < 0 ? '✓ Vu' : pos > 0 ? `▶ ${duration(pos)}` : e.duration,
            onclick: () => play(eps, j),
          });
        }));
      }

      function play(eps, index) {
        let a;
        try {
          a = Repo.account();
        } catch (e) {
          toast(errorText(e));
          return;
        }
        Prefs.addRecent('series', m.id);
        open(PlayerView(eps.map((e) => ({
          url: Xtream.episodeUrl(a, e.id, e.ext), title: `${m.name} — E${e.num} ${e.title}`.trim(), key: epKey(e),
        })), index));
      }

      Repo.seriesInfo(m.id).then((info) => {
        if (!m.image && info.cover) cover.src = info.cover;
        if (info.backdrop && /^https?:/i.test(info.backdrop)) {
          backdrop.classList.remove('broken');
          backdrop.src = info.backdrop;
        }
        meta.textContent = [info.year, info.genre, info.rating && info.rating !== '0' ? `★ ${info.rating}` : '',
          `${info.seasons.length} saison${info.seasons.length > 1 ? 's' : ''}`].filter(Boolean).join('  •  ');
        plot.textContent = info.plot || 'Pas de résumé disponible.';
        seasons = info.seasons;
        if (!seasons.length) plot.textContent = 'Aucun épisode disponible pour cette série.';
        else {
          const resume = seasons.findIndex((s) => s.episodes.some((e) => Prefs.position(epKey(e)) >= 0));
          showSeason(resume >= 0 ? resume : 0);
        }
      }).catch((e) => {
        plot.textContent = errorText(e);
      });

      const view = h('div', { class: 'detail' }, backdrop,
        h('div', { class: 'content' },
          h('div', { class: 'row-buttons' }, h('button', { class: 'btn', text: '←  Retour', onclick: back })),
          h('div', { class: 'top' }, cover,
            h('div', { class: 'col' }, h('h1', { text: m.name }), meta, plot, h('div', { class: 'row-buttons' }, favBtn))),
          h('div', { class: 'columns' }, h('div', { class: 'col' }, seasonBox), h('div', { class: 'col' }, episodeBox))));
      const [c1, c2] = view.querySelectorAll('.columns > .col');
      c1.style.flex = '2';
      c2.style.flex = '6';
      if (seasons.length) showSeason(currentSeason);
      return view;
    },
  };
}

// ---- Player ----------------------------------------------------------------------------------

/** Items: { live: channel } or { url, title, key }. */
function PlayerView(items, startIndex) {
  let index = startIndex;
  let player;
  let video;
  let idleTimer;
  let infoTimer;
  let saveTimer;
  let root;
  let titleEl;
  let info;
  let infoT;
  let infoE;
  let error;
  let errorMsg;
  let vlcBtn;
  const cur = () => items[index];
  const isLive = () => !!(cur() && cur().live);

  function savePosition() {
    const it = cur();
    if (!it || it.live || !video) return;
    const pos = video.currentTime * 1000;
    const dur = video.duration * 1000;
    if (dur > 0 && pos > dur * 0.95) Prefs.savePosition(it.key, -1);
    else if (pos > 30000) Prefs.savePosition(it.key, Math.floor(pos));
  }

  function showInfo(title, line) {
    infoT.textContent = title;
    infoE.textContent = line || '';
    info.classList.remove('hidden');
    clearTimeout(infoTimer);
    infoTimer = setTimeout(() => info.classList.add('hidden'), 5000);
  }

  function liveInfo(s) {
    showInfo(`${s.num > 0 ? `${s.num}  ` : ''}${s.name}`, '');
    Repo.shortEpg(s.id).then((epg) => {
      if (!cur() || cur().live !== s || !epg.length) return;
      const [now, next] = epg;
      infoE.textContent = `${hhmm(now.start)} – ${hhmm(now.end)}  ${now.title}`
        + (next ? `\nEnsuite : ${hhmm(next.start)}  ${next.title}` : '');
    });
  }

  function play(i) {
    savePosition();
    index = i;
    error.classList.add('hidden');
    const it = cur();
    if (it.live) {
      video.controls = false;
      titleEl.textContent = it.live.name;
      Repo.lastLivePlayed = it.live;
      Prefs.addRecent('live', it.live.id);
      try {
        player.play(Streams.live(it.live), { live: true });
      } catch (e) {
        fail(errorText(e));
      }
      liveInfo(it.live);
    } else {
      video.controls = true;
      titleEl.textContent = it.title;
      const start = Math.max(0, Prefs.position(it.key));
      player.play(Streams.vod(it.url), { live: false, start });
      showInfo(it.title, start > 0 ? `Reprise à ${duration(start)}` : '');
    }
  }

  function fail(message) {
    errorMsg.textContent = message + (isLive() ? '\n\n▲ ▼ pour changer de chaîne' : '');
    vlcBtn.classList.toggle('hidden', isLive());
    error.classList.remove('hidden');
  }

  function zap(delta) {
    if (items.length < 2) return;
    play((index + delta + items.length) % items.length);
  }

  function wake() {
    root.classList.remove('idle');
    clearTimeout(idleTimer);
    idleTimer = setTimeout(() => root.classList.add('idle'), 3000);
  }

  return {
    leave() {
      savePosition();
      clearTimeout(idleTimer);
      clearTimeout(infoTimer);
      clearInterval(saveTimer);
      if (player) player.stop();
      window.stv.setFullscreen(false);
    },
    onKey(e) {
      if (e.key === 'f' || e.key === 'F') {
        window.stv.toggleFullscreen();
        return true;
      }
      if (isLive()) {
        const down = Prefs.invertZap ? -1 : 1;
        if (e.key === 'ArrowUp' || e.key === 'PageUp') { zap(-down); return true; }
        if (e.key === 'ArrowDown' || e.key === 'PageDown') { zap(down); return true; }
        if (e.key === 'Enter' || e.key === 'i') { liveInfo(cur().live); return true; }
        return false;
      }
      if (e.key === ' ') {
        if (video.paused) video.play(); else video.pause();
        return true;
      }
      if (e.key === 'ArrowLeft') { video.currentTime = Math.max(0, video.currentTime - 10); wake(); return true; }
      if (e.key === 'ArrowRight') { video.currentTime += 30; wake(); return true; }
      return false;
    },
    render() {
      video = h('video', { autoplay: true });
      titleEl = h('div', { class: 'title' });
      infoT = h('div', { class: 't' });
      infoE = h('div', { class: 'e' });
      info = h('div', { class: 'info hidden' }, infoT, infoE);
      errorMsg = h('div');
      vlcBtn = h('button', {
        class: 'btn', text: 'Ouvrir dans VLC',
        onclick: async () => {
          const it = cur();
          if (!it || it.live) return;
          const ok = await window.stv.openVlc(it.url);
          toast(ok ? 'Lecture lancée dans VLC' : 'VLC n\'est pas installé : téléchargez-le sur videolan.org');
        },
      });
      error = h('div', { class: 'error hidden' }, errorMsg, h('div', { class: 'row-buttons' }, vlcBtn,
        h('button', { class: 'btn', text: 'Retour', onclick: back })));
      const bar = h('div', { class: 'bar' },
        h('button', { class: 'btn', text: '←  Retour', onclick: back }),
        titleEl,
        h('button', { class: 'btn live-only', text: '▲  Chaîne précédente', onclick: () => zap(Prefs.invertZap ? 1 : -1) }),
        h('button', { class: 'btn live-only', text: '▼  Chaîne suivante', onclick: () => zap(Prefs.invertZap ? -1 : 1) }),
        h('button', { class: 'btn', text: '⛶', title: 'Plein écran (F)', onclick: () => window.stv.toggleFullscreen() }));
      root = h('div', { class: 'player', onmousemove: wake }, video, bar, info, error);
      video.addEventListener('dblclick', () => window.stv.toggleFullscreen());
      player = new RetryingPlayer(video, {
        onReady: () => error.classList.add('hidden'),
        onFailed: fail,
        onEnded: () => {
          const it = cur();
          if (it && !it.live) {
            Prefs.savePosition(it.key, -1);
            if (index + 1 < items.length) play(index + 1);
            else back();
          }
        },
      });
      const liveButtons = () => bar.querySelectorAll('.live-only').forEach((b) => b.classList.toggle('hidden', !isLive()));
      saveTimer = setInterval(savePosition, 10000);
      setTimeout(() => {
        play(index);
        liveButtons();
        wake();
      });
      return root;
    },
  };
}

// ---- Search ----------------------------------------------------------------------------------

function SearchView() {
  let timer;
  let hits = [];
  return {
    render() {
      const input = h('input', { class: 'field', placeholder: "Nom d'une chaîne, d'un film ou d'une série…" });
      const empty = h('div', { class: 'header', text: 'Tapez au moins 2 lettres.' });
      const list = new VirtualGrid({
        rowHeight: 46,
        renderItem: (hit) => row({
          title: hit.item.name, left: hit.kind, icon: hit.item.icon || hit.item.image, showIcon: true,
          onclick: () => openHit(hit),
        }),
      });
      function openHit(hit) {
        if (hit.kind === 'TV') {
          const channels = hits.filter((x) => x.kind === 'TV').map((x) => x.item);
          open(PlayerView(channels.map((s) => ({ live: s })), Math.max(0, channels.indexOf(hit.item))));
        } else if (hit.kind === 'Film') open(MovieView(hit.item));
        else open(SeriesView(hit.item));
      }
      async function search() {
        const q = searchKey(input.value.trim());
        if (q.length < 2) {
          hits = [];
          list.setItems([]);
          empty.textContent = 'Tapez au moins 2 lettres.';
          return;
        }
        empty.textContent = 'Recherche…';
        try {
          const [live, films, shows] = await Promise.all([
            Repo.liveStreams(), Repo.movies().catch(() => []), Repo.series().catch(() => []),
          ]);
          const hl = Prefs.hidden('live');
          const hv = Prefs.hidden('vod');
          const hs = Prefs.hidden('series');
          const pick = (arr, hidden, kind) => {
            const out = [];
            for (const it of arr) {
              if (!hidden.has(it.cat) && searchKey(it.name).includes(q)) out.push({ kind, item: it });
              if (out.length >= 60) break;
            }
            return out;
          };
          hits = [...pick(live, hl, 'TV'), ...pick(films, hv, 'Film'), ...pick(shows, hs, 'Série')];
          list.setItems(hits);
          empty.textContent = hits.length ? `${hits.length} résultat(s)` : 'Aucun résultat.';
        } catch (e) {
          empty.textContent = errorText(e);
        }
      }
      input.addEventListener('input', () => {
        clearTimeout(timer);
        timer = setTimeout(search, 350);
      });
      setTimeout(() => input.focus());
      return h('div', { class: 'screen' }, topBar('search'), input, empty, list.el);
    },
  };
}

// ---- Settings and lists ----------------------------------------------------------------------

function SettingsView() {
  return {
    render() {
      const box = h('div', { class: 'scroll' });
      let info = null;
      let version = '';

      function accountLine() {
        const l = Prefs.active;
        if (!l) return 'Aucune liste';
        if (l.type === 'free') return `${l.name}  •  sans abonnement, mise à jour chaque nuit`;
        if (l.type === 'm3u') return `${l.name}  •  liste M3U\n${l.url}`;
        if (!info) return `${l.name}  •  ${l.user}\n${l.server}`;
        const exp = info.expDate ? `expire le ${dateFr(info.expDate)}` : "sans date d'expiration";
        const screens = info.maxConnections ? `  •  ${info.maxConnections} écran(s) max` : '';
        return `${l.name}  •  ${l.user}  •  ${exp}${screens}\n${l.server}`;
      }

      function updateLine() {
        const cur = `Version installée : ${version}`;
        if (Updater.newer(version)) return `${cur}\nNouvelle version ${Updater.latest} disponible : cliquez pour la télécharger`;
        if (Updater.checked) return `${cur}\nVous avez la dernière version. Cliquez pour vérifier à nouveau`;
        return `${cur}\nCliquez pour rechercher une mise à jour`;
      }

      function refresh() {
        const hidden = Prefs.hiddenCount();
        const items = [
          ['Liste utilisée', accountLine(), () => open(ListsView())],
          ['Format des chaînes en direct (Xtream)',
            Prefs.liveFormat === 'm3u8' ? 'HLS (recommandé sur PC). Changez si les chaînes ne se lancent pas.' : 'TS. Changez si les chaînes coupent ou ne se lancent pas.',
            () => { Prefs.liveFormat = Prefs.liveFormat === 'ts' ? 'm3u8' : 'ts'; refresh(); }],
          ['Changer de chaîne avec ▲ ▼', Prefs.invertZap ? 'Inversé : ▲ chaîne suivante' : 'Normal : ▼ chaîne suivante',
            () => { Prefs.invertZap = !Prefs.invertZap; refresh(); }],
          ['Catégories masquées', hidden ? `${hidden} masquée(s). Cliquez pour toutes les réafficher.` : 'Aucune. Clic droit sur une catégorie pour la masquer.',
            () => { if (hidden) { Prefs.clearHidden(); toast('Toutes les catégories sont réaffichées'); refresh(); } }],
          ["Effacer l'historique", 'Vide les listes « Vus récemment »', () => { Prefs.clearRecents(); toast('Historique effacé'); }],
          ['Recharger les listes', 'Récupère les nouvelles chaînes, films et séries', () => { Repo.clear(); toast('Les listes seront rechargées'); }],
          ['Mes listes', 'Changer de liste, en ajouter une (gratuite, Xtream ou M3U) ou en supprimer', () => open(ListsView())],
          ['Mises à jour', updateLine(), async () => {
            if (Updater.newer(version)) {
              window.stv.openExternal(Updater.downloadUrl);
              return;
            }
            try {
              await Updater.check();
            } catch (e) {
              toast(`Vérification impossible : ${errorText(e)}`);
            }
            refresh();
          }],
        ];
        box.replaceChildren(...items.map(([title, sub, action]) => row({ title, sub, onclick: action })));
      }

      refresh();
      window.stv.version().then((v) => { version = v; refresh(); });
      if (Repo.isXtream) Repo.userInfo().then((i) => { info = i; refresh(); }).catch(() => {});
      return h('div', { class: 'screen' }, topBar('settings'), h('h1', { text: 'Paramètres' }), box);
    },
  };
}

function ListsView() {
  return {
    render() {
      const box = h('div', { class: 'scroll' });
      function refresh() {
        const lists = Prefs.playlists();
        const activeId = Prefs.active && Prefs.active.id;
        const rows = lists.map((p) => {
          const kind = { xtream: 'Xtream', free: 'Gratuit', m3u: 'M3U' }[p.type] || 'M3U';
          const detail = p.type === 'xtream' ? `${p.user}  •  ${p.server}` : p.type === 'free' ? 'Chaînes françaises et arabes, films libres de droits' : p.url;
          return row({
            title: `${p.id === activeId ? '✓  ' : ''}${p.name}`, left: kind,
            sub: `${detail}\n${p.id === activeId ? 'Liste utilisée en ce moment' : 'Cliquez pour l\'utiliser  •  clic droit pour la supprimer'}`,
            onclick: () => {
              if (p.id !== activeId) {
                Prefs.setActive(p.id);
                Repo.clear();
                toast(`Liste « ${p.name} » sélectionnée`);
              }
              reset(HomeView());
            },
            oncontextmenu: () => {
              // eslint-disable-next-line no-alert
              if (!window.confirm(`Supprimer « ${p.name} » ?\nSes favoris et son historique seront aussi effacés.`)) return;
              Prefs.removePlaylist(p.id);
              Repo.clear();
              if (!Prefs.active) reset(LoginView());
              else refresh();
            },
          });
        });
        rows.push(row({ title: '＋  Ajouter une liste', sub: 'Chaînes gratuites, code Xtream ou lien M3U', onclick: () => open(LoginView()) }));
        box.replaceChildren(...rows);
      }
      refresh();
      return h('div', { class: 'screen' }, topBar('lists'), h('h1', { text: 'Mes listes' }), box);
    },
  };
}

// ---- Start -----------------------------------------------------------------------------------

Repo.lastLivePlayed = null;
reset(Prefs.active ? HomeView() : LoginView());
