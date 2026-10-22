// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.duress

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.onlytrusted.app.data.repository.MaskedContact
import com.onlytrusted.app.privacy.PhoneUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Simple decoy-vault store for Duress Mode (Android).
 *
 * Design choice: we deliberately do NOT spin up a second SQLCipher database here (as the
 * iOS brief suggests). A full second Room/SQLCipher stack would add ~300 lines of schema,
 * DAO and migration code for a store that will only ever hold a handful of fake contacts
 * (default: 3). Instead we persist a JSON blob in `EncryptedSharedPreferences` which gives
 * us the same AES-256 at-rest guarantee that the real vault has as a secondary layer, and
 * keeps the implementation small and auditable.
 *
 * This store is NOT the real `TrustedContactsRepository` and does not need to interoperate
 * with the Telecom call-screening path. The real-vault contacts remain in the SQLCipher
 * database untouched, so during a duress session a trusted-caller phone call will still be
 * silenced correctly (because the call-screening service queries the real repo synchronously
 * on a binder thread — it doesn't observe the UI mode flag).
 */
object DuressVaultStore {

    private const val PREFS = "duress_vault_prefs_v1"
    private const val KEY_JSON = "contacts_json"

    private val _contacts = MutableStateFlow<List<MaskedContact>>(emptyList())
    val contacts: StateFlow<List<MaskedContact>> = _contacts.asStateFlow()

    private fun prefs(ctx: Context) = EncryptedSharedPreferences.create(
        ctx.applicationContext,
        PREFS,
        MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    data class DecoyContact(
        val id: Long,
        val displayName: String,
        val relation: String?,
        val rawNumber: String?
    )

    fun refresh(ctx: Context) {
        _contacts.value = loadInternal(ctx).toMasked()
    }

    fun snapshot(ctx: Context): List<DecoyContact> = loadInternal(ctx)

    fun seedPlaceholdersIfEmpty(ctx: Context, placeholders: List<DecoyContact>) {
        if (loadInternal(ctx).isNotEmpty()) return
        val stamped = placeholders.mapIndexed { idx, p -> p.copy(id = (idx + 1).toLong()) }
        persist(ctx, stamped)
    }

    fun save(ctx: Context, contact: DecoyContact) {
        val list = loadInternal(ctx).toMutableList()
        val newId = if (contact.id > 0) contact.id else ((list.maxOfOrNull { it.id } ?: 0) + 1)
        val record = contact.copy(id = newId)
        val existingIdx = list.indexOfFirst { it.id == record.id }
        if (existingIdx >= 0) list[existingIdx] = record else list.add(record)
        persist(ctx, list)
    }

    fun delete(ctx: Context, id: Long) {
        val list = loadInternal(ctx).filter { it.id != id }
        persist(ctx, list)
    }

    fun wipe(ctx: Context) {
        prefs(ctx).edit().clear().apply()
        _contacts.value = emptyList()
    }

    // ---- internals ----

    private fun loadInternal(ctx: Context): List<DecoyContact> {
        val raw = prefs(ctx).getString(KEY_JSON, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                DecoyContact(
                    id = o.optLong("id"),
                    displayName = o.optString("name"),
                    relation = o.optString("relation").takeIf { it.isNotEmpty() },
                    rawNumber = o.optString("number").takeIf { it.isNotEmpty() }
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun persist(ctx: Context, list: List<DecoyContact>) {
        val arr = JSONArray()
        list.forEach { c ->
            arr.put(JSONObject().apply {
                put("id", c.id)
                put("name", c.displayName)
                c.relation?.let { put("relation", it) }
                c.rawNumber?.let { put("number", it) }
            })
        }
        prefs(ctx).edit().putString(KEY_JSON, arr.toString()).apply()
        _contacts.value = list.toMasked()
    }

    private fun List<DecoyContact>.toMasked(): List<MaskedContact> = map { c ->
        val numbers = listOfNotNull(c.rawNumber).map { PhoneUtils.normalise(it) }
        MaskedContact(
            id = c.id,
            displayName = c.displayName,
            relation = c.relation,
            isOwner = false,
            maskedEmail = null,
            maskedNumbers = listOfNotNull(c.rawNumber).map { PhoneUtils.mask(it) },
            normalisedNumbers = numbers
        )
    }
}
