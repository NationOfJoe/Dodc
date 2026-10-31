#!/usr/bin/env node
/*
 * Hue Hive online server.
 *
 * Zero dependencies: Node's http module serves the web app, a small JSON API,
 * and Server-Sent Events for live game updates. The shared rules engine
 * referees every move, so clients cannot cheat by editing their copy.
 *
 * Groups (with their sessions and scoreboards) are saved to DATA_DIR/db.json.
 * Rooms (live games) are kept in memory.
 *
 *   PORT=8080 DATA_DIR=./data node server.js
 */
'use strict';
const http = require('http');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const E = require('./public/js/engine.js');

const PORT = +process.env.PORT || 8080;
const PUBLIC_DIR = path.join(__dirname, 'public');
const DATA_DIR = process.env.DATA_DIR || path.join(__dirname, 'data');
const DB_FILE = path.join(DATA_DIR, 'db.json');
const ROOM_IDLE_MS = 12 * 60 * 60 * 1000;

// ---------------- Persistence ----------------
let db = { groups: {} };
try {
  db = JSON.parse(fs.readFileSync(DB_FILE, 'utf8'));
  if (!db.groups) db.groups = {};
} catch (e) { /* first run */ }

let saveTimer = null;
function save() {
  if (saveTimer) return;
  saveTimer = setTimeout(() => {
    saveTimer = null;
    try {
      fs.mkdirSync(DATA_DIR, { recursive: true });
      const tmp = DB_FILE + '.tmp';
      fs.writeFileSync(tmp, JSON.stringify(db));
      fs.renameSync(tmp, DB_FILE);
    } catch (e) { console.error('Could not save data:', e.message); }
  }, 200);
}

// ---------------- Helpers ----------------
const CODE_CHARS = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
function makeCode(len, taken) {
  for (;;) {
    let c = '';
    for (let i = 0; i < len; i++) c += CODE_CHARS[crypto.randomInt(CODE_CHARS.length)];
    if (!taken(c)) return c;
  }
}
const token = () => crypto.randomBytes(18).toString('base64url');
const cleanName = (s, max = 24) => String(s || '').replace(/[\u0000-\u001f<>]/g, '').trim().slice(0, max);
const normCode = (s) => String(s || '').toUpperCase().replace(/[^A-Z0-9]/g, '');

class HttpError extends Error { constructor(status, msg) { super(msg); this.status = status; } }
const bad = (msg) => { throw new HttpError(400, msg); };

function getGroup(code) {
  const g = db.groups[normCode(code)];
  if (!g) throw new HttpError(404, 'No group with that code');
  return g;
}
function newSession(group, name) {
  const s = { id: token().slice(0, 10), name: cleanName(name, 40) || 'Session ' + (group.sessions.length + 1), startedAt: Date.now(), games: [] };
  group.sessions.push(s);
  save();
  return s;
}
function groupSummary(g) {
  return {
    code: g.code, name: g.name, players: g.players, createdAt: g.createdAt,
    sessions: g.sessions.map((s) => ({ id: s.id, name: s.name, startedAt: s.startedAt, games: s.games }))
  };
}

// ---------------- Rooms ----------------
const rooms = new Map();

function roomView(room, seat) {
  const group = db.groups[room.groupCode];
  const session = group && group.sessions.find((s) => s.id === room.sessionId);
  return {
    code: room.code,
    status: room.status,
    you: seat,
    host: 0,
    maxPlayers: room.maxPlayers,
    seats: room.seats.map((s) => ({ name: s.name, online: s.conns > 0 })),
    group: group ? { code: group.code, name: group.name } : null,
    session: session ? { id: session.id, name: session.name, games: session.games.length, standings: E.standings(session) } : null,
    game: room.game ? E.viewFor(room.game, seat) : null
  };
}

function broadcast(room) {
  room.touched = Date.now();
  for (const client of room.clients) send(client.res, roomView(room, client.seat));
}
function send(res, obj) { res.write('data: ' + JSON.stringify(obj) + '\n\n'); }

function seatByToken(room, tok) {
  const i = room.seats.findIndex((s) => s.token === tok);
  if (i < 0) throw new HttpError(403, 'You are not seated in this room');
  return i;
}
function getRoom(code) {
  const r = rooms.get(normCode(code));
  if (!r) throw new HttpError(404, 'No room with that code (it may have expired)');
  return r;
}

function recordGame(room) {
  const group = db.groups[room.groupCode];
  if (!group) return;
  const session = group.sessions.find((s) => s.id === room.sessionId);
  if (!session) return;
  session.games.push(E.gameRecord(room.game));
  room.game.players.forEach((p) => { if (!group.players.includes(p.name)) group.players.push(p.name); });
  save();
}

function startGame(room) {
  room.game = E.newGame(room.seats.map((s) => ({ name: s.name, kind: 'human' })), { starter: room.nextStarter });
  room.nextStarter = (room.game.turn + 1) % room.seats.length;
  room.status = 'playing';
}

// ---------------- API ----------------
const routes = [];
function route(method, pattern, fn) {
  const keys = [];
  const re = new RegExp('^' + pattern.replace(/:(\w+)/g, (_, k) => { keys.push(k); return '([^/]+)'; }) + '$');
  routes.push({ method, re, keys, fn });
}

route('GET', '/api/health', () => ({ ok: true, rooms: rooms.size }));

route('POST', '/api/groups', (p, body) => {
  const name = cleanName(body.name, 40);
  if (!name) bad('Give the group a name');
  const code = makeCode(6, (c) => db.groups[c]);
  const g = { code, name, players: [], sessions: [], createdAt: Date.now() };
  db.groups[code] = g;
  newSession(g);
  return groupSummary(g);
});

route('GET', '/api/groups/:code', (p) => groupSummary(getGroup(p.code)));

route('POST', '/api/groups/:code/sessions', (p, body) => {
  const g = getGroup(p.code);
  return newSession(g, body.name);
});

// Offline games (on one device, against the computer) can also count toward an online group's session.
route('POST', '/api/groups/:code/sessions/:sid/games', (p, body) => {
  const g = getGroup(p.code);
  const session = g.sessions.find((s) => s.id === p.sid);
  if (!session) throw new HttpError(404, 'No such session');
  const rec = body.record || {};
  if (!Array.isArray(rec.results) || rec.results.length < E.MIN_PLAYERS || rec.results.length > E.MAX_PLAYERS) bad('Bad game record');
  const results = rec.results.map((r) => ({
    name: cleanName(r.name) || 'Player',
    score: Math.max(0, Math.min(10000, r.score | 0)),
    kind: r.kind === 'ai' ? 'ai' : 'human',
    level: ['easy', 'medium', 'hard'].includes(r.level) ? r.level : null
  }));
  const best = Math.max(...results.map((r) => r.score));
  const record = { at: Date.now(), results, winners: results.filter((r) => r.score === best).map((r) => r.name) };
  session.games.push(record);
  results.forEach((r) => { if (r.kind === 'human' && !g.players.includes(r.name)) g.players.push(r.name); });
  save();
  return record;
});

route('POST', '/api/rooms', (p, body) => {
  const name = cleanName(body.name);
  if (!name) bad('Enter your name');
  const group = getGroup(body.groupCode);
  let session = null;
  if (!body.newSession) session = group.sessions.find((s) => s.id === body.sessionId) || group.sessions[group.sessions.length - 1];
  if (!session) session = newSession(group, body.sessionName);
  const maxPlayers = Math.min(E.MAX_PLAYERS, Math.max(E.MIN_PLAYERS, body.maxPlayers | 0 || E.MAX_PLAYERS));
  const code = makeCode(5, (c) => rooms.has(c));
  const room = {
    code, groupCode: group.code, sessionId: session.id, maxPlayers,
    seats: [{ name, token: token(), conns: 0 }],
    status: 'lobby', game: null, nextStarter: null, clients: new Set(), touched: Date.now()
  };
  rooms.set(code, room);
  return { code, token: room.seats[0].token, seat: 0 };
});

route('POST', '/api/rooms/:code/join', (p, body) => {
  const room = getRoom(p.code);
  if (body.token) {
    const i = room.seats.findIndex((s) => s.token === body.token);
    if (i >= 0) return { code: room.code, token: body.token, seat: i };
  }
  const name = cleanName(body.name);
  if (!name) bad('Enter your name');
  const same = room.seats.findIndex((s) => s.name.toLowerCase() === name.toLowerCase());
  if (same >= 0) {
    // Let a player pick up their seat again from another device, but only if that seat is disconnected.
    if (room.seats[same].conns > 0) bad('Someone called ' + name + ' is already here. Pick another name.');
    return { code: room.code, token: room.seats[same].token, seat: same };
  }
  if (room.status !== 'lobby') bad('That game has already started');
  if (room.seats.length >= room.maxPlayers) bad('That room is full');
  room.seats.push({ name, token: token(), conns: 0 });
  broadcast(room);
  return { code: room.code, token: room.seats[room.seats.length - 1].token, seat: room.seats.length - 1 };
});

route('POST', '/api/rooms/:code/leave', (p, body) => {
  const room = getRoom(p.code);
  const seat = seatByToken(room, body.token);
  if (room.status !== 'lobby') return { ok: true }; // seats stay reserved once a game has started
  room.seats.splice(seat, 1);
  for (const c of room.clients) {
    if (c.seat === seat) { c.res.end(); room.clients.delete(c); } else if (c.seat > seat) c.seat--;
  }
  if (!room.seats.length) rooms.delete(room.code); else broadcast(room);
  return { ok: true };
});

route('POST', '/api/rooms/:code/start', (p, body) => {
  const room = getRoom(p.code);
  const seat = seatByToken(room, body.token);
  if (seat !== 0) bad('Only the host can start the game');
  if (room.status === 'playing') bad('The game has already started');
  if (room.seats.length < E.MIN_PLAYERS) bad('Wait for at least one more player');
  startGame(room);
  broadcast(room);
  return { ok: true };
});

route('POST', '/api/rooms/:code/action', (p, body) => {
  const room = getRoom(p.code);
  const seat = seatByToken(room, body.token);
  if (room.status !== 'playing' || !room.game) bad('No game in progress');
  const res = E.act(room.game, seat, body.action);
  if (!res.ok) bad(res.error);
  if (room.game.over) { room.status = 'done'; recordGame(room); }
  broadcast(room);
  return res;
});

route('POST', '/api/rooms/:code/rematch', (p, body) => {
  const room = getRoom(p.code);
  seatByToken(room, body.token);
  if (room.status !== 'done') bad('The current game is not finished');
  startGame(room);
  broadcast(room);
  return { ok: true };
});

function events(req, res, params, query) {
  const room = getRoom(params.code);
  const seat = seatByToken(room, query.get('token'));
  res.writeHead(200, {
    'Content-Type': 'text/event-stream',
    'Cache-Control': 'no-cache, no-transform',
    Connection: 'keep-alive',
    'X-Accel-Buffering': 'no',
    'Access-Control-Allow-Origin': '*'
  });
  res.write('retry: 2000\n\n');
  const client = { res, seat };
  room.clients.add(client);
  room.seats[seat].conns++;
  broadcast(room);
  const ping = setInterval(() => res.write(': ping\n\n'), 20000);
  req.on('close', () => {
    clearInterval(ping);
    room.clients.delete(client);
    if (room.seats[client.seat]) room.seats[client.seat].conns = Math.max(0, room.seats[client.seat].conns - 1);
    if (rooms.has(room.code)) broadcast(room);
  });
}

// ---------------- Static files ----------------
const TYPES = {
  '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8',
  '.json': 'application/json', '.webmanifest': 'application/manifest+json', '.svg': 'image/svg+xml',
  '.png': 'image/png', '.ico': 'image/x-icon'
};
function serveStatic(req, res, pathname) {
  let rel = decodeURIComponent(pathname);
  if (rel.endsWith('/')) rel += 'index.html';
  const file = path.normalize(path.join(PUBLIC_DIR, rel));
  if (!file.startsWith(PUBLIC_DIR + path.sep)) { res.writeHead(403); return res.end(); }
  fs.readFile(file, (err, data) => {
    if (err) { res.writeHead(404, { 'Content-Type': 'text/plain' }); return res.end('Not found'); }
    res.writeHead(200, { 'Content-Type': TYPES[path.extname(file)] || 'application/octet-stream', 'Cache-Control': 'no-cache' });
    res.end(data);
  });
}

// ---------------- HTTP server ----------------
function readBody(req) {
  return new Promise((resolve, reject) => {
    let size = 0; const chunks = [];
    req.on('data', (c) => {
      size += c.length;
      if (size > 64 * 1024) { reject(new HttpError(413, 'Request too large')); req.destroy(); } else chunks.push(c);
    });
    req.on('end', () => {
      if (!chunks.length) return resolve({});
      try { resolve(JSON.parse(Buffer.concat(chunks).toString('utf8'))); } catch (e) { reject(new HttpError(400, 'Bad JSON')); }
    });
    req.on('error', reject);
  });
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, 'http://localhost');
  const cors = { 'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Headers': 'Content-Type', 'Access-Control-Allow-Methods': 'GET, POST, OPTIONS' };
  if (req.method === 'OPTIONS') { res.writeHead(204, cors); return res.end(); }
  if (!url.pathname.startsWith('/api/')) {
    if (req.method !== 'GET' && req.method !== 'HEAD') { res.writeHead(405); return res.end(); }
    return serveStatic(req, res, url.pathname);
  }
  try {
    const m = url.pathname.match(/^\/api\/rooms\/([^/]+)\/events$/);
    if (m && req.method === 'GET') return events(req, res, { code: m[1] }, url.searchParams);
    for (const r of routes) {
      if (r.method !== req.method) continue;
      const mm = url.pathname.match(r.re);
      if (!mm) continue;
      const params = {};
      r.keys.forEach((k, i) => { params[k] = decodeURIComponent(mm[i + 1]); });
      const body = req.method === 'POST' ? await readBody(req) : {};
      const out = await r.fn(params, body);
      res.writeHead(200, { ...cors, 'Content-Type': 'application/json' });
      return res.end(JSON.stringify(out));
    }
    throw new HttpError(404, 'Not found');
  } catch (e) {
    const status = e.status || 500;
    if (status === 500) console.error(e);
    if (!res.headersSent) res.writeHead(status, { ...cors, 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ error: status === 500 ? 'Server error' : e.message }));
  }
});

// Forget rooms nobody has touched for a long time.
setInterval(() => {
  const now = Date.now();
  for (const [code, room] of rooms) {
    if (!room.clients.size && now - room.touched > ROOM_IDLE_MS) rooms.delete(code);
  }
}, 10 * 60 * 1000).unref();

if (require.main === module) {
  server.listen(PORT, () => console.log('Hue Hive server on http://localhost:' + PORT));
}
module.exports = { server, rooms, db };
