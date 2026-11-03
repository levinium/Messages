package org.fossify.messages.dialogs

import android.content.Context
import android.view.*
import androidx.appcompat.widget.PopupMenu
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.adapters.MyRecyclerViewAdapter
import org.fossify.commons.extensions.copyToClipboard
import org.fossify.commons.extensions.getPopupMenuTheme
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.setupViewBackground
import org.fossify.commons.interfaces.RefreshRecyclerViewListener
import org.fossify.commons.views.MyRecyclerView
import org.fossify.messages.R
import org.fossify.messages.databinding.ItemManageBlockedKeywordBinding
import org.fossify.messages.extensions.config
import org.fossify.messages.models.BlockCondition.Type
import org.fossify.messages.models.BlockRule

class ManageBlockedKeywordsAdapter(
    activity: BaseSimpleActivity, var blockRules: ArrayList<BlockRule>, val listener: RefreshRecyclerViewListener?,
    recyclerView: MyRecyclerView, itemClick: (Any) -> Unit
) : MyRecyclerViewAdapter(activity, recyclerView, itemClick) {
    init {
        setupDragListener(true)
    }

    override fun getActionMenuId() = R.menu.cab_blocked_keywords

    override fun prepareActionMode(menu: Menu) {
        menu.apply {
            findItem(R.id.cab_copy_keyword).isVisible = isOneItemSelected()
        }
    }

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_copy_keyword -> copyKeywordToClipboard()
            R.id.cab_delete -> deleteSelection()
        }
    }

    override fun getSelectableItemCount() = blockRules.size

    override fun getIsItemSelectable(position: Int) = true

    override fun getItemSelectionKey(position: Int) = blockRules.getOrNull(position)?.id?.hashCode()

    override fun getItemKeyPosition(key: Int) = blockRules.indexOfFirst { it.id.hashCode() == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemManageBlockedKeywordBinding.inflate(layoutInflater, parent, false)
        return createViewHolder(binding.root)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val rule = blockRules[position]
        holder.bindView(rule, allowSingleClick = true, allowLongClick = true) { itemView, _ ->
            setupView(itemView, rule)
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = blockRules.size

    private fun getSelectedItems() = blockRules.filter { selectedKeys.contains(it.id.hashCode()) }

    private fun setupView(view: View, rule: BlockRule) {
        ItemManageBlockedKeywordBinding.bind(view).apply {
            root.setupViewBackground(activity)
            manageBlockedKeywordHolder.isSelected = selectedKeys.contains(rule.id.hashCode())
            manageBlockedKeywordTitle.apply {
                text = describeBlockRule(activity, rule)
                setTextColor(textColor)
            }

            overflowMenuIcon.drawable.apply {
                mutate()
                setTint(activity.getProperTextColor())
            }

            overflowMenuIcon.setOnClickListener {
                showPopupMenu(overflowMenuAnchor, rule)
            }
        }
    }

    private fun showPopupMenu(view: View, rule: BlockRule) {
        finishActMode()
        val theme = activity.getPopupMenuTheme()
        val contextTheme = ContextThemeWrapper(activity, theme)

        PopupMenu(contextTheme, view, Gravity.END).apply {
            inflate(getActionMenuId())
            setOnMenuItemClickListener { item ->
                val ruleKey = rule.id.hashCode()
                when (item.itemId) {
                    R.id.cab_copy_keyword -> {
                        executeItemMenuOperation(ruleKey) {
                            copyKeywordToClipboard()
                        }
                    }

                    R.id.cab_delete -> {
                        executeItemMenuOperation(ruleKey) {
                            deleteSelection()
                        }
                    }
                }
                true
            }
            show()
        }
    }

    private fun executeItemMenuOperation(ruleKey: Int, callback: () -> Unit) {
        selectedKeys.add(ruleKey)
        callback()
        selectedKeys.remove(ruleKey)
    }

    /** A single word or phrase copies as itself; a rule with more to it copies as it reads. */
    private fun copyKeywordToClipboard() {
        val rule = getSelectedItems().firstOrNull() ?: return
        val single = rule.conditions.singleOrNull()?.values?.singleOrNull()
        activity.copyToClipboard(single ?: describeBlockRule(activity, rule))
        finishActMode()
    }

    private fun deleteSelection() {
        val positions = getSelectedItemPositions()
        val deleted = getSelectedItems()
        activity.config.removeBlockRules(deleted.map { it.id }.toSet())

        blockRules.removeAll(deleted.toSet())
        removeSelectedItems(positions)
        if (blockRules.isEmpty()) {
            listener?.refreshItems()
        }
    }
}

/** One line per condition, so "Contains “winner” or “prize”" over "and contains “claim”". */
fun describeBlockRule(context: Context, rule: BlockRule): String {
    val conditions = rule.conditions.mapIndexed { index, condition ->
        val first = index == 0
        val (opening, following) = when (condition.type) {
            Type.CONTAINS -> R.string.block_summary_contains to R.string.block_summary_and_contains
            Type.WORD -> R.string.block_summary_word to R.string.block_summary_and_word
            Type.NOT_CONTAINS -> R.string.block_summary_not_contains to R.string.block_summary_and_not_contains
            Type.REGEX -> R.string.block_summary_regex to R.string.block_summary_and_regex
        }
        val format = if (first) opening else following
        val or = " ${context.getString(R.string.block_rule_or)} "
        val values = condition.values.joinToString(or) { if (condition.type == Type.REGEX) it else "“$it”" }
        context.getString(format, values)
    }
    val options = listOfNotNull(
        context.getString(R.string.block_summary_match_case).takeIf { rule.matchCase },
        context.getString(R.string.block_summary_only_unknown).takeIf { rule.onlyUnknownSenders },
    )
    return (conditions + options).joinToString("\n")
}
