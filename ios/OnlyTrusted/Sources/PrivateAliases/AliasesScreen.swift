// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import SwiftUI
import UIKit
import UniformTypeIdentifiers

/// Full-screen UI for Private Email Aliases (FREE tier, unlimited count).
///
/// Honest claim surfaced in-screen: "we don't operate a mail server; your relay provider
/// forwards." All generation and bookkeeping is on-device.
struct AliasesScreen: View {

    @StateObject private var store = AliasStore.shared
    @StateObject private var sub = SubscriptionState.shared
    @State private var relayPickerOpen = false
    @State private var editingLabel: String? = nil
    @State private var labelDraft: String = ""
    @State private var toast: String? = nil
    @State private var exportURL: URL? = nil
    @State private var showingShareSheet = false
    @State private var showingImportPicker = false
    @State private var showingBulkConfirm: BulkAction? = nil

    private enum BulkAction: String, Identifiable {
        case disableAll, deleteDisabled
        var id: String { rawValue }
        var title: String {
            switch self {
            case .disableAll:     return "Disable every alias?"
            case .deleteDisabled: return "Delete every disabled alias?"
            }
        }
        var detail: String {
            switch self {
            case .disableAll:     return "Flips the on/off bookmark for every alias to OFF. Your relay provider still forwards — remember to disable there too if you want forwarding to stop."
            case .deleteDisabled: return "Removes every alias currently marked disabled. Cannot be undone unless you've exported a CSV."
            }
        }
    }

    var body: some View {
        PremiumGate(
            featureName: "Private Aliases",
            description: "Generate unlimited disposable email aliases on-device; forwarding is done by your chosen relay (iCloud+ Hide My Email, DuckDuckGo, SimpleLogin, Firefox Relay, custom domain)."
        ) { gatedBody }
    }

    private var gatedBody: some View {
        ScrollView {
            VStack(spacing: 16) {
                heroCard
                relayCard
                generateCard
                if sub.hasPremium { premiumToolsCard }
                if store.aliases.isEmpty {
                    emptyStateCard
                } else {
                    aliasesCard
                }
                howItWorksCard
            }
            .padding(20)
        }
        .sheet(isPresented: $showingShareSheet) {
            if let url = exportURL {
                ShareSheet(activityItems: [url])
            }
        }
        .fileImporter(
            isPresented: $showingImportPicker,
            allowedContentTypes: [.commaSeparatedText, .plainText, .text],
            allowsMultipleSelection: false
        ) { result in
            guard case .success(let urls) = result, let url = urls.first else { return }
            let didAccess = url.startAccessingSecurityScopedResource()
            defer { if didAccess { url.stopAccessingSecurityScopedResource() } }
            guard let data = try? Data(contentsOf: url),
                  let text = String(data: data, encoding: .utf8) else {
                showToast("Could not read file")
                return
            }
            let added = store.importCSV(text)
            showToast("Imported \(added) alias\(added == 1 ? "" : "es")")
        }
        .alert(item: $showingBulkConfirm) { bulk in
            Alert(
                title: Text(bulk.title),
                message: Text(bulk.detail),
                primaryButton: .destructive(Text("Do it")) {
                    switch bulk {
                    case .disableAll:     store.disableAll(); showToast("All aliases disabled")
                    case .deleteDisabled: store.deleteAllDisabled(); showToast("Disabled aliases deleted")
                    }
                },
                secondaryButton: .cancel()
            )
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

    private var premiumToolsCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 12) {
                CardHeader(icon: "square.and.arrow.up.on.square",
                           title: "Premium tools",
                           tint: Color.appAccent)
                Text("Portable alias book. Export your list to move to a new device, or batch-tidy a long list.")
                    .font(.caption)
                    .foregroundStyle(Color.appSecondaryText)
                HStack(spacing: 10) {
                    Button {
                        exportToCSV()
                    } label: {
                        Label("Export CSV", systemImage: "square.and.arrow.up")
                            .font(.footnote.weight(.semibold))
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 10)
                            .background(Color.appAccent, in: RoundedRectangle(cornerRadius: 10))
                            .foregroundStyle(Color.appScreenBackground)
                    }
                    .buttonStyle(.plain)
                    .disabled(store.aliases.isEmpty)

                    Button {
                        showingImportPicker = true
                    } label: {
                        Label("Import CSV", systemImage: "square.and.arrow.down")
                            .font(.footnote.weight(.semibold))
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 10)
                            .background(Color.appElevatedSurface, in: RoundedRectangle(cornerRadius: 10))
                            .foregroundStyle(Color.appAccent)
                    }
                    .buttonStyle(.plain)
                }
                HStack(spacing: 10) {
                    Button {
                        showingBulkConfirm = .disableAll
                    } label: {
                        Text("Disable all")
                            .font(.caption.weight(.semibold))
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 8)
                            .background(Color.appElevatedSurface, in: RoundedRectangle(cornerRadius: 10))
                            .foregroundStyle(Color.appSecondaryText)
                    }
                    .buttonStyle(.plain)
                    .disabled(store.aliases.isEmpty)
                    Button {
                        showingBulkConfirm = .deleteDisabled
                    } label: {
                        Text("Delete disabled")
                            .font(.caption.weight(.semibold))
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 8)
                            .background(Color.appElevatedSurface, in: RoundedRectangle(cornerRadius: 10))
                            .foregroundStyle(Color.appDanger)
                    }
                    .buttonStyle(.plain)
                    .disabled(!store.aliases.contains { !$0.enabled })
                }
            }
        }
    }

    private func exportToCSV() {
        let csv = store.exportCSV()
        let fname = "onlytrusted_aliases_\(Int(Date().timeIntervalSince1970)).csv"
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(fname)
        do {
            try csv.data(using: .utf8)?.write(to: url, options: .atomic)
            exportURL = url
            showingShareSheet = true
        } catch {
            showToast("Export failed")
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

