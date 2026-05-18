package com.vaultai.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "repo_files")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepoFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repo_id", nullable = false)
    private Repository repository;

    @Column(nullable = false)
    private String path;

    private String sha;
    private String language;

    @Column(columnDefinition = "TEXT")
    private String rawContent;

    private Long fileSize;
    private Boolean indexed;
    private LocalDateTime indexedAt;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        indexed = false;
    }
}