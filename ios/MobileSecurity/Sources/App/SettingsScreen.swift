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
                Text("Require Face ID / Touch ID / passcode to open MobileSecurity.")
                    .font(.caption).foregroundStyle(.secondary)
            }

            Section("Data") {
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
                    .foregroundStyle(.secondary)
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
                UserDefaults.standard.removePersistentDomain(forName: Bundle.main.bundleIdentifier ?? "")
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This will delete every trusted contact and every setting stored by this app. It cannot be undone.")
        }
    }
}
