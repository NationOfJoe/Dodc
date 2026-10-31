/*
 * Hue Hive: drawing tiles and the honeycomb board (SVG), with pan, zoom and tap.
 */
(function (root) {
  'use strict';
  var E = root.HHEngine;
  var SQ3 = Math.sqrt(3);

  function px(q, r) { return { x: SQ3 * (q + r / 2), y: 1.5 * r }; }
  function hexPoints(s) {
    var pts = [];
    for (var i = 0; i < 6; i++) {
      var a = Math.PI / 180 * (60 * i - 30);
      pts.push((s * Math.cos(a)).toFixed(3) + ',' + (s * Math.sin(a)).toFixed(3));
    }
    return pts.join(' ');
  }
  var HEX = hexPoints(0.96), HEX_IN = hexPoints(0.9), HEX_CELL = hexPoints(0.86);

  var STAR = (function () {
    var p = [];
    for (var i = 0; i < 10; i++) {
      var rad = i % 2 ? 0.22 : 0.52, a = Math.PI / 5 * i - Math.PI / 2;
      p.push((rad * Math.cos(a)).toFixed(3) + ',' + (rad * Math.sin(a) + 0.03).toFixed(3));
    }
    return p.join(' ');
  })();

  function glyph(g, color) {
    var f = ' fill="' + color + '"';
    switch (E.GLYPHS[g]) {
      case 'circle': return '<circle r="0.4"' + f + '/>';
      case 'triangle': return '<polygon points="0,-0.48 0.46,0.34 -0.46,0.34"' + f + '/>';
      case 'square': return '<rect x="-0.35" y="-0.35" width="0.7" height="0.7" rx="0.06"' + f + '/>';
      case 'crescent': return '<path transform="translate(0.06,0) scale(0.82)" d="M0.15,-0.55 A0.57,0.57 0 1 0 0.15,0.55 A0.7,0.7 0 0 1 0.15,-0.55 Z"' + f + '/>';
      default: return '<polygon points="' + STAR + '"' + f + '/>';
    }
  }

  /** SVG markup for one tile centred on 0,0 with radius 1. */
  function tileInner(t, cls) {
    return '<polygon class="tile-bg ' + (cls || '') + '" points="' + HEX + '"/>' +
      '<polygon class="tile-face" points="' + HEX_IN + '"/>' + glyph(t.g, E.COLORS[t.c]);
  }
  function tileSvg(t, cls) {
    return '<svg class="tile-svg" viewBox="-1 -1 2 2" aria-label="' + E.COLOR_NAMES[t.c] + ' ' + E.GLYPHS[t.g] + '">' + tileInner(t, cls) + '</svg>';
  }

  /**
   * An interactive board in a container.
   * opts.onCell(q, r) is called when a cell (tile or target) is tapped.
   */
  function Board(container, opts) {
    this.el = container;
    this.opts = opts;
    this.cam = null;
    this.userCam = false;
    this.data = { board: {}, tentative: [], targets: [], last: [] };
    this.el.innerHTML = '<svg class="board-svg" xmlns="http://www.w3.org/2000/svg"></svg>';
    this.svg = this.el.firstChild;
    this.bind();
  }

  Board.prototype.set = function (d) {
    this.data = d;
    if (!this.userCam) this.fit();
    this.draw();
  };

  Board.prototype.bounds = function () {
    var d = this.data, xs = [], ys = [];
    function add(q, r) { var p = px(q, r); xs.push(p.x); ys.push(p.y); }
    Object.keys(d.board).forEach(function (k) { var c = E.parseKey(k); add(c.q, c.r); });
    d.tentative.forEach(function (c) { add(c.q, c.r); });
    d.targets.forEach(function (c) { add(c.q, c.r); });
    if (!xs.length) add(0, 0);
    var m = 2.4;
    var x0 = Math.min.apply(null, xs) - m, x1 = Math.max.apply(null, xs) + m;
    var y0 = Math.min.apply(null, ys) - m, y1 = Math.max.apply(null, ys) + m;
    // Never zoom in so far that a single tile fills the screen.
    var minW = 9;
    if (x1 - x0 < minW) { var cx = (x0 + x1) / 2; x0 = cx - minW / 2; x1 = cx + minW / 2; }
    if (y1 - y0 < minW * 0.75) { var cy = (y0 + y1) / 2; y0 = cy - minW * 0.375; y1 = cy + minW * 0.375; }
    return { x: x0, y: y0, w: x1 - x0, h: y1 - y0 };
  };

  Board.prototype.fit = function () {
    var b = this.bounds();
    var W = this.el.clientWidth || 300, H = this.el.clientHeight || 300;
    // Expand the box to the container's aspect ratio so we can pan in screen units.
    var ar = W / H;
    if (b.w / b.h > ar) { var nh = b.w / ar; b.y -= (nh - b.h) / 2; b.h = nh; }
    else { var nw = b.h * ar; b.x -= (nw - b.w) / 2; b.w = nw; }
    this.cam = b;
  };

  Board.prototype.resetView = function () { this.userCam = false; this.fit(); this.draw(); };

  Board.prototype.applyCam = function () {
    var c = this.cam;
    this.svg.setAttribute('viewBox', c.x.toFixed(3) + ' ' + c.y.toFixed(3) + ' ' + c.w.toFixed(3) + ' ' + c.h.toFixed(3));
  };

  Board.prototype.draw = function () {
    var d = this.data, out = [];
    var lastSet = {};
    (d.last || []).forEach(function (c) { lastSet[E.key(c.q, c.r)] = 1; });
    d.targets.forEach(function (c) {
      var p = px(c.q, c.r);
      out.push('<polygon class="target" data-q="' + c.q + '" data-r="' + c.r + '" transform="translate(' + p.x.toFixed(3) + ',' + p.y.toFixed(3) + ')" points="' + HEX_CELL + '"/>');
    });
    Object.keys(d.board).forEach(function (k) {
      var c = E.parseKey(k), p = px(c.q, c.r);
      out.push('<g class="cell' + (lastSet[k] ? ' last' : '') + '" data-q="' + c.q + '" data-r="' + c.r + '" transform="translate(' + p.x.toFixed(3) + ',' + p.y.toFixed(3) + ')">' + tileInner(d.board[k]) + '</g>');
    });
    d.tentative.forEach(function (c) {
      var p = px(c.q, c.r);
      out.push('<g class="cell tentative' + (d.invalid ? ' invalid' : '') + '" data-q="' + c.q + '" data-r="' + c.r + '" transform="translate(' + p.x.toFixed(3) + ',' + p.y.toFixed(3) + ')">' + tileInner(c.tile) + '</g>');
    });
    this.svg.innerHTML = out.join('');
    this.applyCam();
  };

  Board.prototype.bind = function () {
    var self = this, svg = this.svg;
    var pointers = {}, start = null, moved = false, pinch = null;

    function toWorld(cx, cy) {
      var r = svg.getBoundingClientRect(), c = self.cam;
      return { x: c.x + (cx - r.left) / r.width * c.w, y: c.y + (cy - r.top) / r.height * c.h };
    }
    function zoomAt(cx, cy, factor) {
      var c = self.cam, w = toWorld(cx, cy);
      var nw = Math.max(5, Math.min(120, c.w * factor));
      var f = nw / c.w;
      c.x = w.x - (w.x - c.x) * f; c.y = w.y - (w.y - c.y) * f; c.w *= f; c.h *= f;
      self.userCam = true;
      self.applyCam();
    }

    svg.addEventListener('pointerdown', function (e) {
      pointers[e.pointerId] = { x: e.clientX, y: e.clientY };
      var ids = Object.keys(pointers);
      if (ids.length === 1) { start = { x: e.clientX, y: e.clientY, cam: Object.assign({}, self.cam), target: e.target }; moved = false; }
      if (ids.length === 2) {
        var a = pointers[ids[0]], b = pointers[ids[1]];
        pinch = { d: Math.hypot(a.x - b.x, a.y - b.y) };
        moved = true;
      }
      try { svg.setPointerCapture(e.pointerId); } catch (err) { /* ignore */ }
    });
    svg.addEventListener('pointermove', function (e) {
      if (!pointers[e.pointerId]) return;
      pointers[e.pointerId] = { x: e.clientX, y: e.clientY };
      var ids = Object.keys(pointers);
      if (ids.length === 2 && pinch) {
        var a = pointers[ids[0]], b = pointers[ids[1]];
        var d = Math.hypot(a.x - b.x, a.y - b.y);
        if (d > 0 && pinch.d > 0) zoomAt((a.x + b.x) / 2, (a.y + b.y) / 2, pinch.d / d);
        pinch.d = d;
        return;
      }
      if (!start) return;
      var dx = e.clientX - start.x, dy = e.clientY - start.y;
      if (!moved && Math.hypot(dx, dy) < 8) return;
      moved = true;
      var r = svg.getBoundingClientRect();
      self.cam.x = start.cam.x - dx / r.width * self.cam.w;
      self.cam.y = start.cam.y - dy / r.height * self.cam.h;
      self.userCam = true;
      self.applyCam();
    });
    function end(e) {
      if (!pointers[e.pointerId]) return;
      delete pointers[e.pointerId];
      var left = Object.keys(pointers).length;
      if (left === 0) {
        if (!moved && start && e.type === 'pointerup') {
          var el = start.target;
          while (el && el !== svg && !(el.getAttribute && el.getAttribute('data-q') !== null)) el = el.parentNode;
          if (el && el !== svg) self.opts.onCell(+el.getAttribute('data-q'), +el.getAttribute('data-r'));
        }
        start = null; pinch = null;
      } else if (left === 1) {
        var id = Object.keys(pointers)[0];
        start = { x: pointers[id].x, y: pointers[id].y, cam: Object.assign({}, self.cam), target: null };
        pinch = null;
      }
    }
    svg.addEventListener('pointerup', end);
    svg.addEventListener('pointercancel', end);
    svg.addEventListener('wheel', function (e) {
      e.preventDefault();
      zoomAt(e.clientX, e.clientY, Math.exp(e.deltaY * 0.0015));
    }, { passive: false });
    root.addEventListener('resize', function () {
      if (!self.el.isConnected) return;
      if (!self.userCam) { self.fit(); self.applyCam(); }
    });
  };

  root.HHBoard = { Board: Board, tileSvg: tileSvg, tileInner: tileInner, px: px };
})(typeof self !== 'undefined' ? self : this);
