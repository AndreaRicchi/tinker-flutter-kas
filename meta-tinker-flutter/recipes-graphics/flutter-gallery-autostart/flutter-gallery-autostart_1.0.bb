SUMMARY = "Start the Flutter gallery on flutter-pi at boot"
DESCRIPTION = "systemd unit that runs the Flutter gallery shipped by \
meta-flutter with the flutter-pi embedder, straight on DRM/KMS, as soon as \
the board boots."
HOMEPAGE = "https://github.com/ardera/flutter-pi"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

# FLUTTER_SDK_TAG and get_flutter_sdk_version() come from meta-flutter: the
# gallery installs under the Flutter SDK version it was built with, so the unit
# follows meta-flutter's SDK rolls.
require conf/include/flutter-version.inc

inherit systemd features_check

REQUIRED_DISTRO_FEATURES = "systemd"

SRC_URI = "file://flutter-gallery.service.in"
S = "${UNPACKDIR}"

# The gallery is an SDK app: meta-flutter names its install directory after
# FLUTTER_APPLICATION_PATH (conf/include/flutter-sdk-app.inc). flutter-pi is
# built with meta-flutter's filesystem layout and takes this directory, which
# holds data/flutter_assets and lib/libapp.so, as its bundle argument.
FLUTTER_GALLERY_APP = "flutter-sdk-dev-integration-tests-flutter-gallery"
FLUTTER_GALLERY_BUNDLE = "${datadir}/flutter/${FLUTTER_GALLERY_APP}/${@get_flutter_sdk_version(d)}/release"

do_configure[noexec] = "1"
do_compile[noexec] = "1"

do_install() {
    install -d ${D}${systemd_system_unitdir}
    sed -e 's|@BINDIR@|${bindir}|g' \
        -e 's|@BUNDLE@|${FLUTTER_GALLERY_BUNDLE}|g' \
        ${S}/flutter-gallery.service.in \
        > ${D}${systemd_system_unitdir}/flutter-gallery.service
}

SYSTEMD_SERVICE:${PN} = "flutter-gallery.service"
SYSTEMD_AUTO_ENABLE = "enable"

RDEPENDS:${PN} = "flutter-pi ${FLUTTER_GALLERY_APP}"
