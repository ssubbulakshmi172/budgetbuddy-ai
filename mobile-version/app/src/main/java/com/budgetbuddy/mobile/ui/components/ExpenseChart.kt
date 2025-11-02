package com.budgetbuddy.mobile.ui.components

import android.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.PercentFormatter
import com.github.mikephil.charting.utils.ColorTemplate

@Composable
fun ExpenseChart(
    data: Map<String, Double>,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            PieChart(context).apply {
                description.isEnabled = false
                setUsePercentValues(true)
                setDrawEntryLabels(false)
                setExtraOffsets(20f, 20f, 20f, 20f)
                dragDecelerationFrictionCoef = 0.95f
                isRotationEnabled = false
                setHoleColor(Color.WHITE)
                setTransparentCircleColor(Color.WHITE)
                setTransparentCircleAlpha(110)
                holeRadius = 58f
                transparentCircleRadius = 61f
                setDrawCenterText(true)
                
                val entries = data.map { PieEntry(it.value.toFloat(), it.key) }
                val dataSet = PieDataSet(entries, "").apply {
                    sliceSpace = 3f
                    selectionShift = 5f
                    colors = ColorTemplate.MATERIAL_COLORS.toList()
                }
                
                this.data = PieData(dataSet).apply {
                    setValueTextSize(11f)
                    setValueTextColor(Color.WHITE)
                    setValueFormatter(PercentFormatter())
                }
                
                animateY(1000)
                invalidate()
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .height(300.dp)
    )
}

