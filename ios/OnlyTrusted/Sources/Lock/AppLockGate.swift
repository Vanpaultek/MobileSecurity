// SPDX-FileCopyrightText: 2026 57Million LLC
// SPDX-License-Identifier: AGPL-3.0-or-later
import SwiftUI
import LocalAuthentication

struct AppLockGate: View {
    let onUnlocked: () -> Void

    @EnvironmentObject private var gate: AppGate
    @State private var error: String?
    @State private var canAuthenticate: Bool = false

    var body: some View {
        VStack(spacing: 20) {
            Image(systemName: "lock.shield.fill")
                .font(.system(size: 60))
                .foregroundStyle(Color.appSecondaryText)
                .decorativeAccessibility()

            Text("Locked")
                .font(.appLargeTitle)

            if let error {
                Text(error)
                    .font(.footnote)
                    .foregroundStyle(.red)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 24)
            }

            if canAuthenticate {
                Button {
                    attemptAuth()
                } label: {
                    Label("Unlock with Face ID / Passcode", systemImage: "faceid")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .padding(.horizontal, 32)
            } else {
                let lockWasOn = gate.appLockEnabled
                VStack(spacing: 10) {
                    Text(lockWasOn
                         ? "This device no longer has a passcode or biometric set. Disable App Lock below to recover, then re-enable it after setting one up."
                         : "Panic lock engaged. Tap below to return to the app.")
                        .font(.footnote)
                        .foregroundStyle(Color.appSecondaryText)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 24)
                    Button {
                        if lockWasOn { gate.appLockEnabled = false }
                        onUnlocked()
                    } label: {
                        Text(lockWasOn ? "Disable App Lock and continue" : "Return to app")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .padding(.horizontal, 32)
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(.systemBackground))
        .onAppear(perform: setup)
    }

    private func setup() {
        var err: NSError?
        canAuthenticate = LAContext().canEvaluatePolicy(.deviceOwnerAuthentication, error: &err)
        if canAuthenticate { attemptAuth() }
    }

    private func attemptAuth() {
        let ctx = LAContext()
        ctx.evaluatePolicy(.deviceOwnerAuthentication,
                           localizedReason: "Unlock OnlyTrusted") { ok, err in
            Task { @MainActor in
                if ok {
                    onUnlocked()
                } else if let err {
                    error = err.localizedDescription
                }
            }
        }
    }
}
