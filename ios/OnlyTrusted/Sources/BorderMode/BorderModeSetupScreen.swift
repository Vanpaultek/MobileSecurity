// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import SwiftUI
import LocalAuthentication

/// Setup + arm/disarm UI for Border Mode (Elite).
///
/// Flow:
///   • `BorderModeSetupScreen` — big "what this does" explainer, cover-identity setup,
///     red "Arm Border Mode" button with the three-step confirmation.
///   • `CoverIdentityEditor` — owner name + 3-5 fake contacts, persisted to
///     `BorderModeState.coverIdentity`.
///   • `BorderModeUpsellSheet` — shown to non-Elite tiers; routes to Upgrade.
struct BorderModeSetupScreen: View {
    @StateObject private var state = BorderModeState.shared
    @StateObject private var duress = DuressState.shared
    @Environment(\.dismiss) private var dismiss

    @State private var showCoverEditor = false
    @State private var showConfirm1 = false
    @State private var showConfirm2 = false
    @State private var showPinPrompt = false
    @State private var pinInput: String = ""
    @State private var pinError: String?
    @State private var armError: String?
    @State private var showDuressBlockedAlert = false
    @State private var disarmError: String?
    @State private var showDisarmPin = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                headerBanner
                explainerCard
                coverIdentityCard
                armSection
                legalFootnote
            }
            .padding(20)
        }
        .appBackdrop()
        .navigationTitle("Border Mode")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $showCoverEditor) {
            NavigationStack { CoverIdentityEditor() }
        }
        // Confirm 1
        .alert("Border Mode will reconfigure your defences",
               isPresented: $showConfirm1) {
            Button("Continue", role: .destructive) { showConfirm2 = true }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("Border Mode will disable biometric unlock, enable Duress Mode, force Dead-Man Wipe to 7 days, and change your Dashboard identity. Continue?")
        }
        // Confirm 2
        .alert("You'll unlock with your PIN only",
               isPresented: $showConfirm2) {
            Button("Continue", role: .destructive) { showPinPrompt = true }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("You will need your PIN to unlock the app. Make sure you remember it. Continue?")
        }
        // Confirm 3 — PIN check (uses the Duress PIN as proof of knowledge: it was already
        // created by the owner, hashed in UserDefaults, and the only PIN this app actually
        // owns. Reusing it avoids inventing a separate app-PIN for a single gate.)
        .sheet(isPresented: $showPinPrompt) { pinSheet }
        .sheet(isPresented: $showDisarmPin) { disarmPinSheet }
        .alert("Duress Mode must be set up first",
               isPresented: $showDuressBlockedAlert) {
            Button("Open Duress setup") { dismiss() }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("Border Mode needs an active Duress PIN. Set that up first — Settings → Duress Mode (Pro) — then come back to arm Border Mode.")
        }
        .alert("Couldn't arm Border Mode",
               isPresented: Binding(
                get: { armError != nil },
                set: { if !$0 { armError = nil } })) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(armError ?? "")
        }
        .alert("Couldn't disarm",
               isPresented: Binding(
                get: { disarmError != nil },
                set: { if !$0 { disarmError = nil } })) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(disarmError ?? "")
        }
    }

    // MARK: - Sections

    private var headerBanner: some View {
        HStack(spacing: 14) {
            Image(systemName: "airplane.departure")
                .font(.system(size: 34, weight: .semibold))
                .foregroundStyle(.white)
                .padding(14)
                .background(
                    LinearGradient(colors: [Color.appDanger, Color.appDanger.opacity(0.6)],
                                   startPoint: .topLeading, endPoint: .bottomTrailing),
                    in: RoundedRectangle(cornerRadius: 16, style: .continuous)
                )
            VStack(alignment: .leading, spacing: 4) {
                Text("Border Mode").font(.title2.weight(.bold))
                    .foregroundStyle(Color.appPrimaryText)
                Text(state.isArmed ? "Armed since \(armedAtString)" : "Elite · high-risk preset")
                    .font(.footnote)
                    .foregroundStyle(Color.appSecondaryText)
            }
            Spacer()
        }
    }

    private var armedAtString: String {
        guard let t = state.armedAt else { return "now" }
        let f = DateFormatter(); f.dateStyle = .short; f.timeStyle = .short
        return f.string(from: t)
    }

    private var explainerCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 10) {
                CardHeader(icon: "shield.lefthalf.filled",
                           title: "What Border Mode does")
                Text("One tap. Every defensive layer at maximum. Designed for border crossings, airports, protests, and hostile territory.")
                    .font(.footnote)
                    .foregroundStyle(Color.appSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)
                VStack(alignment: .leading, spacing: 8) {
                    checklistRow("1", "Dead-Man Wipe forced to 7 days (shortest).")
                    checklistRow("2", "Duress Mode activated.")
                    checklistRow("3", "Biometric unlock OFF. PIN-only (Fifth-Amendment advantage in US).")
                    checklistRow("4", "Sensor Monitor ON.")
                    checklistRow("5", "Screenshot protection ON.")
                    checklistRow("6", "Shield watermark ON — reads \"DO NOT RECORD\".")
                    checklistRow("7", "Pocket-dial protection ON (Android only).")
                    checklistRow("8", "App Lock auto-locks after 30 s.")
                    checklistRow("9", "Dashboard & Vault show cover identity (if set up).")
                }
            }
        }
    }

    private var coverIdentityCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 10) {
                CardHeader(icon: "person.crop.circle.badge.questionmark",
                           title: "Cover identity")
                let name = state.coverIdentity?.ownerName ?? ""
                let count = state.coverIdentity?.contacts.count ?? 0
                Text(count > 0
                     ? "Cover: \(name.isEmpty ? "(no name)" : name) · \(count) contacts"
                     : "Optional. A plausible owner name + 3-5 fake trusted contacts shown while armed.")
                    .font(.footnote)
                    .foregroundStyle(Color.appSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)
                Button {
                    showCoverEditor = true
                } label: {
                    Text(count > 0 ? "Edit cover identity" : "Set up cover identity")
                        .font(.body.weight(.semibold))
                        .frame(maxWidth: .infinity, minHeight: 44)
                }
                .buttonStyle(.borderedProminent)
                .tint(Color.appAccent)
            }
        }
    }

    private var armSection: some View {
        VStack(spacing: 12) {
            if state.isArmed {
                Button {
                    disarm()
                } label: {
                    Label("Disarm Border Mode", systemImage: "lock.open.fill")
                        .font(.body.weight(.bold))
                        .frame(maxWidth: .infinity, minHeight: 52)
                }
                .buttonStyle(.borderedProminent)
                .tint(Color.appAccent)
            } else {
                Button {
                    guard BorderModeState.shared.duressReady else {
                        showDuressBlockedAlert = true
                        return
                    }
                    showConfirm1 = true
                } label: {
                    Label("Arm Border Mode", systemImage: "exclamationmark.octagon.fill")
                        .font(.body.weight(.bold))
                        .frame(maxWidth: .infinity, minHeight: 52)
                }
                .buttonStyle(.borderedProminent)
                .tint(Color.appDanger)
            }
        }
    }

    private var legalFootnote: some View {
        Text("Border Mode is a defensive tool. It does not help you evade a lawful search. Read Settings → Legal → \"Border Mode & law\" before you cross a border.")
            .font(.caption)
            .foregroundStyle(Color.appTertiaryText)
            .fixedSize(horizontal: false, vertical: true)
            .padding(.top, 4)
    }

    // MARK: - PIN prompt

    private var pinSheet: some View {
        NavigationStack {
            VStack(spacing: 16) {
                Text("Confirm with your PIN")
                    .font(.title3.weight(.bold))
                    .foregroundStyle(Color.appPrimaryText)
                Text("Enter your Duress PIN. Border Mode uses it as proof you know how to unlock the app once biometric is turned off.")
                    .font(.footnote)
                    .foregroundStyle(Color.appSecondaryText)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 20)
                SecureField("PIN", text: $pinInput)
                    .keyboardType(.numberPad)
                    .textContentType(.oneTimeCode)
                    .multilineTextAlignment(.center)
                    .padding(16)
                    .background(RoundedRectangle(cornerRadius: 10).fill(Color.appElevatedSurface))
                    .padding(.horizontal, 24)
                if let pinError {
                    Text(pinError).font(.footnote).foregroundStyle(Color.appDanger)
                }
                Button {
                    submitPinAndArm()
                } label: {
                    Text("Arm")
                        .font(.body.weight(.semibold))
                        .frame(maxWidth: .infinity, minHeight: 44)
                }
                .buttonStyle(.borderedProminent)
                .tint(Color.appDanger)
                .padding(.horizontal, 24)
                Spacer()
            }
            .padding(.top, 20)
            .appBackdrop()
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Cancel") {
                        pinInput = ""; pinError = nil
                        showPinPrompt = false
                    }
                }
            }
        }
    }

    private func submitPinAndArm() {
        // Reuse the Duress PIN check — a user who can't produce it has no business arming
        // the preset either.
        if !duress.matches(pin: pinInput) {
            pinError = "Incorrect PIN."
            return
        }
        pinInput = ""; pinError = nil
        showPinPrompt = false
        do {
            try state.arm()
        } catch {
            armError = (error as? BorderModeState.ArmError)?.errorDescription
                ?? error.localizedDescription
        }
    }

    // MARK: - Disarm (biometric + PIN)

    private func disarm() {
        let ctx = LAContext()
        var err: NSError?
        guard ctx.canEvaluatePolicy(.deviceOwnerAuthentication, error: &err) else {
            // No biometric or passcode set — require PIN only.
            promptDisarmPin()
            return
        }
        ctx.evaluatePolicy(.deviceOwnerAuthentication,
                           localizedReason: "Disarm Border Mode") { ok, _ in
            Task { @MainActor in
                if ok {
                    promptDisarmPin()
                } else {
                    disarmError = "Biometric check failed. Border Mode stays armed."
                }
            }
        }
    }

    private func promptDisarmPin() {
        pinError = nil
        pinInput = ""
        showDisarmPin = true
    }

    private var disarmPinSheet: some View {
        NavigationStack {
            VStack(spacing: 16) {
                Text("Enter PIN to disarm")
                    .font(.title3.weight(.bold))
                SecureField("PIN", text: $pinInput)
                    .keyboardType(.numberPad)
                    .textContentType(.oneTimeCode)
                    .multilineTextAlignment(.center)
                    .padding(16)
                    .background(RoundedRectangle(cornerRadius: 10).fill(Color.appElevatedSurface))
                    .padding(.horizontal, 24)
                if let pinError { Text(pinError).font(.footnote).foregroundStyle(Color.appDanger) }
                Button {
                    if duress.matches(pin: pinInput) {
                        pinInput = ""; pinError = nil
                        showDisarmPin = false
                        state.disarm()
                    } else {
                        pinError = "Incorrect PIN."
                    }
                } label: {
                    Text("Disarm").font(.body.weight(.semibold)).frame(maxWidth: .infinity, minHeight: 44)
                }
                .buttonStyle(.borderedProminent)
                .tint(Color.appAccent)
                .padding(.horizontal, 24)
                Spacer()
            }
            .padding(.top, 20)
            .appBackdrop()
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Cancel") { showDisarmPin = false; pinInput = ""; pinError = nil }
                }
            }
        }
    }

    private func checklistRow(_ num: String, _ text: String) -> some View {
        HStack(alignment: .top, spacing: 10) {
            Text(num)
                .font(.caption.weight(.bold))
                .foregroundStyle(Color.appAccent)
                .frame(width: 20)
            Text(text)
                .font(.footnote)
                .foregroundStyle(Color.appPrimaryText)
                .fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 0)
        }
    }
}

// MARK: - Cover Identity editor

struct CoverIdentityEditor: View {
    @StateObject private var state = BorderModeState.shared
    @Environment(\.dismiss) private var dismiss

    @State private var ownerName: String = ""
    @State private var contacts: [BorderModeState.CoverContact] = []

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                PremiumCard {
                    VStack(alignment: .leading, spacing: 10) {
                        CardHeader(icon: "person.text.rectangle",
                                   title: "Owner name")
                        Text("Shown in the Dashboard greeting and watermark when Border Mode is armed. Pick something plausible.")
                            .font(.footnote)
                            .foregroundStyle(Color.appSecondaryText)
                            .fixedSize(horizontal: false, vertical: true)
                        TextField("e.g. John Doe", text: $ownerName)
                            .textFieldStyle(.roundedBorder)
                    }
                }
                PremiumCard {
                    VStack(alignment: .leading, spacing: 10) {
                        CardHeader(icon: "person.3.sequence",
                                   title: "Cover contacts (3-5)")
                        Text("Fake trusted contacts shown in the Vault while armed. Use names + numbers that look boring and real.")
                            .font(.footnote)
                            .foregroundStyle(Color.appSecondaryText)
                            .fixedSize(horizontal: false, vertical: true)
                        ForEach($contacts) { $c in
                            HStack {
                                TextField("Name", text: $c.name)
                                    .textFieldStyle(.roundedBorder)
                                TextField("Number", text: $c.number)
                                    .textFieldStyle(.roundedBorder)
                                    .keyboardType(.phonePad)
                                Button {
                                    contacts.removeAll { $0.id == c.id }
                                } label: {
                                    Image(systemName: "minus.circle.fill")
                                        .foregroundStyle(Color.appDanger)
                                }
                            }
                        }
                        if contacts.count < 5 {
                            Button {
                                contacts.append(BorderModeState.CoverContact())
                            } label: {
                                Label("Add contact", systemImage: "plus.circle.fill")
                                    .font(.footnote.weight(.semibold))
                            }
                            .tint(Color.appAccent)
                        }
                    }
                }
                Button {
                    save()
                    dismiss()
                } label: {
                    Text("Save cover identity")
                        .font(.body.weight(.semibold))
                        .frame(maxWidth: .infinity, minHeight: 48)
                }
                .buttonStyle(.borderedProminent)
                .tint(Color.appAccent)
            }
            .padding(20)
        }
        .appBackdrop()
        .navigationTitle("Cover identity")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            if let cover = state.coverIdentity {
                ownerName = cover.ownerName
                contacts = cover.contacts
            } else {
                contacts = (0..<3).map { _ in BorderModeState.CoverContact() }
            }
        }
    }

    private func save() {
        let cleaned = contacts.filter {
            !$0.name.trimmingCharacters(in: .whitespaces).isEmpty
                && !$0.number.trimmingCharacters(in: .whitespaces).isEmpty
        }
        state.coverIdentity = BorderModeState.CoverIdentity(
            ownerName: ownerName.trimmingCharacters(in: .whitespacesAndNewlines),
            contacts: cleaned
        )
    }
}

// MARK: - Upsell sheet

struct BorderModeUpsellSheet: View {
    let onUpgrade: () -> Void
    let onDismiss: () -> Void

    var body: some View {
        VStack(spacing: 20) {
            Image(systemName: "airplane.departure")
                .font(.system(size: 46, weight: .semibold))
                .foregroundStyle(Color.subGold)
                .padding(.top, 28)
            Text("Border Mode").font(.title2.weight(.bold))
                .foregroundStyle(Color.appPrimaryText)
            Text("One tap. Every defensive layer at maximum. Dead-Man Wipe to 7 days, Duress active, biometric off, screenshot & shield on, cover identity on your Dashboard. Elite tier.")
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
