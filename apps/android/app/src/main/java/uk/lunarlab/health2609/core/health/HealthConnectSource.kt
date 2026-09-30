package uk.lunarlab.health2609.core.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import uk.lunarlab.health2609.core.network.SchoolDayWindowDto
import uk.lunarlab.health2609.core.network.SchoolPeWindowDto

import android.content.Intent
import android.net.Uri
import android.provider.Settings

data class HealthConnectDayAggregate(
    val exerciseMinutes: Int,
    val steps: Long,
    val activeEnergyKcal: Double
)

sealed interface HealthPermissionState {
    data object AvailableAndGranted : HealthPermissionState
    data class MissingPermissions(
        val granted: Set<String>,
        val missing: Set<String>,
        val missingLabels: List<String>
    ) : HealthPermissionState
    data object SdkUnavailable : HealthPermissionState
    data object SdkUpdateRequired : HealthPermissionState
}

private data class InstantRange(
    val start: Instant,
    val end: Instant
)

class HealthConnectSource(
    context: Context
) {
    private val appContext = context.applicationContext

    private val client: HealthConnectClient by lazy {
        HealthConnectClient.getOrCreate(appContext)
    }

    fun sdkStatus(): Int =
        HealthConnectClient.getSdkStatus(appContext)

    fun isAvailable(): Boolean =
        sdkStatus() == HealthConnectClient.SDK_AVAILABLE

    suspend fun checkPermissionState(): HealthPermissionState {
        when (sdkStatus()) {
            HealthConnectClient.SDK_UNAVAILABLE -> return HealthPermissionState.SdkUnavailable
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> return HealthPermissionState.SdkUpdateRequired
        }
        val granted = runCatching {
            client.permissionController.getGrantedPermissions()
        }.getOrDefault(emptySet())

        val missing = REQUIRED_PERMISSIONS.filter { required ->
            !granted.contains(required)
        }.toSet()

        if (missing.isEmpty()) {
            return HealthPermissionState.AvailableAndGranted
        }
        return HealthPermissionState.MissingPermissions(
            granted = granted,
            missing = missing,
            missingLabels = missing.map { permissionLabel(it) }
        )
    }

    suspend fun hasRequiredPermissions(): Boolean {
        return checkPermissionState() is HealthPermissionState.AvailableAndGranted
    }

    fun createSettingsIntent(): Intent {
        val manageIntent = Intent("android.health.connect.action.MANAGE_HEALTH_PERMISSIONS").apply {
            putExtra(Intent.EXTRA_PACKAGE_NAME, appContext.packageName)
        }
        if (manageIntent.resolveActivity(appContext.packageManager) != null) {
            return manageIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val clientSettingsIntent = Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)
        if (clientSettingsIntent.resolveActivity(appContext.packageManager) != null) {
            return clientSettingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", appContext.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    suspend fun getDiagnostics(): String {
        val status = sdkStatus()
        val statusText = when (status) {
            HealthConnectClient.SDK_AVAILABLE -> "SDK_AVAILABLE (1)"
            HealthConnectClient.SDK_UNAVAILABLE -> "SDK_UNAVAILABLE (2)"
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> "SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED (3)"
            else -> "UNKNOWN ($status)"
        }
        val granted = if (status == HealthConnectClient.SDK_AVAILABLE) {
            runCatching { client.permissionController.getGrantedPermissions() }.getOrDefault(emptySet())
        } else emptySet()
        val missing = REQUIRED_PERMISSIONS - granted
        return "HealthConnect[status=$statusText, granted=${granted.size}/${REQUIRED_PERMISSIONS.size}, missing=$missing]"
    }

    suspend fun readOutsideSchoolDay(
        date: LocalDate,
        schoolWindows: List<SchoolDayWindowDto>,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): HealthConnectDayAggregate {
        check(isAvailable()) { "Health Connect is unavailable" }
        check(hasRequiredPermissions()) { "Health Connect permissions are missing" }

        val outsideRanges = outsideSchoolRanges(
            date = date,
            schoolWindows = schoolWindows,
            zoneId = zoneId
        )

        var exerciseDuration = Duration.ZERO
        var steps = 0L
        var activeEnergyKcal = 0.0

        for (range in outsideRanges) {
            if (!range.start.isBefore(range.end)) continue

            val result = client.aggregate(
                AggregateRequest(
                    metrics = setOf(
                        ExerciseSessionRecord.EXERCISE_DURATION_TOTAL,
                        StepsRecord.COUNT_TOTAL,
                        ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL
                    ),
                    timeRangeFilter = TimeRangeFilter.between(
                        range.start,
                        range.end
                    )
                )
            )

            exerciseDuration +=
                result[ExerciseSessionRecord.EXERCISE_DURATION_TOTAL]
                    ?: Duration.ZERO
            steps += result[StepsRecord.COUNT_TOTAL] ?: 0L
            activeEnergyKcal +=
                result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]
                    ?.inKilocalories
                    ?: 0.0
        }

        return HealthConnectDayAggregate(
            exerciseMinutes = exerciseDuration.toMinutes()
                .coerceAtMost(1440)
                .toInt(),
            steps = steps.coerceIn(0L, 200_000L),
            activeEnergyKcal = activeEnergyKcal.coerceIn(0.0, 20_000.0)
        )
    }

    suspend fun readSchoolPeWindows(
        date: LocalDate,
        peWindows: List<SchoolPeWindowDto>,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): HealthConnectDayAggregate {
        check(isAvailable()) { "Health Connect is unavailable" }
        check(hasRequiredPermissions()) { "Health Connect permissions are missing" }

        val ranges = peWindows.mapNotNull { window ->
            val startTime = runCatching { LocalTime.parse(window.startTime) }.getOrNull()
                ?: return@mapNotNull null
            val endTime = runCatching { LocalTime.parse(window.endTime) }.getOrNull()
                ?: return@mapNotNull null
            val start = date.atTime(startTime).atZone(zoneId).toInstant()
            val end = date.atTime(endTime).atZone(zoneId).toInstant()
            if (start.isBefore(end)) InstantRange(start, end) else null
        }

        var exerciseDuration = Duration.ZERO
        var steps = 0L
        var activeEnergyKcal = 0.0

        for (range in ranges) {
            val result = client.aggregate(
                AggregateRequest(
                    metrics = setOf(
                        ExerciseSessionRecord.EXERCISE_DURATION_TOTAL,
                        StepsRecord.COUNT_TOTAL,
                        ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL
                    ),
                    timeRangeFilter = TimeRangeFilter.between(range.start, range.end)
                )
            )
            exerciseDuration +=
                result[ExerciseSessionRecord.EXERCISE_DURATION_TOTAL] ?: Duration.ZERO
            steps += result[StepsRecord.COUNT_TOTAL] ?: 0L
            activeEnergyKcal +=
                result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]
                    ?.inKilocalories ?: 0.0
        }

        return HealthConnectDayAggregate(
            exerciseMinutes = exerciseDuration.toMinutes().coerceAtMost(1440).toInt(),
            steps = steps.coerceIn(0L, 200_000L),
            activeEnergyKcal = activeEnergyKcal.coerceIn(0.0, 20_000.0)
        )
    }

    private fun outsideSchoolRanges(
        date: LocalDate,
        schoolWindows: List<SchoolDayWindowDto>,
        zoneId: ZoneId
    ): List<InstantRange> {
        val dayStart = date.atStartOfDay(zoneId).toInstant()
        val dayEnd = date.plusDays(1).atStartOfDay(zoneId).toInstant()

        val schoolRanges = schoolWindows
            .mapNotNull { window ->
                val startTime = runCatching {
                    LocalTime.parse(window.startTime)
                }.getOrNull() ?: return@mapNotNull null
                val endTime = runCatching {
                    LocalTime.parse(window.endTime)
                }.getOrNull() ?: return@mapNotNull null

                val start = date.atTime(startTime).atZone(zoneId).toInstant()
                val end = date.atTime(endTime).atZone(zoneId).toInstant()

                if (!start.isBefore(end)) {
                    null
                } else {
                    InstantRange(
                        start = maxOf(start, dayStart),
                        end = minOf(end, dayEnd)
                    )
                }
            }
            .filter { it.start.isBefore(it.end) }
            .sortedBy { it.start }
            .fold(mutableListOf<InstantRange>()) { merged, next ->
                val previous = merged.lastOrNull()
                if (
                    previous != null &&
                    !next.start.isAfter(previous.end)
                ) {
                    merged[merged.lastIndex] = InstantRange(
                        start = previous.start,
                        end = maxOf(previous.end, next.end)
                    )
                } else {
                    merged += next
                }
                merged
            }

        if (schoolRanges.isEmpty()) {
            return listOf(InstantRange(dayStart, dayEnd))
        }

        val outside = mutableListOf<InstantRange>()
        var cursor = dayStart

        for (school in schoolRanges) {
            if (cursor.isBefore(school.start)) {
                outside += InstantRange(cursor, school.start)
            }
            cursor = maxOf(cursor, school.end)
        }

        if (cursor.isBefore(dayEnd)) {
            outside += InstantRange(cursor, dayEnd)
        }

        return outside
    }

    companion object {
        val REQUIRED_PERMISSIONS: Set<String> = setOf(
            HealthPermission.getReadPermission(
                ExerciseSessionRecord::class
            ),
            HealthPermission.getReadPermission(
                StepsRecord::class
            ),
            HealthPermission.getReadPermission(
                ActiveCaloriesBurnedRecord::class
            )
        )

        fun permissionLabel(permission: String): String {
            return when {
                permission.contains("STEPS", ignoreCase = true) -> "步数"
                permission.contains("EXERCISE", ignoreCase = true) -> "运动记录"
                permission.contains("ACTIVE_CALORIES", ignoreCase = true) -> "活动能量"
                else -> permission.substringAfterLast('.')
            }
        }
    }
}
