// SPDX-FileCopyrightText: 2026 57Million LLC
// SPDX-License-Identifier: AGPL-3.0-or-later
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
