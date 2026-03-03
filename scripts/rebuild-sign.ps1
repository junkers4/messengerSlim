[CmdletBinding()]
param(
  [string]$Version = '550.0.0.45.63',
  [string]$KeystorePath = "$env:USERPROFILE\.android\debug.keystore",
  [string]$KeyAlias = 'androiddebugkey',
  [string]$StorePass = 'android',
  [string]$KeyPass = 'android'
)

$ErrorActionPreference = 'Stop'

function Resolve-ToolCommand {
  param(
    [Parameter(Mandatory = $true)]
    [string]$CommandName,
    [Parameter(Mandatory = $true)]
    [string]$FallbackFileName
  )

  $cmd = Get-Command $CommandName -ErrorAction SilentlyContinue
  if ($cmd) {
    return $cmd.Source
  }

  $sdkRoot = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
  $buildToolsRoot = Join-Path $sdkRoot 'build-tools'
  if (Test-Path $buildToolsRoot) {
    $fallback = Get-ChildItem $buildToolsRoot -Directory -ErrorAction SilentlyContinue |
      Sort-Object Name -Descending |
      ForEach-Object { Join-Path $_.FullName $FallbackFileName } |
      Where-Object { Test-Path $_ } |
      Select-Object -First 1

    if ($fallback) {
      return $fallback
    }
  }

  throw "Unable to resolve tool '$CommandName'. Not found in PATH and no SDK fallback found for '$FallbackFileName'."
}

function Invoke-Apktool {
  param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$Arguments
  )

  $apktoolCmd = Get-Command 'apktool' -ErrorAction SilentlyContinue
  if ($apktoolCmd) {
    & $apktoolCmd.Source @Arguments
    return
  }

  $localBat = Join-Path $root 'tools\apktool.bat'
  if (Test-Path $localBat) {
    & $localBat @Arguments
    return
  }

  $localJar = Join-Path $root 'tools\apktool.jar'
  if (Test-Path $localJar) {
    & java -jar $localJar @Arguments
    return
  }

  $rootJar = Get-ChildItem -Path $root -Filter 'apktool*.jar' -File -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
  if ($rootJar) {
    & java -jar $rootJar.FullName @Arguments
    return
  }

  $parentDir = Split-Path $root -Parent
  if ($parentDir) {
    $parentJar = Get-ChildItem -Path $parentDir -Filter 'apktool*.jar' -File -ErrorAction SilentlyContinue |
      Sort-Object LastWriteTime -Descending |
      Select-Object -First 1
    if ($parentJar) {
      & java -jar $parentJar.FullName @Arguments
      return
    }
  }

  throw "apktool not found. Add it to PATH, place tools/apktool.bat (or tools/apktool.jar), or place apktool*.jar in this folder or its parent."
}

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$srcDir = Join-Path $root 'work/decompiled'
$unsignedApk = Join-Path $root "output/messenger-$Version-unsigned.apk"
$alignedApk = Join-Path $root "output/messenger-$Version-aligned.apk"
$signedApk = Join-Path $root "output/messenger-$Version-signed.apk"
$zipalignCmd = Resolve-ToolCommand -CommandName 'zipalign' -FallbackFileName 'zipalign.exe'
$apksignerCmd = Resolve-ToolCommand -CommandName 'apksigner' -FallbackFileName 'apksigner.bat'

if (-not (Test-Path $srcDir)) {
  throw "Decompiled source not found: $srcDir"
}

Write-Host 'Building APK from decompiled sources...' -ForegroundColor Cyan
$apktoolArgs = @('b', "$srcDir", '-o', "$unsignedApk")
Invoke-Apktool @apktoolArgs
if ($LASTEXITCODE -ne 0 -or -not (Test-Path $unsignedApk)) {
  throw "APK build failed. Unsigned output not created: $unsignedApk"
}

Write-Host 'Aligning APK...' -ForegroundColor Cyan
if (Test-Path $alignedApk) { Remove-Item -Force $alignedApk }
& $zipalignCmd -f 4 "$unsignedApk" "$alignedApk"
if ($LASTEXITCODE -ne 0 -or -not (Test-Path $alignedApk)) {
  throw "zipalign failed. Aligned output not created: $alignedApk"
}

if (-not (Test-Path $KeystorePath)) {
  Write-Host "Keystore not found, generating debug keystore at: $KeystorePath" -ForegroundColor Yellow
  $keytool = Get-Command keytool -ErrorAction SilentlyContinue
  if (-not $keytool) {
    throw "Keystore not found and keytool is unavailable in PATH. Missing: $KeystorePath"
  }
  $ksDir = Split-Path $KeystorePath -Parent
  if (-not (Test-Path $ksDir)) { New-Item -ItemType Directory -Path $ksDir | Out-Null }
  & $keytool.Source -genkeypair -v -keystore "$KeystorePath" -storepass "$StorePass" -alias "$KeyAlias" -keypass "$KeyPass" -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
  if ($LASTEXITCODE -ne 0 -or -not (Test-Path $KeystorePath)) {
    throw "Failed to generate debug keystore: $KeystorePath"
  }
}

Write-Host 'Signing APK...' -ForegroundColor Cyan
if (Test-Path $signedApk) { Remove-Item -Force $signedApk }
Copy-Item "$alignedApk" "$signedApk"

& $apksignerCmd sign `
  --ks "$KeystorePath" `
  --ks-key-alias "$KeyAlias" `
  --ks-pass "pass:$StorePass" `
  --key-pass "pass:$KeyPass" `
  "$signedApk"
if ($LASTEXITCODE -ne 0) {
  throw "apksigner sign failed for: $signedApk"
}

Write-Host 'Verifying signature...' -ForegroundColor Cyan
& $apksignerCmd verify "$signedApk"
if ($LASTEXITCODE -ne 0) {
  throw "apksigner verify failed for: $signedApk"
}

Write-Host "Done: $signedApk" -ForegroundColor Green
