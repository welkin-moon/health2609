/**
 * Index Page - OpenHarmony & Kaihong Client Root Entry Page
 * Renders AdaptiveStudentAppShell with system bar configuration
 */

import { AdaptiveStudentAppShell } from '../view/AdaptiveStudentAppShell';
import { M3EColors } from '../theme/M3ETheme';
import common from '@ohos.app.ability.common';
import { OfflineCacheService } from '../service/OfflineCacheService';

@Entry
@Component
struct Index {
  aboutToAppear() {
    // Initialize offline cache preferences
    OfflineCacheService.getInstance().init(getContext(this) as common.UIAbilityContext);
  }

  build() {
    Column() {
      AdaptiveStudentAppShell()
    }
    .width('100%')
    .height('100%')
    .backgroundColor(M3EColors.surface)
  }
}
