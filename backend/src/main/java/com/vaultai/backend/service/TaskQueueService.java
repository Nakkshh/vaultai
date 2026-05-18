package com.vaultai.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class TaskQueueService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String INDEX_QUEUE = "vaultai:index_queue";

    public void pushIndexingJob(Long repoId, String fullName,
                                 String branch, String accessToken) {
        try {
            Map<String, Object> task = Map.of(
                    "repo_id", repoId,
                    "full_name", fullName,
                    "branch", branch,
                    "access_token", accessToken,
                    "task_type", "index_repo"
            );
            String payload = objectMapper.writeValueAsString(task);
            redisTemplate.opsForList().leftPush(INDEX_QUEUE, payload);
            log.info("Pushed indexing job for repo: {}", fullName);
        } catch (Exception e) {
            log.error("Failed to push indexing job: {}", e.getMessage());
        }
    }

    public void pushRefreshJob(Long repoId, String fullName,
                            String branch, String accessToken) {
        try {
            Map<String, Object> task = Map.of(
                    "repo_id", repoId,
                    "full_name", fullName,
                    "branch", branch,
                    "access_token", accessToken,
                    "task_type", "refresh_repo"
            );
            String payload = objectMapper.writeValueAsString(task);
            redisTemplate.opsForList().leftPush(INDEX_QUEUE, payload);
            log.info("Pushed refresh job for repo: {}", fullName);
        } catch (Exception e) {
            log.error("Failed to push refresh job: {}", e.getMessage());
        }
    }
}