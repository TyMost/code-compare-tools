# 自动化构建和打包脚本

## 功能说明

这个 Python 脚本 `build_and_package.py` 可以自动化完成以下任务：

1. **前端构建**：切换到 `frontend` 目录并执行 `npm run build`
2. **文件打包**：将生成的 `dist` 目录、`backend/src` 和 `backend/pom.xml` 合并打包
3. **压缩格式**：优先使用 7z 格式，如果失败则使用 ZIP 格式
4. **输出重命名**：将最终文件重命名为时间戳格式（如 `20231203_153600.png`）

## 使用方法

### 方法一：直接运行 Python 脚本

```bash
cd D:\Coding\code-compare-tools\output
python build_and_package.py
```

### 方法二：使用 Python 命令指定完整路径

```bash
python D:\Coding\code-compare-tools\output\build_and_package.py
```

## 系统要求

- Python 3.6+
- Node.js 和 npm（用于前端构建）
- 7-Zip 命令行工具（可选，脚本会自动检测并使用备选方案）

## 安装 7-Zip（可选）

虽然脚本在 7z 不可用时会自动使用 Python zipfile，但如果您希望使用真正的 7z 压缩以获得更好的压缩率，可以安装 7-Zip：

### 方法一：使用提供的 PowerShell 脚本（推荐）
```powershell
# 在 PowerShell 中运行
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
D:\Coding\code-compare-tools\output\install_7z.ps1
```

### 方法二：手动安装
1. 访问 [7-Zip 官网](https://www.7-zip.org/)
2. 下载 64 位版本：https://www.7-zip.org/a/7z2301-x64.exe
3. 运行安装程序
4. 确保 `C:\Program Files\7-Zip` 已添加到系统 PATH 环境变量

### 方法三：使用包管理器
```powershell
# 使用 Chocolatey
choco install 7zip

# 使用 Scoop
scoop install 7zip
```

## 输出文件

- 脚本执行成功后，会在 `D:\Coding\code-compare-tools\output` 目录下生成一个以时间戳命名的文件
- 文件格式为 `[YYYYMMDD_HHMMSS].png`（虽然是 .png 扩展名，但实际是压缩文件）

## 注意事项

1. 确保所有必要的目录和文件存在：
   - `frontend/package.json`
   - `backend/src` 目录
   - `backend/pom.xml` 文件

2. 如果 7z 命令行工具不可用，脚本会自动使用 Python 的 zipfile 模块作为备选方案

3. 脚本会创建临时目录并在完成后自动清理

## 错误处理

脚本包含完善的错误处理机制：
- 检查必要文件和目录是否存在
- 验证构建是否成功
- 自动清理临时文件
- 详细的错误信息输出

## 示例输出

```
🚀 开始自动化构建和打包流程
📂 项目根目录: D:\Coding\code-compare-tools
📂 输出目录: D:\Coding\code-compare-tools\output

==================================================
🔨 开始构建前端项目...
==================================================
✅ 前端构建完成

==================================================
📦 开始创建压缩包...
==================================================
✅ 压缩包创建成功
✅ 重命名为: D:\Coding\code-compare-tools\output\20231203_153600.png

==================================================
🎉 所有任务完成！
==================================================
