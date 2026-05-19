package com.vaultai.backend.controller;

import com.vaultai.backend.entity.SearchEvent;
import com.vaultai.backend.entity.User;
import com.vaultai.backend.repository.SearchEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final SearchEventRepository searchEventRepository;

    @GetMapping("/summary")
    public ResponseEntity<?> summary(@AuthenticationPrincipal User user) {
        long total = searchEventRepository.totalCount(user);
        Double avgLatency = searchEventRepository.avgLatency(user);
        long cacheHits = searchEventRepository.cacheHitCount(user);

        double cacheHitRate = total > 0
                ? Math.round((double) cacheHits / total * 1000.0) / 10.0
                : 0.0;

        List<Object[]> topQueriesRaw = searchEventRepository.topQueries(user);
        List<Map<String, Object>> topQueries = topQueriesRaw.stream()
                .limit(5)
                .map(row -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("query", row[0]);
                    m.put("count", row[1]);
                    return m;
                })
                .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("totalSearches", total);
        result.put("avgLatencyMs", avgLatency != null ? Math.round(avgLatency) : 0);
        result.put("cacheHitRate", cacheHitRate);
        result.put("topQueries", topQueries);

        return ResponseEntity.ok(result);
    }

    @GetMapping("/timeline")
    public ResponseEntity<?> timeline(@AuthenticationPrincipal User user) {
        LocalDateTime since = LocalDateTime.now().minusDays(7);
        List<SearchEvent> events = searchEventRepository.findRecentEvents(user, since);

        Map<String, Long> byDay = new LinkedHashMap<>();
        for (SearchEvent e : events) {
            String day = e.getCreatedAt().toLocalDate().toString();
            byDay.merge(day, 1L, Long::sum);
        }

        List<Map<String, Object>> timeline = byDay.entrySet().stream()
                .map(entry -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("date", entry.getKey());
                    m.put("count", entry.getValue());
                    return m;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of("timeline", timeline));
    }
}