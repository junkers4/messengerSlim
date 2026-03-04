[CmdletBinding()]
param(
  [string]$WorkDir = "work/decompiled_nres"
)

$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$base = Join-Path $root $WorkDir

if (-not (Test-Path $base)) {
  throw "Work directory not found: $base"
}

$targets = @(
  'smali_classes6/com/facebook/messaging/aibot/launcher/activity/AiStudioFragmentActivity.smali',
  'smali_classes6/com/facebook/messaging/aibot/launcher/activity/AiBotImmersiveThreadActivity.smali',
  'smali_classes6/com/facebook/xapp/messaging/composer/aibot/discover/activity/AiBotDiscoverActivity.smali',
  'smali_classes6/com/facebook/messaging/aibot/memory/activity/ManageAiMemorySettingsActivity.smali'
)

$replacement = @"
.method public A32(Landroid/os/Bundle;)V
    .locals 0

    invoke-super {p0, p1}, Lcom/facebook/base/activity/FbFragmentActivity;->A32(Landroid/os/Bundle;)V

    invoke-virtual {p0}, Lcom/facebook/base/activity/FbFragmentActivity;->finish()V

    return-void
.end method
"@

$pattern = '(?s)\.method public A32\(Landroid/os/Bundle;\)V.*?\.end method'

foreach ($rel in $targets) {
  $path = Join-Path $base $rel
  if (-not (Test-Path $path)) {
    Write-Warning "Missing file, skipped: $path"
    continue
  }

  $content = Get-Content -Raw -Path $path
  $updated = [regex]::Replace($content, $pattern, $replacement, 1)

  if ($updated -eq $content) {
    Write-Warning "A32 method not replaced (pattern not found): $path"
    continue
  }

  Set-Content -Path $path -Value $updated -NoNewline
  Write-Host "Patched: $path" -ForegroundColor Green
}

Write-Host 'AI cut patch set applied.' -ForegroundColor Cyan
