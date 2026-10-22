// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.vault

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.onlytrusted.app.OnlyTrustedApp
import com.onlytrusted.app.data.repository.RevealedContact
import com.onlytrusted.app.data.repository.TrustedContactDraft
import com.onlytrusted.app.ui.lock.BiometricPrompter
import com.onlytrusted.app.ui.theme.BrandAccentDark
import com.onlytrusted.app.ui.theme.BrandCyanDark
import com.onlytrusted.app.ui.theme.DangerDark
import com.onlytrusted.app.ui.theme.MidnightBackground
import com.onlytrusted.app.ui.theme.MidnightCard
import com.onlytrusted.app.ui.theme.MidnightElevated
import com.onlytrusted.app.ui.theme.PrimaryTextDark
import com.onlytrusted.app.ui.theme.SecondaryTextDark
import com.onlytrusted.app.ui.theme.TertiaryTextDark

// Purple used for the Name field prefix icon (matches iOS Color.settingsPurple)
private val SettingsPurple = Color(0xFFA78BFA)

@Composable
fun VaultEditScreen(
    contactId: Long?,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as OnlyTrustedApp
    val vm: VaultViewModel = viewModel(factory = viewModelFactory {
        initializer { VaultViewModel(app) }
    })

    var authorised by remember { mutableStateOf(contactId == null) }
    var authError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(contactId) {
        if (contactId != null) {
            val activity = context as? FragmentActivity
            if (activity != null && BiometricPrompter.canAuthenticate(activity)) {
                BiometricPrompter.prompt(
                    activity = activity,
                    title = "Unlock to edit",
                    subtitle = "Confirm it's you before editing a vault entry",
                    onSuccess = { authorised = true },
                    onFailure = { authError = it }
                )
            } else {
                authorised = true
            }
        }
    }

    if (!authorised) {
        LockedView(error = authError, onBack = onDone)
        return
    }

    EditForm(
        contactId = contactId,
        onLoad = { id -> vm.loadForEdit(id) },
        onSave = { draft -> vm.save(draft) { onDone() } },
        onDelete = { id -> vm.delete(id); onDone() },
        onCancel = onDone
    )
}

@Composable
private fun LockedView(error: String?, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBackground)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            tint = BrandAccentDark,
            modifier = Modifier.size(44.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Vault is locked",
            color = PrimaryTextDark,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Unlock to edit this contact.",
            color = SecondaryTextDark,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = DangerDark, fontSize = 12.sp, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onBack,
            colors = ButtonDefaults.buttonColors(
                containerColor = BrandAccentDark,
                contentColor = Color.Black
            )
        ) { Text("Back") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditForm(
    contactId: Long?,
    onLoad: suspend (Long) -> RevealedContact?,
    onSave: (TrustedContactDraft) -> Unit,
    onDelete: (Long) -> Unit,
    onCancel: () -> Unit
) {
    // Form state
    var name by remember { mutableStateOf("") }
    var countryCode by remember { mutableStateOf(CountryCode.US) }
    var phoneDigits by remember { mutableStateOf("") }
    var selectedLabel by remember { mutableStateOf(LabelChip.FAMILY) }
    var customLabel by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    // Preserved but hidden
    var isOwner by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }

    var showCountryPicker by remember { mutableStateOf(false) }
    var showPhotoNotice by remember { mutableStateOf(false) }

    LaunchedEffect(contactId) {
        if (contactId == null) return@LaunchedEffect
        val existing = onLoad(contactId) ?: return@LaunchedEffect
        name = existing.displayName
        isOwner = existing.isOwner
        address = existing.addressPlain.orEmpty()
        notes = existing.notesPlain.orEmpty()
        email = existing.emailPlain.orEmpty()

        val relation = existing.relation.orEmpty()
        if (relation.isNotEmpty()) {
            val known = LabelChip.values().firstOrNull { it.label == relation }
            if (known != null) {
                selectedLabel = known
                customLabel = ""
            } else {
                selectedLabel = LabelChip.CUSTOM
                customLabel = relation
            }
        }

        existing.numbers.firstOrNull()?.let { tn ->
            val (code, digits) = splitDialCode(tn.normalisedNumber)
            countryCode = code ?: CountryCode.US
            phoneDigits = digits
        }
    }

    val trimmedName = name.trim()
    val canSave = trimmedName.isNotEmpty() && phoneDigits.length >= 7
    val resolvedLabel = when (selectedLabel) {
        LabelChip.CUSTOM -> customLabel.trim()
        else -> selectedLabel.label
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBackground)
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MidnightElevated)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back arrow + Contacts cyan link
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onCancel() }
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Back",
                    tint = BrandCyanDark,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    "Contacts",
                    color = BrandCyanDark,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(
                text = if (contactId == null) "Add Trusted Contact" else "Edit Trusted Contact",
                color = PrimaryTextDark,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )

            if (contactId != null) {
                IconButton(onClick = { onDelete(contactId) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = DangerDark)
                }
            }

            // Save pill
            SavePill(enabled = canSave) {
                val normalised = "+" + countryCode.dialCode.drop(1) + phoneDigits
                val labelText = resolvedLabel
                onSave(
                    TrustedContactDraft(
                        id = contactId ?: 0L,
                        displayName = trimmedName,
                        relation = labelText.ifBlank { null },
                        isOwner = isOwner,
                        email = email.ifBlank { null },
                        address = address.ifBlank { null },
                        notes = notes.ifBlank { null },
                        phoneNumbers = listOf(normalised to labelText.ifBlank { null })
                    )
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Portrait + Add Photo side-by-side
            PhotoRow(onAddPhoto = { showPhotoNotice = true })

            // Name field
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldLabel("Name")
                CyanOutlinedField {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        tint = SettingsPurple,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    FieldTextInput(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = "Priya",
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Phone row
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldLabel("Phone Number")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            BorderStroke(1.dp, BrandCyanDark.copy(alpha = 0.4f)),
                            RoundedCornerShape(14.dp)
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Phone icon + flag + chevron (dropdown)
                    Row(
                        modifier = Modifier
                            .clickable { showCountryPicker = true }
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Filled.Phone,
                            contentDescription = null,
                            tint = BrandCyanDark,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(countryCode.flag, fontSize = 18.sp)
                        Icon(
                            Icons.Filled.ExpandMore,
                            contentDescription = "Choose country code",
                            tint = BrandCyanDark,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    // Divider (cyan hairline)
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(28.dp)
                            .background(BrandCyanDark.copy(alpha = 0.25f))
                    )
                    // Digits
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 14.dp, vertical = 14.dp)
                    ) {
                        FieldTextInput(
                            value = phoneDigits,
                            onValueChange = { input ->
                                phoneDigits = input.filter { it.isDigit() }.take(15)
                            },
                            placeholder = "${countryCode.dialCode} 415 555 1234",
                            singleLine = true,
                            keyboardType = KeyboardType.Phone,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Label chips (2 rows: Family/Work/Medical, Other/Custom)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FieldLabel("Label")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(LabelChip.FAMILY, LabelChip.WORK, LabelChip.MEDICAL).forEach { chip ->
                        LabelPill(
                            chip = chip,
                            selected = selectedLabel == chip,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                selectedLabel = chip
                                if (chip != LabelChip.CUSTOM) customLabel = ""
                            }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(LabelChip.OTHER, LabelChip.CUSTOM).forEach { chip ->
                        LabelPill(
                            chip = chip,
                            selected = selectedLabel == chip,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                selectedLabel = chip
                                if (chip != LabelChip.CUSTOM) customLabel = ""
                            }
                        )
                    }
                    // Match iOS layout: third slot empty
                    Spacer(Modifier.weight(1f))
                }
                if (selectedLabel == LabelChip.CUSTOM) {
                    CyanOutlinedField {
                        FieldTextInput(
                            value = customLabel,
                            onValueChange = { customLabel = it },
                            placeholder = "Custom label",
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Notes (optional)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldLabel("Notes (optional)")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            BorderStroke(1.dp, BrandCyanDark.copy(alpha = 0.4f)),
                            RoundedCornerShape(14.dp)
                        )
                        .heightIn(min = 110.dp)
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Filled.Description,
                        contentDescription = null,
                        tint = BrandCyanDark,
                        modifier = Modifier
                            .size(22.dp)
                            .padding(top = 2.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    FieldTextInput(
                        value = notes,
                        onValueChange = { notes = it },
                        placeholder = "E.g. sister, close friend, doctor…",
                        singleLine = false,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 70.dp)
                    )
                }
            }
        }
    }

    if (showCountryPicker) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showCountryPicker = false },
            sheetState = sheetState,
            containerColor = MidnightElevated
        ) {
            LazyColumn {
                items(CountryCode.values().toList()) { code ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                countryCode = code
                                showCountryPicker = false
                            }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(code.flag, fontSize = 20.sp)
                        Spacer(Modifier.width(12.dp))
                        Text(code.countryName, color = PrimaryTextDark, fontSize = 15.sp)
                        Spacer(Modifier.weight(1f))
                        Text(code.dialCode, color = SecondaryTextDark, fontSize = 14.sp)
                        if (countryCode == code) {
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Filled.Check, contentDescription = null, tint = BrandAccentDark)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showPhotoNotice) {
        AlertDialog(
            onDismissRequest = { showPhotoNotice = false },
            title = { Text("Photo attach coming soon", color = PrimaryTextDark) },
            text = {
                Text(
                    "Attaching a photo for a trusted contact isn't supported in this build yet.",
                    color = SecondaryTextDark
                )
            },
            confirmButton = {
                TextButton(onClick = { showPhotoNotice = false }) {
                    Text("OK", color = BrandAccentDark)
                }
            },
            containerColor = MidnightCard
        )
    }
}

@Composable
private fun SavePill(enabled: Boolean, onClick: () -> Unit) {
    val fill = if (enabled) BrandCyanDark else MidnightElevated
    val textColor = if (enabled) MidnightBackground else TertiaryTextDark
    val glow = if (enabled) BrandCyanDark.copy(alpha = 0.5f) else Color.Transparent
    Box(
        modifier = Modifier
            .shadow(elevation = 8.dp, shape = RoundedCornerShape(999.dp), ambientColor = glow, spotColor = glow)
            .background(fill, RoundedCornerShape(999.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 18.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("Save", color = textColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PhotoRow(onAddPhoto: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
    ) {
        // Large portrait circle (160dp) with cyan border + glow
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .shadow(
                    elevation = 10.dp,
                    shape = CircleShape,
                    ambientColor = BrandCyanDark.copy(alpha = 0.7f),
                    spotColor = BrandCyanDark.copy(alpha = 0.7f)
                )
                .size(160.dp)
                .background(MidnightElevated, CircleShape)
                .border(BorderStroke(2.dp, BrandCyanDark), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = null,
                tint = SecondaryTextDark,
                modifier = Modifier.size(92.dp)
            )
        }

        // Smaller Add Photo circle (72dp) + 2-line label beneath
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.clickable { onAddPhoto() }
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .shadow(
                        elevation = 8.dp,
                        shape = CircleShape,
                        ambientColor = BrandCyanDark.copy(alpha = 0.6f),
                        spotColor = BrandCyanDark.copy(alpha = 0.6f)
                    )
                    .size(72.dp)
                    .border(BorderStroke(1.5.dp, BrandCyanDark), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Filled.CameraAlt,
                    contentDescription = "Add contact photo",
                    tint = BrandCyanDark,
                    modifier = Modifier.size(26.dp)
                )
            }
            Text(
                "Add\nPhoto",
                color = BrandCyanDark,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        color = SecondaryTextDark,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun CyanOutlinedField(
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                BorderStroke(1.dp, BrandCyanDark.copy(alpha = 0.4f)),
                RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
private fun FieldTextInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    singleLine: Boolean,
    keyboardType: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        textStyle = TextStyle(color = PrimaryTextDark, fontSize = 15.sp),
        cursorBrush = SolidColor(BrandCyanDark),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier,
        decorationBox = { inner ->
            if (value.isEmpty()) {
                Text(placeholder, color = TertiaryTextDark, fontSize = 15.sp)
            }
            inner()
        }
    )
}

@Composable
private fun LabelPill(
    chip: LabelChip,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val fill = if (selected) BrandCyanDark.copy(alpha = 0.08f) else Color.Transparent
    val borderColor = if (selected) BrandCyanDark else BrandCyanDark.copy(alpha = 0.35f)
    val borderWidth: Dp = if (selected) 2.dp else 1.dp
    val glow = if (selected) BrandCyanDark.copy(alpha = 0.5f) else Color.Transparent

    Row(
        modifier = modifier
            .shadow(
                elevation = if (selected) 8.dp else 0.dp,
                shape = RoundedCornerShape(14.dp),
                ambientColor = glow,
                spotColor = glow
            )
            .background(fill, RoundedCornerShape(14.dp))
            .border(BorderStroke(borderWidth, borderColor), RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = chip.icon,
            contentDescription = null,
            tint = BrandCyanDark,
            modifier = Modifier.size(16.dp)
        )
        Text(
            chip.label,
            color = PrimaryTextDark,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private enum class LabelChip(val label: String, val icon: ImageVector) {
    FAMILY("Family", Icons.Filled.Groups),
    WORK("Work", Icons.Filled.Work),
    MEDICAL("Medical", Icons.Filled.HealthAndSafety),
    OTHER("Other", Icons.Filled.MoreHoriz),
    CUSTOM("Custom", Icons.Filled.LocalOffer)
}

internal enum class CountryCode(
    val countryName: String,
    val dialCode: String,
    val flag: String
) {
    US("United States", "+1", "🇺🇸"),
    UK("United Kingdom", "+44", "🇬🇧"),
    IN("India", "+91", "🇮🇳"),
    CA("Canada", "+1", "🇨🇦"),
    AU("Australia", "+61", "🇦🇺"),
    DE("Germany", "+49", "🇩🇪"),
    FR("France", "+33", "🇫🇷"),
    JP("Japan", "+81", "🇯🇵"),
    BR("Brazil", "+55", "🇧🇷"),
    MX("Mexico", "+52", "🇲🇽")
}

private fun splitDialCode(raw: String): Pair<CountryCode?, String> {
    val digits = raw.filter { it.isDigit() }
    if (!raw.startsWith("+")) return null to digits
    for (c in CountryCode.values().sortedByDescending { it.dialCode.length }) {
        val strip = c.dialCode.drop(1)
        if (digits.startsWith(strip)) {
            return c to digits.drop(strip.length)
        }
    }
    return null to digits
}
