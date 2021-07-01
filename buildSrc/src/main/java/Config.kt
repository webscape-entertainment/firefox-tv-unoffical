import org.gradle.api.Project
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

object Config {
    // Synchronized build configuration for all modules
    const val compileSdkVersion = 29
    const val targetSdkVersion = 29
    const val minSdkVersion = 22

    @JvmStatic
    fun generateDebugVersionName(): String {
        val today = Date()
        val mainVersion = FirefoxTV.VERSION
        val acVersion = AndroidComponents.VERSION//.split(".")[0]
        // Append the year (2 digits) and week in year (2 digits). This will make it easier to distinguish versions and
        // identify ancient versions when debugging issues. However this will still keep the same version number during
        // the week so that we do not end up with a lot of versions in tools like Sentry. As an extra this matches the
        // sections we use in the changelog (weeks).
        return SimpleDateFormat("$mainVersion.yyww-$acVersion", Locale.US).format(today)
    }

    @JvmStatic
    fun releaseVersionName(): String {
        val mainVersion = FirefoxTV.VERSION
        val acVersion = AndroidComponents.VERSION//.split(".")[0]
        return "$mainVersion-$acVersion"
    }
}
