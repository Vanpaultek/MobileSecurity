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
package com.mobilesecurity.app.ui.blocklist

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mobilesecurity.app.MobileSecurityApp
import com.mobilesecurity.app.data.db.entities.BlockedNumber
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BlockListViewModel(private val app: MobileSecurityApp) : AndroidViewModel(app) {

    val numbers = app.repository.blockedNumbers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<BlockedNumber>())

    fun add(number: String, label: String?, blockCalls: Boolean, blockSms: Boolean) {
        if (number.isBlank()) return
        viewModelScope.launch {
            app.repository.block(
                number = number.trim(),
                label = label?.takeIf { it.isNotBlank() },
                blockCalls = blockCalls,
                blockSms = blockSms
            )
        }
    }

    fun remove(number: String) {
        viewModelScope.launch { app.repository.unblock(number) }
    }
}
