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
    [Parameter(Mandatory = $true)][string]$Content,
    [Parameter(Mandatory = $true)][string]$MethodHeader,
    [Parameter(Mandatory = $true)][string]$Replacement
  )

  $pattern = '(?s)' + [regex]::Escape($MethodHeader) + '.*?\.end method'
  return [regex]::Replace($Content, $pattern, $Replacement, 1)
}

$activityA32Noop = @"
.method public A32(Landroid/os/Bundle;)V
    .locals 0

    invoke-super {p0, p1}, Lcom/facebook/base/activity/FbFragmentActivity;->A32(Landroid/os/Bundle;)V

    invoke-virtual {p0}, Lcom/facebook/base/activity/FbFragmentActivity;->finish()V

    return-void
.end method
"@

$activityOnCreateNoop = @"
.method public onCreate(Landroid/os/Bundle;)V
    .locals 0

    invoke-super {p0, p1}, Lcom/facebook/base/activity/FbFragmentActivity;->onCreate(Landroid/os/Bundle;)V

    invoke-virtual {p0}, Lcom/facebook/base/activity/FbFragmentActivity;->finish()V

    return-void
.end method
"@

$receiverA05Noop = @"
.method public A05(Landroid/content/Context;Landroid/content/Intent;LX/0NL;)V
    .locals 0

    return-void
.end method
"@

$serviceA03Noop = @"
.method public A03(Landroid/content/Intent;)V
    .locals 0

    return-void
.end method
"@

$chooserEmpty = @"
.method public onGetChooserTargets(Landroid/content/ComponentName;Landroid/content/IntentFilter;)Ljava/util/List;
    .locals 1

    invoke-static {}, Ljava/util/Collections;->emptyList()Ljava/util/List;

    move-result-object v0

    return-object v0
.end method
"@

$bindNull = @"
.method public final onBind(Landroid/content/Intent;)Landroid/os/IBinder;
    .locals 1

    const/4 v0, 0x0

    return-object v0
.end method
"@

$a01BindNull = @"
.method public A01()Landroid/os/IBinder;
    .locals 1

    const/4 v0, 0x0

    return-object v0
.end method
"@

$a04VoidNoop = @"
.method public A04()V
    .locals 0

    return-void
.end method
"@

$activityTargets = @(
  # RTC (hovory) NECHANE ZAPNUTE - CallPermissionsActivity/ZeroRatingActivity/MinorSafetyNoticeActivity
  'smali_classes8/com/facebook/messenger/intents/ShareIntentHandler.smali',
  'smali_classes8/com/facebook/messenger/intents/MediaEditShareIntentHandler.smali',
  'smali_classes8/com/facebook/messaging/sharing/ShareLauncherActivity.smali',
  'smali_classes7/com/facebook/messaging/threadview/iconpicker/ThreadIconPickerActivity.smali',
  'smali_classes9/com/facebook/messaging/camerautil/CropImage.smali',
  'smali_classes7/com/facebook/ipc/composer/launch/ComposerLaunchActivity.smali',
  'smali_classes8/com/facebook/platform/common/activity/PlatformWrapperActivity.smali',
  'smali_classes3/com/facebook/messenger/intents/FamilyIntentHandlerActivity.smali',
  'smali_classes3/com/facebook/messenger/intents/SameTaskIntentHandlerActivity.smali',
  'smali_classes3/com/facebook/messenger/intents/SecureSameTaskIntentHandlerActivity.smali',
  'smali_classes3/com/facebook/messenger/intents/SecureIntentHandlerActivity.smali',
  'smali_classes3/com/facebook/messenger/intents/IntentHandlerActivity.smali'
)

$methodRules = @(
  @{ Path='smali_classes4/com/facebook/messaging/chatheads/service/ChatHeadsServiceBroadcastReceiver.smali'; Header='.method public A05(Landroid/content/Context;Landroid/content/Intent;LX/0NL;)V'; Replacement=$receiverA05Noop },
  @{ Path='smali/com/facebook/messenger/app/background/MessengerBackgroundWakeupReceiver.smali'; Header='.method public A05(Landroid/content/Context;Landroid/content/Intent;LX/0NL;)V'; Replacement=$receiverA05Noop },
  @{ Path='smali_classes7/com/facebook/messaging/notify/plugins/handler/messagereminder/MessageNotificationSurveyReceiver.smali'; Header='.method public A05(Landroid/content/Context;Landroid/content/Intent;LX/0NL;)V'; Replacement=$receiverA05Noop },
  @{ Path='smali_classes8/com/facebook/messaging/notify/client/MessagesNotificationBroadcastReceiver.smali'; Header='.method public A05(Landroid/content/Context;Landroid/content/Intent;LX/0NL;)V'; Replacement=$receiverA05Noop },
  @{ Path='smali_classes4/com/facebook/messaging/notify/logging/MessagesSystemTrayLogService.smali'; Header='.method public A03(Landroid/content/Intent;)V'; Replacement=$serviceA03Noop },
  @{ Path='smali_classes7/com/facebook/messaging/directshare/DirectShareChooserTargetService.smali'; Header='.method public onGetChooserTargets(Landroid/content/ComponentName;Landroid/content/IntentFilter;)Ljava/util/List;'; Replacement=$chooserEmpty },
  @{ Path='smali_classes10/androidx/sharetarget/ChooserTargetServiceCompat.smali'; Header='.method public onGetChooserTargets(Landroid/content/ComponentName;Landroid/content/IntentFilter;)Ljava/util/List;'; Replacement=$chooserEmpty },
  @{ Path='smali_classes3/com/facebook/wearlistener/DataLayerListenerService.smali'; Header='.method public final onBind(Landroid/content/Intent;)Landroid/os/IBinder;'; Replacement=$bindNull },
  @{ Path='smali_classes10/com/meta/wearable/acdc/sdk/service/ACDCRegistrationService.smali'; Header='.method public A01()Landroid/os/IBinder;'; Replacement=$a01BindNull },
  @{ Path='smali_classes10/com/meta/wearable/acdc/sdk/service/ACDCRegistrationService.smali'; Header='.method public A04()V'; Replacement=$a04VoidNoop }
)

$patched = 0
$missing = 0
$unchanged = 0

foreach ($rel in $activityTargets) {
  $path = Join-Path $base $rel
  if (-not (Test-Path $path)) {
    Write-Warning "Missing activity file: $path"
    $missing++
    continue
  }

  $content = Get-Content -Raw -Path $path
  $updated = Replace-MethodByHeader -Content $content -MethodHeader '.method public A32(Landroid/os/Bundle;)V' -Replacement $activityA32Noop

  if ($updated -eq $content) {
    $updated = Replace-MethodByHeader -Content $content -MethodHeader '.method public onCreate(Landroid/os/Bundle;)V' -Replacement $activityOnCreateNoop
  }

  if ($updated -eq $content) {
    Write-Warning "Activity method not patched (A32/onCreate): $path"
    $unchanged++
    continue
  }

  Set-Content -Path $path -Value $updated -NoNewline
  Write-Host "Patched activity: $path" -ForegroundColor Green
  $patched++
}

foreach ($rule in $methodRules) {
  $path = Join-Path $base $rule.Path
  if (-not (Test-Path $path)) {
    Write-Warning "Missing handler file: $path"
    $missing++
    continue
  }

  $content = Get-Content -Raw -Path $path
  $updated = Replace-MethodByHeader -Content $content -MethodHeader $rule.Header -Replacement $rule.Replacement

  if ($updated -eq $content) {
    Write-Warning "Handler method not patched: $path [$($rule.Header)]"
    $unchanged++
    continue
  }

  Set-Content -Path $path -Value $updated -NoNewline
  Write-Host "Patched handler: $path" -ForegroundColor Green
  $patched++
}

Write-Host "Chat-only extreme patch set applied." -ForegroundColor Cyan
Write-Host "Patched=$patched, Missing=$missing, Unchanged=$unchanged" -ForegroundColor Cyan
