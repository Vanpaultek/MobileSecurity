// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import SwiftUI

/// Encrypted vault backup (Elite). Prompts for a passphrase, encrypts the full local snapshot
/// into a `.ot2bak` file, and hands it to `UIActivityViewController` so the user picks their
/// own iCloud Drive / Files / Dropbox destination.
///
/// Caller gates on `SubscriptionState.shared.hasElite`.
struct EncryptedBackupScreen: View {
    @Environment(\.dismiss) private var dismiss

    @State private var passphrase: String = ""
    @State private var passphraseAgain: String = ""
    @State private var error: String?
    @State private var shareURL: URL?

    var body: some View {
        ScrollView {
            VStack(spacing: 18) {
                headerCard

                PremiumCard {
                    VStack(alignment: .leading, spacing: 10) {
                        CardHeader(icon: "key.fill", title: "Passphrase")
                        Text("At least 12 characters with a letter, a digit, and a symbol. We cannot recover it for you — if you forget it, the backup is gone.")
                            .font(.footnote)
                            .foregroundStyle(Color.appSecondaryText)
                            .fixedSize(horizontal: false, vertical: true)
                        SecureField("Passphrase", text: $passphrase)
                            .textContentType(.newPassword)
                            .padding(12)
                            .background(RoundedRectangle(cornerRadius: 10).fill(Color.appElevatedSurface))
                        SecureField("Confirm passphrase", text: $passphraseAgain)
                            .textContentType(.newPassword)
                            .padding(12)
                            .background(RoundedRectangle(cornerRadius: 10).fill(Color.appElevatedSurface))
                        if let error {
                            Text(error)
                                .font(.footnote)
                                .foregroundStyle(Color.appDanger)
                        }
                    }
                }

                Button {
                    createBackup()
                } label: {
                    HStack(spacing: 10) {
                        Image(systemName: "externaldrive.badge.icloud")
                        Text("Create encrypted backup")
                            .font(.body.weight(.semibold))
                    }
                    .frame(maxWidth: .infinity, minHeight: 48)
                }
                .buttonStyle(.borderedProminent)
                .tint(Color.subGold)
                .padding(.horizontal, 4)
            }
            .padding(20)
        }
        .appBackdrop()
        .navigationTitle("Encrypted backup")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button("Cancel") { dismiss() }.tint(Color.appBrightCyan)
            }
        }
        .sheet(item: Binding(get: { shareURL.map(BackupIdentifiableURL.init) },
                             set: { shareURL = $0?.value })) { wrap in
            ShareSheet(activityItems: [wrap.value])
        }
    }

    private var headerCard: some View {
        PremiumCard {
            HStack(alignment: .top, spacing: 14) {
                ZStack {
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .fill(
                            LinearGradient(
                                colors: [Color.subGold, Color.subGold.opacity(0.75)],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        )
                        .shadow(color: Color.subGold.opacity(0.6), radius: 10)
                    Image(systemName: "externaldrive.badge.icloud")
                        .font(.system(size: 22, weight: .semibold))
                        .foregroundStyle(.white)
                }
                .frame(width: 54, height: 54)
                VStack(alignment: .leading, spacing: 6) {
                    Text("Elite feature")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(Color.subGold)
                    Text("Portable, offline, zero-trust backup.")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Color.appPrimaryText)
                    Text("Everything in one AES-256-GCM encrypted `.ot2bak` file. Save it anywhere — iCloud Drive, Google Drive, a USB stick. Only your passphrase can decrypt it.")
                        .font(.footnote)
                        .foregroundStyle(Color.appSecondaryText)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
    }

    private func createBackup() {
        error = nil
        if passphrase != passphraseAgain {
            error = "Passphrases don't match."
            return
        }
        do {
            let url = try BackupManager.createBackup(passphrase: passphrase)
            // Share-sheet gives the user the chance to save to iCloud Drive / Files /
            // any share destination that handles files.
            shareURL = url
            // Zero out the fields so we don't keep plaintext in SwiftUI state.
            passphrase = ""
            passphraseAgain = ""
        } catch let err as BackupManager.BackupError {
            error = err.errorDescription
        } catch let unexpected {
            error = String(describing: unexpected)
        }
    }
}

/// Elite upsell sheet for the backup row.
struct EncryptedBackupUpsellSheet: View {
    let onUpgrade: () -> Void
    let onDismiss: () -> Void

    var body: some View {
        VStack(spacing: 20) {
            Image(systemName: "externaldrive.badge.icloud")
                .font(.system(size: 46, weight: .semibold))
                .foregroundStyle(Color.subGold)
                .padding(.top, 28)
            Text("Encrypted vault backup")
                .font(.title2.weight(.bold))
                .foregroundStyle(Color.appPrimaryText)
            Text("Export every trusted contact, blocked number, event, and setting as a single AES-256-GCM encrypted file. You pick where it goes — iCloud, Google Drive, anywhere. Elite tier.")
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

/// Shared Identifiable URL wrapper. Mirrors the private one in SettingsScreen so Backup
/// screens can share-sheet without reaching into Settings' private types.
struct BackupIdentifiableURL: Identifiable {
    let value: URL
    var id: URL { value }
    init(_ value: URL) { self.value = value }
}
