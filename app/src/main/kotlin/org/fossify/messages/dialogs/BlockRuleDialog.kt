package org.fossify.messages.dialogs

import android.graphics.Typeface
import android.view.ContextThemeWrapper
import android.view.Gravity
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.core.graphics.ColorUtils
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beInvisibleIf
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.getPopupMenuTheme
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.onTextChangeListener
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.showKeyboard
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.value
import org.fossify.messages.R
import org.fossify.commons.R as CommonsR
import org.fossify.messages.databinding.DialogBlockRuleBinding
import org.fossify.messages.databinding.ItemBlockRuleConditionBinding
import org.fossify.messages.databinding.ItemBlockRuleValueBinding
import org.fossify.messages.extensions.config
import org.fossify.messages.helpers.BlockRuleMatching
import org.fossify.messages.models.BlockCondition
import org.fossify.messages.models.BlockCondition.Type
import org.fossify.messages.models.BlockRule
import org.fossify.messages.models.filledValues

/**
 * Creates or edits a blocking rule, laid out the way a mail flow rule is: conditions joined by AND,
 * each with its own kind and a list of words joined by OR. Every value has a box of its own, so a
 * phrase with spaces in it is just typed as it is.
 *
 * The box at the bottom tries the rule on any message pasted into it, marking each condition as it
 * goes, which is the quickest way to see what a pattern does. OK stays disabled while the rule
 * could not be saved, and the box at fault says why.
 */
class BlockRuleDialog(
    private val activity: BaseSimpleActivity,
    private val originalRule: BlockRule? = null,
    private val callback: () -> Unit,
) {
    private val binding = DialogBlockRuleBinding.inflate(activity.layoutInflater)
    private val rows = mutableListOf<ConditionRow>()
    private var dialog: AlertDialog? = null

    private class ConditionRow(val binding: ItemBlockRuleConditionBinding, var type: Type) {
        val values = mutableListOf<ItemBlockRuleValueBinding>()
    }

    init {
        val conditions = originalRule?.conditions ?: listOf(BlockCondition(Type.CONTAINS, ""))
        conditions.forEach { addRow(it) }

        binding.blockRuleAddCondition.apply {
            styleAsAddAction()
            setOnClickListener {
                addRow(BlockCondition(Type.CONTAINS, "")).values.first().valueText.requestFocus()
                refresh()
            }
        }
        binding.blockRuleMatchCase.isChecked = originalRule?.matchCase == true
        binding.blockRuleMatchCase.setOnCheckedChangeListener { _, _ -> refresh() }
        binding.blockRuleOnlyUnknown.isChecked = originalRule?.onlyUnknownSenders == true
        binding.blockRuleOnlyUnknown.setOnCheckedChangeListener { _, _ -> refresh() }
        binding.blockRuleDivider.setBackgroundColor(activity.getProperTextColor())
        binding.blockRuleTestEdittext.onTextChangeListener { refresh() }

        activity.getAlertDialogBuilder()
            .setPositiveButton(CommonsR.string.ok, null)
            .setNegativeButton(CommonsR.string.cancel, null)
            .apply {
                val title = if (originalRule == null) R.string.block_rule_new else R.string.block_rule_edit
                activity.setupDialogStuff(binding.root, this, title) { alertDialog ->
                    dialog = alertDialog
                    alertDialog.showKeyboard(rows.first().values.first().valueText)
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        save()
                        alertDialog.dismiss()
                    }
                    refresh()
                }
            }
    }

    private fun addRow(condition: BlockCondition): ConditionRow {
        val rowBinding =
            ItemBlockRuleConditionBinding.inflate(activity.layoutInflater, binding.blockRuleConditions, false)
        val row = ConditionRow(rowBinding, condition.type)
        rows += row
        binding.blockRuleConditions.addView(rowBinding.root)
        activity.updateTextColors(rowBinding.root)
        // Each condition is a card of its own and AND a pill between cards, so what sits side by
        // side inside a card (OR) never reads like what joins the cards.
        val textColor = activity.getProperTextColor()
        val primaryColor = activity.getProperPrimaryColor()
        rowBinding.conditionCard.background.mutate().setTint(ColorUtils.setAlphaComponent(textColor, CARD_ALPHA))
        rowBinding.conditionAnd.background.mutate().setTint(ColorUtils.setAlphaComponent(primaryColor, PILL_ALPHA))
        rowBinding.conditionAnd.setTextColor(primaryColor)

        condition.values.ifEmpty { listOf("") }.forEach { addValue(row, it) }
        rowBinding.conditionAddValue.apply {
            styleAsAddAction()
            setOnClickListener {
                addValue(row, "").valueText.requestFocus()
                refresh()
            }
        }
        rowBinding.conditionRemove.applyColorFilter(activity.getProperTextColor())
        rowBinding.conditionRemove.setOnClickListener {
            rows.remove(row)
            binding.blockRuleConditions.removeView(rowBinding.root)
            refresh()
        }
        rowBinding.conditionType.setOnClickListener { pickType(row) }
        showType(row)
        return row
    }

    private fun addValue(row: ConditionRow, text: String): ItemBlockRuleValueBinding {
        val valueBinding =
            ItemBlockRuleValueBinding.inflate(activity.layoutInflater, row.binding.conditionValues, false)
        row.values += valueBinding
        row.binding.conditionValues.addView(valueBinding.root)
        activity.updateTextColors(valueBinding.root)

        valueBinding.valueText.setText(text)
        valueBinding.valueText.onTextChangeListener { refresh() }
        valueBinding.valueRemove.applyColorFilter(activity.getProperTextColor())
        valueBinding.valueRemove.setOnClickListener {
            row.values.remove(valueBinding)
            row.binding.conditionValues.removeView(valueBinding.root)
            refresh()
        }
        styleValue(row.type, valueBinding)
        return valueBinding
    }

    private fun pickType(row: ConditionRow) {
        val contextTheme = ContextThemeWrapper(activity, activity.getPopupMenuTheme())
        PopupMenu(contextTheme, row.binding.conditionType, Gravity.START).apply {
            Type.entries.forEachIndexed { index, type -> menu.add(0, index, index, blockConditionTypeLabel(type)) }
            setOnMenuItemClickListener { item ->
                row.type = Type.entries[item.itemId]
                showType(row)
                refresh()
                true
            }
            show()
        }
    }

    private fun showType(row: ConditionRow) {
        row.binding.apply {
            conditionType.text = activity.getString(blockConditionTypeLabel(row.type))
            val chevron =
                activity.tintedDrawable(CommonsR.drawable.ic_chevron_down_vector, activity.getProperTextColor())
            conditionType.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, chevron, null)
            conditionAddValue.text = activity.getString(
                if (row.type == Type.REGEX) R.string.block_rule_add_value_regex else R.string.block_rule_add_value
            )
        }
        row.values.forEach { styleValue(row.type, it) }
    }

    private fun styleValue(type: Type, value: ItemBlockRuleValueBinding) {
        val isRegex = type == Type.REGEX
        value.valueHint.hint = activity.getString(
            if (isRegex) R.string.block_condition_hint_regex else R.string.block_condition_hint_text
        )
        value.valueText.typeface = if (isRegex) Typeface.MONOSPACE else Typeface.DEFAULT
    }

    private fun currentRule() = BlockRule(
        id = originalRule?.id ?: BlockRule(conditions = emptyList()).id,
        conditions = rows.map { row -> BlockCondition(row.type, row.values.map { it.valueText.value }) },
        onlyUnknownSenders = binding.blockRuleOnlyUnknown.isChecked,
        matchCase = binding.blockRuleMatchCase.isChecked,
    )

    /** Brings every part of the dialog in line with what is typed: errors, the test, and OK. */
    private fun refresh() {
        val rule = currentRule()
        val testMessage = binding.blockRuleTestEdittext.value
        val testing = testMessage.isNotEmpty()
        var timedOut = false

        rows.forEachIndexed { index, row ->
            val condition = rule.conditions[index]
            row.binding.conditionAnd.beVisibleIf(index > 0)
            row.binding.conditionRemove.beVisibleIf(rows.size > 1)
            row.binding.conditionHelp.text = activity.blockConditionHelp(condition)
            row.values.forEachIndexed { valueIndex, value ->
                value.valueOr.beVisibleIf(valueIndex > 0)
                // Kept in place while hidden, so a lone box lines up with boxes that can be removed.
                value.valueRemove.beInvisibleIf(row.values.size < 2)
                val problem = BlockRuleMatching.problemWith(row.type, value.valueText.value)
                value.valueHint.error = activity.blockProblemText(problem)
            }

            if (testing && BlockRuleMatching.problemWith(condition) == null) {
                val satisfied = BlockRuleMatching.isSatisfied(condition, testMessage, rule.matchCase)
                timedOut = timedOut || satisfied == null
                showStatus(row, satisfied == true)
            } else {
                row.binding.conditionStatus.beGone()
            }
        }

        val usable = BlockRuleMatching.isUsable(rule)
        val onlyExclusions = rule.conditions.isNotEmpty() && !BlockRuleMatching.hasPositiveCondition(rule)
        binding.blockRuleNeedsPositive.beVisibleIf(onlyExclusions)
        // The dialog theme draws a disabled button just like an enabled one, so it is dimmed by hand.
        dialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
            isEnabled = usable
            alpha = if (usable) 1f else DISABLED
        }
        showTestResult(rule, testing && usable, timedOut)
    }

    private fun showStatus(row: ConditionRow, satisfied: Boolean) {
        row.binding.conditionStatus.apply {
            setImageResource(if (satisfied) CommonsR.drawable.ic_check_vector else CommonsR.drawable.ic_cross_vector)
            applyColorFilter(if (satisfied) activity.getProperPrimaryColor() else activity.getProperTextColor())
            alpha = if (satisfied) 1f else DIMMED
            beVisibleIf(true)
        }
    }

    private fun showTestResult(rule: BlockRule, show: Boolean, timedOut: Boolean) {
        binding.blockRuleTestResult.apply {
            beVisibleIf(show)
            if (!show) return
            // The box has no sender, so a rule limited to unknown senders is tried as if from one.
            val blocked = BlockRuleMatching.blocks(rule, binding.blockRuleTestEdittext.value) { true }
            val verdict = activity.getString(
                when {
                    blocked -> R.string.block_rule_test_blocked
                    timedOut -> R.string.block_rule_test_timeout
                    else -> R.string.block_rule_test_allowed
                }
            )
            text = if (blocked && rule.onlyUnknownSenders) {
                "$verdict\n${activity.getString(R.string.block_rule_test_unknown_note)}"
            } else {
                verdict
            }
            val color = if (blocked) activity.getProperPrimaryColor() else activity.getProperTextColor()
            setTextColor(color)
            val icon =
                if (blocked) CommonsR.drawable.ic_check_circle_vector else CommonsR.drawable.ic_minus_circle_vector
            setCompoundDrawablesRelativeWithIntrinsicBounds(activity.tintedDrawable(icon, color), null, null, null)
        }
    }

    private fun save() {
        // Empty boxes are dropped. A space at either end of a pattern means something, so only
        // words and phrases are trimmed.
        val rule = currentRule().let { rule ->
            rule.copy(
                conditions = rule.conditions.map { condition ->
                    val values = condition.filledValues
                    condition.copy(values = if (condition.type == Type.REGEX) values else values.map { it.trim() })
                }
            )
        }
        activity.config.saveBlockRule(rule)
        callback()
    }

    private companion object {
        const val DIMMED = 0.6f
        const val DISABLED = 0.4f
        const val CARD_ALPHA = 20
        const val PILL_ALPHA = 45
    }
}
