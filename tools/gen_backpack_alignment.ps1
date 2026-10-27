param(
    [Parameter(Mandatory = $true)][string]$SourceDir
)
$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [Text.Encoding]::UTF8

$source = Join-Path $SourceDir "combat_backpack.bbmodel"
$out = "D:\codex\mod\rebuild\build\backpack_alignment.bbmodel"
$model = Get-Content -LiteralPath $source -Raw -Encoding utf8 | ConvertFrom-Json

function Find-Group($nodes, [string]$name) {
    foreach ($node in $nodes) {
        if ($node -is [string]) { continue }
        if ($node.name -eq $name) { return $node }
        $found = Find-Group $node.children $name
        if ($null -ne $found) { return $found }
    }
    return $null
}

function Collect-ElementIds($group, $ids) {
    foreach ($child in $group.children) {
        if ($child -is [string]) {
            [void]$ids.Add($child)
        } else {
            Collect-ElementIds $child $ids
        }
    }
}

$backpack = Find-Group $model.outliner "combat_backpack"
if ($null -eq $backpack) { throw "combat_backpack group missing" }
$ids = [System.Collections.Generic.HashSet[string]]::new()
Collect-ElementIds $backpack $ids

$body = [ordered]@{
    name = "body"
    box_uv = $false
    rescale = $false
    locked = $true
    light_emission = 0
    render_order = "default"
    allow_mirror_modeling = $true
    from = @(-4, 12, -2)
    to = @(4, 24, 2)
    autouv = 0
    color = 3
    origin = @(0, 12, 0)
    faces = [ordered]@{
        north = @{uv = @(0, 0, 8, 12); texture = $null}
        east = @{uv = @(0, 0, 4, 12); texture = $null}
        south = @{uv = @(0, 0, 8, 12); texture = $null}
        west = @{uv = @(0, 0, 4, 12); texture = $null}
        up = @{uv = @(0, 0, 8, 4); texture = $null}
        down = @{uv = @(0, 0, 8, 4); texture = $null}
    }
    type = "cube"
    uuid = "c6cb7e2b-8b7a-4d2a-ae32-16f48a5eb111"
}

$redBack = [ordered]@{
    name = "body_back_red"
    box_uv = $false
    rescale = $false
    locked = $true
    light_emission = 0
    render_order = "default"
    allow_mirror_modeling = $true
    from = @(-4, 12, 2)
    to = @(4, 24, 2.1)
    autouv = 0
    color = 0
    origin = @(0, 18, 2)
    faces = [ordered]@{
        south = @{uv = @(0, 0, 8, 12); texture = $null}
        up = @{uv = @(0, 0, 8, 1); texture = $null}
        down = @{uv = @(0, 0, 8, 1); texture = $null}
    }
    type = "cube"
    uuid = "46f041b7-7f3f-49c0-bf14-a3cd6f84f222"
}

$bodyGroup = [ordered]@{
    name = "body_REFERENCE_LOCKED"
    origin = @(0, 12, 0)
    color = 3
    uuid = "57d0f654-59f9-4b5f-a420-2e199ca8d333"
    export = $false
    mirror_uv = $false
    isOpen = $true
    locked = $true
    visibility = $true
    autouv = 0
    selected = $false
    children = @($body.uuid)
}

$redGroup = [ordered]@{
    name = "back_red_REFERENCE_LOCKED"
    origin = @(0, 18, 2)
    color = 0
    uuid = "68e1a765-6afa-4c70-8021-3f2aaed9e444"
    export = $false
    mirror_uv = $false
    isOpen = $true
    locked = $true
    visibility = $true
    autouv = 0
    selected = $false
    children = @($redBack.uuid)
}

$backpackGroup = $backpack | ConvertTo-Json -Depth 100 | ConvertFrom-Json
$backpackGroup.locked = $false
$backpackGroup.selected = $true
$backpackGroup.isOpen = $true

$newModel = [ordered]@{
    meta = [ordered]@{
        format_version = "4.10"
        model_format = "free"
        box_uv = $false
    }
    name = "backpack_alignment"
    model_identifier = ""
    visible_box = @(1, 1, 0)
    variable_placeholders = ""
    variable_placeholder_buttons = @()
    timeline_setups = @()
    unhandled_root_fields = @{}
    resolution = [ordered]@{width = 16; height = 16}
    elements = @($body, $redBack) + @($model.elements | Where-Object { $ids.Contains([string]$_.uuid) })
    outliner = @($bodyGroup, $redGroup, $backpackGroup)
    textures = @($model.textures)
    animations = @()
    animation_variable_placeholders = ""
}

New-Item -ItemType Directory -Force -Path (Split-Path $out) | Out-Null
$json = $newModel | ConvertTo-Json -Depth 100 -Compress
[IO.File]::WriteAllText($out, $json, [Text.UTF8Encoding]::new($false))
Write-Output $out
