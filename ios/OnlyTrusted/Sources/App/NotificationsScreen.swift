// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import SwiftUI

@MainActor
final class NotificationPreferences: ObservableObject {
    static let shared = NotificationPreferences()

    @Published var protectionAlerts: Bool { didSet { d.set(protectionAlerts, forKey: Keys.alerts) } }
    @Published var dailySummary: Bool { didSet { d.set(dailySummary, forKey: Keys.summary) } }
    @Published var trustedContactActivity: Bool { didSet { d.set(trustedContactActivity, forKey: Keys.trusted) } }
    @Published var appUpdates: Bool { didSet { d.set(appUpdates, forKey: Keys.updates) } }
    @Published var tipsAndSafety: Bool { didSet { d.set(tipsAndSafety, forKey: Keys.tips) } }
    @Published var accountAndDeviceAlerts: Bool { didSet { d.set(accountAndDeviceAlerts, forKey: Keys.account) } }

    private let d = UserDefaults.standard
    private enum Keys {
        static let alerts = "notif.alerts.v1"
        static let summary = "notif.summary.v1"
        static let trusted = "notif.trusted.v1"
        static let updates = "notif.updates.v1"
        static let tips = "notif.tips.v1"
        static let account = "notif.account.v1"
    }

    private init() {
        protectionAlerts = d.object(forKey: Keys.alerts) as? Bool ?? true
        dailySummary = d.object(forKey: Keys.summary) as? Bool ?? true
        trustedContactActivity = d.object(forKey: Keys.trusted) as? Bool ?? true
        appUpdates = d.object(forKey: Keys.updates) as? Bool ?? false
        tipsAndSafety = d.object(forKey: Keys.tips) as? Bool ?? true
        accountAndDeviceAlerts = d.object(forKey: Keys.account) as? Bool ?? true
    }
}

struct NotificationsScreen: View {
    @StateObject private var prefs = NotificationPreferences.shared
    @Environment(\.dismiss) private var dismiss

    private var anyOn: Bool {
        prefs.protectionAlerts || prefs.dailySummary || prefs.trustedContactActivity
            || prefs.appUpdates || prefs.tipsAndSafety || prefs.accountAndDeviceAlerts
    }

    var body: some View {
        VStack(spacing: 0) {
            header
                .padding(.horizontal, 18)
                .padding(.top, 6)
                .padding(.bottom, 12)
                .background(
                    ZStack {
                        Color.appScreenBackground
                        Color.black.opacity(0.25)
                    }
                    .ignoresSafeArea(edges: .top)
                )

            ScrollView(.vertical, showsIndicators: false) {
                VStack(spacing: 14) {
                    NotifToggleCard(
                        icon: "exclamationmark.shield.fill", tint: Color.notifTeal,
                        title: "Protection Alerts",
                        detail: "Important blocks and security events.",
                        isOn: $prefs.protectionAlerts
                    )
                    NotifToggleCard(
                        icon: "chart.bar.fill", tint: Color.notifTeal,
                        title: "Daily Summary",
                        detail: "A brief summary of your activity.",
                        isOn: $prefs.dailySummary
                    )
                    NotifToggleCard(
                        icon: "person.2.fill", tint: Color.notifTeal,
                        title: "Trusted Contact Activity",
                        detail: "Calls and texts from your trusted contacts.",
                        isOn: $prefs.trustedContactActivity
                    )
                    NotifToggleCard(
                        icon: "bell.fill", tint: Color.notifPurple,
                        title: "App Updates",
                        detail: "New features and improvements (never marketing).",
                        isOn: $prefs.appUpdates
                    )
                    NotifToggleCard(
                        icon: "message.fill", tint: Color.notifOrange,
                        title: "Tips & Safety Insights",
                        detail: "Helpful tips to stay safe and protected.",
                        isOn: $prefs.tipsAndSafety
                    )
                    NotifToggleCard(
                        icon: "gearshape.fill", tint: Color.notifPink,
                        title: "Account & Device Alerts",
                        detail: "Important changes to your account or device.",
                        isOn: $prefs.accountAndDeviceAlerts
                    )

                    if anyOn { successBanner }
                }
                .padding(.horizontal, 18)
                .padding(.top, 8)
                .padding(.bottom, 32)
            }
        }
        .appBackdrop()
        .navigationBarHidden(true)
    }

    // MARK: - Centered header with back and title + subtitle

    private var header: some View {
        VStack(spacing: 10) {
            ZStack {
                HStack {
                    Button(action: { dismiss() }) {
                        HStack(spacing: 4) {
                            Image(systemName: "chevron.left")
                                .font(.system(size: 16, weight: .semibold))
                            Text("Settings")
                                .font(.system(size: 16, weight: .semibold))
                        }
                        .foregroundStyle(Color.appBrightCyan)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Back to Settings")
                    Spacer()
                }
                Text("Notifications")
                    .font(.system(size: 24, weight: .bold, design: .rounded))
                    .foregroundStyle(Color.appPrimaryText)
            }
            .padding(.top, 6)

            Text("Control what you want to be notified about.")
                .font(.subheadline)
                .foregroundStyle(Color.appSecondaryText)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.horizontal, 20)
                .padding(.bottom, 4)
        }
    }

    // MARK: - Success banner

    private var successBanner: some View {
        HStack(spacing: 14) {
            ZStack {
                Circle()
                    .stroke(Color.appBrightCyan.opacity(0.6), lineWidth: 2)
                    .frame(width: 46, height: 46)
                    .shadow(color: Color.appBrightCyan.opacity(0.6), radius: 10, x: 0, y: 0)
                Image(systemName: "bell.fill")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(Color.appBrightCyan)
            }
            .decorativeAccessibility()

            VStack(alignment: .leading, spacing: 2) {
                Text("You're all set!")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundStyle(Color.appBrightCyan)
                Text("Notifications are on (except selected options).")
                    .font(.footnote)
                    .foregroundStyle(Color.appSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 0)
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(Color.appElevatedSurface.opacity(0.5))
                .overlay(
                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .stroke(Color.appBrightCyan.opacity(0.55), lineWidth: 1)
                )
        )
        .padding(.top, 6)
    }
}

// MARK: - Toggle card with colored neon icon chip

private struct NotifToggleCard: View {
    let icon: String
    let tint: Color
    let title: String
    let detail: String
    @Binding var isOn: Bool

    var body: some View {
        HStack(alignment: .center, spacing: 14) {
            ZStack {
                RoundedRectangle(cornerRadius: 11, style: .continuous)
                    .stroke(tint.opacity(0.7), lineWidth: 1.5)
                    .background(
                        RoundedRectangle(cornerRadius: 11, style: .continuous)
                            .fill(tint.opacity(0.15))
                    )
                    .shadow(color: tint.opacity(0.6), radius: 10, x: 0, y: 0)
                Image(systemName: icon)
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundStyle(tint)
            }
            .frame(width: 46, height: 46)
            .decorativeAccessibility()

            VStack(alignment: .leading, spacing: 3) {
                Text(title)
                    .font(.system(size: 16, weight: .bold))
                    .foregroundStyle(Color.appPrimaryText)
                Text(detail)
                    .font(.system(size: 13))
                    .foregroundStyle(Color.appSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 8)
            Toggle("", isOn: $isOn)
                .labelsHidden()
                .tint(Color.appBrightCyan)
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(Color.appElevatedSurface)
                .overlay(
                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .stroke(Color.appBrightCyan.opacity(0.1), lineWidth: 1)
                )
        )
    }
}

// MARK: - Neon tints used by this screen

private extension Color {
    static let notifTeal   = Color(red:   0.0/255.0, green: 209.0/255.0, blue: 193.0/255.0)
    static let notifPurple = Color(red: 168.0/255.0, green:  85.0/255.0, blue: 247.0/255.0)
    static let notifOrange = Color(red: 245.0/255.0, green: 158.0/255.0, blue:  11.0/255.0)
    static let notifPink   = Color(red: 236.0/255.0, green:  72.0/255.0, blue: 153.0/255.0)
}
