import os
import uuid
from typing import List, Dict
from qdrant_client import QdrantClient
from qdrant_client.models import (
    Distance, VectorParams, PointStruct,
    Filter, FieldCondition, MatchValue
)
from dotenv import load_dotenv

load_dotenv()

COLLECTION_NAME = "vaultai_chunks"
VECTOR_SIZE = 3072


def get_client() -> QdrantClient:
    return QdrantClient(
        host=os.getenv("QDRANT_HOST", "localhost"),
        port=int(os.getenv("QDRANT_PORT", 6335))
    )


def ensure_collection():
    client = get_client()
    names = [c.name for c in client.get_collections().collections]
    if COLLECTION_NAME not in names:
        client.create_collection(
            collection_name=COLLECTION_NAME,
            vectors_config=VectorParams(size=VECTOR_SIZE, distance=Distance.COSINE)
        )
        print(f"Created Qdrant collection: {COLLECTION_NAME}")
    else:
        print(f"Qdrant collection already exists: {COLLECTION_NAME}")


def upsert_chunks(chunks: List[Dict], embeddings: List[List[float]],
                  repo_id: int, file_id: int, file_path: str) -> List[str]:
    client = get_client()
    points = []
    point_ids = []
    for chunk, embedding in zip(chunks, embeddings):
        point_id = str(uuid.uuid4())
        point_ids.append(point_id)
        points.append(PointStruct(
            id=point_id,
            vector=embedding,
            payload={
                "repo_id": repo_id,
                "file_id": file_id,
                "file_path": file_path,
                "chunk_text": chunk["text"],
                "start_line": chunk["start_line"],
                "end_line": chunk["end_line"],
            }
        ))
    if points:
        client.upsert(collection_name=COLLECTION_NAME, points=points)
    return point_ids

def delete_points_by_file(file_id: int):
    client = get_client()
    client.delete(
        collection_name=COLLECTION_NAME,
        points_selector=Filter(
            must=[
                FieldCondition(
                    key="file_id",
                    match=MatchValue(value=file_id)
                )
            ]
        )
    )