<#
.SYNOPSIS
  Supplementary nuclear sweep: patches services/receivers/providers by NAME and METHOD
  signatures. Fixes what the main nuclear script missed due to obfuscated base classes.

.DESCRIPTION
  Runs AFTER apply-nuclear-chatonly.ps1.
  Uses filename patterns (Service, Receiver, Provider, Job) to identify components,
  then replaces their lifecycle methods with no-ops.
  Also does a blanket scan of ALL smali files for service-specific methods.
#>
[CmdletBinding()]
param(
  [string]$WorkDir = "work/decompiled_nres_clean"
)

$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$base = Join-Path $root $WorkDir

if (-not (Test-Path $base)) {
  throw "Work directory not found: $base"
}

# ── Whitelists ──

# Paths containing these patterns are protected (will NOT be patched)
$coreWhitelist = @(
  'orca[\\/](auth|app)',
  'messaging[\\/](threadlist|threadview|inbox|composer|lite|app[\\/])',
  'messaging[\\/](model|msys|e2ee|sms|mms)',
  'messaging[\\/](conversationlist|conversation[\\/]|messagelist)',
  'messaging[\\/](media[\\/]|attachments|photos[\\/]|contactpicker|newthread|forwardpicker|groupthread|threadcreation)',
  'messaging[\\/](gifpicker|emojipicker|stickerpicker|audioplayer|voicemessage|richmedia)',
  'messaging[\\/](deeplink|upsell)',
  'messenger[\\/](app[\\/]|lite)',
  'base[\\/](activity|service)',
  'FbFragmentActivity',
  'FbPreferenceActivity',
  'FbActivity',
  'login[\\/]',
  'auth[\\/]',
  'webview',
  'WebViewActivity',
  'browser',
  'InitProvider',
  'WorkManagerInitializer',
  'FileProvider',
  'StartupProvider',
  'LifecycleDispatcher',
  'ProcessLifecycleOwner',
  'firebase[\\/]messaging',
  'EmojiCompatInitializer',
  'ProfilerInitProvider',
  'google[\\/]firebase',
  'androidx[\\/]'
)
$whitelistRx = ($coreWhitelist | ForEach-Object { "($_)" }) -join '|'

# ════════════════════════════════════════════════════════════════
# SWEEP A: Name-based Service sweep
# ════════════════════════════════════════════════════════════════
Write-Host "`n═══ SWEEP A: Service/JobService by filename ═══" -ForegroundColor Magenta

$svcPatterns = @(
  @{ Sig = 'onStartCommand(Landroid/content/Intent;II)I'; Body = "    .locals 1`r`n`r`n    const/4 v0, 0x2`r`n`r`n    return v0" },
  @{ Sig = 'onBind(Landroid/content/Intent;)Landroid/os/IBinder;'; Body = "    .locals 1`r`n`r`n    const/4 v0, 0x0`r`n`r`n    return-object v0" },
  @{ Sig = 'onStartJob(Landroid/app/job/JobParameters;)Z'; Body = "    .locals 1`r`n`r`n    const/4 v0, 0x0`r`n`r`n    return v0" },
  @{ Sig = 'onStopJob(Landroid/app/job/JobParameters;)Z'; Body = "    .locals 1`r`n`r`n    const/4 v0, 0x0`r`n`r`n    return v0" },
  @{ Sig = 'A03(Landroid/content/Intent;)V'; Body = "    .locals 0`r`n`r`n    return-void" }
)

$saFiles = 0; $saMethods = 0; $saWL = 0

Get-ChildItem -Path $base -Recurse -Filter '*.smali' -ErrorAction SilentlyContinue |
  Where-Object { $_.Name -match '(Service|JobService)\.smali$' } |
  ForEach-Object {
    $rel = $_.FullName.Substring((Resolve-Path $base).Path.Length + 1)
    if ($rel -match $whitelistRx) { $saWL++; return }

    $content = [System.IO.File]::ReadAllText($_.FullName)
    $changed = $false

    foreach ($spec in $svcPatterns) {
      $sig = [regex]::Escape($spec.Sig)
      $pattern = "(?s)(\.method(?![^\n]*abstract)[^\n]*\s+$sig)\r?\n.*?\.end method"
      $replacement = "`$1`r`n$($spec.Body)`r`n.end method"
      $updated = [regex]::Replace($content, $pattern, $replacement)
      if ($updated -ne $content) {
        $content = $updated
        $changed = $true
        $saMethods++
      }
    }

    if ($changed) {
      [System.IO.File]::WriteAllText($_.FullName, $content)
      $saFiles++
    }
  }

Write-Host "Sweep A done: $saFiles files, $saMethods methods (whitelisted=$saWL)" -ForegroundColor Cyan

# ════════════════════════════════════════════════════════════════
# SWEEP B: Name-based Receiver sweep
# ════════════════════════════════════════════════════════════════
Write-Host "`n═══ SWEEP B: Receiver by filename ═══" -ForegroundColor Magenta

$rcvPatterns = @(
  @{ Sig = 'onReceive(Landroid/content/Context;Landroid/content/Intent;)V'; Body = "    .locals 0`r`n`r`n    return-void" },
  @{ Sig = 'A05(Landroid/content/Context;Landroid/content/Intent;LX/0NL;)V'; Body = "    .locals 0`r`n`r`n    return-void" }
)

$sbFiles = 0; $sbMethods = 0; $sbWL = 0

Get-ChildItem -Path $base -Recurse -Filter '*.smali' -ErrorAction SilentlyContinue |
  Where-Object { $_.Name -match 'Receiver\.smali$' } |
  ForEach-Object {
    $rel = $_.FullName.Substring((Resolve-Path $base).Path.Length + 1)
    if ($rel -match $whitelistRx) { $sbWL++; return }

    $content = [System.IO.File]::ReadAllText($_.FullName)
    $changed = $false

    foreach ($spec in $rcvPatterns) {
      $sig = [regex]::Escape($spec.Sig)
      $pattern = "(?s)(\.method(?![^\n]*abstract)[^\n]*\s+$sig)\r?\n.*?\.end method"
      $replacement = "`$1`r`n$($spec.Body)`r`n.end method"
      $updated = [regex]::Replace($content, $pattern, $replacement)
      if ($updated -ne $content) {
        $content = $updated
        $changed = $true
        $sbMethods++
      }
    }

    if ($changed) {
      [System.IO.File]::WriteAllText($_.FullName, $content)
      $sbFiles++
    }
  }

Write-Host "Sweep B done: $sbFiles files, $sbMethods methods (whitelisted=$sbWL)" -ForegroundColor Cyan

# ════════════════════════════════════════════════════════════════
# SWEEP C: Name-based Provider sweep
# ════════════════════════════════════════════════════════════════
Write-Host "`n═══ SWEEP C: Provider by filename ═══" -ForegroundColor Magenta

$scFiles = 0; $scWL = 0

Get-ChildItem -Path $base -Recurse -Filter '*.smali' -ErrorAction SilentlyContinue |
  Where-Object { $_.Name -match 'Provider\.smali$' } |
  ForEach-Object {
    $rel = $_.FullName.Substring((Resolve-Path $base).Path.Length + 1)
    if ($rel -match $whitelistRx) { $scWL++; return }

    $content = [System.IO.File]::ReadAllText($_.FullName)
    $pattern = '(?s)(\.method(?![^\n]*abstract)[^\n]*\s+onCreate\(\)Z)\r?\n.*?\.end method'
    $replacement = "`$1`r`n    .locals 1`r`n`r`n    const/4 v0, 0x1`r`n`r`n    return v0`r`n.end method"
    $updated = [regex]::Replace($content, $pattern, $replacement)
    if ($updated -ne $content) {
      [System.IO.File]::WriteAllText($_.FullName, $updated)
      $scFiles++
    }
  }

Write-Host "Sweep C done: $scFiles files (whitelisted=$scWL)" -ForegroundColor Cyan

# ════════════════════════════════════════════════════════════════
# SWEEP D: Blanket method scan across ALL smali files
#   Only targets very specific service-only lifecycle methods
#   that would never appear in non-service classes
# ════════════════════════════════════════════════════════════════
Write-Host "`n═══ SWEEP D: Blanket lifecycle method scan (all smali) ═══" -ForegroundColor Magenta

$sdFiles = 0; $sdMethods = 0; $sdWL = 0

# Very specific method signatures unique to Android services/job services
$blanketSpecs = @(
  @{ Sig = 'onStartCommand(Landroid/content/Intent;II)I'; Body = "    .locals 1`r`n`r`n    const/4 v0, 0x2`r`n`r`n    return v0" },
  @{ Sig = 'onStartJob(Landroid/app/job/JobParameters;)Z'; Body = "    .locals 1`r`n`r`n    const/4 v0, 0x0`r`n`r`n    return v0" },
  @{ Sig = 'onStopJob(Landroid/app/job/JobParameters;)Z'; Body = "    .locals 1`r`n`r`n    const/4 v0, 0x0`r`n`r`n    return v0" }
)

# Pre-filter: only read files that MIGHT contain a target method
# Use a quick string check on filename or do a search
$blanketKeywords = 'onStartCommand|onStartJob|onStopJob'

Get-ChildItem -Path $base -Recurse -Filter '*.smali' -ErrorAction SilentlyContinue | ForEach-Object {
  $rel = $_.FullName.Substring((Resolve-Path $base).Path.Length + 1)
  if ($rel -match $whitelistRx) { return }

  # Quick check: read first pass looking for method keywords  
  $content = [System.IO.File]::ReadAllText($_.FullName)
  if ($content -notmatch $blanketKeywords) { return }

  $changed = $false
  foreach ($spec in $blanketSpecs) {
    $sig = [regex]::Escape($spec.Sig)
    $pattern = "(?s)(\.method(?![^\n]*abstract)[^\n]*\s+$sig)\r?\n.*?\.end method"
    $replacement = "`$1`r`n$($spec.Body)`r`n.end method"
    $updated = [regex]::Replace($content, $pattern, $replacement)
    if ($updated -ne $content) {
      $content = $updated
      $changed = $true
      $sdMethods++
    }
  }

  if ($changed) {
    [System.IO.File]::WriteAllText($_.FullName, $content)
    $sdFiles++
  }
}

Write-Host "Sweep D done: $sdFiles files, $sdMethods methods" -ForegroundColor Cyan

# ════════════════════════════════════════════════════════════════
# SWEEP E: Facebook-specific obfuscated receiver method (A05) 
#   across all files with "Receiver" ancestry indicators
# ════════════════════════════════════════════════════════════════
Write-Host "`n═══ SWEEP E: FB obfuscated receiver A05 (blanket) ═══" -ForegroundColor Magenta

$seFiles = 0; $seMethods = 0

$fbRcvSig = [regex]::Escape('A05(Landroid/content/Context;Landroid/content/Intent;LX/0NL;)V')
$fbRcvPattern = "(?s)(\.method(?![^\n]*abstract)[^\n]*\s+$fbRcvSig)\r?\n.*?\.end method"
$fbRcvReplacement = "`$1`r`n    .locals 0`r`n`r`n    return-void`r`n.end method"

Get-ChildItem -Path $base -Recurse -Filter '*.smali' -ErrorAction SilentlyContinue | ForEach-Object {
  $rel = $_.FullName.Substring((Resolve-Path $base).Path.Length + 1)
  if ($rel -match $whitelistRx) { return }

  $content = [System.IO.File]::ReadAllText($_.FullName)
  if ($content -notmatch 'A05\(Landroid/content/Context') { return }

  $updated = [regex]::Replace($content, $fbRcvPattern, $fbRcvReplacement)
  if ($updated -ne $content) {
    [System.IO.File]::WriteAllText($_.FullName, $updated)
    $seFiles++
    $seMethods++
  }
}

Write-Host "Sweep E done: $seFiles files, $seMethods methods" -ForegroundColor Cyan

# ════════════════════════════════════════════════════════════════
# SWEEP F: Facebook obfuscated service method A03(Intent)V
# ════════════════════════════════════════════════════════════════
Write-Host "`n═══ SWEEP F: FB obfuscated service A03 (blanket) ═══" -ForegroundColor Magenta

$sfFiles = 0

$fbSvcSig = [regex]::Escape('A03(Landroid/content/Intent;)V')
$fbSvcPattern = "(?s)(\.method(?![^\n]*abstract)[^\n]*\s+$fbSvcSig)\r?\n.*?\.end method"
$fbSvcReplacement = "`$1`r`n    .locals 0`r`n`r`n    return-void`r`n.end method"

Get-ChildItem -Path $base -Recurse -Filter '*.smali' -ErrorAction SilentlyContinue | ForEach-Object {
  $rel = $_.FullName.Substring((Resolve-Path $base).Path.Length + 1)
  if ($rel -match $whitelistRx) { return }

  $content = [System.IO.File]::ReadAllText($_.FullName)
  if ($content -notmatch 'A03\(Landroid/content/Intent') { return }

  $updated = [regex]::Replace($content, $fbSvcPattern, $fbSvcReplacement)
  if ($updated -ne $content) {
    [System.IO.File]::WriteAllText($_.FullName, $updated)
    $sfFiles++
  }
}

Write-Host "Sweep F done: $sfFiles files" -ForegroundColor Cyan

# ════════════════════════════════════════════════════════════════
# SUMMARY
# ════════════════════════════════════════════════════════════════
Write-Host "`n════════════════════════════════════════════" -ForegroundColor Yellow
Write-Host "NUCLEAR SWEEP V2 COMPLETE" -ForegroundColor Yellow
Write-Host "Sweep A (Services by name):    $saFiles files, $saMethods methods" -ForegroundColor Green
Write-Host "Sweep B (Receivers by name):   $sbFiles files, $sbMethods methods" -ForegroundColor Green
Write-Host "Sweep C (Providers by name):   $scFiles files" -ForegroundColor Green
Write-Host "Sweep D (Blanket lifecycle):   $sdFiles files, $sdMethods methods" -ForegroundColor Green
Write-Host "Sweep E (FB A05 blanket):      $seFiles files" -ForegroundColor Green
Write-Host "Sweep F (FB A03 blanket):      $sfFiles files" -ForegroundColor Green
$total = $saFiles + $sbFiles + $scFiles + $sdFiles + $seFiles + $sfFiles
Write-Host "TOTAL additional patches:      $total files" -ForegroundColor Yellow
Write-Host "════════════════════════════════════════════" -ForegroundColor Yellow
