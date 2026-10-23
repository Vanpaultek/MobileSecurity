// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import SwiftUI
import PDFKit
import UIKit

/// Premium-only weekly digest. Generates a one-page PDF on-device summarising the last
/// 7 days of app activity, then opens the system share sheet so the user can save or
/// send it. Zero-network: counts come from the local EventStore; no part of the digest
/// is uploaded anywhere.
struct WeeklyDigestScreen: View {

    @StateObject private var sub = SubscriptionState.shared
    @State private var exportURL: URL?
    @State private var showingShare = false
    @State private var toast: String?

    private var counts: WeeklyPDFDigestCounts { WeeklyPDFDigestGenerator.compute() }

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                heroCard
                summaryCard
                if sub.hasPremium {
                    generateCard
                } else {
                    upsellCard
                }
            }
            .padding(20)
        }
        .appBackdrop()
        .navigationTitle("Weekly Digest")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $showingShare) {
            if let exportURL { ShareSheet(activityItems: [exportURL]) }
        }
        .overlay(alignment: .bottom) {
            if let toast {
                Text(toast)
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(Color.appScreenBackground)
                    .padding(.horizontal, 14).padding(.vertical, 10)
                    .background(Color.appAccent, in: Capsule())
                    .padding(.bottom, 24)
                    .transition(.opacity.combined(with: .move(edge: .bottom)))
            }
        }
    }

    private var heroCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 10) {
                CardHeader(icon: "doc.text.image", title: "Weekly Digest", tint: Color.appAccent)
                Text("A one-page PDF summarising the last 7 days: blocked calls, filtered SMS, mic/cam alerts, breach checks. Generated on your device and shared via the system share sheet. Nothing is uploaded.")
                    .font(.footnote)
                    .foregroundStyle(Color.appSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    private var summaryCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 10) {
                CardHeader(icon: "chart.bar", title: "This week at a glance", tint: Color.appAccent)
                row(label: "Mic/camera alerts", value: counts.sensorAlerts)
                row(label: "Blocked calls", value: counts.blockedCalls)
                row(label: "Filtered SMS", value: counts.filteredSms)
                row(label: "Breach checks run", value: counts.breachChecks)
                row(label: "Panic / lockdown events", value: counts.panicEvents)
                row(label: "Total audit events", value: counts.total)
            }
        }
    }

    private func row(label: String, value: Int) -> some View {
        HStack {
            Text(label).font(.subheadline).foregroundStyle(Color.appSecondaryText)
            Spacer()
            Text("\(value)").font(.subheadline.weight(.bold)).foregroundStyle(Color.appPrimaryText)
        }
    }

    private var generateCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 10) {
                Text("Export")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(Color.appSecondaryText)
                Button {
                    generate()
                } label: {
                    HStack {
                        Image(systemName: "square.and.arrow.up")
                        Text("Generate & share PDF")
                            .font(.body.weight(.semibold))
                        Spacer()
                    }
                    .padding(.horizontal, 14).padding(.vertical, 12)
                    .background(Color.appAccent, in: RoundedRectangle(cornerRadius: 12))
                    .foregroundStyle(Color.appScreenBackground)
                }
                .buttonStyle(.plain)
            }
        }
    }

    private var upsellCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 10) {
                CardHeader(icon: "crown.fill", title: "Premium weekly digest", tint: Color.subGold)
                Text("Premium ($4.99/yr) generates a one-page weekly PDF digest you can save or share. All counts come from your on-device audit log; nothing is uploaded.")
                    .font(.footnote)
                    .foregroundStyle(Color.appSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    private func generate() {
        let data = WeeklyPDFDigestGenerator.pdfData(counts: counts)
        let fname = "onlytrusted_weekly_\(Int(Date().timeIntervalSince1970)).pdf"
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(fname)
        do {
            try data.write(to: url, options: [.atomic])
            exportURL = url
            showingShare = true
        } catch {
            withAnimation { toast = "Export failed" }
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.6) {
                withAnimation { toast = nil }
            }
        }
    }
}

// MARK: - Digest logic

struct WeeklyPDFDigestCounts {
    var sensorAlerts: Int = 0
    var blockedCalls: Int = 0
    var filteredSms: Int = 0
    var breachChecks: Int = 0
    var panicEvents: Int = 0
    var total: Int = 0
}

enum WeeklyPDFDigestGenerator {

    /// Walks EventStore back 7 days and buckets events by type.
    static func compute(now: Date = Date()) -> WeeklyPDFDigestCounts {
        let cutoffMillis = Int64(now.addingTimeInterval(-7 * 86400).timeIntervalSince1970 * 1000)
        var out = WeeklyPDFDigestCounts()
        for record in EventStore.shared.allRecent(limit: 5000) {
            guard record.timestampMillis >= cutoffMillis else { continue }
            out.total += 1
            let t = record.type.uppercased()
            if t.contains("MIC") || t.contains("CAMERA") || t.contains("SENSOR") {
                out.sensorAlerts += 1
            }
            if t.contains("CALL") && (t.contains("BLOCK") || t.contains("DROP")) {
                out.blockedCalls += 1
            }
            if t.contains("SMS") && (t.contains("FILTER") || t.contains("DROP") || t.contains("SPAM")) {
                out.filteredSms += 1
            }
            if t.contains("BREACH") { out.breachChecks += 1 }
            if t.contains("PANIC") || t.contains("LOCKDOWN") { out.panicEvents += 1 }
        }
        return out
    }

    /// Build a single-page letter-size PDF with the digest counts.
    static func pdfData(counts: WeeklyPDFDigestCounts, now: Date = Date()) -> Data {
        let format = UIGraphicsPDFRendererFormat()
        format.documentInfo = [
            kCGPDFContextCreator as String: "OnlyTrusted",
            kCGPDFContextAuthor as String: "VanPaulTek Inc"
        ]
        // US Letter at 72dpi: 612 × 792 pts.
        let page = CGRect(x: 0, y: 0, width: 612, height: 792)
        let renderer = UIGraphicsPDFRenderer(bounds: page, format: format)
        let fmt = DateFormatter()
        fmt.dateStyle = .medium
        return renderer.pdfData { ctx in
            ctx.beginPage()
            let titleAttrs: [NSAttributedString.Key: Any] = [
                .font: UIFont.boldSystemFont(ofSize: 28),
                .foregroundColor: UIColor.black
            ]
            let subAttrs: [NSAttributedString.Key: Any] = [
                .font: UIFont.systemFont(ofSize: 12),
                .foregroundColor: UIColor.darkGray
            ]
            let bodyAttrs: [NSAttributedString.Key: Any] = [
                .font: UIFont.systemFont(ofSize: 14),
                .foregroundColor: UIColor.black
            ]
            let valueAttrs: [NSAttributedString.Key: Any] = [
                .font: UIFont.boldSystemFont(ofSize: 14),
                .foregroundColor: UIColor.black
            ]
            let footerAttrs: [NSAttributedString.Key: Any] = [
                .font: UIFont.systemFont(ofSize: 10),
                .foregroundColor: UIColor.gray
            ]

            "OnlyTrusted — Weekly Digest".draw(at: CGPoint(x: 50, y: 50), withAttributes: titleAttrs)
            let since = fmt.string(from: now.addingTimeInterval(-7 * 86400))
            let until = fmt.string(from: now)
            "Period: \(since) → \(until). Generated on this device, no network.".draw(
                at: CGPoint(x: 50, y: 90), withAttributes: subAttrs
            )

            var y: CGFloat = 140
            for (label, value) in [
                ("Mic/camera alerts", counts.sensorAlerts),
                ("Blocked calls", counts.blockedCalls),
                ("Filtered SMS", counts.filteredSms),
                ("Breach checks run", counts.breachChecks),
                ("Panic / lockdown events", counts.panicEvents),
                ("Total audit events", counts.total)
            ] {
                label.draw(at: CGPoint(x: 60, y: y), withAttributes: bodyAttrs)
                "\(value)".draw(at: CGPoint(x: 500, y: y), withAttributes: valueAttrs)
                y += 28
            }

            let footer = "Everything on this page is derived from your local, signed audit log. OnlyTrusted does not operate a backend; nothing on this page has been or will be transmitted off this device unless you tap Share and send it yourself."
            footer.draw(
                in: CGRect(x: 50, y: 720, width: 512, height: 60),
                withAttributes: footerAttrs
            )
        }
    }
}
