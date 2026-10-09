package uk.lunarlab.health2609

import org.junit.Assert.*
import org.junit.Test
import uk.lunarlab.health2609.core.network.*
import uk.lunarlab.health2609.core.storage.UserProfile
import uk.lunarlab.health2609.feature.today.energyBalance

class EnergyBalanceTest {
    private fun summary(intake: Double, unknown: Int = 0, foods: Int = 2, target: Int? = 1800) =
        DailySummaryDto("2026-10-07",
            nutrition = NutritionSummaryDto(recordedFoodItems = foods, unknownEnergyItems = unknown, energyKcal = intake),
            energy = EnergySummaryDto(dailyEnergyReferenceKcal = target, referenceGapKcal = target?.minus(intake)))

    @Test fun balanceUsesExpenditureRatherThanIntakeTarget() {
        val s = summary(1900.0)
        assertEquals("估算缺口 500 千卡", energyBalance(s, UserProfile(), 2400).status)
        assertEquals("估算盈余 500 千卡", energyBalance(s, UserProfile(), 1400).status)
        assertEquals("估算持平", energyBalance(s, UserProfile(), 1900).status)
        assertEquals(energyBalance(s, UserProfile(), 2400),
            energyBalance(s.copy(energy = EnergySummaryDto(dailyEnergyReferenceKcal = 3000)), UserProfile(), 2400))
    }

    @Test fun unknownNutritionProducesBoundsAndDoesNotClaimExactBalance() {
        assertEquals("缺口至多 500 千卡", energyBalance(summary(1900.0, unknown = 1), UserProfile(), 2400).status)
        assertEquals("盈余至少 500 千卡", energyBalance(summary(1900.0, unknown = 1), UserProfile(), 1400).status)
        assertEquals("已知部分持平 · 营养待补", energyBalance(summary(1900.0, unknown = 1), UserProfile(), 1900).status)
    }

    @Test fun missingRecordsOrEstimateHaveExplicitStates() {
        assertEquals("尚未记录餐食", energyBalance(summary(0.0, foods = 0), UserProfile(), 2400).status)
        assertEquals("正在读取记录", energyBalance(null, UserProfile(), 2400).status)
        assertEquals("消耗待估算", energyBalance(summary(1900.0), UserProfile(), null).status)
        assertEquals("估算缺口 2400 千卡", energyBalance(summary(0.0), UserProfile(), 2400).status)
    }

    @Test fun adultEstimateUsesSavedProfileAndManualValueOverridesIt() {
        val male = UserProfile(age = 25, gender = "male", heightCm = 180.0, weightKg = 70.0)
        val female = male.copy(gender = "female")
        assertEquals(2387, male.estimatedDailyExpenditureKcal)
        assertEquals(2155, female.estimatedDailyExpenditureKcal)
        assertEquals(2387, energyBalance(summary(1900.0), male, null).expenditureKcal)
        assertEquals(2500, energyBalance(summary(1900.0), male, 2500).expenditureKcal)
        assertNull(male.copy(age = 18).estimatedDailyExpenditureKcal)
        assertNull(male.copy(gender = "neutral").estimatedDailyExpenditureKcal)
    }

    @Test fun wholeDayEstimateDoesNotDoubleCountMeasuredOrManualActivityCalories() {
        val s = summary(1900.0)
        val withActivity = s.copy(activity = ActivitySummaryDto(activeEnergyKcal = 900.0,
            manuallyEstimatedActiveEnergyKcal = 700.0))
        val adult = UserProfile(age = 25, gender = "male")
        assertEquals(energyBalance(s, adult, null), energyBalance(withActivity, adult, null))
        assertEquals(energyBalance(s, adult, 2400), energyBalance(withActivity, adult, 2400))
    }
}
