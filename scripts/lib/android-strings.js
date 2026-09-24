const KEY_PATTERN = /^[a-z][A-Za-z0-9_]*$/;
const SPECIFIER = /%(?:(\d+)\$)?(@|lld|ld|d|(?:\.\d+)?f)|%%/g;
const LANGUAGES = ['cs', 'en'];

function convertFormat(value) {
  let implicitIndex = 0;
  let hasArguments = false;
  const converted = value.replace(SPECIFIER, (match, position, type) => {
    if (match === '%%') return match;
    hasArguments = true;
    const index = position === undefined ? ++implicitIndex : Number(position);
    const androidType = type === '@' ? 's' : /d$/.test(type) ? 'd' : type;
    return `%${index}$${androidType}`;
  });
  if (converted.replace(/%%|%\d+\$(?:\.\d+)?[sdf]/g, '').includes('%')) {
    throw new Error(`Unsupported format specifier in ${JSON.stringify(value)}`);
  }
  return hasArguments ? converted : converted.replace(/%%/g, '%');
}

function escapeAndroid(value) {
  const escaped = value
    .replace(/\\/g, '\\\\')
    .replace(/'/g, "\\'")
    .replace(/"/g, '\\"')
    .replace(/\n/g, '\\n')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;');
  return /^[@?]/.test(escaped) ? `\\${escaped}` : escaped;
}

function localizedValue(key, entry, language) {
  const localization = entry.localizations && entry.localizations[language];
  if (!localization) throw new Error(`Key "${key}" has no "${language}" localization`);
  if (localization.variations || localization.substitutions) {
    throw new Error(`Key "${key}" uses variations or substitutions, which the generator does not support`);
  }
  const value = localization.stringUnit && localization.stringUnit.value;
  if (typeof value !== 'string') throw new Error(`Key "${key}" has no "${language}" string unit`);
  if (value !== value.trim()) {
    throw new Error(`Key "${key}" has leading or trailing whitespace in "${language}", which Android would drop`);
  }
  return value;
}

function generateAndroidStrings(catalogue) {
  const keys = Object.keys(catalogue.strings)
    .filter((key) => KEY_PATTERN.test(key) && Object.keys(catalogue.strings[key].localizations || {}).length > 0)
    .sort();
  const result = {};
  for (const language of LANGUAGES) {
    const lines = keys.map((key) => {
      const value = escapeAndroid(convertFormat(localizedValue(key, catalogue.strings[key], language)));
      return `    <string name="${key}">${value}</string>`;
    });
    result[language] = `<?xml version="1.0" encoding="utf-8"?>\n<resources>\n${lines.join('\n')}\n</resources>\n`;
  }
  return result;
}

module.exports = { generateAndroidStrings };
