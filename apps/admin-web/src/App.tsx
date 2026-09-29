import { FormEvent, useEffect, useMemo, useState } from "react";
import {
  api,
  type ClassGroup,
  type DishDraft,
  type Nutrition,
  type Overview,
  type PeSessionItem,
  type SchoolDayWindow
} from "./api";

const createTempId = () => Math.random().toString(36).substring(2, 9);

const emptyDish = (): DishDraft => ({
  tempId: createTempId(),
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
  const [statsClassId, setStatsClassId] = useState("");
  const [peSessions, setPeSessions] = useState<PeSessionItem[]>([]);
  const [schoolWindows, setSchoolWindows] = useState<SchoolDayWindow[]>([]);
  const [peActual, setPeActual] = useState<Record<string, string>>({});
  const [peStartTime, setPeStartTime] = useState("14:00");
  const [peEndTime, setPeEndTime] = useState("14:45");
  const [schoolStartTime, setSchoolStartTime] = useState("08:00");
  const [schoolEndTime, setSchoolEndTime] = useState("17:00");
  const [status, setStatus] = useState("正在载入…");
  const [saving, setSaving] = useState(false);
  const [savingPe, setSavingPe] = useState(false);
  const [savingSchoolWindow, setSavingSchoolWindow] = useState(false);
  const [peError, setPeError] = useState("");

  const studentCount =
    typeof overview?.totalStudents === "number"
      ? overview.totalStudents
      : typeof (overview as any)?.studentCount === "number"
      ? (overview as any).studentCount
      : undefined;

  const isPrivacyMasked = Boolean(
    overview &&
      (overview.privacyMasked === true ||
        (studentCount !== undefined && studentCount < 3))
  );

  const participation = useMemo(
    () => Math.round((overview?.meal?.participationRate ?? 0) * 100),
    [overview]
  );

  async function refresh(
    targetDate = date,
    classGroupId = statsClassId,
    signal?: AbortSignal
  ) {
    setStatus("正在同步");
    try {
      const [menuResult, stats, classResult, schoolWindowResult] =
        await Promise.all([
          api.menus(targetDate, signal),
          api.overview(targetDate, classGroupId, signal),
          api.classes(signal),
          api.schoolDayWindows(signal)
        ]);

      if (signal?.aborted) return;

      const lunch = menuResult.menus.find((menu) => menu.mealSlot === "lunch");
      setDishes(
        lunch?.dishes.length
          ? lunch.dishes.map(
              ({ id, name, standardServingGrams, nutritionPerServing }) => ({
                id,
                tempId: id || createTempId(),
                name,
                standardServingGrams,
                nutritionPerServing
              })
            )
          : [emptyDish()]
      );

      setOverview(stats);
      setClasses(classResult.classes);
      setSchoolWindows(schoolWindowResult.items);
      setSelectedClassId((current) =>
        current || classResult.classes[0]?.id || ""
      );
      setStatus("已同步");
    } catch (error) {
      if (signal?.aborted || (error instanceof Error && error.name === "AbortError")) {
        return;
      }
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
    const controller = new AbortController();
    void refresh(date, statsClassId, controller.signal);
    return () => {
      controller.abort();
    };
  }, [date, statsClassId]);

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

  async function addSchoolWindow() {
    if (schoolStartTime >= schoolEndTime) {
      setStatus("在校结束时间需要晚于开始时间");
      return;
    }

    setSavingSchoolWindow(true);
    try {
      await api.saveSchoolDayWindow({
        weekday: weekdayForDate(date),
        startTime: schoolStartTime,
        endTime: schoolEndTime
      });
      const result = await api.schoolDayWindows();
      setSchoolWindows(result.items);
      setStatus("在校时段已保存，学生手机会排除这段时间");
    } catch (error) {
      setStatus(error instanceof Error ? error.message : "在校时段保存失败");
    } finally {
      setSavingSchoolWindow(false);
    }
  }

  async function handleAddPe() {
    if (!selectedClassId) return;

    if (peStartTime >= peEndTime) {
      const msg = "体育课结束时间需要晚于开始时间";
      setStatus(msg);
      setPeError(msg);
      return;
    }
    setPeError("");

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

  const addPeSchedule = handleAddPe;

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
          <span className="environment-badge">线上 Demo · h2609</span>
          <h1>今天的校园健康概览</h1>
          <p>
            菜单、营养构成和体育课实际活动统一录入，学生端只需要确认自己真正吃了多少。
          </p>
        </div>

        <div className="filters">
          <label className="date-field">
            <span>日期</span>
            <input
              value={date}
              onChange={(event) => setDate(event.target.value)}
              type="date"
            />
          </label>

          <label className="date-field">
            <span>统计范围</span>
            <select
              value={statsClassId}
              onChange={(event) => setStatsClassId(event.target.value)}
            >
              <option value="">全校</option>
              {classes.map((item) => (
                <option value={item.id} key={item.id}>
                  {item.name}
                </option>
              ))}
            </select>
          </label>
        </div>
      </header>

      {isPrivacyMasked && (
        <aside className="privacy-notice" role="status" aria-live="polite">
          <span className="privacy-badge">隐私保护</span>
          <span>已启用小样本隐私保护：当前班级样本过少，已脱敏个体精细营养均值</span>
        </aside>
      )}

      <section className="stats-grid" aria-label="统计概览">
        <article className="metric-card strong">
          <span>餐食记录参与率</span>
          <strong>{participation}%</strong>
          <small>{overview?.meal?.participants ?? 0} 名学生已记录</small>
        </article>

        <article className="metric-card">
          <span>平均记录能量</span>
          <strong>
            {isPrivacyMasked
              ? "已脱敏"
              : Math.round(Number(overview?.nutrition?.avg_energy_kcal ?? 0))}
          </strong>
          <small>{isPrivacyMasked ? "小样本隐私保护" : "kcal / 已记录学生"}</small>
        </article>

        <article className="metric-card">
          <span>体育课活动</span>
          <strong>
            {Math.round(Number(overview?.pe?.avg_pe_minutes ?? 0))} min
          </strong>
          <small>
            {overview?.pe?.recorded_sessions ?? 0}/{overview?.pe?.scheduled_sessions ?? 0} 节已录 ·{" "}
            {Math.round(Number(overview?.pe?.recordCoverage ?? 0) * 100)}% 覆盖
          </small>
        </article>

        <article className="metric-card">
          <span>当天总活动</span>
          <strong>
            {Math.round(Number(overview?.activity?.avg_total_minutes ?? 0))} min
          </strong>
          <small>
            {Math.round(Number(overview?.activity?.target_completion_rate ?? 0) * 100)}%
            达到学校运动目标
          </small>
        </article>
      </section>

      <section className="analytics-grid" aria-label="详细统计">
        <article className="surface trend-surface">
          <div className="section-heading compact-heading">
            <div>
              <span className="eyebrow">近 7 天</span>
              <h2>记录与运动趋势</h2>
            </div>
            <span className="scope-badge">
              {statsClassId
                ? classes.find((item) => item.id === statsClassId)?.name ?? "班级"
                : "全校"}
            </span>
          </div>

          {isPrivacyMasked ? (
            <div className="macro-strip masked">
              <span className="masked-cell">
                <b>已脱敏</b>
                蛋白质
              </span>
              <span className="masked-cell">
                <b>已脱敏</b>
                脂肪
              </span>
              <span className="masked-cell">
                <b>已脱敏</b>
                碳水
              </span>
            </div>
          ) : (
            <div className="macro-strip">
              <span>
                <b>{Math.round(Number(overview?.nutrition?.avg_protein_g ?? 0))}g</b>
                蛋白质
              </span>
              <span>
                <b>{Math.round(Number(overview?.nutrition?.avg_fat_g ?? 0))}g</b>
                脂肪
              </span>
              <span>
                <b>{Math.round(Number(overview?.nutrition?.avg_carbohydrate_g ?? 0))}g</b>
                碳水
              </span>
            </div>
          )}

          <div className="trend-list">
            {(overview?.trend ?? []).map((point) => {
              const mealRate = Math.max(
                0,
                Math.min(100, Math.round(point.mealParticipationRate * 100))
              );
              const targetRate = Math.round(point.targetCompletionRate * 100);

              return (
                <div className="trend-row" key={point.date}>
                  <div className="trend-label">
                    <strong>{point.date.slice(5)}</strong>
                    <small>{Math.round(point.avgTotalMinutes)} min</small>
                  </div>
                  <div
                    className="trend-meter"
                    role="progressbar"
                    aria-valuenow={mealRate}
                    aria-valuemin={0}
                    aria-valuemax={100}
                    aria-label={`${point.date} 餐食记录率`}
                    title={`餐食记录 ${mealRate}%`}
                  >
                    <span style={{ width: `${mealRate}%` }} />
                  </div>
                  <b>{mealRate}%</b>
                  <small className="goal-rate">{targetRate}% 达标</small>
                </div>
              );
            })}
          </div>
        </article>

        <article className="surface dish-stats-surface">
          <span className="eyebrow">午餐菜品</span>
          <h2>逐菜平均完成度</h2>
          <p>按学生确认的克数优先换算；没有克数时使用份量倍率。</p>

          <div className="dish-stat-list">
            {(overview?.dishes ?? []).length === 0 ? (
              <p className="empty-copy">当天还没有可统计的菜品记录。</p>
            ) : (
              (overview?.dishes ?? []).map((item) => {
                const completion = Math.round(item.avgCompletion * 100);
                const meter = Math.max(0, Math.min(100, completion));

                return (
                  <div className="dish-stat" key={item.id}>
                    <div className="dish-stat-title">
                      <strong>{item.name}</strong>
                      <small>
                        {item.participants}/{overview?.totalStudents ?? 0} 人记录
                      </small>
                    </div>
                    <div
                      className="dish-meter"
                      role="progressbar"
                      aria-valuenow={meter}
                      aria-valuemin={0}
                      aria-valuemax={100}
                      aria-label={`${item.name} 完成度`}
                    >
                      <span style={{ width: `${meter}%` }} />
                    </div>
                    <strong>{completion}%</strong>
                  </div>
                );
              })
            )}
          </div>
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
              <div
                className="dish-editor"
                key={dish.tempId || `dish-${dish.id || index}`}
              >
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
          <section className="surface school-window-surface">
            <span className="eyebrow">在校时段</span>
            <h2>手机数据排除范围</h2>
            <p>
              按星期保存。学生同步 Health Connect 时只会读取这段时间之外的运动汇总。
            </p>

            <div className="time-grid">
              <label className="stack-field">
                <span>到校</span>
                <input
                  type="time"
                  value={schoolStartTime}
                  onChange={(event) => setSchoolStartTime(event.target.value)}
                />
              </label>
              <label className="stack-field">
                <span>离校</span>
                <input
                  type="time"
                  value={schoolEndTime}
                  onChange={(event) => setSchoolEndTime(event.target.value)}
                />
              </label>
            </div>

            <button
              type="button"
              className="tonal-button"
              disabled={savingSchoolWindow}
              onClick={() => void addSchoolWindow()}
            >
              {savingSchoolWindow ? "保存中…" : "保存当前星期的在校时段"}
            </button>

            <div className="window-list">
              {schoolWindows
                .filter((item) => item.weekday === weekdayForDate(date))
                .map((item) => (
                  <span className="window-chip" key={item.id}>
                    {item.startTime}–{item.endTime}
                  </span>
                ))}
            </div>
          </section>

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
              onClick={() => void handleAddPe()}
            >
              添加到每周课程安排
            </button>
            {peError && (
              <p className="pe-error-notice" role="alert">
                {peError}
              </p>
            )}

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
