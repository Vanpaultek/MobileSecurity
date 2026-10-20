// SPDX-FileCopyrightText: 2026 57Million LLC
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation

/// Access point to the App Group container shared by the main app, CallDirectoryExtension,
/// and MessageFilterExtension. Only files living inside this container are visible to the
/// extensions — regular Application Support won't do.
enum SharedContainer {
    static let appGroupIdentifier = "group.com.onlytrusted.app.shared"

    enum ContainerError: Error { case missingAppGroup }

    static func containerURL() throws -> URL {
        guard let url = FileManager.default.containerURL(
            forSecurityApplicationGroupIdentifier: appGroupIdentifier
        ) else {
            throw ContainerError.missingAppGroup
        }
        return url
    }

    /// Nested helper that returns a stable file URL inside the shared container, creating any
    /// parent directories on the way.
    static func fileURL(named name: String, subpath: String? = nil) throws -> URL {
        let root = try containerURL()
        let folder = subpath.map { root.appendingPathComponent($0, isDirectory: true) } ?? root
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        return folder.appendingPathComponent(name)
    }
}
