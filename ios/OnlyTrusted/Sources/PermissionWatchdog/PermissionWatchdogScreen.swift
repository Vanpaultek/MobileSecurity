// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import SwiftUI

/// iOS App Permission Watchdog screen (Pro). Pro-gating is handled by the Settings row —
/// this screen assumes the viewer is on Pro. See `PermissionWatchdogStore` for the model.
///
/// Shape differs from the Android screen because iOS does not let apps enumerate other
/// apps' permissions. We instead:
///   • deep-link into Settings → Privacy & Security → App Privacy Report;
///   • show OnlyTrusted's own permission footprint with a "why we don't ask" explainer per row.
struct PermissionWatchdogScreen: View {
    @State private var permissions: [PermissionWatchdogStore.AuditedPermission] = []

    var body: some View {
        ScrollView(.vertical, showsIndicators: false) {
            VStack(alignment: .leading, spacing: 20) {
                heroCard
                privacyReportCard
                ownPermissionsCard
                limitationNote
            }
            .padding(.horizontal, 18)
            .padding(.top, 10)
            .padding(.bottom, 32)
        }
        .appBackdrop()
        .navigationTitle("Permission Watchdog")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { refresh() }
    }

    private func refresh() {
        permissions = PermissionWatchdogStore.audit()
    }

    // MARK: - Cards

    private var heroCard: some View {
        PremiumCard {
            HStack(spacing: 14) {
                ZStack {
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(Color.appAccent.opacity(0.18))
                    Image(systemName: "lock.shield.fill")
                        .font(.system(size: 24, weight: .semibold))
                        .foregroundStyle(Color.appAccent)
                }
                .frame(width: 48, height: 48)
                .decorativeAccessibility()
                VStack(alignment: .leading, spacing: 4) {
                    Text("A different kind of watchdog on iOS")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Color.appPrimaryText)
                    Text("iOS doesn't let apps audit other apps' permissions. So here you can jump into Apple's App Privacy Report, and review exactly what OnlyTrusted itself holds.")
                        .font(.caption)
                        .foregroundStyle(Color.appSecondaryText)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
    }

    private var privacyReportCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 12) {
                CardHeader(icon: "chart.bar.doc.horizontal.fill", title: "iOS App Privacy Report")
                Text("Apple's own tool lists which sensors every app on your phone has touched in the past 7 days — camera, mic, photos, location, contacts, and the domains each app has contacted. Available on iOS 15.2+.")
                    .font(.footnote)
                    .foregroundStyle(Color.appSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)
                Text("Open Settings → Privacy & Security → App Privacy Report. If you've never turned it on, tap Record App Activity.")
                    .font(.footnote)
                    .foregroundStyle(Color.appTertiaryText)
                    .fixedSize(horizontal: false, vertical: true)
                Button {
                    PermissionWatchdogStore.openPrivacyReport()
                } label: {
                    HStack {
                        Image(systemName: "arrow.up.right.square.fill")
                        Text("Open iOS Privacy Report")
                            .font(.body.weight(.semibold))
                    }
                    .frame(maxWidth: .infinity, minHeight: 44)
                }
                .buttonStyle(.borderedProminent)
                .tint(Color.appAccent)
            }
        }
    }

    private var ownPermissionsCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 12) {
                CardHeader(icon: "checkmark.shield.fill", title: "What OnlyTrusted asks for")
                Text("Every permission below is listed either as something we hold, or something we deliberately don't ask for — with the reason.")
                    .font(.footnote)
                    .foregroundStyle(Color.appSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)
                ForEach(Array(permissions.enumerated()), id: \.element.id) { pair in
                    if pair.offset > 0 { SettingsDivider() }
                    PermissionRow(permission: pair.element)
                }
            }
        }
    }

    private var limitationNote: some View {
        Text("Why no cross-app audit here? Apple's sandbox blocks third-party apps from seeing what other apps are installed or what each one is allowed to do. The App Privacy Report is Apple's own path and the right place to check.")
            .font(.caption)
            .foregroundStyle(Color.appTertiaryText)
            .fixedSize(horizontal: false, vertical: true)
            .padding(.horizontal, 4)
    }
}

/// Row inside the "What OnlyTrusted asks for" card.
private struct PermissionRow: View {
    let permission: PermissionWatchdogStore.AuditedPermission

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            ZStack {
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .fill(iconBackground)
                Image(systemName: permission.systemImage)
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(iconForeground)
            }
            .frame(width: 36, height: 36)
            .decorativeAccessibility()

            VStack(alignment: .leading, spacing: 4) {
                HStack(alignment: .center, spacing: 8) {
                    Text(permission.title)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Color.appPrimaryText)
                    Spacer()
                    Text(permission.isRequested ? "Requested" : "Not requested")
                        .font(.caption2.weight(.semibold))
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .background(badgeBackground, in: Capsule())
                        .foregroundStyle(badgeForeground)
                }
                Text(permission.currentState)
                    .font(.caption)
                    .foregroundStyle(Color.appSecondaryText)
                Text(permission.explainer)
                    .font(.caption)
                    .foregroundStyle(Color.appTertiaryText)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .padding(.vertical, 6)
    }

    private var iconBackground: Color {
        permission.isRequested
            ? Color.appAccent.opacity(0.16)
            : Color.appSecondaryText.opacity(0.14)
    }

    private var iconForeground: Color {
        permission.isRequested ? Color.appAccent : Color.appSecondaryText
    }

    private var badgeBackground: Color {
        permission.isRequested
            ? Color.appAccent.opacity(0.18)
            : Color.appTertiaryText.opacity(0.18)
    }

    private var badgeForeground: Color {
        permission.isRequested ? Color.appAccent : Color.appTertiaryText
    }
}
