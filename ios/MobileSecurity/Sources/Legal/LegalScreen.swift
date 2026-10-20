// SPDX-FileCopyrightText: 2026 57Million LLC
// SPDX-License-Identifier: AGPL-3.0-or-later
import SwiftUI

struct LegalScreen: View {
    @State private var selection = LegalContent.sections.first?.id ?? ""

    var body: some View {
        VStack(spacing: 0) {
            Picker("Section", selection: $selection) {
                ForEach(LegalContent.sections) { section in
                    Text(section.title).tag(section.id)
                }
            }
            .pickerStyle(.menu)
            .padding()

            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    if let section = LegalContent.sections.first(where: { $0.id == selection }) {
                        Text(section.title).font(.title2.weight(.semibold))
                        Text(section.body)
                            .font(.body)
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
