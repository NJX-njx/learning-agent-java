# Learning Agent Java Backend

> 基于 Spring Boot 和 LangChain4j 的智能学习助手后端服务，支持 MCP (Model Context Protocol) 集成，实现文档 OCR、知识管理和智能分析功能。

## 📋 目录

- [技术栈](#技术栈)
- [项目结构](#项目结构)
- [快速开始](#快速开始)
- [配置说明](#配置说明)
- [API 文档](#api-文档)
- [MCP 集成](#mcp-集成)
- [开发指南](#开发指南)

## 🛠️ 技术栈

| 技术 | 版本 | 说明 |
|-----|------|------|
| Java | 21+ | JDK 运行环境 |
| Spring Boot | 3.4.1 | Web 框架 |
| LangChain4j | 0.36.2 | AI 应用开发框架 |
| Maven | 3.6+ | 构建工具 |
| SQLite | 3.x | 轻量级数据库 |
| Lombok | 1.18.36 | 简化 Java 代码 |

### AI 模型支持

- **文心一言** (默认): 百度 AI Studio 的 ERNIE 系列模型
  - `ernie-4.5-turbo-vl` (支持图像理解)
  - `ernie-4.5-8k` / `ernie-4.0-turbo-8k` / `ernie-3.5-8k`
- **ReAct 模式**: 适配不支持标准 Function Calling 的模型

### MCP 服务集成

- **Notion MCP**: 知识管理，支持页面创建、更新、检索
- **PaddleOCR MCP**: 文档 OCR 解析，支持表格、公式、版面分析

## 📁 项目结构

```
backend-java/
├── pom.xml                          # Maven 项目配置
├── .env.example                     # 环境变量配置模板
├── mcp-config.jsonc                 # MCP 服务器配置
├── README.md                        # 项目文档
│
├── data/                            # 数据目录
│   └── learning_agent.db            # SQLite 数据库
│
├── logs/                            # 日志目录
├── uploads/                         # 文件上传目录
│
└── src/
    ├── main/
    │   ├── java/com/learning/agent/
    │   │   ├── LearningAgentApplication.java    # 应用入口
    │   │   │
    │   │   ├── controller/                      # REST 控制器
    │   │   │   ├── AuthController.java          # 认证接口
    │   │   │   └── AnalyzeController.java       # 分析接口
    │   │   │
    │   │   ├── service/                         # 业务服务
    │   │   │   ├── AuthService.java             # 认证服务
    │   │   │   └── AnalyzeService.java          # 分析服务
    │   │   │
    │   │   ├── config/                          # 配置层
    │   │   │   ├── WebConfig.java               # Web 配置（CORS、文件上传）
    │   │   │   ├── client/                      # 客户端配置
    │   │   │   └── storage/                     # 存储配置
    │   │   │
    │   │   ├── client/                          # 外部客户端
    │   │   │   ├── NotionClient.java            # Notion 客户端 (HTTP)
    │   │   │   ├── NotionMcpClient.java         # Notion MCP 客户端
    │   │   │   ├── NotionTools.java             # Notion 工具定义
    │   │   │   ├── PaddleOcrClient.java         # PaddleOCR 客户端
    │   │   │   └── PaddleOcrMcpClient.java      # PaddleOCR MCP 客户端
    │   │   │
    │   │   ├── workflow/                        # 工作流引擎 (类似 LangGraph)
    │   │   │   ├── AgentState.java              # Agent 状态
    │   │   │   ├── AgentWorkflow.java           # 工作流编排
    │   │   │   ├── ReactExecutor.java           # ReAct 模式执行器
    │   │   │   ├── WorkflowNode.java            # 工作流节点接口
    │   │   │   └── WorkflowNodes.java           # 节点实现集合
    │   │   │
    │   │   ├── dto/                             # 数据传输对象
    │   │   │   ├── web/                         # Web API DTO
    │   │   │   │   ├── LoginRequest.java
    │   │   │   │   ├── RegisterRequest.java
    │   │   │   │   ├── AuthResponse.java
    │   │   │   │   └── AnalyzeResponse.java
    │   │   │   └── client/                      # 外部客户端 DTO
    │   │   │       ├── NotionCreatedPage.java
    │   │   │       ├── NotionWritePayload.java
    │   │   │       ├── OcrStructuredResult.java
    │   │   │       └── OcrTextSpan.java
    │   │   │
    │   │   ├── model/                           # 领域模型
    │   │   │   ├── LearnerProfile.java          # 学习者画像
    │   │   │   ├── LearningTask.java            # 学习任务
    │   │   │   └── LearningTaskType.java        # 任务类型枚举
    │   │   │
    │   │   ├── entity/                          # 实体类
    │   │   │   └── User.java                    # 用户实体
    │   │   │
    │   │   ├── repository/                      # 数据仓库
    │   │   │   └── UserRepository.java          # 用户仓库
    │   │   │
    │   │   ├── util/                            # 工具类
    │   │   │   ├── ApiDiagnostic.java           # API 诊断
    │   │   │   ├── FeedbackLoopManager.java     # 反馈循环管理
    │   │   │   ├── McpConfigLoader.java         # MCP 配置加载
    │   │   │   └── ToolCallDiagnostic.java      # 工具调用诊断
    │   │   │
    │   │   └── exception/                       # 异常处理
    │   │       └── GlobalExceptionHandler.java  # 全局异常处理器
    │   │
    │   └── resources/
    │       ├── application.properties           # 应用配置
    │       ├── schema.sql                       # 数据库表结构
    │       └── static/                          # 静态资源
    │
    └── test/                                    # 测试代码
        ├── java/com/learning/agent/
        │   ├── TestUsers.java                   # 测试用户数据
        │   ├── client/                          # 客户端测试
        │   │   ├── NotionMcpClientTest.java     # Notion MCP 客户端单元测试
        │   │   ├── NotionMcpClientComprehensiveTest.java  # 综合测试
        │   │   └── NotionToolsTest.java         # Notion 工具测试
        │   ├── config/                          # 配置测试
        │   ├── controller/                      # 控制器测试
        │   ├── integration/                     # 集成测试
        │   └── workflow/                        # 工作流测试
        └── resources/
            ├── application.properties           # 测试环境配置
            ├── data.sql                         # 测试数据初始化脚本
            ├── mcp-config-test.jsonc            # MCP 测试配置
            └── README_TEST_CONFIG.md            # 测试配置说明
```

## 🚀 快速开始

### 前置要求

1. **JDK 21+** - [下载地址](https://adoptium.net/)
2. **Maven 3.6+** - [安装指南](https://maven.apache.org/install.html)
3. **Node.js** (用于 Notion MCP) - [下载地址](https://nodejs.org/)
4. **Conda** (用于 PaddleOCR MCP) - [安装指南](https://docs.conda.io/en/latest/miniconda.html)

### 第一步：克隆项目

```bash
git clone <repository-url>
cd learning-agent/backend-java
```

### 第二步：配置环境变量

```bash
# 复制环境变量模板
cp .env.example .env

# 编辑 .env 文件，填入实际配置
nano .env  # 或使用你喜欢的编辑器
```

**必需配置**：
- `WENXIN_API_KEY`: 文心一言 API Key ([获取方式](#1-文心一言-api-配置))
- `NOTION_MCP_TOKEN`: Notion Integration Secret ([获取方式](#2-notion-mcp-配置))
- `PADDLEOCR_MCP_SERVER_URL`: PaddleOCR 服务地址 ([获取方式](#3-paddleocr-mcp-配置))
- `PADDLEOCR_MCP_AISTUDIO_ACCESS_TOKEN`: AI Studio 访问令牌

### 第三步：安装 MCP 服务

#### 安装 Notion MCP

```bash
# 使用 npx 自动安装（推荐）
npx -y @notionhq/notion-mcp-server --version
```

#### 安装 PaddleOCR MCP

```bash
# 1. 创建 conda 环境
conda create -n paddle-agent python=3.10 -y

# 2. 激活环境
conda activate paddle-agent

# 3. 安装 paddleocr-mcp
pip install paddleocr-mcp

# 4. 验证安装
paddleocr_mcp --version
```

### 第四步：运行项目

```bash
# 开发模式（热重载）
mvn spring-boot:run

# 或构建后运行
mvn clean package
java -jar target/learning-agent-1.0.0-SNAPSHOT.jar
```

服务启动后访问：**http://localhost:3001/api/health**

## ⚙️ 配置说明

### 1. 文心一言 API 配置

#### 获取 Access Token

1. 访问 [百度 AI Studio](https://aistudio.baidu.com/)，登录百度账号
2. 点击右上角头像 → **访问令牌**，或访问 [这里](https://aistudio.baidu.com/account/accessToken)
3. 点击「查看」，复制 Access Token

#### 配置环境变量

```bash
# .env 文件
WENXIN_API_KEY=your_access_token_here
WENXIN_BASE_URL=https://aistudio.baidu.com/llm/lmapi/v3
WENXIN_MODEL=ernie-4.5-turbo-vl
```

#### 支持的模型

| 模型 | 特性 | 适用场景 |
|-----|------|---------|
| `ernie-4.5-turbo-vl` | 支持图像理解（默认） | 图文混合分析 |
| `ernie-4.5-8k` | 8K 上下文 | 长文本处理 |
| `ernie-4.0-turbo-8k` | 高性能 | 通用任务 |
| `ernie-3.5-8k` | 经济实惠 | 基础任务 |

更多模型详见 [AI Studio 文档](https://aistudio.baidu.com/llm/lmapi)。

### 2. Notion MCP 配置

#### 创建 Notion Integration

1. 访问 [Notion Integrations](https://www.notion.so/my-integrations)
2. 点击「New integration」，填写信息并创建
3. 复制 **Internal Integration Secret**（以 `secret_` 开头）
4. 在 Notion 中，打开要访问的页面，点击右上角「⋯」→「Connections」→ 添加你的 Integration

#### 配置环境变量

```bash
# .env 文件
NOTION_MCP_TOKEN=secret_your_notion_token_here
NOTION_MCP_VERSION=2022-06-28
```

#### MCP 配置文件

编辑 `mcp-config.jsonc`，根据操作系统调整：

**Windows:**
```jsonc
{
  "mcpServers": {
    "notion": {
      "command": "cmd.exe",
      "args": ["/c", "npx", "-y", "@notionhq/notion-mcp-server"],
      "env": {
        "NOTION_TOKEN": "${NOTION_MCP_TOKEN}"
      }
    }
  }
}
```

**Linux/macOS:**
```jsonc
{
  "mcpServers": {
    "notion": {
      "command": "npx",
      "args": ["-y", "@notionhq/notion-mcp-server"],
      "env": {
        "NOTION_TOKEN": "${NOTION_MCP_TOKEN}"
      }
    }
  }
}
```

### 3. PaddleOCR MCP 配置

#### 产线类型

| 产线 | 说明 | 输出格式 |
|-----|------|---------|
| `OCR` | 基础文字检测与识别 | 纯文本 |
| `PP-StructureV3` | 版面分析（推荐） | Markdown（支持表格、公式、图片） |
| `PaddleOCR-VL` | 多模态大模型文档解析 | 结构化 JSON |

#### 能力来源

| 模式 | 说明 | 配置要求 |
|-----|------|---------|
| `aistudio` | PaddleOCR 官网云服务（推荐） | 需要 `SERVER_URL` 和 `ACCESS_TOKEN` |
| `local` | 本地 Python 库运行 | 需要安装 PaddlePaddle 和 PaddleOCR |
| `qianfan` | 百度智能云千帆平台 | 需要千帆 API Key |
| `self_hosted` | 自托管服务 | 需要自建服务地址 |

#### aistudio 模式配置（推荐）

1. 访问 [PaddleOCR 任务页面](https://aistudio.baidu.com/paddleocr/task)
2. 点击「API」标签
3. 复制 **API_URL**（去掉末尾的 `/ocr` 等端点，只保留基础 URL）
   - 示例：`https://your-app.aistudio-app.com`
4. 复制 **TOKEN**

```bash
# .env 文件
PADDLEOCR_MCP_PIPELINE=PP-StructureV3
PADDLEOCR_MCP_PPOCR_SOURCE=aistudio
PADDLEOCR_MCP_SERVER_URL=https://your-app.aistudio-app.com
PADDLEOCR_MCP_AISTUDIO_ACCESS_TOKEN=your_token_here
PADDLEOCR_MCP_TIMEOUT=120
```

#### local 模式配置

```bash
# 1. 激活环境
conda activate paddle-agent

# 2. 安装依赖
pip install paddlepaddle paddleocr

# 3. 配置环境变量
PADDLEOCR_MCP_PIPELINE=PP-StructureV3
PADDLEOCR_MCP_PPOCR_SOURCE=local
# local 模式不需要 SERVER_URL 和 ACCESS_TOKEN
```

#### MCP 配置文件

编辑 `mcp-config.jsonc`：

**Windows:**
```jsonc
{
  "mcpServers": {
    "paddleocr": {
      "command": "cmd.exe",
      "args": ["/c", "conda", "run", "-n", "paddle-agent", "--no-capture-output", "paddleocr_mcp", "--verbose"],
      "env": {
        "PADDLEOCR_MCP_PIPELINE": "${PADDLEOCR_MCP_PIPELINE:PP-StructureV3}",
        "PADDLEOCR_MCP_PPOCR_SOURCE": "${PADDLEOCR_MCP_PPOCR_SOURCE:aistudio}",
        "PADDLEOCR_MCP_SERVER_URL": "${PADDLEOCR_MCP_SERVER_URL:}",
        "PADDLEOCR_MCP_AISTUDIO_ACCESS_TOKEN": "${PADDLEOCR_MCP_AISTUDIO_ACCESS_TOKEN:}",
        "PADDLEOCR_MCP_TIMEOUT": "${PADDLEOCR_MCP_TIMEOUT:120}"
      }
    }
  }
}
```

**Linux/macOS:**
```jsonc
{
  "mcpServers": {
    "paddleocr": {
      "command": "conda",
      "args": ["run", "-n", "paddle-agent", "--no-capture-output", "paddleocr_mcp", "--verbose"],
      "env": {
        "PADDLEOCR_MCP_PIPELINE": "${PADDLEOCR_MCP_PIPELINE:PP-StructureV3}",
        "PADDLEOCR_MCP_PPOCR_SOURCE": "${PADDLEOCR_MCP_PPOCR_SOURCE:aistudio}",
        "PADDLEOCR_MCP_SERVER_URL": "${PADDLEOCR_MCP_SERVER_URL:}",
        "PADDLEOCR_MCP_AISTUDIO_ACCESS_TOKEN": "${PADDLEOCR_MCP_AISTUDIO_ACCESS_TOKEN:}",
        "PADDLEOCR_MCP_TIMEOUT": "${PADDLEOCR_MCP_TIMEOUT:120}"
      }
    }
  }
}
```

详细文档：[PaddleOCR MCP Server](https://www.paddleocr.ai/main/version3.x/deployment/mcp_server.html)

### 4. Agent 执行配置

```bash
# ReAct 模式（适用于文心一言等不支持标准 Function Calling 的模型）
USE_REACT_MODE=true

# 标准 Function Calling（适用于 OpenAI、Claude 等）
USE_REACT_MODE=false
```

### 5. 其他配置

```bash
# MCP 配置文件路径
MCP_CONFIG_PATH=mcp-config.jsonc

# 服务端口（默认 3001）
SERVER_PORT=3001
```

完整配置模板请参考 `.env.example` 文件。

## 📡 API 文档

基础路径：`http://localhost:3001/api`

### 认证接口

#### 用户注册

```http
POST /register
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "your_password",
  "name": "Your Name"
}
```

**响应示例：**
```json
{
  "success": true,
  "user": {
    "id": "uuid",
    "email": "user@example.com",
    "name": "Your Name",
    "learnerId": "learner_id"
  }
}
```

#### 用户登录

```http
POST /login
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "your_password"
}
```

**响应示例：**
```json
{
  "success": true,
  "user": {
    "id": "uuid",
    "email": "user@example.com",
    "name": "Your Name",
    "learnerId": "learner_id"
  }
}
```

### 分析接口

#### 分析图片/文本

```http
POST /analyze
Content-Type: multipart/form-data

image: <file>           # 可选，图片文件（支持 PNG、JPG，最大 10MB）
message: <string>       # 可选，文本消息
profile: <json>         # 可选，学习者画像 JSON
learnerId: <string>     # 可选，学习者 ID
```

**示例（curl）：**
```bash
curl -X POST http://localhost:3001/api/analyze \
  -F "image=@/path/to/document.png" \
  -F "message=帮我分析这个文档" \
  -F "learnerId=learner_123"
```

**响应示例：**
```json
{
  "success": true,
  "data": {
    "extractedText": "文档内容...",
    "analysis": "根据分析结果...",
    "savedToNotion": true,
    "notionPageUrl": "https://notion.so/page-id"
  },
  "steps": [
    {
      "step": 1,
      "action": "OCR 文字识别",
      "status": "success"
    },
    {
      "step": 2,
      "action": "内容分析",
      "status": "success"
    }
  ]
}
```

### 健康检查

```http
GET /health
```

**响应：** `OK` (HTTP 200)

## 🔗 MCP 集成

本项目通过 Model Context Protocol (MCP) 集成外部能力。

### 可用的 MCP 工具

#### Notion MCP

| 工具 | 说明 | 参数 |
|-----|------|------|
| `notion_create_page` | 创建 Notion 页面 | `parent_id`, `title`, `content` |
| `notion_update_page` | 更新页面内容 | `page_id`, `content` |
| `notion_search` | 搜索页面 | `query` |
| `notion_get_page` | 获取页面详情 | `page_id` |

#### PaddleOCR MCP

| 工具 | 说明 | 参数 |
|-----|------|------|
| `paddleocr_analyze` | 文档 OCR 解析 | `image_url` or `image_base64` |
| `paddleocr_batch` | 批量解析 | `images[]` |

### 使用示例

```java
// 在 AgentWorkflow 中使用 MCP 工具
String result = notionMcpClient.createPage(
    parentId,
    "学习笔记",
    "# 今日学习内容\n\n..."
);

String ocrResult = paddleOcrMcpClient.analyze(imageFile);
```

### 自定义 MCP 服务

1. 编辑 `mcp-config.jsonc` 添加新服务
2. 创建对应的 Client 类（参考 `NotionMcpClient.java`）
3. 在 `WorkflowNodes.java` 中注册新工具
4. 在 `AgentWorkflow.java` 中使用

## 🔧 开发指南

### 项目导入

**IntelliJ IDEA:**
1. File → Open → 选择 `pom.xml`
2. 选择「Open as Project」
3. 等待 Maven 依赖下载完成

**Eclipse:**
1. File → Import → Existing Maven Projects
2. 选择项目目录
3. Finish

### 日志配置

默认日志级别（`application.properties`）：

```properties
# 应用日志
logging.level.com.learning=DEBUG

# LangChain4j 日志
logging.level.dev.langchain4j=DEBUG

# Spring 日志
logging.level.org.springframework.web=INFO
```

### 数据库管理

SQLite 数据库位于 `data/learning_agent.db`。

**查看数据库：**
```bash
# 使用 SQLite CLI
sqlite3 data/learning_agent.db

# 查看表结构
.schema users

# 查询数据
SELECT * FROM users;
```

**重置数据库：**
```bash
rm data/learning_agent.db
# 重启应用，将自动重建
```

### 运行测试

```bash
# 运行所有测试
mvn test

# 运行特定测试类
mvn test -Dtest=NotionMcpClientTest

# 跳过测试构建
mvn clean package -DskipTests
```

### 常见问题

#### 1. MCP 服务启动失败

**现象：** 日志中出现 `Failed to start MCP server`

**解决方案：**
- 检查 Node.js/Conda 是否正确安装
- 确认 `mcp-config.jsonc` 中的命令路径正确
- Windows 用户确保使用 `cmd.exe /c` 前缀

#### 2. 文心一言 API 调用失败

**现象：** `401 Unauthorized` 或 `Invalid API Key`

**解决方案：**
- 确认 `.env` 中的 `WENXIN_API_KEY` 正确
- 访问 [AI Studio](https://aistudio.baidu.com/account/accessToken) 重新获取 Token
- 检查 Token 是否过期或被撤销

#### 3. PaddleOCR aistudio 模式失败

**现象：** `Connection refused` 或 `Invalid token`

**解决方案：**
- 确认 `PADDLEOCR_MCP_SERVER_URL` 不包含端点路径（如 `/ocr`）
- 确认 `PADDLEOCR_MCP_AISTUDIO_ACCESS_TOKEN` 正确
- 在 [AI Studio](https://aistudio.baidu.com/paddleocr/task) 检查服务状态

#### 4. 文件上传大小限制

**现象：** `Maximum upload size exceeded`

**解决方案：**
- 调整 `application.properties` 中的限制：
  ```properties
  spring.servlet.multipart.max-file-size=50MB
  spring.servlet.multipart.max-request-size=50MB
  ```

### 贡献指南

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启 Pull Request

## 🔗 相关链接

- [LangChain4j 文档](https://docs.langchain4j.dev/)
- [Spring Boot 文档](https://spring.io/projects/spring-boot)
- [文心一言 API](https://aistudio.baidu.com/llm/lmapi)
- [Notion API](https://developers.notion.com/)
- [PaddleOCR MCP](https://www.paddleocr.ai/main/version3.x/deployment/mcp_server.html)
- [Model Context Protocol](https://modelcontextprotocol.io/)
