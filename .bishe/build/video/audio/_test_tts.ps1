
Add-Type -AssemblyName System.Speech
 = New-Object System.Speech.Synthesis.SpeechSynthesizer
.SelectVoice('Microsoft Huihui Desktop')
.Rate = -1
.SetOutputToWaveFile('D:\Programming File\IDEA\health-hack-system\.bishe\build\video\audio\test_01.wav')
.Speak([Console]::In.ReadToEnd())
.Dispose()
