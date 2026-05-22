import os
import uuid
from typing import List, Dict
from qdrant_client import QdrantClient
from qdrant_client.models import (
    Distance, VectorParams, PointStruct,
    Filter, FieldCondition, MatchValue,
    PayloadSchemaType
)
from dotenv import load_dotenv

load_dotenv()

COLLECTION_NAME = "vaultai_chunks"
VECTOR_SIZE = 3072


def get_client() -> QdrantClient:
    api_key = os.getenv("QDRANT_API_KEY")
    host = os.getenv("QDRANT_HOST", "localhost")
    port = int(os.getenv("QDRANT_PORT", 6335))

    if api_key:
        return QdrantClient(
            host=host,
            port=port,
            api_key=api_key,
            https=True,
            check_compatibility=False
        )
    return QdrantClient(host=host, port=port, check_compatibility=False)


def ensure_collection():
    client = get_client()
    collections = client.get_collections().collections
    names = [c.name for c in collections]

    if COLLECTION_NAME not in names:
        client.create_collection(
            collection_name=COLLECTION_NAME,
            vectors_config=VectorParams(
                size=VECTOR_SIZE,
                distance=Distance.COSINE
            )
        )
        print(f"Created Qdrant collection: {COLLECTION_NAME}")
    else:
        print(f"Qdrant collection already exists: {COLLECTION_NAME}")

    # Always ensure payload indexes exist
    try:
        client.create_payload_index(
            collection_name=COLLECTION_NAME,
            field_name="repo_id",
            field_schema=PayloadSchemaType.INTEGER
        )
    except Exception:
        pass

    try:
        client.create_payload_index(
            collection_name=COLLECTION_NAME,
            field_name="file_id",
            field_schema=PayloadSchemaType.INTEGER
        )
    except Exception:
        pass


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
        client.upsert(
            collection_name=COLLECTION_NAME,
            points=points
        )

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