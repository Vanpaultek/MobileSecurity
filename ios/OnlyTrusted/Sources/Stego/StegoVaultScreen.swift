// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import SwiftUI
import UniformTypeIdentifiers
import UIKit

/// Steganographic Vault (Elite). Lets a lawyer / journalist / activist hide their vault
/// inside a plausible decoy (a PNG family photo or a PDF receipt). The feature stays
/// opt-in — the regular `.ot2bak` backup still works, this is an additional carrier format.
///
/// Caller gates on `SubscriptionState.shared.hasElite`.
struct StegoVaultScreen: View {
    @Environment(\.dismiss) private var dismiss

    enum Mode: String, CaseIterable, Identifiable {
        case create, load
        var id: String { rawValue }
        var title: String { self == .create ? "Create stego vault" : "Load stego vault" }
    }

    @State private var mode: Mode = .create

    // --- create flow state
    @State private var format: StegoCrypto.CarrierFormat = .png
    @State private var showCarrierPicker = false
    @State private var carrierBytes: Data?
    @State private var carrierSource: String?
    @State private var createPassphrase = ""
    @State private var createPassphraseAgain = ""
    @State private var createError: String?
    @State private var shareURL: URL?
    @State private var showBundledSheet = false

    // --- load flow state
    @State private var showLoadPicker = false
    @State private var loadURL: URL?
    @State private var loadPassphrase = ""
    @State private var loadError: String?
    @State private var decodedPayload: BackupPayload?
    @State private var confirmApply = false
    @State private var restored = false

    var body: some View {
        ScrollView {
            VStack(spacing: 18) {
                headerCard
                modePickerCard
                switch mode {
                case .create: createSection
                case .load:   loadSection
                }
            }
            .padding(20)
        }
        .appBackdrop()
        .navigationTitle("Steganographic Vault")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button(restored ? "Done" : "Cancel") { dismiss() }
                    .tint(Color.appBrightCyan)
            }
        }
        .fileImporter(
            isPresented: $showCarrierPicker,
            allowedContentTypes: format == .png ? [.png] : [.pdf],
            allowsMultipleSelection: false
        ) { result in
            switch result {
            case .success(let urls):
                guard let url = urls.first else { return }
                let gotAccess = url.startAccessingSecurityScopedResource()
                defer { if gotAccess { url.stopAccessingSecurityScopedResource() } }
                if let data = try? Data(contentsOf: url) {
                    carrierBytes = data
                    carrierSource = url.lastPathComponent
                } else {
                    createError = "Could not read carrier file."
                }
            case .failure(let e):
                createError = e.localizedDescription
            }
        }
        .fileImporter(
            isPresented: $showLoadPicker,
            allowedContentTypes: [.png, .pdf],
            allowsMultipleSelection: false
        ) { result in
            switch result {
            case .success(let urls):
                loadURL = urls.first
                decodedPayload = nil
                loadError = nil
            case .failure(let e):
                loadError = e.localizedDescription
            }
        }
        .sheet(item: Binding(get: { shareURL.map(BackupIdentifiableURL.init) },
                             set: { shareURL = $0?.value })) { wrap in
            ShareSheet(activityItems: [wrap.value])
        }
        .sheet(isPresented: $showBundledSheet) {
            NavigationStack {
                BundledCarrierPickerSheet(
                    onPick: { name, data in
                        carrierBytes = data
                        carrierSource = "Bundled · \(name)"
                        showBundledSheet = false
                    },
                    onCancel: { showBundledSheet = false }
                )
            }
        }
        .confirmationDialog("Overwrite everything?",
                            isPresented: $confirmApply,
                            titleVisibility: .visible) {
            Button("Overwrite and restore", role: .destructive) { applyRestore() }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("All current trusted contacts, blocked numbers, events, and settings will be replaced by the contents of this stego vault.")
        }
    }

    // MARK: - Header

    private var headerCard: some View {
        PremiumCard {
            HStack(alignment: .top, spacing: 14) {
                ZStack {
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .fill(
                            LinearGradient(
                                colors: [Color.subGold, Color.subGold.opacity(0.75)],
                                startPoint: .topLeading, endPoint: .bottomTrailing
                            )
                        )
                        .shadow(color: Color.subGold.opacity(0.6), radius: 10)
                    Image(systemName: "photo.badge.plus")
                        .font(.system(size: 22, weight: .semibold))
                        .foregroundStyle(.white)
                }
                .frame(width: 54, height: 54)
                VStack(alignment: .leading, spacing: 6) {
                    Text("Elite feature")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(Color.subGold)
                    Text("Hide the vault in a decoy file.")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Color.appPrimaryText)
                    Text("Choose a PNG family photo or a PDF receipt. OnlyTrusted encrypts your vault and tucks the bytes inside the carrier. The file still opens as a normal image or document — only your passphrase reveals what's inside.")
                        .font(.footnote)
                        .foregroundStyle(Color.appSecondaryText)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
    }

    private var modePickerCard: some View {
        PremiumCard {
            Picker("Mode", selection: $mode) {
                Text("Create").tag(Mode.create)
                Text("Load").tag(Mode.load)
            }
            .pickerStyle(.segmented)
            .padding(.horizontal, 4)
        }
    }

    // MARK: - Create

    private var createSection: some View {
        VStack(spacing: 18) {
            PremiumCard {
                VStack(alignment: .leading, spacing: 12) {
                    CardHeader(icon: "square.and.pencil", title: "Carrier format")
                    Picker("Format", selection: $format) {
                        ForEach(StegoCrypto.CarrierFormat.allCases) { f in
                            Text(f.displayName).tag(f)
                        }
                    }
                    .pickerStyle(.segmented)
                    .onChange(of: format) { _, _ in
                        carrierBytes = nil
                        carrierSource = nil
                    }
                }
            }

            PremiumCard {
                VStack(alignment: .leading, spacing: 12) {
                    CardHeader(icon: "doc.on.clipboard", title: "Carrier file")
                    if let name = carrierSource {
                        Text(name)
                            .font(.footnote.weight(.semibold))
                            .foregroundStyle(Color.appPrimaryText)
                    } else {
                        Text("Pick an existing file or use one of the bundled royalty-free sample images.")
                            .font(.footnote)
                            .foregroundStyle(Color.appSecondaryText)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    HStack(spacing: 10) {
                        Button {
                            showCarrierPicker = true
                        } label: {
                            HStack(spacing: 6) {
                                Image(systemName: "folder")
                                Text("Pick file")
                                    .font(.footnote.weight(.semibold))
                            }
                            .frame(maxWidth: .infinity, minHeight: 36)
                        }
                        .buttonStyle(.bordered)
                        .tint(Color.appBrightCyan)
                        if format == .png {
                            Button {
                                showBundledSheet = true
                            } label: {
                                HStack(spacing: 6) {
                                    Image(systemName: "photo.on.rectangle")
                                    Text("Use bundled image")
                                        .font(.footnote.weight(.semibold))
                                }
                                .frame(maxWidth: .infinity, minHeight: 36)
                            }
                            .buttonStyle(.bordered)
                            .tint(Color.appAccent)
                        }
                    }
                }
            }

            PremiumCard {
                VStack(alignment: .leading, spacing: 10) {
                    CardHeader(icon: "key.fill", title: "Passphrase")
                    Text("At least 12 characters with a letter, a digit, and a symbol. We cannot recover it — if you forget it, the hidden vault is gone forever.")
                        .font(.footnote)
                        .foregroundStyle(Color.appSecondaryText)
                        .fixedSize(horizontal: false, vertical: true)
                    SecureField("Passphrase", text: $createPassphrase)
                        .textContentType(.newPassword)
                        .padding(12)
                        .background(RoundedRectangle(cornerRadius: 10).fill(Color.appElevatedSurface))
                    SecureField("Confirm passphrase", text: $createPassphraseAgain)
                        .textContentType(.newPassword)
                        .padding(12)
                        .background(RoundedRectangle(cornerRadius: 10).fill(Color.appElevatedSurface))
                    if let createError {
                        Text(createError)
                            .font(.footnote)
                            .foregroundStyle(Color.appDanger)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
            }

            Button {
                createStego()
            } label: {
                HStack(spacing: 10) {
                    Image(systemName: "square.and.arrow.down")
                    Text("Create stego file")
                        .font(.body.weight(.semibold))
                }
                .frame(maxWidth: .infinity, minHeight: 48)
            }
            .buttonStyle(.borderedProminent)
            .tint(Color.subGold)
            .disabled(carrierBytes == nil)
        }
    }

    private func createStego() {
        createError = nil
        guard let carrier = carrierBytes else {
            createError = "Pick a carrier file first."
            return
        }
        if createPassphrase != createPassphraseAgain {
            createError = "Passphrases don't match."
            return
        }
        do {
            let result = try StegoVaultManager.createStego(
                format: format, carrierBytes: carrier, passphrase: createPassphrase
            )
            let outURL = FileManager.default.temporaryDirectory
                .appendingPathComponent(result.suggestedFilename)
            try result.bytes.write(to: outURL, options: [.atomic])
            shareURL = outURL
            // Scrub plaintext from state.
            createPassphrase = ""
            createPassphraseAgain = ""
        } catch let e as StegoVaultManager.StegoVaultError {
            createError = e.errorDescription
        } catch {
            createError = String(describing: error)
        }
    }

    // MARK: - Load

    private var loadSection: some View {
        VStack(spacing: 18) {
            if loadURL == nil {
                Button {
                    showLoadPicker = true
                } label: {
                    HStack(spacing: 10) {
                        Image(systemName: "doc.text.magnifyingglass")
                        Text("Pick stego file (.png or .pdf)")
                            .font(.body.weight(.semibold))
                    }
                    .frame(maxWidth: .infinity, minHeight: 48)
                }
                .buttonStyle(.borderedProminent)
                .tint(Color.subGold)
            } else {
                PremiumCard {
                    VStack(alignment: .leading, spacing: 8) {
                        CardHeader(icon: "doc.badge.gearshape", title: "File")
                        Text(loadURL?.lastPathComponent ?? "")
                            .font(.footnote.weight(.semibold))
                            .foregroundStyle(Color.appPrimaryText)
                        Button {
                            loadURL = nil
                            decodedPayload = nil
                            loadPassphrase = ""
                            loadError = nil
                        } label: {
                            Text("Pick a different file")
                                .font(.footnote.weight(.semibold))
                                .foregroundStyle(Color.appBrightCyan)
                        }
                    }
                }
                PremiumCard {
                    VStack(alignment: .leading, spacing: 10) {
                        CardHeader(icon: "key.fill", title: "Passphrase")
                        Text("The passphrase you used when you created this stego vault.")
                            .font(.footnote)
                            .foregroundStyle(Color.appSecondaryText)
                            .fixedSize(horizontal: false, vertical: true)
                        SecureField("Passphrase", text: $loadPassphrase)
                            .textContentType(.password)
                            .padding(12)
                            .background(RoundedRectangle(cornerRadius: 10).fill(Color.appElevatedSurface))
                        if let loadError {
                            Text(loadError)
                                .font(.footnote)
                                .foregroundStyle(Color.appDanger)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                }
                Button {
                    attemptDecode()
                } label: {
                    HStack(spacing: 10) {
                        Image(systemName: "lock.open.fill")
                        Text("Decrypt and preview")
                            .font(.body.weight(.semibold))
                    }
                    .frame(maxWidth: .infinity, minHeight: 48)
                }
                .buttonStyle(.borderedProminent)
                .tint(Color.appAccent)
            }

            if let decoded = decodedPayload {
                PremiumCard {
                    VStack(alignment: .leading, spacing: 10) {
                        CardHeader(icon: "checkmark.seal.fill", title: "Stego vault contents")
                        previewRow("Platform", decoded.platform)
                        previewRow("Contacts", "\(decoded.contacts.count)")
                        previewRow("Blocked", "\(decoded.blockedNumbers.count)")
                        previewRow("Events", "\(decoded.events.count)")
                    }
                }
                Button {
                    confirmApply = true
                } label: {
                    HStack(spacing: 10) {
                        Image(systemName: "arrow.triangle.2.circlepath")
                        Text("Restore and overwrite existing data")
                            .font(.body.weight(.semibold))
                    }
                    .frame(maxWidth: .infinity, minHeight: 48)
                }
                .buttonStyle(.borderedProminent)
                .tint(Color.appDanger)
            }
        }
    }

    private func attemptDecode() {
        loadError = nil
        guard let url = loadURL else {
            loadError = "Pick a stego file first."
            return
        }
        guard !loadPassphrase.isEmpty else {
            loadError = "Enter the passphrase you used when you created this vault."
            return
        }
        do {
            decodedPayload = try StegoVaultManager.openStego(from: url, passphrase: loadPassphrase)
        } catch let e as StegoVaultManager.StegoVaultError {
            loadError = e.errorDescription
            decodedPayload = nil
        } catch {
            loadError = String(describing: error)
            decodedPayload = nil
        }
    }

    private func applyRestore() {
        guard let decoded = decodedPayload else { return }
        do {
            try BackupManager.applyRestore(decoded)
            self.restored = true
            self.decodedPayload = nil
            self.loadPassphrase = ""
            self.loadURL = nil
        } catch {
            self.loadError = String(describing: error)
        }
    }

    private func previewRow(_ label: String, _ value: String) -> some View {
        HStack {
            Text(label).font(.footnote).foregroundStyle(Color.appSecondaryText)
            Spacer()
            Text(value).font(.subheadline.weight(.semibold)).foregroundStyle(Color.appPrimaryText)
        }
    }
}

// MARK: - Bundled carriers

/// Three royalty-free PNGs are shipped in the app bundle for users who don't want to source
/// their own carrier. The images are placeholder gradients generated at build time (see
/// Assets.xcassets/StegoBundled*.imageset) so the shipped IPA stays small — swap them for
/// hand-picked Pexels / Unsplash images before shipping to the App Store if you want more
/// realistic cover stories.
private struct BundledCarrierPickerSheet: View {
    let onPick: (String, Data) -> Void
    let onCancel: () -> Void

    private let bundled: [(String, String)] = [
        ("Nature", "StegoBundledNature"),
        ("Family", "StegoBundledFamily"),
        ("Beach",  "StegoBundledBeach")
    ]

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                PremiumCard {
                    VStack(alignment: .leading, spacing: 10) {
                        CardHeader(icon: "photo.on.rectangle", title: "Bundled carrier images")
                        Text("These royalty-free gradients ship with OnlyTrusted. Pick one if you don't have a personal photo ready. All three are large enough to hold a full vault snapshot.")
                            .font(.footnote)
                            .foregroundStyle(Color.appSecondaryText)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
                ForEach(bundled, id: \.0) { name, asset in
                    Button {
                        if let data = bundledImageData(assetName: asset) {
                            onPick(name, data)
                        }
                    } label: {
                        PremiumCard {
                            HStack(spacing: 14) {
                                if let ui = UIImage(named: asset) {
                                    Image(uiImage: ui)
                                        .resizable()
                                        .aspectRatio(contentMode: .fill)
                                        .frame(width: 72, height: 72)
                                        .clipShape(RoundedRectangle(cornerRadius: 10))
                                } else {
                                    RoundedRectangle(cornerRadius: 10)
                                        .fill(Color.appElevatedSurface)
                                        .frame(width: 72, height: 72)
                                }
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(name)
                                        .font(.subheadline.weight(.semibold))
                                        .foregroundStyle(Color.appPrimaryText)
                                    Text("Royalty-free · 1024×768 PNG")
                                        .font(.caption)
                                        .foregroundStyle(Color.appSecondaryText)
                                }
                                Spacer()
                                Image(systemName: "chevron.right")
                                    .foregroundStyle(Color.appTertiaryText)
                            }
                        }
                    }
                    .buttonStyle(.plain)
                }
                Spacer()
            }
            .padding(20)
        }
        .appBackdrop()
        .navigationTitle("Bundled carriers")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button("Cancel", action: onCancel).tint(Color.appBrightCyan)
            }
        }
    }

    /// Returns the bundled image re-encoded as PNG so we hand `StegoCrypto.embedIntoPNG` a
    /// clean baseline. UIImage(named:) can hold compressed JPEG internally on some devices.
    private func bundledImageData(assetName: String) -> Data? {
        guard let image = UIImage(named: assetName) else { return nil }
        return image.pngData()
    }
}

// MARK: - Upsell

/// Elite upsell sheet shown when a user without Elite taps the Steganographic Vault row.
struct StegoVaultUpsellSheet: View {
    let onUpgrade: () -> Void
    let onDismiss: () -> Void

    var body: some View {
        VStack(spacing: 20) {
            Image(systemName: "photo.badge.plus")
                .font(.system(size: 46, weight: .semibold))
                .foregroundStyle(Color.subGold)
                .padding(.top, 28)
            Text("Steganographic Vault")
                .font(.title2.weight(.bold))
                .foregroundStyle(Color.appPrimaryText)
            Text("Hide your entire OnlyTrusted vault inside a PNG photo or a PDF document. The decoy opens as a normal file in Photos or Preview — only your passphrase reveals what's inside. Built for lawyers, journalists, and activists who may be forced to hand over an unlocked phone. Elite tier.")
                .font(.subheadline)
                .foregroundStyle(Color.appSecondaryText)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)
            Spacer(minLength: 0)
            Button(action: onUpgrade) {
                Text("Upgrade to Elite")
                    .font(.body.weight(.semibold))
                    .frame(maxWidth: .infinity, minHeight: 48)
            }
            .buttonStyle(.borderedProminent)
            .tint(Color.subGold)
            .padding(.horizontal, 24)
            Button("Not now", action: onDismiss)
                .foregroundStyle(Color.appSecondaryText)
                .padding(.bottom, 20)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .appBackdrop()
    }
}
