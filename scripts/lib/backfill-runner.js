const admin = require('firebase-admin');
const path = require('path');

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

async function runBackfill({ usage, collectionName, alreadyDoneLabel, isAlreadyDone, computeFields }) {
  const args = parseArgs(process.argv);
  if (!args.key) {
    console.error(usage);
    process.exit(1);
  }

  const serviceAccount = require(path.resolve(args.key));
  admin.initializeApp({ credential: admin.credential.cert(serviceAccount) });
  const db = admin.firestore();

  const snapshot = await db.collection(collectionName).get();
  console.log(`${collectionName}: ${snapshot.size} documents`);

  const toUpdate = [];
  let skippedMissingName = 0;
  let alreadyDone = 0;

  for (const doc of snapshot.docs) {
    const data = doc.data();
    if (isAlreadyDone(data)) {
      alreadyDone++;
      continue;
    }
    if (typeof data.cz_name !== 'string' || typeof data.eng_name !== 'string') {
      console.warn(`Skipping ${doc.id}: missing cz_name/eng_name`);
      skippedMissingName++;
      continue;
    }
    toUpdate.push({ id: doc.id, fields: computeFields(data) });
  }

  console.log(`${alreadyDoneLabel}: ${alreadyDone}`);
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
      batch.update(db.collection(collectionName).doc(item.id), item.fields);
    }
    await batch.commit();
    console.log(`Committed ${i + chunk.length}/${toUpdate.length}`);
  }

  console.log('Done.');
}

module.exports = { runBackfill };
