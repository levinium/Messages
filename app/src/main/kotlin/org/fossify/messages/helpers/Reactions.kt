package org.fossify.messages.helpers

import android.provider.Telephony
import org.fossify.messages.models.Message
import org.fossify.messages.models.MessageReaction

/**
 * Folds the conversation's reaction messages into the messages they were aimed at.
 *
 * Every tapback arrives as a text of its own saying something like Loved "see you at seven", so
 * left alone the thread fills up with sentences nobody wrote. One that finds the message it quotes
 * is taken off the list and drawn on that message instead. One that finds nothing stays exactly
 * where it is, because a message the user cannot see at all is worse than an oddly worded one.
 *
 * Expects the list in the order the thread shows it, oldest first.
 */
fun List<Message>.withReactionsApplied(): List<Message> {
    val parsed = map { if (it.canFoldAway()) it.body.parseReaction() else null }
    if (parsed.none { it != null }) {
        return this
    }

    val applied = HashMap<Long, MutableList<MessageReaction>>()
    val absorbed = HashSet<Long>()
    for (index in indices) {
        val reaction = parsed[index] ?: continue
        val target = findReactionTarget(index, reaction, parsed)
        if (target != null) {
            absorbed.add(this[index].id)
            applied.getOrPut(target.id) { mutableListOf() }.applyReaction(this[index], reaction)
        }
    }

    if (absorbed.isEmpty()) {
        return this
    }

    return filterNot { it.id in absorbed }.map { message ->
        val reactions = applied[message.id]
        if (reactions.isNullOrEmpty()) message else message.withReactions(reactions.toList())
    }
}

/** Whether a reaction can be put on this message at all. */
fun Message.acceptsReactions(): Boolean {
    if (isScheduled || type == Telephony.Sms.MESSAGE_TYPE_FAILED) {
        return false
    }

    return body.isNotEmpty() || attachment?.attachments?.isNotEmpty() == true
}

/** A reaction that never made it out stays a bubble of its own, so it can be seen and retried. */
private fun Message.canFoldAway() = type != Telephony.Sms.MESSAGE_TYPE_FAILED

/**
 * Walks back from a reaction to the message it quotes.
 *
 * Only the messages above it are considered, and other reactions are skipped, so a tapback never
 * lands on the sentence that carried another one.
 */
private fun List<Message>.findReactionTarget(
    reactionIndex: Int,
    reaction: ParsedReaction,
    parsed: List<ParsedReaction?>,
): Message? {
    for (index in reactionIndex - 1 downTo 0) {
        val candidate = if (parsed[index] == null) this[index] else null
        if (candidate != null && reaction.matches(candidate)) {
            return candidate
        }
    }

    return null
}

private fun ParsedReaction.matches(candidate: Message): Boolean {
    val quoted = quoted
        ?: return candidate.body.isBlank() &&
                candidate.attachment?.attachments?.isNotEmpty() == true

    val body = candidate.body.trim()
    return body.isNotEmpty() && body.matchesQuote(quoted)
}

/** Applies one reaction, keeping to the iPhone's rule of a single tapback per person per message. */
private fun MutableList<MessageReaction>.applyReaction(from: Message, reaction: ParsedReaction) {
    val isFromMe = !from.isReceivedMessage()
    val senderKey = if (isFromMe) OWN_REACTION_KEY else from.senderPhoneNumber
    val existing = indexOfFirst { it.senderKey == senderKey }

    if (reaction.isRemoval) {
        if (existing != -1 && this[existing].emoji == reaction.emoji) {
            removeAt(existing)
        }
        return
    }

    val applied = MessageReaction(
        emoji = reaction.emoji,
        messageId = from.id,
        senderKey = senderKey,
        senderName = from.senderName,
        isFromMe = isFromMe,
        date = from.date,
    )

    if (existing != -1) {
        this[existing] = applied
    } else {
        add(applied)
    }
}

/** Reactions the user picked for themselves are all filed under one key, whatever the SIM. */
private const val OWN_REACTION_KEY = "\u0000me"
