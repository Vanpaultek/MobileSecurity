// SPDX-FileCopyrightText: 2026 57Million LLC
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation

enum LegalContent {
    static let lastUpdated = "2026-10-20"

    struct Section: Identifiable {
        let title: String
        let body: String
        var id: String { title }
    }

    static let sections: [Section] = [
        Section(title: "Audience", body: audience),
        Section(title: "Non-interference", body: nonInterference),
        Section(title: "Call encryption", body: callEncryption),
        Section(title: "Privacy policy", body: privacyPolicy),
        Section(title: "Terms of use", body: termsOfUse),
        Section(title: "Honest claims", body: honestClaims),
        Section(title: "Data principles", body: dataPrinciples),
        Section(title: "Accessibility", body: accessibility)
    ]

    static let audience = """
    MobileSecurity is a serious privacy tool for the phone owner and their immediate family. It is not an entertainment app, a novelty "spy" tool, or a product you install on someone else's device without their consent.

    By using it you are confirming:
    • You are at least 18 (or your local legal age of digital consent, whichever is higher).
    • You own or administer this phone.
    • You will only block calls and intercept SMS on devices you have the right to manage.
    • You understand that installing this app on someone else's phone without their knowledge may be illegal in your country (stalkerware laws, wiretap laws, GDPR and similar regulations around the world).

    If any of those is not true, uninstall now.

    Family data

    Any family phone number, address, email or note you add to the Trusted vault is:
    • Stored only on this device.
    • Protected by Keychain-backed AES-256-GCM encryption via Apple CryptoKit.
    • Visible in the UI only after a Face ID / Touch ID / passcode unlock.
    • Deletable individually or wiped entirely from Settings → Data.

    We never upload family data. The app has no networking code and no entitlements that would permit it.
    """

    static let nonInterference = """
    We built MobileSecurity to protect the owner's phone. That means we deliberately refuse to do things that would turn it into the thing it is defending against.

    1. We never change system settings for you. We only observe, and we deep-link into Settings where the owner flips the switch.
    2. We never scan or probe anyone else's device, network, or account.
    3. We never "hack back." If an attacker tries to compromise your phone, we observe, record, and alert. We do not attempt to identify them beyond what the phone number already shows locally.
    4. We never phone home. There is no server, no cloud, no analytics, no crash reporter that transmits data. The app has no App Transport Security exception entries for any domain because it never contacts any.
    5. We never run on someone else's phone without their knowledge. Installing MobileSecurity on another adult's phone without their consent is stalkerware regardless of your relationship to them and is illegal in most countries.
    """

    static let callEncryption = """
    A lot of apps claim to "encrypt your calls." Most of the time that claim is sloppy or misleading. Here is the honest version.

    Cellular voice/video (your carrier)
    When you place a normal call from your phone's dialer, the audio runs through your mobile carrier (GSM / VoLTE / VoNR / 5G). That channel is encrypted by the carrier, between your phone and the cell tower, with keys the carrier owns. No third-party app can add end-to-end encryption to that path. Any app that says it can is lying.

    MobileSecurity does not touch cellular calls. We will screen them (Phase 2 via Call Directory Extension), we will flag spam, but we do not and cannot encrypt them.

    Voice / video calls over the internet
    For end-to-end encrypted calls use Signal, WhatsApp, FaceTime, or iMessage audio/video. Use those for the call. Use MobileSecurity on the same phone for everything around the call.

    What MobileSecurity does protect
    • Your family vault: Keychain + AES-256-GCM via CryptoKit.
    • Your audit log (Phase 2): SHA-256 hash chain.
    """

    static let privacyPolicy = """
    Last updated: \(lastUpdated)

    MobileSecurity is designed around a single principle: your data stays on your phone.

    What we collect
    Nothing. The app has no server. It does not use the internet. No account is required.

    Inside your device, the app records your trusted-contact vault and your in-app settings. All of this is stored encrypted on this device only (Keychain-protected AES-256-GCM keys via Apple CryptoKit). It is never transmitted off the phone by this app.

    Third parties
    We do not share, sell, rent, trade, or disclose your information to any third party. We cannot — the app has no network capability.

    Permissions and how they are used
    • Face ID / Touch ID / passcode: to unlock the vault and (optionally) the app itself.
    • Contacts, Location, Microphone, Camera: not requested in Phase 1.

    Your rights
    You have the right to access, correct, export, restrict, and delete your data at any time. These rights apply regardless of where you live. We honour, at minimum, protections required by the EU GDPR, UK GDPR, California CCPA/CPRA, Virginia VCDPA, Colorado CPA, Connecticut CTDPA, Utah UCPA, Brazil LGPD, Canada PIPEDA, Australia Privacy Act, Japan APPI, Singapore PDPA, South Korea PIPA, India DPDP Act 2023, and South Africa POPIA.

    Children
    MobileSecurity is not directed at children under 13.

    Contact
    reddy@vanpaultek.com (57Million LLC).
    """

    static let termsOfUse = """
    Last updated: \(lastUpdated)

    What this app is
    A privacy and anti-nuisance tool for iOS. In Phase 1 it provides an encrypted family contact vault and biometric app lock. Phase 2 adds a Call Directory Extension and SMS filter.

    What this app is not
    • It is not a replacement for the system security features built into iOS.
    • It cannot monitor microphone or camera use by other apps on iOS.
    • It cannot stop the person on the other end of a video call from recording their own screen.
    • It cannot draw an overlay watermark on top of other apps (iOS does not allow this).
    • It cannot make your phone untrackable by your carrier.

    If any marketing you see for this app contradicts these points, the marketing is wrong — these terms govern.

    Our responsibilities
    The app will not transmit your data off the device. We will clearly disclose every data-handling practice in the Privacy Policy inside the app.

    Warranty and limits
    The app is provided "as is." To the maximum extent allowed by applicable law, our liability is limited to the amount you paid for the app.

    Contact
    reddy@vanpaultek.com (57Million LLC).
    """

    static let honestClaims = """
    A short list of what this app can and cannot do on iOS.

    It can
    • Store family contacts, numbers, addresses, and notes in an encrypted vault.
    • Protect the vault behind Face ID / Touch ID / passcode.
    • Keep every piece of data on your device only.
    • Panic-lock itself at any time.

    It cannot
    • Monitor other apps' microphone or camera use. iOS reserves this for its own status bar indicator.
    • Draw a watermark overlay on top of other apps. iOS does not allow it.
    • Enumerate other apps' permissions. iOS does not expose this.
    • Encrypt cellular voice/video calls. Nobody but your carrier can.

    If anything inside this app or in its listing suggests otherwise, treat this page as the source of truth.
    """

    static let dataPrinciples = """
    1. Collect nothing we don't need.
    2. Keep it on your device. No cloud, no server, no analytics, no ads. The app has no networking entitlements.
    3. Encrypt at rest with Keychain-held keys.
    4. Make deletion one tap.
    5. Make export one tap (Phase 2).
    6. Keep policies in the binary. The Privacy Policy and these principles ship inside the app with a visible "last updated" date.
    7. Open about limits.
    8. Minimum age 13 (or local minimum, whichever is higher).
    9. No tracking.
    10. No dark patterns.
    """

    static let accessibility = """
    We aim to meet WCAG 2.1 AA for colour contrast, text scaling, and VoiceOver labels, and to respect iOS's Dynamic Type, Bold Text, Increased Contrast, and Reduce Motion settings.

    If you find a screen that is hard to use with VoiceOver, Switch Control, or a large font size, email reddy@vanpaultek.com and we will treat it as a priority fix.
    """
}
