import os
import subprocess

AUDIO = r"D:\Programming File\IDEA\health-hack-system\.bishe\build\video\audio"
os.makedirs(AUDIO, exist_ok=True)


def test_reading_method():
    # 方法 A: 写入 UTF-8 文件，PowerShell 用 Get-Content -Encoding utf8 读取
    txt_file = os.path.join(AUDIO, "_test_input.txt")
    with open(txt_file, "w", encoding="utf-8") as f:
        f.write("各位答辩评委老师好，我是软件技术2401班的肖俊豪。")

    ps_script = f"""
Add-Type -AssemblyName System.Speech
$txt = [System.IO.File]::ReadAllText('{txt_file}', [System.Text.Encoding]::UTF8)
Write-Output "Read length: $($txt.Length)"
Write-Output "Content: $txt"
"""
    script = os.path.join(AUDIO, "_test_read.ps1")
    with open(script, "w", encoding="utf-8-sig") as f:
        f.write(ps_script)

    r = subprocess.run(
        ["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", script],
        capture_output=True,
    )
    print("Method A (File UTF8):")
    print("STDOUT:", r.stdout.decode("gbk", errors="ignore"))

    # 查看当前已有的 scene_01.wav 的生成方式：
    # 如果用之前的 stdin.ReadToEnd()
    ps_old = f"""
$txt = [Console]::In.ReadToEnd()
Write-Output "Old method content: $txt"
"""
    script_old = os.path.join(AUDIO, "_test_old.ps1")
    with open(script_old, "w", encoding="utf-8-sig") as f:
        f.write(ps_old)
    r_old = subprocess.run(
        ["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", script_old],
        input="各位答辩评委老师好，我是软件技术2401班的肖俊豪。".encode("utf-8"),
        capture_output=True,
    )
    print("Method B (Old stdin encode utf8):")
    print("STDOUT:", r_old.stdout.decode("gbk", errors="ignore"))


if __name__ == "__main__":
    test_reading_method()
