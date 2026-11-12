param(
    [string]$ModelPath
)

$ErrorActionPreference = "Stop"
$raw = [IO.File]::ReadAllText($ModelPath, [Text.Encoding]::UTF8)
$model = ([regex]::Replace($raw, '\\(?![\\"/bfnrtu])', '/') |
    ConvertFrom-Json)
foreach ($animation in $model.animations) {
    foreach ($animator in $animation.animators.PSObject.Properties.Value) {
        if ($animator.type -eq "bone") {
            continue
        }
        foreach ($keyframe in $animator.keyframes) {
            if ($keyframe.channel -notin @(
                    "particle", "sound", "timeline")) {
                continue
            }
            $point = $keyframe.data_points[0]
            [pscustomobject]@{
                Animation = $animation.name
                Channel = $keyframe.channel
                Time = $keyframe.time
                Effect = [string]$point.effect
                Locator = [string]$point.locator
                File = [string]$point.file
                Script = [string]$point.script
            }
        }
    }
}
