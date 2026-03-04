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

$binderNoopPublic = @"
.method public onBind(Landroid/content/Intent;)Landroid/os/IBinder;
  .locals 1

  const/4 v0, 0x0

  return-object v0
.end method
"@

$startCommandNoop = @"
.method public onStartCommand(Landroid/content/Intent;II)I
  .locals 1

  const/4 v0, 0x2

  return v0
.end method
"@

$startCommandNoopFinal = @"
.method public final onStartCommand(Landroid/content/Intent;II)I
  .locals 1

  const/4 v0, 0x2

  return v0
.end method
"@

$startJobNoop = @"
.method public onStartJob(Landroid/app/job/JobParameters;)Z
  .locals 1

  const/4 v0, 0x0

  return v0
.end method
"@

$startJobNoopFinal = @"
.method public final onStartJob(Landroid/app/job/JobParameters;)Z
  .locals 1

  const/4 v0, 0x0

  return v0
.end method
"@

$stopJobNoop = @"
.method public onStopJob(Landroid/app/job/JobParameters;)Z
  .locals 1

  const/4 v0, 0x0

  return v0
.end method
"@

$stopJobNoopFinal = @"
.method public final onStopJob(Landroid/app/job/JobParameters;)Z
  .locals 1

  const/4 v0, 0x0

  return v0
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
  @{ Path = 'smali_classes7/com/facebook/account/twofac/protocol/LoginApprovalNotificationService.smali'; Header = '.method public A03(Landroid/content/Intent;)V'; Replacement = $voidIntentNoop },
  @{ Path = 'smali_classes4/com/facebook/push/registration/RegistrarHelperReceiver.smali'; Header = '.method public A05(Landroid/content/Context;Landroid/content/Intent;LX/0NL;)V'; Replacement = $voidReceiverNoop },
  @{ Path = 'smali_classes8/com/facebook/push/nna/NNABroadcastReceiver.smali'; Header = '.method public A05(Landroid/content/Context;Landroid/content/Intent;LX/0NL;)V'; Replacement = $voidReceiverNoop },
  @{ Path = 'smali_classes7/com/facebook/messaging/directshare/DirectShareChooserTargetService.smali'; Header = '.method public onGetChooserTargets(Landroid/content/ComponentName;Landroid/content/IntentFilter;)Ljava/util/List;'; Replacement = $listNoop },
  @{ Path = 'smali_classes10/androidx/sharetarget/ChooserTargetServiceCompat.smali'; Header = '.method public onGetChooserTargets(Landroid/content/ComponentName;Landroid/content/IntentFilter;)Ljava/util/List;'; Replacement = $listNoop },
  @{ Path = 'smali/com/facebook/mqttlite/MqttService.smali'; Header = '.method public onStartCommand(Landroid/content/Intent;II)I'; Replacement = $startCommandNoop },
  @{ Path = 'smali/com/facebook/push/fbnslite/PushSdkFbnsReceiverService.smali'; Header = '.method public onStartCommand(Landroid/content/Intent;II)I'; Replacement = $startCommandNoop },
  @{ Path = 'smali_classes10/androidx/work/impl/background/systemalarm/SystemAlarmService.smali'; Header = '.method public onStartCommand(Landroid/content/Intent;II)I'; Replacement = $startCommandNoop },
  @{ Path = 'smali_classes10/com/facebook/advancedcryptotransport/api/impl/service/AdvancedCryptoTransportNotificationService.smali'; Header = '.method public onBind(Landroid/content/Intent;)Landroid/os/IBinder;'; Replacement = $binderNoopPublic },
  @{ Path = 'smali_classes10/com/facebook/advancedcryptotransport/api/impl/service/AdvancedCryptoTransportNotificationService.smali'; Header = '.method public onStartCommand(Landroid/content/Intent;II)I'; Replacement = $startCommandNoop },
  @{ Path = 'smali_classes10/com/facebook/analytics2/logger/GooglePlayUploadService.smali'; Header = '.method public onStartCommand(Landroid/content/Intent;II)I'; Replacement = $startCommandNoop },
  @{ Path = 'smali_classes10/com/facebook/analytics2/logger/service/LollipopUploadSafeService.smali'; Header = '.method public onStartCommand(Landroid/content/Intent;II)I'; Replacement = $startCommandNoop },
  @{ Path = 'smali_classes10/com/facebook/analytics2/logger/service/LollipopUploadSafeService.smali'; Header = '.method public onStartJob(Landroid/app/job/JobParameters;)Z'; Replacement = $startJobNoop },
  @{ Path = 'smali_classes10/com/facebook/analytics2/logger/service/LollipopUploadSafeService.smali'; Header = '.method public onStopJob(Landroid/app/job/JobParameters;)Z'; Replacement = $stopJobNoop },
  @{ Path = 'smali_classes10/com/facebook/messaging/linkhandling/plugins/facebookvideolinkhandler/implementation/videolinkhandlerhelper/VideoLinkHandlerHelperNotificationService.smali'; Header = '.method public onBind(Landroid/content/Intent;)Landroid/os/IBinder;'; Replacement = $binderNoopPublic },
  @{ Path = 'smali_classes10/com/facebook/messaging/linkhandling/plugins/facebookvideolinkhandler/implementation/videolinkhandlerhelper/VideoLinkHandlerHelperNotificationService.smali'; Header = '.method public onStartCommand(Landroid/content/Intent;II)I'; Replacement = $startCommandNoop },
  @{ Path = 'smali_classes10/com/facebook/voltron/download/scheduledinstaller/OxygenScheduledInstallerJobService.smali'; Header = '.method public onStartJob(Landroid/app/job/JobParameters;)Z'; Replacement = $startJobNoop },
  @{ Path = 'smali_classes10/com/facebook/voltron/download/scheduledinstaller/OxygenScheduledInstallerJobService.smali'; Header = '.method public onStopJob(Landroid/app/job/JobParameters;)Z'; Replacement = $stopJobNoop },
  @{ Path = 'smali_classes10/com/google/android/datatransport/runtime/scheduling/jobscheduling/JobInfoSchedulerService.smali'; Header = '.method public onStartJob(Landroid/app/job/JobParameters;)Z'; Replacement = $startJobNoop },
  @{ Path = 'smali_classes10/com/google/android/datatransport/runtime/scheduling/jobscheduling/JobInfoSchedulerService.smali'; Header = '.method public onStopJob(Landroid/app/job/JobParameters;)Z'; Replacement = $stopJobNoop },
  @{ Path = 'smali_classes11/com/facebook/messenger/app/background/msys/MsysBackgroundSyncService.smali'; Header = '.method public onStartJob(Landroid/app/job/JobParameters;)Z'; Replacement = $startJobNoop },
  @{ Path = 'smali_classes11/com/facebook/messenger/app/background/msys/MsysBackgroundSyncService.smali'; Header = '.method public onStopJob(Landroid/app/job/JobParameters;)Z'; Replacement = $stopJobNoop },
  @{ Path = 'smali_classes12/com/facebook/falco/jobscheduler/FFJobService.smali'; Header = '.method public onStartJob(Landroid/app/job/JobParameters;)Z'; Replacement = $startJobNoop },
  @{ Path = 'smali_classes12/com/facebook/falco/jobscheduler/FFJobService.smali'; Header = '.method public onStopJob(Landroid/app/job/JobParameters;)Z'; Replacement = $stopJobNoop },
  @{ Path = 'smali_classes3/androidx/work/impl/background/systemjob/SystemJobService.smali'; Header = '.method public onStartJob(Landroid/app/job/JobParameters;)Z'; Replacement = $startJobNoop },
  @{ Path = 'smali_classes3/androidx/work/impl/background/systemjob/SystemJobService.smali'; Header = '.method public onStopJob(Landroid/app/job/JobParameters;)Z'; Replacement = $stopJobNoop },
  @{ Path = 'smali_classes3/com/facebook/analytics2/logger/legacy/uploader/LollipopUploadService.smali'; Header = '.method public onStartCommand(Landroid/content/Intent;II)I'; Replacement = $startCommandNoop },
  @{ Path = 'smali_classes3/com/facebook/analytics2/logger/legacy/uploader/LollipopUploadService.smali'; Header = '.method public onStartJob(Landroid/app/job/JobParameters;)Z'; Replacement = $startJobNoop },
  @{ Path = 'smali_classes3/com/facebook/analytics2/logger/legacy/uploader/LollipopUploadService.smali'; Header = '.method public onStopJob(Landroid/app/job/JobParameters;)Z'; Replacement = $stopJobNoop },
  @{ Path = 'smali_classes3/com/facebook/oxygen/preloads/sdk/firstparty/managedappcache/IsManagedAppCacheJobService.smali'; Header = '.method public onStartJob(Landroid/app/job/JobParameters;)Z'; Replacement = $startJobNoop },
  @{ Path = 'smali_classes3/com/facebook/oxygen/preloads/sdk/firstparty/managedappcache/IsManagedAppCacheJobService.smali'; Header = '.method public onStopJob(Landroid/app/job/JobParameters;)Z'; Replacement = $stopJobNoop },
  @{ Path = 'smali_classes10/com/facebook/common/jobscheduler/compat/GcmTaskServiceCompat.smali'; Header = '.method public final onStartCommand(Landroid/content/Intent;II)I'; Replacement = $startCommandNoopFinal },
  @{ Path = 'smali_classes3/com/facebook/common/jobscheduler/compat/JobServiceCompat.smali'; Header = '.method public final onStartCommand(Landroid/content/Intent;II)I'; Replacement = $startCommandNoopFinal },
  @{ Path = 'smali_classes3/com/facebook/common/jobscheduler/compat/JobServiceCompat.smali'; Header = '.method public final onStartJob(Landroid/app/job/JobParameters;)Z'; Replacement = $startJobNoopFinal },
  @{ Path = 'smali_classes3/com/facebook/common/jobscheduler/compat/JobServiceCompat.smali'; Header = '.method public final onStopJob(Landroid/app/job/JobParameters;)Z'; Replacement = $stopJobNoopFinal },
  @{ Path = 'smali_classes4/com/facebook/analytics2/logger/legacy/uploader/AlarmBasedUploadService.smali'; Header = '.method public onBind(Landroid/content/Intent;)Landroid/os/IBinder;'; Replacement = $binderNoopPublic },
  @{ Path = 'smali_classes4/com/facebook/analytics2/logger/legacy/uploader/AlarmBasedUploadService.smali'; Header = '.method public onStartCommand(Landroid/content/Intent;II)I'; Replacement = $startCommandNoop },
  @{ Path = 'smali_classes10/com/google/android/gms/analytics/AnalyticsJobService.smali'; Header = '.method public final onStartCommand(Landroid/content/Intent;II)I'; Replacement = $startCommandNoopFinal },
  @{ Path = 'smali_classes10/com/google/android/gms/analytics/AnalyticsJobService.smali'; Header = '.method public final onStartJob(Landroid/app/job/JobParameters;)Z'; Replacement = $startJobNoopFinal },
  @{ Path = 'smali_classes10/com/google/android/gms/analytics/AnalyticsJobService.smali'; Header = '.method public final onStopJob(Landroid/app/job/JobParameters;)Z'; Replacement = $stopJobNoopFinal },
  @{ Path = 'smali_classes10/com/facebook/screencast/ScreencastService.smali'; Header = '.method public onBind(Landroid/content/Intent;)Landroid/os/IBinder;'; Replacement = $binderNoopPublic },
  @{ Path = 'smali_classes10/com/facebook/screencast/ScreencastService.smali'; Header = '.method public onStartCommand(Landroid/content/Intent;II)I'; Replacement = $startCommandNoop },
  @{ Path = 'smali_classes4/com/facebook/video/heroplayer/service/HeroKeepAliveService.smali'; Header = '.method public onBind(Landroid/content/Intent;)Landroid/os/IBinder;'; Replacement = $binderNoopPublic },
  @{ Path = 'smali_classes4/com/facebook/video/heroplayer/service/HeroKeepAliveService.smali'; Header = '.method public onStartCommand(Landroid/content/Intent;II)I'; Replacement = $startCommandNoop },
  @{ Path = 'smali_classes10/com/google/android/gms/analytics/AnalyticsService.smali'; Header = '.method public final onBind(Landroid/content/Intent;)Landroid/os/IBinder;'; Replacement = $binderNoop },
  @{ Path = 'smali_classes10/com/google/android/gms/analytics/AnalyticsService.smali'; Header = '.method public final onStartCommand(Landroid/content/Intent;II)I'; Replacement = $startCommandNoopFinal },
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
