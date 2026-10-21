// SPDX-FileCopyrightText: 2026 57Million LLC
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation
import SwiftUI

enum CallHandlingMode: String, CaseIterable, Identifiable {
    case silence
    case voicemail
    case block

    var id: String { rawValue }
    var title: String {
        switch self {
        case .silence:   return "Silence (recommended)"
        case .voicemail: return "Send to voicemail"
        case .block:     return "Block completely"
        }
    }
    var detail: String {
        switch self {
        case .silence:   return "Calls are silenced and logged."
        case .voicemail: return "Send unknown calls to voicemail."
        case .block:     return "Reject calls from unknown numbers."
        }
    }
}

@MainActor
final class BlockingPreferences: ObservableObject {
    static let shared = BlockingPreferences()

    @Published var blockUnknownCallers: Bool {
        didSet { d.set(blockUnknownCallers, forKey: Keys.callers) }
    }
    @Published var silenceUnknownTexts: Bool {
        didSet { d.set(silenceUnknownTexts, forKey: Keys.texts) }
    }
    @Published var spamProtection: Bool {
        didSet { d.set(spamProtection, forKey: Keys.spam) }
    }
    @Published var callHandling: CallHandlingMode {
        didSet { d.set(callHandling.rawValue, forKey: Keys.callHandling) }
    }

    private let d = UserDefaults.standard
    private enum Keys {
        static let callers = "block.callers.v1"
        static let texts = "block.texts.v1"
        static let spam = "block.spam.v1"
        static let callHandling = "block.callHandling.v1"
    }

    private init() {
        blockUnknownCallers = d.object(forKey: Keys.callers) as? Bool ?? true
        silenceUnknownTexts = d.object(forKey: Keys.texts) as? Bool ?? true
        spamProtection = d.object(forKey: Keys.spam) as? Bool ?? true
        let raw = d.string(forKey: Keys.callHandling) ?? CallHandlingMode.silence.rawValue
        callHandling = CallHandlingMode(rawValue: raw) ?? .silence
    }
}

struct BlockingOptionsCard: View {
    @ObservedObject var prefs: BlockingPreferences

    var body: some View {
        VStack(spacing: 18) {
            toggleCard
            callHandlingCard
        }
    }

    private var toggleCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 14) {
                CardHeader(icon: "slider.horizontal.3", title: "Blocking & Unknown")
                VStack(spacing: 0) {
                    OptionToggleRow(
                        icon: "phone.down.fill", tint: Color.appAccent,
                        title: "Block unknown callers",
                        detail: "Silence calls from numbers not in your trusted list.",
                        isOn: $prefs.blockUnknownCallers
                    )
                    Divider().padding(.leading, 44)
                    OptionToggleRow(
                        icon: "message.badge.filled.fill", tint: Color.appAccent,
                        title: "Silence unknown texts",
                        detail: "Move unknown messages to a separate folder.",
                        isOn: $prefs.silenceUnknownTexts
                    )
                    Divider().padding(.leading, 44)
                    OptionToggleRow(
                        icon: "exclamationmark.octagon.fill", tint: Color.appAccent,
                        title: "Spam protection",
                        detail: "Block common spam patterns on this device (no cloud).",
                        isOn: $prefs.spamProtection
                    )
                }
            }
        }
    }

    private var callHandlingCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 14) {
                CardHeader(icon: "phone.circle.fill", title: "Call handling")
                VStack(spacing: 0) {
                    ForEach(Array(CallHandlingMode.allCases.enumerated()), id: \.element.id) { idx, mode in
                        CallHandlingRow(
                            mode: mode,
                            isSelected: prefs.callHandling == mode,
                            onTap: { prefs.callHandling = mode }
                        )
                        if idx < CallHandlingMode.allCases.count - 1 {
                            Divider().padding(.leading, 44)
                        }
                    }
                }
            }
        }
    }
}

private struct OptionToggleRow: View {
    let icon: String
    let tint: Color
    let title: String
    let detail: String
    @Binding var isOn: Bool

    var body: some View {
        Toggle(isOn: $isOn) {
            HStack(spacing: 14) {
                ZStack {
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .fill(tint.opacity(0.14))
                    Image(systemName: icon)
                        .font(.body.weight(.semibold))
                        .foregroundStyle(tint)
                }
                .frame(width: 36, height: 36)
                .decorativeAccessibility()

                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Color.appPrimaryText)
                    Text(detail)
                        .font(.caption)
                        .foregroundStyle(Color.appSecondaryText)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
        .tint(Color.appAccent)
        .padding(.vertical, 10)
    }
}

private struct CallHandlingRow: View {
    let mode: CallHandlingMode
    let isSelected: Bool
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 14) {
                ZStack {
                    Circle()
                        .stroke(isSelected ? Color.appAccent : Color.appTertiaryText.opacity(0.4),
                                lineWidth: 2)
                        .frame(width: 22, height: 22)
                    if isSelected {
                        Circle()
                            .fill(Color.appAccent)
                            .frame(width: 12, height: 12)
                    }
                }
                .frame(width: 36, height: 36)
                .decorativeAccessibility()

                VStack(alignment: .leading, spacing: 2) {
                    Text(mode.title)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Color.appPrimaryText)
                    Text(mode.detail)
                        .font(.caption)
                        .foregroundStyle(Color.appSecondaryText)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer()
            }
            .padding(.vertical, 10)
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(isSelected ? [.isSelected, .isButton] : .isButton)
    }
}
