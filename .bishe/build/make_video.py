# -*- coding: utf-8 -*-
"""把每幕画面 + 中文旁白合成为可播放的 MP4。

流程：Huihui 语音合成 → 逐幕量出音频时长 → ffmpeg 逐幕编码
      → concat 拼接 → 混入统一音轨。
"""
from __future__ import annotations

import os
import subprocess
import sys
import wave

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
FRAMES = os.path.join(HERE, "video", "frames")
AUDIO = os.path.join(HERE, "video", "audio")
SEGS = os.path.join(HERE, "video", "segs")
OUT = os.path.join(ROOT, "out")

FFMPEG = "ffmpeg"
FFPROBE = "ffprobe"

# (帧文件, 旁白)
SCENES = [
    ("00_cover.png",
     "各位老师好，我是软件技术2401班的肖俊豪。"
     "我的毕业设计题目是 HHS 智能健康监测平台的设计与实现，"
     "负责用户健康管理端与智能预警模块。下面开始汇报。"),

    ("01_agenda.png",
     "汇报分为四个部分：选题背景与任务、设计思路与技术方案、"
     "系统实现与成果、以及测试与结论。"),

    ("02_problem.png",
     "先说为什么需要这样一个系统。现在手环记录睡眠、体检报告里有化验值、"
     "家里还有血压计，这些数据分散在不同平台，很难拼出一张完整的健康视图。"
     "而且普通人看到空腹血糖七点四，并不知道这意味着什么。"
     "两次体检之间的指标波动，更是完全处于盲区。"
     "所以我把系统的主线定为：统一采集、量化评估、智能预警、个性指导。"),

    ("03_modules.png",
     "项目面向普通用户和系统管理员两类角色，共划分十四个功能模块。"
     "本期重点完成其中七个：健康档案、数据录入、指标管理、健康评分、"
     "智能预警、AI 健康顾问，以及设备接入与同步。"),

    ("04_flow.png",
     "这是核心业务链路。用户用四种方式之一录入数据，经过校验和时间戳去重后写入数据库，"
     "然后发布一条领域事件。预警评估、评分刷新、积分发放三条支线异步监听这个事件，"
     "最后汇聚到前端展示。这里刻意用了异步事件而不是同步调用，"
     "目的是让录入请求不必等待分析结果，同时任一分析环节出问题也不会拖垮主流程。"),

    ("05_usecase.png",
     "从用例看，普通用户有九个核心用例，系统管理员有六个。"
     "其中录入健康数据这个用例，通过 include 复用了 AI 文本解析、"
     "OCR 识别和设备同步三个子用例，避免在模型里重复描述同类行为。"),

    ("06_arch.png",
     "技术方案上采用前后端分离。前端 Vue 3 加 TypeScript，共二十二个路由页面；"
     "后端 Spring Boot 三点二，二十五个控制器、三十七个服务接口；"
     "数据存 MySQL 8.0，缓存和限流用 Redis 7。整套用 Docker Compose 编排，"
     "一条命令即可部署。"),

    ("07_db.png",
     "数据库一共三十五张表，按职责分成五组。全部业务表都以 user_id 关联用户表，"
     "形成以用户为中心的一对多结构。实时指标表按月份做 Range 分区，"
     "用来支撑高频写入。另外统一使用逻辑删除、乐观锁和创建更新时间这几个审计字段。"),

    ("08_er.png",
     "这是数据库物理模型。指标表和预警表通过 metric_key 形成逻辑关联，"
     "这样同一个指标的历史阈值变更也能追溯。"),

    ("10_ui_input.png",
     "界面部分重点说录入。四种录入方式放在同一个入口，用标签切换。"
     "以 AI 文本解析为例，用户用日常语言描述指标，模型拆成结构化列表，"
     "但结果会先回显成表格，逐条核对之后才写入数据库。"
     "这一步人工确认，就是模型误判的兜底。"),

    ("11_ui_alert.png",
     "预警页按级别筛选，严重的排在前面。点开 AI 分析可以看到这次越界的原因和建议。"
     "同一个指标在短时间内反复越界，会被合并成一条并累计触发次数，"
     "避免短时间内反复推送造成告警疲劳。"),

    ("09_ui.png",
     "其余页面沿用统一的布局：左侧导航、顶部栏、内容区三段式。"
     "配色用低饱和的青绿作为主色，深色侧边栏做背景，配合圆角卡片，"
     "兼顾信息密度和可读性。"),

    ("11_class.png",
     "这是健康指标管理的业务类图。Controller 只依赖 Service 接口，"
     "ServiceImpl 实现接口并组合去重服务，通过 Mapper 访问数据，"
     "再通过事件发布器发出领域事件。这样分层之后，各协作者可以独立替换和测试。"),

    ("12_seq.png",
     "这是录入一次指标的完整协作。第一个关键动作是按时间窗查询后去重；"
     "第二个是写入之后立即发布事件，请求线程不再等待；"
     "第三个是监听器异步完成告警、评分和积分的评估。"),

    ("13_api.png",
     "接口层面做了统一约定：都以 api 为前缀，响应体统一封装成 Result，"
     "code 等于二百表示成功；除认证接口外都要带 Bearer 令牌；"
     "分页统一用 page 和 size。这样前后端可以并行开发。"),

    ("14_test.png",
     "测试方面，后端写了四十七个测试类，覆盖认证、档案、指标、预警、评分、"
     "设备同步六组核心逻辑；接口测试十二组，集成测试十二组。"
     "集成测试用 Testcontainers 起真实的 MySQL 和 Redis 容器，"
     "保证测试环境和生产一致。测试过程中修掉了三个比较关键的问题："
     "重复预警没有合并、设备回调没有校验 state、还有评分缓存没有及时失效。"),

    ("15_highlight.png",
     "回头看，我觉得有四个设计值得说：AI 不只是问答，而是融进了录入、预警、报告全流程；"
     "预警做成了完整的子系统，而不只是阈值判断；四种录入方式覆盖了不同场景；"
     "以及 WebSocket 加多渠道推送的全链路实时能力。"),

    ("16_conclusion.png",
     "系统已经完成了完整的架构和十四个模块，形成了从录入到预警到评分的闭环。"
     "但不足也很明确：指标的参考区间和评分权重还缺乏严格的医学验证，"
     "移动端适配不够完善，预警规则目前还是阈值型。"
     "这次最大的收获，是真正理解了能运行和可维护之间的距离。"),

    ("17_thanks.png",
     "我的汇报到此结束，感谢各位老师，恳请批评指正。"),
]


def tts(text: str, out_wav: str, voice="Microsoft Huihui Desktop",
        rate=-1) -> None:
    ps = f"""
Add-Type -AssemblyName System.Speech
$s = New-Object System.Speech.Synthesis.SpeechSynthesizer
$s.SelectVoice('{voice}')
$s.Rate = {rate}
$s.SetOutputToWaveFile('{out_wav}')
$s.Speak([Console]::In.ReadToEnd())
$s.Dispose()
"""
    script = os.path.join(AUDIO, "_tts.ps1")
    with open(script, "w", encoding="utf-8-sig") as f:
        f.write(ps)
    subprocess.run(
        ["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass",
         "-File", script],
        input=text.encode("utf-8"), check=True, capture_output=True)


def duration(path: str) -> float:
    r = subprocess.run(
        [FFPROBE, "-v", "error", "-show_entries", "format=duration",
         "-of", "csv=p=0", path], capture_output=True, text=True)
    return float(r.stdout.strip())


def build() -> str:
    for d in (AUDIO, SEGS):
        os.makedirs(d, exist_ok=True)
    os.makedirs(OUT, exist_ok=True)

    wavs, segs, total = [], [], 0.0
    for i, (frame, line) in enumerate(SCENES):
        fp = os.path.join(FRAMES, frame)
        if not os.path.exists(fp):
            raise SystemExit(f"缺少画面: {fp}")
        wav = os.path.join(AUDIO, f"{i:02d}.wav")
        if not os.path.exists(wav):
            tts(line, wav)
        dur = duration(wav)
        # 旁白前后各留 0.45s 呼吸
        seg_dur = dur + 0.9
        total += seg_dur

        seg = os.path.join(SEGS, f"{i:02d}.mp4")
        # 旁白延后 0.45s 进入，并用静音补足到整段时长
        subprocess.run([
            FFMPEG, "-y", "-loglevel", "error",
            "-loop", "1", "-i", fp,
            "-i", wav,
            "-t", f"{seg_dur:.3f}",
            "-vf", "scale=1920:1080,setsar=1,fade=t=in:st=0:d=0.35,"
                   f"fade=t=out:st={seg_dur - 0.35:.3f}:d=0.35",
            "-af", f"adelay=450|450,apad,atrim=0:{seg_dur:.3f}",
            "-c:v", "libx264", "-preset", "medium", "-crf", "20",
            "-pix_fmt", "yuv420p", "-r", "30",
            "-c:a", "aac", "-b:a", "160k", "-ar", "44100", "-ac", "1",
            "-shortest", seg,
        ], check=True)
        wavs.append(wav)
        segs.append(seg)
        print(f"  {i:02d} {frame:24s} {dur:5.1f}s -> seg {seg_dur:5.1f}s")

    # 拼接视频
    concat_txt = os.path.join(SEGS, "list.txt")
    with open(concat_txt, "w", encoding="utf-8") as f:
        for s in segs:
            f.write(f"file '{s.replace(os.sep, '/')}'\n")
    video_only = os.path.join(SEGS, "video_only.mp4")
    subprocess.run([FFMPEG, "-y", "-loglevel", "error", "-f", "concat",
                    "-safe", "0", "-i", concat_txt, "-c", "copy",
                    video_only], check=True)

    # 拼接旁白（每段前补 0.45s 静音，与画面时长对齐）
    aligned = []
    for i, wav in enumerate(wavs):
        a = os.path.join(AUDIO, f"aligned_{i:02d}.wav")
        seg_dur = duration(segs[i])
        subprocess.run([
            FFMPEG, "-y", "-loglevel", "error", "-i", wav,
            "-af", f"adelay=450|450,apad=pad_dur={seg_dur}",
            "-ar", "44100", "-ac", "1", "-t", f"{seg_dur:.3f}", a,
        ], check=True)
        aligned.append(a)

    atxt = os.path.join(AUDIO, "alist.txt")
    with open(atxt, "w", encoding="utf-8") as f:
        for a in aligned:
            f.write(f"file '{a.replace(os.sep, '/')}'\n")
    audio_all = os.path.join(AUDIO, "narration.wav")
    subprocess.run([FFMPEG, "-y", "-loglevel", "error", "-f", "concat",
                    "-safe", "0", "-i", atxt, "-c", "copy", audio_all],
                   check=True)

    final = os.path.join(OUT, "HHS智能健康监测平台-项目介绍演示.mp4")
    subprocess.run([
        FFMPEG, "-y", "-loglevel", "error",
        "-i", video_only, "-i", audio_all,
        "-c:v", "copy", "-c:a", "aac", "-b:a", "160k", "-shortest",
        "-movflags", "+faststart", final,
    ], check=True)

    print(f"\n总时长约 {total / 60:.1f} 分钟 → {final}")
    return final


if __name__ == "__main__":
    print(build())