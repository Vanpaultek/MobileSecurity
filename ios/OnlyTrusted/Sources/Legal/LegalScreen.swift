// SPDX-FileCopyrightText: 2026 57Million LLC
// SPDX-License-Identifier: AGPL-3.0-or-later
import SwiftUI

struct LegalScreen: View {
    /// Deep-link entry point. Pass a section title (e.g. "Privacy policy") to open directly
    /// on that tab. Nil defaults to the first section.
    let initialSectionTitle: String?

    @State private var selection: String

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
            Picker("Section", selection: $selection) {
                ForEach(LegalContent.sections) { section in
                    Text(section.title).tag(section.id)
                }
            }
            .pickerStyle(.menu)
            .tint(Color.appAccent)
            .padding()

            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    if let section = LegalContent.sections.first(where: { $0.id == selection }) {
                        Text(section.title)
                            .font(.appSectionTitle)
                        Text(section.body)
                            .font(.appBody)
                            .foregroundStyle(Color.appPrimaryText)
                            .textSelection(.enabled)
                    }
                }
                .padding(16)
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
        .navigationTitle("Legal")
        .navigationBarTitleDisplayMode(.inline)
    }
}
