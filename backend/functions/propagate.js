const { FieldPath, FieldValue } = require('firebase-admin/firestore');

const NAME_FIELDS = ['cz_name', 'eng_name'];
const PHOTO_FIELD = 'photo_url';
const CREATED_MEAL = 'created_meal';
const NOT_FOUND = 5;
const MAX_RETRIES = 3;
const MAX_RECHECKS = 3;
const RETRYABLE_CODES = [8, 10, 14];

function shouldRetry(error) {
  return RETRYABLE_CODES.includes(error.code) && error.failedAttempts <= MAX_RETRIES;
}

function changedFields(before, after) {
  const names = NAME_FIELDS.filter(
    (field) => typeof after[field] === 'string' && (before?.[field] ?? null) !== after[field],
  );
  const photoChanged = (before?.[PHOTO_FIELD] ?? null) !== (after[PHOTO_FIELD] ?? null);
  return { names, photoChanged };
}

function desiredPhoto(after) {
  return after[PHOTO_FIELD] ?? FieldValue.delete();
}

function nameUpdates(data, names, after) {
  const updates = {};
  for (const field of names) {
    if (data[field] !== after[field]) updates[field] = after[field];
  }
  return updates;
}

function photoUpdate(data, after) {
  const current = data[PHOTO_FIELD] ?? null;
  const wanted = after[PHOTO_FIELD] ?? null;
  return current === wanted ? {} : { [PHOTO_FIELD]: desiredPhoto(after) };
}

async function propagate(db, id, before, after) {
  if (!after) return;
  const { names, photoChanged } = changedFields(before, after);
  if (names.length === 0 && !photoChanged) return;

  const [consumed, favourites, stats] = await Promise.all([
    db.collectionGroup('foodConsumed')
      .select('food_item_kind', ...NAME_FIELDS)
      .where('food_item_id', '==', id)
      .get(),
    db.collectionGroup('favouriteFoods')
      .select('food_item_kind', ...NAME_FIELDS, PHOTO_FIELD)
      .where('id', '==', id)
      .get(),
    db.collectionGroup('stats').select(new FieldPath('entries', id)).get(),
  ]);

  const writer = db.bulkWriter();
  // Rejections are safe to drop: NOT_FOUND is ignored on purpose, other errors are kept in `failure`.
  const write = (ref, ...args) => writer.update(ref, ...args).catch(() => {});
  let failure = null;
  writer.onWriteError((error) => {
    if (error.code === NOT_FOUND) return false;
    if (shouldRetry(error)) return true;
    failure = failure ?? error;
    return false;
  });

  for (const snapshot of consumed.docs) {
    const data = snapshot.data();
    if (data.food_item_kind === CREATED_MEAL) continue;
    const updates = nameUpdates(data, names, after);
    if (Object.keys(updates).length > 0) write(snapshot.ref, updates);
  }

  for (const snapshot of favourites.docs) {
    const data = snapshot.data();
    if (data.food_item_kind === CREATED_MEAL) continue;
    const updates = nameUpdates(data, names, after);
    if (photoChanged) Object.assign(updates, photoUpdate(data, after));
    if (Object.keys(updates).length > 0) write(snapshot.ref, updates);
  }

  for (const snapshot of stats.docs) {
    if (snapshot.id !== 'foodFrequency') continue;
    const item = snapshot.get(new FieldPath('entries', id, 'item'));
    if (!item || item.food_item_kind === CREATED_MEAL) continue;
    const updates = nameUpdates(item, names, after);
    if (photoChanged) Object.assign(updates, photoUpdate(item, after));
    const flat = [];
    for (const [field, value] of Object.entries(updates)) {
      flat.push(new FieldPath('entries', id, 'item', field), value);
    }
    if (flat.length > 0) write(snapshot.ref, ...flat);
  }

  await writer.close();
  if (failure) throw failure;
}

async function readCurrent(db, id) {
  const snapshot = await db.doc(`foodItems/${id}`).get();
  return snapshot.exists ? snapshot.data() : null;
}

function samePropagatedFields(a, b) {
  return [...NAME_FIELDS, PHOTO_FIELD].every((field) => (a[field] ?? null) === (b[field] ?? null));
}

async function propagateCurrent(db, id, before) {
  let propagated = before;
  let failure = null;
  let current = await readCurrent(db, id);
  for (let attempt = 0; current; attempt++) {
    try {
      await propagate(db, id, propagated, current);
    } catch (error) {
      failure = failure ?? error;
    }
    const latest = await readCurrent(db, id);
    if (!latest || samePropagatedFields(latest, current) || attempt === MAX_RECHECKS) break;
    propagated = current;
    current = latest;
  }
  if (failure) throw failure;
}

module.exports = { propagate, propagateCurrent, shouldRetry };
