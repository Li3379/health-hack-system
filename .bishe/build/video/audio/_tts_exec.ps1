
Add-Type -AssemblyName System.Speech
$s = New-Object System.Speech.Synthesis.SpeechSynthesizer
$s.SelectVoice('Microsoft Huihui Desktop')
$s.Rate = -1
$s.SetOutputToWaveFile('D:/Programming File/IDEA/health-hack-system/.bishe/build/video/audio/scene_10.wav')
$txt = [System.IO.File]::ReadAllText('D:/Programming File/IDEA/health-hack-system/.bishe/build/video/audio/scene_10.wav.txt', [System.Text.Encoding]::UTF8)
$s.Speak($txt)
$s.Dispose()
