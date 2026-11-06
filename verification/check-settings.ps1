param([switch]$ExpectRecovery)
$ErrorActionPreference = 'Stop'
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$workspace = Split-Path $PSScriptRoot -Parent
$dumpPath = Join-Path $PSScriptRoot 'settings-ui.xml'
function Read-Ui {
    & $adb shell uiautomator dump /sdcard/th9-settings.xml | Out-Null
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    & $adb pull /sdcard/th9-settings.xml $dumpPath 2>$null | Out-Null
    $ErrorActionPreference = $previousPreference
    if ($LASTEXITCODE -ne 0) { throw 'Could not read device hierarchy' }
    [xml]$doc = Get-Content -Encoding UTF8 -LiteralPath $dumpPath
    return $doc
}
function Get-Node($resourceId) {
    for ($attempt = 0; $attempt -lt 5; $attempt++) {
        $doc = Read-Ui
        $nodes = $doc.SelectNodes('//node') | Where-Object { $_.'resource-id' -eq ('com.example.th9:id/' + $resourceId) }
        if ($nodes) { return @($nodes)[0] }
    }
    throw "Missing view: $resourceId"
}
function Tap-Node($node) {
    $coordinates = [regex]::Matches($node.bounds, '\d+')
    $centerX = ([int]$coordinates[0].Value + [int]$coordinates[2].Value) / 2
    $centerY = ([int]$coordinates[1].Value + [int]$coordinates[3].Value) / 2
    & $adb shell input tap ([int]$centerX) ([int]$centerY) | Out-Null
}
function Set-Checked($id, [bool]$expected) {
    $node = Get-Node $id
    if (($node.checked -eq 'true') -ne $expected) {
        Tap-Node $node
        # Wait for Android to deliver the input and persist it before a force-stop.
        if (((Get-Node $id).checked -eq 'true') -ne $expected) { throw "Switch did not change: $id" }
    }
}
& $adb shell am force-stop com.example.th9
& $adb shell am start -n com.example.th9/.MainActivity | Out-Null
if ($ExpectRecovery) {
    if ((Get-Node 'tv_home_active_title').text -ne 'Phuc hoi sau khoi dong lai') { throw 'Wrong active session after cold launch' }
    if ((Get-Node 'tv_home_progress_label').text -notmatch '1/3') { throw 'Wrong frozen roster or attendance progress' }
}
Tap-Node (Get-Node 'action_settings')
$initialSound = (Get-Node 'switch_sound').checked -eq 'true'
$initialVibrate = (Get-Node 'switch_vibrate').checked -eq 'true'
$initialTheme = 'rb_theme_system'
foreach ($id in @('rb_theme_system', 'rb_theme_light', 'rb_theme_dark')) {
    if ((Get-Node $id).checked -eq 'true') { $initialTheme = $id }
}
try {
    Set-Checked 'rb_theme_light' $true
    Set-Checked 'switch_sound' $false
    Set-Checked 'switch_vibrate' $false
    & $adb shell am force-stop com.example.th9
    & $adb shell am start -n com.example.th9/.MainActivity | Out-Null
    Tap-Node (Get-Node 'action_settings')
    if ((Get-Node 'rb_theme_light').checked -ne 'true') { throw 'Theme was not restored after force-stop' }
    if ((Get-Node 'switch_sound').checked -ne 'false') { throw 'Sound was not restored after force-stop' }
    if ((Get-Node 'switch_vibrate').checked -ne 'false') { throw 'Vibration was not restored after force-stop' }
    'PASS: light theme, sound OFF, vibration OFF persisted after force-stop and cold launch.' |
        Set-Content -Encoding UTF8 (Join-Path $PSScriptRoot 'settings-cold-start.txt')
} finally {
    Set-Checked 'switch_sound' $initialSound
    Set-Checked 'switch_vibrate' $initialVibrate
    Set-Checked $initialTheme $true
    & $adb shell am force-stop com.example.th9
    & $adb shell am start -n com.example.th9/.MainActivity | Out-Null
}
if ($ExpectRecovery) {
    if ((Get-Node 'tv_home_active_title').text -ne 'Phuc hoi sau khoi dong lai') { throw 'Active session was lost' }
    if ((Get-Node 'tv_home_progress_label').text -notmatch '1/3') { throw 'Attendance progress was lost' }
    'PASS: the exact OPEN session and 1/3 attendance restored on cold startup after force-stop.' |
        Set-Content -Encoding UTF8 (Join-Path $PSScriptRoot 'active-cold-start.txt')
}
