/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package org.mozilla.tv.firefox.webdisplay

import android.content.Context
import mozilla.components.browser.engine.gecko.GeckoEngine
import mozilla.components.browser.engine.gecko.fetch.GeckoViewFetchClient
import mozilla.components.browser.engine.gecko.glean.GeckoAdapter
import mozilla.components.concept.engine.Engine
import mozilla.components.feature.webcompat.WebCompatFeature
import mozilla.components.feature.webcompat.reporter.WebCompatReporterFeature
import mozilla.components.lib.crash.handler.CrashHandlerService
import mozilla.components.support.base.log.Log
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings

/**
 * Helper class for lazily instantiating components needed by the application.
 */
class Components(private val applicationContext: Context) : DefaultComponents(applicationContext) {
    private var launchSafeIntent: SafeIntent? = null

    fun notifyLaunchWithSafeIntent(safeIntent: SafeIntent): Boolean {
        // We can't access the property reference outside of our own lexical scope,
        // so this helper must be in this class.
        if (launchSafeIntent == null) {
            launchSafeIntent = safeIntent
            return true
        }
        return false
    }

    private val runtime by lazy {
        // Allow for exfiltrating Gecko metrics through the Glean SDK.
        val builder = GeckoRuntimeSettings.Builder().aboutConfigEnabled(true)
        builder.telemetryDelegate(GeckoAdapter())
        if (BuildConstants.isDevBuild) {
            // In debug builds, allow to invoke via an Intent that has extras customizing Gecko.
            // In particular, this allows to add command line arguments for custom profiles, etc.
            val extras = launchSafeIntent?.extras
            if (extras != null) {
                builder.extras(extras)
            }
        }
        builder.crashHandler(CrashHandlerService::class.java)
        GeckoRuntime.create(applicationContext, builder.build())
    }

    override val engine: Engine by lazy {
        GeckoEngine(applicationContext, engineSettings, runtime).also {
            WebCompatFeature.install(it)
            WebCompatReporterFeature.install(it)
        }
    }

    override val client by lazy { GeckoViewFetchClient(applicationContext, runtime) }
}
