package org.fossify.messages.activities

import android.content.Intent
import android.os.Bundle
import org.fossify.commons.activities.FAQActivity
import org.fossify.commons.activities.LicenseActivity
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.launchViewIntent
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.APP_FAQ
import org.fossify.commons.helpers.APP_LICENSES
import org.fossify.commons.helpers.LICENSE_EVENT_BUS
import org.fossify.commons.helpers.LICENSE_INDICATOR_FAST_SCROLL
import org.fossify.commons.helpers.LICENSE_SMS_MMS
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.models.FAQItem
import org.fossify.messages.BuildConfig
import org.fossify.messages.R
import org.fossify.messages.databinding.ActivityAboutBinding

/**
 * This fork's own About screen.
 *
 * Commons has one, but it speaks for the Fossify project: its repository, its donations, its
 * social accounts and a privacy policy under a name that has nothing to do with this app. What a
 * fork owes its reader instead is where this code lives, where to report what it gets wrong, and
 * whose work it was built on.
 */
class AboutActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityAboutBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupEdgeToEdge(padBottomSystem = listOf(binding.aboutNestedScrollview))
        setupMaterialScrollListener(binding.aboutNestedScrollview, binding.aboutAppbar)
        setupClicks()
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(
            topAppBar = binding.aboutAppbar,
            navigationIcon = NavigationIcon.Arrow,
            topBarColor = getProperBackgroundColor()
        )

        updateTextColors(binding.aboutHolder)
        binding.aboutVersion.text = BuildConfig.VERSION_NAME
        arrayOf(
            binding.aboutSupportLabel,
            binding.aboutProjectLabel,
            binding.aboutOtherLabel,
        ).forEach { it.setTextColor(getProperPrimaryColor()) }
    }

    private fun setupClicks() = binding.apply {
        aboutFaqHolder.setOnClickListener { launchFAQ() }
        aboutReportIssueHolder.setOnClickListener { launchViewIntent("$PROJECT_URL/issues") }
        aboutSourceHolder.setOnClickListener { launchViewIntent(PROJECT_URL) }
        aboutReleasesHolder.setOnClickListener { launchViewIntent("$PROJECT_URL/releases") }
        aboutUpstreamHolder.setOnClickListener { launchViewIntent(UPSTREAM_URL) }
        aboutSupportUpstreamHolder.setOnClickListener { launchViewIntent(UPSTREAM_DONATE_URL) }
        aboutLicenseHolder.setOnClickListener { launchViewIntent("$PROJECT_URL/blob/main/LICENSE") }
        aboutThirdPartyHolder.setOnClickListener { launchThirdPartyLicences() }
    }

    private fun launchFAQ() {
        val faqItems = arrayListOf(
            FAQItem(R.string.faq_2_title, R.string.faq_2_text),
            FAQItem(R.string.faq_3_title, R.string.faq_3_text),
            FAQItem(R.string.faq_4_title, R.string.faq_4_text),
            FAQItem(
                org.fossify.commons.R.string.faq_9_title_commons,
                org.fossify.commons.R.string.faq_9_text_commons
            ),
        )

        Intent(this, FAQActivity::class.java).apply {
            putExtra(APP_FAQ, faqItems)
            startActivity(this)
        }
    }

    private fun launchThirdPartyLicences() {
        Intent(this, LicenseActivity::class.java).apply {
            putExtra(
                APP_LICENSES,
                LICENSE_EVENT_BUS or LICENSE_SMS_MMS or LICENSE_INDICATOR_FAST_SCROLL
            )
            startActivity(this)
        }
    }

    private companion object {
        const val PROJECT_URL = "https://github.com/levinium/Messages"
        const val UPSTREAM_URL = "https://github.com/FossifyOrg/Messages"
        const val UPSTREAM_DONATE_URL = "https://www.fossify.org/donate/"
    }
}
