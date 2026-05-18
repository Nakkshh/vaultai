package com.vaultai.backend.controller;

import com.vaultai.backend.entity.User;
import com.vaultai.backend.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @PostMapping
    public ResponseEntity<?> search(@AuthenticationPrincipal User user,
                                     @RequestBody Map<String, String> body) {
        String query = body.getOrDefault("query", "").trim();
        if (query.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Query cannot be empty"));
        }

        int topK = 10;
        List<Map<String, Object>> results = searchService.search(user, query, topK);

        return ResponseEntity.ok(Map.of(
                "query", query,
                "count", results.size(),
                "results", results
        ));
    }
}