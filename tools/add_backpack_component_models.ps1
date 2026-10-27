param(
    [string]$Resources = "D:\codex\mod\rebuild\mod\src\main\resources"
)

$ErrorActionPreference = "Stop"
$geoDir = Join-Path $Resources "assets\tkgun\geo\entity"
$itemDir = Join-Path $Resources "assets\tkgun\models\item"

function Add-CoreBone(
        [string]$path,
        [double[]]$pivot,
        [double[]]$origin,
        [double[]]$removeRootOrigin) {
    $json = Get-Content -Raw -LiteralPath $path | ConvertFrom-Json
    $bones = $json.'minecraft:geometry'[0].bones
    $root = $bones | Where-Object { $_.name -eq "combat_backpack" } | Select-Object -First 1
    $removeKey = $removeRootOrigin -join ","
    if ($null -ne $root) {
        $root.cubes = @($root.cubes | Where-Object {
            $_.origin -and (($_.origin -join ",") -ne $removeKey)
        })
    }
    if (@($bones | Where-Object { $_.name -eq "core" }).Count -gt 0) {
        $json.'minecraft:geometry'[0].bones = $bones
        [IO.File]::WriteAllText(
                $path,
                ($json | ConvertTo-Json -Depth 60 -Compress),
                [Text.UTF8Encoding]::new($false))
        return
    }
    $core = [pscustomobject]@{
        name = "core"
        parent = "combat_backpack"
        pivot = $pivot
        cubes = @(
            [pscustomobject]@{
                origin = $origin
                size = @(5, 5, 2)
                uv = [pscustomobject]@{
                    north = [pscustomobject]@{ uv = @(0, 0); uv_size = @(4, 4) }
                    east = [pscustomobject]@{ uv = @(0, 9); uv_size = @(2, 5) }
                    south = [pscustomobject]@{ uv = @(12, 11); uv_size = @(5, 5) }
                    west = [pscustomobject]@{ uv = @(2, 9); uv_size = @(-2, 5) }
                    up = [pscustomobject]@{ uv = @(2, 9); uv_size = @(-2, 5); uv_rotation = 90 }
                    down = [pscustomobject]@{ uv = @(0, 9); uv_size = @(2, 5); uv_rotation = 90 }
                }
            }
        )
    }
    $json.'minecraft:geometry'[0].bones = @($bones) + $core
    [IO.File]::WriteAllText(
            $path,
            ($json | ConvertTo-Json -Depth 60 -Compress),
            [Text.UTF8Encoding]::new($false))
}

Add-CoreBone (Join-Path $geoDir "combat_backpack.geo.json") @(-4, -4.5, 1.5) @(-6.5, -7, 0.5) @(-6.5, -7, 0.5)
Add-CoreBone (Join-Path $geoDir "combat_backpack_rig.geo.json") @(0, 7.5, 3.5) @(-2.5, 5, 2.5) @(-2.5, 5, 2.5)

$coreItemPath = Join-Path $itemDir "backpack_core.json"
$coreItem = Get-Content -Raw -LiteralPath $coreItemPath | ConvertFrom-Json
$coreItem | Add-Member -NotePropertyName display -NotePropertyValue ([pscustomobject]@{
    thirdperson_righthand = [pscustomobject]@{ translation = @(0, 3.5, 0) }
    thirdperson_lefthand = [pscustomobject]@{ translation = @(0, 3.75, 0) }
    firstperson_righthand = [pscustomobject]@{
        rotation = @(-180, 33.75, -180)
        translation = @(3.5, 7.25, -0.25)
    }
    firstperson_lefthand = [pscustomobject]@{
        rotation = @(-180, 46.25, -180)
        translation = @(1.25, 7.75, 1)
    }
    ground = [pscustomobject]@{ translation = @(0, 5, 0) }
    gui = [pscustomobject]@{
        rotation = @(-157.5, 45, -180)
        translation = @(0, 10.75, 0)
        scale = @(2, 2, 2)
    }
}) -Force
$coreItem | Add-Member -NotePropertyName groups -NotePropertyValue @(
    [pscustomobject]@{
        name = "core"
        origin = @(8, 19.5, 11.5)
        children = @(0)
    }) -Force
[IO.File]::WriteAllText(
        $coreItemPath,
        ($coreItem | ConvertTo-Json -Depth 60 -Compress),
        [Text.UTF8Encoding]::new($false))

$launcherPath = Join-Path $itemDir "portable_launcher.json"
$launcher = Get-Content -Raw -LiteralPath $launcherPath | ConvertFrom-Json
$launcher | Add-Member -NotePropertyName display -NotePropertyValue ([pscustomobject]@{
    thirdperson_righthand = [pscustomobject]@{ translation = @(0, 3.25, 0) }
    thirdperson_lefthand = [pscustomobject]@{ translation = @(0, 3.75, 0) }
    firstperson_righthand = [pscustomobject]@{ translation = @(1.25, 4, 0) }
    firstperson_lefthand = [pscustomobject]@{ translation = @(3.5, 5.5, 0) }
    gui = [pscustomobject]@{
        rotation = @(22.5, -45, 0)
        translation = @(0, 6, 0)
        scale = @(1.5, 1.5, 1.5)
    }
}) -Force
$launcher | Add-Member -NotePropertyName groups -NotePropertyValue @(
    [pscustomobject]@{
        name = "C1"
        origin = @(12, 23, 12.25)
        children = @(
            0,
            [pscustomobject]@{
                name = "C2"
                origin = @(13, 23, 12.25)
                children = @(
                    1,
                    [pscustomobject]@{
                        name = "C3"
                        origin = @(17.24264, 27.24264, 12.25)
                        children = @(2)
                    })
            })
    }) -Force
[IO.File]::WriteAllText(
        $launcherPath,
        ($launcher | ConvertTo-Json -Depth 60 -Compress),
        [Text.UTF8Encoding]::new($false))

Write-Output "Backpack component bones and item groups synchronized."
