// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import SwiftUI

/// Elite-tier setup screen for the Rogue Wi-Fi + IMSI-catcher scanner.
struct RogueNetworkScreen: View {
    @Environment(\.dismiss) private var dismiss
    @StateObject private var state = RogueNetworkState.shared

    var body: some View {
        ScrollView {
            VStack(spacing: 18) {
                headerCard
                togglesCard
                scanButton
                alertsCard
                explainerCard
            }
            .padding(20)
        }
        .appBackdrop()
        .navigationTitle("Rogue Network Scanner")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button("Done") { dismiss() }.tint(Color.appBrightCyan)
            }
        }
        .onAppear { RogueNetworkMonitor.scanOnce() }
    }

    private var headerCard: some View {
        PremiumCard {
            HStack(alignment: .top, spacing: 14) {
                ZStack {
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .fill(
                            LinearGradient(
                                colors: [Color.subGold, Color.subGold.opacity(0.75)],
                                startPoint: .topLeading, endPoint: .bottomTrailing
                            )
                        )
                        .shadow(color: Color.subGold.opacity(0.6), radius: 10)
                    Image(systemName: "wifi.exclamationmark")
                        .font(.system(size: 22, weight: .semibold))
                        .foregroundStyle(.white)
                }
                .frame(width: 54, height: 54)
                VStack(alignment: .leading, spacing: 6) {
                    Text("Elite feature")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(Color.subGold)
                    Text("Watch for rogue cell towers + lookalike Wi-Fi.")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Color.appPrimaryText)
                    Text("OnlyTrusted samples the active radio tech and the Wi-Fi SSID at launch. If it spots a forced 2G downgrade (classic IMSI-catcher symptom) or an open AP broadcasting a bank / airline SSID, you get a warning here. 100% on-device.")
                        .font(.footnote)
                        .foregroundStyle(Color.appSecondaryText)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
    }

    private var togglesCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 10) {
                CardHeader(icon: "antenna.radiowaves.left.and.right", title: "What we watch")
                Toggle(isOn: $state.cellularMonitoringEnabled) {
                    Text("Cellular monitoring")
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(Color.appPrimaryText)
                }
                .tint(Color.appAccent)
                Toggle(isOn: $state.wifiMonitoringEnabled) {
                    Text("Wi-Fi monitoring")
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(Color.appPrimaryText)
                }
                .tint(Color.appAccent)
                Text("Wi-Fi SSID reads require Apple's wifi-info entitlement. If it's not provisioned on this build the Wi-Fi probe is a safe no-op; cellular monitoring works regardless.")
                    .font(.caption)
                    .foregroundStyle(Color.appSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    private var scanButton: some View {
        Button {
            RogueNetworkMonitor.scanOnce()
        } label: {
            HStack(spacing: 8) {
                Image(systemName: "arrow.clockwise")
                Text("Scan now")
                    .font(.subheadline.weight(.semibold))
            }
            .frame(maxWidth: .infinity, minHeight: 44)
        }
        .buttonStyle(.borderedProminent)
        .tint(Color.appAccent)
    }

    private var alertsCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    CardHeader(icon: "bell.badge", title: "Recent alerts")
                    Spacer()
                    if !state.alerts.isEmpty {
                        Button("Clear") { state.clearAlerts() }
                            .font(.caption.weight(.semibold))
                            .tint(Color.appBrightCyan)
                    }
                }
                if state.alerts.isEmpty {
                    Text("Nothing suspicious yet. We'll notify you here if a rogue tower or lookalike Wi-Fi lights up during a future scan.")
                        .font(.footnote)
                        .foregroundStyle(Color.appSecondaryText)
                        .fixedSize(horizontal: false, vertical: true)
                } else {
                    ForEach(state.alerts) { alert in
                        VStack(alignment: .leading, spacing: 4) {
                            HStack(spacing: 6) {
                                Image(systemName: "exclamationmark.triangle.fill")
                                    .foregroundStyle(Color.appWarning)
                                Text(alert.title)
                                    .font(.subheadline.weight(.semibold))
                                    .foregroundStyle(Color.appPrimaryText)
                            }
                            Text(alert.detail)
                                .font(.footnote)
                                .foregroundStyle(Color.appSecondaryText)
                                .fixedSize(horizontal: false, vertical: true)
                            Text(relative(alert.timestamp))
                                .font(.caption2)
                                .foregroundStyle(Color.appTertiaryText)
                        }
                        .padding(.vertical, 4)
                        if alert.id != state.alerts.last?.id {
                            Divider().background(Color.white.opacity(0.06))
                        }
                    }
                }
            }
        }
    }

    private var explainerCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 6) {
                HStack(spacing: 8) {
                    Image(systemName: "info.circle.fill")
                        .foregroundStyle(Color.appAccent)
                    Text("What we check")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Color.appPrimaryText)
                }
                Text("• Forced 2G/EDGE / GPRS downgrade (classic IMSI-catcher symptom).\n• Blank carrier name while a SIM is active.\n• Open Wi-Fi broadcasting a bank or airline brand prefix.\nAll checks are strictly on-device — no sample ever leaves your phone.")
                    .font(.caption)
                    .foregroundStyle(Color.appSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    private func relative(_ date: Date) -> String {
        let fmt = RelativeDateTimeFormatter()
        fmt.unitsStyle = .short
        return fmt.localizedString(for: date, relativeTo: Date())
    }
}

/// Elite upsell sheet for the Rogue Network Scanner row in Settings.
struct RogueNetworkUpsellSheet: View {
    let onUpgrade: () -> Void
    let onDismiss: () -> Void

    var body: some View {
        VStack(spacing: 20) {
            Image(systemName: "wifi.exclamationmark")
                .font(.system(size: 46, weight: .semibold))
                .foregroundStyle(Color.subGold)
                .padding(.top, 28)
            Text("Rogue Network Scanner")
                .font(.title2.weight(.bold))
                .foregroundStyle(Color.appPrimaryText)
            Text("Spot IMSI-catchers and lookalike bank Wi-Fi before you connect. OnlyTrusted checks the cell radio tech and SSID on launch — a forced 2G downgrade or an open \"CHASE_WIFI\" sends an alert straight to this screen. Elite tier.")
                .font(.subheadline)
                .foregroundStyle(Color.appSecondaryText)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)
            Spacer(minLength: 0)
            Button(action: onUpgrade) {
                Text("Upgrade to Elite")
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
