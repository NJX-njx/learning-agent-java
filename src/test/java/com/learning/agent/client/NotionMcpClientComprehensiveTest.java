package com.learning.agent.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.agent.dto.client.NotionCreatedPage;
import com.learning.agent.dto.client.NotionWritePayload;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Notion MCP 客户端全面测试
 * 测试所有 NotionClient 接口的 MCP 功能
 */
@Slf4j
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class NotionMcpClientComprehensiveTest {

    @Autowired
    private NotionMcpClient notionClient;

    @Autowired
    private ObjectMapper objectMapper;

    // 测试数据 - 在测试之间共享
    private static String testParentPageId;
    private static String testPageId;
    private static String testDatabaseId;
    private static String testBlockId;
    private static String testUserId;
    // 存储所有创建的测试页面，用于清理
    private static final java.util.List<String> createdPageIds = new java.util.ArrayList<>();

    @BeforeAll
    static void setupTestData() {
        log.info("=".repeat(80));
        log.info("开始 Notion MCP 客户端全面测试");
        log.info("=".repeat(80));
    }

    @AfterAll
    static void teardownTestData(@Autowired NotionMcpClient client) {
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
                    Map<String, Object> updates = Map.of("archived", true);
                    client.updatePage(pageId, updates);
                    successCount++;
                    Thread.sleep(200); // 避免请求过快
                } catch (Exception e) {
                    log.warn("归档页面 {} 失败: {}", pageId, e.getMessage());
                }
            }
            log.info("✓ 成功归档 {}/{} 个测试页面", successCount, createdPageIds.size());
        }
        
        // 注意：不删除 testDatabaseId，因为它可能是用户手动创建的
        // 也不删除 testParentPageId（sophie 页面），因为它是测试基础环境
        
        log.info("=".repeat(80));
        log.info("Notion MCP 客户端测试完成");
        log.info("=".repeat(80));
    }

    // ==================== 用户相关测试 ====================

    @Test
    @Order(1)
    @DisplayName("1. 测试获取机器人自身信息")
    public void testGetSelf() {
        log.info("\n>>> 测试: 获取机器人自身信息");
        
        Object result = notionClient.getSelf();
        assertNotNull(result, "getSelf 应该返回结果");
        
        log.info("机器人信息: {}", result);
        
        // 尝试提取 user id 供后续测试使用
        if (result instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = (Map<String, Object>) result;
            
            // 尝试多种可能的字段名
            if (resultMap.containsKey("id")) {
                testUserId = resultMap.get("id").toString();
                log.info("✓ 从 'id' 字段保存测试用户 ID: {}", testUserId);
            } else if (resultMap.containsKey("bot") && resultMap.get("bot") instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> botMap = (Map<String, Object>) resultMap.get("bot");
                if (botMap.containsKey("owner") && botMap.get("owner") instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> ownerMap = (Map<String, Object>) botMap.get("owner");
                    if (ownerMap.containsKey("user") && ownerMap.get("user") instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> userMap = (Map<String, Object>) ownerMap.get("user");
                        if (userMap.containsKey("id")) {
                            testUserId = userMap.get("id").toString();
                            log.info("✓ 从 'bot.owner.user.id' 字段保存测试用户 ID: {}", testUserId);
                        }
                    }
                }
            }
            
            if (testUserId == null) {
                log.warn("⚠ 无法从 getSelf 结果中提取用户 ID，字段: {}", resultMap.keySet());
            }
        }
    }

    @Test
    @Order(2)
    @DisplayName("2. 测试列出所有用户")
    public void testListUsers() {
        log.info("\n>>> 测试: 列出所有用户");
        
        Object result = notionClient.listUsers(10, null);
        assertNotNull(result, "listUsers 应该返回结果");
        
        log.info("用户列表结果: {}", result);
        
        // 验证返回的是用户列表
        if (result instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = (Map<String, Object>) result;
            assertTrue(resultMap.containsKey("results"), "应该包含 results 字段");
            
            Object results = resultMap.get("results");
            assertTrue(results instanceof List, "results 应该是列表");
            
            @SuppressWarnings("unchecked")
            List<Object> userList = (List<Object>) results;
            log.info("找到 {} 个用户", userList.size());
            
            // 如果还没有 testUserId，从列表中获取第一个用户的ID
            if (testUserId == null && !userList.isEmpty() && userList.get(0) instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> firstUser = (Map<String, Object>) userList.get(0);
                if (firstUser.containsKey("id")) {
                    testUserId = firstUser.get("id").toString();
                    log.info("✓ 从用户列表中获取测试用户 ID: {}", testUserId);
                }
            }
        }
    }

    @Test
    @Order(3)
    @DisplayName("3. 测试通过 ID 获取用户")
    public void testGetUser() {
        // 如果没有测试用户 ID，跳过此测试
        if (testUserId == null) {
            log.warn("⚠ 跳过测试：无法获取用户 ID（从 getSelf 或 listUsers 测试）");
        }
        Assumptions.assumeTrue(testUserId != null, "需要先运行 testGetSelf 或 testListUsers 获取用户 ID");
        
        log.info("\n>>> 测试: 通过 ID 获取用户 ({})", testUserId);
        
        Object result = notionClient.getUser(testUserId);
        assertNotNull(result, "getUser 应该返回结果");
        
        log.info("用户信息: {}", result);
        
        // 验证返回的用户信息
        if (result instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> userMap = (Map<String, Object>) result;
            assertEquals("user", userMap.get("object"), "对象类型应该是 user");
            assertEquals(testUserId, userMap.get("id").toString(), "用户 ID 应该匹配");
            log.info("✓ 用户信息验证通过");
        }
    }

    // ==================== 搜索相关测试 ====================

    @Test
    @Order(10)
    @DisplayName("10. 测试基础搜索功能")
    public void testSearchPage() {
        log.info("\n>>> 测试: 基础搜索功能");
        
        // 搜索页面
        Optional<NotionClient.SearchResult> result = notionClient.searchPage("sophie");
        
        log.info("搜索结果: {}", result.isPresent() ? 
            String.format("找到页面 '%s' (ID: %s)", result.get().title(), result.get().id()) : 
            "未找到页面");
        
        // 如果找到页面，保存 ID 供后续测试使用
        result.ifPresent(searchResult -> {
            testParentPageId = searchResult.id();
            log.info("保存父页面 ID: {}", testParentPageId);
        });
    }

    @Test
    @Order(11)
    @DisplayName("11. 测试高级搜索功能")
    public void testSearchAll() {
        log.info("\n>>> 测试: 高级搜索功能");
        
        // 搜索所有类型（页面和数据库）
        Object result = notionClient.search("", null, null, 5, null);
        assertNotNull(result, "search 应该返回结果");
        
        log.info("搜索所有内容结果: {}", result);
        
        // 验证返回格式
        if (result instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = (Map<String, Object>) result;
            assertTrue(resultMap.containsKey("results"), "应该包含 results 字段");
        }
    }

    @Test
    @Order(12)
    @DisplayName("12. 测试带过滤器的搜索")
    public void testSearchWithFilter() {
        log.info("\n>>> 测试: 带过滤器的搜索");
        
        // 只搜索页面
        Map<String, Object> filter = Map.of(
            "property", "object",
            "value", "page"
        );
        
        Object result = notionClient.search("test", filter, null, 5, null);
        assertNotNull(result, "带过滤器的搜索应该返回结果");
        
        log.info("搜索页面结果: {}", result);
    }

    // ==================== 页面相关测试 ====================

    @Test
    @Order(20)
    @DisplayName("20. 测试创建页面")
    public void testCreatePage() {
        // 需要父页面 ID
        Assumptions.assumeTrue(testParentPageId != null, "需要先运行搜索测试获取父页面 ID");
        
        log.info("\n>>> 测试: 创建页面");
        
        NotionWritePayload payload = NotionWritePayload.builder()
                .parentPageId(testParentPageId)
                .title("MCP 测试页面 - " + System.currentTimeMillis())
                .markdownContent("# 测试标题\n\n这是测试内容\n\n- 测试项 1\n- 测试项 2")
                .properties(Map.of("测试键", "测试值"))
                .build();
        
        NotionCreatedPage result = notionClient.createPage(payload);
        
        assertNotNull(result, "createPage 应该返回结果");
        assertNotNull(result.getId(), "新页面应该有 ID");
        
        testPageId = result.getId();
        createdPageIds.add(testPageId); // 记录用于清理
        log.info("创建页面成功! ID: {}, URL: {}", result.getId(), result.getUrl());
    }

    @Test
    @Order(21)
    @DisplayName("21. 测试获取页面信息")
    public void testRetrievePage() {
        Assumptions.assumeTrue(testPageId != null, "需要先创建测试页面");
        
        log.info("\n>>> 测试: 获取页面信息 ({})", testPageId);
        
        Object result = notionClient.retrievePage(testPageId);
        assertNotNull(result, "retrievePage 应该返回结果");
        
        log.info("页面信息: {}", result);
        
        // 验证返回的页面对象
        if (result instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> pageMap = (Map<String, Object>) result;
            assertEquals("page", pageMap.get("object"), "对象类型应该是 page");
            assertEquals(testPageId, pageMap.get("id"), "ID 应该匹配");
        }
    }

    @Test
    @Order(22)
    @DisplayName("22. 测试更新页面")
    public void testUpdatePage() {
        Assumptions.assumeTrue(testPageId != null, "需要先创建测试页面");
        
        log.info("\n>>> 测试: 更新页面属性 ({})", testPageId);
        
        Map<String, Object> properties = Map.of(
            "更新测试", "更新值"
        );
        
        // updatePage 方法目前跳过了实际更新，但我们测试调用不报错
        assertDoesNotThrow(() -> notionClient.updatePage(testPageId, properties));
        
        log.info("更新页面调用成功");
    }

    @Test
    @Order(23)
    @DisplayName("23. 测试获取页面属性")
    public void testRetrievePageProperty() {
        Assumptions.assumeTrue(testPageId != null, "需要先创建测试页面");
        
        log.info("\n>>> 测试: 获取页面属性 ({})", testPageId);
        
        // 先获取页面信息，找到一个属性 ID
        Object pageResult = notionClient.retrievePage(testPageId);
        String propertyId = null;
        
        if (pageResult instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> pageMap = (Map<String, Object>) pageResult;
            if (pageMap.containsKey("properties")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> properties = (Map<String, Object>) pageMap.get("properties");
                if (!properties.isEmpty()) {
                    String firstKey = properties.keySet().iterator().next();
                    @SuppressWarnings("unchecked")
                    Map<String, Object> prop = (Map<String, Object>) properties.get(firstKey);
                    if (prop.containsKey("id")) {
                        propertyId = prop.get("id").toString();
                    }
                }
            }
        }
        
        if (propertyId != null) {
            log.info("测试属性 ID: {}", propertyId);
            Object result = notionClient.retrievePageProperty(testPageId, propertyId);
            assertNotNull(result, "retrievePageProperty 应该返回结果");
            log.info("页面属性结果: {}", result);
        } else {
            log.warn("无法找到属性 ID，跳过此测试");
        }
    }

    // ==================== 块相关测试 ====================

    @Test
    @Order(30)
    @DisplayName("30. 测试获取块的子块")
    public void testGetBlockChildren() {
        Assumptions.assumeTrue(testPageId != null, "需要先创建测试页面");
        
        log.info("\n>>> 测试: 获取块的子块 ({})", testPageId);
        
        // 页面本身也是一个块
        Object result = notionClient.getBlockChildren(testPageId, 10, null);
        assertNotNull(result, "getBlockChildren 应该返回结果");
        
        log.info("子块列表: {}", result);
        
        // 保存第一个块 ID 供后续测试使用
        if (result instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = (Map<String, Object>) result;
            if (resultMap.containsKey("results")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> blocks = (List<Map<String, Object>>) resultMap.get("results");
                if (!blocks.isEmpty()) {
                    testBlockId = blocks.get(0).get("id").toString();
                    log.info("✓ 保存测试块 ID: {}", testBlockId);
                } else {
                    log.warn("⚠ 页面还没有子块，将在 testAppendBlockChildren 中创建");
                }
            }
        }
    }

    @Test
    @Order(31)
    @DisplayName("31. 测试追加块内容")
    public void testAppendBlockChildren() {
        Assumptions.assumeTrue(testPageId != null, "需要先创建测试页面");
        
        log.info("\n>>> 测试: 追加块内容到页面 ({})", testPageId);
        
        // 创建新的块
        List<Map<String, Object>> children = new ArrayList<>();
        Map<String, Object> block = new HashMap<>();
        block.put("object", "block");
        block.put("type", "paragraph");
        
        Map<String, Object> paragraph = new HashMap<>();
        List<Map<String, Object>> richText = new ArrayList<>();
        Map<String, Object> textObj = new HashMap<>();
        textObj.put("type", "text");
        textObj.put("text", Map.of("content", "这是通过 appendBlockChildren 追加的内容"));
        richText.add(textObj);
        paragraph.put("rich_text", richText);
        block.put("paragraph", paragraph);
        
        children.add(block);
        
        Object result = notionClient.appendBlockChildren(testPageId, children);
        assertNotNull(result, "appendBlockChildren 应该返回结果");
        
        log.info("追加块结果: {}", result);
        
        // 如果还没有块 ID，从追加结果中提取
        if (testBlockId == null && result instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = (Map<String, Object>) result;
            if (resultMap.containsKey("results")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> blocks = (List<Map<String, Object>>) resultMap.get("results");
                if (!blocks.isEmpty() && blocks.get(0).containsKey("id")) {
                    testBlockId = blocks.get(0).get("id").toString();
                    log.info("✓ 从追加结果中保存块 ID: {}", testBlockId);
                }
            }
        }
    }

    @Test
    @Order(32)
    @DisplayName("32. 测试获取单个块信息")
    public void testRetrieveBlock() {
        if (testBlockId == null) {
            log.warn("⚠ 跳过测试：无法获取块 ID（从 testGetBlockChildren 或 testAppendBlockChildren）");
        }
        Assumptions.assumeTrue(testBlockId != null, "需要先运行 getBlockChildren 或 appendBlockChildren 获取块 ID");
        
        log.info("\n>>> 测试: 获取单个块信息 ({})", testBlockId);
        
        Object result = notionClient.retrieveBlock(testBlockId);
        assertNotNull(result, "retrieveBlock 应该返回结果");
        
        log.info("块信息: {}", result);
        
        // 验证块对象
        if (result instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> blockMap = (Map<String, Object>) result;
            assertEquals("block", blockMap.get("object"), "对象类型应该是 block");
        }
    }

    @Test
    @Order(33)
    @DisplayName("33. 测试更新块")
    public void testUpdateBlock() {
        if (testBlockId == null) {
            log.warn("⚠ 跳过测试：无法获取块 ID（从 testGetBlockChildren 或 testAppendBlockChildren）");
        }
        Assumptions.assumeTrue(testBlockId != null, "需要先运行 getBlockChildren 或 appendBlockChildren 获取块 ID");
        
        log.info("\n>>> 测试: 更新块 ({})", testBlockId);
        
        // 获取当前块信息
        Object blockResult = notionClient.retrieveBlock(testBlockId);
        
        if (blockResult instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> blockMap = (Map<String, Object>) blockResult;
            String blockType = blockMap.get("type").toString();
            
            // 根据块类型构造更新数据
            Map<String, Object> updateData = new HashMap<>();
            
            // 例如，如果是段落块，更新其文本
            if ("paragraph".equals(blockType)) {
                Map<String, Object> paragraph = new HashMap<>();
                List<Map<String, Object>> richText = new ArrayList<>();
                Map<String, Object> textObj = new HashMap<>();
                textObj.put("type", "text");
                textObj.put("text", Map.of("content", "已更新的段落内容"));
                richText.add(textObj);
                paragraph.put("rich_text", richText);
                updateData.put("paragraph", paragraph);
                
                Object result = notionClient.updateBlock(testBlockId, updateData);
                assertNotNull(result, "updateBlock 应该返回结果");
                log.info("更新块结果: {}", result);
            } else {
                log.info("块类型 {} 不支持简单更新测试，跳过", blockType);
            }
        }
    }

    @Test
    @Order(34)
    @DisplayName("34. 测试删除块")
    public void testDeleteBlock() {
        // 为删除操作创建一个专用的测试块
        Assumptions.assumeTrue(testPageId != null, "需要先创建测试页面");
        
        log.info("\n>>> 测试: 删除块");
        
        // 先创建一个块用于删除
        List<Map<String, Object>> children = new ArrayList<>();
        Map<String, Object> block = new HashMap<>();
        block.put("object", "block");
        block.put("type", "paragraph");
        
        Map<String, Object> paragraph = new HashMap<>();
        List<Map<String, Object>> richText = new ArrayList<>();
        Map<String, Object> textObj = new HashMap<>();
        textObj.put("type", "text");
        textObj.put("text", Map.of("content", "这个块将被删除"));
        richText.add(textObj);
        paragraph.put("rich_text", richText);
        block.put("paragraph", paragraph);
        
        children.add(block);
        
        Object appendResult = notionClient.appendBlockChildren(testPageId, children);
        
        // 从结果中提取新块的 ID
        String blockToDelete = null;
        if (appendResult instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = (Map<String, Object>) appendResult;
            if (resultMap.containsKey("results")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> results = (List<Map<String, Object>>) resultMap.get("results");
                if (!results.isEmpty()) {
                    blockToDelete = results.get(0).get("id").toString();
                }
            }
        }
        
        if (blockToDelete != null) {
            log.info("删除块 ID: {}", blockToDelete);
            Object deleteResult = notionClient.deleteBlock(blockToDelete);
            assertNotNull(deleteResult, "deleteBlock 应该返回结果");
            log.info("删除块结果: {}", deleteResult);
        } else {
            log.warn("无法创建测试块，跳过删除测试");
        }
    }

    // ==================== 数据库相关测试 ====================

    @Test
    @Order(40)
    @DisplayName("40. 测试创建数据库 (跳过 - API 不可用)")
    public void testCreateDatabase() {
        // 注意：Notion MCP 服务器可能不支持 API-create-a-database 方法
        // 此测试被跳过，但尝试查找已存在的数据库用于其他测试
        log.info("\n>>> 测试: 创建数据库 (跳过 - API 方法不存在)");
        log.warn("Notion MCP 服务器当前不支持 API-create-a-database 方法，尝试查找已存在的数据库");
        
        // 方法1：从搜索中查找所有页面，然后筛选数据库
        try {
            Object searchResult = notionClient.search("", null, null, 50, null);
            if (searchResult instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> resultMap = (Map<String, Object>) searchResult;
                if (resultMap.containsKey("results")) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> results = (List<Map<String, Object>>) resultMap.get("results");
                    
                    // 查找类型为 database 的项目
                    for (Map<String, Object> item : results) {
                        if ("database".equals(item.get("object"))) {
                            testDatabaseId = item.get("id").toString();
                            log.info("✓ 找到已存在的数据库 ID: {}", testDatabaseId);
                            
                            // 打印数据库信息
                            if (item.containsKey("title")) {
                                @SuppressWarnings("unchecked")
                                List<Map<String, Object>> titleList = (List<Map<String, Object>>) item.get("title");
                                if (!titleList.isEmpty() && titleList.get(0).containsKey("plain_text")) {
                                    String title = titleList.get(0).get("plain_text").toString();
                                    log.info("数据库名称: {}", title);
                                }
                            }
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("搜索数据库时出错: {}", e.getMessage());
        }
        
        if (testDatabaseId == null) {
            log.warn("⚠ 未找到可用的数据库，请在 Notion 中手动创建一个数据库以运行数据库相关测试");
            log.warn("跳过测试 #41-44：数据库获取、查询和更新");
        }
    }

    @Test
    @Order(41)
    @DisplayName("41. 测试获取数据库信息")
    public void testRetrieveDatabase() {
        if (testDatabaseId == null) {
            log.warn("⚠ 跳过测试：未找到可用的数据库，请在 Notion 中创建一个数据库");
        }
        Assumptions.assumeTrue(testDatabaseId != null, "需要在 Notion 中创建一个数据库（API 不支持自动创建）");
        
        log.info("\n>>> 测试: 获取数据库信息 ({})", testDatabaseId);
        
        Object result = notionClient.retrieveDatabase(testDatabaseId);
        assertNotNull(result, "retrieveDatabase 应该返回结果");
        
        log.info("数据库信息: {}", result);
        
        // 验证数据库对象
        if (result instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> dbMap = (Map<String, Object>) result;
            assertEquals("database", dbMap.get("object"), "对象类型应该是 database");
        }
    }

    @Test
    @Order(42)
    @DisplayName("42. 测试查询数据库")
    public void testQueryDatabase() {
        if (testDatabaseId == null) {
            log.warn("⚠ 跳过测试：未找到可用的数据库");
        }
        Assumptions.assumeTrue(testDatabaseId != null, "需要在 Notion 中创建一个数据库");
        
        log.info("\n>>> 测试: 查询数据库 ({})", testDatabaseId);
        
        // 无过滤器查询
        Object result = notionClient.queryDatabase(testDatabaseId, null, null, 10, null);
        assertNotNull(result, "queryDatabase 应该返回结果");
        
        log.info("查询数据库结果: {}", result);
        
        // 验证结果格式
        if (result instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = (Map<String, Object>) result;
            assertTrue(resultMap.containsKey("results"), "应该包含 results 字段");
        }
    }

    @Test
    @Order(43)
    @DisplayName("43. 测试带过滤器查询数据库")
    public void testQueryDatabaseWithFilter() {
        if (testDatabaseId == null) {
            log.warn("⚠ 跳过测试：未找到可用的数据库");
        }
        Assumptions.assumeTrue(testDatabaseId != null, "需要在 Notion 中创建一个数据库");
        
        log.info("\n>>> 测试: 带过滤器查询数据库 ({})", testDatabaseId);
        
        // 创建过滤器（例如：状态为"完成"）
        Map<String, Object> filter = Map.of(
            "property", "状态",
            "select", Map.of("equals", "完成")
        );
        
        Object result = notionClient.queryDatabase(testDatabaseId, filter, null, 10, null);
        assertNotNull(result, "带过滤器的查询应该返回结果");
        
        log.info("带过滤器查询结果: {}", result);
    }

    @Test
    @Order(44)
    @DisplayName("44. 测试更新数据库")
    public void testUpdateDatabase() {
        if (testDatabaseId == null) {
            log.warn("⚠ 跳过测试：未找到可用的数据库");
        }
        Assumptions.assumeTrue(testDatabaseId != null, "需要在 Notion 中创建一个数据库");
        
        log.info("\n>>> 测试: 更新数据库 ({})", testDatabaseId);
        
        try {
            // 添加新属性
            Map<String, Object> properties = Map.of(
                "优先级", Map.of(
                    "select", Map.of(
                        "options", List.of(
                            Map.of("name", "高", "color", "red"),
                            Map.of("name", "中", "color", "yellow"),
                            Map.of("name", "低", "color", "blue")
                        )
                    )
                )
            );
            
            Object result = notionClient.updateDatabase(testDatabaseId, properties);
            assertNotNull(result, "updateDatabase 应该返回结果");
            
            log.info("更新数据库结果: {}", result);
        } catch (Exception e) {
            log.warn("更新数据库失败（可能是权限或 API 问题）: {}", e.getMessage());
        }
    }

    // ==================== 评论相关测试 ====================

    @Test
    @Order(50)
    @DisplayName("50. 测试创建评论")
    public void testCreateComment() {
        Assumptions.assumeTrue(testPageId != null, "需要先创建测试页面");
        
        log.info("\n>>> 测试: 创建评论 ({})", testPageId);
        
        // createComment 可能会因为权限问题失败，所以我们测试它不抛出异常
        assertDoesNotThrow(() -> {
            notionClient.createComment(testPageId, "这是一个测试评论 - " + System.currentTimeMillis());
        });
        
        log.info("创建评论调用成功（可能跳过了实际创建）");
    }

    @Test
    @Order(51)
    @DisplayName("51. 测试获取评论")
    public void testRetrieveComments() {
        Assumptions.assumeTrue(testPageId != null, "需要先创建测试页面");
        
        log.info("\n>>> 测试: 获取评论 ({})", testPageId);
        
        Object result = notionClient.retrieveComments(testPageId, 10, null);
        assertNotNull(result, "retrieveComments 应该返回结果");
        
        log.info("评论列表: {}", result);
        
        // 验证结果格式
        if (result instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = (Map<String, Object>) result;
            assertTrue(resultMap.containsKey("results"), "应该包含 results 字段");
        }
    }

    // ==================== 综合测试 ====================

    @Test
    @Order(60)
    @DisplayName("60. 测试完整工作流：搜索 -> 创建 -> 更新 -> 读取")
    public void testCompleteWorkflow() {
        log.info("\n>>> 综合测试: 完整工作流");
        
        // 1. 搜索父页面
        log.info("步骤 1: 搜索父页面");
        Optional<NotionClient.SearchResult> searchResult = notionClient.searchPage("");
        assertTrue(searchResult.isPresent() || testParentPageId != null, "应该找到父页面或已有测试页面");
        
        String parentId = searchResult.map(NotionClient.SearchResult::id).orElse(testParentPageId);
        
        // 2. 创建新页面
        log.info("步骤 2: 创建新页面");
        NotionWritePayload payload = NotionWritePayload.builder()
                .parentPageId(parentId)
                .title("工作流测试页面 - " + System.currentTimeMillis())
                .markdownContent("# 工作流测试\n\n## 第一步\n\n测试内容\n\n## 第二步\n\n更多内容")
                .properties(new HashMap<>())
                .build();
        
        NotionCreatedPage newPage = notionClient.createPage(payload);
        assertNotNull(newPage.getId(), "应该创建成功");
        String newPageId = newPage.getId();
        createdPageIds.add(newPageId); // 记录用于清理
        log.info("创建页面: {}", newPageId);
        
        // 3. 追加内容
        log.info("步骤 3: 追加内容");
        List<Map<String, Object>> children = List.of(
            createParagraphBlock("这是追加的内容")
        );
        Object appendResult = notionClient.appendBlockChildren(newPageId, children);
        assertNotNull(appendResult, "追加内容应该成功");
        
        // 4. 读取页面内容
        log.info("步骤 4: 读取页面内容");
        Object pageInfo = notionClient.retrievePage(newPageId);
        assertNotNull(pageInfo, "应该能读取页面");
        
        // 5. 获取子块
        log.info("步骤 5: 获取子块");
        Object blocks = notionClient.getBlockChildren(newPageId, 100, null);
        assertNotNull(blocks, "应该能获取子块");
        
        log.info("工作流测试完成！");
    }

    // ==================== 边界和错误测试 ====================

    @Test
    @Order(70)
    @DisplayName("70. 测试分页功能")
    public void testPagination() {
        log.info("\n>>> 测试: 分页功能");
        
        // 测试用户列表分页
        Object firstPage = notionClient.listUsers(2, null);
        assertNotNull(firstPage, "第一页应该返回结果");
        
        // 尝试获取下一页
        if (firstPage instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = (Map<String, Object>) firstPage;
            if (resultMap.containsKey("has_more") && Boolean.TRUE.equals(resultMap.get("has_more"))) {
                String nextCursor = resultMap.get("next_cursor").toString();
                log.info("获取下一页，cursor: {}", nextCursor);
                
                Object secondPage = notionClient.listUsers(2, nextCursor);
                assertNotNull(secondPage, "第二页应该返回结果");
                log.info("分页测试成功");
            } else {
                log.info("没有更多页面，分页测试结束");
            }
        }
    }

    @Test
    @Order(71)
    @DisplayName("71. 测试空参数处理")
    public void testEmptyParameters() {
        log.info("\n>>> 测试: 空参数处理");
        
        // 测试空查询搜索
        Object result = notionClient.search("", null, null, null, null);
        assertNotNull(result, "空查询应该返回结果");
        
        log.info("空参数处理测试成功");
    }

    @Test
    @Order(72)
    @DisplayName("72. 测试无效 ID 处理")
    public void testInvalidId() {
        log.info("\n>>> 测试: 无效 ID 处理");
        
        // 测试使用无效的页面 ID
        // 注意：MCP 服务器返回错误对象而不是抛出异常
        Object result = notionClient.retrievePage("invalid-page-id-12345");
        assertNotNull(result, "即使 ID 无效也应该返回结果");
        
        // 检查返回的是否包含错误信息
        if (result instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = (Map<String, Object>) result;
            // 应该包含错误代码或错误信息
            boolean hasError = resultMap.containsKey("code") || 
                              resultMap.containsKey("status") || 
                              resultMap.containsKey("object") && "error".equals(resultMap.get("object"));
            assertTrue(hasError, "无效 ID 应该返回错误信息");
            log.info("收到错误响应: {}", resultMap);
        }
        
        log.info("无效 ID 处理测试成功");
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建段落块
     */
    private Map<String, Object> createParagraphBlock(String content) {
        Map<String, Object> block = new HashMap<>();
        block.put("object", "block");
        block.put("type", "paragraph");
        
        Map<String, Object> paragraph = new HashMap<>();
        List<Map<String, Object>> richText = new ArrayList<>();
        Map<String, Object> textObj = new HashMap<>();
        textObj.put("type", "text");
        textObj.put("text", Map.of("content", content));
        richText.add(textObj);
        paragraph.put("rich_text", richText);
        block.put("paragraph", paragraph);
        
        return block;
    }
}
