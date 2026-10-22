// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation
import AVFoundation
import Contacts
import CoreLocation
import Photos
import UIKit

/// iOS App Permission Watchdog model.
///
/// iOS does NOT let apps enumerate other apps' installed permissions — Apple intentionally
/// scopes the sandbox so apps can't audit the device fleet (good for user privacy, bad for
/// our Android-style daily diff). The iOS version therefore takes a different shape:
///
///   1. We deep-link the user into Settings → Privacy & Security → App Privacy Report, which
///      is Apple's own equivalent on iOS 15.2+.
///   2. We expose a transparent audit of OnlyTrusted's OWN permissions — what we hold now,
///      what we don't ask for and why.
///
/// Everything stays on device. There are no snapshots to persist, so this store is
/// stateless; it just exposes the current permission state of OnlyTrusted itself.
struct PermissionWatchdogStore {

    /// A permission OnlyTrusted could request. `isRequested` is true for ones we actually
    /// ask the user for; false ones carry a "why we don't ask" explainer.
    struct AuditedPermission: Identifiable {
        let id: String
        let title: String
        let systemImage: String
        let isRequested: Bool
        let currentState: String
        let explainer: String
    }

    static func audit() -> [AuditedPermission] {
        [
            AuditedPermission(
                id: "faceid",
                title: "Face ID / Touch ID",
                systemImage: "faceid",
                isRequested: true,
                currentState: "Requested — used to unlock the app and the family vault",
                explainer: "The only biometric we ask for. We never upload anything to a server; " +
                    "the match happens inside the Secure Enclave on your device."
            ),
            AuditedPermission(
                id: "contacts",
                title: "Contacts",
                systemImage: "person.crop.circle.fill",
                isRequested: false,
                currentState: contactsState(),
                explainer: "We don't ask for Contacts. You pick trusted numbers manually inside " +
                    "OnlyTrusted — this way we never read your entire address book."
            ),
            AuditedPermission(
                id: "camera",
                title: "Camera",
                systemImage: "camera.fill",
                isRequested: false,
                currentState: cameraState(),
                explainer: "No camera use anywhere in the app. No scanning, no QR, no photos. " +
                    "If a future feature needs the camera, we'll ask you first."
            ),
            AuditedPermission(
                id: "microphone",
                title: "Microphone",
                systemImage: "mic.fill",
                isRequested: false,
                currentState: micState(),
                explainer: "We never listen. OnlyTrusted does not record audio in any mode, " +
                    "including the sensor-monitoring features."
            ),
            AuditedPermission(
                id: "location",
                title: "Location",
                systemImage: "location.fill",
                isRequested: false,
                currentState: locationState(),
                explainer: "No location use. We don't geofence, we don't tag events with " +
                    "coordinates, we don't phone home with your position."
            ),
            AuditedPermission(
                id: "photos",
                title: "Photos",
                systemImage: "photo.on.rectangle.angled",
                isRequested: false,
                currentState: photosState(),
                explainer: "We don't read your photo library. Exports write to iCloud Drive / " +
                    "Files via the share sheet; nothing is pulled from Photos."
            ),
            AuditedPermission(
                id: "sms-call",
                title: "Messages & Call history",
                systemImage: "message.fill",
                isRequested: false,
                currentState: "Not available to apps on iOS",
                explainer: "iOS does not let third-party apps read SMS or your call log at all. " +
                    "We wire into the Call Directory / Message Filter extensions for blocking — " +
                    "neither extension can see the content of your messages or calls."
            ),
            AuditedPermission(
                id: "tracking",
                title: "App Tracking Transparency",
                systemImage: "network.slash",
                isRequested: false,
                currentState: "Not requested",
                explainer: "OnlyTrusted does not track you across apps or websites and has no " +
                    "ads or analytics SDKs, so there is nothing to ask about here."
            )
        ]
    }

    // MARK: - System state helpers

    private static func contactsState() -> String {
        switch CNContactStore.authorizationStatus(for: .contacts) {
        case .notDetermined: return "Not asked"
        case .restricted:    return "Restricted"
        case .denied:        return "Denied (good — we don't need it)"
        case .authorized:    return "Authorized (we won't use it)"
        case .limited:       return "Limited (we won't use it)"
        @unknown default:    return "Unknown"
        }
    }

    private static func cameraState() -> String {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .notDetermined: return "Not asked"
        case .restricted:    return "Restricted"
        case .denied:        return "Denied (good — we don't need it)"
        case .authorized:    return "Authorized (we won't use it)"
        @unknown default:    return "Unknown"
        }
    }

    private static func micState() -> String {
        switch AVCaptureDevice.authorizationStatus(for: .audio) {
        case .notDetermined: return "Not asked"
        case .restricted:    return "Restricted"
        case .denied:        return "Denied (good — we don't need it)"
        case .authorized:    return "Authorized (we won't use it)"
        @unknown default:    return "Unknown"
        }
    }

    private static func locationState() -> String {
        let mgr = CLLocationManager()
        switch mgr.authorizationStatus {
        case .notDetermined:        return "Not asked"
        case .restricted:           return "Restricted"
        case .denied:               return "Denied (good — we don't need it)"
        case .authorizedAlways:     return "Authorized Always (we won't use it)"
        case .authorizedWhenInUse:  return "Authorized When In Use (we won't use it)"
        @unknown default:           return "Unknown"
        }
    }

    private static func photosState() -> String {
        switch PHPhotoLibrary.authorizationStatus(for: .readWrite) {
        case .notDetermined: return "Not asked"
        case .restricted:    return "Restricted"
        case .denied:        return "Denied (good — we don't need it)"
        case .authorized:    return "Authorized (we won't use it)"
        case .limited:       return "Limited (we won't use it)"
        @unknown default:    return "Unknown"
        }
    }

    // MARK: - Deep link into iOS App Privacy Report

    /// Opens Settings → Privacy & Security → App Privacy Report. Apple does not expose
    /// a direct URL to that pane; the best we can do is land in the app's own Settings
    /// pane and tell the user the path. If a future iOS exposes a deeper URL we can swap.
    static func openPrivacyReport() {
        if let url = URL(string: UIApplication.openSettingsURLString) {
            UIApplication.shared.open(url)
        }
    }
}
