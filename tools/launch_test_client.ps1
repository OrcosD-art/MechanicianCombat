param(
	[switch]$QuickPlay
)

$ErrorActionPreference = "Stop"

$java = "C:\Program Files\Java\jdk-21\bin\java.exe"
$argsFile = "D:\codex\mod\rebuild\launch_test.args"
$gameDir = Get-ChildItem -LiteralPath "D:\Game\mc" -Directory |
		ForEach-Object {
			$versions = Join-Path $_.FullName ".minecraft\versions"
			if (Test-Path -LiteralPath $versions) {
				Get-ChildItem -LiteralPath $versions -Directory
			}
		} |
		Where-Object Name -Like "1.21.1-Fabric*" |
		Select-Object -First 1
if (-not $gameDir) {
	throw "Test client directory was not found"
}

Set-Location -LiteralPath $gameDir.FullName
$argumentText = [System.IO.File]::ReadAllText(
		$argsFile, [System.Text.Encoding]::UTF8)
$matches = [regex]::Matches($argumentText, '"(?<quoted>[^"]*)"|(?<plain>\S+)')
$javaArgs = foreach ($match in $matches) {
	if ($match.Groups["quoted"].Success) {
		$match.Groups["quoted"].Value -replace "[\r\n]+", ""
	} else {
		$match.Groups["plain"].Value
	}
}
$classpathIndex = [Array]::IndexOf($javaArgs, "-cp")
if ($classpathIndex -ge 0 -and $classpathIndex + 1 -lt $javaArgs.Count) {
	$minecraftDir = $gameDir.Parent.Parent.FullName
	$librariesDir = Join-Path $minecraftDir "libraries"
	$profilePath = Join-Path $gameDir.FullName ($gameDir.Name + ".json")
	$profile = Get-Content -Raw -Encoding UTF8 $profilePath |
			ConvertFrom-Json
	$classpath = $javaArgs[$classpathIndex + 1]
	foreach ($library in $profile.libraries) {
		$relative = $library.downloads.artifact.path
		if (-not $relative -and $library.name -match "^([^:]+):([^:]+):(.+)$") {
			$groupPath = $Matches[1] -replace "\.", "/"
			$artifact = $Matches[2]
			$version = $Matches[3]
			$relative = "$groupPath/$artifact/$version/$artifact-$version.jar"
		}
		if ($relative) {
			$jar = Join-Path $librariesDir $relative
			if ((Test-Path -LiteralPath $jar) -and
					-not $classpath.Contains($jar)) {
				$classpath = $jar + ";" + $classpath
			}
		}
	}
	$javaArgs[$classpathIndex + 1] = $classpath
}
if ($QuickPlay) {
	$saves = Join-Path $gameDir.Parent.Parent.FullName "saves"
	$save = Get-ChildItem -LiteralPath $saves -Directory |
			Sort-Object LastWriteTime -Descending |
			Select-Object -First 1
	if ($save) {
		$javaArgs += "--quickPlaySingleplayer"
		$javaArgs += $save.Name
	}
}
& $java @javaArgs
