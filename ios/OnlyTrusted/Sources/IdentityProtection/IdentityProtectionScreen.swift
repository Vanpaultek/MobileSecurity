// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import SwiftUI
import UIKit

/// Deep-link checklist for identity protection actions (FREE tier).
///
/// Honest claim surfaced on the hero: we don't contact credit bureaus, the IRS, or any
/// agency for you. Each row opens the official self-service portal in Safari. Local
/// "done" checkmarks are stored in the Keychain.
struct IdentityProtectionScreen: View {

    @StateObject private var progress = IdentityActionsProgress.shared
    @StateObject private var sub = SubscriptionState.shared
    @StateObject private var region = RegionState.shared
    @State private var showResetConfirm = false

    private var groups: [(IdentityActionPhase, [IdentityAction])] {
        IdentityActionsCatalog.grouped(for: region.country)
    }

    private var completed: Int { progress.done.count }
    private var total: Int { groups.flatMap { $0.1 }.count }

    var body: some View {
        PremiumGate(
            featureName: "Identity Protection",
            description: "Checklist of credit freezes, fraud alerts, HIBP enrollment, FTC report, and SSA account block. Each row deep-links to the official self-service portal."
        ) { gatedBody }
    }

    private var gatedBody: some View {
        ScrollView {
            VStack(spacing: 16) {
                heroCard
                progressCard
                ForEach(groups, id: \.0) { phase, actions in
                    phaseCard(phase: phase, actions: actions)
                }
                howItWorksCard
            }
            .padding(20)
        }
        .appBackdrop()
        .navigationTitle("Identity Protection")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Menu {
                    Button(role: .destructive) {
                        showResetConfirm = true
                    } label: {
                        Label("Reset all checkmarks", systemImage: "arrow.counterclockwise")
                    }
                } label: {
                    Image(systemName: "ellipsis.circle")
                }
            }
        }
        .confirmationDialog("Reset all your checkmarks?",
                            isPresented: $showResetConfirm,
                            titleVisibility: .visible) {
            Button("Reset", role: .destructive) { progress.clearAll() }
            Button("Cancel", role: .cancel) {}
        }
    }

    // MARK: - Cards

    private var heroCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 10) {
                CardHeader(icon: "person.badge.shield.checkmark",
                           title: "Identity Protection",
                           tint: Color.appAccent)
                Text("A checklist of credit freezes, fraud alerts, and ID-theft reports you can file yourself. Each row deep-links to the official self-service portal.")
                    .font(.footnote)
                    .foregroundStyle(Color.appSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)
                Text("We don't contact any bureau for you. Checkmarks live only on this device.")
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(Color.appAccent)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    private var progressCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Text("Progress")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Color.appPrimaryText)
                    Spacer()
                    Text("\(completed) / \(total)")
                        .font(.subheadline.weight(.bold))
                        .foregroundStyle(Color.appAccent)
                }
                ProgressView(value: Double(completed), total: Double(total))
                    .tint(Color.appAccent)
            }
        }
    }

    private func phaseCard(phase: IdentityActionPhase, actions: [IdentityAction]) -> some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 12) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(phase.displayName)
                        .font(.subheadline.weight(.bold))
                        .foregroundStyle(Color.appPrimaryText)
                    Text(phase.subtitle)
                        .font(.caption)
                        .foregroundStyle(Color.appSecondaryText)
                        .fixedSize(horizontal: false, vertical: true)
                }
                ForEach(actions, id: \.id) { action in
                    VStack(spacing: 0) {
                        actionRow(action)
                        if action.id != actions.last?.id {
                            Divider().overlay(Color.appDivider)
                        }
                    }
                }
            }
        }
    }

    private func actionRow(_ action: IdentityAction) -> some View {
        let done = progress.isDone(action.id)
        return VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .top, spacing: 10) {
                Button {
                    progress.toggle(action.id)
                } label: {
                    Image(systemName: done ? "checkmark.square.fill" : "square")
                        .font(.title3)
                        .foregroundStyle(done ? Color.appAccent : Color.appSecondaryText)
                }
                .buttonStyle(.plain)

                VStack(alignment: .leading, spacing: 4) {
                    Text(action.title)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(done ? Color.appSecondaryText : Color.appPrimaryText)
                        .strikethrough(done, color: Color.appSecondaryText)
                    Text("\(action.organisation) · \(action.timeEstimate)")
                        .font(.caption)
                        .foregroundStyle(Color.appSecondaryText)
                    Text(action.notes)
                        .font(.caption)
                        .foregroundStyle(Color.appSecondaryText)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 4)
            }

            HStack(spacing: 14) {
                Button {
                    UIApplication.shared.open(action.url)
                } label: {
                    HStack(spacing: 6) {
                        Image(systemName: "arrow.up.right.square")
                        Text("Open \(action.organisation)")
                    }
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(Color.appAccent)
                }
                .buttonStyle(.plain)

                // Premium: yearly renewal reminder toggle per row.
                if sub.hasPremium {
                    let reminderOn = progress.reminderDate(action.id) != nil
                    Button {
                        if reminderOn {
                            progress.clearReminder(action.id)
                        } else {
                            progress.setReminder(action.id, title: action.title)
                        }
                    } label: {
                        HStack(spacing: 6) {
                            Image(systemName: reminderOn ? "bell.fill" : "bell")
                            Text(reminderOn ? "Reminder set" : "Remind yearly")
                        }
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(reminderOn ? Color.appSuccess : Color.appSecondaryText)
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .padding(.vertical, 6)
    }

    private var howItWorksCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 8) {
                CardHeader(icon: "info.circle", title: "What this is (and isn't)", tint: Color.appSecondaryText)
                Text("This is a visibility and shortcut tool. Tapping \"Open\" launches the agency's own website or app. Nothing on your device is shared with us or with any bureau.")
                    .font(.caption)
                    .foregroundStyle(Color.appSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)
                Text("If you want a paid monitoring service to do these calls for you, pick a provider (Aura, IdentityIQ, LifeLock) directly — we don't resell any of them.")
                    .font(.caption)
                    .foregroundStyle(Color.appSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }
}

#Preview {
    NavigationStack { IdentityProtectionScreen() }
}
