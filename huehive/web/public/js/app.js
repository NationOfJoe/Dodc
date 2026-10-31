/*
 * Hue Hive: screens and game flow for offline (pass-and-play / vs computer) and online play.
 */
(function () {
  'use strict';
  const E = window.HHEngine, AI = window.HHAI, St = window.HHStore, B = window.HHBoard;
  const $app = document.getElementById('app');
  const $modal = document.getElementById('modal');
  const $toast = document.getElementById('toast');
  St.data.onlineNames = St.data.onlineNames || {};

  const esc = (s) => String(s == null ? '' : s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const byId = (id) => document.getElementById(id);
  const LEVELS = { easy: 'Easy', medium: 'Medium', hard: 'Hard' };
  const BOT_NAMES = ['Waggle', 'Nectar', 'Drone', 'Pollen'];
  const fmtDate = (t) => new Date(t).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' });

  // ------------------------------------------------------------------ UI plumbing
  let screen = 'home', params = {};
  const SCREENS = {}, AFTER = {}, ACT = {};

  function go(name, p) {
    if (screen === 'game' && name !== 'game' && G && G.aiTimer) { clearTimeout(G.aiTimer); G.aiTimer = null; }
    // Leaving the table: stop listening to the room (you keep your seat and can rejoin).
    if (G && G.mode === 'online' && name !== 'game' && name !== 'lobby') disconnect();
    screen = name; params = p || {};
    closeModal();
    render();
    window.scrollTo(0, 0);
  }
  function render() {
    $app.className = 'screen-' + screen;
    $app.innerHTML = SCREENS[screen](params);
    if (AFTER[screen]) AFTER[screen](params);
  }

  document.addEventListener('click', (e) => {
    const el = e.target.closest('[data-act]');
    if (!el || el.disabled) return;
    const fn = ACT[el.dataset.act];
    if (fn) { e.preventDefault(); fn(el, e); }
  });
  document.addEventListener('submit', (e) => e.preventDefault());

  let toastTimer = null;
  function toast(msg) {
    $toast.textContent = msg;
    $toast.classList.add('show');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => $toast.classList.remove('show'), 3200);
  }

  let modalOnClose = null;
  function modal(html, onClose) {
    $modal.innerHTML = '<div class="modal-card">' + html + '</div>';
    $modal.hidden = false;
    modalOnClose = onClose || null;
  }
  function closeModal() {
    if ($modal.hidden) return;
    $modal.hidden = true;
    $modal.innerHTML = '';
    const f = modalOnClose; modalOnClose = null;
    if (f) f();
  }
  ACT.closeModal = () => closeModal();

  /** A confirm dialog that works inside the Android WebView. */
  function ask(title, text, yes, no) {
    return new Promise((resolve) => {
      ACT.askYes = () => { modalOnClose = null; closeModal(); resolve(true); };
      ACT.askNo = () => { closeModal(); };
      modal('<h2>' + esc(title) + '</h2>' + (text ? '<p>' + esc(text) + '</p>' : '') +
        '<div class="btn-row"><button class="btn" data-act="askNo">' + esc(no || 'Cancel') + '</button>' +
        '<button class="btn primary" data-act="askYes">' + esc(yes || 'OK') + '</button></div>', () => resolve(false));
    });
  }

  function topbar(title, backAct, extra) {
    return '<header class="topbar"><button class="icon-btn" data-act="' + (backAct || 'home') + '" aria-label="Back">‹</button>' +
      '<div class="tb-title">' + title + '</div>' + (extra || '<span class="icon-btn ghost"></span>') + '</header>';
  }
  ACT.home = () => go('home');

  function logoSvg() {
    const t = [[{ c: 0, g: 4 }, -1, 0], [{ c: 3, g: 0 }, 1, 0], [{ c: 1, g: 1 }, 0, -1.5]];
    return '<svg class="logo" viewBox="-2.2 -2.6 4.4 4.1">' + t.map(([tile, x, y]) =>
      '<g transform="translate(' + (x * 0.92) + ',' + y + ')">' + B.tileInner(tile) + '</g>').join('') + '</svg>';
  }

  // ------------------------------------------------------------------ Home
  SCREENS.home = () => {
    const off = St.data.offline, room = St.data.room;
    let resume = '';
    if (off && off.state && !off.state.over)
      resume += '<button class="btn big accent" data-act="resumeOffline">Continue game<small>' +
        esc(off.state.players.map((p) => p.name).join(' · ')) + '</small></button>';
    if (room)
      resume += '<div class="split"><button class="btn big accent" data-act="resumeOnline">Rejoin online room<small>' + esc(room.code) +
        '</small></button><button class="btn" data-act="forgetRoom" title="Leave room">✕</button></div>';
    return '<div class="home">' + logoSvg() + '<h1>Hue Hive</h1><p class="tagline">Match colours and glyphs on the honeycomb</p>' +
      '<div class="menu">' + resume +
      '<button class="btn big primary" data-act="goOffline">Play on this device<small>vs the computer, or pass and play</small></button>' +
      '<button class="btn big" data-act="goOnline">Play online<small>2–4 friends, each on their own device</small></button>' +
      '<button class="btn big" data-act="goGroups">Groups &amp; scores<small>Scoreboards for every session</small></button>' +
      '<div class="btn-row"><button class="btn" data-act="goRules">How to play</button><button class="btn" data-act="goSettings">Settings</button></div>' +
      '</div></div>';
  };
  ACT.goOffline = () => go('offline');
  ACT.goOnline = () => go('online', { tab: 'create' });
  ACT.goGroups = () => go('groups');
  ACT.goRules = () => go('rules');
  ACT.goSettings = () => go('settings');
  ACT.forgetRoom = async () => {
    if (!(await ask('Leave this room?', 'You will not be able to rejoin unless someone shares the code again.', 'Leave'))) return;
    const r = St.data.room;
    St.data.room = null; St.save();
    if (r) St.api('POST', '/api/rooms/' + r.code + '/leave', { token: r.token }).catch(() => {});
    render();
  };

  // ------------------------------------------------------------------ Group pickers (shared)
  function groupOptions(selected) {
    let o = '<option value="">No group (just play)</option>';
    if (St.data.groups.length) {
      o += '<optgroup label="On this device">' + St.data.groups.map((g) =>
        '<option value="local:' + g.id + '"' + (selected === 'local:' + g.id ? ' selected' : '') + '>' + esc(g.name) + '</option>').join('') + '</optgroup>';
    }
    if (St.data.online.length) {
      o += '<optgroup label="Online groups">' + St.data.online.map((c) =>
        '<option value="online:' + c + '"' + (selected === 'online:' + c ? ' selected' : '') + '>' + esc(St.data.onlineNames[c] || c) + ' (' + c + ')</option>').join('') + '</optgroup>';
    }
    return o;
  }
  async function fetchOnlineGroup(code) {
    const g = await St.api('GET', '/api/groups/' + code);
    St.data.onlineNames[code] = g.name; St.save();
    return g;
  }
  /** Turn a picker value + session choice into {type, id, sessionId}. */
  async function resolveGroupRef(value, newSession) {
    if (!value) return null;
    const [type, id] = value.split(':');
    if (type === 'local') {
      const g = St.localGroup(id);
      if (!g) return null;
      const s = newSession || !g.sessions.length ? St.newSession(g) : g.sessions[g.sessions.length - 1];
      St.save();
      return { type, id, sessionId: s.id };
    }
    let sid;
    if (newSession) sid = (await St.api('POST', '/api/groups/' + id + '/sessions', {})).id;
    else {
      const g = await fetchOnlineGroup(id);
      sid = g.sessions.length ? g.sessions[g.sessions.length - 1].id : (await St.api('POST', '/api/groups/' + id + '/sessions', {})).id;
    }
    return { type, id, sessionId: sid };
  }
  async function fetchSession(ref) {
    if (!ref) return null;
    if (ref.type === 'local') {
      const g = St.localGroup(ref.id);
      const s = g && g.sessions.find((x) => x.id === ref.sessionId);
      return s ? { group: g.name, session: s } : null;
    }
    const g = await fetchOnlineGroup(ref.id);
    const s = g.sessions.find((x) => x.id === ref.sessionId);
    return s ? { group: g.name, session: s } : null;
  }
  function groupPlayers(value) {
    if (!value) return [];
    const [type, id] = value.split(':');
    if (type === 'local') { const g = St.localGroup(id); return g ? g.players : []; }
    return (St.data.onlinePlayers && St.data.onlinePlayers[id]) || [];
  }

  function standingsTable(rows) {
    if (!rows || !rows.length) return '<p class="muted">No games finished yet in this session.</p>';
    return '<table class="standings"><thead><tr><th>#</th><th>Player</th><th title="Wins">Wins</th><th title="Total points">Points</th><th title="Games played">Games</th><th title="Best score">Best</th></tr></thead><tbody>' +
      rows.map((r, i) => '<tr' + (i === 0 ? ' class="lead"' : '') + '><td>' + (i + 1) + '</td><td>' + esc(r.name) + '</td><td>' + r.wins + '</td><td>' + r.points +
        '</td><td>' + r.played + '</td><td>' + r.best + '</td></tr>').join('') + '</tbody></table>';
  }

  // ------------------------------------------------------------------ Offline setup
  let setup = null;
  function defaultSetup() {
    return St.data.lastSetup || {
      seats: [{ kind: 'human', name: St.data.settings.name || 'You' }, { kind: 'medium' }],
      group: '', newSession: false
    };
  }
  SCREENS.offline = () => {
    setup = setup || JSON.parse(JSON.stringify(defaultSetup()));
    if (setup.group && !groupExists(setup.group)) setup.group = '';
    const players = groupPlayers(setup.group);
    const seats = setup.seats.map((s, i) => {
      const kindSel = '<select class="seat-kind" data-i="' + i + '">' +
        ['human', 'easy', 'medium', 'hard'].map((k) => '<option value="' + k + '"' + (s.kind === k ? ' selected' : '') + '>' +
          (k === 'human' ? 'Person' : 'Computer · ' + LEVELS[k]) + '</option>').join('') + '</select>';
      const name = s.kind === 'human'
        ? '<input class="seat-name" data-i="' + i + '" list="group-players" maxlength="24" placeholder="Name" value="' + esc(s.name || '') + '">'
        : '<span class="seat-bot">' + esc(botName(i, s.kind)) + '</span>';
      const del = setup.seats.length > 2 ? '<button class="icon-btn small" data-act="delSeat" data-i="' + i + '" aria-label="Remove">✕</button>' : '';
      return '<div class="seat"><span class="seat-no">' + (i + 1) + '</span>' + kindSel + name + del + '</div>';
    }).join('');
    return topbar('Play on this device') + '<div class="page">' +
      '<section class="card"><h3>Players</h3>' + seats +
      (setup.seats.length < 4 ? '<button class="btn small" data-act="addSeat">+ Add player</button>' : '') +
      '<datalist id="group-players">' + players.map((p) => '<option value="' + esc(p) + '">').join('') + '</datalist>' +
      '<p class="muted small">With two or more people, pass the device around; your tiles stay hidden between turns.</p></section>' +
      '<section class="card"><h3>Keep score</h3><label>Group<select id="setup-group">' + groupOptions(setup.group) + '</select></label>' +
      (setup.group ? '<div class="radio-row"><label><input type="radio" name="sess" value="cont"' + (!setup.newSession ? ' checked' : '') + '> Continue latest session</label>' +
        '<label><input type="radio" name="sess" value="new"' + (setup.newSession ? ' checked' : '') + '> Start a new session</label></div>' : '') +
      '<button class="btn small" data-act="quickGroup">+ New group</button></section>' +
      '<button class="btn big primary" data-act="startOffline">Start game</button></div>';
  };
  function groupExists(v) {
    const [t, id] = v.split(':');
    return t === 'local' ? !!St.localGroup(id) : St.data.online.indexOf(id) >= 0;
  }
  function botName(i, level) { return BOT_NAMES[i % 4] + ' (' + LEVELS[level] + ')'; }
  function readSetup() {
    document.querySelectorAll('.seat-kind').forEach((el) => { setup.seats[+el.dataset.i].kind = el.value; });
    document.querySelectorAll('.seat-name').forEach((el) => { setup.seats[+el.dataset.i].name = el.value.trim(); });
    const g = byId('setup-group');
    if (g) setup.group = g.value;
    const ns = document.querySelector('input[name="sess"]:checked');
    setup.newSession = !!(ns && ns.value === 'new');
  }
  AFTER.offline = () => {
    $app.querySelectorAll('.seat-kind, #setup-group').forEach((el) => el.addEventListener('change', () => {
      readSetup();
      if (el.id === 'setup-group' && el.value.startsWith('online:')) {
        const code = el.value.slice(7);
        fetchOnlineGroup(code).then((g) => {
          St.data.onlinePlayers = St.data.onlinePlayers || {};
          St.data.onlinePlayers[code] = g.players; St.save();
          if (screen === 'offline') render();
        }).catch(() => {});
      }
      render();
    }));
  };
  ACT.addSeat = () => { readSetup(); setup.seats.push({ kind: 'medium' }); render(); };
  ACT.delSeat = (el) => { readSetup(); setup.seats.splice(+el.dataset.i, 1); render(); };
  ACT.quickGroup = () => { readSetup(); newGroupDialog((ref) => { setup.group = ref; render(); }); };
  ACT.startOffline = async (el) => {
    readSetup();
    const seats = setup.seats.map((s, i) => s.kind === 'human'
      ? { name: s.name, kind: 'human' } : { name: botName(i, s.kind), kind: 'ai', level: s.kind });
    if (seats.some((s) => !s.name)) return toast('Give every person a name');
    const names = seats.map((s) => s.name.toLowerCase());
    if (new Set(names).size !== names.length) return toast('Each player needs a different name');
    const firstHuman = seats.find((s) => s.kind === 'human');
    if (firstHuman && !St.data.settings.name) St.data.settings.name = firstHuman.name;
    St.data.lastSetup = JSON.parse(JSON.stringify(setup));
    St.save();
    el.disabled = true;
    let ref = null;
    try { ref = await resolveGroupRef(setup.group, setup.newSession); }
    catch (e) { el.disabled = false; return toast(e.message); }
    // Continue the session next time, now that it exists.
    St.data.lastSetup.newSession = false; St.save();
    setup = null;
    startOffline(seats, ref, null);
  };

  // ------------------------------------------------------------------ Game state (shared)
  let G = null;
  function newG(mode) {
    return { mode, sel: null, tentative: [], swap: false, swapSel: new Set(), viewSeat: -1, cover: false, busy: false, board: null, resultsFor: null };
  }

  function startOffline(seats, ref, starter) {
    const state = E.newGame(seats, { starter: starter == null ? undefined : starter });
    G = newG('offline');
    Object.assign(G, { state, seats, ref, starter: state.turn });
    const humans = humanSeats();
    G.viewSeat = humans.length ? (humans.indexOf(state.turn) >= 0 ? state.turn : humans[0]) : -1;
    G.cover = humans.length > 1;
    persistOffline();
    go('game');
    offlineStep();
  }
  ACT.resumeOffline = () => {
    const o = St.data.offline;
    G = newG('offline');
    Object.assign(G, { state: o.state, seats: o.seats, ref: o.ref, starter: o.starter });
    const humans = humanSeats();
    G.viewSeat = humans.length ? (humans.indexOf(o.state.turn) >= 0 ? o.state.turn : humans[0]) : -1;
    G.cover = humans.length > 1;
    go('game');
    offlineStep();
  };
  function persistOffline() {
    St.data.offline = { state: G.state, seats: G.seats, ref: G.ref, starter: G.starter };
    St.save();
  }
  function humanSeats() {
    return G.state.players.map((p, i) => (p.kind === 'human' ? i : -1)).filter((i) => i >= 0);
  }

  function offlineStep() {
    const s = G.state;
    if (s.over) { finishOffline(); refreshGame(); return; }
    const cur = s.players[s.turn];
    if (cur.kind === 'ai') {
      G.thinking = true;
      refreshGame();
      clearTimeout(G.aiTimer);
      const g = G;
      G.aiTimer = setTimeout(() => {
        if (G !== g || screen !== 'game') return;
        const res = E.act(s, s.turn, AI.chooseAction(s, cur.level));
        if (!res.ok) E.pass(s, s.turn); // should never happen; keeps the game moving
        G.thinking = false;
        persistOffline();
        offlineStep();
      }, 800);
      return;
    }
    G.thinking = false;
    if (G.viewSeat !== s.turn) {
      G.viewSeat = s.turn;
      G.cover = humanSeats().length > 1;
      clearMove();
    }
    refreshGame();
  }

  function finishOffline() {
    if (G.recorded) return;
    G.recorded = true;
    St.data.offline = null; St.save();
    const rec = E.gameRecord(G.state);
    G.saving = St.recordGame(G.ref, rec).catch((e) => toast('Could not save the score: ' + e.message));
    setTimeout(showResults, 600);
  }

  // ------------------------------------------------------------------ Game screen
  function curView() {
    if (!G) return null;
    if (G.mode === 'offline') return E.viewFor(G.state, G.viewSeat);
    return G.room && G.room.game;
  }
  function handHidden(v) {
    if (G.mode !== 'offline') return false;
    if (G.cover) return true;
    // In pass-and-play, hide the last player's tiles while the computer moves.
    return humanSeats().length > 1 && G.state.players[v.turn].kind !== 'human' && !v.over;
  }
  function myTurn(v) {
    return v && !v.over && v.seat >= 0 && v.turn === v.seat && !G.cover && !G.thinking && !G.busy;
  }
  function clearMove() { G.sel = null; G.tentative = []; G.swap = false; G.swapSel = new Set(); }

  SCREENS.game = () => '<div class="game">' +
    '<header class="topbar"><button class="icon-btn" data-act="gameBack" aria-label="Back">‹</button>' +
    '<div class="tb-title" id="g-title"></div><div class="bag" id="g-bag"></div>' +
    '<button class="icon-btn" data-act="fit" aria-label="Fit board" title="Fit board">⤢</button></header>' +
    '<div class="players" id="g-players"></div>' +
    '<div class="board" id="g-board"></div>' +
    '<div class="status" id="g-status"></div>' +
    '<div class="hand" id="g-hand"></div>' +
    '<div class="actions" id="g-actions"></div>' +
    '<div class="cover" id="g-cover" hidden></div></div>';
  AFTER.game = () => {
    G.board = new B.Board(byId('g-board'), { onCell });
    refreshGame();
  };

  function tentativeTiles(v) {
    return G.tentative.map((t) => ({ q: t.q, r: t.r, id: t.id, tile: v.hand.find((h) => h.id === t.id) }));
  }
  function sanitizeTentative(v) {
    G.tentative = G.tentative.filter((t) => v.hand.some((h) => h.id === t.id) && !v.board[E.key(t.q, t.r)]);
    if (G.sel != null && !v.hand.some((h) => h.id === G.sel)) G.sel = null;
    G.swapSel = new Set([...G.swapSel].filter((id) => v.hand.some((h) => h.id === id)));
  }
  function targetsFor(v, tile) {
    const tt = tentativeTiles(v);
    const merged = Object.assign({}, v.board);
    tt.forEach((t) => { merged[E.key(t.q, t.r)] = t.tile; });
    return E.anchors(merged).filter((c) => E.evaluate(v.board, tt.concat([{ q: c.q, r: c.r, tile }])).ok);
  }

  function refreshGame() {
    if (screen !== 'game' || !G) return;
    const v = curView();
    if (!v) return;
    sanitizeTentative(v);
    const mine = myTurn(v);
    const tt = tentativeTiles(v);
    const preview = tt.length ? E.evaluate(v.board, tt) : null;
    const selTile = G.sel != null && v.hand.find((h) => h.id === G.sel);
    const targets = mine && selTile && !G.swap ? targetsFor(v, selTile) : [];
    G.board.set({
      board: v.board, tentative: tt, targets, invalid: preview && !preview.ok,
      last: v.last && v.last.type === 'play' ? v.last.cells : []
    });

    byId('g-title').textContent = G.mode === 'online' ? 'Room ' + G.room.code : 'Hue Hive';
    byId('g-bag').innerHTML = '<span title="Tiles left in the bag">' + v.bagCount + ' in bag</span>';

    byId('g-players').innerHTML = v.players.map((p, i) => {
      const online = G.mode === 'online' && G.room.seats[i] ? (G.room.seats[i].online ? ' on' : ' off') : '';
      return '<div class="pchip' + (i === v.turn && !v.over ? ' turn' : '') + (v.over && v.winners.indexOf(i) >= 0 ? ' won' : '') + '">' +
        (online ? '<i class="dot' + online + '"></i>' : '') +
        '<span class="pname">' + esc(p.name) + (i === v.seat && G.mode === 'online' ? ' (you)' : '') + '</span>' +
        '<span class="pscore">' + p.score + '</span><span class="pcount" title="Tiles in hand">' + p.handCount + '</span></div>';
    }).join('');

    // Status line
    let status = '';
    const cur = v.players[v.turn];
    if (v.over) {
      status = '<b>Game over.</b> ' + esc(v.winners.map((i) => v.players[i].name).join(' & ')) + (v.winners.length > 1 ? ' tie!' : ' wins!');
    } else if (preview) {
      if (preview.ok) {
        const combs = preview.runs.filter((r) => r.comb).length;
        status = '<b class="good">This scores ' + preview.score + '</b>' + (combs ? ' · Full comb! +' + E.COMB_BONUS : '');
      } else status = '<b class="bad">' + esc(preview.error) + '</b>';
    } else if (G.swap) {
      status = 'Tap the tiles to throw back, then swap.';
    } else if (mine) {
      status = selTile ? 'Tap a glowing space to place the tile.' : '<b>Your turn.</b> Pick a tile from your hand.';
    } else if (G.thinking) {
      status = esc(cur.name) + ' is thinking…';
    } else if (G.mode === 'online' && v.turn !== v.seat) {
      status = 'Waiting for ' + esc(cur.name) + '…';
    }
    const last = v.log.length && !v.over ? v.log[v.log.length - 1] : '';
    byId('g-status').innerHTML = '<div>' + status + '</div>' + (last ? '<div class="log">' + esc(last) + '</div>' : '');

    // Hand
    const hidden = handHidden(v);
    const used = new Set(G.tentative.map((t) => t.id));
    if (v.seat < 0) byId('g-hand').innerHTML = '';
    else if (hidden) byId('g-hand').innerHTML = v.hand.map(() => '<div class="htile back"></div>').join('');
    else byId('g-hand').innerHTML = v.hand.filter((t) => !used.has(t.id)).map((t) =>
      '<button class="htile' + (G.sel === t.id ? ' sel' : '') + (G.swapSel.has(t.id) ? ' marked' : '') + '" data-act="handTile" data-id="' + t.id + '"' +
      (mine ? '' : ' disabled') + '>' + B.tileSvg(t) + '</button>').join('');

    // Actions
    let acts = '';
    if (v.over) {
      acts = '<button class="btn primary" data-act="showResults">Results</button>';
    } else if (mine && G.swap) {
      acts = '<button class="btn" data-act="cancelSwap">Cancel</button>' +
        '<button class="btn primary" data-act="doSwap"' + (G.swapSel.size ? '' : ' disabled') + '>Swap ' + (G.swapSel.size || '') + '</button>';
    } else if (mine && G.tentative.length) {
      acts = '<button class="btn" data-act="recall">Take back</button>' +
        '<button class="btn primary" data-act="doPlay"' + (preview && preview.ok ? '' : ' disabled') + '>Play' + (preview && preview.ok ? ' +' + preview.score : '') + '</button>';
    } else if (mine) {
      acts = '<button class="btn" data-act="hint">Hint</button>' +
        '<button class="btn" data-act="startSwap"' + (v.bagCount ? '' : ' disabled') + '>Swap</button>' +
        '<button class="btn" data-act="doPass">Pass</button>';
    }
    byId('g-actions').innerHTML = acts;

    // Pass-and-play cover
    const cover = byId('g-cover');
    if (G.mode === 'offline' && G.cover && !v.over && cur.kind === 'human') {
      cover.hidden = false;
      cover.innerHTML = '<div class="cover-card"><p>Pass the device to</p><h2>' + esc(cur.name) + '</h2>' +
        '<button class="btn big primary" data-act="uncover">Show my tiles</button>' +
        '<p><button class="btn small" data-act="gameBack">Leave game</button></p></div>';
    } else cover.hidden = true;
  }

  function onCell(q, r) {
    const v = curView();
    if (!myTurn(v) || G.swap) return;
    const i = G.tentative.findIndex((t) => t.q === q && t.r === r);
    if (i >= 0) { G.sel = G.tentative[i].id; G.tentative.splice(i, 1); refreshGame(); return; }
    if (G.sel == null || v.board[E.key(q, r)]) return;
    const tile = v.hand.find((h) => h.id === G.sel);
    if (!tile || !targetsFor(v, tile).some((c) => c.q === q && c.r === r)) return;
    G.tentative.push({ q, r, id: G.sel });
    G.sel = null;
    // Handy: if exactly one tile is left that fits, keep going with nothing selected.
    refreshGame();
  }

  ACT.handTile = (el) => {
    const id = +el.dataset.id;
    if (G.swap) { if (G.swapSel.has(id)) G.swapSel.delete(id); else G.swapSel.add(id); }
    else G.sel = G.sel === id ? null : id;
    refreshGame();
  };
  ACT.uncover = () => { G.cover = false; refreshGame(); };
  ACT.fit = () => { if (G && G.board) G.board.resetView(); };
  ACT.recall = () => { clearMove(); refreshGame(); };
  ACT.startSwap = () => { clearMove(); G.swap = true; refreshGame(); };
  ACT.cancelSwap = () => { clearMove(); refreshGame(); };
  ACT.hint = () => {
    const v = curView();
    const moves = E.generateMoves(v.board, v.hand);
    if (!moves.length) return toast(v.bagCount ? 'No tile fits anywhere. Swap some tiles.' : 'No tile fits anywhere. You will have to pass.');
    moves.sort((a, b) => b.score - a.score);
    const m = moves[0];
    clearMove();
    G.tentative = m.placements.map((p) => ({ q: p.q, r: p.r, id: p.tile.id }));
    refreshGame();
  };
  ACT.doPlay = () => submit({ type: 'play', placements: G.tentative.map((t) => ({ q: t.q, r: t.r, id: t.id })) });
  ACT.doSwap = () => submit({ type: 'swap', ids: [...G.swapSel] });
  ACT.doPass = async () => {
    if (await ask('Pass this turn?', 'You will not place or swap any tiles.', 'Pass')) submit({ type: 'pass' });
  };

  function submit(action) {
    if (G.mode === 'offline') {
      const res = E.act(G.state, G.state.turn, action);
      if (!res.ok) return toast(res.error);
      clearMove();
      persistOffline();
      offlineStep();
      return;
    }
    G.busy = true;
    refreshGame();
    St.api('POST', '/api/rooms/' + G.code + '/action', { token: G.token, action })
      .then(() => clearMove())
      .catch((e) => toast(e.message))
      .finally(() => { G.busy = false; refreshGame(); });
  }

  ACT.gameBack = async () => {
    if (G.mode === 'offline') {
      if (G.state.over || await ask('Leave this game?', 'It is saved. You can continue it from the home screen.', 'Leave')) go('home');
    } else if (await ask('Leave the table?', 'You keep your seat and can rejoin from the home screen.', 'Leave')) go('home');
  };

  // ------------------------------------------------------------------ Results
  ACT.showResults = () => showResults();
  async function showResults() {
    const v = curView();
    if (!v || !v.over) return;
    const order = v.players.map((p, i) => ({ ...p, i })).sort((a, b) => b.score - a.score);
    const title = v.winners.length > 1 ? 'A tie!' : esc(v.players[v.winners[0]].name) + ' wins!';
    const again = G.mode === 'offline' ? '<button class="btn primary" data-act="playAgain">Play again</button>'
      : '<button class="btn primary" data-act="rematch">Rematch</button>';
    modal('<h2>' + title + '</h2><table class="final">' + order.map((p) =>
      '<tr' + (v.winners.indexOf(p.i) >= 0 ? ' class="lead"' : '') + '><td>' + esc(p.name) + '</td><td>' + p.score + '</td></tr>').join('') + '</table>' +
      '<div id="res-session"><p class="muted">Loading session scores…</p></div>' +
      '<div class="btn-row"><button class="btn" data-act="closeModal">Board</button><button class="btn" data-act="home">Home</button>' + again + '</div>');
    let html = '';
    try {
      if (G.mode === 'offline') {
        if (G.saving) await G.saving;
        const s = await fetchSession(G.ref);
        html = s ? '<h3>' + esc(s.group) + ' · ' + esc(s.session.name) + '</h3>' + standingsTable(E.standings(s.session))
          : '<p class="muted small">Pick a group before the game to keep a running score.</p>';
      } else {
        const r = G.room;
        html = r.session ? '<h3>' + esc(r.group.name) + ' · ' + esc(r.session.name) + '</h3>' + standingsTable(r.session.standings) : '';
      }
    } catch (e) { html = '<p class="muted">' + esc(e.message) + '</p>'; }
    const el = byId('res-session');
    if (el) el.innerHTML = html;
  }
  ACT.playAgain = () => {
    const n = G.seats.length;
    startOffline(G.seats, G.ref, (G.starter + 1) % n);
  };
  ACT.rematch = () => {
    St.api('POST', '/api/rooms/' + G.code + '/rematch', { token: G.token }).then(() => closeModal()).catch((e) => toast(e.message));
  };

  // ------------------------------------------------------------------ Online
  SCREENS.online = (p) => {
    const tab = p.tab || 'create';
    const server = St.serverUrl();
    const name = esc(St.data.settings.name || '');
    const serverNote = server ? '<p class="muted small">Server: ' + esc(server) + ' · <a href="#" data-act="goSettings">change</a></p>'
      : '<div class="warn">No online server is set. <a href="#" data-act="goSettings">Add one in Settings</a> to play online.</div>';
    const create = '<section class="card"><label>Your name<input id="on-name" maxlength="24" value="' + name + '"></label>' +
      '<label>Group<select id="on-group"><option value="new">+ New group…</option>' +
      St.data.online.map((c) => '<option value="' + c + '"' + (p.group === c ? ' selected' : '') + '>' + esc(St.data.onlineNames[c] || c) + ' (' + c + ')</option>').join('') +
      '</select></label>' +
      '<label id="on-gname-row">New group name<input id="on-gname" maxlength="40" placeholder="Friday night crew"></label>' +
      '<div class="radio-row" id="on-sess-row"><label><input type="radio" name="osess" value="cont" checked> Continue latest session</label>' +
      '<label><input type="radio" name="osess" value="new"> Start a new session</label></div>' +
      '<label>Seats<select id="on-max"><option value="2">2 players</option><option value="3">3 players</option><option value="4" selected>4 players</option></select></label>' +
      '<button class="btn big primary" data-act="createRoom">Create room</button></section>';
    const join = '<section class="card"><label>Room code<input id="on-code" maxlength="5" autocapitalize="characters" value="' + esc(p.code || '') + '" placeholder="ABCDE"></label>' +
      '<label>Your name<input id="on-jname" maxlength="24" value="' + name + '"></label>' +
      '<button class="btn big primary" data-act="joinRoom">Join room</button></section>';
    return topbar('Play online') + '<div class="page">' + serverNote +
      '<div class="tabs"><button class="tab' + (tab === 'create' ? ' active' : '') + '" data-act="onTab" data-tab="create">Create a room</button>' +
      '<button class="tab' + (tab === 'join' ? ' active' : '') + '" data-act="onTab" data-tab="join">Join a room</button></div>' +
      (tab === 'create' ? create : join) + '</div>';
  };
  AFTER.online = () => {
    const sel = byId('on-group');
    if (!sel) return;
    const sync = () => {
      byId('on-gname-row').hidden = sel.value !== 'new';
      byId('on-sess-row').hidden = sel.value === 'new';
    };
    sel.addEventListener('change', sync);
    sync();
  };
  ACT.onTab = (el) => go('online', { ...params, tab: el.dataset.tab });
  ACT.createRoom = async (el) => {
    const name = byId('on-name').value.trim();
    if (!name) return toast('Enter your name');
    St.data.settings.name = name; St.save();
    el.disabled = true;
    try {
      let code = byId('on-group').value;
      let newSession = (document.querySelector('input[name="osess"]:checked') || {}).value === 'new';
      if (code === 'new') {
        const gname = byId('on-gname').value.trim();
        if (!gname) { el.disabled = false; return toast('Name the new group'); }
        const g = await St.api('POST', '/api/groups', { name: gname });
        code = g.code; St.data.onlineNames[code] = g.name; St.rememberOnline(code);
        newSession = false;
      }
      const res = await St.api('POST', '/api/rooms', { name, groupCode: code, newSession, maxPlayers: +byId('on-max').value });
      startOnline(res.code, res.token);
    } catch (e) { el.disabled = false; toast(e.message); }
  };
  ACT.joinRoom = async (el) => {
    const code = byId('on-code').value.trim().toUpperCase();
    const name = byId('on-jname').value.trim();
    if (!code) return toast('Enter the room code');
    if (!name) return toast('Enter your name');
    St.data.settings.name = name; St.save();
    el.disabled = true;
    try {
      const saved = St.data.room && St.data.room.code === code ? St.data.room.token : undefined;
      const res = await St.api('POST', '/api/rooms/' + code + '/join', { name, token: saved });
      startOnline(res.code, res.token);
    } catch (e) { el.disabled = false; toast(e.message); }
  };
  ACT.resumeOnline = () => {
    const r = St.data.room;
    if (r.server && !St.data.settings.server && r.server !== St.serverUrl()) St.data.settings.server = r.server;
    startOnline(r.code, r.token);
  };

  function startOnline(code, token) {
    disconnect();
    St.data.room = { code, token, server: St.serverUrl() }; St.save();
    G = newG('online');
    Object.assign(G, { code, token, room: null });
    go('lobby');
    connect();
  }
  function connect() {
    const g = G;
    const es = new EventSource(St.serverUrl() + '/api/rooms/' + g.code + '/events?token=' + encodeURIComponent(g.token));
    g.es = es;
    es.onmessage = (ev) => { if (G === g) onRoom(JSON.parse(ev.data)); };
    es.onopen = () => { g.connected = true; };
    es.onerror = () => {
      g.connected = false;
      if (es.readyState !== 2 || G !== g) return;
      // The stream was refused: check whether the room still exists.
      St.api('POST', '/api/rooms/' + g.code + '/join', { token: g.token }).then(() => {
        setTimeout(() => { if (G === g && g.es === es) connect(); }, 2500);
      }, (e) => {
        if (e.status === 404 || e.status === 403) {
          St.data.room = null; St.save();
          disconnect();
          go('home');
          toast('That room has closed.');
        } else setTimeout(() => { if (G === g && g.es === es) connect(); }, 4000);
      });
    };
  }
  function disconnect() {
    if (G && G.es) { G.es.close(); G.es = null; }
  }

  function onRoom(room) {
    G.room = room;
    if (room.group) { St.data.onlineNames[room.group.code] = room.group.name; St.rememberOnline(room.group.code); }
    if (room.status === 'lobby') {
      if (screen !== 'lobby') go('lobby'); else render();
      return;
    }
    if (screen !== 'game') go('game'); else refreshGame();
    const doneKey = room.status === 'done' ? 'done:' + (room.session ? room.session.games : '') + ':' + room.game.moveNo : null;
    if (doneKey && G.resultsFor !== doneKey) { G.resultsFor = doneKey; setTimeout(showResults, 500); }
    if (room.status === 'playing' && G.resultsFor) { G.resultsFor = null; closeModal(); }
  }

  SCREENS.lobby = () => {
    const r = G && G.room;
    if (!r) return topbar('Online room', 'lobbyBack') + '<div class="page center"><p class="muted">Connecting…</p></div>';
    const seats = [];
    for (let i = 0; i < r.maxPlayers; i++) {
      const s = r.seats[i];
      seats.push(s ? '<li><i class="dot ' + (s.online ? 'on' : 'off') + '"></i>' + esc(s.name) + (i === 0 ? ' <span class="badge">host</span>' : '') +
        (i === r.you ? ' <span class="badge you">you</span>' : '') + '</li>' : '<li class="empty">Open seat</li>');
    }
    const startBtn = r.you === 0
      ? '<button class="btn big primary" data-act="startRoom"' + (r.seats.length >= 2 ? '' : ' disabled') + '>' + (r.seats.length >= 2 ? 'Start game' : 'Waiting for players…') + '</button>'
      : '<p class="muted center">Waiting for ' + esc(r.seats[0].name) + ' to start the game…</p>';
    return topbar('Online room', 'lobbyBack') + '<div class="page">' +
      '<section class="card center"><p class="muted">Room code</p><div class="room-code">' + esc(r.code) + '</div>' +
      '<button class="btn small" data-act="shareRoom">Invite friends</button></section>' +
      (r.group ? '<p class="muted small center">Scores go to <b>' + esc(r.group.name) + '</b> · ' + esc(r.session ? r.session.name : '') + '</p>' : '') +
      '<section class="card"><h3>Players ' + r.seats.length + '/' + r.maxPlayers + '</h3><ul class="seats">' + seats.join('') + '</ul></section>' +
      startBtn + '<button class="btn" data-act="leaveRoom">Leave room</button></div>';
  };
  ACT.lobbyBack = () => go('home');
  ACT.startRoom = () => St.api('POST', '/api/rooms/' + G.code + '/start', { token: G.token }).catch((e) => toast(e.message));
  ACT.leaveRoom = () => {
    St.api('POST', '/api/rooms/' + G.code + '/leave', { token: G.token }).catch(() => {});
    disconnect();
    St.data.room = null; St.save();
    go('home');
  };
  ACT.shareRoom = () => {
    const base = St.serverUrl();
    const url = base + '/?room=' + G.code;
    const text = 'Join my Hue Hive game! Room code ' + G.code + ' · ' + url;
    if (navigator.share) navigator.share({ title: 'Hue Hive', text, url }).catch(() => {});
    else if (window.HueHiveAndroid && window.HueHiveAndroid.share) window.HueHiveAndroid.share(text);
    else if (navigator.clipboard) navigator.clipboard.writeText(text).then(() => toast('Invite copied'), () => toast(text));
    else toast(text);
  };

  // ------------------------------------------------------------------ Groups
  function newGroupDialog(done) {
    const hasServer = !!St.serverUrl();
    modal('<h2>New group</h2><label>Name<input id="ng-name" maxlength="40" placeholder="Family, Office league…"></label>' +
      '<div class="radio-row"><label><input type="radio" name="ngwhere" value="local" checked> On this device</label>' +
      '<label><input type="radio" name="ngwhere" value="online"' + (hasServer ? '' : ' disabled') + '> Online (share with friends)</label></div>' +
      '<label>Players <small class="muted">(optional, comma separated)</small><input id="ng-players" placeholder="Ana, Ben, Chloe"></label>' +
      '<div class="btn-row"><button class="btn" data-act="closeModal">Cancel</button><button class="btn primary" data-act="ngCreate">Create</button></div>');
    ACT.ngCreate = async (el) => {
      const name = byId('ng-name').value.trim();
      if (!name) return toast('Give the group a name');
      const players = byId('ng-players').value.split(',').map((s) => s.trim()).filter(Boolean).slice(0, 30);
      const where = document.querySelector('input[name="ngwhere"]:checked').value;
      el.disabled = true;
      try {
        let ref;
        if (where === 'local') ref = 'local:' + St.createLocalGroup(name, players).id;
        else {
          const g = await St.api('POST', '/api/groups', { name });
          St.data.onlineNames[g.code] = g.name; St.rememberOnline(g.code);
          ref = 'online:' + g.code;
        }
        closeModal();
        done(ref);
      } catch (e) { el.disabled = false; toast(e.message); }
    };
  }

  SCREENS.groups = () => {
    const local = St.data.groups.map((g) => {
      const games = g.sessions.reduce((n, s) => n + s.games.length, 0);
      return '<button class="list-item" data-act="openGroup" data-ref="local:' + g.id + '"><b>' + esc(g.name) + '</b><small>' +
        g.sessions.length + ' session' + (g.sessions.length === 1 ? '' : 's') + ' · ' + games + ' game' + (games === 1 ? '' : 's') + '</small></button>';
    }).join('') || '<p class="muted small">No groups on this device yet.</p>';
    const online = St.data.online.map((c) => '<button class="list-item" data-act="openGroup" data-ref="online:' + c + '"><b>' +
      esc(St.data.onlineNames[c] || c) + '</b><small>Online · code ' + c + '</small></button>').join('') || '<p class="muted small">No online groups yet.</p>';
    return topbar('Groups & scores') + '<div class="page">' +
      '<p class="muted small">A group is a set of players who keep score together. Each group can have many sessions (one per game night, say), and every session has its own scoreboard.</p>' +
      '<section class="card"><h3>On this device</h3><div class="list">' + local + '</div></section>' +
      '<section class="card"><h3>Online groups</h3><div class="list">' + online + '</div>' +
      '<button class="btn small" data-act="addOnlineGroup">+ Add a group by code</button></section>' +
      '<button class="btn big primary" data-act="newGroup">+ New group</button></div>';
  };
  ACT.newGroup = () => newGroupDialog((ref) => go('group', { ref }));
  ACT.addOnlineGroup = () => {
    modal('<h2>Add an online group</h2><p class="muted small">Ask a member for the six-letter group code.</p>' +
      '<label>Group code<input id="ag-code" maxlength="6" autocapitalize="characters"></label>' +
      '<div class="btn-row"><button class="btn" data-act="closeModal">Cancel</button><button class="btn primary" data-act="agAdd">Add</button></div>');
  };
  ACT.agAdd = async () => {
    const code = byId('ag-code').value.trim().toUpperCase();
    try {
      await fetchOnlineGroup(code);
      St.rememberOnline(code);
      closeModal();
      go('group', { ref: 'online:' + code });
    } catch (e) { toast(e.message); }
  };
  ACT.openGroup = (el) => go('group', { ref: el.dataset.ref });

  SCREENS.group = (p) => {
    const [type, id] = p.ref.split(':');
    let g = p.data;
    if (type === 'local') g = St.localGroup(id);
    if (!g) return topbar('Group', 'goGroups') + '<div class="page center"><p class="muted">' + (p.error ? esc(p.error) : 'Loading…') + '</p></div>';
    const players = g.players.map((n, i) => '<span class="chip">' + esc(n) +
      (type === 'local' ? '<button class="chip-x" data-act="delPlayer" data-i="' + i + '" aria-label="Remove">✕</button>' : '') + '</span>').join('') ||
      '<span class="muted small">Players are added when they finish a game.</span>';
    const sessions = g.sessions.slice().reverse().map((s, idx) => {
      const games = s.games.slice().reverse().map((gm) => '<li><span class="muted">' + fmtDate(gm.at) + '</span> ' +
        gm.results.slice().sort((a, b) => b.score - a.score).map((r) => (gm.winners.indexOf(r.name) >= 0 ? '<b>' + esc(r.name) + ' ' + r.score + '</b>' : esc(r.name) + ' ' + r.score)).join(' · ') + '</li>').join('');
      return '<section class="card"><div class="sess-head"><h3>' + esc(s.name) + (idx === 0 ? ' <span class="badge">current</span>' : '') + '</h3><span class="muted small">' + fmtDate(s.startedAt) + ' · ' +
        s.games.length + ' game' + (s.games.length === 1 ? '' : 's') + '</span></div>' + standingsTable(E.standings(s)) +
        (games ? '<details><summary>Games</summary><ul class="games">' + games + '</ul></details>' : '') + '</section>';
    }).join('');
    return topbar(esc(g.name), 'goGroups') + '<div class="page">' +
      (type === 'online' ? '<section class="card center"><p class="muted small">Group code (share it so friends can add this group)</p><div class="room-code small">' + esc(g.code) + '</div></section>' : '') +
      '<section class="card"><h3>Players</h3><div class="chips">' + players + '</div>' +
      (type === 'local' ? '<div class="inline-form"><input id="gp-name" maxlength="24" placeholder="Add a player"><button class="btn small" data-act="addPlayer">Add</button></div>' : '') + '</section>' +
      '<button class="btn primary" data-act="newSession">Start a new session</button>' + sessions +
      '<button class="btn danger" data-act="removeGroup">' + (type === 'local' ? 'Delete group' : 'Remove from this device') + '</button></div>';
  };
  AFTER.group = (p) => {
    const [type, id] = p.ref.split(':');
    if (type === 'online' && !p.data && !p.error) {
      fetchOnlineGroup(id).then((g) => { if (screen === 'group' && params === p) { p.data = g; render(); } },
        (e) => { if (screen === 'group' && params === p) { p.error = e.message; render(); } });
    }
  };
  ACT.addPlayer = () => {
    const g = St.localGroup(params.ref.split(':')[1]);
    const n = byId('gp-name').value.trim();
    if (!n) return;
    if (g.players.some((x) => x.toLowerCase() === n.toLowerCase())) return toast('Already in the group');
    g.players.push(n); St.save(); render();
  };
  ACT.delPlayer = (el) => {
    const g = St.localGroup(params.ref.split(':')[1]);
    g.players.splice(+el.dataset.i, 1); St.save(); render();
  };
  ACT.newSession = async () => {
    const [type, id] = params.ref.split(':');
    if (type === 'local') { St.newSession(St.localGroup(id)); St.save(); render(); return; }
    try {
      await St.api('POST', '/api/groups/' + id + '/sessions', {});
      go('group', { ref: params.ref });
    } catch (e) { toast(e.message); }
  };
  ACT.removeGroup = async () => {
    const [type, id] = params.ref.split(':');
    if (type === 'local') {
      if (!(await ask('Delete this group?', 'All of its sessions and scores will be lost.', 'Delete'))) return;
      St.data.groups = St.data.groups.filter((g) => g.id !== id);
    } else {
      if (!(await ask('Remove this group from this device?', 'The scores stay on the server. You can add it again with its code.', 'Remove'))) return;
      St.forgetOnline(id);
    }
    St.save();
    go('groups');
  };

  // ------------------------------------------------------------------ Settings
  SCREENS.settings = () => topbar('Settings') + '<div class="page">' +
    '<section class="card"><label>Your name<input id="st-name" maxlength="24" value="' + esc(St.data.settings.name || '') + '"></label>' +
    '<label>Online server address<input id="st-server" placeholder="' + esc(/^https?:$/.test(location.protocol) ? location.origin : 'https://your-server.example.com') + '" value="' + esc(St.data.settings.server || '') + '" inputmode="url" autocapitalize="off"></label>' +
    '<p class="muted small">Online play needs a Hue Hive server (see the README). Leave the address blank to use the server this page came from.</p>' +
    '<div class="btn-row"><button class="btn" data-act="testServer">Test connection</button><button class="btn primary" data-act="saveSettings">Save</button></div></section></div>';
  function readSettings() {
    St.data.settings.name = byId('st-name').value.trim();
    St.data.settings.server = byId('st-server').value.trim();
    St.save();
  }
  ACT.saveSettings = () => { readSettings(); toast('Saved'); go('home'); };
  ACT.testServer = () => {
    readSettings();
    St.api('GET', '/api/health').then(() => toast('Connected to ' + St.serverUrl()), (e) => toast(e.message));
  };

  // ------------------------------------------------------------------ Rules
  const T = (c, g) => B.tileSvg({ c, g });
  const row = (tiles, ok) => '<div class="ex-row ' + (ok ? 'ok' : 'no') + '">' + tiles.map(([c, g]) => T(c, g)).join('') + '<span>' + (ok ? '✓' : '✗') + '</span></div>';
  SCREENS.rules = () => topbar('How to play') + '<div class="page rules">' +
    '<section class="card"><h3>The tiles</h3><p>75 hexagonal tiles: five colours and five glyphs, three copies of each combination. Everyone holds six tiles, hidden from the others.</p>' +
    '<div class="ex-row">' + [0, 1, 2, 3, 4].map((g) => T(g, g)).join('') + '</div></section>' +
    '<section class="card"><h3>Rows</h3><p>Tiles touching in a straight line form a <b>row</b>. A honeycomb has three directions, so a tile can sit in up to three rows at once.</p>' +
    '<p>In every row, <b>each</b> of colour and glyph must be either <b>all the same</b> or <b>all different</b>. A row can never hold two identical tiles, or more than five tiles.</p>' +
    row([[0, 0], [0, 1], [0, 4]], true) + '<p class="muted small">Same colour, different glyphs.</p>' +
    row([[1, 2], [3, 2], [4, 2]], true) + '<p class="muted small">Same glyph, different colours.</p>' +
    row([[0, 0], [2, 1], [3, 4]], true) + '<p class="muted small">Rainbow row: every colour and every glyph different.</p>' +
    row([[0, 0], [0, 1], [2, 2]], false) + '<p class="muted small">Two reds and a green: colours are neither all the same nor all different.</p></section>' +
    '<section class="card"><h3>Your turn</h3><ol><li><b>Place</b> one or more tiles. They must all go in one straight line and join up into one unbroken row (they may sit on both sides of tiles already there). At least one must touch the hive. Every row they make or extend has to follow the rules.</li>' +
    '<li>Or <b>swap</b> any number of tiles with the bag (if it has enough left).</li><li>Or <b>pass</b>.</li></ol><p>Then draw back up to six.</p></section>' +
    '<section class="card"><h3>Scoring</h3><ul><li>Each row you create or extend scores <b>one point per tile</b> in it. A tile in two or three rows scores for each.</li>' +
    '<li>Finishing a row of five is a <b>full comb</b>: +' + E.COMB_BONUS + ' bonus.</li>' +
    '<li>Once the bag is empty, the first player to use their last tile gets +' + E.FINISH_BONUS + ' and the game ends.</li>' +
    '<li>If nobody places a tile for two full rounds, the game ends.</li></ul><p>Most points wins.</p></section>' +
    '<section class="card"><h3>Controls</h3><p>Tap a tile in your hand, then a glowing space. Tap a placed (dashed) tile to pick it up again. Drag to move the board; pinch or scroll to zoom; ⤢ fits it to the screen. <b>Hint</b> suggests your best move.</p></section></div>';

  // ------------------------------------------------------------------ Android back button
  window.HueHive = {
    onBack() {
      if (!$modal.hidden) { closeModal(); return true; }
      if (screen === 'home') return false;
      if (screen === 'game') { ACT.gameBack(); return true; }
      if (screen === 'lobby') { ACT.lobbyBack(); return true; }
      if (screen === 'group') { go('groups'); return true; }
      go('home');
      return true;
    }
  };
  $modal.addEventListener('click', (e) => { if (e.target === $modal) closeModal(); });

  // ------------------------------------------------------------------ Start
  const q = new URLSearchParams(location.search);
  if (q.get('room')) {
    try { history.replaceState(null, '', location.pathname); } catch (e) { /* not allowed for file:// pages */ }
    go('online', { tab: 'join', code: q.get('room').toUpperCase() });
  } else go('home');
})();
