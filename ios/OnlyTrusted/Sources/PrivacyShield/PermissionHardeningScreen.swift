// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import SwiftUI
import AVFoundation
import Photos
import CoreLocation
import UIKit

/// Permission Hardening Assistant.
///
/// Lists OnlyTrusted's current authorization status for the four sensors we might touch:
/// camera, microphone, photos, location. Each row has a one-tap "Open iOS Settings"
/// button so the user can flip the status without hunting through nested menus. At the
/// bottom, an "Audit other apps" card nudges toward Apple's own App Privacy Report —
/// we deliberately do NOT claim to enumerate other apps' permissions (iOS doesn't let us).
struct PermissionHardeningScreen: View {
    @State private var rows: [Row] = []
    @StateObject private var locationBridge = LocationStatusBridge()

    var body: some View {
        ScrollView(.vertical, showsIndicators: false) {
            VStack(alignment: .leading, spacing: 20) {
                heroCard
                permissionsCard
                auditOtherAppsCard
                limitationNote
            }
            .padding(.horizontal, 18)
            .padding(.top, 10)
            .padding(.bottom, 32)
        }
        .appBackdrop()
        .navigationTitle("Hardening Assistant")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { refresh() }
        // Refresh when the user returns from iOS Settings so toggles update in-place.
        .onReceive(NotificationCenter.default.publisher(for: UIApplication.willEnterForegroundNotification)) { _ in
            refresh()
        }
    }

    private func refresh() {
        rows = [
            Row(
                kind: .camera,
                status: Self.cameraStatus(),
                title: "Camera",
                systemImage: "camera.fill",
                blurb: "FaceLock, Smart Link Guard QR scanner, and future features that need the camera will fail silently without this."
            ),
            Row(
                kind: .microphone,
                status: Self.microphoneStatus(),
                title: "Microphone",
                systemImage: "mic.fill",
                blurb: "Voice Biometric enrollment and verification need this; nothing else does."
            ),
            Row(
                kind: .photos,
                status: Self.photosStatus(),
                title: "Photos",
                systemImage: "photo.fill.on.rectangle.fill",
                blurb: "Only required if you load/save a Steganographic Vault decoy image."
            ),
            Row(
                kind: .location,
                status: locationBridge.statusString,
                title: "Location",
                systemImage: "location.fill",
                blurb: "Geo-fence Auto-Lock needs 'Always' authorization to fire exit events in the background."
            )
        ]
    }

    // MARK: - Cards

    private var heroCard: some View {
        PremiumCard {
            HStack(spacing: 14) {
                ZStack {
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(Color.appAccent.opacity(0.18))
                    Image(systemName: "slider.horizontal.3")
                        .font(.system(size: 24, weight: .semibold))
                        .foregroundStyle(Color.appAccent)
                }
                .frame(width: 48, height: 48)
                .decorativeAccessibility()
                VStack(alignment: .leading, spacing: 4) {
                    Text("Your own permission footprint")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Color.appPrimaryText)
                    Text("What OnlyTrusted is currently allowed to touch, plus a one-tap jump into Settings for each.")
                        .font(.caption)
                        .foregroundStyle(Color.appSecondaryText)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
    }

    private var permissionsCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 12) {
                CardHeader(icon: "checkmark.shield.fill", title: "OnlyTrusted's permissions")
                ForEach(Array(rows.enumerated()), id: \.element.id) { pair in
                    if pair.offset > 0 { SettingsDivider() }
                    permissionRow(pair.element)
                }
            }
        }
    }

    private func permissionRow(_ row: Row) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .top, spacing: 12) {
                ZStack {
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .fill(statusTint(for: row.status).opacity(0.18))
                    Image(systemName: row.systemImage)
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundStyle(statusTint(for: row.status))
                }
                .frame(width: 36, height: 36)
                .decorativeAccessibility()
                VStack(alignment: .leading, spacing: 2) {
                    HStack {
                        Text(row.title)
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(Color.appPrimaryText)
                        Spacer()
                        Text(row.status)
                            .font(.caption2.weight(.semibold))
                            .padding(.horizontal, 8)
                            .padding(.vertical, 3)
                            .background(statusTint(for: row.status).opacity(0.18), in: Capsule())
                            .foregroundStyle(statusTint(for: row.status))
                    }
                    Text(row.blurb)
                        .font(.caption)
                        .foregroundStyle(Color.appSecondaryText)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            Button {
                if let url = URL(string: UIApplication.openSettingsURLString) {
                    UIApplication.shared.open(url)
                }
            } label: {
                HStack {
                    Image(systemName: "arrow.up.right.square.fill")
                    Text("Open iOS Settings → OnlyTrusted")
                        .font(.footnote.weight(.semibold))
                }
                .frame(maxWidth: .infinity, minHeight: 36)
            }
            .buttonStyle(.bordered)
            .tint(Color.appAccent)
        }
        .padding(.vertical, 4)
    }

    private var auditOtherAppsCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 12) {
                CardHeader(icon: "chart.bar.doc.horizontal.fill", title: "Audit other apps")
                Text("Apple doesn't let third-party apps see other apps' permission grants. The right place to check is Apple's App Privacy Report, which lists every sensor + domain touched in the past 7 days.")
                    .font(.footnote)
                    .foregroundStyle(Color.appSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)
                Button {
                    if let url = URL(string: UIApplication.openSettingsURLString) {
                        UIApplication.shared.open(url)
                    }
                } label: {
                    HStack {
                        Image(systemName: "arrow.up.right.square.fill")
                        Text("Open iOS Settings")
                            .font(.body.weight(.semibold))
                    }
                    .frame(maxWidth: .infinity, minHeight: 44)
                }
                .buttonStyle(.borderedProminent)
                .tint(Color.appAccent)
                Text("Then: Privacy & Security → App Privacy Report. Turn on Record App Activity if it's your first time.")
                    .font(.caption)
                    .foregroundStyle(Color.appTertiaryText)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    private var limitationNote: some View {
        Text("The deep-link above lands on OnlyTrusted's own Settings page. From there iOS lets you navigate to Privacy & Security without leaving the app. There is no `URL(string:)` form that deep-links straight into App Privacy Report — Apple reserves that path.")
            .font(.caption)
            .foregroundStyle(Color.appTertiaryText)
            .fixedSize(horizontal: false, vertical: true)
            .padding(.horizontal, 4)
    }

    // MARK: - Status helpers

    private struct Row: Identifiable {
        let id = UUID()
        let kind: Kind
        let status: String
        let title: String
        let systemImage: String
        let blurb: String

        enum Kind { case camera, microphone, photos, location }
    }

    private static func cameraStatus() -> String {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized: return "Granted"
        case .denied: return "Denied"
        case .restricted: return "Restricted"
        case .notDetermined: return "Not asked"
        @unknown default: return "Unknown"
        }
    }

    private static func microphoneStatus() -> String {
        switch AVAudioApplication.shared.recordPermission {
        case .granted: return "Granted"
        case .denied: return "Denied"
        case .undetermined: return "Not asked"
        @unknown default: return "Unknown"
        }
    }

    private static func photosStatus() -> String {
        switch PHPhotoLibrary.authorizationStatus(for: .readWrite) {
        case .authorized: return "Granted"
        case .limited: return "Limited"
        case .denied: return "Denied"
        case .restricted: return "Restricted"
        case .notDetermined: return "Not asked"
        @unknown default: return "Unknown"
        }
    }

    private func statusTint(for status: String) -> Color {
        switch status {
        case "Granted", "Always", "Limited", "When in use":
            return Color.appSuccess
        case "Denied", "Restricted":
            return Color.appDanger
        case "Not asked", "Unknown":
            return Color.appWarning
        default:
            return Color.appAccent
        }
    }
}

/// CoreLocation authorization is callback-based rather than snapshot-only (the manager
/// has to exist for the status to be meaningful on first-run). We wrap it in a tiny
/// observable so the view re-renders when the user returns from Settings.
@MainActor
private final class LocationStatusBridge: NSObject, ObservableObject, CLLocationManagerDelegate {
    private let manager = CLLocationManager()
    @Published var statusString: String = "Unknown"

    override init() {
        super.init()
        manager.delegate = self
        update(status: manager.authorizationStatus)
    }

    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        let status = manager.authorizationStatus
        Task { @MainActor in self.update(status: status) }
    }

    private func update(status: CLAuthorizationStatus) {
        switch status {
        case .authorizedAlways: statusString = "Always"
        case .authorizedWhenInUse: statusString = "When in use"
        case .denied: statusString = "Denied"
        case .restricted: statusString = "Restricted"
        case .notDetermined: statusString = "Not asked"
        @unknown default: statusString = "Unknown"
        }
    }
}
