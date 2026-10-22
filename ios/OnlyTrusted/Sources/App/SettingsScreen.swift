// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import SwiftUI
import LocalAuthentication

struct SettingsScreen: View {
    @EnvironmentObject private var gate: AppGate
    @StateObject private var store = VaultStore.shared
    @StateObject private var sub = SubscriptionState.shared
    @StateObject private var duress = DuressState.shared
    @StateObject private var simswap = SIMSwapState.shared

    @State private var appLockToggle = false
    @State private var showLockDeniedAlert = false
    @State private var confirmWipe = false
    @State private var confirmPanic = false
    @State private var shareURL: URL?
    @State private var exportError: String?
    @State private var lastExport: (() throws -> URL)?
    @State private var showThemeSheet = false
    @State private var showDuressUpsell = false
    @State private var showDuressSetup = false
    @State private var showUpgradeSheet = false
    // SIM-Swap Alarm (Pro)
    @State private var showSimSwapUpsell = false
    @State private var showSimSwapSetup = false
    // Pro batch: Wangiri Protection + Safety Check-In
    @State private var showWangiriUpsell = false
    @State private var showWangiriSettings = false
    @State private var showCheckInUpsell = false
    @State private var showCheckInSettings = false
    // Permission Watchdog (Pro)
    @State private var showWatchdogUpsell = false
    @State private var showWatchdog = false
    // F2 Auto-lock inactivity
    @State private var autoLockToggle = false
    @State private var autoLockSeconds: Int = AutoLockPrefs.currentSeconds
    @State private var showAutoLockPicker = false
    // F8 Carrier spam report chooser
    @State private var showCarrierPicker = false

    private var canUseAuth: Bool {
        var err: NSError?
        return LAContext().canEvaluatePolicy(.deviceOwnerAuthentication, error: &err)
    }

    var body: some View {
        VStack(spacing: 0) {
            headerTitle
                .frame(maxWidth: .infinity, alignment: .leading)
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
                VStack(alignment: .leading, spacing: 24) {
                    sectionHeader("Protection")
                    protectionCard

                    sectionHeader("Premium")
                    premiumCard

                    sectionHeader("Appearance")
                    appearanceCard

                    sectionHeader("Security & data")
                    securityCard

                    sectionHeader("More")
                    moreCard

                    footerCard
                }
                .padding(.horizontal, 18)
                .padding(.top, 8)
                .padding(.bottom, 32)
            }
        }
        .appBackdrop()
        .navigationBarHidden(true)
        .onAppear {
            appLockToggle = gate.appLockEnabled
            autoLockSeconds = AutoLockPrefs.currentSeconds
            autoLockToggle = autoLockSeconds > 0
        }
        .sheet(isPresented: $showAutoLockPicker) {
            NavigationStack { AutoLockPickerSheet(seconds: $autoLockSeconds, onDone: { showAutoLockPicker = false }) }
        }
        .confirmationDialog("Carrier spam report", isPresented: $showCarrierPicker, titleVisibility: .visible) {
            ForEach(CarrierSpamReport.Carrier.allCases) { carrier in
                Button(carrier.rawValue) { CarrierSpamReport.open(carrier) }
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("Open the carrier app to report scam calls or texts. If the app isn't installed we'll take you to the App Store.")
        }
        .alert("Set up a passcode first", isPresented: $showLockDeniedAlert) {
            Button("OK", role: .cancel) {}
        } message: {
            Text("Set up a device passcode, Face ID, or Touch ID in the iOS Settings app first. Otherwise App Lock would permanently lock you out.")
        }
        .confirmationDialog("Panic lock now?", isPresented: $confirmPanic, titleVisibility: .visible) {
            Button("Lock app", role: .destructive) { gate.panic() }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("The app will lock immediately and require Face ID / Touch ID / passcode to reopen.")
        }
        .confirmationDialog("Wipe everything?", isPresented: $confirmWipe, titleVisibility: .visible) {
            Button("Wipe", role: .destructive) {
                try? store.wipe()
                try? BlockListStore.shared.wipe()
                EventStore.shared.wipe()
                EventStore.shared.append(type: SecurityEventRecord.Kind.wipe)
                UserDefaults.standard.removePersistentDomain(forName: Bundle.main.bundleIdentifier ?? "")
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This will delete every trusted contact, every blocked number, every event, and every setting stored by this app. It cannot be undone.")
        }
        .sheet(isPresented: $showThemeSheet) { ThemeSelectorSheet() }
        .sheet(isPresented: $showDuressSetup) {
            NavigationStack { DuressSetupScreen() }
        }
        .sheet(isPresented: $showDuressUpsell) {
            DuressUpsellSheet(
                onUpgrade: {
                    showDuressUpsell = false
                    showUpgradeSheet = true
                },
                onDismiss: { showDuressUpsell = false }
            )
            .presentationDetents([.medium])
        }
        .sheet(isPresented: $showSimSwapSetup) {
            NavigationStack { SIMSwapSetupScreen() }
        }
        .sheet(isPresented: $showSimSwapUpsell) {
            SIMSwapUpsellSheet(
                onUpgrade: {
                    showSimSwapUpsell = false
                    showUpgradeSheet = true
                },
                onDismiss: { showSimSwapUpsell = false }
            )
            .presentationDetents([.medium])
        }
        .sheet(isPresented: $showWangiriSettings) {
            NavigationStack { WangiriSettingsScreen() }
        }
        .sheet(isPresented: $showWangiriUpsell) {
            WangiriUpsellSheet(
                onUpgrade: {
                    showWangiriUpsell = false
                    showUpgradeSheet = true
                },
                onDismiss: { showWangiriUpsell = false }
            )
            .presentationDetents([.medium])
        }
        .sheet(isPresented: $showCheckInSettings) {
            NavigationStack { SafetyCheckInScreen() }
        }
        .sheet(isPresented: $showWatchdog) {
            NavigationStack { PermissionWatchdogScreen() }
        }
        .sheet(isPresented: $showWatchdogUpsell) {
            WatchdogUpsellSheet(
                onUpgrade: {
                    showWatchdogUpsell = false
                    showUpgradeSheet = true
                },
                onDismiss: { showWatchdogUpsell = false }
            )
            .presentationDetents([.medium])
        }
        .sheet(isPresented: $showCheckInUpsell) {
            CheckInUpsellSheet(
                onUpgrade: {
                    showCheckInUpsell = false
                    showUpgradeSheet = true
                },
                onDismiss: { showCheckInUpsell = false }
            )
            .presentationDetents([.medium])
        }
        .sheet(isPresented: $showUpgradeSheet) {
            NavigationStack { UpgradeToPremiumScreen() }
        }
        .sheet(item: Binding(get: { shareURL.map(IdentifiableURL.init) },
                             set: { shareURL = $0?.value })) { wrap in
            ShareSheet(activityItems: [wrap.value])
        }
        .fullScreenCover(isPresented: Binding(
            get: { exportError != nil },
            set: { if !$0 { exportError = nil } }
        )) {
            ErrorStateView(
                title: "Something went wrong",
                message: "We couldn't complete the export. " + (exportError ?? ""),
                onTryAgain: lastExport.map { work in { exportError = nil; runExport(work) } },
                onGoBack: { exportError = nil }
            )
        }
    }

    // MARK: - Header title

    private var headerTitle: some View {
        Text("Settings")
            .font(.system(size: 30, weight: .bold, design: .rounded))
            .foregroundStyle(Color.appPrimaryText)
            .padding(.top, 4)
    }


    // MARK: - Section header

    private func sectionHeader(_ title: String) -> some View {
        Text(title)
            .font(.system(size: 20, weight: .semibold, design: .rounded))
            .foregroundStyle(Color.appSecondaryText)
            .padding(.horizontal, 4)
            .padding(.top, 4)
    }

    // MARK: - Protection (mockup-aligned)

    private var protectionCard: some View {
        PremiumCard {
            VStack(spacing: 0) {
                NavigationLink { BlockListScreen() } label: {
                    SettingsNavRow(icon: "shield.lefthalf.filled", iconTint: Color.settingsBlue, label: "Blocking & Unknown")
                }
                SettingsDivider()
                NavigationLink { NotificationsScreen() } label: {
                    SettingsNavRow(icon: "bell.fill", iconTint: Color.settingsBlue, label: "Notifications")
                }
                SettingsDivider()
                NavigationLink { CallHandlingScreen() } label: {
                    SettingsNavRow(icon: "phone.fill", iconTint: Color.settingsBlue, label: "Call Handling")
                }
                SettingsDivider()
                NavigationLink { TextHandlingScreen() } label: {
                    SettingsNavRow(icon: "message.fill", iconTint: Color.settingsBlue, label: "Text Handling")
                }
            }
        }
    }

    // MARK: - Premium (new section)

    private var premiumCard: some View {
        PremiumCard {
            VStack(spacing: 0) {
                NavigationLink { ReportsScreen() } label: {
                    SettingsNavRow(icon: "chart.line.uptrend.xyaxis",
                                   iconTint: Color.settingsBlue,
                                   label: "Reports")
                }
                SettingsDivider()
                NavigationLink { BillingSubscriptionScreen() } label: {
                    SettingsNavRow(icon: "creditcard.fill",
                                   iconTint: Color.subGold,
                                   label: "Billing & Subscription")
                }
                SettingsDivider()
                NavigationLink { UpgradeToPremiumScreen() } label: {
                    SettingsNavRow(icon: "crown.fill",
                                   iconTint: Color.subGold,
                                   label: "Upgrade to Premium")
                }
            }
        }
    }

    // MARK: - Appearance (mockup-aligned)

    private var appearanceCard: some View {
        PremiumCard {
            VStack(spacing: 0) {
                Button { showThemeSheet = true } label: {
                    SettingsNavRow(icon: "paintpalette.fill", iconTint: Color.settingsPurple,
                                   label: "App Theme", value: "Dark (System)")
                }
                .buttonStyle(.plain)
                SettingsDivider()
                Button {
                    if let url = URL(string: UIApplication.openSettingsURLString) {
                        UIApplication.shared.open(url)
                    }
                } label: {
                    SettingsNavRow(icon: "globe", iconTint: Color.settingsPurple,
                                   label: "Language", value: "English")
                }
                .buttonStyle(.plain)
                SettingsDivider()
                Button {
                    if let url = URL(string: UIApplication.openSettingsURLString) {
                        UIApplication.shared.open(url)
                    }
                } label: {
                    SettingsNavRow(icon: "textformat.size", iconTint: Color.settingsPurple,
                                   label: "Text Size", value: "System")
                }
                .buttonStyle(.plain)
            }
        }
    }

    // MARK: - Security & data (preserved functionality not in mockup)

    private var securityCard: some View {
        PremiumCard {
            VStack(spacing: 0) {
                Toggle(isOn: $appLockToggle) {
                    HStack(spacing: 14) {
                        settingsIcon("lock.shield.fill", tint: Color.appAccent)
                        VStack(alignment: .leading, spacing: 2) {
                            Text("App Lock")
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(Color.appPrimaryText)
                            Text("Require biometric or passcode to open.")
                                .font(.caption)
                                .foregroundStyle(Color.appSecondaryText)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                }
                .tint(Color.appAccent)
                .padding(.horizontal, 14)
                .padding(.vertical, 12)
                .onChange(of: appLockToggle) { _, now in
                    if now && !canUseAuth {
                        appLockToggle = false
                        showLockDeniedAlert = true
                        return
                    }
                    gate.appLockEnabled = now
                }

                // F2 — Auto-lock after inactivity.
                SettingsDivider()
                Toggle(isOn: $autoLockToggle) {
                    HStack(spacing: 14) {
                        settingsIcon("clock.fill", tint: Color.appAccent)
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Auto-lock after inactivity")
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(Color.appPrimaryText)
                            Text(autoLockToggle
                                 ? "Re-lock after \(AutoLockPrefs.label(for: autoLockSeconds)) in the background."
                                 : "Only lock when you choose Panic lock.")
                                .font(.caption)
                                .foregroundStyle(Color.appSecondaryText)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                }
                .tint(Color.appAccent)
                .padding(.horizontal, 14)
                .padding(.vertical, 12)
                .onChange(of: autoLockToggle) { _, now in
                    if now {
                        if autoLockSeconds == 0 { autoLockSeconds = 60 }
                        AutoLockPrefs.setSeconds(autoLockSeconds)
                    } else {
                        autoLockSeconds = 0
                        AutoLockPrefs.setSeconds(0)
                    }
                }
                if autoLockToggle {
                    SettingsDivider()
                    Button { showAutoLockPicker = true } label: {
                        SettingsNavRow(icon: "timer", iconTint: Color.appAccent,
                                       label: "Inactivity window",
                                       value: AutoLockPrefs.label(for: autoLockSeconds))
                    }
                    .buttonStyle(.plain)
                }

                SettingsDivider()
                Button { runExport { try DataExporter.write() } } label: {
                    SettingsNavRow(icon: "square.and.arrow.up", iconTint: Color.appAccent,
                                   label: "Export my data as JSON", chevron: false)
                }
                .buttonStyle(.plain)
                // F6 — CSV export of events + contacts + blocked numbers.
                SettingsDivider()
                Button { runExport { try CSVExporter.write() } } label: {
                    SettingsNavRow(icon: "tablecells", iconTint: Color.appAccent,
                                   label: "Export as CSV", chevron: false)
                }
                .buttonStyle(.plain)
                SettingsDivider()
                Button { runExport { try EvidenceExporter.write() } } label: {
                    SettingsNavRow(icon: "doc.badge.plus", iconTint: Color.appAccent,
                                   label: "Export evidence pack", chevron: false)
                }
                .buttonStyle(.plain)
                // F8 — Carrier spam report shortcut.
                SettingsDivider()
                Button { showCarrierPicker = true } label: {
                    SettingsNavRow(icon: "megaphone.fill", iconTint: Color.appAccent,
                                   label: "Carrier spam report")
                }
                .buttonStyle(.plain)
                SettingsDivider()
                Button { confirmPanic = true } label: {
                    SettingsNavRow(icon: "lock.fill", iconTint: Color.appWarning,
                                   label: "Panic lock", chevron: false, labelTint: Color.appWarning)
                }
                .buttonStyle(.plain)
                SettingsDivider()
                Button { confirmWipe = true } label: {
                    SettingsNavRow(icon: "trash.fill", iconTint: Color.appDanger,
                                   label: "Wipe everything", chevron: false, labelTint: Color.appDanger)
                }
                .buttonStyle(.plain)

                SettingsDivider()
                Button {
                    if sub.hasHighPremium {
                        showDuressSetup = true
                    } else {
                        showDuressUpsell = true
                    }
                } label: {
                    SettingsNavRow(
                        icon: duress.isEnabled ? "exclamationmark.shield.fill" : "exclamationmark.shield",
                        iconTint: duress.isEnabled ? Color.appSuccess : Color.subGold,
                        label: "Duress Mode (Pro)",
                        value: duress.isEnabled ? "On" : nil
                    )
                }
                .buttonStyle(.plain)

                SettingsDivider()
                Button {
                    if sub.hasHighPremium {
                        showSimSwapSetup = true
                    } else {
                        showSimSwapUpsell = true
                    }
                } label: {
                    SettingsNavRow(
                        icon: "simcard.fill",
                        iconTint: simswap.isEnabled ? Color.appSuccess : Color.subGold,
                        label: "SIM-Swap Alarm (Pro)",
                        value: simswap.isEnabled ? "On" : nil
                    )
                }
                .buttonStyle(.plain)

                // Wangiri Protection (Pro)
                SettingsDivider()
                Button {
                    if sub.hasHighPremium {
                        showWangiriSettings = true
                    } else {
                        showWangiriUpsell = true
                    }
                } label: {
                    SettingsNavRow(
                        icon: "phone.down.waveform.fill",
                        iconTint: sub.hasHighPremium ? Color.appAccent : Color.subGold,
                        label: "Wangiri Protection (Pro)"
                    )
                }
                .buttonStyle(.plain)

                // Safety Check-In (Pro)
                SettingsDivider()
                Button {
                    if sub.hasHighPremium {
                        showCheckInSettings = true
                    } else {
                        showCheckInUpsell = true
                    }
                } label: {
                    SettingsNavRow(
                        icon: "location.fill.viewfinder",
                        iconTint: sub.hasHighPremium ? Color.appAccent : Color.subGold,
                        label: "Safety Check-In (Pro)"
                    )
                }
                .buttonStyle(.plain)

                // App Permission Watchdog (Pro)
                SettingsDivider()
                Button {
                    if sub.hasHighPremium {
                        showWatchdog = true
                    } else {
                        showWatchdogUpsell = true
                    }
                } label: {
                    SettingsNavRow(
                        icon: "lock.shield.fill",
                        iconTint: sub.hasHighPremium ? Color.appAccent : Color.subGold,
                        label: "App Permission Watchdog (Pro)"
                    )
                }
                .buttonStyle(.plain)
            }
        }
    }

    // MARK: - More

    private var moreCard: some View {
        PremiumCard {
            VStack(spacing: 0) {
                NavigationLink { LegalScreen() } label: {
                    SettingsNavRow(icon: "doc.text.fill", iconTint: Color.appAccent,
                                   label: "Privacy, terms, and honest claims")
                }
                SettingsDivider()
                NavigationLink { AboutScreen() } label: {
                    SettingsNavRow(icon: "info.circle.fill", iconTint: Color.appAccent,
                                   label: "About OnlyTrusted")
                }
            }
        }
    }

    // MARK: - Footer

    private var footerCard: some View {
        VStack(spacing: 4) {
            Text("OnlyTrusted")
                .font(.footnote.weight(.semibold))
                .foregroundStyle(Color.appSecondaryText)
            Text("© 2026 VanPaulTek Inc · GNU AGPL-3.0")
                .font(.caption2)
                .foregroundStyle(Color.appTertiaryText)
            Text("admin@vanpaultek.com")
                .font(.caption2)
                .foregroundStyle(Color.appTertiaryText)
        }
        .frame(maxWidth: .infinity, alignment: .center)
        .padding(.top, 8)
    }

    private func settingsIcon(_ name: String, tint: Color) -> some View {
        Image(systemName: name)
            .font(.body.weight(.semibold))
            .foregroundStyle(tint)
            .frame(width: 28, height: 28)
            .decorativeAccessibility()
    }

    private func runExport(_ work: @escaping () throws -> URL) {
        lastExport = work
        do { shareURL = try work() }
        catch { exportError = String(describing: error) }
    }
}

// MARK: - Row components

struct SettingsNavRow: View {
    let icon: String
    let iconTint: Color
    let label: String
    var value: String? = nil
    var chevron: Bool = true
    var labelTint: Color = Color.appPrimaryText
    /// When true, render the icon as a filled solid-color chip (rounded square with
    /// white icon on tinted gradient) per the new mockup. When false, keeps the
    /// legacy small-icon look used by Security & data destructive rows.
    var chipStyle: Bool = true

    var body: some View {
        HStack(spacing: 14) {
            if chipStyle {
                ZStack {
                    RoundedRectangle(cornerRadius: 11, style: .continuous)
                        .fill(
                            LinearGradient(
                                colors: [iconTint, iconTint.opacity(0.75)],
                                startPoint: .topLeading, endPoint: .bottomTrailing
                            )
                        )
                        .shadow(color: iconTint.opacity(0.5), radius: 8, x: 0, y: 2)
                    Image(systemName: icon)
                        .font(.system(size: 20, weight: .semibold))
                        .foregroundStyle(.white)
                }
                .frame(width: 44, height: 44)
                .decorativeAccessibility()
            } else {
                Image(systemName: icon)
                    .font(.body.weight(.semibold))
                    .foregroundStyle(iconTint)
                    .frame(width: 28, height: 28)
                    .decorativeAccessibility()
            }
            Text(label)
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(labelTint)
            Spacer()
            if let value {
                Text(value)
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(Color.appBrightCyan)
            }
            if chevron {
                Image(systemName: "chevron.right")
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(Color.appTertiaryText)
                    .decorativeAccessibility()
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
    }
}

struct SettingsDivider: View {
    var body: some View {
        Divider()
            .background(Color.appDivider.opacity(0.5))
            .padding(.leading, 72)
    }
}

/// Blue and purple used for the Settings icon chips (match the neon mockup).
extension Color {
    static let settingsBlue   = Color(red:  34.0/255.0, green: 102.0/255.0, blue: 240.0/255.0)
    static let settingsPurple = Color(red: 139.0/255.0, green:  92.0/255.0, blue: 246.0/255.0)
}

// MARK: - Placeholder sub-screens (Call Handling / Text Handling / Theme)

struct CallHandlingScreen: View {
    @State private var mode: CallMode = .silence

    enum CallMode: String, CaseIterable, Identifiable {
        case silence = "Silence (recommended)"
        case voicemail = "Send to voicemail"
        case block = "Block completely"
        var id: String { rawValue }
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                PremiumCard {
                    VStack(alignment: .leading, spacing: 12) {
                        CardHeader(icon: "phone.fill", title: "How to handle unknown calls")
                        Text("Choose what happens when someone not in your trusted contacts tries to call. OnlyTrusted applies this via the Call Directory extension — enable it in iOS Settings → Phone → Call Blocking & Identification.")
                            .font(.footnote)
                            .foregroundStyle(Color.appSecondaryText)
                            .fixedSize(horizontal: false, vertical: true)
                        ForEach(CallMode.allCases) { option in
                            RadioRow(label: option.rawValue, isSelected: mode == option) {
                                mode = option
                            }
                        }
                    }
                }
            }
            .padding(20)
        }
        .appBackdrop()
        .navigationTitle("Call Handling")
        .navigationBarTitleDisplayMode(.inline)
    }
}

struct TextHandlingScreen: View {
    @State private var filterUnknown = true
    @State private var promoteSpam = true

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                PremiumCard {
                    VStack(alignment: .leading, spacing: 12) {
                        CardHeader(icon: "message.fill", title: "How to handle unknown texts")
                        Text("The SMS filter extension can route unknown senders to the Unknown & Junk tab in Messages, keeping your main inbox for trusted contacts.")
                            .font(.footnote)
                            .foregroundStyle(Color.appSecondaryText)
                            .fixedSize(horizontal: false, vertical: true)
                        Toggle("Filter texts from unknown senders", isOn: $filterUnknown)
                            .tint(Color.appAccent)
                        Toggle("Promote suspected spam to the Junk tab", isOn: $promoteSpam)
                            .tint(Color.appAccent)
                    }
                }
            }
            .padding(20)
        }
        .appBackdrop()
        .navigationTitle("Text Handling")
        .navigationBarTitleDisplayMode(.inline)
    }
}

struct RadioRow: View {
    let label: String
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Image(systemName: isSelected ? "largecircle.fill.circle" : "circle")
                    .font(.title3)
                    .foregroundStyle(isSelected ? Color.appAccent : Color.appTertiaryText)
                Text(label)
                    .font(.subheadline)
                    .foregroundStyle(Color.appPrimaryText)
                Spacer()
            }
            .padding(.vertical, 8)
        }
        .buttonStyle(.plain)
    }
}

struct ThemeSelectorSheet: View {
    @Environment(\.dismiss) private var dismiss

    enum Theme: String, CaseIterable, Identifiable {
        case system = "System"
        case dark = "Dark"
        case light = "Light"
        var id: String { rawValue }
    }

    @State private var choice: Theme = .system

    var body: some View {
        NavigationStack {
            VStack(spacing: 16) {
                PremiumCard {
                    VStack(alignment: .leading, spacing: 12) {
                        CardHeader(icon: "paintpalette.fill", title: "App theme")
                        Text("OnlyTrusted is designed dark-first. 'System' follows your device setting; 'Dark' and 'Light' override it just for this app.")
                            .font(.footnote)
                            .foregroundStyle(Color.appSecondaryText)
                            .fixedSize(horizontal: false, vertical: true)
                        ForEach(Theme.allCases) { opt in
                            RadioRow(label: opt.rawValue, isSelected: choice == opt) { choice = opt }
                        }
                    }
                }
                Spacer()
            }
            .padding(20)
            .appBackdrop()
            .navigationTitle("App theme")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") { dismiss() }
                        .tint(Color.appAccent)
                }
            }
        }
    }
}

// MARK: - Share sheet wrapper (kept from previous)

private struct IdentifiableURL: Identifiable {
    let value: URL
    var id: URL { value }
    init(_ value: URL) { self.value = value }
}

/// Pro upsell sheet for the App Permission Watchdog.
struct WatchdogUpsellSheet: View {
    let onUpgrade: () -> Void
    let onDismiss: () -> Void

    var body: some View {
        VStack(spacing: 20) {
            Image(systemName: "lock.shield.fill")
                .font(.system(size: 46, weight: .semibold))
                .foregroundStyle(Color.subGold)
                .padding(.top, 28)
            Text("Permission Watchdog")
                .font(.title2.weight(.bold))
                .foregroundStyle(Color.appPrimaryText)
            Text("Jump into Apple's App Privacy Report and review exactly which permissions OnlyTrusted holds — with the reason we don't ask for the ones we skip. Included in Pro.")
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

/// Pro upsell sheet shown when a non-Pro user taps "Duress Mode (Pro)". Explains the feature
/// in plain language and sends the user to the Upgrade screen.
struct DuressUpsellSheet: View {
    let onUpgrade: () -> Void
    let onDismiss: () -> Void

    var body: some View {
        VStack(spacing: 20) {
            Image(systemName: "exclamationmark.shield.fill")
                .font(.system(size: 46, weight: .semibold))
                .foregroundStyle(Color.subGold)
                .padding(.top, 28)
            Text("Duress Mode")
                .font(.title2.weight(.bold))
                .foregroundStyle(Color.appPrimaryText)
            Text("A secondary PIN that unlocks a decoy vault and silently alerts a trusted contact. Included in Pro.")
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

// MARK: - F2 Auto-lock inactivity window picker

struct AutoLockPickerSheet: View {
    @Binding var seconds: Int
    let onDone: () -> Void

    var body: some View {
        VStack(spacing: 16) {
            PremiumCard {
                VStack(alignment: .leading, spacing: 12) {
                    CardHeader(icon: "clock.fill", title: "Auto-lock after")
                    Text("Re-arm App Lock after this long in the background. Panic lock still locks immediately regardless of this setting.")
                        .font(.footnote)
                        .foregroundStyle(Color.appSecondaryText)
                        .fixedSize(horizontal: false, vertical: true)
                    ForEach(AutoLockPrefs.options, id: \.self) { opt in
                        RadioRow(label: AutoLockPrefs.label(for: opt), isSelected: seconds == opt) {
                            seconds = opt
                            AutoLockPrefs.setSeconds(opt)
                        }
                    }
                }
            }
            Spacer()
        }
        .padding(20)
        .appBackdrop()
        .navigationTitle("Auto-lock")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button("Done", action: onDone)
                    .tint(Color.appAccent)
            }
        }
    }
}
