// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.subscription

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlytrusted.app.ui.theme.BrandAccentDark
import com.onlytrusted.app.ui.theme.BrandCyanDark
import com.onlytrusted.app.ui.theme.MidnightBackground
import com.onlytrusted.app.ui.theme.MidnightElevated
import com.onlytrusted.app.ui.theme.PrimaryTextDark
import com.onlytrusted.app.ui.theme.SecondaryTextDark
import com.onlytrusted.app.ui.theme.TertiaryTextDark
import java.util.Calendar
import java.util.Date

private val settingsBlue = Color(0xFF2266F0)

private enum class BillingCadence { Monthly, Yearly }
private enum class Plan { Free, Premium, Family, HighPremium, Elite }

private data class PlanFeature(val text: String, val included: Boolean)

private fun Plan.displayName(): String = when (this) {
    Plan.Free -> "Free"
    Plan.Premium -> "Premium"
    Plan.Family -> "Family"
    Plan.HighPremium -> "Pro"
    Plan.Elite -> "Elite"
}

private fun Plan.tier(): SubscriptionTier = when (this) {
    Plan.Free -> SubscriptionTier.FREE
    Plan.Premium -> SubscriptionTier.PREMIUM
    Plan.Family -> SubscriptionTier.FAMILY
    Plan.HighPremium -> SubscriptionTier.HIGH_PREMIUM
    Plan.Elite -> SubscriptionTier.ELITE
}

private fun Plan.priceLabel(cadence: BillingCadence): String = when (this) {
    Plan.Free -> "$0"
    Plan.Premium -> if (cadence == BillingCadence.Monthly) "$0.99" else "$4.99"
    Plan.Family -> if (cadence == BillingCadence.Monthly) "$1.99" else "$9.99"
    Plan.HighPremium -> if (cadence == BillingCadence.Monthly) "$2.99" else "$19.99"
    Plan.Elite -> if (cadence == BillingCadence.Monthly) "$3.99" else "$29.99"
}

private fun Plan.cadenceSuffix(cadence: BillingCadence): String = when (this) {
    Plan.Free -> ""
    Plan.Premium, Plan.Family, Plan.HighPremium, Plan.Elite ->
        if (cadence == BillingCadence.Monthly) "per month" else "per year"
}

private fun Plan.features(): List<PlanFeature> = when (this) {
    Plan.Free -> listOf(
        PlanFeature("Trusted contacts + Blocking & Unknown", true),
        PlanFeature("Call Handling + Text Handling + Notifications", true),
        PlanFeature("Data Removal Scan (visibility-only)", true),
        PlanFeature("App Lock, Pocket-dial, Screenshot protection", true),
        PlanFeature("Panic lock + Data export (JSON/CSV)", true)
    )
    Plan.Premium -> listOf(
        PlanFeature("Privacy Shield + mic/camera use alerts", true),
        PlanFeature("Breach Watch (up to 5 emails)", true),
        PlanFeature("Private Aliases (unlimited) + CSV backup", true),
        PlanFeature("Identity Protection + yearly renewal reminders", true),
        PlanFeature("Data Removal 90-day re-check reminders", true),
        PlanFeature("Weekly PDF digest", true),
        PlanFeature("Quiet hours + area-code allowlist", true)
    )
    Plan.Family -> listOf(
        PlanFeature("All Premium features", true),
        PlanFeature("Shared via your App Store / Play family group", true),
        PlanFeature("Per-device alias books (separately exportable)", true),
        PlanFeature("Up to 5 members on one subscription", true)
    )
    Plan.HighPremium -> listOf(
        PlanFeature("All Premium features", true),
        PlanFeature("Duress Mode + decoy vault", true),
        PlanFeature("SIM-Swap Alarm + Wangiri auto-block", true),
        PlanFeature("App Permission Watchdog", true),
        PlanFeature("Tracker Finder (AirTag, Tile, SmartTag)", true),
        PlanFeature("Smart Link Guard (SMS + QR + share sheet)", true),
        PlanFeature("Safety Check-In", true)
    )
    Plan.Elite -> listOf(
        PlanFeature("Everything in Pro", true),
        PlanFeature("App Shield (block any app behind biometric)", true),
        PlanFeature("Border Mode preset + Panic Lockdown", true),
        PlanFeature("Hardware Security Key + Signed Audit Log", true),
        PlanFeature("Encrypted vault backup + Shamir Split + Trusted Witness", true),
        PlanFeature("Dead-Man Wipe + Geo-fence Auto-Lock", true),
        PlanFeature("Face-Change Lock + Voice Biometric", true),
        PlanFeature("Steganographic Vault + Rogue Network + Threat Intel", true)
    )
}

@Composable
fun UpgradeToPremiumScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current

    var cadence by remember { mutableStateOf(BillingCadence.Yearly) }
    var selectedPlan by remember { mutableStateOf(Plan.Premium) }
    var showPurchaseConfirmation by remember { mutableStateOf(false) }
    var restoreNotice by remember { mutableStateOf<String?>(null) }

    Scaffold(containerColor = Color.Transparent) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            com.onlytrusted.app.ui.theme.AppBackdrop()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Pinned top header — translucent so backdrop shows through
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MidnightBackground.copy(alpha = 0.85f),
                                    MidnightBackground.copy(alpha = 0.55f)
                                )
                            )
                        )
                        .padding(horizontal = 18.dp, vertical = 12.dp)
                ) {
                    Header(onBack = onBack)
                }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp)
                    .padding(top = 4.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                HeroCrown()
            IntroText()
            CadenceToggle(cadence = cadence, onChange = { cadence = it })
            PlansGrid(
                cadence = cadence,
                selectedPlan = selectedPlan,
                onSelect = { selectedPlan = it }
            )
            val ctaLabel = when (selectedPlan) {
                Plan.Free -> "Keep Free Plan"
                Plan.Premium -> "Subscribe to Premium"
                Plan.Family -> "Subscribe to Family"
                Plan.HighPremium -> "Subscribe to Pro"
                Plan.Elite -> "Subscribe to Elite"
            }
            CtaButton(label = ctaLabel, onClick = { showPurchaseConfirmation = true })
            RestoreButton(
                onClick = {
                    restoreNotice = if (SubscriptionState.isActive(context)) {
                        "Your Premium access is already active."
                    } else {
                        "No previous purchases were found on this Google account."
                    }
                }
            )
            }
            }
        }

        if (showPurchaseConfirmation) {
            val priceLabel = selectedPlan.priceLabel(cadence)
            val suffix = selectedPlan.cadenceSuffix(cadence)
            AlertDialog(
                onDismissRequest = { showPurchaseConfirmation = false },
                title = { Text("Confirm subscription", color = PrimaryTextDark) },
                text = {
                    val suffixText = if (suffix.isNotEmpty()) " $suffix" else ""
                    Text(
                        "$priceLabel$suffixText will be charged to your Google Play account " +
                            "and auto-renew unless cancelled.",
                        color = SecondaryTextDark
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showPurchaseConfirmation = false
                        val cycleDays = if (cadence == BillingCadence.Monthly) 30 else 365
                        val nextDate = Calendar.getInstance().apply {
                            add(Calendar.DAY_OF_YEAR, cycleDays)
                        }.time
                        val suffixText = if (suffix.isNotEmpty()) " $suffix" else ""
                        SubscriptionState.markPurchased(
                            ctx = context,
                            tier = selectedPlan.tier(),
                            plan = selectedPlan.displayName(),
                            pricing = "$priceLabel$suffixText",
                            nextDate = nextDate
                        )
                        onBack()
                    }) {
                        Text("Subscribe", color = BrandCyanDark)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPurchaseConfirmation = false }) {
                        Text("Cancel", color = TertiaryTextDark)
                    }
                },
                containerColor = MidnightElevated
            )
        }

        if (restoreNotice != null) {
            AlertDialog(
                onDismissRequest = { restoreNotice = null },
                title = { Text("Restore Purchase", color = PrimaryTextDark) },
                text = { Text(restoreNotice ?: "", color = SecondaryTextDark) },
                confirmButton = {
                    TextButton(onClick = { restoreNotice = null }) {
                        Text("OK", color = BrandCyanDark)
                    }
                },
                containerColor = MidnightElevated
            )
        }
    }
}

// =============================================================
//  Header
// =============================================================

@Composable
private fun Header(onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            onClick = onBack,
            color = Color.Transparent,
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Icon(
                Icons.Filled.ChevronLeft,
                contentDescription = "Back",
                tint = BrandCyanDark,
                modifier = Modifier.size(40.dp)
            )
        }
        Text(
            "Upgrade to Premium",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = PrimaryTextDark,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

// =============================================================
//  Hero crown + intro
// =============================================================

@Composable
private fun HeroCrown() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(130.dp)
                .shadow(
                    elevation = 24.dp,
                    shape = CircleShape,
                    ambientColor = BrandCyanDark,
                    spotColor = BrandCyanDark
                )
                .border(3.dp, BrandCyanDark, CircleShape)
        ) {
            Icon(
                Icons.Filled.EmojiEvents,
                contentDescription = null,
                tint = subGold,
                modifier = Modifier
                    .size(54.dp)
                    .shadow(
                        elevation = 14.dp,
                        shape = CircleShape,
                        ambientColor = subGold,
                        spotColor = subGold
                    )
            )
        }
    }
}

@Composable
private fun IntroText() {
    Text(
        "Get more protection and powerful features with OnlyTrusted Premium.",
        fontSize = 16.sp,
        color = SecondaryTextDark,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    )
}

// =============================================================
//  Cadence toggle
// =============================================================

@Composable
private fun CadenceToggle(cadence: BillingCadence, onChange: (BillingCadence) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BrandCyanDark.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        CadenceButton(
            selected = cadence == BillingCadence.Monthly,
            title = "Monthly",
            subtitle = null,
            onClick = { onChange(BillingCadence.Monthly) },
            modifier = Modifier.weight(1f)
        )
        CadenceButton(
            selected = cadence == BillingCadence.Yearly,
            title = "Yearly",
            subtitle = "Save 58%",
            onClick = { onChange(BillingCadence.Yearly) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun CadenceButton(
    selected: Boolean,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) BrandCyanDark else Color.Transparent,
        modifier = modifier.heightIn(min = 52.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .padding(vertical = 6.dp)
        ) {
            Text(
                title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (selected) MidnightBackground else PrimaryTextDark
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) {
                        MidnightBackground.copy(alpha = 0.8f)
                    } else {
                        BrandCyanDark
                    }
                )
            }
        }
    }
}

// =============================================================
//  Plans grid
// =============================================================

@Composable
private fun PlansGrid(
    cadence: BillingCadence,
    selectedPlan: Plan,
    onSelect: (Plan) -> Unit
) {
    // All plans render as full-width cards stacked vertically — matches the Elite card
    // format which users liked. Keeps the layout consistent across every tier so
    // comparing is easier.
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PlanCard(Plan.Free, cadence, selectedPlan == Plan.Free, onSelect, Modifier.fillMaxWidth())
        PlanCard(Plan.Premium, cadence, selectedPlan == Plan.Premium, onSelect, Modifier.fillMaxWidth())
        PlanCard(Plan.Family, cadence, selectedPlan == Plan.Family, onSelect, Modifier.fillMaxWidth())
        PlanCard(Plan.HighPremium, cadence, selectedPlan == Plan.HighPremium, onSelect, Modifier.fillMaxWidth())
        PlanCard(Plan.Elite, cadence, selectedPlan == Plan.Elite, onSelect, Modifier.fillMaxWidth())
    }
}

@Composable
private fun PlanCard(
    plan: Plan,
    cadence: BillingCadence,
    selected: Boolean,
    onSelect: (Plan) -> Unit,
    modifier: Modifier = Modifier
) {
    val outlineColor =
        if (selected) BrandCyanDark else BrandCyanDark.copy(alpha = 0.2f)
    val outlineWidth = if (selected) 2.dp else 1.dp

    Surface(
        onClick = { onSelect(plan) },
        shape = RoundedCornerShape(18.dp),
        color = Color.Transparent,
        modifier = modifier
            .heightIn(min = 360.dp)
            .then(
                if (selected) {
                    Modifier.shadow(
                        elevation = 16.dp,
                        shape = RoundedCornerShape(18.dp),
                        ambientColor = BrandCyanDark,
                        spotColor = BrandCyanDark
                    )
                } else Modifier
            )
            .border(outlineWidth, outlineColor, RoundedCornerShape(18.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PlanChip(plan)
            Text(
                plan.displayName(),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = if (plan == Plan.Premium) BrandCyanDark else PrimaryTextDark
            )
            Text(
                plan.priceLabel(cadence),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryTextDark
            )
            val suffix = plan.cadenceSuffix(cadence)
            if (suffix.isNotEmpty()) {
                Text(
                    suffix,
                    fontSize = 12.sp,
                    color = SecondaryTextDark,
                    textAlign = TextAlign.Center
                )
            }
            when (plan) {
                Plan.Premium -> MostPopularPill()
                Plan.Free -> Text(
                    "Basic protection",
                    fontSize = 12.sp,
                    color = SecondaryTextDark,
                    textAlign = TextAlign.Center
                )
                Plan.Family -> Text(
                    "Up to 5 devices",
                    fontSize = 12.sp,
                    color = SecondaryTextDark,
                    textAlign = TextAlign.Center
                )
                Plan.HighPremium -> AllProtectionPill()
                Plan.Elite -> ElitePill()
            }

            Spacer(Modifier.size(2.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                plan.features().forEach { feature ->
                    FeatureBullet(feature.text, feature.included)
                }
            }
        }
    }
}

@Composable
private fun PlanChip(plan: Plan) {
    val size = 46.dp
    when (plan) {
        Plan.Free -> Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size)
                .background(MidnightElevated, CircleShape)
        ) {
            Icon(
                Icons.Filled.Shield,
                contentDescription = null,
                tint = SecondaryTextDark,
                modifier = Modifier.size(22.dp)
            )
        }
        Plan.Premium -> Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size)
                .shadow(
                    elevation = 10.dp,
                    shape = CircleShape,
                    ambientColor = BrandCyanDark,
                    spotColor = BrandCyanDark
                )
                .border(1.5.dp, BrandCyanDark, CircleShape)
        ) {
            Icon(
                Icons.Filled.EmojiEvents,
                contentDescription = null,
                tint = BrandCyanDark,
                modifier = Modifier.size(22.dp)
            )
        }
        Plan.Family -> Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size)
                .background(settingsBlue.copy(alpha = 0.18f), CircleShape)
        ) {
            Icon(
                Icons.Filled.Group,
                contentDescription = null,
                tint = settingsBlue,
                modifier = Modifier.size(22.dp)
            )
        }
        Plan.HighPremium -> Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size)
                .border(1.5.dp, aboutOrange, CircleShape)
                .shadow(10.dp, CircleShape, ambientColor = aboutOrange, spotColor = aboutOrange)
        ) {
            Icon(
                Icons.Filled.Shield,
                contentDescription = null,
                tint = aboutOrange,
                modifier = Modifier.size(22.dp)
            )
        }
        Plan.Elite -> Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size)
                .border(1.5.dp, BrandCyanDark, CircleShape)
                .shadow(10.dp, CircleShape, ambientColor = BrandCyanDark, spotColor = BrandCyanDark)
        ) {
            Icon(
                Icons.Filled.VpnKey,
                contentDescription = null,
                tint = BrandCyanDark,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

private val aboutOrange = Color(0xFFF59E0B)

@Composable
private fun MostPopularPill() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .border(1.dp, BrandCyanDark, RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            "Most Popular",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = BrandCyanDark
        )
    }
}

@Composable
private fun AllProtectionPill() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .border(1.dp, aboutOrange, RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            "All Protection",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = aboutOrange
        )
    }
}

@Composable
private fun ElitePill() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .border(1.dp, BrandCyanDark, RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            "Elite — Max Security",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = BrandCyanDark
        )
    }
}

@Composable
private fun FeatureBullet(text: String, included: Boolean) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            if (included) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
            contentDescription = null,
            tint = if (included) BrandCyanDark else TertiaryTextDark,
            modifier = Modifier.size(15.dp)
        )
        Text(
            text,
            fontSize = 12.sp,
            color = if (included) PrimaryTextDark else TertiaryTextDark
        )
    }
}

// =============================================================
//  CTA + restore
// =============================================================

@Composable
private fun CtaButton(label: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .shadow(
                elevation = 24.dp,
                shape = RoundedCornerShape(999.dp),
                ambientColor = BrandCyanDark,
                spotColor = BrandCyanDark
            )
    ) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(999.dp),
            color = Color.Transparent,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(BrandCyanDark, BrandAccentDark)
                        ),
                        shape = RoundedCornerShape(999.dp)
                    )
            ) {
                Text(
                    label,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MidnightBackground
                )
            }
        }
    }
}

@Composable
private fun RestoreButton(onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            onClick = onClick,
            color = Color.Transparent
        ) {
            Text(
                "Restore Purchase",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = BrandCyanDark,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}

