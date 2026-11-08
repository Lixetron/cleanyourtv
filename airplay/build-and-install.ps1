# Install the AirPlay receiver (jqssun/android-airplay-server, GPL-3, separate app) on the TV.
# Downloads the pinned official release, checks its SHA-256, installs and configures it:
# server name, start at boot, no Doze/background limits, then back to Home.
#
# Usage: powershell -ExecutionPolicy Bypass -File airplay\build-and-install.ps1 -TvHost <tv ip> [-Name "Mi TV 4S"]
param(
    [string]$TvHost = $env:TV_HOST,
    [string]$Name = '',
    [string]$Version = 'v0.0.33',
    [string]$Sha256 = '2203DCC76AD884265E6E01C2147C917F100E93F37B91BF728BB3EED466396B32'
)
$ErrorActionPreference = 'Stop'
if (-not $TvHost) { throw 'pass -TvHost <tv ip> or set $env:TV_HOST' }
$env:TV_HOST = $TvHost
$repo = Split-Path -Parent $PSScriptRoot
$py = Join-Path $repo 'tools\venv\Scripts\python.exe'
if (-not (Test-Path $py)) { throw 'run: python -m venv tools\venv; tools\venv\Scripts\python.exe -m pip install -r tools\requirements.txt' }
$env:OutputEncoding = New-Object System.Text.UTF8Encoding $false

$apk = Join-Path $PSScriptRoot "airplay-$Version.apk"
if (-not (Test-Path $apk)) {
    $url = "https://github.com/jqssun/android-airplay-server/releases/download/$Version/app-release.apk"
    Write-Host "downloading $url"
    Invoke-WebRequest -Uri $url -OutFile $apk -UseBasicParsing
}
$hash = (Get-FileHash $apk -Algorithm SHA256).Hash
if ($hash -ne $Sha256.ToUpperInvariant()) {
    throw "refusing ${apk}: SHA-256 $hash does not match the pinned $Sha256"
}
if (-not $Name) {
    $Name = (& $py -I (Join-Path $repo 'tools\tv.py') 'settings get global device_name') -join ''
    $Name = $Name.Trim()
    if (-not $Name) { $Name = 'Android TV' }
}

& $py -I (Join-Path $repo 'tools\b64push.py') $apk /data/local/tmp/airplay.apk
if ($LASTEXITCODE -ne 0) { throw 'APK upload failed' }

@"
pm install -r /data/local/tmp/airplay.apk
rm -f /data/local/tmp/airplay.apk
am start -n io.github.jqssun.airplay/.MainActivity
am broadcast -a io.github.jqssun.airplay.SET_SETTING -n io.github.jqssun.airplay/.SettingsReceiver --es key server_name --es value '$Name'
am broadcast -a io.github.jqssun.airplay.SET_SETTING -n io.github.jqssun.airplay/.SettingsReceiver --es key boot_auto_start --es value true
dumpsys deviceidle whitelist +io.github.jqssun.airplay
cmd appops set io.github.jqssun.airplay RUN_IN_BACKGROUND allow
input keyevent KEYCODE_HOME
"@ | & $py -I (Join-Path $repo 'tools\tv.py') --batch
if ($LASTEXITCODE -ne 0) { throw 'the install or one of the settings failed; run the script again once the TV answers' }
Write-Host "Installed. The TV shows up as `"$Name`" in Screen Mirroring on iPhone/iPad/Mac."
