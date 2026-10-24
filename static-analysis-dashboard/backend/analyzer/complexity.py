"""Code complexity module: Cyclomatic Complexity and Cognitive Complexity calculation."""

import ast
import re
from pathlib import Path
from typing import Dict, Any, List, Optional


class PythonComplexityVisitor(ast.NodeVisitor):
    """AST Visitor to compute Cyclomatic and Cognitive complexity for Python code."""

    def __init__(self):
        super().__init__()
        self.file_cyclomatic = 1
        self.file_cognitive = 0
        self.functions: List[Dict[str, Any]] = []
        self._current_function = None
        self._nesting_depth = 0

    def _add_cyclo(self, count: int = 1):
        self.file_cyclomatic += count
        if self._current_function:
            self._current_function["cyclomatic"] += count

    def _add_cognitive(self, base_increment: int = 1, nestable: bool = True):
        penalty = self._nesting_depth if nestable else 0
        total = base_increment + penalty
        self.file_cognitive += total
        if self._current_function:
            self._current_function["cognitive"] += total

    def visit_FunctionDef(self, node: ast.FunctionDef):
        prev_fn = self._current_function
        prev_nesting = self._nesting_depth
        self._nesting_depth = 0

        fn_data = {
            "name": node.name,
            "line": node.lineno,
            "cyclomatic": 1,
            "cognitive": 0,
            "type": "function",
        }
        self._current_function = fn_data
        self.functions.append(fn_data)

        self.generic_visit(node)

        self._current_function = prev_fn
        self._nesting_depth = prev_nesting

    def visit_AsyncFunctionDef(self, node: ast.AsyncFunctionDef):
        self.visit_FunctionDef(node)  # Handled identically

    def visit_If(self, node: ast.If):
        self._add_cyclo(1)
        self._add_cognitive(1, nestable=True)
        self._nesting_depth += 1
        self.generic_visit(node)
        self._nesting_depth -= 1

    def visit_IfExp(self, node: ast.IfExp):
        # Ternary expression
        self._add_cyclo(1)
        self._add_cognitive(1, nestable=True)
        self.generic_visit(node)

    def visit_For(self, node: ast.For):
        self._add_cyclo(1)
        self._add_cognitive(1, nestable=True)
        self._nesting_depth += 1
        self.generic_visit(node)
        self._nesting_depth -= 1

    def visit_AsyncFor(self, node: ast.AsyncFor):
        self.visit_For(node)

    def visit_While(self, node: ast.While):
        self._add_cyclo(1)
        self._add_cognitive(1, nestable=True)
        self._nesting_depth += 1
        self.generic_visit(node)
        self._nesting_depth -= 1

    def visit_ExceptHandler(self, node: ast.ExceptHandler):
        self._add_cyclo(1)
        self._add_cognitive(1, nestable=True)
        self._nesting_depth += 1
        self.generic_visit(node)
        self._nesting_depth -= 1

    def visit_With(self, node: ast.With):
        self.generic_visit(node)

    def visit_AsyncWith(self, node: ast.AsyncWith):
        self.generic_visit(node)

    def visit_BoolOp(self, node: ast.BoolOp):
        # e.g., a and b and c -> 2 extra decision points
        extra = len(node.values) - 1
        self._add_cyclo(extra)
        # In cognitive complexity, boolean operators in sequence add 1
        self._add_cognitive(1, nestable=False)
        self.generic_visit(node)

    def visit_Assert(self, node: ast.Assert):
        self._add_cyclo(1)
        self.generic_visit(node)

    def visit_comprehension(self, node: ast.comprehension):
        # List/Set/Dict comprehension or generator expression
        self._add_cyclo(1 + len(node.ifs))
        self._add_cognitive(1, nestable=True)
        self.generic_visit(node)

    def visit_Match(self, node: ast.Match):
        # Python 3.10+ match statement
        self.generic_visit(node)

    def visit_match_case(self, node: ast.match_case):
        self._add_cyclo(1)
        self._add_cognitive(1, nestable=True)
        self.generic_visit(node)


def calculate_python_complexity(content: str) -> Optional[Dict[str, Any]]:
    """Parse Python code using AST to get exact cyclomatic and cognitive complexity."""
    try:
        tree = ast.parse(content)
        visitor = PythonComplexityVisitor()
        visitor.visit(tree)
        return {
            "cyclomatic": visitor.file_cyclomatic,
            "cognitive": visitor.file_cognitive,
            "functions": sorted(visitor.functions, key=lambda f: f["cyclomatic"], reverse=True),
        }
    except SyntaxError:
        return None
    except Exception:
        return None


def calculate_generic_complexity(content: str, language: str) -> Dict[str, Any]:
    """
    Regex and lexical token scanning for JS/TS, Kotlin, Java, Go, Rust, C/C++, etc.
    Calculates both Cyclomatic and Cognitive complexity with nesting penalty tracking.
    """
    lines = content.splitlines()
    cyclomatic = 1
    cognitive = 0
    nesting_depth = 0
    functions: List[Dict[str, Any]] = []
    current_fn: Optional[Dict[str, Any]] = None

    # Patterns
    fn_pattern = re.compile(
        r"(?:function\s+([a-zA-Z0-9_$]+)|(?:const|let|var)\s+([a-zA-Z0-9_$]+)\s*=\s*(?:async\s*)?\([^)]*\)\s*=>|def\s+([a-zA-Z0-9_$]+)|fun\s+([a-zA-Z0-9_$]+)|(?:public|private|protected|static|\s)+[\w<>\[\]]+\s+([a-zA-Z0-9_$]+)\s*\([^)]*\)\s*\{)",
    )

    # Keywords that increment cyclomatic complexity
    decision_keywords = [
        r"\bif\b",
        r"\belse\s+if\b",
        r"\belif\b",
        r"\bfor\b",
        r"\bwhile\b",
        r"\bcatch\b",
        r"\bexcept\b",
        r"\bcase\b",
    ]
    decision_regex = re.compile("|".join(decision_keywords))
    bool_ops_regex = re.compile(r"(&&|\|\||\band\b|\bor\b)")
    ternary_regex = re.compile(r"\?[^:?]+:")

    in_multi_comment = False

    for idx, raw_line in enumerate(lines, start=1):
        line = raw_line.strip()
        if not line:
            continue

        # Comment handling
        if "/*" in line and "*/" not in line:
            in_multi_comment = True
            continue
        if "*/" in line:
            in_multi_comment = False
            continue
        if in_multi_comment:
            continue
        if line.startswith("//") or line.startswith("#") or line.startswith("--"):
            continue

        # Detect function header
        fn_match = fn_pattern.search(line)
        if fn_match:
            fn_name = next((g for g in fn_match.groups() if g), f"anonymous_fn_{idx}")
            current_fn = {
                "name": fn_name,
                "line": idx,
                "cyclomatic": 1,
                "cognitive": 0,
                "type": "function",
            }
            functions.append(current_fn)

        # Track braces for nesting depth
        open_braces = line.count("{") - line.count("}")
        if "{" in line and "}" in line and line.find("{") > line.find("}"):
            pass  # balanced in line

        # Check decision points
        matches = len(decision_regex.findall(line))
        bool_matches = len(bool_ops_regex.findall(line))
        ternary_matches = len(ternary_regex.findall(line))

        total_decisions = matches + bool_matches + ternary_matches
        if total_decisions > 0:
            cyclomatic += total_decisions
            # Cognitive increments: base + nesting penalty
            # Only count nesting penalty for structural statements (if, for, while, catch)
            structural_count = matches
            penalized_cognitive = structural_count * (1 + max(0, nesting_depth)) + (bool_matches + ternary_matches)
            cognitive += penalized_cognitive

            if current_fn:
                current_fn["cyclomatic"] += total_decisions
                current_fn["cognitive"] += penalized_cognitive

        # Adjust nesting depth for subsequent lines
        nesting_depth += open_braces
        if nesting_depth < 0:
            nesting_depth = 0

    return {
        "cyclomatic": cyclomatic,
        "cognitive": cognitive,
        "functions": sorted(functions, key=lambda f: f["cyclomatic"], reverse=True),
    }


def analyze_file_complexity(file_path: str, content: str = None, language: str = None) -> Dict[str, Any]:
    """Calculate Cyclomatic and Cognitive complexity for a given file."""
    if content is None:
        try:
            with open(file_path, "r", encoding="utf-8", errors="replace") as f:
                content = f.read()
        except Exception:
            return {
                "file_path": file_path,
                "cyclomatic": 1,
                "cognitive": 0,
                "complexity_risk": "Low",
                "functions": [],
            }

    ext = Path(file_path).suffix.lower()

    if ext in [".py", ".pyw"]:
        py_result = calculate_python_complexity(content)
        if py_result:
            result = py_result
        else:
            result = calculate_generic_complexity(content, "Python")
    else:
        result = calculate_generic_complexity(content, language or ext)

    cyclo = result["cyclomatic"]
    cogn = result["cognitive"]

    # Classify complexity risk level
    if cyclo >= 25 or cogn >= 30:
        risk = "Critical"
    elif cyclo >= 15 or cogn >= 20:
        risk = "High"
    elif cyclo >= 8 or cogn >= 10:
        risk = "Moderate"
    else:
        risk = "Low"

    return {
        "file_path": file_path,
        "cyclomatic": cyclo,
        "cognitive": cogn,
        "complexity_risk": risk,
        "functions": result.get("functions", []),
    }
