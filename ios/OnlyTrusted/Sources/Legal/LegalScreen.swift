// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import SwiftUI

struct LegalScreen: View {
    /// Deep-link entry point. Pass a section title (e.g. "Privacy policy") to open directly
    /// on that tab. Nil defaults to the first section.
    let initialSectionTitle: String?

    @State private var selection: String
    @State private var showFullDetails = false
    @Environment(\.dismiss) private var dismiss

    init(initialSectionTitle: String? = nil) {
        self.initialSectionTitle = initialSectionTitle
        let resolved = LegalContent.sections.first(where: {
            initialSectionTitle != nil &&
                $0.title.caseInsensitiveCompare(initialSectionTitle!) == .orderedSame
        })?.id ?? LegalContent.sections.first?.id ?? ""
        _selection = State(initialValue: resolved)
    }

    var body: some View {
        VStack(spacing: 0) {
            VStack(spacing: 14) {
                header
                sectionDropdown
            }
            .padding(.horizontal, 18)
            .padding(.top, 6)
            .padding(.bottom, 14)
            .background(
                ZStack {
                    Color.appScreenBackground
                    Color.black.opacity(0.25)
                }
                .ignoresSafeArea(edges: .top)
            )

            ScrollView(.vertical, showsIndicators: false) {
                VStack(alignment: .leading, spacing: 20) {
                    if isHonestClaims {
                        honestClaimsIntro
                        honestClaimsCard
                        readFullDetailsCard
                    } else {
                        sectionTitle
                        sectionBody
                    }
                }
                .padding(.horizontal, 18)
                .padding(.top, 10)
                .padding(.bottom, 32)
            }
        }
        .appBackdrop()
        .navigationBarHidden(true)
        .sheet(isPresented: $showFullDetails) {
            NavigationStack {
                ScrollView {
                    VStack(alignment: .leading, spacing: 12) {
                        Text("Honest claims — full details")
                            .font(.appSectionTitle)
                        Text(honestClaimsBody)
                            .font(.appBody)
                            .foregroundStyle(Color.appPrimaryText)
                            .textSelection(.enabled)
                    }
                    .padding(20)
                }
                .appBackdrop()
                .navigationTitle("Honest claims")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .topBarTrailing) {
                        Button("Done") { showFullDetails = false }
                            .tint(Color.appBrightCyan)
                    }
                }
            }
        }
    }

    // MARK: - Header

    private var header: some View {
        ZStack {
            HStack {
                Button(action: { dismiss() }) {
                    HStack(spacing: 4) {
                        Image(systemName: "chevron.left")
                            .font(.system(size: 16, weight: .semibold))
                        Text("Settings")
                            .font(.system(size: 16, weight: .semibold))
                    }
                    .foregroundStyle(Color.appBrightCyan)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Back to Settings")
                Spacer()
            }
            Text("Legal & Privacy")
                .font(.system(size: 22, weight: .bold, design: .rounded))
                .foregroundStyle(Color.appPrimaryText)
        }
        .padding(.top, 6)
    }

    // MARK: - Section dropdown

    private var currentSectionTitle: String {
        if let section = LegalContent.sections.first(where: { $0.id == selection }) {
            return titleCase(section.title)
        }
        return ""
    }

    private var sectionDropdown: some View {
        Menu {
            ForEach(LegalContent.sections) { section in
                Button {
                    selection = section.id
                } label: {
                    HStack {
                        Text(titleCase(section.title))
                        if section.id == selection {
                            Spacer()
                            Image(systemName: "checkmark")
                        }
                    }
                }
            }
        } label: {
            HStack(spacing: 10) {
                Text(currentSectionTitle)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(Color.appPrimaryText)
                    .lineLimit(1)
                Spacer()
                Image(systemName: "chevron.down")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(Color.appBrightCyan)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 13)
            .background(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .fill(Color.appElevatedSurface)
                    .overlay(
                        RoundedRectangle(cornerRadius: 14, style: .continuous)
                            .stroke(Color.appBrightCyan.opacity(0.35), lineWidth: 1)
                    )
            )
            .shadow(color: Color.appBrightCyan.opacity(0.12), radius: 8, x: 0, y: 2)
        }
        .accessibilityLabel("Legal section")
        .accessibilityValue(currentSectionTitle)
        .accessibilityHint("Opens a list of legal sections")
    }

    // MARK: - Section body (non-Honest-Claims)

    private var sectionTitle: some View {
        Group {
            if let section = LegalContent.sections.first(where: { $0.id == selection }) {
                Text(titleCase(section.title))
                    .font(.system(size: 28, weight: .bold, design: .rounded))
                    .foregroundStyle(Color.appPrimaryText)
            }
        }
    }

    private var sectionBody: some View {
        Group {
            if let section = LegalContent.sections.first(where: { $0.id == selection }) {
                FormattedLegalText(raw: section.body)
            }
        }
    }

    // MARK: - Honest Claims special layout

    private var isHonestClaims: Bool {
        selection.caseInsensitiveCompare("Honest claims") == .orderedSame
    }

    private var honestClaimsBody: String {
        LegalContent.sections.first(where: {
            $0.title.caseInsensitiveCompare("Honest claims") == .orderedSame
        })?.body ?? ""
    }

    private var honestClaimsIntro: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Honest Claims")
                .font(.system(size: 30, weight: .bold, design: .rounded))
                .foregroundStyle(Color.appPrimaryText)
            Text("OnlyTrusted is a powerful tool, but there are important limits to what it can do. We believe in being clear and honest about them.")
                .font(.subheadline)
                .foregroundStyle(Color.appSecondaryText)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    private var honestClaimsCard: some View {
        HStack(alignment: .top, spacing: 14) {
            ZStack {
                Circle()
                    .fill(Color.appWarning)
                    .frame(width: 56, height: 56)
                    .shadow(color: Color.appWarning.opacity(0.6), radius: 12, x: 0, y: 0)
                Image(systemName: "exclamationmark.triangle.fill")
                    .font(.system(size: 26, weight: .semibold))
                    .foregroundStyle(.white)
            }
            VStack(alignment: .leading, spacing: 14) {
                Text("What this app cannot do")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundStyle(Color.appWarning)
                VStack(spacing: 0) {
                    ForEach(Array(honestLimitsBullets.enumerated()), id: \.offset) { idx, text in
                        bulletRow(text)
                        if idx < honestLimitsBullets.count - 1 {
                            Divider()
                                .background(Color.appWarning.opacity(0.2))
                                .padding(.leading, 22)
                        }
                    }
                }
            }
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .fill(Color.appWarning.opacity(0.06))
                .overlay(
                    RoundedRectangle(cornerRadius: 18, style: .continuous)
                        .stroke(Color.appWarning.opacity(0.5), lineWidth: 1)
                )
        )
    }

    private var honestLimitsBullets: [String] {
        [
            "Cannot monitor other apps' microphone or camera use.",
            "Cannot see messages in other apps.",
            "Cannot block calls on all device types or carriers.",
            "Cannot provide a cloud-based spam database.",
            "Cannot guarantee 100% blocking of unknown contacts."
        ]
    }

    private func bulletRow(_ text: String) -> some View {
        HStack(alignment: .top, spacing: 12) {
            Circle()
                .fill(Color.appWarning)
                .frame(width: 7, height: 7)
                .padding(.top, 7)
            Text(text)
                .font(.subheadline)
                .foregroundStyle(Color.appPrimaryText)
                .fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 0)
        }
        .padding(.vertical, 10)
    }

    private var readFullDetailsCard: some View {
        Button(action: { showFullDetails = true }) {
            HStack(spacing: 14) {
                ZStack {
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(Color.appBrightCyan.opacity(0.14))
                        .overlay(
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .stroke(Color.appBrightCyan.opacity(0.5), lineWidth: 1)
                        )
                    Image(systemName: "doc.text.fill")
                        .font(.system(size: 20, weight: .semibold))
                        .foregroundStyle(Color.appBrightCyan)
                }
                .frame(width: 46, height: 46)

                VStack(alignment: .leading, spacing: 2) {
                    Text("Read the full details")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundStyle(Color.appPrimaryText)
                    Text("View complete platform limitations and disclosures.")
                        .font(.footnote)
                        .foregroundStyle(Color.appSecondaryText)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 0)
                Image(systemName: "chevron.right")
                    .font(.footnote.weight(.bold))
                    .foregroundStyle(Color.appBrightCyan)
            }
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .fill(Color.appElevatedSurface)
                    .overlay(
                        RoundedRectangle(cornerRadius: 16, style: .continuous)
                            .stroke(Color.appBrightCyan.opacity(0.3), lineWidth: 1)
                    )
            )
        }
        .buttonStyle(.plain)
    }

    private func titleCase(_ s: String) -> String {
        // "Terms of use" → "Terms of Use"; "Honest claims" → "Honest Claims"
        let smallWords: Set<String> = ["of", "and", "the", "for", "to"]
        return s.split(separator: " ").enumerated().map { idx, word in
            let w = String(word)
            if idx > 0 && smallWords.contains(w.lowercased()) { return w.lowercased() }
            return w.prefix(1).uppercased() + w.dropFirst()
        }.joined(separator: " ")
    }
}

// MARK: - Legal body formatter
// Renders paragraphs, bullet lists ("• …"), and numbered lists ("1. …") with hanging
// indentation so wrapped lines align with the content, not the bullet/number.

private struct FormattedLegalText: View {
    let raw: String

    private enum Block {
        case paragraph(String)
        case bullets([String])
        case numbered([(String, String)])  // (number, text)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            ForEach(Array(blocks.enumerated()), id: \.offset) { _, block in
                switch block {
                case .paragraph(let text):
                    Text(text)
                        .font(.appBody)
                        .foregroundStyle(Color.appPrimaryText)
                        .fixedSize(horizontal: false, vertical: true)
                        .textSelection(.enabled)
                case .bullets(let items):
                    VStack(alignment: .leading, spacing: 8) {
                        ForEach(Array(items.enumerated()), id: \.offset) { _, item in
                            bulletRow(marker: "•", text: item)
                        }
                    }
                case .numbered(let items):
                    VStack(alignment: .leading, spacing: 8) {
                        ForEach(Array(items.enumerated()), id: \.offset) { _, pair in
                            bulletRow(marker: "\(pair.0).", text: pair.1, markerWidth: 24)
                        }
                    }
                }
            }
        }
    }

    @ViewBuilder
    private func bulletRow(marker: String, text: String, markerWidth: CGFloat = 16) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 8) {
            Text(marker)
                .font(.appBody.weight(.semibold))
                .foregroundStyle(Color.appBrightCyan)
                .frame(width: markerWidth, alignment: .leading)
            Text(text)
                .font(.appBody)
                .foregroundStyle(Color.appPrimaryText)
                .fixedSize(horizontal: false, vertical: true)
                .textSelection(.enabled)
        }
    }

    private var blocks: [Block] {
        // The LegalContent strings are indented with 4 spaces per line because of the Swift
        // multiline-literal convention. Normalise, then walk line-by-line and emit a new
        // block whenever we switch between paragraph lines / bullet lines / numbered lines
        // (or hit a blank separator). This way a paragraph followed immediately by bullets
        // (no blank line between) still renders as two distinct blocks.
        let lines = raw.components(separatedBy: "\n").map {
            $0.trimmingCharacters(in: CharacterSet(charactersIn: " \t"))
        }

        enum Kind { case paragraph, bullet, numbered }

        func kind(of line: String) -> Kind {
            if line.hasPrefix("• ") { return .bullet }
            if (try? /^\d+\.\s/.prefixMatch(in: line)) != nil { return .numbered }
            return .paragraph
        }

        var result: [Block] = []
        var buffer: [String] = []
        var bufferKind: Kind = .paragraph

        func flush() {
            guard !buffer.isEmpty else { return }
            switch bufferKind {
            case .bullet:
                result.append(.bullets(buffer.map { String($0.dropFirst(2)) }))
            case .numbered:
                let numberedPattern = /^(\d+)\.\s(.+)$/
                let items: [(String, String)] = buffer.compactMap { line in
                    guard let m = try? numberedPattern.wholeMatch(in: line) else { return nil }
                    return (String(m.output.1), String(m.output.2))
                }
                result.append(.numbered(items))
            case .paragraph:
                result.append(.paragraph(buffer.joined(separator: "\n")))
            }
            buffer.removeAll()
        }

        for line in lines {
            if line.isEmpty {
                flush()
                continue
            }
            let k = kind(of: line)
            if !buffer.isEmpty && k != bufferKind {
                flush()
            }
            bufferKind = k
            buffer.append(line)
        }
        flush()
        return result
    }
}
