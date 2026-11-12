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
- The tile menu's **Move** places that one app and ends reorder mode, so a press opens apps and a hold
  opens the menu again; **Reorder apps** keeps the multi-app session.
- The App library opens any app on a press (`library_ok_opens`): hold OK offers Open, Add to Home (or
  Remove from Home) and App info, whether or not the app is already on Home.
- Launcher settings → **Clock screensaver** picks Off or 1–60 idle minutes before the clock screensaver
  starts (`home_idle_clock_minutes`), instead of editing `config.xml`.
- `INTERNET`, `ACCESS_COARSE_LOCATION`; `ACCESS_FINE_LOCATION` is granted by the install script.
- `personal/theme-widgets`: the dock, five columns, the static aurora wallpaper, `power_on_home` on,
  `show_stats` off (the weather line takes that place), `icon_only_buttons`, `wifi_status_color` and
  `fade_rows` (rows scrolling up under the clock fade out instead of being cut off there).
- The Wi-Fi pill turns green while the TV is online and stays white when it is not, and the connected
  network's name sits in green above the pills, on the greeting's line (`wifiName`). While a VPN is up, a
  small green shield sits before that name.
- `wifi_status_warnings`: connected without internet — the Wi-Fi pill and the network's name turn orange,
  and an orange warning triangle joins the name. The `fix_time_on_wake` probe shows a small spinner there
  while it asks the weather host, then a check that fades out, or the triangle when no answer comes.
- `fix_time_on_wake`: the Mi TV 4S loses the date on every boot (no working RTC battery), which with strict
  Private DNS leaves it "connected" with no internet until its own NTP sync gets through; and after a long
  standby Android can keep the stale network marked as connected. Home waits `fix_time_delay_seconds` (5 s
  in this theme; 5, 10, 15, 20 or 30 in Launcher settings) after a wake or boot, re-arms `auto_time` where
  the install script granted `WRITE_SECURE_SETTINGS`, and if the clock is wrong or a real request to the
  weather host fails (or does not answer within 5 seconds, since DNS ignores socket timeouts), restarts
  Wi-Fi once — the same switch that fixes it by hand. Off by default; this fork's theme turns it on.
- On Home, holding the Xiaomi MI/PatchWall button (the XMRM-007 remote's, `KEYCODE_BUTTON_4`/191) opens the
  Inputs menu; a tap still opens Live TV/last source. The helper runs from `InputKeyService`, which the
  install script now enables (`-InputKeyService`), and the keycode list is `hold_input_keycodes` in
  `config.xml`.

Everything else is upstream. The launcher still has no background services and no polling; the
network is read only while a weather card or the weather line is on screen.

## Build and install

JDK 17 and Android SDK 34 (platform 34, build-tools 34.0.0), then:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Amazon Corretto\jdk17.0.16_8'
$env:ANDROID_HOME = 'C:\android-tools\android-sdk'
python -m venv tools\venv
tools\venv\Scripts\python.exe -m pip install -r tools\requirements.txt

powershell -ExecutionPolicy Bypass -File launcher\build-and-install.ps1 -TvHost <tv ip> `
    -HomeKeyService -InputKeyService -RestoreKeyServices
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
git merge upstream/main        # on this branch: themes/README.md conflicts (this fork's hold_input_keycodes
                               # row beside upstream's rewritten app_keys row); keep both rows. Earlier merges
                               # also conflicted in InputKeyService.java: keep both the hold block and upstream's
                               # app_keys front: entries
```

## Publishing this fork

The fork lives at [Lixetron/cleanyourtv](https://github.com/Lixetron/cleanyourtv). `origin` is the
fork, `upstream` is Gathin23's repository, `main` mirrors `upstream/main` and the work lives on
`personal`:

```bash
git checkout personal
git push -u origin personal
```

GitHub still opens the fork on `main`, which matches upstream. Pick `personal` as the default branch
under **Settings → Branches** to see the changes first.
