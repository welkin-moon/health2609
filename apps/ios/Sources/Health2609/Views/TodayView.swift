import SwiftUI
#if canImport(PhotosUI)
import PhotosUI
#endif

public struct TodayView: View {
    @ObservedObject var viewModel: TodayViewModel

    #if canImport(PhotosUI)
    @State private var selectedPhotoItem: PhotosPickerItem? = nil
    #endif
    @State private var showManualActivity: Bool = false
    @State private var showEditReference: Bool = false
    @State private var expandedDishGrams: Set<String> = []

    public init(viewModel: TodayViewModel) {
        self.viewModel = viewModel
    }

    public var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                // Top Bar
                topBar

                // Message Banner
                if let msg = viewModel.message {
                    statusBanner(msg)
                }

                if viewModel.loading && viewModel.menu == nil {
                    loadingPlaceholder
                } else {
                    // 1. Today Overview Section ("今日汇总")
                    overviewSection

                    // 2. Lunch Section ("午餐")
                    lunchSection

                    // 3. Physical Activity Section ("运动")
                    activitySection

                    // 4. Home Meal AI Section ("家庭餐")
                    homeMealSection

                    Spacer(minLength: 120)
                }
            }
            .padding(.horizontal, 18)
            .padding(.top, 8)
        }
        .background(M3E.Colors.surface)
        .refreshable {
            viewModel.refresh()
        }
    }

    // MARK: - Top Bar

    private var topBar: some View {
        HStack(alignment: .center) {
            VStack(alignment: .leading, spacing: 2) {
                Text("今天")
                    .font(.system(size: 30, weight: .bold, design: .rounded))
                    .foregroundColor(M3E.Colors.onSurface)

                Text(viewModel.formattedDate)
                    .font(.system(size: 14, weight: .medium))
                    .foregroundColor(M3E.Colors.onSurfaceVariant)
            }

            Spacer()

            Button {
                withAnimation(M3E.Motion.expressive) {
                    viewModel.refresh()
                }
            } label: {
                ZStack {
                    Circle()
                        .fill(M3E.Colors.surfaceContainerHigh)
                        .frame(width: 44, height: 44)

                    Image(systemName: "arrow.clockwise")
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundColor(M3E.Colors.primary)
                        .rotationEffect(.degrees(viewModel.loading ? 360 : 0))
                        .animation(viewModel.loading ? Animation.linear(duration: 1).repeatForever(autoreverses: false) : .default, value: viewModel.loading)
                }
            }
            .disabled(viewModel.loading)
        }
        .padding(.vertical, 4)
    }

    // MARK: - Status Banner

    private func statusBanner(_ msg: String) -> some View {
        HStack(spacing: 12) {
            Image(systemName: "info.circle.fill")
                .foregroundColor(M3E.Colors.onSecondaryContainer)
                .font(.system(size: 18))

            Text(msg)
                .font(.system(size: 14, weight: .medium))
                .foregroundColor(M3E.Colors.onSecondaryContainer)
                .lineLimit(2)

            Spacer()

            Button {
                withAnimation(M3E.Motion.snappy) {
                    viewModel.message = nil
                }
            } label: {
                Image(systemName: "xmark")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(M3E.Colors.onSecondaryContainer.opacity(0.8))
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(M3E.Colors.secondaryContainer)
        .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
        .transition(.scale.combined(with: .opacity))
    }

    private var loadingPlaceholder: some View {
        VStack(spacing: 16) {
            ProgressView()
                .scaleEffect(1.2)
                .tint(M3E.Colors.primary)
            Text("正在同步今日数据…")
                .font(.system(size: 15, weight: .medium))
                .foregroundColor(M3E.Colors.onSurfaceVariant)
        }
        .frame(maxWidth: .infinity, minHeight: 300)
    }

    // MARK: - 1. Today Overview Section ("今日汇总")

    private var overviewSection: some View {
        VStack(alignment: .leading, spacing: 14) {
            SectionHeader(
                title: "今日汇总",
                subtitle: "记录完成后，热量与运动将自动汇聚",
                icon: "chart.pie.fill",
                iconColor: M3E.Colors.primary
            )

            let act = viewModel.summary?.activity
            let nut = viewModel.summary?.nutrition
            let eng = viewModel.summary?.energy

            let totalMins = act?.totalMinutes ?? 0
            let targetMins = max(1, act?.targetMinutes ?? 120)
            let progress = min(max(Double(totalMins) / Double(targetMins), 0.0), 1.0)

            VStack(spacing: 16) {
                // Metric cards row
                HStack(spacing: 10) {
                    OverviewStatBox(
                        value: "\(Int(nut?.energyKcal ?? 0))",
                        unit: "千卡",
                        label: "总摄入热量",
                        accentColor: M3E.Colors.primary
                    )

                    OverviewStatBox(
                        value: "\(totalMins)",
                        unit: "分钟",
                        label: "运动总时长",
                        accentColor: M3E.Colors.secondary
                    )
                }

                // Macronutrient Breakdown
                HStack(spacing: 8) {
                    MacroChip(name: "蛋白质", grams: nut?.proteinG ?? 0.0, color: Color.blue)
                    MacroChip(name: "脂肪", grams: nut?.fatG ?? 0.0, color: Color.orange)
                    MacroChip(name: "碳水", grams: nut?.carbohydrateG ?? 0.0, color: Color.green)
                }

                // Exercise progress bar
                VStack(alignment: .leading, spacing: 6) {
                    HStack {
                        Text("身体活动目标")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundColor(M3E.Colors.onSurface)

                        Spacer()

                        Text("\(totalMins) / \(targetMins) 分钟")
                            .font(.system(size: 13, weight: .bold, design: .rounded))
                            .foregroundColor(act?.targetReached == true ? M3E.Colors.success : M3E.Colors.primary)
                    }

                    GeometryReader { geo in
                        ZStack(alignment: .leading) {
                            Capsule()
                                .fill(M3E.Colors.surfaceContainerHighest)
                                .frame(height: 10)

                            Capsule()
                                .fill(
                                    LinearGradient(
                                        colors: [M3E.Colors.primary, act?.targetReached == true ? M3E.Colors.success : Color(hex: "#7C4DFF")],
                                        startPoint: .leading,
                                        endPoint: .trailing
                                    )
                                )
                                .frame(width: max(10, geo.size.width * CGFloat(progress)), height: 10)
                        }
                    }
                    .frame(height: 10)
                }

                // Next Action Tip ("下一步建议")
                let tip = viewModel.nextActionTip
                HStack(alignment: .top, spacing: 12) {
                    ZStack {
                        Circle()
                            .fill(M3E.Colors.primaryContainer)
                            .frame(width: 38, height: 38)
                        Image(systemName: tip.iconName)
                            .foregroundColor(M3E.Colors.onPrimaryContainer)
                            .font(.system(size: 18))
                    }

                    VStack(alignment: .leading, spacing: 4) {
                        HStack {
                            Text("下一步建议")
                                .font(.system(size: 14, weight: .bold))
                                .foregroundColor(M3E.Colors.onSurface)

                            Spacer()

                            Text(tip.tag)
                                .font(.system(size: 11, weight: .semibold))
                                .padding(.horizontal, 8)
                                .padding(.vertical, 2)
                                .background(M3E.Colors.secondaryContainer)
                                .foregroundColor(M3E.Colors.onSecondaryContainer)
                                .clipShape(Capsule())
                        }

                        Text(tip.message)
                            .font(.system(size: 13))
                            .foregroundColor(M3E.Colors.onSurfaceVariant)
                            .lineSpacing(3)
                    }
                }
                .padding(14)
                .background(M3E.Colors.surfaceContainerLowest)
                .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))

                // Daily energy reference setting
                HStack {
                    Button {
                        withAnimation(M3E.Motion.expressive) {
                            showEditReference.toggle()
                        }
                    } label: {
                        HStack(spacing: 6) {
                            Image(systemName: "slider.horizontal.3")
                                .font(.system(size: 13))
                            Text(eng?.dailyEnergyReferenceKcal != nil ? "每日能量参考: \(eng!.dailyEnergyReferenceKcal!) kcal" : "设置每日能量参考")
                                .font(.system(size: 13, weight: .medium))
                        }
                        .foregroundColor(M3E.Colors.primary)
                    }

                    Spacer()
                }

                if showEditReference {
                    HStack(spacing: 8) {
                        TextField("参考摄入 (500–6000 kcal)", text: $viewModel.energyReferenceInput)
                            #if canImport(UIKit)
                            .keyboardType(.numberPad)
                            #endif
                            .textFieldStyle(.plain)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 8)
                            .background(M3E.Colors.surfaceContainerLowest)
                            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                            .overlay(RoundedRectangle(cornerRadius: 12).stroke(M3E.Colors.outlineVariant, lineWidth: 1))

                        Button {
                            viewModel.saveEnergyReference()
                            withAnimation { showEditReference = false }
                        } label: {
                            Text(viewModel.savingEnergyReference ? "保存中" : "保存")
                                .font(.system(size: 13, weight: .bold))
                                .foregroundColor(M3E.Colors.onPrimary)
                                .padding(.horizontal, 16)
                                .padding(.vertical, 8)
                                .background(M3E.Colors.primary)
                                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                        }
                        .disabled(viewModel.savingEnergyReference)
                    }
                }
            }
            .padding(18)
            .m3Card(backgroundColor: M3E.Colors.surfaceContainerLow, cornerRadius: 32)
        }
    }

    // MARK: - 2. Lunch Section ("午餐")

    private var lunchSection: some View {
        VStack(alignment: .leading, spacing: 14) {
            SectionHeader(
                title: "午餐",
                subtitle: "学校今日菜单，按实际进食比例记录",
                icon: "fork.knife",
                iconColor: Color(hex: "#FF6D00")
            )

            let dishes = viewModel.menu?.dishes ?? []
            if dishes.isEmpty {
                VStack(spacing: 8) {
                    Image(systemName: "calendar.badge.clock")
                        .font(.system(size: 32))
                        .foregroundColor(M3E.Colors.outline)
                    Text("今天的午餐菜单尚未发布")
                        .font(.system(size: 16, weight: .medium))
                        .foregroundColor(M3E.Colors.onSurface)
                    Text("学校管理员发布后会自动显示在此处。")
                        .font(.system(size: 13))
                        .foregroundColor(M3E.Colors.onSurfaceVariant)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 32)
                .m3Card(backgroundColor: M3E.Colors.surfaceContainerLow, cornerRadius: 24)
            } else {
                VStack(spacing: 12) {
                    ForEach(dishes) { dish in
                        DishCard(
                            dish: dish,
                            amount: viewModel.amounts[dish.id] ?? DishAmount(),
                            isGramsExpanded: expandedDishGrams.contains(dish.id),
                            onToggleGrams: {
                                withAnimation(M3E.Motion.expressive) {
                                    if expandedDishGrams.contains(dish.id) {
                                        expandedDishGrams.remove(dish.id)
                                    } else {
                                        expandedDishGrams.insert(dish.id)
                                    }
                                }
                            },
                            onPortionSelect: { portion in
                                viewModel.setPortion(dishId: dish.id, portion: portion)
                            },
                            onGramsChanged: { grams in
                                viewModel.setConsumedGrams(dishId: dish.id, grams: grams)
                            }
                        )
                    }

                    // Lunch Summary Banner & Save Button
                    lunchSummaryBar
                }
            }
        }
    }

    private var lunchSummaryBar: some View {
        let preview = viewModel.lunchPreview
        return HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 3) {
                Text("午餐预估合计")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundColor(M3E.Colors.onTertiaryContainer.opacity(0.8))

                Text("\(Int(preview.energyKcal)) 千卡 · 蛋白 \(String(format: "%.1f", preview.proteinG))g · 脂肪 \(String(format: "%.1f", preview.fatG))g · 碳水 \(String(format: "%.1f", preview.carbohydrateG))g")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundColor(M3E.Colors.onTertiaryContainer)
            }

            Spacer()

            Button {
                viewModel.saveMeal()
            } label: {
                HStack(spacing: 6) {
                    if viewModel.savingMeal {
                        ProgressView()
                            .scaleEffect(0.8)
                            .tint(M3E.Colors.onPrimary)
                    }
                    Text(viewModel.savingMeal ? "保存中" : "保存午餐记录")
                        .font(.system(size: 13, weight: .bold))
                }
                .foregroundColor(M3E.Colors.onPrimary)
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
                .background(M3E.Colors.primary)
                .clipShape(Capsule())
            }
            .disabled(viewModel.savingMeal)
        }
        .padding(16)
        .background(M3E.Colors.tertiaryContainer)
        .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
    }

    // MARK: - 3. Physical Activity Section ("运动")

    private var activitySection: some View {
        VStack(alignment: .leading, spacing: 14) {
            SectionHeader(
                title: "运动",
                subtitle: "学校记校内课，手机补校外活动",
                icon: "figure.run",
                iconColor: Color(hex: "#00B0FF")
            )

            let act = viewModel.summary?.activity

            VStack(spacing: 14) {
                // Header with sync button
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("\(act?.totalMinutes ?? 0) 分钟")
                            .font(.system(size: 24, weight: .bold, design: .rounded))
                            .foregroundColor(M3E.Colors.onSurface)
                        Text("今日已记录运动总计")
                            .font(.system(size: 12))
                            .foregroundColor(M3E.Colors.onSurfaceVariant)
                    }

                    Spacer()

                    Button {
                        viewModel.syncHealthKitActivity()
                    } label: {
                        HStack(spacing: 6) {
                            if viewModel.syncingPhoneActivity {
                                ProgressView()
                                    .scaleEffect(0.8)
                                    .tint(M3E.Colors.primary)
                            } else {
                                Image(systemName: "heart.fill")
                                    .foregroundColor(.red)
                            }
                            Text(viewModel.syncingPhoneActivity ? "同步中…" : "从 Apple 健康同步")
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundColor(M3E.Colors.primary)
                        }
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                        .background(M3E.Colors.secondaryContainer)
                        .clipShape(Capsule())
                    }
                    .disabled(viewModel.syncingPhoneActivity)
                }

                // 3 Mini Stat Boxes (PE minutes, Outside minutes, Active Kcal)
                HStack(spacing: 10) {
                    ActivityMiniBox(title: "校内体育", value: "\(act?.peMinutes ?? 0) 分钟")
                    ActivityMiniBox(title: "校外运动", value: "\(act?.outsideMinutes ?? 0) 分钟")
                    ActivityMiniBox(
                        title: "活动消耗",
                        value: act?.activeEnergyKcal != nil ? "\(Int(act!.activeEnergyKcal!)) 千卡" : "—"
                    )
                }

                // School Day Window Info
                if !viewModel.schoolWindows.isEmpty {
                    HStack(spacing: 6) {
                        Image(systemName: "building.2.fill")
                            .font(.system(size: 11))
                            .foregroundColor(M3E.Colors.onSurfaceVariant)
                        let windowStr = viewModel.schoolWindows.map { "\($0.startTime)–\($0.endTime)" }.joined(separator: ", ")
                        Text("今日在校时段: \(windowStr) (自动排除)")
                            .font(.system(size: 11))
                            .foregroundColor(M3E.Colors.onSurfaceVariant)
                        Spacer()
                    }
                    .padding(.horizontal, 4)
                }

                Divider()
                    .background(M3E.Colors.outlineVariant)

                // Toggle manual activity button
                Button {
                    withAnimation(M3E.Motion.expressive) {
                        showManualActivity.toggle()
                    }
                } label: {
                    HStack {
                        Image(systemName: showManualActivity ? "minus.circle" : "plus.circle")
                        Text(showManualActivity ? "收起手动补记" : "手动补记一次运动")
                            .font(.system(size: 14, weight: .semibold))
                        Spacer()
                    }
                    .foregroundColor(M3E.Colors.primary)
                }

                // Manual Activity Panel
                if showManualActivity {
                    manualActivityEditor
                }
            }
            .padding(18)
            .m3Card(backgroundColor: M3E.Colors.surfaceContainerLow, cornerRadius: 32)
        }
    }

    private var manualActivityEditor: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("运动项目")
                .font(.system(size: 13, weight: .semibold))
                .foregroundColor(M3E.Colors.onSurface)

            // Activity preset chips
            let presets = ["跑步", "跳绳", "羽毛球", "篮球", "自主运动"]
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(presets, id: \.self) { type in
                        Button {
                            viewModel.manualActivityType = type
                        } label: {
                            Text(type)
                                .font(.system(size: 13, weight: .medium))
                                .padding(.horizontal, 14)
                                .padding(.vertical, 7)
                                .background(viewModel.manualActivityType == type ? M3E.Colors.secondaryContainer : M3E.Colors.surfaceContainerHighest)
                                .foregroundColor(viewModel.manualActivityType == type ? M3E.Colors.onSecondaryContainer : M3E.Colors.onSurface)
                                .clipShape(Capsule())
                        }
                    }
                }
            }

            // Duration slider
            HStack {
                Text("时长")
                    .font(.system(size: 13, weight: .semibold))
                Spacer()
                Text("\(viewModel.manualActivityMinutes) 分钟")
                    .font(.system(size: 14, weight: .bold, design: .rounded))
                    .foregroundColor(M3E.Colors.primary)
            }

            Slider(
                value: Binding(
                    get: { Double(viewModel.manualActivityMinutes) },
                    set: { viewModel.manualActivityMinutes = Int($0) }
                ),
                in: 5...180,
                step: 5
            )
            .tint(M3E.Colors.primary)

            // Intensity chips
            Text("运动强度")
                .font(.system(size: 13, weight: .semibold))

            HStack(spacing: 8) {
                IntensityChip(title: "轻松", val: "light", current: $viewModel.manualActivityIntensity)
                IntensityChip(title: "中等", val: "moderate", current: $viewModel.manualActivityIntensity)
                IntensityChip(title: "较累", val: "vigorous", current: $viewModel.manualActivityIntensity)
            }

            // Submit button
            Button {
                viewModel.saveManualActivity()
            } label: {
                HStack {
                    if viewModel.savingActivity {
                        ProgressView().tint(M3E.Colors.onPrimary)
                    }
                    Text(viewModel.savingActivity ? "保存中…" : "记录运动")
                        .font(.system(size: 14, weight: .bold))
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 11)
                .background(M3E.Colors.primary)
                .foregroundColor(M3E.Colors.onPrimary)
                .clipShape(Capsule())
            }
            .disabled(viewModel.savingActivity)
            .padding(.top, 4)
        }
        .padding(14)
        .background(M3E.Colors.surfaceContainerLowest)
        .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
    }

    // MARK: - 4. Home Meal AI Section ("家庭餐")

    private var homeMealSection: some View {
        VStack(alignment: .leading, spacing: 14) {
            SectionHeader(
                title: "家庭餐",
                subtitle: "早晚餐或周末家庭餐食，拍照智能识别",
                icon: "camera.fill",
                iconColor: Color(hex: "#7E57C2")
            )

            VStack(spacing: 16) {
                // Slot selector (breakfast, lunch, dinner)
                HStack(spacing: 8) {
                    ForEach([("breakfast", "早餐"), ("lunch", "午餐"), ("dinner", "晚餐")], id: \.0) { item in
                        Button {
                            viewModel.homeMealSlot = item.0
                        } label: {
                            Text(item.1)
                                .font(.system(size: 13, weight: .semibold))
                                .padding(.horizontal, 16)
                                .padding(.vertical, 8)
                                .background(viewModel.homeMealSlot == item.0 ? M3E.Colors.secondaryContainer : M3E.Colors.surfaceContainerHighest)
                                .foregroundColor(viewModel.homeMealSlot == item.0 ? M3E.Colors.onSecondaryContainer : M3E.Colors.onSurface)
                                .clipShape(Capsule())
                        }
                    }
                    Spacer()
                }

                // Photo Capture / Selection Buttons
                HStack(spacing: 12) {
                    #if canImport(PhotosUI)
                    PhotosPicker(
                        selection: $selectedPhotoItem,
                        matching: .images,
                        photoLibrary: .shared()
                    ) {
                        HStack(spacing: 8) {
                            Image(systemName: "photo.on.rectangle")
                                .font(.system(size: 16))
                            Text(viewModel.analyzingHomeMeal ? "识别中…" : "从相册选择")
                                .font(.system(size: 14, weight: .semibold))
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(M3E.Colors.primaryContainer)
                        .foregroundColor(M3E.Colors.onPrimaryContainer)
                        .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                    }
                    .disabled(viewModel.analyzingHomeMeal)
                    .onChange(of: selectedPhotoItem) { _, newItem in
                        Task {
                            if let data = try? await newItem?.loadTransferable(type: Data.self) {
                                viewModel.analyzeHomeMeal(imageData: data)
                            }
                        }
                    }
                    #endif

                    // Demo / Fast simulate meal capture button
                    Button {
                        // Demo sample meal simulation
                        if let mockJpg = "demo-meal-photo".data(using: .utf8) {
                            viewModel.analyzeHomeMeal(imageData: mockJpg)
                        }
                    } label: {
                        HStack(spacing: 6) {
                            Image(systemName: "camera.viewfinder")
                            Text("拍照识别")
                                .font(.system(size: 14, weight: .semibold))
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(M3E.Colors.surfaceContainerHigh)
                        .foregroundColor(M3E.Colors.onSurface)
                        .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                    }
                    .disabled(viewModel.analyzingHomeMeal)
                }

                // AI analyzing progress
                if viewModel.analyzingHomeMeal {
                    HStack(spacing: 10) {
                        ProgressView()
                            .tint(M3E.Colors.primary)
                        Text("正在识别餐盘中的食物并估算分量…")
                            .font(.system(size: 13))
                            .foregroundColor(M3E.Colors.onSurfaceVariant)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.vertical, 4)
                }

                // AI Notes if any
                if let note = viewModel.homeMealNotes.first {
                    Text(note)
                        .font(.system(size: 12))
                        .foregroundColor(M3E.Colors.onSurfaceVariant)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }

                // Draft food items list
                if !viewModel.homeMealDraft.isEmpty {
                    VStack(spacing: 10) {
                        ForEach(Array(viewModel.homeMealDraft.enumerated()), id: \.element.id) { index, item in
                            HomeMealDraftRow(
                                item: item,
                                onNameChange: { newName in
                                    viewModel.setHomeMealName(index: index, name: newName)
                                },
                                onGramsChange: { grams in
                                    viewModel.setHomeMealGrams(index: index, grams: grams)
                                },
                                onRemove: {
                                    viewModel.removeHomeMealItem(index: index)
                                }
                            )
                        }

                        // Confirmation button
                        Button {
                            viewModel.saveHomeMeal()
                        } label: {
                            HStack {
                                if viewModel.savingHomeMeal {
                                    ProgressView().tint(M3E.Colors.onPrimary)
                                }
                                Text(viewModel.savingHomeMeal ? "正在保存…" : "确认并记入今日营养")
                                    .font(.system(size: 14, weight: .bold))
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 12)
                            .background(M3E.Colors.primary)
                            .foregroundColor(M3E.Colors.onPrimary)
                            .clipShape(Capsule())
                        }
                        .disabled(viewModel.savingHomeMeal)
                        .padding(.top, 4)

                        Text("食物名称和克数均由你最终确认，确认前不计入汇总。")
                            .font(.system(size: 11))
                            .foregroundColor(M3E.Colors.onSurfaceVariant)
                            .multilineTextAlignment(.center)
                    }
                }
            }
            .padding(18)
            .m3Card(backgroundColor: M3E.Colors.surfaceContainerLow, cornerRadius: 32)
        }
    }
}

// MARK: - Section Header

private struct SectionHeader: View {
    let title: String
    let subtitle: String
    let icon: String
    let iconColor: Color

    var body: some View {
        HStack(spacing: 12) {
            ZStack {
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .fill(M3E.Colors.surfaceContainerHigh)
                    .frame(width: 42, height: 42)
                Image(systemName: icon)
                    .foregroundColor(iconColor)
                    .font(.system(size: 19))
            }

            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.system(size: 20, weight: .bold, design: .rounded))
                    .foregroundColor(M3E.Colors.onSurface)
                Text(subtitle)
                    .font(.system(size: 12))
                    .foregroundColor(M3E.Colors.onSurfaceVariant)
            }
            Spacer()
        }
    }
}

// MARK: - Overview Stat Box

private struct OverviewStatBox: View {
    let value: String
    let unit: String
    let label: String
    let accentColor: Color

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(alignment: .firstTextBaseline, spacing: 3) {
                Text(value)
                    .font(.system(size: 28, weight: .bold, design: .rounded))
                    .foregroundColor(M3E.Colors.onSurface)
                Text(unit)
                    .font(.system(size: 12, weight: .medium))
                    .foregroundColor(M3E.Colors.onSurfaceVariant)
            }

            Text(label)
                .font(.system(size: 12, weight: .medium))
                .foregroundColor(M3E.Colors.onSurfaceVariant)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .background(M3E.Colors.surfaceContainerLowest)
        .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
    }
}

private struct MacroChip: View {
    let name: String
    let grams: Double
    let color: Color

    var body: some View {
        HStack(spacing: 4) {
            Circle()
                .fill(color)
                .frame(width: 6, height: 6)
            Text(name)
                .font(.system(size: 12))
                .foregroundColor(M3E.Colors.onSurfaceVariant)
            Text("\(Int(grams))g")
                .font(.system(size: 12, weight: .semibold, design: .rounded))
                .foregroundColor(M3E.Colors.onSurface)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 8)
        .background(M3E.Colors.surfaceContainerLowest)
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
    }
}

// MARK: - Dish Card

private struct DishCard: View {
    let dish: DishDto
    let amount: DishAmount
    let isGramsExpanded: Bool
    let onToggleGrams: () -> Void
    let onPortionSelect: (Double) -> Void
    let onGramsChanged: (Double?) -> Void

    @State private var gramsText: String = ""

    private let portionOptions: [(Double, String)] = [
        (0.0, "没吃"),
        (0.25, "¼份"),
        (0.5, "半份"),
        (0.75, "¾份"),
        (1.0, "1份")
    ]

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            // Dish title & info
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(dish.name)
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundColor(M3E.Colors.onSurface)

                    HStack(spacing: 6) {
                        if let std = dish.standardServingGrams {
                            Text("标准份 \(Int(std))g")
                        }
                        if let kcal = dish.nutritionPerServing?.energyKcal {
                            Text("· \(Int(kcal)) 千卡/份")
                        }
                    }
                    .font(.system(size: 12))
                    .foregroundColor(M3E.Colors.onSurfaceVariant)
                }

                Spacer()

                if let grams = amount.consumedGrams, grams > 0 {
                    Text("\(Int(grams))g")
                        .font(.system(size: 13, weight: .bold, design: .rounded))
                        .foregroundColor(M3E.Colors.primary)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(M3E.Colors.primaryContainer)
                        .clipShape(Capsule())
                }
            }

            // Portion selection chips
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(portionOptions, id: \.0) { option in
                        let selected = abs(amount.servingMultiplier - option.0) < 0.01
                        Button {
                            onPortionSelect(option.0)
                        } label: {
                            Text(option.1)
                                .font(.system(size: 13, weight: selected ? .bold : .medium))
                                .padding(.horizontal, 14)
                                .padding(.vertical, 7)
                                .background(selected ? M3E.Colors.secondaryContainer : M3E.Colors.surfaceContainerHigh)
                                .foregroundColor(selected ? M3E.Colors.onSecondaryContainer : M3E.Colors.onSurface)
                                .clipShape(Capsule())
                        }
                    }
                }
            }

            // Expandable Grams input
            HStack {
                Button(action: onToggleGrams) {
                    HStack(spacing: 4) {
                        Image(systemName: "pencil")
                            .font(.system(size: 11))
                        Text(isGramsExpanded ? "收起精确克数" : "需要时精确到克")
                            .font(.system(size: 12))
                    }
                    .foregroundColor(M3E.Colors.primary)
                }

                Spacer()
            }

            if isGramsExpanded {
                HStack(spacing: 8) {
                    TextField(
                        "实际吃下的克数",
                        text: Binding(
                            get: {
                                if let g = amount.consumedGrams {
                                    return g == 0.0 ? "0" : "\(Int(g))"
                                }
                                return ""
                            },
                            set: { newValue in
                                onGramsChanged(Double(newValue))
                            }
                        )
                    )
                    #if canImport(UIKit)
                    .keyboardType(.numberPad)
                    #endif
                    .textFieldStyle(.plain)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .background(M3E.Colors.surfaceContainerLowest)
                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(M3E.Colors.outlineVariant, lineWidth: 1))

                    Text("克")
                        .font(.system(size: 13))
                        .foregroundColor(M3E.Colors.onSurfaceVariant)
                }
            }
        }
        .padding(14)
        .m3Card(backgroundColor: M3E.Colors.surfaceContainerLowest, cornerRadius: 20)
    }
}

// MARK: - Activity Helper Views

private struct ActivityMiniBox: View {
    let title: String
    let value: String

    var body: some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(title)
                .font(.system(size: 11))
                .foregroundColor(M3E.Colors.onSurfaceVariant)
            Text(value)
                .font(.system(size: 15, weight: .bold, design: .rounded))
                .foregroundColor(M3E.Colors.onSurface)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(10)
        .background(M3E.Colors.surfaceContainerLowest)
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
    }
}

private struct IntensityChip: View {
    let title: String
    let val: String
    @Binding var current: String

    var body: some View {
        Button {
            current = val
        } label: {
            Text(title)
                .font(.system(size: 13, weight: current == val ? .bold : .medium))
                .frame(maxWidth: .infinity)
                .padding(.vertical, 7)
                .background(current == val ? M3E.Colors.secondaryContainer : M3E.Colors.surfaceContainerHighest)
                .foregroundColor(current == val ? M3E.Colors.onSecondaryContainer : M3E.Colors.onSurface)
                .clipShape(Capsule())
        }
    }
}

// MARK: - Home Meal Draft Row

private struct HomeMealDraftRow: View {
    let item: HomeMealDraftItem
    let onNameChange: (String) -> Void
    let onGramsChange: (Double?) -> Void
    let onRemove: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                TextField("食物名称", text: Binding(
                    get: { item.name },
                    set: { onNameChange($0) }
                ))
                .font(.system(size: 14, weight: .semibold))
                .textFieldStyle(.plain)
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(M3E.Colors.surfaceContainerLowest)
                .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                .overlay(RoundedRectangle(cornerRadius: 10).stroke(M3E.Colors.outlineVariant, lineWidth: 1))

                Button(action: onRemove) {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundColor(M3E.Colors.outline)
                        .font(.system(size: 18))
                }
            }

            HStack(spacing: 8) {
                Text("估算克数:")
                    .font(.system(size: 12))
                    .foregroundColor(M3E.Colors.onSurfaceVariant)

                TextField("克数", text: Binding(
                    get: { item.grams != nil ? "\(Int(item.grams!))" : "" },
                    set: { onGramsChange(Double($0)) }
                ))
                #if canImport(UIKit)
                .keyboardType(.numberPad)
                #endif
                .font(.system(size: 13))
                .textFieldStyle(.plain)
                .frame(width: 80)
                .padding(.horizontal, 8)
                .padding(.vertical, 4)
                .background(M3E.Colors.surfaceContainerLowest)
                .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
                .overlay(RoundedRectangle(cornerRadius: 8).stroke(M3E.Colors.outlineVariant, lineWidth: 1))

                Text("g")
                    .font(.system(size: 12))
                    .foregroundColor(M3E.Colors.onSurfaceVariant)

                Spacer()

                if let nut = item.nutritionAtSource {
                    let factor = (item.sourceGrams != nil && item.sourceGrams! > 0 && item.grams != nil)
                        ? (item.grams! / item.sourceGrams!)
                        : 1.0
                    let kcal = Int((nut.energyKcal ?? 0.0) * factor)
                    Text("约 \(kcal) kcal")
                        .font(.system(size: 12, weight: .medium, design: .rounded))
                        .foregroundColor(M3E.Colors.primary)
                }
            }
        }
        .padding(12)
        .background(M3E.Colors.surfaceContainerLowest)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
    }
}
