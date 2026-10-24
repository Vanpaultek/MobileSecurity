// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation
import SwiftUI
#if canImport(CoreTelephony)
import CoreTelephony
#endif

/// Read-only snapshot of the user's region derived from OS signals. 100% on-device, zero
/// network, no permission prompts beyond what we already have.
///
/// Three signals, in priority order when they disagree:
///   1. OS Locale region (set by the user in iOS Settings → General → Language & Region)
///   2. OS TimeZone (follows Settings → General → Date & Time)
///   3. SIM country ISO code (via CoreTelephony; may be nil on devices without a SIM)
///
/// The authoritative value for feature gating is `country` — a 2-letter ISO 3166-1
/// alpha-2 code, uppercase (e.g. "US", "IN", "GB"). Features that filter catalogs by
/// region (Identity Protection, Data Removal Scan, Carrier spam report) read this.
///
/// Travellers: if the user has a US SIM but lives in India (Locale=IN, TZ=Asia/Kolkata,
/// SIM=US), we prefer Locale as the "home" signal — matches most phone setups.
@MainActor
final class RegionState: ObservableObject {

    static let shared = RegionState()

    /// 2-letter ISO 3166-1 alpha-2 country code, uppercase. Falls back to "US" if we
    /// can't determine anything — a conservative default that keeps the current catalog
    /// behavior for users with weird setups.
    @Published private(set) var country: String

    /// Locale region display name (e.g. "India", "United States").
    @Published private(set) var countryName: String

    /// IANA timezone identifier (e.g. "Asia/Kolkata", "America/Los_Angeles").
    @Published private(set) var timeZoneId: String

    /// BCP-47 language tag (e.g. "en-IN", "hi-IN", "en-US").
    @Published private(set) var languageTag: String

    /// SIM carrier country ISO (nullable; nil = no SIM / airplane mode / permission denied).
    @Published private(set) var simCountry: String?

    /// SIM carrier display name (nullable, e.g. "T-Mobile", "Jio").
    @Published private(set) var simCarrier: String?

    private init() {
        let locale = Locale.current
        let regionCode = (locale.region?.identifier ?? "US").uppercased()
        self.country = regionCode
        self.countryName = locale.localizedString(forRegionCode: regionCode) ?? regionCode
        self.timeZoneId = TimeZone.current.identifier
        self.languageTag = locale.identifier.replacingOccurrences(of: "_", with: "-")

        #if canImport(CoreTelephony)
        let networkInfo = CTTelephonyNetworkInfo()
        if let providers = networkInfo.serviceSubscriberCellularProviders,
           let primary = providers.values.first(where: { $0.isoCountryCode != nil }) {
            self.simCountry = primary.isoCountryCode?.uppercased()
            self.simCarrier = primary.carrierName
        } else {
            self.simCountry = nil
            self.simCarrier = nil
        }
        #else
        self.simCountry = nil
        self.simCarrier = nil
        #endif
    }

    /// Short human-readable description for the Settings "Region" row.
    var displaySummary: String {
        var parts: [String] = ["\(countryName)", timeZoneId, languageTag]
        if let simCountry {
            parts.append("SIM: \(simCountry)" + (simCarrier.map { " · \($0)" } ?? ""))
        }
        return parts.joined(separator: " · ")
    }
}
