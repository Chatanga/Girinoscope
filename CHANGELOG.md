# Changelog

## [1.3.1-SNAPSHOT]
### Added
- Slightly improved status messages.
- Reinstated the look and feel selection.
- Changelog popup at startup after an update.
### Fixed
- Some Snap issues.
- The badly supported Desktop API on Linux.

## [1.3.0]
### Added
- Snap support for the Linux folk.
- Support of multiple (hardcoded) devices (Arduino + STM32duino).
- Various settings (device, signal interpretation and theme) are now persisted.
- Added one frame acquisition action.
### Fixed
- Removed badly supported Substance LaF.
- Removed an inappropriate JDK 1.8 method usage.

## [1.2.2]
### Added
- A nicer Look and Feel and a logo.
### Changed
- Migrated to Java 7.
- Migrated the build process from Ant to Maven.
- Replaced the serial library by a better one.
- Introduced this Changelog.
### Fixed
- A regression on the serial connection status display.
- A couple of minor cosmetic bugs.

## [1.1.4]
### Added
- The Y axis could be customized with any value range and unit.
- A captured frame could be saved as a CSV file.
### Fixed
- Serial connection is now forcefully interrupted on shutdown.

## [1.1.2]
### Fixed
- Correct the Unix launch script.
