[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

# tinker-flutter-kas

[kas](https://kas.readthedocs.io) project for the ASUS Tinker Board S (Rockchip
RK3288) that builds a Yocto Project 6.0 "wrynose" image and runs the Flutter
gallery on [flutter-pi](https://github.com/ardera/flutter-pi) as soon as the
board boots.

What the image contains:

- `core-image-base` for `MACHINE = tinker-board-s`: mainline kernel and U-Boot
  from meta-rockchip, systemd as init manager
- `flutter-engine` (release mode) and the `flutter-pi` embedder from meta-flutter
- the Flutter gallery (`dev/integration_tests/flutter_gallery` from the Flutter
  SDK that meta-flutter pins) as an AOT bundle
- `flutter-gallery.service`, enabled, which starts the gallery on HDMI at boot

Layers, all on branch `wrynose` unless noted: openembedded-core, bitbake
(`2.18`), meta-openembedded (meta-oe, meta-python), meta-arm (meta-arm-toolchain,
meta-arm), meta-rockchip, meta-flutter and this repository's
`meta-tinker-flutter`.

## Requirements

- kas 5.5 or newer (configuration format 23), or Docker/Podman for
  `kas-container`
- a build host with plenty of disk: the Flutter engine build alone needs tens
  of gigabytes of sources and build tree, and the first build takes hours
- for flashing: `dd`, and optionally `bmap-tools`

## Build

```bash
kas build tinker-board-s-flutter.yml
```

Or inside the kas container:

```bash
kas-container build tinker-board-s-flutter.yml
```

Every upstream repository is pinned to a `commit` on its release branch, and
kas checks that the branch still contains it. To move to a newer commit, look
up the branch head and update the `commit` value in the kas file:

```bash
git ls-remote https://github.com/meta-flutter/meta-flutter refs/heads/wrynose
```

The build lands in `build/tmp/deploy/images/tinker-board-s/`:

- `core-image-base-tinker-board-s.rootfs.wic`: the full disk image. GPT,
  with idbloader, U-Boot and the root filesystem at the offsets meta-rockchip's
  `rockchip.wks` defines.
- `core-image-base-tinker-board-s.rootfs.wic.bmap`: block map for `bmaptool`.

The same image is written to the SD card and to the eMMC.

## Flashing

Always identify the target device with `lsblk` first and use the whole device
(`/dev/sdX`, `/dev/mmcblkN`), never a partition: `dd` overwrites without asking.

### SD card

```bash
IMG=build/tmp/deploy/images/tinker-board-s/core-image-base-tinker-board-s.rootfs.wic
sudo bmaptool copy "$IMG" /dev/sdX
# or, without bmap-tools
sudo dd if="$IMG" of=/dev/sdX bs=4M status=progress conv=fsync
```

Insert the card and power the board. The RK3288 boot ROM tries the eMMC first
and falls back to the SD card, so the card boots when the eMMC is empty or when
the maskrom jumper is set (see below).

### eMMC

The Tinker Board S has a 16 GB eMMC. Two things to know before starting:

- The jumper between the micro-USB and HDMI connectors has a **maskrom** and
  a **park** position. In maskrom position the eMMC is hidden from the boot
  ROM, which is how you force a board with a bootable eMMC to boot from the SD
  card. Put it back to park once U-Boot is running, otherwise Linux will not
  see the eMMC either.
- The serial console is UART2, 115200 8N1, `ttyS2` in Linux. On the 40-pin
  header it sits at the end away from pin 1: pin 32 is the board's TX (to the
  adapter's RX), pin 33 the board's RX (to the adapter's TX), pin 34 GND. Pins
  8 and 10, where a Raspberry Pi has its console, are UART1 on this board and
  carry nothing during boot.

**1. USB mass storage from U-Boot (micro-USB cable plus serial console)**

The U-Boot that meta-rockchip builds has the `ums` command on the micro-USB
port (`CONFIG_CMD_USB_MASS_STORAGE`, `CONFIG_USB_GADGET_DWC2_OTG`).

1. Write the image to an SD card as above and insert it.
2. Set the jumper to maskrom.
3. Connect the serial console and the micro-USB port to your PC, power on, and
   interrupt autoboot at the U-Boot prompt.
4. Set the jumper back to park, then:

   ```
   => mmc list
   => ums 0 mmc <n>
   ```

   `<n>` is the index `mmc list` reports for the eMMC, the device that is not
   the SD card. Device numbering differs between U-Boot and Linux, so do not
   assume it.
5. A new disk appears on the PC. Flash it exactly like the SD card
   (`bmaptool copy` or `dd` to the whole device), run `sync`, press Ctrl-C in
   U-Boot, power off and remove the SD card.

**2. From Linux booted off the SD card**

1. Boot the SD card image with the jumper in maskrom position and move it back
   to park as soon as U-Boot has started, so the kernel can see the eMMC.
2. Copy `core-image-base-tinker-board-s.rootfs.wic` to the board, for example
   with `scp`.
3. Find the eMMC with `lsblk -d -o NAME,SIZE,TYPE`: the 16 GB `mmcblk` device
   that is not the running SD card. Then:

   ```bash
   dd if=core-image-base-tinker-board-s.rootfs.wic of=/dev/mmcblkN bs=4M conv=fsync
   ```

4. Power off, remove the SD card, power on.

**3. Stock ASUS U-Boot, while the eMMC still holds TinkerOS**

With no SD card inserted, the TinkerOS U-Boot enters USB mass storage mode on
its own when the micro-USB port is connected to a PC: the eMMC shows up as a
disk and is flashed like the SD card. This only works until the eMMC is
overwritten; afterwards use method 1 or 2.

Sources: the [ASUS Tinker Board User Guide](https://github.com/TinkerBoard/TinkerBoard/wiki/User-Guide)
(SD card, UMS and maskrom jumper) and the former
[Home Assistant OS notes for the Tinker Board S](https://github.com/home-assistant/operating-system/blob/12.0/Documentation/boards/asus/README.md)
(jumper position and the U-Boot `ums` sequence).

## Automatic start

`meta-tinker-flutter/recipes-graphics/flutter-gallery-autostart/` installs
`flutter-gallery.service`, enabled by default, which runs:

```
/usr/bin/flutter-pi --release /usr/share/flutter/flutter-sdk-dev-integration-tests-flutter-gallery/<flutter version>/release
```

flutter-pi draws directly on DRM/KMS and reads `/dev/input` itself, so the image
has no compositor. Log in as `root` with no password on the serial console or
over SSH: the kas file sets `debug-tweaks`, which is meant for the bench and
must go before the image ships. On the board:

```bash
systemctl status flutter-gallery
journalctl -u flutter-gallery -b
systemctl stop flutter-gallery       # free the display, e.g. to run another bundle by hand
systemctl disable flutter-gallery    # keep it off across reboots
```

To ship another application, add its meta-flutter recipe to `IMAGE_INSTALL` and
point `FLUTTER_GALLERY_BUNDLE` in the autostart recipe at its bundle directory:
flutter-pi is built with meta-flutter's filesystem layout, so the bundle
argument is the directory that holds `data/flutter_assets` and `lib/libapp.so`.

## 32-bit note

The Tinker Board S is a 32-bit ARM board (Cortex-A17). meta-flutter skips all
Flutter application recipes on 32-bit targets, because `flutter build bundle`
only accepts 64-bit `--target-platform` values; the engine, `gen_snapshot` and
flutter-pi still build for armv7. `meta-tinker-flutter` carries a bbappend for
the gallery that re-enables the recipe and lets the Flutter tool bundle the
CPU independent assets as `linux-arm64`. meta-flutter then compiles the Dart
kernel and runs the armv7 `gen_snapshot` itself, so `libapp.so` is a real
32-bit AOT image. The same three lines work for any other application recipe
you want on this board.

## Kernel configuration

meta-rockchip builds linux-yocto from the generic `multi_v7_defconfig` and
then switches off every non-Rockchip platform. The defconfig relies on some of
those platforms to `select` options it never lists itself, so they disappear
with them: the fixed-voltage regulator driver, which every RK808 regulator on
the Tinker Board S depends on, VFP and NEON, PSCI, which the Rockchip
power-domain driver needs to become visible, HIGHMEM, the Realtek Ethernet
PHY and the generic EHCI/OHCI host drivers. Without them the kernel stops at
"Waiting for root device", and even with the root mounted every hard-float
binary would die and HDMI would stay dark.
`meta-tinker-flutter/recipes-kernel/linux/files/tinker-board-s.cfg` adds them
back as a config fragment.

## Power

The RK3288 draws its peak current when cpufreq takes the four cores to full
speed a few seconds into boot, together with the Ethernet PHY and the USB
ports coming up. A supply that only lights the red LED at idle resets the
board at that point: U-Boot then reports `Reset cause: POR` on the next pass.
Use 5 V at 3 A through a short cable. Until then the kas file passes
`cpufreq.off=1` on the kernel command line, which keeps the CPU at the
600 MHz U-Boot set; remove that line once the supply is right, the gallery is
noticeably slow without the full clock.

## Flutter development

- The Yocto SDK ships the Flutter SDK (`TOOLCHAIN_HOST_TASK` in the kas file):

  ```bash
  kas shell tinker-board-s-flutter.yml -c 'bitbake core-image-base -c populate_sdk'
  ```

- For the application workflow see the meta-flutter
  [recommended development flow](https://github.com/meta-flutter/meta-flutter#recommended-development-flow).

## Repository layout

```
tinker-board-s-flutter.yml                     kas project file
meta-tinker-flutter/                           local layer
layers/                                        upstream layers, cloned by kas (ignored)
  conf/layer.conf
  recipes-graphics/flutter-gallery-autostart/  systemd unit that starts flutter-pi
  recipes-graphics/flutter-sdk/apps/           32-bit bbappend for the gallery
  recipes-kernel/linux/                        kernel config fragment for the board
```
