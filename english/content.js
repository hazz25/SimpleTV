// Story and phrasebook of "Next Stop: London", an English game for intermediate French speakers.
// Each chapter is a real situation; every expression met in a chapter goes into the phrasebook,
// which the daily challenge reviews with spaced repetition.
'use strict';

const CHARACTERS = {
  officer: { name: 'Border officer', emoji: '🛂', voice: 'm' },
  agent: { name: 'Station agent', emoji: '🚇', voice: 'f' },
  sarah: { name: 'Sarah (estate agent)', emoji: '🏠', voice: 'f' },
  james: { name: 'James (your manager)', emoji: '👔', voice: 'm' },
  priya: { name: 'Priya (colleague)', emoji: '💻', voice: 'f' },
  mark: { name: 'Mark (colleague)', emoji: '🍺', voice: 'm' },
  bank: { name: 'Daniel (bank adviser)', emoji: '🏦', voice: 'm' },
  pharmacist: { name: 'Pharmacist', emoji: '💊', voice: 'f' },
  me: { name: 'You', emoji: '🙂', voice: 'm' },
};

// Phrasebook. "note" explains in French what makes the expression natural.
const CARDS = {
  // Chapter 1
  here_for_work: { en: "I'm here for work.", fr: 'Je suis ici pour le travail.', note: 'On dit "for work" ou "on business". "For working" est incorrect.' },
  how_long_staying: { en: 'How long are you staying?', fr: 'Combien de temps restez-vous ?', note: 'Le présent continu sert ici à parler d\'un projet déjà décidé.' },
  about_a_year: { en: 'About a year, maybe longer.', fr: 'Environ un an, peut-être plus.', note: '"About" = environ. Plus naturel que "approximately" à l\'oral.' },
  how_to_get_to: { en: 'Could you tell me how to get to King\'s Cross?', fr: 'Pourriez-vous me dire comment aller à King\'s Cross ?', note: '"Get to" = se rendre à. On ne dit pas "go to" après "how to" dans ce sens.' },
  right_platform: { en: 'Is this the right platform for Paddington?', fr: 'C\'est bien le bon quai pour Paddington ?', note: '"Platform" = quai. "Quay" désigne un quai de port.' },
  change_at: { en: 'You need to change at Green Park.', fr: 'Vous devez changer à Green Park.', note: 'On "change at" une station (correspondance).' },
  mind_the_gap: { en: 'Mind the gap.', fr: 'Attention à la marche (entre le train et le quai).', note: '"Mind" = faire attention à. Très britannique : "Mind your head", "Mind the step".' },
  tap_in: { en: 'You can just tap in with your card.', fr: 'Vous pouvez simplement valider avec votre carte.', note: '"Tap in / tap out" = passer sa carte sans contact à l\'entrée et à la sortie.' },

  // Chapter 2
  bills_included: { en: 'Are bills included?', fr: 'Les charges sont comprises ?', note: '"Bills" = factures (eau, gaz, électricité). Le mot "charges" ne s\'emploie pas ainsi en anglais.' },
  deposit: { en: 'How much is the deposit?', fr: 'Combien coûte la caution ?', note: '"Deposit" = caution / dépôt de garantie. "Caution" en anglais veut dire prudence.' },
  bit_pricey: { en: "It's a bit pricey for me.", fr: 'C\'est un peu cher pour moi.', note: '"Pricey" est plus doux et plus naturel que "too expensive".' },
  knock_off: { en: 'Is there any chance you could knock a bit off the rent?', fr: 'Y aurait-il moyen de baisser un peu le loyer ?', note: '"Knock something off" = faire une remise. Très courant pour négocier poliment.' },
  think_it_over: { en: "I'd like to think it over.", fr: 'J\'aimerais y réfléchir.', note: 'Façon polie de ne pas décider tout de suite.' },
  move_in: { en: 'When can I move in?', fr: 'Quand est-ce que je peux emménager ?', note: '"Move in" = emménager, "move out" = déménager (quitter un logement).' },
  sounds_good: { en: 'Sounds good to me.', fr: 'Ça me va.', note: 'Réponse naturelle pour accepter une proposition.' },
  actually: { en: "Actually, I'd prefer something closer to the station.", fr: 'En fait, je préférerais quelque chose de plus proche de la gare.', note: 'Faux ami : "actually" = en fait, pas "actuellement" (= currently).' },

  // Chapter 3
  nice_to_meet_you: { en: 'Nice to meet you.', fr: 'Ravi de vous rencontrer.', note: 'À la première rencontre. Ensuite, on dit "Nice to see you".' },
  what_do_you_do: { en: 'What do you do?', fr: 'Qu\'est-ce que tu fais dans la vie ?', note: 'Question sur le métier. "What are you doing?" veut dire "Qu\'es-tu en train de faire ?".' },
  in_charge_of: { en: "I'm in charge of the new project.", fr: 'Je suis responsable du nouveau projet.', note: '"Be in charge of" = être responsable de.' },
  let_me_know: { en: 'Let me know if you need anything.', fr: 'Dis-moi si tu as besoin de quoi que ce soit.', note: 'Phrase très fréquente au travail pour proposer son aide.' },
  getting_the_hang: { en: "I'm getting the hang of it.", fr: 'Je commence à m\'y faire.', note: '"Get the hang of something" = prendre le coup de main.' },
  catch_up: { en: "Let's catch up later.", fr: 'On se fait un point plus tard.', note: '"Catch up" = faire le point, ou prendre des nouvelles de quelqu\'un.' },
  sorry_didnt_catch: { en: "Sorry, I didn't catch your name.", fr: 'Pardon, je n\'ai pas bien saisi ton prénom.', note: '"Catch" = saisir, comprendre à l\'oral. Très utile et poli.' },
  eventually: { en: "Eventually, you'll work on the client side.", fr: 'À terme, tu travailleras côté client.', note: 'Faux ami : "eventually" = finalement / à terme, pas "éventuellement" (= possibly).' },

  // Chapter 4
  what_are_you_having: { en: 'What are you having?', fr: 'Qu\'est-ce que tu prends ?', note: 'Au pub ou au restaurant, "have" = prendre (boisson, plat).' },
  my_round: { en: "It's my round.", fr: 'C\'est ma tournée.', note: 'Au pub, chacun paie une "round" pour le groupe à tour de rôle.' },
  cheers: { en: 'Cheers!', fr: 'Merci ! / Santé !', note: 'En Grande-Bretagne, "cheers" veut souvent dire simplement merci.' },
  knackered: { en: "I'm absolutely knackered.", fr: 'Je suis complètement crevé.', note: 'Familier et britannique. Plus neutre : "I\'m exhausted".' },
  fancy: { en: 'Do you fancy another one?', fr: 'Tu en veux un autre ?', note: '"Fancy" = avoir envie de. "Do you fancy a coffee?" est très courant.' },
  pulling_your_leg: { en: "I'm just pulling your leg.", fr: 'Je te fais marcher.', note: 'Expression idiomatique pour dire qu\'on plaisante.' },
  call_it_a_night: { en: "I think I'll call it a night.", fr: 'Je crois que je vais rentrer.', note: '"Call it a night" = terminer la soirée.' },
  how_come: { en: 'How come you moved to London?', fr: 'Comment ça se fait que tu sois venu à Londres ?', note: '"How come" = pourquoi (familier). Attention : pas d\'inversion après "how come".' },

  // Chapter 5
  calling_about: { en: "I'm calling about my card.", fr: 'J\'appelle au sujet de ma carte.', note: 'Phrase d\'ouverture idéale au téléphone.' },
  could_you_repeat: { en: 'Could you repeat that, please?', fr: 'Pourriez-vous répéter, s\'il vous plaît ?', note: 'Indispensable au téléphone. Plus poli que "What?" ou "Repeat".' },
  spell_that: { en: 'Could you spell that for me?', fr: 'Pourriez-vous me l\'épeler ?', note: 'Utile pour les noms, adresses et e-mails.' },
  put_on_hold: { en: "I'm going to put you on hold for a moment.", fr: 'Je vais vous mettre en attente un instant.', note: '"On hold" = en attente au téléphone.' },
  blocked: { en: 'My card has been blocked.', fr: 'Ma carte a été bloquée.', note: 'Le present perfect décrit une situation qui dure encore maintenant.' },
  sort_it_out: { en: "We'll sort it out for you.", fr: 'On va vous arranger ça.', note: '"Sort something out" = régler un problème.' },
  look_into_it: { en: "I'll look into it.", fr: 'Je vais m\'en occuper / regarder ça.', note: '"Look into" = examiner, se renseigner sur un problème.' },
  anything_else: { en: 'Is there anything else I can help you with?', fr: 'Puis-je vous aider pour autre chose ?', note: 'Formule de fin d\'appel. Réponse : "No, that\'s all, thanks."' },

  // Chapter 6
  under_the_weather: { en: "I'm feeling a bit under the weather.", fr: 'Je ne me sens pas très bien.', note: 'Façon courante et polie de dire qu\'on est un peu malade.' },
  sore_throat: { en: "I've got a sore throat.", fr: 'J\'ai mal à la gorge.', note: '"Sore" = douloureux. "A headache" = mal à la tête, "a stomach ache" = mal au ventre.' },
  since_monday: { en: "I've had it since Monday.", fr: 'Je l\'ai depuis lundi.', note: '"Depuis" + durée qui continue = present perfect + "since" (date) ou "for" (durée).' },
  allergic: { en: "I'm allergic to penicillin.", fr: 'Je suis allergique à la pénicilline.', note: 'On dit "allergic to", jamais "allergic of".' },
  prescription: { en: 'Do I need a prescription for this?', fr: 'Faut-il une ordonnance pour ça ?', note: '"Prescription" = ordonnance. "Over the counter" = sans ordonnance.' },
  twice_a_day: { en: 'Take two tablets twice a day.', fr: 'Prenez deux comprimés deux fois par jour.', note: '"Once, twice, three times a day" = une, deux, trois fois par jour.' },
  take_it_easy: { en: 'Take it easy for a few days.', fr: 'Ménagez-vous pendant quelques jours.', note: '"Take it easy" = ne pas forcer, se reposer.' },
  off_sick: { en: "I'm off sick today.", fr: 'Je suis en arrêt maladie aujourd\'hui.', note: '"Be off sick" = ne pas aller travailler parce qu\'on est malade.' },
};

const CHAPTERS = [
  {
    id: 'arrival',
    title: 'Arrivée à Heathrow',
    place: '✈️ Aéroport de Heathrow',
    intro: 'Vous venez d\'atterrir à Londres pour un nouveau travail. Première étape : le contrôle des passeports, puis le métro.',
    steps: [
      { t: 'say', who: 'officer', en: 'Good morning. Passport, please.', fr: 'Bonjour. Votre passeport, s\'il vous plaît.' },
      {
        t: 'choice', who: 'officer', en: 'What brings you to the UK?', fr: 'Qu\'est-ce qui vous amène au Royaume-Uni ?',
        options: [
          { en: "I'm here for work. I'm starting a new job.", ok: true, fb: 'Parfait : clair, naturel, et "for work" est la bonne tournure.' },
          { en: "I'm here for working.", fb: 'Après "for", on met un nom : "for work", pas "for working".' },
          { en: 'I come for to work.', fb: 'Calque du français. On dit "I\'m here for work" ou "I\'ve come to work here".' },
        ],
        learn: ['here_for_work'],
      },
      {
        t: 'listen', who: 'officer', en: 'And how long are you staying?',
        q: 'Que vous demande l\'agent ?',
        options: [
          { fr: 'Combien de temps vous restez.', ok: true },
          { fr: 'Depuis combien de temps vous êtes arrivé.' },
          { fr: 'Où vous allez loger.' },
        ],
        learn: ['how_long_staying'],
      },
      {
        t: 'speak', fr: 'Répondez : environ un an, peut-être plus.',
        model: 'About a year, maybe longer.',
        answers: ['About a year, maybe longer', 'About a year maybe more', 'Around a year, maybe longer'],
        learn: ['about_a_year'],
      },
      { t: 'say', who: 'officer', en: "Lovely. Welcome to London. Enjoy your stay.", fr: 'Très bien. Bienvenue à Londres. Bon séjour.' },
      { t: 'note', title: '💡 "Lovely"', fr: 'Les Britanniques disent "lovely" pour presque tout : "très bien", "parfait", "super". Vous l\'entendrez partout !' },
      {
        t: 'choice', who: 'agent', en: 'Morning! Can I help you?', fr: 'Bonjour ! Je peux vous aider ?',
        options: [
          { en: "Yes, please. Could you tell me how to get to King's Cross?", ok: true, fb: 'Très bien : "Could you tell me…" est poli, et "get to" est le verbe idéal pour un trajet.' },
          { en: "Yes. Where is King's Cross? Tell me.", fb: 'Compréhensible mais trop direct : on ajoute "Could you…" et "please".' },
          { en: "Yes, please. How can I go at King's Cross?", fb: 'On "goes to" un lieu, pas "at". Et "how to get to" est plus naturel.' },
        ],
        learn: ['how_to_get_to'],
      },
      {
        t: 'listen', who: 'agent', en: "Take the Piccadilly line. You need to change at Green Park, then take the Victoria line.",
        q: 'Que devez-vous faire ?',
        options: [
          { fr: 'Prendre la Piccadilly line puis changer à Green Park pour la Victoria line.', ok: true },
          { fr: 'Prendre la Victoria line jusqu\'à Green Park.' },
          { fr: 'Changer de train à King\'s Cross.' },
        ],
        learn: ['change_at'],
      },
      {
        t: 'gap', fr: 'Vous demandez comment payer. L\'agent répond :',
        before: 'You can just', after: 'in with your bank card.', answer: 'tap',
        options: ['tap', 'touch', 'knock'],
        fb: '"Tap in" = passer sa carte sans contact à l\'entrée. À la sortie, on "taps out".',
        learn: ['tap_in'],
      },
      {
        t: 'order', fr: 'Sur le quai, vous vérifiez auprès de quelqu\'un : « C\'est bien le bon quai pour Paddington ? »',
        answer: 'Is this the right platform for Paddington?',
        learn: ['right_platform'],
      },
      { t: 'say', who: 'agent', en: 'Yes, it is. And mind the gap when you get on!', fr: 'Oui. Et attention à la marche en montant !', learn: ['mind_the_gap'] },
      { t: 'note', title: '🚇 "Mind the gap"', fr: '"Mind" veut dire "faites attention à". C\'est l\'annonce la plus célèbre du métro londonien. Vous verrez aussi "Mind your head" (attention à votre tête).' },
    ],
  },
  {
    id: 'flat',
    title: 'Visite d\'appartement',
    place: '🏠 Un appartement à Camden',
    intro: 'Vous visitez un appartement avec Sarah, l\'agente immobilière. Objectif : poser les bonnes questions et négocier poliment.',
    steps: [
      { t: 'say', who: 'sarah', en: "Hi, you must be here for the viewing. Come on in! So, this is the living room.", fr: 'Bonjour, vous venez sûrement pour la visite. Entrez ! Alors, voici le salon.' },
      {
        t: 'choice', who: 'sarah', en: "The rent is fifteen hundred pounds a month.", fr: 'Le loyer est de 1 500 livres par mois.',
        options: [
          { en: 'Are bills included?', ok: true, fb: 'Exactement la bonne question. "Bills" = eau, gaz, électricité, internet.' },
          { en: 'Are the charges included?', fb: 'Faux ami : "charges" en anglais = frais facturés ou accusations. On parle de "bills".' },
          { en: 'Bills are inside?', fb: 'On dit "included" pour "compris".' },
        ],
        learn: ['bills_included'],
      },
      { t: 'say', who: 'sarah', en: "Bills aren't included, I'm afraid, but council tax is quite low here.", fr: 'Les charges ne sont pas comprises, malheureusement, mais la taxe locale est assez basse ici.' },
      { t: 'note', title: '💡 "I\'m afraid"', fr: '"I\'m afraid" n\'exprime pas la peur ici : c\'est une façon polie d\'annoncer une mauvaise nouvelle, comme "malheureusement" ou "je regrette".' },
      {
        t: 'speak', fr: 'Demandez combien coûte la caution.',
        model: 'How much is the deposit?',
        answers: ['How much is the deposit', 'How much is the deposit please', 'What is the deposit'],
        learn: ['deposit'],
      },
      {
        t: 'listen', who: 'sarah', en: "The deposit is five weeks' rent, and you'll get it back at the end of the tenancy.",
        q: 'Que dit Sarah sur la caution ?',
        options: [
          { fr: 'Elle vaut cinq semaines de loyer et sera rendue à la fin de la location.', ok: true },
          { fr: 'Elle vaut cinq mois de loyer et n\'est pas remboursée.' },
          { fr: 'Elle est à payer dans cinq semaines.' },
        ],
      },
      {
        t: 'choice', who: 'sarah', en: 'So, what do you think?', fr: 'Alors, qu\'en pensez-vous ?',
        options: [
          { en: "I really like it, but it's a bit pricey for me. Is there any chance you could knock a bit off the rent?", ok: true, fb: 'Excellent : vous restez positif ("I really like it"), et vous négociez poliment avec "Is there any chance…".' },
          { en: "It's too expensive. Lower the price.", fb: 'Beaucoup trop direct pour un anglophone : on adoucit toujours une demande.' },
          { en: 'It is very expensive, you can do less?', fb: 'La structure est française. Essayez "Could you…" ou "Is there any chance…".' },
        ],
        learn: ['bit_pricey', 'knock_off'],
      },
      { t: 'say', who: 'sarah', en: "I'll ask the landlord. He might go down to fourteen fifty.", fr: 'Je vais demander au propriétaire. Il pourrait descendre à 1 450.' },
      {
        t: 'gap', fr: 'Vous ne voulez pas décider tout de suite :',
        before: "Thanks. I'd like to think it", after: '.', answer: 'over',
        options: ['over', 'about', 'again'],
        fb: '"Think it over" = prendre le temps d\'y réfléchir. C\'est un "phrasal verb" très courant.',
        learn: ['think_it_over'],
      },
      {
        t: 'choice', who: 'sarah', en: "No problem. Is there anything you're not sure about?", fr: 'Pas de souci. Y a-t-il quelque chose qui vous fait hésiter ?',
        options: [
          { en: "Actually, I'd prefer something closer to the station.", ok: true, fb: 'Bien vu : "actually" = en fait. C\'est un faux ami très fréquent.' },
          { en: 'Currently, I prefer near the station.', fb: '"Currently" = actuellement, ce n\'est pas le sens voulu. Et il manque "something".' },
          { en: 'Actually, I prefer that it is near of the station.', fb: 'On dit "near the station" ou "close to the station", jamais "near of".' },
        ],
        learn: ['actually'],
      },
      {
        t: 'order', fr: 'Finalement, l\'appartement vous plaît. Demandez quand vous pouvez emménager.',
        answer: 'When can I move in?',
        learn: ['move_in'],
      },
      { t: 'say', who: 'sarah', en: 'From the first of next month, if that works for you.', fr: 'À partir du premier du mois prochain, si ça vous convient.' },
      {
        t: 'speak', fr: 'Dites que ça vous va.',
        model: 'Sounds good to me.',
        answers: ['Sounds good to me', 'That sounds good to me', 'Sounds good', 'That works for me'],
        learn: ['sounds_good'],
      },
    ],
  },
  {
    id: 'work',
    title: 'Premier jour au bureau',
    place: '🏢 Votre nouveau bureau à la City',
    intro: 'James, votre manager, vous présente à l\'équipe. Il faut se présenter, retenir les prénoms et comprendre votre rôle.',
    steps: [
      { t: 'say', who: 'james', en: "Welcome aboard! Let me introduce you to the team. This is Priya.", fr: 'Bienvenue dans l\'équipe ! Laissez-moi vous présenter. Voici Priya.' },
      {
        t: 'choice', who: 'priya', en: "Hi there! I'm Priya. I look after the data side of things.", fr: 'Salut ! Moi c\'est Priya. Je m\'occupe de tout ce qui touche aux données.',
        options: [
          { en: "Hi Priya, nice to meet you.", ok: true, fb: 'Parfait pour une première rencontre.' },
          { en: 'Hi Priya, nice to see you.', fb: '"Nice to see you" se dit à quelqu\'un qu\'on connaît déjà.' },
          { en: 'Enchanted.', fb: 'Traduction littérale de "enchanté" : ça sonne très bizarre en anglais.' },
        ],
        learn: ['nice_to_meet_you'],
      },
      {
        t: 'listen', who: 'priya', en: "Sorry, it's a bit noisy in here. So, what do you do exactly?",
        q: 'Que vous demande Priya ?',
        options: [
          { fr: 'Quel est votre métier exactement.', ok: true },
          { fr: 'Ce que vous êtes en train de faire.' },
          { fr: 'Ce que vous faites ce week-end.' },
        ],
        learn: ['what_do_you_do'],
      },
      {
        t: 'speak', fr: 'Répondez que vous êtes responsable du nouveau projet.',
        model: "I'm in charge of the new project.",
        answers: ["I'm in charge of the new project", 'I am in charge of the new project', "I'm responsible for the new project"],
        learn: ['in_charge_of'],
      },
      { t: 'say', who: 'mark', en: "Hiya, I'm Mark. I sit just over there.", fr: 'Salut, moi c\'est Mark. Je suis assis juste là-bas.' },
      {
        t: 'choice', who: 'mark', en: '…', fr: 'Vous n\'avez pas bien entendu son prénom.',
        options: [
          { en: "Sorry, I didn't catch your name.", ok: true, fb: 'Très naturel et poli. "Catch" = saisir à l\'oral.' },
          { en: "Sorry, I didn't understand your name.", fb: 'Compréhensible, mais on dit plutôt "I didn\'t catch".' },
          { en: 'What? Repeat.', fb: 'Beaucoup trop sec : ça peut paraître impoli.' },
        ],
        learn: ['sorry_didnt_catch'],
      },
      { t: 'say', who: 'mark', en: "No worries, it's Mark. Let me know if you need anything, yeah?", fr: 'Pas de souci, c\'est Mark. Dis-moi si tu as besoin de quoi que ce soit, d\'accord ?', learn: ['let_me_know'] },
      {
        t: 'listen', who: 'james', en: "For now you'll be on the internal team. Eventually, you'll work on the client side.",
        q: 'Que veut dire James ?',
        options: [
          { fr: 'À terme, vous travaillerez avec les clients.', ok: true },
          { fr: 'Peut-être que vous travaillerez avec les clients.' },
          { fr: 'Vous ne travaillerez jamais avec les clients.' },
        ],
        learn: ['eventually'],
      },
      { t: 'note', title: '⚠️ Faux ami', fr: '"Eventually" veut dire "finalement, à terme". Pour dire "éventuellement", on utilise "possibly" ou "maybe".' },
      {
        t: 'choice', who: 'james', en: 'So, how are you finding it so far?', fr: 'Alors, comment ça se passe pour l\'instant ?',
        options: [
          { en: "Great, thanks. I'm getting the hang of it.", ok: true, fb: 'Expression parfaite pour dire qu\'on commence à s\'habituer.' },
          { en: "I find it with difficulty.", fb: '"How are you finding it?" = comment trouves-tu ça ? Il ne s\'agit pas de chercher quelque chose.' },
          { en: "I'm habituating.", fb: 'Ce verbe n\'existe pas dans ce sens. On dit "I\'m getting used to it" ou "I\'m getting the hang of it".' },
        ],
        learn: ['getting_the_hang'],
      },
      {
        t: 'gap', fr: 'James doit partir en réunion :',
        before: "Brilliant. I've got a meeting now, so let's catch", after: 'later.', answer: 'up',
        options: ['up', 'on', 'back'],
        fb: '"Catch up" = faire le point ou prendre des nouvelles.',
        learn: ['catch_up'],
      },
    ],
  },
  {
    id: 'pub',
    title: 'Au pub avec les collègues',
    place: '🍺 The Crown, un pub près du bureau',
    intro: 'Vendredi soir, Mark et Priya vous emmènent au pub. Vocabulaire familier, humour et règles du pub au programme.',
    steps: [
      { t: 'say', who: 'mark', en: "Right, first round's on me. What are you having?", fr: 'Bon, la première tournée est pour moi. Qu\'est-ce que tu prends ?', learn: ['what_are_you_having'] },
      {
        t: 'choice', who: 'mark', en: 'What are you having?', fr: 'Qu\'est-ce que tu prends ?',
        options: [
          { en: "I'll have a pint of lager, please.", ok: true, fb: 'Parfait : "I\'ll have…" est la formule naturelle pour commander.' },
          { en: 'I want a beer.', fb: '"I want" est trop direct pour commander. Préférez "I\'ll have" ou "Could I have".' },
          { en: 'I take a beer.', fb: 'Calque de "je prends". En anglais, on dit "I\'ll have".' },
        ],
      },
      { t: 'note', title: '🍺 Les "rounds"', fr: 'Au pub, chacun paie à tour de rôle une tournée ("a round") pour tout le groupe. Ne pas payer la sienne est très mal vu !' },
      {
        t: 'listen', who: 'priya', en: "Honestly, I'm absolutely knackered. It's been a long week.",
        q: 'Comment se sent Priya ?',
        options: [
          { fr: 'Elle est épuisée.', ok: true },
          { fr: 'Elle est en colère.' },
          { fr: 'Elle est contente que la semaine commence.' },
        ],
        learn: ['knackered'],
      },
      {
        t: 'choice', who: 'priya', en: 'So how come you moved to London?', fr: 'Alors, comment ça se fait que tu sois venu à Londres ?',
        options: [
          { en: 'I got a job offer I couldn\'t turn down.', ok: true, fb: 'Très naturel. "Turn down" = refuser (une offre).' },
          { en: 'Because I came for the job.', fb: 'Correct mais un peu maladroit. "I got a job offer" sonne mieux.' },
          { en: 'How come I moved? For the work.', fb: 'Inutile de reprendre la question. Et on dit "for work", sans "the".' },
        ],
        learn: ['how_come'],
      },
      { t: 'say', who: 'mark', en: "Seriously? You left Paris for this weather? You're mad! … No, I'm just pulling your leg.", fr: 'Sérieusement ? Tu as quitté Paris pour ce temps ? T\'es fou ! … Non, je te fais marcher.', learn: ['pulling_your_leg'] },
      {
        t: 'speak', fr: 'Mark a fini son verre. C\'est votre tour : dites que c\'est votre tournée.',
        model: "It's my round.",
        answers: ["It's my round", 'It is my round', "It's my round now"],
        learn: ['my_round'],
      },
      {
        t: 'gap', fr: 'Vous proposez un autre verre à Priya :',
        before: 'Do you', after: 'another one?', answer: 'fancy',
        options: ['fancy', 'like', 'envy'],
        fb: '"Fancy" = avoir envie de. "Do you like" demanderait si elle aime ça en général.',
        learn: ['fancy'],
      },
      { t: 'say', who: 'priya', en: 'Go on then. Cheers!', fr: 'Allez, d\'accord. Merci !', learn: ['cheers'] },
      {
        t: 'order', fr: 'Il est tard. Annoncez que vous allez rentrer.',
        answer: "I think I'll call it a night.",
        learn: ['call_it_a_night'],
      },
    ],
  },
  {
    id: 'bank',
    title: 'Appel à la banque',
    place: '📞 Au téléphone avec la banque',
    intro: 'Votre carte est bloquée. Au téléphone, impossible de lire sur les lèvres : il faut savoir demander de répéter et épeler.',
    steps: [
      { t: 'say', who: 'bank', en: "Good afternoon, you're through to Daniel. How can I help?", fr: 'Bonjour, vous êtes en ligne avec Daniel. Que puis-je faire pour vous ?' },
      {
        t: 'choice', who: 'bank', en: 'How can I help?', fr: 'Que puis-je faire pour vous ?',
        options: [
          { en: "Hi, I'm calling about my card. It's been blocked.", ok: true, fb: 'Idéal : vous annoncez le sujet, puis le problème.' },
          { en: 'Hello, I call for my card is blocked.', fb: 'On dit "I\'m calling about", et il manque le verbe : "my card has been blocked".' },
          { en: 'My card. Blocked. Help.', fb: 'Compréhensible, mais beaucoup trop sec.' },
        ],
        learn: ['calling_about', 'blocked'],
      },
      {
        t: 'listen', who: 'bank', en: "Right, I'll need to confirm a few details first. Could I take your full name and date of birth?",
        q: 'Que demande Daniel ?',
        options: [
          { fr: 'Votre nom complet et votre date de naissance.', ok: true },
          { fr: 'Votre numéro de carte et votre code.' },
          { fr: 'Votre adresse et votre numéro de téléphone.' },
        ],
      },
      {
        t: 'speak', fr: 'La ligne est mauvaise. Demandez-lui poliment de répéter.',
        model: 'Could you repeat that, please?',
        answers: ['Could you repeat that please', 'Could you repeat that', 'Can you repeat that please', 'Sorry could you repeat that'],
        learn: ['could_you_repeat'],
      },
      {
        t: 'choice', who: 'bank', en: 'And your postcode is…?', fr: 'Et votre code postal est… ?',
        options: [
          { en: "It's N W one, eight Q P. Sorry, could you spell your surname for me as well?", ok: true, fb: 'Bien : on épelle un code postal lettre par lettre. Et demander d\'épeler un nom est tout à fait normal.' },
          { en: 'Postcode is NW18QP, you spell your name?', fb: 'Il manque une formule polie : "Could you spell…?".' },
          { en: 'I don\'t know the word.', fb: 'Le code postal se dit "postcode" au Royaume-Uni et "zip code" aux États-Unis.' },
        ],
        learn: ['spell_that'],
      },
      {
        t: 'listen', who: 'bank', en: "Thank you. I'm going to put you on hold for a moment while I check your account.",
        q: 'Que va-t-il se passer ?',
        options: [
          { fr: 'Vous allez être mis en attente un instant.', ok: true },
          { fr: 'Il va raccrocher et vous rappeler.' },
          { fr: 'Il va fermer votre compte.' },
        ],
        learn: ['put_on_hold'],
      },
      { t: 'say', who: 'bank', en: "Thanks for holding. It looks like a security check was triggered by a payment abroad. Don't worry, we'll sort it out for you.", fr: 'Merci d\'avoir patienté. Un contrôle de sécurité s\'est déclenché à cause d\'un paiement à l\'étranger. Ne vous inquiétez pas, on va vous arranger ça.', learn: ['sort_it_out'] },
      {
        t: 'gap', fr: 'Daniel promet de vérifier un autre problème :',
        before: "There's also a fee on your account. I'll look", after: 'it.', answer: 'into',
        options: ['into', 'at', 'for'],
        fb: '"Look into something" = examiner un problème. "Look at" = regarder, "look for" = chercher.',
        learn: ['look_into_it'],
      },
      {
        t: 'choice', who: 'bank', en: 'Is there anything else I can help you with today?', fr: 'Puis-je vous aider pour autre chose aujourd\'hui ?',
        options: [
          { en: "No, that's all, thanks. You've been really helpful.", ok: true, fb: 'Parfait pour terminer un appel poliment.' },
          { en: 'No. Bye.', fb: 'Trop abrupt. Ajoutez au moins "thanks".' },
          { en: 'Nothing more, it is finished.', fb: 'On dit plutôt "That\'s all, thanks".' },
        ],
        learn: ['anything_else'],
      },
    ],
  },
  {
    id: 'pharmacy',
    title: 'Chez le pharmacien',
    place: '💊 Une pharmacie à Camden',
    intro: 'Vous êtes tombé malade. Il faut décrire vos symptômes, parler d\'allergie et comprendre la posologie.',
    steps: [
      {
        t: 'speak', fr: 'Vous appelez votre manager : dites que vous êtes en arrêt maladie aujourd\'hui.',
        model: "I'm off sick today.",
        answers: ["I'm off sick today", 'I am off sick today', "I'm off sick"],
        learn: ['off_sick'],
      },
      { t: 'say', who: 'pharmacist', en: "Hello there, what can I do for you?", fr: 'Bonjour, que puis-je faire pour vous ?' },
      {
        t: 'choice', who: 'pharmacist', en: 'What can I do for you?', fr: 'Que puis-je faire pour vous ?',
        options: [
          { en: "I'm feeling a bit under the weather. I've got a sore throat.", ok: true, fb: 'Très naturel : "under the weather" et "a sore throat" sont les expressions que les Britanniques utilisent.' },
          { en: 'I have bad at the throat.', fb: 'Calque de "j\'ai mal à la gorge". On dit "I\'ve got a sore throat".' },
          { en: 'I am sick of my throat.', fb: '"I\'m sick of" veut dire "j\'en ai marre de" !' },
        ],
        learn: ['under_the_weather', 'sore_throat'],
      },
      {
        t: 'listen', who: 'pharmacist', en: 'Oh dear. How long have you had it?',
        q: 'Que vous demande la pharmacienne ?',
        options: [
          { fr: 'Depuis combien de temps vous l\'avez.', ok: true },
          { fr: 'Combien de fois vous l\'avez eu.' },
          { fr: 'Si vous l\'avez déjà eu avant.' },
        ],
      },
      {
        t: 'gap', fr: 'Vous répondez : depuis lundi.',
        before: "I've had it", after: 'Monday.', answer: 'since',
        options: ['since', 'for', 'from'],
        fb: '"Since" + point de départ (since Monday). "For" + durée (for three days).',
        learn: ['since_monday'],
      },
      { t: 'note', title: '⏳ Since ou for ?', fr: 'Pour dire "depuis", l\'anglais utilise le present perfect : "I\'ve had it since Monday" (depuis lundi) ou "for three days" (depuis trois jours). "I have it since Monday" est une erreur typique des francophones.' },
      {
        t: 'choice', who: 'pharmacist', en: 'Are you allergic to anything?', fr: 'Êtes-vous allergique à quelque chose ?',
        options: [
          { en: "Yes, I'm allergic to penicillin.", ok: true, fb: '"Allergic to" : c\'est la seule préposition possible.' },
          { en: "Yes, I'm allergic of penicillin.", fb: 'On dit toujours "allergic to".' },
          { en: 'Yes, I have an allergy at penicillin.', fb: 'On dit "an allergy to", ou plus simplement "I\'m allergic to".' },
        ],
        learn: ['allergic'],
      },
      {
        t: 'speak', fr: 'Demandez s\'il faut une ordonnance pour ce médicament.',
        model: 'Do I need a prescription for this?',
        answers: ['Do I need a prescription for this', 'Do I need a prescription', 'Is a prescription needed for this'],
        learn: ['prescription'],
      },
      {
        t: 'listen', who: 'pharmacist', en: "No, you can buy these over the counter. Take two tablets twice a day, after food.",
        q: 'Comment prendre le médicament ?',
        options: [
          { fr: 'Deux comprimés, deux fois par jour, après le repas.', ok: true },
          { fr: 'Un comprimé, deux fois par jour, avant le repas.' },
          { fr: 'Deux comprimés par jour, à jeun.' },
        ],
        learn: ['twice_a_day'],
      },
      { t: 'say', who: 'pharmacist', en: 'And take it easy for a few days. Plenty of rest and fluids.', fr: 'Et ménagez-vous pendant quelques jours. Beaucoup de repos, et buvez beaucoup.', learn: ['take_it_easy'] },
      { t: 'note', title: '🎉 Fin de la première saison', fr: 'Bravo ! Vous avez survécu à votre premier mois à Londres. Le défi du jour va vous faire réviser toutes ces expressions pour qu\'elles restent gravées.' },
    ],
  },
];

if (typeof module !== 'undefined') module.exports = { CHARACTERS, CARDS, CHAPTERS };
