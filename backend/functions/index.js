const { onDocumentWritten } = require('firebase-functions/v2/firestore');
const { initializeApp } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');
const { propagateCurrent } = require('./propagate');

initializeApp();

exports.propagateCatalogueChanges = onDocumentWritten(
  { document: 'foodItems/{id}', region: 'europe-west1', timeoutSeconds: 540, memory: '512MiB' },
  (event) =>
    propagateCurrent(
      getFirestore(),
      event.params.id,
      event.data.before.exists ? event.data.before.data() : null,
    ),
);
