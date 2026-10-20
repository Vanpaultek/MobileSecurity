// SPDX-FileCopyrightText: 2026 57Million LLC
// SPDX-License-Identifier: AGPL-3.0-or-later
import SwiftUI
import CallKit

@MainActor
final class BlockListViewModel: ObservableObject {
    @Published private(set) var entries: [BlockedNumberRecord] = []
    @Published var reloadStatus: ReloadStatus = .idle

    enum ReloadStatus: Equatable {
        case idle
        case reloading
        case ok
        case failed(String)
    }

    private let store = BlockListStore.shared
    private let callDirectoryIdentifier: String

    init(callDirectoryIdentifier: String = "com.onlytrusted.app.CallDirectoryExtension") {
        self.callDirectoryIdentifier = callDirectoryIdentifier
    }

    func load() {
        entries = store.allRecent()
    }

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

    /// Tell iOS to re-query the Call Directory Extension. The extension is only consulted
    /// at reload time, not per-call — so every change to the block list must be followed
    /// by this call for the change to take effect in the dialer.
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
        List {
            Section {
                if vm.entries.isEmpty {
                    Text("No numbers blocked yet. Add one with the + button.")
                        .foregroundStyle(Color.appSecondaryText)
                } else {
                    ForEach(vm.entries) { record in
                        BlockedRow(record: record)
                    }
                    .onDelete { set in
                        for i in set { vm.remove(id: vm.entries[i].id) }
                    }
                }
            } header: {
                Text("Your block list")
            } footer: {
                Text("Blocks apply after you tap Reload below and after you have enabled OnlyTrusted inside the iOS Settings app, under Phone, Call Blocking and Identification.")
                    .font(.footnote)
                    .foregroundStyle(Color.appSecondaryText)
            }

            Section {
                Button {
                    vm.reloadCallDirectory()
                } label: {
                    Label("Reload Call Directory", systemImage: "arrow.triangle.2.circlepath")
                }
                statusView
            } header: {
                Text("Push changes to iOS")
            }

            Section {
                Button {
                    openURL("App-prefs:Phone")
                } label: {
                    Label("Enable blocking in Phone settings", systemImage: "phone.badge.waveform")
                }
                Button {
                    openURL("App-prefs:MESSAGES")
                } label: {
                    Label("Enable SMS filter in Messages settings", systemImage: "message.badge")
                }
            } header: {
                Text("iOS activation (one-time)")
            } footer: {
                Text("iOS requires each user to turn on third-party call blocking and SMS filtering inside the Settings app. We cannot flip these switches for you. The links above open the correct page.")
                    .font(.footnote)
                    .foregroundStyle(Color.appSecondaryText)
            }
        }
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

    @ViewBuilder
    private var statusView: some View {
        switch vm.reloadStatus {
        case .idle:
            EmptyView()
        case .reloading:
            HStack {
                ProgressView().controlSize(.small)
                Text("Reloading…").font(.footnote).foregroundStyle(Color.appSecondaryText)
            }
        case .ok:
            Label("Changes pushed to iOS.", systemImage: "checkmark.circle.fill")
                .foregroundStyle(.green).font(.footnote)
        case .failed(let message):
            Label("Reload failed: \(message)", systemImage: "exclamationmark.triangle.fill")
                .foregroundStyle(.red).font(.footnote)
        }
    }

    private func openURL(_ raw: String) {
        guard let url = URL(string: raw) else { return }
        UIApplication.shared.open(url)
    }
}

private struct BlockedRow: View {
    let record: BlockedNumberRecord

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(PhoneUtils.mask(record.normalisedNumber))
                .font(.headline).monospaced()
            if let label = record.label {
                Text(label).font(.subheadline).foregroundStyle(Color.appSecondaryText)
            }
            HStack(spacing: 6) {
                if record.blockCalls {
                    Label("Calls", systemImage: "phone.down.fill").font(.caption)
                }
                if record.blockSms {
                    Label("SMS", systemImage: "message.badge.fill").font(.caption)
                }
            }
            .foregroundStyle(Color.appSecondaryText)
        }
    }
}

private struct AddBlockedSheet: View {
    @ObservedObject var vm: BlockListViewModel
    @Environment(\.dismiss) private var dismiss

    @State private var number = ""
    @State private var label = ""
    @State private var blockCalls = true
    @State private var blockSms = true

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Number", text: Binding(
                        get: { number },
                        set: { number = PhoneUtils.sanitiseNumberInput($0) }
                    ))
                    .keyboardType(.phonePad)
                    TextField("Label (optional)", text: $label)
                }
                Section {
                    Toggle("Block incoming calls", isOn: $blockCalls)
                        .tint(Color.appAccent)
                    Toggle("Flag incoming SMS", isOn: $blockSms)
                        .tint(Color.appAccent)
                } footer: {
                    Text("Call blocking takes effect after you tap Reload and after you enable OnlyTrusted inside the iOS Settings app, under Phone.")
                        .font(.footnote)
                        .foregroundStyle(Color.appSecondaryText)
                }
            }
            .navigationTitle("Block a number")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Add") {
                        vm.add(raw: number, label: label.isEmpty ? nil : label,
                               blockCalls: blockCalls, blockSms: blockSms)
                        dismiss()
                    }
                    .disabled(number.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
        }
    }
}
