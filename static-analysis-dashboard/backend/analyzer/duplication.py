"""Code Duplication Detector (Copy-Paste Detector)."""

import hashlib
from typing import Dict, Any, List, Set, Tuple


def normalize_line(line: str) -> str:
    """Normalize code line by removing whitespace, comments, and trivial punctuation."""
    s = line.strip()
    if not s:
        return ""
    # Strip comments
    if s.startswith(("//", "#", "--", "/*", "*")):
        return ""
    # Ignore standalone brackets/keywords
    if s in ["{", "}", "(", ")", "[", "]", ";", "pass", "return", "break", "continue"]:
        return ""
    # Remove internal spaces around operators
    s = "".join(s.split())
    return s


def hash_block(lines: List[str]) -> str:
    """Hash a list of normalized lines."""
    joined = "\n".join(lines)
    return hashlib.md5(joined.encode("utf-8")).hexdigest()


def detect_duplicates(
    files_content: Dict[str, str], min_block_lines: int = 5
) -> Dict[str, Any]:
    """
    Detect duplicate code blocks across multiple files using a sliding-window rolling hash.
    Returns duplicated blocks, total duplicated lines, and duplicate file pairs.
    """
    # 1. Preprocess all files into normalized line entries
    file_normalized: Dict[str, List[Tuple[int, str, str]]] = {}
    for path, content in files_content.items():
        raw_lines = content.splitlines()
        norm_entries = []
        for line_no, raw in enumerate(raw_lines, start=1):
            norm = normalize_line(raw)
            if norm:
                norm_entries.append((line_no, norm, raw))
        if len(norm_entries) >= min_block_lines:
            file_normalized[path] = norm_entries

    # 2. Build hash map of sliding window blocks
    # hash -> list of (file_path, start_index, end_index, raw_snippet)
    block_map: Dict[str, List[Tuple[str, int, int, str]]] = {}

    for path, entries in file_normalized.items():
        count = len(entries)
        for i in range(count - min_block_lines + 1):
            window = entries[i : i + min_block_lines]
            norm_tokens = [w[1] for w in window]
            b_hash = hash_block(norm_tokens)

            start_line = window[0][0]
            end_line = window[-1][0]
            raw_snippet = "\n".join(w[2] for w in window)

            if b_hash not in block_map:
                block_map[b_hash] = []
            block_map[b_hash].append((path, start_line, end_line, raw_snippet))

    # 3. Filter for hashes that appeared more than once across different files or non-overlapping ranges
    duplicate_instances: List[Dict[str, Any]] = []
    duplicated_lines_by_file: Dict[str, Set[int]] = {}

    for b_hash, occurrences in block_map.items():
        if len(occurrences) > 1:
            # Check if occurrences are not just immediate overlaps in the same file
            first = occurrences[0]
            for other in occurrences[1:]:
                # Ignore self-overlap in exact same line range
                if first[0] == other[0] and abs(first[1] - other[1]) < min_block_lines:
                    continue

                duplicate_instances.append({
                    "file_a": first[0],
                    "line_a_start": first[1],
                    "line_a_end": first[2],
                    "file_b": other[0],
                    "line_b_start": other[1],
                    "line_b_end": other[2],
                    "lines_count": min_block_lines,
                    "snippet": first[3][:250] + ("..." if len(first[3]) > 250 else ""),
                })

                # Track duplicated line numbers for line counting
                for f_path, s_l, e_l in [(first[0], first[1], first[2]), (other[0], other[1], other[2])]:
                    if f_path not in duplicated_lines_by_file:
                        duplicated_lines_by_file[f_path] = set()
                    for l_no in range(s_l, e_l + 1):
                        duplicated_lines_by_file[f_path].add(l_no)

    # Calculate total unique duplicated lines
    total_duplicated_lines = sum(len(lines) for lines in duplicated_lines_by_file.values())

    # Limit to top duplicate blocks for clean UI representation
    unique_pairs = []
    seen_pairs = set()
    for d in duplicate_instances:
        pair_key = tuple(sorted([f"{d['file_a']}:{d['line_a_start']}", f"{d['file_b']}:{d['line_b_start']}"]))
        if pair_key not in seen_pairs:
            seen_pairs.add(pair_key)
            unique_pairs.append(d)

    return {
        "total_duplicated_lines": total_duplicated_lines,
        "duplicate_blocks_count": len(unique_pairs),
        "clones": unique_pairs[:50],  # Return top 50 clone blocks
    }
