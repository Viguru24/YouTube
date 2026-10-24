"""FastAPI backend application for Static Code Analysis and Repository Health."""

import os
import platform
import sys
from pathlib import Path
from typing import Dict, Any, List, Optional
from fastapi import FastAPI, HTTPException, Query, Body
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import HTMLResponse, JSONResponse, PlainTextResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel, Field

from analyzer.engine import RepositoryAnalyzer, ScanConfig

app = FastAPI(
    title="CodePulse - Static Code Analysis & Repository Health",
    description="Enterprise-grade static code analysis, complexity metrics, security audits, and code duplication engine.",
    version="1.0.0",
)

# Enable CORS for local dev
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

FRONTEND_DIR = Path(__file__).resolve().parent.parent / "frontend"
WORKSPACE_DIR = Path(__file__).resolve().parent.parent.parent  # e:\Documents\GitHub\Youtube

# In-memory cache for latest scan result
latest_scan_result: Optional[Dict[str, Any]] = None


class ScanRequest(BaseModel):
    repo_path: Optional[str] = Field(None, description="Absolute or relative path to project repository")
    max_files: Optional[int] = Field(1500, description="Max files to analyze")
    min_dup_lines: Optional[int] = Field(5, description="Minimum lines for duplicate code blocks")
    custom_ignore_dirs: Optional[List[str]] = Field(default_factory=list, description="Extra directories to ignore")


@app.get("/api/system-info")
def get_system_info():
    """Return runtime host and environment metadata."""
    return {
        "os": f"{platform.system()} {platform.release()} ({platform.machine()})",
        "python_version": sys.version.split()[0],
        "default_workspace": str(WORKSPACE_DIR),
        "frontend_path": str(FRONTEND_DIR),
    }


@app.get("/api/presets")
def get_preset_paths():
    """Return preconfigured and local workspace path presets."""
    presets = [
        {"name": "Current Workspace (YouTube/Vixz)", "path": str(WORKSPACE_DIR)},
        {"name": "Server Submodule", "path": str(WORKSPACE_DIR / "server")},
        {"name": "Analyzer Self-Scan", "path": str(Path(__file__).resolve().parent.parent)},
    ]
    # Check if app/ or android exists
    if (WORKSPACE_DIR / "app").exists():
        presets.append({"name": "Android App Source", "path": str(WORKSPACE_DIR / "app")})
    return {"presets": presets}


@app.post("/api/scan")
def scan_repository(request: ScanRequest):
    """Run full static analysis on specified repository."""
    global latest_scan_result
    target_path = request.repo_path.strip() if request.repo_path else str(WORKSPACE_DIR)

    p = Path(target_path).resolve()
    if not p.exists():
        raise HTTPException(status_code=400, detail=f"Target path does not exist: {target_path}")
    if not p.is_dir():
        raise HTTPException(status_code=400, detail=f"Target path is not a directory: {target_path}")

    config = ScanConfig(
        repo_path=str(p),
        max_files=request.max_files or 1500,
        custom_ignore_dirs=request.custom_ignore_dirs or [],
        min_dup_lines=request.min_dup_lines or 5,
        run_duplication=True,
    )

    analyzer = RepositoryAnalyzer(config)
    result = analyzer.run_scan()
    latest_scan_result = result
    return result


@app.get("/api/scan/latest")
def get_latest_scan():
    """Retrieve the cached result of the most recent scan."""
    if latest_scan_result is None:
        return {"status": "none", "message": "No scan has been run yet."}
    return latest_scan_result


@app.post("/api/export/markdown", response_class=PlainTextResponse)
def export_markdown_report():
    """Generate Markdown executive health report from the latest scan."""
    if latest_scan_result is None:
        raise HTTPException(status_code=400, detail="No scan data available. Run a scan first.")

    res = latest_scan_result
    meta = res["meta"]
    h = res["health"]
    m = res["metrics"]
    c = res["complexity"]
    s = res["security"]
    l = res["linting"]
    d = res["duplication"]

    md = []
    md.append(f"# Repository Health & Static Analysis Audit Report")
    md.append(f"**Target:** `{meta['scanned_path']}`  ")
    md.append(f"**Timestamp:** {meta['timestamp']} | **Duration:** {meta['scan_time_seconds']}s | **Files Analyzed:** {meta['files_analyzed']}\n")
    md.append("---")
    md.append(f"## Executive Summary")
    md.append(f"- **Overall Health Score:** {h['overall_score']} / 100 ({h['grade']} - {h['grade_desc']})")
    md.append(f"- **Total SLOC:** {m['total_sloc']:,} lines (Physical: {m['total_physical_lines']:,})")
    md.append(f"- **Comment Density:** {m['overall_comment_density']}%")
    md.append(f"- **Avg Cyclomatic Complexity:** {c['avg_cyclomatic']} | **Avg Cognitive Complexity:** {c['avg_cognitive']}")
    md.append(f"- **Security Findings:** {s['total_findings']} issues")
    md.append(f"- **Code Smells / Lint Issues:** {l['total_issues']} issues")
    md.append(f"- **Duplication Rate:** {h['counts']['duplication_rate_pct']}% ({d['total_duplicated_lines']} duplicate lines in {d['duplicate_blocks_count']} blocks)\n")

    md.append("### Health Pillars Breakdown")
    md.append(f"| Dimension | Score (0-100) |")
    md.append(f"| :--- | :--- |")
    md.append(f"| Code Metrics & Comments | {h['pillars']['metrics']} |")
    md.append(f"| Control Flow Complexity | {h['pillars']['complexity']} |")
    md.append(f"| Linting & Code Smells | {h['pillars']['linting']} |")
    md.append(f"| Security & Safety | {h['pillars']['security']} |")
    md.append(f"| Code Duplication (DRY) | {h['pillars']['duplication']} |\n")

    md.append("### Actionable Remediation Checklist")
    for rec in h["recommendations"]:
        md.append(f"- **[{rec['priority']}] {rec['title']}**: {rec['description']}")

    if s["findings"]:
        md.append("\n### Security Alerts")
        for finding in s["findings"][:15]:
            md.append(f"- **[{finding['severity']}]** `{finding['file_path']}:{finding.get('line', 1)}` - {finding['name']}: {finding['message']}")

    return "\n".join(md)


# Mount static assets
if FRONTEND_DIR.exists():
    app.mount("/static", StaticFiles(directory=str(FRONTEND_DIR)), name="static")


@app.get("/", response_class=HTMLResponse)
def serve_dashboard():
    """Serve the single-page application dashboard."""
    index_file = FRONTEND_DIR / "index.html"
    if not index_file.exists():
        return HTMLResponse("<h1>Frontend index.html not found</h1>", status_code=404)
    with open(index_file, "r", encoding="utf-8") as f:
        return HTMLResponse(f.read())


if __name__ == "__main__":
    import uvicorn
    import webbrowser
    import threading

    port = 8585
    url = f"http://localhost:{port}"

    def open_browser():
        time.sleep(1.2)
        try:
            webbrowser.open(url)
        except Exception:
            pass

    import time
    threading.Thread(target=open_browser, daemon=True).start()
    print(f"🚀 CodePulse Static Analysis Server starting at {url}")
    uvicorn.run("main:app", host="127.0.0.1", port=port, reload=False)
