import os
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from typing import List, Optional
from dotenv import load_dotenv

from embedder import generate_embeddings
from searcher import hybrid_search
from reranker import rerank

load_dotenv()

app = FastAPI(title="VaultAI Embedding Service")

from prometheus_fastapi_instrumentator import Instrumentator
Instrumentator().instrument(app).expose(app)


@app.on_event("startup")
def startup_event():
    from reranker import get_model
    get_model()


@app.get("/health")
def health():
    return {"status": "ok"}


class EmbedRequest(BaseModel):
    texts: List[str]

class EmbedResponse(BaseModel):
    embeddings: List[List[float]]

class SearchRequest(BaseModel):
    query: str
    repo_ids: List[int]
    top_k: Optional[int] = 10
    rerank_top_k: Optional[int] = 5

class SearchResult(BaseModel):
    chunk_text: str
    file_path: str
    repo_id: int
    start_line: int
    end_line: int
    score: float

class SearchResponse(BaseModel):
    results: List[SearchResult]


@app.post("/embed", response_model=EmbedResponse)
def embed(request: EmbedRequest):
    if not request.texts:
        raise HTTPException(status_code=400, detail="No texts provided")
    embeddings = generate_embeddings(request.texts)
    return EmbedResponse(embeddings=embeddings)


@app.post("/search", response_model=SearchResponse)
def search(request: SearchRequest):
    if not request.query.strip():
        raise HTTPException(status_code=400, detail="Empty query")

    # Step 1: Hybrid search — get top 20
    candidates = hybrid_search(
        query=request.query,
        repo_ids=request.repo_ids,
        top_k=20
    )

    if not candidates:
        return SearchResponse(results=[])

    # Step 2: Cross-encoder rerank — return top 5
    reranked = rerank(
        query=request.query,
        candidates=candidates,
        top_k=request.rerank_top_k
    )

    results = [
        SearchResult(
            chunk_text=r["chunk_text"],
            file_path=r["file_path"],
            repo_id=r["repo_id"],
            start_line=r["start_line"],
            end_line=r["end_line"],
            score=round(r["rerank_score"], 6)
        )
        for r in reranked
    ]

    return SearchResponse(results=results)
