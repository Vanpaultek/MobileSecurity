// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.voicebio

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import com.onlytrusted.app.ui.theme.AppBackdropScaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlytrusted.app.OnlyTrustedApp
import com.onlytrusted.app.data.repository.MaskedContact
import com.onlytrusted.app.ui.subscription.subGold
import com.onlytrusted.app.ui.theme.BrandAccentDark
import com.onlytrusted.app.ui.theme.BrandCyanDark
import com.onlytrusted.app.ui.theme.DangerDark
import com.onlytrusted.app.ui.theme.MidnightBackground
import com.onlytrusted.app.ui.theme.MidnightCard
import com.onlytrusted.app.ui.theme.MidnightElevated
import com.onlytrusted.app.ui.theme.PrimaryTextDark
import com.onlytrusted.app.ui.theme.SecondaryTextDark
import com.onlytrusted.app.ui.theme.SuccessDark
import com.onlytrusted.app.ui.theme.TertiaryTextDark
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Elite Voice Biometric enrollment UI (Android). Mirrors the iOS `VoiceBioSetupScreen`:
 *   - Trusted-contact list with Enrolled / Not-enrolled chips.
 *   - Tap row → enrollment dialog → 10s mic capture → MFCC → encrypted storage.
 *   - Delete-enrollment action per contact when already enrolled.
 *   - "Why this matters" explainer.
 *
 * The parent (Settings row) is already gated on `SubscriptionState.hasElite`; this
 * composable assumes the user is on Elite.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceBioSetupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as OnlyTrustedApp

    var contacts by remember { mutableStateOf<List<MaskedContact>>(emptyList()) }
    var enrolledIds by remember { mutableStateOf(VoiceBioState.enrolledContactIds(context)) }
    var sheetContact by remember { mutableStateOf<MaskedContact?>(null) }

    LaunchedEffect(Unit) {
        app.trustedContacts.maskedList().collect { contacts = it }
    }

    AppBackdropScaffold(
        topBar = {
            TopAppBar(
                title = { Text("Voice Biometric", color = PrimaryTextDark) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back", color = BrandCyanDark) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            WhyCard()

            SectionCard(title = "Trusted contacts") {
                if (contacts.isEmpty()) {
                    Text(
                        "You haven't added any trusted contacts yet. Add one from the Contacts tab, then come back here to enroll a voice sample.",
                        fontSize = 13.sp,
                        color = SecondaryTextDark
                    )
                } else {
                    contacts.forEachIndexed { index, c ->
                        ContactRow(
                            contact = c,
                            enrolled = c.id in enrolledIds,
                            onClick = { sheetContact = c }
                        )
                        if (index != contacts.lastIndex) {
                            Divider(
                                color = Color.White.copy(alpha = 0.06f),
                                modifier = Modifier.padding(start = 44.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }

        sheetContact?.let { contact ->
            VoiceBioEnrollmentDialog(
                contact = contact,
                isEnrolled = contact.id in enrolledIds,
                onDismiss = {
                    sheetContact = null
                    enrolledIds = VoiceBioState.enrolledContactIds(context)
                }
            )
        }
    }
}

// ================================ Rows =====================================

@Composable
private fun ContactRow(
    contact: MaskedContact,
    enrolled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.GraphicEq,
                contentDescription = null,
                tint = if (enrolled) SuccessDark else BrandAccentDark,
                modifier = Modifier.size(28.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    contact.displayName,
                    color = PrimaryTextDark,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                contact.maskedNumbers.firstOrNull()?.let { num ->
                    Text(num, color = SecondaryTextDark, fontSize = 12.sp)
                }
            }
            if (enrolled) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = SuccessDark,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        "Enrolled",
                        color = SuccessDark,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                Text(
                    "Not enrolled",
                    color = TertiaryTextDark,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ================================ Cards ====================================

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MidnightElevated,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                title,
                color = PrimaryTextDark,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            content()
        }
    }
}

@Composable
private fun WhyCard() {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MidnightElevated,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = BrandCyanDark
            ) {
                Icon(
                    imageVector = Icons.Filled.MicNone,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.padding(10.dp)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Elite feature",
                    fontSize = 11.sp,
                    color = subGold,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Catches AI voice-cloning scams",
                    color = PrimaryTextDark,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "A 10-second clip from social media is enough for scammers to call pretending to be a family member. OnlyTrusted enrolls a mathematical fingerprint of each trusted contact's real voice so a cloned caller stands out.",
                    color = SecondaryTextDark,
                    fontSize = 12.sp
                )
                Text(
                    "Raw audio is never stored — only a 20-number descriptor lives in the encrypted preferences.",
                    color = TertiaryTextDark,
                    fontSize = 11.sp
                )
            }
        }
    }
}

// ============================ Enrollment dialog ============================

private sealed class EnrollPhase {
    data object Idle : EnrollPhase()
    data object Recording : EnrollPhase()
    data object Processing : EnrollPhase()
    data object Success : EnrollPhase()
    data class Error(val message: String) : EnrollPhase()
}

@Composable
private fun VoiceBioEnrollmentDialog(
    contact: MaskedContact,
    isEnrolled: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recorder = remember { VoiceBioRecorder(context) }
    val contactId = contact.id

    var phase by remember { mutableStateOf<EnrollPhase>(EnrollPhase.Idle) }
    var countdown by remember { mutableIntStateOf(VoiceBioState.ENROLLMENT_DURATION_SECONDS) }
    var activeJob by remember { mutableStateOf<Job?>(null) }

    val startRecording: () -> Unit = start@{
        if (phase is EnrollPhase.Recording || phase is EnrollPhase.Processing) return@start
        countdown = VoiceBioState.ENROLLMENT_DURATION_SECONDS
        phase = EnrollPhase.Recording
        activeJob = scope.launch {
            runEnrollment(
                ctx = context,
                recorder = recorder,
                contactId = contactId,
                scope = this,
                onTick = { countdown = it },
                onPhase = { phase = it }
            )
        }
    }

    val micLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startRecording()
        else phase = EnrollPhase.Error("Microphone permission denied.")
    }

    AlertDialog(
        onDismissRequest = {
            activeJob?.cancel()
            recorder.cancel()
            onDismiss()
        },
        containerColor = MidnightCard,
        title = {
            Text(
                "Enroll ${contact.displayName}",
                color = PrimaryTextDark,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (isEnrolled)
                        "A voice print is already on file for this contact. Re-enroll to overwrite it, or delete the enrollment below."
                    else
                        "Record a 10-second sample of this contact's voice. Any sentence works — the system compares voice character, not words.",
                    color = SecondaryTextDark,
                    fontSize = 13.sp
                )
                when (val p = phase) {
                    EnrollPhase.Idle -> Unit
                    EnrollPhase.Recording -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            color = BrandAccentDark,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            "Recording… ${countdown}s remaining",
                            color = PrimaryTextDark,
                            fontSize = 13.sp
                        )
                    }
                    EnrollPhase.Processing -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            color = BrandAccentDark,
                            modifier = Modifier.size(18.dp)
                        )
                        Text("Computing voice fingerprint…", color = PrimaryTextDark, fontSize = 13.sp)
                    }
                    EnrollPhase.Success -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.CheckCircle, null, tint = SuccessDark)
                        Text("Enrollment saved to encrypted storage.", color = SuccessDark, fontSize = 13.sp)
                    }
                    is EnrollPhase.Error -> Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.Warning, null, tint = DangerDark)
                        Text(p.message, color = DangerDark, fontSize = 13.sp)
                    }
                }
            }
        },
        confirmButton = {
            when (phase) {
                EnrollPhase.Recording, EnrollPhase.Processing -> {
                    TextButton(onClick = {
                        activeJob?.cancel()
                        recorder.cancel()
                        phase = EnrollPhase.Idle
                    }) { Text("Cancel", color = DangerDark) }
                }
                else -> {
                    Button(
                        onClick = {
                            if (recorder.hasPermission()) startRecording()
                            else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandAccentDark)
                    ) {
                        Text(
                            if (isEnrolled || phase is EnrollPhase.Success) "Re-enroll" else "Enroll voice",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (isEnrolled) {
                    OutlinedButton(
                        onClick = {
                            VoiceBioState.clear(context, contactId)
                            onDismiss()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerDark)
                    ) {
                        Text("Delete", color = DangerDark)
                    }
                }
                TextButton(onClick = {
                    activeJob?.cancel()
                    recorder.cancel()
                    onDismiss()
                }) { Text("Close", color = SecondaryTextDark) }
            }
        }
    )
}

/**
 * Runs the 10-second mic capture + MFCC extraction + encrypted enrollment in one coroutine.
 * Reports phase transitions and per-second countdown back to the dialog via callbacks.
 */
private suspend fun runEnrollment(
    ctx: Context,
    recorder: VoiceBioRecorder,
    contactId: Long,
    scope: CoroutineScope,
    onTick: (Int) -> Unit,
    onPhase: (EnrollPhase) -> Unit
) {
    val duration = VoiceBioState.ENROLLMENT_DURATION_SECONDS
    val timer = scope.launch {
        for (remaining in duration - 1 downTo 0) {
            delay(1000)
            onTick(remaining)
        }
    }
    try {
        val samples = recorder.record(duration)
        timer.cancel()
        onPhase(EnrollPhase.Processing)
        val descriptor = MFCC.utteranceDescriptor(samples)
        if (descriptor == null) {
            onPhase(EnrollPhase.Error("Couldn't extract a stable voice fingerprint. Try again in a quieter room."))
            return
        }
        VoiceBioState.enroll(ctx, contactId, descriptor)
        onPhase(EnrollPhase.Success)
    } catch (e: VoiceBioRecorder.StartError) {
        timer.cancel()
        onPhase(EnrollPhase.Error(e.message ?: "Mic error."))
    } catch (t: Throwable) {
        timer.cancel()
        onPhase(EnrollPhase.Error(t.message ?: "Recording failed."))
    }
}

// ============================= Upsell dialog ===============================

/** Shown when a non-Elite user taps the Voice Biometric row in Settings. */
@Composable
fun VoiceBioUpsellDialog(onUpgrade: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MidnightCard,
        title = {
            Text(
                "Voice Biometric is an Elite feature",
                color = PrimaryTextDark,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                "Enroll a short voice sample for each trusted contact. If someone calls using " +
                    "an AI-cloned voice, OnlyTrusted spots the mismatch and warns you. 100% " +
                    "on-device — no audio ever leaves your phone. Elite tier.",
                color = SecondaryTextDark
            )
        },
        confirmButton = {
            TextButton(onClick = onUpgrade) {
                Text("Upgrade to Elite", color = subGold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Not now") }
        }
    )
}
