// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import SwiftUI
import UIKit

/// Full-screen UI for Private Email Aliases (FREE tier, unlimited count).
///
/// Honest claim surfaced in-screen: "we don't operate a mail server; your relay provider
/// forwards." All generation and bookkeeping is on-device.
struct AliasesScreen: View {

    @StateObject private var store = AliasStore.shared
    @State private var relayPickerOpen = false
    @State private var editingLabel: String? = nil
    @State private var labelDraft: String = ""
    @State private var toast: String? = nil

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                heroCard
                relayCard
                generateCard
                if store.aliases.isEmpty {
                    emptyStateCard
                } else {
                    aliasesCard
                }
                howItWorksCard
            }
            .padding(20)
        }
        .appBackdrop()
        .navigationTitle("Private Aliases")
        .navigationBarTitleDisplayMode(.inline)
        .overlay(alignment: .bottom) {
            if let toast {
                Text(toast)
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(Color.appScreenBackground)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 10)
                    .background(Color.appAccent, in: Capsule())
                    .padding(.bottom, 24)
                    .transition(.opacity.combined(with: .move(edge: .bottom)))
            }
        }
    }

    // MARK: - Cards

    private var heroCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 10) {
                CardHeader(icon: "at.badge.plus",
                           title: "Private Aliases",
                           tint: Color.appAccent)
                Text("Stop giving out your real email. Generate a disposable alias per site, keep track of them here, and toggle them off when a sender gets noisy.")
                    .font(.footnote)
                    .foregroundStyle(Color.appSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)
                Text("We don't operate a mail server. Your chosen relay provider forwards. Toggles live on this device.")
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(Color.appAccent)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    private var relayCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 12) {
                CardHeader(icon: "arrow.triangle.branch", title: "Your relay", tint: Color.appAccent)
                Button {
                    relayPickerOpen = true
                } label: {
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(store.relay.displayName)
                                .font(.body.weight(.semibold))
                                .foregroundStyle(Color.appPrimaryText)
                            Text(store.relay.subtitle)
                                .font(.caption)
                                .foregroundStyle(Color.appSecondaryText)
                                .fixedSize(horizontal: false, vertical: true)
                                .multilineTextAlignment(.leading)
                        }
                        Spacer()
                        Image(systemName: "chevron.up.chevron.down")
                            .foregroundStyle(Color.appAccent)
                    }
                    .padding(12)
                    .background(Color.appElevatedSurface, in: RoundedRectangle(cornerRadius: 10))
                }
                .buttonStyle(.plain)

                if store.relay == .customDomain {
                    TextField("yourdomain.example", text: $store.customDomain)
                        .textContentType(.URL)
                        .autocapitalization(.none)
                        .disableAutocorrection(true)
                        .padding(12)
                        .background(Color.appElevatedSurface, in: RoundedRectangle(cornerRadius: 10))
                }

                if let url = store.relay.portalURL {
                    Button {
                        UIApplication.shared.open(url)
                    } label: {
                        HStack {
                            Image(systemName: "arrow.up.right.square")
                            Text("Open \(store.relay.displayName)")
                        }
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle(Color.appAccent)
                    }
                }
            }
        }
        .confirmationDialog("Choose your relay", isPresented: $relayPickerOpen, titleVisibility: .visible) {
            ForEach(RelayChoice.allCases) { choice in
                Button(choice.displayName) { store.relay = choice }
            }
            Button("Cancel", role: .cancel) {}
        }
    }

    private var generateCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 12) {
                CardHeader(icon: "sparkles", title: "Generate alias", tint: Color.appAccent)
                Text("Tap to make a fresh random prefix. Copy it, then add it to your relay provider's dashboard so forwarding starts.")
                    .font(.caption)
                    .foregroundStyle(Color.appSecondaryText)
                Button {
                    let a = store.generate()
                    UIPasteboard.general.string = store.address(for: a)
                    showToast("Alias copied to clipboard")
                } label: {
                    HStack {
                        Image(systemName: "plus.circle.fill")
                        Text("Generate & copy new alias")
                            .font(.body.weight(.semibold))
                        Spacer()
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 12)
                    .background(Color.appAccent, in: RoundedRectangle(cornerRadius: 12))
                    .foregroundStyle(Color.appScreenBackground)
                }
                .buttonStyle(.plain)
            }
        }
    }

    private var emptyStateCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 8) {
                CardHeader(icon: "tray", title: "No aliases yet", tint: Color.appSecondaryText)
                Text("Tap \"Generate & copy new alias\" above to create your first. We keep the list on this device and never sync it anywhere.")
                    .font(.caption)
                    .foregroundStyle(Color.appSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    private var aliasesCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 12) {
                CardHeader(icon: "list.bullet.rectangle",
                           title: "Your aliases (\(store.aliases.count))",
                           tint: Color.appAccent)
                ForEach(store.aliases, id: \.id) { alias in
                    VStack(spacing: 0) {
                        aliasRow(alias)
                        if alias.id != store.aliases.last?.id {
                            Divider().overlay(Color.appDivider)
                        }
                    }
                }
            }
        }
    }

    private func aliasRow(_ alias: EmailAlias) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(store.address(for: alias))
                        .font(.system(.footnote, design: .monospaced).weight(.semibold))
                        .foregroundStyle(Color.appPrimaryText)
                        .lineLimit(1)
                        .minimumScaleFactor(0.6)
                    if editingLabel == alias.id {
                        TextField("Label (e.g. Amazon, Netflix…)", text: $labelDraft)
                            .textFieldStyle(.roundedBorder)
                            .font(.caption)
                            .onSubmit {
                                store.rename(alias.id, label: labelDraft)
                                editingLabel = nil
                            }
                    } else if !alias.label.isEmpty {
                        Text(alias.label)
                            .font(.caption)
                            .foregroundStyle(Color.appSecondaryText)
                    } else {
                        Button("Add label") {
                            editingLabel = alias.id
                            labelDraft = alias.label
                        }
                        .font(.caption)
                        .foregroundStyle(Color.appAccent)
                        .buttonStyle(.plain)
                    }
                }
                Spacer()
                Toggle("", isOn: Binding(
                    get: { alias.enabled },
                    set: { _ in store.toggle(alias.id) }
                ))
                .labelsHidden()
                .tint(Color.appAccent)
            }
            HStack(spacing: 10) {
                Button {
                    UIPasteboard.general.string = store.address(for: alias)
                    showToast("Copied")
                } label: {
                    Label("Copy", systemImage: "doc.on.doc")
                        .font(.caption.weight(.semibold))
                }
                Button(role: .destructive) {
                    store.delete(alias.id)
                } label: {
                    Label("Delete", systemImage: "trash")
                        .font(.caption.weight(.semibold))
                }
                Spacer()
                if editingLabel == alias.id {
                    Button("Save") {
                        store.rename(alias.id, label: labelDraft)
                        editingLabel = nil
                    }
                    .font(.caption.weight(.semibold))
                } else if !alias.label.isEmpty {
                    Button {
                        editingLabel = alias.id
                        labelDraft = alias.label
                    } label: {
                        Label("Rename", systemImage: "pencil")
                            .font(.caption.weight(.semibold))
                    }
                }
            }
        }
        .padding(.vertical, 6)
    }

    private var howItWorksCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 10) {
                CardHeader(icon: "info.circle", title: "How this works", tint: Color.appSecondaryText)
                step(num: "1", text: "Pick the relay you already use (or will sign up for).")
                step(num: "2", text: "Tap Generate — we make a random disposable prefix.")
                step(num: "3", text: "Add that alias in the relay's own dashboard so forwarding starts.")
                step(num: "4", text: "Toggle an alias off here to remember you've stopped using it — then disable it in the relay's dashboard too.")
                Text("The on/off toggle is a bookmark. It does not reach into your relay provider; you still need to disable the alias there if you want forwarding to stop.")
                    .font(.caption)
                    .foregroundStyle(Color.appSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.top, 4)
            }
        }
    }

    private func step(num: String, text: String) -> some View {
        HStack(alignment: .top, spacing: 10) {
            Text(num)
                .font(.caption.weight(.bold))
                .foregroundStyle(Color.appScreenBackground)
                .frame(width: 20, height: 20)
                .background(Color.appAccent, in: Circle())
            Text(text)
                .font(.caption)
                .foregroundStyle(Color.appSecondaryText)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    private func showToast(_ msg: String) {
        withAnimation { toast = msg }
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.6) {
            withAnimation { toast = nil }
        }
    }
}

#Preview {
    NavigationStack { AliasesScreen() }
}
