// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import SwiftUI

struct MainTabs: View {
    @EnvironmentObject private var gate: AppGate
    @State private var selectedTab: Tab = .home

    enum Tab: Hashable { case home, contacts, blocked, log, settings }

    var body: some View {
        TabView(selection: $selectedTab) {
            NavigationStack {
                DashboardScreen(onNavigate: { selectedTab = $0 })
            }
            .tabItem { Label("Home", systemImage: "house.fill") }
            .tag(Tab.home)

            NavigationStack { VaultScreen() }
                .tabItem { Label("Contacts", systemImage: "person.2.fill") }
                .tag(Tab.contacts)

            NavigationStack { BlockListScreen() }
                .tabItem { Label("Blocked", systemImage: "nosign") }
                .tag(Tab.blocked)

            NavigationStack { EventsScreen() }
                .tabItem { Label("Log", systemImage: "doc.text.fill") }
                .tag(Tab.log)

            NavigationStack { SettingsScreen() }
                .tabItem { Label("Settings", systemImage: "gearshape.fill") }
                .tag(Tab.settings)
        }
        .tint(Color.appAccent)
        .toolbarBackground(Color.appScreenBackground, for: .tabBar)
        .toolbarBackground(.visible, for: .tabBar)
    }
}

// MARK: - Dashboard (Home tab)

struct DashboardScreen: View {
    let onNavigate: (MainTabs.Tab) -> Void

    @EnvironmentObject private var gate: AppGate
    @StateObject private var vault = VaultStore.shared
    @State private var blockedToday: Int = 0
    @State private var blockedWeek: Int = 0
    @State private var showHonestLimitsSheet = false

    private var protectionActive: Bool { !vault.contacts.isEmpty }

    // Mockup target shows "12 / 8 / 42" populated numbers. These fall back to zero when
    // there is no live data, so the empty-state still renders cleanly.
    private var trustedCount: Int { vault.contacts.count }

    var body: some View {
        ScrollView(.vertical, showsIndicators: false) {
            VStack(alignment: .leading, spacing: 18) {
                CinematicHeader(
                    name: nil,
                    onSettings: { onNavigate(.settings) }
                )
                ProtectionActiveCard(isActive: protectionActive)
                statsRow
                quickActionsSection
                HonestLimitsCallout(onTap: { showHonestLimitsSheet = true })
            }
            .padding(.horizontal, 18)
            .padding(.bottom, 24)
        }
        .background(Color.appScreenBackground.ignoresSafeArea())
        .navigationBarHidden(true)
        .task {
            await vault.reload()
            refreshStats()
        }
        .sheet(isPresented: $showHonestLimitsSheet) {
            NavigationStack {
                HonestLimitsSheet()
            }
        }
    }

    private var statsRow: some View {
        HStack(spacing: 10) {
            StatTile(icon: "person.2.fill",
                     tint: Color.appAccent,
                     value: trustedCount,
                     label: "Trusted\nContacts")
            StatTile(icon: "nosign",
                     tint: Color.appDanger,
                     value: blockedToday,
                     label: "Blocked\nToday")
            StatTile(icon: "chart.bar.fill",
                     tint: Color.appBrightCyan,
                     value: blockedWeek,
                     label: "Blocked\nThis Week")
        }
    }

    private var quickActionsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Quick Actions")
                .font(.system(size: 22, weight: .bold, design: .rounded))
                .foregroundStyle(Color.appPrimaryText)

            HStack(spacing: 10) {
                QuickActionCard(
                    icon: "person.badge.plus.fill",
                    title: "Add Contact",
                    subtitle: "Add to trusted list",
                    tint: Color.appAccent,
                    action: { onNavigate(.contacts) }
                )
                QuickActionCard(
                    icon: "phone.down.fill",
                    title: "Block Number",
                    subtitle: "Block unwanted calls & messages",
                    tint: Color.appDanger,
                    action: { onNavigate(.blocked) }
                )
                QuickActionCard(
                    icon: "doc.text.fill",
                    title: "View Log",
                    subtitle: "See recent blocked activity",
                    tint: Color.appBrightCyan,
                    action: { onNavigate(.log) }
                )
            }
        }
    }

    private func refreshStats() {
        let all = BlockListStore.shared.allRecent()
        let cal = Calendar.current
        let startOfToday = cal.startOfDay(for: Date())
        let startOfWeek = cal.date(byAdding: .day, value: -6, to: startOfToday) ?? startOfToday
        blockedToday = all.filter { $0.addedAt >= startOfToday }.count
        blockedWeek  = all.filter { $0.addedAt >= startOfWeek }.count
    }
}

// MARK: - Cinematic header (lakescape backdrop + wordmark + settings + greeting)

private struct CinematicHeader: View {
    let name: String?
    let onSettings: () -> Void

    private var timeOfDay: String {
        let h = Calendar.current.component(.hour, from: Date())
        if h < 12 { return "Good morning" }
        if h < 18 { return "Good afternoon" }
        return "Good evening"
    }

    var body: some View {
        ZStack(alignment: .topLeading) {
            // Lakescape backdrop (reuse SplashHero asset). The SplashHero PNG has the
            // "OnlyTrusted" wordmark + tagline baked into its middle band, so we scale
            // the image larger than the header frame and anchor to the TOP so only the
            // shield + mountain-ring section shows — the baked wordmark lives off-screen
            // below our clipped frame. A heavier darker gradient on top preserves text
            // legibility for the native greeting block.
            GeometryReader { proxy in
                Image("SplashHero")
                    .resizable()
                    .aspectRatio(contentMode: .fill)
                    .frame(width: proxy.size.width * 1.4,
                           height: proxy.size.width * 1.4 * (1672.0 / 941.0),
                           alignment: .top)
                    .offset(x: -proxy.size.width * 0.2, y: -proxy.size.height * 0.12)
                    .frame(width: proxy.size.width, height: proxy.size.height, alignment: .top)
                    .clipped()
                    .overlay {
                        LinearGradient(
                            colors: [
                                Color.appScreenBackground.opacity(0.12),
                                Color.appScreenBackground.opacity(0.35),
                                Color.appScreenBackground.opacity(0.95)
                            ],
                            startPoint: .top,
                            endPoint: .bottom
                        )
                    }
            }
            .frame(height: 240)
            .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))

            // Top row: wordmark + settings gear
            HStack(alignment: .top) {
                HStack(spacing: 0) {
                    Text("Only").foregroundStyle(.white)
                    Text("Trusted").foregroundStyle(Color.appAccent)
                }
                .font(.system(size: 24, weight: .bold, design: .rounded))

                Spacer()

                Button(action: onSettings) {
                    Image(systemName: "gearshape.fill")
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundStyle(Color.white)
                        .frame(width: 40, height: 40)
                        .background(
                            Circle()
                                .fill(Color.white.opacity(0.08))
                                .overlay(Circle().stroke(Color.appBrightCyan.opacity(0.4), lineWidth: 1))
                        )
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Settings")
            }
            .padding(.horizontal, 16)
            .padding(.top, 14)

            // Greeting block anchored to bottom-left of the header area
            VStack(alignment: .leading, spacing: 2) {
                Spacer()
                Text("\(timeOfDay),")
                    .font(.system(size: 28, weight: .bold, design: .rounded))
                    .foregroundStyle(.white)
                HStack(spacing: 8) {
                    Text(name ?? "Welcome")
                        .font(.system(size: 28, weight: .bold, design: .rounded))
                        .foregroundStyle(Color.appAccent)
                    Text("👋")
                        .font(.system(size: 24))
                }
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 18)
            .frame(maxWidth: .infinity, alignment: .leading)
            .frame(height: 240, alignment: .bottomLeading)
        }
        .frame(height: 240)
    }
}

// MARK: - Protection active card

private struct ProtectionActiveCard: View {
    let isActive: Bool

    var body: some View {
        HStack(spacing: 16) {
            // Neon shield badge — thick glowing cyan ring with heavy bloom + bright white
            // shield in the center. Previous version used a thin ring with low alpha glow;
            // this matches the "neon" reference.
            ZStack {
                Circle()
                    .stroke(Color.appBrightCyan.opacity(isActive ? 1.0 : 0.35), lineWidth: 4)
                    .frame(width: 86, height: 86)
                    .shadow(color: Color.appBrightCyan.opacity(isActive ? 0.9 : 0),
                            radius: 14, x: 0, y: 0)
                    .shadow(color: Color.appBrightCyan.opacity(isActive ? 0.55 : 0),
                            radius: 28, x: 0, y: 0)
                Image(systemName: "checkmark.shield.fill")
                    .font(.system(size: 44, weight: .semibold))
                    .foregroundStyle(.white.opacity(0.92))
                    .shadow(color: Color.appBrightCyan.opacity(0.6), radius: 8, x: 0, y: 0)
                    .accessibilityLabel("Protection shield")
            }

            VStack(alignment: .leading, spacing: 4) {
                Text(isActive ? "Protection is active" : "Setup needed")
                    .font(.system(size: 20, weight: .bold, design: .rounded))
                    .foregroundStyle(Color.appAccent)
                Text(isActive
                     ? "Only trusted contacts can reach you."
                     : "Add trusted contacts to begin.")
                    .font(.subheadline)
                    .foregroundStyle(Color.appSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)
            }

            Spacer(minLength: 0)

            Image(systemName: "chevron.right")
                .font(.footnote.weight(.bold))
                .foregroundStyle(Color.appTertiaryText)
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .fill(Color.appElevatedSurface)
                .overlay(
                    RoundedRectangle(cornerRadius: 20, style: .continuous)
                        .stroke(Color.appAccent.opacity(isActive ? 0.4 : 0.15), lineWidth: 1)
                )
                .shadow(color: Color.appAccent.opacity(isActive ? 0.2 : 0), radius: 18, x: 0, y: 0)
        )
    }
}

// MARK: - Stat tile (icon chip + number + label, previous version)

private struct StatTile: View {
    let icon: String
    let tint: Color
    let value: Int
    let label: String

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .top) {
                Image(systemName: icon)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(tint)
                    .frame(width: 32, height: 32)
                    .background(tint.opacity(0.18), in: RoundedRectangle(cornerRadius: 8, style: .continuous))
                Spacer()
                Text("\(value)")
                    .font(.system(size: 28, weight: .bold, design: .rounded))
                    .foregroundStyle(tint)
                    .minimumScaleFactor(0.6)
            }
            Text(label)
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(Color.appSecondaryText)
                .fixedSize(horizontal: false, vertical: true)
                .lineLimit(2)
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(Color.appElevatedSurface)
        )
    }
}

// MARK: - Quick action card (icon chip + title + subtitle, previous version)

private struct QuickActionCard: View {
    let icon: String
    let title: String
    let subtitle: String
    let tint: Color
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 10) {
                Image(systemName: icon)
                    .font(.system(size: 22, weight: .semibold))
                    .foregroundStyle(tint)
                    .frame(width: 46, height: 46)
                    .background(tint.opacity(0.18), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                    .shadow(color: tint.opacity(0.4), radius: 10, x: 0, y: 0)

                Text(title)
                    .font(.system(size: 15, weight: .bold, design: .rounded))
                    .foregroundStyle(Color.appPrimaryText)
                    .multilineTextAlignment(.leading)

                Text(subtitle)
                    .font(.system(size: 12))
                    .foregroundStyle(Color.appSecondaryText)
                    .multilineTextAlignment(.leading)
                    .fixedSize(horizontal: false, vertical: true)
                    .lineLimit(2)
            }
            .padding(12)
            .frame(maxWidth: .infinity, minHeight: 150, alignment: .topLeading)
            .background(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .fill(Color.appElevatedSurface)
            )
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Honest Limits callout + sheet (restored)

private struct HonestLimitsCallout: View {
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(alignment: .top, spacing: 14) {
                Image(systemName: "exclamationmark.triangle.fill")
                    .font(.system(size: 22, weight: .semibold))
                    .foregroundStyle(Color.appWarning)
                    .frame(width: 48, height: 48)
                    .background(
                        Color.appWarning.opacity(0.18),
                        in: RoundedRectangle(cornerRadius: 12, style: .continuous)
                    )
                    .shadow(color: Color.appWarning.opacity(0.4), radius: 10)

                VStack(alignment: .leading, spacing: 4) {
                    Text("Honest Limits")
                        .font(.system(size: 17, weight: .bold, design: .rounded))
                        .foregroundStyle(Color.appWarning)
                    Text("This app cannot monitor other apps' microphone or camera use. Tap to see all platform limits.")
                        .font(.system(size: 13))
                        .foregroundStyle(Color.appSecondaryText)
                        .fixedSize(horizontal: false, vertical: true)
                }

                Spacer(minLength: 0)

                Image(systemName: "chevron.right")
                    .font(.footnote.weight(.bold))
                    .foregroundStyle(Color.appWarning.opacity(0.7))
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .fill(Color.appWarning.opacity(0.08))
                    .overlay(
                        RoundedRectangle(cornerRadius: 18, style: .continuous)
                            .stroke(Color.appWarning.opacity(0.5), lineWidth: 1)
                    )
            )
        }
        .buttonStyle(.plain)
    }
}

private struct HonestLimitsSheet: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                limitBullet("Monitor other apps' microphone or camera use.")
                limitBullet("Stop recordings made on another device.")
                limitBullet("Place watermarks over other apps on iOS.")
                limitBullet("Prevent carrier or operating-system tracking.")
                limitBullet("Guarantee that every unwanted contact will be blocked — some requires enabling Apple's system extensions in iOS Settings.")
            }
            .padding(20)
        }
        .background(Color.appScreenBackground.ignoresSafeArea())
        .navigationTitle("Honest Limits")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button("Done") { dismiss() }
                    .tint(Color.appAccent)
            }
        }
    }

    private func limitBullet(_ text: String) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 10) {
            Circle()
                .fill(Color.appWarning)
                .frame(width: 6, height: 6)
                .offset(y: -3)
            Text(text)
                .font(.subheadline)
                .foregroundStyle(Color.appPrimaryText)
                .fixedSize(horizontal: false, vertical: true)
        }
    }
}

