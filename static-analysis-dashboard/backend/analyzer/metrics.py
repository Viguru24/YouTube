"""Code metrics module: SLOC, Physical lines, Blank lines, and Comment Density."""

import os
from pathlib import Path
from typing import Dict, Any, List, Tuple

# Mapping of file extensions to language name and comment styles
LANGUAGE_CONFIGS: Dict[str, Dict[str, Any]] = {
    # Python
    ".py": {
        "language": "Python",
        "single": ["#"],
        "multi_start": ['"""', "'''"],
        "multi_end": ['"""', "'''"],
    },
    ".pyw": {
        "language": "Python",
        "single": ["#"],
        "multi_start": ['"""', "'''"],
        "multi_end": ['"""', "'''"],
    },
    # JavaScript & TypeScript
    ".js": {"language": "JavaScript", "single": ["//"], "multi_start": ["/*"], "multi_end": ["*/"]},
    ".mjs": {"language": "JavaScript", "single": ["//"], "multi_start": ["/*"], "multi_end": ["*/"]},
    ".cjs": {"language": "JavaScript", "single": ["//"], "multi_start": ["/*"], "multi_end": ["*/"]},
    ".jsx": {"language": "JavaScript (JSX)", "single": ["//"], "multi_start": ["/*"], "multi_end": ["*/"]},
    ".ts": {"language": "TypeScript", "single": ["//"], "multi_start": ["/*"], "multi_end": ["*/"]},
    ".tsx": {"language": "TypeScript (TSX)", "single": ["//"], "multi_start": ["/*"], "multi_end": ["*/"]},
    # Kotlin & Java
    ".kt": {"language": "Kotlin", "single": ["//"], "multi_start": ["/*"], "multi_end": ["*/"]},
    ".kts": {"language": "Kotlin Script", "single": ["//"], "multi_start": ["/*"], "multi_end": ["*/"]},
    ".java": {"language": "Java", "single": ["//"], "multi_start": ["/*"], "multi_end": ["*/"]},
    # C-family
    ".c": {"language": "C", "single": ["//"], "multi_start": ["/*"], "multi_end": ["*/"]},
    ".h": {"language": "C/C++ Header", "single": ["//"], "multi_start": ["/*"], "multi_end": ["*/"]},
    ".cpp": {"language": "C++", "single": ["//"], "multi_start": ["/*"], "multi_end": ["*/"]},
    ".cc": {"language": "C++", "single": ["//"], "multi_start": ["/*"], "multi_end": ["*/"]},
    ".cxx": {"language": "C++", "single": ["//"], "multi_start": ["/*"], "multi_end": ["*/"]},
    ".hpp": {"language": "C++ Header", "single": ["//"], "multi_start": ["/*"], "multi_end": ["*/"]},
    ".cs": {"language": "C#", "single": ["//"], "multi_start": ["/*"], "multi_end": ["*/"]},
    # Go & Rust
    ".go": {"language": "Go", "single": ["//"], "multi_start": ["/*"], "multi_end": ["*/"]},
    ".rs": {"language": "Rust", "single": ["//"], "multi_start": ["/*"], "multi_end": ["*/"]},
    # Swift & Objective-C
    ".swift": {"language": "Swift", "single": ["//"], "multi_start": ["/*"], "multi_end": ["*/"]},
    ".m": {"language": "Objective-C", "single": ["//"], "multi_start": ["/*"], "multi_end": ["*/"]},
    # Web & Styling
    ".html": {"language": "HTML", "single": [], "multi_start": ["<!--"], "multi_end": ["-->"]},
    ".htm": {"language": "HTML", "single": [], "multi_start": ["<!--"], "multi_end": ["-->"]},
    ".vue": {"language": "Vue", "single": ["//"], "multi_start": ["<!--", "/*"], "multi_end": ["-->", "*/"]},
    ".svelte": {"language": "Svelte", "single": ["//"], "multi_start": ["<!--", "/*"], "multi_end": ["-->", "*/"]},
    ".css": {"language": "CSS", "single": [], "multi_start": ["/*"], "multi_end": ["*/"]},
    ".scss": {"language": "SCSS", "single": ["//"], "multi_start": ["/*"], "multi_end": ["*/"]},
    ".sass": {"language": "Sass", "single": ["//"], "multi_start": ["/*"], "multi_end": ["*/"]},
    ".less": {"language": "Less", "single": ["//"], "multi_start": ["/*"], "multi_end": ["*/"]},
    # Scripting
    ".sh": {"language": "Shell", "single": ["#"], "multi_start": [], "multi_end": []},
    ".bash": {"language": "Bash", "single": ["#"], "multi_start": [], "multi_end": []},
    ".zsh": {"language": "Zsh", "single": ["#"], "multi_start": [], "multi_end": []},
    ".ps1": {"language": "PowerShell", "single": ["#"], "multi_start": ["<#"], "multi_end": ["#>"]},
    ".psm1": {"language": "PowerShell Module", "single": ["#"], "multi_start": ["<#"], "multi_end": ["#>"]},
    ".bat": {"language": "Batch", "single": ["rem ", "REM ", "::"], "multi_start": [], "multi_end": []},
    ".cmd": {"language": "Batch", "single": ["rem ", "REM ", "::"], "multi_start": [], "multi_end": []},
    # Data & Config
    ".json": {"language": "JSON", "single": [], "multi_start": [], "multi_end": []},
    ".yaml": {"language": "YAML", "single": ["#"], "multi_start": [], "multi_end": []},
    ".yml": {"language": "YAML", "single": ["#"], "multi_start": [], "multi_end": []},
    ".toml": {"language": "TOML", "single": ["#"], "multi_start": [], "multi_end": []},
    ".xml": {"language": "XML", "single": [], "multi_start": ["<!--"], "multi_end": ["-->"]},
    ".sql": {"language": "SQL", "single": ["--"], "multi_start": ["/*"], "multi_end": ["*/"]},
    ".md": {"language": "Markdown", "single": [], "multi_start": ["<!--"], "multi_end": ["-->"]},
    ".gradle": {"language": "Gradle (Groovy)", "single": ["//"], "multi_start": ["/*"], "multi_end": ["*/"]},
}


def detect_language(file_path: str) -> str:
    """Detect language from file extension or filename."""
    ext = Path(file_path).suffix.lower()
    name = Path(file_path).name.lower()

    if name in ["dockerfile", "dockerfile.dev", "dockerfile.prod"]:
        return "Dockerfile"
    if name in ["makefile", "gnumakefile"]:
        return "Makefile"
    if name in [".env", ".env.example", ".env.local"]:
        return "Env Config"
    if ext in LANGUAGE_CONFIGS:
        return LANGUAGE_CONFIGS[ext]["language"]
    return "Plain Text / Other"


def analyze_file_metrics(file_path: str, content: str = None) -> Dict[str, Any]:
    """Calculate lines, SLOC, blank lines, comment lines, and comment density for a file."""
    if content is None:
        try:
            with open(file_path, "r", encoding="utf-8", errors="replace") as f:
                content = f.read()
        except Exception:
            return {
                "file_path": file_path,
                "language": "Unknown",
                "physical_lines": 0,
                "blank_lines": 0,
                "comment_lines": 0,
                "sloc": 0,
                "comment_density": 0.0,
                "file_size_bytes": 0,
            }

    ext = Path(file_path).suffix.lower()
    config = LANGUAGE_CONFIGS.get(
        ext,
        {"language": detect_language(file_path), "single": ["//", "#"], "multi_start": ["/*"], "multi_end": ["*/"]},
    )
    language = config.get("language", detect_language(file_path))
    singles: List[str] = config.get("single", [])
    multi_starts: List[str] = config.get("multi_start", [])
    multi_ends: List[str] = config.get("multi_end", [])

    lines = content.splitlines()
    physical_lines = len(lines)
    blank_lines = 0
    comment_lines = 0
    sloc = 0

    in_multi_comment = False
    active_multi_end = None

    for line in lines:
        stripped = line.strip()

        # Blank line
        if not stripped:
            blank_lines += 1
            continue

        # In multiline comment
        if in_multi_comment:
            comment_lines += 1
            if active_multi_end and active_multi_end in stripped:
                in_multi_comment = False
                active_multi_end = None
            continue

        # Check for start of multiline comment
        started_multi = False
        for start, end in zip(multi_starts, multi_ends):
            if stripped.startswith(start):
                comment_lines += 1
                started_multi = True
                # Check if it also closes on the same line (and isn't just start marker)
                if stripped.endswith(end) and len(stripped) > len(start) + len(end):
                    in_multi_comment = False
                elif end in stripped[len(start):]:
                    in_multi_comment = False
                else:
                    in_multi_comment = True
                    active_multi_end = end
                break

        if started_multi:
            continue

        # Check for single-line comment
        is_single_comment = False
        for s in singles:
            if stripped.startswith(s):
                comment_lines += 1
                is_single_comment = True
                break

        if is_single_comment:
            continue

        # Otherwise it is a line of code (SLOC)
        sloc += 1
        # Check if line contains inline comment
        for s in singles:
            if s in stripped:
                # counts as code line with comment
                pass

    total_code_and_comment = sloc + comment_lines
    comment_density = round((comment_lines / total_code_and_comment * 100), 2) if total_code_and_comment > 0 else 0.0

    return {
        "file_path": file_path,
        "language": language,
        "physical_lines": physical_lines,
        "blank_lines": blank_lines,
        "comment_lines": comment_lines,
        "sloc": sloc,
        "comment_density": comment_density,
        "file_size_bytes": len(content.encode("utf-8")),
    }


def aggregate_metrics(file_metrics_list: List[Dict[str, Any]]) -> Dict[str, Any]:
    """Aggregate metrics across all analyzed files."""
    total_physical = 0
    total_blank = 0
    total_comment = 0
    total_sloc = 0
    total_size = 0
    language_breakdown: Dict[str, Dict[str, Any]] = {}

    for m in file_metrics_list:
        total_physical += m["physical_lines"]
        total_blank += m["blank_lines"]
        total_comment += m["comment_lines"]
        total_sloc += m["sloc"]
        total_size += m.get("file_size_bytes", 0)

        lang = m["language"]
        if lang not in language_breakdown:
            language_breakdown[lang] = {
                "language": lang,
                "file_count": 0,
                "sloc": 0,
                "physical_lines": 0,
                "comment_lines": 0,
                "blank_lines": 0,
            }
        language_breakdown[lang]["file_count"] += 1
        language_breakdown[lang]["sloc"] += m["sloc"]
        language_breakdown[lang]["physical_lines"] += m["physical_lines"]
        language_breakdown[lang]["comment_lines"] += m["comment_lines"]
        language_breakdown[lang]["blank_lines"] += m["blank_lines"]

    total_code_and_comment = total_sloc + total_comment
    overall_density = round((total_comment / total_code_and_comment * 100), 2) if total_code_and_comment > 0 else 0.0

    # Calculate percentages for languages
    lang_list = []
    for lang_name, stats in language_breakdown.items():
        pct = round((stats["sloc"] / total_sloc * 100), 1) if total_sloc > 0 else 0.0
        stats["percentage"] = pct
        lang_list.append(stats)
    lang_list.sort(key=lambda x: x["sloc"], reverse=True)

    return {
        "total_files": len(file_metrics_list),
        "total_physical_lines": total_physical,
        "total_blank_lines": total_blank,
        "total_comment_lines": total_comment,
        "total_sloc": total_sloc,
        "total_size_bytes": total_size,
        "overall_comment_density": overall_density,
        "languages": lang_list,
    }
