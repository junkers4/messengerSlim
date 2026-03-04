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

function Replace-FirstMethod {
  param(
    [Parameter(Mandatory = $true)]
    [string]$Content,
    [Parameter(Mandatory = $true)]
    [string]$MethodSignatureRegex,
    [Parameter(Mandatory = $true)]
    [string]$Replacement
  )

  $pattern = "(?s)\\.method[\\s\\S]*?$MethodSignatureRegex[\\s\\S]*?\\.end method"
  return [regex]::Replace($Content, $pattern, $Replacement, 1)
}

$voidReceiverNoop = @"
.method public A05(Landroid/content/Context;Landroid/content/Intent;LX/0NL;)V
    .locals 0

    return-void
.end method
"@

$voidIntentNoop = @"
.method public A03(Landroid/content/Intent;)V
    .locals 0

    return-void
.end method
"@

$listNoop = @"
.method public onGetChooserTargets(Landroid/content/ComponentName;Landroid/content/IntentFilter;)Ljava/util/List;
    .locals 1

    invoke-static {}, Ljava/util/Collections;->emptyList()Ljava/util/List;

    move-result-object v0

    return-object v0
.end method
"@

$binderNoop = @"
.method public final onBind(Landroid/content/Intent;)Landroid/os/IBinder;
    .locals 1

    const/4 v0, 0x0

    return-object v0
.end method
"@

$binderNoopA01 = @"
.method public A01()Landroid/os/IBinder;
    .locals 1

    const/4 v0, 0x0

    return-object v0
.end method
"@

$voidA04Noop = @"
.method public A04()V
    .locals 0

    return-void
.end method
"@

$rules = @(
  @{ Path = 'smali_classes4/com/facebook/messaging/chatheads/service/ChatHeadsServiceBroadcastReceiver.smali'; Sig = 'A05\(Landroid/content/Context;Landroid/content/Intent;LX/0NL;\)V'; Replacement = $voidReceiverNoop },
  @{ Path = 'smali/com/facebook/messenger/app/background/MessengerBackgroundWakeupReceiver.smali'; Sig = 'A05\(Landroid/content/Context;Landroid/content/Intent;LX/0NL;\)V'; Replacement = $voidReceiverNoop },
  @{ Path = 'smali_classes7/com/facebook/messaging/notify/plugins/handler/messagereminder/MessageNotificationSurveyReceiver.smali'; Sig = 'A05\(Landroid/content/Context;Landroid/content/Intent;LX/0NL;\)V'; Replacement = $voidReceiverNoop },
  @{ Path = 'smali_classes4/com/facebook/messaging/notify/logging/MessagesSystemTrayLogService.smali'; Sig = 'A03\(Landroid/content/Intent;\)V'; Replacement = $voidIntentNoop },
  @{ Path = 'smali_classes7/com/facebook/messaging/directshare/DirectShareChooserTargetService.smali'; Sig = 'onGetChooserTargets\(Landroid/content/ComponentName;Landroid/content/IntentFilter;\)Ljava/util/List;'; Replacement = $listNoop },
  @{ Path = 'smali_classes10/androidx/sharetarget/ChooserTargetServiceCompat.smali'; Sig = 'onGetChooserTargets\(Landroid/content/ComponentName;Landroid/content/IntentFilter;\)Ljava/util/List;'; Replacement = $listNoop },
  @{ Path = 'smali_classes3/com/facebook/wearlistener/DataLayerListenerService.smali'; Sig = 'onBind\(Landroid/content/Intent;\)Landroid/os/IBinder;'; Replacement = $binderNoop },
  @{ Path = 'smali_classes10/com/meta/wearable/acdc/sdk/service/ACDCRegistrationService.smali'; Sig = 'A01\(\)Landroid/os/IBinder;'; Replacement = $binderNoopA01 },
  @{ Path = 'smali_classes10/com/meta/wearable/acdc/sdk/service/ACDCRegistrationService.smali'; Sig = 'A04\(\)V'; Replacement = $voidA04Noop }
)

$patched = 0
$missing = 0
$unchanged = 0

foreach ($rule in $rules) {
  $path = Join-Path $base $rule.Path
  if (-not (Test-Path $path)) {
    Write-Warning "Missing file, skipped: $path"
    $missing++
    continue
  }

  $content = Get-Content -Raw -Path $path
  $updated = Replace-FirstMethod -Content $content -MethodSignatureRegex $rule.Sig -Replacement $rule.Replacement

  if ($updated -eq $content) {
    Write-Warning "Method pattern not replaced: $path [$($rule.Sig)]"
    $unchanged++
    continue
  }

  Set-Content -Path $path -Value $updated -NoNewline
  Write-Host "Patched: $path" -ForegroundColor Green
  $patched++
}

Write-Host "Battery cut patch set applied." -ForegroundColor Cyan
Write-Host "Patched=$patched, Missing=$missing, Unchanged=$unchanged" -ForegroundColor Cyan
