param(
    [string]$AnimationDir = (
        Join-Path $PSScriptRoot '../mod/src/main/resources/assets/tkgun/animations/entity')
)

$ErrorActionPreference = 'Stop'
$sourcePath = Join-Path $AnimationDir 'veritas_et_justitia_rig.animation.json'
$source = Get-Content -LiteralPath $sourcePath -Raw | ConvertFrom-Json
$idleUpper = $source.animations.idle_upper
$utf8 = New-Object System.Text.UTF8Encoding($false)

Get-ChildItem -LiteralPath $AnimationDir -Filter '*_rig.animation.json' |
        Where-Object { $_.FullName -ne (Resolve-Path $sourcePath).Path } |
        ForEach-Object {
    $doc = Get-Content -LiteralPath $_.FullName -Raw | ConvertFrom-Json
    $animations = [ordered]@{}
    foreach ($property in $doc.animations.PSObject.Properties) {
        if ($property.Name -ne 'idle_upper') {
            $animations[$property.Name] = $property.Value
        }
    }
    $animations['idle_upper'] = $idleUpper
    $doc.animations = [pscustomobject]$animations
    $json = $doc | ConvertTo-Json -Depth 100 -Compress
    [IO.File]::WriteAllText($_.FullName, $json, $utf8)
    Write-Output "applied idle_upper -> $($_.Name)"
}
