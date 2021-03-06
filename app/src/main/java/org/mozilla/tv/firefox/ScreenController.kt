/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.tv.firefox

import android.content.Context
import android.text.TextUtils
import android.view.KeyEvent
import androidx.annotation.VisibleForTesting
import androidx.annotation.VisibleForTesting.NONE
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import io.reactivex.Observable
import io.reactivex.subjects.BehaviorSubject
import mozilla.components.browser.session.Session
import org.mozilla.tv.firefox.ScreenControllerStateMachine.ActiveScreen
import org.mozilla.tv.firefox.ScreenControllerStateMachine.Transition
import org.mozilla.tv.firefox.channels.SettingsScreen
import org.mozilla.tv.firefox.ext.serviceLocator
import org.mozilla.tv.firefox.navigationoverlay.NavigationOverlayFragment
import org.mozilla.tv.firefox.session.SessionRepo
import org.mozilla.tv.firefox.settings.SettingsFragment
import org.mozilla.tv.firefox.telemetry.UrlTextInputLocation
import org.mozilla.tv.firefox.utils.URLs
import org.mozilla.tv.firefox.utils.UrlUtils
import org.mozilla.tv.firefox.webdisplay.WebDisplayFragment
import org.mozilla.tv.firefox.widget.InlineAutocompleteEditText

class ScreenController(private val sessionRepo: SessionRepo) {

    private val _currentActiveScreen = BehaviorSubject.createDefault(ActiveScreen.NAVIGATION_OVERLAY)
    /**
     * Observers will be notified just before the fragment transaction is committed
     */
    val currentActiveScreen: Observable<ActiveScreen> = _currentActiveScreen
            .distinctUntilChanged()
            .hide()

    /**
     * To keep things simple, we add all the fragments at start instead of creating them when needed
     * in order to make the assumption that all Fragments exist.
     * To show the correct Fragment, we use Fragment hide/show to make sure the correct Fragment is visible.
     * We DO NOT use the Fragment backstack so that all transitions are controlled in the same manner, and we
     * don't end up mixing backstack actions with show/hide.
     */
    fun setUpFragmentsForNewSession(fragmentManager: FragmentManager, session: Session) {
        val renderFragment = WebDisplayFragment.createForSession(session)
        fragmentManager
            .beginTransaction()
            .add(R.id.container_web_render, renderFragment, WebDisplayFragment.FRAGMENT_TAG)
            // We add NavigationOverlayFragment last so that it takes focus
            .add(R.id.container_navigation_overlay, NavigationOverlayFragment(), NavigationOverlayFragment.FRAGMENT_TAG)
            .commitNow()

        _currentActiveScreen.onNext(ActiveScreen.NAVIGATION_OVERLAY)
    }

    /**
     * Loads the given url. If isTextInput is true, there should be no null parameters.
     */
    fun onUrlEnteredInner(
        context: Context,
        fragmentManager: FragmentManager,
        urlStr: String,
        isTextInput: Boolean,
        autocompleteResult: InlineAutocompleteEditText.AutocompleteResult?,
        inputLocation: UrlTextInputLocation?
    ) {
        if (TextUtils.isEmpty(urlStr.trim())) {
            return
        }

        val isUrl = UrlUtils.isUrl(urlStr)
        val updatedUrlStr = if (isUrl) UrlUtils.normalize(urlStr) else UrlUtils.createSearchUrl(context, urlStr)

        if (updatedUrlStr != null) {
            showBrowserScreenForUrl(fragmentManager, updatedUrlStr)
        }

        if (isTextInput) {
            // Non-text input events are handled at the source, e.g. home tile click events.
            if (autocompleteResult == null) {
                throw IllegalArgumentException("Expected non-null autocomplete result for text input")
            }
            if (inputLocation == null) {
                throw IllegalArgumentException("Expected non-null input location for text input")
            }

            //TelemetryIntegration.INSTANCE.urlBarEvent(isUrl, autocompleteResult, inputLocation)
        }
    }

    fun showSettingsScreen(fragmentManager: FragmentManager, settingsScreen: SettingsScreen) {
        val transition = when (settingsScreen) {
            SettingsScreen.DATA_COLLECTION -> Transition.ADD_SETTINGS_DATA
            SettingsScreen.CLEAR_COOKIES -> Transition.ADD_SETTINGS_COOKIES
            SettingsScreen.FXA_PROFILE -> Transition.ADD_FXA_PROFILE
        }
        handleTransitionAndUpdateActiveScreen(fragmentManager, transition)
    }

    fun showBrowserScreenForCurrentSession(fragmentManager: FragmentManager, session: Session) {
        if (session.url != URLs.APP_URL_HOME) {
            handleTransitionAndUpdateActiveScreen(fragmentManager, Transition.SHOW_BROWSER)
        }
    }

    fun showBrowserScreenForUrl(fragmentManager: FragmentManager, url: String) {
        handleTransitionAndUpdateActiveScreen(fragmentManager, Transition.SHOW_BROWSER)
        val webDisplayFragment = fragmentManager.webDisplayFragment()
        webDisplayFragment.loadUrl(url)
    }

    fun showNavigationOverlay(fragmentManager: FragmentManager?, toShow: Boolean) {
        fragmentManager ?: return
        fragmentManagerShowNavigationOverlay(fragmentManager, toShow)

        val currentScreen = if (toShow) ActiveScreen.NAVIGATION_OVERLAY else ActiveScreen.WEB_RENDER
        _currentActiveScreen.onNext(currentScreen)
    }

    private fun fragmentManagerShowNavigationOverlay(fragmentManager: FragmentManager, toShow: Boolean) {
        val transaction = fragmentManager.beginTransaction()
        val overlayFragment = fragmentManager.navigationOverlayFragment()

        if (toShow) {
            // If a user navigates to YouTube while a video is fullscreened, it will cause YouTube
            // to display oddly (see #1719). Exiting fullscreen is asynchronous, so handling it
            // here is safer than just before navigation. Most browsers don't show the URL
            // bar while fullscreen is active and so we are aligning with that strategy and exiting
            // fullscreen before any navigation options on the overlay are made available to the user
            val fullScreenExited = overlayFragment.context?.serviceLocator?.sessionRepo?.exitFullScreenIfPossible()
            if (fullScreenExited == true) {
                //TelemetryIntegration.INSTANCE.fullScreenVideoProgrammaticallyClosed()
            }

            transaction.show(overlayFragment)
            //MenuInteractionMonitor.menuOpened()
            // TODO: Disabled until Overlay refactor is complete #1666
            // overlayFragment.navOverlayScrollView.updateOverlayForHomescreen(isOnHomeUrl(fragmentManager))
        } else {
            transaction.hide(overlayFragment)
            //MenuInteractionMonitor.menuClosed()
        }

        transaction.commit()
    }

    fun dispatchKeyEvent(
        keyEvent: KeyEvent,
        fragmentManager: FragmentManager,
        @VisibleForTesting(otherwise = NONE) currentActiveScreen: ActiveScreen? = _currentActiveScreen.value
    ): Boolean {
        if (keyEvent.keyCode == KeyEvent.KEYCODE_MENU) {
            return when (keyEvent.action) {
                KeyEvent.ACTION_DOWN -> handleMenu(fragmentManager)
                else -> true // We swallow ACTION_UP to only handle the key event once.
            }
        }

        return when (currentActiveScreen) {
            ScreenControllerStateMachine.ActiveScreen.WEB_RENDER ->
                fragmentManager.webDisplayFragment().dispatchKeyEvent(keyEvent)
            ScreenControllerStateMachine.ActiveScreen.NAVIGATION_OVERLAY ->
                fragmentManager.navigationOverlayFragment().dispatchKeyEvent(keyEvent)

            else -> false
        }
    }

    fun handleBack(fragmentManager: FragmentManager): Boolean {
        if (_currentActiveScreen.value == ActiveScreen.WEB_RENDER) {
            if (sessionRepo.attemptBack()) return true
        }
        val transition = ScreenControllerStateMachine.getNewStateBackPress(_currentActiveScreen.value!!, canGoBack())
        return handleTransitionAndUpdateActiveScreen(fragmentManager, transition)
    }

    fun handleMenu(fragmentManager: FragmentManager): Boolean {
        val transition = ScreenControllerStateMachine.getNewStateMenuPress(_currentActiveScreen.value!!, isOnHomeUrl())

        //if (transition == Transition.ADD_OVERLAY) {
            //TelemetryIntegration.INSTANCE.menuOpenedFromMenuButton()
        //}

        return handleTransitionAndUpdateActiveScreen(fragmentManager, transition)
    }

    private fun canGoBack(): Boolean {
        return sessionRepo.state.blockingFirst().backEnabled
    }

    private fun isOnHomeUrl(): Boolean {
        @Suppress("DEPRECATION")
        return sessionRepo.state.blockingFirst().currentUrl == URLs.APP_URL_HOME
    }

    private fun handleTransitionAndUpdateActiveScreen(fragmentManager: FragmentManager, transition: Transition): Boolean {
        // Call show() before hide() so that focus moves correctly to the shown fragment once others are hidden
        when (transition) {
            Transition.ADD_OVERLAY -> {
                // We always update the currentActiveScreen value before beginning the fragment transaction
                _currentActiveScreen.onNext(ActiveScreen.NAVIGATION_OVERLAY)
                fragmentManagerShowNavigationOverlay(fragmentManager, true)
            }
            Transition.REMOVE_OVERLAY -> {
                _currentActiveScreen.onNext(ActiveScreen.WEB_RENDER)
                showNavigationOverlay(fragmentManager, false)
            }
            Transition.ADD_SETTINGS_DATA -> {
                _currentActiveScreen.onNext(ActiveScreen.SETTINGS)
                fragmentManager.beginTransaction()
                    .hide(fragmentManager.navigationOverlayFragment())
                    .add(R.id.container_settings, SettingsFragment.newInstance(SettingsScreen.DATA_COLLECTION),
                            SettingsFragment.FRAGMENT_TAG)
                    .commit()
            }
            Transition.ADD_SETTINGS_COOKIES -> {
                _currentActiveScreen.onNext(ActiveScreen.SETTINGS)
                fragmentManager.beginTransaction()
                    .hide(fragmentManager.navigationOverlayFragment())
                    .add(R.id.container_settings, SettingsFragment.newInstance(SettingsScreen.CLEAR_COOKIES),
                            SettingsFragment.FRAGMENT_TAG)
                    .commit()
            }
            Transition.REMOVE_SETTINGS -> {
                _currentActiveScreen.onNext(ActiveScreen.NAVIGATION_OVERLAY)
                fragmentManager.findFragmentByTag(SettingsFragment.FRAGMENT_TAG).let {
                    fragmentManager.beginTransaction()
                        .remove(it!!)
                        .show(fragmentManager.navigationOverlayFragment())
                        .commit()
                }
            }
            Transition.ADD_FXA_PROFILE -> {
                _currentActiveScreen.onNext(ActiveScreen.FXA_PROFILE)
                fragmentManager.beginTransaction()
                    .hide(fragmentManager.navigationOverlayFragment())
                    .add(R.id.container_settings, SettingsFragment.newInstance(SettingsScreen.FXA_PROFILE),
                        SettingsFragment.FRAGMENT_TAG)
                    .commit()
            }
            Transition.REMOVE_FXA_PROFILE -> {
                _currentActiveScreen.onNext(ActiveScreen.NAVIGATION_OVERLAY)
                fragmentManager.findFragmentByTag(SettingsFragment.FRAGMENT_TAG).let {
                    fragmentManager.beginTransaction()
                        .remove(it!!)
                        .show(fragmentManager.navigationOverlayFragment())
                        .commit()
                }
            }
            Transition.SHOW_BROWSER -> {
                _currentActiveScreen.onNext(ActiveScreen.WEB_RENDER)
                fragmentManager.beginTransaction()
                    .maybeRemoveSettingsScreen(fragmentManager)
                    .hide(fragmentManager.navigationOverlayFragment())
                    .commitNow()
            }
            Transition.EXIT_APP -> { return false }
            Transition.NO_OP -> { return true }
        }
        return true
    }
}

private fun FragmentManager.webDisplayFragment(): WebDisplayFragment =
    this.findFragmentByTag(WebDisplayFragment.FRAGMENT_TAG) as WebDisplayFragment

private fun FragmentManager.navigationOverlayFragment(): NavigationOverlayFragment =
    this.findFragmentByTag(NavigationOverlayFragment.FRAGMENT_TAG) as NavigationOverlayFragment

private fun FragmentTransaction.maybeRemoveSettingsScreen(
    fragmentManager: FragmentManager
): FragmentTransaction {
    val settingsScreen = fragmentManager.findFragmentByTag(SettingsFragment.FRAGMENT_TAG)

    return if (settingsScreen != null) {
        this.remove(settingsScreen)
    } else {
        this
    }
}
