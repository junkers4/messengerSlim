param([string]$WorkDir = "work\decompiled_nres")

$root = "c:\Users\vladimir\Desktop\Projects\messengerslim\messengerSlim"
$nres = "$root\$WorkDir"
$clean = "$root\work\decompiled"

$actuallyPatched = @()

Get-ChildItem $nres -Recurse -Filter "*Activity*.smali" | ForEach-Object {
    $rel = $_.FullName.Replace("$nres\", "")
    $cleanPath = "$clean\$rel"
    
    if (-not (Test-Path -LiteralPath $cleanPath)) { return }
    
    $nresHasFinish = (Select-String -LiteralPath $_.FullName -Pattern "->finish\(\)V" -Quiet) -eq $true
    $cleanHasFinish = (Select-String -LiteralPath $cleanPath -Pattern "->finish\(\)V" -Quiet) -eq $true
    
    if ($nresHasFinish -and -not $cleanHasFinish) {
        $actuallyPatched += $rel
        Write-Host "INJECTED: $rel"
    }
}

Write-Host ""
Write-Host "=== Total actually-injected Activity files: $($actuallyPatched.Count) ==="
$actuallyPatched | Out-File "$root\actually_patched_activities.txt" -Encoding utf8 -Force
Write-Host "Saved to actually_patched_activities.txt"
