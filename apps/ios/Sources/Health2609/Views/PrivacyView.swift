import SwiftUI

public struct PrivacyView: View {
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass

    public init() {}

    public var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                // Header
                VStack(alignment: .leading, spacing: 8) {
                    Text("你的数据，边界很清楚")
                        .font(.system(size: 28, weight: .bold, design: .rounded))
                        .foregroundColor(M3E.Colors.onSurface)

                    Text("学校、手机和你本人，只提供完成当天记录真正需要的那一部分。")
                        .font(.system(size: 16, weight: .regular))
                        .foregroundColor(M3E.Colors.onSurfaceVariant)
                        .lineSpacing(4)
                }
                .padding(.top, 16)
                .padding(.horizontal, 4)

                // Privacy Section Cards: Adaptive Grid on iPad/Landscape, Single Column on iPhone
                if horizontalSizeClass == .regular {
                    LazyVGrid(
                        columns: [
                            GridItem(.flexible(), spacing: 16, alignment: .top),
                            GridItem(.flexible(), spacing: 16, alignment: .top)
                        ],
                        spacing: 16
                    ) {
                        cards
                    }
                } else {
                    VStack(spacing: 16) {
                        cards
                    }
                }

                // Security Tag
                HStack(spacing: 10) {
                    Image(systemName: "checkmark.shield.fill")
                        .font(.system(size: 18))
                        .foregroundColor(M3E.Colors.success)
                    Text("健康 2609 严格遵守隐私保护与最小必要数据收集原则")
                        .font(.system(size: 13, weight: .medium))
                        .foregroundColor(M3E.Colors.onSurfaceVariant)
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 14)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(M3E.Colors.surfaceContainerHigh)
                .clipShape(M3E.Shapes.chip)
                .padding(.top, 8)

                Spacer(minLength: horizontalSizeClass == .regular ? 40 : 120)
            }
            .padding(.horizontal, horizontalSizeClass == .regular ? 28 : 20)
            .frame(maxWidth: M3E.Layout.maxContentWidth)
            .frame(maxWidth: .infinity, alignment: .center)
        }
        .background(M3E.Colors.surface)
    }

    @ViewBuilder
    private var cards: some View {
        PrivacyCard(
            icon: "figure.run.square.stack",
            iconColor: M3E.Colors.primary,
            title: "校内体育由学校记录",
            bodyText: "只用课程安排和老师确认的实际活动时间，不拿手机数据猜体育课。",
            detailText: "课间走动不会被误算成体育课，确保体育成绩公正与严肃性。"
        )

        PrivacyCard(
            icon: "iphone.and.arrow.forward",
            iconColor: M3E.Colors.secondary,
            title: "手机只补校外运动",
            bodyText: "先在手机本地沙盒中排除学校设置的在校时段，再汇总当天运动。",
            detailText: "上传到云端的仅是当天汇总数值，绝不收集任何实时 GPS 轨迹。"
        )

        PrivacyCard(
            icon: "camera.viewfinder",
            iconColor: M3E.Colors.tertiary,
            title: "餐食照片由你确认",
            bodyText: "照片先用来智能识别食物，名称和分量均可自主修改，确认后才会记入当天。",
            detailText: "仅保存确认后的标准化营养素结果，服务端不持久化原始个人餐食照片。"
        )

        PrivacyCard(
            icon: "heart.text.square.fill",
            iconColor: M3E.Colors.primary,
            title: "Apple 健康本地沙盒保护",
            bodyText: "严格遵循 iOS HealthKit 数据规范，步数与卡路里仅在本地进行差集运算。",
            detailText: "系统授权后仅读取必要数据。无可读数据时保留已有记录；通用重签包可使用手动记录。"
        )
    }
}

// MARK: - Privacy Card Component

private struct PrivacyCard: View {
    let icon: String
    let iconColor: Color
    let title: String
    let bodyText: String
    let detailText: String

    var body: some View {
        HStack(alignment: .top, spacing: 16) {
            ZStack {
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .fill(M3E.Colors.secondaryContainer)
                    .frame(width: 48, height: 48)

                Image(systemName: icon)
                    .font(.system(size: 22))
                    .foregroundColor(M3E.Colors.onSecondaryContainer)
            }

            VStack(alignment: .leading, spacing: 6) {
                Text(title)
                    .font(.system(size: 17, weight: .semibold, design: .rounded))
                    .foregroundColor(M3E.Colors.onSurface)

                Text(bodyText)
                    .font(.system(size: 14))
                    .foregroundColor(M3E.Colors.onSurface)
                    .lineSpacing(3)

                Text(detailText)
                    .font(.system(size: 12))
                    .foregroundColor(M3E.Colors.onSurfaceVariant)
                    .padding(.top, 2)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(18)
        .m3Card(backgroundColor: M3E.Colors.surfaceContainerLow, cornerRadius: 24)
    }
}
