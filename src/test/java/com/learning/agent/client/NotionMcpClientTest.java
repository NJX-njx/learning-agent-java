package com.learning.agent.client;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class NotionMcpClientTest {

    @Autowired
    private NotionMcpClient notionClient;

    @Test
    public void testConnectionAndSearch() {
        // 1. 测试初始化 (在 Spring 启动时已自动完成)
        assertNotNull(notionClient, "Client should be initialized");

        // 2. 测试连接 - 调用 getSelf 验证 MCP 连接是否正常
        System.out.println("开始测试 Notion MCP 连接...");
        
        try {
            Object self = notionClient.getSelf();
            assertNotNull(self, "getSelf should return bot information");
            System.out.println("✓ MCP 连接成功，机器人信息: " + self);
            
            // 3. 连接成功后测试搜索功能
            System.out.println("开始测试 Notion 搜索...");
            Optional<NotionClient.SearchResult> result = notionClient.searchPage("Learning Dashboard");
            
            if (result.isPresent()) {
                System.out.println("✓ 搜索成功! 找到页面: " + result.get().title() + " (ID: " + result.get().id() + ")");
            } else {
                System.out.println("✓ 搜索完成，未找到指定页面");
            }
        } catch (RuntimeException e) {
            fail("Notion MCP 连接失败: " + e.getMessage() + 
                 "\n请检查: \n1. .env 文件中 NOTION_MCP_TOKEN 是否正确配置\n2. npx 命令是否可用\n3. 网络连接是否正常", e);
        }
    }
}
