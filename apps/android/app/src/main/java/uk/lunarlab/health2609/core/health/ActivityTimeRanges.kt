package uk.lunarlab.health2609.core.health

import java.time.Instant

internal data class InstantRange(val start: Instant, val end: Instant)

internal fun List<InstantRange>.mergeActivityRanges(): List<InstantRange> =
    filter { it.start.isBefore(it.end) }.sortedBy { it.start }
        .fold(mutableListOf()) { merged, next ->
            val previous = merged.lastOrNull()
            if (previous != null && !next.start.isAfter(previous.end)) {
                merged[merged.lastIndex] = InstantRange(previous.start, maxOf(previous.end, next.end))
            } else merged.add(next)
            merged
        }
