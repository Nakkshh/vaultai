import os
from typing import List, Dict
from qdrant_client import QdrantClient
from qdrant_client.models import Filter, FieldCondition, MatchAny
from rank_bm25 import BM25Okapi
from embedder import generate_embeddings
from dotenv import load_dotenv

load_dotenv()

COLLECTION_NAME = "vaultai_chunks"
QDRANT_HOST = os.getenv("QDRANT_HOST", "localhost")
QDRANT_PORT = int(os.getenv("QDRANT_PORT", 6335))


def get_qdrant_client():
    return QdrantClient(host=QDRANT_HOST, port=QDRANT_PORT)


def hybrid_search(query: str, repo_ids: List[int], top_k: int = 10) -> List[Dict]:
    client = get_qdrant_client()

    query_embedding = generate_embeddings([query])[0]

    qdrant_filter = Filter(
        must=[
            FieldCondition(
                key="repo_id",
                match=MatchAny(any=repo_ids)
            )
        ]
    )

    vector_results = client.query_points(
        collection_name=COLLECTION_NAME,
        query=query_embedding,
        query_filter=qdrant_filter,
        limit=50,
        with_payload=True
    ).points

    if not vector_results:
        return []

    candidates = []
    for hit in vector_results:
        candidates.append({
            "chunk_text": hit.payload.get("chunk_text", ""),
            "file_path": hit.payload.get("file_path", ""),
            "repo_id": hit.payload.get("repo_id", 0),
            "start_line": hit.payload.get("start_line", 0),
            "end_line": hit.payload.get("end_line", 0),
            "vector_score": hit.score,
            "vector_rank": 0
        })

    for i, c in enumerate(candidates):
        c["vector_rank"] = i + 1

    tokenized_corpus = [c["chunk_text"].lower().split() for c in candidates]
    tokenized_query = query.lower().split()

    bm25 = BM25Okapi(tokenized_corpus)
    bm25_scores = bm25.get_scores(tokenized_query)

    bm25_ranked = sorted(
        enumerate(bm25_scores),
        key=lambda x: x[1],
        reverse=True
    )
    bm25_rank_map = {idx: rank + 1 for rank, (idx, _) in enumerate(bm25_ranked)}

    k = 60
    for i, candidate in enumerate(candidates):
        vector_rank = candidate["vector_rank"]
        bm25_rank = bm25_rank_map.get(i, len(candidates))
        rrf_score = (1 / (k + vector_rank)) + (1 / (k + bm25_rank))
        candidate["rrf_score"] = rrf_score

    candidates.sort(key=lambda x: x["rrf_score"], reverse=True)

    results = []
    for c in candidates[:top_k]:
        results.append({
            "chunk_text": c["chunk_text"],
            "file_path": c["file_path"],
            "repo_id": c["repo_id"],
            "start_line": c["start_line"],
            "end_line": c["end_line"],
            "score": round(c["rrf_score"], 6)
        })

    return results


def build_bm25_index(texts: List[str]) -> BM25Okapi:
    tokenized = [t.lower().split() for t in texts]
    return BM25Okapi(tokenized)