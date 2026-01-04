package com.learning.agent.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Notion 工具类完整测试
 * 测试所有通过 NotionTools 暴露给 AI Agent 的功能
 */
@Slf4j
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class NotionToolsTest {

    @Autowired
    private NotionTools notionTools;

    @Autowired
    private ObjectMapper objectMapper;

    // 测试数据
    private static String testParentPageId;
    private static String testPageId;
    private static String testDatabaseId;
    private static String testUserId;
    // 存储所有创建的测试页面，用于清理
    private static final java.util.List<String> createdPageIds = new java.util.ArrayList<>();

    @BeforeAll
    static void setupTestData() {
        log.info("=".repeat(80));
        log.info("开始 Notion Tools 测试");
        log.info("=".repeat(80));
    }

    @AfterAll
    static void teardownTestData(@Autowired NotionTools tools) {
        log.info("=".repeat(80));
        log.info("开始清理测试数据");
        log.info("=".repeat(80));
        
        // 删除主测试页面
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
                    // 避免请求过快
                    Thread.sleep(200);
                } catch (Exception e) {
                    log.warn("归档页面 {} 失败: {}", pageId, e.getMessage());
                }
            }
            log.info("✓ 成功归档 {}/{} 个测试页面", successCount, createdPageIds.size());
        }
        
        log.info("=".repeat(80));
        log.info("Notion Tools 测试完成");
        log.info("=".repeat(80));
    }

    // ==================== 搜索工具测试 ====================

    @Test
    @Order(1)
    @DisplayName("1. 测试 notionSearch 工具")
    public void testNotionSearch() throws Exception {
        log.info("\n>>> 测试工具: notionSearch");
        
        String result = notionTools.notionSearch("sophie");
        assertNotNull(result, "notionSearch 应该返回结果");
        
        // 解析 JSON 结果
        JsonNode jsonResult = objectMapper.readTree(result);
        log.info("搜索结果: {}", jsonResult);
        
        // 检查结果格式
        assertTrue(jsonResult.has("found"), "结果应该包含 found 字段");
        
        // 如果找到页面，保存 ID
        if (jsonResult.get("found").asBoolean()) {
            assertTrue(jsonResult.has("id"), "找到页面时应该有 id 字段");
            assertTrue(jsonResult.has("title"), "找到页面时应该有 title 字段");
            testParentPageId = jsonResult.get("id").asText();
            log.info("找到页面: {} (ID: {})", jsonResult.get("title").asText(), testParentPageId);
        }
    }

    @Test
    @Order(2)
    @DisplayName("2. 测试 notionSearchAll 工具")
    public void testNotionSearchAll() throws Exception {
        log.info("\n>>> 测试工具: notionSearchAll");
        
        String result = notionTools.notionSearchAll("test", null, null, 5, null);
        assertNotNull(result, "notionSearchAll 应该返回结果");
        
        JsonNode jsonResult = objectMapper.readTree(result);
        log.info("高级搜索结果: {}", jsonResult);
        
        assertTrue(jsonResult.has("results"), "结果应该包含 results 字段");
    }

    @Test
    @Order(3)
    @DisplayName("3. 测试 notionSearchAll 带过滤器")
    public void testNotionSearchAllWithFilter() throws Exception {
        log.info("\n>>> 测试工具: notionSearchAll (带过滤器)");
        
        String filterJson = objectMapper.writeValueAsString(Map.of(
            "property", "object",
            "value", "page"
        ));
        
        String result = notionTools.notionSearchAll("", filterJson, null, 5, null);
        assertNotNull(result, "带过滤器的搜索应该返回结果");
        
        JsonNode jsonResult = objectMapper.readTree(result);
        log.info("过滤搜索结果: {}", jsonResult);
    }

    // ==================== 页面操作工具测试 ====================

    @Test
    @Order(10)
    @DisplayName("10. 测试 notionCreatePage 工具")
    public void testNotionCreatePage() throws Exception {
        Assumptions.assumeTrue(testParentPageId != null, "需要先运行搜索测试获取父页面 ID");
        
        log.info("\n>>> 测试工具: notionCreatePage");
        
        String title = "工具测试页面 - " + System.currentTimeMillis();
        String content = "# 测试标题\n\n这是通过工具创建的测试页面\n\n- 项目 1\n- 项目 2";
        
        String result = notionTools.notionCreatePage(testParentPageId, title, content);
        assertNotNull(result, "notionCreatePage 应该返回结果");
        
        JsonNode jsonResult = objectMapper.readTree(result);
        log.info("创建页面结果: {}", jsonResult);
        
        assertTrue(jsonResult.has("id"), "结果应该包含 id 字段");
        testPageId = jsonResult.get("id").asText();
        createdPageIds.add(testPageId); // 记录用于清理
        log.info("创建页面成功，ID: {}", testPageId);
    }

    @Test
    @Order(11)
    @DisplayName("11. 测试 notionAppendContent 工具")
    public void testNotionAppendContent() throws Exception {
        Assumptions.assumeTrue(testPageId != null, "需要先创建测试页面");
        
        log.info("\n>>> 测试工具: notionAppendContent");
        
        String content = "这是追加的内容\n另一行内容\n第三行内容";
        
        String result = notionTools.notionAppendContent(testPageId, content);
        assertNotNull(result, "notionAppendContent 应该返回结果");
        
        JsonNode jsonResult = objectMapper.readTree(result);
        log.info("追加内容结果: {}", jsonResult);
        
        assertTrue(jsonResult.has("success"), "结果应该包含 success 字段");
        assertTrue(jsonResult.get("success").asBoolean(), "追加应该成功");
    }

    @Test
    @Order(12)
    @DisplayName("12. 测试 notionRetrievePage 工具")
    public void testNotionRetrievePage() throws Exception {
        Assumptions.assumeTrue(testPageId != null, "需要先创建测试页面");
        
        log.info("\n>>> 测试工具: notionRetrievePage");
        
        String result = notionTools.notionRetrievePage(testPageId);
        assertNotNull(result, "notionRetrievePage 应该返回结果");
        
        JsonNode jsonResult = objectMapper.readTree(result);
        log.info("获取页面结果: {}", jsonResult);
        
        assertEquals("page", jsonResult.get("object").asText(), "对象类型应该是 page");
        assertEquals(testPageId, jsonResult.get("id").asText(), "ID 应该匹配");
    }

    // ==================== 块操作工具测试 ====================

    @Test
    @Order(20)
    @DisplayName("20. 测试 notionGetBlockChildren 工具")
    public void testNotionGetBlockChildren() throws Exception {
        Assumptions.assumeTrue(testPageId != null, "需要先创建测试页面");
        
        log.info("\n>>> 测试工具: notionGetBlockChildren");
        
        String result = notionTools.notionGetBlockChildren(testPageId, 10, null);
        assertNotNull(result, "notionGetBlockChildren 应该返回结果");
        
        JsonNode jsonResult = objectMapper.readTree(result);
        log.info("获取子块结果: {}", jsonResult);
        
        assertTrue(jsonResult.has("results"), "结果应该包含 results 字段");
    }

    @Test
    @Order(21)
    @DisplayName("21. 测试 notionRetrieveBlock 工具")
    public void testNotionRetrieveBlock() throws Exception {
        Assumptions.assumeTrue(testPageId != null, "需要先创建测试页面");
        
        log.info("\n>>> 测试工具: notionRetrieveBlock");
        
        // 先获取子块
        String childrenResult = notionTools.notionGetBlockChildren(testPageId, 1, null);
        JsonNode childrenJson = objectMapper.readTree(childrenResult);
        
        if (childrenJson.has("results") && childrenJson.get("results").size() > 0) {
            String blockId = childrenJson.get("results").get(0).get("id").asText();
            log.info("测试块 ID: {}", blockId);
            
            String result = notionTools.notionRetrieveBlock(blockId);
            assertNotNull(result, "notionRetrieveBlock 应该返回结果");
            
            JsonNode jsonResult = objectMapper.readTree(result);
            log.info("获取块结果: {}", jsonResult);
            
            assertEquals("block", jsonResult.get("object").asText(), "对象类型应该是 block");
        } else {
            log.warn("页面没有子块，跳过此测试");
        }
    }

    // ==================== 用户工具测试 ====================

    @Test
    @Order(30)
    @DisplayName("30. 测试 notionGetSelf 工具")
    public void testNotionGetSelf() throws Exception {
        log.info("\n>>> 测试工具: notionGetSelf");
        
        String result = notionTools.notionGetSelf();
        assertNotNull(result, "notionGetSelf 应该返回结果");
        
        JsonNode jsonResult = objectMapper.readTree(result);
        log.info("机器人信息: {}", jsonResult);
        
        assertTrue(jsonResult.has("object"), "结果应该包含 object 字段");
        if (jsonResult.has("id")) {
            testUserId = jsonResult.get("id").asText();
            log.info("保存用户 ID: {}", testUserId);
        }
    }

    @Test
    @Order(31)
    @DisplayName("31. 测试 notionListUsers 工具")
    public void testNotionListUsers() throws Exception {
        log.info("\n>>> 测试工具: notionListUsers");
        
        String result = notionTools.notionListUsers(10, null);
        assertNotNull(result, "notionListUsers 应该返回结果");
        
        JsonNode jsonResult = objectMapper.readTree(result);
        log.info("用户列表: {}", jsonResult);
        
        assertTrue(jsonResult.has("results"), "结果应该包含 results 字段");
    }

    @Test
    @Order(32)
    @DisplayName("32. 测试 notionGetUser 工具")
    public void testNotionGetUser() throws Exception {
        Assumptions.assumeTrue(testUserId != null, "需要先运行 notionGetSelf");
        
        log.info("\n>>> 测试工具: notionGetUser");
        
        String result = notionTools.notionGetUser(testUserId);
        assertNotNull(result, "notionGetUser 应该返回结果");
        
        JsonNode jsonResult = objectMapper.readTree(result);
        log.info("用户信息: {}", jsonResult);
        
        assertEquals(testUserId, jsonResult.get("id").asText(), "用户 ID 应该匹配");
    }

    // ==================== 数据库工具测试 ====================

    @Test
    @Order(40)
    @DisplayName("40. 测试 notionRetrieveDatabase 工具")
    public void testNotionRetrieveDatabase() throws Exception {
        log.info("\n>>> 测试工具: notionRetrieveDatabase");
        
        // 尝试查找数据库：搜索所有项目，筛选数据库类型
        try {
            String searchResult = notionTools.notionSearchAll("", null, null, 50, null);
            JsonNode searchJson = objectMapper.readTree(searchResult);
            
            if (searchJson.has("results")) {
                for (JsonNode item : searchJson.get("results")) {
                    if (item.has("object") && "database".equals(item.get("object").asText())) {
                        testDatabaseId = item.get("id").asText();
                        log.info("✓ 找到数据库 ID: {}", testDatabaseId);
                        
                        // 打印数据库名称
                        if (item.has("title") && item.get("title").isArray() && item.get("title").size() > 0) {
                            String title = item.get("title").get(0).get("plain_text").asText();
                            log.info("数据库名称: {}", title);
                        }
                        
                        // 测试获取数据库信息
                        String result = notionTools.notionRetrieveDatabase(testDatabaseId);
                        assertNotNull(result, "notionRetrieveDatabase 应该返回结果");
                        
                        JsonNode jsonResult = objectMapper.readTree(result);
                        log.info("数据库信息: {}", jsonResult);
                        
                        assertEquals("database", jsonResult.get("object").asText(), "对象类型应该是 database");
                        return; // 测试成功，退出
                    }
                }
            }
            
            log.warn("⚠ 未找到数据库，请在 Notion 中创建一个数据库以运行此测试");
        } catch (Exception e) {
            log.warn("查找数据库失败: {}", e.getMessage());
        }
    }

    @Test
    @Order(41)
    @DisplayName("41. 测试 notionQueryDatabase 工具")
    public void testNotionQueryDatabase() throws Exception {
        if (testDatabaseId == null) {
            log.warn("⚠ 跳过测试：未找到数据库，请在 Notion 中创建一个数据库");
        }
        Assumptions.assumeTrue(testDatabaseId != null, "需要在 Notion 中创建一个数据库");
        
        log.info("\n>>> 测试工具: notionQueryDatabase");
        
        String result = notionTools.notionQueryDatabase(testDatabaseId, null, null, 10, null);
        assertNotNull(result, "notionQueryDatabase 应该返回结果");
        
        JsonNode jsonResult = objectMapper.readTree(result);
        log.info("查询数据库结果: {}", jsonResult);
        
        assertTrue(jsonResult.has("results"), "结果应该包含 results 字段");
    }

    // ==================== executeTool 测试 ====================

    @Test
    @Order(50)
    @DisplayName("50. 测试 executeTool - notionSearch")
    public void testExecuteToolSearch() throws Exception {
        log.info("\n>>> 测试: executeTool - notionSearch");
        
        Map<String, Object> args = Map.of("query", "sophie");
        String result = notionTools.executeTool("notionSearch", args);
        
        assertNotNull(result, "executeTool 应该返回结果");
        JsonNode jsonResult = objectMapper.readTree(result);
        assertTrue(jsonResult.has("found"), "结果应该包含 found 字段");
        
        log.info("通过 executeTool 搜索结果: {}", jsonResult);
    }

    @Test
    @Order(51)
    @DisplayName("51. 测试 executeTool - notionCreatePage")
    public void testExecuteToolCreatePage() throws Exception {
        Assumptions.assumeTrue(testParentPageId != null, "需要先运行搜索测试");
        
        log.info("\n>>> 测试: executeTool - notionCreatePage");
        
        Map<String, Object> args = new HashMap<>();
        args.put("parentPageId", testParentPageId);
        args.put("title", "通过 executeTool 创建 - " + System.currentTimeMillis());
        args.put("content", "这是通过 executeTool 创建的页面");
        
        String result = notionTools.executeTool("notionCreatePage", args);
        
        assertNotNull(result, "executeTool 应该返回结果");
        JsonNode jsonResult = objectMapper.readTree(result);
        assertTrue(jsonResult.has("id"), "结果应该包含 id 字段");
        
        String createdPageId = jsonResult.get("id").asText();
        createdPageIds.add(createdPageId); // 记录用于清理
        
        log.info("通过 executeTool 创建页面: {}", jsonResult);
    }

    @Test
    @Order(52)
    @DisplayName("52. 测试 executeTool - notionAppendContent")
    public void testExecuteToolAppendContent() throws Exception {
        Assumptions.assumeTrue(testPageId != null, "需要先创建测试页面");
        
        log.info("\n>>> 测试: executeTool - notionAppendContent");
        
        Map<String, Object> args = Map.of(
            "pageId", testPageId,
            "content", "通过 executeTool 追加的内容"
        );
        
        String result = notionTools.executeTool("notionAppendContent", args);
        
        assertNotNull(result, "executeTool 应该返回结果");
        JsonNode jsonResult = objectMapper.readTree(result);
        assertTrue(jsonResult.get("success").asBoolean(), "追加应该成功");
        
        log.info("通过 executeTool 追加内容: {}", jsonResult);
    }

    @Test
    @Order(53)
    @DisplayName("53. 测试 executeTool - notionGetSelf")
    public void testExecuteToolGetSelf() throws Exception {
        log.info("\n>>> 测试: executeTool - notionGetSelf");
        
        String result = notionTools.executeTool("notionGetSelf", new HashMap<>());
        
        assertNotNull(result, "executeTool 应该返回结果");
        JsonNode jsonResult = objectMapper.readTree(result);
        assertTrue(jsonResult.has("object"), "结果应该包含 object 字段");
        
        log.info("通过 executeTool 获取机器人信息: {}", jsonResult);
    }

    @Test
    @Order(54)
    @DisplayName("54. 测试 executeTool - notionListUsers")
    public void testExecuteToolListUsers() throws Exception {
        log.info("\n>>> 测试: executeTool - notionListUsers");
        
        Map<String, Object> args = Map.of("pageSize", 5);
        String result = notionTools.executeTool("notionListUsers", args);
        
        assertNotNull(result, "executeTool 应该返回结果");
        JsonNode jsonResult = objectMapper.readTree(result);
        assertTrue(jsonResult.has("results"), "结果应该包含 results 字段");
        
        log.info("通过 executeTool 列出用户: {}", jsonResult);
    }

    @Test
    @Order(55)
    @DisplayName("55. 测试 executeTool - notionGetBlockChildren")
    public void testExecuteToolGetBlockChildren() throws Exception {
        Assumptions.assumeTrue(testPageId != null, "需要先创建测试页面");
        
        log.info("\n>>> 测试: executeTool - notionGetBlockChildren");
        
        Map<String, Object> args = Map.of(
            "blockId", testPageId,
            "pageSize", 10
        );
        
        String result = notionTools.executeTool("notionGetBlockChildren", args);
        
        assertNotNull(result, "executeTool 应该返回结果");
        JsonNode jsonResult = objectMapper.readTree(result);
        assertTrue(jsonResult.has("results"), "结果应该包含 results 字段");
        
        log.info("通过 executeTool 获取子块: {}", jsonResult);
    }

    @Test
    @Order(56)
    @DisplayName("56. 测试 executeTool - notionRetrievePage")
    public void testExecuteToolRetrievePage() throws Exception {
        Assumptions.assumeTrue(testPageId != null, "需要先创建测试页面");
        
        log.info("\n>>> 测试: executeTool - notionRetrievePage");
        
        Map<String, Object> args = Map.of("pageId", testPageId);
        String result = notionTools.executeTool("notionRetrievePage", args);
        
        assertNotNull(result, "executeTool 应该返回结果");
        JsonNode jsonResult = objectMapper.readTree(result);
        assertEquals("page", jsonResult.get("object").asText(), "对象类型应该是 page");
        
        log.info("通过 executeTool 获取页面: {}", jsonResult);
    }

    @Test
    @Order(57)
    @DisplayName("57. 测试 executeTool - 未知工具")
    public void testExecuteToolUnknown() {
        log.info("\n>>> 测试: executeTool - 未知工具");
        
        String result = notionTools.executeTool("unknownTool", new HashMap<>());
        
        assertNotNull(result, "未知工具应该返回错误消息");
        assertTrue(result.contains("Unknown tool"), "应该包含错误消息");
        
        log.info("未知工具结果: {}", result);
    }

    // ==================== 综合场景测试 ====================

    @Test
    @Order(60)
    @DisplayName("60. 测试完整工作流场景")
    public void testCompleteWorkflowScenario() throws Exception {
        log.info("\n>>> 综合测试: 完整工作流场景");
        
        // 场景：搜索 -> 创建页面 -> 追加内容 -> 读取验证
        
        // 1. 搜索父页面
        log.info("步骤 1: 搜索父页面");
        String searchResult = notionTools.notionSearch("");
        JsonNode searchJson = objectMapper.readTree(searchResult);
        
        String parentId = testParentPageId;
        if (searchJson.get("found").asBoolean()) {
            parentId = searchJson.get("id").asText();
        }
        assertNotNull(parentId, "应该有父页面 ID");
        
        // 2. 创建新页面
        log.info("步骤 2: 创建新页面");
        String createResult = notionTools.notionCreatePage(
            parentId,
            "工作流场景测试 - " + System.currentTimeMillis(),
            "# 场景测试\n\n初始内容"
        );
        JsonNode createJson = objectMapper.readTree(createResult);
        String newPageId = createJson.get("id").asText();
        createdPageIds.add(newPageId); // 记录用于清理
        
        // 3. 追加内容
        log.info("步骤 3: 追加内容");
        String appendResult = notionTools.notionAppendContent(
            newPageId,
            "这是追加的第一段内容\n这是追加的第二段内容"
        );
        JsonNode appendJson = objectMapper.readTree(appendResult);
        assertTrue(appendJson.get("success").asBoolean(), "追加应该成功");
        
        // 4. 读取页面验证
        log.info("步骤 4: 读取页面验证");
        String retrieveResult = notionTools.notionRetrievePage(newPageId);
        JsonNode retrieveJson = objectMapper.readTree(retrieveResult);
        assertEquals(newPageId, retrieveJson.get("id").asText(), "页面 ID 应该匹配");
        
        // 5. 获取子块验证内容
        log.info("步骤 5: 获取子块验证");
        String blocksResult = notionTools.notionGetBlockChildren(newPageId, 100, null);
        JsonNode blocksJson = objectMapper.readTree(blocksResult);
        assertTrue(blocksJson.has("results"), "应该有子块");
        
        int blockCount = blocksJson.get("results").size();
        log.info("页面共有 {} 个子块", blockCount);
        assertTrue(blockCount > 0, "应该至少有一个块");
        
        log.info("完整工作流场景测试成功！");
    }

    @Test
    @Order(61)
    @DisplayName("61. 测试多页面创建场景")
    public void testMultiplePageCreationScenario() throws Exception {
        Assumptions.assumeTrue(testParentPageId != null, "需要父页面 ID");
        
        log.info("\n>>> 综合测试: 多页面创建场景");
        
        // 创建多个页面
        int pageCount = 3;
        String[] pageIds = new String[pageCount];
        
        for (int i = 0; i < pageCount; i++) {
            log.info("创建页面 {}/{}", i + 1, pageCount);
            
            String result = notionTools.notionCreatePage(
                testParentPageId,
                "批量测试页面 " + (i + 1) + " - " + System.currentTimeMillis(),
                "# 页面 " + (i + 1) + "\n\n这是第 " + (i + 1) + " 个测试页面"
            );
            
            JsonNode jsonResult = objectMapper.readTree(result);
            pageIds[i] = jsonResult.get("id").asText();
            createdPageIds.add(pageIds[i]); // 记录用于清理
            
            // 短暂延迟避免请求过快
            Thread.sleep(500);
        }
        
        // 验证所有页面都创建成功
        for (int i = 0; i < pageCount; i++) {
            String retrieveResult = notionTools.notionRetrievePage(pageIds[i]);
            JsonNode jsonResult = objectMapper.readTree(retrieveResult);
            assertEquals(pageIds[i], jsonResult.get("id").asText(), "页面 " + (i + 1) + " 应该存在");
            log.info("验证页面 {}/{} 成功", i + 1, pageCount);
        }
        
        log.info("多页面创建场景测试成功！");
    }

    @Test
    @Order(62)
    @DisplayName("62. 测试搜索和查询场景")
    public void testSearchAndQueryScenario() throws Exception {
        log.info("\n>>> 综合测试: 搜索和查询场景");
        
        // 1. 全局搜索
        log.info("步骤 1: 全局搜索");
        String searchResult = notionTools.notionSearchAll("test", null, null, 5, null);
        JsonNode searchJson = objectMapper.readTree(searchResult);
        assertTrue(searchJson.has("results"), "搜索应该返回结果");
        
        // 2. 过滤搜索页面
        log.info("步骤 2: 过滤搜索页面");
        String filterJson = objectMapper.writeValueAsString(Map.of(
            "property", "object",
            "value", "page"
        ));
        String pageSearchResult = notionTools.notionSearchAll("", filterJson, null, 5, null);
        JsonNode pageSearchJson = objectMapper.readTree(pageSearchResult);
        assertTrue(pageSearchJson.has("results"), "页面搜索应该返回结果");
        
        // 3. 过滤搜索数据库（注意：Notion API 不支持 'database' 作为过滤值）
        log.info("步骤 3: 尝试过滤搜索数据库");
        try {
            String dbFilterJson = objectMapper.writeValueAsString(Map.of(
                "property", "object",
                "value", "database"  // 已知会失败
            ));
            String dbSearchResult = notionTools.notionSearchAll("", dbFilterJson, null, 5, null);
            JsonNode dbSearchJson = objectMapper.readTree(dbSearchResult);
            
            // 检查是否返回错误或结果
            if (dbSearchJson.has("results")) {
                log.info("数据库搜索返回结果");
            } else if (dbSearchJson.has("code") || dbSearchJson.has("status")) {
                log.info("数据库搜索返回错误（预期行为）: {}", dbSearchJson.get("message").asText());
            }
        } catch (Exception e) {
            log.info("数据库搜索失败（预期行为 - Notion 不支持 'database' 过滤器）: {}", e.getMessage());
        }
        
        log.info("搜索和查询场景测试成功！");
    }
}
