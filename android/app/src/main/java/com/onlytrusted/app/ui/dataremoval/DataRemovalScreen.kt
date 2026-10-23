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
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlytrusted.app.ui.theme.BrandAccentDark
import com.onlytrusted.app.ui.theme.BrandCyanDark
import com.onlytrusted.app.ui.theme.MidnightBackground
import com.onlytrusted.app.ui.theme.MidnightCard
import com.onlytrusted.app.ui.theme.MidnightElevated
import com.onlytrusted.app.ui.theme.PrimaryTextDark
import com.onlytrusted.app.ui.theme.SecondaryTextDark
import com.onlytrusted.app.ui.theme.TertiaryTextDark
import com.onlytrusted.app.ui.theme.WarningDark

/** Data Removal Scan (FREE, visibility-only). Mirrors iOS `DataRemovalScreen`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataRemovalScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    var firstName by remember { mutableStateOf(DataRemovalState.firstName(context)) }
    var lastName by remember { mutableStateOf(DataRemovalState.lastName(context)) }
    var stateCode by remember { mutableStateOf(DataRemovalState.stateCode(context)) }
    var statePickerOpen by remember { mutableStateOf(false) }
    var sitesVisible by remember { mutableStateOf(false) }

    val sites = remember { DataRemovalCatalog.sites(context) }

    val canScan = firstName.trim().isNotEmpty() && lastName.trim().isNotEmpty()

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
            // Warning hero
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MidnightElevated,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = WarningDark.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(16.dp)
                    )
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
                        "This scan does not remove your data. It only generates search links so you can see where your name may be published. Removal has to be done manually with each site.",
                        fontSize = 12.sp, color = SecondaryTextDark
                    )
                }
            }

            // Form
            CardBox {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Your details",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PrimaryTextDark
                    )
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "State (optional)",
                            fontSize = 13.sp,
                            color = SecondaryTextDark,
                            modifier = Modifier.weight(1f)
                        )
                        Box {
                            TextButton(onClick = { statePickerOpen = true }) {
                                Text(
                                    stateCode.ifEmpty { "Any" },
                                    color = BrandAccentDark,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            DropdownMenu(
                                expanded = statePickerOpen,
                                onDismissRequest = { statePickerOpen = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Any") },
                                    onClick = {
                                        stateCode = ""
                                        DataRemovalState.setStateCode(context, "")
                                        statePickerOpen = false
                                    }
                                )
                                USStates.all.forEach { s ->
                                    DropdownMenuItem(
                                        text = { Text(s) },
                                        onClick = {
                                            stateCode = s
                                            DataRemovalState.setStateCode(context, s)
                                            statePickerOpen = false
                                        }
                                    )
                                }
                            }
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
                Text(if (canScan) "Scan now" else "Enter first + last name")
            }

            if (sitesVisible) {
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
                            "Tap Check on any row to open the site's search. We don't share your name — the browser does.",
                            fontSize = 11.sp, color = SecondaryTextDark
                        )
                        sites.forEach { site ->
                            SiteRow(site = site, firstName = firstName, lastName = lastName) { uri ->
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
private fun SiteRow(
    site: DataRemovalSite,
    firstName: String,
    lastName: String,
    onOpen: (android.net.Uri) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(site.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PrimaryTextDark)
            Text(
                hostLabel(site.searchUrl),
                fontSize = 10.sp,
                color = SecondaryTextDark,
                maxLines = 1
            )
        }
        Button(
            onClick = {
                val uri = DataRemovalCatalog.renderSearchUrl(site.searchUrl, firstName, lastName)
                if (uri != null) onOpen(uri)
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = BrandAccentDark,
                contentColor = Color.White
            )
        ) {
            Text("Check")
        }
    }
}

private fun hostLabel(raw: String): String =
    runCatching { android.net.Uri.parse(raw).host.orEmpty() }
        .getOrDefault(raw)

@Composable
private fun CardBox(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MidnightElevated,
        modifier = Modifier.fillMaxWidth()
    ) { content() }
}
