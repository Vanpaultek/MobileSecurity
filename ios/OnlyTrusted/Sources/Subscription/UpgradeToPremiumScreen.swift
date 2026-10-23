// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import SwiftUI

/// Upgrade to Premium screen — plan comparison + purchase CTA.
/// Pure UI + stubbed purchase flow. When StoreKit is wired, replace `confirmPurchase()` with
/// `Product.purchase()` and gate the plan features behind `SubscriptionState.shared.isActive`.
struct UpgradeToPremiumScreen: View {
    @Environment(\.dismiss) private var dismiss
    @StateObject private var sub = SubscriptionState.shared

    @State private var billingCadence: BillingCadence = .yearly
    @State private var selectedPlan: Plan = .premium
    @State private var showPurchaseConfirmation = false
    @State private var showRestoreNotice: String?

    enum BillingCadence: Hashable { case monthly, yearly }
    enum Plan: Hashable { case free, premium, family, highPremium, elite }

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
                VStack(spacing: 22) {
                    heroCrown
                    introText
                    cadenceToggle
                    plansGrid
                    ctaButton
                    restoreButton
                }
                .padding(.horizontal, 18)
                .padding(.top, 8)
                .padding(.bottom, 32)
            }
        }
        .appBackdrop()
        .navigationBarHidden(true)
        .alert("Confirm subscription", isPresented: $showPurchaseConfirmation) {
            Button("Subscribe") { confirmPurchase() }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("\(selectedPlan.priceLabel(for: billingCadence)) \(selectedPlan.cadenceSuffix(for: billingCadence)) will be charged to your Apple ID and auto-renew unless cancelled.")
        }
        .alert(showRestoreNotice ?? "", isPresented: Binding(
            get: { showRestoreNotice != nil },
            set: { if !$0 { showRestoreNotice = nil } }
        )) {
            Button("OK", role: .cancel) {}
        }
    }

    // MARK: - Header

    private var header: some View {
        ZStack {
            HStack {
                Button(action: { dismiss() }) {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 20, weight: .semibold))
                        .foregroundStyle(Color.appBrightCyan)
                        .frame(width: 40, height: 40)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Back")
                Spacer()
            }
            Text("Upgrade to Premium")
                .font(.system(size: 22, weight: .bold, design: .rounded))
                .foregroundStyle(Color.appPrimaryText)
        }
    }

    private var heroCrown: some View {
        ZStack {
            Circle()
                .stroke(Color.appBrightCyan, lineWidth: 3)
                .frame(width: 130, height: 130)
                .shadow(color: Color.appBrightCyan.opacity(0.7), radius: 18, x: 0, y: 0)
            Image(systemName: "crown.fill")
                .font(.system(size: 54, weight: .semibold))
                .foregroundStyle(Color.subGold)
                .shadow(color: Color.subGold.opacity(0.7), radius: 14, x: 0, y: 0)
        }
    }

    private var introText: some View {
        Text("Get more protection and powerful features with OnlyTrusted Premium.")
            .font(.system(size: 16))
            .foregroundStyle(Color.appSecondaryText)
            .multilineTextAlignment(.center)
            .fixedSize(horizontal: false, vertical: true)
            .padding(.horizontal, 20)
    }

    // MARK: - Cadence toggle (Monthly | Yearly save 40%)

    private var cadenceToggle: some View {
        HStack(spacing: 0) {
            cadenceButton(.monthly, "Monthly", subtitle: nil)
            cadenceButton(.yearly, "Yearly", subtitle: "Save 58%")
        }
        .padding(4)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(Color.clear)
                .overlay(
                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .stroke(Color.appBrightCyan.opacity(0.3), lineWidth: 1)
                )
        )
    }

    private func cadenceButton(_ cadence: BillingCadence, _ title: String, subtitle: String?) -> some View {
        let selected = billingCadence == cadence
        return Button {
            billingCadence = cadence
        } label: {
            VStack(spacing: 2) {
                Text(title)
                    .font(.system(size: 16, weight: .bold))
                    .foregroundStyle(selected ? Color.appScreenBackground : Color.appPrimaryText)
                if let subtitle {
                    Text(subtitle)
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundStyle(selected ? Color.appScreenBackground.opacity(0.8) : Color.appBrightCyan)
                }
            }
            .frame(maxWidth: .infinity, minHeight: 52)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(selected ? Color.appBrightCyan : Color.clear)
            )
        }
        .buttonStyle(.plain)
    }

    // MARK: - Plans grid

    private var plansGrid: some View {
        // All plans render as full-width cards stacked vertically — matches the Elite
        // card format which users liked. Keeps the layout consistent across every
        // tier so comparing is easier.
        VStack(spacing: 10) {
            planCard(.free)
            planCard(.premium)
            planCard(.family)
            planCard(.highPremium)
            planCard(.elite)
        }
    }

    private func planCard(_ plan: Plan) -> some View {
        let isSelected = plan == selectedPlan
        return Button {
            selectedPlan = plan
        } label: {
            VStack(spacing: 10) {
                plan.chip
                Text(plan.name)
                    .font(.system(size: 18, weight: .bold))
                    .foregroundStyle(plan == .premium ? Color.appBrightCyan : Color.appPrimaryText)
                Text(plan.priceLabel(for: billingCadence))
                    .font(.system(size: 24, weight: .bold, design: .rounded))
                    .foregroundStyle(Color.appPrimaryText)
                Text(plan.cadenceSuffix(for: billingCadence))
                    .font(.system(size: 12))
                    .foregroundStyle(Color.appSecondaryText)
                switch plan {
                case .free:
                    Text("Basic protection")
                        .font(.system(size: 12))
                        .foregroundStyle(Color.appSecondaryText)
                case .premium:
                    Text("Most Popular")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundStyle(Color.appBrightCyan)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(Capsule().stroke(Color.appBrightCyan, lineWidth: 1))
                case .family:
                    Text("Up to 5 devices")
                        .font(.system(size: 12))
                        .foregroundStyle(Color.appSecondaryText)
                case .highPremium:
                    Text("All Protection")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundStyle(Color.aboutOrange)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(Capsule().stroke(Color.aboutOrange, lineWidth: 1))
                case .elite:
                    Text("Elite — Max Security")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundStyle(Color.appBrightCyan)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(
                            Capsule()
                                .stroke(
                                    LinearGradient(colors: [Color.appBrightCyan, Color.aboutOrange],
                                                   startPoint: .leading, endPoint: .trailing),
                                    lineWidth: 1
                                )
                        )
                }

                VStack(alignment: .leading, spacing: 10) {
                    ForEach(Array(plan.features.enumerated()), id: \.offset) { _, feature in
                        FeatureBullet(text: feature.text, included: feature.included)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.top, 6)
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .top)
            .background(
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .fill(Color.clear)
                    .overlay(
                        RoundedRectangle(cornerRadius: 18, style: .continuous)
                            .stroke(
                                isSelected ? Color.appBrightCyan : Color.appBrightCyan.opacity(0.2),
                                lineWidth: isSelected ? 2 : 1
                            )
                    )
                    .shadow(color: isSelected ? Color.appBrightCyan.opacity(0.5) : .clear,
                            radius: isSelected ? 12 : 0, x: 0, y: 0)
            )
        }
        .buttonStyle(.plain)
    }

    // MARK: - CTA + Restore

    private var ctaButton: some View {
        Button(action: { showPurchaseConfirmation = true }) {
            Text(ctaLabel)
                .font(.system(size: 18, weight: .bold, design: .rounded))
                .foregroundStyle(Color.appScreenBackground)
                .frame(maxWidth: .infinity, minHeight: 56)
                .background(
                    Capsule(style: .continuous)
                        .fill(
                            LinearGradient(
                                colors: [Color.appBrightCyan, Color.appAccent],
                                startPoint: .leading, endPoint: .trailing
                            )
                        )
                        .shadow(color: Color.appBrightCyan.opacity(0.6), radius: 18, x: 0, y: 0)
                )
        }
        .buttonStyle(.plain)
        .padding(.top, 6)
    }

    private var restoreButton: some View {
        Button(action: {
            showRestoreNotice = sub.isActive
                ? "Your Premium access is already active."
                : "No previous purchases were found on this Apple ID."
        }) {
            Text("Restore Purchase")
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(Color.appBrightCyan)
        }
        .buttonStyle(.plain)
    }

    private var ctaLabel: String {
        switch selectedPlan {
        case .free: return "Keep Free Plan"
        case .premium: return "Subscribe to Premium"
        case .family: return "Subscribe to Family"
        case .highPremium: return "Subscribe to Pro"
        case .elite: return "Subscribe to Elite"
        }
    }

    // MARK: - Purchase stub

    private func confirmPurchase() {
        // Future: Transaction via StoreKit. For now mark the local state so the UI
        // reflects the active plan + next billing date.
        let nextCycleDays = billingCadence == .monthly ? 30 : 365
        let nextDate = Calendar.current.date(byAdding: .day, value: nextCycleDays, to: Date()) ?? Date()
        sub.markPurchased(
            tier: selectedPlan.tier,
            plan: selectedPlan.name,
            pricing: selectedPlan.priceLabel(for: billingCadence) + " \(selectedPlan.cadenceSuffix(for: billingCadence))",
            nextDate: nextDate
        )
        dismiss()
    }
}

// MARK: - Feature bullet row

private struct FeatureBullet: View {
    let text: String
    let included: Bool

    var body: some View {
        HStack(alignment: .top, spacing: 8) {
            Image(systemName: included ? "checkmark.square.fill" : "square")
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(included ? Color.appBrightCyan : Color.appTertiaryText)
            Text(text)
                .font(.system(size: 12))
                .foregroundStyle(included ? Color.appPrimaryText : Color.appTertiaryText)
                .fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 0)
        }
    }
}

// MARK: - Plan model

extension UpgradeToPremiumScreen.Plan {
    var name: String {
        switch self {
        case .free: return "Free"
        case .premium: return "Premium"
        case .family: return "Family"
        case .highPremium: return "Pro"
        case .elite: return "Elite"
        }
    }

    var tier: SubscriptionTier {
        switch self {
        case .free: return .free
        case .premium: return .premium
        case .family: return .family
        case .highPremium: return .highPremium
        case .elite: return .elite
        }
    }

    func priceLabel(for cadence: UpgradeToPremiumScreen.BillingCadence) -> String {
        switch self {
        case .free: return "$0"
        case .premium:
            return cadence == .monthly ? "$0.99" : "$4.99"
        case .family:
            return cadence == .monthly ? "$1.99" : "$9.99"
        case .highPremium:
            return cadence == .monthly ? "$2.99" : "$19.99"
        case .elite:
            return cadence == .monthly ? "$3.99" : "$29.99"
        }
    }

    func cadenceSuffix(for cadence: UpgradeToPremiumScreen.BillingCadence) -> String {
        switch self {
        case .free: return ""
        case .premium, .family, .highPremium, .elite:
            return cadence == .monthly ? "per month" : "per year"
        }
    }

    @ViewBuilder
    var chip: some View {
        switch self {
        case .free:
            Image(systemName: "shield.lefthalf.filled")
                .font(.system(size: 22, weight: .semibold))
                .foregroundStyle(Color.appSecondaryText)
                .frame(width: 46, height: 46)
                .background(Circle().fill(Color.appElevatedSurface))
        case .premium:
            Image(systemName: "crown.fill")
                .font(.system(size: 22, weight: .semibold))
                .foregroundStyle(Color.appBrightCyan)
                .frame(width: 46, height: 46)
                .background(
                    Circle()
                        .stroke(Color.appBrightCyan, lineWidth: 1.5)
                        .shadow(color: Color.appBrightCyan.opacity(0.6), radius: 10)
                )
        case .family:
            Image(systemName: "person.3.fill")
                .font(.system(size: 20, weight: .semibold))
                .foregroundStyle(Color.settingsBlue)
                .frame(width: 46, height: 46)
                .background(Circle().fill(Color.settingsBlue.opacity(0.18)))
        case .highPremium:
            Image(systemName: "shield.checkerboard")
                .font(.system(size: 22, weight: .semibold))
                .foregroundStyle(Color.aboutOrange)
                .frame(width: 46, height: 46)
                .background(
                    Circle()
                        .stroke(Color.aboutOrange, lineWidth: 1.5)
                        .shadow(color: Color.aboutOrange.opacity(0.6), radius: 10)
                )
        case .elite:
            Image(systemName: "key.horizontal.fill")
                .font(.system(size: 22, weight: .semibold))
                .foregroundStyle(
                    LinearGradient(colors: [Color.appBrightCyan, Color.aboutOrange],
                                   startPoint: .leading, endPoint: .trailing)
                )
                .frame(width: 46, height: 46)
                .background(
                    Circle()
                        .stroke(
                            LinearGradient(colors: [Color.appBrightCyan, Color.aboutOrange],
                                           startPoint: .leading, endPoint: .trailing),
                            lineWidth: 1.5
                        )
                        .shadow(color: Color.appBrightCyan.opacity(0.6), radius: 10)
                )
        }
    }

    struct Feature { let text: String; let included: Bool }

    var features: [Feature] {
        switch self {
        case .free: return [
            .init(text: "Block unknown contacts", included: true),
            .init(text: "Trusted contacts", included: false),
            .init(text: "Basic logs", included: false)
        ]
        case .premium: return [
            .init(text: "Breach Watch — monitor 5 emails", included: true),
            .init(text: "Weekly PDF digest", included: true),
            .init(text: "Alias book backup (CSV import/export)", included: true),
            .init(text: "Quiet hours + area-code allowlist", included: true),
            .init(text: "Data Removal + Identity renewal reminders", included: true)
        ]
        case .family: return [
            .init(text: "All Premium features", included: true),
            .init(text: "Family sharing", included: true),
            .init(text: "Usage reports", included: true),
            .init(text: "Priority support", included: true)
        ]
        case .highPremium: return [
            .init(text: "All Family features", included: true),
            .init(text: "Duress Mode + decoy vault", included: true),
            .init(text: "SIM-Swap Alarm", included: true),
            .init(text: "App Permission Watchdog", included: true),
            .init(text: "Tracker Finder (AirTag, Tile…)", included: true),
            .init(text: "Smart Link Guard (SMS + QR)", included: true),
            .init(text: "Wangiri + Safety Check-In", included: true)
        ]
        case .elite: return [
            .init(text: "Everything in Pro", included: true),
            .init(text: "Hardware security key (YubiKey, Titan, SoloKey)", included: true),
            .init(text: "Dead-man wipe (auto-wipe if device confiscated)", included: true),
            .init(text: "Encrypted vault backup to your own cloud", included: true),
            .init(text: "Panic lockdown mode", included: true),
            .init(text: "Priority support with 24h response", included: true)
        ]
        }
    }
}
