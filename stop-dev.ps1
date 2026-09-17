# 一键停止本地开发环境脚本
Write-Host "=== 正在停止前端与后端本地服务 ===" -ForegroundColor Cyan

# 停止 8083 后端
$backendConns = Get-NetTCPConnection -LocalPort 8083 -State Listen -ErrorAction SilentlyContinue
foreach ($conn in $backendConns) {
    if ($conn.OwningProcess -gt 0) {
        Write-Host "正在停止后端进程 PID: $($conn.OwningProcess)" -ForegroundColor Yellow
        Stop-Process -Id $conn.OwningProcess -Force -ErrorAction SilentlyContinue
    }
}

# 停止 5173 前端
$frontendConns = Get-NetTCPConnection -LocalPort 5173 -State Listen -ErrorAction SilentlyContinue
foreach ($conn in $frontendConns) {
    if ($conn.OwningProcess -gt 0) {
        Write-Host "正在停止前端进程 PID: $($conn.OwningProcess)" -ForegroundColor Yellow
        Stop-Process -Id $conn.OwningProcess -Force -ErrorAction SilentlyContinue
    }
}

Write-Host "本地前端和后端服务已停止。" -ForegroundColor Green
Write-Host "注：Docker 数据库容器（MySQL、Redis、Qdrant）仍保持运行。如需关闭 Docker 容器，可运行：docker compose stop" -ForegroundColor Gray
