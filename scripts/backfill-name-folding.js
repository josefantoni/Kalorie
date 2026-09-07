#!/usr/bin/env node

const { foldDiacritics } = require('./lib/diacritics');
const { runBackfill } = require('./lib/backfill-runner');

runBackfill({
  usage: 'Usage: node backfill-name-folding.js --key <path-to-service-account.json> [--dry-run]',
  collectionName: 'foodItems',
  alreadyDoneLabel: 'Already folded',
  isAlreadyDone: (data) => typeof data.cz_name_folded === 'string' && typeof data.eng_name_folded === 'string',
  computeFields: (data) => ({
    cz_name_folded: foldDiacritics(data.cz_name.toLowerCase()),
    eng_name_folded: foldDiacritics(data.eng_name.toLowerCase()),
  }),
}).catch((err) => {
  console.error(err);
  process.exit(1);
});
