[CmdletBinding()]
param(
  [string]$WorkDir = "work/decompiled_nres",
  [ValidateSet('core', 'extended', 'aggressive')]
  [string]$Level = 'extended'
)

$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$base = Join-Path $root $WorkDir

if (-not (Test-Path $base)) {
  throw "Work directory not found: $base"
}

$coreTargets = @(
  'smali_classes6/com/facebook/messaging/aibot/launcher/activity/AiStudioFragmentActivity.smali',
  'smali_classes6/com/facebook/messaging/aibot/launcher/activity/AiBotImmersiveThreadActivity.smali',
  'smali_classes6/com/facebook/xapp/messaging/composer/aibot/discover/activity/AiBotDiscoverActivity.smali',
  'smali_classes6/com/facebook/messaging/aibot/memory/activity/ManageAiMemorySettingsActivity.smali'
)

$extendedOnlyTargets = @(
  'smali_classes6/com/facebook/messaging/aibot/launcher/activity/AiStudioCreationFragmentActivity.smali',
  'smali_classes6/com/facebook/messaging/aibot/launcher/activity/AiStudioProfileActivity.smali',
  'smali_classes6/com/facebook/messaging/aibot/launcher/activity/AiStudioCharacterProfileCreationActivity.smali',
  'smali_classes6/com/facebook/messaging/aibot/launcher/activity/AiStudioImmersiveThreadFirstBloksActivity.smali'
)

$aggressiveOnlyTargets = @(
  'smali_classes6/com/facebook/messaging/aibot/appsettingsui/MetaAiLookupSettingsActivity.smali',
  'smali_classes6/com/facebook/messaging/aibot/botpicker/ugcbot/inthreadaibotediting/InThreadAiBotEditingActivity.smali',
  'smali_classes6/com/facebook/messaging/aibot/rpg/ScenesRPGIntroActivity.smali',
  'smali_classes7/com/facebook/messaging/aibot/aiplugins/search/activity/AISearchSourceBottomSheetActivity.smali'
)

$targets = switch ($Level) {
  'core' { $coreTargets }
  'extended' { $coreTargets + $extendedOnlyTargets }
  'aggressive' { $coreTargets + $extendedOnlyTargets + $aggressiveOnlyTargets }
}

$targets = $targets | Select-Object -Unique

$a32Replacement = @"
.method public A32(Landroid/os/Bundle;)V
    .locals 0

    invoke-super {p0, p1}, Lcom/facebook/base/activity/FbFragmentActivity;->A32(Landroid/os/Bundle;)V

    invoke-virtual {p0}, Lcom/facebook/base/activity/FbFragmentActivity;->finish()V

    return-void
.end method
"@

$onCreateReplacement = @"
.method public onCreate(Landroid/os/Bundle;)V
    .locals 0

    invoke-super {p0, p1}, Lcom/facebook/base/activity/FbFragmentActivity;->onCreate(Landroid/os/Bundle;)V

    invoke-virtual {p0}, Lcom/facebook/base/activity/FbFragmentActivity;->finish()V

    return-void
.end method
"@

$a32Pattern = '(?s)\.method public A32\(Landroid/os/Bundle;\)V.*?\.end method'
$onCreatePattern = '(?s)\.method public onCreate\(Landroid/os/Bundle;\)V.*?\.end method'

$patched = 0
$alreadyPatched = 0
$missing = 0
$unmatched = 0

foreach ($rel in $targets) {
  $path = Join-Path $base $rel
  if (-not (Test-Path $path)) {
    Write-Warning "Missing file, skipped: $path"
    $missing++
    continue
  }

  $content = Get-Content -Raw -Path $path
  if ($content -match 'invoke-virtual \{p0\}, Lcom/facebook/base/activity/FbFragmentActivity;->finish\(\)V') {
    Write-Host "Already patched: $path" -ForegroundColor DarkYellow
    $alreadyPatched++
    continue
  }

  $updated = [regex]::Replace($content, $a32Pattern, $a32Replacement, 1)

  if ($updated -eq $content) {
    $updated = [regex]::Replace($content, $onCreatePattern, $onCreateReplacement, 1)
  }

  if ($updated -eq $content) {
    Write-Warning "No patchable lifecycle method found (A32/onCreate): $path"
    $unmatched++
    continue
  }

  Set-Content -Path $path -Value $updated -NoNewline
  Write-Host "Patched: $path" -ForegroundColor Green
  $patched++
}

Write-Host "AI cut patch set applied (level=$Level)." -ForegroundColor Cyan
Write-Host "Patched=$patched, AlreadyPatched=$alreadyPatched, Missing=$missing, Unmatched=$unmatched" -ForegroundColor Cyan
