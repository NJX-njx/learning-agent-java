package com.learning.agent.dto.web;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 认证响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private boolean success;
    private UserInfo user;
    private String error;
    private String message; // 兼容前端错误处理

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserInfo {
        private String id;
        private String email;
        private String name;
        private String learnerId;
    }

    public static AuthResponse success(String id, String email, String name, String learnerId) {
        return AuthResponse.builder()
                .success(true)
                .user(UserInfo.builder()
                        .id(id)
                        .email(email)
                        .name(name)
                        .learnerId(learnerId)
                        .build())
                .build();
    }

    public static AuthResponse error(String errorMessage) {
        return AuthResponse.builder()
                .success(false)
                .error(errorMessage)
                .message(errorMessage)
                .build();
    }
}
