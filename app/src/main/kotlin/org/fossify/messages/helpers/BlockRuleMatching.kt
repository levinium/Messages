package org.fossify.messages.helpers

import org.fossify.messages.models.BlockCondition
import org.fossify.messages.models.BlockCondition.Type
import org.fossify.messages.models.BlockRule
import org.fossify.messages.models.filledValues
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/**
 * Matching ignores case unless the rule says otherwise. Inside a word or phrase, any run of spaces
 * matches any run of whitespace, so "polling location" still catches a message that wraps between
 * the two words. A regular expression is used as written, and can still switch case for part of
 * itself with (?i) and (?-i).
 *
 * Blank values are left out, so an empty box under a condition changes nothing. A pattern that
 * takes too long on one message is treated as not matching. Blocking is silent, so when in doubt
 * the message is let through.
 */
object BlockRuleMatching {

    private const val IGNORE_CASE = Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
    private const val MATCH_BUDGET_NANOS = 100_000_000L
    private const val NOT_WORD = "[\\p{L}\\p{N}_]"

    enum class Problem {
        /** A condition has no text in any of its values. */
        EMPTY,

        /** A regular expression does not compile. */
        INVALID_PATTERN,

        /** A regular expression matches an empty message, so it would block every message. */
        MATCHES_EVERYTHING,
    }

    /**
     * Whether one condition holds for one message, or null if that could not be decided: a pattern
     * ran out of time before any value settled it.
     */
    fun isSatisfied(condition: BlockCondition, body: String, matchCase: Boolean = false): Boolean? {
        if (problemWith(condition) != null) return null
        val wantsMatch = condition.type != Type.NOT_CONTAINS
        var undecided = false
        for (value in condition.filledValues) {
            when (find(condition.type, value, body, matchCase)) {
                true -> return wantsMatch
                null -> undecided = true
                false -> Unit
            }
        }
        return if (undecided) null else !wantsMatch
    }

    /**
     * [isUnknownSender] is only asked when the rule cares, and after the text has matched, since
     * looking a number up in the contacts costs more than matching a message.
     */
    fun blocks(rule: BlockRule, body: String, isUnknownSender: () -> Boolean): Boolean {
        return isUsable(rule) &&
            rule.conditions.all { isSatisfied(it, body, rule.matchCase) == true } &&
            (!rule.onlyUnknownSenders || isUnknownSender())
    }

    fun problemWith(condition: BlockCondition): Problem? {
        val values = condition.filledValues
        if (values.isEmpty()) return Problem.EMPTY
        return values.firstNotNullOfOrNull { problemWith(condition.type, it) }
    }

    /** What is wrong with one value, if anything. A blank value is not a problem; it is ignored. */
    fun problemWith(type: Type, value: String): Problem? {
        if (type != Type.REGEX || value.isBlank()) return null
        val pattern = try {
            Pattern.compile(value, IGNORE_CASE)
        } catch (_: PatternSyntaxException) {
            return Problem.INVALID_PATTERN
        }
        return if (pattern.matcher("").find()) Problem.MATCHES_EVERYTHING else null
    }

    /**
     * A rule made only of "doesn't contain" conditions would block nearly every message, so a
     * rule needs at least one condition that looks for something.
     */
    fun hasPositiveCondition(rule: BlockRule) = rule.conditions.any { it.type != Type.NOT_CONTAINS }

    fun isUsable(rule: BlockRule): Boolean {
        return rule.conditions.isNotEmpty() &&
            hasPositiveCondition(rule) &&
            rule.conditions.all { problemWith(it) == null }
    }

    /** Two rules that would block exactly the same messages, whatever order anything is in. */
    fun isSameRule(first: BlockRule, second: BlockRule): Boolean {
        fun key(rule: BlockRule) = Triple(
            rule.onlyUnknownSenders,
            rule.matchCase,
            rule.conditions.map { condition ->
                val values = condition.filledValues.map { normalize(condition.type, it, rule.matchCase) }
                condition.type to values.toSet()
            }.toSet(),
        )
        return key(first) == key(second)
    }

    private fun normalize(type: Type, value: String, matchCase: Boolean): String {
        if (type == Type.REGEX) return value
        val spaced = value.trim().replace(Regex("\\s+"), " ")
        return if (matchCase) spaced else spaced.lowercase()
    }

    private fun find(type: Type, value: String, body: String, matchCase: Boolean): Boolean? {
        val source = when (type) {
            Type.CONTAINS, Type.NOT_CONTAINS -> phrase(value)
            Type.WORD -> "(?<!$NOT_WORD)${phrase(value)}(?!$NOT_WORD)"
            Type.REGEX -> value
        }
        val pattern = Pattern.compile(source, if (matchCase) 0 else IGNORE_CASE)
        return try {
            pattern.matcher(DeadlineCharSequence(body, System.nanoTime() + MATCH_BUDGET_NANOS)).find()
        } catch (_: MatchTimeout) {
            null
        }
    }

    private object MatchTimeout : RuntimeException()

    /** Lets a runaway pattern be stopped: the regex engine reads every character through here. */
    private class DeadlineCharSequence(
        private val inner: CharSequence,
        private val deadline: Long,
    ) : CharSequence {
        override val length get() = inner.length

        override fun get(index: Int): Char {
            if (System.nanoTime() > deadline) throw MatchTimeout
            return inner[index]
        }

        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence =
            DeadlineCharSequence(inner.subSequence(startIndex, endIndex), deadline)

        override fun toString() = inner.toString()
    }
}

/** Each word quoted, with any run of whitespace between them. */
private fun phrase(text: String) =
    text.trim().split(Regex("\\s+")).joinToString("\\s+") { Pattern.quote(it) }
