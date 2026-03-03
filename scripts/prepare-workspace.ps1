[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

function Get-BuildToolPath {
  param(
    [Parameter(Mandatory = $true)]
    [string]$FileName
  )

  $sdkRoot = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
  $buildToolsRoot = Join-Path $sdkRoot 'build-tools'
  if (-not (Test-Path $buildToolsRoot)) {
    return $null
  }

  $tool = Get-ChildItem $buildToolsRoot -Directory -ErrorAction SilentlyContinue |
    Sort-Object Name -Descending |
    ForEach-Object { Join-Path $_.FullName $FileName } |
    Where-Object { Test-Path $_ } |
    Select-Object -First 1

  return $tool
}

Write-Host 'Preparing local workspace...' -ForegroundColor Cyan

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$parentDir = Split-Path $root -Parent
$dirs = @(
  (Join-Path $root 'input'),
  (Join-Path $root 'output'),
  (Join-Path $root 'work')
)

foreach ($d in $dirs) {
  if (-not (Test-Path $d)) {
    New-Item -ItemType Directory -Path $d | Out-Null
    Write-Host "Created: $d"
  }
}

$tools = @('java', 'apktool', 'zipalign', 'apksigner')
foreach ($t in $tools) {
  $cmd = Get-Command $t -ErrorAction SilentlyContinue
  if ($null -eq $cmd) {
    if ($t -eq 'apktool') {
      $localBat = Join-Path $root 'tools\apktool.bat'
      $localJar = Join-Path $root 'tools\apktool.jar'
      $rootJar = Get-ChildItem -Path $root -Filter 'apktool*.jar' -File -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
      $parentJar = $null
      if ($parentDir) {
        $parentJar = Get-ChildItem -Path $parentDir -Filter 'apktool*.jar' -File -ErrorAction SilentlyContinue |
          Sort-Object LastWriteTime -Descending |
          Select-Object -First 1
      }
      if (Test-Path $localBat) {
        Write-Host "OK (local): apktool -> $localBat" -ForegroundColor Green
      } elseif (Test-Path $localJar) {
        Write-Host "OK (local): apktool -> java -jar $localJar" -ForegroundColor Green
      } elseif ($rootJar) {
        Write-Host "OK (local): apktool -> java -jar $($rootJar.FullName)" -ForegroundColor Green
      } elseif ($parentJar) {
        Write-Host "OK (parent): apktool -> java -jar $($parentJar.FullName)" -ForegroundColor Green
      } else {
        Write-Warning "Tool not found in PATH/local tools/parent folder: $t"
      }
    } elseif ($t -eq 'zipalign') {
      $fallback = Get-BuildToolPath -FileName 'zipalign.exe'
      if ($fallback) {
        Write-Host "OK (SDK): zipalign -> $fallback" -ForegroundColor Green
      } else {
        Write-Warning "Tool not found in PATH/SDK: $t"
      }
    } elseif ($t -eq 'apksigner') {
      $fallback = Get-BuildToolPath -FileName 'apksigner.bat'
      if ($fallback) {
        Write-Host "OK (SDK): apksigner -> $fallback" -ForegroundColor Green
      } else {
        Write-Warning "Tool not found in PATH/SDK: $t"
      }
    } else {
      Write-Warning "Tool not found in PATH: $t"
    }
  } else {
    Write-Host "OK: $t -> $($cmd.Source)" -ForegroundColor Green
  }
}

Write-Host 'Workspace ready.' -ForegroundColor Green
