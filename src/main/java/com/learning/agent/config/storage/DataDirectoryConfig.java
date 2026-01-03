package com.learning.agent.config.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 数据目录配置
 * 使用 BeanFactoryPostProcessor 确保数据库目录在数据源初始化之前创建
 */
@Configuration
public class DataDirectoryConfig {

    private static final Logger log = LoggerFactory.getLogger(DataDirectoryConfig.class);

    /**
     * BeanFactoryPostProcessor 在所有 bean 定义加载后、实例化前执行
     * 这确保了目录在 DataSource 创建之前就已经存在
     */
    @Bean
    public static BeanFactoryPostProcessor dataDirectoryInitializer() {
        return beanFactory -> {
            Environment env = beanFactory.getBean(Environment.class);
            String dataDir = env.getProperty("data.dir", "data");
            String uploadDir = env.getProperty("upload.dir", "uploads");

            createDirectoryIfNotExists(dataDir, "数据库目录");
            createDirectoryIfNotExists(uploadDir, "上传目录");
        };
    }

    private static void createDirectoryIfNotExists(String dirPath, String description) {
        Path path = Paths.get(dirPath);
        if (!Files.exists(path)) {
            try {
                Files.createDirectories(path);
                log.info("{}已创建: {}", description, path.toAbsolutePath());
            } catch (IOException e) {
                log.error("无法创建{}: {}", description, path.toAbsolutePath(), e);
                throw new RuntimeException("无法创建" + description + ": " + dirPath, e);
            }
        } else {
            log.debug("{}已存在: {}", description, path.toAbsolutePath());
        }
    }
}
