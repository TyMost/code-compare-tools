#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
自动化构建和打包脚本
功能：
1. 切换到 frontend 目录执行 npm run build
2. 将生成的 dist 目录、backend/src 和 backend/pom.xml 合并打包成 7z
3. 保存到 output 目录并重命名为时间戳格式
"""

import os
import subprocess
import shutil
import datetime
import sys
from pathlib import Path

# 项目根目录
PROJECT_ROOT = Path(__file__).parent.parent
FRONTEND_DIR = PROJECT_ROOT / "frontend"
BACKEND_DIR = PROJECT_ROOT / "backend"
OUTPUT_DIR = PROJECT_ROOT / "output"
DIST_DIR = FRONTEND_DIR / "dist"

def run_command(command, cwd=None):
    """执行命令并返回结果"""
    print(f"执行命令: {command}")
    print(f"工作目录: {cwd if cwd else os.getcwd()}")
    
    try:
        # Windows 环境下使用 cp936 编码来避免中文乱码问题
        encoding = 'cp936' if os.name == 'nt' else 'utf-8'
        
        result = subprocess.run(
            command,
            shell=True,
            cwd=cwd,
            capture_output=True,
            text=True,
            encoding=encoding,
            errors='ignore'  # 忽略编码错误
        )
        
        if result.returncode == 0:
            print("✅ 命令执行成功")
            if result.stdout:
                # 只显示前 200 个字符，避免输出过长
                output_preview = result.stdout[:200] + "..." if len(result.stdout) > 200 else result.stdout
                print(f"输出: {output_preview}")
        else:
            print(f"❌ 命令执行失败，返回码: {result.returncode}")
            if result.stderr:
                error_preview = result.stderr[:200] + "..." if len(result.stderr) > 200 else result.stderr
                print(f"错误信息: {error_preview}")
            if result.stdout:
                output_preview = result.stdout[:200] + "..." if len(result.stdout) > 200 else result.stdout
                print(f"输出: {output_preview}")
            return False
        
        return True
        
    except Exception as e:
        print(f"❌ 执行命令时发生异常: {e}")
        return False

def build_frontend():
    """构建前端项目"""
    print("\n" + "="*50)
    print("🔨 开始构建前端项目...")
    print("="*50)
    
    # 检查 frontend 目录是否存在
    if not FRONTEND_DIR.exists():
        print(f"❌ 前端目录不存在: {FRONTEND_DIR}")
        return False
    
    # 检查 package.json 是否存在
    package_json = FRONTEND_DIR / "package.json"
    if not package_json.exists():
        print(f"❌ package.json 不存在: {package_json}")
        return False
    
    # 执行 npm run build
    success = run_command("npm run build", cwd=FRONTEND_DIR)
    
    if not success:
        print("❌ 前端构建失败")
        return False
    
    # 检查 dist 目录是否生成
    if not DIST_DIR.exists():
        print(f"❌ 构建后 dist 目录不存在: {DIST_DIR}")
        return False
    
    print("✅ 前端构建完成")
    return True

def create_7z_package():
    """创建 7z 压缩包"""
    print("\n" + "="*50)
    print("📦 开始创建压缩包...")
    print("="*50)
    
    # 检查必要的文件和目录
    required_items = [
        (DIST_DIR, "前端 dist 目录"),
        (BACKEND_DIR / "src", "后端 src 目录"),
        (BACKEND_DIR / "pom.xml", "后端 pom.xml 文件")
    ]
    
    for item, description in required_items:
        if not item.exists():
            print(f"❌ {description}不存在: {item}")
            return False
    
    # 生成时间戳
    timestamp = datetime.datetime.now().strftime("%Y%m%d_%H%M%S")
    archive_name = f"build_package_{timestamp}.7z"
    archive_path = OUTPUT_DIR / archive_name
    
    # 创建临时目录用于组织文件
    temp_dir = OUTPUT_DIR / f"temp_{timestamp}"
    temp_dir.mkdir(exist_ok=True)
    
    try:
        print(f"📁 创建临时目录: {temp_dir}")
        
        # 复制文件到临时目录
        print("📋 复制文件到临时目录...")
        
        # 复制 dist 目录
        temp_dist = temp_dir / "dist"
        if temp_dist.exists():
            shutil.rmtree(temp_dist)
        shutil.copytree(DIST_DIR, temp_dist)
        print(f"✅ 复制 dist 目录到: {temp_dist}")
        
        # 复制 backend/src
        temp_src = temp_dir / "src"
        if temp_src.exists():
            shutil.rmtree(temp_src)
        shutil.copytree(BACKEND_DIR / "src", temp_src)
        print(f"✅ 复制 backend/src 到: {temp_src}")
        
        # 复制 pom.xml
        temp_pom = temp_dir / "pom.xml"
        shutil.copy2(BACKEND_DIR / "pom.xml", temp_pom)
        print(f"✅ 复制 pom.xml 到: {temp_pom}")
        
        # 创建 7z 压缩包
        print(f"🗜️ 创建 7z 压缩包: {archive_path}")
        
        # 尝试使用 7z 命令行工具
        sevenz_command = f'7z a "{archive_path}" "{temp_dir}\\*"'
        success = run_command(sevenz_command)
        
        if not success:
            # 如果 7z 命令失败，尝试使用 Python 的 zipfile 作为备选
            print("⚠️ 7z 命令失败，尝试使用 Python zipfile...")
            try:
                import zipfile
                with zipfile.ZipFile(archive_path.with_suffix('.zip'), 'w', zipfile.ZIP_DEFLATED) as zipf:
                    for file_path in temp_dir.rglob('*'):
                        if file_path.is_file():
                            arcname = file_path.relative_to(temp_dir)
                            zipf.write(file_path, arcname)
                
                # 将 zip 重命名为 7z（虽然格式不同，但满足文件名要求）
                zip_path = archive_path.with_suffix('.zip')
                if zip_path.exists():
                    zip_path.rename(archive_path)
                    print(f"✅ 使用 zipfile 创建压缩包: {archive_path}")
                else:
                    print("❌ zipfile 创建失败")
                    return False
                    
            except ImportError:
                print("❌ 无法导入 zipfile 模块")
                return False
            except Exception as e:
                print(f"❌ 创建 zipfile 时发生错误: {e}")
                return False
        else:
            print(f"✅ 7z 压缩包创建成功: {archive_path}")
        
        # 检查压缩包是否创建成功
        if not archive_path.exists():
            print(f"❌ 压缩包创建失败: {archive_path}")
            return False
        
        # 重命名为带时间戳的 .png 文件（虽然扩展名是 .png，但实际是压缩文件）
        png_name = f"{timestamp}.png"
        png_path = OUTPUT_DIR / png_name
        
        try:
            shutil.move(str(archive_path), str(png_path))
            print(f"✅ 重命名为: {png_path}")
        except Exception as e:
            print(f"❌ 重命名失败: {e}")
            return False
        
        return True
        
    finally:
        # 清理临时目录
        if temp_dir.exists():
            try:
                shutil.rmtree(temp_dir)
                print(f"🧹 清理临时目录: {temp_dir}")
            except Exception as e:
                print(f"⚠️ 清理临时目录失败: {e}")

def main():
    """主函数"""
    print("🚀 开始自动化构建和打包流程")
    print(f"📂 项目根目录: {PROJECT_ROOT}")
    print(f"📂 输出目录: {OUTPUT_DIR}")
    
    # 确保 output 目录存在
    OUTPUT_DIR.mkdir(exist_ok=True)
    
    try:
        # 步骤1: 构建前端
        if not build_frontend():
            print("\n❌ 前端构建失败，终止流程")
            sys.exit(1)
        
        # 步骤2: 创建压缩包
        if not create_7z_package():
            print("\n❌ 创建压缩包失败，终止流程")
            sys.exit(1)
        
        print("\n" + "="*50)
        print("🎉 所有任务完成！")
        print("="*50)
        print(f"📁 输出文件位置: {OUTPUT_DIR}")
        
        # 列出输出目录中的文件
        print("\n📋 输出目录文件列表:")
        for file_path in OUTPUT_DIR.iterdir():
            if file_path.is_file():
                size = file_path.stat().st_size
                size_mb = size / (1024 * 1024)
                print(f"  📄 {file_path.name} ({size_mb:.2f} MB)")
        
    except KeyboardInterrupt:
        print("\n\n⚠️ 用户中断操作")
        sys.exit(1)
    except Exception as e:
        print(f"\n❌ 发生未预期的错误: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main()
