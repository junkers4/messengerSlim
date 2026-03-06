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

function Replace-AllPatterns {
  param(
    [Parameter(Mandatory = $true)][string]$Content,
    [Parameter(Mandatory = $true)]$Specs
  )

  $updated = $Content
  $count = 0
  foreach ($spec in $Specs) {
    $newContent = [regex]::Replace($updated, $spec.Pattern, $spec.Replacement)
    if ($newContent -ne $updated) {
      $updated = $newContent
      $count++
    }
  }

  return [PSCustomObject]@{
    Content = $updated
    Changes = $count
  }
}

$targetPathRegex = '(background|notify|notification|push|mqtt|sync|upload|job|scheduler|alarm|directshare|wear|analytics|bugreport|keepalive|screencast|messengercar|fbnslite|oxygen|datatransport|systemalarm|systemjob|falco|nna|registration|chatheads|worker|workmanager|heartbeat|presence|poll|fetch|wakeup|bootstrap|daemon|headless|trigger|reminder|survey|chooser|foregroundservice)'
$criticalWhitelistRegex = '(auth|login|orca\\threadview|messaging\\threadview|messaging\\threadlist|messenger\\splashscreen|messenger\\neue\\mainactivity|checkpoint|waitingforstrings|abstractintenthandler|appmoduledownload|delegatingfbfragmentframeworkactivity|incallactivity|androidx\\activity|androidx\\fragment|androidx\\core\\app\\componentactivity|fileprovider|startupprovider|workmanagerinitializer|processlifecycleowner|emojiCompatinitializer)'

$methodSpecs = @(
  @{
    Pattern = '(?s)(\.method[^\n]* A05\(Landroid/content/Context;Landroid/content/Intent;LX/0NL;\)V)\n.*?\.end method'
    Replacement = '$1`n    .locals 0`n`n    return-void`n.end method'
  },
  @{
    Pattern = '(?s)(\.method[^\n]* onReceive\(Landroid/content/Context;Landroid/content/Intent;\)V)\n.*?\.end method'
    Replacement = '$1`n    .locals 0`n`n    return-void`n.end method'
  },
  @{
    Pattern = '(?s)(\.method[^\n]* A03\(Landroid/content/Intent;\)V)\n.*?\.end method'
    Replacement = '$1`n    .locals 0`n`n    return-void`n.end method'
  },
  @{
    Pattern = '(?s)(\.method[^\n]* onHandleIntent\(Landroid/content/Intent;\)V)\n.*?\.end method'
    Replacement = '$1`n    .locals 0`n`n    return-void`n.end method'
  },
  @{
    Pattern = '(?s)(\.method[^\n]* onHandleWork\(Landroid/content/Intent;\)V)\n.*?\.end method'
    Replacement = '$1`n    .locals 0`n`n    return-void`n.end method'
  },
  @{
    Pattern = '(?s)(\.method[^\n]* onStartCommand\(Landroid/content/Intent;II\)I)\n.*?\.end method'
    Replacement = '$1`n    .locals 1`n`n    const/4 v0, 0x2`n`n    return v0`n.end method'
  },
  @{
    Pattern = '(?s)(\.method[^\n]* onStartJob\(Landroid/app/job/JobParameters;\)Z)\n.*?\.end method'
    Replacement = '$1`n    .locals 1`n`n    const/4 v0, 0x0`n`n    return v0`n.end method'
  },
  @{
    Pattern = '(?s)(\.method[^\n]* onStopJob\(Landroid/app/job/JobParameters;\)Z)\n.*?\.end method'
    Replacement = '$1`n    .locals 1`n`n    const/4 v0, 0x0`n`n    return v0`n.end method'
  },
  @{
    Pattern = '(?s)(\.method[^\n]* onBind\(Landroid/content/Intent;\)Landroid/os/IBinder;)\n.*?\.end method'
    Replacement = '$1`n    .locals 1`n`n    const/4 v0, 0x0`n`n    return-object v0`n.end method'
  },
  @{
    Pattern = '(?s)(\.method[^\n]* onGetChooserTargets\(Landroid/content/ComponentName;Landroid/content/IntentFilter;\)Ljava/util/List;)\n.*?\.end method'
    Replacement = '$1`n    .locals 1`n`n    invoke-static {}, Ljava/util/Collections;->emptyList()Ljava/util/List;`n`n    move-result-object v0`n`n    return-object v0`n.end method'
  },
  @{
    Pattern = '(?s)(\.method[^\n]* A01\(\)Landroid/os/IBinder;)\n.*?\.end method'
    Replacement = '$1`n    .locals 1`n`n    const/4 v0, 0x0`n`n    return-object v0`n.end method'
  },
  @{
    Pattern = '(?s)(\.method[^\n]* A04\(\)V)\n.*?\.end method'
    Replacement = '$1`n    .locals 0`n`n    return-void`n.end method'
  },
  @{
    Pattern = '(?s)(\.method[^\n]* doWork\(\)Landroidx/work/ListenableWorker\$Result;)\n.*?\.end method'
    Replacement = '$1`n    .locals 1`n`n    invoke-static {}, Landroidx/work/ListenableWorker$Result;->success()Landroidx/work/ListenableWorker$Result;`n`n    move-result-object v0`n`n    return-object v0`n.end method'
  }
)

$patchedFiles = 0
$patchedMethods = 0
$unchangedFiles = 0
$skippedCritical = 0

$files = Get-ChildItem -Path $base -Recurse -Filter '*.smali' | Where-Object {
  $_.FullName -match $targetPathRegex
}

foreach ($file in $files) {
  if ($file.FullName -match $criticalWhitelistRegex) {
    $skippedCritical++
    continue
  }

  $content = Get-Content -Raw -Path $file.FullName
  $result = Replace-AllPatterns -Content $content -Specs $methodSpecs

  if ($result.Changes -gt 0) {
    Set-Content -Path $file.FullName -Value $result.Content -NoNewline
    $patchedFiles++
    $patchedMethods += $result.Changes
    Write-Host "Patched: $($file.FullName) (+$($result.Changes) method groups)" -ForegroundColor Green
  }
  else {
    $unchangedFiles++
  }
}

Write-Host "No-background mode patch set applied." -ForegroundColor Cyan
Write-Host "PatchedFiles=$patchedFiles, PatchedMethodGroups=$patchedMethods, UnchangedFiles=$unchangedFiles, SkippedCritical=$skippedCritical" -ForegroundColor Cyan
