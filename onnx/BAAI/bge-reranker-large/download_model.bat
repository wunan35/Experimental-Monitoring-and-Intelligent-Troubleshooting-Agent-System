@echo off
chcp 65001 >/dev/null
echo ============================================
echo BGE-Reranker-large 模型下载脚本 (Windows)
echo ============================================
echo.

REM 检查 Python 是否安装
python --version >/dev/null 2>&1
if errorlevel 1 (
    echo 错误: 未找到 Python
    echo 请先安装 Python: https://www.python.org/downloads/
    pause
    exit /b 1
)

echo 检测到 Python 已安装
echo.

REM 运行 Python 下载脚本
python download_model.py

if errorlevel 1 (
    echo.
    echo 下载失败，请检查错误信息
) else (
    echo.
    echo 下载完成！
)

pause
