// SPDX-FileCopyrightText: 2026 57Million LLC
// SPDX-License-Identifier: AGPL-3.0-or-later
import SwiftUI
import CallKit

@MainActor
final class BlockListViewModel: ObservableObject {
    @Published private(set) var entries: [BlockedNumberRecord] = []
    @Published var reloadStatus: ReloadStatus = .idle

    enum ReloadStatus: Equatable {
        case idle, reloading, ok
        case failed(String)
    }

    private let store = BlockListStore.shared
    private let callDirectoryIdentifier: String

    init(callDirectoryIdentifier: String = "com.onlytrusted.app.CallDirectoryExtension") {
        self.callDirectoryIdentifier = callDirectoryIdentifier
    }

    func load() { entries = store.allRecent() }

    func add(raw: String, label: String?, blockCalls: Bool, blockSms: Bool) {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        let record = BlockedNumberRecord(raw: trimmed, label: label,
                                         blockCalls: blockCalls, blockSms: blockSms)
        _ = try? store.upsert(record)
        load()
    }

    func remove(id: UUID) {
        try? store.remove(id: id)
        load()
    }

    func reloadCallDirectory() {
        reloadStatus = .reloading
        CXCallDirectoryManager.sharedInstance.reloadExtension(withIdentifier: callDirectoryIdentifier) { [weak self] error in
            Task { @MainActor in
                guard let self else { return }
                if let error {
                    self.reloadStatus = .failed(error.localizedDescription)
                    EventStore.shared.append(type: SecurityEventRecord.Kind.callDirectoryReloaded,
                                             detail: "failed: \(error.localizedDescription)")
                } else {
                    self.reloadStatus = .ok
                    EventStore.shared.append(type: SecurityEventRecord.Kind.callDirectoryReloaded,
                                             detail: "entries=\(self.store.allSorted().count)")
                }
            }
        }
    }
}

struct BlockListScreen: View {
    @StateObject private var vm = BlockListViewModel()
    @State private var showAdd = false

    var body: some View {
        Group {
            if vm.entries.isEmpty {
                PremiumEmptyState(
                    icon: "nosign",
                    title: "No numbers blocked yet",
                    message: "Add a number here and OnlyTrusted will silence it on every incoming call and SMS.",
                    ctaTitle: "Block your first number",
                    ctaAction: { showAdd = true }
                )
            } else {
                ScrollView(.vertical, showsIndicators: false) {
                    VStack(spacing: 18) {
                        heroCard
                        blockedListCard
                        activationCard
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 12)
                    .padding(.bottom, 32)
                }
            }
        }
        .background(Color(.systemGroupedBackground).ignoresSafeArea())
        .navigationTitle("Blocked")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button { showAdd = true } label: {
                    Image(systemName: "plus.circle.fill").decorativeAccessibility()
                }
                .accessibilityLabel("Block a number")
                .minimumHitTarget()
            }
        }
        .sheet(isPresented: $showAdd) { AddBlockedSheet(vm: vm) }
        .task { vm.load() }
    }

    private var heroCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 18) {
                HStack(alignment: .top) {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("\(vm.entries.count)")
                            .font(.system(size: 44, weight: .bold, design: .rounded))
                            .foregroundStyle(Color.appPrimaryText)
                        Text(vm.entries.count == 1 ? "number blocked" : "numbers blocked")
                            .font(.subheadline)
                            .foregroundStyle(Color.appSecondaryText)
                    }
                    Spacer()
                    ZStack {
                        Circle().fill(Color.appAccent.opacity(0.14))
                        Image(systemName: "nosign")
                            .font(.title.weight(.semibold))
                            .foregroundStyle(Color.appAccent)
                    }
                    .frame(width: 72, height: 72)
                    .decorativeAccessibility()
                }

                Button {
                    vm.reloadCallDirectory()
                } label: {
                    Label("Push changes to iOS", systemImage: "arrow.triangle.2.circlepath")
                        .font(.body.weight(.semibold))
                        .frame(maxWidth: .infinity, minHeight: 32)
                }
                .buttonStyle(.borderedProminent)
                .tint(Color.appAccent)
                .controlSize(.large)

                statusLine
            }
        }
    }

    @ViewBuilder
    private var statusLine: some View {
        switch vm.reloadStatus {
        case .idle: EmptyView()
        case .reloading:
            HStack(spacing: 8) {
                ProgressView().controlSize(.small)
                Text("Reloading…")
                    .font(.footnote)
                    .foregroundStyle(Color.appSecondaryText)
            }
        case .ok:
            Label("Changes pushed to iOS.", systemImage: "checkmark.circle.fill")
                .font(.footnote)
                .foregroundStyle(Color.appSuccess)
        case .failed(let m):
            Label("Reload failed: \(m)", systemImage: "exclamationmark.triangle.fill")
                .font(.footnote)
                .foregroundStyle(Color.appDanger)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    private var blockedListCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 14) {
                CardHeader(icon: "list.bullet", title: "Your block list")
                VStack(spacing: 12) {
                    ForEach(Array(vm.entries.enumerated()), id: \.element.id) { idx, record in
                        BlockedRow(record: record, onDelete: { vm.remove(id: record.id) })
                        if idx < vm.entries.count - 1 {
                            Divider().padding(.leading, 44)
                        }
                    }
                }
            }
        }
    }

    private var activationCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 14) {
                CardHeader(icon: "gearshape.fill", title: "One-time iOS activation", tint: Color.appWarning)
                Text("iOS requires each user to turn on third-party call blocking and SMS filtering in the Settings app. We cannot flip these switches for you. The links below open the right page.")
                    .font(.subheadline)
                    .foregroundStyle(Color.appSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)
                VStack(spacing: 10) {
                    SystemLinkRow(icon: "phone.badge.waveform", label: "Enable blocking in Phone settings") {
                        openURL("App-prefs:Phone")
                    }
                    SystemLinkRow(icon: "message.badge", label: "Enable SMS filter in Messages settings") {
                        openURL("App-prefs:MESSAGES")
                    }
                }
            }
        }
    }

    private func openURL(_ raw: String) {
        guard let url = URL(string: raw) else { return }
        UIApplication.shared.open(url)
    }
}

// MARK: - Row

private struct BlockedRow: View {
    let record: BlockedNumberRecord
    let onDelete: () -> Void

    var body: some View {
        HStack(alignment: .top, spacing: 14) {
            ZStack {
                RoundedRectangle(cornerRadius: 9, style: .continuous)
                    .fill(Color.appDanger.opacity(0.14))
                Image(systemName: "nosign")
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(Color.appDanger)
            }
            .frame(width: 32, height: 32)
            .decorativeAccessibility()

            VStack(alignment: .leading, spacing: 2) {
                Text(PhoneUtils.mask(record.normalisedNumber))
                    .font(.subheadline.weight(.semibold).monospaced())
                    .foregroundStyle(Color.appPrimaryText)
                if let label = record.label {
                    Text(label)
                        .font(.caption)
                        .foregroundStyle(Color.appSecondaryText)
                }
                HStack(spacing: 6) {
                    if record.blockCalls { TagChip(label: "Calls", icon: "phone.down.fill") }
                    if record.blockSms { TagChip(label: "SMS", icon: "message.fill") }
                }
                .padding(.top, 2)
            }
            Spacer()
            Button(role: .destructive, action: onDelete) {
                Image(systemName: "trash")
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(Color.appDanger)
                    .frame(width: 36, height: 36)
                    .background(Circle().fill(Color.appDanger.opacity(0.12)))
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Remove \(record.normalisedNumber) from block list")
            .minimumHitTarget()
        }
    }
}

private struct TagChip: View {
    let label: String
    let icon: String
    var body: some View {
        HStack(spacing: 4) {
            Image(systemName: icon)
                .font(.caption2)
                .decorativeAccessibility()
            Text(label).font(.caption2.weight(.semibold))
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 3)
        .background(Capsule().fill(Color.appAccent.opacity(0.12)))
        .foregroundStyle(Color.appAccent)
    }
}

// MARK: - Add sheet

private struct AddBlockedSheet: View {
    @ObservedObject var vm: BlockListViewModel
    @Environment(\.dismiss) private var dismiss

    @State private var number = ""
    @State private var label = ""
    @State private var blockCalls = true
    @State private var blockSms = true

    var body: some View {
        NavigationStack {
            ScrollView(.vertical, showsIndicators: false) {
                VStack(spacing: 18) {
                    PremiumCard {
                        VStack(alignment: .leading, spacing: 14) {
                            CardHeader(icon: "number", title: "Number")
                            TextField("Phone number", text: Binding(
                                get: { number },
                                set: { number = PhoneUtils.sanitiseNumberInput($0) }
                            ))
                            .keyboardType(.phonePad)
                            .textFieldStyle(.plain)
                            .padding(.vertical, 8)
                            Divider()
                            TextField("Label (optional)", text: $label)
                                .textFieldStyle(.plain)
                                .padding(.vertical, 8)
                        }
                    }

                    PremiumCard {
                        VStack(alignment: .leading, spacing: 14) {
                            CardHeader(icon: "slider.horizontal.3", title: "What to block")
                            Toggle("Block incoming calls", isOn: $blockCalls)
                                .tint(Color.appAccent)
                            Toggle("Flag incoming SMS", isOn: $blockSms)
                                .tint(Color.appAccent)
                            Text("Call blocking takes effect after you tap Reload and after you enable OnlyTrusted inside the iOS Settings app, under Phone.")
                                .font(.footnote)
                                .foregroundStyle(Color.appSecondaryText)
                                .padding(.top, 4)
                        }
                    }
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 16)
            }
            .background(Color(.systemGroupedBackground).ignoresSafeArea())
            .navigationTitle("Block a number")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }.tint(Color.appAccent)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Add") {
                        vm.add(raw: number, label: label.isEmpty ? nil : label,
                               blockCalls: blockCalls, blockSms: blockSms)
                        dismiss()
                    }
                    .tint(Color.appAccent)
                    .disabled(number.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
        }
    }
}
