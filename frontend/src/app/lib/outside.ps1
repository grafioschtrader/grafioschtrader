$root = (Get-Location).ProviderPath
$result = @()

Get-ChildItem -Recurse -Filter *.ts | ForEach-Object {

    $file = $_
    $fullPath = $file.FullName
    $relPath = $fullPath.Substring($root.Length).TrimStart('\','/')

    $dirRel = Split-Path $relPath -Parent
    if ([string]::IsNullOrEmpty($dirRel)) {
        $depth = 0
    } else {
        $depth = ($dirRel -split '[\\/]').Where({ $_ -ne '' }).Count
    }

    $lineNumber = 0

    Get-Content $fullPath | ForEach-Object {
        $line = $_
        $lineNumber++

        if ($line -match "^\s*import\s+.*\s+from\s+['""](?<path>\.\./[^'""]*)['""]") {

            $relImport = $matches['path']

            $ups = 0
            $tmp = $relImport
            while ($tmp.StartsWith("../")) {
                $ups++
                $tmp = $tmp.Substring(3)
            }

            if ($ups -gt $depth) {

                $text = "{0}:{1}: {2}" -f $relPath, $lineNumber, $line.Trim()

                # Sortierschlüssel aus { ... } bestimmen
                $sortKey = $line.Trim()
                if ($line -match "import\s*\{([^}]*)\}") {
                    $names = $matches[1]
                    $firstName = $names.Split(',')[0].Trim()
                    if ($firstName) {
                        $sortKey = $firstName
                    }
                }

                $result += [PSCustomObject]@{
                    SortKey = $sortKey
                    Text    = $text
                }
            }
        }
    }
}

$result |
  Sort-Object SortKey, Text |
  Select-Object -ExpandProperty Text
