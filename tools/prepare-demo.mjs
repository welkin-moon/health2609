// Seed only the local, isolated competition database through actual API handlers.
// Existing menus and the main student's records are preserved.
const base = process.env.HEALTH2609_DEMO_URL || 'http://127.0.0.1:18790';
const host = new URL(base).hostname;
if (!['localhost', '127.0.0.1', '[::1]'].includes(host)) throw new Error('Demo preparation is local-only');
const now = new Date();
const date = process.argv[2] || `${now.getFullYear()}-${String(now.getMonth()+1).padStart(2,'0')}-${String(now.getDate()).padStart(2,'0')}`;
async function call(path, body, participant = 'demo-student', admin = false, method = body ? 'POST' : 'GET') {
  const response = await fetch(`${base}${path}`, { method, headers: {
    'Content-Type': 'application/json', 'x-demo-school': 'demo-school',
    'x-demo-participant': participant, 'x-demo-role': admin ? 'admin' : 'student'
  }, ...(body ? { body: JSON.stringify(body) } : {}), signal: AbortSignal.timeout(20000) });
  if (!response.ok) throw new Error(`${path}: HTTP ${response.status}`);
  return response.json();
}
let menu = await call(`/v1/today/menu?date=${date}`);
if (!menu.dishes.length) {
  await call('/v1/admin/menus', { date, mealSlot: 'lunch', dishes: [
    { name: '番茄炒蛋', standardServingGrams: 150, nutritionPerServing: { energyKcal: 180, proteinG: 12, fatG: 10, carbohydrateG: 10 } },
    { name: '清炒时蔬', standardServingGrams: 120, nutritionPerServing: { energyKcal: 75, proteinG: 3, fatG: 4, carbohydrateG: 8 } },
    { name: '米饭', standardServingGrams: 180, nutritionPerServing: { energyKcal: 230, proteinG: 5, fatG: 1, carbohydrateG: 48 } }
  ] }, 'demo-student', true, 'PUT');
  menu = await call(`/v1/today/menu?date=${date}`);
}
// These two synthetic participants make privacy-threshold behavior demonstrable.
// They are fixture records, not actual student health data or measured users.
for (const [participant, portion] of [['demo-student-2', 0.5], ['demo-student-3', 1]]) {
  await call('/v1/meals/consumption', { date, mealSlot: 'lunch', items: menu.dishes.map(dish => ({ dishId: dish.id, servingMultiplier: portion })) }, participant);
  await call('/v1/activity/outside-school', { date, exerciseMinutes: participant.endsWith('2') ? 20 : 30 }, participant);
}
console.log(JSON.stringify({ date, dishes: menu.dishes.map(d => d.name), syntheticParticipants: 2, mainStudentPreserved: true }));
