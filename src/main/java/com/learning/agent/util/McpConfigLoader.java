package com.learning.agent.util;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 配置加载器
 * 解析 mcp-config.jsonc 文件
 */
@Slf4j
@Component
public class McpConfigLoader {

    private final ObjectMapper objectMapper;
    private final Environment environment;
    private final ResourceLoader resourceLoader;

    @Value("${mcp.config.path:mcp-config.jsonc}")
    private String configPath;

    private Map<String, McpServerConfig> serverConfigs;

    public McpConfigLoader(ObjectMapper objectMapper, Environment environment, ResourceLoader resourceLoader) {
        this.objectMapper = objectMapper;
        this.environment = environment;
        this.resourceLoader = resourceLoader;
        // 启用 JSON 注释支持，用于解析 JSONC 文件
        this.objectMapper.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
    }

    @PostConstruct
    public void init() {
        loadConfig();
    }

    public McpServerConfig getServerConfig(String serverName) {
        if (serverConfigs == null) {
            loadConfig();
        }

        McpServerConfig config = serverConfigs.get(serverName);
        if (config == null) {
            throw new RuntimeException("未在 MCP 配置中找到服务器：" + serverName);
        }
        return config;
    }

    private void loadConfig() {
        serverConfigs = new HashMap<>();

        try {
            InputStream inputStream = null;
            
            // 1. 尝试从 classpath 加载（适用于测试环境）
            try {
                Resource resource = resourceLoader.getResource("classpath:" + configPath);
                if (resource.exists()) {
                    inputStream = resource.getInputStream();
                    log.debug("Loading MCP config from classpath: {}", configPath);
                }
            } catch (Exception e) {
                log.debug("Could not load from classpath: {}", configPath);
            }
            
            // 2. 如果 classpath 不存在，尝试从文件系统加载
            if (inputStream == null) {
                Path path = Path.of(configPath);
                if (!Files.exists(path)) {
                    // 尝试在当前工作目录查找
                    path = Path.of(System.getProperty("user.dir"), configPath);
                }
                if (Files.exists(path)) {
                    inputStream = Files.newInputStream(path);
                    log.debug("Loading MCP config from file system: {}", path);
                }
            }
            
            if (inputStream == null) {
                log.warn("MCP config file not found: {}", configPath);
                return;
            }

            Map<String, Object> parsed = objectMapper.readValue(inputStream,
                    new TypeReference<>() {
                    });

            @SuppressWarnings("unchecked")
            Map<String, Object> mcpServers = (Map<String, Object>) parsed.get("mcpServers");
            if (mcpServers == null) {
                throw new RuntimeException("配置文件缺少 mcpServers 字段");
            }

            for (Map.Entry<String, Object> entry : mcpServers.entrySet()) {
                String name = entry.getKey();
                @SuppressWarnings("unchecked")
                Map<String, Object> value = (Map<String, Object>) entry.getValue();

                McpServerConfig config = parseServerConfig(value);
                serverConfigs.put(name, config);
            }

            log.info("Loaded {} MCP server configs", serverConfigs.size());

        } catch (IOException e) {
            log.error("Failed to load MCP config", e);
            throw new RuntimeException("Failed to load MCP config", e);
        }
    }

    @SuppressWarnings({ "unchecked", "null" })
    private McpServerConfig parseServerConfig(Map<String, Object> value) {
        String url = (String) value.get("url");
        String command = (String) value.get("command");
        List<String> args = (List<String>) value.get("args");
        Map<String, String> env = (Map<String, String>) value.get("env");
        String workingDirectory = (String) value.get("workingDirectory");
        Integer timeoutSeconds = value.get("timeoutSeconds") instanceof Number n ? n.intValue() : null;

        // 处理环境变量替换
        if (env != null) {
            Map<String, String> processedEnv = new HashMap<>();
            for (Map.Entry<String, String> envEntry : env.entrySet()) {
                String envValue = envEntry.getValue();
                if (envValue != null && envValue.startsWith("${") && envValue.endsWith("}")) {
                    // 支持 ${VAR_NAME} 和 ${VAR_NAME:default} 两种格式
                    String content = envValue.substring(2, envValue.length() - 1);
                    String varName;
                    String defaultValue = "";

                    int colonIndex = content.indexOf(':');
                    if (colonIndex > 0) {
                        varName = content.substring(0, colonIndex);
                        defaultValue = content.substring(colonIndex + 1);
                    } else {
                        varName = content;
                    }

                    // 优先从 Spring Environment 读取（支持 .env 文件），其次从系统环境变量读取
                    String resolved = environment.getProperty(varName);
                    if (resolved == null || resolved.isEmpty()) {
                        resolved = System.getenv(varName);
                        if (resolved == null) {
                            resolved = "";
                        }
                    }
                    if (resolved.isEmpty()) {
                        if (defaultValue.isEmpty()) {
                            log.warn("Environment variable {} referenced but not set", varName);
                        } else {
                            log.debug("Environment variable {} not set, using default: {}", varName, defaultValue);
                        }
                        resolved = defaultValue;
                    }
                    processedEnv.put(envEntry.getKey(), resolved);
                } else {
                    processedEnv.put(envEntry.getKey(), envValue);
                }
            }
            env = processedEnv;
        }

        return new McpServerConfig(url, command, args, env, workingDirectory, timeoutSeconds);
    }

    /**
     * MCP 服务器配置记录
     */
    public record McpServerConfig(
            String url,
            String command,
            List<String> args,
            Map<String, String> env,
            String workingDirectory,
            Integer timeoutSeconds) {
        public boolean isHttpType() {
            return url != null && !url.isEmpty();
        }

        public boolean isCommandType() {
            return command != null && !command.isEmpty();
        }
    }
}
