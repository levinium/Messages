package org.fossify.messages.dialogs

import android.content.Context
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.messages.R
import org.fossify.commons.R as CommonsR
import org.fossify.messages.helpers.BlockRuleMatching.Problem
import org.fossify.messages.models.BlockCondition
import org.fossify.messages.models.BlockCondition.Type
import org.fossify.messages.models.filledValues

/** The words and styling the blocking rule editor shows, kept apart from its layout logic. */

internal fun blockConditionTypeLabel(type: Type) = when (type) {
    Type.CONTAINS -> R.string.block_condition_contains
    Type.WORD -> R.string.block_condition_word
    Type.NOT_CONTAINS -> R.string.block_condition_not_contains
    Type.REGEX -> R.string.block_condition_regex
}

internal fun Context.blockProblemText(problem: Problem?) = when (problem) {
    Problem.INVALID_PATTERN -> getString(R.string.block_condition_error_pattern)
    Problem.MATCHES_EVERYTHING -> getString(R.string.block_condition_error_everything)
    Problem.EMPTY, null -> null
}

/** What a condition does, saying "any one of these" once it has more than one value. */
internal fun Context.blockConditionHelp(condition: BlockCondition): String {
    val several = condition.filledValues.size > 1
    val base = getString(
        when (condition.type) {
            Type.CONTAINS -> R.string.block_condition_help_contains
            Type.WORD -> R.string.block_condition_help_word
            Type.NOT_CONTAINS -> if (several) {
                R.string.block_condition_help_not_contains_any
            } else {
                R.string.block_condition_help_not_contains
            }
            Type.REGEX -> R.string.block_condition_help_regex
        }
    )
    val needsAny = several && condition.type != Type.NOT_CONTAINS
    return if (needsAny) getString(R.string.block_condition_help_any, base) else base
}

internal fun Context.tintedDrawable(drawableId: Int, color: Int) =
    AppCompatResources.getDrawable(this, drawableId)?.mutate()?.apply { setTint(color) }

/** A text link that adds something: primary color, with a plus in front. */
internal fun TextView.styleAsAddAction() {
    val color = context.getProperPrimaryColor()
    setTextColor(color)
    setCompoundDrawablesRelativeWithIntrinsicBounds(
        context.tintedDrawable(CommonsR.drawable.ic_plus_vector, color), null, null, null
    )
}
