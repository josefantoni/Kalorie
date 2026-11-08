const { describe, it, before, after, beforeEach } = require('node:test');
const assert = require('node:assert/strict');
const { initializeApp, deleteApp } = require('firebase-admin/app');
const { getFirestore, FieldPath } = require('firebase-admin/firestore');
const { propagate, propagateCurrent, shouldRetry } = require('../propagate');

const BARCODE = '8593803625270';
const OLD = { cz_name: 'Tvaroh', eng_name: 'Quark' };
const NEW = { cz_name: 'Madeta tvaroh', eng_name: 'Madeta quark' };
const PHOTO = 'https://example.test/photo.jpg';

let app;
let db;

before(() => {
  process.env.FIRESTORE_EMULATOR_HOST ??= '127.0.0.1:8080';
  app = initializeApp({ projectId: 'demo-kalorie' }, 'propagate-tests');
  db = getFirestore(app);
});

after(() => deleteApp(app));

beforeEach(async () => {
  await fetch(`http://${process.env.FIRESTORE_EMULATOR_HOST}/emulator/v1/projects/demo-kalorie/databases/(default)/documents`, {
    method: 'DELETE',
  });
});

const defined = (o) => Object.fromEntries(Object.entries(o).filter(([, v]) => v !== undefined));

const consumed = (uid, id, extra = {}) =>
  db.doc(`users/${uid}/foodConsumed/${id}-${uid}`).set({
    food_item_id: BARCODE, food_item_kind: 'catalogue', ...OLD,
    weight: 120, calories_per_hundred_grams: 70, date: 1_700_000_000, ...extra,
  });

const favourite = (uid, extra = {}) =>
  db.doc(`users/${uid}/favouriteFoods/${BARCODE}`).set(defined({
    id: BARCODE, food_item_kind: 'catalogue', ...OLD, photo_url: PHOTO,
    calories_per_hundred_grams: 70, ...extra,
  }));

const frequency = (uid, key = BARCODE, itemExtra = {}) =>
  db.doc(`users/${uid}/stats/foodFrequency`).set({
    entries: { [key]: { count: 3, last_logged_at: 1_700_000_000, item: defined({
      id: key, food_item_kind: 'catalogue', ...OLD, photo_url: PHOTO,
      calories_per_hundred_grams: 70, ...itemExtra,
    }) } },
  });

const read = async (path) => (await db.doc(path).get());

describe('propagate', () => {
  it('renames foodConsumed, favouriteFoods and foodFrequency of every user and leaves nutrition alone', async () => {
    for (const uid of ['a', 'b']) {
      await consumed(uid, 'x');
      await favourite(uid);
      await frequency(uid);
    }
    await propagate(db, BARCODE, { ...OLD }, { ...NEW, photo_url: PHOTO });

    for (const uid of ['a', 'b']) {
      const c = (await read(`users/${uid}/foodConsumed/x-${uid}`)).data();
      assert.equal(c.cz_name, NEW.cz_name);
      assert.equal(c.eng_name, NEW.eng_name);
      assert.equal(c.weight, 120);
      assert.equal(c.date, 1_700_000_000);
      assert.equal(c.food_item_kind, 'catalogue');
      assert.equal('photo_url' in c, false);
      const f = (await read(`users/${uid}/favouriteFoods/${BARCODE}`)).data();
      assert.equal(f.cz_name, NEW.cz_name);
      assert.equal(f.calories_per_hundred_grams, 70);
      const s = (await read(`users/${uid}/stats/foodFrequency`)).data().entries[BARCODE];
      assert.equal(s.item.eng_name, NEW.eng_name);
      assert.equal(s.count, 3);
      assert.equal(s.item.calories_per_hundred_grams, 70);
    }
  });

  it('writes nothing when none of the three fields changed', async () => {
    await consumed('a', 'x');
    const before = (await read('users/a/foodConsumed/x-a')).updateTime;
    await propagate(db, BARCODE, { ...OLD, fat: 1 }, { ...OLD, fat: 2 });
    assert.deepEqual((await read('users/a/foodConsumed/x-a')).updateTime, before);
  });

  it('writes nothing for a copy that already has the new values', async () => {
    await consumed('a', 'x', NEW);
    const before = (await read('users/a/foodConsumed/x-a')).updateTime;
    await propagate(db, BARCODE, OLD, NEW);
    assert.deepEqual((await read('users/a/foodConsumed/x-a')).updateTime, before);
  });

  it('renames copies made while the submission was pending when the item is created', async () => {
    await consumed('a', 'x');
    await favourite('a', { photo_url: undefined });
    await propagate(db, BARCODE, null, NEW);
    assert.equal((await read('users/a/foodConsumed/x-a')).data().cz_name, NEW.cz_name);
    assert.equal((await read(`users/a/favouriteFoods/${BARCODE}`)).data().eng_name, NEW.eng_name);
  });

  it('removes photo_url from favourite and frequency copies instead of writing null', async () => {
    await consumed('a', 'x');
    await favourite('a');
    await frequency('a');
    await propagate(db, BARCODE, { ...OLD, photo_url: PHOTO }, { ...OLD });

    const f = (await read(`users/a/favouriteFoods/${BARCODE}`)).data();
    assert.equal('photo_url' in f, false);
    const s = (await read('users/a/stats/foodFrequency')).data().entries[BARCODE].item;
    assert.equal('photo_url' in s, false);
    assert.equal('photo_url' in (await read('users/a/foodConsumed/x-a')).data(), false);
  });

  it('copies a new photo into favourite and frequency but never into foodConsumed', async () => {
    await consumed('a', 'x');
    await favourite('a', { photo_url: undefined });
    await frequency('a', BARCODE, { photo_url: undefined });
    await propagate(db, BARCODE, { ...OLD }, { ...OLD, photo_url: PHOTO });

    assert.equal((await read(`users/a/favouriteFoods/${BARCODE}`)).data().photo_url, PHOTO);
    assert.equal((await read('users/a/stats/foodFrequency')).data().entries[BARCODE].item.photo_url, PHOTO);
    assert.equal('photo_url' in (await read('users/a/foodConsumed/x-a')).data(), false);
  });

  it('renames an external copy and keeps its kind, but leaves a created_meal copy alone', async () => {
    await consumed('a', 'ext', { food_item_kind: 'external' });
    await consumed('a', 'meal', { food_item_kind: 'created_meal' });
    await propagate(db, BARCODE, OLD, NEW);

    const ext = (await read('users/a/foodConsumed/ext-a')).data();
    assert.equal(ext.cz_name, NEW.cz_name);
    assert.equal(ext.food_item_kind, 'external');
    assert.equal((await read('users/a/foodConsumed/meal-a')).data().cz_name, OLD.cz_name);
  });

  it('keeps the copy eng_name when the catalogue item has none', async () => {
    await consumed('a', 'x');
    await propagate(db, BARCODE, OLD, { cz_name: NEW.cz_name });
    const c = (await read('users/a/foodConsumed/x-a')).data();
    assert.equal(c.cz_name, NEW.cz_name);
    assert.equal(c.eng_name, OLD.eng_name);
  });

  it('does nothing when the item was deleted', async () => {
    await consumed('a', 'x');
    await propagate(db, BARCODE, OLD, null);
    assert.equal((await read('users/a/foodConsumed/x-a')).data().cz_name, OLD.cz_name);
  });

  it('updates a foodFrequency entry whose key starts with a digit and leaves other entries alone', async () => {
    await db.doc('users/a/stats/foodFrequency').set({
      entries: {
        [BARCODE]: { count: 1, last_logged_at: 1, item: { id: BARCODE, food_item_kind: 'catalogue', ...OLD } },
        other: { count: 1, last_logged_at: 1, item: { id: 'other', food_item_kind: 'catalogue', ...OLD } },
      },
    });
    await propagate(db, BARCODE, OLD, NEW);
    const entries = (await read('users/a/stats/foodFrequency')).data().entries;
    assert.equal(entries[BARCODE].item.cz_name, NEW.cz_name);
    assert.equal(entries.other.item.cz_name, OLD.cz_name);
  });
  it('does not leave an unhandled rejection when a copy is deleted during the update', async () => {
    await consumed('a', 'x');
    await consumed('b', 'x');
    const racing = new Proxy(db, {
      get(target, prop) {
        if (prop === 'collectionGroup') {
          return (name) => {
            let query = target.collectionGroup(name);
            if (name !== 'foodConsumed') return query;
            const wrapped = {
              select: (...fields) => { query = query.select(...fields); return wrapped; },
              where: (...args) => ({
                get: async () => {
                  const snap = await query.where(...args).get();
                  await snap.docs[0].ref.delete();
                  return snap;
                },
              }),
            };
            return wrapped;
          };
        }
        const value = target[prop];
        return typeof value === 'function' ? value.bind(target) : value;
      },
    });
    const rejections = [];
    const spy = (reason) => rejections.push(reason);
    process.on('unhandledRejection', spy);
    try {
      await propagate(racing, BARCODE, OLD, NEW);
      await new Promise((resolve) => setImmediate(resolve));
    } finally {
      process.off('unhandledRejection', spy);
    }
    assert.deepEqual(rejections, []);
    const renamed = [(await read('users/a/foodConsumed/x-a')), (await read('users/b/foodConsumed/x-b'))]
      .filter((snap) => snap.exists);
    assert.equal(renamed.length, 1);
    assert.equal(renamed[0].data().cz_name, NEW.cz_name);
  });

  it('queues no write when a later query fails', async () => {
    await consumed('a', 'x');
    const queued = [];
    const failing = new Proxy(db, {
      get(target, prop) {
        if (prop === 'collectionGroup') {
          return (name) => {
            if (name !== 'favouriteFoods') return target.collectionGroup(name);
            const failingQuery = {
              select: () => failingQuery,
              where: () => ({ get: async () => { throw Object.assign(new Error('index building'), { code: 9 }); } }),
            };
            return failingQuery;
          };
        }
        if (prop === 'bulkWriter') {
          return () => {
            const writer = target.bulkWriter();
            const update = writer.update.bind(writer);
            writer.update = (...args) => { queued.push(args); return update(...args); };
            return writer;
          };
        }
        const value = target[prop];
        return typeof value === 'function' ? value.bind(target) : value;
      },
    });
    await assert.rejects(propagate(failing, BARCODE, OLD, NEW), { code: 9 });
    assert.deepEqual(queued, []);
  });
});

describe('shouldRetry', () => {
  it('retries a transient error up to three times', () => {
    assert.equal(shouldRetry({ code: 14, failedAttempts: 3 }), true);
    assert.equal(shouldRetry({ code: 14, failedAttempts: 4 }), false);
  });

  it('retries only the codes the SDK retries for BatchWrite', () => {
    for (const code of [8, 10, 14]) assert.equal(shouldRetry({ code, failedAttempts: 1 }), true);
    assert.equal(shouldRetry({ code: 3, failedAttempts: 1 }), false);
  });
});

describe('propagateCurrent', () => {
  it('propagates the current catalogue document, not the event snapshot', async () => {
    await db.doc(`foodItems/${BARCODE}`).set({ cz_name: 'C tvaroh', eng_name: 'C quark' });
    await consumed('a', 'x');
    await propagateCurrent(db, BARCODE, OLD);
    assert.equal((await read('users/a/foodConsumed/x-a')).data().cz_name, 'C tvaroh');
  });

  it('propagates again when the catalogue document changed while it was propagating', async () => {
    await db.doc(`foodItems/${BARCODE}`).set({ cz_name: 'B tvaroh', eng_name: 'B quark' });
    await consumed('a', 'x');
    const racing = new Proxy(db, {
      get(target, prop) {
        if (prop === 'collectionGroup') {
          return (name) => {
            let query = target.collectionGroup(name);
            if (name !== 'foodConsumed') return query;
            const wrapped = {
              select: (...fields) => { query = query.select(...fields); return wrapped; },
              where: (...args) => ({
                get: async () => {
                  const snap = await query.where(...args).get();
                  await target.doc(`foodItems/${BARCODE}`).set({ cz_name: 'C tvaroh', eng_name: 'C quark' });
                  return snap;
                },
              }),
            };
            return wrapped;
          };
        }
        const value = target[prop];
        return typeof value === 'function' ? value.bind(target) : value;
      },
    });
    await propagateCurrent(racing, BARCODE, OLD);
    assert.equal((await read('users/a/foodConsumed/x-a')).data().cz_name, 'C tvaroh');
  });

  it('rechecks the catalogue document even when the first propagation fails', async () => {
    await db.doc(`foodItems/${BARCODE}`).set({ cz_name: 'B tvaroh', eng_name: 'B quark' });
    await consumed('a', 'x');
    await consumed('b', 'x');
    let writers = 0;
    const racing = new Proxy(db, {
      get(target, prop) {
        if (prop === 'collectionGroup') {
          return (name) => {
            let query = target.collectionGroup(name);
            if (name !== 'foodConsumed') return query;
            const wrapped = {
              select: (...fields) => { query = query.select(...fields); return wrapped; },
              where: (...args) => ({
                get: async () => {
                  const snap = await query.where(...args).get();
                  await target.doc(`foodItems/${BARCODE}`).set({ cz_name: 'C tvaroh', eng_name: 'C quark' });
                  return snap;
                },
              }),
            };
            return wrapped;
          };
        }
        if (prop === 'bulkWriter') {
          return () => {
            const writer = target.bulkWriter();
            if (writers++ > 0) return writer;
            let handler;
            const onWriteError = writer.onWriteError.bind(writer);
            const update = writer.update.bind(writer);
            writer.onWriteError = (fn) => { handler = fn; onWriteError(fn); };
            writer.update = (ref, ...args) => {
              if (!ref.path.endsWith('x-a')) return update(ref, ...args);
              handler(Object.assign(new Error('denied'), { code: 7, failedAttempts: 1 }));
              return Promise.resolve();
            };
            return writer;
          };
        }
        const value = target[prop];
        return typeof value === 'function' ? value.bind(target) : value;
      },
    });
    await assert.rejects(propagateCurrent(racing, BARCODE, OLD), { code: 7 });
    assert.equal((await read('users/a/foodConsumed/x-a')).data().cz_name, 'C tvaroh');
    assert.equal((await read('users/b/foodConsumed/x-b')).data().cz_name, 'C tvaroh');
  });

  it('does nothing when the catalogue document no longer exists', async () => {
    await consumed('a', 'x');
    await propagateCurrent(db, BARCODE, OLD);
    assert.equal((await read('users/a/foodConsumed/x-a')).data().cz_name, OLD.cz_name);
  });
});
