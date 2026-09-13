package org.fossify.messages.levinium.activities

import android.content.Intent
import org.fossify.commons.activities.BaseSplashActivity
import org.fossify.messages.activities.MainActivity

/**
 * Lives in the applicationId's package rather than the upstream namespace on purpose.
 *
 * Commons switches the launcher icon by toggling activity aliases whose component names it builds
 * as "${baseConfig.appId.removeSuffix(".debug")}.activities.SplashActivity<Color>". This fork keeps
 * the upstream namespace so the sources stay diffable, so that string only resolves if this
 * activity and its aliases actually sit under the applicationId. Moving this class back under
 * org.fossify.messages makes changing the app icon color throw "Unknown component".
 */
class SplashActivity : BaseSplashActivity() {
    override fun initActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
