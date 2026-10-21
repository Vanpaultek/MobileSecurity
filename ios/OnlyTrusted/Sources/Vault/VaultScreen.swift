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
                PremiumEmptyState(
                    icon: "person.2.circle.fill",
                    title: "No trusted contacts yet",
                    message: "Start by adding yourself as the owner, then your immediate family. Everything you type is encrypted the moment you save it.",
                    ctaTitle: "Add your first contact",
                    ctaAction: { showAdd = true }
                )
            } else {
                ScrollView(.vertical, showsIndicators: false) {
                    LazyVStack(spacing: 14) {
                        ForEach(store.contacts) { contact in
                            ContactCard(
                                contact: contact,
                                onReveal: { requestReveal(contact.id) },
                                onEdit: { editing = contact.id },
                                onDelete: { try? store.delete(id: contact.id) }
                            )
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 12)
                    .padding(.bottom, 32)
                }
            }
        }
        .background(Color.appScreenBackground.ignoresSafeArea())
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

private struct ContactCard: View {
    let contact: MaskedContact
    let onReveal: () -> Void
    let onEdit: () -> Void
    let onDelete: () -> Void

    @State private var showingDeleteConfirm = false

    var body: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 14) {
                HStack(alignment: .top, spacing: 14) {
                    AvatarChip(initials: initials, isOwner: contact.isOwner)
                    VStack(alignment: .leading, spacing: 4) {
                        HStack(alignment: .firstTextBaseline, spacing: 8) {
                            Text(contact.displayName)
                                .font(.title3.weight(.semibold))
                                .foregroundStyle(Color.appPrimaryText)
                            if contact.isOwner {
                                Text("Owner")
                                    .font(.caption2.weight(.bold))
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 3)
                                    .background(
                                        Capsule().fill(Color.appAccent.opacity(0.14))
                                    )
                                    .foregroundStyle(Color.appAccent)
                            }
                        }
                        if let relation = contact.relation {
                            Text(relation)
                                .font(.subheadline)
                                .foregroundStyle(Color.appSecondaryText)
                        }
                    }
                    Spacer(minLength: 0)
                }

                if !contact.maskedNumbers.isEmpty {
                    VStack(alignment: .leading, spacing: 6) {
                        ForEach(contact.maskedNumbers, id: \.self) { num in
                            HStack(spacing: 10) {
                                Image(systemName: "phone.fill")
                                    .font(.footnote)
                                    .foregroundStyle(Color.appTertiaryText)
                                    .decorativeAccessibility()
                                Text(num)
                                    .font(.subheadline.monospaced())
                                    .foregroundStyle(Color.appPrimaryText)
                            }
                        }
                        if let email = contact.maskedEmail {
                            HStack(spacing: 10) {
                                Image(systemName: "envelope.fill")
                                    .font(.footnote)
                                    .foregroundStyle(Color.appTertiaryText)
                                    .decorativeAccessibility()
                                Text(email)
                                    .font(.subheadline)
                                    .foregroundStyle(Color.appSecondaryText)
                            }
                        }
                    }
                }

                HStack(spacing: 10) {
                    Button(action: onEdit) {
                        Label("Edit", systemImage: "pencil")
                            .font(.subheadline.weight(.semibold))
                            .frame(maxWidth: .infinity, minHeight: 32)
                    }
                    .buttonStyle(.bordered)
                    .tint(Color.appAccent)

                    Button(action: onReveal) {
                        Label("Reveal", systemImage: "eye.fill")
                            .font(.subheadline.weight(.semibold))
                            .frame(maxWidth: .infinity, minHeight: 32)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(Color.appAccent)
                    .accessibilityLabel("Reveal \(contact.displayName) with Face ID")
                }
            }
        }
        .contextMenu {
            Button(role: .destructive) { showingDeleteConfirm = true } label: {
                Label("Delete", systemImage: "trash")
            }
        }
        .confirmationDialog("Delete \(contact.displayName)?",
                            isPresented: $showingDeleteConfirm,
                            titleVisibility: .visible) {
            Button("Delete", role: .destructive, action: onDelete)
            Button("Cancel", role: .cancel) {}
        }
    }

    private var initials: String {
        let parts = contact.displayName.split(separator: " ")
        let letters = parts.prefix(2).compactMap { $0.first?.uppercased() }
        return letters.joined()
    }
}

private struct AvatarChip: View {
    let initials: String
    let isOwner: Bool

    var body: some View {
        ZStack {
            Circle()
                .fill(
                    LinearGradient(
                        colors: [Color.appAccent, Color.appAccent.opacity(0.65)],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
            if initials.isEmpty {
                Image(systemName: isOwner ? "person.fill" : "person")
                    .font(.title3)
                    .foregroundStyle(.white)
            } else {
                Text(initials)
                    .font(.subheadline.weight(.bold))
                    .foregroundStyle(.white)
            }
        }
        .frame(width: 46, height: 46)
        .decorativeAccessibility()
    }
}

private struct RevealedSheet: View {
    let data: RevealedContact
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView(.vertical, showsIndicators: false) {
                VStack(spacing: 16) {
                    PremiumCard {
                        VStack(alignment: .leading, spacing: 14) {
                            CardHeader(icon: "person.text.rectangle.fill", title: "Identity")
                            LabeledRow(label: "Name", value: data.displayName)
                            if let relation = data.relation {
                                LabeledRow(label: "Relation", value: relation)
                            }
                            if data.isOwner {
                                LabeledRow(label: "Role", value: "Phone owner")
                            }
                        }
                    }

                    if !data.numbers.isEmpty {
                        PremiumCard {
                            VStack(alignment: .leading, spacing: 14) {
                                CardHeader(icon: "phone.fill", title: "Phone numbers")
                                ForEach(data.numbers) { n in
                                    LabeledRow(
                                        label: n.label ?? "Phone",
                                        value: n.normalisedNumber,
                                        monospaced: true
                                    )
                                }
                            }
                        }
                    }

                    if let address = data.addressPlain, !address.isEmpty {
                        PremiumCard {
                            VStack(alignment: .leading, spacing: 10) {
                                CardHeader(icon: "mappin.and.ellipse", title: "Address")
                                Text(address)
                                    .font(.subheadline)
                                    .foregroundStyle(Color.appPrimaryText)
                                    .fixedSize(horizontal: false, vertical: true)
                            }
                        }
                    }

                    if let notes = data.notesPlain, !notes.isEmpty {
                        PremiumCard {
                            VStack(alignment: .leading, spacing: 10) {
                                CardHeader(icon: "note.text", title: "Notes")
                                Text(notes)
                                    .font(.subheadline)
                                    .foregroundStyle(Color.appPrimaryText)
                                    .fixedSize(horizontal: false, vertical: true)
                            }
                        }
                    }
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 16)
            }
            .background(Color.appScreenBackground.ignoresSafeArea())
            .navigationTitle(data.displayName)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Hide") { dismiss() }
                        .tint(Color.appAccent)
                }
            }
        }
    }
}

private struct LabeledRow: View {
    let label: String
    let value: String
    var monospaced: Bool = false

    var body: some View {
        HStack(alignment: .firstTextBaseline) {
            Text(label)
                .font(.subheadline)
                .foregroundStyle(Color.appSecondaryText)
            Spacer(minLength: 12)
            Text(value)
                .font(monospaced ? .subheadline.monospaced() : .subheadline.weight(.medium))
                .foregroundStyle(Color.appPrimaryText)
                .multilineTextAlignment(.trailing)
        }
    }
}
