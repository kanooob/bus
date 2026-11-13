#!/usr/bin/env python3
"""Updates Android app versionCode and versionName across the project."""

from __future__ import annotations

import argparse
import os
import re
import sys
from pathlib import Path

BUILD_GRADLE_PATH = Path("app/build.gradle.kts")
VERSION_CODE_RE = re.compile(r"^(\s*versionCode\s*=\s*)(\d+)(\s*)$", re.MULTILINE)
VERSION_NAME_RE = re.compile(r'^(\s*versionName\s*=\s*")([^"]+)("\s*)$', re.MULTILINE)
VALID_VERSION_NAME_RE = re.compile(r"^[0-9]+(?:\.[0-9]+){1,3}(?:-[0-9A-Za-z.-]+)?$")


def parse_current_version(gradle_content: str) -> tuple[int, str]:
    code_match = VERSION_CODE_RE.search(gradle_content)
    name_match = VERSION_NAME_RE.search(gradle_content)
    if not code_match or not name_match:
        raise ValueError(
            "Impossible de trouver versionCode ou versionName dans app/build.gradle.kts"
        )
    return int(code_match.group(2)), name_match.group(2).strip()


def bump_semver(current_version: str, bump_type: str) -> str:
    clean = current_version.strip().lstrip("vV").split("-", 1)[0].split("+", 1)[0]
    parts = [int(p) for p in clean.split(".") if p.isdigit()]
    while len(parts) < 3:
        parts.append(0)
    major, minor, patch = parts[0], parts[1], parts[2]

    if bump_type == "major":
        return f"{major + 1}.0.0"
    if bump_type == "minor":
        return f"{major}.{minor + 1}.0"
    if bump_type == "patch":
        return f"{major}.{minor}.{patch + 1}"
    raise ValueError(f"Type d'incrément inconnu : {bump_type}")


def compute_new_version(
    current_code: int,
    current_name: str,
    bump_type: str,
    requested_name: str,
    requested_code: str,
) -> tuple[int, str]:
    cleaned_name = requested_name.strip().lstrip("vV")
    if bump_type == "custom":
        if not cleaned_name:
            raise ValueError(
                "Une nouvelle version (version_name) est obligatoire lorsque bump_type = custom."
            )
        new_name = cleaned_name
    elif cleaned_name:
        new_name = cleaned_name
    else:
        new_name = bump_semver(current_name, bump_type)

    if not VALID_VERSION_NAME_RE.match(new_name):
        raise ValueError(
            f"Format de versionName invalide : '{new_name}'. Exemple attendu : 1.2.0 ou 1.2.0-beta.1"
        )

    cleaned_code = requested_code.strip()
    if cleaned_code:
        if not cleaned_code.isdigit() or int(cleaned_code) <= 0:
            raise ValueError(
                f"Le versionCode doit être un entier strictement positif, reçu : '{cleaned_code}'"
            )
        new_code = int(cleaned_code)
    else:
        new_code = current_code + 1

    return new_code, new_name


def update_gradle_file(gradle_content: str, new_code: int, new_name: str) -> str:
    updated = VERSION_CODE_RE.sub(rf"\g<1>{new_code}\g<3>", gradle_content, count=1)
    updated = VERSION_NAME_RE.sub(rf"\g<1>{new_name}\g<3>", updated, count=1)
    return updated


def write_github_outputs(outputs: dict[str, str]) -> None:
    github_output = os.environ.get("GITHUB_OUTPUT")
    if not github_output:
        return
    with open(github_output, "a", encoding="utf-8") as fh:
        for key, value in outputs.items():
            fh.write(f"{key}={value}\n")


def main() -> int:
    parser = argparse.ArgumentParser(description="Met à jour la version de l'application Android.")
    parser.add_argument(
        "--bump-type",
        choices=["patch", "minor", "major", "custom"],
        default="patch",
        help="Type d'incrément de version",
    )
    parser.add_argument("--version-name", default="", help="Version explicite (ex: 1.2.0)")
    parser.add_argument("--version-code", default="", help="Code de version entier explicite")
    parser.add_argument("--gradle-file", default=str(BUILD_GRADLE_PATH), help="Chemin vers app/build.gradle.kts")
    parser.add_argument("--dry-run", action="store_true", help="Ne modifie pas le fichier sur disque")
    args = parser.parse_args()

    gradle_path = Path(args.gradle_file)
    if not gradle_path.exists():
        print(f"Erreur : fichier introuvable : {gradle_path}", file=sys.stderr)
        return 1

    content = gradle_path.read_text(encoding="utf-8")
    old_code, old_name = parse_current_version(content)
    new_code, new_name = compute_new_version(
        current_code=old_code,
        current_name=old_name,
        bump_type=args.bump_type,
        requested_name=args.version_name,
        requested_code=args.version_code,
    )

    updated_content = update_gradle_file(content, new_code, new_name)
    if not args.dry_run:
        gradle_path.write_text(updated_content, encoding="utf-8")

    tag_name = f"v{new_name}"
    outputs = {
        "old_version_code": str(old_code),
        "old_version_name": old_name,
        "new_version_code": str(new_code),
        "new_version_name": new_name,
        "tag_name": tag_name,
    }
    write_github_outputs(outputs)

    print(f"Ancienne version : v{old_name} (versionCode={old_code})")
    print(f"Nouvelle version : v{new_name} (versionCode={new_code})")
    print(f"Tag Git associé  : {tag_name}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
