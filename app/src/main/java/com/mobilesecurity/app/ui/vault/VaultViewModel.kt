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
package com.mobilesecurity.app.ui.vault

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mobilesecurity.app.MobileSecurityApp
import com.mobilesecurity.app.data.repository.MaskedContact
import com.mobilesecurity.app.data.repository.RevealedContact
import com.mobilesecurity.app.data.repository.TrustedContactDraft
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class VaultViewModel(private val app: MobileSecurityApp) : AndroidViewModel(app) {

    val contacts: StateFlow<List<MaskedContact>> = app.trustedContacts.maskedList()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _revealed = MutableStateFlow<RevealedContact?>(null)
    val revealed: StateFlow<RevealedContact?> = _revealed.asStateFlow()

    fun save(draft: TrustedContactDraft, onSaved: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = app.trustedContacts.save(draft)
            onSaved(id)
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            app.trustedContacts.delete(id)
            if (_revealed.value?.id == id) _revealed.value = null
        }
    }

    fun reveal(id: Long) {
        viewModelScope.launch {
            _revealed.value = app.trustedContacts.reveal(id)
        }
    }

    fun hideRevealed() {
        _revealed.value = null
    }

    suspend fun loadForEdit(id: Long): RevealedContact? = app.trustedContacts.reveal(id)
}
