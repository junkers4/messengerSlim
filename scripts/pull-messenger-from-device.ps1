[CmdletBinding()]
param(
  [string]$ExpectedVersion = '550.0.0.45.63',
  [string]$PackageName = 'com.facebook.orca'
)

$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$inputDir = Join-Path $root 'input'
New-Item -ItemType Directory -Force -Path $inputDir | Out-Null

$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
if (-not (Test-Path $adb)) {
  throw "ADB not found at: $adb"
}

Write-Host 'Checking connected Android devices...' -ForegroundColor Cyan
& $adb start-server | Out-Null
$deviceLines = (& $adb devices) | Select-Object -Skip 1 | Where-Object { $_ -match "\tdevice$" }
if (-not $deviceLines) {
  throw 'No authorized Android device found. Connect phone (USB), enable Developer options + USB debugging, and accept RSA prompt.'
}

Write-Host "Reading app version for $PackageName..." -ForegroundColor Cyan
$versionRaw = & $adb shell dumpsys package $PackageName | Select-String -Pattern 'versionName=' | Select-Object -First 1
if (-not $versionRaw) {
  throw "Package not found on device: $PackageName"
}
$versionName = ($versionRaw.ToString() -replace '.*versionName=', '').Trim()
Write-Host "Detected version: $versionName"
if ($versionName -ne $ExpectedVersion) {
  throw "Wrong Messenger version. Expected: $ExpectedVersion, found: $versionName"
}

$pmPath = & $adb shell pm path $PackageName
$baseLine = $pmPath | Where-Object { $_ -match '^package:.*/base\.apk$' } | Select-Object -First 1
if (-not $baseLine) {
  $baseLine = $pmPath | Select-Object -First 1
}
if (-not $baseLine) {
  throw 'Could not resolve APK path from device.'
}

$remoteApk = ($baseLine -replace '^package:', '').Trim()
$outApk = Join-Path $inputDir "messenger-$ExpectedVersion.apk"

Write-Host "Pulling APK from device: $remoteApk" -ForegroundColor Cyan
& $adb pull $remoteApk $outApk

if (-not (Test-Path $outApk)) {
  throw "Pull failed, file not created: $outApk"
}

Write-Host "Done: $outApk" -ForegroundColor Green
Write-Host 'Note: if app uses split APKs, this pulls base APK (sufficient for static analysis; full reinstall may need splits).' -ForegroundColor Yellow
