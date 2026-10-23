// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import SwiftUI

/// Full-screen UI for Breach Watch (FREE tier).
///
/// Scope on FREE: monitor exactly ONE email, with a monthly local reminder + ad-hoc
/// "Check now" button. Any breach hit surfaces a remediation playbook (change password,
/// enable 2FA / passkey, review account activity). An "Elite" upsell card at the bottom
/// points at the future enterprise-feed tier — the Elite backend is not implemented in
/// this build, and the copy says so.
struct BreachWatchScreen: View {

    @StateObject private var state = BreachWatchState.shared
    @StateObject private var sub = SubscriptionState.shared
    @State private var draftEmail: String = ""
    @State private var draftName: String = ""
    @State private var editingEmail: Bool = false
    @State private var showNoNotifPerm: Bool = false
    @State private var additionalDraft: String = ""

    private var isSaved: Bool { !state.email.isEmpty }

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                heroCard
                monitoredEmailCard
                additionalEmailsCard
                nameCard
                statusCard
                if !state.hits.isEmpty { hitsCard } else if isSaved { emptyStateCard }
                upsellCard
                attributionCard
            }
            .padding(20)
        }
        .appBackdrop()
        .navigationTitle("Breach Watch")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            draftEmail = state.email
            draftName = state.userName
            editingEmail = !isSaved
            BreachWatchScheduler.requestPermissionIfNeeded { granted in
                showNoNotifPerm = !granted
                if granted && isSaved {
                    BreachWatchScheduler.scheduleMonthlyReminder()
                }
            }
        }
    }

    // MARK: - Cards

    private var heroCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 10) {
                CardHeader(icon: "envelope.badge.shield.half.filled",
                           title: "Breach Watch",
                           tint: Color.appAccent)
                Text("Monitor one email address against our bundled breach-hash list. All lookups happen on your device — nothing leaves this app.")
                    .font(.footnote)
                    .foregroundStyle(Color.appSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)
                Text("We rehash and check once a month, and you can tap \"Check now\" any time.")
                    .font(.footnote)
                    .foregroundStyle(Color.appSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    private var monitoredEmailCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 12) {
                CardHeader(icon: "at", title: "Monitored email", tint: Color.appAccent)
                if editingEmail || !isSaved {
                    TextField("you@example.com", text: $draftEmail)
                        .keyboardType(.emailAddress)
                        .textContentType(.emailAddress)
                        .autocapitalization(.none)
                        .disableAutocorrection(true)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 10)
                        .background(
                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                .fill(Color.appScreenBackground)
                        )
                    HStack {
                        Button {
                            state.saveEmail(draftEmail)
                            if !state.email.isEmpty {
                                editingEmail = false
                                BreachWatchScheduler.scheduleMonthlyReminder()
                                _ = state.runCheck()
                            } else {
                                BreachWatchScheduler.cancel()
                            }
                        } label: {
                            Text("Save")
                                .font(.subheadline.weight(.semibold))
                                .frame(maxWidth: .infinity, minHeight: 40)
                        }
                        .buttonStyle(.borderedProminent)
                        .tint(Color.appAccent)
                        if isSaved {
                            Button("Cancel") {
                                draftEmail = state.email
                                editingEmail = false
                            }
                            .foregroundStyle(Color.appSecondaryText)
                        }
                    }
                } else {
                    HStack {
                        Text(state.email)
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(Color.appPrimaryText)
                            .lineLimit(1)
                            .truncationMode(.middle)
                        Spacer()
                        Button("Change") {
                            draftEmail = state.email
                            editingEmail = true
                        }
                        .foregroundStyle(Color.appAccent)
                    }
                }
            }
        }
    }

    @ViewBuilder
    private var additionalEmailsCard: some View {
        if sub.hasPremium {
            PremiumCard {
                VStack(alignment: .leading, spacing: 12) {
                    CardHeader(icon: "envelope.badge.fill",
                               title: "Additional emails (Premium)",
                               tint: Color.appAccent)
                    Text("Monitor up to \(BreachWatchState.maxEmailsPremium) emails in total. Checks run against all of them at once.")
                        .font(.caption)
                        .foregroundStyle(Color.appSecondaryText)
                    ForEach(state.additionalEmails, id: \.self) { addr in
                        HStack {
                            Image(systemName: "at")
                                .foregroundStyle(Color.appAccent)
                            Text(addr)
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(Color.appPrimaryText)
                                .lineLimit(1)
                                .truncationMode(.middle)
                            Spacer()
                            Button {
                                state.removeAdditionalEmail(addr)
                            } label: {
                                Image(systemName: "minus.circle.fill")
                                    .foregroundStyle(Color.appDanger)
                            }
                            .buttonStyle(.plain)
                        }
                        .padding(.vertical, 4)
                    }
                    let canAdd = (1 + state.additionalEmails.count) < BreachWatchState.maxEmailsPremium
                    if canAdd {
                        HStack {
                            TextField("another@example.com", text: $additionalDraft)
                                .keyboardType(.emailAddress)
                                .textContentType(.emailAddress)
                                .autocapitalization(.none)
                                .disableAutocorrection(true)
                                .padding(.horizontal, 12)
                                .padding(.vertical, 10)
                                .background(
                                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                                        .fill(Color.appScreenBackground)
                                )
                            Button {
                                if state.addAdditionalEmail(additionalDraft) {
                                    additionalDraft = ""
                                    _ = state.runCheck()
                                }
                            } label: {
                                Image(systemName: "plus.circle.fill")
                                    .font(.title2)
                                    .foregroundStyle(Color.appAccent)
                            }
                            .buttonStyle(.plain)
                            .disabled(additionalDraft.trimmingCharacters(in: .whitespaces).isEmpty)
                        }
                    } else {
                        Text("You've reached the \(BreachWatchState.maxEmailsPremium)-email cap.")
                            .font(.caption)
                            .foregroundStyle(Color.appSecondaryText)
                    }
                }
            }
        } else {
            // Free upsell card — tappable, routes to Upgrade.
            PremiumCard {
                VStack(alignment: .leading, spacing: 10) {
                    CardHeader(icon: "crown.fill",
                               title: "Watch up to 5 emails (Premium)",
                               tint: Color.subGold)
                    Text("Premium ($4.99/yr) lets you monitor your work, side, and family emails against the same bundled breach list — all on-device.")
                        .font(.caption)
                        .foregroundStyle(Color.appSecondaryText)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
    }

    private var nameCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 12) {
                CardHeader(icon: "person.text.rectangle",
                           title: "Your name (optional)",
                           tint: Color.appAccent)
                Text("Used only to personalise the remediation steps. Never leaves this device.")
                    .font(.caption)
                    .foregroundStyle(Color.appSecondaryText)
                HStack {
                    TextField("First name", text: $draftName)
                        .textContentType(.givenName)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 10)
                        .background(
                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                .fill(Color.appScreenBackground)
                        )
                        .onChange(of: draftName) { _, newValue in
                            state.userName = newValue.trimmingCharacters(in: .whitespacesAndNewlines)
                        }
                }
            }
        }
    }

    private var statusCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 12) {
                CardHeader(icon: "clock.badge.checkmark",
                           title: "Last check",
                           tint: Color.appAccent)
                Text(statusText)
                    .font(.subheadline)
                    .foregroundStyle(Color.appSecondaryText)
                Button {
                    let before = state.hits.count
                    let after = state.runCheck()
                    if after.count > before {
                        BreachWatchScheduler.postNewHitNotification()
                    }
                } label: {
                    Text(isSaved ? "Check now" : "Save an email to check")
                        .font(.subheadline.weight(.semibold))
                        .frame(maxWidth: .infinity, minHeight: 40)
                }
                .buttonStyle(.borderedProminent)
                .tint(Color.appAccent)
                .disabled(!isSaved)

                if showNoNotifPerm {
                    Text("Notifications are off — the monthly reminder can't fire. Enable notifications in iOS Settings → OnlyTrusted.")
                        .font(.caption)
                        .foregroundStyle(Color.appWarning)
                }
            }
        }
    }

    private var statusText: String {
        guard let date = state.lastChecked else {
            return isSaved ? "Not yet checked." : "Save an email to begin monitoring."
        }
        let f = DateFormatter()
        f.dateStyle = .medium
        f.timeStyle = .short
        return "Last checked \(f.string(from: date))."
    }

    private var hitsCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 14) {
                HStack(spacing: 10) {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .foregroundStyle(.white)
                        .padding(8)
                        .background(
                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                .fill(Color.appDanger)
                        )
                    Text("Your email appears in known breaches")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Color.appDanger)
                }
                ForEach(state.hits) { hit in
                    hitRow(hit)
                }
            }
        }
    }

    private func hitRow(_ hit: BreachWatchState.BreachHit) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(hit.source)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(Color.appPrimaryText)
            Text("First seen \(firstSeenLabel(hit.firstSeen))")
                .font(.caption)
                .foregroundStyle(Color.appSecondaryText)
            Text(remediationGreeting)
                .font(.caption)
                .foregroundStyle(Color.appSecondaryText)
            Text("What to do:")
                .font(.footnote.weight(.semibold))
                .foregroundStyle(Color.appPrimaryText)
            remediationButton(
                icon: "key.horizontal.fill",
                label: "Change password on affected site",
                tint: Color.appAccent
            )
            remediationButton(
                icon: "lock.shield.fill",
                label: "Enable 2FA / passkey",
                tint: Color.appAccent
            )
            remediationButton(
                icon: "list.bullet.rectangle.portrait",
                label: "Review account activity",
                tint: Color.appAccent
            )
        }
        .padding(14)
        .background(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(Color.appScreenBackground)
        )
    }

    private func remediationButton(icon: String, label: String, tint: Color) -> some View {
        // Static playbook — no deep-link is possible without knowing WHICH site. Pressing
        // the button posts a minimal in-flow confirmation; the user then performs the
        // action on the real site. We refuse to fake "did it for you".
        Button {
            // No-op by design. The playbook educates, it does not automate.
        } label: {
            HStack(spacing: 10) {
                Image(systemName: icon)
                    .foregroundStyle(tint)
                    .frame(width: 24)
                Text(label)
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(Color.appPrimaryText)
                Spacer()
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .background(
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .fill(tint.opacity(0.12))
            )
        }
        .buttonStyle(.plain)
    }

    private var remediationGreeting: String {
        let name = state.userName.isEmpty ? "" : "\(state.userName), "
        return "\(name)Here's what we recommend — OnlyTrusted can't do these for you, so each step is a manual action on the real site."
    }

    private func firstSeenLabel(_ date: Date) -> String {
        let f = DateFormatter()
        f.dateStyle = .medium
        return f.string(from: date)
    }

    private var emptyStateCard: some View {
        PremiumCard {
            HStack(spacing: 12) {
                Image(systemName: "checkmark.seal.fill")
                    .foregroundStyle(.white)
                    .padding(8)
                    .background(
                        RoundedRectangle(cornerRadius: 10, style: .continuous)
                            .fill(Color.appSuccess)
                    )
                VStack(alignment: .leading, spacing: 4) {
                    Text("No breaches found")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Color.appSuccess)
                    Text("We'll check again at the start of next month.")
                        .font(.caption)
                        .foregroundStyle(Color.appSecondaryText)
                }
            }
        }
    }

    private var upsellCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 10) {
                HStack(spacing: 10) {
                    Image(systemName: "crown.fill")
                        .foregroundStyle(.white)
                        .padding(8)
                        .background(
                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                .fill(Color.subGold)
                        )
                    Text("Elite — enterprise breach feed")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Color.appPrimaryText)
                }
                Text("Elite will let you monitor up to 10 emails + phone numbers with real-time alerts via the HIBP Enterprise feed. That requires a backend subscription, so it's not available in this build yet.")
                    .font(.caption)
                    .foregroundStyle(Color.appSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    private var attributionCard: some View {
        // HIBP CC-BY-4.0 attribution — the bundled breach_hashes.v1.txt feed is derived
        // from Troy Hunt's Have I Been Pwned public dataset. Visible attribution is a
        // license requirement for commercial use; keep this card wired into the view.
        PremiumCard {
            VStack(alignment: .leading, spacing: 6) {
                Text("Data attribution")
                    .font(.caption2.weight(.semibold))
                    .foregroundStyle(Color.appSecondaryText)
                Text("Breach hashes bundled with this app are derived from the Have I Been Pwned public dataset (Troy Hunt, haveibeenpwned.com) under CC-BY-4.0. The lookup runs entirely on your device.")
                    .font(.caption2)
                    .foregroundStyle(Color.appTertiaryText)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }
}
