// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import SwiftUI
import UIKit

/// Data Removal Scan (FREE tier, visibility-only). Generates deep-link search URLs for
/// each bundled data-broker / people-search site; opens the user's choice in Safari.
///
/// Hard rule — honest UX: every surface tells the user this feature does NOT remove the
/// data, only shows where exposure might exist. OnlyTrusted cannot act on behalf of the
/// user because the deletion flow for every site is manual, often paper-based, and
/// legally scoped to the record owner.
struct DataRemovalScreen: View {

    @StateObject private var state = DataRemovalState.shared
    @State private var sitesVisible: Bool = false
    @State private var statePickerOpen: Bool = false

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                warningHero
                formCard
                scanButton
                if sitesVisible { sitesCard }
                paidToolsCard
            }
            .padding(20)
        }
        .appBackdrop()
        .navigationTitle("Data Removal Scan")
        .navigationBarTitleDisplayMode(.inline)
    }

    // MARK: - Hero

    private var warningHero: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 8) {
                HStack(spacing: 10) {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .foregroundStyle(.white)
                        .padding(8)
                        .background(
                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                .fill(Color.appWarning)
                        )
                    Text("Visibility only")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Color.appWarning)
                }
                Text("This scan does not remove your data. It only generates search links so you can see where your name may be published. Removal has to be done manually with each site.")
                    .font(.footnote)
                    .foregroundStyle(Color.appSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .background(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .stroke(Color.appWarning.opacity(0.6), lineWidth: 1)
        )
    }

    // MARK: - Form

    private var formCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 12) {
                CardHeader(icon: "person.text.rectangle",
                           title: "Your details",
                           tint: Color.appAccent)

                field(placeholder: "First name", value: Binding(
                    get: { state.firstName },
                    set: { state.firstName = $0 }
                ), contentType: .givenName)

                field(placeholder: "Last name", value: Binding(
                    get: { state.lastName },
                    set: { state.lastName = $0 }
                ), contentType: .familyName)

                HStack {
                    Text("State (optional)")
                        .font(.subheadline)
                        .foregroundStyle(Color.appSecondaryText)
                    Spacer()
                    Button {
                        statePickerOpen = true
                    } label: {
                        HStack(spacing: 6) {
                            Text(state.state.isEmpty ? "Any" : state.state)
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(Color.appAccent)
                            Image(systemName: "chevron.down")
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(Color.appAccent)
                        }
                    }
                    .confirmationDialog("Select state", isPresented: $statePickerOpen,
                                        titleVisibility: .visible) {
                        Button("Any") { state.state = "" }
                        ForEach(USStates.all, id: \.self) { s in
                            Button(s) { state.state = s }
                        }
                        Button("Cancel", role: .cancel) {}
                    }
                }
            }
        }
    }

    private func field(placeholder: String, value: Binding<String>,
                       contentType: UITextContentType) -> some View {
        TextField(placeholder, text: value)
            .textContentType(contentType)
            .autocapitalization(.words)
            .disableAutocorrection(true)
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .background(
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .fill(Color.appScreenBackground)
            )
    }

    private var canScan: Bool {
        !state.firstName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
        !state.lastName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    private var scanButton: some View {
        Button {
            sitesVisible = canScan
        } label: {
            Text(canScan ? "Scan now" : "Enter first + last name")
                .font(.subheadline.weight(.semibold))
                .frame(maxWidth: .infinity, minHeight: 44)
        }
        .buttonStyle(.borderedProminent)
        .tint(Color.appAccent)
        .disabled(!canScan)
    }

    // MARK: - Sites

    private var sitesCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 12) {
                CardHeader(icon: "list.bullet.rectangle",
                           title: "People-search sites",
                           tint: Color.appAccent)
                Text("Tap Check on any row to open the site's search. We don't share your name — the browser does.")
                    .font(.caption)
                    .foregroundStyle(Color.appSecondaryText)
                ForEach(DataRemovalCatalog.sites) { site in
                    siteRow(site)
                }
            }
        }
    }

    private func siteRow(_ site: DataRemovalSite) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(site.name)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(Color.appPrimaryText)
                Text(hostLabel(site.search_url))
                    .font(.caption)
                    .foregroundStyle(Color.appSecondaryText)
                    .lineLimit(1)
            }
            Spacer()
            Button {
                if let url = DataRemovalCatalog.renderSearchURL(
                    site.search_url,
                    firstName: state.firstName,
                    lastName: state.lastName
                ) {
                    UIApplication.shared.open(url)
                }
            } label: {
                Text("Check")
                    .font(.footnote.weight(.semibold))
                    .padding(.horizontal, 14)
                    .padding(.vertical, 8)
                    .background(
                        RoundedRectangle(cornerRadius: 10, style: .continuous)
                            .fill(Color.appAccent)
                    )
                    .foregroundStyle(.white)
            }
            .buttonStyle(.plain)
        }
        .padding(.vertical, 6)
    }

    private func hostLabel(_ raw: String) -> String {
        URL(string: raw)?.host ?? raw
    }

    private var paidToolsCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 8) {
                HStack(spacing: 10) {
                    Image(systemName: "info.circle.fill")
                        .foregroundStyle(.white)
                        .padding(8)
                        .background(
                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                .fill(Color.appAccent)
                        )
                    Text("To actually remove")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Color.appPrimaryText)
                }
                Text("Use a paid data-removal service like OneRep.com, Optery.com, or DeleteMe.com. OnlyTrusted cannot remove information on your behalf.")
                    .font(.footnote)
                    .foregroundStyle(Color.appSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }
}
