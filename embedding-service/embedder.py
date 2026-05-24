import urllib.request
import json
import os
import time
from typing import List
from dotenv import load_dotenv

load_dotenv()

GOOGLE_API_KEY = os.getenv("GOOGLE_API_KEY")
EMBEDDING_MODEL = "models/gemini-embedding-001"


def generate_embeddings(texts: List[str]) -> List[List[float]]:
    all_embeddings = []
    for text in texts:
        text = text.replace("\n", " ").strip()
        if not text:
            all_embeddings.append([0.0] * 3072)
            continue
        try:
            url = f"https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-001:embedContent?key={GOOGLE_API_KEY}"
            payload = json.dumps({
                "model": EMBEDDING_MODEL,
                "content": {"parts": [{"text": text}]}
            }).encode()
            req = urllib.request.Request(url, data=payload,
                                         headers={"Content-Type": "application/json"})
            with urllib.request.urlopen(req) as r:
                data = json.loads(r.read())
                all_embeddings.append(data["embedding"]["values"])
            time.sleep(0.05)
        except Exception as e:
            print(f"Embedding error: {e}")
            all_embeddings.append([0.0] * 3072)
    return all_embeddings


def get_embedding_dimension() -> int:
    return 3072
