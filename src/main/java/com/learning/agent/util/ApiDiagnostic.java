package com.learning.agent.util;

import com.learning.agent.config.AppConfigProperties;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * API 配置诊断工具
 * 在应用启动时测试 API 连接
 * <p>
 * 启用方式：添加 --api.diagnostic.enabled=true 参数
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "api.diagnostic.enabled", havingValue = "true")
public class ApiDiagnostic implements CommandLineRunner {

    private final AppConfigProperties appConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ApiDiagnostic(AppConfigProperties appConfig) {
        this.appConfig = appConfig;
    }

    @Override
    public void run(String... args) {
        log.info("========================================");
        log.info("开始 API 诊断");
        log.info("========================================");

        testApiConnection();

        log.info("========================================");
        log.info("API 诊断完成");
        log.info("========================================");
    }

    private void testApiConnection() {
        try {
            log.info("测试配置:");
            log.info("  Base URL: {}", appConfig.getWenxinApiBaseUrl());
            log.info("  Model: {}", appConfig.getWenxinApiModel());
            String apiKey = appConfig.getWenxinApiKey();
            log.info("  API Key: {}...", apiKey != null && apiKey.length() > 6 ? apiKey.substring(0, 6) : "未设置");

            if (apiKey == null || apiKey.isBlank()) {
                log.error("❌ API Key 未配置！请设置 WENXIN_API_KEY 环境变量");
                return;
            }

            RestClient client = RestClient.builder()
                    .defaultHeader("Authorization", "Bearer " + apiKey)
                    .defaultHeader("Content-Type", "application/json")
                    .build();

            // 构建简单的测试请求
            Map<String, Object> requestBody = Map.of(
                    "model", appConfig.getWenxinApiModel(),
                    "messages", List.of(
                            Map.of("role", "user", "content", "测试：1+1=?")
                    ),
                    "temperature", 0.7,
                    "max_tokens", 50,
                    "stream", false
            );

            String requestJson = objectMapper.writeValueAsString(requestBody);
            String endpoint = appConfig.getWenxinApiBaseUrl() + "/chat/completions";

            log.info("发送测试请求到: {}", endpoint);
            log.debug("请求体: {}", requestJson);

            String response = client.post()
                    .uri(endpoint)
                    .body(requestJson != null ? requestJson : "")
                    .retrieve()
                    .body(String.class);

            if (response == null || response.isEmpty()) {
                log.error("✖ API 返回空响应");
                return;
            }

            log.info("✓ API 调用成功");
            log.debug("原始响应: {}", response);

            // 解析响应
            @SuppressWarnings("unchecked")
            Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);

            // 检查响应结构
            if (responseMap.containsKey("choices")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> choices = (List<Map<String, Object>>) responseMap.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    log.info("✓ 响应包含 {} 个 choice", choices.size());
                    Object message = choices.getFirst().get("message");
                    if (message != null) {
                        log.info("✓ 消息内容: {}", message);
                    }
                } else {
                    log.warn("⚠ choices 数组为空");
                }
            } else {
                log.error("❌ 响应不包含 choices 字段");
                log.error("响应包含的字段: {}", responseMap.keySet());
            }

            if (responseMap.containsKey("error")) {
                log.error("❌ API 返回错误: {}", responseMap.get("error"));
            }

        } catch (Exception e) {
            log.error("❌ API 测试失败", e);
            log.error("请检查:");
            log.error("  1. API Key 是否正确");
            log.error("  2. Base URL 是否可访问");
            log.error("  3. 网络连接是否正常");
            log.error("  4. 模型名称是否支持");
        }
    }
}
