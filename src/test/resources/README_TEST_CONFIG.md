# 测试环境配置说明

## 配置文件位置

- **测试配置**: `src/test/resources/application.properties`
- **测试 MCP 配置**: `src/test/resources/mcp-config-test.jsonc`
- **测试数据**: `src/test/resources/data.sql`

## 环境变量设置

测试需要从环境变量读取以下配置：

### 必需配置

```bash
# Notion API Token (必需 - 用于 Notion MCP 测试)
export NOTION_MCP_TOKEN=ntn_your_token_here
```

### 可选配置

```bash
# PaddleOCR 配置（如果需要测试 OCR 功能）
export PADDLEOCR_MCP_AISTUDIO_ACCESS_TOKEN=your_token_here
export PADDLEOCR_MCP_PIPELINE=PP-StructureV3
export PADDLEOCR_MCP_PPOCR_SOURCE=aistudio
```

## Windows PowerShell 设置环境变量

```powershell
# 设置 Notion token
$env:NOTION_MCP_TOKEN = "ntn_your_token_here"

# 设置 PaddleOCR token (可选)
$env:PADDLEOCR_MCP_AISTUDIO_ACCESS_TOKEN = "your_token_here"
```

## Linux/macOS Bash 设置环境变量

```bash
# 设置 Notion token
export NOTION_MCP_TOKEN="ntn_your_token_here"

# 设置 PaddleOCR token (可选)
export PADDLEOCR_MCP_AISTUDIO_ACCESS_TOKEN="your_token_here"
```

## IDE 中设置环境变量

### IntelliJ IDEA

1. 打开 Run/Debug Configurations
2. 选择测试配置
3. 在 "Environment variables" 中添加：
   ```
   NOTION_MCP_TOKEN=ntn_your_token_here
   ```

### VS Code

在 `.vscode/settings.json` 中添加：

```json
{
  "java.test.config": {
    "env": {
      "NOTION_MCP_TOKEN": "ntn_your_token_here"
    }
  }
}
```

## 运行测试

### Maven 命令行

```bash
# Windows PowerShell
$env:NOTION_MCP_TOKEN = "ntn_your_token_here"
mvn test

# Linux/macOS
NOTION_MCP_TOKEN=ntn_your_token_here mvn test
```

### 使用测试脚本

```bash
# Windows
.\run-notion-tests.bat

# Linux/macOS
./run-notion-tests.sh
```

## 测试配置特点

1. **独立测试数据库**: 使用 `learning_agent_test.db`，不影响开发数据库
2. **自动数据初始化**: 每次测试前重新创建表和数据
3. **测试用户数据**: 自动加载预置的测试用户
4. **外部配置**: Token 从环境变量读取，不硬编码在代码中
5. **测试专用 MCP 配置**: 使用 `mcp-config-test.jsonc`

## 安全提示

⚠️ **重要**: 
- 不要将 token 提交到代码库
- 使用环境变量或密钥管理工具
- 测试配置文件只包含配置结构，不包含实际 token

## 故障排除

### 测试启动失败

如果看到 `Could not resolve placeholder 'notion.mcp.token'` 错误：

1. 确认环境变量已设置：
   ```bash
   echo $NOTION_MCP_TOKEN  # Linux/macOS
   echo $env:NOTION_MCP_TOKEN  # Windows PowerShell
   ```

2. 检查环境变量是否在测试进程中可见

3. 如果不需要运行 Notion 测试，token 可以为空，相关测试会被跳过

### MCP 连接失败

1. 确认 Node.js 和 npx 已安装
2. 测试网络连接（可能需要下载 @notionhq/notion-mcp-server）
3. 查看日志中的 MCP stderr 输出
