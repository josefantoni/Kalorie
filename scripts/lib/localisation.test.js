const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { generateAndroidStrings, generateXcstrings } = require('./localisation');

function entry(cs, en = cs) {
  return { cs, en };
}

test('android_writesCzechAndEnglishFilesFromTheSameKeys', () => {
  const { cs, en } = generateAndroidStrings({ common_ok: entry('Dobrá', 'OK') });
  assert.match(cs, /<string name="common_ok">Dobrá<\/string>/);
  assert.match(en, /<string name="common_ok">OK<\/string>/);
});

test('android_sortsKeysSoDiffsStayStable', () => {
  const { cs } = generateAndroidStrings({ b_key: entry('B'), a_key: entry('A') });
  assert.ok(cs.indexOf('a_key') < cs.indexOf('b_key'));
});

test('android_convertsObjectiveCAndLongSpecifiersToPositionalAndroidOnes', () => {
  const { cs } = generateAndroidStrings({
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

test('android_keepsLiteralPercentAsDoubleOnlyWhenTheStringTakesArguments', () => {
  const { cs } = generateAndroidStrings({ with_args: entry('%@ 100%%'), no_args: entry('100%%') });
  assert.match(cs, />%1\$s 100%%</);
  assert.match(cs, />100%</);
});

test('android_escapesXmlAndAndroidSpecialCharacters', () => {
  const { cs } = generateAndroidStrings({
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

test('source_throwsWhenAKeyHasNoEnglishText', () => {
  assert.throws(() => generateAndroidStrings({ common_yes: { cs: 'Ano' } }), /no "en" text/);
  assert.throws(() => generateXcstrings({ common_yes: { cs: 'Ano' } }), /no "en" text/);
});

test('source_throwsOnAnEmptyText', () => {
  assert.throws(() => generateAndroidStrings({ common_yes: entry('') }), /no "cs" text/);
});

test('source_throwsOnAnUnknownLanguageSoATypoIsNotSilentlyIgnored', () => {
  assert.throws(() => generateAndroidStrings({ common_yes: { cs: 'Ano', en: 'Yes', EN: 'Yes' } }), /unknown language/);
});

test('source_throwsOnAKeyAndroidCannotUseAsAResourceName', () => {
  assert.throws(() => generateAndroidStrings({ 'common-ok': entry('x') }), /Invalid key/);
  assert.throws(() => generateAndroidStrings({ '': entry('x') }), /Invalid key/);
});

test('source_throwsOnWhitespaceAndroidWouldDrop', () => {
  assert.throws(() => generateAndroidStrings({ padded: entry(' Ano') }), /leading or trailing whitespace/);
});

test('android_throwsOnAnUnsupportedSpecifier', () => {
  assert.throws(() => generateAndroidStrings({ odd: entry('%x') }), /Unsupported format specifier/);
});

test('xcstrings_usesXcodesLayoutSoOpeningTheFileInXcodeDoesNotReformatIt', () => {
  const output = generateXcstrings({ common_ok: entry('Dobrá', 'OK') });
  assert.equal(
    output,
    [
      '{',
      '  "sourceLanguage" : "cs",',
      '  "strings" : {',
      '    "common_ok" : {',
      '      "extractionState" : "manual",',
      '      "localizations" : {',
      '        "cs" : {',
      '          "stringUnit" : {',
      '            "state" : "translated",',
      '            "value" : "Dobrá"',
      '          }',
      '        },',
      '        "en" : {',
      '          "stringUnit" : {',
      '            "state" : "translated",',
      '            "value" : "OK"',
      '          }',
      '        }',
      '      }',
      '    }',
      '  },',
      '  "version" : "1.0"',
      '}',
    ].join('\n')
  );
});

test('xcstrings_ordersKeysIgnoringCaseSoAnXcodeRewriteDoesNotMakeTheCheckFail', () => {
  const output = JSON.parse(generateXcstrings({ account_signInProvider_apple: entry('Apple'), account_signedIn_name: entry('Jméno') }));
  assert.deepEqual(Object.keys(output.strings), ['account_signedIn_name', 'account_signInProvider_apple']);
});

test('xcstrings_keepsKeysXcodeExtractedFromCodeAndDropsRemovedManualKeys', () => {
  const existing = JSON.stringify({
    strings: {
      g: {},
      removed_key: { extractionState: 'manual', localizations: {} },
      common_ok: { extractionState: 'manual', localizations: {} },
    },
  });
  const output = JSON.parse(generateXcstrings({ common_ok: entry('Dobrá', 'OK') }, existing));
  assert.deepEqual(Object.keys(output.strings), ['common_ok', 'g']);
  assert.equal(output.strings.common_ok.localizations.en.stringUnit.value, 'OK');
});

test('xcstrings_isStableWhenRegeneratedFromItsOwnOutput', () => {
  const source = { common_ok: entry('Dobrá', 'OK') };
  const first = generateXcstrings(source, JSON.stringify({ strings: { g: {} } }));
  assert.equal(generateXcstrings(source, first), first);
});

test('xcstrings_keepsFormatSpecifiersAsWrittenInTheSource', () => {
  const output = JSON.parse(generateXcstrings({ moderation_reports_count: entry('%lld× nahlášeno', '%lld× reported') }));
  assert.equal(output.strings.moderation_reports_count.localizations.cs.stringUnit.value, '%lld× nahlášeno');
});

test('realSource_generatesEveryKeyInBothFormats', () => {
  const source = JSON.parse(fs.readFileSync(path.join(__dirname, '..', '..', 'localisation', 'strings.json'), 'utf8'));
  const keys = Object.keys(source);
  const catalogue = JSON.parse(generateXcstrings(source));
  const { cs, en } = generateAndroidStrings(source);
  assert.deepEqual(Object.keys(catalogue.strings).sort(), [...keys].sort());
  for (const key of ['common_ok', 'dashboard_empty_title', 'defaultMeals_breakfast']) {
    assert.ok(keys.includes(key));
  }
  for (const key of keys) {
    assert.ok(cs.includes(`name="${key}"`), `${key} missing from Czech strings.xml`);
    assert.ok(en.includes(`name="${key}"`), `${key} missing from English strings.xml`);
  }
});
