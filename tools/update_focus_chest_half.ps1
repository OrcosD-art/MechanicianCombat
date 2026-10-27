param(
    [string]$SourceModel = "D:\Download\USER\work\AModel\机械师装备\破垒炮\破垒重炮\BG261015.bbmodel",
    [string]$AnimationJson = "D:\codex\mod\rebuild\mod\src\main\resources\assets\tkgun\animations\entity\bowgun_rig.animation.json"
)

$ErrorActionPreference = "Stop"

function Read-BlockbenchJson([string]$path) {
    $raw = [IO.File]::ReadAllText($path)
    $evaluator = [Text.RegularExpressions.MatchEvaluator] {
        param($match)
        $slashes = $match.Value
        $nextIndex = $match.Index + $match.Length
        $next = if ($nextIndex -lt $raw.Length) { $raw[$nextIndex] } else { [char]0 }
        if (($slashes.Length % 2) -eq 1 -and '"\/bfnrtu'.Contains([string]$next)) {
            return $slashes
        }
        if (($slashes.Length % 2) -eq 1) {
            return $slashes + '\'
        }
        return $slashes
    }
    return [regex]::Replace($raw, '\\+', $evaluator) | ConvertFrom-Json
}

function Replace-Formula($value, [ref]$count) {
    if ($value -is [string]) {
        $pattern = 'head_x_rotation(\s*)/(\s*)(?:3|\$22)'
        if ($value -match $pattern) {
            $count.Value++
            $evaluator = [Text.RegularExpressions.MatchEvaluator] {
                param($match)
                return "head_x_rotation$($match.Groups[1].Value)/$($match.Groups[2].Value)2"
            }
            return [regex]::Replace($value, $pattern, $evaluator)
        }
        return $value
    }
    if ($value -is [System.Array]) {
        for ($i = 0; $i -lt $value.Count; $i++) {
            $value[$i] = Replace-Formula $value[$i] $count
        }
        return $value
    }
    if ($null -ne $value -and $value -is [pscustomobject]) {
        foreach ($property in $value.PSObject.Properties) {
            $property.Value = Replace-Formula $property.Value $count
        }
    }
    return $value
}

$sourceNames = @(
    "Focus_ready", "sliding_fast_ready", "rolling_fast_ready", "Focus_over_shooting",
    "Focus_shooting_extra", "Focus_hold", "Focus_shooting",
    "Focus_shooting1", "Focus_shooting2", "Focus_shooting3", "Focus_shooting4",
    "Focus_shooting5", "Focus_shooting6", "Focus_shooting7"
)
$runtimeNames = @(
    "Focus_ready", "sliding_fast_ready", "rolling_fast_ready", "Focus_over_shooting",
    "Focus_shooting_extra", "Focus_hold", "Focus_shooting",
    "Focus_shooting1", "Focus_shooting2", "Focus_shooting3", "Focus_shooting4",
    "Focus_shooting5", "Focus_shooting6", "Focus_shooting7",
    "Focus_ready_upper", "Focus_over_shooting_upper", "Focus_shooting_upper",
    "Focus_shooting2_upper", "Focus_shooting3_upper", "Focus_shooting4_upper",
    "Focus_shooting5_upper", "Focus_shooting6_upper", "Focus_shooting7_upper",
    "Focus_ready_hold"
)

$source = Read-BlockbenchJson $SourceModel
$sourceCount = 0
foreach ($animation in $source.animations) {
    if ($animation.name -notin $sourceNames) {
        continue
    }
    foreach ($property in $animation.animators.PSObject.Properties) {
        $animator = $property.Value
        if ($animator.type -ne "bone" -or $animator.name -ne "chest") {
            continue
        }
        foreach ($keyframe in $animator.keyframes) {
            if ($keyframe.channel -ne "rotation") {
                continue
            }
            $keyframe.data_points = Replace-Formula $keyframe.data_points ([ref]$sourceCount)
        }
    }
}

$backup = "$SourceModel.bak_before_chest_half_" + (Get-Date -Format "yyyyMMdd-HHmmss")
Copy-Item -LiteralPath $SourceModel -Destination $backup
$sourceJson = $source | ConvertTo-Json -Depth 100 -Compress
[IO.File]::WriteAllText($SourceModel, $sourceJson, [Text.UTF8Encoding]::new($false))

$animation = [IO.File]::ReadAllText($AnimationJson) | ConvertFrom-Json
$runtimeCount = 0
foreach ($name in $runtimeNames) {
    $target = $animation.animations.$name
    if ($null -eq $target -or $null -eq $target.bones.chest) {
        continue
    }
    $target.bones.chest.rotation = Replace-Formula $target.bones.chest.rotation ([ref]$runtimeCount)
}
$animationJson = $animation | ConvertTo-Json -Depth 100 -Compress
[IO.File]::WriteAllText($AnimationJson, $animationJson, [Text.UTF8Encoding]::new($false))

Write-Output "Updated chest head_x divisor: source=$sourceCount runtime=$runtimeCount"
Write-Output "Source backup: $backup"
