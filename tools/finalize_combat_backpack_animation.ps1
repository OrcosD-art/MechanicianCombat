param(
    [string]$AnimationPath = "D:\codex\mod\rebuild\mod\src\main\resources\assets\tkgun\animations\entity\combat_backpack.animation.json"
)

$ErrorActionPreference = "Stop"
$document = Get-Content -Raw -Encoding UTF8 $AnimationPath | ConvertFrom-Json
foreach ($property in $document.animations.PSObject.Properties) {
    $animation = $property.Value
    if ($animation.PSObject.Properties.Name -contains "sound_effects") {
        $animation.PSObject.Properties.Remove("sound_effects")
    }
    if ($property.Name -eq "Maintenance") {
        $animation | Add-Member -NotePropertyName animation_length -NotePropertyValue 1 -Force
    }
}
[IO.File]::WriteAllText(
    $AnimationPath,
    ($document | ConvertTo-Json -Depth 100 -Compress),
    [Text.UTF8Encoding]::new($false))
Write-Output "Finalized backpack animation."
