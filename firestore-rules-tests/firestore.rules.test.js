const { describe, it, before, after, beforeEach } = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const path = require('node:path');
const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require('@firebase/rules-unit-testing');
const { doc, getDoc, setDoc, updateDoc, deleteDoc, collection, query, where, getDocs, deleteField } = require('firebase/firestore');

const UUID = '3F2504E0-4F89-41D3-9A0C-0305E82C3301';
const BARCODE = '8593807012345';
const TICKET_ID = 'aB3dE5gH7jK9mN1pQ3sT';
const OTHER_ID = 'zY8xW6vU4tS2rQ0pO9nM';
const LOCALE_IDS = { cs: 'cS3dE5gH7jK9mN1pQ3sT', en: 'eN3dE5gH7jK9mN1pQ3sT', sk: 'sK3dE5gH7jK9mN1pQ3sT' };

const BUCKET_URL = 'https://firebasestorage.googleapis.com/v0/b/kalorie-bf11c.firebasestorage.app/o/';
const photoUrl = (folder, owner, name = 'a.jpg') =>
  `${BUCKET_URL}${folder}%2F${owner}%2F${name}?alt=media&token=t`;
const submissionPhoto = (uid) => photoUrl('submissionPhotos', uid);
const suggestionPhoto = (uid, itemId = BARCODE, sid = TICKET_ID) =>
  photoUrl('photoSuggestions', uid, `${itemId}%2F${sid}.jpg`);
const catalogPhoto = (itemId) => photoUrl('catalogPhotos', itemId);

let env;

function foodItem(id, overrides = {}) {
  return {
    id,
    cz_name: 'Rohlik',
    eng_name: 'Roll',
    cz_name_lowercase: 'rohlik',
    eng_name_lowercase: 'roll',
    cz_name_folded: 'rohlik',
    eng_name_folded: 'roll',
    cz_name_search_terms: ['r', 'ro', 'roh', 'rohl', 'rohli', 'rohlik'],
    eng_name_search_terms: ['r', 'ro', 'rol', 'roll'],
    weight: 100,
    date: 1_700_000_000,
    calories_per_hundred_grams: 250,
    fat: 1,
    fat_unsaturated_fatty_acids: 0.5,
    carbohydrate: 50,
    carbohydrate_pure_sugar: 2,
    protein: 8,
    salt: 1,
    ...overrides,
  };
}

function submission(id, uid, overrides = {}) {
  return {
    id,
    barcode: BARCODE,
    submitted_by: uid,
    status: 'pending',
    submitted_at: 1_700_000_000,
    item: foodItem(BARCODE, { photo_url: submissionPhoto(uid) }),
    ...overrides,
  };
}

function report(barcode, uid, overrides = {}) {
  return {
    barcode,
    reported_by: uid,
    reason: 'Calories look wrong',
    reported_at: 1_700_000_000,
    status: 'pending',
    locale: 'cs',
    hidden: false,
    ...overrides,
  };
}

function suggestion(itemId, uid, overrides = {}, id = TICKET_ID) {
  return {
    item_id: itemId,
    suggested_by: uid,
    photo_url: suggestionPhoto(uid, itemId, id),
    suggested_at: 1_700_000_000,
    status: 'pending',
    locale: 'cs',
    hidden: false,
    ...overrides,
  };
}

const anonymous = () => env.unauthenticatedContext().firestore();
const user = (uid) => env.authenticatedContext(uid).firestore();
const maintainer = (uid = 'maintainer') =>
  env.authenticatedContext(uid, { maintainer: true }).firestore();

async function seed(fn) {
  await env.withSecurityRulesDisabled(async (ctx) => fn(ctx.firestore()));
}

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-kalorie',
    firestore: {
      rules: readFileSync(path.join(__dirname, '../backend/firestore.rules'), 'utf8'),
    },
  });
});

after(async () => {
  await env.cleanup();
});

beforeEach(async () => {
  await env.clearFirestore();
});

describe('users/{userId}', () => {
  it('lets a user read and write their own subcollections', async () => {
    const db = user('alice');
    await assertSucceeds(setDoc(doc(db, 'users/alice/foodConsumed/x'), { a: 1 }));
    await assertSucceeds(getDoc(doc(db, 'users/alice/foodConsumed/x')));
  });

  it('lets a user read and write their own body weights and settings', async () => {
    const db = user('alice');
    await assertSucceeds(setDoc(doc(db, 'users/alice/bodyWeights/2026-10-27'), { id: '2026-10-27', weight_kg: 78.4 }));
    await assertSucceeds(getDoc(doc(db, 'users/alice/bodyWeights/2026-10-27')));
    await assertSucceeds(setDoc(doc(db, 'users/alice/settings/app'), { show_body_weight: false }, { merge: true }));
    await assertSucceeds(getDoc(doc(db, 'users/alice/settings/app')));
    await assertSucceeds(setDoc(doc(db, 'users/alice/settings/goals'), { goals: [{ valid_from: '2026-11-06', kcal_goal: 2000 }] }, { merge: true }));
    await assertSucceeds(getDoc(doc(db, 'users/alice/settings/goals')));
  });

  it("denies another user's body weights and settings", async () => {
    await seed(async (db) => {
      await setDoc(doc(db, 'users/alice/bodyWeights/2026-10-27'), { id: '2026-10-27', weight_kg: 78.4 });
      await setDoc(doc(db, 'users/alice/settings/app'), { show_body_weight: true });
    });
    await assertFails(getDoc(doc(user('bob'), 'users/alice/bodyWeights/2026-10-27')));
    await assertFails(setDoc(doc(user('bob'), 'users/alice/bodyWeights/2026-10-28'), { id: '2026-10-28', weight_kg: 70 }));
    await assertFails(getDoc(doc(user('bob'), 'users/alice/settings/app')));
    await assertFails(getDoc(doc(user('bob'), 'users/alice/settings/goals')));
    await assertFails(setDoc(doc(user('bob'), 'users/alice/settings/goals'), { goals: [] }));
    await assertFails(setDoc(doc(user('bob'), 'users/alice/settings/app'), { show_body_weight: false }));
    await assertFails(getDoc(doc(maintainer(), 'users/alice/bodyWeights/2026-10-27')));
  });

  it('lets an anonymous-auth user use their own uid', async () => {
    const db = env.authenticatedContext('anon-1', { firebase: { sign_in_provider: 'anonymous' } }).firestore();
    await assertSucceeds(setDoc(doc(db, 'users/anon-1/mealTypes/m'), { a: 1 }));
  });

  it("denies another user's data", async () => {
    await seed((db) => setDoc(doc(db, 'users/alice/foodConsumed/x'), { a: 1 }));
    await assertFails(getDoc(doc(user('bob'), 'users/alice/foodConsumed/x')));
    await assertFails(setDoc(doc(user('bob'), 'users/alice/foodConsumed/y'), { a: 1 }));
  });

  it('denies unauthenticated access', async () => {
    await assertFails(getDoc(doc(anonymous(), 'users/alice')));
  });

  it('gives a maintainer no access to private user data', async () => {
    await assertFails(getDoc(doc(maintainer(), 'users/alice/foodConsumed/x')));
  });
});

describe('foodItems', () => {
  it('is readable by any signed-in user and not by a signed-out one', async () => {
    await seed((db) => setDoc(doc(db, `foodItems/${BARCODE}`), foodItem(BARCODE)));
    await assertSucceeds(getDoc(doc(user('alice'), `foodItems/${BARCODE}`)));
    await assertFails(getDoc(doc(anonymous(), `foodItems/${BARCODE}`)));
  });

  it('is not writable by a regular user', async () => {
    await assertFails(setDoc(doc(user('alice'), `foodItems/${BARCODE}`), foodItem(BARCODE)));
  });

  it('is writable by a maintainer', async () => {
    await assertSucceeds(setDoc(doc(maintainer(), `foodItems/${BARCODE}`), foodItem(BARCODE)));
  });

  describe('photo_url', () => {
    const write = (photo_url) =>
      setDoc(doc(maintainer(), `foodItems/${BARCODE}`), foodItem(BARCODE, { photo_url }));

    it('is optional, so items without a photo keep saving', async () => {
      await assertSucceeds(setDoc(doc(maintainer(), `foodItems/${BARCODE}`), foodItem(BARCODE)));
    });

    it('accepts a catalogPhotos download URL', async () => {
      await assertSucceeds(write(catalogPhoto(BARCODE)));
    });

    it('denies a submissionPhotos URL, which the author could delete', async () => {
      await assertFails(write(submissionPhoto('alice')));
    });

    it('denies a URL that is not from the project bucket', async () => {
      await assertFails(write(`https://example.com/o/catalogPhotos%2F${BARCODE}%2Fa.jpg`));
      await assertFails(write(catalogPhoto(BARCODE).replace('kalorie-bf11c', 'other-project')));
    });

    it('denies a photo_url that is not a string or is too long', async () => {
      await assertFails(write(42));
      await assertFails(write(catalogPhoto(BARCODE) + 'x'.repeat(1024)));
    });
  });

  it('cannot be deleted, even by a maintainer', async () => {
    await seed((db) => setDoc(doc(db, `foodItems/${BARCODE}`), foodItem(BARCODE)));
    await assertFails(deleteDoc(doc(maintainer(), `foodItems/${BARCODE}`)));
  });

  const { itemId: itemIdCases } = JSON.parse(
    readFileSync(path.join(__dirname, '../fixtures/food-item-validation-cases.json'), 'utf8')
  );

  for (const { input, valid } of itemIdCases) {
    it(`${valid ? 'accepts' : 'rejects'} the id "${input}"`, async () => {
      const write = setDoc(doc(maintainer(), `foodItems/${input}`), foodItem(input));
      await (valid ? assertSucceeds(write) : assertFails(write));
    });
  }

  it('requires the id field to equal the document id', async () => {
    await assertFails(setDoc(doc(maintainer(), `foodItems/${BARCODE}`), foodItem('12345678')));
  });

  for (const field of [
    'cz_name', 'eng_name', 'cz_name_lowercase', 'eng_name_lowercase', 'cz_name_folded',
    'eng_name_folded', 'cz_name_search_terms', 'eng_name_search_terms', 'weight', 'date',
    'calories_per_hundred_grams', 'fat', 'fat_unsaturated_fatty_acids', 'carbohydrate',
    'carbohydrate_pure_sugar', 'protein', 'salt',
  ]) {
    it(`requires ${field}`, async () => {
      const data = foodItem(BARCODE);
      delete data[field];
      await assertFails(setDoc(doc(maintainer(), `foodItems/${BARCODE}`), data));
    });
  }

  it('rejects a numeric field written as a string', async () => {
    await assertFails(setDoc(doc(maintainer(), `foodItems/${BARCODE}`), foodItem(BARCODE, { fat: '1' })));
  });

  it('rejects search terms written as a string instead of a list', async () => {
    await assertFails(setDoc(doc(maintainer(), `foodItems/${BARCODE}`), foodItem(BARCODE, { cz_name_search_terms: 'rohlik' })));
  });

  it('rejects a name field written as a number', async () => {
    await assertFails(setDoc(doc(maintainer(), `foodItems/${BARCODE}`), foodItem(BARCODE, { cz_name: 1 })));
  });

  it('accepts the optional numeric fields when present and numeric', async () => {
    const data = foodItem(BARCODE, { energy_kj: 1000, fat_saturated: 0.2, fiber: 3 });
    await assertSucceeds(setDoc(doc(maintainer(), `foodItems/${BARCODE}`), data));
  });

  it('rejects an optional numeric field of the wrong type', async () => {
    await assertFails(setDoc(doc(maintainer(), `foodItems/${BARCODE}`), foodItem(BARCODE, { fiber: 'x' })));
  });

  it('accepts measure_unit grams and millilitres and rejects anything else', async () => {
    await assertSucceeds(setDoc(doc(maintainer(), `foodItems/${BARCODE}`), foodItem(BARCODE, { measure_unit: 'grams' })));
    await assertSucceeds(setDoc(doc(maintainer(), `foodItems/${BARCODE}`), foodItem(BARCODE, { measure_unit: 'millilitres' })));
    await assertFails(setDoc(doc(maintainer(), `foodItems/${BARCODE}`), foodItem(BARCODE, { measure_unit: 'litres' })));
  });

  it('accepts a maintainer write with a valid alcohol_by_volume', async () => {
    await assertSucceeds(setDoc(doc(maintainer(), `foodItems/${BARCODE}`), foodItem(BARCODE, { alcohol_by_volume: 12 })));
  });

  it('accepts a write without alcohol_by_volume', async () => {
    await assertSucceeds(setDoc(doc(maintainer(), `foodItems/${BARCODE}`), foodItem(BARCODE)));
  });

  for (const invalid of [0, -1, 101, '12']) {
    it(`rejects alcohol_by_volume ${JSON.stringify(invalid)}`, async () => {
      await assertFails(setDoc(doc(maintainer(), `foodItems/${BARCODE}`), foodItem(BARCODE, { alcohol_by_volume: invalid })));
    });
  }
});

describe('foodItemSubmissions', () => {
  const sid = 'sub-1';
  const path = `foodItemSubmissions/${sid}`;

  it('lets a user create their own pending submission', async () => {
    await assertSucceeds(setDoc(doc(user('alice'), path), submission(sid, 'alice')));
  });

  it('accepts a submission without a barcode when the item id is a UUID', async () => {
    const data = submission(sid, 'alice', { item: foodItem(UUID, { photo_url: submissionPhoto('alice') }) });
    delete data.barcode;
    await assertSucceeds(setDoc(doc(user('alice'), path), data));
  });

  it('denies creation while signed out', async () => {
    await assertFails(setDoc(doc(anonymous(), path), submission(sid, 'alice')));
  });

  it('denies a submission attributed to someone else', async () => {
    await assertFails(setDoc(doc(user('alice'), path), submission(sid, 'bob')));
  });

  it('requires the id field to equal the document id', async () => {
    await assertFails(setDoc(doc(user('alice'), path), submission('other', 'alice')));
  });

  it('denies creating a submission that is already rejected', async () => {
    await assertFails(setDoc(doc(user('alice'), path), submission(sid, 'alice', { status: 'rejected' })));
  });

  it('denies a submission whose item fails validFoodItem', async () => {
    const item = foodItem(BARCODE);
    delete item.cz_name;
    await assertFails(setDoc(doc(user('alice'), path), submission(sid, 'alice', { item })));
  });

  describe('photo_url', () => {
    const withPhoto = (uid, photo_url) =>
      submission(sid, uid, { item: foodItem(BARCODE, { photo_url }) });

    it('is required on creation', async () => {
      await assertFails(setDoc(doc(user('alice'), path), submission(sid, 'alice', { item: foodItem(BARCODE) })));
    });

    it("denies a URL in another user's folder", async () => {
      await assertFails(setDoc(doc(user('alice'), path), withPhoto('alice', submissionPhoto('bob'))));
    });

    it('denies a non-Storage URL', async () => {
      await assertFails(setDoc(doc(user('alice'), path), withPhoto('alice', 'https://example.com/a.jpg')));
    });

    it('denies a catalogPhotos URL', async () => {
      await assertFails(setDoc(doc(user('alice'), path), withPhoto('alice', catalogPhoto(BARCODE))));
    });

    it("denies a resubmit that points at another user's folder or drops the photo", async () => {
      await seed((db) => setDoc(doc(db, path), submission(sid, 'alice')));
      await assertFails(setDoc(doc(user('alice'), path), withPhoto('alice', submissionPhoto('bob'))));
      await assertFails(setDoc(doc(user('alice'), path), submission(sid, 'alice', { item: foodItem(BARCODE) })));
      await assertSucceeds(setDoc(doc(user('alice'), path), withPhoto('alice', photoUrl('submissionPhotos', 'alice', 'b.jpg'))));
    });

    it('lets a maintainer reject a pending submission that has no photo', async () => {
      await seed((db) => setDoc(doc(db, path), submission(sid, 'alice', { item: foodItem(BARCODE) })));
      const data = submission(sid, 'alice', { item: foodItem(BARCODE), status: 'rejected', reject_reason: 'No photo' });
      await assertSucceeds(setDoc(doc(maintainer(), path), data));
    });
  });

  describe('reading', () => {
    beforeEach(() => seed((db) => setDoc(doc(db, path), submission(sid, 'alice'))));

    it('is allowed for the author', async () => {
      await assertSucceeds(getDoc(doc(user('alice'), path)));
    });

    it('is allowed for a maintainer', async () => {
      await assertSucceeds(getDoc(doc(maintainer(), path)));
    });

    it("is denied for another user", async () => {
      await assertFails(getDoc(doc(user('bob'), path)));
    });
  });

  describe('updating', () => {
    beforeEach(() => seed((db) => setDoc(doc(db, path), submission(sid, 'alice'))));

    it('lets a maintainer reject with a reason', async () => {
      const data = submission(sid, 'alice', { status: 'rejected', reject_reason: 'Blurry photo' });
      await assertSucceeds(setDoc(doc(maintainer(), path), data));
    });

    it('denies a maintainer rejecting without a reason', async () => {
      const data = submission(sid, 'alice', { status: 'rejected', reject_reason: '' });
      await assertFails(setDoc(doc(maintainer(), path), data));
    });

    it('denies a maintainer update that leaves the status pending', async () => {
      const data = submission(sid, 'alice', { reject_reason: 'x' });
      await assertFails(setDoc(doc(maintainer(), path), data));
    });

    it('denies a maintainer update that changes the author', async () => {
      const data = submission(sid, 'mallory', { status: 'rejected', reject_reason: 'x' });
      await assertFails(setDoc(doc(maintainer(), path), data));
    });

    it('denies a maintainer update that changes submitted_at', async () => {
      const data = submission(sid, 'alice', { status: 'rejected', reject_reason: 'x', submitted_at: 1 });
      await assertFails(setDoc(doc(maintainer(), path), data));
    });

    it('lets the author edit while the submission stays pending', async () => {
      const item = foodItem(BARCODE, { cz_name: 'Housky', photo_url: submissionPhoto('alice') });
      await assertSucceeds(setDoc(doc(user('alice'), path), submission(sid, 'alice', { item })));
    });

    it('denies the author approving or rejecting their own submission', async () => {
      await assertFails(setDoc(doc(user('alice'), path), submission(sid, 'alice', { status: 'rejected', reject_reason: 'x' })));
    });

    it("denies another user's update", async () => {
      await assertFails(setDoc(doc(user('bob'), path), submission(sid, 'alice')));
    });
  });

  describe('deleting', () => {
    beforeEach(() => seed((db) => setDoc(doc(db, path), submission(sid, 'alice'))));

    it('is allowed for the author', async () => {
      await assertSucceeds(deleteDoc(doc(user('alice'), path)));
    });

    it('is allowed for a maintainer', async () => {
      await assertSucceeds(deleteDoc(doc(maintainer(), path)));
    });

    it('is denied for another user', async () => {
      await assertFails(deleteDoc(doc(user('bob'), path)));
    });
  });
});

const clean = (obj) => Object.fromEntries(Object.entries(obj).filter(([, v]) => v !== undefined));

function describeTicket({ name, owner, createdField, make: rawMake, extraCreateCases, immutableField }) {
  const make = (item, uid, overrides = {}, id = TICKET_ID) => clean(rawMake(item, uid, overrides, id));
  describe(name, () => {
    const path = `${name}/${TICKET_ID}`;
    const resolution = (overrides = {}) => ({
      status: 'accepted',
      response: 'Thanks!',
      resolved_at: 1_700_000_500,
      hidden: false,
      ...overrides,
    });

    beforeEach(() => seed((db) => setDoc(doc(db, `foodItems/${BARCODE}`), foodItem(BARCODE))));

    describe('create', () => {
      it('lets a user create a pending ticket under an auto id', async () => {
        await assertSucceeds(setDoc(doc(user('alice'), path), make(BARCODE, 'alice')));
      });

      it('accepts a UUID-identified item', async () => {
        await seed((db) => setDoc(doc(db, `foodItems/${UUID}`), foodItem(UUID)));
        await assertSucceeds(setDoc(doc(user('alice'), path), make(UUID, 'alice')));
      });

      it('lets the same user create a second ticket for the same item', async () => {
        await assertSucceeds(setDoc(doc(user('alice'), path), make(BARCODE, 'alice')));
        await assertSucceeds(setDoc(doc(user('alice'), `${name}/${OTHER_ID}`), make(BARCODE, 'alice', {}, OTHER_ID)));
      });

      it('denies a ticket attributed to someone else', async () => {
        await assertFails(setDoc(doc(user('alice'), path), make(BARCODE, 'alice', { [owner]: 'bob' })));
      });

      it('denies an invalid item id', async () => {
        await assertFails(setDoc(doc(user('alice'), path), make('123', 'alice')));
      });

      it('denies any status but pending', async () => {
        await assertFails(setDoc(doc(user('alice'), path), make(BARCODE, 'alice', { status: 'accepted' })));
        await assertFails(setDoc(doc(user('alice'), path), make(BARCODE, 'alice', { status: undefined })));
      });

      it('denies hidden: true or a missing hidden', async () => {
        await assertFails(setDoc(doc(user('alice'), path), make(BARCODE, 'alice', { hidden: true })));
        await assertFails(setDoc(doc(user('alice'), path), make(BARCODE, 'alice', { hidden: undefined })));
      });

      it('accepts cs, en and sk and denies a missing or unknown locale', async () => {
        for (const locale of ['cs', 'en', 'sk']) {
          await assertSucceeds(setDoc(doc(user('alice'), `${name}/${LOCALE_IDS[locale]}`), make(BARCODE, 'alice', { locale }, LOCALE_IDS[locale])));
        }
        await assertFails(setDoc(doc(user('alice'), path), make(BARCODE, 'alice', { locale: 'de' })));
        await assertFails(setDoc(doc(user('alice'), path), make(BARCODE, 'alice', { locale: undefined })));
      });

      it('denies the fields only a later write may carry', async () => {
        await assertFails(setDoc(doc(user('alice'), path), make(BARCODE, 'alice', { response: 'x' })));
        await assertFails(setDoc(doc(user('alice'), path), make(BARCODE, 'alice', { seen_at: 1_700_000_100 })));
        await assertFails(setDoc(doc(user('alice'), path), make(BARCODE, 'alice', { resolved_at: 1_700_000_100 })));
      });

      it(`requires ${createdField} to be a number`, async () => {
        await assertFails(setDoc(doc(user('alice'), path), make(BARCODE, 'alice', { [createdField]: 'now' })));
      });

      it('denies creation while signed out', async () => {
        await assertFails(setDoc(doc(anonymous(), path), make(BARCODE, 'alice')));
      });

      extraCreateCases(path);
    });

    describe('once a ticket is pending', () => {
      beforeEach(() => seed((db) => setDoc(doc(db, path), make(BARCODE, 'alice'))));

      it('lets the author and a maintainer read it, not another user', async () => {
        await assertSucceeds(getDoc(doc(user('alice'), path)));
        await assertSucceeds(getDoc(doc(maintainer(), path)));
        await assertFails(getDoc(doc(user('bob'), path)));
      });

      it('lets the author query their visible tickets, not another user\'s', async () => {
        const col = (db) => collection(db, name);
        await assertSucceeds(getDocs(query(col(user('alice')), where(owner, '==', 'alice'), where('hidden', '==', false))));
        await assertFails(getDocs(query(col(user('alice')), where(owner, '==', 'bob'))));
        await assertFails(getDocs(col(user('alice'))));
      });

      it('lets a maintainer query the pending queue', async () => {
        await assertSucceeds(getDocs(query(collection(maintainer(), name), where('status', '==', 'pending'))));
      });

      describe('seen_at and hidden', () => {
        it('lets the author write seen_at repeatedly', async () => {
          await assertSucceeds(updateDoc(doc(user('alice'), path), { seen_at: 1_700_000_100 }));
          await assertSucceeds(updateDoc(doc(user('alice'), path), { seen_at: 1_700_000_200 }));
        });

        it('denies a seen_at that is not a number', async () => {
          await assertFails(updateDoc(doc(user('alice'), path), { seen_at: 'now' }));
        });

        it('lets the author hide it but not unhide it', async () => {
          await assertSucceeds(updateDoc(doc(user('alice'), path), { hidden: true }));
          await assertFails(updateDoc(doc(user('alice'), path), { hidden: false }));
        });

        it('lets the author write seen_at and hidden together', async () => {
          await assertSucceeds(updateDoc(doc(user('alice'), path), { seen_at: 1_700_000_100, hidden: true }));
        });

        it('denies the author writing anything else', async () => {
          for (const change of [
            { status: 'accepted' },
            { response: 'x' },
            { resolved_at: 1 },
            { [immutableField.name]: immutableField.value },
            { seen_at: 1_700_000_100, status: 'accepted' },
          ]) {
            await assertFails(updateDoc(doc(user('alice'), path), change));
          }
        });

        it('denies another user and, for these fields, a maintainer', async () => {
          await assertFails(updateDoc(doc(user('bob'), path), { seen_at: 1_700_000_100 }));
          await assertFails(updateDoc(doc(maintainer(), path), { seen_at: 1_700_000_100 }));
          await assertFails(updateDoc(doc(maintainer(), path), { hidden: true }));
        });
      });

      describe('resolution', () => {
        it('lets a maintainer resolve it with a response', async () => {
          await assertSucceeds(updateDoc(doc(maintainer(), path), resolution()));
        });

        it('lets a maintainer reject it', async () => {
          await assertSucceeds(updateDoc(doc(maintainer(), path), resolution({ status: 'rejected' })));
        });

        it('denies an empty response, one over 1000 characters, and a missing one', async () => {
          await assertFails(updateDoc(doc(maintainer(), path), resolution({ response: '' })));
          await assertFails(updateDoc(doc(maintainer(), path), resolution({ response: 'x'.repeat(1001) })));
          await assertFails(updateDoc(doc(maintainer(), path), { status: 'accepted', resolved_at: 1, hidden: false }));
          await assertSucceeds(updateDoc(doc(maintainer(), path), resolution({ response: 'x'.repeat(1000) })));
        });

        it('denies a status other than accepted or rejected', async () => {
          await assertFails(updateDoc(doc(maintainer(), path), resolution({ status: 'pending' })));
          await assertFails(updateDoc(doc(maintainer(), path), resolution({ status: 'done' })));
        });

        it('requires resolved_at to be a number and hidden to be false', async () => {
          await assertFails(updateDoc(doc(maintainer(), path), resolution({ resolved_at: 'now' })));
          await assertFails(updateDoc(doc(maintainer(), path), resolution({ hidden: true })));
        });

        it('denies touching any other field in the same write', async () => {
          await assertFails(updateDoc(doc(maintainer(), path), resolution({ [immutableField.name]: immutableField.value })));
          await assertFails(updateDoc(doc(maintainer(), path), resolution({ seen_at: 1 })));
        });

        it('denies a second resolution', async () => {
          await assertSucceeds(updateDoc(doc(maintainer(), path), resolution()));
          await assertFails(updateDoc(doc(maintainer(), path), resolution({ status: 'rejected', response: 'again' })));
        });

        it('denies a regular user resolving their own ticket', async () => {
          await assertFails(updateDoc(doc(user('alice'), path), resolution()));
        });

        it('brings back a ticket the author had hidden', async () => {
          await updateDoc(doc(user('alice'), path), { hidden: true });
          await assertSucceeds(updateDoc(doc(maintainer(), path), resolution()));
          const snap = await getDoc(doc(maintainer(), path));
          assert.equal(snap.data().hidden, false);
        });
      });

      describe('account deletion', () => {
        const anonymise = { [owner]: deleteField(), author_deleted: true };

        it('lets the author remove the owner field and set author_deleted', async () => {
          await assertSucceeds(updateDoc(doc(user('alice'), path), anonymise));
        });

        it('denies it together with any other field', async () => {
          await assertFails(updateDoc(doc(user('alice'), path), { ...anonymise, hidden: true }));
          await assertFails(updateDoc(doc(user('alice'), path), { ...anonymise, status: 'accepted' }));
        });

        it('denies removing the owner without author_deleted, and setting it to false', async () => {
          await assertFails(updateDoc(doc(user('alice'), path), { [owner]: deleteField() }));
          await assertFails(updateDoc(doc(user('alice'), path), { [owner]: deleteField(), author_deleted: false }));
        });

        it('denies handing the ticket to someone else', async () => {
          await assertFails(updateDoc(doc(user('alice'), path), { [owner]: 'bob', author_deleted: true }));
        });

        it('denies another user anonymising it', async () => {
          await assertFails(updateDoc(doc(user('bob'), path), anonymise));
        });

        it('leaves the ticket resolvable by the maintainer and unreadable by anyone else', async () => {
          await assertSucceeds(updateDoc(doc(user('alice'), path), anonymise));
          await assertFails(getDoc(doc(user('alice'), path)));
          await assertSucceeds(getDoc(doc(maintainer(), path)));
          await assertSucceeds(updateDoc(doc(maintainer(), path), resolution()));
        });
      });

      describe('delete', () => {
        it('lets the author withdraw a pending ticket', async () => {
          await assertSucceeds(deleteDoc(doc(user('alice'), path)));
        });

        it('lets a maintainer delete it', async () => {
          await assertSucceeds(deleteDoc(doc(maintainer(), path)));
        });

        it('denies another user', async () => {
          await assertFails(deleteDoc(doc(user('bob'), path)));
        });

        it('denies the author deleting a resolved ticket', async () => {
          await seed((db) => updateDoc(doc(db, path), resolution()));
          await assertFails(deleteDoc(doc(user('alice'), path)));
          await assertSucceeds(deleteDoc(doc(maintainer(), path)));
        });
      });
    });
  });
}

describeTicket({
  name: 'foodItemReports',
  owner: 'reported_by',
  createdField: 'reported_at',
  make: report,
  immutableField: { name: 'reason', value: 'Changed my mind' },
  extraCreateCases: (path) => {
    it('enforces a reason of 1 to 500 characters', async () => {
      await assertFails(setDoc(doc(user('alice'), path), report(BARCODE, 'alice', { reason: '' })));
      await assertSucceeds(setDoc(doc(user('alice'), path), report(BARCODE, 'alice', { reason: 'x'.repeat(500) })));
      await env.clearFirestore();
      await assertFails(setDoc(doc(user('alice'), path), report(BARCODE, 'alice', { reason: 'x'.repeat(501) })));
    });
  },
});

describeTicket({
  name: 'photoSuggestions',
  owner: 'suggested_by',
  createdField: 'suggested_at',
  make: suggestion,
  immutableField: { name: 'photo_url', value: suggestionPhoto('alice', UUID) },
  extraCreateCases: () => {
    const ticket = `photoSuggestions/${TICKET_ID}`;

    it('requires the photo_url to be the ticket\'s own file', async () => {
      await assertSucceeds(setDoc(doc(user('alice'), ticket), suggestion(BARCODE, 'alice')));
    });

    it('denies a photo_url for another suggestion id, item, user or folder', async () => {
      const bad = [
        suggestionPhoto('alice', BARCODE, OTHER_ID),
        suggestionPhoto('bob'),
        suggestionPhoto('alice', UUID),
        submissionPhoto('alice'),
        catalogPhoto(BARCODE),
      ];
      for (const photo_url of bad) {
        await assertFails(setDoc(doc(user('alice'), ticket), suggestion(BARCODE, 'alice', { photo_url })));
      }
    });

    it('denies a photo_url longer than 1024 characters', async () => {
      await assertFails(setDoc(doc(user('alice'), ticket), suggestion(BARCODE, 'alice', {
        photo_url: `${suggestionPhoto('alice')}&${'x'.repeat(1024)}`,
      })));
    });

    it('denies a suggestion carrying a field beyond those it defines', async () => {
      await assertFails(setDoc(doc(user('alice'), ticket), suggestion(BARCODE, 'alice', { padding: 'x'.repeat(1000) })));
    });

    it('denies a suggestion for an item that does not exist', async () => {
      await assertFails(setDoc(doc(user('alice'), ticket), suggestion(UUID, 'alice')));
    });

    it('denies a suggestion for an item that already has a photo', async () => {
      await seed((db) => setDoc(doc(db, `foodItems/${UUID}`), foodItem(UUID, { photo_url: catalogPhoto(UUID) })));
      await assertFails(setDoc(doc(user('alice'), ticket), suggestion(UUID, 'alice')));
    });
  },
});

describe('config', () => {
  it('can be read without signing in', async () => {
    await seed((db) => setDoc(doc(db, 'config/app'), { min_version_ios: '1.0.1' }));
    await assertSucceeds(getDoc(doc(anonymous(), 'config/app')));
    await assertSucceeds(getDoc(doc(user('alice'), 'config/app')));
  });

  it('cannot be written by anyone, maintainer included', async () => {
    await assertFails(setDoc(doc(anonymous(), 'config/app'), { min_version_ios: '9.9' }));
    await assertFails(setDoc(doc(user('alice'), 'config/app'), { min_version_ios: '9.9' }));
    await assertFails(setDoc(doc(maintainer(), 'config/app'), { min_version_ios: '9.9' }));
  });
});

describe('everything else', () => {
  it('is denied by default', async () => {
    await assertFails(getDoc(doc(user('alice'), 'somethingElse/x')));
    await assertFails(setDoc(doc(maintainer(), 'somethingElse/x'), { a: 1 }));
  });
});
