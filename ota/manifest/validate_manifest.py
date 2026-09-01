#!/usr/bin/env python3
"""Valida o ota/manifest/manifest.json (formato, versão, URLs de Release).

Usado pelo job `ota-manifest` da CI e pela recipe `just ota-manifest-check`.
Anti-rollback em si é aplicado pelo device (ADR-020); aqui garantimos só que
o manifest está bem-formado e aponta para Releases deste repo.
"""

import json
import re
import subprocess
import sys
from pathlib import Path

MANIFEST_PATH = Path(__file__).with_name("manifest.json")

REQUIRED_KEYS = ("type", "version", "host", "port", "bin", "littlefs")
EXPECTED_TYPE = "robo-felipe-tamagotchi"
SEMVER = re.compile(r"\d+\.\d+\.\d+")


def repo_slug() -> str:
    """owner/repo do remote origin — a Release mora no mesmo repo do manifest."""
    url = subprocess.run(
        ["git", "remote", "get-url", "origin"],
        capture_output=True,
        text=True,
        check=True,
    ).stdout.strip()
    match = re.search(r"github(?:\.com)[:/]([^/]+/[^/]+?)(?:\.git)?$", url)
    if not match:
        raise RuntimeError(f"Não consegui extrair owner/repo de: {url}")
    return match.group(1)


def validate(manifest: dict, repo: str) -> list[str]:
    """Retorna lista de erros (vazia = válido)."""
    errors: list[str] = []

    missing = [key for key in REQUIRED_KEYS if key not in manifest]
    if missing:
        errors.append(f"campos faltando: {', '.join(missing)}")
        return errors

    if manifest["type"] != EXPECTED_TYPE:
        errors.append(f"type: esperado {EXPECTED_TYPE!r}, veio {manifest['type']!r}")

    if not SEMVER.fullmatch(str(manifest["version"])):
        errors.append(f"version: esperado X.Y.Z, veio {manifest['version']!r}")

    if manifest["host"] != "github.com":
        errors.append(f"host: esperado 'github.com', veio {manifest['host']!r}")
    if manifest["port"] != 443:
        errors.append(f"port: esperado 443, veio {manifest['port']!r}")

    version = manifest["version"]
    prefix = f"/{repo}/releases/download/v{version}/"
    for key, filename in (("bin", "firmware.img"), ("littlefs", "filesystem.img")):
        path = manifest[key]
        if not path.startswith(prefix):
            errors.append(
                f"{key}: esperado caminho começando com {prefix!r}, veio {path!r}"
            )
        if not path.endswith(filename):
            errors.append(f"{key}: esperado terminar em {filename!r}, veio {path!r}")

    if manifest["bin"] == manifest["littlefs"]:
        errors.append("bin e littlefs apontam para o mesmo arquivo")

    return errors


def main() -> int:
    try:
        manifest = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
        repo = repo_slug()
    except (json.JSONDecodeError, OSError, RuntimeError, subprocess.CalledProcessError) as exc:
        print(f"ERRO: {exc}", file=sys.stderr)
        return 1

    errors = validate(manifest, repo)
    if errors:
        for error in errors:
            print(f"ERRO: {error}", file=sys.stderr)
        return 1

    print(f"OK — manifest válido ({manifest['type']} v{manifest['version']})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
