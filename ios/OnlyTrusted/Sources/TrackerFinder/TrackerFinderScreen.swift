// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import SwiftUI

/// Pro-tier Tracker Finder screen. Pro-gating is handled by the Settings row that opens
/// this screen — see `SettingsScreen.swift`. If this view is instantiated the viewer is
/// already on `SubscriptionState.hasHighPremium`.
///
/// Shape differs from Android because iOS does not let third-party apps run continuous
/// BLE scans from the background without a special entitlement, and because iOS 14.5+
/// already auto-alerts owners about separated AirTags via the Find My network. We make
/// those limits explicit in the explainer card so users understand what we add on top of
/// Apple's own protection.
struct TrackerFinderScreen: View {

    @StateObject private var scanner = TrackerFinderScanner.shared
    @State private var confirmClear = false

    var body: some View {
        ScrollView(.vertical, showsIndicators: false) {
            VStack(alignment: .leading, spacing: 20) {
                heroCard
                scanCard
                followersCard
                recentCard
                explainerCard
                clearButton
            }
            .padding(.horizontal, 18)
            .padding(.top, 10)
            .padding(.bottom, 32)
        }
        .appBackdrop()
        .navigationTitle("Tracker Finder")
        .navigationBarTitleDisplayMode(.inline)
        .confirmationDialog("Clear tracker history?",
                            isPresented: $confirmClear,
                            titleVisibility: .visible) {
            Button("Clear", role: .destructive) {
                TrackerFinderStore.clear()
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("Removes every stored sighting from this device. The follower list will reset — devices will only re-appear after three new scan sessions.")
        }
    }

    // MARK: - Cards

    private var heroCard: some View {
        PremiumCard {
            HStack(spacing: 14) {
                ZStack {
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(Color.appAccent.opacity(0.18))
                    Image(systemName: "location.magnifyingglass")
                        .font(.system(size: 24, weight: .semibold))
                        .foregroundStyle(Color.appAccent)
                }
                .frame(width: 48, height: 48)
                .decorativeAccessibility()
                VStack(alignment: .leading, spacing: 4) {
                    Text("Scan for AirTags, Tiles, and SmartTags")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Color.appPrimaryText)
                    Text("A 30-second scan looks for known tracker signatures and flags any device that keeps reappearing across your scans — the pattern a stalker-tag would create.")
                        .font(.caption)
                        .foregroundStyle(Color.appSecondaryText)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
    }

    private var scanCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 12) {
                CardHeader(icon: "antenna.radiowaves.left.and.right", title: "One-shot scan")
                scanStatusLine
                Button {
                    scanner.startOneShot(duration: 30)
                } label: {
                    HStack {
                        Image(systemName: "dot.radiowaves.left.and.right")
                        Text(scanButtonTitle)
                            .font(.body.weight(.semibold))
                    }
                    .frame(maxWidth: .infinity, minHeight: 44)
                }
                .buttonStyle(.borderedProminent)
                .tint(Color.appAccent)
                .disabled(isScanning)

                if isScanning {
                    Button("Stop scan") {
                        scanner.stop()
                    }
                    .foregroundStyle(Color.appSecondaryText)
                }
            }
        }
    }

    private var followersCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 12) {
                CardHeader(icon: "exclamationmark.triangle.fill",
                           title: "Possible followers")
                if scanner.followers.isEmpty {
                    Text("No device is reappearing across your scans yet. A tracker has to be seen in 3+ scans over more than 15 minutes before it ends up here.")
                        .font(.footnote)
                        .foregroundStyle(Color.appSecondaryText)
                        .fixedSize(horizontal: false, vertical: true)
                } else {
                    ForEach(Array(scanner.followers.enumerated()), id: \.element.id) { pair in
                        if pair.offset > 0 { SettingsDivider() }
                        FollowerRow(tracker: pair.element)
                    }
                }
            }
        }
    }

    private var recentCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 12) {
                CardHeader(icon: "list.bullet.rectangle.portrait",
                           title: "What this scan saw")
                if scanner.liveSightings.isEmpty {
                    Text("No tracker signatures in this session yet. Tap Scan now, then walk a few metres so the signal strength can change.")
                        .font(.footnote)
                        .foregroundStyle(Color.appSecondaryText)
                        .fixedSize(horizontal: false, vertical: true)
                } else {
                    ForEach(Array(scanner.liveSightings.prefix(10).enumerated()), id: \.element.id) { pair in
                        if pair.offset > 0 { SettingsDivider() }
                        SightingRow(sighting: pair.element)
                    }
                }
            }
        }
    }

    private var explainerCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 12) {
                CardHeader(icon: "info.circle.fill", title: "What this does — and doesn't")
                explainerLine(
                    icon: "checkmark.circle.fill",
                    tint: Color.appSuccess,
                    text: "We flag AirTag, Tile, and Samsung SmartTag signatures, plus any unnamed BLE device that reappears across 3+ scans over >15 minutes."
                )
                explainerLine(
                    icon: "sparkles",
                    tint: Color.appAccent,
                    text: "iOS 14.5+ already auto-alerts you about AirTags that have been separated from their owner and travel with you. Tracker Finder is additive — it also catches Tile and SmartTag, which Apple's built-in alert doesn't."
                )
                explainerLine(
                    icon: "exclamationmark.circle.fill",
                    tint: Color.appWarning,
                    text: "iOS does not let third-party apps keep a BLE scan running from the lock screen for hours. Each scan is a 30-second foreground run that you tap off."
                )
                explainerLine(
                    icon: "lock.fill",
                    tint: Color.appAccent,
                    text: "Peripheral identifiers are hashed with a per-install salt before they touch disk, and the sighting file is sealed with AES-GCM in Application Support."
                )
            }
        }
    }

    private func explainerLine(icon: String, tint: Color, text: String) -> some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: icon)
                .font(.footnote.weight(.semibold))
                .foregroundStyle(tint)
                .frame(width: 20, alignment: .center)
                .decorativeAccessibility()
            Text(text)
                .font(.footnote)
                .foregroundStyle(Color.appSecondaryText)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    private var clearButton: some View {
        Button(role: .destructive) {
            confirmClear = true
        } label: {
            HStack {
                Image(systemName: "trash.fill")
                Text("Clear history")
                    .font(.body.weight(.semibold))
            }
            .frame(maxWidth: .infinity, minHeight: 44)
        }
        .buttonStyle(.bordered)
        .tint(Color.appDanger)
    }

    // MARK: - Scan status helpers

    private var isScanning: Bool {
        if case .scanning = scanner.state { return true }
        return false
    }

    private var scanButtonTitle: String {
        switch scanner.state {
        case .scanning(let remaining): return "Scanning… \(remaining)s"
        default: return "Scan now (30 s)"
        }
    }

    private var scanStatusLine: some View {
        let text: String = {
            switch scanner.state {
            case .idle:
                return "Tap scan to look for AirTags, Tiles, SmartTags, and unknown BLE devices."
            case .bluetoothUnavailable(let reason):
                return reason
            case .scanning(let remaining):
                return "Scanning… \(remaining) seconds left."
            case .stopped(let detections):
                return "Scan finished — \(detections) classified signature\(detections == 1 ? "" : "s")."
            }
        }()
        return Text(text)
            .font(.footnote)
            .foregroundStyle(Color.appSecondaryText)
            .fixedSize(horizontal: false, vertical: true)
    }
}

// MARK: - Rows

private struct FollowerRow: View {
    let tracker: TrackerFinderStore.FollowerTracker

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            ZStack {
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .fill(Color.appWarning.opacity(0.16))
                Image(systemName: "dot.radiowaves.left.and.right")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(Color.appWarning)
            }
            .frame(width: 36, height: 36)
            .decorativeAccessibility()
            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    Text(tracker.type.displayName)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Color.appPrimaryText)
                    Spacer()
                    Text("\(tracker.sessionCount) scans")
                        .font(.caption2.weight(.semibold))
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .background(Color.appWarning.opacity(0.18), in: Capsule())
                        .foregroundStyle(Color.appWarning)
                }
                Text("ID \(tracker.shortLabel) · avg RSSI \(tracker.averageRssi) dBm")
                    .font(.caption)
                    .foregroundStyle(Color.appSecondaryText)
                Text("First seen \(relative(tracker.firstSeen)) · last seen \(relative(tracker.lastSeen))")
                    .font(.caption2)
                    .foregroundStyle(Color.appTertiaryText)
            }
        }
        .padding(.vertical, 6)
    }

    private func relative(_ date: Date) -> String {
        let f = RelativeDateTimeFormatter()
        f.unitsStyle = .abbreviated
        return f.localizedString(for: date, relativeTo: Date())
    }
}

private struct SightingRow: View {
    let sighting: TrackerFinderStore.Sighting

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .font(.footnote.weight(.semibold))
                .foregroundStyle(tint)
                .frame(width: 24)
                .decorativeAccessibility()
            VStack(alignment: .leading, spacing: 2) {
                Text(sighting.type.displayName)
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(Color.appPrimaryText)
                Text("ID …\(String(sighting.idHash.suffix(6))) · \(sighting.rssi) dBm")
                    .font(.caption2)
                    .foregroundStyle(Color.appTertiaryText)
            }
            Spacer()
            Text(sighting.timestamp, style: .time)
                .font(.caption2)
                .foregroundStyle(Color.appTertiaryText)
        }
        .padding(.vertical, 4)
    }

    private var icon: String {
        switch sighting.type {
        case .appleAirTag:     return "applelogo"
        case .tile:            return "circle.grid.2x2.fill"
        case .samsungSmartTag: return "square.stack.3d.up.fill"
        case .unknownBle:      return "questionmark.circle.fill"
        }
    }

    private var tint: Color {
        switch sighting.type {
        case .appleAirTag:     return Color.appAccent
        case .tile:            return Color.appBrightCyan
        case .samsungSmartTag: return Color.appAccent
        case .unknownBle:      return Color.appWarning
        }
    }
}
