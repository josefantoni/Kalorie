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

function connect(args) {
  if (admin.apps.length === 0) {
    const serviceAccount = require(path.resolve(args.key));
    admin.initializeApp({ credential: admin.credential.cert(serviceAccount) });
  }
  return admin.firestore();
}

function applyFields(batch, ref, fields) {
  if (!Array.isArray(fields)) {
    batch.update(ref, fields);
    return;
  }
  batch.update(ref, ...fields.flatMap(([fieldPath, value]) => [new admin.firestore.FieldPath(...fieldPath), value]));
}

async function runBackfill({
  usage,
  collectionName,
  collectionGroup = false,
  requireNames = true,
  alreadyDoneLabel,
  isAlreadyDone,
  computeFields,
  afterScan,
}) {
  const args = parseArgs(process.argv);
  if (!args.key) {
    console.error(usage);
    process.exit(1);
  }

  const db = connect(args);
  const query = collectionGroup ? db.collectionGroup(collectionName) : db.collection(collectionName);
  const snapshot = await query.get();
  console.log(`${collectionName}${collectionGroup ? ' (collection group)' : ''}: ${snapshot.size} documents`);

  const toUpdate = [];
  let skippedMissingName = 0;
  let alreadyDone = 0;

  for (const doc of snapshot.docs) {
    const data = doc.data();
    if (afterScan) afterScan.visit?.(doc, data);
    if (isAlreadyDone(data)) {
      alreadyDone++;
      continue;
    }
    if (requireNames && (typeof data.cz_name !== 'string' || typeof data.eng_name !== 'string')) {
      console.warn(`Skipping ${doc.ref.path}: missing cz_name/eng_name`);
      skippedMissingName++;
      continue;
    }
    toUpdate.push({ ref: doc.ref, fields: computeFields(data) });
  }

  console.log(`${alreadyDoneLabel}: ${alreadyDone}`);
  if (requireNames) console.log(`Missing cz_name/eng_name (skipped): ${skippedMissingName}`);
  console.log(`To update: ${toUpdate.length}`);
  if (afterScan) afterScan.report?.();

  if (args.dryRun) {
    console.log('Dry run — no writes made. Sample:', toUpdate.slice(0, 5).map(({ ref, fields }) => ({ path: ref.path, fields })));
    return;
  }

  for (let i = 0; i < toUpdate.length; i += args.batchSize) {
    const chunk = toUpdate.slice(i, i + args.batchSize);
    const batch = db.batch();
    for (const item of chunk) {
      applyFields(batch, item.ref, item.fields);
    }
    await batch.commit();
    console.log(`Committed ${i + chunk.length}/${toUpdate.length}`);
  }

  console.log('Done.');
}

module.exports = { runBackfill };
