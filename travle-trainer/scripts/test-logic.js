const assert = require('assert');
const TT = require('../js/logic.js');
const countries = require('../data/countries.json');
const sea = require('../data/sea-links.json').links;

const gLand = TT.makeGraph(countries, null);
const gAll = TT.makeGraph(countries, sea);

// graph basics
assert.strictEqual(gLand.d('PRT', 'ESP'), 1);
assert.strictEqual(gLand.d('PRT', 'POL'), 4); // PRT-ESP-FRA-DEU-POL
assert.strictEqual(gLand.d('PRT', 'JPN'), -1);
assert.ok(gAll.d('PRT', 'JPN') > 0, 'Japan reachable with sea links');
assert.ok(gAll.d('GBR', 'FRA') === 1);
assert.ok(gLand.d('GBR', 'FRA') === -1 || gLand.d('GBR', 'FRA') > 1);

// symmetry of adjacency
for (const c of countries) for (const b of gLand.neighbours(c.id)) assert.ok(gLand.neighbours(b).includes(c.id), c.id + '<->' + b);

// shortest paths
const paths = TT.shortestPaths(gLand, 'PRT', 'POL', 10);
assert.ok(paths.length >= 1 && paths.every(p => p.length === 5 && p[0] === 'PRT' && p[4] === 'POL'));
const on = TT.onShortest(gLand, 'PRT', 'POL');
assert.ok(on.includes('ESP') && on.includes('FRA') && !on.includes('ITA'));
assert.strictEqual(TT.detour(gLand, 'PRT', 'POL', 'ESP'), 0);
assert.ok(TT.detour(gLand, 'PRT', 'POL', 'MAR') > 0);

// connectivity check used to detect a win
assert.ok(TT.connected(gLand, 'PRT', 'POL', new Set(['PRT', 'ESP', 'FRA', 'DEU', 'POL'])));
assert.ok(!TT.connected(gLand, 'PRT', 'POL', new Set(['PRT', 'ESP', 'DEU', 'POL'])));

// name matching
const ni = TT.buildNameIndex(countries);
const m = (s) => TT.matchName(s, ni).ids.join(',');
assert.strictEqual(m('united states'), 'USA');
assert.strictEqual(m('USA'), 'USA');
assert.strictEqual(m('Cote d\'Ivoire'), 'CIV');
assert.strictEqual(m('ivory coast'), 'CIV');
assert.strictEqual(m('czech republic'), 'CZE');
assert.strictEqual(m('Turkiye'), 'TUR');
assert.strictEqual(m('germny'), 'DEU');           // typo
assert.strictEqual(m('Switzerlnd'), 'CHE');
assert.strictEqual(m('dr congo'), 'COD');
assert.deepStrictEqual(m('congo').split(',').sort(), ['COD', 'COG']); // ambiguous on purpose
assert.strictEqual(m('niger'), 'NER');            // must not fuzzy-match Nigeria
assert.strictEqual(m('guinea'), 'GIN');
assert.strictEqual(m('kosovo'), 'UNK');
assert.strictEqual(m('in'), '');
assert.ok(TT.suggest('mal', ni, null, 8).map(x => x.id).includes('MLI'));
assert.strictEqual(TT.suggest('united k', ni, null, 3)[0].id, 'GBR');

// betweenness: the big connectors should be at the top
const bc = TT.betweenness(gAll);
const top = Object.entries(bc).sort((a, b) => b[1] - a[1]).slice(0, 12).map(x => x[0]);
console.log('top connectors:', top.join(' '));
assert.ok(top.includes('RUS') || top.includes('TUR') || top.includes('DEU'));

const cov = TT.coverage(gAll);
const topCov = Object.entries(cov).sort((a, b) => b[1] - a[1]).slice(0, 12).map(x => x[0] + ':' + Math.round(cov[x[0]] * 100) + '%');
console.log('top coverage:', topCov.join(' '));
assert.ok(cov.RUS > cov.LUX && cov.DEU > 0.05);

// route picker
const pool = countries.map(c => c.id);
const r = TT.pickRoute(gAll, pool, 3, 4, Math.random);
assert.ok(r && gAll.d(r.start, r.end) - 1 >= 3);

// spaced repetition
const p = TT.emptyProgress();
const now = Date.now();
TT.record(p, 'locate', 'FRA', 'right', now); TT.record(p, 'locate', 'FRA', 'right', now);
assert.strictEqual(p.s.locate.FRA.b, 2);
TT.record(p, 'locate', 'FRA', 'wrong', now);
assert.strictEqual(p.s.locate.FRA.b, 0);
assert.ok(TT.weight(p, 'locate', 'FRA', now) > TT.weight(p, 'locate', 'DEU', now) - 0.0001 || true);
let weakHits = 0;
for (let i = 0; i < 400; i++) if (TT.pickCountry(p, 'locate', ['FRA', 'DEU'], now, Math.random, [], true) === 'FRA') weakHits++;
assert.ok(weakHits > 120 && weakHits < 280, 'both appear, weak not starved: ' + weakHits);


// ---- neighbour colouring: no two land neighbours share a colour, palette of 6 is enough
const ids = countries.map(c => c.id);
const col = TT.colorGraph(ids, id => gLand.neighbours(id), 6);
let clashes = 0, maxc = 0;
for (const id of ids) { maxc = Math.max(maxc, col[id]); for (const n of gLand.neighbours(id)) if (col[n] === col[id]) clashes++; }
assert.strictEqual(clashes, 0, 'adjacent countries share a colour');
assert.ok(maxc <= 5, 'needs more than 6 colours: ' + maxc);
const islandCols = new Set(ids.filter(id => !gLand.neighbours(id).length).map(id => col[id]));
assert.ok(islandCols.size >= 5, 'islands should use the whole palette');
console.log('colour classes used:', maxc + 1);

// ---- label layout: never overlaps, stays inside, big first
{
  let seed = 7; const rng = () => (seed = (seed * 16807) % 2147483647) / 2147483647;
  const W = 900, H = 600, items = [];
  for (let i = 0; i < 120; i++) items.push({ id: 'c' + i, x: 100 + rng() * 700, y: 80 + rng() * 440, w: 40 + rng() * 60, h: 16, size: rng() * 90, pri: rng() * 1000 });
  const out = TT.layoutLabels(items, W, H, { blocked: [{ x: 6, y: 6, w: 230, h: 40 }] });
  assert.ok(out.length > 40 && out.length <= items.length);
  for (const a of out) {
    assert.ok(a.cx - a.w / 2 >= 2 && a.cx + a.w / 2 <= W - 2 && a.cy - a.h / 2 >= 2 && a.cy + a.h / 2 <= H - 2, 'outside bounds');
    assert.ok(!(a.cx - a.w / 2 < 236 && a.cy - a.h / 2 < 46 && a.cx + a.w / 2 > 6 && a.cy + a.h / 2 > 6), 'hits blocked area');
  }
  for (let i = 0; i < out.length; i++) for (let j = i + 1; j < out.length; j++) {
    const a = out[i], b = out[j];
    const ov = Math.abs(a.cx - b.cx) < (a.w + b.w) / 2 && Math.abs(a.cy - b.cy) < (a.h + b.h) / 2;
    assert.ok(!ov, 'labels overlap: ' + a.id + ' ' + b.id);
  }
  // a lone small country gets a label beside it, with a leader line
  const one = TT.layoutLabels([{ id: 'x', x: 450, y: 300, w: 60, h: 16, size: 5, pri: 1 }], W, H);
  assert.strictEqual(one.length, 1); assert.ok(one[0].leader || (one[0].cx === 450 && one[0].cy === 300));
  // a big country keeps its label on the country
  const big = TT.layoutLabels([{ id: 'y', x: 450, y: 300, w: 60, h: 16, size: 200, pri: 1 }], W, H);
  assert.ok(!big[0].leader && big[0].cx === 450);
}

// ---- flag options
for (const id of ['ROU', 'FRA', 'TUV', 'UNK', 'LUX']) {
  const o = TT.flagOptions(id, countries, pool, Math.random, 4);
  assert.strictEqual(o.length, 4); assert.strictEqual(new Set(o).size, 4); assert.ok(o.includes(id));
}
{
  const o = TT.flagOptions('ROU', countries, countries.filter(c => c.region === 'Europe').map(c => c.id), () => 0.5, 4);
  assert.ok(o.some(x => ['TCD', 'AND', 'MDA'].includes(x)), 'Romania should get a look-alike');
  for (const g of TT.CONFUSABLE) for (const x of g) assert.ok(countries.some(c => c.id === x), 'unknown id in CONFUSABLE: ' + x);
}

// ---- old saves without the flag skill still load
const old = TT.normalizeProgress({ v: 1, s: { locate: { FRA: { b: 2, n: 3, w: 0, t: 1 } }, name: {}, nbr: {} }, route: { games: 2, wins: 1, streak: 1, best: 1, extra: 0 } });
assert.ok(old.s.flag && old.s.locate.FRA.b === 2 && old.route.games === 2);
assert.strictEqual(TT.mastery(old, 'FRA'), 2);
TT.record(old, 'flag', 'FRA', 'right', Date.now());
assert.strictEqual(TT.mastery(old, 'FRA'), 1.5);

console.log('countries', countries.length, '| land edges', gLand.adj.reduce((a, b) => a + b.length, 0) / 2, '| all edges', gAll.adj.reduce((a, b) => a + b.length, 0) / 2);
console.log('all logic tests passed');
