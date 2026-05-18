package com.vaultai.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "repositories")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Repository {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String githubRepoId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String fullName;

    private String defaultBranch;
    private String language;
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IndexStatus indexStatus;

    private LocalDateTime lastIndexedAt;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum IndexStatus {
        PENDING, QUEUED, PROCESSING, COMPLETED, FAILED
    }
}