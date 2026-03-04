[CmdletBinding()]
param(
  [Parameter(Mandatory = $true)]
  [string]$SourceImage,
  [string]$WorkDir = "work/decompiled_nres"
)

$ErrorActionPreference = 'Stop'

Add-Type -AssemblyName System.Drawing
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$base = Join-Path $root $WorkDir

if (-not (Test-Path $base)) {
  throw "Work directory not found: $base"
}

$srcPath = if ([System.IO.Path]::IsPathRooted($SourceImage)) { $SourceImage } else { Join-Path $root $SourceImage }
if (-not (Test-Path $srcPath)) {
  throw "Source image not found: $srcPath"
}

$targets = @(
  (Join-Path $base 'res/eqn.png'),
  (Join-Path $base 'res/eqq.png')
)

foreach ($target in $targets) {
  if (-not (Test-Path $target)) {
    throw "Target launcher resource not found: $target"
  }
}

function Resize-CoverAndSavePng {
  param(
    [Parameter(Mandatory = $true)][string]$InputPath,
    [Parameter(Mandatory = $true)][string]$OutputPath,
    [Parameter(Mandatory = $true)][int]$OutW,
    [Parameter(Mandatory = $true)][int]$OutH
  )

  $src = [System.Drawing.Bitmap]::new($InputPath)
  try {
    $scale = [Math]::Max($OutW / $src.Width, $OutH / $src.Height)
    $scaledW = [int][Math]::Ceiling($src.Width * $scale)
    $scaledH = [int][Math]::Ceiling($src.Height * $scale)

    $tmp = [System.Drawing.Bitmap]::new($scaledW, $scaledH)
    try {
      $g = [System.Drawing.Graphics]::FromImage($tmp)
      try {
        $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
        $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
        $g.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
        $g.Clear([System.Drawing.Color]::Transparent)
        $g.DrawImage($src, 0, 0, $scaledW, $scaledH)
      }
      finally {
        $g.Dispose()
      }

      $dst = [System.Drawing.Bitmap]::new($OutW, $OutH)
      try {
        $g2 = [System.Drawing.Graphics]::FromImage($dst)
        try {
          $g2.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
          $g2.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
          $g2.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
          $g2.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
          $g2.Clear([System.Drawing.Color]::Transparent)
          $x = -[int](($scaledW - $OutW) / 2)
          $y = -[int](($scaledH - $OutH) / 2)
          $g2.DrawImage($tmp, $x, $y)
        }
        finally {
          $g2.Dispose()
        }

        $dst.Save($OutputPath, [System.Drawing.Imaging.ImageFormat]::Png)
      }
      finally {
        $dst.Dispose()
      }
    }
    finally {
      $tmp.Dispose()
    }
  }
  finally {
    $src.Dispose()
  }
}

foreach ($target in $targets) {
  $img = [System.Drawing.Image]::FromFile($target)
  try {
    $w = $img.Width
    $h = $img.Height
  }
  finally {
    $img.Dispose()
  }

  Resize-CoverAndSavePng -InputPath $srcPath -OutputPath $target -OutW $w -OutH $h
  Write-Host "Updated launcher layer: $target ($w x $h)" -ForegroundColor Green
}

Write-Host "Custom icon applied for $WorkDir from $srcPath" -ForegroundColor Cyan
