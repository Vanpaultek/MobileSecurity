// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import SwiftUI
import CoreLocation

/// Geo-fenced Auto-Lock + Alert (Elite) setup.
///
/// Reached from Settings → Security & data → "Geo-fence Auto-Lock (Elite)". The parent row
/// is gated on `SubscriptionState.hasElite`; this screen assumes the user is on Elite.
///
/// Shows:
///  - Elite header card with the "Always location" caveat.
///  - List of trusted locations (name, radius slider, delete). Up to 5.
///  - "Add current location" button (uses CLLocationManager one-shot fetch, prompts for a
///    name after the fix lands).
///  - Auto-lock toggle (default OFF). Flipping on requests Always authorization.
///  - SMS alert toggle, alert-contact picker, share-location toggle.
struct GeofenceSetupScreen: View {
    @Environment(\.dismiss) private var dismiss
    @StateObject private var state = GeofenceState.shared
    @StateObject private var manager = GeofenceManager.shared
    @StateObject private var vault = VaultStore.shared

    @State private var showAddLocationSheet = false
    @State private var pendingLocation: CLLocation?
    @State private var pendingLocationName: String = ""
    @State private var pendingRadius: Double = 150
    @State private var fetchInFlight: Bool = false
    @State private var fetchErrorMessage: String?

    private var realContacts: [MaskedContact] {
        vault.realContactsForSetup()
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 18) {
                headerCard
                authorizationCard
                locationsCard
                autoLockCard
                if state.isEnabled {
                    smsCard
                }
                limitationsNote
            }
            .padding(20)
        }
        .appBackdrop()
        .navigationTitle("Geo-fence Auto-Lock")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button("Done") { dismiss() }.tint(Color.appBrightCyan)
            }
        }
        .onAppear {
            manager.requestAlwaysAuthorization()
            if state.ownerName.isEmpty, let owner = realContacts.first(where: { $0.isOwner }) {
                state.ownerName = owner.displayName
            }
        }
        .sheet(isPresented: $showAddLocationSheet) {
            NavigationStack {
                namePromptSheet
            }
            .presentationDetents([.medium])
        }
        .alert("Couldn't get current location", isPresented: Binding(
            get: { fetchErrorMessage != nil },
            set: { if !$0 { fetchErrorMessage = nil } }
        )) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(fetchErrorMessage ?? "")
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
                                colors: [Color.appAccent, Color.appAccent.opacity(0.7)],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        )
                        .shadow(color: Color.appAccent.opacity(0.6), radius: 10)
                    Image(systemName: "location.circle.fill")
                        .font(.system(size: 24, weight: .semibold))
                        .foregroundStyle(.white)
                }
                .frame(width: 54, height: 54)
                VStack(alignment: .leading, spacing: 6) {
                    Text("Elite feature")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(Color.subGold)
                    Text("Auto-lock when the device leaves every trusted area.")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Color.appPrimaryText)
                    Text("Define up to 5 trusted locations. If the device exits all of them, OnlyTrusted locks itself and (optionally) sends a silent SMS to a chosen contact.")
                        .font(.footnote)
                        .foregroundStyle(Color.appSecondaryText)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
    }

    // MARK: - Authorization

    @ViewBuilder
    private var authorizationCard: some View {
        if !manager.hasAlwaysAuthorization {
            PremiumCard {
                VStack(alignment: .leading, spacing: 10) {
                    HStack(spacing: 8) {
                        Image(systemName: "exclamationmark.triangle.fill")
                            .foregroundStyle(Color.appWarning)
                        Text("Needs \"Always\" location")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(Color.appPrimaryText)
                    }
                    Text(authorizationExplanation)
                        .font(.footnote)
                        .foregroundStyle(Color.appSecondaryText)
                        .fixedSize(horizontal: false, vertical: true)
                    Button {
                        if manager.authorizationStatus == .denied || manager.authorizationStatus == .restricted {
                            if let url = URL(string: UIApplication.openSettingsURLString) {
                                UIApplication.shared.open(url)
                            }
                        } else {
                            manager.requestAlwaysAuthorization()
                        }
                    } label: {
                        Text(authorizationCTA)
                            .font(.footnote.weight(.semibold))
                            .frame(maxWidth: .infinity, minHeight: 36)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(Color.appAccent)
                }
            }
        }
    }

    private var authorizationExplanation: String {
        switch manager.authorizationStatus {
        case .notDetermined:
            return "Tap below to grant OnlyTrusted \"Always\" access to your location. iOS first asks for \"While using the app\"; the Always upgrade prompt appears on the next geofence check."
        case .authorizedWhenInUse:
            return "You picked \"While using the app\". Background region monitoring won't fire — tap below so iOS prompts for the Always upgrade."
        case .denied, .restricted:
            return "Location access is turned off. Open iOS Settings → OnlyTrusted → Location and pick \"Always\" to enable background geofence monitoring."
        default:
            return "Pick \"Always\" so OnlyTrusted can detect you leaving a trusted area even when the app is closed."
        }
    }

    private var authorizationCTA: String {
        switch manager.authorizationStatus {
        case .denied, .restricted: return "Open iOS Settings"
        case .authorizedWhenInUse: return "Upgrade to Always"
        default: return "Allow location access"
        }
    }

    // MARK: - Locations

    private var locationsCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 12) {
                CardHeader(icon: "mappin.circle.fill", title: "Trusted locations")
                Text("Up to 5 locations. OnlyTrusted auto-locks when the device exits all of them.")
                    .font(.footnote)
                    .foregroundStyle(Color.appSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)

                if state.geofences.isEmpty {
                    Text("No trusted locations yet.")
                        .font(.footnote)
                        .foregroundStyle(Color.appTertiaryText)
                        .padding(.vertical, 6)
                } else {
                    ForEach(state.geofences) { fence in
                        geofenceRow(fence)
                    }
                }

                Button {
                    startAddLocationFlow()
                } label: {
                    HStack(spacing: 8) {
                        if fetchInFlight {
                            ProgressView().tint(.white)
                        }
                        Image(systemName: "plus.circle.fill")
                        Text(fetchInFlight ? "Getting GPS fix..." : "Add current location")
                    }
                    .font(.body.weight(.semibold))
                    .frame(maxWidth: .infinity, minHeight: 44)
                }
                .buttonStyle(.borderedProminent)
                .tint(Color.appAccent)
                .disabled(state.geofences.count >= GeofenceState.maxGeofences || fetchInFlight)

                if state.geofences.count >= GeofenceState.maxGeofences {
                    Text("You've hit the 5-location limit. Delete one to add another.")
                        .font(.caption)
                        .foregroundStyle(Color.appWarning)
                }
            }
        }
    }

    private func geofenceRow(_ fence: TrustedGeofence) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(fence.name)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Color.appPrimaryText)
                    Text(String(format: "%.4f, %.4f · radius %.0fm",
                                fence.latitude, fence.longitude, fence.radius))
                        .font(.caption)
                        .foregroundStyle(Color.appSecondaryText)
                }
                Spacer()
                Button(role: .destructive) {
                    state.removeGeofence(id: fence.id)
                } label: {
                    Image(systemName: "trash")
                        .foregroundStyle(Color.appDanger)
                }
                .buttonStyle(.plain)
            }
            HStack(spacing: 10) {
                Text("100m").font(.caption).foregroundStyle(Color.appTertiaryText)
                Slider(
                    value: Binding(
                        get: { fence.radius },
                        set: { state.updateRadius(id: fence.id, radius: $0) }
                    ),
                    in: GeofenceState.radiusRange,
                    step: 50
                )
                .tint(Color.appAccent)
                Text("1km").font(.caption).foregroundStyle(Color.appTertiaryText)
            }
        }
        .padding(10)
        .background(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(Color.appElevatedSurface)
        )
    }

    private func startAddLocationFlow() {
        fetchInFlight = true
        fetchErrorMessage = nil
        manager.fetchCurrentLocation { location in
            fetchInFlight = false
            guard let location else {
                fetchErrorMessage = manager.lastRequestLocationError
                    ?? "Could not get a GPS fix. Check that location services are on."
                return
            }
            pendingLocation = location
            pendingLocationName = defaultName(for: state.geofences.count)
            pendingRadius = 150
            showAddLocationSheet = true
        }
    }

    private func defaultName(for existingCount: Int) -> String {
        switch existingCount {
        case 0: return "Home"
        case 1: return "Office"
        default: return "Location \(existingCount + 1)"
        }
    }

    private var namePromptSheet: some View {
        VStack(spacing: 18) {
            Text("Name this location")
                .font(.title3.weight(.bold))
                .foregroundStyle(Color.appPrimaryText)
                .padding(.top, 20)
            if let loc = pendingLocation {
                Text(String(format: "%.5f, %.5f", loc.coordinate.latitude, loc.coordinate.longitude))
                    .font(.caption)
                    .foregroundStyle(Color.appSecondaryText)
            }
            TextField("e.g. Home", text: $pendingLocationName)
                .textFieldStyle(.roundedBorder)
                .padding(.horizontal, 24)
            VStack(alignment: .leading, spacing: 6) {
                Text("Radius: \(Int(pendingRadius))m")
                    .font(.footnote)
                    .foregroundStyle(Color.appSecondaryText)
                Slider(value: $pendingRadius, in: GeofenceState.radiusRange, step: 50)
                    .tint(Color.appAccent)
            }
            .padding(.horizontal, 24)

            HStack {
                Button("Cancel") {
                    showAddLocationSheet = false
                }
                .buttonStyle(.bordered)
                Spacer()
                Button("Save") {
                    if let loc = pendingLocation {
                        _ = state.addGeofence(
                            name: pendingLocationName,
                            coordinate: loc.coordinate,
                            radius: pendingRadius
                        )
                    }
                    showAddLocationSheet = false
                }
                .buttonStyle(.borderedProminent)
                .tint(Color.appAccent)
                .disabled(pendingLocationName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }
            .padding(.horizontal, 24)
            Spacer()
        }
        .appBackdrop()
        .navigationBarTitleDisplayMode(.inline)
    }

    // MARK: - Auto-lock toggle

    private var autoLockCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 12) {
                Toggle(isOn: Binding(
                    get: { state.isEnabled },
                    set: { newValue in
                        if newValue && state.geofences.isEmpty {
                            // Don't let the user enable monitoring with zero fences — the
                            // feature would be a lie.
                            fetchErrorMessage = "Add at least one trusted location first."
                            return
                        }
                        state.isEnabled = newValue
                    }
                )) {
                    HStack(spacing: 12) {
                        Image(systemName: state.isEnabled ? "lock.shield.fill" : "lock.shield")
                            .foregroundStyle(state.isEnabled ? Color.appSuccess : Color.subGold)
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Auto-lock enabled")
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(Color.appPrimaryText)
                            Text(state.isEnabled
                                 ? "Monitoring. The app will lock and alert on exit."
                                 : "Off. Trusted locations are stored but nothing fires.")
                                .font(.caption)
                                .foregroundStyle(Color.appSecondaryText)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                }
                .tint(Color.appAccent)
            }
        }
    }

    // MARK: - SMS card

    private var smsCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 14) {
                CardHeader(icon: "message.badge.filled.fill", title: "Alert SMS")

                Toggle(isOn: $state.smsAlertEnabled) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Silent SMS alert")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(Color.appPrimaryText)
                        Text("Pre-fills Messages with the alert on exit (iOS limitation).")
                            .font(.caption)
                            .foregroundStyle(Color.appSecondaryText)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
                .tint(Color.appAccent)

                if state.smsAlertEnabled {
                    Divider().background(Color.appDivider.opacity(0.3))
                    Text("Alert contact")
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle(Color.appSecondaryText)
                    if realContacts.isEmpty {
                        Text("Add a trusted contact first — nobody to alert.")
                            .font(.footnote)
                            .foregroundStyle(Color.appWarning)
                    } else {
                        ForEach(realContacts) { contact in
                            Button {
                                state.alertContactId = contact.id
                            } label: {
                                HStack(spacing: 12) {
                                    Image(systemName: state.alertContactId == contact.id ? "largecircle.fill.circle" : "circle")
                                        .foregroundStyle(state.alertContactId == contact.id ? Color.appAccent : Color.appTertiaryText)
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(contact.displayName)
                                            .font(.subheadline.weight(.semibold))
                                            .foregroundStyle(Color.appPrimaryText)
                                        if let first = contact.maskedNumbers.first {
                                            Text(first)
                                                .font(.caption)
                                                .foregroundStyle(Color.appSecondaryText)
                                        }
                                    }
                                    Spacer()
                                }
                                .padding(.vertical, 4)
                            }
                            .buttonStyle(.plain)
                        }
                    }

                    Divider().background(Color.appDivider.opacity(0.3))

                    Toggle(isOn: $state.shareLocationInAlert) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Share location in alert")
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(Color.appPrimaryText)
                            Text("Appends a Google Maps link with the GPS fix. Off by default for privacy.")
                                .font(.caption)
                                .foregroundStyle(Color.appSecondaryText)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                    .tint(Color.appAccent)

                    TextField("Your name (used in the alert)",
                              text: $state.ownerName)
                        .textFieldStyle(.roundedBorder)
                }
            }
        }
    }

    // MARK: - Footer caveat

    private var limitationsNote: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 6) {
                HStack(spacing: 8) {
                    Image(systemName: "info.circle.fill").foregroundStyle(Color.appWarning)
                    Text("iOS limitation")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Color.appPrimaryText)
                }
                Text("iOS has no API for truly silent SMS. On exit, OnlyTrusted presents the Messages composer pre-filled with the alert; you (or whoever has the device) still has to tap Send. If you need silent send, run OnlyTrusted on Android.")
                    .font(.caption)
                    .foregroundStyle(Color.appSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }
}

/// Elite upsell sheet shown when a non-Elite user taps the Geo-fence Auto-Lock row.
struct GeofenceUpsellSheet: View {
    let onUpgrade: () -> Void
    let onDismiss: () -> Void

    var body: some View {
        VStack(spacing: 20) {
            Image(systemName: "location.circle.fill")
                .font(.system(size: 46, weight: .semibold))
                .foregroundStyle(Color.subGold)
                .padding(.top, 28)
            Text("Geo-fence Auto-Lock")
                .font(.title2.weight(.bold))
                .foregroundStyle(Color.appPrimaryText)
            Text("Define up to five trusted locations (home, office, parent's place). OnlyTrusted auto-locks and alerts a contact the moment the device leaves all of them. 100% on-device. Elite tier.")
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
