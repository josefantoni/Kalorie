#!/usr/bin/env node

const { runBackfill } = require('./lib/backfill-runner');
const { catalogueFieldValues } = require('./lib/slovak-name-backfill');

const FIELDS = ['cz_name_folded', 'sk_name_folded', 'eng_name_folded'];

function foldedFields(data) {
  const wanted = catalogueFieldValues(data);
  return Object.fromEntries(FIELDS.map((field) => [field, wanted[field]]));
}

runBackfill({
  usage: 'Usage: node backfill-name-folding.js --key <path-to-service-account.json> [--dry-run]',
  collectionName: 'foodItems',
  alreadyDoneLabel: 'Already folded',
  isAlreadyDone: (data) =>
    typeof data.cz_name === 'string' &&
    typeof data.eng_name === 'string' &&
    Object.entries(foldedFields(data)).every(([field, value]) => data[field] === value),
  computeFields: foldedFields,
}).catch((err) => {
  console.error(err);
  process.exit(1);
});
