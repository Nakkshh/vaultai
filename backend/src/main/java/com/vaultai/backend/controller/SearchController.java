package com.vaultai.backend.controller;

import com.vaultai.backend.entity.User;
import com.vaultai.backend.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.*;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;
    private final StringRedisTemplate redisTemplate;

    private static final int RATE_LIMIT = 60;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    @PostMapping
    public ResponseEntity<?> search(@AuthenticationPrincipal User user,
                                    @RequestBody Map<String, Object> body) {
        String rateLimitKey = "vaultai:rate:" + user.getId();
        Long count = redisTemplate.opsForValue().increment(rateLimitKey);
        if (count == 1) redisTemplate.expire(rateLimitKey, WINDOW);
        if (count > RATE_LIMIT) {
            return ResponseEntity.status(429).body(
                    Map.of("error", "Rate limit exceeded. 60 searches per minute allowed.")
            );
        }

        String query = body.getOrDefault("query", "").toString().trim();
        if (query.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Query cannot be empty"));
        }

        // Optional single repo filter
        Object repoIdObj = body.get("repo_id");
        Long filterRepoId = repoIdObj != null
                ? Long.valueOf(repoIdObj.toString()) : null;

        List<Map<String, Object>> results = searchService.search(
                user, query, 10, filterRepoId);

        return ResponseEntity.ok(Map.of(
                "query", query,
                "count", results.size(),
                "results", results
        ));
    }
}