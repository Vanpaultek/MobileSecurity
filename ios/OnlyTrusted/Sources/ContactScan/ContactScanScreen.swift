// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import SwiftUI

/// Scan every system contact's phone numbers against the bundled scam-numbers feed.
/// Mirrors Android `ContactScanScreen`. The user triggers the scan explicitly; we never
/// touch Contacts until they tap the button.
struct ContactScanScreen: View {

    @State private var scanning = false
    @State private var report: ContactsScamScanner.ScamScanReport?
    @State private var error: String?

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                heroCard
                if scanning {
                    scanningCard
                } else if let report {
                    summaryCard(report)
                    ForEach(report.matches) { matchCard($0) }
                } else {
                    readyCard
                }
                if let error {
                    PremiumCard {
                        Text(error).font(.caption).foregroundStyle(Color.appDanger)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
            }
            .padding(20)
        }
        .appBackdrop()
        .navigationTitle("Contact Safety Scan")
        .navigationBarTitleDisplayMode(.inline)
    }

    private var heroCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 10) {
                CardHeader(icon: "person.2.badge.gearshape.fill",
                           title: "Scan contacts for scams",
                           tint: Color.appAccent)
                Text("We'll check every phone number in your contacts against our bundled scam-numbers list. Runs on this device only — your contacts are never uploaded or transmitted anywhere.")
                    .font(.footnote).foregroundStyle(Color.appSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)
                Text("Catches scam numbers you may have saved from forgotten calls — a common attack path for 'bank fraud alert' and 'tax arrears' scams.")
                    .font(.caption).foregroundStyle(Color.appSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    private var scanningCard: some View {
        PremiumCard {
            HStack(spacing: 12) {
                ProgressView().tint(Color.appAccent)
                Text("Scanning your contacts…").font(.subheadline).foregroundStyle(Color.appPrimaryText)
            }
            .padding(.vertical, 6)
        }
    }

    private var readyCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 10) {
                Text("Ready to scan.").font(.subheadline).foregroundStyle(Color.appSecondaryText)
                Text("Tapping Scan will trigger iOS to request Contacts permission the first time. We only use it to compare numbers against the local scam list.")
                    .font(.caption).foregroundStyle(Color.appSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)
                Button { runScan() } label: {
                    Text("Scan my contacts")
                        .font(.body.weight(.semibold))
                        .frame(maxWidth: .infinity, minHeight: 48)
                        .background(Color.appAccent, in: RoundedRectangle(cornerRadius: 12))
                        .foregroundStyle(Color.appScreenBackground)
                }
                .buttonStyle(.plain)
            }
        }
    }

    private func summaryCard(_ r: ContactsScamScanner.ScamScanReport) -> some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 8) {
                let hitColor: Color = r.matches.isEmpty ? Color.appSuccess : Color.appDanger
                HStack(spacing: 10) {
                    Image(systemName: r.matches.isEmpty ? "checkmark.seal.fill" : "exclamationmark.triangle.fill")
                        .foregroundStyle(hitColor)
                    Text(r.matches.isEmpty
                         ? "No scam numbers found in your contacts"
                         : "\(r.matches.count) potential scam number(s) found")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(hitColor)
                }
                Text("Scanned \(r.contactsScanned) contact(s), \(r.numbersScanned) phone number(s).")
                    .font(.caption).foregroundStyle(Color.appSecondaryText)
                Button("Re-scan") { runScan() }
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(Color.appAccent)
            }
        }
    }

    private func matchCard(_ m: ContactsScamScanner.ScamMatch) -> some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 6) {
                    Image(systemName: "exclamationmark.triangle.fill").foregroundStyle(Color.appDanger)
                    Text(m.contactName).font(.subheadline.weight(.semibold)).foregroundStyle(Color.appPrimaryText)
                }
                Text(m.phoneNumber).font(.caption).foregroundStyle(Color.appSecondaryText)
                Text("Matched against bundled scam numbers list.")
                    .font(.caption2).foregroundStyle(Color.appSecondaryText)
            }
        }
    }

    private func runScan() {
        scanning = true
        error = nil
        ContactsScamScanner.scan { r, err in
            scanning = false
            report = r
            ContactScanState.save(r)
            if let err {
                error = err.localizedDescription
            }
        }
    }
}

#Preview {
    NavigationStack { ContactScanScreen() }
}
