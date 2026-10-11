#!/usr/bin/env python3
"""Argosy smell guard. PostToolUse hook on Edit/Write: checks the just-written
content against scripts/ci/smell-rules.json and the KDoc and block-comment checks
in scripts/ci/comment_checks.py (the same rules CI enforces on app/src/main; the
comment checks also cover libretrodroid here), plus a hook-only prose-comment-block
check on config and build files, and feeds violations back so
the agent self-corrects at write time. Fails open on any internal error."""

import importlib
import json
import os
import re
import sys


def glob_to_re(glob):
    out, i = [], 0
    while i < len(glob):
        c = glob[i]
        if glob[i : i + 3] == "**/":
            out.append("(?:.*/)?")
            i += 3
        elif glob[i : i + 2] == "**":
            out.append(".*")
            i += 2
        elif c == "*":
            out.append("[^/]*")
            i += 1
        elif c == "?":
            out.append("[^/]")
            i += 1
        else:
            out.append(re.escape(c))
            i += 1
    return re.compile("".join(out) + r"\Z")


def matches_any(path, globs):
    return any(glob_to_re(g).match(path) for g in globs)


HOOK_ONLY_KDOC_PATHS = ["libretrodroid/src/**/*.kt"]

PROSE_PATHS = [
    "**/*.conf", "**/*.gradle", "**/*.gradle.kts", "**/*.pro",
    "**/*.properties", "**/*.sh", "**/*.xml", "**/*.json",
]
PROSE_EXCLUDE_PATHS = ["app/src/main/res/values*/strings*.xml"]
PROSE_COMMENT_RE = re.compile(r"^\s*(//|#|<!--)")
MAX_PROSE_COMMENT_LINES = 1


def load_ci_module(root, name):
    ci_dir = os.path.join(root, "scripts", "ci")
    if ci_dir not in sys.path:
        sys.path.insert(0, ci_dir)
    return importlib.import_module(name)


def prose_findings(text, rel, added=None):
    if not matches_any(rel, PROSE_PATHS):
        return []
    if matches_any(rel, PROSE_EXCLUDE_PATHS):
        return []

    out = []
    run = []
    for line in text.splitlines():
        if PROSE_COMMENT_RE.match(line):
            run.append(line)
            continue
        out.extend(flag_prose_run(run, added))
        run = []
    out.extend(flag_prose_run(run, added))
    return out


def flag_prose_run(run, added):
    if len(run) <= MAX_PROSE_COMMENT_LINES:
        return []
    if added is not None and not any(ln in added for ln in run):
        return []
    return [(
        "prose-comment-block",
        "A comment block this long in a config or build file is narration. State the "
        "constraint in a line, or delete it and put the reasoning in the commit message.",
        "{} lines starting: {}".format(len(run), run[0].strip()[:90]),
    )]


def stability_findings(text, rel, stability_checks, added=None):
    if stability_checks is None:
        return []
    if not matches_any(rel, stability_checks.PATHS):
        return []
    lines = text.splitlines()
    touched = None if added is None else (lambda i: lines[i] in added)
    return [
        (rule_id, message, snippet)
        for rule_id, message, snippet, _ in stability_checks.stability_findings(lines, touched)
    ]


def service_stability_findings(text, rel, service_checks, added=None):
    if not matches_any(rel, service_checks.PATHS):
        return []
    lines = text.splitlines()
    touched = None if added is None else (lambda i: lines[i] in added)
    return [
        (rule_id, message, snippet)
        for rule_id, message, snippet, _ in service_checks.findings(lines, touched)
    ]


def kdoc_findings(text, rel, comment_checks, added=None):
    if comment_checks is None:
        return []
    if not matches_any(rel, comment_checks.PATHS + HOOK_ONLY_KDOC_PATHS):
        return []
    lines = text.splitlines()
    touched = None if added is None else (lambda i: lines[i] in added)
    return [
        (rule_id, message, snippet)
        for rule_id, message, snippet, _ in comment_checks.comment_findings(lines, touched)
    ]


def main():
    try:
        payload = json.load(sys.stdin)
    except Exception:
        sys.exit(0)

    if (payload.get("tool_name") or "") not in ("Edit", "Write"):
        sys.exit(0)

    tool_input = payload.get("tool_input") or {}
    file_path = tool_input.get("file_path", "") or ""
    root = os.environ.get("CLAUDE_PROJECT_DIR") or os.getcwd()
    rel = os.path.relpath(file_path, root) if file_path.startswith(root) else file_path

    if payload["tool_name"] == "Edit":
        text = tool_input.get("new_string")
        if not text:
            sys.exit(0)
        carried = set((tool_input.get("old_string") or "").splitlines())
        lines = [ln for ln in text.splitlines() if ln not in carried]
        block_text = text
        added_lines = set(lines)
        text = "\n".join(lines)
    else:
        text = tool_input.get("content")
        block_text = text
        added_lines = None
    if not text:
        sys.exit(0)

    try:
        with open(os.path.join(root, "scripts", "ci", "smell-rules.json")) as f:
            rules = json.load(f)["rules"]
    except Exception:
        sys.exit(0)

    findings = []
    for rule in rules:
        if not matches_any(rel, rule["path_include"]):
            continue
        if matches_any(rel, rule.get("path_exclude", [])):
            continue
        pat = re.compile(rule["added_regex"])
        skip = re.compile(rule["skip_line_regex"]) if rule.get("skip_line_regex") else None
        for line in text.splitlines():
            if skip and skip.search(line):
                continue
            if pat.search(line):
                findings.append((rule, line.strip()))

    try:
        comment_checks = load_ci_module(root, "comment_checks")
    except Exception:
        comment_checks = None
    try:
        doc_findings = kdoc_findings(block_text, rel, comment_checks, added_lines)
    except Exception:
        doc_findings = []
    try:
        prose = prose_findings(block_text, rel, added_lines)
    except Exception:
        prose = []
    try:
        stability = stability_findings(
            block_text, rel, load_ci_module(root, "stability_checks"), added_lines
        )
    except Exception:
        stability = []
    try:
        stability += service_stability_findings(
            block_text, rel, load_ci_module(root, "service_stability_checks"), added_lines
        )
    except Exception:
        pass

    if not findings and not doc_findings and not prose and not stability:
        sys.exit(0)

    lines = ["SMELL GUARD: the content just written violates house rules:"]
    seen = set()
    for rule, line in findings:
        if rule["id"] not in seen:
            lines.append("[{}] {}".format(rule["id"], rule["message"]))
            seen.add(rule["id"])
        lines.append("    {}".format(line[:120]))

    for rule_id, message, snippet in doc_findings + prose + stability:
        if rule_id not in seen:
            lines.append("[{}] {}".format(rule_id, message))
            seen.add(rule_id)
        lines.append("    {}".format(snippet))
    if any(rule_id.startswith("kdoc-") for rule_id, _, _ in doc_findings):
        lines.append(
            "Default is ZERO comments. Before you keep any KDoc, it must pass all four:"
        )
        lines.append("    1. Does it describe the declaration, and only what it does?")
        lines.append("    2. Does it add value the code does not already carry?")
        lines.append("    3. Does the name already say it? Then delete it.")
        lines.append("    4. Is it an inline comment disguised as a docblock? Then delete it.")
        lines.append("Deleting is the expected outcome. Rewording is not a fix.")

    ci_enforced = comment_checks is not None and matches_any(rel, comment_checks.PATHS)
    if findings or stability or (doc_findings and ci_enforced):
        lines.append(
            "Fix the flagged lines now; CI enforces the same rules on the PR diff"
            " (prose-comment-block is checked here only)."
        )
    else:
        lines.append("Fix the flagged lines now; this check runs at write time only.")
    sys.stderr.write("\n".join(lines) + "\n")
    sys.exit(2)


if __name__ == "__main__":
    main()
