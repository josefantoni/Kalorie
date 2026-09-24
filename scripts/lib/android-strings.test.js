const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { generateAndroidStrings } = require('./android-strings');

function entry(cs, en = cs) {
  return { localizations: { cs: { stringUnit: { value: cs } }, en: { stringUnit: { value: en } } } };
}

function generate(strings) {
  return generateAndroidStrings({ sourceLanguage: 'cs', strings });
}

test('generate_writesCzechAndEnglishFilesFromTheSameKeys', () => {
  const { cs, en } = generate({ common_ok: entry('Dobrá', 'OK') });
  assert.match(cs, /<string name="common_ok">Dobrá<\/string>/);
  assert.match(en, /<string name="common_ok">OK<\/string>/);
});

test('generate_skipsKeysThatAreLiteralsHarvestedFromText', () => {
  const csOnlyJunk = { localizations: { cs: { stringUnit: { value: 'x' } } } };
  const { cs } = generate({ common_ok: entry('Dobrá'), '': {}, '%lld kcal': csOnlyJunk, kcal: {}, g: {}, '100': {} });
  assert.equal(cs.match(/<string /g).length, 1);
});

test('generate_sortsKeysSoDiffsStayStable', () => {
  const { cs } = generate({ b_key: entry('B'), a_key: entry('A') });
  assert.ok(cs.indexOf('a_key') < cs.indexOf('b_key'));
});

test('generate_convertsObjectiveCAndLongSpecifiersToPositionalAndroidOnes', () => {
  const { cs } = generate({
    one: entry('Zamítnuto: %@'),
    two: entry('%lld× nahlášeno'),
    three: entry('%@ a %lld'),
    four: entry('%1$lld kcal / 100 %2$@'),
  });
  assert.match(cs, />Zamítnuto: %1\$s</);
  assert.match(cs, />%1\$d× nahlášeno</);
  assert.match(cs, />%1\$s a %2\$d</);
  assert.match(cs, />%1\$d kcal \/ 100 %2\$s</);
});

test('generate_keepsLiteralPercentAsDoubleOnlyWhenTheStringTakesArguments', () => {
  const { cs } = generate({ with_args: entry('%@ 100%%'), no_args: entry('100%%') });
  assert.match(cs, />%1\$s 100%%</);
  assert.match(cs, />100%</);
});

test('generate_escapesXmlAndAndroidSpecialCharacters', () => {
  const { cs } = generate({
    a: entry(`Tom & "Jerry" <b> it's`),
    b: entry('@home'),
    c: entry('?what'),
    d: entry('řádek\nřádek'),
  });
  assert.match(cs, />Tom &amp; \\"Jerry\\" &lt;b> it\\'s</);
  assert.match(cs, />\\@home</);
  assert.match(cs, />\\\?what</);
  assert.match(cs, />řádek\\nřádek</);
});

test('generate_throwsWhenAValidKeyHasNoEnglishTranslation', () => {
  const missing = { localizations: { cs: { stringUnit: { value: 'Ano' } } } };
  assert.throws(() => generate({ common_yes: missing }), /no "en" localization/);
});

test('generate_throwsOnPluralVariations', () => {
  const plural = {
    localizations: {
      cs: { variations: { plural: {} } },
      en: { stringUnit: { value: 'x' } },
    },
  };
  assert.throws(() => generate({ some_count: plural }), /variations or substitutions/);
});

test('generate_throwsOnWhitespaceAndroidWouldDrop', () => {
  assert.throws(() => generate({ padded: entry(' Ano') }), /leading or trailing whitespace/);
});

test('generate_throwsOnAnUnsupportedSpecifier', () => {
  assert.throws(() => generate({ odd: entry('%x') }), /Unsupported format specifier/);
});

test('generate_realCatalogueProducesEveryKeyAndroidNeedsForItsDashboard', () => {
  const catalogue = JSON.parse(
    fs.readFileSync(path.join(__dirname, '..', '..', 'iOS', 'Kalorie', 'Resources', 'Localizable.xcstrings'), 'utf8')
  );
  const { cs, en } = generateAndroidStrings(catalogue);
  for (const key of ['common_ok', 'dashboard_empty_title', 'defaultMeals_breakfast']) {
    assert.match(cs, new RegExp(`name="${key}"`));
    assert.match(en, new RegExp(`name="${key}"`));
  }
});
