[CmdletBinding()]
param(
  [string]$Version = '550.0.0.45.63',
  [string]$PackageName = 'com.facebook.orca'
)

$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$apkPath = Join-Path $root "input/messenger-$Version.apk"

if (-not (Test-Path $apkPath)) {
  throw "APK not found: $apkPath"
}

$aapt = Join-Path $env:LOCALAPPDATA 'Android\Sdk\build-tools\35.0.0\aapt.exe'
if (-not (Test-Path $aapt)) {
  throw "aapt not found: $aapt"
}

$badging = & $aapt dump badging $apkPath
$pkgLine = $badging | Select-String -Pattern "^package:"
if (-not $pkgLine) {
  throw 'Unable to read APK metadata via aapt.'
}

$pkgText = $pkgLine.ToString()
$foundPackage = if ($pkgText -match "name='([^']+)'") { $matches[1] } else { '' }
$foundVersion = if ($pkgText -match "versionName='([^']+)'") { $matches[1] } else { '' }

Write-Host "Detected package: $foundPackage"
Write-Host "Detected version: $foundVersion"

if ($foundPackage -ne $PackageName) {
  throw "Unexpected package. Expected '$PackageName', found '$foundPackage'."
}

if ($foundVersion -ne $Version) {
  throw "Unexpected version. Expected '$Version', found '$foundVersion'."
}

Write-Host 'APK verification OK.' -ForegroundColor Green
