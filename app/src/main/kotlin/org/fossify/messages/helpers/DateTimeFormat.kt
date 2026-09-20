package org.fossify.messages.helpers

import android.content.Context
import org.fossify.commons.extensions.formatDateOrTime
import org.fossify.commons.extensions.getTimeFormat
import org.fossify.commons.extensions.getTimeFormatWithSeconds
import org.fossify.messages.extensions.config
import org.joda.time.DateTime
import org.joda.time.LocalDate

/**
 * A spelled-out date reads unambiguously at a glance: "Wed, Sep 19, 2026, 8:03 PM" cannot be
 * misread the way 09/19 and 19/09 can. Commons' own numeric formats stay available behind a
 * setting for anyone who prefers them.
 */
private const val VERBOSE_DAY = "EEE, MMM d"
private const val VERBOSE_DAY_YEAR = "EEE, MMM d, yyyy"

/** Commons hands back "hh:mm a", which renders 8pm as "08:03 PM". Drop the padding zero. */
private fun Context.verboseTimePattern(withSeconds: Boolean = false): String {
    val commonsPattern = if (withSeconds) getTimeFormatWithSeconds() else getTimeFormat()
    return if (commonsPattern.contains("a")) {
        if (withSeconds) "h:mm:ss a" else "h:mm a"
    } else {
        commonsPattern
    }
}

private fun Long.isToday() = LocalDate(this) == LocalDate.now()

private fun Long.isThisYear() = DateTime(this).year == DateTime.now().year

/** The full thing: "Wed, Sep 19, 2026, 8:03 PM". Used under an opened message. */
fun Long.formatMessageDateTime(context: Context, withSeconds: Boolean = false): String {
    if (!context.config.useVerboseDateFormat) {
        val timePattern = if (withSeconds) {
            context.getTimeFormatWithSeconds()
        } else {
            context.getTimeFormat()
        }
        return DateTime(this).toString("${context.config.dateFormat} $timePattern")
    }

    return DateTime(this)
        .toString("$VERBOSE_DAY_YEAR, ${context.verboseTimePattern(withSeconds)}")
}

/**
 * Same idea, but drops what the reader can infer: today shows only a time, and this year drops
 * the year. Used for the date separators running down a conversation.
 */
fun Long.formatMessageDateTimeCompact(context: Context): String {
    if (!context.config.useVerboseDateFormat) {
        return formatDateOrTime(context, hideTimeOnOtherDays = false, showCurrentYear = false)
    }

    val time = context.verboseTimePattern()
    val pattern = when {
        isToday() -> time
        isThisYear() -> "$VERBOSE_DAY, $time"
        else -> "$VERBOSE_DAY_YEAR, $time"
    }

    return DateTime(this).toString(pattern)
}

/**
 * The conversations list, where the date sits in a narrow column beside the name. A full timestamp
 * would truncate, so this gives the date alone once the message is older than today.
 */
fun Long.formatConversationDate(context: Context): String {
    if (!context.config.useVerboseDateFormat) {
        return formatDateOrTime(context, hideTimeOnOtherDays = true, showCurrentYear = false)
    }

    val pattern = when {
        isToday() -> context.verboseTimePattern()
        isThisYear() -> VERBOSE_DAY
        else -> VERBOSE_DAY_YEAR
    }

    return DateTime(this).toString(pattern)
}
