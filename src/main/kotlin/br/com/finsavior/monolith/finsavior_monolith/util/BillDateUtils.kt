package br.com.finsavior.monolith.finsavior_monolith.util

import br.com.finsavior.monolith.finsavior_monolith.model.enums.MonthEnum
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

/**
 * Helpers to work with the "MMM yyyy" billing-month strings (e.g. "Jun 2026")
 * used throughout the bill domain. Centralized so fixed-bill generation and the
 * scheduler share the exact same date logic.
 */
object BillDateUtils {

    data class MonthYear(val monthId: Int, val year: Int) {
        fun toBillDate(): String = "${MonthEnum.entries.first { it.id == monthId }.value} $year"
    }

    fun parse(billDate: String): MonthYear {
        val parts = billDate.trim().split(" ")
        require(parts.size == 2) { "Formato de data inválido: $billDate" }
        val monthId = MonthEnum.valueOf(parts[0].uppercase(Locale.getDefault())).id
        val year = parts[1].toInt()
        return MonthYear(monthId, year)
    }

    fun currentMonthYear(): MonthYear {
        val now = LocalDate.now()
        return MonthYear(now.monthValue, now.year)
    }

    /** Inclusive list of billing-month strings from [start] through December of start's year. */
    fun monthsThroughDecember(start: MonthYear): List<String> =
        (start.monthId..12).map { MonthYear(it, start.year).toBillDate() }

    /** Inclusive list of billing-month strings from [start] through [end] (assumes same or later end). */
    fun monthsBetween(start: MonthYear, end: MonthYear): List<String> {
        val result = mutableListOf<String>()
        var m = start.monthId
        var y = start.year
        while (y < end.year || (y == end.year && m <= end.monthId)) {
            result.add(MonthYear(m, y).toBillDate())
            m++
            if (m > 12) { m = 1; y++ }
        }
        return result
    }

    /** Builds the real purchase date for a given billing month + day-of-month, clamping the day to the month length. */
    fun purchaseDateFor(billDate: String, dayOfMonth: Int?): LocalDate? {
        if (dayOfMonth == null) return null
        val my = parse(billDate)
        val ym = YearMonth.of(my.year, my.monthId)
        val clampedDay = dayOfMonth.coerceIn(1, ym.lengthOfMonth())
        return ym.atDay(clampedDay)
    }
}
