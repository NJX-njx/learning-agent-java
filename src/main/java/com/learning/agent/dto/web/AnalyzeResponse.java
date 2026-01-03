package com.learning.agent.dto.web;

import com.learning.agent.dto.client.NotionCreatedPage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 分析响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyzeResponse {
    private boolean success;
    private AnalyzeData data;
    private String error;
    private String message; // 兼容前端错误处理

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnalyzeData {
        private List<String> pageIds;
        private List<NotionCreatedPage> createdPages;
        private List<String> contents;
        private List<Step> steps;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Step {
        private String title;
        private String status;
        private String duration;
        private String details;
    }

    public static AnalyzeResponse success(AnalyzeData data) {
        return AnalyzeResponse.builder()
                .success(true)
                .data(data)
                .build();
    }

    public static AnalyzeResponse error(String errorMessage) {
        return AnalyzeResponse.builder()
                .success(false)
                .error(errorMessage)
                .message(errorMessage)
                .build();
    }
}
