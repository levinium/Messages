package org.fossify.messages.helpers

import org.fossify.messages.models.Message

/**
 * The replies carriers honor as an opt-out, per the CTIA messaging guidelines. Matched against
 * the whole message, the way short code services match them.
 */
private val OPT_OUT_KEYWORDS =
    setOf("STOP", "STOPALL", "UNSUBSCRIBE", "CANCEL", "END", "QUIT", "OPTOUT", "REVOKE")

private fun Message.isOptOutReply() =
    !isReceivedMessage() && body.trim().uppercase() in OPT_OUT_KEYWORDS

/** Every received message that has something to forward to 7726, newest first. */
fun List<Message>.reportableAsSpam(): List<Message> {
    return filter { it.isReceivedMessage() && it.body.isNotBlank() }.sortedByDescending { it.date }
}

/**
 * The message a report most likely means.
 *
 * Usually the newest one, except once you have replied STOP: what arrives after that is the
 * sender confirming you are unsubscribed, and reporting the confirmation reports nothing. So the
 * suggestion is the newest message from before your last opt-out, when there is one.
 */
fun List<Message>.suggestedSpamReport(): Message? {
    val reportable = reportableAsSpam()
    val lastOptOut = filter { it.isOptOutReply() }.maxOfOrNull { it.date }
        ?: return reportable.firstOrNull()

    return reportable.firstOrNull { it.date < lastOptOut } ?: reportable.firstOrNull()
}
