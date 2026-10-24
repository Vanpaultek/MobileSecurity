// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import SwiftUI
import ContactsUI
import Contacts

/// SwiftUI wrapper around `CNContactPickerViewController` so VaultEditScreen can offer
/// "Pick from Contacts" alongside the manual-entry form. The user picks exactly ONE
/// contact; we read its display name + first phone number via the picker's result; the
/// user's broader address book is never scanned (that's the Contact Safety Scan screen).
///
/// Phase 2 should also persist `contact.identifier` on TrustedContactRecord so the vault
/// can live-read fresh name/number from `CNContactStore.unifiedContact(withIdentifier:)`
/// at display time. For now we just pre-fill the manual form fields.
struct ContactPickerSheet: UIViewControllerRepresentable {
    /// Called with (displayName, rawPhoneNumber) after a successful pick. Both may be
    /// empty — caller should validate.
    var onPick: (_ displayName: String, _ rawPhone: String) -> Void

    func makeUIViewController(context: Context) -> CNContactPickerViewController {
        let picker = CNContactPickerViewController()
        picker.displayedPropertyKeys = [CNContactPhoneNumbersKey]
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ uiViewController: CNContactPickerViewController, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator(onPick: onPick) }

    final class Coordinator: NSObject, CNContactPickerDelegate {
        let onPick: (String, String) -> Void
        init(onPick: @escaping (String, String) -> Void) { self.onPick = onPick }

        func contactPicker(_ picker: CNContactPickerViewController, didSelect contact: CNContact) {
            let name = [contact.givenName, contact.familyName]
                .filter { !$0.isEmpty }
                .joined(separator: " ")
            let phone = contact.phoneNumbers.first?.value.stringValue ?? ""
            onPick(name, phone)
        }

        func contactPicker(_ picker: CNContactPickerViewController,
                           didSelect contactProperty: CNContactProperty) {
            let name = [contactProperty.contact.givenName, contactProperty.contact.familyName]
                .filter { !$0.isEmpty }
                .joined(separator: " ")
            let phone = (contactProperty.value as? CNPhoneNumber)?.stringValue ?? ""
            onPick(name, phone)
        }
    }
}
