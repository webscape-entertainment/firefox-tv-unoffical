/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package org.mozilla.tv.firefox.webrender

import android.content.Context
import mozilla.components.browser.engine.system.SystemEngine
import mozilla.components.browser.session.SessionManager
import mozilla.components.concept.engine.DefaultSettings
import mozilla.components.concept.engine.Engine
import mozilla.components.feature.session.SessionUseCases
import mozilla.components.support.utils.SafeIntent
import org.mozilla.tv.firefox.R
import org.mozilla.tv.firefox.utils.BuildConstants
import org.mozilla.tv.firefox.utils.Settings

/**
 * Helper class for lazily instantiating components needed by the application.
 * That is allready set as fallback in main: DefaultComponents
 */
class Components(applicationContext: Context) : DefaultComponents(applicationContext) {
    fun notifyLaunchWithSafeIntent(@Suppress("UNUSED_PARAMETER") safeIntent: SafeIntent): Boolean {
        // For the system WebView, we don't need the initial launch intent right now.  In the
        // future, we might configure a proxy server using this intent for automation.
        return false
    }
}
