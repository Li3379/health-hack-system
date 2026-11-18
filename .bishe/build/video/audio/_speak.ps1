
Add-Type -AssemblyName System.Speech
 = New-Object System.Speech.Synthesis.SpeechSynthesizer
.SelectVoice('Microsoft Huihui Desktop')
.Rate = -1
.SetOutputToWaveFile('D:\Programming File\IDEA\health-hack-system\.bishe\build\video\audio\_test_speak.wav')
 = [System.IO.File]::ReadAllText('D:\Programming File\IDEA\health-hack-system\.bishe\build\video\audio\_test_speak.txt', [System.Text.Encoding]::UTF8)
.Speak()
.Dispose()
