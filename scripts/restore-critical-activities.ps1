param([string]$WorkDir = "work\decompiled_nres")

$w = "c:\Users\vladimir\Desktop\Projects\messengerslim\messengerSlim"
$clean = "$w\work\decompiled"
$nres = "$w\$WorkDir"

$files = @(
    "smali_classes4\com\facebook\base\activity\DelegatingFbFragmentFrameworkActivity.smali",
    "smali_classes2\com\facebook\resources\impl\WaitingForStringsActivity.smali",
    "smali_classes3\com\facebook\messenger\intents\AbstractIntentHandlerActivity.smali",
    "smali_classes8\com\facebook\orca\threadview\ThreadViewActivity.smali",
    "smali_classes8\com\facebook\orca\threadview\ThreadViewBubblesActivity.smali",
    "smali_classes5\com\facebook\messaging\rtc\incall\activity\InCallActivity.smali",
    "smali\com\facebook\messenger\splashscreen\MessengerSplashScreenActivity.smali",
    "smali_classes10\com\facebook\messaging\auth\LaunchScreenActivity.smali",
    "smali_classes10\com\facebook\messaging\neue\nux\NeueNuxActivity.smali",
    "smali_classes10\com\facebook\messaging\settings\surface\MessengerSettingActivity.smali",
    "smali_classes7\com\facebook\checkpoint\CheckpointActivity.smali",
    "smali_classes3\com\facebook\voltron\ui\impl\AppModuleDownloadActivity2.smali",
    "smali_classes2\com\facebook\messenger\neue\MainActivity.smali"
)

$ok = 0; $fail = 0; $missing = 0

foreach ($f in $files) {
    $src = "$clean\$f"
    $dst = "$nres\$f"
    if (-not (Test-Path -LiteralPath $src)) {
        Write-Host "NO_SRC: $f"
        $missing++
        continue
    }
    Copy-Item -LiteralPath $src -Destination $dst -Force
    $patched = (Select-String -LiteralPath $dst -Pattern "FbFragmentActivity;->finish" -Quiet) -eq $true
    if (-not $patched) {
        Write-Host "OK:     $f"
        $ok++
    } else {
        Write-Host "FAIL:   $f"
        $fail++
    }
}

Write-Host ""
Write-Host "=== DONE: $ok OK, $fail FAIL, $missing NO_SRC ==="
