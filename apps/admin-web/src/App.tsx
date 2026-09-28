import { FormEvent, useEffect, useMemo, useState } from "react";
import { api, type DishDraft, type Nutrition, type Overview } from "./api";

const emptyDish = (): DishDraft => ({
  name: "",
  standardServingGrams: 150,
  nutritionPerServing: null
});

const localDate = () => {
  const now = new Date();
  const offset = now.getTimezoneOffset() * 60_000;
  return new Date(now.getTime() - offset).toISOString().slice(0, 10);
};

const nutritionKeys: Array<{
  key: keyof Nutrition;
  label: string;
  unit: string;
}> = [
  { key: "energyKcal", label: "能量", unit: "kcal" },
  { key: "proteinG", label: "蛋白质", unit: "g" },
  { key: "fatG", label: "脂肪", unit: "g" },
  { key: "carbohydrateG", label: "碳水", unit: "g" }
];

function normalizeDish(dish: DishDraft): DishDraft {
  const nutrition = dish.nutritionPerServing;
  const hasNutrition = nutrition && Object.values(nutrition).some(
    (value) => typeof value === "number" && Number.isFinite(value)
  );

  return {
    ...dish,
    name: dish.name.trim(),
    nutritionPerServing: hasNutrition ? nutrition : null
  };
}

export function App() {
  const [date, setDate] = useState(localDate());
  const [dishes, setDishes] = useState<DishDraft[]>([
    emptyDish(),
    emptyDish(),
    emptyDish()
  ]);
  const [overview, setOverview] = useState<Overview | null>(null);
  const [status, setStatus] = useState("正在载入…");
  const [saving, setSaving] = useState(false);

  const participation = useMemo(
    () => Math.round((overview?.meal?.participationRate ?? 0) * 100),
    [overview]
  );

  async function refresh(targetDate = date) {
    setStatus("正在同步");
    try {
      const [menuResult, stats] = await Promise.all([
        api.menus(targetDate),
        api.overview(targetDate)
      ]);
      const lunch = menuResult.menus.find((menu) => menu.mealSlot === "lunch");
      if (lunch?.dishes.length) {
        setDishes(
          lunch.dishes.map(
            ({ name, standardServingGrams, nutritionPerServing }) => ({
              name,
              standardServingGrams,
              nutritionPerServing
            })
          )
        );
      }
      setOverview(stats);
      setStatus("已同步");
    } catch (error) {
      setStatus(error instanceof Error ? error.message : "载入失败");
    }
  }

  useEffect(() => {
    void refresh(date);
  }, [date]);

  function updateDish(index: number, patch: Partial<DishDraft>) {
    setDishes((items) =>
      items.map((item, i) => (i === index ? { ...item, ...patch } : item))
    );
  }

  function updateNutrition(
    index: number,
    key: keyof Nutrition,
    rawValue: string
  ) {
    const parsed = rawValue === "" ? undefined : Number(rawValue);
    setDishes((items) =>
      items.map((item, i) => {
        if (i !== index) return item;
        return {
          ...item,
          nutritionPerServing: {
            ...(item.nutritionPerServing ?? {}),
            [key]: parsed
          }
        };
      })
    );
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    const valid = dishes
      .map(normalizeDish)
      .filter((dish) => dish.name);

    if (!valid.length) {
      setStatus("至少填写一道菜");
      return;
    }

    setSaving(true);
    try {
      await api.saveMenu(date, "lunch", valid);
      await refresh(date);
      setStatus("午餐菜单已保存");
    } catch (error) {
      setStatus(error instanceof Error ? error.message : "保存失败");
    } finally {
      setSaving(false);
    }
  }

  return (
    <main className="shell">
      <header className="topbar">
        <div>
          <span className="eyebrow">health2609 · 学校管理台</span>
          <h1>今天的校园健康概览</h1>
          <p>录菜单、记体育活动时间、看汇总。比赛 Demo 先把核心流程做顺。</p>
        </div>

        <label className="date-field">
          <span>日期</span>
          <input
            value={date}
            onChange={(event) => setDate(event.target.value)}
            type="date"
          />
        </label>
      </header>

      <section className="stats-grid" aria-label="统计概览">
        <article className="metric-card strong">
          <span>餐食记录参与率</span>
          <strong>{participation}%</strong>
          <small>{overview?.meal?.participants ?? 0} 名学生已记录</small>
        </article>

        <article className="metric-card">
          <span>平均食用份量</span>
          <strong>
            {Number(overview?.meal?.avg_serving_multiplier ?? 0).toFixed(1)}×
          </strong>
          <small>按已记录菜品计算</small>
        </article>

        <article className="metric-card">
          <span>体育课活动</span>
          <strong>
            {Math.round(Number(overview?.pe?.avg_pe_minutes ?? 0))} min
          </strong>
          <small>{overview?.pe?.recorded_sessions ?? 0} 节已录实际活动时间</small>
        </article>

        <article className="metric-card">
          <span>校外运动</span>
          <strong>
            {Math.round(Number(overview?.activity?.avg_outside_minutes ?? 0))} min
          </strong>
          <small>来自学生手机的校外日聚合</small>
        </article>
      </section>

      <section className="content-grid">
        <form className="surface" onSubmit={submit}>
          <div className="section-heading">
            <div>
              <span className="eyebrow">午餐菜单</span>
              <h2>学生端今天会看到这些菜</h2>
            </div>
            <button
              type="button"
              className="text-button"
              onClick={() => setDishes((items) => [...items, emptyDish()])}
            >
              + 添加菜品
            </button>
          </div>

          <div className="dish-list">
            {dishes.map((dish, index) => (
              <div className="dish-editor" key={index}>
                <div className="dish-row">
                  <label>
                    <span>菜品</span>
                    <input
                      value={dish.name}
                      onChange={(event) =>
                        updateDish(index, { name: event.target.value })
                      }
                      placeholder="例如 番茄炒蛋"
                    />
                  </label>

                  <label className="grams">
                    <span>标准份 / g</span>
                    <input
                      min="1"
                      max="3000"
                      type="number"
                      value={dish.standardServingGrams ?? ""}
                      onChange={(event) =>
                        updateDish(index, {
                          standardServingGrams: event.target.value
                            ? Number(event.target.value)
                            : null
                        })
                      }
                    />
                  </label>

                  <button
                    aria-label="删除菜品"
                    className="icon-button"
                    type="button"
                    onClick={() =>
                      setDishes((items) => items.filter((_, i) => i !== index))
                    }
                  >
                    ×
                  </button>
                </div>

                <div className="nutrition-grid">
                  {nutritionKeys.map(({ key, label, unit }) => (
                    <label key={key}>
                      <span>{label} / {unit}</span>
                      <input
                        min="0"
                        step="0.1"
                        type="number"
                        value={dish.nutritionPerServing?.[key] ?? ""}
                        onChange={(event) =>
                          updateNutrition(index, key, event.target.value)
                        }
                        placeholder="可选"
                      />
                    </label>
                  ))}
                </div>
              </div>
            ))}
          </div>

          <div className="actions">
            <span className="status">{status}</span>
            <button className="filled-button" disabled={saving} type="submit">
              {saving ? "保存中…" : "保存菜单"}
            </button>
          </div>
        </form>

        <aside className="surface note">
          <span className="eyebrow">数据口径</span>
          <h2>校内与校外分开算</h2>
          <p>
            体育课由管理员按课程安排记录当节实际活动分钟；学生手机只补充校外运动，
            不拿手机数据反推校内体育课。
          </p>
          <div className="rule">
            <span>当天运动</span>
            <strong>体育课实际活动 + 校外手机聚合</strong>
          </div>
        </aside>
      </section>
    </main>
  );
}
