#!/usr/bin/env node

const { searchTerms } = require('./lib/search-terms');
const { runBackfill } = require('./lib/backfill-runner');

function sameTerms(stored, computed) {
  return Array.isArray(stored) && stored.length === computed.length && stored.every((term, i) => term === computed[i]);
}

runBackfill({
  usage: 'Usage: node backfill-search-terms.js --key <path-to-service-account.json> [--dry-run]',
  collectionName: 'foodItems',
  alreadyDoneLabel: 'Search terms already up to date',
  isAlreadyDone: (data) =>
    typeof data.cz_name === 'string' &&
    typeof data.eng_name === 'string' &&
    sameTerms(data.cz_name_search_terms, searchTerms(data.cz_name)) &&
    sameTerms(data.eng_name_search_terms, searchTerms(data.eng_name)),
  computeFields: (data) => ({
    cz_name_search_terms: searchTerms(data.cz_name),
    eng_name_search_terms: searchTerms(data.eng_name),
  }),
}).catch((err) => {
  console.error(err);
  process.exit(1);
});
