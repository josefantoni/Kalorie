#!/usr/bin/env node

const admin = require('firebase-admin');
const path = require('path');

const DIACRITIC_FOLD_MAP = {
  'á': 'a', 'Á': 'A',
  'č': 'c', 'Č': 'C',
  'ď': 'd', 'Ď': 'D',
  'é': 'e', 'É': 'E',
  'ě': 'e', 'Ě': 'E',
  'í': 'i', 'Í': 'I',
  'ň': 'n', 'Ň': 'N',
  'ó': 'o', 'Ó': 'O',
  'ř': 'r', 'Ř': 'R',
  'š': 's', 'Š': 'S',
  'ť': 't', 'Ť': 'T',
  'ú': 'u', 'Ú': 'U',
  'ů': 'u', 'Ů': 'U',
  'ý': 'y', 'Ý': 'Y',
  'ž': 'z', 'Ž': 'Z',
};

function foldDiacritics(input) {
  return input.split('').map((c) => DIACRITIC_FOLD_MAP[c] ?? c).join('');
}

function parseArgs(argv) {
  const args = { dryRun: false, key: null, batchSize: 500 };
  for (let i = 2; i < argv.length; i++) {
    if (argv[i] === '--dry-run') {
      args.dryRun = true;
    } else if (argv[i] === '--key') {
      args.key = argv[++i];
    }
  }
  return args;
}

async function main() {
  const args = parseArgs(process.argv);
  if (!args.key) {
    console.error('Usage: node backfill-name-folding.js --key <path-to-service-account.json> [--dry-run]');
    process.exit(1);
  }

  const serviceAccount = require(path.resolve(args.key));
  admin.initializeApp({ credential: admin.credential.cert(serviceAccount) });
  const db = admin.firestore();

  const snapshot = await db.collection('foodItems').get();
  console.log(`foodItems: ${snapshot.size} documents`);

  const toUpdate = [];
  let skippedMissingName = 0;
  let alreadyFolded = 0;

  for (const doc of snapshot.docs) {
    const data = doc.data();
    if (typeof data.cz_name_folded === 'string' && typeof data.eng_name_folded === 'string') {
      alreadyFolded++;
      continue;
    }
    if (typeof data.cz_name !== 'string' || typeof data.eng_name !== 'string') {
      console.warn(`Skipping ${doc.id}: missing cz_name/eng_name`);
      skippedMissingName++;
      continue;
    }
    toUpdate.push({
      id: doc.id,
      cz_name_folded: foldDiacritics(data.cz_name.toLowerCase()),
      eng_name_folded: foldDiacritics(data.eng_name.toLowerCase()),
    });
  }

  console.log(`Already folded: ${alreadyFolded}`);
  console.log(`Missing cz_name/eng_name (skipped): ${skippedMissingName}`);
  console.log(`To update: ${toUpdate.length}`);

  if (args.dryRun) {
    console.log('Dry run — no writes made. Sample:', toUpdate.slice(0, 5));
    return;
  }

  for (let i = 0; i < toUpdate.length; i += args.batchSize) {
    const chunk = toUpdate.slice(i, i + args.batchSize);
    const batch = db.batch();
    for (const item of chunk) {
      batch.update(db.collection('foodItems').doc(item.id), {
        cz_name_folded: item.cz_name_folded,
        eng_name_folded: item.eng_name_folded,
      });
    }
    await batch.commit();
    console.log(`Committed ${i + chunk.length}/${toUpdate.length}`);
  }

  console.log('Done.');
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
