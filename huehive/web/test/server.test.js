'use strict';
const test = require('node:test');
const assert = require('node:assert');
const os = require('os');
const fs = require('fs');
const path = require('path');

process.env.DATA_DIR = fs.mkdtempSync(path.join(os.tmpdir(), 'huehive-'));
const { server } = require('../server.js');

let base;
test.before(() => new Promise((res) => server.listen(0, () => { base = 'http://127.0.0.1:' + server.address().port; res(); })));
test.after(() => new Promise((res) => server.close(res)));

async function call(method, p, body) {
  const r = await fetch(base + p, { method, headers: { 'Content-Type': 'application/json' }, body: body ? JSON.stringify(body) : undefined });
  return { status: r.status, body: await r.json() };
}

function nextEvent(code, token) {
  // Read one Server-Sent Event and close the stream.
  return new Promise((resolve, reject) => {
    const http = require('http');
    const req = http.get(base + '/api/rooms/' + code + '/events?token=' + token, (res) => {
      let buf = '';
      res.on('data', (c) => {
        buf += c;
        const m = buf.match(/data: (.*)\n\n/);
        if (m) { req.destroy(); resolve(JSON.parse(m[1])); }
      });
    });
    req.on('error', (e) => { if (e.code !== 'ECONNRESET') reject(e); });
  });
}

test('a full online flow: group, room, join, start, moves, scoring', async () => {
  const g = await call('POST', '/api/groups', { name: 'Crew' });
  assert.strictEqual(g.status, 200);
  assert.match(g.body.code, /^[A-Z0-9]{6}$/);
  assert.strictEqual(g.body.sessions.length, 1);

  const room = await call('POST', '/api/rooms', { name: 'Ana', groupCode: g.body.code, maxPlayers: 2 });
  assert.strictEqual(room.status, 200);
  const code = room.body.code;

  assert.strictEqual((await call('POST', '/api/rooms/' + code + '/start', { token: room.body.token })).status, 400, 'needs 2 players');
  const ben = await call('POST', '/api/rooms/' + code + '/join', { name: 'Ben' });
  assert.strictEqual(ben.body.seat, 1);
  assert.strictEqual((await call('POST', '/api/rooms/' + code + '/join', { name: 'Cy' })).status, 400, 'room full');
  assert.strictEqual((await call('POST', '/api/rooms/' + code + '/start', { token: ben.body.token })).status, 400, 'only host starts');
  assert.strictEqual((await call('POST', '/api/rooms/' + code + '/start', { token: room.body.token })).status, 200);

  const tokens = [room.body.token, ben.body.token];
  let view = await nextEvent(code, tokens[0]);
  assert.strictEqual(view.status, 'playing');
  assert.strictEqual(view.game.hand.length, 6);
  assert.ok(!('hand' in view.game.players[1]));

  // The wrong player cannot move.
  const turn = view.game.turn;
  const wrong = await call('POST', '/api/rooms/' + code + '/action', { token: tokens[1 - turn], action: { type: 'pass' } });
  assert.strictEqual(wrong.status, 400);
  // Someone else's tile id is rejected.
  const mine = (await nextEvent(code, tokens[turn])).game.hand;
  const other = (await nextEvent(code, tokens[1 - turn])).game.hand;
  const cheat = await call('POST', '/api/rooms/' + code + '/action', { token: tokens[turn], action: { type: 'play', placements: [{ q: 0, r: 0, id: other[0].id }] } });
  assert.strictEqual(cheat.status, 400);
  const ok = await call('POST', '/api/rooms/' + code + '/action', { token: tokens[turn], action: { type: 'play', placements: [{ q: 0, r: 0, id: mine[0].id }] } });
  assert.strictEqual(ok.status, 200);
  assert.strictEqual(ok.body.score, 1);

  // Pass until the game ends (two full rounds without placing).
  let t = 1 - turn;
  for (let i = 0; i < 4; i++) {
    assert.strictEqual((await call('POST', '/api/rooms/' + code + '/action', { token: tokens[t], action: { type: 'pass' } })).status, 200);
    t = 1 - t;
  }
  view = await nextEvent(code, tokens[0]);
  assert.strictEqual(view.status, 'done');
  assert.strictEqual(view.session.games, 1);
  const grp = await call('GET', '/api/groups/' + g.body.code);
  assert.strictEqual(grp.body.sessions[0].games.length, 1);
  assert.deepStrictEqual(grp.body.players.sort(), ['Ana', 'Ben']);

  // Rematch, and recording an offline game into a new session.
  assert.strictEqual((await call('POST', '/api/rooms/' + code + '/rematch', { token: tokens[1] })).status, 200);
  const s2 = await call('POST', '/api/groups/' + g.body.code + '/sessions', { name: 'Week 2' });
  const rec = await call('POST', '/api/groups/' + g.body.code + '/sessions/' + s2.body.id + '/games', {
    record: { results: [{ name: 'Ana', score: 50, kind: 'human' }, { name: 'Bot', score: 70, kind: 'ai', level: 'hard' }] }
  });
  assert.deepStrictEqual(rec.body.winners, ['Bot']);
});

test('unknown rooms and groups give 404', async () => {
  assert.strictEqual((await call('GET', '/api/groups/ZZZZZZ')).status, 404);
  assert.strictEqual((await call('POST', '/api/rooms/ZZZZZ/join', { name: 'x' })).status, 404);
});

test('static files are served and traversal is blocked', async () => {
  const r = await fetch(base + '/');
  assert.strictEqual(r.status, 200);
  assert.match(await r.text(), /Hue Hive/);
  const bad = await fetch(base + '/%2e%2e/server.js');
  assert.notStrictEqual(bad.status, 200);
});
