/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.tv.firefox.navigationoverlay

import androidx.lifecycle.ViewModel
import io.reactivex.Observable
import org.mozilla.tv.firefox.hint.HintContent
import org.mozilla.tv.firefox.hint.HintViewModel
import org.mozilla.tv.firefox.session.SessionRepo
import org.mozilla.tv.firefox.webdisplay.WebDisplayHintViewModel

/**
 * Contains business logic for, and exposes data to the hint bar.
 *
 * Although the exposed data is the same between this and [WebDisplayHintViewModel],
 * the business logic, dependencies, and API are all substantially different. As
 * the exposed data is the most trivial part of the implementation, these were
 * broken into two classes.
 */
class OverlayHintViewModel(
    sessionRepo: SessionRepo,
    closeMenuHint: HintContent
) : ViewModel(), HintViewModel {
    // TODO this will require an additional dependency when overlay hint is updated
    // to change contextually according to the currently focused view

    override val isDisplayed: Observable<Boolean> = sessionRepo.state
            .map { it.backEnabled }
            .distinctUntilChanged()
            .replay(1)
            .autoConnect(0)
    override val hints: Observable<List<HintContent>> = Observable.just(listOf(closeMenuHint))
}
