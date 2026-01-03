package com.learning.agent.repository;

import com.learning.agent.entity.User;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 用户数据仓库
 * 使用 Spring Data JDBC + SQLite 持久化存储
 */
@Repository
public interface UserRepository extends CrudRepository<User, String> {

    /**
     * 根据邮箱查找用户
     */
    @Query("SELECT * FROM users WHERE email = :email")
    Optional<User> findByEmail(@Param("email") String email);

    /**
     * 根据 learnerId 查找用户
     */
    @Query("SELECT * FROM users WHERE learner_id = :learnerId")
    Optional<User> findByLearnerId(@Param("learnerId") String learnerId);

    /**
     * 检查邮箱是否已存在
     */
    @Query("SELECT COUNT(*) > 0 FROM users WHERE email = :email")
    boolean existsByEmail(@Param("email") String email);
}
