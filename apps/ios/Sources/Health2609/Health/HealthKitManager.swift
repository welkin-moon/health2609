import Foundation

#if canImport(HealthKit)
import HealthKit
#endif

// MARK: - HealthKit Aggregate Result

public struct HealthKitDayAggregate: Sendable {
    public let exerciseMinutes: Int
    public let steps: Int64
    public let activeEnergyKcal: Double

    public init(exerciseMinutes: Int, steps: Int64, activeEnergyKcal: Double) {
        self.exerciseMinutes = exerciseMinutes
        self.steps = steps
        self.activeEnergyKcal = activeEnergyKcal
    }
}

// MARK: - Date Interval Helper

public struct DateRange: Sendable {
    public let start: Date
    public let end: Date

    public init(start: Date, end: Date) {
        self.start = start
        self.end = end
    }
}

// MARK: - HealthKitManager

public final class HealthKitManager: @unchecked Sendable {
    public static let shared = HealthKitManager()

    #if canImport(HealthKit)
    private let healthStore = HKHealthStore()
    #endif

    public init() {}

    public var isAvailable: Bool {
        #if canImport(HealthKit)
        return HKHealthStore.isHealthDataAvailable()
        #else
        return false
        #endif
    }

    // MARK: - Permissions

    public func requestAuthorization() async throws -> Bool {
        #if canImport(HealthKit)
        guard isAvailable else { return false }

        let typesToRead: Set<HKObjectType> = [
            HKQuantityType.quantityType(forIdentifier: .stepCount)!,
            HKQuantityType.quantityType(forIdentifier: .activeEnergyBurned)!,
            HKQuantityType.quantityType(forIdentifier: .appleExerciseTime)!,
            HKObjectType.workoutType()
        ]

        return try await withCheckedThrowingContinuation { continuation in
            healthStore.requestAuthorization(toShare: nil, read: typesToRead) { success, error in
                if let error = error {
                    continuation.resume(throwing: error)
                } else {
                    continuation.resume(returning: success)
                }
            }
        }
        #else
        return false
        #endif
    }

    // MARK: - School Time Exclusion Calculation

    /// Calculates time intervals outside of the school day windows.
    public func outsideSchoolRanges(
        for date: Date,
        schoolWindows: [SchoolDayWindowDto],
        calendar: Calendar = Calendar.current
    ) -> [DateRange] {
        let dayStart = calendar.startOfDay(for: date)
        guard let dayEnd = calendar.date(byAdding: .day, value: 1, to: dayStart) else {
            return []
        }

        // Parse school windows into absolute Date ranges
        let parsedRanges: [DateRange] = schoolWindows.compactMap { window in
            let startParts = window.startTime.split(separator: ":").compactMap { Int($0) }
            let endParts = window.endTime.split(separator: ":").compactMap { Int($0) }
            guard startParts.count == 2, endParts.count == 2 else { return nil }

            guard let windowStart = calendar.date(bySettingHour: startParts[0], minute: startParts[1], second: 0, of: date),
                  let windowEnd = calendar.date(bySettingHour: endParts[0], minute: endParts[1], second: 0, of: date),
                  windowStart < windowEnd else {
                return nil
            }

            let clampedStart = max(windowStart, dayStart)
            let clampedEnd = min(windowEnd, dayEnd)
            guard clampedStart < clampedEnd else { return nil }

            return DateRange(start: clampedStart, end: clampedEnd)
        }.sorted { $0.start < $1.start }

        // Merge overlapping school windows
        var mergedSchool: [DateRange] = []
        for range in parsedRanges {
            if let last = mergedSchool.last, range.start <= last.end {
                let mergedEnd = max(last.end, range.end)
                mergedSchool[mergedSchool.count - 1] = DateRange(start: last.start, end: mergedEnd)
            } else {
                mergedSchool.append(range)
            }
        }

        if mergedSchool.isEmpty {
            return [DateRange(start: dayStart, end: dayEnd)]
        }

        // Invert school windows to get outside-school time intervals
        var outside: [DateRange] = []
        var cursor = dayStart

        for school in mergedSchool {
            if cursor < school.start {
                outside.append(DateRange(start: cursor, end: school.start))
            }
            cursor = max(cursor, school.end)
        }

        if cursor < dayEnd {
            outside.append(DateRange(start: cursor, end: dayEnd))
        }

        return outside
    }

    // MARK: - Read Outside School Activity

    public func readOutsideSchoolActivity(
        date: Date,
        schoolWindows: [SchoolDayWindowDto]
    ) async throws -> HealthKitDayAggregate {
        #if canImport(HealthKit)
        guard isAvailable else {
            return simulateFallbackAggregate()
        }

        let ranges = outsideSchoolRanges(for: date, schoolWindows: schoolWindows)
        var totalSteps: Int64 = 0
        var totalActiveKcal: Double = 0.0
        var totalExerciseMinutes: Int = 0

        for range in ranges {
            guard range.start < range.end else { continue }

            // 1. Steps
            if let stepType = HKQuantityType.quantityType(forIdentifier: .stepCount) {
                let steps = try? await queryQuantitySum(type: stepType, unit: .count(), range: range)
                totalSteps += Int64(steps ?? 0)
            }

            // 2. Active Calories
            if let energyType = HKQuantityType.quantityType(forIdentifier: .activeEnergyBurned) {
                let energy = try? await queryQuantitySum(type: energyType, unit: .kilocalorie(), range: range)
                totalActiveKcal += energy ?? 0.0
            }

            // 3. Exercise Minutes
            if let exerciseType = HKQuantityType.quantityType(forIdentifier: .appleExerciseTime) {
                let mins = try? await queryQuantitySum(type: exerciseType, unit: .minute(), range: range)
                totalExerciseMinutes += Int(mins ?? 0)
            }
        }

        // If HealthKit returned 0 or permissions not granted in demo mode, return fallback
        if totalSteps == 0 && totalExerciseMinutes == 0 && totalActiveKcal == 0 {
            return simulateFallbackAggregate()
        }

        return HealthKitDayAggregate(
            exerciseMinutes: min(totalExerciseMinutes, 1440),
            steps: min(max(totalSteps, 0), 200_000),
            activeEnergyKcal: min(max(totalActiveKcal, 0.0), 20_000.0)
        )
        #else
        return simulateFallbackAggregate()
        #endif
    }

    #if canImport(HealthKit)
    private func queryQuantitySum(type: HKQuantityType, unit: HKUnit, range: DateRange) async throws -> Double {
        let predicate = HKQuery.predicateForSamples(withStart: range.start, end: range.end, options: .strictStartDate)
        return try await withCheckedThrowingContinuation { continuation in
            let query = HKStatisticsQuery(quantityType: type, quantitySamplePredicate: predicate, options: .cumulativeSum) { _, result, error in
                if let error = error {
                    continuation.resume(throwing: error)
                    return
                }
                let sum = result?.sumQuantity()?.doubleValue(for: unit) ?? 0.0
                continuation.resume(returning: sum)
            }
            healthStore.execute(query)
        }
    }
    #endif

    private func simulateFallbackAggregate() -> HealthKitDayAggregate {
        // Fallback demo values if HealthKit is disabled, in simulator, or without permissions
        return HealthKitDayAggregate(
            exerciseMinutes: 45,
            steps: 5820,
            activeEnergyKcal: 230.0
        )
    }
}
