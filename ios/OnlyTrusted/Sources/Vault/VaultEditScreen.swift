// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import SwiftUI
import LocalAuthentication

/// Mockup #10 "Add Trusted Contact" form.
///
/// Layout (top → bottom):
///   • Nav bar — Cancel · "Add Trusted Contact" · Save (disabled when invalid)
///   • Add Photo circle (placeholder — tap shows "coming soon" alert)
///   • Name field
///   • Phone number row: country code picker + digits input
///   • Label pill chips: Family / Work / Medical / Other / Custom
///     ("Custom" reveals a free-form text input below)
///   • Notes multiline
///
/// Validation: Save enabled when `name` is non-blank AND the phone has ≥7 digits.
struct VaultEditScreen: View {
    let contactId: UUID?
    @ObservedObject var store: VaultStore
    @Environment(\.dismiss) private var dismiss

    // Form state
    @State private var name = ""
    @State private var countryCode: CountryCode = .us
    @State private var phoneDigits = ""
    @State private var selectedLabel: LabelChip = .family
    @State private var customLabel = ""
    @State private var notes = ""

    // Preserved data model fields the mockup does not surface
    @State private var isOwner = false
    @State private var email = ""
    @State private var address = ""

    // Sheets / alerts
    @State private var showCountryPicker = false
    @State private var showPhotoNotice = false
    @State private var showContactPicker = false

    @State private var authorised: Bool
    @State private var authError: String?
    @State private var saveError: String?

    init(contactId: UUID?, store: VaultStore) {
        self.contactId = contactId
        self.store = store
        _authorised = State(initialValue: contactId == nil)
    }

    // MARK: - Validation

    private var trimmedName: String {
        name.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private var resolvedLabel: String {
        switch selectedLabel {
        case .custom:
            return customLabel.trimmingCharacters(in: .whitespacesAndNewlines)
        default:
            return selectedLabel.rawValue
        }
    }

    private var canSave: Bool {
        authorised &&
        !trimmedName.isEmpty &&
        phoneDigits.count >= 7
    }

    var body: some View {
        ZStack {
            if !authorised {
                lockedView
            } else {
                ScrollView(.vertical, showsIndicators: false) {
                    VStack(spacing: 24) {
                        photoPicker
                        if contactId == nil { pickFromContactsButton }
                        nameField
                        phoneRow
                        labelRow
                        notesField

                        if let saveError {
                            Text(saveError)
                                .font(.footnote)
                                .foregroundStyle(Color.appDanger)
                                .frame(maxWidth: .infinity, alignment: .leading)
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 20)
                    .padding(.bottom, 32)
                }
            }
        }
        .appBackdrop()
        .faceLockShield()
        .navigationTitle(contactId == nil ? "Add Trusted Contact" : "Edit Trusted Contact")
        .navigationBarTitleDisplayMode(.inline)
        .toolbarBackground(Color.appScreenBackground, for: .navigationBar)
        .toolbarBackground(.visible, for: .navigationBar)
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button(action: { dismiss() }) {
                    HStack(spacing: 4) {
                        Image(systemName: "chevron.left")
                            .font(.system(size: 15, weight: .semibold))
                        Text("Contacts")
                            .font(.system(size: 15, weight: .semibold))
                    }
                    .foregroundStyle(Color.appBrightCyan)
                }
            }
            ToolbarItem(placement: .confirmationAction) {
                Button(action: { attemptSave() }) {
                    Text("Save")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundStyle(canSave ? Color.appScreenBackground : Color.appTertiaryText)
                        .padding(.horizontal, 18)
                        .padding(.vertical, 8)
                        .background(
                            Capsule(style: .continuous)
                                .fill(canSave ? Color.appBrightCyan : Color.appElevatedSurface)
                                .shadow(color: canSave ? Color.appBrightCyan.opacity(0.5) : .clear,
                                        radius: 8, x: 0, y: 0)
                        )
                }
                .disabled(!canSave)
            }
            if contactId != nil && authorised {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(role: .destructive) {
                        if let id = contactId { try? store.delete(id: id); dismiss() }
                    } label: {
                        Image(systemName: "trash").decorativeAccessibility()
                    }
                    .accessibilityLabel("Delete contact")
                    .minimumHitTarget()
                }
            }
        }
        .sheet(isPresented: $showCountryPicker) {
            CountryCodePickerSheet(selected: $countryCode)
        }
        .alert("Photo attach coming soon",
               isPresented: $showPhotoNotice) {
            Button("OK", role: .cancel) {}
        } message: {
            Text("Attaching a photo for a trusted contact isn't supported in this build yet.")
        }
        .task {
            if !authorised { authenticate() }
            if let id = contactId, authorised { prefill(id) }
        }
        .onChange(of: authorised) { _, now in
            if now, let id = contactId { prefill(id) }
        }
    }

    // MARK: - Sections

    private var photoPicker: some View {
        HStack(alignment: .center, spacing: 12) {
            Spacer(minLength: 0)

            // Large portrait circle with cyan glowing border
            ZStack {
                Circle()
                    .fill(Color.appElevatedSurface)
                    .frame(width: 160, height: 160)
                Image(systemName: "person.crop.circle.fill")
                    .font(.system(size: 92, weight: .regular))
                    .foregroundStyle(Color.appSecondaryText)
            }
            .overlay(
                Circle()
                    .stroke(Color.appBrightCyan, lineWidth: 2)
                    .shadow(color: Color.appBrightCyan.opacity(0.7), radius: 10, x: 0, y: 0)
            )

            // Floating "Add Photo" circle button beside it (camera icon + label beneath)
            Button {
                showPhotoNotice = true
            } label: {
                VStack(spacing: 6) {
                    ZStack {
                        Circle()
                            .stroke(Color.appBrightCyan, lineWidth: 1.5)
                            .frame(width: 72, height: 72)
                            .shadow(color: Color.appBrightCyan.opacity(0.6), radius: 8, x: 0, y: 0)
                        Image(systemName: "camera.fill")
                            .font(.system(size: 26, weight: .regular))
                            .foregroundStyle(Color.appBrightCyan)
                    }
                    Text("Add\nPhoto")
                        .font(.footnote.weight(.semibold))
                        .multilineTextAlignment(.center)
                        .foregroundStyle(Color.appBrightCyan)
                }
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Add contact photo")

            Spacer(minLength: 0)
        }
        .padding(.vertical, 8)
    }

    /// "Pick from Contacts" button — only shown when adding a new contact. Opens the
    /// system CNContactPickerViewController; on return, pre-fills `name` + the phone
    /// fields. The user can still edit the pre-filled fields before saving. Honest
    /// claim: we only read the ONE contact tapped, never the full address book.
    private var pickFromContactsButton: some View {
        VStack(spacing: 6) {
            Button {
                showContactPicker = true
            } label: {
                HStack {
                    Image(systemName: "person.crop.circle.badge.plus")
                        .foregroundStyle(Color.appBrightCyan)
                    Text("Pick from Contacts")
                        .font(.body.weight(.semibold))
                        .foregroundStyle(Color.appBrightCyan)
                    Spacer()
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 12)
                .background(Color.appBrightCyan.opacity(0.15), in: RoundedRectangle(cornerRadius: 12))
            }
            .buttonStyle(.plain)
            Text("We only read the one contact you tap — never your full address book.")
                .font(.caption)
                .foregroundStyle(Color.appSecondaryText)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .sheet(isPresented: $showContactPicker) {
            ContactPickerSheet { pickedName, pickedPhone in
                if !pickedName.isEmpty { name = pickedName }
                if !pickedPhone.isEmpty {
                    // Strip to digits + parse country-code prefix.
                    let digitsOnly = pickedPhone.filter(\.isNumber)
                    if digitsOnly.count == 11 && digitsOnly.hasPrefix("1") {
                        countryCode = .us
                        phoneDigits = String(digitsOnly.dropFirst())
                    } else if digitsOnly.count == 10 {
                        countryCode = .us
                        phoneDigits = digitsOnly
                    } else {
                        phoneDigits = digitsOnly
                    }
                }
                showContactPicker = false
            }
        }
    }

    private var nameField: some View {
        VStack(alignment: .leading, spacing: 8) {
            fieldLabel("Name")
            HStack(spacing: 12) {
                Image(systemName: "person.fill")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(Color.settingsPurple)
                    .frame(width: 24)
                TextField(
                    "",
                    text: $name,
                    prompt: Text("Priya").foregroundColor(Color.appTertiaryText)
                )
                .textContentType(.name)
                .autocorrectionDisabled(true)
                .foregroundStyle(Color.appPrimaryText)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 14)
            .background(fieldBorderBackground)
        }
    }

    private var phoneRow: some View {
        VStack(alignment: .leading, spacing: 8) {
            fieldLabel("Phone Number")
            HStack(spacing: 0) {
                // Phone icon + flag/dial-code dropdown on the left (one unified pill)
                Button {
                    showCountryPicker = true
                } label: {
                    HStack(spacing: 8) {
                        Image(systemName: "phone.fill")
                            .font(.system(size: 18, weight: .semibold))
                            .foregroundStyle(Color.appBrightCyan)
                        Text(countryCode.flag)
                            .font(.title3)
                        Image(systemName: "chevron.down")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(Color.appBrightCyan)
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 14)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Country code \(countryCode.name)")

                // Divider between dropdown and input
                Rectangle()
                    .fill(Color.appBrightCyan.opacity(0.25))
                    .frame(width: 1)
                    .padding(.vertical, 10)

                // Phone digits input
                TextField(
                    "",
                    text: Binding(
                        get: { phoneDigits },
                        set: { phoneDigits = sanitiseDigits($0) }
                    ),
                    prompt: Text("\(countryCode.dialCode) 415 555 1234")
                        .foregroundColor(Color.appTertiaryText)
                )
                .keyboardType(.phonePad)
                .textContentType(.telephoneNumber)
                .foregroundStyle(Color.appPrimaryText)
                .padding(.horizontal, 14)
                .padding(.vertical, 14)
            }
            .background(fieldBorderBackground)
        }
    }

    private var labelRow: some View {
        VStack(alignment: .leading, spacing: 10) {
            fieldLabel("Label")
            // Row 1: Family / Work / Medical
            HStack(spacing: 10) {
                ForEach(Array(LabelChip.allCases.prefix(3)), id: \.self) { chip in
                    LabelPill(chip: chip, selected: selectedLabel == chip) {
                        selectedLabel = chip
                        if chip != .custom { customLabel = "" }
                    }
                }
            }
            // Row 2: Other / Custom
            HStack(spacing: 10) {
                ForEach(Array(LabelChip.allCases.dropFirst(3)), id: \.self) { chip in
                    LabelPill(chip: chip, selected: selectedLabel == chip) {
                        selectedLabel = chip
                        if chip != .custom { customLabel = "" }
                    }
                }
                Spacer(minLength: 0)
            }
            if selectedLabel == .custom {
                TextField(
                    "",
                    text: $customLabel,
                    prompt: Text("Custom label").foregroundColor(Color.appTertiaryText)
                )
                .foregroundStyle(Color.appPrimaryText)
                .padding(.horizontal, 14)
                .padding(.vertical, 14)
                .background(fieldBorderBackground)
            }
        }
    }

    private var notesField: some View {
        VStack(alignment: .leading, spacing: 8) {
            fieldLabel("Notes (optional)")
            HStack(alignment: .top, spacing: 12) {
                Image(systemName: "doc.text.fill")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(Color.appBrightCyan)
                    .frame(width: 24)
                    .padding(.top, 2)
                TextField(
                    "",
                    text: $notes,
                    prompt: Text("E.g. sister, close friend, doctor…").foregroundColor(Color.appTertiaryText),
                    axis: .vertical
                )
                .lineLimit(3...6)
                .foregroundStyle(Color.appPrimaryText)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 14)
            .background(fieldBorderBackground)
        }
    }

    private var lockedView: some View {
        VStack(spacing: 16) {
            Image(systemName: "lock.fill")
                .font(.system(size: 44, weight: .semibold))
                .foregroundStyle(Color.appAccent)
            Text("Vault is locked")
                .font(.title3.weight(.semibold))
                .foregroundStyle(Color.appPrimaryText)
            Text("Unlock to edit this contact.")
                .font(.subheadline)
                .foregroundStyle(Color.appSecondaryText)
            if let authError {
                Text(authError)
                    .font(.footnote)
                    .foregroundStyle(Color.appDanger)
                    .multilineTextAlignment(.center)
            }
            Button("Try again", action: authenticate)
                .font(.body.weight(.semibold))
                .foregroundStyle(.black)
                .padding(.horizontal, 20)
                .padding(.vertical, 12)
                .background(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(Color.appAccent)
                )
                .padding(.top, 8)
        }
        .padding(32)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    // MARK: - Helpers

    private func fieldLabel(_ text: String) -> some View {
        Text(text)
            .font(.footnote.weight(.semibold))
            .foregroundStyle(Color.appSecondaryText)
    }

    private var fieldBackground: some View {
        RoundedRectangle(cornerRadius: 12, style: .continuous)
            .fill(Color.appCardBackground)
    }

    /// Field background with the neon cyan hairline border used across the new mockup.
    private var fieldBorderBackground: some View {
        RoundedRectangle(cornerRadius: 14, style: .continuous)
            .fill(Color.clear)
            .overlay(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .stroke(Color.appBrightCyan.opacity(0.4), lineWidth: 1)
            )
    }

    private func sanitiseDigits(_ raw: String) -> String {
        String(raw.filter { $0.isNumber }).prefix(15).description
    }

    private func authenticate() {
        let ctx = LAContext()
        var err: NSError?
        guard ctx.canEvaluatePolicy(.deviceOwnerAuthentication, error: &err) else {
            authorised = false
            authError = "Set a device passcode, Face ID, or Touch ID in iOS Settings before editing a vault entry."
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
        isOwner = existing.isOwner
        address = existing.addressPlain ?? ""
        notes = existing.notesPlain ?? ""

        if let relation = existing.relation, !relation.isEmpty {
            if let known = LabelChip(rawValue: relation) {
                selectedLabel = known
                customLabel = ""
            } else {
                selectedLabel = .custom
                customLabel = relation
            }
        } else {
            selectedLabel = .family
            customLabel = ""
        }

        if let first = existing.numbers.first {
            let (code, digits) = splitDialCode(first.normalisedNumber)
            countryCode = code ?? .us
            phoneDigits = digits
        }
    }

    private func splitDialCode(_ raw: String) -> (CountryCode?, String) {
        let digits = raw.filter { $0.isNumber }
        guard raw.hasPrefix("+") else {
            return (nil, String(digits))
        }
        for code in CountryCode.allCases.sorted(by: { $0.dialCode.count > $1.dialCode.count }) {
            let strip = String(code.dialCode.dropFirst()) // drop "+"
            if digits.hasPrefix(strip) {
                return (code, String(digits.dropFirst(strip.count)))
            }
        }
        return (nil, String(digits))
    }

    private func attemptSave() {
        let normalised = "+\(String(countryCode.dialCode.dropFirst()))\(phoneDigits)"
        let labelText = resolvedLabel
        let draft = TrustedContactDraft(
            id: contactId,
            displayName: trimmedName,
            relation: labelText.isEmpty ? nil : labelText,
            isOwner: isOwner,
            email: email.isEmpty ? nil : email,
            address: address.isEmpty ? nil : address,
            notes: notes.isEmpty ? nil : notes,
            phoneNumbers: [(normalised, labelText.isEmpty ? nil : labelText)]
        )
        do {
            try store.save(draft)
            dismiss()
        } catch {
            saveError = String(describing: error)
        }
    }
}

// MARK: - Label chip card (icon on left + label, rounded rectangle, cyan selected border)

private struct LabelPill: View {
    let chip: LabelChip
    let selected: Bool
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 10) {
                Image(systemName: chip.icon)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Color.appBrightCyan)
                Text(chip.rawValue)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Color.appPrimaryText)
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 14)
            .frame(maxWidth: .infinity)
            .background(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .fill(selected ? Color.appBrightCyan.opacity(0.08) : Color.clear)
                    .overlay(
                        RoundedRectangle(cornerRadius: 14, style: .continuous)
                            .stroke(
                                selected ? Color.appBrightCyan : Color.appBrightCyan.opacity(0.35),
                                lineWidth: selected ? 2 : 1
                            )
                    )
                    .shadow(color: selected ? Color.appBrightCyan.opacity(0.5) : .clear,
                            radius: 8, x: 0, y: 0)
            )
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? [.isSelected, .isButton] : [.isButton])
        .accessibilityLabel(chip.rawValue)
    }
}

// MARK: - Country codes

enum CountryCode: String, CaseIterable {
    case us, uk, ind, ca, au, de, fr, jp, br, mx

    var dialCode: String {
        switch self {
        case .us, .ca: return "+1"
        case .uk: return "+44"
        case .ind: return "+91"
        case .au: return "+61"
        case .de: return "+49"
        case .fr: return "+33"
        case .jp: return "+81"
        case .br: return "+55"
        case .mx: return "+52"
        }
    }

    var flag: String {
        switch self {
        case .us: return "🇺🇸"
        case .uk: return "🇬🇧"
        case .ind: return "🇮🇳"
        case .ca: return "🇨🇦"
        case .au: return "🇦🇺"
        case .de: return "🇩🇪"
        case .fr: return "🇫🇷"
        case .jp: return "🇯🇵"
        case .br: return "🇧🇷"
        case .mx: return "🇲🇽"
        }
    }

    var name: String {
        switch self {
        case .us: return "United States"
        case .uk: return "United Kingdom"
        case .ind: return "India"
        case .ca: return "Canada"
        case .au: return "Australia"
        case .de: return "Germany"
        case .fr: return "France"
        case .jp: return "Japan"
        case .br: return "Brazil"
        case .mx: return "Mexico"
        }
    }
}

private struct CountryCodePickerSheet: View {
    @Binding var selected: CountryCode
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                ForEach(CountryCode.allCases, id: \.self) { code in
                    Button {
                        selected = code
                        dismiss()
                    } label: {
                        HStack(spacing: 12) {
                            Text(code.flag).font(.title3)
                            Text(code.name)
                                .foregroundStyle(Color.appPrimaryText)
                            Spacer()
                            Text(code.dialCode)
                                .foregroundStyle(Color.appSecondaryText)
                            if selected == code {
                                Image(systemName: "checkmark")
                                    .foregroundStyle(Color.appAccent)
                            }
                        }
                    }
                    .listRowBackground(Color.appCardBackground)
                }
            }
            .scrollContentBackground(.hidden)
            .background(Color.appScreenBackground)
            .navigationTitle("Country code")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
        }
    }
}

// MARK: - Label chip enum

private enum LabelChip: String, CaseIterable {
    case family = "Family"
    case work = "Work"
    case medical = "Medical"
    case other = "Other"
    case custom = "Custom"

    var icon: String {
        switch self {
        case .family:  return "person.2.fill"
        case .work:    return "briefcase.fill"
        case .medical: return "heart.text.square.fill"
        case .other:   return "ellipsis.circle.fill"
        case .custom:  return "tag.fill"
        }
    }
}
