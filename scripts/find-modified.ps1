param([string]$WorkDir = "work\decompiled_nres")

$root = "c:\Users\vladimir\Desktop\Projects\messengerslim\messengerSlim"
$nres = (Resolve-Path "$root\$WorkDir").Path.TrimEnd('\')
$clean = (Resolve-Path "$root\work\decompiled").Path.TrimEnd('\')

$different = @()
$onlyInNres = @()

Get-ChildItem $nres -Recurse -Filter "*.smali" | ForEach-Object {
    $rel = $_.FullName.Substring($nres.Length + 1)
    $cleanPath = "$clean\$rel"
    
    if (-not (Test-Path -LiteralPath $cleanPath)) {
        $onlyInNres += $rel
        return
    }
    
    $h1 = Get-FileHash -LiteralPath $_.FullName -Algorithm MD5
    $h2 = Get-FileHash -LiteralPath $cleanPath -Algorithm MD5
    
    if ($h1.Hash -ne $h2.Hash) {
        $different += $rel
    }
}

Write-Host "=== Files MODIFIED in nres vs clean ==="
Write-Host "Count: $($different.Count)"
$different | Sort-Object | ForEach-Object { Write-Host $_ }

Write-Host ""
Write-Host "=== Files ONLY in nres (new files) ==="
Write-Host "Count: $($onlyInNres.Count)"
$onlyInNres | Sort-Object | Select-Object -First 20 | ForEach-Object { Write-Host $_ }

$different | Out-File "$root\actually_modified.txt" -Encoding utf8 -Force
Write-Host ""
Write-Host "Saved modified list to actually_modified.txt"
