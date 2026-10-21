// SPDX-FileCopyrightText: 2026 57Million LLC
// SPDX-License-Identifier: AGPL-3.0-or-later
import SwiftUI

@MainActor
final class NotificationPreferences: ObservableObject {
    static let shared = NotificationPreferences()

    @Published var protectionAlerts: Bool { didSet { d.set(protectionAlerts, forKey: Keys.alerts) } }
    @Published var dailySummary: Bool { didSet { d.set(dailySummary, forKey: Keys.summary) } }
    @Published var trustedContactActivity: Bool { didSet { d.set(trustedContactActivity, forKey: Keys.trusted) } }

    private let d = UserDefaults.standard
    private enum Keys {
        static let alerts = "notif.alerts.v1"
        static let summary = "notif.summary.v1"
        static let trusted = "notif.trusted.v1"
    }

    private init() {
        protectionAlerts = d.object(forKey: Keys.alerts) as? Bool ?? true
        dailySummary = d.object(forKey: Keys.summary) as? Bool ?? false
        trustedContactActivity = d.object(forKey: Keys.trusted) as? Bool ?? true
    }
}

struct NotificationsScreen: View {
    @StateObject private var prefs = NotificationPreferences.shared

    private var anyOn: Bool {
        prefs.protectionAlerts || prefs.dailySummary || prefs.trustedContactActivity
    }

    var body: some View {
        ScrollView(.vertical, showsIndicators: false) {
            VStack(spacing: 18) {
                togglesCard
                marketingCard
                if anyOn { successBanner }
            }
            .padding(.horizontal, 20)
            .padding(.top, 12)
            .padding(.bottom, 32)
        }
        .background(Color.appScreenBackground.ignoresSafeArea())
        .navigationTitle("Notifications")
        .navigationBarTitleDisplayMode(.inline)
    }

    private var togglesCard: some View {
        PremiumCard {
            VStack(spacing: 0) {
                NotifToggleRow(
                    icon: "bell.badge.fill", tint: Color.appAccent,
                    title: "Protection Alerts",
                    detail: "Important blocks and security events.",
                    isOn: $prefs.protectionAlerts
                )
                Divider().padding(.leading, 44)
                NotifToggleRow(
                    icon: "sun.max.fill", tint: Color.appAccent,
                    title: "Daily Summary",
                    detail: "A brief summary of activity.",
                    isOn: $prefs.dailySummary
                )
                Divider().padding(.leading, 44)
                NotifToggleRow(
                    icon: "person.2.fill", tint: Color.appAccent,
                    title: "Trusted Contact Activity",
                    detail: "Calls and texts from trusted contacts.",
                    isOn: $prefs.trustedContactActivity
                )
            }
        }
    }

    private var marketingCard: some View {
        PremiumCard {
            HStack(spacing: 14) {
                ZStack {
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .fill(Color.appTertiaryText.opacity(0.15))
                    Image(systemName: "sparkles")
                        .font(.body.weight(.semibold))
                        .foregroundStyle(Color.appTertiaryText)
                }
                .frame(width: 36, height: 36)
                .decorativeAccessibility()

                VStack(alignment: .leading, spacing: 2) {
                    Text("New Features")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Color.appPrimaryText)
                    Text("Updates and improvements (never marketing).")
                        .font(.caption)
                        .foregroundStyle(Color.appSecondaryText)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer()
                Toggle("", isOn: .constant(false)).labelsHidden().disabled(true)
            }
        }
    }

    private var successBanner: some View {
        PremiumCard {
            HStack(spacing: 14) {
                ZStack {
                    Circle().fill(Color.appSuccess.opacity(0.18))
                    Image(systemName: "bell.badge.fill")
                        .font(.body.weight(.semibold))
                        .foregroundStyle(Color.appSuccess)
                }
                .frame(width: 36, height: 36)
                .decorativeAccessibility()

                VStack(alignment: .leading, spacing: 2) {
                    Text("You're all set!")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Color.appPrimaryText)
                    Text("Notifications are on.")
                        .font(.caption)
                        .foregroundStyle(Color.appSecondaryText)
                }
                Spacer()
            }
        }
    }
}

private struct NotifToggleRow: View {
    let icon: String
    let tint: Color
    let title: String
    let detail: String
    @Binding var isOn: Bool

    var body: some View {
        Toggle(isOn: $isOn) {
            HStack(spacing: 14) {
                ZStack {
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .fill(tint.opacity(0.14))
                    Image(systemName: icon)
                        .font(.body.weight(.semibold))
                        .foregroundStyle(tint)
                }
                .frame(width: 36, height: 36)
                .decorativeAccessibility()

                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Color.appPrimaryText)
                    Text(detail)
                        .font(.caption)
                        .foregroundStyle(Color.appSecondaryText)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
        .tint(Color.appAccent)
        .padding(.vertical, 10)
    }
}
