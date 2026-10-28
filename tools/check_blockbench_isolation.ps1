param(
    [string]$Root = "D:\codex\mod\rebuild"
)
$ErrorActionPreference = "Stop"

$expected = @(
    @{ Script = "export_gun_raw.ps1"; Version = "5.2.2"; Profile = "bbprofile_bb522"; Plugin = "tools\geckolib.js" },
    @{ Script = "export_item_model_raw.ps1"; Version = "5.2.2"; Profile = "bbprofile_bb522"; Plugin = $null },
    @{ Script = "export_auto_launcher_model.ps1"; Version = "5.2.2"; Profile = "bbprofile_bb522"; Plugin = "tools\geckolib.js" },
    @{ Script = "gen_backpack_item.ps1"; Version = "5.2.2"; Profile = "bbprofile_bb522"; Plugin = $null },
    @{ Script = "gen_backpack_assets.ps1"; Version = "4.12.6"; Profile = "bbprofile_bb4126"; Plugin = "tools\geckolib_legacy_412.js" },
    @{ Script = "run_export.ps1"; Version = "4.12.6"; Profile = "bbprofile_bb4126"; Plugin = "tools\geckolib_legacy_412.js" },
    @{ Script = "run_js.ps1"; Version = "5.2.2"; Profile = "bbprofile_bb522"; Plugin = $null },
    @{ Script = "bb_launch.ps1"; Version = "4.12.6"; Profile = "bbprofile_bb4126"; Plugin = $null }
)

$failures = New-Object System.Collections.Generic.List[string]
$rows = @()
foreach ($item in $expected) {
    $path = Join-Path $Root ("tools\" + $item.Script)
    if (-not (Test-Path -LiteralPath $path)) {
        $failures.Add("Missing script: $path")
        continue
    }
    $exeMatch = Select-String -Path $path -Pattern '\[string\]\$Exe\s*=\s*"([^"]+)"' | Select-Object -First 1
    $profileMatch = Select-String -Path $path -Pattern '\[string\]\$Profile\s*=\s*"([^"]+)"' | Select-Object -First 1
    $pluginMatch = Select-String -Path $path -Pattern '\$(?:Plugin|pluginPath)\s*=\s*"([^"]+)"' | Select-Object -First 1
    $exe = if ($exeMatch) { $exeMatch.Matches[0].Groups[1].Value } else { "" }
    $profile = if ($profileMatch) { $profileMatch.Matches[0].Groups[1].Value } else { "" }
    $plugin = if ($pluginMatch) { $pluginMatch.Matches[0].Groups[1].Value } else { "" }
    $actualVersion = if (Test-Path -LiteralPath $exe) {
        (Get-Item -LiteralPath $exe).VersionInfo.ProductVersion
    } else {
        "missing"
    }
    $rows += [pscustomobject]@{
        Script = $item.Script
        Version = $actualVersion
        Profile = Split-Path -Leaf $profile
        Plugin = if ($plugin) { Split-Path -Leaf $plugin } else { "" }
    }
    if ($actualVersion -notlike ($item.Version + "*")) {
        $failures.Add("$($item.Script): expected Blockbench $($item.Version), got $actualVersion")
    }
    if ((Split-Path -Leaf $profile) -ne $item.Profile) {
        $failures.Add("$($item.Script): expected profile $($item.Profile), got $(Split-Path -Leaf $profile)")
    }
    if ($item.Plugin) {
        $expectedPlugin = [IO.Path]::GetFullPath((Join-Path $Root $item.Plugin))
        if (-not $plugin -or [IO.Path]::GetFullPath($plugin) -ne $expectedPlugin) {
            $failures.Add("$($item.Script): expected plugin $expectedPlugin, got $plugin")
        }
    }
}

$legacy = Join-Path $Root "tools\geckolib_legacy_412.js"
$modern = Join-Path $Root "tools\geckolib.js"
if (-not (Test-Path -LiteralPath $legacy)) {
    $failures.Add("Missing legacy GeckoLib plugin: $legacy")
} elseif (-not (Select-String -Path $legacy -Pattern '"max_version":"5.0.0"' -Quiet)) {
    $failures.Add("Legacy GeckoLib plugin does not declare the Blockbench 4.x ceiling.")
}
if (-not (Test-Path -LiteralPath $modern)) {
    $failures.Add("Missing modern GeckoLib plugin: $modern")
} elseif (-not (Select-String -Path $modern -Pattern '"min_version":"5.0.0"' -Quiet)) {
    $failures.Add("Modern GeckoLib plugin does not declare the Blockbench 5.x floor.")
}

$profileChecks = @(
    @{ Profile = "bbprofile_bb4126"; Plugin = "animation_utils.js" },
    @{ Profile = "bbprofile_bb522"; Plugin = "geckolib.js" }
)
foreach ($check in $profileChecks) {
    $pluginDir = Join-Path $Root ($check.Profile + "\plugins")
    $files = @(Get-ChildItem -LiteralPath $pluginDir -File -ErrorAction SilentlyContinue)
    if ($check.Plugin) {
        if (-not ($files.Name -contains $check.Plugin)) {
            $failures.Add("$($check.Profile): missing expected plugin $($check.Plugin)")
        }
        if ($files.Count -ne 1) {
            $failures.Add("$($check.Profile): expected exactly one plugin, got $($files.Count)")
        }
    } elseif ($files.Count -ne 0) {
        $failures.Add("$($check.Profile): expected no plugins, got $($files.Count)")
    }
}

$legacyProfile = Join-Path $Root "bbprofile\plugins"
$legacyProfileFiles = @(Get-ChildItem -LiteralPath $legacyProfile -File -ErrorAction SilentlyContinue)
if ($legacyProfileFiles.Count -gt 1) {
    $failures.Add("bbprofile: duplicate plugin files remain ($($legacyProfileFiles.Name -join ', '))")
}

$oldProfiles = Select-String -Path (Join-Path $Root "tools\*.ps1") `
    -Pattern 'bbprofile5|bbprof_backpack_ps' |
    Where-Object { $_.Filename -ne "check_blockbench_isolation.ps1" }
foreach ($hit in $oldProfiles) {
    $failures.Add("$($hit.Filename):$($hit.LineNumber) still uses shared legacy profile $($hit.Matches[0].Value)")
}

$rows | Format-Table -AutoSize
if ($failures.Count -gt 0) {
    $failures | ForEach-Object { Write-Error $_ }
    exit 1
}
Write-Output "Blockbench version/profile/plugin isolation: OK"
