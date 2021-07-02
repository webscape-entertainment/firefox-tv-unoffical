/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.tv.firefox.webdisplay

import android.content.Context
import android.content.SharedPreferences
import mozilla.components.browser.domains.autocomplete.ShippedDomainsProvider
import mozilla.components.browser.icons.BrowserIcons
import mozilla.components.browser.session.Session
import mozilla.components.browser.session.SessionManager
import mozilla.components.browser.session.engine.EngineMiddleware
import mozilla.components.browser.session.storage.SessionStorage
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.browser.storage.sync.PlacesHistoryStorage
import mozilla.components.browser.thumbnails.ThumbnailsMiddleware
import mozilla.components.browser.thumbnails.storage.ThumbnailStorage
import mozilla.components.concept.base.crash.Breadcrumb
import mozilla.components.concept.engine.DefaultSettings
import mozilla.components.concept.engine.Engine
import mozilla.components.concept.fetch.Client
import mozilla.components.feature.addons.migration.DefaultSupportedAddonsChecker
import mozilla.components.feature.addons.migration.SupportedAddonsChecker
import mozilla.components.feature.addons.update.AddonUpdater
import mozilla.components.feature.addons.update.DefaultAddonUpdater
import mozilla.components.feature.app.links.AppLinksInterceptor
import mozilla.components.feature.app.links.AppLinksUseCases
import mozilla.components.feature.contextmenu.ContextMenuUseCases
import mozilla.components.feature.customtabs.store.CustomTabsServiceStore
import mozilla.components.feature.downloads.DownloadsUseCases
import mozilla.components.feature.intent.processing.TabIntentProcessor
import mozilla.components.feature.media.middleware.RecordingDevicesMiddleware
import mozilla.components.feature.prompts.PromptMiddleware
import mozilla.components.feature.readerview.ReaderViewMiddleware
import mozilla.components.feature.search.SearchUseCases
import mozilla.components.feature.search.middleware.SearchMiddleware
import mozilla.components.feature.search.region.RegionMiddleware
import mozilla.components.feature.session.SessionUseCases
import mozilla.components.feature.session.middleware.LastAccessMiddleware
import mozilla.components.feature.session.middleware.undo.UndoMiddleware
import mozilla.components.feature.tabs.CustomTabsUseCases
import mozilla.components.feature.tabs.TabsUseCases
import mozilla.components.lib.crash.Crash
import mozilla.components.lib.crash.CrashReporter
import mozilla.components.lib.crash.service.CrashReporterService
import mozilla.components.lib.fetch.httpurlconnection.HttpURLConnectionClient
import mozilla.components.lib.publicsuffixlist.PublicSuffixList
import mozilla.components.service.location.LocationService
import org.mozilla.tv.firefox.ext.components
import org.mozilla.tv.firefox.utils.BuildConstants
import org.mozilla.tv.firefox.utils.Settings
import java.util.concurrent.TimeUnit

private const val DAY_IN_MINUTES = 24 * 60L

@Suppress("LargeClass")
abstract class DefaultComponents(val applicationContext: Context) {
    companion object {
        const val SAMPLE_BROWSER_PREFERENCES = "sample_browser_preferences"
        const val PREF_LAUNCH_EXTERNAL_APP = "sample_browser_launch_external_app"
    }

    abstract val engine: Engine

    val publicSuffixList by lazy { PublicSuffixList(applicationContext) }

    val preferences: SharedPreferences =
        applicationContext.getSharedPreferences(SAMPLE_BROWSER_PREFERENCES, Context.MODE_PRIVATE)

    // Shoud be fixed to old version of firefox tv
    open fun genUserAgent(): String =
        "Mozilla/5.0 (Linux; Android 7.1.2) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Focus/2.2.0.2 Chrome/59.0.3071.125 Mobile Safari/537.36"

    // Engine Settings
    val engineSettings by lazy {
        DefaultSettings().apply {
            trackingProtectionPolicy = Settings.getInstance(applicationContext).trackingProtectionPolicy;
            requestInterceptor = CustomContentRequestInterceptor(applicationContext);
            userAgentString = genUserAgent();

            displayZoomControls = false;
            loadWithOverviewMode = true; // To respect the html viewport

            // We don't have a reason for users to access local files; assets can still
            // be loaded via file:///android_asset/
            allowFileAccess = false;
            allowContentAccess = false;

            remoteDebuggingEnabled = BuildConstants.isDevBuild;

            mediaPlaybackRequiresUserGesture = false // Allows auto-play (which improves YouTube experience).
        }
    }

    val addonUpdater =
        DefaultAddonUpdater(applicationContext, AddonUpdater.Frequency(1, TimeUnit.DAYS))

    open val client: Client by lazy { HttpURLConnectionClient() }

    val icons by lazy { BrowserIcons(applicationContext, client) }

    // Storage
    private val lazyHistoryStorage = lazy { PlacesHistoryStorage(applicationContext) }
    val historyStorage by lazy { lazyHistoryStorage.value }

    val sessionStorage by lazy { SessionStorage(applicationContext, engine) }

    val thumbnailStorage by lazy { ThumbnailStorage(applicationContext) }

    val store by lazy {
        BrowserStore(middleware = listOf(
            ReaderViewMiddleware(),
            ThumbnailsMiddleware(thumbnailStorage),
            UndoMiddleware(::sessionManagerLookup),
            RegionMiddleware(
                applicationContext,
                LocationService.default()
            ),
            SearchMiddleware(applicationContext),
            RecordingDevicesMiddleware(applicationContext),
            LastAccessMiddleware(),
            PromptMiddleware()
        ) + EngineMiddleware.create(engine, ::findSessionById))
    }

    private fun findSessionById(tabId: String): Session? {
        return sessionManager.findSessionById(tabId)
    }

    private fun sessionManagerLookup(): SessionManager {
        return sessionManager
    }

    val sessionManager by lazy {
        SessionManager(engine, store).apply {
            icons.install(engine, store)
        }
    }

    val customTabsStore by lazy { CustomTabsServiceStore() }

    val sessionUseCases by lazy { SessionUseCases(store, sessionManager) }

    val customTabsUseCases by lazy { CustomTabsUseCases(sessionManager, sessionUseCases.loadUrl) }


    val supportedAddonsChecker by lazy {
        DefaultSupportedAddonsChecker(applicationContext, SupportedAddonsChecker.Frequency(1, TimeUnit.DAYS))
    }

    val searchUseCases by lazy {
        SearchUseCases(store, tabsUseCases)
    }

    val defaultSearchUseCase by lazy {
        { searchTerms: String ->
            searchUseCases.defaultSearch.invoke(
                searchTerms = searchTerms,
                searchEngine = null,
                parentSessionId = null
            )
        }
    }
    val appLinksUseCases by lazy { AppLinksUseCases(applicationContext) }

    val appLinksInterceptor by lazy {
        AppLinksInterceptor(
            applicationContext,
            interceptLinkClicks = true,
            launchInApp = {
                applicationContext.components.preferences.getBoolean(PREF_LAUNCH_EXTERNAL_APP, false)
            }
        )
    }

    // Intent
    val tabIntentProcessor by lazy {
        TabIntentProcessor(tabsUseCases, sessionUseCases.loadUrl, searchUseCases.newTabSearch)
    }

    /*private val menuItems by lazy {
        val items = mutableListOf(
            menuToolbar,
            BrowserMenuHighlightableItem("No Highlight", R.drawable.mozac_ic_share, R.color.black,
                highlight = BrowserMenuHighlight.LowPriority(
                    notificationTint = ContextCompat.getColor(applicationContext, R.color.holo_green_dark),
                    label = "Highlight"
                )
            ) {
                Toast.makeText(applicationContext, "Highlight", Toast.LENGTH_SHORT).show()
            },
            BrowserMenuImageText("Share", R.drawable.mozac_ic_share, R.color.black) {
                Toast.makeText(applicationContext, "Share", Toast.LENGTH_SHORT).show()
            },
            SimpleBrowserMenuItem("Settings") {
                Toast.makeText(applicationContext, "Settings", Toast.LENGTH_SHORT).show()
            },
            SimpleBrowserMenuItem("Find In Page") {
                FindInPageIntegration.launch?.invoke()
            },
            SimpleBrowserMenuItem("Restore after crash") {
                sessionUseCases.crashRecovery.invoke()
            },
            BrowserMenuDivider()
        )

        items.add(
            SimpleBrowserMenuItem("Add to homescreen") {
                MainScope().launch {
                    webAppUseCases.addToHomescreen()
                }
            }.apply {
                visible = { webAppUseCases.isPinningSupported() && store.state.selectedTabId != null }
            }
        )

        items.add(
            SimpleBrowserMenuItem("Open in App") {
                val getRedirect = appLinksUseCases.appLinkRedirect
                store.state.selectedTab?.let {
                    val redirect = getRedirect.invoke(it.content.url)
                    redirect.appIntent?.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    appLinksUseCases.openAppLink.invoke(redirect.appIntent)
                }
            }.apply {
                visible = {
                    store.state.selectedTab?.let {
                        appLinksUseCases.appLinkRedirect(it.content.url).hasExternalApp()
                    } ?: false
                }
            }
        )

        items.add(
            BrowserMenuCheckbox("Request desktop site", {
                store.state.selectedTab?.content?.desktopMode == true
            }) { checked ->
                sessionUseCases.requestDesktopSite(checked)
            }.apply {
                visible = { store.state.selectedTab != null }
            }
        )
        items.add(
            BrowserMenuCheckbox("Open links in apps", {
                preferences.getBoolean(PREF_LAUNCH_EXTERNAL_APP, false)
            }) { checked ->
                preferences.edit().putBoolean(PREF_LAUNCH_EXTERNAL_APP, checked).apply()
            }
        )

        items
    }

    private val menuToolbar by lazy {
        val back = BrowserMenuItemToolbar.TwoStateButton(
            primaryImageResource = mozilla.components.ui.icons.R.drawable.mozac_ic_back,
            primaryImageTintResource = R.color.photonBlue90,
            primaryContentDescription = "Back",
            isInPrimaryState = {
                store.state.selectedTab?.content?.canGoBack ?: true
            },
            disableInSecondaryState = true,
            secondaryImageTintResource = R.color.photonGrey40
        ) {
            sessionUseCases.goBack()
        }

        val forward = BrowserMenuItemToolbar.TwoStateButton(
            primaryImageResource = mozilla.components.ui.icons.R.drawable.mozac_ic_forward,
            primaryContentDescription = "Forward",
            primaryImageTintResource = R.color.photonBlue90,
            isInPrimaryState = {
                store.state.selectedTab?.content?.canGoForward ?: true
            },
            disableInSecondaryState = true,
            secondaryImageTintResource = R.color.photonGrey40
        ) {
            sessionUseCases.goForward()
        }

        val refresh = BrowserMenuItemToolbar.TwoStateButton(
            primaryImageResource = mozilla.components.ui.icons.R.drawable.mozac_ic_refresh,
            primaryContentDescription = "Refresh",
            primaryImageTintResource = R.color.photonBlue90,
            isInPrimaryState = {
                store.state.selectedTab?.content?.loading == false
            },
            secondaryImageResource = mozilla.components.ui.icons.R.drawable.mozac_ic_stop,
            secondaryContentDescription = "Stop",
            secondaryImageTintResource = R.color.photonBlue90,
            disableInSecondaryState = false
        ) {
            if (store.state.selectedTab?.content?.loading == true) {
                sessionUseCases.stopLoading()
            } else {
                sessionUseCases.reload()
            }
        }

        BrowserMenuItemToolbar(listOf(back, forward, refresh))
    }*/

    val shippedDomainsProvider by lazy {
        ShippedDomainsProvider().also { it.initialize(applicationContext) }
    }

    val tabsUseCases: TabsUseCases by lazy { TabsUseCases(store, sessionManager) }
    val downloadsUseCases: DownloadsUseCases by lazy { DownloadsUseCases(store) }
    val contextMenuUseCases: ContextMenuUseCases by lazy { ContextMenuUseCases(store) }

    val crashReporter: CrashReporter by lazy {
        CrashReporter(
            applicationContext,
            services = listOf(
                object : CrashReporterService {
                    override val id: String
                        get() = "xxx"
                    override val name: String
                        get() = "Test"

                    override fun createCrashReportUrl(identifier: String): String? {
                        return null
                    }

                    override fun report(crash: Crash.UncaughtExceptionCrash): String? {
                        return null
                    }

                    override fun report(crash: Crash.NativeCodeCrash): String? {
                        return null
                    }

                    override fun report(
                        throwable: Throwable,
                        breadcrumbs: ArrayList<Breadcrumb>
                    ): String? {
                        return null
                    }
                }
            )
        ).install(applicationContext)
    }
}
