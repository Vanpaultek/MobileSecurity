// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation
import SwiftUI

/// Elite-tier (`SubscriptionState.hasElite`) Rogue Wi-Fi + IMSI-catcher scanner.
///
/// Observes the device's current cellular + Wi-Fi state and raises an alert when a
/// red-flag heuristic fires. All detection is 100% on-device — no network calls, no
/// server-side analysis. Alerts are stored in `UserDefaults` so the Elite screen can
/// show a short history across launches.
///
/// Pairs with:
///   - `RogueNetworkMonitor` for the actual CoreTelephony / NetworkExtension probes.
///   - `RogueNetworkScreen`  for the Elite setup UI with toggles + recent-alerts list.
@MainActor
final class RogueNetworkState: ObservableObject {
    static let shared = RogueNetworkState()

    enum AlertKind: String, Codable {
        case forcedDowngrade
        case openWifiOnBrandSsid
        case ssidCollision
        case missingCarrier
    }

    struct Alert: Codable, Identifiable, Equatable {
        var id: String { "\(timestamp.timeIntervalSince1970)-\(kind.rawValue)" }
        let kind: AlertKind
        let title: String
        let detail: String
        let timestamp: Date
    }

    @Published var cellularMonitoringEnabled: Bool {
        didSet {
            UserDefaults.standard.set(cellularMonitoringEnabled, forKey: Self.cellularKey)
        }
    }

    @Published var wifiMonitoringEnabled: Bool {
        didSet {
            UserDefaults.standard.set(wifiMonitoringEnabled, forKey: Self.wifiKey)
        }
    }

    @Published private(set) var alerts: [Alert] = []

    private static let cellularKey = "roguenetwork.cell.v1"
    private static let wifiKey = "roguenetwork.wifi.v1"
    private static let alertsKey = "roguenetwork.alerts.v1"
    /// Cap persisted alerts so the pref blob stays bounded.
    private static let maxAlerts = 50

    private init() {
        // Default both toggles ON so the Elite flag actually watches once the user
        // visits the screen. The monitor only starts after the user taps "Done".
        let defaults = UserDefaults.standard
        if defaults.object(forKey: Self.cellularKey) == nil {
            defaults.set(true, forKey: Self.cellularKey)
        }
        if defaults.object(forKey: Self.wifiKey) == nil {
            defaults.set(true, forKey: Self.wifiKey)
        }
        cellularMonitoringEnabled = defaults.bool(forKey: Self.cellularKey)
        wifiMonitoringEnabled = defaults.bool(forKey: Self.wifiKey)
        alerts = Self.loadAlerts()
    }

    func recordAlert(_ alert: Alert) {
        // De-dup against the most recent alert of the same kind + title fired within
        // the last 10 minutes so a steady state doesn't spam the list.
        if let last = alerts.first,
           last.kind == alert.kind,
           last.title == alert.title,
           Date().timeIntervalSince(last.timestamp) < 600 {
            return
        }
        var next = alerts
        next.insert(alert, at: 0)
        if next.count > Self.maxAlerts {
            next = Array(next.prefix(Self.maxAlerts))
        }
        alerts = next
        persistAlerts()
    }

    func clearAlerts() {
        alerts = []
        persistAlerts()
    }

    private func persistAlerts() {
        guard let data = try? JSONEncoder().encode(alerts) else { return }
        UserDefaults.standard.set(data, forKey: Self.alertsKey)
    }

    private static func loadAlerts() -> [Alert] {
        guard let data = UserDefaults.standard.data(forKey: alertsKey),
              let decoded = try? JSONDecoder().decode([Alert].self, from: data) else {
            return []
        }
        return decoded
    }
}
