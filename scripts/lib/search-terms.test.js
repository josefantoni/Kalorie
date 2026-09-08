const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { searchTerms } = require('./search-terms');

const fixture = JSON.parse(
  fs.readFileSync(path.join(__dirname, '..', '..', 'TextKit', 'fixtures', 'text-kit-cases.json'), 'utf8')
);

for (const { input, expected } of fixture.searchTerms) {
  test(`searchTerms(${JSON.stringify(input)}) matches the shared fixture`, () => {
    assert.deepEqual(searchTerms(input), expected);
  });
}

test('searchTerms_collapsesRepeatedWhitespace_andHasNoEmptyTerms', () => {
  assert.ok(!searchTerms('Tvaroh   light').includes(''));
});

test('searchTerms_deduplicatesSharedPrefixesAcrossWords', () => {
  const terms = searchTerms('mléko mléko');
  assert.equal(new Set(terms).size, terms.length);
});
