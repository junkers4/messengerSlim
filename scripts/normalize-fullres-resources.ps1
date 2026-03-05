[CmdletBinding()]
param(
  [string]$ResRoot = "work/decompiled_fullres_probe/res"
)

$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$resPath = Join-Path $root $ResRoot

if (-not (Test-Path $resPath)) {
  throw "Resource path not found: $resPath"
}

Write-Host "Normalizing .2 resource types under: $resPath" -ForegroundColor Cyan

# 1) Normalize values XML tag names and references (@color.2/ -> @color/, ?attr.2/ -> ?attr/)
$valuesDirs = Get-ChildItem -Path $resPath -Directory | Where-Object { $_.Name -like 'values*' }
foreach ($dir in $valuesDirs) {
  $xmlFiles = Get-ChildItem -Path $dir.FullName -File -Filter '*.xml' -ErrorAction SilentlyContinue
  foreach ($file in $xmlFiles) {
    $content = Get-Content -Raw -Encoding UTF8 $file.FullName

    $updated = $content
    # opening/closing tags like <color.2 ...> and </color.2>
    $updated = [regex]::Replace($updated, '<([a-zA-Z_][a-zA-Z0-9_]*)\.[0-9]+(\s|>)', '<$1$2')
    $updated = [regex]::Replace($updated, '</([a-zA-Z_][a-zA-Z0-9_]*)\.[0-9]+>', '</$1>')

    # resource refs: @type.2/name or ?type.2/name
    $updated = [regex]::Replace($updated, '@([a-zA-Z_][a-zA-Z0-9_]*)\.[0-9]+/', '@$1/')
    $updated = [regex]::Replace($updated, '\?([a-zA-Z_][a-zA-Z0-9_]*)\.[0-9]+/', '?$1/')

    # in public.xml-like attributes type="drawable.2"
    $updated = [regex]::Replace($updated, 'type="([a-zA-Z_][a-zA-Z0-9_]*)\.[0-9]+"', 'type="$1"')

    if ($updated -ne $content) {
      Set-Content -Path $file.FullName -Value $updated -Encoding UTF8
    }
  }
}

# 2) Normalize non-values XML references in layout/menu/etc
$otherXmls = Get-ChildItem -Path $resPath -Recurse -File -Filter '*.xml' |
  Where-Object { $_.FullName -notmatch '\\values[^\\]*\\' }

foreach ($file in $otherXmls) {
  $content = Get-Content -Raw -Encoding UTF8 $file.FullName
  $updated = $content
  $updated = [regex]::Replace($updated, '@([a-zA-Z_][a-zA-Z0-9_]*)\.[0-9]+/', '@$1/')
  $updated = [regex]::Replace($updated, '\?([a-zA-Z_][a-zA-Z0-9_]*)\.[0-9]+/', '?$1/')

  if ($updated -ne $content) {
    Set-Content -Path $file.FullName -Value $updated -Encoding UTF8
  }
}

Write-Host "Normalization finished." -ForegroundColor Green
