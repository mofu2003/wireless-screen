import Foundation

/// One source of truth for names and public links used by the apps.
///
/// Build-system identities live in `project.yml` and Gradle; these values are
/// the runtime copy that user-facing code and diagnostics need.
enum ProductIdentity {
    static let name = "无线副屏"
    static let receiverName = "无线副屏接收端"
    static let repositoryURL = "https://github.com/mofu2003/wireless-screen"
    static let latestReleaseURL = "https://github.com/mofu2003/wireless-screen/releases/latest"
    static let logFolderName = "WirelessScreen"
    static let logFileBaseName = "wirelessscreen"
    static let diagnosticFilePrefix = "WirelessScreen"
}
