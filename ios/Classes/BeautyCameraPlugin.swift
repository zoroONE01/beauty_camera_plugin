import Flutter
import UIKit
import AVFoundation

public class BeautyCameraPlugin: NSObject, FlutterPlugin {
    static let channelName = "com.example/beauty_camera_plugin"
    static let orientationChannelName = "com.example/beauty_camera_plugin/orientation"
    static let platformViewTypeId = "com.example/camera_preview_view"
    
    private var cameraManager: CameraManager?
    private var orientationStreamHandler: OrientationStreamHandler?
    
    public static func register(with registrar: FlutterPluginRegistrar) {
        let channel = FlutterMethodChannel(name: channelName, binaryMessenger: registrar.messenger())
        let instance = BeautyCameraPlugin()
        registrar.addMethodCallDelegate(instance, channel: channel)
        
        // Register orientation event channel
        let orientationChannel = FlutterEventChannel(name: orientationChannelName, binaryMessenger: registrar.messenger())
        instance.orientationStreamHandler = OrientationStreamHandler()
        orientationChannel.setStreamHandler(instance.orientationStreamHandler)
        
        // Register platform view for camera preview
        registrar.register(
            CameraPlatformViewFactory(messenger: registrar.messenger(), plugin: instance),
            withId: platformViewTypeId
        )
    }
    
    public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        switch call.method {
        case "getPlatformVersion":
            result("iOS " + UIDevice.current.systemVersion)
            
        case "initializeCamera":
            initializeCamera(result: result)
            
        case "takePicture":
            takePicture(result: result)
            
        case "setFilter":
            if let args = call.arguments as? [String: Any],
               let filterType = args["filterType"] as? String {
                setFilter(filterType: filterType, result: result)
            } else {
                result(FlutterError(code: "INVALID_ARGUMENTS", message: "Filter type is required", details: nil))
            }
            
        case "setFilterIntensity":
            if let args = call.arguments as? [String: Any],
               let intensity = args["intensity"] as? Double {
                setFilterIntensity(intensity: Float(intensity), result: result)
            } else {
                result(FlutterError(code: "INVALID_ARGUMENTS", message: "Intensity is required", details: nil))
            }
            
        case "switchCamera":
            switchCamera(result: result)
            
        case "toggleFlash":
            toggleFlash(result: result)
            
        case "setZoom":
            if let args = call.arguments as? [String: Any],
               let zoom = args["zoom"] as? Double {
                setZoom(zoom: Float(zoom), result: result)
            } else {
                result(FlutterError(code: "INVALID_ARGUMENTS", message: "Zoom level is required", details: nil))
            }
            
        case "setExposure":
            if let args = call.arguments as? [String: Any],
               let exposure = args["exposure"] as? Double {
                setExposure(exposure: Float(exposure), result: result)
            } else {
                result(FlutterError(code: "INVALID_ARGUMENTS", message: "Exposure value is required", details: nil))
            }
            
        case "setFocusPoint":
            if let args = call.arguments as? [String: Any],
               let x = args["x"] as? Double,
               let y = args["y"] as? Double {
                setFocusPoint(x: Float(x), y: Float(y), result: result)
            } else {
                result(FlutterError(code: "INVALID_ARGUMENTS", message: "Focus point x and y are required", details: nil))
            }
            
        case "setAutoFocus":
            if let args = call.arguments as? [String: Any],
               let enabled = args["enabled"] as? Bool {
                setAutoFocus(enabled: enabled, result: result)
            } else {
                result(FlutterError(code: "INVALID_ARGUMENTS", message: "Auto focus enabled flag is required", details: nil))
            }
            
        case "dispose":
            dispose(result: result)
            
        default:
            result(FlutterMethodNotImplemented)
        }
    }
    
    private func initializeCamera(result: @escaping FlutterResult) {
        guard cameraManager == nil else {
            result(nil)
            return
        }
        
        cameraManager = CameraManager()
        cameraManager?.initializeCamera { [weak self] error in
            DispatchQueue.main.async {
                if let error = error {
                    result(FlutterError(code: "CAMERA_INIT_ERROR", message: error.localizedDescription, details: nil))
                } else {
                    self?.orientationStreamHandler?.startListening()
                    result(nil)
                }
            }
        }
    }
    
    private func takePicture(result: @escaping FlutterResult) {
        guard let cameraManager = cameraManager else {
            result(FlutterError(code: "CAMERA_NOT_INITIALIZED", message: "Camera not initialized", details: nil))
            return
        }
        
        cameraManager.takePicture { imagePath, error in
            DispatchQueue.main.async {
                if let error = error {
                    result(FlutterError(code: "CAPTURE_ERROR", message: error.localizedDescription, details: nil))
                } else {
                    result(imagePath)
                }
            }
        }
    }
    
    private func setFilter(filterType: String, result: @escaping FlutterResult) {
        guard let cameraManager = cameraManager else {
            result(FlutterError(code: "CAMERA_NOT_INITIALIZED", message: "Camera not initialized", details: nil))
            return
        }
        
        cameraManager.setFilter(filterType: filterType) { error in
            DispatchQueue.main.async {
                if let error = error {
                    result(FlutterError(code: "FILTER_ERROR", message: error.localizedDescription, details: nil))
                } else {
                    result(nil)
                }
            }
        }
    }
    
    private func setFilterIntensity(intensity: Float, result: @escaping FlutterResult) {
        guard let cameraManager = cameraManager else {
            result(FlutterError(code: "CAMERA_NOT_INITIALIZED", message: "Camera not initialized", details: nil))
            return
        }
        
        cameraManager.setFilterIntensity(intensity: intensity) { error in
            DispatchQueue.main.async {
                if let error = error {
                    result(FlutterError(code: "FILTER_INTENSITY_ERROR", message: error.localizedDescription, details: nil))
                } else {
                    result(nil)
                }
            }
        }
    }
    
    private func switchCamera(result: @escaping FlutterResult) {
        guard let cameraManager = cameraManager else {
            result(FlutterError(code: "CAMERA_NOT_INITIALIZED", message: "Camera not initialized", details: nil))
            return
        }
        
        cameraManager.switchCamera { error in
            DispatchQueue.main.async {
                if let error = error {
                    result(FlutterError(code: "CAMERA_SWITCH_ERROR", message: error.localizedDescription, details: nil))
                } else {
                    result(nil)
                }
            }
        }
    }
    
    private func toggleFlash(result: @escaping FlutterResult) {
        guard let cameraManager = cameraManager else {
            result(FlutterError(code: "CAMERA_NOT_INITIALIZED", message: "Camera not initialized", details: nil))
            return
        }
        
        cameraManager.toggleFlash { error in
            DispatchQueue.main.async {
                if let error = error {
                    result(FlutterError(code: "FLASH_ERROR", message: error.localizedDescription, details: nil))
                } else {
                    result(nil)
                }
            }
        }
    }
    
    private func setZoom(zoom: Float, result: @escaping FlutterResult) {
        guard let cameraManager = cameraManager else {
            result(FlutterError(code: "CAMERA_NOT_INITIALIZED", message: "Camera not initialized", details: nil))
            return
        }
        
        cameraManager.setZoom(zoom: zoom) { error in
            DispatchQueue.main.async {
                if let error = error {
                    result(FlutterError(code: "ZOOM_ERROR", message: error.localizedDescription, details: nil))
                } else {
                    result(nil)
                }
            }
        }
    }
    
    private func setExposure(exposure: Float, result: @escaping FlutterResult) {
        guard let cameraManager = cameraManager else {
            result(FlutterError(code: "CAMERA_NOT_INITIALIZED", message: "Camera not initialized", details: nil))
            return
        }
        
        cameraManager.setExposure(exposure: exposure) { error in
            DispatchQueue.main.async {
                if let error = error {
                    result(FlutterError(code: "EXPOSURE_ERROR", message: error.localizedDescription, details: nil))
                } else {
                    result(nil)
                }
            }
        }
    }
    
    private func setFocusPoint(x: Float, y: Float, result: @escaping FlutterResult) {
        guard let cameraManager = cameraManager else {
            result(FlutterError(code: "CAMERA_NOT_INITIALIZED", message: "Camera not initialized", details: nil))
            return
        }
        
        cameraManager.setFocusPoint(x: x, y: y) { error in
            DispatchQueue.main.async {
                if let error = error {
                    result(FlutterError(code: "FOCUS_POINT_ERROR", message: error.localizedDescription, details: nil))
                } else {
                    result(nil)
                }
            }
        }
    }
    
    private func setAutoFocus(enabled: Bool, result: @escaping FlutterResult) {
        guard let cameraManager = cameraManager else {
            result(FlutterError(code: "CAMERA_NOT_INITIALIZED", message: "Camera not initialized", details: nil))
            return
        }
        
        cameraManager.setAutoFocus(enabled: enabled) { error in
            DispatchQueue.main.async {
                if let error = error {
                    result(FlutterError(code: "AUTO_FOCUS_ERROR", message: error.localizedDescription, details: nil))
                } else {
                    result(nil)
                }
            }
        }
    }
    
    private func dispose(result: @escaping FlutterResult) {
        orientationStreamHandler?.stopListening()
        cameraManager?.dispose()
        cameraManager = nil
        result(nil)
    }
    
    func getCameraManager() -> CameraManager? {
        return cameraManager
    }
}
