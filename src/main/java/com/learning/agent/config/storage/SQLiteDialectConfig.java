package com.learning.agent.config.storage;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.relational.core.dialect.AnsiDialect;
import org.springframework.data.relational.core.dialect.Dialect;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;

/**
 * SQLite 数据库方言配置
 * Spring Data JDBC 不原生支持 SQLite，需要手动配置方言
 */
@Configuration
public class SQLiteDialectConfig {
    /**
     * 配置 SQLite 使用 ANSI SQL 方言
     * SQLite 基本兼容 ANSI SQL，所以使用 AnsiDialect 即可
     */
    @Bean
    public Dialect jdbcDialect(NamedParameterJdbcOperations operations) {
        return AnsiDialect.INSTANCE;
    }
}
