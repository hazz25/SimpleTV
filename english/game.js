// "Next Stop: London": story chapters, daily spaced review, phrasebook.
'use strict';

// ---- Helpers ---------------------------------------------------------------------------------

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

const shuffle = (a) => {
  const b = a.slice();
  for (let i = b.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [b[i], b[j]] = [b[j], b[i]];
  }
  return b;
};

let toastTimer;
function toast(msg) {
  const t = document.getElementById('toast');
  t.textContent = msg;
  t.classList.remove('hidden');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => t.classList.add('hidden'), 2800);
}

const DAY = 24 * 3600 * 1000;
const today = () => new Date().toISOString().slice(0, 10);
const yesterday = () => new Date(Date.now() - DAY).toISOString().slice(0, 10);

// ---- Saved progress --------------------------------------------------------------------------

const Game = {
  key: 'nextstop_v1',
  data: null,
  load() {
    try {
      this.data = JSON.parse(localStorage.getItem(this.key)) || null;
    } catch (e) {
      this.data = null;
    }
    this.data = Object.assign({
      xp: 0, streak: 0, lastDay: null, done: {}, cards: {}, settings: { accent: 'uk', slow: false, typing: false },
    }, this.data || {});
  },
  save() {
    localStorage.setItem(this.key, JSON.stringify(this.data));
  },
  get settings() {
    return this.data.settings;
  },
  addXp(n) {
    this.data.xp += n;
    this.touchStreak();
    this.save();
  },
  touchStreak() {
    const d = today();
    if (this.data.lastDay === d) return;
    this.data.streak = this.data.lastDay === yesterday() ? this.data.streak + 1 : 1;
    this.data.lastDay = d;
  },
  get streak() {
    const last = this.data.lastDay;
    return last === today() || last === yesterday() ? this.data.streak : 0;
  },
  learn(id) {
    if (!CARDS[id] || this.data.cards[id]) return false;
    // New expressions come back tomorrow: they were just practised.
    this.data.cards[id] = { box: 0, due: Date.now() + DAY / 2, seen: 0, ok: 0 };
    this.save();
    return true;
  },
  due() {
    const now = Date.now();
    return Object.entries(this.data.cards).filter(([, c]) => c.due <= now).map(([id]) => id);
  },
  review(id, correct) {
    const c = this.data.cards[id];
    if (!c) return;
    const INTERVALS = [1, 2, 4, 8, 16, 32, 64];
    c.seen++;
    if (correct) {
      c.ok++;
      c.box = Math.min(c.box + 1, INTERVALS.length - 1);
      c.due = Date.now() + INTERVALS[c.box] * DAY - 3600 * 1000;
    } else {
      c.box = 0;
      c.due = Date.now() + 10 * 60 * 1000;
    }
    this.save();
  },
  unlocked(index) {
    return index === 0 || !!this.data.done[CHAPTERS[index - 1].id];
  },
};
Game.load();

// ---- Comparing what was said or typed --------------------------------------------------------

const CONTRACTIONS = {
  "i'm": 'i am', "you're": 'you are', "it's": 'it is', "that's": 'that is', "i'll": 'i will', "i'd": 'i would',
  "i've": 'i have', "don't": 'do not', "didn't": 'did not', "can't": 'cannot', "couldn't": 'could not',
  "there's": 'there is', "let's": 'let us', "we'll": 'we will', "you'll": 'you will', "he's": 'he is',
  "she's": 'she is', "isn't": 'is not', "aren't": 'are not', "won't": 'will not', "what's": 'what is',
  "you've": 'you have', "we're": 'we are', "they're": 'they are', "wasn't": 'was not', "doesn't": 'does not',
};

function words(s) {
  return s.toLowerCase().replace(/[’‘`]/g, "'").replace(/[^a-z0-9' ]+/g, ' ').split(/\s+/)
    .filter(Boolean).flatMap((w) => (CONTRACTIONS[w] || w.replace(/^'+|'+$/g, '')).split(' ')).filter(Boolean);
}

function similarity(said, expected) {
  const want = words(expected);
  const pool = words(said);
  if (!want.length) return 0;
  let hit = 0;
  for (const w of want) {
    const i = pool.indexOf(w);
    if (i >= 0) {
      hit++;
      pool.splice(i, 1);
    }
  }
  return hit / want.length - Math.min(0.25, pool.length * 0.05);
}

function bestMatch(guesses, answers) {
  let best = 0;
  for (const g of guesses) for (const a of answers) best = Math.max(best, similarity(g, a));
  return best;
}

// ---- Navigation ------------------------------------------------------------------------------

const app = document.getElementById('app');
const stack = [];

function show(view) {
  Voice.stop();
  stack.push(view);
  draw();
}

function draw() {
  const v = stack[stack.length - 1];
  app.replaceChildren(v());
  window.scrollTo(0, 0);
}

function back() {
  Voice.stop();
  if (stack.length <= 1) return false;
  stack.pop();
  draw();
  return true;
}

function home() {
  Voice.stop();
  stack.length = 1;
  draw();
}

// Used by the Android app's Back button.
window.goBack = () => back();

function topBar(title, extra) {
  return h('div', { class: 'top' }, h('button', { class: 'icon-btn', text: '←', 'aria-label': 'Retour', onclick: back }),
    h('h2', { text: title }), extra || null);
}

function speakTool(text, gender, label = '🔊') {
  return h('button', { class: 'tool', text: label, onclick: () => Voice.speak(text, { gender, slow: label === '🐢' }) });
}

// ---- Home ------------------------------------------------------------------------------------

function HomeView() {
  const learned = Object.keys(Game.data.cards).length;
  const due = Game.due().length;
  const next = CHAPTERS.findIndex((c) => !Game.data.done[c.id]);
  const allDone = next < 0;
  return h('div', null,
    h('h1', { text: 'Next Stop: London' }),
    h('div', { class: 'muted', text: 'Votre nouvelle vie à Londres, en anglais.' }),
    h('div', { class: 'stats' },
      h('div', { class: 'stat' }, h('b', { text: `🔥 ${Game.streak}` }), h('span', { text: Game.streak > 1 ? 'jours de suite' : 'jour de suite' })),
      h('div', { class: 'stat' }, h('b', { text: `⭐ ${Game.data.xp}` }), h('span', { text: 'points' })),
      h('div', { class: 'stat' }, h('b', { text: `📒 ${learned}` }), h('span', { text: 'expressions' }))),
    h('button', {
      class: 'btn primary',
      onclick: () => (allDone ? show(ChaptersView) : startChapter(next)),
    }, allDone ? '🗺️  Rejouer un chapitre' : `▶  ${next === 0 && !learned ? 'Commencer l\'histoire' : 'Continuer l\'histoire'}`,
    h('span', { class: 'sub', text: allDone ? 'Saison 1 terminée !' : `Chapitre ${next + 1} : ${CHAPTERS[next].title}` })),
    h('button', { class: 'btn', onclick: () => show(() => ChallengeView()) },
      '🎯  Défi du jour',
      h('span', { class: 'sub', text: learned ? (due ? `${Math.min(due, 12)} expression${due > 1 ? 's' : ''} à réviser (5 min)` : 'Tout est révisé pour aujourd\'hui ✓') : 'Terminez le premier chapitre pour le débloquer' })),
    h('button', { class: 'btn', onclick: () => show(ChaptersView) }, '🗺️  Chapitres', h('span', { class: 'sub', text: `${Object.keys(Game.data.done).length} / ${CHAPTERS.length} terminés` })),
    h('button', { class: 'btn', onclick: () => show(PhrasebookView) }, '📒  Mon carnet', h('span', { class: 'sub', text: 'Toutes les expressions apprises, à réécouter' })),
    h('button', { class: 'btn', onclick: () => show(SettingsView) }, '⚙️  Réglages', h('span', { class: 'sub', text: 'Accent, vitesse, micro' })),
    Voice.canSpeak() ? null : h('p', { class: 'muted small', text: 'Votre navigateur ne lit pas les voix : utilisez Chrome, Edge ou l\'app Android pour entendre les dialogues.' }));
}

function ChaptersView() {
  return h('div', null, topBar('Chapitres'),
    CHAPTERS.map((c, i) => {
      const done = Game.data.done[c.id];
      const open = Game.unlocked(i);
      return h('button', {
        class: `btn chapter${open ? '' : ' locked'}`,
        onclick: () => (open ? startChapter(i) : toast('Terminez le chapitre précédent pour débloquer celui-ci.')),
      },
      h('div', { class: 'num', text: open ? String(i + 1) : '🔒' }),
      h('div', { class: 'body' }, h('div', { text: c.title }), h('div', { class: 'sub muted small', text: c.place })),
      done ? h('div', { class: 'stars', text: '★'.repeat(done.stars) + '☆'.repeat(3 - done.stars) }) : null);
    }));
}

// ---- Story chapters --------------------------------------------------------------------------

function startChapter(index) {
  const chapter = CHAPTERS[index];
  const state = { index, chapter, step: -1, mistakes: 0, xp: 0, learned: [], chat: [] };
  show(() => ChapterView(state));
}

function ChapterView(state) {
  const { chapter } = state;
  const root = h('div', { class: 'story' });
  const total = chapter.steps.length;
  const bar = h('div', { class: 'progress' }, h('div'));
  const chat = h('div', { class: 'chat' });
  const task = h('div', { class: 'task' });
  root.append(topBar(chapter.title, bar), h('div', { class: 'place', text: chapter.place }), chat, task);

  function setProgress() {
    bar.firstChild.style.width = `${Math.max(0, state.step) / total * 100}%`;
  }

  function addBubble(who, en, fr, { me = false } = {}) {
    const ch = CHARACTERS[who] || CHARACTERS.me;
    const frEl = h('div', { class: 'fr hidden', text: fr || '' });
    const b = h('div', { class: `bubble${me ? ' me' : ''}` },
      h('div', { class: 'who', text: `${ch.emoji} ${me ? 'Vous' : ch.name}` }),
      h('div', { class: 'en', text: en }),
      fr ? frEl : null,
      h('div', { class: 'tools' }, speakTool(en, ch.voice), speakTool(en, ch.voice, '🐢'),
        fr ? h('button', { class: 'tool', text: 'FR', onclick: () => frEl.classList.toggle('hidden') }) : null));
    chat.append(b);
    b.scrollIntoView({ behavior: 'smooth', block: 'end' });
    return b;
  }

  function speakAs(who, text) {
    const ch = CHARACTERS[who] || CHARACTERS.me;
    return Voice.speak(text, { gender: ch.voice, slow: Game.settings.slow });
  }

  function learnFrom(step) {
    for (const id of step.learn || []) {
      if (Game.learn(id)) {
        state.learned.push(id);
        toast(`📒 Nouvelle expression : ${CARDS[id].en}`);
      }
    }
  }

  function continueButton(label = 'Continuer') {
    return h('button', { class: 'btn primary', text: label, onclick: next });
  }

  function feedback(ok, text) {
    return h('div', { class: `feedback ${ok ? 'ok' : 'ko'}` }, h('b', { text: ok ? '✓ Bien joué !' : '✗ Pas tout à fait' }), text || '');
  }

  function gain(n) {
    state.xp += n;
    Game.addXp(n);
  }

  function next() {
    Voice.stop();
    state.step++;
    setProgress();
    if (state.step >= total) {
      finish();
      return;
    }
    const s = chapter.steps[state.step];
    task.replaceChildren();
    ({ say, choice, listen, speak, gap, order, note }[s.t])(s);
  }

  // A character speaks.
  function say(s) {
    addBubble(s.who, s.en, s.fr);
    speakAs(s.who, s.en);
    learnFrom(s);
    task.append(continueButton());
  }

  // Pick the most natural reply.
  function choice(s) {
    if (s.en && s.en !== '…') {
      addBubble(s.who, s.en, s.fr);
      speakAs(s.who, s.en);
    } else if (s.fr) {
      chat.append(h('div', { class: 'narration', text: s.fr }));
    }
    let firstTry = true;
    task.append(h('div', { class: 'label', text: 'Que répondez-vous ?' }));
    const buttons = shuffle(s.options).map((o) => {
      const b = h('button', {
        class: 'option', text: o.en,
        onclick: () => {
          const fb = task.querySelector('.feedback');
          if (fb) fb.remove();
          if (o.ok) {
            b.classList.add('ok');
            buttons.forEach((x) => { x.disabled = true; });
            addBubble('me', o.en, null, { me: true });
            Voice.speak(o.en, { gender: 'm', slow: Game.settings.slow });
            if (firstTry) gain(10);
            learnFrom(s);
            task.append(feedback(true, o.fb), continueButton());
          } else {
            b.classList.add('ko');
            b.disabled = true;
            if (firstTry) state.mistakes++;
            firstTry = false;
            task.append(feedback(false, o.fb));
          }
        },
      });
      return b;
    });
    task.append(...buttons);
  }

  // Listen without the text, then answer.
  function listen(s) {
    const ch = CHARACTERS[s.who] || CHARACTERS.me;
    const play = (slow) => Voice.speak(s.en, { gender: ch.voice, slow });
    task.append(
      h('div', { class: 'label', text: `🎧 Écoutez ${ch.name}` }),
      h('button', { class: 'big-listen', onclick: () => play(false) }, '🔊  Écouter'),
      h('div', { class: 'row' }, h('button', { class: 'tool', text: '🐢 Plus lentement', onclick: () => play(true) })),
      h('div', { class: 'prompt', text: s.q }));
    let firstTry = true;
    const buttons = shuffle(s.options).map((o) => {
      const b = h('button', {
        class: 'option', text: o.fr,
        onclick: () => {
          if (o.ok) {
            b.classList.add('ok');
            buttons.forEach((x) => { x.disabled = true; });
            if (firstTry) gain(10);
            addBubble(s.who, s.en, null);
            learnFrom(s);
            task.append(feedback(true, `Il a dit : « ${s.en} »`), continueButton());
          } else {
            b.classList.add('ko');
            b.disabled = true;
            if (firstTry) state.mistakes++;
            firstTry = false;
            toast('Réécoutez, au besoin plus lentement 🐢');
          }
        },
      });
      return b;
    });
    task.append(...buttons);
    setTimeout(() => play(Game.settings.slow), 300);
  }

  // Say it out loud (or type it).
  function speak(s) {
    const heard = h('div', { class: 'heard muted' });
    const model = h('div', { class: 'model hidden', text: s.model });
    let fails = 0;
    const done = (text, viaVoice) => {
      addBubble('me', text, null, { me: true });
      gain(viaVoice ? 20 : 12);
      learnFrom(s);
      task.replaceChildren(feedback(true, viaVoice ? `Phrase modèle : « ${s.model} »` : `Bien écrit ! Phrase modèle : « ${s.model} »`), continueButton());
      Voice.speak(s.model, { gender: 'm', slow: Game.settings.slow });
    };
    const miss = () => {
      fails++;
      if (fails === 1) state.mistakes++;
      model.classList.remove('hidden');
      if (fails >= 2 && !task.querySelector('.skip')) {
        task.append(h('button', {
          class: 'btn center skip', text: 'Passer cette étape',
          onclick: () => {
            addBubble('me', s.model, null, { me: true });
            learnFrom(s);
            next();
          },
        }));
      }
    };
    const typed = h('input', { class: 'field hidden', placeholder: 'Écrivez votre phrase en anglais', autocapitalize: 'off' });
    const check = h('button', {
      class: 'btn primary hidden', text: 'Vérifier',
      onclick: () => {
        const v = typed.value.trim();
        if (!v) return;
        if (bestMatch([v], s.answers.concat([s.model])) >= 0.85) done(v, false);
        else {
          heard.textContent = 'Presque… comparez avec la phrase modèle ci-dessus.';
          miss();
        }
      },
    });
    typed.addEventListener('keydown', (e) => e.key === 'Enter' && check.click());
    const showTyping = () => {
      typed.classList.remove('hidden');
      check.classList.remove('hidden');
      mic.classList.add('hidden');
      typeBtn.classList.add('hidden');
      typed.focus();
    };
    const mic = h('button', {
      class: 'mic', text: '🎤', 'aria-label': 'Parler',
      onclick: async () => {
        mic.classList.add('listening');
        heard.textContent = 'Je vous écoute…';
        try {
          const guesses = await Voice.listen();
          mic.classList.remove('listening');
          if (!guesses.length) {
            heard.textContent = 'Je n\'ai rien entendu. Réessayez en parlant un peu plus fort.';
            return;
          }
          heard.textContent = `J'ai entendu : « ${guesses[0]} »`;
          if (bestMatch(guesses, s.answers.concat([s.model])) >= 0.8) done(guesses[0], true);
          else miss();
        } catch (e) {
          mic.classList.remove('listening');
          heard.textContent = /not-allowed|permission/i.test(e.message)
            ? 'Le micro est bloqué. Autorisez-le, ou écrivez votre réponse.'
            : 'Le micro ne fonctionne pas ici : écrivez votre réponse.';
          showTyping();
        }
      },
    });
    const typeBtn = h('button', { class: 'tool', text: '⌨️ Écrire plutôt', onclick: showTyping });
    task.append(
      h('div', { class: 'label', text: '🗣️ À vous de parler' }),
      h('div', { class: 'prompt', text: s.fr }),
      model,
      mic, heard,
      h('div', { class: 'row' }, h('button', { class: 'tool', text: '💡 Voir la phrase', onclick: () => model.classList.remove('hidden') }),
        h('button', { class: 'tool', text: '🔊 Écouter le modèle', onclick: () => Voice.speak(s.model, { gender: 'm', slow: true }) }), typeBtn),
      typed, check);
    if (Game.settings.typing || !Voice.canListen()) showTyping();
  }

  // Fill in the missing word.
  function gap(s) {
    const blank = h('span', { class: 'blank', text: '…' });
    task.append(h('div', { class: 'label', text: '✏️ Complétez' }), h('div', { class: 'muted small', text: s.fr }),
      h('div', { class: 'sentence' }, `${s.before} `, blank, ` ${s.after}`));
    let firstTry = true;
    const buttons = shuffle(s.options).map((w) => {
      const b = h('button', {
        class: 'option', text: w,
        onclick: () => {
          const fb = task.querySelector('.feedback');
          if (fb) fb.remove();
          if (w === s.answer) {
            b.classList.add('ok');
            buttons.forEach((x) => { x.disabled = true; });
            blank.textContent = w;
            const full = `${s.before} ${w} ${s.after}`.replace(/\s+([.,!?])/g, '$1');
            Voice.speak(full, { gender: 'm', slow: Game.settings.slow });
            if (firstTry) gain(10);
            learnFrom(s);
            task.append(feedback(true, s.fb), continueButton());
          } else {
            b.classList.add('ko');
            b.disabled = true;
            if (firstTry) state.mistakes++;
            firstTry = false;
            task.append(feedback(false, s.fb));
          }
        },
      });
      return b;
    });
    task.append(...buttons);
  }

  // Put the words in order.
  function order(s) {
    const tokens = s.answer.replace(/[.?!]$/, '').split(' ');
    const line = h('div', { class: 'answer-line' });
    const chips = h('div', { class: 'chips' });
    const picked = [];
    let firstTry = true;
    const renderLine = () => line.replaceChildren(...picked.map((p, i) => h('button', {
      class: 'chip', text: p.word,
      onclick: () => {
        p.src.classList.remove('used');
        picked.splice(i, 1);
        renderLine();
      },
    })));
    shuffle(tokens.map((w, i) => ({ w, i }))).forEach(({ w }) => {
      const c = h('button', {
        class: 'chip', text: w,
        onclick: () => {
          if (c.classList.contains('used')) return;
          c.classList.add('used');
          picked.push({ word: w, src: c });
          renderLine();
        },
      });
      chips.append(c);
    });
    const checkBtn = h('button', {
      class: 'btn primary', text: 'Vérifier',
      onclick: () => {
        const fb = task.querySelector('.feedback');
        if (fb) fb.remove();
        const said = picked.map((p) => p.word).join(' ');
        if (words(said).join(' ') === words(s.answer).join(' ')) {
          checkBtn.remove();
          if (firstTry) gain(15);
          addBubble('me', s.answer, null, { me: true });
          Voice.speak(s.answer, { gender: 'm', slow: Game.settings.slow });
          learnFrom(s);
          task.append(feedback(true, `« ${s.answer} »`), continueButton());
        } else {
          if (firstTry) state.mistakes++;
          firstTry = false;
          task.append(feedback(false, 'L\'ordre n\'est pas encore bon. Touchez un mot pour le retirer.'));
        }
      },
    });
    task.append(h('div', { class: 'label', text: '🧩 Remettez les mots dans l\'ordre' }), h('div', { class: 'prompt', text: s.fr }), line, chips, checkBtn);
  }

  // A tip about culture or grammar.
  function note(s) {
    task.append(h('div', { class: 'note-card' }, h('h3', { text: s.title }), h('div', { text: s.fr })), continueButton());
  }

  function finish() {
    const stars = state.mistakes <= 1 ? 3 : state.mistakes <= 3 ? 2 : 1;
    const prev = Game.data.done[chapter.id];
    Game.data.done[chapter.id] = { stars: Math.max(stars, prev ? prev.stars : 0) };
    gain(prev ? 10 : 50);
    const nextIndex = state.index + 1;
    task.replaceChildren(h('div', { class: 'end' },
      h('div', { class: 'big', text: '🎉' }),
      h('h2', { text: 'Chapitre terminé !' }),
      h('div', { class: 'stars', text: '★'.repeat(stars) + '☆'.repeat(3 - stars) }),
      h('p', { class: 'muted', text: `+${state.xp} points  •  ${state.learned.length} nouvelle${state.learned.length > 1 ? 's' : ''} expression${state.learned.length > 1 ? 's' : ''} dans votre carnet` }),
      h('p', { class: 'small muted', text: 'Revenez demain pour le défi du jour : c\'est la répétition qui fait retenir.' })),
    nextIndex < CHAPTERS.length
      ? h('button', { class: 'btn primary', text: `▶  Chapitre ${nextIndex + 1} : ${CHAPTERS[nextIndex].title}`, onclick: () => { stack.pop(); startChapter(nextIndex); } })
      : h('button', { class: 'btn primary', text: '🏆  Saison terminée ! Retour à l\'accueil', onclick: home }),
    h('button', { class: 'btn center', text: 'Accueil', onclick: home }));
  }

  // Intro card.
  task.append(h('div', { class: 'note-card' }, h('h3', { text: `Chapitre ${state.index + 1}` }), h('div', { text: chapter.intro })),
    h('button', { class: 'btn primary', text: 'C\'est parti !', onclick: next }));
  setProgress();
  return root;
}

// ---- Daily challenge (spaced repetition) -----------------------------------------------------

function ChallengeView() {
  const learned = Object.keys(Game.data.cards);
  if (!learned.length) {
    return h('div', null, topBar('Défi du jour'),
      h('div', { class: 'note-card' }, h('h3', { text: 'Pas encore d\'expressions' }),
        h('div', { text: 'Jouez le premier chapitre : chaque expression rencontrée s\'ajoute à votre carnet, et le défi du jour vous la fera réviser au bon moment.' })),
      h('button', { class: 'btn primary', text: '▶  Commencer l\'histoire', onclick: () => { stack.pop(); startChapter(0); } }));
  }
  let queue = Game.due();
  let extra = false;
  if (!queue.length) {
    queue = shuffle(learned).slice(0, 8);
    extra = true;
  }
  queue = shuffle(queue).slice(0, 12);
  const session = { queue, pos: 0, right: 0, retry: new Set() };
  const root = h('div');
  const bar = h('div', { class: 'progress' }, h('div'));
  const task = h('div', { class: 'task' });
  root.append(topBar('Défi du jour', bar));
  if (extra) root.append(h('p', { class: 'muted small', text: 'Tout est déjà révisé pour aujourd\'hui : voici un entraînement bonus.' }));
  root.append(task);

  function others(id, field, n) {
    return shuffle(Object.keys(CARDS).filter((k) => k !== id)).slice(0, n).map((k) => CARDS[k][field]);
  }

  function nextCard() {
    Voice.stop();
    bar.firstChild.style.width = `${session.pos / session.queue.length * 100}%`;
    if (session.pos >= session.queue.length) return end();
    const id = session.queue[session.pos];
    const card = CARDS[id];
    const box = (Game.data.cards[id] || { box: 0 }).box;
    task.replaceChildren();
    // Harder exercises as an expression gets better known.
    const kind = box <= 0 ? 'listen' : box === 1 ? 'pick' : (box % 2 === 0 ? 'produce' : 'listen-pick');
    const answer = (ok) => {
      if (!session.retry.has(id)) Game.review(id, ok);
      if (ok) {
        session.right++;
        Game.addXp(5);
      } else if (!session.retry.has(id)) {
        session.retry.add(id);
        session.queue.push(id); // seen again at the end of the session
      }
      task.append(h('div', { class: `feedback ${ok ? 'ok' : 'ko'}` }, h('b', { text: ok ? '✓ Bien joué !' : '✗ À revoir' }),
        h('div', { text: `${card.en} = ${card.fr}` }), h('div', { class: 'small muted', text: card.note })),
      h('button', { class: 'btn primary', text: 'Suivant', onclick: () => { session.pos++; nextCard(); } }));
      Voice.speak(card.en, { gender: 'f', slow: Game.settings.slow });
    };
    const options = (correct, wrong, label) => {
      const buttons = shuffle([correct, ...wrong]).map((t) => {
        const b = h('button', {
          class: 'option', text: t,
          onclick: () => {
            buttons.forEach((x) => { x.disabled = true; });
            const ok = t === correct;
            b.classList.add(ok ? 'ok' : 'ko');
            if (!ok) buttons.find((x) => x.textContent === correct).classList.add('ok');
            answer(ok);
          },
        });
        return b;
      });
      task.append(h('div', { class: 'prompt', text: label }), ...buttons);
    };

    if (kind === 'listen' || kind === 'listen-pick') {
      task.append(h('div', { class: 'label', text: '🎧 Écoutez' }),
        h('button', { class: 'big-listen', onclick: () => Voice.speak(card.en, { gender: 'f' }) }, '🔊  Écouter'),
        h('div', { class: 'row' }, h('button', { class: 'tool', text: '🐢 Plus lentement', onclick: () => Voice.speak(card.en, { gender: 'f', slow: true }) })));
      options(card.fr, others(id, 'fr', 3), 'Que signifie cette phrase ?');
      setTimeout(() => Voice.speak(card.en, { gender: 'f', slow: Game.settings.slow }), 250);
    } else if (kind === 'pick') {
      task.append(h('div', { class: 'label', text: '🇬🇧 Comment dit-on…' }));
      options(card.en, others(id, 'en', 3), card.fr);
    } else {
      // Say it, or type it, from the French.
      const heard = h('div', { class: 'heard muted' });
      const typed = h('input', { class: 'field', placeholder: 'Écrivez la phrase en anglais', autocapitalize: 'off' });
      const verify = (guesses, voice) => {
        const ok = bestMatch(guesses, [card.en]) >= (voice ? 0.8 : 0.85);
        heard.textContent = voice ? `J'ai entendu : « ${guesses[0] || ''} »` : '';
        task.querySelectorAll('button, input').forEach((x) => { x.disabled = true; });
        answer(ok);
      };
      const check = h('button', { class: 'btn primary', text: 'Vérifier', onclick: () => typed.value.trim() && verify([typed.value], false) });
      typed.addEventListener('keydown', (e) => e.key === 'Enter' && check.click());
      task.append(h('div', { class: 'label', text: '🗣️ Dites-le en anglais' }), h('div', { class: 'prompt', text: card.fr }));
      if (Voice.canListen() && !Game.settings.typing) {
        const mic = h('button', {
          class: 'mic', text: '🎤',
          onclick: async () => {
            mic.classList.add('listening');
            heard.textContent = 'Je vous écoute…';
            try {
              const g = await Voice.listen();
              mic.classList.remove('listening');
              if (!g.length) {
                heard.textContent = 'Je n\'ai rien entendu. Réessayez.';
                return;
              }
              verify(g, true);
            } catch (e) {
              mic.classList.remove('listening');
              heard.textContent = 'Le micro ne fonctionne pas ici : écrivez votre réponse.';
            }
          },
        });
        task.append(mic, heard, h('div', { class: 'muted small', text: 'ou écrivez-la :' }));
      }
      task.append(typed, check, h('button', { class: 'btn center', text: 'Je ne sais pas', onclick: () => { task.querySelectorAll('button, input').forEach((x) => { x.disabled = true; }); answer(false); } }));
    }
  }

  function end() {
    const total = new Set(session.queue).size;
    task.replaceChildren(h('div', { class: 'end' },
      h('div', { class: 'big', text: session.right >= total ? '🏆' : '💪' }),
      h('h2', { text: 'Défi terminé !' }),
      h('p', { text: `${Math.min(session.right, total)} / ${total} bonnes réponses du premier coup` }),
      h('p', { class: 'muted', text: `🔥 Série : ${Game.streak} jour${Game.streak > 1 ? 's' : ''}` }),
      h('p', { class: 'small muted', text: 'Les expressions bien retenues reviendront dans quelques jours, les autres dès demain.' })),
    h('button', { class: 'btn primary', text: 'Accueil', onclick: home }));
  }

  nextCard();
  return root;
}

// ---- Phrasebook and settings -----------------------------------------------------------------

function PhrasebookView() {
  const learned = Game.data.cards;
  const LEVEL = ['Nouvelle', 'En cours', 'En cours', 'Bien connue', 'Bien connue', 'Maîtrisée', 'Maîtrisée'];
  const groups = CHAPTERS.map((c) => {
    const ids = [...new Set(c.steps.flatMap((s) => s.learn || []))].filter((id) => learned[id]);
    if (!ids.length) return null;
    return h('div', null, h('h3', { text: c.title }),
      ids.map((id) => h('div', { class: 'card' },
        h('div', { class: 'head' }, h('div', { class: 'en', text: CARDS[id].en }), speakTool(CARDS[id].en, 'f'), speakTool(CARDS[id].en, 'f', '🐢')),
        h('div', { class: 'fr', text: CARDS[id].fr }),
        h('div', { class: 'note', text: CARDS[id].note }),
        h('div', { class: 'level', text: `Niveau : ${LEVEL[learned[id].box] || 'Maîtrisée'}` }))));
  }).filter(Boolean);
  return h('div', null, topBar('Mon carnet'),
    groups.length ? groups : h('p', { class: 'muted', text: 'Votre carnet est vide pour l\'instant : chaque expression rencontrée dans l\'histoire viendra s\'y ranger.' }));
}

function SettingsView() {
  const s = Game.settings;
  const set = (k, v) => {
    s[k] = v;
    Game.save();
    draw();
  };
  const choice = (label, active, onclick) => h('button', { class: `option${active ? ' ok' : ''}`, text: label, onclick });
  return h('div', null, topBar('Réglages'),
    h('div', { class: 'task' },
      h('div', { class: 'label', text: 'Accent' }),
      choice('🇬🇧 Britannique (Londres)', s.accent !== 'us', () => set('accent', 'uk')),
      choice('🇺🇸 Américain', s.accent === 'us', () => set('accent', 'us')),
      h('div', { class: 'label', text: 'Vitesse des voix', style: null }),
      choice('Normale', !s.slow, () => set('slow', false)),
      choice('Ralentie (pour commencer)', s.slow, () => set('slow', true)),
      h('div', { class: 'label', text: 'Réponses orales' }),
      choice('🎤 Parler au micro', !s.typing, () => set('typing', false)),
      choice('⌨️ Toujours écrire (pas de micro)', s.typing, () => set('typing', true)),
      h('div', { class: 'row' }, h('button', { class: 'tool', text: '🔊 Tester la voix', onclick: () => Voice.speak('Hello! Welcome to London. Mind the gap!', { gender: 'f' }) }))),
    Voice.canListen() ? null : h('p', { class: 'muted small', text: 'La reconnaissance vocale n\'est pas disponible dans ce navigateur : vous pourrez écrire vos réponses. Elle fonctionne dans Chrome, Edge et l\'app Android.' }),
    h('button', {
      class: 'btn center', text: 'Recommencer depuis le début',
      onclick: () => {
        // eslint-disable-next-line no-alert
        if (window.confirm('Effacer toute votre progression (points, chapitres, carnet) ?')) {
          localStorage.removeItem(Game.key);
          Game.load();
          home();
        }
      },
    }));
}

// ---- Start -----------------------------------------------------------------------------------

stack.push(HomeView);
draw();
