// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation

/// Auto Privacy Shield — Phase 1 (iOS).
///
/// Honest scope: Apple does not let a consumer app toggle OTHER apps' camera or mic
/// access. iOS reserves that control to supervised MDM. So this registry only owns
/// OnlyTrusted's own camera + mic sessions. Any place in the app that opens an
/// `AVCaptureSession` / `AVAudioEngine` registers a `CaptureHandle` with a `stop`
/// callback; when the scene goes inactive/background (and in a few other emergency
/// cases) the registry calls `stop()` on every live handle so we tear capture down
/// deterministically. We never auto-restart on return to foreground — that would be
/// surprising and could silently re-enable mic/camera after the user walked away.
///
/// The dashboard (`PrivacyShieldDashboard`) reads `history` to show "stopped N sessions
/// at HH:MM" — concrete, user-visible evidence that the feature is doing real work.
@MainActor
final class CaptureRegistry: ObservableObject {

    enum Kind: String, Codable {
        case camera
        case microphone
    }

    /// What a registered feature promises: a stable `id`, a human label, the sensor kind,
    /// and a `stop` closure the registry calls on scene phase changes. `stop` must be
    /// idempotent — the registry may call it after the feature has already torn down.
    struct Handle: Identifiable, Hashable {
        let id: UUID
        let feature: String
        let kind: Kind
        let registeredAt: Date
        /// Not Hashable/Equatable — compared by `id` only.
        let stop: @MainActor () -> Void

        static func == (lhs: Handle, rhs: Handle) -> Bool { lhs.id == rhs.id }
        func hash(into hasher: inout Hasher) { hasher.combine(id) }

        init(feature: String, kind: Kind, stop: @escaping @MainActor () -> Void) {
            self.id = UUID()
            self.feature = feature
            self.kind = kind
            self.registeredAt = Date()
            self.stop = stop
        }
    }

    /// A past teardown — surfaced in the dashboard so the user can confirm the shield fired.
    struct Event: Identifiable, Codable {
        let id: UUID
        let timestamp: Date
        let feature: String
        let kind: Kind
        let reason: Reason

        enum Reason: String, Codable {
            case background          // scene went .background / .inactive
            case manualStopAll       // user tapped Stop recording now
            case featureUnregistered // feature finished its session normally
        }

        init(feature: String, kind: Kind, reason: Reason, timestamp: Date = Date()) {
            self.id = UUID()
            self.timestamp = timestamp
            self.feature = feature
            self.kind = kind
            self.reason = reason
        }
    }

    static let shared = CaptureRegistry()

    /// Currently-live capture handles. The dashboard snapshots `count` and
    /// `activeFeatures` to show the live state.
    @Published private(set) var active: [Handle] = []
    /// Most recent teardown events, newest first. Capped at 10 per the brief.
    @Published private(set) var history: [Event] = []

    private static let historyCap = 10

    private init() {}

    // MARK: - Feature API

    /// Register a live capture session. Returns the handle; the caller must retain it so
    /// it can call `unregister(_:)` when the session ends normally.
    @discardableResult
    func register(feature: String, kind: Kind, stop: @escaping @MainActor () -> Void) -> Handle {
        let handle = Handle(feature: feature, kind: kind, stop: stop)
        active.append(handle)
        return handle
    }

    /// Normal unregister path: a feature finished its session on its own. We log an
    /// event so the user can see the clean teardown; we do NOT call `stop` here (the
    /// feature already did).
    func unregister(_ handle: Handle) {
        guard let idx = active.firstIndex(where: { $0.id == handle.id }) else { return }
        active.remove(at: idx)
        append(event: Event(feature: handle.feature, kind: handle.kind, reason: .featureUnregistered))
    }

    // MARK: - Scene-phase / emergency teardown

    /// Called from `OnlyTrustedApp.scenePhase`. Walks every live handle, calls `stop()`,
    /// clears the live list, logs one event per torn-down handle. Honours the user's
    /// "Enable auto-stop" preference — if the user disabled auto-stop the registry
    /// becomes a no-op on background. Returns the number of sessions that were stopped.
    @discardableResult
    func stopAll(reason: Event.Reason = .background) -> Int {
        if reason == .background && !PrivacyShieldState.shared.autoStopEnabled {
            return 0
        }
        let snapshot = active
        active.removeAll()
        for h in snapshot {
            h.stop()
            append(event: Event(feature: h.feature, kind: h.kind, reason: reason))
        }
        return snapshot.count
    }

    private func append(event: Event) {
        history.insert(event, at: 0)
        if history.count > Self.historyCap {
            history = Array(history.prefix(Self.historyCap))
        }
    }
}
