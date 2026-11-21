const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { slovakName } = require('./name-rules');

const fixture = JSON.parse(
  fs.readFileSync(path.join(__dirname, '..', '..', 'TextKit', 'fixtures', 'text-kit-cases.json'), 'utf8')
);

for (const { slovak, czech, english, expected } of fixture.slovakName) {
  test(`slovakName(${JSON.stringify([slovak, czech, english])}) matches the shared fixture`, () => {
    assert.equal(slovakName(slovak, czech, english), expected);
  });
}
