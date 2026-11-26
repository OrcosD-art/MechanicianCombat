param(
    [Parameter(Mandatory = $true)][string]$SourceAnimation,
    [string]$AnimationRoot = "D:\codex\mod\rebuild\mod\src\main\resources\assets\mechaniciancombat\animations\entity",
    [string]$GeometryRoot = "D:\codex\mod\rebuild\mod\src\main\resources\assets\mechaniciancombat\geo\entity"
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [Text.Encoding]::UTF8

$targets = @(
    @{Animation = "bowgun_rig.animation.json"; Geometry = "bowgun_rig.geo.json"},
    @{Animation = "abyssal_ancient_cannon_rig.animation.json"; Geometry = "abyssal_ancient_cannon_rig.geo.json"},
    @{Animation = "veritas_et_justitia_rig.animation.json"; Geometry = "veritas_et_justitia_rig.geo.json"}
)
$source = Get-Content -Raw -Encoding UTF8 $SourceAnimation |
    ConvertFrom-Json

function Copy-JsonValue([object]$value) {
    return ($value | ConvertTo-Json -Depth 100 -Compress |
        ConvertFrom-Json)
}

foreach ($target in $targets) {
    $animationPath = Join-Path $AnimationRoot $target.Animation
    $geometryPath = Join-Path $GeometryRoot $target.Geometry
    $document = Get-Content -Raw -Encoding UTF8 $animationPath |
        ConvertFrom-Json
    $geometry = (Get-Content -Raw -Encoding UTF8 $geometryPath |
        ConvertFrom-Json).'minecraft:geometry'[0]
    $boneNames = @($geometry.bones.name)
    $animations = $document.animations

    foreach ($clipName in "open", "close") {
        $clip = if ($animations.PSObject.Properties.Name.Contains($clipName)) {
            $animations.$clipName
        } else {
            $newClip = [pscustomobject]@{
                loop = "hold_on_last_frame"
                animation_length = 1.0
                bones = [pscustomobject]@{}
            }
            $animations | Add-Member -NotePropertyName $clipName `
                -NotePropertyValue $newClip -Force
            $newClip
        }
        $clip.loop = "hold_on_last_frame"
        foreach ($sourceBone in $source.animations.$clipName.bones.PSObject.Properties) {
            if ($sourceBone.Name -eq "gun" -or $boneNames -notcontains $sourceBone.Name) {
                continue
            }
            $clip.bones | Add-Member -NotePropertyName $sourceBone.Name `
                -NotePropertyValue (Copy-JsonValue $sourceBone.Value) -Force
        }
        # The shared BG261015 base animation owns the gun root. open/close
        # only hold the folding mechanism below that root.
        if ($clip.bones.PSObject.Properties.Name.Contains("gun")) {
            $clip.bones.PSObject.Properties.Remove("gun")
        }
    }

    foreach ($clipName in "Focus_ready", "Focus_over") {
        $clip = $animations.$clipName
        if ($null -eq $clip) {
            throw "$($target.Animation) is missing $clipName"
        }
        foreach ($sourceBone in $source.animations.$clipName.bones.PSObject.Properties) {
            if ($sourceBone.Name -eq "gun" -or $boneNames -notcontains $sourceBone.Name) {
                continue
            }
            $clip.bones | Add-Member -NotePropertyName $sourceBone.Name `
                -NotePropertyValue (Copy-JsonValue $sourceBone.Value) -Force
        }
    }

    [IO.File]::WriteAllText(
        $animationPath,
        ($document | ConvertTo-Json -Depth 100 -Compress),
        (New-Object Text.UTF8Encoding($false)))
    Write-Output "updated $($target.Animation)"
}
