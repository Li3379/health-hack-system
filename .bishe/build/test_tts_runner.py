import os
import subprocess

AUDIO = r"D:\Programming File\IDEA\health-hack-system\.bishe\build\video\audio"
os.makedirs(AUDIO, exist_ok=True)


def tts(text: str, out_wav: str, voice="Microsoft Huihui Desktop", rate=-1) -> None:
    ps = f"""
Add-Type -AssemblyName System.Speech
$s = New-Object System.Speech.Synthesis.SpeechSynthesizer
$s.SelectVoice('{voice}')
$s.Rate = {rate}
$s.SetOutputToWaveFile('{out_wav}')
$s.Speak([Console]::In.ReadToEnd())
$s.Dispose()
"""
    script = os.path.join(AUDIO, "_tts_exec.ps1")
    with open(script, "w", encoding="utf-8-sig") as f:
        f.write(ps)
    r = subprocess.run(
        ["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", script],
        input=text.encode("utf-8"),
        capture_output=True,
    )
    if r.returncode != 0:
        print("TTS stderr:", r.stderr.decode("gbk", errors="ignore"))
        raise RuntimeError("TTS failed")


if __name__ == "__main__":
    test_wav = os.path.join(AUDIO, "test_huihui.wav")
    tts("各位答辩评委老师好，我是软件技术2401班的肖俊豪。", test_wav)
    print("Success! Generated:", test_wav, "size:", os.path.getsize(test_wav))
    if os.path.exists(test_wav):
        os.remove(test_wav)
