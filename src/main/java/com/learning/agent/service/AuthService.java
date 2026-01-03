package com.learning.agent.service;

import com.learning.agent.dto.web.AuthResponse;
import com.learning.agent.dto.web.LoginRequest;
import com.learning.agent.dto.web.RegisterRequest;
import com.learning.agent.entity.User;
import com.learning.agent.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * 用户认证服务
 */
@Slf4j
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 用户注册
     */
    @Transactional
    @SuppressWarnings("null")
    public AuthResponse register(RegisterRequest request) {
        if (request.getEmail() == null || request.getEmail().isEmpty()) {
            return AuthResponse.error("Email is required");
        }
        if (request.getPassword() == null || request.getPassword().isEmpty()) {
            return AuthResponse.error("Password is required");
        }

        // 验证密码强度
        if (request.getPassword().length() < 6) {
            return AuthResponse.error("Password must be at least 6 characters");
        }

        // 检查邮箱是否已存在
        if (userRepository.existsByEmail(request.getEmail())) {
            return AuthResponse.error("User already exists");
        }

        // 创建新用户，密码使用 BCrypt 加密
        LocalDateTime now = LocalDateTime.now();
        User newUser = User.builder()
                .id(UUID.randomUUID().toString())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .name(request.getName())
                .learnerId(UUID.randomUUID().toString())
                .createdAt(now)
                .updatedAt(now)
                .build();

        newUser = userRepository.save(newUser);

        log.info("New user registered: {}", newUser.getEmail());

        return AuthResponse.success(newUser.getId(), newUser.getEmail(), newUser.getName(), newUser.getLearnerId());
    }

    /**
     * 用户登录
     */
    public AuthResponse login(LoginRequest request) {
        if (request.getEmail() == null || request.getEmail().isEmpty()) {
            return AuthResponse.error("Email is required");
        }
        if (request.getPassword() == null || request.getPassword().isEmpty()) {
            return AuthResponse.error("Password is required");
        }

        Optional<User> userOpt = userRepository.findByEmail(request.getEmail());
        if (userOpt.isEmpty()) {
            return AuthResponse.error("Invalid credentials");
        }

        User user = userOpt.get();

        // 使用 BCrypt 验证密码
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            return AuthResponse.error("Invalid credentials");
        }

        log.info("User logged in: {}", request.getEmail());

        return AuthResponse.success(user.getId(), user.getEmail(), user.getName(), user.getLearnerId());
    }
}
