param(
    [int]$Port = 9336,
    [string]$Exe = "C:\Users\lmc\AppData\Local\Programs\Blockbench\Blockbench.exe",
    [string]$Profile = "D:\codex\mod\rebuild\bbprof_backpack_ps",
    [string]$Plugin = "D:\codex\mod\rebuild\tools\geckolib.js",
    [string]$SourceDir = ""
)
$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [Text.Encoding]::UTF8

$Root = "D:\codex\mod\rebuild"
if (-not $SourceDir) { throw "SourceDir is required (ASCII script, Chinese path supplied by caller)" }
$WearablePath = Join-Path $SourceDir "combat_backpack.bbmodel"
$ItemPath = (Get-ChildItem -LiteralPath $SourceDir -File -Filter "combat_backpack*.bbmodel" |
    Where-Object {
        $_.Name -ne "combat_backpack.bbmodel" -and
        $_.Name -notlike "combat_backpack_gray*.bbmodel"
    } |
    Select-Object -First 1).FullName
if (-not $ItemPath) { throw "item bbmodel not found" }
$Res = Join-Path $Root "mod\src\main\resources\assets\tkgun"
$OutGeo = Join-Path $Res "geo\entity\combat_backpack.geo.json"
$OutRigGeo = Join-Path $Res "geo\entity\combat_backpack_rig.geo.json"
$RawGeo = Join-Path $Root "build\combat_backpack.raw.geo.json"
$OutAnim = Join-Path $Res "animations\entity\combat_backpack.animation.json"
$OutItem = Join-Path $Res "models\item\combat_backpack.json"
$OutTex = Join-Path $Res "textures\entity\combat_backpack.png"
$ItemTex = Join-Path $Res "textures\item\combat_backpack.png"
$JobPath = Join-Path $Root "build\combat_backpack_job.js"
$ExportScript = Join-Path $Root "tools\bb_export_geckolib.js"
$Cdp = Join-Path $Root "tools\cdp.ps1"
$ProbePath = Join-Path $Root "build\combat_backpack_probe.js"
$PluginLoadPath = Join-Path $Root "build\combat_backpack_plugin_load.js"
$BlockbenchOut = Join-Path $Root "build\combat_backpack_blockbench.out.log"
$BlockbenchErr = Join-Path $Root "build\combat_backpack_blockbench.err.log"

function Clone-Json($value) {
    return ($value | ConvertTo-Json -Depth 100 | ConvertFrom-Json)
}

function Find-Group($nodes, [string]$name) {
    foreach ($node in $nodes) {
        if ($node -is [string]) { continue }
        if ($node.name -eq $name) { return $node }
        $found = Find-Group $node.children $name
        if ($null -ne $found) { return $found }
    }
    return $null
}

function Expand-Group-Data($model) {
    if ($null -eq $model.groups) { return }
    $groupMap = @{}
    foreach ($group in $model.groups) {
        $groupMap[[string]$group.uuid] = $group
    }
    function Apply-To-Node($node) {
        if ($node -is [string]) { return }
        $group = $groupMap[[string]$node.uuid]
        if ($null -ne $group) {
            $node | Add-Member -NotePropertyName name -NotePropertyValue $group.name -Force
            $node | Add-Member -NotePropertyName origin -NotePropertyValue @($group.origin) -Force
            $node | Add-Member -NotePropertyName rotation -NotePropertyValue @($group.rotation) -Force
        }
        foreach ($child in $node.children) {
            Apply-To-Node $child
        }
    }
    foreach ($node in $model.outliner) {
        Apply-To-Node $node
    }
}

function Shift-GroupY($group, [double]$amount) {
    if ($null -ne $group.pivot) {
        $group.pivot[1] = [double]$group.pivot[1] + $amount
    }
    foreach ($child in $group.children) {
        if ($child -isnot [string]) { Shift-GroupY $child $amount }
    }
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

function Write-JsonFile([string]$path, $value) {
    New-Item -ItemType Directory -Force -Path (Split-Path $path) | Out-Null
    $json = $value | ConvertTo-Json -Depth 100 -Compress
    [IO.File]::WriteAllText($path, $json, [Text.UTF8Encoding]::new($false))
}

function Decode-Texture($model, [string]$path) {
    $texture = $model.textures[0]
    $source = [string]$texture.source
    if ($source.StartsWith("data:image/") -and $source.Contains(";base64,")) {
        $bytes = [Convert]::FromBase64String($source.Substring($source.IndexOf(",", [StringComparison]::Ordinal) + 1))
    } else {
        $bytes = [IO.File]::ReadAllBytes([string]$texture.path)
    }
    New-Item -ItemType Directory -Force -Path (Split-Path $path) | Out-Null
    [IO.File]::WriteAllBytes($path, $bytes)
}

$wearable = Get-Content -LiteralPath $WearablePath -Raw -Encoding utf8 | ConvertFrom-Json
Expand-Group-Data $wearable
$item = Get-Content -LiteralPath $ItemPath -Raw -Encoding utf8 | ConvertFrom-Json
$c3Group = Find-Group $wearable.outliner "C3"
if ($null -eq $c3Group) { throw "C3 group missing" }
$c3ChildIds = @($c3Group.children | Where-Object { $_ -is [string] })
$c3Locators = @($wearable.elements | Where-Object {
    $_.type -eq "locator" -and
    $_.uuid -in $c3ChildIds -and
    $_.name -in @("front", "rear")
})
if ($c3Locators.Count -ne 2) { throw "front/rear locators missing from C3" }

# Wearable export: keep only the backpack subtree and rebase it from feet to chest height.
$wearExport = Clone-Json $wearable
$backpack = Clone-Json (Find-Group $wearExport.outliner "combat_backpack")
if ($null -eq $backpack) { throw "combat_backpack group missing" }
if ($backpack.PSObject.Properties.Name -contains "parent") {
    $backpack.PSObject.Properties.Remove("parent")
}
$ids = [System.Collections.Generic.HashSet[string]]::new()
Collect-ElementIds $backpack $ids
Shift-GroupY $backpack -12.0
foreach ($element in $wearExport.elements) {
    if (-not $ids.Contains([string]$element.uuid)) { continue }
    if ($null -eq $element.from -or $null -eq $element.to) { continue }
    $element.from[1] = [double]$element.from[1] - 12.0
    $element.to[1] = [double]$element.to[1] - 12.0
    if ($null -ne $element.origin) {
        $element.origin[1] = [double]$element.origin[1] - 12.0
    }
}
$wearExport.elements = @($wearExport.elements |
    Where-Object { $ids.Contains([string]$_.uuid) })
$wearExport.outliner = @($backpack)

Decode-Texture $wearable $OutTex
Decode-Texture $item $ItemTex

$jobObject = [ordered]@{
    name = "combat_backpack"
    modelJson = $wearExport
    skinUv = @{}
}
$job = "globalThis.__BB_JOB = " + ($jobObject | ConvertTo-Json -Depth 100 -Compress) + ";`nreturn 1;"
[IO.File]::WriteAllText($JobPath, $job, [Text.UTF8Encoding]::new($false))

New-Item -ItemType Directory -Force -Path (Join-Path $Profile "plugins") | Out-Null
Copy-Item -LiteralPath $Plugin -Destination (Join-Path $Profile "plugins\geckolib.js") -Force

$process = Start-Process -FilePath $Exe -ArgumentList @(
    "--remote-debugging-port=$Port", "--no-sandbox", "--user-data-dir=$Profile"
) -WindowStyle Hidden -PassThru -RedirectStandardOutput $BlockbenchOut `
    -RedirectStandardError $BlockbenchErr
try {
    $ready = $false
    for ($i = 0; $i -lt 45; $i++) {
        try {
            $content = (Invoke-WebRequest "http://127.0.0.1:$Port/json/list" -UseBasicParsing -TimeoutSec 3).Content
            if ($content -match "webSocketDebuggerUrl") { $ready = $true; break }
        } catch { }
        Start-Sleep -Seconds 2
    }
    if (-not $ready) { throw "Blockbench debug port $Port did not start" }
    Start-Sleep -Seconds 7

    $probeJs = @'
return JSON.stringify({
    version: Blockbench.version,
    gecko_actions: Object.keys(BarItems || {}).filter(k => /^export_geckolib/.test(k))
});
'@
    [IO.File]::WriteAllText($ProbePath, $probeJs, [Text.UTF8Encoding]::new($false))
    $probe = ((& powershell -NoProfile -ExecutionPolicy Bypass -File $Cdp --port $Port --file $ProbePath) -join "`n") |
        ConvertFrom-Json
    if (-not $probe.gecko_actions -or $probe.gecko_actions.Count -eq 0) {
        $pluginJs = $Plugin.Replace("\", "/")
        $loadJs = @'
const info = { catalog: false, loaded: false, actions: [], error: null };
function findGecko() {
    const all = Plugins.all || [];
    return all.filter(p => p && p.id === 'geckolib')[0] || all['geckolib'] || null;
}
try {
    if (Plugins.loading_promise) {
        try { await Plugins.loading_promise; } catch (e) { }
    }
    for (let i = 0; i < 60; i++) {
        info.catalog = !!findGecko();
        if (info.catalog) break;
        await new Promise(r => setTimeout(r, 500));
    }
    const plugin = findGecko();
    if (!plugin) throw new Error('geckolib not found in plugin catalog');
    await plugin.load('__PLUGIN_PATH__');
    info.loaded = !!plugin.loaded;
    for (let i = 0; i < 40; i++) {
        info.actions = Object.keys(BarItems || {}).filter(k => /^export_geckolib/.test(k));
        if (info.actions.length) break;
        await new Promise(r => setTimeout(r, 500));
    }
} catch (e) {
    info.error = String(e && e.stack || e);
}
return JSON.stringify(info);
'@.Replace("__PLUGIN_PATH__", $pluginJs)
        [IO.File]::WriteAllText($PluginLoadPath, $loadJs, [Text.UTF8Encoding]::new($false))
        $loaded = ((& powershell -NoProfile -ExecutionPolicy Bypass -File $Cdp --port $Port --file $PluginLoadPath) -join "`n") |
            ConvertFrom-Json
        if (-not $loaded.actions -or $loaded.actions.Count -eq 0) {
            throw "GeckoLib plugin failed to load: $($loaded.error)"
        }
    }

    & powershell -NoProfile -ExecutionPolicy Bypass -File $Cdp --port $Port --file $JobPath | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Blockbench model load failed ($LASTEXITCODE)" }
    $raw = & powershell -NoProfile -ExecutionPolicy Bypass -File $Cdp --port $Port --file $ExportScript
    if ($LASTEXITCODE -ne 0) { throw "Blockbench export failed ($LASTEXITCODE)" }
    $info = ($raw -join "`n") | ConvertFrom-Json
    Write-Output ("actions: " + (($info.log | ForEach-Object { [string]$_ }) -join " | "))
    Write-Output ("exports: " + (($info.exports | ForEach-Object {
        "{0} [{1}] {2}" -f $_.name, $_.type, $_.bytes
    }) -join ", "))

    $wroteGeo = $false
    $wroteAnim = $false
    foreach ($entry in $info.exports) {
        $name = ([string]$entry.name).ToLowerInvariant()
        if (-not $entry.content) { continue }
        if ($name.EndsWith(".geo") -or $name.Contains("geo")) {
            [IO.File]::WriteAllText($RawGeo, [string]$entry.content, [Text.UTF8Encoding]::new($false))
            $doc = [string]$entry.content | ConvertFrom-Json
            $rigDoc = Clone-Json $doc
            foreach ($geometry in $doc.'minecraft:geometry') {
                $geometry.description.texture_width = 48
                $geometry.description.texture_height = 48
                $geometry.bones = @($geometry.bones | Where-Object { $_.name -ne "bb_main" })
                foreach ($bone in $geometry.bones) {
                    if ($null -ne $bone.pivot) {
                        if ($bone.name -eq "combat_backpack") {
                            $bone.pivot[0] = 0.0
                            $bone.pivot[1] = 0.0
                            $bone.pivot[2] = 0.0
                        } else {
                            # Rebase rotating bones around the backpack root.
                            # Wearable models are mirrored four pixels from the
                            # armor/player coordinate origin.
                            $bone.pivot[0] = [double]$bone.pivot[0] - 4.0
                            $bone.pivot[1] = [double]$bone.pivot[1] - 24.0
                            $bone.pivot[2] = [double]$bone.pivot[2] - 2.0
                        }
                    }
                    foreach ($cube in $bone.cubes) {
                        $cube.origin[0] = [double]$cube.origin[0] - 4.0
                        $cube.origin[1] = [double]$cube.origin[1] - 12.0
                        $cube.origin[2] = [double]$cube.origin[2] - 2.0
                        if ($null -ne $cube.pivot) {
                            $cube.pivot[0] = [double]$cube.pivot[0] - 4.0
                            $cube.pivot[1] = [double]$cube.pivot[1] - 12.0
                            $cube.pivot[2] = [double]$cube.pivot[2] - 2.0
                        }
                    }
                }
                $geometry.bones = @($geometry.bones |
                    Where-Object { $_.name -notin @("front", "rear") })
                foreach ($locator in $c3Locators) {
                    $position = @($locator.position)
                    $pivotX = ([double]$position[0] * -1.0) - 4.0
                    $pivotY = ([double]$position[1]) - 24.0
                    $pivotZ = ([double]$position[2]) - 2.0
                    $locatorBone = [pscustomobject]@{
                        name = $locator.name
                        parent = "C3"
                        pivot = @($pivotX, $pivotY, $pivotZ)
                    }
                    $geometry.bones = @($geometry.bones) + $locatorBone
                }
            }
            foreach ($geometry in $rigDoc.'minecraft:geometry') {
                $geometry.description.texture_width = 48
                $geometry.description.texture_height = 48
                $geometry.bones = @($geometry.bones | Where-Object { $_.name -ne "bb_main" })
                foreach ($bone in $geometry.bones) {
                    if ($null -ne $bone.pivot) {
                        if ($bone.name -eq "combat_backpack") {
                            $bone.pivot[0] = 0.0
                            $bone.pivot[1] = 0.0
                            $bone.pivot[2] = 4.0
                        } else {
                            $bone.pivot[1] = [double]$bone.pivot[1] - 12.0
                        }
                    }
                }
                $geometry.bones = @($geometry.bones |
                    Where-Object { $_.name -notin @("front", "rear") })
                foreach ($locator in $c3Locators) {
                    $position = @($locator.position)
                    $pivotX = ([double]$position[0] * -1.0)
                    $pivotY = ([double]$position[1]) - 12.0
                    $pivotZ = [double]$position[2]
                    $locatorBone = [pscustomobject]@{
                        name = $locator.name
                        parent = "C3"
                        pivot = @($pivotX, $pivotY, $pivotZ)
                    }
                    $geometry.bones = @($geometry.bones) + $locatorBone
                }
            }
            Write-JsonFile $OutGeo $doc
            Write-JsonFile $OutRigGeo $rigDoc
            $wroteGeo = $true
        } elseif ($name.Contains("animation") -or $name.EndsWith(".json")) {
            New-Item -ItemType Directory -Force -Path (Split-Path $OutAnim) | Out-Null
            [IO.File]::WriteAllText($OutAnim, [string]$entry.content, [Text.UTF8Encoding]::new($false))
            $wroteAnim = $true
        }
    }
    if (-not $wroteGeo -or -not $wroteAnim) { throw "missing geo or animation export" }
    Write-Output "wrote $OutItem"
    Write-Output "wrote $OutGeo"
    Write-Output "wrote $OutRigGeo"
    Write-Output "wrote $OutAnim"
} finally {
    if ($process -and -not $process.HasExited) {
        Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
    }
}
