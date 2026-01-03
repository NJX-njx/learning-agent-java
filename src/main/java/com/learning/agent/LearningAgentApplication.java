package com.learning.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Learning Agent 应用入口类
 * 智能学习助手后端服务
 */
@SpringBootApplication
@EnableAsync
public class LearningAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(LearningAgentApplication.class, args);
    }
}
