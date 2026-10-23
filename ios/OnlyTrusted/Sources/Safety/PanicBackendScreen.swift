// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import SwiftUI

/// Lets the user configure (or clear) an optional HTTPS endpoint + bearer token for the
/// Panic SOS backend path. When set, the Dashboard SOS button posts to this endpoint
/// instead of opening the Messages composer. Keeping this free-tier (same tier as F1
/// Panic SOS itself) because it's just a config form — the actual delivery is the user's
/// own server.
struct PanicBackendScreen: View {
    @Environment(\.dismiss) private var dismiss
    @State private var endpoint: String = PanicBackend.endpoint
    @State private var token: String = PanicBackend.token
    @State private var testStatus: String = ""
    @State private var testing = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                headerCard
                formCard
                testCard
                aboutCard
            }
            .padding(.horizontal, 18)
            .padding(.vertical, 20)
        }
        .appBackdrop()
        .navigationTitle("Panic SOS backend")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .confirmationAction) {
                Button("Save") { save() }
                    .disabled(endpoint.isEmpty && token.isEmpty && !PanicBackend.isConfigured)
            }
        }
    }

    private var headerCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            Label("Optional server fanout", systemImage: "antenna.radiowaves.left.and.right")
                .font(.headline)
                .foregroundStyle(Color.appBrightCyan)
            Text("""
Panic SOS works out of the box — one tap dials your first trusted contact and opens a \
pre-filled Messages composer (iOS requires you to tap Send). If you run your own server, \
configure it here and the panic flow will POST an alert instead of opening the composer, \
so the SMS goes out silently.

OnlyTrusted never sees the alert — you run the server.
""")
                .font(.footnote)
                .foregroundStyle(Color.appSecondaryText)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(Color.appElevatedSurface)
        )
    }

    private var formCard: some View {
        VStack(alignment: .leading, spacing: 14) {
            VStack(alignment: .leading, spacing: 6) {
                Text("Endpoint URL")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(Color.appPrimaryText)
                TextField("https://your.server/sos", text: $endpoint)
                    .textFieldStyle(.roundedBorder)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .keyboardType(.URL)
            }
            VStack(alignment: .leading, spacing: 6) {
                Text("Bearer token")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(Color.appPrimaryText)
                SecureField("Paste token", text: $token)
                    .textFieldStyle(.roundedBorder)
            }

            if PanicBackend.isConfigured {
                Button(role: .destructive) {
                    endpoint = ""
                    token = ""
                    PanicBackend.endpoint = ""
                    PanicBackend.token = ""
                    testStatus = "Backend cleared. Panic will fall back to the Messages composer."
                } label: {
                    Label("Clear backend", systemImage: "xmark.circle")
                }
                .buttonStyle(.bordered)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(Color.appElevatedSurface)
        )
    }

    private var testCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Test the endpoint")
                .font(.headline)
                .foregroundStyle(Color.appPrimaryText)
            Text("Sends a `test` payload with no real recipients so you can confirm the server reachability. The server should respond 2xx on success.")
                .font(.footnote)
                .foregroundStyle(Color.appSecondaryText)
            Button(action: runTest) {
                HStack {
                    if testing { ProgressView() }
                    Text(testing ? "Testing…" : "Send test POST")
                }
            }
            .buttonStyle(.borderedProminent)
            .tint(Color.appBrightCyan)
            .disabled(testing || !canTestNow)
            if !testStatus.isEmpty {
                Text(testStatus)
                    .font(.footnote)
                    .foregroundStyle(Color.appSecondaryText)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(Color.appElevatedSurface)
        )
    }

    private var aboutCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Server contract")
                .font(.headline)
                .foregroundStyle(Color.appPrimaryText)
            Text("""
POST JSON body:
{
  "recipients": ["+15551234567", ...],
  "isDuress": false,
  "location": "https://maps.apple.com/?ll=...",
  "createdAt": "2026-10-22T..."
}
Headers: Content-Type: application/json, Authorization: Bearer <token>.
Return 200/201/202 to confirm the server accepted the request. A 2xx response does NOT guarantee SMS delivery — your server owns that.
""")
                .font(.caption)
                .foregroundStyle(Color.appSecondaryText)
                .textSelection(.enabled)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(Color.appElevatedSurface)
        )
    }

    private var canTestNow: Bool {
        guard let url = URL(string: endpoint.trimmingCharacters(in: .whitespaces)),
              url.scheme == "https", url.host != nil
        else { return false }
        return !token.trimmingCharacters(in: .whitespaces).isEmpty
    }

    private func save() {
        PanicBackend.endpoint = endpoint.trimmingCharacters(in: .whitespaces)
        PanicBackend.token = token.trimmingCharacters(in: .whitespaces)
        testStatus = PanicBackend.isConfigured
            ? "Saved. Panic will POST to this endpoint instead of opening the Messages composer."
            : "Not saved — endpoint must be https:// and token must be non-empty."
    }

    private func runTest() {
        guard canTestNow else {
            testStatus = "Fill in a valid https:// URL and a non-empty token first."
            return
        }
        // Persist the current form values before firing so the test uses them.
        PanicBackend.endpoint = endpoint.trimmingCharacters(in: .whitespaces)
        PanicBackend.token = token.trimmingCharacters(in: .whitespaces)

        testing = true
        testStatus = "Sending test POST…"
        Task {
            let result = await PanicBackend.send(
                recipients: [],
                isDuress: false,
                location: "test"
            )
            await MainActor.run {
                testing = false
                switch result {
                case .ok:
                    testStatus = "✓ Server returned 2xx. Panic will post to this endpoint."
                case .notConfigured:
                    testStatus = "Not configured — recheck URL + token."
                case .serverRejected(let code):
                    testStatus = "✗ Server returned HTTP \(code)."
                case .transportError(let msg):
                    testStatus = "✗ Transport error: \(msg)"
                }
            }
        }
    }
}
