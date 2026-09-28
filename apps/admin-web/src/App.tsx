import { FormEvent, useEffect, useMemo, useState } from "react";
import {
  api,
  type ClassGroup,
  type DishDraft,
  type Nutrition,
  type Overview,
  type PeSessionItem
} from "./api";

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

const weekdayForDate = (date: string) => {
  const day = new Date(`${date}T00:00:00Z`).getUTCDay();
  return day === 0 ? 7 : day;
};

const nutritionKeys: Array<{
  key: keyof Nutrition;
  label: string;
  unit: string;
}> = [
  { key: "energyKcal", label: "能量", unit: "kcal" },
  { key: "proteinG", label: "蛋白质", unit: "g" },
  { key: "fatG", label: "脂肪", unit: "g" },
  { key: "carbohydrateG", label: "碳水", unit: "g" },
  { key: "fiberG", label: "膳食纤维", unit: "g" },
  { key: "sodiumMg", label: "钠", unit: "mg" },
  { key: "sugarG", label: "糖", unit: "g" },
  { key: "saturatedFatG", label: "饱和脂肪", unit: "g" }
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
  const [classes, setClasses] = useState<ClassGroup[]>([]);
  const [selectedClassId, setSelectedClassId] = useState("");
  const [peSessions, setPeSessions] = useState<PeSessionItem[]>([]);
  const [peActual, setPeActual] = useState<Record<string, string>>({});
  const [peStartTime, setPeStartTime] = useState("14:00");
  const [peEndTime, setPeEndTime] = useState("14:45");
  const [status, setStatus] = useState("正在载入…");
  const [saving, setSaving] = useState(false);
  const [savingPe, setSavingPe] = useState(false);

  const participation = useMemo(
    () => Math.round((overview?.meal?.participationRate ?? 0) * 100),
    [overview]
  );

  async function refresh(targetDate = date) {
    setStatus("正在同步");
    try {
      const [menuResult, stats, classResult] = await Promise.all([
        api.menus(targetDate),
        api.overview(targetDate),
        api.classes()
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
      setClasses(classResult.classes);
      setSelectedClassId((current) =>
        current || classResult.classes[0]?.id || ""
      );
      setStatus("已同步");
    } catch (error) {
      setStatus(error instanceof Error ? error.message : "载入失败");
    }
  }

  async function refreshPe(
    targetDate = date,
    classGroupId = selectedClassId
  ) {
    if (!classGroupId) {
      setPeSessions([]);
      return;
    }

    try {
      const result = await api.peSessions(targetDate, classGroupId);
      setPeSessions(result.items);
      setPeActual(
        Object.fromEntries(
          result.items.map((item) => [
            item.timetableId,
            item.actualActivityMinutes?.toString() ?? ""
          ])
        )
      );
    } catch (error) {
      setStatus(error instanceof Error ? error.message : "体育课载入失败");
    }
  }

  useEffect(() => {
    void refresh(date);
  }, [date]);

  useEffect(() => {
    void refreshPe(date, selectedClassId);
  }, [date, selectedClassId]);

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

  async function submitMenu(event: FormEvent) {
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
      setStatus(error instanceof Error ? error.message : "菜单保存失败");
    } finally {
      setSaving(false);
    }
  }

  async function addPeSchedule() {
    if (!selectedClassId) return;

    setSavingPe(true);
    try {
      await api.savePeTimetable({
        classGroupId: selectedClassId,
        weekday: weekdayForDate(date),
        startTime: peStartTime,
        endTime: peEndTime
      });
      await refreshPe();
      setStatus("体育课安排已保存");
    } catch (error) {
      setStatus(error instanceof Error ? error.message : "课程安排保存失败");
    } finally {
      setSavingPe(false);
    }
  }

  async function savePeActual(item: PeSessionItem) {
    const minutes = Number(peActual[item.timetableId]);
    if (!Number.isFinite(minutes) || minutes < 0 || minutes > 300) {
      setStatus("体育课实际活动时间请输入 0–300 分钟");
      return;
    }

    setSavingPe(true);
    try {
      await api.savePeSession({
        timetableId: item.timetableId,
        date,
        actualActivityMinutes: Math.round(minutes)
      });
      await Promise.all([refreshPe(), refresh(date)]);
      setStatus("体育课实际活动时间已记录");
    } catch (error) {
      setStatus(error instanceof Error ? error.message : "体育记录保存失败");
    } finally {
      setSavingPe(false);
    }
  }

  return (
    <main className="shell">
      <header className="topbar">
        <div>
          <span className="eyebrow">health2609 · 学校管理台</span>
          <h1>今天的校园健康概览</h1>
          <p>
            菜单、营养构成和体育课实际活动统一录入，学生端只需要确认自己真正吃了多少。
          </p>
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
          <span>平均记录能量</span>
          <strong>
            {Math.round(Number(overview?.nutrition?.avg_energy_kcal ?? 0))}
          </strong>
          <small>kcal / 已记录学生</small>
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
          <small>学生手机校外聚合均值</small>
        </article>
      </section>

      <section className="content-grid">
        <form className="surface menu-surface" onSubmit={submitMenu}>
          <div className="section-heading">
            <div>
              <span className="eyebrow">午餐菜单 · 营养构成</span>
              <h2>每份菜品数据</h2>
              <p>学生选择克数后，系统按标准份自动换算整份营养。</p>
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

                  <label>
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

        <div className="side-stack">
          <section className="surface pe-surface">
            <span className="eyebrow">体育课</span>
            <h2>课程安排 + 实际活动</h2>

            <label className="stack-field">
              <span>班级</span>
              <select
                value={selectedClassId}
                onChange={(event) => setSelectedClassId(event.target.value)}
              >
                {classes.map((item) => (
                  <option value={item.id} key={item.id}>
                    {item.name}
                  </option>
                ))}
              </select>
            </label>

            <div className="time-grid">
              <label className="stack-field">
                <span>开始</span>
                <input
                  type="time"
                  value={peStartTime}
                  onChange={(event) => setPeStartTime(event.target.value)}
                />
              </label>
              <label className="stack-field">
                <span>结束</span>
                <input
                  type="time"
                  value={peEndTime}
                  onChange={(event) => setPeEndTime(event.target.value)}
                />
              </label>
            </div>

            <button
              type="button"
              className="tonal-button"
              disabled={savingPe || !selectedClassId}
              onClick={() => void addPeSchedule()}
            >
              添加到每周课程安排
            </button>

            <div className="pe-session-list">
              {peSessions.length === 0 ? (
                <p className="empty-copy">当天没有匹配的体育课安排。</p>
              ) : (
                peSessions.map((item) => (
                  <div className="pe-session" key={item.timetableId}>
                    <div>
                      <strong>{item.startTime}–{item.endTime}</strong>
                      <small>今天实际活动分钟</small>
                    </div>
                    <input
                      type="number"
                      min="0"
                      max="300"
                      value={peActual[item.timetableId] ?? ""}
                      onChange={(event) =>
                        setPeActual((current) => ({
                          ...current,
                          [item.timetableId]: event.target.value
                        }))
                      }
                    />
                    <button
                      type="button"
                      className="mini-button"
                      disabled={savingPe}
                      onClick={() => void savePeActual(item)}
                    >
                      保存
                    </button>
                  </div>
                ))
              )}
            </div>
          </section>

          <aside className="surface note">
            <span className="eyebrow">统计口径</span>
            <h2>学校只提供校内事实</h2>
            <p>
              体育课按课程表匹配，并由管理员填写当节实际活动时间；学生手机只负责校外运动。
            </p>
            <div className="rule">
              <span>当天运动</span>
              <strong>体育课实际活动 + 校外运动</strong>
            </div>
          </aside>
        </div>
      </section>
    </main>
  );
}
