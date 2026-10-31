/*
 * Hue Hive: rules engine.
 *
 * A pure, dependency-free module shared by the browser, the Android WebView
 * and the Node server (which uses it to referee online games).
 * State is plain JSON so it can be stored or sent over the network as is.
 */
(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else root.HHEngine = factory();
})(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  var COLORS = ['#ef4444', '#f59e0b', '#22c55e', '#3b82f6', '#a855f7'];
  var COLOR_NAMES = ['Ruby', 'Amber', 'Jade', 'Azure', 'Violet'];
  var GLYPHS = ['circle', 'triangle', 'square', 'crescent', 'star'];
  var COPIES = 3;
  var HAND_SIZE = 6;
  var MAX_RUN = 5;
  var COMB_BONUS = 5;
  var FINISH_BONUS = 5;
  var MIN_PLAYERS = 2;
  var MAX_PLAYERS = 4;

  // The three straight lines through a hex (axial coordinates, pointy-top).
  var AXES = [[1, 0], [0, 1], [1, -1]];
  var NEIGHBORS = [[1, 0], [-1, 0], [0, 1], [0, -1], [1, -1], [-1, 1]];

  function key(q, r) { return q + ',' + r; }
  function parseKey(k) { var p = k.split(','); return { q: +p[0], r: +p[1] }; }

  // ---------- Random numbers (state-carried, so games are reproducible) ----------
  function nextRand(state) {
    var t = (state.seed = (state.seed + 0x6d2b79f5) | 0);
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  }
  function shuffle(state, arr) {
    for (var i = arr.length - 1; i > 0; i--) {
      var j = Math.floor(nextRand(state) * (i + 1));
      var tmp = arr[i]; arr[i] = arr[j]; arr[j] = tmp;
    }
    return arr;
  }

  function createTiles() {
    var tiles = [], id = 0;
    for (var copy = 0; copy < COPIES; copy++)
      for (var c = 0; c < COLORS.length; c++)
        for (var g = 0; g < GLYPHS.length; g++) tiles.push({ id: id++, c: c, g: g });
    return tiles;
  }

  // ---------- Game setup ----------
  /**
   * players: [{name, kind: 'human'|'ai', level?: 'easy'|'medium'|'hard'}]
   * opts: {seed?, starter?}
   */
  function newGame(players, opts) {
    opts = opts || {};
    if (!players || players.length < MIN_PLAYERS || players.length > MAX_PLAYERS)
      throw new Error('A game needs 2 to 4 players');
    var seed = opts.seed != null ? opts.seed | 0 : (Math.random() * 2147483647) | 0;
    var state = {
      v: 1,
      seed: seed,
      players: players.map(function (p) {
        return { name: String(p.name || 'Player'), kind: p.kind === 'ai' ? 'ai' : 'human', level: p.level || null, hand: [], score: 0 };
      }),
      bag: [],
      board: {},
      turn: 0,
      moveNo: 0,
      stall: 0,
      over: false,
      winners: [],
      last: null,
      log: []
    };
    state.bag = shuffle(state, createTiles());
    state.players.forEach(function (p) { draw(state, p); });
    var starter = opts.starter;
    if (starter == null) starter = Math.floor(nextRand(state) * players.length);
    state.turn = ((starter % players.length) + players.length) % players.length;
    return state;
  }

  function draw(state, player) {
    while (player.hand.length < HAND_SIZE && state.bag.length) player.hand.push(state.bag.pop());
  }

  // ---------- Rules ----------
  function runAt(get, q, r, a) {
    var dq = AXES[a][0], dr = AXES[a][1];
    var sq = q, sr = r;
    while (get(key(sq - dq, sr - dr))) { sq -= dq; sr -= dr; }
    var cells = [], cq = sq, cr = sr, t;
    while ((t = get(key(cq, cr)))) { cells.push({ q: cq, r: cr, t: t }); cq += dq; cr += dr; }
    return cells;
  }

  /** Returns null if the tiles can form a run, otherwise a reason. */
  function runProblem(tiles) {
    if (tiles.length > MAX_RUN) return 'A row can hold at most ' + MAX_RUN + ' tiles';
    var cs = {}, gs = {}, both = {}, nc = 0, ng = 0, nb = 0;
    for (var i = 0; i < tiles.length; i++) {
      var t = tiles[i];
      if (!cs[t.c]) { cs[t.c] = 1; nc++; }
      if (!gs[t.g]) { gs[t.g] = 1; ng++; }
      var b = t.c * 10 + t.g;
      if (!both[b]) { both[b] = 1; nb++; }
    }
    var n = tiles.length;
    if (nb !== n) return 'A row cannot hold two identical tiles';
    if (nc !== 1 && nc !== n) return 'Colours in a row must be all the same or all different';
    if (ng !== 1 && ng !== n) return 'Glyphs in a row must be all the same or all different';
    return null;
  }

  function sharedAxis(cells) {
    for (var a = 0; a < 3; a++) {
      var ok = true;
      for (var i = 1; i < cells.length && ok; i++) {
        var p = cells[0], c = cells[i];
        if (a === 0) ok = c.r === p.r;
        else if (a === 1) ok = c.q === p.q;
        else ok = c.q + c.r === p.q + p.r;
      }
      if (ok) return a;
    }
    return -1;
  }

  function fail(error) { return { ok: false, error: error }; }

  /**
   * Check a placement against the board.
   * placements: [{q, r, tile}]
   * returns {ok, error?, score, runs: [{cells, comb}]}
   */
  function evaluate(board, placements) {
    if (!placements.length) return fail('Place at least one tile');
    var overlay = {}, i, p, k;
    for (i = 0; i < placements.length; i++) {
      p = placements[i];
      k = key(p.q, p.r);
      if (board[k] || overlay[k]) return fail('That space is taken');
      overlay[k] = p.tile;
    }
    var get = function (kk) { return overlay[kk] || board[kk]; };

    if (placements.length > 1) {
      var a = sharedAxis(placements);
      if (a < 0) return fail('All tiles must go in one straight row');
      var run = runAt(get, placements[0].q, placements[0].r, a);
      var inRun = {};
      run.forEach(function (c) { inRun[key(c.q, c.r)] = 1; });
      for (i = 0; i < placements.length; i++)
        if (!inRun[key(placements[i].q, placements[i].r)]) return fail('Tiles in a row must join up without gaps');
    }

    var boardEmpty = true;
    for (k in board) { if (Object.prototype.hasOwnProperty.call(board, k)) { boardEmpty = false; break; } }
    if (!boardEmpty) {
      var touches = false;
      for (i = 0; i < placements.length && !touches; i++) {
        p = placements[i];
        for (var n = 0; n < 6; n++)
          if (board[key(p.q + NEIGHBORS[n][0], p.r + NEIGHBORS[n][1])]) { touches = true; break; }
      }
      if (!touches) return fail('Tiles must touch the hive');
    }

    var seen = {}, runs = [], score = 0;
    for (i = 0; i < placements.length; i++) {
      p = placements[i];
      for (var ax = 0; ax < 3; ax++) {
        var cells = runAt(get, p.q, p.r, ax);
        if (cells.length < 2) continue;
        var id = ax + ':' + key(cells[0].q, cells[0].r);
        if (seen[id]) continue;
        seen[id] = 1;
        var problem = runProblem(cells.map(function (c) { return c.t; }));
        if (problem) return fail(problem);
        var comb = cells.length === MAX_RUN;
        runs.push({ axis: ax, cells: cells.map(function (c) { return { q: c.q, r: c.r }; }), comb: comb });
        score += cells.length + (comb ? COMB_BONUS : 0);
      }
    }
    if (!runs.length) score = 1;
    return { ok: true, score: score, runs: runs };
  }

  // ---------- Actions (mutate state) ----------
  function checkTurn(state, seat) {
    if (state.over) return 'The game is over';
    if (seat !== state.turn) return 'It is not your turn';
    return null;
  }

  function play(state, seat, placements) {
    var e = checkTurn(state, seat);
    if (e) return fail(e);
    if (!Array.isArray(placements) || !placements.length) return fail('Place at least one tile');
    var player = state.players[seat];
    var used = {}, pl = [];
    for (var i = 0; i < placements.length; i++) {
      var x = placements[i];
      var id = x && x.id;
      var tile = null;
      for (var j = 0; j < player.hand.length; j++) if (player.hand[j].id === id) tile = player.hand[j];
      if (!tile || used[id]) return fail('That tile is not in your hand');
      if (!Number.isInteger(x.q) || !Number.isInteger(x.r) || Math.abs(x.q) > 200 || Math.abs(x.r) > 200) return fail('Bad position');
      used[id] = 1;
      pl.push({ q: x.q, r: x.r, tile: tile });
    }
    var ev = evaluate(state.board, pl);
    if (!ev.ok) return ev;
    pl.forEach(function (p) { state.board[key(p.q, p.r)] = p.tile; });
    player.hand = player.hand.filter(function (t) { return !used[t.id]; });
    draw(state, player);
    var score = ev.score, finished = false;
    if (!player.hand.length && !state.bag.length) { score += FINISH_BONUS; finished = true; }
    player.score += score;
    var combs = ev.runs.filter(function (r) { return r.comb; }).length;
    state.last = { seat: seat, type: 'play', cells: pl.map(function (p) { return { q: p.q, r: p.r }; }), score: score, combs: combs, finished: finished };
    addLog(state, player.name + ' placed ' + pl.length + ' tile' + (pl.length > 1 ? 's' : '') + ' for ' + score +
      (combs ? ' (' + (combs > 1 ? combs + ' full combs' : 'full comb') + '!)' : '') + (finished ? ' and went out' : ''));
    state.stall = 0;
    state.moveNo++;
    if (finished) endGame(state); else advance(state);
    return { ok: true, score: score };
  }

  function exchange(state, seat, ids) {
    var e = checkTurn(state, seat);
    if (e) return fail(e);
    if (!Array.isArray(ids) || !ids.length) return fail('Choose tiles to swap');
    var player = state.players[seat];
    if (ids.length > state.bag.length) return fail('There are not enough tiles in the bag');
    var set = {}, out = [];
    ids.forEach(function (id) { set[id] = 1; });
    player.hand = player.hand.filter(function (t) { if (set[t.id]) { out.push(t); return false; } return true; });
    if (out.length !== ids.length) {
      player.hand = player.hand.concat(out);
      return fail('That tile is not in your hand');
    }
    draw(state, player);
    state.bag = shuffle(state, state.bag.concat(out));
    state.last = { seat: seat, type: 'swap', count: out.length };
    addLog(state, player.name + ' swapped ' + out.length + ' tile' + (out.length > 1 ? 's' : ''));
    state.stall++;
    state.moveNo++;
    advance(state);
    return { ok: true };
  }

  function pass(state, seat) {
    var e = checkTurn(state, seat);
    if (e) return fail(e);
    state.last = { seat: seat, type: 'pass' };
    addLog(state, state.players[seat].name + ' passed');
    state.stall++;
    state.moveNo++;
    advance(state);
    return { ok: true };
  }

  /** action: {type:'play', placements} | {type:'swap', ids} | {type:'pass'} */
  function act(state, seat, action) {
    if (!action) return fail('No action');
    if (action.type === 'play') return play(state, seat, action.placements);
    if (action.type === 'swap') return exchange(state, seat, action.ids);
    if (action.type === 'pass') return pass(state, seat);
    return fail('Unknown action');
  }

  function advance(state) {
    // If nobody has placed a tile for two full rounds, the hive is stuck.
    if (state.stall >= state.players.length * 2) {
      addLog(state, 'Nobody can place a tile. The game ends.');
      endGame(state);
      return;
    }
    state.turn = (state.turn + 1) % state.players.length;
  }

  function endGame(state) {
    state.over = true;
    var best = Math.max.apply(null, state.players.map(function (p) { return p.score; }));
    state.winners = [];
    state.players.forEach(function (p, i) { if (p.score === best) state.winners.push(i); });
    addLog(state, 'Game over. ' + state.winners.map(function (i) { return state.players[i].name; }).join(' & ') +
      (state.winners.length > 1 ? ' tie' : ' wins') + ' with ' + best + '.');
  }

  function addLog(state, text) {
    state.log.push(text);
    if (state.log.length > 60) state.log.shift();
  }

  /** What one seat may see (-1 = spectator). Hides other players' hands and the bag order. */
  function viewFor(state, seat) {
    return {
      board: state.board,
      players: state.players.map(function (p) {
        return { name: p.name, kind: p.kind, level: p.level, score: p.score, handCount: p.hand.length };
      }),
      hand: seat >= 0 && state.players[seat] ? state.players[seat].hand.slice() : [],
      seat: seat,
      bagCount: state.bag.length,
      turn: state.turn,
      moveNo: state.moveNo,
      over: state.over,
      winners: state.winners.slice(),
      last: state.last,
      log: state.log.slice(-30)
    };
  }

  // ---------- Move generation (used by the AI and for hints) ----------
  function anchors(board) {
    var res = {}, any = false;
    for (var k in board) {
      if (!Object.prototype.hasOwnProperty.call(board, k)) continue;
      any = true;
      var p = parseKey(k);
      for (var n = 0; n < 6; n++) {
        var nk = key(p.q + NEIGHBORS[n][0], p.r + NEIGHBORS[n][1]);
        if (!board[nk]) res[nk] = 1;
      }
    }
    if (!any) return [{ q: 0, r: 0 }];
    return Object.keys(res).map(parseKey);
  }

  function uniqueKinds(tiles) {
    var seen = {}, out = [];
    tiles.forEach(function (t) { var b = t.c * 10 + t.g; if (!seen[b]) { seen[b] = 1; out.push(t); } });
    return out;
  }

  /** All legal plays for a hand. Each: {placements:[{q,r,tile}], score, runs} */
  function generateMoves(board, hand, maxMoves) {
    maxMoves = maxMoves || 20000;
    var moves = [], seen = {};
    var anc = anchors(board);

    // Returns false if this exact placement was already found (so its extensions were explored too).
    function record(pl, ev) {
      var sig = pl.map(function (p) { return key(p.q, p.r) + ':' + p.tile.c + p.tile.g; }).sort().join('|');
      if (seen[sig]) return false;
      seen[sig] = 1;
      moves.push({ placements: pl.slice(), score: ev.score, runs: ev.runs });
      return true;
    }

    function extend(pl, rest, a) {
      if (moves.length >= maxMoves || !rest.length) return;
      var overlay = {};
      pl.forEach(function (p) { overlay[key(p.q, p.r)] = p.tile; });
      var get = function (k) { return overlay[k] || board[k]; };
      var run = runAt(get, pl[0].q, pl[0].r, a);
      var dq = AXES[a][0], dr = AXES[a][1];
      var ends = [
        { q: run[run.length - 1].q + dq, r: run[run.length - 1].r + dr },
        { q: run[0].q - dq, r: run[0].r - dr }
      ];
      var kinds = uniqueKinds(rest);
      ends.forEach(function (cell) {
        kinds.forEach(function (t) {
          var next = pl.concat([{ q: cell.q, r: cell.r, tile: t }]);
          var ev = evaluate(board, next);
          if (!ev.ok || !record(next, ev)) return;
          var idx = rest.indexOf(t);
          extend(next, rest.slice(0, idx).concat(rest.slice(idx + 1)), a);
        });
      });
    }

    var kinds = uniqueKinds(hand);
    anc.forEach(function (cell) {
      kinds.forEach(function (t) {
        var pl = [{ q: cell.q, r: cell.r, tile: t }];
        var ev = evaluate(board, pl);
        if (!ev.ok || !record(pl, ev)) return;
        var idx = hand.indexOf(t);
        var rest = hand.slice(0, idx).concat(hand.slice(idx + 1));
        for (var a = 0; a < 3; a++) extend(pl, rest, a);
      });
    });
    return moves;
  }

  // ---------- Session scoreboards ----------
  /** session: {games:[{results:[{name, score}], winners:[name]}]} */
  function standings(session) {
    var rows = {};
    (session && session.games || []).forEach(function (g) {
      g.results.forEach(function (r) {
        var row = rows[r.name] || (rows[r.name] = { name: r.name, played: 0, wins: 0, points: 0, best: 0 });
        row.played++;
        row.points += r.score;
        row.best = Math.max(row.best, r.score);
        if (g.winners.indexOf(r.name) >= 0) row.wins++;
      });
    });
    return Object.keys(rows).map(function (k) { return rows[k]; }).sort(function (a, b) {
      return b.wins - a.wins || b.points - a.points || a.name.localeCompare(b.name);
    });
  }

  function gameRecord(state) {
    return {
      at: Date.now(),
      results: state.players.map(function (p) { return { name: p.name, score: p.score, kind: p.kind, level: p.level }; }),
      winners: state.winners.map(function (i) { return state.players[i].name; })
    };
  }

  return {
    COLORS: COLORS, COLOR_NAMES: COLOR_NAMES, GLYPHS: GLYPHS, COPIES: COPIES, HAND_SIZE: HAND_SIZE,
    MAX_RUN: MAX_RUN, COMB_BONUS: COMB_BONUS, FINISH_BONUS: FINISH_BONUS,
    MIN_PLAYERS: MIN_PLAYERS, MAX_PLAYERS: MAX_PLAYERS, AXES: AXES, NEIGHBORS: NEIGHBORS,
    key: key, parseKey: parseKey, nextRand: nextRand, createTiles: createTiles,
    newGame: newGame, evaluate: evaluate, runProblem: runProblem, runAt: runAt,
    play: play, exchange: exchange, pass: pass, act: act, viewFor: viewFor,
    anchors: anchors, generateMoves: generateMoves, standings: standings, gameRecord: gameRecord
  };
});
