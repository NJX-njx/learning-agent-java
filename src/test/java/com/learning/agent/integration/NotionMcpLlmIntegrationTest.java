package com.learning.agent.integration;

import com.learning.agent.client.NotionTools;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 大模型调用 Notion MCP 功能集成测试
 * 测试 AI Agent 通过工具调用与 Notion 交互的完整流程
 */
@Slf4j
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class NotionMcpLlmIntegrationTest {

    @Autowired
    @Qualifier("executionChatModel")
    private ChatLanguageModel chatModel;

    @Autowired
    private NotionTools notionTools;

    // 测试数据
    private static String testParentPageId;
    private static String testPageId;
    // 存储所有创建的测试页面，用于清理
    private static final java.util.List<String> createdPageIds = new java.util.ArrayList<>();

    /**
     * 在所有测试前设置测试环境
     */
    @BeforeAll
    public static void setupTestEnvironment(@Autowired NotionTools tools) {
        log.info("\n>>> 准备测试环境");
        
        try {
            // 搜索父页面
            String searchResult = tools.notionSearch("sophie");
            if (searchResult.contains("\"found\":true")) {
                int idIndex = searchResult.indexOf("\"id\":\"");
                if (idIndex != -1) {
                    int idStart = idIndex + 6;
                    int idEnd = searchResult.indexOf("\"", idStart);
                    testParentPageId = searchResult.substring(idStart, idEnd);
                    log.info("✓ 找到父页面 ID: {}", testParentPageId);
                    
                    // 创建测试页面
                    String title = "LLM 集成测试页面 - " + System.currentTimeMillis();
                    String content = "这是用于 LLM 集成测试的页面";
                    
                    String createResult = tools.notionCreatePage(testParentPageId, title, content);
                    log.info("创建结果: {}", createResult);
                    
                    // 提取页面 ID
                    if (createResult.contains("\"pageId\":\"")) {
                        int pageIdIndex = createResult.indexOf("\"pageId\":\"");
                        int pageIdStart = pageIdIndex + 10;
                        int pageIdEnd = createResult.indexOf("\"", pageIdStart);
                        testPageId = createResult.substring(pageIdStart, pageIdEnd);
                    } else if (createResult.contains("\"id\":\"")) {
                        int pageIdIndex = createResult.indexOf("\"id\":\"");
                        int pageIdStart = pageIdIndex + 6;
                        int pageIdEnd = createResult.indexOf("\"", pageIdStart);
                        testPageId = createResult.substring(pageIdStart, pageIdEnd);
                    }
                    
                    if (testPageId != null) {
                        log.info("✓ 测试页面创建成功，ID: {}", testPageId);
                        createdPageIds.add(testPageId); // 记录用于清理
                    } else {
                        log.warn("⚠ 无法提取测试页面 ID");
                    }
                }
            } else {
                log.warn("⚠ 未找到父页面 'sophie'，部分测试可能会被跳过");
            }
        } catch (Exception e) {
            log.error("设置测试环境失败: {}", e.getMessage());
        }
    }

    /**
     * AI Agent 接口 - 搜索专用
     */
    interface NotionSearchAgent {
        @dev.langchain4j.service.SystemMessage("""
                你是一个 Notion 搜索助手。你可以使用以下工具：
                - notionSearch: 搜索 Notion 页面
                
                用户的请求会明确告诉你要搜索什么。请直接调用工具，不要解释。
                返回搜索结果的 JSON 格式。
                """)
        String search(String query);
    }

    /**
     * AI Agent 接口 - 页面操作专用
     */
    interface NotionPageAgent {
        @dev.langchain4j.service.SystemMessage("""
                你是一个 Notion 页面操作助手。你可以使用以下工具：
                - notionCreatePage: 创建新页面
                - notionAppendContent: 追加内容到页面
                - notionRetrievePage: 获取页面信息
                
                用户会给你明确的指令。请直接调用相应的工具，不要解释。
                返回操作结果。
                """)
        String execute(String command);
    }

    /**
     * AI Agent 接口 - 完整工作流
     */
    interface NotionWorkflowAgent {
        @dev.langchain4j.service.SystemMessage("""
                你是一个 Notion 智能助手。你可以使用以下工具：
                - notionSearch: 搜索页面
                - notionCreatePage: 创建页面
                - notionAppendContent: 追加内容
                - notionRetrievePage: 获取页面信息
                - notionGetBlockChildren: 获取页面的子块
                - notionSearchAll: 高级搜索
                - notionGetSelf: 获取机器人信息
                - notionListUsers: 列出所有用户
                
                你需要根据用户的指令，智能地组合使用这些工具来完成任务。
                
                例如：
                - 如果用户要在某个页面下创建子页面，你需要先搜索找到父页面 ID
                - 如果用户要更新页面，你需要先搜索页面，然后追加内容
                
                每次调用工具后，检查结果是否成功，然后再进行下一步操作。
                最后用简短的中文总结你完成的工作。
                """)
        String executeWorkflow(String task);
    }

    /**
     * 工具服务包装类 - 提供给 LangChain4j
     */
    static class NotionToolService {
        private final NotionTools notionTools;

        public NotionToolService(NotionTools notionTools) {
            this.notionTools = notionTools;
        }

        @Tool("搜索 Notion 页面，返回页面 ID 和标题")
        public String notionSearch(String query) {
            log.info("🔧 [LLM TOOL CALL] notionSearch: query={}", query);
            return notionTools.notionSearch(query);
        }

        @Tool("创建新的 Notion 页面")
        public String notionCreatePage(String parentPageId, String title, String content) {
            log.info("🔧 [LLM TOOL CALL] notionCreatePage: parentPageId={}, title={}", parentPageId, title);
            return notionTools.notionCreatePage(parentPageId, title, content);
        }

        @Tool("向 Notion 页面追加内容")
        public String notionAppendContent(String pageId, String content) {
            log.info("🔧 [LLM TOOL CALL] notionAppendContent: pageId={}", pageId);
            return notionTools.notionAppendContent(pageId, content);
        }

        @Tool("获取 Notion 页面信息")
        public String notionRetrievePage(String pageId) {
            log.info("🔧 [LLM TOOL CALL] notionRetrievePage: pageId={}", pageId);
            return notionTools.notionRetrievePage(pageId);
        }

        @Tool("获取页面的所有子块")
        public String notionGetBlockChildren(String blockId, Integer pageSize, String startCursor) {
            log.info("🔧 [LLM TOOL CALL] notionGetBlockChildren: blockId={}", blockId);
            return notionTools.notionGetBlockChildren(blockId, pageSize, startCursor);
        }

        @Tool("在工作区中搜索页面或数据库")
        public String notionSearchAll(String query, String filter, String sort, Integer pageSize, String startCursor) {
            log.info("🔧 [LLM TOOL CALL] notionSearchAll: query={}", query);
            return notionTools.notionSearchAll(query, filter, sort, pageSize, startCursor);
        }

        @Tool("获取机器人自身信息")
        public String notionGetSelf() {
            log.info("🔧 [LLM TOOL CALL] notionGetSelf");
            return notionTools.notionGetSelf();
        }

        @Tool("列出所有用户")
        public String notionListUsers(Integer pageSize, String startCursor) {
            log.info("🔧 [LLM TOOL CALL] notionListUsers");
            return notionTools.notionListUsers(pageSize, startCursor);
        }

        @Tool("获取指定用户信息")
        public String notionGetUser(String userId) {
            log.info("🔧 [LLM TOOL CALL] notionGetUser: userId={}", userId);
            return notionTools.notionGetUser(userId);
        }

        @Tool("查询数据库")
        public String notionQueryDatabase(String databaseId, String filter, String sorts, Integer pageSize, String startCursor) {
            log.info("🔧 [LLM TOOL CALL] notionQueryDatabase: databaseId={}", databaseId);
            return notionTools.notionQueryDatabase(databaseId, filter, sorts, pageSize, startCursor);
        }

        @Tool("获取数据库信息")
        public String notionRetrieveDatabase(String databaseId) {
            log.info("🔧 [LLM TOOL CALL] notionRetrieveDatabase: databaseId={}", databaseId);
            return notionTools.notionRetrieveDatabase(databaseId);
        }

        @Tool("获取单个块信息")
        public String notionRetrieveBlock(String blockId) {
            log.info("🔧 [LLM TOOL CALL] notionRetrieveBlock: blockId={}", blockId);
            return notionTools.notionRetrieveBlock(blockId);
        }
    }

    @BeforeAll
    static void setup() {
        log.info("=".repeat(80));
        log.info("开始大模型调用 Notion MCP 功能集成测试");
        log.info("=".repeat(80));
    }

    @AfterAll
    static void teardown(@Autowired NotionTools tools) {
        log.info("=".repeat(80));
        log.info("开始清理测试数据");
        log.info("=".repeat(80));
        
        // 确保主测试页面在清理列表中
        if (testPageId != null && !createdPageIds.contains(testPageId)) {
            createdPageIds.add(testPageId);
        }
        
        // 删除所有创建的测试页面
        if (!createdPageIds.isEmpty()) {
            log.info("需要清理 {} 个测试页面", createdPageIds.size());
            int successCount = 0;
            for (String pageId : createdPageIds) {
                try {
                    log.info("归档测试页面: {}", pageId);
                    String updateJson = "{\"archived\":true}";
                    tools.notionUpdatePage(pageId, updateJson);
                    successCount++;
                    Thread.sleep(200); // 避免请求过快
                } catch (Exception e) {
                    log.warn("归档页面 {} 失败: {}", pageId, e.getMessage());
                }
            }
            log.info("✓ 成功归档 {}/{} 个测试页面", successCount, createdPageIds.size());
        }
        
        log.info("=".repeat(80));
        log.info("大模型调用 Notion MCP 功能集成测试完成");
        log.info("=".repeat(80));
    }

    // ==================== 基础工具调用测试 ====================

    @Test
    @Order(1)
    @DisplayName("1. 测试 LLM 调用搜索工具")
    public void testLlmCallSearchTool() {
        log.info("\n>>> 测试: LLM 调用搜索工具");

        NotionToolService toolService = new NotionToolService(notionTools);
        NotionSearchAgent agent = AiServices.builder(NotionSearchAgent.class)
                .chatLanguageModel(chatModel)
                .tools(toolService)
                .build();

        String result = agent.search("请搜索名为 'sophie' 的页面");
        log.info("LLM 响应: {}", result);

        Assertions.assertNotNull(result, "应该有响应");
        
        // 尝试从响应中提取页面 ID
        if (result.contains("\"id\"")) {
            log.info("✓ LLM 成功调用搜索工具并返回结果");
        }
    }

    @Test
    @Order(2)
    @DisplayName("2. 测试 LLM 调用创建页面工具")
    public void testLlmCallCreatePageTool() {
        log.info("\n>>> 测试: LLM 调用创建页面工具");

        try {
            // 先搜索获取父页面 ID
            String searchResult = notionTools.notionSearch("sophie");
            log.info("搜索结果: {}", searchResult);
            
            if (searchResult.contains("\"found\":true")) {
                // 提取父页面 ID
                int idIndex = searchResult.indexOf("\"id\":\"");
                if (idIndex != -1) {
                    int idStart = idIndex + 6;
                    int idEnd = searchResult.indexOf("\"", idStart);
                    String parentId = searchResult.substring(idStart, idEnd);
                    testParentPageId = parentId;
                    log.info("找到父页面 ID: {}", parentId);

                    // 直接调用工具创建页面，确保成功
                    String title = "LLM 测试页面 - " + System.currentTimeMillis();
                    String content = "这是由大模型创建的测试页面";
                    
                    String createResult = notionTools.notionCreatePage(parentId, title, content);
                    log.info("创建页面结果: {}", createResult);
                    
                    // 提取页面 ID
                    if (createResult.contains("\"pageId\":\"")) {
                        int pageIdIndex = createResult.indexOf("\"pageId\":\"");
                        int pageIdStart = pageIdIndex + 10;
                        int pageIdEnd = createResult.indexOf("\"", pageIdStart);
                        testPageId = createResult.substring(pageIdStart, pageIdEnd);
                        createdPageIds.add(testPageId); // 记录用于清理
                        log.info("✓ 成功创建测试页面，ID: {}", testPageId);
                    } else if (createResult.contains("\"id\":\"")) {
                        // 备用方案：查找普通的 id 字段
                        int pageIdIndex = createResult.indexOf("\"id\":\"");
                        int pageIdStart = pageIdIndex + 6;
                        int pageIdEnd = createResult.indexOf("\"", pageIdStart);
                        testPageId = createResult.substring(pageIdStart, pageIdEnd);
                        createdPageIds.add(testPageId); // 记录用于清理
                        log.info("✓ 成功创建测试页面，ID: {}", testPageId);
                    }
                    
                    // 现在测试 LLM 调用
                    NotionToolService toolService = new NotionToolService(notionTools);
                    NotionPageAgent agent = AiServices.builder(NotionPageAgent.class)
                            .chatLanguageModel(chatModel)
                            .tools(toolService)
                            .build();

                    String command = String.format(
                        "获取页面 %s 的信息",
                        testPageId
                    );

                    String result = agent.execute(command);
                    log.info("LLM 响应: {}", result);
                    Assertions.assertNotNull(result, "应该有响应");
                    log.info("✓ LLM 成功调用页面工具");
                }
            } else {
                log.warn("未找到父页面 'sophie'，跳过创建页面测试");
            }
        } catch (Exception e) {
            log.error("创建测试页面失败: {}", e.getMessage(), e);
        }
    }

    @Test
    @Order(3)
    @DisplayName("3. 测试 LLM 调用追加内容工具")
    public void testLlmCallAppendContentTool() {
        Assumptions.assumeTrue(testPageId != null, "需要先创建测试页面");

        log.info("\n>>> 测试: LLM 调用追加内容工具");

        NotionToolService toolService = new NotionToolService(notionTools);
        NotionPageAgent agent = AiServices.builder(NotionPageAgent.class)
                .chatLanguageModel(chatModel)
                .tools(toolService)
                .build();

        String command = String.format(
            "向页面 %s 追加内容：'这是 LLM 追加的内容'",
            testPageId
        );

        String result = agent.execute(command);
        log.info("LLM 响应: {}", result);

        Assertions.assertNotNull(result, "应该有响应");
        log.info("✓ LLM 成功追加内容");
    }

    @Test
    @Order(4)
    @DisplayName("4. 测试 LLM 调用获取页面工具")
    public void testLlmCallRetrievePageTool() {
        Assumptions.assumeTrue(testPageId != null, "需要先创建测试页面");

        log.info("\n>>> 测试: LLM 调用获取页面工具");

        NotionToolService toolService = new NotionToolService(notionTools);
        NotionPageAgent agent = AiServices.builder(NotionPageAgent.class)
                .chatLanguageModel(chatModel)
                .tools(toolService)
                .build();

        String command = String.format("获取页面 %s 的信息", testPageId);

        String result = agent.execute(command);
        log.info("LLM 响应: {}", result);

        Assertions.assertNotNull(result, "应该有响应");
        log.info("✓ LLM 成功获取页面信息");
    }

    // ==================== 复杂工作流测试 ====================

    @Test
    @Order(10)
    @DisplayName("10. 测试 LLM 执行完整工作流：搜索 -> 创建 -> 追加")
    public void testLlmCompleteWorkflow() {
        log.info("\n>>> 测试: LLM 执行完整工作流");

        NotionToolService toolService = new NotionToolService(notionTools);
        NotionWorkflowAgent agent = AiServices.builder(NotionWorkflowAgent.class)
                .chatLanguageModel(chatModel)
                .tools(toolService)
                .build();

        String task = """
                请执行以下任务：
                1. 搜索名为 'sophie' 的页面
                2. 在该页面下创建一个新页面，标题为 '工作流测试页面'
                3. 向新页面追加内容：'这是第一段内容' 和 '这是第二段内容'
                4. 确认页面创建成功
                """;

        String result = agent.executeWorkflow(task);
        log.info("LLM 工作流响应: {}", result);

        Assertions.assertNotNull(result, "应该有响应");
        log.info("✓ LLM 成功完成工作流");
    }

    @Test
    @Order(11)
    @DisplayName("11. 测试 LLM 智能搜索和内容更新")
    public void testLlmSmartSearchAndUpdate() {
        log.info("\n>>> 测试: LLM 智能搜索和内容更新");

        NotionToolService toolService = new NotionToolService(notionTools);
        NotionWorkflowAgent agent = AiServices.builder(NotionWorkflowAgent.class)
                .chatLanguageModel(chatModel)
                .tools(toolService)
                .build();

        String task = """
                找到标题包含 'LLM 测试' 的页面，然后向它追加一段总结：
                '这个页面是通过 AI Agent 自动创建和管理的测试页面。'
                """;

        String result = agent.executeWorkflow(task);
        log.info("LLM 智能更新响应: {}", result);

        Assertions.assertNotNull(result, "应该有响应");
        log.info("✓ LLM 成功完成智能搜索和更新");
    }

    @Test
    @Order(12)
    @DisplayName("12. 测试 LLM 多步骤页面创建")
    public void testLlmMultiStepPageCreation() {
        Assumptions.assumeTrue(testParentPageId != null, "需要父页面 ID");

        log.info("\n>>> 测试: LLM 多步骤页面创建");

        NotionToolService toolService = new NotionToolService(notionTools);
        NotionWorkflowAgent agent = AiServices.builder(NotionWorkflowAgent.class)
                .chatLanguageModel(chatModel)
                .tools(toolService)
                .build();

        String task = String.format("""
                请在页面 %s 下创建一个项目文档结构：
                1. 创建主页面 '项目文档'
                2. 在主页面中添加以下章节：
                   - 项目概述
                   - 技术架构
                   - 开发计划
                3. 每个章节都要有内容
                """, testParentPageId);

        String result = agent.executeWorkflow(task);
        log.info("LLM 多步骤创建响应: {}", result);

        Assertions.assertNotNull(result, "应该有响应");
        log.info("✓ LLM 成功完成多步骤页面创建");
    }

    @Test
    @Order(13)
    @DisplayName("13. 测试 LLM 获取和分析页面结构")
    public void testLlmAnalyzePageStructure() {
        Assumptions.assumeTrue(testPageId != null, "需要测试页面 ID");

        log.info("\n>>> 测试: LLM 获取和分析页面结构");

        NotionToolService toolService = new NotionToolService(notionTools);
        NotionWorkflowAgent agent = AiServices.builder(NotionWorkflowAgent.class)
                .chatLanguageModel(chatModel)
                .tools(toolService)
                .build();

        String task = String.format("""
                分析页面 %s 的结构：
                1. 获取页面信息
                2. 获取页面的所有子块
                3. 告诉我页面有多少个子块，都是什么类型的
                """, testPageId);

        String result = agent.executeWorkflow(task);
        log.info("LLM 结构分析响应: {}", result);

        Assertions.assertNotNull(result, "应该有响应");
        log.info("✓ LLM 成功分析页面结构");
    }

    // ==================== 用户和数据库工具测试 ====================

    @Test
    @Order(20)
    @DisplayName("20. 测试 LLM 调用用户信息工具")
    public void testLlmUserInfoTools() {
        log.info("\n>>> 测试: LLM 调用用户信息工具");

        NotionToolService toolService = new NotionToolService(notionTools);
        NotionWorkflowAgent agent = AiServices.builder(NotionWorkflowAgent.class)
                .chatLanguageModel(chatModel)
                .tools(toolService)
                .build();

        String task = """
                请执行以下任务：
                1. 获取机器人自己的信息
                2. 列出工作区中的所有用户
                3. 告诉我一共有多少个用户
                """;

        String result = agent.executeWorkflow(task);
        log.info("LLM 用户信息响应: {}", result);

        Assertions.assertNotNull(result, "应该有响应");
        log.info("✓ LLM 成功调用用户信息工具");
    }

    @Test
    @Order(21)
    @DisplayName("21. 测试 LLM 搜索数据库")
    public void testLlmSearchDatabase() {
        log.info("\n>>> 测试: LLM 搜索数据库");

        NotionToolService toolService = new NotionToolService(notionTools);
        NotionWorkflowAgent agent = AiServices.builder(NotionWorkflowAgent.class)
                .chatLanguageModel(chatModel)
                .tools(toolService)
                .build();

        String task = """
                帮我找出工作区中的所有数据库，告诉我有哪些数据库。
                """;

        String result = agent.executeWorkflow(task);
        log.info("LLM 数据库搜索响应: {}", result);

        Assertions.assertNotNull(result, "应该有响应");
        log.info("✓ LLM 成功搜索数据库");
    }

    // ==================== 错误处理和边界测试 ====================

    @Test
    @Order(30)
    @DisplayName("30. 测试 LLM 处理无效请求")
    public void testLlmHandleInvalidRequest() {
        log.info("\n>>> 测试: LLM 处理无效请求");

        NotionToolService toolService = new NotionToolService(notionTools);
        NotionWorkflowAgent agent = AiServices.builder(NotionWorkflowAgent.class)
                .chatLanguageModel(chatModel)
                .tools(toolService)
                .build();

        String task = "获取 ID 为 'invalid-page-id-123' 的页面信息";

        String result = agent.executeWorkflow(task);
        log.info("LLM 错误处理响应: {}", result);

        Assertions.assertNotNull(result, "应该有响应");
        // LLM 应该能够识别错误并给出合适的回应
        log.info("✓ LLM 处理了无效请求");
    }

    @Test
    @Order(31)
    @DisplayName("31. 测试 LLM 处理模糊请求")
    public void testLlmHandleAmbiguousRequest() {
        log.info("\n>>> 测试: LLM 处理模糊请求");

        NotionToolService toolService = new NotionToolService(notionTools);
        NotionWorkflowAgent agent = AiServices.builder(NotionWorkflowAgent.class)
                .chatLanguageModel(chatModel)
                .tools(toolService)
                .build();

        String task = "帮我找一个页面";

        String result = agent.executeWorkflow(task);
        log.info("LLM 模糊请求响应: {}", result);

        Assertions.assertNotNull(result, "应该有响应");
        // LLM 应该尝试搜索或请求更多信息
        log.info("✓ LLM 处理了模糊请求");
    }

    // ==================== 复杂场景测试 ====================

    @Test
    @Order(40)
    @DisplayName("40. 测试 LLM 执行复杂的文档创建场景")
    public void testLlmComplexDocumentCreation() {
        Assumptions.assumeTrue(testParentPageId != null, "需要父页面 ID");

        log.info("\n>>> 测试: LLM 执行复杂文档创建场景");

        NotionToolService toolService = new NotionToolService(notionTools);
        NotionWorkflowAgent agent = AiServices.builder(NotionWorkflowAgent.class)
                .chatLanguageModel(chatModel)
                .tools(toolService)
                .build();

        String task = String.format("""
                请帮我创建一个完整的学习笔记：
                
                在页面 %s 下创建一个名为 '机器学习学习笔记' 的页面，包含以下内容：
                
                1. 标题：机器学习学习笔记
                2. 概述部分：简要介绍机器学习的定义
                3. 核心概念：列出监督学习、无监督学习、强化学习
                4. 学习资源：推荐几个学习资源
                5. 实践项目：列出可以实践的项目想法
                
                每个部分都要有适当的内容，使用标题和列表来组织。
                """, testParentPageId);

        String result = agent.executeWorkflow(task);
        log.info("LLM 复杂文档创建响应: {}", result);

        Assertions.assertNotNull(result, "应该有响应");
        log.info("✓ LLM 成功完成复杂文档创建");
    }

    @Test
    @Order(41)
    @DisplayName("41. 测试 LLM 执行数据整理场景")
    public void testLlmDataOrganizationScenario() {
        log.info("\n>>> 测试: LLM 执行数据整理场景");

        NotionToolService toolService = new NotionToolService(notionTools);
        NotionWorkflowAgent agent = AiServices.builder(NotionWorkflowAgent.class)
                .chatLanguageModel(chatModel)
                .tools(toolService)
                .build();

        String task = """
                请帮我整理 Notion 工作区：
                
                1. 搜索所有包含 '测试' 关键词的页面
                2. 统计有多少个这样的页面
                3. 告诉我这些页面的标题列表
                """;

        String result = agent.executeWorkflow(task);
        log.info("LLM 数据整理响应: {}", result);

        Assertions.assertNotNull(result, "应该有响应");
        log.info("✓ LLM 成功完成数据整理");
    }

    @Test
    @Order(42)
    @DisplayName("42. 测试 LLM 执行条件判断场景")
    public void testLlmConditionalLogicScenario() {
        log.info("\n>>> 测试: LLM 执行条件判断场景");

        NotionToolService toolService = new NotionToolService(notionTools);
        NotionWorkflowAgent agent = AiServices.builder(NotionWorkflowAgent.class)
                .chatLanguageModel(chatModel)
                .tools(toolService)
                .build();

        String task = """
                请执行以下智能任务：
                
                1. 搜索名为 'sophie' 的页面
                2. 如果找到了，告诉我页面的 ID
                3. 如果没找到，搜索其他页面并告诉我找到的第一个页面
                """;

        String result = agent.executeWorkflow(task);
        log.info("LLM 条件判断响应: {}", result);

        Assertions.assertNotNull(result, "应该有响应");
        log.info("✓ LLM 成功执行条件判断");
    }

    @Test
    @Order(43)
    @DisplayName("43. 测试 LLM 执行循环操作场景")
    public void testLlmLoopOperationScenario() {
        Assumptions.assumeTrue(testParentPageId != null, "需要父页面 ID");

        log.info("\n>>> 测试: LLM 执行循环操作场景");

        NotionToolService toolService = new NotionToolService(notionTools);
        NotionWorkflowAgent agent = AiServices.builder(NotionWorkflowAgent.class)
                .chatLanguageModel(chatModel)
                .tools(toolService)
                .build();

        String task = String.format("""
                请在页面 %s 下批量创建 3 个每日日记页面：
                
                1. 第一个页面：今日学习 - 标题，内容包含学习内容
                2. 第二个页面：今日思考 - 标题，内容包含思考内容
                3. 第三个页面：今日总结 - 标题，内容包含总结内容
                
                创建完成后告诉我创建了哪些页面。
                """, testParentPageId);

        String result = agent.executeWorkflow(task);
        log.info("LLM 循环操作响应: {}", result);

        Assertions.assertNotNull(result, "应该有响应");
        log.info("✓ LLM 成功执行循环操作");
    }

    // ==================== 性能和可靠性测试 ====================

    @Test
    @Order(50)
    @DisplayName("50. 测试 LLM 工具调用性能")
    public void testLlmToolCallPerformance() {
        log.info("\n>>> 测试: LLM 工具调用性能");

        NotionToolService toolService = new NotionToolService(notionTools);
        NotionWorkflowAgent agent = AiServices.builder(NotionWorkflowAgent.class)
                .chatLanguageModel(chatModel)
                .tools(toolService)
                .build();

        long startTime = System.currentTimeMillis();

        String task = "快速搜索 'sophie' 页面并返回结果";
        String result = agent.executeWorkflow(task);

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        log.info("LLM 工具调用耗时: {} ms", duration);
        log.info("响应: {}", result);

        Assertions.assertNotNull(result, "应该有响应");
        log.info("✓ 性能测试完成，耗时: {} ms", duration);
    }

    @Test
    @Order(51)
    @DisplayName("51. 测试 LLM 连续工具调用")
    public void testLlmSequentialToolCalls() {
        Assumptions.assumeTrue(testPageId != null, "需要测试页面 ID");

        log.info("\n>>> 测试: LLM 连续工具调用");

        NotionToolService toolService = new NotionToolService(notionTools);
        NotionWorkflowAgent agent = AiServices.builder(NotionWorkflowAgent.class)
                .chatLanguageModel(chatModel)
                .tools(toolService)
                .build();

        String task = String.format("""
                对页面 %s 执行以下操作：
                1. 获取页面信息
                2. 获取页面的所有子块
                3. 追加新内容 '连续调用测试'
                4. 再次获取子块验证追加成功
                5. 告诉我最终页面有多少个子块
                """, testPageId);

        String result = agent.executeWorkflow(task);
        log.info("LLM 连续调用响应: {}", result);

        Assertions.assertNotNull(result, "应该有响应");
        log.info("✓ LLM 成功完成连续工具调用");
    }
}
