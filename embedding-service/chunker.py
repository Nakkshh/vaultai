from typing import List, Dict
from tree_sitter_languages import get_language, get_parser

SUPPORTED_LANGUAGES = {
    "python": "python",
    "javascript": "javascript",
    "typescript": "typescript",
    "java": "java",
    "go": "go",
    "rust": "rust",
}

CHUNK_NODE_TYPES = {
    "python": {"function_definition", "class_definition"},
    "javascript": {"function_declaration", "class_declaration",
                   "arrow_function", "method_definition"},
    "typescript": {"function_declaration", "class_declaration",
                   "method_definition", "arrow_function"},
    "java": {"method_declaration", "class_declaration",
             "constructor_declaration"},
    "go": {"function_declaration", "method_declaration"},
    "rust": {"function_item", "impl_item"},
}

MAX_CHUNK_CHARS = 1500
MIN_CHUNK_CHARS = 10


def chunk_code(content: str, language: str) -> List[Dict]:
    if language in SUPPORTED_LANGUAGES:
        chunks = _ast_chunk(content, language)
        if chunks:
            return chunks
    return _fallback_chunk(content)


def _ast_chunk(content: str, language: str) -> List[Dict]:
    try:
        parser = get_parser(SUPPORTED_LANGUAGES[language])
        tree = parser.parse(bytes(content, "utf8"))
        root = tree.root_node
        target_types = CHUNK_NODE_TYPES.get(language, set())
        chunks = []
        lines = content.split("\n")
        _extract_nodes(root, target_types, lines, chunks)
        if not chunks:
            return _fallback_chunk(content)
        return chunks
    except Exception:
        return _fallback_chunk(content)


def _extract_nodes(node, target_types: set, lines: List[str], chunks: List[Dict]):
    if node.type in target_types:
        start_line = node.start_point[0]
        end_line = node.end_point[0]
        chunk_text = "\n".join(lines[start_line:end_line + 1])
        if len(chunk_text) < MIN_CHUNK_CHARS:
            return
        if len(chunk_text) > MAX_CHUNK_CHARS:
            chunks.extend(_split_large_chunk(chunk_text, start_line))
        else:
            chunks.append({"text": chunk_text, "start_line": start_line, "end_line": end_line})
        return
    for child in node.children:
        _extract_nodes(child, target_types, lines, chunks)


def _split_large_chunk(text: str, base_line: int) -> List[Dict]:
    lines = text.split("\n")
    chunks = []
    current_lines = []
    current_chars = 0
    start_line = base_line
    for i, line in enumerate(lines):
        current_lines.append(line)
        current_chars += len(line)
        if current_chars >= MAX_CHUNK_CHARS:
            chunks.append({"text": "\n".join(current_lines),
                           "start_line": start_line, "end_line": base_line + i})
            current_lines = []
            current_chars = 0
            start_line = base_line + i + 1
    if current_lines and current_chars >= MIN_CHUNK_CHARS:
        chunks.append({"text": "\n".join(current_lines),
                       "start_line": start_line, "end_line": base_line + len(lines) - 1})
    return chunks


def _fallback_chunk(content: str) -> List[Dict]:
    lines = content.split("\n")
    chunks = []
    current_lines = []
    current_chars = 0
    start_line = 0
    for i, line in enumerate(lines):
        current_lines.append(line)
        current_chars += len(line)
        if current_chars >= MAX_CHUNK_CHARS:
            text = "\n".join(current_lines)
            if len(text) >= MIN_CHUNK_CHARS:
                chunks.append({"text": text, "start_line": start_line, "end_line": i})
            current_lines = []
            current_chars = 0
            start_line = i + 1
    if current_lines:
        text = "\n".join(current_lines)
        if len(text) >= MIN_CHUNK_CHARS:
            chunks.append({"text": text, "start_line": start_line, "end_line": len(lines) - 1})
    return chunks
