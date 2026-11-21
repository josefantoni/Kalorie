const test = require('node:test');
const assert = require('node:assert/strict');
const {
  catalogueUpdate,
  userCopyUpdate,
  frequencyUpdate,
  createdMealUpdate,
  submissionUpdate,
  isBlankCatalogueName,
} = require('./slovak-name-backfill');
const { foldDiacritics } = require('./diacritics');
const { searchTerms } = require('./search-terms');

function upToDate(item) {
  return {
    ...item,
    cz_name_folded: foldDiacritics(item.cz_name.toLowerCase()),
    eng_name_folded: foldDiacritics(item.eng_name.toLowerCase()),
    cz_name_search_terms: searchTerms(item.cz_name),
    eng_name_search_terms: searchTerms(item.eng_name),
    sk_name_folded: foldDiacritics(item.sk_name.toLowerCase()),
    sk_name_search_terms: searchTerms(item.sk_name),
  };
}

const asMap = (update) => Object.fromEntries(update.map(([fieldPath, value]) => [fieldPath.join('.'), value]));

test('catalogueUpdate fills sk_name from cz_name and derives the three Slovak fields', () => {
  const update = asMap(catalogueUpdate({ cz_name: 'Mléko', eng_name: 'Milk' }));

  assert.equal(update.sk_name, 'Mléko');
  assert.equal(update.sk_name_folded, 'mleko');
  assert.deepEqual(update.sk_name_search_terms, ['m', 'ml', 'mle', 'mlek', 'mleko']);
});

test('catalogueUpdate falls back to eng_name when cz_name is empty', () => {
  assert.equal(asMap(catalogueUpdate({ cz_name: '', eng_name: 'Milk' })).sk_name, 'Milk');
});

test('catalogueUpdate keeps an existing translation and derives its folded fields from it', () => {
  const update = asMap(catalogueUpdate({ cz_name: 'Mléko', eng_name: 'Milk', sk_name: 'Mäso' }));

  assert.equal(update.sk_name, undefined, 'a stored translation is not rewritten');
  assert.equal(update.sk_name_folded, 'maso');
});

test('catalogueUpdate recomputes cz_ and eng_ folded fields under the extended fold map', () => {
  const stale = { cz_name: 'Mäso', eng_name: 'Meat', cz_name_folded: 'maso'.replace('a', 'ä') };
  const update = asMap(catalogueUpdate(stale));

  assert.equal(update.cz_name_folded, 'maso');
});

test('catalogueUpdate is idempotent: a document it already completed needs nothing', () => {
  const first = asMap(catalogueUpdate({ cz_name: 'Mléko', eng_name: 'Milk' }));
  const written = upToDate({ cz_name: 'Mléko', eng_name: 'Milk', sk_name: first.sk_name });

  assert.equal(catalogueUpdate(written), null);
});

test('catalogueUpdate prefixes every path for a submission item', () => {
  const update = catalogueUpdate({ cz_name: 'Mléko', eng_name: 'Milk' }, ['item']);

  assert.ok(update.every(([fieldPath]) => fieldPath[0] === 'item'));
});

test('catalogueUpdate skips a document without cz_name and eng_name strings', () => {
  assert.equal(catalogueUpdate({ cz_name: 'Mléko' }), null);
});

test('isBlankCatalogueName reports a whitespace-only cz_name or eng_name', () => {
  assert.equal(isBlankCatalogueName({ cz_name: '  ', eng_name: 'Milk' }), true);
  assert.equal(isBlankCatalogueName({ cz_name: 'Mléko', eng_name: '' }), true);
  assert.equal(isBlankCatalogueName({ cz_name: 'Mléko', eng_name: 'Milk' }), false);
});

test('submissionUpdate marks a legacy submission as Czech and leaves its Slovak name empty for the maintainer', () => {
  const update = new Map(
    submissionUpdate({ item: { cz_name: 'Mléko', eng_name: '' } }).map(([fieldPath, value]) => [fieldPath.join('.'), value]),
  );
  assert.equal(update.get('name_language'), 'cs');
  assert.equal(update.get('item.sk_name'), '');
  assert.deepEqual(update.get('item.sk_name_search_terms'), []);
});

test('submissionUpdate is idempotent', () => {
  const data = { item: { cz_name: 'Mléko', eng_name: '' } };
  for (const [fieldPath, value] of submissionUpdate(data)) {
    const target = fieldPath.length === 1 ? data : data.item;
    target[fieldPath[fieldPath.length - 1]] = value;
  }
  assert.equal(submissionUpdate(data), null);
});

test('userCopyUpdate writes sk_name from the copy’s own names, not from the catalogue', () => {
  assert.deepEqual(userCopyUpdate({ cz_name: 'Mléko', eng_name: 'Milk' }), [[['sk_name'], 'Mléko']]);
  assert.deepEqual(userCopyUpdate({ cz_name: '', eng_name: 'Milk' }), [[['sk_name'], 'Milk']]);
});

test('userCopyUpdate leaves a copy that already has sk_name alone', () => {
  assert.equal(userCopyUpdate({ cz_name: 'Mléko', eng_name: 'Milk', sk_name: 'Mlieko' }), null);
});

test('userCopyUpdate leaves a copy whose sk_name is legitimately empty alone', () => {
  assert.equal(userCopyUpdate({ cz_name: 'Rohlík', eng_name: '', sk_name: '' }), null);
});

test('frequencyUpdate leaves an entry whose sk_name is legitimately empty alone', () => {
  assert.equal(frequencyUpdate({ entries: { a: { item: { cz_name: '', eng_name: 'Oat', sk_name: '' } } } }), null);
});

test('createdMealUpdate leaves an ingredient whose sk_name is legitimately empty alone and fills only the missing one', () => {
  assert.equal(createdMealUpdate({ ingredients: [{ cz_name: '', eng_name: 'Oat bowl', sk_name: '' }] }), null);
  const update = createdMealUpdate({
    ingredients: [
      { cz_name: '', eng_name: 'Oat', sk_name: '' },
      { cz_name: 'Mléko', eng_name: 'Milk' },
    ],
  });
  assert.deepEqual(update[0][1].map((ingredient) => ingredient.sk_name), ['', 'Mléko']);
});

test('frequencyUpdate addresses each entry that lacks sk_name by its own path', () => {
  const update = frequencyUpdate({
    entries: {
      a: { item: { cz_name: 'Mléko', eng_name: 'Milk' } },
      b: { item: { cz_name: 'Chléb', eng_name: 'Bread', sk_name: 'Chlieb' } },
    },
  });

  assert.deepEqual(update, [[['entries', 'a', 'item', 'sk_name'], 'Mléko']]);
});

test('frequencyUpdate ignores a stats document without entries', () => {
  assert.equal(frequencyUpdate({ something: 1 }), null);
});

test('createdMealUpdate adds sk_name to the ingredients that lack it and keeps the others', () => {
  const update = createdMealUpdate({
    ingredients: [
      { cz_name: 'Mléko', eng_name: 'Milk', grams: 100 },
      { cz_name: 'Chléb', eng_name: 'Bread', sk_name: 'Chlieb', grams: 50 },
    ],
  });

  assert.deepEqual(update, [[['ingredients'], [
    { cz_name: 'Mléko', eng_name: 'Milk', grams: 100, sk_name: 'Mléko' },
    { cz_name: 'Chléb', eng_name: 'Bread', sk_name: 'Chlieb', grams: 50 },
  ]]]);
});

test('createdMealUpdate needs nothing when every ingredient has sk_name', () => {
  assert.equal(createdMealUpdate({ ingredients: [{ cz_name: 'Mléko', eng_name: 'Milk', sk_name: 'Mlieko' }] }), null);
});
