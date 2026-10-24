"""Multi-language linting, syntax verification, and code smell detector."""

import ast
import re
import shutil
import subprocess
from pathlib import Path
from typing import Dict, Any, List, Optional


class PythonCodeSmellVisitor(ast.NodeVisitor):
    """AST visitor to detect Python code smells and design anti-patterns."""

    def __init__(self, lines: List[str]):
        super().__init__()
        self.lines = lines
        self.issues: List[Dict[str, Any]] = []

    def visit_FunctionDef(self, node: ast.FunctionDef):
        # 1. Too many parameters (> 5)
        total_args = len(node.args.args) + len(node.args.kwonlyargs)
        if node.args.vararg:
            total_args += 1
        if node.args.kwarg:
            total_args += 1
        if total_args > 5:
            self.issues.append({
                "rule": "too-many-arguments",
                "severity": "Warning",
                "category": "Code Smell",
                "line": node.lineno,
                "column": node.col_offset,
                "message": f"Function '{node.name}' has {total_args} parameters (threshold: 5). Consider using a data object or config dict.",
            })

        # 2. Mutable default argument (e.g. def foo(x=[]))
        for default in node.args.defaults + node.args.kw_defaults:
            if default and isinstance(default, (ast.List, ast.Dict, ast.Set)):
                self.issues.append({
                    "rule": "mutable-default-argument",
                    "severity": "Warning",
                    "category": "Bug Risk",
                    "line": default.lineno,
                    "column": default.col_offset,
                    "message": f"Default argument in '{node.name}' is mutable ({type(default).__name__.lower()}). Use None as default instead.",
                })

        # 3. Excessive function length (> 60 lines)
        if hasattr(node, "end_lineno") and node.end_lineno:
            fn_length = node.end_lineno - node.lineno
            if fn_length > 60:
                self.issues.append({
                    "rule": "function-too-long",
                    "severity": "Info",
                    "category": "Maintainability",
                    "line": node.lineno,
                    "column": node.col_offset,
                    "message": f"Function '{node.name}' is {fn_length} lines long (threshold: 60). Consider breaking it into smaller helpers.",
                })

        self.generic_visit(node)

    def visit_AsyncFunctionDef(self, node: ast.AsyncFunctionDef):
        self.visit_FunctionDef(node)

    def visit_ExceptHandler(self, node: ast.ExceptHandler):
        # Bare except:
        if node.type is None:
            self.issues.append({
                "rule": "bare-except",
                "severity": "Warning",
                "category": "Code Smell",
                "line": node.lineno,
                "column": node.col_offset,
                "message": "Bare 'except:' catches all exceptions including SystemExit and KeyboardInterrupt. Use 'except Exception:' instead.",
            })
        # Empty except body: pass
        if len(node.body) == 1 and isinstance(node.body[0], ast.Pass):
            self.issues.append({
                "rule": "silent-exception-swallow",
                "severity": "Warning",
                "category": "Bug Risk",
                "line": node.lineno,
                "column": node.col_offset,
                "message": "Exception is silently suppressed with 'pass'. Consider logging the error or handling it.",
            })
        self.generic_visit(node)

    def visit_Call(self, node: ast.Call):
        # Flag print statements
        if isinstance(node.func, ast.Name) and node.func.id == "print":
            self.issues.append({
                "rule": "debug-print-leftover",
                "severity": "Info",
                "category": "Cleanliness",
                "line": node.lineno,
                "column": node.col_offset,
                "message": "Leftover 'print()' statement detected. Use a proper logging framework (e.g. logging module).",
            })
        self.generic_visit(node)


def lint_python_content(content: str, file_path: str) -> List[Dict[str, Any]]:
    """Lint Python code using AST parsing and smell detectors."""
    issues: List[Dict[str, Any]] = []
    lines = content.splitlines()

    # 1. Syntax Verification
    try:
        tree = ast.parse(content, filename=file_path)
    except SyntaxError as e:
        issues.append({
            "rule": "syntax-error",
            "severity": "Error",
            "category": "Syntax",
            "line": e.lineno or 1,
            "column": e.offset or 0,
            "message": f"Python Syntax Error: {e.msg}",
        })
        return issues
    except Exception as e:
        issues.append({
            "rule": "parse-failure",
            "severity": "Error",
            "category": "Syntax",
            "line": 1,
            "column": 0,
            "message": f"Failed to parse Python file: {str(e)}",
        })
        return issues

    # 2. AST Smell Visitor
    visitor = PythonCodeSmellVisitor(lines)
    visitor.visit(tree)
    issues.extend(visitor.issues)

    # 3. Line-by-line checks
    for idx, line in enumerate(lines, start=1):
        # Line length > 120
        if len(line) > 120 and not line.strip().startswith(("#", "http://", "https://", '"', "'")):
            issues.append({
                "rule": "line-too-long",
                "severity": "Info",
                "category": "Formatting",
                "line": idx,
                "column": 120,
                "message": f"Line length is {len(line)} characters (recommended max 120).",
            })
        # Trailing whitespace
        if line.endswith(" ") or line.endswith("\t"):
            issues.append({
                "rule": "trailing-whitespace",
                "severity": "Info",
                "category": "Formatting",
                "line": idx,
                "column": len(line),
                "message": "Line has trailing whitespace.",
            })

    return issues


def lint_javascript_content(content: str, file_path: str) -> List[Dict[str, Any]]:
    """Lint JavaScript/TypeScript code for syntax and common smells."""
    issues: List[Dict[str, Any]] = []
    lines = content.splitlines()

    for idx, raw_line in enumerate(lines, start=1):
        line = raw_line.strip()
        if not line or line.startswith("//") or line.startswith("/*"):
            continue

        # eval() usage
        if re.search(r"\beval\s*\(", line):
            issues.append({
                "rule": "no-eval",
                "severity": "Error",
                "category": "Security / Code Smell",
                "line": idx,
                "column": line.find("eval"),
                "message": "Use of 'eval()' is dangerous and represents a significant security and performance liability.",
            })

        # var declaration
        if re.search(r"\bvar\s+[a-zA-Z0-9_$]+", line):
            issues.append({
                "rule": "no-var",
                "severity": "Warning",
                "category": "Code Smell",
                "line": idx,
                "column": line.find("var"),
                "message": "Use 'let' or 'const' instead of 'var' to prevent variable hoisting and scope leak issues.",
            })

        # Loose equality == or !=
        if re.search(r"[^=!><]=[=][^=]", line) and not re.search(r"=>", line):
            issues.append({
                "rule": "strict-equality",
                "severity": "Info",
                "category": "Code Smell",
                "line": idx,
                "column": 0,
                "message": "Use strict equality '===' instead of loose equality '==' to prevent unexpected type coercion.",
            })

        # Leftover console.log
        if "console.log(" in line:
            issues.append({
                "rule": "no-console-log",
                "severity": "Info",
                "category": "Cleanliness",
                "line": idx,
                "column": line.find("console.log"),
                "message": "Leftover 'console.log' call. Remove or replace with a production-grade logging service.",
            })

        # Debugger keyword
        if re.search(r"\bdebugger\s*;", line):
            issues.append({
                "rule": "no-debugger",
                "severity": "Warning",
                "category": "Cleanliness",
                "line": idx,
                "column": line.find("debugger"),
                "message": "Remove 'debugger;' statement before pushing to source control.",
            })

        # Empty catch block
        if re.search(r"catch\s*\([^)]*\)\s*\{\s*\}", line):
            issues.append({
                "rule": "empty-catch-block",
                "severity": "Warning",
                "category": "Bug Risk",
                "line": idx,
                "column": 0,
                "message": "Empty catch block silently ignores exceptions. Log or rethrow the error.",
            })

        # Line length > 120
        if len(raw_line) > 120 and not line.startswith(("//", "/*", "*", "import", "export")):
            issues.append({
                "rule": "line-too-long",
                "severity": "Info",
                "category": "Formatting",
                "line": idx,
                "column": 120,
                "message": f"Line exceeds 120 characters ({len(raw_line)} chars).",
            })

    return issues


def lint_generic_content(content: str, file_path: str, language: str) -> List[Dict[str, Any]]:
    """Generic linting for other languages (Kotlin, Java, Go, Rust, C#, Shell, etc.)."""
    issues: List[Dict[str, Any]] = []
    lines = content.splitlines()

    for idx, raw_line in enumerate(lines, start=1):
        line = raw_line.strip()
        if not line:
            continue

        # Look for TODO / FIXME / HACK / BUG markers
        todo_match = re.search(r"\b(TODO|FIXME|HACK|XXX|BUG)\b[:\s]*(.*)", line, re.IGNORECASE)
        if todo_match and (line.startswith("//") or line.startswith("#") or line.startswith("/*") or line.startswith("*")):
            tag = todo_match.group(1).upper()
            msg = todo_match.group(2).strip() or "Unresolved task"
            issues.append({
                "rule": f"{tag.lower()}-comment",
                "severity": "Info" if tag == "TODO" else "Warning",
                "category": "Technical Debt",
                "line": idx,
                "column": raw_line.find(tag),
                "message": f"{tag} found: {msg[:80]}",
            })

        # Deep nesting (> 4 indent tabs or 16 spaces)
        indent_spaces = len(raw_line) - len(raw_line.lstrip(" "))
        indent_tabs = len(raw_line) - len(raw_line.lstrip("\t"))
        if indent_spaces >= 20 or indent_tabs >= 5:
            if not line.startswith(("//", "/*", "*", "#")):
                issues.append({
                    "rule": "deeply-nested-block",
                    "severity": "Info",
                    "category": "Maintainability",
                    "line": idx,
                    "column": 0,
                    "message": "Block is deeply nested (>= 5 levels). Consider extracting helper methods.",
                })

        # Trailing whitespace
        if raw_line.endswith(" ") or raw_line.endswith("\t"):
            issues.append({
                "rule": "trailing-whitespace",
                "severity": "Info",
                "category": "Formatting",
                "line": idx,
                "column": len(raw_line),
                "message": "Line has trailing whitespace.",
            })

    return issues


def run_external_linter_if_available(file_path: str, language: str) -> List[Dict[str, Any]]:
    """
    Optional bridge to run external linters (flake8, ruff, eslint) if installed in PATH.
    Gracefully returns empty list if external tool is not available.
    """
    external_issues: List[Dict[str, Any]] = []
    ext = Path(file_path).suffix.lower()

    # Try ruff or flake8 for Python
    if ext == ".py":
        if shutil.which("ruff"):
            try:
                proc = subprocess.run(
                    ["ruff", "check", "--output-format=json", file_path],
                    capture_output=True,
                    text=True,
                    timeout=5,
                )
                import json
                if proc.stdout.strip():
                    records = json.loads(proc.stdout)
                    for r in records:
                        external_issues.append({
                            "rule": f"ruff-{r.get('code')}",
                            "severity": "Warning",
                            "category": "External Linter",
                            "line": r.get("location", {}).get("row", 1),
                            "column": r.get("location", {}).get("column", 0),
                            "message": r.get("message", ""),
                        })
            except Exception:
                pass
    return external_issues


def analyze_file_linting(file_path: str, content: str = None, language: str = None) -> List[Dict[str, Any]]:
    """Analyze a file for lint issues, syntax errors, and code smells."""
    if content is None:
        try:
            with open(file_path, "r", encoding="utf-8", errors="replace") as f:
                content = f.read()
        except Exception:
            return []

    ext = Path(file_path).suffix.lower()
    issues: List[Dict[str, Any]] = []

    if ext in [".py", ".pyw"]:
        issues = lint_python_content(content, file_path)
    elif ext in [".js", ".mjs", ".cjs", ".jsx", ".ts", ".tsx"]:
        issues = lint_javascript_content(content, file_path)
    else:
        issues = lint_generic_content(content, file_path, language or ext)

    # Attach file path to each issue
    for issue in issues:
        issue["file_path"] = file_path

    # Try external tool if applicable
    ext_issues = run_external_linter_if_available(file_path, language or ext)
    for ei in ext_issues:
        ei["file_path"] = file_path
    issues.extend(ext_issues)

    return issues
