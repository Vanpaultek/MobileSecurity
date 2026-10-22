// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import UIKit
import MobileCoreServices
import UniformTypeIdentifiers
import Social

/// Smart Link Guard — iOS Share Extension entry point.
///
/// Invoked from the system share sheet whenever the user shares a URL or a snippet of
/// text that contains a URL (Messages, Safari, Mail, Notes, …). Pulls the URL out of
/// the extension context, runs it through the shared [SmartLinkGuardRules] on-device,
/// and renders a verdict screen with Open / Cancel actions. If the user picks Open and
/// the URL is parseable we hand control back to the host via `UIApplication.open(_:)`
/// through the extension's hosting UIResponder chain.
final class ShareViewController: UIViewController {

    private var detectedURL: String?

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemBackground
        extractURLFromContext()
    }

    // MARK: - URL extraction

    private func extractURLFromContext() {
        guard let items = extensionContext?.inputItems as? [NSExtensionItem] else {
            renderNoURL()
            return
        }
        for item in items {
            guard let attachments = item.attachments else { continue }
            for provider in attachments {
                if provider.hasItemConformingToTypeIdentifier(UTType.url.identifier) {
                    provider.loadItem(forTypeIdentifier: UTType.url.identifier) { [weak self] data, _ in
                        // Pull the string synchronously inside the completion so we don't
                        // ship a non-Sendable `Any` across isolation boundaries.
                        let payload: String? = {
                            if let url = data as? URL { return url.absoluteString }
                            if let str = data as? String { return str }
                            return nil
                        }()
                        DispatchQueue.main.async {
                            if let payload { self?.handle(payload) }
                            else { self?.renderNoURL() }
                        }
                    }
                    return
                } else if provider.hasItemConformingToTypeIdentifier(UTType.plainText.identifier) {
                    provider.loadItem(forTypeIdentifier: UTType.plainText.identifier) { [weak self] data, _ in
                        let text: String? = data as? String
                        let firstUrl: String? = text.flatMap { SmartLinkGuardRules.extractUrls($0).first }
                        DispatchQueue.main.async {
                            if let firstUrl { self?.handle(firstUrl) }
                            else { self?.renderNoURL() }
                        }
                    }
                    return
                }
            }
        }
        renderNoURL()
    }

    private func handle(_ raw: String) {
        detectedURL = raw
        let result = SmartLinkGuardRules.inspect(raw)
        renderVerdict(result)
    }

    // MARK: - Rendering

    private func renderNoURL() {
        let v = VerdictView(
            verdictLabel: "No link to check",
            verdictColor: .secondaryLabel,
            urlText: "The share sheet didn't include a URL or any text that looks like one.",
            reasons: ["Share a message, link, or snippet that contains a URL and try again."]
        )
        v.openAction = nil
        v.cancelAction = { [weak self] in self?.cancel() }
        attach(v)
    }

    private func renderVerdict(_ result: URLSafetyResult) {
        let v = VerdictView(
            verdictLabel: result.verdict.badgeText,
            verdictColor: colorFor(result.verdict),
            urlText: result.url,
            reasons: result.reasons
        )
        v.openAction = { [weak self] in self?.openInHost(result.url, blocked: result.verdict == .danger) }
        v.cancelAction = { [weak self] in self?.cancel() }
        attach(v)
    }

    private func attach(_ v: UIView) {
        view.subviews.forEach { $0.removeFromSuperview() }
        v.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(v)
        NSLayoutConstraint.activate([
            v.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor),
            v.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor),
            v.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            v.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor)
        ])
    }

    private func colorFor(_ v: URLVerdict) -> UIColor {
        switch v {
        case .safe: return .systemGreen
        case .caution: return .systemOrange
        case .danger: return .systemRed
        }
    }

    // MARK: - Actions

    private func openInHost(_ url: String, blocked: Bool) {
        guard !blocked else {
            cancel()
            return
        }
        let withScheme = url.contains("://") ? url : "https://\(url)"
        guard let parsed = URL(string: withScheme) else {
            cancel()
            return
        }
        // Share extensions can't call UIApplication.open directly; walk the responder
        // chain and send the "openURL:" selector dynamically. Private-API tricks here
        // are forbidden under App Review, so we settle for completing with the URL
        // placed on the pasteboard — the user can paste it in Safari if they still
        // want to open the link after seeing the Caution verdict.
        UIPasteboard.general.string = parsed.absoluteString
        extensionContext?.completeRequest(returningItems: nil, completionHandler: nil)
    }

    private func cancel() {
        extensionContext?.completeRequest(returningItems: nil, completionHandler: nil)
    }
}

/// Simple UIKit view that renders the verdict badge, URL preview, reason bullets, and
/// Open / Cancel buttons. Kept dependency-free so the extension bundle stays tiny.
private final class VerdictView: UIView {

    var openAction: (() -> Void)?
    var cancelAction: (() -> Void)?

    init(verdictLabel: String, verdictColor: UIColor, urlText: String, reasons: [String]) {
        super.init(frame: .zero)
        backgroundColor = .systemBackground

        let title = UILabel()
        title.text = "Smart Link Guard"
        title.font = .preferredFont(forTextStyle: .headline)
        title.textColor = .label

        let badge = UILabel()
        badge.text = "  \(verdictLabel.uppercased())  "
        badge.font = .boldSystemFont(ofSize: 13)
        badge.textColor = .white
        badge.backgroundColor = verdictColor
        badge.layer.cornerRadius = 10
        badge.layer.masksToBounds = true
        badge.textAlignment = .center

        let urlLabel = UILabel()
        urlLabel.text = urlText
        urlLabel.numberOfLines = 0
        urlLabel.font = .systemFont(ofSize: 14, weight: .semibold)
        urlLabel.textColor = .label

        let reasonStack = UIStackView()
        reasonStack.axis = .vertical
        reasonStack.spacing = 6
        for r in reasons {
            let bullet = UILabel()
            bullet.text = "• \(r)"
            bullet.numberOfLines = 0
            bullet.font = .systemFont(ofSize: 13)
            bullet.textColor = .secondaryLabel
            reasonStack.addArrangedSubview(bullet)
        }

        let openButton = UIButton(type: .system)
        openButton.setTitle(openAction == nil ? "Open" : "Open", for: .normal)
        openButton.setTitleColor(.white, for: .normal)
        openButton.backgroundColor = verdictColor
        openButton.layer.cornerRadius = 10
        openButton.titleLabel?.font = .boldSystemFont(ofSize: 15)
        openButton.contentEdgeInsets = UIEdgeInsets(top: 10, left: 20, bottom: 10, right: 20)
        openButton.addTarget(self, action: #selector(didTapOpen), for: .touchUpInside)

        let cancelButton = UIButton(type: .system)
        cancelButton.setTitle("Cancel", for: .normal)
        cancelButton.titleLabel?.font = .systemFont(ofSize: 15, weight: .semibold)
        cancelButton.contentEdgeInsets = UIEdgeInsets(top: 10, left: 20, bottom: 10, right: 20)
        cancelButton.addTarget(self, action: #selector(didTapCancel), for: .touchUpInside)

        let buttonRow = UIStackView(arrangedSubviews: [cancelButton, openButton])
        buttonRow.axis = .horizontal
        buttonRow.distribution = .fillEqually
        buttonRow.spacing = 12

        let column = UIStackView(arrangedSubviews: [title, badge, urlLabel, reasonStack, UIView(), buttonRow])
        column.axis = .vertical
        column.spacing = 16
        column.alignment = .leading
        column.distribution = .fill

        addSubview(column)
        column.translatesAutoresizingMaskIntoConstraints = false
        buttonRow.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            column.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 20),
            column.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -20),
            column.topAnchor.constraint(equalTo: topAnchor, constant: 24),
            column.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -24),
            buttonRow.leadingAnchor.constraint(equalTo: column.leadingAnchor),
            buttonRow.trailingAnchor.constraint(equalTo: column.trailingAnchor)
        ])
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    @objc private func didTapOpen() { openAction?() }
    @objc private func didTapCancel() { cancelAction?() }
}
