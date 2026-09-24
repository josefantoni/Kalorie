const KEY_PATTERN = /^[a-z][A-Za-z0-9_]*$/;
const SPECIFIER = /%(?:(\d+)\$)?(@|lld|ld|d|(?:\.\d+)?f)|%%/g;
const LANGUAGES = ['cs', 'en'];

function validateSource(source) {
  const keys = Object.keys(source).sort();
  for (const key of keys) {
    if (!KEY_PATTERN.test(key)) throw new Error(`Invalid key ${JSON.stringify(key)}`);
    const entry = source[key];
    const unknown = Object.keys(entry).filter((language) => !LANGUAGES.includes(language));
    if (unknown.length > 0) throw new Error(`Key "${key}" has unknown language(s): ${unknown.join(', ')}`);
    for (const language of LANGUAGES) {
      const value = entry[language];
      if (typeof value !== 'string' || value.length === 0) {
        throw new Error(`Key "${key}" has no "${language}" text`);
      }
      if (value !== value.trim()) {
        throw new Error(`Key "${key}" has leading or trailing whitespace in "${language}", which Android would drop`);
      }
    }
  }
  return keys;
}

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

function generateAndroidStrings(source) {
  const keys = validateSource(source);
  const result = {};
  for (const language of LANGUAGES) {
    const lines = keys.map((key) => {
      const value = escapeAndroid(convertFormat(source[key][language]));
      return `    <string name="${key}">${value}</string>`;
    });
    result[language] = `<?xml version="1.0" encoding="utf-8"?>\n<resources>\n${lines.join('\n')}\n</resources>\n`;
  }
  return result;
}

function xcodeJson(value, indent = '') {
  if (typeof value === 'string') return JSON.stringify(value);
  const inner = `${indent}  `;
  const lines = Object.entries(value).map(([key, child]) => `${inner}${JSON.stringify(key)} : ${xcodeJson(child, inner)}`);
  return `{\n${lines.join(',\n')}\n${indent}}`;
}

// Xcode orders catalogue keys ignoring case and rewrites the file on build, so match it.
function xcodeOrder(a, b) {
  const [x, y] = [a.toLowerCase(), b.toLowerCase()];
  if (x !== y) return x < y ? -1 : 1;
  return a < b ? -1 : a > b ? 1 : 0;
}

// Xcode adds keys it extracts from source code; they are not in strings.json and must survive.
function extractedEntries(existing, source) {
  if (!existing) return {};
  const { strings = {} } = JSON.parse(existing);
  return Object.fromEntries(
    Object.entries(strings).filter(([key, value]) => !(key in source) && value.extractionState !== 'manual')
  );
}

function generateXcstrings(source, existing = null) {
  const keys = validateSource(source);
  const entries = extractedEntries(existing, source);
  for (const key of keys) {
    const localizations = {};
    for (const language of LANGUAGES) {
      localizations[language] = { stringUnit: { state: 'translated', value: source[key][language] } };
    }
    entries[key] = { extractionState: 'manual', localizations };
  }
  const strings = Object.fromEntries(Object.keys(entries).sort(xcodeOrder).map((key) => [key, entries[key]]));
  return xcodeJson({ sourceLanguage: 'cs', strings, version: '1.0' });
}

module.exports = { generateAndroidStrings, generateXcstrings };
