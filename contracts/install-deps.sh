#!/usr/bin/env bash
# Fetches the Solidity dependencies (pinned versions) into contracts/lib.
set -euo pipefail
cd "$(dirname "$0")"
OZ_VERSION=v5.7.0
FORGE_STD_VERSION=v1.16.2
rm -rf lib && mkdir -p lib/openzeppelin-contracts lib/openzeppelin-contracts-upgradeable lib/forge-std
curl -sSL "https://github.com/OpenZeppelin/openzeppelin-contracts/archive/refs/tags/${OZ_VERSION}.tar.gz" \
  | tar -xz --strip-components=1 -C lib/openzeppelin-contracts
curl -sSL "https://github.com/OpenZeppelin/openzeppelin-contracts-upgradeable/archive/refs/tags/${OZ_VERSION}.tar.gz" \
  | tar -xz --strip-components=1 -C lib/openzeppelin-contracts-upgradeable
curl -sSL "https://github.com/foundry-rs/forge-std/archive/refs/tags/${FORGE_STD_VERSION}.tar.gz" \
  | tar -xz --strip-components=1 -C lib/forge-std
echo "OpenZeppelin (standard + upgradeable) ${OZ_VERSION} and forge-std ${FORGE_STD_VERSION} installed in contracts/lib"
