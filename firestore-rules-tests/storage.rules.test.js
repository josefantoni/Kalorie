const { describe, it, before, after, beforeEach } = require('node:test');
const { readFileSync } = require('node:fs');
const path = require('node:path');
const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require('@firebase/rules-unit-testing');
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
  });
});

after(async () => {
  await env.cleanup();
});

beforeEach(async () => {
  await env.clearStorage();
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
