from sentence_transformers import CrossEncoder
from typing import List, Dict
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

_model = None

def get_model():
    global _model
    if _model is None:
        logger.info("Loading cross-encoder model...")
        _model = CrossEncoder("cross-encoder/ms-marco-MiniLM-L-6-v2")
        logger.info("Cross-encoder model loaded.")
    return _model

def rerank(query: str, candidates: List[Dict], top_k: int = 5) -> List[Dict]:
    if not candidates:
        return []

    model = get_model()

    pairs = [[query, c["chunk_text"]] for c in candidates]

    try:
        scores = model.predict(pairs)
    except Exception as e:
        logger.error(f"Reranking failed: {e}")
        return candidates[:top_k]

    for i, candidate in enumerate(candidates):
        candidate["rerank_score"] = float(scores[i])

    reranked = sorted(candidates, key=lambda x: x["rerank_score"], reverse=True)

    return reranked[:top_k]