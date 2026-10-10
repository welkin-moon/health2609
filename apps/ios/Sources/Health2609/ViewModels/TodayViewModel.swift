import Foundation
import SwiftUI
import Combine
#if canImport(UIKit)
import UIKit
#endif

public struct LunchTotals: Sendable {
    public var energyKcal: Double
    public var proteinG: Double
    public var fatG: Double
    public var carbohydrateG: Double

    public init(energyKcal: Double = 0.0, proteinG: Double = 0.0, fatG: Double = 0.0, carbohydrateG: Double = 0.0) {
        self.energyKcal = energyKcal
        self.proteinG = proteinG
        self.fatG = fatG
        self.carbohydrateG = carbohydrateG
    }
}

public struct NextActionTip: Sendable {
    public let title: String
    public let message: String
    public let iconName: String
    public let tag: String

    public init(title: String, message: String, iconName: String, tag: String) {
        self.title = title
        self.message = message
        self.iconName = iconName
        self.tag = tag
    }
}

@MainActor
public final class TodayViewModel: ObservableObject {
    @Published public var date: String
    @Published public var loading: Bool = true
    @Published public var savingMeal: Bool = false
    @Published public var savingActivity: Bool = false
    @Published public var savingEnergyReference: Bool = false
    @Published public var syncingPhoneActivity: Bool = false
    @Published public var analyzingHomeMeal: Bool = false
    @Published public var savingHomeMeal: Bool = false

    @Published public var menu: TodayMenuDto? = nil
    @Published public var summary: DailySummaryDto? = nil
    @Published public var schoolWindows: [SchoolDayWindowDto] = []
    @Published public var amounts: [String: DishAmount] = [:]

    @Published public var manualActivityType: String = "自主运动"
    @Published public var manualActivityMinutes: Int = 30
    @Published public var manualActivityIntensity: String = "moderate"

    @Published public var energyReferenceInput: String = ""
    @Published public var homeMealSlot: String = "dinner"
    @Published public var homeMealDraft: [HomeMealDraftItem] = []
    @Published public var homeMealNotes: [String] = []
    @Published public var message: String? = nil

    private let api: HealthApiClient
    private let healthKit: HealthKitManager
    private var refreshTask: Task<Void, Never>?
    private var windowsLoaded = false

    public init(
        api: HealthApiClient = HealthApi.shared,
        healthKit: HealthKitManager = HealthKitManager.shared
    ) {
        self.api = api
        self.healthKit = healthKit

        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(identifier: "Asia/Shanghai")
        formatter.dateFormat = "yyyy-MM-dd"
        self.date = formatter.string(from: Date())

        refresh()
    }

    // MARK: - Computed Properties

    public var formattedDate: String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(identifier: "Asia/Shanghai")
        formatter.dateFormat = "yyyy-MM-dd"
        guard let parsed = formatter.date(from: date) else { return date }

        let displayFormatter = DateFormatter()
        displayFormatter.timeZone = TimeZone(identifier: "Asia/Shanghai")
        displayFormatter.locale = Locale(identifier: "zh_CN")
        displayFormatter.dateFormat = "M月d日 EEEE"
        return displayFormatter.string(from: parsed)
    }

    public var lunchPreview: LunchTotals {
        guard let dishes = menu?.dishes else { return LunchTotals() }
        var totalKcal: Double = 0.0
        var totalProtein: Double = 0.0
        var totalFat: Double = 0.0
        var totalCarbs: Double = 0.0

        for dish in dishes {
            let amount = amounts[dish.id] ?? DishAmount()
            let m = amount.servingMultiplier
            if let nut = dish.nutritionPerServing {
                totalKcal += (nut.energyKcal ?? 0.0) * m
                totalProtein += (nut.proteinG ?? 0.0) * m
                totalFat += (nut.fatG ?? 0.0) * m
                totalCarbs += (nut.carbohydrateG ?? 0.0) * m
            }
        }

        func round1(_ v: Double) -> Double { (v * 10).rounded() / 10 }
        return LunchTotals(
            energyKcal: round1(totalKcal),
            proteinG: round1(totalProtein),
            fatG: round1(totalFat),
            carbohydrateG: round1(totalCarbs)
        )
    }

    public var nextActionTip: NextActionTip {
        let totalExercise = summary?.activity.totalMinutes ?? 0
        let target = max(1, summary?.activity.targetMinutes ?? 120)
        let intakeKcal = summary?.nutrition.energyKcal ?? 0.0

        if totalExercise == 0 && intakeKcal == 0 {
            return NextActionTip(
                title: "开始今天的记录",
                message: "记录午餐和下午运动后，系统将依据《中国学龄儿童膳食指南》为你量身定制个性化行动建议。",
                iconName: "sparkles",
                tag: "指南建议"
            )
        }

        if totalExercise < target {
            let remaining = target - totalExercise
            return NextActionTip(
                title: "运动冲刺建议",
                message: "距离每日 120 分钟运动目标还差 \(remaining) 分钟。建议放学后进行 15 分钟跳绳或 20 分钟快走，保持活力！",
                iconName: "figure.run",
                tag: "运动目标"
            )
        } else {
            return NextActionTip(
                title: "运动达标与能量补给",
                message: "太棒了！今日已达成 120 分钟运动目标（累计 \(totalExercise) 分钟）。晚餐建议搭配清淡鱼虾或豆制品，补充优质蛋白并充分饮水。",
                iconName: "checkmark.seal.fill",
                tag: "均衡达标"
            )
        }
    }

    // MARK: - Actions

    public func refresh() {
        refreshTask?.cancel()
        loading = true
        windowsLoaded = false
        message = nil
        let requestedDate = date

        refreshTask = Task {
            do {
                async let menuTask = api.todayMenu(date: date, mealSlot: "lunch")
                async let summaryTask = api.todaySummary(date: date)
                async let windowsTask = api.schoolDayWindows(date: date)

                let (menuResult, summaryResult, windowsResult) = try await (menuTask, summaryTask, windowsTask)

                guard !Task.isCancelled, self.date == requestedDate else { return }
                self.windowsLoaded = true
                self.menu = menuResult
                self.summary = summaryResult
                self.schoolWindows = windowsResult.windows

                self.energyReferenceInput = summaryResult.energy.dailyEnergyReferenceKcal.map { String($0) } ?? ""

                // Initialize amounts for each dish
                for dish in menuResult.dishes {
                    if self.amounts[dish.id] == nil {
                        self.amounts[dish.id] = DishAmount(servingMultiplier: dish.savedServingMultiplier ?? 0.0, consumedGrams: dish.savedConsumedGrams)
                    }
                }

                self.loading = false
            } catch {
                guard !Task.isCancelled else { return }
                self.loading = false
                self.message = error.localizedDescription
            }
        }
    }

    public func reloadIdentity() {
        amounts = [:]
        menu = nil
        summary = nil
        schoolWindows = []
        homeMealDraft = []
        homeMealNotes = []
        refresh()
    }

    public func setPortion(dishId: String, portion: Double) {
        let portion = portion.isFinite ? min(max(portion, 0), 5) : 0
        let dish = menu?.dishes.first(where: { $0.id == dishId })
        let grams: Double?
        if let std = dish?.standardServingGrams {
            grams = std * portion
        } else {
            grams = nil
        }
        amounts[dishId] = DishAmount(servingMultiplier: portion, consumedGrams: grams)
    }

    public func setConsumedGrams(dishId: String, grams: Double?) {
        let dish = menu?.dishes.first(where: { $0.id == dishId })
        let maxGrams = (dish?.standardServingGrams ?? 1000.0) * 5.0
        let safeGrams = grams.flatMap { $0.isFinite ? min(max($0, 0.0), maxGrams) : nil }

        let multiplier: Double
        if let safe = safeGrams, let std = dish?.standardServingGrams, std > 0 {
            multiplier = safe / std
        } else {
            multiplier = 0.0
        }

        amounts[dishId] = DishAmount(servingMultiplier: multiplier, consumedGrams: safeGrams)
    }

    public func saveMeal() {
        guard let menu = menu, !savingMeal else { return }
        savingMeal = true
        message = nil

        let items: [MealItemRequest] = menu.dishes.map { dish in
            let dishId = dish.id
            let amount = amounts[dishId] ?? DishAmount()
            return MealItemRequest(
                dishId: dishId,
                servingMultiplier: amount.servingMultiplier,
                consumedGrams: amount.consumedGrams
            )
        }

        Task {
            do {
                _ = try await api.saveMeal(request: MealConsumptionRequest(date: date, mealSlot: "lunch", items: items))
                await refreshSummary(successMessage: "今天的午餐已记录")
            } catch {
                self.savingMeal = false
                self.message = "午餐保存失败: \(error.localizedDescription)"
            }
        }
    }

    public func saveManualActivity(type: String = "自主运动") {
        guard !savingActivity else { return }
        savingActivity = true
        message = nil

        let chosenType = type.trimmingCharacters(in: .whitespaces).isEmpty ? manualActivityType : type

        Task {
            do {
                let req = ManualActivityRequest(
                    date: date,
                    activityType: chosenType,
                    durationMinutes: manualActivityMinutes,
                    intensity: manualActivityIntensity
                )
                _ = try await api.saveManualActivity(request: req)
                await refreshSummary(successMessage: "运动记录已加入今天")
            } catch {
                self.savingActivity = false
                self.message = "运动保存失败: \(error.localizedDescription)"
            }
        }
    }

    public func syncHealthKitActivity() {
        guard !syncingPhoneActivity, windowsLoaded, !loading else {
            message = "请先成功加载学校在校时段，再同步健康数据。"
            return
        }
        syncingPhoneActivity = true
        message = "正在读取健康数据并排除在校时段…"

        Task {
            do {
                guard try await healthKit.requestAuthorization() else {
                    throw HealthKitReadError.unavailable
                }

                let formatter = DateFormatter()
                formatter.locale = Locale(identifier: "en_US_POSIX")
                formatter.timeZone = TimeZone(identifier: "Asia/Shanghai")
                formatter.dateFormat = "yyyy-MM-dd"
                let targetDate = formatter.date(from: date) ?? Date()

                let aggregate = try await healthKit.readOutsideSchoolActivity(
                    date: targetDate,
                    schoolWindows: schoolWindows
                )

                let req = OutsideSchoolActivityRequest(
                    date: date,
                    exerciseMinutes: aggregate.exerciseMinutes,
                    steps: aggregate.steps,
                    activeEnergyKcal: aggregate.activeEnergyKcal
                )
                _ = try await api.saveOutsideSchoolActivity(request: req)
                await refreshSummary(successMessage: "Apple 健康校外运动已同步 (已排除在校时段)")
            } catch {
                self.syncingPhoneActivity = false
                self.message = "健康数据同步失败: \(error.localizedDescription)"
            }
        }
    }

    public func analyzeHomeMeal(imageData: Data) {
        guard !analyzingHomeMeal else { return }
        #if canImport(UIKit)
        guard let image = UIImage(data: imageData) else {
            message = "无法读取照片，请重新选择"
            return
        }
        let longest = max(image.size.width, image.size.height)
        let scale = min(1, 1600 / max(longest, 1))
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: image.size.width * scale, height: image.size.height * scale), format: format)
        let resized = renderer.image { _ in image.draw(in: CGRect(origin: .zero, size: CGSize(width: image.size.width * scale, height: image.size.height * scale))) }
        guard let jpegData = resized.jpegData(compressionQuality: 0.82) else { return }
        #else
        let jpegData = imageData
        #endif
        analyzingHomeMeal = true
        message = "正在智能识别餐食内容…"

        Task {
            do {
                let result = try await api.analyzeHomeMeal(imageData: jpegData, mimeType: "image/jpeg", fileName: "meal.jpg")
                self.homeMealDraft = result.items.map { item in
                    HomeMealDraftItem(
                        name: item.name,
                        sourceGrams: item.estimatedGrams,
                        grams: item.estimatedGrams,
                        confidence: item.confidence,
                        nutritionAtSource: item.nutrition,
                        needsConfirmation: item.needsConfirmation
                    )
                }
                self.homeMealNotes = result.notes
                self.analyzingHomeMeal = false
                self.message = self.homeMealDraft.isEmpty ? "未能识别出明确食物，可尝试重新拍摄或手动输入" : "识别完成，请核对名称和分量"
            } catch {
                self.analyzingHomeMeal = false
                self.message = "餐食识别失败: \(error.localizedDescription)"
            }
        }
    }

    public func setHomeMealName(index: Int, name: String) {
        guard homeMealDraft.indices.contains(index) else { return }
        homeMealDraft[index].name = name
    }

    public func setHomeMealGrams(index: Int, grams: Double?) {
        guard homeMealDraft.indices.contains(index) else { return }
        homeMealDraft[index].grams = grams.flatMap { $0.isFinite ? min(max($0, 0.0), 5000.0) : nil }
    }

    public func removeHomeMealItem(index: Int) {
        guard homeMealDraft.indices.contains(index) else { return }
        homeMealDraft.remove(at: index)
    }

    public func saveHomeMeal() {
        guard !savingHomeMeal, !homeMealDraft.isEmpty else { return }
        savingHomeMeal = true
        message = nil

        let confirmedItems: [ConfirmedHomeMealItemRequest] = homeMealDraft
            .filter { !$0.name.trimmingCharacters(in: .whitespaces).isEmpty }
            .map { item in
                let scaled = NutritionMath.scaledNutrition(
                    nutrition: item.nutritionAtSource,
                    sourceGrams: item.sourceGrams,
                    confirmedGrams: item.grams
                )
                return ConfirmedHomeMealItemRequest(
                    name: item.name.trimmingCharacters(in: .whitespaces),
                    grams: item.grams,
                    nutrition: scaled
                )
            }

        guard !confirmedItems.isEmpty else {
            savingHomeMeal = false
            message = "请至少保留一项有效食物"
            return
        }

        Task {
            do {
                let req = ConfirmedHomeMealRequest(
                    date: date,
                    mealSlot: homeMealSlot,
                    items: confirmedItems
                )
                _ = try await api.saveHomeMeal(request: req)
                self.homeMealDraft = []
                self.homeMealNotes = []
                await refreshSummary(successMessage: "家庭餐已成功确认并记入今日记录")
            } catch {
                self.savingHomeMeal = false
                self.message = "家庭餐保存失败: \(error.localizedDescription)"
            }
        }
    }

    public func saveEnergyReference() {
        guard !savingEnergyReference else { return }
        let trimmed = energyReferenceInput.trimmingCharacters(in: .whitespacesAndNewlines)
        let kcal: Int?
        if trimmed.isEmpty {
            kcal = nil
        } else if let value = Int(trimmed), (500...6000).contains(value) {
            kcal = value
        } else {
            message = "参考能量请输入 500–6000 kcal 之间的数值，留空可清除"
            return
        }

        savingEnergyReference = true
        message = nil

        Task {
            do {
                _ = try await api.saveEnergyReference(request: EnergyReferenceRequest(dailyEnergyReferenceKcal: kcal))
                await refreshSummary(successMessage: "每日参考能量已更新")
            } catch {
                self.savingEnergyReference = false
                self.message = "参考能量保存失败: \(error.localizedDescription)"
            }
        }
    }

    private func refreshSummary(successMessage: String) async {
        do {
            let updated = try await api.todaySummary(date: date)
            self.summary = updated
            self.savingMeal = false
            self.savingActivity = false
            self.savingHomeMeal = false
            self.savingEnergyReference = false
            self.syncingPhoneActivity = false
            self.message = successMessage
        } catch {
            self.savingMeal = false
            self.savingActivity = false
            self.savingHomeMeal = false
            self.savingEnergyReference = false
            self.syncingPhoneActivity = false
            self.message = "\(successMessage)，但汇总刷新失败"
        }
    }
}
