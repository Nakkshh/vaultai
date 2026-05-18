package com.vaultai.backend.repository;

import com.vaultai.backend.entity.Repository;
import com.vaultai.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface RepositoryRepo extends JpaRepository<Repository, Long> {
    List<Repository> findByUser(User user);
    Optional<Repository> findByGithubRepoIdAndUser(String githubRepoId, User user);
    boolean existsByGithubRepoIdAndUser(String githubRepoId, User user);
}