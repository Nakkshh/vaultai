package com.vaultai.backend.repository;

import com.vaultai.backend.entity.Chunk;
import com.vaultai.backend.entity.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ChunkRepository extends JpaRepository<Chunk, Long> {
    List<Chunk> findByRepository(Repository repository);
    long countByRepository(Repository repository);
    void deleteByRepository(Repository repository);
}