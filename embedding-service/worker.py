import os
import json
import redis
import logging
import requests
import psycopg2
from dotenv import load_dotenv

from chunker import chunk_code
from embedder import generate_embeddings
from qdrant_manager import ensure_collection, upsert_chunks, delete_points_by_file
from db import (get_files_for_repo, save_chunk, mark_file_indexed,
                update_repo_status, get_connection, get_file_shas_for_repo,
                delete_chunks_for_file, update_file_content,
                insert_new_file, get_file_id_by_path)

load_dotenv()

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

REDIS_HOST = os.getenv("REDIS_HOST", "localhost")
REDIS_PORT = int(os.getenv("REDIS_PORT", 6381))
REDIS_PASSWORD = os.getenv("REDIS_PASSWORD", None)
GITHUB_API = "https://api.github.com"
INDEX_QUEUE = "vaultai:index_queue"

r = redis.Redis(
    host=REDIS_HOST,
    port=REDIS_PORT,
    password=REDIS_PASSWORD,
    decode_responses=True,
    ssl=True if REDIS_PASSWORD else False
)

SUPPORTED_EXTENSIONS = {
    ".java", ".py", ".js", ".ts", ".jsx", ".tsx",
    ".go", ".rs", ".cpp", ".c", ".h", ".cs",
    ".md", ".txt", ".yaml", ".yml", ".json"
}

def is_supported(path: str) -> bool:
    return any(path.endswith(ext) for ext in SUPPORTED_EXTENSIONS)

def detect_language(path: str) -> str:
    if path.endswith(".java"): return "java"
    if path.endswith(".py"): return "python"
    if path.endswith((".js", ".jsx")): return "javascript"
    if path.endswith((".ts", ".tsx")): return "typescript"
    if path.endswith(".go"): return "go"
    if path.endswith(".rs"): return "rust"
    if path.endswith(".md"): return "markdown"
    return "text"

def github_headers(access_token: str) -> dict:
    return {
        "Authorization": f"Bearer {access_token}",
        "Accept": "application/vnd.github.v3+json"
    }

def fetch_repo_tree(full_name: str, branch: str, access_token: str) -> list:
    url = f"{GITHUB_API}/repos/{full_name}/git/trees/{branch}?recursive=1"
    resp = requests.get(url, headers=github_headers(access_token))
    if resp.status_code == 200:
        return resp.json().get("tree", [])
    return []

def fetch_file_content(full_name: str, path: str, access_token: str):
    import base64
    url = f"{GITHUB_API}/repos/{full_name}/contents/{path}"
    resp = requests.get(url, headers=github_headers(access_token))
    if resp.status_code == 200:
        data = resp.json()
        if data.get("encoding") == "base64":
            return base64.b64decode(data["content"]).decode("utf-8", errors="ignore")
    return None

def index_file(conn, file_id: int, repo_id: int,
               path: str, content: str, language: str):
    chunks = chunk_code(content, language)
    if not chunks:
        return 0
    texts = [c["text"] for c in chunks]
    embeddings = generate_embeddings(texts)
    point_ids = upsert_chunks(chunks, embeddings, repo_id, file_id, path)
    for chunk, point_id in zip(chunks, point_ids):
        save_chunk(conn, file_id, repo_id,
                   chunk["text"], chunk["start_line"],
                   chunk["end_line"], point_id)
    mark_file_indexed(conn, file_id)
    return len(chunks)


def process_repo(repo_id: int, full_name: str):
    logger.info(f"Starting indexing for repo: {full_name} (id={repo_id})")
    update_repo_status(repo_id, "PROCESSING")
    ensure_collection()

    files = get_files_for_repo(repo_id)
    logger.info(f"Found {len(files)} files to index")

    if not files:
        update_repo_status(repo_id, "COMPLETED")
        return

    total_chunks = 0
    conn = get_connection()

    try:
        for file in files:
            file_id = file["id"]
            path = file["path"]
            language = file["language"] or "text"
            content = file["raw_content"]

            if not content or not content.strip():
                continue

            n = index_file(conn, file_id, repo_id, path, content, language)
            conn.commit()
            total_chunks += n
            logger.info(f"  {path}: {n} chunks")

        update_repo_status(repo_id, "COMPLETED")
        logger.info(f"Completed {full_name}: {total_chunks} total chunks")

    except Exception as e:
        logger.error(f"Indexing failed for {full_name}: {e}")
        update_repo_status(repo_id, "FAILED")
        conn.rollback()
    finally:
        conn.close()


def process_refresh(repo_id: int, full_name: str,
                    branch: str, access_token: str):
    logger.info(f"Refreshing repo: {full_name}")
    update_repo_status(repo_id, "PROCESSING")
    ensure_collection()

    # Get current file SHAs from DB
    existing_shas = get_file_shas_for_repo(repo_id)

    # Get latest tree from GitHub
    tree = fetch_repo_tree(full_name, branch, access_token)

    new_chunks = 0
    conn = get_connection()

    try:
        for item in tree:
            if item.get("type") != "blob":
                continue
            path = item.get("path", "")
            sha = item.get("sha", "")
            size = item.get("size", 0)

            if not is_supported(path):
                continue
            if size > 100_000:
                continue

            if path in existing_shas:
                if existing_shas[path] == sha:
                    continue  # unchanged — skip

                # File changed — re-index
                logger.info(f"  Changed: {path}")
                file_id = get_file_id_by_path(conn, repo_id, path)
                if not file_id:
                    continue

                content = fetch_file_content(full_name, path, access_token)
                if not content:
                    continue

                # Delete old chunks
                delete_chunks_for_file(conn, file_id)
                delete_points_by_file(file_id)

                # Update file content
                update_file_content(conn, file_id, sha, content)
                conn.commit()

                # Re-index
                language = detect_language(path)
                n = index_file(conn, file_id, repo_id, path, content, language)
                conn.commit()
                new_chunks += n

            else:
                # New file — add and index
                logger.info(f"  New file: {path}")
                content = fetch_file_content(full_name, path, access_token)
                if not content:
                    continue

                language = detect_language(path)
                file_id = insert_new_file(
                    conn, repo_id, path, sha, language, content, size
                )
                conn.commit()

                n = index_file(conn, file_id, repo_id, path, content, language)
                conn.commit()
                new_chunks += n

        update_repo_status(repo_id, "COMPLETED")
        logger.info(f"Refresh complete for {full_name}: {new_chunks} new/updated chunks")

    except Exception as e:
        logger.error(f"Refresh failed for {full_name}: {e}")
        update_repo_status(repo_id, "FAILED")
        conn.rollback()
    finally:
        conn.close()


def process_task(payload: str):
    task = json.loads(payload)
    repo_id = task.get("repo_id")
    full_name = task.get("full_name")
    task_type = task.get("task_type")

    if task_type == "index_repo":
        process_repo(repo_id, full_name)
    elif task_type == "refresh_repo":
        process_refresh(
            repo_id,
            full_name,
            task.get("branch", "main"),
            task.get("access_token", "")
        )
    else:
        logger.warning(f"Unknown task type: {task_type}")


def run():
    logger.info(f"Worker listening on {INDEX_QUEUE}...")
    while True:
        result = r.brpop(INDEX_QUEUE, timeout=5)
        if result:
            _, payload = result
            try:
                process_task(payload)
            except Exception as e:
                logger.error(f"Task failed: {e}")


if __name__ == "__main__":
    run()