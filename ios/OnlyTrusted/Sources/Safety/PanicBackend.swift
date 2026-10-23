// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation

/// Optional Panic SOS backend. iOS can't send a truly silent SMS from the device, so the
/// default panic flow (F1) opens the Messages composer pre-filled and waits for the user
/// to tap Send. For users running their own server (Twilio / MessageBird / MessageBird /
/// bring-your-own-stack), the backend path turns panic into a real one-tap alert — the
/// app POSTs an `AlertPayload` to an HTTPS endpoint and the server fans out SMS on the
/// user's behalf.
///
/// This stays faithful to the no-cloud-for-us principle: WE never run a server, host no
/// data, and know nothing about who the alert went to. The user picks (and operates)
/// their own endpoint.
///
/// If the user has NOT configured a backend, callers should fall through to the
/// Messages composer path.
enum PanicBackend {

    private enum Keys {
        static let endpoint = "panic.backend.endpoint"
        static let token = "panic.backend.token"
    }

    /// HTTPS URL the app POSTs to. Must start with `https://`.
    static var endpoint: String {
        get { UserDefaults.standard.string(forKey: Keys.endpoint) ?? "" }
        set { UserDefaults.standard.set(newValue, forKey: Keys.endpoint) }
    }

    /// Bearer token sent in the `Authorization` header. Stored in UserDefaults for the
    /// current build; shipping builds should migrate to Keychain.
    static var token: String {
        get { UserDefaults.standard.string(forKey: Keys.token) ?? "" }
        set { UserDefaults.standard.set(newValue, forKey: Keys.token) }
    }

    static var isConfigured: Bool {
        guard let url = URL(string: endpoint), url.scheme == "https", url.host != nil
        else { return false }
        return !token.isEmpty
    }

    struct AlertPayload: Codable {
        let recipients: [String]
        let isDuress: Bool
        let location: String
        let createdAt: String
    }

    enum SendResult {
        case ok                     // Server returned 2xx (delivery NOT guaranteed)
        case notConfigured          // Caller should fall back to Messages composer
        case serverRejected(Int)    // HTTP non-2xx
        case transportError(String) // Network / DNS / TLS failure
    }

    /// Fires the alert. 2xx response means the server accepted the request — it does NOT
    /// mean the SMS has been delivered to any recipient (the server owns that work).
    /// Timeout 15s so a stalled server doesn't block the panic UI.
    static func send(recipients: [String], isDuress: Bool, location: String) async -> SendResult {
        guard isConfigured else { return .notConfigured }
        guard let url = URL(string: endpoint) else { return .notConfigured }

        let payload = AlertPayload(
            recipients: recipients,
            isDuress: isDuress,
            location: location,
            createdAt: ISO8601DateFormatter().string(from: Date())
        )

        var req = URLRequest(url: url, timeoutInterval: 15)
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        do {
            req.httpBody = try JSONEncoder().encode(payload)
            let (_, response) = try await URLSession.shared.data(for: req)
            guard let http = response as? HTTPURLResponse else {
                return .transportError("No HTTP response")
            }
            if (200...299).contains(http.statusCode) { return .ok }
            return .serverRejected(http.statusCode)
        } catch {
            return .transportError(error.localizedDescription)
        }
    }
}
