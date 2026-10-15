#!/usr/bin/env node

const admin = require('firebase-admin');
const path = require('path');

function parseArgs(argv) {
  const args = { key: null, apply: false };
  for (let i = 2; i < argv.length; i++) {
    if (argv[i] === '--key') {
      args.key = argv[++i];
    } else if (argv[i] === '--apply') {
      args.apply = true;
    }
  }
  return args;
}

async function main() {
  const usage = 'Usage: node cleanup-merged-anonymous-accounts.js --key <path-to-service-account.json> [--apply]';
  const args = parseArgs(process.argv);
  if (!args.key) {
    console.error(usage);
    process.exit(1);
  }

  const serviceAccount = require(path.resolve(args.key));
  admin.initializeApp({ credential: admin.credential.cert(serviceAccount) });
  const db = admin.firestore();

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

    console.log(`${args.apply ? 'DELETE' : 'WOULD DELETE'} ${sourceUid} (merged into ${targetUid}${source ? '' : ', auth account already gone'})`);
    if (!args.apply) continue;

    await db.recursiveDelete(db.doc(`users/${sourceUid}`));
    if (source) await admin.auth().deleteUser(sourceUid);
    await marker.ref.delete();
  }
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
