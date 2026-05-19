package com.vaultai.backend.controller;

import com.vaultai.backend.entity.ApiKey;
import com.vaultai.backend.entity.User;
import com.vaultai.backend.repository.ApiKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/keys")
@RequiredArgsConstructor
public class ApiKeyController {

    private final ApiKeyRepository apiKeyRepository;

    @PostMapping
    public ResponseEntity<?> createKey(@AuthenticationPrincipal User user,
                                        @RequestBody Map<String, String> body) {
        String name = body.getOrDefault("name", "My API Key");
        String rawKey = "vai_" + UUID.randomUUID().toString().replace("-", "");
        String hash = sha256(rawKey);

        apiKeyRepository.save(ApiKey.builder()
                .user(user)
                .keyHash(hash)
                .name(name)
                .build());

        return ResponseEntity.ok(Map.of(
                "key", rawKey,
                "name", name,
                "message", "Save this key — it won't be shown again."
        ));
    }

    @GetMapping
    public ResponseEntity<?> listKeys(@AuthenticationPrincipal User user) {
        List<Map<String, Object>> keys = apiKeyRepository.findByUser(user)
                .stream()
                .map(k -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", k.getId());
                    m.put("name", k.getName());
                    m.put("createdAt", k.getCreatedAt().toString());
                    m.put("lastUsedAt", k.getLastUsedAt() != null
                            ? k.getLastUsedAt().toString() : "Never");
                    return m;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(keys);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteKey(@AuthenticationPrincipal User user,
                                        @PathVariable Long id) {
        apiKeyRepository.findById(id).ifPresent(key -> {
            if (key.getUser().getId().equals(user.getId())) {
                apiKeyRepository.delete(key);
            }
        });
        return ResponseEntity.ok(Map.of("message", "Key deleted"));
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 failed", e);
        }
    }
}