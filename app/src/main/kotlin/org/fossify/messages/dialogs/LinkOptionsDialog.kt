package org.fossify.messages.dialogs

import android.app.Activity
import androidx.appcompat.app.AlertDialog
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.copyToClipboard
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.getContrastColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.launchViewIntent
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.shareTextIntent
import org.fossify.messages.R
import org.fossify.messages.databinding.DialogLinkOptionsBinding
import org.fossify.messages.databinding.LayoutLinkActionsBinding
import org.fossify.messages.helpers.withoutParameters

/**
 * What to do with a link that was long pressed in a message.
 *
 * Shows the address itself rather than only naming the actions, because the whole point of the
 * second row is that the two addresses differ: a link is often most of the way tracking parameters,
 * and seeing what is about to be copied is the only way to tell one form from the other.
 */
class LinkOptionsDialog(private val activity: Activity, private val url: String) {

    private var dialog: AlertDialog? = null
    private val binding = DialogLinkOptionsBinding.inflate(activity.layoutInflater)

    init {
        val clean = url.withoutParameters()
        val hasParameters = clean != url

        binding.linkFullUrl.text = url
        binding.linkCleanUrl.text = clean
        binding.linkDivider.setBackgroundColor(
            activity.getProperTextColor().adjustAlpha(DIVIDER_ALPHA)
        )

        setupActions(binding.linkFullActions, url)
        setupActions(binding.linkCleanActions, clean)

        if (!hasParameters) {
            binding.linkDivider.beGone()
            binding.linkCleanLabel.beGone()
            binding.linkCleanUrl.beGone()
            binding.linkCleanActions.root.beGone()
        }

        activity.getAlertDialogBuilder()
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this, R.string.link) { alertDialog ->
                    dialog = alertDialog
                }
            }
    }

    private fun setupActions(actions: LayoutLinkActionsBinding, address: String) {
        val primaryColor = activity.getProperPrimaryColor()
        val textColor = activity.getProperTextColor()

        arrayOf(actions.linkActionOpenIcon, actions.linkActionCopyIcon, actions.linkActionShareIcon)
            .forEach { icon ->
                icon.background.applyColorFilter(primaryColor)
                icon.applyColorFilter(primaryColor.getContrastColor())
            }

        arrayOf(actions.linkActionOpenText, actions.linkActionCopyText, actions.linkActionShareText)
            .forEach { it.setTextColor(textColor) }

        actions.linkActionOpen.apply {
            tooltipText = activity.getString(R.string.open_link)
            setOnClickListener { act { activity.launchViewIntent(address) } }
        }
        actions.linkActionCopy.apply {
            tooltipText = activity.getString(R.string.copy_link)
            setOnClickListener { act { activity.copyToClipboard(address) } }
        }
        actions.linkActionShare.apply {
            tooltipText = activity.getString(R.string.share_link)
            setOnClickListener { act { activity.shareTextIntent(address) } }
        }
    }

    private fun act(action: () -> Unit) {
        dialog?.dismiss()
        action()
    }

    private companion object {
        const val DIVIDER_ALPHA = 0.2f
    }
}
