const { foldDiacritics } = require('./diacritics');
const { searchTerms } = require('./search-terms');
const { slovakName } = require('./name-rules');

function hasName(value) {
  return typeof value === 'string' && value.trim().length > 0;
}

function hasSlovakName(value) {
  return typeof value === 'string';
}

function sameValue(stored, computed) {
  if (Array.isArray(computed)) {
    return Array.isArray(stored) && stored.length === computed.length && stored.every((term, i) => term === computed[i]);
  }
  return stored === computed;
}

function catalogueFieldValues(item) {
  const sk = hasName(item.sk_name) ? item.sk_name : slovakName(null, item.cz_name, item.eng_name);
  return {
    sk_name: sk,
    sk_name_folded: foldDiacritics(sk.toLowerCase()),
    sk_name_search_terms: searchTerms(sk),
    cz_name_folded: foldDiacritics(item.cz_name.toLowerCase()),
    eng_name_folded: foldDiacritics(item.eng_name.toLowerCase()),
    cz_name_search_terms: searchTerms(item.cz_name),
    eng_name_search_terms: searchTerms(item.eng_name),
  };
}

function hasCatalogueNames(item) {
  return item && typeof item.cz_name === 'string' && typeof item.eng_name === 'string';
}

function catalogueUpdate(item, pathPrefix = []) {
  if (!hasCatalogueNames(item)) return null;
  const wanted = catalogueFieldValues(item);
  const stale = Object.entries(wanted).filter(([field, value]) => !sameValue(item[field], value));
  if (stale.length === 0) return null;
  return stale.map(([field, value]) => [[...pathPrefix, field], value]);
}

function userCopyUpdate(data) {
  if (hasSlovakName(data.sk_name)) return null;
  return [[['sk_name'], slovakName(null, data.cz_name, data.eng_name)]];
}

function frequencyUpdate(data) {
  if (data.entries === null || typeof data.entries !== 'object') return null;
  const updates = [];
  for (const [key, entry] of Object.entries(data.entries)) {
    const item = entry?.item;
    if (!item || hasSlovakName(item.sk_name)) continue;
    updates.push([['entries', key, 'item', 'sk_name'], slovakName(null, item.cz_name, item.eng_name)]);
  }
  return updates.length > 0 ? updates : null;
}

function createdMealUpdate(data) {
  if (!Array.isArray(data.ingredients)) return null;
  if (data.ingredients.every((ingredient) => hasSlovakName(ingredient?.sk_name))) return null;
  const ingredients = data.ingredients.map((ingredient) =>
    hasSlovakName(ingredient?.sk_name)
      ? ingredient
      : { ...ingredient, sk_name: slovakName(null, ingredient?.cz_name, ingredient?.eng_name) },
  );
  return [[['ingredients'], ingredients]];
}

function submissionUpdate(data) {
  const item = data.item;
  if (!hasCatalogueNames(item)) return null;
  const sk = typeof item.sk_name === 'string' ? item.sk_name : '';
  const wanted = {
    sk_name: sk,
    sk_name_folded: foldDiacritics(sk.toLowerCase()),
    sk_name_search_terms: searchTerms(sk),
    cz_name_folded: foldDiacritics(item.cz_name.toLowerCase()),
    eng_name_folded: foldDiacritics(item.eng_name.toLowerCase()),
    cz_name_search_terms: searchTerms(item.cz_name),
    eng_name_search_terms: searchTerms(item.eng_name),
  };
  const updates = Object.entries(wanted)
    .filter(([field, value]) => !sameValue(item[field], value))
    .map(([field, value]) => [['item', field], value]);
  if (typeof data.name_language !== 'string') updates.push([['name_language'], 'cs']);
  return updates.length > 0 ? updates : null;
}

function isBlankCatalogueName(item) {
  return Boolean(item) && [item.cz_name, item.eng_name].some((name) => typeof name === 'string' && name.trim().length === 0);
}

module.exports = {
  catalogueFieldValues,
  catalogueUpdate,
  submissionUpdate,
  userCopyUpdate,
  frequencyUpdate,
  createdMealUpdate,
  isBlankCatalogueName,
};
