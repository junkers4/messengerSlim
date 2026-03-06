[CmdletBinding()]
param(
  [string]$WorkDir = "work/decompiled_nres",
  [string[]]$Targets = @('34.png','3u.png','24.png','4c.png','bbu.png','3j.png')
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$resPath = Join-Path $root "$WorkDir/res"

if (-not (Test-Path $resPath)) {
  throw "Resource path not found: $resPath"
}

foreach ($name in $Targets) {
  $path = Join-Path $resPath $name
  if (-not (Test-Path $path)) {
    Write-Warning "Missing asset: $path"
    continue
  }

  $image = [System.Drawing.Image]::FromFile($path)
  $width = $image.Width
  $height = $image.Height
  $image.Dispose()

  $backupPath = "$path.bak_v8ui"
  if (-not (Test-Path $backupPath)) {
    Copy-Item $path $backupPath -Force
  }

  $bitmap = New-Object System.Drawing.Bitmap($width, $height)
  $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
  $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality

  $rect = New-Object System.Drawing.Rectangle(0, 0, $width, $height)
  $backgroundBrush = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
    $rect,
    [System.Drawing.Color]::FromArgb(255, 10, 12, 22),
    [System.Drawing.Color]::FromArgb(255, 19, 23, 37),
    60
  )
  $graphics.FillRectangle($backgroundBrush, $rect)
  $backgroundBrush.Dispose()

  $glow1 = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(70, 90, 130, 255))
  $glow2 = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(55, 0, 230, 200))
  $graphics.FillEllipse($glow1, [int]($width * 0.05), [int]($height * 0.08), [int]($width * 0.55), [int]($height * 0.28))
  $graphics.FillEllipse($glow2, [int]($width * 0.42), [int]($height * 0.52), [int]($width * 0.40), [int]($height * 0.22))
  $glow1.Dispose()
  $glow2.Dispose()

  $linePen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(120, 130, 170, 255), [single]([Math]::Max(3, $width / 220)))
  for ($index = 0; $index -lt 4; $index++) {
    $y = [int]($height * (0.18 + $index * 0.06))
    $graphics.DrawLine($linePen, [int]($width * 0.12), $y, [int]($width * 0.88), [int]($y + $height * 0.06))
  }
  $linePen.Dispose()

  $panelBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(210, 15, 18, 28))
  $graphics.FillRectangle($panelBrush, [int]($width * 0.08), [int]($height * 0.72), [int]($width * 0.84), [int]($height * 0.14))
  $panelBrush.Dispose()

  $titleSize = [single]([Math]::Max(24, [Math]::Round($width / 18)))
  $titleFont = New-Object System.Drawing.Font('Segoe UI', $titleSize, [System.Drawing.FontStyle]::Bold)
  $subFont = New-Object System.Drawing.Font('Segoe UI', [single]([Math]::Max(12, $titleSize * 0.38)), [System.Drawing.FontStyle]::Regular)
  $stringFormat = New-Object System.Drawing.StringFormat
  $stringFormat.Alignment = [System.Drawing.StringAlignment]::Center

  $titleBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(245, 245, 248, 255))
  $subBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(185, 180, 200, 230))
  $graphics.DrawString('Spravaren', $titleFont, $titleBrush, [single]($width / 2), [single]($height * 0.735), $stringFormat)
  $graphics.DrawString('dark mode', $subFont, $subBrush, [single]($width / 2), [single]($height * 0.81), $stringFormat)

  $titleBrush.Dispose()
  $subBrush.Dispose()
  $titleFont.Dispose()
  $subFont.Dispose()
  $stringFormat.Dispose()
  $graphics.Dispose()

  $bitmap.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
  $bitmap.Dispose()

  Write-Host "Restyled: $name ($width x $height)" -ForegroundColor Green
}

Write-Host 'Cool UI v2 assets applied.' -ForegroundColor Cyan
