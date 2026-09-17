# 一键启动本地开发环境脚本
param([switch]$RebuildBackend)

$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot

Write-Host "=== 1. 检查并启动基础设施容器 (MySQL, Redis, Qdrant) ===" -ForegroundColor Cyan
& docker compose up -d mysql redis qdrant

Start-Sleep -Seconds 2

Write-Host "=== 2. 检查后端服务 (8083) ===" -ForegroundColor Cyan
$backendConn = Get-NetTCPConnection -LocalPort 8083 -State Listen -ErrorAction SilentlyContinue
if (-not $backendConn) {
    Write-Host "正在启动后端服务 (Spring Boot)..." -ForegroundColor Yellow
    $backendScript = Join-Path $projectRoot "runtime\dev-local\start-backend.ps1"
    $backendArgs = if ($RebuildBackend) { "" } else { "-SkipBuild" }
    Start-Process powershell -ArgumentList "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", "`"$backendScript`"", $backendArgs -WindowStyle Minimized
    Write-Host "后端启动进程已在后台运行。" -ForegroundColor Green
} else {
    Write-Host "后端服务已在端口 8083 运行。" -ForegroundColor Green
}

Write-Host "=== 3. 检查前端服务 (5173) ===" -ForegroundColor Cyan
$frontendConn = Get-NetTCPConnection -LocalPort 5173 -State Listen -ErrorAction SilentlyContinue
if (-not $frontendConn) {
    Write-Host "正在启动前端服务 (Vite)..." -ForegroundColor Yellow
    $frontendDir = Join-Path $projectRoot "frontend"
    Start-Process powershell -ArgumentList "-NoProfile", "-Command", "cd `"$frontendDir`"; npm run dev" -WindowStyle Minimized
    Write-Host "前端启动进程已在后台运行。" -ForegroundColor Green
} else {
    Write-Host "前端服务已在端口 5173 运行。" -ForegroundColor Green
}

Write-Host ""
Write-Host "==========================================" -ForegroundColor Green
Write-Host "系统服务状态就绪！" -ForegroundColor Green
Write-Host "前端访问地址: http://localhost:5173" -ForegroundColor Yellow
Write-Host "后端接口地址: http://localhost:8083" -ForegroundColor Yellow
Write-Host "后端健康检查: http://localhost:8083/api/health" -ForegroundColor Yellow
Write-Host "测试登录账号: admin / 123456" -ForegroundColor Yellow
Write-Host "==========================================" -ForegroundColor Green
