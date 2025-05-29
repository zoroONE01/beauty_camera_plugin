import Flutter
import UIKit

public class SwiftBeautyCameraPlugin: NSObject, FlutterPlugin {
  public static func register(with registrar: FlutterPluginRegistrar) {
    BeautyCameraPlugin.register(with: registrar)
  }
}
