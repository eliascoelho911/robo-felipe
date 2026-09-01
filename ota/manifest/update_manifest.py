#!/usr/bin/env python3
"""Atualiza ota/manifest/manifest.json para uma tag de Release (vX.Y.Z).

Usado pelo workflow de release para apontar o manifest para os binários da
Release recém-publicada (ADR-020). Também roda localmente para simular.
"""

import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from validate_manifest import validate, repo_slug

MANIFEST_PATH = Path(__file__).with_name("manifest.json")
TAG_PATTERN = re.compile(r"v(\d+\.\d+\.\d+)")


def main() -> int:
    if len(sys.argv) != 2 or not TAG_PATTERN.fullmatch(sys.argv[1]):
        print(f"Uso: {sys.argv[0]} vX.Y.Z", file=sys.stderr)
        return 2
    tag = sys.argv[1]
    version = tag[1:]
    repo = repo_slug()

    manifest = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
    manifest["version"] = version
    manifest["bin"] = f"/{repo}/releases/download/{tag}/firmware.img"
    manifest["littlefs"] = f"/{repo}/releases/download/{tag}/filesystem.img"

    # Falha cedo se o resultado ficaria inválido — nunca escreve um manifest
    # que o device recusaria.
    errors = validate(manifest, repo)
    if errors:
        for error in errors:
            print(f"ERRO: {error}", file=sys.stderr)
        return 1

    MANIFEST_PATH.write_text(
        json.dumps(manifest, indent=2) + "\n", encoding="utf-8"
    )
    print(f"OK — manifest atualizado para {tag}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
