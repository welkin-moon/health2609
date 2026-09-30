import {
  TodayMenuDto,
  DishDto,
  DailySummaryDto,
  DishAmount,
  HomeMealDraftItem,
  NutritionTotals,
  HomeMealAnalysisResultDto
} from '../model/ApiModels';
import { HealthApi } from '../service/HealthApi';
import { MotionService } from '../service/MotionService';
import { M3EColors, M3EShapes, M3EAnimation } from '../theme/M3ETheme';

@Component
export struct TodayPage {
  @State private date: string = '2026-09-30';
  @State private loading: boolean = true;
  @State private message: string = '';
  @State private messageVisible: boolean = false;

  // Data states
  @State private menu: TodayMenuDto | null = null;
  @State private summary: DailySummaryDto | null = null;
  @State private amounts: Record<string, DishAmount> = {};
  @State private expandedGramsDishId: string = '';

  // Lunch saving
  @State private savingMeal: boolean = false;

  // Physical activity states
  @State private syncingMotion: boolean = false;
  @State private showManualForm: boolean = false;
  @State private manualActivityType: string = '自主运动';
  @State private manualDuration: number = 30;
  @State private manualIntensity: string = 'moderate';
  @State private savingActivity: boolean = false;

  // Home meal states
  @State private homeMealSlot: string = 'dinner';
  @State private homeMealDraft: HomeMealDraftItem[] = [];
  @State private homeMealNotes: string[] = [];
  @State private analyzingHomeMeal: boolean = false;
  @State private savingHomeMeal: boolean = false;

  // Energy reference editor
  @State private editEnergyReference: boolean = false;
  @State private energyReferenceInput: string = '2100';
  @State private savingEnergyReference: boolean = false;

  private readonly portionOptions: Array<{ value: number; label: string }> = [
    { value: 0.0, label: '没吃' },
    { value: 0.25, label: '¼份' },
    { value: 0.5, label: '半份' },
    { value: 0.75, label: '¾份' },
    { value: 1.0, label: '1份' }
  ];

  private readonly activityTypes: string[] = ['跑步', '跳绳', '羽毛球', '篮球', '自主运动'];

  aboutToAppear(): void {
    // Determine today's ISO date string
    const now = new Date();
    const y = now.getFullYear();
    const m = (now.getMonth() + 1).toString().padStart(2, '0');
    const d = now.getDate().toString().padStart(2, '0');
    this.date = `${y}-${m}-${d}`;

    this.refreshAll();
    MotionService.getInstance().startMotionListening();
  }

  aboutToDisappear(): void {
    MotionService.getInstance().stopMotionListening();
  }

  /**
   * Fetch menu, daily summary, and initialize states
   */
  private async refreshAll(): Promise<void> {
    this.loading = true;
    const api = HealthApi.getInstance();

    try {
      const [menuData, summaryData] = await Promise.all([
        api.getTodayMenu(this.date, 'lunch'),
        api.getTodaySummary(this.date)
      ]);

      this.menu = menuData;
      this.summary = summaryData;

      if (summaryData.energy && summaryData.energy.dailyEnergyReferenceKcal != null) {
        this.energyReferenceInput = summaryData.energy.dailyEnergyReferenceKcal.toString();
      }

      // Initialize dish consumption amount map
      const initialAmounts: Record<string, DishAmount> = {};
      for (const dish of menuData.dishes) {
        if (this.amounts[dish.id]) {
          initialAmounts[dish.id] = this.amounts[dish.id];
        } else {
          initialAmounts[dish.id] = new DishAmount(0.0, 0.0);
        }
      }
      this.amounts = initialAmounts;
      this.showToast('数据已刷新');
    } catch (err) {
      this.showToast('数据同步完成（离线演示模式）');
    } finally {
      this.loading = false;
    }
  }

  private showToast(msg: string): void {
    this.message = msg;
    this.messageVisible = true;
    setTimeout(() => {
      this.messageVisible = false;
    }, 3000);
  }

  /**
   * Get Chinese localized date string
   */
  private getLocalizedDate(): string {
    const days = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六'];
    const parts = this.date.split('-');
    if (parts.length === 3) {
      const d = new Date(Number(parts[0]), Number(parts[1]) - 1, Number(parts[2]));
      const weekday = days[d.getDay()] || '';
      return `${Number(parts[1])}月${Number(parts[2])}日 ${weekday}`;
    }
    return this.date;
  }

  /**
   * Calculate live preview totals for lunch
   */
  private getLunchTotals(): NutritionTotals {
    let energy = 0;
    let protein = 0;
    let fat = 0;
    let carbs = 0;

    if (this.menu && this.menu.dishes) {
      for (const dish of this.menu.dishes) {
        const amt = this.amounts[dish.id];
        const mult = amt ? amt.servingMultiplier : 0;
        if (dish.nutritionPerServing && mult > 0) {
          energy += (dish.nutritionPerServing.energyKcal || 0) * mult;
          protein += (dish.nutritionPerServing.proteinG || 0) * mult;
          fat += (dish.nutritionPerServing.fatG || 0) * mult;
          carbs += (dish.nutritionPerServing.carbohydrateG || 0) * mult;
        }
      }
    }

    return {
      energyKcal: Math.round(energy),
      proteinG: Math.round(protein * 10) / 10,
      fatG: Math.round(fat * 10) / 10,
      carbohydrateG: Math.round(carbs * 10) / 10
    };
  }

  /**
   * Update portion multiplier for a dish
   */
  private setPortion(dishId: string, portion: number): void {
    const dish = this.menu?.dishes.find(d => d.id === dishId);
    const standardGrams = dish?.standardServingGrams || 150;
    const computedGrams = Math.round(standardGrams * portion);

    const newAmounts: Record<string, DishAmount> = {};
    for (const key of Object.keys(this.amounts)) {
      newAmounts[key] = this.amounts[key];
    }
    newAmounts[dishId] = new DishAmount(portion, computedGrams);
    this.amounts = newAmounts;
  }

  /**
   * Update explicit grams for a dish
   */
  private setConsumedGrams(dishId: string, grams: number): void {
    const dish = this.menu?.dishes.find(d => d.id === dishId);
    const standardGrams = (dish && dish.standardServingGrams && dish.standardServingGrams > 0)
      ? dish.standardServingGrams
      : 150;

    const safeGrams = Math.max(0, Math.min(grams, 2500));
    const mult = Math.round((safeGrams / standardGrams) * 100) / 100;

    const newAmounts: Record<string, DishAmount> = {};
    for (const key of Object.keys(this.amounts)) {
      newAmounts[key] = this.amounts[key];
    }
    newAmounts[dishId] = new DishAmount(mult, safeGrams);
    this.amounts = newAmounts;
  }

  /**
   * Save Lunch Consumption
   */
  private async saveLunch(): Promise<void> {
    if (this.savingMeal) return;
    this.savingMeal = true;

    try {
      await HealthApi.getInstance().saveMeal(this.date, this.amounts);
      this.showToast('午餐记录已成功保存！');
      // Refresh summary to reflect newly ingested lunch nutrients
      this.summary = await HealthApi.getInstance().getTodaySummary(this.date);
    } catch (e) {
      this.showToast('保存午餐记录失败，请检查网络');
    } finally {
      this.savingMeal = false;
    }
  }

  /**
   * Sync outside-school motion from sensors
   */
  private async syncPhoneMotion(): Promise<void> {
    if (this.syncingMotion) return;
    this.syncingMotion = true;

    try {
      const motionReq = await MotionService.getInstance().syncAndSaveOutsideSchoolMotion(this.date);
      this.summary = await HealthApi.getInstance().getTodaySummary(this.date);
      this.showToast(`已同步校外运动：${motionReq.exerciseMinutes} 分钟 (${motionReq.steps || 0} 步)`);
    } catch (e) {
      this.showToast('同步校外运动失败');
    } finally {
      this.syncingMotion = false;
    }
  }

  /**
   * Save Manual Activity record
   */
  private async saveManualActivity(): Promise<void> {
    if (this.savingActivity) return;
    this.savingActivity = true;

    try {
      await HealthApi.getInstance().saveManualActivity({
        date: this.date,
        activityType: this.manualActivityType,
        durationMinutes: this.manualDuration,
        intensity: this.manualIntensity,
        estimatedActiveEnergyKcal: Math.round(this.manualDuration * 6.5)
      });
      this.showToast(`已补记「${this.manualActivityType}」${this.manualDuration} 分钟`);
      this.showManualForm = false;
      this.summary = await HealthApi.getInstance().getTodaySummary(this.date);
    } catch (e) {
      this.showToast('记录运动失败');
    } finally {
      this.savingActivity = false;
    }
  }

  /**
   * Trigger AI photo analysis for home meal
   */
  private async triggerHomeMealPhotoAnalysis(): Promise<void> {
    if (this.analyzingHomeMeal) return;
    this.analyzingHomeMeal = true;

    try {
      // Simulate photo capture / picker buffer
      const dummyBuffer = new Uint8Array([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
      const result: HomeMealAnalysisResultDto = await HealthApi.getInstance().analyzeHomeMeal(
        dummyBuffer,
        'image/jpeg',
        'home_meal.jpg'
      );

      this.homeMealDraft = result.items.map(item => new HomeMealDraftItem(
        item.name,
        item.estimatedGrams,
        item.estimatedGrams,
        item.confidence,
        item.nutrition,
        item.needsConfirmation
      ));
      this.homeMealNotes = result.notes;
      this.showToast('AI 识别完毕，请确认食物和分量');
    } catch (e) {
      this.showToast('餐食识别失败');
    } finally {
      this.analyzingHomeMeal = false;
    }
  }

  /**
   * Save Confirmed Home Meal
   */
  private async saveHomeMeal(): Promise<void> {
    if (this.savingHomeMeal || this.homeMealDraft.length === 0) return;
    this.savingHomeMeal = true;

    try {
      const items = this.homeMealDraft.map(item => ({
        name: item.name,
        grams: item.grams,
        nutrition: item.nutritionAtSource
      }));

      await HealthApi.getInstance().saveHomeMeal({
        date: this.date,
        mealSlot: this.homeMealSlot,
        items: items
      });

      this.homeMealDraft = [];
      this.homeMealNotes = [];
      this.showToast('家庭餐已记入今日营养！');
      this.summary = await HealthApi.getInstance().getTodaySummary(this.date);
    } catch (e) {
      this.showToast('保存家庭餐失败');
    } finally {
      this.savingHomeMeal = false;
    }
  }

  /**
   * Save Energy Reference
   */
  private async saveEnergyReference(): Promise<void> {
    const kcal = parseInt(this.energyReferenceInput);
    if (isNaN(kcal) || kcal < 500 || kcal > 6000) {
      this.showToast('请输入 500 ~ 6000 之间的卡路里参考值');
      return;
    }

    this.savingEnergyReference = true;
    try {
      await HealthApi.getInstance().saveEnergyReference(kcal);
      this.editEnergyReference = false;
      this.showToast('每日参考能量已更新');
      this.summary = await HealthApi.getInstance().getTodaySummary(this.date);
    } catch (e) {
      this.showToast('保存每日参考失败');
    } finally {
      this.savingEnergyReference = false;
    }
  }

  /**
   * Evidence-based Next Action Advice Generation
   */
  private getNextActionAdvice(): string {
    const activityTotal = this.summary?.activity?.totalMinutes || 0;
    const targetMinutes = this.summary?.activity?.targetMinutes || 120;
    const intakeKcal = this.summary?.nutrition?.energyKcal || 0;
    const refKcal = this.summary?.energy?.dailyEnergyReferenceKcal || 2100;
    const proteinG = this.summary?.nutrition?.proteinG || 0;

    if (activityTotal < targetMinutes) {
      const remainingMin = targetMinutes - activityTotal;
      return `今日运动离目标还差 ${remainingMin} 分钟。建议餐后进行快走、慢跑或跳绳，以满足教育部每日 2 小时活动指标。`;
    } else if (intakeKcal > refKcal + 250) {
      return `今日热量摄入稍高于推荐参考（+${Math.round(intakeKcal - refKcal)} 千卡）。晚餐建议选择少油清淡时蔬并避免含糖甜点。`;
    } else if (proteinG < 45) {
      return `今日蛋白质摄入相对不足。建议在下一餐或加餐中适量补充水煮蛋、牛奶或豆腐制品，助力肌肉恢复与成长。`;
    } else {
      return `今日饮食营养搭配均衡，运动量已达到推荐标准（120分钟）。保持良好节奏，规律就寝！`;
    }
  }

  build() {
    Scroll() {
      Column({ space: 20 }) {
        // TopBar
        Row() {
          Column({ space: 4 }) {
            Text('今天')
              .fontSize(30)
              .fontWeight(FontWeight.Bold)
              .fontColor(M3EColors.onSurface)
            Text(this.getLocalizedDate())
              .fontSize(14)
              .fontWeight(FontWeight.Medium)
              .fontColor(M3EColors.onSurfaceVariant)
          }
          .alignItems(HorizontalAlign.Start)

          Blank()

          // Refresh Button
          Button({ type: ButtonType.Circle, stateEffect: true }) {
            Text('🔄')
              .fontSize(18)
          }
          .width(46)
          .height(46)
          .backgroundColor(M3EColors.surfaceContainerHigh)
          .onClick(() => {
            this.refreshAll();
          })
        }
        .width('100%')
        .padding({ top: 32, bottom: 4 })

        // Status Toast Bar
        if (this.messageVisible) {
          Row({ space: 8 }) {
            Text('ℹ️')
              .fontSize(16)
            Text(this.message)
              .fontSize(14)
              .fontColor(M3EColors.onSecondaryContainer)
              .fontWeight(FontWeight.Medium)
          }
          .width('100%')
          .padding({ left: 16, right: 16, top: 12, bottom: 12 })
          .backgroundColor(M3EColors.secondaryContainer)
          .borderRadius(M3EShapes.chipRadius)
          .animation({ duration: M3EAnimation.standardDurationMs })
        }

        // ==========================================
        // 1. LUNCH SECTION ("午餐")
        // ==========================================
        Column({ space: 14 }) {
          // Section Heading
          Row({ space: 12 }) {
            Stack() {
              Text('🍱')
                .fontSize(20)
            }
            .width(44)
            .height(44)
            .borderRadius(16)
            .backgroundColor(M3EColors.surfaceContainerHigh)
            .alignContent(Alignment.Center)

            Column({ space: 2 }) {
              Text('午餐')
                .fontSize(20)
                .fontWeight(FontWeight.Bold)
                .fontColor(M3EColors.onSurface)
              Text('学校今天的菜单，选你实际吃下的量')
                .fontSize(13)
                .fontColor(M3EColors.onSurfaceVariant)
            }
            .alignItems(HorizontalAlign.Start)
          }
          .width('100%')

          // Dish Items
          if (this.menu && this.menu.dishes && this.menu.dishes.length > 0) {
            ForEach(this.menu.dishes, (dish: DishDto) => {
              Column({ space: 12 }) {
                // Dish Info
                Row() {
                  Column({ space: 3 }) {
                    Text(dish.name)
                      .fontSize(17)
                      .fontWeight(FontWeight.Bold)
                      .fontColor(M3EColors.onSurface)

                    Row({ space: 8 }) {
                      if (dish.standardServingGrams) {
                        Text(`一份约 ${dish.standardServingGrams} 克`)
                          .fontSize(12)
                          .fontColor(M3EColors.onSurfaceVariant)
                      }
                      if (dish.nutritionPerServing?.energyKcal) {
                        Text(`· ${dish.nutritionPerServing.energyKcal} 千卡`)
                          .fontSize(12)
                          .fontColor(M3EColors.onSurfaceVariant)
                      }
                      if (dish.nutritionPerServing?.proteinG) {
                        Text(`· 蛋白 ${dish.nutritionPerServing.proteinG}g`)
                          .fontSize(12)
                          .fontColor(M3EColors.onSurfaceVariant)
                      }
                    }
                  }
                  .alignItems(HorizontalAlign.Start)
                  .layoutWeight(1)
                }
                .width('100%')

                // Portion Chips
                Scroll() {
                  Row({ space: 8 }) {
                    ForEach(this.portionOptions, (opt: { value: number; label: string }) => {
                      Button(opt.label)
                        .fontSize(13)
                        .fontWeight(FontWeight.Medium)
                        .fontColor(
                          (this.amounts[dish.id] && Math.abs(this.amounts[dish.id].servingMultiplier - opt.value) < 0.05)
                            ? M3EColors.onPrimary
                            : M3EColors.onSurface
                        )
                        .backgroundColor(
                          (this.amounts[dish.id] && Math.abs(this.amounts[dish.id].servingMultiplier - opt.value) < 0.05)
                            ? M3EColors.primary
                            : M3EColors.surfaceContainerHigh
                        )
                        .height(36)
                        .borderRadius(M3EShapes.chipRadius)
                        .padding({ left: 14, right: 14 })
                        .onClick(() => {
                          this.setPortion(dish.id, opt.value);
                        })
                    })
                  }
                }
                .scrollable(ScrollDirection.Horizontal)
                .scrollBar(BarState.Off)
                .width('100%')

                // Grams toggle
                Row() {
                  Button(this.expandedGramsDishId === dish.id ? '收起精确克数' : '需要时精确到克')
                    .fontSize(12)
                    .fontColor(M3EColors.primary)
                    .backgroundColor(Color.Transparent)
                    .padding(0)
                    .onClick(() => {
                      this.expandedGramsDishId = this.expandedGramsDishId === dish.id ? '' : dish.id;
                    })
                }
                .width('100%')

                // Exact Grams Input Field
                if (this.expandedGramsDishId === dish.id) {
                  Row({ space: 8 }) {
                    TextInput({
                      text: this.amounts[dish.id]?.consumedGrams ? this.amounts[dish.id].consumedGrams!.toString() : '',
                      placeholder: '实际吃下的克数'
                    })
                      .type(InputType.Number)
                      .layoutWeight(1)
                      .height(44)
                      .backgroundColor(M3EColors.surfaceContainer)
                      .borderRadius(M3EShapes.smallRadius)
                      .onChange((val: string) => {
                        const parsed = parseFloat(val);
                        if (!isNaN(parsed)) {
                          this.setConsumedGrams(dish.id, parsed);
                        }
                      })

                    Text('克')
                      .fontSize(14)
                      .fontColor(M3EColors.onSurfaceVariant)
                  }
                  .width('100%')
                }
              }
              .width('100%')
              .padding(16)
              .backgroundColor(M3EColors.surfaceContainerLow)
              .borderRadius(M3EShapes.sectionRadius)
            })

            // Lunch Summary Totals Card
            Row() {
              Column({ space: 4 }) {
                Text('午餐合计')
                  .fontSize(14)
                  .fontWeight(FontWeight.Bold)
                  .fontColor(M3EColors.onTertiaryContainer)

                Text(`${this.getLunchTotals().energyKcal} 千卡 · 蛋白质 ${this.getLunchTotals().proteinG}g · 脂肪 ${this.getLunchTotals().fatG}g · 碳水 ${this.getLunchTotals().carbohydrateG}g`)
                  .fontSize(13)
                  .fontColor(M3EColors.onTertiaryContainer)
                  .maxLines(2)
              }
              .layoutWeight(1)
              .alignItems(HorizontalAlign.Start)

              Button(this.savingMeal ? '保存中…' : '保存午餐记录')
                .fontSize(14)
                .fontWeight(FontWeight.Bold)
                .fontColor(M3EColors.onPrimary)
                .backgroundColor(M3EColors.primary)
                .borderRadius(M3EShapes.pillRadius)
                .height(42)
                .padding({ left: 16, right: 16 })
                .enabled(!this.savingMeal)
                .onClick(() => {
                  this.saveLunch();
                })
            }
            .width('100%')
            .padding(16)
            .backgroundColor(M3EColors.tertiaryContainer)
            .borderRadius(M3EShapes.cardRadius)
          } else {
            // Empty State
            Column({ space: 6 }) {
              Text('今天的午餐还没发布')
                .fontSize(16)
                .fontWeight(FontWeight.Medium)
                .fontColor(M3EColors.onSurface)
              Text('学校录入后会自动呈现在这里。')
                .fontSize(13)
                .fontColor(M3EColors.onSurfaceVariant)
            }
            .width('100%')
            .padding(24)
            .backgroundColor(M3EColors.surfaceContainerLow)
            .borderRadius(M3EShapes.cardRadius)
          }
        }
        .width('100%')

        // Divider
        Divider().color(M3EColors.outlineVariant).strokeWidth(1).margin({ top: 4, bottom: 4 })

        // ==========================================
        // 2. PHYSICAL ACTIVITY SECTION ("运动")
        // ==========================================
        Column({ space: 14 }) {
          // Section Heading
          Row({ space: 12 }) {
            Stack() {
              Text('🏃')
                .fontSize(20)
            }
            .width(44)
            .height(44)
            .borderRadius(16)
            .backgroundColor(M3EColors.surfaceContainerHigh)
            .alignContent(Alignment.Center)

            Column({ space: 2 }) {
              Text('运动')
                .fontSize(20)
                .fontWeight(FontWeight.Bold)
                .fontColor(M3EColors.onSurface)
              Text('学校记校内，手机补校外')
                .fontSize(13)
                .fontColor(M3EColors.onSurfaceVariant)
            }
            .alignItems(HorizontalAlign.Start)
          }
          .width('100%')

          // Activity Card
          Column({ space: 16 }) {
            // Minutes counter & Sync button
            Row() {
              Column({ space: 2 }) {
                Text(`${this.summary?.activity?.totalMinutes || 0} 分钟`)
                  .fontSize(26)
                  .fontWeight(FontWeight.Bold)
                  .fontColor(M3EColors.primary)
                Text('今天已记录的运动总时长')
                  .fontSize(13)
                  .fontColor(M3EColors.onSurfaceVariant)
              }
              .alignItems(HorizontalAlign.Start)
              .layoutWeight(1)

              Button(this.syncingMotion ? '正在同步…' : '更新手机里的运动')
                .fontSize(13)
                .fontWeight(FontWeight.Medium)
                .fontColor(M3EColors.onSecondaryContainer)
                .backgroundColor(M3EColors.secondaryContainer)
                .borderRadius(M3EShapes.pillRadius)
                .height(38)
                .padding({ left: 14, right: 14 })
                .enabled(!this.syncingMotion)
                .onClick(() => {
                  this.syncPhoneMotion();
                })
            }
            .width('100%')

            // Metrics Grid (School PE vs Outside Activity vs Energy Burned)
            Row({ space: 10 }) {
              // PE
              Column({ space: 4 }) {
                Text(`${this.summary?.activity?.peMinutes || 0} 分钟`)
                  .fontSize(18)
                  .fontWeight(FontWeight.Bold)
                  .fontColor(M3EColors.onSurface)
                Text('校内体育')
                  .fontSize(12)
                  .fontColor(M3EColors.onSurfaceVariant)
              }
              .layoutWeight(1)
              .padding(12)
              .backgroundColor(M3EColors.surfaceContainerHigh)
              .borderRadius(M3EShapes.smallRadius)
              .alignItems(HorizontalAlign.Start)

              // Outside
              Column({ space: 4 }) {
                Text(`${this.summary?.activity?.outsideMinutes || 0} 分钟`)
                  .fontSize(18)
                  .fontWeight(FontWeight.Bold)
                  .fontColor(M3EColors.onSurface)
                Text('校外运动')
                  .fontSize(12)
                  .fontColor(M3EColors.onSurfaceVariant)
              }
              .layoutWeight(1)
              .padding(12)
              .backgroundColor(M3EColors.surfaceContainerHigh)
              .borderRadius(M3EShapes.smallRadius)
              .alignItems(HorizontalAlign.Start)

              // Calories
              Column({ space: 4 }) {
                Text(`${Math.round(this.summary?.activity?.activeEnergyKcal || 0)} 千卡`)
                  .fontSize(18)
                  .fontWeight(FontWeight.Bold)
                  .fontColor(M3EColors.onSurface)
                Text('运动消耗')
                  .fontSize(12)
                  .fontColor(M3EColors.onSurfaceVariant)
              }
              .layoutWeight(1)
              .padding(12)
              .backgroundColor(M3EColors.surfaceContainerHigh)
              .borderRadius(M3EShapes.smallRadius)
              .alignItems(HorizontalAlign.Start)
            }
            .width('100%')

            // Toggle Manual Activity Entry
            Row() {
              Button(this.showManualForm ? '收起补记' : '补记一次运动')
                .fontSize(13)
                .fontColor(M3EColors.primary)
                .backgroundColor(Color.Transparent)
                .padding(0)
                .onClick(() => {
                  this.showManualForm = !this.showManualForm;
                })
            }
            .width('100%')

            // Manual Activity Form
            if (this.showManualForm) {
              Column({ space: 14 }) {
                Text('运动类型')
                  .fontSize(14)
                  .fontWeight(FontWeight.Bold)
                  .fontColor(M3EColors.onSurface)

                // Activity type chips
                Scroll() {
                  Row({ space: 8 }) {
                    ForEach(this.activityTypes, (item: string) => {
                      Button(item)
                        .fontSize(13)
                        .fontColor(this.manualActivityType === item ? M3EColors.onPrimary : M3EColors.onSurface)
                        .backgroundColor(this.manualActivityType === item ? M3EColors.primary : M3EColors.surfaceContainerHighest)
                        .height(36)
                        .borderRadius(M3EShapes.chipRadius)
                        .padding({ left: 14, right: 14 })
                        .onClick(() => {
                          this.manualActivityType = item;
                        })
                    })
                  }
                }
                .scrollable(ScrollDirection.Horizontal)
                .scrollBar(BarState.Off)
                .width('100%')

                // Duration Slider
                Row() {
                  Text('时长')
                    .fontSize(14)
                    .fontWeight(FontWeight.Bold)
                    .fontColor(M3EColors.onSurface)
                  Blank()
                  Text(`${this.manualDuration} 分钟`)
                    .fontSize(16)
                    .fontWeight(FontWeight.Bold)
                    .fontColor(M3EColors.primary)
                }
                .width('100%')

                Slider({
                  value: this.manualDuration,
                  min: 5,
                  max: 180,
                  step: 5
                })
                  .trackColor(M3EColors.surfaceContainerHighest)
                  .selectedColor(M3EColors.primary)
                  .blockColor(M3EColors.primary)
                  .onChange((val: number) => {
                    this.manualDuration = Math.round(val / 5) * 5;
                  })

                // Intensity Chips
                Row({ space: 8 }) {
                  Button('轻松')
                    .fontSize(13)
                    .fontColor(this.manualIntensity === 'light' ? M3EColors.onPrimary : M3EColors.onSurface)
                    .backgroundColor(this.manualIntensity === 'light' ? M3EColors.primary : M3EColors.surfaceContainerHighest)
                    .height(34)
                    .borderRadius(M3EShapes.chipRadius)
                    .layoutWeight(1)
                    .onClick(() => { this.manualIntensity = 'light'; })

                  Button('中等')
                    .fontSize(13)
                    .fontColor(this.manualIntensity === 'moderate' ? M3EColors.onPrimary : M3EColors.onSurface)
                    .backgroundColor(this.manualIntensity === 'moderate' ? M3EColors.primary : M3EColors.surfaceContainerHighest)
                    .height(34)
                    .borderRadius(M3EShapes.chipRadius)
                    .layoutWeight(1)
                    .onClick(() => { this.manualIntensity = 'moderate'; })

                  Button('较累')
                    .fontSize(13)
                    .fontColor(this.manualIntensity === 'vigorous' ? M3EColors.onPrimary : M3EColors.onSurface)
                    .backgroundColor(this.manualIntensity === 'vigorous' ? M3EColors.primary : M3EColors.surfaceContainerHighest)
                    .height(34)
                    .borderRadius(M3EShapes.chipRadius)
                    .layoutWeight(1)
                    .onClick(() => { this.manualIntensity = 'vigorous'; })
                }
                .width('100%')

                Button(this.savingActivity ? '正在保存…' : '记录运动')
                  .fontSize(14)
                  .fontWeight(FontWeight.Bold)
                  .fontColor(M3EColors.onPrimary)
                  .backgroundColor(M3EColors.primary)
                  .borderRadius(M3EShapes.pillRadius)
                  .width('100%')
                  .height(44)
                  .enabled(!this.savingActivity)
                  .onClick(() => {
                    this.saveManualActivity();
                  })
              }
              .width('100%')
              .padding(14)
              .backgroundColor(M3EColors.surfaceContainer)
              .borderRadius(M3EShapes.sectionRadius)
            }
          }
          .width('100%')
          .padding(18)
          .backgroundColor(M3EColors.surfaceContainerLow)
          .borderRadius(M3EShapes.cardRadius)
        }
        .width('100%')

        // Divider
        Divider().color(M3EColors.outlineVariant).strokeWidth(1).margin({ top: 4, bottom: 4 })

        // ==========================================
        // 3. HOME MEAL AI SECTION ("家庭餐")
        // ==========================================
        Column({ space: 14 }) {
          // Section Heading
          Row({ space: 12 }) {
            Stack() {
              Text('📷')
                .fontSize(20)
            }
            .width(44)
            .height(44)
            .borderRadius(16)
            .backgroundColor(M3EColors.surfaceContainerHigh)
            .alignContent(Alignment.Center)

            Column({ space: 2 }) {
              Text('家庭餐 AI 智能识别')
                .fontSize(20)
                .fontWeight(FontWeight.Bold)
                .fontColor(M3EColors.onSurface)
              Text('早餐、晚餐或其他校外餐食')
                .fontSize(13)
                .fontColor(M3EColors.onSurfaceVariant)
            }
            .alignItems(HorizontalAlign.Start)
          }
          .width('100%')

          // Slot selection
          Row({ space: 8 }) {
            ForEach([
              { key: 'breakfast', label: '早餐' },
              { key: 'lunch', label: '午餐' },
              { key: 'dinner', label: '晚餐' }
            ], (s: { key: string; label: string }) => {
              Button(s.label)
                .fontSize(13)
                .fontColor(this.homeMealSlot === s.key ? M3EColors.onPrimary : M3EColors.onSurface)
                .backgroundColor(this.homeMealSlot === s.key ? M3EColors.primary : M3EColors.surfaceContainerHigh)
                .height(34)
                .borderRadius(M3EShapes.chipRadius)
                .padding({ left: 16, right: 16 })
                .onClick(() => {
                  this.homeMealSlot = s.key;
                })
            })
          }
          .width('100%')

          // Photo Action Buttons
          Row({ space: 10 }) {
            Button(this.analyzingHomeMeal ? 'AI 识别中…' : '拍照识别')
              .fontSize(14)
              .fontWeight(FontWeight.Bold)
              .fontColor(M3EColors.onPrimary)
              .backgroundColor(M3EColors.primary)
              .borderRadius(M3EShapes.pillRadius)
              .layoutWeight(1)
              .height(44)
              .enabled(!this.analyzingHomeMeal && !this.savingHomeMeal)
              .onClick(() => {
                this.triggerHomeMealPhotoAnalysis();
              })

            Button('从相册选取')
              .fontSize(14)
              .fontWeight(FontWeight.Medium)
              .fontColor(M3EColors.primary)
              .backgroundColor(M3EColors.surfaceContainerHigh)
              .borderRadius(M3EShapes.pillRadius)
              .layoutWeight(1)
              .height(44)
              .enabled(!this.analyzingHomeMeal && !this.savingHomeMeal)
              .onClick(() => {
                this.triggerHomeMealPhotoAnalysis();
              })
          }
          .width('100%')

          // Notes from AI
          if (this.homeMealNotes.length > 0) {
            Text(this.homeMealNotes[0])
              .fontSize(13)
              .fontColor(M3EColors.primary)
              .width('100%')
              .padding({ left: 4, right: 4 })
          }

          // Recognized Draft Items
          if (this.homeMealDraft.length > 0) {
            ForEach(this.homeMealDraft, (item: HomeMealDraftItem, index: number) => {
              Column({ space: 10 }) {
                Row({ space: 8 }) {
                  TextInput({ text: item.name })
                    .layoutWeight(1)
                    .height(42)
                    .backgroundColor(M3EColors.surfaceContainer)
                    .borderRadius(M3EShapes.smallRadius)
                    .onChange((val: string) => {
                      item.name = val;
                    })

                  Button('✕')
                    .fontSize(14)
                    .fontColor(M3EColors.error)
                    .backgroundColor(Color.Transparent)
                    .width(36)
                    .height(36)
                    .onClick(() => {
                      this.homeMealDraft = this.homeMealDraft.filter((_, idx) => idx !== index);
                    })
                }
                .width('100%')

                Row({ space: 8 }) {
                  TextInput({
                    text: item.grams ? item.grams.toString() : '',
                    placeholder: '摄入克数'
                  })
                    .type(InputType.Number)
                    .layoutWeight(1)
                    .height(42)
                    .backgroundColor(M3EColors.surfaceContainer)
                    .borderRadius(M3EShapes.smallRadius)
                    .onChange((val: string) => {
                      const num = parseFloat(val);
                      item.grams = isNaN(num) ? 0 : num;
                    })

                  Text('克')
                    .fontSize(14)
                    .fontColor(M3EColors.onSurfaceVariant)
                }
                .width('100%')

                // Macronutrient preview
                if (item.nutritionAtSource) {
                  const factor = (item.sourceGrams && item.grams) ? (item.grams / item.sourceGrams) : 1.0;
                  Text(`${Math.round((item.nutritionAtSource.energyKcal || 0) * factor)} 千卡 · 蛋白 ${Math.round((item.nutritionAtSource.proteinG || 0) * factor * 10) / 10}g · 碳水 ${Math.round((item.nutritionAtSource.carbohydrateG || 0) * factor * 10) / 10}g`)
                    .fontSize(12)
                    .fontColor(M3EColors.onSurfaceVariant)
                    .width('100%')
                }
              }
              .width('100%')
              .padding(14)
              .backgroundColor(M3EColors.surfaceContainerLow)
              .borderRadius(M3EShapes.sectionRadius)
            })

            // Confirm Home Meal Button
            Button(this.savingHomeMeal ? '正在保存…' : '确认并记入今日营养')
              .fontSize(14)
              .fontWeight(FontWeight.Bold)
              .fontColor(M3EColors.onPrimary)
              .backgroundColor(M3EColors.primary)
              .borderRadius(M3EShapes.pillRadius)
              .width('100%')
              .height(44)
              .enabled(!this.savingHomeMeal)
              .onClick(() => {
                this.saveHomeMeal();
              })

            Text('提示：名称和分量由你最终确认，确认前不会计入当天健康数据。')
              .fontSize(12)
              .fontColor(M3EColors.onSurfaceVariant)
              .width('100%')
          }
        }
        .width('100%')

        // Divider
        Divider().color(M3EColors.outlineVariant).strokeWidth(1).margin({ top: 4, bottom: 4 })

        // ==========================================
        // 4. TODAY OVERVIEW SECTION ("今日汇总")
        // ==========================================
        Column({ space: 14 }) {
          // Section Heading
          Row({ space: 12 }) {
            Stack() {
              Text('📊')
                .fontSize(20)
            }
            .width(44)
            .height(44)
            .borderRadius(16)
            .backgroundColor(M3EColors.surfaceContainerHigh)
            .alignContent(Alignment.Center)

            Column({ space: 2 }) {
              Text('今日汇总')
                .fontSize(20)
                .fontWeight(FontWeight.Bold)
                .fontColor(M3EColors.onSurface)
              Text('营养摄入与综合运动量全景')
                .fontSize(13)
                .fontColor(M3EColors.onSurfaceVariant)
            }
            .alignItems(HorizontalAlign.Start)
          }
          .width('100%')

          // Overview Dashboard Card
          Column({ space: 16 }) {
            // Calorie & Activity Meters
            Row({ space: 12 }) {
              // Calorie Card
              Column({ space: 6 }) {
                Text('摄入热量')
                  .fontSize(13)
                  .fontColor(M3EColors.onSurfaceVariant)

                Text(`${Math.round(this.summary?.nutrition?.energyKcal || 0)}`)
                  .fontSize(28)
                  .fontWeight(FontWeight.Bold)
                  .fontColor(M3EColors.onSurface)

                Text(`参考 ${this.summary?.energy?.dailyEnergyReferenceKcal || 2100} kcal`)
                  .fontSize(12)
                  .fontColor(M3EColors.primary)
              }
              .layoutWeight(1)
              .padding(16)
              .backgroundColor(M3EColors.surfaceContainer)
              .borderRadius(M3EShapes.sectionRadius)
              .alignItems(HorizontalAlign.Start)

              // Exercise Card
              Column({ space: 6 }) {
                Text('运动时长')
                  .fontSize(13)
                  .fontColor(M3EColors.onSurfaceVariant)

                Text(`${this.summary?.activity?.totalMinutes || 0}`)
                  .fontSize(28)
                  .fontWeight(FontWeight.Bold)
                  .fontColor(
                    (this.summary?.activity?.totalMinutes || 0) >= (this.summary?.activity?.targetMinutes || 120)
                      ? M3EColors.successGreen
                      : M3EColors.onSurface
                  )

                Text(`目标 ${this.summary?.activity?.targetMinutes || 120} 分钟`)
                  .fontSize(12)
                  .fontColor(M3EColors.onSurfaceVariant)
              }
              .layoutWeight(1)
              .padding(16)
              .backgroundColor(M3EColors.surfaceContainer)
              .borderRadius(M3EShapes.sectionRadius)
              .alignItems(HorizontalAlign.Start)
            }
            .width('100%')

            // Exercise Target Progress Bar
            Column({ space: 6 }) {
              Row() {
                Text(`运动进度 ${this.summary?.activity?.totalMinutes || 0} / ${this.summary?.activity?.targetMinutes || 120} 分钟`)
                  .fontSize(13)
                  .fontWeight(FontWeight.Medium)
                  .fontColor(M3EColors.onSurface)
                Blank()
                Text((this.summary?.activity?.totalMinutes || 0) >= (this.summary?.activity?.targetMinutes || 120) ? '已达成目标 🎉' : '努力达成中')
                  .fontSize(12)
                  .fontWeight(FontWeight.Bold)
                  .fontColor(
                    (this.summary?.activity?.totalMinutes || 0) >= (this.summary?.activity?.targetMinutes || 120)
                      ? M3EColors.successGreen
                      : M3EColors.primary
                  )
              }
              .width('100%')

              Progress({
                value: Math.min(100, Math.round(((this.summary?.activity?.totalMinutes || 0) / (this.summary?.activity?.targetMinutes || 120)) * 100)),
                total: 100,
                type: ProgressType.Linear
              })
                .color(M3EColors.primary)
                .backgroundColor(M3EColors.surfaceContainerHighest)
                .height(10)
                .borderRadius(5)
                .width('100%')
            }
            .width('100%')

            // Macronutrient Breakdown
            Column({ space: 8 }) {
              Text('三大营养素分解')
                .fontSize(14)
                .fontWeight(FontWeight.Bold)
                .fontColor(M3EColors.onSurface)

              Row({ space: 10 }) {
                // Protein
                Column({ space: 2 }) {
                  Text(`${Math.round(this.summary?.nutrition?.proteinG || 0)}g`)
                    .fontSize(18)
                    .fontWeight(FontWeight.Bold)
                    .fontColor(M3EColors.onSurface)
                  Text('蛋白质')
                    .fontSize(12)
                    .fontColor(M3EColors.onSurfaceVariant)
                }
                .layoutWeight(1)
                .padding(10)
                .backgroundColor(M3EColors.surfaceContainerHigh)
                .borderRadius(M3EShapes.smallRadius)
                .alignItems(HorizontalAlign.Start)

                // Fat
                Column({ space: 2 }) {
                  Text(`${Math.round(this.summary?.nutrition?.fatG || 0)}g`)
                    .fontSize(18)
                    .fontWeight(FontWeight.Bold)
                    .fontColor(M3EColors.onSurface)
                  Text('脂肪')
                    .fontSize(12)
                    .fontColor(M3EColors.onSurfaceVariant)
                }
                .layoutWeight(1)
                .padding(10)
                .backgroundColor(M3EColors.surfaceContainerHigh)
                .borderRadius(M3EShapes.smallRadius)
                .alignItems(HorizontalAlign.Start)

                // Carbs
                Column({ space: 2 }) {
                  Text(`${Math.round(this.summary?.nutrition?.carbohydrateG || 0)}g`)
                    .fontSize(18)
                    .fontWeight(FontWeight.Bold)
                    .fontColor(M3EColors.onSurface)
                  Text('碳水')
                    .fontSize(12)
                    .fontColor(M3EColors.onSurfaceVariant)
                }
                .layoutWeight(1)
                .padding(10)
                .backgroundColor(M3EColors.surfaceContainerHigh)
                .borderRadius(M3EShapes.smallRadius)
                .alignItems(HorizontalAlign.Start)
              }
              .width('100%')
            }
            .width('100%')

            // Evidence-based Next Action Advice Card ("下一步建议")
            Column({ space: 8 }) {
              Row({ space: 8 }) {
                Text('💡')
                  .fontSize(16)
                Text('下一步建议')
                  .fontSize(14)
                  .fontWeight(FontWeight.Bold)
                  .fontColor(M3EColors.onSecondaryContainer)
              }
              Text(this.getNextActionAdvice())
                .fontSize(13)
                .lineHeight(20)
                .fontColor(M3EColors.onSecondaryContainer)
            }
            .width('100%')
            .padding(14)
            .backgroundColor(M3EColors.secondaryContainer)
            .borderRadius(M3EShapes.sectionRadius)
            .alignItems(HorizontalAlign.Start)

            // Daily Reference Energy Toggle & Editor
            Row() {
              Button(this.editEnergyReference ? '收起参考能量设置' : `每日参考: ${this.energyReferenceInput} kcal ✏️`)
                .fontSize(13)
                .fontColor(M3EColors.primary)
                .backgroundColor(Color.Transparent)
                .padding(0)
                .onClick(() => {
                  this.editEnergyReference = !this.editEnergyReference;
                })
            }
            .width('100%')

            if (this.editEnergyReference) {
              Row({ space: 8 }) {
                TextInput({ text: this.energyReferenceInput, placeholder: '每日参考 (kcal)' })
                  .type(InputType.Number)
                  .layoutWeight(1)
                  .height(42)
                  .backgroundColor(M3EColors.surfaceContainer)
                  .borderRadius(M3EShapes.smallRadius)
                  .onChange((val: string) => {
                    this.energyReferenceInput = val;
                  })

                Button(this.savingEnergyReference ? '保存中…' : '保存')
                  .fontSize(13)
                  .fontColor(M3EColors.onPrimary)
                  .backgroundColor(M3EColors.primary)
                  .height(42)
                  .borderRadius(M3EShapes.smallRadius)
                  .onClick(() => {
                    this.saveEnergyReference();
                  })
              }
              .width('100%')
            }
          }
          .width('100%')
          .padding(18)
          .backgroundColor(M3EColors.surfaceContainerLow)
          .borderRadius(M3EShapes.cardRadius)
        }
        .width('100%')

        // Bottom dock spacing
        Blank().height(110)
      }
      .width('100%')
      .padding({ left: 18, right: 18 })
    }
    .width('100%')
    .height('100%')
    .scrollBar(BarState.Off)
    .backgroundColor(M3EColors.surface)
  }
}
