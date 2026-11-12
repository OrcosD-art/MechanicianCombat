param(
    [string]$OldJar = "D:\codex\mod\rebuild\backups\MechanicianCombat-r749.jar",
    [string]$NewAnimationPath = "D:\codex\mod\rebuild\mod\src\main\resources\assets\mechaniciancombat\animations\entity\veritas_et_justitia_rig.animation.json"
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.IO.Compression.FileSystem

$entryName = "assets/mechaniciancombat/animations/entity/veritas_et_justitia_rig.animation.json"
$zip = [IO.Compression.ZipFile]::OpenRead($OldJar)
try {
    $entry = $zip.GetEntry($entryName)
    $reader = [IO.StreamReader]::new(
        $entry.Open(), [Text.Encoding]::UTF8, $true, 4096, $true)
    try {
        $old = $reader.ReadToEnd() | ConvertFrom-Json
    } finally {
        $reader.Dispose()
    }
} finally {
    $zip.Dispose()
}
$new = [IO.File]::ReadAllText(
    $NewAnimationPath, [Text.Encoding]::UTF8) | ConvertFrom-Json

foreach ($name in @($new.animations.PSObject.Properties.Name)) {
    $oldAnimation = $old.animations.$name
    $newClip = $new.animations.$name
    if ($null -eq $oldAnimation) {
        Write-Output "$name : NEW"
        continue
    }
    if ([double]$oldAnimation.animation_length -ne
            [double]$newClip.animation_length) {
        Write-Output ("{0} : length {1} -> {2}" -f
            $name, $oldAnimation.animation_length,
            $newClip.animation_length)
    }
    foreach ($channel in @("sound_effects", "particle_effects", "timeline")) {
        $oldValue = $oldAnimation.$channel
        $newValue = $newClip.$channel
        $oldJson = if ($null -eq $oldValue) { "" } else {
            $oldValue | ConvertTo-Json -Depth 20 -Compress
        }
        $newJson = if ($null -eq $newValue) { "" } else {
            $newValue | ConvertTo-Json -Depth 20 -Compress
        }
        if ($oldJson -ne $newJson) {
            Write-Output "$name : $channel changed"
            Write-Output "  old: $oldJson"
            Write-Output "  new: $newJson"
        }
    }
}
