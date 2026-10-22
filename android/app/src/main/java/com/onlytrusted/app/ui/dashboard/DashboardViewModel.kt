// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.dashboard

import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.onlytrusted.app.OnlyTrustedApp
import com.onlytrusted.app.data.db.entities.SecurityEvent
import android.app.role.RoleManager
import android.os.Build
import android.telecom.TelecomManager
import com.onlytrusted.app.privacy.ProtectionSettings
import com.onlytrusted.app.privacy.SensorPrivacyStatus
import com.onlytrusted.app.privacy.ShieldController
import com.onlytrusted.app.security.AppLockManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardState(
    val micEvents24h: Int = 0,
    val cameraEvents24h: Int = 0,
    val blockedCalls24h: Int = 0,
    val flaggedSms24h: Int = 0,
    val untrustedCalls24h: Int = 0,
    val untrustedSms24h: Int = 0,
    val sensorPrivacy: SensorPrivacyStatus.Snapshot = SensorPrivacyStatus.Snapshot(null, null),
    val trustedContactCount: Int = 0,
    val trustedOnlyCalls: Boolean = false,
    val trustedOnlySms: Boolean = false,
    val auditChainIntact: Boolean? = null
)

class DashboardViewModel(private val app: OnlyTrustedApp) : AndroidViewModel(app) {

    private val repo = app.repository
    private val trusted = app.trustedContacts
    private val since24h: Long get() = System.currentTimeMillis() - 24 * 60 * 60 * 1000L

    private val _runtimeState = MutableStateFlow(initialRuntimeState())
    val runtimeState: StateFlow<RuntimeState> = _runtimeState.asStateFlow()

    val state: StateFlow<DashboardState> = combine(
        repo.micCountSince(since24h),
        repo.cameraCountSince(since24h),
        repo.blockedCallCountSince(since24h),
        repo.flaggedSmsCountSince(since24h),
        trusted.count
    ) { mic, cam, calls, sms, contactCount ->
        DashboardState(
            micEvents24h = mic,
            cameraEvents24h = cam,
            blockedCalls24h = calls,
            flaggedSms24h = sms,
            trustedContactCount = contactCount
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardState())

    data class RuntimeState(
        val sensorPrivacy: SensorPrivacyStatus.Snapshot,
        val trustedOnlyCalls: Boolean,
        val trustedOnlySms: Boolean,
        val auditChainIntact: Boolean?,
        val appLockEnabled: Boolean,
        val isDefaultCallScreener: Boolean,
        val hasAnyTrustedContact: Boolean,
        val canDrawOverlays: Boolean,
        val ownerTag: String,
        /** Normalised numbers of trusted contacts, used by the panic SOS button. */
        val contactNumbers: List<String> = emptyList(),
        val contactNames: List<String> = emptyList()
    )

    private fun initialRuntimeState(): RuntimeState = RuntimeState(
        sensorPrivacy = SensorPrivacyStatus.read(app),
        trustedOnlyCalls = ProtectionSettings.trustedOnlyCalls(app),
        trustedOnlySms = ProtectionSettings.trustedOnlySms(app),
        auditChainIntact = null,
        appLockEnabled = AppLockManager.isEnabled(app),
        isDefaultCallScreener = isDefaultCallScreener(),
        hasAnyTrustedContact = false,
        canDrawOverlays = ShieldController.canDraw(app),
        ownerTag = ProtectionSettings.ownerTag(app)
    )

    fun refreshRuntime() {
        _runtimeState.value = _runtimeState.value.copy(
            sensorPrivacy = SensorPrivacyStatus.read(app),
            trustedOnlyCalls = ProtectionSettings.trustedOnlyCalls(app),
            trustedOnlySms = ProtectionSettings.trustedOnlySms(app),
            appLockEnabled = AppLockManager.isEnabled(app),
            isDefaultCallScreener = isDefaultCallScreener(),
            canDrawOverlays = ShieldController.canDraw(app),
            ownerTag = ProtectionSettings.ownerTag(app)
        )
    }

    fun startShield(): Boolean = ShieldController.turnOn(app)
    fun stopShield() = ShieldController.turnOff(app)
    fun openOverlayPermission() = ShieldController.openOverlayPermissionSettings(app)

    fun setHasAnyTrustedContact(value: Boolean) {
        if (_runtimeState.value.hasAnyTrustedContact != value) {
            _runtimeState.value = _runtimeState.value.copy(hasAnyTrustedContact = value)
        }
        // Also refresh the panic-contact snapshot used by the F1 SOS button.
        viewModelScope.launch {
            val list = trusted.maskedList().first()
            _runtimeState.value = _runtimeState.value.copy(
                contactNumbers = list.flatMap { it.normalisedNumbers },
                contactNames = list.map { it.displayName }
            )
        }
    }

    private fun isDefaultCallScreener(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val role = app.getSystemService(RoleManager::class.java) ?: return false
            role.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        } else {
            val telecom = app.getSystemService(TelecomManager::class.java) ?: return false
            telecom.defaultDialerPackage == app.packageName
        }
    }

    fun setTrustedOnlyCalls(enabled: Boolean) {
        ProtectionSettings.setTrustedOnlyCalls(app, enabled)
        refreshRuntime()
    }

    fun setTrustedOnlySms(enabled: Boolean) {
        ProtectionSettings.setTrustedOnlySms(app, enabled)
        refreshRuntime()
    }

    fun verifyAuditChain() {
        viewModelScope.launch {
            val ok = repo.verifyRecent(limit = 100)
            _runtimeState.value = _runtimeState.value.copy(auditChainIntact = ok)
        }
    }

    fun verifyFullAuditChain() {
        viewModelScope.launch {
            val ok = repo.verifyChain()
            _runtimeState.value = _runtimeState.value.copy(auditChainIntact = ok)
        }
    }

    fun triggerPanicLock(context: Context) {
        AppLockManager.resetUnlock(context)
        viewModelScope.launch { repo.log(type = SecurityEvent.TYPE_PANIC_LOCK) }
    }
}
