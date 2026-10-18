// Builds two single-file versions of the app in dist/:
//   travle-trainer.html          data + code inlined, d3/topojson loaded from cdnjs (for hosting)
//   travle-trainer-offline.html  everything inlined, works by double-clicking with no internet
// Run: node scripts/make-single.js
const fs = require('fs');
const path = require('path');
const ROOT = path.join(__dirname, '..');
const read = f => fs.readFileSync(path.join(ROOT, f), 'utf8');
const safeJSON = f => JSON.stringify(JSON.parse(read(f))).replace(/<\//g, '<\\/');
const safeJS = f => read(f).replace(/<\/script/gi, '<\\/script');

const css = read('css/style.css');
const body = read('index.html').match(/<body>([\s\S]*?)<script src="vendor/)[1].trim();
const data = ['countries', 'world', 'sea-links', 'flags']
  .map(n => `<script type="application/json" id="data-${n}">${safeJSON('data/' + n + '.json')}</script>`).join('\n');
const app = `<script>\n${safeJS('js/logic.js')}\n</script>\n<script>\n${safeJS('js/app.js')}\n</script>`;

const cdn = `<title>Travle Trainer</title>
<meta name="claude:viewport-fit-sides" content="cover">
<style>
${css}
</style>
${body}
${data}
<script src="https://cdnjs.cloudflare.com/ajax/libs/d3/7.9.0/d3.min.js"></script>
<script src="https://cdnjs.cloudflare.com/ajax/libs/topojson-client/3.1.0/topojson-client.min.js"></script>
${app}
`;

const offline = `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<title>Travle Trainer</title>
<style>
${css}
</style>
</head>
<body>
${body}
${data}
<script>${safeJS('vendor/d3.min.js')}</script>
<script>${safeJS('vendor/topojson-client.min.js')}</script>
${app}
</body>
</html>
`;

fs.mkdirSync(path.join(ROOT, 'dist'), { recursive: true });
fs.writeFileSync(path.join(ROOT, 'dist/travle-trainer.html'), cdn);
fs.writeFileSync(path.join(ROOT, 'dist/travle-trainer-offline.html'), offline);
console.log('dist/travle-trainer.html', (cdn.length / 1024).toFixed(0) + ' KB');
console.log('dist/travle-trainer-offline.html', (offline.length / 1024).toFixed(0) + ' KB');
