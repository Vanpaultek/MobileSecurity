// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import SwiftUI

/// Elite-tier (`SubscriptionState.hasElite`) setup screen for FIDO2 hardware security keys
/// (YubiKey / Google Titan Key / SoloKey). Shown from Settings → Security & data when the
/// user is on Elite. Non-Elite users get the upsell sheet (`HardwareKeyUpsellSheet`) via the
/// row's tap handler in `SettingsScreen`.
///
/// Flow:
///  - Explainer card ("Why hardware security keys matter").
///  - List of enrolled keys with Remove buttons; empty state when none.
///  - "Enroll key" primary button → `HardwareKeyGate.enrollNewKey` (opens iOS security-key
///    sheet). On success the new credential is persisted to the Keychain via
///    `HardwareKeyState.addKey`.
///  - Simulator-only developer skip toggle (only visible when running on the iOS Simulator).
struct HardwareKeySetupScreen: View {
    @Environment(\.dismiss) private var dismiss
    @StateObject private var keys = HardwareKeyState.shared

    @State private var showNameSheet = false
    @State private var pendingName = ""
    @State private var enrollError: String?
    @State private var enrollSuccess: String?

    var body: some View {
        ScrollView(.vertical, showsIndicators: false) {
            VStack(alignment: .leading, spacing: 20) {
                explainerCard
                enrolledKeysCard
                enrollActionCard
                if HardwareKeyState.isRunningOnSimulator {
                    simulatorFallbackCard
                }
                deviceLimitationCard
            }
            .padding(.horizontal, 18)
            .padding(.top, 10)
            .padding(.bottom, 32)
        }
        .appBackdrop()
        .navigationTitle("Hardware Security Key")
        .navigationBarTitleDisplayMode(.inline)
        .alert("Enrollment failed", isPresented: Binding(
            get: { enrollError != nil },
            set: { if !$0 { enrollError = nil } }
        )) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(enrollError ?? "")
        }
        .alert("Key enrolled", isPresented: Binding(
            get: { enrollSuccess != nil },
            set: { if !$0 { enrollSuccess = nil } }
        )) {
            Button("Done", role: .cancel) {}
        } message: {
            Text(enrollSuccess ?? "")
        }
        .sheet(isPresented: $showNameSheet) {
            NavigationStack {
                NameNewKeySheet(
                    name: $pendingName,
                    onCancel: { showNameSheet = false },
                    onContinue: {
                        showNameSheet = false
                        beginEnrollment(name: pendingName)
                    }
                )
            }
            .presentationDetents([.medium])
        }
    }

    // MARK: - Explainer

    private var explainerCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 12) {
                CardHeader(icon: "key.horizontal.fill",
                           title: "Why hardware security keys matter",
                           tint: Color.subGold)
                Text("A hardware key is a physical device (like a YubiKey or Google Titan Key) that proves YOU are present — not just someone who knows your PIN. Once enrolled:")
                    .font(.footnote)
                    .foregroundStyle(Color.appSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)
                bulletRow("Duress Mode can only be disarmed with the key.")
                bulletRow("“Wipe everything” needs the key.")
                bulletRow("Encrypted backup export needs the key.")
                bulletRow("Dead-Man Wipe can only be disabled with the key.")
                Text("This defeats SIM-swap attackers, coerced-PIN entry, and remote account-takeover attempts. Add two keys and keep one in a safe place in case you lose the first.")
                    .font(.footnote)
                    .foregroundStyle(Color.appSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.top, 2)
            }
        }
    }

    private func bulletRow(_ text: String) -> some View {
        HStack(alignment: .top, spacing: 8) {
            Image(systemName: "checkmark.seal.fill")
                .font(.caption)
                .foregroundStyle(Color.appAccent)
                .padding(.top, 2)
            Text(text)
                .font(.footnote)
                .foregroundStyle(Color.appPrimaryText)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    // MARK: - Enrolled keys list

    private var enrolledKeysCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 12) {
                CardHeader(icon: "lock.shield.fill", title: "Enrolled keys")
                if keys.enrolledKeys.isEmpty {
                    Text("No keys enrolled yet. Add one below.")
                        .font(.footnote)
                        .foregroundStyle(Color.appSecondaryText)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.vertical, 6)
                } else {
                    ForEach(keys.enrolledKeys) { key in
                        HStack(spacing: 12) {
                            Image(systemName: "key.horizontal.fill")
                                .font(.subheadline)
                                .foregroundStyle(Color.subGold)
                                .frame(width: 28, height: 28)
                                .background(
                                    Circle().fill(Color.subGold.opacity(0.15))
                                )
                            VStack(alignment: .leading, spacing: 2) {
                                Text(key.displayName)
                                    .font(.subheadline.weight(.semibold))
                                    .foregroundStyle(Color.appPrimaryText)
                                Text("Added on \(key.formattedDateAdded())")
                                    .font(.caption)
                                    .foregroundStyle(Color.appSecondaryText)
                            }
                            Spacer()
                            Button(role: .destructive) {
                                keys.removeKey(id: key.id)
                            } label: {
                                Text("Remove")
                                    .font(.caption.weight(.semibold))
                            }
                            .buttonStyle(.bordered)
                            .tint(Color.appDanger)
                        }
                        .padding(.vertical, 4)
                    }
                }
            }
        }
    }

    // MARK: - Enroll CTA

    private var enrollActionCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 12) {
                Text("Insert / tap your security key when prompted.")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(Color.appPrimaryText)
                Text("Supports USB, Lightning, NFC, and Bluetooth FIDO2 keys. The key never leaves your hand — iOS talks to it directly.")
                    .font(.footnote)
                    .foregroundStyle(Color.appSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)
                Button {
                    pendingName = defaultNameSuggestion()
                    showNameSheet = true
                } label: {
                    HStack(spacing: 8) {
                        Image(systemName: "plus.circle.fill")
                        Text("Enroll key")
                            .font(.body.weight(.semibold))
                    }
                    .frame(maxWidth: .infinity, minHeight: 48)
                }
                .buttonStyle(.borderedProminent)
                .tint(Color.subGold)
            }
        }
    }

    private func defaultNameSuggestion() -> String {
        if keys.enrolledKeys.isEmpty { return "Primary key" }
        return "Backup key \(keys.enrolledKeys.count)"
    }

    // MARK: - Simulator fallback

    private var simulatorFallbackCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 10) {
                HStack(spacing: 8) {
                    Image(systemName: "ladybug.fill")
                        .foregroundStyle(Color.appWarning)
                    Text("Simulator developer skip")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Color.appPrimaryText)
                }
                Text("The iOS Simulator cannot talk to a real USB / Lightning / NFC key. Enable this to short-circuit the hardware-key prompt during manual testing. Device builds ignore this flag.")
                    .font(.caption)
                    .foregroundStyle(Color.appSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)
                Toggle(isOn: $keys.simulatorSkipEnabled) {
                    Text("Skip hardware key check (simulator only)")
                        .font(.footnote.weight(.medium))
                        .foregroundStyle(Color.appPrimaryText)
                }
                .tint(Color.appWarning)
            }
        }
    }

    // MARK: - Limitation note

    private var deviceLimitationCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 6) {
                HStack(spacing: 8) {
                    Image(systemName: "info.circle.fill")
                        .foregroundStyle(Color.appAccent)
                    Text("Device compatibility")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Color.appPrimaryText)
                }
                Text("Hardware key enrollment is not supported in the iOS Simulator — test on a physical device. iOS 15+ required. All enrollment data stays on this device (Keychain, device-only access).")
                    .font(.caption)
                    .foregroundStyle(Color.appSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    // MARK: - Enroll handler

    private func beginEnrollment(name: String) {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        let friendly = trimmed.isEmpty ? defaultNameSuggestion() : trimmed
        HardwareKeyGate.enrollNewKey(displayName: friendly) { result in
            switch result {
            case .success(let key):
                keys.addKey(key)
                enrollSuccess = "“\(key.displayName)” is now armed."
            case .failure(let error):
                enrollError = (error as NSError).localizedDescription
            }
        }
    }
}

// MARK: - Nickname sheet

private struct NameNewKeySheet: View {
    @Binding var name: String
    let onCancel: () -> Void
    let onContinue: () -> Void

    var body: some View {
        VStack(spacing: 16) {
            PremiumCard {
                VStack(alignment: .leading, spacing: 12) {
                    CardHeader(icon: "pencil", title: "Name this key")
                    Text("Pick a nickname you'll remember if you ever need to remove it (for example, “YubiKey 5C” or “Office backup”).")
                        .font(.footnote)
                        .foregroundStyle(Color.appSecondaryText)
                        .fixedSize(horizontal: false, vertical: true)
                    TextField("Key nickname", text: $name)
                        .textFieldStyle(.roundedBorder)
                    HStack {
                        Button("Cancel", role: .cancel, action: onCancel)
                            .buttonStyle(.bordered)
                        Spacer()
                        Button("Continue", action: onContinue)
                            .buttonStyle(.borderedProminent)
                            .tint(Color.subGold)
                            .disabled(name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    }
                }
            }
            Spacer()
        }
        .padding(.horizontal, 18)
        .padding(.top, 18)
        .appBackdrop()
        .navigationTitle("New key")
        .navigationBarTitleDisplayMode(.inline)
    }
}

// MARK: - Upsell sheet (shown to free/Premium/Family/Pro users)

/// Elite upsell sheet shown when a user without Elite taps the Hardware Security Key row.
/// Mirrors the pattern established by `DuressUpsellSheet` / `TrackerFinderUpsellSheet` so
/// the Settings screen can stitch all upsells together uniformly.
struct HardwareKeyUpsellSheet: View {
    let onUpgrade: () -> Void
    let onDismiss: () -> Void

    var body: some View {
        VStack(spacing: 20) {
            Image(systemName: "key.horizontal.fill")
                .font(.system(size: 46, weight: .semibold))
                .foregroundStyle(Color.subGold)
                .padding(.top, 28)
            Text("Hardware Security Key")
                .font(.title2.weight(.bold))
                .foregroundStyle(Color.appPrimaryText)
            Text("Use a YubiKey, Google Titan Key, or any FIDO2 security key to gate the most dangerous actions: disarming Duress Mode, wiping the vault, exporting encrypted backups, and disabling the Dead-Man Wipe. Included in Elite.")
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
