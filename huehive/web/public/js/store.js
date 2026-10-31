/*
 * Hue Hive: what this device remembers (groups kept on the device, links to online
 * groups, settings, an unfinished offline game, the online room you are in) and a
 * tiny client for the online server.
 */
(function (root) {
  'use strict';
  var KEY = 'huehive.v1';

  function defaults() {
    return { groups: [], online: [], settings: { server: '', name: '' }, room: null, offline: null, lastSetup: null };
  }
  function load() {
    try {
      var d = JSON.parse(root.localStorage.getItem(KEY));
      if (d) { var out = defaults(); for (var k in d) out[k] = d[k]; return out; }
    } catch (e) { /* storage may be blocked */ }
    return defaults();
  }
  var data = load();
  function save() { try { root.localStorage.setItem(KEY, JSON.stringify(data)); } catch (e) { /* ignore */ } }

  function uid() { return Date.now().toString(36) + Math.random().toString(36).slice(2, 7); }

  // ---------- Groups kept on this device ----------
  function newSession(group, name) {
    var s = { id: uid(), name: name || 'Session ' + (group.sessions.length + 1), startedAt: Date.now(), games: [] };
    group.sessions.push(s);
    return s;
  }
  function createLocalGroup(name, players) {
    var g = { id: uid(), name: name, players: players || [], sessions: [], createdAt: Date.now() };
    newSession(g);
    data.groups.push(g);
    save();
    return g;
  }
  function localGroup(id) { return data.groups.find(function (g) { return g.id === id; }) || null; }

  // ---------- Server ----------
  function serverUrl() {
    var s = (data.settings.server || '').trim().replace(/\/+$/, '');
    if (s) return /^https?:\/\//i.test(s) ? s : 'http://' + s;
    if (/^https?:$/.test(root.location.protocol)) return root.location.origin;
    return '';
  }
  function api(method, path, body) {
    var base = serverUrl();
    if (!base) return Promise.reject(new Error('Set the online server address in Settings first.'));
    return fetch(base + path, {
      method: method,
      headers: body ? { 'Content-Type': 'application/json' } : {},
      body: body ? JSON.stringify(body) : undefined
    }).then(function (r) {
      return r.json().catch(function () { return {}; }).then(function (j) {
        if (!r.ok) { var e = new Error(j.error || 'Server error (' + r.status + ')'); e.status = r.status; throw e; }
        return j;
      });
    }, function () { throw new Error('Could not reach the server at ' + base); });
  }

  function rememberOnline(code) {
    if (data.online.indexOf(code) < 0) { data.online.push(code); save(); }
  }
  function forgetOnline(code) {
    data.online = data.online.filter(function (c) { return c !== code; });
    save();
  }

  /** Save a finished game to a group's session, wherever the group lives. */
  function recordGame(ref, record) {
    if (!ref) return Promise.resolve();
    if (ref.type === 'local') {
      var g = localGroup(ref.id);
      if (!g) return Promise.resolve();
      var s = g.sessions.find(function (x) { return x.id === ref.sessionId; });
      if (!s) s = newSession(g);
      s.games.push(record);
      record.results.forEach(function (r) {
        if (r.kind === 'human' && g.players.indexOf(r.name) < 0) g.players.push(r.name);
      });
      save();
      return Promise.resolve();
    }
    return api('POST', '/api/groups/' + ref.id + '/sessions/' + ref.sessionId + '/games', { record: record });
  }

  root.HHStore = {
    data: data, save: save, uid: uid,
    createLocalGroup: createLocalGroup, localGroup: localGroup, newSession: newSession,
    serverUrl: serverUrl, api: api, rememberOnline: rememberOnline, forgetOnline: forgetOnline,
    recordGame: recordGame
  };
})(typeof self !== 'undefined' ? self : this);
