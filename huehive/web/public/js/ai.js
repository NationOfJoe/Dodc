/*
 * Hue Hive: computer opponents.
 * easy   - plays a random legal move, often a modest one
 * medium - takes the highest-scoring move
 * hard   - weighs score against the tiles it keeps and the openings it leaves
 */
(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory(require('./engine.js'));
  else root.HHAI = factory(root.HHEngine);
})(typeof self !== 'undefined' ? self : this, function (E) {
  'use strict';

  function rand(state) { return E.nextRand(state); }

  function toAction(move) {
    return { type: 'play', placements: move.placements.map(function (p) { return { q: p.q, r: p.r, id: p.tile.id }; }) };
  }

  // Tiles to throw back when there is nothing worth playing: duplicates first, then loners.
  function swapAction(state, hand) {
    if (!state.bag.length) return { type: 'pass' };
    var counts = {}, ids = [];
    hand.forEach(function (t) { var b = t.c * 10 + t.g; counts[b] = (counts[b] || 0) + 1; });
    var seenKind = {};
    hand.forEach(function (t) {
      var b = t.c * 10 + t.g;
      if (seenKind[b]) ids.push(t.id); else seenKind[b] = 1;
    });
    if (!ids.length) ids = hand.map(function (t) { return t.id; });
    return { type: 'swap', ids: ids.slice(0, state.bag.length) };
  }

  function leaveValue(rest) {
    var v = 0;
    for (var i = 0; i < rest.length; i++)
      for (var j = i + 1; j < rest.length; j++) {
        var a = rest[i], b = rest[j];
        if (a.c === b.c && a.g === b.g) v -= 1.2;
        else if (a.c === b.c || a.g === b.g) v += 0.45;
        else v += 0.1; // can still form a mixed row
      }
    return v;
  }

  // Penalise leaving a row of four with an open end: the next player may finish a comb.
  function riskOf(board, move) {
    var overlay = {};
    move.placements.forEach(function (p) { overlay[E.key(p.q, p.r)] = p.tile; });
    var get = function (k) { return overlay[k] || board[k]; };
    var risk = 0;
    move.runs.forEach(function (run) {
      if (run.cells.length !== E.MAX_RUN - 1) return;
      var a = E.AXES[run.axis];
      var first = run.cells[0], last = run.cells[run.cells.length - 1];
      var open = !get(E.key(first.q - a[0], first.r - a[1])) || !get(E.key(last.q + a[0], last.r + a[1]));
      if (open) risk += 3.5;
    });
    return risk;
  }

  /** Choose an action for the seat whose turn it is. */
  function chooseAction(state, level) {
    var seat = state.turn;
    var hand = state.players[seat].hand;
    var moves = E.generateMoves(state.board, hand);
    if (!moves.length) return swapAction(state, hand);

    if (level === 'easy') {
      moves.sort(function (a, b) { return a.score - b.score; });
      if (rand(state) < 0.25) return toAction(moves[moves.length - 1]);
      var cut = Math.max(1, Math.ceil(moves.length * 0.6));
      return toAction(moves[Math.floor(rand(state) * cut)]);
    }

    if (level === 'hard') {
      var bagLeft = state.bag.length;
      var best = null, bestVal = -Infinity;
      moves.forEach(function (m) {
        var usedIds = {};
        m.placements.forEach(function (p) { usedIds[p.tile.id] = 1; });
        var rest = hand.filter(function (t) { return !usedIds[t.id]; });
        var val = m.score - riskOf(state.board, m);
        if (bagLeft > 0) val += leaveValue(rest) * 0.6;
        else {
          // Endgame: emptying the hand ends the game with a bonus.
          if (!rest.length) val += E.FINISH_BONUS + 2;
          val += m.placements.length * 0.8;
        }
        val += rand(state) * 0.01;
        if (val > bestVal) { bestVal = val; best = m; }
      });
      if (best.score <= 2 && bagLeft >= 6 && leaveValue(hand) < -1) return swapAction(state, hand);
      return toAction(best);
    }

    // medium
    var top = -1, pick = [];
    moves.forEach(function (m) {
      if (m.score > top) { top = m.score; pick = [m]; }
      else if (m.score === top) pick.push(m);
    });
    return toAction(pick[Math.floor(rand(state) * pick.length)]);
  }

  return { chooseAction: chooseAction };
});
