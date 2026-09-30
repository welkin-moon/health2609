import { M3EColors, M3EShapes, M3EAnimation } from '../theme/M3ETheme';
import { TodayPage } from './TodayPage';
import { PrivacyPage } from './PrivacyPage';

interface DestinationItem {
  id: string;
  label: string;
  iconText: string;
}

@Component
export struct StudentAppShell {
  @State private selectedDestination: string = 'today';

  private readonly destinations: DestinationItem[] = [
    { id: 'today', label: '今天', iconText: '📅' },
    { id: 'privacy', label: '隐私', iconText: '🛡️' }
  ];

  build() {
    Stack({ alignContent: Alignment.BottomCenter }) {
      // Main Page Content Area
      if (this.selectedDestination === 'today') {
        TodayPage()
      } else {
        PrivacyPage()
      }

      // M3E Floating Bottom Capsule Dock
      Row({ space: 8 }) {
        ForEach(this.destinations, (dest: DestinationItem) => {
          Row({ space: 8 }) {
            Text(dest.iconText)
              .fontSize(18)

            if (this.selectedDestination === dest.id) {
              Text(dest.label)
                .fontSize(14)
                .fontWeight(FontWeight.Bold)
                .fontColor(M3EColors.onSecondaryContainer)
            }
          }
          .padding({
            left: this.selectedDestination === dest.id ? 20 : 16,
            right: this.selectedDestination === dest.id ? 20 : 16,
            top: 10,
            bottom: 10
          })
          .backgroundColor(
            this.selectedDestination === dest.id
              ? M3EColors.secondaryContainer
              : Color.Transparent
          )
          .borderRadius(M3EShapes.pillRadius)
          .animation({
            duration: M3EAnimation.springDurationMs,
            curve: Curve.FastOutSlowIn
          })
          .onClick(() => {
            this.selectedDestination = dest.id;
          })
        })
      }
      .padding(6)
      .margin({ bottom: 20 })
      .backgroundColor(M3EColors.surfaceContainerHigh)
      .borderRadius(M3EShapes.dockRadius)
      .shadow({
        radius: 16,
        color: '#26000000',
        offsetX: 0,
        offsetY: 6
      })
    }
    .width('100%')
    .height('100%')
    .backgroundColor(M3EColors.surface)
  }
}
