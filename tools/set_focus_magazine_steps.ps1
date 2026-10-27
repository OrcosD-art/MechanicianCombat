param(
    [string]$AnimationJson = "D:\codex\mod\rebuild\mod\src\main\resources\assets\tkgun\animations\entity\bowgun_rig.animation.json"
)

$ErrorActionPreference = "Stop"
$document = [IO.File]::ReadAllText($AnimationJson) | ConvertFrom-Json

function New-StepValue([double[]]$vector) {
    return [pscustomobject][ordered]@{
        pre = $vector
        post = $vector
    }
}

function New-StepChannel([double[]]$vector, [double]$duration) {
    $channel = [ordered]@{}
    $channel["0.0"] = New-StepValue $vector
    $channel[$duration.ToString("0.####", [Globalization.CultureInfo]::InvariantCulture)] =
        New-StepValue $vector
    return [pscustomobject]$channel
}

$shooting = $document.animations.Focus_shooting
$shootingDuration = [double]$shooting.animation_length
$shooting.bones | Add-Member -Force -NotePropertyName change3 -NotePropertyValue ([pscustomobject][ordered]@{
    rotation = New-StepChannel @(0, 0, 0) $shootingDuration
    position = New-StepChannel @(-1, 0, 0) $shootingDuration
    scale = New-StepChannel @(1, 1, 1) $shootingDuration
})
$shooting.bones | Add-Member -Force -NotePropertyName magazine_1 -NotePropertyValue ([pscustomobject][ordered]@{
    position = New-StepChannel @(0, 0, 0) $shootingDuration
})
$shooting.bones | Add-Member -Force -NotePropertyName magazine_2 -NotePropertyValue ([pscustomobject][ordered]@{
    position = New-StepChannel @(0, 0, 0) $shootingDuration
})

$hold = $document.animations.Focus_hold
$holdDuration = [double]$hold.animation_length
$hold.bones | Add-Member -Force -NotePropertyName change3 -NotePropertyValue ([pscustomobject][ordered]@{
    rotation = New-StepChannel @(0, 0, 0) $holdDuration
    position = New-StepChannel @(-1, 0, 0) $holdDuration
    scale = New-StepChannel @(1, 1, 1) $holdDuration
})
$hold.bones | Add-Member -Force -NotePropertyName magazine_1 -NotePropertyValue ([pscustomobject][ordered]@{
    position = New-StepChannel @(0, 0, 0) $holdDuration
})
$hold.bones | Add-Member -Force -NotePropertyName magazine_2 -NotePropertyValue ([pscustomobject][ordered]@{
    position = New-StepChannel @(0, 0, 0) $holdDuration
})

$json = $document | ConvertTo-Json -Depth 100 -Compress
[IO.File]::WriteAllText($AnimationJson, $json, [Text.UTF8Encoding]::new($false))
Write-Output "Applied step magazine frames to Focus_shooting and Focus_hold."
