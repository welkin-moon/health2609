/**
 * OfflineCacheService for HarmonyOS NEXT & OpenHarmony / KaihongOS
 * Local storage via @ohos.data.preferences with in-memory map fallback for intermittent connectivity
 */

import common from '@ohos.app.ability.common';
import dataPreferences from '@ohos.data.preferences';
import hilog from '@ohos.hilog';
import {
  TodayMenuDto,
  DailySummaryDto,
  PendingOfflineRecord
} from '../model/ApiModels';

const TAG = 'OfflineCacheService';
const DOMAIN = 0x2609;
const PREFERENCES_NAME = 'health2609_campus_offline_store';

export class OfflineCacheService {
  private static instance: OfflineCacheService | null = null;
  private memoryCache: Map<string, string> = new Map<string, string>();
  private preferencesInstance: dataPreferences.Preferences | null = null;
  private isInitialized: boolean = false;
  private initialization: Promise<void> | null = null;
  private queueMutation: Promise<void> = Promise.resolve();

  private constructor() {}

  public static getInstance(): OfflineCacheService {
    if (!OfflineCacheService.instance) {
      OfflineCacheService.instance = new OfflineCacheService();
    }
    return OfflineCacheService.instance;
  }

  /**
   * Initializes the preferences store with context if available
   */
  public async init(context?: common.UIAbilityContext): Promise<void> {
    if (this.isInitialized) {
      return;
    }
    if (this.initialization) return this.initialization;
    if (context) {
      this.initialization = this.initializeStore(context);
      return this.initialization;
    }
  }

  private async initializeStore(context: common.UIAbilityContext): Promise<void> {
      try {
        this.preferencesInstance = await dataPreferences.getPreferences(context, PREFERENCES_NAME);
        this.isInitialized = true;
        hilog.info(DOMAIN, TAG, 'Preferences store initialized successfully');
      } catch (err) {
        hilog.warn(DOMAIN, TAG, 'Preferences init failed, falling back to memory cache: %{public}s', JSON.stringify(err));
      }
  }

  private async putString(key: string, value: string): Promise<void> {
    if (this.preferencesInstance) {
      try {
        await this.preferencesInstance.put(key, value);
        await this.preferencesInstance.flush();
      } catch (e) {
        hilog.error(DOMAIN, TAG, 'Error persisting key %{public}s: %{public}s', key, JSON.stringify(e));
        throw e;
      }
    }
    this.memoryCache.set(key, value);
  }

  private async getString(key: string): Promise<string | null> {
    if (this.preferencesInstance) {
      try {
        const val = await this.preferencesInstance.get(key, '');
        if (typeof val === 'string' && val.length > 0) {
          return val;
        }
      } catch (e) {
        hilog.warn(DOMAIN, TAG, 'Error reading key %{public}s: %{public}s', key, JSON.stringify(e));
      }
    }
    return this.memoryCache.get(key) || null;
  }

  /* -------------------------------------------------------------------------- */
  /*                              Menu Cache                                    */
  /* -------------------------------------------------------------------------- */

  public async saveCachedMenu(date: string, menu: TodayMenuDto): Promise<void> {
    const key = `menu_${date}_${menu.mealSlot}`;
    await this.putString(key, JSON.stringify(menu));
    hilog.info(DOMAIN, TAG, 'Cached menu saved for date: %{public}s', date);
  }

  public async getCachedMenu(date: string, mealSlot: string = 'lunch'): Promise<TodayMenuDto | null> {
    const key = `menu_${date}`;
    const raw = await this.getString(key);
    if (!raw) {
      return null;
    }
    try {
      return JSON.parse(raw) as TodayMenuDto;
    } catch (e) {
      return null;
    }
  }

  /* -------------------------------------------------------------------------- */
  /*                            Summary Cache                                   */
  /* -------------------------------------------------------------------------- */

  public async saveCachedSummary(date: string, summary: DailySummaryDto): Promise<void> {
    const key = `summary_${date}`;
    await this.putString(key, JSON.stringify(summary));
    hilog.info(DOMAIN, TAG, 'Cached summary saved for date: %{public}s', date);
  }

  public async getCachedSummary(date: string): Promise<DailySummaryDto | null> {
    const key = `summary_${date}`;
    const raw = await this.getString(key);
    if (!raw) {
      return null;
    }
    try {
      return JSON.parse(raw) as DailySummaryDto;
    } catch (e) {
      return null;
    }
  }

  /* -------------------------------------------------------------------------- */
  /*                         Offline Pending Records                            */
  /* -------------------------------------------------------------------------- */

  public async enqueuePendingRecord(record: PendingOfflineRecord): Promise<void> {
    if (this.initialization) await this.initialization;
    if (!this.preferencesInstance) throw new Error('本地存储不可用，记录尚未保存');
    const operation = this.queueMutation.then(async () => {
      const records = await this.getPendingRecords();
      records.push(record);
      await this.putString('offline_pending_records', JSON.stringify(records));
    });
    this.queueMutation = operation.catch(() => {});
    await operation;
  }

  public async getPendingRecords(): Promise<PendingOfflineRecord[]> {
    const raw = await this.getString('offline_pending_records');
    if (!raw) {
      return [];
    }
    try {
      return JSON.parse(raw) as PendingOfflineRecord[];
    } catch (e) {
      return [];
    }
  }

  public async removePendingRecord(id: string): Promise<void> {
    const operation = this.queueMutation.then(async () => {
      const records = await this.getPendingRecords();
      await this.putString('offline_pending_records', JSON.stringify(records.filter((record: PendingOfflineRecord) => record.id !== id)));
    });
    this.queueMutation = operation.catch(() => {});
    await operation;
  }

  public async clearPendingRecords(): Promise<void> {
    const operation = this.queueMutation.then(async () => {
      await this.putString('offline_pending_records', '[]');
    });
    this.queueMutation = operation.catch(() => {});
    await operation;
  }

  public async getPendingCount(): Promise<number> {
    const records = await this.getPendingRecords();
    return records.length;
  }
}