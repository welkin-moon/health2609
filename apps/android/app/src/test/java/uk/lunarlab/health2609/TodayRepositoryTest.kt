package uk.lunarlab.health2609

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import uk.lunarlab.health2609.core.network.NutritionDto
import uk.lunarlab.health2609.feature.today.TodayRepository

class TodayRepositoryTest {

    private val repository = TodayRepository(FakeHealthApi())

    @Test
    fun testScaledNutrition_nullNutritionReturnsNull() {
        val result = repository.scaledNutrition(
            nutrition = null,
            sourceGrams = 100.0,
            confirmedGrams = 150.0
        )
        assertNull(result)
    }

    @Test
    fun testScaledNutrition_nullOrZeroSourceGramsReturnsOriginal() {
        val original = NutritionDto(energyKcal = 200.0, proteinG = 10.0)
        val resultZero = repository.scaledNutrition(
            nutrition = original,
            sourceGrams = 0.0,
            confirmedGrams = 150.0
        )
        assertEquals(original, resultZero)

        val resultNull = repository.scaledNutrition(
            nutrition = original,
            sourceGrams = null,
            confirmedGrams = 150.0
        )
        assertEquals(original, resultNull)
    }

    @Test
    fun testScaledNutrition_scalesLinearlyByGrams() {
        val original = NutritionDto(
            energyKcal = 200.0,
            proteinG = 10.0,
            fatG = 5.0,
            carbohydrateG = 20.0,
            fiberG = 3.0,
            sodiumMg = 300.0,
            sugarG = 4.0,
            saturatedFatG = 1.5
        )
        val result = repository.scaledNutrition(
            nutrition = original,
            sourceGrams = 100.0,
            confirmedGrams = 200.0 // 2.0x factor
        )
        assertNotNull(result)
        assertEquals(400.0, result?.energyKcal ?: 0.0, 0.001)
        assertEquals(20.0, result?.proteinG ?: 0.0, 0.001)
        assertEquals(10.0, result?.fatG ?: 0.0, 0.001)
        assertEquals(40.0, result?.carbohydrateG ?: 0.0, 0.001)
        assertEquals(6.0, result?.fiberG ?: 0.0, 0.001)
        assertEquals(600.0, result?.sodiumMg ?: 0.0, 0.001)
        assertEquals(8.0, result?.sugarG ?: 0.0, 0.001)
        assertEquals(3.0, result?.saturatedFatG ?: 0.0, 0.001)
    }

    @Test
    fun testScaledNutrition_clampsFactorToMax10() {
        val original = NutritionDto(energyKcal = 100.0)
        val result = repository.scaledNutrition(
            nutrition = original,
            sourceGrams = 10.0,
            confirmedGrams = 500.0 // factor 50 -> clamped to 10.0
        )
        assertEquals(1000.0, result?.energyKcal ?: 0.0, 0.001)
    }
}
