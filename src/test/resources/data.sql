-- 插入测试用户数据
-- 注意：密码是 BCrypt 加密后的 "test123"
-- 原始密码: test123
-- BCrypt Hash: $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy

-- 测试用户1 - 普通用户
INSERT OR IGNORE INTO users (id, email, password, name, learner_id, created_at, updated_at) 
VALUES (
    'test-user-001',
    'test@example.com',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    '测试用户',
    'learner-001',
    datetime('now'),
    datetime('now')
);

-- 测试用户2 - 管理员
INSERT OR IGNORE INTO users (id, email, password, name, learner_id, created_at, updated_at) 
VALUES (
    'test-admin-001',
    'admin@example.com',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    '管理员',
    'learner-admin-001',
    datetime('now'),
    datetime('now')
);

-- 测试用户3 - Sophie (与Notion测试页面对应)
INSERT OR IGNORE INTO users (id, email, password, name, learner_id, created_at, updated_at) 
VALUES (
    'test-sophie-001',
    'sophie@example.com',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    'Sophie',
    'learner-sophie-001',
    datetime('now'),
    datetime('now')
);
