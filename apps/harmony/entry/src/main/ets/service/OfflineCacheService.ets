/**
 * OfflineCacheService for HarmonyOS NEXT & OpenHarmony / KaihongOS
 * Local storage via @ohos.data.preferences with in-memory map fallback for intermittent connectivity
 */

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
  public async init(context?: Context): Promise<void> {
    if (this.isInitialized) {
      return;
    }
    if (context) {
      try {
        this.preferencesInstance = await dataPreferences.getPreferences(context, PREFERENCES_NAME);
        this.isInitialized = true;
        hilog.info(DOMAIN, TAG, 'Preferences store initialized successfully');
      } catch (err) {
        hilog.warn(DOMAIN, TAG, 'Preferences init failed, falling back to memory cache: %{public}s', JSON.stringify(err));
      }
    }
  }

  private async putString(key: string, value: string): Promise<void> {
    this.memoryCache.set(key, value);
    if (this.preferencesInstance) {
      try {
        await this.preferencesInstance.put(key, value);
        await this.preferencesInstance.flush();
      } catch (e) {
        hilog.error(DOMAIN, TAG, 'Error persisting key %{public}s: %{public}s', key, JSON.stringify(e));
      }
    }
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
    const key = `menu_${date}`;
    await this.putString(key, JSON.stringify(menu));
    hilog.info(DOMAIN, TAG, 'Cached menu saved for date: %{public}s', date);
  }

  public async getCachedMenu(date: string): Promise<TodayMenuDto | null> {
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
    const records = await this.getPendingRecords();
    records.push(record);
    await this.putString('offline_pending_records', JSON.stringify(records));
    hilog.info(DOMAIN, TAG, 'Queued pending offline record: %{public}s (%{public}s)', record.id, record.endpointType);
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
    const records = await this.getPendingRecords();
    const filtered = records.filter(r => r.id !== id);
    await this.putString('offline_pending_records', JSON.stringify(filtered));
  }

  public async clearPendingRecords(): Promise<void> {
    await this.putString('offline_pending_records', JSON.stringify([]));
  }

  public async getPendingCount(): Promise<number> {
    const records = await this.getPendingRecords();
    return records.length;
  }
}