FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

# See files/tinker-board-s.cfg for why the generic ARM defconfig loses options
# the Tinker Board S needs once meta-rockchip removes the other platforms.
SRC_URI:append:tinker-board-s = " file://tinker-board-s.cfg"
