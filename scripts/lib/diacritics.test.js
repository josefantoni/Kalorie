const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { foldDiacritics } = require('./diacritics');

const fixture = JSON.parse(
  fs.readFileSync(path.join(__dirname, '..', '..', 'TextKit', 'fixtures', 'text-kit-cases.json'), 'utf8')
);

for (const { input, expected } of fixture.foldDiacritics) {
  test(`foldDiacritics(${JSON.stringify(input)}) matches the shared fixture`, () => {
    assert.equal(foldDiacritics(input), expected);
  });
}
