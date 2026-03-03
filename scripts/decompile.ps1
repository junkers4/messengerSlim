[CmdletBinding()]
param(
  [string]$Version = '550.0.0.45.63'
)

$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$inputApk = Join-Path $root "input/messenger-$Version.apk"
$outDir = Join-Path $root 'work/decompiled'

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

if (-not (Test-Path $inputApk)) {
  throw "Input APK not found: $inputApk"
}

if (Test-Path $outDir) {
  Write-Host "Removing existing decompiled folder: $outDir" -ForegroundColor Yellow
  Remove-Item -Recurse -Force $outDir
}

Write-Host 'Running apktool decompile...' -ForegroundColor Cyan
$apktoolArgs = @('d', "$inputApk", '-o', "$outDir")
Invoke-Apktool @apktoolArgs

Write-Host "Done. Decompiled to: $outDir" -ForegroundColor Green
