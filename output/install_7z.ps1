# PowerShell 脚本：安装 7-Zip
# 用于解决 '7z' 不是内部或外部命令的问题

Write-Host "🔧 开始安装 7-Zip..." -ForegroundColor Green

# 检查是否已安装 7-Zip
try {
    $7zPath = Get-Command "7z" -ErrorAction Stop
    Write-Host "✅ 7-Zip 已安装，路径: $($7zPath.Source)" -ForegroundColor Green
    exit 0
} catch {
    Write-Host "⚠️ 7-Zip 未安装，开始下载和安装..." -ForegroundColor Yellow
}

# 下载 URL（7-Zip 官方下载链接）
$downloadUrl = "https://www.7-zip.org/a/7z2301-x64.exe"
$installerPath = "$env:TEMP\7z-installer.exe"

Write-Host "📥 下载 7-Zip 安装程序..."
try {
    Invoke-WebRequest -Uri $downloadUrl -OutFile $installerPath -UseBasicParsing
    Write-Host "✅ 下载完成: $installerPath" -ForegroundColor Green
} catch {
    Write-Host "❌ 下载失败: $_" -ForegroundColor Red
    exit 1
}

# 静默安装 7-Zip
Write-Host "📦 安装 7-Zip..."
try {
    $process = Start-Process -FilePath $installerPath -ArgumentList "/S" -Wait -PassThru
    if ($process.ExitCode -eq 0) {
        Write-Host "✅ 7-Zip 安装成功" -ForegroundColor Green
    } else {
        Write-Host "❌ 7-Zip 安装失败，退出码: $($process.ExitCode)" -ForegroundColor Red
        exit 1
    }
} catch {
    Write-Host "❌ 安装过程出错: $_" -ForegroundColor Red
    exit 1
}

# 清理安装文件
try {
    Remove-Item $installerPath -Force
    Write-Host "🧹 清理安装文件完成" -ForegroundColor Green
} catch {
    Write-Host "⚠️ 清理安装文件失败: $_" -ForegroundColor Yellow
}

# 将 7-Zip 添加到系统 PATH（如果还没有）
$7zInstallPath = "${env:ProgramFiles}\7-Zip"
if ($env:PATH -notlike [string]::Format("*{0}*", $7zInstallPath)) {
    Write-Host "🔗 将 7-Zip 添加到系统 PATH..."
    try {
        [Environment]::SetEnvironmentVariable("PATH", $env:PATH + ";" + $7zInstallPath, "User")
        Write-Host "✅ PATH 环境变量已更新" -ForegroundColor Green
        Write-Host "⚠️ 请重新启动 PowerShell 或命令提示符以使 PATH 生效" -ForegroundColor Yellow
    } catch {
        Write-Host "⚠️ 更新 PATH 失败，请手动将 '$7zInstallPath' 添加到系统 PATH" -ForegroundColor Yellow
    }
}

Write-Host "🎉 7-Zip 安装完成！" -ForegroundColor Green
Write-Host "💡 现在可以重新运行 build_and_package.py 脚本来使用真正的 7z 压缩" -ForegroundColor Cyan
