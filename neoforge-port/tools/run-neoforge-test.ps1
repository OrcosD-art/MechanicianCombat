param(
    [string]$VersionDir = "",
    [string]$GameDir = "",
    [string]$QuickPlayWorld = "",
    [string]$JavaHome = "C:\Program Files\Java\jdk-21",
    [string]$FmlVersion = ""
)

$ErrorActionPreference = "Stop"
if (-not $VersionDir) {
    $VersionDir = Get-ChildItem -LiteralPath "D:\Game\mc" -Directory |
        ForEach-Object {
            Join-Path $_.FullName ".minecraft\versions\1.21.1-NeoForge"
        } |
        Where-Object {
            Test-Path -LiteralPath (Join-Path $_ "1.21.1-NeoForge.json")
        } |
        Select-Object -First 1
}
if (-not $VersionDir) {
    throw "NeoForge test instance not found"
}
if (-not $GameDir) {
    $GameDir = $VersionDir
}
$GameDir = [IO.Path]::GetFullPath($GameDir)
New-Item -ItemType Directory -Path $GameDir -Force | Out-Null
$minecraftRoot = Split-Path (Split-Path $VersionDir -Parent) -Parent
$profilePath = Join-Path $VersionDir "1.21.1-NeoForge.json"
if (-not (Test-Path -LiteralPath $profilePath)) {
    $profilePath = Get-ChildItem -LiteralPath $VersionDir -File -Filter "*.json" |
        Where-Object Name -Like "*neoforge*.json" |
        Select-Object -First 1 -ExpandProperty FullName
}
if (-not $profilePath) {
    throw "NeoForge version JSON not found in $VersionDir"
}
$profile = Get-Content -Raw -Encoding UTF8 $profilePath | ConvertFrom-Json
if (-not $FmlVersion) {
    $fmlLibrary = $profile.libraries |
        Where-Object { $_.name -match '^net\.neoforged\.fancymodloader:loader:(.+)$' } |
        Select-Object -First 1
    if (-not $fmlLibrary) {
        throw "Unable to determine FML version from $profilePath"
    }
    $FmlVersion = [regex]::Match($fmlLibrary.name,
            '^net\.neoforged\.fancymodloader:loader:(.+)$').Groups[1].Value
}
$librariesRoot = Join-Path $minecraftRoot "libraries"

function Test-Rules($rules) {
    if (-not $rules) {
        return $true
    }
    $allowed = $false
    foreach ($rule in $rules) {
        $matches = $true
        if ($rule.os.name -and $rule.os.name -notin "windows", "universal") {
            $matches = $false
        }
        if ($rule.features) {
            $matches = $false
        }
        if ($matches) {
            $allowed = $rule.action -eq "allow"
        }
    }
    return $allowed
}

$classpath = New-Object System.Collections.Generic.List[string]
$seenClasspath = New-Object 'System.Collections.Generic.HashSet[string]' (
        [StringComparer]::OrdinalIgnoreCase)
foreach ($library in $profile.libraries) {
    if (-not (Test-Rules $library.rules)) {
        continue
    }
    $artifact = $library.downloads.artifact
    if (-not $artifact.path) {
        continue
    }
    if ($artifact.path -like "net/neoforged/neoforge/*") {
        continue
    }
    $artifactPath = $artifact.path
    if ($artifactPath -match '^net/neoforged/fancymodloader/(loader|earlydisplay)/') {
        $artifactPath = $artifactPath -replace
                '^net/neoforged/fancymodloader/(loader|earlydisplay)/[^/]+/',
                "net/neoforged/fancymodloader/`$1/$FmlVersion/"
    }
    $path = Join-Path $librariesRoot ($artifactPath.Replace("/", "\"))
    if ((Test-Path -LiteralPath $path) -and $seenClasspath.Add($path)) {
        $classpath.Add($path)
    }
}

$uuid = "00000FFFFFFFFFFFFFFFFFFFFFF1B9CC"
$replacements = @{
    '${auth_player_name}' = "NeoForgeTest"
    '${version_name}' = "1.21.1-NeoForge"
    '${game_directory}' = $GameDir
    '${assets_root}' = (Join-Path $minecraftRoot "assets")
    '${assets_index_name}' = $profile.assetIndex.id
    '${auth_uuid}' = $uuid
    '${auth_access_token}' = $uuid
    '${clientid}' = $uuid
    '${auth_xuid}' = $uuid
    '${user_type}' = "msa"
    '${version_type}' = "release"
    '${resolution_width}' = "854"
    '${resolution_height}' = "480"
    '${natives_directory}' = (Join-Path $VersionDir "1.21.1-NeoForge-natives")
    '${launcher_name}' = "Codex"
    '${launcher_version}' = "1"
    '${library_directory}' = $librariesRoot
    '${classpath_separator}' = [IO.Path]::PathSeparator
}

function Convert-Argument($arguments) {
    $result = New-Object System.Collections.Generic.List[string]
    foreach ($argument in $arguments) {
        if ($argument -is [string]) {
            $value = $argument
            foreach ($entry in $replacements.GetEnumerator()) {
                $value = $value.Replace($entry.Key, $entry.Value)
            }
            $result.Add($value)
        } elseif ($argument.value -and (Test-Rules $argument.rules)) {
            foreach ($value in $argument.value) {
                foreach ($entry in $replacements.GetEnumerator()) {
                    $value = $value.Replace($entry.Key, $entry.Value)
                }
                $result.Add($value)
            }
        }
    }
    return $result
}

$jvm = Convert-Argument $profile.arguments.jvm
$game = Convert-Argument $profile.arguments.game
for ($i = 0; $i -lt $game.Count - 1; $i++) {
    if ($game[$i] -eq "--fml.fmlVersion") {
        $game[$i + 1] = $FmlVersion
    }
}
if ($QuickPlayWorld) {
    $game = @($game) + @("--quickPlaySingleplayer", $QuickPlayWorld)
}
$javaArgs = New-Object System.Collections.Generic.List[string]
for ($i = 0; $i -lt $jvm.Count; $i++) {
    $value = $jvm[$i]
    if ($value -eq "-cp") {
        $i++
        $javaArgs.Add("-cp")
        $javaArgs.Add($classpath -join [IO.Path]::PathSeparator)
    } else {
        $javaArgs.Add($value)
    }
}
$javaArgs.Add("-Xmx3072m")
$javaArgs.Add("-Dfml.ignoreInvalidMinecraftCertificates=True")
$javaArgs.Add("-Dfml.ignorePatchDiscrepancies=True")
$javaArgs.Add("-Dcustomskinloader.ignorePatchFailure=true")
$javaArgs.Add($profile.mainClass)
foreach ($value in $game) {
    $javaArgs.Add($value)
}

$startInfo = New-Object Diagnostics.ProcessStartInfo
$startInfo.FileName = Join-Path $JavaHome "bin\java.exe"
$startInfo.WorkingDirectory = $GameDir
$startInfo.UseShellExecute = $false
$quoted = foreach ($argument in $javaArgs) {
    if ($argument -notmatch '[\s"]' -and $argument.Length -gt 0) {
        $argument
        continue
    }
    $builder = New-Object Text.StringBuilder
    [void] $builder.Append('"')
    $slashes = 0
    foreach ($character in $argument.ToCharArray()) {
        if ($character -eq '\') {
            $slashes++
            continue
        }
        if ($character -eq '"') {
            [void] $builder.Append('\', $slashes * 2 + 1)
            [void] $builder.Append('"')
            $slashes = 0
            continue
        }
        if ($slashes -gt 0) {
            [void] $builder.Append('\', $slashes)
            $slashes = 0
        }
        [void] $builder.Append($character)
    }
    if ($slashes -gt 0) {
        [void] $builder.Append('\', $slashes * 2)
    }
    [void] $builder.Append('"')
    $builder.ToString()
}
$startInfo.Arguments = $quoted -join " "
[IO.File]::WriteAllText("D:\codex\mod\neoforge-port\build\launcher-arguments.txt",
        $startInfo.Arguments, (New-Object Text.UTF8Encoding($false)))
$process = [Diagnostics.Process]::Start($startInfo)
$process.WaitForExit()
exit $process.ExitCode
