# Changelog

## [1.4.0]
### Added
- New Girino variant with automatic trigger and multichannels (SoftGirino).

## [1.3.5]
### Fixed
- A potential deadlock when talking too fast with a Girino device (#6).

## [1.3.4]
### Fixed
- An obvious regression in displayed frequency units introduced in 1.3.0.

## [1.3.3]
### Added
- Toolbar shortcuts for the acquisition rate and the trigger mode.
- The serial port list is now updated each time the corresponding menu is open.
### Changed
- Switched to JDK 1.8 to accomodate the markdown plugin for Maven.

## [1.3.2]
### Changed
- Export to CSV in a single column without the index.
- Stop filtering out serial ports based on their name.
### Fixed
- Removed unneeded build dependencies from the distributed JAR.
- Data stroke width is now applied starting with the current frame.
- Synchronously load the content for the about dialog.

## [1.3.1]
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
