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
        ScrollView(.vertical, showsIndicators: false) {
            VStack(spacing: 18) {
                privacyCard
                dataCard
                moreCard
                footerCard
            }
            .padding(.horizontal, 20)
            .padding(.top, 12)
            .padding(.bottom, 32)
        }
        .background(Color(.systemGroupedBackground).ignoresSafeArea())
        .navigationTitle("Settings")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { appLockToggle = gate.appLockEnabled }
        .alert("Set up a passcode first", isPresented: $showLockDeniedAlert) {
            Button("OK", role: .cancel) {}
        } message: {
            Text("Set up a device passcode, Face ID, or Touch ID in the iOS Settings app first. Otherwise App Lock would permanently lock you out.")
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

    private var privacyCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 14) {
                CardHeader(icon: "lock.shield.fill", title: "Privacy")
                VStack(spacing: 0) {
                    Toggle(isOn: $appLockToggle) {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("App Lock").font(.subheadline.weight(.semibold))
                            Text("Require Face ID, Touch ID, or your device passcode to open OnlyTrusted.")
                                .font(.caption)
                                .foregroundStyle(Color.appSecondaryText)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                    .tint(Color.appAccent)
                    .padding(.vertical, 10)
                    .onChange(of: appLockToggle) { _, now in
                        if now && !canUseAuth {
                            appLockToggle = false
                            showLockDeniedAlert = true
                            return
                        }
                        gate.appLockEnabled = now
                    }
                }
            }
        }
    }

    private var dataCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 14) {
                CardHeader(icon: "square.and.arrow.up.fill", title: "Data")
                VStack(spacing: 10) {
                    SystemLinkRow(icon: "square.and.arrow.up", label: "Export my data as JSON") {
                        runExport { try DataExporter.write() }
                    }
                    SystemLinkRow(icon: "doc.badge.plus", label: "Export evidence pack") {
                        runExport { try EvidenceExporter.write() }
                    }
                    Button {
                        confirmWipe = true
                    } label: {
                        HStack(spacing: 14) {
                            Image(systemName: "trash.fill")
                                .font(.body.weight(.semibold))
                                .foregroundStyle(Color.appDanger)
                                .frame(width: 28, height: 28)
                                .decorativeAccessibility()
                            Text("Wipe everything")
                                .font(.subheadline.weight(.medium))
                                .foregroundStyle(Color.appDanger)
                            Spacer()
                        }
                        .padding(.horizontal, 14)
                        .padding(.vertical, 12)
                        .background(
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .fill(Color.appDanger.opacity(0.08))
                        )
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    private var moreCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 14) {
                CardHeader(icon: "ellipsis.circle.fill", title: "More")
                VStack(spacing: 10) {
                    NavigationLink {
                        LegalScreen()
                    } label: {
                        settingsRow(icon: "doc.text.fill", label: "Privacy, terms, and honest claims")
                    }
                    NavigationLink {
                        AboutScreen()
                    } label: {
                        settingsRow(icon: "info.circle.fill", label: "About and attributions")
                    }
                }
            }
        }
    }

    private var footerCard: some View {
        VStack(spacing: 4) {
            Text("OnlyTrusted")
                .font(.footnote.weight(.semibold))
                .foregroundStyle(Color.appSecondaryText)
            Text("© 2026 57Million LLC. GNU AGPL-3.0.")
                .font(.caption2)
                .foregroundStyle(Color.appTertiaryText)
            Text("Support: reddy@vanpaultek.com")
                .font(.caption2)
                .foregroundStyle(Color.appTertiaryText)
        }
        .padding(.top, 8)
    }

    private func settingsRow(icon: String, label: String) -> some View {
        HStack(spacing: 14) {
            Image(systemName: icon)
                .font(.body.weight(.semibold))
                .foregroundStyle(Color.appAccent)
                .frame(width: 28, height: 28)
                .decorativeAccessibility()
            Text(label)
                .font(.subheadline.weight(.medium))
                .foregroundStyle(Color.appPrimaryText)
            Spacer()
            Image(systemName: "chevron.right")
                .font(.footnote.weight(.semibold))
                .foregroundStyle(Color.appTertiaryText)
                .decorativeAccessibility()
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .background(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(Color(.systemGroupedBackground))
        )
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
