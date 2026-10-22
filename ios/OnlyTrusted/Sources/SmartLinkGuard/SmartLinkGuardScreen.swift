// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import SwiftUI
import UIKit

/// Smart Link Guard (Pro) — iOS screen reached from
///   Settings → Security & data → "Smart Link Guard (Pro)".
///
/// Mirrors the Android screen: QR scan button, manual URL check, recent inspections
/// list with verdict badges, and the plain-language "what we check" explainer card.
/// Everything runs through [SmartLinkGuardRules.inspect] which lives in the shared
/// Swift rules file — zero network calls.
struct SmartLinkGuardScreen: View {
    @StateObject private var state = SmartLinkGuardState.shared

    @State private var manualInput: String = ""
    @State private var activeResult: URLSafetyResult?
    @State private var showScanner = false
    @State private var openError: String?

    var body: some View {
        ScrollView(.vertical, showsIndicators: false) {
            VStack(alignment: .leading, spacing: 20) {
                headerCard
                scannersCard
                recentCard
                explainerCard
                iosLimitCard
            }
            .padding(.horizontal, 18)
            .padding(.top, 10)
            .padding(.bottom, 32)
        }
        .appBackdrop()
        .navigationTitle("Smart Link Guard")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $showScanner) {
            SmartLinkGuardQRScanner(
                onDetected: { value in
                    showScanner = false
                    inspectAndStore(value, source: .qr)
                },
                onCancel: { showScanner = false }
            )
            .ignoresSafeArea()
        }
        .alert("Couldn't open link", isPresented: Binding(
            get: { openError != nil },
            set: { if !$0 { openError = nil } }
        )) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(openError ?? "")
        }
        .sheet(item: Binding(
            get: { activeResult.map(IdentifiableResult.init) },
            set: { activeResult = $0?.value }
        )) { wrap in
            VerdictSheet(
                result: wrap.value,
                onOpen: { openURL(wrap.value.url) },
                onDismiss: { activeResult = nil }
            )
            .presentationDetents([.medium, .large])
        }
        .onAppear { state.reload() }
    }

    // MARK: - Cards

    private var headerCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 8) {
                CardHeader(icon: "shield.lefthalf.filled",
                           title: "On-device link inspection",
                           tint: Color.appAccent)
                Text("Smart Link Guard checks every URL against 8 safety heuristics locally. No cloud, no logging, no browsing history leaves this device.")
                    .font(.footnote)
                    .foregroundStyle(Color.appSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    private var scannersCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 14) {
                Button {
                    showScanner = true
                } label: {
                    HStack(spacing: 10) {
                        Image(systemName: "qrcode.viewfinder")
                        Text("Scan a QR code")
                    }
                    .font(.body.weight(.semibold))
                    .frame(maxWidth: .infinity, minHeight: 48)
                }
                .buttonStyle(.borderedProminent)
                .tint(Color.appBrightCyan)

                Divider().background(Color.appDivider.opacity(0.3))

                Text("Check a URL manually")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(Color.appPrimaryText)

                TextField("Paste or type a link", text: $manualInput)
                    .textFieldStyle(.roundedBorder)
                    .autocorrectionDisabled()
                    .textInputAutocapitalization(.never)
                    .keyboardType(.URL)

                HStack(spacing: 10) {
                    Button {
                        if let str = UIPasteboard.general.string, !str.isEmpty {
                            manualInput = str
                        }
                    } label: {
                        HStack(spacing: 6) {
                            Image(systemName: "doc.on.clipboard")
                            Text("Paste")
                        }
                    }
                    .buttonStyle(.bordered)

                    Spacer()

                    Button {
                        let trimmed = manualInput.trimmingCharacters(in: .whitespacesAndNewlines)
                        guard !trimmed.isEmpty else { return }
                        inspectAndStore(trimmed, source: .manual)
                    } label: {
                        Text("Check")
                            .font(.body.weight(.semibold))
                            .frame(minWidth: 90, minHeight: 36)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(Color.appAccent)
                    .disabled(manualInput.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
        }
    }

    private var recentCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    Text("Recent scans")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Color.appPrimaryText)
                    Spacer()
                    if !state.recent.isEmpty {
                        Button("Clear") { state.clear() }
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(Color.appSecondaryText)
                    }
                }
                if state.recent.isEmpty {
                    Text("Nothing inspected yet. Scan a QR code, paste a URL above, or share a link to OnlyTrusted via the share sheet.")
                        .font(.footnote)
                        .foregroundStyle(Color.appSecondaryText)
                        .fixedSize(horizontal: false, vertical: true)
                } else {
                    ForEach(state.recent) { scan in
                        RecentScanRow(scan: scan) {
                            activeResult = SmartLinkGuardRules.inspect(scan.url)
                        }
                        if scan.id != state.recent.last?.id {
                            Divider().background(Color.appDivider.opacity(0.25))
                        }
                    }
                }
            }
        }
    }

    private var explainerCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 10) {
                CardHeader(icon: "info.circle.fill",
                           title: "What we check",
                           tint: Color.appBrightCyan)
                ForEach(Array(ruleExplainers.enumerated()), id: \.offset) { idx, line in
                    HStack(alignment: .top, spacing: 10) {
                        Text("\(idx + 1).")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(Color.appBrightCyan)
                            .frame(width: 20, alignment: .leading)
                        Text(line)
                            .font(.caption)
                            .foregroundStyle(Color.appSecondaryText)
                    }
                }
            }
        }
    }

    private var iosLimitCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 8) {
                CardHeader(icon: "exclamationmark.bubble.fill",
                           title: "How this works on iOS",
                           tint: Color.appWarning)
                Text("Apple doesn't let apps intercept incoming SMS the way Android does. OnlyTrusted ships a Share Extension — share a suspicious link from Messages or Safari to \"OnlyTrusted Smart Link Guard\" and we'll show the verdict before you tap. QR codes work the same regardless of source.")
                    .font(.caption)
                    .foregroundStyle(Color.appSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    // MARK: - Helpers

    private func inspectAndStore(_ url: String, source: SmartLinkGuardScanSource) {
        let result = SmartLinkGuardRules.inspect(url)
        let scan = SmartLinkGuardScan(
            url: result.url,
            verdict: result.verdict,
            source: source,
            topReason: result.reasons.first ?? ""
        )
        state.record(scan)
        activeResult = result
    }

    private func openURL(_ url: String) {
        let safe = url.contains("://") ? url : "https://\(url)"
        guard let parsed = URL(string: safe) else {
            openError = "This link can't be parsed as a URL."
            return
        }
        UIApplication.shared.open(parsed) { ok in
            if !ok { openError = "iOS wouldn't open this link." }
        }
    }
}

private struct IdentifiableResult: Identifiable {
    let value: URLSafetyResult
    var id: String { value.url }
    init(_ value: URLSafetyResult) { self.value = value }
}

/// Single row in the "Recent scans" list.
private struct RecentScanRow: View {
    let scan: SmartLinkGuardScan
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 10) {
                VerdictBadge(verdict: scan.verdict)
                VStack(alignment: .leading, spacing: 2) {
                    Text(scan.url)
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle(Color.appPrimaryText)
                        .lineLimit(1)
                        .truncationMode(.middle)
                    Text("\(timeString(scan.timestamp)) · \(scan.source.rawValue)" +
                        (scan.topReason.isEmpty ? "" : " · \(scan.topReason)"))
                        .font(.caption2)
                        .foregroundStyle(Color.appSecondaryText)
                        .lineLimit(2)
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.caption)
                    .foregroundStyle(Color.appTertiaryText)
            }
        }
        .buttonStyle(.plain)
    }

    private func timeString(_ d: Date) -> String {
        let f = DateFormatter()
        f.dateStyle = .short
        f.timeStyle = .short
        return f.string(from: d)
    }
}

struct VerdictBadge: View {
    let verdict: URLVerdict

    private var tint: Color {
        switch verdict {
        case .safe: return Color.appSuccess
        case .caution: return Color.appWarning
        case .danger: return Color.appDanger
        }
    }

    private var iconName: String {
        switch verdict {
        case .safe: return "checkmark.shield.fill"
        case .caution: return "exclamationmark.triangle.fill"
        case .danger: return "xmark.octagon.fill"
        }
    }

    var body: some View {
        HStack(spacing: 4) {
            Image(systemName: iconName)
                .font(.caption2.weight(.semibold))
            Text(verdict.badgeText)
                .font(.caption2.weight(.semibold))
        }
        .foregroundStyle(tint)
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
        .background(
            RoundedRectangle(cornerRadius: 10, style: .continuous)
                .fill(tint.opacity(0.15))
                .overlay(
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .stroke(tint.opacity(0.5), lineWidth: 1)
                )
        )
    }
}

/// Modal sheet shown when the user inspects a URL (manual, QR, or recent tap).
struct VerdictSheet: View {
    let result: URLSafetyResult
    let onOpen: () -> Void
    let onDismiss: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack(spacing: 12) {
                VerdictBadge(verdict: result.verdict)
                Text(result.verdict.badgeText)
                    .font(.title3.weight(.bold))
                    .foregroundStyle(Color.appPrimaryText)
                Spacer()
                Button {
                    onDismiss()
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .font(.title2)
                        .foregroundStyle(Color.appSecondaryText)
                }
                .buttonStyle(.plain)
            }

            Text(result.url)
                .font(.footnote)
                .foregroundStyle(Color.appPrimaryText)
                .padding(12)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .fill(Color.appElevatedSurface)
                )

            VStack(alignment: .leading, spacing: 6) {
                ForEach(result.reasons, id: \.self) { r in
                    HStack(alignment: .top, spacing: 6) {
                        Text("•")
                            .foregroundStyle(Color.appSecondaryText)
                        Text(r)
                            .font(.footnote)
                            .foregroundStyle(Color.appSecondaryText)
                    }
                }
            }

            Spacer(minLength: 0)

            HStack(spacing: 10) {
                Button {
                    onDismiss()
                } label: {
                    Text("Cancel")
                        .font(.body.weight(.semibold))
                        .frame(maxWidth: .infinity, minHeight: 44)
                }
                .buttonStyle(.bordered)
                Button {
                    onOpen()
                    onDismiss()
                } label: {
                    HStack(spacing: 6) {
                        Image(systemName: "safari")
                        Text(result.verdict == .danger ? "Open anyway" : "Open")
                    }
                    .font(.body.weight(.semibold))
                    .frame(maxWidth: .infinity, minHeight: 44)
                }
                .buttonStyle(.borderedProminent)
                .tint(result.verdict == .danger ? Color.appDanger : Color.appAccent)
            }
        }
        .padding(.horizontal, 20)
        .padding(.top, 20)
        .padding(.bottom, 20)
        .appBackdrop()
    }
}

/// Pro upsell sheet for Smart Link Guard.
struct SmartLinkGuardUpsellSheet: View {
    let onUpgrade: () -> Void
    let onDismiss: () -> Void

    var body: some View {
        VStack(spacing: 20) {
            Image(systemName: "shield.lefthalf.filled")
                .font(.system(size: 46, weight: .semibold))
                .foregroundStyle(Color.subGold)
                .padding(.top, 28)
            Text("Smart Link Guard")
                .font(.title2.weight(.bold))
                .foregroundStyle(Color.appPrimaryText)
            Text("Inspects every URL from SMS and QR codes before you tap it. Flags shorteners, lookalike domains, suspicious TLDs, and phishing paths — all 100% on-device. Included in Pro.")
                .font(.subheadline)
                .foregroundStyle(Color.appSecondaryText)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)
            Spacer(minLength: 0)
            Button(action: onUpgrade) {
                Text("Upgrade to Pro")
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

private let ruleExplainers: [String] = [
    "Scheme check — insecure http:// and ftp:// are flagged.",
    "Shortener check — bit.ly, t.co, tinyurl, etc. hide the real destination.",
    "Suspicious TLDs — .zip, .mov, .xyz, .top and other scam-heavy endings.",
    "Punycode / homograph — xn-- domains that look like real brands.",
    "Direct IP — real brands use their domain name, not a raw IP.",
    "Verified allowlist — ~200 top legit domains get a visible pass.",
    "Typosquat — Levenshtein distance 1-2 against the allowlist.",
    "Phishing paths — /login, /verify, /wallet, /paypal on unverified hosts."
]
