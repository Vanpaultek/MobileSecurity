// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import SwiftUI

/// Elite-tier dashboard for the Offline Threat Intel database.
struct ThreatIntelScreen: View {
    @Environment(\.dismiss) private var dismiss

    @State private var vaultHits: [ThreatIntelDatabase.VaultScanHit] = []
    @State private var deviceHits: [ThreatIntelDatabase.DeviceScanHit] = []
    @State private var vaultScanRan = false
    @State private var deviceScanRan = false

    private var db: ThreatIntelDatabase { .shared }

    var body: some View {
        ScrollView {
            VStack(spacing: 18) {
                headerCard
                countersCard
                vaultScanCard
                deviceScanCard
                freshnessCard
            }
            .padding(20)
        }
        .appBackdrop()
        .navigationTitle("Offline Threat Intel")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button("Done") { dismiss() }.tint(Color.appBrightCyan)
            }
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
                                startPoint: .topLeading, endPoint: .bottomTrailing
                            )
                        )
                        .shadow(color: Color.subGold.opacity(0.6), radius: 10)
                    Image(systemName: "shield.lefthalf.filled")
                        .font(.system(size: 22, weight: .semibold))
                        .foregroundStyle(.white)
                }
                .frame(width: 54, height: 54)
                VStack(alignment: .leading, spacing: 6) {
                    Text("Elite feature")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(Color.subGold)
                    Text("Offline threat database.")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Color.appPrimaryText)
                    Text("Bundled scam-numbers, phishing-URL patterns, breached-email prefixes, and Pegasus / Candiru / Predator IoCs. Everything is consulted on-device — not a byte of your vault or device metadata ever leaves the phone.")
                        .font(.footnote)
                        .foregroundStyle(Color.appSecondaryText)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
    }

    private var countersCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 12) {
                CardHeader(icon: "chart.bar.doc.horizontal", title: "Today's intel")
                LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
                    counterTile(title: "Scam numbers", value: db.scamNumbers.count, icon: "phone.badge.waveform")
                    counterTile(title: "Phishing URLs", value: db.phishingPatterns.count, icon: "link.badge.plus")
                    counterTile(title: "Breach prefixes", value: db.breachHashPrefixes.count, icon: "envelope.badge.shield.half.filled")
                    counterTile(title: "Pegasus IoCs", value: pegasusCount, icon: "ladybug")
                }
            }
        }
    }

    private var pegasusCount: Int {
        guard let p = db.pegasus else { return 0 }
        return p.filenames.count + p.plist_entries.count + p.dylib_sha256.count
             + p.process_names.count + p.domains.count
    }

    private func counterTile(title: String, value: Int, icon: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: 6) {
                Image(systemName: icon)
                    .foregroundStyle(Color.appBrightCyan)
                Text(title)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(Color.appSecondaryText)
            }
            Text("\(value)")
                .font(.title3.weight(.bold))
                .foregroundStyle(Color.appPrimaryText)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(10)
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(Color.white.opacity(0.03))
        )
    }

    private var vaultScanCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 10) {
                CardHeader(icon: "person.3.sequence", title: "Check my vault")
                Text("Cross-references every trusted contact's phone + email against the bundled feeds. No contact data is uploaded anywhere.")
                    .font(.footnote)
                    .foregroundStyle(Color.appSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)
                Button {
                    vaultHits = db.scanVault()
                    vaultScanRan = true
                } label: {
                    HStack(spacing: 8) {
                        Image(systemName: "magnifyingglass")
                        Text("Check my vault")
                            .font(.subheadline.weight(.semibold))
                    }
                    .frame(maxWidth: .infinity, minHeight: 44)
                }
                .buttonStyle(.borderedProminent)
                .tint(Color.appAccent)
                if vaultScanRan {
                    if vaultHits.isEmpty {
                        HStack(spacing: 6) {
                            Image(systemName: "checkmark.seal.fill")
                                .foregroundStyle(Color.appSuccess)
                            Text("No matches in the bundled feeds.")
                                .font(.footnote.weight(.semibold))
                                .foregroundStyle(Color.appPrimaryText)
                        }
                    } else {
                        ForEach(vaultHits) { hit in
                            VStack(alignment: .leading, spacing: 2) {
                                HStack(spacing: 6) {
                                    Image(systemName: "exclamationmark.triangle.fill")
                                        .foregroundStyle(Color.appWarning)
                                    Text(hit.contactName)
                                        .font(.subheadline.weight(.semibold))
                                        .foregroundStyle(Color.appPrimaryText)
                                }
                                Text(hit.reason)
                                    .font(.caption)
                                    .foregroundStyle(Color.appSecondaryText)
                                Text(hit.value)
                                    .font(.caption2)
                                    .foregroundStyle(Color.appTertiaryText)
                            }
                            .padding(.vertical, 2)
                        }
                    }
                }
            }
        }
    }

    private var deviceScanCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 10) {
                CardHeader(icon: "iphone.gen3", title: "Scan my device")
                Text("iOS limits third-party apps to their own sandbox, so this scan only walks files OnlyTrusted itself can see. A clean result is expected on a healthy device; a hit means something inside this app's directory matches a Pegasus / Candiru / Predator IoC filename.")
                    .font(.footnote)
                    .foregroundStyle(Color.appSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)
                Button {
                    deviceHits = db.scanSandbox()
                    deviceScanRan = true
                } label: {
                    HStack(spacing: 8) {
                        Image(systemName: "doc.text.magnifyingglass")
                        Text("Scan my device")
                            .font(.subheadline.weight(.semibold))
                    }
                    .frame(maxWidth: .infinity, minHeight: 44)
                }
                .buttonStyle(.bordered)
                .tint(Color.appAccent)
                if deviceScanRan {
                    if deviceHits.isEmpty {
                        HStack(spacing: 6) {
                            Image(systemName: "checkmark.seal.fill")
                                .foregroundStyle(Color.appSuccess)
                            Text("No IoC filenames in the app sandbox.")
                                .font(.footnote.weight(.semibold))
                                .foregroundStyle(Color.appPrimaryText)
                        }
                    } else {
                        ForEach(deviceHits) { hit in
                            VStack(alignment: .leading, spacing: 2) {
                                Text(hit.kind)
                                    .font(.subheadline.weight(.semibold))
                                    .foregroundStyle(Color.appWarning)
                                Text(hit.value)
                                    .font(.caption2)
                                    .foregroundStyle(Color.appTertiaryText)
                                    .lineLimit(2)
                                    .truncationMode(.middle)
                            }
                            .padding(.vertical, 2)
                        }
                    }
                }
            }
        }
    }

    private var freshnessCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 6) {
                HStack(spacing: 8) {
                    Image(systemName: "clock.arrow.circlepath")
                        .foregroundStyle(Color.appAccent)
                    Text("Last update")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Color.appPrimaryText)
                }
                HStack {
                    Text(lastUpdatedLabel)
                        .font(.footnote)
                        .foregroundStyle(Color.appSecondaryText)
                    Spacer()
                    Text("\(db.totalEntries) entries")
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle(Color.appPrimaryText)
                }
                Text("Feeds refresh with each app update. The runtime never reaches out to any server for these — there is no live-fetch path, by design.")
                    .font(.caption)
                    .foregroundStyle(Color.appSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    private var lastUpdatedLabel: String {
        guard let d = db.lastUpdated else { return "Bundled with this release" }
        let fmt = DateFormatter()
        fmt.dateStyle = .medium
        return fmt.string(from: d)
    }
}

/// Elite upsell sheet for the Threat Intel row in Settings.
struct ThreatIntelUpsellSheet: View {
    let onUpgrade: () -> Void
    let onDismiss: () -> Void

    var body: some View {
        VStack(spacing: 20) {
            Image(systemName: "shield.lefthalf.filled")
                .font(.system(size: 46, weight: .semibold))
                .foregroundStyle(Color.subGold)
                .padding(.top, 28)
            Text("Offline Threat Intel")
                .font(.title2.weight(.bold))
                .foregroundStyle(Color.appPrimaryText)
            Text("Bundled scam numbers, phishing URLs, breached-email prefixes, and Pegasus / Candiru / Predator IoCs — all loaded on-device, never a server call. Check your vault for compromised contacts in one tap. Elite tier.")
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
