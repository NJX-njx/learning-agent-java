package com.learning.agent.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 应用配置属性加载器
 * 提供优雅的配置加载，支持环境变量缺失时的默认值
 */
@Slf4j
@Component
@Getter
public class AppConfigProperties {

    private final Environment environment;

    // Notion MCP 配置
    private String notionMcpToken;
    private String notionMcpVersion;

    // PaddleOCR MCP 配置
    private String paddleOcrMcpServer;
    private int paddleOcrRequestTimeoutMs;
    private int paddleOcrRequestRetries;
    private int paddleOcrMcpInitTimeoutSec;

    // 文心一言 API 配置
    private String wenxinApiKey;
    private String wenxinApiBaseUrl;
    private String wenxinApiModel;

    public AppConfigProperties(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void init() {
        // Notion MCP 配置
        notionMcpToken = getProperty("notion.mcp.token", "");
        notionMcpVersion = getProperty("notion.mcp.version", "2022-06-28");

        // PaddleOCR MCP 配置
        paddleOcrMcpServer = getProperty("paddleocr.mcp.server", "paddleocr");
        paddleOcrRequestTimeoutMs = getIntProperty("paddleocr.request.timeout-ms", 120000);
        paddleOcrRequestRetries = getIntProperty("paddleocr.request.retries", 3);
        paddleOcrMcpInitTimeoutSec = getIntProperty("paddleocr.mcp.init-timeout-sec", 120);

        // 文心一言 API 配置
        wenxinApiKey = getProperty("wenxin.api.key", "");
        wenxinApiBaseUrl = getProperty("wenxin.api.base-url", "https://aistudio.baidu.com/llm/lmapi/v3");
        wenxinApiModel = getProperty("wenxin.api.model", "ernie-4.5-turbo-vl");

        // 验证必需配置
        validateRequiredConfig();
        
        // 记录配置状态
        logConfigStatus();
    }

    private String getProperty(String key, String defaultValue) {
        String value = environment.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            if (defaultValue.isEmpty()) {
                log.debug("Property {} not set, using empty default", key);
            }
            return defaultValue;
        }
        return value;
    }

    private int getIntProperty(String key, int defaultValue) {
        String value = environment.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            log.warn("Invalid integer value for {}: {}, using default: {}", key, value, defaultValue);
            return defaultValue;
        }
    }

    private void logConfigStatus() {
        log.info("=".repeat(80));
        log.info("配置验证通过 ✓");
        log.info("");
        
        // 文心一言
        log.info("=== 文心一言 API 配置 ===");
        log.info("  API Key (前6位): {}...", wenxinApiKey.substring(0, Math.min(6, wenxinApiKey.length())));
        log.info("  Model: {}", wenxinApiModel);
        log.info("");
        
        // Notion
        log.info("=== Notion MCP 配置 ===");
        log.info("  Token (前6位): {}...", notionMcpToken.substring(0, Math.min(6, notionMcpToken.length())));
        log.info("  Version: {}", notionMcpVersion);
        log.info("");

        // PaddleOCR
        log.info("=== PaddleOCR MCP 配置 ===");
        log.info("  Server: {}", paddleOcrMcpServer);
        log.info("  Timeout: {}ms", paddleOcrRequestTimeoutMs);
        log.info("  Retries: {}", paddleOcrRequestRetries);
        
        log.info("=".repeat(80));
    }

    /**
     * 验证必需的环境变量
     * 如果缺失，输出清晰的错误信息并退出
     */
    private void validateRequiredConfig() {
        java.util.List<String> missingConfigs = new java.util.ArrayList<>();

        if (wenxinApiKey == null || wenxinApiKey.trim().isEmpty()) {
            missingConfigs.add("WENXIN_API_KEY");
        }

        if (notionMcpToken == null || notionMcpToken.trim().isEmpty()) {
            missingConfigs.add("NOTION_MCP_TOKEN");
        }

        if (!missingConfigs.isEmpty()) {
            log.error("=".repeat(80));
            log.error("配置验证失败！以下必需的环境变量未设置：");
            log.error("");

            for (String config : missingConfigs) {
                log.error("  ❌ {}", config);
            }

            log.error("");
            log.error("请通过以下方式之一进行配置：");
            log.error("");
            log.error("1. 使用 .env 文件（推荐）：");
            log.error("   复制 .env.example 为 .env 并填入实际值");
            log.error("");
            log.error("2. 设置环境变量");
            log.error("");
            log.error("获取文心一言 API Key：https://aistudio.baidu.com/account/accessToken");
            log.error("获取 Notion Token：https://www.notion.so/my-integrations");
            log.error("=".repeat(80));

            System.exit(1);
        }
    }

    /**
     * 检查 Notion 是否已配置
     */
    public boolean isNotionConfigured() {
        return notionMcpToken != null && !notionMcpToken.isEmpty();
    }

    /**
     * 检查文心一言是否已配置
     */
    public boolean isWenxinConfigured() {
        return wenxinApiKey != null && !wenxinApiKey.isEmpty();
    }
}
