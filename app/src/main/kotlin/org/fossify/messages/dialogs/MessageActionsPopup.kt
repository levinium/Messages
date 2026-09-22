package org.fossify.messages.dialogs

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.getBottomNavigationBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.getTextSize
import org.fossify.commons.extensions.isDynamicTheme
import org.fossify.commons.extensions.realScreenSize
import org.fossify.messages.R
import org.fossify.messages.databinding.ItemMessageActionBinding
import org.fossify.messages.databinding.ItemMessageReactionMoreBinding
import org.fossify.messages.databinding.ItemMessageReactionOptionBinding
import org.fossify.messages.databinding.PopupMessageActionsBinding
import org.fossify.messages.helpers.ReactionKind

/** One line in the menu a long press opens. */
class MessageAction(
    @DrawableRes val icon: Int,
    @StringRes val label: Int,
    val onSelected: () -> Unit,
)

/** The tapback strip above those lines, left off when the conversation cannot be replied to. */
class MessageReactionRow(
    val chosen: String?,
    val onPick: (emoji: String) -> Unit,
    val onPickOther: () -> Unit,
)

/**
 * Everything you can do to one message, floating beside the message itself.
 *
 * Anchored to the bubble rather than centered like the app's other dialogs, because the whole
 * point of it is which message it belongs to: it opens on the bubble's own side of the thread,
 * above it when there is room below the fold and below it otherwise.
 */
class MessageActionsPopup(
    private val activity: Activity,
    private val anchor: View,
    private val alignToEnd: Boolean,
    private val reactions: MessageReactionRow?,
    private val actions: List<MessageAction>,
) {
    private val binding = PopupMessageActionsBinding.inflate(activity.layoutInflater)
    private val popup = PopupWindow(
        binding.root,
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
        true
    )

    fun show() {
        val textColor = activity.getProperTextColor()
        binding.root.background.mutate().apply {
            setTint(popupColor())
        }
        binding.root.minimumWidth =
            activity.resources.getDimensionPixelSize(R.dimen.message_actions_min_width)

        reactions?.let { addReactions(it) }
        binding.messageActionsDivider.apply {
            if (reactions != null) {
                beVisible()
                setBackgroundColor(textColor.adjustAlpha(DIVIDER_ALPHA))
            }
        }

        actions.forEach { addAction(it, textColor) }

        popup.apply {
            elevation = activity.resources.getDimension(org.fossify.commons.R.dimen.medium_margin)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
        }

        val position = placeBeside()
        popup.showAtLocation(anchor, Gravity.NO_GRAVITY, position.first, position.second)
    }

    private fun addReactions(row: MessageReactionRow) {
        binding.messageActionsReactions.beVisible()
        val strip = binding.messageActionsReactions
        val fontSize = activity.getTextSize()

        ReactionKind.entries.forEach { kind ->
            val option = ItemMessageReactionOptionBinding.inflate(activity.layoutInflater, strip, false)
            option.reactionOption.apply {
                text = kind.emoji
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)
            }

            if (kind.emoji == row.chosen) {
                option.reactionOptionSelection.apply {
                    beVisible()
                    backgroundTintList = ColorStateList.valueOf(
                        activity.getProperPrimaryColor().adjustAlpha(SELECTION_ALPHA)
                    )
                }
            }

            option.root.contentDescription = kind.emoji
            option.root.setOnClickListener {
                popup.dismiss()
                row.onPick(kind.emoji)
            }
            strip.addView(option.root)
        }

        val more = ItemMessageReactionMoreBinding.inflate(activity.layoutInflater, strip, false)
        more.reactionMore.applyColorFilter(activity.getProperTextColor())
        more.root.contentDescription = activity.getString(R.string.pick_a_reaction)
        more.root.setOnClickListener {
            popup.dismiss()
            row.onPickOther()
        }
        strip.addView(more.root)
    }

    private fun addAction(action: MessageAction, textColor: Int) {
        val row = ItemMessageActionBinding.inflate(
            activity.layoutInflater, binding.messageActionsItems, false
        )

        row.messageActionIcon.setImageResource(action.icon)
        row.messageActionIcon.applyColorFilter(textColor)
        row.messageActionLabel.apply {
            text = activity.getString(action.label)
            setTextColor(textColor)
        }

        row.root.setOnClickListener {
            popup.dismiss()
            action.onSelected()
        }

        binding.messageActionsItems.addView(row.root)
    }

    /**
     * Where the menu goes: on the bubble's own side, and on whichever side of it has the room.
     */
    private fun placeBeside(): Pair<Int, Int> {
        val unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        binding.root.measure(unspecified, unspecified)
        val width = binding.root.measuredWidth
        val height = binding.root.measuredHeight

        val screen = activity.realScreenSize
        val margin = activity.resources.getDimensionPixelSize(
            org.fossify.commons.R.dimen.activity_margin
        )

        val bounds = IntArray(2)
        anchor.getLocationOnScreen(bounds)
        val anchorLeft = bounds[0]
        val anchorTop = bounds[1]
        val anchorBottom = anchorTop + anchor.height

        val preferredX = if (alignToEnd) anchorLeft + anchor.width - width else anchorLeft
        val x = preferredX.coerceIn(margin, (screen.x - width - margin).coerceAtLeast(margin))

        val below = anchorBottom + margin
        val y = if (below + height + margin <= screen.y) {
            below
        } else {
            (anchorTop - margin - height).coerceAtLeast(margin)
        }

        return x to y
    }

    private fun popupColor(): Int {
        return if (activity.isDynamicTheme()) {
            activity.resources.getColor(
                org.fossify.commons.R.color.you_dialog_background_color, activity.theme
            )
        } else {
            activity.getBottomNavigationBackgroundColor()
        }
    }

    private companion object {
        const val DIVIDER_ALPHA = 0.2f
        const val SELECTION_ALPHA = 0.25f
    }
}
