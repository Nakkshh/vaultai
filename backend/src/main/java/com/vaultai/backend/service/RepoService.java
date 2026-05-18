package com.vaultai.backend.service;

import com.vaultai.backend.entity.*;
import com.vaultai.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class RepoService {

    private final RepositoryRepo repositoryRepo;
    private final RepoFileRepository repoFileRepository;
    private final GitHubService gitHubService;
    private final TaskQueueService taskQueueService;

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            ".java", ".py", ".js", ".ts", ".jsx", ".tsx",
            ".go", ".rs", ".cpp", ".c", ".h", ".cs",
            ".md", ".txt", ".yaml", ".yml", ".json"
    );

    public List<Map<String, Object>> listUserRepos(User user) {
        return gitHubService.getUserRepos(user.getGithubAccessToken());
    }

    public List<Repository> getConnectedRepos(User user) {
        return repositoryRepo.findByUser(user);
    }

    @Transactional
    public Repository connectRepo(User user, String githubRepoId,
                                String name, String fullName,
                                String defaultBranch, String language,
                                String description) {

        if (repositoryRepo.existsByGithubRepoIdAndUser(githubRepoId, user)) {
            return repositoryRepo
                    .findByGithubRepoIdAndUser(githubRepoId, user)
                    .orElseThrow();
        }

        Repository repo = Repository.builder()
                .user(user)
                .githubRepoId(githubRepoId)
                .name(name)
                .fullName(fullName)
                .defaultBranch(defaultBranch != null ? defaultBranch : "main")
                .language(language)
                .description(description)
                .indexStatus(Repository.IndexStatus.PENDING)
                .build();

        repo = repositoryRepo.save(repo);

        // Fetch files
        fetchAndStoreFiles(user, repo);

        // Now update to QUEUED after files are saved
        repo.setIndexStatus(Repository.IndexStatus.QUEUED);
        repo = repositoryRepo.save(repo);

        // Push to Celery queue
        taskQueueService.pushIndexingJob(
                repo.getId(),
                repo.getFullName(),
                repo.getDefaultBranch(),
                user.getGithubAccessToken()
        );

        return repo;
    }

    @Transactional
    private void fetchAndStoreFiles(User user, Repository repo) {
        List<Map<String, Object>> tree = gitHubService.getRepoTree(
                user.getGithubAccessToken(),
                repo.getFullName(),
                repo.getDefaultBranch()
        );

        int saved = 0;
        for (Map<String, Object> item : tree) {
            String type = (String) item.get("type");
            String path = (String) item.get("path");
            String sha = (String) item.get("sha");

            if (!"blob".equals(type)) continue;
            if (!isSupportedFile(path)) continue;

            Object sizeObj = item.get("size");
            long size = sizeObj != null ? ((Number) sizeObj).longValue() : 0;
            if (size > 100_000) continue; // skip files > 100KB

            String content = gitHubService.getFileContent(
                    user.getGithubAccessToken(),
                    repo.getFullName(),
                    path
            );

            if (content == null) continue;

            RepoFile file = RepoFile.builder()
                    .repository(repo)
                    .path(path)
                    .sha(sha)
                    .language(detectLanguage(path))
                    .rawContent(content)
                    .fileSize(size)
                    .build();

            repoFileRepository.save(file);
            saved++;
        }

        log.info("Saved {} files for repo {}", saved, repo.getFullName());
    }

    private boolean isSupportedFile(String path) {
        return SUPPORTED_EXTENSIONS.stream().anyMatch(path::endsWith);
    }

    private String detectLanguage(String path) {
        if (path.endsWith(".java")) return "java";
        if (path.endsWith(".py")) return "python";
        if (path.endsWith(".js") || path.endsWith(".jsx")) return "javascript";
        if (path.endsWith(".ts") || path.endsWith(".tsx")) return "typescript";
        if (path.endsWith(".go")) return "go";
        if (path.endsWith(".rs")) return "rust";
        if (path.endsWith(".md")) return "markdown";
        return "text";
    }
}