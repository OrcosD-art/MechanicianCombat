param(
    [string]$OldGeo = "D:\codex\mod\rebuild\work\veritas_close_combat_current\close_combat.geo.raw.json",
    [string]$NewGeo = "D:\codex\mod\rebuild\work\veritas_close_combat_20261111_2025\close_combat.geo.raw.json",
    [string]$OldAnimation = "D:\codex\mod\rebuild\work\veritas_close_combat_current\close_combat.animation.raw.json",
    [string]$NewAnimation = "D:\codex\mod\rebuild\work\veritas_close_combat_20261111_2025\close_combat.animation.raw.json"
)

$ErrorActionPreference = "Stop"
function Read-Json([string]$path) {
    return [IO.File]::ReadAllText($path, [Text.Encoding]::UTF8) |
        ConvertFrom-Json
}
$oldGeo = Read-Json $OldGeo
$newGeo = Read-Json $NewGeo
$oldBones = $oldGeo.bones
$newBones = $newGeo.bones
foreach ($name in @("effect_2", "1_5", "1_6")) {
    $oldBone = $oldBones | Where-Object name -eq $name | Select-Object -First 1
    $newBone = $newBones | Where-Object name -eq $name | Select-Object -First 1
    Write-Output "GEOMETRY $name"
    Write-Output ("  old: {0}" -f
        ($oldBone | ConvertTo-Json -Compress -Depth 20))
    Write-Output ("  new: {0}" -f
        ($newBone | ConvertTo-Json -Compress -Depth 20))
}

$oldAnim = Read-Json $OldAnimation
$newAnim = Read-Json $NewAnimation
foreach ($animationName in @("ATK_1", "ATK_2", "ATK_hold_over")) {
    foreach ($name in @("effect_2", "1_5", "1_6")) {
        $oldBone = $oldAnim.animations.$animationName.bones.$name
        $newBone = $newAnim.animations.$animationName.bones.$name
        $oldJson = $oldBone | ConvertTo-Json -Compress -Depth 20
        $newJson = $newBone | ConvertTo-Json -Compress -Depth 20
        if ($oldJson -ne $newJson) {
            Write-Output "ANIMATION $animationName $name"
            Write-Output "  old: $oldJson"
            Write-Output "  new: $newJson"
        }
    }
}
