// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import SwiftUI
import UniformTypeIdentifiers

/// Restore (Elite). File picker → passphrase → decrypt → confirm → apply.
///
/// Caller gates on `SubscriptionState.shared.hasElite`.
struct BackupRestoreScreen: View {
    @Environment(\.dismiss) private var dismiss

    @State private var pickedURL: URL?
    @State private var passphrase: String = ""
    @State private var error: String?
    @State private var showPicker = false
    @State private var decoded: BackupPayload?
    @State private var confirmApply = false
    @State private var done = false

    var body: some View {
        ScrollView {
            VStack(spacing: 18) {
                headerCard

                if pickedURL == nil {
                    Button { showPicker = true } label: {
                        HStack(spacing: 10) {
                            Image(systemName: "arrow.down.doc")
                            Text("Pick backup file (.ot2bak)")
                                .font(.body.weight(.semibold))
                        }
                        .frame(maxWidth: .infinity, minHeight: 48)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(Color.subGold)
                    .padding(.horizontal, 4)
                } else {
                    PremiumCard {
                        VStack(alignment: .leading, spacing: 8) {
                            CardHeader(icon: "doc.badge.gearshape", title: "File")
                            Text(pickedURL?.lastPathComponent ?? "")
                                .font(.footnote)
                                .foregroundStyle(Color.appPrimaryText)
                            Button {
                                pickedURL = nil
                                decoded = nil
                                passphrase = ""
                                error = nil
                            } label: {
                                Text("Pick a different file")
                                    .font(.footnote.weight(.semibold))
                                    .foregroundStyle(Color.appBrightCyan)
                            }
                        }
                    }

                    PremiumCard {
                        VStack(alignment: .leading, spacing: 10) {
                            CardHeader(icon: "key.fill", title: "Passphrase")
                            Text("The passphrase you entered when you created this backup.")
                                .font(.footnote)
                                .foregroundStyle(Color.appSecondaryText)
                                .fixedSize(horizontal: false, vertical: true)
                            SecureField("Passphrase", text: $passphrase)
                                .textContentType(.password)
                                .padding(12)
                                .background(RoundedRectangle(cornerRadius: 10).fill(Color.appElevatedSurface))
                            if let error {
                                Text(error)
                                    .font(.footnote)
                                    .foregroundStyle(Color.appDanger)
                                    .fixedSize(horizontal: false, vertical: true)
                            }
                        }
                    }

                    Button { attemptDecrypt() } label: {
                        HStack(spacing: 10) {
                            Image(systemName: "lock.open.fill")
                            Text("Decrypt and preview")
                                .font(.body.weight(.semibold))
                        }
                        .frame(maxWidth: .infinity, minHeight: 48)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(Color.appAccent)
                    .padding(.horizontal, 4)
                }

                if let decoded {
                    PremiumCard {
                        VStack(alignment: .leading, spacing: 10) {
                            CardHeader(icon: "checkmark.seal.fill", title: "Backup contents")
                            previewRow("Exported", dateFmt(decoded.exportedAtMillis))
                            previewRow("Platform", decoded.platform)
                            previewRow("Contacts", "\(decoded.contacts.count)")
                            previewRow("Blocked numbers", "\(decoded.blockedNumbers.count)")
                            previewRow("Events", "\(decoded.events.count)")
                        }
                    }

                    Button { confirmApply = true } label: {
                        HStack(spacing: 10) {
                            Image(systemName: "arrow.triangle.2.circlepath")
                            Text("Restore and overwrite existing data")
                                .font(.body.weight(.semibold))
                        }
                        .frame(maxWidth: .infinity, minHeight: 48)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(Color.appDanger)
                    .padding(.horizontal, 4)
                }
            }
            .padding(20)
        }
        .appBackdrop()
        .navigationTitle("Restore backup")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button(done ? "Done" : "Cancel") { dismiss() }
                    .tint(Color.appBrightCyan)
            }
        }
        .fileImporter(
            isPresented: $showPicker,
            allowedContentTypes: [UTType(filenameExtension: "ot2bak") ?? .data, .data],
            allowsMultipleSelection: false
        ) { result in
            switch result {
            case .success(let urls):
                pickedURL = urls.first
                decoded = nil
                error = nil
            case .failure(let err):
                error = err.localizedDescription
            }
        }
        .confirmationDialog(
            "Overwrite everything?",
            isPresented: $confirmApply,
            titleVisibility: .visible
        ) {
            Button("Overwrite and restore", role: .destructive) { applyRestore() }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("All current trusted contacts, blocked numbers, events, and settings will be replaced by the backup. This cannot be undone.")
        }
    }

    private var headerCard: some View {
        PremiumCard {
            HStack(alignment: .top, spacing: 14) {
                ZStack {
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .fill(
                            LinearGradient(
                                colors: [Color.appBrightCyan, Color.appBrightCyan.opacity(0.75)],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        )
                        .shadow(color: Color.appBrightCyan.opacity(0.6), radius: 10)
                    Image(systemName: "arrow.down.doc")
                        .font(.system(size: 22, weight: .semibold))
                        .foregroundStyle(.white)
                }
                .frame(width: 54, height: 54)
                VStack(alignment: .leading, spacing: 6) {
                    Text("Elite feature")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(Color.subGold)
                    Text("Restore from an encrypted `.ot2bak`.")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Color.appPrimaryText)
                    Text("Pick the file, enter the passphrase, and confirm. Everything in the app will be overwritten with the backup contents.")
                        .font(.footnote)
                        .foregroundStyle(Color.appSecondaryText)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
    }

    private func previewRow(_ label: String, _ value: String) -> some View {
        HStack {
            Text(label).font(.footnote).foregroundStyle(Color.appSecondaryText)
            Spacer()
            Text(value).font(.subheadline.weight(.semibold)).foregroundStyle(Color.appPrimaryText)
        }
    }

    private func attemptDecrypt() {
        error = nil
        guard let url = pickedURL else {
            error = "Pick a backup file first."
            return
        }
        guard !passphrase.isEmpty else {
            error = "Enter the passphrase you used when you created the backup."
            return
        }
        do {
            decoded = try BackupManager.decryptBackup(fileURL: url, passphrase: passphrase)
        } catch let err as BackupManager.BackupError {
            error = err.errorDescription
            decoded = nil
        } catch let unexpected {
            error = String(describing: unexpected)
            decoded = nil
        }
    }

    private func applyRestore() {
        guard let decoded else { return }
        do {
            try BackupManager.applyRestore(decoded)
            self.done = true
            // Clear plaintext from state.
            passphrase = ""
            self.decoded = nil
            self.pickedURL = nil
        } catch {
            self.error = String(describing: error)
        }
    }

    private func dateFmt(_ millis: Int64) -> String {
        let d = Date(timeIntervalSince1970: TimeInterval(millis) / 1000)
        let f = DateFormatter()
        f.dateFormat = "MMM d, yyyy · h:mm a"
        return f.string(from: d)
    }
}

/// Elite upsell sheet for the restore row.
struct BackupRestoreUpsellSheet: View {
    let onUpgrade: () -> Void
    let onDismiss: () -> Void

    var body: some View {
        VStack(spacing: 20) {
            Image(systemName: "arrow.down.doc")
                .font(.system(size: 46, weight: .semibold))
                .foregroundStyle(Color.subGold)
                .padding(.top, 28)
            Text("Restore from backup")
                .font(.title2.weight(.bold))
                .foregroundStyle(Color.appPrimaryText)
            Text("Pick a `.ot2bak` file from anywhere on your device, enter your passphrase, and OnlyTrusted restores every contact, blocked number, event, and setting. Elite tier.")
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
