# -*- coding: utf-8 -*-
"""基于真实前端页面与新选题《基于多源健康数据融合的智能监测与预警系统的设计与实现》，
全自动生成毕设答辩模拟演练高清演示视频（1080p，含中文语音旁白与点击引导）。

分镜包括：
  01: 答辩封面与开场白
  02: 登录认证界面与操作
  03: 健康管理仪表盘与全局概览
  04: 智能数据录入与多源融合（AI文本解析）
  05: 健康指标管理与趋势可视化
  06: 智能预警与分级处置（规则引擎+AI建议）
  07: 多维健康评分量化评估
  08: AI 健康顾问在线交互
  09: 实时监控与多端扩展
  10: 答辩全系统总结与致谢
"""
from __future__ import annotations

import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
FRAMES_DIR = os.path.join(HERE, "video", "frames")
AUDIO_DIR = os.path.join(HERE, "video", "audio")
SEGS_DIR = os.path.join(HERE, "video", "segs")
OUT_DIR = os.path.join(ROOT, "out")

os.makedirs(AUDIO_DIR, exist_ok=True)
os.makedirs(SEGS_DIR, exist_ok=True)
os.makedirs(OUT_DIR, exist_ok=True)

FFMPEG = "ffmpeg"
FFPROBE = "ffprobe"

SCENES = [
    # 01
    ("scene_01_cover.png",
     "各位答辩评委老师好，我是软件学院软件技术2401班的肖俊豪，我的校内指导教师是陈湘龙老师。"
     "今天我汇报的毕业设计题目是基于多源健康数据融合的智能监测与预警系统的设计与实现。"
     "随着智能穿戴与家用检测仪器的普及，个人健康数据呈现出多源、高频和碎片化的特征，"
     "但传统管理方式存在数据分散、指标解读门槛高、异常发现滞后等痛点。"
     "为此，本课题设计并实现了一套集数据多源融合、趋势动态监测、多级规则预警、大模型个性解读于一体的现代化健康监测平台。"
     "下面由我向各位老师进行系统的实际功能演练与答辩演示。"),

    # 02
    ("scene_02_login.png",
     "现在屏幕上展示的是系统的登录认证模块。系统基于 Spring Security 与无状态 J W T 令牌机制实现前后端分离鉴权。"
     "未登录用户无法访问受限业务接口。"
     "现在我们输入用户账号肖俊豪和密码，点击登录按钮。"
     "系统后端完成哈希比对后下发签名令牌，前端自动完成持久化存储，并平滑引导页面跳转至主控仪表盘。"),

    # 03
    ("scene_03_dashboard.png",
     "登录成功后，页面自动进入系统的核心主页，健康管理仪表盘。"
     "大家可以看到，顶部清晰展示了当前登录用户肖俊豪的身份标识与当日状态概览。"
     "仪表盘采用组件化布局，上方依次是健康评分、未读预警、健康指标总数和体检报告统计卡片；"
     "中间集成了动态环形进度条与最近预警摘要，直观呈现当日活动完成情况。"
     "用户在一屏之内即可全面掌握个人的整体健康生理态势，无需在各个独立模块间频繁切换。"),

    # 04
    ("scene_04_data_input.png",
     "接下来，我们点击左侧导航栏的智能录入模块。"
     "面对传统健康软件录入繁琐的难题，本系统构建了多源数据融合引擎，支持手工快捷输入、AI 自然语言解析、拍照识别以及智能设备自动同步。"
     "现在我们演示最实用的 AI 文本解析：在输入框中输入一句日常口语：昨天心率 72，血压 120比80，空腹血糖七点四，点击智能解析按钮。"
     "后端基于大语言模型与抽取引擎，在毫秒级内自动拆解出心率、收缩压、舒张压与血糖四项结构化数据。"
     "系统特别设计了人机协同确认机制，在表格核对无误后点击确认入库。"
     "后端写入数据库并利用复合唯一索引做时间戳冲突消解，同时发布领域事件，驱动下游分析。"),

    # 05
    ("scene_05_metrics.png",
     "数据成功入库后，我们点击左侧导航栏的健康指标模块。"
     "系统在此对用户连续采集的心率、血压、血糖、体重、睡眠等多维生理指标进行了时间序列聚合。"
     "在页面上方，用户可以自由切换指标类型与时间区间。下方的可视化折线图清晰标注了医学参考区间。"
     "例如我们刚才录入的空腹血糖七点四毫摩尔每升，曲线明显高出正常上限。"
     "在下方的明细列表中，系统自动赋予了琥珀色偏高标签，免去了用户自行对照参考值换算的认知负担。"),

    # 06
    ("scene_06_alerts.png",
     "现在我们点击左侧导航栏的智能预警页面。这是本课题的核心创新亮点。"
     "刚才录入的高血糖指标入库后，后端由 Spring 事件机制异步驱动预警引擎，依据用户个性化阈值规则库进行实时比对，自动生成了一条警告级别预警。"
     "为了避免告警疲劳，引擎内置了时间窗口聚合去重策略，短时间内同类指标反复越界将被合并并累计触发频次。"
     "更重要的是，我们点击卡片上的 AI 分析建议：系统联动大语言模型，针对本次血糖七点四的具体数值与用户年龄健康画像，即时输出了原因排查与饮食生活干预建议。"
     "最后我们点击确认处置，完成闭环管理。"),

    # 07
    ("scene_07_score.png",
     "接下来，我们点击切换到健康评分模块。"
     "针对传统健康软件缺乏综合量化指标的问题，本系统自主研发了多因子加权评分算法。"
     "总分设定为百分制，综合考量了基础指标达标率、近期偏离波动性、生活作息达标度以及异常预警惩罚扣分五大维度。"
     "页面左侧以直观的环形动画呈现总分与健康等级，右侧详细展开了各单项因子的得分明细。"
     "当用户持续保持规律运动或指标恢复正常后，评分模型将动态回升，以此形成游戏化的健康改善激励机制。"),

    # 08
    ("scene_08_aichat.png",
     "紧接着，我们点击左侧导航栏的 AI 顾问。"
     "针对普通大众看不懂生涩化验报告、缺乏全天候健康咨询的痛点，平台借助 Lang Chain for J 框架，深度集成了阿里通义千问大语言模型。"
     "在对话框中，我们发送一条提问：我刚才测的空腹血糖是七点四，请问晚餐应该注意吃什么？"
     "系统通过提示词工程，将提问与用户的最新档案、近期指标和预警上下文组装后提交大模型。"
     "大家请看，AI 顾问以打字机效果迅速给出了极具针对性的低升糖饮食建议，包括主食粗细搭配、蔬菜摄入比例及餐后散步指引，真正起到了口袋私人医生的作用。"),

    # 09
    ("scene_09_realtime.png",
     "最后，我们点击进入左侧导航栏的实时监控模块。"
     "系统利用全双工 Web Socket 长连接与轻量级心跳保活协议，模拟了可穿戴智能设备的秒级高频流式数据接入。"
     "在实时大屏上，心率波形与血氧饱和度实时动态演进，一旦侦测到瞬时骤停或极度心动过速，毫秒级推送到前端声光报警。"
     "此外，系统还扩展了体检报告智能归档与预防保健风险评估，真正做到了覆盖个人全生命周期的健康档案大融合。"),

    # 10
    ("scene_10_summary.png",
     "到这里，基于多源健康数据融合的智能监测与预警系统的核心功能就全部演示完毕了。"
     "在整个毕业设计的开发过程中，我完整经历了一个大型软件工程从需求分析、UML建模、数据库物理设计、代码编写到集成测试与容器化部署的全生命周期。"
     "系统实现了前后端 14 个核心功能模块、35 张高范式数据表及 25 个控制器，各模块运转稳定、性能优良。"
     "在项目完成的过程中，我得到了校内指导教师陈湘龙老师耐心细致的指导与解惑。"
     "在此向陈老师以及各位答辩评审老师致以最崇高的敬意与感谢！恳请各位老师批评指正，谢谢大家！"),
]


def tts(text: str, out_wav: str, voice="Microsoft Huihui Desktop", rate=-1) -> None:
    # 写入显式 UTF-8 文本文件，彻底解决 Console::In 默认代码页导致的中文乱码发音问题
    txt_path = out_wav + ".txt"
    with open(txt_path, "w", encoding="utf-8") as f:
        f.write(text)

    clean_txt_path = txt_path.replace("\\", "/")
    clean_out_wav = out_wav.replace("\\", "/")

    ps = f"""
Add-Type -AssemblyName System.Speech
$s = New-Object System.Speech.Synthesis.SpeechSynthesizer
$s.SelectVoice('{voice}')
$s.Rate = {rate}
$s.SetOutputToWaveFile('{clean_out_wav}')
$txt = [System.IO.File]::ReadAllText('{clean_txt_path}', [System.Text.Encoding]::UTF8)
$s.Speak($txt)
$s.Dispose()
"""
    script = os.path.join(AUDIO_DIR, "_tts_exec.ps1")
    with open(script, "w", encoding="utf-8-sig") as f:
        f.write(ps)
    r = subprocess.run(
        ["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", script],
        capture_output=True,
    )
    if r.returncode != 0:
        print("TTS stderr:", r.stderr.decode("gbk", errors="ignore"))
        raise RuntimeError(f"TTS failed for text: {text[:20]}...")


def get_duration(path: str) -> float:
    r = subprocess.run(
        [FFPROBE, "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", path],
        capture_output=True,
        text=True,
    )
    return float(r.stdout.strip())


def render_segments() -> list[str]:
    seg_files = []
    print(f"=== 开始生成 {len(SCENES)} 幕高清音视频分段 ===")

    for i, (frame_name, narration) in enumerate(SCENES, start=1):
        frame_path = os.path.join(FRAMES_DIR, frame_name)
        if not os.path.exists(frame_path):
            raise FileNotFoundError(f"画面文件不存在: {frame_path}")

        wav_path = os.path.join(AUDIO_DIR, f"scene_{i:02d}.wav")
        # 重新生成以保证内容最新
        print(f"[{i:02d}/{len(SCENES):02d}] 合成语音: {narration[:24]}...")
        tts(narration, wav_path)

        dur = get_duration(wav_path)
        # 前后各预留 0.45s 呼吸时间
        seg_dur = dur + 0.90
        seg_mp4 = os.path.join(SEGS_DIR, f"seg_{i:02d}.mp4")

        print(f"      编码分段 {seg_mp4} (时长: {seg_dur:.2f}s)...")
        # 旁白必须用 adelay 烧录进该段，再 apad 填满
        cmd = [
            FFMPEG, "-y",
            "-loop", "1", "-i", frame_path,
            "-i", wav_path,
            "-af", "adelay=450|450,apad,atrim=0:" + f"{seg_dur:.3f}",
            "-t", f"{seg_dur:.3f}",
            "-c:v", "libx264", "-preset", "medium", "-crf", "18",
            "-pix_fmt", "yuv420p", "-r", "30",
            "-c:a", "aac", "-b:a", "192k",
            "-shortest",
            seg_mp4
        ]
        r = subprocess.run(cmd, capture_output=True)
        if r.returncode != 0:
            print("FFmpeg error:", r.stderr.decode("gbk", errors="ignore"))
            raise RuntimeError(f"FFmpeg failed on segment {i}")

        seg_files.append(seg_mp4)

    return seg_files


def concat_video(seg_files: list[str]) -> str:
    print("=== 开始无损拼接全片视频 ===")
    list_txt = os.path.join(SEGS_DIR, "concat_list.txt")
    with open(list_txt, "w", encoding="utf-8") as f:
        for p in seg_files:
            # Windows 路径转 ffmpeg 格式
            clean_path = p.replace("\\", "/")
            f.write(f"file '{clean_path}'\n")

    final_mp4 = os.path.join(OUT_DIR, "HHS智能健康监测平台-项目介绍演示.mp4")
    cmd = [
        FFMPEG, "-y",
        "-f", "concat",
        "-safe", "0",
        "-i", list_txt,
        "-c", "copy",
        final_mp4
    ]
    subprocess.run(cmd, check=True, capture_output=True)
    print(f"Final MP4 generated successfully at: {final_mp4}")

    # 验证音视频质量
    dur = get_duration(final_mp4)
    print(f"全片总时长: {dur / 60:.1f} 分钟 ({dur:.2f} 秒)")

    # 检查音频响度
    vol_cmd = [FFMPEG, "-i", final_mp4, "-af", "volumedetect", "-f", "null", "-"]
    vr = subprocess.run(vol_cmd, capture_output=True, text=True)
    for line in vr.stderr.splitlines():
        if "mean_volume" in line or "max_volume" in line:
            print("  Audio check:", line.strip())

    return final_mp4


if __name__ == "__main__":
    segs = render_segments()
    concat_video(segs)
