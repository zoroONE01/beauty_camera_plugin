import Flutter
import UIKit
import AVFoundation
import CoreImage
import Photos

class CameraManager: NSObject {
    
    // MARK: - Properties
    private var captureSession: AVCaptureSession?
    private var videoDeviceInput: AVCaptureDeviceInput?
    private var photoOutput: AVCapturePhotoOutput?
    private var videoDataOutput: AVCaptureVideoDataOutput?
    private var previewLayer: AVCaptureVideoPreviewLayer?
    
    // Filter properties
    private var currentFilter: CIFilter?
    private var filterIntensity: Float = 1.0
    private var currentFilterType: String = "none"
    
    // Camera state
    private var isUsingFrontCamera = false
    private var flashMode: AVCaptureDevice.FlashMode = .off
    private var zoomLevel: CGFloat = 1.0
    private var exposureLevel: Float = 0.0
    private var isAutoFocusEnabled = true
    
    // Core Image context for filter processing
    private let ciContext = CIContext()
    
    // Queue for video processing
    private let videoDataOutputQueue = DispatchQueue(label: "VideoDataOutput", qos: .userInitiated, attributes: [], autoreleaseFrequency: .workItem)
    
    // MARK: - Initialization
    override init() {
        super.init()
    }
    
    // MARK: - Camera Setup
    func initializeCamera(completion: @escaping (Error?) -> Void) {
        // Check camera permission
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            setupCamera(completion: completion)
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { granted in
                if granted {
                    self.setupCamera(completion: completion)
                } else {
                    completion(CameraError.permissionDenied)
                }
            }
        case .denied, .restricted:
            completion(CameraError.permissionDenied)
        @unknown default:
            completion(CameraError.unknown)
        }
    }
    
    private func setupCamera(completion: @escaping (Error?) -> Void) {
        captureSession = AVCaptureSession()
        guard let captureSession = captureSession else {
            completion(CameraError.sessionCreationFailed)
            return
        }
        
        captureSession.beginConfiguration()
        
        // Configure session preset
        if captureSession.canSetSessionPreset(.photo) {
            captureSession.sessionPreset = .photo
        }
        
        do {
            // Setup camera input
            try setupCameraInput()
            
            // Setup photo output
            setupPhotoOutput()
            
            // Setup video data output for real-time filtering
            setupVideoDataOutput()
            
            captureSession.commitConfiguration()
            
            // Start the session on a background queue
            DispatchQueue.global(qos: .userInitiated).async {
                captureSession.startRunning()
                DispatchQueue.main.async {
                    completion(nil)
                }
            }
        } catch {
            captureSession.commitConfiguration()
            completion(error)
        }
    }
    
    private func setupCameraInput() throws {
        guard let captureSession = captureSession else { return }
        
        // Remove existing input if any
        if let currentInput = videoDeviceInput {
            captureSession.removeInput(currentInput)
        }
        
        // Get the desired camera device
        let deviceDiscoverySession = AVCaptureDevice.DiscoverySession(
            deviceTypes: [.builtInWideAngleCamera, .builtInTrueDepthCamera],
            mediaType: .video,
            position: isUsingFrontCamera ? .front : .back
        )
        
        guard let videoDevice = deviceDiscoverySession.devices.first else {
            throw CameraError.deviceNotFound
        }
        
        let videoDeviceInput = try AVCaptureDeviceInput(device: videoDevice)
        
        if captureSession.canAddInput(videoDeviceInput) {
            captureSession.addInput(videoDeviceInput)
            self.videoDeviceInput = videoDeviceInput
        } else {
            throw CameraError.inputAddFailed
        }
    }
    
    private func setupPhotoOutput() {
        guard let captureSession = captureSession else { return }
        
        photoOutput = AVCapturePhotoOutput()
        
        if let photoOutput = photoOutput, captureSession.canAddOutput(photoOutput) {
            captureSession.addOutput(photoOutput)
            
            // Configure photo output
            photoOutput.isHighResolutionCaptureEnabled = true
            if let connection = photoOutput.connection(with: .video) {
                if connection.isVideoStabilizationSupported {
                    connection.preferredVideoStabilizationMode = .auto
                }
            }
        }
    }
    
    private func setupVideoDataOutput() {
        guard let captureSession = captureSession else { return }
        
        videoDataOutput = AVCaptureVideoDataOutput()
        
        if let videoDataOutput = videoDataOutput, captureSession.canAddOutput(videoDataOutput) {
            captureSession.addOutput(videoDataOutput)
            
            // Configure video data output
            videoDataOutput.videoSettings = [
                kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA
            ]
            videoDataOutput.setSampleBufferDelegate(self, queue: videoDataOutputQueue)
            videoDataOutput.alwaysDiscardsLateVideoFrames = true
            
            if let connection = videoDataOutput.connection(with: .video) {
                if connection.isVideoStabilizationSupported {
                    connection.preferredVideoStabilizationMode = .auto
                }
            }
        }
    }
    
    // MARK: - Public Methods
    func takePicture(completion: @escaping (String?, Error?) -> Void) {
        guard let photoOutput = photoOutput else {
            completion(nil, CameraError.outputNotConfigured)
            return
        }
        
        var photoSettings = AVCapturePhotoSettings()
        
        // Configure format
        if #available(iOS 11.0, *) {
            if photoOutput.availablePhotoCodecTypes.contains(.hevc) {
                photoSettings = AVCapturePhotoSettings(format: [AVVideoCodecKey: AVVideoCodecType.hevc])
            }
        }
        
        // Configure flash
        if photoOutput.supportedFlashModes.contains(flashMode) {
            photoSettings.flashMode = flashMode
        }
        
        // Enable high resolution capture
        photoSettings.isHighResolutionPhotoEnabled = true
        
        // Create photo capture delegate
        let photoCaptureDelegate = PhotoCaptureDelegate { [weak self] imageData, error in
            if let error = error {
                completion(nil, error)
                return
            }
            
            guard let imageData = imageData else {
                completion(nil, CameraError.captureDataNil)
                return
            }
            
            // Apply filter if needed
            let finalImageData: Data
            if self?.currentFilterType != "none", let filteredData = self?.applyFilterToImageData(imageData) {
                finalImageData = filteredData
            } else {
                finalImageData = imageData
            }
            
            // Save to temporary file
            self?.saveImageData(finalImageData, completion: completion)
        }
        
        photoOutput.capturePhoto(with: photoSettings, delegate: photoCaptureDelegate)
    }
    
    func setFilter(filterType: String, completion: @escaping (Error?) -> Void) {
        currentFilterType = filterType
        currentFilter = createFilter(for: filterType)
        completion(nil)
    }
    
    func setFilterIntensity(intensity: Float, completion: @escaping (Error?) -> Void) {
        filterIntensity = max(0.0, min(1.0, intensity))
        completion(nil)
    }
    
    func switchCamera(completion: @escaping (Error?) -> Void) {
        guard let captureSession = captureSession else {
            completion(CameraError.sessionNotConfigured)
            return
        }
        
        captureSession.beginConfiguration()
        
        isUsingFrontCamera.toggle()
        
        do {
            try setupCameraInput()
            captureSession.commitConfiguration()
            completion(nil)
        } catch {
            captureSession.commitConfiguration()
            completion(error)
        }
    }
    
    func toggleFlash(completion: @escaping (Error?) -> Void) {
        switch flashMode {
        case .off:
            flashMode = .on
        case .on:
            flashMode = .auto
        case .auto:
            flashMode = .off
        @unknown default:
            flashMode = .off
        }
        completion(nil)
    }
    
    func setZoom(zoom: Float, completion: @escaping (Error?) -> Void) {
        guard let device = videoDeviceInput?.device else {
            completion(CameraError.deviceNotFound)
            return
        }
        
        let zoomFactor = 1.0 + Float(zoom) * Float(device.maxAvailableVideoZoomFactor - 1.0)
        
        do {
            try device.lockForConfiguration()
            device.videoZoomFactor = min(max(CGFloat(zoomFactor), 1.0), device.maxAvailableVideoZoomFactor)
            device.unlockForConfiguration()
            zoomLevel = CGFloat(zoomFactor)
            completion(nil)
        } catch {
            completion(error)
        }
    }
    
    func setExposure(exposure: Float, completion: @escaping (Error?) -> Void) {
        guard let device = videoDeviceInput?.device else {
            completion(CameraError.deviceNotFound)
            return
        }
        
        let exposureValue = max(min(exposure, 2.0), -2.0)
        exposureLevel = exposureValue
        
        do {
            try device.lockForConfiguration()
            device.setExposureTargetBias(exposureValue, completionHandler: nil)
            device.unlockForConfiguration()
            completion(nil)
        } catch {
            completion(error)
        }
    }
    
    func setFocusPoint(x: Float, y: Float, completion: @escaping (Error?) -> Void) {
        guard let device = videoDeviceInput?.device else {
            completion(CameraError.deviceNotFound)
            return
        }
        
        let focusPoint = CGPoint(x: CGFloat(x), y: CGFloat(y))
        
        do {
            try device.lockForConfiguration()
            if device.isFocusPointOfInterestSupported {
                device.focusPointOfInterest = focusPoint
                device.focusMode = .autoFocus
            }
            if device.isExposurePointOfInterestSupported {
                device.exposurePointOfInterest = focusPoint
                device.exposureMode = .autoExpose
            }
            device.unlockForConfiguration()
            completion(nil)
        } catch {
            completion(error)
        }
    }
    
    func setAutoFocus(enabled: Bool, completion: @escaping (Error?) -> Void) {
        guard let device = videoDeviceInput?.device else {
            completion(CameraError.deviceNotFound)
            return
        }
        
        isAutoFocusEnabled = enabled
        
        do {
            try device.lockForConfiguration()
            if enabled {
                if device.isFocusModeSupported(.continuousAutoFocus) {
                    device.focusMode = .continuousAutoFocus
                }
            } else {
                if device.isFocusModeSupported(.locked) {
                    device.focusMode = .locked
                }
            }
            device.unlockForConfiguration()
            completion(nil)
        } catch {
            completion(error)
        }
    }
    
    func dispose() {
        captureSession?.stopRunning()
        captureSession = nil
        videoDeviceInput = nil
        photoOutput = nil
        videoDataOutput = nil
        previewLayer = nil
        currentFilter = nil
    }
    
    func getPreviewLayer() -> AVCaptureVideoPreviewLayer? {
        guard let captureSession = captureSession else { return nil }
        
        if previewLayer == nil {
            previewLayer = AVCaptureVideoPreviewLayer(session: captureSession)
            previewLayer?.videoGravity = .resizeAspectFill
        }
        
        return previewLayer
    }
    
    // MARK: - Filter Creation
    private func createFilter(for type: String) -> CIFilter? {
        switch type.lowercased() {
        case "sepia":
            return CIFilter(name: "CISepiaTone")
        case "mono", "grayscale":
            return CIFilter(name: "CIPhotoEffectMono")
        case "negative", "invert":
            return CIFilter(name: "CIColorInvert")
        case "vintage":
            return CIFilter(name: "CIPhotoEffectVintage")
        case "dramatic":
            return CIFilter(name: "CIPhotoEffectDramatic")
        case "noir":
            return CIFilter(name: "CIPhotoEffectNoir")
        case "fade":
            return CIFilter(name: "CIPhotoEffectFade")
        case "instant":
            return CIFilter(name: "CIPhotoEffectInstant")
        case "process":
            return CIFilter(name: "CIPhotoEffectProcess")
        case "transfer":
            return CIFilter(name: "CIPhotoEffectTransfer")
        default:
            return nil
        }
    }
    
    // MARK: - Image Processing
    private func applyFilterToImageData(_ imageData: Data) -> Data? {
        guard let filter = currentFilter,
              let inputImage = CIImage(data: imageData) else {
            return nil
        }
        
        filter.setValue(inputImage, forKey: kCIInputImageKey)
        
        // Apply intensity if the filter supports it
        if filter.inputKeys.contains("inputIntensity") {
            filter.setValue(filterIntensity, forKey: "inputIntensity")
        }
        
        guard let outputImage = filter.outputImage,
              let cgImage = ciContext.createCGImage(outputImage, from: outputImage.extent) else {
            return nil
        }
        
        let uiImage = UIImage(cgImage: cgImage)
        return uiImage.jpegData(compressionQuality: 0.9)
    }
    
    private func saveImageData(_ imageData: Data, completion: @escaping (String?, Error?) -> Void) {
        // Create a unique filename
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd-HH-mm-ss-SSS"
        let filename = "IMG_\(formatter.string(from: Date())).jpg"
        
        // Save to temporary directory
        let tempDir = NSTemporaryDirectory()
        let filePath = URL(fileURLWithPath: tempDir).appendingPathComponent(filename)
        
        do {
            try imageData.write(to: filePath)
            completion(filePath.path, nil)
        } catch {
            completion(nil, error)
        }
    }
}

// MARK: - AVCaptureVideoDataOutputSampleBufferDelegate
extension CameraManager: AVCaptureVideoDataOutputSampleBufferDelegate {
    func captureOutput(_ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer, from connection: AVCaptureConnection) {
        // This is where you could implement real-time filter preview if needed
        // For now, we'll handle filtering during photo capture
    }
}

// MARK: - Photo Capture Delegate
private class PhotoCaptureDelegate: NSObject, AVCapturePhotoCaptureDelegate {
    private let completion: (Data?, Error?) -> Void
    
    init(completion: @escaping (Data?, Error?) -> Void) {
        self.completion = completion
    }
    
    func photoOutput(_ output: AVCapturePhotoOutput, didFinishProcessingPhoto photo: AVCapturePhoto, error: Error?) {
        if let error = error {
            completion(nil, error)
            return
        }
        
        guard let imageData = photo.fileDataRepresentation() else {
            completion(nil, CameraError.captureDataNil)
            return
        }
        
        completion(imageData, nil)
    }
}

// MARK: - Camera Errors
enum CameraError: LocalizedError {
    case permissionDenied
    case sessionCreationFailed
    case deviceNotFound
    case inputAddFailed
    case outputNotConfigured
    case sessionNotConfigured
    case captureDataNil
    case unknown
    
    var errorDescription: String? {
        switch self {
        case .permissionDenied:
            return "Camera permission denied"
        case .sessionCreationFailed:
            return "Failed to create capture session"
        case .deviceNotFound:
            return "Camera device not found"
        case .inputAddFailed:
            return "Failed to add camera input"
        case .outputNotConfigured:
            return "Photo output not configured"
        case .sessionNotConfigured:
            return "Capture session not configured"
        case .captureDataNil:
            return "Captured image data is nil"
        case .unknown:
            return "Unknown camera error"
        }
    }
}
