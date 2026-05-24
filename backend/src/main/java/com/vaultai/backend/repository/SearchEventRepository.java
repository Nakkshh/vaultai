package com.vaultai.backend.repository;

import com.vaultai.backend.entity.SearchEvent;
import com.vaultai.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface SearchEventRepository extends JpaRepository<SearchEvent, Long> {

    List<SearchEvent> findByUserOrderByCreatedAtDesc(User user);

    @Query("SELECT AVG(e.latencyMs) FROM SearchEvent e WHERE e.user = :user")
    Double avgLatency(@Param("user") User user);

    @Query("SELECT COUNT(e) FROM SearchEvent e WHERE e.user = :user AND e.cacheHit = true")
    Long cacheHitCount(@Param("user") User user);

    @Query("SELECT COUNT(e) FROM SearchEvent e WHERE e.user = :user")
    Long totalCount(@Param("user") User user);

    @Query("SELECT e.query, COUNT(e) as cnt FROM SearchEvent e WHERE e.user = :user " +
           "GROUP BY e.query ORDER BY cnt DESC")
    List<Object[]> topQueries(@Param("user") User user);

    @Query("SELECT e FROM SearchEvent e WHERE e.user = :user AND e.createdAt >= :since ORDER BY e.createdAt ASC")
    List<SearchEvent> findRecentEvents(@Param("user") User user, @Param("since") LocalDateTime since);
}
