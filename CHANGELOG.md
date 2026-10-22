# Changelog

All notable changes to this package are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/).

## [Unreleased]

### Changed

- Javadoc, README and the pinned contract describe credits in the current unit (1 credit = $0.001 since 2026-10-19); test fixtures use matching credit and USD pairs. No code change: the SDK reads credit amounts as the API returns them.

## [0.1.0] - 2026-10-19

First release of the PopNow Java SDK (`ai.popnow:popnow-java`, package `ai.popnow`, Java 17+).

### Added

- `PopNowClient` with `POPNOW_API_KEY`, HTTPS-only base URL (loopback HTTP allowed), explicit
  `popnow-java/<version>` User-Agent, `Accept-Language` for error messages, and separate request,
  upload and wait budgets. Redirects are never followed.
- Envelope decoding that branches on the business code. A sealed `PopNowException` taxonomy:
  `PopNowApiException` (status, code, `reason()` from `data.reason`, request id, `Retry-After`,
  `remediation()`), protocol, transport, timeout, wait-timeout, upload and webhook failures.
- Retries with jittered exponential back-off honouring `Retry-After`, only for requests that are
  safe to repeat; `50302` is never resent and task submissions are retried only under an
  `Idempotency-Key`.
- Tasks: `quote`, `createTask` (idempotency key, `X-PopNow-Retention`, server-side `wait` through
  `CreateTaskOptions`), `getTask`, `waitForTerminal`, `run`, `retryTask`, `purgeTask`, `listTasks`.
  Amounts in credits (`maxCredits`, `estimatedCredits`, `credits`) next to their USD equivalents.
- Models: `listModels`, `getModel` with credit prices (`ModelCredits`) and input schemas.
- Account: `getBalance` and `getUsage` in credits and USD.
- Files: streamed three-step `uploadFile` / `uploadBytes`, documents and `.safetensors` LoRA
  weights, `importLora` (`POST /files/import`), `createDownloadUrl`.
- Asset library: `assets()` with `create`, `uploadFile`, `list`, `listAll`, `get`, `update`,
  `delete`, `usage` and read-only `characters()`.
- Webhooks: `CallbackSignatureVerifier` for task callbacks and `EndpointWebhookVerifier` for
  Standard Webhooks account endpoints, both checking the signature before the timestamp.
- Tests: local stub server, contract drift check against a pinned OpenAPI copy, and an
  English-only and naming guard over every tracked file.

[0.1.0]: https://github.com/popnow-ai/popnow-java/releases/tag/v0.1.0
