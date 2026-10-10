/**
 * AdaptiveStudentAppShell - Material 3 Expressive Responsive Shell for OpenHarmony / Kaihong
 * Features floating capsule dock on handhelds & adaptive navigation on tablets/campus terminals
 */

import { M3EColors, M3EShapes, M3ETypography, BreakpointType } from '../theme/M3ETheme';
import { PrivateAccountView } from './PrivateAccountView';
import { TodayView } from './TodayView';
import { PrivacyView } from './PrivacyView';

interface NavDestination {
  key: string;
  label: string;
  iconChar: string;
}

const NAV_DESTINATIONS: NavDestination[] = [
  { key: 'today', label: '今天', iconChar: '历' },
  { key: 'account', label: '私密记录', iconChar: '账' },
  { key: 'privacy', label: '隐私', iconChar: '盾' }
];

@Component
export struct AdaptiveStudentAppShell {
  @State selectedDestination: string = 'today';
  @State currentBreakpoint: string = 'sm';

  build() {
    GridRow({
      columns: 12,
      breakpoints: { value: ['600vp', '840vp'] }
    }) {
      GridCol({ span: 12 }) {
        Stack({ alignContent: Alignment.Bottom }) {
          // Main Body View
          Column() {
            if (this.selectedDestination === 'account') {
              PrivateAccountView()
            } else if (this.selectedDestination === 'privacy') {
              PrivacyView()
            } else {
              TodayView()
            }
          }
          .width('100%')
          .height('100%')
          .backgroundColor(M3EColors.surface)

          // Floating Capsule Dock (M3E Signature Navigation)
          this.FloatingCapsuleDock()
        }
        .width('100%')
        .height('100%')
      }
    }
    .onBreakpointChange((breakpoint: string) => {
      this.currentBreakpoint = breakpoint;
    })
    .width('100%')
    .height('100%')
  }

  @Builder
  FloatingCapsuleDock() {
    Row({ space: 6 }) {
      ForEach(NAV_DESTINATIONS, (dest: NavDestination) => {
        this.DockItem(dest)
      })
    }
    .padding(6)
    .backgroundColor(M3EColors.surfaceContainerHigh)
    .borderRadius(M3EShapes.floatingDockRadius)
    .shadow({
      radius: 16,
      color: '#26000000',
      offsetX: 0,
      offsetY: 6
    })
    .margin({ bottom: this.currentBreakpoint === 'sm' ? 24 : 32 })
  }

  @Builder
  DockItem(dest: NavDestination) {
    Row({ space: 8 }) {
      Text(dest.iconChar)
        .fontSize(16)
        .fontWeight(FontWeight.Bold)
        .fontColor(
          this.selectedDestination === dest.key
            ? M3EColors.onSecondaryContainer
            : M3EColors.onSurfaceVariant
        )

      if (this.selectedDestination === dest.key) {
        Text(dest.label)
          .fontSize(M3ETypography.labelLarge)
          .fontWeight(FontWeight.Medium)
          .fontColor(M3EColors.onSecondaryContainer)
      }
    }
    .padding({
      left: this.selectedDestination === dest.key ? 20 : 16,
      right: this.selectedDestination === dest.key ? 20 : 16,
      top: 12,
      bottom: 12
    })
    .backgroundColor(
      this.selectedDestination === dest.key
        ? M3EColors.secondaryContainer
        : Color.Transparent
    )
    .borderRadius(M3EShapes.selectedPillRadius)
    .animation({
      duration: 250,
      curve: Curve.EaseInOut
    })
    .onClick(() => {
      this.selectedDestination = dest.key;
    })
  }
}
