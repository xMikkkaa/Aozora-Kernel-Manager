# Aozora Kernel Manager

Simple Android kernel manager and system tuner built with Flutter.

## Features
- **Tuning Profiles**: Power Save, Balance, Gaming, Performance.
- **System Tweaks**: RAM cleaner, fstrim, bypass charging.
- **App Manager**: Per-app profile switching (requires daemon).
- **Dashboard**: Real-time system stats (CPU, RAM, Battery, etc).

## Requirements
- Root access (Magisk, KernelSU, or APatch).
- [**Aozora Kernel Helper**](https://t.me/KaiProject2/1077) installed for Binary and modified Powerhal.

## Notes
To use the App Manager and background services, the [**autd**](https://github.com/xMikkkaa/Automation-Daemon) binary must be present in `/system/bin/`. Without it, the app works in basic mode (manual profile switching only).

## Disclaimer
I am not responsible for bricked devices or dead SD cards. Use at your own risk.
