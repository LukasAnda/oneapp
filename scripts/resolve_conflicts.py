#!/usr/bin/env python3
"""Resolve git merge conflicts using Claude Sonnet.

Called by sync-template.yml after `git merge` exits with conflicts.
Reads each conflicted file, asks Sonnet to resolve it using OneApp's
ownership rules, writes the result back, and stages the file.
"""
import os
import subprocess
import anthropic

client = anthropic.Anthropic(api_key=os.environ["ANTHROPIC_API_KEY"])

SYSTEM_PROMPT = """You are resolving merge conflicts in a user's fork of the OneApp Android template.

OneApp is a self-evolving Android shell. The template repo owns the CI/agent infrastructure.
Users own their app code — plugins, manifest, build config.

Ownership rules (when in doubt, apply these):
- app/src/                  → USER owns (custom plugins and Kotlin code)
- app/AndroidManifest.xml   → USER owns (permissions for their plugins)
- app/build.gradle.kts      → USER owns (their signing, deps, BuildConfig fields)
- .github/workflows/        → TEMPLATE owns (CI/CD improvements)
- scripts/                  → TEMPLATE owns (agent and tooling improvements)
- build-stubs/              → TEMPLATE owns (plugin API stubs)
- IDENTITY.md               → USER owns (their agent constitution)
- MANIFEST.md               → USER owns (their installed plugins list)
- JOURNAL.md                → USER owns (their evolution history)
- README.md                 → TEMPLATE owns (project docs)
- docs/                     → MERGE (template additions + user changes)
- gradle/libs.versions.toml → MERGE (prefer newer versions, keep user additions)
- All other files            → TEMPLATE owns by default

For merge, include everything from both sides sensibly.
For user-owned files with no user customisation, still prefer the template (user hasn't touched it).

Return ONLY the resolved file content. No explanation, no markdown fences.
"""


def get_conflicted_files() -> list[str]:
    result = subprocess.run(
        ["git", "diff", "--name-only", "--diff-filter=U"],
        capture_output=True, text=True, check=True,
    )
    return [f for f in result.stdout.strip().splitlines() if f]


def resolve_file(path: str, content: str) -> str:
    message = client.messages.create(
        model="claude-sonnet-4-6",
        max_tokens=8096,
        system=SYSTEM_PROMPT,
        messages=[{
            "role": "user",
            "content": f"File: {path}\n\nConflicted content:\n\n{content}",
        }],
    )
    return message.content[0].text


def main():
    conflicted = get_conflicted_files()
    if not conflicted:
        print("No conflicted files — nothing to resolve.")
        return

    print(f"Resolving {len(conflicted)} conflicted file(s) with Claude Sonnet...")

    for path in conflicted:
        print(f"  Resolving: {path}")
        with open(path, "r") as f:
            content = f.read()

        resolved = resolve_file(path, content)

        with open(path, "w") as f:
            f.write(resolved)

        subprocess.run(["git", "add", path], check=True)
        print(f"  ✓ {path}")

    print("All conflicts resolved and staged.")


if __name__ == "__main__":
    main()
