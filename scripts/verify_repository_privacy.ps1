$ErrorActionPreference = "Stop"

$blockedPaths = '\.(pdf|apk|aab|jks|keystore)$|(^|/)(statements|vault|fixtures?)/|(^|/)(local\.properties|secrets\.properties)$'
$blockedContent = @(
    '(?im)^[^\r\n]*\b[A-Z]:\\Users\\',
    '(?i)\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b',
    '\b(?:\d[ -]?){12,19}\b'
)

$tracked = git ls-files
$pathHits = $tracked | Where-Object { $_ -match $blockedPaths }
if ($pathHits) {
    throw "Tracked private artifact path(s): $($pathHits -join ', ')"
}

$contentHits = foreach ($path in $tracked) {
    if ($path -match '\.(jar|png|jpg|jpeg|gif|webp|ico)$') { continue }
    $content = Get-Content -LiteralPath $path -Raw -ErrorAction SilentlyContinue
    foreach ($pattern in $blockedContent) {
        if ($content -match $pattern) { "$path matches protected-content policy"; break }
    }
}
if ($contentHits) {
    throw "Potential private data found: $($contentHits -join '; ')"
}

Write-Output "Repository privacy check passed for $($tracked.Count) tracked files."
