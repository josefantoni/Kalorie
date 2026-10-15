#!/usr/bin/env node

const admin = require('firebase-admin');
const path = require('path');

function parseArgs(argv) {
  const args = { key: null, bucket: null, apply: false };
  for (let i = 2; i < argv.length; i++) {
    if (argv[i] === '--key') {
      args.key = argv[++i];
    } else if (argv[i] === '--bucket') {
      args.bucket = argv[++i];
    } else if (argv[i] === '--apply') {
      args.apply = true;
    }
  }
  return args;
}

async function main() {
  const usage = 'Usage: node cleanup-merged-anonymous-accounts.js --key <path-to-service-account.json> --bucket <storage-bucket> [--apply]';
  const args = parseArgs(process.argv);
  if (!args.key || !args.bucket) {
    console.error(usage);
    process.exit(1);
  }

  const serviceAccount = require(path.resolve(args.key));
  admin.initializeApp({ credential: admin.credential.cert(serviceAccount), storageBucket: args.bucket });
  const db = admin.firestore();
  const bucket = admin.storage().bucket();

  const markers = await db.collectionGroup('mergedAnonymousAccounts').get();
  console.log(`${markers.size} marker(s) found. ${args.apply ? 'Deleting.' : 'Dry run, pass --apply to delete.'}`);

  for (const marker of markers.docs) {
    const sourceUid = marker.id;
    const targetUid = marker.ref.parent.parent.id;

    let source = null;
    try {
      source = await admin.auth().getUser(sourceUid);
    } catch (err) {
      if (err.code !== 'auth/user-not-found') throw err;
    }

    if (source && source.providerData.length > 0) {
      console.log(`SKIP ${sourceUid}: has a linked provider, not an anonymous account`);
      continue;
    }

    const submissions = await db.collection('foodItemSubmissions').where('submitted_by', '==', sourceUid).get();
    const reports = await db.collection('foodItemReports').where('reported_by', '==', sourceUid).get();
    const [photos] = await bucket.getFiles({ prefix: `submissionPhotos/${sourceUid}/` });

    console.log(
      `${args.apply ? 'DELETE' : 'WOULD DELETE'} ${sourceUid} (merged into ${targetUid}${source ? '' : ', auth account already gone'}), ` +
      `${submissions.size} submission(s), ${reports.size} report(s), ${photos.length} photo(s)`
    );
    if (!args.apply) continue;

    for (const doc of submissions.docs) await doc.ref.delete();
    for (const doc of reports.docs) await doc.ref.delete();
    for (const photo of photos) await photo.delete();
    await db.recursiveDelete(db.doc(`users/${sourceUid}`));
    if (source) await admin.auth().deleteUser(sourceUid);
    await marker.ref.delete();
  }
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
