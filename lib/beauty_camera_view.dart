import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'beauty_camera_controller.dart';

class BeautyCameraView extends StatefulWidget {
  final BeautyCameraController controller;
  final Widget? loadingWidget;
  final Widget? errorWidget;

  const BeautyCameraView({
    super.key,
    required this.controller,
    this.loadingWidget,
    this.errorWidget,
  });

  @override
  _BeautyCameraViewState createState() => _BeautyCameraViewState();
}

class _BeautyCameraViewState extends State<BeautyCameraView> {
  @override
  void initState() {
    super.initState();

    // Initialize camera if not already initialized
    if (!widget.controller.isCameraInitialized) {
      widget.controller.initializeCamera();
    }
  }

  @override
  void didUpdateWidget(BeautyCameraView oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.controller != oldWidget.controller &&
        !widget.controller.isCameraInitialized) {
      // If controller changes, re-initialize if needed
      widget.controller.initializeCamera();
    }
  }

  @override
  Widget build(BuildContext context) {
    if (widget.controller.lastErrorMessage != null &&
        widget.errorWidget != null) {
      return widget.errorWidget!;
    } else if (widget.controller.lastErrorMessage != null) {
      return Center(
        child: Text(
          'Error: ${widget.controller.lastErrorMessage}',
          style: const TextStyle(color: Colors.red),
          textAlign: TextAlign.center,
        ),
      );
    }

    if (!widget.controller.isCameraInitialized) {
      return widget.loadingWidget ??
          const Center(child: CircularProgressIndicator());
    }

    // This is where the native camera preview will be rendered.
    // The viewType must match the one registered in the native Android code.
    // Preview orientation is locked and will not auto-rotate with device.
    return Container(
      // Wrap AndroidView in Container to provide additional layout constraints
      alignment: Alignment.center,
      child: const AndroidView(
        viewType: 'com.example/camera_preview_view',
        creationParamsCodec: StandardMessageCodec(),
        // Lock the preview orientation by using a fixed layout direction
        layoutDirection: TextDirection.ltr,
        // creationParams can be used to pass initial parameters to the native view if needed.
        // creationParams: <String, dynamic>{},
      ),
    );
  }
}
