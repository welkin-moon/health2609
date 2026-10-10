import Foundation

enum PrivateSummary {
    static func compose(date: String, campus: DailySummaryDto?, menu: TodayMenuDto?, records: [PrivateJournalRecord]) -> DailySummaryDto {
        var nutrition = NutritionSummaryDto()
        func add(_ value: NutritionDto, multiplier: Double = 1) {
            nutrition.energyKcal += (value.energyKcal ?? 0) * multiplier
            nutrition.proteinG += (value.proteinG ?? 0) * multiplier
            nutrition.fatG += (value.fatG ?? 0) * multiplier
            nutrition.carbohydrateG += (value.carbohydrateG ?? 0) * multiplier
            nutrition.fiberG += (value.fiberG ?? 0) * multiplier
            nutrition.sodiumMg += (value.sodiumMg ?? 0) * multiplier
            nutrition.sugarG += (value.sugarG ?? 0) * multiplier
            nutrition.saturatedFatG += (value.saturatedFatG ?? 0) * multiplier
        }
        // Only saved campus lunch portions enter the nutrition base. A demo server's
        // household meals/manual exercise/preferences are never private account records.
        if menu?.date == date {
            for dish in menu?.dishes ?? [] {
                let multiplier = dish.savedServingMultiplier ?? NutritionMath.calculateGramsMultiplier(consumedGrams: dish.savedConsumedGrams, standardServingGrams: dish.standardServingGrams) ?? 0
                if let value = dish.nutritionPerServing { add(value, multiplier: multiplier) }
            }
        }
        var activity = ActivitySummaryDto()
        activity.peMinutes = campus?.date == date ? campus?.activity.peMinutes ?? 0 : 0
        activity.targetMinutes = campus?.activity.targetMinutes ?? 120
        var reference: Int?
        var wearable = OutsideSchoolActivityRequest(date: date, exerciseMinutes: 0)
        for record in records where !record.deleted {
            switch record.entityType {
            case "home_meal":
                if let meal = try? record.decode(ConfirmedHomeMealRequest.self), meal.date == date {
                    for item in meal.items { if let value = item.nutrition { add(value) } }
                }
            case "manual_activity":
                if let value = try? record.decode(ManualActivityRequest.self), value.date == date,
                   (1...600).contains(value.durationMinutes) {
                    activity.manualOutsideMinutes += value.durationMinutes
                    switch value.intensity {
                    case "light": activity.intensityMinutes.light += value.durationMinutes
                    case "vigorous": activity.intensityMinutes.vigorous += value.durationMinutes
                    default: activity.intensityMinutes.moderate += value.durationMinutes
                    }
                    activity.manuallyEstimatedActiveEnergyKcal += value.estimatedActiveEnergyKcal ?? 0
                }
            case "preference":
                if record.entityId == "energy_reference", let value = try? record.decode(EnergyReferenceRequest.self) {
                    reference = value.dailyEnergyReferenceKcal
                }
            case "outside_activity":
                if let value = try? record.decode(OutsideSchoolActivityRequest.self), value.date == date { wearable = value }
            default: break // Preserve unsupported entities in the journal for future clients.
            }
        }
        activity.healthConnectOutsideMinutes = wearable.exerciseMinutes
        activity.activeEnergyKcal = wearable.activeEnergyKcal
        activity.outsideMinutes = max(activity.manualOutsideMinutes, wearable.exerciseMinutes)
        activity.totalMinutes = activity.peMinutes + activity.outsideMinutes
        activity.targetReached = activity.totalMinutes >= activity.targetMinutes
        let macroEnergy = nutrition.proteinG * 4 + nutrition.fatG * 9 + nutrition.carbohydrateG * 4
        if macroEnergy > 0 {
            nutrition.macroCompositionPercent = MacroCompositionDto(protein: nutrition.proteinG * 4 / macroEnergy * 100,
                fat: nutrition.fatG * 9 / macroEnergy * 100, carbohydrate: nutrition.carbohydrateG * 4 / macroEnergy * 100)
        }
        let energy = EnergySummaryDto(dailyEnergyReferenceKcal: reference, intakeKcal: nutrition.energyKcal,
            referenceGapKcal: reference.map { Double($0) - nutrition.energyKcal })
        return DailySummaryDto(date: date, nutrition: nutrition, activity: activity, energy: energy)
    }
}
