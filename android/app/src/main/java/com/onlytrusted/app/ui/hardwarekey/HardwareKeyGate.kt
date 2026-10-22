// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.hardwarekey

import android.content.Context
import android.util.Base64
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom

/**
 * Elite-tier gating helper. Mirrors the iOS `HardwareKeyGate` API.
 *
 * `requireAssertion(context, reason, onSuccess, onCancel)` is the entry point used by Wipe
 * everything, Duress disarm, Dead-Man Wipe disable, encrypted backup export, etc. When at
 * least one key is enrolled, the user is prompted via Android's Credential Manager; when no
 * key is enrolled the feature stays opt-in and `onSuccess` fires immediately.
 *
 * Enrollment goes through `enrollNewKey(...)` which assembles a WebAuthn `create` request JSON
 * and hands it to `CredentialManager.createCredential`. The returned `authenticatorData` /
 * public key material is persisted by [HardwareKeyState].
 */
object HardwareKeyGate {

    sealed class Outcome {
        data object Success : Outcome()
        data object Cancelled : Outcome()
        data class Failed(val message: String) : Outcome()
    }

    sealed class EnrollOutcome {
        data class Success(val key: HardwareKeyState.EnrolledKey) : EnrollOutcome()
        data object Cancelled : EnrollOutcome()
        data class Failed(val message: String) : EnrollOutcome()
    }

    /**
     * Call this before every sensitive action. The lambda receives `true` on success,
     * `false` on cancel / failure.
     *
     * The gate honours the dev-only emulator skip when (and only when) the current process
     * is detected as an emulator. A real-device build ignores the flag.
     */
    fun requireAssertion(
        context: Context,
        reason: String,
        scope: CoroutineScope,
        onSuccess: () -> Unit,
        onCancel: (String?) -> Unit
    ) {
        val keys = HardwareKeyState.enrolledKeys(context)
        if (keys.isEmpty()) {
            // Opt-in feature: no keys enrolled → caller proceeds without the hardware prompt.
            onSuccess()
            return
        }
        if (HardwareKeyState.isRunningOnEmulator &&
            HardwareKeyState.isEmulatorSkipEnabled(context)
        ) {
            onSuccess()
            return
        }

        scope.launch {
            val outcome = runAssertion(context, keys.map { it.credentialId }, reason)
            when (outcome) {
                is Outcome.Success -> onSuccess()
                is Outcome.Cancelled -> onCancel(null)
                is Outcome.Failed -> onCancel(outcome.message)
            }
        }
    }

    /** Enroll a new hardware key. Prompts the user to tap/insert the key. */
    fun enrollNewKey(
        context: Context,
        displayName: String,
        scope: CoroutineScope,
        onResult: (EnrollOutcome) -> Unit
    ) {
        scope.launch {
            onResult(runEnrollment(context, displayName))
        }
    }

    // --------------------------------------------------------------------- impl

    private suspend fun runEnrollment(
        context: Context,
        displayName: String
    ): EnrollOutcome = withContext(Dispatchers.IO) {
        val challenge = randomBase64Url(32)
        val userId = randomBase64Url(16)

        // Minimal WebAuthn registration request JSON. ES256 (-7) is the FIDO2-mandatory
        // algorithm; we also accept RS256 (-257) for broader hardware compatibility.
        val requestJson = JSONObject().apply {
            put("challenge", challenge)
            put("rp", JSONObject().apply {
                put("name", "OnlyTrusted")
                put("id", HardwareKeyState.RELYING_PARTY_ID)
            })
            put("user", JSONObject().apply {
                put("id", userId)
                put("name", displayName)
                put("displayName", displayName)
            })
            put("pubKeyCredParams", JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "public-key")
                    put("alg", -7)
                })
                put(JSONObject().apply {
                    put("type", "public-key")
                    put("alg", -257)
                })
            })
            put("timeout", 60000)
            put("attestation", "none")
            put("authenticatorSelection", JSONObject().apply {
                // "cross-platform" asks the OS for a roaming authenticator (USB / NFC / BLE
                // security key) rather than the platform fingerprint sensor.
                put("authenticatorAttachment", "cross-platform")
                put("userVerification", "preferred")
            })
        }.toString()

        try {
            val cm = CredentialManager.create(context)
            val response = cm.createCredential(
                context = context,
                request = CreatePublicKeyCredentialRequest(requestJson)
            ) as CreatePublicKeyCredentialResponse

            val responseJson = JSONObject(response.registrationResponseJson)
            val credentialId = responseJson.optString("id")
            val attestation = responseJson.optJSONObject("response")
                ?.optString("attestationObject")
                ?.takeIf { it.isNotEmpty() }

            if (credentialId.isNullOrEmpty()) {
                EnrollOutcome.Failed("The authenticator did not return a credential id.")
            } else {
                val stored = HardwareKeyState.addKey(
                    ctx = context,
                    credentialId = credentialId,
                    publicKey = attestation,
                    displayName = displayName
                )
                EnrollOutcome.Success(stored)
            }
        } catch (_: CreateCredentialCancellationException) {
            EnrollOutcome.Cancelled
        } catch (e: CreateCredentialException) {
            EnrollOutcome.Failed(e.localizedMessage ?: e::class.java.simpleName)
        } catch (e: Exception) {
            EnrollOutcome.Failed(e.localizedMessage ?: "Enrollment failed.")
        }
    }

    private suspend fun runAssertion(
        context: Context,
        allowedCredentialIds: List<String>,
        @Suppress("UNUSED_PARAMETER") reason: String
    ): Outcome = withContext(Dispatchers.IO) {
        val challenge = randomBase64Url(32)

        val allowCredentials = JSONArray().apply {
            allowedCredentialIds.forEach { id ->
                put(JSONObject().apply {
                    put("type", "public-key")
                    put("id", id)
                    put("transports", JSONArray(listOf("usb", "nfc", "ble")))
                })
            }
        }

        val requestJson = JSONObject().apply {
            put("challenge", challenge)
            put("timeout", 60000)
            put("rpId", HardwareKeyState.RELYING_PARTY_ID)
            put("userVerification", "preferred")
            put("allowCredentials", allowCredentials)
        }.toString()

        try {
            val cm = CredentialManager.create(context)
            val response = cm.getCredential(
                context = context,
                request = GetCredentialRequest(
                    credentialOptions = listOf(GetPublicKeyCredentialOption(requestJson))
                )
            )
            // The gate only cares that the authenticator asserted — we are not verifying
            // signatures off-device because this is a local, on-device gate (not a server
            // login). Still, double-check that we actually got a PublicKey credential back.
            if (response.credential is PublicKeyCredential) {
                Outcome.Success
            } else {
                Outcome.Failed("Unexpected credential type.")
            }
        } catch (_: GetCredentialCancellationException) {
            Outcome.Cancelled
        } catch (e: GetCredentialException) {
            Outcome.Failed(e.localizedMessage ?: e::class.java.simpleName)
        } catch (e: Exception) {
            Outcome.Failed(e.localizedMessage ?: "Hardware key check failed.")
        }
    }

    private fun randomBase64Url(byteLength: Int): String {
        val bytes = ByteArray(byteLength)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(
            bytes,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
    }
}
