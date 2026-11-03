package org.fossify.messages.helpers

import android.content.Context
import org.fossify.commons.extensions.getMyContactsCursor
import org.fossify.commons.helpers.ContactLookupResult
import org.fossify.commons.helpers.SimpleContactsHelper
import org.fossify.messages.extensions.config

object ReceiverUtils {

    /**
     * A sender counts as unknown only when the lookup says so outright. Without an address, or
     * when the contacts cannot be read, a rule limited to unknown senders does not block.
     */
    fun isMessageFilteredOut(context: Context, body: String, address: String?): Boolean {
        val isUnknownSender by lazy {
            if (address.isNullOrBlank()) return@lazy false
            val privateCursor = context.getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
            SimpleContactsHelper(context).existsSync(address, privateCursor) == ContactLookupResult.NotFound
        }
        return context.config.blockRules.any { rule -> BlockRuleMatching.blocks(rule, body) { isUnknownSender } }
    }
}
