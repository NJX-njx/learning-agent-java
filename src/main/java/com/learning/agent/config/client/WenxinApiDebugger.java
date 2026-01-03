package com.learning.agent.config.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * 文心一言 API 调试工具
 */
public class WenxinApiDebugger {

    private static final Logger log = LoggerFactory.getLogger(WenxinApiDebugger.class);

    /**
     * 测试原始 API 调用
     */
    public static void testRawApiCall(String baseUrl, String apiKey, String model) {
        try {
            log.info("=== 测试文心一言 API ===");
            log.info("Base URL: {}", baseUrl);
            log.info("Model: {}", model);

            RestClient client = RestClient.builder()
                    .defaultHeader("Authorization", "Bearer " + apiKey)
                    .defaultHeader("Content-Type", "application/json")
                    .build();

            String requestBody = """
                    {
                        "model": "%s",
                        "messages": [
                            {"role": "user", "content": "【系统指令】
                    你是一个有帮助的助手。
                    
                    【用户问题】
                    简单回答：1+1=?"}
                        ],
                        "temperature": 0.7,
                        "max_tokens": 50
                    }
                    """.formatted(model);

            log.info("发送请求到: {}/chat/completions", baseUrl);
            log.debug("请求体: {}", requestBody);

            ResponseEntity<String> response = client.post()
                    .uri(baseUrl + "/chat/completions")
                    .body(requestBody)
                    .retrieve()
                    .toEntity(String.class);

            log.debug("响应状态码: {}", response.getStatusCode());
            String responseBody = response.getBody();
            log.debug("响应体: {}", responseBody);

            if (responseBody == null || responseBody.isEmpty()) {
                log.error("响应体为空");
                return;
            }

            // 尝试解析响应
            ObjectMapper mapper = new ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> responseMap = mapper.readValue(responseBody, Map.class);

            if (responseMap.containsKey("choices")) {
                log.info("✓ 响应包含 choices 字段");
            } else {
                log.warn("✗ 响应不包含 choices 字段");
                log.warn("响应字段: {}", responseMap.keySet());
            }

            if (responseMap.containsKey("error")) {
                log.error("API 返回错误: {}", responseMap.get("error"));
            }

        } catch (Exception e) {
            log.error("API 调用失败", e);
        }
    }
}
