param(
    [string]$ModelPath = "D:\Download\USER\work\AModel\机械师装备\炮\枢德之裁\close_combat.bbmodel"
)

$ErrorActionPreference = "Stop"
$raw = [IO.File]::ReadAllText($ModelPath, [Text.Encoding]::UTF8)
$validJson = [regex]::Replace($raw, '\\(?![\\"/bfnrtu])', '/')
$model = $validJson | ConvertFrom-Json
$elements = @{}
foreach ($element in $model.elements) {
    $elements[[string]$element.uuid] = $element
}

function Walk($nodes, [string]$parent = "") {
    foreach ($node in @($nodes)) {
        if ($node -is [string]) {
            $element = $elements[[string]$node]
            if ($null -eq $element) {
                continue
            }
            $faces = foreach ($face in $element.faces.PSObject.Properties) {
                $texture = $face.Value.texture
                if ($null -ne $texture) {
                    "$($face.Name):$texture"
                }
            }
            [pscustomobject]@{
                Group = $parent
                Type = $element.type
                UUID = $element.uuid
                From = ($element.from -join ",")
                To = ($element.to -join ",")
                Origin = ($element.origin -join ",")
                Rotation = ($element.rotation -join ",")
                Faces = ($faces -join " ")
                UpUV = ($element.faces.up.uv -join ",")
            }
            continue
        }
        $name = [string]$node.name
        foreach ($child in @($node.children)) {
            if ($child -is [string]) {
                $element = $elements[[string]$child]
                if ($null -ne $element -and $element.type -eq "cube") {
                    [pscustomobject]@{
                        Group = $name
                        Type = "child-ref"
                        UUID = $child
                        From = ($element.from -join ",")
                        To = ($element.to -join ",")
                        Origin = ($element.origin -join ",")
                        Rotation = ($element.rotation -join ",")
                        Faces = ""
                        UpUV = ($element.faces.up.uv -join ",")
                    }
                }
            }
        }
        Walk $node.children $name
    }
}

Walk $model.outliner | Where-Object {
    $_.Group -match '^(effect_|1_|2_)'
} | Format-Table -AutoSize

foreach ($animationName in @("ATK_1", "ATK_2", "ATK_3", "ATK_4",
                             "ATK_run", "ATK_3_hold", "ATK_hold_over")) {
    $animation = $model.animations |
        Where-Object { $_.name -eq $animationName } |
        Select-Object -First 1
    if ($null -eq $animation) {
        continue
    }
    Write-Output "ANIMATION $animationName"
    foreach ($animator in $animation.animators.PSObject.Properties.Value) {
        if ($animator.name -notmatch '^(effect_|1_|2_)') {
            continue
        }
        Write-Output ("  {0}" -f $animator.name)
        foreach ($keyframe in $animator.keyframes) {
            $point = $keyframe.data_points[0]
            Write-Output ("    {0} t={1} interp={2} ({3},{4},{5})" -f
                $keyframe.channel, $keyframe.time,
                $keyframe.interpolation, $point.x, $point.y, $point.z)
        }
    }
}
