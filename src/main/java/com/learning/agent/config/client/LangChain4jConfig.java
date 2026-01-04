package com.learning.agent.config.client;

import com.learning.agent.config.AppConfigProperties;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;

/**
 * LangChain4j 配置类
 * 配置 AI 模型客户端，使用 LangChain4j 框架
 */
@Slf4j
@Configuration
public class LangChain4jConfig {

    private final AppConfigProperties appConfig;

    public LangChain4jConfig(AppConfigProperties appConfig) {
        this.appConfig = appConfig;
    }

    /**
     * 规划模型 - 用于生成任务规划
     * 温度较低，保证输出稳定性
     */
    @Bean("planningChatModel")
    public ChatLanguageModel planningChatModel() {
        return OpenAiChatModel.builder()
                .baseUrl(appConfig.getWenxinApiBaseUrl())
                .apiKey(appConfig.getWenxinApiKey())
                .modelName(appConfig.getWenxinApiModel())
                .temperature(0.1)
                .maxTokens(2048)
                .timeout(Duration.ofSeconds(120))
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    /**
     * 执行模型 - 用于执行任务，支持工具调用
     * 温度适中，平衡创造性和准确性
     */
    @Bean("executionChatModel")
    @Primary
    public ChatLanguageModel executionChatModel() {
        return OpenAiChatModel.builder()
                .baseUrl(appConfig.getWenxinApiBaseUrl())
                .apiKey(appConfig.getWenxinApiKey())
                .modelName(appConfig.getWenxinApiModel())
                .temperature(0.7)
                .maxTokens(4096)
                .timeout(Duration.ofSeconds(180))
                .logRequests(true)
                .logResponses(true)
                .build();
    }
}
