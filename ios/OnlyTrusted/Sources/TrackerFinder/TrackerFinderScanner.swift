// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation
import CoreBluetooth
import UserNotifications

/// One-shot BLE scan driver for Tracker Finder (Pro).
///
/// Why one-shot?
///   iOS does not let third-party apps keep an open BLE scan running from the lock screen
///   for hours — the system aggressively suspends background scanners and strips mfr data
///   from advertisements once backgrounded. Apple's own Find My network is the only tool
///   with that permission, and since iOS 14.5 Apple itself auto-alerts the owner of any
///   AirTag separated from its owner that travels with you. Our scanner is therefore
///   additive: we scan while the Tracker Finder screen is open OR for 30 seconds after
///   the user taps "Scan now". Both modes are foreground.
///
/// The scanner classifies each discovered peripheral per the brief's rules:
///   • Apple AirTag  — Apple mfr data (0x004C), payload 25-31 bytes (Offline Finding packet)
///   • Tile          — service UUID 0xFEED or Tile mfr data (0x00B9)
///   • Samsung tag   — Samsung mfr data (0x0075) + known SmartTag service UUIDs
///   • Unknown       — otherwise, no name, becomes a "following" candidate per the store rule
///
/// Classifications are persisted to `TrackerFinderStore` with per-install hashed IDs.
@MainActor
final class TrackerFinderScanner: NSObject, ObservableObject {

    /// Current state of the scanner — drives the UI (`.idle`, `.scanning`, `.stopped`).
    enum State: Equatable {
        case idle
        case bluetoothUnavailable(String)
        case scanning(remainingSeconds: Int)
        case stopped(detections: Int)
    }

    static let shared = TrackerFinderScanner()

    @Published private(set) var state: State = .idle
    /// All classifications seen in the current session, newest first.
    @Published private(set) var liveSightings: [TrackerFinderStore.Sighting] = []
    /// Trackers currently classified as "following" (updated after each session).
    @Published private(set) var followers: [TrackerFinderStore.FollowerTracker] = []

    private var central: CBCentralManager?
    private var sessionId = UUID()
    private var countdownTimer: Timer?
    private var pendingStart: Bool = false
    private var detectionCount: Int = 0

    override private init() {
        super.init()
        // Delay central init until first `startOneShot(...)` call so the system OS prompt
        // for Bluetooth is tied to a user action, not app launch.
    }

    // MARK: - Public API

    /// Kick off a 30-second foreground scan. Idempotent — calling again while already
    /// scanning restarts the countdown.
    func startOneShot(duration: Int = 30) {
        liveSightings = []
        detectionCount = 0
        sessionId = UUID()
        countdownTimer?.invalidate()

        if central == nil {
            central = CBCentralManager(delegate: self, queue: .main, options: [
                CBCentralManagerOptionShowPowerAlertKey: true as NSNumber
            ])
            pendingStart = true
            return
        }
        beginScan(duration: duration)
    }

    func stop() {
        countdownTimer?.invalidate()
        countdownTimer = nil
        central?.stopScan()
        state = .stopped(detections: detectionCount)
        followers = TrackerFinderStore.followingTrackers()
    }

    // MARK: - Internal

    private var remainingSeconds: Int = 0

    private func beginScan(duration: Int) {
        guard let central, central.state == .poweredOn else {
            pendingStart = true
            return
        }
        // Service UUIDs: Tile (0xFEED). Apple + Samsung mfr data arrives via scan
        // response regardless of filter when scanning with `nil` service UUIDs.
        central.scanForPeripherals(withServices: nil, options: [
            CBCentralManagerScanOptionAllowDuplicatesKey: true as NSNumber
        ])
        remainingSeconds = duration
        state = .scanning(remainingSeconds: duration)
        countdownTimer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { [weak self] _ in
            Task { @MainActor in
                guard let self else { return }
                self.remainingSeconds -= 1
                if self.remainingSeconds <= 0 {
                    self.stop()
                } else {
                    self.state = .scanning(remainingSeconds: self.remainingSeconds)
                }
            }
        }
    }

    // MARK: - Classification

    private func classify(mfr: Data?,
                          serviceUUIDs: [String],
                          localName: String?) -> TrackerFinderStore.TrackerType? {
        // Manufacturer-data heuristic: first 2 LE bytes are the Bluetooth SIG company ID.
        let companyId: UInt16? = {
            guard let mfr, mfr.count >= 2 else { return nil }
            return UInt16(mfr[0]) | (UInt16(mfr[1]) << 8)
        }()

        // Apple (0x004C) — AirTag / Offline Finding packets have length 25..31 bytes.
        if companyId == 0x004C, let mfr, (25...31).contains(mfr.count) {
            return .appleAirTag
        }

        // Tile — service UUID 0xFEED or Tile mfr ID 0x00B9.
        if serviceUUIDs.contains(where: { $0.hasPrefix("FEED") }) || companyId == 0x00B9 {
            return .tile
        }

        // Samsung — mfr ID 0x0075; the SmartTag service UUIDs vary but mfr data alone
        // is a strong signal, so we take it.
        if companyId == 0x0075 {
            return .samsungSmartTag
        }

        // Unknown BLE device — only flag if it has no human-readable name. If it has one
        // we skip it (headphones, speakers, laptops).
        if (localName ?? "").isEmpty {
            return .unknownBle
        }
        return nil
    }

    private func maybePostFollowerAlert() {
        let followers = TrackerFinderStore.followingTrackers()
        self.followers = followers
        guard let newest = followers.first else { return }

        let defaults = UserDefaults.standard
        let lastAlertKey = "tracker_finder.last_alert_for"
        let lastAlertAtKey = "tracker_finder.last_alert_at"
        let lastHash = defaults.string(forKey: lastAlertKey)
        let lastAt = defaults.double(forKey: lastAlertAtKey)
        // De-dupe: don't nag more than once an hour for the same tracker.
        if lastHash == newest.idHash,
           Date().timeIntervalSince1970 - lastAt < 3600 { return }

        defaults.set(newest.idHash, forKey: lastAlertKey)
        defaults.set(Date().timeIntervalSince1970, forKey: lastAlertAtKey)

        let center = UNUserNotificationCenter.current()
        center.requestAuthorization(options: [.alert, .sound]) { granted, _ in
            guard granted else { return }
            let content = UNMutableNotificationContent()
            content.title = "Possible tracker following you"
            content.body = "A \(newest.type.displayName) has reappeared across \(newest.sessionCount) scans. Tap to review."
            content.sound = .default
            let request = UNNotificationRequest(
                identifier: "tracker-finder-\(newest.idHash)",
                content: content,
                trigger: nil
            )
            center.add(request)
        }
    }
}

// MARK: - CBCentralManagerDelegate

extension TrackerFinderScanner: @preconcurrency CBCentralManagerDelegate {
    // The CBCentralManager we create is initialised with `queue: .main`, so delegate
    // callbacks already arrive on the main thread. We annotate the delegate hop with
    // @MainActor and sidestep the Swift 6 sending diagnostics by extracting value-type
    // snapshots before any @MainActor work.

    @MainActor
    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        let btState = central.state
        switch btState {
        case .poweredOn:
            if pendingStart {
                pendingStart = false
                beginScan(duration: 30)
            }
        case .poweredOff:
            state = .bluetoothUnavailable("Bluetooth is off. Turn it on in Control Center to scan.")
        case .unauthorized:
            state = .bluetoothUnavailable("Allow OnlyTrusted to use Bluetooth in iOS Settings → OnlyTrusted → Bluetooth.")
        case .unsupported:
            state = .bluetoothUnavailable("This device doesn't support Bluetooth LE scanning.")
        case .resetting, .unknown:
            state = .bluetoothUnavailable("Bluetooth is resetting — try again in a moment.")
        @unknown default:
            state = .bluetoothUnavailable("Bluetooth is unavailable.")
        }
    }

    @MainActor
    func centralManager(_ central: CBCentralManager,
                        didDiscover peripheral: CBPeripheral,
                        advertisementData: [String: Any],
                        rssi RSSI: NSNumber) {
        // Snapshot the advertisement data by extracting the only three fields we use.
        // This keeps us well inside Sendable bounds even on strict concurrency.
        let localName = (advertisementData[CBAdvertisementDataLocalNameKey] as? String)
            ?? peripheral.name
        let mfr = advertisementData[CBAdvertisementDataManufacturerDataKey] as? Data
        let serviceUUIDStrings: [String] = (advertisementData[CBAdvertisementDataServiceUUIDsKey]
            as? [CBUUID])?.map { $0.uuidString.uppercased() } ?? []
        let identifier = peripheral.identifier.uuidString
        let rssi = RSSI.intValue

        guard let type = classify(
            mfr: mfr,
            serviceUUIDs: serviceUUIDStrings,
            localName: localName
        ) else {
            return
        }

        let idHash = TrackerFinderStore.hashIdentifier(identifier)
        let sighting = TrackerFinderStore.Sighting(
            idHash: idHash,
            type: type,
            rssi: rssi,
            timestamp: Date(),
            sessionId: sessionId
        )
        TrackerFinderStore.record(sighting)
        liveSightings.insert(sighting, at: 0)
        if liveSightings.count > 50 {
            liveSightings = Array(liveSightings.prefix(50))
        }
        detectionCount += 1
        maybePostFollowerAlert()
    }
}
