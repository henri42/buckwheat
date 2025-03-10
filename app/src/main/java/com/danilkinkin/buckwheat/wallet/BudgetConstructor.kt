package com.danilkinkin.buckwheat.wallet

import androidx.compose.animation.*
import androidx.compose.animation.core.EaseInOutQuad
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.danilkinkin.buckwheat.R
import com.danilkinkin.buckwheat.base.ButtonRow
import com.danilkinkin.buckwheat.data.AppViewModel
import com.danilkinkin.buckwheat.data.PathState
import com.danilkinkin.buckwheat.data.SpendsViewModel
import com.danilkinkin.buckwheat.util.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.*


@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun BudgetConstructor(
    forceChange: Boolean = false,
    appViewModel: AppViewModel = hiltViewModel(),
    spendsViewModel: SpendsViewModel = hiltViewModel(),
    onChange: (budget: BigDecimal, finishDate: Date?) -> Unit = { _, _ -> },
) {
    var rawBudget by remember {
        val restBudget =
            (spendsViewModel.budget.value!! - spendsViewModel.spent.value!! - spendsViewModel.spentFromDailyBudget.value!!)
                .setScale(2, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString()

        val converted = if (restBudget != "0") {
            tryConvertStringToNumber(restBudget)
        } else {
            Triple("", "0", "")
        }

        mutableStateOf(converted.first + converted.second)
    }
    var budget by remember {
        val restBudget =
            (spendsViewModel.budget.value!! - spendsViewModel.spent.value!! - spendsViewModel.spentFromDailyBudget.value!!)
                .setScale(2, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString()

        mutableStateOf(BigDecimal(restBudget))
    }
    val dateToValue = remember { mutableStateOf(spendsViewModel.finishDate.value) }
    var showUseSuggestion by remember {
        val useBudget = budget != spendsViewModel.budget.value!!
                && spendsViewModel.budget.value!! != BigDecimal(0)

        val length = if (spendsViewModel.finishDate.value !== null) {
            countDays(
                spendsViewModel.finishDate.value!!,
                spendsViewModel.startDate.value!!,
            )
        } else {
            0
        }
        val finishDate = LocalDate.now().plusDays(length.toLong() - 1).toDate()

        val useDate = length != 0
                && (spendsViewModel.finishDate.value == null || !isSameDay(
            finishDate.time,
            spendsViewModel.finishDate.value!!.time
        ))

        mutableStateOf(
            useBudget || useDate
        )
    }

    val keyboardController = LocalSoftwareKeyboardController.current

    Column {
        val days = if (dateToValue.value != null) countDays(dateToValue.value!!) else 0

        UseLastSuggestionChip(
            visible = showUseSuggestion,
            onClick = {
                rawBudget =
                    if (spendsViewModel.budget.value!! !== BigDecimal(0)) {
                        tryConvertStringToNumber(spendsViewModel.budget.value!!.toString()).join(
                            third = false
                        )
                    } else {
                        Triple("", "0", "").join(third = false)
                    }

                budget = spendsViewModel.budget.value!!

                val length = countDays(
                    spendsViewModel.finishDate.value!!,
                    spendsViewModel.startDate.value!!,
                )
                val finishDate = LocalDate.now().plusDays(length.toLong() - 1).toDate()

                dateToValue.value = finishDate

                onChange(
                    budget,
                    finishDate,
                )

                showUseSuggestion = false
            }
        )

        val focusRequester = remember { FocusRequester() }

        BasicTextField(
            value = rawBudget,
            onValueChange = {
                val converted = tryConvertStringToNumber(it)

                rawBudget = converted.join(third = false)
                budget = converted.join().toBigDecimal()

                onChange(budget, dateToValue.value)
            },
            textStyle = MaterialTheme.typography.displayLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            ),
            visualTransformation = visualTransformationAsCurrency(
                currency = ExtendCurrency(type = CurrencyType.NONE),
                hintColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = { keyboardController?.hide() }
            ),
            modifier = Modifier.focusRequester(focusRequester),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { input ->
                Column {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 56.dp, bottom = 80.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        input()
                    }
                }
            },
        )

        LaunchedEffect(Unit) {
            if (forceChange) focusRequester.requestFocus()
        }

        ButtonRow(
            icon = painterResource(R.drawable.ic_calendar),
            text = if (days > 0) {
                String.format(
                    pluralStringResource(R.plurals.finish_date_label, days),
                    prettyDate(dateToValue.value!!, showTime = false, forceShowDate = true),
                    days,
                )
            } else {
                stringResource(R.string.finish_date_not_select)
            },
            onClick = {
                appViewModel.openSheet(PathState(
                    name = FINISH_DATE_SELECTOR_SHEET,
                    args = mapOf("initialDate" to dateToValue.value),
                    callback = { result ->
                        if (!result.containsKey("finishDate")) return@PathState

                        dateToValue.value = result["finishDate"] as Date

                        onChange(budget, dateToValue.value)
                    }
                ))
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UseLastSuggestionChip(
    visible: Boolean,
    onClick: () -> Unit
) {
    val localDensity = LocalDensity.current

    Row(
        modifier = Modifier
            .padding(start = 16.dp)
            .height(32.dp)
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(
                tween(durationMillis = 150)
            ) + slideInHorizontally(
                tween(
                    durationMillis = 150,
                    easing = EaseInOutQuad,
                )
            ) { with(localDensity) { 10.dp.toPx().toInt() } },
            exit = fadeOut(
                tween(durationMillis = 150)
            ) + slideOutHorizontally(
                tween(
                    durationMillis = 150,
                    easing = EaseInOutQuad,
                )
            ) { with(localDensity) { 10.dp.toPx().toInt() } },
        ) {
            SuggestionChip(
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_autorenew),
                        tint = MaterialTheme.colorScheme.primary,
                        contentDescription = null,
                    )
                },
                label = {
                    Text(text = stringResource(R.string.use_last))
                },
                onClick = {
                    onClick()
                }
            )
        }
    }
}

