package com.vaultai.backend.repository;

import com.vaultai.backend.entity.RepoFile;
import com.vaultai.backend.entity.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface RepoFileRepository extends JpaRepository<RepoFile, Long> {
    List<RepoFile> findByRepository(Repository repository);
    Optional<RepoFile> findByRepositoryAndPath(Repository repository, String path);
    long countByRepository(Repository repository);
}