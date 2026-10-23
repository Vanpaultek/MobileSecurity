#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 VanPaulTek Inc
# SPDX-License-Identifier: AGPL-3.0-or-later
#
# Flip the booted iOS simulator's OnlyTrusted subscription tier for testing. Rough
# equivalent of `adb shell` tapping through the Upgrade flow on Android — this writes
# the UserDefaults keys that `SubscriptionState` reads at init-time and relaunches the
# app so the singleton picks up the new values.
#
# Usage:
#   scripts/ios_set_tier.sh <tier>
#     tier = free | premium | family | highPremium | elite
#
# Optional env:
#   SIM_UDID  — target simulator UDID. Default: whichever one is currently booted.
#   BUNDLE_ID — app bundle id. Default: com.onlytrusted.app
#
# The script does nothing clever — it just writes the UserDefaults keys and relaunches
# the app so SubscriptionState.init() reads the new tier. For a real StoreKit purchase
# flow use Settings → Upgrade to Premium → Subscribe inside the app.

set -euo pipefail

TIER="${1:-}"
if [[ -z "$TIER" ]]; then
    echo "Usage: $0 <free|premium|family|highPremium|elite>" >&2
    exit 1
fi

case "$TIER" in
    free|premium|family|highPremium|elite) ;;
    *) echo "Unknown tier '$TIER'. Valid: free premium family highPremium elite" >&2; exit 1 ;;
esac

SIM_UDID="${SIM_UDID:-$(xcrun simctl list devices booted -j | python3 -c '
import json, sys
data = json.load(sys.stdin)
for runtime, devs in data["devices"].items():
    for d in devs:
        if d.get("state") == "Booted":
            print(d["udid"]); sys.exit(0)
')}"
if [[ -z "$SIM_UDID" ]]; then
    echo "No booted iOS simulator. Boot one in Xcode first." >&2
    exit 1
fi

BUNDLE_ID="${BUNDLE_ID:-com.onlytrusted.app}"

# Plan name + pricing per tier — just cosmetic strings surfaced in Settings → Billing.
case "$TIER" in
    free)        PLAN="Free";           PRICE="\$0";                   ACTIVE=false ;;
    premium)     PLAN="Premium";        PRICE="\$4.99 per year";       ACTIVE=true  ;;
    family)      PLAN="Family";         PRICE="\$9.99 per year";       ACTIVE=true  ;;
    highPremium) PLAN="Pro";            PRICE="\$19.99 per year";      ACTIVE=true  ;;
    elite)       PLAN="Elite";          PRICE="\$29.99 per year";      ACTIVE=true  ;;
esac

echo "Setting $BUNDLE_ID → tier=$TIER on simulator $SIM_UDID"
xcrun simctl terminate "$SIM_UDID" "$BUNDLE_ID" 2>/dev/null || true
xcrun simctl spawn "$SIM_UDID" defaults write "$BUNDLE_ID" subscription.v1.active -bool $ACTIVE
xcrun simctl spawn "$SIM_UDID" defaults write "$BUNDLE_ID" subscription.v1.tier -string "$TIER"
xcrun simctl spawn "$SIM_UDID" defaults write "$BUNDLE_ID" subscription.v1.plan_name -string "$PLAN"
xcrun simctl spawn "$SIM_UDID" defaults write "$BUNDLE_ID" subscription.v1.pricing -string "$PRICE"
xcrun simctl launch "$SIM_UDID" "$BUNDLE_ID" >/dev/null
echo "Done. App relaunched; Settings → Breach Watch / Private Aliases / etc. should now reflect $TIER."
