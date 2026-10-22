// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import SwiftUI
import MessageUI

/// Pro-only screen reached from Settings → Security & data → "Safety Check-In (Pro)".
///
/// One-tap SMS blast to the entire Trusted Vault: "I'm safe" + current time.
/// Optional daily nag notification if the owner hasn't checked in by a chosen time.
struct SafetyCheckInScreen: View {
    @StateObject private var state = CheckInState.shared
    @StateObject private var vault = VaultStore.shared
    @StateObject private var duress = DuressState.shared

    @State private var showComposer = false
    @State private var showNoContactsAlert = false
    @State private var showCantSendAlert = false
    @State private var statusMessage: String?
    @State private var scheduledToggleBinding = false
    @State private var pickedTime: Date = Date()

    private var trustedNumbers: [String] {
        // Dedupe and keep normalised form only — iOS MessageUI accepts any dialable form.
        var seen = Set<String>()
        return vault.realContactsForSetup()
            .flatMap { $0.normalisedNumbers }
            .filter { num in
                guard !num.isEmpty else { return false }
                return seen.insert(num).inserted
            }
    }

    private var ownerName: String {
        if !duress.ownerName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return duress.ownerName
        }
        if let owner = vault.realContactsForSetup().first(where: { $0.isOwner }) {
            return owner.displayName
        }
        return ""
    }

    var body: some View {
        ScrollView(.vertical, showsIndicators: false) {
            VStack(alignment: .leading, spacing: 20) {
                statusCard
                primaryCTA
                scheduledCard
                howCard
            }
            .padding(.horizontal, 18)
            .padding(.top, 10)
            .padding(.bottom, 32)
        }
        .appBackdrop()
        .navigationTitle("Safety Check-In")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            scheduledToggleBinding = state.scheduledEnabled
            var comp = DateComponents()
            comp.hour = state.scheduledHour
            comp.minute = state.scheduledMinute
            pickedTime = Calendar.current.date(from: comp) ?? Date()
        }
        .sheet(isPresented: $showComposer) {
            CheckInSMSComposer(
                recipients: trustedNumbers,
                body: CheckInState.composedBody(ownerName: ownerName)
            ) { result in
                showComposer = false
                switch result {
                case .sent:
                    state.markCheckedIn()
                    EventStore.shared.append(
                        type: SecurityEventRecord.Kind.safetyCheckInSent,
                        detail: "recipients=\(trustedNumbers.count)"
                    )
                    statusMessage = "Check-in sent to \(trustedNumbers.count) contact\(trustedNumbers.count == 1 ? "" : "s")."
                case .cancelled:
                    statusMessage = "Check-in cancelled."
                case .failed:
                    statusMessage = "Check-in could not be sent. Try again."
                @unknown default:
                    break
                }
            }
        }
        .alert("No trusted contacts", isPresented: $showNoContactsAlert) {
            Button("OK", role: .cancel) {}
        } message: {
            Text("Add at least one trusted contact on the Contacts tab, then come back here.")
        }
        .alert("Messages isn't available", isPresented: $showCantSendAlert) {
            Button("OK", role: .cancel) {}
        } message: {
            Text("This device can't send SMS. Try again on an iPhone with an active cellular plan or iMessage account.")
        }
        .alert("Check-in", isPresented: Binding(
            get: { statusMessage != nil },
            set: { if !$0 { statusMessage = nil } }
        )) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(statusMessage ?? "")
        }
    }

    // MARK: - Status card

    private var statusCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 10) {
                CardHeader(icon: "location.fill.viewfinder",
                           title: "Blast your status in one tap",
                           tint: Color.appAccent)
                Text("Sends a pre-written 'I'm safe' SMS to every number in your trusted vault.")
                    .font(.footnote)
                    .foregroundStyle(Color.appSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)
                Divider().background(Color.appDivider.opacity(0.3))
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Last check-in")
                            .font(.caption)
                            .foregroundStyle(Color.appSecondaryText)
                        Text(lastCheckInLabel)
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(Color.appPrimaryText)
                    }
                    Spacer()
                    VStack(alignment: .trailing, spacing: 2) {
                        Text("Will notify")
                            .font(.caption)
                            .foregroundStyle(Color.appSecondaryText)
                        Text("\(trustedNumbers.count) contact\(trustedNumbers.count == 1 ? "" : "s")")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(Color.appBrightCyan)
                    }
                }
            }
        }
    }

    private var lastCheckInLabel: String {
        guard let at = state.lastCheckInAt else { return "Never" }
        let f = DateFormatter()
        f.dateStyle = .medium
        f.timeStyle = .short
        return f.string(from: at)
    }

    // MARK: - Primary CTA

    private var primaryCTA: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 14) {
                Text("Preview")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(Color.appSecondaryText)
                Text(CheckInState.composedBody(ownerName: ownerName))
                    .font(.footnote)
                    .foregroundStyle(Color.appPrimaryText)
                    .padding(14)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .fill(Color.appElevatedSurface)
                    )
                Button(action: handleImSafeTap) {
                    HStack(spacing: 10) {
                        Image(systemName: "checkmark.shield.fill")
                        Text("I'm safe")
                    }
                    .font(.body.weight(.semibold))
                    .frame(maxWidth: .infinity, minHeight: 50)
                }
                .buttonStyle(.borderedProminent)
                .tint(Color.appAccent)
                .disabled(trustedNumbers.isEmpty)
                if trustedNumbers.isEmpty {
                    Text("Add a trusted contact first so the check-in has somewhere to go.")
                        .font(.caption)
                        .foregroundStyle(Color.appWarning)
                }
            }
        }
    }

    private func handleImSafeTap() {
        if trustedNumbers.isEmpty {
            showNoContactsAlert = true
            return
        }
        if !CheckInSMSComposer.canSendText {
            showCantSendAlert = true
            return
        }
        showComposer = true
    }

    // MARK: - Scheduled nag

    private var scheduledCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 12) {
                CardHeader(icon: "bell.badge.fill",
                           title: "Scheduled check-in",
                           tint: Color.appWarning)
                Toggle(isOn: Binding(
                    get: { state.scheduledEnabled },
                    set: { want in
                        if want {
                            state.requestNotificationPermissionIfNeeded { granted in
                                if granted {
                                    state.scheduledEnabled = true
                                } else {
                                    state.scheduledEnabled = false
                                    statusMessage = "Enable notifications in iOS Settings to use the scheduled check-in."
                                }
                            }
                        } else {
                            state.scheduledEnabled = false
                        }
                    }
                )) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Nag me if I forget")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(Color.appPrimaryText)
                        Text("Posts a local notification each day at the time below.")
                            .font(.caption)
                            .foregroundStyle(Color.appSecondaryText)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
                .tint(Color.appWarning)
                if state.scheduledEnabled {
                    DatePicker("Daily reminder time",
                               selection: $pickedTime,
                               displayedComponents: .hourAndMinute)
                        .datePickerStyle(.compact)
                        .tint(Color.appAccent)
                        .onChange(of: pickedTime) { _, newTime in
                            let comps = Calendar.current.dateComponents([.hour, .minute], from: newTime)
                            state.scheduledHour = comps.hour ?? 21
                            state.scheduledMinute = comps.minute ?? 0
                        }
                }
            }
        }
    }

    private var howCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 10) {
                CardHeader(icon: "info.circle.fill",
                           title: "How it works on iOS",
                           tint: Color.appBrightCyan)
                Text("Tapping 'I'm safe' opens the Messages composer with the body and recipients pre-filled. You tap Send once; iOS blasts the SMS to every trusted contact. Apple has no API for truly silent SMS, so the composer step is unavoidable on iOS. On Android we send silently.")
                    .font(.caption)
                    .foregroundStyle(Color.appSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }
}

/// Upsell shown to free users.
struct CheckInUpsellSheet: View {
    let onUpgrade: () -> Void
    let onDismiss: () -> Void

    var body: some View {
        VStack(spacing: 20) {
            Image(systemName: "location.fill.viewfinder")
                .font(.system(size: 46, weight: .semibold))
                .foregroundStyle(Color.subGold)
                .padding(.top, 28)
            Text("Safety Check-In")
                .font(.title2.weight(.bold))
                .foregroundStyle(Color.appPrimaryText)
            Text("One tap sends 'I'm safe' to every trusted contact. Optional daily nag if you forget. Included in Pro.")
                .font(.subheadline)
                .foregroundStyle(Color.appSecondaryText)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)
            Spacer(minLength: 0)
            Button(action: onUpgrade) {
                Text("Upgrade to Pro")
                    .font(.body.weight(.semibold))
                    .frame(maxWidth: .infinity, minHeight: 48)
            }
            .buttonStyle(.borderedProminent)
            .tint(Color.subGold)
            .padding(.horizontal, 24)
            Button("Not now", action: onDismiss)
                .foregroundStyle(Color.appSecondaryText)
                .padding(.bottom, 20)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .appBackdrop()
    }
}
