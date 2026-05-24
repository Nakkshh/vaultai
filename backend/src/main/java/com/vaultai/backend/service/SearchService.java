package com.vaultai.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaultai.backend.entity.SearchEvent;
import com.vaultai.backend.entity.User;
import com.vaultai.backend.repository.RepositoryRepo;
import com.vaultai.backend.repository.SearchEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class SearchService {

    private final RestTemplate restTemplate;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RepositoryRepo repositoryRepo;
    private final SearchEventRepository searchEventRepository;

    @Value("${app.embedding-service-url:http://localhost:8002}")
    private String embeddingServiceUrl;

    private static final String CACHE_PREFIX = "vaultai:search:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    public List<Map<String, Object>> search(User user, String query,
                                         int topK, Long filterRepoId) {
        long start = System.currentTimeMillis();

        List<Long> repoIds = repositoryRepo.findByUser(user)
                .stream()
                .filter(r -> "COMPLETED".equals(r.getIndexStatus().name()))
                .map(r -> r.getId())
                .filter(id -> filterRepoId == null || id.equals(filterRepoId))
                .toList();

        if (repoIds.isEmpty()) return List.of();

        String cacheKey = CACHE_PREFIX + user.getId() + ":"
                + (filterRepoId != null ? filterRepoId + ":" : "")
                + query.toLowerCase().trim().hashCode();

        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                List<Map<String, Object>> results = objectMapper.readValue(cached,
                        objectMapper.getTypeFactory()
                                .constructCollectionType(List.class, Map.class));
                logEvent(user, query, System.currentTimeMillis() - start,
                        results.size(), true);
                return results;
            } catch (Exception e) {
                log.warn("Cache parse error: {}", e.getMessage());
            }
        }

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("query", query);
        requestBody.put("repo_ids", repoIds);
        requestBody.put("top_k", topK);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    embeddingServiceUrl + "/search",
                    HttpMethod.POST,
                    new HttpEntity<>(requestBody, headers),
                    new ParameterizedTypeReference<>() {}
            );

            Map<String, Object> body = response.getBody();
            List<Map<String, Object>> results = body != null
                    ? (List<Map<String, Object>>) body.get("results")
                    : List.of();

            try {
                redisTemplate.opsForValue().set(
                        cacheKey,
                        objectMapper.writeValueAsString(results),
                        CACHE_TTL
                );
            } catch (Exception e) {
                log.warn("Cache write error: {}", e.getMessage());
            }

            long latency = System.currentTimeMillis() - start;
            logEvent(user, query, latency, results.size(), false);
            return results;

        } catch (Exception e) {
            log.error("Search failed: {}", e.getMessage());
            return List.of();
        }
    }

    @Async
    public void logEvent(User user, String query, long latencyMs,
                          int resultCount, boolean cacheHit) {
        try {
            searchEventRepository.save(SearchEvent.builder()
                    .user(user)
                    .query(query)
                    .latencyMs(latencyMs)
                    .resultCount(resultCount)
                    .cacheHit(cacheHit)
                    .build());
        } catch (Exception e) {
            log.warn("Failed to log search event: {}", e.getMessage());
        }
    }
}
