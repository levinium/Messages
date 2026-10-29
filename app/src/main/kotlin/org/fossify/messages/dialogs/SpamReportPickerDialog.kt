package org.fossify.messages.dialogs

import android.app.Activity
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.app.AlertDialog
import org.fossify.commons.databinding.DialogRadioGroupBinding
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.onGlobalLayout
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.messages.R
import org.fossify.messages.helpers.formatMessageDateTimeCompact
import org.fossify.messages.models.Message

/**
 * Which of a conversation's messages to forward to 7726.
 *
 * Listed oldest first, the way the conversation itself reads, and opened scrolled to the bottom
 * where the newest is. The suggested message is picked already; if it is too far up to be seen
 * from the bottom, the list opens on it instead.
 */
class SpamReportPickerDialog(
    private val activity: Activity,
    private val messages: List<Message>,
    private val selected: Message?,
    private val callback: (message: Message) -> Unit,
) {
    private var dialog: AlertDialog? = null
    private val binding = DialogRadioGroupBinding.inflate(activity.layoutInflater)

    init {
        // Each row is two lines, so without a gap the time reads as the end of the row above.
        val rowSpacing =
            activity.resources.getDimensionPixelSize(org.fossify.commons.R.dimen.small_margin)
        val buttons = messages.map { message ->
            val button = activity.layoutInflater.inflate(org.fossify.commons.R.layout.radio_button, null)
            (button as RadioButton).apply {
                text = label(message)
                isChecked = message == selected
                setPadding(paddingLeft, rowSpacing, paddingRight, rowSpacing)
                setOnClickListener {
                    dialog?.dismiss()
                    callback(message)
                }
                binding.dialogRadioGroup.addView(
                    this,
                    RadioGroup.LayoutParams(
                        RadioGroup.LayoutParams.MATCH_PARENT,
                        RadioGroup.LayoutParams.WRAP_CONTENT
                    )
                )
            }
        }

        binding.dialogRadioHolder.onGlobalLayout {
            val holder = binding.dialogRadioHolder
            val bottom = (binding.dialogRadioGroup.height - holder.height).coerceAtLeast(0)
            val row = buttons.getOrNull(messages.indexOf(selected))
            holder.scrollY = if (row == null || row.top >= bottom) bottom else row.top
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok) { _, _ -> selected?.let(callback) }
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this, R.string.report_spam_pick) { alertDialog ->
                    dialog = alertDialog
                }
            }
    }

    private fun label(message: Message): String {
        val time = (message.date * 1000L).formatMessageDateTimeCompact(activity)
        val body = message.body.replace(Regex("\\s+"), " ").trim()
        val snippet = if (body.length > SNIPPET_LENGTH) {
            body.take(SNIPPET_LENGTH).trimEnd() + "…"
        } else {
            body
        }
        return "$time\n$snippet"
    }

    private companion object {
        const val SNIPPET_LENGTH = 80
    }
}
