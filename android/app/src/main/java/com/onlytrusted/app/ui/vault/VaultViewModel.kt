// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.vault

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.onlytrusted.app.OnlyTrustedApp
import com.onlytrusted.app.data.repository.MaskedContact
import com.onlytrusted.app.data.repository.RevealedContact
import com.onlytrusted.app.data.repository.TrustedContactDraft
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class VaultViewModel(private val app: OnlyTrustedApp) : AndroidViewModel(app) {

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
