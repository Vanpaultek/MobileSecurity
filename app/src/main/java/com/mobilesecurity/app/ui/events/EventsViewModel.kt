/*
 * Copyright (C) 2026 57Million LLC
 *
 * This file is part of MobileSecurity.
 *
 * MobileSecurity is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MobileSecurity is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.mobilesecurity.app.ui.events

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mobilesecurity.app.MobileSecurityApp
import com.mobilesecurity.app.data.db.entities.SecurityEvent
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class EventsViewModel(private val app: MobileSecurityApp) : AndroidViewModel(app) {

    val events = app.repository.recentEvents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<SecurityEvent>())

    fun clear() {
        viewModelScope.launch { app.repository.clearEvents() }
    }
}
