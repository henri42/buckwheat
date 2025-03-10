package com.danilkinkin.buckwheat.data

import android.content.Context
import android.net.Uri
import androidx.lifecycle.*
import com.danilkinkin.buckwheat.data.entities.Spent
import com.danilkinkin.buckwheat.data.entities.Storage
import com.danilkinkin.buckwheat.di.DatabaseRepository
import com.danilkinkin.buckwheat.util.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.*
import javax.inject.Inject
import kotlin.math.abs


@HiltViewModel
class SpendsViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val db: DatabaseRepository,
) : ViewModel() {
    enum class Mode { ADD, EDIT }
    enum class Stage { IDLE, CREATING_SPENT, EDIT_SPENT, COMMITTING_SPENT }
    enum class Action { PUT_NUMBER, SET_DOT, REMOVE_LAST }
    enum class RecalcRestBudgetMethod { REST, ADD_TODAY, ASK }

    private val spentDao = db.spentDao()
    private val storageDao = db.storageDao()

    var mode: MutableLiveData<Mode> = MutableLiveData(Mode.ADD)
    var stage: MutableLiveData<Stage> = MutableLiveData(Stage.IDLE)
    var lastRemoveSpent: MutableSharedFlow<Spent> = MutableSharedFlow()

    var budget: MutableLiveData<BigDecimal> = MutableLiveData(
        try {
            storageDao.get("budget").value.toBigDecimal()
        } catch (e: Exception) {
            0.toBigDecimal()
        }
    )
    var spent: MutableLiveData<BigDecimal> = MutableLiveData(
        try {
            storageDao.get("spent").value.toBigDecimal()
        } catch (e: Exception) {
            0.0.toBigDecimal()
        }
    )
    var dailyBudget: MutableLiveData<BigDecimal> = MutableLiveData(
        try {
            storageDao.get("dailyBudget").value.toBigDecimal()
        } catch (e: Exception) {
            0.0.toBigDecimal()
        }
    )
    var spentFromDailyBudget: MutableLiveData<BigDecimal> = MutableLiveData(
        try {
            storageDao.get("spentFromDailyBudget").value.toBigDecimal()
        } catch (e: Exception) {
            0.0.toBigDecimal()
        }
    )

    var recalcRestBudgetMethod: MutableLiveData<RecalcRestBudgetMethod> = MutableLiveData(
        try {
            RecalcRestBudgetMethod.valueOf(storageDao.get("recalcRestBudgetMethod").value)
        } catch (e: Exception) {
            RecalcRestBudgetMethod.ASK
        }
    )

    var overspendingWarnHidden: MutableLiveData<Boolean> = try {
        MutableLiveData(storageDao.get("overspendingWarnHidden").value.toBoolean())
    } catch (e: Exception) {
        MutableLiveData(false)
    }

    var startDate: MutableLiveData<Date> = try {
        MutableLiveData(Date(storageDao.get("startDate").value.toLong()))
    } catch (e: Exception) {
        MutableLiveData(Date())
    }
    var finishDate: MutableLiveData<Date?> = try {
        MutableLiveData(Date(storageDao.get("finishDate").value.toLong()))
    } catch (e: Exception) {
        MutableLiveData(null)
    }
    var lastReCalcBudgetDate: Date? = try {
        Date(storageDao.get("lastReCalcBudgetDate").value.toLong())
    } catch (e: Exception) {
        null
    }

    var currency: MutableLiveData<ExtendCurrency> = try {
        MutableLiveData(ExtendCurrency.getInstance(storageDao.get("currency").value))
    } catch (e: Exception) {
        MutableLiveData(ExtendCurrency(value = null, type = CurrencyType.NONE))
    }

    var editedSpent: Spent? = null
    var currentSpent: BigDecimal = 0.0.toBigDecimal()
    var currentComment: String = ""

    var requireReCalcBudget: MutableLiveData<Boolean> = MutableLiveData(false)
    var requireSetBudget: MutableLiveData<Boolean> = MutableLiveData(false)
    var finishPeriod: MutableLiveData<Boolean> = MutableLiveData(false)

    var rawSpentValue: MutableLiveData<String> = MutableLiveData("")

    init {
        if (
            lastReCalcBudgetDate !== null
            && !isSameDay(lastReCalcBudgetDate!!.time, Date().time)
            && countDays(finishDate.value!!) > 0
        ) {
            if (((dailyBudget.value ?: BigDecimal(0)) - (spentFromDailyBudget.value
                    ?: BigDecimal(0)) > BigDecimal(0))
            ) {
                when (recalcRestBudgetMethod.value) {
                    RecalcRestBudgetMethod.ASK, null -> requireReCalcBudget.value = true
                    RecalcRestBudgetMethod.REST -> reCalcDailyBudget(calcBudgetPerDaySplit())
                    RecalcRestBudgetMethod.ADD_TODAY -> reCalcDailyBudget(calcBudgetPerDay() + calcRequireDistributeBudget())
                }
            } else {
                reCalcDailyBudget(calcBudgetPerDaySplit())
            }
        } else if (lastReCalcBudgetDate === null) {
            requireSetBudget.value = true
        } else if (lastReCalcBudgetDate !== null && finishDate.value!!.time <= Date().time) {
            finishPeriod.value = true
        }

        // Bug fix https://github.com/danilkinkin/buckwheat/issues/28
        if (this.dailyBudget.value!! - this.spentFromDailyBudget.value!! - this.currentSpent > BigDecimal(
                0
            )
        ) {
            hideOverspendingWarn(false)
        }

        var initAppDate = Date().time

        viewModelScope.launch {
            while (true) {
                delay(5000L)

                if (isSameDay(initAppDate, Date().time)) {
                    continue
                }

                initAppDate = Date().time
                if (
                    lastReCalcBudgetDate !== null
                    && !isSameDay(lastReCalcBudgetDate!!.time, Date().time)
                    && countDays(finishDate.value!!) > 0
                ) {
                    if (((dailyBudget.value ?: BigDecimal(0)) - (spentFromDailyBudget.value
                            ?: BigDecimal(0)) > BigDecimal(0))
                    ) {
                        requireReCalcBudget.value = true
                    } else {
                        reCalcDailyBudget(calcBudgetPerDaySplit())
                    }
                } else if (lastReCalcBudgetDate === null) {
                    requireSetBudget.value = true
                } else if (lastReCalcBudgetDate !== null && finishDate.value!!.time <= Date().time) {
                    finishPeriod.value = true
                }
            }
        }
    }

    fun getCountLastDaySpends(): LiveData<Int> {
        return this.spentDao.getCountLastDaySpends()
    }

    fun getSpends(): LiveData<List<Spent>> {
        return this.spentDao.getAll()
    }

    fun changeCurrency(currency: ExtendCurrency) {
        storageDao.set(Storage("currency", currency.value.toString()))

        this.currency.value = currency
    }

    fun changeRecalcRestBudgetMethod(method: RecalcRestBudgetMethod) {
        storageDao.set(Storage("recalcRestBudgetMethod", method.toString()))

        this.recalcRestBudgetMethod.value = method
    }

    fun changeBudget(budget: BigDecimal, finishDate: Date) {
        storageDao.set(Storage("budget", budget.toString()))
        this.budget.value = budget

        val startDate = roundToDay(Date())
        storageDao.set(Storage("startDate", startDate.time.toString()))
        this.startDate.value = startDate

        val roundedFinishDate = Date(roundToDay(finishDate).time + DAY - 1000)
        storageDao.set(Storage("finishDate", roundedFinishDate.time.toString()))
        this.finishDate.value = roundedFinishDate

        storageDao.set(Storage("spent", 0.0.toString()))
        this.spent.value = 0.0.toBigDecimal()

        storageDao.set(Storage("dailyBudget", 0.0.toString()))
        this.dailyBudget.value = 0.0.toBigDecimal()

        storageDao.set(Storage("spentFromDailyBudget", 0.0.toString()))
        this.spentFromDailyBudget.value = 0.0.toBigDecimal()

        storageDao.set(Storage("lastReCalcBudgetDate", roundToDay(startDate).time.toString()))
        this.lastReCalcBudgetDate = startDate

        hideOverspendingWarn(false)

        this.spentDao.deleteAll()

        requireSetBudget.value = false
        finishPeriod.value = false

        resetSpent()
        reCalcDailyBudget(
            (budget / countDays(roundedFinishDate).toBigDecimal()).setScale(
                0,
                RoundingMode.FLOOR
            )
        )
    }

    fun calcBudgetPerDaySplit(
        applyCurrentSpent: Boolean = false,
        excludeCurrentDay: Boolean = false,
    ): BigDecimal {
        val restDays = countDays(finishDate.value!!) - if (excludeCurrentDay) 1 else 0
        val restBudget = (budget.value!! - spent.value!!) - dailyBudget.value!!
        var splitBudget = restBudget + dailyBudget.value!! - spentFromDailyBudget.value!!
        if (applyCurrentSpent) {
            splitBudget -= currentSpent
        }

        return (splitBudget / restDays.toBigDecimal().coerceAtLeast(BigDecimal(1)))
            .setScale(
                0,
                RoundingMode.FLOOR
            )
    }
    fun calcBudgetPerDay(
        excludeCurrentDay: Boolean = false,
    ): BigDecimal {
        val restDays = countDays(finishDate.value!!) - if (excludeCurrentDay) 1 else 0
        val restBudget = (budget.value!! - spent.value!!) - dailyBudget.value!!

        return (restBudget / restDays.toBigDecimal().coerceAtLeast(BigDecimal(1)))
            .setScale(
                0,
                RoundingMode.FLOOR
            )
    }

    fun calcRequireDistributeBudget(): BigDecimal {
        val restDays = countDays(finishDate.value!!)
        val skippedDays = abs(countDays(lastReCalcBudgetDate!!))

        val restBudget =
            (budget.value!! - spent.value!!) - dailyBudget.value!!
        val perDayBudget = restBudget / (restDays + skippedDays - 1).coerceAtLeast(1).toBigDecimal()

        return perDayBudget * (skippedDays - 1).coerceAtLeast(0)
            .toBigDecimal() + dailyBudget.value!! - spentFromDailyBudget.value!!
    }

    fun calcResetBudget(): BigDecimal {
        return budget.value!! - spent.value!! - spentFromDailyBudget.value!!
    }

    fun reCalcDailyBudget(dailyBudget: BigDecimal) {
        this.dailyBudget.value = dailyBudget
        lastReCalcBudgetDate = roundToDay(Date())
        this.spent.value = this.spent.value!! + spentFromDailyBudget.value!!
        this.spentFromDailyBudget.value = BigDecimal(0)

        storageDao.set(Storage("spent", this.spent.value.toString()))
        storageDao.set(Storage("dailyBudget", dailyBudget.toString()))
        storageDao.set(Storage("spentFromDailyBudget", 0.0.toString()))
        storageDao.set(Storage("lastReCalcBudgetDate", lastReCalcBudgetDate!!.time.toString()))
    }

    fun createSpent() {
        currentSpent = 0.0.toBigDecimal()

        stage.value = Stage.CREATING_SPENT
    }

    fun editSpent(value: BigDecimal) {
        currentSpent = value

        stage.value = Stage.EDIT_SPENT
    }

    fun commitSpent() {
        if (stage.value !== Stage.EDIT_SPENT) return

        val fSpent = currentSpent
            .setScale(2, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()

        if (fSpent == "0") return

        if (editedSpent !== null) {
            this.spentDao.delete(editedSpent!!)
            this.spentDao.insert(editedSpent!!.copy(value = currentSpent, comment = currentComment))

            if (!isSameDay(editedSpent!!.date.time, Date().time)) {
                val restDays = countDays(finishDate.value!!)
                val spentPerDay = (editedSpent!!.value - currentSpent) / restDays.toBigDecimal()

                dailyBudget.value = dailyBudget.value!! + spentPerDay
                this.spent.value = this.spent.value!! - editedSpent!!.value + currentSpent

                storageDao.set(
                    Storage("dailyBudget", dailyBudget.value.toString())
                )
                storageDao.set(
                    Storage("spent", this.spent.value.toString())
                )
            } else {
                spentFromDailyBudget.value = spentFromDailyBudget.value!! - editedSpent!!.value + currentSpent

                storageDao.set(Storage("spentFromDailyBudget", spentFromDailyBudget.value.toString()))
            }
        } else {
            this.spentDao.insert(Spent(currentSpent, Date(), currentComment))

            spentFromDailyBudget.value = spentFromDailyBudget.value?.plus(currentSpent)
            storageDao.set(Storage("spentFromDailyBudget", spentFromDailyBudget.value.toString()))
        }

        currentSpent = 0.0.toBigDecimal()
        currentComment = ""
        rawSpentValue.value = ""

        stage.value = Stage.COMMITTING_SPENT

        resetSpent()
    }

    fun resetSpent() {
        currentSpent = 0.0.toBigDecimal()
        currentComment = ""
        rawSpentValue.value = ""

        stage.value = Stage.IDLE
        mode.value = Mode.ADD
        editedSpent = null
    }

    fun editSpent(spent: Spent) {
        editedSpent = spent
        currentSpent = spent.value
        currentComment = spent.comment
        rawSpentValue.value = tryConvertStringToNumber(spent.value.toString()).join(third = false)

        stage.value = Stage.EDIT_SPENT
        mode.value = Mode.EDIT
    }

    fun removeSpent(spent: Spent) {
        this.spentDao.markAsDeleted(spent.uid, true)

        if (!isSameDay(spent.date.time, Date().time)) {
            val restDays = countDays(finishDate.value!!)
            val spentPerDay = spent.value / restDays.toBigDecimal()

            dailyBudget.value = dailyBudget.value!! + spentPerDay
            this.spent.value = this.spent.value!! - (spent.value - spentPerDay)

            storageDao.set(
                Storage("dailyBudget", dailyBudget.value.toString())
            )
            storageDao.set(
                Storage("spent", this.spent.value.toString())
            )
        } else {
            spentFromDailyBudget.value = spentFromDailyBudget.value!! - spent.value

            storageDao.set(Storage("spentFromDailyBudget", spentFromDailyBudget.value.toString()))
        }
        editedSpent = null

        viewModelScope.launch {
            lastRemoveSpent.emit(spent)
        }
    }

    fun undoRemoveSpent(spent: Spent) {
        if (this.spentDao.getById(spent.uid) !== null) {
            this.spentDao.markAsDeleted(spent.uid, false)
        } else {
            this.spentDao.insert(spent.copy(deleted = false))
        }

        if (!isSameDay(spent.date.time, Date().time)) {
            val restDays = countDays(finishDate.value!!)
            val spentPerDay = spent.value / restDays.toBigDecimal()

            dailyBudget.value = dailyBudget.value!! - spentPerDay
            this.spent.value = this.spent.value!! + (spent.value - spentPerDay)

            storageDao.set(
                Storage("dailyBudget", dailyBudget.value.toString())
            )
            storageDao.set(
                Storage("spent", this.spent.value.toString())
            )
        } else {
            spentFromDailyBudget.value = spentFromDailyBudget.value!! + spent.value

            storageDao.set(
                Storage("spentFromDailyBudget", spentFromDailyBudget.value.toString())
            )
        }
    }

    fun commitDeletedSpends() {
        this.spentDao.commitDeleted()
    }

    fun hideOverspendingWarn(overspendingWarnHidden: Boolean) {
        storageDao.set(Storage("overspendingWarnHidden", overspendingWarnHidden.toString()))

        this.overspendingWarnHidden.value = overspendingWarnHidden

    }

    fun exportAsCsv(context: Context, uri: Uri) {
        val stream = context.contentResolver.openOutputStream(uri)

        val printer = CSVPrinter(
            stream?.writer(),
            CSVFormat.DEFAULT.withHeader("amount", "comment", "commit_time")
        )

        this.spentDao.getAllSync().forEach {
            val dateFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
            val formattedDateTime: String =  it.date.toLocalDateTime().format(dateFormatter)

            printer.printRecord(it.value, it.comment, formattedDateTime)
        }

        printer.flush()
        printer.close()
        stream?.close()
    }
}
