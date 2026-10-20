// SPDX-FileCopyrightText: 2026 57Million LLC
// SPDX-License-Identifier: AGPL-3.0-or-later
import SwiftUI
import LocalAuthentication

struct VaultEditScreen: View {
    let contactId: UUID?
    @ObservedObject var store: VaultStore
    @Environment(\.dismiss) private var dismiss

    @State private var name = ""
    @State private var relation = ""
    @State private var isOwner = false
    @State private var email = ""
    @State private var address = ""
    @State private var notes = ""
    @State private var numbers: [NumberRow] = [NumberRow()]
    @State private var authorised: Bool
    @State private var authError: String?
    @State private var saveError: String?

    init(contactId: UUID?, store: VaultStore) {
        self.contactId = contactId
        self.store = store
        _authorised = State(initialValue: contactId == nil)
    }

    var body: some View {
        Form {
            if !authorised {
                Section {
                    Text("Vault is locked. Unlock to edit.")
                    if let authError { Text(authError).foregroundStyle(.red) }
                    Button("Try again", action: authenticate)
                }
            } else {
                Section("Identity") {
                    TextField("Display name", text: $name).textContentType(.name)
                    TextField("Relation (e.g. spouse, mother, child)", text: $relation)
                    Toggle("This is the phone owner", isOn: $isOwner)
                    TextField("Email (optional, stored masked)", text: $email)
                        .keyboardType(.emailAddress)
                        .textContentType(.emailAddress)
                        .autocapitalization(.none)
                }

                Section("Phone numbers") {
                    ForEach($numbers) { $row in
                        HStack {
                            TextField("Number", text: Binding(
                                get: { row.raw },
                                set: { row.raw = PhoneUtils.sanitiseNumberInput($0) }
                            ))
                            .keyboardType(.phonePad)
                            TextField("Label", text: $row.label)
                                .frame(maxWidth: 120)
                        }
                    }
                    .onDelete { indexSet in
                        numbers.remove(atOffsets: indexSet)
                        if numbers.isEmpty { numbers = [NumberRow()] }
                    }
                    Button {
                        numbers.append(NumberRow())
                    } label: {
                        Label("Add another number", systemImage: "plus.circle")
                    }
                }

                Section("Encrypted at rest") {
                    TextField("Address", text: $address, axis: .vertical).lineLimit(3...)
                    TextField("Notes", text: $notes, axis: .vertical).lineLimit(3...)
                }

                if let saveError {
                    Section { Text(saveError).foregroundStyle(.red) }
                }
            }
        }
        .navigationTitle(contactId == nil ? "Add trusted contact" : "Edit trusted contact")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button("Cancel") { dismiss() }
            }
            ToolbarItem(placement: .confirmationAction) {
                Button("Save") { attemptSave() }
                    .disabled(name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || !authorised)
            }
            if contactId != nil && authorised {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(role: .destructive) {
                        if let id = contactId { try? store.delete(id: id); dismiss() }
                    } label: {
                        Image(systemName: "trash")
                    }
                }
            }
        }
        .task {
            if !authorised { authenticate() }
            if let id = contactId, authorised { prefill(id) }
        }
        .onChange(of: authorised) { _, now in
            if now, let id = contactId { prefill(id) }
        }
    }

    private func authenticate() {
        let ctx = LAContext()
        var err: NSError?
        guard ctx.canEvaluatePolicy(.deviceOwnerAuthentication, error: &err) else {
            authorised = true
            return
        }
        ctx.evaluatePolicy(.deviceOwnerAuthentication,
                           localizedReason: "Unlock to edit a vault entry") { ok, err in
            Task { @MainActor in
                authorised = ok
                if let err { authError = err.localizedDescription }
            }
        }
    }

    private func prefill(_ id: UUID) {
        guard let existing = try? store.reveal(id: id) else { return }
        name = existing.displayName
        relation = existing.relation ?? ""
        isOwner = existing.isOwner
        address = existing.addressPlain ?? ""
        notes = existing.notesPlain ?? ""
        numbers = existing.numbers.map { NumberRow(raw: $0.normalisedNumber, label: $0.label ?? "") }
        if numbers.isEmpty { numbers = [NumberRow()] }
    }

    private func attemptSave() {
        let draft = TrustedContactDraft(
            id: contactId,
            displayName: name,
            relation: relation.isEmpty ? nil : relation,
            isOwner: isOwner,
            email: email.isEmpty ? nil : email,
            address: address.isEmpty ? nil : address,
            notes: notes.isEmpty ? nil : notes,
            phoneNumbers: numbers.map { ($0.raw, $0.label.isEmpty ? nil : $0.label) }
        )
        do {
            try store.save(draft)
            dismiss()
        } catch {
            saveError = String(describing: error)
        }
    }
}

private struct NumberRow: Identifiable {
    let id = UUID()
    var raw: String = ""
    var label: String = ""
}
