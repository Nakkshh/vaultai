# VaultAI — Semantic Code & Document Search Platform

A production-grade semantic search engine for codebases. Connect your GitHub repositories and search them in natural language.

🔗 **Live Demo:** [vaultai-prod.vercel.app](https://vaultai-prod.vercel.app)

---

## Architecture
Next.js (Vercel) → Spring Boot Gateway (Render) → FastAPI Embedding Service (HF Spaces)
↓                                    ↓
NeonDB (PostgreSQL)                  Qdrant Cloud (Vector DB)
Upstash Redis                        Celery Worker (Render)

---

## Tech Stack

| Layer | Technology |
|---|---|
| API Gateway | Java 17, Spring Boot 3.5, Spring Security, JWT |
| AI / Embedding | Python, FastAPI, OpenAI text-embedding-3-small |
| Search | Qdrant Cloud, BM25, cross-encoder re-ranking (ms-marco-MiniLM-L-6-v2) |
| Async Indexing | Redis Queue, Celery Workers |
| Database | PostgreSQL (NeonDB) |
| Frontend | Next.js, TypeScript, Tailwind CSS |
| Observability | Prometheus, Grafana |
| CI/CD | GitHub Actions |

---

## Key Features

- **GitHub OAuth 2.0** — login and repo ingestion via GitHub API
- **AST-aware chunking** — tree-sitter splits code at function and class boundaries
- **Hybrid search** — BM25 keyword + Qdrant vector search merged via Reciprocal Rank Fusion
- **Cross-encoder re-ranking** — ms-marco-MiniLM-L-6-v2 for precision improvement
- **Incremental re-indexing** — SHA-based diff detection, only changed files re-embedded
- **Redis caching** — 90%+ cache hit rate, sub-50ms repeated query latency
- **Search analytics** — real-time dashboard tracking latency, cache hit rate, top queries
- **API key management** — scoped keys with SHA-256 hashing
- **Rate limiting** — Redis sliding window, 60 requests/min per user
- **Observability** — Prometheus metrics + Grafana dashboards

---

## Production Deployment

| Service | Platform | URL |
|---|---|---|
| Frontend | Vercel | [vaultai-prod.vercel.app](https://vaultai-prod.vercel.app) |
| Backend | Render | — |
| Embedding Service | HF Spaces | — |
| Worker | Render Background Worker | — |
| Database | NeonDB | — |
| Redis | Upstash | — |
| Vector DB | Qdrant Cloud | — |

---

## Local Setup

### Prerequisites
- Docker Desktop
- Java 17
- Python 3.11
- Node.js 18+

### 1. Clone the repo

```bash
git clone https://github.com/Nakkshh/vaultai.git
cd vaultai
```

### 2. Start infrastructure

```bash
docker compose up -d
```

Starts: Redis (6381), Qdrant (6335), Prometheus (9092), Grafana (3006)

### 3. Backend

Create `backend/.env`:
DATABASE_URL=your_neondb_url
GITHUB_CLIENT_ID=your_github_client_id
GITHUB_CLIENT_SECRET=your_github_client_secret
JWT_SECRET=your_jwt_secret

```bash
cd backend
export $(cat .env | xargs)     # Windows: set each var manually
./mvnw spring-boot:run
```

Runs on port **8085**

### 4. Embedding Service

Create `embedding-service/.env`:
OPENAI_API_KEY=your_openai_key
QDRANT_HOST=localhost
QDRANT_PORT=6335
QDRANT_API_KEY=
DATABASE_URL=your_neondb_url
REDIS_HOST=localhost
REDIS_PORT=6381
REDIS_PASSWORD=

```bash
cd embedding-service
python -m venv venv
venv\Scripts\activate          # Mac/Linux: source venv/bin/activate
pip install -r requirements.txt
uvicorn main:app --port 8002 --reload
```

Runs on port **8002**

### 5. Worker

```bash
cd embedding-service
venv\Scripts\activate
python worker.py
```

### 6. Frontend

```bash
cd frontend
npm install
npm run dev
```

Runs on port **3005**

---

## Local Ports

| Service | Port |
|---|---|
| Spring Boot | 8085 |
| FastAPI | 8002 |
| Next.js | 3005 |
| Redis | 6381 |
| Qdrant | 6335 |
| Prometheus | 9092 |
| Grafana | 3006 |

---

## Observability

- **Prometheus** → `http://localhost:9092` — scrapes Spring Boot and FastAPI metrics
- **Grafana** → `http://localhost:3006` (admin/admin) — dashboards for API latency, request rate, JVM memory

---

## CI/CD

GitHub Actions runs on every push to `main`:

| Job | What it does |
|---|---|
| backend | Compiles Spring Boot with Java 17, skips tests |
| embedding | Syntax checks all Python modules |

---

## Database Schema
users           — GitHub OAuth user profiles
repositories    — connected GitHub repos with index status
repo_files      — raw file content with SHA for diff detection
chunks          — AST-chunked code with Qdrant point IDs
search_events   — query logs for analytics (latency, cache hit)
api_keys        — hashed API keys per user

---

## How Search Works

User types query
Spring Boot receives request, checks Redis cache
Cache miss → calls FastAPI /search endpoint
FastAPI embeds query via OpenAI
Qdrant vector search → top 50 candidates
BM25 keyword search over candidates
RRF merges both result lists
Cross-encoder re-ranks top 20 → returns top 5
Results cached in Redis (5 min TTL)
Results returned to user


---

## How Indexing Works

User connects GitHub repo
Spring Boot fetches repo file tree via GitHub API
Files saved to NeonDB
Indexing job pushed to Redis queue
Worker picks up job
tree-sitter AST chunking per file
OpenAI generates embeddings per chunk
Chunks stored in Qdrant Cloud
Metadata saved to NeonDB
Repo status updated to COMPLETED
