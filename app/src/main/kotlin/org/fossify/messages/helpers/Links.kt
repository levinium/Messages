package org.fossify.messages.helpers

/**
 * The link with everything from the first question mark onwards taken off.
 *
 * That is where the tracking tends to live: campaign names, click ids and referrers a link picks
 * up on its way from wherever it was found. What is left is the page itself.
 */
fun String.withoutParameters(): String {
    val query = indexOf('?')
    return if (query == -1) this else take(query)
}
