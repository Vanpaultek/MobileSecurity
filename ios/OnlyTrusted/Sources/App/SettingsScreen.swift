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
    // Tracker Finder (Pro)
    @State private var showTrackerFinderUpsell = false
    @State private var showTrackerFinder = false
    // Smart Link Guard (Pro)
    @State private var showSmartLinkGuardUpsell = false
    @State private var showSmartLinkGuard = false
    // Hardware Security Key (Elite)
    @State private var showHardwareKeyUpsell = false
    @State private var showHardwareKeySetup = false
    @State private var hardwareKeyGateError: String?
    // Border Mode (Elite)
    @State private var showBorderModeUpsell = false
    @State private var showBorderModeSetup = false
    @StateObject private var borderMode = BorderModeState.shared
    // Dead-Man Wipe (Elite)
    @State private var showDeadmanWipeUpsell = false
    @State private var showDeadmanWipeSetup = false
    // Geo-fence Auto-Lock (Elite)
    @State private var showGeofenceUpsell = false
    @State private var showGeofenceSetup = false
    @StateObject private var geofence = GeofenceState.shared
    // Face-Change Shoulder-Surfing Lock (Elite)
    @State private var showFaceLockUpsell = false
    @State private var showFaceLockSetup = false
    @StateObject private var faceLock = FaceLockState.shared
    // Encrypted Vault Backup (Elite)
    @State private var showEncryptedBackupUpsell = false
    @State private var showEncryptedBackupSetup = false
    @State private var showBackupRestoreUpsell = false
    @State private var showBackupRestore = false
    @StateObject private var deadman = DeadmanWipeState.shared
    // Shamir Split (Elite) — vault master-key split over BLE.
    @State private var showShamirUpsell = false
    @State private var showShamirSetup = false
    // Trusted Witness (Elite) — proximity-approved sensitive actions.
    @State private var showTrustedWitnessUpsell = false
    @State private var showTrustedWitnessSetup = false
    @State private var trustedWitnessError: String?
    @StateObject private var trustedWitness = TrustedWitnessState.shared
    // Steganographic Vault (Elite) — hides the vault inside a decoy PNG/PDF.
    @State private var showStegoVaultUpsell = false
    @State private var showStegoVaultSetup = false
    // Signed Audit Log (Elite) — hardware-signed, merkle-anchored event log.
    @State private var showSignedLogUpsell = false
    @State private var showSignedLogSetup = false
    @StateObject private var signedLog = SignedLogStore.shared
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
                // Elite-tier gate stack: Trusted Witness (if armed) first, then the Hardware
                // Security Key tap (if enrolled). Both gates are opt-in — if the user hasn't
                // enabled them this collapses to the pre-Elite behaviour.
                TrustedWitnessGate.require(
                    action: .wipeEverything,
                    reason: "Have your witness approve the wipe."
                ) { tw in
                    switch tw {
                    case .success:
                        HardwareKeyGate.requireAssertion(
                            reason: "Confirm wipe with your security key."
                        ) { outcome in
                            switch outcome {
                            case .success:
                                try? store.wipe()
                                try? BlockListStore.shared.wipe()
                                EventStore.shared.wipe()
                                EventStore.shared.append(type: SecurityEventRecord.Kind.wipe)
                                HardwareKeyState.shared.clearAll()
                                UserDefaults.standard.removePersistentDomain(forName: Bundle.main.bundleIdentifier ?? "")
                            case .cancelled:
                                hardwareKeyGateError = "Wipe cancelled — security key tap missing."
                            case .failed(let msg):
                                hardwareKeyGateError = "Hardware key required: \(msg)"
                            }
                        }
                    case .cancelled:
                        trustedWitnessError = "Wipe cancelled — witness did not approve."
                    case .failed(let msg):
                        trustedWitnessError = "Witness required: \(msg)"
                    }
                }
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This will delete every trusted contact, every blocked number, every event, and every setting stored by this app. It cannot be undone.")
        }
        .alert("Hardware key required", isPresented: Binding(
            get: { hardwareKeyGateError != nil },
            set: { if !$0 { hardwareKeyGateError = nil } }
        )) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(hardwareKeyGateError ?? "")
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
        .sheet(isPresented: $showTrackerFinder) {
            NavigationStack { TrackerFinderScreen() }
        }
        .sheet(isPresented: $showTrackerFinderUpsell) {
            TrackerFinderUpsellSheet(
                onUpgrade: {
                    showTrackerFinderUpsell = false
                    showUpgradeSheet = true
                },
                onDismiss: { showTrackerFinderUpsell = false }
            )
            .presentationDetents([.medium])
        }
        .sheet(isPresented: $showSmartLinkGuard) {
            NavigationStack { SmartLinkGuardScreen() }
        }
        .sheet(isPresented: $showSmartLinkGuardUpsell) {
            SmartLinkGuardUpsellSheet(
                onUpgrade: {
                    showSmartLinkGuardUpsell = false
                    showUpgradeSheet = true
                },
                onDismiss: { showSmartLinkGuardUpsell = false }
            )
            .presentationDetents([.medium])
        }
        .sheet(isPresented: $showHardwareKeySetup) {
            NavigationStack { HardwareKeySetupScreen() }
        }
        .sheet(isPresented: $showHardwareKeyUpsell) {
            HardwareKeyUpsellSheet(
                onUpgrade: {
                    showHardwareKeyUpsell = false
                    showUpgradeSheet = true
                },
                onDismiss: { showHardwareKeyUpsell = false }
            )
            .presentationDetents([.medium])
        }
        // Border Mode (Elite)
        .sheet(isPresented: $showBorderModeSetup) {
            NavigationStack { BorderModeSetupScreen() }
        }
        .sheet(isPresented: $showBorderModeUpsell) {
            BorderModeUpsellSheet(
                onUpgrade: {
                    showBorderModeUpsell = false
                    showUpgradeSheet = true
                },
                onDismiss: { showBorderModeUpsell = false }
            )
            .presentationDetents([.medium])
        }
        // Dead-Man Wipe (Elite)
        .sheet(isPresented: $showDeadmanWipeSetup) {
            NavigationStack { DeadmanWipeSetupScreen() }
        }
        .sheet(isPresented: $showDeadmanWipeUpsell) {
            DeadmanWipeUpsellSheet(
                onUpgrade: {
                    showDeadmanWipeUpsell = false
                    showUpgradeSheet = true
                },
                onDismiss: { showDeadmanWipeUpsell = false }
            )
            .presentationDetents([.medium])
        }
        // Geo-fence Auto-Lock (Elite)
        .sheet(isPresented: $showGeofenceSetup) {
            NavigationStack { GeofenceSetupScreen() }
        }
        .sheet(isPresented: $showGeofenceUpsell) {
            GeofenceUpsellSheet(
                onUpgrade: {
                    showGeofenceUpsell = false
                    showUpgradeSheet = true
                },
                onDismiss: { showGeofenceUpsell = false }
            )
            .presentationDetents([.medium])
        }
        // Face-Change Shoulder-Surfing Lock (Elite)
        .sheet(isPresented: $showFaceLockSetup) {
            NavigationStack { FaceLockSetupScreen() }
        }
        .sheet(isPresented: $showFaceLockUpsell) {
            FaceLockUpsellSheet(
                onUpgrade: {
                    showFaceLockUpsell = false
                    showUpgradeSheet = true
                },
                onDismiss: { showFaceLockUpsell = false }
            )
            .presentationDetents([.medium])
        }
        // Encrypted Backup (Elite)
        .sheet(isPresented: $showEncryptedBackupSetup) {
            NavigationStack { EncryptedBackupScreen() }
        }
        .sheet(isPresented: $showEncryptedBackupUpsell) {
            EncryptedBackupUpsellSheet(
                onUpgrade: {
                    showEncryptedBackupUpsell = false
                    showUpgradeSheet = true
                },
                onDismiss: { showEncryptedBackupUpsell = false }
            )
            .presentationDetents([.medium])
        }
        // Backup Restore (Elite)
        .sheet(isPresented: $showBackupRestore) {
            NavigationStack { BackupRestoreScreen() }
        }
        .sheet(isPresented: $showBackupRestoreUpsell) {
            BackupRestoreUpsellSheet(
                onUpgrade: {
                    showBackupRestoreUpsell = false
                    showUpgradeSheet = true
                },
                onDismiss: { showBackupRestoreUpsell = false }
            )
            .presentationDetents([.medium])
        }
        // Shamir Split (Elite)
        .sheet(isPresented: $showShamirSetup) {
            NavigationStack { ShamirSetupScreen() }
        }
        .sheet(isPresented: $showShamirUpsell) {
            ShamirUpsellSheet(
                onUpgrade: {
                    showShamirUpsell = false
                    showUpgradeSheet = true
                },
                onDismiss: { showShamirUpsell = false }
            )
            .presentationDetents([.medium])
        }
        // Trusted Witness (Elite)
        .sheet(isPresented: $showTrustedWitnessSetup) {
            NavigationStack { TrustedWitnessSetupScreen() }
        }
        .sheet(isPresented: $showTrustedWitnessUpsell) {
            TrustedWitnessUpsellSheet(
                onUpgrade: {
                    showTrustedWitnessUpsell = false
                    showUpgradeSheet = true
                },
                onDismiss: { showTrustedWitnessUpsell = false }
            )
            .presentationDetents([.medium])
        }
        // Steganographic Vault (Elite)
        .sheet(isPresented: $showStegoVaultSetup) {
            NavigationStack { StegoVaultScreen() }
        }
        .sheet(isPresented: $showStegoVaultUpsell) {
            StegoVaultUpsellSheet(
                onUpgrade: {
                    showStegoVaultUpsell = false
                    showUpgradeSheet = true
                },
                onDismiss: { showStegoVaultUpsell = false }
            )
            .presentationDetents([.medium])
        }
        // Signed Audit Log (Elite)
        .sheet(isPresented: $showSignedLogSetup) {
            NavigationStack { SignedLogScreen() }
        }
        .sheet(isPresented: $showSignedLogUpsell) {
            SignedLogUpsellSheet(
                onUpgrade: {
                    showSignedLogUpsell = false
                    showUpgradeSheet = true
                },
                onDismiss: { showSignedLogUpsell = false }
            )
            .presentationDetents([.medium])
        }
        .sheet(isPresented: Binding(
            get: { TrustedWitnessApprovalPresenter.shared.isActive },
            set: { _ in }
        )) {
            TrustedWitnessApprovalOverlay()
        }
        .alert("Trusted Witness required", isPresented: Binding(
            get: { trustedWitnessError != nil },
            set: { if !$0 { trustedWitnessError = nil } }
        )) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(trustedWitnessError ?? "")
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

                // Tracker Finder (Pro) — scan nearby BLE for AirTag / Tile / SmartTag
                // and flag persistent followers.
                SettingsDivider()
                Button {
                    if sub.hasHighPremium {
                        showTrackerFinder = true
                    } else {
                        showTrackerFinderUpsell = true
                    }
                } label: {
                    SettingsNavRow(
                        icon: "location.magnifyingglass",
                        iconTint: sub.hasHighPremium ? Color.appAccent : Color.subGold,
                        label: "Tracker Finder (Pro)"
                    )
                }
                .buttonStyle(.plain)

                // Smart Link Guard (Pro) — on-device URL inspection for QR codes +
                // links shared via the system share sheet.
                SettingsDivider()
                Button {
                    if sub.hasHighPremium {
                        showSmartLinkGuard = true
                    } else {
                        showSmartLinkGuardUpsell = true
                    }
                } label: {
                    SettingsNavRow(
                        icon: "shield.lefthalf.filled",
                        iconTint: sub.hasHighPremium ? Color.appAccent : Color.subGold,
                        label: "Smart Link Guard (Pro)"
                    )
                }
                .buttonStyle(.plain)

                // Border Mode (Elite) — one-tap preset for high-risk crossings. Placed at
                // the top of Elite rows per the brief. Toggles every defensive layer at once
                // (Dead-Man Wipe 7d, Duress active, biometric off, screenshot + shield on,
                // auto-lock 30s, cover identity swap).
                SettingsDivider()
                Button {
                    if sub.hasElite {
                        showBorderModeSetup = true
                    } else {
                        showBorderModeUpsell = true
                    }
                } label: {
                    SettingsNavRow(
                        icon: "airplane.departure",
                        iconTint: borderMode.isArmed ? Color.appDanger : (sub.hasElite ? Color.appAccent : Color.subGold),
                        label: "Border Mode (Elite)",
                        value: borderMode.isArmed ? "Armed" : nil
                    )
                }
                .buttonStyle(.plain)

                // Hardware Security Key (Elite) — FIDO2 YubiKey / Titan / SoloKey. Elite tier
                // gates the most dangerous actions (Wipe everything, Duress disarm, encrypted
                // backup export, Dead-Man Wipe disable) behind a physical key tap.
                SettingsDivider()
                Button {
                    if sub.hasElite {
                        showHardwareKeySetup = true
                    } else {
                        showHardwareKeyUpsell = true
                    }
                } label: {
                    SettingsNavRow(
                        icon: "key.horizontal.fill",
                        iconTint: sub.hasElite ? Color.appSuccess : Color.subGold,
                        label: "Hardware Security Key (Elite)",
                        value: sub.hasElite && HardwareKeyState.shared.hasEnrolledKey ? "On" : nil
                    )
                }
                .buttonStyle(.plain)

                // Face-Change Shoulder-Surfing Lock (Elite) — front camera watches for faces
                // while sensitive screens are open. Blurs when the owner walks away; locks
                // instantly if a stranger or additional face appears.
                SettingsDivider()
                Button {
                    if sub.hasElite {
                        showFaceLockSetup = true
                    } else {
                        showFaceLockUpsell = true
                    }
                } label: {
                    SettingsNavRow(
                        icon: "face.dashed",
                        iconTint: sub.hasElite && faceLock.isEnrolled ? Color.appSuccess : (sub.hasElite ? Color.appAccent : Color.subGold),
                        label: "Face-Change Lock (Elite)",
                        value: sub.hasElite && faceLock.isEnrolled ? "On" : nil
                    )
                }
                .buttonStyle(.plain)

                // Geo-fence Auto-Lock (Elite) — up to 5 trusted locations. The device
                // auto-locks and (optionally) sends an SMS alert when it leaves every
                // trusted zone. iOS requires "Always" location authorization to fire the
                // background region monitor; the setup screen surfaces that caveat.
                SettingsDivider()
                Button {
                    if sub.hasElite {
                        showGeofenceSetup = true
                    } else {
                        showGeofenceUpsell = true
                    }
                } label: {
                    SettingsNavRow(
                        icon: "location.circle.fill",
                        iconTint: geofence.isEnabled ? Color.appSuccess : (sub.hasElite ? Color.appAccent : Color.subGold),
                        label: "Geo-fence Auto-Lock (Elite)",
                        value: geofence.isEnabled ? "On · \(geofence.geofences.count)" : nil
                    )
                }
                .buttonStyle(.plain)

                // Dead-Man Wipe (Elite) — auto-wipe after N days of no unlock. Tapping
                // opens the setup screen (with toggle, interval picker, timer status, and
                // reset button). The value shown here collapses the toggle state + interval
                // into a short "On · 30d" / "Off" label so the row doubles as toggle info.
                SettingsDivider()
                Button {
                    if sub.hasElite {
                        showDeadmanWipeSetup = true
                    } else {
                        showDeadmanWipeUpsell = true
                    }
                } label: {
                    SettingsNavRow(
                        icon: "clock.badge.exclamationmark",
                        iconTint: deadman.isEnabled ? Color.appDanger : Color.subGold,
                        label: "Dead-Man Wipe (Elite)",
                        value: deadman.isEnabled ? "On · \(deadman.intervalDays)d" : nil
                    )
                }
                .buttonStyle(.plain)

                // Encrypted vault backup (Elite) — AES-256-GCM blob exported via the
                // standard share sheet; user saves to iCloud / Drive / wherever.
                SettingsDivider()
                Button {
                    if sub.hasElite {
                        showEncryptedBackupSetup = true
                    } else {
                        showEncryptedBackupUpsell = true
                    }
                } label: {
                    SettingsNavRow(
                        icon: "externaldrive.badge.icloud",
                        iconTint: sub.hasElite ? Color.appAccent : Color.subGold,
                        label: "Encrypted vault backup (Elite)"
                    )
                }
                .buttonStyle(.plain)

                // Restore from backup (Elite) — pick a `.ot2bak`, enter passphrase,
                // overwrite everything in the app.
                SettingsDivider()
                Button {
                    if sub.hasElite {
                        showBackupRestore = true
                    } else {
                        showBackupRestoreUpsell = true
                    }
                } label: {
                    SettingsNavRow(
                        icon: "arrow.down.doc",
                        iconTint: sub.hasElite ? Color.appAccent : Color.subGold,
                        label: "Restore from backup (Elite)"
                    )
                }
                .buttonStyle(.plain)

                // Shamir Split (Elite) — split the vault master key K-of-N across trusted
                // contacts via short-range Bluetooth. If the user loses access they gather
                // K contacts in person to reconstruct the key.
                SettingsDivider()
                Button {
                    if sub.hasElite {
                        showShamirSetup = true
                    } else {
                        showShamirUpsell = true
                    }
                } label: {
                    SettingsNavRow(
                        icon: "rectangle.split.3x1",
                        iconTint: ShamirState.shared.mySplit != nil ? Color.appSuccess
                            : (sub.hasElite ? Color.appAccent : Color.subGold),
                        label: "Shamir Split (Elite)",
                        value: ShamirState.shared.mySplit.map { "\($0.threshold)-of-\($0.total)" }
                    )
                }
                .buttonStyle(.plain)

                // Trusted Witness (Elite) — require a trusted contact's physical proximity +
                // explicit approval before the most dangerous actions (Wipe, Disarm Duress,
                // Disable Dead-Man Wipe, Vault export, Change master PIN). Wired into the
                // Wipe confirmation above.
                SettingsDivider()
                Button {
                    if sub.hasElite {
                        showTrustedWitnessSetup = true
                    } else {
                        showTrustedWitnessUpsell = true
                    }
                } label: {
                    SettingsNavRow(
                        icon: "person.2.wave.2",
                        iconTint: trustedWitness.featureEnabled ? Color.appSuccess
                            : (sub.hasElite ? Color.appAccent : Color.subGold),
                        label: "Trusted Witness (Elite)",
                        value: trustedWitness.featureEnabled ? "On · \(trustedWitness.witnesses.count)" : nil
                    )
                }
                .buttonStyle(.plain)

                // Steganographic Vault (Elite) — hide the vault inside an innocuous PNG or
                // PDF decoy. Opens an on-screen wizard with create + load flows.
                SettingsDivider()
                Button {
                    if sub.hasElite {
                        showStegoVaultSetup = true
                    } else {
                        showStegoVaultUpsell = true
                    }
                } label: {
                    SettingsNavRow(
                        icon: "photo.badge.plus",
                        iconTint: sub.hasElite ? Color.appAccent : Color.subGold,
                        label: "Steganographic Vault (Elite)"
                    )
                }
                .buttonStyle(.plain)

                // Signed Audit Log (Elite) — hardware-key-signed events + Merkle commits +
                // tamper-evident evidence pack export + verifier. Requires an enrolled key.
                SettingsDivider()
                Button {
                    if sub.hasElite {
                        showSignedLogSetup = true
                    } else {
                        showSignedLogUpsell = true
                    }
                } label: {
                    SettingsNavRow(
                        icon: "signature",
                        iconTint: signedLog.signEveryEntry && HardwareKeyState.shared.hasEnrolledKey
                            ? Color.appSuccess
                            : (sub.hasElite ? Color.appAccent : Color.subGold),
                        label: "Signed Audit Log (Elite)",
                        value: signedLog.signEveryEntry && HardwareKeyState.shared.hasEnrolledKey ? "On" : nil
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

/// Pro upsell sheet for Tracker Finder.
struct TrackerFinderUpsellSheet: View {
    let onUpgrade: () -> Void
    let onDismiss: () -> Void

    var body: some View {
        VStack(spacing: 20) {
            Image(systemName: "location.magnifyingglass")
                .font(.system(size: 46, weight: .semibold))
                .foregroundStyle(Color.subGold)
                .padding(.top, 28)
            Text("Tracker Finder")
                .font(.title2.weight(.bold))
                .foregroundStyle(Color.appPrimaryText)
            Text("Scan nearby Bluetooth for AirTag, Tile, Samsung SmartTag, and unknown trackers. Any device that keeps reappearing across your scans gets flagged as a possible follower. Included in Pro.")
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
