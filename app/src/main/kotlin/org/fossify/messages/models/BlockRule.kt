package org.fossify.messages.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Blocks an incoming message when every one of its conditions holds, and, with [onlyUnknownSenders],
 * only when it comes from a number that is not in the contacts. Conditions ignore case unless
 * [matchCase] is set. A plain blocked keyword is a rule with a single
 * [BlockCondition.Type.CONTAINS] condition of one value.
 */
@Serializable
data class BlockRule(
    val id: String = UUID.randomUUID().toString(),
    val conditions: List<BlockCondition>,
    val onlyUnknownSenders: Boolean = false,
    val matchCase: Boolean = false,
)

/**
 * Holds when any one of its [values] is found, the way a mail flow rule takes a list of words. A
 * [Type.NOT_CONTAINS] condition is the reverse: it holds when none of them is.
 */
@Serializable
data class BlockCondition(
    val type: Type,
    val values: List<String>,
) {
    constructor(type: Type, value: String) : this(type, listOf(value))

    @Serializable
    enum class Type {
        /** The text appears anywhere, inside other words too. */
        @SerialName("contains")
        CONTAINS,

        /** The text appears as a whole word or phrase, not as part of a longer word. */
        @SerialName("word")
        WORD,

        /** The text does not appear anywhere. */
        @SerialName("not_contains")
        NOT_CONTAINS,

        /** A regular expression finds a match somewhere in the message. */
        @SerialName("regex")
        REGEX,
    }
}

/** The values with text in them; an empty box under a condition changes nothing. */
val BlockCondition.filledValues get() = values.filter { it.isNotBlank() }

/** The file written by exporting blocked keywords. */
@Serializable
data class BlockRulesExport(
    val version: Int = 1,
    val rules: List<BlockRule>,
)
