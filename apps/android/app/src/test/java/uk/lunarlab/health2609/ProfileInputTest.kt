package uk.lunarlab.health2609

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import uk.lunarlab.health2609.feature.today.profileFromInputs

class ProfileInputTest {
    @Test
    fun decimalValuesAreKeptUntilSave() {
        val profile = profileFromInputs("14", "neutral", "165.5", "55.25")!!
        assertEquals(165.5, profile.heightCm, 0.001)
        assertEquals(55.25, profile.weightKg, 0.001)
    }

    @Test
    fun incompleteOrOutOfRangeEditsCannotBeSaved() {
        for (age in listOf("", "1", "26", "14.5")) {
            assertNull(profileFromInputs(age, "neutral", "165", "55"))
        }
        for (height in listOf("", ".", "79", "231", "NaN", "Infinity")) {
            assertNull(profileFromInputs("14", "neutral", height, "55"))
        }
        for (weight in listOf("", ".", "19", "201", "NaN", "Infinity")) {
            assertNull(profileFromInputs("14", "neutral", "165", weight))
        }
    }
}
