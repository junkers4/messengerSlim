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

function Replace-MethodByHeader {
  param(
    [Parameter(Mandatory = $true)]
    [string]$Content,
    [Parameter(Mandatory = $true)]
    [string]$MethodHeader,
    [Parameter(Mandatory = $true)]
    [string]$Replacement
  )

  $pattern = '(?s)' + [regex]::Escape($MethodHeader) + '.*?\.end method'
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
  @{ Path = 'smali/com/facebook/fdidlite/FDIDSyncLiteReceiver.smali'; Header = '.method public A05(Landroid/content/Context;Landroid/content/Intent;LX/0NL;)V'; Replacement = $voidReceiverNoop },
  @{ Path = 'smali_classes4/com/facebook/messaging/chatheads/service/ChatHeadsServiceBroadcastReceiver.smali'; Header = '.method public A05(Landroid/content/Context;Landroid/content/Intent;LX/0NL;)V'; Replacement = $voidReceiverNoop },
  @{ Path = 'smali/com/facebook/messenger/app/background/MessengerBackgroundWakeupReceiver.smali'; Header = '.method public A05(Landroid/content/Context;Landroid/content/Intent;LX/0NL;)V'; Replacement = $voidReceiverNoop },
  @{ Path = 'smali_classes10/com/facebook/bugreporter/core/scheduler/AlarmsBroadcastReceiver.smali'; Header = '.method public A05(Landroid/content/Context;Landroid/content/Intent;LX/0NL;)V'; Replacement = $voidReceiverNoop },
  @{ Path = 'smali_classes12/com/facebook/analytics2/fabric/onefabric/OneFabricUploadAlarmReceiver.smali'; Header = '.method public A05(Landroid/content/Context;Landroid/content/Intent;LX/0NL;)V'; Replacement = $voidReceiverNoop },
  @{ Path = 'smali_classes7/com/facebook/messaging/messengerprefs/notif/NoteNotificationBroadcastReceiver.smali'; Header = '.method public A05(Landroid/content/Context;Landroid/content/Intent;LX/0NL;)V'; Replacement = $voidReceiverNoop },
  @{ Path = 'smali_classes7/com/facebook/messaging/notify/plugins/handler/messagereminder/MessageNotificationSurveyReceiver.smali'; Header = '.method public A05(Landroid/content/Context;Landroid/content/Intent;LX/0NL;)V'; Replacement = $voidReceiverNoop },
  @{ Path = 'smali_classes8/com/facebook/messaging/notify/client/MessagesNotificationBroadcastReceiver.smali'; Header = '.method public A05(Landroid/content/Context;Landroid/content/Intent;LX/0NL;)V'; Replacement = $voidReceiverNoop },
  @{ Path = 'smali_classes4/com/facebook/messaging/notify/logging/MessagesSystemTrayLogService.smali'; Header = '.method public A03(Landroid/content/Intent;)V'; Replacement = $voidIntentNoop },
  @{ Path = 'smali_classes4/com/facebook/messengercar/CarNotificationService.smali'; Header = '.method public A03(Landroid/content/Intent;)V'; Replacement = $voidIntentNoop },
  @{ Path = 'smali_classes7/com/facebook/messaging/notify/service/MessengerDirectReplyService.smali'; Header = '.method public A03(Landroid/content/Intent;)V'; Replacement = $voidIntentNoop },
  @{ Path = 'smali_classes7/com/facebook/messaging/notify/service/MessengerLikeService.smali'; Header = '.method public A03(Landroid/content/Intent;)V'; Replacement = $voidIntentNoop },
  @{ Path = 'smali_classes7/com/facebook/messaging/directshare/DirectShareChooserTargetService.smali'; Header = '.method public onGetChooserTargets(Landroid/content/ComponentName;Landroid/content/IntentFilter;)Ljava/util/List;'; Replacement = $listNoop },
  @{ Path = 'smali_classes10/androidx/sharetarget/ChooserTargetServiceCompat.smali'; Header = '.method public onGetChooserTargets(Landroid/content/ComponentName;Landroid/content/IntentFilter;)Ljava/util/List;'; Replacement = $listNoop },
  @{ Path = 'smali_classes3/com/facebook/wearlistener/DataLayerListenerService.smali'; Header = '.method public final onBind(Landroid/content/Intent;)Landroid/os/IBinder;'; Replacement = $binderNoop },
  @{ Path = 'smali_classes10/com/meta/wearable/acdc/sdk/service/ACDCRegistrationService.smali'; Header = '.method public A01()Landroid/os/IBinder;'; Replacement = $binderNoopA01 },
  @{ Path = 'smali_classes10/com/meta/wearable/acdc/sdk/service/ACDCRegistrationService.smali'; Header = '.method public A04()V'; Replacement = $voidA04Noop }
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
  $updated = Replace-MethodByHeader -Content $content -MethodHeader $rule.Header -Replacement $rule.Replacement

  if ($updated -eq $content) {
    Write-Warning "Method pattern not replaced: $path [$($rule.Header)]"
    $unchanged++
    continue
  }

  Set-Content -Path $path -Value $updated -NoNewline
  Write-Host "Patched: $path" -ForegroundColor Green
  $patched++
}

Write-Host "Battery cut patch set applied." -ForegroundColor Cyan
Write-Host "Patched=$patched, Missing=$missing, Unchanged=$unchanged" -ForegroundColor Cyan
