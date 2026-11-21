#!/usr/bin/env node

const { runBackfill } = require('./lib/backfill-runner');
const {
  catalogueUpdate,
  userCopyUpdate,
  frequencyUpdate,
  createdMealUpdate,
  submissionUpdate,
  isBlankCatalogueName,
} = require('./lib/slovak-name-backfill');

function blankCatalogueNameReport(label) {
  const blank = [];
  return {
    visit: (doc, data) => {
      if (isBlankCatalogueName(data)) blank.push(doc.ref.path);
    },
    report: () => {
      console.log(`${label} with an empty cz_name or eng_name: ${blank.length}`);
      for (const path of blank) console.warn(`  ${path}`);
    },
  };
}

const passes = [
  {
    collectionName: 'foodItems',
    alreadyDoneLabel: 'Catalogue items already complete',
    isAlreadyDone: (data) => typeof data.cz_name === 'string' && typeof data.eng_name === 'string' && catalogueUpdate(data) === null,
    computeFields: (data) => catalogueUpdate(data),
    afterScan: blankCatalogueNameReport('foodItems'),
  },
  {
    collectionName: 'foodItemSubmissions',
    alreadyDoneLabel: 'Submissions already complete',
    requireNames: false,
    isAlreadyDone: (data) => submissionUpdate(data) === null,
    computeFields: submissionUpdate,
  },
  {
    collectionName: 'foodConsumed',
    collectionGroup: true,
    requireNames: false,
    alreadyDoneLabel: 'Entries already complete',
    isAlreadyDone: (data) => userCopyUpdate(data) === null,
    computeFields: userCopyUpdate,
  },
  {
    collectionName: 'favouriteFoods',
    collectionGroup: true,
    requireNames: false,
    alreadyDoneLabel: 'Favourites already complete',
    isAlreadyDone: (data) => userCopyUpdate(data) === null,
    computeFields: userCopyUpdate,
  },
  {
    collectionName: 'stats',
    collectionGroup: true,
    requireNames: false,
    alreadyDoneLabel: 'Stats documents already complete',
    isAlreadyDone: (data) => frequencyUpdate(data) === null,
    computeFields: frequencyUpdate,
  },
  {
    collectionName: 'myCreatedMeals',
    collectionGroup: true,
    requireNames: false,
    alreadyDoneLabel: 'Created meals already complete',
    isAlreadyDone: (data) => createdMealUpdate(data) === null,
    computeFields: createdMealUpdate,
  },
];

(async () => {
  for (const pass of passes) {
    await runBackfill({
      usage: 'Usage: node backfill-slovak-names.js --key <path-to-service-account.json> [--dry-run]',
      ...pass,
    });
  }
})().catch((err) => {
  console.error(err);
  process.exit(1);
});
