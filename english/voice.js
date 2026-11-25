// English voices and speech recognition: the browser's own (web version) or Android's (app version).
'use strict';

const Voice = (() => {
  const bridge = window.AndroidVoice || null;
  const pending = {};
  let seq = 0;

  // Callbacks from the Android app.
  window.__voiceDone = (id) => {
    const p = pending[id];
    if (p) {
      delete pending[id];
      p.resolve();
    }
  };
  window.__voiceResult = (id, json) => {
    const p = pending[id];
    if (!p) return;
    delete pending[id];
    try {
      const r = JSON.parse(json);
      if (r.error) p.reject(new Error(r.error));
      else p.resolve(r.results || []);
    } catch (e) {
      p.reject(e);
    }
  };

  const lang = () => (Game.settings.accent === 'us' ? 'en-US' : 'en-GB');

  const FEMALE = /female|libby|sonia|maisie|mia|hazel|susan|kate|serena|martha|samantha|jenny|aria|emma|ava|michelle|zira|fiona|karen|moira|tessa/i;
  const MALE = /\bmale|ryan|george|thomas|daniel|oliver|arthur|guy|davis|eric|brian|andrew|christopher|mark|david|alex|fred/i;

  function pickVoice(gender) {
    if (!('speechSynthesis' in window)) return null;
    const all = speechSynthesis.getVoices();
    const wanted = lang().toLowerCase();
    let pool = all.filter((v) => v.lang.replace('_', '-').toLowerCase() === wanted);
    if (!pool.length) pool = all.filter((v) => v.lang.toLowerCase().startsWith('en'));
    if (!pool.length) return null;
    const quality = (v) => (/natural|neural|online|google|premium|enhanced/i.test(v.name) ? 2 : 0) + (v.localService ? 0 : 1);
    const byGender = pool.filter((v) => (gender === 'f' ? FEMALE.test(v.name) && !/\bmale/i.test(v.name) : MALE.test(v.name)));
    const list = (byGender.length ? byGender : pool).slice().sort((a, b) => quality(b) - quality(a));
    return list[0];
  }

  if ('speechSynthesis' in window) speechSynthesis.getVoices(); // starts loading the voice list

  function canSpeak() {
    return !!bridge || 'speechSynthesis' in window;
  }

  function speak(text, { slow = false, gender = 'f' } = {}) {
    const rate = slow ? 0.7 : 0.95;
    if (bridge) {
      return new Promise((resolve, reject) => {
        const id = `s${++seq}`;
        pending[id] = { resolve, reject };
        bridge.speak(id, text, rate, gender === 'm' ? 0.9 : 1.1, lang());
        setTimeout(() => window.__voiceDone(id), 15000 + text.length * 120);
      });
    }
    if (!('speechSynthesis' in window)) return Promise.resolve();
    return new Promise((resolve) => {
      speechSynthesis.cancel();
      const u = new SpeechSynthesisUtterance(text);
      u.lang = lang();
      const v = pickVoice(gender);
      if (v) u.voice = v;
      u.rate = rate;
      u.pitch = v && ((gender === 'f') === FEMALE.test(v.name)) ? 1 : (gender === 'm' ? 0.85 : 1.15);
      u.onend = resolve;
      u.onerror = resolve;
      speechSynthesis.speak(u);
    });
  }

  function stop() {
    if (bridge) bridge.stop();
    else if ('speechSynthesis' in window) speechSynthesis.cancel();
  }

  function canListen() {
    if (bridge) return bridge.canListen();
    return !!(window.SpeechRecognition || window.webkitSpeechRecognition);
  }

  /** Resolves with what the person probably said (several guesses, best first). */
  function listen() {
    stop();
    if (bridge) {
      return new Promise((resolve, reject) => {
        const id = `l${++seq}`;
        pending[id] = { resolve, reject };
        bridge.listen(id, lang());
      });
    }
    const R = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!R) return Promise.reject(new Error('unsupported'));
    return new Promise((resolve, reject) => {
      const r = new R();
      r.lang = lang();
      r.interimResults = false;
      r.maxAlternatives = 5;
      let done = false;
      r.onresult = (e) => {
        done = true;
        const res = e.results[0];
        const out = [];
        for (let i = 0; i < res.length; i++) out.push(res[i].transcript);
        resolve(out);
      };
      r.onerror = (e) => {
        done = true;
        reject(new Error(e.error || 'error'));
      };
      r.onend = () => {
        if (!done) resolve([]);
      };
      r.start();
    });
  }

  return { canSpeak, speak, stop, canListen, listen };
})();
