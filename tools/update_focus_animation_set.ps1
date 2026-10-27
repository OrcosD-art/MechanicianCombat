param(
    [string]$AnimationJson = "D:\codex\mod\rebuild\mod\src\main\resources\assets\tkgun\animations\entity\bowgun_rig.animation.json"
)

$ErrorActionPreference = "Stop"

function Copy-JsonObject($value) {
    return ($value | ConvertTo-Json -Depth 100 -Compress) | ConvertFrom-Json
}

$doc = [IO.File]::ReadAllText($AnimationJson) | ConvertFrom-Json
$animations = $doc.animations

# Capture the old set before Focus_shooting is replaced by the base 1-3 shot animation.
$oldShots = @{}
for ($index = 1; $index -le 7; $index++) {
    $sourceName = if ($index -eq 1) { "Focus_shooting" } else { "Focus_shooting$index" }
    if ($null -eq $animations.$sourceName) {
        throw "Missing source animation: $sourceName"
    }
    $oldShots[$index] = Copy-JsonObject $animations.$sourceName
}

$hold = Copy-JsonObject $animations.Focus
$hold | Add-Member -NotePropertyName name -NotePropertyValue "Focus_hold" -Force
$hold | Add-Member -NotePropertyName loop -NotePropertyValue $true -Force
$animations | Add-Member -NotePropertyName Focus_hold -NotePropertyValue $hold -Force

$baseShot = Copy-JsonObject $animations.Focus_shooting_extra
$baseShot | Add-Member -NotePropertyName name -NotePropertyValue "Focus_shooting" -Force
$baseShot.bones | Add-Member -NotePropertyName magazine_1 -NotePropertyValue (
    [pscustomobject]@{ position = @(0, 0, 0) }) -Force
$animations.Focus_shooting = $baseShot

for ($index = 1; $index -le 7; $index++) {
    $shot = $oldShots[$index]
    $shot | Add-Member -NotePropertyName name -NotePropertyValue "Focus_shooting$index" -Force
    $animations | Add-Member -NotePropertyName "Focus_shooting$index" -NotePropertyValue $shot -Force
}

$animations.PSObject.Properties.Remove("Focus")

$json = $doc | ConvertTo-Json -Depth 100 -Compress
[IO.File]::WriteAllText($AnimationJson, $json, [Text.UTF8Encoding]::new($false))
Write-Output "Updated focus animations: Focus_hold, base Focus_shooting, Focus_shooting1..7"
