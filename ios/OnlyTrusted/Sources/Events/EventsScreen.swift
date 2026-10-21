// SPDX-FileCopyrightText: 2026 57Million LLC
// SPDX-License-Identifier: AGPL-3.0-or-later
import SwiftUI

struct EventsScreen: View {
    @State private var rows: [SecurityEventRecord] = []
    @State private var chainOk: Bool? = nil
    @State private var filter: EventFilter = .all

    private var filteredRows: [SecurityEventRecord] {
        filter.filter(rows)
    }

    private var grouped: [(String, [SecurityEventRecord])] {
        let fmt = DateFormatter()
        fmt.dateStyle = .medium
        fmt.timeStyle = .none
        let dict = Dictionary(grouping: filteredRows) { row -> String in
            fmt.string(from: Date(timeIntervalSince1970: Double(row.timestampMillis) / 1000.0))
        }
        return dict
            .sorted { lhs, rhs in
                (lhs.value.first?.timestampMillis ?? 0) > (rhs.value.first?.timestampMillis ?? 0)
            }
    }

    var body: some View {
        Group {
            if rows.isEmpty {
                PremiumEmptyState(
                    icon: "clock.badge.checkmark.fill",
                    title: "No events yet",
                    message: "Blocked calls, flagged SMS, panic locks, and vault edits will appear here as they happen."
                )
            } else {
                ScrollView(.vertical, showsIndicators: false) {
                    VStack(spacing: 18) {
                        chainIntegrityCard
                        filterChips

                        VStack(spacing: 20) {
                            ForEach(grouped, id: \.0) { day, dayRows in
                                EventDaySection(day: day, rows: dayRows)
                            }
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 12)
                    .padding(.bottom, 32)
                }
            }
        }
        .background(Color.appScreenBackground.ignoresSafeArea())
        .navigationTitle("Events")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    reload()
                } label: {
                    Image(systemName: "arrow.clockwise").decorativeAccessibility()
                }
                .accessibilityLabel("Reload event log")
                .minimumHitTarget()
            }
        }
        .task { reload() }
    }

    @ViewBuilder
    private var chainIntegrityCard: some View {
        PremiumCard {
            HStack(spacing: 14) {
                ZStack {
                    Circle().fill(chainTint.opacity(0.14))
                    Image(systemName: chainIcon)
                        .font(.title3.weight(.semibold))
                        .foregroundStyle(chainTint)
                }
                .frame(width: 44, height: 44)
                .decorativeAccessibility()

                VStack(alignment: .leading, spacing: 2) {
                    Text(chainTitle)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Color.appPrimaryText)
                    Text(chainMessage)
                        .font(.footnote)
                        .foregroundStyle(Color.appSecondaryText)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 0)
            }
        }
    }

    @ViewBuilder
    private var filterChips: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 10) {
                ForEach(EventFilter.allCases, id: \.self) { f in
                    FilterChip(
                        label: f.title,
                        isSelected: filter == f,
                        onTap: { filter = f }
                    )
                }
            }
            .padding(.horizontal, 2)
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
        case .some(true): Color.appSuccess
        case .some(false): Color.appDanger
        case .none: Color.appAccent
        }
    }
    private var chainTitle: String {
        switch chainOk {
        case .some(true): "Audit chain intact"
        case .some(false): "Audit chain MISMATCH"
        case .none: "Checking audit chain"
        }
    }
    private var chainMessage: String {
        switch chainOk {
        case .some(true): "No event has been retroactively modified since you last reset the log."
        case .some(false): "Something tampered with the event log. Export the evidence pack and contact support."
        case .none: "Verifying every row of the audit log. This usually takes under a second."
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

// MARK: - Filter chip

enum EventFilter: CaseIterable, Hashable {
    case all, blocked, allowed, flagged, system

    var title: String {
        switch self {
        case .all: "All"
        case .blocked: "Blocked"
        case .allowed: "Allowed"
        case .flagged: "Spam SMS"
        case .system: "System"
        }
    }

    func filter(_ rows: [SecurityEventRecord]) -> [SecurityEventRecord] {
        switch self {
        case .all: return rows
        case .blocked: return rows.filter { $0.type == SecurityEventRecord.Kind.callBlocked }
        case .allowed: return rows.filter { $0.type == SecurityEventRecord.Kind.callAllowed }
        case .flagged: return rows.filter { $0.type == SecurityEventRecord.Kind.smsFlagged }
        case .system: return rows.filter {
            [SecurityEventRecord.Kind.panicLock,
             SecurityEventRecord.Kind.vaultEdit,
             SecurityEventRecord.Kind.wipe,
             SecurityEventRecord.Kind.callDirectoryReloaded].contains($0.type)
        }
        }
    }
}

private struct FilterChip: View {
    let label: String
    let isSelected: Bool
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            Text(label)
                .font(.footnote.weight(.semibold))
                .padding(.horizontal, 16)
                .padding(.vertical, 9)
                .background(
                    Capsule().fill(isSelected ? Color.appAccent : Color.appCardBackground)
                )
                .foregroundStyle(isSelected ? Color.white : Color.appPrimaryText)
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}

// MARK: - Day section + row

private struct EventDaySection: View {
    let day: String
    let rows: [SecurityEventRecord]

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(day)
                .font(.footnote.weight(.semibold))
                .foregroundStyle(Color.appSecondaryText)
                .textCase(.uppercase)
                .padding(.leading, 8)

            PremiumCard {
                VStack(spacing: 14) {
                    ForEach(Array(rows.enumerated()), id: \.element.id) { idx, row in
                        EventRow(record: row)
                        if idx < rows.count - 1 {
                            Divider().padding(.leading, 46)
                        }
                    }
                }
            }
        }
    }
}

private struct EventRow: View {
    let record: SecurityEventRecord

    var body: some View {
        HStack(alignment: .top, spacing: 14) {
            ZStack {
                RoundedRectangle(cornerRadius: 9, style: .continuous)
                    .fill(typeTint.opacity(0.14))
                Image(systemName: typeIcon)
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(typeTint)
            }
            .frame(width: 32, height: 32)
            .decorativeAccessibility()

            VStack(alignment: .leading, spacing: 3) {
                Text(humanLabel(record.type))
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(Color.appPrimaryText)
                Text(Date(timeIntervalSince1970: Double(record.timestampMillis) / 1000.0),
                     style: .time)
                    .font(.caption)
                    .foregroundStyle(Color.appSecondaryText)
                if let detail = record.detail {
                    Text(detail)
                        .font(.caption.monospaced())
                        .foregroundStyle(Color.appTertiaryText)
                        .lineLimit(2)
                        .padding(.top, 1)
                }
            }
            Spacer(minLength: 0)
        }
    }

    private var typeIcon: String {
        switch record.type {
        case SecurityEventRecord.Kind.callBlocked: "phone.down.fill"
        case SecurityEventRecord.Kind.callAllowed: "phone.fill"
        case SecurityEventRecord.Kind.smsFlagged: "message.badge.fill"
        case SecurityEventRecord.Kind.callDirectoryReloaded: "arrow.triangle.2.circlepath"
        case SecurityEventRecord.Kind.panicLock: "lock.fill"
        case SecurityEventRecord.Kind.vaultEdit: "person.text.rectangle.fill"
        case SecurityEventRecord.Kind.wipe: "trash.fill"
        default: "circle"
        }
    }

    private var typeTint: Color {
        switch record.type {
        case SecurityEventRecord.Kind.callBlocked, SecurityEventRecord.Kind.smsFlagged: Color.appDanger
        case SecurityEventRecord.Kind.callAllowed: Color.appSuccess
        case SecurityEventRecord.Kind.panicLock, SecurityEventRecord.Kind.wipe: Color.appWarning
        default: Color.appAccent
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
