package org.fossify.messages.helpers

import android.content.Context
import org.fossify.commons.extensions.formatDateOrTime
import org.fossify.commons.extensions.getTimeFormat
import org.fossify.commons.extensions.getTimeFormatWithSeconds
import org.fossify.messages.extensions.config
import org.joda.time.DateTime
import org.joda.time.LocalDate

/**
 * A spelled-out date reads unambiguously at a glance: "Wed · Sep 19, 2026 · 8:03 PM" cannot be
 * misread the way 09/19 and 19/09 can. Commons' own numeric formats stay available behind a
 * setting for anyone who prefers them.
 *
 * Middots separate the parts that answer different questions - which weekday, which date, what
 * time - leaving the comma to do only the job it is good at, holding a year onto a date.
 */
private const val SEPARATOR = " · "
private const val WEEKDAY = "EEE"
private const val DAY = "MMM d"
private const val DAY_YEAR = "MMM d, yyyy"

/** Commons hands back "hh:mm a", which renders 8pm as "08:03 PM". Drop the padding zero. */
private fun Context.verboseTimePattern(withSeconds: Boolean = false): String {
    val commonsPattern = if (withSeconds) getTimeFormatWithSeconds() else getTimeFormat()
    return if (commonsPattern.contains("a")) {
        if (withSeconds) "h:mm:ss a" else "h:mm a"
    } else {
        commonsPattern
    }
}

private fun Long.daysAgo() = org.joda.time.Days
    .daysBetween(LocalDate(this), LocalDate.now())
    .days

private fun Long.isThisYear() = DateTime(this).year == DateTime.now().year

/**
 * The day something happened, as a reader would say it: "Today", "Yesterday", or the date itself.
 * Returns null when relative days are switched off or the date is too old to name.
 */
private fun Long.relativeDay(context: Context): String? {
    if (!context.config.useRelativeDays) {
        return null
    }

    return when (daysAgo()) {
        0 -> context.getString(org.fossify.commons.R.string.today)
        1 -> context.getString(org.fossify.commons.R.string.yesterday)
        else -> null
    }
}

/** The weekday and date, dropping the year while it is still the current one. */
private fun Long.formatDay(context: Context): String {
    relativeDay(context)?.let { return it }

    val pattern = if (isThisYear()) DAY else DAY_YEAR
    return DateTime(this).toString("$WEEKDAY'$SEPARATOR'$pattern")
}

/**
 * Always names the day, because that is the point of asking: "Today · 8:03 PM",
 * "Wed · Sep 19 · 8:03 PM", or "Wed · Sep 19, 2026 · 8:03 PM" once the year has turned.
 * Used under an opened message, in the message properties and on the thread's date separators.
 */
fun Long.formatMessageDateTime(context: Context, withSeconds: Boolean = false): String {
    if (!context.config.useVerboseDateFormat) {
        val timePattern = if (withSeconds) {
            context.getTimeFormatWithSeconds()
        } else {
            context.getTimeFormat()
        }
        return DateTime(this).toString("${context.config.dateFormat} $timePattern")
    }

    val time = DateTime(this).toString(context.verboseTimePattern(withSeconds))
    return "${formatDay(context)}$SEPARATOR$time"
}

/** Kept for the thread's date separators, which want the same thing the message details want. */
fun Long.formatMessageDateTimeCompact(context: Context): String {
    if (!context.config.useVerboseDateFormat) {
        return formatDateOrTime(context, hideTimeOnOtherDays = false, showCurrentYear = false)
    }

    return formatMessageDateTime(context)
}

/**
 * The conversations list, where the date sits in a narrow column beside the name. A full timestamp
 * would truncate, so today gives a time and everything else gives a date alone.
 */
fun Long.formatConversationDate(context: Context): String {
    if (!context.config.useVerboseDateFormat) {
        return formatDateOrTime(context, hideTimeOnOtherDays = true, showCurrentYear = false)
    }

    if (daysAgo() == 0) {
        return DateTime(this).toString(context.verboseTimePattern())
    }

    return formatDay(context)
}
