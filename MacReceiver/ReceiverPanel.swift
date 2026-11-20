import SwiftUI

// MARK: - Receiver panel (issues #82/#17)

/// The receiver-mode sections of the panel: live status, the advertised
/// name, and how-to copy. Lives inside the shared grouped Form.
struct ReceiverSections: View {
    @ObservedObject var controller: ReceiverController
    @AppStorage("showAnalytics") private var showAnalytics = false

    var body: some View {
        // The receiver exists only while receiver mode is on; observed in a
        // subview because nested ObservableObjects don't republish.
        if let receiver = controller.receiver {
            ReceiverStatusSection(receiver: receiver, controller: controller)
        }

        Section {
            ReceiverNameField { controller.setAdvertisedName($0) }
        } header: {
            Text("Name")
        } footer: {
            Text("How this Mac appears in the sender's device list.")
        }

        Section {
            Toggle("Performance overlay", isOn: $showAnalytics)
        } footer: {
            Text("FPS, frame timing, and latency graphs at the bottom of the video window while streaming.")
        }

        Section("How to connect") {
            Label("在安卓、Linux 或另一台 Mac 上打开无线副屏发送端。",
                  systemImage: "rectangle.connected.to.line.below")
            Label("让发送设备和这台 Mac 处在同一网络，在发送端选择这台 Mac 即可投屏。",
                  systemImage: "wifi")
            Label("收到画面后会自动打开窗口，可用绿色交通灯按钮进入全屏。",
                  systemImage: "arrow.up.left.and.arrow.down.right")
        }
        .font(.subheadline)
    }
}

/// Live state of the running receiver: connection, stream format, and any
/// compatibility signal from the connected Mac.
private struct ReceiverStatusSection: View {
    @ObservedObject var receiver: StreamReceiver
    let controller: ReceiverController

    var body: some View {
        Section("This Mac as a display") {
            HStack(alignment: .firstTextBaseline) {
                Circle()
                    .fill(receiver.connected ? Color.green : Color.orange)
                    .frame(width: 9, height: 9)
                VStack(alignment: .leading, spacing: 2) {
                    Text(receiver.connected ? "Connected" : "Waiting for a computer…")
                    // The port matters on a Mac (firewall prompts), so the
                    // idle caption names it instead of repeating the status.
                    Text(receiver.connected || receiver.status != "Ready to connect"
                         ? receiver.status : "Listening on port 9000")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Spacer()
                if controller.streaming {
                    Button("Show Window") { controller.showWindow() }
                        .controlSize(.small)
                        .help("Bring the video window back if you closed it — the stream keeps running either way.")
                }
            }
            if receiver.videoSize != .zero {
                // Not LabeledContent: that is macOS 13, the app runs on 12.
                HStack {
                    Text("Stream")
                    Spacer()
                    Text("\(Int(receiver.videoSize.width))×\(Int(receiver.videoSize.height)) @ \(receiver.fps) fps")
                        .foregroundColor(.secondary)
                }
            }
            if let message = peerMessage {
                Label(message, systemImage: "exclamationmark.triangle")
                    .font(.caption)
                    .foregroundStyle(.orange)
            }
        }
    }

    /// Compatibility copy from the sender (issue #132) — surfaced inline;
    /// the Mac app updates from its own release channel, so there is no
    /// blocking gate like on iOS.
    private var peerMessage: String? {
        switch receiver.peerSignal {
        case let .updateReceiver(message, _): return message
        case let .updateMac(message): return message
        case nil: return nil
        }
    }
}

/// The advertised-name editor — kept out of any high-frequency observed
/// object so streaming updates can't rebuild it mid-edit (same reasoning as
/// the iOS DeviceNameField).
private struct ReceiverNameField: View {
    @AppStorage("receiverName") private var name = Host.current().localizedName ?? "Mac"
    let onChange: (String) -> Void

    var body: some View {
        TextField("Name", text: $name)
            // The single-value onChange: the two-value form is macOS 14.
            .onChange(of: name, perform: onChange)
    }
}
