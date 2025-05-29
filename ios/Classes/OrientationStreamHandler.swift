import Flutter
import UIKit
import CoreMotion

class OrientationStreamHandler: NSObject, FlutterStreamHandler {
    private var eventSink: FlutterEventSink?
    private var motionManager: CMMotionManager?
    private var isListening = false
    
    func onListen(withArguments arguments: Any?, eventSink events: @escaping FlutterEventSink) -> FlutterError? {
        self.eventSink = events
        startListening()
        return nil
    }
    
    func onCancel(withArguments arguments: Any?) -> FlutterError? {
        stopListening()
        self.eventSink = nil
        return nil
    }
    
    func startListening() {
        guard !isListening else { return }
        
        motionManager = CMMotionManager()
        guard let motionManager = motionManager else { return }
        
        if motionManager.isDeviceMotionAvailable {
            motionManager.deviceMotionUpdateInterval = 0.1 // Update every 100ms
            motionManager.startDeviceMotionUpdates(to: .main) { [weak self] motion, error in
                guard let motion = motion, error == nil else { return }
                self?.processDeviceMotion(motion)
            }
            isListening = true
        }
        
        // Also listen for orientation changes
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(orientationDidChange),
            name: UIDevice.orientationDidChangeNotification,
            object: nil
        )
        
        UIDevice.current.beginGeneratingDeviceOrientationNotifications()
    }
    
    func stopListening() {
        guard isListening else { return }
        
        motionManager?.stopDeviceMotionUpdates()
        motionManager = nil
        isListening = false
        
        NotificationCenter.default.removeObserver(self)
        UIDevice.current.endGeneratingDeviceOrientationNotifications()
    }
    
    @objc private func orientationDidChange() {
        sendOrientationData()
    }
    
    private func processDeviceMotion(_ motion: CMDeviceMotion) {
        // You can use motion data for more precise orientation detection if needed
        sendOrientationData()
    }
    
    private func sendOrientationData() {
        guard let eventSink = eventSink else { return }
        
        let deviceOrientation = UIDevice.current.orientation
        let uiOrientation = UIApplication.shared.statusBarOrientation
        
        // Convert orientations to match Android values
        let deviceOrientationValue: Int
        let uiOrientationValue: Int
        
        switch deviceOrientation {
        case .portrait:
            deviceOrientationValue = 0
        case .portraitUpsideDown:
            deviceOrientationValue = 2
        case .landscapeLeft:
            deviceOrientationValue = 3
        case .landscapeRight:
            deviceOrientationValue = 1
        default:
            deviceOrientationValue = 0
        }
        
        switch uiOrientation {
        case .portrait:
            uiOrientationValue = 0
        case .portraitUpsideDown:
            uiOrientationValue = 2
        case .landscapeLeft:
            uiOrientationValue = 3
        case .landscapeRight:
            uiOrientationValue = 1
        default:
            uiOrientationValue = 0
        }
        
        let orientationData: [String: Any] = [
            "deviceOrientation": deviceOrientationValue,
            "uiOrientation": uiOrientationValue
        ]
        
        eventSink(orientationData)
    }
}
