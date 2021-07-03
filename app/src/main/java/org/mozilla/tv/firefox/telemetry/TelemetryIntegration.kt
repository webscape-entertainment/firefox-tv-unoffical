/* -*- Mode: Java; c-basic-offset: 4; tab-width: 20; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.tv.firefox.telemetry

private const val SHARED_PREFS_KEY = "telemetryLib" // Don't call it TelemetryWrapper to avoid accidental IDE rename.
private const val KEY_CLICKED_HOME_TILE_IDS_PER_SESSION = "clickedHomeTileIDsPerSession"
private const val KEY_REMOTE_CONTROL_NAME = "remoteControlName"
private const val YOUTUBE_TILE_ID = "youtube"

enum class MediaSessionEventType(internal val value: String) {
    PLAY("play"), PAUSE("pause"),
    NEXT("next"), PREV("prev"),
    SEEK("seek"),

    PLAY_PAUSE_BUTTON("play_pause_btn")
}

enum class UrlTextInputLocation(internal val extra: String) {
    // We hardcode the Strings so we can change the enum without changing the sent telemetry values.
    HOME("home"),
    MENU("menu"),
}

private enum class ReceivedTabDeviceType(val extra: String) {
    // We hardcode the Strings so we can change the enum without changing the sent telemetry values.
    DESKTOP("desktop"),
    MOBILE("mobile"),
    TABLET("tablet"),
    TV("tv"),
    VR("vr"),

    UNKNOWN("unknown")
}
