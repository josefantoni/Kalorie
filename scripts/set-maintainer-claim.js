#!/usr/bin/env node

const admin = require('firebase-admin');
const path = require('path');

function parseArgs(argv) {
  const args = { key: null, uid: null, remove: false };
  for (let i = 2; i < argv.length; i++) {
    if (argv[i] === '--key') {
      args.key = argv[++i];
    } else if (argv[i] === '--uid') {
      args.uid = argv[++i];
    } else if (argv[i] === '--remove') {
      args.remove = true;
    }
  }
  return args;
}

async function main() {
  const usage = 'Usage: node set-maintainer-claim.js --key <path-to-service-account.json> --uid <firebase-uid> [--remove]';
  const args = parseArgs(process.argv);
  if (!args.key || !args.uid) {
    console.error(usage);
    process.exit(1);
  }

  const serviceAccount = require(path.resolve(args.key));
  admin.initializeApp({ credential: admin.credential.cert(serviceAccount) });

  const claims = args.remove ? {} : { maintainer: true };
  await admin.auth().setCustomUserClaims(args.uid, claims);

  const user = await admin.auth().getUser(args.uid);
  console.log(`Claims for ${args.uid}:`, user.customClaims || {});
  console.log('Sign out and back in on the device (or force a token refresh) for the claim to reach the client.');
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
