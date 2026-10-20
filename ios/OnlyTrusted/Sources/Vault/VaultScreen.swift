// SPDX-FileCopyrightText: 2026 57Million LLC
// SPDX-License-Identifier: AGPL-3.0-or-later
import SwiftUI
import LocalAuthentication

struct VaultScreen: View {
    @StateObject private var store = VaultStore.shared
    @State private var revealed: RevealedContact?
    @State private var revealError: String?
    @State private var showAdd = false
    @State private var editing: UUID?

    var body: some View {
        Group {
            if store.contacts.isEmpty {
                ContentUnavailableView(
                    "No trusted contacts yet",
                    systemImage: "person.2.circle",
                    description: Text("Start by adding yourself as the owner, then add immediate family. Everything you type on the next screen is encrypted immediately.")
                )
            } else {
                List {
                    ForEach(store.contacts) { contact in
                        ContactRow(contact: contact,
                                   onReveal: { requestReveal(contact.id) },
                                   onEdit: { editing = contact.id })
                    }
                    .onDelete { indexSet in
                        for index in indexSet {
                            let id = store.contacts[index].id
                            try? store.delete(id: id)
                        }
                    }
                }
                .listStyle(.insetGrouped)
            }
        }
        .navigationTitle("Family vault")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    showAdd = true
                } label: {
                    Image(systemName: "plus.circle.fill").decorativeAccessibility()
                }
                .accessibilityLabel("Add trusted contact")
                .minimumHitTarget()
            }
        }
        .task { await store.reload() }
        .sheet(isPresented: $showAdd) {
            NavigationStack { VaultEditScreen(contactId: nil, store: store) }
        }
        .sheet(item: Binding(get: { editing.map(IdentifiableUUID.init) },
                             set: { editing = $0?.value })) { wrap in
            NavigationStack { VaultEditScreen(contactId: wrap.value, store: store) }
        }
        .sheet(item: $revealed) { data in
            RevealedSheet(data: data)
        }
        .alert("Can't reveal", isPresented: Binding(get: { revealError != nil }, set: { _ in revealError = nil })) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(revealError ?? "")
        }
    }

    private func requestReveal(_ id: UUID) {
        let ctx = LAContext()
        var err: NSError?
        let can = ctx.canEvaluatePolicy(.deviceOwnerAuthentication, error: &err)
        guard can else {
            // No biometric or PIN enrolled: reveal anyway (same semantics as Android emulator fallback).
            attemptReveal(id)
            return
        }
        ctx.evaluatePolicy(.deviceOwnerAuthentication,
                           localizedReason: "Unlock vault to reveal contact") { ok, err in
            Task { @MainActor in
                if ok {
                    attemptReveal(id)
                } else if let err {
                    revealError = err.localizedDescription
                }
            }
        }
    }

    @MainActor
    private func attemptReveal(_ id: UUID) {
        do {
            revealed = try store.reveal(id: id)
        } catch {
            revealError = String(describing: error)
        }
    }
}

private struct IdentifiableUUID: Identifiable {
    let value: UUID
    var id: UUID { value }
    init(_ value: UUID) { self.value = value }
}

extension RevealedContact: Identifiable {}

private struct ContactRow: View {
    let contact: MaskedContact
    let onReveal: () -> Void
    let onEdit: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Image(systemName: contact.isOwner ? "shield.lefthalf.filled" : "lock.fill")
                    .decorativeAccessibility()
                Text(contact.displayName).font(.headline)
                Spacer()
                if contact.isOwner {
                    Text("Owner")
                        .font(.caption.weight(.semibold))
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .background(
                            RoundedRectangle(cornerRadius: 6, style: .continuous)
                                .fill(Color.appAccent.opacity(0.15))
                        )
                        .foregroundStyle(Color.appAccent)
                }
            }
            if let relation = contact.relation {
                Text(relation).font(.subheadline).foregroundStyle(Color.appSecondaryText)
            }
            ForEach(contact.maskedNumbers, id: \.self) { num in
                Text(num).font(.subheadline).monospaced()
            }
            if let email = contact.maskedEmail {
                Text(email).font(.subheadline).foregroundStyle(Color.appSecondaryText)
            }
            HStack {
                Spacer()
                Button("Edit", action: onEdit).buttonStyle(.borderless)
                Button {
                    onReveal()
                } label: {
                    Image(systemName: "eye").decorativeAccessibility()
                }
                .buttonStyle(.borderless)
                .accessibilityLabel("Reveal \(contact.displayName) with Face ID")
                .minimumHitTarget()
            }
        }
        .padding(.vertical, 2)
    }
}

private struct RevealedSheet: View {
    let data: RevealedContact
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                Section("Identity") {
                    LabeledContent("Name", value: data.displayName)
                    if let relation = data.relation {
                        LabeledContent("Relation", value: relation)
                    }
                    if data.isOwner {
                        LabeledContent("Role", value: "Phone owner")
                    }
                }
                if !data.numbers.isEmpty {
                    Section("Phone numbers") {
                        ForEach(data.numbers) { n in
                            LabeledContent(n.label ?? "Phone", value: n.normalisedNumber)
                                .monospaced()
                        }
                    }
                }
                if let address = data.addressPlain, !address.isEmpty {
                    Section("Address (encrypted at rest)") {
                        Text(address)
                    }
                }
                if let notes = data.notesPlain, !notes.isEmpty {
                    Section("Notes (encrypted at rest)") {
                        Text(notes)
                    }
                }
            }
            .navigationTitle(data.displayName)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Hide") { dismiss() }
                }
            }
        }
    }
}
