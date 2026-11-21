#!/usr/bin/env node

const { runBackfill } = require('./lib/backfill-runner');
const { catalogueFieldValues } = require('./lib/slovak-name-backfill');

const FIELDS = ['cz_name_search_terms', 'sk_name_search_terms', 'eng_name_search_terms'];

function sameTerms(stored, computed) {
  return Array.isArray(stored) && stored.length === computed.length && stored.every((term, i) => term === computed[i]);
}

function termFields(data) {
  const wanted = catalogueFieldValues(data);
  return Object.fromEntries(FIELDS.map((field) => [field, wanted[field]]));
}

runBackfill({
  usage: 'Usage: node backfill-search-terms.js --key <path-to-service-account.json> [--dry-run]',
  collectionName: 'foodItems',
  alreadyDoneLabel: 'Search terms already up to date',
  isAlreadyDone: (data) =>
    typeof data.cz_name === 'string' &&
    typeof data.eng_name === 'string' &&
    Object.entries(termFields(data)).every(([field, value]) => sameTerms(data[field], value)),
  computeFields: termFields,
}).catch((err) => {
  console.error(err);
  process.exit(1);
});
