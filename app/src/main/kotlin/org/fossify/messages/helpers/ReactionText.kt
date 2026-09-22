package org.fossify.messages.helpers

import org.fossify.messages.extensions.isAudioMimeType
import org.fossify.messages.extensions.isImageMimeType
import org.fossify.messages.extensions.isVideoMimeType
import org.fossify.messages.models.Message

/**
 * The six tapbacks an iPhone offers, and the wording it falls back to when one has to travel as an
 * ordinary text rather than over iMessage or RCS.
 *
 * SMS has no reactions of its own, so this wording is the whole protocol: a phone that understands
 * it shows a tapback, and one that does not shows a readable sentence. Changing a word here breaks
 * the conversation with every other phone, so the strings stay in English and out of the
 * translations.
 */
enum class ReactionKind(val emoji: String, val verb: String, val removedNoun: String) {
    LOVE("❤️", "Loved", "a heart"),
    LIKE("👍", "Liked", "a like"),
    DISLIKE("👎", "Disliked", "a dislike"),
    LAUGH("😂", "Laughed at", "a laugh"),
    EMPHASIZE("‼️", "Emphasized", "an exclamation"),
    QUESTION("❓", "Questioned", "a question mark"),
}

/** What a reaction message was saying, once the wrapping has been taken off it. */
data class ParsedReaction(
    val emoji: String,
    val kind: ReactionKind?,
    val isRemoval: Boolean,
    /** The message text that was quoted, or null when the reaction named an attachment instead. */
    val quoted: String?,
)

/** Reads a message body as a tapback, or returns null if it is an ordinary message. */
fun String.parseReaction(): ParsedReaction? {
    val body = trim()
    if (body.isEmpty()) {
        return null
    }

    parseRemovedReaction(body)?.let { return it }
    parseEmojiReaction(body)?.let { return it }
    return parseNamedReaction(body)
}

/**
 * The text to send so the reaction reads the way an iPhone would have written it.
 *
 * Straight quotes rather than the curly ones iOS uses, because curly quotes push the whole message
 * out of the GSM alphabet and into a two byte encoding, which more than halves what fits in a
 * single text.
 */
fun formatReaction(
    kind: ReactionKind?,
    emoji: String,
    target: Message,
    isRemoval: Boolean,
): String {
    val quoted = target.quoteForReaction()
    return when {
        isRemoval && kind != null -> "$REMOVED_PREFIX${kind.removedNoun}$REMOVED_INFIX$quoted"
        isRemoval -> "$REMOVED_PREFIX$emoji$REMOVED_INFIX$quoted"
        kind != null -> "${kind.verb} $quoted"
        else -> "$REACTED_PREFIX$emoji$REACTED_INFIX$quoted"
    }
}

/** A long message is shortened before it is quoted, at both ends, so a prefix counts as a match. */
internal fun String.matchesQuote(quoted: String): Boolean {
    if (this == quoted) {
        return true
    }

    val cut = ELLIPSES.firstOrNull { quoted.endsWith(it) } ?: return false
    val prefix = quoted.dropLast(cut.length).trimEnd()
    return prefix.isNotEmpty() && startsWith(prefix)
}

private fun parseRemovedReaction(body: String): ParsedReaction? {
    if (!body.startsWith(REMOVED_PREFIX)) {
        return null
    }

    val rest = body.removePrefix(REMOVED_PREFIX)
    val split = rest.indexOf(REMOVED_INFIX)
    if (split == -1) {
        return null
    }

    val noun = rest.substring(0, split)
    val target = parseReactionTarget(rest.substring(split + REMOVED_INFIX.length)) ?: return null
    val kind = ReactionKind.entries.firstOrNull { it.removedNoun == noun }

    return when {
        kind != null -> ParsedReaction(kind.emoji, kind, isRemoval = true, quoted = target.quoted)
        noun.isEmojiOnly() -> ParsedReaction(noun, null, isRemoval = true, quoted = target.quoted)
        else -> null
    }
}

private fun parseEmojiReaction(body: String): ParsedReaction? {
    if (!body.startsWith(REACTED_PREFIX)) {
        return null
    }

    val rest = body.removePrefix(REACTED_PREFIX)
    val split = rest.indexOf(REACTED_INFIX)
    if (split == -1) {
        return null
    }

    val emoji = rest.substring(0, split)
    if (!emoji.isEmojiOnly()) {
        return null
    }

    val target = parseReactionTarget(rest.substring(split + REACTED_INFIX.length)) ?: return null
    return ParsedReaction(emoji, null, isRemoval = false, quoted = target.quoted)
}

private fun parseNamedReaction(body: String): ParsedReaction? {
    val kind = ReactionKind.entries.firstOrNull { body.startsWith("${it.verb} ") } ?: return null
    val target = parseReactionTarget(body.removePrefix("${kind.verb} ")) ?: return null
    return ParsedReaction(kind.emoji, kind, isRemoval = false, quoted = target.quoted)
}

/** What the reaction was pointed at: a quotation, or one of the phrases standing in for a file. */
private fun parseReactionTarget(target: String): ReactionTarget? {
    val trimmed = target.trim()
    if (trimmed.lowercase() in ATTACHMENT_PHRASES) {
        return ReactionTarget(quoted = null)
    }

    QUOTE_PAIRS.forEach { (open, close) ->
        if (trimmed.length > 2 && trimmed.first() == open && trimmed.last() == close) {
            return ReactionTarget(trimmed.substring(1, trimmed.length - 1))
        }
    }

    return null
}

private fun Message.quoteForReaction(): String {
    val text = body.trim()
    if (text.isEmpty()) {
        return attachmentPhrase()
    }

    val shortened = if (text.length > MAX_QUOTED_LENGTH) {
        text.take(MAX_QUOTED_LENGTH).trimEnd() + ELLIPSIS
    } else {
        text
    }

    return "\"$shortened\""
}

private fun Message.attachmentPhrase(): String {
    val mimetype = attachment?.attachments?.firstOrNull()?.mimetype.orEmpty()
    return when {
        mimetype.isImageMimeType() -> "an image"
        mimetype.isVideoMimeType() -> "a video"
        mimetype.isAudioMimeType() -> "an audio message"
        else -> "an attachment"
    }
}

private data class ReactionTarget(val quoted: String?)

private const val REACTED_PREFIX = "Reacted "
private const val REACTED_INFIX = " to "
private const val REMOVED_PREFIX = "Removed "
private const val REMOVED_INFIX = " from "

private const val ELLIPSIS = "…"
private val ELLIPSES = listOf(ELLIPSIS, "...")

/** Long enough to identify the message, short enough that the reaction still fits in one text. */
private const val MAX_QUOTED_LENGTH = 128

/** iOS quotes with curly quotes, other senders with straight ones, so both are read. */
private val QUOTE_PAIRS = listOf('“' to '”', '"' to '"')

/** What is said in place of the message text when the reaction landed on a file. */
private val ATTACHMENT_PHRASES = setOf(
    "an image",
    "a photo",
    "a picture",
    "a video",
    "a movie",
    "an audio message",
    "an attachment",
)
