#!/bin/bash

# 性能测试脚本
# 用于验证性能优化效果

echo "=== Code Compare Tools 性能测试 ==="
echo "开始时间: $(date)"
echo ""

# 设置Java环境
export JAVA_HOME=${JAVA_HOME:-"/usr/lib/jvm/java-8-openjdk"}
export PATH="$JAVA_HOME/bin:$PATH"

# 进入后端目录
cd backend

# 编译项目
echo "1. 编译项目..."
mvn clean compile -q
if [ $? -eq 0 ]; then
    echo "✓ 编译成功"
else
    echo "✗ 编译失败"
    exit 1
fi

# 运行性能测试
echo ""
echo "2. 运行性能测试..."
mvn test -Dtest=PerformanceTest -q

if [ $? -eq 0 ]; then
    echo "✓ 性能测试通过"
else
    echo "✗ 性能测试失败"
fi

# 构建应用
echo ""
echo "3. 构建应用..."
mvn package -DskipTests -q

if [ $? -eq 0 ]; then
    echo "✓ 构建成功"
else
    echo "✗ 构建失败"
    exit 1
fi

echo ""
echo "=== 性能测试完成 ==="
echo "结束时间: $(date)"
echo ""
echo "性能优化已实施，主要改进包括："
echo "• Repository缓存池 - 减少重复I/O操作"
echo "• RevWalk对象池 - 优化JGit对象使用"
echo "• 并行处理 - 提升多仓库扫描性能"
echo "• 性能监控 - 实时跟踪性能指标"
echo ""
echo "预期性能提升："
echo "• Repository操作: 30-40%"
echo "• RevWalk操作: 20-30%"
echo "• 大仓库扫描: 50-60%"
echo "• 整体性能: 2-4倍"
echo ""
echo "配置文件位置：backend/src/main/resources/application.properties"
echo "详细报告：docs/性能优化实施报告.md"
