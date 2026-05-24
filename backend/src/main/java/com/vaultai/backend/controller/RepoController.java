package com.vaultai.backend.controller;

import com.vaultai.backend.entity.*;
import com.vaultai.backend.service.RepoService;
import com.vaultai.backend.service.TaskQueueService;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/repos")
@RequiredArgsConstructor
public class RepoController {

    private final RepoService repoService;
    private final TaskQueueService taskQueueService;

    // List all GitHub repos of logged-in user
    @GetMapping("/available")
    public ResponseEntity<?> availableRepos(@AuthenticationPrincipal User user) {
        List<Map<String, Object>> repos = repoService.listUserRepos(user);
        return ResponseEntity.ok(repos);
    }

    // List connected (indexed) repos
    @GetMapping
    public ResponseEntity<?> connectedRepos(@AuthenticationPrincipal User user) {
        List<Repository> repos = repoService.getConnectedRepos(user);
        List<Map<String, Object>> result = repos.stream().map(r -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", r.getId());
            map.put("name", r.getName());
            map.put("fullName", r.getFullName());
            map.put("indexStatus", r.getIndexStatus().name());
            map.put("language", r.getLanguage() != null ? r.getLanguage() : "");
            map.put("description", r.getDescription() != null ? r.getDescription() : "");
            return map;
        }).collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(result);
    }

    // Connect a repo and trigger indexing
    @PostMapping("/connect")
    public ResponseEntity<?> connectRepo(@AuthenticationPrincipal User user,
                                          @RequestBody Map<String, String> body) {
        Repository repo = repoService.connectRepo(
                user,
                body.get("githubRepoId"),
                body.get("name"),
                body.get("fullName"),
                body.get("defaultBranch"),
                body.get("language"),
                body.get("description")
        );
        return ResponseEntity.ok(Map.of(
                "id", repo.getId(),
                "name", repo.getName(),
                "indexStatus", repo.getIndexStatus().name()
        ));
    }

    // Get index status of a repo
    @GetMapping("/{id}/status")
    public ResponseEntity<?> repoStatus(@AuthenticationPrincipal User user,
                                        @PathVariable Long id) {
        return repoService.getConnectedRepos(user).stream()
                .filter(r -> r.getId().equals(id))
                .findFirst()
                .map(r -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("indexStatus", r.getIndexStatus().name());
                    map.put("lastIndexedAt", r.getLastIndexedAt() != null
                            ? r.getLastIndexedAt().toString() : "");
                    return ResponseEntity.ok(map);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/refresh")
    public ResponseEntity<?> refreshRepo(@AuthenticationPrincipal User user,
                                        @PathVariable Long id) {
        return repoService.getConnectedRepos(user).stream()
                .filter(r -> r.getId().equals(id))
                .findFirst()
                .map(repo -> {
                    taskQueueService.pushRefreshJob(
                            repo.getId(),
                            repo.getFullName(),
                            repo.getDefaultBranch(),
                            user.getGithubAccessToken()
                    );
                    return ResponseEntity.ok(Map.of(
                            "message", "Refresh queued",
                            "repoId", repo.getId()
                    ));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
