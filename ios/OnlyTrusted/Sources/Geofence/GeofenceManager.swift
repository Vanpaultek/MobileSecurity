// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation
import SwiftUI
import CoreLocation
import UserNotifications

/// Elite tier Geo-fenced Auto-Lock + Alert.
///
/// Wraps `CLLocationManager` to monitor every trusted geofence the user has defined. On exit
/// we check whether *any* other trusted region is currently entered — i.e. is the user still
/// inside one of their trusted areas (home → office drive-through case). If not, the device
/// has truly left all trusted zones, so we:
///
///   1. Call `AppGate.panic()` through the shared singleton to auto-lock the app.
///   2. Post a local notification "Device left trusted area".
///   3. Present the SMS composer (iOS cannot send unattended SMS) pre-filled with the
///      alert text and the chosen trusted-contact's number, optionally including a Google
///      Maps URL to the current GPS fix.
///   4. Log a `GEOFENCE_EXIT` SecurityEvent.
///
/// # iOS Always-Authorization limitation
///
/// `startMonitoring(for: CLCircularRegion)` requires *Always* location authorization — the
/// stricter "While using the app" grant will not fire delegate callbacks when the app is in
/// the background, which defeats the entire purpose of the feature. We request Always at
/// setup time, but iOS splits this into a two-step prompt (first While-using, then an
/// Always upgrade prompt the next time location is used). If the user never upgrades to
/// Always, the Setup screen surfaces a warning banner.
@MainActor
final class GeofenceManager: NSObject, ObservableObject {
    static let shared = GeofenceManager()

    @Published private(set) var authorizationStatus: CLAuthorizationStatus
    @Published private(set) var currentLocation: CLLocation?
    @Published private(set) var lastExitAt: Date?
    @Published private(set) var lastRequestLocationError: String?

    private let locationManager: CLLocationManager
    private var pendingOneShotLocation: ((CLLocation?) -> Void)?

    override init() {
        self.locationManager = CLLocationManager()
        self.authorizationStatus = locationManager.authorizationStatus
        super.init()
        locationManager.delegate = self
        locationManager.allowsBackgroundLocationUpdates = false // we only need region events
        locationManager.pausesLocationUpdatesAutomatically = true
        locationManager.desiredAccuracy = kCLLocationAccuracyBest
    }

    /// True if the user granted "Always" — the only grant that lets region monitoring fire
    /// in the background. "When in use" is insufficient for this feature.
    var hasAlwaysAuthorization: Bool {
        authorizationStatus == .authorizedAlways
    }

    var hasAnyAuthorization: Bool {
        authorizationStatus == .authorizedAlways || authorizationStatus == .authorizedWhenInUse
    }

    // MARK: - Permission requests

    /// Call the first time the user interacts with the setup screen. iOS will promote this to
    /// an "Always" prompt the next time location is actually used (if the user picks
    /// "While using the app" first).
    func requestAlwaysAuthorization() {
        switch authorizationStatus {
        case .notDetermined:
            locationManager.requestWhenInUseAuthorization()
        case .authorizedWhenInUse:
            // Upgrade to Always — required for background region monitoring.
            locationManager.requestAlwaysAuthorization()
        default:
            break
        }
    }

    // MARK: - One-shot location fetch (used by "Add current location")

    /// Fetches a single GPS fix and hands it to the completion. The handler runs on the
    /// main actor. iOS can take a few seconds to produce a fix the first time, especially
    /// on simulator.
    func fetchCurrentLocation(completion: @escaping @MainActor (CLLocation?) -> Void) {
        guard hasAnyAuthorization else {
            lastRequestLocationError = "Location permission not granted."
            completion(nil)
            return
        }
        lastRequestLocationError = nil
        pendingOneShotLocation = { loc in
            Task { @MainActor in completion(loc) }
        }
        locationManager.requestLocation()
    }

    // MARK: - Monitoring lifecycle

    func startMonitoring() {
        guard hasAlwaysAuthorization else {
            // We still try `requestAlways` so iOS surfaces the prompt now if we're mid-flow.
            requestAlwaysAuthorization()
            return
        }
        resyncMonitoring()
    }

    func stopMonitoring() {
        for region in locationManager.monitoredRegions {
            locationManager.stopMonitoring(for: region)
        }
    }

    /// Pushes the latest `GeofenceState.geofences` to `CLLocationManager`. Called whenever
    /// the user adds/removes/edits a fence while monitoring is on.
    func resyncMonitoring() {
        guard hasAlwaysAuthorization else { return }
        // Remove regions that no longer exist in state.
        let keep = Set(GeofenceState.shared.geofences.map { $0.id.uuidString })
        for region in locationManager.monitoredRegions {
            if !keep.contains(region.identifier) {
                locationManager.stopMonitoring(for: region)
            }
        }
        // Register any new/updated regions.
        let existing = Set(locationManager.monitoredRegions.map { $0.identifier })
        for fence in GeofenceState.shared.geofences {
            if !existing.contains(fence.id.uuidString) {
                locationManager.startMonitoring(for: fence.toRegion())
            }
        }
    }

    // MARK: - Exit handling

    /// Called when `CLLocationManager` reports a region exit. Checks whether the device is
    /// still inside *any* trusted region before escalating — this handles the common case
    /// where the user drives from home to office and never actually leaves all trusted
    /// zones.
    private func handleRegionExit(regionIdentifier: String) {
        let state = GeofenceState.shared
        guard state.isEnabled, !state.geofences.isEmpty else { return }
        lastExitAt = Date()

        // If any *other* geofence currently contains our last known location, we're still
        // inside a trusted zone. `requestLocation()` here is best-effort: if we can't get a
        // fresh fix we rely on the last cached one.
        let fallbackLocation = currentLocation
        fetchCurrentLocation { [weak self] fix in
            guard let self else { return }
            let location = fix ?? fallbackLocation
            let stillInside = location.map { loc in
                state.geofences.contains { fence in
                    fence.id.uuidString != regionIdentifier &&
                        self.distance(from: loc.coordinate, to: fence.coordinate) <= fence.radius
                }
            } ?? false
            if !stillInside {
                self.triggerExitAlert(at: location)
            }
        }
    }

    private func triggerExitAlert(at location: CLLocation?) {
        let state = GeofenceState.shared

        // 1. Audit log — before we touch anything else so even if the lock surface eats the
        // user's taps we still have the trail.
        let detail: String = {
            if let location {
                let lat = String(format: "%.5f", location.coordinate.latitude)
                let lon = String(format: "%.5f", location.coordinate.longitude)
                return "exit all trusted zones @ \(lat),\(lon)"
            }
            return "exit all trusted zones (no fix)"
        }()
        EventStore.shared.append(type: SecurityEventRecord.Kind.geofenceExit, detail: detail)

        // 2. Local notification — surfaces an alert even if the user never opens the app
        // after the lock.
        postLocalNotification()

        // 3. SMS composer — iOS cannot send unattended SMS. We stash the pre-built body +
        // recipient on `GeofenceAlertCoordinator`, which the main app's RootView will pick
        // up and present once the user unlocks (or now if the app is foreground).
        if state.smsAlertEnabled, let contactId = state.alertContactId {
            let number = VaultStore.shared.realContactsForSetup()
                .first(where: { $0.id == contactId })?
                .normalisedNumbers.first
            if let number {
                let body = GeofenceSOS.body(
                    ownerName: state.ownerName,
                    at: Date(),
                    location: state.shareLocationInAlert ? location : nil
                )
                GeofenceAlertCoordinator.shared.present(recipient: number, body: body)
            }
        }

        // 4. Lock the app — do this last so the SMS prep above runs while the main scene is
        // still active.
        Task { @MainActor in
            AppLockBridge.shared.panic()
        }
    }

    private func postLocalNotification() {
        let center = UNUserNotificationCenter.current()
        center.requestAuthorization(options: [.alert, .sound]) { granted, _ in
            guard granted else { return }
            let content = UNMutableNotificationContent()
            content.title = "Device left trusted area"
            content.body = "OnlyTrusted locked the app and sent an alert because the device left every trusted location."
            content.sound = .default
            let req = UNNotificationRequest(
                identifier: "geofence.exit.\(UUID().uuidString)",
                content: content,
                trigger: nil
            )
            center.add(req, withCompletionHandler: nil)
        }
    }

    private func distance(from a: CLLocationCoordinate2D, to b: CLLocationCoordinate2D) -> Double {
        let locA = CLLocation(latitude: a.latitude, longitude: a.longitude)
        let locB = CLLocation(latitude: b.latitude, longitude: b.longitude)
        return locA.distance(from: locB)
    }
}

// MARK: - CLLocationManagerDelegate

extension GeofenceManager: CLLocationManagerDelegate {
    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        let status = manager.authorizationStatus
        Task { @MainActor in
            self.authorizationStatus = status
            // If the user just granted Always and the feature is enabled, pick up where we
            // left off.
            if status == .authorizedAlways, GeofenceState.shared.isEnabled {
                self.resyncMonitoring()
            }
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let newest = locations.last else { return }
        Task { @MainActor in
            self.currentLocation = newest
            if let cb = self.pendingOneShotLocation {
                self.pendingOneShotLocation = nil
                cb(newest)
            }
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        Task { @MainActor in
            self.lastRequestLocationError = error.localizedDescription
            if let cb = self.pendingOneShotLocation {
                self.pendingOneShotLocation = nil
                cb(nil)
            }
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didExitRegion region: CLRegion) {
        let identifier = region.identifier
        Task { @MainActor in
            self.handleRegionExit(regionIdentifier: identifier)
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, monitoringDidFailFor region: CLRegion?, withError error: Error) {
        // Nothing actionable — the user can toggle the feature off/on in Settings. We swallow
        // the error silently so the UI stays clean.
    }
}

/// Shared message body builder. Kept separate so the Settings "Preview SMS" button and the
/// real exit flow produce identical text.
enum GeofenceSOS {
    static func body(ownerName: String, at date: Date = Date(), location: CLLocation? = nil) -> String {
        let name = ownerName.isEmpty ? "an OnlyTrusted user" : ownerName
        let f = DateFormatter()
        f.dateStyle = .short
        f.timeStyle = .short
        let stamp = f.string(from: date)
        var body = "\u{26A0}\u{FE0F} OnlyTrusted alert: \(name)'s device left all trusted locations at \(stamp)."
        if let location {
            let lat = location.coordinate.latitude
            let lon = location.coordinate.longitude
            body += " Location: https://maps.google.com/?q=\(lat),\(lon)"
        }
        return body
    }
}

/// Lets the Geofence manager hand a prepared SMS to the SwiftUI layer without taking a direct
/// SwiftUI dependency. The RootView watches `pendingAlert`; when non-nil it presents the
/// `GeofenceSMSComposer`.
@MainActor
final class GeofenceAlertCoordinator: ObservableObject {
    static let shared = GeofenceAlertCoordinator()

    struct PendingAlert: Identifiable, Equatable {
        let id = UUID()
        let recipient: String
        let body: String
    }

    @Published var pendingAlert: PendingAlert?

    private init() {}

    func present(recipient: String, body: String) {
        pendingAlert = PendingAlert(recipient: recipient, body: body)
    }

    func dismiss() {
        pendingAlert = nil
    }
}

/// Thin indirection that lets non-SwiftUI code (the `CLLocationManager` delegate) trigger a
/// panic lock without reaching into the `@EnvironmentObject` graph. The app registers the
/// live `AppGate` here on launch.
@MainActor
final class AppLockBridge {
    static let shared = AppLockBridge()

    private weak var gate: AppGate?
    private init() {}

    func register(gate: AppGate) {
        self.gate = gate
    }

    func panic() {
        gate?.panic()
    }
}
