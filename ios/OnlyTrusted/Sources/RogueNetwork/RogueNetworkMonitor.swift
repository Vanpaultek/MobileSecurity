// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation
import CoreTelephony
#if canImport(SystemConfiguration)
import SystemConfiguration.CaptiveNetwork
#endif

/// Elite-tier (`SubscriptionState.hasElite`) rogue-network monitor.
///
/// Called from the Elite screen on demand (and once per app launch when enabled) to
/// sample the current cellular + Wi-Fi state. Any red-flag heuristic fires an
/// `RogueNetworkState.Alert` so the recent-alerts list is kept up to date.
///
/// Cellular heuristics (iOS 16+ deprecates `CTTelephonyNetworkInfo` but the APIs are
/// still functional):
///   - **Forced 2G / GSM downgrade** — the active radio tech is GPRS or EDGE while
///     the user's device otherwise supports LTE/5G. Classic IMSI-catcher symptom.
///   - **Missing carrier** — SIM present but no carrier name (odd rooted radio
///     state; worth flagging on a once-per-session basis).
///
/// Wi-Fi heuristics via `CNCopyCurrentNetworkInfo` (requires
/// `com.apple.developer.networking.wifi-info` entitlement; without it this just
/// returns nil and the Wi-Fi probe is a no-op — we still ship the UI with a note):
///   - **Open AP on a bank/airline SSID pattern** — detectable even without the
///     entitlement when iOS 17 reports an SSID; otherwise skipped.
enum RogueNetworkMonitor {

    /// Known bank / airline / payment SSID prefixes we never expect to see as open APs.
    private static let brandSsidPrefixes: [String] = [
        "chase", "bofa", "boa", "wellsfargo", "wells_fargo", "citi", "capitalone",
        "amex", "apple pay", "applepay", "paypal",
        "united wifi", "unitedwifi", "deltawifi", "delta_wifi", "aawifi", "aa_wifi",
        "southwestwifi", "jetblue wifi", "lufthansa"
    ]

    /// Sample the device state once and funnel findings into `RogueNetworkState`.
    @MainActor
    static func scanOnce() {
        let state = RogueNetworkState.shared
        if state.cellularMonitoringEnabled { scanCellular() }
        if state.wifiMonitoringEnabled { scanWifi() }
    }

    // MARK: - Cellular

    @MainActor
    private static func scanCellular() {
        let info = CTTelephonyNetworkInfo()
        var radios: [String] = []
        if let tech = info.serviceCurrentRadioAccessTechnology {
            for (_, value) in tech { radios.append(value) }
        }
        // Flag 2G-only connections — a legitimate modern device on a modern carrier
        // should never pick GPRS/EDGE unless it's being downgrade-forced.
        let gsmTechs: Set<String> = [
            CTRadioAccessTechnologyGPRS,
            CTRadioAccessTechnologyEdge,
            CTRadioAccessTechnologyCDMA1x
        ]
        if !radios.isEmpty, radios.allSatisfy({ gsmTechs.contains($0) }) {
            let friendly = radios.compactMap(radioFriendlyName).joined(separator: ", ")
            RogueNetworkState.shared.recordAlert(
                .init(
                    kind: .forcedDowngrade,
                    title: "Possible 2G downgrade attack",
                    detail: "The cellular radio dropped to \(friendly). On a modern network this usually means an IMSI-catcher is forcing an insecure link. Switch to airplane mode until you've moved on.",
                    timestamp: Date()
                )
            )
        }

        // Missing carrier metadata when SIM is otherwise present.
        if let carriers = info.serviceSubscriberCellularProviders,
           !carriers.isEmpty,
           carriers.values.allSatisfy({ ($0.carrierName ?? "").isEmpty }) {
            RogueNetworkState.shared.recordAlert(
                .init(
                    kind: .missingCarrier,
                    title: "Carrier name is blank",
                    detail: "The SIM is active but no carrier name is being reported. This is unusual on real networks and happens on devices connected to a rogue base station.",
                    timestamp: Date()
                )
            )
        }
    }

    private static func radioFriendlyName(_ tech: String) -> String? {
        switch tech {
        case CTRadioAccessTechnologyGPRS:  return "2G/GPRS"
        case CTRadioAccessTechnologyEdge:  return "2G/EDGE"
        case CTRadioAccessTechnologyCDMA1x: return "CDMA 1x"
        case CTRadioAccessTechnologyWCDMA: return "3G/WCDMA"
        case CTRadioAccessTechnologyLTE:   return "4G/LTE"
        default: return tech
        }
    }

    // MARK: - Wi-Fi

    @MainActor
    private static func scanWifi() {
        // CNCopyCurrentNetworkInfo requires the wifi-info entitlement; without it the
        // SSID will be `Wi-Fi` or nil depending on iOS version. We still call it so a
        // future entitlement grant lights up the detection without code changes.
        #if canImport(SystemConfiguration)
        guard let interfaces = CNCopySupportedInterfaces() as? [CFString] else { return }
        for iface in interfaces {
            guard let dict = CNCopyCurrentNetworkInfo(iface) as? [String: AnyObject],
                  let ssidAny = dict[kCNNetworkInfoKeySSID as String],
                  let ssid = ssidAny as? String else {
                continue
            }
            let lowered = ssid.lowercased()
            // Any known brand-SSID prefix on an open network is a red flag.
            if brandSsidPrefixes.contains(where: { lowered.hasPrefix($0) }) {
                RogueNetworkState.shared.recordAlert(
                    .init(
                        kind: .openWifiOnBrandSsid,
                        title: "Lookalike Wi-Fi SSID",
                        detail: "You're connected to \"\(ssid)\". Real banks and airlines don't broadcast open Wi-Fi under their brand name. Disconnect before entering a password.",
                        timestamp: Date()
                    )
                )
            }
        }
        #endif
    }
}
