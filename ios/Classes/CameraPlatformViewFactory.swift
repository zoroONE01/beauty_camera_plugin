import Flutter
import UIKit
import AVFoundation

class CameraPlatformViewFactory: NSObject, FlutterPlatformViewFactory {
    private var messenger: FlutterBinaryMessenger
    private weak var plugin: BeautyCameraPlugin?
    
    init(messenger: FlutterBinaryMessenger, plugin: BeautyCameraPlugin) {
        self.messenger = messenger
        self.plugin = plugin
        super.init()
    }
    
    func create(
        withFrame frame: CGRect,
        viewIdentifier viewId: Int64,
        arguments args: Any?
    ) -> FlutterPlatformView {
        return CameraPlatformView(
            frame: frame,
            viewIdentifier: viewId,
            arguments: args,
            binaryMessenger: messenger,
            plugin: plugin
        )
    }
    
    func createArgsCodec() -> FlutterMessageCodec & NSObjectProtocol {
        return FlutterStandardMessageCodec.sharedInstance()
    }
}

class CameraPlatformView: NSObject, FlutterPlatformView {
    private var _view: UIView
    private var cameraManager: CameraManager?
    
    init(
        frame: CGRect,
        viewIdentifier viewId: Int64,
        arguments args: Any?,
        binaryMessenger messenger: FlutterBinaryMessenger?,
        plugin: BeautyCameraPlugin?
    ) {
        _view = UIView(frame: frame)
        super.init()
        
        // Get camera manager from plugin
        cameraManager = plugin?.getCameraManager()
        
        // Setup camera preview
        setupCameraPreview()
    }
    
    func view() -> UIView {
        return _view
    }
    
    private func setupCameraPreview() {
        guard let cameraManager = cameraManager,
              let previewLayer = cameraManager.getPreviewLayer() else {
            return
        }
        
        // Configure preview layer
        previewLayer.frame = _view.bounds
        previewLayer.videoGravity = .resizeAspectFill
        
        // Add preview layer to view
        _view.layer.addSublayer(previewLayer)
        
        // Ensure the preview layer resizes with the view
        _view.layoutSubviews()
    }
    
    deinit {
        // Clean up if needed
        _view.layer.sublayers?.forEach { layer in
            if layer is AVCaptureVideoPreviewLayer {
                layer.removeFromSuperlayer()
            }
        }
    }
}
