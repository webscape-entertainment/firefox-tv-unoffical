/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.tv.firefox.ext

import android.content.Context
import android.view.accessibility.AccessibilityManager
import org.mozilla.tv.firefox.webdisplay.Components
import org.mozilla.tv.firefox.FirefoxApplication

// Extension functions for the Context class

fun Context.getAccessibilityManager() = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager

// AccessibilityManager.isAccessibilityEnabled can be enabled for more things than just VoiceView. If we want
// VoiceView, we only need to check this one field; via comments on https://stackoverflow.com/a/12362545/582004.
fun Context.isVoiceViewEnabled() = getAccessibilityManager().isTouchExplorationEnabled

fun Context.getDimenPixelSize(dimen: Int) = resources.getDimensionPixelSize(dimen)

/**
 * Get the FirefoxApplication object from a context.
 */
val Context.application: FirefoxApplication
    get() = applicationContext as FirefoxApplication

/**
 * Get the components of this application.
 */
val Context.components: Components
    get() = application.components
