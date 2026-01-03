package com.learning.agent.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 测试文心一言 API 直接调用
 */
@Slf4j
@SpringBootTest
public class WenxinApiTest {

    @Value("${wenxin.api.key}")
    private String apiKey;

    @Value("${wenxin.api.base-url}")
    private String baseUrl;

    @Value("${wenxin.api.model}")
    private String modelName;

    @Autowired
    private ObjectMapper objectMapper;

    private static final DateTimeFormatter BCE_DATE_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .withZone(ZoneOffset.UTC);

    @Test
    public void testDirectApiCall() throws Exception {
        System.out.println("=== 文心一言 API 直接调用测试 ===");
        System.out.println("Base URL: " + baseUrl);
        System.out.println("Model: " + modelName);
        System.out.println("API Key (前6位): " + apiKey.substring(0, 6) + "...");

        // 构建请求体
        Map<String, Object> requestBody = Map.of(
                "model", modelName,
                "messages", List.of(
                        Map.of("role", "system", "content", "你是一个有帮助的助手。"),
                        Map.of("role", "user", "content", "你好，请回答：1+1等于几？")),
                "temperature", 0.7,
                "max_tokens", 100);

        String requestJson = objectMapper.writeValueAsString(requestBody);
        System.out.println("\n请求体:");
        System.out.println(requestJson);

        // 创建 RestClient
        String dateValue = BCE_DATE_FORMATTER.format(ZonedDateTime.now(ZoneOffset.UTC));

        RestClient restClient = RestClient.builder()
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader("x-bce-date", dateValue)
                .build();

        // 发送请求
        String endpoint = baseUrl + "/chat/completions";
        System.out.println("\n请求 URL: " + endpoint);

        try {
            String response = restClient.post()
                    .uri(endpoint)
                    .body(requestJson != null ? requestJson : "")
                    .retrieve()
                    .body(String.class);

            System.out.println("\n响应体:");
            System.out.println(response);

            // 解析响应
            Map<String, Object> responseMap = objectMapper.readValue(response,
                    new com.fasterxml.jackson.core.type.TypeReference<>() {
                    });
            System.out.println("\n解析后的响应:");
            System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(responseMap));

        } catch (Exception e) {
            log.error("\n调用失败:", e);
        }
    }
}
