# Contributing

Thanks for helping improve the PopNow Java SDK.

## Development setup

```bash
git clone https://github.com/popnow-ai/popnow-java
cd popnow-java
mvn -B verify
```

Requires JDK 17 or newer and Maven 3.9. The tests run against a local stub server and need no API
key. Offline, set `POPNOW_SKIP_LIVE_CONTRACT=1` to skip the comparison with the published OpenAPI
spec. `mvn -B -Prelease -Dgpg.skip verify` also builds the Javadoc (doclint on) and sources jars.

## Guidelines

- One runtime dependency: Jackson databind, 2.12 or newer. Do not add others.
- Keep `maven.compiler.release` at 17; CI tests 17 and 21. Compiler warnings fail the build.
- Follow the API contract in the [OpenAPI spec](https://popnow.ai/docs/openapi.yaml); do not add
  endpoints or fields that are not documented. When the spec changes, refresh
  `contracts/openapi.yaml` and re-check the constants `ContractDriftTest` pins.
- Write code, comments, docs and commit messages in English (`RepositoryGuardTest` enforces it).
- Never commit API keys, webhook secrets or real task output links.

## Pull requests

1. Fork the repository and create a branch.
2. Add or update tests and run `mvn -B verify`.
3. Describe the change and link any related issue.
