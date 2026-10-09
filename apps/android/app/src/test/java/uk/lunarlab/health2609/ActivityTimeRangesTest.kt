package uk.lunarlab.health2609

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test
import uk.lunarlab.health2609.core.health.InstantRange
import uk.lunarlab.health2609.core.health.mergeActivityRanges

class ActivityTimeRangesTest {
    private fun range(start: String, end: String) = InstantRange(
        Instant.parse("2026-10-06T${start}:00Z"), Instant.parse("2026-10-06T${end}:00Z")
    )

    @Test fun overlappingAndRepeatedLessonsAreReadOnlyOnce() {
        val result = listOf(range("10:30", "11:15"), range("10:00", "11:00"),
            range("10:00", "11:00"), range("11:15", "11:30")).mergeActivityRanges()
        assertEquals(listOf(range("10:00", "11:30")), result)
        assertEquals(90, Duration.between(result.single().start, result.single().end).toMinutes().toInt())
    }

    @Test fun gapsArePreservedAndInvalidIntervalsAreIgnored() {
        assertEquals(listOf(range("08:00", "09:00"), range("10:00", "11:00")),
            listOf(range("11:00", "10:00"), range("10:00", "10:00"),
                range("10:00", "11:00"), range("08:00", "09:00")).mergeActivityRanges())
    }
}
