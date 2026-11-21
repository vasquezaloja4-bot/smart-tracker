package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class ChartSlice(
    val label: String,
    val count: Int,
    val color: Color
)

@Composable
fun StatusDonutChart(
    slices: List<ChartSlice>,
    centerLabel: String,
    centerTitle: String,
    modifier: Modifier = Modifier
) {
    val total = slices.sumOf { it.count }.coerceAtLeast(1)
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Box(
            modifier = Modifier.size(136.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(136.dp)) {
                val strokeWidth = 22.dp.toPx()
                val diameter = size.minDimension - strokeWidth
                val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
                val arcSize = Size(diameter, diameter)

                drawArc(
                    color = trackColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth)
                )

                var startAngle = -90f
                slices.filter { it.count > 0 }.forEach { slice ->
                    val sweep = (slice.count.toFloat() / total.toFloat()) * 360f
                    drawArc(
                        color = slice.color,
                        startAngle = startAngle,
                        sweepAngle = (sweep - 3f).coerceAtLeast(2f),
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                    startAngle += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = centerLabel,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = centerTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            slices.forEach { slice ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(slice.color)
                        )
                        Text(
                            text = slice.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = slice.count.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun HorizontalBarChart(
    items: List<ChartSlice>,
    modifier: Modifier = Modifier
) {
    val maxCount = (items.maxOfOrNull { it.count } ?: 1).coerceAtLeast(1)
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items.forEach { item ->
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = item.count.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(5.dp))
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(999.dp))
                ) {
                    drawRoundRect(
                        color = trackColor,
                        size = size,
                        cornerRadius = CornerRadius(size.height / 2f, size.height / 2f)
                    )
                    val fraction = (item.count.toFloat() / maxCount.toFloat()).coerceIn(0f, 1f)
                    if (fraction > 0f) {
                        drawRoundRect(
                            color = item.color,
                            size = Size(size.width * fraction, size.height),
                            cornerRadius = CornerRadius(size.height / 2f, size.height / 2f)
                        )
                    }
                }
            }
        }
    }
}
