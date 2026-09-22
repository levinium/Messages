package org.fossify.messages.dialogs

import android.app.Activity
import android.content.res.ColorStateList
import android.util.TypedValue
import android.widget.GridLayout
import androidx.appcompat.app.AlertDialog
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getTextSize
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.messages.R
import org.fossify.messages.databinding.DialogReactionPickerBinding
import org.fossify.messages.databinding.ItemMessageReactionOptionBinding

/**
 * The emoji beyond the six standard tapbacks.
 *
 * Whatever is picked here travels as `Reacted <emoji> to "the message"`, which is how an iPhone
 * sends a reaction it has no word for.
 */
class ReactionPickerDialog(
    private val activity: Activity,
    private val selected: String?,
    private val callback: (emoji: String) -> Unit,
) {
    private var dialog: AlertDialog? = null
    private val binding = DialogReactionPickerBinding.inflate(activity.layoutInflater)

    init {
        val emojiSize = activity.getTextSize() * EMOJI_SCALE
        val selectionColor = activity.getProperPrimaryColor().adjustAlpha(SELECTION_ALPHA)

        activity.resources.getStringArray(R.array.reaction_emoji).forEach { emoji ->
            val option = ItemMessageReactionOptionBinding.inflate(
                activity.layoutInflater, binding.reactionPickerGrid, false
            )

            option.reactionOption.apply {
                text = emoji
                setTextSize(TypedValue.COMPLEX_UNIT_PX, emojiSize)
            }

            if (emoji == selected) {
                option.reactionOptionSelection.apply {
                    beVisible()
                    backgroundTintList = ColorStateList.valueOf(selectionColor)
                }
            }

            option.root.layoutParams = GridLayout.LayoutParams().apply {
                width = activity.resources.getDimensionPixelSize(R.dimen.reaction_picker_option_size)
                height = width
            }

            option.root.setOnClickListener {
                dialog?.dismiss()
                callback(emoji)
            }

            binding.reactionPickerGrid.addView(option.root)
        }

        activity.getAlertDialogBuilder()
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this, R.string.pick_a_reaction) { alertDialog ->
                    dialog = alertDialog
                }
            }
    }

    private companion object {
        /** Big enough to pick out of a grid without turning the dialog into a keyboard. */
        const val EMOJI_SCALE = 1.5f
        const val SELECTION_ALPHA = 0.25f
    }
}
