# who.ps1 - 快速分清本机各端口上的服务属于哪个项目
#
# 为什么需要它：
#   1. netstat 只给 PID，不给项目；本机 node.exe 有十几个，进程名完全无法区分。
#   2. "mvn spring-boot:run" 会生成两个 java 进程：Maven 外壳 + fork 出的应用。
#      只有子进程监听端口，父进程不监听 —— 只看 java 进程数会误以为跑了两份。
#   3. Docker Desktop 发布端口时会被 com.docker.backend 和 wslrelay 双重持有，
#      跟应用无关。
#
# 用法：
#   powershell -NoProfile -ExecutionPolicy Bypass -File scripts\who.ps1

$ErrorActionPreference = 'SilentlyContinue'

function Resolve-Proj([string]$cmd) {
    if (-not $cmd) { return '未知' }
    if ($cmd -match 'health-hack-system')  { return 'HHS' }
    if ($cmd -match 'oaiss')               { return 'OAISS' }
    if ($cmd -match 'starmap')             { return 'starmap' }
    if ($cmd -match 'com\.docker\.backend' -or $cmd -match 'wslrelay') { return 'Docker' }
    return '其他'
}

function Resolve-Role([string]$cmd, [string]$procName) {
    if ($procName -eq 'com.docker.backend.exe' -or $procName -eq 'wslrelay.exe') {
        return 'Docker 端口转发'
    }
    if ($procName -eq 'node.exe') {
        if ($cmd -match 'vite') { return 'Vite 开发服务器' }
        if ($cmd -match 'next') { return 'Next.js' }
        return 'Node 进程'
    }
    if ($procName -eq 'java.exe') {
        if ($cmd -match 'spring-boot:run') { return 'Maven 外壳-不监听' }
        if ($cmd -match 'backend')         { return 'Spring Boot 应用' }
        return 'Java 进程'
    }
    return $procName
}

$procMap = @{}
Get-CimInstance Win32_Process | ForEach-Object { $procMap[[int]$_.ProcessId] = $_ }

$conns = @()
if (Get-Command Get-NetTCPConnection -ErrorAction SilentlyContinue) {
    $conns = Get-NetTCPConnection -State Listen
} else {
    # 兜底：旧系统用 netstat 解析
    $conns = netstat -ano | Select-String 'LISTENING' | ForEach-Object {
        $f = ($_ -split '\s+') | Where-Object { $_ }
        [PSCustomObject]@{ LocalPort = [int]($f[1] -replace '.*:', ''); OwningProcess = [int]$f[-1] }
    }
}

# 默认只显示能被识别出项目的"应用端口"；加 -All 看全部监听端口。
$showAll = $args -contains '-All'

$conns | Where-Object { $_.LocalPort -lt 20000 } |
    Select-Object -Property LocalPort, OwningProcess -Unique |
    ForEach-Object {
        $p    = $procMap[[int]$_.OwningProcess]
        $cmd  = if ($p) { $p.CommandLine } else { '' }
        $pnam = if ($p) { $p.Name }        else { '?' }
        [PSCustomObject]@{
            端口 = $_.LocalPort
            进程 = $_.OwningProcess
            项目 = Resolve-Proj $cmd
            服务 = Resolve-Role $cmd $pnam
        }
    } |
    Where-Object { $showAll -or $_.项目 -in @('HHS', 'OAISS', 'starmap', 'Docker') } |
    Sort-Object 项目, 端口 |
    Format-Table -AutoSize

Write-Host ''
Write-Host '识别要点：' -ForegroundColor Cyan
Write-Host '  * 按「项目」列聚合即可 —— 命令行里的仓库路径才是唯一可靠依据'
Write-Host '  * 「Maven 外壳-不监听」与「Spring Boot 应用」成对出现，只有后者占端口'
Write-Host '  * Docker 端口转发层与应用无关，实际服务看 docker ps'
Write-Host '  * Vite 端口会漂移，认标题不认端口：HHS 是「HHS - 健康管理系统」'
