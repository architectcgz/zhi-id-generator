#!/bin/bash

# ID Generator Maven仓库发布脚本
# 用法: ./deploy.sh [release|snapshot]

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

cd "$PROJECT_DIR"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}ID Generator Maven仓库发布${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# 检查参数
DEPLOY_TYPE=${1:-release}

if [ "$DEPLOY_TYPE" != "release" ] && [ "$DEPLOY_TYPE" != "snapshot" ]; then
    echo -e "${RED}错误: 无效的发布类型 '$DEPLOY_TYPE'${NC}"
    echo "用法: $0 [release|snapshot]"
    exit 1
fi

echo -e "${YELLOW}发布类型: $DEPLOY_TYPE${NC}"
echo ""

# 检查当前版本
CURRENT_VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)
echo -e "当前版本: ${GREEN}$CURRENT_VERSION${NC}"

if [ "$DEPLOY_TYPE" = "snapshot" ]; then
    if [[ ! "$CURRENT_VERSION" =~ -SNAPSHOT$ ]]; then
        echo -e "${RED}错误: Snapshot发布需要版本号以-SNAPSHOT结尾${NC}"
        echo "当前版本: $CURRENT_VERSION"
        exit 1
    fi
else
    if [[ "$CURRENT_VERSION" =~ -SNAPSHOT$ ]]; then
        echo -e "${RED}错误: Release发布不能使用SNAPSHOT版本${NC}"
        echo "当前版本: $CURRENT_VERSION"
        exit 1
    fi
fi

echo ""
echo -e "${YELLOW}步骤1: 清理项目${NC}"
mvn clean

echo ""
echo -e "${YELLOW}步骤2: 运行测试${NC}"
mvn test

echo ""
echo -e "${YELLOW}步骤3: 编译打包${NC}"
mvn package -DskipTests

echo ""
echo -e "${YELLOW}步骤4: 发布到Maven仓库${NC}"

if [ "$DEPLOY_TYPE" = "release" ]; then
    # Release发布（可能需要GPG签名）
    read -p "是否需要GPG签名? (y/n): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        mvn deploy -P release
    else
        mvn deploy
    fi
else
    # Snapshot发布
    mvn deploy
fi

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}发布成功!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo "已发布的模块:"
echo "  - com.platform:id-generator-client:$CURRENT_VERSION"
echo "  - com.platform:id-generator-spring-boot-starter:$CURRENT_VERSION"
echo "  - com.platform:id-generator-server:$CURRENT_VERSION"
echo ""
echo "其他项目可以通过以下方式使用:"
echo ""
echo "<dependency>"
echo "    <groupId>com.platform</groupId>"
echo "    <artifactId>id-generator-spring-boot-starter</artifactId>"
echo "    <version>$CURRENT_VERSION</version>"
echo "</dependency>"
echo ""
