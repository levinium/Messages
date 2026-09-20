// Unicode code points are the constants here; naming each boundary would only obscure them.
@file:Suppress("MagicNumber")

package org.fossify.messages.helpers

/** Messages made only of emoji are rendered larger, the way most messaging apps do it. */
const val MAX_ENLARGED_EMOJI = 6

/**
 * Whether [this] is nothing but emoji and whitespace, with at least one emoji in it.
 *
 * Deliberately conservative: anything it is not sure about counts as text, because rendering an
 * ordinary message at double size is far more jarring than missing the effect on an unusual one.
 * Digits are rejected outright, which costs us keycap emoji like 1️⃣ but avoids enlarging a bare
 * "2" or a verification code.
 */
fun String.isEmojiOnly(): Boolean {
    if (isBlank()) {
        return false
    }

    var emojiCount = 0
    var index = 0
    while (index < length) {
        val codePoint = codePointAt(index)
        index += Character.charCount(codePoint)

        when {
            Character.isWhitespace(codePoint) -> Unit
            isEmojiModifier(codePoint) -> Unit
            isEmojiCodePoint(codePoint) -> emojiCount++
            else -> return false
        }
    }

    return emojiCount > 0
}

/** How many emoji [this] contains, ignoring the joiners and modifiers that decorate them. */
fun String.emojiCount(): Int {
    var count = 0
    var index = 0
    while (index < length) {
        val codePoint = codePointAt(index)
        index += Character.charCount(codePoint)
        if (isEmojiCodePoint(codePoint) && !isEmojiModifier(codePoint)) {
            count++
        }
    }

    return count
}

/** Zero-width joiners, variation selectors and skin tones: parts of an emoji, not emoji in itself. */
private fun isEmojiModifier(codePoint: Int): Boolean {
    return codePoint == ZERO_WIDTH_JOINER ||
            codePoint in VARIATION_SELECTORS ||
            codePoint in SKIN_TONE_MODIFIERS ||
            codePoint in COMBINING_MARKS_FOR_SYMBOLS
}

private fun isEmojiCodePoint(codePoint: Int): Boolean {
    return isEmojiModifier(codePoint) || EMOJI_RANGES.any { codePoint in it }
}

private const val ZERO_WIDTH_JOINER = 0x200D
private val VARIATION_SELECTORS = 0xFE00..0xFE0F
private val SKIN_TONE_MODIFIERS = 0x1F3FB..0x1F3FF
private val COMBINING_MARKS_FOR_SYMBOLS = 0x20D0..0x20FF

private val EMOJI_RANGES = listOf(
    0x203C..0x203C, // double exclamation mark
    0x2049..0x2049, // exclamation question mark
    0x2190..0x21FF, // arrows
    0x2300..0x23FF, // miscellaneous technical, the clocks and the hourglasses
    0x25A0..0x25FF, // geometric shapes
    0x2600..0x27BF, // miscellaneous symbols and dingbats
    0x2B00..0x2BFF, // miscellaneous symbols and arrows
    0x1F000..0x1F0FF, // mahjong, dominoes and playing cards
    0x1F100..0x1F1FF, // enclosed alphanumeric supplement, includes the flag letters
    0x1F200..0x1F2FF, // enclosed ideographic supplement
    0x1F300..0x1F5FF, // miscellaneous symbols and pictographs
    0x1F600..0x1F64F, // emoticons
    0x1F680..0x1F6FF, // transport and map symbols
    0x1F700..0x1F77F, // alchemical symbols
    0x1F900..0x1F9FF, // supplemental symbols and pictographs
    0x1FA00..0x1FAFF, // symbols and pictographs extended-A
)
