// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import SwiftUI

/// Premium-only Advanced Blocking screen. Edits quiet-hours window + NANP area-code
/// allowlist. Enforcement:
///  * Quiet Hours: queried by call/SMS routing paths before deciding whether to ring.
///  * Area-code allowlist: queried by the unknown-caller flagger before marking a call
///    as spam.
/// Free users see an upsell card.
struct AdvancedBlockingScreen: View {

    @StateObject private var rules = AdvancedBlocking.shared
    @StateObject private var sub = SubscriptionState.shared
    @State private var areaCodesDraft: String = ""
    @State private var toast: String?

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                heroCard
                if sub.hasPremium {
                    quietHoursCard
                    areaCodesCard
                    liveStatusCard
                } else {
                    upsellCard
                }
            }
            .padding(20)
        }
        .appBackdrop()
        .navigationTitle("Advanced Blocking")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            areaCodesDraft = rules.areaCodeAllowlist.joined(separator: ", ")
        }
        .overlay(alignment: .bottom) {
            if let toast {
                Text(toast)
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(Color.appScreenBackground)
                    .padding(.horizontal, 14).padding(.vertical, 10)
                    .background(Color.appAccent, in: Capsule())
                    .padding(.bottom, 24)
                    .transition(.opacity.combined(with: .move(edge: .bottom)))
            }
        }
    }

    private var heroCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 10) {
                CardHeader(icon: "line.horizontal.3.decrease.circle",
                           title: "Advanced Blocking",
                           tint: Color.appAccent)
                Text("Finer-grained rules for who can reach you and when. Rules are stored on this device and applied by the call + SMS routing paths.")
                    .font(.footnote)
                    .foregroundStyle(Color.appSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    private var quietHoursCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 12) {
                CardHeader(icon: "moon.stars", title: "Quiet Hours", tint: Color.appAccent)
                Toggle(isOn: $rules.quietHoursEnabled) {
                    Text("Only trusted contacts ring during the window below")
                        .font(.subheadline)
                        .foregroundStyle(Color.appPrimaryText)
                }
                .tint(Color.appAccent)

                HStack {
                    timePicker(label: "From", binding: Binding(
                        get: { minutesToDate(rules.quietStartMinutes) },
                        set: { rules.quietStartMinutes = dateToMinutes($0) }
                    ))
                    timePicker(label: "To", binding: Binding(
                        get: { minutesToDate(rules.quietEndMinutes) },
                        set: { rules.quietEndMinutes = dateToMinutes($0) }
                    ))
                }
                Text("Windows that cross midnight (e.g. 22:00 → 07:00) are supported.")
                    .font(.caption2)
                    .foregroundStyle(Color.appSecondaryText)
            }
        }
    }

    private func timePicker(label: String, binding: Binding<Date>) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label).font(.caption).foregroundStyle(Color.appSecondaryText)
            DatePicker("", selection: binding, displayedComponents: .hourAndMinute)
                .labelsHidden()
                .datePickerStyle(.compact)
        }
    }

    private var areaCodesCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 10) {
                CardHeader(icon: "phone.and.waveform", title: "Area-code allowlist", tint: Color.appAccent)
                Text("Comma-separated 3-digit codes. Any inbound caller from one of these area codes is never flagged as unknown-caller spam, even if they're not in your vault.")
                    .font(.caption)
                    .foregroundStyle(Color.appSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)
                HStack {
                    TextField("e.g. 415, 212, 650", text: $areaCodesDraft)
                        .keyboardType(.numbersAndPunctuation)
                        .autocapitalization(.none)
                        .disableAutocorrection(true)
                        .padding(.horizontal, 12).padding(.vertical, 10)
                        .background(
                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                .fill(Color.appScreenBackground)
                        )
                    Button {
                        let parsed = areaCodesDraft
                            .split { !$0.isNumber }
                            .map(String.init)
                            .filter { $0.count == 3 }
                        rules.areaCodeAllowlist = Array(Set(parsed)).sorted()
                        areaCodesDraft = rules.areaCodeAllowlist.joined(separator: ", ")
                        showToast("Saved \(rules.areaCodeAllowlist.count) code\(rules.areaCodeAllowlist.count == 1 ? "" : "s")")
                    } label: {
                        Text("Save").font(.footnote.weight(.semibold))
                            .padding(.horizontal, 14).padding(.vertical, 10)
                            .background(Color.appAccent, in: RoundedRectangle(cornerRadius: 10))
                            .foregroundStyle(Color.appScreenBackground)
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    private var liveStatusCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 10) {
                CardHeader(icon: "waveform.badge.magnifyingglass",
                           title: "Right now",
                           tint: Color.appSecondaryText)
                HStack {
                    Image(systemName: rules.isQuietHoursNow() ? "moon.fill" : "sun.max.fill")
                        .foregroundStyle(rules.isQuietHoursNow() ? Color.appAccent : Color.appWarning)
                    Text(rules.isQuietHoursNow() ? "Quiet hours are active" : "Quiet hours are OFF")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Color.appPrimaryText)
                }
                Text("Allowlisted area codes: \(rules.areaCodeAllowlist.isEmpty ? "none" : rules.areaCodeAllowlist.joined(separator: ", "))")
                    .font(.caption)
                    .foregroundStyle(Color.appSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    private var upsellCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 10) {
                CardHeader(icon: "crown.fill",
                           title: "Advanced Blocking (Premium)",
                           tint: Color.subGold)
                Text("Premium ($4.99/yr) unlocks Quiet Hours (only trusted contacts ring at night) and an area-code allowlist (whitelist specific NANP area codes so they're never flagged as unknown).")
                    .font(.footnote)
                    .foregroundStyle(Color.appSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    // MARK: - Helpers

    private func minutesToDate(_ m: Int) -> Date {
        var comps = DateComponents(); comps.hour = (m / 60) % 24; comps.minute = m % 60
        return Calendar.current.date(from: comps) ?? Date()
    }
    private func dateToMinutes(_ d: Date) -> Int {
        let c = Calendar.current.dateComponents([.hour, .minute], from: d)
        return (c.hour ?? 0) * 60 + (c.minute ?? 0)
    }
    private func showToast(_ msg: String) {
        withAnimation { toast = msg }
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.6) {
            withAnimation { toast = nil }
        }
    }
}
