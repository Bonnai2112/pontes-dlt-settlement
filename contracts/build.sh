#!/usr/bin/env bash
# Compiles and tests the contracts with Foundry (Docker), then exports ABI and bytecode for the Java adapter.
set -euo pipefail
cd "$(dirname "$0")"
[ -d lib/openzeppelin-contracts ] || ./install-deps.sh
FORGE="docker run --rm -v $PWD:/work -w /work --entrypoint forge ghcr.io/foundry-rs/foundry:stable"
$FORGE build
$FORGE test -vv
TARGET=../src/main/resources/contracts
mkdir -p "$TARGET"
# Eurosystem DLT: V1 implementation + ERC-1967 proxy, the Java adapter deploys the former behind the latter.
# Market DLT: Identity Registry, one ERC-3643 token per ISIN and the Hash-Link Contract registry.
for CONTRACT in EuroCashToken:EuroCashToken ERC1967Proxy:ERC1967Proxy IdentityRegistry:IdentityRegistry \
                SecurityToken:SecurityToken HashLinkRegistry:HashLinkRegistry; do
  FILE=${CONTRACT%%:*}; NAME=${CONTRACT##*:}
  python3 -c "import json; a=json.load(open('out/$FILE.sol/$NAME.json')); open('$TARGET/$NAME.bin','w').write(a['bytecode']['object'].removeprefix('0x')); json.dump(a['abi'], open('$TARGET/$NAME.abi.json','w'))"
done
echo "ABI and bytecode exported to src/main/resources/contracts"
