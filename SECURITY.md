# Security

This repository is a **proof of concept**. It is not production software and must not be used to
hold or move real value.

## Development keys

The repository intentionally contains throw-away keys so that the local Besu network works out of
the box:

- `besu/validator/key`: private key of the single QBFT validator of the local network;
- `pontes.besu.operator-private-key` in `src/main/resources/application-besu.yml`: Besu's
  well-known public development key, pre-funded in `besu/genesis.json`.

Never reuse these keys, the genesis file or this configuration on any shared or public network.

## Reporting a vulnerability

Please open a [GitHub security advisory](../../security/advisories/new) rather than a public issue.
