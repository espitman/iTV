/* Self-contained QR encoder (byte mode, versions 1–4, EC M).
   Used only for the pairing URL on the TV settings mockup. */
(function (global) {
  "use strict";

  var EXP = new Array(512);
  var LOG = new Array(256);
  (function initGF() {
    var x = 1;
    for (var i = 0; i < 255; i++) {
      EXP[i] = x;
      LOG[x] = i;
      x <<= 1;
      if (x & 256) x ^= 0x11d;
    }
    for (i = 255; i < 512; i++) EXP[i] = EXP[i - 255];
  })();

  function gfMul(a, b) {
    if (a === 0 || b === 0) return 0;
    return EXP[LOG[a] + LOG[b]];
  }

  function rsMul(p, q) {
    var r = new Array(p.length + q.length - 1).fill(0);
    for (var i = 0; i < p.length; i++) {
      for (var j = 0; j < q.length; j++) r[i + j] ^= gfMul(p[i], q[j]);
    }
    return r;
  }

  function rsGenerator(nsym) {
    var g = [1];
    for (var i = 0; i < nsym; i++) g = rsMul(g, [1, EXP[i]]);
    return g;
  }

  function rsEncode(data, nsym) {
    var gen = rsGenerator(nsym);
    var res = data.slice();
    for (var i = 0; i < nsym; i++) res.push(0);
    for (i = 0; i < data.length; i++) {
      var coef = res[i];
      if (coef === 0) continue;
      for (var j = 0; j < gen.length; j++) res[i + j] ^= gfMul(gen[j], coef);
    }
    return res.slice(data.length);
  }

  var VERSIONS = {
    1: { size: 21, data: 16, ec: 10, align: [] },
    2: { size: 25, data: 28, ec: 16, align: [18] },
    3: { size: 29, data: 44, ec: 26, align: [22] },
    4: { size: 33, data: 64, ec: 36, align: [26] }
  };

  function chooseVersion(byteLen) {
    for (var v = 1; v <= 4; v++) {
      if (VERSIONS[v].data - 2 >= byteLen) return v;
    }
    throw new Error("QR payload too long");
  }

  function bitsToBytes(bits) {
    var out = [];
    for (var i = 0; i < bits.length; i += 8) {
      var byte = 0;
      for (var j = 0; j < 8; j++) byte = (byte << 1) | (bits[i + j] || 0);
      out.push(byte);
    }
    return out;
  }

  function encodeData(text, version) {
    var bytes = [];
    for (var i = 0; i < text.length; i++) {
      var c = text.charCodeAt(i);
      if (c > 255) throw new Error("QR supports Latin-1 only");
      bytes.push(c);
    }
    var cap = VERSIONS[version].data;
    var bits = [];
    function push(val, n) {
      for (var k = n - 1; k >= 0; k--) bits.push((val >> k) & 1);
    }
    push(0b0100, 4);
    push(bytes.length, 8);
    for (i = 0; i < bytes.length; i++) push(bytes[i], 8);
    var remaining = cap * 8 - bits.length;
    push(0, Math.min(4, remaining));
    while (bits.length % 8) bits.push(0);
    var data = bitsToBytes(bits);
    var pads = [0xec, 0x11];
    var p = 0;
    while (data.length < cap) data.push(pads[p++ % 2]);
    return data;
  }

  function fillRect(mod, reserved, r, c, w, h, val) {
    for (var i = 0; i < h; i++) {
      for (var j = 0; j < w; j++) {
        mod[r + i][c + j] = val;
        reserved[r + i][c + j] = 1;
      }
    }
  }

  function placeFinder(mod, reserved, r, c) {
    fillRect(mod, reserved, r, c, 7, 7, 1);
    fillRect(mod, reserved, r + 1, c + 1, 5, 5, 0);
    fillRect(mod, reserved, r + 2, c + 2, 3, 3, 1);
  }

  function placeAlign(mod, reserved, r, c) {
    fillRect(mod, reserved, r - 2, c - 2, 5, 5, 1);
    fillRect(mod, reserved, r - 1, c - 1, 3, 3, 0);
    mod[r][c] = 1;
    reserved[r][c] = 1;
  }

  function maskBit(mask, r, c) {
    switch (mask) {
      case 0: return (r + c) % 2 === 0;
      case 1: return r % 2 === 0;
      case 2: return c % 3 === 0;
      case 3: return (r + c) % 3 === 0;
      case 4: return (Math.floor(r / 2) + Math.floor(c / 3)) % 2 === 0;
      case 5: return ((r * c) % 2) + ((r * c) % 3) === 0;
      case 6: return (((r * c) % 2) + ((r * c) % 3)) % 2 === 0;
      case 7: return (((r + c) % 2) + ((r * c) % 3)) % 2 === 0;
      default: return false;
    }
  }

  function placeFormat(mod, reserved, size, mask) {
    var ec = 0b00;
    var data = (ec << 3) | mask;
    var bits = data << 10;
    for (var i = 14; i >= 10; i--) {
      if ((bits >>> i) & 1) bits ^= 0x537 << (i - 10);
    }
    bits = ((data << 10) | (bits & 0x3ff)) ^ 0x5412;
    var positions = [
      [8, 0], [8, 1], [8, 2], [8, 3], [8, 4], [8, 5], [8, 7], [8, 8],
      [7, 8], [5, 8], [4, 8], [3, 8], [2, 8], [1, 8], [0, 8]
    ];
    var positions2 = [
      [size - 1, 8], [size - 2, 8], [size - 3, 8], [size - 4, 8], [size - 5, 8],
      [size - 6, 8], [size - 7, 8], [size - 8, 8],
      [8, size - 7], [8, size - 6], [8, size - 5], [8, size - 4], [8, size - 3],
      [8, size - 2], [8, size - 1]
    ];
    for (var i = 0; i < 15; i++) {
      var bit = (bits >> i) & 1;
      mod[positions[i][0]][positions[i][1]] = bit;
      reserved[positions[i][0]][positions[i][1]] = 1;
      mod[positions2[i][0]][positions2[i][1]] = bit;
      reserved[positions2[i][0]][positions2[i][1]] = 1;
    }
    mod[size - 8][8] = 1;
    reserved[size - 8][8] = 1;
  }

  function placeData(mod, reserved, size, codewords, mask) {
    var bits = [];
    for (var i = 0; i < codewords.length; i++) {
      for (var b = 7; b >= 0; b--) bits.push((codewords[i] >> b) & 1);
    }
    var bit = 0;
    var up = true;
    for (var col = size - 1; col > 0; col -= 2) {
      if (col === 6) col--;
      for (var n = 0; n < size; n++) {
        var row = up ? size - 1 - n : n;
        for (var k = 0; k < 2; k++) {
          var c = col - k;
          if (reserved[row][c]) continue;
          var v = bits[bit++] || 0;
          if (maskBit(mask, row, c)) v ^= 1;
          mod[row][c] = v;
        }
      }
      up = !up;
    }
  }

  function penalty(mod, size) {
    var score = 0;
    var r, c, run, dark = 0;
    for (r = 0; r < size; r++) {
      run = 1;
      for (c = 1; c < size; c++) {
        if (mod[r][c] === mod[r][c - 1]) run++;
        else {
          if (run >= 5) score += 3 + (run - 5);
          run = 1;
        }
      }
      if (run >= 5) score += 3 + (run - 5);
    }
    for (c = 0; c < size; c++) {
      run = 1;
      for (r = 1; r < size; r++) {
        if (mod[r][c] === mod[r - 1][c]) run++;
        else {
          if (run >= 5) score += 3 + (run - 5);
          run = 1;
        }
      }
      if (run >= 5) score += 3 + (run - 5);
    }
    for (r = 0; r < size - 1; r++) {
      for (c = 0; c < size - 1; c++) {
        var v = mod[r][c];
        if (v === mod[r][c + 1] && v === mod[r + 1][c] && v === mod[r + 1][c + 1]) score += 3;
      }
    }
    function finderLike(line) {
      var pat = [1, 0, 1, 1, 1, 0, 1];
      var s = 0;
      for (var i = 0; i <= line.length - 7; i++) {
        var ok = true;
        for (var j = 0; j < 7; j++) if (line[i + j] !== pat[j]) ok = false;
        if (!ok) continue;
        var left = i >= 4 && line.slice(i - 4, i).every(function (x) { return x === 0; });
        var right = i + 11 <= line.length && line.slice(i + 7, i + 11).every(function (x) { return x === 0; });
        if (left || right) s += 40;
      }
      return s;
    }
    for (r = 0; r < size; r++) score += finderLike(mod[r]);
    for (c = 0; c < size; c++) {
      var col = [];
      for (r = 0; r < size; r++) col.push(mod[r][c]);
      score += finderLike(col);
    }
    for (r = 0; r < size; r++) for (c = 0; c < size; c++) if (mod[r][c]) dark++;
    var percent = (dark * 100) / (size * size);
    score += Math.abs(Math.floor(percent / 5) - 10) * 10;
    return score;
  }

  function build(text) {
    var version = chooseVersion(text.length);
    var spec = VERSIONS[version];
    var size = spec.size;
    var data = encodeData(text, version);
    var ec = rsEncode(data, spec.ec);
    var codewords = data.concat(ec);

    function skeleton() {
      var mod = [];
      var reserved = [];
      var r, c;
      for (r = 0; r < size; r++) {
        mod[r] = new Array(size).fill(0);
        reserved[r] = new Array(size).fill(0);
      }
      placeFinder(mod, reserved, 0, 0);
      placeFinder(mod, reserved, 0, size - 7);
      placeFinder(mod, reserved, size - 7, 0);
      fillRect(mod, reserved, 7, 0, 8, 1, 0);
      fillRect(mod, reserved, 0, 7, 1, 8, 0);
      fillRect(mod, reserved, 7, size - 8, 8, 1, 0);
      fillRect(mod, reserved, 0, size - 8, 1, 8, 0);
      fillRect(mod, reserved, size - 8, 0, 8, 1, 0);
      fillRect(mod, reserved, size - 8, 7, 1, 8, 0);
      for (c = 8; c < size - 8; c++) {
        mod[6][c] = c % 2 === 0 ? 1 : 0;
        reserved[6][c] = 1;
      }
      for (r = 8; r < size - 8; r++) {
        mod[r][6] = r % 2 === 0 ? 1 : 0;
        reserved[r][6] = 1;
      }
      spec.align.forEach(function (p) {
        placeAlign(mod, reserved, p, p);
      });
      reserved[size - 8][8] = 1;
      mod[size - 8][8] = 1;
      for (r = 0; r < 9; r++) reserved[r][8] = 1;
      for (c = 0; c < 9; c++) reserved[8][c] = 1;
      for (r = size - 8; r < size; r++) reserved[r][8] = 1;
      for (c = size - 8; c < size; c++) reserved[8][c] = 1;
      return { mod: mod, reserved: reserved };
    }

    var best = null;
    var bestScore = Infinity;
    var bestMask = 0;
    for (var mask = 0; mask < 8; mask++) {
      var sk = skeleton();
      placeFormat(sk.mod, sk.reserved, size, mask);
      placeData(sk.mod, sk.reserved, size, codewords, mask);
      var score = penalty(sk.mod, size);
      if (score < bestScore) {
        bestScore = score;
        best = sk.mod;
        bestMask = mask;
      }
    }
    return { modules: best, size: size, version: version, mask: bestMask };
  }

  function toSvg(text, opts) {
    opts = opts || {};
    var qr = build(text);
    var quiet = opts.quiet == null ? 2 : opts.quiet;
    var dim = qr.size + quiet * 2;
    var parts = [
      '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ' +
        dim + " " + dim + '" shape-rendering="crispEdges" aria-hidden="true">'
    ];
    parts.push('<rect width="' + dim + '" height="' + dim + '" fill="' + (opts.light || "#F3FBFC") + '"/>');
    var dark = opts.dark || "#05080C";
    for (var r = 0; r < qr.size; r++) {
      for (var c = 0; c < qr.size; c++) {
        if (!qr.modules[r][c]) continue;
        parts.push(
          '<rect x="' + (c + quiet) + '" y="' + (r + quiet) + '" width="1" height="1" fill="' + dark + '"/>'
        );
      }
    }
    parts.push("</svg>");
    return parts.join("");
  }

  global.ITVQR = { build: build, toSvg: toSvg };
})(window);
