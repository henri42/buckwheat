package com.danilkinkin.buckwheat.finishPeriod

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.graphics.Point
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme.colors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.PaintingStyle.Companion.Stroke
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.danilkinkin.buckwheat.R
import com.danilkinkin.buckwheat.data.entities.Spent
import com.danilkinkin.buckwheat.ui.*
import com.danilkinkin.buckwheat.util.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.*
import kotlin.math.abs

@Composable
fun MinMaxSpentCard(
    modifier: Modifier = Modifier,
    isMin: Boolean,
    spends: List<Spent>,
    currency: ExtendCurrency,
) {
    val minSpent = spends.minByOrNull { it.value }
    val maxSpent = spends.maxByOrNull { it.value }

    val spent = if (isMin) minSpent else maxSpent

    val minValue = minSpent?.value ?: BigDecimal(0)
    val maxValue = maxSpent?.value ?: BigDecimal(0)
    val currValue = spent?.value ?: BigDecimal(0)

    val harmonizedColor = toPalette(
        harmonize(
            combineColors(
                colorMin,
                colorMax,
                if (maxValue - minValue == BigDecimal(0)) {
                    if (isMin) 0f else 1f
                } else if (maxValue != BigDecimal(0)) {
                    ((currValue - minValue) / (maxValue - minValue)).toFloat()
                } else {
                    0f
                },
            )
        )
    )

    StatCard(
        modifier = modifier,
        value = if (spent != null) {
            prettyCandyCanes(
                spent.value,
                currency = currency,
            )
        } else {
            "-"
        },
        label = stringResource(if (isMin) R.string.min_spent else R.string.max_spent),
        colors = CardDefaults.cardColors(
            containerColor = harmonizedColor.container,
            contentColor = harmonizedColor.onContainer,
        ),
        content = {
            Spacer(modifier = Modifier.height(6.dp))

            if (spent != null) {
                Text(
                    text = prettyDate(
                        spent.date,
                        showTime = false,
                        forceShowDate = true,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = MaterialTheme.typography.bodyLarge.fontWeight,
                )
                Text(
                    text = prettyDate(
                        spent.date,
                        showTime = true,
                        forceHideDate = true,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = MaterialTheme.typography.bodyLarge.fontWeight,
                )
            }
        },
        backdropContent = {
            if (spends.isNotEmpty()) {
                SpendsChart(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(),
                    spends = spends,
                    markedSpent = spent,
                    chartPadding = PaddingValues(vertical = 16.dp, horizontal = 16.dp),
                    showBeforeMarked = 4,
                    showAfterMarked = 1,
                )
            }
        }
    )
}

@Preview(name = "Min spent")
@Composable
private fun PreviewMin() {
    BuckwheatTheme {
        MinMaxSpentCard(
            isMin = true,
            currency = ExtendCurrency(type = CurrencyType.NONE),
            spends = listOf(
                Spent(value = BigDecimal(52), date = LocalDate.now().minusDays(2).toDate()),
                Spent(value = BigDecimal(72), date = LocalDate.now().minusDays(2).toDate()),
                Spent(value = BigDecimal(42), date = LocalDate.now().minusDays(2).toDate()),
                Spent(value = BigDecimal(52), date = LocalDate.now().minusDays(1).toDate()),
                Spent(value = BigDecimal(72), date = LocalDate.now().minusDays(1).toDate()),
                Spent(value = BigDecimal(42), date = LocalDate.now().minusDays(1).toDate()),
                Spent(value = BigDecimal(56), date = Date()),
                Spent(value = BigDecimal(15), date = Date()),
                Spent(value = BigDecimal(42), date = Date()),
            ),
        )
    }
}

@Preview(name = "Max spent")
@Composable
private fun PreviewMax() {
    BuckwheatTheme {
        MinMaxSpentCard(
            isMin = false,
            currency = ExtendCurrency(type = CurrencyType.NONE),
            spends = listOf(
                Spent(value = BigDecimal(52), date = LocalDate.now().minusDays(2).toDate()),
                Spent(value = BigDecimal(72), date = LocalDate.now().minusDays(2).toDate()),
                Spent(value = BigDecimal(42), date = LocalDate.now().minusDays(2).toDate()),
                Spent(value = BigDecimal(52), date = LocalDate.now().minusDays(1).toDate()),
                Spent(value = BigDecimal(72), date = LocalDate.now().minusDays(1).toDate()),
                Spent(value = BigDecimal(42), date = LocalDate.now().minusDays(1).toDate()),
                Spent(value = BigDecimal(56), date = Date()),
                Spent(value = BigDecimal(15), date = Date()),
                Spent(value = BigDecimal(42), date = Date()),
            ),
        )
    }
}

@Preview(name = "Min spent (Night mode)", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun PreviewMinNightMode() {
    BuckwheatTheme {
        MinMaxSpentCard(
            isMin = true,
            currency = ExtendCurrency(type = CurrencyType.NONE),
            spends = listOf(
                Spent(value = BigDecimal(52), date = LocalDate.now().minusDays(2).toDate()),
                Spent(value = BigDecimal(72), date = LocalDate.now().minusDays(2).toDate()),
                Spent(value = BigDecimal(42), date = LocalDate.now().minusDays(2).toDate()),
                Spent(value = BigDecimal(52), date = LocalDate.now().minusDays(1).toDate()),
                Spent(value = BigDecimal(72), date = LocalDate.now().minusDays(1).toDate()),
                Spent(value = BigDecimal(42), date = LocalDate.now().minusDays(1).toDate()),
                Spent(value = BigDecimal(56), date = Date()),
                Spent(value = BigDecimal(15), date = Date()),
                Spent(value = BigDecimal(42), date = Date()),
            ),
        )
    }
}

@Preview(name = "Max spent (Night mode)", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun PreviewMaxNightMode() {
    BuckwheatTheme {
        MinMaxSpentCard(
            isMin = false,
            currency = ExtendCurrency(type = CurrencyType.NONE),
            spends = listOf(
                Spent(value = BigDecimal(52), date = LocalDate.now().minusDays(2).toDate()),
                Spent(value = BigDecimal(72), date = LocalDate.now().minusDays(2).toDate()),
                Spent(value = BigDecimal(42), date = LocalDate.now().minusDays(2).toDate()),
                Spent(value = BigDecimal(52), date = LocalDate.now().minusDays(1).toDate()),
                Spent(value = BigDecimal(72), date = LocalDate.now().minusDays(1).toDate()),
                Spent(value = BigDecimal(42), date = LocalDate.now().minusDays(1).toDate()),
                Spent(value = BigDecimal(56), date = Date()),
                Spent(value = BigDecimal(15), date = Date()),
                Spent(value = BigDecimal(42), date = Date()),
            ),
        )
    }
}

@Preview(name = "Same spends")
@Composable
private fun PreviewWithSameSpends() {
    BuckwheatTheme {
        MinMaxSpentCard(
            isMin = false,
            currency = ExtendCurrency(type = CurrencyType.NONE),
            spends = listOf(
                Spent(value = BigDecimal(42), date = LocalDate.now().minusDays(1).toDate()),
                Spent(value = BigDecimal(42), date = Date()),
            ),
        )
    }
}

@Preview(name = "One spent")
@Composable
private fun PreviewWithOneSpent() {
    BuckwheatTheme {
        MinMaxSpentCard(
            isMin = false,
            currency = ExtendCurrency(type = CurrencyType.NONE),
            spends = listOf(
                Spent(value = BigDecimal(42), date = Date()),
            ),
        )
    }
}

@Preview(name = "No spends")
@Composable
private fun PreviewWithZeroSpends() {
    BuckwheatTheme {
        MinMaxSpentCard(
            isMin = false,
            currency = ExtendCurrency(type = CurrencyType.NONE),
            spends = listOf(),
        )
    }
}