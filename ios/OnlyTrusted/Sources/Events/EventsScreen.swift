// SPDX-FileCopyrightText: 2026 57Million LLC
// SPDX-License-Identifier: AGPL-3.0-or-later
import SwiftUI

struct EventsScreen: View {
    @State private var rows: [SecurityEventRecord] = []
    @State private var chainOk: Bool? = nil

    var body: some View {
        List {
            Section {
                statusRow
            }

            if rows.isEmpty {
                Section {
                    ContentUnavailableView(
                        "No events yet",
                        systemImage: "clock.arrow.circlepath",
                        description: Text("Blocked calls, flagged SMS, panic locks, and vault edits appear here as they happen.")
                    )
                    .frame(minHeight: 200)
                }
            } else {
                Section("Recent") {
                    ForEach(rows) { row in
                        EventRow(record: row)
                    }
                }
            }
        }
        .navigationTitle("Events")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    reload()
                } label: {
                    Image(systemName: "arrow.clockwise")
                }
            }
        }
        .task { reload() }
    }

    @ViewBuilder
    private var statusRow: some View {
        HStack {
            Image(systemName: chainIcon)
                .foregroundStyle(chainTint)
            Text(chainMessage)
                .font(.footnote)
        }
    }

    private var chainIcon: String {
        switch chainOk {
        case .some(true): "checkmark.seal.fill"
        case .some(false): "exclamationmark.octagon.fill"
        case .none: "ellipsis.circle"
        }
    }

    private var chainTint: Color {
        switch chainOk {
        case .some(true): .green
        case .some(false): .red
        case .none: .secondary
        }
    }

    private var chainMessage: String {
        switch chainOk {
        case .some(true): "Audit chain intact. No event has been retroactively modified."
        case .some(false): "Audit chain MISMATCH. Something tampered with the event log."
        case .none: "Checking audit chain…"
        }
    }

    private func reload() {
        rows = EventStore.shared.allRecent()
        Task.detached {
            let ok = EventStore.shared.verifyChain()
            await MainActor.run { chainOk = ok }
        }
    }
}

private struct EventRow: View {
    let record: SecurityEventRecord

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(humanLabel(record.type)).font(.headline)
            Text(Date(timeIntervalSince1970: Double(record.timestampMillis) / 1000.0),
                 style: .relative)
                .font(.caption).foregroundStyle(.secondary)
            if let detail = record.detail {
                Text(detail).font(.caption).monospaced().foregroundStyle(.secondary)
            }
        }
    }

    private func humanLabel(_ type: String) -> String {
        switch type {
        case SecurityEventRecord.Kind.callBlocked: "Call blocked"
        case SecurityEventRecord.Kind.callAllowed: "Call allowed"
        case SecurityEventRecord.Kind.smsFlagged: "Spam SMS flagged"
        case SecurityEventRecord.Kind.callDirectoryReloaded: "Call directory refreshed"
        case SecurityEventRecord.Kind.panicLock: "Panic lock engaged"
        case SecurityEventRecord.Kind.vaultEdit: "Vault edited"
        case SecurityEventRecord.Kind.wipe: "Data wiped"
        default: type
        }
    }
}
