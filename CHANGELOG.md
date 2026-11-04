# Changelog

All notable changes to Aozora Kernel Manager are documented here.

## Unreleased

### Security

- Authenticate daemon IPC peers with `SO_PEERCRED`; only uid 0 and the app uid
  are accepted, and the socket is created with mode 0660.
- Restrict the daemon working directory and app-owned `autd/` files to the app
  uid, with no world-writable paths.
- Disable Android backup and define backup and data-extraction exclusions.
- Stop exporting the toast and boot receivers, and bound toast text length.
- Require SHA-256 digest, package-name, and signing-certificate checks against
  the installed app before an update is installed with `pm install -r`.
- Quote and validate profile IDs, frequencies, governors, and shell arguments
  before they reach root commands.
- Verify a stale daemon PID against its command line and stop it with TERM
  before KILL.

### Added

- Length-prefixed IPC results carry the command exit code and remain compatible
  with older daemon builds.
- Configurable idle-drain alerts (0.5-10.0%/hr) delivered at most once per hour.
- Percentage-based battery stats reset with hysteresis.
- Failure feedback for cache cleaning, profile application, Quick Settings tile
  actions, and app updates.

### Changed

- Battery stats reset now runs through the daemon instead of deleting the stats
  file from the UI.
- Charge limits are re-applied after each deep-sleep wake.
- The battery logger validates JSON, rotates at 2 MiB, and writes app-owned
  files.
- Profiles run from the kernel helper when installed, otherwise from the
  bundled Aozora-Rupture engine.
- GPU control is documented and implemented as frequency-only on Tegra.
- Settings, Tweaks, Battery, and About use one shared ToggleCard with switch
  semantics for accessibility.
- The floating bottom navigation maps items to explicit pager indices.

### Removed

- The detached native APK installer; updates now use the verified in-app flow.
- Unused home status cards, the unused daemon notification helper, the default
  color palette, and the unreferenced Play Store icon.

### Fixed

- Graceful handling of AUTD event-pipe setup failures.
- MTK GPU power limits now write the expected `key value` pair.
- Kernel node writes report failures and restore original permissions.
- Removed iorenice and CPU0 taskset pinning from the HWUI tuning path.