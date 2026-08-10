package no.vardir.skald.core.text

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Dates, the way a thumb wants them.
 *
 * A desktop can afford `@due(2026-06-01)`: the keyboard is right there and the
 * syntax is one line in a manual. A phone cannot — so a due date has to be
 * pickable from a handful of chips, and anything typed has to be forgiving:
 * "tomorrow", "fri", "+3d" and "1/6" all mean a day.
 *
 * Everything here is a pure function of a string and today's date, so the
 * pickers are tested without a clock.
 */
object Dates {

    /** One offer in the due-date picker. A null [iso] is "no date at all". */
    data class Choice(val label: String, val iso: String?)

    private val ISO = Regex("""^(\d{4})-(\d{1,2})-(\d{1,2})$""")
    private val DAY_MONTH = Regex("""^(\d{1,2})[./](\d{1,2})\.?$""")
    private val AMOUNT = Regex("""^(?:in\s+)?\+?(\d{1,3})\s*(d|day|days|w|week|weeks|mo|month|months)?$""")

    private val WEEKDAYS = mapOf(
        "monday" to DayOfWeek.MONDAY, "mon" to DayOfWeek.MONDAY,
        "tuesday" to DayOfWeek.TUESDAY, "tue" to DayOfWeek.TUESDAY, "tues" to DayOfWeek.TUESDAY,
        "wednesday" to DayOfWeek.WEDNESDAY, "wed" to DayOfWeek.WEDNESDAY,
        "thursday" to DayOfWeek.THURSDAY, "thu" to DayOfWeek.THURSDAY, "thur" to DayOfWeek.THURSDAY,
        "friday" to DayOfWeek.FRIDAY, "fri" to DayOfWeek.FRIDAY,
        "saturday" to DayOfWeek.SATURDAY, "sat" to DayOfWeek.SATURDAY,
        "sunday" to DayOfWeek.SUNDAY, "sun" to DayOfWeek.SUNDAY,
    )

    private val MONTHS =
        listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    private fun on(iso: String): LocalDate? = runCatching { LocalDate.parse(iso) }.getOrNull()

    /**
     * The nearest [day] on or after [from]. `strict` pushes it a full week out,
     * which is what "next Friday" means when today is already a Friday — and,
     * read the same way, what it means on any other day too.
     */
    private fun advance(from: LocalDate, day: DayOfWeek, strict: Boolean): LocalDate {
        val delta = ((day.value - from.dayOfWeek.value) + 7) % 7
        return from.plusDays(if (strict) (delta + 7).toLong() else delta.toLong())
    }

    /**
     * Read a typed date as loosely as it can be read without guessing wrongly:
     * an ISO day, a weekday, a relative amount, or `d/m`. Null when it is none
     * of those, so a half-typed word simply offers nothing yet.
     */
    fun parse(input: String, todayIso: String): String? {
        val today = on(todayIso) ?: return null
        var text = input.trim().lowercase()
        if (text.isEmpty()) return null

        ISO.find(text)?.let { m ->
            val (y, mo, d) = m.destructured
            return runCatching { LocalDate.of(y.toInt(), mo.toInt(), d.toInt()).toString() }.getOrNull()
        }

        when (text) {
            "today", "tod", "now" -> return today.toString()
            "tomorrow", "tmr", "tom", "imorgen" -> return today.plusDays(1).toString()
            "yesterday" -> return today.minusDays(1).toString()
            "weekend", "this weekend" -> return advance(today, DayOfWeek.SATURDAY, strict = false).toString()
            "next week" -> return advance(today, DayOfWeek.MONDAY, strict = true).toString()
            "next month" -> return today.plusMonths(1).toString()
            "eow", "end of week" -> return advance(today, DayOfWeek.FRIDAY, strict = false).toString()
        }

        var strict = false
        if (text.startsWith("next ")) {
            strict = true
            text = text.removePrefix("next ").trim()
        }
        WEEKDAYS[text]?.let { return advance(today, it, strict).toString() }
        if (strict) return null

        AMOUNT.find(text)?.let { m ->
            val n = m.groupValues[1].toLongOrNull() ?: return null
            return when (m.groupValues[2]) {
                "w", "week", "weeks" -> today.plusWeeks(n)
                "mo", "month", "months" -> today.plusMonths(n)
                else -> today.plusDays(n)
            }.toString()
        }

        DAY_MONTH.find(text)?.let { m ->
            val day = m.groupValues[1].toIntOrNull() ?: return null
            val month = m.groupValues[2].toIntOrNull() ?: return null
            // A bare day and month means the next one to come round, not one
            // that has already been and gone.
            val thisYear = runCatching { LocalDate.of(today.year, month, day) }.getOrNull() ?: return null
            return (if (thisYear.isBefore(today)) thisYear.plusYears(1) else thisYear).toString()
        }

        return null
    }

    /** A date said the way a person would say it, relative to today. */
    fun label(iso: String, todayIso: String): String {
        val date = on(iso) ?: return iso
        val today = on(todayIso) ?: return iso
        return when {
            date == today -> "Today"
            date == today.plusDays(1) -> "Tomorrow"
            date == today.minusDays(1) -> "Yesterday"
            date.isAfter(today) && date.isBefore(today.plusDays(7)) ->
                date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
            date.year == today.year -> "${date.dayOfMonth} ${MONTHS[date.monthValue - 1]}"
            else -> "${date.dayOfMonth} ${MONTHS[date.monthValue - 1]} ${date.year}"
        }
    }

    fun addDays(iso: String, days: Long): String? = on(iso)?.plusDays(days)?.toString()

    /**
     * The chips the due-date picker offers. Deduplicated against each other, so
     * "this weekend" does not appear a second time on a Friday evening.
     */
    fun dueChoices(todayIso: String): List<Choice> {
        val today = on(todayIso) ?: return listOf(Choice("No date", null))
        val offers = listOf(
            Choice("Today", today.toString()),
            Choice("Tomorrow", today.plusDays(1).toString()),
            Choice("Weekend", advance(today, DayOfWeek.SATURDAY, strict = false).toString()),
            Choice("Next week", advance(today, DayOfWeek.MONDAY, strict = true).toString()),
            Choice("In two weeks", today.plusWeeks(2).toString()),
        )
        val seen = mutableSetOf<String?>()
        return listOf(Choice("No date", null)) + offers.filter { seen.add(it.iso) }
    }
}
