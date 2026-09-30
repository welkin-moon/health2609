import SwiftUI

@main
struct Health2609App: App {
    @StateObject private var viewModel = TodayViewModel()

    var body: some Scene {
        WindowGroup {
            StudentAppShell(viewModel: viewModel)
                .background(M3E.Colors.surface)
        }
    }
}
