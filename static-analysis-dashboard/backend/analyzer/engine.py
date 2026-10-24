"""Main analysis orchestrator engine for repository scanning."""

import fnmatch
import os
import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import Dict, Any, List, Set, Optional

from .metrics import analyze_file_metrics, aggregate_metrics, detect_language
from .complexity import analyze_file_complexity
from .linter import analyze_file_linting
from .security import scan_content_for_secrets, audit_dependencies
from .duplication import detect_duplicates
from .health_score import calculate_health_scores


# File extensions to ignore by default (binary, media, compiled)
IGNORED_EXTENSIONS = {
    ".png", ".jpg", ".jpeg", ".gif", ".ico", ".svg", ".webp", ".bmp",
    ".mp4", ".mp3", ".wav", ".avi", ".mov", ".mkv",
    ".zip", ".tar", ".gz", ".7z", ".rar", ".jar", ".war",
    ".exe", ".dll", ".so", ".dylib", ".bin", ".obj", ".o", ".a",
    ".pyc", ".pyo", ".class", ".dex", ".apk", ".aar",
    ".ttf", ".otf", ".woff", ".woff2", ".eot",
    ".db", ".sqlite", ".sqlite3",
    ".pdf", ".docx", ".xlsx",
}

# Directories to always ignore
DEFAULT_IGNORE_DIRS = {
    ".git", ".svn", ".hg", "node_modules", "vendor", "venv", ".venv",
    "env", ".env", "build", "dist", "out", "target", "bin", "obj",
    ".gradle", ".kotlin", ".idea", ".vscode", "__pycache__", ".pytest_cache",
    ".mypy_cache", ".ruff_cache", "coverage", ".next", ".nuxt",
}


@dataclass
class ScanConfig:
    repo_path: str
    max_files: int = 1500
    custom_ignore_dirs: List[str] = field(default_factory=list)
    min_dup_lines: int = 5
    run_duplication: bool = True


class RepositoryAnalyzer:
    """Orchestrates comprehensive multi-dimensional static code analysis."""

    def __init__(self, config: ScanConfig):
        self.config = config
        self.repo_path = Path(config.repo_path).resolve()
        self.ignore_dirs = DEFAULT_IGNORE_DIRS.union(set(config.custom_ignore_dirs))
        self.gitignore_patterns: List[str] = self._load_gitignore()

    def _load_gitignore(self) -> List[str]:
        """Load patterns from root .gitignore if present."""
        gi_path = self.repo_path / ".gitignore"
        patterns = []
        if gi_path.exists() and gi_path.is_file():
            try:
                with open(gi_path, "r", encoding="utf-8", errors="ignore") as f:
                    for line in f:
                        line = line.strip()
                        if line and not line.startswith("#"):
                            patterns.append(line.rstrip("/"))
            except Exception:
                pass
        return patterns

    def _is_ignored(self, path: Path) -> bool:
        """Check if path is in ignored directories or matches gitignore."""
        # Check parent directory names
        for parent in path.relative_to(self.repo_path).parts[:-1]:
            if parent in self.ignore_dirs:
                return True

        rel_str = str(path.relative_to(self.repo_path)).replace("\\", "/")

        for pattern in self.gitignore_patterns:
            if fnmatch.fnmatch(rel_str, pattern) or fnmatch.fnmatch(path.name, pattern):
                return True
            if pattern.endswith("/*") and fnmatch.fnmatch(rel_str, pattern[:-2] + "/*"):
                return True

        return False

    def collect_source_files(self) -> List[Path]:
        """Walk directory and collect source files."""
        collected: List[Path] = []

        if not self.repo_path.exists() or not self.repo_path.is_dir():
            return collected

        for root, dirs, files in os.walk(self.repo_path):
            # Prune ignored directories in-place for high performance
            dirs[:] = [d for d in dirs if d not in self.ignore_dirs]

            for file in files:
                f_path = Path(root) / file
                ext = f_path.suffix.lower()

                if ext in IGNORED_EXTENSIONS:
                    continue

                if self._is_ignored(f_path):
                    continue

                # Skip files larger than 2MB to prevent memory bottlenecks
                try:
                    if f_path.stat().st_size > 2 * 1024 * 1024:
                        continue
                except Exception:
                    continue

                collected.append(f_path)
                if len(collected) >= self.config.max_files:
                    return collected

        return collected

    def run_scan(self) -> Dict[str, Any]:
        """Execute complete static analysis scan."""
        start_time = time.time()
        files = self.collect_source_files()

        file_metrics_list = []
        file_complexity_list = []
        all_lint_issues = []
        all_security_findings = []
        files_for_duplication: Dict[str, str] = {}
        file_details_map: Dict[str, Dict[str, Any]] = {}

        for f_path in files:
            rel_path = str(f_path.relative_to(self.repo_path)).replace("\\", "/")
            try:
                with open(f_path, "r", encoding="utf-8", errors="replace") as f:
                    content = f.read()
            except Exception:
                continue

            lang = detect_language(str(f_path))

            # 1. Metrics
            f_metric = analyze_file_metrics(rel_path, content)
            file_metrics_list.append(f_metric)

            # 2. Complexity
            # Only run complexity on code-oriented languages
            if f_metric["sloc"] > 0 and lang not in ["Plain Text / Other", "JSON", "Markdown", "XML"]:
                f_complex = analyze_file_complexity(rel_path, content, lang)
            else:
                f_complex = {
                    "file_path": rel_path,
                    "cyclomatic": 1,
                    "cognitive": 0,
                    "complexity_risk": "Low",
                    "functions": [],
                }
            file_complexity_list.append(f_complex)

            # 3. Linting
            if f_metric["sloc"] > 0:
                issues = analyze_file_linting(rel_path, content, lang)
                all_lint_issues.extend(issues)

            # 4. Security
            # Secret detection across all files
            sec_issues = scan_content_for_secrets(content, rel_path)
            all_security_findings.extend(sec_issues)

            # Dependency audit for manifests
            dep_issues = audit_dependencies(rel_path, content)
            all_security_findings.extend(dep_issues)

            # Store content for duplication detection if code file
            if self.config.run_duplication and f_metric["sloc"] >= 10:
                if lang not in ["JSON", "Plain Text / Other", "Markdown"]:
                    files_for_duplication[rel_path] = content

            # Detailed record per file for dashboard view
            file_details_map[rel_path] = {
                "path": rel_path,
                "language": lang,
                "lines": f_metric["physical_lines"],
                "sloc": f_metric["sloc"],
                "comments": f_metric["comment_lines"],
                "blank": f_metric["blank_lines"],
                "density": f_metric["comment_density"],
                "cyclomatic": f_complex["cyclomatic"],
                "cognitive": f_complex["cognitive"],
                "risk": f_complex["complexity_risk"],
                "issues_count": len([i for i in all_lint_issues if i.get("file_path") == rel_path]),
            }

        # 5. Duplication
        if self.config.run_duplication and len(files_for_duplication) > 1:
            duplication_res = detect_duplicates(files_for_duplication, min_block_lines=self.config.min_dup_lines)
        else:
            duplication_res = {
                "total_duplicated_lines": 0,
                "duplicate_blocks_count": 0,
                "clones": [],
            }

        # Aggregations
        metrics_summary = aggregate_metrics(file_metrics_list)

        # Health Scoring
        health_summary = calculate_health_scores(
            metrics_summary=metrics_summary,
            complexity_list=file_complexity_list,
            lint_issues=all_lint_issues,
            security_findings=all_security_findings,
            duplication_summary=duplication_res,
        )

        # Top complex files ranking
        top_complex_files = sorted(
            [c for c in file_complexity_list if c["cyclomatic"] > 1],
            key=lambda x: (x["cyclomatic"] + x["cognitive"]),
            reverse=True,
        )[:20]

        # Top largest files ranking
        top_largest_files = sorted(
            file_metrics_list,
            key=lambda x: x["sloc"],
            reverse=True,
        )[:20]

        duration_sec = round(time.time() - start_time, 2)

        return {
            "meta": {
                "scanned_path": str(self.repo_path),
                "repo_name": self.repo_path.name or str(self.repo_path),
                "scan_time_seconds": duration_sec,
                "timestamp": time.strftime("%Y-%m-%d %H:%M:%S"),
                "files_analyzed": len(files),
            },
            "health": health_summary,
            "metrics": metrics_summary,
            "complexity": {
                "top_complex_files": top_complex_files,
                "avg_cyclomatic": round(
                    sum(c["cyclomatic"] for c in file_complexity_list) / max(1, len(file_complexity_list)), 2
                ),
                "avg_cognitive": round(
                    sum(c["cognitive"] for c in file_complexity_list) / max(1, len(file_complexity_list)), 2
                ),
            },
            "linting": {
                "total_issues": len(all_lint_issues),
                "issues": all_lint_issues[:150],  # Return up to 150 detailed issues
            },
            "security": {
                "total_findings": len(all_security_findings),
                "findings": all_security_findings,
            },
            "duplication": duplication_res,
            "file_explorer": list(file_details_map.values()),
            "top_largest_files": top_largest_files,
        }
