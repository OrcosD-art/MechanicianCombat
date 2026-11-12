param(
    [string]$RawAnimation = "D:\codex\mod\rebuild\work\veritas_close_combat_current\close_combat.animation.raw.json",
    [string]$TargetAnimation = "D:\codex\mod\rebuild\mod\src\main\resources\assets\mechaniciancombat\animations\entity\veritas_et_justitia_rig.animation.json"
)

$ErrorActionPreference = "Stop"
$raw = [IO.File]::ReadAllText(
    $RawAnimation, [Text.Encoding]::UTF8) | ConvertFrom-Json
$target = [IO.File]::ReadAllText(
    $TargetAnimation, [Text.Encoding]::UTF8) | ConvertFrom-Json

foreach ($animationName in @("ATK_1", "ATK_2", "ATK_3",
                             "ATK_4", "ATK_hold_over")) {
    Write-Output "ANIMATION $animationName"
    $rawBones = $raw.animations.$animationName.bones
    $targetBones = $target.animations.$animationName.bones
    foreach ($boneName in @("effect_1", "1_1", "1_2", "1_3",
                            "effect_2", "1_5", "1_6", "effect_3")) {
        $rawBone = $rawBones.$boneName
        $targetBone = $targetBones.$boneName
        if ($null -eq $rawBone -and $null -eq $targetBone) {
            continue
        }
        Write-Output "  $boneName"
        foreach ($channel in @("position", "rotation", "scale")) {
            $rawValue = $rawBone.$channel
            $targetValue = $targetBone.$channel
            if ($null -eq $rawValue -and $null -eq $targetValue) {
                continue
            }
            Write-Output ("    {0} raw={1}" -f $channel,
                ($rawValue | ConvertTo-Json -Compress -Depth 20))
            Write-Output ("    {0} target={1}" -f $channel,
                ($targetValue | ConvertTo-Json -Compress -Depth 20))
        }
    }
}
