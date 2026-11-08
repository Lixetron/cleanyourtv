# Personal fork of CleanYourTV

This checkout is a fork of [Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv) for the
Xiaomi Mi TV 4S (MiTV-MSSP1, `nino`, Android TV 9). It keeps the upstream launcher light and adds the
widget cards and their editor from the [Liquid Glass fork](https://github.com/Nefritor/cleanyourtv-liquid-glass),
plus the weather line under the clock.

## What this fork adds

- `com.gathin.widgets` (Widgets, Cards, Data, Astro, BlurGlass, Arrange, WidgetSettings) copied from
  the Liquid Glass fork's `shared/widgets`.
- `HomeWeather` and the `homeWeather` line under the clock (MET Norway, the TV's rough location).
- A 4 × 2 widget layer on Home: weather, world clocks, moon and sun by default, faded out as the
  app grid scrolls up. Started and stopped with Home.
- The cards editor: Launcher settings → **Widgets** (add, move, resize, remove, per-card options).
  It opens over the real Home, so the cards stand where they will.
- `INTERNET`, `ACCESS_COARSE_LOCATION`; `ACCESS_FINE_LOCATION` is granted by the install script.
- `personal/theme-widgets`: the dock, five columns, the static aurora wallpaper, `power_on_home` on,
  `show_stats` off (the weather line takes that place).

Everything else is upstream. The launcher still has no background services and no polling; the
network is read only while a weather card or the weather line is on screen.

## Build and install

JDK 17 and Android SDK 34 (platform 34, build-tools 34.0.0), then:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Amazon Corretto\jdk17.0.16_8'
$env:ANDROID_HOME = 'S:\projects\lixetron\clean-tv\.tools\android-sdk'
python -m venv tools\venv
tools\venv\Scripts\python.exe -m pip install -r tools\requirements.txt

powershell -ExecutionPolicy Bypass -File launcher\build-and-install.ps1 -TvHost <tv ip> `
    -HomeKeyService -RestoreKeyServices
powershell -ExecutionPolicy Bypass -File tools\theme.ps1 -TvHost <tv ip> personal\theme-widgets
```

`launcher/app/src/main/java/com/gathin/tvhome/MainActivity.java` → `widgetSurface()` holds the grid
size, its place on screen (418 / 112 dp) and the default cards; `columns` in the theme is the dock.

## AirPlay

`airplay/` installs the separate GPL-3 receiver
[jqssun/android-airplay-server](https://github.com/jqssun/android-airplay-server) (`io.github.jqssun.airplay`,
pinned `v0.0.33`, SHA-256 checked) as a background service: the TV appears in iOS Control Center →
Screen Mirroring as "Mi TV 4S", starts with the TV, and is exempt from Doze and background limits.
Only the MIT script lives here; the APK is downloaded from the official release and is not committed.

```powershell
powershell -ExecutionPolicy Bypass -File airplay\build-and-install.ps1 -TvHost <tv ip> -Name "Mi TV 4S"
```

Verified on the Mi TV 4S: auto-start after a reboot, TCP 7000 listening, mDNS `_airplay._tcp` and
`_raop._tcp` answering. Screen mirroring and audio work; DRM video (Netflix, Apple TV+) does not,
because FairPlay cannot be decrypted.

## Keeping up with upstream

```bash
git remote add upstream https://github.com/Gathin23/cleanyourtv.git   # already added here
git fetch upstream
git merge upstream/main        # on this branch, resolve conflicts in MainActivity by hand
```

## Publishing this fork

The checkout already has the full upstream history. Create an empty repository on GitHub (no README,
no licence), then:

```bash
git remote add origin https://github.com/<you>/<repo>.git
git push -u origin personal
```

`origin` is free: this checkout's remote is named `upstream`.
