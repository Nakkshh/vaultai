package com.vaultai.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class GitHubService {

    @Value("${app.github.api-base}")
    private String apiBase;

    private final RestTemplate restTemplate;

    private HttpHeaders headers(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.set("Accept", "application/vnd.github.v3+json");
        return h;
    }

    public List<Map<String, Object>> getUserRepos(String accessToken) {
        String url = apiBase + "/user/repos?per_page=100&sort=updated&type=owner";
        ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                url, HttpMethod.GET,
                new HttpEntity<>(headers(accessToken)),
                new ParameterizedTypeReference<>() {}
        );
        return response.getBody() != null ? response.getBody() : List.of();
    }

    public List<Map<String, Object>> getRepoTree(String accessToken,
                                                   String fullName,
                                                   String branch) {
        String url = apiBase + "/repos/" + fullName + "/git/trees/" + branch + "?recursive=1";
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET,
                    new HttpEntity<>(headers(accessToken)),
                    new ParameterizedTypeReference<>() {}
            );
            Map<String, Object> body = response.getBody();
            if (body != null && body.containsKey("tree")) {
                return (List<Map<String, Object>>) body.get("tree");
            }
        } catch (Exception e) {
            log.error("Failed to fetch repo tree for {}: {}", fullName, e.getMessage());
        }
        return List.of();
    }

    public String getFileContent(String accessToken, String fullName, String path) {
        String url = apiBase + "/repos/" + fullName + "/contents/" + path;
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET,
                    new HttpEntity<>(headers(accessToken)),
                    new ParameterizedTypeReference<>() {}
            );
            Map<String, Object> body = response.getBody();
            if (body != null && "base64".equals(body.get("encoding"))) {
                String encoded = ((String) body.get("content")).replaceAll("\\s", "");
                return new String(Base64.getDecoder().decode(encoded));
            }
        } catch (Exception e) {
            log.warn("Could not fetch file {}: {}", path, e.getMessage());
        }
        return null;
    }
}