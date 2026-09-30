import { M3EColors, M3EShapes } from '../theme/M3ETheme';

interface PrivacyItemData {
  iconText: string;
  title: string;
  body: string;
  detail: string;
}

@Component
export struct PrivacyPage {
  private privacyItems: PrivacyItemData[] = [
    {
      iconText: '🏫',
      title: '校内体育由学校记录',
      body: '只用课程安排和老师确认的实际活动时间，不拿手机数据猜体育课。',
      detail: '课间走动不会被误算成体育课。'
    },
    {
      iconText: '🏃',
      title: '手机只补校外运动',
      body: '先在手机上排除学校设置的在校时段，再汇总当天运动。',
      detail: '上传的是当天汇总，不是运动轨迹。'
    },
    {
      iconText: '📸',
      title: '餐食照片由你确认',
      body: '照片先用来识别食物，名称和分量都可以改，确认后才会记进当天。',
      detail: '保存确认后的餐食结果，不保存原始照片。'
    },
    {
      iconText: '🛡️',
      title: '鸿蒙端侧沙箱安全',
      body: '严格遵循 HarmonyOS NEXT 安全隔离机制，草稿与传感器数据受系统沙箱保护。',
      detail: '权限按需申请，绝不索取非必要隐私信息。'
    }
  ];

  build() {
    Scroll() {
      Column({ space: 20 }) {
        // Header
        Column({ space: 8 }) {
          Text('你的数据，边界很清楚')
            .fontSize(28)
            .fontWeight(FontWeight.Bold)
            .fontColor(M3EColors.onSurface)
            .width('100%')

          Text('学校、手机和你本人，只提供完成当天记录真正需要的那一部分。')
            .fontSize(16)
            .lineHeight(24)
            .fontColor(M3EColors.onSurfaceVariant)
            .width('100%')
        }
        .width('100%')
        .alignItems(HorizontalAlign.Start)
        .padding({ top: 32, bottom: 8 })

        // Privacy Sections
        ForEach(this.privacyItems, (item: PrivacyItemData) => {
          Row({ space: 16 }) {
            // Icon Badge
            Stack() {
              Text(item.iconText)
                .fontSize(22)
            }
            .width(52)
            .height(52)
            .borderRadius(18)
            .backgroundColor(M3EColors.secondaryContainer)
            .alignContent(Alignment.Center)

            // Content
            Column({ space: 6 }) {
              Text(item.title)
                .fontSize(17)
                .fontWeight(FontWeight.Bold)
                .fontColor(M3EColors.onSurface)
                .width('100%')

              Text(item.body)
                .fontSize(15)
                .lineHeight(22)
                .fontColor(M3EColors.onSurface)
                .width('100%')

              Text(item.detail)
                .fontSize(13)
                .lineHeight(18)
                .fontColor(M3EColors.onSurfaceVariant)
                .width('100%')
            }
            .layoutWeight(1)
            .alignItems(HorizontalAlign.Start)
          }
          .width('100%')
          .padding(18)
          .backgroundColor(M3EColors.surfaceContainerLow)
          .borderRadius(M3EShapes.sectionRadius)
          .alignItems(VerticalAlign.Top)
        })

        // Safety K-Anonymity card
        Column({ space: 8 }) {
          Row({ space: 8 }) {
            Text('🔒')
              .fontSize(16)
            Text('群体统计 k-匿名保护 (k ≥ 3)')
              .fontSize(14)
              .fontWeight(FontWeight.Medium)
              .fontColor(M3EColors.primary)
          }
          Text('面向管理端的班级与年级报表实施差分与群体抑制算法，样本少于 3 人时不展示个人平均营养值，杜绝反推个体隐私。')
            .fontSize(13)
            .lineHeight(19)
            .fontColor(M3EColors.onSurfaceVariant)
        }
        .width('100%')
        .padding(16)
        .backgroundColor(M3EColors.surfaceContainerHighest)
        .borderRadius(M3EShapes.sectionRadius)
        .alignItems(HorizontalAlign.Start)

        // Bottom spacing for capsule dock
        Blank().height(100)
      }
      .width('100%')
      .padding({ left: 20, right: 20 })
    }
    .width('100%')
    .height('100%')
    .scrollBar(BarState.Off)
    .backgroundColor(M3EColors.surface)
  }
}
