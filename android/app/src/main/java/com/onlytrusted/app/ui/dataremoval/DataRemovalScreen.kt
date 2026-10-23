// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.dataremoval

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import com.onlytrusted.app.ui.theme.BrandAccentDark
import com.onlytrusted.app.ui.theme.BrandCyanDark
import com.onlytrusted.app.ui.theme.MidnightBackground
import com.onlytrusted.app.ui.theme.MidnightElevated
import com.onlytrusted.app.ui.theme.PrimaryTextDark
import com.onlytrusted.app.ui.theme.SecondaryTextDark
import com.onlytrusted.app.ui.theme.WarningDark

/**
 * Data Removal Scan (FREE, visibility-only). Three search modes — Name / Phone /
 * Address — selectable via segmented picker. Fields change to match. Only sites
 * whose catalog entry supports the active mode appear in the "Check" list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataRemovalScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    var mode by remember { mutableStateOf(DataRemovalState.mode(context)) }
    var firstName by remember { mutableStateOf(DataRemovalState.firstName(context)) }
    var lastName by remember { mutableStateOf(DataRemovalState.lastName(context)) }
    var stateCode by remember { mutableStateOf(DataRemovalState.stateCode(context)) }
    var phone by remember { mutableStateOf(DataRemovalState.phone(context)) }
    var street by remember { mutableStateOf(DataRemovalState.street(context)) }
    var city by remember { mutableStateOf(DataRemovalState.city(context)) }
    var statePickerOpen by remember { mutableStateOf(false) }
    var sitesVisible by remember { mutableStateOf(false) }

    fun currentForm() = DataRemovalForm(
        firstName = firstName, lastName = lastName, state = stateCode,
        phone = phone, street = street, city = city
    )

    val canScan = DataRemovalCatalog.sites(context, mode)
        .any { DataRemovalCatalog.renderUrl(it, mode, currentForm()) != null }

    val scanLabel = if (canScan) "Scan now" else when (mode) {
        SearchMode.NAME -> "Enter first + last name"
        SearchMode.PHONE -> "Enter a phone number"
        SearchMode.ADDRESS -> "Enter street + city + state"
    }

    Scaffold(
        containerColor = MidnightBackground,
        topBar = {
            TopAppBar(
                title = { Text("Data Removal Scan", color = PrimaryTextDark) },
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
            WarningHero()

            // Mode picker
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SearchMode.entries.forEachIndexed { index, m ->
                    SegmentedButton(
                        selected = mode == m,
                        onClick = {
                            mode = m
                            DataRemovalState.setMode(context, m)
                            sitesVisible = false
                        },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = SearchMode.entries.size
                        )
                    ) {
                        Text(m.label)
                    }
                }
            }

            // Form (mode-dependent)
            CardBox {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val icon = when (mode) {
                        SearchMode.NAME -> Icons.Filled.Person
                        SearchMode.PHONE -> Icons.Filled.Phone
                        SearchMode.ADDRESS -> Icons.Filled.LocationOn
                    }
                    val title = when (mode) {
                        SearchMode.NAME -> "Your name"
                        SearchMode.PHONE -> "Phone number"
                        SearchMode.ADDRESS -> "Address"
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(10.dp), color = BrandAccentDark) {
                            Icon(icon, null, tint = Color.White, modifier = Modifier.padding(8.dp))
                        }
                        Spacer(Modifier.size(10.dp))
                        Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = PrimaryTextDark)
                    }
                    when (mode) {
                        SearchMode.NAME -> {
                            OutlinedTextField(
                                value = firstName,
                                onValueChange = {
                                    firstName = it
                                    DataRemovalState.setFirstName(context, it)
                                },
                                placeholder = { Text("First name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = lastName,
                                onValueChange = {
                                    lastName = it
                                    DataRemovalState.setLastName(context, it)
                                },
                                placeholder = { Text("Last name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            StatePickerRow(
                                label = "State (optional)",
                                selected = stateCode,
                                open = statePickerOpen,
                                onOpenChange = { statePickerOpen = it },
                                onSelected = {
                                    stateCode = it
                                    DataRemovalState.setStateCode(context, it)
                                }
                            )
                        }
                        SearchMode.PHONE -> {
                            OutlinedTextField(
                                value = phone,
                                onValueChange = {
                                    phone = it
                                    DataRemovalState.setPhone(context, it)
                                },
                                placeholder = { Text("Phone number (digits only)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                "Enter 10 digits (US) or full international format with country code.",
                                fontSize = 11.sp,
                                color = SecondaryTextDark
                            )
                        }
                        SearchMode.ADDRESS -> {
                            OutlinedTextField(
                                value = street,
                                onValueChange = {
                                    street = it
                                    DataRemovalState.setStreet(context, it)
                                },
                                placeholder = { Text("Street (e.g. 123 Main St)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = city,
                                onValueChange = {
                                    city = it
                                    DataRemovalState.setCity(context, it)
                                },
                                placeholder = { Text("City") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            StatePickerRow(
                                label = "State",
                                selected = stateCode,
                                open = statePickerOpen,
                                onOpenChange = { statePickerOpen = it },
                                onSelected = {
                                    stateCode = it
                                    DataRemovalState.setStateCode(context, it)
                                }
                            )
                        }
                    }
                }
            }

            // Scan button
            Button(
                onClick = { sitesVisible = canScan },
                enabled = canScan,
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandAccentDark,
                    contentColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(scanLabel)
            }

            if (sitesVisible) {
                val visibleSites = DataRemovalCatalog.sites(context, mode)
                CardBox {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = RoundedCornerShape(10.dp), color = BrandAccentDark) {
                                Icon(Icons.Filled.PersonSearch, null, tint = Color.White,
                                    modifier = Modifier.padding(8.dp))
                            }
                            Spacer(Modifier.size(10.dp))
                            Text(
                                "People-search sites",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PrimaryTextDark
                            )
                        }
                        Text(
                            "Tap Check on any row to open the site's search in your browser. We don't share your details — the browser does.",
                            fontSize = 11.sp, color = SecondaryTextDark
                        )
                        if (visibleSites.isEmpty()) {
                            Text(
                                "No sites in the catalog support ${mode.label.lowercase()} search.",
                                fontSize = 12.sp,
                                color = SecondaryTextDark
                            )
                        }
                        val form = currentForm()
                        visibleSites.forEach { site ->
                            SiteRow(site = site, mode = mode, form = form) { uri ->
                                val intent = Intent(Intent.ACTION_VIEW, uri)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                runCatching { context.startActivity(intent) }
                            }
                        }
                    }
                }
            }

            // Paid tools card
            CardBox {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(10.dp), color = BrandAccentDark) {
                            Icon(Icons.Filled.Info, null, tint = Color.White,
                                modifier = Modifier.padding(8.dp))
                        }
                        Spacer(Modifier.size(10.dp))
                        Text(
                            "To actually remove",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PrimaryTextDark
                        )
                    }
                    Text(
                        "Use a paid data-removal service like OneRep.com, Optery.com, or DeleteMe.com. OnlyTrusted cannot remove information on your behalf.",
                        fontSize = 12.sp, color = SecondaryTextDark
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun WarningHero() {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MidnightElevated,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, WarningDark.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(10.dp), color = WarningDark) {
                    Icon(Icons.Filled.Warning, null, tint = Color.White,
                        modifier = Modifier.padding(8.dp))
                }
                Spacer(Modifier.size(10.dp))
                Text(
                    "Visibility only",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = WarningDark
                )
            }
            Text(
                "This scan does not remove your data. It only generates search links so you can see where your name, phone, or address may be published. Removal has to be done manually with each site.",
                fontSize = 12.sp, color = SecondaryTextDark
            )
        }
    }
}

@Composable
private fun StatePickerRow(
    label: String,
    selected: String,
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    onSelected: (String) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, color = SecondaryTextDark, modifier = Modifier.weight(1f))
        Box {
            TextButton(onClick = { onOpenChange(true) }) {
                Text(
                    selected.ifEmpty { "Any" },
                    color = BrandAccentDark,
                    fontWeight = FontWeight.SemiBold
                )
            }
            DropdownMenu(expanded = open, onDismissRequest = { onOpenChange(false) }) {
                DropdownMenuItem(
                    text = { Text("Any") },
                    onClick = { onSelected(""); onOpenChange(false) }
                )
                USStates.all.forEach { s ->
                    DropdownMenuItem(
                        text = { Text(s) },
                        onClick = { onSelected(s); onOpenChange(false) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SiteRow(
    site: DataRemovalSite,
    mode: SearchMode,
    form: DataRemovalForm,
    onOpen: (android.net.Uri) -> Unit
) {
    val uri = DataRemovalCatalog.renderUrl(site, mode, form)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(site.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PrimaryTextDark)
            Text(
                uri?.host.orEmpty().ifEmpty { site.template(mode).orEmpty() },
                fontSize = 10.sp,
                color = SecondaryTextDark,
                maxLines = 1
            )
        }
        Button(
            enabled = uri != null,
            onClick = { if (uri != null) onOpen(uri) },
            colors = ButtonDefaults.buttonColors(
                containerColor = BrandAccentDark,
                disabledContainerColor = MidnightElevated,
                contentColor = Color.White
            )
        ) {
            Text("Check")
        }
    }
}

@Composable
private fun CardBox(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MidnightElevated,
        modifier = Modifier.fillMaxWidth()
    ) { content() }
}
