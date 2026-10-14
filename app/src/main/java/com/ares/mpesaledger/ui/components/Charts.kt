package com.ares.mpesaledger.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ares.mpesaledger.ui.format.formatCents
import com.ares.mpesaledger.ui.format.formatCompact
import com.ares.mpesaledger.ui.theme.AresGreen
import com.ares.mpesaledger.ui.theme.AresMuted
import com.ares.mpesaledger.ui.theme.AresOutline
import com.ares.mpesaledger.ui.theme.AresSurfaceHighest
import com.ares.mpesaledger.ui.theme.ChartIncome
import com.ares.mpesaledger.ui.theme.ChartSpending
import com.ares.mpesaledger.ui.theme.Num

data class BarItem(val label: String, val valueCents: Long, val count: Int)

/**
 * Spending by category: one measure, so one hue (brand green), sorted largest first. Each row
 * carries its own label and value as text, so identity never depends on colour. Rows are
 * tappable (opens Activity filtered to that category).
 */
@Composable
fun CategoryBars(items: List<BarItem>, onClick: (BarItem) -> Unit, modifier: Modifier = Modifier) {
    val max = items.maxOfOrNull { it.valueCents }?.takeIf { it > 0 } ?: 1L
    val total = items.sumOf { it.valueCents }.takeIf { it > 0 } ?: 1L
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { item ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .clickable { onClick(item) }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text("${Math.round(item.valueCents * 100.0 / total)}%", style = Num.small, color = AresMuted)
                    Spacer(Modifier.width(12.dp))
                    Text(formatCents(item.valueCents), style = Num.medium)
                }
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(AresSurfaceHighest, RoundedCornerShape(50)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth((item.valueCents.toFloat() / max).coerceIn(0.02f, 1f))
                            .height(8.dp)
                            .background(AresGreen, RoundedCornerShape(50)),
                    )
                }
            }
        }
    }
}

data class TrendPoint(val label: String, val title: String, val incomeCents: Long, val spendingCents: Long)

/**
 * 12-month trend: income vs spending (out + fees) as grouped bars on ONE shared Ksh axis.
 * Tap a month to select it (the touch equivalent of a hover tooltip); the selected month's
 * values are shown as text above the plot. Legend is always present (2 series).
 */
@Composable
fun TrendChart(
    points: List<TrendPoint>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val axisStyle = Num.small.copy(color = AresMuted)
    val max = points.maxOfOrNull { maxOf(it.incomeCents, it.spendingCents) }?.takeIf { it > 0 } ?: 100L
    val ticks = niceTicks(max)
    val top = ticks.last()
    val sel = points.getOrNull(selected)

    Column(modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            LegendDot(ChartIncome, "Income")
            LegendDot(ChartSpending, "Spending")
        }
        Spacer(Modifier.height(10.dp))
        if (sel != null) {
            Text(sel.title, style = MaterialTheme.typography.labelLarge, color = AresMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Income ${formatCents(sel.incomeCents)}", style = Num.medium)
                Text("Spending ${formatCents(sel.spendingCents)}", style = Num.medium)
            }
        }
        Spacer(Modifier.height(12.dp))
        if (points.all { it.incomeCents == 0L && it.spendingCents == 0L }) {
            Text("No M-Pesa activity in these 12 months.", style = MaterialTheme.typography.bodyMedium, color = AresMuted)
            return@Column
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .semantics { contentDescription = "Monthly income and spending, last ${points.size} months" }
                .pointerInput(points.size) {
                    detectTapGestures { pos ->
                        val plotLeft = AXIS_WIDTH.toPx()
                        val slot = (size.width - plotLeft) / points.size
                        val index = ((pos.x - plotLeft) / slot).toInt()
                        if (index in points.indices) onSelect(index)
                    }
                },
        ) {
            val axisW = AXIS_WIDTH.toPx()
            val labelH = 20.dp.toPx()
            val plotH = size.height - labelH
            val slot = (size.width - axisW) / points.size
            val gap = 2.dp.toPx()
            val barW = ((slot * 0.62f) - gap) / 2f
            val radius = CornerRadius(minOf(4.dp.toPx(), barW / 2f))

            // Recessive gridlines + compact labels.
            ticks.forEach { t ->
                val y = plotH - plotH * (t.toFloat() / top)
                drawLine(AresOutline, Offset(axisW, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                drawLabel(measurer, formatCompact(t), Offset(0f, y - 8.dp.toPx()), axisStyle)
            }

            points.forEachIndexed { i, p ->
                val x0 = axisW + slot * i + (slot - (barW * 2 + gap)) / 2f
                val dim = if (i == selected) 1f else 0.45f
                drawBar(x0, plotH, barW, plotH * (p.incomeCents.toFloat() / top), ChartIncome.copy(alpha = dim), radius)
                drawBar(x0 + barW + gap, plotH, barW, plotH * (p.spendingCents.toFloat() / top), ChartSpending.copy(alpha = dim), radius)
                val label = measurer.measure(p.label.take(3), axisStyle.copy(color = if (i == selected) AresGreen else AresMuted))
                drawText(label, topLeft = Offset(axisW + slot * i + (slot - label.size.width) / 2f, plotH + 4.dp.toPx()))
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = AresMuted)
    }
}

private val AXIS_WIDTH = 36.dp

/** Bars grow from the baseline with only their top corners rounded. */
private fun DrawScope.drawBar(x: Float, baseline: Float, width: Float, height: Float, color: Color, radius: CornerRadius) {
    if (height <= 0f) return
    val h = maxOf(height, 2.dp.toPx())
    val path = Path().apply {
        addRoundRect(RoundRect(x, baseline - h, x + width, baseline, radius, radius, CornerRadius.Zero, CornerRadius.Zero))
    }
    drawPath(path, color)
}

private fun DrawScope.drawLabel(measurer: TextMeasurer, text: String, at: Offset, style: TextStyle) {
    drawText(measurer.measure(text, style), topLeft = at)
}

/** 0 plus 3 evenly spaced "nice" ticks (1/2/2.5/5 × 10^n Ksh) covering [maxCents]. */
internal fun niceTicks(maxCents: Long): List<Long> {
    val maxKsh = maxOf(maxCents / 100.0, 1.0)
    val rough = maxOf(maxKsh / 3, 1.0)
    val mag = Math.pow(10.0, Math.floor(Math.log10(rough)))
    val step = listOf(1.0, 2.0, 2.5, 5.0, 10.0).first { it * mag >= rough } * mag
    return (0..3).map { (it * step * 100).toLong() }
}
