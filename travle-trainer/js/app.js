// Travle Trainer UI. Depends on d3, topojson-client and logic.js (global TT).
(function () {
  'use strict';

  const STORE_KEY = 'travle-trainer.v1';
  const ASIA = new Set(['Middle East', 'Central & South Asia', 'East & SE Asia']);
  const subIn = (...names) => c => names.includes(c.sub);
  // group: World, Continents or Sub-regions. box = [west, south, east, north] used for the first view.
  const REGIONS = {
    'World': { group: 'World', lon: 10, box: null, test: () => true },
    'Europe': { group: 'Continents', lon: 15, box: [-25, 34, 45, 71], test: c => c.region === 'Europe' },
    'Asia': { group: 'Continents', lon: 88, box: [26, -11, 146, 56], test: c => ASIA.has(c.region) },
    'Africa': { group: 'Continents', lon: 20, box: [-20, -36, 52, 38], test: c => c.region === 'Africa' },
    'North America': { group: 'Continents', lon: -100, box: [-170, 5, -50, 72], test: c => c.region === 'North America' || c.region === 'Caribbean' },
    'South America': { group: 'Continents', lon: -60, box: [-85, -57, -33, 14], test: c => c.region === 'South America' },
    'Oceania': { group: 'Continents', lon: 165, box: [112, -48, 220, 14], test: c => c.region === 'Oceania' },
    'Western & Central Europe': { group: 'Sub-regions', lon: 8, box: [-12, 41, 26, 59], test: subIn('Western Europe', 'Central Europe') },
    'Northern Europe': { group: 'Sub-regions', lon: 5, box: [-25, 52, 32, 72], test: subIn('Northern Europe') },
    'Southern Europe & Balkans': { group: 'Sub-regions', lon: 12, box: [-11, 34, 36, 49], test: subIn('Southern Europe', 'Southeast Europe') },
    'Eastern Europe': { group: 'Sub-regions', lon: 32, box: [20, 43, 50, 62], test: subIn('Eastern Europe') },
    'North Africa': { group: 'Sub-regions', lon: 10, box: [-18, 8, 40, 38], test: subIn('Northern Africa') },
    'West Africa': { group: 'Sub-regions', lon: 0, box: [-26, 3, 17, 28], test: subIn('Western Africa') },
    'Central Africa': { group: 'Sub-regions', lon: 18, box: [5, -19, 36, 24], test: subIn('Middle Africa') },
    'East Africa': { group: 'Sub-regions', lon: 42, box: [24, -28, 60, 18], test: subIn('Eastern Africa') },
    'Southern Africa': { group: 'Sub-regions', lon: 24, box: [10, -36, 36, -16], test: subIn('Southern Africa') },
    'Middle East': { group: 'Sub-regions', lon: 45, box: [25, 10, 65, 43], test: c => c.region === 'Middle East' },
    'Central & South Asia': { group: 'Sub-regions', lon: 75, box: [44, 4, 100, 56], test: c => c.region === 'Central & South Asia' },
    'East & SE Asia': { group: 'Sub-regions', lon: 115, box: [90, -12, 150, 55], test: c => c.region === 'East & SE Asia' },
    'Northern & Central America': { group: 'Sub-regions', lon: -100, box: [-170, 6, -52, 72], test: c => c.region === 'North America' },
    'Caribbean': { group: 'Sub-regions', lon: -75, box: [-90, 8, -58, 28], test: c => c.region === 'Caribbean' },
    'Pacific Islands': { group: 'Sub-regions', lon: 165, box: [125, -24, 215, 18], test: c => c.region === 'Oceania' && c.sub !== 'Australia and New Zealand' },
  };
  const DIFFICULTY = { short: [1, 2], medium: [3, 4], long: [5, 8] };

  const $ = (s, r) => (r || document).querySelector(s);
  const esc = s => String(s).replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
  const fmt = n => Math.round(n).toLocaleString('en-US');

  function embedded(name) {
    const el = document.getElementById('data-' + name);
    return el ? JSON.parse(el.textContent) : null;
  }
  async function loadJSON(name) {
    const e = embedded(name);
    if (e) return e;
    const r = await fetch('data/' + name + '.json', { cache: 'no-store' });
    if (!r.ok) throw new Error('Could not load data/' + name + '.json');
    return r.json();
  }

  // ---------- persistence ----------
  let progress = TT.emptyProgress();
  let settings = { region: 'World', smart: true, sea: true, extra: true, zoomTo: false, difficulty: 'medium', bonus: 5, mode: 'learn',
    flags: true, learnNames: true, learnFlags: false, flagQ: 'type', colorBy: {} };
  function loadStore() {
    try {
      const raw = localStorage.getItem(STORE_KEY);
      if (!raw) return;
      const d = JSON.parse(raw);
      if (d.progress && d.progress.v === 1) progress = TT.normalizeProgress(d.progress);
      if (d.settings) Object.assign(settings, d.settings);
      if (!settings.colorBy || typeof settings.colorBy !== 'object') settings.colorBy = {};
      if (!REGIONS[settings.region]) settings.region = 'World';
    } catch (e) { /* storage unavailable */ }
  }
  function saveStore() {
    try { localStorage.setItem(STORE_KEY, JSON.stringify({ progress, settings })); } catch (e) { /* ignore */ }
  }
  function applyTheme(t) {
    if (t) document.documentElement.dataset.theme = t; else delete document.documentElement.dataset.theme;
  }
  function loadTheme() { try { return localStorage.getItem(STORE_KEY + '.theme'); } catch (e) { return null; } }
  function saveTheme(t) { try { localStorage.setItem(STORE_KEY + '.theme', t); } catch (e) { /* ignore */ } }

  function km(a, b) {
    const R = 6371, r = Math.PI / 180;
    const dLat = (b.lat - a.lat) * r, dLng = (b.lng - a.lng) * r;
    const x = Math.sin(dLat / 2) ** 2 + Math.cos(a.lat * r) * Math.cos(b.lat * r) * Math.sin(dLng / 2) ** 2;
    return 2 * R * Math.asin(Math.sqrt(x));
  }

  async function main() {
    loadStore();
    const th = loadTheme(); if (th) applyTheme(th);
    const [countries, world, seaDoc, flagSvgs] = await Promise.all([loadJSON('countries'), loadJSON('world'), loadJSON('sea-links'), loadJSON('flags')]);
    const byId = new Map(countries.map(c => [c.id, c]));
    const nameIndex = TT.buildNameIndex(countries);
    const gLand = TT.makeGraph(countries, null);
    const gAll = TT.makeGraph(countries, seaDoc.links);
    const graph = () => (settings.sea ? gAll : gLand);
    const bcCache = {};
    const connectors = () => bcCache[settings.sea] || (bcCache[settings.sea] = TT.coverage(graph()));
    const nm = id => byId.get(id).name;

    // flags: inline SVG data URIs, so they work offline
    const flagUri = new Map();
    const flagSrc = id => {
      let u = flagUri.get(id);
      if (!u && flagSvgs[id]) { u = 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(flagSvgs[id]); flagUri.set(id, u); }
      return u || '';
    };
    const flagHTML = (id, cls, alt) => (flagSvgs[id] ? `<img class="flag ${cls || ''}" src="${flagSrc(id)}" alt="${esc(alt || '')}" draggable="false">` : '');
    const flagInline = (id, cls) => (settings.flags ? flagHTML(id, cls || 'inline') + ' ' : '');
    // name + flag + capital, shown after an answer so they are learned together
    const answerCard = id => {
      const c = byId.get(id);
      return `<div class="answer">${settings.flags ? flagHTML(id, 'md') : ''}<div class="atext"><b>${esc(c.name)}</b>${c.capital ? `<span>Capital: ${esc(c.capital)}</span>` : ''}<span>${esc(c.sub || c.region)}</span></div></div>`;
    };

    // ---------- map ----------
    const svg = d3.select('#map');
    const gRoot = svg.append('g');
    const gShapes = gRoot.append('g');
    const gRings = gRoot.append('g');
    const gLabels = gRoot.append('g');
    // name labels for Learn mode live outside the zoomed group so they stay a constant size
    const gOver = svg.append('g').attr('class', 'overlay').style('pointer-events', 'none');
    let anchors = new Map(), sizeMain = new Map();
    let overlayRAF = 0;
    let modeKey = settings.mode;
    const DEFAULT_COLORS = { learn: 'neighbour' };
    const features = topojson.feature(world, world.objects.countries).features;

    // main-land bounds geometry per feature (ignore far-off islands when zooming to a country)
    const mainGeom = new Map();
    const featsById = new Map();
    features.forEach(f => {
      const a = f.properties.a;
      if (!a) return;
      if (!featsById.has(a)) featsById.set(a, []);
      featsById.get(a).push(f);
    });
    function mainGeometry(f) {
      if (f.geometry.type !== 'MultiPolygon') return f.geometry;
      const polys = f.geometry.coordinates.map(c => ({ type: 'Polygon', coordinates: c }));
      const areas = polys.map(p => d3.geoArea(p));
      const max = Math.max(...areas);
      const keep = polys.filter((p, i) => areas[i] >= max * 0.3).map(p => p.coordinates);
      return { type: 'MultiPolygon', coordinates: keep };
    }
    features.forEach(f => mainGeom.set(f, mainGeometry(f)));

    const tiny = countries.filter(c => c.area < 7000 || !c.hasShape);
    const pathsById = new Map();
    const ringsById = new Map();
    const pathSel = gShapes.selectAll('path').data(features).join('path')
      .attr('class', f => 'c ' + (f.properties.a ? 'play' : 'bg'))
      .attr('data-id', f => f.properties.a || null)
      .attr('data-bg', f => (f.properties.a ? null : f.properties.n || 'Territory'));
    pathSel.each(function (f) {
      const a = f.properties.a; if (!a) return;
      if (!pathsById.has(a)) pathsById.set(a, []);
      pathsById.get(a).push(this);
    });
    const ringSel = gRings.selectAll('circle').data(tiny).join('circle')
      .attr('class', 'ring').attr('data-id', c => c.id);
    ringSel.each(function (c) { ringsById.set(c.id, this); });

    let W = 0, H = 0, proj, geoPath, curK = 1;
    let ringSize = new Map();
    const zoom = d3.zoom().scaleExtent([1, 80]).on('zoom', e => {
      curK = e.transform.k;
      gRoot.attr('transform', e.transform);
      updateRings(); updateLabels(); scheduleOverlay();
    });
    svg.call(zoom).on('dblclick.zoom', null);

    function updateRings() {
      ringSel.attr('r', 7 / curK).style('stroke-width', 1.4 / curK)
        .style('display', c => ((ringSize.get(c.id) || 0) * curK < 14 ? null : 'none'));
    }

    function project() {
      const reg = REGIONS[settings.region];
      proj = d3.geoNaturalEarth1().rotate([-reg.lon, 0]).fitExtent([[6, 6], [W - 6, H - 6]], { type: 'Sphere' });
      geoPath = d3.geoPath(proj);
      pathSel.attr('d', geoPath);
      ringSize = new Map();
      tiny.forEach(c => {
        let size = 0;
        (featsById.get(c.id) || []).forEach(f => {
          const b = geoPath.bounds(f);
          size = Math.max(size, b[1][0] - b[0][0], b[1][1] - b[0][1]);
        });
        ringSize.set(c.id, size);
      });
      ringSel.each(function (c) {
        const p = proj([c.lng, c.lat]);
        d3.select(this).attr('cx', p[0]).attr('cy', p[1]);
      });
      zoom.translateExtent([[-W * 0.3, -H * 0.3], [W * 1.3, H * 1.3]]);
      // label anchors: centre of the biggest piece of each country, and its on-screen size at zoom 1
      anchors = new Map(); sizeMain = new Map();
      countries.forEach(c => {
        let best = null, bestA = -1, size = 0;
        (featsById.get(c.id) || []).forEach(f => {
          const b = geoPath.bounds({ type: 'Feature', geometry: mainGeom.get(f) });
          size = Math.max(size, b[1][0] - b[0][0], b[1][1] - b[0][1]);
          const polys = f.geometry.type === 'MultiPolygon' ? f.geometry.coordinates.map(co => ({ type: 'Polygon', coordinates: co })) : [f.geometry];
          polys.forEach(p => { const a = d3.geoArea(p); if (a > bestA) { bestA = a; best = p; } });
        });
        let a = best ? geoPath.centroid(best) : null;
        if (!a || !isFinite(a[0]) || !isFinite(a[1])) a = proj([c.lng, c.lat]);
        anchors.set(c.id, a); sizeMain.set(c.id, size);
      });
      scheduleOverlay();
    }
    function transformFor(boxPx, fill, maxK) {
      const bw = Math.max(boxPx[1][0] - boxPx[0][0], 1), bh = Math.max(boxPx[1][1] - boxPx[0][1], 1);
      const k = Math.max(1, Math.min(maxK || 60, fill * Math.min(W / bw, H / bh)));
      const cx = (boxPx[0][0] + boxPx[1][0]) / 2, cy = (boxPx[0][1] + boxPx[1][1]) / 2;
      return d3.zoomIdentity.translate(W / 2 - k * cx, H / 2 - k * cy).scale(k);
    }
    function goTo(t, animate) {
      if (animate === false) svg.call(zoom.transform, t);
      else svg.transition().duration(550).call(zoom.transform, t);
    }
    function viewRegion(animate) {
      const reg = REGIONS[settings.region];
      if (!reg.box) return goTo(d3.zoomIdentity, animate);
      const [w, s, e, n] = reg.box;
      const pts = [[w, s], [e, s], [w, n], [e, n], [(w + e) / 2, n], [(w + e) / 2, s]].map(p => proj(p));
      const xs = pts.map(p => p[0]), ys = pts.map(p => p[1]);
      goTo(transformFor([[Math.min(...xs), Math.min(...ys)], [Math.max(...xs), Math.max(...ys)]], 0.92, 30), animate);
    }
    function boundsOf(ids) {
      let x0 = Infinity, y0 = Infinity, x1 = -Infinity, y1 = -Infinity;
      ids.forEach(id => {
        const c = byId.get(id);
        const fs = featsById.get(id) || [];
        if (fs.length) fs.forEach(f => {
          const b = geoPath.bounds({ type: 'Feature', geometry: mainGeom.get(f) });
          x0 = Math.min(x0, b[0][0]); y0 = Math.min(y0, b[0][1]); x1 = Math.max(x1, b[1][0]); y1 = Math.max(y1, b[1][1]);
        });
        else { const p = proj([c.lng, c.lat]); x0 = Math.min(x0, p[0] - 4); y0 = Math.min(y0, p[1] - 4); x1 = Math.max(x1, p[0] + 4); y1 = Math.max(y1, p[1] + 4); }
      });
      return [[x0, y0], [x1, y1]];
    }
    function viewIds(ids, fill) {
      const b = boundsOf(ids);
      goTo(transformFor(b, fill || 0.55, 40));
    }
    $('#zin').onclick = () => svg.transition().duration(250).call(zoom.scaleBy, 1.6);
    $('#zout').onclick = () => svg.transition().duration(250).call(zoom.scaleBy, 1 / 1.6);
    $('#zreset').onclick = () => viewRegion();

    // marks + labels
    let marks = new Map();    // id -> class
    let pulses = new Set();
    let dimSet = new Set();   // countries faded out (Learn mode, outside the chosen region)
    let labels = new Map();   // id -> text
    const labelSel = () => gLabels.selectAll('text');
    function repaint() {
      pathsById.forEach((nodes, id) => {
        const cls = 'c play' + (marks.has(id) ? ' ' + marks.get(id) : '') + (dimSet.has(id) ? ' dim' : '');
        nodes.forEach(n => { if (n.getAttribute('class') !== cls) n.setAttribute('class', cls); });
      });
      ringsById.forEach((n, id) => {
        const cls = 'ring' + (marks.has(id) ? ' ' + marks.get(id) : '') + (pulses.has(id) ? ' pulse' : '') + (dimSet.has(id) ? ' dim' : '');
        if (n.getAttribute('class') !== cls) n.setAttribute('class', cls);
      });
    }
    function setLabels(map) {
      labels = map;
      const data = [...labels.entries()].map(([id, text]) => ({ id, text }));
      gLabels.selectAll('text').data(data, d => d.id).join('text').attr('class', 'lbl').text(d => d.text);
      updateLabels();
    }
    function updateLabels() {
      gLabels.selectAll('text').each(function (d) {
        const c = byId.get(d.id), p = proj([c.lng, c.lat]);
        d3.select(this).attr('x', p[0]).attr('y', p[1] - 9 / curK).attr('font-size', 12 / curK).style('stroke-width', 3.2 / curK);
      });
    }
    function setMarks(m, pulseIds) { marks = m; pulses = new Set(pulseIds || []); repaint(); }
    function clearMap() { setMarks(new Map()); setLabels(new Map()); }
    function setDim(set) { dimSet = set; repaint(); }

    // ---------- map colour coding ----------
    const HUES = [212, 28, 135, 330, 52, 268];
    const nbrColor = TT.colorGraph(countries.map(c => c.id), id => gLand.neighbours(id), HUES.length);
    const COLOR_MODES = ['plain', 'neighbour', 'region', 'importance', 'mastery'];
    function colorModeNow() {
      const v = settings.colorBy && settings.colorBy[modeKey];
      return COLOR_MODES.includes(v) ? v : (DEFAULT_COLORS[modeKey] || 'plain');
    }
    function applyBase() {
      const cm = colorModeNow();
      const fills = new Map();
      let legend = '';
      if (cm === 'neighbour') {
        countries.forEach(c => fills.set(c.id, `hsl(${HUES[nbrColor[c.id] % HUES.length]} var(--cs) var(--cl))`));
      } else if (cm === 'region') {
        const reg = REGIONS[settings.region];
        const pool = countries.filter(c => reg.test(c));
        const keyOf = settings.region === 'World' ? (c => c.region) : (c => c.sub || c.region);
        const keys = [...new Set(pool.map(keyOf))].sort();
        const hue = k => Math.round((keys.indexOf(k) * 360 / keys.length + 205) % 360);
        pool.forEach(c => fills.set(c.id, `hsl(${hue(keyOf(c))} var(--cs) var(--cl))`));
        legend = keys.map(k => `<span><i style="background:hsl(${hue(k)} var(--cs) var(--cl))"></i>${esc(k)}</span>`).join('');
      } else if (cm === 'importance') {
        const cov = connectors();
        const max = Math.max(...Object.values(cov)) || 1;
        countries.forEach(c => fills.set(c.id, `color-mix(in srgb, var(--accent) ${Math.round(Math.sqrt(cov[c.id] / max) * 88)}%, var(--land))`));
        legend = '<span>Rarely on a route</span><span class="grad"></span><span>Often on a route</span>';
      } else if (cm === 'mastery') {
        countries.forEach(c => { const v = TT.mastery(progress, c.id); if (v !== null) fills.set(c.id, `var(--p${Math.round(v)})`); });
        legend = PROG_LABELS.map((l, i) => `<span><i style="background:var(--p${i})"></i>${l}</span>`).join('') + '<span><i style="background:var(--land);border:1px solid var(--stroke)"></i>New</span>';
      }
      const paint = (n, id) => { const f = fills.get(id); if (f) n.style.setProperty('--cfill', f); else n.style.removeProperty('--cfill'); };
      pathsById.forEach((nodes, id) => nodes.forEach(n => paint(n, id)));
      ringsById.forEach((n, id) => paint(n, id));
      if (modeKey !== 'progress') { const lg = $('#legend'); lg.innerHTML = legend; lg.hidden = !legend; }
      const sel = $('#colorSel'); if (sel) sel.value = cm;
    }

    // ---------- name labels (Learn mode) ----------
    const mctx = document.createElement('canvas').getContext('2d');
    const twCache = new Map();
    function textW(str) {
      let w = twCache.get(str);
      if (w == null) { mctx.font = '600 11px system-ui, -apple-system, "Segoe UI", Roboto, Arial, sans-serif'; w = mctx.measureText(str).width; twCache.set(str, w); }
      return w;
    }
    function scheduleOverlay() {
      if (overlayRAF) return;
      overlayRAF = requestAnimationFrame(() => { overlayRAF = 0; drawOverlay(); });
    }
    function clearOverlay() { gOver.selectAll('*').remove(); }
    function drawOverlay() {
      const ids = mode && mode.overlayIds ? mode.overlayIds() : null;
      if (!ids || !ids.length) { clearOverlay(); return; }
      const t = d3.zoomTransform(svg.node());
      const showFlag = settings.learnFlags && settings.learnNames;
      const items = [];
      for (const id of ids) {
        const a = anchors.get(id); if (!a) continue;
        const c = byId.get(id), tw = textW(c.name);
        items.push({ id, tw, x: t.applyX(a[0]), y: t.applyY(a[1]), w: tw + 8 + (showFlag ? 20 : 0), h: 16, size: Math.max(14, (sizeMain.get(id) || 0) * t.k), pri: c.area });
      }
      const placed = TT.layoutLabels(items, W, H, { blocked: [{ x: 6, y: 6, w: 250, h: 40 }] });
      const sel = gOver.selectAll('g.ol').data(placed, d => d.id);
      sel.exit().remove();
      const ent = sel.enter().append('g').attr('class', 'ol');
      ent.append('line'); ent.append('image'); ent.append('text');
      const all = ent.merge(sel);
      all.classed('sel', d => mode.sel === d.id);
      all.select('line').attr('x1', d => d.ax).attr('y1', d => d.ay).attr('x2', d => d.cx).attr('y2', d => d.cy).style('display', d => (d.leader ? null : 'none'));
      all.select('image').attr('width', 16).attr('height', 11).attr('x', d => d.cx - d.w / 2 + 4).attr('y', d => d.cy - 5.5)
        .attr('href', d => (showFlag ? flagSrc(d.id) : null)).style('display', showFlag ? null : 'none');
      all.select('text').text(d => nm(d.id)).attr('x', d => d.cx - d.w / 2 + 4 + (showFlag ? 20 : 0) + d.tw / 2).attr('y', d => d.cy + 4);
      const el = $('#lblCount');
      if (el && mode.sel !== undefined && settings.learnNames) {
        el.textContent = placed.length >= items.length ? `All ${items.length} names shown.` : `Showing ${placed.length} of ${items.length} names. Zoom in to see the rest.`;
      }
    }

    let toastTimer = null;
    function toast(msg, ms) {
      const t = $('#toast'); t.textContent = msg; t.hidden = false;
      clearTimeout(toastTimer); toastTimer = setTimeout(() => { t.hidden = true; }, ms || 2200);
    }

    // pointer handling
    const tip = $('#tip'), wrap = $('#mapWrap');
    svg.on('click.pick', e => {
      const el = e.target.closest('[data-id],[data-bg]');
      if (!el) return;
      const id = el.getAttribute('data-id');
      if (id) mode.pick && mode.pick(id); else mode.pick && mode.pick(null, el.getAttribute('data-bg'));
    });
    svg.on('mousemove.tip', e => {
      if (!mode.hover) { tip.hidden = true; return; }
      const el = e.target.closest('[data-id],[data-bg]');
      if (!el) { tip.hidden = true; return; }
      const id = el.getAttribute('data-id');
      if (id) tip.innerHTML = flagInline(id, 'sm') + esc(nm(id)); else tip.textContent = el.getAttribute('data-bg');
      const r = wrap.getBoundingClientRect();
      tip.style.left = (e.clientX - r.left) + 'px'; tip.style.top = (e.clientY - r.top) + 'px';
      tip.hidden = false;
    });
    svg.on('mouseleave.tip', () => { tip.hidden = true; });

    // ---------- pools ----------
    function poolIds() {
      const reg = REGIONS[settings.region];
      return countries.filter(c => reg.test(c) && (settings.extra || !c.extra)).map(c => c.id);
    }
    function poolSet() { return new Set(poolIds()); }

    // ---------- panel helpers ----------
    const panel = $('#panel');
    let session = { right: 0, total: 0, streak: 0 };
    function resetSession() { session = { right: 0, total: 0, streak: 0 }; }
    function statsHTML() {
      const acc = session.total ? Math.round(100 * session.right / session.total) : 0;
      return `<div class="stats"><span>Streak <b>${session.streak}</b></span><span>Correct <b>${session.right}/${session.total}</b></span><span>Accuracy <b>${session.total ? acc + '%' : '–'}</b></span></div>`;
    }
    function tally(right) {
      session.total++;
      if (right) { session.right++; session.streak++; } else session.streak = 0;
    }
    function regionSelect() {
      const by = {};
      Object.entries(REGIONS).forEach(([k, r]) => (by[r.group] = by[r.group] || []).push(k));
      const opt = r => `<option${r === settings.region ? ' selected' : ''}>${esc(r)}</option>`;
      return `<div class="sel-line"><label for="regionSel">Region</label><select id="regionSel">${by.World.map(opt).join('')}<optgroup label="Continents">${by.Continents.map(opt).join('')}</optgroup><optgroup label="Sub-regions">${by['Sub-regions'].map(opt).join('')}</optgroup></select></div>`;
    }
    function optionsHTML(opts) {
      const parts = [];
      if (opts.smart) parts.push(`<label><input type="checkbox" id="optSmart"${settings.smart ? ' checked' : ''}> Focus on countries I miss</label>`);
      if (opts.zoom) parts.push(`<label><input type="checkbox" id="optZoom"${settings.zoomTo ? ' checked' : ''}> Zoom in on the country</label>`);
      if (opts.sea) parts.push(`<label><input type="checkbox" id="optSea"${settings.sea ? ' checked' : ''}> Count sea links (ferries, bridges)</label>`);
      if (opts.diff) parts.push(`<label>Route length <select id="optDiff">${['short', 'medium', 'long'].map(d => `<option value="${d}"${settings.difficulty === d ? ' selected' : ''}>${d} (${DIFFICULTY[d][0]}${DIFFICULTY[d][0] === DIFFICULTY[d][1] ? '' : '–' + DIFFICULTY[d][1]} between)</option>`).join('')}</select></label>`);
      if (opts.bonus) parts.push(`<label>Extra guesses <select id="optBonus">${[3, 5, 8].map(b => `<option value="${b}"${settings.bonus === b ? ' selected' : ''}>${b}</option>`).join('')}</select></label>`);
      if (!opts.noFlags) parts.push(`<label><input type="checkbox" id="optFlags"${settings.flags ? ' checked' : ''}> Show flags with country names</label>`);
      parts.push(`<label><input type="checkbox" id="optExtra"${settings.extra ? ' checked' : ''}> Include Kosovo, Taiwan, Palestine, Western Sahara</label>`);
      return `<details class="opts"><summary>Options</summary><div class="grid">${parts.join('')}</div></details>`;
    }
    function bindCommon(onChange) {
      const rs = $('#regionSel');
      if (rs) rs.onchange = () => { settings.region = rs.value; saveStore(); project(); viewRegion(); applyBase(); onChange(true); };
      const bind = (id, key, fn) => { const el = $(id); if (el) el.onchange = () => { settings[key] = fn(el); saveStore(); applyBase(); if (key === 'flags') { if (mode.render) mode.render(); return; } onChange(false, key); }; };
      bind('#optSmart', 'smart', e => e.checked);
      bind('#optZoom', 'zoomTo', e => e.checked);
      bind('#optSea', 'sea', e => e.checked);
      bind('#optDiff', 'difficulty', e => e.value);
      bind('#optBonus', 'bonus', e => +e.value);
      bind('#optExtra', 'extra', e => e.checked);
      bind('#optFlags', 'flags', e => e.checked);
    }

    function attachAutocomplete(input, list, onSubmit, allowed) {
      let items = [], active = -1;
      const close = () => { list.hidden = true; items = []; active = -1; };
      const draw = () => {
        list.innerHTML = items.map((it, i) => `<li role="option" data-i="${i}" aria-selected="${i === active}">${esc(it.name)}</li>`).join('');
        list.hidden = !items.length;
      };
      input.addEventListener('input', () => {
        items = TT.suggest(input.value, nameIndex, allowed ? allowed() : null, 6); active = -1; draw();
      });
      input.addEventListener('keydown', e => {
        if (e.key === 'ArrowDown' && items.length) { e.preventDefault(); active = (active + 1) % items.length; draw(); }
        else if (e.key === 'ArrowUp' && items.length) { e.preventDefault(); active = (active - 1 + items.length) % items.length; draw(); }
        else if (e.key === 'Escape') close();
        else if (e.key === 'Enter') {
          e.preventDefault(); e.stopPropagation();
          const pick = active >= 0 ? items[active] : null;
          const text = input.value; close();
          onSubmit(pick ? { id: pick.id } : text);
        }
      });
      list.addEventListener('mousedown', e => {
        const li = e.target.closest('li'); if (!li) return;
        e.preventDefault();
        const it = items[+li.dataset.i]; close(); onSubmit({ id: it.id });
      });
      input.addEventListener('blur', () => setTimeout(close, 120));
      return { close };
    }

    // ---------- mode: locate ----------
    let recent = [];
    function pickNext(skill) {
      const id = TT.pickCountry(progress, skill, poolIds().filter(i => skill !== 'nbr' || byId.get(i).borders.length), Date.now(), Math.random, recent, settings.smart);
      if (id) { recent.push(id); if (recent.length > 4) recent.shift(); }
      return id;
    }
    function finishRecord(skill, id, result) { TT.record(progress, skill, id, result, Date.now()); saveStore(); if (colorModeNow() === 'mastery') applyBase(); }

    const locate = {
      title: 'Locate', hover: false, st: null, timer: null,
      start() { resetSession(); recent = []; this.next(); },
      next() {
        clearTimeout(this.timer);
        const id = pickNext('locate');
        clearMap();
        this.st = { id, over: false, hinted: false };
        this.render();
      },
      render() {
        const st = this.st, c = st && byId.get(st.id);
        if (!c) { panel.innerHTML = regionSelect() + '<p class="blurb">No countries in this selection.</p>'; bindCommon(() => this.start()); return; }
        panel.innerHTML = `
          ${regionSelect()}
          <p class="blurb">Click the country on the map. Tiny countries show as rings.</p>
          <div class="prompt"><span class="kicker">Find</span><span class="big">${flagInline(c.id)}${esc(c.name)}</span></div>
          <p class="fb ${st.fbc || 'info'}" id="fb">${st.fb || ''}</p>
          ${st.over ? answerCard(st.id) : ''}
          <div class="row"><button class="btn quiet" id="hintBtn" type="button"${st.over ? ' disabled' : ''}>Hint</button>
          <button class="btn" id="skipBtn" type="button">${st.over ? 'Next' : 'Skip'}</button></div>
          ${statsHTML()}${optionsHTML({ smart: true })}`;
        $('#hintBtn').onclick = () => this.hint();
        $('#skipBtn').onclick = () => { if (!st.over) this.giveUp(); else this.next(); };
        bindCommon(r => { if (r || true) this.start(); });
      },
      hint() {
        const st = this.st, c = byId.get(st.id); if (st.over) return;
        st.hinted = true;
        const near = c.borders.slice(0, 2).map(nm);
        st.fb = near.length ? `Hint: it borders ${near.join(' and ')}.` : `Hint: an island or island group in ${c.sub || c.region}.`;
        st.fbc = 'info'; $('#fb').textContent = st.fb; $('#fb').className = 'fb info';
      },
      giveUp() {
        const st = this.st; if (st.over) return;
        st.over = true; tally(false); finishRecord('locate', st.id, 'wrong');
        setMarks(new Map([[st.id, 'warn']]), [st.id]); setLabels(new Map([[st.id, nm(st.id)]]));
        st.fb = `It was ${nm(st.id)}.`; st.fbc = 'badc'; this.render();
      },
      pick(id, bgName) {
        const st = this.st; if (!st || st.over) return;
        if (!id) return toast(bgName + ' is not part of the game.');
        st.over = true;
        const right = id === st.id;
        tally(right);
        finishRecord('locate', st.id, right ? (st.hinted ? 'hinted' : 'right') : 'wrong');
        if (right) {
          setMarks(new Map([[id, 'ok']])); setLabels(new Map([[id, nm(id)]]));
          st.fb = 'Correct.'; st.fbc = 'good';
          this.timer = setTimeout(() => this.next(), 900);
        } else {
          setMarks(new Map([[id, 'bad'], [st.id, 'ok']]), [st.id]);
          setLabels(new Map([[id, nm(id)], [st.id, nm(st.id)]]));
          const off = km(byId.get(id), byId.get(st.id));
          st.fb = `That is ${nm(id)}, about ${fmt(Math.round(off / 10) * 10)} km from ${nm(st.id)}.`; st.fbc = 'badc';
        }
        this.render();
      },
      stop() { clearTimeout(this.timer); },
    };

    // ---------- mode: name ----------
    const nameMode = {
      title: 'Name', hover: false, st: null, ac: null,
      start() { resetSession(); recent = []; this.next(); },
      next() {
        const id = pickNext('name');
        clearMap();
        this.st = { id, over: false, hinted: false };
        if (id) {
          setMarks(new Map([[id, 'sel']]), [id]);
          if (settings.zoomTo) viewIds([id], 0.4); else viewRegion();
        }
        this.render(true);
      },
      render(focus) {
        const st = this.st, c = st && byId.get(st.id);
        if (!c) { panel.innerHTML = regionSelect() + '<p class="blurb">No countries in this selection.</p>'; bindCommon(() => this.start()); return; }
        panel.innerHTML = `
          ${regionSelect()}
          <p class="blurb">Type the name of the highlighted country.</p>
          <div class="prompt"><span class="kicker">What is this country?</span></div>
          <div class="field"><input id="ans" type="text" autocomplete="off" autocapitalize="off" spellcheck="false" placeholder="Start typing…"${st.over ? ' disabled' : ''}><ul class="ac" id="acList" role="listbox" hidden></ul></div>
          <p class="fb ${st.fbc || 'info'}" id="fb">${st.fb || ''}</p>
          ${st.over ? answerCard(st.id) : (st.hinted && settings.flags ? `<div class="hintflag">${flagHTML(st.id, 'md')}</div>` : '')}
          <div class="row"><button class="btn primary" id="checkBtn" type="button">${st.over ? 'Next' : 'Check'}</button>
          <button class="btn quiet" id="hintBtn" type="button"${st.over ? ' disabled' : ''}>Hint</button>
          <button class="btn quiet" id="skipBtn" type="button"${st.over ? ' disabled' : ''}>I don't know</button></div>
          ${statsHTML()}${optionsHTML({ smart: true, zoom: true })}`;
        const input = $('#ans');
        this.ac = attachAutocomplete(input, $('#acList'), v => this.submit(v));
        $('#checkBtn').onclick = () => (st.over ? this.next() : this.submit(input.value));
        $('#hintBtn').onclick = () => this.hint();
        $('#skipBtn').onclick = () => this.reveal(null);
        bindCommon((r, key) => { if (key === 'zoomTo') { this.render(); if (settings.zoomTo) viewIds([st.id], 0.4); else viewRegion(); } else this.start(); });
        if (focus && !st.over) input.focus();
      },
      hint() {
        const st = this.st, c = byId.get(st.id); if (st.over) return;
        st.hinted = true;
        st.fb = `Hint: ${c.name.length} letters, starts with “${c.name[0]}”. Capital: ${c.capital || 'none'}.`;
        st.fbc = 'info'; $('#fb').textContent = st.fb; $('#fb').className = 'fb info';
        if (settings.flags && !$('.hintflag')) $('#fb').insertAdjacentHTML('afterend', `<div class="hintflag">${flagHTML(st.id, 'md')}</div>`);
      },
      reveal(guessId) {
        const st = this.st; if (st.over) return;
        st.over = true; tally(false); finishRecord('name', st.id, 'wrong');
        const m = new Map([[st.id, 'warn']]);
        const lab = new Map([[st.id, nm(st.id)]]);
        if (guessId && guessId !== st.id) { m.set(guessId, 'bad'); lab.set(guessId, nm(guessId)); }
        setMarks(m, [st.id]); setLabels(lab);
        st.fb = guessId && guessId !== st.id ? `Not ${nm(guessId)}. This is ${nm(st.id)}.` : `This is ${nm(st.id)}.`;
        st.fbc = 'badc'; this.render();
      },
      submit(v) {
        const st = this.st; if (st.over) return;
        let ids;
        if (typeof v === 'object') ids = [v.id];
        else { const r = TT.matchName(v, nameIndex); ids = r.ids; if (!ids.length) { st.fb = 'No country by that name. Check the spelling.'; st.fbc = 'badc'; $('#fb').textContent = st.fb; $('#fb').className = 'fb badc'; return; } }
        if (ids.includes(st.id)) {
          st.over = true; tally(true); finishRecord('name', st.id, st.hinted ? 'hinted' : 'right');
          setMarks(new Map([[st.id, 'ok']])); setLabels(new Map([[st.id, nm(st.id)]]));
          st.fb = `Correct: ${nm(st.id)}.`; st.fbc = 'good'; this.render();
        } else this.reveal(ids[0]);
      },
      pick() { /* name mode is typed; clicking does nothing */ },
    };

    // ---------- mode: neighbours ----------
    const nbrMode = {
      title: 'Neighbours', hover: false, st: null, timer: null,
      start() { resetSession(); recent = []; this.next(); },
      next() {
        clearTimeout(this.timer);
        const id = pickNext('nbr'); clearMap();
        if (!id) { this.st = null; this.render(); return; }
        const need = new Set(byId.get(id).borders);
        this.st = { id, need, found: new Set(), strikes: 0, over: false };
        setMarks(new Map([[id, 'sel']]), [id]); setLabels(new Map([[id, nm(id)]]));
        if (settings.zoomTo) viewIds([id, ...need], 0.6); else viewRegion();
        this.render();
      },
      render() {
        const st = this.st;
        if (!st) { panel.innerHTML = regionSelect() + '<p class="blurb">No countries with land borders in this selection.</p>'; bindCommon(() => this.start()); return; }
        panel.innerHTML = `
          ${regionSelect()}
          <p class="blurb">Click every country that shares a land border with the highlighted one.</p>
          <div class="prompt"><span class="kicker">Neighbours of</span><span class="big">${flagInline(st.id)}${esc(nm(st.id))}</span></div>
          <div class="row"><span>Found <b>${st.found.size}</b> of <b>${st.need.size}</b></span>
            <span class="dots" aria-label="${st.strikes} wrong of 3">${[0, 1, 2].map(i => `<i class="${i < st.strikes ? 'on' : ''}"></i>`).join('')}</span></div>
          <p class="fb ${st.fbc || 'info'}" id="fb">${st.fb || ''}</p>
          ${st.over ? answerCard(st.id) : ''}
          <div class="row"><button class="btn${st.over ? ' primary' : ' quiet'}" id="skipBtn" type="button">${st.over ? 'Next' : 'Show answer'}</button></div>
          ${statsHTML()}${optionsHTML({ smart: true, zoom: true })}`;
        $('#skipBtn').onclick = () => (st.over ? this.next() : this.finish(false));
        bindCommon((r, key) => { if (key === 'zoomTo') { if (settings.zoomTo) viewIds([st.id, ...st.need], 0.6); else viewRegion(); } else this.start(); });
      },
      finish(good) {
        const st = this.st; if (st.over) return;
        st.over = true; tally(good); finishRecord('nbr', st.id, good ? 'right' : 'wrong');
        const m = new Map([[st.id, 'sel']]);
        const lab = new Map([[st.id, nm(st.id)]]);
        st.need.forEach(n => { m.set(n, st.found.has(n) ? 'ok' : 'warn'); lab.set(n, nm(n)); });
        setMarks(m); setLabels(lab);
        st.fb = good ? 'All neighbours found.' : 'Missed ones are shown in amber.'; st.fbc = good ? 'good' : 'badc';
        this.render();
      },
      pick(id, bgName) {
        const st = this.st; if (!st || st.over) return;
        if (!id) return toast(bgName + ' is not part of the game.');
        if (id === st.id || st.found.has(id)) return;
        if (st.need.has(id)) {
          st.found.add(id);
          const m = new Map(marks); m.set(id, 'ok'); setMarks(m, [st.id]);
          const lab = new Map(labels); lab.set(id, nm(id)); setLabels(lab);
          if (st.found.size === st.need.size) return this.finish(st.strikes <= 1);
          st.fb = ''; this.render();
        } else {
          st.strikes++;
          const m = new Map(marks); m.set(id, 'bad'); setMarks(m, [st.id]);
          const lab = new Map(labels); lab.set(id, nm(id)); setLabels(lab);
          setTimeout(() => {
            if (this.st !== st) return;
            const m2 = new Map(marks); if (m2.get(id) === 'bad') m2.delete(id); setMarks(m2, [st.id]);
            const l2 = new Map(labels); l2.delete(id); setLabels(l2);
          }, 1100);
          st.fb = `${nm(id)} does not border ${nm(st.id)}.`; st.fbc = 'badc';
          if (st.strikes >= 3) return this.finish(false);
          this.render();
        }
      },
      stop() { clearTimeout(this.timer); },
    };

    // ---------- mode: route (Travle) ----------
    const routeMode = {
      title: 'Route', hover: false, st: null, seen: new Set(),
      start() { this.next(); },
      next() {
        clearMap();
        const g = graph();
        const [lo, hi] = DIFFICULTY[settings.difficulty];
        let pair = TT.pickRoute(g, poolIds(), lo, hi, Math.random, this.seen);
        if (!pair) pair = TT.pickRoute(g, poolIds(), 1, 8, Math.random, this.seen);
        if (!pair) { this.st = null; this.render(); return; }
        this.seen.add(pair.start + '>' + pair.end);
        const inter = pair.steps - 1;
        this.st = { S: pair.start, E: pair.end, D: pair.steps, inter, max: inter + settings.bonus, guesses: [], over: false, won: false };
        this.paint();
        viewRegion();
        this.render(true);
      },
      paint() {
        const st = this.st, m = new Map(), lab = new Map();
        m.set(st.S, 'start'); m.set(st.E, 'end'); lab.set(st.S, nm(st.S)); lab.set(st.E, nm(st.E));
        st.guesses.forEach(x => { m.set(x.id, x.delta === 0 ? 'ok' : x.delta === 1 ? 'l1' : x.delta === 2 ? 'l2' : 'bad'); lab.set(x.id, nm(x.id)); });
        if (st.over && !st.won) TT.shortestPaths(graph(), st.S, st.E, 1)[0].forEach(id => { if (!m.has(id)) { m.set(id, 'hint'); lab.set(id, nm(id)); } });
        if (st.over && st.won) TT.shortestPaths(graph(), st.S, st.E, 1)[0].forEach(id => { if (!m.has(id)) { m.set(id, 'hint'); lab.set(id, nm(id)); } });
        setMarks(m); setLabels(lab);
      },
      render(focus) {
        const st = this.st;
        if (!st) { panel.innerHTML = regionSelect() + '<p class="blurb">No route found for this selection. Try another region or length.</p>' + optionsHTML({ sea: true, diff: true, bonus: true }); bindCommon(() => this.start()); return; }
        const left = st.max - st.guesses.length;
        const guessRows = st.guesses.map(x => {
          const cls = x.delta === 0 ? 'ok' : x.delta === 1 ? 'l1' : x.delta === 2 ? 'l2' : 'bad';
          const label = x.hinted ? 'hint · on a shortest route' : x.delta === 0 ? 'on a shortest route' : x.delta >= 3 ? 'far off the route' : `detour of ${x.delta}`;
          return `<li><span class="dot ${cls}"></span><span>${flagInline(x.id, 'sm')}${esc(nm(x.id))}</span><span class="meta">${label}</span></li>`;
        }).join('');
        let end = '';
        if (st.over) {
          const routes = TT.shortestPaths(graph(), st.S, st.E, 3);
          end = `<div class="route-box"><strong>${st.won ? `Connected with ${st.guesses.length} guess${st.guesses.length === 1 ? '' : 'es'}. The shortest route needs ${st.inter}.` : 'Out of guesses.'}</strong>
            ${routes.map(r => `<div class="route-line">${r.map((id, i) => `${i ? '<span class="arrow">→</span>' : ''}<span>${esc(nm(id))}</span>`).join('')}</div>`).join('')}</div>`;
        }
        panel.innerHTML = `
          ${regionSelect()}
          <p class="blurb">Connect the two countries by guessing the ones in between. Green is on a shortest route, yellow and orange are detours. Type a name or click the map.</p>
          <div class="prompt"><span class="kicker">Route</span><span class="big">${flagInline(st.S)}${esc(nm(st.S))} → ${flagInline(st.E)}${esc(nm(st.E))}</span></div>
          <div class="row"><span><b>${left}</b> guess${left === 1 ? '' : 'es'} left</span><span class="blurb">${st.inter} stop${st.inter === 1 ? '' : 's'} between on the shortest route</span></div>
          ${st.over ? '' : `<div class="field"><input id="ans" type="text" autocomplete="off" autocapitalize="off" spellcheck="false" placeholder="Guess a country…"><ul class="ac" id="acList" role="listbox" hidden></ul></div>`}
          <p class="fb ${st.fbc || 'info'}" id="fb">${st.fb || ''}</p>
          ${guessRows ? `<ul class="guesses">${guessRows}</ul>` : ''}
          ${end}
          <div class="row">${st.over ? '<button class="btn primary" id="nextBtn" type="button">Next route</button>' : '<button class="btn quiet" id="hintBtn" type="button">Hint</button><button class="btn quiet" id="giveBtn" type="button">Give up</button>'}</div>
          <div class="stats"><span>Played <b>${progress.route.games}</b></span><span>Won <b>${progress.route.wins}</b></span><span>Streak <b>${progress.route.streak}</b></span><span>Best <b>${progress.route.best}</b></span></div>
          ${optionsHTML({ sea: true, diff: true, bonus: true })}`;
        if (!st.over) {
          const input = $('#ans');
          attachAutocomplete(input, $('#acList'), v => this.submit(v));
          $('#hintBtn').onclick = () => this.hint();
          $('#giveBtn').onclick = () => this.end(false);
          if (focus) input.focus();
        } else $('#nextBtn').onclick = () => this.next();
        bindCommon((r, key) => { if (key === 'sea') { this.start(); } else this.start(); });
      },
      add(id, hinted) {
        const st = this.st, g = graph();
        const delta = TT.detour(g, st.S, st.E, id);
        st.guesses.push({ id, delta: Math.min(delta, 9), hinted: !!hinted });
        const set = new Set([st.S, st.E, ...st.guesses.map(x => x.id)]);
        this.paint();
        if (TT.connected(g, st.S, st.E, set)) return this.end(true);
        if (st.guesses.length >= st.max) return this.end(false);
        st.fb = hinted ? `Hint: ${nm(id)} is on a shortest route.` : delta === 0 ? `${nm(id)} is on a shortest route.` : (delta >= 3 ? `${nm(id)} is far off the route.` : `${nm(id)} is a detour of ${delta}.`);
        st.fbc = delta === 0 ? 'good' : 'info';
        this.render(true);
      },
      submit(v) {
        const st = this.st; if (!st || st.over) return;
        let ids;
        if (typeof v === 'object') ids = [v.id];
        else {
          const r = TT.matchName(v, nameIndex); ids = r.ids;
          if (!ids.length) { st.fb = 'No country by that name.'; st.fbc = 'badc'; $('#fb').textContent = st.fb; $('#fb').className = 'fb badc'; return; }
          if (ids.length > 1) { st.fb = 'Be more specific: ' + ids.map(nm).join(' or ') + '.'; st.fbc = 'badc'; $('#fb').textContent = st.fb; $('#fb').className = 'fb badc'; return; }
        }
        const id = ids[0];
        if (id === st.S || id === st.E) { st.fb = `${nm(id)} is already an end point.`; st.fbc = 'badc'; $('#fb').textContent = st.fb; $('#fb').className = 'fb badc'; return; }
        if (st.guesses.some(x => x.id === id)) { st.fb = `You already guessed ${nm(id)}.`; st.fbc = 'badc'; $('#fb').textContent = st.fb; $('#fb').className = 'fb badc'; return; }
        this.add(id, false);
      },
      pick(id, bgName) {
        const st = this.st; if (!st || st.over) return;
        if (!id) return toast(bgName + ' is not part of the game.');
        if (id === st.S || id === st.E || st.guesses.some(x => x.id === id)) return toast(nm(id) + ' is already on the board.');
        this.add(id, false);
      },
      hint() {
        const st = this.st; if (!st || st.over) return;
        const g = graph();
        const have = new Set([st.S, st.E, ...st.guesses.map(x => x.id)]);
        const cands = TT.onShortest(g, st.S, st.E).filter(id => !have.has(id));
        if (!cands.length) return;
        const near = cands.filter(id => g.neighbours(id).some(n => have.has(n)));
        const pool = near.length ? near : cands;
        this.add(pool[Math.floor(Math.random() * pool.length)], true);
      },
      end(won) {
        const st = this.st; if (st.over) return;
        st.over = true; st.won = won;
        const r = progress.route;
        r.games++; if (won) { r.wins++; r.streak++; r.best = Math.max(r.best, r.streak); } else r.streak = 0;
        r.extra += Math.max(0, st.guesses.length - st.inter);
        saveStore(); this.paint(); this.render();
      },
    };

    // ---------- mode: atlas ----------
    const atlas = {
      title: 'Atlas', hover: true, sel: null,
      start() { this.sel = null; clearMap(); viewRegion(); this.render(); },
      pick(id) { if (!id) return; this.select(id); },
      select(id) {
        this.sel = id;
        const c = byId.get(id), m = new Map([[id, 'sel']]), lab = new Map([[id, c.name]]);
        graph().neighbours(id).forEach(n => { m.set(n, 'nbr'); lab.set(n, nm(n)); });
        setMarks(m, [id]); setLabels(lab);
        viewIds([id, ...graph().neighbours(id).filter(n => gLand.d(id, n) === 1)], 0.5);
        this.render();
      },
      render() {
        const bc = connectors();
        const ranked = Object.entries(bc).sort((a, b) => b[1] - a[1]).map(x => x[0]);
        const maxBc = bc[ranked[0]] || 1;
        let body;
        if (this.sel) {
          const c = byId.get(this.sel), g = graph();
          const land = gLand.neighbours(c.id), all = g.neighbours(c.id).filter(n => !land.includes(n));
          const rank = ranked.indexOf(c.id) + 1;
          body = `
            ${settings.flags ? `<div class="learn-head">${flagHTML(c.id, 'xl')}</div>` : ''}
            <div class="prompt"><span class="kicker">${esc(c.region)} · ${esc(c.sub || '')}</span><span class="big">${esc(c.name)}</span></div>
            <dl class="facts"><dt>Capital</dt><dd>${esc(c.capital || '–')}</dd><dt>Area</dt><dd>${fmt(c.area)} km²</dd>
              <dt>On shortest routes</dt><dd>${Math.round(bc[c.id] * 100)}% of country pairs (#${rank} of ${ranked.length})</dd></dl>
            <h2>Land neighbours (${land.length})</h2>
            <div class="chips">${land.length ? land.map(n => `<button class="chip" data-go="${n}" type="button">${esc(nm(n))}</button>`).join('') : '<span class="blurb">None: an island or island group.</span>'}</div>
            ${all.length && settings.sea ? `<h2>Sea links (${all.length})</h2><div class="chips">${all.map(n => `<button class="chip sea" data-go="${n}" type="button">${esc(nm(n))}</button>`).join('')}</div>` : ''}
            <div class="row"><button class="btn" id="backBtn" type="button">Back to connectors</button></div>`;
        } else {
          body = `
            <p class="blurb">Hover or click a country to see its neighbours. These countries sit on a shortest route for the most pairs of countries, so they are the best first guesses in Route mode.</p>
            <h2>Top connectors</h2>
            <ol class="list" style="padding:0">${ranked.slice(0, 20).map((id, i) => `<li data-go="${id}"><span class="rk">${i + 1}</span><span>${flagInline(id, 'sm')}${esc(nm(id))}</span><span class="rk" style="text-align:right">${Math.round(bc[id] * 100)}%</span></li>`).join('')}</ol>`;
        }
        panel.innerHTML = `${regionSelect()}${body}${optionsHTML({ sea: true })}`;
        panel.querySelectorAll('[data-go]').forEach(el => { el.onclick = () => this.select(el.dataset.go); });
        const back = $('#backBtn'); if (back) back.onclick = () => this.start();
        bindCommon(() => (this.sel ? this.select(this.sel) : this.start()));
      },
    };

    // ---------- mode: progress ----------
    const PROG_LABELS = ['Missed', 'Weak', 'Shaky', 'Okay', 'Good', 'Mastered'];
    const progressMode = {
      title: 'Progress', hover: true,
      reset: false,
      start() {
        this.reset = false;
        const m = new Map();
        countries.forEach(c => { const v = TT.mastery(progress, c.id); if (v !== null) m.set(c.id, 'm' + Math.round(v)); });
        setMarks(m); setLabels(new Map()); viewRegion();
        this.render();
      },
      pick(id) { if (id) toast(nm(id) + ': ' + this.describe(id)); },
      describe(id) {
        const parts = [['Locate', 'locate'], ['Name', 'name'], ['Neighbours', 'nbr'], ['Flags', 'flag']].map(([l, k]) => {
          const e = progress.s[k][id]; return e ? `${l} ${PROG_LABELS[e.b]}` : `${l} new`;
        });
        return parts.join(' · ');
      },
      render() {
        const all = countries.map(c => c.id);
        const skills = [['Locate', 'locate', all], ['Name', 'name', all], ['Flags', 'flag', all], ['Neighbours', 'nbr', all.filter(i => byId.get(i).borders.length)]];
        const bars = skills.map(([l, k, ids]) => {
          const n = ids.filter(i => progress.s[k][i] && progress.s[k][i].b >= TT.MASTERED).length;
          const pct = Math.round(100 * n / ids.length);
          return `<div class="bar"><span>${l}</span><span class="track"><span class="fill" style="display:block;width:${pct}%"></span></span><span class="pct">${pct}%</span></div>`;
        }).join('');
        const regBars = Object.keys(REGIONS).filter(r => REGIONS[r].group === 'Continents').map(r => {
          const ids = countries.filter(c => REGIONS[r].test(c)).map(c => c.id);
          let sum = 0; ids.forEach(i => { const v = TT.mastery(progress, i); if (v !== null) sum += v / 5; });
          const pct = Math.round(100 * sum / ids.length);
          return `<div class="bar"><span>${r}</span><span class="track"><span class="fill" style="display:block;width:${pct}%"></span></span><span class="pct">${pct}%</span></div>`;
        }).join('');
        const weak = countries.map(c => ({ c, v: TT.mastery(progress, c.id), w: ['locate', 'name', 'nbr', 'flag'].reduce((a, k) => a + ((progress.s[k][c.id] || {}).w || 0), 0) }))
          .filter(x => x.v !== null).sort((a, b) => a.v - b.v || b.w - a.w).slice(0, 8);
        const seen = countries.filter(c => TT.mastery(progress, c.id) !== null).length;
        panel.innerHTML = `
          ${regionSelect()}
          <p class="blurb">${seen ? `You have practised ${seen} of ${countries.length} countries. The map is coloured by how well you know each one.` : 'Nothing practised yet. Try Locate, Name, Flags or Neighbours and your progress will appear here.'}</p>
          <h2>Countries mastered</h2><div class="bars">${bars}</div>
          <h2>By region</h2><div class="bars">${regBars}</div>
          ${weak.length ? `<h2>Weakest spots</h2><ol class="list" style="padding:0">${weak.map((x, i) => `<li data-go="${x.c.id}"><span class="rk">${i + 1}</span><span>${flagInline(x.c.id, 'sm')}${esc(x.c.name)}</span><span class="rk">${PROG_LABELS[Math.round(x.v)]}</span></li>`).join('')}</ol>` : ''}
          <div class="row"><button class="btn quiet" id="resetBtn" type="button">${this.reset ? 'Click again to erase all progress' : 'Reset progress'}</button></div>`;
        panel.querySelectorAll('[data-go]').forEach(el => { el.onclick = () => { viewIds([el.dataset.go], 0.3); toast(nm(el.dataset.go) + ': ' + this.describe(el.dataset.go), 3000); }; });
        $('#resetBtn').onclick = () => {
          if (!this.reset) { this.reset = true; return this.render(); }
          progress = TT.emptyProgress(); saveStore(); this.start();
        };
        bindCommon(() => this.start());
        const lg = $('#legend');
        lg.innerHTML = PROG_LABELS.map((l, i) => `<span><i style="background:var(--p${i})"></i>${l}</span>`).join('') + '<span><i style="background:var(--land);border:1px solid var(--stroke)"></i>New</span>';
      },
    };

    // ---------- mode: learn ----------
    const PRACTICE = [['locate', 'Locate'], ['name', 'Name'], ['flags', 'Flags'], ['nbr', 'Neighbours'], ['route', 'Route']];
    const learn = {
      title: 'Learn', sel: null, hov: null,
      get hover() { return settings.learnNames; },
      start() { this.sel = null; this.hov = null; this.dim(); viewRegion(); this.paintMarks(); this.render(); },
      stop() { setDim(new Set()); clearOverlay(); },
      dim() { const pool = poolSet(); setDim(new Set(countries.filter(c => !pool.has(c.id)).map(c => c.id))); },
      overlayIds() {
        const pool = poolIds();
        if (settings.learnNames) return this.sel && !pool.includes(this.sel) ? pool.concat(this.sel) : pool;
        return this.sel ? [this.sel] : [];
      },
      paintMarks() {
        const m = new Map(), pulse = [];
        if (this.sel) {
          m.set(this.sel, 'sel'); pulse.push(this.sel);
          if (settings.learnNames) gLand.neighbours(this.sel).forEach(n => m.set(n, 'nbr'));
        }
        if (this.hov && this.hov !== this.sel) { m.set(this.hov, 'warn'); pulse.push(this.hov); }
        setMarks(m, pulse);
      },
      pick(id, bg) {
        if (!id) return toast(bg + ' is not part of the game.');
        this.select(id, false);
      },
      select(id, zoomTo) {
        this.sel = id; this.paintMarks(); this.renderCard();
        panel.querySelectorAll('button.tile').forEach(t => t.classList.toggle('on', t.dataset.go === id));
        if (zoomTo) viewIds([id], 0.3);
        scheduleOverlay();
      },
      renderCard() {
        const host = $('#learnCard'); if (!host) return;
        const c = this.sel && byId.get(this.sel);
        if (!c) { host.innerHTML = '<p class="blurb">Click a country on the map, or a flag below, to see its name, capital and neighbours.</p>'; return; }
        const land = gLand.neighbours(c.id);
        const sea = graph().neighbours(c.id).filter(n => !land.includes(n));
        host.innerHTML = `
          <div class="learn-head">${flagHTML(c.id, 'xl')}<div class="prompt"><span class="kicker">${esc(c.sub || c.region)}</span><span class="big">${esc(c.name)}</span></div></div>
          <dl class="facts"><dt>Capital</dt><dd>${esc(c.capital || '–')}</dd><dt>Area</dt><dd>${fmt(c.area)} km²</dd><dt>Land borders</dt><dd>${land.length}</dd></dl>
          <div class="chips">${land.length ? land.map(n => `<button class="chip" data-go="${n}" type="button">${esc(nm(n))}</button>`).join('') : '<span class="blurb">Island nation: no land borders.</span>'}${settings.sea ? sea.map(n => `<button class="chip sea" data-go="${n}" type="button" title="Sea link">${esc(nm(n))}</button>`).join('') : ''}</div>
          <div class="row"><button class="btn" id="zoomBtn" type="button">Zoom to country</button></div>`;
        host.querySelectorAll('[data-go]').forEach(el => { el.onclick = () => this.select(el.dataset.go, true); });
        $('#zoomBtn').onclick = () => viewIds([c.id], 0.3);
      },
      render() {
        const pool = poolIds(), names = settings.learnNames;
        const tiles = pool.slice().sort((a, b) => nm(a).localeCompare(nm(b))).map(id =>
          `<button class="tile${id === this.sel ? ' on' : ''}" type="button" data-go="${id}"${names ? '' : ' aria-label="Flag, name hidden"'}>${flagHTML(id, 'tg')}${names ? `<span>${esc(nm(id))}</span>` : ''}</button>`).join('');
        panel.innerHTML = `
          ${regionSelect()}
          <p class="blurb">Study first with names on, then switch them off and test yourself. Click any country or flag to check it.</p>
          <div class="toggles">
            <label class="switch"><input type="checkbox" id="lnNames"${names ? ' checked' : ''}><span>Show names on map</span></label>
            <label class="switch"><input type="checkbox" id="lnFlags"${settings.learnFlags ? ' checked' : ''}><span>Flags beside names</span></label>
          </div>
          <p class="blurb" id="lblCount">${names ? '' : 'Names are hidden. Click a country or a flag to reveal it.'}</p>
          <div class="card" id="learnCard"></div>
          <div class="row practice"><span class="blurb">Practise this region</span>${PRACTICE.map(([k, l]) => `<button class="btn quiet" data-mode="${k}" type="button">${l}</button>`).join('')}</div>
          <details class="gallery"${pool.length <= 70 ? ' open' : ''}><summary>All ${pool.length} countries${names ? '' : ' (flags only)'}</summary><div class="tiles">${tiles}</div></details>
          ${optionsHTML({ sea: true, noFlags: true })}`;
        this.renderCard();
        $('#lnNames').onchange = e => { settings.learnNames = e.target.checked; saveStore(); this.paintMarks(); this.render(); scheduleOverlay(); };
        $('#lnFlags').onchange = e => { settings.learnFlags = e.target.checked; saveStore(); scheduleOverlay(); };
        panel.querySelectorAll('button.tile').forEach(el => {
          el.onclick = () => this.select(el.dataset.go, true);
          el.onmouseenter = () => { this.hov = el.dataset.go; this.paintMarks(); };
          el.onmouseleave = () => { this.hov = null; this.paintMarks(); };
        });
        panel.querySelectorAll('[data-mode]').forEach(el => { el.onclick = () => setMode(el.dataset.mode); });
        bindCommon((r, key) => { if (key === 'sea') this.renderCard(); else this.start(); });
        scheduleOverlay();
      },
    };

    // ---------- mode: flags ----------
    const FLAGQ = [['type', 'Flag → type the name'], ['map', 'Flag → find it on the map'], ['pick', 'Name → pick the flag']];
    const flagMode = {
      title: 'Flags', hover: false, st: null,
      start() { resetSession(); recent = []; this.next(); },
      next() {
        const id = pickNext('flag'); clearMap();
        if (!id) { this.st = null; this.render(); return; }
        const q = settings.flagQ;
        this.st = { id, q, over: false, hinted: false, picked: null, opts: q === 'pick' ? TT.flagOptions(id, countries, poolIds(), Math.random, 4) : null };
        viewRegion();
        this.render(true);
      },
      render(focus) {
        const st = this.st;
        const qSel = `<div class="sel-line"><label for="optFlagQ">Question</label><select id="optFlagQ">${FLAGQ.map(([k, l]) => `<option value="${k}"${k === settings.flagQ ? ' selected' : ''}>${l}</option>`).join('')}</select></div>`;
        const bindQ = () => { const q = $('#optFlagQ'); if (q) q.onchange = () => { settings.flagQ = q.value; saveStore(); this.start(); }; };
        if (!st) { panel.innerHTML = regionSelect() + qSel + '<p class="blurb">No countries in this selection.</p>'; bindQ(); bindCommon(() => this.start()); return; }
        const c = byId.get(st.id);
        let body, buttons;
        if (st.q === 'pick') {
          body = `<p class="blurb">Pick the flag that belongs to this country. Keys 1 to 4 also work.</p>
            <div class="prompt"><span class="kicker">Which flag is</span><span class="big">${esc(c.name)}</span></div>
            <div class="flaggrid">${st.opts.map((o, i) => {
              const cls = st.over ? (o === st.id ? ' ok' : (o === st.picked ? ' bad' : '')) : '';
              return `<button type="button" class="flagopt${cls}" data-opt="${o}" aria-label="Flag option ${i + 1}${st.over ? ': ' + esc(nm(o)) : ''}">${flagHTML(o, 'opt')}<kbd>${i + 1}</kbd>${st.over ? `<span class="cap">${esc(nm(o))}</span>` : ''}</button>`;
            }).join('')}</div>`;
          buttons = st.over ? '<button class="btn primary" id="nextBtn" type="button">Next</button>' : '<button class="btn quiet" id="idkBtn" type="button">I don\'t know</button>';
        } else {
          body = `<p class="blurb">${st.q === 'type' ? 'Type the name of the country this flag belongs to.' : 'Click the country this flag belongs to.'}</p>
            <div class="bigflag">${flagHTML(st.id, 'lg', 'Flag to identify')}</div>
            ${st.q === 'type' ? `<div class="field"><input id="ans" type="text" autocomplete="off" autocapitalize="off" spellcheck="false" placeholder="Start typing…"${st.over ? ' disabled' : ''}><ul class="ac" id="acList" role="listbox" hidden></ul></div>` : ''}`;
          buttons = st.over
            ? '<button class="btn primary" id="nextBtn" type="button">Next</button>'
            : (st.q === 'type' ? '<button class="btn primary" id="checkBtn" type="button">Check</button>' : '') + '<button class="btn quiet" id="hintBtn" type="button">Hint</button><button class="btn quiet" id="idkBtn" type="button">I don\'t know</button>';
        }
        panel.innerHTML = `
          ${regionSelect()}${qSel}
          ${body}
          <p class="fb ${st.fbc || 'info'}" id="fb">${st.fb || ''}</p>
          ${st.over ? answerCard(st.id) : ''}
          <div class="row">${buttons}</div>
          ${statsHTML()}${optionsHTML({ smart: true, zoom: true, noFlags: true })}`;
        bindQ();
        const next = $('#nextBtn'); if (next) next.onclick = () => this.next();
        const idk = $('#idkBtn'); if (idk) idk.onclick = () => this.conclude('wrong', null, `This flag belongs to ${nm(st.id)}.`);
        const hint = $('#hintBtn'); if (hint) hint.onclick = () => this.hint();
        panel.querySelectorAll('[data-opt]').forEach(el => { el.onclick = () => this.choose(el.dataset.opt); });
        if (st.q === 'type' && !st.over) {
          const input = $('#ans');
          attachAutocomplete(input, $('#acList'), v => this.submit(v));
          $('#checkBtn').onclick = () => this.submit(input.value);
          if (focus) input.focus();
        }
        bindCommon((r, key) => { if (key === 'zoomTo') { if (st.over && settings.zoomTo) viewIds([st.id], 0.4); } else this.start(); });
      },
      hint() {
        const st = this.st; if (!st || st.over) return;
        const c = byId.get(st.id);
        st.hinted = true;
        const near = c.borders.slice(0, 2).map(nm);
        st.fb = st.q === 'type'
          ? `Hint: ${c.name.length} letters, starts with “${c.name[0]}”. Region: ${c.sub || c.region}.`
          : (near.length ? `Hint: it borders ${near.join(' and ')}.` : `Hint: an island or island group in ${c.sub || c.region}.`);
        st.fbc = 'info'; $('#fb').textContent = st.fb; $('#fb').className = 'fb info';
      },
      conclude(result, guessId, msg) {
        const st = this.st; if (!st || st.over) return;
        st.over = true;
        const right = result !== 'wrong';
        tally(right); finishRecord('flag', st.id, result);
        const m = new Map([[st.id, right ? 'ok' : 'warn']]);
        const lab = new Map([[st.id, nm(st.id)]]);
        if (guessId && guessId !== st.id) { m.set(guessId, 'bad'); lab.set(guessId, nm(guessId)); }
        setMarks(m, [st.id]); setLabels(lab);
        if (settings.zoomTo) viewIds([st.id], 0.4);
        st.fb = msg; st.fbc = right ? 'good' : 'badc';
        this.render();
      },
      submit(v) {
        const st = this.st; if (!st || st.over || st.q !== 'type') return;
        let ids;
        if (typeof v === 'object') ids = [v.id];
        else {
          ids = TT.matchName(v, nameIndex).ids;
          if (!ids.length) { st.fb = 'No country by that name. Check the spelling.'; st.fbc = 'badc'; $('#fb').textContent = st.fb; $('#fb').className = 'fb badc'; return; }
        }
        if (ids.includes(st.id)) this.conclude(st.hinted ? 'hinted' : 'right', null, `Correct: ${nm(st.id)}.`);
        else this.conclude('wrong', ids[0], `Not ${nm(ids[0])}. This flag belongs to ${nm(st.id)}.`);
      },
      choose(id) {
        const st = this.st; if (!st || st.over || st.q !== 'pick') return;
        st.picked = id;
        if (id === st.id) this.conclude('right', null, 'Correct.');
        else this.conclude('wrong', id, `That flag belongs to ${nm(id)}. The right one is outlined in green.`);
      },
      pick(id, bg) {
        const st = this.st; if (!st || st.over || st.q !== 'map') return;
        if (!id) return toast(bg + ' is not part of the game.');
        if (id === st.id) this.conclude(st.hinted ? 'hinted' : 'right', null, `Correct: ${nm(st.id)}.`);
        else this.conclude('wrong', id, `That is ${nm(id)}. This flag belongs to ${nm(st.id)}.`);
      },
    };

    // ---------- shell: tabs, theme, mode switching ----------
    const MODES = { learn, locate, name: nameMode, flags: flagMode, nbr: nbrMode, route: routeMode, atlas, progress: progressMode };
    let mode = locate;
    function setMode(key) {
      if (mode.stop) mode.stop();
      settings.mode = key; modeKey = key; saveStore();
      mode = MODES[key];
      $('#tip').hidden = true; $('#toast').hidden = true;
      $('#legend').hidden = key !== 'progress';
      document.querySelectorAll('.tab').forEach(t => t.setAttribute('aria-current', String(t.dataset.mode === key)));
      clearMap();
      mode.start();
      applyBase();
      scheduleOverlay();
    }
    const tabs = $('#tabs');
    tabs.innerHTML = Object.entries(MODES).map(([k, m]) => `<button class="tab" type="button" data-mode="${k}">${m.title}</button>`).join('');
    tabs.onclick = e => { const b = e.target.closest('.tab'); if (b) setMode(b.dataset.mode); };
    $('#colorSel').onchange = e => { settings.colorBy[modeKey] = e.target.value; saveStore(); applyBase(); };
    $('#themeBtn').onclick = () => {
      const cur = document.documentElement.dataset.theme || (matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light');
      const next = cur === 'dark' ? 'light' : 'dark';
      applyTheme(next); saveTheme(next);
    };
    document.addEventListener('keydown', e => {
      if (/^[1-4]$/.test(e.key) && mode === flagMode && mode.st && mode.st.q === 'pick' && !mode.st.over && !/^(INPUT|SELECT|TEXTAREA)$/.test(document.activeElement.tagName)) {
        const o = mode.st.opts[+e.key - 1]; if (o) { e.preventDefault(); mode.choose(o); }
        return;
      }
      if (e.key !== 'Enter' && e.key !== ' ') return;
      if (/^(INPUT|SELECT|TEXTAREA|BUTTON)$/.test(document.activeElement.tagName)) return;
      const st = mode.st;
      if (e.repeat) return;
      if (st && st.over && mode.next) { e.preventDefault(); mode.next(); }
    });

    // size + initial render
    function measure() {
      const r = $('#mapWrap').getBoundingClientRect();
      const w = Math.round(r.width), h = Math.round(r.height);
      if (!w || !h || (w === W && h === H)) return false;
      W = w; H = h; svg.attr('width', W).attr('height', H);
      return true;
    }
    measure(); project(); updateRings(); viewRegion(false);
    let rt = null;
    new ResizeObserver(() => { clearTimeout(rt); rt = setTimeout(() => { if (measure()) { project(); updateRings(); updateLabels(); viewRegion(false); } }, 120); }).observe($('#mapWrap'));

    if (!MODES[settings.mode]) settings.mode = 'learn';
    setMode(settings.mode);
    window.__tt = { setMode, MODES, get mode() { return mode; }, byId, graph, settings, progress: () => progress, applyBase, drawOverlay, REGIONS };
  }

  main().catch(err => {
    console.error(err);
    const p = document.getElementById('panel');
    if (p) p.innerHTML = '<p>Could not start: ' + esc(err.message) + '</p>';
  });
})();
