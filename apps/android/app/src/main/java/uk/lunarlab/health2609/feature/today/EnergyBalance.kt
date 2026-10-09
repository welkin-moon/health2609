package uk.lunarlab.health2609.feature.today

import kotlin.math.abs
import kotlin.math.roundToInt
import uk.lunarlab.health2609.core.network.DailySummaryDto
import uk.lunarlab.health2609.core.storage.UserProfile

internal class EnergySettingsSaveException(message: String, cause: Throwable) : Exception(message, cause)

internal data class EnergyBalance(
    val expenditureKcal: Int?,
    val knownIntakeKcal: Int,
    val status: String,
    val explanation: String
)

internal fun energyBalance(
    summary: DailySummaryDto?,
    profile: UserProfile,
    expenditureOverrideKcal: Int?
): EnergyBalance {
    val expenditure = expenditureOverrideKcal ?: profile.estimatedDailyExpenditureKcal
    val intake = summary?.nutrition?.energyKcal?.roundToInt() ?: 0
    val unknown = (summary?.nutrition?.unknownEnergyItems ?: 0) > 0
    val gap = expenditure?.minus(intake)
    val status = when {
        summary == null -> "正在读取记录"
        summary.nutrition.recordedFoodItems == 0 -> "尚未记录餐食"
        gap == null -> "消耗待估算"
        unknown && gap > 0 -> "缺口至多 $gap 千卡"
        unknown && gap < 0 -> "盈余至少 ${abs(gap)} 千卡"
        unknown -> "已知部分持平 · 营养待补"
        gap > 0 -> "估算缺口 $gap 千卡"
        gap < 0 -> "估算盈余 ${abs(gap)} 千卡"
        else -> "估算持平"
    }
    val source = when {
        expenditureOverrideKcal != null -> "全天消耗采用你填写的估算值。"
        expenditure != null -> "全天消耗按已保存的身体信息及轻活动系数 1.4 粗略估算。"
        else -> "请在应用设置的「能量设置」填写全天消耗估算；成长阶段和未指定性别不套用成人公式。"
    }
    return EnergyBalance(
        expenditure, intake, status,
        source + if (unknown) " 部分食物营养待补，摄入只计已知部分，缺口可能更小或转为盈余。"
        else " 这是全天消耗与已记录摄入的比较；当天尚未记录的餐食会改变结果。"
    )
}
