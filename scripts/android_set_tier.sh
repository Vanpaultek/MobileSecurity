#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 VanPaulTek Inc
# SPDX-License-Identifier: AGPL-3.0-or-later
#
# Flip the connected Android device's OnlyTrusted subscription tier for testing.
# Equivalent of scripts/ios_set_tier.sh — writes the plain SharedPreferences file via
# `adb shell run-as com.onlytrusted.app` and relaunches the app so SubscriptionState's
# next prefs() read picks up the new tier.
#
# Only works on debug builds (release builds disable run-as). The subscription prefs
# are NOT inside EncryptedSharedPreferences — only sensitive feature state is (vault
# key material, breach-watch email, aliases). The tier is deliberately plain so this
# backdoor can exist for testing without compromising user data.
#
# Usage:
#   scripts/android_set_tier.sh <tier>
#     tier = free | premium | family | highPremium | elite
#
# Optional env:
#   SERIAL     — target device serial (adb -s). Default: auto-detect single device.
#   PACKAGE    — app package id. Default: com.onlytrusted.app
#   ACTIVITY   — launch activity. Default: com.onlytrusted.app/.MainActivity

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

PACKAGE="${PACKAGE:-com.onlytrusted.app}"
ACTIVITY="${ACTIVITY:-$PACKAGE/.MainActivity}"

# Resolve serial — if SERIAL unset, require exactly one device.
if [[ -z "${SERIAL:-}" ]]; then
    DEVICE_COUNT=$(adb devices | grep -c -E "device$" || true)
    if [[ "$DEVICE_COUNT" -ne 1 ]]; then
        echo "Multiple or no adb devices. Set SERIAL=<serial> or disconnect extras." >&2
        adb devices
        exit 1
    fi
fi
ADB_ARGS=()
[[ -n "${SERIAL:-}" ]] && ADB_ARGS+=(-s "$SERIAL")

# Android SubscriptionState stores the tier enum NAME in uppercase (e.g. "PREMIUM",
# "HIGH_PREMIUM"). Map the lowercase CLI arg to the on-disk enum name.
case "$TIER" in
    free)        ENUM="FREE";         ACTIVE=false; PLAN="Free";   PRICE="\$0" ;;
    premium)     ENUM="PREMIUM";      ACTIVE=true;  PLAN="Premium";PRICE="\$4.99 per year" ;;
    family)      ENUM="FAMILY";       ACTIVE=true;  PLAN="Family"; PRICE="\$9.99 per year" ;;
    highPremium) ENUM="HIGH_PREMIUM"; ACTIVE=true;  PLAN="Pro";    PRICE="\$19.99 per year" ;;
    elite)       ENUM="ELITE";        ACTIVE=true;  PLAN="Elite";  PRICE="\$29.99 per year" ;;
esac

# ~335 days from now, in milliseconds. The Billing screen reads this as the next renewal.
NEXT_MS=$(( ($(date +%s) + 335 * 86400) * 1000 ))

echo "Setting $PACKAGE → tier=$TIER (enum $ENUM) on device${SERIAL:+ $SERIAL}..."

adb ${ADB_ARGS[@]+"${ADB_ARGS[@]}"} shell am force-stop "$PACKAGE"

# Build the XML and overwrite the prefs file. `tee` inside run-as avoids the common
# `sh: can't create` permission trap that plain `>` hits when the shell redirect runs
# as the adb user rather than the app user.
XML="<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <boolean name=\"subscription.v1.active\" value=\"$ACTIVE\" />
    <string name=\"subscription.v1.pricing\">$PRICE</string>
    <string name=\"subscription.v1.plan_name\">$PLAN</string>
    <string name=\"subscription.v1.tier\">$ENUM</string>
    <long name=\"subscription.v1.next_billing\" value=\"$NEXT_MS\" />
</map>"

adb ${ADB_ARGS[@]+"${ADB_ARGS[@]}"} shell "run-as $PACKAGE mkdir -p shared_prefs"
echo "$XML" | adb ${ADB_ARGS[@]+"${ADB_ARGS[@]}"} shell "run-as $PACKAGE tee shared_prefs/subscription_prefs_v1.xml >/dev/null"

adb ${ADB_ARGS[@]+"${ADB_ARGS[@]}"} shell am start -n "$ACTIVITY" >/dev/null
echo "Done. App relaunched; Settings → Breach Watch / Private Aliases / etc. should now reflect $TIER."
