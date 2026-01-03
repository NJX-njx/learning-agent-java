package com.learning.agent;

/**
 * 测试用户常量
 * 包含预置在数据库中的测试用户信息
 */
public final class TestUsers {

    private TestUsers() {
        // 工具类，禁止实例化
    }

    /**
     * 测试用户1 - 普通用户
     */
    public static final class TestUser {
        public static final String ID = "test-user-001";
        public static final String EMAIL = "test@example.com";
        public static final String PASSWORD = "test123"; // 原始密码
        public static final String NAME = "测试用户";
        public static final String LEARNER_ID = "learner-001";
    }

    /**
     * 测试用户2 - 管理员
     */
    public static final class Admin {
        public static final String ID = "test-admin-001";
        public static final String EMAIL = "admin@example.com";
        public static final String PASSWORD = "test123"; // 原始密码
        public static final String NAME = "管理员";
        public static final String LEARNER_ID = "learner-admin-001";
    }

    /**
     * 测试用户3 - Sophie (与Notion测试页面对应)
     */
    public static final class Sophie {
        public static final String ID = "test-sophie-001";
        public static final String EMAIL = "sophie@example.com";
        public static final String PASSWORD = "test123"; // 原始密码
        public static final String NAME = "Sophie";
        public static final String LEARNER_ID = "learner-sophie-001";
    }

    /**
     * 所有测试用户的共同密码 (BCrypt Hash)
     */
    public static final String PASSWORD_HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
}
