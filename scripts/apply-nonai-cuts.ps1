[CmdletBinding()]
param(
  [string]$WorkDir = "work/decompiled_nres",
  [ValidateSet('core', 'extended', 'aggressive')]
  [string]$Level = 'core'
)

$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$base = Join-Path $root $WorkDir

if (-not (Test-Path $base)) {
  throw "Work directory not found: $base"
}

$coreTargets = @(
  'smali_classes5/com/facebook/stickers/store/StickerStoreActivity.smali',
  'smali_classes6/com/facebook/messaging/reels/unreadsettings/UnreadReelsSettingsActivity.smali',
  'smali_classes7/com/facebook/messaging/marketplace/meetingplan/MeetingPlanActivity.smali',
  'smali_classes7/com/facebook/messaging/marketplace/viewlisting/ThreadNotificationViewListingActivity.smali',
  'smali_classes7/com/facebook/messaging/montage/prefs/MontagePreferenceActivity.smali',
  'smali_classes8/com/facebook/messaging/wellbeing/unknowncontact/messagerequests/threadlist/MessageRequestsActivity.smali'
)

$extendedOnlyTargets = @(
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
  'smali_classes9/com/facebook/messaging/montage/viewer/StoryPreviewPopupActivity.smali'
)

$aggressiveOnlyTargets = @(
  'smali_classes7/com/facebook/messaging/avatar/socialstickers/ui/settings/AvatarUsabilitySettingActivity.smali',
  'smali_classes8/com/facebook/messaging/about/MessengerAboutLicenseActivity.smali',
  'smali_classes7/com/facebook/messaging/memories/viewer/activity/MemoryViewerActivity.smali',
  'smali_classes8/com/facebook/messaging/attribution/ChatHeadsReplyFlowHandlerActivity.smali',
  'smali_classes7/com/facebook/messaging/users/username/EditUsernameActivity.smali',
  'smali_classes8/com/facebook/messaging/users/displayname/ChangeDisplayNameSettingsActivity.smali',
  'smali_classes9/com/facebook/nativetemplates/fb/shell/messenger/NativeTemplatesMessengerActivity.smali',
  # RequestPermissionsActivity NECHANE ZIVE - cez nu appka pyta mikrofon/kameru pre hovory
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
  'smali_classes7/com/facebook/messaging/dma/ui/MsplitConsentSettingsActivity.smali',
  'smali_classes7/com/facebook/messaging/invites/settingsurface/InviteFriendSettingActivity.smali',
  'smali_classes7/com/facebook/messaging/professionalmode/businesstools/settings/ProModeBusinessToolsSettingsActivity.smali',
  'smali_classes7/com/facebook/messaging/wellbeing/safetyecosystem/authenticinteractions/ondevicesafetydetection/activity/SafetyDetectionSettingsActivity.smali',
  'smali_classes8/com/facebook/messaging/lid/appsettingsui/LanguageIdentificationSettingsActivity.smali',
  'smali_classes8/com/facebook/messaging/nativepagereply/faq/mesettings/BusinessInboxFAQSettingActivity.smali',
  'smali_classes8/com/facebook/messaging/search/edithistory/M3SearchEditHistoryActivity.smali',
  'smali_classes8/com/facebook/zero/zerobalance/ui/AutoflexSettingsActivity.smali',
  'smali_classes9/com/facebook/offlineexperiment/internalsettings/OfflineExperimentPreferenceActivity.smali'
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

$a0FReplacement = @"
.method public A0F(Landroid/os/Bundle;)V
  .locals 0

  invoke-super {p0, p1}, Lcom/facebook/base/activity/FbPreferenceActivity;->A0F(Landroid/os/Bundle;)V

  invoke-virtual {p0}, Lcom/facebook/base/activity/FbPreferenceActivity;->finish()V

  return-void
.end method
"@

$aaOReplacement = @"
.method public AaO()Ljava/lang/String;
  .locals 1

  const-string v0, ""

  return-object v0
.end method
"@

$a0HReplacement = @"
.method public A0H(Lcom/facebook/msys/mca/Mailbox;)V
  .locals 0

  return-void
.end method
"@

$a32Pattern = '(?s)\.method public A32\(Landroid/os/Bundle;\)V.*?\.end method'
$onCreatePattern = '(?s)\.method public onCreate\(Landroid/os/Bundle;\)V.*?\.end method'
$a0FPattern = '(?s)\.method public A0F\(Landroid/os/Bundle;\)V.*?\.end method'
$aaOPattern = '(?s)\.method public AaO\(\)Ljava/lang/String;.*?\.end method'
$a0HPattern = '(?s)\.method public A0H\(Lcom/facebook/msys/mca/Mailbox;\)V.*?\.end method'

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
  if ($content -match '->finish\(\)V') {
    Write-Host "Already patched: $path" -ForegroundColor DarkYellow
    $alreadyPatched++
    continue
  }

  $updated = [regex]::Replace($content, $a32Pattern, $a32Replacement, 1)

  if ($updated -eq $content) {
    $updated = [regex]::Replace($content, $onCreatePattern, $onCreateReplacement, 1)
  }

  if ($updated -eq $content) {
    $updated = [regex]::Replace($content, $a0FPattern, $a0FReplacement, 1)
  }

  if ($updated -eq $content) {
    $updated = [regex]::Replace($content, $aaOPattern, $aaOReplacement, 1)
  }

  if ($updated -eq $content) {
    $updated = [regex]::Replace($content, $a0HPattern, $a0HReplacement, 1)
  }

  if ($updated -eq $content) {
    Write-Warning "No patchable method found (A32/onCreate/A0F/AaO/A0H): $path"
    $unmatched++
    continue
  }

  Set-Content -Path $path -Value $updated -NoNewline
  Write-Host "Patched: $path" -ForegroundColor Green
  $patched++
}

Write-Host "Non-AI cut patch set applied (level=$Level)." -ForegroundColor Cyan
Write-Host "Patched=$patched, AlreadyPatched=$alreadyPatched, Missing=$missing, Unmatched=$unmatched" -ForegroundColor Cyan
