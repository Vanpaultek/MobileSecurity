// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation
import SwiftUI
import UIKit

enum CallHandlingMode: String, CaseIterable, Identifiable {
    case silence, voicemail, block

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
    var icon: String {
        switch self {
        case .silence:   return "phone.fill"
        case .voicemail: return "recordingtape"
        case .block:     return "nosign"
        }
    }
    var iconTint: Color {
        switch self {
        case .silence:   return Color(red: 0.22, green: 0.56, blue: 0.95) // blue
        case .voicemail: return Color.appTertiaryText
        case .block:     return Color.appDanger
        }
    }
}

enum TextHandlingMode: String, CaseIterable, Identifiable {
    case separateFolder, deleteAutomatically, silenceButKeep

    var id: String { rawValue }
    var title: String {
        switch self {
        case .separateFolder:      return "Move to separate folder (recommended)"
        case .deleteAutomatically: return "Delete automatically"
        case .silenceButKeep:      return "Silence but keep"
        }
    }
    var detail: String {
        switch self {
        case .separateFolder:      return "Keep unknown messages out of your main inbox."
        case .deleteAutomatically: return "Remove unknown messages without notification."
        case .silenceButKeep:      return "Keep in a separate folder without alerts."
        }
    }
    var icon: String {
        switch self {
        case .separateFolder:      return "message.badge.filled.fill"
        case .deleteAutomatically: return "trash.fill"
        case .silenceButKeep:      return "eye.slash.fill"
        }
    }
    var iconTint: Color {
        switch self {
        case .separateFolder:      return Color.blockingPurple
        case .deleteAutomatically: return Color.appDanger
        case .silenceButKeep:      return Color.appTertiaryText
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
    @Published var textHandling: TextHandlingMode {
        didSet { d.set(textHandling.rawValue, forKey: Keys.textHandling) }
    }

    private let d = UserDefaults.standard
    private enum Keys {
        static let callers = "blocking.block_unknown_callers"
        static let texts = "blocking.silence_unknown_texts"
        static let spam = "blocking.spam_protection"
        static let callHandling = "blocking.call_handling_mode"
        static let textHandling = "blocking.text_handling_mode"
    }

    private init() {
        blockUnknownCallers = d.object(forKey: Keys.callers) as? Bool ?? true
        silenceUnknownTexts = d.object(forKey: Keys.texts) as? Bool ?? true
        spamProtection = d.object(forKey: Keys.spam) as? Bool ?? true
        let callRaw = d.string(forKey: Keys.callHandling) ?? CallHandlingMode.silence.rawValue
        callHandling = CallHandlingMode(rawValue: callRaw) ?? .silence
        let textRaw = d.string(forKey: Keys.textHandling) ?? TextHandlingMode.separateFolder.rawValue
        textHandling = TextHandlingMode(rawValue: textRaw) ?? .separateFolder
    }
}

// MARK: - Public composite view used by BlockListScreen

struct BlockingOptionsCard: View {
    @ObservedObject var prefs: BlockingPreferences

    var body: some View {
        VStack(spacing: 20) {
            // Three stacked toggle cards (each in its own rounded surface, per mockup)
            VStack(spacing: 12) {
                ToggleCard(
                    icon: "shield.lefthalf.filled",
                    tint: Color.blockingOrange,
                    title: "Block unknown callers",
                    detail: "Silence calls from numbers not in your trusted list.",
                    isOn: $prefs.blockUnknownCallers
                )
                ToggleCard(
                    icon: "message.badge.filled.fill",
                    tint: Color.blockingPurple,
                    title: "Silence unknown texts",
                    detail: "Move unknown messages to a separate folder.",
                    isOn: $prefs.silenceUnknownTexts
                )
                ToggleCard(
                    icon: "exclamationmark.shield.fill",
                    tint: Color.blockingAmber,
                    title: "Spam protection",
                    detail: "Block common spam patterns on this device (no cloud).",
                    isOn: $prefs.spamProtection
                )
            }

            // Call handling section
            RadioGroupSection(
                title: "Call handling",
                subtitle: "Choose how to handle calls from unknown numbers.",
                selectedId: prefs.callHandling.id,
                options: CallHandlingMode.allCases.map { mode in
                    .init(id: mode.id,
                          icon: mode.icon,
                          tint: mode.iconTint,
                          title: mode.title,
                          detail: mode.detail,
                          onTap: { prefs.callHandling = mode })
                },
                footnote: "iOS applies these preferences via the Call Directory extension. Carrier-level blocks are outside any app's control."
            )

            // Text handling section
            RadioGroupSection(
                title: "Text handling",
                subtitle: "Choose how to handle text messages from unknown numbers.",
                selectedId: prefs.textHandling.id,
                options: TextHandlingMode.allCases.map { mode in
                    .init(id: mode.id,
                          icon: mode.icon,
                          tint: mode.iconTint,
                          title: mode.title,
                          detail: mode.detail,
                          onTap: { prefs.textHandling = mode })
                },
                footnote: nil
            )

            MutedExtensionRow(
                label: "iOS extensions needed",
                value: "Enable in iOS Settings",
                action: openIOSSettings
            )
        }
    }

    private func openIOSSettings() {
        if let url = URL(string: "App-prefs:"), UIApplication.shared.canOpenURL(url) {
            UIApplication.shared.open(url)
            return
        }
        if let url = URL(string: UIApplication.openSettingsURLString) {
            UIApplication.shared.open(url)
        }
    }
}

// MARK: - Shared palette extras used by this screen only

extension Color {
    static let blockingOrange = Color(red: 245.0/255.0, green: 158.0/255.0, blue: 11.0/255.0)
    static let blockingPurple = Color(red: 168.0/255.0, green: 85.0/255.0, blue: 247.0/255.0)
    static let blockingAmber  = Color(red: 234.0/255.0, green: 179.0/255.0, blue: 8.0/255.0)
}

// MARK: - Toggle card (icon chip + title + detail + cyan toggle, each in its own surface)

private struct ToggleCard: View {
    let icon: String
    let tint: Color
    let title: String
    let detail: String
    @Binding var isOn: Bool

    var body: some View {
        HStack(alignment: .center, spacing: 14) {
            IconChip(icon: icon, tint: tint)
            VStack(alignment: .leading, spacing: 3) {
                Text(title)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Color.appPrimaryText)
                Text(detail)
                    .font(.system(size: 12))
                    .foregroundStyle(Color.appSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 8)
            Toggle("", isOn: $isOn)
                .labelsHidden()
                .tint(Color.appBrightCyan)
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(Color.appElevatedSurface)
                .overlay(
                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .stroke(Color.appBrightCyan.opacity(0.12), lineWidth: 1)
                )
        )
    }
}

// MARK: - Radio group section (big title + subtitle, then a card with radio rows)

private struct BlockingRadioRowSpec: Identifiable {
    let id: String
    let icon: String
    let tint: Color
    let title: String
    let detail: String
    let onTap: () -> Void
}

private struct RadioGroupSection: View {
    let title: String
    let subtitle: String
    let selectedId: String
    let options: [BlockingRadioRowSpec]
    let footnote: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.system(size: 24, weight: .bold, design: .rounded))
                .foregroundStyle(Color.appPrimaryText)
            Text(subtitle)
                .font(.system(size: 13))
                .foregroundStyle(Color.appSecondaryText)
                .fixedSize(horizontal: false, vertical: true)
            RadioOptionsCard(options: options, selectedId: selectedId)
            if let footnote {
                Text(footnote)
                    .font(.caption)
                    .foregroundStyle(Color.appSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.top, 2)
            }
        }
    }
}

private struct RadioOptionsCard: View {
    let options: [BlockingRadioRowSpec]
    let selectedId: String

    var body: some View {
        VStack(spacing: 0) {
            ForEach(Array(options.enumerated()), id: \.element.id) { idx, spec in
                BlockingRadioRow(spec: spec, isSelected: spec.id == selectedId)
                if idx < options.count - 1 {
                    Divider()
                        .background(Color.appTertiaryText.opacity(0.15))
                        .padding(.leading, 76)
                }
            }
        }
        .padding(.vertical, 4)
        .frame(maxWidth: .infinity)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(Color.appElevatedSurface)
                .overlay(
                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .stroke(Color.appBrightCyan.opacity(0.12), lineWidth: 1)
                )
        )
    }
}

private struct BlockingRadioRow: View {
    let spec: BlockingRadioRowSpec
    let isSelected: Bool

    var body: some View {
        Button(action: spec.onTap) {
            HStack(spacing: 14) {
                // Radio circle on the left
                ZStack {
                    Circle()
                        .stroke(isSelected ? Color.appBrightCyan : Color.appTertiaryText.opacity(0.5),
                                lineWidth: 2)
                        .frame(width: 24, height: 24)
                    if isSelected {
                        Circle()
                            .fill(Color.appBrightCyan)
                            .frame(width: 12, height: 12)
                            .shadow(color: Color.appBrightCyan.opacity(0.7), radius: 6, x: 0, y: 0)
                    }
                }
                .frame(width: 36, height: 36)

                // Icon chip
                IconChip(icon: spec.icon, tint: spec.tint)

                // Title + detail
                VStack(alignment: .leading, spacing: 3) {
                    Text(spec.title)
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(Color.appPrimaryText)
                        .fixedSize(horizontal: false, vertical: true)
                    Text(spec.detail)
                        .font(.system(size: 12))
                        .foregroundStyle(Color.appSecondaryText)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isSelected ? [.isSelected, .isButton] : .isButton)
    }
}

// MARK: - Colored icon chip (rounded square with tinted fill + icon)

private struct IconChip: View {
    let icon: String
    let tint: Color

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 11, style: .continuous)
                .fill(tint)
                .shadow(color: tint.opacity(0.5), radius: 8, x: 0, y: 2)
            Image(systemName: icon)
                .font(.system(size: 20, weight: .semibold))
                .foregroundStyle(.white)
        }
        .frame(width: 44, height: 44)
        .decorativeAccessibility()
    }
}

// MARK: - Muted iOS activation row (kept functionality)

private struct MutedExtensionRow: View {
    let label: String
    let value: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 14) {
                ZStack {
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .fill(Color.appTertiaryText.opacity(0.14))
                    Image(systemName: "gearshape.fill")
                        .font(.body.weight(.semibold))
                        .foregroundStyle(Color.appTertiaryText)
                }
                .frame(width: 36, height: 36)
                .decorativeAccessibility()

                VStack(alignment: .leading, spacing: 2) {
                    Text(label)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Color.appSecondaryText)
                    Text(value)
                        .font(.caption)
                        .foregroundStyle(Color.appTertiaryText)
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(Color.appTertiaryText)
                    .decorativeAccessibility()
            }
            .padding(.vertical, 10)
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(label). \(value)")
    }
}
