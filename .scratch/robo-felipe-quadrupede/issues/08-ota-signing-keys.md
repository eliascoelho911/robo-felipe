# 08 — Geração de chaves RSA para OTA assinado

## Type
task

## Status
open

## Assignee
unclaimed

## Blocked by
none

## Question

Gerar o par de chaves RSA-3072 para assinatura de firmware OTA
(ADR-007), e configurar o fluxo de build:

1. Gerar chave privada (guardar em segredo, fora do repo):
   ```
   openssl genrsa -out priv_key.pem 3072
   ```
2. Derivar chave pública (embarcar no firmware):
   ```
   openssl rsa -in priv_key.pem -pubout > rsa_key.pub
   ```
3. Converter a pública para `public_key.h` (header C com array de
   bytes) para `#include` no firmware.
4. Script de build que assina o firmware:
   ```
   openssl dgst -sign priv_key.pem -sha256 -out fw.sign -binary fw.bin
   cat fw.sign fw.bin > fw.img
   ```
5. Adicionar `public_key.h` ao firmware.
6. Configurar CI (GitHub Actions?) para gerar `fw.img` assinado em
   releases.

Tarefa: gerar chaves, criar `public_key.h`, escrever script de build,
documentar o fluxo. A chave privada NÃO entra no repo (`.gitignore`).
