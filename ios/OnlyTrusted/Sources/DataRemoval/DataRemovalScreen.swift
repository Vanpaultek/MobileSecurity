// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import SwiftUI
import UIKit

/// Data Removal Scan (FREE tier, visibility-only). Generates deep-link search URLs for
/// each bundled data-broker / people-search site; opens the user's choice in Safari.
///
/// Three search modes — Name / Phone / Address. The segmented picker up top switches
/// between them; the form fields and the visible site list update to match. Each site
/// in the bundled catalog declares which modes it supports via `search_urls`; sites
/// without a template for the selected mode drop out of the list.
///
/// Hard rule — honest UX: the hero and footer repeat that this feature does NOT remove
/// data. OnlyTrusted cannot act on behalf of the user because every site's removal flow
/// is manual, often paper-based, and legally scoped to the record owner.
struct DataRemovalScreen: View {

    @StateObject private var state = DataRemovalState.shared
    @StateObject private var reminders = DataRemovalReminders.shared
    @StateObject private var sub = SubscriptionState.shared
    @State private var sitesVisible: Bool = false
    @State private var statePickerOpen: Bool = false

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                warningHero
                modePicker
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
                Text("This scan does not remove your data. It only generates search links so you can see where your name, phone, or address may be published. Removal has to be done manually with each site.")
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

    // MARK: - Mode picker

    private var modePicker: some View {
        Picker("Search by", selection: Binding(
            get: { state.searchMode },
            set: { newMode in
                state.searchMode = newMode
                sitesVisible = false   // force a fresh scan when mode changes
            }
        )) {
            ForEach(SearchMode.allCases) { mode in
                Text(mode.label).tag(mode)
            }
        }
        .pickerStyle(.segmented)
    }

    // MARK: - Form (mode-dependent fields)

    private var formCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 12) {
                CardHeader(icon: formIcon,
                           title: formTitle,
                           tint: Color.appAccent)

                switch state.searchMode {
                case .name:    nameFields
                case .phone:   phoneFields
                case .address: addressFields
                }
            }
        }
    }

    private var formIcon: String {
        switch state.searchMode {
        case .name:    return "person.text.rectangle"
        case .phone:   return "phone.circle"
        case .address: return "mappin.and.ellipse"
        }
    }

    private var formTitle: String {
        switch state.searchMode {
        case .name:    return "Your name"
        case .phone:   return "Phone number"
        case .address: return "Address"
        }
    }

    @ViewBuilder private var nameFields: some View {
        field(placeholder: "First name",
              value: Binding(get: { state.firstName }, set: { state.firstName = $0 }),
              contentType: .givenName,
              keyboard: .default,
              capitalization: .words)

        field(placeholder: "Last name",
              value: Binding(get: { state.lastName }, set: { state.lastName = $0 }),
              contentType: .familyName,
              keyboard: .default,
              capitalization: .words)

        statePickerRow(label: "State (optional)")
    }

    @ViewBuilder private var phoneFields: some View {
        field(placeholder: "Phone number (digits only)",
              value: Binding(get: { state.phone }, set: { state.phone = $0 }),
              contentType: .telephoneNumber,
              keyboard: .phonePad,
              capitalization: .none)
        Text("Enter 10 digits (US) or full international format with country code.")
            .font(.caption)
            .foregroundStyle(Color.appSecondaryText)
    }

    @ViewBuilder private var addressFields: some View {
        field(placeholder: "Street (e.g. 123 Main St)",
              value: Binding(get: { state.street }, set: { state.street = $0 }),
              contentType: .streetAddressLine1,
              keyboard: .default,
              capitalization: .words)

        field(placeholder: "City",
              value: Binding(get: { state.city }, set: { state.city = $0 }),
              contentType: .addressCity,
              keyboard: .default,
              capitalization: .words)

        statePickerRow(label: "State")
    }

    private func statePickerRow(label: String) -> some View {
        HStack {
            Text(label)
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

    private func field(placeholder: String,
                       value: Binding<String>,
                       contentType: UITextContentType,
                       keyboard: UIKeyboardType,
                       capitalization: UITextAutocapitalizationType) -> some View {
        TextField(placeholder, text: value)
            .textContentType(contentType)
            .keyboardType(keyboard)
            .autocapitalization(capitalization)
            .disableAutocorrection(true)
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .background(
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .fill(Color.appScreenBackground)
            )
    }

    // MARK: - Scan button + validation

    private var canScan: Bool {
        let f = state.form
        return DataRemovalCatalog.sites(for: state.searchMode)
            .contains { DataRemovalCatalog.renderURL(site: $0, mode: state.searchMode, form: f) != nil }
    }

    private var scanButtonLabel: String {
        if canScan { return "Scan now" }
        switch state.searchMode {
        case .name:    return "Enter first + last name"
        case .phone:   return "Enter a phone number"
        case .address: return "Enter street + city + state"
        }
    }

    private var scanButton: some View {
        Button {
            sitesVisible = canScan
        } label: {
            Text(scanButtonLabel)
                .font(.subheadline.weight(.semibold))
                .frame(maxWidth: .infinity, minHeight: 44)
        }
        .buttonStyle(.borderedProminent)
        .tint(Color.appAccent)
        .disabled(!canScan)
    }

    // MARK: - Sites

    private var modeSites: [DataRemovalSite] {
        DataRemovalCatalog.sites(for: state.searchMode)
    }

    private var sitesCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 12) {
                CardHeader(icon: "list.bullet.rectangle",
                           title: "People-search sites",
                           tint: Color.appAccent)
                Text("Tap Check on any row to open the site's search in Safari. We don't share your details — Safari does.")
                    .font(.caption)
                    .foregroundStyle(Color.appSecondaryText)

                let f = state.form
                let sites = modeSites
                if sites.isEmpty {
                    Text("No sites in the catalog support \(state.searchMode.label.lowercased()) search.")
                        .font(.footnote)
                        .foregroundStyle(Color.appSecondaryText)
                }
                ForEach(sites) { site in
                    siteRow(site, form: f)
                }
            }
        }
    }

    private func siteRow(_ site: DataRemovalSite, form: DataRemovalForm) -> some View {
        let url = DataRemovalCatalog.renderURL(site: site, mode: state.searchMode, form: form)
        let isRequested = reminders.isRequested(site: site.name)
        return VStack(alignment: .leading, spacing: 6) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(site.name)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Color.appPrimaryText)
                    Text(url?.host ?? site.template(for: state.searchMode) ?? "")
                        .font(.caption)
                        .foregroundStyle(Color.appSecondaryText)
                        .lineLimit(1)
                }
                Spacer()
                Button {
                    if let url { UIApplication.shared.open(url) }
                } label: {
                    Text("Check")
                        .font(.footnote.weight(.semibold))
                        .padding(.horizontal, 14)
                        .padding(.vertical, 8)
                        .background(
                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                .fill(url == nil ? Color.appElevatedSurface : Color.appAccent)
                        )
                        .foregroundStyle(url == nil ? Color.appSecondaryText : .white)
                }
                .buttonStyle(.plain)
                .disabled(url == nil)
            }

            // Premium: per-site "Mark as removal requested" + status badge.
            if sub.hasPremium {
                HStack(spacing: 8) {
                    if isRequested, let when = reminders.requestedAt(site: site.name) {
                        Image(systemName: "clock.badge.checkmark")
                            .foregroundStyle(Color.appSuccess)
                        Text("Requested \(dayLabel(when)). Re-check reminder in \(daysUntilCheck(when)) days.")
                            .font(.caption2)
                            .foregroundStyle(Color.appSecondaryText)
                        Spacer()
                        Button("Clear") { reminders.unmark(site: site.name) }
                            .font(.caption2.weight(.semibold))
                            .foregroundStyle(Color.appSecondaryText)
                    } else {
                        Button {
                            BreachWatchScheduler.requestPermissionIfNeeded { _ in }
                            reminders.markRequested(site: site.name)
                        } label: {
                            Label("Mark as removal requested", systemImage: "flag.badge.ellipsis")
                                .font(.caption2.weight(.semibold))
                                .foregroundStyle(Color.appAccent)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
        .padding(.vertical, 6)
    }

    private func dayLabel(_ date: Date) -> String {
        let f = DateFormatter()
        f.dateStyle = .medium
        return f.string(from: date)
    }

    private func daysUntilCheck(_ requestedAt: Date) -> Int {
        let elapsed = Date().timeIntervalSince(requestedAt) / 86400
        let remaining = DataRemovalReminders.reminderDays - elapsed
        return max(0, Int(remaining.rounded()))
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
