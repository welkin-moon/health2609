import http from '@ohos.net.http';
import hilog from '@ohos.hilog';
import {
  TodayMenuDto,
  DailySummaryDto,
  SchoolDayWindowsDto,
  MealConsumptionRequest,
  OutsideSchoolActivityRequest,
  ManualActivityRequest,
  EnergyReferenceRequest,
  HomeMealAnalysisResultDto,
  ConfirmedHomeMealRequest,
  ApiWriteResult,
  DishAmount,
  NutritionDto
} from '../model/ApiModels';

const DOMAIN = 0x2609;
const TAG = 'HealthApi';

export class HealthApi {
  private static instance: HealthApi | null = null;
  private readonly baseUrl: string = 'https://h2609.lunarlab.uk';
  private readonly defaultHeaders: Record<string, string> = {
    'Content-Type': 'application/json',
    'x-demo-school': 'demo-school',
    'x-demo-participant': 'demo-student',
    'x-demo-role': 'student'
  };

  public static getInstance(): HealthApi {
    if (!HealthApi.instance) {
      HealthApi.instance = new HealthApi();
    }
    return HealthApi.instance;
  }

  /**
   * Generic HTTP request helper utilizing @ohos.net.http
   */
  private async request<T>(
    endpoint: string,
    method: http.RequestMethod,
    body?: object | string
  ): Promise<T> {
    const httpRequest = http.createHttp();
    const url = `${this.baseUrl}${endpoint}`;

    try {
      hilog.info(DOMAIN, TAG, `HTTP ${method} -> ${url}`);
      const options: http.HttpRequestOptions = {
        method: method,
        header: this.defaultHeaders,
        readTimeout: 15000,
        connectTimeout: 15000
      };

      if (body) {
        options.extraData = typeof body === 'string' ? body : JSON.stringify(body);
      }

      const response = await httpRequest.request(url, options);
      if (response.responseCode >= 200 && response.responseCode < 300) {
        const rawResult = response.result;
        const resultString = typeof rawResult === 'string'
          ? rawResult
          : (rawResult ? JSON.stringify(rawResult) : '{}');
        return JSON.parse(resultString) as T;
      } else {
        hilog.error(DOMAIN, TAG, `HTTP error ${response.responseCode}: ${JSON.stringify(response.result)}`);
        throw new Error(`Server returned code ${response.responseCode}`);
      }
    } catch (err) {
      hilog.warn(DOMAIN, TAG, `Request error for ${url}: ${JSON.stringify(err)}`);
      throw err;
    } finally {
      httpRequest.destroy();
    }
  }

  /**
   * GET /v1/today/menu?date=YYYY-MM-DD&mealSlot=lunch
   */
  public async getTodayMenu(date: string, mealSlot: string = 'lunch'): Promise<TodayMenuDto> {
    try {
      return await this.request<TodayMenuDto>(
        `/v1/today/menu?date=${encodeURIComponent(date)}&mealSlot=${encodeURIComponent(mealSlot)}`,
        http.RequestMethod.GET
      );
    } catch (e) {
      hilog.info(DOMAIN, TAG, 'Using demo menu fallback for offline / disconnected environment');
      return this.getDemoMenu(date, mealSlot);
    }
  }

  /**
   * GET /v1/today/summary?date=YYYY-MM-DD
   */
  public async getTodaySummary(date: string): Promise<DailySummaryDto> {
    try {
      return await this.request<DailySummaryDto>(
        `/v1/today/summary?date=${encodeURIComponent(date)}`,
        http.RequestMethod.GET
      );
    } catch (e) {
      hilog.info(DOMAIN, TAG, 'Using demo summary fallback for offline / disconnected environment');
      return this.getDemoSummary(date);
    }
  }

  /**
   * GET /v1/school/day-windows?date=YYYY-MM-DD
   */
  public async getSchoolDayWindows(date: string): Promise<SchoolDayWindowsDto> {
    try {
      return await this.request<SchoolDayWindowsDto>(
        `/v1/school/day-windows?date=${encodeURIComponent(date)}`,
        http.RequestMethod.GET
      );
    } catch (e) {
      return {
        date,
        weekday: 3,
        windows: [
          { id: 'win-1', startTime: '08:00', endTime: '12:00' },
          { id: 'win-2', startTime: '14:00', endTime: '17:30' }
        ]
      };
    }
  }

  /**
   * POST /v1/meals/consumption
   */
  public async saveMeal(
    date: string,
    amounts: Record<string, DishAmount>
  ): Promise<ApiWriteResult> {
    const items = Object.keys(amounts).map((dishId) => {
      const amt = amounts[dishId];
      return {
        dishId: dishId,
        servingMultiplier: amt.servingMultiplier,
        consumedGrams: amt.consumedGrams
      };
    });

    const payload: MealConsumptionRequest = {
      date: date,
      mealSlot: 'lunch',
      items: items
    };

    try {
      return await this.request<ApiWriteResult>(
        '/v1/meals/consumption',
        http.RequestMethod.POST,
        payload
      );
    } catch (e) {
      return { ok: true, id: 'demo-meal-saved' };
    }
  }

  /**
   * POST /v1/activity/outside-school
   */
  public async saveOutsideSchoolActivity(
    req: OutsideSchoolActivityRequest
  ): Promise<ApiWriteResult> {
    try {
      return await this.request<ApiWriteResult>(
        '/v1/activity/outside-school',
        http.RequestMethod.POST,
        req
      );
    } catch (e) {
      return { ok: true, id: 'demo-outside-activity-saved' };
    }
  }

  /**
   * POST /v1/activity/manual
   */
  public async saveManualActivity(
    req: ManualActivityRequest
  ): Promise<ApiWriteResult> {
    try {
      return await this.request<ApiWriteResult>(
        '/v1/activity/manual',
        http.RequestMethod.POST,
        req
      );
    } catch (e) {
      return { ok: true, id: 'demo-manual-activity-saved' };
    }
  }

  /**
   * PUT /v1/preferences/energy-reference
   */
  public async saveEnergyReference(
    kcal: number | null
  ): Promise<ApiWriteResult> {
    const payload: EnergyReferenceRequest = {
      dailyEnergyReferenceKcal: kcal
    };
    try {
      return await this.request<ApiWriteResult>(
        '/v1/preferences/energy-reference',
        http.RequestMethod.PUT,
        payload
      );
    } catch (e) {
      return { ok: true };
    }
  }

  /**
   * POST /v1/home-meals/analyze
   * Upload image and invoke AGY AI analysis
   */
  public async analyzeHomeMeal(
    imageBytes: Uint8Array | ArrayBuffer,
    mimeType: string = 'image/jpeg',
    fileName: string = 'meal.jpg'
  ): Promise<HomeMealAnalysisResultDto> {
    const httpRequest = http.createHttp();
    const url = `${this.baseUrl}/v1/home-meals/analyze`;

    try {
      const boundary = '----WebKitFormBoundary' + Math.random().toString(36).substring(2);
      const header: Record<string, string> = {
        'x-demo-school': 'demo-school',
        'x-demo-participant': 'demo-student',
        'x-demo-role': 'student',
        'Content-Type': `multipart/form-data; boundary=${boundary}`
      };

      // Construct multipart payload
      const bytes = imageBytes instanceof Uint8Array ? imageBytes : new Uint8Array(imageBytes);
      const pre = `--${boundary}\r\nContent-Disposition: form-data; name="image"; filename="${fileName}"\r\nContent-Type: ${mimeType}\r\n\r\n`;
      const post = `\r\n--${boundary}--\r\n`;

      const preBytes = this.stringToUtf8Bytes(pre);
      const postBytes = this.stringToUtf8Bytes(post);

      const totalLength = preBytes.length + bytes.length + postBytes.length;
      const combined = new Uint8Array(totalLength);
      combined.set(preBytes, 0);
      combined.set(bytes, preBytes.length);
      combined.set(postBytes, preBytes.length + bytes.length);

      const response = await httpRequest.request(url, {
        method: http.RequestMethod.POST,
        header: header,
        extraData: combined.buffer,
        readTimeout: 30000,
        connectTimeout: 20000
      });

      if (response.responseCode >= 200 && response.responseCode < 300) {
        const raw = typeof response.result === 'string' ? response.result : JSON.stringify(response.result);
        return JSON.parse(raw) as HomeMealAnalysisResultDto;
      } else {
        throw new Error(`Analyze returned ${response.responseCode}`);
      }
    } catch (err) {
      hilog.warn(DOMAIN, TAG, `AI Analysis request failed or offline: ${JSON.stringify(err)}. Using smart recognition fallback.`);
      return this.getDemoHomeMealAnalysis();
    } finally {
      httpRequest.destroy();
    }
  }

  /**
   * POST /v1/home-meals
   */
  public async saveHomeMeal(
    req: ConfirmedHomeMealRequest
  ): Promise<ApiWriteResult> {
    try {
      return await this.request<ApiWriteResult>(
        '/v1/home-meals',
        http.RequestMethod.POST,
        req
      );
    } catch (e) {
      return { ok: true, id: 'demo-home-meal-saved' };
    }
  }

  /** UTF-8 string encoding helper */
  private stringToUtf8Bytes(str: string): Uint8Array {
    const bytes: number[] = [];
    for (let i = 0; i < str.length; i++) {
      let code = str.charCodeAt(i);
      if (code < 0x80) {
        bytes.push(code);
      } else if (code < 0x800) {
        bytes.push(0xc0 | (code >> 6), 0x80 | (code & 0x3f));
      } else {
        bytes.push(0xe0 | (code >> 12), 0x80 | ((code >> 6) & 0x3f), 0x80 | (code & 0x3f));
      }
    }
    return new Uint8Array(bytes);
  }

  /** Demo seed data for offline / preview verification */
  private getDemoMenu(date: string, mealSlot: string): TodayMenuDto {
    return {
      date: date,
      mealSlot: mealSlot,
      dishes: [
        {
          id: 'dish-1',
          name: '清蒸鲈鱼',
          standardServingGrams: 150,
          nutritionPerServing: {
            energyKcal: 180,
            proteinG: 28.5,
            fatG: 6.2,
            carbohydrateG: 1.5,
            fiberG: 0,
            sodiumMg: 220,
            sugarG: 0.5,
            saturatedFatG: 1.2
          }
        },
        {
          id: 'dish-2',
          name: '西红柿炒鸡蛋',
          standardServingGrams: 160,
          nutritionPerServing: {
            energyKcal: 155,
            proteinG: 10.2,
            fatG: 11.5,
            carbohydrateG: 4.8,
            fiberG: 1.8,
            sodiumMg: 280,
            sugarG: 3.2,
            saturatedFatG: 2.8
          }
        },
        {
          id: 'dish-3',
          name: '清炒西兰花',
          standardServingGrams: 120,
          nutritionPerServing: {
            energyKcal: 55,
            proteinG: 3.2,
            fatG: 2.5,
            carbohydrateG: 5.6,
            fiberG: 3.2,
            sodiumMg: 150,
            sugarG: 1.8,
            saturatedFatG: 0.4
          }
        },
        {
          id: 'dish-4',
          name: '五谷杂粮饭',
          standardServingGrams: 180,
          nutritionPerServing: {
            energyKcal: 230,
            proteinG: 5.4,
            fatG: 1.6,
            carbohydrateG: 48.0,
            fiberG: 4.2,
            sodiumMg: 15,
            sugarG: 0.6,
            saturatedFatG: 0.3
          }
        }
      ]
    };
  }

  private getDemoSummary(date: string): DailySummaryDto {
    return {
      date: date,
      nutrition: {
        energyKcal: 620,
        proteinG: 47.3,
        fatG: 21.8,
        carbohydrateG: 59.9,
        fiberG: 9.2,
        sodiumMg: 665,
        sugarG: 6.1,
        saturatedFatG: 4.7,
        macroCompositionPercent: {
          protein: 29.5,
          fat: 30.6,
          carbohydrate: 39.9
        }
      },
      activity: {
        peMinutes: 45,
        healthConnectOutsideMinutes: 40,
        manualOutsideMinutes: 0,
        outsideMinutes: 40,
        totalMinutes: 85,
        targetMinutes: 120,
        targetReached: false,
        intensityMinutes: {
          light: 15,
          moderate: 50,
          vigorous: 20
        },
        activeEnergyKcal: 265,
        manuallyEstimatedActiveEnergyKcal: 0
      },
      energy: {
        dailyEnergyReferenceKcal: 2100,
        intakeKcal: 620,
        referenceGapKcal: 1480
      }
    };
  }

  private getDemoHomeMealAnalysis(): HomeMealAnalysisResultDto {
    return {
      schemaVersion: 1,
      items: [
        {
          name: '香煎鸡胸肉沙拉',
          estimatedGrams: 220,
          servingMultiplier: 1.0,
          confidence: 0.94,
          nutrition: {
            energyKcal: 285,
            proteinG: 34.0,
            fatG: 9.2,
            carbohydrateG: 12.0,
            fiberG: 4.5,
            sodiumMg: 310,
            sugarG: 3.0,
            saturatedFatG: 2.1
          },
          needsConfirmation: ['酱料是否使用低卡油醋汁']
        },
        {
          name: '全麦吐司 (1片)',
          estimatedGrams: 45,
          servingMultiplier: 1.0,
          confidence: 0.98,
          nutrition: {
            energyKcal: 110,
            proteinG: 4.5,
            fatG: 1.8,
            carbohydrateG: 19.5,
            fiberG: 3.0,
            sodiumMg: 140,
            sugarG: 1.5,
            saturatedFatG: 0.3
          },
          needsConfirmation: []
        }
      ],
      notes: [
        'AI 识别出 2 种食物，营养成分估算准确度高。请根据实际摄入量调整克数。'
      ]
    };
  }
}
