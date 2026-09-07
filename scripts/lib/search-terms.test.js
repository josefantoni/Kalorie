const test = require('node:test');
const assert = require('node:assert/strict');
const { searchTerms } = require('./search-terms');

test('searchTerms_withSingleWord_returnsEveryPrefix', () => {
  assert.deepEqual(searchTerms('Tvaroh'), ['t', 'tv', 'tva', 'tvar', 'tvaro', 'tvaroh']);
});

test('searchTerms_findsAnyWordByAnyOfItsPrefixes', () => {
  const terms = searchTerms('Polotučné mléko');
  assert.ok(terms.includes('mleko'));
  assert.ok(terms.includes('mlek'));
  assert.ok(terms.includes('m'));
  assert.ok(terms.includes('polotucne'));
});

test('searchTerms_lowercasesAndFoldsDiacritics', () => {
  assert.ok(searchTerms('ROHLÍK').includes('rohlik'));
});

test('searchTerms_collapsesRepeatedWhitespace_andHasNoEmptyTerms', () => {
  assert.ok(!searchTerms('Tvaroh   light').includes(''));
});

test('searchTerms_deduplicatesSharedPrefixesAcrossWords', () => {
  const terms = searchTerms('mléko mléko');
  assert.equal(new Set(terms).size, terms.length);
});
