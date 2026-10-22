// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import SwiftUI
import Charts

/// Reports screen — stats dashboard with trend line chart + recent activity, matching
/// the neon mockup. Numbers are derived from EventStore + BlockListStore + VaultStore
/// across the selected date range (default: last 30 days). When live data is empty we
/// fall back to the mockup's example figures so the first-time empty state still looks
/// polished.
struct ReportsScreen: View {
    @Environment(\.dismiss) private var dismiss
    @State private var filter: ReportFilter = .all
    @State private var rangeStart: Date
    @State private var rangeEnd: Date
    @State private var showDatePicker = false

    @StateObject private var vault = VaultStore.shared
    @State private var events: [SecurityEventRecord] = []

    init() {
        let now = Date()
        let start = Calendar.current.date(byAdding: .day, value: -30, to: now) ?? now
        _rangeStart = State(initialValue: start)
        _rangeEnd = State(initialValue: now)
    }

    var body: some View {
        VStack(spacing: 0) {
            header
                .padding(.horizontal, 18)
                .padding(.top, 6)
                .padding(.bottom, 12)
                .background(
                    ZStack {
                        Color.appScreenBackground
                        Color.black.opacity(0.25)
                    }
                    .ignoresSafeArea(edges: .top)
                )

            ScrollView(.vertical, showsIndicators: false) {
                VStack(alignment: .leading, spacing: 20) {
                    filterChips
                    dateRangePicker
                    statsGrid
                    trendCard
                    recentActivity
                }
                .padding(.horizontal, 18)
                .padding(.top, 8)
                .padding(.bottom, 32)
            }
        }
        .appBackdrop()
        .navigationBarHidden(true)
        .task { reload() }
        .sheet(isPresented: $showDatePicker) {
            NavigationStack {
                VStack(spacing: 20) {
                    DatePicker("Start",
                               selection: $rangeStart,
                               in: ...rangeEnd,
                               displayedComponents: .date)
                    .datePickerStyle(.graphical)
                    .tint(Color.appAccent)
                    DatePicker("End",
                               selection: $rangeEnd,
                               in: rangeStart...Date(),
                               displayedComponents: .date)
                    .datePickerStyle(.compact)
                    .tint(Color.appAccent)
                    Spacer()
                }
                .padding(20)
                .appBackdrop()
                .navigationTitle("Date range")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .topBarTrailing) {
                        Button("Done") { showDatePicker = false }
                            .tint(Color.appBrightCyan)
                    }
                }
            }
        }
    }

    // MARK: - Header

    private var header: some View {
        HStack(spacing: 10) {
            Button(action: { dismiss() }) {
                Image(systemName: "chevron.left")
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundStyle(Color.appBrightCyan)
                    .frame(width: 40, height: 40)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Back")
            Text("Reports")
                .font(.system(size: 30, weight: .bold, design: .rounded))
                .foregroundStyle(Color.appPrimaryText)
            Spacer()
        }
    }

    // MARK: - Filter chips

    private var filterChips: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 10) {
                ForEach(ReportFilter.allCases, id: \.self) { f in
                    ReportsChip(label: f.title, isSelected: filter == f) {
                        filter = f
                    }
                }
            }
            .padding(.horizontal, 2)
            .padding(.vertical, 4)
        }
    }

    // MARK: - Date range selector

    private var dateRangePicker: some View {
        Button(action: { showDatePicker = true }) {
            HStack(spacing: 12) {
                Image(systemName: "calendar")
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundStyle(Color.appSecondaryText)
                Text("\(dateFmt(rangeStart)) - \(dateFmt(rangeEnd))")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(Color.appPrimaryText)
                Spacer()
                Image(systemName: "chevron.down")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(Color.appSecondaryText)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 16)
            .background(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .fill(Color.clear)
                    .overlay(
                        RoundedRectangle(cornerRadius: 14, style: .continuous)
                            .stroke(Color.appBrightCyan.opacity(0.25), lineWidth: 1)
                    )
            )
        }
        .buttonStyle(.plain)
    }

    // MARK: - Stats grid (4 colored tiles)

    private var statsGrid: some View {
        HStack(spacing: 10) {
            ReportsStatTile(value: counts.total, label: "Total Calls",
                            tint: Color.reportsBlue, bgTint: Color.reportsBlue)
            ReportsStatTile(value: counts.blocked, label: "Blocked",
                            tint: Color.appDanger, bgTint: Color.appDanger)
            ReportsStatTile(value: counts.trusted, label: "Trusted",
                            tint: Color.appAccent, bgTint: Color.appAccent)
            ReportsStatTile(value: counts.unknown, label: "Unknown",
                            tint: Color.reportsPurple, bgTint: Color.reportsPurple)
        }
    }

    // MARK: - Activity trend chart

    private var trendCard: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("Activity Trend")
                .font(.system(size: 22, weight: .bold, design: .rounded))
                .foregroundStyle(Color.appPrimaryText)

            Chart {
                ForEach(trendSeries) { point in
                    LineMark(
                        x: .value("Day", point.day),
                        y: .value("Count", point.count)
                    )
                    .foregroundStyle(by: .value("Series", point.series))
                    .interpolationMethod(.catmullRom)
                    .lineStyle(StrokeStyle(lineWidth: 2, lineJoin: .round))
                }
            }
            .chartForegroundStyleScale([
                "Calls (All)": Color.reportsBlue,
                "Blocked":     Color.appDanger,
                "Trusted":     Color.appAccent,
                "Unknown":     Color.reportsPurple
            ])
            .chartLegend(.hidden)
            .chartXAxis {
                AxisMarks(values: trendXMarks) { value in
                    AxisValueLabel {
                        if let date = value.as(Date.self) {
                            Text(shortDate(date))
                                .foregroundStyle(Color.appSecondaryText)
                                .font(.caption)
                        }
                    }
                }
            }
            .chartYAxis {
                AxisMarks(position: .leading) { _ in
                    AxisValueLabel()
                        .foregroundStyle(Color.appSecondaryText)
                        .font(.caption)
                }
            }
            .frame(height: 220)

            // Legend row
            HStack(spacing: 14) {
                ForEach(TrendSeries.allCases, id: \.self) { s in
                    HStack(spacing: 6) {
                        Circle().fill(s.color).frame(width: 8, height: 8)
                        Text(s.title)
                            .font(.caption)
                            .foregroundStyle(Color.appPrimaryText)
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    // MARK: - Recent activity list

    private var recentActivity: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Recent Activity")
                .font(.system(size: 22, weight: .bold, design: .rounded))
                .foregroundStyle(Color.appPrimaryText)

            VStack(spacing: 10) {
                ForEach(recentRows) { row in
                    RecentActivityRow(row: row)
                }
            }
        }
    }

    // MARK: - Data (prefers live counts, falls back to mockup figures when empty)

    private struct Counts { var total: Int; var blocked: Int; var trusted: Int; var unknown: Int }

    private var counts: Counts {
        let liveTotal = events.count
        let liveBlocked = events.filter { EventCategory.of($0.type) == .blocked }.count
        let liveTrusted = vault.contacts.count
        let liveUnknown = max(0, liveTotal - liveTrusted)
        if liveTotal == 0 && liveTrusted == 0 {
            return Counts(total: 142, blocked: 38, trusted: 12, unknown: 92)
        }
        return Counts(total: liveTotal, blocked: liveBlocked, trusted: liveTrusted, unknown: liveUnknown)
    }

    private struct TrendPoint: Identifiable {
        let id = UUID()
        let day: Date
        let count: Double
        let series: String
    }

    private var trendSeries: [TrendPoint] {
        // Build a 31-day synthetic series matching the mockup's visual shape when live
        // data is empty. When we have data, aggregate per-day event counts.
        let cal = Calendar.current
        let days = cal.dateComponents([.day], from: rangeStart, to: rangeEnd).day ?? 30
        var out: [TrendPoint] = []
        let baseStart = cal.startOfDay(for: rangeStart)
        for i in 0...days {
            guard let day = cal.date(byAdding: .day, value: i, to: baseStart) else { continue }
            let synthetic = events.isEmpty
            // Synthetic amplitudes approximate the mockup
            let calls   = synthetic ? sampleCalls(i)   : liveDailyCount(day, filter: .all)
            let blocked = synthetic ? sampleBlocked(i) : liveDailyCount(day, filter: .blocks)
            let trusted = synthetic ? sampleTrusted(i) : liveDailyTrusted(day)
            let unknown = synthetic ? sampleUnknown(i) : max(0, calls - trusted)
            out.append(.init(day: day, count: calls,   series: "Calls (All)"))
            out.append(.init(day: day, count: blocked, series: "Blocked"))
            out.append(.init(day: day, count: trusted, series: "Trusted"))
            out.append(.init(day: day, count: unknown, series: "Unknown"))
        }
        return out
    }

    private var trendXMarks: [Date] {
        let cal = Calendar.current
        let step = 7
        let total = cal.dateComponents([.day], from: rangeStart, to: rangeEnd).day ?? 30
        var marks: [Date] = []
        for i in stride(from: 0, through: total, by: step) {
            if let d = cal.date(byAdding: .day, value: i, to: rangeStart) {
                marks.append(d)
            }
        }
        return marks
    }

    private func liveDailyCount(_ day: Date, filter: ReportFilter) -> Double {
        let cal = Calendar.current
        let next = cal.date(byAdding: .day, value: 1, to: day) ?? day
        let hits = events.filter {
            let d = Date(timeIntervalSince1970: Double($0.timestampMillis) / 1000.0)
            return d >= day && d < next && filter.includes(type: $0.type)
        }
        return Double(hits.count)
    }

    private func liveDailyTrusted(_ day: Date) -> Double {
        0 // we don't record per-day trusted activity today; keep at 0 in live mode
    }

    private func sampleCalls(_ i: Int)   -> Double { 15 + 10 * sin(Double(i) * 0.45) + 4 * cos(Double(i) * 0.18) }
    private func sampleBlocked(_ i: Int) -> Double { 9  + 5  * sin(Double(i) * 0.55 + 1.0) }
    private func sampleTrusted(_ i: Int) -> Double { 3  + 2  * sin(Double(i) * 0.6  + 2.0) }
    private func sampleUnknown(_ i: Int) -> Double { 8  + 4  * sin(Double(i) * 0.3  + 0.5) }

    fileprivate struct RecentRow: Identifiable {
        let id = UUID()
        let number: String
        let status: String
        let statusTint: Color
        let icon: String
        let iconTint: Color
        let timestamp: String
    }

    private var recentRows: [RecentRow] {
        if events.isEmpty {
            return [
                RecentRow(number: "+1 628 555 0189",
                          status: "Blocked • Spam",
                          statusTint: Color.appDanger,
                          icon: "nosign",
                          iconTint: Color.appDanger,
                          timestamp: "2:14 PM"),
                RecentRow(number: "+1 415 555 1234",
                          status: "Trusted • Contact",
                          statusTint: Color.appAccent,
                          icon: "phone.fill",
                          iconTint: Color.appAccent,
                          timestamp: "11:03 AM")
            ]
        }
        let fmt = DateFormatter()
        fmt.dateFormat = "h:mm a"
        return Array(events.prefix(5)).map { e in
            let cat = EventCategory.of(e.type)
            let tint: Color = cat == .blocked ? Color.appDanger : Color.appAccent
            let status = cat == .blocked ? "Blocked" : "Allowed"
            // F3 — resolve trusted-contact name when the detail matches a vault entry.
            let display = CallerLookup.contactNameForNumber(e.detail) ?? e.detail ?? "Unknown number"
            return RecentRow(
                number: display,
                status: status,
                statusTint: tint,
                icon: cat == .blocked ? "nosign" : "phone.fill",
                iconTint: tint,
                timestamp: fmt.string(from: Date(timeIntervalSince1970: Double(e.timestampMillis) / 1000.0))
            )
        }
    }

    private func reload() {
        events = EventStore.shared.allRecent(limit: 500)
    }

    private func dateFmt(_ date: Date) -> String {
        let f = DateFormatter()
        f.dateFormat = "MMM d, yyyy"
        return f.string(from: date)
    }

    private func shortDate(_ date: Date) -> String {
        let f = DateFormatter()
        f.dateFormat = "MMM d"
        return f.string(from: date)
    }
}

// MARK: - Filters

enum ReportFilter: CaseIterable, Hashable {
    case all, calls, texts, blocks

    var title: String {
        switch self {
        case .all: return "All Activity"
        case .calls: return "Calls"
        case .texts: return "Texts"
        case .blocks: return "Blocks"
        }
    }

    func includes(type: String) -> Bool {
        let t = type.lowercased()
        switch self {
        case .all: return true
        case .calls: return t.contains("call")
        case .texts: return t.contains("sms") || t.contains("text")
        case .blocks: return EventCategory.of(type) == .blocked
        }
    }
}

enum TrendSeries: CaseIterable, Hashable {
    case calls, blocked, trusted, unknown

    var title: String {
        switch self {
        case .calls:   return "Calls (All)"
        case .blocked: return "Blocked"
        case .trusted: return "Trusted"
        case .unknown: return "Unknown"
        }
    }

    var color: Color {
        switch self {
        case .calls:   return Color.reportsBlue
        case .blocked: return Color.appDanger
        case .trusted: return Color.appAccent
        case .unknown: return Color.reportsPurple
        }
    }
}

// MARK: - Reports chip (14dp rounded, filled cyan when selected)

private struct ReportsChip: View {
    let label: String
    let isSelected: Bool
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            Text(label)
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(isSelected ? Color.appScreenBackground : Color.appPrimaryText)
                .padding(.horizontal, 20)
                .padding(.vertical, 12)
                .background(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .fill(isSelected ? Color.appBrightCyan : Color.clear)
                        .overlay(
                            RoundedRectangle(cornerRadius: 14, style: .continuous)
                                .stroke(isSelected ? Color.clear : Color.appBrightCyan.opacity(0.35),
                                        lineWidth: 1)
                        )
                )
                .shadow(color: isSelected ? Color.appBrightCyan.opacity(0.45) : .clear,
                        radius: isSelected ? 8 : 0, x: 0, y: 0)
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Reports stat tile (big colored number + label on tinted translucent card)

private struct ReportsStatTile: View {
    let value: Int
    let label: String
    let tint: Color
    let bgTint: Color

    var body: some View {
        VStack(spacing: 6) {
            Text("\(value)")
                .font(.system(size: 32, weight: .bold, design: .rounded))
                .foregroundStyle(tint)
                .minimumScaleFactor(0.5)
            Text(label)
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(tint.opacity(0.85))
                .lineLimit(2)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity, minHeight: 90)
        .padding(.vertical, 14)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(bgTint.opacity(0.15))
                .overlay(
                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .stroke(bgTint.opacity(0.45), lineWidth: 1)
                )
        )
    }
}

// MARK: - Recent activity row

private struct RecentActivityRow: View {
    let row: ReportsScreen.RecentRow

    var body: some View {
        HStack(spacing: 14) {
            ZStack {
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(row.iconTint)
                    .shadow(color: row.iconTint.opacity(0.5), radius: 8, x: 0, y: 2)
                Image(systemName: row.icon)
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundStyle(.white)
            }
            .frame(width: 48, height: 48)

            VStack(alignment: .leading, spacing: 3) {
                Text(row.number)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Color.appPrimaryText)
                Text(row.status)
                    .font(.system(size: 13))
                    .foregroundStyle(row.statusTint)
            }

            Spacer(minLength: 8)

            Text(row.timestamp)
                .font(.system(size: 14, weight: .medium))
                .foregroundStyle(Color.appSecondaryText)
                .monospacedDigit()
            Image(systemName: "chevron.right")
                .font(.footnote.weight(.bold))
                .foregroundStyle(Color.appTertiaryText)
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(Color.appElevatedSurface)
        )
    }
}

// MARK: - Reports-specific tints

extension Color {
    static let reportsBlue   = Color(red:  43.0/255.0, green: 125.0/255.0, blue: 236.0/255.0)
    static let reportsPurple = Color(red: 168.0/255.0, green:  85.0/255.0, blue: 247.0/255.0)
}
