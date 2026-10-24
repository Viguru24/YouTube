"""Security and safety scanner: Hardcoded credentials, secrets detection, and dependency audit."""

import json
import math
import re
from pathlib import Path
from typing import Dict, Any, List, Tuple


def calculate_entropy(data: str) -> float:
    """Calculate the Shannon entropy of a string."""
    if not data:
        return 0.0
    entropy = 0.0
    length = len(data)
    occurrences = {}
    for char in data:
        occurrences[char] = occurrences.get(char, 0) + 1
    for count in occurrences.values():
        p = count / length
        entropy -= p * math.log2(p)
    return entropy


def mask_secret(secret: str) -> str:
    """Mask a detected secret showing only first 4 and last 3 characters."""
    if len(secret) <= 8:
        return "********"
    return f"{secret[:4]}...{'*' * (len(secret) - 7)}...{secret[-3:]}"


# High confidence secret patterns
SECRET_PATTERNS = [
    {
        "id": "SEC-001",
        "name": "AWS Access Key ID",
        "pattern": re.compile(r"\b(AKIA[0-9A-Z]{16})\b"),
        "severity": "Critical",
        "category": "Cloud Credentials",
    },
    {
        "id": "SEC-002",
        "name": "GitHub Personal Access Token",
        "pattern": re.compile(r"\b(gh[pousr]_[A-Za-z0-9_]{36,255}|github_pat_[A-Za-z0-9_]{82})\b"),
        "severity": "Critical",
        "category": "VCS Credentials",
    },
    {
        "id": "SEC-003",
        "name": "Google API Key",
        "pattern": re.compile(r"\b(AIza[0-9A-Za-z\-_]{35})\b"),
        "severity": "High",
        "category": "API Key",
    },
    {
        "id": "SEC-004",
        "name": "OpenAI / Anthropic API Key",
        "pattern": re.compile(r"\b(sk-[a-zA-Z0-9_-]{32,64}|sk-ant-[a-zA-Z0-9_-]{32,64})\b"),
        "severity": "Critical",
        "category": "API Key",
    },
    {
        "id": "SEC-005",
        "name": "Private RSA/EC/PGP Key Header",
        "pattern": re.compile(r"-----BEGIN (?:RSA|DSA|EC|OPENSSH|PGP)?\s*PRIVATE KEY-----"),
        "severity": "Critical",
        "category": "Cryptographic Key",
    },
    {
        "id": "SEC-006",
        "name": "Slack Webhook or Bot Token",
        "pattern": re.compile(r"(https:\/\/hooks\.slack\.com\/services\/T[0-9A-Z]+\/B[0-9A-Z]+\/[0-9a-zA-Z]+|xox[baprs]-[0-9a-zA-Z]{10,48})"),
        "severity": "High",
        "category": "OAuth Token",
    },
    {
        "id": "SEC-007",
        "name": "Stripe Live Secret Key",
        "pattern": re.compile(r"\b(sk_live_[0-9a-zA-Z]{24,34})\b"),
        "severity": "Critical",
        "category": "Payment Credentials",
    },
    {
        "id": "SEC-008",
        "name": "JSON Web Token (JWT)",
        "pattern": re.compile(r"\b(eyJ[A-Za-z0-9_-]{10,}\.eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,})\b"),
        "severity": "High",
        "category": "Auth Token",
    },
    {
        "id": "SEC-009",
        "name": "Database Connection String with Credentials",
        "pattern": re.compile(r"(?:postgres|postgresql|mysql|mongodb|redis):\/\/[a-zA-Z0-9_\-\.]+:[^@\s]{3,}@[a-zA-Z0-9_\-\.]+"),
        "severity": "Critical",
        "category": "Database Credentials",
    },
    {
        "id": "SEC-010",
        "name": "Hardcoded Password Assignment",
        "pattern": re.compile(r"""(?:password|passwd|pwd|secret_key)\s*[:=]\s*["']([^"'\s]{8,})["']""", re.IGNORECASE),
        "severity": "High",
        "category": "Hardcoded Secret",
    },
]

# Insecure code practices
INSECURE_CODE_PATTERNS = [
    {
        "id": "SEC-101",
        "name": "Disabled SSL / TLS Certificate Verification",
        "pattern": re.compile(r"(verify\s*=\s*False|NODE_TLS_REJECT_UNAUTHORIZED\s*=\s*['\"]?0['\"]?|rejectUnauthorized:\s*false)", re.IGNORECASE),
        "severity": "High",
        "category": "Insecure Transport",
        "message": "Certificate verification is disabled, exposing network calls to Man-in-the-Middle (MitM) attacks.",
    },
    {
        "id": "SEC-102",
        "name": "Insecure Binding to All Interfaces (0.0.0.0)",
        "pattern": re.compile(r"""(?:host|bind|listen)\s*[:=]\s*['"]0\.0\.0\.0['"]""", re.IGNORECASE),
        "severity": "Medium",
        "category": "Network Exposure",
        "message": "Service is bound to 0.0.0.0, exposing it to all public and local network interfaces.",
    },
    {
        "id": "SEC-103",
        "name": "Debug Mode Enabled in Configuration",
        "pattern": re.compile(r"""(?:DEBUG|debug)\s*[:=]\s*(?:True|true|1)\b"""),
        "severity": "Medium",
        "category": "Information Disclosure",
        "message": "Debug mode enabled. Ensure this is disabled in production environments.",
    },
]

# Known vulnerable or high-risk package versions database
KNOWN_VULNERABLE_PACKAGES = {
    # npm
    "lodash": {"max_vulnerable": "4.17.20", "cve": "CVE-2020-8203 (Prototype Pollution)", "severity": "High"},
    "axios": {"max_vulnerable": "1.5.1", "cve": "CVE-2023-45857 (SSRF / Header leakage)", "severity": "High"},
    "express": {"max_vulnerable": "4.18.1", "cve": "Multiple CVEs in earlier releases", "severity": "Medium"},
    "tar": {"max_vulnerable": "6.1.8", "cve": "CVE-2021-37713 (Arbitrary File Overwrite)", "severity": "High"},
    "jsonwebtoken": {"max_vulnerable": "8.5.1", "cve": "CVE-2022-23529 (Remote Code Execution)", "severity": "Critical"},
    "request": {"max_vulnerable": "999.0.0", "cve": "Package is officially DEPRECATED and unmaintained", "severity": "Medium"},
    # python
    "urllib3": {"max_vulnerable": "2.0.6", "cve": "CVE-2023-45803 (Request Body Leakage)", "severity": "High"},
    "requests": {"max_vulnerable": "2.30.0", "cve": "CVE-2023-32681 (Sensitive Header Leak)", "severity": "Medium"},
    "pyyaml": {"max_vulnerable": "5.3.1", "cve": "CVE-2020-14343 (Arbitrary Code Execution in yaml.load)", "severity": "High"},
    "pillow": {"max_vulnerable": "10.0.0", "cve": "CVE-2023-4863 (Heap Buffer Overflow)", "severity": "Critical"},
    "jinja2": {"max_vulnerable": "3.1.2", "cve": "CVE-2024-22195 (Cross-Site Scripting HTML injection)", "severity": "Medium"},
    "cryptography": {"max_vulnerable": "41.0.5", "cve": "CVE-2023-49083 (Null pointer dereference / crash)", "severity": "Medium"},
    "flask": {"max_vulnerable": "2.2.4", "cve": "CVE-2023-30861 (Cookie Session Spoofing)", "severity": "High"},
}


def scan_content_for_secrets(content: str, file_path: str) -> List[Dict[str, Any]]:
    """Scan file text content for secrets and hardcoded credentials."""
    findings: List[Dict[str, Any]] = []
    lines = content.splitlines()

    # Don't flag sample/dummy env file variables if they clearly contain placeholder words
    is_dummy_file = any(part in file_path.lower() for part in [".env.example", "test", "mock", "dummy", "spec"])

    for idx, raw_line in enumerate(lines, start=1):
        line = raw_line.strip()
        if not line:
            continue

        # Check secret patterns
        for sp in SECRET_PATTERNS:
            match = sp["pattern"].search(line)
            if match:
                matched_val = match.group(1) if match.groups() else match.group(0)

                # Skip obvious placeholders
                if any(ph in matched_val.lower() for ph in ["your_", "example", "my-secret", "placeholder", "xxxx", "test", "token_here"]):
                    continue

                # For hardcoded password assignment, also verify entropy > 3.0 to reduce false positives
                if sp["id"] == "SEC-010":
                    if calculate_entropy(matched_val) < 2.8:
                        continue

                findings.append({
                    "id": sp["id"],
                    "name": sp["name"],
                    "severity": sp["severity"],
                    "category": sp["category"],
                    "file_path": file_path,
                    "line": idx,
                    "masked_secret": mask_secret(matched_val),
                    "entropy": round(calculate_entropy(matched_val), 2),
                    "message": f"Potential {sp['name']} detected. Secrets should never be stored in code.",
                })

        # Check insecure code patterns
        for ip in INSECURE_CODE_PATTERNS:
            if ip["pattern"].search(line):
                findings.append({
                    "id": ip["id"],
                    "name": ip["name"],
                    "severity": ip["severity"],
                    "category": ip["category"],
                    "file_path": file_path,
                    "line": idx,
                    "masked_secret": "N/A",
                    "entropy": 0.0,
                    "message": ip["message"],
                })

    return findings


def audit_dependencies(file_path: str, content: str = None) -> List[Dict[str, Any]]:
    """Audit project dependency manifest files for vulnerabilities and insecure version specs."""
    vulnerabilities: List[Dict[str, Any]] = []
    file_name = Path(file_path).name.lower()

    if content is None:
        try:
            with open(file_path, "r", encoding="utf-8", errors="replace") as f:
                content = f.read()
        except Exception:
            return vulnerabilities

    # 1. Audit package.json
    if file_name == "package.json":
        try:
            pkg_data = json.loads(content)
            deps = {**pkg_data.get("dependencies", {}), **pkg_data.get("devDependencies", {})}
            for dep_name, version_spec in deps.items():
                clean_ver = re.sub(r"[^0-9\.]", "", str(version_spec))

                # Check wildcard versions
                if str(version_spec) in ["*", "latest"] or str(version_spec).startswith(">="):
                    vulnerabilities.append({
                        "file_path": file_path,
                        "package": dep_name,
                        "version": str(version_spec),
                        "severity": "Medium",
                        "type": "Unpinned Dependency",
                        "advisory": f"Package '{dep_name}' uses open/wildcard version '{version_spec}'. Pin exact versions to prevent breaking changes and supply chain injection.",
                    })

                # Check known vulnerable packages
                if dep_name in KNOWN_VULNERABLE_PACKAGES:
                    vuln_info = KNOWN_VULNERABLE_PACKAGES[dep_name]
                    vulnerabilities.append({
                        "file_path": file_path,
                        "package": dep_name,
                        "version": str(version_spec),
                        "severity": vuln_info["severity"],
                        "type": "Known Vulnerability",
                        "advisory": f"{vuln_info['cve']} (affects versions <= {vuln_info['max_vulnerable']}). Recommended: Upgrade {dep_name} to latest safe version.",
                    })
        except Exception:
            pass

    # 2. Audit requirements.txt
    elif file_name in ["requirements.txt", "requirements.in"]:
        lines = content.splitlines()
        for idx, line in enumerate(lines, start=1):
            line = line.strip()
            if not line or line.startswith("#"):
                continue

            # Parse package and specifier
            match = re.match(r"^([a-zA-Z0-9_\-\.]+)\s*(==|>=|<=|~=|>|<)?\s*([0-9a-zA-Z\.\-_]*)?", line)
            if match:
                pkg_name, op, ver = match.groups()
                pkg_lower = pkg_name.lower()

                if not op:
                    vulnerabilities.append({
                        "file_path": file_path,
                        "line": idx,
                        "package": pkg_name,
                        "version": "unpinned",
                        "severity": "Low",
                        "type": "Unpinned Dependency",
                        "advisory": f"Package '{pkg_name}' is unpinned. Pin with '==' to ensure deterministic builds.",
                    })

                if pkg_lower in KNOWN_VULNERABLE_PACKAGES:
                    vuln_info = KNOWN_VULNERABLE_PACKAGES[pkg_lower]
                    vulnerabilities.append({
                        "file_path": file_path,
                        "line": idx,
                        "package": pkg_name,
                        "version": ver or "unpinned",
                        "severity": vuln_info["severity"],
                        "type": "Known Vulnerability",
                        "advisory": f"{vuln_info['cve']}. Recommended: Upgrade {pkg_name} to latest safe release.",
                    })

    # 3. Audit build.gradle / build.gradle.kts
    elif "gradle" in file_name:
        for idx, line in enumerate(content.splitlines(), start=1):
            if "http://" in line and not "localhost" in line:
                vulnerabilities.append({
                    "file_path": file_path,
                    "line": idx,
                    "package": "Repository Config",
                    "version": "N/A",
                    "severity": "High",
                    "type": "Insecure Repository",
                    "advisory": "Insecure HTTP repository URL found in Gradle build script. Always use HTTPS for dependency repositories.",
                })
            if "+" in line and ("implementation" in line or "classpath" in line):
                vulnerabilities.append({
                    "file_path": file_path,
                    "line": idx,
                    "package": "Gradle Dependency",
                    "version": "+",
                    "severity": "Medium",
                    "type": "Dynamic Versioning",
                    "advisory": "Dynamic '+' version specified in Gradle dependency. This causes unpredictable builds and potential dependency hijacking.",
                })

    return vulnerabilities
