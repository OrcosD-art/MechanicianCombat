param(
	[Parameter(Mandatory = $true)]
	[string[]] $Path
)

$utf8NoBom = [System.Text.UTF8Encoding]::new($false)
$oldLength = '"animation_length":1,"bones":'
$newLength = '"animation_length":1.25,"bones":'
$oldParticles = '"particle_effects":{}'
$newParticles = '"particle_effects":{"0.25":{"effect":"particles","locator":"locator"}}'

foreach ($file in $Path) {
	$text = [System.IO.File]::ReadAllText($file)
	$key = '"Overheat":{'
	$keyMatches = [regex]::Matches($text, [regex]::Escape($key))
	if ($keyMatches.Count -ne 1) {
		throw "$file : expected one Overheat animation, found $($keyMatches.Count)"
	}

	$objectStart = $keyMatches[0].Index + $key.Length - 1
	$depth = 0
	$inString = $false
	$escaped = $false
	$objectEnd = -1

	for ($i = $objectStart; $i -lt $text.Length; $i++) {
		$ch = $text[$i]
		if ($inString) {
			if ($escaped) {
				$escaped = $false
			} elseif ($ch -eq '\') {
				$escaped = $true
			} elseif ($ch -eq '"') {
				$inString = $false
			}
			continue
		}

		if ($ch -eq '"') {
			$inString = $true
		} elseif ($ch -eq '{') {
			$depth++
		} elseif ($ch -eq '}') {
			$depth--
			if ($depth -eq 0) {
				$objectEnd = $i
				break
			}
		}
	}

	if ($objectEnd -lt 0) {
		throw "$file : could not find the end of Overheat"
	}

	$length = $objectEnd - $objectStart + 1
	$object = $text.Substring($objectStart, $length)
	if ($object.Contains($newParticles)) {
		Write-Host "$file : already patched"
		continue
	}
	if ([regex]::Matches($object, [regex]::Escape($oldLength)).Count -ne 1) {
		throw "$file : unexpected Overheat animation_length"
	}
	if ([regex]::Matches($object, [regex]::Escape($oldParticles)).Count -ne 1) {
		throw "$file : unexpected Overheat particle_effects"
	}

	$object = $object.Replace($oldLength, $newLength)
	$object = $object.Replace($oldParticles, $newParticles)
	$text = $text.Remove($objectStart, $length).Insert($objectStart, $object)
	[System.IO.File]::WriteAllText($file, $text, $utf8NoBom)
	Write-Host "$file : patched Overheat at 0.25s"
}
