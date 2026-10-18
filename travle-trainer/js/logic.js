// Pure logic: graph, shortest paths, name matching, spaced repetition. No DOM.
(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else root.TT = factory();
})(typeof self !== 'undefined' ? self : this, function () {

  // ---------- text ----------
  function norm(s) {
    return String(s).normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase()
      .replace(/&/g, ' and ').replace(/[^a-z0-9 ]+/g, ' ').replace(/\s+/g, ' ').trim();
  }
  function lev(a, b, max) {
    if (Math.abs(a.length - b.length) > max) return max + 1;
    let prev = Array.from({ length: b.length + 1 }, (_, i) => i);
    for (let i = 1; i <= a.length; i++) {
      const cur = [i];
      let rowMin = i;
      for (let j = 1; j <= b.length; j++) {
        cur[j] = Math.min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (a[i - 1] === b[j - 1] ? 0 : 1));
        if (cur[j] < rowMin) rowMin = cur[j];
      }
      if (rowMin > max) return max + 1;
      prev = cur;
    }
    return prev[b.length];
  }

  const MANUAL_ALIASES = {
    USA: ['usa', 'us', 'america', 'united states of america'],
    GBR: ['uk', 'britain', 'great britain', 'england'],
    ARE: ['uae', 'emirates'],
    CIV: ['ivory coast', 'cote d ivoire'],
    COD: ['drc', 'dr congo', 'democratic republic of the congo', 'congo kinshasa', 'congo'],
    COG: ['congo brazzaville', 'republic of the congo', 'congo republic', 'congo'],
    CZE: ['czech republic', 'czechia'],
    MMR: ['burma'], SWZ: ['swaziland', 'eswatini'], MKD: ['macedonia', 'north macedonia'],
    TLS: ['east timor', 'timor leste'], CPV: ['cabo verde', 'cape verde'],
    PSE: ['palestine', 'state of palestine'], VAT: ['vatican', 'holy see', 'vatican city'],
    KOR: ['south korea', 'korea south', 'republic of korea'], PRK: ['north korea', 'korea north'],
    RUS: ['russian federation'], TUR: ['turkiye', 'turkey'], GMB: ['gambia', 'the gambia'],
    BHS: ['bahamas', 'the bahamas'], STP: ['sao tome', 'sao tome and principe'],
    FSM: ['micronesia'], LAO: ['laos'], VNM: ['vietnam', 'viet nam'],
    BIH: ['bosnia', 'bosnia herzegovina', 'bosnia and herzegovina'],
    TTO: ['trinidad', 'tobago', 'trinidad and tobago'], ATG: ['antigua', 'antigua and barbuda', 'barbuda'],
    KNA: ['st kitts', 'saint kitts', 'st kitts and nevis', 'saint kitts and nevis', 'nevis'],
    VCT: ['st vincent', 'saint vincent', 'saint vincent and the grenadines', 'grenadines'],
    LCA: ['st lucia', 'saint lucia'], NLD: ['holland', 'netherlands'], CHN: ['prc'],
    TWN: ['republic of china', 'chinese taipei'], UNK: ['kosovo'], ESH: ['western sahara'],
    PNG: ['papua new guinea'], GNB: ['guinea bissau'], SLE: ['sierra leone'], SYR: ['syria'],
    IRN: ['iran'], BRN: ['brunei'], MDA: ['moldova'], TZA: ['tanzania'], BOL: ['bolivia'], VEN: ['venezuela'],
  };

  function buildNameIndex(countries) {
    const index = new Map();   // normalized alias -> Set(ids)
    const display = new Map(); // id -> main name
    const add = (key, id) => {
      if (!key) return;
      if (!index.has(key)) index.set(key, new Set());
      index.get(key).add(id);
    };
    for (const c of countries) {
      display.set(c.id, c.name);
      add(norm(c.name), c.id);
      for (const a of c.alt) {
        const k = norm(a);
        if (k.length >= 4) add(k, c.id);
      }
      for (const a of MANUAL_ALIASES[c.id] || []) add(norm(a), c.id);
    }
    // "Congo" alone stays ambiguous on purpose (handled by callers)
    return { index, display, keys: [...index.keys()] };
  }

  // -> { ids:[...], fuzzy:boolean } ; ids empty when nothing matched
  function matchName(input, nameIndex, allowedIds) {
    const q = norm(input);
    if (!q) return { ids: [], fuzzy: false };
    const ok = id => !allowedIds || allowedIds.has(id);
    const exact = nameIndex.index.get(q);
    if (exact) {
      const ids = [...exact].filter(ok);
      if (ids.length) return { ids, fuzzy: false };
    }
    const max = q.length >= 9 ? 2 : q.length >= 5 ? 1 : 0;
    if (!max) return { ids: [], fuzzy: false };
    let best = max + 1, found = new Set();
    for (const key of nameIndex.keys) {
      const d = lev(q, key, max);
      if (d > max) continue;
      const ids = [...nameIndex.index.get(key)].filter(ok);
      if (!ids.length) continue;
      if (d < best) { best = d; found = new Set(ids); }
      else if (d === best) ids.forEach(i => found.add(i));
    }
    return { ids: [...found], fuzzy: found.size > 0 };
  }

  // autocomplete suggestions: [{id,name}]
  function suggest(input, nameIndex, allowedIds, limit) {
    const q = norm(input);
    if (!q) return [];
    const scored = new Map();
    for (const key of nameIndex.keys) {
      let s = -1;
      if (key.startsWith(q)) s = 0;
      else if (key.split(' ').some(w => w.startsWith(q))) s = 1;
      else if (q.length >= 3 && key.includes(q)) s = 2;
      if (s < 0) continue;
      for (const id of nameIndex.index.get(key)) {
        if (allowedIds && !allowedIds.has(id)) continue;
        const main = norm(nameIndex.display.get(id)) === key ? -0.5 : 0;
        const val = s + main;
        if (!scored.has(id) || scored.get(id) > val) scored.set(id, val);
      }
    }
    return [...scored.entries()]
      .sort((a, b) => a[1] - b[1] || nameIndex.display.get(a[0]).localeCompare(nameIndex.display.get(b[0])))
      .slice(0, limit || 6)
      .map(([id]) => ({ id, name: nameIndex.display.get(id) }));
  }

  // ---------- graph ----------
  // countries: [{id, borders}] ; seaLinks: [[a,b,kind]] or null
  function makeGraph(countries, seaLinks) {
    const ids = countries.map(c => c.id);
    const index = new Map(ids.map((id, i) => [id, i]));
    const n = ids.length;
    const adj = Array.from({ length: n }, () => new Set());
    const kind = new Map(); // "a|b" -> 'land' | sea kind
    const edge = (a, b, k) => {
      if (!index.has(a) || !index.has(b) || a === b) return;
      adj[index.get(a)].add(index.get(b));
      adj[index.get(b)].add(index.get(a));
      const key = a < b ? a + '|' + b : b + '|' + a;
      if (!kind.has(key) || k === 'land') kind.set(key, k);
    };
    for (const c of countries) for (const b of c.borders) edge(c.id, b, 'land');
    for (const l of seaLinks || []) edge(l[0], l[1], l[2] || 'hop');
    const adjList = adj.map(s => [...s]);
    const dist = new Int16Array(n * n).fill(-1);
    for (let s = 0; s < n; s++) {
      const q = [s]; dist[s * n + s] = 0;
      for (let h = 0; h < q.length; h++) {
        const u = q[h];
        for (const v of adjList[u]) if (dist[s * n + v] < 0) { dist[s * n + v] = dist[s * n + u] + 1; q.push(v); }
      }
    }
    const g = {
      ids, index, n, adj: adjList, kind, dist,
      d: (a, b) => dist[index.get(a) * n + index.get(b)],
      neighbours: id => adjList[index.get(id)].map(i => ids[i]),
      edgeKind: (a, b) => kind.get(a < b ? a + '|' + b : b + '|' + a) || null,
    };
    return g;
  }

  // up to `limit` shortest paths a -> b, as arrays of ids
  function shortestPaths(g, a, b, limit) {
    limit = limit || 6;
    const D = g.d(a, b);
    if (D < 0) return [];
    const out = [];
    const walk = (u, path) => {
      if (out.length >= limit) return;
      if (u === g.index.get(b)) { out.push(path.map(i => g.ids[i])); return; }
      const du = g.d(g.ids[u], b);
      for (const v of g.adj[u]) {
        if (g.d(g.ids[v], b) === du - 1) walk(v, path.concat(v));
      }
    };
    walk(g.index.get(a), [g.index.get(a)]);
    return out;
  }

  // every country that sits on at least one shortest path a -> b (excluding a and b)
  function onShortest(g, a, b) {
    const D = g.d(a, b), res = [];
    if (D < 0) return res;
    for (const id of g.ids) {
      if (id === a || id === b) continue;
      if (g.d(a, id) >= 0 && g.d(id, b) >= 0 && g.d(a, id) + g.d(id, b) === D) res.push(id);
    }
    return res;
  }

  // how many extra steps a route through `id` costs (0 = on a shortest route)
  function detour(g, a, b, id) {
    const x = g.d(a, id), y = g.d(id, b);
    if (x < 0 || y < 0) return 99;
    return x + y - g.d(a, b);
  }

  // is `b` reachable from `a` using only the countries in `set` (which includes a and b)?
  function connected(g, a, b, set) {
    const seen = new Set([a]), q = [a];
    for (let h = 0; h < q.length; h++) {
      const u = q[h];
      if (u === b) return true;
      for (const v of g.neighbours(u)) if (set.has(v) && !seen.has(v)) { seen.add(v); q.push(v); }
    }
    return false;
  }

  // Brandes betweenness on the unweighted graph, normalized to 0..1
  function betweenness(g) {
    const n = g.n, bc = new Float64Array(n);
    for (let s = 0; s < n; s++) {
      const stack = [], pred = Array.from({ length: n }, () => []);
      const sigma = new Float64Array(n), dist = new Int32Array(n).fill(-1), delta = new Float64Array(n);
      sigma[s] = 1; dist[s] = 0;
      const q = [s];
      for (let h = 0; h < q.length; h++) {
        const v = q[h]; stack.push(v);
        for (const w of g.adj[v]) {
          if (dist[w] < 0) { dist[w] = dist[v] + 1; q.push(w); }
          if (dist[w] === dist[v] + 1) { sigma[w] += sigma[v]; pred[w].push(v); }
        }
      }
      while (stack.length) {
        const w = stack.pop();
        for (const v of pred[w]) delta[v] += (sigma[v] / sigma[w]) * (1 + delta[w]);
        if (w !== s) bc[w] += delta[w];
      }
    }
    const norm2 = (n - 1) * (n - 2);
    const out = {};
    g.ids.forEach((id, i) => { out[id] = bc[i] / norm2; });
    return out;
  }

  // share of all connected country pairs for which `id` sits on at least one shortest route
  function coverage(g) {
    const n = g.n, d = g.dist, cnt = new Float64Array(n);
    let pairs = 0;
    for (let a = 0; a < n; a++) for (let b = a + 1; b < n; b++) {
      const D = d[a * n + b];
      if (D < 2) { if (D > 0) pairs++; continue; }
      pairs++;
      for (let k = 0; k < n; k++) {
        if (k === a || k === b) continue;
        const x = d[a * n + k], y = d[k * n + b];
        if (x > 0 && y > 0 && x + y === D) cnt[k]++;
      }
    }
    const out = {};
    g.ids.forEach((id, i) => { out[id] = cnt[i] / pairs; });
    return out;
  }

  function pickRoute(g, pool, minInter, maxInter, rng, avoid) {
    rng = rng || Math.random;
    for (let tries = 0; tries < 4000; tries++) {
      const a = pool[Math.floor(rng() * pool.length)], b = pool[Math.floor(rng() * pool.length)];
      if (a === b) continue;
      const D = g.d(a, b);
      if (D < 0) continue;
      const inter = D - 1;
      if (inter < minInter || inter > maxInter) continue;
      if (avoid && avoid.has(a + '>' + b)) continue;
      return { start: a, end: b, steps: D };
    }
    return null;
  }

  // ---------- spaced repetition (Leitner boxes, picked by weight) ----------
  const SKILLS = ['locate', 'name', 'nbr', 'flag'];
  const BOX_WEIGHT = [9, 6, 4, 2.5, 1.2, 0.4];
  const MASTERED = 4;

  function emptyProgress() {
    return { v: 1, s: { locate: {}, name: {}, nbr: {}, flag: {} }, route: { games: 0, wins: 0, streak: 0, best: 0, extra: 0 } };
  }
  // fills in anything missing (older saves have no flag skill)
  function normalizeProgress(p) {
    const base = emptyProgress();
    p = Object.assign(base, p || {});
    p.s = Object.assign({}, base.s, p.s || {});
    for (const k of SKILLS) if (!p.s[k]) p.s[k] = {};
    p.route = Object.assign({}, base.route, p.route || {});
    return p;
  }
  function record(p, skill, id, result, now) {
    // result: 'right' | 'wrong' | 'hinted'
    const e = p.s[skill][id] || (p.s[skill][id] = { b: 0, n: 0, w: 0, t: 0 });
    e.n++; e.t = now;
    if (result === 'right') e.b = Math.min(5, e.b + 1);
    else if (result === 'wrong') { e.b = 0; e.w++; }
    return e;
  }
  function weight(p, skill, id, now) {
    const e = p.s[skill][id];
    if (!e) return 4.5; // unseen
    let w = BOX_WEIGHT[e.b];
    const days = (now - e.t) / 86400000;
    if (e.b >= 3 && days > 3) w *= 2;
    return w;
  }
  function pickCountry(p, skill, pool, now, rng, recent, smart) {
    rng = rng || Math.random;
    const cand = pool.filter(id => !(recent && recent.includes(id)));
    const list = cand.length ? cand : pool;
    if (!list.length) return null;
    if (!smart) return list[Math.floor(rng() * list.length)];
    const ws = list.map(id => weight(p, skill, id, now));
    let r = rng() * ws.reduce((a, b) => a + b, 0);
    for (let i = 0; i < list.length; i++) { r -= ws[i]; if (r <= 0) return list[i]; }
    return list[list.length - 1];
  }
  // 0 = unseen, 1..6 = box+1 ; combined = average over seen skills
  function mastery(p, id) {
    let sum = 0, cnt = 0;
    for (const s of SKILLS) { const e = p.s[s][id]; if (e) { sum += e.b; cnt++; } }
    return cnt ? sum / cnt : null;
  }


  // ---------- neighbour colouring (DSatur on land borders) ----------
  // ids: [id], neighbours: id -> [id]. Returns { id: colourIndex }. Islands with no
  // land neighbours cycle through the palette so they do not all look alike.
  function colorGraph(ids, neighbours, palette) {
    palette = palette || 6;
    const color = {};
    const linked = ids.filter(id => neighbours(id).length);
    const left = new Set(linked);
    while (left.size) {
      let best = null, bs = -1, bd = -1;
      for (const id of left) {
        const nb = neighbours(id);
        const used = new Set();
        for (const n of nb) if (color[n] !== undefined) used.add(color[n]);
        if (used.size > bs || (used.size === bs && nb.length > bd)) { best = id; bs = used.size; bd = nb.length; }
      }
      const used = new Set();
      for (const n of neighbours(best)) if (color[n] !== undefined) used.add(color[n]);
      let c = 0; while (used.has(c)) c++;
      color[best] = c; left.delete(best);
    }
    let k = 0;
    for (const id of ids) if (!neighbours(id).length) color[id] = (k++) % palette;
    return color;
  }

  // ---------- label placement ----------
  // items: [{ id, x, y, w, h, size, pri }] in screen px (x,y = anchor, w,h = label box,
  // size = on-screen size of the country, pri = higher goes first).
  // Returns placed labels [{ ...item, cx, cy, ax, ay, leader }] that never overlap and stay
  // inside W x H. Countries too small for their label get it moved beside them with a leader.
  function layoutLabels(items, W, H, opts) {
    opts = opts || {};
    const pad = opts.pad == null ? 2 : opts.pad;
    const placed = [];
    const rects = (opts.blocked || []).map(r => ({ x: r.x, y: r.y, w: r.w, h: r.h }));
    const free = (x, y, w, h) => {
      if (x < 2 || y < 2 || x + w > W - 2 || y + h > H - 2) return false;
      for (const r of rects) {
        if (x < r.x + r.w + pad && x + w + pad > r.x && y < r.y + r.h + pad && y + h + pad > r.y) return false;
      }
      return true;
    };
    const sorted = items.slice().sort((a, b) => (b.pri || 0) - (a.pri || 0));
    const DIRS = [[0, -1], [0, 1], [1, 0], [-1, 0], [1, -1], [-1, -1], [1, 1], [-1, 1]];
    for (const it of sorted) {
      if (it.x < -30 || it.x > W + 30 || it.y < -30 || it.y > H + 30) continue;
      const w = it.w, h = it.h || 16;
      const cands = [[0, 0, false]];
      if (it.size >= w * 0.85) {
        const s = it.size;
        for (const [dx, dy] of DIRS) cands.push([dx * s * 0.22, dy * Math.min(s, w) * 0.14, false]);
      } else {
        for (let level = 0; level < 4; level++) {
          const gap = Math.max(it.size / 2, 5) + 3 + level * h * 0.95;
          for (const [dx, dy] of DIRS) cands.push([dx * (dx ? w / 2 + gap : 0), dy * (dy ? h / 2 + gap : 0), true]);
        }
      }
      for (const [ox, oy, leader] of cands) {
        const cx = it.x + ox, cy = it.y + oy;
        const rx = cx - w / 2, ry = cy - h / 2;
        if (!free(rx, ry, w, h)) continue;
        rects.push({ x: rx, y: ry, w, h });
        placed.push(Object.assign({}, it, { cx, cy, ax: it.x, ay: it.y, leader }));
        break;
      }
    }
    return placed;
  }

  // ---------- flag quiz options ----------
  const CONFUSABLE = [
    ['ROU', 'TCD', 'AND', 'MDA'], ['IDN', 'MCO', 'POL'], ['IRL', 'CIV', 'ITA', 'MEX'],
    ['SVK', 'SVN', 'RUS', 'SRB', 'HRV'], ['NLD', 'LUX', 'FRA', 'RUS'], ['AUS', 'NZL', 'FJI', 'TUV'],
    ['SEN', 'MLI', 'GIN', 'CMR', 'GHA', 'BFA'], ['DNK', 'NOR', 'SWE', 'FIN', 'ISL'],
    ['VEN', 'COL', 'ECU'], ['BOL', 'GHA', 'LTU', 'ETH'], ['EGY', 'YEM', 'SYR', 'IRQ', 'SDN'],
    ['ARE', 'KWT', 'JOR', 'PSE', 'SDN'], ['LBN', 'AUT', 'LVA', 'PER', 'CAN'], ['HND', 'SLV', 'NIC', 'ARG', 'URY'],
    ['USA', 'LBR', 'MYS', 'CUB', 'CHL'], ['TUR', 'TUN', 'PAK', 'DZA', 'MRT'], ['BRA', 'PRT', 'GNB', 'MOZ'],
    ['BEL', 'DEU', 'UKR', 'GAB'], ['CHN', 'VNM', 'MAR'], ['NER', 'IND', 'IRL'], ['SMR', 'ESP', 'GTM'],
  ];
  function shuffle(a, rng) {
    rng = rng || Math.random;
    for (let i = a.length - 1; i > 0; i--) { const j = Math.floor(rng() * (i + 1)); [a[i], a[j]] = [a[j], a[i]]; }
    return a;
  }
  // n options (answer + n-1 distractors), shuffled. Look-alike flags first (up to 2), then
  // neighbours in the same sub-region, then anything in the pool.
  function flagOptions(id, countries, pool, rng, n) {
    n = n || 4;
    const byId = new Map(countries.map(c => [c.id, c]));
    const out = new Set();
    const add = x => { if (x !== id && byId.has(x) && out.size < n - 1) out.add(x); };
    let conf = 0;
    for (const g of CONFUSABLE) {
      if (!g.includes(id)) continue;
      for (const x of shuffle(g.slice(), rng)) { if (conf < 2 && x !== id && !out.has(x) && byId.has(x)) { add(x); conf++; } }
    }
    const me = byId.get(id);
    shuffle(pool.filter(x => byId.get(x) && byId.get(x).sub === me.sub), rng).forEach(add);
    shuffle(pool.slice(), rng).forEach(add);
    shuffle(countries.map(c => c.id), rng).forEach(add);
    return shuffle([id, ...out], rng);
  }

  return {
    norm, lev, buildNameIndex, matchName, suggest,
    colorGraph, layoutLabels, flagOptions, shuffle, CONFUSABLE, makeGraph, shortestPaths, onShortest, detour, connected, betweenness, coverage, pickRoute,
    SKILLS, MASTERED, emptyProgress, normalizeProgress, record, weight, pickCountry, mastery,
  };
});
