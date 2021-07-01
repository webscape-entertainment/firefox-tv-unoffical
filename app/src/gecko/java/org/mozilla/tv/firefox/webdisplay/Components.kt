/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

// Suppress for literal UA comment below. detekt doesn't support lower-level annotations
// for MaxLineLength: https://github.com/arturbosch/detekt/issues/715
@file:Suppress("MaxLineLength")

package org.mozilla.tv.firefox.webdisplay

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import mozilla.components.browser.engine.gecko.GeckoEngine
import mozilla.components.browser.engine.gecko.fetch.GeckoViewFetchClient
import mozilla.components.browser.engine.gecko.glean.GeckoAdapter
import mozilla.components.concept.engine.Engine
import mozilla.components.browser.engine.gecko
import mozilla.components.feature.webcompat.WebCompatFeature
import mozilla.components.feature.webcompat.reporter.WebCompatReporterFeature
import mozilla.components.lib.crash.handler.CrashHandlerService
import mozilla.components.support.base.log.Log
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.tv.firefox.BuildConfig

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

    override fun genUserAgent(): String {
        val uaBuilder = StringBuilder()

        uaBuilder.append("Mozilla/5.0")

        uaBuilder.append(" (Linux; Android ").append(Build.VERSION.RELEASE).append(")")

        val appName = applicationContext.resources.getString(R.string.useragent_appname)
        val appVersion: String? // unknown if Android framework returns null but not worth crashing over.
        try {
            appVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (e: PackageManager.NameNotFoundException) {
            // This should be impossible - we should always be able to get information about ourselves:
            throw IllegalStateException("Unable find package details for Focus", e)
        }

        val geckoVersion = "${org.mozilla.geckoview.BuildConfig.MOZ_APP_VERSION}"
        uaBuilder.append(" Gecko/" + geckoVersion)
        uaBuilder.append(" Firefox/" + geckoVersion)
        uaBuilder.append(" " + appName + "/" + appVersion)

        return uaBuilder.toString()
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
