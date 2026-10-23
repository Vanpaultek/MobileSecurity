# Offline Threat Intel bundle (Elite)

Zero-network, bundle-only threat-intel feeds. Every file in this directory is loaded on
app launch and consulted from Elite feature flows (Smart Link Guard, Wangiri auto-block,
and the Offline Threat Intel screen). No request ever leaves the device.

| File | Purpose | Shipping size target | Shipping source |
| --- | --- | --- | --- |
| `scam_numbers.v1.txt` | Known scam / premium-rate phone numbers (E.164). | ~5 MB (~500k lines) | FTC Consumer Sentinel + FCC do-not-call violator feed + CEPT premium-rate ranges. |
| `phishing_urls.v1.txt` | Phishing URL / host patterns. Hashed into a Bloom filter at load. | ~1 MB (50k patterns) | APWG public URL feed (daily refresh). |
| `breach_hashes.v1.txt` | SHA-256 prefixes of breached email addresses. Hashed into a Bloom filter at load. | ~15 MB (~100M entries, 10-hex-char prefixes) | HaveIBeenPwned public pwned-emails dataset. |
| `pegasus_iocs.v1.json` | Pegasus / Candiru / Predator IoCs — filenames, plist keys, dylib SHA-256, process names, domains. | ~50 KB | Citizen Lab reports + Amnesty Tech MVT indicator sets. |

The files in this folder are the compact placeholders bundled with the current
build so the full pipeline works end-to-end. They will be replaced by the full feeds
during the release build via `tools/refresh_threatintel.sh` (not yet wired into CI).

## Format conventions

Every file is UTF-8. Lines beginning with `#` are comments. Empty lines are ignored.
Numeric values are E.164 (digits after `+`, no spaces). Hashes are lowercase hex.

## Refresh cadence

Daily rebuilds of this bundle will land via normal app updates. The runtime never
reaches out to any server for these feeds — there is no live-fetch path, by design.
