import os
import psycopg2
import psycopg2.extras
from dotenv import load_dotenv

load_dotenv()

def get_connection():
    return psycopg2.connect(os.getenv("DATABASE_URL"))

def get_files_for_repo(repo_id: int):
    conn = get_connection()
    try:
        with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            cur.execute("""
                SELECT id, path, language, raw_content, sha
                FROM repo_files
                WHERE repo_id = %s AND indexed = false AND raw_content IS NOT NULL
            """, (repo_id,))
            return cur.fetchall()
    finally:
        conn.close()

def save_chunk(conn, file_id: int, repo_id: int, chunk_text: str,
               start_line: int, end_line: int, qdrant_point_id: str):
    with conn.cursor() as cur:
        cur.execute("""
            INSERT INTO chunks (file_id, repo_id, chunk_text, start_line, end_line, qdrant_point_id)
            VALUES (%s, %s, %s, %s, %s, %s)
        """, (file_id, repo_id, chunk_text, start_line, end_line, qdrant_point_id))

def mark_file_indexed(conn, file_id: int):
    with conn.cursor() as cur:
        cur.execute("""
            UPDATE repo_files SET indexed = true, indexed_at = NOW()
            WHERE id = %s
        """, (file_id,))

def update_repo_status(repo_id: int, status: str):
    conn = get_connection()
    try:
        with conn.cursor() as cur:
            cur.execute("""
                UPDATE repositories
                SET index_status = %s,
                    last_indexed_at = CASE WHEN %s = 'COMPLETED' THEN NOW() ELSE last_indexed_at END
                WHERE id = %s
            """, (status, status, repo_id))
        conn.commit()
    finally:
        conn.close()

def get_file_shas_for_repo(repo_id: int) -> dict:
    """Returns {path: sha} for all indexed files in a repo."""
    conn = get_connection()
    try:
        with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            cur.execute("""
                SELECT path, sha FROM repo_files
                WHERE repo_id = %s
            """, (repo_id,))
            rows = cur.fetchall()
            return {row["path"]: row["sha"] for row in rows}
    finally:
        conn.close()

def delete_chunks_for_file(conn, file_id: int):
    with conn.cursor() as cur:
        cur.execute("DELETE FROM chunks WHERE file_id = %s", (file_id,))

def update_file_content(conn, file_id: int, sha: str, content: str):
    with conn.cursor() as cur:
        cur.execute("""
            UPDATE repo_files
            SET sha = %s, raw_content = %s, indexed = false, indexed_at = NULL
            WHERE id = %s
        """, (sha, content, file_id))

def insert_new_file(conn, repo_id: int, path: str, sha: str,
                    language: str, content: str, size: int) -> int:
    with conn.cursor() as cur:
        cur.execute("""
            INSERT INTO repo_files (repo_id, path, sha, language, raw_content, file_size, indexed)
            VALUES (%s, %s, %s, %s, %s, %s, false)
            RETURNING id
        """, (repo_id, path, sha, language, content, size))
        return cur.fetchone()[0]

def get_file_id_by_path(conn, repo_id: int, path: str) -> int:
    with conn.cursor() as cur:
        cur.execute("""
            SELECT id FROM repo_files WHERE repo_id = %s AND path = %s
        """, (repo_id, path))
        row = cur.fetchone()
        return row[0] if row else None