/**
 * Index Page - HarmonyOS NEXT Client Root Entry Page
 * Renders StudentAppShell / AdaptiveStudentAppShell with offline caching
 */

import { StudentAppShell } from '../view/StudentAppShell';
import { M3EColors } from '../theme/M3ETheme';
import { OfflineCacheService } from '../service/OfflineCacheService';

@Entry
@Component
struct Index {
  aboutToAppear() {
    OfflineCacheService.getInstance().init(getContext(this));
  }

  build() {
    Column() {
      StudentAppShell()
    }
    .width('100%')
    .height('100%')
    .backgroundColor(M3EColors.surface)
  }
}
