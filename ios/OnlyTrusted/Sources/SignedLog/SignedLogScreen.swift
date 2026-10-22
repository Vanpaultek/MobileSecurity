// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import SwiftUI
import UniformTypeIdentifiers

/// Elite-tier (`SubscriptionState.hasElite`) setup screen for the Hardware-Signed Audit Log.
///
/// Hardware-key enrollment is a prerequisite — if no key is enrolled we show a stub that
/// links to the enrollment screen. Once enrolled the user can:
///   - Toggle "Sign every audit entry" (default OFF — each event triggers a key tap).
///   - Commit a Merkle batch of events (24h window) and view the root.
///   - Export a signed evidence pack (JSON + PDF).
///   - Verify a previously-exported pack.
struct SignedLogScreen: View {
    @Environment(\.dismiss) private var dismiss
    @StateObject private var signed = SignedLogStore.shared
    @StateObject private var keys = HardwareKeyState.shared

    @State private var showExportShare = false
    @State private var exportBundle: SignedEvidenceExporter.Pack?
    @State private var exportError: String?
    @State private var verifyPickerVisible = false
    @State private var verifyReport: SignedEvidenceVerifier.Report?
    @State private var showEnrollRouter = false
    @State private var commitSuccess: String?

    var body: some View {
        ScrollView {
            VStack(spacing: 18) {
                headerCard
                if keys.hasEnrolledKey {
                    toggleCard
                    merkleCard
                    exportCard
                    verifyCard
                    limitationsCard
                } else {
                    enrollPromptCard
                }
            }
            .padding(20)
        }
        .appBackdrop()
        .navigationTitle("Signed Audit Log")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button("Done") { dismiss() }.tint(Color.appBrightCyan)
            }
        }
        .sheet(isPresented: $showEnrollRouter) {
            NavigationStack { HardwareKeySetupScreen() }
        }
        .sheet(isPresented: $showExportShare) {
            if let bundle = exportBundle {
                ShareSheet(activityItems: [bundle.jsonURL, bundle.pdfURL])
            }
        }
        .alert("Export failed", isPresented: Binding(
            get: { exportError != nil },
            set: { if !$0 { exportError = nil } }
        )) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(exportError ?? "")
        }
        .alert(verifyReport?.valid == true ? "Verified" : "Tampered",
               isPresented: Binding(get: { verifyReport != nil },
                                    set: { if !$0 { verifyReport = nil } })) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(verifyReport?.summary ?? "")
        }
        .alert("Merkle batch committed",
               isPresented: Binding(get: { commitSuccess != nil },
                                    set: { if !$0 { commitSuccess = nil } })) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(commitSuccess ?? "")
        }
        .fileImporter(
            isPresented: $verifyPickerVisible,
            allowedContentTypes: [.json],
            allowsMultipleSelection: false
        ) { result in
            switch result {
            case .success(let urls):
                guard let url = urls.first else { return }
                verifyReport = SignedEvidenceVerifier.verify(url: url)
            case .failure(let e):
                verifyReport = SignedEvidenceVerifier.Report(
                    valid: false, brokenAtEventIndex: nil, summary: e.localizedDescription
                )
            }
        }
    }

    // MARK: - Header

    private var headerCard: some View {
        PremiumCard {
            HStack(alignment: .top, spacing: 14) {
                ZStack {
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .fill(
                            LinearGradient(
                                colors: [Color.subGold, Color.subGold.opacity(0.75)],
                                startPoint: .topLeading, endPoint: .bottomTrailing
                            )
                        )
                        .shadow(color: Color.subGold.opacity(0.6), radius: 10)
                    Image(systemName: "signature")
                        .font(.system(size: 22, weight: .semibold))
                        .foregroundStyle(.white)
                }
                .frame(width: 54, height: 54)
                VStack(alignment: .leading, spacing: 6) {
                    Text("Elite feature")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(Color.subGold)
                    Text("Court-ready audit log.")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Color.appPrimaryText)
                    Text("Every security event is signed with your hardware key and anchored into a local Merkle commitment. Export a tamper-evident PDF for a lawyer or examiner — any altered event or missing signature is spotted the moment the pack is verified.")
                        .font(.footnote)
                        .foregroundStyle(Color.appSecondaryText)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
    }

    // MARK: - Enroll prompt (shown when no hardware key is enrolled)

    private var enrollPromptCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 10) {
                CardHeader(icon: "key.horizontal.fill", title: "Enroll a hardware key first")
                Text("The Signed Audit Log needs a FIDO2 key (YubiKey, Google Titan, SoloKey) to produce signatures. Enroll one on the Hardware Security Key screen, then come back here.")
                    .font(.footnote)
                    .foregroundStyle(Color.appSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)
                Button {
                    showEnrollRouter = true
                } label: {
                    HStack(spacing: 8) {
                        Image(systemName: "arrow.right.circle.fill")
                        Text("Open Hardware Security Key setup")
                            .font(.body.weight(.semibold))
                    }
                    .frame(maxWidth: .infinity, minHeight: 44)
                }
                .buttonStyle(.borderedProminent)
                .tint(Color.subGold)
            }
        }
    }

    // MARK: - Toggle

    private var toggleCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 10) {
                CardHeader(icon: "signature", title: "Sign every audit entry")
                Toggle(isOn: $signed.signEveryEntry) {
                    Text(signed.signEveryEntry
                         ? "Each new security event requires a key tap."
                         : "Signatures only when you commit a Merkle batch.")
                        .font(.footnote.weight(.medium))
                        .foregroundStyle(Color.appPrimaryText)
                }
                .tint(Color.appAccent)
                Text("Default is OFF because every event (blocked call, SMS filter hit) would otherwise ask for a key tap. Lawyers typically turn this on only during a case or trip.")
                    .font(.caption)
                    .foregroundStyle(Color.appSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    // MARK: - Merkle

    private var merkleCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 10) {
                CardHeader(icon: "square.on.square", title: "Merkle commitment")
                statusRow("Last commit", signed.lastCommitDescription)
                statusRow("Events signed", "\(signed.signedCount)")
                Text("Commit a batch to compute a root hash over the latest events. A real deployment would submit this root to OpenTimestamps / RFC 3161 for a public time anchor — this build stores it locally so you can still prove the chain to a verifier.")
                    .font(.caption)
                    .foregroundStyle(Color.appSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)
                Button {
                    if let commit = signed.commitMerkleBatch() {
                        commitSuccess = "\(commit.count) new events anchored.\nRoot: \(commit.rootHex.prefix(16))…"
                    } else {
                        commitSuccess = "Nothing new to commit — the chain is already anchored."
                    }
                } label: {
                    HStack(spacing: 8) {
                        Image(systemName: "arrow.triangle.merge")
                        Text("Commit new events")
                            .font(.subheadline.weight(.semibold))
                    }
                    .frame(maxWidth: .infinity, minHeight: 40)
                }
                .buttonStyle(.bordered)
                .tint(Color.appAccent)
            }
        }
    }

    // MARK: - Export

    private var exportCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 10) {
                CardHeader(icon: "square.and.arrow.up", title: "Export signed evidence pack")
                Text("Produces a two-file bundle: a machine-readable JSON for forensic tools and a human-readable PDF for the courtroom. Both files include the hash chain, signatures, Merkle roots, and the enrolled public keys so a verifier can reproduce every step.")
                    .font(.footnote)
                    .foregroundStyle(Color.appSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)
                Button {
                    do {
                        let bundle = try SignedEvidenceExporter.write()
                        exportBundle = bundle
                        showExportShare = true
                    } catch {
                        exportError = String(describing: error)
                    }
                } label: {
                    HStack(spacing: 8) {
                        Image(systemName: "doc.badge.plus")
                        Text("Export signed evidence pack")
                            .font(.subheadline.weight(.semibold))
                    }
                    .frame(maxWidth: .infinity, minHeight: 44)
                }
                .buttonStyle(.borderedProminent)
                .tint(Color.subGold)
            }
        }
    }

    // MARK: - Verify

    private var verifyCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 10) {
                CardHeader(icon: "checkmark.shield", title: "Verify evidence pack")
                Text("Load a previously-exported pack (either from this device or another). We recompute every row hash and Merkle root; the public key you enrolled is used to confirm the signatures.")
                    .font(.footnote)
                    .foregroundStyle(Color.appSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)
                Button {
                    verifyPickerVisible = true
                } label: {
                    HStack(spacing: 8) {
                        Image(systemName: "doc.text.magnifyingglass")
                        Text("Pick evidence pack to verify")
                            .font(.subheadline.weight(.semibold))
                    }
                    .frame(maxWidth: .infinity, minHeight: 44)
                }
                .buttonStyle(.bordered)
                .tint(Color.appAccent)
            }
        }
    }

    // MARK: - Limitations

    private var limitationsCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 6) {
                HStack(spacing: 8) {
                    Image(systemName: "info.circle.fill")
                        .foregroundStyle(Color.appAccent)
                    Text("What this build does and doesn't")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Color.appPrimaryText)
                }
                Text("• Hardware signatures use iOS's `ASAuthorizationController` tap flow — a real device + enrolled FIDO2 key is required.\n• Merkle commits are stored locally only; this build does not submit them to OpenTimestamps or RFC 3161 servers.\n• A future server-backed verifier can accept this pack unchanged and confirm the signatures off-device.")
                    .font(.caption)
                    .foregroundStyle(Color.appSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    private func statusRow(_ label: String, _ value: String) -> some View {
        HStack {
            Text(label).font(.footnote).foregroundStyle(Color.appSecondaryText)
            Spacer()
            Text(value)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(Color.appPrimaryText)
        }
    }
}

/// Elite upsell sheet for the Signed Audit Log row in Settings.
struct SignedLogUpsellSheet: View {
    let onUpgrade: () -> Void
    let onDismiss: () -> Void

    var body: some View {
        VStack(spacing: 20) {
            Image(systemName: "signature")
                .font(.system(size: 46, weight: .semibold))
                .foregroundStyle(Color.subGold)
                .padding(.top, 28)
            Text("Signed Audit Log")
                .font(.title2.weight(.bold))
                .foregroundStyle(Color.appPrimaryText)
            Text("Every security event is signed with your hardware key and anchored into a local Merkle commitment. Export a court-ready evidence pack: lawyers and examiners can verify every row and spot the exact event number if the pack has been altered. Elite tier.")
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
