package com.learning.agent.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LangChain4jConfig 配置测试
 * 验证 LangChain4j 模型配置是否正确
 */
@SpringBootTest
@TestPropertySource(properties = {
        "wenxin.api.key=test-api-key",
        "wenxin.api.base-url=https://test-url.com",
        "wenxin.api.model=test-model"
})
class LangChain4jConfigTest {

    @Autowired
    @Qualifier("planningChatModel")
    private ChatLanguageModel planningChatModel;

    @Autowired
    @Qualifier("executionChatModel")
    private ChatLanguageModel executionChatModel;

    @Test
    void testPlanningChatModel_ShouldBeCreated() {
        assertNotNull(planningChatModel, "Planning chat model should be created");
    }

    @Test
    void testExecutionChatModel_ShouldBeCreated() {
        assertNotNull(executionChatModel, "Execution chat model should be created");
    }

    @Test
    void testBothModels_ShouldBeDifferentInstances() {
        assertNotSame(planningChatModel, executionChatModel,
                "Planning and execution models should be different instances");
    }
}
