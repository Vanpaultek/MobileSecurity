// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.p2p

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Elite Trusted Witness gate (Android). Mirrors the iOS [TrustedWitnessGate] API:
 * call before every sensitive action, and if the feature is off / no witness enrolled /
 * the action isn't in the required-set, the lambda fires `onSuccess` immediately.
 *
 * When the feature IS armed:
 *   1. We start the shared [OtBluetoothPeerManager] in advertising mode with a
 *      [OtBluetoothPeerManager.PayloadKind.WITNESS_APPROVAL_REQUEST] payload.
 *   2. The witness's app scans, matches our service UUID + the attestation code shown
 *      on both screens, and replies with WITNESS_APPROVAL_REPLY { approved = 0x01 or 0x00 }.
 *   3. 60-second timeout. Simulator fallback auto-approves after ~1 second so the gated
 *      flow can still be exercised in CI / local dev.
 */
object TrustedWitnessGate {

    sealed class Outcome {
        data object Success : Outcome()
        data object Cancelled : Outcome()
        data class Failed(val message: String) : Outcome()
    }

    fun require(
        context: Context,
        action: TrustedWitnessState.GatedAction,
        reason: String,
        scope: CoroutineScope,
        onSuccess: () -> Unit,
        onCancel: (String?) -> Unit
    ) {
        if (!TrustedWitnessState.requiresWitness(context, action)) {
            onSuccess()
            return
        }
        TrustedWitnessApprovalPresenter.start(
            context = context,
            action = action,
            reason = reason,
            scope = scope,
            onOutcome = { outcome ->
                when (outcome) {
                    is Outcome.Success -> onSuccess()
                    is Outcome.Cancelled -> onCancel(null)
                    is Outcome.Failed -> onCancel(outcome.message)
                }
            }
        )
    }
}

/**
 * Shared presenter that drives the "Waiting for witness…" overlay. Observed by the
 * composable in [TrustedWitnessSetupScreen] via [isActive].
 */
object TrustedWitnessApprovalPresenter {

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private val _currentAction = MutableStateFlow<TrustedWitnessState.GatedAction?>(null)
    val currentAction: StateFlow<TrustedWitnessState.GatedAction?> = _currentAction.asStateFlow()

    private val _attestationCode = MutableStateFlow("")
    val attestationCode: StateFlow<String> = _attestationCode.asStateFlow()

    private var activeManager: OtBluetoothPeerManager? = null
    private var currentOutcome: ((TrustedWitnessGate.Outcome) -> Unit)? = null
    private val jobs = Job()

    fun start(
        context: Context,
        action: TrustedWitnessState.GatedAction,
        reason: String,
        scope: CoroutineScope,
        onOutcome: (TrustedWitnessGate.Outcome) -> Unit
    ) {
        if (_isActive.value) {
            // Already in-flight — bounce back with cancelled so we never run two gates at once.
            onOutcome(TrustedWitnessGate.Outcome.Cancelled)
            return
        }
        _isActive.value = true
        _currentAction.value = action
        _attestationCode.value = ShamirSSS.randomBytes(3)
            .joinToString("") { "%02X".format(it) }
        currentOutcome = onOutcome

        val mgr = OtBluetoothPeerManager(context)
        activeManager = mgr
        val payload = action.name.toByteArray()
        mgr.startAdvertising(
            OtBluetoothPeerManager.PayloadKind.WITNESS_APPROVAL_REQUEST,
            payload,
            _attestationCode.value
        )

        // 60s timeout
        scope.launch(jobs) {
            delay(60_000)
            finish(TrustedWitnessGate.Outcome.Failed("No witness approved in 60 seconds."))
        }

        // Observe payloads
        scope.launch(jobs) {
            mgr.lastPayload.collect { lp ->
                if (lp != null &&
                    lp.kind == OtBluetoothPeerManager.PayloadKind.WITNESS_APPROVAL_REPLY
                ) {
                    val approved = lp.payload.firstOrNull() == 1.toByte()
                    if (approved) {
                        finish(TrustedWitnessGate.Outcome.Success)
                    } else {
                        finish(TrustedWitnessGate.Outcome.Cancelled)
                    }
                }
            }
        }

        // Emulator / dev convenience: synthesise an auto-approval so UI flows work
        // without a second device.
        if (isEmulator()) {
            scope.launch(jobs) {
                delay(1200)
                mgr.deliverLastPayload(
                    OtBluetoothPeerManager.PayloadKind.WITNESS_APPROVAL_REPLY,
                    byteArrayOf(0x01) + _attestationCode.value.toByteArray()
                )
            }
        }
    }

    fun cancel() {
        finish(TrustedWitnessGate.Outcome.Cancelled)
    }

    private fun finish(outcome: TrustedWitnessGate.Outcome) {
        if (!_isActive.value) return
        _isActive.value = false
        _currentAction.value = null
        _attestationCode.value = ""
        activeManager?.stop()
        activeManager = null
        val cb = currentOutcome
        currentOutcome = null
        jobs.cancelChildren()
        cb?.invoke(outcome)
    }

    private fun isEmulator(): Boolean {
        val fp = (android.os.Build.FINGERPRINT ?: "").lowercase()
        val model = (android.os.Build.MODEL ?: "").lowercase()
        val product = (android.os.Build.PRODUCT ?: "").lowercase()
        return fp.startsWith("generic") ||
            fp.contains("emulator") ||
            model.contains("emulator") ||
            product.contains("sdk_gphone") ||
            product.contains("emulator")
    }
}
