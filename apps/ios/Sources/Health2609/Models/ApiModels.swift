import Foundation

// MARK: - Nutrition Data Transfer Objects

public struct NutritionDto: Codable, Hashable, Sendable {
    public var energyKcal: Double?
    public var proteinG: Double?
    public var fatG: Double?
    public var carbohydrateG: Double?
    public var fiberG: Double?
    public var sodiumMg: Double?
    public var sugarG: Double?
    public var saturatedFatG: Double?

    public init(
        energyKcal: Double? = nil,
        proteinG: Double? = nil,
        fatG: Double? = nil,
        carbohydrateG: Double? = nil,
        fiberG: Double? = nil,
        sodiumMg: Double? = nil,
        sugarG: Double? = nil,
        saturatedFatG: Double? = nil
    ) {
        self.energyKcal = energyKcal
        self.proteinG = proteinG
        self.fatG = fatG
        self.carbohydrateG = carbohydrateG
        self.fiberG = fiberG
        self.sodiumMg = sodiumMg
        self.sugarG = sugarG
        self.saturatedFatG = saturatedFatG
    }
}

// MARK: - Dish & Menu Data Transfer Objects

public struct DishDto: Codable, Identifiable, Hashable, Sendable {
    public var id: String
    public var name: String
    public var standardServingGrams: Double?
    public var nutritionPerServing: NutritionDto?

    public init(
        id: String,
        name: String,
        standardServingGrams: Double? = nil,
        nutritionPerServing: NutritionDto? = nil
    ) {
        self.id = id
        self.name = name
        self.standardServingGrams = standardServingGrams
        self.nutritionPerServing = nutritionPerServing
    }
}

public struct TodayMenuDto: Codable, Hashable, Sendable {
    public var date: String
    public var mealSlot: String
    public var dishes: [DishDto]

    public init(date: String, mealSlot: String, dishes: [DishDto]) {
        self.date = date
        self.mealSlot = mealSlot
        self.dishes = dishes
    }
}

// MARK: - Meal Consumption Requests

public struct MealItemRequest: Codable, Hashable, Sendable {
    public var dishId: String
    public var servingMultiplier: Double?
    public var consumedGrams: Double?

    public init(dishId: String, servingMultiplier: Double? = nil, consumedGrams: Double? = nil) {
        self.dishId = dishId
        self.servingMultiplier = servingMultiplier
        self.consumedGrams = consumedGrams
    }
}

public struct MealConsumptionRequest: Codable, Hashable, Sendable {
    public var date: String
    public var mealSlot: String
    public var items: [MealItemRequest]

    public init(date: String, mealSlot: String = "lunch", items: [MealItemRequest]) {
        self.date = date
        self.mealSlot = mealSlot
        self.items = items
    }
}

// MARK: - School Day & PE Windows

public struct SchoolDayWindowDto: Codable, Identifiable, Hashable, Sendable {
    public var id: String
    public var startTime: String
    public var endTime: String

    public init(id: String, startTime: String, endTime: String) {
        self.id = id
        self.startTime = startTime
        self.endTime = endTime
    }
}

public struct SchoolDayWindowsDto: Codable, Hashable, Sendable {
    public var date: String
    public var weekday: Int
    public var windows: [SchoolDayWindowDto]

    public init(date: String, weekday: Int, windows: [SchoolDayWindowDto]) {
        self.date = date
        self.weekday = weekday
        self.windows = windows
    }
}

// MARK: - Physical Activity Requests

public struct OutsideSchoolActivityRequest: Codable, Hashable, Sendable {
    public var date: String
    public var exerciseMinutes: Int
    public var steps: Int64?
    public var activeEnergyKcal: Double?

    public init(date: String, exerciseMinutes: Int, steps: Int64? = nil, activeEnergyKcal: Double? = nil) {
        self.date = date
        self.exerciseMinutes = exerciseMinutes
        self.steps = steps
        self.activeEnergyKcal = activeEnergyKcal
    }
}

public struct ManualActivityRequest: Codable, Hashable, Sendable {
    public var date: String
    public var activityType: String
    public var startTime: String?
    public var durationMinutes: Int
    public var intensity: String
    public var estimatedActiveEnergyKcal: Double?

    public init(
        date: String,
        activityType: String,
        startTime: String? = nil,
        durationMinutes: Int,
        intensity: String,
        estimatedActiveEnergyKcal: Double? = nil
    ) {
        self.date = date
        self.activityType = activityType
        self.startTime = startTime
        self.durationMinutes = durationMinutes
        self.intensity = intensity
        self.estimatedActiveEnergyKcal = estimatedActiveEnergyKcal
    }
}

public struct EnergyReferenceRequest: Codable, Hashable, Sendable {
    public var dailyEnergyReferenceKcal: Int?

    public init(dailyEnergyReferenceKcal: Int?) {
        self.dailyEnergyReferenceKcal = dailyEnergyReferenceKcal
    }
}

// MARK: - Summary & Analytics DTOs

public struct MacroCompositionDto: Codable, Hashable, Sendable {
    public var protein: Double
    public var fat: Double
    public var carbohydrate: Double

    public init(protein: Double = 0.0, fat: Double = 0.0, carbohydrate: Double = 0.0) {
        self.protein = protein
        self.fat = fat
        self.carbohydrate = carbohydrate
    }
}

public struct NutritionSummaryDto: Codable, Hashable, Sendable {
    public var energyKcal: Double
    public var proteinG: Double
    public var fatG: Double
    public var carbohydrateG: Double
    public var fiberG: Double
    public var sodiumMg: Double
    public var sugarG: Double
    public var saturatedFatG: Double
    public var macroCompositionPercent: MacroCompositionDto

    public init(
        energyKcal: Double = 0.0,
        proteinG: Double = 0.0,
        fatG: Double = 0.0,
        carbohydrateG: Double = 0.0,
        fiberG: Double = 0.0,
        sodiumMg: Double = 0.0,
        sugarG: Double = 0.0,
        saturatedFatG: Double = 0.0,
        macroCompositionPercent: MacroCompositionDto = MacroCompositionDto()
    ) {
        self.energyKcal = energyKcal
        self.proteinG = proteinG
        self.fatG = fatG
        self.carbohydrateG = carbohydrateG
        self.fiberG = fiberG
        self.sodiumMg = sodiumMg
        self.sugarG = sugarG
        self.saturatedFatG = saturatedFatG
        self.macroCompositionPercent = macroCompositionPercent
    }
}

public struct IntensityMinutesDto: Codable, Hashable, Sendable {
    public var light: Int
    public var moderate: Int
    public var vigorous: Int

    public init(light: Int = 0, moderate: Int = 0, vigorous: Int = 0) {
        self.light = light
        self.moderate = moderate
        self.vigorous = vigorous
    }
}

public struct ActivitySummaryDto: Codable, Hashable, Sendable {
    public var peMinutes: Int
    public var healthConnectOutsideMinutes: Int
    public var manualOutsideMinutes: Int
    public var outsideMinutes: Int
    public var totalMinutes: Int
    public var targetMinutes: Int
    public var targetReached: Bool
    public var intensityMinutes: IntensityMinutesDto
    public var activeEnergyKcal: Double?
    public var manuallyEstimatedActiveEnergyKcal: Double

    public init(
        peMinutes: Int = 0,
        healthConnectOutsideMinutes: Int = 0,
        manualOutsideMinutes: Int = 0,
        outsideMinutes: Int = 0,
        totalMinutes: Int = 0,
        targetMinutes: Int = 120,
        targetReached: Bool = false,
        intensityMinutes: IntensityMinutesDto = IntensityMinutesDto(),
        activeEnergyKcal: Double? = nil,
        manuallyEstimatedActiveEnergyKcal: Double = 0.0
    ) {
        self.peMinutes = peMinutes
        self.healthConnectOutsideMinutes = healthConnectOutsideMinutes
        self.manualOutsideMinutes = manualOutsideMinutes
        self.outsideMinutes = outsideMinutes
        self.totalMinutes = totalMinutes
        self.targetMinutes = targetMinutes
        self.targetReached = targetReached
        self.intensityMinutes = intensityMinutes
        self.activeEnergyKcal = activeEnergyKcal
        self.manuallyEstimatedActiveEnergyKcal = manuallyEstimatedActiveEnergyKcal
    }
}

public struct EnergySummaryDto: Codable, Hashable, Sendable {
    public var dailyEnergyReferenceKcal: Int?
    public var intakeKcal: Double
    public var referenceGapKcal: Double?

    public init(
        dailyEnergyReferenceKcal: Int? = nil,
        intakeKcal: Double = 0.0,
        referenceGapKcal: Double? = nil
    ) {
        self.dailyEnergyReferenceKcal = dailyEnergyReferenceKcal
        self.intakeKcal = intakeKcal
        self.referenceGapKcal = referenceGapKcal
    }
}

public struct DailySummaryDto: Codable, Hashable, Sendable {
    public var date: String
    public var nutrition: NutritionSummaryDto
    public var activity: ActivitySummaryDto
    public var energy: EnergySummaryDto

    public init(
        date: String,
        nutrition: NutritionSummaryDto = NutritionSummaryDto(),
        activity: ActivitySummaryDto = ActivitySummaryDto(),
        energy: EnergySummaryDto = EnergySummaryDto()
    ) {
        self.date = date
        self.nutrition = nutrition
        self.activity = activity
        self.energy = energy
    }
}

// MARK: - Home Meal AI Analysis DTOs

public struct HomeMealAnalysisItemDto: Codable, Hashable, Sendable {
    public var name: String
    public var estimatedGrams: Double?
    public var servingMultiplier: Double?
    public var confidence: Double
    public var nutrition: NutritionDto?
    public var needsConfirmation: [String]

    public init(
        name: String,
        estimatedGrams: Double? = nil,
        servingMultiplier: Double? = nil,
        confidence: Double = 0.0,
        nutrition: NutritionDto? = nil,
        needsConfirmation: [String] = []
    ) {
        self.name = name
        self.estimatedGrams = estimatedGrams
        self.servingMultiplier = servingMultiplier
        self.confidence = confidence
        self.nutrition = nutrition
        self.needsConfirmation = needsConfirmation
    }
}

public struct HomeMealAnalysisResultDto: Codable, Hashable, Sendable {
    public var schemaVersion: Int
    public var items: [HomeMealAnalysisItemDto]
    public var notes: [String]

    public init(
        schemaVersion: Int = 1,
        items: [HomeMealAnalysisItemDto] = [],
        notes: [String] = []
    ) {
        self.schemaVersion = schemaVersion
        self.items = items
        self.notes = notes
    }
}

public struct ConfirmedHomeMealItemRequest: Codable, Hashable, Sendable {
    public var name: String
    public var grams: Double?
    public var nutrition: NutritionDto?

    public init(name: String, grams: Double?, nutrition: NutritionDto? = nil) {
        self.name = name
        self.grams = grams
        self.nutrition = nutrition
    }
}

public struct ConfirmedHomeMealRequest: Codable, Hashable, Sendable {
    public var date: String
    public var mealSlot: String
    public var items: [ConfirmedHomeMealItemRequest]

    public init(date: String, mealSlot: String = "dinner", items: [ConfirmedHomeMealItemRequest]) {
        self.date = date
        self.mealSlot = mealSlot
        self.items = items
    }
}

public struct ApiWriteResult: Codable, Hashable, Sendable {
    public var ok: Bool
    public var id: String?

    public init(ok: Bool, id: String? = nil) {
        self.ok = ok
        self.id = id
    }
}

// MARK: - UI Domain Helper Models

public struct DishAmount: Hashable, Sendable {
    public var servingMultiplier: Double
    public var consumedGrams: Double?

    public init(servingMultiplier: Double = 0.0, consumedGrams: Double? = nil) {
        self.servingMultiplier = servingMultiplier
        self.consumedGrams = consumedGrams
    }
}

public struct HomeMealDraftItem: Identifiable, Hashable, Sendable {
    public var id: UUID
    public var name: String
    public var sourceGrams: Double?
    public var grams: Double?
    public var confidence: Double
    public var nutritionAtSource: NutritionDto?
    public var needsConfirmation: [String]

    public init(
        id: UUID = UUID(),
        name: String,
        sourceGrams: Double? = nil,
        grams: Double? = nil,
        confidence: Double = 0.0,
        nutritionAtSource: NutritionDto? = nil,
        needsConfirmation: [String] = []
    ) {
        self.id = id
        self.name = name
        self.sourceGrams = sourceGrams
        self.grams = grams
        self.confidence = confidence
        self.nutritionAtSource = nutritionAtSource
        self.needsConfirmation = needsConfirmation
    }
}

// MARK: - Deterministic Nutrition Math (Unified with @health2609/contracts)

public enum NutritionMath {
    /// Deterministically calculate nutrition based on portion multiplier.
    public static func calculateServingNutrition(nutrition: NutritionDto?, multiplier: Double) -> NutritionDto {
        let m = max(0.0, multiplier)
        guard let nutrition = nutrition else {
            return NutritionDto(
                energyKcal: 0,
                proteinG: 0,
                fatG: 0,
                carbohydrateG: 0,
                fiberG: 0,
                sodiumMg: 0,
                sugarG: 0,
                saturatedFatG: 0
            )
        }
        func round2(_ val: Double?) -> Double {
            round((val ?? 0.0) * m * 100) / 100
        }
        return NutritionDto(
            energyKcal: round2(nutrition.energyKcal),
            proteinG: round2(nutrition.proteinG),
            fatG: round2(nutrition.fatG),
            carbohydrateG: round2(nutrition.carbohydrateG),
            fiberG: round2(nutrition.fiberG),
            sodiumMg: round2(nutrition.sodiumMg),
            sugarG: round2(nutrition.sugarG),
            saturatedFatG: round2(nutrition.saturatedFatG)
        )
    }

    /// Macro energy formula: 4 kcal/g protein, 9 kcal/g fat, 4 kcal/g carbs.
    public static func calculateMacroEnergy(proteinG: Double?, fatG: Double?, carbohydrateG: Double?) -> Double {
        let p = max(0.0, proteinG ?? 0.0)
        let f = max(0.0, fatG ?? 0.0)
        let c = max(0.0, carbohydrateG ?? 0.0)
        return round((p * 4.0 + f * 9.0 + c * 4.0) * 100) / 100
    }

    /// Calculate multiplier from consumed grams and standard serving grams.
    public static func calculateGramsMultiplier(consumedGrams: Double?, standardServingGrams: Double?) -> Double? {
        guard let grams = consumedGrams, let std = standardServingGrams, std > 0, grams >= 0 else {
            return nil
        }
        return round((grams / std) * 1000) / 1000
    }

    /// Scale nutrition from source grams to confirmed grams.
    public static func scaledNutrition(nutrition: NutritionDto?, sourceGrams: Double?, confirmedGrams: Double?) -> NutritionDto? {
        guard let nutrition = nutrition else { return nil }
        guard let source = sourceGrams, source > 0, let confirmed = confirmedGrams else {
            return nutrition
        }
        let factor = min(max(confirmed / source, 0.0), 10.0)
        func scale(_ val: Double?) -> Double? {
            guard let val = val else { return nil }
            return round(val * factor * 100) / 100
        }
        return NutritionDto(
            energyKcal: scale(nutrition.energyKcal),
            proteinG: scale(nutrition.proteinG),
            fatG: scale(nutrition.fatG),
            carbohydrateG: scale(nutrition.carbohydrateG),
            fiberG: scale(nutrition.fiberG),
            sodiumMg: scale(nutrition.sodiumMg),
            sugarG: scale(nutrition.sugarG),
            saturatedFatG: scale(nutrition.saturatedFatG)
        )
    }
}
