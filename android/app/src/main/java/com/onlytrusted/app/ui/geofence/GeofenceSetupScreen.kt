// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.geofence

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.location.LocationServices
import com.onlytrusted.app.OnlyTrustedApp
import com.onlytrusted.app.data.repository.MaskedContact
import com.onlytrusted.app.geofence.GeofenceManager
import com.onlytrusted.app.geofence.GeofenceState
import com.onlytrusted.app.geofence.TrustedGeofence
import com.onlytrusted.app.ui.subscription.subGold
import com.onlytrusted.app.ui.theme.BrandAccentDark
import com.onlytrusted.app.ui.theme.BrandCyanDark
import com.onlytrusted.app.ui.theme.DangerDark
import com.onlytrusted.app.ui.theme.MidnightBackground
import com.onlytrusted.app.ui.theme.MidnightCard
import com.onlytrusted.app.ui.theme.MidnightElevated
import com.onlytrusted.app.ui.theme.PrimaryTextDark
import com.onlytrusted.app.ui.theme.SecondaryTextDark
import com.onlytrusted.app.ui.theme.WarningDark
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Geo-fenced Auto-Lock + Alert (Elite) setup screen. Caller gates on `SubscriptionState.hasElite`.
 *
 * UI structure mirrors iOS `GeofenceSetupScreen.swift`:
 *   - Elite header + Always-location caveat.
 *   - Trusted locations card (up to 5, radius slider 100m-1km).
 *   - Auto-lock toggle.
 *   - SMS alert block (contact picker + share-location toggle).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeofenceSetupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as OnlyTrustedApp

    val contactsFlow = remember { app.trustedContacts.maskedList() }
    val contacts by contactsFlow.collectAsState(initial = emptyList())

    var geofences by remember { mutableStateOf(GeofenceState.geofences(context)) }
    var isEnabled by remember { mutableStateOf(GeofenceState.isEnabled(context)) }
    var smsEnabled by remember { mutableStateOf(GeofenceState.smsAlertEnabled(context)) }
    var shareLocation by remember { mutableStateOf(GeofenceState.shareLocationInAlert(context)) }
    var alertContactId by remember { mutableStateOf(GeofenceState.alertContactId(context)) }
    var ownerName by remember { mutableStateOf(GeofenceState.ownerName(context)) }

    var hasFineLocation by remember { mutableStateOf(GeofenceManager.hasForegroundLocationPermission(context)) }
    var hasBackgroundLocation by remember { mutableStateOf(GeofenceManager.hasBackgroundLocationPermission(context)) }

    var showNameDialog by remember { mutableStateOf(false) }
    var pendingLat by remember { mutableStateOf(0.0) }
    var pendingLon by remember { mutableStateOf(0.0) }
    var pendingName by remember { mutableStateOf("") }
    var pendingRadius by remember { mutableStateOf(150f) }
    var fetchInFlight by remember { mutableStateOf(false) }
    var fetchError by remember { mutableStateOf<String?>(null) }

    // Request runtime permissions in two steps (platform constraint). Fine location first,
    // then — once granted — background location so Android 10+ shows the second prompt.
    val finePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasFineLocation = granted
    }
    val backgroundPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasBackgroundLocation = granted
    }

    // Also request SEND_SMS up-front so the exit-time silent send works. Reuses the
    // permission from Duress Mode if already granted.
    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* ignored — the broadcast receiver re-checks before each send */ }

    LaunchedEffect(contacts) {
        if (ownerName.isBlank()) {
            contacts.firstOrNull { it.isOwner }?.let {
                ownerName = it.displayName
                GeofenceState.setOwnerName(context, it.displayName)
            }
        }
    }

    Scaffold(
        containerColor = MidnightBackground,
        topBar = {
            TopAppBar(
                title = { Text("Geo-fence Auto-Lock", color = PrimaryTextDark) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back", color = BrandCyanDark) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MidnightBackground)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HeaderCard()

            if (!hasFineLocation || !hasBackgroundLocation) {
                PermissionCard(
                    hasFine = hasFineLocation,
                    hasBackground = hasBackgroundLocation,
                    onRequestFine = {
                        finePermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    },
                    onRequestBackground = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        }
                    },
                    onOpenAppSettings = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        runCatching { context.startActivity(intent) }
                    }
                )
            }

            // Locations
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Trusted locations", color = PrimaryTextDark, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text(
                        "Up to ${GeofenceState.MAX_GEOFENCES} locations. OnlyTrusted auto-locks when the device exits all of them.",
                        color = SecondaryTextDark,
                        fontSize = 12.sp
                    )

                    if (geofences.isEmpty()) {
                        Text("No trusted locations yet.", color = SecondaryTextDark, fontSize = 12.sp)
                    } else {
                        geofences.forEach { fence ->
                            GeofenceRow(
                                fence = fence,
                                onRadiusChange = { newRadius ->
                                    GeofenceState.updateRadius(context, fence.id, newRadius)
                                    geofences = GeofenceState.geofences(context)
                                    GeofenceManager.resyncMonitoring(context)
                                },
                                onDelete = {
                                    GeofenceState.removeGeofence(context, fence.id)
                                    geofences = GeofenceState.geofences(context)
                                    isEnabled = GeofenceState.isEnabled(context)
                                    GeofenceManager.resyncMonitoring(context)
                                }
                            )
                        }
                    }

                    Button(
                        onClick = {
                            if (!hasFineLocation) {
                                finePermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                return@Button
                            }
                            fetchInFlight = true
                            fetchError = null
                            fetchCurrentLocation(context) { loc ->
                                fetchInFlight = false
                                if (loc == null) {
                                    fetchError =
                                        "Could not get a GPS fix. Check that location services are on."
                                } else {
                                    pendingLat = loc.first
                                    pendingLon = loc.second
                                    pendingName = defaultName(geofences.size)
                                    pendingRadius = 150f
                                    showNameDialog = true
                                }
                            }
                        },
                        enabled = geofences.size < GeofenceState.MAX_GEOFENCES && !fetchInFlight,
                        colors = ButtonDefaults.buttonColors(containerColor = BrandAccentDark),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (fetchInFlight) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Getting GPS fix…", color = Color.White)
                        } else {
                            Icon(Icons.Filled.Add, null, tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("Add current location", color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (geofences.size >= GeofenceState.MAX_GEOFENCES) {
                        Text(
                            "You've hit the ${GeofenceState.MAX_GEOFENCES}-location limit. Delete one to add another.",
                            color = WarningDark,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Auto-lock toggle
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Auto-lock enabled", color = PrimaryTextDark, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (isEnabled) "Monitoring. The app will lock and alert on exit."
                                else "Off. Trusted locations are stored but nothing fires.",
                                color = SecondaryTextDark,
                                fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { want ->
                                if (want && geofences.isEmpty()) {
                                    fetchError = "Add at least one trusted location first."
                                    return@Switch
                                }
                                if (want && !hasFineLocation) {
                                    finePermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                    return@Switch
                                }
                                GeofenceState.setEnabled(context, want)
                                isEnabled = want
                                if (want) {
                                    GeofenceManager.startMonitoring(context)
                                    // Nudge the user to grant background location now so
                                    // Play Services actually delivers exit events while
                                    // the app is backgrounded.
                                    if (!hasBackgroundLocation && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                        backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                                    }
                                    // Nudge for SMS so the exit alert can actually fire.
                                    smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
                                } else {
                                    GeofenceManager.stopMonitoring(context)
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = BrandAccentDark
                            )
                        )
                    }
                }
            }

            // SMS card
            if (isEnabled) {
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Alert SMS", color = PrimaryTextDark, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Silent SMS alert", color = PrimaryTextDark, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Sent via SmsManager when the device exits every trusted location.",
                                    color = SecondaryTextDark, fontSize = 12.sp
                                )
                            }
                            Switch(
                                checked = smsEnabled,
                                onCheckedChange = {
                                    smsEnabled = it
                                    GeofenceState.setSmsAlertEnabled(context, it)
                                    if (it) smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = BrandAccentDark
                                )
                            )
                        }

                        if (smsEnabled) {
                            Text("Alert contact", color = SecondaryTextDark, fontSize = 12.sp)
                            if (contacts.isEmpty()) {
                                Text(
                                    "Add a trusted contact first — nobody to alert.",
                                    color = WarningDark, fontSize = 12.sp
                                )
                            } else {
                                contacts.forEach { c ->
                                    AlertContactRow(c, alertContactId == c.id) {
                                        alertContactId = c.id
                                        GeofenceState.setAlertContactId(context, c.id)
                                    }
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Share location in alert", color = PrimaryTextDark, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "Appends a Google Maps link. Off by default for privacy.",
                                        color = SecondaryTextDark, fontSize = 12.sp
                                    )
                                }
                                Switch(
                                    checked = shareLocation,
                                    onCheckedChange = {
                                        shareLocation = it
                                        GeofenceState.setShareLocationInAlert(context, it)
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = BrandAccentDark
                                    )
                                )
                            }

                            OutlinedTextField(
                                value = ownerName,
                                onValueChange = {
                                    ownerName = it
                                    GeofenceState.setOwnerName(context, it)
                                },
                                singleLine = true,
                                label = { Text("Your name (used in the alert)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
        }

        if (showNameDialog) {
            AlertDialog(
                onDismissRequest = { showNameDialog = false },
                containerColor = MidnightCard,
                title = { Text("Name this location", color = PrimaryTextDark, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "GPS: %.5f, %.5f".format(pendingLat, pendingLon),
                            color = SecondaryTextDark, fontSize = 12.sp
                        )
                        OutlinedTextField(
                            value = pendingName,
                            onValueChange = { pendingName = it },
                            singleLine = true,
                            label = { Text("e.g. Home") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("Radius: ${pendingRadius.toInt()}m", color = PrimaryTextDark, fontSize = 12.sp)
                        Slider(
                            value = pendingRadius,
                            onValueChange = { pendingRadius = it },
                            valueRange = GeofenceState.MIN_RADIUS_METERS..GeofenceState.MAX_RADIUS_METERS,
                            steps = 17 // 50m granularity across 100-1000
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        showNameDialog = false
                        GeofenceState.addGeofence(
                            context, pendingName, pendingLat, pendingLon, pendingRadius
                        )
                        geofences = GeofenceState.geofences(context)
                        GeofenceManager.resyncMonitoring(context)
                    }, enabled = pendingName.isNotBlank()) {
                        Text("Save", color = BrandAccentDark, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showNameDialog = false }) { Text("Cancel") }
                }
            )
        }

        if (fetchError != null) {
            AlertDialog(
                onDismissRequest = { fetchError = null },
                containerColor = MidnightCard,
                title = { Text("Heads up", color = PrimaryTextDark, fontWeight = FontWeight.Bold) },
                text = { Text(fetchError.orEmpty(), color = SecondaryTextDark) },
                confirmButton = {
                    TextButton(onClick = { fetchError = null }) {
                        Text("OK", color = BrandAccentDark)
                    }
                }
            )
        }
    }
}

/// Elite upsell dialog surfaced by Settings when the user taps the row without Elite.
@Composable
fun GeofenceUpsellDialog(onDismiss: () -> Unit, onUpgrade: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MidnightCard,
        title = {
            Text(
                "Geo-fence Auto-Lock is an Elite feature",
                color = PrimaryTextDark,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                "Define up to 5 trusted locations (home, office, parent's place). OnlyTrusted " +
                    "auto-locks and alerts a contact the moment the device leaves all of them. " +
                    "100% on-device.",
                color = SecondaryTextDark
            )
        },
        confirmButton = {
            TextButton(onClick = onUpgrade) {
                Text("Upgrade to Elite", color = subGold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Not now") }
        }
    )
}

@Composable
private fun HeaderCard() {
    Card {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = BrandAccentDark,
                modifier = Modifier.padding(end = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.padding(10.dp)
                )
            }
            Column {
                Text("Elite feature", fontSize = 11.sp, color = subGold, fontWeight = FontWeight.SemiBold)
                Text(
                    "Auto-lock when the device leaves every trusted area.",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PrimaryTextDark
                )
                Text(
                    "Define up to 5 trusted locations. If the device exits all of them, OnlyTrusted " +
                        "locks itself and (optionally) sends a silent SMS to a chosen contact.",
                    fontSize = 12.sp,
                    color = SecondaryTextDark
                )
            }
        }
    }
}

@Composable
private fun PermissionCard(
    hasFine: Boolean,
    hasBackground: Boolean,
    onRequestFine: () -> Unit,
    onRequestBackground: () -> Unit,
    onOpenAppSettings: () -> Unit
) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Info, contentDescription = null, tint = WarningDark)
                Spacer(Modifier.width(8.dp))
                Text("Location permission required", color = PrimaryTextDark, fontWeight = FontWeight.SemiBold)
            }
            when {
                !hasFine -> {
                    Text(
                        "OnlyTrusted needs \"Precise location\" to register geofences with Google Play Services.",
                        color = SecondaryTextDark, fontSize = 12.sp
                    )
                    Button(
                        onClick = onRequestFine,
                        colors = ButtonDefaults.buttonColors(containerColor = BrandAccentDark),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Grant location access", color = Color.White) }
                }
                !hasBackground -> {
                    Text(
                        "Grant \"Allow all the time\" so OnlyTrusted can detect exits while the app is in the background. Without it, exit events only fire while the app is open.",
                        color = SecondaryTextDark, fontSize = 12.sp
                    )
                    Button(
                        onClick = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) onOpenAppSettings else onRequestBackground,
                        colors = ButtonDefaults.buttonColors(containerColor = BrandAccentDark),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) "Open app settings" else "Allow all the time",
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GeofenceRow(
    fence: TrustedGeofence,
    onRadiusChange: (Float) -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MidnightElevated,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(fence.name, color = PrimaryTextDark, fontWeight = FontWeight.SemiBold)
                    Text(
                        "%.4f, %.4f · radius %dm".format(
                            fence.latitude, fence.longitude, fence.radiusMeters.toInt()
                        ),
                        color = SecondaryTextDark, fontSize = 11.sp
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = DangerDark)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("100m", color = SecondaryTextDark, fontSize = 10.sp)
                Spacer(Modifier.width(6.dp))
                Slider(
                    value = fence.radiusMeters,
                    onValueChange = onRadiusChange,
                    valueRange = GeofenceState.MIN_RADIUS_METERS..GeofenceState.MAX_RADIUS_METERS,
                    steps = 17,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(6.dp))
                Text("1km", color = SecondaryTextDark, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun AlertContactRow(contact: MaskedContact, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text(contact.displayName, color = PrimaryTextDark, fontWeight = FontWeight.SemiBold)
            contact.maskedNumbers.firstOrNull()?.let {
                Text(it, color = SecondaryTextDark, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MidnightElevated,
        modifier = Modifier.fillMaxWidth()
    ) { content() }
}

private fun defaultName(existingCount: Int): String = when (existingCount) {
    0 -> "Home"
    1 -> "Office"
    else -> "Location ${existingCount + 1}"
}

@SuppressLint("MissingPermission")
private fun fetchCurrentLocation(
    context: android.content.Context,
    onResult: (Pair<Double, Double>?) -> Unit
) {
    if (!GeofenceManager.hasForegroundLocationPermission(context)) {
        onResult(null)
        return
    }
    val client = LocationServices.getFusedLocationProviderClient(context)
    runCatching {
        client.lastLocation
            .addOnSuccessListener { loc ->
                if (loc != null) onResult(loc.latitude to loc.longitude) else onResult(null)
            }
            .addOnFailureListener { onResult(null) }
    }.onFailure { onResult(null) }
}

// Compose already provides Modifier.size(Dp) via androidx.compose.foundation.layout — the
// standalone helper that lived here collided with it. Removed to avoid resolution errors.

/// Allow `suspendCancellableCoroutine { cont -> cont.resume(x) }` without importing the kotlin.coroutines
/// namespace in the main file.
private suspend fun <T> awaitResult(block: ((T?) -> Unit) -> Unit): T? =
    suspendCancellableCoroutine { cont ->
        block { cont.resume(it) }
    }
