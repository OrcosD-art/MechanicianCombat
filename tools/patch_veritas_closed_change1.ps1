param(
    [Parameter(Mandatory = $true)]
    [string]$Path
)

$ErrorActionPreference = "Stop"
$stateNames = @(
    "idle", "walk_front", "walk_back", "walk_left", "walk_right",
    "run", "roll", "roll_to_idle", "sliding", "reload", "reload_upper",
    "roll_to_idle_upper", "sliding_fast_ready", "rolling_fast_ready"
)
$encoding = New-Object System.Text.UTF8Encoding($false)
$text = [IO.File]::ReadAllText($Path, [Text.Encoding]::UTF8)

function Find-ValueEnd([string]$json, [int]$start) {
    $open = $json[$start]
    if ($open -ne [char]'{' -and $open -ne [char]'[') {
        throw "Expected object or array at offset $start"
    }
    $depth = 0
    $quoted = $false
    $escaped = $false
    for ($i = $start; $i -lt $json.Length; $i++) {
        $ch = $json[$i]
        if ($quoted) {
            if ($escaped) {
                $escaped = $false
            } elseif ($ch -eq [char]'\') {
                $escaped = $true
            } elseif ($ch -eq [char]'"') {
                $quoted = $false
            }
            continue
        }
        if ($ch -eq [char]'"') {
            $quoted = $true
        } elseif ($ch -eq [char]'{' -or $ch -eq [char]'[') {
            $depth++
        } elseif ($ch -eq [char]'}' -or $ch -eq [char]']') {
            $depth--
            if ($depth -eq 0) {
                return $i
            }
        }
    }
    throw "Unclosed JSON value at offset $start"
}

foreach ($name in $stateNames) {
    $pattern = '"' + [regex]::Escape($name) + '"\s*:\s*\{'
    $matches = [regex]::Matches($text, $pattern)
    if ($matches.Count -eq 0) {
        continue
    }
    if ($matches.Count -ne 1) {
        throw "Expected one animation named $name, found $($matches.Count)"
    }
    $animationStart = $text.IndexOf([char]'{', $matches[0].Index)
    $animationEnd = Find-ValueEnd $text $animationStart
    $slice = $text.Substring($animationStart, $animationEnd - $animationStart + 1)

    $changeIndex = $slice.IndexOf('"change1"', [StringComparison]::Ordinal)
    if ($changeIndex -lt 0) {
        continue
    }
    $changeColon = $slice.IndexOf([char]':', $changeIndex)
    $changeStart = $slice.IndexOf([char]'{', $changeColon)
    $changeEnd = Find-ValueEnd $slice $changeStart
    $changeSlice = $slice.Substring($changeStart, $changeEnd - $changeStart + 1)

    $rotationMatch = [regex]::Match($changeSlice, '"rotation"\s*:\s*')
    if (-not $rotationMatch.Success) {
        continue
    }
    $rotationStart = $rotationMatch.Index + $rotationMatch.Length
    $rotationEnd = Find-ValueEnd $changeSlice $rotationStart
    $newChangeSlice = $changeSlice.Substring(0, $rotationStart) + "[0,0,0]" +
        $changeSlice.Substring($rotationEnd + 1)
    $newSlice = $slice.Substring(0, $changeStart) + $newChangeSlice +
        $slice.Substring($changeEnd + 1)
    $text = $text.Substring(0, $animationStart) + $newSlice +
        $text.Substring($animationEnd + 1)
}

[IO.File]::WriteAllText($Path, $text, $encoding)
Write-Output "Updated closed-state change1 channels: $Path"
