'use strict';
const test = require('node:test');
const assert = require('node:assert');
const E = require('../public/js/engine.js');
const AI = require('../public/js/ai.js');

const T = (c, g, id = c * 10 + g + 1000) => ({ id, c, g });

test('tile set has 75 tiles, 3 of each kind', () => {
  const tiles = E.createTiles();
  assert.strictEqual(tiles.length, 75);
  const kinds = {};
  tiles.forEach((t) => { kinds[t.c * 10 + t.g] = (kinds[t.c * 10 + t.g] || 0) + 1; });
  assert.strictEqual(Object.keys(kinds).length, 25);
  Object.values(kinds).forEach((n) => assert.strictEqual(n, 3));
});

test('run rule: same or all different on each trait, no duplicates', () => {
  assert.strictEqual(E.runProblem([T(0, 0), T(0, 1), T(0, 2)]), null); // same colour
  assert.strictEqual(E.runProblem([T(0, 0), T(1, 0), T(2, 0)]), null); // same glyph
  assert.strictEqual(E.runProblem([T(0, 0), T(1, 1), T(2, 2)]), null); // rainbow
  assert.ok(E.runProblem([T(0, 0), T(0, 0)]));
  assert.ok(E.runProblem([T(0, 0), T(0, 1), T(1, 2)]));
  assert.ok(E.runProblem([T(0, 0), T(1, 0), T(2, 1)]));
  assert.ok(E.runProblem([T(0, 0), T(0, 1), T(0, 2), T(0, 3), T(0, 4), T(0, 0)]));
});

test('scoring counts every run and the full-comb bonus', () => {
  const board = {};
  board[E.key(0, 0)] = T(0, 0, 1);
  board[E.key(1, 0)] = T(0, 1, 2);
  board[E.key(2, 0)] = T(0, 2, 3);
  board[E.key(3, 0)] = T(0, 3, 4);
  const ev = E.evaluate(board, [{ q: 4, r: 0, tile: T(0, 4, 5) }]);
  assert.ok(ev.ok);
  assert.strictEqual(ev.score, 5 + E.COMB_BONUS);

  // A tile joining two runs scores both.
  const b2 = {};
  b2[E.key(0, 0)] = T(1, 1, 1);
  b2[E.key(0, 1)] = T(2, 2, 2);
  const ev2 = E.evaluate(b2, [{ q: 1, r: 0, tile: T(1, 2, 3) }]);
  assert.ok(ev2.ok, ev2.error);
  // (1,0) neighbours (0,0) on axis 0, and (0,1) on axis 2 (1,-1 direction)
  assert.strictEqual(ev2.score, 4);
});

test('placements must be in one row, joined and touching the hive', () => {
  const board = {};
  board[E.key(0, 0)] = T(0, 0, 1);
  assert.ok(!E.evaluate(board, [{ q: 5, r: 5, tile: T(0, 1) }]).ok);
  assert.ok(!E.evaluate(board, [{ q: 1, r: 0, tile: T(0, 1) }, { q: -1, r: 1, tile: T(0, 2) }]).ok);
  assert.ok(!E.evaluate(board, [{ q: 1, r: 0, tile: T(0, 1) }, { q: 3, r: 0, tile: T(0, 2) }]).ok);
  assert.ok(E.evaluate(board, [{ q: 1, r: 0, tile: T(0, 1) }, { q: 2, r: 0, tile: T(0, 2) }]).ok);
  assert.ok(E.evaluate(board, [{ q: -1, r: 0, tile: T(0, 1) }, { q: 1, r: 0, tile: T(0, 2) }]).ok);
});

test('hidden information: view shows only your own hand', () => {
  const s = E.newGame([{ name: 'A' }, { name: 'B' }], { seed: 1 });
  const v = E.viewFor(s, 0);
  assert.strictEqual(v.hand.length, 6);
  assert.strictEqual(v.players[1].handCount, 6);
  assert.ok(!('hand' in v.players[1]));
  assert.ok(!('bag' in v));
});

test('swap and pass rules', () => {
  const s = E.newGame([{ name: 'A' }, { name: 'B' }], { seed: 5, starter: 0 });
  assert.ok(!E.pass(s, 1).ok, 'out of turn');
  const ids = s.players[0].hand.slice(0, 2).map((t) => t.id);
  assert.ok(E.exchange(s, 0, ids).ok);
  assert.strictEqual(s.players[0].hand.length, 6);
  assert.strictEqual(s.bag.length, 75 - 12);
  assert.strictEqual(s.turn, 1);
  assert.ok(E.pass(s, 1).ok);
});

for (const levels of [['easy', 'medium'], ['hard', 'medium', 'easy'], ['hard', 'hard', 'hard', 'hard']]) {
  test(`AI games run to completion (${levels.join(' vs ')})`, () => {
    for (let seed = 1; seed <= 2; seed++) {
      const s = E.newGame(levels.map((l, i) => ({ name: 'Bot' + i, kind: 'ai', level: l })), { seed });
      let guard = 0;
      while (!s.over && guard++ < 500) {
        const action = AI.chooseAction(s, s.players[s.turn].level);
        const res = E.act(s, s.turn, action);
        assert.ok(res.ok, res.error);
      }
      assert.ok(s.over, 'game finished');
      // Every tile is accounted for.
      const onBoard = Object.keys(s.board).length;
      const inHands = s.players.reduce((n, p) => n + p.hand.length, 0);
      assert.strictEqual(onBoard + inHands + s.bag.length, 75);
      // Every run on the board is legal.
      for (const k of Object.keys(s.board)) {
        const { q, r } = E.parseKey(k);
        for (let a = 0; a < 3; a++) {
          const run = E.runAt((kk) => s.board[kk], q, r, a);
          if (run.length > 1) assert.strictEqual(E.runProblem(run.map((c) => c.t)), null);
        }
      }
    }
  });
}

test('hard AI beats easy AI more often than not', () => {
  let hardWins = 0;
  for (let seed = 10; seed < 22; seed++) {
    const s = E.newGame([{ name: 'H', kind: 'ai', level: 'hard' }, { name: 'E', kind: 'ai', level: 'easy' }], { seed, starter: seed % 2 });
    while (!s.over) E.act(s, s.turn, AI.chooseAction(s, s.players[s.turn].level));
    if (s.winners.includes(0) && s.winners.length === 1) hardWins++;
  }
  assert.ok(hardWins >= 7, "hard won " + hardWins + "/12");
});

test('standings rank by wins, then points', () => {
  const st = E.standings({ games: [
    { results: [{ name: 'A', score: 40 }, { name: 'B', score: 50 }], winners: ['B'] },
    { results: [{ name: 'A', score: 60 }, { name: 'B', score: 30 }], winners: ['A'] },
    { results: [{ name: 'A', score: 45 }, { name: 'B', score: 20 }, { name: 'C', score: 10 }], winners: ['A'] }
  ] });
  assert.deepStrictEqual(st.map((r) => r.name), ['A', 'B', 'C']);
  assert.strictEqual(st[0].wins, 2);
  assert.strictEqual(st[0].points, 145);
});
