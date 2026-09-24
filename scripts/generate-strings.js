#!/usr/bin/env node

const fs = require('node:fs');
const path = require('node:path');
const { generateAndroidStrings, generateXcstrings } = require('./lib/localisation');

const root = path.join(__dirname, '..');
const source = path.join(root, 'localisation', 'strings.json');
const res = path.join(root, 'Android', 'app', 'src', 'main', 'res');

const isCheck = process.argv.includes('--check');
const strings = JSON.parse(fs.readFileSync(source, 'utf8'));
const android = generateAndroidStrings(strings);
const xcstrings = path.join(root, 'iOS', 'Kalorie', 'Resources', 'Localizable.xcstrings');
const existingXcstrings = fs.existsSync(xcstrings) ? fs.readFileSync(xcstrings, 'utf8') : null;
const outputs = {
  [xcstrings]: generateXcstrings(strings, existingXcstrings),
  [path.join(res, 'values', 'strings.xml')]: android.cs,
  [path.join(res, 'values-en', 'strings.xml')]: android.en,
};

const stale = [];
for (const [file, content] of Object.entries(outputs)) {
  const current = fs.existsSync(file) ? fs.readFileSync(file, 'utf8') : null;
  if (current === content) continue;
  stale.push(path.relative(root, file));
  if (!isCheck) fs.writeFileSync(file, content);
}

if (isCheck && stale.length > 0) {
  console.error(`Out of date, run "npm run generate-strings" in scripts/:\n  ${stale.join('\n  ')}`);
  process.exit(1);
}
console.log(isCheck ? 'Generated string files are up to date.' : `Wrote ${stale.length} file(s).`);
