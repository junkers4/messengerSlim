<#
.SYNOPSIS
  Nuclear chat-only patch: removes EVERYTHING except core chat functionality.
  Applies UI cuts, background/battery kills, service/receiver neutralization,
  and broad activity sweeps with a tight whitelist.

.DESCRIPTION
  Run on a CLEAN decompiled_nres tree. This is the all-in-one script.
  After this, only apply-custom-icon.ps1 + rebuild-sign.ps1 are needed.
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

# ── Helpers ──────────────────────────────────────────────────────────────

function Replace-MethodByHeader {
  param(
    [string]$Content,
    [string]$MethodHeader,
    [string]$Replacement
  )
  $pattern = '(?s)' + [regex]::Escape($MethodHeader) + '.*?\.end method'
  return [regex]::Replace($Content, $pattern, $Replacement, 1)
}

function Replace-MethodByRegex {
  param(
    [string]$Content,
    [string]$Pattern,
    [string]$Replacement
  )
  return [regex]::Replace($Content, $Pattern, $Replacement)
}

# ── Stats ────────────────────────────────────────────────────────────────
$stats = @{
  ActivityPatched = 0
  ActivitySkipped = 0
  ActivityMissing = 0
  ActivityWhitelisted = 0
  ServicePatched = 0
  ServiceSkipped = 0
  ReceiverPatched = 0
  ReceiverSkipped = 0
  BroadActivityPatched = 0
  BroadActivityAlready = 0
  BroadServicePatched = 0
  BroadReceiverPatched = 0
  BroadProviderPatched = 0
}

# ════════════════════════════════════════════════════════════════════════
# PHASE 1: Targeted UI activity kills (from apply-nonai-cuts aggressive)
# ════════════════════════════════════════════════════════════════════════
Write-Host "`n═══ PHASE 1: Targeted UI Activity Cuts ═══" -ForegroundColor Magenta

$uiTargets = @(
  # Core non-chat
  'smali_classes5/com/facebook/stickers/store/StickerStoreActivity.smali',
  'smali_classes6/com/facebook/messaging/reels/unreadsettings/UnreadReelsSettingsActivity.smali',
  'smali_classes7/com/facebook/messaging/marketplace/meetingplan/MeetingPlanActivity.smali',
  'smali_classes7/com/facebook/messaging/marketplace/viewlisting/ThreadNotificationViewListingActivity.smali',
  'smali_classes7/com/facebook/messaging/montage/prefs/MontagePreferenceActivity.smali',
  'smali_classes8/com/facebook/messaging/wellbeing/unknowncontact/messagerequests/threadlist/MessageRequestsActivity.smali',
  # Extended
  'smali_classes11/com/facebook/messaging/montage/audience/picker/MontageAudiencePickerActivity.smali',
  'smali_classes4/com/facebook/messaging/montage/composer/MontageComposerActivity.smali',
  'smali_classes7/com/facebook/messaging/business/common/activity/BusinessActivity.smali',
  'smali_classes7/com/facebook/messaging/montage/prefs/MontageAudiencePreferenceActivity.smali',
  'smali_classes8/com/facebook/messaging/history/HistoryActivity.smali',
  'smali_classes8/com/facebook/messaging/montage/blocking/MontageHiddenUsersActivity.smali',
  'smali_classes8/com/facebook/messaging/montage/viewer/MontageViewerActivity.smali',
  'smali_classes8/com/facebook/messaging/montage/viewer/MontageViewerBubblesActivity.smali',
  'smali_classes8/com/facebook/messaging/search/messages/MessageSearchActivity.smali',
  'smali_classes9/com/facebook/messaging/montage/list/MontageListActivity.smali',
  'smali_classes9/com/facebook/messaging/montage/viewer/StoryPreviewPopupActivity.smali',
  # Aggressive UI
  'smali_classes7/com/facebook/messaging/avatar/socialstickers/ui/settings/AvatarUsabilitySettingActivity.smali',
  'smali_classes8/com/facebook/messaging/about/MessengerAboutLicenseActivity.smali',
  'smali_classes7/com/facebook/messaging/memories/viewer/activity/MemoryViewerActivity.smali',
  'smali_classes8/com/facebook/messaging/attribution/ChatHeadsReplyFlowHandlerActivity.smali',
  'smali_classes7/com/facebook/messaging/users/username/EditUsernameActivity.smali',
  'smali_classes8/com/facebook/messaging/users/displayname/ChangeDisplayNameSettingsActivity.smali',
  'smali_classes9/com/facebook/nativetemplates/fb/shell/messenger/NativeTemplatesMessengerActivity.smali',
  'smali_classes8/com/facebook/runtimepermissions/RequestPermissionsActivity.smali',
  'smali_classes7/com/facebook/messaging/accountswitch/switchoneclick/SwitchOneClickActivity.smali',
  'smali_classes10/com/facebook/messaging/accountswitch/SwitchAccountActivity.smali',
  'smali_classes10/com/facebook/messaging/accountswitch/silent/SwitchAccountsSilentActivity.smali',
  'smali_classes8/com/facebook/quickpromotion/debug/QuickPromotionSettingsActivity.smali',
  'smali_classes4/com/facebook/quickpromotion/debug/QuickPromotionFiltersActivity.smali',
  'smali_classes8/com/facebook/quickpromotion/debug/QuickPromotionTriggersActivity.smali',
  'smali_classes8/com/facebook/quickpromotion/debug/SeguePreviewSettingsActivity.smali',
  'smali_classes8/com/facebook/debug/perfoverlay/PerfOverlaySettingsActivity.smali',
  'smali_classes8/com/facebook/debug/saddataoverlay/SadDataOverlaySettingsActivity.smali',
  'smali_classes10/com/facebook/messaging/internalprefs/fxpf/FXPFDebugActivity.smali',
  'smali_classes7/com/facebook/messaging/internalprefs/presence/MessengerInternalPresenceDebugActivity.smali',
  'smali_classes8/com/facebook/messaging/internalprefs/MessengerInternalBasePreferenceActivity.smali',
  'smali_classes8/com/facebook/messaging/internalprefs/burner/MessengerInternalBurnerActivity.smali',
  'smali_classes8/com/facebook/messaging/internalprefs/burner/MessengerInternalBurnerBulkReceiveActivity.smali',
  'smali_classes8/com/facebook/messaging/internalprefs/burner/MessengerInternalBurnerBulkSendActivity.smali',
  'smali_classes8/com/facebook/messaging/internalprefs/burner/MessengerInternalBurnerHistoryFlowActivity.smali',
  'smali_classes8/com/facebook/messaging/internalprefs/omnistore/preference/MessengerInternalOmnistoreCollectionInfoActivity.smali',
  'smali_classes8/com/facebook/payments/p2p/messenger/core/prefs/PaymentsPreferenceActivity.smali',
  'smali_classes8/com/facebook/payments/p2p/messenger/core/prefs/transactions/MessengerPayHistoryActivity.smali',
  'smali_classes8/com/facebook/payments/p2p/awareness/PaymentAwarenessActivity.smali',
  'smali_classes8/com/facebook/payments/p2p/messenger/core/prefs/method/verification/PaymentMethodVerificationHostActivity.smali',
  'smali_classes8/com/facebook/payments/picker/PickerScreenActivity.smali',
  'smali_classes8/com/facebook/payments/shipping/form/ShippingAddressActivity.smali',
  'smali_classes8/com/facebook/payments/paymentmethods/cardform/CardFormActivity.smali',
  'smali_classes10/com/facebook/messaging/settings/surface/MessengerSettingActivity.smali',
  'smali_classes7/com/facebook/messaging/settings/mesettings/surface/MessengerMePreferenceActivity.smali',
  'smali_classes7/com/facebook/messaging/threadsettings2/activity/ThreadSettingsActivity.smali',
  'smali_classes9/com/facebook/messaging/graph/contactmanagement/AllContactsActivity.smali',
  'smali_classes9/com/facebook/fbavatar/FbAvatarEditorBaseActivity.smali',
  'smali_classes10/com/facebook/messaging/business/plugins/suggestedreply/settings/mesettings/SuggestedReplySettingsActivity.smali',
  'smali_classes10/com/facebook/messaging/earlyaccess/settingsui/EarlyAccessSettingActivity.smali',
  'smali_classes10/com/facebook/messaging/encryptedbackups/setting/infiniteretroactivebackups/activity/EbIrbSettingsActivity.smali',
  'smali_classes10/com/facebook/messaging/wellbeing/unknowncontact/reachabilitycontrols/activity/ReachabilitySettingsActivity.smali',
  'smali_classes6/com/facebook/messaging/accessibility/settingsurface/AccessibilitySettingActivity.smali',
  'smali_classes6/com/facebook/messaging/accessibility/settingsurface/ColorFilterSettingActivity.smali',
  'smali_classes6/com/facebook/messaging/accessibility/settingsurface/motionreduction/MotionReductionSettingActivity.smali',
  'smali_classes6/com/facebook/messaging/composer/keyboardsettings/MessengerKeyboardPreferenceActivity.smali',
  'smali_classes6/com/facebook/messaging/encryptedbackups/infiniteretroactivebackups/setup/upsell/activity/StorageSettingsEbUpsellActivity.smali',
  'smali_classes6/com/facebook/messaging/memories/appsettingsui/MemoriesAppSettingsActivity.smali',
  'smali_classes6/com/facebook/messaging/privacycheckup/privacycheckupsetting/PrivacyCheckupSettingsActivity.smali',
  'smali_classes7/com/facebook/messaging/business/ads/orderhistory/ui/InboxOrderHistorySettingActivity.smali',
  'smali_classes7/com/facebook/messaging/business/bizrtc/pagertcsettings/PageAudioAndVideoCallsSettingActivity.smali',
  'smali_classes7/com/facebook/messaging/dma/ui/MsplitConsentSettingsActivity.smali',
  'smali_classes7/com/facebook/messaging/invites/settingsurface/InviteFriendSettingActivity.smali',
  'smali_classes7/com/facebook/messaging/professionalmode/businesstools/settings/ProModeBusinessToolsSettingsActivity.smali',
  'smali_classes7/com/facebook/messaging/wellbeing/safetyecosystem/authenticinteractions/ondevicesafetydetection/activity/SafetyDetectionSettingsActivity.smali',
  'smali_classes8/com/facebook/messaging/lid/appsettingsui/LanguageIdentificationSettingsActivity.smali',
  'smali_classes8/com/facebook/messaging/nativepagereply/faq/mesettings/BusinessInboxFAQSettingActivity.smali',
  'smali_classes8/com/facebook/messaging/search/edithistory/M3SearchEditHistoryActivity.smali',
  'smali_classes8/com/facebook/zero/zerobalance/ui/AutoflexSettingsActivity.smali',
  'smali_classes9/com/facebook/offlineexperiment/internalsettings/OfflineExperimentPreferenceActivity.smali',
  # Chat-only extreme additions
  'smali_classes3/com/facebook/rtc/activities/CallPermissionsActivity.smali',
  'smali_classes5/com/facebook/rtc/activities/ZeroRatingActivity.smali',
  'smali_classes7/com/facebook/rtc/activities/integrityprecall/MinorSafetyNoticeActivity.smali',
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
  'smali_classes3/com/facebook/messenger/intents/IntentHandlerActivity.smali',
  # Meta AI / AI Bot entrypoints
  'smali_classes6/com/facebook/messaging/aibot/launcher/activity/AiStudioFragmentActivity.smali',
  'smali_classes6/com/facebook/messaging/aibot/launcher/activity/AiBotImmersiveThreadActivity.smali',
  'smali_classes6/com/facebook/xapp/messaging/composer/aibot/discover/activity/AiBotDiscoverActivity.smali',
  'smali_classes6/com/facebook/messaging/aibot/memory/activity/ManageAiMemorySettingsActivity.smali',
  'smali_classes6/com/facebook/messaging/aibot/launcher/activity/AiStudioCreationFragmentActivity.smali',
  'smali_classes6/com/facebook/messaging/aibot/launcher/activity/AiStudioProfileActivity.smali',
  'smali_classes6/com/facebook/messaging/aibot/launcher/activity/AiStudioCharacterProfileCreationActivity.smali',
  'smali_classes6/com/facebook/messaging/aibot/launcher/activity/AiStudioImmersiveThreadFirstBloksActivity.smali',
  'smali_classes6/com/facebook/messaging/aibot/appsettingsui/MetaAiLookupSettingsActivity.smali',
  'smali_classes6/com/facebook/messaging/aibot/botpicker/ugcbot/inthreadaibotediting/InThreadAiBotEditingActivity.smali',
  'smali_classes6/com/facebook/messaging/aibot/rpg/ScenesRPGIntroActivity.smali'
)

$a32Replace = @"
.method public A32(Landroid/os/Bundle;)V
    .locals 0
    invoke-super {p0, p1}, Lcom/facebook/base/activity/FbFragmentActivity;->A32(Landroid/os/Bundle;)V
    invoke-virtual {p0}, Lcom/facebook/base/activity/FbFragmentActivity;->finish()V
    return-void
.end method
"@

$onCreateReplace = @"
.method public onCreate(Landroid/os/Bundle;)V
    .locals 0
    invoke-super {p0, p1}, Lcom/facebook/base/activity/FbFragmentActivity;->onCreate(Landroid/os/Bundle;)V
    invoke-virtual {p0}, Lcom/facebook/base/activity/FbFragmentActivity;->finish()V
    return-void
.end method
"@

$a0FReplace = @"
.method public A0F(Landroid/os/Bundle;)V
    .locals 0
    invoke-super {p0, p1}, Lcom/facebook/base/activity/FbPreferenceActivity;->A0F(Landroid/os/Bundle;)V
    invoke-virtual {p0}, Lcom/facebook/base/activity/FbPreferenceActivity;->finish()V
    return-void
.end method
"@

foreach ($rel in ($uiTargets | Select-Object -Unique)) {
  $path = Join-Path $base $rel
  if (-not (Test-Path $path)) {
    $stats.ActivityMissing++
    continue
  }
  $content = Get-Content -Raw -Path $path
  if ($content -match '->finish\(\)V') {
    $stats.ActivitySkipped++
    continue
  }
  $updated = $content
  $updated = [regex]::Replace($updated, '(?s)\.method public A32\(Landroid/os/Bundle;\)V.*?\.end method', $a32Replace, 1)
  if ($updated -eq $content) {
    $updated = [regex]::Replace($content, '(?s)\.method public onCreate\(Landroid/os/Bundle;\)V.*?\.end method', $onCreateReplace, 1)
  }
  if ($updated -eq $content) {
    $updated = [regex]::Replace($content, '(?s)\.method public A0F\(Landroid/os/Bundle;\)V.*?\.end method', $a0FReplace, 1)
  }
  if ($updated -ne $content) {
    Set-Content -Path $path -Value $updated -NoNewline
    $stats.ActivityPatched++
  } else {
    $stats.ActivitySkipped++
  }
}

Write-Host "Phase 1 done: Patched=$($stats.ActivityPatched), Skipped=$($stats.ActivitySkipped), Missing=$($stats.ActivityMissing)" -ForegroundColor Cyan

# ════════════════════════════════════════════════════════════════════════
# PHASE 2: Targeted battery/background service+receiver kills
# ════════════════════════════════════════════════════════════════════════
Write-Host "`n═══ PHASE 2: Targeted Battery/Background Kills ═══" -ForegroundColor Magenta

$batteryRules = @(
  # Receivers - A05 no-op
  @{ P='smali/com/facebook/fdidlite/FDIDSyncLiteReceiver.smali'; H='.method public A05(Landroid/content/Context;Landroid/content/Intent;LX/0NL;)V'; R=".method public A05(Landroid/content/Context;Landroid/content/Intent;LX/0NL;)V`n    .locals 0`n`n    return-void`n.end method" },
  @{ P='smali_classes4/com/facebook/messaging/chatheads/service/ChatHeadsServiceBroadcastReceiver.smali'; H='.method public A05(Landroid/content/Context;Landroid/content/Intent;LX/0NL;)V'; R=".method public A05(Landroid/content/Context;Landroid/content/Intent;LX/0NL;)V`n    .locals 0`n`n    return-void`n.end method" },
  @{ P='smali/com/facebook/messenger/app/background/MessengerBackgroundWakeupReceiver.smali'; H='.method public A05(Landroid/content/Context;Landroid/content/Intent;LX/0NL;)V'; R=".method public A05(Landroid/content/Context;Landroid/content/Intent;LX/0NL;)V`n    .locals 0`n`n    return-void`n.end method" },
  @{ P='smali_classes10/com/facebook/bugreporter/core/scheduler/AlarmsBroadcastReceiver.smali'; H='.method public A05(Landroid/content/Context;Landroid/content/Intent;LX/0NL;)V'; R=".method public A05(Landroid/content/Context;Landroid/content/Intent;LX/0NL;)V`n    .locals 0`n`n    return-void`n.end method" },
  @{ P='smali_classes12/com/facebook/analytics2/fabric/onefabric/OneFabricUploadAlarmReceiver.smali'; H='.method public A05(Landroid/content/Context;Landroid/content/Intent;LX/0NL;)V'; R=".method public A05(Landroid/content/Context;Landroid/content/Intent;LX/0NL;)V`n    .locals 0`n`n    return-void`n.end method" },
  @{ P='smali_classes7/com/facebook/messaging/messengerprefs/notif/NoteNotificationBroadcastReceiver.smali'; H='.method public A05(Landroid/content/Context;Landroid/content/Intent;LX/0NL;)V'; R=".method public A05(Landroid/content/Context;Landroid/content/Intent;LX/0NL;)V`n    .locals 0`n`n    return-void`n.end method" },
  @{ P='smali_classes7/com/facebook/messaging/notify/plugins/handler/messagereminder/MessageNotificationSurveyReceiver.smali'; H='.method public A05(Landroid/content/Context;Landroid/content/Intent;LX/0NL;)V'; R=".method public A05(Landroid/content/Context;Landroid/content/Intent;LX/0NL;)V`n    .locals 0`n`n    return-void`n.end method" },
  @{ P='smali_classes8/com/facebook/messaging/notify/client/MessagesNotificationBroadcastReceiver.smali'; H='.method public A05(Landroid/content/Context;Landroid/content/Intent;LX/0NL;)V'; R=".method public A05(Landroid/content/Context;Landroid/content/Intent;LX/0NL;)V`n    .locals 0`n`n    return-void`n.end method" },
  @{ P='smali_classes4/com/facebook/push/registration/RegistrarHelperReceiver.smali'; H='.method public A05(Landroid/content/Context;Landroid/content/Intent;LX/0NL;)V'; R=".method public A05(Landroid/content/Context;Landroid/content/Intent;LX/0NL;)V`n    .locals 0`n`n    return-void`n.end method" },
  @{ P='smali_classes8/com/facebook/push/nna/NNABroadcastReceiver.smali'; H='.method public A05(Landroid/content/Context;Landroid/content/Intent;LX/0NL;)V'; R=".method public A05(Landroid/content/Context;Landroid/content/Intent;LX/0NL;)V`n    .locals 0`n`n    return-void`n.end method" },
  # Services - A03 no-op
  @{ P='smali_classes4/com/facebook/messaging/notify/logging/MessagesSystemTrayLogService.smali'; H='.method public A03(Landroid/content/Intent;)V'; R=".method public A03(Landroid/content/Intent;)V`n    .locals 0`n`n    return-void`n.end method" },
  @{ P='smali_classes4/com/facebook/messengercar/CarNotificationService.smali'; H='.method public A03(Landroid/content/Intent;)V'; R=".method public A03(Landroid/content/Intent;)V`n    .locals 0`n`n    return-void`n.end method" },
  @{ P='smali_classes7/com/facebook/messaging/notify/service/MessengerDirectReplyService.smali'; H='.method public A03(Landroid/content/Intent;)V'; R=".method public A03(Landroid/content/Intent;)V`n    .locals 0`n`n    return-void`n.end method" },
  @{ P='smali_classes7/com/facebook/messaging/notify/service/MessengerLikeService.smali'; H='.method public A03(Landroid/content/Intent;)V'; R=".method public A03(Landroid/content/Intent;)V`n    .locals 0`n`n    return-void`n.end method" },
  @{ P='smali_classes7/com/facebook/account/twofac/protocol/LoginApprovalNotificationService.smali'; H='.method public A03(Landroid/content/Intent;)V'; R=".method public A03(Landroid/content/Intent;)V`n    .locals 0`n`n    return-void`n.end method" },
  # Chooser
  @{ P='smali_classes7/com/facebook/messaging/directshare/DirectShareChooserTargetService.smali'; H='.method public onGetChooserTargets(Landroid/content/ComponentName;Landroid/content/IntentFilter;)Ljava/util/List;'; R=".method public onGetChooserTargets(Landroid/content/ComponentName;Landroid/content/IntentFilter;)Ljava/util/List;`n    .locals 1`n`n    invoke-static {}, Ljava/util/Collections;->emptyList()Ljava/util/List;`n`n    move-result-object v0`n`n    return-object v0`n.end method" },
  @{ P='smali_classes10/androidx/sharetarget/ChooserTargetServiceCompat.smali'; H='.method public onGetChooserTargets(Landroid/content/ComponentName;Landroid/content/IntentFilter;)Ljava/util/List;'; R=".method public onGetChooserTargets(Landroid/content/ComponentName;Landroid/content/IntentFilter;)Ljava/util/List;`n    .locals 1`n`n    invoke-static {}, Ljava/util/Collections;->emptyList()Ljava/util/List;`n`n    move-result-object v0`n`n    return-object v0`n.end method" },
  # MQTT/Push - onStartCommand
  @{ P='smali/com/facebook/mqttlite/MqttService.smali'; H='.method public onStartCommand(Landroid/content/Intent;II)I'; R=".method public onStartCommand(Landroid/content/Intent;II)I`n    .locals 1`n`n    const/4 v0, 0x2`n`n    return v0`n.end method" },
  @{ P='smali/com/facebook/push/fbnslite/PushSdkFbnsReceiverService.smali'; H='.method public onStartCommand(Landroid/content/Intent;II)I'; R=".method public onStartCommand(Landroid/content/Intent;II)I`n    .locals 1`n`n    const/4 v0, 0x2`n`n    return v0`n.end method" },
  # WorkManager/SystemAlarm
  @{ P='smali_classes10/androidx/work/impl/background/systemalarm/SystemAlarmService.smali'; H='.method public onStartCommand(Landroid/content/Intent;II)I'; R=".method public onStartCommand(Landroid/content/Intent;II)I`n    .locals 1`n`n    const/4 v0, 0x2`n`n    return v0`n.end method" },
  # Analytics/Upload services
  @{ P='smali_classes10/com/facebook/analytics2/logger/GooglePlayUploadService.smali'; H='.method public onStartCommand(Landroid/content/Intent;II)I'; R=".method public onStartCommand(Landroid/content/Intent;II)I`n    .locals 1`n`n    const/4 v0, 0x2`n`n    return v0`n.end method" },
  @{ P='smali_classes10/com/facebook/analytics2/logger/service/LollipopUploadSafeService.smali'; H='.method public onStartCommand(Landroid/content/Intent;II)I'; R=".method public onStartCommand(Landroid/content/Intent;II)I`n    .locals 1`n`n    const/4 v0, 0x2`n`n    return v0`n.end method" },
  @{ P='smali_classes10/com/facebook/analytics2/logger/service/LollipopUploadSafeService.smali'; H='.method public onStartJob(Landroid/app/job/JobParameters;)Z'; R=".method public onStartJob(Landroid/app/job/JobParameters;)Z`n    .locals 1`n`n    const/4 v0, 0x0`n`n    return v0`n.end method" },
  @{ P='smali_classes10/com/facebook/analytics2/logger/service/LollipopUploadSafeService.smali'; H='.method public onStopJob(Landroid/app/job/JobParameters;)Z'; R=".method public onStopJob(Landroid/app/job/JobParameters;)Z`n    .locals 1`n`n    const/4 v0, 0x0`n`n    return v0`n.end method" },
  # AdvancedCrypto notification service
  @{ P='smali_classes10/com/facebook/advancedcryptotransport/api/impl/service/AdvancedCryptoTransportNotificationService.smali'; H='.method public onStartCommand(Landroid/content/Intent;II)I'; R=".method public onStartCommand(Landroid/content/Intent;II)I`n    .locals 1`n`n    const/4 v0, 0x2`n`n    return v0`n.end method" },
  @{ P='smali_classes10/com/facebook/advancedcryptotransport/api/impl/service/AdvancedCryptoTransportNotificationService.smali'; H='.method public onBind(Landroid/content/Intent;)Landroid/os/IBinder;'; R=".method public onBind(Landroid/content/Intent;)Landroid/os/IBinder;`n    .locals 1`n`n    const/4 v0, 0x0`n`n    return-object v0`n.end method" },
  # Video link handler
  @{ P='smali_classes10/com/facebook/messaging/linkhandling/plugins/facebookvideolinkhandler/implementation/videolinkhandlerhelper/VideoLinkHandlerHelperNotificationService.smali'; H='.method public onStartCommand(Landroid/content/Intent;II)I'; R=".method public onStartCommand(Landroid/content/Intent;II)I`n    .locals 1`n`n    const/4 v0, 0x2`n`n    return v0`n.end method" },
  @{ P='smali_classes10/com/facebook/messaging/linkhandling/plugins/facebookvideolinkhandler/implementation/videolinkhandlerhelper/VideoLinkHandlerHelperNotificationService.smali'; H='.method public onBind(Landroid/content/Intent;)Landroid/os/IBinder;'; R=".method public onBind(Landroid/content/Intent;)Landroid/os/IBinder;`n    .locals 1`n`n    const/4 v0, 0x0`n`n    return-object v0`n.end method" },
  # Oxygen/Installer jobs
  @{ P='smali_classes10/com/facebook/voltron/download/scheduledinstaller/OxygenScheduledInstallerJobService.smali'; H='.method public onStartJob(Landroid/app/job/JobParameters;)Z'; R=".method public onStartJob(Landroid/app/job/JobParameters;)Z`n    .locals 1`n`n    const/4 v0, 0x0`n`n    return v0`n.end method" },
  @{ P='smali_classes10/com/facebook/voltron/download/scheduledinstaller/OxygenScheduledInstallerJobService.smali'; H='.method public onStopJob(Landroid/app/job/JobParameters;)Z'; R=".method public onStopJob(Landroid/app/job/JobParameters;)Z`n    .locals 1`n`n    const/4 v0, 0x0`n`n    return v0`n.end method" },
  # Google DataTransport
  @{ P='smali_classes10/com/google/android/datatransport/runtime/scheduling/jobscheduling/JobInfoSchedulerService.smali'; H='.method public onStartJob(Landroid/app/job/JobParameters;)Z'; R=".method public onStartJob(Landroid/app/job/JobParameters;)Z`n    .locals 1`n`n    const/4 v0, 0x0`n`n    return v0`n.end method" },
  @{ P='smali_classes10/com/google/android/datatransport/runtime/scheduling/jobscheduling/JobInfoSchedulerService.smali'; H='.method public onStopJob(Landroid/app/job/JobParameters;)Z'; R=".method public onStopJob(Landroid/app/job/JobParameters;)Z`n    .locals 1`n`n    const/4 v0, 0x0`n`n    return v0`n.end method" },
  # Msys background sync
  @{ P='smali_classes11/com/facebook/messenger/app/background/msys/MsysBackgroundSyncService.smali'; H='.method public onStartJob(Landroid/app/job/JobParameters;)Z'; R=".method public onStartJob(Landroid/app/job/JobParameters;)Z`n    .locals 1`n`n    const/4 v0, 0x0`n`n    return v0`n.end method" },
  @{ P='smali_classes11/com/facebook/messenger/app/background/msys/MsysBackgroundSyncService.smali'; H='.method public onStopJob(Landroid/app/job/JobParameters;)Z'; R=".method public onStopJob(Landroid/app/job/JobParameters;)Z`n    .locals 1`n`n    const/4 v0, 0x0`n`n    return v0`n.end method" },
  # Falco job scheduler
  @{ P='smali_classes12/com/facebook/falco/jobscheduler/FFJobService.smali'; H='.method public onStartJob(Landroid/app/job/JobParameters;)Z'; R=".method public onStartJob(Landroid/app/job/JobParameters;)Z`n    .locals 1`n`n    const/4 v0, 0x0`n`n    return v0`n.end method" },
  @{ P='smali_classes12/com/facebook/falco/jobscheduler/FFJobService.smali'; H='.method public onStopJob(Landroid/app/job/JobParameters;)Z'; R=".method public onStopJob(Landroid/app/job/JobParameters;)Z`n    .locals 1`n`n    const/4 v0, 0x0`n`n    return v0`n.end method" },
  # SystemJobService (WorkManager)
  @{ P='smali_classes3/androidx/work/impl/background/systemjob/SystemJobService.smali'; H='.method public onStartJob(Landroid/app/job/JobParameters;)Z'; R=".method public onStartJob(Landroid/app/job/JobParameters;)Z`n    .locals 1`n`n    const/4 v0, 0x0`n`n    return v0`n.end method" },
  @{ P='smali_classes3/androidx/work/impl/background/systemjob/SystemJobService.smali'; H='.method public onStopJob(Landroid/app/job/JobParameters;)Z'; R=".method public onStopJob(Landroid/app/job/JobParameters;)Z`n    .locals 1`n`n    const/4 v0, 0x0`n`n    return v0`n.end method" },
  # Legacy upload services
  @{ P='smali_classes3/com/facebook/analytics2/logger/legacy/uploader/LollipopUploadService.smali'; H='.method public onStartCommand(Landroid/content/Intent;II)I'; R=".method public onStartCommand(Landroid/content/Intent;II)I`n    .locals 1`n`n    const/4 v0, 0x2`n`n    return v0`n.end method" },
  @{ P='smali_classes3/com/facebook/analytics2/logger/legacy/uploader/LollipopUploadService.smali'; H='.method public onStartJob(Landroid/app/job/JobParameters;)Z'; R=".method public onStartJob(Landroid/app/job/JobParameters;)Z`n    .locals 1`n`n    const/4 v0, 0x0`n`n    return v0`n.end method" },
  @{ P='smali_classes3/com/facebook/analytics2/logger/legacy/uploader/LollipopUploadService.smali'; H='.method public onStopJob(Landroid/app/job/JobParameters;)Z'; R=".method public onStopJob(Landroid/app/job/JobParameters;)Z`n    .locals 1`n`n    const/4 v0, 0x0`n`n    return v0`n.end method" },
  # Oxygen managed app cache
  @{ P='smali_classes3/com/facebook/oxygen/preloads/sdk/firstparty/managedappcache/IsManagedAppCacheJobService.smali'; H='.method public onStartJob(Landroid/app/job/JobParameters;)Z'; R=".method public onStartJob(Landroid/app/job/JobParameters;)Z`n    .locals 1`n`n    const/4 v0, 0x0`n`n    return v0`n.end method" },
  @{ P='smali_classes3/com/facebook/oxygen/preloads/sdk/firstparty/managedappcache/IsManagedAppCacheJobService.smali'; H='.method public onStopJob(Landroid/app/job/JobParameters;)Z'; R=".method public onStopJob(Landroid/app/job/JobParameters;)Z`n    .locals 1`n`n    const/4 v0, 0x0`n`n    return v0`n.end method" },
  # GcmTaskService/JobServiceCompat (final methods)
  @{ P='smali_classes10/com/facebook/common/jobscheduler/compat/GcmTaskServiceCompat.smali'; H='.method public final onStartCommand(Landroid/content/Intent;II)I'; R=".method public final onStartCommand(Landroid/content/Intent;II)I`n    .locals 1`n`n    const/4 v0, 0x2`n`n    return v0`n.end method" },
  @{ P='smali_classes3/com/facebook/common/jobscheduler/compat/JobServiceCompat.smali'; H='.method public final onStartCommand(Landroid/content/Intent;II)I'; R=".method public final onStartCommand(Landroid/content/Intent;II)I`n    .locals 1`n`n    const/4 v0, 0x2`n`n    return v0`n.end method" },
  @{ P='smali_classes3/com/facebook/common/jobscheduler/compat/JobServiceCompat.smali'; H='.method public final onStartJob(Landroid/app/job/JobParameters;)Z'; R=".method public final onStartJob(Landroid/app/job/JobParameters;)Z`n    .locals 1`n`n    const/4 v0, 0x0`n`n    return v0`n.end method" },
  @{ P='smali_classes3/com/facebook/common/jobscheduler/compat/JobServiceCompat.smali'; H='.method public final onStopJob(Landroid/app/job/JobParameters;)Z'; R=".method public final onStopJob(Landroid/app/job/JobParameters;)Z`n    .locals 1`n`n    const/4 v0, 0x0`n`n    return v0`n.end method" },
  # AlarmBasedUploadService
  @{ P='smali_classes4/com/facebook/analytics2/logger/legacy/uploader/AlarmBasedUploadService.smali'; H='.method public onBind(Landroid/content/Intent;)Landroid/os/IBinder;'; R=".method public onBind(Landroid/content/Intent;)Landroid/os/IBinder;`n    .locals 1`n`n    const/4 v0, 0x0`n`n    return-object v0`n.end method" },
  @{ P='smali_classes4/com/facebook/analytics2/logger/legacy/uploader/AlarmBasedUploadService.smali'; H='.method public onStartCommand(Landroid/content/Intent;II)I'; R=".method public onStartCommand(Landroid/content/Intent;II)I`n    .locals 1`n`n    const/4 v0, 0x2`n`n    return v0`n.end method" },
  # Google Analytics
  @{ P='smali_classes10/com/google/android/gms/analytics/AnalyticsJobService.smali'; H='.method public final onStartCommand(Landroid/content/Intent;II)I'; R=".method public final onStartCommand(Landroid/content/Intent;II)I`n    .locals 1`n`n    const/4 v0, 0x2`n`n    return v0`n.end method" },
  @{ P='smali_classes10/com/google/android/gms/analytics/AnalyticsJobService.smali'; H='.method public final onStartJob(Landroid/app/job/JobParameters;)Z'; R=".method public final onStartJob(Landroid/app/job/JobParameters;)Z`n    .locals 1`n`n    const/4 v0, 0x0`n`n    return v0`n.end method" },
  @{ P='smali_classes10/com/google/android/gms/analytics/AnalyticsJobService.smali'; H='.method public final onStopJob(Landroid/app/job/JobParameters;)Z'; R=".method public final onStopJob(Landroid/app/job/JobParameters;)Z`n    .locals 1`n`n    const/4 v0, 0x0`n`n    return v0`n.end method" },
  @{ P='smali_classes10/com/google/android/gms/analytics/AnalyticsService.smali'; H='.method public final onBind(Landroid/content/Intent;)Landroid/os/IBinder;'; R=".method public final onBind(Landroid/content/Intent;)Landroid/os/IBinder;`n    .locals 1`n`n    const/4 v0, 0x0`n`n    return-object v0`n.end method" },
  @{ P='smali_classes10/com/google/android/gms/analytics/AnalyticsService.smali'; H='.method public final onStartCommand(Landroid/content/Intent;II)I'; R=".method public final onStartCommand(Landroid/content/Intent;II)I`n    .locals 1`n`n    const/4 v0, 0x2`n`n    return v0`n.end method" },
  # Screencast
  @{ P='smali_classes10/com/facebook/screencast/ScreencastService.smali'; H='.method public onBind(Landroid/content/Intent;)Landroid/os/IBinder;'; R=".method public onBind(Landroid/content/Intent;)Landroid/os/IBinder;`n    .locals 1`n`n    const/4 v0, 0x0`n`n    return-object v0`n.end method" },
  @{ P='smali_classes10/com/facebook/screencast/ScreencastService.smali'; H='.method public onStartCommand(Landroid/content/Intent;II)I'; R=".method public onStartCommand(Landroid/content/Intent;II)I`n    .locals 1`n`n    const/4 v0, 0x2`n`n    return v0`n.end method" },
  # Video keep alive
  @{ P='smali_classes4/com/facebook/video/heroplayer/service/HeroKeepAliveService.smali'; H='.method public onBind(Landroid/content/Intent;)Landroid/os/IBinder;'; R=".method public onBind(Landroid/content/Intent;)Landroid/os/IBinder;`n    .locals 1`n`n    const/4 v0, 0x0`n`n    return-object v0`n.end method" },
  @{ P='smali_classes4/com/facebook/video/heroplayer/service/HeroKeepAliveService.smali'; H='.method public onStartCommand(Landroid/content/Intent;II)I'; R=".method public onStartCommand(Landroid/content/Intent;II)I`n    .locals 1`n`n    const/4 v0, 0x2`n`n    return v0`n.end method" },
  # Wearable
  @{ P='smali_classes3/com/facebook/wearlistener/DataLayerListenerService.smali'; H='.method public final onBind(Landroid/content/Intent;)Landroid/os/IBinder;'; R=".method public final onBind(Landroid/content/Intent;)Landroid/os/IBinder;`n    .locals 1`n`n    const/4 v0, 0x0`n`n    return-object v0`n.end method" },
  @{ P='smali_classes10/com/meta/wearable/acdc/sdk/service/ACDCRegistrationService.smali'; H='.method public A01()Landroid/os/IBinder;'; R=".method public A01()Landroid/os/IBinder;`n    .locals 1`n`n    const/4 v0, 0x0`n`n    return-object v0`n.end method" },
  @{ P='smali_classes10/com/meta/wearable/acdc/sdk/service/ACDCRegistrationService.smali'; H='.method public A04()V'; R=".method public A04()V`n    .locals 0`n`n    return-void`n.end method" }
)

$p2patched = 0
$p2missing = 0
$p2skip = 0
foreach ($rule in $batteryRules) {
  $path = Join-Path $base $rule.P
  if (-not (Test-Path $path)) { $p2missing++; continue }
  $content = Get-Content -Raw -Path $path
  $updated = Replace-MethodByHeader -Content $content -MethodHeader $rule.H -Replacement $rule.R
  if ($updated -ne $content) {
    Set-Content -Path $path -Value $updated -NoNewline
    $p2patched++
  } else { $p2skip++ }
}
Write-Host "Phase 2 done: Patched=$p2patched, Missing=$p2missing, Skipped=$p2skip" -ForegroundColor Cyan

# ════════════════════════════════════════════════════════════════════════
# PHASE 3: BROAD SWEEP — kill ALL background-related smali files
# ════════════════════════════════════════════════════════════════════════
Write-Host "`n═══ PHASE 3: Broad Background Sweep ═══" -ForegroundColor Magenta

$bgPathRegex = '(background|notify|notification|push|mqtt|sync|upload|job|scheduler|alarm|directshare|wear|analytics|bugreport|keepalive|screencast|messengercar|fbnslite|oxygen|datatransport|systemalarm|systemjob|falco|nna|registration|chatheads|reels|montage|stories|sticker|marketplace|payments|rtc|calls|videocall|voip|camera|ar\\b|augmented|avatar|game|playground|memor|commerce|business|ads\\b|adunit|tracking|telemetry|logging|crashreport|errorreport|profiler|perf|debug|internalprefs|experiment|quickpromotion|survey|feedback|sso|accountkit|credential|appindex|deeplinking|shortcut|widget|bubble|pip\\b|floatingwindow|overlay|badge|jewel|aibot|metaai|aistudio|assistant)'

$bgMethodSpecs = @(
  @{ Pattern = '(?s)(\.method(?![^\n]*abstract)[^\n]* A05\(Landroid/content/Context;Landroid/content/Intent;LX/0NL;\)V)\n.*?\.end method'; Replacement = "`$1`n    .locals 0`n`n    return-void`n.end method" },
  @{ Pattern = '(?s)(\.method(?![^\n]*abstract)[^\n]* A03\(Landroid/content/Intent;\)V)\n.*?\.end method'; Replacement = "`$1`n    .locals 0`n`n    return-void`n.end method" },
  @{ Pattern = '(?s)(\.method(?![^\n]*abstract)[^\n]* onStartCommand\(Landroid/content/Intent;II\)I)\n.*?\.end method'; Replacement = "`$1`n    .locals 1`n`n    const/4 v0, 0x2`n`n    return v0`n.end method" },
  @{ Pattern = '(?s)(\.method(?![^\n]*abstract)[^\n]* onStartJob\(Landroid/app/job/JobParameters;\)Z)\n.*?\.end method'; Replacement = "`$1`n    .locals 1`n`n    const/4 v0, 0x0`n`n    return v0`n.end method" },
  @{ Pattern = '(?s)(\.method(?![^\n]*abstract)[^\n]* onStopJob\(Landroid/app/job/JobParameters;\)Z)\n.*?\.end method'; Replacement = "`$1`n    .locals 1`n`n    const/4 v0, 0x0`n`n    return v0`n.end method" },
  @{ Pattern = '(?s)(\.method(?![^\n]*abstract)[^\n]* onBind\(Landroid/content/Intent;\)Landroid/os/IBinder;)\n.*?\.end method'; Replacement = "`$1`n    .locals 1`n`n    const/4 v0, 0x0`n`n    return-object v0`n.end method" },
  @{ Pattern = '(?s)(\.method(?![^\n]*abstract)[^\n]* onGetChooserTargets\(Landroid/content/ComponentName;Landroid/content/IntentFilter;\)Ljava/util/List;)\n.*?\.end method'; Replacement = "`$1`n    .locals 1`n`n    invoke-static {}, Ljava/util/Collections;->emptyList()Ljava/util/List;`n`n    move-result-object v0`n`n    return-object v0`n.end method" },
  @{ Pattern = '(?s)(\.method(?![^\n]*abstract)[^\n]* A01\(\)Landroid/os/IBinder;)\n.*?\.end method'; Replacement = "`$1`n    .locals 1`n`n    const/4 v0, 0x0`n`n    return-object v0`n.end method" },
  @{ Pattern = '(?s)(\.method(?![^\n]*abstract)[^\n]* A04\(\)V)\n.*?\.end method'; Replacement = "`$1`n    .locals 0`n`n    return-void`n.end method" },
  @{ Pattern = '(?s)(\.method(?![^\n]*abstract)[^\n]* onReceive\(Landroid/content/Context;Landroid/content/Intent;\)V)\n.*?\.end method'; Replacement = "`$1`n    .locals 0`n`n    return-void`n.end method" }
)

$p3files = 0
$p3methods = 0
Get-ChildItem -Path $base -Recurse -Filter '*.smali' | Where-Object {
  $_.FullName -match $bgPathRegex
} | ForEach-Object {
  $content = Get-Content -Raw -Path $_.FullName
  $changed = $false
  foreach ($spec in $bgMethodSpecs) {
    $updated = [regex]::Replace($content, $spec.Pattern, $spec.Replacement)
    if ($updated -ne $content) {
      $content = $updated
      $changed = $true
      $p3methods++
    }
  }
  if ($changed) {
    Set-Content -Path $_.FullName -Value $content -NoNewline
    $p3files++
  }
}
Write-Host "Phase 3 done: Files=$p3files, MethodGroups=$p3methods" -ForegroundColor Cyan

# ════════════════════════════════════════════════════════════════════════
# PHASE 4: NUCLEAR ACTIVITY SWEEP — finish() everything not whitelisted
# ════════════════════════════════════════════════════════════════════════
Write-Host "`n═══ PHASE 4: Nuclear Activity Sweep ═══" -ForegroundColor Magenta

# Whitelist: only these path patterns are allowed to survive
$activityWhitelist = @(
  # Third-party / framework base classes — MUST NEVER be patched
  'androidx[\\/]',
  'android[\\/]',
  'com[\\/]google[\\/]',
  'com[\\/]android[\\/]',
  'com[\\/]spotify[\\/]',
  'fx[\\/]thirdpartyoauth',
  # Facebook base activity classes — MUST NEVER be patched
  'base[\\/]activity[\\/]Delegating',
  'orca[\\/](auth|app|Fb|HomeTabs|Droidguard|threadview)',
  'messenger[\\/]neue[\\/]',
  'messenger[\\/]intents[\\/]',
  'resources[\\/]impl[\\/]Waiting',
  'voltron[\\/]',
  'checkpoint[\\/]',
  'rtc[\\/](activities|incall)',
  'messaging[\\/](threadlist|threadview|inbox|composer|lite)',
  'messaging[\\/](model|msys|e2ee|sms|mms)',
  'messaging[\\/]app[\\/]',
  'messenger[\\/]app[\\/]',
  'messenger[\\/]lite',
  'base[\\/]activity',
  'FbFragmentActivity',
  'FbPreferenceActivity',
  'FbActivity',
  'messaging[\\/]conversationlist',
  'messaging[\\/]conversation[\\/]',
  'messaging[\\/]messagelist',
  'messaging[\\/]media[\\/](picker|gallery)',
  'messaging[\\/]attachments',
  'messaging[\\/]photos[\\/]',
  'messaging[\\/]contactpicker',
  'messaging[\\/]newthread',
  'messaging[\\/]forwardpicker',
  'messaging[\\/]groupthread',
  'messaging[\\/]threadcreation',
  'login',
  'auth[\\/]',
  'growth[\\/]',
  'webview',
  'WebViewActivity',
  'browser',
  'CustomTab',
  'TransparentActivity',
  'messaging[\\/]gifpicker',
  'messaging[\\/]emojipicker',
  'messaging[\\/]stickerpicker',
  'messaging[\\/]audioplayer',
  'messaging[\\/]voicemessage',
  'messaging[\\/]richmedia',
  'messaging[\\/]upsell',
  'messaging[\\/]deeplink',
  'messaging[\\/]notification[\\/].*open',
  'messaging[\\/]notification[\\/].*redirect',
  'installreferrer',
  'FacebookContentProvider',
  'InitProvider',
  'WorkManagerInitializer',
  'FileProvider',
  'StartupProvider'
)
$whitelistPattern = ($activityWhitelist | ForEach-Object { "($_)" }) -join '|'

$p4patched = 0
$p4whitelisted = 0
$p4already = 0
$p4nomatch = 0

Get-ChildItem -Path $base -Recurse -Filter '*.smali' | ForEach-Object {
  $top = Get-Content $_.FullName -TotalCount 15 -ErrorAction SilentlyContinue
  if (-not $top) { return }
  $header = $top -join "`n"

  # Only target Activity subclasses
  if ($header -notmatch '\.super\s+L[^\n]*Activity;') { return }

  $rel = $_.FullName.Replace((Resolve-Path $base).Path + '\', '')

  # Skip whitelisted
  if ($rel -match $whitelistPattern) {
    $p4whitelisted++
    return
  }

  $content = Get-Content -Raw -Path $_.FullName

  # Already patched?
  if ($content -match '->finish\(\)V') {
    $p4already++
    return
  }

  $updated = $content
  # Try all known lifecycle entry points
  $updated = [regex]::Replace($updated, '(?s)\.method public A32\(Landroid/os/Bundle;\)V.*?\.end method', $a32Replace, 1)
  if ($updated -eq $content) {
    $updated = [regex]::Replace($content, '(?s)\.method public onCreate\(Landroid/os/Bundle;\)V.*?\.end method', $onCreateReplace, 1)
  }
  if ($updated -eq $content) {
    $updated = [regex]::Replace($content, '(?s)\.method public A0F\(Landroid/os/Bundle;\)V.*?\.end method', $a0FReplace, 1)
  }

  if ($updated -ne $content) {
    Set-Content -Path $_.FullName -Value $updated -NoNewline
    $p4patched++
  } else {
    $p4nomatch++
  }
}
Write-Host "Phase 4 done: Patched=$p4patched, Whitelisted=$p4whitelisted, Already=$p4already, NoMatch=$p4nomatch" -ForegroundColor Cyan

# ════════════════════════════════════════════════════════════════════════
# PHASE 5: NUCLEAR SERVICE/RECEIVER SWEEP — no-op everything possible
# ════════════════════════════════════════════════════════════════════════
Write-Host "`n═══ PHASE 5: Nuclear Service/Receiver Sweep ═══" -ForegroundColor Magenta

# Service whitelist: only keep absolute minimum
$serviceWhitelist = @(
  'messaging[\\/](msys|e2ee|sms|mms)',
  'messaging[\\/]app[\\/]',
  'messenger[\\/]app[\\/]',
  'orca[\\/]app',
  'base[\\/]',
  'InitProvider',
  'WorkManagerInitializer',
  'FileProvider',
  'firebase[\\/]messaging',
  'messaging[\\/]service[\\/]core',
  'messaging[\\/]lite'
)
$svcWhitelistPattern = ($serviceWhitelist | ForEach-Object { "($_)" }) -join '|'

$p5svc = 0
$p5rcv = 0
$p5svcwl = 0
$p5rcvwl = 0

$svcMethodSpecs = @(
  @{ Pattern = '(?s)(\.method(?![^\n]*abstract)[^\n]* onStartCommand\(Landroid/content/Intent;II\)I)\n.*?\.end method'; Replacement = "`$1`n    .locals 1`n`n    const/4 v0, 0x2`n`n    return v0`n.end method" },
  @{ Pattern = '(?s)(\.method(?![^\n]*abstract)[^\n]* onStartJob\(Landroid/app/job/JobParameters;\)Z)\n.*?\.end method'; Replacement = "`$1`n    .locals 1`n`n    const/4 v0, 0x0`n`n    return v0`n.end method" },
  @{ Pattern = '(?s)(\.method(?![^\n]*abstract)[^\n]* onStopJob\(Landroid/app/job/JobParameters;\)Z)\n.*?\.end method'; Replacement = "`$1`n    .locals 1`n`n    const/4 v0, 0x0`n`n    return v0`n.end method" },
  @{ Pattern = '(?s)(\.method(?![^\n]*abstract)[^\n]* onBind\(Landroid/content/Intent;\)Landroid/os/IBinder;)\n.*?\.end method'; Replacement = "`$1`n    .locals 1`n`n    const/4 v0, 0x0`n`n    return-object v0`n.end method" },
  @{ Pattern = '(?s)(\.method(?![^\n]*abstract)[^\n]* A05\(Landroid/content/Context;Landroid/content/Intent;LX/0NL;\)V)\n.*?\.end method'; Replacement = "`$1`n    .locals 0`n`n    return-void`n.end method" },
  @{ Pattern = '(?s)(\.method(?![^\n]*abstract)[^\n]* A03\(Landroid/content/Intent;\)V)\n.*?\.end method'; Replacement = "`$1`n    .locals 0`n`n    return-void`n.end method" },
  @{ Pattern = '(?s)(\.method(?![^\n]*abstract)[^\n]* onReceive\(Landroid/content/Context;Landroid/content/Intent;\)V)\n.*?\.end method'; Replacement = "`$1`n    .locals 0`n`n    return-void`n.end method" }
)

Get-ChildItem -Path $base -Recurse -Filter '*.smali' | ForEach-Object {
  $top = Get-Content $_.FullName -TotalCount 15 -ErrorAction SilentlyContinue
  if (-not $top) { return }
  $header = $top -join "`n"
  $rel = $_.FullName.Replace((Resolve-Path $base).Path + '\', '')

  $isService = $header -match '\.super\s+L[^\n]*(Service|JobService);'
  $isReceiver = $header -match '\.super\s+L[^\n]*Receiver;'

  if (-not $isService -and -not $isReceiver) { return }

  # Whitelist check
  if ($rel -match $svcWhitelistPattern) {
    if ($isService) { $p5svcwl++ } else { $p5rcvwl++ }
    return
  }

  $content = Get-Content -Raw -Path $_.FullName
  $changed = $false
  foreach ($spec in $svcMethodSpecs) {
    $updated = [regex]::Replace($content, $spec.Pattern, $spec.Replacement)
    if ($updated -ne $content) {
      $content = $updated
      $changed = $true
    }
  }
  if ($changed) {
    Set-Content -Path $_.FullName -Value $content -NoNewline
    if ($isService) { $p5svc++ } else { $p5rcv++ }
  }
}
Write-Host "Phase 5 done: Services=$p5svc (whitelisted=$p5svcwl), Receivers=$p5rcv (whitelisted=$p5rcvwl)" -ForegroundColor Cyan

# ════════════════════════════════════════════════════════════════════════
# PHASE 6: Provider neutralization (query returns empty cursor)
# ════════════════════════════════════════════════════════════════════════
Write-Host "`n═══ PHASE 6: ContentProvider Sweep ═══" -ForegroundColor Magenta

$providerWhitelist = @(
  'orca[\\/]',
  'messaging[\\/]',
  'messenger[\\/]',
  'base[\\/]',
  'FileProvider',
  'InitProvider',
  'WorkManagerInitializer',
  'StartupProvider',
  'LifecycleDispatcher',
  'ProcessLifecycleOwner',
  'EmojiCompatInitializer',
  'ProfilerInitProvider',
  'firebase',
  'google[\\/]',
  'androidx[\\/]'
)
$provWhitelistPattern = ($providerWhitelist | ForEach-Object { "($_)" }) -join '|'

$p6 = 0
$p6wl = 0
Get-ChildItem -Path $base -Recurse -Filter '*.smali' | ForEach-Object {
  $top = Get-Content $_.FullName -TotalCount 15 -ErrorAction SilentlyContinue
  if (-not $top) { return }
  $header = $top -join "`n"
  if ($header -notmatch '\.super\s+L[^\n]*Provider;') { return }
  $rel = $_.FullName.Replace((Resolve-Path $base).Path + '\', '')
  if ($rel -match $provWhitelistPattern) { $p6wl++; return }

  $content = Get-Content -Raw -Path $_.FullName
  $pattern6 = '(?s)(\.method(?![^\n]*abstract)[^\n]* onCreate\(\)Z)\n.*?\.end method'
  $replace6 = "`$1`n    .locals 1`n`n    const/4 v0, 0x1`n`n    return v0`n.end method"
  $updated = [regex]::Replace($content, $pattern6, $replace6)
  if ($updated -ne $content) {
    Set-Content -Path $_.FullName -Value $updated -NoNewline
    $p6++
  }
}
Write-Host "Phase 6 done: Providers neutralized=$p6, Whitelisted=$p6wl" -ForegroundColor Cyan

# ════════════════════════════════════════════════════════════════════════
# PHASE 7: Branding text replace (Messenger -> Spravaren in smali strings)
# ════════════════════════════════════════════════════════════════════════
Write-Host "`n═══ PHASE 7: Branding Replace (Messenger -> Spravaren) ═══" -ForegroundColor Magenta

$p7files = 0
$p7hits = 0
Get-ChildItem -Path $base -Recurse -Filter '*.smali' | ForEach-Object {
  $content = Get-Content -Raw -Path $_.FullName
  if ($content -notmatch '"Messenger"') { return }

  $updated = $content.Replace('"Messenger"', '"Spravaren"')
  if ($updated -ne $content) {
    $hits = ([regex]::Matches($content, '"Messenger"')).Count
    $p7hits += $hits
    Set-Content -Path $_.FullName -Value $updated -NoNewline
    $p7files++
  }
}
Write-Host "Phase 7 done: Files=$p7files, Replacements=$p7hits" -ForegroundColor Cyan

# ════════════════════════════════════════════════════════════════════════
# SUMMARY
# ════════════════════════════════════════════════════════════════════════
Write-Host "`n════════════════════════════════════════════" -ForegroundColor Yellow
Write-Host "NUCLEAR CHAT-ONLY PATCH COMPLETE" -ForegroundColor Yellow
Write-Host "Phase 1 (UI cuts): $($stats.ActivityPatched) patched" -ForegroundColor Green
Write-Host "Phase 2 (Battery targeted): $p2patched patched" -ForegroundColor Green
Write-Host "Phase 3 (Background sweep): $p3files files, $p3methods methods" -ForegroundColor Green
Write-Host "Phase 4 (Activity nuclear): $p4patched patched, $p4whitelisted whitelisted" -ForegroundColor Green
Write-Host "Phase 5 (Svc/Rcv nuclear): Svc=$p5svc Rcv=$p5rcv" -ForegroundColor Green
Write-Host "Phase 6 (Provider): $p6 neutralized" -ForegroundColor Green
Write-Host "Phase 7 (Branding): $p7files files, $p7hits replacements" -ForegroundColor Green
Write-Host "════════════════════════════════════════════" -ForegroundColor Yellow
