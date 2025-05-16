import 'package:flutter/material.dart';
import '../beauty_camera_plugin.dart';

/// A widget that displays the camera preview with tap-to-focus functionality.
class CameraPreview extends StatefulWidget {
  /// The ID of the texture to display.
  final int textureId;

  /// The aspect ratio of the camera (width / height).
  final double? aspectRatio;

  /// The camera plugin instance.
  final BeautyCameraPlugin cameraPlugin;

  /// Optional fit value for the texture.
  final BoxFit fit;

  /// Optional filter overlay color
  final Color? filterOverlay;

  /// Callback when a focus is requested. If not provided, the default implementation
  /// will be used, which calls [BeautyCameraPlugin.setFocusPoint].
  final Future<bool> Function(double x, double y)? onFocusRequested;

  /// Callback when focus animation completes
  final VoidCallback? onFocusComplete;

  const CameraPreview({
    super.key,
    required this.textureId,
    required this.cameraPlugin,
    this.aspectRatio,
    this.fit = BoxFit.cover,
    this.filterOverlay,
    this.onFocusRequested,
    this.onFocusComplete,
  });

  @override
  State<CameraPreview> createState() => _CameraPreviewState();
}

class _CameraPreviewState extends State<CameraPreview>
    with SingleTickerProviderStateMixin {
  Offset? _focusPoint;
  late AnimationController _focusAnimationController;

  @override
  void initState() {
    super.initState();
    _focusAnimationController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1500),
    );
    _focusAnimationController.addStatusListener((status) {
      if (status == AnimationStatus.completed) {
        setState(() {
          _focusPoint = null;
        });
        if (widget.onFocusComplete != null) {
          widget.onFocusComplete!();
        }
      }
    });
  }

  @override
  void dispose() {
    _focusAnimationController.dispose();
    super.dispose();
  }

  Future<void> _handleTap(TapUpDetails details) async {
    final RenderBox box = context.findRenderObject() as RenderBox;
    final Offset localPoint = box.globalToLocal(details.globalPosition);

    // Convert to normalized coordinates (0.0 to 1.0)
    final double x = localPoint.dx / box.size.width;
    final double y = localPoint.dy / box.size.height;

    // Set the focus point for visual feedback
    setState(() {
      _focusPoint = localPoint;
      _focusAnimationController.reset();
      _focusAnimationController.forward();
    });

    // Request focus
    bool success;
    if (widget.onFocusRequested != null) {
      success = await widget.onFocusRequested!(x, y);
    } else {
      final result = await widget.cameraPlugin.setFocusPoint(x, y);
      success = result['success'] == true;
    }

    if (!success && mounted) {
      // If focus fails, we could provide visual feedback or retry
      // For now, we'll just reset the animation
      _focusAnimationController.reset();
      setState(() {
        _focusPoint = null;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTapUp: _handleTap,
      child: Stack(
        fit: StackFit.expand,
        children: [
          // Camera preview texture
          AspectRatio(
            aspectRatio:
                widget.aspectRatio ?? 3 / 4, // Default if not specified
            child: Stack(
              children: [
                Texture(textureId: widget.textureId),
                // Optional color filter overlay
                if (widget.filterOverlay != null)
                  Container(color: widget.filterOverlay),
              ],
            ),
          ),
          // Focus indicator
          if (_focusPoint != null)
            Positioned(
              left: _focusPoint!.dx - 50,
              top: _focusPoint!.dy - 50,
              child: FadeTransition(
                opacity: Tween<double>(begin: 1.0, end: 0.0).animate(
                  CurvedAnimation(
                    parent: _focusAnimationController,
                    curve: Interval(0.5, 1.0),
                  ),
                ),
                child: ScaleTransition(
                  scale: Tween<double>(begin: 1.2, end: 0.8).animate(
                    CurvedAnimation(
                      parent: _focusAnimationController,
                      curve: Curves.easeInOut,
                    ),
                  ),
                  child: Container(
                    width: 100,
                    height: 100,
                    decoration: BoxDecoration(
                      border: Border.all(color: Colors.white, width: 2),
                      borderRadius: BorderRadius.circular(50),
                    ),
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }
}
