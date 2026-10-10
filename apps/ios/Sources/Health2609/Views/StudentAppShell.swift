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
    @State private var showSettings = false
    @State private var selectedTab: StudentTab = .today

    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @Environment(\.verticalSizeClass) private var verticalSizeClass

    public init(viewModel: TodayViewModel) {
        self.viewModel = viewModel
    }

    public var body: some View {
        GeometryReader { geometry in
            let useSideRail = (horizontalSizeClass == .regular && geometry.size.width >= 600)

            ZStack {
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
                .padding(.leading, useSideRail ? M3E.Layout.railContentInset : 0)

                // Navigation: Side Navigation Rail on iPad/Regular or Floating Dock on Compact
                if useSideRail {
                    HStack {
                        sideNavigationRail
                            .padding(.leading, 14)
                            .padding(.vertical, 18)
                        Spacer()
                    }
                    .transition(.move(edge: .leading).combined(with: .opacity))
                } else {
                    VStack {
                        Spacer()
                        floatingDock
                            .padding(.bottom, 12)
                    }
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                }
            }
            .overlay(alignment: .topTrailing) {
                Button { showSettings = true } label: {
                    Image(systemName: "gearshape.fill")
                        .padding(12)
                        .background(M3E.Colors.surfaceContainerHigh, in: Circle())
                }
                .accessibilityLabel("设置学校与学生身份")
                .padding(.top, 64)
                .padding(.trailing, 18)
            }
            .sheet(isPresented: $showSettings) {
                StudentSettingsView { viewModel.reloadIdentity() }
            }
            .animation(M3E.Motion.expressive, value: useSideRail)
            .ignoresSafeArea(.keyboard, edges: .bottom)
        }
    }

    // MARK: - Side Navigation Rail (iPadOS & Wide Landscape)

    private var sideNavigationRail: some View {
        VStack(spacing: 20) {
            // Brand emblem
            ZStack {
                Circle()
                    .fill(M3E.Colors.primaryContainer)
                    .frame(width: 44, height: 44)
                Image(systemName: "figure.run.circle.fill")
                    .font(.system(size: 26))
                    .foregroundColor(M3E.Colors.onPrimaryContainer)
            }
            .padding(.top, 12)

            Spacer()

            // Destination Navigation Items
            VStack(spacing: 12) {
                ForEach(StudentTab.allCases) { tab in
                    let isSelected = selectedTab == tab

                    Button {
                        withAnimation(M3E.Motion.expressive) {
                            selectedTab = tab
                        }
                    } label: {
                        VStack(spacing: 6) {
                            ZStack {
                                RoundedRectangle(cornerRadius: 18, style: .continuous)
                                    .fill(isSelected ? M3E.Colors.secondaryContainer : Color.clear)
                                    .frame(width: 58, height: 38)

                                Image(systemName: tab.icon)
                                    .font(.system(size: 19, weight: isSelected ? .semibold : .regular))
                                    .foregroundColor(isSelected ? M3E.Colors.onSecondaryContainer : M3E.Colors.onSurfaceVariant)
                            }

                            Text(tab.title)
                                .font(.system(size: 12, weight: isSelected ? .bold : .medium, design: .rounded))
                                .foregroundColor(isSelected ? M3E.Colors.onSecondaryContainer : M3E.Colors.onSurfaceVariant)
                        }
                        .frame(width: 66)
                    }
                    .buttonStyle(.plain)
                }
            }

            Spacer()

            // Safe sandbox privacy badge
            Image(systemName: "checkmark.shield")
                .font(.system(size: 16))
                .foregroundColor(M3E.Colors.outline)
                .padding(.bottom, 16)
        }
        .frame(width: 74)
        .padding(.vertical, 8)
        .background(
            RoundedRectangle(cornerRadius: 36, style: .continuous)
                .fill(M3E.Colors.surfaceContainerHigh)
                .shadow(color: Color.black.opacity(0.12), radius: 18, x: 0, y: 6)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 36, style: .continuous)
                .strokeBorder(M3E.Colors.outlineVariant.opacity(0.3), lineWidth: 1)
        )
    }

    // MARK: - Floating Bottom Dock (Handheld Compact)

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
