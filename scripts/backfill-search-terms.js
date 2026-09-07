#!/usr/bin/env node

const { searchTerms } = require('./lib/search-terms');
const { runBackfill } = require('./lib/backfill-runner');

runBackfill({
  usage: 'Usage: node backfill-search-terms.js --key <path-to-service-account.json> [--dry-run]',
  collectionName: 'foodItems',
  alreadyDoneLabel: 'Already has search terms',
  isAlreadyDone: (data) => Array.isArray(data.cz_name_search_terms) && Array.isArray(data.eng_name_search_terms),
  computeFields: (data) => ({
    cz_name_search_terms: searchTerms(data.cz_name),
    eng_name_search_terms: searchTerms(data.eng_name),
  }),
}).catch((err) => {
  console.error(err);
  process.exit(1);
});
