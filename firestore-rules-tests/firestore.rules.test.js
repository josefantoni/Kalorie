const { describe, it, before, after, beforeEach } = require('node:test');
const { readFileSync } = require('node:fs');
const path = require('node:path');
const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require('@firebase/rules-unit-testing');
const { doc, getDoc, setDoc, deleteDoc } = require('firebase/firestore');

const UUID = '3F2504E0-4F89-41D3-9A0C-0305E82C3301';
const BARCODE = '8593807012345';

let env;

function foodItem(id, overrides = {}) {
  return {
    id,
    cz_name: 'Rohlik',
    eng_name: 'Roll',
    cz_name_lowercase: 'rohlik',
    eng_name_lowercase: 'roll',
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
    item: foodItem(BARCODE),
    ...overrides,
  };
}

function report(barcode, uid, overrides = {}) {
  return {
    barcode,
    reported_by: uid,
    reason: 'Calories look wrong',
    reported_at: 1_700_000_000,
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
      rules: readFileSync(path.join(__dirname, '../Kalorie/firestore.rules'), 'utf8'),
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

  it('cannot be deleted, even by a maintainer', async () => {
    await seed((db) => setDoc(doc(db, `foodItems/${BARCODE}`), foodItem(BARCODE)));
    await assertFails(deleteDoc(doc(maintainer(), `foodItems/${BARCODE}`)));
  });

  for (const id of ['12345678', '123456789012', '1234567890123']) {
    it(`accepts a ${id.length}-digit barcode id`, async () => {
      await assertSucceeds(setDoc(doc(maintainer(), `foodItems/${id}`), foodItem(id)));
    });
  }

  for (const id of ['1234567', '123456789', '12345678901234', '12345678A', UUID.toLowerCase(), 'not-an-id']) {
    it(`rejects the id "${id}"`, async () => {
      await assertFails(setDoc(doc(maintainer(), `foodItems/${id}`), foodItem(id)));
    });
  }

  it('accepts an uppercase hyphenated UUID id', async () => {
    await assertSucceeds(setDoc(doc(maintainer(), `foodItems/${UUID}`), foodItem(UUID)));
  });

  it('requires the id field to equal the document id', async () => {
    await assertFails(setDoc(doc(maintainer(), `foodItems/${BARCODE}`), foodItem('12345678')));
  });

  for (const field of [
    'cz_name', 'eng_name', 'cz_name_lowercase', 'eng_name_lowercase', 'weight', 'date',
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
});

describe('foodItemSubmissions', () => {
  const sid = 'sub-1';
  const path = `foodItemSubmissions/${sid}`;

  it('lets a user create their own pending submission', async () => {
    await assertSucceeds(setDoc(doc(user('alice'), path), submission(sid, 'alice')));
  });

  it('accepts a submission without a barcode when the item id is a UUID', async () => {
    const data = submission(sid, 'alice', { item: foodItem(UUID) });
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
      const item = foodItem(BARCODE, { cz_name: 'Housky' });
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

describe('foodItemReports', () => {
  const rid = `${BARCODE}_alice`;
  const path = `foodItemReports/${rid}`;

  it('lets a user create a report under {barcode}_{uid}', async () => {
    await assertSucceeds(setDoc(doc(user('alice'), path), report(BARCODE, 'alice')));
  });

  it('accepts a UUID-identified food as the barcode', async () => {
    await assertSucceeds(setDoc(doc(user('alice'), `foodItemReports/${UUID}_alice`), report(UUID, 'alice')));
  });

  it('denies a document id that does not match barcode and uid', async () => {
    await assertFails(setDoc(doc(user('alice'), `foodItemReports/${BARCODE}_bob`), report(BARCODE, 'alice')));
    await assertFails(setDoc(doc(user('alice'), 'foodItemReports/random'), report(BARCODE, 'alice')));
  });

  it('denies a report attributed to someone else', async () => {
    await assertFails(setDoc(doc(user('alice'), path), report(BARCODE, 'alice', { reported_by: 'bob' })));
  });

  it('denies an invalid barcode', async () => {
    await assertFails(setDoc(doc(user('alice'), 'foodItemReports/123_alice'), report('123', 'alice')));
  });

  it('enforces a reason of 1 to 500 characters', async () => {
    await assertFails(setDoc(doc(user('alice'), path), report(BARCODE, 'alice', { reason: '' })));
    await assertSucceeds(setDoc(doc(user('alice'), path), report(BARCODE, 'alice', { reason: 'x'.repeat(500) })));
    await env.clearFirestore();
    await assertFails(setDoc(doc(user('alice'), path), report(BARCODE, 'alice', { reason: 'x'.repeat(501) })));
  });

  it('requires reported_at to be a number', async () => {
    await assertFails(setDoc(doc(user('alice'), path), report(BARCODE, 'alice', { reported_at: 'now' })));
  });

  it('denies creation while signed out', async () => {
    await assertFails(setDoc(doc(anonymous(), path), report(BARCODE, 'alice')));
  });

  describe('once a report is open', () => {
    beforeEach(() => seed((db) => setDoc(doc(db, path), report(BARCODE, 'alice'))));

    it('lets the reporter and a maintainer read it', async () => {
      await assertSucceeds(getDoc(doc(user('alice'), path)));
      await assertSucceeds(getDoc(doc(maintainer(), path)));
    });

    it('denies another user reading it', async () => {
      await assertFails(getDoc(doc(user('bob'), path)));
    });

    it('refuses a second report from the same user, since there is no update rule', async () => {
      await assertFails(setDoc(doc(user('alice'), path), report(BARCODE, 'alice', { reason: 'Changed my mind' })));
    });

    it('lets the reporter and a maintainer delete it', async () => {
      await assertSucceeds(deleteDoc(doc(user('alice'), path)));
      await seed((db) => setDoc(doc(db, path), report(BARCODE, 'alice')));
      await assertSucceeds(deleteDoc(doc(maintainer(), path)));
    });

    it('denies another user deleting it', async () => {
      await assertFails(deleteDoc(doc(user('bob'), path)));
    });
  });

  it("lets a user probe their own report id when it does not exist, but not someone else's", async () => {
    await assertSucceeds(getDoc(doc(user('alice'), path)));
    await assertFails(getDoc(doc(user('bob'), path)));
  });
});

describe('everything else', () => {
  it('is denied by default', async () => {
    await assertFails(getDoc(doc(user('alice'), 'somethingElse/x')));
    await assertFails(setDoc(doc(maintainer(), 'somethingElse/x'), { a: 1 }));
  });
});
