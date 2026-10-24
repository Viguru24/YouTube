"""Repository health scoring and grading algorithm with actionable recommendations."""

from typing import Dict, Any, List


def calculate_health_scores(
    metrics_summary: Dict[str, Any],
    complexity_list: List[Dict[str, Any]],
    lint_issues: List[Dict[str, Any]],
    security_findings: List[Dict[str, Any]],
    duplication_summary: Dict[str, Any],
) -> Dict[str, Any]:
    """Calculate overall health score, pillar scores, letter grade, and remediation plan."""

    total_sloc = max(1, metrics_summary.get("total_sloc", 0))
    total_files = max(1, metrics_summary.get("total_files", 0))

    # --- 1. Metrics & Documentation Pillar (Weight: 15%) ---
    density = metrics_summary.get("overall_comment_density", 0.0)
    # Ideal comment density is between 12% and 25%
    if density >= 12 and density <= 28:
        metrics_score = 95.0
    elif density >= 8:
        metrics_score = 85.0
    elif density >= 4:
        metrics_score = 75.0
    else:
        metrics_score = max(50.0, 50.0 + density * 5.0)

    # --- 2. Complexity Pillar (Weight: 25%) ---
    # High cyclomatic / cognitive complexity reduces score
    avg_cyclo = (
        sum(c.get("cyclomatic", 1) for c in complexity_list) / len(complexity_list)
        if complexity_list
        else 1.0
    )
    high_risk_files = sum(1 for c in complexity_list if c.get("complexity_risk") in ["High", "Critical"])
    high_risk_ratio = high_risk_files / total_files

    complexity_score = 100.0
    complexity_score -= min(35.0, (avg_cyclo - 1.0) * 4.0)
    complexity_score -= min(40.0, high_risk_ratio * 100.0)
    complexity_score = max(20.0, round(complexity_score, 1))

    # --- 3. Linting & Code Smells Pillar (Weight: 20%) ---
    # Count error, warning, info
    errors = sum(1 for i in lint_issues if i.get("severity") == "Error")
    warnings = sum(1 for i in lint_issues if i.get("severity") == "Warning")
    infos = sum(1 for i in lint_issues if i.get("severity") == "Info")

    # Defects per 1000 SLOC
    defect_penalty = (errors * 15.0 + warnings * 3.0 + infos * 0.5) / (total_sloc / 1000.0)
    lint_score = max(25.0, round(100.0 - min(75.0, defect_penalty), 1))

    # --- 4. Security & Safety Pillar (Weight: 25%) ---
    sec_critical = sum(1 for s in security_findings if s.get("severity") == "Critical")
    sec_high = sum(1 for s in security_findings if s.get("severity") == "High")
    sec_med = sum(1 for s in security_findings if s.get("severity") in ["Medium", "Warning"])

    sec_score = 100.0
    sec_score -= sec_critical * 25.0
    sec_score -= sec_high * 12.0
    sec_score -= sec_med * 5.0
    sec_score = max(0.0, round(sec_score, 1))

    # --- 5. Duplication Pillar (Weight: 15%) ---
    dup_lines = duplication_summary.get("total_duplicated_lines", 0)
    dup_percent = (dup_lines / total_sloc) * 100.0 if total_sloc > 0 else 0.0
    # < 3% is great (100 score), > 20% drops down to ~50
    dup_score = max(20.0, round(100.0 - min(80.0, dup_percent * 3.5), 1))

    # --- Overall Score (0-100) ---
    overall = (
        metrics_score * 0.15
        + complexity_score * 0.25
        + lint_score * 0.20
        + sec_score * 0.25
        + dup_score * 0.15
    )
    overall_score = round(overall, 1)

    # Determine Letter Grade
    if overall_score >= 95:
        grade = "A+"
        grade_desc = "Exceptional Health"
    elif overall_score >= 88:
        grade = "A"
        grade_desc = "Strong Code Quality"
    elif overall_score >= 78:
        grade = "B"
        grade_desc = "Good, Minor Remediation Needed"
    elif overall_score >= 68:
        grade = "C"
        grade_desc = "Moderate Technical Debt"
    elif overall_score >= 55:
        grade = "D"
        grade_desc = "High Technical Debt & Risks"
    else:
        grade = "F"
        grade_desc = "Critical Health Deficits"

    # Actionable Recommendations
    recommendations: List[Dict[str, Any]] = []

    if sec_critical > 0 or sec_high > 0:
        recommendations.append({
            "priority": "P0 - Urgent",
            "category": "Security",
            "icon": "shield-alert",
            "title": f"Revoke & Rotate {sec_critical + sec_high} Exposed Secrets/Vulnerabilities",
            "description": "Hardcoded API keys, private keys, or high-risk dependencies were found. Move secrets to environment variables or secret managers immediately.",
        })

    if high_risk_files > 0:
        recommendations.append({
            "priority": "P1 - High",
            "category": "Complexity",
            "icon": "git-pull-request",
            "title": f"Refactor {high_risk_files} Highly Complex Modules",
            "description": "Modules with Cyclomatic/Cognitive complexity above acceptable thresholds are prone to regression bugs. Extract methods and decouple branching.",
        })

    if errors > 0:
        recommendations.append({
            "priority": "P1 - High",
            "category": "Quality",
            "icon": "alert-triangle",
            "title": f"Resolve {errors} Syntax / Linter Errors",
            "description": "Source files contain critical syntax errors or severe code smells that can prevent builds or cause runtime failures.",
        })

    if dup_percent > 5.0:
        recommendations.append({
            "priority": "P2 - Medium",
            "category": "Duplication",
            "icon": "copy",
            "title": f"Consolidate Duplicate Code ({round(dup_percent, 1)}% duplication rate)",
            "description": f"{dup_lines} duplicate lines detected across files. Extract repeated logic into shared utility packages to maintain DRY principles.",
        })

    if density < 8.0:
        recommendations.append({
            "priority": "P3 - Low",
            "category": "Documentation",
            "icon": "book-open",
            "title": "Improve In-Code Documentation",
            "description": f"Comment density is currently {density}%. Adding docstrings and architectural explanations to key methods will improve team maintainability.",
        })

    if not recommendations:
        recommendations.append({
            "priority": "P4 - Maintenance",
            "category": "Health",
            "icon": "check-circle",
            "title": "Repository is in Peak Health",
            "description": "No critical security risks, excessive complexity, or syntax smells detected. Keep automated scans in your CI/CD pipeline.",
        })

    return {
        "overall_score": overall_score,
        "grade": grade,
        "grade_desc": grade_desc,
        "pillars": {
            "metrics": round(metrics_score, 1),
            "complexity": round(complexity_score, 1),
            "linting": round(lint_score, 1),
            "security": round(sec_score, 1),
            "duplication": round(dup_score, 1),
        },
        "recommendations": recommendations,
        "counts": {
            "critical_security": sec_critical,
            "high_security": sec_high,
            "lint_errors": errors,
            "lint_warnings": warnings,
            "high_complexity_files": high_risk_files,
            "duplicate_blocks": duplication_summary.get("duplicate_blocks_count", 0),
            "duplicated_lines": dup_lines,
            "duplication_rate_pct": round(dup_percent, 2),
        },
    }
