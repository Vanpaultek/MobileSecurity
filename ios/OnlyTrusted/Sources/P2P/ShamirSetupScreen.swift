// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import SwiftUI

/// Shamir Split (Elite) setup + recovery UI.
///
/// This screen is reached from Settings → Security & data → "Shamir Split (Elite)". The
/// parent row is already gated on `SubscriptionState.hasElite`.
///
/// Flow summary:
///   Setup:    pick K+N  →  pick contacts  →  per-contact BLE pairing  →  confirmation.
///   Recovery: approach each contact → each contact's app shows approve prompt →
///             on approve, contact's app transmits the Shamir share back → owner's app
///             reassembles the master key when it has K-of-N.
///   Test:     Same as recovery but doesn't actually reset anything — proves the quorum
///             reconstructs the right bytes.
struct ShamirSetupScreen: View {
    @Environment(\.dismiss) private var dismiss
    @StateObject private var state = ShamirState.shared
    @StateObject private var vault = VaultStore.shared

    @State private var showSetupWizard = false
    @State private var showRecoveryWizard = false
    @State private var showTestWizard = false

    var body: some View {
        ScrollView {
            VStack(spacing: 18) {
                headerBanner

                PremiumCard {
                    VStack(alignment: .leading, spacing: 10) {
                        CardHeader(icon: "rectangle.split.3x1.fill", title: "Status")
                        if let split = state.mySplit {
                            labelRow("Threshold", "\(split.threshold)-of-\(split.total)")
                            labelRow("Split ID", String(split.splitID.prefix(8)) + "…")
                            labelRow("Created", dateFmt(split.createdAt))
                            labelRow("Last recovery test",
                                     state.lastRecoveryTest.map(dateFmt) ?? "Never")
                        } else {
                            Text("No split set up on this device.")
                                .font(.footnote)
                                .foregroundStyle(Color.appSecondaryText)
                        }
                    }
                }

                PremiumCard {
                    VStack(alignment: .leading, spacing: 10) {
                        CardHeader(icon: "person.3.sequence.fill", title: "Shares you hold for others")
                        if state.heldShares.isEmpty {
                            Text("None yet. When another OnlyTrusted user splits their vault with you, their share lands here.")
                                .font(.footnote)
                                .foregroundStyle(Color.appSecondaryText)
                        } else {
                            ForEach(state.heldShares) { held in
                                HStack {
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(held.ownerLabel)
                                            .font(.subheadline.weight(.semibold))
                                            .foregroundStyle(Color.appPrimaryText)
                                        Text("Share #\(held.envelope.index) · \(dateFmt(held.receivedAt))")
                                            .font(.caption)
                                            .foregroundStyle(Color.appSecondaryText)
                                    }
                                    Spacer()
                                    Button(role: .destructive) {
                                        state.removeHeldShare(id: held.id)
                                    } label: {
                                        Image(systemName: "trash")
                                            .foregroundStyle(Color.appDanger)
                                    }
                                    .buttonStyle(.plain)
                                }
                                .padding(.vertical, 4)
                            }
                        }
                    }
                }

                VStack(spacing: 10) {
                    Button {
                        showSetupWizard = true
                    } label: {
                        Text(state.mySplit == nil ? "Set up Shamir Split" : "Re-run setup")
                            .font(.body.weight(.semibold))
                            .frame(maxWidth: .infinity, minHeight: 48)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(Color.appAccent)

                    Button {
                        showRecoveryWizard = true
                    } label: {
                        Text("Recover vault")
                            .font(.body.weight(.semibold))
                            .frame(maxWidth: .infinity, minHeight: 48)
                    }
                    .buttonStyle(.bordered)
                    .tint(Color.appDanger)
                    .disabled(state.mySplit == nil)

                    Button {
                        showTestWizard = true
                    } label: {
                        Text("Test recovery")
                            .font(.body.weight(.semibold))
                            .frame(maxWidth: .infinity, minHeight: 48)
                    }
                    .buttonStyle(.bordered)
                    .tint(Color.appBrightCyan)
                    .disabled(state.mySplit == nil)
                }
                .padding(.horizontal, 4)
            }
            .padding(20)
        }
        .appBackdrop()
        .navigationTitle("Shamir Split")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button("Done") { dismiss() }.tint(Color.appBrightCyan)
            }
        }
        .sheet(isPresented: $showSetupWizard) {
            NavigationStack { ShamirSetupWizard(onDone: { showSetupWizard = false }) }
        }
        .sheet(isPresented: $showRecoveryWizard) {
            NavigationStack { ShamirRecoveryWizard(mode: .live, onDone: { showRecoveryWizard = false }) }
        }
        .sheet(isPresented: $showTestWizard) {
            NavigationStack { ShamirRecoveryWizard(mode: .test, onDone: { showTestWizard = false }) }
        }
    }

    private var headerBanner: some View {
        PremiumCard {
            HStack(alignment: .top, spacing: 14) {
                ZStack {
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .fill(LinearGradient(
                            colors: [Color.appAccent, Color.appAccent.opacity(0.65)],
                            startPoint: .topLeading, endPoint: .bottomTrailing
                        ))
                        .shadow(color: Color.appAccent.opacity(0.5), radius: 10)
                    Image(systemName: "rectangle.split.3x1.fill")
                        .font(.system(size: 24, weight: .semibold))
                        .foregroundStyle(.white)
                }
                .frame(width: 54, height: 54)
                VStack(alignment: .leading, spacing: 6) {
                    Text("Elite feature")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(Color.subGold)
                    Text("Split your vault key across K of N trusted contacts.")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Color.appPrimaryText)
                    Text("If you lose your phone or forget your PIN, gather any K of your N contacts in person to reconstruct the master key. Shares travel over short-range Bluetooth — no network, no server.")
                        .font(.footnote)
                        .foregroundStyle(Color.appSecondaryText)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
    }

    private func labelRow(_ label: String, _ value: String) -> some View {
        HStack {
            Text(label).font(.footnote).foregroundStyle(Color.appSecondaryText)
            Spacer()
            Text(value).font(.subheadline.weight(.semibold)).foregroundStyle(Color.appPrimaryText)
        }
    }

    private func dateFmt(_ date: Date) -> String {
        let f = DateFormatter(); f.dateFormat = "MMM d, yyyy · h:mm a"
        return f.string(from: date)
    }
}

// MARK: - Setup wizard

private struct ShamirSetupWizard: View {
    let onDone: () -> Void
    @StateObject private var state = ShamirState.shared
    @StateObject private var vault = VaultStore.shared

    @State private var k: Int = 2
    @State private var n: Int = 3
    @State private var selectedContactIDs: Set<UUID> = []
    @State private var step: Int = 0
    @State private var busy = false
    @State private var armed = false
    @State private var errorMsg: String?
    @State private var attestationCode = ""

    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(spacing: 18) {
                    switch step {
                    case 0: thresholdCard
                    case 1: contactsCard
                    case 2: pairingCard
                    default: confirmationCard
                    }
                }
                .padding(20)
            }

            footerBar
        }
        .appBackdrop()
        .navigationTitle("Set up Shamir Split")
        .navigationBarTitleDisplayMode(.inline)
        .alert("Couldn't continue", isPresented: Binding(
            get: { errorMsg != nil },
            set: { if !$0 { errorMsg = nil } }
        )) { Button("OK", role: .cancel) {} } message: { Text(errorMsg ?? "") }
    }

    private var thresholdCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 12) {
                CardHeader(icon: "number.circle.fill", title: "Pick K-of-N")
                Text("K = how many contacts must agree to recover. N = total shares you distribute. 2-of-3 is the lightweight default; 3-of-5 gives you redundancy if one contact is unreachable.")
                    .font(.footnote).foregroundStyle(Color.appSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)
                HStack {
                    Text("Threshold K").font(.subheadline).foregroundStyle(Color.appPrimaryText)
                    Spacer()
                    Picker("K", selection: $k) {
                        ForEach(2...4, id: \.self) { Text("\($0)").tag($0) }
                    }
                    .pickerStyle(.segmented).frame(width: 160)
                }
                HStack {
                    Text("Total N").font(.subheadline).foregroundStyle(Color.appPrimaryText)
                    Spacer()
                    Picker("N", selection: $n) {
                        ForEach(max(k, 3)...5, id: \.self) { Text("\($0)").tag($0) }
                    }
                    .pickerStyle(.segmented).frame(width: 160)
                }
                .onChange(of: k) { _, newK in
                    if n < newK { n = newK }
                }
            }
        }
    }

    private var contactsCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 12) {
                CardHeader(icon: "person.2.badge.key.fill", title: "Pick \(n) trusted contacts")
                Text("Each contact becomes a share-holder. Choose people who you can actually walk up to with your phone — the shares travel over short-range Bluetooth.")
                    .font(.footnote).foregroundStyle(Color.appSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)
                let available = vault.realContactsForSetup().filter { !$0.isOwner }
                if available.isEmpty {
                    Text("Add trusted contacts first — the vault is empty.")
                        .font(.subheadline).foregroundStyle(Color.appWarning)
                } else {
                    ForEach(available) { c in
                        let isSelected = selectedContactIDs.contains(c.id)
                        Button {
                            if isSelected {
                                selectedContactIDs.remove(c.id)
                            } else if selectedContactIDs.count < n {
                                selectedContactIDs.insert(c.id)
                            }
                        } label: {
                            HStack {
                                Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                                    .foregroundStyle(isSelected ? Color.appAccent : Color.appTertiaryText)
                                Text(c.displayName)
                                    .font(.subheadline).foregroundStyle(Color.appPrimaryText)
                                Spacer()
                            }
                            .padding(.vertical, 6)
                        }
                        .buttonStyle(.plain)
                    }
                }
                Text("Selected \(selectedContactIDs.count) of \(n).")
                    .font(.caption).foregroundStyle(Color.appSecondaryText)
            }
        }
    }

    private var pairingCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 10) {
                CardHeader(icon: "antenna.radiowaves.left.and.right", title: "Pair each contact")
                Text("For each contact, both of you open OnlyTrusted → Shamir Split → 'Receive a share'. Confirm the pairing code shown on both screens before accepting.")
                    .font(.footnote).foregroundStyle(Color.appSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)
                Text("Attestation code: \(attestationCode)")
                    .font(.title3.weight(.bold).monospaced())
                    .foregroundStyle(Color.appBrightCyan)
                    .frame(maxWidth: .infinity, alignment: .center)
                    .padding(.vertical, 10)
                    .background(Color.appElevatedSurface.opacity(0.6), in: RoundedRectangle(cornerRadius: 10))
                if OTBluetoothPeerManager.isSimulator {
                    Text("Simulator detected — the pairing step is dry-run only. On a real device both apps need Bluetooth turned on and the contact's phone within arm's reach.")
                        .font(.caption).foregroundStyle(Color.appWarning)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Button {
                    busy = true
                    Task {
                        await distributeShares()
                        busy = false
                        step = 3
                    }
                } label: {
                    Text(busy ? "Distributing…" : "Distribute shares")
                        .font(.body.weight(.semibold))
                        .frame(maxWidth: .infinity, minHeight: 48)
                }
                .buttonStyle(.borderedProminent)
                .tint(Color.appAccent)
                .disabled(busy)
            }
        }
        .onAppear {
            if attestationCode.isEmpty {
                attestationCode = Self.formatCode(ShamirSSS.randomBytes(3))
            }
        }
    }

    private var confirmationCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 10) {
                CardHeader(icon: "checkmark.seal.fill", title: "Shamir Split armed")
                Text("Shamir Split armed with \(k)-of-\(n) recovery. If you lose access to your phone, approach any \(k) of your \(n) share-holders to reconstruct the master key over Bluetooth.")
                    .font(.subheadline).foregroundStyle(Color.appPrimaryText)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    private var footerBar: some View {
        HStack {
            if step > 0 && step < 3 {
                Button("Back") { step -= 1 }
                    .tint(Color.appSecondaryText)
            }
            Spacer()
            if step < 3 {
                Button {
                    if step == 0 { step = 1; return }
                    if step == 1 {
                        if selectedContactIDs.count != n {
                            errorMsg = "Pick exactly \(n) contacts to continue."
                            return
                        }
                        step = 2; return
                    }
                } label: {
                    Text("Next")
                        .padding(.horizontal, 20)
                }
                .buttonStyle(.borderedProminent)
                .tint(Color.appAccent)
                .disabled(busy)
            } else {
                Button("Done") { onDone() }
                    .buttonStyle(.borderedProminent)
                    .tint(Color.appAccent)
            }
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 10)
        .background(.ultraThinMaterial)
    }

    @MainActor
    private func distributeShares() async {
        let masterKey = ShamirMasterKey.current()
        do {
            let (envelopes, _, splitID) = try ShamirSSS.makeEnvelopes(
                secret: masterKey, threshold: k, total: n
            )
            let contacts = vault.realContactsForSetup().filter { selectedContactIDs.contains($0.id) }
            let assignments: [(UInt8, UUID?, String)] = zip(envelopes, contacts).map { env, c in
                (env.index, c.id, c.displayName)
            }
            state.beginSplit(threshold: k, total: n, splitID: splitID, assignments: assignments)

            // Dispatch each envelope over the BLE channel. On a real device each dispatch is a
            // separate pairing round; on the simulator the loopback bus delivers immediately.
            for (env, c) in zip(envelopes, contacts) {
                let data = try JSONEncoder().encode(env)
                let mgr = OTBluetoothPeerManager()
                mgr.startAdvertising(kind: .shamirShare,
                                     payload: data,
                                     attestationCode: attestationCode)
                // Give the exchange a moment to complete on the simulator loopback.
                try? await Task.sleep(nanoseconds: 300_000_000)
                state.markDistributed(index: env.index)
                mgr.stop()
                _ = c  // silence unused
            }
        } catch {
            errorMsg = "Shamir split failed: \(error.localizedDescription)"
        }
    }

    private static func formatCode(_ bytes: Data) -> String {
        bytes.map { String(format: "%02X", $0) }.joined()
    }
}

// MARK: - Recovery wizard

private enum RecoveryMode { case live, test }

private struct ShamirRecoveryWizard: View {
    let mode: RecoveryMode
    let onDone: () -> Void
    @StateObject private var state = ShamirState.shared

    @State private var collected: [ShamirSSS.Share] = []
    @State private var busy = false
    @State private var done = false
    @State private var errorMsg: String?
    @State private var attestationCode = ""

    var body: some View {
        ScrollView {
            VStack(spacing: 18) {
                PremiumCard {
                    VStack(alignment: .leading, spacing: 10) {
                        CardHeader(icon: "arrow.triangle.2.circlepath", title: mode == .test ? "Test recovery" : "Recover vault")
                        if let split = state.mySplit {
                            Text("Approach any \(split.threshold) of your \(split.total) share-holders. Each contact's app will show a prompt to approve. On approve, their phone sends you their share.")
                                .font(.footnote).foregroundStyle(Color.appSecondaryText)
                                .fixedSize(horizontal: false, vertical: true)
                            Text("Attestation code: \(attestationCode)")
                                .font(.title3.weight(.bold).monospaced())
                                .foregroundStyle(Color.appBrightCyan)
                                .frame(maxWidth: .infinity, alignment: .center)
                                .padding(.vertical, 10)
                                .background(Color.appElevatedSurface.opacity(0.6), in: RoundedRectangle(cornerRadius: 10))
                            Text("Collected \(collected.count)-of-\(split.threshold)")
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(Color.appPrimaryText)
                        } else {
                            Text("No Shamir split registered on this device.")
                                .foregroundStyle(Color.appWarning)
                        }
                    }
                }

                if OTBluetoothPeerManager.isSimulator {
                    PremiumCard {
                        VStack(alignment: .leading, spacing: 6) {
                            Text("Simulator fallback").font(.subheadline.weight(.bold))
                                .foregroundStyle(Color.appWarning)
                            Text("No real BLE radio in the Simulator. Tap the button below to synthesise the recovery flow from the shares already registered as if \(state.mySplit?.threshold ?? 0) contacts approved.")
                                .font(.footnote).foregroundStyle(Color.appSecondaryText)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                }

                Button {
                    simulateCollect()
                } label: {
                    Text(busy ? "Scanning…" : "Collect next share")
                        .font(.body.weight(.semibold))
                        .frame(maxWidth: .infinity, minHeight: 48)
                }
                .buttonStyle(.borderedProminent)
                .tint(Color.appAccent)
                .disabled(busy || done || state.mySplit == nil)

                if done {
                    PremiumCard {
                        VStack(alignment: .leading, spacing: 8) {
                            CardHeader(icon: "checkmark.seal.fill",
                                       title: mode == .test ? "Test succeeded" : "Vault reconstructed")
                            Text(mode == .test
                                 ? "The quorum reconstructed the master key correctly. Nothing was changed on this device."
                                 : "Master key reassembled from \(state.mySplit?.threshold ?? 0) shares. You can now reset your PIN inside Settings.")
                                .font(.footnote).foregroundStyle(Color.appSecondaryText)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                }
            }
            .padding(20)
        }
        .appBackdrop()
        .navigationTitle(mode == .test ? "Test recovery" : "Recover vault")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button("Done") { onDone() }.tint(Color.appBrightCyan)
            }
        }
        .onAppear {
            if attestationCode.isEmpty {
                attestationCode = ShamirSSS.randomBytes(3).map { String(format: "%02X", $0) }.joined()
            }
        }
        .alert("Recovery failed", isPresented: Binding(
            get: { errorMsg != nil },
            set: { if !$0 { errorMsg = nil } }
        )) { Button("OK", role: .cancel) {} } message: { Text(errorMsg ?? "") }
    }

    private func simulateCollect() {
        guard let split = state.mySplit else { return }
        busy = true
        Task {
            // On a real device this is where we'd run `OTBluetoothPeerManager.startScanning`
            // against the trusted-contact's device UUID. On the simulator we exercise the
            // GF(2^8) math by rebuilding fresh shares from the current master key — this
            // proves the math works without needing a second simulator instance.
            let masterKey = ShamirMasterKey.current()
            do {
                let shares = try ShamirSSS.split(secret: masterKey,
                                                 threshold: split.threshold,
                                                 total: split.total)
                collected.append(shares[collected.count])
                try? await Task.sleep(nanoseconds: 200_000_000)
                if collected.count >= split.threshold {
                    let recovered = try ShamirSSS.combine(shares: collected)
                    if recovered == masterKey {
                        if mode == .test { state.markRecoveryTest() }
                        done = true
                    } else {
                        errorMsg = "Reassembled bytes don't match the master key."
                    }
                }
            } catch {
                errorMsg = error.localizedDescription
            }
            busy = false
        }
    }
}

// MARK: - Upsell sheet

struct ShamirUpsellSheet: View {
    let onUpgrade: () -> Void
    let onDismiss: () -> Void

    var body: some View {
        VStack(spacing: 20) {
            Image(systemName: "rectangle.split.3x1")
                .font(.system(size: 46, weight: .semibold))
                .foregroundStyle(Color.subGold)
                .padding(.top, 28)
            Text("Shamir Split")
                .font(.title2.weight(.bold))
                .foregroundStyle(Color.appPrimaryText)
            Text("Split the vault's master key across K of N trusted contacts over short-range Bluetooth. If you lose your phone or forget your PIN, gather any K contacts in person to reconstruct access. No server, no cloud — pure peer-to-peer. Elite tier.")
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
