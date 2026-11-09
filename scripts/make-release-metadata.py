#!/usr/bin/env python3
"""Genera version.json para Mega Equalizer a partir de los APK del release.

Ejemplo:
    python scripts/make-release-metadata.py \
        --apk-dir release-assets \
        --version-name 1.0 \
        --version-code 1000 \
        --tag v1.0 \
        --repo angelonmx/mega-equalizer \
        --notes-file RELEASE_NOTES.md \
        --output release-assets/version.json
"""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
from pathlib import Path
from urllib.parse import quote

ABIS = ("arm64-v8a", "armeabi-v7a", "universal")
CHUNK_SIZE = 1024 * 1024


def sha256sum(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(CHUNK_SIZE), b""):
            digest.update(chunk)
    return digest.hexdigest()


def find_apk(apk_dir: Path, version: str, abi: str) -> Path:
    expected = apk_dir / f"mega-equalizer-{version}-{abi}.apk"
    if expected.is_file():
        return expected

    matches = sorted(apk_dir.glob(f"*-{abi}.apk"))
    if len(matches) == 1:
        return matches[0]
    if not matches:
        raise FileNotFoundError(
            f"No se encontró el APK para {abi} en {apk_dir}. "
            f"Se esperaba {expected.name}."
        )
    names = ", ".join(match.name for match in matches)
    raise RuntimeError(f"Hay varios APK para {abi}: {names}")


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Genera el archivo version.json de Mega Equalizer."
    )
    parser.add_argument("--apk-dir", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--version-name", required=True)
    parser.add_argument("--version-code", required=True, type=int)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--repo", required=True)
    parser.add_argument("--notes-file", type=Path)
    parser.add_argument("--notes", default="")
    args = parser.parse_args()

    if not args.apk_dir.is_dir():
        print(f"Error: no existe el directorio {args.apk_dir}", file=sys.stderr)
        return 1

    notes = args.notes
    if args.notes_file:
        if not args.notes_file.is_file():
            print(f"Error: no existe {args.notes_file}", file=sys.stderr)
            return 1
        notes = args.notes_file.read_text(encoding="utf-8").strip()

    assets: dict[str, dict[str, str]] = {}
    used_names: set[str] = set()

    for abi in ABIS:
        apk = find_apk(args.apk_dir, args.version_name, abi)
        if apk.stat().st_size <= 0:
            print(f"Error: {apk.name} está vacío", file=sys.stderr)
            return 1
        if apk.name in used_names:
            print(f"Error: nombre de APK repetido: {apk.name}", file=sys.stderr)
            return 1
        used_names.add(apk.name)
        assets[abi] = {
            "name": apk.name,
            "url": (
                f"https://github.com/{args.repo}/releases/download/"
                f"{quote(args.tag)}/{quote(apk.name)}"
            ),
            "sha256": sha256sum(apk),
        }
        print(f"{abi}: {apk.name} ({apk.stat().st_size} bytes)")

    metadata = {
        "versionCode": args.version_code,
        "versionName": args.version_name,
        "notes": notes,
        "assets": assets,
    }

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        json.dumps(metadata, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"version.json escrito en {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
