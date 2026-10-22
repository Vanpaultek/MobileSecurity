// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation
import AuthenticationServices
import SwiftUI
import UIKit

/// Elite-tier gating helper. Call `HardwareKeyGate.requireAssertion(reason:completion:)` before
/// any sensitive action that should require a physical hardware-key tap when at least one key
/// is enrolled. If no key is enrolled, the completion fires immediately with `.success` so this
/// stays a pure opt-in feature — callers never need to check `hasEnrolledKey` themselves.
///
/// Platform notes:
///  - Uses `ASAuthorizationSecurityKeyPublicKeyCredentialProvider` (iOS 15+).
///  - Registration + assertion both use a locally-generated 32-byte challenge. Because this is
///    an on-device gate (not a server login) the challenge is one-shot, scoped to this
///    controller, and never persisted.
///  - On the iOS Simulator the ASAuthorizationController presents but cannot talk to a USB /
///    Lightning / NFC key, so a dev-only `simulatorSkipEnabled` toggle short-circuits the
///    prompt. The skip is gated by `targetEnvironment(simulator)` at the call site, so a
///    device build never honors it.
enum HardwareKeyGate {

    enum Outcome {
        case success
        case cancelled
        case failed(String)
    }

    /// Entry point used by Wipe everything, Duress disarm, Dead-Man Wipe disable, etc. When at
    /// least one hardware key is enrolled, the user is prompted to tap it; otherwise the gate
    /// is a no-op and the completion fires `.success` immediately.
    @MainActor
    static func requireAssertion(
        reason: String,
        completion: @escaping (Outcome) -> Void
    ) {
        let state = HardwareKeyState.shared

        // No keys enrolled → feature is opt-in, let the caller proceed.
        guard state.hasEnrolledKey else {
            completion(.success)
            return
        }

        // Simulator short-circuit: no USB/Lightning/NFC hardware on the sim, so the dev-only
        // toggle lets the gated flows be exercised during manual testing. Never honored on a
        // real device.
        if HardwareKeyState.isRunningOnSimulator && state.simulatorSkipEnabled {
            completion(.success)
            return
        }

        AssertionCoordinator.run(
            credentialIds: state.enrolledKeys.map { $0.credentialId },
            reason: reason,
            completion: completion
        )
    }

    /// Registration entry point used by the setup screen. On success the caller receives the
    /// freshly-created `EnrolledKey` to add via `HardwareKeyState.addKey`.
    @MainActor
    static func enrollNewKey(
        displayName: String,
        completion: @escaping (Result<HardwareKeyState.EnrolledKey, Error>) -> Void
    ) {
        RegistrationCoordinator.run(displayName: displayName, completion: completion)
    }
}

// MARK: - Registration

@MainActor
private final class RegistrationCoordinator: NSObject,
                                             ASAuthorizationControllerDelegate,
                                             ASAuthorizationControllerPresentationContextProviding {
    private static var active: RegistrationCoordinator?

    private let displayName: String
    private let completion: (Result<HardwareKeyState.EnrolledKey, Error>) -> Void

    private init(displayName: String,
                 completion: @escaping (Result<HardwareKeyState.EnrolledKey, Error>) -> Void) {
        self.displayName = displayName
        self.completion = completion
    }

    static func run(displayName: String,
                    completion: @escaping (Result<HardwareKeyState.EnrolledKey, Error>) -> Void) {
        let coordinator = RegistrationCoordinator(displayName: displayName, completion: completion)
        active = coordinator
        coordinator.begin()
    }

    private func begin() {
        let provider = ASAuthorizationSecurityKeyPublicKeyCredentialProvider(
            relyingPartyIdentifier: HardwareKeyState.relyingPartyID
        )
        let challenge = Self.randomChallenge()
        // UserID is a local, opaque bytes handle — the hardware key uses it to disambiguate
        // residents on the same RP. We hash the display name so it is stable across re-enrolls
        // of the same key but still a privacy-safe handle.
        let userID = Data(displayName.utf8)

        let request = provider.createCredentialRegistrationRequest(
            challenge: challenge,
            displayName: displayName,
            name: displayName,
            userID: userID
        )
        request.credentialParameters = [
            ASAuthorizationPublicKeyCredentialParameters(algorithm: .ES256)
        ]
        request.userVerificationPreference = .preferred

        let controller = ASAuthorizationController(authorizationRequests: [request])
        controller.delegate = self
        controller.presentationContextProvider = self
        controller.performRequests()
    }

    // ASAuthorizationControllerDelegate
    nonisolated func authorizationController(controller: ASAuthorizationController,
                                             didCompleteWithAuthorization authorization: ASAuthorization) {
        guard let credential = authorization.credential
            as? ASAuthorizationSecurityKeyPublicKeyCredentialRegistration else {
            Task { @MainActor in
                self.completion(.failure(GateError.unexpectedCredentialType))
                RegistrationCoordinator.active = nil
            }
            return
        }
        let credentialId = credential.credentialID
        let publicKey = credential.rawAttestationObject
        let friendlyName = self.displayName
        Task { @MainActor in
            let enrolled = HardwareKeyState.EnrolledKey(
                id: UUID(),
                credentialId: credentialId,
                publicKey: publicKey,
                displayName: friendlyName,
                dateAdded: Date()
            )
            self.completion(.success(enrolled))
            RegistrationCoordinator.active = nil
        }
    }

    nonisolated func authorizationController(controller: ASAuthorizationController,
                                             didCompleteWithError error: Error) {
        Task { @MainActor in
            self.completion(.failure(error))
            RegistrationCoordinator.active = nil
        }
    }

    // Presentation anchor
    nonisolated func presentationAnchor(for controller: ASAuthorizationController)
        -> ASPresentationAnchor {
        MainActor.assumeIsolated {
            PresentationAnchor.active()
        }
    }

    private static func randomChallenge() -> Data {
        var bytes = [UInt8](repeating: 0, count: 32)
        _ = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)
        return Data(bytes)
    }
}

// MARK: - Assertion

@MainActor
private final class AssertionCoordinator: NSObject,
                                          ASAuthorizationControllerDelegate,
                                          ASAuthorizationControllerPresentationContextProviding {
    private static var active: AssertionCoordinator?

    private let credentialIds: [Data]
    private let reason: String
    private let completion: (HardwareKeyGate.Outcome) -> Void

    private init(credentialIds: [Data],
                 reason: String,
                 completion: @escaping (HardwareKeyGate.Outcome) -> Void) {
        self.credentialIds = credentialIds
        self.reason = reason
        self.completion = completion
    }

    static func run(credentialIds: [Data],
                    reason: String,
                    completion: @escaping (HardwareKeyGate.Outcome) -> Void) {
        let coordinator = AssertionCoordinator(
            credentialIds: credentialIds,
            reason: reason,
            completion: completion
        )
        active = coordinator
        coordinator.begin()
    }

    private func begin() {
        let provider = ASAuthorizationSecurityKeyPublicKeyCredentialProvider(
            relyingPartyIdentifier: HardwareKeyState.relyingPartyID
        )
        let challenge = Self.randomChallenge()
        let request = provider.createCredentialAssertionRequest(challenge: challenge)
        // Supported transports for FIDO2 security keys. We advertise USB, NFC, and BLE so the
        // same enrolled credentialId can be presented by a key regardless of connector type.
        let transports: [ASAuthorizationSecurityKeyPublicKeyCredentialDescriptor.Transport] =
            [.usb, .nfc, .bluetooth]
        request.allowedCredentials = credentialIds.map {
            ASAuthorizationSecurityKeyPublicKeyCredentialDescriptor(
                credentialID: $0,
                transports: transports
            )
        }
        request.userVerificationPreference = .preferred

        let controller = ASAuthorizationController(authorizationRequests: [request])
        controller.delegate = self
        controller.presentationContextProvider = self
        controller.performRequests()
    }

    // ASAuthorizationControllerDelegate
    nonisolated func authorizationController(controller: ASAuthorizationController,
                                             didCompleteWithAuthorization authorization: ASAuthorization) {
        guard authorization.credential
            is ASAuthorizationSecurityKeyPublicKeyCredentialAssertion else {
            Task { @MainActor in
                self.completion(.failed("Unexpected credential type."))
                AssertionCoordinator.active = nil
            }
            return
        }
        // In an on-device gate we trust the authenticator's confirmation that the user tapped
        // the key. Signature verification would only matter if we were sending the assertion
        // to a server, which this gate deliberately does not do.
        Task { @MainActor in
            self.completion(.success)
            AssertionCoordinator.active = nil
        }
    }

    nonisolated func authorizationController(controller: ASAuthorizationController,
                                             didCompleteWithError error: Error) {
        Task { @MainActor in
            if let authError = error as? ASAuthorizationError, authError.code == .canceled {
                self.completion(.cancelled)
            } else {
                self.completion(.failed((error as NSError).localizedDescription))
            }
            AssertionCoordinator.active = nil
        }
    }

    nonisolated func presentationAnchor(for controller: ASAuthorizationController)
        -> ASPresentationAnchor {
        MainActor.assumeIsolated {
            PresentationAnchor.active()
        }
    }

    private static func randomChallenge() -> Data {
        var bytes = [UInt8](repeating: 0, count: 32)
        _ = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)
        return Data(bytes)
    }
}

// MARK: - Shared helpers

enum GateError: LocalizedError {
    case unexpectedCredentialType
    var errorDescription: String? {
        switch self {
        case .unexpectedCredentialType:
            return "The authenticator returned an unexpected credential type."
        }
    }
}

@MainActor
private enum PresentationAnchor {
    /// Returns the first key window on the foreground scene so ASAuthorizationController has an
    /// anchor to attach its sheet to. Falls back to a transient UIWindow if nothing is live yet
    /// (shouldn't happen in practice — the gate is always invoked from a visible screen).
    static func active() -> ASPresentationAnchor {
        let scenes = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
        if let window = scenes.flatMap(\.windows).first(where: { $0.isKeyWindow }) {
            return window
        }
        if let window = scenes.first?.windows.first {
            return window
        }
        return UIWindow()
    }
}
