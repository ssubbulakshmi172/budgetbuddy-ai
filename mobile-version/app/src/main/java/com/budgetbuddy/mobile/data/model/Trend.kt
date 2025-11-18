package com.budgetbuddy.mobile.data.model

import java.time.YearMonth

/**
 * Data class for trend analysis results
 * Not a Room entity - computed on the fly
 */
data class Trend(
    val category: String,
    val direction: TrendDirection,
    val strength: Double, // 0.0 to 1.0
    val startAmount: Double,
    val endAmount: Double
) {
    enum class TrendDirection {
        INCREASING, DECREASING, STABLE
    }
}

data class SpendingSpike(
    val category: String,
    val month: YearMonth,
    val amount: Double,
    val increase: Double,
    val percentageIncrease: Double
)

data class SpendingDip(
    val category: String,
    val month: YearMonth,
    val amount: Double,
    val decrease: Double,
    val percentageDecrease: Double
)

data class TrendAnalysisResult(
    val trends: List<Trend>,
    val spikes: List<SpendingSpike>,
    val dips: List<SpendingDip>
)

