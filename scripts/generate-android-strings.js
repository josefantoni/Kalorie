#!/usr/bin/env node

const fs = require('node:fs');
const path = require('node:path');
const { generateAndroidStrings } = require('./lib/android-strings');

const root = path.join(__dirname, '..');
const catalogue = path.join(root, 'iOS', 'Kalorie', 'Resources', 'Localizable.xcstrings');
const res = path.join(root, 'Android', 'app', 'src', 'main', 'res');
const targets = { cs: path.join(res, 'values', 'strings.xml'), en: path.join(res, 'values-en', 'strings.xml') };

const isCheck = process.argv.includes('--check');
const generated = generateAndroidStrings(JSON.parse(fs.readFileSync(catalogue, 'utf8')));

const stale = [];
for (const [language, file] of Object.entries(targets)) {
  const current = fs.existsSync(file) ? fs.readFileSync(file, 'utf8') : null;
  if (current === generated[language]) continue;
  stale.push(path.relative(root, file));
  if (!isCheck) fs.writeFileSync(file, generated[language]);
}

if (isCheck && stale.length > 0) {
  console.error(`Out of date, run "npm run generate-android-strings" in scripts/:\n  ${stale.join('\n  ')}`);
  process.exit(1);
}
console.log(isCheck ? 'Android strings are up to date.' : `Wrote ${stale.length} file(s).`);
