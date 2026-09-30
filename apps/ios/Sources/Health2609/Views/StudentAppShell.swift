import SwiftUI

public enum StudentTab: String, CaseIterable, Identifiable {
    case today = "today"
    case privacy = "privacy"

    public var id: String { rawValue }

    public var title: String {
        switch self {
        case .today: return "今天"
        case .privacy: return "隐私"
        }
    }

    public var icon: String {
        switch self {
        case .today: return "calendar"
        case .privacy: return "lock.shield.fill"
        }
    }
}

public struct StudentAppShell: View {
    @ObservedObject var viewModel: TodayViewModel
    @State private var selectedTab: StudentTab = .today

    public init(viewModel: TodayViewModel) {
        self.viewModel = viewModel
    }

    public var body: some View {
        ZStack(alignment: .bottom) {
            // Main Content Area
            Group {
                switch selectedTab {
                case .today:
                    TodayView(viewModel: viewModel)
                case .privacy:
                    PrivacyView()
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)

            // Floating Bottom Dock Capsule
            floatingDock
                .padding(.bottom, 12)
        }
        .ignoresSafeArea(.keyboard, edges: .bottom)
    }

    // MARK: - Floating Bottom Dock

    private var floatingDock: some View {
        HStack(spacing: 6) {
            ForEach(StudentTab.allCases) { tab in
                let isSelected = selectedTab == tab

                Button {
                    withAnimation(M3E.Motion.expressive) {
                        selectedTab = tab
                    }
                } label: {
                    HStack(spacing: 8) {
                        Image(systemName: tab.icon)
                            .font(.system(size: 18, weight: isSelected ? .semibold : .regular))

                        if isSelected {
                            Text(tab.title)
                                .font(.system(size: 15, weight: .semibold, design: .rounded))
                                .transition(.scale.combined(with: .opacity))
                        }
                    }
                    .foregroundColor(isSelected ? M3E.Colors.onSecondaryContainer : M3E.Colors.onSurfaceVariant)
                    .padding(.horizontal, isSelected ? 20 : 16)
                    .padding(.vertical, 12)
                    .background(
                        Group {
                            if isSelected {
                                RoundedRectangle(cornerRadius: 28, style: .continuous)
                                    .fill(M3E.Colors.secondaryContainer)
                            } else {
                                Color.clear
                            }
                        }
                    )
                }
                .buttonStyle(.plain)
            }
        }
        .padding(6)
        .background(
            RoundedRectangle(cornerRadius: 34, style: .continuous)
                .fill(M3E.Colors.surfaceContainerHigh)
                .shadow(color: Color.black.opacity(0.12), radius: 16, x: 0, y: 6)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 34, style: .continuous)
                .strokeBorder(M3E.Colors.outlineVariant.opacity(0.3), lineWidth: 1)
        )
    }
}
