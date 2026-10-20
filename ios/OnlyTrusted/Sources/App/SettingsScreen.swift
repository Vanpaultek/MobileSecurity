// SPDX-FileCopyrightText: 2026 57Million LLC
// SPDX-License-Identifier: AGPL-3.0-or-later
import SwiftUI
import LocalAuthentication

struct SettingsScreen: View {
    @EnvironmentObject private var gate: AppGate
    @StateObject private var store = VaultStore.shared

    @State private var appLockToggle = false
    @State private var showLockDeniedAlert = false
    @State private var confirmWipe = false
    @State private var shareURL: URL?
    @State private var exportError: String?

    private var canUseAuth: Bool {
        var err: NSError?
        return LAContext().canEvaluatePolicy(.deviceOwnerAuthentication, error: &err)
    }

    var body: some View {
        Form {
            Section("Privacy") {
                Toggle("App lock", isOn: $appLockToggle)
                    .onChange(of: appLockToggle) { _, now in
                        if now && !canUseAuth {
                            // Prevent the Android-style lockout bug on day one.
                            appLockToggle = false
                            showLockDeniedAlert = true
                            return
                        }
                        gate.appLockEnabled = now
                    }
                Text("Require Face ID / Touch ID / passcode to open OnlyTrusted.")
                    .font(.caption).foregroundStyle(Color.appSecondaryText)
            }

            Section("Data") {
                Button {
                    runExport { try DataExporter.write() }
                } label: {
                    Label("Export my data (JSON)", systemImage: "square.and.arrow.up")
                }
                Button {
                    runExport { try EvidenceExporter.write() }
                } label: {
                    Label("Export evidence pack (for carrier / bank / police)", systemImage: "doc.badge.plus")
                }
                Button(role: .destructive) {
                    confirmWipe = true
                } label: {
                    Label("Wipe everything", systemImage: "trash")
                }
            }

            Section("More") {
                NavigationLink("Privacy, terms & honest claims") {
                    LegalScreen()
                }
                NavigationLink("About & attributions") {
                    AboutScreen()
                }
            }

            Section {
                Text("© 2026 57Million LLC. Licensed under the GNU Affero General Public License v3.0.\nSupport: reddy@vanpaultek.com")
                    .font(.caption)
                    .foregroundStyle(Color.appSecondaryText)
            }
        }
        .navigationTitle("Settings")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { appLockToggle = gate.appLockEnabled }
        .alert("Set up a passcode first", isPresented: $showLockDeniedAlert) {
            Button("OK", role: .cancel) {}
        } message: {
            Text("Set up a device passcode or Face ID / Touch ID in system Settings first — otherwise App Lock would permanently lock you out.")
        }
        .confirmationDialog("Wipe everything?", isPresented: $confirmWipe, titleVisibility: .visible) {
            Button("Wipe", role: .destructive) {
                try? store.wipe()
                try? BlockListStore.shared.wipe()
                EventStore.shared.wipe()
                EventStore.shared.append(type: SecurityEventRecord.Kind.wipe)
                UserDefaults.standard.removePersistentDomain(forName: Bundle.main.bundleIdentifier ?? "")
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This will delete every trusted contact, every blocked number, every event, and every setting stored by this app. It cannot be undone.")
        }
        .sheet(item: Binding(get: { shareURL.map(IdentifiableURL.init) },
                             set: { shareURL = $0?.value })) { wrap in
            ShareSheet(activityItems: [wrap.value])
        }
        .alert("Export failed", isPresented: Binding(get: { exportError != nil }, set: { _ in exportError = nil })) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(exportError ?? "")
        }
    }

    private func runExport(_ work: () throws -> URL) {
        do { shareURL = try work() }
        catch { exportError = String(describing: error) }
    }
}

private struct IdentifiableURL: Identifiable {
    let value: URL
    var id: URL { value }
    init(_ value: URL) { self.value = value }
}
