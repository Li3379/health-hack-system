
Add-Type -AssemblyName System.Speech
$txt = [System.IO.File]::ReadAllText('D:\Programming File\IDEA\health-hack-system\.bishe\build\video\audio\_test_input.txt', [System.Text.Encoding]::UTF8)
Write-Output "Read length: $($txt.Length)"
Write-Output "Content: $txt"
