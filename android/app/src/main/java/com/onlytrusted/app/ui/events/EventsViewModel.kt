// SPDX-FileCopyrightText: 2026 57Million LLC
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.events

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.onlytrusted.app.OnlyTrustedApp
import com.onlytrusted.app.data.db.entities.SecurityEvent
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class EventsViewModel(private val app: OnlyTrustedApp) : AndroidViewModel(app) {

    val events = app.repository.recentEvents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<SecurityEvent>())

    fun clear() {
        viewModelScope.launch { app.repository.clearEvents() }
    }
}
