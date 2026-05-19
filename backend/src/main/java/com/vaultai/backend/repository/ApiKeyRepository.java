package com.vaultai.backend.repository;

import com.vaultai.backend.entity.ApiKey;
import com.vaultai.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {
    List<ApiKey> findByUser(User user);
    Optional<ApiKey> findByKeyHash(String keyHash);
}