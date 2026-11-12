param(
    [string]$ModelPath = "D:\Download\USER\work\AModel\机械师装备\炮\枢德之裁\close_combat.bbmodel"
)

$ErrorActionPreference = "Stop"
$raw = [IO.File]::ReadAllText($ModelPath, [Text.Encoding]::UTF8)
$validJson = [regex]::Replace($raw, '\\(?![\\"/bfnrtu])', '/')
$model = $validJson | ConvertFrom-Json

function Walk-Group($nodes, [string]$path = "") {
    foreach ($node in @($nodes)) {
        if ($node -is [string]) {
            continue
        }
        $name = [string]$node.name
        $full = if ($path) { "$path/$name" } else { $name }
        [pscustomobject]@{
            Path = $full
            UUID = [string]$node.uuid
            Origin = ($node.origin -join ",")
            Rotation = ($node.rotation -join ",")
            ChildCount = @($node.children).Count
        }
        if ($node.children) {
            Walk-Group $node.children $full
        }
    }
}

Write-Output "TEXTURES"
foreach ($texture in $model.textures) {
    Write-Output ("{0} id={1} {2}x{3}" -f
        $texture.name, $texture.id, $texture.width, $texture.height)
}

Write-Output "GROUPS"
Walk-Group $model.outliner | Format-Table -AutoSize

Write-Output "ANIMATIONS"
foreach ($animation in $model.animations) {
    Write-Output ("{0}: length={1} loop={2} snapping={3}" -f
        $animation.name, $animation.length, $animation.loop,
        $animation.snapping)
    foreach ($animator in $animation.animators.PSObject.Properties.Value) {
        Write-Output ("  {0} ({1})" -f $animator.name, $animator.type)
        foreach ($keyframe in $animator.keyframes) {
            $points = @($keyframe.data_points)
            $values = foreach ($point in $points) {
                if ($null -ne $point.x) {
                    "($($point.x),$($point.y),$($point.z))"
                } elseif ($null -ne $point.effect) {
                    "effect=$($point.effect), locator=$($point.locator)"
                } elseif ($null -ne $point.script) {
                    "script=$($point.script)"
                } else {
                    ($point | ConvertTo-Json -Compress -Depth 10)
                }
            }
            Write-Output ("    {0,-10} t={1,-8} interp={2} {3}" -f
                $keyframe.channel, $keyframe.time,
                $keyframe.interpolation, ($values -join " | "))
        }
    }
}
