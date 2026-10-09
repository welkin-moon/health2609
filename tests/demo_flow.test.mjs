import test from 'node:test';
import assert from 'node:assert/strict';
import { app } from './worker-app.mjs';
import { sqliteDb } from './sqlite-db.mjs';

const date = '2026-10-06';
async function fixture(t) {
  const db = sqliteDb(); t.after(db.close);
  db.sqlite.exec(`INSERT INTO schools (id,name,daily_activity_target_minutes) VALUES ('qa-school','测试学校',60);
    INSERT INTO class_groups VALUES ('qa-class','qa-school','测试班');
    INSERT INTO student_memberships (id,school_id,class_group_id,participant_id) VALUES
      ('qa-1','qa-school','qa-class','qa-1'),('qa-2','qa-school','qa-class','qa-2'),('qa-3','qa-school','qa-class','qa-3');`);
  async function call(path, body, { admin = false, method = body ? 'POST' : 'GET', participant = 'qa-1' } = {}) {
    const response = await app.request(path, { method, headers: {
      'Content-Type': 'application/json', 'x-demo-school': 'qa-school', 'x-demo-participant': participant,
      'x-demo-role': admin ? 'admin' : 'student'
    }, ...(body ? { body: JSON.stringify(body) } : {}) }, { DB: db.DB });
    const value = await response.json();
    return { status: response.status, value };
  }
  return { ...db, call };
}

test('real SQLite demo flow: admin menu -> student portion -> read-back -> class statistics', async t => {
  const { call } = await fixture(t);
  assert.equal((await call('/v1/admin/menus', { date, mealSlot: 'lunch', dishes: [
    { name: '米饭', standardServingGrams: 200, nutritionPerServing: { energyKcal: 260, carbohydrateG: 56 } }
  ] }, { admin: true, method: 'PUT' })).status, 200);
  const menu = (await call(`/v1/today/menu?date=${date}`)).value;
  const dish = menu.dishes[0].id;
  for (const participant of ['qa-1','qa-2','qa-3']) {
    assert.equal((await call('/v1/meals/consumption', { date, mealSlot: 'lunch', items: [
      { dishId: dish, consumedGrams: 100 }
    ] }, { participant })).status, 200);
  }
  assert.equal((await call(`/v1/today/menu?date=${date}`)).value.dishes[0].savedServingMultiplier, 0.5);
  assert.equal((await call(`/v1/today/summary?date=${date}`)).value.nutrition.energyKcal, 130);
  const stats = (await call(`/v1/admin/stats/overview?date=${date}&classGroupId=qa-class`, undefined, { admin: true })).value;
  assert.equal(stats.meal.participationRate, 1);
  assert.equal(stats.nutrition.avg_energy_kcal, 130);
  assert.equal(stats.privacyMasked, false);
});

test('real SQLite meal updates replace the same slot, manual unknown nutrients stay absent', async t => {
  const { call, sqlite } = await fixture(t);
  for (const grams of [100, 150]) assert.equal((await call('/v1/home-meals', {
    date, mealSlot: 'dinner', items: [{ name: '苹果', grams }]
  })).status, 200);
  const saved = (await call(`/v1/home-meals?date=${date}`)).value;
  assert.equal(saved.meals.length, 1);
  assert.equal(saved.meals[0].items[0].grams, 150);
  assert.equal(saved.meals[0].items[0].nutrition, null);
  const summary = (await call(`/v1/today/summary?date=${date}`)).value;
  assert.equal(summary.nutrition.recordedFoodItems, 1);
  assert.equal(summary.nutrition.unknownEnergyItems, 1);
  assert.equal(sqlite.prepare("SELECT COUNT(*) AS n FROM home_meals WHERE student_membership_id='qa-1'").get().n, 1);
});

test('student, admin and seven-day trend use the same selected school activity source', async t => {
  const { call } = await fixture(t);
  const schedule = await call('/v1/admin/pe/timetable', { classGroupId: 'qa-class', weekday: 2, startTime: '14:00', endTime: '14:45' }, { admin: true, method: 'PUT' });
  assert.equal(schedule.status, 200);
  const timetableId = schedule.value.id;
  assert.equal((await call('/v1/admin/pe/session', { timetableId, date, actualActivityMinutes: 30 }, { admin: true, method: 'PUT' })).status, 200);
  for (const participant of ['qa-1','qa-2','qa-3']) {
    assert.equal((await call('/v1/activity/manual', { date, activityType: '散步', durationMinutes: 20, intensity: 'light' }, { participant })).status, 200);
    assert.equal((await call('/v1/activity/outside-school', { date, exerciseMinutes: 25 }, { participant })).status, 200);
    assert.equal((await call('/v1/activity/school-source', { date, source: 'health_connect', exerciseMinutes: 40 }, { participant, method: 'PUT' })).status, 200);
  }
  const daily = (await call(`/v1/today/summary?date=${date}`)).value;
  assert.equal(daily.activity.totalMinutes, 65);
  assert.equal(daily.activity.outsideMinutes, 25);
  const stats = (await call(`/v1/admin/stats/overview?date=${date}`, undefined, { admin: true })).value;
  assert.equal(stats.activity.avg_total_minutes, 65);
  assert.equal(stats.activity.target_completion_rate, 1);
  assert.equal(stats.trend.at(-1).avgTotalMinutes, 65);
  assert.equal(stats.trend.at(-1).targetCompletionRate, 1);
});

test('invalid calendar dates and PE durations cannot corrupt the demo database', async t => {
  const { call, sqlite } = await fixture(t);
  for (const path of ['/v1/today/menu','/v1/today/summary','/v1/admin/stats/overview','/v1/activity/manual']) {
    assert.equal((await call(`${path}?date=2026-02-30`, undefined, { admin: true })).status, 400);
  }
  const schedule = await call('/v1/admin/pe/timetable', { classGroupId: 'qa-class', weekday: 2, startTime: '14:00', endTime: '14:45' }, { admin: true, method: 'PUT' });
  const result = await call('/v1/admin/pe/session', { timetableId: schedule.value.id, date, actualActivityMinutes: 46 }, { admin: true, method: 'PUT' });
  assert.equal(result.status, 400);
  assert.equal(result.value.error, 'pe_minutes_exceed_lesson');
  assert.equal(sqlite.prepare('SELECT COUNT(*) AS n FROM pe_sessions WHERE timetable_id=?').get(schedule.value.id).n, 0);
});
