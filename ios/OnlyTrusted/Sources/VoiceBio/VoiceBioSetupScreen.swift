// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import SwiftUI

/// Elite Voice Biometric enrollment UI. Reached from Settings → "Voice Biometric (Elite)".
/// The parent row is gated on `SubscriptionState.hasElite`; this screen assumes Elite.
///
/// Flow:
///   1. List of trusted contacts from `VaultStore.shared.contacts`.
///   2. Tap a contact → opens an enrollment sheet.
///   3. "Enroll voice" button records 10s via `VoiceBioRecorder`, extracts MFCC,
///      stores the 20-dim descriptor in Keychain via `VoiceBioState`.
///   4. If already enrolled, a "Delete enrollment" button is offered.
///
/// On the iOS Simulator the mic doesn't exist, so recording is blocked with an
/// inline fallback — the Keychain path still works and can be seeded manually.
struct VoiceBioSetupScreen: View {
    @Environment(\.dismiss) private var dismiss
    @StateObject private var state = VoiceBioState.shared
    @StateObject private var store = VaultStore.shared

    @State private var sheetContact: MaskedContact?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                whyCard

                PremiumCard {
                    VStack(alignment: .leading, spacing: 12) {
                        CardHeader(icon: "person.3.fill", title: "Trusted contacts")
                        if store.contacts.isEmpty {
                            Text("You haven't added any trusted contacts yet. Add one from the Contacts tab, then come back here to enroll a voice sample.")
                                .font(.footnote)
                                .foregroundStyle(Color.appSecondaryText)
                                .fixedSize(horizontal: false, vertical: true)
                        } else {
                            ForEach(store.contacts) { contact in
                                Button { sheetContact = contact } label: {
                                    contactRow(contact)
                                }
                                .buttonStyle(.plain)
                                if contact.id != store.contacts.last?.id {
                                    Divider()
                                        .background(Color.appDivider.opacity(0.5))
                                        .padding(.leading, 48)
                                }
                            }
                        }
                    }
                }

                if VoiceBioState.isRunningOnSimulator {
                    simulatorNoticeCard
                }
            }
            .padding(20)
        }
        .appBackdrop()
        .navigationTitle("Voice Biometric")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button("Done") { dismiss() }.tint(Color.appBrightCyan)
            }
        }
        .sheet(item: $sheetContact) { c in
            NavigationStack {
                VoiceBioEnrollmentSheet(contact: c)
            }
        }
        .task { await store.reload() }
    }

    // MARK: - Rows

    private func contactRow(_ contact: MaskedContact) -> some View {
        let enrolled = state.isEnrolled(contactID: contact.id)
        return HStack(spacing: 12) {
            Image(systemName: "waveform.and.mic")
                .font(.body.weight(.semibold))
                .foregroundStyle(enrolled ? Color.appSuccess : Color.appAccent)
                .frame(width: 32, height: 32)
            VStack(alignment: .leading, spacing: 2) {
                Text(contact.displayName)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(Color.appPrimaryText)
                if let first = contact.maskedNumbers.first {
                    Text(first)
                        .font(.caption)
                        .foregroundStyle(Color.appSecondaryText)
                }
            }
            Spacer()
            if enrolled {
                HStack(spacing: 4) {
                    Image(systemName: "checkmark.circle.fill").font(.caption)
                    Text("Enrolled").font(.caption.weight(.semibold))
                }
                .foregroundStyle(Color.appSuccess)
            } else {
                Text("Not enrolled")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(Color.appTertiaryText)
            }
            Image(systemName: "chevron.right")
                .font(.caption)
                .foregroundStyle(Color.appTertiaryText)
        }
        .padding(.vertical, 8)
    }

    // MARK: - Cards

    private var whyCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 10) {
                CardHeader(icon: "sparkles.rectangle.stack", title: "Why this matters")
                Text("AI voice cloning is cheap and fast now. A 10-second clip from social media is enough for a scammer to call pretending to be a family member asking for money. OnlyTrusted enrolls a mathematical fingerprint of each trusted contact's real voice; if the live caller's voice doesn't match during the first few seconds of a call, you'll see a warning.")
                    .font(.footnote)
                    .foregroundStyle(Color.appSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)
                Text("Raw audio is never stored or uploaded — only a 20-number descriptor lives in the device Keychain.")
                    .font(.caption)
                    .foregroundStyle(Color.appTertiaryText)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    private var simulatorNoticeCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 8) {
                HStack(spacing: 10) {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .foregroundStyle(Color.subGold)
                    Text("Simulator limitation")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Color.appPrimaryText)
                }
                Text("The iOS Simulator has no microphone, so voice enrollment can only be completed on a real device. Everything else — Keychain storage, descriptor math, deepfake comparison — works identically here.")
                    .font(.footnote)
                    .foregroundStyle(Color.appSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }
}

// MARK: - Enrollment sheet

/// Modal sheet shown when a contact row is tapped. Owns the recorder lifetime.
struct VoiceBioEnrollmentSheet: View {
    @Environment(\.dismiss) private var dismiss
    @StateObject private var state = VoiceBioState.shared
    let contact: MaskedContact

    enum Phase: Equatable {
        case idle
        case requestingPermission
        case recording(secondsRemaining: Int)
        case processing
        case success
        case error(String)
    }

    @State private var phase: Phase = .idle
    @State private var recorder: VoiceBioRecorder?
    @State private var countdownTask: Task<Void, Never>?

    private var isEnrolled: Bool { state.isEnrolled(contactID: contact.id) }

    var body: some View {
        ScrollView {
            VStack(spacing: 18) {
                PremiumCard {
                    VStack(alignment: .leading, spacing: 10) {
                        CardHeader(icon: "waveform.and.mic", title: contact.displayName)
                        Text(isEnrolled
                             ? "A voice print is already on file for this contact. You can re-enroll (which overwrites the stored descriptor) or delete it below."
                             : "Record a 10-second sample of this contact's voice. Ask them to say their name and read a short sentence (any sentence — the system compares voice character, not words).")
                            .font(.footnote)
                            .foregroundStyle(Color.appSecondaryText)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }

                PremiumCard {
                    VStack(alignment: .leading, spacing: 14) {
                        phaseLabel
                        phaseAction
                        if isEnrolled {
                            Button(role: .destructive) {
                                state.clear(contactID: contact.id)
                                phase = .idle
                            } label: {
                                Text("Delete enrollment")
                                    .font(.body.weight(.semibold))
                                    .frame(maxWidth: .infinity, minHeight: 44)
                            }
                            .buttonStyle(.bordered)
                            .tint(Color.appDanger)
                        }
                    }
                }

                if VoiceBioState.isRunningOnSimulator {
                    PremiumCard {
                        HStack(alignment: .top, spacing: 10) {
                            Image(systemName: "info.circle")
                                .foregroundStyle(Color.subGold)
                            Text("Simulator: enrollment requires a real device microphone. The Keychain and descriptor pipeline still work — you can test the comparison path by seeding a vector programmatically.")
                                .font(.footnote)
                                .foregroundStyle(Color.appSecondaryText)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                }
            }
            .padding(20)
        }
        .appBackdrop()
        .navigationTitle("Enroll voice")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button("Done") { cancel(); dismiss() }.tint(Color.appBrightCyan)
            }
        }
        .onDisappear { cancel() }
    }

    // MARK: - Subviews

    @ViewBuilder
    private var phaseLabel: some View {
        switch phase {
        case .idle:
            HStack(spacing: 8) {
                Image(systemName: isEnrolled ? "checkmark.circle.fill" : "mic.slash")
                    .foregroundStyle(isEnrolled ? Color.appSuccess : Color.appTertiaryText)
                Text(isEnrolled ? "Enrolled" : "Ready to record")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(Color.appPrimaryText)
            }
        case .requestingPermission:
            labelRow(icon: "mic.badge.plus", text: "Requesting microphone permission…", tint: Color.appAccent)
        case .recording(let seconds):
            labelRow(icon: "waveform.circle.fill", text: "Recording… \(seconds)s remaining", tint: Color.appAccent)
        case .processing:
            labelRow(icon: "cpu", text: "Computing voice fingerprint…", tint: Color.appAccent)
        case .success:
            labelRow(icon: "checkmark.seal.fill", text: "Enrollment saved to Keychain.", tint: Color.appSuccess)
        case .error(let msg):
            labelRow(icon: "exclamationmark.triangle.fill", text: msg, tint: Color.appDanger)
        }
    }

    @ViewBuilder
    private var phaseAction: some View {
        switch phase {
        case .recording:
            Button(role: .destructive) { cancel() } label: {
                Text("Cancel").frame(maxWidth: .infinity, minHeight: 48)
            }
            .buttonStyle(.borderedProminent)
            .tint(Color.appDanger)
        case .requestingPermission, .processing:
            ProgressView().frame(maxWidth: .infinity)
        default:
            Button {
                Task { await startEnrollment() }
            } label: {
                Text(isEnrolled ? "Re-enroll voice" : "Enroll voice")
                    .font(.body.weight(.semibold))
                    .frame(maxWidth: .infinity, minHeight: 48)
            }
            .buttonStyle(.borderedProminent)
            .tint(Color.appAccent)
            .disabled(VoiceBioState.isRunningOnSimulator)
        }
    }

    private func labelRow(icon: String, text: String, tint: Color) -> some View {
        HStack(spacing: 10) {
            Image(systemName: icon).foregroundStyle(tint)
            Text(text)
                .font(.footnote.weight(.medium))
                .foregroundStyle(Color.appPrimaryText)
                .fixedSize(horizontal: false, vertical: true)
            Spacer()
        }
    }

    // MARK: - Enrollment flow

    @MainActor
    private func startEnrollment() async {
        guard !VoiceBioState.isRunningOnSimulator else {
            phase = .error("Enrollment requires a real device microphone.")
            return
        }
        phase = .requestingPermission
        let granted = await VoiceBioRecorder.requestPermission()
        guard granted else {
            phase = .error("Microphone permission denied. Open Settings → OnlyTrusted → Microphone.")
            return
        }
        let rec = VoiceBioRecorder()
        self.recorder = rec
        do {
            try rec.start()
        } catch {
            phase = .error((error as? LocalizedError)?.errorDescription ?? "Couldn't start the audio engine.")
            self.recorder = nil
            return
        }

        let duration = Int(VoiceBioState.enrollmentDurationSeconds)
        phase = .recording(secondsRemaining: duration)

        countdownTask = Task { @MainActor in
            for remaining in stride(from: duration - 1, through: 0, by: -1) {
                try? await Task.sleep(nanoseconds: 1_000_000_000)
                if Task.isCancelled { return }
                phase = .recording(secondsRemaining: remaining)
            }
            await finishRecording()
        }
    }

    @MainActor
    private func finishRecording() async {
        guard let rec = recorder else { return }
        recorder = nil
        countdownTask = nil
        phase = .processing

        let samples = rec.stop()
        guard let descriptor = MFCC.utteranceDescriptor(samples: samples) else {
            phase = .error("Couldn't extract a stable voice fingerprint. Try again in a quieter room.")
            return
        }
        state.enroll(contactID: contact.id, descriptor: descriptor)
        phase = .success
    }

    private func cancel() {
        countdownTask?.cancel()
        countdownTask = nil
        _ = recorder?.stop()
        recorder = nil
        if case .recording = phase { phase = .idle }
    }
}

// MARK: - Upsell sheet (non-Elite users)

/// Elite upsell shown when a non-Elite user taps the Voice Biometric row in Settings.
struct VoiceBioUpsellSheet: View {
    let onUpgrade: () -> Void
    let onDismiss: () -> Void

    var body: some View {
        VStack(spacing: 20) {
            Image(systemName: "waveform.and.mic")
                .font(.system(size: 46, weight: .semibold))
                .foregroundStyle(Color.subGold)
                .padding(.top, 28)
            Text("Voice Biometric Verification")
                .font(.title2.weight(.bold))
                .foregroundStyle(Color.appPrimaryText)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 24)
            Text("Enroll a short voice sample for each trusted contact. If someone calls using an AI-cloned voice, OnlyTrusted spots the mismatch and warns you before you share anything. 100% on-device — no audio ever leaves your phone. Elite tier.")
                .font(.subheadline)
                .foregroundStyle(Color.appSecondaryText)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)
            Spacer(minLength: 0)
            Button(action: onUpgrade) {
                Text("Upgrade to Elite")
                    .font(.body.weight(.semibold))
                    .frame(maxWidth: .infinity, minHeight: 48)
            }
            .buttonStyle(.borderedProminent)
            .tint(Color.subGold)
            .padding(.horizontal, 24)
            Button("Not now", action: onDismiss)
                .foregroundStyle(Color.appSecondaryText)
                .padding(.bottom, 20)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .appBackdrop()
    }
}
