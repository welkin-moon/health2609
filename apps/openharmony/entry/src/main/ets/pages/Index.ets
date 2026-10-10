/**
 * Index Page - OpenHarmony Client Root Entry Page
 * Renders StudentAppShell / AdaptiveStudentAppShell with offline caching
 */

import { AdaptiveStudentAppShell } from '../view/AdaptiveStudentAppShell';
import { M3EColors } from '../theme/M3ETheme';
import common from '@ohos.app.ability.common';
import { PrivateRepository } from '../sync/PrivateRepository';
import { OfflineCacheService } from '../service/OfflineCacheService';

@Entry
@Component
struct Index {
  @State ready: boolean = false;
  @State failure: string = '';

  async aboutToAppear() {
    const context = getContext(this) as common.UIAbilityContext;
    await OfflineCacheService.getInstance().init(context);
    try { await PrivateRepository.getInstance().init(context); this.ready = true; }
    catch (error) { this.failure = (error as Error).message || '安全存储未能开启，原记录已保留'; }
  }

  build() {
    Column() {
      if (this.ready) {
        AdaptiveStudentAppShell()
      } else {
        Text(this.failure || '正在开启本机加密存储…').padding(24)
      }
    }
    .width('100%')
    .height('100%')
    .backgroundColor(M3EColors.surface)
  }
}
