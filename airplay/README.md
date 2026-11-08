# AirPlay receiver for the TV (iPhone / iPad / Mac)

An ad-free background AirPlay receiver, so the TV shows up in iOS Control Center → Screen Mirroring.
It is the separate GPL-3 app [jqssun/android-airplay-server](https://github.com/jqssun/android-airplay-server)
(package `io.github.jqssun.airplay`, built on the UxPlay protocol core). This folder only holds the
MIT install script and no GPL code; the APK is downloaded from the project's official GitHub release.

```powershell
powershell -ExecutionPolicy Bypass -File airplay\build-and-install.ps1 -TvHost <tv ip> [-Name "Mi TV 4S"]
```

What the script does:

- downloads the pinned release (`v0.0.33`) and checks its SHA-256;
- installs it over ADB (`pm install -r`, never uninstalled by the debloat lists);
- names the receiver after `-Name`, or the TV's `device_name` when the option is left out;
- turns on start at boot, exempts the app from Doze and background limits, launches it once so
  `BOOT_COMPLETED` reaches it, and returns to Home.

Verified on the Mi TV 4S (MiTV-MSSP1, Android 9): the receiver auto-starts after a reboot, listens on
TCP 7000, and answers mDNS `_airplay._tcp` as the configured name.

What works: screen mirroring (H.264/H.265), audio (AAC/ALAC), video playback. What does not: DRM
content (Netflix, Apple TV+, Disney+), because Apple's FairPlay video DRM cannot be decrypted.

## Settings over ADB

```powershell
# list every setting with its value
tools\venv\Scripts\python.exe -I tools\tv.py "am broadcast -a io.github.jqssun.airplay.LIST_SETTINGS -n io.github.jqssun.airplay/.SettingsReceiver"
# e.g. require a PIN
tools\venv\Scripts\python.exe -I tools\tv.py "am broadcast -a io.github.jqssun.airplay.SET_SETTING -n io.github.jqssun.airplay/.SettingsReceiver --es key require_pin --es value true"
```

## Remove

```powershell
tools\venv\Scripts\python.exe -I tools\tv.py "pm uninstall io.github.jqssun.airplay"
```

The app is GPL-3; its source and releases are at the link above. Nothing here changes the launcher.
