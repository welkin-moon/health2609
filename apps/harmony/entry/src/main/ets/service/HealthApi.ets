/**
 * HealthApi HTTP Service for OpenHarmony & Kaihong OS
 * Uses @ohos.net.http with tenant headers and offline resilience
 */

import http from '@ohos.net.http';
import hilog from '@ohos.hilog';
import photoAccessHelper from '@ohos.file.photoAccessHelper';
import fs from '@ohos.file.fs';
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
  PendingOfflineRecord
} from '../model/ApiModels';
import { OfflineCacheService } from './OfflineCacheService';

const TAG = 'HealthApi';
const DOMAIN = 0x2609;

class HttpStatusError extends Error {
  constructor(code: number) { super(`服务器拒绝请求 (${code})，请检查登录或输入`); }
}

export class HealthApi {
  private syncing: boolean = false;
  private baseUrl: string = 'https://h2609.lunarlab.uk';
  private defaultHeaders: Record<string, string> = {
    'Content-Type': 'application/json',
    'x-demo-school': 'demo-school',
    'x-demo-participant': 'demo-student',
    'x-demo-role': 'student'
  };

  private offlineCache: OfflineCacheService = OfflineCacheService.getInstance();

  constructor(baseUrl?: string) {
    if (baseUrl) {
      this.baseUrl = baseUrl.replace(/\/+$/, '');
    }
  }

  private async requestJson<T>(
    endpoint: string,
    method: http.RequestMethod,
    extraHeaders: Record<string, string> = {},
    body?: string
  ): Promise<T> {
    const httpRequest = http.createHttp();
    const url = `${this.baseUrl}/${endpoint.replace(/^\/+/, '')}`;

    const headers: Record<string, string> = JSON.parse(JSON.stringify(this.defaultHeaders)) as Record<string, string>;
    for (const key of Object.keys(extraHeaders)) headers[key] = extraHeaders[key];

    try {
      hilog.info(DOMAIN, TAG, 'Request [%{public}s] %{public}s', method, url);
      const response = await httpRequest.request(url, {
        method: method,
        header: headers,
        extraData: body,
        readTimeout: 30000,
        connectTimeout: 15000
      });

      if (response.responseCode >= 200 && response.responseCode < 300) {
        const resultString = typeof response.result === 'string'
          ? response.result
          : JSON.stringify(response.result);
        return JSON.parse(resultString) as T;
      } else {
        throw new HttpStatusError(response.responseCode);
      }
    } finally {
      httpRequest.destroy();
    }
  }

  /* -------------------------------------------------------------------------- */
  /*                               API Methods                                  */
  /* -------------------------------------------------------------------------- */

  /**
   * Fetches today's lunch menu with offline cache fallback
   */
  public async getTodayMenu(date: string, mealSlot: string = 'lunch'): Promise<TodayMenuDto> {
    try {
      const data = await this.requestJson<TodayMenuDto>(
        `v1/today/menu?date=${encodeURIComponent(date)}&mealSlot=${encodeURIComponent(mealSlot)}`,
        http.RequestMethod.GET
      );
      await this.offlineCache.saveCachedMenu(date, data);
      return data;
    } catch (err) {
      hilog.warn(DOMAIN, TAG, 'Failed to fetch menu from network, checking cache: %{public}s', JSON.stringify(err));
      const cached = await this.offlineCache.getCachedMenu(date, mealSlot);
      if (cached) {
        return cached;
      }
      throw new Error('菜单暂不可用，请联网后重试');
    }
  }

  /**
   * Fetches today's student summary with offline cache fallback
   */
  public async getTodaySummary(date: string): Promise<DailySummaryDto> {
    try {
      const data = await this.requestJson<DailySummaryDto>(
        `v1/today/summary?date=${encodeURIComponent(date)}`,
        http.RequestMethod.GET
      );
      await this.offlineCache.saveCachedSummary(date, data);
      return data;
    } catch (err) {
      hilog.warn(DOMAIN, TAG, 'Failed to fetch summary from network, checking cache: %{public}s', JSON.stringify(err));
      const cached = await this.offlineCache.getCachedSummary(date);
      if (cached) {
        return cached;
      }
      throw new Error('今日汇总暂不可用，请联网后重试');
    }
  }

  /**
   * Fetches school day window constraints
   */
  public async getSchoolDayWindows(date: string): Promise<SchoolDayWindowsDto> {
    try {
      return await this.requestJson<SchoolDayWindowsDto>(
        `v1/school/day-windows?date=${encodeURIComponent(date)}`,
        http.RequestMethod.GET
      );
    } catch (err) {
      throw new Error('无法获得在校时段，暂不上传运动数据以避免错误计入');
    }
  }

  /**
   * Submits student lunch consumption
   */
  public async saveMeal(request: MealConsumptionRequest): Promise<ApiWriteResult> {
    const payload = JSON.stringify(request);
    try {
      return await this.requestJson<ApiWriteResult>(
        'v1/meals/consumption',
        http.RequestMethod.POST,
        {},
        payload
      );
    } catch (err) {
      if (err instanceof HttpStatusError) throw err;
      hilog.warn(DOMAIN, TAG, 'saveMeal network failed, queueing offline: %{public}s', JSON.stringify(err));
      const record = new PendingOfflineRecord(
        `meal_${Date.now()}`,
        Date.now(),
        'meal',
        payload
      );
      await this.offlineCache.enqueuePendingRecord(record);
      return { ok: true, id: record.id, queued: true };
    }
  }

  /**
   * Submits outside-school aggregated activity
   */
  public async saveOutsideSchoolActivity(request: OutsideSchoolActivityRequest): Promise<ApiWriteResult> {
    const payload = JSON.stringify(request);
    try {
      return await this.requestJson<ApiWriteResult>(
        'v1/activity/outside-school',
        http.RequestMethod.POST,
        {},
        payload
      );
    } catch (err) {
      if (err instanceof HttpStatusError) throw err;
      hilog.warn(DOMAIN, TAG, 'saveOutsideSchoolActivity network failed, queueing offline: %{public}s', JSON.stringify(err));
      const record = new PendingOfflineRecord(
        `act_out_${Date.now()}`,
        Date.now(),
        'activity_outside',
        payload
      );
      await this.offlineCache.enqueuePendingRecord(record);
      return { ok: true, id: record.id, queued: true };
    }
  }

  /**
   * Submits manual activity session
   */
  public async saveManualActivity(request: ManualActivityRequest): Promise<ApiWriteResult> {
    const payload = JSON.stringify(request);
    try {
      return await this.requestJson<ApiWriteResult>(
        'v1/activity/manual',
        http.RequestMethod.POST,
        {},
        payload
      );
    } catch (err) {
      if (err instanceof HttpStatusError) throw err;
      // A timeout can happen after the server commits. Replaying append-only
      // records without an idempotency key would create duplicate meals/activity.
      throw new Error('提交结果未能确认，请先刷新汇总再决定是否重试');
    }
  }

  /**
   * Updates student daily energy reference target
   */
  public async saveEnergyReference(request: EnergyReferenceRequest): Promise<ApiWriteResult> {
    const payload = JSON.stringify(request);
    try {
      return await this.requestJson<ApiWriteResult>(
        'v1/preferences/energy-reference',
        http.RequestMethod.PUT,
        {},
        payload
      );
    } catch (err) {
      if (err instanceof HttpStatusError) throw err;
      hilog.warn(DOMAIN, TAG, 'saveEnergyReference network failed, queueing offline: %{public}s', JSON.stringify(err));
      const record = new PendingOfflineRecord(
        `en_ref_${Date.now()}`,
        Date.now(),
        'energy_ref',
        payload
      );
      await this.offlineCache.enqueuePendingRecord(record);
      return { ok: true, id: record.id, queued: true };
    }
  }

  /**
   * AI Analysis of Home Meal photograph
   * Supports multipart/binary or simulated AI model recognition for campus terminals
   */
  public async analyzeHomeMeal(): Promise<HomeMealAnalysisResultDto> {
    const options = new photoAccessHelper.PhotoSelectOptions();
    options.MIMEType = photoAccessHelper.PhotoViewMIMETypes.IMAGE_TYPE;
    options.maxSelectNumber = 1;
    const selection = await new photoAccessHelper.PhotoViewPicker().select(options);
    if (selection.photoUris.length === 0) throw new Error('未选择照片');
    const file = await fs.open(selection.photoUris[0], fs.OpenMode.READ_ONLY);
    let image: ArrayBuffer;
    try {
      const size = (await fs.stat(file.fd)).size;
      if (size <= 0 || size > 8 * 1024 * 1024) throw new Error('请选择 8 MB 以内的照片');
      image = new ArrayBuffer(size);
      let offset = 0;
      while (offset < size) {
        const chunk = new ArrayBuffer(size - offset);
        const read = await fs.read(file.fd, chunk);
        if (read === 0) throw new Error('照片读取不完整');
        new Uint8Array(image).set(new Uint8Array(chunk, 0, read), offset);
        offset += read;
      }
    } finally { await fs.close(file); }
    // Validate the encoded format; a filename extension is not evidence of JPEG.
    const bytes = new Uint8Array(image);
    const jpeg = bytes.length >= 3 && bytes[0] === 255 && bytes[1] === 216 && bytes[2] === 255;
    const png = bytes.length >= 8 && bytes[0] === 137 && bytes[1] === 80 && bytes[2] === 78 && bytes[3] === 71;
    if (!jpeg && !png) throw new Error('请选择 JPEG 或 PNG 照片');
    const request = http.createHttp();
    try {
      const response = await request.request(this.baseUrl + '/v1/home-meals/analyze', {
        method: http.RequestMethod.POST,
        header: { 'x-demo-school': 'demo-school', 'x-demo-participant': 'demo-student', 'x-demo-role': 'student' },
        multiFormDataList: [{ name: 'image', contentType: jpeg ? 'image/jpeg' : 'image/png',
          remoteFileName: jpeg ? 'meal.jpg' : 'meal.png', data: image }],
        connectTimeout: 15000,
        readTimeout: 90000
      });
      if (response.responseCode < 200 || response.responseCode >= 300) throw new HttpStatusError(response.responseCode);
      const result = JSON.parse(response.result as string) as HomeMealAnalysisResultDto;
      if (!result.items || result.items.length === 0) throw new Error('未识别到食物，请换一张照片');
      return result;
    } finally { request.destroy(); }
  }

  /**
   * Saves confirmed home meal
   */
  public async saveHomeMeal(request: ConfirmedHomeMealRequest): Promise<ApiWriteResult> {
    const payload = JSON.stringify(request);
    try {
      return await this.requestJson<ApiWriteResult>(
        'v1/home-meals',
        http.RequestMethod.POST,
        {},
        payload
      );
    } catch (err) {
      if (err instanceof HttpStatusError) throw err;
      // A timeout can happen after the server commits. Replaying append-only
      // records without an idempotency key would create duplicate meals/activity.
      throw new Error('提交结果未能确认，请先刷新汇总再决定是否重试');
    }
  }

  /**
   * Replays pending offline records when network is reconnected
   */
  public async syncOfflineRecords(): Promise<number> {
    if (this.syncing) return 0;
    this.syncing = true;
    try {
    const records = await this.offlineCache.getPendingRecords();
    if (records.length === 0) {
      return 0;
    }

    let synced = 0;
    for (const record of records) {
      if (record.endpointType === 'activity_manual' || record.endpointType === 'home_meal') break;
      try {
        if (record.endpointType === 'meal') {
          await this.requestJson<ApiWriteResult>('v1/meals/consumption', http.RequestMethod.POST, {}, record.payloadJson);
        } else if (record.endpointType === 'activity_manual') {
          await this.requestJson<ApiWriteResult>('v1/activity/manual', http.RequestMethod.POST, {}, record.payloadJson);
        } else if (record.endpointType === 'activity_outside') {
          await this.requestJson<ApiWriteResult>('v1/activity/outside-school', http.RequestMethod.POST, {}, record.payloadJson);
        } else if (record.endpointType === 'energy_ref') {
          await this.requestJson<ApiWriteResult>('v1/preferences/energy-reference', http.RequestMethod.PUT, {}, record.payloadJson);
        } else if (record.endpointType === 'home_meal') {
          await this.requestJson<ApiWriteResult>('v1/home-meals', http.RequestMethod.POST, {}, record.payloadJson);
        } else {
          break;
        }
        await this.offlineCache.removePendingRecord(record.id);
        synced++;
      } catch (err) {
        hilog.error(DOMAIN, TAG, 'Failed to replay record %{public}s: %{public}s', record.id, JSON.stringify(err));
        break;
      }
    }
    hilog.info(DOMAIN, TAG, 'Synchronized %{public}d pending offline records', synced);
    return synced;
    } finally { this.syncing = false; }
  }
}
