-- 用户表
CREATE TABLE IF NOT EXISTS users (
    id TEXT PRIMARY KEY,
    email TEXT UNIQUE NOT NULL,
    password TEXT NOT NULL,
    name TEXT,
    learner_id TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建邮箱索引
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);

-- 创建 learner_id 索引
CREATE INDEX IF NOT EXISTS idx_users_learner_id ON users(learner_id);
