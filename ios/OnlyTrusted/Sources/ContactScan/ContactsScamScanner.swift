// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import Contacts
import Foundation

/// Scans the system contacts database against the bundled scam-numbers feed. Mirrors
/// Android `ContactsScamScanner`. All work is local — contacts are enumerated via
/// CNContactStore, numbers normalised to E.164-ish, and compared against
/// `Data/scam_numbers.v1.txt`. Nothing is transmitted off-device.
enum ContactsScamScanner {

    struct ScamMatch: Identifiable {
        let id = UUID()
        let contactName: String
        let phoneNumber: String
        let normalisedNumber: String
    }

    struct ScamScanReport {
        let contactsScanned: Int
        let numbersScanned: Int
        let matches: [ScamMatch]
    }

    /// Requests Contacts permission, enumerates all contacts, and runs the cross-check.
    /// Returns the report via the completion handler on the main thread.
    static func scan(completion: @escaping @Sendable (ScamScanReport, Error?) -> Void) {
        let store = CNContactStore()
        store.requestAccess(for: .contacts) { granted, err in
            if let err {
                DispatchQueue.main.async { completion(ScamScanReport(contactsScanned: 0, numbersScanned: 0, matches: []), err) }
                return
            }
            guard granted else {
                DispatchQueue.main.async {
                    completion(ScamScanReport(contactsScanned: 0, numbersScanned: 0, matches: []),
                               NSError(domain: "ContactsScamScanner", code: 1,
                                       userInfo: [NSLocalizedDescriptionKey: "Contacts access denied"]))
                }
                return
            }
            DispatchQueue.global(qos: .userInitiated).async {
                let scamNumbers = loadScamNumbers()
                var contactsScanned = 0
                var numbersScanned = 0
                var matches: [ScamMatch] = []
                let keys: [CNKeyDescriptor] = [
                    CNContactFamilyNameKey as CNKeyDescriptor,
                    CNContactGivenNameKey as CNKeyDescriptor,
                    CNContactPhoneNumbersKey as CNKeyDescriptor
                ]
                let request = CNContactFetchRequest(keysToFetch: keys)
                do {
                    try store.enumerateContacts(with: request) { contact, _ in
                        contactsScanned += 1
                        let name = [contact.givenName, contact.familyName]
                            .filter { !$0.isEmpty }
                            .joined(separator: " ")
                            .ifEmpty("Unknown")
                        for labelled in contact.phoneNumbers {
                            numbersScanned += 1
                            let raw = labelled.value.stringValue
                            let normalised = normalise(raw)
                            if scamNumbers.contains(normalised) {
                                matches.append(ScamMatch(contactName: name,
                                                         phoneNumber: raw,
                                                         normalisedNumber: normalised))
                            }
                        }
                    }
                } catch {
                    DispatchQueue.main.async { completion(ScamScanReport(contactsScanned: contactsScanned,
                                                                         numbersScanned: numbersScanned,
                                                                         matches: matches), error) }
                    return
                }
                let report = ScamScanReport(contactsScanned: contactsScanned,
                                            numbersScanned: numbersScanned,
                                            matches: matches)
                DispatchQueue.main.async { completion(report, nil) }
            }
        }
    }

    private static func loadScamNumbers() -> Set<String> {
        let bundle = Bundle.main
        guard let url = bundle.url(forResource: "scam_numbers.v1", withExtension: "txt", subdirectory: "Data")
                ?? bundle.url(forResource: "scam_numbers.v1", withExtension: "txt"),
              let text = try? String(contentsOf: url, encoding: .utf8) else {
            return []
        }
        var out: Set<String> = []
        for raw in text.split(whereSeparator: { $0.isNewline }) {
            let trimmed = raw.trimmingCharacters(in: .whitespaces)
            if trimmed.isEmpty || trimmed.hasPrefix("#") { continue }
            out.insert(normalise(trimmed))
        }
        return out
    }

    /// Normalise to E.164-ish digits with "+" prefix. 10-digit numbers get "+1" (NANP).
    private static func normalise(_ raw: String) -> String {
        let digits = raw.filter { $0.isNumber }
        if raw.hasPrefix("+") { return "+" + digits }
        if digits.count == 10 { return "+1" + digits }
        return "+" + digits
    }
}

private extension String {
    func ifEmpty(_ fallback: String) -> String { isEmpty ? fallback : self }
}
