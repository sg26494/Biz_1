// Builds data/countries.json and data/world.json from world-countries + world-atlas.
// Run: node scripts/build-data.js
const fs = require('fs');
const path = require('path');
const wc = require('world-countries');
const topo = require('world-atlas/countries-50m.json');

const ROOT = path.join(__dirname, '..');
const EXTRA = new Set(['TWN', 'PSE', 'ESH', 'UNK']); // not UN members, but usually in the game

const included = wc.filter(c => c.independent === true || EXTRA.has(c.cca3));
const ids = new Set(included.map(c => c.cca3));

const REGION = c => {
  const s = c.subregion;
  if (c.region === 'Europe') return 'Europe';
  if (c.region === 'Africa') return 'Africa';
  if (c.region === 'Oceania') return 'Oceania';
  if (s === 'Western Asia') return 'Middle East';
  if (s === 'Central Asia' || s === 'Southern Asia') return 'Central & South Asia';
  if (s === 'Eastern Asia' || s === 'South-Eastern Asia') return 'East & SE Asia';
  if (s === 'Caribbean') return 'Caribbean';
  if (s === 'South America') return 'South America';
  return 'North America'; // Northern + Central America
};

const NAME_FIX = { COG: 'Republic of the Congo', COD: 'DR Congo', UNK: 'Kosovo', CZE: 'Czechia' };

// symmetric land borders, restricted to included countries
const adj = {};
for (const c of included) adj[c.cca3] = new Set();
for (const c of included) {
  for (const b of c.borders) {
    if (!ids.has(b)) continue;
    adj[c.cca3].add(b);
    adj[b].add(c.cca3);
  }
}
// quirks: drop borders that are invisible on a map (overseas bits) or wrong for learners
const drop = [['FRA', 'NLD'], ['GBR', 'CYP']];
for (const [a, b] of drop) { adj[a]?.delete(b); adj[b]?.delete(a); }
// Kosovo (wc lists it as UNK on its neighbours already; make it symmetric)
for (const n of ['ALB', 'MKD', 'MNE', 'SRB']) { adj.UNK.add(n); adj[n].add('UNK'); }

const countries = included.map(c => {
  const geom = topo.objects.countries.geometries.find(g => g.id === c.ccn3 && c.ccn3);
  const isKosovo = c.cca3 === 'UNK';
  return {
    id: c.cca3,
    name: NAME_FIX[c.cca3] || c.name.common,
    official: c.name.official,
    region: REGION(c),
    sub: c.subregion || '',
    capital: (c.capital && c.capital[0]) || '',
    lat: c.latlng[0],
    lng: c.latlng[1],
    area: c.area,
    extra: EXTRA.has(c.cca3),
    hasShape: !!geom || isKosovo,
    alt: [...new Set([c.name.common, c.name.official, ...(c.altSpellings || [])])],
    borders: [...adj[c.cca3]].sort(),
  };
});

// world geometry: tag each atlas shape with a3 (our id) or null for background territories
const geoms = topo.objects.countries.geometries.filter(g => g.id !== '010'); // drop Antarctica
const byCcn3 = {};
for (const c of included) if (c.ccn3) byCcn3[c.ccn3] = c.cca3;
for (const g of geoms) {
  const name = g.properties && g.properties.name;
  let a3 = g.id ? byCcn3[g.id] : null;
  if (!a3 && name === 'Kosovo') a3 = 'UNK';
  if (!a3 && name === 'Somaliland') a3 = 'SOM';
  if (!a3 && name === 'N. Cyprus') a3 = 'CYP';
  g.properties = { n: name, a: a3 || null };
  delete g.id;
}
topo.objects.countries.geometries = geoms;

fs.writeFileSync(path.join(ROOT, 'data/countries.json'), JSON.stringify(countries));
fs.writeFileSync(path.join(ROOT, 'data/world.json'), JSON.stringify(topo));

const noShape = countries.filter(c => !c.hasShape).map(c => c.id);
console.log('countries:', countries.length, '| no shape (ring marker only):', noShape.join(','));
console.log('no land neighbours:', countries.filter(c => !c.borders.length).length);
console.log('regions:', JSON.stringify(countries.reduce((m, c) => (m[c.region] = (m[c.region] || 0) + 1, m), {})));

// ---- flags: data/flags.json { ID: "<svg…>" } from country-flag-icons (3x2) ----
const wcById = new Map(wc.map(c => [c.cca3, c]));
const flagDir = path.join(ROOT, 'node_modules/country-flag-icons/3x2');
const flags = {};
const noFlag = [];
for (const c of countries) {
  const a2 = c.id === 'UNK' ? 'XK' : wcById.get(c.id).cca2;
  const f = path.join(flagDir, a2 + '.svg');
  if (!fs.existsSync(f)) { noFlag.push(c.id); continue; }
  flags[c.id] = fs.readFileSync(f, 'utf8').replace(/\s*\n\s*/g, '').trim();
}
fs.writeFileSync(path.join(ROOT, 'data/flags.json'), JSON.stringify(flags));
console.log('flags:', Object.keys(flags).length, '| missing:', noFlag.join(',') || 'none');
