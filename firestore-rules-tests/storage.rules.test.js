const { describe, it, before, after, beforeEach } = require('node:test');
const { readFileSync } = require('node:fs');
const path = require('node:path');
const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require('@firebase/rules-unit-testing');
const { doc, setDoc } = require('firebase/firestore');
const { ref, uploadBytes, getBytes, deleteObject, listAll } = require('firebase/storage');

let env;

const JPEG = { contentType: 'image/jpeg' };
const bytes = (size = 1024) => new Uint8Array(size);

const user = (uid) => env.authenticatedContext(uid).storage();
const maintainer = () => env.authenticatedContext('maintainer', { maintainer: true }).storage();
const anonymous = () => env.unauthenticatedContext().storage();

async function seed(filePath) {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await uploadBytes(ref(ctx.storage(), filePath), bytes(), JPEG);
  });
}

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-kalorie',
    storage: {
      rules: readFileSync(path.join(__dirname, '../backend/storage.rules'), 'utf8'),
    },
    firestore: {
      rules: readFileSync(path.join(__dirname, '../backend/firestore.rules'), 'utf8'),
    },
  });
});

after(async () => {
  await env.cleanup();
});

beforeEach(async () => {
  await env.clearStorage();
  await env.clearFirestore();
});

describe('submissionPhotos/{uid}', () => {
  const file = 'submissionPhotos/alice/a.jpg';

  it('lets the author upload a JPEG into their own folder', async () => {
    await assertSucceeds(uploadBytes(ref(user('alice'), file), bytes(), JPEG));
  });

  it('denies uploading into another user\'s folder', async () => {
    await assertFails(uploadBytes(ref(user('bob'), file), bytes(), JPEG));
  });

  it('denies unauthenticated uploads', async () => {
    await assertFails(uploadBytes(ref(anonymous(), file), bytes(), JPEG));
  });

  it('denies a non-JPEG upload', async () => {
    await assertFails(uploadBytes(ref(user('alice'), file), bytes(), { contentType: 'image/png' }));
  });

  it('denies an upload of 1 MiB or more', async () => {
    await assertFails(uploadBytes(ref(user('alice'), file), bytes(1024 * 1024), JPEG));
  });

  it('lets the author and the maintainer read, and nobody else', async () => {
    await seed(file);
    await assertSucceeds(getBytes(ref(user('alice'), file)));
    await assertSucceeds(getBytes(ref(maintainer(), file)));
    await assertFails(getBytes(ref(user('bob'), file)));
    await assertFails(getBytes(ref(anonymous(), file)));
  });

  it('lets the author list their own folder, and nobody else', async () => {
    await seed(file);
    await assertSucceeds(listAll(ref(user('alice'), 'submissionPhotos/alice')));
    await assertFails(listAll(ref(user('bob'), 'submissionPhotos/alice')));
    await assertFails(listAll(ref(anonymous(), 'submissionPhotos/alice')));
  });

  it('lets the author and the maintainer delete, and nobody else', async () => {
    await seed(file);
    await assertFails(deleteObject(ref(user('bob'), file)));
    await assertSucceeds(deleteObject(ref(user('alice'), file)));
    await seed(file);
    await assertSucceeds(deleteObject(ref(maintainer(), file)));
  });

  it('denies the maintainer uploading into a user\'s folder', async () => {
    await assertFails(uploadBytes(ref(maintainer(), file), bytes(), JPEG));
  });
});

describe('catalogPhotos/{itemId}', () => {
  const file = 'catalogPhotos/item-1/a.jpg';

  it('lets the maintainer upload a JPEG', async () => {
    await assertSucceeds(uploadBytes(ref(maintainer(), file), bytes(), JPEG));
  });

  it('denies a regular user uploading', async () => {
    await assertFails(uploadBytes(ref(user('alice'), file), bytes(), JPEG));
  });

  it('denies a maintainer upload that is not a JPEG under 1 MiB', async () => {
    await assertFails(uploadBytes(ref(maintainer(), file), bytes(), { contentType: 'image/png' }));
    await assertFails(uploadBytes(ref(maintainer(), file), bytes(1024 * 1024), JPEG));
  });

  it('lets any signed-in user read, but not an unauthenticated one', async () => {
    await seed(file);
    await assertSucceeds(getBytes(ref(user('alice'), file)));
    await assertFails(getBytes(ref(anonymous(), file)));
  });

  it('lets only the maintainer delete', async () => {
    await seed(file);
    await assertFails(deleteObject(ref(user('alice'), file)));
    await assertSucceeds(deleteObject(ref(maintainer(), file)));
  });
});

describe('everything else', () => {
  it('denies access outside the known folders', async () => {
    await assertFails(uploadBytes(ref(maintainer(), 'other/a.jpg'), bytes(), JPEG));
  });
});

describe('photoSuggestions/{uid}', () => {
  const ITEM = '8593807012345';
  const UUID_ITEM = '3F2504E0-4F89-41D3-9A0C-0305E82C3301';
  const file = `photoSuggestions/alice/${ITEM}.jpg`;

  const seedItem = (id, extra = {}) =>
    env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), `foodItems/${id}`), { id, ...extra });
    });

  // The Storage emulator reaches the Firestore emulator lazily, so the first cross-service rule
  // evaluations of a run are denied whatever the rules say. Retry a known-good upload until it passes.
  before(async () => {
    await seedItem(ITEM);
    let lastError;
    for (let attempt = 0; attempt < 20; attempt++) {
      try {
        await uploadBytes(ref(user('warmup'), `photoSuggestions/warmup/${ITEM}.jpg`), bytes(), JPEG);
        return;
      } catch (error) {
        lastError = error;
        await new Promise((resolve) => setTimeout(resolve, 100));
      }
    }
    throw new Error(`Storage emulator never reached Firestore: ${lastError}`);
  });

  beforeEach(() => seedItem(ITEM));

  it('lets a user upload a JPEG for a photo-less item', async () => {
    await assertSucceeds(uploadBytes(ref(user('alice'), file), bytes(), JPEG));
  });

  it('accepts a UUID-identified item', async () => {
    await seedItem(UUID_ITEM);
    await assertSucceeds(uploadBytes(ref(user('alice'), `photoSuggestions/alice/${UUID_ITEM}.jpg`), bytes(), JPEG));
  });

  it('denies uploading into another user\'s folder', async () => {
    await assertFails(uploadBytes(ref(user('bob'), file), bytes(), JPEG));
  });

  it('denies unauthenticated uploads', async () => {
    await assertFails(uploadBytes(ref(anonymous(), file), bytes(), JPEG));
  });

  it('denies a non-JPEG upload', async () => {
    await assertFails(uploadBytes(ref(user('alice'), file), bytes(), { contentType: 'image/png' }));
  });

  it('denies a file of 1 MB or more', async () => {
    await assertFails(uploadBytes(ref(user('alice'), file), bytes(1024 * 1024), JPEG));
  });

  it('denies a file name that is not an item id', async () => {
    await assertFails(uploadBytes(ref(user('alice'), 'photoSuggestions/alice/a.jpg'), bytes(), JPEG));
    await assertFails(uploadBytes(ref(user('alice'), `photoSuggestions/alice/${ITEM}.png`), bytes(), JPEG));
  });

  it('denies a photo for an item that does not exist', async () => {
    await assertFails(uploadBytes(ref(user('alice'), `photoSuggestions/alice/${UUID_ITEM}.jpg`), bytes(), JPEG));
  });

  it('denies a photo for an item that already has one', async () => {
    await seedItem(UUID_ITEM, { photo_url: 'https://example.invalid/p.jpg' });
    await assertFails(uploadBytes(ref(user('alice'), `photoSuggestions/alice/${UUID_ITEM}.jpg`), bytes(), JPEG));
  });

  describe('once the file exists', () => {
    beforeEach(() => seed(file));

    it('denies overwriting it', async () => {
      await assertFails(uploadBytes(ref(user('alice'), file), bytes(), JPEG));
    });

    it('lets the owner and a maintainer read it', async () => {
      await assertSucceeds(getBytes(ref(user('alice'), file)));
      await assertSucceeds(getBytes(ref(maintainer(), file)));
    });

    it('denies another user reading it', async () => {
      await assertFails(getBytes(ref(user('bob'), file)));
    });

    it('lets the owner and a maintainer delete it', async () => {
      await assertSucceeds(deleteObject(ref(user('alice'), file)));
      await seed(file);
      await assertSucceeds(deleteObject(ref(maintainer(), file)));
    });

    it('denies another user deleting it', async () => {
      await assertFails(deleteObject(ref(user('bob'), file)));
    });

    it('denies re-uploading after deleting the file while a suggestion is pending', async () => {
      await env.withSecurityRulesDisabled(async (ctx) => {
        await setDoc(doc(ctx.firestore(), `photoSuggestions/${ITEM}_alice`), { item_id: ITEM });
      });
      await assertSucceeds(deleteObject(ref(user('alice'), file)));
      await assertFails(uploadBytes(ref(user('alice'), file), bytes(), JPEG));
    });

    it('allows re-uploading once the pending suggestion is gone', async () => {
      await assertSucceeds(deleteObject(ref(user('alice'), file)));
      await assertSucceeds(uploadBytes(ref(user('alice'), file), bytes(), JPEG));
    });
  });
});
