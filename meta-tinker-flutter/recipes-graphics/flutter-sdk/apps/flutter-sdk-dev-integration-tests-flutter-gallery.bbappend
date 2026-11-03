# Build the gallery for the 32-bit Tinker Board S.
#
# meta-flutter skips every application recipe on 32-bit targets
# (conf/include/flutter-app.inc, commit 3ad3a09 "flutter-app: skip app recipes
# on 32-bit targets"): `flutter build bundle` only accepts the 64-bit
# --target-platform values, so the "linux-arm" it derives from TARGET_ARCH is
# rejected by the Flutter tool.
#
# That limit is in the tool, not in the toolchain. The asset bundle that step
# produces does not depend on the CPU, and meta-flutter then compiles the Dart
# kernel and runs gen_snapshot from flutter-engine-sdk-native itself, both
# built by the armv7 engine. So let the tool bundle the assets as linux-arm64
# and leave the AOT image to the real target.
FLUTTER_APP_SUPPORTED_ARCHS:append:arm = " arm"
FLUTTER_BUILD_ARGS:remove:arm = "linux-arm"
FLUTTER_BUILD_ARGS:append:arm = " linux-arm64"
