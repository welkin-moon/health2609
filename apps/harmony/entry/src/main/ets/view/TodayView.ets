/**
 * TodayView - Material 3 Expressive (M3E) Native Today Screen for OpenHarmony / Kaihong
 * Implements Lunch, Physical Activity, Home Meal AI, and Today Overview with Adaptive Grid
 */

import { M3EColors, M3EShapes, M3ETypography } from '../theme/M3ETheme';
import {
  TodayMenuDto,
  DailySummaryDto,
  DishDto,
  ConfirmedHomeMealRequest,
  ManualActivityRequest,
  EnergyReferenceRequest,
  ApiWriteResult,
  MealItemRequest,
  ConfirmedHomeMealItemRequest,
  DishAmount,
  HomeMealDraftItem,
  NutritionTotals,
  roundOneDecimal,
  calculateServingNutrition
} from '../model/ApiModels';
import { PrivateRepository } from '../sync/PrivateRepository';
import { identifier } from '../sync/PrivateCrypto';
import { HealthApi } from '../service/HealthApi';
import { OfflineCacheService } from '../service/OfflineCacheService';

interface PortionOption {
  value: number;
  label: string;
}

const PORTION_OPTIONS: PortionOption[] = [
  { value: 0.0, label: '没吃' },
  { value: 0.25, label: '¼份' },
  { value: 0.5, label: '半份' },
  { value: 0.75, label: '¾份' },
  { value: 1.0, label: '1份' }
];

@Component
export struct TodayView {
  private healthApi: HealthApi = new HealthApi();
  private cacheService: OfflineCacheService = OfflineCacheService.getInstance();

  @State currentDate: string = '';
  @State displayDate: string = '';
  @State isLoading: boolean = true;
  @State statusMessage: string = '';
  @State statusIsError: boolean = false;
  @State pendingOfflineCount: number = 0;
  @State privateEnergy: number = 0;
  @State privateMinutes: number = 0;
  @State privateCount: number = 0;
  @State privateReference: number = 2000;

  // Menu & Lunch State
  @State menu: TodayMenuDto | null = null;
  @State dishAmounts: Map<string, DishAmount> = new Map<string, DishAmount>();
  @State dirtyDishIds: string[] = [];
  @State dirtyDishDate: string = '';
  @State showExactGramsMap: Map<string, boolean> = new Map<string, boolean>();
  @State isSavingMeal: boolean = false;
  @State lunchTotals: NutritionTotals = new NutritionTotals();

  // Summary State
  @State summary: DailySummaryDto | null = null;
  @State isEditingReference: boolean = false;
  @State energyReferenceInput: string = '2000';
  @State isSavingReference: boolean = false;

  // Home Meal AI State
  @State homeMealSlot: string = 'dinner';
  @State homeMealDraft: HomeMealDraftItem[] = [];
  @State homeMealNotes: string = '';
  @State isAnalyzingHomeMeal: boolean = false;
  @State isSavingHomeMeal: boolean = false;

  // Activity State
  @State isShowManualActivity: boolean = false;
  @State manualActivityType: string = '自主运动';
  @State manualDurationMinutes: number = 30;
  @State manualIntensity: 'light' | 'moderate' | 'vigorous' = 'moderate';
  @State isSavingActivity: boolean = false;
  @State isSyncingActivity: boolean = false;

  aboutToAppear() {
    const today = new Date();
    const y = today.getFullYear();
    const m = String(today.getMonth() + 1).padStart(2, '0');
    const d = String(today.getDate()).padStart(2, '0');
    this.currentDate = `${y}-${m}-${d}`;

    const weekdays = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六'];
    this.displayDate = `${today.getMonth() + 1}月${today.getDate()}日 ${weekdays[today.getDay()]}`;

    this.refreshPrivateSummary();
    this.loadTodayData();
  }

  /**
   * Loads today's menu, summary, and pending queue count
   */
  async loadTodayData() {
    const requestedDate = this.currentDate;
    if (this.dirtyDishDate !== requestedDate) {
      this.dirtyDishIds = [];
      this.dirtyDishDate = '';
    }
    this.isLoading = true;
    this.statusMessage = '';
    try {
      const menuData = await this.healthApi.getTodayMenu(requestedDate, 'lunch');
      const summaryData = await this.healthApi.getTodaySummary(requestedDate);

      if (requestedDate !== this.currentDate) return;

      this.menu = menuData;
      this.summary = summaryData;

      this.refreshPrivateSummary();

      // Initialize dish amounts
      const amounts = new Map<string, DishAmount>();
      const retainedDirtyDishIds: string[] = [];
      if (menuData && menuData.dishes) {
        for (const dish of menuData.dishes) {
          const current = this.dishAmounts.get(dish.id);
          const isDirty = this.dirtyDishIds.includes(dish.id);
          if (current && isDirty) {
            amounts.set(dish.id, current);
            retainedDirtyDishIds.push(dish.id);
            continue;
          }

          const savedGrams = dish.savedConsumedGrams ?? null;
          let savedMultiplier = dish.savedServingMultiplier ?? null;
          if (savedMultiplier === null) {
            savedMultiplier = savedGrams !== null && dish.standardServingGrams !== null && dish.standardServingGrams > 0
              ? savedGrams / dish.standardServingGrams
              : 0;
          }
          const restoredGrams = savedGrams !== null
            ? savedGrams
            : savedMultiplier > 0 && dish.standardServingGrams !== null
              ? Math.round(dish.standardServingGrams * savedMultiplier)
              : null;
          amounts.set(dish.id, new DishAmount(savedMultiplier, restoredGrams));
        }
      }
      this.dishAmounts = amounts;
      this.dirtyDishIds = retainedDirtyDishIds;
      this.calculateLunchTotals();

      this.pendingOfflineCount = await this.cacheService.getPendingCount();
    } catch (err) {
      if (requestedDate !== this.currentDate) return;
      this.statusMessage = '无法加载今日数据，请检查网络或登录后重试';
      this.statusIsError = true;
    } finally {
      if (requestedDate === this.currentDate) this.isLoading = false;
    }
  }

  /**
   * Recalculates preview nutrition totals for lunch
   */
  calculateLunchTotals() {
    let energy = 0;
    let protein = 0;
    let fat = 0;
    let carbs = 0;

    if (this.menu && this.menu.dishes) {
      for (const dish of this.menu.dishes) {
        const amount = this.dishAmounts.get(dish.id);
        if (amount && amount.servingMultiplier > 0 && dish.nutritionPerServing) {
          energy += (dish.nutritionPerServing.energyKcal || 0) * amount.servingMultiplier;
          protein += (dish.nutritionPerServing.proteinG || 0) * amount.servingMultiplier;
          fat += (dish.nutritionPerServing.fatG || 0) * amount.servingMultiplier;
          carbs += (dish.nutritionPerServing.carbohydrateG || 0) * amount.servingMultiplier;
        }
      }
    }

    this.lunchTotals = new NutritionTotals(
      roundOneDecimal(energy),
      roundOneDecimal(protein),
      roundOneDecimal(fat),
      roundOneDecimal(carbs)
    );
  }

  /**
   * Portion chip selected
   */
  onPortionSelect(dish: DishDto, portion: number) {
    if (this.isSavingMeal) return;
    const grams = dish.standardServingGrams ? Math.round(dish.standardServingGrams * portion) : null;
    const updated = new Map<string, DishAmount>(this.dishAmounts);
    updated.set(dish.id, new DishAmount(portion, grams));
    this.dishAmounts = updated;
    this.markDishDirty(dish.id);
    this.calculateLunchTotals();
  }

  markDishDirty(dishId: string) {
    this.dirtyDishDate = this.currentDate;
    if (!this.dirtyDishIds.includes(dishId)) {
      this.dirtyDishIds = this.dirtyDishIds.concat(dishId);
    }
  }

  /**
   * Custom grams entered
   */
  onGramsChange(dish: DishDto, gramsStr: string) {
    if (this.isSavingMeal) return;
    const grams = Number(gramsStr);
    const validGrams = !Number.isFinite(grams) || grams < 0 ? null : grams;
    let multiplier = 0;
    if (validGrams && dish.standardServingGrams && dish.standardServingGrams > 0) {
      multiplier = Math.round((validGrams / dish.standardServingGrams) * 100) / 100;
    }
    const updated = new Map<string, DishAmount>(this.dishAmounts);
    updated.set(dish.id, new DishAmount(multiplier, validGrams));
    this.dishAmounts = updated;
    this.markDishDirty(dish.id);
    this.calculateLunchTotals();
  }

  /**
   * Save Lunch Consumption
   */
  async saveLunch() {
    if (!this.menu || this.isSavingMeal) return;
    for (const dish of this.menu.dishes) {
      const amount = this.dishAmounts.get(dish.id);
      if (amount && amount.consumedGrams !== null) {
        const maxGrams = dish.standardServingGrams === null
          ? 5000
          : Math.min(5000, dish.standardServingGrams * 5);
        if (amount.consumedGrams > maxGrams) {
          this.statusMessage = `${dish.name}的分量不能超过 ${maxGrams} 克（最多5份），请调整后再保存`;
          this.statusIsError = true;
          return;
        }
      }
    }
    this.isSavingMeal = true;
    try {
      const items: MealItemRequest[] = [];
      for (const d of this.menu.dishes) {
        const amt = this.dishAmounts.get(d.id) || new DishAmount();
        const item: MealItemRequest = {
          dishId: d.id,
          servingMultiplier: amt.servingMultiplier
        };
        if (amt.consumedGrams !== null) item.consumedGrams = amt.consumedGrams;
        items.push(item);
      }

      const res = await this.healthApi.saveMeal({
        date: this.currentDate,
        mealSlot: 'lunch',
        items: items
      });

      if (res.ok) {
        this.statusMessage = res.queued ? '午餐已暂存，等待同步' : '午餐记录已成功保存！';
        this.statusIsError = false;
        if (!res.queued) {
          this.dirtyDishIds = [];
          this.dirtyDishDate = '';
        }
        // Refresh school demo summary
        this.summary = await this.refreshSummary();
        this.pendingOfflineCount = await this.cacheService.getPendingCount();
      }
    } catch (e) {
      this.statusMessage = '午餐保存失败，请重试';
      this.statusIsError = true;
    } finally {
      this.isSavingMeal = false;
    }
  }

  /**
   * AI Home Meal Analysis Trigger
   */
  async triggerHomeMealAnalysis(templateType: string) {
    this.isAnalyzingHomeMeal = true;
    this.statusMessage = '';
    try {
      const result = await this.healthApi.analyzeHomeMeal();
      this.homeMealDraft = result.items.map(item => new HomeMealDraftItem(
        item.name,
        item.estimatedGrams ?? null,
        item.estimatedGrams ?? null,
        item.confidence,
        item.nutrition ?? null,
        item.needsConfirmation
      ));
      this.homeMealNotes = result.notes && result.notes.length > 0 ? result.notes[0] : '';
    } catch (e) {
      this.statusMessage = '餐食识别未能连接云端，请手动添加或重试';
      this.statusIsError = true;
    } finally {
      this.isAnalyzingHomeMeal = false;
    }
  }

  /**
   * Save Confirmed Home Meal
   */
  async saveHomeMeal() {
    if (this.homeMealDraft.length === 0 || this.isSavingHomeMeal) return;
    if (this.homeMealDraft.some((item: HomeMealDraftItem) => item.grams === null || !Number.isFinite(item.grams) || item.grams < 0 || item.name.trim().length === 0)) {
      this.statusMessage = '请核对每份食物的名称与克数';
      this.statusIsError = true;
      return;
    }
    this.isSavingHomeMeal = true;
    try {
      const items: ConfirmedHomeMealItemRequest[] = this.homeMealDraft.map((item: HomeMealDraftItem): ConfirmedHomeMealItemRequest => ({
        name: item.name,
        grams: item.grams,
        nutrition: calculateServingNutrition(item.nutritionAtSource, item.sourceGrams && item.grams !== null ? item.grams / item.sourceGrams : 1)
      }));

      const request: ConfirmedHomeMealRequest = {
        date: this.currentDate,
        mealSlot: this.homeMealSlot,
        items: items
      };
      await PrivateRepository.getInstance().save('home_meal', this.currentDate + ':' + this.homeMealSlot, JSON.stringify(request));
      const res: ApiWriteResult = { ok: true };

      if (res.ok) {
        this.statusMessage = '家庭餐已加密保存到本机；可到私密记录页手动同步';
        this.statusIsError = false;
        this.homeMealDraft = [];
        this.refreshPrivateSummary();
        this.pendingOfflineCount = await this.cacheService.getPendingCount();
      }
    } catch (e) {
      this.statusMessage = '本地加密保存失败，家庭餐草稿已保留，请重试';
      this.statusIsError = true;
    } finally {
      this.isSavingHomeMeal = false;
    }
  }

  /**
   * Save Manual Activity Record
   */
  async saveManualActivity() {
    if (this.isSavingActivity) return;
    this.isSavingActivity = true;
    try {
      const request: ManualActivityRequest = {
        date: this.currentDate,
        activityType: this.manualActivityType.trim() || '自主运动',
        durationMinutes: this.manualDurationMinutes,
        intensity: this.manualIntensity
      };
      await PrivateRepository.getInstance().save('manual_activity', this.currentDate + ':' + identifier(), JSON.stringify(request));
      const res: ApiWriteResult = { ok: true };

      if (res.ok) {
        this.statusMessage = '运动已加密保存到本机；可到私密记录页手动同步';
        this.statusIsError = false;
        this.isShowManualActivity = false;
        this.refreshPrivateSummary();
        this.pendingOfflineCount = await this.cacheService.getPendingCount();
      }
    } catch (e) {
      this.statusMessage = '本地加密保存失败，运动内容已保留，请重试';
      this.statusIsError = true;
    } finally {
      this.isSavingActivity = false;
    }
  }

  /**
   * Sync Campus / Terminal Activity
   */
  async syncCampusActivity() {
    this.isSyncingActivity = true;
    try {
      const syncedCount = await this.healthApi.syncOfflineRecords();
      this.summary = await this.refreshSummary();
      this.pendingOfflineCount = await this.cacheService.getPendingCount();
      this.statusMessage = syncedCount > 0
        ? `已同步 ${syncedCount} 条离线数据并刷新运动记录`
        : this.pendingOfflineCount > 0
          ? `还有 ${this.pendingOfflineCount} 条记录等待同步`
          : '终端运动数据已是最新';
      this.statusIsError = false;
    } catch (e) {
      this.statusMessage = '同步检查完成';
    } finally {
      this.isSyncingActivity = false;
    }
  }

  /**
   * Save Energy Reference
   */
  async saveEnergyReference() {
    const kcal = Number(this.energyReferenceInput);
    if (!Number.isFinite(kcal) || kcal < 500 || kcal > 6000) {
      this.statusMessage = '请输入合理的每日能量参考（500 - 6000 千卡）';
      this.statusIsError = true;
      return;
    }

    this.isSavingReference = true;
    try {
      const preference: EnergyReferenceRequest = { dailyEnergyReferenceKcal: kcal };
      await PrivateRepository.getInstance().save('preference', 'energy_reference', JSON.stringify(preference));
      this.isEditingReference = false;
      this.refreshPrivateSummary();
      this.statusMessage = '个人参考热量已加密保存到本机';
      this.statusIsError = false;
    } catch (e) {
      this.statusMessage = '参考热量保存失败，请重试';
      this.statusIsError = true;
    } finally {
      this.isSavingReference = false;
    }
  }

  private refreshPrivateSummary(): void {
    let energy = 0, minutes = 0, count = 0, reference = 2000;
    for (const record of PrivateRepository.getInstance().records()) {
      if (record.entityType === 'home_meal') {
        const meal = JSON.parse(record.payloadJson) as ConfirmedHomeMealRequest;
        if (meal.date === this.currentDate) { count++; for (const item of meal.items) energy += Number(item.nutrition?.energyKcal ?? 0); }
      } else if (record.entityType === 'manual_activity') {
        const activity = JSON.parse(record.payloadJson) as ManualActivityRequest;
        if (activity.date === this.currentDate) { count++; minutes += activity.durationMinutes; }
      } else if (record.entityType === 'preference' && record.entityId === 'energy_reference') {
        reference = (JSON.parse(record.payloadJson) as EnergyReferenceRequest).dailyEnergyReferenceKcal ?? 2000;
      }
    }
    this.privateEnergy = Math.round(energy); this.privateMinutes = minutes; this.privateCount = count; this.privateReference = reference;
    if (!this.isEditingReference) this.energyReferenceInput = String(reference);
  }

  @Builder
  PrivateOverview() {
    Column({ space: 6 }) {
      Text('今日个人记录').fontSize(20).fontWeight(FontWeight.Medium)
      Text(`家庭餐 ${this.privateEnergy} 千卡 · 手动运动 ${this.privateMinutes} 分钟 · ${this.privateCount} 条记录`)
      Text(`个人每日参考 ${this.privateReference} 千卡；先保存到本机，账号页可手动同步与查看历史。`).fontSize(12)
        // Daily Energy Reference Edit
        Row() {
          Text(`修改个人每日参考（${this.privateReference} 千卡）`)
            .fontSize(M3ETypography.labelMedium)
            .fontColor(M3EColors.primary)
            .fontWeight(FontWeight.Medium)
            .onClick(() => {
              this.isEditingReference = !this.isEditingReference;
            })
        }

        if (this.isEditingReference) {
          Row({ space: 8 }) {
            TextInput({
              placeholder: '每日参考（千卡）',
              text: this.energyReferenceInput
            })
              .type(InputType.Number)
              .layoutWeight(1)
              .height(38)
              .backgroundColor(M3EColors.surfaceContainerHighest)
              .borderRadius(M3EShapes.buttonRadius)
              .onChange((val: string) => {
                this.energyReferenceInput = val;
              })

            Button(this.isSavingReference ? '...' : '保存')
              .height(38)
              .backgroundColor(M3EColors.primary)
              .fontColor(M3EColors.onPrimary)
              .borderRadius(M3EShapes.buttonRadius)
              .onClick(() => {
                this.saveEnergyReference();
              })
          }
          .width('100%')
        }


    }.width('100%').alignItems(HorizontalAlign.Start).padding(16).backgroundColor(M3EColors.surfaceContainer).borderRadius(M3EShapes.sectionRadius)
  }

  async refreshSummary(): Promise<DailySummaryDto | null> {
    try { return await this.healthApi.getTodaySummary(this.currentDate); }
    catch (e) { return this.summary; }
  }

  build() {
    Scroll() {
      Column({ space: 16 }) {
        // TopBar
        this.TopBar()
        this.PrivateOverview()

        // Status Banner if any
        if (this.statusMessage.length > 0) {
          this.StatusBanner()
        }

        // Adaptive Multi-Column Layout (Tablet / Landscape Terminal vs Handheld)
        GridRow({
          columns: 12,
          gutter: { x: 16, y: 16 },
          breakpoints: { value: ['600vp', '840vp'] }
        }) {
          // Left Pane on Tablet / First Section on Handheld: Lunch & Home Meal
          GridCol({ span: { sm: 12, md: 6, lg: 6 } }) {
            Column({ space: 18 }) {
              this.LunchSection()
              this.HomeMealSection()
            }
            .width('100%')
          }

          // Right Pane on Tablet / Second Section on Handheld: Summary & Activity
          GridCol({ span: { sm: 12, md: 6, lg: 6 } }) {
            Column({ space: 18 }) {
              this.TodayOverviewSection()
              this.PhysicalActivitySection()
            }
            .width('100%')
          }
        }
      }
      .width('100%')
      .padding({ left: 16, right: 16, top: 12, bottom: 120 })
    }
    .width('100%')
    .height('100%')
    .scrollBar(BarState.Off)
  }

  /* -------------------------------------------------------------------------- */
  /*                                UI Builders                                 */
  /* -------------------------------------------------------------------------- */

  @Builder
  TopBar() {
    Row() {
      Column({ space: 2 }) {
        Text('今天')
          .fontSize(M3ETypography.headlineMedium)
          .fontWeight(FontWeight.Bold)
          .fontColor(M3EColors.onSurface)

        Text(this.displayDate)
          .fontSize(M3ETypography.bodySmall)
          .fontColor(M3EColors.onSurfaceVariant)
      }
      .alignItems(HorizontalAlign.Start)

      Blank()

      // Pending Offline Sync Badge
      if (this.pendingOfflineCount > 0) {
        Row({ space: 4 }) {
          Text('离线待同步')
            .fontSize(M3ETypography.labelSmall)
            .fontColor(M3EColors.onSecondaryContainer)
          Text(`${this.pendingOfflineCount}`)
            .fontSize(M3ETypography.labelSmall)
            .fontWeight(FontWeight.Bold)
            .fontColor(M3EColors.onSecondaryContainer)
        }
        .padding({ left: 8, right: 8, top: 4, bottom: 4 })
        .backgroundColor(M3EColors.secondaryContainer)
        .borderRadius(M3EShapes.badgeRadius)
        .margin({ right: 10 })
      }

      // Refresh Button
      Button({ type: ButtonType.Circle }) {
        Text(this.isLoading ? '...' : '↻')
          .fontSize(20)
          .fontWeight(FontWeight.Bold)
          .fontColor(M3EColors.primary)
      }
      .width(42)
      .height(42)
      .backgroundColor(M3EColors.surfaceContainerHigh)
      .onClick(() => {
        this.refreshPrivateSummary();
    this.loadTodayData();
      })
    }
    .width('100%')
    .alignItems(VerticalAlign.Center)
    .padding({ top: 4, bottom: 4 })
  }

  @Builder
  StatusBanner() {
    Row({ space: 8 }) {
      Text(this.statusIsError ? '!' : '✓')
        .fontSize(14)
        .fontWeight(FontWeight.Bold)
        .fontColor(this.statusIsError ? M3EColors.onErrorContainer : M3EColors.onSecondaryContainer)

      Text(this.statusMessage)
        .fontSize(M3ETypography.bodySmall)
        .fontColor(this.statusIsError ? M3EColors.onErrorContainer : M3EColors.onSecondaryContainer)
        .layoutWeight(1)
    }
    .width('100%')
    .padding(12)
    .backgroundColor(this.statusIsError ? M3EColors.errorContainer : M3EColors.secondaryContainer)
    .borderRadius(M3EShapes.chipRadius)
  }

  /* ------------------------------- 午餐 SECTION ------------------------------ */

  @Builder
  LunchSection() {
    Column({ space: 14 }) {
      // Section Heading
      this.SectionHeader('午餐', '学校今天的菜单，选你实际吃下的量', '餐')

      // Dishes List
      if (this.menu && this.menu.dishes && this.menu.dishes.length > 0) {
        ForEach(this.menu.dishes, (dish: DishDto) => {
          this.DishCard(dish)
        }, (dish: DishDto) => dish.id)

        // Lunch Preview & Save Card
        this.LunchSummaryCard()
      } else {
        // Empty State
        Column({ space: 6 }) {
          Text('今天的午餐还没发布')
            .fontSize(M3ETypography.titleMedium)
            .fontWeight(FontWeight.Medium)
            .fontColor(M3EColors.onSurface)

          Text('学校录入后会自动出现在这里。')
            .fontSize(M3ETypography.bodySmall)
            .fontColor(M3EColors.onSurfaceVariant)
        }
        .width('100%')
        .padding(18)
        .backgroundColor(M3EColors.surfaceContainerLow)
        .borderRadius(M3EShapes.innerSectionRadius)
      }
    }
    .width('100%')
    .alignItems(HorizontalAlign.Start)
  }

  @Builder
  DishCard(dish: DishDto) {
    Column({ space: 10 }) {
      // Dish Header: Name & Serving info
      Column({ space: 3 }) {
        Text(dish.name)
          .fontSize(M3ETypography.titleMedium)
          .fontWeight(FontWeight.Medium)
          .fontColor(M3EColors.onSurface)

        Row({ space: 8 }) {
          if (dish.standardServingGrams) {
            Text(`一份约 ${dish.standardServingGrams} 克`)
              .fontSize(M3ETypography.bodySmall)
              .fontColor(M3EColors.onSurfaceVariant)
          }
          if (dish.nutritionPerServing && dish.nutritionPerServing.energyKcal) {
            Text(`· ${Math.round(dish.nutritionPerServing.energyKcal)} 千卡`)
              .fontSize(M3ETypography.bodySmall)
              .fontColor(M3EColors.onSurfaceVariant)
          }
        }
      }
      .width('100%')
      .alignItems(HorizontalAlign.Start)

      // Portion Selection Chips (0, 1/4, 1/2, 3/4, 1)
      Row({ space: 6 }) {
        ForEach(PORTION_OPTIONS, (option: PortionOption) => {
          this.PortionChip(dish, option)
        })
      }
      .width('100%')

      // Grams input toggle
      Row() {
        Text(this.showExactGramsMap.get(dish.id) ? '收起精确克数' : '需要时精确到克')
          .fontSize(M3ETypography.labelMedium)
          .fontColor(M3EColors.primary)
          .fontWeight(FontWeight.Medium)
          .onClick(() => {
            const current = this.showExactGramsMap.get(dish.id) || false;
            const updated = new Map<string, boolean>(this.showExactGramsMap);
            updated.set(dish.id, !current);
            this.showExactGramsMap = updated;
          })
      }

      // Expandable Exact Grams Input
      if (this.showExactGramsMap.get(dish.id)) {
        Row({ space: 8 }) {
          TextInput({
            placeholder: '实际克数',
            text: (this.dishAmounts.get(dish.id)?.consumedGrams ?? '') + ''
          })
            .type(InputType.Number)
            .height(40)
            .layoutWeight(1)
            .enabled(!this.isSavingMeal)
            .backgroundColor(M3EColors.surfaceContainerHigh)
            .borderRadius(M3EShapes.buttonRadius)
            .onChange((value: string) => {
              this.onGramsChange(dish, value);
            })

          Text('克')
            .fontSize(M3ETypography.bodyMedium)
            .fontColor(M3EColors.onSurfaceVariant)
        }
        .width('100%')
        .padding({ top: 4 })
      }
    }
    .width('100%')
    .padding(16)
    .backgroundColor(M3EColors.surfaceContainerLow)
    .borderRadius(M3EShapes.innerSectionRadius)
    .alignItems(HorizontalAlign.Start)
  }

  @Builder
  PortionChip(dish: DishDto, option: PortionOption) {
    // Check if selected
    Text(option.label)
      .fontSize(M3ETypography.labelLarge)
      .fontWeight(FontWeight.Medium)
      .fontColor(
        Math.abs((this.dishAmounts.get(dish.id)?.servingMultiplier ?? 0) - option.value) < 0.05
          ? M3EColors.onSecondaryContainer
          : M3EColors.onSurfaceVariant
      )
      .padding({ left: 12, right: 12, top: 8, bottom: 8 })
      .backgroundColor(
        Math.abs((this.dishAmounts.get(dish.id)?.servingMultiplier ?? 0) - option.value) < 0.05
          ? M3EColors.secondaryContainer
          : M3EColors.surfaceContainerHigh
      )
      .borderRadius(M3EShapes.chipRadius)
      .enabled(!this.isSavingMeal)
      .onClick(() => {
        this.onPortionSelect(dish, option.value);
      })
  }

  @Builder
  LunchSummaryCard() {
    Row() {
      Column({ space: 4 }) {
        Text('午餐合计')
          .fontSize(M3ETypography.labelLarge)
          .fontWeight(FontWeight.Medium)
          .fontColor(M3EColors.onTertiaryContainer)

        Text(`${Math.round(this.lunchTotals.energyKcal)} 千卡 · 蛋白 ${this.lunchTotals.proteinG}g · 脂肪 ${this.lunchTotals.fatG}g · 碳水 ${this.lunchTotals.carbohydrateG}g`)
          .fontSize(M3ETypography.bodySmall)
          .fontColor(M3EColors.onTertiaryContainer)
      }
      .layoutWeight(1)
      .alignItems(HorizontalAlign.Start)

      Button(this.isSavingMeal ? '保存中...' : '保存午餐记录')
        .fontSize(M3ETypography.labelMedium)
        .fontWeight(FontWeight.Medium)
        .backgroundColor(M3EColors.primary)
        .fontColor(M3EColors.onPrimary)
        .borderRadius(M3EShapes.buttonRadius)
        .height(38)
        .enabled(!this.isSavingMeal)
        .onClick(() => {
          this.saveLunch();
        })
    }
    .width('100%')
    .padding(16)
    .backgroundColor(M3EColors.tertiaryContainer)
    .borderRadius(M3EShapes.innerSectionRadius)
    .alignItems(VerticalAlign.Center)
  }

  /* ----------------------------- 家庭餐 AI SECTION --------------------------- */

  @Builder
  HomeMealSection() {
    Column({ space: 14 }) {
      this.SectionHeader('私密家庭餐 / 其他餐食', '早餐、晚餐或其他不在学校吃的东西', '照')

      Column({ space: 12 }) {
        // Slot Selector: 早餐 / 午餐 / 晚餐
        Row({ space: 8 }) {
          this.MealSlotChip('breakfast', '早餐')
          this.MealSlotChip('lunch', '午餐')
          this.MealSlotChip('dinner', '晚餐')
        }

        // Camera / Gallery Photo Trigger
        Row({ space: 10 }) {
          Button(this.isAnalyzingHomeMeal ? '识别分析中...' : '选择餐食照片')
            .layoutWeight(1)
            .height(42)
            .backgroundColor(M3EColors.primary)
            .fontColor(M3EColors.onPrimary)
            .borderRadius(M3EShapes.buttonRadius)
            .enabled(!this.isAnalyzingHomeMeal)
            .onClick(() => {
              this.triggerHomeMealAnalysis(this.homeMealSlot);
            })


        }
        .width('100%')

        // AI Note
        if (this.homeMealNotes.length > 0) {
          Text(this.homeMealNotes)
            .fontSize(M3ETypography.bodySmall)
            .fontColor(M3EColors.onSurfaceVariant)
        }

        // Detected Food Items (Editable)
        if (this.homeMealDraft.length > 0) {
          ForEach(this.homeMealDraft, (item: HomeMealDraftItem, index: number) => {
            this.HomeMealDraftRow(item, index)
          })

          // Save Home Meal Button
          Button(this.isSavingHomeMeal ? '正在记入...' : '确认并记入今日营养')
            .width('100%')
            .height(42)
            .backgroundColor(M3EColors.primary)
            .fontColor(M3EColors.onPrimary)
            .borderRadius(M3EShapes.buttonRadius)
            .enabled(!this.isSavingHomeMeal)
            .onClick(() => {
              this.saveHomeMeal();
            })

          Text('名称和分量由你最后确认；确认前不会记进今天。')
            .fontSize(M3ETypography.labelSmall)
            .fontColor(M3EColors.onSurfaceVariant)
        }
      }
      .width('100%')
      .padding(16)
      .backgroundColor(M3EColors.surfaceContainerLow)
      .borderRadius(M3EShapes.cardRadius)
    }
    .width('100%')
    .alignItems(HorizontalAlign.Start)
  }

  @Builder
  MealSlotChip(slotValue: string, slotLabel: string) {
    Text(slotLabel)
      .fontSize(M3ETypography.labelMedium)
      .fontWeight(FontWeight.Medium)
      .fontColor(this.homeMealSlot === slotValue ? M3EColors.onSecondaryContainer : M3EColors.onSurfaceVariant)
      .padding({ left: 16, right: 16, top: 8, bottom: 8 })
      .backgroundColor(this.homeMealSlot === slotValue ? M3EColors.secondaryContainer : M3EColors.surfaceContainerHigh)
      .borderRadius(M3EShapes.chipRadius)
      .onClick(() => {
        this.homeMealSlot = slotValue;
      })
  }

  @Builder
  HomeMealDraftRow(item: HomeMealDraftItem, index: number) {
    Column({ space: 8 }) {
      Row({ space: 8 }) {
        TextInput({ text: item.name })
          .layoutWeight(1)
          .height(38)
          .backgroundColor(M3EColors.surfaceContainerHighest)
          .borderRadius(M3EShapes.buttonRadius)
          .onChange((val: string) => {
            item.name = val;
          })

        Button('移除')
          .fontSize(M3ETypography.labelSmall)
          .fontColor(M3EColors.error)
          .backgroundColor(Color.Transparent)
          .onClick(() => {
            const list = this.homeMealDraft.slice();
            list.splice(index, 1);
            this.homeMealDraft = list;
          })
      }

      Row({ space: 8 }) {
        TextInput({ text: (item.grams ?? '') + '', placeholder: '分量(克)' })
          .layoutWeight(1)
          .height(38)
          .type(InputType.Number)
          .backgroundColor(M3EColors.surfaceContainerHighest)
          .borderRadius(M3EShapes.buttonRadius)
          .onChange((val: string) => {
            const grams = Number(val);
            const list = this.homeMealDraft.slice();
            const replacement = new HomeMealDraftItem(item.name, item.sourceGrams,
              Number.isFinite(grams) && grams >= 0 ? grams : null, item.confidence,
              item.nutritionAtSource, item.needsConfirmation);
            replacement.id = item.id;
            list[index] = replacement;
            this.homeMealDraft = list;
          })

        Text('克')
          .fontSize(M3ETypography.bodyMedium)
          .fontColor(M3EColors.onSurfaceVariant)
      }

      if (item.nutritionAtSource && item.nutritionAtSource.energyKcal) {
        Text(`估算 ${Math.round(item.nutritionAtSource.energyKcal)} 千卡 · 蛋白 ${item.nutritionAtSource.proteinG}g · 碳水 ${item.nutritionAtSource.carbohydrateG}g`)
          .fontSize(M3ETypography.labelSmall)
          .fontColor(M3EColors.onSurfaceVariant)
      }
    }
    .width('100%')
    .padding(12)
    .backgroundColor(M3EColors.surfaceContainerHigh)
    .borderRadius(M3EShapes.innerSectionRadius)
  }

  /* ----------------------------- 运动 SECTION -------------------------------- */

  @Builder
  PhysicalActivitySection() {
    Column({ space: 14 }) {
      this.SectionHeader('运动（手动补记保存在私密记录）', '学校记校内，终端/手机补校外', '动')

      Column({ space: 14 }) {
        // Activity Minutes Stats Row
        Row() {
          Column({ space: 2 }) {
            Text(`${this.summary?.activity?.totalMinutes ?? 0} 分钟`)
              .fontSize(M3ETypography.headlineSmall)
              .fontWeight(FontWeight.Bold)
              .fontColor(M3EColors.onSurface)

            Text('今天已记录的运动')
              .fontSize(M3ETypography.labelSmall)
              .fontColor(M3EColors.onSurfaceVariant)
          }
          .alignItems(HorizontalAlign.Start)

          Blank()

          Button(this.isSyncingActivity ? '同步中...' : '更新终端记录')
            .fontSize(M3ETypography.labelMedium)
            .backgroundColor(M3EColors.secondaryContainer)
            .fontColor(M3EColors.onSecondaryContainer)
            .borderRadius(M3EShapes.buttonRadius)
            .height(36)
            .onClick(() => {
              this.syncCampusActivity();
            })
        }
        .width('100%')

        // Breakdown Columns: School PE / Outside / Energy
        Row({ space: 10 }) {
          this.MetricBlock(`${this.summary?.activity?.peMinutes ?? 0} 分钟`, '校内体育')
          this.MetricBlock(`${this.summary?.activity?.outsideMinutes ?? 0} 分钟`, '校外运动')
          this.MetricBlock(
            this.summary?.activity?.activeEnergyKcal ? `${Math.round(this.summary.activity.activeEnergyKcal)} 千卡` : '—',
            '活动消耗'
          )
        }
        .width('100%')

        // Manual Activity Logging Toggle
        Row() {
          Text(this.isShowManualActivity ? '收起补记' : '补记一次运动')
            .fontSize(M3ETypography.labelMedium)
            .fontColor(M3EColors.primary)
            .fontWeight(FontWeight.Medium)
            .onClick(() => {
              this.isShowManualActivity = !this.isShowManualActivity;
            })
        }

        // Expandable Manual Activity Form
        if (this.isShowManualActivity) {
          Column({ space: 12 }) {
            Text('运动类型')
              .fontSize(M3ETypography.labelMedium)
              .fontWeight(FontWeight.Medium)

            Row({ space: 6 }) {
              ForEach(['跑步', '跳绳', '羽毛球', '篮球', '自主运动'], (itemType: string) => {
                Text(itemType)
                  .fontSize(M3ETypography.labelMedium)
                  .fontColor(this.manualActivityType === itemType ? M3EColors.onSecondaryContainer : M3EColors.onSurfaceVariant)
                  .padding({ left: 10, right: 10, top: 6, bottom: 6 })
                  .backgroundColor(this.manualActivityType === itemType ? M3EColors.secondaryContainer : M3EColors.surfaceContainerHigh)
                  .borderRadius(M3EShapes.chipRadius)
                  .onClick(() => {
                    this.manualActivityType = itemType;
                  })
              })
            }

            // Duration Slider
            Row() {
              Text(`运动时长: ${this.manualDurationMinutes} 分钟`)
                .fontSize(M3ETypography.bodyMedium)
                .fontWeight(FontWeight.Medium)
            }
            .width('100%')

            Slider({
              value: this.manualDurationMinutes,
              min: 5,
              max: 180,
              step: 5,
              style: SliderStyle.InSet
            })
              .blockColor(M3EColors.primary)
              .trackColor(M3EColors.surfaceContainerHighest)
              .selectedColor(M3EColors.primary)
              .onChange((val: number) => {
                this.manualDurationMinutes = Math.round(val);
              })

            // Intensity Selection
            Row({ space: 8 }) {
              this.IntensityChip('light', '轻松')
              this.IntensityChip('moderate', '中等')
              this.IntensityChip('vigorous', '较累')
            }

            Button(this.isSavingActivity ? '保存中...' : '记录运动')
              .width('100%')
              .height(40)
              .backgroundColor(M3EColors.primary)
              .fontColor(M3EColors.onPrimary)
              .borderRadius(M3EShapes.buttonRadius)
              .enabled(!this.isSavingActivity)
              .onClick(() => {
                this.saveManualActivity();
              })
          }
          .width('100%')
          .padding(14)
          .backgroundColor(M3EColors.surfaceContainerHigh)
          .borderRadius(M3EShapes.innerSectionRadius)
          .alignItems(HorizontalAlign.Start)
        }
      }
      .width('100%')
      .padding(16)
      .backgroundColor(M3EColors.surfaceContainerLow)
      .borderRadius(M3EShapes.cardRadius)
    }
    .width('100%')
    .alignItems(HorizontalAlign.Start)
  }

  @Builder
  IntensityChip(val: 'light' | 'moderate' | 'vigorous', label: string) {
    Text(label)
      .fontSize(M3ETypography.labelMedium)
      .fontColor(this.manualIntensity === val ? M3EColors.onSecondaryContainer : M3EColors.onSurfaceVariant)
      .padding({ left: 14, right: 14, top: 6, bottom: 6 })
      .backgroundColor(this.manualIntensity === val ? M3EColors.secondaryContainer : M3EColors.surfaceContainerHighest)
      .borderRadius(M3EShapes.chipRadius)
      .onClick(() => {
        this.manualIntensity = val;
      })
  }

  /* ---------------------------- 今日汇总 SECTION ----------------------------- */

  @Builder
  TodayOverviewSection() {
    Column({ space: 14 }) {
      this.SectionHeader('学校演示汇总', '学校示例统计，与个人加密记录分开', '总')

      Column({ space: 14 }) {
        Column({ space: 4 }) {
          Text('今日反馈')
            .fontSize(M3ETypography.titleLarge)
            .fontWeight(FontWeight.Bold)
            .fontColor(M3EColors.onSurface)

          Text('记录完成后，这里会自动更新。')
            .fontSize(M3ETypography.bodySmall)
            .fontColor(M3EColors.onSurfaceVariant)
        }
        .width('100%')
        .alignItems(HorizontalAlign.Start)

        // Calorie & Exercise Meters
        Row({ space: 12 }) {
          this.MetricBlock(
            `${Math.round(this.summary?.nutrition?.energyKcal ?? 0)} 千卡`,
            '摄入热量'
          )
          this.MetricBlock(
            `${this.summary?.activity?.totalMinutes ?? 0} 分钟`,
            '运动'
          )
        }
        .width('100%')

        // Macronutrient breakdown
        Row({ space: 10 }) {
          this.MetricBlock(
            `${roundOneDecimal(this.summary?.nutrition?.proteinG ?? 0)} 克`,
            '蛋白质'
          )
          this.MetricBlock(
            `${roundOneDecimal(this.summary?.nutrition?.fatG ?? 0)} 克`,
            '脂肪'
          )
          this.MetricBlock(
            `${roundOneDecimal(this.summary?.nutrition?.carbohydrateG ?? 0)} 克`,
            '碳水'
          )
        }
        .width('100%')

        // Exercise Progress Bar
        Column({ space: 6 }) {
          Row() {
            Text(`运动 ${this.summary?.activity?.totalMinutes ?? 0} / ${this.summary?.activity?.targetMinutes ?? 120} 分钟`)
              .fontSize(M3ETypography.labelMedium)
              .fontWeight(FontWeight.Medium)

            Blank()

            Text(this.summary?.activity?.targetReached ? '已达目标' : '今日进度')
              .fontSize(M3ETypography.labelSmall)
              .fontColor(this.summary?.activity?.targetReached ? M3EColors.primary : M3EColors.onSurfaceVariant)
          }
          .width('100%')

          Progress({
            value: this.summary?.activity?.totalMinutes ?? 0,
            total: this.summary?.activity?.targetMinutes ?? 120,
            type: ProgressType.Linear
          })
            .width('100%')
            .color(M3EColors.primary)
            .backgroundColor(M3EColors.surfaceContainerHighest)
        }
        .width('100%')

        // Evidence-Based Next Action Tip ("下一步建议")
        this.EvidenceBasedTipCard()
      }
      .width('100%')
      .padding(16)
      .backgroundColor(M3EColors.surfaceContainerLow)
      .borderRadius(M3EShapes.cardRadius)
    }
    .width('100%')
    .alignItems(HorizontalAlign.Start)
  }

  @Builder
  EvidenceBasedTipCard() {
    Column({ space: 6 }) {
      Row({ space: 6 }) {
        Text('★')
          .fontSize(14)
          .fontColor(M3EColors.primary)

        Text('下一步建议')
          .fontSize(M3ETypography.labelLarge)
          .fontWeight(FontWeight.Bold)
          .fontColor(M3EColors.onSurface)
      }

      Text(this.getEvidenceBasedSuggestion())
        .fontSize(M3ETypography.bodySmall)
        .fontColor(M3EColors.onSurfaceVariant)
        .lineHeight(18)
    }
    .width('100%')
    .padding(12)
    .backgroundColor(M3EColors.surfaceContainerHigh)
    .borderRadius(M3EShapes.innerSectionRadius)
    .alignItems(HorizontalAlign.Start)
  }

  getEvidenceBasedSuggestion(): string {
    if (!this.summary) return '今日记录还未加载，请稍后刷新。';
    const total = this.summary.activity.totalMinutes;
    const target = this.summary.activity.targetMinutes;
    if (total < target) return '可以根据体力与当天安排，分段进行散步、球类或其他喜欢的活动。记录不完整时，汇总可能低于实际活动量。';
    return '继续保持规律进餐、休息和喜欢的活动。每日参考热量仅用于记录对照，不代表个人饮食处方。';
  }

  /* ---------------------------- Helper Builders ------------------------------ */

  @Builder
  SectionHeader(title: string, subtitle: string, iconChar: string) {
    Row({ space: 10 }) {
      Text(iconChar)
        .fontSize(14)
        .fontWeight(FontWeight.Bold)
        .fontColor(M3EColors.onSecondaryContainer)
        .padding({ left: 10, right: 10, top: 6, bottom: 6 })
        .backgroundColor(M3EColors.secondaryContainer)
        .borderRadius(M3EShapes.chipRadius)

      Column({ space: 2 }) {
        Text(title)
          .fontSize(M3ETypography.titleLarge)
          .fontWeight(FontWeight.Medium)
          .fontColor(M3EColors.onSurface)

        Text(subtitle)
          .fontSize(M3ETypography.bodySmall)
          .fontColor(M3EColors.onSurfaceVariant)
      }
      .alignItems(HorizontalAlign.Start)
    }
    .width('100%')
    .alignItems(VerticalAlign.Center)
  }

  @Builder
  MetricBlock(val: string, label: string) {
    Column({ space: 2 }) {
      Text(val)
        .fontSize(M3ETypography.titleMedium)
        .fontWeight(FontWeight.Bold)
        .fontColor(M3EColors.onSurface)

      Text(label)
        .fontSize(M3ETypography.labelSmall)
        .fontColor(M3EColors.onSurfaceVariant)
    }
    .layoutWeight(1)
    .padding(10)
    .backgroundColor(M3EColors.surfaceContainerHigh)
    .borderRadius(M3EShapes.innerSectionRadius)
    .alignItems(HorizontalAlign.Start)
  }
}
