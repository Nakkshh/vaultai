# VaultAI — Semantic Code & Document Search Platform

A production-grade semantic search engine for codebases and developer docs.
Connect your GitHub repos and search them in natural language.

## Architecture

```
Next.js (Vercel) → Spring Boot API Gateway (Render) → FastAPI Embedding Service (Render)
                                  ↓                              ↓
                           NeonDB (PostgreSQL)            Qdrant (Vector DB)
                           Redis (Upstash)                Celery Workers
```

## Tech Stack

| Layer | Technology |
|---|---|
| API Gateway | Java 21, Spring Boot 3.2, Spring Security |
| AI/Embedding | Python, FastAPI, sentence-transformers |
| Search | Qdrant vector DB, BM25, cross-encoder re-ranking |
| Async Workers | Celery, Redis |
| Database | PostgreSQL (NeonDB) |
| Frontend | Next.js 14, TypeScript, Tailwind |
| Observability | Prometheus, Grafana |
| CI/CD | GitHub Actions |

## Key Features

- GitHub OAuth + repo ingestion via GitHub API
- AST-aware code chunking using tree-sitter
- Hybrid BM25 + vector search with RRF merging
- Cross-encoder re-ranking (ms-marco-MiniLM-L-6-v2)
- Incremental diff-based re-indexing
- Redis query result caching
- Search analytics dashboard
- Scoped API key management
- Rate limiting (60 req/min sliding window)
- Prometheus + Grafana observability

## Local Setup

```bash
# Infra
docker compose up -d

# Backend
cd backend && export $(cat .env | xargs) && ./mvnw spring-boot:run

# Embedding service
cd embedding-service && source venv/bin/activate && uvicorn main:app --port 8002 --reload

# Worker
cd embedding-service && python worker.py

# Frontend
cd frontend && npm run dev
```

## Ports

| Service | Port |
|---|---|
| Spring Boot | 8085 |
| FastAPI | 8002 |
| Next.js | 3005 |
| Redis | 6381 |
| Qdrant | 6335 |
| Prometheus | 9092 |
| Grafana | 3006 |