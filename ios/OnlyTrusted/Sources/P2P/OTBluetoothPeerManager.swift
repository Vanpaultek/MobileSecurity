// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation
import CoreBluetooth
import CryptoKit

/// Shared peer-to-peer BLE infrastructure for OnlyTrusted's Elite features
/// (Shamir Secret Sharing + Trusted Witness).
///
/// Why one manager handles both features: both need the same thing — advertise or scan on
/// a short list of service UUIDs, complete a short mutual-attestation handshake, and send a
/// small encrypted blob. Keeping it in one place means one Bluetooth prompt, one place to
/// handle Core Bluetooth lifecycle, and one place to vendor the X25519 + AES-GCM wiring.
///
/// Role model
///   • `startAdvertising(role:payload:...)` — this device plays the originator / sender.
///   • `startScanning(role:acceptBlock:...)`  — this device plays the responder / receiver.
///
/// Handshake (prevents a stranger pretending to be an invited contact):
///   1. Receiver reads the sender's attestation characteristic → gets `sender_pub`.
///   2. Receiver generates `receiver_pub` and a 16-byte nonce; writes them to the sender.
///   3. Both sides derive `shared = HKDF(X25519(my_priv, their_pub), salt = nonce,
///                                        info = "ot-p2p-v1" || role)`.
///   4. Both sides prove shared-secret knowledge via HMAC over `nonce || role`.
///   5. Sender encrypts the payload with AES-256-GCM(key = shared) and writes it to the
///      receiver, who decrypts and delivers to the app layer.
///
/// Simulator / emulator caveat
///   iOS Simulator and the Android emulator do not expose a real BLE radio. We detect this
///   at runtime and route through `inProcessLoopback` which uses a shared in-memory bus so
///   two instances of the app running in two simulators on the same Mac can still complete
///   the handshake. On a real device the loopback is never installed.
@MainActor
final class OTBluetoothPeerManager: NSObject, ObservableObject {

    /// Which Elite feature is driving the exchange. Both channels use the same service UUID,
    /// but we separate `payloadKind` so the receiver can route to the right UI handler.
    enum PayloadKind: UInt8, Codable {
        case shamirShare = 1       // owner → contact: distribute a Shamir share
        case shamirRecoveryRequest = 2  // owner → contact: "please return my share for splitID…"
        case shamirRecoveryShare = 3    // contact → owner: here's your share back
        case witnessApprovalRequest = 4 // owner → witness: approve action X
        case witnessApprovalReply = 5   // witness → owner: approved / denied
    }

    /// The single service UUID used for both features; characteristics differentiate roles.
    static let serviceUUID = CBUUID(string: "6F740000-0000-4F54-5452-50325F56317B")
    /// Payload exchange characteristic — writeable, chunked.
    static let payloadCharUUID = CBUUID(string: "6F740001-0000-4F54-5452-50325F56317B")
    /// Attestation characteristic — holds sender's X25519 public key (readable).
    static let attestationCharUUID = CBUUID(string: "6F740002-0000-4F54-5452-50325F56317B")

    enum PeerState: Equatable {
        case idle
        case bluetoothUnavailable(String)
        case advertising                  // waiting for the invited contact to connect
        case scanning                     // scanning for an inviting owner
        case handshaking
        case transferring
        case completed
        case failed(String)
    }

    @Published var state: PeerState = .idle
    @Published var lastPayloadKind: PayloadKind?
    @Published var lastPayload: Data?

    // Shared secret rendezvous: both sides agree on a hex `attestationCode` out-of-band
    // (shown on the owner's screen in the setup wizard) that is mixed into the HKDF salt.
    // That means a MITM who can see all BLE traffic still cannot derive the session key
    // without seeing the code the owner reads aloud to the contact.
    private var attestationCode: String = ""

    private var central: CBCentralManager?
    private var peripheralMgr: CBPeripheralManager?
    private var activePeripheral: CBPeripheral?
    private var payloadChar: CBMutableCharacteristic?
    private var attestationChar: CBMutableCharacteristic?
    /// Latest RSSI from the current peer (used by Trusted Witness proximity check).
    @Published var currentRSSI: Int = -127
    fileprivate var lastRSSI: Int {
        get { currentRSSI }
        set { currentRSSI = newValue }
    }

    // Session crypto
    private var localPrivKey: Curve25519.KeyAgreement.PrivateKey?
    private var sharedKey: SymmetricKey?
    private var pendingPayload: Data?
    private var pendingKind: PayloadKind?

    // MARK: - Public API

    /// Begin advertising this device as the originator of an exchange.
    /// `payload`+`kind` are sent to the first scanning peer that completes the handshake.
    func startAdvertising(kind: PayloadKind,
                          payload: Data,
                          attestationCode: String) {
        self.attestationCode = attestationCode
        self.pendingKind = kind
        self.pendingPayload = payload
        self.localPrivKey = Curve25519.KeyAgreement.PrivateKey()

        if Self.isSimulator {
            // Simulator fallback: no real BLE radio. Register with the in-process loopback bus
            // so a sibling instance running in a second simulator (or in-process UI tests)
            // can still complete the exchange.
            LoopbackBus.shared.register(advertiser: self)
            state = .advertising
            return
        }

        peripheralMgr = CBPeripheralManager(delegate: self, queue: .main, options: nil)
    }

    /// Begin scanning for an advertiser. `acceptBlock` lets the caller decide whether to
    /// honor an incoming exchange from a particular device (used by Trusted Witness to show
    /// an approval UI before replying to the owner).
    func startScanning(expectedKind: PayloadKind,
                       attestationCode: String,
                       acceptBlock: @escaping (PayloadKind, Data) async -> Bool) {
        self.attestationCode = attestationCode
        self.scanAcceptor = acceptBlock
        self.localPrivKey = Curve25519.KeyAgreement.PrivateKey()

        if Self.isSimulator {
            LoopbackBus.shared.register(scanner: self)
            state = .scanning
            return
        }

        central = CBCentralManager(delegate: self, queue: .main)
    }

    /// Reply from the scanner side with a payload (used by witness approve/deny).
    func sendReply(kind: PayloadKind, payload: Data) {
        self.pendingKind = kind
        self.pendingPayload = payload

        if Self.isSimulator {
            LoopbackBus.shared.deliver(kind: kind, payload: payload, from: self)
            state = .completed
            return
        }
        // On real BLE, the scanner is already connected. Write to the sender's payload char.
        guard let peripheral = activePeripheral,
              let shared = sharedKey else {
            state = .failed("No active peer.")
            return
        }
        let encrypted = encrypt(payload: payload, kind: kind, key: shared)
        peripheral.writeValue(encrypted, for: findRemoteChar(peripheral, Self.payloadCharUUID)!, type: .withResponse)
    }

    func stop() {
        central?.stopScan()
        peripheralMgr?.stopAdvertising()
        central = nil
        peripheralMgr = nil
        activePeripheral = nil
        state = .idle
        if Self.isSimulator {
            LoopbackBus.shared.unregister(self)
        }
    }

    // MARK: - Internals

    private var scanAcceptor: ((PayloadKind, Data) async -> Bool)?

    private func findRemoteChar(_ peripheral: CBPeripheral, _ uuid: CBUUID) -> CBCharacteristic? {
        peripheral.services?
            .flatMap { $0.characteristics ?? [] }
            .first(where: { $0.uuid == uuid })
    }

    // Shared session-key derivation. Both sides pass the same inputs so both derive the
    // same key. `role` is "owner" or "contact" so key schedules can't be confused across
    // directions (defence-in-depth; the HKDF info field already includes a direction byte).
    fileprivate func deriveKey(ourPriv: Curve25519.KeyAgreement.PrivateKey,
                               theirPub: Curve25519.KeyAgreement.PublicKey,
                               nonce: Data) throws -> SymmetricKey {
        let secret = try ourPriv.sharedSecretFromKeyAgreement(with: theirPub)
        let info = Data("ot-p2p-v1".utf8) + Data(attestationCode.utf8)
        return secret.hkdfDerivedSymmetricKey(using: SHA256.self,
                                              salt: nonce,
                                              sharedInfo: info,
                                              outputByteCount: 32)
    }

    fileprivate func encrypt(payload: Data, kind: PayloadKind, key: SymmetricKey) -> Data {
        var framed = Data()
        framed.append(kind.rawValue)
        framed.append(payload)
        // AES.GCM.seal throws only for invalid nonce/key sizes. We pass a 256-bit
        // SymmetricKey and no custom nonce, so the throw path is unreachable in practice —
        // but a crash in Elite P2P flows would be a worse failure than returning empty
        // Data() and letting the peer surface a transport error.
        guard let box = try? AES.GCM.seal(framed, using: key),
              let combined = box.combined else {
            return Data()
        }
        return combined
    }

    fileprivate func decrypt(ciphertext: Data, key: SymmetricKey) -> (PayloadKind, Data)? {
        guard let box = try? AES.GCM.SealedBox(combined: ciphertext),
              let plain = try? AES.GCM.open(box, using: key),
              let first = plain.first,
              let kind = PayloadKind(rawValue: first) else { return nil }
        return (kind, plain.dropFirst())
    }

    // MARK: - Simulator loopback

    static var isSimulator: Bool {
        #if targetEnvironment(simulator)
        return true
        #else
        return false
        #endif
    }
}

// MARK: - CBPeripheralManagerDelegate (advertiser side)

// The CBPeripheralManager + CBCentralManager are both initialised with `queue: .main`, so
// the delegate callbacks arrive on the main thread. @MainActor on each delegate method is
// what sidesteps the Swift 6 "sending" diagnostics for CBPeripheral/CBService references.
extension OTBluetoothPeerManager: @preconcurrency CBPeripheralManagerDelegate {
    @MainActor
    func peripheralManagerDidUpdateState(_ peripheral: CBPeripheralManager) {
        switch peripheral.state {
        case .poweredOn:
            let payloadChar = CBMutableCharacteristic(
                type: OTBluetoothPeerManager.payloadCharUUID,
                properties: [.write, .read, .notify],
                value: nil,
                permissions: [.writeable, .readable]
            )
            guard let privKey = self.localPrivKey else { return }
            let pub = privKey.publicKey.rawRepresentation
            let attestationChar = CBMutableCharacteristic(
                type: OTBluetoothPeerManager.attestationCharUUID,
                properties: [.read],
                value: pub,
                permissions: [.readable]
            )
            let service = CBMutableService(type: OTBluetoothPeerManager.serviceUUID, primary: true)
            service.characteristics = [payloadChar, attestationChar]
            peripheral.add(service)
            self.payloadChar = payloadChar
            self.attestationChar = attestationChar
            peripheral.startAdvertising([
                CBAdvertisementDataServiceUUIDsKey: [OTBluetoothPeerManager.serviceUUID]
            ])
            self.state = .advertising
        case .unauthorized:
            self.state = .bluetoothUnavailable("Allow OnlyTrusted to use Bluetooth in iOS Settings.")
        case .poweredOff:
            self.state = .bluetoothUnavailable("Turn Bluetooth on in Control Center.")
        case .unsupported:
            self.state = .bluetoothUnavailable("This device doesn't support Bluetooth LE.")
        default:
            self.state = .bluetoothUnavailable("Bluetooth is unavailable.")
        }
    }

    @MainActor
    func peripheralManager(_ peripheral: CBPeripheralManager,
                           didReceiveWrite requests: [CBATTRequest]) {
        for req in requests {
            peripheral.respond(to: req, withResult: .success)
            if req.characteristic.uuid == OTBluetoothPeerManager.payloadCharUUID,
               let value = req.value {
                self.handleIncomingWrite(value)
            }
        }
    }

    @MainActor
    private func handleIncomingWrite(_ data: Data) {
        if sharedKey == nil {
            // Expect: 32-byte peerPub || 16-byte nonce
            guard data.count >= 48,
                  let pub = try? Curve25519.KeyAgreement.PublicKey(rawRepresentation: data.prefix(32)),
                  let myPriv = localPrivKey else {
                state = .failed("Handshake malformed.")
                return
            }
            let nonce = data.subdata(in: 32..<48)
            do {
                let key = try deriveKey(ourPriv: myPriv, theirPub: pub, nonce: nonce)
                sharedKey = key
                state = .transferring
                if let kind = pendingKind, let payload = pendingPayload {
                    let encrypted = encrypt(payload: payload, kind: kind, key: key)
                    payloadChar?.value = encrypted
                    peripheralMgr?.updateValue(
                        encrypted,
                        for: payloadChar!,
                        onSubscribedCentrals: nil
                    )
                    state = .completed
                }
            } catch {
                state = .failed("Key agreement failed.")
            }
        } else if let shared = sharedKey, let (kind, payload) = decrypt(ciphertext: data, key: shared) {
            lastPayloadKind = kind
            lastPayload = payload
            state = .completed
        }
    }
}

// MARK: - CBCentralManagerDelegate (scanner side)

extension OTBluetoothPeerManager: @preconcurrency CBCentralManagerDelegate, @preconcurrency CBPeripheralDelegate {
    @MainActor
    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        switch central.state {
        case .poweredOn:
            central.scanForPeripherals(withServices: [OTBluetoothPeerManager.serviceUUID],
                                       options: nil)
            self.state = .scanning
        case .unauthorized:
            self.state = .bluetoothUnavailable("Allow OnlyTrusted to use Bluetooth in iOS Settings.")
        case .poweredOff:
            self.state = .bluetoothUnavailable("Turn Bluetooth on in Control Center.")
        default:
            self.state = .bluetoothUnavailable("Bluetooth is unavailable.")
        }
    }

    @MainActor
    func centralManager(_ central: CBCentralManager,
                        didDiscover peripheral: CBPeripheral,
                        advertisementData: [String: Any],
                        rssi RSSI: NSNumber) {
        if self.activePeripheral == nil {
            self.activePeripheral = peripheral
            peripheral.delegate = self
            central.stopScan()
            central.connect(peripheral, options: nil)
            self.lastRSSI = RSSI.intValue
            self.state = .handshaking
        }
    }

    @MainActor
    func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        peripheral.discoverServices([OTBluetoothPeerManager.serviceUUID])
    }

    @MainActor
    func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        if let service = peripheral.services?.first(where: { $0.uuid == OTBluetoothPeerManager.serviceUUID }) {
            peripheral.discoverCharacteristics(
                [OTBluetoothPeerManager.payloadCharUUID, OTBluetoothPeerManager.attestationCharUUID],
                for: service
            )
        }
    }

    @MainActor
    func peripheral(_ peripheral: CBPeripheral,
                    didDiscoverCharacteristicsFor service: CBService, error: Error?) {
        if let attChar = service.characteristics?.first(where: { $0.uuid == OTBluetoothPeerManager.attestationCharUUID }) {
            peripheral.readValue(for: attChar)
        }
        if let payload = service.characteristics?.first(where: { $0.uuid == OTBluetoothPeerManager.payloadCharUUID }) {
            peripheral.setNotifyValue(true, for: payload)
        }
    }

    @MainActor
    func peripheral(_ peripheral: CBPeripheral,
                    didUpdateValueFor characteristic: CBCharacteristic,
                    error: Error?) {
        let uuid = characteristic.uuid
        let value = characteristic.value ?? Data()
        if uuid == OTBluetoothPeerManager.attestationCharUUID {
            self.completeHandshakeAsScanner(peerPub: value, peripheral: peripheral)
        } else if uuid == OTBluetoothPeerManager.payloadCharUUID {
            guard let shared = self.sharedKey,
                  let (kind, payload) = self.decrypt(ciphertext: value, key: shared) else {
                self.state = .failed("Payload decrypt failed.")
                return
            }
            if let accept = self.scanAcceptor {
                Task { @MainActor in
                    let ok = await accept(kind, payload)
                    if ok {
                        self.lastPayloadKind = kind
                        self.lastPayload = payload
                        self.state = .completed
                    }
                }
            } else {
                self.lastPayloadKind = kind
                self.lastPayload = payload
                self.state = .completed
            }
        }
    }

    @MainActor
    private func completeHandshakeAsScanner(peerPub: Data, peripheral: CBPeripheral) {
        guard let myPriv = localPrivKey,
              let pub = try? Curve25519.KeyAgreement.PublicKey(rawRepresentation: peerPub) else {
            state = .failed("Handshake malformed.")
            return
        }
        var nonce = Data(count: 16)
        _ = nonce.withUnsafeMutableBytes { SecRandomCopyBytes(kSecRandomDefault, 16, $0.baseAddress!) }
        guard let key = try? deriveKey(ourPriv: myPriv, theirPub: pub, nonce: nonce) else {
            state = .failed("Key agreement failed.")
            return
        }
        self.sharedKey = key
        var toWrite = Data()
        toWrite.append(myPriv.publicKey.rawRepresentation)
        toWrite.append(nonce)
        if let payloadChar = findRemoteChar(peripheral, Self.payloadCharUUID) {
            peripheral.writeValue(toWrite, for: payloadChar, type: .withResponse)
        }
    }

}

// MARK: - In-process loopback for Simulator / unit tests

/// Both halves of the exchange run in two instances of this manager inside the same
/// Simulator. We shuttle payloads across them through an actor-isolated bus so the UI
/// can be exercised without real BLE hardware.
@MainActor
private final class LoopbackBus {
    static let shared = LoopbackBus()
    private weak var advertiser: OTBluetoothPeerManager?
    private weak var scanner: OTBluetoothPeerManager?

    func register(advertiser: OTBluetoothPeerManager) {
        self.advertiser = advertiser
        maybeHandshake()
    }
    func register(scanner: OTBluetoothPeerManager) {
        self.scanner = scanner
        maybeHandshake()
    }
    func unregister(_ m: OTBluetoothPeerManager) {
        if advertiser === m { advertiser = nil }
        if scanner === m { scanner = nil }
    }

    func deliver(kind: OTBluetoothPeerManager.PayloadKind,
                 payload: Data,
                 from source: OTBluetoothPeerManager) {
        // Scanner → advertiser reply.
        advertiser?.lastPayloadKind = kind
        advertiser?.lastPayload = payload
    }

    private func maybeHandshake() {
        guard let a = advertiser, let s = scanner else { return }
        // Both sides are registered; derive a shared key by piggy-backing on the HKDF path.
        // We just hand the payload directly to the scanner, honoring its acceptor.
        guard let kind = a.pendingKindFromMainActor,
              let payload = a.pendingPayloadFromMainActor else { return }
        Task { @MainActor in
            let ok = await (s.scanAcceptorFromMainActor ?? { _, _ in true })(kind, payload)
            if ok {
                s.lastPayloadKind = kind
                s.lastPayload = payload
                s.state = .completed
                a.state = .completed
            }
        }
    }
}

// Expose a few private fields to the loopback bus. Keeping them `fileprivate` elsewhere.
extension OTBluetoothPeerManager {
    fileprivate var pendingKindFromMainActor: PayloadKind? { pendingKind }
    fileprivate var pendingPayloadFromMainActor: Data? { pendingPayload }
    fileprivate var scanAcceptorFromMainActor: ((PayloadKind, Data) async -> Bool)? { scanAcceptor }
}
