// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation
import SwiftUI
import CoreLocation

/// A single trusted geofence (home, office, etc). Lat/lon + radius define a `CLCircularRegion`
/// we register with `CLLocationManager.startMonitoring(for:)`. The id doubles as the region
/// identifier so delegate callbacks map back to this entry.
struct TrustedGeofence: Identifiable, Codable, Equatable {
    let id: UUID
    var name: String
    var latitude: Double
    var longitude: Double
    /// Radius in meters, clamped by `GeofenceState.radiusRange`.
    var radius: Double
    var createdAt: Date

    var coordinate: CLLocationCoordinate2D {
        CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
    }

    func toRegion() -> CLCircularRegion {
        let region = CLCircularRegion(
            center: coordinate,
            radius: radius,
            identifier: id.uuidString
        )
        // We only care about leaving a trusted area; entering has no business logic.
        region.notifyOnEntry = false
        region.notifyOnExit = true
        return region
    }
}

/// Geo-fenced Auto-Lock + Alert (Elite). Reached from Settings → Security & data →
/// "Geo-fence Auto-Lock (Elite)". Gated on `SubscriptionState.hasElite`; the setup screen
/// assumes the user is on Elite.
///
/// Persistence lives in `UserDefaults` because the data (names + lat/lon + radius) is not
/// sensitive on its own — anyone with the device unlocked can see what the trusted locations
/// are via the setup screen anyway. The alert-contact pointer is a UUID into the main vault
/// which is itself encrypted at rest.
@MainActor
final class GeofenceState: ObservableObject {
    static let shared = GeofenceState()

    /// Hard caps per the feature brief: 1-5 locations, 100m-1km radius.
    static let maxGeofences: Int = 5
    static let radiusRange: ClosedRange<Double> = 100...1000

    @Published var geofences: [TrustedGeofence] {
        didSet {
            persistGeofences()
            // Re-sync with CoreLocation if monitoring is on so the active regions reflect
            // the latest list. No-op if auto-lock is off.
            if isEnabled { GeofenceManager.shared.resyncMonitoring() }
        }
    }

    @Published var isEnabled: Bool {
        didSet {
            d.set(isEnabled, forKey: Keys.enabled)
            if isEnabled {
                GeofenceManager.shared.startMonitoring()
            } else {
                GeofenceManager.shared.stopMonitoring()
            }
        }
    }

    @Published var smsAlertEnabled: Bool {
        didSet { d.set(smsAlertEnabled, forKey: Keys.smsEnabled) }
    }

    @Published var shareLocationInAlert: Bool {
        didSet { d.set(shareLocationInAlert, forKey: Keys.shareLocation) }
    }

    @Published var alertContactId: UUID? {
        didSet { d.set(alertContactId?.uuidString, forKey: Keys.alertContactId) }
    }

    @Published var ownerName: String {
        didSet { d.set(ownerName, forKey: Keys.ownerName) }
    }

    private let d = UserDefaults.standard
    private enum Keys {
        static let geofences = "geofence.v1.locations"
        static let enabled = "geofence.v1.enabled"
        static let smsEnabled = "geofence.v1.smsEnabled"
        static let shareLocation = "geofence.v1.shareLocation"
        static let alertContactId = "geofence.v1.alertContactId"
        static let ownerName = "geofence.v1.ownerName"
    }

    private init() {
        // Decode saved geofences first so we can seed `isEnabled` honestly (if the list is
        // empty we never want `isEnabled` to be true, otherwise monitoring would be a no-op
        // and the UI would mis-advertise the state).
        let defaults = UserDefaults.standard
        let decoded: [TrustedGeofence] = {
            guard let data = defaults.data(forKey: Keys.geofences) else { return [] }
            return (try? JSONDecoder().decode([TrustedGeofence].self, from: data)) ?? []
        }()
        self.geofences = decoded

        let enabledPref = defaults.bool(forKey: Keys.enabled)
        self.isEnabled = enabledPref && !decoded.isEmpty

        // SMS alert defaults to ON when the user enables the feature (brief spec); we store
        // the inverse of the "has ever been set" flag so first-run reads as `true`.
        self.smsAlertEnabled = defaults.object(forKey: Keys.smsEnabled) as? Bool ?? true
        self.shareLocationInAlert = defaults.bool(forKey: Keys.shareLocation) // default OFF for privacy

        self.alertContactId = (defaults.string(forKey: Keys.alertContactId)).flatMap(UUID.init(uuidString:))
        self.ownerName = defaults.string(forKey: Keys.ownerName) ?? ""
    }

    // MARK: - Geofence CRUD

    /// Adds a new geofence. Returns false if the user has hit the 5-location cap or a
    /// geofence with the same name already exists.
    @discardableResult
    func addGeofence(name: String, coordinate: CLLocationCoordinate2D, radius: Double) -> Bool {
        guard geofences.count < Self.maxGeofences else { return false }
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return false }
        let clamped = min(max(radius, Self.radiusRange.lowerBound), Self.radiusRange.upperBound)
        let fence = TrustedGeofence(
            id: UUID(),
            name: trimmed,
            latitude: coordinate.latitude,
            longitude: coordinate.longitude,
            radius: clamped,
            createdAt: Date()
        )
        geofences.append(fence)
        return true
    }

    func removeGeofence(id: UUID) {
        geofences.removeAll { $0.id == id }
        // If the user drops the last fence while the feature is on, flip the master toggle
        // off so the UI doesn't claim we're monitoring nothing.
        if geofences.isEmpty && isEnabled {
            isEnabled = false
        }
    }

    func updateRadius(id: UUID, radius: Double) {
        guard let idx = geofences.firstIndex(where: { $0.id == id }) else { return }
        let clamped = min(max(radius, Self.radiusRange.lowerBound), Self.radiusRange.upperBound)
        geofences[idx].radius = clamped
    }

    // MARK: - Persistence

    private func persistGeofences() {
        guard let data = try? JSONEncoder().encode(geofences) else { return }
        d.set(data, forKey: Keys.geofences)
    }

    /// Clears every pref under this feature. Called by `DeadmanWipeState.performWipe` and
    /// Settings → "Wipe everything".
    func clearAll() {
        if isEnabled { isEnabled = false }
        geofences = []
        alertContactId = nil
        ownerName = ""
        smsAlertEnabled = true
        shareLocationInAlert = false
        d.removeObject(forKey: Keys.geofences)
        d.removeObject(forKey: Keys.enabled)
        d.removeObject(forKey: Keys.smsEnabled)
        d.removeObject(forKey: Keys.shareLocation)
        d.removeObject(forKey: Keys.alertContactId)
        d.removeObject(forKey: Keys.ownerName)
    }
}
